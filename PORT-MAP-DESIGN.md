# The port map — a module publishes what it DID, and dependents read it

Status: design, 2026-07-29. Prompted by Ashley being the first genuine dependent port.

---

## The problem, stated from evidence rather than principle

A dependent module's frontend can only parse **Java**. Ashley resolves against
`libgdx/gdx/src` — the *upstream* Java — never against the 596 Scala files the libGDX port
actually emitted. So today a dependent arrives at the base's decisions by **re-deriving** them:
it inherits the base's `PortManifest` and re-runs identically-configured phases over the same
Java, and `ManifestAgreement` verifies that the two derivations agree.

That works, and it caught real drift. But re-derivation has a ceiling, and `ManifestAgreement`
already documents where it stops:

- it cannot see a parameterised phase's **configuration** unless that phase implements
  `SurfacePolicy` — `CollectionsTransform` does not, so two differently-configured instances
  compare equal by name;
- it cannot see **nested-type** drops, which are covered only by the never-fired tally;
- it cannot see anything about the base's **emitted output** at all.

And re-derivation is only as good as the phases being deterministic *and* identically configured.
It answers "did we both intend the same thing?", never "what did you actually produce?"

**The concrete case.** Ashley's `ImmutableArray.toArray(Class)` forwards to `Array.toArray(Class)`,
which the base drops. That was found by `RewriteTrace`'s orphaned-call check **after** translating
and emitting. With a map of what the base produced, it is a lookup answerable *before* translation
begins: `Array#toArray(Class)` is not in the base's exported surface.

---

## The artifact

Each module, at the end of its run, publishes a **port map**: the correspondence between the
upstream Java surface and what this port actually emitted.

Per type and per member, one of five dispositions:

| disposition | meaning |
|---|---|
| `ported` | translated mechanically; carries the emitted FQN + signature, which may differ from Java's after a rename or a retype |
| `renamed` | ported, but at a different FQN (`PackageRenameTransform`, or a type rename) |
| `substituted` | dropped and replaced at the same FQN by injected Scala (`Substitutions.inject`) |
| `dropped` | not emitted and NOT replaced — every reference must have been rewritten away |
| `added` | present in the port and absent upstream — an injected type, a support shim, a member a `(c)` rule introduced |

A member additionally records whether its **body** was substituted (`MethodBodyTransform`), because
that changes behaviour without changing signature, and a dependent's author needs to know the body
they are calling is not the upstream one.

### The engine already computes all of it

Nothing here needs new analysis — only assembly and publication:

| field | source, today |
|---|---|
| emitted FQN + member signature | `SrcMap` (`srcmap.tsv`: unit, member, kind, Java origin, digest) |
| `substituted` / `dropped` | `Substituted` tags + `SubstitutionCheck`'s two halves |
| `renamed` | `PackageRenameTransform.check`'s matched prefixes |
| `added` | `Substitutions.inject` contents + `RuntimePlan` |
| body substituted | `MethodBodyTransform.substituted` |
| engine identity | `EnginePin` / `EngineInfo.version` |
| per-member change detection | `srcmap` digests, already diffed against `baseline/members.tsv` |

So the port map is a **projection of artifacts that already exist**, published in one file with a
declared schema.

---

## What it buys, in order of value

1. **A dependent reads the base's surface instead of guessing at it.** `ManifestAgreement` stops
   being "did we configure the same thing?" and becomes "does what I am about to emit agree with
   what you actually emitted?" — which is the question that matters and the one it cannot ask today.

2. **Call migration becomes mechanical.** A new `(b)` phase — `PortMapTransform(map)` — rewrites a
   dependent's references from the map: a renamed type re-points, a dropped member's call is flagged
   *before* emission with the base's own reason string attached, a body-substituted member is
   reported so its caller knows. Today each of these is re-derived by re-running the base's phases,
   and only the drops are caught, only afterwards.

3. **It closes the three holes `ManifestAgreement` admits to.** Phase configuration, nested types
   and emitted output all become observable, because the map records *output*, not *intent*.

