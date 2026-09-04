package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** A constant declared INSIDE an anonymous class must never be emitted as `inline val` (there is no
  * companion to put it in), and a reference to it must never go through the class's synthetic FQN
  * (whose numeric suffix becomes a syntax error after package rename: `Skin.107.K`). */
class AnonClassEmitSpec extends munit.FunSuite:

  // pre-16: instance constant — `final String K = "k"` (not static, allowed in every version)
  private val instanceConstSrc =
    """package demo;
      |class C {
      |  void go() {
      |    new Runnable() {
      |      final String K = "k";
      |      public void run() { System.out.println(K); }
      |    }.run();
      |  }
      |}
      |""".stripMargin

  test("an instance constant in an anonymous class is emitted as a plain val, never inline val") {
    val p = SpoonTir.fromSource(instanceConstSrc)
    val emitted = new TirEmitter(p).emit
    assert(!emitted.contains("inline val"), s"anonymous class constant must not be inline val:\n$emitted")
    assert(emitted.contains("val K"), s"anonymous class constant should be a plain val:\n$emitted")
  }

  test("a reference to an anonymous class instance constant uses the bare name, not a FQN") {
    val p = SpoonTir.fromSource(instanceConstSrc)
    val emitted = new TirEmitter(p).emit
    // the reference to K inside `run()` must NOT go through a fully-qualified path with a numeric
    // anonymous-class suffix (which would produce `demo.C.1.K` — a syntax error)
    assert(!emitted.matches("(?s).*\\b\\d+\\.K\\b.*"),
      s"FQN reference through numeric anonymous class suffix:\n$emitted")
  }
