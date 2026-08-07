package balticporter.corpus

import balticporter.core.{ActionCache, RuntimePlan}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.runner.PortRun
import balticporter.tir.{Decision, DecisionLog, Pipeline}

/** EMISSION IS NOT A PURE FUNCTION OF THE UNIT, and the action cache stored only half of what it
  * produces.
  *
  * `Translated.sourceOf` memoises emitted TEXT through an [[ActionCache]], on the stated argument
  * that the text is a pure function of the unit, its dependencies' signatures and the engine. The
  * text is. Rendering also produces two things the text cannot carry:
  *
  *   - `Decision`s taken AT EMISSION — `WidenedSeal`, `ForcedClassInit`, preview's `Unrenderable` —
  *     which travel out through `TirEmitter.emissionDecisions` and into `decisions.tsv`;
  *   - the NOTE RECORDS `NoteCoverageCheck` joins the decisions against.
  *
  * A cache HIT returns the text without rendering, so both vanish — while the cached text still
  * CARRIES the porter note, because the note is characters in the file. That is `CLAUDE.md` §4.575's
  * exact defect (a note in the code with no decision behind it), and it is invisible to the check
  * built for it: `NoteCoverageCheck` compares the run's decisions against what the EMITTER recorded
  * printing, and on a hit both of those derive from the rendering that did not happen. The compile
  * is green, every count is flat, and `decisions.tsv` is one row short.
  *
  * The fix is a refusal rather than a replay, and the reason is identity: a `Decision` and a
  * `PorterNote.Printed` are keyed on a `SymId`, which is interning order and dies with the run, so a
  * record written by one run and replayed by another would join against the wrong symbols — and
  * re-resolving it by name is the join `NoteCoverageCheck`'s own doc calls empty on exactly the
  * decisions it exists for. A unit whose rendering recorded either is therefore never STORED, so no
  * such unit can ever be hit.
  *
  * Latent today — no port in this corpus sets `cache` — and `cache` is a documented `PortConfig`
  * key, so the first port to set one is the first to lose rows.
  */
class ActionCacheSideEffectSpec extends munit.FunSuite:

  /** a java `sealed` hierarchy whose permitted subtype lands in ANOTHER emitted file: scala's
    * `sealed` is FILE-scoped, so the seal cannot be reproduced and the emitter records a
    * `WidenedSeal` decision and prints its note WHILE RENDERING. The smallest real shape that
    * produces an emission-time decision at all. */
  private val sources = List(
    "A.java" -> "package p;\npublic sealed class A permits B { }\n",
    "B.java" -> "package p;\npublic final class B extends A { }\n",
  )

  private def translated(cache: ActionCache): PortRun.Translated =
    val program = Pipeline.run(SpoonTir.fromSources(sources), Nil)
    val log     = new DecisionLog
    val emitter = new TirEmitter(program, provenance = scala.None, notes = log)
    PortRun.Translated(program, RuntimePlan.none, emitter, program.units, Nil, Some(cache), log)

  private def run(cache: ActionCache): (List[String], List[Decision], Int, Int) =
    val t     = translated(cache)
    val texts = t.emitOrder.map(t.sourceOf)
    (texts, t.emitter.emissionDecisions, t.emitter.notesPrinted.size, t.cacheHits)

  test("a unit whose rendering RECORDED a decision is never stored, so a re-run reproduces it") {
    val dir   = java.nio.file.Files.createTempDirectory("bp-cache-spec")
    val cache = new ActionCache(dir, enabled = true)

    val (text1, decisions1, notes1, hits1) = run(cache)
    // the shape is real: rendering `p.A` widened its seal, recorded it, and printed the note.
    assertEquals(decisions1.map(_.kind), List(Decision.Kind.WidenedSeal))
    assertEquals(notes1, 1)
    assertEquals(hits1, 0, "nothing can be served from an empty cache")
    assert(text1.exists(_.contains("porter: widened-seal")), text1.mkString("\n"))

    // …and a SECOND run over the same program, against the same populated cache, must be
    // indistinguishable. Before the fix this ran green with `decisions2 == Nil` and `notes2 == 0`
    // while `text2 == text1` — the note in the file, and nothing behind it.
    val (text2, decisions2, notes2, _) = run(cache)
    assertEquals(text2, text1, "the cache's existing claim: byte-identical text")
    assertEquals(decisions2.map(_.tsv), decisions1.map(_.tsv),
      "a cached re-run must reproduce decisions.tsv byte-identically")
    assertEquals(notes2, notes1, "…and the note records NoteCoverageCheck joins them against")
  }

  test("a unit that records NOTHING is still cached — the refusal is scoped, not a kill switch") {
    val dir   = java.nio.file.Files.createTempDirectory("bp-cache-spec")
    val cache = new ActionCache(dir, enabled = true)
    val plain = List("C.java" -> "package p;\npublic class C { int x = 1; }\n",
                     "D.java" -> "package p;\npublic class D { }\n")

    def once(): (List[String], Int) =
      val program = Pipeline.run(SpoonTir.fromSources(plain), Nil)
      val log     = new DecisionLog
      val e       = new TirEmitter(program, provenance = scala.None, notes = log)
      val t       = PortRun.Translated(program, RuntimePlan.none, e, program.units, Nil, Some(cache), log)
      (t.emitOrder.map(t.sourceOf), t.cacheHits)

    val (text1, hits1) = once()
    assertEquals(hits1, 0)
    val (text2, hits2) = once()
    assertEquals(text2, text1)
    assert(hits2 > 0, s"a decision-free unit must still be served from the cache (hits = $hits2)")
  }

  test("re-emitting one unit does not DOUBLE its emission decisions") {
    // `emissionDecisions` documented this idempotence and the buffer was never cleared, while the
    // note records ARE cleared — so a second `emitUnit` left one note and two decisions for one
    // subject, and `NoteCoverageCheck`'s arithmetic became a function of how many times the emitter
    // had been called. `Determinism` and the cache both re-render units.
    val program = Pipeline.run(SpoonTir.fromSources(sources), Nil)
    val e       = new TirEmitter(program, provenance = scala.None, notes = new DecisionLog)
    program.units.foreach(e.emitUnit)
    val once = (e.emissionDecisions.map(_.tsv), e.notesPrinted.size)
    program.units.foreach(e.emitUnit)
    assertEquals((e.emissionDecisions.map(_.tsv), e.notesPrinted.size), once)
  }
