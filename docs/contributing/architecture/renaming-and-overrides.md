# Renaming and overrides

Several unrelated phases need to rename a declaration — turning a Java bean getter into a Scala
property, resolving a name clash created by an earlier rewrite, giving a port author's own chosen
name to a member. All of them share one hard problem: Java lets a member exist under a name that
Scala cannot use in the same place, or an override component spans several classes, and renaming one
member without renaming everything it overrides or is overridden by breaks the hierarchy. This page
covers the two shared building blocks that solve that problem, and the decisions a contributor must
know before changing either.

## `OverrideGraph`: which declarations must move together

`OverrideGraph` answers structural questions about a member across a class hierarchy: which
declarations are its parents in the override sense, which are its children, which declarations
override which, and — the operation everything else builds on — the full closure of a member: every
declaration reachable from it by the override relation, plus a record of which of those reach outside
this run's own units (external anchors) and which reach into a base module the current run does not
itself emit (base anchors).

Edges in this graph are keyed by descriptor — the same source-level parameter spelling used for
overload identity elsewhere in the engine — never by bare name and arity. Matching by name and arity
alone conflates two different questions: "is this the same member, overridden" and "do these two
declarations merely happen to share a name and argument count". The two look identical from a shallow
match and are only told apart by descriptor-accurate comparison.

**Anchoring is conservative on purpose.** A closure that reaches an external parent about which the
engine has no surface data anchors — the whole closure is refused as a unit rather than renamed
partially. An anchored closure is always counted so the refusal is visible; the alternative, silently
renaming only the reachable part and leaving the anchored part alone, would produce a hierarchy that
disagrees with itself, which is a contract broken silently rather than a translation refused loudly.
An anchored closure never invents a name for a member that was not actually asked to be renamed — a
policy entry naming an accessor that does not exist is reported as a mismatch, never treated as
license to synthesise one.

## What is never renamed

A declaration whose signature and name are a class-file fact — something the frontend read off an
external symbol rather than something a compilation unit in this run declares — is never moved by a
renaming phase, structurally: ownership is decided by climbing the symbol's owner chain to see
whether it reaches one of this run's own units (see
[intermediate-representation.md](intermediate-representation.md)), and only an owned declaration is
ever a rename target.

## `MemberRenamer`: applying a rename request

`MemberRenamer` sits above the override graph and turns a rename *request* into an actual rewrite. It
expands the request through the member's full override closure, refuses a request that touches an
anchored closure, checks the requested new name against every name already *effectively* visible at
the point the rename would land, and records exactly one decision per renamed declaration. The rewrite
itself is applied once, as a single symbol-table update — every use of the symbol picks up the new
name automatically through the symbol reference, rather than being rewritten one call site at a time.

**Effective names are read parents-first.** Because Java allows two declarations related by
inheritance to reuse a name Scala would treat as a clash, checking a candidate new name against only
the declarations directly in scope is not enough — a name that looks free locally can already be
taken by an ancestor, and the check has to walk ancestors before descendants to see that. Where a
captured local variable is what actually needs renaming rather than a member, the same rule applies
only where that local is genuinely shadowed within its own enclosing scope, never as a blanket rename
of every capture that merely shares a name with something unrelated.

Several call sites of this shared machinery differ in one rule each, layered on top of the same
closure-and-anchoring core:

- A rename driven by where a member needs to move to match some other target (following a base
  module's own published rename, or retargeting a redirected type) may not move a *base's* own
  declaration — there was no agreement from the base to move it, so touching it would silently break
  the contract the base already published.
- A rename a port author writes explicitly, by contrast, is a name the author is free to choose, so a
  collision is refused outright rather than silently resolved, on the theory that a free choice has an
  easy one-line fix and a silent resolution would hide that a collision happened at all.
- Collision resolution itself is delegated to the emitter's own existing suffix-until-free machinery
  rather than re-implemented — the renaming layer only refuses what that machinery cannot move on its
  own.

### Symbolic names and binary compatibility

A rename's target may be a symbolic Scala operator name (`+`, `<=`, `unary_-`, and similarly-shaped
names). Where it is, the phase emits Scala's `@targetName` annotation carrying the original Java name,
so the class file keeps the Java-derived name for binary compatibility while the source reads as an
operator. Overloads that already shared one Java name are never mistaken for a naming collision under
the new name, because Java itself forbids two overloads sharing an erased signature — if they
coexisted under the old name, they coexist under the new one. As with every other renaming path here,
the whole override component moves together, or the rename is refused for the whole component; call
sites always follow through the same single rewrite.

## The property collapse: a `var`/`val` decision, not a blanket rule

Turning a Java getter/setter pair into a Scala property defaults to the same shape either way — a
`def` pair, bodies carried over verbatim — with collapsing the pair into a plain `var` available only
as an explicit, opt-in choice per entry. Even where that choice is made, the collapse silently
degrades back to the `def` pair whenever either of two structural guards fails:

- A `var` cannot implement an abstract accessor while also being asked to override a *concrete* one
  anywhere else in the hierarchy, in either direction — a plain field simply cannot occupy both roles
  at once.
- A `val` only compiles where nothing anywhere in the whole program ever assigns to it — Java's
  `final` keyword is irrelevant here, because a field the language allowed a constructor to assign
  more than once is still a `final` field in Java's own sense but not a `val` in Scala's.

Whether storage should end up mutable is decided from the *surviving* member the collapse settles on,
never from the original field, and a field a constructor's own synthesis has already decided to fill
positionally is left to that mechanism to give a keyword to — the property collapse has no visibility
into that decision and does not attempt to second-guess it.

Where a library's own convention is broad enough that hand-listing every getter/setter pair to convert
would amount to transcribing the whole program, the same phase also accepts a scope, in the same shape
used by retyping rules elsewhere, to derive the rest of the conversion structurally from Java's own
bean-naming convention. A hand-written entry in the table always wins over anything the scope would
otherwise derive for the same member.

## Changing an accessor's arity is an override-graph decision too

A separate phase decides whether a Java method with an empty parameter list and no side-effecting use
of its own result should be emitted parenless, matching Scala's own convention for a pure accessor.
This is not a syntactic renderer's choice: it changes the declaration's actual arity in the tree and
rewrites every call site through the symbol table, so the decision has to be made once for a whole
override component and either applied everywhere that component reaches or refused everywhere it
reaches — never for one override in isolation, which would produce a hierarchy whose members disagree
about their own arity. An abstract method is never treated as eligible on its own: an interface
method's declared arity is what a functional-interface conversion elsewhere in the pipeline reads, so
changing only the abstract half while leaving concrete implementations alone would desynchronise the
two.
