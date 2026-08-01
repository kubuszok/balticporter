package balticporter.tir

/** The ONE `k=v` payload grammar (CLAUDE.md §4.575), now that it has a SECOND consumer.
  *
  * It was specified only through the porter note, whose own check reads the SLUG and deliberately
  * never parses the pairs — so the parse side had no spec at all until the port map's `shape` column
  * needed one. Both of the grammar's rules are properties of the grammar and not of the note, which
  * is why they moved to `KeyValues` and why they are pinned here.
  */
class KeyValuesSpec extends munit.FunSuite:

  test("a value with WHITESPACE is quoted and round-trips whole") {
    // The failure this prevents: the pair list is whitespace-separated, so an unquoted
    // `key=a -> b` is three tokens and every reader truncates at the first space. Measured once as
    // 594 porter notes reported unbacked, both sides reading a value neither had written.
    val pairs = List("key" -> "com.example.a -> b", "n" -> "1")
    val text  = KeyValues.render(pairs)
    assertEquals(text, """key="com.example.a -> b" n=1""")
    assertEquals(KeyValues.parse(text), Map("key" -> "com.example.a -> b", "n" -> "1"))
  }

  test("nothing may OPEN or CLOSE a Scala comment — the delimiters are spaced, never dropped") {
    // Scala block comments NEST (§4.58), so a value carrying an opening delimiter swallows the rest
    // of the emitted file. Neutralised rather than rejected: a value that cannot be rendered safely
    // is still information.
    val out = KeyValues.render(List("why" -> "see the /* marker */ above"))
    assert(!clue(out).contains("/*"))
    assert(!out.contains("*/"))
    assert(out.contains("marker"), "…and the content survives")
  }

  test("an EMPTY value is quoted, so it is a value and not a missing token") {
    assertEquals(KeyValues.render(List("a" -> "", "b" -> "x")), """a="" b=x""")
    assertEquals(KeyValues.parse("""a="" b=x"""), Map("a" -> "", "b" -> "x"))
  }

  test("NEGATIVE: an UNKNOWN key is KEPT, and a malformed token costs its pair and not the row") {
    // A payload written by a NEWER engine must degrade to "I do not understand this key", never to
    // a parse failure that discards the keys this engine does understand — `DESIGN.md` §8.3's
    // per-question degradation, at the grammar rather than at the artifact.
    assertEquals(KeyValues.parse("form=class fromTheFuture=7"),
                 Map("form" -> "class", "fromTheFuture" -> "7"))
    // a quote that never closes takes the rest of the payload and stops; the pairs before it stand.
    assertEquals(KeyValues.parse("""a=1 b="unclosed"""), Map("a" -> "1", "b" -> "unclosed"))
    // a trailing token with no `=` is not a pair, and is dropped rather than thrown on.
    assertEquals(KeyValues.parse("a=1 garbage"), Map("a" -> "1"))
    assertEquals(KeyValues.parse(""), Map.empty[String, String])
  }

  test("the PORTER NOTE and the port map are the SAME grammar, not two spellings of one") {
    // The whole reason the primitives moved to `api`. A reader that has learned the note's spelling
    // must be able to read a `shape` column, and two renderings of one grammar is exactly the drift
    // §4.56 is about.
    assertEquals(PorterNote.safe("a\tb"), KeyValues.safe("a\tb"))
    assertEquals(PorterNote.value("a b"), KeyValues.value("a b"))
  }
