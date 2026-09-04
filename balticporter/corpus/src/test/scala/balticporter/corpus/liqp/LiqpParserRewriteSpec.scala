package balticporter.corpus.liqp

/** DECISION D-liqp-1b's rewrite — the generated parser's references INTO the ported library, moved
  * to the namespace the port emits before javac reads them. */
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

  // -----------------------------------------------------------------------------------------
  // …AND SO ARE THE GENERATED SOURCES THEMSELVES — the input javac reads.

  private def tree(files: (String, String)*): java.nio.file.Path =
    val root = java.nio.file.Files.createTempDirectory("liqp-gen")
    files.foreach { (rel, text) =>
      val p = root.resolve(rel)
      java.nio.file.Files.createDirectories(p.getParent)
      java.nio.file.Files.writeString(p, text)
    }
    root

  test("the GENERATED SOURCES are part of the cache key — same tree, same digest") {
    val a = tree("liquid/parser/v4/LiquidParser.java" -> "class LiquidParser {}",
                 "liquid/parser/v4/LiquidLexer.java"  -> "class LiquidLexer {}")
    val b = tree("liquid/parser/v4/LiquidParser.java" -> "class LiquidParser {}",
                 "liquid/parser/v4/LiquidLexer.java"  -> "class LiquidLexer {}")
    assertEquals(LiqpClasspath.generatedDigest(a), LiqpClasspath.generatedDigest(b))
  }

  test("…and CONTENT decides it — a regenerated grammar is a different key") {
    val a = tree("liquid/parser/v4/LiquidParser.java" -> "class LiquidParser { void rule(); }")
    val b = tree("liquid/parser/v4/LiquidParser.java" -> "class LiquidParser { void rule2(); }")
    assertNotEquals(LiqpClasspath.generatedDigest(a), LiqpClasspath.generatedDigest(b))
  }

  test("…and so does the FILE SET — a new rule producing a new file is a different key") {
    val a = tree("liquid/parser/v4/LiquidParser.java" -> "class LiquidParser {}")
    val b = tree("liquid/parser/v4/LiquidParser.java"  -> "class LiquidParser {}",
                 "liquid/parser/v4/LiquidVisitor.java" -> "class LiquidVisitor {}")
    assertNotEquals(LiqpClasspath.generatedDigest(a), LiqpClasspath.generatedDigest(b))
  }

  test("…and a RENAME with identical bytes is a different key too — the path is in the digest") {
    // The one case a content-only hash misses: ANTLR renames a generated class when the grammar's
    // own name changes, and javac then compiles a class file at a name nothing imports.
    val a = tree("liquid/parser/v4/LiquidParser.java" -> "class X {}")
    val b = tree("liquid/parser/v5/LiquidParser.java" -> "class X {}")
    assertNotEquals(LiqpClasspath.generatedDigest(a), LiqpClasspath.generatedDigest(b))
  }

  test("an ABSENT tree digests to a stated value rather than throwing — the fatality is elsewhere") {
    // `compileParser` already refuses an absent tree with the regenerate command, and it is the
    // right place: it can say what to run. The digest is consulted BEFORE that, on a freshness
    // question, so it must have an answer — and a distinct one, or a tree that vanished would
    // reuse the cache of the tree that was there.
    val gone = java.nio.file.Files.createTempDirectory("liqp-gen").resolve("nope")
    val real = tree("a/B.java" -> "class B {}")
    assertNotEquals(LiqpClasspath.generatedDigest(gone), LiqpClasspath.generatedDigest(real))
  }
