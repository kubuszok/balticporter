package balticporter.corpus

import balticporter.testkit.{PortFixture, PortSuite}
import balticporter.tir.*
import balticporter.transform.NullabilityTransform

/** TWO OVERLOADS THAT DIFFER ONLY IN A RETYPED PARAMETER MUST STAY TWO MEMBERS.
  *
  * The hazard is plausible enough to pin rather than argue: `Descriptor.Param.Unresolved` renders
  * `?`, `Descriptor.ofInfo` cannot spell a `TypeRepr.OrType`, and `NullabilityTransform` turns
  * `m(String)` into `m(String | Null)`. If a member key were derived from the RETYPED `info` and an
  * unspellable slot degraded to `?`, then `m(@Null String)` and `m(@Null Integer)` would both key as
  * `demo.Holder#m(?)` — one key naming two members, which is the exact failure `MemberKey` exists to
  * prevent, and which would silently mis-address every policy key, every source-map member row and
  * every published contract row in the family.
  *
  * It does not happen, and BOTH reasons are pinned here rather than one, because either alone would
  * be a coincidence a later change could withdraw:
  *
  *   - `Symbol.descriptor` is recorded by the FRONTEND from the JAVA signature and no phase rewrites
  *     it, so the key of a retyped member is the key its Java always had;
  *   - and the engine's own fallback refuses rather than guessing — `Descriptor.total` is ALL of the
  *     parameters or none, so an unspellable slot yields NO descriptor rather than a `?` that
  *     collides. "A descriptor with a single guessed parameter is a key that matches the WRONG
  *     overload, which is strictly worse than no key at all."
  */
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
