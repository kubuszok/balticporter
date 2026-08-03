package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, Pipeline, SymbolTable, TypeRepr}
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
    assert(clue(out).contains("private val add$handle: java.lang.invoke.MethodHandle"))
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
    assert(clue(out).contains(
      "def add(a: scala.Int, b: scala.Int): scala.Int = add$handle.invokeExact(a, b).asInstanceOf[scala.Int]"))
  }

  test("void native uses ofVoid and a Unit-discarding body") {
    assert(out.contains("FunctionDescriptor.ofVoid(java.lang.foreign.ValueLayout.JAVA_INT)"))
    assert(clue(out).contains("def log(level: scala.Int): scala.Unit = { log$handle.invokeExact(level); () }"))
  }

  // -- ENGINE-LIMITS M10: the handle NAME is keyed on the method, never on the mint counter -------

  private val overloaded =
    """package demo;
      |class Over {
      |  public static native void copyJni(float[] src, int n);
      |  public static native void copyJni(int[] src, int n);
      |  public static native void copyJni(short[] src, int n);
      |  public static native long only(long x);
      |}
      |""".stripMargin

  private def emit(src: String): String =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(new PanamaFfiTransform()))).emit

  test("M10 — a native with NO same-named sibling needs no disambiguator at all") {
    assert(clue(emit(overloaded)).contains("private val only$handle:"))
  }

  test("M10 — OVERLOADED natives get distinct handles, ordered by the erased signature") {
    val o = emit(overloaded)
    List("copyJni$0$handle", "copyJni$1$handle", "copyJni$2$handle").foreach(n =>
      assert(clue(o).contains(s"private val $n:"), s"missing $n"))
    // and each body reads ITS OWN handle — the field and the call are one derivation, not two.
    assert(o.contains("copyJni$0$handle.invokeExact"))
    assert(o.contains("copyJni$2$handle.invokeExact"))
  }

  test("M10 — an UNRELATED declaration ahead of the natives does not move a single handle name") {
    // THE MEASUREMENT THIS ENTRY IS ABOUT, as a fixture. Every symbol the frontend interns shifts
    // every later `SymId`, so under the old name the whole class's emitted text moved whenever a
    // file above it gained one — 122 member digests in four types on the JS-E05 wave, in types that
    // change never touched. Nothing else in this repository can see that: the port still compiled,
    // every check count was flat, and the only instrument that COULD see it is the one the name
    // defeated.
    val shifted = overloaded.replace("class Over {", "class Over {\n  static int unrelated(int q) { return q + 1; }")
    val a = emit(overloaded)
    val b = emit(shifted)
    val handles = (s: String) => "\\w+\\$(?:\\d+\\$)?handle".r.findAllIn(s).toList.distinct.sorted
    assertEquals(clue(handles(b)), clue(handles(a)))
  }

  test("M10 — the key is counter-free ALL THE WAY DOWN: an unreadable signature falls back to POSITION") {
    // The degenerate cell behind the fix, EXERCISED — and this test is deliberately not claimed as
    // failing-first, because the cell cannot be reached from java at all. The `MethodType` case is
    // guaranteed by `run`'s own filter, so no source produces the empty key; what made it worth
    // removing is that `sortBy` is STABLE, so a group whose keys all degenerated would fall back to
    // the order `natives` iterates in — the MINT COUNTER, which is the one thing M10 says no
    // emitted identifier may be keyed on. Nothing would have reported it either: the names stay
    // distinct and well-formed and only their ASSIGNMENT to declarations moves.
    //
    // So the signatures are stripped by hand to make the fallback the only key, and the
    // perturbation is the one the test above uses, kept on ONE line so the declarations' own
    // positions do not move. What this pins is that the fallback is TOTAL and positional: it runs,
    // it names every native, and the mapping from a declaration's position to its handle is the
    // same on both sides.
    def byPosition(src: String): Map[(String, Int), String] =
      val p0      = SpoonTir.fromSource(src)
      val natives = p0.symbols.all.filter(_.flags.isNative).map(_.id).toSet
      val blind   = p0.rebuilt(symbols = SymbolTable(p0.symbols.all.map(s =>
        if natives(s.id) then s.copy(info = TypeRepr.NoType) else s)))
      new PanamaFfiTransform().handleNames(blind, natives).map { (m, n) =>
        val o = Decision.originOf(blind, m)
        (o.javaPath.split('/').last, o.line) -> n
      }
    val sameLine = overloaded.replace("class Over {", "class Over { static int unrelated(int q) { return q + 1; }")
    assertEquals(clue(byPosition(sameLine)), clue(byPosition(overloaded)))
  }
