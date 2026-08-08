package balticporter.runner

import balticporter.core.PortMap
import balticporter.tir.{IdiomCandidate, IdiomKind, IdiomLog, IdiomVerdict, Origin, Surface}
import balticporter.transform.BeanPropertyTransform.Target

/** THE COLLAPSE VERDICT IS WHOLE-PROGRAM-DEPENDENT, AND A DEPENDENT RE-DERIVES IT.
  *
  * Every other §1(b) policy is a TABLE and a dependent inherits the base's instance, so two modules
  * agree by construction. `BeanCollapse`'s verdict is DERIVED — `overriddenBelow` over the run's
  * descendants, `concreteRelative` over its override closure, `writtenSymbols` over its assignments,
  * `closureOf(_).isAnchored` over its parents — and a dependent's model CONTAINS its base's units
  * plus its own. One subclass overriding the accessor, or one write of the field, and the dependent
  * answers `Refuse` about a base declaration the base COLLAPSED.
  *
  * NOTHING ELSE CAN SEE IT: the manifest entry is identical on both sides, so `surfaceFingerprint`
  * is EQUAL and `SurfaceDivergence` has nothing to compare; the phase agrees with itself, so
  * `idiom(refused)` reports an honest refusal with a real guard; every count is flat; and the two
  * ports each compile alone and cannot compile together — §1.5's failure arriving through a
  * derivation rather than through a table.
  *
  * Asserted on the PURE FUNCTION, which is `PortRun.baseSurfaceFindings`' own stated pattern: the
  * negative cases ("the two agree", "the base said nothing", "this pair is mine") are exactly the
  * ones a two-module port on disk makes expensive and a value makes cheap.
  */
