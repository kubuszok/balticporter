package balticporter.testkit

import balticporter.core.Substitutions
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** THE RECOVERY BACKSTOP — the completeness half of comment preservation (`DESIGN.md` §8.8).
  *
  * The attachment channel places the overwhelming majority correctly and cannot be COMPLETE: a
  * construct the emission consumes takes its comments with it. What this asserts is that the
  * comment still reaches the file, that it says WHERE it came from, that it is not emitted twice,
  * and that a comment documenting a member the port deliberately drops is NOT put back.
  */
class RecoveredTriviaSpec extends munit.FunSuite:

  private def occurrences(hay: String, needle: String): Int =
    var n = 0; var i = hay.indexOf(needle)
    while i >= 0 do { n += 1; i = hay.indexOf(needle, i + 1) }
    n

  private def out(java: String): String = PortFixture.port(java).out

  private val multiCtor =
    """package demo;
      |public class Multi {
      |    private int w;
      |    private int h;
      |    public Multi() {
      |        this(1, 2);
      |        // defaulted both
      |    }
      |    public Multi(int w, int h) {
      |        this.w = w;
      |        this.h = h;
      |        // both given
      |    }
      |}
      |""".stripMargin

  test("a comment the funnel CONSUMED is recovered, once, with its java coordinates") {
    // the funnel splices the promoted constructor's statements into the class body, so its block —
    // and the `trailing` slot on it — is gone before anything renders.
    val o = out(multiCtor)
    assertEquals(occurrences(o, "both given"), 1, o)
    val marks = TriviaMark.scan(o)
    assertEquals(marks.size, 1, o)
    assertEquals(marks.head.line, 12, o)
    assert(marks.head.javaPath.endsWith("Snippet.java"), marks.head.javaPath)
    // the marker sits on its OWN line, directly above the comment it introduces — so the check's
    // normalisation still finds the comment's body rather than the engine's words about it.
    val ls = o.linesIterator.toList
    val at = ls.indexWhere(_.contains("both given"))
    assert(at > 0, o)
    assert(ls(at - 1).contains(TriviaMark.Marker), ls(at - 1))
  }

  test("the comment the attachment channel DID place is not recovered as well") {
    val o = out(multiCtor)
    assertEquals(occurrences(o, "defaulted both"), 1, o)
    assertEquals(TriviaMark.scan(o).count(_.line == 7), 0, o)
  }

  test("a `for` header comment — stripped by design — is recovered after its member") {
    val o = out(
      """package demo;
        |public class Loop {
        |    public int sum(int n) {
        |        int t = 0;
        |        for (int i = 0 /* start at zero */; i < n; i++) { t += i; }
        |        return t;
        |    }
        |}
        |""".stripMargin)
    assert(o.contains("start at zero"), o)
    assertEquals(occurrences(o, "start at zero"), 1, o)
  }

  test("a NESTING block comment recovered by the backstop still goes out line-by-line as `//`") {
    val o = out(
      """package demo;
        |public class Nest {
        |    private int w;
        |    public Nest() { this(1); /* see the /* marker */ }
        |    public Nest(int w) { this.w = w; }
        |}
        |""".stripMargin)
    assert(o.contains("see the"), o)
    val line = o.linesIterator.find(_.contains("see the /* marker")).getOrElse("")
    assert(line.trim.startsWith("//"), s"[$line] in:\n$o")
    // …and nothing in the emitted file can open a comment Scala never closes.
    val code  = o.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"")
    var depth = 0; var i = 0; var inLine = false
    while i < code.length do
      if inLine then { if code.charAt(i) == '\n' then inLine = false; i += 1 }
      else if code.startsWith("//", i) then { inLine = true; i += 2 }
      else if code.startsWith("/*", i) then { depth += 1; i += 2 }
      else if code.startsWith("*/", i) then { depth -= 1; i += 2 }
      else i += 1
    assertEquals(depth, 0, s"unbalanced block-comment delimiters in:\n$o")
  }

  test("no comment is emitted twice, and every one in the source reaches the file") {
    val java =
      """package demo;
        |public class All {
        |    /** the field */
        |    public int a;
        |    private int b;
        |    public All() {
        |        this(1);
        |        // the nilary one
        |    }
        |    /** the real one */
        |    public All(int b) {
        |        this.b = b;
        |        // done initialising
        |    }
        |    public void f() {
        |        // a leading statement comment
        |        int t = b;
        |        // and a trailing one
        |    }
        |}
        |""".stripMargin
    val o = out(java)
    List("the field", "the nilary one", "the real one", "done initialising",
         "a leading statement comment", "and a trailing one")
      .foreach(c => assertEquals(occurrences(o, c), 1, s"'$c' in:\n$o"))
    // and the check agrees, over TEXT: nothing lost.
    val r = TriviaCheck.check(List(TriviaCheck.Unit("Snippet.java", o)), Map.empty, _ => Some(java))
    assertEquals(r.lost, Nil, r.lost.map(_.render).mkString("\n"))
  }

  test("a DUPLICATED comment body is recovered once, not once per occurrence") {
    // the check cannot tell two identical bodies apart on the emitted side either, so recovering
    // one is what makes `lost` zero — recovering both would be a comment emitted twice.
    val java =
      """package demo;
        |public class Dup {
        |    private int w;
        |    public Dup() {
        |        this(1);
        |        // TODO
        |    }
        |    public Dup(int w) {
        |        this.w = w;
        |        // TODO
        |    }
        |}
        |""".stripMargin
    val o = out(java)
    assertEquals(occurrences(o, "// TODO"), 1, o)
    val r = TriviaCheck.check(List(TriviaCheck.Unit("Snippet.java", o)), Map.empty, _ => Some(java))
    assertEquals(r.lost, Nil, r.lost.map(_.render).mkString("\n"))
  }

  // ---- the DELIBERATE lane ----

  private val dropped =
    """package demo;
      |public class Policy {
      |    public int keep() { return 1; }
      |    /** Documentation of the member this port drops. */
      |    public int gone(java.lang.Class<?> c) { return 2; }
      |}
      |""".stripMargin

  test("a DROPPED member's javadoc is `deliberate` — not lost, and not interleaved back in") {
    val subs = Substitutions(dropMethods = Set("demo.Policy#gone"))
    val prog = SpoonTir.fromSource(dropped, subs = subs)
    val p    = Ported(prog, Pipeline.run(prog, Nil), Nil, Map("Snippet.java" -> dropped))
    val o    = p.out
    assert(!o.contains("def gone"), o)
    // the port does not have the member, so it must not carry documentation of it
    assert(!o.contains("Documentation of the member this port drops"), o)

    val members = CommentAnchor.membersOf(p.after)
    val r = TriviaCheck.check(List(TriviaCheck.Unit("Snippet.java", o)), members, _ => Some(dropped))
    assertEquals(r.lost, Nil, r.lost.map(_.render).mkString("\n"))
    assertEquals(r.deliberate.size, 1, r.deliberate.map(_.render).mkString("\n"))
    assert(r.deliberate.head.detail.contains("Documentation of the member"), r.deliberate.head.render)
  }

  test("without the member table nothing is `deliberate` — the honest degradation") {
    // over-reporting `lost` is visible; under-reporting it is not. A caller that cannot tell an
    // emitted member from a dropped one must therefore report the loss, not excuse it.
    val subs = Substitutions(dropMethods = Set("demo.Policy#gone"))
    val prog = SpoonTir.fromSource(dropped, subs = subs)
    val o    = Ported(prog, Pipeline.run(prog, Nil), Nil, Map("Snippet.java" -> dropped)).out
    val r    = TriviaCheck.check(List(TriviaCheck.Unit("Snippet.java", o)), Map.empty, _ => Some(dropped))
    assertEquals(r.deliberate, Nil)
    assertEquals(r.lost.size, 1, r.lost.map(_.render).mkString("\n"))
  }

  // ---- the marker's two contracts ----

  test("the marker is exempt from NOTE COVERAGE by SHAPE") {
    val text = "class C {\n  " + TriviaMark.render("com/example/A.java", 7) + "\n  // a comment\n}\n"
    assertEquals(PorterNote.scan(text), Nil, PorterNote.scan(text).toString)
  }

  test("the marker is STRIPPED by everything that searches emitted text for a string") {
    val text = "x " + TriviaMark.render("com/example/Dropped.java", 3) + " y"
    assert(!TriviaMark.stripAll(text).contains("Dropped"), TriviaMark.stripAll(text))
    assert(!balticporter.core.SubstitutionCheck.withoutPorterNotes(text).contains("Dropped"))
    // …and the two strippers are one function, so a porter note goes with it
    val both = TriviaMark.render("a/B.java", 1) + "\n/* porter: dropped-type reason=universal rule=r */\ncode"
    assertEquals(TriviaMark.stripAll(both).trim, "code")
  }

  test("each lane files its findings under ITS OWN check name") {
    // a finding carries the name it is filed against, so a lane that passed its own name to
    // `record` and left the name inside the finding alone filed every row under the other lane —
    // silently, with both counts plausible: `lost` read 12 and `deliberate` 0 on a run whose own
    // stdout said the reverse.
    val f = TriviaCheck.Finding("A.java", TriviaKind.Line, "// x", 1)
    assertEquals(f.report("trivia").check, "trivia")
    assertEquals(f.report("trivia(deliberate)").check, "trivia(deliberate)")
    assertEquals(TriviaCheck.Recovered("A.java", 1).report.check, "trivia(recovered)")
  }

  test("a value that could open a comment cannot reach the marker") {
    assert(!TriviaMark.render("weird/*path", 1).contains("/*path"), TriviaMark.render("weird/*path", 1))
  }
