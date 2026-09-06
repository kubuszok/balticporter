# Baltic Porter — working rules

Baltic Porter is a **framework for porting Java libraries to Scala 3**, not a program for porting
one library; libGDX spearheads because it surfaces the most issues per file. **If a decision makes
the engine better at libGDX and worse at the next library, it is the wrong decision.** Each section's
details live in a path-scoped file under `.claude/rules/` (the `details:` line); ids in parentheses
are `ENGINE-LIMITS.md` entries carrying the measured evidence.

---

## 1. Universal vs library-specific — the rule that governs every phase and plugin

Decide first **which of three kinds** a rule is. Get this wrong and the framework silently becomes a
libGDX porter.

### (a) Universal — belongs in the engine, unparameterised

A fact about **Java and Scala**, true of every codebase (array covariance, interface constants,
unchecked raw conversion). Lives in `api` / `engine` / `frontend-spoon` with no configuration.

### (b) Reusable mechanism, per-library policy — belongs in the engine, PARAMETERISED

Same mechanics for every library; WHICH attributes/types/references get modified is a constructor
parameter. **An empty/default parameter must make the phase a no-op.** Current phases:

| phase(params) — mechanism | policy |
|---|---|
| `ClassTableTransform(redirects, scope)` — re-point a reflective name lookup at an explicit table | which method → which table, `RuleScope`; disjoint scopes compose, overlapping refuse |
| `StaticForwarderTransform(List[Forwarder])` — a wrapper's statics are members of argument 1 | which wrapper, receiver, members |
| `Substitutions` — do not emit these types/methods; inject this Scala instead | which ones, replacement sources |
| `CollectionsTransform(scope, families, familyScopes, retarget, retargetRewrites, retargetRewritesByDesc, retargetTypeArgs, retargetCoercions, reifiedCarriers, reflectiveSinks, retargetIndexedFields)` — retype collections, API-map call sites; JDK table is a §1(a) constant | which declarations, which extra families (per-entry `RuleScope`, D12), which library types retarget (java FQN → scala FQN that extends the source), per-member rewrites (`Rename`, `BoolDispatch`, `Construct`, `ForEach`, `Collect`, `Chain`, `FieldWrite`, `DropWrite`, `IndexedField`, `Template`), type-arg maps, coercion templates, reified carriers (K20), reflective sinks (K21), indexed field rewrites keyed by (source, field) to avoid key collision with method rewrites. `MergeablePolicy`: independent keys union, same source/different target refuses |
| `PrimitiveToOpaqueTransform(OpaqueSpec)` — seed, propagate along pure-move flows, retype (scalar and `Array[Prim]`, one container deep), coerce at the boundary | which primitive, name, mint site, seed FQNs as an exact `Set[String]` (O4), scope. A formal on a callee this run does not emit is read off the BASE'S PUBLISHED PORT MAP (`RunScope.baseMemberUpstream`), never re-derived (O8) |
| `PortabilityCheck(targets)` — match a rule against every external symbol, report each site | WHICH BACKENDS (`PortManifest.targets`); the rule list is derived from them |
| `ApiParityCheck(ParityRef)` — parse both sides with scalameta, classify divergences by family | WHICH hand-port tree(s) (`PortManifest.parity`); `upstreamMarkers` decides which files are parties; empty = no-op |
| `MemberRenameTransform(renames)` — rename over the whole override component or refuse | which members, what name (symbolic emits `@targetName`) |
| `NullabilityTransform(annotations, target, scope, nullableMembers)` — move nullability into the type, strip annotation, coerce at seams, rewrite `== null` | which annotations, target shape (`Union`/`Named`/`OptionTarget`), `RuleScope`, `nullableMembers` exact FQNs (K13.6); `MergeablePolicy` union |
| `BeanPropertyTransform(pairs, targets, scope)` — accessor pair → scala property over the override component, derive java-convention pairs in scope | explicit pairs, derivation scope; `Only(Set.empty)` = no-op; configured key wins |
| `NullaryArityTransform(scope)` — drop `()` from getter-like nullary methods, whole-or-none per component | `RuleScope`; `Only(Set.empty)` default (it MINTS an arity) |
| `ClassToTraitTransform(specs)` — abstract class → trait, ctor params → abstract vals, direct subclasses gain `override val` | `Map[fqn, List[ParamMapping]]`; `SurfacePolicy`; differing mappings refuse |
| `AddMembersTransform(members)` — splice hand-written members at the end of a class body, or of its COMPANION (`MemberSpec.static` — a spliced member has no symbol, so its home rides on the node) | which owners, which members, which home; `Only(Set.empty)` default; same owner+name+home refuses |
| `RegistryTransform(entries, facadeMembers)` — reflective instantiation becomes a `Class`-keyed registry: rewrite the call, MINT the table/`register`/`create` at the placement, elide the handler the rewrite made dead | which callee, `RuleScope`, `Placement.Member`/`Object` (the three names, `T`'s bound), `seeds`, `handles`, `miss` (`Null`/`Throw`/`JvmReflect(onFailure)`, the non-JVM cost COUNTED); `Only(Set.empty)` default (it MINTS); independent callees union, one placement slot twice refuses (P10) |
| `ElementWitnessTransform(witness, members, subjectTypes, dropBound, boxedWitness, scope)` — an array whose element is a TYPE PARAMETER is allocated/copied/cleared through a type-class WITNESS, java's implicit `Object` bound dropped and CLOSED under application, raw constructions completed, `eq`/`ne` operands ascribed | which type class, its member names, which declarations at which type-parameter indexes, whose bound goes, the boxed witness a declaration that cannot be threaded takes instead (CT7); `Only(Set.empty)` default (it MINTS a clause); independent subjects union, one subject at two index lists refuses (K41) |

**Obligations of every (b) phase** (details: `.claude/rules/phases.md`):

- **A no-op parameter still needs its DEFAULT chosen against what the phase did BEFORE it had one.**
  A RETYPING phase takes a `RuleScope` (`Everywhere(except)` / `Only(include)`, cut only at a
  `Symbol.fullName` separator) defaulting to `Everywhere(Set.empty)`; a phase that ADDS declarations
  defaults to `Only(Set.empty)`. `TransformFactory.scopeOf` takes the phase's own default.
- The scope, and any parameter a reader could point at in the emitted signatures, is SURFACE: the
  phase implements `SurfacePolicy`, or two configurations fingerprint EQUAL by NAME (CT9). **An
  empty key contributes NO `policy=` segment** (the fingerprint no-op rule).
- **Every seam a scope creates is COUNTED**, read through the DECLARATION never node types, at
  external callees JDK or third-party alike (K15); a formal that stays JAVA is read LITERALLY; a
  coercion may never precede a rewrite of the same call; the residue's `Rewrite.accountedBy` lanes
  are declared and `rewrite-callsites` reports a phase naming none (K5.10).
- An obligation the engine's own translation created is the PHASE's, never a port's drop key (K5.7);
  a re-parenting owes the `override` modifier it moved (K28); a repair asks whether the languages
  disagree HERE; a substitution or synthesis matches a member by SIGNATURE, never by bare name.
- A class a FRAMEWORK instantiates has no caller to change: a constructor-changing phase owes
  *takes the value without a parameter* (CT7); a refusing escape hatch is asked *is there no value,
  or no NAME?*, indexed by where the mechanism ATTACHED (PROGRESS §10.8.11).
- Collections/retarget obligations (boundary directions, the third population, reified positions
  K18/K20/K21, egress sinks): `.claude/rules/collections.md`.

### (c) Genuinely library-specific — a SEPARATE, PLUGGED-IN RULE

Knowledge that applies to ONE library is a separate rule the porting program plugs in; it does not
go in the engine at any level of generality. A (c) is a value (an `OpaqueSpec`); the MECHANISM is a (b).

### The balance

Design every rule as reusable as possible; reach for (c) only after establishing the mechanism
cannot be shared — most library-specific-looking things are a (b) with the policy inlined.
**Counterweight: a shape that recurs is not a mechanism** — where the target language already has
the abstraction, `runtime/` refuses it, and only a BASE can ship a support type (P10).

### Enforcing it

No file under `balticporter/{api,engine,frontend-spoon,runtime}/` may name a ported library or one
of its DEPENDENCIES **in code**, test sources included; doc comments are wanted and must drive nothing:

```
grep -rn --include='*.scala' -E "badlogic|libgdx|liqp|liquid\.parser|earlygrey|simplegraphs|dongbat|jbump|czyzby|noise4j|tommyettinger|anim8|textratypist|regexodus|kotcrab|visui|crashinvaders|eskalon|mgsx|vladsch|flexmark|nibor|fasterxml|antlr|strftime" balticporter/api balticporter/engine balticporter/frontend-spoon balticporter/runtime | grep -vE ":\s*(\*|//|/\*)"
```

---

## 1.5 A dependent module INHERITS the shared surface; it never restates it

An extension resolves against the base's JAVA, so the base's policy must be redone identically or
the two ports compile alone and not together. The shared surface is a VALUE — `PortManifest`,
`base.extendedBy(...)` (`.conf`: `base = "…"`) — never repeated policy.
| inherited — a fact about the SHARED SURFACE | not inherited — this module's build |
|---|---|
| `dropTypes`, `dropMethods`, `packageRenames`, `surface` | `sourceSet`, `frontend`, `provenance`, `runtimeMode`, `supportSources`, `project` |
| `typeRenames`, `subPackages`, `flattenNestedTypes`, `allowPackageSplit` | **`inject`**, `targets`, `verdictOverrides`, `dependencies` |

- `targets`: a dependent may narrow, never widen (fatal; widen the BASE instead). All-platform is the
  stated intent; a `Verdict.Depend` is answered by DECLARING the artifact, never a rewrite (P6).
- `inject`: the drop binds every module; exactly ONE module ships each replacement file.
- Emission identity is (`portRoot`, `sourceSet`): N upstream modules in one destination are ONE port
  with a glob list; a third tree gets its own disjoint root. A synthesised unit is written only by
  the module owning its declarations (`RunScope`, O5). A base's green numbers say nothing about its
  dependents: `just measure-all`.
- `surface` composes only through the phase's `MergeablePolicy` (`surfaceFold`, at the base's
  position, on `policyChain`); undeclared is fatal `SurfaceDivergence`; a refusal is a finding; place
  a dependent's early phase by an EMPTY base instance, never `runsBefore`.
