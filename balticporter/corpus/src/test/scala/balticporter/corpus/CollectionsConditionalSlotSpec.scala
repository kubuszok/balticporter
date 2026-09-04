package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** A CONDITIONAL's conversion belongs to its BRANCHES — JLS 15.25, met at a retyping's own seam. */
class CollectionsConditionalSlotSpec extends PortSuite:

  test("each BRANCH is bridged at the slot, not the conditional's lub") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Attrs {
        |  private Map<String, String> attributes;
        |  @SuppressWarnings("unchecked")
        |  Collection<String> values() {
        |    return attributes != null ? attributes.values() : Collections.EMPTY_LIST;
        |  }
        |}
        |""".stripMargin, new CollectionsTransform)
    // the `else` arm is a `Buffer` at a `JavaCollection` result — java's own `List <: Collection`,
    // an edge the mapping has no image for — and nothing reached it while the walk stopped at the
    // `if`.
    assertEmits(p, "balticporter.runtime.JavaCollection.from(balticporter.runtime.JavaCollections.emptyList())")
  }

  test("…and a NESTED conditional resolves one level down") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Pick {
        |  @SuppressWarnings("unchecked")
        |  Collection<String> go(int n, List<String> a, List<String> b) {
        |    return n > 0 ? a : n < 0 ? b : Collections.EMPTY_LIST;
        |  }
        |}
        |""".stripMargin, new CollectionsTransform)
    // the recursion goes back through `coerce`, so an inner conditional is an arm like any other and
    // every guard still answers.
    assertEmits(p, "balticporter.runtime.JavaCollection.from(a)")
    assertEmits(p, "balticporter.runtime.JavaCollection.from(b)")
  }

  test("NEGATIVE — a conditional this phase has no opinion about is left IDENTICAL") {
    val p = port(
      """package demo;
        |class Plain {
        |  String pick(int n, String a, String b) { return n > 0 ? a : b; }
        |}
        |""".stripMargin, new CollectionsTransform)
    // the descent is identity-preserving where neither arm moves, so no member digest shifts for a
    // conditional that was already right — the over-approximation §5 has no instrument for.
    assertEmits(p, "return if (n > 0) a else b")
  }
