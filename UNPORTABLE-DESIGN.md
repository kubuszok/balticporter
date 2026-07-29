# Unportable-construct markers, the failure report, and best-effort emission — design plan

Design only; nothing here is implemented. Governed by CLAUDE.md §1 (no library names in
`core`/`frontend-spoon`/`scala-emit`), §3 (every translation path gets a check when it gets a
translation; walk with `StandardTraversal`), §5 (one change, one measurement), §6 (output
constraints). This plan deliberately reuses the four existing checks' machinery rather than adding
a parallel reporting system; §8 states the OmissionCheck disposition explicitly.

---

## 0. Prior art: dotty best-effort compilation, and where we differ

Reference: https://nightly.scala-lang.org/docs/internals/best-effort-compilation.html — the explicit
inspiration for the emission half of this design.

Dotty's mechanism, mapped onto ours:

| dotty | this design | borrow / reject |
|---|---|---|
| `ERRORtype` on the erroring tree — first-class in the tree and the `.betasty` format, not a side table | `Tree.Approx` term node + `UnportableTag` symbol tag (§1) | **borrow**, same reason: whatever carries the tree/symbols carries the marker for free; a side table has independent lifetime and can silently desynchronize |
| erroring subtree kept, with position and message | `Approx.inner` kept whole, `Unportable` diag + `Origin` | **borrow**, strengthened: our inner is a *complete, typed* term (an approximation we constructed), not a recovery stub |
| with errors, stop after Pickler — downstream phases never handle error trees | full transform pipeline keeps running over marked trees | **reject.** Dotty stops because its error trees are ill-typed and every later phase would need error-handling. Our `Approx.inner` carries a real `TypeRepr`, so phases need no special handling — and stopping would forfeit the whole-program transforms that might *fix* the marked site (§2) |
| error types poison parents through typing | no in-tree poisoning; poisoning happens at the **gate**, per emitted unit (§6) | **reject in-tree propagation**: it destroys the precision the diff and report need, and would block transforms from reaching siblings. A unit containing an `Open` marker is non-deliverable; the marker itself stays exactly as wide as the wrong code |
| `.betasty` in a separate `META-INF/best-effort` dir, distinct header — degraded artifacts never masquerade as real ones | best-effort tree in a separate directory with a banner per file, a `PORT-INCOMPLETE` sentinel, and nonzero exit (§6) | **borrow directly** |
| consumption is opt-in (`-Ywith-best-effort-tasty`), hidden flags, frontend-only when detected | only the measure scripts consume best-effort output; deliverable emission refuses while any `Open` marker survives | **borrow** |

Where we fundamentally differ: dotty is *recovering from errors in its own input*, discovered by its
typer, and can say nothing beyond "this did not typecheck." We are *recording constructs we chose to
translate approximately*, detected **before any Scala compiler sees the output**, and we hold the
original Java. That buys three things dotty cannot have: (i) a taxonomy of *why* (§3), (ii) a
statement of what a hand-porter would write plus ranked remediations against our own seams (§4),
and (iii) **expected-error correlation** — compiling the best-effort tree, every scalac error at a
marked region is *classified*, every error at an unmarked region is an engine gap, and every marked
region that compiles clean is a false-positive candidate (§6.3). Dotty has no analogue of that
third check.

One doctrinal note: PLAN.md:139–141 and `Frontend.scala:5–8` say "there is no best-effort emission
(the anti-omission stance)". This design *refines*, not reverses, that stance: what was forbidden
was **silent** best-effort. A marker that blocks the deliverable, prints on every run, and fences
its region in a separately-labelled artifact is the opposite of silence — it is `OmissionCheck`'s
"deferral must still be VISIBLE" principle (`OmissionCheck.scala:3–14`) applied to emission itself.

---

## 1. The marker in the TIR

### 1.1 The diagnosis value

New file `core/src/main/scala/balticporter/tir/Unportable.scala`:

```scala
final case class Unportable(
    kind: UnportableKind,             // closed engine taxonomy, §3
    detail: String,                   // one sentence from the minting site
    handPort: Option[String],         // what a hand-porter would write, when the minter knows
    remediations: List[Remediation],  // ranked, §4.2
    state: MarkState = MarkState.Open,
)

enum MarkState:
  case Open                                    // must not ship
  case Resolved(byPhase: String, how: String)  // a phase discharged it — inner is now correct

enum Remediation:
  case EngineGap(gapId: String, note: String)              // (a) universal fix, tracked by id
  case Configure(mechanism: String, snippet: String)       // (b) e.g. "Substitutions.dropMethods" + the literal line to add
  case DropAndInject(memberKey: String, sketch: String)    // (b) via Substitutions + inject
  case PlugInRule(sketch: String)                          // (c) a Phase the porting program writes
```

`Configure.snippet` and every other string are built **from `Program` data at runtime** (the FQNs
come from the input being ported), so the engine mechanism stays library-free — same argument that
lets `StaticForwarderTransform` live in core while `LibgdxCoreMigrate.scala:121–128` supplies the
policy. The grep gate of CLAUDE.md §1 continues to hold.

### 1.2 Two attachment points, matching what the codebase already does

**Term-level — a wrapper node in the `Tree` enum** (`Tir.scala`, beside `Opaque` at `Tir.scala:295–297`):

```scala
/** a term translated only APPROXIMATELY: syntactically complete, semantically wrong.
  * `inner` is the approximation; `diag.state == Open` means it MUST NOT ship. */
final case class Approx(inner: Term, diag: Unportable, origin: Origin) extends Term:
  def tpe: TypeRepr = inner.tpe
```

