package balticporter.core

import java.nio.file.Path

/** CLAUDE.md §5.4's rule, as ONE function — realpath both operands, `normalize` only where the
  * path does not exist.
  *
  * Every path this project compares has two forms: the one an operator or a config WROTE, and the
  * one the parser RECORDED. They differ whenever a symlink is in play, and a symlink is in play in
  * the normal case — a git worktree reaches its sibling checkouts through one. A lexical
  * `normalize` keeps the link and `Files.walk` follows it, so a `startsWith` between the two
  * matches nothing, silently, while the code around it looks correct.
  *
  * §5.4 is a rule because three separate parts of the engine were bitten by it. This object exists
  * because the REPAIRS were separate too: four private helpers, each spelling the rule differently,
  * and three of the four with a different exception policy — one of them a bug (a bare `normalize`
  * fallback leaves a relative path relative, so the `relativize` that follows can throw, and the
  * outer `catch` then returns the raw absolute path its own doc promises never to emit). Four
  * copies of a rule are four chances to get it wrong, and duplication is the one failure a grep CAN
  * see: `RealPathSpec` asserts that `.toRealPath(` appears in production code only in this file.
  *
  * The comparison forms are here too, rather than composed at each call site, because "realpath
  * both operands" is exactly the half a call site forgets — a `RealPath.of(a).startsWith(b)` reads
  * fine and is the bug.
  */
object RealPath:

  /** `p` with symlinks resolved, falling back to `toAbsolutePath.normalize` when it does not exist
    * (a synthetic origin, a directory not yet created).
    *
    * `toAbsolutePath` is not decoration: a fallback that returned a RELATIVE path would make
    * [[relativize]] throw `IllegalArgumentException` ("'other' is different type of Path"), which
    * is precisely the divergence the check report's copy carried.
    *
    * Catches `Exception`, not `IOException`: `toRealPath` also throws `SecurityException` under a
    * manager and `InvalidPathException` for a name the filesystem rejects, and the emitter's copy —
    * which caught `IOException` alone — would let either kill the emission of a whole port. A path
    * this cannot resolve is a path that does not exist, however it fails to. */
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

  /** [[of]] for an input whose ABSENCE must be fatal — a declared source root, a file a config
    * named. CLAUDE.md §5.1's missing-input rule: a path that silently normalises to a
    * nothing-in-particular produces a run that reports success over no input at all, and the
    * diagnostic has to name the path or the operator cannot act on it.
    *
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
