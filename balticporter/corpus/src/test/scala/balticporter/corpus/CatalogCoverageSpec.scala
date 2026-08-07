package balticporter.corpus

import balticporter.catalog.{Attaches, CatalogLog, Differences, Dispatch, DiffId, JS, Lowering, Obligations, Status}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{CatalogCheck, Origin}

/** THE COVERAGE LANES, AND THE PROOF THAT THEY CAN FAIL.
  *
  * A coverage mechanism that only ever sees passing input cannot distinguish "the rule holds" from
  * "the rule is inert", and `DESIGN.md` §2.8 names the exact failure to avoid: a facility with no
  * call sites is indistinguishable from one that is not there. So every lane here is exercised in
  * BOTH directions — a run that discharges its obligations reports nothing, and a run that does not
  * reports precisely the row it skipped.
  *
  * The negatives are built from the SURFACE, never by poking the log: a probe that wrote a hole
  * into the log directly would prove that the reporting works and nothing about whether the wrapper
  * ever produces one.
  */
class CatalogCoverageSpec extends munit.FunSuite:

  private val origin = Origin("Snippet.java", 1, 1)
  /** a FRESH stand-in for the Java node being lowered — `Lowering.of`'s `subject`, which joins the
    * two dispatches of ONE node by identity. A `def`, so every call site is a different node. */
  private def node: AnyRef = new Object

  // -------------------------------------------------------------------------------------------
  // The wrapper, in isolation — the mechanism, before any Java is involved.
  // -------------------------------------------------------------------------------------------

  test("an arm that CONSULTS its attached row leaves no hole") {
    val log = new CatalogLog
    given CatalogLog = log
    Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin, node) {
      Obligations.consult(JS.E(3), origin)(scala.None)
    }
    // JS-E17 also attaches to this kind (at BOTH dispatches) and is `Open`, so it is a DECLARED
    // hole here and not a defect — the work list, which is what that lane is. What this test is
    // about is JS-E03, and consulting it leaves nothing owed.
    assert(!log.undischarged.map(_.id).contains(JS.E(3)))
    assertEquals(log.consulted(JS.E(3)), 1)
    assertEquals(log.fired(JS.E(3)), 0)
  }

  test("THE NEGATIVE: an arm that returns WITHOUT consulting is reported, with its kind and site") {
    // The `_ => false`-style probe every check in this engine owes. `Lowering.of` is entered
    // exactly as the frontend enters it and the body simply declines to ask — which is the defect
    // shape, written down.
    val log = new CatalogLog
    given CatalogLog = log
    Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin, node)(())
    val holes = log.undischarged
    // two rows attach at this (kind, dispatch): JS-E03, which the registry says is handled — so its
    // hole is an ENGINE GAP — and JS-E17, which the registry itself calls `Open`, so its hole is
    // DECLARED. The lane keeps them apart by the `kind` column and this is the pair that shows it.
    assertEquals(holes.map(_.id), List(JS.E(3), JS.E(17)))
    assertEquals(holes.head.kind, "CtOperatorAssignment")
    assertEquals(holes.head.dispatch, Dispatch.Statement)
    assertEquals(CatalogCheck.undischarged(log).map(_.kind), List("ENGINE GAP", "declared open"))
  }

  test("…and the hole is one finding per ROW, however many sites produced it") {
    val log = new CatalogLog
    given CatalogLog = log
    (1 to 40).foreach(_ => Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin, node)(()))
    // one finding per ROW — two rows attach here, and each is reported once with 40 sites behind it.
    assertEquals(log.undischarged.size, 2)
    assertEquals(log.undischarged.map(_.sites), List(40, 40))
  }

  test("THE DELEGATION SEAM: one node lowered by BOTH dispatches is one obligation, not two") {
    // `SpoonTir.stmtArm` hands whole nodes to the expression arm — `case inv: CtInvocation =>
    // expr(inv)`, `case cc: CtConstructorCall => ctorCall(cc)`, and the `CtUnaryOperator` default.
    // The inner dispatch opens a scope of its own, so every consult happens there; a row attached at
    // the STATEMENT dispatch of such a kind would be reported as a hole at every one of those nodes
    // while the arm had in fact considered it. No row is in that position today, and every row that
    // ever attaches to a delegating statement kind would be.
    val log = new CatalogLog
    given CatalogLog = log
    val one = node
    Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin, one) {
      Lowering.of("CtOperatorAssignment", Dispatch.Expression, origin, one) {
        Obligations.consult(JS.E(3), origin)(scala.None)
      }
    }
    assert(!log.undischarged.map(_.id).contains(JS.E(3)),
      "the inner dispatch consulted it for this very node — the outer scope has no hole to report")
    assertEquals(log.consulted(JS.E(3)), 1, "and it is counted ONCE: two scopes, one consideration")
  }

  test("…and the join is by NODE IDENTITY: a CHILD's consult discharges nothing of its parent's") {
    // The negative that makes the rule above a rule rather than a leak. `if (x) y += 1` puts a
    // second `CtOperatorAssignment` INSIDE a statement scope on the same line and of the same kind,
    // so anything reading `at` or `kind` would take the child's consult for the parent's.
    val log = new CatalogLog
    given CatalogLog = log
    Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin, node) {
      Lowering.of("CtOperatorAssignment", Dispatch.Expression, origin, node) {
        Obligations.consult(JS.E(3), origin)(scala.None)
      }
    }
    // (JS-E04 and JS-E17 are holes here too — the inner scope owes both and this probe consults
    // neither — which is not what this assertion is about.)
    assert(log.undischarged.map(_.id).contains(JS.E(3)),
      "a different node consulted it; this statement's own obligation is still owed")
  }

  test("the DISPATCH is part of the key — JS-E03 is owed at a statement and JS-E04 at an expression") {
    // The pair the whole mechanism was designed around. A kind-only attachment could not tell them
    // apart, and the two rows exist precisely because java gives one node kind two meanings.
    // JS-E17 rides on both, being a `Dispatch.Either` row: the lvalue is evaluated twice whichever
    // position the node is in, so it is one difference and not the E03/E04 pair.
    assertEquals(Differences.owedAt("CtOperatorAssignment", Dispatch.Statement), List(JS.E(3), JS.E(17)))
    assertEquals(Differences.owedAt("CtOperatorAssignment", Dispatch.Expression), List(JS.E(4), JS.E(17)))
    assertEquals(Differences.owedAt("CtLiteral", Dispatch.Expression), Nil)
  }

  test("FATAL mode raises on a hole the registry claims is handled, and never on a declared-open row") {
    val fatal = new CatalogLog(fatal = true)
    intercept[AssertionError] {
      given CatalogLog = fatal
      Lowering.of("CtOperatorAssignment", Dispatch.Statement, origin, node)(())
    }
    // JS-E17 is `Open`. It attaches at BOTH dispatches, no arm consults it, and a testkit run must
    // NOT die on it — it is the work list, and a mode that died on the work list would make the
    // work list unrunnable. This is the one exemption, and it is derived from the row's own status,
    // which is why the body below discharges the HANDLED row beside it and still does not raise.
    val alsoFatal = new CatalogLog(fatal = true)
    given CatalogLog = alsoFatal
    Lowering.of("CtOperatorAssignment", Dispatch.Expression, origin, node) {
      Obligations.consult(JS.E(4), origin)(scala.None)
    }
    assertEquals(alsoFatal.undischarged.map(_.id), List(JS.E(17)))
  }

  // -------------------------------------------------------------------------------------------
  // The lanes, over a real lowering.
  // -------------------------------------------------------------------------------------------

  private def lower(java: String): CatalogLog =
    val log = new CatalogLog
    SpoonTir.fromSource(java, catalog = log)
    log

  test("a real lowering reaches the JS-E rows the frontend wires, and reports the one it does not") {
    val log = lower("""
      public class S {
        int f(Object a, Object b, int i, byte c, String s) {
          boolean same = a == b;
          // `i++` as a VALUE. As a bare statement it reaches `stmtKind`'s own increment arm, which
          // lowers to a plain `Assign` because the value is discarded — so the row's
          // `Dispatch.Expression` attachment is exactly right and the statement arm owes nothing.
          int m = i++;
          c += 3;
          String t = a + "x";
          int j = (i > 0) ? i : 0;
          int k = (j = i);
          return k;
        }
      }
    """)
    // consulted AND fired: the difference was considered here and it applied.
    List(JS.E(1), JS.E(2), JS.E(3), JS.E(14), JS.E(15)).foreach { id =>
      assert(log.consulted(id) > 0, s"$id was never consulted")
      assert(log.fired(id) > 0, s"$id was consulted and never applied")
    }
    // JS-E04 attaches at the EXPRESSION dispatch, and `k = (j = i)` is a plain assignment — so this
    // snippet never opens a scope that owes it. That is what makes the `consulted` lane a
    // measurement rather than a constant: a row is reached because the port has the shape, not
    // because the arm exists.
    assert(!log.reached.contains(JS.E(4)), "no compound assignment in expression position here")
  }

  test("…and a compound assignment IN EXPRESSION POSITION reaches JS-E04 and narrows") {
    // The other half of the pair, and the one that would have been silently wrong: the identical
    // narrowing sat twelve lines away in the statement arm for the whole life of the frontend, and
    // what reported it was the wrapper rather than a compile or a count.
    val log = lower("public class S { int f(byte c) { return (c += 3); } }")
    assert(log.fired(JS.E(4)) > 0, "JS-E04 was consulted and never applied at a `byte` compound assign")
    assert(log.fired(JS.E(3)) == 0, "the statement row must not fire for an expression-position node")
  }

  test("THE NEGATIVE for `unreached`: a port that lowers nothing reports every mechanised row") {
    val empty = new CatalogLog
    val rows  = CatalogCheck.unreached(empty).map(_.owner).toSet
    assertEquals(rows, Differences.mechanised.map(_.id.toString).toSet)
    assert(rows.nonEmpty, "no row is mechanised — the unreached lane would be vacuous")
  }

  test("…and `unreached` is NARROWED to rows whose surface exists — the [rev-1] claim, mechanised") {
    // A lane reporting "this row is live" on the strength of a surface nobody built would be
    // reporting about the mechanism and not about the port. So an `Unmechanised` row may never
    // appear on the unreached lane, however unreached it is.
    val unreached    = CatalogCheck.unreached(new CatalogLog).map(_.owner).toSet
    val unmechanised = Differences.unmechanised.map(_.id.toString).toSet
    assertEquals(unreached.intersect(unmechanised), Set.empty[String])
    assert(unmechanised.nonEmpty, "nothing is unmechanised — this narrowing would be vacuous")
    assertEquals(CatalogCheck.unmechanised.map(_.owner).toSet, unmechanised)
  }

  test("the `consulted` lane counts rows and not sites, and moves with the wiring") {
    val log = lower("public class S { boolean f(Object a, Object b) { return a == b; } }")
    val reached = CatalogCheck.consulted(log).map(_.owner).toSet
    assert(reached.contains(JS.E(1).toString))
    // JS-E03 attaches at the STATEMENT dispatch and this snippet has no compound assignment, so it
    // is consulted zero times — which is the honest answer and is what makes the lane a measurement
    // rather than a constant.
    assert(!reached.contains(JS.E(3).toString))
  }

  test("catalog.tsv holds EVERY row, reached or not — the artifact answers `never touched`") {
    val log  = lower("public class S { int f(int i) { return i; } }")
    val rows = CatalogCheck.tsv(log)
    assertEquals(rows.size, Differences.all.size)
    assert(rows.exists(_.startsWith("JS-E04\t")), "a never-reached row must still have a line")
  }

  test("every row's attachment is HONEST about the surface that exists") {
    // The registry's own guard rail, and the one an area wave will be tempted to bend: a row may
    // not claim a lowering attachment at a Spoon kind the frontend does not dispatch on, because
    // the obligation would then be owed at a site nothing ever enters — a claim that reads as
    // coverage and can never fail.
    // …and through `leaves`, because a row may attach at more than one place: an `Attaches.Both`
    // is not an `Attaches.Lowered`, so an `isInstanceOf` on the top-level value silently skips the
    // lowering half of every two-surface row — which is the one shape this guard exists to hold.
    val dispatched = balticporter.frontend.spoon.SpoonKinds.lowered.map(_.name).toSet
    val bad = Differences.all.flatMap(d =>
      Differences.leaves(d.attaches).collect { case Attaches.Lowered(k, _) => (d.id, k) })
      .filterNot((_, k) => dispatched.contains(k))
    assertEquals(bad, Nil, s"attached to a kind no arm lowers: $bad")
  }

  /** WHICH of the two term dispatches a lowered kind can actually be reached at.
    *
    * Derived from the `by` symbol the registry already carries, which is the claim being checked:
    * `stmtKind` is the statement dispatch and `exprNoCast` is the expression one, and a kind whose
    * claim names one of them cannot be reached at the other. Where the claim names a NAMED HELPER
    * instead (`SpoonTir.invocation`, `SpoonTir.literal`, `SpoonTir.classDef`), the helper says
    * nothing about position, so the question falls to Spoon's own hierarchy — a node reaches
    * `stmtKind` iff it is a `CtStatement` and `exprNoCast` iff it is a `CtExpression`, because those
    * two wrappers are entered for every node of those types and for nothing else.
    *
    * Both halves are structural (§4.56): one reads the registry's own recorded symbol, the other
    * reads the class hierarchy out of the jar. Neither is a hand-written table of kinds, which is
    * what would go stale the first time an arm moved. */
  private def legalDispatches(k: balticporter.frontend.spoon.SpoonKinds.Kind): Set[Dispatch] =
    import balticporter.frontend.spoon.SpoonKinds
    val by = k.claim match
      case SpoonKinds.Claim.Lowered(b) => b
      case _                           => ""
    val fromClaim =
      Set(Option.when(by.contains("stmtKind"))(Dispatch.Statement),
          Option.when(by.contains("exprNoCast"))(Dispatch.Expression)).flatten
    if fromClaim.nonEmpty then fromClaim
    else
      // …and a kind that is NEITHER — a `CtMethod`, a `CtField` — answers the empty set, which is
      // the honest answer: no term dispatch is ever entered for it, so no `Lowered` attachment can
      // be right about it. Resolved out of both node packages rather than one, because a name that
      // does not resolve would otherwise throw where the sweep wants a finding.
      def resolve(pkg: String): Option[Class[?]] =
        try Some(Class.forName(s"$pkg.${k.name}", false, getClass.getClassLoader))
        catch { case _: ClassNotFoundException => scala.None }
      resolve("spoon.reflect.code").orElse(resolve("spoon.reflect.declaration")) match
        case scala.None => Set.empty
        case Some(cls)  => Set(
          Option.when(classOf[spoon.reflect.code.CtStatement].isAssignableFrom(cls))(Dispatch.Statement),
          Option.when(classOf[spoon.reflect.code.CtExpression[?]].isAssignableFrom(cls))(Dispatch.Expression),
        ).flatten

  test("…and about the DISPATCH — a kind only a statement arm reaches owes nothing as an expression") {
    // The guard above validates the KIND and stops there, so `Lowered("CtAssert", Expression)` — a
    // statement-only kind claimed at the expression dispatch — passes it while owing an obligation
    // at a scope `exprNoCast` never opens for that kind. `Differences.owedAt` would answer with the
    // row, `Lowering.of` would never be entered with that pair, and the obligation would read as
    // coverage that can never fail: exactly the shape the kind half exists to prevent, one column
    // over.
    import balticporter.frontend.spoon.SpoonKinds
    def complaint(id: String, k: String, disp: Dispatch): Option[String] =
      val legal = SpoonKinds.byName.get(k).map(legalDispatches).getOrElse(Set.empty)
      val asked = disp match
        case Dispatch.Either => Set(Dispatch.Statement, Dispatch.Expression)
        case one             => Set(one)
      Option.when(!asked.subsetOf(legal))(
        s"$id attaches $k/$disp, and $k is reached at ${legal.mkString("{", ", ", "}")}")

    // …through `leaves`, for the reason the KIND guard above states and this one did not follow:
    // an `Attaches.Both` is not an `Attaches.Lowered`, so a top-level `match` skipped the lowering
    // half of every two-surface row — which is exactly the population the DISPATCH question is
    // about, since a `Both` exists precisely because one row is decided at more than one place.
    // The two guards are one rule read at two columns and had two different answers.
    val bad = Differences.all.flatMap(d =>
      Differences.leaves(d.attaches).collect {
        case Attaches.Lowered(k, disp) => complaint(d.id.toString, k, disp)
      }.flatten)
    assertEquals(bad, Nil, bad.mkString("\n"))

    // THE NEGATIVE, through the same function the sweep runs — a probe that poked at the derivation
    // instead would prove the derivation and nothing about the guard. `CtAssert` is a `CtStatement`
    // and not a `CtExpression`, and `SpoonTir` lowers it in `stmtKind`.
    assert(complaint("JS-X99", "CtAssert", Dispatch.Expression).isDefined,
      "a statement-only kind claimed at the expression dispatch must be reported")
    assert(complaint("JS-X99", "CtAssert", Dispatch.Either).isDefined)
    assert(complaint("JS-X99", "CtAssert", Dispatch.Statement).isEmpty)
    assertEquals(legalDispatches(SpoonKinds.byName("CtAssert")), Set(Dispatch.Statement))
    assertEquals(legalDispatches(SpoonKinds.byName("CtOperatorAssignment")),
      Set(Dispatch.Statement, Dispatch.Expression))
    assertEquals(legalDispatches(SpoonKinds.byName("CtBinaryOperator")), Set(Dispatch.Expression))
    // a kind whose claim names a HELPER rather than either dispatcher — answered by the hierarchy.
    assertEquals(legalDispatches(SpoonKinds.byName("CtInvocation")),
      Set(Dispatch.Statement, Dispatch.Expression))
    assertEquals(legalDispatches(SpoonKinds.byName("CtLiteral")), Set(Dispatch.Expression))
  }