- No dependent key may edit what a base EMITS (`SurfaceIntrusion`), screened over the whole
  `subjects` against the base's PUBLISHED MAP, never its `governs` claim (D10).
- A dependent's retyping phase scopes on the ENTRY (D12); a DERIVED surface is published and
  compared as `base-surface`; a dependent FOLLOWS the base's member renames (D14).
- **A base's SOURCE SET is decided before its policy**, against the REFERENCE port's own ownership:
  a base may not emit a type the hand port declares in the DEPENDENT's namespace, nor one the
  dependent DROPS or reshapes (`SurfaceIntrusion`, `ExtraDrop`, both fatal), and it cannot shrink
  out of the overlap — a base never resolves against its dependent (K43).
- `ManifestAgreement` runs on every port; resolution roots outside the source root with no base
  declared is fatal — declare an empty manifest and say so.

details: `.claude/rules/dependents.md`

## 2. Adding a library to the corpus

`balticporter/corpus/` (`balticporter.corpus.<lib>`): **make it compile; test-compile, port and RUN
its tests (§3); the Auditor (§4) runs by user request.** details: `.claude/rules/dependents.md`

## 2.1 A port is named for its DESTINATION, never for its upstream

`ported/<reference module id>/` (`sge`, `sge-ecs`, `ssg-liquid`). **The port's `label` and
`PortManifest.name` take the same value.** NOT reached: `port-report/<main class>/` (measurement
identity — never rename), `packageRenames`, the upstream tree name.

