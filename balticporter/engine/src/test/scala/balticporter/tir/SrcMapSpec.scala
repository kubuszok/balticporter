package balticporter.tir

class SrcMapSpec extends munit.FunSuite:

  private def e(unit: String, member: String, kind: String, s: Int, en: Int, jp: String = "p/Foo.java", jl: Int = 1) =
    SrcMap.Entry(unit, member, kind, s, en, jp, jl, "deadbeef")

  // --- the source root, derived from the PORT (CLAUDE.md §4.6) -------------------------------

  test("the Java source root is DERIVED from the unit, not read from a flag") {
    assertEquals(
      SrcMap.sourceRootOf("a.b.c.utils.Grid", "/anywhere/at/all/upstream/src/a/b/c/utils/Grid.java"),
      Some("/anywhere/at/all/upstream/src/"))
    // …so the recorded path is the same from any checkout, with no script involved
    assertEquals(
      SrcMap.relativise("/anywhere/at/all/upstream/src/a/b/c/utils/Grid.java", Some("/anywhere/at/all/upstream/src/")),
      "a/b/c/utils/Grid.java")
  }

  test("a nested unit's root comes from its TOP-LEVEL name") {
    assertEquals(SrcMap.sourceRootOf("p.Outer$Inner", "/r/p/Outer.java"), Some("/r/"))
  }

  test("no root is invented when the origin does not match the FQN (a renamed package)") {
    assertEquals(SrcMap.sourceRootOf("new.pkg.Foo", "/r/old/pkg/Foo.java"), scala.None)
    // and a synthetic origin is carried through untouched rather than relativised into nonsense
    assertEquals(SrcMap.relativise("<synthetic>", Some("/r/")), "<synthetic>")
  }

  // --- resolution ---------------------------------------------------------------------------

  private val idx = SrcMap.Index.of(List(
    e("p.Foo", "p.Foo", "class", 1, 40),
    e("p.Foo", "p.Foo#a()", "def", 3, 6, jl = 10),
    e("p.Foo", "p.Foo$Inner", "class", 8, 20, jl = 30),
    e("p.Foo", "p.Foo$Inner#b()", "def", 10, 14, jl = 33),
    e("p.Bar", "p.Bar", "class", 1, 9, "p/Bar.java"),
  ))

  test("a compiler path resolves by the LONGEST matching suffix, so the output root is irrelevant") {
    assertEquals(idx.unitForFile("port/src_managed/main/scala/p/Foo.scala"), Some("p.Foo"))
    assertEquals(idx.unitForFile("/abs/whatever/p/Foo.scala"), Some("p.Foo"))
    assertEquals(idx.unitForFile("p/Foo.scala"), Some("p.Foo"))
    assertEquals(idx.unitForFile("p/Nope.scala"), scala.None)
  }

  test("a stack frame's runtime class resolves to the FILE its bytes were emitted into") {
    assertEquals(idx.unitForClass("p.Foo"), Some("p.Foo"))
    assertEquals(idx.unitForClass("p.Foo$"), Some("p.Foo"))               // companion
    assertEquals(idx.unitForClass("p.Foo$Inner"), Some("p.Foo"))          // nested type
    assertEquals(idx.unitForClass("p.Foo$$anonfun$3"), Some("p.Foo"))     // lambda
    assertEquals(idx.unitForClass("p.Foobar"), scala.None)                // cuts only at separators
  }

  test("a line resolves to the INNERMOST member containing it") {
    assertEquals(idx.at("p.Foo", 4).map(_.member), Some("p.Foo#a()"))
    assertEquals(idx.at("p.Foo", 12).map(_.member), Some("p.Foo$Inner#b()"))
    assertEquals(idx.at("p.Foo", 18).map(_.member), Some("p.Foo$Inner"))
    // between members: the unit itself, which still names the right Java FILE
    assertEquals(idx.at("p.Foo", 30).map(_.member), Some("p.Foo"))
  }

  test("resolveFrame joins the two: class name + line -> member + Java origin") {
    val r = idx.resolveFrame("p.Foo$Inner", 12)
    assertEquals(r.map(_.member), Some("p.Foo$Inner#b()"))
    assertEquals(r.map(_.javaAt), Some("p/Foo.java:33"))
  }

  test("an unknown file/class is None, not a wrong answer") {
    assertEquals(idx.resolveFile("munit/Assertions.scala", 12), scala.None)
    assertEquals(idx.resolveFrame("munit.Assertions", 12), scala.None)
    assertEquals(SrcMap.Index.empty.resolveFrame("p.Foo", 1), scala.None)
  }

  // --- the tsv ------------------------------------------------------------------------------

  test("an entry round-trips through its TSV line") {
    val x = e("p.Foo", "p.Foo#a()", "def", 3, 6)
    assertEquals(SrcMap.parse(x.tsv), Some(x))
    assertEquals(SrcMap.parse("#header\tline"), scala.None)
  }