with a smart constructor `Approx.of` that **rejects `Origin.synthetic`** — a marker must point at
real Java (§2.2).

This is *not* `Tree.Opaque`. `Opaque` is a raw string for *valid* generated Scala
(`PanamaFfiTransform.scala:62–63` uses it legitimately; the emitter prints it verbatim,
`TirEmitter.scala:781`). `Approx` carries a structured inner term the transforms can still rewrite,
plus the must-not-compile semantics `Opaque` never had. The two must not be merged.

**Definition/symbol-level — a `SymTag`** (the open extension point, `Tir.scala:67`, precedented by
`Substituted` at `Substitutions.scala:69–73`):

```scala
final case class UnportableTag(diag: Unportable) extends SymTag
```

for findings whose subject is a declaration's *shape*, not an expression: a constructor topology
with no single-primary encoding (tagged on the class symbol), a member whose signature cannot be
expressed (tagged on the `DefDef` symbol). Tags ride `SymbolTable.withTag`/`updated`
(`Tir.scala:304–310`) and survive every phase because phases thread the table, not rebuild it.

### 1.3 Why not the alternatives

- **Side table on `Program`.** Trees have no identity: `StandardTraversal.mapTerm` rebuilds every
  node with `copy` on every phase (`Phase.scala:168–227`), so there is nothing stable to key on.
  Keying on `Origin` collides on synthetic nodes and — worse — a phase that deletes a subtree
  leaves a stale entry that either silently vanishes or falsely blocks. A marker whose lifetime is
  independent of the data it describes is *exactly* the defect class this project keeps hitting
  (CLAUDE.md §3); dotty reached the same conclusion by putting `ERRORtype` in the format, not
  beside it.
- **A `diag` field on every `Tree` case class.** Touches every node and every construction site in
  the frontend and transforms for a field that is `None` almost everywhere. The codebase precedent
  is targeted carriage on the node that needs it (`AnonClass.dropped`, `Tir.scala:236`;
  `Symbol.droppedAnnotations`, `Tir.scala:101–103`), not universal fields.
- **Symbol tags only.** Term sites are many-per-symbol; the fence in best-effort output, the
  expected-error correlation, and the diff all need the *exact expression* pinned.

### 1.4 Interaction with `StandardTraversal` and transforms

`mapTerm` gains one case, written like the `New`/`AnonClass` case whose comment states the law
(`Phase.scala:179–182` — "or a rewrite silently stops at the `new`"):

```scala
case x: Tree.Approx => ph.transformApprox(x.copy(inner = mapTerm(ph, x.inner)))
```

So every phase's hooks reach *inside* the approximation — collections retyping, forwarder
unwrapping, everything — and `Xref.build` indexes through it, so `PortabilityCheck`/`RewriteTrace`
see the inner sites. A phase that pattern-matches for a specific shape (`case a: Tree.Apply`)
simply fails to match an `Approx`-wrapped one and leaves it alone: **the safe default is
marker-preserved, code-untouched**. Erasing a marker requires deliberately matching `Tree.Approx`
and constructing a replacement — which is the *point*: discharge is an explicit act
(`Approx.resolve(a, phaseName, how)` returns `a.copy(diag = a.diag.copy(state = Resolved(...)))`).

Known hazard — shape-matching consumers that look *through* statement lists: `CtorFunnel.superArgsOf`
inspects constructor prologues, and several emitter special forms match `Block`/`Apply` shapes.
Mitigated by a minting rule (wrap the **smallest wrong term**, never a `this(...)`/`super(...)`
delegation head) and by the falsifying experiment in §9 (R1). The check for it is the same
traversal-based one as everything else, so a violation is a loud gate failure, not a silent drift.

The check arrives with the node (CLAUDE.md §3): §2's conservation check and §6's gate land in the
same change that adds `Approx`.

---

## 2. How markers survive transforms

### 2.1 Conservation: markers are never removed, only discharged

State machine instead of deletion. A marker's lifecycle is `Open → Resolved(byPhase, how)` — it
**never leaves the tree** until emission. The emitter renders `Resolved` markers as their bare
`inner` (they are ordinary correct code now) and reports them as "fixed by phase X"; only `Open`
ones block and fence.

`MarkerCheck` (new, `core/.../tir/MarkerCheck.scala`) runs inside `Pipeline.run`
(`Phase.scala:90–94`, which already re-derives the xref between phases, so an inventory walk per
phase is in-budget):

- **Inventory** = multiset of `(origin, kind)` over all `Approx` nodes (collected by a
  `StandardTraversal`-based phase, the exact pattern of
  `OmissionCheck.droppedAnonMembers`, `OmissionCheck.scala:46–61`) plus all `UnportableTag`s.
- **Invariant per phase**: every `(origin, kind)` in the before-inventory appears in the
  after-inventory, with state equal or advanced (`Open → Resolved` only). A marker may vanish only
  if its enclosing symbol was legitimately removed — `Substituted`-tagged
  (`Substitutions.scala:69`) or covered by `dropsMethod` — in which case it is reported as
  *discharged by substitution*, named. Anything else fails the pipeline **naming the phase**, the
  same loud-failure shape as CHECK 1/2 (`LibgdxCoreMigrate.scala:190–192, 233–236`).

This is what "stops a phase from erasing one": not trust, a counted invariant between every pair of
phases, derived from the tree, exactly like `RewriteTrace.check` is derived from the tree rather
than from a list an author maintains (`RewriteTrace.scala:15–18`).

