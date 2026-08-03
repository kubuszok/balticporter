package balticporter.corpus.anim8

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **anim8-gdx** (`src/main/java`, 16 types — GIF/PNG8/APNG writers and the dithering and
  * palette-reduction machinery behind them) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.anim8.Anim8Migrate [--determinism=full]
  *
  * ==Why anim8-gdx is in the corpus==
  * It is the corpus's first library whose difficulty is per-LINE rather than per-file. 16 files
  * hold 19,594 lines — `PNG8` alone is 8,351 and `PaletteReducer` 5,989 — and the shape that
  * dominates them is not found anywhere in libGDX, Ashley or simple-graphs:
  *
  *   - **enormous constant data.** `ConstantData` is 108 lines of Java holding four string literals
  *     of 47,935 / 6,390 / 6,390 / 6,390 characters, in ISO-8859-1, full of control characters and
  *     high bytes, decoded with `getBytes(StandardCharsets.ISO_8859_1)` inside a `static { }` block.
  *     Every byte of that has to survive the frontend, the TIR and the emitter unchanged, and
  *     nothing but a running assertion can prove that it did — which is exactly why this port's
  *     suite pins actual byte values at known indices rather than lengths (see `src/test`).
  *   - **arithmetic in bulk.** `OtherMath`'s `barronSpline`/`probit`/`cbrt`/`atan2` are bit-pattern
  *     approximations built on `NumberUtils.floatToIntBits` and hex float literals (`0x1p-8`).
  *     Every CLAUDE.md §4.4 form that translates to valid Scala meaning something else lives here.
  *
  * ==A DEPENDENT port==
  * Every one of the 16 files resolves against libGDX core — `Pixmap`, `FileHandle`, `Color`, `Gdx`,
  * `Array`, `ByteArray`, `FloatArray`, `IntIntMap`, `OrderedMap`, `NumberUtils`, `MathUtils`,
  * `Interpolation`, `StreamUtils`, `Disposable` — so `gdx/src` is a RESOLUTION root, parsed so
  * references resolve and never emitted here, and the policy is [[LibgdxPolicy.core]] extended
  * rather than restated (CLAUDE.md §1.5). `LibgdxCoreMigrate` emits the base and the two are
  * compiled together by `just anim8-measure`.
  *
  * ==Scope==
  * `src/main/java` only. `src/test/java` (20 files) is deliberately excluded and named rather than
  * silently dropped: it contains no `@Test` at all. Every file in it is an `ApplicationAdapter`
  * demo or a JMH-style startup bench driven by `gdx-backend-lwjgl3` — `StillImageDemo`,
  * `VideoConvertDemo`, `InteractiveReducer`, `ShaderCaptureDemo` and friends — which needs a
  * desktop backend, and no backend is ported. There is therefore NO upstream suite to migrate, and
  * this port's only behavioural evidence is the hand-written suite in `ported/sge-anim8/src/test/scala`
  * (CLAUDE.md §3: a green compile says nothing about behaviour). See PROGRESS.md.
  */
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
      label     = "anim8",
      portRoot  = repoRoot.resolve("ported/sge-anim8"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(base, files, Nil, resolutionRoots = List(gdxSrc)),
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

/** anim8-gdx's per-library policy — a DEPENDENT of libGDX core's.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and signature-affecting phases are
  * INHERITED, not restated: they are facts about the surface anim8 compiles against, and a
  * dependent that re-declared them would be free to drift. What anim8 adds is its own namespace
  * claim, its own rename, and whatever its own 16 files need.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  */
object Anim8Policy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "anim8",
      governs = Set("com.github.tommyettinger.anim8"),
      // sge puts anim8 at `sge.anim8` (`../sge/sge-extension/anim8/src/main/scala/sge/anim8`), so
      // the whole namespace moves with one pair. libGDX's `com.badlogic.gdx -> sge` is INHERITED
      // from the base manifest, not restated; longest-prefix-wins keeps the two apart.
      packageRenames = Map("com.github.tommyettinger.anim8" -> "sge.anim8"),
      surface = List(
        // LAST, deliberately, for the reason AshleyPolicy states: this reads what the BASE
        // actually emitted and reports a reference the base does not ship, so it must run after
        // any seam that re-points such a reference, or it reports the very sites the next phase
        // repairs. A residue check, exactly like `PortabilityCheck`.
        balticporter.transform.PortMapTransform.forBases("libgdx-core"),
      ),
    ))
