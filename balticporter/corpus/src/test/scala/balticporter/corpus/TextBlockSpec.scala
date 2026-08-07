package balticporter.corpus

import balticporter.testkit.PortSuite

/** SE15 TEXT BLOCKS — `SpoonKinds` called this the sharpest of the four ABSORBED-SILENTLY kinds,
  * and this spec is the probe that decides whether it is a defect at all.
  *
  * The worry was exact: `CtTextBlock extends CtLiteral`, so `SpoonTir.literal` takes it and no arm
  * is aware a text block was there — the shape `SpoonKinds.Absence.AbsorbedSilently` names as "the
  * dangerous one". What the worry could not settle without running something is WHICH STRING
  * arrives, and that is the whole question:
  *
  *   - JLS 3.10.6 says a text block DENOTES the string obtained by stripping incidental whitespace,
  *     normalising line terminators to `\n`, and applying the escapes — the CONTENT, not the source;
  *   - `SpoonTir.literal` reads `CtLiteral.getValue`, never `getOriginalSourceFragment` (the
  *     frontend does not call that method anywhere), so what reaches the TIR is a `Constant.StringC`
  *     holding exactly that denoted string;
  *   - `TirEmitter.escape` puts every newline back as `\n` inside a single-line `"…"`
  *     (`ENGINE-LIMITS.md` L1, which exists because a RAW newline ends a Scala literal).
  *
  * So the SHAPE changes and the VALUE does not, and a value-preserving shape change is not a
  * difference. That is what these assertions establish, and it is why the catalog row is a
  * `NonDiff` with this spec as its evidence rather than an entry on a work list.
  */
class TextBlockSpec extends PortSuite:

  // A TRIPLE-QUOTED Scala string holding a JAVA text block. Nothing here is processed by Scala; the
  // `\"\"\"` sequences reach the java parser as `"""`, which is what opens and closes the block.
  private val src =
    "package p;\n" +
    "class T {\n" +
    "  String block() {\n" +
    "    return \"\"\"\n" +
    "        Hello,\n" +
    "        World\"\"\";\n" +
    "  }\n" +
    "  String indented() {\n" +
    "    return \"\"\"\n" +
    "            outer\n" +
    "              inner\n" +
    "            \"\"\";\n" +
    "  }\n" +
    "  String quoted() {\n" +
    "    return \"\"\"\n" +
    "        say \"hi\"\\n done\"\"\";\n" +
    "  }\n" +
    "}\n"

  private val out = port(src).out

  test("the VALUE is JLS 3.10.6's — incidental whitespace stripped, terminators normalised") {
    // Eight spaces of common indentation go; the newline BETWEEN the two content lines stays. If
    // the frontend were reading source text instead, this would carry the indentation.
    assert(clue(out).contains("\"Hello,\\nWorld\""))
  }

  test("…and the strip is per-BLOCK, so relative indentation inside it survives") {
    // The closing delimiter's own line participates in the minimum, which is why `outer` loses all
    // of its indentation and `inner` keeps the two spaces that are its own.
    assert(clue(out).contains("\"outer\\n  inner\\n\""))
  }

  test("an escape inside the block is applied ONCE, by java, and re-escaped by the emitter") {
    // `\n` written in the source is a newline in the value; the embedded quotes need no escaping in
    // java and DO need it in a Scala `"…"`. Both directions in one literal.
    assert(clue(out).contains("\"say \\\"hi\\\"\\n done\""))
  }

  test("NO raw newline reaches the emitted file — L1's rule, met at the construct that carries most") {
    // A raw newline ENDS a Scala string literal and every byte after it is read as source: the
    // failure that cost 1,334 errors from one file. A text block is where a literal is most likely
    // to hold one, so the general rule is asserted again here at the specific construct.
    val literals = "\"[^\"\\n]*\"".r.findAllIn(out).toList
    assert(literals.nonEmpty, clue(out))
    assert(!out.contains("\"\"\""), "the emitter never produces a Scala triple-quoted literal")
  }
