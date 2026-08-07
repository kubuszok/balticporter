package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.*
import balticporter.tir.RewriteCallSitesCheck.Issue
import balticporter.transform.{CollectionBoundaryCheck, CollectionClosureCheck, CollectionsTransform, RetargetBoundaryCheck}

/** THE STANDING QUESTION every retyping phase owes — `Rewrite`, `RewriteCallSitesCheck`, and the
  * `ENGINE-LIMITS.md` K5.6 sentence they close.
  *
  * Every test here is in BOTH directions, because the whole value of the check is that it can tell
  * an accounted phase from an unaccounted one, and a check that reported the same thing about both
  * would be the silence it exists to replace.
  *
  * The two tests that carry the design are the last two: the retyped set is DERIVED and cannot be
  * under-reported by the phase, and the site count CANNOT be taken before the phase runs — which is
  * the measured reason the proposal's `find`/`fix` half is not retrofitted onto these phases and
  * lives in `ENGINE-LIMITS.md` K5.10 rather than in a comment.
  */
class RewriteCallSitesSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |class Holder {
      |  List<String> names = new ArrayList<>();
      |  void add(String s) { names.add(s); }
      |  int size() { return names.size(); }
      |}
      |""".stripMargin

  /** a phase that runs and moves NOTHING — the honest baseline, and the one that proves the check's
    * population is derived from what a phase DID rather than from a list of phase names. */
  private class Silent extends Phase:
    def name: String = "silent"

  /** the rewrite itself: every occurrence of `java.lang.String` is re-pointed at a type the fixture
    * declares. What it retypes TO is irrelevant — nothing is emitted here — and re-pointing at a
    * symbol the fixture certainly has keeps the test from depending on how the frontend happens to
    * intern a primitive. */
  private def repoint(t: TypeRepr)(using p: Program): TypeRepr = t match
    case TypeRepr.TypeRef(pre, s) if p.symbolOf(s).exists(_.fullName == "java.lang.String") =>
      p.symbols.all.find(_.fullName == "demo.Holder").map(x => TypeRepr.TypeRef(pre, x.id)).getOrElse(t)
    case _ => t

  /** …declared, with the lanes it claims count its residue. */
  private class Mover(account: Set[String]) extends Phase, Rewrite:
    def name: String = "mover"
    def accountedBy: Set[String] = account
    override def transformType(t: TypeRepr)(using Program): TypeRepr = repoint(t)

  /** …and the same rewrite with no account at all: the shape `ENGINE-LIMITS.md` K5.6 says can be
    * reintroduced at any time, and which nothing in the engine could see. */
  private class Unaccounted extends Phase:
    def name: String = "unaccounted-mover"
    override def transformType(t: TypeRepr)(using Program): TypeRepr = repoint(t)

  // -------------------------------------------------------------------------------------------
  // the observation: WHAT MOVED is derived from the pipeline, never declared by the phase
  // -------------------------------------------------------------------------------------------

  test("a phase that moves nothing produces NO row at all — the check is about retyping phases, and the list of those is derived") {
    val p = port(src, new Silent)
    assertEquals(p.rewrites.all.map(_.phase), Nil)
    assertEquals(RewriteCallSitesCheck.check(p.rewrites, Some(Set.empty)), Nil)
  }

  test("a phase that MOVES a declaration is recorded whether it wanted to be or not") {
    val p = port(src, new Unaccounted)
    assertEquals(p.rewrites.all.map(_.phase), List("unaccounted-mover"))
    assert(p.rewrites.all.head.retyped.nonEmpty, "the pipeline must observe the declarations the phase moved")
  }

  test("…and only OWNED declarations: an external's signature is a fact about a class file (§4.56)") {
    val p = port(src, new Unaccounted)
    val moved = p.rewrites.all.head.retyped
    assert(moved.nonEmpty)
    assert(moved.forall(p.after.owns), "a retyped set that contained an external would make the count mean something else")
  }

  // -------------------------------------------------------------------------------------------
  // the two findings, each in both directions
  // -------------------------------------------------------------------------------------------

  test("Unaccounted — a phase that retypes and names no lane is a finding") {
    val p  = port(src, new Unaccounted)
    val fs = RewriteCallSitesCheck.check(p.rewrites, Some(Set.empty))
    assertEquals(fs.map(_.issue), List(Issue.Unaccounted))
    assertEquals(fs.head.phase, "unaccounted-mover")
  }

  test("…and the SAME rewrite naming a lane that recorded is NOT a finding — the check is about the account, not about retyping") {
    val p  = port(src, new Mover(Set("some-lane")))
    assert(p.rewrites.all.nonEmpty)
    assertEquals(RewriteCallSitesCheck.check(p.rewrites, Some(Set("some-lane"))), Nil)
  }

  test("UnwiredAccounting — a lane a phase NAMES that recorded nothing in this run is a finding") {
    val p  = port(src, new Mover(Set("some-lane")))
    val fs = RewriteCallSitesCheck.check(p.rewrites, Some(Set("a-different-lane")))
    assertEquals(fs.map(_.issue), List(Issue.UnwiredAccounting))
    assert(fs.head.detail.contains("some-lane"))
  }

  test("…one row per NAMED lane, so a phase claiming three cannot hide two behind one that recorded") {
    val p  = port(src, new Mover(Set("a", "b", "c")))
    val fs = RewriteCallSitesCheck.check(p.rewrites, Some(Set("b")))
    assertEquals(fs.map(_.issue), List(Issue.UnwiredAccounting, Issue.UnwiredAccounting))
    assertEquals(fs.map(_.detail.contains("'a'")).count(identity), 1)
  }

  test("…and with the artifact layer OFF the wiring question is NOT ASKED — an empty snapshot is not an answer") {
    val p = port(src, new Mover(Set("some-lane")))
    // `None` is "nothing recorded because nothing COULD record", which is what `CheckReport` reports
    // when the layer is off (§5.1). Read as the empty SET it would say every accounted phase in the
    // engine is unwired — a finding manufactured by a diagnostic switch.
    assertEquals(RewriteCallSitesCheck.check(p.rewrites, scala.None), Nil)
    assertEquals(RewriteCallSitesCheck.check(p.rewrites, Some(Set.empty)).map(_.issue),
                 List(Issue.UnwiredAccounting))
  }

  test("…while the UNACCOUNTED lane is asked either way: it reads the pipeline, which no artifact switch touches") {
    val p = port(src, new Unaccounted)
    assertEquals(RewriteCallSitesCheck.check(p.rewrites, scala.None).map(_.issue), List(Issue.Unaccounted))
  }

  test("every issue carries a §1 classification — a finding an agent cannot classify costs it a full investigation (§4.45)") {
    Issue.values.foreach { i =>
      val c = Issue.classification(i)
      assert(c.contains("§1("), s"$i does not say which of §1's three kinds the fix is")
      assert(c.length > 120, s"$i's classification is too short to act on")
    }
  }

  // -------------------------------------------------------------------------------------------
  // the engine's own retyping phases
  // -------------------------------------------------------------------------------------------

  test("CollectionsTransform names all THREE of its lanes — three residues, three different fixes") {
    assertEquals(new CollectionsTransform().accountedBy,
                 Set(CollectionClosureCheck.Name, CollectionBoundaryCheck.Name, RetargetBoundaryCheck.Name))
  }

  test("…and it really does move declarations, so its account is not vacuous") {
    val p = port(src, new CollectionsTransform)
    val patch = p.rewrites.all.find(_.phase == "java-collections->scala")
    assert(patch.exists(_.retyped.nonEmpty), "the collection retype must be observable as a declaration move")
    assertEquals(RewriteCallSitesCheck.check(p.rewrites,
      Some(Set(CollectionClosureCheck.Name, CollectionBoundaryCheck.Name, RetargetBoundaryCheck.Name))), Nil)
  }

  // -------------------------------------------------------------------------------------------
  // WHY `find`/`fix` IS NOT RETROFITTED — measured here rather than asserted in a comment
  // -------------------------------------------------------------------------------------------

  test("a retyping phase's site count CANNOT be taken before it runs — its own `transformType` moves nothing until `run` has resolved its tables (K5.10)") {
    val before = balticporter.frontend.spoon.SpoonTir.fromSource(src)
    val ph     = new CollectionsTransform
    given Program = before
    // exactly what a dry-run site count would compute: this phase's own `transformType` over every
    // owned declaration's `info`, before the phase has run.
    val predicted = before.owned.filter(id =>
      before.symbolOf(id).exists(s => StandardTraversal.mapType(ph, s.info) != s.info))
    assertEquals(predicted.size, 0,
      "if this is ever non-zero the phase became predictable and K5.10's refusal should be re-measured")
    // …and what it actually moved, once it ran.
    val p = port(src, ph)
    assert(p.rewrites.all.exists(_.retyped.nonEmpty),
      "the same phase moves declarations when it runs — which is the whole gap the number above measures")
  }
