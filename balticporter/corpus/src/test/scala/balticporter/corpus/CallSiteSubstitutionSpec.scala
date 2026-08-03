package balticporter.corpus

import balticporter.core.{PolicyIssue, Substitutions}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.{CallSiteSubstitutionTransform, PackageRenameTransform}

/** The call-site seam, end to end: keep the method mechanically translated, replace ONE call in it.
  *
  * The properties asserted here are the ones the seam is only useful if it has, and each of them is
  * a failure this repository has already paid for once at a different seam:
  *
  *   - '''overload exactness''' (CLAUDE.md §4.4's flagship). A key for `remove(Object)` that also
  *     rewrote `remove(int)` would produce a green compile and a different program.
  *   - '''the spliced arguments are TREES.''' A later phase — the package rename, which runs LAST
  *     (§4.56) — must reach them. Spliced as text they would be the one region of the program no
  *     phase can see.
  *   - '''a refusal is COUNTED''' rather than approximated, and a decision is recorded only for
  *     what was actually rewritten: a porter note claiming a substitution that did not happen is
  *     the one artifact a reader takes at face value.
  */
class CallSiteSubstitutionSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Bag {
      |  boolean remove(Object o) { return true; }
      |  Object remove(int i) { return null; }
      |}
      |class Codec {
      |  static String encode(Object o) { return ""; }
      |}
      |class Aux { boolean pass(Object o) { return true; } }
      |interface Pred { boolean test(Object o); }
      |class Store {
      |  Bag bag = new Bag();
      |  Pred asValue() { Aux a = new Aux(); return a::pass; }
      |  boolean byValue(Integer x) { return bag.remove(x); }
      |  Object byIndex() { return bag.remove(1); }
      |  String write(Object o) { return Codec.encode(o); }
      |  boolean twice(Integer a, Integer b) { return bag.remove(a) && bag.remove(b); }
      |}
      |""".stripMargin

  private def run(policy: Map[String, String]) =
    val phase = new CallSiteSubstitutionTransform(policy)
    val after = Pipeline.run(SpoonTir.fromSource(src), List(phase))
    (phase, new TirEmitter(after).emit)

  private def runTraced(policy: Map[String, String], extra: List[balticporter.tir.Phase] = Nil) =
    val phase        = new CallSiteSubstitutionTransform(policy)
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(src), phase :: extra)
    val emitter      = new TirEmitter(after, notes = log)
    (phase, emitter.emit, log, emitter.notesPrinted)

  // -------------------------------------------------------------------------
  // the rewrite
  // -------------------------------------------------------------------------

  test("the call is replaced by the template, with the receiver and the argument spliced in") {
    val (phase, out) = run(Map(
      "demo.Bag#remove(Object)" -> "demo.Support.removeValue({recv}, {arg0})"))
    assert(clue(out).contains("demo.Support.removeValue(this.bag, x)"))
    assertEquals(phase.substituted, List("demo.Bag#remove(Object)" -> 3))
    assertEquals(phase.policyReport.findings, Nil)
    // …and the SIGNATURE around it is exactly what the mechanical translation produced — this is
    // the property that distinguishes the seam from replacing the whole body.
    assert(clue(out).contains("def byValue(x: java.lang.Integer): scala.Boolean"))
  }

  test("OVERLOAD EXACTNESS: a key for remove(Object) does not touch remove(int) — §4.4's flagship") {
    val (phase, out) = run(Map(
      "demo.Bag#remove(Object)" -> "demo.Support.removeValue({recv}, {arg0})"))
    assert(clue(out).contains("demo.Support.removeValue(this.bag, x)"))
    // the by-INDEX overload is a different member and stays exactly as translated. `Symbol.fullName`
    // is the same string for both, so nothing but the descriptor could have told them apart.
    assert(clue(out).contains("this.bag.remove(1)"))
    // three sites name the Object overload; the int overload is not one of them
    assertEquals(phase.substituted, List("demo.Bag#remove(Object)" -> 3))
  }

  test("every site of the bound callee is rewritten, and the count is STATED") {
    val (phase, out) = run(Map("demo.Bag#remove(Object)" -> "demo.Support.rm({arg0})"))
    assertEquals(phase.substituted, List("demo.Bag#remove(Object)" -> 3))
    assert(clue(out).contains("demo.Support.rm(a) && demo.Support.rm(b)"))
  }

  test("an empty policy is a TOTAL no-op — byte-identical output, no findings, no decisions") {
    val (phase, out, log, notes) = runTraced(Map.empty)
    assertEquals(out, new TirEmitter(SpoonTir.fromSource(src)).emit)
    assertEquals(phase.substituted, Nil)
    assertEquals(phase.refusals, Nil)
    assertEquals(phase.policyReport.findings, Nil)
    assertEquals(log.all, Nil)
    assertEquals(notes, Nil)
  }

  // -------------------------------------------------------------------------
  // the holes are TREES
  // -------------------------------------------------------------------------

  test("a spliced argument is a TREE: the package rename, which runs LAST, still reaches it") {
    // The load-bearing property. `demo.Store` is renamed to `port.Store`, and the receiver spliced
    // into the template is a reference INSIDE the replaced expression — as text it would keep the
    // upstream namespace in a file that declares the new one, which is §4.56's failure exactly, and
    // it would compile nowhere and be reported by nothing.
    val (_, out, _, _) = runTraced(
      Map("demo.Bag#remove(Object)" -> "demo.Support.removeValue({recv}, {arg0})"),
      List(new PackageRenameTransform(Map("demo" -> "port"))))
    assert(clue(out).contains("package port"))
    assert(clue(out).contains("removeValue(this.bag, x)"))
    // the TEMPLATE text is the port's own and is spliced verbatim — it is written in the port's
    // FINAL namespace by its author, exactly as `MethodBodyTransform`'s bodies are.
    assert(clue(out).contains("demo.Support.removeValue"))
  }

  // -------------------------------------------------------------------------
  // the case the seam exists for: a DROPPED member that is still called
  // -------------------------------------------------------------------------

  /** the same shape as `src`, with the member the port is about to DROP and one caller of it. */
  private val dropped =
    """package demo;
      |class Bag {
      |  boolean remove(Object o) { return true; }
      |}
      |class Store {
      |  Bag bag = new Bag();
      |  boolean byValue(Integer x) { return bag.remove(x); }
      |}
      |""".stripMargin

  test("a call to a member `dropMethods` REMOVED is still rewritten — `ENGINE-LIMITS.md` D7") {
    // The case the whole seam exists for. The base drops a member; the dependent still calls it,
    // from inside a method that is otherwise entirely mechanical. Before this phase the port's only
    // two options were to replace the CALLER's whole body (forking it from upstream permanently) or
    // to drop the caller too (deleting the feature); the call site itself had no seam at all.
    //
    // A dropped member has no DECLARATION symbol, which is why the callee is bound through
    // `PolicyBinder.bindCallee` — the reference side, which the frontend interned anyway.
    val subs  = Substitutions(dropMethods = Set("demo.Bag#remove(Object)"))
    val phase = new CallSiteSubstitutionTransform(Map(
      "demo.Bag#remove(Object)" -> "demo.Support.rm({recv}, {arg0})"))
    val after = Pipeline.run(SpoonTir.fromSource(dropped, subs = subs), List(phase))
    val out   = new TirEmitter(after).emit
    assertEquals(phase.substituted, List("demo.Bag#remove(Object)" -> 1))
    assertEquals(phase.policyReport.findings, Nil)
    assert(clue(out).contains("demo.Support.rm(this.bag, x)"))
    // …and the member really is gone from the emitted class, which is what made the call dead
    assert(!out.contains("def remove"))
  }

  // -------------------------------------------------------------------------
  // policy faults — every one before the pipeline runs
  // -------------------------------------------------------------------------

  test("a template naming {argN} beyond the callee's arity is MALFORMED at BIND time") {
    val (phase, out) = run(Map("demo.Bag#remove(Object)" -> "f({arg0}, {arg3})"))
    assertEquals(phase.substituted, Nil)
    val f = phase.policyReport.findings
    assertEquals(f.map(_.issue), List(PolicyIssue.Malformed))
    assert(clue(f.head.detail).contains("{arg3}"))
    assert(clue(f.head.detail).contains("1 argument"))
    // and nothing was half-applied: the original call is still there
    assert(clue(out).contains("this.bag.remove(x)"))
  }

  test("a template naming {recv} on a STATIC callee is malformed — there is no receiver") {
    val (phase, out) = run(Map("demo.Codec#encode(Object)" -> "g({recv}, {arg0})"))
    assertEquals(phase.substituted, Nil)
    assertEquals(phase.policyReport.findings.map(_.issue), List(PolicyIssue.Malformed))
    assert(clue(out).contains("demo.Codec.encode(o)"))
  }

  test("an unparseable template is malformed and reported with the key, not silently emitted") {
    val (phase, out) = run(Map("demo.Bag#remove(Object)" -> "f({arg0)"))
    assertEquals(phase.substituted, Nil)
    val f = phase.policyReport.findings
    assertEquals(f.map(_.issue), List(PolicyIssue.Malformed))
    assertEquals(f.head.key, "demo.Bag#remove(Object)")
    assert(!out.contains("{arg0"))
  }

  test("a key that matches NOTHING is reported — a typo must not silently keep the call") {
    val (phase, out) = run(Map("demo.Bag#typoed(Object)" -> "f({arg0})"))
    assertEquals(phase.substituted, Nil)
    val f = phase.policyReport.findings
    assertEquals(f.map(_.issue), List(PolicyIssue.NeverMatched))
    assertEquals(f.head.key, "demo.Bag#typoed(Object)")
    assert(clue(out).contains("this.bag.remove(x)"))
  }

  test("a BARE key on an overloaded callee is AMBIGUOUS with the candidates, never one of them") {
    // DESIGN.md §8.1's asymmetry decided on purpose: bare stays legal for `dropMethods` and is
    // refused here, because a positional template can only be right for one arity.
    val (phase, out) = run(Map("demo.Bag#remove" -> "f({arg0})"))
    assertEquals(phase.substituted, Nil)
    val f = phase.policyReport.findings
    assertEquals(f.map(_.issue), List(PolicyIssue.Unverifiable))
    assert(clue(f.head.detail).contains("demo.Bag#remove(Object)"))
    assert(clue(f.head.detail).contains("demo.Bag#remove(int)"))
    assert(clue(out).contains("this.bag.remove(x)"))
  }

  test("a bare key naming exactly ONE overload binds — a parameter list it does not need") {
    val (phase, out) = run(Map("demo.Codec#encode" -> "demo.Support.enc({arg0})"))
    assertEquals(phase.substituted, List("demo.Codec#encode" -> 1))
    assertEquals(phase.policyReport.findings, Nil)
    assert(clue(out).contains("demo.Support.enc(o)"))
  }

  // -------------------------------------------------------------------------
  // provenance
  // -------------------------------------------------------------------------

  test("one DECISION per declaration, carrying the key and the site count, plus a porter note") {
    val (_, out, log, notes) = runTraced(Map("demo.Bag#remove(Object)" -> "demo.Support.rm({arg0})"))
    val ds = log.all.filter(_.kind == balticporter.tir.Decision.Kind.SubstitutedCall)
    // two DECLARATIONS use the callee (`byValue` once, `twice` twice) — never one row per site
    assertEquals(ds.map(_.subjectFqn).sorted, List("demo.Store#byValue", "demo.Store#twice"))
    assertEquals(ds.flatMap(_.detail.get("sites")).sorted, List("1", "2"))
    // the KEY is carried by the REASON and nowhere else — printed in the detail as well, a note
    // would say the same string twice and read as two facts
    assert(ds.forall(_.reason.detail.endsWith("demo.Bag#remove(Object)")))
    assert(!ds.exists(_.detail.contains("key")))
    assert(ds.forall(_.reason.className == "configured"))
    // …and each one is BESIDE the code, because a substituted call may have no resemblance to the
    // Java the source map points at (§4.575)
    assert(clue(out).contains("/* porter: substituted-call"))
    assertEquals(notes.map(_.kind.toString).distinct, List("SubstitutedCall"))
    assertEquals(notes.size, 2)
  }

  test("the callee used as a METHOD VALUE is REFUSED and counted, never silently left") {
    // `bag::remove` has no argument list, so a positional template has nothing to splice. Left
    // unreported it is a surviving reference to exactly the member the port declared it does not
    // call — a silence no compile, no check and no test can see.
    val (phase, out) = run(Map("demo.Aux#pass(Object)" -> "demo.Support.rm({arg0})"))
    val r = phase.refusals
    assertEquals(r.map(_._1), List("demo.Aux#pass(Object)"))
    assert(clue(r.head._2).contains("METHOD VALUE"))
    assert(clue(out).contains("a.pass"))              // the reference itself survives, as stated
    // …and it is a FINDING, filed under the same key the manifest carries
    val f = phase.policyReport.findings
    assertEquals(f.map(_.issue), List(PolicyIssue.Unverifiable))
    assertEquals(f.head.key, "demo.Aux#pass(Object)")
  }

  test("a REFUSED site records no decision and no note — only what was rewritten is claimed") {
    // `{recv}` against a static callee never installs, so nothing is rewritten and nothing is
    // decided; the finding is the whole record. The complement of the test above.
    val (phase, out, log, notes) = runTraced(Map("demo.Codec#encode(Object)" -> "g({recv})"))
    assertEquals(log.all, Nil)
    assertEquals(notes, Nil)
    assert(!out.contains("porter: substituted-call"))
    assert(phase.policyReport.findings.nonEmpty)
  }
