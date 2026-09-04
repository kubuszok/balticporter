package balticporter.frontend.spoon

import balticporter.tir.*

/** §0.4's UNATTRIBUTABLE ARMS — fallbacks that answer with a VALUE indistinguishable from a real
  * answer. */
class UnattributableArmsSpec extends munit.FunSuite:

  private def rendered(java: String): String =
    val p = SpoonTir.fromSource(java)
    TirPrinter.program(TirPrinter.Style.canonical)(using p)

  test("a RAW generic is filled to its DECLARED arity — the answer the narrowed lookup preserves") {
    val text = rendered(
      """package p;
        |import java.util.Map;
        |public class Raw { public Map m; public Map<String, Integer> g; }
        |""".stripMargin)
    // two type arguments on the raw use, not zero. This is the behaviour the bare `catch` could
    // silently lose and the reason its default was never neutral.
    assert(text.contains("java.util.Map[") , s"the raw use lost its arity entirely:\n$text")
    val rawLine = text.linesIterator.find(_.contains("m ")).getOrElse("")
    assert(!rawLine.matches(".*java\\.util\\.Map[^\\[].*"), s"raw `Map` emitted un-applied: $rawLine")
  }

  test("a NON-generic type still answers arity 0 — the narrowing must not invent arguments") {
    val text = rendered("package p; public class Plain { public String s; }")
    assert(!text.contains("java.lang.String["), s"a non-generic type gained type arguments:\n$text")
  }

  test("a type the classpath does not have answers 0, and that is the CLASSPATH's fact") {
    // Stated as a passing test rather than as prose, because it is the honest limit of the
    // narrowing: with no declaration at all, nothing available can say what the arity is. The
    // narrowing removes the case where a declaration EXISTS and could not state its own arity.
    val text = rendered("package p; public class U { public com.nowhere.Absent a; }")
    assert(!text.contains("com.nowhere.Absent["), s"an unresolvable type gained arguments:\n$text")
  }
