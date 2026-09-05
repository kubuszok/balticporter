package balticporter.corpus.anim8

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.LlsClasspath
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **anim8-gdx** (`src/main/java`, 16 types — GIF/PNG8/APNG writers, dithering/
  * palette-reduction machinery) through the TIR. Difficulty is per-LINE (19,594 lines, `PNG8`
  * alone 8,351): enormous constant data and bulk bit-pattern arithmetic. A DEPENDENT port:
  * `gdx/src` a RESOLUTION root, policy [[LibgdxPolicy.core]] EXTENDED (§1.5). No upstream suite;
  * evidence is the hand-written suite in `ported/sge-anim8/src/test/scala` (CLAUDE.md §3). */
object Anim8Migrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/anim8-gdx/src/main/java").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-anim8",
      portRoot  = repoRoot.resolve("ported/sge-anim8"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, LlsClasspath.entries(repoRoot), resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(Anim8Policy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "anim8-gdx",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "src/main/java",
        sourceRoot       = base.toString,
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just anim8-measure",
    ).execute()

/** anim8-gdx's per-library policy -- a DEPENDENT of libGDX core's. `dropTypes`/`dropMethods`/
  * `packageRenames`/signature-affecting phases are INHERITED, not restated; `inject` is NOT
  * inherited (exactly one module ships each replacement file). */
object Anim8Policy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-anim8",
      governs = Set("com.github.tommyettinger.anim8"),
      // sge puts anim8 at sge.anim8; libGDX's com.badlogic.gdx -> sge is INHERITED, not restated.
      packageRenames = Map("com.github.tommyettinger.anim8" -> "sge.anim8"),
      // ONE PER-LOCATION SELECTION on `jdk-surface` (`DESIGN.md` §8.16/§8.21): 195 sites of
      // `java.util.Arrays.fill(<float[]>, 0, w, 0)` in dithering loops -- a primitive-array
      // receiver with no scala-collection image to map onto, so the phase's silence is coverage
      // by coincidence, examined and accepted. The other six `Arrays` rows stay: same shape, not
      // a claim about the family.
      resolutions = Map(
        "java.util.Arrays#fill(float[],int,int,float)" -> "accept-jdk-member",
      ),
      surface = List(
        // LAST, deliberately (as AshleyPolicy): reads what the BASE actually emitted; must run
        // after any seam re-pointing such a reference.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
      // THE REFERENCE HAND PORT for sge-anim8. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge-extension/anim8/src/main/scala").normalize))),
    ))
