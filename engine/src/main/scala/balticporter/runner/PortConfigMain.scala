package balticporter.runner

import balticporter.tir.{ConfigError, DebugFlags}

import java.nio.file.Path

/** Run a port from a `.conf` — the whole engine, from a command line, with no Scala to write.
  *
  * {{{
  * PortConfigMain <port.conf> [--determinism=full|off]
  * }}}
  *
  * Named for `CorrelateMain`, and shaped like it: the REQUEST ([[PortConfig.load]]) is separable
  * from the argument parsing, so a porting program that wants its own `main` — to name its report
  * directory, to run two source sets, to pass a factory registry it built itself — calls the loader
  * directly and never touches this class. The corpus's own simple-graphs migration does exactly
  * that, and is the proof that a migration program is a conf plus three lines.
  *
  * ==The report directory==
  * `CheckReport.dir` is derived from the MAIN CLASS's simple name, so every port run through this
  * entry point reports into `port-report/PortConfigMain`. That is correct for one port and wrong for
  * two, and it is why the corpus keeps a per-port `main`: the report directory is measurement
  * identity, and two ports sharing one is two baselines overwriting each other. A consumer running
  * several ports from confs sets `balticporter.reportDir` per invocation, or writes the same
  * three-line `main`.
  */
object PortConfigMain:

  def main(args: Array[String]): Unit =
    val confs = args.filterNot(_.startsWith("--")).toList
    if confs.sizeIs != 1 then
      System.err.println(
        "usage: PortConfigMain <port.conf> [--determinism=full|off]\n" +
          "  one configuration file per run — a run is one source set of one module, and its " +
          "report directory is its measurement identity.")
      sys.exit(2)
    try PortConfig.load(absolute(confs.head), args.toSeq).execute()
    catch
      case e: ConfigError =>
        System.err.println(e.getMessage)
        sys.exit(1)

  /** relative to `balticporter.root` when set, else to the working directory — the same rule
    * `CorrelateRun.Request.absolute` follows, and for the same reason: sbt's non-forked `run` has
    * the SUBPROJECT as its working directory, so a path that reads correctly in a shell resolves to
    * nothing here. */
  private def absolute(s: String): Path =
    val p = Path.of(s)
    if p.isAbsolute then p.normalize else DebugFlags.root.resolve(p).normalize
