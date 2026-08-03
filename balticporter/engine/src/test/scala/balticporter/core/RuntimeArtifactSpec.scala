package balticporter.core

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** The vendored runtime text and the PUBLISHED runtime module must be the same bytes.
  *
  * This is §1.3's bug one level down. Publishing `balticporter-runtime` stops two ports from
  * carrying divergent bodies at one FQN — and would achieve nothing if the engine kept a second,
  * hand-maintained text of the same types for the vendored fallback. So the vendored copy is
  * GENERATED from `balticporter/runtime/src/main/scala` by build.sbt's resource generator, and this suite is
  * what proves the generator ran and covered everything: not "the strings look right", but "the
  * set of files and every byte of each agree with the module that is actually published".
  */
class RuntimeArtifactSpec extends munit.FunSuite:

  /** the real module's source directory, written into the test resources by build.sbt so the
    * check does not depend on where the suite is run from. */
  private val runtimeSourceDir: Path =
    val is = Option(getClass.getClassLoader.getResourceAsStream("balticporter/runtime-source-dir.txt"))
      .getOrElse(fail("balticporter/runtime-source-dir.txt is missing — core's Test resourceGenerator did not run"))
    val s = try new String(is.readAllBytes(), "UTF-8").trim finally is.close()
    Path.of(s)

  private def published: Map[String, String] =
    Files.walk(runtimeSourceDir).iterator().asScala.toList
      // `package.scala` declares no type — it carries the module's admission rule (what may be
      // added to the runtime at all) and is deliberately not a vendorable unit.
      .filter(p => p.getFileName.toString.endsWith(".scala") && p.getFileName.toString != "package.scala")
      .map(p => s"${RuntimeArtifact.Package}.${p.getFileName.toString.stripSuffix(".scala")}" -> Files.readString(p))
      .toMap

  test("every type the published module carries is vendored, and nothing else is") {
    assertEquals(RuntimeArtifact.vendored.keySet, published.keySet)
    assert(published.nonEmpty, "the runtime module has no sources — the vendoring check proves nothing")
  }

  test("the vendored text is byte-identical to the published source") {
    published.foreach { (fqn, src) =>
      assertEquals(RuntimeArtifact.sourceOf(fqn), src, s"vendored $fqn has drifted from balticporter/runtime/src/main/scala")
    }
  }

  test("the vendored source declares the type at the FQN it is filed under") {
    RuntimeArtifact.vendored.foreach { (fqn, src) =>
      val simple = fqn.substring(RuntimeArtifact.Package.length + 1)
      assert(src.startsWith(s"package ${RuntimeArtifact.Package}\n"), s"$fqn: wrong package header")
      assert(
        src.contains(s"trait $simple") || src.contains(s"class $simple") || src.contains(s"object $simple"),
        s"$fqn: no declaration of $simple in the vendored text",
      )
    }
  }

  test("the runtime artifact is version-locked to the engine") {
    assertEquals(RuntimeArtifact.version, EngineInfo.version)
    assertEquals(RuntimeArtifact.organization, EngineInfo.organization)
    assertEquals(RuntimeArtifact.artifact, "balticporter-runtime")
  }

  test("the engine version comes from the build, not from a literal") {
    // the defect this replaced: EngineInfo said 0.1.0-M0 while the build published 0.1.0-SNAPSHOT.
    assertEquals(EngineInfo.version, BuildVersion.version)
    assertEquals(EngineInfo.fingerprint, s"balticporter/${BuildVersion.version}")
  }

  test("closure follows references BETWEEN support types") {
    // JavaIterable.iterator() returns a JavaIterator: vendoring the first alone does not compile.
    val closed = RuntimeArtifact.closure(Set(s"${RuntimeArtifact.Package}.JavaIterable"))
    assert(closed.contains(s"${RuntimeArtifact.Package}.JavaIterator"), clue(closed))
  }

  test("an unknown FQN is reported, not silently dropped") {
    intercept[NoSuchElementException](RuntimeArtifact.sourceOf("balticporter.runtime.Nope"))
  }
