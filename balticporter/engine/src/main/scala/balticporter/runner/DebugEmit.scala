package balticporter.runner

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ConfigError, DebugFlags, Phase, Pipeline, Program, TirPrinter}
import balticporter.transform.CollectionsTransform

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Model a Java source tree ONCE and look at ONE type — as TIR, as emitted Scala, and at any
  * phase boundary you name. `just debug-emit --root <dir> --fqn <Type>`.
  *
  * ```
  * engine/runMain balticporter.runner.DebugEmit \
  *   --root ../sge/original-src/libgdx/gdx/src \
  *   --include utils/ \
  *   --fqn com.badlogic.gdx.utils.DelayedRemovalArray \
  *   --phases collections,mutable-params --dump-before '*' --dump-after '*'
  * ```
  *
  * ## What this replaces
  *
  * Two techniques this repository used instead, both recorded as gaps by the readiness audit:
  * copying `src_managed`, flipping a local `debug` flag and recompiling; and reading a
  * case-class `toString`. The first costs a full build per question, the second is unreadable.
  *
  * ## Why it lives in `engine` and not in `corpus`
  *
  * It was written in `corpus`, which is `publish / skip := true`. The user of a debugging tool is
  * an agent in ANOTHER repository (CLAUDE.md §4.45) holding the published engine, and a tool that
  * ships in no artifact is a tool that agent does not have — the same reason an engine limit may
  * not live only in a per-library status file. Nothing here was corpus-specific: every path is an
  * argument and every phase named below is a universal transform, so the move is a package line.
  *
  * It deliberately does NOT take a port `.conf`. That would be convenient — the paths and the
  * phase list are already written down there — and it would make this class a SECOND assembly path
  * for a port's pipeline, beside `PortRun`'s, free to drift from it (DESIGN.md §5.7's "not a second
  * truth" is the same argument one level down). What this prints is the pipeline's view of one
  * type, not a port's emitted file: there is no substitution, no injection, no package rename and
  * no provenance header here. Reproducing a port's output is `PortRun`'s job, and `just debug-emit`
  * says so rather than approximating it.
  *
  * ## What it must not do
  *
  * The first version of this file hardcoded ONE library's source root and one frontend. An engine
  * tool that only works on the library its author had open is not a tool. Every path here is an
  * argument; `--root` is required and there is no default.
  *
  * ## Flags
  *
  * | flag | |
  * |---|---|
  * | `--root <dir>` | Java source root (required). Relative paths resolve against `balticporter.root`. |
  * | `--include <substr>` | only CONVERT files whose path under the root contains this. Repeatable. |
  * | `--fast` | do not add the root as a resolution root — parse only the included files. Seconds instead of minutes on a large library, at the cost of resolution fidelity. |
  * | `--fqn <FullName>` | the one type to print. Omit to list what the model contains. |
  * | `--phases a,b` | run these transforms first, named EXACTLY as a port `.conf` names them (`collections`, `mutable-params`, `panama-ffi`, …), or by their OWN name for the ones `PortRun` weaves and no `.conf` can configure (`sam-anon->lambda`, `return-this-census`). Resolved through the same `TransformFactory` SPI plus that woven list — run with an unknown name to have the whole set printed. A phase that needs policy is refused here and belongs to `PortRun`. |
  * | `--dump-before <phase>` / `--dump-after <phase>` | print the TIR at that boundary (`*` = every phase). Narrowed to `--fqn` automatically. |
  * | `--scala` | also print the emitted Scala for `--fqn` |
  * | `--canonical` | print the canonical form (no symbol ids, no origins) — the digest input |
  * | `--classpath <path>` | a resolution classpath entry. Repeatable. |
  * | `--lenient` | shadow-resolve what the classpath cannot see (matches the corpus migrations) |
  */
object DebugEmit:

  /** Resolve `--phases` through the SPI — the SAME name → phase resolution a port `.conf` uses,
    * and the reason this class no longer has a registry of its own.
    *
    * It had one: three entries, and it had already DIVERGED — it called the FFI phase `panama`
    * while the config front door calls it `panama-ffi`, so the two doors disagreed about the name
    * of a phase and neither was wrong on its own terms. That is the standing cost of a second truth
    * (DESIGN.md §5.7), paid here by an agent who reads one name in a `.conf` and types it into a
    * diagnostic that answers "unknown phase". Resolving through [[TransformRegistry]] also widens
    * this from three phases to every default-constructible one, with nothing to maintain.
    *
    * It is name resolution, NOT pipeline assembly, and the distinction is what keeps this class
    * from becoming a second way to build a port: no `.conf` is read, so no policy can enter here.
    * A factory that REQUIRES policy therefore throws its own [[balticporter.tir.ConfigError]]
    * against the empty config, and that refusal is passed through with the one thing the operator
    * needs to hear — a phase configured by a port is driven by the port, through `PortRun`.
    *
    * ==…AND THE SPI IS NOT THE WHOLE PIPELINE, which is what made this answer wrongly==
    * A phase reaches a `TransformFactory` only if a port may CONFIGURE it, and the idiom layer may
    * not: it is §1(a), so a knob on it is the shape §1 forbids, and `PortRun` WEAVES it into every
    * pipeline instead. Resolved through the registry alone, `--phases sam-anon->lambda` therefore
    * answered "unknown transform" — about a phase that runs in every port, cannot be turned off, and
    * is exactly the kind an agent reaches for this tool to bracket. §4.6's promise is that "is this
    * phase even responsible" costs one run and no diff; it does not hold for a phase the tooling
    * cannot name.
    *
    * So the woven list is consulted FIRST, by the phase's OWN name — which is the name
    * `skipPhases`, `dumpTirBefore` and `dumpTirAfter` already take, so an operator types one string
    * everywhere — and it is `PortRun`'s list rather than a copy, because a second construction site
    * would model a pipeline the run does not have.
    *
    * Returns `Left(message)` rather than exiting, so a spec can assert what the operator is told
    * without taking the JVM down with it. */
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
                // the factory's own error, VERBATIM and with the key it names — a message rewritten
                // here would be a second statement of a contract only the factory holds.
                Left(s"[debug-emit] '$n' takes POLICY and this tool reads no port configuration — " +
                  s"drive it through `PortRun` with the port's `.conf`. The factory said: ${e.where}: ${e.why}")
              case e: ConfigError =>
                Left(s"[debug-emit] ${e.why}\n  available: ${nameable(registry).mkString(", ")}")
      }
    }

  /** one of the phases `PortRun` weaves into every pipeline, by its own `name`.
    *
    * A FRESH instance per call, and that is what `PortRun.wovenIdiomPhases` being a `def` buys: a
    * phase carries the buffers it fills, so two `--phases` runs handed one instance would file each
    * other's rows. */
  private def wovenPhase(name: String): Option[Phase] =
    PortRun.wovenIdiomPhases.find(_.name == name)

  /** every name `--phases` accepts — the SPI's and the woven ones, which is what an unknown-name
    * error has to list or it sends the reader looking for a factory that does not exist. */
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

    // The dump flags are read by `Pipeline.run` from system properties, so setting them HERE is
    // enough — same JVM, no marker file, no rebuild. (`DebugFlags` reads them as `def`s precisely
    // so a main class can do this.)
    //
    // …and they are RESTORED afterwards, because this main is the one that may run UNFORKED, inside
    // the `sbt -client` server: a flag left behind there outlives the command that set it and is
    // then a debug flag nobody can see poisoning a later invocation — exactly the failure
    // `just debug-clear` exists for, one layer up.
    //
    // `--dump-after` is given as a CONFIG NAME (`collections`); the pipeline matches on the phase's
    // OWN name (`java-collections->scala`). A silently untranslated alias would print nothing and
    // read as "the phase changed nothing" — the exact failure mode a kill switch exists to avoid —
    // so translate, and pass anything unrecognised (`*`, a real phase name) through unchanged.
    // Built from the phases actually CONSTRUCTED, so a name is never translated by instantiating a
    // factory that would have refused the empty config.
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
    // `--fast` parses ONLY the included files. Without it the whole root participates in
    // resolution (what a real port does), which for a large library is the whole library — the
    // `--include` filter then narrows what is CONVERTED, not what is parsed.
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
