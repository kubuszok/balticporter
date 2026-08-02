package balticporter.corpus.liqp

/** DECISION D-liqp-1b's rewrite — the generated parser's references INTO the ported library, moved
  * to the namespace the port emits before javac reads them.
  *
  * Two things are under test and only the first is obvious.
  *
  * **The §4.56 separator discipline.** A rewrite that matched the string `liqp` would rewrite the
  * generated parser's own package, every identifier containing it and every honest name that
  * happens to share the prefix — silently, with javac perfectly happy, because the result is still
  * a name. `CLAUDE.md` §4.56 is the rule and this is the sharpest place in the corpus to break it:
  * the parser's OWN package is `liquid.parser.v4`, one letter apart from the library's `liqp`.
  *
  * **The enum-constant form, which no compile can see.** The port emits a java enum as a Scala
  * `sealed abstract class` plus a companion `object` of `case object`s. Scala's static forwarders
  * put `values()`/`valueOf(String)` on the companion CLASS and each constant on the MODULE class,
  * so `ErrorMode.LAX` compiles against any java enum and is a `NoSuchFieldError` at RUN time —
  * `CLAUDE.md` §3's defect class, arriving through a build step rather than through a translation.
  * Measured: `valueOf("LAX")` reaches the forwarder and returns the same singleton, so the parser's
  * `errorMode == ErrorMode.STRICT` reference comparison still holds.
  */
class LiqpParserRewriteSpec extends munit.FunSuite:

  private def rewrite(s: String): String = LiqpClasspath.rewriteReferences(s)._1
  private def counts(s: String): (Int, Int) =
    val (_, pkg, enums) = LiqpClasspath.rewriteReferences(s)
    (pkg, enums)

  test("an import INTO the ported library moves to the emitted namespace") {
    assertEquals(rewrite("import liqp.TemplateParser;"), "import ssg.liquid.TemplateParser;")
    assertEquals(counts("import liqp.TemplateParser;"), (1, 0))
  }

  test("the parser's OWN package is untouched — it is external, and one letter away") {
    val src = "package liquid.parser.v4;\nimport liquid.parser.v4.LiquidLexer;"
    assertEquals(rewrite(src), src)
    assertEquals(counts(src), (0, 0))
  }

  test("a prefix is not a structural fact — nothing that merely CONTAINS the name moves") {
    for src <- List(
        "int liqpCount = 0;",            // an identifier starting with it
        "String myliqp = \"x\";",        // …and one ending with it
        "other.liqp.Thing t;",           // a qualified name where it is not the ROOT
        "obj.liqp.field = 1;",           // …the same, through a value
      )
    do assertEquals(rewrite(src), src, s"rewrote: $src")
  }

  test("the cut is at a `.` — `liqp` alone, or before any other character, is not a match") {
    assertEquals(rewrite("liqp"), "liqp")
    assertEquals(rewrite("liqp2.Thing"), "liqp2.Thing")
    assertEquals(rewrite("liqp_x.Thing"), "liqp_x.Thing")
  }

  test("an ENUM CONSTANT becomes valueOf — the form the emitted Scala can actually link") {
    assertEquals(
      rewrite("private TemplateParser.ErrorMode m = TemplateParser.ErrorMode.LAX;"),
      "private TemplateParser.ErrorMode m = TemplateParser.ErrorMode.valueOf(\"LAX\");")
    assertEquals(
      rewrite("return errorMode == TemplateParser.ErrorMode.STRICT;"),
      "return errorMode == TemplateParser.ErrorMode.valueOf(\"STRICT\");")
  }

  test("the enum TYPE in a declaration position is left alone — only a CONSTANT selector moves") {
    val decl = "public LiquidParser(TokenStream in, TemplateParser.ErrorMode errorMode) {"
    assertEquals(rewrite(decl), decl)
    assertEquals(counts(decl), (0, 0))
  }

  test("`valueOf` and `values` are already the forwarder form and are not re-wrapped") {
    for src <- List(
        "TemplateParser.ErrorMode.valueOf(name)",
        "TemplateParser.ErrorMode.values()",
      )
    do assertEquals(rewrite(src), src, s"rewrote: $src")
  }

  test("a SCREAMING_CASE constant on any OTHER type is untouched — this is not a general rule") {
    for src <- List(
        "Flavor.LIQUID",
        "SomeOther.ErrorMode.LAX",
        "Integer.MAX_VALUE",
      )
    do assertEquals(rewrite(src), src, s"rewrote: $src")
  }

  test("both rewrites compose on the shape the generated parser actually has") {
    val src =
      """package liquid.parser.v4;
        |import liqp.TemplateParser;
        |public class LiquidParser {
        |    private TemplateParser.ErrorMode errorMode = TemplateParser.ErrorMode.LAX;
        |    boolean isStrict() { return errorMode == TemplateParser.ErrorMode.STRICT; }
        |    boolean isWarn()   { return errorMode == TemplateParser.ErrorMode.WARN; }
        |}""".stripMargin
    val (out, pkg, enums) = LiqpClasspath.rewriteReferences(src)
    assertEquals(pkg, 1)
    assertEquals(enums, 3)
    assert(out.contains("package liquid.parser.v4;"), out)
    assert(out.contains("import ssg.liquid.TemplateParser;"), out)
    assert(out.contains("TemplateParser.ErrorMode.valueOf(\"LAX\")"), out)
    assert(!out.contains("ErrorMode.LAX"), out)
  }

  test("the rewrite POLICY is part of the cache key — parser classes depend on it") {
    assert(LiqpClasspath.rewritePolicy.contains("ssg.liquid"), LiqpClasspath.rewritePolicy)
    assert(LiqpClasspath.rewritePolicy.contains("TemplateParser.ErrorMode"),
      LiqpClasspath.rewritePolicy)
  }
