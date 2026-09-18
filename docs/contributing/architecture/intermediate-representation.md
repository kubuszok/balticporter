# The intermediate representation

Every phase and every check operates on one typed, whole-program tree: the TIR. It is built once by
a frontend and then rewritten in place by an ordered sequence of transform phases. This page
describes its four parts — symbols, types, trees, and provenance — and how a declaration's
non-mechanical history is recorded alongside it.

## Symbols and identity

Every declaration gets a `Symbol`: an interned `SymId` (a stable identity, never a string), a
resolved `TypeRepr`, an owner, a set of flags, an `Origin`, and an open tag slot a rule can use for
its own bookkeeping. Every reference node in the tree points at a `Symbol`, not at a name — so a
rewrite that renames a declaration, or a query that asks "where is this symbol used", keys on
identity and never has to re-resolve a name after an earlier phase already changed it.

This matters because names are exactly the thing later phases move. A package rename, a member
rename, a type promoted out of a nested position — all of these change what a declaration is
*called* without changing what it *is*. A rule keyed on the current spelling would have to re-derive
which declaration it means every time an earlier phase ran; a rule keyed on `SymId` does not.

An external symbol (something the program references but does not declare — a JDK type, a
third-party library member) is interned lazily, with no owner and no declaration behind it.
**Ownership is structural, not a stored flag**: a symbol is owned if and only if climbing its chain
of owners eventually reaches one of the program's own compilation units. This one rule answers every
"is this mine to rewrite" question a phase needs to ask, and it is asked fresh every time, from the
symbol's actual owner chain — never cached as a boolean decided once and reused, and never derived
from a name prefix.

### Overload identity: the descriptor

A `Symbol` also carries an optional `Descriptor` — the source-level spelling of its parameter list,
such as `(int, String)`. This is deliberately the spelling an author would write today, not the
erased, JVM-level signature: within one overload set the source spelling is already unambiguous
(Java forbids two overloads with the same erasure), and it is the form every manifest key and every
policy table already uses. A policy key that names a member is resolved once, before any phase runs,
by a `PolicyBinder`, which turns a written key into a bound `SymId` — so a phase never has to compare
raw strings against a symbol table itself, and a key that matches nothing, matches more than one
member, or names a member no phase can touch is reported as such rather than silently doing nothing.

A field has no descriptor; that is not a gap, just a reflection of the fact that fields have no
parameter list. A member the engine itself creates (a synthetic accessor, a promoted constructor
parameter) gets a descriptor in the same grammar but is never mistaken for something the frontend
actually saw in the Java source — a policy key that tries to name a synthetic member gets its own,
distinct refusal rather than being treated as a typo.

## Types

Types are a structured algebra, `TypeRepr`, never a flat string: applied type constructors,
intersections with a resolved linearisation of parents, self-types, F-bounded and higher-kinded type
parameters, wildcards with their bounds, path-dependent and singleton types, and method and
polymorphic-method signatures. Every node in the tree carries its own resolved type, and every type
reference points at a type symbol rather than repeating a name. Transforms and the emitter read this
type directly; nothing re-infers a type from context during a later phase, which is what lets a
retyping rule move both sides of a slot (a declaration and every use of it) in lockstep instead of
guessing at one side from the other.

## Trees

The tree itself is a typed, Scala-shaped structure — nodes such as `ValDef`, `DefDef`, `Apply`,
`Select`, `Ident`, `New` and `Lambda` — each carrying its resolved type, an optional symbol, and an
`Origin` pointing back at the Java that produced it. The shape deliberately mirrors what a Scala 3
compiler plugin sees through its own reflection API, because the whole design of a transform phase
(below) is modelled on a compiler mini-phase, not on a general-purpose AST-rewriting library.

Every phase that walks the tree does so through the shared `StandardTraversal`, never a private
recursion written per phase. This is what makes it possible to state, and check, that every node kind
the frontend can produce is visited somewhere: a private walk that forgets one node kind is invisible
until something inside that kind goes untranslated, while a shared traversal gives every phase the
same blind spots to close once.

## Program: the whole-program index

A `Program` bundles every compilation unit together with a `SymbolTable` and a kinded cross-reference
index (`XrefIndex`) recording, for every symbol, its definition, its uses grouped by kind, and its
callers. `usagesOf`, `definitionOf`, `callersOf`, `symbolAt` and `typeOf` are the query surface every
phase and check uses to ask a whole-program question — "does anything still call this member", "what
overrides this declaration" — while it rewrites. The index is rebuilt between phases, so a query
always answers against the tree as the *previous* phase left it, never a stale snapshot from several
phases back.

## Origin and provenance

Every tree node carries an `Origin`: the Java file path and position that produced it. After
emission, a node also carries the Scala position it was rendered at. This is the mechanism behind
every "which line produced this" and "which Java produced this Scala" question a report answers —
diagnostics are never matched up by re-opening an emitted file and reading it, only by following the
origin recorded on the node itself. See
[provenance-and-licensing.md](provenance-and-licensing.md) for how this is used to correlate compiler
and test output, and how a comment from the original source is carried across.

## Decisions belong beside the symbol, not inside it

A declaration's *mechanical* facts — its type, its tree shape, its origin — live on the symbol and
the node. Its *non-mechanical* facts — why it was renamed, why it was dropped, why its body was
replaced — are a separate, append-only log entry per declaration, one `Decision` per change, keyed on
the declaration reached through the cross-reference index rather than by re-deriving "the current
definition" from a name. Keeping decisions out of the symbol model itself is what lets the model stay
a plain fact about the program, queryable by every phase, while the decision log stays a record of
*this particular run's* choices. The full shape of that log, and the comment written beside the code
that mirrors it, is described in
[provenance-and-licensing.md](provenance-and-licensing.md).

## A construct with no faithful translation is still a first-class tree node

Where a phase can only translate a construct approximately, the tree carries that fact directly, as a
wrapper node around the approximated term, rather than in a side table keyed by position (positions
do not survive a rewrite) or a silent, undocumented best guess. This keeps the traversal, and every
later phase, aware that the node underneath is not to be trusted as a normal translation. The full
mechanism — what the wrapper carries, how it is reported, and how a shipping run refuses to emit one —
is described in
[unportable-constructs-and-refusals.md](unportable-constructs-and-refusals.md).
