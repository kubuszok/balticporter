package balticporter.transform

import balticporter.core.PortMap
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Pipeline, Program, RuleScope }

/** A call whose receiver a SCOPED [[TypeRedirectTransform]] moved binds to the TARGET type's members: the base's published renames of the SOURCE member ([[PortMapTransform]]'s follow) do not reach
  * it, only the redirect's own `memberRenames` do. A call on a receiver the redirect left alone still follows the base.
  */
class TypeRedirectBaseRenameSpec extends munit.FunSuite:

  private val sources = List(
    "com/base/Bits.java" ->
      """package com.base;
        |public class Bits {
        |  public boolean get(int i) { return false; }
        |  public boolean isEmpty() { return true; }
        |}
        |""".stripMargin,
    "com/dep/Entity.java" ->
      """package com.dep;
        |import com.base.Bits;
        |public class Entity {
        |  Bits bits = new Bits();
        |  boolean none() { return bits.isEmpty(); }
        |  boolean has(int i) { return bits.get(i); }
        |  boolean local() { Bits b = new Bits(); return b.isEmpty(); }
        |}
        |""".stripMargin,
    "com/other/Plain.java" ->
      """package com.other;
        |import com.base.Bits;
        |public class Plain {
        |  boolean none(Bits b) { return b.isEmpty(); }
        |}
        |""".stripMargin
  )

  /** the base published `isEmpty()` as the parenless `empty` — a bean-shaped rename. */
  private val baseMap = PortMap.Map0(
    "base",
    engine = "test",
    entries = List(
      PortMap.Entry("member", "com.base.Bits#isEmpty()", "com.base.Bits#empty", PortMap.Disposition.Renamed, shape = "form=parenless")
    )
  )

  private def redirect(scope: RuleScope) = new TypeRedirectTransform(
    redirects = Map("com.base.Bits" -> "scala.collection.mutable.BitSet"),
    memberRenames = Map("com.base.Bits" -> Map("get" -> "contains")),
    scopes = if scope.isUnrestricted then Map.empty else Map("com.base.Bits" -> scope)
  )

  private def run(scope: RuleScope): Program =
    Pipeline.run(SpoonTir.fromSources(sources), List(new PortMapTransform(List(baseMap)), redirect(scope)))

  private def body(out: String, cls: String): String =
    val at = out.indexOf(s"class $cls")
    assert(at >= 0, out)
    out.substring(at, out.indexOf("\n}", at))

  test("inside the redirect's scope the call keeps the TARGET's spelling; outside it follows the base's rename") {
    val out    = new TirEmitter(run(RuleScope.Only(Set("com.dep"))), externalParenless = Set("scala.collection.mutable.BitSet#isEmpty")).emit
    val entity = body(out, "Entity")
    assert(clue(entity).contains("bits.isEmpty\n"))
    assert(clue(entity).contains("b.isEmpty\n"))
    assert(!entity.contains(".empty"), entity)
    // the redirect's own rename still applies at the moved receiver
    assert(clue(entity).contains("bits.contains(i)"))
    // a receiver the scope left on the base type follows the base's published spelling
    assert(clue(body(out, "Plain")).contains("b.empty"))
  }

  test("the target member's ARITY is the port's to declare — without it java's `()` stays") {
    // the twin names the target's member, so `externalParenless` (keyed `Owner#member` on the
    // TARGET) is what decides; nothing about the source member's published shape carries over
    val entity = body(new TirEmitter(run(RuleScope.Only(Set("com.dep")))).emit, "Entity")
    assert(clue(entity).contains("bits.isEmpty()"))
  }

  test("an UNSCOPED redirect keeps the source member's symbol — the source type survives nowhere") {
    val out = new TirEmitter(run(RuleScope.everywhere)).emit
    assert(clue(body(out, "Plain")).contains("b.empty"))
    assert(clue(body(out, "Entity")).contains("bits.empty"))
  }
