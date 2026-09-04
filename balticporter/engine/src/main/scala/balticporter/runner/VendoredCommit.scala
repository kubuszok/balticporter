package balticporter.runner

import java.nio.file.Path

/** The truthful `Provenance.upstreamCommit` for a VENDORED source tree (CLAUDE.md §4.57). The
  * honest pin is the LAST COMMIT THAT TOUCHED THE TREE (never `HEAD`, which churns on every
  * unrelated commit) plus the repo's `origin` URL when it has one. A dirty tree is stated
  * (`+dirty`); where git answers nothing, `commit unknown` rather than inventing an anchor. Paths
  * are realpathed before relativising (§5.4). */
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

  private def realpath(p: Path): Path = balticporter.core.RealPath.of(p)

  /** stdout of a git command, trimmed — `None` on non-zero exit or any failure to run. */
  private def git(cwd: Path, args: String*): Option[String] =
    try
      val pb   = new ProcessBuilder(("git" +: args)*).directory(cwd.toFile).redirectErrorStream(false)
      val proc = pb.start()
      val out  = new String(proc.getInputStream.readAllBytes()).trim
      if proc.waitFor() == 0 then Some(out) else None
    catch case _: Exception => None
