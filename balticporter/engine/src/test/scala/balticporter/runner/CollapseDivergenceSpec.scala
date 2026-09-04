package balticporter.runner

import balticporter.core.PortMap
import balticporter.tir.{IdiomCandidate, IdiomKind, IdiomLog, IdiomVerdict, Origin, Surface}
import balticporter.transform.BeanPropertyTransform.Target

/** THE COLLAPSE VERDICT IS WHOLE-PROGRAM-DEPENDENT, AND A DEPENDENT RE-DERIVES IT. */
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

  /** the GAPS half. Every assertion below is about what disagreed; the DENOMINATOR — how many
    * verdicts were compared at all — has its own cell at the bottom, because `0 gaps` because
    * everything agreed and `0 gaps` because nothing was compared are the same line otherwise. */
  private def gapsOf(idioms: IdiomLog, bases: List[(String, PortMap.Map0)],
                     pairs: Map[String, String], t: String => Target): List[Surface.Gap] =
    PortRun.collapseDivergence(idioms, bases, pairs, t).gaps

  private val pairs   = Map("p.Base#w" -> "getW/setW")
  private val asVar   = (_: String) => Target.Var
  private val collapsed = base("base-mod", List("p.Base"), List("p.Base#getW" -> "var"))

  test("a base that COLLAPSED and a dependent that REFUSES is FATAL, and the gap names BOTH answers") {
    val gaps = gapsOf(
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
    val plain = base("base-mod", List("p.Base"), List("p.Base#getW" -> ""))
    val gaps  = gapsOf(log("p.Base#w" -> IdiomVerdict.Converted),
                                           List(plain), pairs, asVar)
    assertEquals(clue(gaps).size, 1)
    assert(gaps.head.fatal)
    assert(clue(gaps.head.why).contains("it published NO collapse"))
  }

  test("AGREEMENT is silent — in both shapes") {
    assertEquals(gapsOf(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), pairs, asVar), Nil)
    val plain = base("base-mod", List("p.Base"), List("p.Base#getW" -> ""))
    assertEquals(gapsOf(
      log("p.Base#w" -> IdiomVerdict.Refused("NotRequested", "the port did not ask")),
      List(plain), pairs, asVar), Nil)
  }

  test("a pair over a type the base does NOT emit is not asked about — §1.5's rule read here") {
    // "Ask that question of what the base EMITS, never of its `governs` CLAIM." A dependent's own
    // declarations routinely live inside the base's namespace, and a rule that screened by the claim
    // would report every pair such a module writes about its OWN members (`ENGINE-LIMITS.md` D10).
    val elsewhere = base("base-mod", List("p.Other"), List("p.Other#x" -> "var"))
    assertEquals(gapsOf(
      log("p.Base#w" -> IdiomVerdict.Refused("OverriddenBelow", "mine")),
      List(elsewhere), pairs, asVar), Nil)
  }

  test("a base that emits the TYPE and publishes no member row is UNKNOWN, never 'not collapsed'") {
    // Assuming the base did not collapse it would be §4.6's fabricated fact: a default the caller
    // cannot tell from a real answer. Non-fatal, and only where this run DID collapse — a run that
    // refused agrees with every reading of an absent row.
    val silent = base("base-mod", List("p.Base"), Nil)
    val gaps   = gapsOf(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(silent), pairs, asVar)
    assertEquals(clue(gaps).size, 1)
    assert(!gaps.head.fatal, "an unanswered question is a finding, not a refusal")
    assert(clue(gaps.head.why).contains("fabricated fact"))
    assertEquals(gapsOf(
      log("p.Base#w" -> IdiomVerdict.Refused("NotRequested", "mine")), List(silent), pairs, asVar), Nil)
  }

  test("a BASE port — no bases, or no pairs — asks nothing at all") {
    assertEquals(gapsOf(log("p.Base#w" -> IdiomVerdict.Converted),
                                            Nil, pairs, asVar), Nil)
    assertEquals(gapsOf(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), Map.empty, asVar), Nil)
  }

  test("the VERDICT comes from the phase's own log, never from a second derivation (§4.6)") {
    // K2.5's measured shape: a residue count that re-derived its own question could not tell a
    // refusal from a switched-off fix. Here the same rule keeps a fatal finding honest — every
    // answer this function gives about THIS run is a row the phase filed.
    val gaps = gapsOf(
      log("p.Base#w" -> IdiomVerdict.Refused("AnchoredClosure", "an unparsed parent")),
      List(collapsed), pairs, asVar)
    assertEquals(clue(gaps).size, 1, "the guard's NAME does not matter — only that it refused")
  }

  test("…and the SHAPE comes from the port's own `targetOf`, so `val` and `var` are not one answer") {
    val asVal = (_: String) => Target.Val
    assertEquals(gapsOf(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), pairs, asVal).size, 1,
      "the base published `var` and this run derived `val` — two different emitted surfaces")
    assert(gapsOf(log("p.Base#w" -> IdiomVerdict.Converted),
                                      List(collapsed), pairs, asVal).head.why.contains("a collapsed `val`"))
  }

  test("the DENOMINATOR is published beside the gaps — §3, read at this check") {
    // A dependent reporting `base-surface 0` because sixty verdicts AGREED and one reporting 0
    // because the comparison never ran are indistinguishable from the outside, and the second is
    // every way this silently stops working: a base map that was not discovered, a pairs table the
    // merge did not carry, a type row the base stopped emitting.
    val agreed = PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), pairs, asVar)
    assertEquals(agreed.gaps, Nil)
    assertEquals(clue(agreed.checked), 1, "…and it says the comparison HAPPENED")

    // the three ways it is legitimately zero, each of which must not read as agreement
    assertEquals(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            Nil, pairs, asVar).checked, 0, "no base")
    assertEquals(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(collapsed), Map.empty, asVar).checked, 0, "no pairs")
    val elsewhere = base("base-mod", List("p.Other"), List("p.Other#x" -> "var"))
    assertEquals(PortRun.collapseDivergence(log("p.Base#w" -> IdiomVerdict.Converted),
                                            List(elsewhere), pairs, asVar).checked, 0,
      "the base does not emit this type")
  }

  test("`MemberShape.form` ROUND-TRIPS, and is absent from every row that is not a collapse") {
    assertEquals(Surface.parseMember(Surface.render(Surface.MemberShape(form = "var"))).form, "var")
    assertEquals(Surface.render(Surface.MemberShape()), "", "sparse by design")
    // …and a map from an engine that never carried the key answers "", which reaches no comparison:
    // `PortMap.freshness` calls such a map Stale and the base is refused wholesale.
    assertEquals(Surface.parseMember("vis=private").form, "")
  }

  test("D15 bug 2: lookup by ACCESSOR name, not property key — a key mismatch silently reads as 'no row'") {
    // The port map keys member rows by the UPSTREAM accessor name (`Owner#getW`), while the
    // BeanCollapse idiom log records by the PROPERTY key (`Owner#w`). The lookup must translate
    // through the `pairs` table. Without the fix every pair whose property name differs from the
    // accessor name was reported `unanswered` — 79 of them on the gdx-test port.
    val accessorBase = base("base-mod", List("p.Base"),
      List("p.Base#getW" -> "var"))  // upstream key is the ACCESSOR name
    val accessorPairs = Map("p.Base#w" -> "getW/setW")
    // The idiom log records by PROPERTY key
    val gaps = gapsOf(
      log("p.Base#w" -> IdiomVerdict.Converted),
      List(accessorBase), accessorPairs, asVar)
    assertEquals(clue(gaps), Nil, "the accessor key `getW` matches the map row and the two agree")

    // …and a DISAGREEMENT is still reported when they disagree
    val accessorBasePlain = base("base-mod", List("p.Base"),
      List("p.Base#getW" -> ""))  // base did NOT collapse
    val disagree = gapsOf(
      log("p.Base#w" -> IdiomVerdict.Converted),
      List(accessorBasePlain), accessorPairs, asVar)
    assertEquals(clue(disagree).size, 1, "the lookup found the row and the two disagree")
    assert(disagree.head.fatal, "a genuine disagreement is still fatal")
  }
