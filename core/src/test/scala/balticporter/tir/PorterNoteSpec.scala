package balticporter.tir

/** The PORTER NOTE grammar and the check that holds the emitter to it.
  *
  * Both directions are tested, because both have already been wrong once: the first run of
  * [[NoteCoverageCheck]] reported 594 notes as unbacked, on a corpus where every one of them was
  * derived — the value `key="com.badlogic.gdx -> sge"` contains a space, the pair list is
  * whitespace-separated, and both sides were reading a truncated value neither had written.
  */
class PorterNoteSpec extends munit.FunSuite:

  private def d(kind: Decision.Kind, fqn: String, detail: Map[String, String], reason: Reason) =
    Decision(kind, SymId(1), fqn, detail, reason, Origin("src/p/Foo.java", 12, 1))

  // -------------------------------------------------------------------------
  // the grammar
  // -------------------------------------------------------------------------

  test("one line: kind slug, the §1 classification, the detail, and no free text") {
    val note = PorterNote.render(
      d(Decision.Kind.RenamedMember, "p.Foo#style", Map("from" -> "style", "to" -> "style$shadow"),
        Reason.Universal("member-rename(§4.55)")), "")
    assertEquals(note,
      "/* porter: renamed-member reason=universal rule=member-rename(§4.55) from=style to=style$shadow */\n")
  }

  test("a CONFIGURED reason names the phase and the key — the two things an agent edits") {
    val note = PorterNote.render(
      d(Decision.Kind.DroppedType, "p.Json", Map("key" -> "p.Json"), Reason.Configured("substitutions", "p.Json")), "")
    assert(note.contains("reason=configured"), note)
    assert(note.contains("phase=substitutions"), note)
    assert(note.contains("key=p.Json"), note)
  }

  test("a value containing WHITESPACE is quoted, so the pair list stays parseable") {
    val note = PorterNote.render(
      d(Decision.Kind.RenamedPackage, "p.Foo", Map("from" -> "p.Foo", "to" -> "q.Foo"),
        Reason.Configured("package-rename", "p -> q")), "")
    assert(note.contains("""key="p -> q""""), note)
    // …and the note is still ONE line: the quoting is what stops the value being three tokens.
    assertEquals(note.count(_ == '\n'), 1, note)
  }

  test("`why` becomes the free text and moves to a second line when it does not fit") {
    val long = "because " + ("x" * 200)
    val note = PorterNote.render(
      d(Decision.Kind.DroppedMember, "p.Foo#bar", Map("key" -> "p.Foo#bar", "why" -> long),
        Reason.Configured("substitutions", "p.Foo#bar")), "  ")
    assertEquals(note.count(_ == '\n'), 2, note)
    assert(note.contains(s"— $long */"), note)
    // the indent is carried onto the continuation, so the note reads as one block at the member's
    // own column rather than starting at column 0 in the middle of a class body.
    assert(note.linesIterator.forall(l => l.isEmpty || l.startsWith("  ")), note)
  }

  test("a note can never OPEN or CLOSE a comment — scala block comments NEST (§4.58)") {
    val note = PorterNote.render(
      d(Decision.Kind.SubstitutedBody, "p.Foo#bar",
        Map("why" -> "the java said /* keep */ and then some"), Reason.Universal("r")), "")
    assert(!note.stripSuffix(" */\n").contains("*/"), note)
    assert(note.count(_ == '\n') >= 1)
    assert(!note.dropRight(4).contains("/*") || note.indexOf("/*") == 0, note)
  }

  test("a kind OUTSIDE `Rendered` produces no note at all — one edit turns a family off") {
    assert(!PorterNote.Rendered(Decision.Kind.RetypedSignature))
    assertEquals(
      PorterNote.render(d(Decision.Kind.RetypedSignature, "p.Foo#bar", Map.empty, Reason.Universal("r")), ""),
      "")
  }

  test("scan reads the SLUG back, and reports one it does not know rather than skipping it") {
    val text = "/* porter: renamed-member from=a */\nclass X\n/* porter: made-up key=z */"
    val found = PorterNote.scan(text)
    assertEquals(found.map(_.slug), List("renamed-member", "made-up"))
    assertEquals(found.map(_.kind), List(Some(Decision.Kind.RenamedMember), scala.None))
  }

  // -------------------------------------------------------------------------
  // E8 — the coverage check, in BOTH directions
  // -------------------------------------------------------------------------

  private val sym = SymId(1)
  private val renamed =
    d(Decision.Kind.RenamedMember, "p.Foo#style", Map("from" -> "style", "to" -> "style$shadow"),
      Reason.Universal("member-rename(§4.55)"))
  private val printed = PorterNote.Printed(Decision.Kind.RenamedMember, sym, "p.Foo#style", "p.Foo")
  private val goodText = "p.Foo" -> ("class Foo {\n" + PorterNote.render(renamed, "  ") + "  var style$shadow = 0\n}")

  test("clean: a decision about an emitted subject, noted, backed, and in the file") {
    assertEquals(
      NoteCoverageCheck.check(List(renamed), List(printed), Set(sym), List(goodText)),
      Nil)
  }

  test("MISSING: the run emitted the subject, recorded the decision, and printed no note") {
    val fs = NoteCoverageCheck.check(List(renamed), printed = Nil, emitted = Set(sym),
      texts = List("p.Foo" -> "class Foo { var style$shadow = 0 }"))
    assertEquals(fs.map(_.issue), List(NoteCoverageCheck.Issue.Missing))
    assertEquals(fs.head.subject, "p.Foo#style")
  }

  test("a decision about a subject this run does NOT emit is covered by decisions.tsv, not by a note") {
    assertEquals(
      NoteCoverageCheck.check(List(renamed), printed = Nil, emitted = Set.empty,
        texts = List("p.Foo" -> "class Foo")),
      Nil)
  }

  test("a kind outside `Rendered` is never demanded — that is a statement, not a gap") {
    val retyped = d(Decision.Kind.RetypedSignature, "p.Foo#xs", Map("from" -> "a", "to" -> "b"), Reason.Universal("r"))
    assertEquals(
      NoteCoverageCheck.check(List(retyped), printed = Nil, emitted = Set(sym), texts = List("p.Foo" -> "class Foo")),
      Nil)
  }

  test("UNBACKED: a note in the text of a kind the emitter did not record printing") {
    val smuggled = "p.Foo" -> "class Foo {\n  /* porter: substituted-body reason=universal rule=hand-written */\n}"
    val fs = NoteCoverageCheck.check(List(renamed), List(printed), Set(sym), List(goodText, smuggled))
    assertEquals(fs.map(_.issue), List(NoteCoverageCheck.Issue.Unbacked))
    assertEquals(fs.head.kind, Some(Decision.Kind.SubstitutedBody))
  }

  test("UNBACKED also catches a slug no `Decision.Kind` has — nothing could have derived it") {
    val fs = NoteCoverageCheck.check(Nil, Nil, Set.empty,
      List("p.Foo" -> "/* porter: invented-by-hand key=x */\nclass Foo"))
    assertEquals(fs.map(_.issue), List(NoteCoverageCheck.Issue.Unbacked))
    assertEquals(fs.head.kind, scala.None)
  }

  test("NOT WRITTEN: the emitter recorded a note the assembled file does not carry") {
    val fs = NoteCoverageCheck.check(List(renamed), List(printed), Set(sym),
      List("p.Foo" -> "class Foo { var style$shadow = 0 }"))
    // the note is missing from the text AND from the derivation's own arithmetic; both fire, and
    // that is right — they are two different repairs.
    assert(fs.exists(_.issue == NoteCoverageCheck.Issue.NotWritten), fs.map(_.render).mkString("\n"))
  }
