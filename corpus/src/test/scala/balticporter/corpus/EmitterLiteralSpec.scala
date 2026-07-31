package balticporter.corpus

import balticporter.testkit.PortSuite

/** Two LEXICAL seams of the emitter, pinned through the pipeline — a Java snippet in, the emitted
  * Scala asserted. Both are CLAUDE.md §1(a) facts about the two languages' lexers, both were found
  * by porting anim8-gdx, and neither is visible to any check: the emitted file simply does not
  * parse, so the whole failure arrives as a wall of syntax errors attributed to whatever the lexer
  * was reading when it gave up.
  *
  *   - **a literal's VALUE has to be re-escaped.** `Constant.StringC` holds decoded text, so a
  *     control character, a newline or a lone surrogate has to be put back in a form Scala accepts.
  *     anim8's `ConstantData` holds four ISO-8859-1 literals (47,935 + 3 × 6,390 characters) full of
  *     both: **1,334 errors** from one file, because one unescaped newline ends the literal and
  *     every byte after it is read as source.
  *   - **a prefix operator and its operand are two tokens.** Scala's lexer takes a maximal run of
  *     operator characters as one identifier, so `-` against a literal that already renders `-…`
  *     gives `--`. Java negating a hex literal whose `long` value is negative is routine in
  *     hash-mixing code (`x * -0xC13FA9A902A6328FL`): **48 errors** in one method.
  *
  * No phase is involved: `port(java)` with no phases is the emitter's own identity fixture.
  */
class EmitterLiteralSpec extends PortSuite:

  // -------------------------------------------------------------------------------------------
  // string literals
  // -------------------------------------------------------------------------------------------

  // NB the two escaping levels. This is a TRIPLE-QUOTED Scala string, so nothing in it is processed
  // by Scala — `\u0001` reaches the Java parser as six characters, and JAVA expands it (Java
  // processes `\uXXXX` at the source level, before tokenising). So the literal's VALUE below is:
  // `a`, U+0001, `b`, LF, `c`, FF, `d`, `"`, `e`, `\`, `f`, `£`, `g`, DEL.
  private val stringy =
    """package p;
      |class Lit {
      |  String s() { return "a\u0001b\nc\fd\"e\\f£g\u007f"; }
      |}
      |""".stripMargin

  test("every character Scala's lexer cannot take verbatim is escaped") {
    val p = port(stringy)
    assertEmits(p, "\\u0001")  // a control character with no named escape
    assertEmits(p, "\\n")      // the one that ENDS the literal if it is left raw
    assertEmits(p, "\\f")      // a named escape the five-case version did not have
    assertEmits(p, "\\\"")     // the quote
    assertEmits(p, "\\\\")     // the backslash
    assertEmits(p, "\\u007f")  // DEL — printable-looking, and an illegal character
  }

  test("ordinary non-ASCII text is emitted VERBATIM — the file is UTF-8 and Scala reads it as UTF-8") {
    // The alternative (escaping everything above ASCII) is safe and unreadable, and it would churn
    // every port's diff for the sake of characters that already round-trip.
    assertEmits(port(stringy), "£")
  }

  test("NO raw control character survives anywhere in the emitted source") {
    // The strongest form of the assertion and the cheapest: a control character in the output is a
    // file that does not parse, wherever it came from. Tabs/newlines are the emitter's own layout.
    val out  = port(stringy).out
    val bad  = out.filter(c => (c < ' ' || c.toInt == 0x7f) && c != '\n' && c != '\r' && c != '\t')
    assertEquals(bad.map(_.toInt).toList, List.empty[Int], s"raw control characters in:\n$out")
  }

  // -------------------------------------------------------------------------------------------
  // prefix operators
  // -------------------------------------------------------------------------------------------

  test("a prefix `-` on a literal that renders NEGATIVE parenthesises rather than lexing as `--`") {
    // `0xC13FA9A902A6328FL` has bit 63 set, so its `long` value IS -4521708957497675121; the java
    // `-` in front of it then renders against a leading `-`.
    val p = port(
      """package p;
        |class Mix {
        |  long mix(long x) { return x * -0xC13FA9A902A6328FL; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "-(-4521708957497675121L)")
    assertNotEmits(p, "--")
  }

  test("a prefix operator on an ordinary operand is NOT parenthesised — the fix is not a rewrite") {
    // The negative half: a check that only ever fires is a check that has changed the output for
    // everything. `-y` and `!b` must be untouched.
    val p = port(
      """package p;
        |class Plain {
        |  int neg(int y) { return -y; }
        |  boolean not(boolean b) { return !b; }
        |}
        |""".stripMargin
    )
    assertEmits(p, "-y")
    assertNotEmits(p, "-(y)")
    assertEmits(p, "!b")
    assertNotEmits(p, "!(b)")
  }