---

## 3. Compiling is not the gate

The compile-error count is **typer-only**: one typer error skips `RefChecks`, so at 0 the count
RISES, serially — read `errors.tsv`'s MEMBER column, never the headline (G30). A green compile says
nothing about behaviour (four silent defects shipped clean). So:

- **Every translation path gets a check at the same time it gets a translation.**
- **Walk the tree with `StandardTraversal`**, never a private recursion, wherever the IR says a node
  can appear (`allClassDefs`, `allDeclaredClasses`).
- **Prefer running ported tests over any number of further compile fixes.** Read the emitted output.
- **An idiom transform's safety argument is a REFUSAL ENUMERATION, never a suite result**: every
  behavioural delta is guarded, shaped away, or COUNTED — one lane row per declined site naming the
  guard. A transformer that cannot enumerate its deltas does not ship. Its `members.tsv` blast is
  CLASSIFIED, not minimised, and the unattributed residue is EMPTY (M10).
- **"Refuse loudly" is a claim about the emitted text**: where the untranslated form is also valid
  Scala, the refusal is COUNTED when written (M6). A repair at a USE cannot discharge the
  DECLARATION's obligation (G8.10). A new arm inherits its node's catalog obligations.

details: `.claude/rules/phases.md`

## 3.5 Consult the REFERENCE PORT before inventing a rule

**Look in sge/ssg before designing a rule, and before concluding no faithful translation exists**
(`grep -rn "<construct>" ../sge/sge/src/main/scala/`); record what they EMITTED and whether they
SOLVED or SKIPPED it — a skip is not a model. The reference SUITE is a differential gate: census twice,
adapt only by an enumerated NAME/SHIM table applied per RECEIVER to comment-masked code, measure a
blocker at the EMITTED SITE, price a mapping row by compiling UNEDITED, check the java compiles
against the dependency version the run supplies. **What the hand port emitted is evidence; what it
implies for a MECHANICAL port is a hypothesis measured before it is policy** (K16). **The default
contract is JAVA'S BEHAVIOUR**: behaviour/omission — java wins; API spelling — EXACT hand-port
parity, marked `unjustified` where `divergence-investigator` finds no recorded decision
(`ported/<module>/divergence-verdicts.tsv`). details: `.claude/rules/dependents.md`

## 3.6 Where a discovery goes

A lesson that would change how the NEXT library is ported does not belong only in that port's
progress section — nothing loads it, and it gets re-derived. **There is one document per KIND**, and
a discovery goes into whichever fits, in the same commit that learned it:

| home | for |
|---|---|
| this file | a governing rule or constraint for all porting work |
| `ENGINE-LIMITS.md` | a MEASURED dead end or engine limit — what not to retry, and what it cost |
| `DESIGN.md` | a DECISION about what the engine is or how it is built |
| `PROGRESS.md` | the STATE of a port or of publishability — measurements, residues, remaining work |
| a skill (`.claude/skills/**`) | a procedure, e.g. adding a library to the corpus |
| an agent definition (`.claude/agents/**`) | what a reviewer should hunt for |

**Do not add a seventh document.** A new file for one investigation is a file nothing loads; see §3.7.
The rule goes above; the numbers stay in that library's `PROGRESS.md` section; a rule naming one
library is that library's manifest policy (§1c). A dead end without its number is an opinion.

## 3.7 A RESEARCH FILE IS NOT A DELIVERABLE

Scratch files live under gitignored **`.balticporter/`**, nowhere else. **No committed file may
CITE one.** What it found goes to a §3.6 home and the file is deleted, never committed. **A TODO
list shrinks by DELETION** — never a done section, never `[x]`.

