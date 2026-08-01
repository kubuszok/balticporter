package balticporter.corpus

import balticporter.core.{PolicyIssue, PolicyReport}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, DecisionLog, PorterNote, Pipeline, Program}
import balticporter.transform.*

/** `ENGINE-LIMITS.md` CT7 — THE THIRD ANSWER, and the warning that makes its absence visible.
  *
  * A class a FRAMEWORK instantiates is neither of the two answers the attachment decision had. It is
  * not a boundary — its body genuinely needs the context — and it must not take the clause, because
  * a reflective construction cannot supply one. The measured cost of getting this wrong is the whole
  * reason CLAUDE.md §3 says what it says: the emitted file compiled at 0 scalac errors, every check
  * count was identical, `context-seam` was 0, and five tests stopped running.
  *
  * Two separable halves, and this spec pins both:
  *
  *   - the ANSWER — `selfSupplied` names the type, its constructors keep java's signature, and a
  *     `private given` member filled by the port's own expression is what its `summon`s resolve
  *     against. That is the reference hand port's shape, reached from policy rather than by hand;
  *   - the WARNING — a threaded class NOTHING IN THIS PROGRAM CONSTRUCTS whose ancestry leaves the
  *     program is the CT7 shape, observed rather than declared. It cannot refuse (a class this
  *     library's USERS construct looks identical from inside), so it counts.
  *
  * Every assertion here is negative-testable: revert the guard named in its comment and it fails.
  */
