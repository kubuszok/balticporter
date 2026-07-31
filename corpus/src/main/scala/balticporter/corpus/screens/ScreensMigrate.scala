package balticporter.corpus.screens

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.corpus.libgdx.LibgdxPolicy
import balticporter.runner.{Determinism, PortRun, SourceSet, VendoredCommit}
import balticporter.transform.TypeRedirectTransform

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate **libgdx-screenmanager** (`src/main/java`, 22 types — a screen stack, a transition
  * queue and eleven concrete transitions) through the TIR.
  *
  *   corpus/runMain balticporter.corpus.screens.ScreensMigrate [--determinism=full]
  *
  * ==A DEPENDENT port, with a SECOND dependency the corpus has not met before==
  * Every one of the 22 files resolves against libGDX core, so `gdx/src` is a resolution root and
  * the policy is [[LibgdxPolicy.core]] EXTENDED rather than restated (CLAUDE.md §1.5) — the same
  * shape as Ashley's and anim8's.
  *
  * What is new is the OTHER dependency. `build.gradle` declares
  * `api "com.github.crykn.guacamole:gdx:v0.3.6"` — with the comment "is exposed because of
  * NestableFrameBuffer" — and ten guacamole types reach into these sources: `Preconditions`,
  * `Pair`, `@Beta`, `Logger`, `LoggerService`, `GLUtils`, `NestableFrameBuffer`,
  * `QuadMeshGenerator`, `ShaderProgramFactory`, `ShaderCompatibilityHelper`. guacamole is a
  * SEPARATE upstream library that this corpus does not vendor and does not port, so it is a
  * resolution input only: [[ScreensClasspath]] fetches exactly what `build.gradle` declares and
  * the frontend resolves against the JAR.
  *
  * Resolving it is necessary and not sufficient. A resolved-but-unported type still EMITS as
  * `de.damios.guacamole.…`, which is a Scala name nothing in reach declares — the "reference the
  * resolution root cannot supply" shape. The engine already has the (b) mechanism for exactly
  * that, [[TypeRedirectTransform]]: every occurrence is re-pointed at a shape-compatible type this
  * port ships itself, hand-written under `screens-core/src/main/scala` (CLAUDE.md §5.5 — `src/` is
  * the hand-written half of a port). See [[ScreensPolicy.guacamole]] for the table and for what
  * each replacement is.
  *
  * ==Where this port is strictly better than the reference hand port==
  * `sge-extension/screens` has 20 Scala files to these 22 Java ones, and the difference is not
  * only arithmetic (PROGRESS.md §1.1):
  *
  *   - **`NestableFrameBuffer` is absent from sge**, which uses a plain `FrameBuffer` for the
  *     screen manager's own two buffers. libGDX's `FrameBuffer.end()` unbinds to framebuffer 0
  *     rather than to whatever was bound on `begin()`, so a screen or transition that binds an FBO
  *     of its OWN inside a managed render rebinds to the DEFAULT buffer when it finishes and the
  *     screen manager's buffer is silently lost for the rest of the frame. Upstream's whole reason
  *     for depending on guacamole is that type. This port carries it.
  *   - `ManagedScreenAdapter`, `BasicInputMultiplexer` and `Supplier` are simply not in sge; they
  *     are ported here mechanically.
  *
  * ==Scope==
  * `src/main/java` only for THIS run; [[ScreensTestMigrate]] takes `src/test/java` (7 files, 12
  * `@Test`). `src/example/java` (5 files) is deliberately out of scope and named rather than
  * silently skipped: it is a `gdx-backend-lwjgl3` demo application, and no backend is ported.
  */
