package balticporter.frontend.ts

import java.io.IOException
import java.nio.file.{ Files, Path }

/** Resolves the sibling `ssg` checkout that some frontend-ts specs read for comparison: its hand-written reference port (`<module>/reference/scala` on the `balticporter-generated` branch,
  * `<module>/src/main/scala` on `master`) and its vendored upstream sources under `original-src/`.
  *
  * Resolution order: the `balticporter.ssgRoot` system property, then the `BALTICPORTER_SSG_ROOT` environment variable, then a walk up from the working directory for the first ancestor whose `ssg`
  * child directory contains `build.sbt`. The walk is what finds the checkout from a worktree nested under `.claude/worktrees/<name>`: `.claude/worktrees/ssg` is a symlink to the real checkout, so the
  * walk finds it one level up without needing to reach the primary checkout root.
  *
  * A location named EXPLICITLY (the property or the variable) that does not resolve to a real checkout is reported as unavailable -- it never falls back to the walk-up. That lets a caller hide an
  * otherwise-found checkout on purpose, to verify that a spec is really skipped rather than passing having asserted nothing.
  *
  * Tests in this module fork (`Test / fork := true`): a system property given to `sbt --client` never reaches the forked test JVM, because the client only sends a command string to an already-running
  * server -- it does not carry its own process's properties into that server. The environment variable does reach the forked JVM, but only if it was already present when the SERVER itself started (a
  * client cannot inject one into a live server): run `sbt --client shutdown` first, so the next `sbt --client` command starts a fresh server that inherits the variable from the shell that launched
  * it.
  */
object ConsumerCheckout:

  private val SystemPropertyKey = "balticporter.ssgRoot"
  private val EnvironmentKey    = "BALTICPORTER_SSG_ROOT"

  private def realOrNormalized(path: Path): Path =
    try path.toRealPath()
    catch case _: IOException => path.normalize()

  private def isCheckout(dir: Path): Boolean =
    Files.isDirectory(dir) && Files.isRegularFile(dir.resolve("build.sbt"))

  private def explicitRoot: Option[String] =
    sys.props.get(SystemPropertyKey).orElse(sys.env.get(EnvironmentKey))

  private def walkUpForSsg(): Option[Path] =
    val start = realOrNormalized(Path.of(sys.props.getOrElse("user.dir", ".")))
    Iterator.iterate(Option(start))(_.flatMap(p => Option(p.getParent))).takeWhile(_.isDefined).flatten.map(_.resolve("ssg")).find(isCheckout).map(realOrNormalized)

  /** The ssg checkout root, or `None` when neither an explicit location nor the walk-up found a usable one (a `build.sbt` beside a directory named `ssg`).
    */
  def resolve(): Option[Path] =
    explicitRoot match
      case Some(root) =>
        val candidate = Path.of(root)
        if isCheckout(candidate) then Some(realOrNormalized(candidate)) else None
      case None =>
        walkUpForSsg()

  /** `ssg`'s hand-written reference sources for one module (e.g. `"ssg-js"`), checked on the `balticporter-generated` branch's layout first (`<module>/reference/scala`), then the `master` layout
    * (`<module>/src/main/scala`). Returns the directory found and which layout it was, so a caller can name it in an `assume` message.
    */
  def referenceScala(module: String): Option[(Path, String)] =
    resolve().flatMap { root =>
      val reference = root.resolve(module).resolve("reference/scala")
      val mainScala = root.resolve(module).resolve("src/main/scala")
      if Files.isDirectory(reference) then Some((reference, "reference/scala"))
      else if Files.isDirectory(mainScala) then Some((mainScala, "src/main/scala"))
      else None
    }

  /** A path under `ssg`'s vendored upstream sources (`original-src/<relative>`), if it exists. */
  def originalSrc(relative: String): Option[Path] =
    resolve().map(_.resolve("original-src").resolve(relative)).filter(Files.exists(_))
