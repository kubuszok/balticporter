package balticporter.corpus.vfx

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **gdx-vfx** (44 types — post-processing shader effects for libGDX) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.vfx.VfxMigrate [--determinism=full]
  *
  * ==Why gdx-vfx is in the corpus==
  * It is the corpus's first GL-facing library, and the first whose whole reason for existing is a
  * resource the JVM does not own: every effect is a `ShaderProgram` compiled from a `.vert`/`.frag`
  * asset, driven through framebuffer ping-pong. Two consequences the earlier libraries never
  * produced:
  *
  *   - **a library whose API surface is mostly libGDX's.** `VfxFrameBuffer`, `ViewportQuadMesh` and
  *     `VfxGLUtils` are thin wrappers over `FrameBuffer`, `Mesh`, `ShaderProgram`, `GL20` and
  *     `Gdx.gl`, so nearly every signature this port emits mentions a type the BASE port emitted.
  *     That is what makes it a real test of §1.5: it can only compile if libGDX core's transforms
  *     did to those signatures exactly what this run assumes they did.
  *   - **a reflective branch that exists only for a backend nobody ports.** `VfxGLUtils`' static
  *     initialiser reaches `ClassReflection.newInstance(ClassReflection.forName("…gwt.
  *     GwtVfxGlExtension"))` when the application type is `WebGL`. See [[VfxPolicy]] for what the
  *     port does with it and why.
  *
  * ==A DEPENDENT port==
  * Every one of the 44 files resolves against libGDX core — `Gdx`, `GL20`, `Texture`, `Pixmap`,
  * `Mesh`, `ShaderProgram`, `FrameBuffer`, `Array`, `Pool`, `Disposable`, `Vector2`, `Matrix4`,
  * `WidgetGroup` — so `gdx/src` is a RESOLUTION root, parsed so references resolve and never
  * emitted here, and the policy is [[LibgdxPolicy.core]] EXTENDED rather than restated
  * (CLAUDE.md §1.5). `LibgdxCoreMigrate` emits the base and the two are compiled together by
  * `just vfx-measure`.
  *
  * ==Scope==
  * The two LIBRARY gradle modules, `gdx-vfx/core/src` (23 types) and `gdx-vfx/effects/src` (21),
  * emitted into ONE sbt module — which is what the reference hand port does too
  * (`../sge/sge-extension/vfx` holds both, at `sge.vfx`). They share one package root
  * (`com.crashinvaders.vfx`), effects depends on core and nothing depends on effects alone, so a
  * second port would buy a module boundary the consumer does not have.
  *
  * Deliberately excluded, and named rather than silently dropped:
  *
  *   - **`gdx-vfx/gwt/src`** (1 file, `GwtVfxGlExtension`) — the GWT backend's `VfxGlExtension`
  *     implementation. sge targets Scala Native and Scala.js; a GWT backend is dead weight, and the
  *     reference port does not carry it either. Its absence is exactly what makes the reflective
  *     branch above unreachable rather than merely unported.
  *   - **`demo/`** (74 files) — five launcher modules and an LML/VisUI-driven demo application.
  *     Not library surface, and it depends on third-party libraries (`gdx-lml`, `VisUI`) that are
  *     not in the corpus.
  *
  * ==There is no upstream suite==
  * `@Test` count over the WHOLE gdx-vfx checkout, comments stripped: **0**. gdx-vfx ships no test
  * source set at all — not a set of demos misread as one (anim8) and not a runnable sample module
  * (jbump), simply nothing. So there is no `VfxTestMigrate` and no emitted test source set; this
  * port's only behavioural evidence is the hand-written MUnit suite committed under
  * `vfx-core/src/test/scala` (CLAUDE.md §3: a green compile says nothing about behaviour), which is
  * anim8's precedent. `just vfx-measure` prints the upstream zero and the hand-written count side
  * by side, because `0 == 0` must not be allowed to read as agreement.
  */
object VfxMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    // The COMMON root of the two library modules. Spoon takes each file as its own input resource
    // (`SpoonTir` adds them one at a time), so a source root that is a plain ancestor rather than a
    // package root is fine — and it is what keeps `PortRun.converted`'s "under sourceRoot, not
    // under a resolutionRoot" test able to see both modules as this run's own.
    val base   = repoRoot.resolve("../sge/original-src/gdx-vfx/gdx-vfx").normalize
    val gdxSrc = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    // `gwt/` is the third directory under `base` and is NOT converted — see the scope note.
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filter(f => f.startsWith("core/src/") || f.startsWith("effects/src/"))
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    PortRun(
      label     = "vfx",
      portRoot  = repoRoot.resolve("vfx-core"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
      phases    = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest  = Some(VfxPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "gdx-vfx",
        upstreamCommit   = VendoredCommit.of(base),
        originalLicense  = "Apache-2.0",
        // `sourceRoot` above is `<checkout>/gdx-vfx`, so a relativised origin reads
        // `core/src/com/crashinvaders/…` and this prefix restores the upstream repo-relative form.
        sourcePathPrefix = "gdx-vfx",
        sourceRoot       = base.toString,
      )),
      // NOT `Vendored`: `LibgdxCoreMigrate` already vendors the collection shims into the module
      // this output is compiled beside. Vendoring again would define every support type twice —
      // which the JVM tolerates only while the copies agree, and the Scala.js/Native linkers reject.
      runtimeMode = RuntimeMode.Dependency,
      determinism = Determinism.fromArgs(args.toSeq),
      nextStep    = "just vfx-measure",
    ).execute()

/** gdx-vfx's per-library policy — a DEPENDENT of libGDX core's.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and signature-affecting phases are
  * INHERITED, not restated: they are facts about the surface gdx-vfx compiles against, and a
  * dependent that re-declared them would be free to drift. What gdx-vfx adds is its own namespace
  * claim, its own rename, and the one seam its 44 files need.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  */
object VfxPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "vfx",
      governs = Set("com.crashinvaders.vfx"),
      // sge puts gdx-vfx at `sge.vfx` (`../sge/sge-extension/vfx/src/main/scala/sge/vfx`), with the
      // upstream subpackages carried straight through — `sge/vfx/effects`, `sge/vfx/framebuffer`,
      // `sge/vfx/gl`, `sge/vfx/utils` are all there in the hand port — so one prefix pair moves the
      // whole library. libGDX's `com.badlogic.gdx -> sge` is INHERITED from the base manifest, not
      // restated; longest-prefix-wins keeps the two apart.
      packageRenames = Map("com.crashinvaders.vfx" -> "sge.vfx"),
      surface = List(
        // LAST, deliberately, for the reason AshleyPolicy states: this reads what the BASE actually
        // emitted and reports a reference the base does not ship, so it must run after any seam
        // that re-points such a reference, or it reports the very sites the next phase repairs. A
        // residue check, exactly like `PortabilityCheck`.
        balticporter.transform.PortMapTransform.forBases("libgdx-core"),
      ),
    ))