class GlobalsToContextFrameworkSpec extends munit.FunSuite:

  /** the CT7 shape beside its two controls: a threaded class this program DOES construct, and a
    * threaded class nothing constructs whose ancestry never leaves the program. */
  private val src =
    """package demo;
      |
      |public class Cfg { public static Svc svc; }
      |public class Svc { public int width() { return 0; } }
      |
      |public class Model { int w; public Model() { w = Cfg.svc.width(); } }
      |
      |public class ModelTest extends munit.FunSuite {
      |  void check() { Model m = new Model(); int w = Cfg.svc.width(); }
      |}
      |
      |public class Boot { void go() { Model m = new Model(); } }
      |public class Runner { void go() { Boot b = new Boot(); } }
      |""".stripMargin

  /** a self-supplied type whose PARENT took the clause — the one shape the third answer cannot
    * cover, because a `given` member is not in scope in an `extends` clause. */
  private val inheritedSrc =
    """package demo;
      |public class Cfg { public static Svc svc; }
      |public class Svc { public int width() { return 0; } }
      |public class Base { int w; public Base() { w = Cfg.svc.width(); } }
      |public class Child extends Base { }
      |""".stripMargin

  private def base = ContextHolder(
    holder  = "demo.Cfg",
    context = ContextType.Injected("demo.Ctx"),
    members = Map("svc" -> "svc"),
    attach  = ContextAttach.Class,
  )

  private def portedFrom(source: String, h: ContextHolder)
      : (GlobalsToImplicitsTransform, Program, DecisionLog, String) =
    val phase        = new GlobalsToImplicitsTransform(List(h))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(source, "Framework.java"), List(phase))
    (phase, after, log, new TirEmitter(after, notes = log).emit)

  private def ported(h: ContextHolder) = portedFrom(src, h)

  /** the emitted CODE with the porter notes stripped — a note names the UPSTREAM member on purpose
    * (§4.575). */
  private def code(out: String): String =
    out.linesIterator.filterNot(l => l.contains(PorterNote.Marker) || l.trim.startsWith("—")).mkString("\n")

  private def seams(p: GlobalsToImplicitsTransform, a: Program, k: ContextSeamCheck.Kind) =
    p.seams(a).filter(_.kind == k)

  private def render(p: GlobalsToImplicitsTransform, a: Program) = p.seams(a).map(_.render).mkString("\n")

  // -------------------------------------------------------------------------
  // the WARNING — the check CT7 lacked
  // -------------------------------------------------------------------------

  test("a threaded class NOTHING CONSTRUCTS whose ancestry leaves the program is WARNED on") {
    // the measured loss, reproduced in miniature: every step of the closure is right — `ModelTest`
    // constructs `Model`, `Model` is threaded, the instantiate edge threads `ModelTest`, the clause
    // lands on its constructor — and nothing in the program ever builds a `ModelTest`.
    // NEGATIVE: delete the `warnUnconstructed` call at the end of `ContextNeed.grow` and this is 0,
    // with every other number in the run unchanged, which is exactly what was measured.
    val (p, a, _, out) = ported(base)
    assert(clue(code(out)).contains("class ModelTest(using demo.Ctx)"), code(out))
    val ws = seams(p, a, ContextSeamCheck.Kind.UnconstructedThread)
    assertEquals(clue(ws).map(_.subject), List("demo.ModelTest"), render(p, a))
    assert(clue(ws.head.detail).contains("munit.FunSuite"), ws.head.render)
    assert(ws.head.detail.contains("selfSupplied"), ws.head.render)
    assert(ContextSeamCheck.Kind.classification(ContextSeamCheck.Kind.UnconstructedThread)
      .contains("§1(b)"))
  }

  test("…and it does NOT fire for a class the program constructs, nor for one rooted inside it") {
    // The two false positives that would make the warning noise rather than a finding. `Model` and
    // `Boot` are both constructed by something owned; `Runner` is constructed by nothing at all and
    // is still not this shape, because its ancestry never leaves the program.
    // NEGATIVE: drop the `java.lang.Object` exclusion and `Runner` and `Boot` join the list, which
    // is every threaded class in a port that has no framework at all.
    val (p, a, _, _) = ported(base)
    val warned = seams(p, a, ContextSeamCheck.Kind.UnconstructedThread).map(_.subject).toSet
    assertEquals(clue(warned), Set("demo.ModelTest"), render(p, a))
    // …and the control really is in the closure, or the assertion above proves nothing.
    val threaded = p.seams(a) // (sanity: the classes below are threaded, per the emitted text)
    assert(clue(threaded).ne(null))
    val (_, _, log, out) = ported(base)
    val cs = log.of(Decision.Kind.RetypedSignature).map(_.subjectFqn).toSet
    assert(clue(cs).contains("demo.Boot"), code(out))
    assert(cs.contains("demo.Runner"), code(out))
  }

  // -------------------------------------------------------------------------
  // the ANSWER — takes the value without taking a parameter
  // -------------------------------------------------------------------------

  private lazy val supplied = ported(base.copy(
    selfSupplied = Map("demo.ModelTest" -> "demo.TestFixture.ctx()")))

  test("a `selfSupplied` type takes NO clause and gets a `private given` at the head of its body") {
    // the reference hand port's shape, reached from policy: `private given Sge =
    // SgeTestFixture.testSge()` as a MEMBER of a suite class with a no-arg constructor.
    // NEGATIVE: remove the `selfSupplied` arm from `GlobalsToImplicitsTransform`'s
    // `transformClassDef` and the clause comes back — the suite compiles and cannot be instantiated.
    val (_, _, _, out) = supplied
    val c = code(out)
    assert(clue(c).contains("class ModelTest extends munit.FunSuite"), c)
    assert(!c.contains("class ModelTest(using"), c)
    assert(c.contains("private given demo.Ctx = demo.TestFixture.ctx()"), c)
    // …at the HEAD of the body: a class body is a constructor, and a statement that used the
    // context before the given was initialised would read `null`.
    val body = c.split("class ModelTest extends munit.FunSuite").last
    assert(clue(body).indexOf("private given") < body.indexOf("def check"), body)
  }

  test("its BODY still reads through the context — a self-supplied read is not a residual global") {
    // the half that makes this a resolution and not a refusal. `ReadPlan` asks `supplies`, not
    // `classes`.
    // NEGATIVE: restore `case Site.Cls(c, cap) if classes(c)` in `readPlan` and the read is left
    // naming `demo.Cfg` with a `residual-global-read` seam beside it — a global reintroduced by the
    // very entry that was meant to remove one.
    val (p, a, _, out) = supplied
    assert(clue(code(out)).contains("scala.Predef.summon[demo.Ctx].svc.width()"), code(out))
    assertEquals(clue(seams(p, a, ContextSeamCheck.Kind.ResidualGlobalRead)).map(_.subject), Nil,
      render(p, a))
  }

  test("the warning STOPS once the entry exists, and a `self-supplied` seam replaces it") {
    // The warning is a question; the entry is the answer, and a port that answered it must not keep
    // being asked. The count does not vanish — it MOVES, which is what makes the boundary sizeable.
    val (p, a, _, _) = supplied
    assertEquals(clue(seams(p, a, ContextSeamCheck.Kind.UnconstructedThread)).map(_.subject), Nil,
      render(p, a))
    val ss = seams(p, a, ContextSeamCheck.Kind.SelfSupplied)
    assertEquals(clue(ss).map(_.subject), List("demo.ModelTest"), render(p, a))
    assertEquals(ss.head.key, "demo.ModelTest")
    assert(clue(ss.head.detail).contains("framework-instantiated"), ss.head.render)
  }

  test("the decision says the SIGNATURE DID NOT MOVE, and the note sits above the class") {
    // `InjectedMember` and not `RetypedSignature`, because nothing was retyped: what the port
    // gained is a member the engine put there. §4.575 — the reader is an agent holding the emitted
    // file, and its question is asked at the `class` line.
    val (_, _, log, out) = supplied
    val ds = log.of(Decision.Kind.InjectedMember).filter(_.subjectFqn == "demo.ModelTest")
    assertEquals(clue(ds).size, 1)
    assertEquals(ds.head.detail.get("source"), Some("demo.TestFixture.ctx()"))
    assert(clue(ds.head.detail("why")).contains("reflective"))
    assertEquals(ds.head.reason, balticporter.tir.Reason.Configured("globals->implicits", "demo.ModelTest"))
    // no `RetypedSignature` row for it: the whole point is that the signature is java's.
    assert(!log.of(Decision.Kind.RetypedSignature).map(_.subjectFqn).contains("demo.ModelTest"))
    // the note is emitted, and it heads the unit whose `class` line the question is asked at —
    // nothing else is declared between it and `class ModelTest`.
    val lines = out.linesIterator.toList
    val at    = lines.indexWhere(_.contains("porter: injected-member"))
    assert(clue(at) >= 0, out)
    val after = lines.drop(at + 1)
    assertEquals(clue(after.find(l => l.startsWith("class ") || l.startsWith("object "))),
      Some("class ModelTest extends munit.FunSuite {"), out)
  }

  // -------------------------------------------------------------------------
  // the refusals — every one of them counted, none of them silent
  // -------------------------------------------------------------------------

  test("a self-supplied type whose PARENT took the clause is REFUSED, not emitted broken") {
    // A `given` member is in scope for the body and NOT in the `extends` clause: the parent's
    // constructor runs before this class's members exist, so the super call would have no argument
    // and nothing to build one from. There is no rewrite that repairs it here.
    // NEGATIVE: delete `checkSelfSupplied` and the port emits `class Child extends Base` against a
    // `class Base(using demo.Ctx)` — one scalac error, at a line no finding named.
    val (p, a, _, _) = portedFrom(inheritedSrc, base.copy(
      selfSupplied = Map("demo.Child" -> "demo.TestFixture.ctx()")))
    val ss = seams(p, a, ContextSeamCheck.Kind.SelfSupplied).filter(_.detail.contains("UNSATISFIED"))
    assertEquals(clue(ss).map(_.subject), List("demo.Child"), render(p, a))
    assert(clue(ss.head.detail).contains("demo.Base"), ss.head.render)
    val fs = p.policyReport.findings.filter(_.issue == PolicyIssue.Unverifiable)
    assert(clue(fs).exists(_.detail.contains("extends")), fs.toString)
  }

  test("an entry naming a type the closure never reaches is a DEAD BINDING, reported") {
    // `PolicyBinder.bindType` asks *does this program declare this type*, which a real class answers
    // whether or not the threading would ever have touched it — CT6's blindness, one key over.
    // NEGATIVE: delete `recordDeadSelf` and the entry binds, emits nothing, and is invisible.
    val (p, _, _, out) = ported(base.copy(selfSupplied = Map("demo.Svc" -> "demo.TestFixture.ctx()")))
    val fs = p.policyReport.findings.filter(_.issue == PolicyIssue.NeverMatched)
    assertEquals(clue(fs).map(_.key), List("demo.Svc"), fs.toString)
    assert(fs.head.detail.contains("never reached"), fs.head.detail)
    assert(!clue(code(out)).contains("private given"), code(out))
  }

  test("an entry with NO expression is MALFORMED — it would leave the body with no given at all") {
    // The one value that cannot mean anything: neither a clause nor a member, and every `summon` in
    // the body a compile error at a line the port never wrote. It is also refused BEFORE it takes
    // the type out of the threading, so one mistake cannot produce a second, worse one.
    val (p, _, _, out) = ported(base.copy(selfSupplied = Map("demo.ModelTest" -> "  ")))
    val fs = p.policyReport.findings.filter(_.issue == PolicyIssue.Malformed)
    assertEquals(clue(fs).map(_.key), List("demo.ModelTest"), fs.toString)
    // …and the type is still threaded, which is the pre-entry behaviour rather than a third one.
    assert(clue(code(out)).contains("class ModelTest(using demo.Ctx)"), code(out))
  }

  test("a `#` key is a MEMBER key and is refused as such — a different question, a different answer") {
    val (p, _, _, _) = ported(base.copy(selfSupplied = Map("demo.ModelTest#check" -> "demo.F.ctx()")))
    val fs = p.policyReport.findings.filter(_.key == "demo.ModelTest#check")
    assert(clue(fs).nonEmpty, p.policyReport.findings.toString)
    assert(fs.exists(_.detail.contains("MEMBER key")), fs.toString)
  }

  // -------------------------------------------------------------------------
  // the probe a real compiler reads (the M2 lesson: a claim about scalac needs scalac)
  // -------------------------------------------------------------------------

  /** {{{ scala-cli compile --scala 3.8.4 --server=false <the path printed below> }}} */
  test("emitted probe is written for a real compiler, ONE FILE PER UNIT as a port writes it") {
    val (_, after, l, _) = supplied
    val emitter = new TirEmitter(after, notes = l)
    val dir = java.nio.file.Path
      .of(sys.props.getOrElse("balticporter.dumpProbe", s"${sys.props("user.dir")}/target/probe"),
          "ct7-self-supplied")
    java.nio.file.Files.createDirectories(dir)
    // the port's OWN hand-written Scala: the context type, and the fixture that builds one. Neither
    // is anything the frontend ever saw, which is exactly the category the expression is in.
    java.nio.file.Files.writeString(dir.resolve("Ctx.scala"),
      "package demo\nfinal case class Ctx(svc: Svc)\nobject Ctx { var global: Ctx = null }\n" +
        "object TestFixture { def ctx(): Ctx = Ctx(new Svc) }\n")
    java.nio.file.Files.writeString(dir.resolve("FunSuite.scala"),
      "package munit\nclass FunSuite\n")
    after.units.foreach { u =>
      val nm = after.symbolOf(u.symbol).map(_.name).getOrElse("Unit")
      java.nio.file.Files.writeString(dir.resolve(s"$nm.scala"), emitter.emitUnit(u))
    }
    println(s"[ct7-probe] wrote ${dir.toAbsolutePath}")
  }
