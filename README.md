# Baltic Porter

A deterministic engine for porting Java libraries to Scala 3.

Baltic Porter reads a Java code base, builds a typed model of it, applies a pipeline of translation
rules and writes Scala 3 sources that compile on the JVM, Scala.js and Scala Native. It is a
*framework for porting*, not a one-off converter: the same engine, configured per library, is rerun
whenever the upstream project or the rules change, and the generated code is a build product — never
edited, never committed.

It exists because hand ports rot. [SGE](https://github.com/kubuszok/sge) (libGDX),
[SSG](https://github.com/kubuszok/ssg) (flexmark, Liqp and friends) and
[lls](https://github.com/kubuszok/lls) were first ported by hand; they are now generated from the
upstream sources on every build.

> **Status:** pre-release. Snapshots are published for every commit on `master`; there is no
> stable release and the configuration format still moves.

## What it does

- **Translates semantics, not syntax.** Java constructs that compile to *different* Scala behaviour
  are handled deliberately: `==` on references, `x++` as a value, `break`/`continue` and labelled
  jumps, `switch` fall-through and fall-out, `try`-with-resources, class initialisers, varargs,
  compound assignment at side-effecting lvalues, records, reified casts over retyped collections.
  The full catalogue lives in `balticporter.catalog.Differences`.
- **Separates universal rules from library policy.** Facts about Java and Scala live in the engine.
  Reusable mechanisms (renames, collection retargeting, nullability, opaque types, bean properties,
  context threading, …) are phases that take their policy as a value. Knowledge about one library is
  that library's configuration — the engine never names a ported library.
- **Ports modules that depend on each other.** A dependent port inherits its base's published
  surface instead of restating it, and disagreements are reported as findings.
- **Explains itself.** Every decision is recorded per declaration (`decisions.tsv`) and written
  next to the generated member as a `/* porter: … */` note; errors in generated code are attributed
  back to the member and the rule that produced it.
- **Refuses loudly.** What it cannot translate faithfully is counted and reported, not guessed.
- **Keeps the licence trail.** Every generated file carries a header naming the upstream project,
  commit and licence, and the upstream notice files are copied next to the output.
- **Ports tests too.** JUnit suites are converted to MUnit, including per-test instance state,
  `@Before`, expected exceptions and rules.

Besides the Java frontend (built on [Spoon](https://spoon.gforge.inria.fr/)) there are experimental
TypeScript and Dart frontends.

## Quick start

Artifacts are published to Maven Central's snapshot repository under `com.kubuszok`, versioned by
commit: `<40-character commit hash>-SNAPSHOT`.

```scala
resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
libraryDependencies += "com.kubuszok" %% "balticporter-corpus" % "<commit>-SNAPSHOT"
```

Describe the port in a `.conf` file (paths resolve against the file itself):

```hocon
label = "mylib"

input {
  sourceRoot    = "upstream/src/main/java"
  classpathFile = "target/mylib-classpath.txt"   # the upstream library's own dependencies
}

output {
  portRoot  = "target/port"
  sourceSet = "main"
}

manifest {
  name    = "mylib"
  governs = ["com.example.mylib"]
  packageRenames { "com.example.mylib" = "mylib" }
}
```

and run it:

```scala
balticporter.runner.PortConfig.load(java.nio.file.Path.of("mylib.conf"), Nil).execute()
```

The Scala sources land in `target/port/src_managed/main/scala`, and a report directory next to them
holds what the run decided, what it refused and what it could not resolve. Wire the call into an sbt
`sourceGenerator` to regenerate on every build — that is how SGE, SSG and lls use it
(`project/BalticPorterGen.scala` in each).

## Modules

| module | what it is |
|---|---|
| `balticporter-api` | the typed intermediate representation, manifests, the catalogue of Java/Scala differences |
| `balticporter-frontend-spoon` | Java sources → the model |
| `balticporter-engine` | translation phases, checks, the Scala emitter, the run and its reports |
| `balticporter-runtime` | the small support library generated code may depend on (JVM, Scala.js, Scala Native) |
| `balticporter-testkit` | helpers for testing a translation rule against inline Java |
| `balticporter-corpus` | the port configurations of the libraries ported so far, with the hand-written files they inject |
| `balticporter-frontend-ts`, `balticporter-frontend-dart` | experimental non-Java frontends |

## Working on a port with Claude Code

The repository is also a [Claude Code plugin marketplace](.claude-plugin/marketplace.json). The
`balticporter` plugin gives an agent working in a *consuming* repository the procedures it needs
(how generation is wired, tracing a generated defect back to the rule that produced it, bumping the
pinned engine version, the local gate before a push) and guard hooks for the mistakes that cost the
most time. Enable it in the consumer's `.claude/settings.json`:

```json
{
  "extraKnownMarketplaces": { "balticporter": { "source": { "source": "github", "repo": "kubuszok/balticporter" } } },
  "enabledPlugins": { "balticporter@balticporter": true }
}
```

## Building

sbt 2, JDK 22. `sbt --client test` runs every module's suite; nothing outside this repository is
needed.

## Licence

Apache License 2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE). Generated code is a derivative of
the sources it was generated from and keeps *their* licence.
