package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.PanamaFfiTransform

/** Demonstrates JNI → Panama: `native` methods become generated `java.lang.foreign` downcall
  * bindings (a MethodHandle over a FunctionDescriptor built from the signature).
  *
  *   corpus-tests/runMain balticporter.corpus.PanamaDemo
  */
object PanamaDemo:

  private val src =
    """package demo;
      |class Native {
      |  public static native int add(int a, int b);
      |  public static native double scale(double x, long n);
      |  public static native void log(int level);
      |}
      |""".stripMargin

  def main(args: Array[String]): Unit =
    val before = SpoonTir.fromSource(src)
    println("// ===== BEFORE =====\n")
    println(new TirEmitter(before).emit)

    val after = Pipeline.run(before, List(new PanamaFfiTransform()))
    println("\n// ===== AFTER (JNI -> Panama FFI) =====\n")
    println(new TirEmitter(after).emit)
