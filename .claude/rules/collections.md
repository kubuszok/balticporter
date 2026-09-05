---
paths:
  - "**/CollectionsTransform*.scala"
  - "**/RetargetBoundaryCheck*.scala"
  - "**/JavaCollections*.scala"
  - "**/*Retarget*.scala"
---

# Collections retyping and retargeting — the obligations of a retyping phase

Detail for `CLAUDE.md` §1(b)'s `CollectionsTransform` row and the seam paragraphs. The general
obligations (scope, `SurfacePolicy`, counting, `accountedBy`) are `.claude/rules/phases.md`.

## Policy shape

- `families` adds per-library entries beside the §1(a) JDK table, each getting the same kind-aware
  call rewrites and boundary counts; `familyScopes` is per-ENTRY `RuleScope` (D12).
- `retarget` retypes a library type usable wherever the java source was (the scala target extends
  or implements the java source, so no coercion is needed). `retargetRewrites` keys `(name, arity)`,
  `retargetRewritesByDesc` keys `(name, Descriptor)` for overloads (descriptor wins); ten rewrite
  variants: `Rename`, `BoolDispatch(flagIndex, onTrue, onFalse)`, `Construct(companion, factory,
  fillTypeArgs, dropTrailing)`, `ForEach(via, arity)`, `Collect(via, into)`, `Chain(members, parens,
  dropArgs)`, `FieldWrite(field, method)`, `DropWrite(field, readTarget, why)`,
  `IndexedField(field)`, `Template(expr)` with `$recv`,
  `$0`..., `$T0`..., `$Target` holes. `retargetTypeArgs` maps arity-changing retargets
  (`SourceArg`/`FixedType`); `retargetCoercions` maps `(actualHead, expectedHead)` to templates.
- `MergeablePolicy`: independent keys union; same source with a different target refuses; scope
  disagreement on the same source refuses.
- A base gaining a `retarget` table reaches no fold when no dependent CONSTRUCTS the phase: one
  instance, inherited — `manifest` 0 on thirteen ports, nine port maps moved by the fingerprint.

## Two directions, one subtyping argument

`RetargetBoundaryCheck` (`collection-retarget`) counts the direction a subtyping argument does not
license — a value the JDK PRODUCES at a retargeted type — with kinds `ExternalProducer`,
`CastToTarget`, `IteratorRemove`. The boundary check cannot see it: the position-blind retyping
moved the node type on both sides of that slot.

## The third population — the seam INSIDE the program

A mapping that sends ONE java family to TWO unrelated scala families breaks java's own subtyping
edges, and both sides of such a slot are the phase's OWN OUTPUT: no JDK type in the comparison, no
arm of a JDK-shaped check fires. Measured at **16 of one port's 24 attributed errors while the
boundary lane counted none**, for THREE blindnesses no wider guard closes: the disagreement is at
no formal's HEAD (one type variable bound to two java-related types inside one argument list); one
side is a type the PROGRAM DECLARES (a head-FQN side test answers "not a party"); or the callee is a
symbol the PHASE MINTED with no signature, so *formals line up with arguments* is false and the call
is skipped. So `collection-internal` counts where BOTH sides are the phase's own, each row stating
the java EDGE broken and the two targets it became. **The split rests on the TARGETS, never their
package**: three runtime targets DO extend a scala collection so java's relation survives, and a
package test would report every correct slot they reach.

## Reified positions — not slots at all

- `instanceof` and a downcast ask about a RUNTIME OBJECT; a retyping moves the static type and not
  one object. A ported library holds its own values (the targets) and its producers' (java's own
  classes) at the same `Object` slot. Answer over BOTH (`JavaCollections.Reified`), refuse where the
  target is one no view can be, count the refusal; do NOT fire where the representation is known —
  an operand the phase retyped or one whose type the PROGRAM DECLARES (without that exclusion the
  coercion lands on `Queue.iterator()` in every `for` loop). **160 of 183 remaining failures on one
  library at 0 errors** (K18).
