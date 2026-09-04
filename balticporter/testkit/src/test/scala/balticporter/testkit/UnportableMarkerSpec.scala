package balticporter.testkit

import balticporter.tir.*

/** THE MARKER (`DESIGN.md` §6.2/§6.4/§6.5), under test end to end. */
class UnportableMarkerSpec extends PortSuite:

  private val src = "package p; public class M { public int go(int a) { return a + 1; } }"

  /** wraps the body of `go` in an OPEN marker, as a frontend mint site would. */
  private class Mint extends Phase:
    def name: String = "test/mint"
    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if !p.symbolOf(d.symbol).exists(_.name == "go") then d
      else d.copy(rhs = d.rhs.map(r =>
        Tree.Unportable.open(r, UnportableKind.UnmodelledNodeKind("CtSwitchExpression"),
          Some(balticporter.catalog.DiffId(balticporter.catalog.Area.S, 9)),
          "a switch EXPRESSION, which the frontend has no arm for", r.tpe, r.origin)))

  // a `lazy val`, not a `def`: `Ported.emitter` is a value that RECORDS as it renders (§5.1), so a
  // fresh fixture per call would hand each test an emitter that has emitted nothing.
  private lazy val marked = port(src, new Mint)

  // -- the smart constructor -----------------------------------------------------------------

  test("a marker must point at REAL JAVA — a synthetic origin is refused at construction") {
    // §6.2's rule, and the precondition `markerKey` depends on: `<synthetic>:0:0` would collapse
    // every marker in a program onto ONE key, and the conservation check would then report nothing
    // while looking correct.
    val t = Tree.Literal(Constant.UnitC, TypeRepr.NoType, Origin.synthetic)
    intercept[IllegalArgumentException](
      Tree.Unportable.open(t, UnportableKind.FrontendBlindSpot, scala.None, "x",
        TypeRepr.NoType, Origin.synthetic))
  }

  // -- the traversal -------------------------------------------------------------------------

  /** a phase that renames nothing and rewrites every `Literal(IntC)` — the shape that must reach
    * INSIDE an approximation, or a later whole-program transform can never be what fixes it. */
  private class BumpInts extends Phase:
    def name: String = "test/bump-ints"
    override def transformTerm(t: Term)(using Program): Term = t match
      case l @ Tree.Literal(Constant.IntC(n), _, _) => l.copy(const = Constant.IntC(n + 100))
      case other                                    => other

  test("every phase's hooks reach INSIDE the approximation, and the wrapper is REBUILT") {
    val p  = port(src, new Mint, new BumpInts)
    val ms = MarkerCheck.inventory(p.after, p.after.units)
    assertEquals(ms.size, 1, "the marker must survive a phase that does not know about markers")
    // the literal INSIDE the marked region was rewritten, which is the property that makes keeping
    // a marked tree worth anything at all.
    assert(TirPrinter.program(TirPrinter.Style.canonical)(using p.after).contains("101"),
      "the phase did not reach inside the marker")
  }

  test("a phase that matches for a SHAPE simply fails to match a wrapped one — marker-preserved") {
    // The safe default, stated as a fixture: `BumpInts` matches a `Literal` and the marked body is
    // a `Return`, so nothing about the marker itself is disturbed and it is still Open.
    val p  = port(src, new Mint, new BumpInts)
    assert(MarkerCheck.inventory(p.after, p.after.units).forall(_.marker.state.isOpen))
  }

  // -- the conservation law ------------------------------------------------------------------

  /** the defect the check exists for: a phase that DELETES a marked subtree. */
  private class EraseBodies extends Phase:
    def name: String = "test/erase-bodies"
    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if !p.symbolOf(d.symbol).exists(_.name == "go") then d
      else d.copy(rhs = d.rhs.map(r => Tree.Literal(Constant.IntC(0), r.tpe, r.origin)))

  /** …and the act that is NOT that defect: an explicit discharge. */
  private class DischargeMarkers extends Phase:
    def name: String = "test/discharge"
    override def transformTerm(t: Term)(using Program): Term = t match
      case m: Tree.Unportable => m.resolved(name, "the fixture decided this shape is expressible")
      case other              => other

  test("an ERASED marker is a finding — the emitted code is identical and nothing else can see it") {
    val p  = port(src, new Mint, new EraseBodies)
    val fs = MarkerCheck.check(p.before, p.after, p.after.units)
    // `p.before` holds no markers — the mint happens in the pipeline — so the check is run against
    // the program the MINTING phase produced instead, which is what `PortRun` does with `parsed`.
    val minted = port(src, new Mint)
    val real   = MarkerCheck.check(minted.after, p.after, p.after.units)
    assertEquals(fs, Nil, "with nothing minted before the pipeline there is nothing to conserve")
    assertEquals(real.map(_.kind), List("erased"))
    assert(real.head.detail.contains("deleted the marked subtree instead of discharging it"))
  }

  test("a DISCHARGED marker is not a finding — and that is the whole difference") {
    val minted = port(src, new Mint)
    val p      = port(src, new Mint, new DischargeMarkers)
    assertEquals(MarkerCheck.check(minted.after, p.after, p.after.units).filter(_.kind == "erased"), Nil)
    val inv = MarkerCheck.inventory(p.after, p.after.units)
    assertEquals(inv.size, 1)
    assertEquals(inv.head.marker.state, MarkerState.Resolved("test/discharge",
      "the fixture decided this shape is expressible"))
  }

  test("a marker whose whole DECLARATION is gone is not an erasure — no exemption list needed") {
    // §6.5's own risk row. The owner answers it: if the declaration is gone then so is everything
    // in it, and the marker went WITH the code rather than being taken out of it.
    class DropTheMethod extends Phase:
      def name: String = "test/drop-method"
      override def transformClassDef(c: Tree.ClassDef)(using p: Program): Tree.ClassDef =
        c.copy(body = c.body.filterNot {
          case d: Tree.DefDef => p.symbolOf(d.symbol).exists(_.name == "go")
          case _              => false
        })
    val minted = port(src, new Mint)
    val p      = port(src, new Mint, new DropTheMethod)
    assertEquals(MarkerCheck.check(minted.after, p.after, p.after.units).filter(_.kind == "erased"), Nil)
  }

  test("an OPEN marker is a finding on its own, carrying its FIRST remedy") {
    val fs = MarkerCheck.check(marked.after, marked.after, marked.after.units).filter(_.kind == "open")
    assertEquals(fs.size, 1)
    assert(fs.head.detail.contains("unmodelled-node-kind(CtSwitchExpression)"))
    assert(fs.head.detail.contains("JS-S09"), s"the catalog id must be in the finding: ${fs.head.detail}")
    assert(fs.head.detail.contains("§1(a) ENGINE:"), "a finding must say which of §1's kinds the fix is")
  }

  // -- emission --------------------------------------------------------------------------------

  test("the SHIPPING default renders an open marker as `compiletime.error` — loudest, not quietest") {
    // The orchestrator's gate means a real deliverable run never reaches this branch, because the
    // tree is not written at all. What reaches it is an emitter with no orchestrator around it,
    // which is every fixture — so the default has to be the loud answer.
    assertEmits(marked, "scala.compiletime.error")
    assertEmits(marked, "porter: unrenderable")
    assertEmits(marked, "unmodelled-node-kind(CtSwitchExpression)")
  }

  test("the refusal is RECORDED as `Unrenderable` — §2.6's reconciliation, not a second kind") {
    marked.out // the decisions are the EMITTER's, made while rendering; nothing exists before it runs
    val ds = marked.emitter.emissionDecisions.filter(_.kind == Decision.Kind.Unrenderable)
    assertEquals(ds.size, 1)
    assertEquals(ds.head.detail("construct"), "unmodelled-node-kind(CtSwitchExpression)")
    assertEquals(ds.head.detail("catalog"), "JS-S09")
  }

  test("BEST EFFORT renders the approximation inside deterministic fences, with a file banner") {
    val p = port(src, new Mint)
    assert(p.bestEffortOut.contains("/* balticporter:unportable unmodelled-node-kind(CtSwitchExpression) JS-S09"),
      p.bestEffortOut)
    assert(p.bestEffortOut.contains("/* balticporter:end-unportable */"))
    // the APPROXIMATION is inside the fence — a comment cannot change program shape, which is what
    // makes the fence admissible at all, and the whole point of the mode is that an operator can
    // read the file.
    assert(p.bestEffortOut.contains("a + 1"))
    assert(!p.bestEffortOut.contains("compiletime.error"))
    // …and the banner, above everything, naming the regions. A file that looks like deliverable
    // output and is not is the single thing this mode must never produce.
    assert(p.bestEffortOut.contains("BEST-EFFORT OUTPUT"))
    assert(p.bestEffortOut.contains("This file MUST NOT ship."))
    assert(p.bestEffortOut.contains("unmodelled-node-kind(CtSwitchExpression) [JS-S09] at"))
  }

  test("AT ZERO OPEN MARKERS the two modes are byte-identical — §6.4's standing claim") {
    // Stated as *by construction, same emitter, same tree*, which is true and is exactly the kind
    // of claim that stops being true one refactor later. No marker means no fence and no banner, so
    // there is nothing for the mode to add.
    val p = port(src)
    assertEquals(p.bestEffortOut, p.out)
    assertEquals(MarkerCheck.inventory(p.after, p.after.units), Nil)
  }

  test("…and a RESOLVED marker keeps that identity: a discharge is not a degradation") {
    val p = port(src, new Mint, new DischargeMarkers)
    assertEquals(p.bestEffortOut, p.out)
  }

  test("a fence may never OPEN or CLOSE a comment — Scala block comments NEST (§4.58)") {
    class MintNasty extends Phase:
      def name: String = "test/mint-nasty"
      override def transformDefDef(d: Tree.DefDef)(using pr: Program): Tree.DefDef =
        if !pr.symbolOf(d.symbol).exists(_.name == "go") then d
        else d.copy(rhs = d.rhs.map(r =>
          Tree.Unportable.open(r, UnportableKind.FrontendBlindSpot, scala.None,
            "see the /* marker */ in the source", r.tpe, r.origin)))
    val out = port(src, new MintNasty).bestEffortOut
    assert(!out.contains("see the /* marker */"), out)
    assert(out.contains("see the / * marker * /"), out)
  }

  test("a RESOLVED marker renders as its inner and nothing else — work done is not a residue") {
    val p = port(src, new Mint, new DischargeMarkers)
    assertNotEmits(p, "compiletime.error")
    assertNotEmits(p, "balticporter:unportable")
    assertEmits(p, "a + 1")
  }