## 4. The Auditor

`.claude/agents/porting-auditor.md` — an adversarial reviewer for over-specificity, missed cases and
shortcuts. Fable 5, expensive: **run by the user once a whole piece of work is delivered; never speculatively.**

## 4.45 The consumer is an AGENT IN ANOTHER REPOSITORY

sge/ssg agents will run a published engine without this session's context. **An engine limit lives
somewhere the engine ships** (this file, `ENGINE-LIMITS.md`, a skill, an agent definition), in the
commit that measures it; **every check and `ENGINE-LIMITS.md` entry says which of §1's three kinds
the fix is**; a lane kind mixing a residue that COMPILES with one that cannot is split.

## 4.5 Never model a Java interface on a Scala COLLECTION trait

Write a standalone trait with Java's own shape and ARITY (`iterator()`, `hasNext()`); restore
interop with **extension methods**, never by extending `scala.collection.*`, on **one** of a pair
of related shims, never both. Invisible while any typer error remains (§3).

## 4.4 Java statement semantics Scala does not share — the ones that COMPILE

Each translates to valid Scala meaning something else; none moves a count; all were found by RUNNING tests:

| Java | naive Scala | why it is wrong | faithful |
|---|---|---|---|
| `a == b` (references) | `a == b` | Scala's `==` calls `equals` — and inside an `equals` body that is infinite recursion | `a eq b` |
| `x++` as a value | `{ x += 1; x }` | post-increment yields the value BEFORE the update; every circular buffer was off by one | `{ val p = x; x += 1; p }` |
| `break` / `continue` | *nothing* | the loop simply ran on / the rest of the body still executed | `boundary` around the LOOP for break, around the BODY for continue; name the outer one when both |
| `break L` / `continue L` | *nothing* | a labelled jump crosses nested loops and switches by definition | a NAMED boundary on the labelled statement, `break(())(using brk)` |
| `L:` on a statement that is NOT a loop | *nothing* — a label on an `if`, a block or a `switch` had nowhere to live | `break L` leaves THAT statement; dropped, `JsonReader` fell through after `bool(name,true)` and emitted a second, string event for every unquoted bool/null/number | `Tree.Labeled` + a named `boundary` around the STATEMENT. A LOOP's label stays in the loop node — it is also `continue L`'s target, whose boundary goes elsewhere |
| any boundary the emitter INTERPOSES | an un-annotated `break(())` under it | `boundary.break` with no `using` resolves the INNERMOST `Label`, so a new boundary silently steals the enclosing loop's jumps | name the enclosing boundary whenever anything inside the body renders with a boundary of its own; over-approximate, an unused name costs one identifier |
| `switch` with no `default` | `match` with no `case _` | java FALLS OUT when nothing matches; scala throws `MatchError` — and falling out is often the normal path | always emit the fall-out arm |
| `switch` on a `String`/boxed/enum selector that is `null` | the same `match` | java throws `NullPointerException` the instant the selector is null (JLS 14.11), IMPLICITLY — a classic switch has no `case null` to opt out with. `null` matches no literal pattern, so it reaches the fall-out arm above and the EXCEPTIONAL path became a silent no-op | `case null => throw new NullPointerException(…)` ahead of the java arms, for a selector whose type is not a scala value class — and never where the java itself writes `case null` (SE21's opt-out) |
| a case's trailing `break L` | stripped as a case terminator | only an UNLABELLED break ends a case; a labelled one leaves the LOOP | strip unlabelled only |
| a `break` in the MIDDLE of a case | *nothing* | it ends the CASE — and fallthrough is lowered by duplicating the next case's tail INTO this arm, so what ran on is code java put in a different case | a named `boundary` around the ARM; `match` cannot leave an arm early |
| an ARROW arm — `case A -> e;` (SE14) | the colon form's lowering: fallthrough duplication, and the fall-out arm above | java's arrow form has NO fallthrough at all (JLS 14.11.2); a rule that decides "did this case run on?" by looking for a trailing `break` finds none, and duplicates the NEXT arm's tail into every arrow arm | read the CASE KIND, never the body. A parser may also NORMALISE the arm (Spoon wraps `case 1 -> doIt();` in a `yield` node), so undo the parser's shape before translating it |
| a `switch` in EXPRESSION position (SE14) | the same `match`, plus the fall-out arm above | a switch EXPRESSION cannot fall out: JLS 15.28.1 requires it to be EXHAUSTIVE, so `case _ => ()` answers `()` where java answers nothing. An ENHANCED switch statement must be exhaustive too | no fall-out arm for an expression, and for a statement only where it is not enhanced. **Ask BOTH of JLS 14.11.2's disjuncts**, read off the SELECTOR'S TYPE and never off the label's shape (a qualified enum constant at a supertype selector is enhanced); resolution failure keeps the fall-out arm. Java throws `MatchException`, scala `MatchError` — both throw |
| `yield v` that is NOT the arm's last statement (SE14) | the value, in place | java's `yield` completes the WHOLE switch expression abruptly, from arbitrary depth (JLS 14.21); dropped into place, every statement after it still runs | peel a TAIL `yield` into the arm's value; give any other one a value-carrying `scala.util.boundary` around the ARM, `break(v)(using n)`. `arbitrary depth` includes a nested switch STATEMENT — carry the java construct on the node; a `Unit` result type is a coincidence |
| `static final int X = 0` | `final val X: Int = 0` | java INLINES a constant variable, so reading it never triggers the class initialiser; the typed `val` does, and libgdx's `Vector3`/`Matrix4` initialisers are a cycle | `inline val X = 0`, literal rendered AT the declared type. Catalog `JS-C08` |
| a `final` FIELD | `val x` | java HIDES a field, never overrides it (JLS 8.3) | `final val`, `JS-C53` |
| `int x = intValue` at a `float` slot — assignment, argument, return | `x` (relying on `int2float`) | Scala's implicit `int2float`/`long2float`/`long2double` are deprecated since 2.13.1; java widens implicitly (JLS 5.1.2) and `-Werror` promotes the warning | `.toFloat`/`.toDouble` at the slot, emitted by the FRONTEND's `coerce` function (F10) |
| a java CLASS INITIALISER — `static { … }` **or a non-constant static FIELD initialiser** | `locally { … }` / a `val` in the companion `object` | it is EMITTED and never RUNS: java initialises the CLASS on the first `new`, static access or subclass init (JLS 12.4.1); Scala initialises the OBJECT when something touches the OBJECT, and `new Template(…)` does not. A registration silently answers "not registered". The two spellings are ONE construct (JLS 12.4.2 step 9) | reproduce JAVA'S OWN TRIGGER LIST, item by item: `val _ = <Type>` at the head of the CLASS body (instantiation) and `val _ = <Ancestor>` at the head of the COMPANION body (subclass init, nearest bearing ancestor only). A static ACCESS and a CONSTANT need nothing. NOT from every use, not for an interface. TWO refusals, counted: reflection, and a companion in a MUTUAL CYCLE (`Vector3`/`Matrix4` traded 26 passing tests for an `ExceptionInInitializerError`). K22, catalog `JS-C07` |
| `super(args)` in a 2nd ctor | *nothing* | scala secondary constructors cannot call super; every exception lost its message | promote the widest super call to the PRIMARY and delegate (JDK throwables only — elsewhere padding is a guess) |
| `@Before` | *nothing* | JUnit runs it before EVERY test, on a fresh instance; MUnit has neither | call it at the head of each test body — AFTER the reconstruction the row below emits |
| a test class's INSTANCE STATE — a field initialiser, an instance initialiser block, a constructor body, or a field a test ASSIGNS | the same members on the converted suite | JUnit rebuilds the whole test OBJECT per `@Test` (JLS 12.5 runs once per test); MUnit's suite is ONE instance, so every test after the first inherits the last one's state. Only RUNNING the suite sees it | HOIST java's initialisation sequence into `override def bpFreshState(): Unit = { <zero MY fields>; super.bpFreshState(); <MY step 4>; <MY ctor body> }`, called at the head of every test body AHEAD of `@Before`; a `static` field is never reset. OBJECT IDENTITY is not reproduced and is counted (X4) |
| `@Test(expected = E.class)` | body run bare | it would PASS while checking nothing | `intercept[E] { … }` |
| `@Rule ExpectedException thrown` + `thrown.expect(E.class)` | the field emitted, nothing applying it | the expected throw propagates and the harness records a FAILURE where java recorded a pass (37 of one suite's 40) | MODEL THE RULE, do not wrap a REGION: `var bpExpected: List[Throwable => Boolean]`, each `expect`/`expectMessage` an APPEND where java wrote it, `catch { case t: Throwable => … }` around the whole converted body, `forall` for `allOf`, rethrow when nothing was armed, `fail` when nothing threw. A LIST, not a flag — two armings are a CONJUNCTION (X5) |
| an array forwarded through a `T...` slot at an EXTERNAL callee — `String.format(fmt, args)` | the same call, array unspread | scalac reads a class file's `T...` as a REPEATED parameter, so the bare array conforms as ONE element wherever that element is `Object`. A callee the port DECLARES is unaffected: its parameter is emitted `Array[T]` | the spread, `args*` — `Arrays.asList(arr*)` still ALIASES `arr` (K6.5) |
| a PRIMITIVE array at a reference `T...` slot — `Arrays.asList(intArr)` | a forward (the row above), or the bare array | java's rule for the slot is ASSIGNABILITY, and `int[]` is assignable to nothing but `int[]` — java builds `new Object[]{ intArr }`: a `List<int[]>` of SIZE 1 | pack as ONE element. Compare the COMPONENT to the vararg's; a primitive passes through only at its own primitive's vararg |
| `arr[f()] += x`, `arr[f()]++` — a compound assignment or increment at a SIDE-EFFECTING lvalue | `arr(f()) = arr(f()) + x` | java evaluates the array reference and the index ONCE (JLS 15.26.2, 15.14.2); the translation runs `f()` twice, three times in expression position | bind each lvalue subexpression to a temporary once (`{ val $lv1 = arr; val $lv2 = f(); $lv1($lv2) = $lv1($lv2) + x }`); simple lvalues keep the direct form. F7, catalog `JS-E17` |
| `list.remove(anInteger)` | `buffer.remove(x)` | java resolved `remove(Object)` — by VALUE, returning `boolean`; scala's only `Buffer.remove` is BY INDEX, and `Integer2int` applies silently | a by-value helper. Read WHICH overload java resolved off the call's RESULT type: `remove(Object)` returns a primitive `boolean` |
| a call whose CANDIDATE SET spans one of java's three RESOLUTION PHASES — `f(int)` beside `f(Object)`, fixed-arity beside vararg, generic beside non-generic | the same call, rendered as java wrote it | java resolves in THREE PHASES (JLS 15.12.2) and an earlier phase WINS OUTRIGHT; scala resolves in ONE and PREFERS a non-generic alternative. javac and scalac bind DIFFERENT members and both typecheck | no faithful translation short of a resolver, REFUSED — the RISK is counted at every call whose candidate set spans a phase, with the denominator beside it. T17, catalog `JS-C22`/`JS-C23` |
| a java `record` (SE16) | a scala `case class` | six cells differ against `javac`, two unrepairable: an EXPLICIT accessor is `E120` beside the case class's `val`; `unapply` reads the constructor PARAMETERS where java's record pattern reads the ACCESSOR (JLS 14.30.1). Also `toString`, `hashCode`, `equals` on `double`/`float` (`Double.compare`), and the added surface (`copy`, `apply`, …) | a plain `final class` with javac's four members WRITTEN OUT — `equals`/`hashCode`/`toString` over the FIELDS and an `unapply` over the ACCESSORS. Recorded on the decision: no JVM record, every accessor runs, accessor exceptions arrive raw. T20, catalog `JS-C43` |
| `x instanceof Map`, `(Map<K,V>) x` — a REIFIED occurrence of a type a phase RETYPED | `x.isInstanceOf[mutable.Map[?, ?]]`, `x.asInstanceOf[mutable.Map[K,V]]` | a retyping moves STATIC types; these ask about a RUNTIME OBJECT, and a ported library holds BOTH representations at every `Object` slot (160 of liqp's 183 failures at 0 errors) | answer over both representations (`JavaCollections.Reified`); where the target is CONCRETE (`mutable.HashMap`, `ArrayBuffer`, `Tuple2`) REFUSE and count. An UPCAST of an operand the phase itself retyped must be left alone (K18) |
| a PRIMITIVE class literal at a `Class<T>` slot — `json.readValue("x", int.class, d)` | `classOf[Int]` | java types `int.class` as `Class<Integer>` (JLS 15.8.2), so it fits a `Class<T extends Object>` slot and BINDS `T` to the wrapper; scala's `classOf[Int]` is `Class[Int]`, which fits no such slot, and with `T` left free scala infers it from the EXPECTED type instead — `Required: Class[Object & Int]` | `classOf[Int].asInstanceOf[java.lang.Class[java.lang.Integer]]` — the runtime object is still `int.class` — and PIN java's own inferred `T` at the call, which is what lets `Predef.Integer2int` unbox the result. `JS-E20` |
| `TypeReference<Map<K,V>>`, `TypeToken<…>`, `Class<T>` — a type ARGUMENT a THIRD PARTY reifies | the argument retyped along with every slot | the argument survives into the class file's generic signature and jackson/gson/guice READ IT BACK — `Cannot construct instance of scala.collection.mutable.Map`. No phase can walk to it (10 of 23 failures) | do not retype a type argument a reified CARRIER holds; bridge at the USE. The carrier list is per-library (b); `java.lang.Class` is the only guaranteed one (K20) |
| `try (R r = …) { … }` | a bare `try { … }` | java closes every resource on ANY completion, in REVERSE order, BEFORE this try's own `catch`/`finally` (JLS 14.20.3); a resource opened for its side effect compiles perfectly with nothing released | JLS 14.20.3.1's own lowering INLINE — one nesting per resource, `primary`/`addSuppressed`, and a `case b: boundary.Break[?] => throw b` arm AHEAD of the recorder. Never `Using(r) { r => … }`: `return`s and `boundary.break`s do not survive a lambda |
| a jump inside a `try` whose `catch` is broad (`Exception`/`RuntimeException`/`Throwable`) | the `boundary.break` emitted under that translated `catch` | java's jump cannot be intercepted; scala's `boundary.Break` **extends `RuntimeException`**, so the handler eats it. Dotty's `DropBreaks.prepareForTry` makes a break under a `try` ALWAYS the exception form | a re-throw arm AHEAD of the java arms — `case b: scala.util.boundary.Break[?] => throw b` — wherever a jump crosses the catch. `finally` untouched; a NARROW catch left alone |
| a rename onto a stdlib member that RETURNS instead of MUTATING — `Bits.xor(bits)` | `bitSet.xor(other)` | java's `Bits.xor` mutates `this`; Scala's `BitSet.xor` RETURNS a new set. Compiles, original unchanged | the in-place operator (`bitSet ^= other`) or a Template. Read the MUTABILITY of the source member |
| an INCLUSIVE range bound translated to an EXCLUSIVE API — `removeRange(start, end)` | `target.removeRange(start, end)` | java's `end` is the last index removed; scala's is the first KEPT — a silent off-by-one | translate the bound (`end + 1`) and restate java's own REFUSALS (`end >= size`, `start > end`) |

Before adding a translation for a Java statement form, ask what it means when its value or its
control flow is used, not only what it looks like. This table is what an agent must know BY HEART;
`balticporter.catalog.Differences` is the complete set (`DESIGN.md` §2.8, `just catalog`).

## 4.55 A renaming pass reads EFFECTIVE names, PARENTS-FIRST

Java reuses names where Scala cannot; each is fixed by renaming the SYMBOL. **Read effective names,
not originals; scan parents before children; keep appending until free.** A CAPTURE can be what
moves — rename it only where really shadowed; scala's enclosing-vs-inherited AMBIGUITY is read off
the enclosing SCOPE (C16). **A name clash and an implementation pair look identical** — decide from
the emitted SHAPE. A loose `(name, arity)` key (a `Map` or a `find`) indexes to a `List` and the
CALLER decides; `OverrideGraph.overridden` is FILTERED, never re-derived (K28.2). A promotion moves
a NAME, never a POSITION (JLS 12.5 step 4 incl. BLOCKS, then 5; C12). details: `.claude/rules/emitter.md`

## 4.56 A rename decides OWNERSHIP structurally, never by name

**A symbol is owned iff climbing its owners reaches a `program.units` symbol.** **An unowned
symbol's SIGNATURE is a class-file fact no phase may move** (K15). Cut only at a `Symbol.fullName`
separator (`.`, `$`, `#`); a namespace rename runs LAST; an artifact joining POLICY to OBSERVED code
carries BOTH names (`Correlate.Dropped`); match a runtime class name at a separator; unit ownership
is `FrontendConfig.files`, never a path prefix. **A phase may only conclude something about a type
from what the PHASE ITSELF did to that type.** A guard is derived from ALL the pass's targets, asked
of the ANCESTRY (K2.6); a `match` arm under a supertype arm is dead — one `TypeShape.of` derivation,
answers at the callers (G21); a guard whose evidence can be MISSING states the REFUTATION (G33); a
node-kind test owes every syntax java has (`C::new`, CT6); an instrument's filter states the
COMPLEMENT; the survivors are not the DECLARATION (`permits`); a synthesised member carries the
parent's scope through one `ParentSubst` (G25, K28.2); a refusal lane is a POPULATION and a
two-direction predicate needs two functions (C3). details: `.claude/rules/emitter.md`

## 4.57 Every emission backend carries PROVENANCE — it is a licence obligation

Every generated file ships an attribution header; nothing reports a missing one. Path from the
unit's `Origin`, never its FQN. **A banner is not always the notice**: `Provenance.notices` are
copied into `src_managed/`, NOT gated on the artifact layer; a declared absent file is FATAL.
details: `.claude/rules/emitter.md`

## 4.575 A PORTER NOTE puts the decision where the question is asked

Every `decisions.tsv` row is also emitted BESIDE the code:
`/* porter: <kind-slug> reason=universal|configured|library-rule rule=…|phase=… key=… k=v … — why */`
**Notes are DERIVED, never authored** (`NoteCoverageCheck`). **Original trivia FIRST, note LAST,
member next. A note may never open or close a comment** (`PorterNote.safe`). Placement:
`AtDeclaration` / `InBody` / `NotInTree`. A text-searching check strips notes first.
details: `.claude/rules/emitter.md`

## 4.58 COMMENTS are part of the port — and only a TEXT-to-TEXT check can see them

**Slice VERBATIM from the source buffer; never re-print.** Guard the buffer, not the position. One
comment, one home (a CLAIMED identity set; the positional header harvest FIRST, V3); a consumed
comment gets an exact home or a QUOTATION with its coordinates, counted apart. A nesting `/*` is
emitted as `//` lines; a separator test skips comments. **`TriviaCheck` compares SOURCE TEXT to
EMITTED TEXT, never the tree; a `catch` around a harvest is how it regresses.**
details: `.claude/rules/emitter.md`

## 4.59 A construct the PARSER SYNTHESISES is not a construct the parser MODELS

**Derive what java DERIVES from the declaration java WROTE** (records; an anonymous class's ctor is
the SUPERCLASS ctor's, G29); read the parser's synthesis only where a fixture has shown the two
agree. details: `.claude/rules/emitter.md`

## 4.6 A kill switch beats another condition

First establish **which code produces** a wrong construct — never add a condition to the gate you
suspect. **A bare `catch` is a kill switch somebody left on**: wrap only the ONE lookup where
absence is normal, as one shared function. Flags (`Pipeline.run` reads them; gate on a marker
FILE, `sbt --client` never sees env vars; precedence `run.properties` < `debug.properties` < `-D`):

| flag | does |
|---|---|
| `balticporter.skipPhases=<name>,<name>` (or `*`) | omit those phases; the answer in one run |
| `balticporter.dumpTirBefore=<phase>` / `dumpTirAfter=<phase>` | print the TIR around a phase |
| `balticporter.dumpOnly=<fqn>` | narrow either dump to one type |
| `balticporter.tracePhases` | announce each phase as it runs |
| `balticporter.traceNode=<Kind>` | `TirTrace.mint` prints constructing frames for a node kind |
| `balticporter.baseReports=<p1:p2>` | FALLBACK ONLY — belongs to the PORT (`PortManifest.baseReports`) |

**Reach it through `just`** (`debug-flags`, `debug-set`/`debug-clear`, `debug-emit`, `correlate`,
`members-unchanged`). **Clear a flag when done.** `reportPathRoot` comes from the PORT, not the
operator. details: `.claude/rules/emitter.md`

## 5. Measurement discipline

- **Reproduce every number with the measure lanes, serially**: `just` with no recipe lists them;
  mechanism is `scripts/_lib.sh`, policy the `Justfile` variables; `just measure-all` runs all
  (`BP_FULL=1`). **Never add `set -e` to a lane.**
- Each lane diffs every registered check against the baseline; `PortRun.RequiredChecks` is asserted
  against what recorded — a number reaching stdout and not `findings.tsv` fails the run.
- A resolution DRAINS a lane visibly (`<lane> N->M, remediation(resolved) 0->(N-M)`, `sum(drained)`);
  ONE POLICY, ONE SPELLING; an accept answers a QUESTION, never a DEFECT.
- **Baselined in BOTH directions, written by the run, promoted by `just baseline-accept`, never
  hand-edited**: `expected-errors` (+ `.js`, `.native`, `.ref`), `expected-lost`, `findings.tsv`,
  `port-map.tsv`, the drop-in lane. **Fewer errors fails as loudly as more.**
- **The JDK is an input to the measurement** (`jdk_guard`); read `overrides nothing` as a JDK
  mismatch first; never move `jdk_version` to fix one (M5.10).
- **So is the JAVA TREE**: `counts.tsv`'s `upstream` row, `upstream_guard` ahead of every diff.
- **Widening a guard is measured on the ports it was not aimed at**; a narrowing is not exempt (G21).
  **Change one thing, then measure.** A DRY RUN of one phase is not a pipeline measurement. Record
  regressions under "Do NOT retry"; `before->after` in the commit subject.

details: `.claude/rules/measurement.md`

### 5.1 A diagnostic over emitted code is ATTRIBUTABLE — never read it by hand

`srcmap.tsv` + `members.tsv` come from the emitter's own recording; `CorrelateRun` joins compiler
and test output through them. **Never open an emitted file to find which member an error is in** —
`errors.tsv` says. `members.tsv` vs its baseline is the blast radius BEFORE any compile; accept a
baseline only from a CURRENT run. The test lane is the only one that sees §4.4: parse every
TERMINAL MARKER and gate on each; run engine specs with `testOnly *` AFTER `measure-all`.
`decisions.tsv` records WHY per DECLARATION, scoped to this module (D2). **An artifact write is
gated on the artifact LAYER**, never a flag. Deliberate failures are DERIVED (`dropped-types.tsv`).
details: `.claude/rules/measurement.md`

## 5.4 Compare paths through `toRealPath`, on BOTH sides — always

A worktree reaches its siblings through a symlink; `normalize` keeps it and `Files.walk` follows it,
so a lexical `startsWith` matches nothing. Realpath both operands; `normalize` only when the path
does not exist. details: `.claude/rules/measurement.md`

## 5.5 Emitted code is a BUILD PRODUCT — `src_managed/`, never `src/`

Every port writes its generated Scala to `<port>/src_managed/{main,test}/scala`, which is
gitignored and deleted by `sbt clean`. `src/` holds only the hand-written part of a port — the
shims and overrides a library needs and the engine cannot derive.

This is the layout a target project is meant to use, so the engine must produce it, not
approximate it. `SbtGen.managedMain` / `managedTest` give the paths; `SbtGen.emit` writes the
`.gitignore` and the `sourceGenerators` + `cleanFiles` settings that make sbt see the directory
and `clean` remove it. Never hardcode an output path in a corpus migrator.

The reason is diagnostic, not tidiness: emitted code is reproducible from the Java sources plus
the manifest and is invalidated by every engine change. Committed alongside `src/`, a `git status`
can no longer distinguish a DECISION from an ARTEFACT — which is the single thing this project's
measurement discipline (§5) depends on being able to see.

## 6. Scala 3 output constraints

- **Never cast to `scala.Nothing`.**
- Vararg spread is `args*`, never `: _*`.
- Emit **fully-qualified names, no imports**, for the structural phase — this deletes the whole
  import-decision bug class. Human-readable imports are a separate, optional beautification backend.

## 7. Comments and files

- A doc comment states the CONTRACT in at most 5 lines and cites an id (`ENGINE-LIMITS.md Kxx`,
  `DESIGN.md §n`, `CLAUDE.md §n`) for the why. No history, no measurement narrative, no quotation
  of another document in code.
- A Scala file stays at or under ~2,000 lines, split by MECHANISM; read it with targeted greps and
  line ranges, never whole.

## 8. Agent briefs

- Name the sections and files to read (`CLAUDE.md §4.56`, `ENGINE-LIMITS.md K28`) — never "read fully".
- Give the numbers (baseline before, expected after), the OWNED files, the verification recipe
  (`just <lane>-measure`, `members-unchanged`), and the environment rules (worktree, `sbt --client` with the lane's server dir,
  no `pkill`, launchd for long runs).
- One subject per wave; one commit per riser; `before->after` in the subject.
- No subagents unless the brief says so.
