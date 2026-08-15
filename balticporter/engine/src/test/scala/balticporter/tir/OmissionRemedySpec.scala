package balticporter.tir

import balticporter.catalog.CatalogLog
import balticporter.frontend.spoon.SpoonTir
import balticporter.runner.PortRun

/** THE `omissions` MENU — three accepts on a lane of seven kinds (`DESIGN.md` §8.16).
  *
  * The positives are cheap and the NEGATIVES are the reason this file exists, because every one of
  * them is silent by default. A remedy's `lane` and `kind` are plain strings that no compiler
  * checks, so a menu can declare a kind its check never files: the vocabulary accepts the id, the
  * key binds, the port reads `NeverApplied`, and its author goes looking for a site that is right
  * there. And a drain that fired one kind too wide would empty a lane the engine deliberately does
  * NOT let a port empty — four of this lane's seven kinds are LOSSES the port cannot honestly accept
  * (C3, C11, T1's residue, M6/I9), and an accept on one would drain a defect rather than a question.
  */
class OmissionRemedySpec extends munit.FunSuite:

  private val Java =
    """package com.demo;
      |public class Widget {
      |  public int size;
      |  public Widget(int n) { this.size = n; }
      |  public int get() { return size; }
      |}""".stripMargin

  private def program: Program = SpoonTir.fromSource(Java, catalog = CatalogLog.discarding)

  private def plan(p: Program, declared: Map[String, String]): ResolutionPlan =
    val binder = new PolicyBinder(p, p.members)
    val vocab  = RemedyVocabulary.from(PortRun.CheckRemedies)
    val pl     = ResolutionPlan.of(declared, vocab, vocab.byId.keySet, binder)
    binder.resolving(pl)
    pl

  private def sym(p: Program, fqn: String): SymId =
    p.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse(
      fail(s"no symbol $fqn in ${p.symbols.all.map(_.fullName).take(40).mkString(", ")}"))

  private def row(kind: String, at: SymId, owner: String = "com.demo.Widget") =
    OmissionCheck.Finding(kind, owner, "d", Origin.synthetic, at)

  // -------------------------------------------------------------------------------------------
  // the MENU is well-formed
  // -------------------------------------------------------------------------------------------

  test("every remedy names THIS check's lane and a kind this check really files") {
    OmissionCheck.remedies.foreach { r =>
      assertEquals(clue(r).lane, OmissionCheck.Name)
      assert(clue(OmissionCheck.Kind.all).contains(clue(r).kind))
      assertEquals(clue(r).alsoKinds, Nil) // this lane's kinds PARTITION it; none splits one site
    }
  }

  test("…and none is emission-affecting, which is what an `accept` MEANS") {
    // `emissionAffecting` is what puts a selection in §1.5's MUST-agree column. These three move a
    // row between two lanes and change no signature, so a base and a dependent choosing differently
    // cannot produce two ports that compile alone and fail together.
    OmissionCheck.remedies.foreach(r => assert(!clue(r).emissionAffecting))
  }

  test("…and the whole check-side vocabulary still assembles: no two remedies claim one id") {
    // `RemedyVocabulary.from` REFUSES a duplicate rather than resolving it. Asserted over the WHOLE
    // of `CheckRemedies` and not over this menu, deliberately: scoped to three ids it would pass
    // while one of them collided with a lane that shipped earlier.
    val v = RemedyVocabulary.from(PortRun.CheckRemedies)
    assertEquals(v.ids.size, PortRun.CheckRemedies.map(_.remedies.size).sum)
    OmissionCheck.remedies.foreach(r => assert(clue(v).contains(clue(r).id)))
  }

  test("…and it is reachable from a port CONFIG, not only from Scala") {
    // `PortRun.CheckRemedies` is what both the `.conf` loader's KNOWN set and the run's ACTIVE set
    // are built from: a check that declared a menu and did not register here would be a menu no
    // `.conf` could name and no run could apply, silently and in both directions.
    assert(clue(PortRun.CheckRemedies.toSet).contains(OmissionCheck))
  }

  // -------------------------------------------------------------------------------------------
  // the FOUR kinds that take NOTHING — the half of this lane a port may not empty
  // -------------------------------------------------------------------------------------------

  test("no remedy answers a LOSS: super-args, nilary ctor, cause message, anon member, lambda return") {
    // Each is an omission where the port runs LESS than java and no reading of the site yields
    // "this is fine": C3 (padding refused), C11 (all three keeps measured worse), the Throwable
    // delegation refusal, T1's residue, and M6/I9 — which is a WORK ITEM rather than a refusal, so
    // accepting it would retire it silently. Asserted on the MENU rather than on a drain, because a
    // property that held only because of which rows a fixture produced is one refactor from false.
    val answered = OmissionCheck.remedies.flatMap(_.kinds).toSet
    List(OmissionCheck.Kind.DroppedSuperArgs, OmissionCheck.Kind.DroppedNilaryCtor,
         OmissionCheck.Kind.DroppedCauseMessage, OmissionCheck.Kind.DroppedAnonMember,
         OmissionCheck.Kind.UnnameableLambdaReturn)
      .foreach(k => assert(!clue(answered).contains(clue(k))))
  }

  // -------------------------------------------------------------------------------------------
  // the DRAIN — it fires at the kind it declared, at the subject it declared, and nowhere else
  // -------------------------------------------------------------------------------------------

  test("`accept-promoted-body` drains its own kind at the CONSTRUCTOR and leaves every other kind") {
    val p  = program
    val at = sym(p, "com.demo.Widget#<init>")
    val pl = plan(p, Map("com.demo.Widget#<init>(int)" -> "accept-promoted-body"))
    val rows = OmissionCheck.Kind.all.map(row(_, at))
    val kept = OmissionCheck.resolved(pl, rows)
    assertEquals(clue(kept).map(_.what),
      OmissionCheck.Kind.all.filterNot(_ == OmissionCheck.Kind.PromotedBodyEveryPath))
    assertEquals(pl.all.map(_.remedy.id), List("accept-promoted-body"))
    assertEquals(pl.all.map(_.drained), List(1))
  }

  test("…and a SUPER-ARGS row at that very constructor is NOT drained by it") {
    // The sharpest negative this lane has: `super(args) dropped` and `promoted constructor body
    // runs on every path` fire at the SAME constructor on nine of the corpus's rows, and only the
    // second has an answer. A drain keyed on the declaration alone would take both.
    val p  = program
    val at = sym(p, "com.demo.Widget#<init>")
    val pl = plan(p, Map("com.demo.Widget#<init>(int)" -> "accept-promoted-body"))
    val kept = OmissionCheck.resolved(pl, List(row(OmissionCheck.Kind.DroppedSuperArgs, at)))
    assertEquals(kept.size, 1)
    assertEquals(pl.all, Nil)
  }

  test("`accept-dropped-annotation` is keyed at a MEMBER; the TYPE id is keyed at the type") {
    // Two ids for one act, because `Remedy.subject` is per remedy and this lane's rows sit at both.
    assertEquals(OmissionCheck.AcceptDroppedAnnotation.subject, Remedy.Subject.OwnedMember)
    assertEquals(OmissionCheck.AcceptDroppedTypeAnnotation.subject, Remedy.Subject.OwnedType)
    val p    = program
    val fld  = sym(p, "com.demo.Widget#size")
    val tpe  = sym(p, "com.demo.Widget")
    val pl   = plan(p, Map("com.demo.Widget#size" -> "accept-dropped-annotation",
                           "com.demo.Widget"      -> "accept-dropped-type-annotation"))
    val kept = OmissionCheck.resolved(pl, List(
      row(OmissionCheck.Kind.DroppedAnnotation, fld, "com.demo.Widget#size"),
      row(OmissionCheck.Kind.DroppedAnnotation, tpe)))
    assertEquals(clue(kept), Nil)
    assertEquals(pl.all.map(_.remedy.id).sorted,
      List("accept-dropped-annotation", "accept-dropped-type-annotation"))
    // …and neither reports a CONFLICT, which is the whole reason the pair can exist: a bare FQN and
    // an `owner#member` key cannot bind the same declaration, so `Remedy.overlaps` is never asked
    // about a pair that could answer one row.
    assertEquals(clue(pl.troubles), Nil)
  }

  test("…and the member id refuses a bare TYPE key, which is what keeps the pair honest") {
    // `Remedy.Subject.OwnedMember` binds through `bindMember`, whose grammar has no `#`-less form:
    // a port that wrote the type key against the member id is told so rather than silently binding
    // to something else. The two refusals stay each other's opposite (`DESIGN.md` §8.16).
    val p  = program
    val pl = plan(p, Map("com.demo.Widget" -> "accept-dropped-annotation"))
    assertEquals(pl.entries.map(_.target), List(scala.None))
    assert(pl.troubles.isEmpty) // the BINDER reported it, not the plan — one grammar, one sentence
  }

  test("a row whose `at` is None is UNSELECTABLE — never drained by a selection elsewhere") {
    val p  = program
    val pl = plan(p, Map("com.demo.Widget#<init>(int)" -> "accept-promoted-body"))
    val orphan = row(OmissionCheck.Kind.PromotedBodyEveryPath, SymId.None)
    assertEquals(OmissionCheck.resolved(pl, List(orphan)).size, 1)
    assertEquals(pl.all, Nil)
  }

  test("an EMPTY plan drains nothing and returns the findings unchanged") {
    // §1(b)'s empty-parameter rule at a menu: a port that selects nothing sees the lane it had.
    val rows = OmissionCheck.Kind.all.map(row(_, SymId.None))
    assertEquals(OmissionCheck.resolved(ResolutionPlan.empty, rows), rows)
  }
