package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.GlobalsToImplicitsTransform

/** Demonstrates globals → implicits: a class `Config` whose `static` state is ambient context
  * becomes an instance threaded as a `using Config` through every method that reaches it
  * (found via the call graph), with a boundary `given` synthesized in its companion.
  *
  *   corpus/runMain balticporter.corpus.GlobalsDemo
  */
object GlobalsDemo:

  private val src =
    """package demo;
      |class Config {
      |  static int verbosity = 0;
      |  static boolean enabled() { return verbosity > 0; }
      |}
      |class Logger {
      |  void log(String msg) { if (Config.enabled()) System.out.println(msg); }
      |}
      |class App {
      |  void run(Logger l, String m) { l.log(m); }
      |}
      |""".stripMargin

  private val transform = new GlobalsToImplicitsTransform(_.name == "Config")

  def main(args: Array[String]): Unit =
    val before = SpoonTir.fromSource(src)
    println("// ===== BEFORE =====\n")
    println(new TirEmitter(before).emit)

    val after = Pipeline.run(before, List(transform))
    println("\n// ===== AFTER (globals -> implicits) =====\n")
    println(new TirEmitter(after).emit)
