# The port map and dependent modules

Some libraries build on top of others: a second library's Java sources reference the first library's
classes directly. Baltic Porter ports such a dependent library as its own module, configured with a
base module it extends. This page describes what the base publishes, how the dependent reads it, and
the checks that catch the two disagreeing.

## The problem re-deriving intent does not solve

A dependent module's frontend only ever parses *Java* — its own upstream sources, plus the base
library's upstream Java sources for type resolution. It never reads the base module's *emitted
Scala*. Left there, a dependent can only re-derive what it believes the base intended: it inherits
the base's `PortManifest`, runs the same configured phases the base declared, and a consistency check
confirms the two runs agree on what they *intended* to do. That answers "did the two modules
configure this the same way", never "what did the base module actually produce" — and those can
differ. A base
module might drop a type the dependent still has a live call into; a nested type might get merged or
renamed in a way the dependent's own re-derivation does not reproduce exactly; the base's configured
phases might simply not have run over that particular declaration this time.

## The port map: what a module actually did

Each module publishes a port map at the end of its run: a table joining the upstream Java surface to
what this port actually emitted. Every type and member gets exactly one of five dispositions:

| disposition | meaning |
|---|---|
| `ported` | translated mechanically; the map records the emitted fully-qualified name and signature |
| `renamed` | ported, at a different fully-qualified name |
| `substituted` | dropped and replaced at the same name by hand-written, injected Scala |
| `dropped` | not emitted and not replaced — every reference to it must be rewritten away |
| `added` | present in the port, absent from the upstream Java |

A member also records whether its *body* was substituted even when its signature did not change,
because a caller needs to know when behaviour, not just shape, has moved. The whole map is a
projection over artifacts that already exist from the run — the source map, the list of injected
files, the substitution table, the member digests — published under one declared, versioned schema
rather than as a second, independently-authored fact about the module.

## What reading the map buys a dependent

A dependent that reads the base's port map, instead of only its manifest, gets three things a
manifest alone cannot give it: call sites into a dropped or body-substituted base member get rewritten
mechanically, carrying the base's own stated reason; renamed base types are followed by the same
longest-matching-prefix rule the dependent already uses for its own package renames; and any gap
between what the base *intended* (its manifest) and what it *actually shipped* (a phase
misconfiguration, an unexpected drop, an emitted spelling nobody predicted) becomes visible, because
the map records output, not intent. A base module that changes its published surface breaks its
dependents loudly, at their very next run, instead of silently.

## Freshness

The map's header carries a digest over every upstream Java path it attributes a member to. A
dependent recomputes the same digest and gets one of four answers:

| answer | meaning | what the dependent does |
|---|---|---|
| `Fresh` | engine version, sources, policy and JDK all match | uses the map |
| `Stale` | the engine, sources or manifest have since changed | refuses the map, reports it, falls back to re-deriving |
| `JdkMismatch` | the map was published against a different JDK | refuses the map, reports it, and the run stops |
| `Unverified` | no fingerprint recorded, or the sources sit outside this run's resolution roots | uses the map anyway, but reports that it could not verify it |

A JDK mismatch is treated more strictly than a stale map, because the other three checks only need
the base to be re-run — a JDK mismatch means the base's already-emitted Scala came from class files
this run cannot see at all, and no amount of re-deriving reproduces that. An empty base manifest is
the documented way to say "this resolution root is not itself a ported module"; it is exempt from
freshness checking and claims no namespace of its own.

A map-consuming phase always runs last in a dependent's own configured phases, because it is
answering a question about whatever remains *after* every one of this module's own rules has already
applied — the same position a portability check occupies for the same reason.

## What the port map does not close

Member *signatures* are not compared by the map — its join key is the emitted signature with renames
already reversed, so the hole this closes is a name change, and the hole this leaves open is a
retyping that changes only a parameter's type. A dependent case where more than one base shares a
common ancestor is not exercised by the current corpus and remains untested.

## The published surface: beyond drops and renames

The port map answers "what happened to this declaration", but a dependent's own configured phases
often need a structural answer about a declaration they do not own at all: is this type mine, is it
the base's, or is it something neither of us declared (a JDK type, a third-party type)? Left
unanswered structurally, a rule meant to distinguish "my own declarations" from "everything else" ends
up treating a base module's declarations the same as a JDK type — usually the wrong answer, because
agreeing with the base about a shared declaration's shape is exactly what keeps the two ports
compiling together.

A `Surface` view answers this with one of three results — `Own`, `Published`, or `Unknown` — and an
`Unknown` that would have shaped emitted text fails the run outright rather than falling back to a
guess. The base's own published constructor signatures and member renames are part of what a
dependent reads through this view, so that a dependent's own retyping and rename phases follow the
base's actual choices for declarations it does not itself emit, instead of re-deriving a shape that
might not agree.

## What is inherited and what is not

A dependent extends a base by composing manifests (`base.extendedBy(dependent)`), never by copying or
restating the base's policy. Facts about the *shared surface* — which types and members the base
dropped or renamed, how its packages map — are inherited automatically, because an extension resolves
against the base's own Java, and getting any of this wrong means the two ports simply do not compile
against each other. Facts about *this module's own build* — its own source set, its own frontend, its
own list of injected files, which platforms it targets — are never inherited, because widening or
narrowing them is a decision only the dependent itself can make (targets in particular may only ever
be narrowed by a dependent, never widened, since a wider dependent would depend on emitted Scala
nobody has checked against that platform at all).
