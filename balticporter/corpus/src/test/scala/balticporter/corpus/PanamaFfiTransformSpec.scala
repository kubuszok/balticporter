package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.PanamaFfiTransform

/** JNI → Panama: `native` methods become generated `java.lang.foreign` downcall bindings —
  * a MethodHandle over a FunctionDescriptor built from the signature. Asserts the emitted
  * Scala; the generated bindings compile against java.lang.foreign (JDK 22+). */
class PanamaFfiTransformSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Native {
      |  public static native int add(int a, int b);
      |  public static native double scale(double x, long n);
      |  public static native void log(int level);
      |}
      |""".stripMargin

  private val after = Pipeline.run(SpoonTir.fromSource(src), List(new PanamaFfiTransform()))
  private val out   = new TirEmitter(after).emit

  test("generates a downcall MethodHandle per native, with a signature-derived descriptor") {
    assert(clue(out).matches("(?s).*private val add\\$\\d+\\$handle: java.lang.invoke.MethodHandle.*"))
    assert(out.contains("""java.lang.foreign.Linker.nativeLinker().downcallHandle("""))
    assert(out.contains("""defaultLookup().find("add").orElseThrow()"""))
    // int add(int,int) → descriptor of JAVA_INT × 3
    assert(out.contains("java.lang.foreign.FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT, java.lang.foreign.ValueLayout.JAVA_INT)"))
    // double scale(double,long) → mixed layouts
    assert(out.contains("FunctionDescriptor.of(java.lang.foreign.ValueLayout.JAVA_DOUBLE, java.lang.foreign.ValueLayout.JAVA_DOUBLE, java.lang.foreign.ValueLayout.JAVA_LONG)"))
  }

  test("every native method leaves a §1(a) row naming the handle that replaced it") {
    // `Pipeline.runTraced`, not `run`: the latter drains each phase's buffer into a log it discards.
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(new PanamaFfiTransform()))._2
    val ds  = log.of(balticporter.tir.Decision.Kind.RetypedSignature)
    assertEquals(clue(ds).size, 3) // one per native, and nothing else in the file
    assert(ds.forall(_.reason == balticporter.tir.Reason.Universal("jni-to-panama")))
    assert(ds.exists(_.subjectFqn.endsWith("#add")))
    assert(ds.forall(_.detail("from").contains("native")))
    assert(ds.forall(_.detail("to").contains("handle")), clue(ds.map(_.detail("to"))))
  }

  test("a program with no native method records nothing") {
    val log = Pipeline.runTraced(
      SpoonTir.fromSource("package demo;\nclass Plain { int f() { return 1; } }\n"),
      List(new PanamaFfiTransform()))._2
    assertEquals(log.all, Nil)
  }

  test("replaces the native body with a handle invocation") {
    assert(out.matches("(?s).*def add\\(a: scala.Int, b: scala.Int\\): scala.Int = add\\$\\d+\\$handle.invokeExact\\(a, b\\).asInstanceOf\\[scala.Int\\].*"))
  }

  test("void native uses ofVoid and a Unit-discarding body") {
    assert(out.contains("FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.JAVA_INT)"))
    assert(out.matches("(?s).*def log\\(level: scala.Int\\): scala.Unit = \\{ log\\$\\d+\\$handle.invokeExact\\(level\\); \\(\\) \\}.*"))
  }
