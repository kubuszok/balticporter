# Getting started

## Requirements

- JDK 22 or newer for the process that runs the port. The JDK is an *input*: what a Java member
  overrides is read from that JDK's class files, so generate and compile on the same major version.
- [coursier](https://get-coursier.io/) (`cs`) when the port resolves the upstream library's own
  dependencies for you.
- The upstream Java sources on disk — a git submodule is the usual shape, because the commit is
  recorded in every generated file's header.

## Dependency

Artifacts are published to Maven Central's snapshot repository under `com.kubuszok`, one version per
commit of Baltic Porter:

```scala
resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
libraryDependencies += "com.kubuszok" %% "balticporter-corpus" % "{{ balticporter_version() }}"
```

`balticporter-corpus` brings the engine, the Java frontend and the configurations of the libraries
ported so far. The engine alone is `balticporter-engine` plus `balticporter-frontend-spoon`.

!!! note

    The engine is compiled with Scala {{ scala.3 }}, the version sbt 2 compiles a build's `project/`
    directory with. A Scala compiler cannot read classes produced by a *newer* minor version, so the
    engine follows sbt rather than the newest Scala. The code it **generates** compiles with any
    Scala 3 your library uses.

## Describe the port

A port is a `.conf` file. Every path in it resolves against the file itself, so a port directory
can be moved freely.

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

## Run it

From any JVM entry point:

```scala
balticporter.runner.PortConfig.load(java.nio.file.Path.of("mylib.conf"), Nil).execute()
```

or without writing any code: `balticporter.runner.PortConfigMain mylib.conf`.

The Scala sources land in `target/port/src_managed/main/scala`. Next to them the run writes its
report: what was decided and why, what was refused, what could not be resolved.

## Generate on every build

The ports in production run the engine as an sbt source generator, so the generated code never
enters version control. Put the dependency on the *build's* classpath and call the engine from a
task:

```scala title="project/plugins.sbt"
resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
libraryDependencies += "com.kubuszok" %% "balticporter-corpus" % "{{ balticporter_version() }}"
```

```scala title="project/PortGen.scala"
import sbt.*
import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

object PortGen {
  def generate(base: File, log: sbt.util.Logger): Seq[File] = synchronized {
    val out = base.toPath.resolve("target/port/src_managed/main/scala")
    balticporter.runner.PortConfig.load(base.toPath.resolve("mylib.conf"), Nil).execute()
    Files.walk(out).iterator().asScala.filter(_.toString.endsWith(".scala")).map(_.toFile).toSeq
  }
}
```

```scala title="build.sbt"
Compile / sourceGenerators += Def.task {
  PortGen.generate((ThisBuild / baseDirectory).value, streams.value.log)
},
// generated code is not held to the project's own lint level
scalacOptions += "-Wconf:src=target/port/.*:s"
```

A real generator also skips the run when nothing it depends on has changed — the engine version, the
upstream commit, the configuration and the JDK major — by keeping those in a marker file beside the
output. `project/BalticPorterGen.scala` in [SGE](https://github.com/kubuszok/sge) is a complete
example, including per-platform source rows and classpath resources.

!!! tip "Never edit generated code"

    A wrong generated member is fixed where it was decided: in the port's configuration, or in the
    engine when the rule is true of every library. The next generation overwrites anything else.
