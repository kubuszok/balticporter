package balticporter.sbtgen

import java.nio.file.{Files, Path}
import balticporter.core.{EngineInfo, EnginePin, RuntimeArtifact, RuntimeMode}
import balticporter.tir.Phase
import balticporter.transform.CollectionsTransform

/** `emitPort` is the seam where the ORCHESTRATOR — not the caller — decides how the runtime
  * reaches the port. Every assertion here is about something the caller did NOT say.
  */
class SbtGenRuntimeSpec extends munit.FunSuite:

  private class Inert extends Phase:
    def name = "inert"

  private def spec = SbtGen.ProjectSpec(
    moduleName = "demo",
    organization = "com.example",
    scalaVersion = "3.8.4",
    sbtVersion = "2.0.3",
    deps = Nil,
    engineFingerprint = EngineInfo.fingerprint,
  )

  private def withRoot(f: Path => Unit): Unit =
    val dir = Files.createTempDirectory("bp-sbtgen")
    try f(dir)
    finally Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)

  test("a run whose phases need the runtime declares the dependency the caller never mentioned") {
    withRoot { root =>
      val plan = SbtGen.emitPort(root, spec, List(new Inert, new CollectionsTransform))
      val build = Files.readString(root.resolve("build.sbt"))
      assert(build.contains(s""""${RuntimeArtifact.organization}" %% "${RuntimeArtifact.artifact}" % "${RuntimeArtifact.version}""""), clue(build))
      // …and did NOT also vendor the sources: exactly one delivery.
      assert(!Files.exists(SbtGen.managedMain(root).resolve("balticporter/runtime/JavaIterator.scala")))
      assertEquals(plan.mode, RuntimeMode.Dependency)
    }
  }

  test("a run whose phases need nothing gets no dependency and no sources") {
    withRoot { root =>
      SbtGen.emitPort(root, spec, List(new Inert))
      val build = Files.readString(root.resolve("build.sbt"))
      assert(!build.contains(RuntimeArtifact.artifact), clue(build))
      assert(!build.contains("libraryDependencies"), clue(build))
    }
  }

  test("--vendored-runtime adds no dependency and carries the sources in the PLAN, unwritten") {
    withRoot { root =>
      val plan = SbtGen.emitPort(root, spec, List(new CollectionsTransform), RuntimeMode.Vendored)
      val build = Files.readString(root.resolve("build.sbt"))
      assert(!build.contains(RuntimeArtifact.artifact), clue(build))
      // eleven — the phase's whole `runtimeTypes`; `RuntimePlanSpec` enumerates them and says why.
      assertEquals(plan.sources.size, 11)
      assertEquals(plan.sources.get(s"${RuntimeArtifact.Package}.JavaIterator"),
                   Some(RuntimeArtifact.sourceOf(s"${RuntimeArtifact.Package}.JavaIterator")))
      // …and this did NOT write them. The build generator cannot know which source set the run is
      // producing, and it guessed `main`: a `sourceSet = Test` port with a generated project
      // vendored the whole runtime into BOTH trees, defining every support type twice. The run
      // writes them into its own `outDir` (`PortRunProjectSpec`), which is the one place that knows.
      assertEquals(Files.exists(SbtGen.managedMain(root).resolve("balticporter")), false)
      assertEquals(Files.exists(SbtGen.managedTest(root).resolve("balticporter")), false)
    }
  }

  test("the generated project PINS the engine that produced it, checkably") {
    withRoot { root =>
      SbtGen.emitPort(root, spec, List(new CollectionsTransform))
      val pin = EnginePin.read(root).getOrElse(fail(s"${EnginePin.fileName} was not written"))
      assertEquals(pin.engineVersion, EngineInfo.version)
      assertEquals(pin.runtimeVersion, RuntimeArtifact.version)
      assertEquals(pin.runtimeMode, "dependency")
      assertEquals(EnginePin.check(root), Right(Some(pin)))
      assert(Files.readString(root.resolve("build.sbt")).contains(s"Engine ${EngineInfo.version}"))
    }
  }

  test("emission stays byte-deterministic with the pin and the derived dependency in it") {
    withRoot { a =>
      withRoot { b =>
        SbtGen.emitPort(a, spec, List(new CollectionsTransform))
        SbtGen.emitPort(b, spec, List(new CollectionsTransform))
        List("build.sbt", EnginePin.fileName, ".gitignore", "project/build.properties").foreach { f =>
          assertEquals(Files.readString(a.resolve(f)), Files.readString(b.resolve(f)), s"$f is not deterministic")
        }
      }
    }
  }

  test("hand-declared deps survive alongside the derived one, in that order") {
    withRoot { root =>
      val s = spec.copy(deps = List(SbtGen.Dep("org.example", "thing", "1.0")))
      SbtGen.emitPort(root, s, List(new CollectionsTransform))
      val build = Files.readString(root.resolve("build.sbt"))
      assert(build.indexOf("\"org.example\" % \"thing\"") < build.indexOf(RuntimeArtifact.artifact), clue(build))
    }
  }

  // A coordinate published outside the default resolver set resolves NOTHING, and the failure is a
  // resolver error in somebody else's build with no finding anywhere here. The URL therefore travels
  // WITH the coordinate (`ArtifactDep.resolver`) and this is where it lands.
  test("a dependency's own repository reaches the generated build, ONCE, named from its URL") {
    withRoot { root =>
      val snaps = "https://central.sonatype.com/repository/maven-snapshots"
      val s = spec.copy(deps = List(
        SbtGen.Dep.of(balticporter.catalog.ArtifactDep("com.example", "a", "1.0-SNAPSHOT",
          balticporter.catalog.CrossKind.Platform, Some(snaps))),
        SbtGen.Dep.of(balticporter.catalog.ArtifactDep("com.example", "b", "1.0-SNAPSHOT",
          balticporter.catalog.CrossKind.Platform, Some(snaps))),
      ))
      SbtGen.emit(root, s)
      val build = Files.readString(root.resolve("build.sbt"))
      val entry = s"""resolvers += "central.sonatype.com-repository-maven-snapshots" at "$snaps""""
      assertEquals(build.linesIterator.count(_ == entry), 1, clue(build))
      // …and the cross kind is still the artifact's own: `%%%`, not the `%%` a JS build cannot use.
      assert(build.contains(""""com.example" %%% "a" % "1.0-SNAPSHOT""""), clue(build))
    }
  }

  test("…and a spec whose coordinates are all on the default resolvers writes no block at all") {
    withRoot { root =>
      val s = spec.copy(deps = List(SbtGen.Dep("org.example", "thing", "1.0")))
      SbtGen.emit(root, s)
      assert(!Files.readString(root.resolve("build.sbt")).contains("resolvers"))
    }
  }