object ScreensMigrate:

  def main(args: Array[String]): Unit =
    val repoRoot = ScreensPort.repoRoot
    val base     = ScreensPort.upstream(repoRoot).resolve("src/main/java")
    val gdxSrc   = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize

    PortRun(
      label     = "screens",
      portRoot  = repoRoot.resolve("screens-core"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(
        base,
        ScreensPort.javaFiles(base),
        classpath       = ScreensClasspath.entries(repoRoot),
        resolutionRoots = List(gdxSrc),
      ),
      phases     = Nil, // supplied by the manifest — the two sources are mutually exclusive
      manifest   = Some(ScreensPolicy.core(repoRoot)),
      provenance = Some(Provenance(
        upstreamName     = "libgdx-screenmanager",
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
      nextStep    = "just screens-measure",
    ).execute()

/** Paths and file selection shared by the two source sets of this port. */
object ScreensPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def upstream(repoRoot: Path): Path =
    repoRoot.resolve("../sge/original-src/libgdx-screenmanager").normalize

  def javaFiles(base: Path): List[String] =
    Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

/** libgdx-screenmanager's per-library policy — a DEPENDENT of libGDX core's.
  *
  * The base's `dropTypes`, `dropMethods`, `packageRenames` and signature-affecting phases are
  * INHERITED, not restated: they are facts about the surface screenmanager compiles against, and a
  * dependent that re-declared them would be free to drift. What this module adds is its own
  * namespace claim, its own renames, and the guacamole redirect.
  *
  * `inject` is deliberately NOT inherited (see [[balticporter.core.PortManifest]]): a drop is an
  * observation about the shared API and binds every module that sees it, but exactly one module
  * ships each replacement file. libGDX core ships the replacements for the types it dropped.
  */
object ScreensPolicy:

  def core(repoRoot: Path): PortManifest =
    LibgdxPolicy.core(repoRoot).extendedBy(PortManifest(
      name    = "screens",
      governs = Set("de.eskalon.commons"),
      // sge flattens this library's three upstream packages into `sge.screen` and
      // `sge.screen.utils` (`../sge/sge-extension/screens/src/main/scala/sge/screen`), so
      // `core.ManagedGame` and `screen.ScreenManager` become siblings. Three pairs rather than
      // one, because `de.eskalon.commons -> sge.screen` alone would produce `sge.screen.screen`.
      // libGDX's `com.badlogic.gdx -> sge` is INHERITED from the base, not restated.
      packageRenames = Map(
        "de.eskalon.commons.screen" -> "sge.screen",
        "de.eskalon.commons.core"   -> "sge.screen",
        "de.eskalon.commons.utils"  -> "sge.screen.utils",
      ),
      surface = List(guacamole),
    ))

  /** The guacamole seam.
    *
    * `com.github.crykn.guacamole:gdx` is a separate Apache-2.0 upstream that this corpus neither
    * vendors nor ports. Its types RESOLVE (through [[ScreensClasspath]]) and therefore translate
    * correctly — a `Preconditions.checkArgument` call is understood as a static call and a
    * `NestableFrameBuffer` field is understood as a `FrameBuffer` subtype — but they cannot be
    * EMITTED, because this run converts no guacamole compilation unit. Re-pointing them at Scala
    * this port ships is the engine's §1(b) answer to precisely that (see [[TypeRedirectTransform]]
    * for why a dependent cannot instead inject at the other module's FQN).
    *
    * The replacements live in `screens-core/src/main/scala/sge/screen/guacamole` and are
    * hand-written — which is a statement about scope, not a claim about quality: they are the
    * hand-written half of a port (CLAUDE.md §5.5), and the day guacamole becomes a corpus port of
    * its own, this table is what gets deleted.
    */
  def guacamole: TypeRedirectTransform = new TypeRedirectTransform(Map(
    // `NestableFrameBuffer` is why upstream depends on guacamole at all, and it is the one type
    // whose ABSENCE from the reference hand port is a behavioural defect (PROGRESS.md §1.1).
    "de.damios.guacamole.gdx.graphics.NestableFrameBuffer"      -> "sge.screen.guacamole.NestableFrameBuffer",
    "de.damios.guacamole.gdx.graphics.GLUtils"                  -> "sge.screen.guacamole.GLUtils",
    "de.damios.guacamole.gdx.graphics.QuadMeshGenerator"        -> "sge.screen.guacamole.QuadMeshGenerator",
    "de.damios.guacamole.gdx.graphics.ShaderProgramFactory"     -> "sge.screen.guacamole.ShaderProgramFactory",
    "de.damios.guacamole.gdx.graphics.ShaderCompatibilityHelper" -> "sge.screen.guacamole.ShaderCompatibilityHelper",
    "de.damios.guacamole.gdx.log.Logger"                        -> "sge.screen.guacamole.Logger",
    "de.damios.guacamole.gdx.log.LoggerService"                 -> "sge.screen.guacamole.LoggerService",
    "de.damios.guacamole.Preconditions"                         -> "sge.screen.guacamole.Preconditions",
    "de.damios.guacamole.tuple.Pair"                            -> "sge.screen.guacamole.Pair",
    "de.damios.guacamole.annotations.Beta"                      -> "sge.screen.guacamole.Beta",
  ))

/** libgdx-screenmanager's COMPILE-scope dependency, for shadow-class resolution only.
  *
  * `build.gradle` declares `implementation "com.badlogicgames.gdx:gdx:1.13.5"` and
  * `api "com.github.crykn.guacamole:gdx:v0.3.6"`. libGDX arrives as a SOURCE resolution root
  * instead — the same tree `LibgdxCoreMigrate` ports, so the two runs cannot see different
  * signatures — and is therefore excluded here rather than resolved twice from two versions.
  * What is left is guacamole and the annotation jars it and screenmanager both use
  * (`org.jspecify:jspecify`, which supplies the `@Nullable`/`@NullMarked` on these sources).
  *
  * guacamole is published only on jitpack, so the repository is named explicitly. A failure here
  * is FATAL rather than an empty classpath, for the reason [[SimpleGraphsClasspath]] records: an
  * import the frontend cannot resolve does not fail, it resolves WRONGLY — `Preconditions.check…`
  * becomes an unqualified call on the enclosing class — and the port then emits nonsense and
  * reports success.
  */
object ScreensClasspath:

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/screens-classpath.txt")

  private val coordinates = List(
    "com.github.crykn.guacamole:gdx:v0.3.6",
    "org.jspecify:jspecify:0.3.0",
  )

  def entries(repoRoot: Path): List[Path] =
    ensure(repoRoot).toString.split(java.io.File.pathSeparator).toList
      .filter(_.nonEmpty).map(Path.of(_))

  /** Guarantee the cache file exists, fetching once if it does not. Returns the joined line. */
  def ensure(repoRoot: Path): String =
    val out = cache(repoRoot)
    if Files.exists(out) && Files.readString(out).trim.nonEmpty then Files.readString(out).trim
    else
      val cmd = List("cs", "fetch", "--classpath", "-r", "https://jitpack.io",
        // libGDX is a SOURCE resolution root; a second copy of it on the frontend classpath is a
        // second answer to every `com.badlogic.gdx.*` name, decided by scan order.
        "--exclude", "com.badlogicgames.gdx:gdx") ++ coordinates
      val proc = new ProcessBuilder(cmd*).redirectErrorStream(true).start()
      val raw  = new String(proc.getInputStream.readAllBytes()).trim
      // `cs` writes PROGRESS to stderr and the classpath to stdout; merged here so a failure is
      // reportable, then filtered to the one line holding a jar (`SimpleGraphsClasspath` records
      // what taking the whole output cost).
      val line = raw.linesIterator.filter(_.contains(".jar")).toList.lastOption.getOrElse("")
      if proc.waitFor() != 0 || line.isEmpty then
        throw new IllegalStateException(
          s"[screens] could not fetch the frontend classpath (is `cs` installed?):\n$raw")
      Files.createDirectories(out.getParent)
      Files.writeString(out, line)
      line
