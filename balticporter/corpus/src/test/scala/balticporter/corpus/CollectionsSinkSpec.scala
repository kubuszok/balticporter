package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, DecisionLog, Pipeline, Program}
import balticporter.transform.{CollectionBoundaryCheck, CollectionsTransform}

/** THE OTHER END OF THE CARRIER'S CALL — a value this phase retyped, handed to external code that
  * reads its RUNTIME REPRESENTATION (`ENGINE-LIMITS.md` K21 face 1). */
class CollectionsSinkSpec extends PortSuite:

  private val Sink = "java.lang.StringBuilder"

  private def ported(source: String, sinks: Set[String] = Set(Sink))
      : (DecisionLog, Program, CollectionsTransform, String) =
    val ph             = new CollectionsTransform(reflectiveSinks = sinks)
    val (after, notes) = Pipeline.runTraced(SpoonTir.fromSource(source), List(ph))
    (notes, after, ph, new TirEmitter(after, notes = notes).emit)

  private def egress(ph: CollectionsTransform, p: Program): List[String] =
    ph.boundary(p).filter(_.issue == CollectionBoundaryCheck.Issue.OpaqueEgress).map(_.slot)

  /** liqp's `Json.apply` and `Template.putStringKey` at the size a spec can hold: one argument whose
    * static type this phase MOVED, and one whose static type is `Object` and says nothing. */
  private val Shape =
    """package demo;
      |import java.util.HashMap;
      |import java.util.Map;
      |class T {
      |  static String known() {
      |    Map<String, Object> m = new HashMap<String, Object>();
      |    StringBuilder sb = new StringBuilder();
      |    sb.append(m);
      |    return sb.toString();
      |  }
      |  static String opaque(Object value) {
      |    StringBuilder sb = new StringBuilder();
      |    sb.append(value);
      |    return sb.toString();
      |  }
      |}
      |""".stripMargin

  // -------------------------------------------------------------------------
  // 1. the bridge
  // -------------------------------------------------------------------------

  test("a declared sink's `Object` argument is bridged — with the value's static type KNOWN") {
    val (_, _, _, out) = ported(Shape)
    assert(clue(out).contains("sb.append(balticporter.runtime.JavaCollections.Reified.toJavaValue(m))"),
           "`toJava` is one level and this callee walks the whole tree, so the deep bridge wins here")
  }

  test("…and with the value's static type `Object`, which is the case nothing could see") {
    val (_, _, _, out) = ported(Shape)
    assert(clue(out).contains("sb.append(balticporter.runtime.JavaCollections.Reified.toJavaValue(value))"),
           "the port's data model holds both representations at an `Object` slot (K18), so the " +
             "question is asked of the OBJECT — the helper is identity for everything else")
  }

  test("a NON-opaque formal at the same sink is left alone — the bridge is about the SLOT") {
    val (_, _, _, out) = ported(
      """package demo;
        |class T {
        |  static String s(String x) { StringBuilder sb = new StringBuilder(); sb.append(x); return sb.toString(); }
        |}
        |""".stripMargin)
    assert(!clue(out).contains("toJavaValue"),
           "`append(String)` is a typed slot; nothing about it can be a retyped collection")
  }

  // -------------------------------------------------------------------------
  // 2-3. the no-op, and what stands in its place
  // -------------------------------------------------------------------------

  test("an UNDECLARED sink bridges nothing — an empty set is the no-op, with no code path") {
    val (_, _, _, out) = ported(Shape, sinks = Set.empty)
    assert(!clue(out).contains("toJavaValue"))
    assert(out.contains("sb.append(balticporter.runtime.JavaCollections.toJava(m))"),
           "the pre-K21 emission: a STATICALLY known collection at a universal formal already had " +
             "the one-level `toJava`, which is right for a callee that does not walk the value")
    assert(out.contains("sb.append(value)"),
           "and an `Object`-typed argument had nothing at all — the seam K21 is about")
  }

  test("…and is COUNTED instead, once per CALLEE, which is the review list a port reads") {
    val (_, after, ph, _) = ported(Shape, sinks = Set.empty)
    val rows = egress(ph, after)
    assertEquals(clue(rows).size, 1,
                 "the question is per METHOD — `does this external method read what I hand it?` — " +
                   "and one row per call site would bury it")
    assert(rows.head.contains("java.lang.StringBuilder"), clue(rows))
  }

  test("…and a DECLARED sink is bridged rather than counted — a closed seam is not a residue") {
    val (_, after, ph, _) = ported(Shape)
    assertEquals(clue(egress(ph, after)), Nil)
  }

  // -------------------------------------------------------------------------
  // 4. what the phase can prove it never touched
  // -------------------------------------------------------------------------

  test("an argument the phase can prove is not its own is neither bridged nor counted") {
    val (_, after, ph, out) = ported(
      """package demo;
        |class T {
        |  static String s() { StringBuilder sb = new StringBuilder(); sb.append("x"); return sb.toString(); }
        |}
        |""".stripMargin, sinks = Set.empty)
    assert(!clue(out).contains("toJavaValue"))
    assertEquals(clue(egress(ph, after)), Nil,
                 "a `String` at an `Object` formal is provably not a representation this engine " +
                   "introduced; counting it would drown the rows that are")
  }

  // -------------------------------------------------------------------------
  // 4b. the D2 filter, which the DEDUP has to survive
  // -------------------------------------------------------------------------

  test("a DEPENDENT's row survives a BASE that reaches the same callee at a smaller (path, line)") {
    // "One row per callee" is right, and the site kept for it is a REPORTING detail that the D2
    // filter then reads as if it were the whole population. Keep ONE origin per callee across the
    // whole program — base units included — and `boundary(units)` drops the row whenever the
    // surviving origin is in a file this module does not emit: the dependent has the seam, has no
    // row, and nothing anywhere says so. `Base.java` sorts before `Dep.
    val ph = new CollectionsTransform(reflectiveSinks = Set.empty)
    val (after, _) = Pipeline.runTraced(SpoonTir.fromSources(List(
      "Base.java" ->
        """package demo;
          |class Base { static void b(Object v) { StringBuilder sb = new StringBuilder(); sb.append(v); } }
          |""".stripMargin,
      "Dep.java" ->
        """package demo;
          |class Dep { static void d(Object v) { StringBuilder sb = new StringBuilder(); sb.append(v); } }
          |""".stripMargin)), List(ph))
    def unit(n: String) = after.units.filter(u => after.symbolOf(u.symbol).exists(_.fullName == n))
    def egressOf(us: List[balticporter.tir.Tree.ClassDef]) =
      ph.boundary(after, us).filter(_.issue == CollectionBoundaryCheck.Issue.OpaqueEgress)
    assertEquals(clue(egressOf(unit("demo.Base"))).size, 1)
    assertEquals(clue(egressOf(unit("demo.Dep"))).size, 1,
                 "the dependent has the same seam and must have its own row; a dedup that keeps " +
                   "one global origin makes this zero, silently")
    assertEquals(egressOf(unit("demo.Dep")).head.origin.javaPath.endsWith("Dep.java"), true,
                 "and the row it gets must be a site THIS module emits, or the finding points at " +
                   "a file its reader does not have")
    assertEquals(clue(egressOf(after.units)).size, 1,
                 "…while the whole program still reports ONE row per callee — the dedup's own claim")
  }

  // -------------------------------------------------------------------------
  // 5. the record
  // -------------------------------------------------------------------------

  test("the bridge is RECORDED per declaration, with the entry an agent would edit") {
    val (log, _, _, _) = ported(Shape)
    val rows = log.all.filter(_.kind == Decision.Kind.BridgedEgress)
    assert(clue(rows.map(_.render)).nonEmpty)
    assertEquals(rows.map(_.reason.className).distinct, List("configured"))
    assert(rows.forall(_.reason.detail.contains(Sink)),
           "the key is the manifest entry VERBATIM — the string an agent edits (§4.575)")
    assert(rows.exists(_.subjectFqn.endsWith("known")) && rows.exists(_.subjectFqn.endsWith("opaque")),
           "one row per DECLARATION the bridge reached, never one per site (§5.1)")
  }