class CollapseDivergenceSpec extends munit.FunSuite:

  private def base(module: String, types: List[String],
                   members: List[(String, String)]): (String, PortMap.Map0) =
    val rows =
      types.map(t => PortMap.Entry("type", t, t, PortMap.Disposition.Ported)) ++
        members.map((k, form) => PortMap.Entry("member", k, k, PortMap.Disposition.Ported,
          shape = Surface.render(Surface.MemberShape(form = form))))
    module -> PortMap.Map0(module, "eng", rows)

  private def log(rows: (String, IdiomVerdict)*): IdiomLog =
    val l = new IdiomLog
    rows.foreach((k, v) => l.record(
      IdiomCandidate(IdiomKind.BeanCollapse, v, k, s"property via `getW/setW`", Origin.synthetic)))
    l

  private val pairs   = Map("p.Base#w" -> "getW/setW")
  private val asVar   = (_: String) => Target.Var
  private val collapsed = base("base-mod", List("p.Base"), List("p.Base#w" -> "var"))

  test("a base that COLLAPSED and a dependent that REFUSES is FATAL, and the gap names BOTH answers") {
    val gaps = PortRun.collapseDivergence(
      log("p.Base#w" -> IdiomVerdict.Refused("OverriddenBelow", "a subclass overrides it")),
      List(collapsed), pairs, asVar)
    assertEquals(clue(gaps).size, 1)
    assert(gaps.head.fatal, "this run has already emitted the losing shape")
    assertEquals(gaps.head.subject, "p.Base#w")
    assert(clue(gaps.head.why).contains("a collapsed `var`"), "what the BASE published")
    assert(gaps.head.why.contains("NO collapse"), "…and what THIS run derived")
    assert(gaps.head.why.contains("no count moves"), "…and why nothing else reports it")
    assertEquals(gaps.head.module, Some("base-mod"))
    assert(clue(gaps.head.fix).contains("§1(b)"), "§4.45: a finding an agent cannot classify is a\n" +
      "      full investigation")
  }

  test("…and the other direction too — a dependent that COLLAPSES what the base did not") {
    val plain = base("base-mod", List("p.Base"), List("p.Base#w" -> ""))
    val gaps  = PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                           List(plain), pairs, asVar)
    assertEquals(clue(gaps).size, 1)
    assert(gaps.head.fatal)
    assert(clue(gaps.head.why).contains("it published NO collapse"))
  }

  test("AGREEMENT is silent — in both shapes") {
    assertEquals(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), pairs, asVar), Nil)
    val plain = base("base-mod", List("p.Base"), List("p.Base#w" -> ""))
    assertEquals(PortRun.collapseDivergence(
      log("p.Base#w" -> IdiomVerdict.Refused("NotRequested", "the port did not ask")),
      List(plain), pairs, asVar), Nil)
  }

  test("a pair over a type the base does NOT emit is not asked about — §1.5's rule read here") {
    // "Ask that question of what the base EMITS, never of its `governs` CLAIM." A dependent's own
    // declarations routinely live inside the base's namespace, and a rule that screened by the claim
    // would report every pair such a module writes about its OWN members (`ENGINE-LIMITS.md` D10).
    val elsewhere = base("base-mod", List("p.Other"), List("p.Other#x" -> "var"))
    assertEquals(PortRun.collapseDivergence(
      log("p.Base#w" -> IdiomVerdict.Refused("OverriddenBelow", "mine")),
      List(elsewhere), pairs, asVar), Nil)
  }

  test("a base that emits the TYPE and publishes no member row is UNKNOWN, never 'not collapsed'") {
    // Assuming the base did not collapse it would be §4.6's fabricated fact: a default the caller
    // cannot tell from a real answer. Non-fatal, and only where this run DID collapse — a run that
    // refused agrees with every reading of an absent row.
    val silent = base("base-mod", List("p.Base"), Nil)
    val gaps   = PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(silent), pairs, asVar)
    assertEquals(clue(gaps).size, 1)
    assert(!gaps.head.fatal, "an unanswered question is a finding, not a refusal")
    assert(clue(gaps.head.why).contains("fabricated fact"))
    assertEquals(PortRun.collapseDivergence(
      log("p.Base#w" -> IdiomVerdict.Refused("NotRequested", "mine")), List(silent), pairs, asVar), Nil)
  }

  test("a BASE port — no bases, or no pairs — asks nothing at all") {
    assertEquals(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            Nil, pairs, asVar), Nil)
    assertEquals(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), Map.empty, asVar), Nil)
  }

  test("the VERDICT comes from the phase's own log, never from a second derivation (§4.6)") {
    // K2.5's measured shape: a residue count that re-derived its own question could not tell a
    // refusal from a switched-off fix. Here the same rule keeps a fatal finding honest — every
    // answer this function gives about THIS run is a row the phase filed.
    val gaps = PortRun.collapseDivergence(
      log("p.Base#w" -> IdiomVerdict.Refused("AnchoredClosure", "an unparsed parent")),
      List(collapsed), pairs, asVar)
    assertEquals(clue(gaps).size, 1, "the guard's NAME does not matter — only that it refused")
  }

  test("…and the SHAPE comes from the port's own `targetOf`, so `val` and `var` are not one answer") {
    val asVal = (_: String) => Target.Val
    assertEquals(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), pairs, asVal).size, 1,
      "the base published `var` and this run derived `val` — two different emitted surfaces")
    assert(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                      List(collapsed), pairs, asVal).head.why.contains("a collapsed `val`"))
  }

  test("`MemberShape.form` ROUND-TRIPS, and is absent from every row that is not a collapse") {
    assertEquals(Surface.parseMember(Surface.render(Surface.MemberShape(form = "var"))).form, "var")
    assertEquals(Surface.render(Surface.MemberShape()), "", "sparse by design")
    // …and a map from an engine that never carried the key answers "", which reaches no comparison:
    // `PortMap.freshness` calls such a map Stale and the base is refused wholesale.
    assertEquals(Surface.parseMember("vis=private").form, "")
  }
