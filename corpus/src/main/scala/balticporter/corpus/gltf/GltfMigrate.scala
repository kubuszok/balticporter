package balticporter.corpus.gltf

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **gdx-gltf** (`gltf/src`, 135 types / 11,307 lines — a glTF 2.0 loader, exporter and
  * PBR rendering pipeline for libGDX) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.gltf.GltfMigrate [--determinism=full]
  *
  * ==Why gdx-gltf is in the corpus==
  * It is the largest port after libGDX core itself, and the first one whose difficulty is
  * INHERITANCE DEPTH rather than file count or line count. Its 135 types are stacked on libGDX's
  * 3D pipeline: `PBRShader extends DefaultShader extends BaseShader extends BaseShaderProvider`,
  * `Scene`/`SceneManager` over `ModelInstance`/`RenderableProvider`, sixteen `Attribute`
  * subclasses over `Attribute`'s `register`/`compareTo` protocol, and `AnimationControllerHack`
  * which reaches into `AnimationController`'s protected state. Every one of those parents is
  * EMITTED Scala this run never sees — it resolves against libGDX's Java (CLAUDE.md §1.5) — so
  * the port is a sustained test of whether the base's transforms produce a surface a deep
  * subclass hierarchy can actually extend.
  *
  * It is also the first library in the corpus that is a genuine THIRD-PARTY extension: unlike
  * Ashley and anim8 it is not written by libGDX's authors, so its use of the base API is the use
  * a downstream consumer makes, not the use the base's own tests make.
  *
  * ==A DEPENDENT port==
  * All 118 distinct `com.badlogic.*` imports across the 135 files resolve inside `gdx/src` —
  * there is no backend or extension reference anywhere in the library — so `gdx/src` is a
  * RESOLUTION root, parsed so references resolve and never emitted here, and the policy is
  * [[LibgdxPolicy.core]] extended rather than restated (CLAUDE.md §1.5). `LibgdxCoreMigrate`
  * emits the base and the two are compiled together by `just gltf-measure`.
  *
  * ==Scope==
  * `gltf/src` only. Deliberately excluded, and named rather than silently dropped:
  *
  *   - `demo/` (35 files) — a libGDX application with desktop, html and android launchers.
  *   - `ibl-composer/` (25 files) — an authoring tool (a VisUI desktop app for baking IBL maps).
  *   - six of the seven files in `gltf/test` — see [[GltfTestMigrate]], which explains why.
  *
  * `gltf/test/net/mgsx/gltf/scene3d/attributes/AttributesCompareTest.java` (8 `@Test` methods) IS
  * in scope and is ported by [[GltfTestMigrate]] as a dependent of this run.
  */
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
      label     = "gltf",
      portRoot  = repoRoot.resolve("gltf-core"),
      sourceSet = SourceSet.Main,
      // libGDX core is a RESOLUTION root: parsed so every reference resolves, never emitted here.
      // `LibgdxCoreMigrate` emits it, and the two are compiled together.
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
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just gltf-measure",
    ).execute()

/** gdx-gltf's per-library policy — a DEPENDENT of libGDX core's.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and signature-affecting phases are
  * INHERITED, not restated: they are facts about the surface gdx-gltf compiles against, and a
  * dependent that re-declared them would be free to drift. What gdx-gltf adds is its own namespace
  * claim, its own rename, and whatever its own 135 files need.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  */
object GltfPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "gltf",
      governs = Set("net.mgsx.gltf"),
      // sge puts gdx-gltf at `sge.gltf` (`../sge/sge-extension/gltf/src/main/scala/sge/gltf`),
      // package for package: `net/mgsx/gltf/scene3d/shaders` is `sge/gltf/scene3d/shaders`, all 28
      // subpackages carried straight through. One prefix pair moves the whole library. libGDX's
      // `com.badlogic.gdx -> sge` is INHERITED from the base manifest, not restated; longest-
      // prefix-wins keeps the two apart.
      packageRenames = Map("net.mgsx.gltf" -> "sge.gltf"),
      // gdx-gltf's OWN replacements. `inject` is not inherited — exactly one module ships each
      // replacement file, and libGDX core ships the ones for the types IT dropped.
      inject  = List(repoRoot.resolve("corpus/gltf-overrides")),
      surface = List(
        // ==THE THREE REFLECTIVE SITES, and why all three are a GWT workaround rather than a need==
        //
        // gdx-gltf reaches `com.badlogic.gdx.utils.reflect.ClassReflection` in exactly three
        // methods, and the base drops that type: reflective instantiation is the one thing Scala.js
        // and Scala Native cannot do. Two of the three say so themselves, in an upstream comment —
        // "call X via reflection to avoid compilation error with GWT" — so the reflection is not
        // what the code MEANS, it is how it dodged a compiler that is not in this port's target
        // set. CLAUDE.md §3.5: the reference hand port (`../sge/sge-extension/gltf`) SOLVED both
        // the same way, `PixmapBinaryLoaderHack.scala` and `GLTFBinaryExporter.savePNG` each making
        // the direct call the facade was emulating, with the WebGL guard kept.
        //
        // The bodies are written in the port's FINAL namespace: `MethodBodyTransform` splices them
        // verbatim and the package rename never sees them, while the KEYS are upstream because the
        // phase matches them against the model before the rename runs. Getting that backwards is
        // one compile error naming `net.mgsx` in a file that declares `package sge.gltf`.
        new balticporter.transform.MethodBodyTransform(Map(
          // 1. `new Pixmap(bytes, off, len)`, reached through `getConstructor(…).newInstance(…)`.
          "net.mgsx.gltf.loaders.shared.texture.PixmapBinaryLoaderHack#load" ->
            """{
              |  if (sge.Gdx.app.getType() eq sge.Application.ApplicationType.WebGL) {
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
              |  if (sge.Gdx.app.getType() eq sge.Application.ApplicationType.WebGL) {
              |    throw new sge.gltf.loaders.exceptions.GLTFUnsupportedException(
              |      "saving pixmap not supported for WebGL")
              |  } else {
              |    sge.graphics.PixmapIO.writePNG(file, pixmap)
              |  }
              |}""".stripMargin,
          // 3. The one site where reflection was doing real work: `ext` fabricates a material
          //    extension object from its `Class`. No direct call replaces that, so it takes the
          //    factory registry the base's own injected `Pools` and Ashley's `ComponentFactories`
          //    both take — `GLTFExtensionFactories`, injected by THIS module (see `inject`).
          //    Everything else in the 335-line exporter translates mechanically, which is exactly
          //    the case `MethodBodyTransform` exists for.
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
          // A FOURTH entry stood here and is GONE, which is worth a line because its absence is the
          // measurement: `AnimationsPlayer#clearAnimations` carried upstream's own body plus an
          // ascription, to work around the engine dropping java's `protected` (`ENGINE-LIMITS.md`
          // T12 — both `setAnimation` overloads shipped public, `setAnimation(null)` became
          // `E051 Ambiguous overload`). `DESIGN.md` §8.7 renders `protected` as
          // `protected[<package>]`, dotty prunes the inaccessible alternative before overload
          // resolution, and the call resolves exactly as javac resolved it. The condition its own
          // comment named has been met, so the entry retires rather than being kept "just in case".
        )),
        // ==THE THREE DEAD `Json` CALL SITES, and why the seam for them ships DISABLED==
        //
        // libGDX core drops `com.badlogic.gdx.utils.Json` (reflection is the one thing Scala.js and
        // Native cannot do) and injects a facade whose reflective paths raise. gdx-gltf calls into
        // it three times, so those three calls compile and are INERT at run time:
        //
        //   SeparatedDataFileResolver.java:30  Json#fromJson(Class,FileHandle)
        //   BinaryDataFileResolver.java:97     Json#fromJson(Class,String)
        //   GLTFExporter.java:241              Json#prettyPrint(Object)
        //
        // `CallSiteSubstitutionTransform` is the mechanism that repairs them, and it was DRY-RUN
        // against exactly these three keys: 3/3 bound, 3 sites rewritten, 0 policy findings, each
        // at the line above. What is missing is the other side — the reference hand port replaced
        // the whole reflective path with 2,268 lines of Jsoniter codecs (`GLTFCodecs`,
        // `GLTFExporterJson`), which is a decision THIS port has not made. Naming a codec that does
        // not exist would trade three inert calls for three that do not compile, so the entry stays
        // out until the codecs are injected. See `PROGRESS.md` §gdx-gltf.
        //
        // LAST, deliberately, for the reason AshleyPolicy states: this reads what the BASE
        // actually emitted and reports a reference the base does not ship, so it must run after
        // any seam that re-points such a reference, or it reports the very sites the next phase
        // repairs. A residue check, exactly like `PortabilityCheck`.
        balticporter.transform.PortMapTransform.forBases("libgdx-core"),
      ),
    ))

  /** gdx-gltf's own JUnit suite, as a dependent of [[core]]. */
  def test(repoRoot: Path): PortManifest = core(repoRoot).extendedBy(PortManifest(
    name    = "gltf-test",
    surface = List(new balticporter.transform.TestFrameworkTransform()),
  ))