4. **It is the hand-off artifact `PORT-INVENTORY.md` needs.** 18 libraries, each supervised by a
   different agent in a different repository. A published port map per library is what makes those
   ports composable without every agent reading every other agent's manifest — and it is checkable,
   so a base that changes its surface breaks its dependents *loudly* at their next run.

5. **It answers "what did this port actually do?" for a human**, which today requires reading a
   253-line migration program and four check outputs.

---

## On storing the CONFIGURATION as JSON/YAML — a qualified yes

The proposal is to store the port configuration as config too. My assessment, split, because the
two halves of a manifest are not alike:

**The declarative half is data and can be config.** `dropTypes`, `dropMethods`, `packageRenames`,
`MethodBodyTransform`'s key→body map, `Forwarder` lists, `ClassTableTransform` maps — all of these
are string-keyed values with no behaviour. `PortManifest.fromJson` is cheap and the schema is the
same one the port map already needs.

**The `surface` half is code and cannot be.** A phase is a value: `new GdxSharedIteratorRule` is a
§1(c) rule whose whole content is an invariant of one library's design, expressed as a traversal. No
config format expresses it, and the moment one tries, it becomes a plugin-loading mechanism —
precisely what `CLAUDE.md` §2.1 says the engine does not have and does not want ("Implement
`balticporter.tir.Phase`; there is no registry, service loader or plugin descriptor").

**One argument for config is stronger than it first appears, and one is weaker.**

- *Stronger than it appears*: the "Scala is typed and checkable" defence is thin for exactly the
  parts being proposed as config. A `dropTypes` key is a `String` either way — a typo is a runtime
  no-op in both forms, which is why `PolicyReport` had to be built at all. Scala buys nothing there.
- *Weaker than it appears*: config does not **replace** the Scala manifest, it **adds** to it, since
  the phase list must stay code. Two homes for policy is a cost, and the split is not obvious from
  either side.

**The resolution is that the port map largely dissolves the question.** The reason a dependent
restates declarative policy today is that it has no way to learn it. Once the base publishes a map,
the dependent *reads* the drops and renames as data rather than declaring them — so the declarative
half of a dependent's manifest mostly disappears rather than moving to YAML. Config is then worth
having for the **base** module of a library and for the parts an agent edits by hand, and the
schema is shared with the map.

Recommendation: **build the map first**, then let `PortManifest` load its declarative half from the
same schema. Doing config first would standardise a shape that the map is about to change.

---

## Risks, with the cheapest falsifying experiment for each

- **R1 — the map goes stale against the base's emitted output.** Mitigation: it carries the engine
  version (`EnginePin`) and the base's `members.tsv` digests, so a dependent can detect that the map
  was produced by a different engine or a different source revision. *Falsifier*: publish a map,
  change one base member's body, re-run the dependent, assert it reports the staleness rather than
  silently using the old entry.
- **R2 — the map becomes a second source of truth that disagrees with the manifest.** Mitigation:
  the map is an OUTPUT and never an input to its own module; only *dependents* read it. A module's
  own behaviour must never depend on its own map. *Falsifier*: delete a module's own map, re-run,
  assert byte-identical output.
- **R3 — schema churn breaks consumers.** Mitigation: version the schema in the file; a consumer
  refuses an unknown major rather than mis-reading it.
- **R4 — it makes the two-module case easy and the 18-module case still hard**, because a diamond
  (two bases sharing a third) needs map composition. *Falsifier*: `PortManifest.baseChain` already
  handles a chain; build the map consumer against a genuine diamond before claiming it composes.

---

## Suggested order

1. **Emit the map** from `PortRun` — assembly only, no new analysis. Verify: libGDX core's map
   round-trips its own 596 emitted types and 19 528 members with every number unchanged.
2. **Consume it in `ManifestAgreement`** — replace the re-derived shared surface with the base's
   published one where a map is available, and keep re-derivation as the fallback. Verify against
   Ashley: the 605-type agreement must still report 0, and `toArray(Class)` must be reportable
   *before* emission.
3. **`PortMapTransform`** — mechanical call migration for a dependent.
4. **`PortManifest.fromJson`** for the declarative half, sharing the map's schema.
5. Only then consider config as the primary authoring form.
