package balticporter.runner

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ConfigError, DebugFlags, Phase, Pipeline, Program, TirPrinter}
import balticporter.transform.CollectionsTransform

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Model a Java source tree once and print one type as TIR / emitted Scala, optionally around
  * named phases. Does NOT take a `.conf` — it is pipeline inspection, not port assembly.
  *
  * Flags: `--root` (required), `--fqn`, `--phases a,b`, `--dump-before`/`--dump-after`,
  * `--scala`, `--canonical`, `--include`, `--fast`, `--classpath`, `--lenient`. */
object DebugEmit:

  /** Resolve `--phases` names through woven phases first, then the SPI registry.
    * A factory requiring policy throws `ConfigError`; woven §1(a) phases are resolved by own name.
    * Returns `Left(message)` for testability. */
  def phasesFor(names: List[String],
                registry: TransformRegistry = TransformRegistry.discover()): Either[String, List[Phase]] =
    val empty = HoconView.root(com.typesafe.config.ConfigFactory.empty)
    names.foldLeft[Either[String, List[Phase]]](Right(Nil)) { (acc, n) =>
      acc.flatMap { done =>
        wovenPhase(n) match
          case Some(p) => Right(done :+ p)
          case scala.None =>
            try Right(done :+ registry.phase(n, empty, s"--phases $n"))
            catch
              case e: ConfigError if registry.get(n).isDefined =>
                Left(s"[debug-emit] '$n' takes POLICY and this tool reads no port configuration — " +
                  s"drive it through `PortRun` with the port's `.conf`. The factory said: ${e.where}: ${e.why}")
              case e: ConfigError =>
                Left(s"[debug-emit] ${e.why}\n  available: ${nameable(registry).mkString(", ")}")
      }
    }

  /** A woven phase by own name. Fresh instance per call (phases carry mutable buffers). */
  private def wovenPhase(name: String): Option[Phase] =
    PortRun.wovenIdiomPhases.find(_.name == name)

  /** Every name `--phases` accepts: SPI factories + woven phases. */
  def nameable(registry: TransformRegistry): List[String] =
    (registry.names ++ PortRun.wovenIdiomPhases.map(_.name)).distinct.sorted

  private def opts(args: List[String]): Map[String, List[String]] =
    args.foldLeft((Map.empty[String, List[String]], Option.empty[String])) {
      case ((m, _), a) if a.startsWith("--") =>
        val k = a.drop(2)
        (m.updated(k, m.getOrElse(k, Nil)), Some(k))
      case ((m, Some(k)), v) => (m.updated(k, m(k) :+ v), Some(k))
      case ((m, scala.None), v) => (m.updated("", m.getOrElse("", Nil) :+ v), scala.None)
    }._1

  def main(args: Array[String]): Unit =
    val o        = opts(args.toList)
    val repoRoot = DebugFlags.root
    def one(k: String): Option[String] = o.get(k).flatMap(_.headOption)
    def flag(k: String): Boolean       = o.contains(k)

    val registry = TransformRegistry.discover()
    val rootArg = one("root").getOrElse {
      System.err.println("DebugEmit: --root <java-source-root> is required (there is deliberately no default).")
      System.err.println(s"  phases available to --phases: ${nameable(registry).mkString(", ")}")
      sys.exit(2)
    }
    val root = { val p = Path.of(rootArg); (if p.isAbsolute then p else repoRoot.resolve(p)).normalize }
    if !Files.isDirectory(root) then
      System.err.println(s"DebugEmit: --root is not a directory: $root")
      sys.exit(2)

    val includes = o.getOrElse("include", Nil)
    val files = Files.walk(root).iterator().asScala
      .filter(_.toString.endsWith(".java"))
      .map(p => root.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .filter(f => includes.isEmpty || includes.exists(f.contains))
      .toList.sorted
    if files.isEmpty then
      System.err.println(s"DebugEmit: no .java under $root matching ${includes.mkString(", ")}")
      sys.exit(2)

    val names  = one("phases").toList.flatMap(_.split(',').map(_.trim)).filter(_.nonEmpty)
    val phases = phasesFor(names, registry) match
      case Right(ps)  => ps
      case Left(why)  => System.err.println(why); sys.exit(2)

    // Set dump flags as system properties (restored afterwards for unforked sbt -client runs).
    // Translate config names to phase own names for --dump-before/--dump-after.
    val alias: Map[String, String] = names.zip(phases.map(_.name)).toMap
    def phaseNames(v: String): String = v.split(',').map(_.trim).map(n => alias.getOrElse(n, n)).mkString(",")
    val touched = List(
      one("dump-before").map(v => "dumpTirBefore" -> phaseNames(v)),
      one("dump-after").map(v => "dumpTirAfter" -> phaseNames(v)),
      one("fqn").map(v => "dumpOnly" -> v),
    ).flatten
    val saved = touched.map((k, _) => k -> Option(System.getProperty(DebugFlags.Prefix + k)))
    touched.foreach((k, v) => System.setProperty(DebugFlags.Prefix + k, v))
    try run(o, root, files, phases, one, flag)
    finally saved.foreach {
      case (k, Some(v))    => System.setProperty(DebugFlags.Prefix + k, v)
      case (k, scala.None) => System.clearProperty(DebugFlags.Prefix + k)
    }

  private def run(
      o: Map[String, List[String]], root: Path, files: List[String], phases: List[Phase],
      one: String => Option[String], flag: String => Boolean,
  ): Unit =
    val cp = o.getOrElse("classpath", Nil).map(Path.of(_))
    // `--fast` skips resolution roots; `--include` narrows conversion, not parsing.
    val resolutionRoots = if flag("fast") then Nil else List(root)
    println(s"[debug-emit] modelling ${files.size} file(s) under $root" +
      (if flag("fast") then " (--fast: no resolution roots)" else " (+ the whole root for resolution)") + "…")
    val types   = SpoonTir.buildModel(FrontendConfig(root, files, cp, resolutionRoots), lenient = flag("lenient"))
    val raw     = SpoonTir.fromTypes(types)
    val program = if phases.isEmpty then raw else Pipeline.run(raw, phases)
    given Program = program
    println(s"[debug-emit] TIR: ${program.units.size} units, ${program.symbols.all.size} symbols" +
      (if phases.isEmpty then " (no phases)" else s" after ${phases.map(_.name).mkString(", ")}"))

    val style = if flag("canonical") then TirPrinter.Style.canonical else TirPrinter.Style.debug
    one("fqn") match
      case scala.None =>
        println("[debug-emit] no --fqn given; the model contains:")
        program.units.flatMap(u => program.symbolOf(u.symbol).map(_.fullName)).sorted.foreach(n => println(s"  $n"))
      case Some(fqn) =>
        TirPrinter.unit(fqn, style) match
          case scala.None =>
            System.err.println(s"[debug-emit] no unit named '$fqn' — run without --fqn to list them")
            sys.exit(1)
          case Some(text) =>
            println(s"===== TIR $fqn =====")
            println(text)
            if flag("scala") then
              val emitter = new TirEmitter(program, CollectionsTransform.runtimeConcreteMembers)
              program.units.find(u => program.symbolOf(u.symbol).exists(_.fullName == fqn)).foreach { u =>
                println(s"===== SCALA $fqn =====")
                println(emitter.emitUnit(u))
              }
