package balticporter.tir

import balticporter.catalog.CatalogLog
import balticporter.frontend.spoon.SpoonTir
import balticporter.runner.PortRun

/** THE `jdk-surface` MENU — one accept, at an EXTERNAL callee (`DESIGN.md` §8.16).
  *
  * Two things here can be wrong with no other instrument to see them. The KEY SHAPE: this lane's
  * subject column is the MEMBER and the member is one the program does not declare, so an
  * `Ownership.Owned` binding refuses every key a reader could copy out of the report — the round
  * trip §8.18 calls "a printed key and a bindable key are the same key". And the KIND: `unhandled`
  * is the one of this lane's three findings that a port can settle by reading a call, while
  * `kept-iterable` stands for a compile error that is really there (K9) and `stale-refusal` is a
  * contradiction between two ENGINE tables. A drain one kind too wide would let a port accept both.
  */
class JdkSurfaceRemedySpec extends munit.FunSuite:

  private val Java =
    """package com.demo;
      |import java.util.Arrays;
      |public class Widget {
      |  public void fill(float[] xs) { Arrays.fill(xs, 1f); }
      |}""".stripMargin

  private def program: Program = SpoonTir.fromSource(Java, catalog = CatalogLog.discarding)

  private def plan(p: Program, declared: Map[String, String]): ResolutionPlan =
    val binder = new PolicyBinder(p, p.members)
    val vocab  = RemedyVocabulary.from(PortRun.CheckRemedies)
    val pl     = ResolutionPlan.of(declared, vocab, vocab.byId.keySet, binder)
    binder.resolving(pl)
    pl

  /** the interned EXTERNAL member — the thing this lane's subject column names. */
  private def callee(p: Program): SymId =
    p.symbols.all.find(s => s.name == "fill" && !p.owns(s.id)).map(_.id)
      .getOrElse(fail("the frontend interned no external `Arrays#fill`"))

  private def row(d: JdkSurfaceCheck.Disposition, at: SymId) =
    JdkSurfaceCheck.Finding("java.util.Arrays#fill(float[],float)", d, 1, Origin.synthetic, at)

  /** the three dispositions `isFinding` admits — the whole population this menu is judged against. */
  private val findingKinds = List(
    JdkSurfaceCheck.Disposition.Unhandled("java.util.Arrays"),
    JdkSurfaceCheck.Disposition.KeptIterable("java.util.List"),
    JdkSurfaceCheck.Disposition.StaleRefusal("java.util.Map$Entry#setValue"))

  test("the menu names THIS check's lane and the one kind it answers") {
    assertEquals(JdkSurfaceCheck.remedies.map(_.lane), List(JdkSurfaceCheck.Name))
    assertEquals(JdkSurfaceCheck.AcceptJdkMember.kind, "unhandled")
    // …read off the disposition rather than trusted as a literal: the two must be the same string
    // or the drain silently matches nothing.
    assertEquals(JdkSurfaceCheck.AcceptJdkMember.kind,
      JdkSurfaceCheck.Disposition.Unhandled("x").label)
    assert(!JdkSurfaceCheck.AcceptJdkMember.emissionAffecting)
    assertEquals(JdkSurfaceCheck.AcceptJdkMember.subject, Remedy.Subject.ExternalMember)
  }

  test("…and it is registered, and the whole check-side vocabulary still assembles") {
    val v = RemedyVocabulary.from(PortRun.CheckRemedies)
    assert(clue(PortRun.CheckRemedies.toSet).contains(JdkSurfaceCheck))
    assertEquals(v.ids.size, PortRun.CheckRemedies.map(_.remedies.size).sum)
    assert(clue(v).contains("accept-jdk-member"))
  }

  test("K9 and STALE-REFUSAL have no entry — one is a compile error, the other an engine bug") {
    // `kept-iterable` stands for an emitted `for (x <- xs)` that does not compile (noise4j's two
    // errors ARE these two rows), so an accept would state that an uncompilable emission is right.
    // `stale-refusal` says `Refusals` and a phase table contradict each other, which is a fact about
    // the engine and about no port. Asserted on the MENU, so it cannot be true by accident of which
    // rows a fixture produced.
    val answered = JdkSurfaceCheck.remedies.flatMap(_.kinds).toSet
    assert(!clue(answered).contains("kept-iterable"))
    assert(!clue(answered).contains("stale-refusal"))
  }

  test("the key a port writes is the SUBJECT COLUMN VERBATIM, and it binds at the external callee") {
    // The round trip: `java.util.Arrays#fill(float[],float)` is what `findings.tsv` prints and what
    // the manifest must accept. `Ownership.Owned` would refuse it as `ExternalOnly`.
    val p  = program
    val pl = plan(p, Map("java.util.Arrays#fill(float[],float)" -> "accept-jdk-member"))
    assertEquals(clue(pl.entries).map(_.target), List(Some(callee(p))))
    val kept = JdkSurfaceCheck.resolved(pl, findingKinds.map(row(_, callee(p))))
    assertEquals(clue(kept).map(_.disposition.label), List("kept-iterable", "stale-refusal"))
    assertEquals(pl.all.map(_.remedy.id), List("accept-jdk-member"))
    assertEquals(pl.all.map(_.drained), List(1))
  }

  test("…and a row with no symbol is unselectable, and an empty plan drains nothing") {
    val p    = program
    val pl   = plan(p, Map("java.util.Arrays#fill(float[],float)" -> "accept-jdk-member"))
    val rows = findingKinds.map(row(_, SymId.None))
    assertEquals(JdkSurfaceCheck.resolved(pl, rows).size, 3)
    assertEquals(pl.all, Nil)
    assertEquals(JdkSurfaceCheck.resolved(ResolutionPlan.empty, rows), rows)
  }
