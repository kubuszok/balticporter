package balticporter.core

import java.nio.file.Path

/** CLAUDE.md §5.4's rule, as ONE function — realpath both operands, `normalize` only where the
  * path does not exist. A lexical `normalize` keeps a symlink and `Files.walk` follows it, so a
  * `startsWith` between the two matches nothing, silently — common (a git worktree). Consolidates
  * four separate repair copies (one buggy); `RealPathSpec` pins `.toRealPath(` to this file alone. */
object RealPath:

  /** `p` with symlinks resolved, falling back to `toAbsolutePath.normalize` when it does not exist.
    * `toAbsolutePath` matters: a RELATIVE fallback makes [[relativize]] throw. Catches `Exception`,
    * not `IOException`: `toRealPath` also throws `SecurityException`/`InvalidPathException`, and a
    * narrower catch would let either kill a whole port's emission. */
  def of(p: Path): Path =
    try p.toRealPath()
    catch case _: Exception => p.toAbsolutePath.normalize

  /** [[of]] as a `String` — for the callers that compare or store text rather than paths. */
  def str(p: Path): String = of(p).toString

  /** is `p` under `root`? BOTH operands go through [[of]], which is the whole point. */
  def startsWith(p: Path, root: Path): Boolean = of(p).startsWith(of(root))

  /** `p` relative to `root`, both realpathed. Throws for two paths with no common root, as
    * `Path.relativize` does — a caller that can survive that must say so. */
  def relativize(root: Path, p: Path): Path = of(root).relativize(of(p))

  /** [[of]] for an input whose ABSENCE must be fatal — a declared source root, a config-named file.
    * CLAUDE.md §5.1's missing-input rule: a silently-normalised nothing-in-particular reports
    * success over no input at all, and the diagnostic must name the path.
    * @throws java.nio.file.NoSuchFileException when `p` does not exist or cannot be resolved */
  def ofExisting(p: Path, what: String): Path =
    try p.toRealPath()
    catch
      case e: Exception =>
        throw new java.nio.file.NoSuchFileException(
          p.toString, null,
          s"$what does not exist (or cannot be resolved): ${e.getClass.getSimpleName}" +
            Option(e.getMessage).map(m => s" — $m").getOrElse("")
        )
