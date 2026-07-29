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

1. ~~**Emit the map** from `PortRun`~~ — **BUILT.** `PortMap`, `port-map.tsv` per run.
2. ~~**Consume it in `ManifestAgreement`**~~ — **BUILT.** See below.
3. ~~**`PortMapTransform`**~~ — **BUILT.** See below.
4. **`PortManifest.fromJson`** for the declarative half, sharing the map's schema.
5. Only then consider config as the primary authoring form.

---

## Steps 2 and 3, as built

### Discovery — a dependent is not told where its bases' maps are

`PortMap.discover(reportRoot, exclude)` scans `port-report/*/{run-latest,baseline}/port-map.tsv`
and keys each map on the `module=` field of its own header, not on the directory name — a report
directory is named after the migration PROGRAM and a `PortManifest` names the MODULE, and nothing
enforces that those agree. `run-latest` wins over `baseline`, so a dependent run in the same session
as its base sees what the base just produced; the committed baseline is the fallback for a fresh
checkout. `PortRun.discoverBasePorts` passes `exclude = {label, manifest.name}`, which is where R2
is enforced: a module never reads its own map.

### `ManifestAgreement` — published where possible, re-derived where not

`check` now takes `List[BasePort]`, one per declared base, each carrying its manifest and its map if
one was found AND is fresh. Per shared type:

- an entry in a base's map decides tag parity from **what the base produced** (`Dropped` and
  `Substituted` both oblige the dependent to tag) and the expected emitted name from the map's
  `emitted` column;
- no entry, but a base with a usable map **claims** the namespace ⇒ `BaseSurfaceAbsent`, a finding
  re-derivation cannot make at all: a manifest is silent about what it never mentioned;
- otherwise the old re-derivation path, unchanged.

Three new non-fatal, LOUD kinds exist so the fallback is never silent: `BaseMapStale` (refused, not
used), `BaseMapUnverified` (used, freshness unprovable), `BaseMapMissing` (base declares policy and
has published nothing). An **empty** base manifest — the documented way to declare a resolution root
that is not a ported module — is exempt from `BaseMapMissing` and claims no namespace.

### R1 — staleness, and how it is detected

Schema 2 adds `sources=` and `files=` to the header: a digest over `(path, sha256(file))` for every
distinct `javaPath` the map attributes a member to. That file list is **derived from the map
itself**, so a consumer recomputes the same digest with nothing to agree on beyond the map. Three
answers, and the difference between the last two is the point:

| answer | meaning | what the consumer does |
|---|---|---|
| `Fresh` | engine and sources match | uses the map |
| `Stale` | engine differs, or the base's Java has changed | **refuses** it, reports, re-derives |
| `Unverified` | no fingerprint, or sources not under this run's resolution roots | uses it, reports |

Falsifier, run as `PortMapSpec` "R1 FALSIFIER": publish a map, change one base member's body,
consult it — `Stale`, naming the change.

### `PortMapTransform` — a §1(b) phase over a base's published output

`new PortMapTransform(maps)`; `Nil` is a total no-op. Implements `PolicySource` (a map matching
NOTHING is reported — the wrong module's map, or one published before a namespace moved) and
`SurfacePolicy` (fingerprinted by module@engine/sources#entries, so two modules handed different
maps do not compare equal).

- **re-points a renamed type** by the same mechanism as `PackageRenameTransform` — owned symbols,
  longest prefix, cut at a separator — with the prefixes taken from the base's `Renamed` entries
  instead of from this module's configuration;
- **reports a call to a `Dropped` member or type**, naming the module that dropped it and quoting
  its record;
- **reports a call into a `body`-flagged member**, which no signature can show.

Overload identity is the hard part and is documented at `select`: a TIR symbol's `fullName` is
`X#m` for *every* overload, so arity is the whole discriminator. Exact arity wins; **no** arity
match means no record rather than the nearest one (the first version attributed a 1-argument call to
the map's 0-argument entry). Its findings are recorded by `PortRun` under the `port-map` check on
every run, `Nil` included.

### What is NOT closed

- **Member SIGNATURES are not compared.** A map's member `upstream` key is the *emitted* signature
  with renames reversed, not the Java one, so a base that retyped a parameter publishes the retyped
  key. Comparing it against a dependent's Java-derived key would need the base's erasure re-derived —
  the thing a map exists to stop doing. So hole 1 is closed for anything that reaches a NAME and
  open for a retyping that changes only a parameter's type.
- **R4 (the diamond) is still untested.** The lookups merge N maps with the nearest base winning,
  but no corpus library has two bases sharing a third.
