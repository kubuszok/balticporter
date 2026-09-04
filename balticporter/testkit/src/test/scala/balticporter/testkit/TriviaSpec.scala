package balticporter.testkit

import balticporter.tir.*

/** The comment-preservation spec, end to end: Java source → `SpoonTir` → `Pipeline` (with a phase
  * that really rewrites, to prove pass-through) → `TirEmitter`. */
class TriviaSpec extends munit.FunSuite:

  private val java =
    """/*
      | * Copyright 2011 Example Foundation
      | *
      | * Licensed under the Apache License, Version 2.0 (the "License");
      | * you may not use this file except in compliance with the License.
      | */
      |
      |package demo;
      |
      |/** The probe class.
      |  * <p>Second paragraph of the class doc.</p>
      |  */
      |public class Probe {
      |
      |    /** How many things there are. */
      |    public int count = 3;
      |
      |    // a plain line comment on a field
      |    public int other;
      |
      |    /**
      |     * Adds one to everything.
      |     * @param n how much to add
      |     */
      |    public int add(int n) {
      |        // leading comment on a statement
      |        int total = count + n;
      |        if (n > 0) {
      |            // a comment on a NESTED statement
      |            total = total + 1;
      |        }
      |        total = total + compute(/* hoisted out of an expression */ n);
      |        return total;
      |    }
      |
      |    private int compute(int n) { return n; }
      |
      |    /* a block comment that contains /* an opener Scala would nest on */
      |    public void tricky() {
      |        java.lang.System.out.println("*/ is not a delimiter in a string");
      |    }
      |}
      |""".stripMargin

  /** A phase that really rewrites a term: every `3` becomes `4`. If `StandardTraversal` unwrapped
    * `Tree.Commented` instead of rebuilding it, the literal would still change and every comment
    * inside a body would disappear — so this is what makes "the comments survived" mean something. */
  private val bumpThrees = new Phase:
    def name = "trivia-spec/bump-threes"
    override def transformTerm(t: Term)(using Program): Term = t match
      case l @ Tree.Literal(Constant.IntC(3), _, _) => l.copy(const = Constant.IntC(4))
      case other                                    => other

  private lazy val ported = PortFixture.port(java, bumpThrees)
  private lazy val out    = ported.out

  private def occurrences(hay: String, needle: String): Int =
    if needle.isEmpty then 0
    else
      var n = 0; var i = hay.indexOf(needle)
      while i >= 0 do { n += 1; i = hay.indexOf(needle, i + 1) }
      n

  // ---- the phase really ran (so "survived" is a claim about a rewritten tree) ----

  test("the phase rewrote the tree the comments ride on") {
    assert(out.contains("= 4"), out)
    assert(!out.contains("= 3"), out)
  }

  // ---- placement ----

  test("the file's licence header is emitted VERBATIM, above the package clause") {
    // the WHOLE block, byte for byte — gutter, blank line and delimiters included. A normalising
    // re-print passes a `contains` on the wording and fails this.
    val notice =
      """/*
        | * Copyright 2011 Example Foundation
        | *
        | * Licensed under the Apache License, Version 2.0 (the "License");
        | * you may not use this file except in compliance with the License.
        | */""".stripMargin
    assert(out.contains(notice), out)
    assert(out.indexOf(notice) < out.indexOf("package demo"), out)
  }

  test("a class Javadoc is emitted VERBATIM, above the class") {
    // Verbatim TEXT; the leading INDENT is the one thing re-derived, so the comment sits at the
    // node's depth rather than at the column Java happened to use (`TirEmitter.triviaText`). The
    // source wrote a two-space gutter at top level; here it reads as one space at indent 0.
    val doc =
      """/** The probe class.
        | * <p>Second paragraph of the class doc.</p>
        | */""".stripMargin
    assert(out.contains(doc), out)
    assert(out.indexOf("The probe class.") < out.indexOf("class Probe"), out)
  }

  test("a Javadoc's own indentation survives inside a nested member") {
    // the method's Javadoc is re-indented to the METHOD (two spaces), and its internal alignment
    // — the ` * ` gutter and the tab before `how much to add` — is carried across unchanged.
    val doc =
      """  /**
        |   * Adds one to everything.
        |   * @param n how much to add
        |   */""".stripMargin
    assert(out.contains(doc), out)
  }

  test("a field Javadoc and a field line comment each sit above their field") {
    assert(out.indexOf("How many things there are.") < out.indexOf("count"), out)
    assert(out.contains("// a plain line comment on a field"), out)
    assert(out.indexOf("a plain line comment on a field") < out.indexOf("other"), out)
  }

  test("a method Javadoc sits above the method, tags included") {
    assert(out.contains("Adds one to everything."), out)
    assert(out.contains("@param n how much to add"), out)
    assert(out.indexOf("Adds one to everything.") < out.indexOf("def add"), out)
  }

  test("a statement comment sits above its statement") {
    val c = out.indexOf("// leading comment on a statement")
    assert(c >= 0, out)
    assert(c < out.indexOf("total"), out)
  }

  test("an expression comment HOISTS to the statement it was written in") {
    val c = out.indexOf("hoisted out of an expression")
    assert(c >= 0, out)
    // …the statement it was written in, not the method's Javadoc and not the `return`
    assert(c > out.indexOf("Adds one to everything."), out)
    assert(c < out.indexOf("return total"), out)
  }

  // ---- the claimed-set property: every comment EXACTLY once ----

  test("a nested statement's comment is emitted once, not once per enclosing harvest point") {
    assertEquals(occurrences(out, "a comment on a NESTED statement"), 1, out)
  }

  test("no comment in the source is emitted twice") {
    val each = List(
      "Copyright 2011 Example Foundation",
      "The probe class.",
      "How many things there are.",
      "a plain line comment on a field",
      "Adds one to everything.",
      "leading comment on a statement",
      "a comment on a NESTED statement",
      "hoisted out of an expression",
    )
    each.foreach(c => assertEquals(occurrences(out, c), 1, s"'$c' in:\n$out"))
  }

  // ---- syntactic safety ----

  test("a block comment Scala would nest on is re-emitted as line comments, whole") {
    // every character of the original survives — including its `/*` and `*/` — but nothing in the
    // emitted form can open a comment Scala never closes.
    assert(out.contains("an opener Scala would nest on"), out)
    val line = out.linesIterator.find(_.contains("an opener Scala would nest on")).getOrElse("")
    assert(line.trim.startsWith("//"), line)
  }

  test("emitted comment delimiters are balanced for Scala's NESTING block comments") {
    // strip string literals first: `"*/ is not a delimiter in a string"` is code, not a comment.
    val code  = out.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"")
    var depth = 0
    var i     = 0
    var inLine = false
    while i < code.length do
      if inLine then
        if code.charAt(i) == '\n' then inLine = false
        i += 1
      else if code.startsWith("//", i) then { inLine = true; i += 2 }
      else if code.startsWith("/*", i) then { depth += 1; i += 2 }
      else if code.startsWith("*/", i) then { depth -= 1; i += 2 }
      else i += 1
    assertEquals(depth, 0, s"unbalanced block-comment delimiters in:\n$out")
  }

  // ---- printer contract ----

  test("canonical elides trivia; the identity form carries it") {
    given Program = ported.after
    val unit      = ported.after.units.head
    val canonical = TirPrinter.canonical(unit)
    val identity  = TirPrinter.render(unit, TirPrinter.Style.identity)
    assert(!canonical.contains("Adds one to everything."), canonical)
    assert(!canonical.contains("Commented"), canonical)
    assert(identity.contains("Adds one to everything."), identity)
    assert(identity.contains("Commented"), identity)
    // …and the digest is taken over the form that carries them, or a comment-only source edit
    // would be a cache HIT that re-serves the previous file (`TirCacheKey`).
    assertNotEquals(TirPrinter.digest(unit), TirPrinter.sha256(canonical))
  }

  // ---- the no-comment case is byte-identical to the pre-trivia world ----

  /** The emitted probe, written to this module's own `target/trivia-probe/` on every run, so an
    * operator can put a real compiler over it (`scala-cli compile --server=false
    * testkit/target/trivia-probe`). "Comments cannot break syntax" is a claim about a PARSER, and
    * the specs above are string assertions; this is how the claim gets checked by the only
    * authority on it. */
  test("emitted probe is written for a real compiler") {
    val p = _root_.java.nio.file.Path.of("target", "trivia-probe", "Probe.scala")
    _root_.java.nio.file.Files.createDirectories(p.getParent)
    _root_.java.nio.file.Files.writeString(p, out)
    assert(_root_.java.nio.file.Files.size(p) > 0)
  }

  test("a source with no comments mints no Commented node and no leading trivia") {
    val bare = PortFixture.parse("package demo; public class Bare { public int f(int n) { int t = n; return t; } }")
    given Program = bare
    var wrapped   = 0
    val scan = new Phase:
      def name = "trivia-spec/count-wrappers"
      override def transformTerm(t: Term)(using Program): Term =
        t match { case _: Tree.Commented => wrapped += 1; case _ => () }
        t
    bare.units.foreach(u => StandardTraversal.mapClassDef(scan, u))
    assertEquals(wrapped, 0)
    assertEquals(bare.units.head.leading, Nil)
    assertEquals(bare.units.head.unitLeading, Nil)
  }
