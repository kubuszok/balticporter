package balticporter.corpus.libgdx

import balticporter.corpus.JnigenClasspath
import balticporter.core.{FrontendConfig, PortManifest, Provenance, ResourceTree, RuntimeMode}
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.corpus.lls.{LlsMigrate, LlsPolicy}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Rung L0 of the libGDX ladder (PROGRESS.md §13.29) ON THE LLS BASE: core minus the utilities family,
  * a DEPENDENT of `ported/lls` (CLAUDE.md §1.5), with no policy of its own — its compile count is the
  * honest measure of Java-as-Scala over the base's decisions, before any of core's own. */
object LibgdxL0Migrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    // the utilities family is the lls port's (PROGRESS.md §13.29): its files are the BASE's units,
    // resolved through `gdx/src` and never emitted twice.
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .filterNot(LlsMigrate.Files.toSet)
      .toList.sorted

    PortRun(
      label     = "sge-l0",
      portRoot  = repoRoot.resolve("ported/sge-l0"),
      sourceSet = SourceSet.Main,
      // gdx-jnigen-loader carries `SharedLibraryLoader`, which `gdx/src` references and no
      // longer declares; without it on the classpath Spoon resolves no declaration for it.
      frontend  = FrontendConfig(base, files, JnigenClasspath.entries(repoRoot), resolutionRoots = List(base)),
      phases    = Nil,
      manifest  = Some(LibgdxLadder.universal(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "libGDX",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gdx/src",
        sourceRoot       = base.toString,
      )),
      runtimeMode = RuntimeMode.Vendored,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gdx-l0-measure",
    ).execute()

object LibgdxLadder:

  /** L0's manifest: a dependent of the lls port carrying the universal facts only. `packageRenames`
    * for the rest of core (the base's `utils`/`math -> lowlevel.*` are inherited, longest prefix
    * wins); the `List` rename keeps `scala.List` out; `MutableParamsTransform` is inherited from the
    * base. No drop, inject, resolutions or parity (PROGRESS.md §13.29). */
  def universal(repoRoot: Path): PortManifest =
    LlsPolicy.core(repoRoot, LlsPolicy.DefaultRungs).extendedBy(PortManifest(
      name           = "sge-l0",
      governs        = Set("com.badlogic.gdx"),
      packageRenames = Map("com.badlogic.gdx" -> "sge"),
      typeRenames    = Map("com.badlogic.gdx.scenes.scene2d.ui.List" -> "SgeList"),
      resources      = List(ResourceTree(
        root  = repoRoot.resolve("../sge/original-src/libgdx/gdx/res").normalize,
        files = List(
          "com/badlogic/gdx/utils/lsans-15.fnt", "com/badlogic/gdx/utils/lsans-15.png",
          "com/badlogic/gdx/graphics/g3d/shaders/default.vertex.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/default.fragment.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/depth.vertex.glsl",
          "com/badlogic/gdx/graphics/g3d/shaders/depth.fragment.glsl"))),
      dependencies   = List(balticporter.catalog.ArtifactDep("com.badlogicgames.gdx", "gdx-jnigen-loader", "2.5.2",
                                                             balticporter.catalog.CrossKind.Java)),
    ))
