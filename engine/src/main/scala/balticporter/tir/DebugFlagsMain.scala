package balticporter.tir

import java.nio.file.{Files, Path}

/** SHOW the §4.6 flag resolution — which layer defines each flag right now, what the merged result
  * is, and what a run already recorded. `just debug-flags [PORT]`.
  *
  * {{{
  * engine/runMain balticporter.tir.DebugFlagsMain --root /path/to/checkout [--port LibgdxCoreMigrate]
  * }}}
  *
  * ==Why this is a program and not a `cat`==
  *
  * Two files and a precedence rule look like something a shell can print, and that is the trap: the
  * rule ("system property, then `debug.properties`, then `run.properties`") would then exist twice,
  * and the copy that explains a run would be free to disagree with the copy that performs it. So
  * the merge is [[DebugFlags.resolution]] — the same fold [[DebugFlags.get]] uses — and this class
  * only renders it.
  *
  * ==What it can and cannot see==
  *
  * The layer this JVM CANNOT speak for is the system properties of the migration, because the
  * migration is a JVM forked from a long-running `sbt -client` server: its `-D`s come from
  * `build.sbt`, never from the caller's command line or environment (that is the whole reason the
  * marker files exist). So the system-property layer printed here is THIS process's, labelled as
  * such, and the two FILE layers are the ones that genuinely predict the next run.
  *
  * `--port` closes the remaining gap from the other end: the run's own report records the flags it
  * actually saw ([[CheckReport]] writes them into `report.md`), so "did my flag reach the run" is
  * answerable after the fact instead of by re-running with a print statement.
  */
object DebugFlagsMain:

  private def usage(): Nothing =
    System.err.println(
      """usage: DebugFlagsMain [--root <dir>] [--port <PortReportDir>]
        |  --root <dir>   the checkout whose .balticporter/ is read (default: balticporter.root, else cwd)
        |  --port <name>  also print what port-report/<name>'s last run recorded
        |""".stripMargin)
    sys.exit(2)

  def main(args: Array[String]): Unit =
    var root = DebugFlags.root
    var port = scala.Option.empty[String]
    var i    = 0
    while i < args.length do
      def next(): String = { i += 1; if i < args.length then args(i) else usage() }
      args(i) match
        case "--root"        => root = Path.of(next()).toAbsolutePath.normalize
        case "--port"        => port = Some(next())
        case "-h" | "--help" => usage()
        case other           => System.err.println(s"unknown option: $other"); usage()
      i += 1
    println(render(root, DebugFlags.sysFlags, port))

  /** the whole report as a string — separable from the argument parsing so a spec can assert on it
    * (the shape every other main in this package follows, and for the same reason). */
  def render(root: Path, sysProps: Map[String, String], port: Option[String]): String =
    val sb     = new StringBuilder
    val layers = DebugFlags.layers(root, sysProps)
    sb.append(s"[balticporter] flag resolution under $root\n\n")

    sb.append("LAYERS, in increasing precedence:\n")
    layers.foreach { l =>
      val where = l.file.map(_.toString).getOrElse("(this JVM)")
      val state =
        if !l.present then "ABSENT"
        else if l.props.isEmpty && l.ignored.isEmpty then "empty"
        else s"${l.props.size} flag(s)" + (if l.ignored.nonEmpty then s", ${l.ignored.size} ignored" else "")
      sb.append(f"  ${l.name}%-18s $where%-70s $state\n")
    }

    sb.append("\nEFFECTIVE — what a run started now would resolve:\n")
    val res = DebugFlags.resolution(root, sysProps)
    if res.isEmpty then sb.append("  (none — every flag is unset, and every consumer therefore a no-op)\n")
    else
      val w = res.map(_.key.length).max
      res.foreach { r =>
        val shadow =
          if r.shadowed.isEmpty then ""
          else s"  (shadows ${r.shadowed.map((s, v) => s"$s=$v").mkString(", ")})"
        // A key nothing reads is a flag that does nothing, and the run it was meant for looks
        // entirely normal — the failure `just debug-flags` is called about, one spelling earlier.
        val unknown = if DebugFlags.known.contains(r.key) then "" else "   !! UNKNOWN KEY — nothing reads it"
        sb.append(s"  ${r.key.padTo(w, ' ')} = ${r.value}   [${r.source}]$shadow$unknown\n")
      }

    // The failure this exists to make visible: a hand-written entry that no accessor will ever
    // look up, because every key is read as `balticporter.<name>`. It is a flag that does nothing,
    // and nothing else in the engine can report it — the run it was meant for is simply normal.
    val ignored = layers.filter(_.ignored.nonEmpty)
    if ignored.nonEmpty then
      sb.append("\n!! IGNORED — entries no accessor can read; every key must start with `balticporter.`:\n")
      ignored.foreach(l => l.ignored.toList.sorted.foreach((k, v) => sb.append(s"     ${l.name}: $k=$v\n")))

    sb.append(
      "\nNOTE  a migration runs in a JVM FORKED from the sbt server, so it sees the two FILES plus\n" +
        "      build.sbt's javaOptions — never your shell's environment, never a -D on your command\n" +
        "      line (CLAUDE.md §4.6). The system-property layer above is THIS process's.\n" +
        "      Set one with `just debug-set <key> <value>`; clear them with `just debug-clear`.\n")

    port.foreach(p => sb.append('\n').append(recorded(root, p)))
    sb.result()

  /** what the named port's LAST run recorded — the other end of the same question. */
  private def recorded(root: Path, port: String): String =
    val dir = root.resolve(s"port-report/$port/run-latest")
    val md  = dir.resolve("report.md")
    if !Files.isRegularFile(md) then
      val known =
        val d = root.resolve("port-report")
        if !Files.isDirectory(d) then Nil
        else
          val s = Files.list(d)
          try s.toArray(new Array[Path](_)).toList.filter(Files.isDirectory(_)).map(_.getFileName.toString).sorted
          finally s.close()
      s"!! no run for '$port' at $md\n" +
        (if known.isEmpty then "   (no port-report/ in this checkout — nothing has run here)\n"
         else s"   ports with a report directory: ${known.mkString(", ")}\n")
    else
      val line = Files.readAllLines(md).toArray(Array.empty[String]).toList
        .find(_.startsWith("debug flags:"))
        .getOrElse("debug flags: (not recorded — this run predates the record)")
      s"AS RECORDED BY THE LAST RUN of $port ($md):\n  $line\n"
