package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Correlate, Decision, DecisionLog, Pipeline, PorterNote, SrcMap}

/** E9 — PREVIEW MODE: say it in the OUTPUT, or refuse and count.
  *
  * `ENGINE-LIMITS.md` M6 is right for a port that ships: where the engine has no faithful Scala it
  * refuses, leaves a residue comment, and carries a NUMBER. It is wrong for the first week of a NEW
  * library, where the operator is an agent in another repository (CLAUDE.md §4.45) that has to find
  * the residue at all — and `/* break … */ ()` compiles perfectly.
  *
  * `preview = true` turns each such site into `scala.compiletime.error`. The port deliberately does
  * not compile, and every error carries the four things a residue comment does not: WHAT could not
  * be rendered, WHY, WHAT the agent must do, and the JAVA ORIGIN.
  *
  * Both halves are asserted, and the first is the one that matters most: with the flag OFF the
  * emitted text is EXACTLY what it was, character for character. A diagnostic mode that perturbs
  * the shipping emission is not a diagnostic mode.
  */
class PreviewModeSpec extends munit.FunSuite:

  /** a labelled `break` whose label the emitter cannot see — the residue shape, minted directly
    * rather than fished out of the corpus, which has zero of them (`break residue: 0`). */
  private val src =
    """package demo;
      |class Jumpy {
      |  int scan(int[] xs) {
      |    int n = 0;
      |    for (int x : xs) { n += x; }
      |    return n;
      |  }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)

  /** the emitter cannot be asked for a residue from Java that has none, so the node is built by
    * hand — which is also the honest test: a `Break` with a label nothing registered. */
  private def emitBreak(preview: Boolean): (String, List[Decision]) =
    val log = new DecisionLog
    val e   = new TirEmitter(program, provenance = scala.None, notes = log, preview = preview)
    // reach the term renderer through a public emission, with a hand-built node substituted in:
    // `TirEmitter` has no public term entry, so the probe goes through a UNIT whose body carries
    // the node. Built by rewriting the parsed method's body.
    val unit = program.units.head
    val out  = e.emitUnit(withStrayBreak(unit))
    (out, e.emissionDecisions)

  /** replace the method body with a stray labelled `break` — the one form neither the frontend nor
    * the emitter can translate. */
  private def withStrayBreak(cd: balticporter.tir.Tree.ClassDef): balticporter.tir.Tree.ClassDef =
    import balticporter.tir.*
    val stray = Tree.Break(Some("outer"), TypeRepr.NoType, Origin("demo/Jumpy.java", 3, 5))
    cd.copy(body = cd.body.map {
      case d: Tree.DefDef if d.rhs.isDefined && !d.paramss.flatten.isEmpty => d.copy(rhs = Some(stray))
      case other                                                            => other
    })

  test("OFF (the shipping default): the residue comment M6 counts, and the port compiles") {
    val (out, ds) = emitBreak(preview = false)
    assert(clue(out).contains("/* break outer: label not in scope */ ()"))
    assert(!out.contains("scala.compiletime.error"), out)
    assertEquals(ds, Nil)
  }

  test("ON: `scala.compiletime.error` carrying construct, reason, ACTION and java origin") {
    val (out, _) = emitBreak(preview = true)
    assert(clue(out).contains("scala.compiletime.error("), out)
    val msg = out.substring(out.indexOf("scala.compiletime.error("))
    assert(msg.contains("balticporter: break:"), msg)                       // construct
    assert(msg.contains("label is not in scope"), msg)                      // why
    assert(msg.contains("NAMED boundary"), msg)                             // what to do
    assert(msg.contains("origin demo/Jumpy.java:3"), msg)                   // where it came from
    // …and the residue comment is GONE: the two are alternatives, not layers.
    assert(!out.contains("/* break outer: label not in scope */"), out)
  }

  test("ON: an `Unrenderable` decision is recorded, with a porter note beside the error") {
    val (out, ds) = emitBreak(preview = true)
    assertEquals(ds.map(_.kind), List(Decision.Kind.Unrenderable))
    assertEquals(ds.head.detail("construct"), "break")
    assert(ds.head.detail("action").nonEmpty)
    assertEquals(ds.head.origin.javaPath, "demo/Jumpy.java")
    // the note makes the error DERIVED rather than something the emitter wrote from a local
    // condition — which is what `NoteCoverageCheck` holds every note to.
    assertEquals(PorterNote.scan(out).map(_.kind), List(Some(Decision.Kind.Unrenderable)))
  }

  test("the count goes to its OWN lane — a declared refusal is not an engine gap") {
    val err = Correlate.ScalacError(
      "E???", "Error", "src_managed/main/scala/demo/Jumpy.scala", 4, 5,
      "balticporter: break: labelled `break outer` whose label is not in scope; …; origin demo/Jumpy.java:3")
    val real = err.copy(message = "not found: value wibble")
    val out  = Correlate.locateErrors(List(err, real), SrcMap.Index.empty)
    assertEquals(out.map(_.lane), List(Correlate.Lane.Declared, Correlate.Lane.Unmapped))
  }
