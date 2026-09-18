# Phases and policy

Every rule the engine applies is one of three kinds, and getting this classification wrong is the
single most consequential mistake a contributor can make — it is the difference between an engine
that ports Java in general and a program that happens to port one library.

## The three kinds of rule

**A universal rule** is a fact about Java and Scala, true of every codebase — array covariance,
interface constants inlining differently, unchecked raw-type conversion. It belongs in `api`,
`engine` or `frontend-spoon`, unconditionally, with no configuration at all.

**A reusable mechanism with per-library policy** applies the same mechanics to every library, but
*which* attributes, types or references get touched is a value supplied per port. This is the
overwhelming majority of what looks, at first glance, like library-specific behaviour: turning a
Java collection type into a Scala one, retyping a primitive into an opaque type, renaming a member —
the *mechanism* is universal, only the *table* of what to apply it to differs. An empty or default
policy value must make the phase a complete no-op; this is what makes it safe to add a mechanism to
the engine without changing what every existing port emits.

**A genuinely library-specific rule** is knowledge that applies to exactly one library and cannot be
expressed as a policy value passed to an existing mechanism. It is a separate rule the porting
program plugs in, not a change to the engine's own modules. Reach for this only after establishing
that no existing mechanism can be parameterised to express the same fact — most things that look
library-specific are actually the second kind, with the policy simply not yet factored out.

A shape that recurs across several libraries is not automatically a mechanism to build: where the
target language already has the abstraction (a `Map`, in the case of a keyed lookup table three
different libraries each hand-wrote their own version of), the runtime module refuses to ship a
support type for it, and the engine instead mints the small amount of per-library code the port
actually needs.

## A `Phase` is a compiler mini-phase, not a rewrite script

The transform API is deliberately shaped like a Scala 3 compiler plugin phase: `runsAfter` and
`runsBefore` declare ordering constraints against other phases by name; `transformX` hooks are
overridden only for the node kinds a phase actually touches; a phase needing a whole-program view
implements a full-control run method instead. Every hook runs with the whole `Program` in scope, so a
phase can query uses, definitions and overrides while it rewrites.

`Pipeline.order` is stable in declaration order — the position phases run in is determined by where
they were declared plus whatever ordering edges they state, resolved deterministically, never by an
implicit sort on the phase's name. The cross-reference index is rebuilt between phases, so each phase
sees a fresh, correct picture of the tree the previous phase left behind.

## Scope: how a retyping rule limits itself

A rule that changes types — a phase implementing the reusable-mechanism kind above, where the policy
retypes something — takes a `RuleScope`: either `Everywhere(except)` or `Only(include)`, matched
against a symbol's full name and cut only at an actual name separator (`.`, `$`, `#`), never at an
arbitrary substring. The scope is a set of concrete entries, not a predicate, so an entry that names
nothing real is itself reportable.

**The default for an empty scope must be chosen against what the phase did before it had a scope
parameter at all.** A phase that retypes existing declarations defaults to `Everywhere(Set.empty)` —
before scoping existed, it touched everything, so an empty scope must still touch everything. A phase
that *adds* new declarations defaults to `Only(Set.empty)` — before scoping existed, it added
nothing, so an empty scope must still add nothing. Getting this backwards turns "no configuration" in
one case into "silently do nothing" and in the other into "silently touch everything", both of which
defeat the entire purpose of an empty-parameter no-op.

A scope that excludes a location from a retyping rule does not mean the location can be quietly left
in its old, untouched shape and forgotten — a scoped-out location is still a real place where two
representations meet, and it is counted through a check dedicated to precisely this boundary, read
through the declaration that was scoped out rather than through the node (a node-driven check has
often already had its type remapped by the surrounding rewrite, and so reports nothing at exactly the
place that needs reporting).

A companion mechanism, `FlowPropagation`, grows a small hand-written seed set of "this should be
retyped" into everything reachable from it by a pure, symmetric move — an assignment, a `val x = ref`,
a `return ref`, an argument flowing into a parameter. Arithmetic is deliberately *not* one of these
edges: it breaks the chain of pure moves, which is exactly what gives a boundary coercion somewhere
principled to go. An edge kind the propagation does not recognise is always treated as a missed
opportunity to grow the seed set, never as a reason to invent a new, spurious one.

## Surface: what a phase's policy is allowed to affect

A phase's policy is *surface* precisely when a reader of the emitted code could point at a place in
the signature and say "this is here because of that policy value". Any such phase implements
`SurfacePolicy`, whose job is to let two configurations of the same phase be compared for equality by
what they would emit, not by object identity. **An empty policy key contributes nothing to that
comparison** — the same no-op rule as above, restated at the level of comparing two configurations
rather than at the level of one phase's own behaviour.

This is what lets a dependent module and its base agree, or safely disagree, about a shared phase: if
both configure a phase identically, the comparison says so; if they diverge in a way that would
change emitted signatures, that divergence is a hard failure caught before either module emits
anything, not a difference discovered later by comparing two already-compiled ports.

## How a base and a dependent compose one phase's policy

A dependent module inherits the shared parts of a base's policy rather than restating them (see
[port-map-and-dependent-modules.md](port-map-and-dependent-modules.md) for the full inherited/
not-inherited split). Sometimes, though, a dependent needs to configure the *same* phase further —
add its own entries to a rename table the base already populated, for instance. A phase opts into
this by implementing `MergeablePolicy`, declaring exactly one operation: given a later configuration
of itself, produce either a merged policy or a refusal with a reason. Only a few phases declare a
merge rule at all, because a generic union is right for some policy shapes (an ordered list of
independent entries) and silently wrong for others (two mutually exclusive scope directions cannot
both be true at once) — writing a merge rule the engine applies uniformly to every phase would smuggle
policy back into the engine itself, the exact mistake the three-kind split exists to prevent.

A phase that declares no merge rule is not disabled — it simply means that if a base and a dependent
both configure it, the two configurations must already be identical, or the run fails loudly rather
than silently choosing one and dropping the other. Two same-name phase instances that were once
allowed to sort by name and silently pick a winner are exactly the failure mode this guards against:
the correct behaviour is to detect the disagreement before any phase runs, not to let the pipeline
run to completion and disagree with itself.

## Configuration is a second door onto the same values

A `.conf` file, read through `PortConfig`, and a Scala program calling the manifest's constructors
directly build the exact same values — there is no parallel model living inside the configuration
loader. What a configuration file can express is deliberately narrower than what Scala code can
express: a phase must already be compiled and registered under a name (through `TransformFactory` and
a `ServiceLoader` lookup) before a `.conf` file can select it and hand it a value-shaped policy.
Nothing is instantiated from an arbitrary class name written in a string, and no expression language
is interpreted from configuration — the one sanctioned indirection is a factory name resolving to a
class the consumer already compiled.