- A generic type ARGUMENT a third party reads back out of the class file (`TypeReference`,
  `TypeToken`, guice `Key`, `Class<T>`) is a third reified position the port writes nowhere —
  `Cannot construct instance of scala.collection.mutable.Map`, 10 of 23 failures, every count flat
  (K20). Do not retype a type argument a reified CARRIER holds; bridge at the USE. The MECHANISM
  belongs to the TRAVERSAL (the only place that knows it is descending into an argument) and the
  BRIDGE is the existing external-callee seam; WHICH carriers is `reifiedCarriers`, a (b) parameter.
- The same third party reads the OTHER end: a retyped `mutable.Map` handed to a serialiser is
  bean-serialised internals; a java `public` FIELD emitted as a scala `var` is PRIVATE on the JVM, so
  a framework auto-detecting fields sees ZERO properties and answers three of four assertions
  CORRECTLY from data that is not there (K21, 13 failures at 0 errors). A retyping phase owes an
  answer where its value LEAVES the program.
- That answer is RUN-TIME, at TWO seams: the object is asked deep and by view (a one-level `asJava`
  is the refusal already recorded; a copy detaches both directions), and the ACCESSOR the framework
  calls back is the same seam read from the other side (an emitted property can interpose;
  `@BeanProperty` cannot). Both lists ship EMPTY (`reflectiveSinks`, the bean-read list) and the
  engine publishes the CANDIDATES: one row per external callee with an opaque formal, one per emitted
  type with java-public fields. A candidate list is a residue of the POLICY and is published where
  the PHASE runs: the egress list on every port with collections, the bean list only where a module
  carries the `Only(Set.empty)`-defaulted phase — a module without it publishes nothing and claims
  nothing.
- **"N failures are gated behind this one" is a HYPOTHESIS.** Both K21 faces sat behind K20's
  exception; closing K20 flipped 2 of the 10 predicted. Re-census after the fix; quote the family
  that went to zero, never the suite delta.

## The phase may only reason from what it did

- `CollectionsTransform` decided "this cast can never succeed" from a `java.` prefix on the source
  type — and `java.lang.Object` has one. `(Collection<V>) anObject` is an ordinary downcast the phase
  does not touch; deleting it broke three sites in libGDX's `Json` inside a dropped type, invisible
  to every count. "Did I move this type?" is a lookup in `typeMap`/`remap`/`kindOf`.
- The argument bridge opened with `if javaIterableSym == SymId.None then t` while also serving
  `java.util.Collection`: a library using `Collection` throughout had the whole pass inert. Derive a
  guard from ALL targets. The shim-receiver refusal tested the HEAD SYMBOL against three shims and was
  `false` for a library's own `Cursor extends java.util.Iterator`; ask the ANCESTRY, and a TYPE
  PARAMETER's BOUND is the same question — 16 errors (K2.6).
- A coercion keyed on *the callee's declared result is not `Object`* found nothing because
  `java.util.Iterator#next()` interns with no signature; its predecessor keyed on *the result IS the
  capture* read a node the frontend fills with java's answer. State the REFUTATION (`!x.exists(isObject)`)
  where the artifact can be MISSING; three lines away, an unreadable class file must answer `false`
  because there the signature IS the evidence (G33).
- A residue count cannot tell a refusal from a switched-off fix: a reported boundary whose (source
  kind, target) pair HAS a factory is an engine bug the phase can check at the moment it files the
  finding — 5 findings misreading themselves for the life of a port (K2.5).
- `OverrideGraph.overridden` compared descriptors as strings; `go(T)` in `P<T>` and `go(String)` in
  `implements P<String>` had no edge, the retyping went looking for an external ancestor, found
  `java.lang.Enum` and held java's signature — two `E007`s naming `java.lang.Enum#parseOption`. A
  substituted edge may only ADD (unsubstituted first), and the spelling both sides are read in is ONE
  derivation moved out of `Descriptor.ofInfo`.

## Reference-port shapes are hypotheses

"ssg kept `java.util` in 32 of its 130 files" read as "scope the collections phase out of the seams"
cost `27 -> 47` errors, and `27 -> 51` turned off (K16). A hand port keeps a JDK type consistent by
editing every caller; a mechanical port that exempts some declarations SPLITS a call graph.

## Runtime shims (§4.5)

A Java collection interface's counterpart is a standalone trait with Java's arity; interop is by
extension methods on ONE of a related pair, never by extending `scala.collection.*`. A `Map` is not
a semantic the target LACKS, so `balticporter/runtime/package.scala` refuses it (P10).
