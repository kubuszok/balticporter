package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** An array handed to an external method's `T[]` formal, `T` the method's own type variable: scala infers `T` from the argument, so the argument stays its own array type. */
class GenericArrayArgSpec extends munit.FunSuite:

  private def emit(src: String): String = new TirEmitter(SpoonTir.fromSource(src)).emit

  test("Arrays.copyOf keeps the argument's array type, so the result fits the String[] slot") {
    val out = emit(
      """package demo;
        |class Settings {
        |  String[] suffixes = { "a", "b" };
        |  String[] copy() { return java.util.Arrays.copyOf(suffixes, suffixes.length); }
        |}
        |""".stripMargin
    )
    assert(
      clue(out).contains("java.util.Arrays.copyOf(this.suffixes, this.suffixes.length)") || out.contains("java.util.Arrays.copyOf(suffixes, suffixes.length)")
    )
    assert(!out.contains("asInstanceOf[scala.Array[java.lang.Object]]"))
  }

  test("a class-file Object[] formal still takes java's covariant array") {
    val out = emit(
      """package demo;
        |class Fill {
        |  String[] names = { "a" };
        |  void clear() { java.util.Arrays.fill(names, null); }
        |}
        |""".stripMargin
    )
    assert(clue(out).contains("asInstanceOf[scala.Array[java.lang.Object]]"))
  }
