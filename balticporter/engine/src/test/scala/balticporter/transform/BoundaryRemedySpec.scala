package balticporter.transform

import balticporter.runner.PortRun
import balticporter.tir.*

/** THE THREE BOUNDARY MENUS — what each check offers, and the two things a menu can be wrong about
  * that nothing else in a run would notice. */
class BoundaryRemedySpec extends munit.FunSuite:

  private val menus: List[(RemedySource, String, Set[String])] = List(
    (CollectionBoundaryCheck, CollectionBoundaryCheck.Name,
     CollectionBoundaryCheck.Issue.values.map(_.toString).toSet),
    (ContextSeamCheck, ContextSeamCheck.Name,
     ContextSeamCheck.Kind.values.map(_.label).toSet),
    (NullabilityBoundaryCheck, NullabilityBoundaryCheck.Name,
     NullabilityBoundaryCheck.Issue.values.map(_.toString).toSet),
  )

  test("every remedy names ITS OWN check's lane, and a kind that check really files") {
    menus.foreach { (source, lane, kinds) =>
      source.remedies.foreach { r =>
        assertEquals(clue(r).lane, lane)
        assert(clue(kinds).contains(clue(r).kind))
      }
    }
  }

  test("…and the whole check-side vocabulary assembles: no two remedies claim one id") {
    // `RemedyVocabulary.from` REFUSES a duplicate rather than resolving it, so this is the assertion
    // that EVERY check-side menu the engine ships can be held at once — the thing a run does on
    // every port, and the property that breaks the day two lanes reach for the same obvious slug.
    val v = RemedyVocabulary.from(PortRun.CheckRemedies)
    assertEquals(v.ids.size, PortRun.CheckRemedies.map(_.remedies.size).sum)
    menus.foreach((source, _, _) => source.remedies.foreach(r => assert(clue(v).contains(clue(r).id))))
  }

  test("…and none of them is emission-affecting, which is what an `accept` MEANS") {
    // Not decoration: `emissionAffecting` is what puts a selection in §1.5's MUST-agree column. An
    // accept moves a row between two lanes and changes no byte, so a dependent that inherited one
    // and a base that declared it cannot produce two ports that fail to compile together.
    menus.flatMap(_._1.remedies).foreach(r => assert(!clue(r).emissionAffecting))
  }

  test("…and every one of them is reachable from a port CONFIG, not only from Scala") {
    // `PortRun.CheckRemedies` is what both the `.conf` loader's KNOWN set and the run's ACTIVE set
    // are built from, so a check that declared a menu and did not register here would be a menu no
    // `.conf` could name and no run could apply — silently, in both directions.
    val registered = PortRun.CheckRemedies.toSet
    menus.foreach((source, _, _) => assert(clue(registered).contains(source)))
  }

  // -------------------------------------------------------------------------------------------
  // the DRAIN, per lane — it fires at the kind it declared and at nothing else
  // -------------------------------------------------------------------------------------------

  private val Java =
    """package com.demo;
      |public class Widget {
      |  public int size() { return 1; }
      |}""".stripMargin

  private def fixture(key: String, id: String): (Program, SymId, ResolutionPlan) =
    val p      = balticporter.frontend.spoon.SpoonTir.fromSource(Java, catalog = balticporter.catalog.CatalogLog.discarding)
    val binder = new PolicyBinder(p, p.members)
    val vocab  = RemedyVocabulary.from(PortRun.CheckRemedies)
    val plan   = ResolutionPlan.of(Map(key -> id), vocab, vocab.byId.keySet, binder)
    binder.resolving(plan)
    (p, p.symbols.all.find(_.fullName == "com.demo.Widget#size").get.id, plan)

  private def widgetType(p: Program): SymId =
    p.symbols.all.find(_.fullName == "com.demo.Widget").get.id

  test("nullability: `accept-scoped-out` drains a ScopedOut row and leaves every other kind") {
    val (_, size, plan) = fixture("com.demo.Widget#size", "accept-scoped-out")
    def row(i: NullabilityBoundaryCheck.Issue) =
      NullabilityBoundaryCheck.Finding(i, "com.demo.Widget#size", "d", Origin.synthetic, SymId.None, size)
    val rows = NullabilityBoundaryCheck.Issue.values.toList.map(row)
    val kept = NullabilityBoundaryCheck.resolved(plan, rows)
    assertEquals(kept.map(_.issue), rows.map(_.issue).filterNot(_ == NullabilityBoundaryCheck.Issue.ScopedOut))
    assertEquals(plan.all.map(_.remedy.id), List("accept-scoped-out"))
  }

  test("…and a row whose `at` is None is UNSELECTABLE — never drained by a selection elsewhere") {
    // `SymId.None` is what a site with no nameable declaration carries (a method local, whose owner
    // is not a declaration either). A fallback to the finding's UNIT would have made one selection
    // drain every row in a file, which is why the default is None and not that.
    val (_, _, plan) = fixture("com.demo.Widget#size", "accept-scoped-out")
    val orphan = NullabilityBoundaryCheck.Finding(
      NullabilityBoundaryCheck.Issue.ScopedOut, "?#local", "d", Origin.synthetic, SymId.None)
    assertEquals(NullabilityBoundaryCheck.resolved(plan, List(orphan)).size, 1)
    assertEquals(plan.all, Nil)
  }

  test("context-seam: `accept-unconstructed-thread` is keyed at the TYPE, not at a constructor") {
    val (p, _, plan) = fixture("com.demo.Widget", "accept-unconstructed-thread")
    val at = widgetType(p)
    def row(k: ContextSeamCheck.Kind) =
      ContextSeamCheck.Finding(k, "com.demo.Widget", "holder", "d", Origin.synthetic, at)
    val rows = ContextSeamCheck.Kind.values.toList.map(row)
    val kept = ContextSeamCheck.resolved(plan, rows)
    assertEquals(kept.map(_.kind), rows.map(_.kind).filterNot(_ == ContextSeamCheck.Kind.UnconstructedThread))
    assertEquals(plan.all.map(_.subjectFqn), List("com.demo.Widget"))
  }

  test("…and NOTHING drains a `lost-clause`, which is an engine bug and not a port's to silence") {
    // The one kind in this check that is reachable from no manifest key (`DESIGN.md` §8.2,
    // `ENGINE-LIMITS.md` CT5). Asserted on the MENU and not on the run's plumbing on purpose: the
    // run appends those rows after the drain has already happened, and a property that held only
    // because of WHERE a list was concatenated is a property one refactor away from being false.
    val ids = ContextSeamCheck.remedies.map(_.kind).toSet
    assert(!clue(ids).contains(ContextSeamCheck.Kind.LostClause.label))
  }

  test("collection-boundary: the two accepts are keyed at the EXTERNAL callee") {
    // Both rows' `enclosing` is the callee — the phase records them there because the question is
    // per external METHOD — so the selection key names a member this program references and does not
    // declare, and an `Ownership.Owned` binding would have refused it as `ExternalOnly`.
    CollectionBoundaryCheck.remedies.foreach(r =>
      assertEquals(clue(r).subject, Remedy.Subject.ExternalMember))
  }

  test("…and a REFUSED kind (`ReifiedOccurrence`, `InexpressibleParent`) has no entry at all") {
    // K18 and K5.7 — known divergences the engine refuses to repair, not review lists. An `accept`
    // on either would drain a defect rather than a question.
    val kinds = CollectionBoundaryCheck.remedies.map(_.kind).toSet
    assert(!clue(kinds).contains(CollectionBoundaryCheck.Issue.ReifiedOccurrence.toString))
    assert(!clue(kinds).contains(CollectionBoundaryCheck.Issue.InexpressibleParent.toString))
  }
