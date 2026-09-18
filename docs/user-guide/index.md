# Baltic Porter

A deterministic engine for porting Java libraries to Scala 3.

Baltic Porter reads a Java code base, builds a typed model of it, applies a pipeline of translation
rules and writes Scala 3 sources that compile on the JVM, Scala.js and Scala Native. The same engine,
configured per library, is rerun whenever the upstream project or the rules change: the generated
code is a build product, never edited and never committed.

!!! warning "Pre-release"

    Snapshots are published for every commit on `master`. There is no stable release yet and the
    configuration format still moves.

## Why

A hand port of a large Java library is months of work that starts rotting the day it is finished:
upstream moves on, and every divergence the porter introduced — deliberately or not — has to be
rediscovered at the next merge. A mechanical port turns that into a build step. What is specific to
one library is written down once, as that library's configuration; what is true of Java and Scala in
general is fixed once, in the engine, for every library.

## What you get

- **Semantics, not syntax.** Java constructs that compile to *different* Scala behaviour are
  translated deliberately — reference equality, post-increment as a value, labelled jumps, `switch`
  fall-through, `try`-with-resources, class initialisers, varargs, records and many more.
- **Library policy as data.** Renames, dropped and replaced types, collection retargeting,
  nullability, opaque types, bean properties and context threading are reusable phases that take
  their policy from the port's configuration.
- **Multi-module ports.** A dependent port inherits the published surface of its base.
- **Explanations.** Every decision is written next to the generated member as a `/* porter: … */`
  note and into the run's report tables; compiler errors are attributed back to the member and the
  rule that produced it.
- **Loud refusals.** What cannot be translated faithfully is counted and reported, never guessed.
- **A licence trail.** Every generated file names its upstream project, commit and licence.
- **Tests.** JUnit suites are converted to MUnit.

## Who uses it

- [SGE](https://github.com/kubuszok/sge) — libGDX on the JVM, Scala.js and Scala Native
- [SSG](https://github.com/kubuszok/ssg) — flexmark, Liqp and friends
- [lls](https://github.com/kubuszok/lls) — libGDX's low-level collections and maths

Continue with [Getting started](getting-started.md).
