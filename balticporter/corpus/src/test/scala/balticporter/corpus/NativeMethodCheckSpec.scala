package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ NativeMethodCheck, Pipeline }
import balticporter.transform.PanamaFfiTransform

/** The `jni-native` lane: every JNI `native` method emitted as a bodiless `@scala.native` is one row, so a JVM-only port is visible rather than found at a Scala.js or Scala Native link.
  */
class NativeMethodCheckSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Lib {
      |  public static native int add(int a, int b);
      |  native void poke();
      |  int plain() { return 1; }
      |  static class Inner { native long handle(); }
      |}
      |""".stripMargin

  test("each bodiless native is one row, nested classes included, and each row is what the emitter annotates") {
    val p   = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val fs  = NativeMethodCheck.check(p, p.units)
    val out = new TirEmitter(p).emit
    assertEquals(clue(fs.map(f => f.member)).sorted, List("add", "handle", "poke"))
    assertEquals(clue(out).linesIterator.count(_.trim == "@scala.native"), fs.size)
    assert(fs.forall(_.report.check == NativeMethodCheck.Name))
  }

  test("NEGATIVE: a native the FFI phase rewrote has a body and is not counted") {
    val p = Pipeline.run(SpoonTir.fromSource(src), List(new PanamaFfiTransform()))
    assertEquals(clue(NativeMethodCheck.check(p, p.units)), Nil)
  }
