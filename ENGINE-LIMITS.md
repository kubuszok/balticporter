# Engine limits — the measured dead ends, for the NEXT library

**Read this when the first wall of typer errors appears, not at the end.**

Every entry below is a fact about Java, Scala 3, Spoon under `noClasspath`, dotty, or Baltic
Porter's own architecture, measured once (mostly on libGDX) so you do not have to re-derive it.

### Triage 2026-09-04

Full triage of the 161 open ids (`### <ID>` headings without `CLOSED`), corrected 2026-09-04: a
REFUSAL is not a closure (campaign contract, `PROGRESS.md` §13: "FIX EVERYTHING — no entry may end
open, limit, refused or do-not-retry without a measured exit"), so every entry whose own citation
said "refused", "genuine expressiveness limit", "no Scala image", "not a gap" or "permanent
language-level limitation" moved from closed-in-fact to its own row below. Corrected split: **109
closed-in-fact** (shrunk to the 4-line CLOSED form), **20 refused-by-design** (heading's added
`CLOSED` removed, body kept, citation says what a card would need), **6 superseded** (pointer to
the closing id/rule), **1 needs-measurement**, **32** still open (families) across 11 families (I newly has open members: six idiom refusals).
Seven pre-existing entries whose OWN heading mixed `CLOSED` with a genuine residual (K19, X4 named
by the coordinator, plus G33/K5.10/K15/M10/O6 found the same way) had no `Triage` line at all and
now do, without any other edit to their heading or body. Per-id citations are each entry's own
`Triage` line.

| family | open (FAMILY) ids | refused-by-design ids | shared mechanism | subplan item |
|---|---|---|---|---|
| C (ctors) | C3, C7 | — | ctor-funnel residual shapes: promoted-body-on-every-path; C3's 4c (synthesis through a synthesised parent), 4d (delegation-head slot) and 4g/CT13 landed 2026-09-05 with two counted refusals (a slotted head argument naming another parameter; colliding post-body slot names) | item 4 |
| CT (context) | — | — | CT11 closed by subplan item 5 (holder + throwing accessor, init at the first threaded static method); CT13 closed 2026-09-05 | — |
| D (dependents) | D2, D8, D13, D14 | — | dependent/base seams: run-time annotation-key screen; redirect+drop pairing check; member-rename manifest spelling; `followMemberRenames` reorder | no card yet |
| G (generics) | 0.2, G9, G11, G12, G18, G19, G21, G24, G33 | G8, G10 | bounds/erasure with no consistent fill or cast to write: F-bound sibling-formal fill, decl-vs-ref erasure, argument-erasure unification, raw-anonymous-class cast, `?`-sentinel naming collision, a cosmetic double-cast | no card yet |
| K (collections) | K5.6, K14, K16, K23, K37, K38, K5.10 | K5.8, K15, K19 | retarget/boundary residues with no fix path: cast-after-retype, retarget→typeMap coercion, scope-seam counting, `spliterator` re-keying, per-dependent/per-entry retarget scoping, an unobserved symbol-swap residue, untranslated `super`-receiver names, unreadable-class-file bridging, reference-identity-on-a-coercion | no card yet |
| M (measurement) | M5.12, M5.13, M5.14, M10 | — | Metals BSP-connect timeout; a diagnostic id keyed on a mint counter (fix belongs in `Minter.table`, unbuilt) | no card yet |
| O (opaque) | K13.6, O7, O6 | O3 | opaque-sentinel/JVM-null unification; GL-enum-family `ConstantsAs` mint form; `Key`/`Button`/`HttpStatus` opaque shapes with no vocabulary; a container deeper than one erasure-identity level | no card yet |
| P (platform) | P9 | P3, P10 | force registration + manifest key for off-JVM `ServiceLoader` codegen; per-library JVM-only-API substitution has no engine-level closure; no shared reflective-registry abstraction across ports | no card yet |
| T (members) | T7, T16.5 | T10, T17, T18, T20, T21 | qualified `super[X]` TIR node; record/annotation-type synthesis; overloaded enum ctors; overload-resolution-divergence prediction; instanceof-pattern flow scope; JVM `Record` reflection; three un-enum-syntax'able enum shapes | no card yet |
| X (tests) | X7 | X4 | headless-graphics native-image loader-path decision; MUnit single-instance vs JUnit fresh-instance object identity | no card yet |
| I (idioms) | — | I1, I2, I3, I4, I5, I6 | six idiom transforms priced and refused on cost/no-evidence/missing-dataflow grounds (`this.type`, instanceof-cascade, StringBuilder, case-class derivation, array-init loops, try/finally lowering) | no card yet |

## How to read an entry

- A number is evidence: `13 → 28` means built, measured, worse. `+277` catastrophically worse.
  `inert` means built, ran, changed nothing — usually proof the suspected gate isn't responsible.
- "Do NOT retry" means do not retry as stated; where reopening from a different angle is worth it,
  the entry says what has to be answered first.
- Every entry names its fix kind per `CLAUDE.md` §1: **(a)** engine bug/gap — fix in
  `api`/`engine`/`frontend-spoon`, unparameterised; **(b)** configure an existing phase — supply
  your library's values; **(c)** library-specific rule — plug into your own migration program, never
  the engine.
- Worked examples name libGDX/corpus constructs to document *why* a rule exists; they drive
  nothing — substitute your own library's shape.
- A port name in an entry is the name that port had when measured; `CLAUDE.md` §2.1 / `PROGRESS.md`
  §1 has the rename table (`libgdx-core`→`sge`, `ashley`→`sge-ecs`, `liqp`→`ssg-liquid`, …). Numbers
  are unaffected — `port-report/<X>/` keys on the migrator class, which did not move.
- Full measurements live in `PROGRESS.md`, per library; this file holds the rule only.

## 0. The root cause behind most of these

**The engine's recorded type is not a reliable witness of what the emitted Scala will have or
infer.** Three faces: (1) a node's `tpe` can disagree with the emitter's printed Scala — reasoning
from `t.tpe` reasons about a type the output doesn't have; (2) two renderings of one Java type in
one class is the engine's most persistent defect shape — a declaration renders in one scope, a use
re-renders Spoon's type in another and gets something else; (3) a frontend-recorded `Symbol.info` is
a *pre-transform* rendering — `CollectionsTransform.run` retypes signatures after the frontend
records them, so late `Minter.infoOf` is a *third* rendering (coercing to it: 1 → 3). The type the
emitter will finally print is only knowable after all transforms have run; a check needing it
belongs in a late TIR pass (`RewriteTrace.check`). *Fix kind: (a).*

---

### 0.1 The SECOND root cause: **five loud fallback arms, and ninety-five silent ones** — CLOSED
Three tiers: refuses (whole compilation unit fails), degrades-and-counts (tracked by a check — expected, not a work item), degrades SILENTLY (a fabricated fact nothing can distinguish from real data — the worst tier).
Triage (2026-09-04): CLOSED-IN-FACT — wave 2.15 states "0 fabricated facts remain"

### 0.2 `Symbol.isUnresolvedTypeVar` is `startsWith("?")`, and **10,417 libGDX symbols match it**

(a) engine — `Symbol.isUnresolvedTypeVar(fullName) = fullName.startsWith("?")`.
Cause: two unrelated mint sites produce `?`-prefixed names — the real sentinel (`SpoonTir.tpe`'s `?T`, `resolveVar`'s `?var$name`) and an incidental one (`Minter.fullNameOf` falling back to `?` for a member whose OWNER it could not name, e.g. `?#actual`).
Measured by `MarkerCheck.sentinels`: 10,417 matches on libGDX core, 29 on its own test set, zero of them actual sentinels — `CLAUDE.md` §4.56's "a prefix is not a structural fact" inside the engine's own predicate.
Not yet corrupting output: the 3 consumers (`TirEmitter.typeSym`, the type-bound renderer, `CollectionsTransform.namesUnresolved`) are all reached only from a TYPE position, which parameter symbols don't normally reach — a property of today's symbol traffic, not a guarantee.
Do not retry: widening/narrowing the predicate without a measured before/after commit — the reading side (`MarkerCheck`) is already exact via full-string equality against the mint site.
Not fixed here, deliberately — narrowing changes what the emitter prints and needs its own measured commit.
Triage (2026-09-04): FAMILY G: marker-symbol naming collision (Minter.fullNameOf's `?`-prefixed fallback) — "Not fixed here, deliberately — narrowing changes what the emitter prints and needs its own measured commit."

## 1. Generics, raw types and wildcards

### G1. Erase USES (casts), never DECLARATIONS — **+277 errors** — CLOSED
Symptom/cause: rendering a raw declaration as `Object`-parameterised instead of wildcard. `Array[?]` accepts `Array[String]`; `Array[Object]` does not — widening a declaration to satisfy one use breaks every other use.
Numbers: +277 errors.
Triage (2026-09-04): CLOSED-IN-FACT — "hard invariant, not a tuning knob"; Do-not-retry stands, nothing left to build

### G2. A raw generic renders `[?]`, everywhere — and `?` DOES round-trip across an override — CLOSED
Design space measured in full — inherited-fill toggle × un-nameable-raw-fallback:
Triage (2026-09-04): CLOSED-IN-FACT — "settled to a small per-site residue"; inference-variable case "liqp 58 → 57, 0 members moved elsewhere"

### G3. A class must see its INHERITED INSTANTIATION — 162 → 7, and the guard that cannot work — CLOSED
Symptom: override re-renders parent's raw type with no type variable in scope, disagreeing with parent (`AssetLoadingTask implements AsyncTask<Void>` puts `T→Void` in the map; unrelated raw field `Array<AssetDescriptor>` then renders `Array[AssetDescriptor[Void]]`).
Fix: the fill is an obligation of OVERRIDING MEMBERS, not of the class (4→3, gated by `inOverridingMember` off the same `overrides` flag `execDef` computes) — a member the class declares for itself carries no such obligation.
Triage (2026-09-04): CLOSED-IN-FACT — final fix line "(4→3, gated by inOverridingMember off the same overrides flag execDef computes)"

### G4. A name-keyed fill's success is a property of the CORPUS's naming — re-test it

(a) engine — result is a property of THIS corpus's naming, re-test per library.
Numbers: nested-only fill + inherited fill ON: 14 errors; nested-only + inherited OFF (coherent pairing): 19; unrestricted name-directed fill + inherited fill: 1.
Cause: libGDX names its asset type parameter `T` consistently enough that the "wrong" unrestricted fill agrees on both sides of nearly every override.
Do not retry: treating the unrestricted-fill win as proof of the principled design — a library with less uniform naming would invert this result; that's the expected outcome, not a regression.
Next: re-test as the corpus grows (`CLAUDE.md` §2) — rule moves toward the principled nested-only fill if any library inverts it.
Triage (2026-09-04): NEEDS-MEASUREMENT — "Next: re-test as the corpus grows … rule moves toward the principled nested-only fill if any library inverts it" — re-run the fill A/B measurement (§this entry's table) on the next corpus library via that library's `-measure` lane

### G5. An override's return type must NOT be rendered from the parent's declaration — 162 → **438**

(a) engine.
Symptom: override's return type rendered from parent's declaration directly — 110×E164, two independent captures of `Array[AssetDescriptor[?]]` comparing unequal to itself.
Cause: parent's raw type reference names the PARENT's type variables, not in scope in the subclass.
Numbers: 162 → 438 (worse) under the naive repair.
Fix: use the parent's already-rendered result with parent formals substituted by the subclass's actual args, via `CtorFunnel.parentTypeSubst` (already computed for constructor replays), applied to member signatures.
Triage (2026-09-04): SUPERSEDED — G25 — "Unified into one substitution, ParentSubst (previously four separate spellings, two of which didn't have it)"

### G6. A de-wildcarded raw PARENT and its overrides must agree — CLOSED
Symptom: `extends Configurable[?]` is illegal in Scala; emitter picks `Object` for a raw parent, but overriding members were rendered `[?]` by the raw fill — 8 classes, `needs to be abstract`.
Fix: the type argument chosen for a raw parent must be reused as the fill for that variable in every member overriding one from that parent — keyed off the emitted parent so it cannot disagree with itself.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: the type argument chosen for a raw parent must be reused as the fill for that variable in every member … keyed off the emitted parent so it cannot disagree with itself" — shipped rule, no residue named

### G7. Wildcards in an `extends` clause take the parameter's DECLARED bound, not `AnyRef` — CLOSED
Fix: wildcards in an `extends` clause take the parameter's DECLARED bound (resolved left-to-right, so a later bound can name an earlier parameter, e.g. `T <: ParticleBatch[D]`), not `AnyRef`.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: wildcards in an extends clause take the parameter's DECLARED bound … not AnyRef" — shipped rule, no residue named

### G8. A partially-nameable F-BOUNDED class has no consistent fill — a genuine expressiveness limit
Symptom: partially-nameable F-bounded class has no consistent fill.
Triage (2026-09-04): REFUSED-BY-DESIGN — family G: F-bound fill has no consistent single answer across sibling formals — filling one poisons another; no scala image exists to write, and no card exists to close it short of a language-level workaround

### G8.7 An unconstrained F-BOUNDED result is ASCRIBED, never instantiated — G22's pin at the shape its fourth condition declines. **ssg-md 26 → 20. CLOSED**

G22 pins an unconstrained method type variable via an explicit type argument, declining where the bound mentions a named variable (F-bound). G8.7: a SELECTION doesn't need a fill — ascribe the receiver's static type instead (`getBuilder().asInstanceOf[ISequenceBuilder[?,T]]`), since an ascription need not satisfy the bound the way an argument must.
ssg-md 26 → 20, six sites in one file, 7 member digests, all other ports byte-identical.
Rule: a type ARGUMENT must satisfy its bound; an ASCRIPTION need not — where a call's result is one of the method's own variables, ascribe rather than instantiate.

### G8.10 Java's UNCHECKED OVERRIDE — an F-bounded, RESULT-ONLY type parameter is erased at the DECLARATION, which is the position G8.7 could not reach. **ssg-md 42 -> 34, `overload-risk` 563 -> 557. CLOSED**

JLS 8.4.2 unchecked override — an F-bounded, RESULT-ONLY type parameter can be dropped entirely by an implementor (erasure subsignature); scala has no subsignature rule so this reads as E038/`needs to be abstract`, invisible until 0 typer errors (`RefChecks`).
Fix erases the parameter at the DECLARATION (three conjuncts: not in any parameter, bound mentions the variable itself, RESULT mentions the variable) via `unwritableResultVars`/`tpErased`; ssg-md 42 → 34, overload-risk 563 → 557 (1655→1638 test lane), all other 12 ports byte-identical.
Rule: an F-bounded parameter with no denotable instantiation (G8), used only as an unconstrained result, is UNWRITABLE — erase it at the declaration, matching java's own unchecked-override erasure; G8.7's use-site ascription stays (now an identity cast).

### G8.9 A widened `equals` PARAMETER at an `Object` slot — the third value scala types wider than `Object`, and the port made it. **ssg-md 20 → 19. CLOSED**

`equals(Object)`'s parameter is retyped to `scala.Any` (so it overrides `Object.equals` after erasure); forwarding that parameter to an `Object`-typed slot (e.g. a shared equality helper) then fails: `Found: (o: Any) / Required: Object`.
ssg-md 20 → 19, 10 member digests over 5 `equals` bodies, all other ports byte-identical.
Rule: this is the third value scala types wider than `Object` (alongside a type-parameter-typed value and a wildcard-filled receiver read) — the cast is driven by what was INTERNED for the symbol (`Minter.infoOf`), never by the java/reference node type.

### G8.5 A `null` takes its type FROM THE SLOT, and two slots have no formal to read — **ssg-md 28 → 26. CLOSED**

`null` takes its type from the SLOT (JLS 5.2); two slots have no argument-list formal to read it from — an expression-bodied lambda body (`options -> null`, SAM result type) and an inlined `this(null)` from `CtorFunnel`'s constructor-parameter substitution (emitting `null.getAll()` where java read `other.getAll()`).
ssg-md 28 → 26, 16 member digests over 4 declarations, every check flat; exception found: a formal typed `T | scala.Null` needs no ascription (states its own default).
Rule: ascribe ALWAYS at an inlined null, narrowly at a lambda body (only where `Null` fails to conform); resolve the target variable from the receiver's own instantiation composed along the hierarchy; collapse override-equivalent SAM re-declarations (JLS 9.8) to one abstract method.

### G9. Scala CHECKS an F-bound where javac does not

(a) engine.
Symptom: 43×E057 — erasing an F-bounded variable to `Object` (what javac does) fails scala's bound check (`Node[Object,Object,Actor]` where `Object` cannot satisfy `N <: Node[N,V,A]`).
Cause: scala CHECKS an F-bound at the erased type where javac does not; latent for the project's whole history, only appeared once the typer went green (see M1).
Fix needed: F-bound-aware erasure — erase `N` to its OWN bound with the recursion cut, not to `Object`.
Triage (2026-09-04): FAMILY G: F-bound-aware erasure at the callee formal — "Fix needed: F-bound-aware erasure — erase N to its OWN bound with the recursion cut, not to Object" (unbuilt)

### G10. A RAW anonymous class has no faithful Scala image — REFUSED, not approximated
Symptom: a RAW anonymous class with a body has no faithful Scala image (`new ReadOnlySerializer() { … }`) — naming the type argument doesn't help either, since the body is written against the erasure and only overrides under the `Object` instantiation, which fails to conform to the expected parameterised type.
Triage (2026-09-04): REFUSED-BY-DESIGN — family G: a raw anonymous class body typed against the erasure has no argument-position cast naming the callee's own type variable (G12's dead end) — a card would need that cast mechanism built first

### G11. Erasing a RECEIVER to its erased view LOSES members — 7 → **41**

(a) engine.
Symptom: broadening `erasedReceiverView` to fire on a rendered wildcard and consider the callee's result type: 7 → 41 errors, 21×E008 `not a member`.
Cause: `Array[Object]` loses library-specific members the code then calls — the erased view is only safe where the capture is genuinely unusable.
Do not retry: widening the erased-receiver-view trigger this way.
Next: the context-dependent-fill problem needs the FIELD's declared rendering at the READ, not a wider receiver cast.
Triage (2026-09-04): FAMILY G: context-dependent fill at the field's declared rendering — "Next: the context-dependent-fill problem needs the FIELD's declared rendering at the READ, not a wider receiver cast"

### G12. A callee's own type variables do not resolve at the call site

(a) engine (frontend-spoon `SpoonTir` + emitter `TirEmitter`).
Base: a callee's own type variables have no meaning at the call site, so argument-position raw→parameterized coercion renders a `?T` stub — `uncheckedGeneric` declines. Working shape: `appliedCtorArgs` coerces `new C<targs>(args)` arguments with C's own parameters replaced by the explicit type ARGUMENTS (`rawCtorArgs` for the raw counterpart).
Place 2 (inherited formal, resolved via the `extends` clause keyed `(declaring FQN, formal name)`): a raw argument at an INHERITED generic method needs java's unchecked-conversion cast; gated on array-DIMENSION agreement to avoid trading a compile error for a run-time `ClassCastException` (G26, still open). ssg-md 67 → 58 with the guard (67 → 49 without it — 9 sites unsafe).
Found-not-fixed at place 2: `argSlots` (dispatch) and `coerceArgsFixed` (cast) read different formal sources (erased vs un-erased), so JS-G09 stays flat at `fired 144`. Widening `uncheckedSlot` with the inherited-formal predicate was tried and REVERTED — inert and perturbs its own `tpe`-lowering denominators. Real fix: `argSlots` reading the declaration's un-erased formals — its own step, touches all 15 ports.
Place 3 (RECEIVER's own instantiation, at a `null` argument): `receiverTypeArgs` was gated on `tpConcrete` (false for a type parameter) — widened via `tpNameableHere`/`sameVarInScope`; `coerceArgsFixed` also needed `recvSubst` threaded in. ssg-md 43 → 40 (3 closed where census predicted 2 — `OrderedMap#addNulls`'s cast survives `CollectionsTransform`'s `add`→`+=` rewrite). 16 member digests, all attributable (5 closed-site, 7 correct-but-unnecessary over-approximation casts, 4 M10 key-renumbering).
Place 4 (EMITTER's `numericOverloadAscription`, naming a callee's whole declared signature including a class type parameter like `S` in `class B<S extends B<S>>`): resolved via `ParentSubst` composed with the receiver's own application; declines (keeps java's error) on a RAW receiver at either the bare variable or a top-level-wildcard result. ssg-md main 35 → 34, test set 42 → 40; 2 member digests, all other 13 lanes byte-identical.
Triage (2026-09-04): FAMILY G: argSlots reading declaration's un-erased formals — "Real fix: argSlots reading the declaration's un-erased formals — its own step, touches all 15 ports"

### G13. `rawCtorArgs` erased-formal fallback — THREE gates, all worse; and what each taught

(a) engine.
Symptom: `rawCtorArgs` erased-formal fallback attempts, all worse: no gate (cast every formal mentioning a class type var) 2→23; + skip when arg already shares target's head constructor 1→5; + require a sibling arg pin the instantiation to its erasure 1→43 (E057).
Cause: head-constructor gate is correct/necessary (casting `Array[Foo]→Array[Object]` erases the argument's own type argument, losing members — same failure as G1/G11); "pinned by sibling" cannot be decided from recorded types because Spoon types a class literal (`Texture.class`) as raw `Class`, collapsing to the erasure and falsely marking every loader as pinned.
Do not retry these three gates — the problem was later solved by INVERTING direction (`rawCtorSpecialisation` casts the erased argument UP to the binding a precise sibling implies, instead of casting precise DOWN to erasure).
Note: the engine's recorded type is not a reliable witness of what emitted Scala will infer (§0) — a good candidate for the unportable marker, since the java is exploiting raw-type unsoundness.
Triage (2026-09-04): SUPERSEDED — rawCtorSpecialisation — "the problem was later solved by INVERTING direction (rawCtorSpecialisation casts the erased argument UP to the binding a precise sibling implies …)"

### G13.5 A SLOT TEST THAT READS THE RECORDED JAVA TYPES IS BLIND WHERE JAVA'S OWN ERASURE COLLAPSES THEM — **ssg-md 7 → 6. CLOSED**

`arrayCovSlot` (JLS 10.10 array covariance cast) compares recorded java array types, blind where java's OWN erasure already collapsed them to one type (`<E extends Enum<E>> E[] getUniverse(Class<E>)` — both sides read `java.lang.Enum[]` in Spoon, so no cast is inserted, though the emitted term is `Array[E]` at an `Array[Enum[?]]` slot and scala arrays are invariant).
Fixed by asking the same question of the RENDERED types instead (`arrayCovRendered`, same `arrayCov` gate) — ssg-md 7 → 6, 2 member digests, corpus-wide flat.
Rule: where java's erasure already unified two recorded java types, compare the EMITTER's RENDERED types instead (§0's rule read at a slot).

### G14. Under `noClasspath`, a REFERENCE erases and a DECLARATION does not — CLOSED
Attempts: consulting the REFERENCE formal (not declaration) under noClasspath 13→28; disabling array covariance for a generic array formal 13→28; same in "result shares argument's type var" form 10→26; unconditional bound-erasing of a callee formal +47.
Fix: drive a synthesized cast from the DECLARATION's formal, never the reference's; check type-variable identity by the id its declaring type minted (`<owner>$$T`), never by name.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: drive a synthesized cast from the DECLARATION's formal, never the reference's" — shipped rule, no residue named

### G15. Gate synthesized casts on the BARRIER-AWARE frame, not on name resolution — +2 — CLOSED
Symptom: gating the name-directed fill on `resolveTypeParam` (name resolution) instead of the accessible-frame check: +2 `Not found: type T`.
Fix: gate on `accessibleTp` (`SpoonTir.tpAccessibleHere`), the barrier-aware frame, not name resolution.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: gate on accessibleTp (SpoonTir.tpAccessibleHere), the barrier-aware frame, not name resolution" — shipped rule closing the measured +2

### G16. Casting a type-parameter argument to `Object` when the resolved formal is PRIMITIVE — inert — CLOSED
Symptom/status: casting a type-parameter argument to `Object` when the resolved formal is PRIMITIVE — reasoning sound (a type variable can never denote a primitive, so Spoon mis-resolved the overload) but the rule fires NOWHERE.
Triage (2026-09-04): CLOSED-IN-FACT — "the rule fires NOWHERE … Recorded so this sound-looking rule is not rebuilt"

### G17. `selfRawFormalArgs` and `rawToParameterized` — already covered, do not re-add — CLOSED
`selfRawFormalArgs` (raw arg into self-typed formal, `Cell.set(Cell)`): 0 change — `uncheckedGeneric` already emits that cast; would only append a duplicate identical `asInstanceOf`.
Triage (2026-09-04): CLOSED-IN-FACT — "already present and strictly more general as SpoonTir.uncheckedGeneric" — nothing to add

### G18. An inner class of an ANCESTOR is in scope by simple name

(a) engine.
Symptom: an inner class of an ANCESTOR referenced by simple name is illegal, not merely verbose to project (`ObjectMap.Entries` used from `OrderedMap[K,V]`; `TextArea` exporting `TextField.TextFieldClickListener`).
Cause: a type nested in an ancestor is an inherited MEMBER type; a nested-type path also picks its separator PER LEVEL.
Triage (2026-09-04): FAMILY G: inherited-member nested-type-path resolution — no Fix line is stated at all (Symptom/Cause only)

### G19. An override's TYPE-PARAMETER BOUNDS must follow the PARENT — four measured dead ends

(a) for the rule; bounding only the overridden overload of an injected substitute is (b) policy in that library's manifest.
Symptom: java's `<T>` means `<T extends Object>`, rendered `[T <: Object]` correctly — but `classOf[Int]` is `Class[Int]` and `Int` is not `<: Object`, so a call passing a primitive class literal needs the bound absent (1 site wants the bound, 16 want it gone).
Attempts: give substitute bound `[T <: Object]` 1→16 (all `Class[Int]` vs `Class[Object]`); + write java's own static type via cast 1→21; drop java's implicit Object bound on METHOD type params 1→7 (clean refutation — the bound IS load-bearing wherever a method's T flows into a class's T); pin T + cast literal + give substitute the bound 1→52 (52 unexplained `equals(Object)` vs `equals(Any)` clashes — root cause NOT understood, do not re-run without that answer).
Cause found via tracing: Spoon reports `actuals=1` for a call carrying a primitive class literal (hands back the INFERRED type argument with no explicit source syntax) — existing code deliberately declines this case.
Fix direction: take an override's type-parameter bounds from the OVERRIDDEN member, via an injected parent's signature (same channel `TirEmitter(program, externalConcrete)` opened for diamond disambiguation) — extend that, don't invent a second channel.
Triage (2026-09-04): FAMILY G: override bounds taken from the OVERRIDDEN member via an injected parent's signature — "Fix direction: … extend that, don't invent a second channel" (direction only, unbuilt)

### G20. A STATIC member sees NONE of its class's type parameters — carry it in the FRAME, not a flag — CLOSED
Symptom: 3×`Not found: type T` on gdx-vfx's `PrioritizedArray` — a per-class object-pool idiom with a raw `static class Wrapper<T>` holding a raw anonymous `Pool<Wrapper>` whose instance method (`newObject`) could wrongly still see the outer class's type parameter.
Numbers: 0 members moved on libGDX core, libGDX test, Ashley, anim8, simple-graphs, noise4j, jbump — no other corpus library writes a generic static in a generic class.
Fix: carry accessibility in the type-parameter FRAME, not a resettable flag — static `execDef` starts from an empty accessible map plus its own formals; static `fieldDef` pushes an empty frame around its initialiser; everything lexically inside (including anonymous classes) inherits it with nothing to reset.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 0 members moved on libGDX core, libGDX test, Ashley, anim8, simple-graphs, noise4j, jbump"

### G21. A RAW result read through an ERASED RECEIVER must be TYPED as what it emits

(a) engine — frontend-spoon (G11's erased-receiver view, `knownReceiverArgs`, argument-erasure derivations).
Symptom: `Found: Wrapper[Object] / Required: Wrapper[T]` (gdx-vfx); reorder attempts hit `E035 Unbound wildcard type`, `E081 Missing parameter type`, `E008 value getName is not a member of Object`.
Cause: the call node kept Spoon's raw declared result instead of the erased instantiation; the argument side is THREE separate erasure derivations (`eraseDependentArgs`, `knownReceiverArgs`, `coerceArgsFixed`) that disagree once the receiver view is narrowed per position; and thirteen `SpoonTir` matches have a wildcard arm shadowed by a preceding type-parameter arm (`CtWildcardReference extends CtTypeParameterReference`), making "is this nameable" answer `false` for anything containing `?`.
Numbers: receiver retype+guard extension: 0 members moved on all other ports. First per-position attempt: ssg-md 13→11, libGDX 0→1 (regressed, reverted). Blanket arm-reorder attempts: ssg-md 5→8 (both variants). `TypeShape` unification (wave 17): 0 member digests on all 17 port reports. Final narrowed per-position rule: ssg-md 2→1, other 15 lanes byte-identical.
Do not retry: reordering the 13 wildcard-shadowed match arms blanket (measured worse, 5→8 twice); shipping the per-position argument rule before the 3 erasure derivations read one table.
Next step: unify the 3 argument-erasure derivations — the remaining ssg-md row is exactly the positions `writtenAt` declines because a type variable is written there. The residual error is itself a post-typer `E057` (RefChecks-adjacent, gated behind K28), not a typer error.
Triage (2026-09-04): FAMILY G: unify the 3 argument-erasure derivations — "Next step: unify the 3 argument-erasure derivations — the remaining ssg-md row is exactly the positions writtenAt declines"

### G22. A method TYPE PARAMETER constrained only by its BOUND infers `Nothing` in Scala and its BOUND in java — CLOSED

An unconstrained method type parameter consumed only by a member selection is instantiated at java's BOUND but scala's LOWER bound (`Nothing`), failing the selection. Closed by `SpoonTir.pinUnconstrainedTypeArgs`, pinning java's resolved bound under four conditions (no formal mentions the variable; no target type; every variable has a real bound; the bound names no other type variable) — the fourth condition needed `mentionsNamedTypeVar`'s wildcard-first arm order since `CtWildcardReference extends CtTypeParameterReference` made every `?` read as F-bounded.
Numbers: liqp 4 → 3 (one site); libGDX 0 errors, 0 member digests.
Rule: see CLAUDE.md §6 ("never cast to `scala.Nothing`") — the same language disagreement met at a cast.

### G23. Java's `?` is bounded by `Object`; scala's is bounded by `Any` — and the gap is one operation wide — CLOSED
Symptom: `Found: Buffer[?] / Required: IterableOnce[Object]` at `dst ++= src` where `src: Buffer[?]`.
Numbers: liqp 26 → 22, four sites (`Push`, `Unshift`), 4 member digests, no other port moved.
Fix: rewrite `dst.addAll(src)` to `JavaCollections.addAll(dst, src)` (performs java's own unchecked read, returns java's `boolean`), keyed structurally on the SOURCE's sole type argument being a `TypeBounds` — nothing wider.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: liqp 26 → 22, four sites …, 4 member digests, no other port moved"

### G24. Java's `<T>` bound is VACUOUS and the emitted `T <: java.lang.Object` is not — 1 error, OPEN. THE FIX WAS BUILT AND MEASURED AT 0 -> 50; DO NOT RETRY BLIND

(a) engine — frontend-spoon (`TirEmitter.typeParam`).
Symptom: `Found: …[Serializable] / Required: …[A]`, `A is a type variable with constraint <: Object` (reproduced standalone on scalac 3.8.4).
Cause: unbounded java `<T>` implicitly means `T extends Object`, admitting every reference type incl. `java.io.Serializable` (rooted at `Any`, not `AnyRef`); the port emits that bound LITERALLY (`T <: java.lang.Object`), which is not vacuous in Scala 3's lattice.
Numbers: dropping the bound everywhere: libGDX 0 → 50 (49 × §4.4 `eq`-needs-`AnyRef` breaks, 1 × wildcard capture). Method-only drop: libGDX 0 → 6 (method `T` used as a class type argument, e.g. `Array#of`/`#with`, `AssetManager#load`). `members.tsv` blast: 174 (both-halves) / 143 (method-only), 0 changed check counts either way. Target: liqp 1 error (`ComparingExpressionNodeTest`, `List<List<Serializable>>`).
Do not retry without also fixing: (1) `SpoonTir.referenceIdentity`'s `asRef` to cover a type-variable operand (the `eq` half, repairable); (2) the wildcard-capture family, where `Array[?]`'s element capture is tied to the declared bound with no argument slot to state the difference (not repairable at the operation). No site-local fix exists for the liqp error either — pinning the lub to `Object` just moves the mismatch to the enclosing `List<List<Serializable>>` declaration (`Buffer` is invariant).
Also: hand-written overrides depending on the emitted bound (e.g. `Json.scala`'s `readValue[T <: Object]`) move with any change here and are not independently choosable.
Triage (2026-09-04): FAMILY G: F-bound erasure of an unbounded `<T>` — heading itself: "1 error, OPEN. THE FIX WAS BUILT AND MEASURED AT 0 -> 50; DO NOT RETRY BLIND", two named unrepaired families

### G25. A member SYNTHESISED INTO A SUBCLASS carries the PARENT'S SCOPE with it — **41 of one port's 42 `Not found: type` errors, 243 → 201. CLOSED**

Three mechanisms (diamond-disambiguating forwarder, synthesised primary ctor, constructor replay) copy a parent-declared signature into a subclass that declares none of its type parameters, giving `Not found: type T`/`N`. Unified into one substitution, `ParentSubst` (previously four separate spellings, two of which didn't have it), completed over `TypeRepr` including non-generic parents in the chain and a forwarded generic method's own type parameters.
Numbers: 41 of 42 `Not found: type` errors closed, port total 243 → 201; blast 13 declarations; one port-map row moved (`synthesised-primary` → `widest-root`).
Rule: it is a SUBSTITUTION, never an erasure; the 1 residual error is a different mechanism (a call-site ascription typed off the callee's declaring class, not an `extends`-clause substitution).

### G26. A `T[]...` slot's ARITY is decided by ASSIGNABILITY, and the port reads it as "both are arrays" — **two fixes measured WORSE with the element type unanswered (ssg-md 81 → 83, and 81 → 81 at `markers` 0 → 1); shipped once it had one, at ssg-md 58 → 49. CLOSED**

Java packs a vararg argument into a one-element array whenever it is assignable only to the COMPONENT and not the parameter's ARRAY type (`dims(arg) >= dims(comp) + 1`, probed against javac on 5 cells) — the port had read it as "is the argument an array", wrong for `H[]...` slots. Shipping the arity fix alone needed the element type, which needed G12's inherited-formal cast.
Numbers: declared-component-only: `markers` 0 → 1 (18 `?H` sentinel refs), regressed. Declared-component + raw-inference guard: ssg-md 81 → 83, worse. Shipped (dimension test + `inheritedFormal` lookup + `coerce`'s element-cast fix): ssg-md 58 → 49, `markers` 0 → 0, all other ports flat.
Rule: arity and element type are two separable questions; do not ship the dimension test until the element has a source. Reference port's hand-written pack (`AstActionHandler`) independently confirmed both halves.

### G27. An EXTERNAL member's SYMBOL answers a question it was never told the answer to — the `Type::method` split read off `Flags` and `MethodType` fails in OPPOSITE directions. **ssg-md 47 → 45. CLOSED**

The static-vs-unbound-instance split and arity of a `Type::method` reference were read off an external symbol's `Flags`/`MethodType`, but `Minter.external` interns those with fabricated defaults (`isStatic=false`, `MethodType` often `NoType`) — wrong in both directions at once (`java.util.Objects.isNull` mis-read as instance; `Comparable.compareTo(T)` mis-read as nilary).
Numbers: fix moved both facts onto the parsed node (`Tree.MethodRef.referent: Static | Instance(arity)`): ssg-md 47 → 45; the constructor-ref arm (`Type::new` ignoring arity) closed a further 45 → 43; eleven sge/ssg upstream lanes byte-identical (232 `::new` sites unmoved).
Rule: a fact about java syntax belongs on the NODE, never inferred from an external symbol that was never told it.

### G28. A POLY EXPRESSION takes its type from the SLOT, and an OVERLOAD SET is not a slot — **ssg-md test set 25 → 12, main 38 → 37. CLOSED**

A lambda/method-reference argument at a callee overloaded at that arity, whose alternatives differ at that index, fails scalac's poly-expression typing (`E134 None of the alternatives match`) even though javac resolved unambiguously. Fixed by ascribing the argument to java's resolved SAM type via `TirEmitter.polyOperand` (a `Tree.Typed`, never `asInstanceOf` — a cast would elaborate the literal to the wrong SAM first and throw at run time).
Numbers: ssg-md test set 25 → 12, main 38 → 37 (10+12 member digests). Over-approximates harmlessly on 2 other ports (simple-graphs, screenmanager: 3 extra digests, 0 errors, stated per CLAUDE.md §5 rather than narrowed). liqp: `portability(all|emitted)` 54 → 55, one new real `Callable` usage from the ascription.
Do not retry narrowing further: no rule distinguishing the one site that fails from the two that resolve unaided has been found (tried: nilary-literal, primitive-vs-typevar alternative — both disproved by counter-examples in the same corpus).

### G29. An ANONYMOUS CLASS's constructor is one the PARSER SYNTHESISES, so every call-site rule keyed on the callee's DECLARATION answers about a member java never wrote — **ssg-md 19 → 18. CLOSED**

`new P(args){…}`'s executable reference resolves to the synthesised anonymous subtype's constructor (Spoon: one untyped parameter, `isVarArgs=false`), so vararg-pack logic (`declParams`) read it as non-variadic — loud (arity error) if the parent is overloaded, silent (scala auto-tuples 2 args into a `Tuple2`) if not.
Numbers: fix resolves an anonymous executable against the SUPERCLASS constructor per JLS 15.9.5.1 (erased param types first, arity fallback) in the shared `declParams` lookup: ssg-md 19 → 18, 1 member digest, `catalog` JS-G37 fired 135 → 136; every other port unmoved.
Rule: fix the shared lookup (`declParams`), not each caller (`varargPack`) — it is one question and both readers must get the same answer.

### G30. A RAW BOUND is name-FILLED from the ENCLOSING declaration, and at a type that is not the declaring one the names are a COINCIDENCE — **the FIRST post-typer error the corpus has ever produced. CLOSED per SLOT, ssg-md 1 -> 1 and the 1 is a DIFFERENT error**

`nameFilledArgs` fills a raw bound-as-type-argument from in-scope names, correct for java's F-bound idiom where the raw type IS the declaring type; wrong where a different interface happens to reuse the same parameter spellings (`ReferencingNode`'s `B` vs `ReferenceNode`'s own `B <: Node`), producing `E057 Type argument B does not conform to upper bound Node` — the corpus's first post-typer error, invisible below 0 typer errors (CLAUDE.md §3).
A declaring-type-only test was tried and refuted (breaks libGDX's `Tree`/`Node` self-reference at 0 errors). Closed by `SpoonTir.licensedFills`: license each slot only from a structural fact (identity F-bound; unbounded formal; same spelled bound; or an unreadable bound), applied as a FIXPOINT since a declined slot substitutes as a projection other bounds can still name.
Numbers: ssg-md 1 → 1, but the 1 is a DIFFERENT error (moved to an inferred type in `SegmentedSequenceFull#create`) — evidence that post-typer risers are SERIALISED one phase at a time; see CLAUDE.md §3.

### G31. An F-BOUNDED type applied to a WILDCARD cannot CAPTURE-CONVERT, so no extension method reaches it — **CLOSED; the riser that unlocked `RefChecks`, ssg-md 1 -> 131**

`for (x : Iterable<?>)` over an F-bounded iterable (`ISegmentBuilder<S extends ISegmentBuilder<S>>`) fails scala's capture conversion: dotty substitutes `Any` for the wildcard, producing `ISegmentBuilder[Any]` where the self-bound needs `ISegmentBuilder[CAP]` — `E057`. No wildcard spelling repairs it (probed on scalac 3.8.4, including java's own `? extends ISegmentBuilder<?>`).
Closed by `SpoonTir.iterableOperand`: render the operand at the SUPERTYPE java actually iterated at (JLS 14.14.2), an upcast (K18's reified exclusion applies), guarded to F-bounded receivers only and declining where the found element type mentions a type variable.
Numbers: ssg-md 1 → 131, on 4 changed member rows — this is `RefChecks` running for the first time in the corpus (K28's wall), not a regression.

### G32. Scala 3 will NOT eta-expand a NULLARY method from a bare name, so `Type::nilaryStatic` is the one static method reference that has to be a LAMBDA — **ssg-md-ext 3 -> 0, and BYTE-IDENTICAL on all fourteen lanes. CLOSED**
Scala 3 eta-expands a paramful method against an expected SAM but refuses a nullary one (`method … must be called with () argument`), so a `Type::getX` static ref used as a `Supplier`-shaped SAM emitted a non-typechecking field read instead of a lambda.
Fixed via `Referent.Static(arity)`, arity read off the `MethodRef` NODE (`SpoonTir.referentOf`), not the symbol (an external member interns with no `MethodType`). ssg-md-ext 3 -> 0; byte-identical on all 14 `measure-all` lanes.
Rule: split emission by ARITY, not SAM shape; only the nullary static case needs wrapping in a lambda, every other arity keeps the qualified-name reference.

### G33. G23's gap read at a READ, and the DOUBLE ASCRIPTION the fix leaves — **USL 2 -> 0, libGDX 0 -> 0 at one declaration. CLOSED, with a cosmetic residue OPEN**
Raw `Iterator.next()`'s implicit `Object` bound (JLS 4.4) meets `CollectionsTransform`'s unbounded `JavaIterator[?]` shim, whose capture is bounded by `Any`: `Found: it.A / Required: Object`. Two obvious guards (result-is-capture; declared-result-is-not-Object) both fired on 0 sites because the frontend records java's own substituted answer and `Iterator#next()` interns with no signature at all.
Fixed structurally via `CollectionsTransform.capturedObjectRead`, keyed on "receiver is one of the four owned shims with a sole wildcard type argument, none of whose 76 members return bare `Object`". USL 2 -> 0; corpus-wide exactly one other site (libGDX `CharArray#appendAll`), 2 member digests, all check counts flat.
Residue (OPEN, cosmetic): where the frontend already coerced the argument (G14), emits a harmless double `.asInstanceOf[Object].asInstanceOf[Object]` — belongs to `TirEmitter`'s general `Tree.Typed` collapsing arm (owes JS-G34/JS-E06), not fixed here.
Triage (2026-09-04): FAMILY G: cosmetic double `.asInstanceOf[Object].asInstanceOf[Object]` belongs to TirEmitter's general `Tree.Typed` collapsing arm (owes `JS-G34`/`JS-E06`), not fixed here

### G34. Under `noClasspath`, Spoon's `getExecutableDeclaration` resolves to an UNRELATED type's method that shares the name — **gltf 5 errors, 2 of which are D14's; gdx 0 = 0. CLOSED**
`Gdx.app.getType()` lenient-resolved by name alone to `java.lang.reflect.Field#getType`, so the frontend interned the call under the wrong type and the emitted error named a member never called.
Fixed with `declAgrees`: a structural guard requiring the resolved declaration's owner to be the receiver's static type or a supertype (BFS via `typeDeclarationOf`); disagreement falls through to the reference branch, interned under the receiver's own type. gdx 0 = 0 flat; gltf's 5 errors are the pre-existing D4/C3 floor (3) plus D14 rename-propagation gaps (2) — the fix only corrected the diagnostic's named type.
Rule: see CLAUDE.md §4.56 — resolve/classify structurally, never by name.

## 2. Constructors

### C1. Never promote a paramful constructor to the primary without a WHOLE-PROGRAM check — +14 — CLOSED
Symptom: `E134 None of the overloaded alternatives` on a subclass with a bare `extends P` when `P`'s primary was promoted paramful.
Numbers: deleting the fixpoint for SYNTHESISED plans: 0 -> 4 errors, omissions 180 -> 196 (guard now gated on `reachableArgumentFree`, not "java wrote a bare extends"). Fallback bug (dropping straight to `nilaryPlan`) cost dropped-supers 30 -> 79; fixed via `plan0(synthesis=false)` fallback, restoring 30.
Triage (2026-09-04): CLOSED-IN-FACT — "Pinned by SyntheticPrimaryWithholdingSpec"

### C1.5. `primary.isEmpty` is NOT "nothing was nominated" — 109 escaping paths came back — CLOSED
Symptom: promoted-body escapes regressed to 95 on libGDX core (`CharArray` alone re-gained 9).
Numbers: escapes 95 -> 31 once guarded on `Plan.isSynthesised` (not `synthetic.nonEmpty`).
Rule: every "is this a synthesised primary" predicate must go through `Plan.isSynthesised`; a marker-only-disambiguated class has an empty slot list and reads as unsynthesised under the naive test.
Triage (2026-09-04): CLOSED-IN-FACT — "Pinned by SyntheticPrimaryWithholdingSpec (both directions)"

### C1.6. A `val` derived from a WHOLE-PROGRAM write count does not survive a DEPENDENT — 7 -> 23 — CLOSED
Symptom: `E052 Reassignment to val` on dependent-written base fields (gdx-gltf: `ShaderProgram.vertexShader/fragmentShader`, `PBRFloatAttribute.value`).
Numbers: write-count-alone rule: gltf 7 -> 23 errors. Narrowed to a JAVA fact instead (`final || private`, neither of which can drift from outside): libGDX core 5 `val` of 53 hoisted slots vs 20 under the wide rule.
Triage (2026-09-04): CLOSED-IN-FACT — "pinned by SyntheticPrimarySlotsSpec"

### C2. A promoted constructor's parameters AND top-level locals become MEMBERS
(a) engine — `CtorFunnel` / renaming.
Symptom: inlining a promoted body without renaming what it declares collides with real fields (`GLVersion.vendorString`, `PolygonRegion.textureCoords`), including the INHERITED-name case, which silently captures an unqualified read instead of failing to compile.
Rule: see CLAUDE.md §4.55 — read before writing any renaming pass.
Triage (2026-09-04): SUPERSEDED — CLAUDE.md §4.55 — "Rule: see CLAUDE.md §4.55 (read before writing any renaming pass)"

### C3. `super(args)` in a secondary constructor — and why PADDING is not a fix
(a) engine — `CtorFunnel` (throwable padding, synthesised primary, branch replay, parent-delegation inlining); count it, don't guess (like C7).
Symptom: no root passes its own params straight through and no root is nilary -> delegation falls back to `this()`, silently constructing a differently-configured object (0 compile errors); refused loudly it's `E134`.
Cause: multiple roots calling different `super(...)` overloads with nothing to promote as a delegation target.
Numbers: padding a shorter super call to reach a wider parent ctor measured 0 -> 55 errors — exact only for the fixed JDK-Throwable family, a guess elsewhere; 49 non-throwable sites left COUNTED instead. Throwable family fixed via synthesised primary at the widest overload, read off the TARGET ctor's formals (not the arguments, which lose subtype info matched by head name): liqp omissions 4 -> 1, errors 31 -> 31, 8 digests (`LiquidException`). Branch replay split into MAY-assign prologue / MUST-assign replay (two functions, not one — a branching `if` prologue is neither): ssg-md omissions 61 -> 54, 17 digests, 0 -> 0 errors, CommonMark conformance 1828 -> 1870/1870, every other port byte-identical. Parent-delegation inlining + post-body replay (wave 3.1ax): visui omissions 64 -> 52, gltf 0 unchanged; VisWindow still `E134` (guard 3: param read >1x with a non-simple argument).
Post-body through parameters (item 4, 2026-09-04): the child's synthesised primary carries the parent secondary's post-body inputs as parameters (null-guarded for typed params, boolean-guarded for param-less post-bodies like `ownsTexture = true`); each secondary evaluates the argument once in its `this(...)` call. DistanceFieldFont omissions 0 -> 2 (COUNTED: `region` used 2x in `BitmapFont`'s delegation head `region != null ? Array.with(region) : null` — binding delegation-head args to synthesised slots is the next mechanism, same shape as the post-body slot). VisWindow still E134: the withholding fixpoint (C1/C7) demotes the synthesis because `VisDialog extends VisWindow` reaches it nilary.
Residue: padding to `(null, null)` sets `cause` where java leaves it unset. sge-visui: 45 dropped-super sites, only 3 are errors (`VisScrollPane`/`VisSlider`/`VisWindow`) — the loud form is strictly better; don't chase the silent 42.
Not retry: matching super-call args to fill slots by HEAD NAME; consulting the throwable synthesis before K5.5's "leave alone" fence (cost libGDX omissions 46 -> 50).
Next step: guard-3 residue — synthesise a primary that takes the evaluated argument as a parameter so the post-body can reference it without double evaluation.
Triage (2026-09-04): FAMILY C: ctor funnel shapes (subplan item 4) — "Next step: guard-3 residue — synthesise a primary that takes the evaluated argument as a parameter so the post-body can reference it without double evaluation"
Next step: VisWindow requires `formalsOf` to resolve `Window.WindowStyle` (a nested-class type); a post-body that references NO param but has side effects (DistanceFieldFont's `ownsTexture = true`) needs a boolean guard parameter.
Next step (item 4c): VisWindow's withholding-fixpoint gap — `VisDialog`'s nilary `this()` reaches the synthesised primary through `this(title, true)`, which the fixpoint's `reachableArgumentFree` needs to recognise.
Item 4c (2026-09-04): `resolvedThroughParentPlan` resolves a child's diverging `super(args)` through the parent's already-computed synthesised plan (`Plan.rootArgs`). Plans are computed parents-first (topological order); the `needNilary` fixpoint excludes synthesised children (they pass slots, not nilary extends). A non-owned parent without a published plan is refused and counted (E134, `droppedSuperArgs`). visui 4 -> 3 (VisWindow E134 resolved); gdx 0 held; gdx-test 187 passing / 4 failing held.
Item 4e (2026-09-04): synthesised slots substitute parent scope for constructor type params (`ctorTypeParamSubst` -- `T extends Texture` becomes `? <: Texture`) and value-typed post-body inputs default to JVM zero with a boolean guard; delegation values map against `uniquePbParams` (a root going through a 2-arg chain fills defaults for the 7-arg chain's params). gltf 8 -> 0 (`PBRTextureAttribute`/`PBRCubemapAttribute`).
Item 4f (2026-09-05): delegation-head slot -- a parent param used >1x in the delegation head with a non-simple caller arg is bound to a synthesised slot (`region$dh`) evaluated once; the `extends` clause uses `if (dhSlot != null) <expr> else supSlot` so direct roots fill supSlot and indirect roots fill dhSlot. A substituted `null != null` condition is simplified away (Scala 3 parser issue with `null.asInstanceOf[T]` in `this(...)` arguments). gdx omissions 43 -> 35 (DistanceFieldFont resolved); visui omissions 53 -> 40; gdx 0 errors held; gdx-test 187/4 held.

### C4. Several roots, none nilary, plus an explicit nilary constructor = a clash with no plan — CLOSED
Symptom: synthesised Scala primary collides with an emitted `def this()`.
Fix: when several roots exist, none nilary, but an explicit nilary ctor exists, promote THAT one and inline its `this(args)` delegation via `effects` — sound wherever `supersedes` holds.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: … promote THAT one and inline its this(args) delegation via effects — sound wherever supersedes holds"

### C5. Constructor REPLAY repeats work Java did once — a declared cost, not a defect — CLOSED
Symptom: replaying a parent's statements after `this()` re-runs allocation (e.g. `new DelayedRemovalArray(1000)` re-allocates and discards a 16-element array) — a declared COST that replaces a previously wrong-array bug, not a new defect.
Triage (2026-09-04): CLOSED-IN-FACT — "a declared COST that replaces a previously wrong-array bug, not a new defect" — accepted by design

### C6. Do NOT tighten `supersedes` to inspect assignment RIGHT-HAND SIDES — it removes no effect and costs the argument
(a) engine, fix belongs at the PROMOTION (C7), not at `supersedes`.
Symptom/temptation: `supersedes`'s docstring claims the prologue is "invisible" when pure field assignment, so an escaping RHS (e.g. `this.n = Registry.register()`) looks like a bug to fix by requiring an effect-free RHS.
Cause it must NOT be fixed there: `this()` runs the prologue BEFORE `supersedes` is even consulted, so refusing the replay doesn't stop the escaping call — it only drops the constructor's argument on top.
Numbers (kill-switch probe): replay accepted — escaping call runs, argument delivered; replay refused — escaping call STILL runs, argument LOST + omission finding. 330 accepted replays on libGDX have a non-re-readable prologue RHS, all harmless (allocation/pure JDK static/cast); Ashley's one non-harmless shape (`Family.Builder.get()`, mutates a static cache) is unhelped by refusal either. Tightening would refuse 330+ replays to fix 0 defects.
Real defect is C7 (promoted body running on every path): refusing the PROMOTION costs 0 -> 41 errors, so emission stands and the divergence is counted via `OmissionCheck.promotedBodyOnEveryPath`.
Triage (2026-09-04): SUPERSEDED — C7 — "Real defect is C7 (promoted body running on every path); refusing the PROMOTION costs 0 -> 41 errors … divergence is counted via OmissionCheck.promotedBodyOnEveryPath"

### C7. A PROMOTED constructor's body runs on EVERY construction path — refusing it costs 0 -> 41
(a) engine — `CtorFunnel`; the fix is "count it", as in C3.
Symptom: a Scala class body IS its constructor, so a promoted root's body runs on every path a secondary reaches via `this(...)`, where java ran disjoint bodies — `Material` bumps a static id counter twice, `Button` double-adds a `ClickListener`, `Table` leaks a pooled `Cell`.
Numbers: A2's synthesised `protected` primary (promotes no java constructor, so nothing escapes) retired most of it — libGDX core escapes 140 -> 31, omissions 177 -> 67, 0 errors. Prefix-stripping (`CtorFunnel.Plans.residualBody`, subtracting a canonically-printed matched prefix, never tree-equality) shipped: omissions 193 -> 177, 16 paths across 10 classes repaired (`Button` 4/10). Collapse-recreated-defect fix (skip collapse where the promotion still has an escaping path): omissions noise4j 3 -> 0, libGDX core 67 -> 65 (`Object2dArray`, `Dialog`).
Not retry: blanket refusal of every escaping promotion = 0 -> 41 `E120` errors; a TARGETED refusal of shape-6 (promoted-nilary) non-empty bodies only = 0 -> 35 errors and still 65 escaping paths — same bad trade at 85% of the cost (experiment was `DebugFlags`-gated, not in tree).
Residue: `Material`/`Table` (shape 6) not reached by prefix-stripping, still counted by `OmissionCheck.promotedBodyOnEveryPath`; a SUBCLASS reaching a promoted paramful root via `extends C(args)` is C3's `droppedSuperArgs` domain instead. Corpus reach: 61 classes / 160 ctor paths escape in emitted units (libGDX core 59/156, sg 2/4; Ashley's count is legitimately plan-dependent, not a miss).
Triage (2026-09-04): FAMILY C: ctor funnel shapes (subplan item 4) — "Residue: Material/Table (shape 6) not reached by prefix-stripping, still counted by OmissionCheck.promotedBodyOnEveryPath"

### C8. A SYNTHESISED primary is SHADOWED by a narrower real constructor — the test is APPLICABILITY, not signature equality — **0 -> 2** — CLOSED
Symptom: `secondary constructor must call a preceding constructor` (`DistanceFieldFontCache` delegated to ITSELF).
Numbers: measured 0 -> 2 compile errors on libGDX core when the synthesis first widened past a nilary root. Fixed via a final companion-marker parameter changing the primary's ARITY (removes it from every delegation's candidate set); attempt order is COLLAPSE-if-no-escaping-path (C7) then marker. libGDX core: omissions 177 -> 176 (`DistanceFieldFontCache`), 0 errors, 6 classes gain a marker.
Rule: predicate is per-ROOT, about the ARGUMENTS the emitter writes (applicability + most-specific), not slot-list equality; the ascribed marker type means no second applicability check is needed after disambiguation.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: … libGDX core: omissions 177 -> 176 (DistanceFieldFontCache), 0 errors, 6 classes gain a marker"

### C9. A companion-`private` marker type CANNOT appear in a `protected` primary's signature — CLOSED
Symptom: `non-private constructor C … refers to private class Funnel in its type signature`.
Numbers: re-verified on the engine's own emitted text (protected marker works; private marker is one error per disambiguated class). Pinned by `SyntheticPrimaryDisambiguationSpec`.
Fix: the marker must be `protected` in the companion (not private, not public) — compiles, runs, reachable from a subclass `extends` clause in another package, at both the primary and a secondary.
Triage (2026-09-04): CLOSED-IN-FACT — "Pinned by SyntheticPrimaryDisambiguationSpec"

### C10. `uninitialized` REPLACES THE CAST and nothing else — keyed on the fallback, 2,466 vs 1,184 — CLOSED
Symptom: applying `scala.compiletime.uninitialized` to every uninitialised field silently took back the `T | scala.Null = null` default the nullability phase had just introduced.
Numbers: libGDX core 1,184 placeholders keyed vs 2,466 unkeyed (`a85d8872`). Emitted only for a field of a CLASS (structural: symbol's owner is a class, never a local `var`) — the naive version cost 0 -> 3 compile errors (`uninitialized can only be used as the RHS of a mutable field definition`).
Rule: only `NullabilitySpec` asserting BOTH halves caught the regression; no other check or count moved.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: only NullabilitySpec asserting BOTH halves caught the regression; no other check or count moved" — fix stands

### C11. A NILARY constructor in front of a NILARY primary cannot be emitted, and all three ways of keeping its delegation are WORSE — 1 site, `omissions 65 -> 66` — CLOSED
Symptom: a nilary java ctor whose delegation carries arguments (`C() { this(seed(), "d"); }`) was silently dropped whenever the class's primary is Scala's implicit nilary one; `new BitmapFont()` built a font with no data/page/glyph. 0 errors, no other count moved.
Numbers: dependent blindness fixed via a `Dropped` MEMBER row (`refusal=ctor-funnel/nilary-dropped(C11)`) — libgdx-core map 19606 -> 19607 rows, `decisions.tsv` 3893 -> 3894, 1 member digest, all other checks on 13 ports flat.
Triage (2026-09-04): CLOSED-IN-FACT — "dependent blindness fixed via a Dropped MEMBER row …, 1 member digest, all other checks on 13 ports flat"; downstream witness explicitly "not owed here"

### C12. A PROMOTED CONSTRUCTOR LOCAL keeps its NAME and loses its POSITION — **liqp 161/414 -> 357/218 passing, 0 compile errors either side. CLOSED**
`TirEmitter.orderBody` hoisted every `ValDef` — including a promoted constructor's own locals, not just real fields — to the head of the class body, reordering java's constructor sequence and producing an `NPE` on a field read before its (now-later) assignment.
liqp 161/414 -> 357/218 passing, 0 scalac errors either side, 1 member digest (`Template`). Correction (audit-2 F6) also fixed instance-initialiser-block ordering within JLS step 4: 16 member digests over 5 ports, every suite outcome unchanged.
Rule: hoist only members OWNED by the class (real fields plus init blocks, JLS 12.5 step 4, textual order) — never a `ValDef` owned by the constructor itself (promoted locals stay in place, in java's order); ownership is decided structurally via the frontend's `owner`, never by node kind, name, or origin line (CLAUDE.md §4.56).

### C13. A DORMANT `Flags` BIT IS A RENDERING RULE NOBODY WROTE — populating `isSealed` emitted `sealed` at every hierarchy the rule had just refused — CLOSED
Symptom: populating `Flags.isSealed` made every sealed hierarchy emit `sealed`, including ones `TirEmitter.sealOf` had just refused — the emitted file carried both the porter note explaining the refusal and the contradicting keyword.
Numbers: 0 member digests moved on all fifteen ports (no corpus library has a java-17 source file); caught by one spec failure in the same commit.
Rule: CLAUDE.md §4.56's fast-path-guard rule, one artifact over — the decision belongs at exactly one place (`sealOf`); the flag stays a fact about the java, never an instruction to the emitter.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 0 member digests moved on all fifteen ports …; caught by one spec failure in the same commit"

### C14. A REASSIGNED constructor parameter is read by the DELEGATION before its `var` exists — **ssg-md 30 → 28. CLOSED**

`MutableParamsTransform` inserted the repurposed `var` after the `super(…)`/`this(…)` delegation but left the delegation itself naming that not-yet-declared local (JLS 8.8.7 makes the delegation the constructor's first statement — an exact substitution, not a repair).
ssg-md 30 → 28 (`Segment$Base`, `Segment$Text`), 3 member digests, every other port unchanged; closed by `MutableParamsTransform.slotsInDelegation`.
Same fix applies to a never-promoted secondary constructor, which emits the identical wrong order.

### C15. A CONSTRUCTOR REPLAY ACROSS A MODULE BOUNDARY DIED ON THE BASE'S `private`, and what it dropped was the WHOLE super call rather than the one statement — **one module measured and SKIPPED at 0 compile errors and 0 tests, then admitted. CLOSED**

Cross-module constructor replay (C3) dropped java's whole `super(args)` when the base's non-public members were unreachable, because the widening set was derived from subclasses the RUN happened to contain rather than the class's own declarations — a copy constructor silently emitted with no children, no `markerSuffix`, no chars, at 0 compile errors.
Fixed via `CtorFunnel.Plans.externalReplayWidenings`, widening only FIELDS (never methods) and only MUTABLE ones: libGDX 535, ssg-md 437, liqp 92, gdx-gltf 36, jbump 30, ashley 20 members widened; ashley `base-surface` 6→0; all 14 lanes flat.
Reach is decided by the STATEMENT's prefix (`other.f` vs `this.f`), not the symbol's access level alone — a replay touching a `private` field or a prefix-selected `protected` METHOD stays refused and counted.

### C16. TWO EXTENSIONS MEASURED AND SKIPPED AT **2 COMPILE ERRORS EACH** — **CLOSED: both gaps fixed, both modules IN at 0 errors, milestone 2 at 29 of 29.** Neither was the family its error code named

`E049 ambiguous reference` was §4.55's capture-clash guard missing that scalac's ambiguity fires for an INHERITED METHOD too, not only a field; `E007` on a `Pair[TocOptions,…]` was not a generics gap at all — `OverrideGraph` compared two descriptors as strings, found no edge, and `CollectionsTransform.applyClassFileOverrides` wrongly treated the empty answer as a class-file override of a member (`java.lang.Enum#parseOption`) that doesn't exist, holding java's `java.util.List` signature under an already-retyped parent.
Both fixes flat across 16 lanes; both modules IN at 0 compile errors on first run, 284→331 units, 180→188 tests passing at expected-lost 0.
Rule: an error's CODE names the shape scalac saw, not which mechanism owes the answer — read the emitted porter note before trusting the diagnosis (ENGINE-LIMITS K28.2, third site).

## 3. `this`, inner classes and anonymous classes

### C16.1 The resolution-root parent AND the inlined test body — CLOSED

`resolveCapturedLocalClashes` had two scope holes: `visibleMembers`'s inherited set was built only from `p.units`, missing a resolution-root parent's direct members; and `TestFrameworkTransform` inlines a `@Test` body as a `Tree.Block` argument (not a `DefDef`/`Lambda`), so the collector never enclosed anonymous classes inside converted tests.
Fixed via a symbol-table fallback for the first and a `discoverScope` post-pass scanning class-body statements for the second; ashley's `EntitySystem#engine` bean pair now compiles at 0 errors (JVM/JS/Native), ashley 108/2/2, `api-parity(accessor)` 2→1.
Rule: a fix targeting `Tree.Lambda` misses a body a later transform inlines as `Tree.Block` — check the actual node shape the upstream transform produces.

### T1. A `CtNewClass` is a SUBTYPE of `CtConstructorCall` — 156 silently dropped bodies — CLOSED
Symptom: translating a `CtConstructorCall` without checking for the `CtNewClass` subtype emits every java anonymous class as a bare constructor call with its body discarded — 156 sites, every button silently doing nothing; only 4 `java.util.Comparator` sites failed to compile, which is the only reason it was noticed.
Rule: if your frontend touches constructor calls, check this subtype relationship first.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix model: … Rule: if your frontend touches constructor calls, check this subtype relationship first" — shipped

### T2. Inside an anonymous body, only a `this` in VALUE position may be rebound — **+33** twice — CLOSED
Symptom: rebinding an anonymous body's `this` in MEMBER-ACCESS position (33→66 `E008`s), and separately treating an untyped `this` as the anonymous instance, both regressed by the same +33.
Numbers: +33 errors from each of the two wrong approaches.
Triage (2026-09-04): CLOSED-IN-FACT — "the existing bare-name resolution (Scala resolves lexically, as Java did) is already correct as an access target"

### T3. An anonymous class has no name, so it cannot be a `this` QUALIFIER — CLOSED
Symptom: Spoon suggests a `this` qualifier like `Pixmap$1`, which names nothing in emitted code.
Fix: emit the reference bare; Scala resolves it lexically to the enclosing anonymous class's member, matching what Java resolved.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: emit the reference bare; Scala resolves it lexically to the enclosing anonymous class's member"

### T4. Qualified `Outer.this` needs BOTH a static guard and a supertype guard — +22 — CLOSED
Symptom: +22 errors when a library nests a subclass inside its own base (`DynamicsModifier.FaceDirection extends DynamicsModifier`) and Spoon reports a plain `this` reaching an inherited member under the member's declaring type.
Numbers: +22 errors closed by the combined guard.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: +22 errors closed by the combined guard"

### T5. "The frontend cannot see it" is NOT "we cannot see it" — CLOSED
Symptom: under `noClasspath`, Spoon does not resolve an implicit access to an inherited instance field to a `CtFieldWrite` at all, so neither the null-target nor implicit-`CtThisAccess` branch of a field-access translator ever sees it; qualifying it as `this.f` measured 0 change (the code path never runs).
Rule: check what the TIR and the emitter already know before recording a blocker, and before writing another frontend branch that will never be reached.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: solved from downstream instead — the TIR already knew the symbol was an instance member of an ancestor"

### T6. A Java `@interface` is an ANNOTATION TYPE — 161 errors if it is not — CLOSED
Symptom: Spoon reports `@interface` as `CtInterface`; emitted as an ordinary `trait` nothing can be annotated with it — 161 errors the instant annotations were emitted (7→179).
Numbers: a suite with no `@Test` emitted runs zero tests and reports success — found only by checking `@Test in Java: 221 / emitted: 0` directly, after two sessions of compile-count work missed it.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: needs explicit Flags.isAnnotation to become class X extends scala.annotation.StaticAnnotation" — shipped, residual annotation-argument handling is a counted lane not an open defect

### T7. Concrete-member DIAMOND — solved at the EMITTER; qualified `super[X]` still has no TIR node

- (a) engine — emitter shipped; TIR node still missing
- Symptom: `class Entries extends MapIterator with JavaIterator` where `remove()` is concrete in both — scala linearisation demands explicit disambiguation java never required (java: `MapIterator.remove()` simply implements `Iterator.remove()`). 11 sites in one module; applies to any java class inheriting a concrete method while implementing an interface with a default for it.
- Fix shipped: `TirEmitter.diamondOverrides` synthesises `override def remove(): Unit = super[MapIterator].remove()` for members concrete from both the superclass chain and a mixin, choosing the SUPERCLASS (the parent java would run) — a rendering repair, not a tree rewrite.
- Remaining limit: `Tree.Super(cls, …)` carries the class but the emitter prints bare `super`; qualified `super[X]` still has no TIR node, so any transform needing one in a TREE is blocked on adding it.
- Also: `export` diamonds must dedupe by DECLARING TYPE (drop the diamond duplicate, keep the most-specific genuine redeclaration).
- Not retry: don't re-derive the diamond-forwarder fix; it ships as-is.

---
Triage (2026-09-04): FAMILY T: qualified `super[X]` TIR node — "Remaining limit: Tree.Super(cls, …) carries the class but the emitter prints bare super; qualified super[X] still has no TIR node, so any transform needing one in a TREE is blocked"

### T8. Enum constants with class bodies — CLOSED

Java enum constants with class bodies lower to a sealed abstract class plus one `case object` per constant; noise4j (first corpus library with the shape) exposed three gaps — constant-body FIELDS dropped silently, missing `override` on constant-body methods, and unhandled init blocks/nested types.
noise4j 7 → 0 errors, 6 member digests, `catalog(consulted) JS-C25` 29→50; every count elsewhere flat.
Fixed by harvesting `CtField` in `SpoonTir.enumCase` (mirroring `anonClass`) and passing `overrides = overridesInherited(m)` instead of the default `false`.

### T9. A method-LOCAL named class is refused by the frontend outright — **CLOSED; the arm was twenty lines and the cost was twenty-eight OTHER walks**

Method-local named classes were refused by `SpoonTir.stmtKind`; zero corpus sites until liqp, where five `@Test`-body sites cost 62/639 tests plus 12 cascade errors.
liqp discovery 575→637/639 (tests lost 64→2), 0 scalac errors before/after, suite 574/1→631/6. The frontend arm itself was ~20 lines; the real cost was replacing 28 hand-rolled recursions with `StandardTraversal.allClassDefs`/`TirEmitter.allDeclaredClasses` (0 member digests on all 11 lanes for that change alone).
Rule lifted to CLAUDE.md §3. Two residues left OPEN at 0 corpus sites, both counted: local-class-vs-inner-class name collision, and method-local enum reference mis-spelling/mis-projection.

### T10. A java ENUM CONSTRUCTOR has a BODY, and it runs. **6 libGDX sides silently broken, 0 errors**
Symptom: enum constructor bodies (beyond pure self-assignment) were silently dropped; fields the body computed stayed at their declared defaults, at 0 compile errors and every check count unchanged.
Numbers: libGDX `Cubemap.CubemapSide` — all 6 sides shipped `up == null`, `getUp(out)` threw; found via anim8's `Dithered.DitherAlgorithm.legibleName` (`toString()` null for 22 constants), only because the same constructor also tripped T11 (a compile error).
Fix: `EnumCtorBodySpec`.
Triage (2026-09-04): REFUSED-BY-DESIGN — family T: an OVERLOADED enum constructor is left entirely untranslated (0 corpus sites so far) — a card would need a case-object-per-overload synthesis the emitter doesn't have

### T11. A PROMOTED enum constructor parameter IS a member — `name` collides with `Enum.name()`, a DECLARED member collides with it, and "supersedes" was a NAME test — **ssg-md 89 → 81 for the third half** — CLOSED
Symptom: `E120 Conflicting definitions: var name: String … and def name(): String …` from a promoted parameter colliding with synthesized `Enum.name()`; separately, a promoted parameter collided with a DECLARED accessor of the same name (liqp `Flavor.isLiquidStyleInclude`); separately, a body FIELD a parameter "supersedes" was matched by NAME alone and wrongly dropped when it was really a different member at a different type (ssg-md `HtmlMatch.open`: `Pattern` field vs `String` parameter) — 8 errors, first mis-attributed entirely to `java.util.regex`.
Numbers: anim8 1 error fixed (synthesized-name suppression); liqp 56→54 (declared-collidee rename); ssg-md 89→81, 6 member digests (type-based supersedes fix); 0 members moved elsewhere for each fix.
Triage (2026-09-04): CLOSED-IN-FACT — "Residue: a promoted parameter superseding nothing still renders as a public mutable var java never had — left as-is" — accepted by design

### T11.5 An OVERLOADED enum constructor: the primary is java's ROOT, and `ctors.head` was not a refusal but a WRONG ANSWER — **2 errors + a silent default, 177 → 175. CLOSED for the expressible shape, COUNTED for the rest**

The emitter picked `ctors.head` (tree order) as the primary instead of java's DELEGATION ROOT; every corpus library so far had only one constructor, so the bug was invisible until an overload existed — one case object got `too many arguments`, another silently took the field's default instead of the value java's delegation supplied.
Fixed to resolve the root by delegation chain and pass each constant its named overload's own arguments (arity-based; refused where two overloads share an arity, per T17). 2 errors + a silent default fixed, 177 → 175, blast 2 declarations, `trivia(recovered)` 4→5.
Every refusal now counted via `OmissionCheck.overloadedEnumCtors`.

### T12. Java `protected` is DROPPED, and accessibility is an input to OVERLOAD RESOLUTION — 1 error — CLOSED
Symptom: emitting java `protected` as bare scala `protected` (after an earlier burn-down dropped it to `""` entirely) breaks same-package-caller access; worse, once restored bare it can turn a unique java overload resolution into `E051 Ambiguous overload`, because java excludes inaccessible overloads as CANDIDATES and scala's bare `protected` (subclass-via-`this` only) makes a `protected` overload public.
Numbers: libGDX core has 867 `protected` declarations; gdx-gltf `AnimationController.setAnimation` produced 1 `E051`, surfacing a port and three weeks after the original drop; 20 same-package non-subclass caller sites in libGDX core need bare-`protected`-breaking access.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix/rule: emit protected[<emitted package tail>] … Do not simplify to bare protected" — shipped, residual widenings named and accepted

### T13. `Enum.ordinal()` is part of every java enum's SURFACE, mentioned or not — CLOSED
Symptom: `value ordinal is not a member of …` — `Enum.ordinal()` (final in java, present on every enum whether mentioned or not) was never synthesized alongside `name()`/`values()`/`valueOf()`.
Numbers: libGDX core 69 members, libGDX test 71, Ashley 75, anim8 71, noise4j 6, simple-graphs 0, jbump 0 changed; no error or check count moved anywhere.
Fix: an abstract member on the sealed class plus `override def ordinal(): Int = <index>` per constant (O(1), matching java's own field read; `values().indexOf(this)` was rejected as O(n) and allocating). Suppressed WHOLE (base + constants) when the enum itself declares `ordinal` (same T11 namespace-collision reason) — never suppressed per-constant.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: … no error or check count moved anywhere" — shipped, 0 residue across corpus

### T14. A java STATIC is INHERITED by every subclass; a Scala companion inherits nothing — emit the DECLARING type — **CLOSED, 20 errors**

Java lets a static be named through any subclass (`ZoneOffset.systemDefault()`, declared on `ZoneId`); scala companions inherit nothing from each other, so a verbatim-translated receiver name fails. Fixed by emitting the call through the resolved member's DECLARING type instead of the source name, for both static methods (`SpoonTir.staticCallQualifier`, off the interned symbol's real owner) and static/interface-constant fields (`staticFieldAccess` widened from a superclass-only walk to a full breadth-first inheritance closure reaching interfaces too).
liqp **76 → 56** (all 20 in the test source set, `nodes/{Gt,GtEq,Lt,LtEq}NodeTest`); libgdx core **0 → 0 errors but 10 members changed** (GL30/GL20 interface constants — compiled before only because `TirEmitter.classDef` re-exports a parent's companion); every other port byte-for-byte unchanged across fifteen lanes.
Same rule as CLAUDE.md §1(a) ("java interface constants are static and inherited; scala companions do not inherit"), now applied at methods too — read off the symbol's owner, never the written receiver name (§4.56).

### T15. A RECEIVER IS AN OPERAND — `.m` binds tighter than every control-flow expression — **CLOSED, 2 errors and an unknown number of silent ones**
`TirEmitter.operand` parenthesised operators and `Typed`/`Spread` but not four receiver positions — `Select`'s qualifier, `InstanceOf`, `ArrayLength`, `ArrayAccess` — so a ternary/if-else receiver's `.m()` landed inside one branch only; same-type branches compile and call the wrong side silently.
liqp: 2 errors (`InsertionTest`). anim8's silent face shipped green for the life of the port: 0 errors, 23/23 tests passing, while `writeBigPalette` wrote nothing when called with `null`.
Fix: one call to `operand` in each of the four positions — `CLAUDE.md` §4.4's shape reached at the emitter rather than at a statement form.

### T16. A TYPE's annotation is harvested where NO EXPRESSION TRANSLATOR exists, so every argument-bearing one is DROPPED — **CLOSED; liqp 633/4 -> 636/1, and the check that reported it had read 1 since the port began**
`defineType` minted a type's symbol before any `BodyTranslator` existed, so every argument-bearing annotation on a TYPE (markers were fine) was silently dropped via `OmissionCheck`; jackson's `@JsonSerialize` missing meant bean-serialisation instead of `toLiquid()`.
Fixed by resolving before defining and building a `BodyTranslator` against the same id (3 lines), plus `FrontendConfig.preservedAnnotations` (b)-policy and escaping the `using` keyword arg. `omissions` 1->0; suite 633/4->636/1 (net one new pass after a K21-shaped regression was fixed too); `collection-boundary` 25->24; 3 members moved; 0 digests on all other ports.
Rule: an (a) fix that removes an omission can expose a (b) policy gap, because the omission was suppressing the code path the policy is about — re-census after the fix, don't subtract.

### T16.5 "ABSORBED SILENTLY" is a SUSPICION, and a probe either retires it or sharpens it — three kinds, three different answers
(a) engine / frontend-spoon — procedure, not a fix: `SpoonKinds.Absence.AbsorbedSilently` proves DISPATCH, never OUTPUT.
Probed three kinds, three different answers: `CtTextBlock` — **not a difference** (`getValue` already returns JLS 3.10.6's denoted string; emitter re-escapes; moved to `Lowered`, catalog `JS-E18` `NonDiff`). `CtRecord` — **wrong in both halves**: components/accessors DO arrive (fields+methods), but the emitted class extends `java.lang.Record` without the abstract `equals`/`hashCode`/`toString` it declares — not concrete, invisible until the port reaches 0 typer errors (§3). `CtAnnotationMethod` — **understated**: the whole element is dropped, not just its `default` clause; newly reachable once T16 makes a type's arguments carryable.
`CtAnnotationFieldAccess` is `NeverVisited` — unreachable because the annotation is dropped by policy first, so it is recorded as unprobed rather than guessed.
Not retry: trusting a classification's own wording as a description of the OUTPUT — write a fixture instead.
Next: both real defects (record, annotation type) are named but unbuilt.
Triage (2026-09-04): FAMILY T: record/annotation-type synthesis gaps — "Next: both real defects (record, annotation type) are named but unbuilt"

### T17. Java resolves an overload in THREE PHASES and Scala in ONE — **the divergence cannot be predicted without a resolver; the RISK is counted instead**
Symptom: a call javac binds to `f(int)` can bind to `f(Object)` in the port — both compile, no error, no moved digest, `CLAUDE.md` §4.4's defect class with no statement form to key on.
Triage (2026-09-04): REFUSED-BY-DESIGN — family T: predicting java's 3-phase vs scala's 1-phase overload resolution divergence needs a resolver the size of javac's own — refused as compiler-sized; the risk is counted (`overload-risk`), never predicted

### T18. An `instanceof` PATTERN BINDING is FLOW-SCOPED, and no lexical placement of a `val` is faithful — **REFUSED, and the refusal moved from unit-fatal to per-site**
Three placements tried, each fails a real shape: a `val` at the test site breaks `&&` short-circuiting; a hoisted `var` breaks per-iteration lambda capture (§4.4 class, no compile error); rewriting to `match` is exact but only for the subset where the binding isn't read after the `if`, and needs `Tree.Match` to carry a second provenance so its `break` boundary doesn't steal an enclosing loop's.
Triage (2026-09-04): REFUSED-BY-DESIGN — family T: instanceof pattern-binding's JLS flow scope has no lexical Scala placement — all three tried placements each break a real shape; a card would need `Tree.Match` to carry a second boundary provenance, unbuilt

### T19. A RECORD PATTERN is blocked by the RECORD and not by the pattern — **CLOSED once `JS-C43` derived an extractor over the ACCESSORS**; and the UNNAMED pattern is not reachable at all
Blocked because the emitted record had no extractor at all; unblocked by an `unapply` derived over the ACCESSORS (JLS 14.30.1), never a case-class-style one over constructor params — those two disagree silently on an explicit accessor. Also added JLS 14.30.2's UNCONDITIONAL component pattern as a separate node (`Tree.BindPattern` vs `Tree.TypePattern`, decided via the parser's subtype relation) so a widening/no-test component still matches `null`.
Cost: 2 IR nodes, 1 frontend arm, 2 emitter arms, 0 corpus sites moved; `PatternSwitchSpec` +6 tests. `CtUnnamedPattern`'s `RefusedLoudly` claim was also corrected to `NeverVisited` — no source Spoon accepts produces one.
Rule: a gate names the CAPABILITY it needs, never the implementation somebody guessed would supply it.

### T20. A ported record is not a JVM RECORD, and its extractor is a FUNCTION — **three residues `JS-C43` cannot close**
Symptom: `x.isInstanceOf[java.lang.Record]` true (scalac accepts the `extends` javac refuses), but `x.getClass.isRecord()` false and `getRecordComponents()` null — a reflective serialiser/mapper sees a non-record. Also: java's record pattern runs accessors lazily, stopping at the first failing component; the emitted `unapply` builds the whole tuple first, so ALL accessors run; an accessor's thrown exception arrives raw instead of wrapped in `MatchException`.
Numbers: 29 side-by-side observations byte-identical against javac for value/behaviour of the declaration itself. All three recorded on the `RecordMembers` decision as porter notes (`reflective=`, `patternAccessors=`, `patternThrow=`).
Triage (2026-09-04): REFUSED-BY-DESIGN — family T: no scala construct reproduces a JVM `Record` (class-file attribute, lazy per-component matching, unwrapped `MatchException`) — a card would need a consumer-side reflection shim, not attempted

### T21. A ported java enum IS a `java.lang.Enum`, and only the `enum` SYNTAX can say so — **ssg-md 171 → 137, and the shape had been un-askable for five libraries**
(a) engine — universal (JLS 8.9). Symptom: `class Flags cannot extend java.lang.Enum: only enums defined with the enum syntax can` — the prior `sealed abstract class` + `case object` lowering conforms to no `<E extends Enum<E>>` bound anywhere (36 errors on the first library that wrote one).
Numbers: ssg-md 171 -> 137. libGDX: 0->0 errors, 44 checks flat, 72 member digests + 82 port-map rows moved, suite 217/4 identical to before. Port map now publishes `form=enum` vs `form=enum-class` for `base-surface`.
Triage (2026-09-04): REFUSED-BY-DESIGN — family T: three enum shapes stay permanently un-enum-syntax'd (constant with a class body, a name clash with java's second namespace, zero-constant enums) — a card would need per-shape lowering the enum-syntax path can't express

### T22. An `@interface`'s own ELEMENTS are dropped, and only a library that READS one back can see it — **sge-ai 20 → 16, `trivia(recovered)` 5 → 1. CLOSED**
`@interface` elements (`String name() default ""`) were emitted as nothing at all — a bare `extends scala.annotation.StaticAnnotation` — invisible until a library reads them back reflectively (gdx-ai, 4x `E008 Not Found`); markers-only annotations in the rest of the corpus never exposed it.
sge-ai errors 20->16, `trivia(recovered)` 5->1, 7 member digests, every other port byte-identical.
Rule: the parameter keeps java's name (`val`, default read via `coercedExprOf`), the read loses its parens — guarded on program ownership + `isAnnotation`, never on a name (§4.56). Open residue: JVM RETENTION/reflective visibility of the annotation itself is unanswered (scala 3 has no `ClassfileAnnotation`).

### T23. A policy key CAN now name a member inside an enum constant's body — CLOSED
An enum constant's class body (JLS 8.9.1) needed two fixes: `MemberKey.parse` splits at the LAST `#` so `Owner$Enum#Const#member` keys parse, and `MethodBodyTransform.rewrite` now maps `cd.enumCases` as well as `cd.body`.
Met on gdx-ai: a `TypeRedirectTransform` on the TYPE was the better cut (fixes all three signatures at once, leaves the constant body mechanically translated) — errors 10->0, port-map 12->0, `substitution(dangling)` 0.
Rule: the redirect is the better cut wherever the wall is a TYPE; T23's fix is for the case where the wall is a BODY only an enum constant declares.

### T24. A java method reference at a SAM the port emits as a plain trait is ETA-EXPANDED, and the reference build refuses it — CLOSED (catalog `JS-C52`)
(a) engine. Under `-Werror` a java method reference to a SAM emitted as a plain trait warned *eta-expanded even though T does not have @FunctionalInterface* — the frontend had dropped that annotation.
Fix: preserve `@FunctionalInterface` (read from class files for external types via `Minter.external`); emit the explicit lambda only where the target SAM carries no annotation (unreadable class file counts as unannotated).
Numbers: first cut (lambda at every static reference) moved 139 gdx port-map rows; narrowed version moves 0 digests. gdx `.ref` 1362->1331, ashley byte-identical, liqp `.ref` 106->100, textra `.ref` 402->401.

### T25. An unused catch variable triggers `-Wunused:patvars` under `-Werror` — CLOSED
(a) engine — universal. The emitter faithfully kept java's unused catch-variable name (`catch (Exception ignored)`), triggering `E198` under `-Wunused:patvars`.
Fix: `TirEmitter.tryStr` scans each catch body for references to the param symbol and emits `_` when unused. 45 sites on gdx, all catch clauses.

### T26. Unused locals and private members trigger `-Wunused:locals,privates` under `-Werror` — **.ref 97 -> 54 (49 of 70 E198 closed). T26.1 CLOSED, T26.2 residue**
(a) engine. `UnusedSymbolTransform` (late phase) walks `Ident`/`Select` vs `Assign.lhs` counts to classify each symbol read / write-only / unreferenced; deletes pure-unused bindings, emits `@nowarn` for effectful/write-only ones, refuses to touch API-surface signatures.
gdx: 70 E198 sites across 11 shapes (serialVersionUID, unused locals/privates, for-loop leftovers, write-only vars, anon-class API surface); 49 of 70 closed, `.ref` 97->54. T26.3 (separate bug, CLOSED wave 3.1ao): `refCollector` never counted `Tree.MethodRef`, so privates referenced only via a method reference (`this::visit`) were deleted — ssg-md regressed 0->45 errors across 4 files; fixed by one line adding the `MethodRef` case.
Residue (T26.2, open): an unreferenced private whose only reference is inside a `MethodBodyTransform` substitution's `Tree.Opaque` text is invisible to the walk, so `@nowarn` there triggers "does not suppress" — left un-annotated as `.ref` residue.

## 4. Collections, shims and the JDK boundary

### K1. Never model a Java interface on a Scala COLLECTION trait — the governing rule is `CLAUDE.md` §4.5
(a) engine — see CLAUDE.md §4.5.
Symptom: 14 libGDX classes implementing both java `Iterable`/`Iterator` are ILLEGAL under scala collection traits: 24 "cannot override final member", 19 `size` vs `IterableOnceOps`, 15 `isEmpty`, 15 "inherits conflicting members".
Cause: scala's collection traits are large and interlocking; java's small orthogonal interfaces routinely combine on one class, which scala's traits cannot host.
Numbers: standalone traits with java's own arity: 145 -> 47 -> 69, cluster closed. `foreach` on BOTH iterable+iterator shims made every `for` ambiguous: 23 errors — belongs on the iterable only. Parenless-accessor rewrite must decline on a shim: 24 errors.
Not retry: modelling on `scala.collection.*` at all.
Triage (2026-09-04): SUPERSEDED — CLAUDE.md §4.5 — "the governing rule is CLAUDE.md §4.5"

### K2. The JDK/Scala collection BOUNDARY is universal, and neither obvious fix works — CLOSED
Symptom: a JDK-returned `java.util.Map[String, java.util.List[String]]` flowing into a retyped declaration, and `appendAll(JavaIterable[?])` handed a `mutable.ArrayBuffer` — two collection worlds that cannot meet, which java itself never has.
Triage (2026-09-04): CLOSED-IN-FACT — "Built instead: CollectionsTransform.coerce wraps at the SLOT … Took simple-graphs to 0"

### K2.5 A pass gated on ONE of its targets is SWITCHED OFF for every program that lacks that target — 3 errors, and a whole pass silently inert — CLOSED
Symptom: `E134 None of the overloaded alternatives of method of in object Filters` at boundary calls; `collection-boundary` DID report every one as `ShimBoundary` the whole time, correctly, but that finding cannot distinguish "no factory exists" from "factory exists but the pass never ran."
Numbers: liqp 90 -> 87 errors, `collection-boundary` 13 -> 8, 8 digests/4 members (5 bridges emitted where none was, 3 newly compile). The other 2 are K6.5's deliberately-refused `Arrays.asList` sources — fixed by reading the refusal from `handledStatic`'s table (bottom-up traversal means the inner call is already final by wrap time), giving a new row `CollectionBoundaryCheck.Issue.RefusedSource`: liqp 56 -> 56 (couldn't compile either way), `collection-boundary` 16 -> 18, 8 digests/4 members.
Rule: a residue count is only as good as the assumption everything able to close it RAN — check from inside the phase whether a factory exists for the reported (source kind, target shim) pair before trusting the count.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: liqp 90 -> 87 errors, collection-boundary 13 -> 8 … couldn't compile either way" — both faces fixed, new Issue row types shipped

### K2.6 A SHIM's arity is INHERITED, so the guard that protects it must be asked of the ANCESTRY — **16 errors, 201 → 182. CLOSED**
The parenless-call refusal (`size`/`iterator`/`hasNext`/`next` on a shim receiver) tested the receiver's HEAD SYMBOL against the three shim symbols directly — exact for a receiver the phase itself retyped, `false` for `trait Cursor extends java.util.Iterator` (no shim symbol, but `hasNext` still resolves through it), producing `method hasNext in trait JavaIterator must be called with () argument`.
Fix asks two ancestries, not one: a class's PARENTS and a TYPE PARAMETER's upper BOUND (2 of 16 sites needed the latter alone); a parent may have either of two spellings mid-pass (already-retyped shim name, or java original whose `typeMap` target is the shim) — decided from what the phase itself did to the type (§4.56), never from a name. Conservative default is `false` (no suppression) on unresolved chains.
Numbers: 16 errors closed, 201 -> 182; blast 6 declarations, every check count flat.

### K2.7 A node whose TYPE overstates its EMISSION is patched ONCE PER POSITION until somebody fixes the emission — **ssg-md 81 → 69, `collection-boundary` 27 → 26. CLOSED**
`keySet()`/`entrySet()` retyped the node's declared type but the emitter printed non-`Set` views; patches at `transformValDef`/`coerce` covered declarations only, missing method RESULTS and conditional branches — 12 errors on one port.
Fixed with `JavaCollections.keySetView`/`entrySetView`, live write-through runtime views matching java's own refusal semantics; ssg-md 81 → 69, `collection-boundary` 27 → 26, `Issue.ShimBoundary` now empty on all fifteen ports.
Rule: patch the REWRITE with a runtime type matching the record, not each read position — a node's type must equal what it emits (K6's rule).

### K3. Injected sources are for SEMANTICS the target lacks — never for adapting SHAPES — CLOSED
Rule: inject only semantics the target genuinely LACKS (a version-locked runtime dependency); shapes the engine could emit correctly must be emitted, nothing shipped as glue.
Triage (2026-09-04): CLOSED-IN-FACT — "Both offenders (PortedSuite K4, Asserts façade X2) failed this test and were deleted"

### K4. RETRACTED — the TIR expresses a CURRIED APPLICATION perfectly well — CLOSED
Retracted claim: `Tree.Apply.fun` is itself a `Term`, so `test(name)(body)` currying is a nested `Apply` — the TIR expresses it fine; no gap existed.
Rule: before concluding an IR cannot express a target idiom, build the tree and emit it.
Triage (2026-09-04): CLOSED-IN-FACT — "RETRACTED … No work outstanding"

### K5. A java class that EXTENDS a JDK collection — CLOSED, and what the shim must get exactly right
JDK abstract collection bases (`AbstractCollection` etc.) weren't retyped like their interfaces, leaving classes half-translated; the shim needed the base's own exact abstract/concrete split, java's parameter types, java's type-parameter bounds — plus receiver-less calls inside double-brace initialisers and unclaimed `put` rewrites at those same call sites.
simple-graphs' 27 of 30 errors fixed at 0; liqp 56 → 52 (main 27 flat, test 29 → 25), 9 members; `java.util.Collection`/`AbstractCollection` both map to the `JavaCollection` shim family, bridged at slots by `coerce`.
Rule: a shim for a JDK abstract base mirrors that base's own abstract/concrete split member-for-member, never a list of members the corpus happens to call; a rewrite keyed on `Tree.Apply` must also lower implicit-receiver and `MethodRef` call shapes for the same member family.

### K5.5 Several constructors reaching the SAME parent constructor — a SYNTHESISED primary — CLOSED
Symptom: multiple java constructors calling `super(...)` with different arguments were collapsed onto one nominated call; simple-graphs' shortest path returned size 0 instead of 39 — compiled, silent.
Numbers: libGDX 0 → 5 (no-arg-root case mishandled), omissions 46 → 50 (picked first root, not widest) → final 46 → 43 once `superExpressed` stopped double-counting.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: libGDX 0 → 5 … final 46 → 43 once superExpressed stopped double-counting"

### K5.6 A cast that only BECOMES impossible after a retyping
(a) engine — `CollectionsTransform`.
Symptom: `(Collection<V>) anArrayList` compiles then throws `ClassCastException` at runtime; no check count moves (not an omission, portability site, or signature mismatch).
Cause: retyping maps `Collection` to the runtime shim while leaving the surviving `java.util.List`-derived value alone (e.g. from an `IntStream` chain K6 correctly declines to collapse); the resulting `asInstanceOf[JavaCollection[V]]` can never succeed.
Rule: a phase that retypes must ask what it did to CASTS around the moved types — dropping the cast turns a runtime failure into a compile error on the same line and lets `coerce` bridge the argument (CLAUDE.md §4.4/M6).
Status: the "a new retyping phase can reintroduce this" standing question is CLOSED by K5.10 (`Rewrite.accountedBy`); the cast itself is NOT fixed, so this entry stays open.
Fix kind: (a).
Triage (2026-09-04): FAMILY K: cast-around-a-retype dropping — "the cast itself is NOT fixed, so this entry stays open"

### K5.7 A class that IMPLEMENTS `Map.Entry` — the target is FINAL, so the PARENT stays java's — CLOSED
Symptom: `extends scala.Tuple2[K, V]` fails three ways at once (`final`, wrong ctor arity, no `setValue`) — the parent is kept as JAVA's own `java.util.Map.Entry` instead.
Numbers: liqp 49 → 47, `collection-boundary` 12 → 13 (parent restore); liqp main 1 → 0, `collection-boundary` 14 → 15, 3 digests (setValue refusal); correction 0 → 0 errors, suite flat 357/218; ssg-md 13 → 11, 4 digests, checks flat (slot projection, wave 15).
Triage (2026-09-04): CLOSED-IN-FACT — "correction 0 → 0 errors, suite flat 357/218; ssg-md 13 → 11, 4 digests, checks flat"

### K5.8 A `super` receiver is a SYNTAX question, and it is answered of the RESULT — not of the arm
Symptom: inherited-call rewrites on a class extending a retyped collection placed `super` illegally — `entrySet()` as bare receiver, `Seq.get` as `super(i)`, operators infix `super ++= m` — E040 syntax errors, worse than the type errors replaced.
Numbers: liqp 14 → 13 (`superPlaced`); liqp 10 → 9, 3 digests (`superIsThis` fallback, `Sort$SortableMap#toString`); `entrySet()`/`Seq.get` still untranslated under java's names (M6).
Fix: `TirEmitter.applyStr0` renders operators on a `super` receiver as a selection (`super.++=(m)`); the phase checks the structural property `superPlaced` (every `Tree.Super` stands as a `Select` qualifier) on the BUILT result, covering future rewrites by construction. Fallback `superIsThis` rewrites to `this.m` when no override exists anywhere in the PROGRAM between `super` and `this` (checked transitively).
Triage (2026-09-04): REFUSED-BY-DESIGN — family K: `entrySet()`/`Seq.get` stay untranslated under java's own names at a `super` receiver — a card would need the rewrite M6's refuse-and-count policy declined to build

### K5.9 A METHOD REFERENCE is a second NODE SHAPE of a rewrite keyed on a CALL — and it has to be LOWERED — CLOSED
Symptom: `Map.Entry::getKey` (a `Tree.MethodRef`) hits the emitter's post-phase lambda expansion selecting a member the retyped receiver lacks — `value getKey is not a member of (String, Insertion)`.
Numbers: liqp 8 → 7, one site (`Insertions#getNames`), 3 member digests, every check count flat.
Fix: the phase LOWERS an unbound instance reference into the lambda the emitter would build, runs the SAME rewrite on the synthesised `Apply`; the lambda parameter is emitted UNANNOTATED (`ValDef` with `NoType`) so scalac infers it from the expected function type.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: liqp 8 → 7, one site …, 3 member digests, every check count flat"

### K5.10 The standing question every RETYPING phase owes — asked of the PIPELINE, and NOT as a usage count. **K5.6's open sentence CLOSED; the generic `usagesOf \ callSites` form REFUSED at 3,045 against 152**
Built: `Rewrite.accountedBy` declares check lanes per phase; `Pipeline.runTraced` DERIVES what moved by comparing owned symbols' `info` AND their tree-level `tpt`s across the phase (tree-only retyping was previously invisible — 0 blast on all ports once fixed, no engine phase currently writes that shape).
First run found two unanswered phases (`primitive->opaque`, `type-redirect`) on the largest port; both closed via `PolicyReport`/`opaque-boundary`/`base-surface` lanes, `rewrite-callsites` 2 → 0 on libGDX core; a symbol swap stays an unobserved residue (indistinguishable from a legitimate `Substitutions` drop).
The generic `usagesOf(s) \ callSites` form is REFUSED as a substitute: 1,077 declaration-moves / 3,045 recorded usages against 152 real seams the four boundary checks count — reasoning without the phase's own `typeMap` is forbidden (§4.56); pre-run site pricing is also refused, since every phase resolves its own symbols inside `run` (0 declarations move if `transformType` is applied beforehand).
Triage (2026-09-04): FAMILY K: a symbol swap stays an unobserved residue, indistinguishable from a legitimate `Substitutions` drop

### K6. `java.util.stream` — the CHAIN collapses; and the two rules that make that safe — CLOSED
Symptom: `xs.stream().filter(p).collect(Collectors.toList())` needs a WHOLE-CHAIN rewrite (`stream()`→receiver-as-collection, `filter` survives, `collect(toList())`→nothing); per-call mapping alone does not type-check.
Triage (2026-09-04): CLOSED-IN-FACT — "Closed via JavaCollections.toStream at the CONSUMER slot …: liqp 6 → 5, collection-boundary 14 → 13, 3 digests"

### K6.5 A java `T...` becomes an `Array[T]`, so a REWRITE onto a scala vararg must undo the pack — CLOSED
(a) engine/frontend-spoon, universal. Symptom: `E007 Found: Array[T] / Required: T` rewriting a packed java vararg call onto a scala `A*` runtime helper (`Arrays.asList` etc.); silently wrong where the vararg element is `Object` (whole array becomes one `%s`).
Numbers: liqp pack-opening 38 -> 26 (12 sites, 22 digests; an earlier accidental 76 -> 67 via K15's wrap was the wrong VALUE); explicit-inference fix 19 -> 14; external-callee spread fix 67 -> 58; primitive-vs-reference component check 0 digests moved on all 11 lanes; `Tree.Repeated`/`Tree.NewArray` composition-collision fix: liqp test 59 -> 61 (3 sites).
Rule: a phase that pattern-matches an argument list owes every shape the frontend's vararg convention can produce; a coercion cast is stripped exactly when what it wraps already has the type the rewritten member wants.
Triage (2026-09-04): CLOSED-IN-FACT — "external-callee spread fix 67 -> 58; primitive-vs-reference component check 0 digests moved on all 11 lanes"

### K7. A java enhanced-for BINDING may be declared at a supertype, and the port dropped it — CLOSED
(a) engine/frontend-spoon, universal. Symptom: `for (Object e : collection)` over a wildcarded `Collection<?>` binds `e` at `Object` in java; scala's `for (e <- xs)` binds at the iterable's element type, an unusable capture for a wildcard — `Found: ?1.CAP`.
Numbers: libGDX main 27 members changed, 217/221 tests pass; liqp 57 -> 56 (var-binding case); 0 members moved elsewhere.
Fix: re-bind with `for (e$e <- xs) { val/var e: T = e$e.asInstanceOf[T]; … }`; derive the fresh name from the RAW name and escape only the whole suffixed name.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: libGDX main 27 members changed, 217/221 tests pass; liqp 57 -> 56 …, 0 members moved elsewhere"

### K8. `Type::method` is TWO java forms sharing one syntax — CLOSED
(a) engine/frontend-spoon, universal. Symptom: `Edge<V>::getWeight` on an INSTANCE method emitted as a qualified name reads `value Edge is not a member of sge.graphs` — the error points at the package.
Fix: distinguish via `Flags.isStatic`; take the lambda's arity from the method's own `MethodType` so a multi-parameter bound reference (`String::compareTo` as `Comparator`) also works.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: distinguish via Flags.isStatic …" — shipped rule

### K9. CLOSED (wave 2.4, 2026-08-27) — a java enhanced-for over a JDK `Iterable` the port KEPT emits java's own desugaring; noise4j 2 -> 0 on the construct, and the typer gate then lifted to 7 RefChecks rows

Tried: emitting `for (x <- xs)` (needs Scala `foreach`) over a JDK iterable a port KEPT (no `CollectionsTransform`) failed with "value foreach is not a member"; fixed by emitting java's own iterator-protocol desugaring whenever the post-pipeline receiver's head symbol is still external/unretyped `java.*`/`javax.*`.
Numbers: noise4j 2 -> 0 on the construct; RefChecks gate then rose to 7 (E164 `overrides nothing` in enum constant bodies, an unrelated riser per CLAUDE.md §3).
Rule: decide from what a PHASE did to the type (post-pipeline head symbol), never the type's NAME; `JdkSurfaceCheck` reports a `kept-iterable` finding per receiver left in `java.*`, before any compile.
Numbers (2026-09-05, ladder L0): the same protocol for a PROGRAM type that reaches `java.lang.Iterable` through owned parents and declares no `foreach` — libGDX core at L0 225 -> 0 on the construct (310 -> 26 total); the full port byte-flat.

### K10. A TYPE-VARIABLE map key arrives carrying java's `Object` WIDENING — CLOSED
(a) engine — `CollectionsTransform`. Symptom: `Found: Object / Required: K` calling a retyped `Map`'s `get`/`remove`/`containsKey` with a key whose static type is a type variable.
Numbers: gdx-vfx `ValueArrayMap` 3 sites; 0 members moved on any other corpus port — resolved.
Rule: a phase that retypes owns the coercions around what it moved (K5.6's rule, met at this slot).
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: gdx-vfx ValueArrayMap 3 sites; 0 members moved on any other corpus port — resolved"

### K10.5 Two java types mapped to ONE scala target share every call rewrite — and `java.util.Stack` is where that costs a semantic — CLOSED
(a) engine — `CollectionsTransform`. Symptom: `stack.peek()` on a `java.util.Stack` mapped onto the same target as `ArrayList` silently reached the `Deque` rewrite arm (`headOption.orNull`) — wrong end, wrong empty behaviour, no compile error.
Numbers: liqp's one site (`blocks/For.java`): 4 members changed, 633/4 flat, `collection-closure` 8 -> 0 (was flagging `Stack` as unmapped on 5 libraries).
Rule: a java type needing its own rewrites needs its own TARGET; an availability match is not a semantic match.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: liqp's one site …: 4 members changed, 633/4 flat, collection-closure 8 -> 0"

### K11. A CAPACITY hint at a HASHED collection has no one-argument scala constructor — CLOSED
(a) engine — `CollectionsTransform`'s `copyConstructor`/`capacityConstructor`. Symptom: E134 (no matching constructor) translating `new HashMap<>(10)` — `HashMap` declares only `()` and `(initialCapacity, loadFactor)`.
Numbers: gdx-vfx `ValueArrayMap` fixed; 0 members moved elsewhere (a bare one-arg hashed constructor was a compile error everywhere before this).
Rule: the SET of hashed targets is read off the phase's own `typeMap`, never a name test.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: gdx-vfx ValueArrayMap fixed; 0 members moved elsewhere"

### K12. A component under an UNPARSED PARENT is frozen — was **12 of 144**, now **0**; and the surface that fixed it may not be demand-derived — CLOSED
(a) engine — `OverrideGraph.closureOf` / `ExternalSurface`. Symptom: a bean-property/override closure REFUSES (counted) whenever a parent has no `ClassDef` — an unparsed JDK interface — because the closure cannot see every declaration of the override component.
Numbers: 12 of 12 anchors freed on libGDX core; 0 members changed on all 13 ports.
Rule: a surface may be trusted only where it is COMPLETE (closed by spec); `java.lang.Object`'s member set must be included too, or renaming `toString`/`equals`/`hashCode`/`clone` reads as unanchored.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 12 of 12 anchors freed on libGDX core; 0 members changed on all 13 ports"

## 5. Portability and platform

### K12.5 The SURVEY and the RULE LIST are two artifacts, and a row can be STATED for two waves without ever being ASKED — CLOSED
(a) engine — `PortabilityCheck`. Symptom: `java.util.WeakHashMap`'s survey row (`Absent, Refuse(...)`) was quoted in design docs as an enforced refusal but matched no port, because the rule list had no rule for it.
Rule: every survey row verdicted `Refuse` must have a corresponding rule; `PortabilityTargetsSpec` pins the JS-only set so a missing rule is now visible as an absent name.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: … PortabilityTargetsSpec pins the JS-only set so a missing rule is now visible as an absent name"

### K13. `T | Null` is NOT transparent at an ABSTRACT type parameter — CLOSED by `Target.Named`, with a COUNTED residue of five scope-outs

Tried: Union target (`T | Null`) fails at every use of an annotated abstract `T` in a plain `T` slot (632 declarations, 35 errors); switched nullability target to `Target.Named("lowlevel.Nullable")`, closing every position-blind retyping seam that opened (Apply-node re-typing, member access, `== null` rewrite, external-callee coercion, injected-shim formals, overload-erasure clashes): `661 -> 268 -> 236 -> 73 -> 17 -> 8 -> 0`.
Numbers: `AbstractTypeParameter` 155 -> 0, base 0 errors; residue = 5 scoped-out FQNs (`CharArray`/`Image` erasure clash, `Json`/`Pools` injected-shim mismatch, `TemporalAction` funnel gap); wrapper-empty-vs-JVM-null bug (wave 1.1e) recovered 4 regressed suites; `@nowarn(deprecated)` placement 712 -> 49 -> 0.
Rule: switching the nullability TARGET (union floor to a concrete wrapper) closes seams a union floor cannot at an abstract type parameter; every coercion a retyping phase owns must move with the signature change; a scope exit on a generic type must name the type AND every subtype re-stating the annotation.

### K13.5 A wrapper target's LAST THREE SEAMS ARE ALL ONE SENTENCE — the retype changes a SIGNATURE, so everything java tied to that signature has to move with it — CLOSED
(a) engine — `NullabilityTransform`. Symptom: three seams surfaced only on DEPENDENTS once the base closed at 0: (1) `E038`/`E007` — an override not restating java's marker keeps the old signature; (2) `E120` — an overload set java kept apart by descriptor collapses under an opaque wrapper's erasure; (3) `E006 Not found: type T` — a coercion ascription renders the callee's own type variable verbatim at a call site that cannot name it.
Numbers: gdx-ai 0->1->0, TextraTypist 0->2->0, VisUI 7->8->7; base 0 errors, `nullability-boundary` 184 -> 169, 307 member digests moved.
Rule: a wrapper-target retype must move override contracts, overload-erasure clashes and coercion ascriptions together with the signature — invisible on a base, surfaces on the first dependent that resolves against it.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: gdx-ai 0->1->0, TextraTypist 0->2->0, VisUI 7->8->7; base 0 errors, nullability-boundary 184 -> 169"

### K13.6 `nullableMembers` — the unannotated case (no annotation in the java, hand port wraps in Nullable)

(b) configure `NullabilityTransform` — per-library policy. Symptom: 6 ashley members return null with NO java nullability annotation, but sge's hand port wraps them in `Nullable[T]` per migration notes.
Fix: `nullableMembers: Set[String]` — exact member FQNs matched against `Symbol.fullName`, treated identically to a configured annotation; empty = no-op, fingerprint segment omitted; `MergeablePolicy` union; `.conf` key `nullableMembers`.
Numbers: ashley `api-parity(null-model)` 6 -> 0, `nullability-boundary` 0->1 main / 0->18 test, compile 0=0 all platforms, suite 108/2/2 baseline-identical; gdx `members.tsv` byte-identical.
Known residue: `SystemManager#getSystem` deliberately EXCLUDED — `.orNull` on an opaque `Nullable`'s absent value returns the sentinel `NestedNone`, not JVM null, so an internal `!= null` check is always true and a downstream cast throws `ClassCastException` (33 newly-failing ashley tests when included).
Do NOT retry: adding `SystemManager#getSystem` without first fixing `slotUnwrap`'s opaque-sentinel vs JVM-null confusion (touches every wrapper coercion in the pipeline).
Triage (2026-09-04): FAMILY O: opaque-sentinel vs JVM-null unification in slotUnwrap — "Do NOT retry: adding SystemManager#getSystem without first fixing slotUnwrap's opaque-sentinel vs JVM-null confusion (touches every wrapper coercion in the pipeline)"

### K13.8 A `RuleScope` cut THROUGH an override component SPLITS it — OPEN, engine (a); scope the ENTRY over the closure meanwhile

`NullabilityTransform(scope = Only(Array, ArrayMap, ObjectMap, ObjectSet))` on the lls port (the four
of its twelve types that carry `@Null`) retyped `ObjectMap#remove(K)` to `Nullable[V]` and left
`OrderedMap#remove(K)` — an OVERRIDE of it, outside the scope — at `V`: **1 error** (`E164`,
2026-09-06), emitted silently. §4.55's contract is whole-component-or-refuse; a scope boundary is a
third cut the phase does not ask about. The same run reports each scope entry with no annotated
declaration of its own (`OrderedMap`, `OrderedSet`) as a `policy` "never matched" row even though the
entry is what lets the override retype through — the precision defect beside the split. Both are
§1(a). Until fixed: scope on the ENTRY over the override CLOSURE (`LlsPolicy.Annotated`, six types)
and carry the two `policy` rows. Do NOT widen back to `Everywhere`: a base's scope is inherited, and
`Everywhere` decides core's nullability at L0 (PROGRESS.md §13.29).

### K14. A RETARGET's subtyping licence is ONE-DIRECTIONAL — the producer side is COUNTED, never coerced

(a) engine (counter) built; (b) per-library policy for a coercion when a real producer appears. Symptom: `CollectionsTransform(retarget)` licenses only "the retyped value reaches a slot still declaring the java type"; it says nothing about a java-typed value the JDK HANDS BACK arriving at a retyped slot — silent `ClassCastException` at run time, green compile.
Cause: a retarget contributes to neither `mappedTypes` nor `retypedTargets` so `CollectionBoundaryCheck` cannot see it; `transformType` moves both sides of the slot's node type; no `transformIdent` means a static receiver keeps its java spelling under a moved node type.
Fix built: `RetargetBoundaryCheck` (`collection-retarget`) reports all three shapes, proven against a SYNTHETIC producer since the corpus itself measures 0.
Do NOT retry: synthesizing a general wrapper against a synthetic case — a coercion needs per-entry policy (a factory FQN) like `typeMap` carries.
Next step: move a type needing this out of `retarget` into `typeMap` with a kind+factory, where the seam becomes a counted `coerce` boundary (`DESIGN.md` §8.12).
Triage (2026-09-04): FAMILY K: move the coercion out of `retarget` into `typeMap` with a kind+factory — "Next step: move a type needing this out of retarget into typeMap with a kind+factory, where the seam becomes a counted coerce boundary"

### K15. A retyping phase owes a boundary count at EXTERNAL callees — CLOSED where a class file can be READ, OPEN where it cannot (counted, never bridged)

Tried: retyping is position-blind, so a class-file's own (unmovable) signature at an EXTERNAL callee's result, argument, field, generic pass-through, shim formal or `new`-site was invisible to every boundary check (both sides read the same moved node type); frontend now interns external members' `MethodType`/field types (scope-free, ALL-or-none per class file) and every arm bridges toward what the class file actually declares.
Numbers: liqp 15 errors/0 findings before -> 86->76->74->70->67->74->74->6 across the arm chain, `collection-boundary` climbing 8->10->0->12->10->16->12->14; jbump caught+fixed a 0->4 error regression from bridging before rewrite; gdx 0 errors/0 digests throughout.
Rule: where the class file can be READ, bridge (`toJava`/`fromJava`, never toward the node's post-retype type); where it cannot (NoType, partial classpath, minted symbol, refused-static, unreadable pass-through), COUNT and never bridge; a `new` is not a call and needs its own structural test; bridging must run AFTER rewrites, never before.
Triage (2026-09-04): REFUSED-BY-DESIGN — family K: an unreadable class file (NoType, partial classpath, minted symbol, refused-static) has no signature to bridge toward — permanently counted, never bridged, by definition

### K16. A `CollectionsTransform` SCOPE is not a way to opt out of its residue — 27 → 47 narrow, 27 → 51 off. DO NOT RETRY

(b) per-library policy warning + (a) engine gap. Symptom: scoping declarations OUT of `CollectionsTransform` to avoid its boundary residue makes a port WORSE, not better.
Cause: a scope SPLITS a call graph rather than removing a boundary — every error removed at a scoped-out declaration reappears at its (still-retyped) callers; a scoped-out body also loses the phase's REWRITES (not just its retyping), so an enhanced-for over a real `java.util.List` (K9's shape) then dominates; none of the newly-opened seams are counted — `collection-boundary` stays flat while the error count rises.
Numbers: one library, one commit: no scope 27 main/49 test errors; narrow scope (18 named types) 47 main/49 test; whole-package scope-out (phase off) 51 main/42 test — anchored at commit `b95480b5`; the shape (both scoped configs worse) holds even as absolute figures moved since.
Do NOT retry: scoping OUT declarations of a library whose collection types are its currency, expecting the residue to shrink — it does not; scope only a genuine ISLAND (a bridge class nothing else consumes).
Next step (engine gap, unbuilt): a scope's own opened seams need their own count, from the declaration on each side; until then a scope must be measured on the WHOLE port via compile, never reasoned about.
Triage (2026-09-04): FAMILY K: scope's own opened seams need their own count — "Next step (engine gap, unbuilt): a scope's own opened seams need their own count, from the declaration on each side"

### K17. A java CONVERSION emitted as a scala CAST asserts where java CONVERTED — **36 test failures, 0 compile errors. ALL THREE FACES CLOSED**

Tried: java performs a CONVERSION at three positions the emitter rendered as `asInstanceOf` (an assertion): Face1 a lambda at an external functional-interface slot (SAM elaborated to `Function0`, then cast to e.g. `Supplier`); Face2 a conditional over mixed boxed numerics (JLS 15.25 binary promotion vs scala's LUB, plus a Scala-3-only widening-direction gap since scala 3 dropped weak conformance); Face3 a cast expression's own boxed type read from Spoon's pre-cast node type instead of the term's post-cast type.
Fix: Face1 — stop interposing a cast around a poly expression at all (`SpoonTir.polyExpression`/`polyArgsUncast`, one predicate replacing divergent per-site exclusion lists); Face2 — `SpoonTir.promotedBranch` converts each conditional OPERAND to java's own computed branch type, both narrowing AND widening; Face3 — read the TERM's type (`SpoonTir.castType`, post-cast) not the parser's pre-cast node type, in both `coerce` and `uncheckedGeneric`.
Numbers: liqp 357/218 -> 364/211 (Face2), 572/3 -> 574/1 (Face3); libGDX core blast 82+1248 member digests (Face2 widening-gap fix + Face3 redundant-cast removal), 0 elsewhere; unboxing-cast direction probed exhaustively (45 wrapper/primitive cells) confirming java THROWS rather than converts; residual `JS-E06` cell (a value a LATER phase retypes after the frontend decided) counted via `CastConversionCheck`, reads 0 on all 15 ports, kept as a fixture-only lane.

### K18. A retyping moves STATIC types; an `instanceof` and a downcast ask about a RUNTIME OBJECT — **160 test failures, 0 compile errors, every check count flat. CLOSED**

Tried: `CollectionsTransform` retyping is position-blind, so an `instanceof`/downcast (a question about a RUNTIME OBJECT, not a static slot) was retyped too — `value.isInstanceOf[mutable.Map[?,?]]` asks a different question than java's `instanceof java.util.Map`, silently wrong since a port holds BOTH representations (its own values and values from jackson/ANTLR/external callers) at every `Object` slot.
Fix: `JavaCollections.Reified` answers java's question over BOTH representations via a live view; refuse+count where the target is a CONCRETE mapping type no view can be (`Issue.ReifiedOccurrence`); never touch an UPCAST of a value the phase itself retyped or of a type the PROGRAM DECLARES (`vouched`/`Program.owns`); keep java's cast and put the coercion INSIDE it.
Numbers: liqp 392->552 passing, 183->23 failing, 160 flipped/0 newly failing, errors 0 throughout; a naive scala-side-`Iterable` widening was measurably WORSE (550 vs 552 — maps then wrongly satisfy `instanceof Collection`); libGDX blast 0 members with `Program.owns` exclusion vs 9 wrongly-coerced members without it; residue = refused concrete-target sites (liqp: 2), catalog row `JS-G48` stays `Partial` for it.

### K19. A reified COERCION is a new OBJECT where java's cast was the IDENTITY — **the wrap-then-retest chain is CLOSED; reference IDENTITY is OPEN by construction**

Tried: K18's reified coercion builds a NEW wrapper object where java's cast was the IDENTITY; `balticporter.runtime.Wrapping`'s delegating factories close the wrap-then-retest chain (`(Collection)x instanceof List` then recast) by recording what they wrap so every `Reified.is*/as*` looks through transitively — but reference identity/`equals`/`hashCode` on a coerced value are OPEN BY CONSTRUCTION, since no live view can be `eq`/`equals`-identical to what it views, and neither a copy (K15) nor an equals-transparent wrapper is safe.
Numbers: chain fix at 0 corpus regressions (identity arm kept FIRST in every `as*`; an UNMODIFIABLE wrapper deliberately excluded, or it would write through); identity/equality residue costs nothing today (no corpus port compares by reference); the same CONCRETE-target refusal (K18's `ReifiedOccurrence`) also covers `Tuple2`, unbuilt though arguably safe (immutable, no write-through) — 2 sites in ssg-md's `Pair.equals` silently return `false` for equal `Pair`s, declared as expected failures.
Rule/next step: if a library's semantics turn on collection reference identity, scope those declarations OUT of the retyping (`RuleScope`) rather than trying a cleverer wrapper; build the `Tuple2` reified pair only if a second library needs it or a `Pair`-comparing consumer appears in production code.
Triage (2026-09-04): REFUSED-BY-DESIGN — family K: no live view can be `eq`/`equals`-identical to what it views — reference identity/equals/hashCode on a coerced value has no safe encoding; the `Tuple2` reified pair is also named unbuilt

### K20. A REIFIED TYPE ARGUMENT is read out of the CLASS FILE by someone else — **10 test failures on liqp, 0 compile errors, every check count flat. CLOSED**

Tried: a generic type ARGUMENT surviving into a class file's signature (jackson `TypeReference<Map<String,Object>>`, gson `TypeToken`, guice `Key`/`TypeLiteral`, `Class<T>`) is read back by a third party at run time; `CollectionsTransform` retyped the argument, so jackson tried to construct `scala.collection.mutable.Map` and threw — no slot, no node, every check clean.
Fix: `Phase.preservesTypeArgsOf` hook on the TRAVERSAL leaves a declared carrier's type argument in java's namespace; `CollectionsTransform.reifiedCarriers` is the (b) per-library carrier-FQN list, plus `UniversalCarriers = {java.lang.Class}` every port gets; K15's `externalProducer` bridge then applies unchanged.
Numbers: liqp 552->554 passing (throw family 10->0), 4 digests in `LiquidSupport`, `collection-boundary` 15->14; a later `retargetClassOf` widening broke JDK `classOf` literals (liqp 3 errors), fixed by scoping the sync to retarget-table entries only (3->0). Rule: never retype an argument a declared reified carrier reads back.

### K21. A retyped VALUE and an emitted CLASS are read out of the class file at the OTHER end of the same call — **13 test failures on liqp, 0 compile errors, every check count flat, and three of the four assertions pass by accident. BOTH FACES CLOSED (554/21 → 567/8, and face 2's own bridge guard 631/6 → 633/4 once T9 gave it its first retyped field)**

Tried: two faces of the port's own emitted value/class read back by a third party — Face1 a retyped collection crossing OUT to a serialiser (jackson bean-serialises `scala.collection.mutable.*` internals); Face2 a java `public` field emitted as a scala `var` is PRIVATE on the JVM, so reflective bean readers find zero properties (liqp's null-coalescing comparisons then pass 3 of 4 assertions "by accident" from missing data).
Fix: Face1 — `JavaCollections.Reified.toJavaValue` bridges DEEP, by VIEW, at every declared reflective sink formal (`CollectionsTransform.reflectiveSinks`, (b), empty default), counted per-CALLEE (`Issue.OpaqueEgress`). Face2 — `PublicFieldAccessorTransform(scope)` emits an explicit bean pair (never `@BeanProperty`, which round-trips the raw field back OUT), refuses+counts a name clash or an unreachable `lowerUpper` capitalisation; a MINTED accessor for a phase-retyped field is ALWAYS bridged.
Numbers: liqp 554->559 (Face1), 559->567 (Face2), 21->8 failing overall, `errors 0` throughout. Rule: `getClass.getFields` provably cannot see a scala field (a hard limit, not a gap); the SETTER direction stays unclosed — no corpus consumer writes a property.

### K22. A java CLASS INITIALISER runs at CLASS INITIALISATION; the `object` it is emitted into is initialised by nothing — **5 test failures on liqp, 0 compile errors, every check count flat, and the whole family was invisible until K21 closed. CLOSED for both port-visible triggers; the REFLECTIVE one is refused, and so is a companion whose initialisation is CYCLIC (face 2)**

Tried: java's `static { }` initialiser runs at CLASS INITIALISATION (JLS 12.4.1); emitted into a scala `object`'s body it runs only when something touches the OBJECT, which `new T(...)` never does — silent mis-registration (SPI providers never register), 0 compile errors, every check flat.
Fix: `TirEmitter.forceCompanion` enumerates JLS 12.4.1's OWN trigger list and forces only the two broken items — `val _ = <T>` at the class-body head (instantiation trigger) and the same at a subclass's COMPANION head, forcing the nearest `<clinit>`-bearing ancestor; a reflective load is REFUSED; JLS 12.4.2 step 9 (fields+blocks as ONE sequence) is the real predicate, not the block node.
Numbers: liqp 567->572 passing, corpus blast 7 whole-type digests on libGDX + 3 on liqp. Face-2 correction: widening unconditionally hit a MUTUAL re-entrant cycle the JVM tolerates and scala cannot (`Vector3`/`Matrix4`, 217/4 -> 191/10 with 20 unreached) before being refused-and-counted (`ReentrantRefused`, self-edges excluded).

### P1. A `--js` compile proves NOTHING as a portability gate — CLOSED
(a) engine. Symptom: a `--js` compile of a port proves nothing about portability — Scala.js type-checks against JDK signatures and compiles `java.lang.reflect` happily; only the LINKER rejects it, and only for code reachable from an entry point, which a library lacks.
Rule: portability must be checked over the TIR (`PortabilityCheck`), never by compiling for the target.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: portability must be checked over the TIR (PortabilityCheck), never by compiling for the target" — governing rule, mechanism in place

### P2. A check that is not WIRED is not a check, and a rule that does not exist reports clean — CLOSED
(a) engine (wiring+rule) / (b) per-library API list. Symptom: a ported JUnit suite silently shipped for Scala.js/Native, neither of which has JUnit; nothing caught it.
Rule: when a check reports zero, name an API you KNOW is present and confirm the check sees it (same move that found P4).
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: wire the check before the translation (PortRun.RequiredChecks); a silent assumption becomes a number"

### P3. A JVM-only API in the LIBRARY is not an engine gap
(b) `Substitutions` + (c) injected sources; not an engine gap. Symptom: `java.lang.reflect`, `Thread`, networking, `java.util.zip`, `java.util.concurrent` in emitted code just means the LIBRARY uses them.
Fix: per-library substitution — replace the type with an injected implementation, exactly what a hand-port does.
Triage (2026-09-04): REFUSED-BY-DESIGN — family P: each JVM-only API a library uses is answered per-library via `Substitutions`/injection, one entry at a time — there is no single engine-level fix that closes this class; a card here is bookkeeping, not a build item

### P4. An EXTERNAL MEMBER is only identifiable through its owner — and it had no owner — CLOSED
(a) engine — frontend interning. Symptom: 9 of `PortabilityCheck`'s member-level rules (`Class#forName`, `#newInstance`, six reflective readers, `System#getProperty`) had NEVER fired in the project's whole history while the check reported coverage.
Numbers: libGDX core 139 -> 147, test port 148 -> 156.
Rule: an external type is correctly rooted at `SymId.None`; an external MEMBER must be rooted at its owning type, one level down — verified against a hostile rename spec.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: libGDX core 139 -> 147, test port 148 -> 156" — verified against a hostile rename spec

### P6. ONE portability rule list for TWO backends is wrong for one of them — **8 of 27 rules measured too broad, and one of the eight factually WRONG. CLOSED by a target set**

Tried: one `jsAndNative` portability rule list applied to every port regardless of declared targets; a survey at Scala.js 1.22.0/Native 0.5.11 found 7 rules DEMONSTRABLY REAL AND MAINTAINED on Native (`java.nio.channels.`, `java.nio.file.`, `java.util.concurrent.`, `Thread`, `ProcessBuilder`, `java.util.zip.`, `java.net.`); one rule (`System.getProperty`) was factually WRONG while `System.getenv` (the member it should have named) had no rule; a ninth defect was a matcher with no separator cut (`Thread` prefix-matched `ThreadLocal`).
Fix: `PortManifest.targets` scopes the rule list per port (default = all 3 platforms); fixed the matcher's separator cut and the false `why` strings; added the missing time/text/locale area (`MessageFormat`, `Collator`, `BreakIterator`, `Calendar`, `TimeZone`) plus `MessageDigest`/`SecureRandom`/`WeakReference`/`System#getenv`.
Numbers: `portability(all|emitted)` unchanged on every lane — only finding IDs moved. Rule: the DEFAULT must be every question the check asked before parameterisation (all 3 platforms), or `Set.empty`/`Set(Jvm)` would collapse 3 counted lanes to a floor in one commit.

### P5. The engine emits `.scala` AND NOTHING ELSE — a `META-INF/services` file is a deliverable no phase carries, and a rename moves both its NAME and its CONTENTS — **CLOSED; `PortManifest.serviceProviders`, one hand-written file traded for one manifest key**

Tried: `META-INF/services/<iface>` (`ServiceLoader` provider registry) is a deliverable no phase emitted, copied or renamed — reflectively-constructed providers gave the closure nothing to see, so 0 compile errors, 0 findings, and every registration a library performs at class-init silently no-ops.
Fix: `PortabilityCheck` gained a `java.util.ServiceLoader` rule; `PortManifest.serviceProviders` names the upstream descriptors, and `ServiceProviders` copies+renames each (file NAME and every provider LINE) through the run's own `emittedName`; empty = no-op, a declared-but-missing file is FATAL.
Numbers: liqp is the only corpus port shipping one (2 providers), hand-written file replaced by the emitted pair; 14 others write nothing. Platform split: Scala.js has NO `ServiceLoader` at source level (must not be ported); Scala Native resolves it at LINK time from the same resource files.

### P6. `accept-jvm-only` — the menu entry whose APPLY ARM cannot fire, and why it ships anyway — **CLOSED as a design finding; 0 of the portability rules name `Platform.Jvm`**

Tried: shipped an `accept-jvm-only` remediation entry so a port could say "this location is JVM-only, stop reporting it"; measuring found its APPLY arm structurally unreachable — a non-JVM-targeting port makes the selection a CONTRADICTION (refused), and a JVM-only port has ZERO portability rules asking about the JVM at all (`rulesFor(Set(Jvm)) == Nil`).
Numbers: demonstrated live on liqp: `remediation` 12->14 with `portability(emitted)` flat at 56 (refusal did not drain); once P5's cross-platform wrapper landed the entry was DISCHARGED and removed: `remediation` 20->17, `portability(all|emitted)` 56->54, 0 errors, 636/1 unchanged.
Rule: `targets` and `accept-jvm-only` are two spellings of one decision — the module-level knob or a per-API `verdictOverrides`/dependency declaration is the honest one; a port reaching for "accept" almost always should declare a dependency instead.

### P7. `portability(emitted)` counted every DROPPED type on every RENAMING port — **CLOSED; libGDX core 153 -> 69, `remediation` 30 -> 15, at 0 errors and 0 moved member digests**

Tried: `portability(emitted)` was meant to exclude findings inside types the port DROPS, but the filter compared a `dropTypes` key (UPSTREAM FQN) against `Symbol.fullName` (EMITTED name) — on any renaming port the set was always EMPTY, so `emitted` == `all` and every violation inside a dropped type counted as shipped.
Fix: read the frontend's own DROP TAG instead of re-deriving from the key string — `PortRun.droppedIds`, one predicate, also fixing `dependency-coverage` and `jdk-surface`, which read the same set.
Numbers: libGDX core `portability(emitted)` 153 -> 69, `remediation` 30 -> 15, `jdk-surface` 24 -> 22, 0 errors, 0 digests. Rule: an artifact joining POLICY (upstream namespace) to OBSERVED code (emitted namespace) needs BOTH names, or it silently agrees only on a non-renaming port.

### P8. CLOSED — a declared coordinate is a 2×2 over BOTH programs, and the PROVIDES-SET is read from the artifact

Tried: `dependency-coverage`'s `unneeded` lane read a `Verdict.Depend` coordinate as stale once its redirect REMOVED the JDK usage that justified it — instructing "remove" would delete a coordinate the emitted code now names outright; `policy 0->1` on liqp, both usage lanes blind to it.
Fix: ask a 2x2 over BOTH programs (pre-pipeline usage x emitted usage) — EMITTED decides keep/remove, ORIGINAL decides the sentence (`Covered`/`Stale`/`Introduced`/`Unused`); the emitted half's PROVIDES-SET is read from the artifact's own JAR (`cs fetch --intransitive`), three-valued (`Unverifiable` when unfetchable, never collapsed).
Numbers: liqp `policy` 1 -> 0 (`Covered`); new `dependency-coverage(declared)` lane 0 -> N on all 15 ports; correction — a call-site-only substitution (no interned type) was invisible to the emitted-usage walk, closed by scanning the emitted program's `Tree.Opaque` splices, flat on all 15 ports.

### P9. OPEN — the descriptor SHIPS and nothing OFF THE JVM ever reads it: P5's silence, one platform over — **COUNTED, not closed; liqp `service-providers` 2 -> 3**

(a) engine (count shipped) + (a)/(b) engine + manifest key (trigger, unbuilt). Symptom: P5's SPI descriptor emits fine for the JVM, but Scala.js/Native resolve providers by REGISTRATION (an object body a cross-platform wrapper generates), which nothing in a ported LIBRARY ever forces — `PlatformServiceLoader.load` silently returns an empty iterator, no compile error, no count.
Cause: P5's fix answered a JVM-trigger fact (a classpath scan needs nothing to run first) that does not carry to registration-based resolution; it is K22's class-initialiser shape at a declaration a CODE GENERATOR wrote, not the port.
Fix built: `ServiceProviders.findings` files one `off-jvm-unwired` row per descriptor for a port's declared non-JVM targets; empty targets = no-op.
Numbers: liqp `service-providers` 2 -> 3 (counted, not fixed).
Do NOT retry: assuming P5 closes the non-JVM half.
Next step: force a `val _ = <RegistrationObject>` ahead of first use (K22's mechanism) plus a new (b) manifest key naming the codegen's generated object FQN — unbuilt because no corpus port builds off the JVM to measure it against.
Triage (2026-09-04): FAMILY P: force registration ahead of first use plus a (b) manifest key for the codegen's generated object FQN — "Next step: force a val _ = <RegistrationObject> ahead of first use … plus a new (b) manifest key … unbuilt because no corpus port builds off the JVM"

### P10. "Reflective instantiation becomes a REGISTRY" is a shape three ports share — refused as a SHARED SUPPORT TYPE, BUILT as a phase that MINTS one INTO each port. **`RegistryTransform` (§1b) SHIPPED on ALL THREE; ashley `portability(injected)` 4 -> 0, gltf's injected factories file DELETED, ai `registry(jvm-only-miss)` 0 -> 2 + `registry(guarded-call)` 0 -> 1 -> 0 (wave 3's `Miss.JvmReflect(onFailure)`) and its hand suite 103/2 -> 104/1; 0 errors held everywhere**
(b) engine — `RegistryTransform`/`RegistryCheck`. Symptom: three ports (libGDX `Pools`, Ashley `ComponentFactories`, gdx-gltf `GLTFExtensionFactories`) independently hand-wrote a `Class`-keyed registry replacing `Class#newInstance` (unavailable off the JVM).
What was RIGHT in the 2026-09-04 refusal, and stays refused: nothing SHARED can hold all three — the covering abstraction is a `Map` the target language already has, so `balticporter/runtime` still ships no registry type; and the three ports disagree about what an unregistered key answers, which a single-parameter table cannot hold.
What was WRONG: the conclusion was drawn about a SUPPORT TYPE and applied to the MECHANISM. A phase that MINTS the table and its `register`/`create` INTO the port at a declared `Placement` (an existing type's members, CT7; or a top-level `object` written only by the module that emits the call sites, O5) gives each port its own names, its own `T` bound and its own `Miss` (`Null`/`Throw`/`JvmReflect(onFailure)`) — nothing shared has to agree, and the *8 net lines* pricing was measuring the wrong thing: what the extraction buys is that gdx-gltf and gdx-ai get the mechanism from a VALUE instead of a fourth hand-written file.
Numbers (ashley, wave 1): 0 typer errors held on JVM/JS/Native/ref; suite 112 = 108 pass / 2 fail / 2 skipped, held; `portability(injected)` 4 -> 0 (the injected `ComponentFactories.scala` is deleted), `registry(jvm-only-miss)` 0 -> 2 (Scala.js, Scala Native) at the module that MINTS, the other six registry lanes 0, and the TEST source set flat on every registry lane and every other check — the rewrite runs over the base's unit there, the minting and the findings do not, every other ashley lane byte-identical, gdx `members-unchanged` 0. `members.tsv` blast 7, all attributed: 4 minted (the unit and its three text members) + 3 changed (`Engine`'s class digest, `registerComponentFactory`'s source, `createComponent`'s body) — residue EMPTY.
Numbers (wave 2, gltf + ai, `PROGRESS.md` §13.27). gltf: the `GLTFMaterialExporter#ext` body key and the injected `GLTFExtensionFactories.scala` are BOTH retired for one `Placement.Object` entry with 7 `seeds` and `miss = Throw("sge.utils.GdxRuntimeException", …)` — java's own `catch (ReflectionException) { throw new GdxRuntimeException(e) }`, so `handles` makes that `try` dead EXACTLY; 0 errors held JVM/js/native and 2 on `.ref`, suite 29/1 held, ALL SEVEN registry lanes 0, `policy` 2 held (every seed's nilary ctor verified in the TIR), `members.tsv` blast 15 = 11 minted + `GLTFMaterialExporter`'s class digest and `ext`'s body, residue EMPTY. ai: `Task#cloneTask()` retired for one entry with `miss = JvmReflect` and `handles` EMPTY; 0 errors held on all four, `registry(jvm-only-miss)` 2, `registry(guarded-call)` 1, `api-parity(port-extra)` 565 -> 568 (the minted object plus `register`/`create`), the 20 `api-parity(hand-port-extra) *#newInstance` rows held, blast 8 = 4 minted + 2 changed. gdx `members-unchanged` 0 and every gdx finding byte-identical: both entries are scoped to their own packages.
Numbers (wave 3 — `Miss.JvmReflect(onFailure)` and `ClassTableTransform(scope)`, `PROGRESS.md` §13.27). STOP (a) is CLOSED. `Miss.JvmReflect` now carries an `onFailure` — `OnFailure.Null` (the pre-parameter answer, which RENDERS as `JvmReflect` so ashley's and every other port's `policy=` is provably flat) or `OnFailure.Throw(fqn, message)`, which the emitted `create` raises when the JVM reflection ITSELF fails. ai declares java's own `Task.java:266-272` (`catch (ReflectionException e) { throw new TaskCloneException(e) }`) as `JvmReflect(Throw("sge.ai.btree.TaskCloneException", …))` with `handles = ReflectionException`, so the handler the rewrite made dead is elided EXACTLY: `registry(guarded-call)` 1 -> 0 (the `Task.java:266` row), `registry(jvm-only-miss)` 2 and the other five lanes held, 0 errors held on JVM/js/native/ref, every other ai check byte-identical, `members.tsv` blast 4 all attributed — `Task#cloneTask()` and its class digest (the elided `try`) plus `TaskFactories#<stmt3>` and its class digest (the new miss arm), residue EMPTY; the ai `port-map` moves by exactly those two members and the `policy=` header, ai-test's by the header alone (rows unchanged). gdx `members-unchanged` 0 with `findings` and the WHOLE `port-map` — header included — byte-identical: both new parameters contribute NO fingerprint segment at their defaults. The hand suite's measured exit MOVED but did not close: `cloneTask creates independent copy` now fails with `TaskCloneException: cannot clone a task with no no-argument constructor` where it failed with an NPE from `copyTo(null)` — JAVA'S OWN ANSWER, since javac raises `ReflectionException` for a type with no nilary ctor. What the test asserts is the REFERENCE port's divergence (an abstract `Task#newInstance()` java does not declare), so the row is re-anchored at the minted registry rather than deleted; ai-test 104/1 and ai-diff 94/1 held, the differential's declared row `expected 1, unexpected 0`.
STOP (b), RE-MEASURED and STILL OPEN — `openTask` stays a `MethodBodyTransform` body, now for THREE named shapes and not one. `ClassTableTransform` HAS the half it was missing: a `RuleScope` (`Everywhere(Set.empty)` default, the pre-scope path, no fingerprint segment) and a `MergeablePolicy` whose same-callee/different-table pair COMPOSES over DISJOINT scopes (`RuleScope.disjoint`) and REFUSES where they overlap. It is not enough:
(b1) §1(b) PORT — `Everywhere(Set.empty)` is DISJOINT FROM NOTHING. libGDX core binds `ClassReflection#forName -> AssetTypeRegistry#classFor` at that default, which covers `com.badlogic.gdx.ai`, so ai's `Only(Set("com.badlogic.gdx.ai"))` overlaps it and the fold REFUSES (`SurfaceFold.Cause.Conflict`, fatal `SurfaceDivergence`). Making the pair disjoint means NARROWING THE BASE — `Everywhere(Set("com.badlogic.gdx.ai"))`, or an `Only` over the three declarations libGDX core actually calls `forName` from (`Skin.java:521`, `Json.java:996`, `ResourceData.java:132`) — and both are NON-EMPTY scopes, so both move gdx's `port-map` `policy=` and the header nine dependents inherit: its own commit with its own measurement, never a rider on another.
(b2) §1(b) ENGINE — `RegistryTransform` cannot key TWO entries on ONE callee. After the name half, `openTask` reads `newInstance(<table>.classFor(s))` and `cloneTask` reads `newInstance(this.getClass())`: one callee, two java contracts (`BehaviorTreeParser.java:539` wraps `ReflectionException` in `GdxRuntimeException("Cannot parse behavior tree!!!", e)`; `Task.java:270` wraps it in `TaskCloneException`). `miss` and `handles` are per-ENTRY while `mapping` is `Map[SymId, Rewritten]` keyed by the CALLEE SYMBOL, so a second entry OVERWRITES the first and `transformApply` has no site key — the last entry wins at every site. It needs what `ClassTableTransform` just got: admission per SITE, the mapping keyed by `(callee, Origin)`, and the disjointness rule over entry scopes.
(b3) §1(c) PORT — even with both, the registry would be WORSE than the kept body. `create` builds by `() => new X`, and `decorator.Random` has no nilary constructor (C11: java's `Random()` delegates to `this(ConstantFloatDistribution.ZERO_POINT_FIVE)`), so neither `seeds` nor `JvmReflect` can construct one of the eighteen built-ins; the injected `TaskRegistry` writes that factory by hand. Retiring the BODY would not retire the FILE. `findMetadata`/`getField`/`setField`/`castValue` are the `TaskField` (c) and were never P10.
Next: (b1) as its own gdx-scoped commit, then (b2); a fourth `Miss` before a fourth port.
Two engine defects this wave MEASURED and fixed: `RewriteTrace.orphanedCalls` called a text-spliced member an orphan (`signature` 14 -> 15 -> 14); and `PortRun`'s idiom `ownPaths` is keyed by java PATH, so a MINTED unit put `<synthetic>` in the owned set and every origin-less candidate in the program — a BASE's included — passed the filter (ashley `idiom(refused)` 123 -> 125 -> 123, rows about `ScrollPane` in a dependent's lane). The fix costs gdx 2 rows it had been counting for the same reason (`idiom(refused)` 2476 -> 2474, `TextureHandle.scala` is a minted unit) and makes it consistent with the other twenty ports; that a candidate with NO java origin is invisible to the idiom lanes is the residue, and the real fix is to attribute a candidate by SYMBOL ownership rather than by path — a corpus-wide blast, not this wave's.
Residue, named not fixed: `portability(emitted)` reads external SYMBOLS, and a member spliced as `Tree.Opaque` has none, so the emitted reflective arm is counted at the DECLARATION (`registry(jvm-only-miss)`, one row per declared non-JVM target) and not per API — the blind spot `add-members` and `method-body` already have; a minted unit's licence header reads `<unknown>` (§4.57) because giving it the call site's java path makes the trivia harvest claim that file's comments (measured `trivia(recovered)` 0 -> 17); `port-map` names the three text members `<stmtN>`.
Rule: a shape recurring across N files may still be a mechanism — the question is whether what recurs is the TYPE (which the target language may already have, and which then belongs in no shared module) or the TRANSLATION (which is always the engine's). `Miss.JvmReflect` is DECLARED by a port and its non-JVM cost COUNTED (`registry(jvm-only-miss)`, one row per declared non-JVM target), never silently refused: refusing it outright makes the mechanism unusable on all three ports P10 names, because each of them instantiates types nothing registers.
Do NOT retry: a registry keyed on anything but `Class[T] -> () => T`; a concurrent table (`runtime` ships no threading); pricing an extraction by LINES SAVED in the ports that already have one; `Miss.Throw` at a SELF-CLONE (it refuses the site and the dropped callee then does not compile); composing a dependent's `Only` scope with a base's unrestricted `Everywhere(Set.empty)` (they OVERLAP — the base must narrow first, which is a gdx measurement of its own).

### P11. A call to an EXTERNAL MEMBER whose arity differs between JVM and JS/Native — **CLOSED; gdx-test JS 1 -> 0, Native 1 -> 0**

Tried: the frontend reads external members from JVM class files (a java method always has `()`), but a Scala.js/Native platform shim may declare the same member PARENLESS (munit 1.2.0's `Description#getTestClass`/`getMethodName`); the emitter kept the JVM arity — `E050 ... does not take parameters` on JS/Native only (the old scala-cli cross-compiles never saw it; the sbt matrix compiles against the real platform classpath).
Fix: `PortManifest.externalParenless` — exact member-FQN set, dropped `()` when listed (legal on the JVM too), empty = no-op; separately `dropMethods`+`TestFrameworkTransform(dropFields)` removed a dead `@Rule TestWatcher` field entirely, avoiding a second, deeper type error inside it.
Numbers: gdx-test JS 1->0, Native 1->0, JVM/ref unchanged, suite 184/7, 0 member deltas on gdx-core/Ashley. Rule: `externalParenless` is universal (arity); the field drop is per-library policy.

### K23. SE8 put DEFAULT METHODS on `List`, `Map` and `Collection`, and a library written since uses them like `get` — **ssg-md 137 → 106; six mapped, two REFUSED, one gap named**

(a) engine — `CollectionsTransform`. Symptom: SE8 default methods on `List`/`Map`/`Collection` (`sort`, `computeIfAbsent`, `removeIf`, `containsValue`, `containsAll`, `ensureCapacity`, `listIterator`, `spliterator`) landed on a retyped owner with no rewrite arm — `jdk-surface` had silently reported them `unhandled` for the port's life (38 rows, 33 errors' worth).
Cause: each scala-named lookalike means something DIFFERENT — `sorted` doesn't mutate, `getOrElseUpdate` records a null result java never stores, `filterInPlace` keeps the COMPLEMENT of what `removeIf` removes, equality DIRECTION differs for `containsValue`/`containsAll`.
Fix: 6 members mapped as dedicated helpers, never renames; `removeIf` split by receiver KIND (Buffer vs Set); `ensureCapacity` is a no-op. Numbers: ssg-md 137 -> 106 initial; a bound-method-reference gap was later BUILT (main 37->35, test 13->7); `listIterator`'s refusal was REVERSED and BUILT as a standalone shim (ssg-md 11->9, `collection-closure` 2->0, `jdk-surface` 26->25).
Do NOT retry: the original `spliterator` refusal's stated EVIDENCE (a live view reports neither ORDERED nor SIZED) — measured FALSE on 3.8.4; `spliterator` STAYS refused for a real reason (parallel-decomposition, stream-only) but must be re-keyed at all 3 owners it's re-declared at, not just one.
Rule: a REFUSED verdict rests on the RECEIVER's capabilities, not the stdlib's shape — re-test it whenever the receiver's type changes; a refusal lane must be keyed at every owner java re-declares a default method at.
Triage (2026-09-04): FAMILY K: `spliterator` refusal re-keyed at every owner java re-declares a default method at — "a refusal lane must be keyed at every owner java re-declares a default method at" (currently keyed at only one)

### K24. Java declares `get`, `contains` and `remove` over `Object` ON PURPOSE, and a retyping types them at the element — **ssg-md 106 → 89 at the PROBE face, then 49 → 47 at a third condition on the RECEIVER face: scala's `Map[K, V]` is INVARIANT in `K` and java's `get(Object)` never asked. CLOSED**

Tried: java's `Map.get/containsKey/remove(Object)`, `Collection.contains(Object)`, `Set.remove(Object)` look up BY VALUE on purpose (a probe of an unrelated type MISSES rather than fails to compile); once the receiver retypes to scala's element-typed `Map[K,V]`/`Set[A]` the argument no longer conforms — a naive cast would throw where java answers null/false.
Fix: probe helpers (`mapGet`/`mapContainsKey`/`mapRemove`/`setContains`/`setRemove`) widen the PROBE to `Any`, reproducing java's lookup direction, oracle-free (`Object` is java's reference-hierarchy TOP); 3 further conditions closed later — a wildcard-bearing KEY the probe doesn't literally spell (INVARIANT `K`, via `TypeRepr` equality) and a probe at a PROPER ANCESTOR of the element type (`ancestorProbe`, walks `extends` edges, no subtype oracle).
Numbers: ssg-md 106->89 (probe face), 49->47 (wildcard-key, 18 digests), ssg-md-ext 1->0 (ancestor), each byte-identical elsewhere; `jdk-surface` 27->25. Rule: answer conformance with NO subtyping oracle (`Object`-is-top, or this run's own `extends` edges), never a name test; `getOrDefault`/`put` at an `Object` probe left NAMED not filled.

### K25. A member that OVERRIDES A CLASS FILE may not have its formals moved — **ssg-md 69 → 67, `collection-boundary` 26 → 22, every other port flat. CLOSED, with two errors it made LOUD**

Tried: `CLAUDE.md` §4.56 (an unowned symbol's signature is a class-file fact no phase may move) applies to an OVERRIDE too — `class BitFieldSet extends java.util.AbstractSet` declares `containsAll(Collection<?>)` over an UNPARSED parent, so retyping the override's formal broke it against the class file it overrides (4 errors).
Fix: `Issue.ClassFileOverride` holds the member's formals at java's own shape via existing scope machinery; correct test is 4 conjuncts (`isOverride` AND no in-program ancestor resolves this signature AND no external ancestor that MAY declare it is one the mapping covers AND read `overriders` downward) — never permissive `mayDeclare` alone (measured 69->113, wrongly holding 104 members of program-declared interfaces).
Numbers: ssg-md 69->67 (5 members held), `collection-boundary` 26->22; held members also made 2 previously-silent errors loud (invisible before since `RefChecks` never runs at nonzero typer errors). Rule: an ANONYMOUS class body is not on the spine the holding machinery splices along, so it stays retyped — an anonymous symbol has no `Definition`.

### K26. The mapping BREAKS java's own subtyping edges, and the seam that leaves has the JDK on NEITHER side — **16 of one port's 24 attributed errors, counted by nothing. LANE BUILT (`collection-internal`, 7 + 16); `DeclaredSubtype` CLOSED at the slot (`16 -> 0` and `7 -> 5`, 11 errors); `SplitTypeVariable` CLOSED at the INFERENCE site (`5 -> 0`, ssg-md 18 -> 13). CLOSED**

Tried: `typeMap` sends `Collection` to a standalone shim but every java SUBTYPE to a `scala.collection.*` type, so java's `List <: Collection` edge has no scala-side image; every boundary check compares against the JDK and this seam has the phase's OWN OUTPUT on both sides — `collection-boundary` read 0 while 16 of ssg-md's 24 attributed errors were exactly this (three distinct blindnesses: no formal HEAD to compare, a program-declared class re-parented onto the far side, or a phase-MINTED callee with `NoType`).
Fix: new lane `collection-internal` names the broken java EDGE and both targets it became — `DeclaredSubtype` (both sides are the phase's own output) CLOSED by reading the phase's own re-parenting record transitively; `SplitTypeVariable` (javac binds a type variable from a PARAMETERISED/invariant argument, only converts a bare one) CLOSED by substituting at the inference site before `coerce`.
Numbers: lane built at ssg-md 7+16; `DeclaredSubtype` 16->0 (test)/7->5 (main), 11 errors closed; `SplitTypeVariable` 5->0, ssg-md 18->13, 4 digests; every other port byte-identical. Rule: never derive "internal" from PACKAGE membership (3 runtime targets deliberately extend a scala collection); a third arm at the CALL's OPERANDS was built, measured 1 false positive, and REMOVED.

### K27. A MINTED PARENT puts its own members beside the class's, and java's one-candidate call becomes scala's ambiguous one — **ssg-md test set 44 → 34, main flat at 40, eleven other lanes byte-identical. CLOSED**

Tried: a program class the phase re-parents onto a scala collection (`OrderedMap implements java.util.Map` -> `extends scala.collection.mutable.Map`) inherits the scala member BESIDE the java-obliged one at the same name — java's ONE-candidate call becomes scala's AMBIGUOUS one (E051), 10 sites, `overload-risk` correctly reading ZERO.
Fix: pin the call with java's OWN disambiguating idiom — ascribe the `Object`-typed argument, the exact node kind the frontend already builds for a java `(Object)` cast; guarded by 4 conjuncts (owner re-parented by this phase; parent's target not STANDALONE; parent declares that name+arity AT ITS type parameter; argument not already `Object`).
Numbers: ssg-md test set 44->34, main flat at 40, eleven other lanes byte-identical; fixed two implementation bugs along the way (a LAST-parent-wins bug, and reading the `extends` CLAUSE instead of the bare receiver type). Rule: where BOTH alternatives take `Object`, no ascription can disambiguate and the refusal is LOUD, not counted.

### K28. A MINTED PARENT's members are a REFCHECKS question, and it has FIVE verdicts — **REACHED at wave 18: ssg-md 1 -> 131. The five verdicts are all present and they are 79 of the 131; the pricing was RIGHT about the shape and LOW about the size, for a reason the probe could not see** — CLOSED
(a) engine — `CollectionsTransform`/`RefChecks`/`MemberRenamer`. Symptom: RefChecks (invisible until a port is at 0 typer errors, CLAUDE.md §3) rises when a class the phase re-parents onto a scala collection inherits a minted parent's members beside java's own — 6 verdicts on a reduced probe (compiles / strip-override / strip-override-as-overload / real-retype / rename-or-do-not-mint / synthesize-abstract), plus 3 unpredicted families found only once the port actually reached 0 (E164 at a program-declared parent, an F-bounded generic override per JLS 8.4.2/G8.10, a java field named like an inherited JDK method).
Numbers: ssg-md RefChecks 1->131 (reveal) -> 60 (override-strip) -> 34 (F-bound/getBuilder family) -> 27 (duplicate-parent drop, flat on its own) -> 0 (rename+bridge mechanism); every other port byte-identical throughout; 167 member digests at the final wave, residue EMPTY.
Rule: an obligation the ENGINE'S OWN translation created belongs to the engine, never a manifest key; a diamond forwarder must not be minted over a `final` parent member (scala already accepts what java did there); a `(name,arity)` index used where only one spelling can be right (K28.2's cross-package visibility bug) is a choice nobody made — hold the whole candidate list and let the caller decide.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: … 167 member digests at the final wave, residue EMPTY"

### K29. A class that DEFINES a java collection calls the JDK's DEFAULT implementations through `super`, and a re-parenting removes them — **BUILT. ssg-md whole-compile 40 → 30, its TEST SET 6 → 0 and main 34 → 30, with the four `super` rows the bare mapping opened never opening** — CLOSED
(a) engine — `CollectionsTransform`, phase obligation. Symptom: `typeMap` mapped `Set` but not `AbstractSet` (breaking java's own `AbstractSet <: Set` edge); mapping `AbstractSet` alone closed one wall (ssg-md test set 6->0) but OPENED 4 new errors — `super.containsAll/addAll/removeAll/retainAll(c)` inside `BitFieldSet`'s own JDK-default-delegating overrides, since the new target has none of `AbstractCollection`'s bulk defaults at java's shape.
Numbers: built as 4 separably-measured commits (helpers alone: ssg-md main 34->32, 0 digests, flat elsewhere; the arm; the super->this substitution, both flat on all 17 ports; then the mapping: main 32->30, test set 6->0, 12 digests all in `BitFieldSet`/`BitFieldSetTest`); net lane arithmetic: `collection-closure` 3->2, `collection-boundary` 22->21 main/6->4 test, `jdk-surface` 25->26 (one row moved to the correct lane).
Rule: where a phase RE-PARENTS a class, ask what the JDK PARENT was implementing for it and answer PER MEMBER from the JDK's own body — never from the member's name, never as a general super->this rewrite.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: built as 4 separably-measured commits …; net lane arithmetic: collection-closure 3->2, collection-boundary 22->21 main/6->4 test, jdk-surface 25->26" — shipped

### K30. A JDK member the phase answers can arrive at a NODE KIND or an ARITY the table does not have, and scala accepts BOTH silently — **ssg-md 9 → 7, `jdk-surface` 25 → 23, `collection-boundary` 21 → 20. CLOSED**

Tried: a JDK member the phase already maps can arrive at a shape the rewrite TABLE doesn't recognise and scala silently accepts it as something else — a raw FIELD (`Collections.EMPTY_LIST`, no `Tree.Apply` arm sees a `Tree.Select`) wrapped into a nonsense element type; a second, unmapped ARITY (`List.addAll(int, Collection)`) fell onto `Growable.addAll(IterableOnce)`, which scala AUTO-TUPLES java's two arguments into — appending a pair where java inserted a collection, green compile, no count moved.
Fix: rewrite the raw FIELD to the same helper the CALL already uses (`Tree.Select` arm beside `Tree.Apply`), preserving reference identity; add the missing positional-arity arm (`insertAll`, java's own semantics); a third, related fix — a reference `Tree.If`'s two BRANCHES need per-arm conversion (JLS 15.25), not one lub-typed rewrite.
Numbers: ssg-md 9->7, `jdk-surface` 25->23, `collection-boundary` 21->20 (2 independent, separable fixes); a related conditional-branch fix separately ssg-md 6->5. Rule: a rewrite table keyed on `owner#name` is implicitly ALSO keyed on a node kind and an arity nobody wrote down — ask "what else can this member be in a tree" whenever a JDK member is mapped.

### K31. `size()` IS A HINT — `AbstractCollection.toArray` reconciles it against the ITERATOR, in both directions, and the runtime helper trusted it. **ssg-md's suite 703 → 704 passing, 0 errors, every check count flat, 0 member digests** — CLOSED
(a) engine, built. Symptom: `JavaCollections.toArray` allocated `new Array[Object](xs.size)` and filled by iterating — the obvious shape, not what java's `AbstractCollection.toArray` does; `size()` is a HINT java's own implementation RECONCILES against the iterator both ways, so a collection whose `size()` is a property of the TYPE rather than the current value (flexmark's `BitFieldSet`, a fixed-universe bit-set) got a trailing run of `null`s or an `ArrayIndexOutOfBoundsException` — valid scala, right elements, wrong array, no compile error, no count.
Numbers: ssg-md suite 703->704 passing, 0 errors, every check count flat, 0 member digests (behaviour-preserving by construction wherever the iterator yields exactly `size` elements — all 15 ports but this one).
Rule: read the JDK's own body for the arms your reimplementation doesn't have, not just its signature and happy path — this was the ONE uncensused failure in the port's first suite run, invisible for a wave because the JUnit message read as an ordering/equals problem.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: ssg-md suite 703->704 passing, 0 errors, every check count flat, 0 member digests"

### K32. K26's lane has a THIRD blindness — a broken edge with NO PROGRAM-DECLARED side and NO shared type variable, which is 2 errors it reports as **0**. **PARTLY CLOSED at the two slots; the LANE still reads 0**

Tried: `collection-internal` (K26) has a THIRD blindness — a broken java subtyping edge where BOTH ends are the phase's own external targets (neither program-declared, no shared type variable to bind at) — 2 real compile errors (a `DataKey<Collection<...>>` construction plus an `addAll`) the lane reported as 0.
Fix: both concrete slots closed — the `addAll` rewrite routes a SHIM source to the union-typed helper (was deciding a question about the wrong operand); `instantiatedFormals` binds a CONSTRUCTOR's class type parameters from the `new`'s own type argument (a `new` has no receiver, unlike K26's deferred ordinary-call case).
Numbers: ssg-md test scope 2 errors -> 0, `collection-internal` 0->0 throughout — the LANE was never taught the third `Issue` kind, recorded so the next port meeting this shape isn't silently unwarned.

### K33. A `return` in a `@Test` body has no method to leave once the body becomes a REGISTRATION — **LATENT, and currently masked** — CLOSED
(a) engine, NOT built deliberately. Symptom: `TestFrameworkTransform` turns `@Test void m(){...}` into a `test("m"){...}` STATEMENT; a java `return` inside then has no enclosing method — `E091 return outside method definition` (loud, unlike CLAUDE.md §3's silent non-local-return case).
Numbers: fired once on ssg-md (`FullSpecTestCase.testSpecExample`), then the corpus's own test-hierarchy work MASKED it (that method takes part in an override relation and stays a `def`, where `return` is legal) — 0 instances in the corpus today.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: … 0 instances in the corpus today" — LATENT and deliberately not built per CLAUDE.md §5's catalog-coverage rule

## 6. Porting a test suite

### X1. Converting JUnit to MUnit is a STRUCTURAL transform, not an annotation rename — CLOSED
(a) universal + (b) target framework parameterised. Symptom: converting JUnit to MUnit is a STRUCTURAL transform (class hierarchy, registration statements, lifecycle inlining), never an annotation rename.
Rule: belongs in the engine with the target framework as a (b) parameter, not as library policy.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: belongs in the engine with the target framework as a (b) parameter, not as library policy" — mechanism built (TestFrameworkTransform), referenced by the closed X2-X6 entries

### X2. CLOSED — MUnit's `assertEquals` is TYPE-CONSTRAINED, and all 33 errors were the transform's job

Tried: MUnit's `assertEquals[A,B]` needs a `Compare[A,B]`, unlike java's untyped form — mapping alone measured 1->33 errors; closing them needed JAVA'S OWN BINARY NUMERIC/REFERENCE PROMOTION re-applied in the transform plus X3's fix.
Fix: an argument-order permutation table for every junit assertion form (never `assertEquals` for `assertSame`/`NotSame`); `assertArrayEquals(e,a,delta)` emitted as an explicit length-then-elementwise loop; overload resolved from ARGUMENTS' static types, not the callee's signature; a `NoType`/primitive operand REFUSES the reference-widening (a boxed-numeric pair must not widen to `Object`, since scala's boxed `==` is numeric where java's `Integer.equals(Long)` is false).
Numbers: `balticporter.runtime.Asserts` deleted, 0 compile errors, 217/221 passing (33->0); liqp's independent widening gap 3->2; a downstream `NullabilityTransform` bug this uncovered (a `Tree.Typed` cast's own NODE TYPE overwritten by an unwrap arm that should only touch its OPERAND) cost gdx-test 0->4->0, since fixed.

### X3. CLOSED — a Java `static` test helper emits into the COMPANION OBJECT; use the framework's assertion OBJECT

Tried: a java `static` test helper's translation lands in the scala COMPANION OBJECT, invisible to an instance-scoped assertion trait (`Not found: assertEquals`/`fail`, ~6 errors); moving the helper onto the suite instance was REJECTED (changes scope for every static member; a helper literally named `test` would overload the framework's registration method).
Fix: MUnit declares every assertion on BOTH an inherited trait and an `munit.Assertions` OBJECT — emit assertions fully-qualified through the OBJECT, resolving identically from a suite body, companion, nested class or lambda.
Rule: when a target framework offers assertions as both inherited AND object members, emit the object members — inheritance is the only one a translated scope can fail to reach.

### X4. Calling `@Before` at the head of each test does not reproduce JUnit's FRESH INSTANCE — **PREDICTED, MEASURED at 4 of one suite's 10, then CLOSED: sge-ai `6 / 4` → `10 / 0`, every other suite in the corpus byte-for-byte identical in outcome**

Tried: calling `@Before` at the head of each test is exact only where setup ASSIGNS the fields it needs; a field carrying state through its own INITIALISER still leaked, since MUnit builds ONE suite instance where JUnit constructs a fresh one per `@Test` — gdx-ai's `ParallelTest` (4 fields initialised in place) got 4/10 failures, all `IllegalStateException`, invisible to every instrument but the suite.
Fix: hoist java's WHOLE initialisation sequence (JLS 12.5) out of the class body into `override def bpFreshState(): Unit = {zero MY fields; super.bpFreshState(); MY step4; MY ctor body}`, called at the head of every test ahead of `@Before`; a constructor the lowering cannot replay keeps BOTH its constructor and initialisers, refused and counted.
Numbers: sge-ai test 6/4->10/0; every other converted suite in the corpus BYTE-IDENTICAL (23 suites carry the member, ~200 stateless suites untouched). Rule: object IDENTITY is NOT reproduced (anything outliving the test that holds the instance sees the reset, counted) and is a shape limit, not a bug.
Triage (2026-09-04): REFUSED-BY-DESIGN — family X: object IDENTITY is not reproduced across a MUnit single-instance suite vs JUnit's fresh-per-test model — permanent shape limit, counted not fixed

### X5. JUnit lifecycle and enablement — CLOSED, except what has no translation

Tried: `@After` used to leave `tearDown` an ordinary never-called method (tests passed while leaking state); `@Ignore` ENABLED a disabled upstream test, turning a known-broken result green.
Fix: `@After` -> `try {...} finally {tearDown()}`; `@Ignore` -> `test(munit.TestOptions("n").ignore){...}`; `@BeforeClass`/`@AfterClass` -> `beforeAll()`/`afterAll()`.
Residue: what has NO translation (`@Rule`, `@ClassRule`, `@RunWith`, JUnit 5, TestNG, JUnit-3 `TestCase`, Hamcrest) is now REPORTED with its §1 class instead of vanishing; `@RunWith` changes which tests are ENUMERATED, so a converted suite can silently run a different SET while looking complete.

### X6. The JUnit surface a phase HANDLES is a VOCABULARY and a SCOPE, and both were narrower than JUnit — **CLOSED, four faces, all (a)**

Tried: censusing a large real suite (liqp, 639 `@Test`) found four gaps, none visible to any compile: assertion statics were only ONE FQN (`org.junit.Assert`), missing `junit.framework.Assert`/`TestCase` (31 sites); `assertThrows` had no mapping though `intercept` already existed; the rewrite was SUITE-SCOPED so a helper class with no `@Test` was never visited; a converted-class walk double-counted nested suites.
Fix: added the two JUnit-3 FQNs (not a parameter); mapped `assertThrows` to `intercept`, refusing two shapes it cannot honestly express; split the walk so assertion-rewriting runs over every unit while `@Test`-scoped conversion stays suite-scoped; one walk per unit.
Numbers: `PortabilityCheck` gained an `org.hamcrest.` rule (liqp: 1535 references, previously invisible to every portability lane though the phase printed the count to stdout); all lanes 0 members changed, since none of these shapes existed in the corpus before this. Rule: a decision not to translate is only defensible if what it leaves behind is a NUMBER.

### X5. A `@Rule` is not ONE construct — `ExpectedException` WRITES ITS CONTRACT DOWN, and the half no lexical wrap can express is the ARMING. **ssg-md's suite 683 → 703 passing at wave 24's `intercept`, and 704 → 721 at wave 25's MODEL of the rule — 0 errors throughout** — CLOSED
(a) engine. Symptom: `@Rule ExpectedException` is the one rule class whose contract junit WRITES DOWN (`ExpectedExceptionStatement.evaluate`) — 37 of one suite's 40 failures were the port being RIGHT about the library and wrong about the harness (a java-expected exception simply escaped `intercept`'s lexical wrap).
Numbers: wave24 `intercept`-only: 20 converted, 17 refused (683->703); wave25 full MODEL closes all 37 (704->721), 0 errors throughout, 43 digests, residue empty; 5 of 7 originally-enumerated deltas STOPPED BEING GUARDS once the model REPLACED (not supplemented) `intercept`.
Rule: `@Rule` and `@ClassRule` are TWO constructs (per-test vs whole-class-run scope) and must be distinguished even with zero corpus evidence for the class-rule shape — counted, not silently unhandled.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: … wave25 full MODEL closes all 37 (704->721), 0 errors throughout, 43 digests, residue empty"

### X7. A HEADLESS FIXTURE CANNOT UNLOCK A SUITE THAT DECODES AN IMAGE — the wall is a NATIVE library the port needs and nothing loads. **MEASURED with a stack; do not build the fixture first**

Neither (a) nor (b) — a native library is a BUILD input. Symptom: a differential-suite census classified 9 tests as blocked by a missing headless FIXTURE; probing the EMITTED tree directly (§3.5) found the real blocker one layer below any fixture — `ExceptionInInitializerError` resolving `java.lang.foreign` downcall handles into the library's own NATIVE image at the first PNG decode, which nothing in the port loads (java's backend app loads natives; a headless port has none).
Cause: a stub can supply a SERVICE (an interface the program declares) and never a SYMBOL (a `SymbolLookup` miss inside a `<clinit>`, one layer below anything program types can reach); the reference hand port's own suite passes these tests only because it DIVERGED (a hand-written ImageIO shim replacing the native decode) — its green suite is wrong evidence about this port, expensively.
Numbers: one 20-line probe program found it; the alternative (a fixture wave: 6 stubs, a 162-method GL no-op, suite adaptation) would have unlocked ZERO tests.
Rule: probe the emitted site before building a fixture wave; the exits are a port that puts the native image on the loader path, or a §1(c) substitution of the decode path — both decisions for a port that intends headless graphics.
Triage (2026-09-04): FAMILY X: native-image loader-path decision for headless graphics — "the exits are a port that puts the native image on the loader path, or a §1(c) substitution of the decode path — both decisions for a port that intends headless graphics" (undecided)

## 7. Measurement discipline — the ones that will mislead you

### X8. A java test that SUBCLASSES `ClassLoader` sees the SYSTEM loader, and sbt's forked test JVM keeps the application classes OUT of it — 5 tests, closed by an injected helper (2026-09-03) — CLOSED
(b) test-manifest policy (`dropTypes`+`inject`), a harness fact not an engine gap. Symptom: Ashley's `ComponentClassFactory extends ClassLoader` (ASM-generated subclass via `defineClass`) relies on `ClassLoader()`'s default SYSTEM-loader parent; sbt's forked test JVM loads project classes in a CHILD loader, so resolving the generated class's superclass failed — `NoClassDefFoundError`, ashley 108/2/2 -> 103/3/6, invisible to error-count/outcome-total comparisons (`TestDiff` caught it).
Rule: a test reaching for the SYSTEM loader, a loader-less `Class.forName`, or `ClassLoader.getSystemResource` is a harness assumption to check before a lane runs under a different runner; `TestDiff.newlySkipped`/`disappeared` are what catch it.
Triage (2026-09-04): CLOSED-IN-FACT — heading: "5 tests, closed by an injected helper (2026-09-03)"; "restoring ashley to 108/2/2"

### M1. The error count is a TYPER-ONLY measurement — the governing rule is `CLAUDE.md` §3 — CLOSED
(a) — understanding the gate, not changing it. Symptom: dotty's `Phase.isRunnable` is `!ctx.reporter.hasErrors`, so a file with one E007 beside a file with a missing `override` reports 1 error, not 2.
Numbers: reaching typer-zero for the first time made the count RISE 1->145 (93 override + 8 unimplemented-member errors that were there all along); another pass rose 300->6 with FOUR compiler phases running for the first time (parser/naming, typer, PostTyper bound checks [11 latent E057], RefChecks [907 latent E164]); one file verified byte-identical with/without a fix yet errored only with it.
Rule: expect the count to RISE when it first reaches 0 — that is the gate beginning to tell the truth.
Triage (2026-09-04): CLOSED-IN-FACT — governing rule already documented (CLAUDE.md §3): "Rule: expect the count to RISE when it first reaches 0 — that is the gate beginning to tell the truth"

### M2. A green compile says nothing about behaviour — the governing rule is `CLAUDE.md` §4.4 — CLOSED
(a). Symptom: §4.4's table was found entirely by RUNNING tests, never by a moved compile-error count; pass trajectory 48->52->88->113->115->183->187->188->201->217, with 115->183 a step-change (fixing control flow let the suite finish inside the timeout, not 68 tests fixed one at a time).
Numbers: 2 of 13 fixes were SILENT behavioural changes that would have shipped — a shadowing field emitting as ONE field, and an `override` of a method an injected substitute never declared, compiling to nothing.
Rule: run tests; do not watch only compile-error counts fall.
Triage (2026-09-04): CLOSED-IN-FACT — governing rule already documented (CLAUDE.md §4.4): "Rule: run tests; do not watch only compile-error counts fall"

### M3. A two-stage measurement can be honest about its own stage and still lie — CLOSED
(a) in the measurement scripts. Symptom: a two-stage measurement (test-only re-emit) is blind to a stale CORE transform until the core measure runs — one experiment's first reading (a harmless-looking 5->5) was against a stale core.
Rule: run the core measure first whenever the change is to the engine.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: run the core measure first whenever the change is to the engine" — procedure established

### M4. A kill switch beats another condition — the governing rule is `CLAUDE.md` §4.6 — CLOSED
(a), governed by CLAUDE.md §4.6. Symptom: three consecutive widenings of one gate's condition each measured 11->11 INERT; a kill switch (return input unchanged, print on entry) showed 72120 suppressed calls with the cast UNCHANGED — the gate wasn't responsible, the cast came from the EMITTER, found by tracing all 16 construction sites of that node kind.
Numbers: two further false leads recorded — "give the gate the rendered receiver type" (6->6, the Spoon type was already non-raw) and widening a scope-clearing gate (3->3, the cast path doesn't route through it).
Rule: a kill switch (return-input-unchanged + print-on-entry, or a construction-site tracer) beats guessing at another condition; `sbt -client` talks to a long-running server, so gate any debug switch on a marker FILE, never a shell env var.
Triage (2026-09-04): CLOSED-IN-FACT — governing rule already documented (CLAUDE.md §4.6): "a kill switch … beats guessing at another condition"

### M5. Walk the tree with `StandardTraversal` — the governing rule is `CLAUDE.md` §3 — CLOSED
(a). Symptom: both silent-omission defects in this project were hand-rolled traversals stopping one node short (the anonymous-class omission itself, and a mutable-params transform that walked class bodies by hand and never saw an anonymous class's method — 15 `E052 Reassignment to val` appeared the moment anonymous bodies started being emitted).
Rule: walk with `StandardTraversal`; add the check in the same commit as the translation path and negative-test it — a check that has never failed is not known to work.
Triage (2026-09-04): CLOSED-IN-FACT — governing rule already documented (CLAUDE.md §3): "walk with StandardTraversal; add the check in the same commit as the translation path"

### M5.5 After editing `runtime/`, RESTART the sbt server before believing a vendoring spec — CLOSED
(a) process. Symptom: `RuntimeArtifact` reads runtime sources from a build-copied resource; `sbt -client`'s CLASSLOADER caches that resource for the server's life, so a non-forked spec kept seeing a stale copy after a `runtime/` edit.
Numbers: a plausible-looking build change (`IO.copyFile`->`IO.write`) was attributed to the wrong layer and reverted once restarting the server (not the build fix) made the failing specs pass.
Rule: restart the sbt server after editing `runtime/`, before trusting a vendoring spec; a change that cannot be shown to fix what it claims to fix is a comment that will mislead the next reader.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: restart the sbt server after editing runtime/, before trusting a vendoring spec" — procedure established

### M5.6 Killing a hung `sbt -client` WEDGES the server permanently — kill the SERVER, not the client — CLOSED
(b) instrument/process. Symptom: killing a hung `sbt -client` WEDGES the server permanently (sbt 2's `NetworkChannel.shutdown` blocks forever on a full queue), and every later command — including `sbt -batch`, which also goes through sbtn — queues behind the corpse, looking like a slow machine, indefinitely.
Rule: distinguish wedged-from-slow before killing anything — no file touched in 3 minutes (`find -newermt`) AND CPU unmoved between two `ps -o time=` samples means WEDGED; either one moving means merely slow.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: distinguish wedged-from-slow before killing anything" — procedure established

### M5.6b A dead server turns `sbt -batch` into a GREEN-LOOKING NON-RESULT — exit 0, no tests run — CLOSED
(b) instrument. Symptom: `sbt -batch` is a CLIENT — when its server dies or is killed mid-queue, it prints `[error] sbt server disconnected` and EXITS 0 having executed nothing (measured: 26 minutes queued at 0% CPU, then a 36-byte exit-0 zero-suite "completion" the instant the dead server was killed).
Rule: gate on OUTPUT, never exit status — a test lane's true marker is `[info] Passed: Total ...` per project; a run with none of them ran nothing whatever the shell says (same shape as §5.1's skipped-test lane).
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: gate on OUTPUT, never exit status" — procedure established

### M5.6c `sbt -batch "a" "b"` JOINS its arguments into ONE command — `"x/testOnly *" "y/testOnly *"` runs x with `y/testOnly` as a test-name GLOB — CLOSED
process. Symptom: `sbt -batch` JOINS its quoted arguments into ONE command — six quoted args printed ONE `Passed: Total 178` line; only the first ran, the rest were consumed as `testOnly` GLOBS matching nothing, silently.
Fix: join multiple commands into ONE string separated by `;`, and count `Passed:` lines against commands sent; every Justfile lane already does this.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: join multiple commands into ONE string … every Justfile lane already does this"

### M5.6d A classpath cache keyed on its COORDINATES is a cache keyed on nothing the resolver owns — the jars are in SOMEBODY ELSE'S cache — CLOSED
(a) corpus mechanism (`ClasspathCache.fresh`). Symptom: two ports reported `DID NOT RUN — refusing to measure stale output` with no visible error — coursier had EVICTED a resolved jar's directory after the coordinates sidecar was written, so a cache HIT pointed at files that no longer existed; worse, a seeding script then promoted the STALE `run-latest` into the committed baseline, caught only by `git status` on `port-map.tsv`.
Rule: a recipe's `DID NOT RUN` arm must print the last 20 lines of `OUT` (not only scalac-shaped ones); a lane that did not run must never be followed by a baseline accept.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: fresh now requires every cached entry to actually EXIST on disk … cs re-fetches in seconds"

### M5.7 An unchanged-tree `testFull` is a cache REPLAY — it proves nothing about flakiness — CLOSED
process. Symptom: sbt 2 caches test results — `testFull` on an unchanged tree is a perfect REPLAY (totals, stdout, timings reprinted) that executed nothing; 8 consecutive "runs" completed in ~8s with a spec's own file side-effect keeping its OLD mtime through all 8. Neither a comment-only edit nor a new classfile with a per-run constant busts the cache.
Rule: "N consecutive green runs" of an unchanged tree is ONE run; a cached green can also MASK real flakiness until an unrelated edit re-executes the suite, so a surprising failure may diff against the wrong commit.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: testOnly * at the ROOT bypasses the result cache and re-executes everything"

### M5.8 A symbol's ANNOTATIONS are types too — and `mapSymbols` was not showing them — CLOSED
(a) engine, done. Symptom: `StandardTraversal.mapSymbols` routed `Symbol.info` through `transformType` but never `Symbol.annotations` (each carries its own `tpe`, which the emitter renders `@...` from) — EVERY retyping phase had this blind spot: a §1(b) type redirect moved a marker annotation's type everywhere except the annotation itself (3 sites, `value <pkg> is not a member of` — no check moved, no test broke, the phase's own policy report claimed success).
Rule: an annotation is a declaration's CONTRACT, not decoration — a traversal that treats it as decoration is as wrong as dropping it; pinned by `TypeRedirectTransformSpec`'s annotation case.
Triage (2026-09-04): CLOSED-IN-FACT — "pinned by TypeRedirectTransformSpec's annotation case"

### M5.9 A baseline ACCEPTED IN A WORKTREE may not reproduce in the primary checkout — realpath the provenance root — CLOSED
(a) universal, the third CLAUDE.md §5.4 instance and the first to reach an EMITTED BYTE. Symptom: `TirEmitter.sourcePathOf` compared the parser-recorded origin against `Provenance.sourceRoot` with a LEXICAL `startsWith`; a git worktree reaches its sibling checkout through a symlink, so the two spellings of one directory disagreed only in worktrees, and a marker-cut fallback rendered a doubled path segment there vs the primary checkout.
Numbers: 44 vfx + 6 noise4j WHOLE-FILE digests in worktree-accepted baselines the primary checkout could not reproduce — 0 member digests, 0 counts moved, found only when `just measure-all` first ran in the primary after a wave of worktree-side integrations.
Fix: realpath BOTH operands before comparing, normalize only as the not-exists fallback (§5.4's rule verbatim); pinned by a negative-proofed symlink spec.
Triage (2026-09-04): CLOSED-IN-FACT — "pinned by a negative-proofed symlink spec"

### M5.10 The JDK is an INPUT to the measurement — a frontend on one JDK and a compile on another — CLOSED
(a) universal — the first measurement input this project had that no artifact named. Symptom: every number rests on TWO ambient JVMs (frontend, forked by sbt; compiler, `scala-cli --jvm`/`JAVA_HOME`) — nothing compared them; a migration under GraalVM 24 (newest-installed-JDK default) emitted `override def getChars` (added to `CharSequence` in JDK 23), then a JDK-22 compile reported `E037 overrides nothing` at a PERFECT translation, with every check count, finding, digest and all three port-map fingerprints flat — only an unrecorded input moved.
Rule: never read a bare `overrides nothing` at a faithful translation as an engine gap before checking which JDK each half of the run used; never "fix" a JDK split by moving `jdk_version` — that changes the measurement and must be re-accepted, not absorbed.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: … PortMap schema 4 adds a jdk= fingerprint and a mismatch is the only FATAL map-freshness verdict"

### M5.11 `sbt -client` in a git WORKTREE connects to ANOTHER worktree's server — a run that writes its artifacts into someone else's checkout — CLOSED
(b) instrument, engine untouched. Symptom: `sbtn` resolves "the" server for a project root by a hash of the base directory, and git worktrees sharing one `.git` resolve to the SAME hash — a run from one worktree was forked by ANOTHER worktree's server, wrote `run-latest/` there, compared against the wrong baseline; caused two multi-hour hangs and orphaned servers surviving removed worktrees for days.
Numbers: gdx-measure ~25min -> ~2min wall with the per-worktree client restored; two concurrent worktrees run without interference.
Rule: `sbt -client`, bare `sbtn`, and bare `sbt -batch` are all forbidden in lanes/agent briefs — every measure lane's migrator invocation must use the per-worktree-scoped client.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: gdx-measure ~25min -> ~2min wall with the per-worktree client restored; two concurrent worktrees run without interference"

### M5.12 Metals v2 standalone MCP per checkout — TOOLING LANDED, BUILD CONNECTION OPEN (2026-09-03)

tooling, no §1 kind. Symptom: Metals v2 standalone MCP per checkout landed (per-checkout port/label, launchd-managed) but the BUILD CONNECTION fails — a hard 2-minute await on `initialized()`, and the BSP connect to sbt cancels at 60s twice in a row regardless of machine load, though a direct framed BSP client to the same `sbt bsp` answers in 1-6s.
Cause unknown: Metals boots its OWN sbt launcher JVM (`-bsp`, not the thin client) per checkout; the slowness is specific to that spawned process.
Do NOT retry: pointing Metals' BSP at a lane's `SBT_GLOBAL_SERVER_DIR` to reuse the warm server — wedged the lane's thin client for 48 min (M5.11's shape, self-inflicted); Metals 1.6.6's `metals-mcp` is not a fallback (same broken service).
Next step: capture the spawned `sbt -bsp` child's own log; if the 60s/2min limits are the only wall, needs an upstream change making the await configurable/lazy.
Triage (2026-09-04): FAMILY M: Metals v2 BSP-connect timeout — heading itself: "TOOLING LANDED, BUILD CONNECTION OPEN"; "Next step: capture the spawned sbt -bsp child's own log"


### M5.13 The lls jar on the gdx TEST port's Spoon classpath — 0 -> 218 errors; the MAIN port takes it at 0 — OPEN (2026-09-05)
(b) port value (`FrontendConfig.classpath`), engine untouched. `LibgdxCoreMigrate` and eight dependents put `com.kubuszok:lls_3:0.3.0` (stdlib excluded) on the frontend classpath so `FrontendConfig.internTypes` reads `isFinal`/parents of the retarget targets: gdx `.ref` 1 -> 0, every other lane flat. The same jar on `LibgdxTestMigrate` (source root `gdx/test`, resolution root `gdx/src`) reads 218 errors — the jar's class files displace ECJ's resolution of types coming from the resolution ROOT, not the stdlib (excluded already). The test port keeps `Nil`; its `.ref` 1 stays.
Do NOT retry: the stdlib exclusion is not the lever. Next: read which 218 members ECJ mis-resolves (`errors.tsv` MEMBER column) against the jar's package list before touching the classpath order.

### M5.14 sbt 2's action cache REPLAYS a failed compile without its diagnostics, across worktrees — OPEN (2026-09-05)
(b) instrument, engine untouched. Two faces measured the same day: (1) liqp's `-ref` compile read "no countable error" — `sbt.util.CachedCompileFailure` replayed a previously failed compile of byte-identical inputs with none of its 3 warnings, and `clean` does not bypass the disk action cache (`~/Library/Caches/sbt/v2/ac`); fix 4dcdd7b3: every `-ref` project carries a per-execution `scalacOptions` nonce (`refPortSettings`), `compile_guard` names a replay. (2) an `engine/compile` that failed in ONE agent worktree (a `clean` racing the compile, `NoSuchFileException` on `BuildVersion.class`) was cached with `exitCode 1` and replayed in EVERY worktree with the same inputs, blocking a wave; the poisoned entry is deleted by hand from the primary (an agent's `rm` under `~/Library/Caches` is refused by the harness).
Numbers: liqp `.ref` 3 read as 0 countable; B2's worktree blocked until the entry (`ac/sha256-9833a1a0…-48`) was removed.
Note (2026-09-06): a suspected "replayed success" on lls was NOT one — the 11 `E164` rows (K39) appear only in an emission WITHOUT the `ordering` step (JDK `java.util.Iterator` kept as a parent); a standalone migrator experiment had overwritten `src_managed` between lanes. The ladder ports keep a per-run nonce anyway, so their counts are always real compiles.
Rule: a failed compile's number is read only from a run that PRINTED diagnostics; a `CachedCompileFailure` line in a capture is a replay, never a count. Do NOT put the nonce on the JVM port projects (a full recompile per lane); delete the entry instead. Next: an sbt 2 setting that never caches `exitCode != 0` results, if one exists (none found in the 2.0.x docs).
### M6. Refuse and COUNT rather than approximate — CLOSED
(a) engine. Symptom: 4 places the port deliberately carries a NUMBER instead of a guess (49 dropped `super(args)`, 156 construction paths running a promoted constructor's body java never ran, a raw anonymous class refused, a single-primary encoding left as a genuine compile error) — the fourth's own justification ("the compiler is a louder tracker than silence") is FALSE wherever the untranslated construct is ALSO valid scala: a refused java lambda `return` left a scala NON-LOCAL RETURN from the enclosing method, libGDX core carrying 3 of them at 0 compile errors until COUNTED.
Numbers: `omissions` 66->69 (main), 3->4 (test) once the lambda-return case was counted.
Rule: "refuse loudly" is a claim about emitted TEXT you don't control — refuse AND count, always; a residue comment count is itself a measure, don't delete it for tidiness.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: refuse AND count always … PortRun(preview=true) is a separate diagnostic mode" — mechanism shipped

### M7. A check over EMITTED TEXT must join on a RECORDED id, never on the rendering — 594 → 0 — CLOSED
(a). Symptom: `NoteCoverageCheck`'s first version joined a porter note back to its decision by re-parsing the note's `k=v` text against the decision's `detail` string — reported 594 false "unbacked notes" on libGDX core because the pair-list is whitespace-separated and a value containing a space got truncated differently on each side.
Numbers: 594 -> 0; separately, `SubstitutionCheck.dangling`'s plain substring search matched a note's own UPSTREAM-FQN mention as a live reference, reporting `substitution(dangling)` 0->3 on a port whose replacement was on disk — fixed by stripping porter notes first.
Rule: a check over emitted text must join on a RECORDED id, never on the rendering; a check that greps for an FQN must strip porter notes first.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 594 -> 0"

### M8. A note is emitted only where the emitter ASKS for one — a member on a special path has none — CLOSED
(a) engine. Symptom: `NoteCoverageCheck`'s OTHER direction ("a decision with no note") fires whenever a policy decides about a member the emitter renders through a path that never calls `declNotes` — a java `static {}` block (carried as a synthetic member, emitted as `locally {...}`, never a `def`) got a `MethodBodyTransform` decision recorded with no note beside the code.
Numbers: `porter-notes` 1->0 on gdx-vfx, 0 members moved elsewhere (no other port decides about a `<clinit>`).
Fix: `<clinit>`'s emission path now calls `declNotes`.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: porter-notes 1->0 on gdx-vfx, 0 members moved elsewhere"

### M9. A lane's ERROR COUNT was the one measurement nothing compared — 0 -> 3 exited 0 — CLOSED
(a) engine — measurement machinery, no library involved. Symptom: the compile-error TOTAL was printed to stdout and never diffed — a non-zero count is LEGITIMATE here (gltf sits at 3, noise4j at 2, for written-down reasons), so no lane could distinguish "3, as always" from "3, as of this commit"; `measure-all` walked through a real regression (screens 0->3, an external vararg shape change broke a hand-written shim) reporting success, found only by reading the raw capture.
Rule: every number a lane prints must be diffed against a committed baseline, including the headline error count — a hand-typed baseline is the one that can disagree with the run that produced it.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: baseline/expected-errors … gated in BOTH directions" — mechanism shipped

### M10. An identifier keyed on a raw `SymId` turns a ONE-SYMBOL change into a 122-member blast — and `members.tsv` is exactly the instrument it defeats. **The EMITTED half is CLOSED; the DIAGNOSTIC half is OPEN, measured at 263 `findings.tsv` rows on ssg-md**

Tried: `PanamaFfiTransform.handleName` named a downcall handle off the frontend's MINT COUNTER, so interning one extra symbol ANYWHERE earlier shifted every later handle name — one unrelated conversion moved 122 members across 4 Panama types (deterministic, just not STABLE ACROSS COMMITS).
Fix (EMITTED half, CLOSED): key the disambiguator on WHAT JAVA OVERLOADS ON (erased signature, sorted, derived once) — a lone native gets no suffix, siblings get a stable ordinal that only renumbers when that overload set changes. 122->0.
Numbers/Rule: the DIAGNOSTIC half (a raw `@<SymId>` in finding/member-key ids via `Minter.external`) stayed OPEN — 263 id-stripped rows for a 4-line change (12 real), 538+2350 for a 5-line change (12+6 real), 12 on a pure additive scope-only wave; no engine-emitted identifier may key on a mint counter — fix belongs in `Minter.table`, NOT `TirPrinter` (this entry's earlier wrong prescription).
Triage (2026-09-04): FAMILY M: no engine-emitted identifier may key on a mint counter — fix belongs in `Minter.table`, not yet built (263 id-stripped diagnostic rows on ssg-md)

### M11. A commit that changes EMISSION and does not re-accept the baseline ships a digest ITS OWN CODE CANNOT REPRODUCE — and every lane still exits 0. **CLOSED, and the cost is that the next wave pays for it**

Tried: a commit changing EMISSION with no baseline re-accept ships a digest its own code cannot reproduce, invisibly — `af8273ca` touched no `baseline/*`, `just measure-all` exited 0 while carrying 2 stale member digests, a stale `port-map.tsv` row and a stale `findings.tsv` row whose re-hashed id made the COUNT not move.
Cause: `members.tsv` is a DIAGNOSTIC, not a gate — invisible to the one command a wave is required to run; a later unrelated baseline accept silently ABSORBS the stale rows forever.
Fix: settled via `git stash` + one lane run + compare; closed by re-accepting six lanes in a code-free commit. Rule: a commit touching `api/`/`engine/`/`frontend-spoon/`/`runtime/` either re-accepts a baseline or is measured to 0 on `just members-unchanged`.

## 8. A DEPENDENT reading its base's published port map

### D1. A TIR symbol's `fullName` is the SAME for every overload. **263 → 8, then CLOSED for policy**

Tried: `Symbol.fullName` for a member is `owner#name` with NO parameter list; arity-only overload selection against libGDX's map produced 263 Ashley findings, 118 `Ambiguous`.
Fix: `Symbol.descriptor` — a symbol carries its source-level parameter spelling as a SEPARATE field, resolved once by `PolicyBinder`; two latent cross-grammar divergences fixed alongside (array params, `equals(Object)` pre/post retype).
Numbers/Rule: 263 -> 8 residual (an EMITTED-namespace join kept arity-based deliberately); no-arity-match must mean NO record, and an xref recording a call TWICE must collapse to one — a fact about the TIR, not any library.

### D2. A dependent's program CONTAINS its base — filter every per-site report by ownership

(a) engine. Symptom: a dependent's `Program` CONTAINS its base, so an unfiltered per-site report attributes the BASE's own findings to the dependent — 255 of Ashley's 263 D1 findings were inside libGDX's own files; the SAME shape recurred across 6 artifacts (`OmissionCheck`, `PortabilityCheck`, port-map findings, collection-closure, `decisions.tsv` [a phase-log leak], and a REWRITING-side leak in `NullabilityTransform`, which retyped the BASE's own annotated declarations).
Cause: filtering must be STRUCTURAL (climb the `owner` chain to a base's map-named type, never a lexical origin-path comparison) — six independent copies of this climb existed before consolidation, all reporting-side, none rewriting-side.
Fix: `Surface.owns` (one climb, `api`), fuel-exhaustion counting as NOT owned (honest `Unknown`); `RunScope` (which units THIS run emits, which merged-phase keys THIS manifest contributed) for the rewriting side; the annotation-FQN key kind still needs a run-time screen (names no declaration, so the manifest-time `governs` screen can't see it) — a non-fatal `policy` finding, since the emission is already correct.
Numbers: libgdx-test published 1240 decisions of which 961 were libGDX core's (now withheld, not sectioned, with the withheld COUNT printed); Ashley 499->131, ashley-test 657->196, simple-graphs-test 93->23.
Rule: withhold cross-module rows, don't section them in the same file; every new per-site check must start from the run's own units, never scan `program.units` bare.
Triage (2026-09-04): FAMILY D: run-time screen for the annotation-FQN key kind — "the annotation-FQN key kind still needs a run-time screen (names no declaration, so the manifest-time governs screen can't see it) — a non-fatal policy finding"

### D3. A `<synthetic>` origin is not a file — exclude it from any source fingerprint — CLOSED
(a) engine. Symptom: `PortMap`'s staleness check digests the java files a map attributes members to; libGDX core's one `<synthetic>`-origin member put an unresolvable path in that file set, so the FIRST dependent run reported the base's map `Unverified` — a check's first real firing was a false positive.
Fix: exclude any `<...>`-bracketed origin from a source fingerprint, matching `SrcMap.relativise`'s existing rule.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: exclude any <...>-bracketed origin from a source fingerprint, matching SrcMap.relativise's existing rule"

### D4. `CtorFunnel`'s fixpoint is WHOLE-PROGRAM, and a dependent's program is a different one — 3 errors — CLOSED
(a) engine. Symptom: `CtorFunnel.Plans` decides a class's scala primary constructor at a WHOLE-PROGRAM fixpoint (withholding a paramful promotion wherever ANY subclass needs a nilary `extends`); a dependent's Program contains its base, so the fixpoint can reach a DIFFERENT answer for a BASE class than the base's own run did — gdx-gltf's 3 subclasses of libGDX's paramful `Attribute` (no shared parameter list) made its fixpoint withhold `Attribute`'s promotion, replaying a nilary prologue against the ACTUALLY-paramful base class: `E171`/`E134`, 3 errors, `ManifestAgreement` reporting 0 since nothing in the dependent's own run disagrees with itself. Confirmed as genuine DRIFT (not a bug) by reproducing the identical shape in one program with two different, both-correct answers.
Numbers: fixpoint fix closes cleanly; 2 of gltf's 3 errors are NOT closed and cannot be — two roots call two DIFFERENT base secondary constructors and a scala `extends` clause reaches only ONE primary (C3's wall shape, a counted refusal, not a plan).
Fix: port-map schema 3 carries `primary=`/`primaryKind=`/`primaryVis=` per type; `Surface` answers mine-vs-base structurally; the fixpoint now runs over the run's OWN classes only, reconciling a non-owned class against the published row — split by a provable property (`paramfulPrimary && needNilary && !reachableArgumentFree`: a class failing conjunct 1/3 is invariant under extra subclasses and any disagreement with the published row is a FATAL engine bug; a class satisfying both is legitimately a function of its own subclasses).
Triage (2026-09-04): CLOSED-IN-FACT — "the fixpoint fix closes cleanly"; residual 2 errors are C3's own counted-refusal wall, not this entry's

### D5. A REPLAY may not widen a `private` member the run does not EMIT — **CLOSED; gltf 7 -> 3 errors, `omissions` 3 -> 12**

Tried: `CtorFunnel.Plans.replayFor` expresses a super-call as the parent's statements REPLAYED after `this()`; within one module `widen` correctly drops `private`, ACROSS a module boundary the DECLARATION still says `private` in the base's already-emitted file — gdx-gltf: 4 errors.
Fix: read the published `vis=` via `reachablePrivate` — own class widens for real, base-published private (or unpublished) REFUSES+drops+records a non-fatal `Surface.Gap`. Two silent bugs fixed en route: `Surface.memberShape` must key by the OVERLOAD SET not bare `fullName`; every checker must build `Plans` WITH the run's surface.
Numbers/Rule: gltf 7->3; the `memberShape` fix alone was worth 272 false reports elsewhere; only the DEPENDENT can see this problem and only the BASE can fix it — do NOT retry a blanket refusal of cross-class widening.

### D6. An all-static class collapses to an `object`, and a CONSUMER is the one that names it as a TYPE — CLOSED
(a) engine, fixed; recorded because the shape recurs. Symptom: a java class whose every member is `static` still IS a type; the emitter collapses it to a bare `object`, guarded on nobody extending/instantiating it — a THIRD face is a TYPE POSITION with no `new`/`extends` (a `Class<T>` argument, a generic return inferred at `T = TheClass`); libGDX has 31 all-static classes and names none as a type, so 5 ports never saw it — gdx-gltf, which CONSUMES another library's constant-holder, cost 3 errors from one 8-line file.
Rule: a CROSS-MODULE fifth face has NO local repair — a base that collapsed a class and a dependent naming it as a type can be seen by NEITHER run; answered by port-map `form=` and `TirEmitter.surfaceGaps`, attributed to the base and non-fatal (measured 0 across the corpus today).
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: … libGDX moves 0 members, gdx-gltf's 5 errors close"; the cross-module fifth face is "measured 0 across the corpus today"

### D6.5. A drop and its INJECTION are in different namespaces, so nothing paired them — 10 false findings — CLOSED
(a) engine, fixed; §4.56's rule at a third artifact. Symptom: `PortMap.of` distinguishes `Dropped` from `Substituted` by comparing `dropTypes` (a manifest key, UPSTREAM namespace) against `injectedFqns` (files actually written, EMITTED namespace) — directly compared, the test is false for EVERY renaming port, so `Substituted` had never once been produced; libGDX's map carried `Dropped com.badlogic.gdx.utils.Json` beside an unrelated `Added sge.utils.Json` row with nothing joining them, invisible until gdx-gltf (the first port to reference an injected replacement) was told 10 times the base "emits nothing at that name" about a type it ships and compiles against.
Numbers: port-map findings 10 -> 0, no emitted text moved, every other check identical.
Rule: wherever a port artifact has a POLICY column and an OBSERVED column, check whether some predicate actually compares one to the other — both instances found so far were silent until a consumer acted on the wrong answer.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: port-map findings 10 -> 0, no emitted text moved, every other check identical"

### D7. An inherited drop leaves a CALL SITE the engine had no seam for — CLOSED; what remains is POLICY

Tried: a base drops a member the dependent still calls; the engine's only two seams are whole-declaration (`dropMethods`, `MethodBodyTransform`) — neither can say "keep this method, rewrite this ONE call." gdx-gltf: 1 error, diagnosed twice, no repair available.
Fix: `CallSiteSubstitutionTransform` — keyed like `dropMethods`, value an expression TEMPLATE spliced as TREES, default-off; `bindCallee` binds off the REFERENCE-side symbol (a dropped member has no declaration symbol); an entry placed after a re-pointing phase silently matches zero sites, now its own finding.
Numbers/Rule: dry-run on gdx-gltf's three `Json` sites — 3/3 bound, 0 findings — NOT enabled, since the port hasn't decided a replacement; what remains at every reached site is POLICY, not an engine gap.

### D8. A TYPE REDIRECT that only rewrites `TypeRepr` is a PARTIAL redirect — and its own contract said that was impossible

(a) engine — the mechanism was incomplete, not the policy; fixed. Symptom: `TypeRedirectTransform`'s doc promised totality but only rewrote `transformType`; a type reference reaches the emitted file THREE ways — a `TypeRepr` occurrence (rewritten), a static access `T.m()` (via an `Ident`'s SymId, or — for a PARSED type, the ordinary dependent case — the emitter re-qualifies from the member symbol's OWNER, ignoring the tree's qualifier), and an `@T` annotation (M5.8) — the latter two untouched, so the first library to redirect a type WITH static/annotation use hit 26 broken references across 10 types with every check reporting clean.
Fix: mint a TWIN symbol (same name/signature, owned by the target) for STATIC members only, re-pointing `Ident.sym`/`Select.sym`/`Apply.method` — re-pointing the ORIGINAL owner instead was measured WORSE (port-map 0->6, detaching the base's own members from their unit and breaking every D2 mine-vs-base filter); twinning: 6->0, 0 members moved.
Numbers: 26 references fixed on the surfacing library; a phase claiming totality needs a spec PER OCCURRENCE KIND with a negative half, since the positive spec passes even on a partial redirect.
Rule: a port that redirects a type it OWNS must ALSO drop it — the redirect only re-points references and never deletes the declaration; nothing enforces the pairing (a coherence property of the config), confirmed live: Stage P's redirect of libGDX's `Disposable` needed its paired drop or the orphan type ships beside 47 classes gaining it as a parent, at 0 errors and every check flat either way.
Triage (2026-09-04): FAMILY D: enforce the redirect+drop pairing as a config-coherence check — "nothing enforces the pairing (a coherence property of the config)"

## 9. Asserted, not measured

Reasoned, not observed — no corpus number stands behind an OPEN item here; a bullet marked CLOSED graduated by acquiring one.

- Duplicate injected-runtime definitions will break the Scala.js/Native linkers once a second module is ported — design reasoning only, unobserved.
- Enum constant FIELD drop CLOSED by observation (T8, noise4j, 4 errors); the initializer-block/nested-type halves remain reasoned, 0 sites across 4 libraries.
- An assignment used as a VALUE re-evaluates its LHS (`a[f(x)]=v` runs `f(x)` twice, `+=` thrice) — 7 pure-index sites in noise4j's `Grid`, unbuilt; simple form has a cheap exact fix, compound form needs the LHS decomposed. (a), unbuilt.
- A `StaticForwarderTransform` wrapper whose overloads aren't all receiver-first would rewrite wrongly (name-only matching) — safe under current policy, needs a guard when a second library configures it.
- Typo'd policy key silently no-ops — CLOSED: `PolicyReport` collects a `never matched` finding from every phase, baselined; caught a real dead key (`getName` in the `ClassReflection` forwarder, `policy 1->0` on removal).

### D9. A (b) phase configured in a BASE manifest is one no DEPENDENT may ever configure — and adding one to a base that has dependents cannot land. **P1 blocked, 2 ports fatal** — **CLOSED by M5m**

Tried: `PortManifest.extendedBy` unions most manifest rows key-by-key but concatenates `surface` (a `List[Phase]`) by IDENTITY — two instances of one parameterised phase never merge, so `ManifestAgreement` fatally reports `SurfaceDivergence`; folding the dependents' entries into the base's own table broke, since the base publishes ONE map and N dependents cannot all agree with it.
Fix: `MergeablePolicy` (api) — a phase declares HOW its policy composes with a nearer manifest's instance of itself; `PortManifest.surfaceFold` folds same-name phases through it at the BASE's pipeline position, so a base's own published digest stays byte-identical.
Numbers/Rule: 1 fatal `SurfaceDivergence` each on ashley/screens before -> 0 after; the question before writing base (b)-phase policy is "does any DEPENDENT already CONSTRUCT this phase" — new policy on an instance no dependent constructs is free.

### D10. `governs` IS A NAMESPACE, NOT A SET OF DECLARATIONS — and a TEST SOURCE SET is always inside its base's. **3 fatal findings on a key about the module's OWN member** — CLOSED
(a) engine. Symptom: the `governs` screen (§1.5's "no dependent key may edit what a base emits") was implemented as "is the subject inside the base's claimed NAMESPACE" rather than "does the base actually EMIT it" — a dependent's own TEST source set shares its base's package by construction, so a `dropMethods` key naming the dependent's OWN test class read as an `ExtraDrop` against the base: 3 fatal findings for one 3-key entry, with no way to comply.
Numbers: liqp test port `manifest` 3 -> 0, unchanged elsewhere, 0 member digests moved.
Rule: ask the POSITIVE question (does the base emit this), never the claim — the claim is a namespace, not a set of declarations.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: liqp test port manifest 3 -> 0, unchanged elsewhere, 0 member digests moved"

### D12. A DEPENDENT'S RETYPING PHASE REACHES ITS BASE'S DECLARATIONS, where it emits nothing and moves only what the run DERIVES — **1 FATAL `base-surface`, at 0 compile errors either side. CLOSED**

Tried: `TypeRedirectTransform` was the one retyping phase with no `RuleScope` — a dependent's Program contains its base, so redirecting a type inside a base declaration changes only what the run DERIVES; gdx-ai silently re-derived a wrong primary against the base's published one, both ports at 0 errors, only the port-map comparison caught it.
Fix: `TypeRedirectTransform(scopes)`, per-ENTRY `RuleScope` (forced by the merge, since a base's whole-program redirect and a dependent's package-scoped one merge into ONE phase instance); default `Everywhere(Set.empty)` is the pre-scope path.
Numbers/Rule: 1 FATAL `base-surface` closed at 0 errors either side; a dependent's COERCION must also read the base's published map for "did the base retype this formal".

### D13. A MERGED phase's REFUSAL is filtered out by the subject screen written for its KEYS — **`policy 0` beside eight compile errors, and the diagnosis a green sibling port had made unreachable**

(a) engine. Symptom: `PortRun` scopes a MERGED phase's findings to the subjects THIS manifest contributed — correct for a key, but a REFUSAL a phase files while RUNNING was dropped the same way, since every key on the phase was the BASE's: `sge-visui` had 8/32 errors from a base type-redirect's member-rename reaching all 5 implementors for re-parenting but NONE for the rename (a real name COLLISION with the widget toolkit's own `close()` handlers, correctly refused whole), and `policy` read 0 — the ONLY trace was a `ScopedOut` row in `decisions.tsv`, while two green sibling ports (no collider) gave false confidence.
Cause: `PolicyIssue` records what the engine could PROVE about a key, never WHOSE FACT a finding is — one derived from BINDING a key is about the key (the manifest), one filed while a phase RUNS is about this run's own program.
Fix: `PolicyFinding.About`, defaulting to `TheKey` (no behavior change for existing findings), subject screen asked only of that arm.
Numbers: `policy 0 -> 1` on sge-visui, 0 member digests. Do NOT retry: refusing the REDIRECT alongside a failed rename (spec'd against it — un-redirecting emits MORE errors); the residual seam (which member keeps the name) is per-library POLICY with no manifest spelling yet.
Triage (2026-09-04): FAMILY D: manifest spelling for the residual member-keeps-the-name seam — "the residual seam (which member keeps the name) is per-library POLICY with no manifest spelling yet"

### D11. A published map's `upstream` column was a DIRECTORY read as a PACKAGE — **9,261 of one base's 9,370 rows and 1,792 of another's, and the first dependent one of them ever had reported 459 fatal findings** — CLOSED
(a) engine, two closed sub-fixes. Symptom: `PortMap.upstreamOf` derived a member's upstream PACKAGE from its java file's DIRECTORY PATH — exact only while `sourceRoot` IS a package root, broken on a 53-module maven checkout (corrupting 9,261 of 9,370 rows, and 1,792 of gdx-vfx's two-module root) — invisible until the FIRST dependent's first run reported 459 fatal findings about types the base emits perfectly well.
Numbers: ssg-md dependent `manifest` 459 -> 1 (residual is a separate `BaseMapUnverified` fact), 0 member digests.
Fix: the declared package is always a SUFFIX of the path-derived one — truncate down to where it matches the already-known unrenamed name, guarded on that name having a QUALIFIED head (a bare-name `SrcMap` key, e.g. a promoted constructor parameter, trivially "matches" any suffix and must NOT truncate).
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: ssg-md dependent manifest 459 -> 1 …; Second, related bug CLOSED at wave 8 … 422/422 -> 0"

### D14. A dependent RE-DETECTS the base's bean/nullary pairs over the whole program, and `followMemberRenames` corrects what the re-detection refuses — **visui 7 -> 408 when scoping was tried without the reorder**

(b)/(a) engine ordering fix. Symptom: a dependent's `BeanPropertyTransform`/`NullaryArityTransform` detect over ALL `program.owned` symbols including the base's, so a guard passing in the base's smaller program can fail in the dependent's wider one (a base member's override component now includes a dependent override with a non-getter body: visui's `MimicActor.getWidth`, 157 errors; a setter with no matching getter, 3 errors).
Fix in place: `PortMapTransform.followMemberRenames` (apply the base's PUBLISHED renames after the phases run) corrects the remaining refusals.
Do NOT retry: scoping per-phase detection to `RunScope.emitsSymbol` alone — measured visui 7 -> 408 (the bean phase then can't see the base's pairs in the override component, and `followMemberRenames` can't propagate to already-processed dependent overrides).
Next step (open design): reorder so `followMemberRenames` runs BEFORE the bean/nullary phases, then scope detection to owned declarations. Current residue: `policy 0 -> 2` refusals on dependents.
Triage (2026-09-04): FAMILY D: reorder followMemberRenames before the bean/nullary phases — "Next step (open design): reorder … Current residue: policy 0 -> 2 refusals on dependents"

### D15. A dependent's locally-derived primary disagrees with the base's published one when retyping phases minted opaque types over base units — **base-surface 82 -> 22 (3 fatal -> 0, 79 unanswered -> 0). FOLLOWED (non-fatal)** — CLOSED
(a) universal, two bugs in the base-surface contract check. Bug1 symptom: `CtorFunnel.Plans.reconciled` compared a dependent's LOCALLY-DERIVED primary constructor descriptor against the base's PUBLISHED one and found a mismatch wherever the base's retyping phases minted an opaque type over base units (`GLTexture`: published `(int,T)`, dependent locally derived `(int,int)`) — FATAL, though the dependent doesn't emit these classes so the disagreement never reaches emitted text.
Numbers: `base-surface` 82 -> 22 (3 fatal -> 0, 79 unanswered -> 0), 136 collapse verdicts, 0 disagreeing; 0 of 16 real test-port compile errors were at any affected class.
Numbers (2026-09-05, bug2 — ONE derivation): the published slot and the local one both spelled a parameter from `Symbol.name`, so a value class the frontend interned from java (`boolean`) and one a phase MINTED (`Boolean`) at the same `fullName` gave two answers, decided by an unordered `symbols.all.find` that flipped when the lls jar enlarged the table; `Descriptor.paramOfType` now reads `ValueClassPrimitives` off the `fullName` and is the single derivation both sides call. `base-surface` 20->19 ai, 21->20 visui, 20->19 ashley (22->21 ashley-test), 21->20 screens, 20->19 anim8 (the `DistanceFieldFont` row; gltf 20 and vfx 19 never carried it), gdx `port-map` ONE `primary=` row respelled, every other check and `members-unchanged` 0 flat.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: base-surface 82 -> 22 (3 fatal -> 0, 79 unanswered -> 0), 136 collapse verdicts, 0 disagreeing"

### D16. A type renamed by `typeRenames` was published under the POST-rename simple name, not java's — **every consumer that joins the map to the pre-rename program missed it. CLOSED**

Tried: `PortMap.of` derived `upstream` by inverting the PACKAGE rename table alone, so a `typeRenames` entry recovered only the renamed tail — every reader joining the map to the pre-rename program missed the row; a type both dropped AND phantom-emitted also produced TWO rows sharing one key (35 pairs on libGDX).
Fix: pass the FULL rename table to `PortMap.of`; `unrename` inverts by LONGEST VALUE MATCH; filter `typeEntries` against `dropTypes` by UPSTREAM name.
Two amendments, both FATAL bugs the fix exposed: a `Substituted` entry lost its `shape=` payload (8 ports "DID NOT RUN"); `emittedByBase` indexed by UPSTREAM name only, so a type-renamed type was FATAL on ashley — both fixed by reading the right namespace.

## 9.5 Control flow — what a `break` really leaves, and the boundary that steals it

### F1. A java LABEL sits on ANY statement, not only a loop. **55 → 10 residues**

(a) engine/frontend-spoon, universal. Symptom: java's `LabeledStatement` (JLS 14.7) can sit on ANY statement (an `if`, a bare block, a `switch`), not only a loop; a loop-only label encoding cannot express most labelled breaks — 45 of 55 untranslated jumps on libGDX core were labelled breaks to a NON-loop, silently dropped: `JsonReader` produced a spurious duplicate parse event for every unquoted bool/null/number until fixed, verified against javac's own build via a differential event probe.
Fix: `Tree.Labeled(name, stmt)` — a WRAPPER minted only for a labelled NON-loop statement (a loop keeps its label in its own node, since it is ALSO `continue L`'s target and the two boundaries go in different places); emission is a NAMED `scala.util.boundary` around the statement, omitted when nothing breaks to it.
Numbers: 55 -> 10 residues (the remaining 10 are F3's different shape).
Rule: if a residue shows for a labelled jump, the frontend node exists — check it's being minted, don't invent a new mechanism.
Triage (2026-09-04): SUPERSEDED — F3 — "55 -> 10 residues (the remaining 10 are F3's different shape)"

### F2. A `boundary` the emitter INTERPOSES steals the enclosing loop's un-annotated jumps — CLOSED
(a) engine, universal — the hazard F1 creates. Symptom: `scala.util.boundary.break(())` with no `using` resolves the INNERMOST given `Label` — the moment ANY construct interposes a new boundary between a loop and an UNLABELLED `break`/`continue` under it, that jump silently retargets and the outer loop just runs on; naming the inner boundary's OWN label does not help, that IS "innermost given".
Rule: any lowering introducing a scoped, implicitly-resolved capability must re-examine every use that was resolving to an outer one.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: TirEmitter.interposes … loopWithJumps NAMES the enclosing loop's boundary whenever it does" — mechanism shipped

### F3. An unlabelled `break` in the MIDDLE of a case ends the CASE. **10 → 0 residues** — CLOSED
(a), universal. Symptom: the frontend already deletes a case-TERMINATING `break` and lowers real fallthrough by TAIL DUPLICATION — so an unlabelled `break` still standing in the MIDDLE of a case body means "stop HERE", and code that ran on past it belongs to a DIFFERENT case; `GlyphLayout`'s colour-tag parser fell through into an unrelated `continue outer` and re-scanned the run, green compile, no count moved.
Numbers: 10 -> 0 residues.
Fix: scala's `match` cannot leave an arm early, so the arm gets its own NAMED `boundary` (F2's reason).
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 10 -> 0 residues"

### F4. A translated CATCH swallows a translated JUMP — `boundary.Break` is a `RuntimeException` — CLOSED
(a) engine, universal. Symptom: `scala.util.boundary.Break[T] extends RuntimeException(null,null,false,false)` (deliberately not a `ControlThrowable`), so `NonFatal` matches it — a translated `break`/`continue` inside a `try` whose boundary is OUTSIDE it is silently CAUGHT by any sufficiently-broad catch: the loop runs on and the handler body runs for a condition java never had; dotty's `DropBreaks` optimiser does NOT save this (`prepareForTry` shadows every enclosing label under a `try`) — deterministic, not a race.
Numbers: 0 on all lanes today — the corpus's one real instance (`Json#writeFields`) sits inside a type libGDX DROPS, invisible to every count (only one port-map digest moved); liqp's 19 broad catches genuinely lack the shape (early exits are `return`s, jumps sit outside any `try`). Reference hand port ssg shipped a related swallow to production.
Rule: a defect class is worth closing at a corpus count of zero — the evidence for such a repair is a SPEC that EXECUTES the swallow, never a port.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 0 on all lanes today"

### F5. TRY-WITH-RESOURCES was dropped WHOLE — the frontend modelled it and the emitter never printed it — CLOSED
(a) engine, universal. Symptom: `Tree.Try.resources` was populated correctly by the frontend and carried through every phase, even printed in the TIR debug view — but `TirEmitter.tryStr` computed the resource text and NEVER interpolated it; a resource opened for its side effect alone compiled perfectly with the lock never acquired or released — no error, no count, nothing in the emitted file to say a java statement was ever there.
Numbers: liqp 0->0 errors, 0 digests moved anywhere — no corpus library uses the construct; the gate is entirely a behavioural SPEC that executes it.
Rule: SE9's bare-reference resource form was ALSO silently dropped by a `collect{case lv: CtLocalVariable}` frontend read — now REFUSED LOUDLY (M6) rather than mistranslated.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: liqp 0->0 errors, 0 digests moved anywhere — no corpus library uses the construct"

### F5.1 The SAME defect one node over — `Tree.CaseDef.guard` was carried by every phase and never rendered. **CLOSED, 0 blast, found by an INSTRUMENT rather than by a port**

Tried: F5's own lesson recurred one node over — `Tree.CaseDef.guard` was carried through every phase, but `TirEmitter.matchStr` never read it; dropping a guard WIDENS the arm, and no corpus site mints one, so nothing could ever have reported it.
Found by `EmissionFieldCoverageSpec` — an instrument perturbing every field of every `Tree` node, deriving both its node-kind and field enumerations rather than listing either.
Numbers/Rule: byte-identical on all 15 ports; what generalises is the INSTRUMENT, not the specific field.

### F6. A NULL selector must NPE — the fall-out arm's own defect, read at the other value — CLOSED
Tried: java throws `NullPointerException` the instant a reference-typed `switch` sees a null selector, IMPLICITLY; scala's `match` special-cases nothing and falls through to the fall-out arm — the SAME mechanism as the fall-out-arm defect read at the opposite selector value (adding that arm to fix "unmatched throws MatchError" is what created THIS silent no-op).
Numbers: libGDX 55 member digests moved, errors 0->0 — 55 switches whose null path was a silent no-op, `JsonValue` alone carrying 19.
Rule: universal, counted as `switch-null` from the trees against the emitter's guarded set.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: libGDX 55 member digests moved, errors 0->0"

### F7. A compound assignment evaluates its LVALUE ONCE; the emitted form evaluates it TWICE — CLOSED

Tried: JLS 15.26.2/15.14.2 evaluate a compound assignment's lvalue subexpressions ONCE, storing back through that SAME reference — every emitter arm translated the lvalue and then USED THE TRANSLATION TWICE (three times in expression position). The defect was in the ENGINE and had not fired in the corpus — both halves were the finding.
Fix: `TirEmitter.termArm`'s `Tree.Assign`/`Tree.IncDec` arms bind each non-trivial lvalue subexpression to a `val` once; simple lvalues (ident/`this`/literal) keep the direct form, no digest churn.
Rule: java's evaluation order is not a per-library question — universal, catalog `JS-E17`.

### F8. The compound-assignment NARROWING was written once, for the STATEMENT arm; the EXPRESSION arm twelve lines away kept the defect — **CLOSED, at 0 moved member digests over fifteen ports**

Tried: `b += 3` on a `byte` needs java's implicit narrowing cast back (JLS 15.26.2) — the STATEMENT arm applied it, its EXPRESSION twin did NOT, silently storing an `Int` into a `Byte` slot — LOUD, yet unreachable by every measurement this project has.
Found by `catalog(undischarged)`, proven only by `CatalogAreaESpec` since the corpus cannot exercise it.
Fix: the predicate is now ONE function both dispatches call — two copies were the same defect with a longer fuse.

## 10. Comments (trivia) — what still does not survive, with its number

Governing rule CLAUDE.md §4.58; this section is the residue only. Carrying comments costs +33.8%/+51.1%/+38.9% emitted bytes on libGDX/Ashley/simple-graphs core (smaller on test sets) — a third of a well-documented library's emitted text is its documentation. 0 compile errors, every other check flat, determinism green; srcmap unit/member counts identical (only member DIGESTS moved, exactly the members that gained a comment).

### F9. A `return` inside an enhanced-`for` body is a NON-LOCAL RETURN once the loop is a `foreach` lambda — CLOSED (catalog `JS-S26`)

Tried: `for (T x : xs) { ...; return x; }` emitted as a `for`-comprehension desugars to `xs.foreach(...)`, turning java's `return` into scala's NON-LOCAL return.
Fix: where `returnsIn(body)` finds a `return`, lower to java's own `while(it.hasNext){...}` shape, deciding ARITY from the callee symbol's declaration — the previous heuristic (`program.owns(receiverType)`) was wrong both directions.
Numbers: simple-graphs 6, ssg-md 7 errors closed; 58 gdx port-map rows moved.

### F10. A LOSSY WIDENING PRIMITIVE CONVERSION (`int→float`, `long→float`, `long→double`) relies on Scala's deprecated implicit conversion — CLOSED

Tried: java widens `int->float`/`long->float`/`long->double` implicitly (JLS 5.1.2); scala 2.13.1 deprecated the matching implicits, an error under `-Werror`.
Fix: a new `SpoonTir.coerce` branch detects the three lossy pairs and emits explicit `.toFloat`/`.toDouble`.
Numbers: 253 sites on gdx (247+3+3), suppressed by K13's `@nowarn(deprecated)`.

### V1. A comment the FRONTEND claimed and dropped, and one the EMISSION consumes. **222 → 100 → 0 lost** — CLOSED
(a) engine, universal. Symptom: `TriviaCheck` compares java text to emitted text on every run; libGDX core reported 222 dropped comments, falling to 100 after one fix — but the entry's own framing of the remaining 100 (by EMISSION-side context) was WRONG about where the loss happened: every traced site died on ONE frontend line — the statement fold accumulated comment-statements into a pending buffer, folded them onto the NEXT statement, and DISCARDED them when there was none (claim-then-drop, unrecoverable by any coarser harvest).
Numbers: libGDX core 222 -> 100 -> 65 -> 18 -> 0 lost; corpus total 233 -> 198 -> 77 -> 0 lost.
Rule: reading a residue's CATEGORY names as a diagnosis is itself a trap; `recovered` is a residue to keep whittling down, never a success to report.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: libGDX core 222 -> 100 -> 65 -> 18 -> 0 lost; corpus total 233 -> 198 -> 77 -> 0 lost"

### V2. `TirPrinter.canonical` must NOT carry trivia, and `TirPrinter.digest` MUST — CLOSED
(a) engine. Symptom: two TIR text renderers have OPPOSITE trivia requirements — a phase-boundary debug dump must NOT carry comments (would bury the nodes a phase actually moved; no phase reads a comment), while the ACTION CACHE's key MUST include them (keying on the comment-free form would cache-HIT a source edit that changed only a comment, silently re-serving stale text, surviving even a `clean`).
Fix: `TirPrinter.canonical`/`Style.canonical` elides trivia; `TirPrinter.digest`/`Style.identity` renders canonical+trivia — everything that reaches the emitted file and nothing that doesn't; `TirCacheKey` keys on `digest`.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: TirPrinter.canonical/Style.canonical elides trivia … TirCacheKey keys on digest" — mechanism shipped


### V3. Spoon attaches only ONE of several consecutive FILE-LEADING comment blocks — 9+ sites — CLOSED
(a) engine/frontend-spoon. Symptom: when a java file opens with two consecutive block comments, `CtCompilationUnit.getComments` carries the first and `CtPackageDeclaration.getComments` (never read by `SpoonTir`) carries the second.
Numbers: libGDX core trivia 100 -> 65, gdx-vfx 11 -> 9, 30 whole-file digests moved. Rule: reading one more parser attachment slot is NOT the fix — no set of slots can say which of two blocks came first.
Fix: harvest is now POSITIONAL (`CommentScanner.firstCodeOffset`: a comment is the file's iff no code precedes it), parser-attached comments merged in by OFFSET, header CLAIMS its spans so nothing double-emits.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: libGDX core trivia 100 -> 65, gdx-vfx 11 -> 9, 30 whole-file digests moved"

## 11. Literals and the emitted file's LEXICAL correctness

The emitter's output is TEXT; two facts about Scala's lexer decide whether that text is a file at all, invisible to any check until the compiler fails at a position unrelated to the actual construct — found via anim8-gdx, the first corpus library whose difficulty is per-LINE rather than per-file (16 files, 19,594 lines; `ConstantData` alone is 108 lines holding four ISO-8859-1 string literals of 47,935 and three 6,390 characters).

### L1. A literal's VALUE must be RE-ESCAPED — **1,334 errors from ONE file** — CLOSED
(a) engine, built. Symptom: `Constant.StringC` holds DECODED text; the emitter only escaped 5 characters and passed everything else raw — a raw NEWLINE ends the literal (1,334 of anim8's 1,383 first-run errors, mis-attributed to unrelated lines), a lone SURROGATE would silently change VALUE on UTF-8 write-out with NO error at all.
Numbers: found via anim8-gdx's `ConstantData` (108 lines, 47,935- and 6,390-character ISO-8859-1 literals); libGDX has 4 affected files/11 members but only with chars dotty happens to tolerate — "a corpus that has not met a construct is not evidence it's handled."
Rule: `EmitterLiteralSpec`'s strongest assertion is that NO raw control character appears anywhere in emitted source.
Triage (2026-09-04): CLOSED-IN-FACT — "Rule: EmitterLiteralSpec's strongest assertion is that NO raw control character appears anywhere in emitted source" — pinned

### L2. A prefix operator and its operand are TWO tokens — **48 errors in one method** — CLOSED
(a) engine, built. Symptom: scala's lexer takes a maximal run of operator characters as ONE token, so a prefix `-` against an operand already rendering with a leading `-` (a negative hex `long` literal) produces `--`, a different token — anim8's `analyzeOverboard` did this 14 times for 48 `E040` errors.
Fix: PARENTHESISE the operand (the only fix that cannot mis-lex; a separating space was rejected on inspection, since `- -4L` parses as infix waiting for a left operand); the test is on the two CHARACTERS that would meet, never the operator's name.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: PARENTHESISE the operand (the only fix that cannot mis-lex)" — shipped

### L3. A CLASS LITERAL needs a CLASS — an all-static class named by one must not collapse — CLOSED
(a) engine, built. Symptom: an all-static class collapses to an `object`, but a `classOf` reference to it needs a CLASS (an object's only type is `X.type`) — java's log-tag idiom (`TAG = VfxGLUtils.class.getSimpleName()`, inside the very class it names) hit `Expected a type, but found a term`; `classOf[VfxGLUtils.type]` is a TRAP — compiles, but returns `"VfxGLUtils$"`, a different string than java, green compile (CLAUDE.md §3).
Numbers: 0 members moved on any other corpus port.
Fix: `classOf` is a THIRD construct (beside `extends`/`new`) that withholds the collapse, via `TirEmitter.typeNamedElsewhere` (also D6's guard) — a lazy per-symbol scan, one SET per construct.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 0 members moved on any other corpus port"

### L4. A Scala KEYWORD as a PACKAGE SEGMENT — the emitter escaped IDENTIFIERS and never PATHS — CLOSED
(a) engine, universal, built. Symptom: java's reserved words are not scala's (`type`, `object`, `val`, `given`, ...), so a legal java package segment (`com.fasterxml.jackson.core.type.TypeReference`) is unparseable scala — 3 errors on liqp, NONE naming the actual cause.
Numbers: 5 of 6 corpus libraries had no keyword segment anywhere, which is why this survived to the sixth.
Fix: escape PER SEGMENT, cutting only at §4.56's three separators (`.`,`$`,`#`); gated by a spec on the EMISSION (nothing but scalac can count a syntax error at first occurrence).
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 5 of 6 corpus libraries had no keyword segment anywhere, which is why this survived to the sixth" — fix gated by spec

## 12. Threading a CONTEXT through a program

Turning a static holder into a `using` parameter is whole-program reachability, and three of its edges are not call-graph facts (`DESIGN.md` §8.4). Entries prefixed `CT` were originally minted `X1`-`X4`, colliding with §6's `X1`-`X5` — an entry whose id may move carries its own "Title, for renumbering" line.

### L5. A MULTI-CATCH's union type needs PARENTHESES, and a `catalog(consulted)` row cannot see that — **2 errors + 2 cascades, 175 → 171. CLOSED**

Tried: `catch (A | B e)` has had a correct TIR lowering since multi-catch was modelled, and catalog row `JS-S14` read `Handled` for the construct's whole life — but the EMITTED TEXT did not parse: `case e: A | B =>` parses as a PATTERN ALTERNATIVE (may not bind a variable), not a typed pattern at a union.
Fix: parenthesise — `case e: (A | B) =>` — a grammar fact, not a type fact; narrowed to only union catch types. Numbers: 2 errors + 2 cascades, 175 -> 171; 5 corpus libraries wrote no multi-catch.
Rule: a `catalog(consulted)` row proves the LOWERING fired, never that the EMITTER rendered what it built.

### CT1. An anonymous body's LEXICAL HOME is not in the owner chain — the capture lands on the CLASS — CLOSED
(a) engine. Symptom: the frontend interns an anonymous class with its ENCLOSING CLASS as owner (for its emitted name); a pass finding "the declaration this body was written inside" by climbing `Symbol.owner` reaches the CLASS and loses the METHOD — wrongly landing a capture as a class-level global read, and wrongly freezing an anonymous `Runnable#run` as an ordinary method anchored to its external interface.
Rule: same one level down — a MEMBER of such a body must be reached from the member, looking UP one level, not from a bare symbol climb.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: the xref already answers it … do not build a private 'where am I' traversal" — mechanism identified and used, nothing left to build

### CT2. A `lazy val` cannot receive a context — the deferred static is a CACHE PAIR, not a `lazy val` — CLOSED
(b) configure, asserted (a language fact, not a measurement). Symptom: a class initialiser reading a static holder cannot be threaded (no signature) and cannot become a `lazy val` (its initialiser has no parameter list — the exact problem being solved); a null-sentinel cache also fails, since a primitive-typed static legitimately holds its own zero.
Fix: `private var f$set: Boolean` / `private var f$value: T` plus a `def f(using T): T` reusing the FIELD'S OWN SYMBOL (no call site changes) — a CACHE PAIR, not a `lazy val`; per-site opt-in (`sites { "...#<clinit>" = "lazy-init" }`), since it does NOT reproduce the JVM's class-init lock.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: private var f\$set … a CACHE PAIR, not a lazy val" — mechanism shipped, per-site opt-in

### CT3. An anonymous `(using T)` clause is an EMITTER capability, not a phase one — CLOSED
(a) engine. Symptom: every parameter the emitter rendered was `name: Type`, so an anonymous `(using T)` required MINTING a name — measured to actively break things: a context parameter named after an emitted root package shadows it and breaks every fully-qualified reference in scope.
Fix: a `using` parameter symbol with an EMPTY NAME (otherwise impossible) renders anonymously — an EMITTER capability, not something a phase can express by minting a name.
Triage (2026-09-04): CLOSED-IN-FACT — "Fix: a using parameter symbol with an EMPTY NAME … renders anonymously" — mechanism shipped

### CT4. A CONSTRUCTOR could not carry a `using` clause — **CLOSED; 5 errors → 0, and the fix is one distinction**

Tried: adding a `(using T)` clause to a class's constructors is the reference hand port's own shape; the TIR edit was correct but EMISSION failed at 5 errors from ONE cause appearing 3 ways — a constructor gaining a parameter was no longer nilary so a SYNTHETIC nilary primary was emitted beside it; where it DID promote, the list was rebuilt FLAT, dropping the `given` grouping; a subclass then saw an ambiguous overload.
Fix: all three are `paramss.flatten` — the funnel's plan now models the split (`Plan.givens` beside `primaryParams`), read through `CtorFunnel.valueParams` everywhere. Numbers: 5 errors -> 0, validated by RUNNING a compiled fixture; 578 clauses emitted, 0 flattened, 0 synthesised empty primaries.
Rule: those "0" figures cover only classes the funnel PROMOTES or SYNTHESISES — CT5 is the separate `Plan.none` gap.

### CT5. A class the funnel neither PROMOTES nor SYNTHESISES has nowhere to put the context clause — **CLOSED; 57 errors → 3, and the primary hosts the clause and nothing else**

Tried: CT4 closed the clause for classes the funnel BUILDS a primary for; the THIRD outcome (`Plan.none`) has no primary to append to, so a threaded class's body had no given in scope — 57 errors from 188 threaded classes, plus a silently-uncounted shape losing its threading entirely.
Fix: `Plan.none` gains exactly `Plan.givens` (read off the class's own constructors) — the primary HOSTS the clause and delegates NOTHING; clause-conditional, byte-identical where no clause exists. Numbers: 57 -> 3 errors (unrelated); the silent-loss shape is now COUNTED (`context-seam`'s `lost-clause`).
Rule: do not work around a funnel gap in the threading phase; three shapes (object, trait, enum) cannot host a clause at all and are negative-tested.

### CT6. The INSTANTIATE edge does not exist for a GENERIC class, nor for a `C::new`, and the `sites` exit the seam NAMES does not exist either — **CLOSED; 3 errors → 1, and all three faces are read off the NODE**

Tried: three faces, invisible to every count until compiled — Face A: `Xref` relabels a generic `new G<...>()` as `Tycon`, missing the INSTANTIATE edge; Face B: the `sites`/`lazy-init` exit can only name a READ, but a field initialiser CONSTRUCTING a threaded type reads no holder; Face C: a constructor METHOD REFERENCE (`Type::new`) is recorded at a shared `TypeTree` node Face A's fix cannot reach.
Fix: all three now read the NODE directly — Face A asks does a `Tree.New` CONSTRUCT `c`; Face B widens `deferrals` to "reads a holder OR constructs a threaded type", licensed only because the site must be NAMED; Face C reads the `MethodRef` node's own recorded constructor symbol.
Numbers: P5 replay 3 -> 1 error (unrelated residual); Face C population 232 `::new` sites, most byte-identical, one moved `context-seam` 44->45 at 0 moved members (a BARE TYPER ERROR nothing pointed at before). Rule: a phase may only conclude something about a type from what it reads AT THE NODE, never a shared index's recorded kind.

### CT7. A class a FRAMEWORK instantiates cannot host the clause, and nothing can put a `given` in generated code — **CLOSED; the numbers below are what it cost, and the fix is a THIRD ANSWER plus the warning that finds it**

Tried: after CT5+CT6, the P5 delivery reached 0 scalac errors — and the base's own SUITE silently lost 5 tests, visible ONLY to `tests.tsv`: MUnit instantiates a suite REFLECTIVELY, which cannot be handed a `using` clause (true of every JUnit suite/`ServiceLoader` impl too); no existing manifest key reaches it.
Fix: the reference hand port's own shape — a NO-ARG constructor plus the context as a `private given` MEMBER — so `ContextHolder.selfSupplied` names a type taking the context WITHOUT a parameter; a new WARNING `unconstructed-thread` flags candidates rather than refusing (cannot tell framework- from user-instantiated).
Numbers: the SHIPPED criterion fires at exactly 1 real hit that generalizes correctly; two looser criteria (60, 74 of 188) were REJECTED as unusable. Rule: which declarations are framework-instantiated is NOT DERIVABLE from the program alone; `tests.tsv`'s DID-NOT-RUN gate remains the only detector.

### CT8. A DEPENDENT cannot declare a `sites` policy for its OWN types — the holder is inherited and the phase is not `MergeablePolicy` — **CLOSED; the per-declaration half is what a dependent adds, and the shared half is what it may not restate**

Tried: the phase's policy lives in the BASE manifest per §1.5, correctly — but the phase was NOT `MergeablePolicy`, so a dependent constructing its own instance got a fatal `SurfaceDivergence`; yet a dependent's OWN boundaries are in its OWN types, which the base cannot govern — the diagnostic's own suggested exit names a manifest a dependent cannot write in.
Fix: `GlobalsToImplicitsTransform extends MergeablePolicy`; `ContextHolderExtension` is a NEW value (holder + sites + selfSupplied, no field able to restate the shared holder) — merge splits by failure mode: shared fields AGREE-OR-REFUSE, per-type fields UNION, per-declaration fields UNION.
Rule: a shared-surface field cannot be restated in a per-declaration extension; a dependent's `sites` key naming a BASE declaration is still fatal `SurfaceIntrusion`. Non-finding: gdx-gltf's 7 pre-existing errors were byte-identical to its reverted baseline.
## 13. Retyping a PRIMITIVE to an opaque domain type

All five entries below (O1-O5) come from Stage P6's attempt to enable an opaque family (`TextureHandle`) on libGDX core (`PROGRESS.md` §11.25); O1 and O2 are two halves of "a retyping phase owes more than the declaration it was pointed at", O3 is a family the spec cannot ask for at all, O5 blocked the whole family (a MINTED unit the run emitted from every module in the pipeline, found only by compiling the DEPENDENTS, invisible to the base's 21 green check counts). O1, O2, O5 CLOSED; O3, O4 remain named residues.

Numbers: O1+O2 together were 6 scalac errors (3 each) on an otherwise-complete, correct delivery (matches the reference hand port exactly, all 21 check counts unchanged); re-applying the `OpaqueSpec` verbatim after all fixes: 6 -> 0 errors, coercions 27->30, members 34->37 (each accounted precisely). The family is now SHIPPED (`LibgdxPolicy.mainPhases`, all 13 ports green, byte-identical headlines) — every number reproduced exactly on the full 13-port delivery run, which is why O5 (the dependent-only defect) was worth reading before any of the base-only fixes.

### CT9. A dependent whose OWN declarations sit inside a base's CLAIMED namespace cannot name one — and a REFUSED merge silently runs only ONE of the two instances — **CLOSED, both faces; the screen now asks what the base EMITS, and a refused pair stops the run**

Tried: two invisible-to-every-count bugs surfaced once a real dependent (libgdx-test) was measured — Face A: the `governs` screen refused a dependent's `selfSupplied` key for ITS OWN test suite because the FQN falls inside the base's claimed namespace, though the base never emits or parses that file; Face B: a REFUSED merge (`SurfaceDivergence`) didn't stop two same-name phase instances existing — `Pipeline.order` keys by NAME, so the LATER instance silently replaced the base's, running the dependent's own empty holder alone with NO error and 0 `decisions.tsv` rows for the vanished policy.
Fix A: the screen now asks what the base's PUBLISHED PORT MAP actually EMITS, not what its manifest DROPS. Fix B: a refused merge now STOPS THE RUN at a fatal gate before any phase executes; needed ordering INSTANCES not names, plus a third answer (DEDUP) for an EQUAL-fingerprint pair, since Pipeline had been running a truly-equal pair TWICE with nothing able to see it.
Numbers: all 13 ports 0 members changed, every check identical for both fixes; also fixed alongside — `PortManifest.fingerprint` was NAME-ONLY for a non-`SurfacePolicy` phase.

### CT10. The `java.lang.Enum` ANCHOR is a real over-refusal, and lifting it is measured **32 → 41 errors** — it was MASKING the enum-clause gap, not causing it — CLOSED
(a) engine. Symptom: `ExternalSurface.jdkPlatform`'s `java.lang.Enum` entry over-refuses (JLS 8.1.4 fixes its surface forever, since no class but the compiler's own `enum` desugaring may extend it) — lifting it was HYPOTHESIZED to close 6 of 11 `E172`s on sge-visui; MEASURED to make things 9 errors WORSE (32->41): the refusals just moved further along the same seams, because an enum's companion static CAN take the clause but its INSTANCE methods route through the enum's CONSTRUCTOR (cannot carry one), and the wall behind THAT is `toString()` overriding `java.lang.Object#toString` (can never take a clause at all).
Fix (on remeasure): the true bug was different — 5 of 6 members are `private static`, and JLS 8.2 says a PRIVATE member is NOT INHERITED at all (no override relation exists to declare against), so the anchor's unknown-is-yes logic was answering a question with no meaning for it; fixed by reading the MODIFIER (private skips the anchor entirely; `static` alone still anchors, since it hides rather than overrides). Numbers: with the modifier fix, lift alone measured 17->27 (matching the predicted shape), then 27->12 once the uncovered enums got a `selfSupplied` entry; `context-seam` ended at 39, its PRE-lift value.
Triage (2026-09-04): CLOSED-IN-FACT — "context-seam ended at 39, its PRE-lift value" — private-member anchor bug fixed, net neutral on the counter

### CT11. A static field whose initialiser constructs a threaded class becomes a holder with a throwing accessor — **CLOSED; visui 7 -> 3 (BLOCKER), gdx 0, context-seam flat**

(a) engine, DERIVED. `private static final Actor BLOCKER = new Actor()` beside `static { BLOCKER.addListener(...); }`: the field becomes `private var BLOCKER$holder` + a throwing `def BLOCKER`, and the initialiser plus the clinit body that reads it move into every threaded static method behind an `eq null` guard (JLS step-9 sequence). `ContextNeed.discoverFieldHolders` runs after the first growth fixpoint, seeds readers of held fields, and re-grows. No manifest key: the accessor keeps the field's name. A class with no threaded static method refuses and counts as `unsuppliable-use`.

### CT12. Class-to-trait: the nominated type is INJECTED, not derived, because trait-init order differs from class-init order — **CLOSED; gdx 0 -> 20 -> 0, gdx-test 180/11, ashley 108/2/2, drop-in 408 -> 32/7**

Tried: `ClassToTraitTransform` ((b), per-library WHICH types/mappings) rewrites a nominated abstract class into a trait — DERIVING the trait naively fails because a scala trait's field initialisers run in SUBCLASS LINEARISATION order, not declaration order.
Fix: the trait is INJECTED by hand (matching sge's shape, field order fixed) rather than derived; the phase transforms the nominated type's TIR so `CtorFunnel` sees a parent with no constructor; mapped `ValDef`s removed from the TIR; a WIDEST-PRIMARY plan rewrites narrower constructor calls into `this(...)` delegations targeting the widest one.
Numbers: gdx `ClassToTraitTransform` itself 0->20->10->4->3->1->0 across 6 fixes; final gdx-test 180/11 baseline, ashley 108/2/2 baseline, ecs-dropin 408->32/7. Rule: 2 residual `E198` rows are COUNTED not fixed; a later widening over-fired on every nilary-delegating class and had to be narrowed.

### CT13. `stripSuperArgs` dropped the Block's `expr` — a promoted ctor's field assignment vanished at 0 compile errors — CLOSED

(a) engine, in `ClassToTraitTransform.stripSuperArgs`. `stripSuperArgs` reconstructs the constructor body from `CtorFunnel.stmtsOf(d)`, which returns only `Block.stats` (not the `expr`). For a two-statement constructor `{ super(args); this.effect = effect; }` parsed as `Block(stats=[super, assign], expr=Unit)`, the rebuilt Block placed the assignment in `expr` and the Unit literal vanished. `CtorFunnel.stmtsOf` then read only `stats=[super()]`, the primary body was empty, and the field stayed at `uninitialized` — read as null at run time, at 0 compile errors. Fix: preserve the original Block's `expr` so that all statements stay in `stats`. gdx `.ref` 5 -> 3 (two unset-field warnings gone).

### O1. A coercion reads the boundary TERM's own type, so a seed reaching it through an `if` is INVISIBLE — was 3 errors — CLOSED
(a) engine, in `PrimitiveToOpaqueTransform`'s coercion. Symptom: the phase retypes seed REFERENCES for consistent boundary detection, but every coercion test is exact only for a BARE reference and blind to a COMPOSITE term carrying one — a ternary's `Apply` branch is correctly retyped but the enclosing `Tree.If` is not (nothing retypes a composite node from its branches), so `+`/assignment sees a type mismatch: 3 errors on libGDX core.
Numbers: 3 errors -> 0. Rule: which node kinds are "carriers" is ENUMERATED and a missed one is a compile error, never a silent unwrap (`Try`, `Lambda`, an anonymous-class body still uncovered).
Fix: `carriesOpaque` asks the SEED TABLE about the declaration a value flows from and descends compound-but-not-a-move shapes (`if`, a block's tail, a `match` arm, `Commented`); `coerce` rewrites each LEAF branch, not the whole composite — settled against the REFERENCE PORT's own shape (§3.5), correct for a MIXED carrier where one whole-node coercion has no type to target.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 3 errors -> 0"

### O2. A retyped PARAMETER leaves its METHOD's signature stale — and the ctor funnel reads the signature — was 3 errors — CLOSED
(a) engine, in the phase's retype loop. Symptom: the retype loop rewrote a seed VALUE symbol's info and a seed METHOD's RETURN, but never a method's PARAMETER types in its `MethodType` — a seeded PARAMETER carried the opaque type on its own `ValDef` while the signature still listed the primitive; the emitter (reads the `ValDef`) rendered correctly, but `CtorFunnel` (deliberately reads the SIGNATURE) synthesised a subclass primary typed from the STALE list — 3 errors.
Numbers: 3 errors -> 0; `Symbol.descriptor`/`MemberKey` UNAFFECTED by design (frontend-set, never rewritten) — verified `port-map`/`signature` both 0.
Rule: a phase that retypes a DECLARATION owes every DERIVED signature that mentions it — the TIR stores a parameter's type twice and only one is what a given consumer reads.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 3 errors -> 0; Symbol.descriptor/MemberKey UNAFFECTED by design … verified port-map/signature both 0"

### O3. An opaque family that lands on an ARRAY ELEMENT is INEXPRESSIBLE — not refused, unreachable
(a) engine, in the phase's eligibility test/retype loop/coercion. Symptom: `taggablePrim` tested only a bare scalar or a method's bare return, so a declaration whose element is the domain value (`int[] locations`) was INVISIBLE to seeding and propagation (which runs between SYMBOLS, and an array element has none) — 33 real ported type positions the reference hand port types `Array[AttributeLocation]` were simply unreachable, not refused.
Numbers: engine specs 984=984, corpus 430=430; libGDX core 0 errors, every check/suite identical.
Rule: CLOSED for exactly ONE container depth (the erasure-identity boundary), not "arrays now work" generally — a deeper container has genuinely no coercion to name.
Triage (2026-09-04): REFUSED-BY-DESIGN — family O: a container deeper than one erasure-identity level (e.g. `List[int[]]`) has no coercion to name at all — a card would need a per-element copying bridge with real allocation cost, not attempted

### O4. An `OpaqueSpec`'s `hints` is a PREDICATE, so the surface fingerprint cannot see it — CLOSED
(a) engine, in the spec's own type. Symptom: the phase never implemented `SurfacePolicy`, so `PortManifest.fingerprint` compared two instances by NAME ALONE — and even fixed, `hints: Symbol => Boolean` (a lambda) has no stable rendering, so two specs seeding the SAME opaque type from DIFFERENT declarations (the one field a port actually edits) fingerprinted EQUAL, invisible to `ManifestAgreement` and every published port map.
Numbers: pure fingerprint move — every port inheriting the phase moves its `policy=` header at 0 errors/checks/suites moved.
Rule: a §1(b) parameter that cannot be RENDERED is one the surface contract cannot hold an opinion about — the predicate's expressiveness was never actually used (every port wrote an exact-FQN test by hand anyway).
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: pure fingerprint move — every port inheriting the phase moves its policy= header at 0 errors/checks/suites moved"

### O5. A MINTED unit has no origin, so EVERY module in the pipeline emits it — was 24 errors, six suites stopped — CLOSED
(a) engine, in what a run decides to WRITE, not the phase's translation. Symptom: the phase MINTS its opaque companion as a top-level unit with `Origin.synthetic`; `PortRun.converted`'s documented fallback ("a unit with no usable origin is converted, to avoid a silent omission") is right for a PARSED unit and wrong for a MINTED one — since a dependent's model CONTAINS its base's units, the phase minted the SAME object in 9 modules where only 1 was legitimate, each duplicate producing 3 compile errors (opacity is per-DEFINITION, so a duplicate's own accessors don't even type-check against the first) — 24 errors, 6 dependent suites stopped, while the BASE's own 21 checks stayed perfectly green.
Numbers: `just measure-all` green end to end, exactly one `TextureHandle.scala` exists, all 6 stopped suites back at committed outcomes.
Rule: a phase that SYNTHESISES a declaration owes the same one-module answer `inject` does; correction 2: the fence must ALSO refuse when hints SPAN two modules (fixed via `refuseSpanningHints`, a §1(c) library-rule refusal).
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: just measure-all green end to end, exactly one TextureHandle.scala exists, all 6 stopped suites back at committed outcomes"

### O6. An opaque family that REPLACES a java CLASS — Align CLOSED, nested/class-to-opaque OPEN

Tried: 4 of 8 opaque families sge declares over a java CLASS at that FQN are a case the mechanism has NO vocabulary for (the phase unconditionally MINTS, colliding with any injected replacement) — `Align` has a clean answer; `Input.Key`/`Button` (nested) and `HttpStatus` (constructor+field) remain OPEN with no vocabulary at all.
Fix (Align only): a new `Existing` target form retypes seeds against an ALREADY-INJECTED opaque type instead of minting a new one (supporting fixes: `FlowPropagation` walks through `Tree.Commented`, `wrapCall` handles the BOXED primitive form, `MergeablePolicy` lets a DEPENDENT seed additional hints). Numbers: Align — gdx 7->0, gdx-test 217/4 unchanged, ashley 0, 123 declarations retyped; dependent blast gdx-vfx 0->8->0, visui 7->26->7.
Rule (residue): `Key`/`Button` need injecting into an existing companion (scala 3 forbids splitting — unbuilt); `HttpStatus` needs a different mechanism; TextraTypist's own unconnected fields are LATENT, invisible to every count.
Triage (2026-09-04): FAMILY O: `Key`/`Button` need injecting into an existing companion (scala 3 forbids splitting one — unbuilt); `HttpStatus` needs a different mechanism entirely

## 14. The IDIOM layer — what was REFUSED, with its number

`DESIGN.md` §8.15 licenses this layer: it has no DIFFERENCE to discharge, so its mandate is the reference ports read as evidence and its safety argument is a REFUSAL ENUMERATION rather than a suite result. This section is the rows that were priced and NOT built, each with the number that decided it. Every one is classified (a)/(b)/(c).

### O7. A GL ENUM FAMILY is a VOCABULARY, and this mechanism retypes declarations rather than minting one

(b) engine — unbuilt, `ConstantsAs(enumFqn, constants)` form. Symptom: 15 GL enum families (`GLEnum.scala`'s 14 plus `Pixels`) sit behind the same scope fence (`RuleScope.Everywhere(except=GL20/30/31/32)`) `TextureHandle` needs for an unrelated reason (a nullary GL call reads as a real flow edge that must NOT retype), hiding every GL-typed formal from any opaque spec.
Cause: not a retype-with-different-target but a MINT with different CONTENT — sge keeps java's raw constants AS `Int` while ALSO minting named `val`s derived from those `static final` declarations (never transcribed, §4.59).
Numbers: signature counts by family (`TextureTarget` 22 formals down to `BufferUsage` 1); `UniformLocation` corrected out of the "consumer-side, unseedable" bucket — 32 real ported positions, expressible today.
Next step: build `ShaderType` (2 constants/2 formals) end to end first, then `TextureTarget`, to prove the fence composes with `TextureHandle`'s.
Triage (2026-09-04): FAMILY O: ConstantsAs(enumFqn, constants) GL-enum-family form — "(b) engine — unbuilt, ConstantsAs(enumFqn, constants) form"; "Next step: build ShaderType … end to end first, then TextureTarget"

### O8. `FlowPropagation` does not follow an ARRAY ELEMENT READ — `UniformLocation` 0 -> 37 as `Mint`, 0 -> 19 as `Existing` — CLOSED
(a) engine — `FlowPropagation`/coercion, two edges + three rules. Symptom: `UniformLocation` (sge's opaque type over GL handles in an `int[]`) exposed that array-ELEMENT reads were never followed: as `Mint` 0->37 (no extensions, java compares raw ints); as `Target.Existing` (O6's precedent) 0->19 (`refSym` had no `Tree.ArrayAccess` arm, `Return` used the wrong helper for an `If`-wrapped tail).
Numbers: 19->0; gdx 0 errors all platforms, 20 digests all in `BaseShader`. Dependent blast (wave 2.11, separate): a prior `coerceArgs` fix unwrapped opaque args at non-emitted callees the BASE retyped — gltf-test 3->18, screens 0->4, textra 0->40, visui 7->34 — fixed by reading the base's published port map (`RunScope.baseMemberUpstream`), back to 3/0/0/7.
Rule: a coercion at a non-emitted callee reads what the BASE published, never re-derives from this run's own view.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: 19->0; gdx 0 errors all platforms, 20 digests … Dependent blast … fixed … back to 3/0/0/7"

### O9. `PrimitiveToOpaqueTransform` binds `primSym` to the WRONG `scala.Int` when a prior phase minted a duplicate — textra Align 0 -> 58 — CLOSED
(a) engine, one `find` -> `minByOption`. Symptom: `retargetFixedTypeSyms` mints a second `Symbol` named `"scala.Int"` via a map separate from the one the opaque phase resolves `intSym` from; `find` over `SymbolTable.all` (unordered `Map.values`) could bind the wrong (minted, high-`SymId`) one, making every real `Int` field fail `isPrim` and silently dropping every hint.
Numbers: textra 122->62 (58 Align rows + 2 sibling-family rows, same bug); gdx 0/0/0/54 held; every dependent unchanged.
Fix: `minByOption(_.id.raw)` — lowest `SymId` is always the frontend's original — applied to `primSym`, `boxedPrimSym`, `arraySym`.
Triage (2026-09-04): CLOSED-IN-FACT — "Numbers: textra 122->62 …; gdx 0/0/0/54 held; every dependent unchanged"

### I1. `return this;` → `this.type` — **REFUSED on a measured 2 of 709, §1(a)**
Payoff depends on a split nobody had measured: precision gained only where the declared return type is a STRICT ANCESTOR of the declaring class (fluent builder), not where it already IS the class (self-typed, the common case).
Numbers: `ReturnThisCensus` on libGDX — 709 methods answer `this`, 678 self-typed, 29 answer something else, only 2 in the ancestor-typed bucket that gains anything (4 on gdx-gltf, 0 elsewhere) — libGDX's own `Vector<T extends Vector<T>>` already self-types.
Rule: `this.type` on a base constrains every DEPENDENT's override (`SurfaceIntrusion` one level down) — re-read the census before building; it is re-derived every run.
Triage (2026-09-04): REFUSED-BY-DESIGN — family I: `return this` -> `this.type` priced at 2 of 709 payoff sites — refused on cost; a card would need `ReturnThisCensus` re-run on every new corpus addition to see if the split moves

### I2. instanceof-cascade → `match` — **REFUSED on three independent mechanisms, §1(b)**
Payoff: 29 chains corpus-wide (libGDX 12, gltf 7, liqp 10), 2-3 arms typically — each blocker alone exceeds it: (1) cannot compose with K18's reified-position runtime disjunction, not expressible as a scala type pattern; (2) java's `else if` RE-EVALUATES the scrutinee per arm, `match` evaluates once — silently different for a non-stable expression, no purity test exists (same open question as F7/JS-E17); (3) arms carry `return`/`boundary.break`, needing the same named-boundary interposition T18 already refused for the same jump-stealing hazard.
Triage (2026-09-04): REFUSED-BY-DESIGN — family I: instanceof-cascade -> `match` needs a reified-position-compatible pattern encoding, a purity test for a re-evaluated scrutinee, and T18's still-unbuilt named-boundary interposition — three blockers, none closed

### I3. StringBuilder → interpolation — **REFUSED, §1(a), and the evidence is the hand ports'**
Both reference ports keep `StringBuilder` and reach for `s"..."` only for one-shot messages — no sampled file replaces a loop-accumulated builder; `java.lang.StringBuilder` is directly usable from scala, nothing to translate. Payoff 15 of 82 `new StringBuilder` sites; the perf claim is only plausible, not measured, and detection needs a dataflow question this engine doesn't have.
Triage (2026-09-04): REFUSED-BY-DESIGN — family I: `StringBuilder` -> interpolation needs a one-shot-vs-loop dataflow detector the engine doesn't have, and no hand-port evidence licenses it anyway

### I4. equals/hashCode → `case class` / derivation — **REFUSED, §1(b)/(c), zero hand-port evidence**
`case class` appears exactly once in the sampled hand port, inside a file-level redesign unifying 4 java classes — not a translation of an equals/hashCode pair. Blockers beyond the absent evidence: a `case class` MINTS 6 new members into the shared surface for a cosmetic gain; its equality is over the PRIMARY CONSTRUCTOR's params, a different set than "declared fields" (decided by the ctor funnel, §8.2); a mutable case class's `hashCode` is a live hazard once an instance is a `HashMap` key, which the corpus's value-shaped classes routinely are. `JS-C43`'s record work (6 cells differ, 2 unrepairable) is the precedent.
Triage (2026-09-04): REFUSED-BY-DESIGN — family I: equals/hashCode -> `case class` needs a fields-vs-primary-constructor-params reconciliation plus a live-HashMap-key hazard analysis, and zero hand-port evidence licenses it

### I5. C-style array-init loop → `Array.fill`/`tabulate` — **REFUSED, §1(a), negative evidence**
`Array.tabulate(n)(f)` allocates the function per call in addition to the array; gating on "provably one-shot" needs a dataflow answer this engine doesn't have. The hand port did the opposite on purpose in hot files (`DelaunayTriangulator.scala`: 12 indexed `while` loops, zero `.map`/`.foreach`, grows a reused buffer). Population <=514 candidates, only 2 confirmed, self-declared under-sampled.
Triage (2026-09-04): REFUSED-BY-DESIGN — family I: C-style array-init loop -> `Array.fill`/`tabulate` needs a provably-one-shot dataflow answer the engine doesn't have; the hand port went the opposite way in hot files

### I6. try/finally-close → `Using` or the JLS 14.20.3 lowering — **REFUSED twice, §1(b)**
`Using(...)` already forbidden by §4.4 (body holds `return`/`boundary.break` bound outside the try — zero `Using(` sites in either reference corpus). Rewriting a hand-written `finally { r.close(); }` into the JLS lowering changes exception-path BEHAVIOUR: java's pair does not suppress (a close exception during unwinding REPLACES the primary), the lowering PRESERVES the primary and suppresses instead — usually what the author wanted; silently upgrading it is the port disagreeing with the library, green compile, no moved count. Population 16 sites; remaining gain nil, the hand-written form is already idiomatic scala.
Triage (2026-09-04): REFUSED-BY-DESIGN — family I: try/finally-close -> `Using`/JLS lowering changes exception-suppression behaviour (replaces vs suppresses the primary) — refused twice, no safe rewrite exists

### I7. null → `Option` — **AVAILABLE and off; `Named` is the preferred wrapper target** — CLOSED
`Target.OptionTarget` is built and spec-tested, closes K13 exactly as `Named` does, stays off: `Some(x)` ALLOCATES on every wrap where `Named`'s opaque `T | Null` is zero-allocation; hand-port evidence agrees (34 `Option[` vs 301 `null` in translated-only files, `Option` only at seams a human identified). No standalone corpus library carries nullability annotations to measure against (7 of 11 upstreams surveyed carry none).
Rule: `Named`+opaque for a hot path, `Union` for a port at the floor, `Option` where allocation is acceptable. Separately: index-`for` -> `Range` stays unchanged by agreement — 1,588 sites, concentrated in perf-critical code where the hand port independently converged on `while`.
Triage (2026-09-04): CLOSED-IN-FACT — "AVAILABLE and off; Named is the preferred wrapper target" — deliberate shipped-but-disabled policy

### I8. The `getClass()` residue a SAM conversion leaves, which NO guard can close — §1(a), COUNTED — CLOSED
Guard 5 closes the IDENTITY half of the difference (per-evaluation allocation) but not the CLASS-NAME half — java's anonymous class has a stable name (`Outer$1`); a lambda's is a synthetic, unstable hidden-class name. No structural test exists (any value reference can reach `getClass()`); it is a §4.4-shaped residue (valid scala, green compile, no moved count), COUNTED on the conversion's own `Decision` (`Decision.Kind.SamLambda`, `was=`) rather than guarded against.
Triage (2026-09-04): CLOSED-IN-FACT — "COUNTED on the conversion's own Decision … rather than guarded against" — deliberate, no structural test exists

### I9. The SAM conversion was BLOCKED on M6, not on its own guards — 0 → 4 errors on libGDX core, §1(a). **CLOSED**

Tried: wiring `SamLambdaTransform` in took the base 0->4 errors (reverted, unwired) — the transformer's own claims held exactly (23 predicted = 23 moved members); the blocker was the emitter's `Tree.Lambda` arm, which restores java's "return leaves the lambda" via a nested `def` but REFUSES (renders bare) whenever it cannot NAME that def's result type.
Fix: `Tree.Lambda.resultTpt`, filled by `SamLambdaTransform` from the anon's `DefDef.returnTpt` (source-converted) and by `SpoonTir.samResultTpt` from the abstract method's class-file result (source-written).
Numbers: 0->4->0 errors; `omissions` 66->69->66, proving M6's "refuse loudly" claim false here (a scala `return` in a closure is a legal non-local return, not a compile error, until counted) — plus a `PorterNote` placement fix and an M10-shaped `lambdaSeq` name-counter collision fixed alongside.

### I9. A RAW-typed SAM target — **PREDICTED loud, MEASURED clean, §1(a). Do not add the guard** — CLOSED
Symptom: a raw generic SAM target (`new Comparator(){...}`, ascribed `[?]` per CLAUDE.md §3.5) produces a lambda at a wildcard-applied type, no corpus site to confirm scalac accepts it.
Numbers: `scala-cli compile --scala 3.8.4` exit 0, no diagnostics — the wildcard instantiates to the same erasure the raw java type already had.
Rule: do not add a 7th guard (`RawTarget`) on suspicion alone — measure first; `SamLambdaTransformSpec` now pins this emission.
Triage (2026-09-04): CLOSED-IN-FACT — "PREDICTED loud, MEASURED clean, §1(a). Do not add the guard"

### K34. `retargetSelectRewrite` did not handle `Chain`/`Template` on `Tree.Select` -- 12 errors on ashley (Bits `.empty`, `.length` on BitSet). **CLOSED (wave 3.1ac)**

Tried: `retargetSelectRewrite` handled `Rename` at a `Tree.Select` call site but not `Chain`/`Template` — 12 errors on ashley (`Bits.empty`->`isEmpty`, `Bits.length`->Template), 0 on gdx.
Fix: extend to `Chain`/`Template` at arity 0; a `selectChainRewritten` identity set lets `transformApply` strip the outer `()` when a 0-arg Apply wraps an already-rewritten Select.
Numbers: 12 errors closed on ashley, 54 engine specs green.

### K35. A call into a DROPPED+INJECTED type cannot follow the injected member's arity -- **CLOSED**

Tried: nothing read the injected `.scala` file's own arity/type spelling, only java's arity.
Fix: `InjectedSurface` reads every injected file with scalameta, feeding `injectedOverrideTypes` (rebuilds an override's parameter types from the injected parent's own strings, substituted through `extends`) and `calleeHasParens` (a call into an injected member follows the injected arity).
Numbers: gdx 0/0/0 blast, 138 `InjectedSurfaceSpec` assertions green; a prior ashley-specific hand workaround is now unnecessary.

### K36. Retarget runtime: peek/first/pop exception class, removeRange inclusive bound, ensureCapacity growth, Array(T[]) capacity, Iterator.remove — **gdx-test 35 -> 11 failing, 8 SortTest CLOSED**

Tried: 8 families of retarget call-site semantics compile clean and diverge at RUN TIME — exception class on empty (peek/pop), inclusive-vs-exclusive `removeRange` bound, `ensureCapacity`/`setSize` growth+refusal, wrong default capacity on array-copy construction, two divergent `toString` shapes, in-place-vs-returning `xor`, a read-only iterator bridge with no `remove()` handle.
Fix: each gets a Template restating java's own rule, a dedicated factory (`DynamicArray.from`), or — for `Iterator.remove()` — an index-tracking removing-iterator shim keyed on retarget target FQN (3 mechanisms: `ArrayDeque` via `Buffer.remove(i)`, `DynamicArray` via index callbacks, `ObjectMap` via parallel key-tracking); unsupported targets keep the read-only bridge plus a counted `IteratorRemove` finding.
Numbers: gdx-test 35->11 failing (SortTest + iterator fixes), then IteratorRemove closes 180/11->184/7 (27 digests, all `.iterator` on retarget targets); 3 residual `JsonMatcherTests` failures declared expected (sge deliberately drops `CharArray` string-concat semantics).

Nested iterator types (2026-09-04, subplan item 1): java's `Keys`/`Values`/`Entries` are MapIterators over one reused entry; their image is `scala.collection.Iterator` (rows `X$Keys/$Values/$Entries -> scala.collection.Iterator`, `retargetTypeArgs` with `Applied(Tuple2, …)` for Entries), the producing call snapshots (`Collect` takes `.iterator` when the call's own type is Iterator; a standalone `entries()`/`iterator()` becomes an ArrayBuffer-backed `Iterator[(K, V)]`), `hasNext()` is parenless (`Chain`). Reads only; a cursor `remove()` stays counted. anim8 1 -> 0 (23/23 run), ai 2 -> 1, textra 4 -> 1, gdx 0 = 0 (184/7), no policy file names a port.

### K37 Retarget dependents: base retarget table measured on every dependent before it lands

(b), per-library retarget policy applied across dependents. Symptom: the base's collections-retarget wave reached 0 errors on libGDX core alone, but the same table (inherited via `extendedBy`) runs over every dependent's own program too — first corpus-wide measurement found 230 dependent errors born of a 0-error base, invisible from the base's own green run.
Numbers (floor -> final, ~2 dozen waves): anim8 0->1, textra 0->9, gltf 3->0(residue), vfx 0->0, ai 0->2, visui 7->6, gdx/gdx-test/screens 0->0 throughout; final corpus residue ~55 rows. After subplan item 1 (2026-09-04): anim8 0, ai 1 (`new Array<S>` at a type variable: MkArray evidence, item 1b), textra 1 (same family), gltf 2 (items 2, 3), visui 4 (items 4-7), vfx 0 (64/64), ashley 0. Item 1b (same day): `Construct.typeVarEvidence` puts a `given lowlevel.MkArray[T] = MkArray.anyRef[AnyRef].asInstanceOf[…]` (sge's own `createRef` cast) in scope at a type-variable element — ai 1 -> 0, textra 1 -> 0; gdx 0 (184/7), anim8 0 (23/23), ashley 0. Item 4 (2026-09-04): `resolvedThroughParent` carries post-bodies through synthesised parameters (null-guarded or boolean-guarded); DistanceFieldFont omissions 0 -> 2 (C3 counted); VisWindow still E134 (C3 item 4c: withholding fixpoint).
Classification: 5 §1(a) engine bugs closed (duplicate FixedType symbols/O9, TypeApply(Select) dispatch gap, static-receiver fallback, `$T0` applied-type rendering, transitive-givens closure, bare-ref-return-refusal over-broad); a dozen+ §1(b) retarget-table policy gaps (missing Entry retargets, arity-blind `nullableMembers`, set-iteration ForEach entries, copy-constructor descriptor keys, IdentityMap->ArrayMap rename); one §1(c) injection (gdx-gltf's `GLTFMorphTarget`, extends a `final` retarget target); a residue of COUNTED cross-boundary gaps (nested-type-of-retarget-parent references, wildcard captures, Tuple2 immutability at copy patterns).
Residue 2026-09-05: four `getSelectedIndex` bodies (gdx `SelectBox`/`List`, textra `TextraListBox`/`TextraSelectBox`) stay hand-written because java passes an `OrderedSet` where an `ObjectSet` is expected and lls `OrderedSet` is FINAL and does not extend `ObjectSet` — no engine image; the exit is an lls change (cross-repo) or a per-site `Template`, recorded in `PROGRESS.md` §13.27.
Rule: a base retarget table is never validated by its own green run — measure on EVERY dependent before landing (§1.5, met at 230 rows).
Triage (2026-09-04): FAMILY K: per-dependent retarget-table residues — "final corpus residue ~55 rows"; post-subplan-item-1 residues remain nonzero (ai 1, textra 1, gltf 2, visui 4)
Item 2 (2026-09-04): the actual mechanism was not `CollectionsRetarget`'s owner fallback (`ObjectMap` has no `put` retargetRewrite entry) but `NullabilityTransform.coerceArgs` — `ObjectMap#put`'s `@Null`-annotated value formal gets coerced on every call reaching that inherited method, blind to a dropped+injected receiver; `isSubstitutedReceiver` closes it the same way `isRetargetted` already does for a retargetted one, reading `RunScope.ownSubstitutedOwners`/`baseSubstitutedOwners` (new). gltf 2 -> 1 (`exportMesh` closes; `copyLayout` is item 3). The `CollectionsRetarget` guard (`retargetRewrite`/`retargetSelectRewrite`) was still added per the design card and is a real, if here inert, correction for a Template-rewrite shape. Collateral on gdx, all attributed: nullability-boundary 105 -> 110 (+5 genuine `Json`/`Pool` sites previously silently wrong; 4 other rows are message-text-only), 0 net compile errors gdx/gltf/ashley, 1465 engine+corpus specs green.
Item 3 (2026-09-04): `copyLayout`'s `ObjectMap#putAll` argument coercion carries java's own use-site wildcard (`? extends K, ? extends V`) at an invariant retarget target; the SAME-arity strip arm (`transformType`) already narrowed lower-bounded wildcards but left upper-bounded ones — correct at a DECLARATION (`?` is valid Scala and the right image of java's own wildcard: `TiledMap#setOwnedResources(DynamicArray<? extends Disposable>)` needs it, first attempt stripped there too and cost gdx 0 -> 5), wrong at a CAST TARGET (`asInstanceOf` needs a reifiable type). Fix: `stripCastWildcard`, applied only to `Tree.Typed` nodes, strips the upper bound when it is GROUND (`hasNestedBound` guards a raw-type-erasure bound, e.g. `Tree.Node<N extends Node<N,…>>`, from being used as a false-concrete instantiation). gltf 1 -> 0 (`copyLayout` closes; `exportMesh`/item 2 already closed). gdx 0, 0 members moved by this fix specifically (no `putAll`-shaped call in gdx core needed the cast strip; the 136 members that DID move on this measurement are pre-existing drift, present before item 3 touched anything — see below). gdx-test 184/7 unchanged. **Separately found, not this item's to fix**: `port-sge-gltfJVM/Test/compile` now reports 8 errors on `PBRTextureAttribute`/`PBRCubemapAttribute` (`Not found: type T`, `Found: Null` in a constructor) — unrelated to collections/wildcards, looks like item 4's `CtorFunnel` parameter-threading reaching a gltf-only shape never measured against gltf (item 4's own commit measured only gdx and visui). Also separately found: this worktree's `LibgdxCoreMigrate`/`LibgdxTestMigrate` findings/port-map baselines are stale against master's current tip (GLFrameBuffer builder ctors, TextArea/TextField primaries, `OrderedMap$*` parents all differ) — present in a measurement taken BEFORE any item-3 edit, so pre-existing and not this item's regression.

Item 4e (2026-09-04): `ctorTypeParamSubst` substitutes a generic constructor's own type params as wildcards, `javaDefault` gives value-typed post-body slots their JVM zero with a boolean guard, and delegation values map against `uniquePbParams` so a root going through a partial chain fills defaults for the rest. gltf 8 -> 0 (C3 item 4e); gdx 0, gdx-test 184/7, visui 3 all held.
Items 6-7 (2026-09-04): `CharArray` `append(Object)`/`toString()` descriptor rows replace the two `Dialogs#getStackTrace` body substitutions (visui 0 residue from that construct; gdx-test 184/7 -> 187/4, `JsonMatcherTests` newly passing); `VisTextField#keyboard.show(boolean)` body substitutions deleted — gdx 1.14.1's vendored `OnscreenKeyboard.show(TextField)` broke VisUI's own 1.14.0 `show(boolean)` call, sge's hand port re-parented `VisTextField` onto its own `TextField` to keep it, this port's `VisTextField` keeps upstream's `Widget` parent and cannot, so the fix is a counted residue (`ported/sge-visui/divergence-verdicts.tsv`) — visui 7 -> 5 (3 `E007 Found: Boolean` at the residue, 2 unrelated pre-existing).

### K38. ImmutableArray per-entry retarget: `Array -> ArrayBuffer` for ashley beside the base's `Array -> DynamicArray` -- **OPEN**

(b) engine mechanism, unbuilt. Symptom: exact parity with sge's `ImmutableArray(ArrayBuffer[A])` needs a PER-ENTRY-SCOPED retarget — a dependent (ashley) declaring `Array -> ArrayBuffer` for its own declarations beside the base's `Array -> DynamicArray` — `CollectionsTransform` today allows only one target per source type, whole-program.
Numbers: the dual-backing `ImmutableArray` injection (wave 3.2e) already works for both normal compile and drop-in without this; only the scope-override extension is missing.
Rule: the Pool class-to-trait gap this entry originally described is CLOSED separately by CT12; remaining drop-in residue on Pool is 7 `SystemManager` errors plus K13.6's opaque-sentinel limit, unrelated to this entry.
Triage (2026-09-04): FAMILY K: per-entry-scoped retarget (dual target per source type) — heading itself "OPEN"; "(b) engine mechanism, unbuilt … only the scope-override extension is missing"

### K39. The DIAMOND FORWARDER sees only INJECTED parents, never a CLASS FILE's DEFAULT method — **11 `E164` rows on the ladder's L0 step, 0 on every other port. CLOSED 2026-09-06**

(a) universal, engine. Symptom: a class whose SUPERCLASS declares a concrete member and whose INTERFACE parent is an EXTERNAL type carrying a JLS 9.4.3 `default` of the same name and arity is legal java (JLS 8.4.8 — the class member implements the interface method) and `E164 inherits conflicting members` in scala. `TirEmitter.diamondOverrides` already mints `override def m() = super[Sup].m()` for exactly this shape, but its `externalOf` reads `externalConcrete`, which `RuntimePlan` derives from the SUPPORT TYPES a run injects as parents — a JDK interface is not one, so `java.util.Iterator#remove`'s default is invisible to it.
Numbers (2026-09-05): 11 rows on `LibgdxL0Migrate` — `IntIntMap`/`IntMap`/`LongMap`/`ObjectFloatMap`/`ObjectIntMap`/`ObjectLongMap`'s `$Entries`/`$Values`/`$Keys`, each extending its own `MapIterator` and `java.util.Iterator`. They are INVISIBLE while any typer error remains (§3/G30) and appeared the moment L0's typer count reached 0. Zero on the full port and every dependent: their policy retargets those collections away, so the shape never reaches an emit.
Numbers (2026-09-06, the fix): `SpoonTirBuilder.externalDefaults` records the JLS 9.4.3 `default` methods — declared UNION inherited, arity-only, `CtMethod.isDefaultMethod` or "neither abstract, static nor private" on the class-file shadow — of every EXTERNAL interface a program type (nested types included) names as a parent, published as `Program.internedDefaults`; `diamondOverrides.externalOf` unions it with `externalConcrete`. The guard is the RECORDED default: an abstract interface method contributes nothing, and a parent this program declares is excluded because the emitter already reads its body. lls with `java.util.Iterator` still a parent (`LLS_RUNGS=nullable,enrich,witness` — the default steps retarget it to the `JavaIterator` shim, whose entry `externalConcrete` already had, which is why the default lane never saw this) **11 -> 0** JVM/JS/native, suite 273 held; default lls 0 held; gdx, gdx-L0 and ashley flat.
Rule: the exit is NOT "add the JDK interfaces to `externalConcrete`" (kept, and it is what the fix obeys) — minting a forwarder wherever an external interface MIGHT carry a default changes emitted text at every class-plus-interface parent pair in the corpus. §1(a) engine.

### K40. The RAW-RECEIVER erased view cannot be driven by the member's RESULT — **2 L0 rows left counted; the attempt cost 3 refusal specs and 4 errors. Do NOT retry as written**

(a) universal, engine. Symptom: a RAW type erases its members WHOLE (JLS 4.8), the RESULT as much as the formals, so `rawClass.getEnumConstants()` is `Object[]` in java and `Array[c.T]` through the `Class[?]` this engine emits — `ClassReflection#getEnumConstants` and `Json#getFields` on the ladder's L0 step. `SpoonTir.erasedReceiverView` already interposes java's erased view; its `depends` asks only the PARAMETERS.
Numbers (2026-09-05): adding the RESULT to `depends` closed both rows and opened four elsewhere (`ResourceData$SaveData#loadAsset`, `Tree#expandAll`, `OrderedMap`/`OrderedSet`'s `remove()` — an `asInstanceOf[T]` naming a variable the emitted nested class cannot see); narrowed to a receiver with NO actual type arguments it still broke three REFUSAL specs (`UnconstrainedResultPinSpec` x2, `NumericOverloadAscriptionSpec`), because Spoon reports a CAPTURE as raw and the view then erases a receiver java never erased. It also made `mentionsTypeVarFilled` recurse on F-bounds until the `catch` swallowed a `StackOverflowError` (ssg-md 41 s -> 19 min) — that fuel leak is fixed separately and is real.
Rule: the exit is not `depends`; it is telling a RAW USE from a CAPTURE at `castType` (the G21 shadow, one more time). Until then the two rows are COUNTED residue on L0, and the same reading forbids the sibling attempt: dropping the `Object[]` array-covariance cast at a DECLARED `T[]`/`T...` formal is java-correct and costs `ComparatorOrderingPortSpec`'s control assertion, so it waits for the same distinction.

### K41. The OCCUPANCY SENTINEL: an open-addressed table's `null` cannot lose its `Object` bound -- **105 counted sites over 9 declarations, 2 diff files still incompatible. Do NOT drop those bounds**

(b) per-library, and it is a REPRESENTATION question the engine may not answer. Symptom: `ElementWitnessTransform`
drops java's implicit `<: java.lang.Object` bound on an element type so a primitive or opaque element becomes
admissible. Where the class reads `null` at an element slot to mean AN EMPTY SLOT — every probe of an
open-addressed table — the emitted code still COMPILES after the drop (`x == null` is universal equality in
Scala, `null.asInstanceOf[T]` is legal), and at a primitive element type every empty slot reads `0`/`false`
and the probe loop finds a key that was never inserted. Nothing else moves: no error, no other count.
Numbers (2026-09-06, lls): 105 `witness(OccupancySentinel)` rows over `ObjectMap`, `ObjectSet`, `OrderedSet`,
`IdentityMap`, `Object{Int,Float,Long}Map`, `IntMap`/`LongMap` and `BinaryHeap`; those nine keep the bound and
are absent from `dropBound`. The two lls suite files that need `ObjectMap[String, Int]`/`OrderedMap[String, Int]`
(`ObjectMapTest`, `OrderedMapTest`) stay `diff-incompatible` for this reason. Reading them as a
whole POPULATION is why the count is taken off the TYPE of the compared operand (`keyTable[i] == null`,
`key != null` on a local and `while ((key = keyTable[next]) != null)` are three spellings of one question),
never off the node shape.
Rule: the exit is a PARALLEL OCCUPANCY ARRAY — a different data structure, hand-written, and the library's to
ship; there is no coercion and no cast that closes it. Until a library ships one, keep the class OUT of
`dropBound` and let the boxed element type keep java's own null semantics. Two neighbours the same wave
measured and left counted: `witness(ErasedArrayCast)` 30 — an element-typed array presented as
`Array[java.lang.Object]` because JAVA wrote a RAW receiver (`Sort`'s `TimSort` field), which type-checks and
throws `ClassCastException` at a primitive element type, so `TimSort` is not a subject; and
`witness(UnhandledCreation)` 4 — `<V> V[] toArray(Class<V>)` reflects its array type out of a `Class`
argument and java's signature carries no clause to put a witness on.

### K42. `NullaryArityTransform`'s getter-like scan reads the ENGINE's OPERATOR LOWERING as a call -- **lls 27/392, gdx 133/1547 converted; the fix moves 408 gdx members. Do NOT land it as a base-flat change**

(a) universal, engine, and the population is COUNTED today. Symptom: `hasSideEffects` marks any
`Tree.Apply` with arguments side-effecting. The frontend lowers every java OPERATOR to exactly that
shape — `SpoonTirBodyExprs.binApply` mints `Tree.Apply` on a synthetic `scala.<op>#==` symbol so the
emitter can render it infix — so `return size == 0;` reads as a call and every comparison-bodied
getter is refused. §4.59: what the parser SYNTHESISES is not what java WROTE, and the argument count
of an operator node is a fact about this engine's translation.
Numbers (2026-09-06): lls `arity` step — 392 owned nilary value-returning declarations, 27 converted,
173 `AnchoredClosure` (57 `java.lang.Object`, 50 `java.util.Iterator`, 39 `java.lang.Iterable` — all
honest §4.5 anchors — plus 29 on unknown-surface JDK types), 164 `SideEffectingBody`, 10
`ComponentPartial`, 9 `StaticMember`, 9 `Overloaded`. `ObjectSet#isEmpty` (`return size == 0`) is one
of the 164. gdx: 1547 considered, 133 converted, 415 `SideEffectingBody`.
Priced: admitting java's own operators (a CLOSED list of 19 binary + 4 unary + the `eq`/`ne` the
frontend substitutes for reference `==`, never a phase-minted `+=`, which is a mutation) takes lls
27 -> 93 converted and `SideEffectingBody` 164 -> 96, and gdx 133 -> 199 with **408 emitted members
moved**. gdx's arity shape is PUBLISHED (`form=parenless` in its port map, read by nine dependents,
`PortMapTransform.followMemberRenames`), so that is a whole-corpus re-baseline and not a base-flat
fix. ashley already carries the defect as per-library policy — two `BeanPropertyTransform` getter-only
pairs whose comment names this guard (`Bag#isEmpty`, `ComponentOperationHandler#hasOperationsToProcess`).
Rule: land it with the dependent wave that re-measures all nine, never alone; and when it lands, close
`hasSideEffects`'s own `case _ => ()` default at the same time — an unenumerated node kind there is an
UNDER-refusal, the direction this phase promises never to take.

### K43. The FULL-POLICY libGDX port cannot extend `ported/lls` AS SCOPED — CLOSED 2026-09-06 by narrowing the lls port to the 12 files the real lls declares (0 errors held; L0 on it 13 -> 57, each a step seam); the ladder's L0 is the dependent

(b) per-library scope, and the decision is the maintainer's (an lls-repo API decision, not an engine
change). Symptom: `LibgdxPolicy.core = LlsPolicy.core(repoRoot, <default steps>).extendedBy(…)`, gdx's
`sourceSet` minus `LlsMigrate.Files`, `FrontendConfig(base, files, Nil, resolutionRoots = List(base))`
(the source root as its own resolution root, so `PortRun.partitionUnits` emits only the declared
files). `PortRun.execute` stops at `SurfaceFold` — line 164, before the FRONTEND runs, so that
source-set arrangement is READ off `partitionUnits` and was never exercised.

Numbers (2026-09-06, one migration run): **5 FATAL**. `SurfaceDivergence: reassigned-params->var` —
`MutableParamsTransform` is in BOTH surfaces and implements neither `SurfacePolicy` nor
`MergeablePolicy`, so two instances can be neither composed nor compared. Then four
`SurfaceIntrusion`, each naming a subject inside lls's `governs` that lls's PUBLISHED MAP emits:
`java-collections->scala` (**63 subjects**, first `Array -> lowlevel.util.DynamicArray`),
`nullability` (`CharArray`, from `nullabilityErasureExempt`), `globals->implicits`
(`FlushablePool`, from `requiredGivens`) — those three ARE the wave's intended deletions — and
`class-to-trait` (`com.badlogic.gdx.utils.Pool`), which is **not**: lls emits `lowlevel.util.Pool`
and gdx drops+injects sge's AD-003 TRAIT (CT12). Behind them, unreached because the run stops first:
four fatal `ManifestAgreement.ExtraDrop` — gdx drops `Align`, `Bits`, `Pool` and
`reflect.ArrayReflection`, and all four stand in lls's published map.

The wall is the SCOPE. Seven of `LlsMigrate.Files`'s 54 are types the REFERENCE hand port declares in
the DEPENDENT's namespace — `sge/utils/{Align,BinaryHeap,NumberUtils,Pool,PoolManager,TimeUtils}.scala`
and `sge/math/RandomXS128.scala`, an eighth by rename (`GdxRuntimeException` -> `sge/utils/SgeError`).
Emitting them from `ported/lls` into `lowlevel.*` is not deleting policy: it deletes O6's `Align`
opaque type, AD-003/CT12's `Pool` trait, the `Bits -> scala.collection.mutable.BitSet` retarget and
the reflection drop, and moves six types out of the namespace §3.5's parity contract puts them in.
Nor can the scope simply shrink back to the twelve: they reference `GdxRuntimeException`,
`Collections`, `Predicate`, `Null`, `ArraySupplier` and `RandomXS128`, which sge owns, and a BASE
cannot resolve against its DEPENDENT — which is why the scope was widened to 54 in the first place.

Rule: decide the base's SOURCE SET before its policy — against the REAL base. Checked 2026-09-06:
`../lls` declares NONE of `Align`, `BinaryHeap`, `NumberUtils`, `Pool`, `PoolManager`, `TimeUtils`,
`RandomXS128`, `GdxRuntimeException`, `Collections`, `Predicate`, `ArraySupplier`; sge declares them.
The 54-file set was this repo's "cover all utilities" choice, wider than lls, so the intrusions and
extra drops are the dependent deciding types that are ITS OWN. The mechanism is not in question: nine
extension ports sit on the full core port at 0 errors. Exit: narrow `LlsMigrate.Files` to what lls
declares and answer the twelve references the way lls did (its own error type, `MkArray` for
`ArraySupplier`, a function for `Predicate`) as lls-manifest policy; the full port then extends it.
Until then the ladder's L0 sits on lls because it carries none of the colliding policy: `LibgdxLadder.universal` extends `LlsPolicy.core` with no drop,
inject or surface of its own, and runs (551 units, 2 -> 13 errors, every one a base step's seam;
PROGRESS.md §13.29 A3). The full port's parity decisions are not re-derived on the lls base; the
ladder re-applies each as a step. `MutableParamsTransform`'s missing `SurfacePolicy`/`MergeablePolicy`
was the one universal fix — landed with the family re-baseline it moves (`policy=` on every port
that lists the phase).

Outcome (2026-09-06, maintainer's choice): `LlsMigrate.Files` is the twelve (+ `Null`); the references
are class-file externals off the gdx 1.14.1 JAR (`GdxCoreClasspath`) — NOT a whole-tree resolution
root, which asked **300** base contracts before any phase ran — and are answered in `LlsPolicy.core`
(`TypeRedirectTransform` scoped to the entry, `ArraySupplier -> Function1` retarget, drops, one
injected `Collections`). Two rules it left: a base's steps SCOPE ON THE BASE'S OWN DECLARATIONS or
the inherited surface decides the dependent's ladder (CLAUDE.md §1.5), and a refused nullability
site keeps its annotation, so whoever narrows a port ships the annotation TYPE (K13.8 beside it).

### K44. A bounded-given clause threaded onto a METHOD does not reach its OVERRIDE component — OPEN, engine (a); 2 errors on the ladder port, gone when `Json` is dropped

`GlobalsToImplicitsTransform`'s bounded-given threading (the `MkArray` clause a declaration takes
because it constructs a witness subject at its own type parameter) closes over CONSTRUCTING callers
but not over the override graph it already builds: `Json#readValue[T]` gained `(using MkArray[T])`
and the anonymous `new Json() { override def readValue[T](…) }` in `Skin.getJsonLoader` did not —
**2 errors** (`E134` at the overload set, `E172` at the override's `super` call; 2026-09-06, step
"witness", 57 -> 44 otherwise). §4.55: a signature moves over the whole override component or the
phase refuses; an anonymous subclass is a member of that component (G29). Not fixed in place: the
only site sits in `Json`, which the reflection step drops; re-measure when a second site appears.

### K45. `CollectionsTransform`'s merge kept the BASE's `scope` — a dependent could not widen it; CLOSED 2026-09-06 (composes like `NullaryArityTransform`: `Only` unions, `Everywhere` unions its exceptions, mixed refuses)

Measured on the ladder port: a `CollectionsTransform(scope = Only("com.badlogic.gdx"), retarget = Comparator -> Ordering)`
fragment on top of lls's `Only(twelve)` instance changed NOTHING — **44 -> 44, `policy=` unmoved, 0
members** — because `mergedWith` built the merged instance with `scope = scope` (the base's) and no
finding said so. With the composed scope the same fragment measures **44 -> 15**. Flat by
construction elsewhere: every other base/dependent pair constructs the phase at the default
`Everywhere(Set.empty)` on both sides (grep over `balticporter/corpus`), whose union is itself.
Rule (CLAUDE.md §1.5 D12): a base's `Only` is the base's ENTRY; the dependent's fragment names its own,
and the merge is the union — a merge that silently prefers one side is the "two configurations
fingerprint EQUAL" blind spot one level down.

### K46. The witness phase's raw-wildcard fill owed TWO things it did not pay — CLOSED 2026-09-06 (a class literal's payload; java's unchecked conversion at a filled formal): ladder port 15 -> 10, `RawConversion` 4 counted

`ElementWitnessTransform` fills a raw wildcard with `java.lang.Object` wherever it unbound the element
parameter (K41's "closed under application"). Measured on the ladder port: (i) `mapClassDef` maps a
literal's TYPE but never the type a class literal CARRIES, so `Array.class` stayed
`classOf[DynamicArray[?]]` beside a lambda ascribed `PoolSupplier[DynamicArray[Object]]` — `E134`
at `PoolManager.addPool`; (ii) a raw formal filled to `C[Object]` is INVARIANT where java's raw `C`
took any `C<X>` — `Tree.findExpandedValues(Array values)`, `List.setItems(Array)`: 4 `E007`/`E134`.
Both are obligations the fill created (CLAUDE.md §1(b): an obligation the engine's own translation
created is the PHASE's). Paid inside the phase, never in the traversal: a class literal is a REIFIED
position other phases must not retype (K18/K20), so only this phase fills the payload it unbound;
and the argument gets java's own unchecked conversion (JLS 5.1.9) `arg.asInstanceOf[C[Object]]`,
erasure-sound, one `witness(RawConversion)` row per site. The fill mints its own `Object` reference,
absent from the pre-run symbol table — a predicate reading `program.symbolOf` answered `false` for
it (the first attempt's silent no-op). K40's two "raw receiver" rows are a different face (the
RECEIVER's erased view) and stay.
