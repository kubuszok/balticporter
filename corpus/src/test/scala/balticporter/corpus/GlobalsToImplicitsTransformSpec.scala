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
    // cross-unit type references are fully qualified (FQN emission, no imports).
    assert(out.contains("def log(msg: java.lang.String)(using ctx: demo.Config)")) // direct reference
    assert(out.contains("def run(l: demo.Logger, m: java.lang.String)(using ctx: demo.Config)")) // caller of log — closure
    assert(out.contains("l.log(m)")) // call site UNCHANGED: `using` auto-forwards from `ctx` in scope
  }

  test("rewrites a static reference to the threaded context") {
    assert(out.contains("if (ctx.enabled())")) // Config.enabled() -> ctx.enabled()
  }

  test("the threaded methods and the de-statified members each leave a §1(b) row") {
    // `Pipeline.runTraced`, not `run`: the latter drains each phase's buffer into a log it discards.
    val log = Pipeline.runTraced(SpoonTir.fromSource(src),
      List(new GlobalsToImplicitsTransform(_.name == "Config")))._2
    val ds = log.of(balticporter.tir.Decision.Kind.RetypedSignature)

    // keyed by the CONTEXT CLASS — the mechanism is general and which class is an ambient context
    // is the one thing the porting program decides, through `isContext`
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == balticporter.tir.Reason.Configured("globals->implicits", "demo.Config")))

    val fqns = ds.map(_.subjectFqn).toSet
    assert(clue(fqns).contains("demo.Logger#log"))  // direct reference
    assert(fqns.contains("demo.App#run"))           // caller of it — the closure
    assert(fqns.contains("demo.Config#verbosity"))  // the static that became instance state
    // a CALL into a threaded method is not a row: its argument comes from the `using` in scope, so
    // the call site did not change at all — which is why `using` was chosen over a parameter.
    assert(ds.forall(_.detail.contains("key")))
  }

  test("no context class, no decisions") {
    val log = Pipeline.runTraced(SpoonTir.fromSource(src),
      List(new GlobalsToImplicitsTransform(_.name == "NoSuchClass")))._2
    assertEquals(log.all, Nil)
  }

  test("synthesizes a boundary given in the companion") {
    assert(out.contains("object Config"))
    assert(out.contains("given ConfigCtx: Config = new Config"))
  }
