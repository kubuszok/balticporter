package balticporter.tir

import java.nio.file.{Files, Path}

/** Renders the §4.6 flag resolution: layers, effective merge, and what a port's last run recorded.
  *
  * Uses [[DebugFlags.resolution]] (the same fold [[DebugFlags.get]] reads). The system-property
  * layer shown is THIS process's, not the forked migration's. `--port` shows what the last run
  * actually saw. `just debug-flags [PORT]`. */
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

  /** The whole report as a string, separable from argument parsing for testability. */
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
        // a key nothing reads is a flag that does nothing
        val unknown = if DebugFlags.known.contains(r.key) then "" else "   !! UNKNOWN KEY — nothing reads it"
        // port-supplied flag left here silently changes emitted output
        val fallback =
          if DebugFlags.PortSupplied.contains(r.key)
          then "   (FALLBACK — a port states this in its own configuration and IGNORES this flag; " +
            "set here only for a tool that has no port)"
          else ""
        sb.append(s"  ${r.key.padTo(w, ' ')} = ${r.value}   [${r.source}]$shadow$unknown$fallback\n")
      }

    // entries no accessor reads (every key must start with `balticporter.`)
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

  /** What the named port's last run recorded. */
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
