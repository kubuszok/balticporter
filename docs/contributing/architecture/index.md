# Engine architecture

These pages describe how Baltic Porter is built, for anyone contributing to the engine itself rather
than configuring a port of a particular library. Each page stands on its own, but reading them in
order builds up the whole picture: what the engine is and how a run flows through it, the tree every
phase operates on, how a rule is classified and configured, how one module builds on another, how
names and overrides are kept consistent, how the engine handles what it cannot translate, and how
every decision is recorded and attributed.

- **[Overview](overview.md)** — why the engine is a deterministic re-compiler rather than a
  transpiler or an LLM translator, what it deliberately refuses to be, the pipeline a run goes
  through from frontend to reports, the module layout and which module may depend on which, and the
  rule that no shared module may name a ported library in its own code.

- **[The intermediate representation](intermediate-representation.md)** — the typed, whole-program
  tree every phase and check operates on: why identity is a stable symbol rather than a name, the
  structured type algebra, the typed tree nodes themselves, the whole-program index a phase queries
  while it rewrites, and how a node's Java and Scala provenance is carried alongside it.

- **[Phases and policy](phases-and-policy.md)** — the three kinds of rule a contributor must
  correctly classify before writing one: a universal engine fact, a reusable mechanism whose policy
  is supplied per library, or a genuinely library-specific rule plugged in separately. Covers how a
  phase declares scope and policy, how phases are ordered, what makes a policy part of a port's
  "surface", and how a base module's and a dependent module's configurations of the same phase
  compose.

- **[The port map and dependent modules](port-map-and-dependent-modules.md)** — what a base module
  publishes about what it actually emitted, as opposed to what it merely intended; how a dependent
  module reads that instead of re-deriving it; the freshness checks that catch a stale or
  JDK-mismatched map; and what is inherited automatically by an extending module versus what stays
  each module's own decision.

- **[Renaming and overrides](renaming-and-overrides.md)** — the two shared building blocks behind
  every rename in the engine: the override graph that finds every declaration that must move
  together, and the member renamer that applies a rename request through it. Covers what is never
  renamed because its signature is a fact about an external class file, how a name clash is resolved
  by reading effective names ancestors-first, and the property and arity decisions built on top.

- **[Unportable constructs and refusals](unportable-constructs-and-refusals.md)** — how the engine
  refuses a construct it cannot translate faithfully instead of silently approximating it: the marker
  that lives directly in the tree, the reports and source-map correlation that make a refusal
  traceable, best-effort and preview modes for early exploration of a new library, and the difference
  catalogue that records every known Java-versus-Scala semantic gap, including which platforms an API
  is actually available on.

- **[Provenance and licensing](provenance-and-licensing.md)** — how every non-mechanical decision a
  port makes is recorded twice, once in a machine-readable log and once as a comment beside the
  generated code; how original Java comments are carried across verbatim; and why generated code lives
  in a separate, gitignored directory from hand-written shims.

## What these pages are not

These pages describe how the engine is built, not how to configure a port of a particular library —
that material lives in the user guide instead, covering getting started, writing a port
configuration, customizing translation, and reading a run's report. One piece of the user guide is
worth knowing about from here too: the reference table of Java statement and expression forms that
compile to valid but differently-behaving Scala, each with the translation the engine actually
produces. That table is the concrete, per-construct companion to the general refusal mechanism
described in [Unportable constructs and refusals](unportable-constructs-and-refusals.md) — this page
explains *how* the engine decides it cannot translate something faithfully and *how* it reports that
decision; the user guide's table is the enumeration of specific constructs that fall into that
category and exactly what is emitted for each one.

## Reading order

The pages are ordered so that each one only assumes what came before it: the overview sets up the
pipeline and module boundaries every later page refers to; the intermediate representation is the
common vocabulary every later page uses to talk about trees, symbols and types; phases and policy
explains how a rule is written and configured once that vocabulary is in hand; the port map extends
that to more than one module at a time; renaming and overrides, and unportable constructs, are both
worked examples of the phase-and-policy model applied to two of the engine's hardest problems; and
provenance and licensing closes the loop by describing how every decision made along the way is
recorded and attributed back to its origin. A contributor changing one specific mechanism can usually
start directly at the page that names it, using the overview only to place that mechanism within the
pipeline as a whole.

## A note on verification

None of these pages describe how a change is verified before it lands — that is deliberately kept
separate from the architecture itself, since the two evolve on different schedules and a
verification recipe is far more likely to change than the shape of the pipeline it is checking. In
short, a rule change is judged first against the framework's own golden corpus of accepted
translations, and second against whether every module in the corpus still compiles and still passes
its ported test suite. A contributor working on one of the mechanisms described here should expect to
run that corpus, not just a unit spec for the mechanism in isolation, before treating a change as
finished.
