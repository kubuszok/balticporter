package balticporter.runner

import java.nio.file.{Files, Path}

/** The truthful `Provenance.upstreamCommit` for a VENDORED source tree.
  *
  * Every generated file carries an attribution header (CLAUDE.md §4.57), and its
  * `upstream-commit` line exists so an investigating agent can pin the exact Java the port was
  * derived from. Three of the live ports shipped a PATH there ("vendored in ../sge/…") — true,
  * machine-local, and pinning nothing. The honest pin is the LAST COMMIT THAT TOUCHED THE TREE in
  * the repository that contains it, plus that repository's `origin` URL when it has one. Where the
  * vendored tree is a full upstream clone (all three sge trees are — libgdx, ashley and
  * simple-graphs each carry their real upstream `origin`), that hash IS a true upstream commit
  * and the line becomes directly actionable: fetch origin, check out the hash, diff the subtree.
  *
  * Last-touch, not `HEAD`: `HEAD` moves on every unrelated commit to the containing repository,
  * which would churn every generated header — and the member-digest baseline with it — while the
  * Java the port reads is byte-identical. The last commit touching the tree changes exactly when
  * the sources can have changed, and pins the identical subtree state either way.
  *
  * A dirty tree is stated (`+dirty`): a header claiming a commit the working files no longer
  * match is the wrong-but-plausible value §4.57 warns defeats the line's only purpose. The same
  * clause covers the fallback: where git answers nothing, say `commit unknown` rather than
  * inventing an anchor.
  *
  * Paths are realpathed before relativising (CLAUDE.md §5.4 — a worktree reaches its sibling
  * checkout through a symlink, and `git -C` follows it while a lexical relativise keeps it). */
object VendoredCommit:

  def of(sourceRoot: Path): String =
    val real = realpath(sourceRoot)
    (for
      top  <- git(real, "rev-parse", "--show-toplevel")
      hash <- git(real, "log", "-1", "--format=%H", "--", ".")
      if hash.nonEmpty
    yield
      val topReal  = realpath(Path.of(top))
      val repoName = topReal.getFileName.toString
      val tree     = topReal.relativize(real).toString match
        case ""  => "."
        case rel => rel
      val dirty  = git(real, "status", "--porcelain", "--", ".").exists(_.nonEmpty)
      val origin = git(real, "config", "--get", "remote.origin.url").filter(_.nonEmpty)
      val where  = origin match
        case Some(url) => s"; origin $url"
        case None      => ""
      s"$repoName@$hash${if dirty then "+dirty" else ""} (last change to $tree$where)"
    ).getOrElse(s"vendored at ${sourceRoot.getFileName}; commit unknown")

  private def realpath(p: Path): Path =
    if Files.exists(p) then p.toRealPath() else p.normalize()

  /** stdout of a git command, trimmed — `None` on non-zero exit or any failure to run. */
  private def git(cwd: Path, args: String*): Option[String] =
    try
      val pb   = new ProcessBuilder(("git" +: args)*).directory(cwd.toFile).redirectErrorStream(false)
      val proc = pb.start()
      val out  = new String(proc.getInputStream.readAllBytes()).trim
      if proc.waitFor() == 0 then Some(out) else None
    catch case _: Exception => None
