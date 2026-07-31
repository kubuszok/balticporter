package balticporter.corpus

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.MethodBodyTransform

/** The body-substitution seam: keep the class mechanically translated, replace named method BODIES.
  *
  * Every assertion here is a property the seam is only useful if it has — the signature surviving
  * untouched is what keeps call sites type-checking, and the reporting of a key that matched
  * nothing is what stops a typo from silently leaving the original body in place (CLAUDE.md §3: a
  * check reporting zero is only as good as its coverage, so the misses are tested, not just the
  * hits).
  */
class MethodBodyTransformSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Box {
      |  private int n;
      |  public Box(int n) { this.n = n; }
      |  public <T> T make(Class<T> type) { return null; }
      |  public int plain() { return n; }
      |  public int over(int a) { return a; }
      |  public int over(String a) { return a.length(); }
      |}
      |""".stripMargin

  /** a class whose only interesting member is a STATIC INITIALISER — the shape a `<clinit>` key
    * addresses. Kept apart from `src` so the other tests' emitted text does not move. */
  private val withClinit =
    """package demo;
      |class Boot {
      |  static String mode;
      |  static { mode = Platform.detect().name(); }
      |}
      |""".stripMargin

  private def emit(policy: Map[String, String]) =
    val before = SpoonTir.fromSource(src)
    val phase  = new MethodBodyTransform(policy)
    val after  = Pipeline.run(before, List(phase))
    (phase, new TirEmitter(after).emit)

  /** the same, but keeping the DECISION LOG — a substituted body must also carry its porter note
    * (CLAUDE.md §4.575), and only the traced form can show that. */
  private def emitTraced(source: String, policy: Map[String, String]) =
    val before        = SpoonTir.fromSource(source)
    val phase         = new MethodBodyTransform(policy)
    val (after, log)  = Pipeline.runTraced(before, List(phase))
    val emitter       = new TirEmitter(after, notes = log)
    (phase, emitter.emit, emitter.notesPrinted)

  test("replaces the named body and leaves the SIGNATURE untouched") {
    // NB the backticks: the Java parameter is `type`, which is a Scala KEYWORD, so the emitter
    // renders it ``type``. The substituted text is spliced VERBATIM and the engine does not rewrite
    // identifiers inside it, so policy must name the parameter as EMITTED, not as Java spelled it.
    // Getting this wrong produces a body that does not compile — pinned here because it is the
    // trap this seam has, and it is invisible until the target compiler runs.
    val (phase, out) = emit(Map("demo.Box#make(Class)" -> "demo.Factory.create(`type`)"))
    assert(clue(out).contains("demo.Factory.create(`type`)"))
    assert(!out.contains("return null"))
    // ...and the generic signature is exactly what the mechanical translation produced. This is the
    // property that distinguishes this seam from `dropMethods` + inject: every call site still
    // resolves, and `RewriteTrace`'s signature check stays meaningful.
    assert(clue(out).contains("def make[T <: java.lang.Object](`type`: java.lang.Class[T]): T"))
    // the rest of the class is untouched
    assert(clue(out).contains("def plain()"))
    assertEquals(phase.substituted, List("demo.Box#make(Class)"))
  }

  test("an empty policy is a TOTAL no-op — byte-identical output, no findings") {
    val (phase, out) = emit(Map.empty)
    val plainOut     = new TirEmitter(SpoonTir.fromSource(src)).emit
    assertEquals(out, plainOut)
    assertEquals(phase.substituted, Nil)
    assertEquals(phase.policyReport.findings, Nil)
  }

  test("a key that matches NOTHING is reported — a typo must not silently keep the original body") {
    val (phase, out) = emit(Map("demo.Box#typoed(Class)" -> "???"))
    assertEquals(phase.substituted, Nil)
    val f = phase.policyReport.findings
    assertEquals(f.size, 1)
    assertEquals(f.head.issue, PolicyIssue.NeverMatched)
    assertEquals(f.head.key, "demo.Box#typoed(Class)")
    // and the mechanically translated body is still there, which is exactly why the report matters
    assert(clue(out).contains("return null") || out.contains("null"))
  }

  test("a CONSTRUCTOR key is REFUSED, not honoured — CtorFunnel reads constructor bodies") {
    val (phase, out) = emit(Map("demo.Box#<init>" -> "???"))
    assertEquals(phase.substituted, Nil)
    assertEquals(phase.policyReport.findings.map(_.issue), List(PolicyIssue.Malformed))
    assert(!out.contains("???"))
  }

  test("a PRIMITIVE parameter is keyed by its JAVA name, matching Substitutions.dropMethods") {
    // Pinned because it is the one part of the key convention that could plausibly go either way,
    // and because getting it wrong is mystifying: the key simply matches nothing and reports
    // `NeverMatched`, which reads like a typo. libGDX's own manifest already writes
    // `Array#<init>(boolean,int,Class)`, and this agrees with it.
    val (phase, out) = emit(Map("demo.Box#over(int)" -> "0"))
    assertEquals(phase.substituted, List("demo.Box#over(int)"))
    assertEquals(phase.policyReport.findings, Nil)
    assert(clue(out).contains("def over(a: scala.Int): scala.Int = 0"))
    assert(clue(out).contains("a.length()")) // the String overload untouched
  }

  test("the BARE key on an overloaded member reports Unverifiable — it gives every overload one body") {
    val (phase, _) = emit(Map("demo.Box#over" -> "0"))
    assertEquals(phase.substituted, List("demo.Box#over", "demo.Box#over"))
    assertEquals(phase.policyReport.findings.map(_.issue), List(PolicyIssue.Unverifiable))
  }

  test("a STATIC INITIALISER is addressable as `<clinit>`, and its substitution carries a NOTE") {
    // A `static { … }` block is where a library puts the one thing it does reflectively, or the one
    // branch that reaches a backend the port does not have — so it is exactly a body a port needs
    // to replace, and it is not a `def` in the emitted Scala (it renders as `locally { … }`).
    //
    // The second assertion is the one that found a real gap: the emitter's init-block arm rendered
    // the replaced body with NO `declNotes` call, so the decision shipped with no note beside the
    // code and `NoteCoverageCheck` failed the run for it. Nothing else can see that — the output
    // compiles perfectly either way and no other count moves.
    val (phase, out, printed) =
      emitTraced(withClinit, Map("demo.Boot#<clinit>" -> """{ demo.Boot.mode = "default" }"""))
    assertEquals(phase.substituted, List("demo.Boot#<clinit>"))
    assertEquals(phase.policyReport.findings, Nil)
    assert(clue(out).contains("""locally { demo.Boot.mode = "default" }"""))
    assert(!out.contains("Platform.detect"))
    assert(clue(out).contains("/* porter: substituted-body"))
    assertEquals(printed.map(_.kind.toString), List("SubstitutedBody"))
  }

  test("the PRECISE key selects ONE overload and leaves its twin mechanically translated") {
    val (phase, out) = emit(Map("demo.Box#over(String)" -> "-1"))
    assertEquals(phase.substituted, List("demo.Box#over(String)"))
    assertEquals(phase.policyReport.findings, Nil)
    assert(clue(out).contains("def over(a: java.lang.String): scala.Int = -1"))
    // the INT overload is the one that must survive mechanically — `a.length()` was the body of the
    // String overload, i.e. the one just replaced.
    assert(clue(out).contains("def over(a: scala.Int)"))
    assert(!out.contains("a.length()"))
  }
