package balticporter.testkit

import balticporter.core.{RuntimeMode, RuntimePlan}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Phase, Pipeline, Program, SymId}

/** One Java snippet taken through the pipeline, with everything a test wants to assert on.
  *
  * `before` and `after` are both kept because half the useful assertions are about the XREF —
  * "the old symbol is vacated, the new one inherited its positions" — and those need the two
  * programs side by side. `out` is the emitted Scala.
  */
/** @param sources the java the fixture parsed, `fileName -> code`. Handed to the emitter as its
  *   `javaSource`, because an in-memory snippet's `Origin.javaPath` names a file that does not
  *   exist — without it the comment-recovery backstop is the one behaviour no spec could reach,
  *   which is exactly the shape a fixture must not have. */
final case class Ported(before: Program, after: Program, phases: List[Phase],
                        sources: Map[String, String] = Map.empty):

  /** what the phases that ran require of `balticporter-runtime`. Derived, not passed: the fixture
    * is a miniature of the orchestrator, so a test exercises the same derivation a real port does
    * rather than a hand-assembled approximation of it. */
  lazy val plan: RuntimePlan = RuntimePlan.of(phases, RuntimeMode.Dependency)

  lazy val emitter: TirEmitter =
    new TirEmitter(after, plan.concreteMembers,
                   javaSource = path => sources.collectFirst { case (n, c) if path.endsWith(n) => c })

  /** the emitted Scala for the whole program. */
  lazy val out: String = emitter.emit

  /** the symbol id for a fully-qualified name, in whichever program. */
  def idIn(p: Program, fullName: String): Option[SymId] = p.symbols.all.find(_.fullName == fullName).map(_.id)
  def idBefore(fullName: String): Option[SymId]         = idIn(before, fullName)
  def idAfter(fullName: String): Option[SymId]          = idIn(after, fullName)

/** Run Java source through phases and get the emitted Scala back.
  *
  * This is the four lines every engine spec opened with — parse, run, emit, look a symbol up by
  * name — and the reason it is a MODULE rather than a copied preamble is CLAUDE.md §4.45: the
  * consumer is an agent in another repository, writing tests for its own library's phases without
  * this repository's context. What it must not have to reinvent is how to hold the engine.
  */
object PortFixture:

  /** parse `java`, run `phases` over it, and hand back both programs plus the emitted Scala.
    * With no phases this is the emitter's own identity fixture — useful, and the honest baseline
    * for "did my phase change anything". */
  def port(java: String, phases: Phase*): Ported =
    val before = SpoonTir.fromSource(java)
    Ported(before, Pipeline.run(before, phases.toList), phases.toList, Map("Snippet.java" -> java))

  /** the same over SEVERAL compilation units, each `fileName -> code`. A Java file declares exactly
    * one package, so every rule about a PACKAGE BOUNDARY — default access, `protected`, an override
    * that crosses one — is untestable from a single snippet. */
  def portAll(sources: List[(String, String)], phases: Phase*): Ported =
    val before = SpoonTir.fromSources(sources)
    Ported(before, Pipeline.run(before, phases.toList), phases.toList, sources.toMap)

  /** parse only — for tests about the FRONTEND rather than about a phase. */
  def parse(java: String): Program = SpoonTir.fromSource(java)

/** MUnit base class for suites that port a snippet and assert on the result.
  *
  * Extends `munit.FunSuite` rather than offering a mixin because that is how the corpus specs are
  * written and there is no second base to compose with. The assertions carry `munit.Location` so a
  * failure points at the caller's line, not at this file.
  */
abstract class PortSuite extends munit.FunSuite:

  def port(java: String, phases: Phase*): Ported = PortFixture.port(java, phases*)

  def portAll(sources: List[(String, String)], phases: Phase*): Ported = PortFixture.portAll(sources, phases*)

  /** the emitted Scala contains `snippet` — with the WHOLE output in the failure message, because
    * "expected substring not found" without the text is the single most expensive failure mode
    * when the emitter is what changed. */
  def assertEmits(p: Ported, snippet: String)(using munit.Location): Unit =
    if !p.out.contains(snippet) then
      fail(s"emitted Scala does not contain:\n  $snippet\n--- emitted ---\n${p.out}\n---------------")

  def assertNotEmits(p: Ported, snippet: String)(using munit.Location): Unit =
    if p.out.contains(snippet) then
      fail(s"emitted Scala still contains:\n  $snippet\n--- emitted ---\n${p.out}\n---------------")

  def assertEmitsMatch(p: Ported, regex: String)(using munit.Location): Unit =
    if !p.out.matches(s"(?s).*$regex.*") then
      fail(s"emitted Scala does not match:\n  $regex\n--- emitted ---\n${p.out}\n---------------")

  /** a type that the phase was supposed to retype AWAY is no longer referenced anywhere.
    *
    * The xref form of the assertion, not the textual one: a name can vanish from the output
    * because the emitter stopped printing it, which is a different (and worse) fact than the
    * symbol having no usages left. */
  def assertVacated(p: Ported, fullName: String)(using munit.Location): Unit =
    val id = p.idBefore(fullName).getOrElse(fail(s"$fullName is not in the pre-phase symbol table"))
    assert(p.before.usagesOf(id).nonEmpty, s"$fullName had no usages before the phase — the fixture proves nothing")
    assertEquals(p.after.usagesOf(id), Nil, s"$fullName is still used after the phase")
