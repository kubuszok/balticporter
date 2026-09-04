package balticporter.runner

import balticporter.tir.{ConfigError, DebugFlags}

import java.nio.file.Path

/** Run a port from a `.conf` — the whole engine, from a command line, with no Scala to write:
  * `PortConfigMain <port.conf> [--determinism=full|off]`. The REQUEST ([[PortConfig.load]]) is
  * separable from argument parsing. `CheckReport.dir` derives from the MAIN CLASS's simple name,
  * so this entry point reports into `port-report/PortConfigMain` — wrong for two ports, which is
  * why the corpus keeps a per-port `main`. */
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

  /** relative to `balticporter.root` when set, else the working directory — same rule as
    * `CorrelateRun.Request.absolute`: sbt's non-forked `run` uses the SUBPROJECT as its cwd. */
  private def absolute(s: String): Path =
    val p = Path.of(s)
    if p.isAbsolute then p.normalize else DebugFlags.root.resolve(p).normalize
