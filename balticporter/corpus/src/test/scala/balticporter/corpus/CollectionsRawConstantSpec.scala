package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** Java's RAW static constants, and java's POSITIONAL `addAll` — two shapes that reached scalac as
  * something else and neither of which is a mapping gap.
  *
  * `Collections.EMPTY_LIST`/`EMPTY_SET`/`EMPTY_MAP` are declared RAW, which is why java's own javadoc
  * points readers at `emptyList()` instead: reading one at a parameterised slot is an UNCHECKED
  * CONVERSION (JLS 5.1.9), legal with a warning, and the libraries that use them write
  * `@SuppressWarnings("unchecked")` over the site. Scala has no unchecked conversion, so the external
  * field wrap produced `Buffer[java.util.Collections.EMPTY_LIST.E]` — an element type naming the raw
  * field's own variable. No unchecked-conversion machinery is needed to fix it: JAVA ALREADY HAS THE
  * TYPED FORM and documents these as it, so the FIELD rewrites to the same helper the CALL does and
  * the raw type is gone rather than worked around.
  *
  * `List.addAll(int, Collection)` is the other: it fell through every arm and scala accepted
  * `buf.addAll(0, c)` by AUTO-TUPLING against `Growable.addAll(IterableOnce)`, turning java's two
  * arguments into one pair.
  */
class CollectionsRawConstantSpec extends PortSuite:

  test("`Collections.EMPTY_LIST` becomes the TYPED factory java says it is") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Box {
        |  @SuppressWarnings("unchecked")
        |  List<String> none() { return Collections.EMPTY_LIST; }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.emptyList()")
    // …and the raw wrap is GONE, not merely joined: `fromJava` at a raw field is the weaker answer,
    // because it PRESERVES the type this one removes.
    assertNotEmits(p, "fromJava(java.util.Collections.EMPTY_LIST)")
  }

  test("…and so do `EMPTY_SET` and `EMPTY_MAP`") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Box {
        |  @SuppressWarnings("unchecked")
        |  Set<String> noneSet() { return Collections.EMPTY_SET; }
        |  @SuppressWarnings("unchecked")
        |  Map<String, String> noneMap() { return Collections.EMPTY_MAP; }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.emptySet()")
    assertEmits(p, "balticporter.runtime.JavaCollections.emptyMap()")
  }

  test("the FIELD and the CALL land on the same helper, which is what keeps java's IDENTITY") {
    // java's `EMPTY_LIST` IS the object `emptyList()` returns, and the runtime hands back one shared
    // instance for exactly that reason — so `xs == Collections.EMPTY_LIST`, which this engine emits
    // as `eq` (§4.4), goes on answering what java answers. Two different targets would not.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Box {
        |  @SuppressWarnings("unchecked")
        |  boolean isNone(List<String> xs) { return xs == Collections.EMPTY_LIST; }
        |  List<String> mk() { return Collections.emptyList(); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "eq balticporter.runtime.JavaCollections.emptyList()")
  }

  test("NEGATIVE — an ordinary external field is still WRAPPED, not rewritten") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Box {
        |  int count(java.util.jar.Attributes a) { return 0; }
        |}
        |""".stripMargin, new CollectionsTransform)
    // the table is three keys and closed by JAVA — `Collections` has no other field. Nothing else
    // may take this path, because a non-empty external collection has no factory to become.
    assertNotEmits(p, "JavaCollections.emptyList()")
  }

  test("java's POSITIONAL `addAll(int, c)` is an INSERT, not an append") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Box {
        |  void merge(List<String> dst, List<String> src) { dst.addAll(0, src); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.insertAll(dst, 0, src)")
    // the shape that used to reach scalac: `Growable.addAll` takes ONE `IterableOnce`, so scala
    // auto-tupled java's two arguments into a pair. It is a compile error at most element types and
    // a silently appended tuple at `Any`.
    assertNotEmits(p, "dst.addAll(0, src)")
  }

  test("…and the ONE-argument `addAll` is untouched by it") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Box {
        |  void merge(List<String> dst, List<String> src) { dst.addAll(src); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "dst ++= src")
    assertNotEmits(p, "insertAll")
  }

  test("the tables agree: every rewritten static FIELD is declared handled and has a helper") {
    // `jdk-surface` asks ONE question of an external member and a table split by node kind would
    // report a member the phase answers as the port's JDK wall.
    CollectionsTransform.StaticFieldFactories.foreach: (key, helper) =>
      assert(CollectionsTransform.handledStatics.contains(key), s"$key is rewritten and not declared")
      assert(CollectionsTransform.StaticHelpers.contains(helper), s"$helper is named and not minted")
    assert(CollectionsTransform.StaticHelpers.contains("insertAll"))
  }
