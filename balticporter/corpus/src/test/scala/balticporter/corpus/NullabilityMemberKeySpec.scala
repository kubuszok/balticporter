package balticporter.corpus

import balticporter.testkit.{PortFixture, PortSuite}
import balticporter.tir.*
import balticporter.transform.NullabilityTransform

/** TWO OVERLOADS THAT DIFFER ONLY IN A RETYPED PARAMETER MUST STAY TWO MEMBERS. */
class NullabilityMemberKeySpec extends PortSuite:

  private val java =
    """package demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS)
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |class Holder {
      |  void m(@Null String s) {}
      |  void m(@Null Integer i) {}
      |  void plain(String s) {}
      |}
      |""".stripMargin

  private def retyped: Program =
    Pipeline.runTraced(PortFixture.parse(java), List(new NullabilityTransform(Set("demo.Null"))))._1

  private def overloads(p: Program): List[Symbol] =
    p.symbols.all.toList.filter(_.fullName == "demo.Holder#m").sortBy(_.id.raw)

  test("the two overloads ARE retyped — otherwise this spec proves nothing") {
    val ms = overloads(retyped)
    assertEquals(ms.size, 2)
    assert(ms.forall(_.info match
      case TypeRepr.MethodType(List((_, TypeRepr.OrType(_, _))), _, _) => true
      case _                                                          => false), clue(ms.map(_.info)))
  }

  test("…and they keep DISTINCT member keys — the descriptor is the java signature, not the info") {
    val keys = overloads(retyped).map(s => MemberKey(s.fullName.takeWhile(_ != '#'), "m", s.descriptor).render)
    assertEquals(keys.distinct.size, 2, clue(keys))
    assert(keys.forall(k => !k.contains("?")), clue(keys))
    // …and they are the keys the UNRETYPED program has, unchanged
    val before = overloads(PortFixture.parse(java)).map(_.descriptor)
    assertEquals(overloads(retyped).map(_.descriptor), before)
  }

  test("the ENGINE's fallback REFUSES a retyped parameter rather than spelling it `?`") {
    val p  = retyped
    val ms = overloads(p)
    // `Descriptor.total` is all-or-none, so an `OrType` slot yields NO descriptor — never a `?` that
    // two overloads would share. The unannotated method is unaffected and still derives cleanly.
    assert(ms.forall(m => Descriptor.ofInfo(p, m.info).isEmpty), clue(ms.map(m => Descriptor.ofInfo(p, m.info))))
    val plain = p.symbols.all.find(_.fullName == "demo.Holder#plain").getOrElse(fail("no `plain`"))
    assertEquals(Descriptor.ofInfo(p, plain.info).map(_.render), Some("String"))
  }
