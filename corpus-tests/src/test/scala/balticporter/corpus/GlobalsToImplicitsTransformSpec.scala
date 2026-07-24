package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.GlobalsToImplicitsTransform

/** globals → implicits: a context class's `static` state becomes an instance threaded as a
  * `using` parameter through the transitive closure of callers (via the call graph), with a
  * boundary `given` synthesized in the companion. Asserts the emitted Scala. */
class GlobalsToImplicitsTransformSpec extends munit.FunSuite:

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

  private val after = Pipeline.run(SpoonTir.fromSource(src), List(new GlobalsToImplicitsTransform(_.name == "Config")))
  private val out   = new TirEmitter(after).emit

  test("de-statics the context and reads its own members via this") {
    assert(clue(out).contains("var verbosity: scala.Int = 0"))  // no longer static
    assert(out.contains("return this.verbosity > 0"))           // C's own method: this.member
  }

  test("threads `using Config` through the call-graph closure") {
    assert(out.contains("def log(msg: java.lang.String)(using ctx: Config)")) // direct reference
    assert(out.contains("def run(l: Logger, m: java.lang.String)(using ctx: Config)")) // caller of log — closure
    assert(out.contains("l.log(m)")) // call site UNCHANGED: `using` auto-forwards from `ctx` in scope
  }

  test("rewrites a static reference to the threaded context") {
    assert(out.contains("if (ctx.enabled())")) // Config.enabled() -> ctx.enabled()
  }

  test("synthesizes a boundary given in the companion") {
    assert(out.contains("object Config"))
    assert(out.contains("given ConfigCtx: Config = new Config"))
  }
