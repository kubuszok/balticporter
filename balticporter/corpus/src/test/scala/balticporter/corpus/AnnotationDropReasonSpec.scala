package balticporter.corpus

import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite

/** WHY an annotation was dropped, which `droppedAnnotations` could not say.
  *
  * `SpoonTir.annotationsOf` writes one list, read by `OmissionCheck` as `omissions` rows and by
  * `TestFrameworkTransform` as an exact FQN match. Three different things used to write the same
  * value into it, and one of them wrote NOTHING at all:
  *
  *   - the port DECLINED it (no expression translator in scope, or the port's `AnnotationPolicy`
  *     does not claim the family) — policy, and the FQN is the whole answer;
  *   - the argument list would not TRANSLATE — an engine defect in the constant-expression path,
  *     reported with the same one string, so a reader could not tell it from the line above;
  *   - the whole SET would not read — `catch { case _ => Nil }`, which is not a drop at all: the
  *     declaration then looks as though it carries no annotations, `omissions` reports nothing, and
  *     a `@Test` or a `@JsonProperty` is simply gone (`CLAUDE.md` §4.6's fabricated fact, at the
  *     one value where the default is indistinguishable from a real answer).
  *
  * The sentinels are what makes the second and third visible. They are SENTINELS and not decorated
  * names on purpose: `Symbol.droppedAnnotations` is documented as annotations BY NAME and every
  * consumer matches an FQN exactly — `TestFrameworkTransform` still has to recognise a `@Test`
  * whose arguments would not translate, or the conversion loses a test.
  */
class AnnotationDropReasonSpec extends PortSuite:

  private val src =
    """package demo;
      |class Holder {
      |  @Deprecated int marker;
      |  @SuppressWarnings("unchecked") int withArgs;
      |}
      |""".stripMargin

  private def droppedOn(name: String): List[String] =
    val p = port(src)
    p.after.symbols.all.filter(_.name == name).flatMap(_.droppedAnnotations).toList

  test("a MARKER annotation is carried, not dropped — the path an unreadable value list must not take") {
    assertEquals(droppedOn("marker"), Nil)
    val p = port(src)
    assert(p.after.symbols.all.exists(s => s.name == "marker" && s.annotations.nonEmpty))
  }

  test("an annotation DECLINED for policy is reported by its exact FQN and by nothing else") {
    // a FIELD has no expression translator in scope, which is the `case (None, _)` arm: the drop is
    // a decision, and the FQN is the complete answer. No sentinel rides with it.
    assertEquals(droppedOn("withArgs"), List("java.lang.SuppressWarnings"))
  }

  test("the three sentinels are distinct, and none of them can collide with an FQN") {
    val all = List(SpoonTir.UnresolvedAnnotation, SpoonTir.UnreadableAnnotations,
                   SpoonTir.FailedAnnotationArguments)
    assertEquals(all.distinct.size, 3)
    all.foreach { s =>
      assert(s.startsWith("<") && s.endsWith(">"), s"$s must not be mistakable for an annotation name")
      assert(!s.contains("."), s"$s must not be mistakable for an FQN")
    }
  }
