package balticporter.corpus.demo

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.{ContextHolder, ContextType, GlobalsToImplicitsTransform}

/** Demonstrates globals → context: a class `Config` whose `static` state is ambient context
  * becomes a value threaded as an anonymous `(using Ctx)` through every declaration that reaches
  * it — found by the five-edge closure, not the call graph — with every read rewritten to a
  * summon, and NO ambient `given` anywhere (DESIGN.md §8.4). */
object GlobalsDemo:

  private val src =
    """package demo;
      |class Config {
      |  static int verbosity = 0;
      |  static boolean enabled() { return verbosity > 0; }
      |}
      |class Logger {
      |  void log(String msg) { if (Config.verbosity > 0) System.out.println(msg); }
      |}
      |class App {
      |  void run(Logger l, String m) { l.log(m); }
      |}
      |""".stripMargin

  private val transform = new GlobalsToImplicitsTransform(List(ContextHolder(
    holder  = "demo.Config",
    context = ContextType.Minted("demo.Ctx"),
    members = Map("verbosity" -> "verbosity"),
  )))

  def main(args: Array[String]): Unit =
    val before = SpoonTir.fromSource(src)
    println("// ===== BEFORE =====\n")
    println(new TirEmitter(before).emit)

    val (after, log) = Pipeline.runTraced(before, List(transform))
    println("\n// ===== AFTER (globals -> context) =====\n")
    println(new TirEmitter(after, notes = log).emit)
    println(s"\n// decisions: ${log.summary}")
    println(s"// seams: ${transform.seams(after).size}")