### 2.2 Origin integrity

Two constraints, both mechanical:

1. `Approx.of` refuses `Origin.synthetic` (`Tir.scala:23–24`). Frontend mint sites use the Spoon
   position they already extract (`SpoonTir.scala:887–891`); transform mint sites use the term's
   own origin, which came from Java.
2. The conservation invariant matches on `origin` — a phase that rewrites a marker's origin breaks
   the multiset equality and fails loudly. Origins are therefore immutable-in-practice for marked
   nodes, guaranteeing every surviving marker still points at real Java.

---

## 3. Taxonomy of unportability

Derived from what this codebase has actually produced — the open error table
(LIBGDX-PORT-STATUS.md:74–81), §0.2 (:274–284), the 31 refused constructors (:317–325), the E049/E051
residue (:386–392), and the existing checks. `UnportableKind` is a closed engine enum; a new kind is
an engine change (it comes with mint sites and report text), which is the correct friction.

| kind | witnessed by (doc-comment examples, per CLAUDE.md §1) | remediation class |
|---|---|---|
| `RawGenericConversion` — unchecked conversion whose faithful cast would need the callee's own type variable | raw `new ReadOnlySerializer(){…}` (STATUS :274–281; on the do-not-retry list) | (c) `DropAndInject` the member today; upgrade to (a) if a general encoding is ever found |
| `ContextDependentRawFill` — one Java type rendered differently at declaration vs use | `OrderedMap.keys` / `AssetManager.loaders` (STATUS :59–72) | (a) engine: field reads carry the field's declared rendering |
| `JdkBoundaryFlow` — a JDK collection value flowing into a declaration a phase retyped, needing deep conversion | `getHeaderFields()` → `mutable.Map[String, mutable.Buffer[String]]` (STATUS :80, :283–284) | (b) parameterise `CollectionsTransform` with a boundary-conversion policy (or don't-retype-JDK-fed-declarations rule) |
| `CtorTopology` — constructor graph with no faithful single-primary encoding, or `super(args)` that cannot be promoted/replayed | two-roots-plus-explicit-nilary (STATUS :95–117); `Throwable` cause semantics, side-effectful parent nilary paths, varargs parents (:317–325) | (a) `CtorFunnel` extension for the mechanical shapes; (c) per-class `DropAndInject` for the semantic ones (I/O in a parent constructor is not the engine's to decide) |
| `PlatformHostileApi` — JVM-only API in emitted code | `PortabilityCheck.jsAndNative` (`PortabilityCheck.scala:32–56`) | (b) `Substitutions` / `StaticForwarderTransform` / injected shim — this kind's remediations are computed from the xref shape (§4.2) |
| `ReflectiveLookup` — name→class/member resolution at runtime | the `forName` re-pointing (`LibgdxCoreMigrate.scala:110–113`) | (b) `ClassTableTransform(Map)` |
| `OverloadDivergence` — Java and Scala resolve a call to different overloads | `setRegion(int,int,int,int)` vs `(float,…)` (STATUS :391–392) | (a) engine: emit the widening Java performed |
| `FrontendBlindSpot` — Spoon under noClasspath produces a node the frontend cannot classify | the inherited-field write E049 (STATUS :386–390, do-not-retry :484–488) | (a) frontend; marker minted where classification fails instead of guessing |
| `UnmodeledConstruct` — a member/statement kind the frontend cannot carry | `AnonClass.dropped` kinds (`SpoonTir.scala:646–659`); enum-constant body fields (STATUS §0.3) | (a) frontend coverage |
| `AnnotationResidue` — annotation or argument that would not translate | `@Target({...})` residue (STATUS :176–178) | (a) |

Note what this table encodes: most kinds are (a) or (b). CLAUDE.md §1's "reach for (c) only after
establishing the mechanism cannot be shared" is built into the report — (c) suggestions rank last
unless the kind is inherently semantic (`CtorTopology`'s I/O cases).

---

## 4. The report

### 4.1 Assembly — one document, five feeds, zero new check machinery

New `core/.../tir/PortReport.scala` takes what already exists and one new feed:

1. `OmissionCheck.check` findings (`OmissionCheck.scala:20–21`)
2. `PortabilityCheck.inEmittedCode` + `inInjectedSource` (`PortabilityCheck.scala:95, 103`)
3. `RewriteTrace.check` mismatches (`RewriteTrace.scala:61–62`)
4. the substitution CHECK 1/2 results — lifted from inline code in
   `LibgdxCoreMigrate.scala:190–237` into a callable `SubstitutionCheck` in core so the report can
   include them (the migration keeps its `sys.error` behaviour)
5. **new**: the marker inventory (§2.1) with states and remediations

Output, written by the *porting program* to a directory it chooses (engine takes `Path`s;
recommended `port-report/run-latest/`):

- `report.md` — the operator document below
- `findings.tsv` — one line per finding, sorted by (kind, path, line, owner); the machine feed for
  the diff (§5) and for agents. Deterministic: sorted, no timestamps, no absolute paths (paths
  relative to the source root).

Finding identity: `id = short-hash(kind | javaPath | ownerFullName | detailDigest)`; line number is
carried but is a tiebreak, not part of the id, so a whitespace edit upstream does not orphan a
baseline entry. An upstream bump (PLAN.md §8) resets the baseline anyway.

### 4.2 Suggestion computation

Two layers, both in core, both data-driven:

- **Mint-site templates**: each kind carries default remediations (§3 table), rendered with the
  finding's own symbols.
- **`Remediator` post-pass**: pattern-matches findings against the engine's seams using the xref.
  Example: a `PlatformHostileApi` finding whose call sites all go through one static wrapper type
  (visible via `Program.usages`, `Tir.scala:349–354`) yields a ready
  `StaticForwarderTransform.Forwarder(wrapper = <fqn>, receiver = <fqn>, members = <set>)` snippet
  — the exact shape the operator pastes into their migration program. All FQNs come from the
  program being ported; the engine names only its own mechanisms.

### 4.3 Sample (operator view, `report.md`)

```markdown
# Port report — 2026-07-28 run `run-latest`   DELIVERABLE: NO (2 open markers)

translated: 603/605 units clean · 2 units carry open markers · 31 findings total
fixed since baseline: 1 · still open: 2 · new: 0

## Open markers — the port will not ship until each is resolved or configured

### BP-7f3a2c  RawGenericConversion   ui/Skin.java:602  (in com.badlogic.gdx.scenes.scene2d.ui.Skin#getJsonLoader)
Java:
    json.setSerializer(TintedDrawable.class, new ReadOnlySerializer() {
        public Object read (Json json, JsonValue jsonData, Class type) { … }
    });
Why unportable: the raw anonymous class fixes its own type; Scala infers `ReadOnlySerializer[Nothing]`
and no explicit argument makes the `Object`-erased body override. Java accepted this as an unchecked
conversion; the faithful cast would need the callee's own type variable (measured refusal —
LIBGDX-PORT-STATUS.md do-not-retry).
A hand-porter would write: a typed serializer `new ReadOnlySerializer[TintedDrawable] { def read(…): TintedDrawable = … }`
adjusting the body's erased signature to the instantiated one.
Suggested remediations (ranked):
  1. (c) DropAndInject: `Substitutions.dropMethods += "…ui.Skin#getJsonLoader(FileHandle)"`,
     inject `overrides/…/SkinJsonLoader.scala` (sketch included in findings.tsv)
  2. (a) EngineGap ENG-0007: general encoding for raw anonymous subclasses of generic SAM-like types — none known; see do-not-retry before attempting

### BP-91d40e  JdkBoundaryFlow   net/NetJavaImpl.java:196  (in …HttpResponse anonymous body)
…
Suggested remediations (ranked):
  1. (b) Configure CollectionsTransform: boundary conversion for `java.util.Map[String, java.util.List[String]]`
     at a retyped declaration — add `Boundary(from = …, to = …)` to the transform's parameters
  2. (c) PlugInRule: site-specific wrapper phase

## Resolved this run
BP-2231aa CtorTopology …RegionInfluencer — resolved by phase `ctor-funnel` (explicit-nilary promotion)

## Standing findings from the other gates (unchanged machinery)
omissions: 31 × super(args) dropped … · portability: none · signatures: clean · substitutions: 10 verified removed
```

Location, Java excerpt (read at report time from `Origin.javaPath` — the frontend's source root is
known to the porting program), the why, the hand-port sketch, and ranked remediations: each item of
the brief's requirement 4 has a concrete slot.

---

## 5. Semantic diff between runs

"Semantic" means *aligned by symbol, not by line*, at both stages. All of it is deterministic:
sorted output, digests over canonical text, no clocks.

### 5.1 Stage A — Java→IR

A canonical TIR serialization per unit: `TirPrinter.canonical(unit, program)` — a deterministic
pretty-print in which **symbols render as `fullName`, never `SymId`** (`SymId` is interning-order
dependent, `Tir.scala:29–33`, so raw ids must never reach persisted artifacts), `ParamRef` renders
by binder-relative index, fields in declaration order. Persist per unit:
`unit fullName → sha256(canonical)` — twice, once for the frontend's raw output and once
post-pipeline, so a change localizes to *frontend* vs *phase* immediately. Full canonical texts go
to the scratch/report dir for drill-down on the units whose digests changed.

### 5.2 Stage B — IR→Scala

The emitted source is already deterministic text. The diff unit is the **member**:
`TirEmitter.emitUnit` (`TirEmitter.scala:35–40`) assembles per-member strings inside `classDef`; it
records `(unitFqn, memberFullName, startLine, sha256(memberText))` as it joins them. Two artifacts:

- `members.tsv` — the digest table, sorted; run-over-run comparison lists members
  added/removed/changed, which is the blast-radius answer at source level.
- `srcmap.tsv` — member → emitted line range → Java `Origin`. This is the piece that makes scalac
  output attributable (§6.3).

> **STATUS 2026-07-29 — BUILT.** `core/.../tir/SrcMap.scala` is the model, the artifacts and the
> lookup; `TirEmitter.emitUnit` records. Measured on libGDX core: **19 247 members over 594 units,
> 0 unlocatable**; on the test port, 290 over 29.
>
> Four decisions worth keeping:
>
> - **Positions are recovered by SEARCH, not by threading an offset.** One wrapper — `memberStat` —
>   remembers the exact string each class-body member rendered to, and `srcMapOf` locates those
>   strings in the finished unit. Slots are reserved PRE-ORDER, so a nested class precedes its own
>   members, and the cursor advances by one character past each match, so a member is still findable
>   inside its owner while two textually identical siblings resolve to two positions. The
>   alternative — a cursor parameter on ~40 rendering methods — could not have been added without
>   re-measuring the port; this one **cannot change a byte of output**, and a test asserts exactly
>   that (`SrcMapEmitSpec`). A member the emitter renders but cannot find again is COUNTED and
>   printed, never dropped: a map with silent holes attributes an error to the wrong member.
> - **The member key carries the parameter types.** `owner#name(T1,T2)`, the form
>   `Substitutions.dropMethods` already uses. Java overloading puts eight `encode`s in one class and
>   a key that merges them cannot say which one changed.
> - **A class-body statement with no symbol gets an ordinal** (`owner#<stmtN>`). This is not an edge
>   case: `TestFrameworkTransform` lowers every `@Test` method to a bare `test("…"){ … }` statement,
>   so without it a test file would map only at unit granularity — and the amendment below is
>   precisely about anchoring test failures. The statement's own `Origin` is what actually locates
>   the Java; the ordinal only has to be unique.
> - **The Java path is relativised against a root DERIVED FROM THE PORT**, not against
>   `balticporter.reportPathRoot`. A unit's origin ends in its own package path, so stripping that
>   suffix *is* the source root — no flag, no script, same answer from any checkout. This is
>   CLAUDE.md §4.6's last paragraph applied at the point where it was still open: `CheckReport`
>   anchors on a flag only the measure scripts set, and `SrcMap` deliberately does not repeat it
>   (the flag remains the fallback for a unit whose emitted FQN no longer matches its origin — a
>   package rename).

### 5.3 Baseline and classification

`port-report/run-latest/` is overwritten every run; `port-report/baseline/` is promoted **only by
an explicit `--accept-baseline`** (golden-test discipline — an agent iterates against a fixed
baseline and promotes when a step is accepted, which is exactly the "am I moving in the right
direction" loop of the brief). Classification is computed from `findings.tsv` keyed by finding id:

- **fixed**: `Open` in baseline; now `Resolved`, discharged-by-substitution, or absent with its
  unit's members changed (the change is shown)
- **still broken**: `Open` in both
- **newly broken**: `Open` now only

plus, independently, the member-digest delta — so "you touched 3 members you did not intend to"
surfaces even when no marker is involved. This directly serves CLAUDE.md §5: the cost of "two
changes measured together" drops, because the blast radius of each change is visible before any
compile cycle.

---

## 6. Best-effort emission

### 6.1 One emitter, one flag, three effects

Not a second code path. `TirEmitter` gains `bestEffort: Boolean` affecting exactly:

1. **`Approx` rendering.** `Open` markers render as the inner term inside deterministic comment
   fences: `/*<bp:BP-7f3a2c>*/ inner /*</bp>*/`. Comments cannot change program shape; §6
   constraints (no `Nothing` casts, `args*`) are untouched because the inner term is whatever it
   already was. `Resolved` markers render bare in *both* modes.
2. **Banner.** Each file with open markers gets a header block: this file is best-effort; these
   regions (finding ids + Java origins) are not valid Scala; these regions were fixed since
   baseline (from §5.3); these are still broken.
3. **Destination + sentinel.** Best-effort output goes to a separate directory chosen by the
   porting program (dotty's `META-INF/best-effort` move), with a `PORT-INCOMPLETE` sentinel file
   listing the open finding ids, and the migration exits nonzero — same loudness as CHECK 1/2.

In deliverable mode (`bestEffort = false`) the gate runs first: **any `Open` marker → the
deliverable tree is not written** (`sys.error`, like `LibgdxCoreMigrate.scala:192`), and the report
says why, per finding. When markers reach zero, best-effort output minus fences and banner is
byte-identical to deliverable output *by construction* (same emitter, same tree) — and §9 (R5)
keeps a check asserting exactly that, so the mode cannot rot into a divergent path.

### 6.2 Coexistence with today's workflow

`gdx_measure.sh` currently compiles a tree that contains known-wrong code — it has been running in
what this design calls best-effort mode all along, just unlabelled. After this change the scripts
point at the best-effort directory explicitly; nothing else about the measurement loop moves. The
"deliverable" claim becomes a *positive statement the gate makes* (zero open markers + four checks
clean), rather than the absence of complaints.

### 6.3 Expected-error correlation — the check dotty cannot have

After scala-cli runs over the best-effort tree, a small step joins `/tmp/gdxmeasure.txt` against
`srcmap.tsv` (§5.2):

- error at a **marked** region → *classified*: expected, already carrying remediation; counted per
  finding
- error at an **unmarked** region → *engine gap*: the triage queue, with member + Java origin
  attached automatically
- **marked region with no error** → *false-positive candidate*: the marker is over-cautious or the
  approximation is accidentally right — both worth knowing (an accidentally-compiling wrong
  approximation is precisely the silent-defect class of CLAUDE.md §3)

This turns the compile step from a number into a triaged list, and it is the piece that pays first
(§9).

> **STATUS 2026-07-29 — BUILT, and EXTENDED to the behavioural gate.**
> `core/.../tir/Correlate.scala` is the join; `CorrelateMain` is the command the measure scripts
> run after the compiler and the test runner. Three lanes, not two: `Approx` (marked), `EngineGap`
> (located), and **`Unmapped`** — a diagnostic in a file the source map does not cover (injected
> Scala, a runtime shim, a dependency). Folding `Unmapped` into either of the other two would be a
> lie in both directions: an injected shim's error is not an engine gap, and it is not a
> false-positive marker either.
>
> Demonstrated by breaking one universal rule on purpose (`override` no longer emitted): **916
> errors, 916 located to a member and a Java line, 0 unmapped** —
> `E164 … FileHandle#equals(Any)  [com/badlogic/gdx/files/FileHandle.java:660]`.
>
> ### 6.4 The amendment — TEST-failure correlation (LIBRARY-READINESS.md §2.4)
>
> §6.3 as written reads the compiler. CLAUDE.md §4.4 lists ten Java forms that translate to *valid*
> Scala meaning something else, **none of which moves a compile-error count**; every one was found
> by running the ported tests. So the same join runs over the test runner's output.
>
> - MUnit's console form is parsed into per-test outcomes with their stacks.
> - A failure is anchored on the **first stack frame that lands in ported code**, main-scope
>   preferred over test-scope, with the quality of the anchor RECORDED rather than assumed:
>   `main-frame` (threw inside the library — exact, the §4.4 case), `test-frame` (a plain assertion
>   mismatch: names where the failure was OBSERVED, not where the wrong value was computed),
>   `assert-site`, `suite`, `none`.
> - Pass/fail sets are persisted (`tests.tsv`) and diffed the way §5.3 diffs findings:
>   newly-failing / newly-passing / still-failing, plus **tests in the baseline that did not run at
>   all** — a suite that stopped running is not a suite that passed.
> - Each failing test is joined against the member-digest delta, so a **newly-failing test whose
>   anchored member also changed digest** is called out. That is the highest-value signal the engine
>   produces, and it is what recovers the half a `test-frame` anchor cannot give.
> - **Expected failures are DATA**, read from `baseline/expected-failures.tsv` (`suite`, `test` —
>   `*` for a whole suite — `reason`). `core` may not name a ported library (CLAUDE.md §1), so the
>   four deliberate `Json.fromJson` failures cannot be known to the engine. An expected failure that
>   starts PASSING is reported too: a substitution that began working is news.
>
> Demonstrated by breaking a second universal rule — the `inline val` of §4.4's `static final`
> row — which produces **zero scalac errors** and is invisible to every other gate in this
> repository. See §9's Stage 1 status block for the numbers.

---

## 7. Minting sites

Where markers come from, in order of adoption:

1. **Frontend refusal points.** `SpoonTir.unsupported` throws today (`SpoonTir.scala:887–891`,
   `Frontend.scala:5–8`); each site where a syntactically-complete approximation exists becomes a
   mint site (throwing remains for constructs with no approximation at all). The documented
   refusals — the raw-anon shape, unresolvable coercions where the engine currently emits the
   uncast term knowing scalac will object — are the first candidates, adopted **one at a time,
   measured** (CLAUDE.md §5), validated by §6.3's false-positive check.
2. **Transform refusal points.** e.g. `CollectionsTransform` detecting a JDK-fed retyped
   declaration it cannot convert deeply mints `JdkBoundaryFlow` instead of leaving the mismatch for
   scalac.
3. **Gate-derived tags.** `CtorTopology` markers for the refused constructors are minted *by the
   gate from `CtorFunnel.Plans`*, not by the frontend — preserving the invariant that this
   knowledge is derived from the same decision the emitter uses and can never disagree with it
   (`OmissionCheck.scala:80–94` states why; that property must not be traded for marker uniformity).

The engine is *not* expected to predict every scalac error — §6.3's unmarked-error lane exists
precisely because scalac remains the outer oracle. Markers cover what the engine *knows* it
approximated; the correlation covers what it didn't know.

---

## 8. Disposition of the four existing checks — explicitly

- **`OmissionCheck` — extended, not subsumed, not left alone.** Its findings flow into `PortReport`
  as `Unportable`-shaped entries (kind `CtorTopology` / `AnnotationResidue` / `UnmodeledConstruct`)
  so the operator sees one document. Its *derived* check (`droppedSuperArgs`,
  `OmissionCheck.scala:80–94`) stays derived from `CtorFunnel.Plans` — converting it to
  frontend-minted markers would reintroduce the drift-vs-emitter risk its doc comment exists to
  prevent. The *frontend-recorded* carriers (`Symbol.droppedAnnotations`, `Tir.scala:101–103`;
  `AnonClass.dropped`, `Tir.scala:236`) are natural `UnportableTag`s and migrate to the marker in
  Stage 3, deleting the bespoke fields — but only after the tests run (§9).
- **`PortabilityCheck` — unchanged**, feeds the report; its findings get `Remediator` suggestions
  (§4.2). Its rules stay a check, not markers: they are *policy about targets* (Scala.js/Native), not
  translation approximations, and a library targeting only the JVM legitimately runs with them waived.
- **`RewriteTrace` — unchanged**, feeds the report. It is the completeness guarantee for
  signature rewrites and is orthogonal to markers.
- **Substitution CHECK 1/2 — lifted** from `LibgdxCoreMigrate.scala:190–237` into a core
  `SubstitutionCheck` so all porting programs (and the report) share it; behaviour identical.

---

## 9. Migration path from today, staged against the 6 + 4

Ground truth: 6 typer errors in `gdx/src` (root causes enumerated at LIBGDX-PORT-STATUS.md:74–81),
4 more with `gdx/test`, 221 tests never run, and the RefChecks wave still ahead (STATUS §0.1). The
brief's constraint: this design must make reaching *tests running* faster, not become a detour.

**Stage 1 — report + diff over what already exists. No TIR change. This is the stage that pays
first.**
(a) member digests + `srcmap.tsv` in `TirEmitter` (§5.2); (b) error correlation in
`gdx_measure.sh`/`gdx_test_measure.sh` (§6.3, initially with an empty marker set — every error lands
in the "engine gap, auto-located" lane); (c) `PortReport` assembling the four existing checks +
`SubstitutionCheck` lift (§4.1, §8); (d) baseline/run-latest persistence + classification (§5.3).

> **STATUS 2026-07-29 — (c) partially BUILT, (d) BUILT. (a) and (b) still open.**
>
> `core/.../tir/CheckReport.scala` is the assembly and persistence layer. Each of `OmissionCheck`,
> `PortabilityCheck` (twice — all-references and emitted-only — plus the injected-source scan) and
> `RewriteTrace` records its COMPLETE result; the caller's `take(20)` truncation now applies only
> to the terminal render. Output per port under `port-report/<main-class>/`:
> `run-latest/{findings.tsv,counts.tsv,report.md,diff.txt,subject.txt}` and a promotable
> `baseline/{findings.tsv,counts.tsv}` (`scripts/port_baseline.sh accept <port>`). `findings.tsv`
> is sorted, path-relative and clock-free, so the diff is stable; the finding id excludes the line
> (R7) and gains a `/n` suffix when a member has several findings that differ only by line, which
> the line-free id would otherwise merge. `subject.txt` is the `before->after` fragment CLAUDE.md
> §5 asks every commit subject to carry.
>
> Not built: the `SubstitutionCheck` LIFT. CHECK 1 and CHECK 2 are still inline filesystem code in
> `LibgdxCoreMigrate.scala:203/240`, so the substitution result is the one of the four that reaches
> stdout but not `findings.tsv`. Lifting it needs §1.2's `PortRun`, or at minimum an edit to the
> migration program.
>
> Recording is invoked FROM the checks rather than from an orchestrator, because there is no
> orchestrator (§1.2) and check invocation is copy-paste per migration program. It is gated off
> unless the run supplies `balticporter.root` or a report dir, so a check is still a pure function
> everywhere else. When `PortRun` lands, the `record` calls move into it.
>
> **Measured coverage observation, recorded because it is a real limit of the four checks:** with
> `balticporter.skipPhases=*` (the whole transform pipeline off) on libGDX core, all four check
> counts are UNCHANGED — 46 / 139 / 67 / 2 / 0. The checks measure frontend and emission facts; no
> transform in that pipeline moves any of them. So the diff layer cannot, today, detect a
> transform regression. That is an argument for Stage 1(a)'s member digests, which would.

> **STATUS 2026-07-29 (second pass) — (a) BUILT, (b) BUILT, and the LIBRARY-READINESS.md §2.4
> AMENDMENT BUILT.** Stage 1 is complete apart from the `SubstitutionCheck` lift, which still needs
> `PortRun`.
>
> | piece | where | measured |
> |---|---|---|
> | (a) member digests + `srcmap.tsv` | `core/.../tir/SrcMap.scala`, `TirEmitter.emitUnit` (§5.2) | 19 247 members / 594 units (core), 290 / 29 (tests), **0 unlocatable** |
> | (b) scalac-error correlation | `core/.../tir/Correlate.scala` + `CorrelateMain`, wired into `scripts/gdx_measure.sh` (§6.3) | 916/916 located in the deliberate-breakage run; 0/0 in the clean one |
> | AMENDMENT: test-failure correlation | same files, wired into `scripts/gdx_test_measure.sh` (§6.4) | 221 outcomes parsed; 4 expected failures classified from data; a deliberate §4.4 regression located to `Matrix4#<stmt1>` [`Matrix4.java:91`] with **zero scalac errors** |
>
> **The coverage limit above is now closed, and the way it closed is the thing to remember.** After
> the second deliberate breakage was reverted, `run-latest/members.tsv` compared **byte-identical**
> to `baseline/members.tsv` — which is the port proving, without a compile and without a test run,
> that the emitted output is back where it started. The same file said "1 740 members changed"
> while the breakage was in, and "17 member(s) of `Matrix4` changed" beside the failing test. No
> check count moved at any point in that sequence.
>
> **What Stage 2 needs from this, and nothing more:**
>
> - a `markers.tsv` of `unit<TAB>member` lines. `Correlate.locateErrors` already takes that set and
>   routes matching errors to the `Approx` lane; `CorrelateMain --markers` already reads it. The
>   marker side has to WRITE it — from the `Approx`/`UnportableTag` inventory, keyed the way
>   `SrcMap` keys members (`owner#name(T1,T2)`, ordinal for an unnamed statement).
> - nothing else. The false-positive lane §6.3 asks for ("marked region with no error") is one
>   set-difference over the same two inputs and was left unbuilt only because the marker input does
>   not exist yet.
>
> **What is NOT built, deliberately:** no `Unportable`, no `Tree.Approx`, no traversal case, no
> `MarkState` conservation — LIBRARY-READINESS.md §2.4 defers all of it.
Payoff against the 6+4: every remaining error is automatically attributed to (member, Java origin)
and every engine change's blast radius is visible per member *before* the compile cycle — the
per-iteration cost of the endgame drops now, and the machinery is in place for the RefChecks wave,
which is where a step-up in error count is *expected* (STATUS §0.1) and where hand-triage would be
most expensive.

**Stage 1.5 — diagnosis flags (BUILT 2026-07-29, not previously in this plan).**

LIBRARY-READINESS.md §2.3/§2.5 asked for three ad-hoc techniques to become first-class. Two are
now flags read by `balticporter.tir.DebugFlags`, and one is a printer:

| flag | answers |
|---|---|
| `balticporter.skipPhases=<name>[,…]` or `*` | "is this phase even responsible" — one run, no source edit. An unmatched name is REPORTED with the pipeline's actual phase names, so a typo cannot masquerade as "the phase changed nothing". |
| `balticporter.dumpTirBefore` / `dumpTirAfter=<phase>|*`, `balticporter.dumpOnly=<fqn>` | "what did the tree look like either side of that phase" |
| `balticporter.tracePhases=true` | "did it run, and did the program's size move" |
| `balticporter.traceNode=<Kind>` | construction provenance via `TirTrace.mint`. MECHANISM ONLY — no construction site is wired, because every site of interest is in `SpoonTir`/`TirEmitter`. |

`TirPrinter` is the rendering: total over `Tree`/`TypeRepr` (an unhandled node is an exhaustivity
warning, not a silently unprinted subtree), symbols by `fullName`, types in surface syntax, and a
`canonical` style with no `SymId` and no origin that is the input to `TirPrinter.digest` — i.e. the
substrate Stage 3(a)'s Stage-A diff needs.

**Where a flag is read from, and why it is not an environment variable.** `sbt -client` talks to a
long-running server, and the migration runs in a JVM FORKED from it whose `-D` options come from
`build.sbt`. Neither an exported variable nor a `-D` on the operator's command line reaches it. So
`DebugFlags` resolves, in increasing precedence: `<root>/.balticporter/run.properties` (written by
a measure script), `<root>/.balticporter/debug.properties` (hand-written, wins), then system
properties (for a direct `java` run, a test, or a main class that sets one before it builds a
pipeline — which is how `DebugEmit` forwards `--dump-after`). This is CLAUDE.md §4.6's marker-file
rule, generalised; §4.6 itself should gain the flag names.

**Stage 2 — the marker.** `Unportable` + `Tree.Approx` + `UnportableTag` + traversal case +
conservation check + emission gate + fences/banner/sentinel (§1, §2, §6.1), landing *together* as
one change (CLAUDE.md §3: check and translation arrive together). Then mint sites one at a time
(§7), each measured against §6.3's precision check. The known 6 convert from status-doc prose into
machine-carried findings with remediations; the substitution-suggestion lane (`DropAndInject` with a
ready snippet) is the shortcut for the residue that is genuinely (c) — e.g. the raw-anon site,
whose fastest correct resolution is a per-library override, not more engine work.

**Stage 3 — deferred until after the suite runs.** (a) Stage-A canonical TIR diff (§5.1) — high
build cost, debugging-breadth payoff, not on the path to running tests; (b) migrating
`droppedAnnotations`/`AnonClass.dropped` to `UnportableTag` (§8); (c) `Remediator` xref-driven
suggestion mining beyond the mint-site templates (§4.2). Deferring these is deliberate: CLAUDE.md
§3 — running ported tests is worth more than any further tooling polish.

---

## 10. Honest risks, with the cheapest falsifying experiment for each

- **R1 — Shape-matcher blindness.** An `Approx` wrapper makes `CtorFunnel`'s prologue inspection or
  an emitter special form miss its shape, silently changing funnel/emission decisions.
  *Falsifier*: a test migration that wraps **every** body term in `Approx` with a no-op `Resolved`
  diag and asserts (i) emitted output byte-identical, (ii) `CtorFunnel.Plans` identical. One corpus
  run; any diff names the blind matcher.
- **R2 — Marker flood.** Instrumented refusal points mint markers for sites that actually compile
  fine; the deliverable gate blocks forever and the report drowns. *Falsifier*: adopt one mint site
  (§7 step 1), run §6.3 — the marked-but-clean count is the false-positive rate, measured before
  the second site is added.
- **R3 — Nondeterministic artifacts.** Hash-order iteration or absolute paths leak into
  `findings.tsv`/digests, poisoning every diff. *Falsifier*: run the migration twice, `diff -r` the
  two `run-latest` trees — must be byte-identical (the determinism contract PLAN.md §3.3 already
  demands of passes, applied to the report).
- **R4 — Conservation false-positives.** Legitimate deletions (`Substitutions.dropMethods`,
  `CtorFunnel` replay moving statements across constructors) trip the invariant. *Falsifier*:
  inject synthetic markers into members known to be dropped/replayed in the libGDX run and confirm
  each is reported *discharged*, not *erased* — before any real mint site depends on it.
- **R5 — Best-effort rot.** The flag grows semantics until best-effort and deliverable output
  diverge. *Falsifier (standing check, not one-off)*: at zero open markers, strip fences and banner
  from best-effort output and byte-compare with deliverable output on every measured run.
- **R6 — §1 drift in suggestions.** Remediation templates accrete library knowledge into core.
  *Guard*: templates may interpolate only strings drawn from `Program`/manifest data; the CLAUDE.md
  §1 grep gate already catches a literal, and reviewing `Remediator` is in the Auditor's remit.
- **R7 — Finding-id churn.** Upstream line shifts orphan baseline entries. *Accepted, bounded*: id
  excludes the line (§4.1); an upstream bump is a declared `bump` (PLAN.md §8) that resets the
  baseline anyway. *Falsifier*: insert a comment line at the top of one Java file, re-run, and
  count orphaned ids — must be zero.
- **R8 — The design itself as a detour.** The real risk named by the brief: building this instead
  of fixing the 6+4. Mitigation is the staging in §9 — Stage 1 contains no TIR surgery, reuses the
  existing checks, and each of its four pieces independently shortens the current fix loop; if
  Stage 1 does not measurably reduce iteration cost on the next two error fixes, stop and
  re-evaluate before Stage 2.
