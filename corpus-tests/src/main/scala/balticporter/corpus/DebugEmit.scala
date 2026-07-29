package balticporter.corpus

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Phase, Pipeline, Program, TirPrinter}
import balticporter.transform.{CollectionsTransform, MutableParamsTransform, PanamaFfiTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Model a Java source tree ONCE and look at ONE type — as TIR, as emitted Scala, and at any
  * phase boundary you name.
  *
  * ```
  * corpus-tests/runMain balticporter.corpus.DebugEmit \
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
  * ## What it must not do
  *
  * The previous version of this file hardcoded ONE library's source root and one frontend. An
  * engine tool that only works on the library its author had open is not a tool. Every path here
  * is an argument; `--root` is required and there is no default.
  *
  * ## Flags
  *
  * | flag | |
  * |---|---|
  * | `--root <dir>` | Java source root (required). Relative paths resolve against `balticporter.root`. |
  * | `--include <substr>` | only CONVERT files whose path under the root contains this. Repeatable. |
  * | `--fast` | do not add the root as a resolution root — parse only the included files. Seconds instead of minutes on a large library, at the cost of resolution fidelity. |
  * | `--fqn <FullName>` | the one type to print. Omit to list what the model contains. |
  * | `--phases a,b` | run these transforms first — `${Phases.available}` |
  * | `--dump-before <phase>` / `--dump-after <phase>` | print the TIR at that boundary (`*` = every phase). Narrowed to `--fqn` automatically. |
  * | `--scala` | also print the emitted Scala for `--fqn` |
  * | `--canonical` | print the canonical form (no symbol ids, no origins) — the digest input |
  * | `--classpath <path>` | a resolution classpath entry. Repeatable. |
  * | `--lenient` | shadow-resolve what the classpath cannot see (matches the corpus migrations) |
  */
object DebugEmit:

  /** the universal transforms, under short names a shell will not mangle. A phase's own name
    * contains `->`, which is a redirection operator unquoted. */
  object Phases:
    val registry: Map[String, () => Phase] = Map(
      "collections"    -> (() => new CollectionsTransform),
      "mutable-params" -> (() => new MutableParamsTransform),
      "panama"         -> (() => new PanamaFfiTransform()),
    )
    def available: String = registry.keys.toList.sorted.mkString(", ")

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
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    def one(k: String): Option[String] = o.get(k).flatMap(_.headOption)
    def flag(k: String): Boolean       = o.contains(k)

    val rootArg = one("root").getOrElse {
      System.err.println("DebugEmit: --root <java-source-root> is required (there is deliberately no default).")
      System.err.println(s"  phases available to --phases: ${Phases.available}")
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

    val names   = one("phases").toList.flatMap(_.split(',').map(_.trim)).filter(_.nonEmpty)
    val unknown = names.filterNot(Phases.registry.contains)
    if unknown.nonEmpty then
      System.err.println(s"[debug-emit] unknown phase(s): ${unknown.mkString(", ")} — available: ${Phases.available}")
      sys.exit(2)
    val phases  = names.map(Phases.registry(_)())

    // The dump flags are read by `Pipeline.run` from system properties, so setting them HERE is
    // enough — same JVM, no marker file, no rebuild. (`DebugFlags` reads them as `def`s precisely
    // so a main class can do this.)
    //
    // `--dump-after` is given as a CLI ALIAS; the pipeline matches on the phase's OWN name. A
    // silently untranslated alias would print nothing and read as "the phase changed nothing" —
    // the exact failure mode a kill switch exists to avoid — so translate, and pass anything
    // unrecognised (`*`, a real phase name) through unchanged.
    val alias: Map[String, String] = Phases.registry.map((k, mk) => k -> mk().name)
    def phaseNames(v: String): String = v.split(',').map(_.trim).map(n => alias.getOrElse(n, n)).mkString(",")
    one("dump-before").foreach(v => System.setProperty("balticporter.dumpTirBefore", phaseNames(v)))
    one("dump-after").foreach(v => System.setProperty("balticporter.dumpTirAfter", phaseNames(v)))
    one("fqn").foreach(v => System.setProperty("balticporter.dumpOnly", v))

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
