package balticporter.corpus.gltf

import balticporter.core.{FrontendConfig, ParityRef, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **gdx-gltf** (`gltf/src`, 135 types / 11,307 lines — a glTF 2.0 loader, exporter and
  * PBR rendering pipeline for libGDX) through the TIR. Largest port after libGDX core, first
  * whose difficulty is INHERITANCE DEPTH: 135 types stacked on libGDX's 3D pipeline, every
  * parent EMITTED Scala this run never sees (§1.5). A DEPENDENT port: `gdx/src` a RESOLUTION
  * root, [[LibgdxPolicy.core]] EXTENDED. Scope: `gltf/src` plus one real test file (see [[GltfTestMigrate]]). */
object GltfMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/gdx-gltf/gltf/src").normalize
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "sge-gltf",
      portRoot  = repoRoot.resolve("ported/sge-gltf"),
      sourceSet = SourceSet.Main,
      // libGDX core is a RESOLUTION root, compiled together by LibgdxCoreMigrate.
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(GltfPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "gdx-gltf",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        sourcePathPrefix = "gltf/src",
        sourceRoot       = base.toString,
      )),
      // NOT Vendored: LibgdxCoreMigrate already vendors the collection shims into this
      // module.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gltf-measure",
    ).execute()

/** gdx-gltf's per-library policy -- a DEPENDENT of libGDX core's. `dropTypes`/`dropMethods`/
  * `packageRenames`/signature-affecting phases are INHERITED, not restated; `inject` is
  * NOT inherited (exactly one module ships each replacement file). */
object GltfPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "sge-gltf",
      governs = Set("net.mgsx.gltf"),
      // sge puts gdx-gltf at sge.gltf, package for package. libGDX's own
      // com.badlogic.gdx -> sge is INHERITED, not restated.
      packageRenames = Map("net.mgsx.gltf" -> "sge.gltf"),
      // ONE PER-LOCATION SELECTION (`DESIGN.md` §8.16/§8.21): CtorFunnel promoted the NILARY
      // GLTFLoaderBase() (java body `this(null)`), so its five statements run on EVERY
      // construction path. `promotionEscapes` deliberately does not decide whether this MATTERS
      // (C6/C7) — READ at this site: all four discarded objects have no-arg constructors
      // initialising only their own empty containers, so nothing observes the waste.
      resolutions = Map(
        "net.mgsx.gltf.loaders.shared.GLTFLoaderBase#<init>(TextureResolver)" -> "accept-promoted-body",
      ),
      // 3.1az: GLTFMorphTarget extends ObjectMap<String, Integer>, and lls ObjectMap is
      // final -- the hand port extends HashMap[String, Int] instead (Json.Serializable is
      // dead, Json is dropped by the base); the injected replacement reproduces that shape
      // (K37 SubclassOfTarget, §1c).
      dropTypes = Set("net.mgsx.gltf.data.geometry.GLTFMorphTarget"),
      // gdx-gltf's OWN replacements. `inject` is not inherited — exactly one module ships each
      // replacement file, and libGDX core ships the ones for the types IT dropped.
      inject  = List(repoRoot.resolve("balticporter/corpus/gltf-overrides")),
      surface = List(
        // THE THREE REFLECTIVE SITES are a GWT workaround, not a genuine need: two say so in an
        // upstream comment. CLAUDE.md §3.5: the reference hand port SOLVED both the same way,
        // making the direct call the facade was emulating (WebGL guard kept). KEY is upstream
        // namespace (matched before rename); BODY is the port's FINAL namespace (spliced
        // verbatim).
        new balticporter.transform.MethodBodyTransform(Map(
          // 1. `new Pixmap(bytes, off, len)`, reached through `getConstructor(…).newInstance(…)`.
          "net.mgsx.gltf.loaders.shared.texture.PixmapBinaryLoaderHack#load" ->
            """{
              |  if (sge.Gdx.app.`type` eq sge.Application.ApplicationType.WebGL) {
              |    throw new sge.gltf.loaders.exceptions.GLTFUnsupportedException(
              |      "load pixmap from bytes not supported for WebGL")
              |  } else {
              |    new sge.graphics.Pixmap(encodedData, offset, len)
              |  }
              |}""".stripMargin,
          // 2. `PixmapIO.writePNG(file, pixmap)`, reached through `forName` + `getMethod` + `invoke`.
          //    `PixmapIO` is ported and portable, so the facade bought nothing here either.
          "net.mgsx.gltf.exporters.GLTFBinaryExporter#savePNG" ->
            """{
              |  if (sge.Gdx.app.`type` eq sge.Application.ApplicationType.WebGL) {
              |    throw new sge.gltf.loaders.exceptions.GLTFUnsupportedException(
              |      "saving pixmap not supported for WebGL")
              |  } else {
              |    sge.graphics.PixmapIO.writePNG(file, pixmap)
              |  }
              |}""".stripMargin,
          // 3. the one site where reflection did real work: fabricates a material extension
          //    object from its Class -- takes the factory registry the base's Pools and
          //    Ashley's ComponentFactories both take (GLTFExtensionFactories, injected by
          //    this module).
          "net.mgsx.gltf.exporters.GLTFMaterialExporter#ext" ->
            """{
              |  if (m.extensions == null) {
              |    m.extensions = new sge.gltf.data.GLTFExtensions()
              |  }
              |  var e: T = m.extensions.get(`type`, ext)
              |  if (e == null) {
              |    this.base.useExtension(ext, false)
              |    e = sge.gltf.data.extensions.GLTFExtensionFactories.create(`type`)
              |    m.extensions.set(ext, e)
              |  }
              |  e
              |}""".stripMargin,
          // A FOURTH entry (AnimationsPlayer#clearAnimations, an ascription working around
          // ENGINE-LIMITS T12's dropped `protected`) retired once T12 closed: DESIGN.md §8.7
          // now renders `protected` as `protected[<package>]` and overload resolution
          // matches javac's.
        )),
        // THE THREE DEAD Json CALL SITES ship the repair seam DISABLED: the base's injected
        // Json facade raises on every reflective path, so gdx-gltf's three calls compile and are
        // INERT at run time. `CallSiteSubstitutionTransform` was DRY-RUN against exactly these
        // three keys (3/3 bound), but the entry stays OUT until a replacement codec is injected.
        // LAST, deliberately (as AshleyPolicy): reads what the BASE actually emitted.
        balticporter.transform.PortMapTransform.forBases("sge"),
      ),
      // THE REFERENCE HAND PORT for sge-gltf. NOT inherited (DESIGN.md §8.23).
      parity = Some(ParityRef(roots = List(
        repoRoot.resolve("../sge/sge-extension/gltf/src/main/scala").normalize))),
    ))

  /** gdx-gltf's own JUnit suite, as a dependent of [[core]]. */
  def test(repoRoot: Path): PortManifest = core(repoRoot).extendedBy(PortManifest(
    name    = "sge-gltf-test",
    surface = List(new balticporter.transform.TestFrameworkTransform()),
  ))
