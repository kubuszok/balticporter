package balticporter.corpus.gltf

import balticporter.corpus.ClasspathCache
import balticporter.core.{FrontendConfig, Provenance, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}

/** Port gdx-gltf's own JUnit suite through the same pipeline as `gltf/src`.
  *
  *   corpus/runMain balticporter.corpus.gltf.GltfTestMigrate [--determinism=full]
  *
  * ==A TEST FILE IS NOT A TEST (the jbump/anim8 lesson, one more time)==
  * `gltf/test` holds SEVEN Java files and exactly EIGHT `@Test` methods, all of them in ONE file.
  * The other six — `Benchmark`, `ExportOBJTest`, `ExportSharedIndexBufferTest`, `ImportGLTFTest`,
  * `SharedTextureTest`, `ProceduralExamples` — are `extends Game` / `extends ApplicationAdapter`
  * classes with a `public static void main` that opens a window through
  * `com.badlogic.gdx.backends.lwjgl.LwjglApplication`. That backend is not in `gdx/src`, is not
  * ported, and is the only import in the whole tree that `gdx/src` cannot resolve. They are demos
  * with `Test` in the name, and counting the seven files would have claimed a suite this library
  * does not have.
  *
  * So [[testFiles]] names the ONE file, and the `gltf-measure` lane re-derives the 8 from the whole
  * `gltf/test` tree with `java_test_count` rather than trusting this comment: the day upstream
  * adds a real `@Test` to another file, the lane says so.
  *
  * ==What the eight tests cover==
  * `Attribute.compareTo` on four of gdx-gltf's sixteen attribute types (`FogAttribute`,
  * `PBRIridescenceAttribute`, `PBRVolumeAttribute`, `PBRTextureAttribute`), each in a
  * same/different pair over `copy()`. Narrow, but it is the exact protocol libGDX's material
  * sorting depends on and it needs no GL context, no asset and no backend — which is why it is the
  * one thing upstream itself could test.
  *
  * A DEPENDENT of a DEPENDENT: the suite resolves against `gltf/src` (ported by [[GltfMigrate]])
  * which itself resolves against `libgdx/gdx/src` (ported by `LibgdxCoreMigrate`).
  * `PortManifest.baseChain` carries both, so the drops, renames and surface phases of BOTH
  * ancestors are inherited rather than restated.
  */
object GltfTestMigrate:

  /** The one file in `gltf/test` that is a suite. See the class comment for the other six. */
  val testFiles: List[String] =
    List("net/mgsx/gltf/scene3d/attributes/AttributesCompareTest.java")

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val gltfSrc  = repoRoot.resolve("../sge/original-src/gdx-gltf/gltf/src").normalize
    val testRoot = repoRoot.resolve("../sge/original-src/gdx-gltf/gltf/test").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    // Not a glob over the tree: six of the seven files are backend-driven demos that no resolution
    // root can supply (`com.badlogic.gdx.backends.lwjgl`). Naming the file makes the exclusion a
    // statement rather than a silent filter — and the lane counts `@Test` over the WHOLE tree, so
    // a suite appearing in another file is reported, not absorbed.
    val missing = testFiles.filterNot(f => Files.exists(testRoot.resolve(f)))
    if missing.nonEmpty then
      System.err.println(s"[gltf-test] named test file(s) not found under $testRoot: ${missing.mkString(", ")}")
      sys.exit(1)

    PortRun(
      label     = "sge-gltf-test",
      portRoot  = repoRoot.resolve("ported/sge-gltf"),
      sourceSet = SourceSet.Test,
      // JUnit 4.12 — gdx-gltf's OWN version, from `build.gradle`'s `junitVersion`. On the FRONTEND
      // classpath so Spoon resolves `org.junit.Assert`/`org.junit.Test`; an unresolved import does
      // not fail, it resolves WRONGLY (ENGINE-LIMITS.md §6).
      frontend  = FrontendConfig(testRoot, testFiles, GltfClasspath.resolve(repoRoot),
                                 resolutionRoots = List(gltfSrc, gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(GltfPolicy.test(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "gdx-gltf",
        upstreamCommit   = VendoredCommit.of(testRoot),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gltf/test",
        sourceRoot       = testRoot.toString,
      )),
      // The MAIN source set of this module is compiled beside this one and already resolves the
      // runtime; vendoring again would define every support type twice.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gltf-measure",
    ).execute()

/** gdx-gltf's TEST-scope dependency, for shadow-class resolution only.
  *
  * JUnit 4.12 — the version `gdx-gltf/build.gradle` declares, not the one that looks current
  * (`AshleyClasspath` records what guessing a modern version costs). `TestFrameworkTransform`
  * converts the JUnit surface, so nothing from this jar survives into the emitted Scala; it is
  * needed at FRONTEND time only, so that `Assert.assertEquals` resolves to a static method on a
  * known type rather than to an unqualified call on the suite itself.
  *
  * Cached, like `AshleyClasspath`, so a run does not depend on the network.
  */
object GltfClasspath:

  /** the version `gdx-gltf/build.gradle` declares. */
  val Coordinates: List[String] = List("junit:junit:4.12")

  def resolve(repoRoot: Path): List[Path] =
    // through the shared mechanism, which also RECORDS these coordinates beside the cache: reused
    // for a different set, a cached line resolves this suite's imports against a version the port
    // no longer declares — and an import that resolves WRONGLY does not fail, it emits nonsense
    // (`ClasspathCache`, which is also where the fetch failure is fatal rather than empty).
    ClasspathCache.entries(repoRoot.resolve("out/gltf-test-classpath.txt"), "gltf-test", Coordinates)
