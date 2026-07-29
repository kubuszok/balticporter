package balticporter.emit

import balticporter.tir.*
import balticporter.tir.TypeRepr.*

/** The source map is only worth anything if its line ranges are TRUE of the text that was
  * actually written, and only safe to add if turning it on cannot move a byte of that text. Both
  * are asserted here against a hand-built program with a nested class and two same-named
  * overloads — the two shapes that break a naive position table. */
class SrcMapEmitSpec extends munit.FunSuite:

  private val FOO   = SymId(1)
  private val INT   = SymId(2)
  private val COUNT = SymId(3)
  private val INC   = SymId(4)
  private val INC2  = SymId(5)
  private val P     = SymId(6)
  private val INNER = SymId(7)
  private val IM    = SymId(8)

  private val jav  = "/a/b/src/srcmapdemo/Foo.java"
  private def O(l: Int) = Origin(jav, l, 1)
  private val tInt = TypeRef(NoPrefix, INT)
  private def tt(t: TypeRepr) = TypeTree(t, O(0))

  private def body(l: Int) = Some(Tree.Block(Nil, Tree.Return(Some(Tree.Literal(Constant.IntC(1), tInt, O(l))), tInt, O(l)), tInt, O(l)))

  private val countDef = Tree.ValDef(COUNT, tt(tInt), rhs = None, origin = O(11))
  private val inc0     = Tree.DefDef(INC, List(Nil), tt(tInt), body(20), O(20))
  private val inc1     = Tree.DefDef(INC2, List(List(Tree.ValDef(P, tt(tInt), None, O(30)))), tt(tInt), body(30), O(30))
  private val innerM   = Tree.DefDef(IM, List(Nil), tt(tInt), body(41), O(41))
  private val inner    = Tree.ClassDef(INNER, Nil, None, List(innerM), O(40))
  private val foo      = Tree.ClassDef(FOO, Nil, None, List(countDef, inc0, inc1, inner), O(5))

  private val symbols = SymbolTable(List(
    Symbol(FOO, "Foo", "srcmapdemo.Foo", Flags(), SymId.None, TypeRef(NoPrefix, FOO), origin = O(5)),
    Symbol(INT, "Int", "scala.Int", Flags(), SymId.None, NoType),
    Symbol(COUNT, "count", "srcmapdemo.Foo#count", Flags(isMutable = true), FOO, tInt),
    Symbol(INC, "inc", "srcmapdemo.Foo#inc", Flags(), FOO, MethodType(Nil, tInt)),
    Symbol(INC2, "inc", "srcmapdemo.Foo#inc", Flags(), FOO, MethodType(List("p" -> tInt), tInt)),
    Symbol(P, "p", "p", Flags(isParam = true), INC2, tInt),
    Symbol(INNER, "Inner", "srcmapdemo.Foo$Inner", Flags(), FOO, TypeRef(NoPrefix, INNER), origin = O(40)),
    Symbol(IM, "im", "srcmapdemo.Foo$Inner#im", Flags(), INNER, MethodType(Nil, tInt)),
  ))

  private val program = new Program(List(foo), symbols, Xref.build(List(foo)))

  private def withProps[A](kv: (String, String)*)(f: => A): A =
    val saved = kv.map((k, _) => k -> Option(System.getProperty(k)))
    kv.foreach((k, v) => System.setProperty(k, v))
    try f
    finally saved.foreach {
      case (k, Some(v))    => System.setProperty(k, v)
      case (k, scala.None) => System.clearProperty(k)
    }

  private def emitWithMap(): (String, List[SrcMap.Entry]) =
    val tmp = java.nio.file.Files.createTempDirectory("bp-srcmap")
    withProps("balticporter.report" -> "on", "balticporter.reportDir" -> tmp.toString) {
      val text = new TirEmitter(program).emitUnit(foo)
      // filter to THIS spec's unit: `SrcMap.recorded` is process-global, sbt runs suites in
      // one JVM, and another emitter spec running concurrently records into it too.
      val es   = SrcMap.snapshot().filter(_.unit == "srcmapdemo.Foo")
      SrcMap.reset()
      (text, es)
    }

  test("turning the source map ON does not move a byte of emitted output") {
    val off = new TirEmitter(program).emitUnit(foo)
    val (on, _) = emitWithMap()
    assertNoDiff(on, off)
  }

  test("every recorded range names the lines the member was ACTUALLY written to") {
    val (text, es) = emitWithMap()
    val lines = text.linesIterator.toVector
    assert(es.nonEmpty, "nothing was recorded")
    es.foreach { e =>
      assert(e.start >= 1 && e.end <= lines.size, s"$e out of range (${lines.size} lines)")
      val name = e.member.split('#').last.takeWhile(c => c.isLetterOrDigit || c == '_')
      if name.nonEmpty && e.kind != "class" then
        assert(lines(e.start - 1).contains(name), s"line ${e.start} `${lines(e.start - 1)}` does not declare ${e.member}")
    }
  }

  test("two overloads of one name are DIFFERENT members, at different lines") {
    val (_, es) = emitWithMap()
    val incs = es.filter(_.member.startsWith("srcmapdemo.Foo#inc"))
    assertEquals(incs.map(_.member).sorted, List("srcmapdemo.Foo#inc()", "srcmapdemo.Foo#inc(Int)"))
    assertEquals(incs.map(_.start).distinct.size, 2)
    // …and their digests differ, so a change to one is not attributed to the other
    assertEquals(incs.map(_.digest).distinct.size, 2)
  }

  test("a nested class is recorded, and its member resolves INSIDE it, not to the outer class") {
    val (_, es) = emitWithMap()
    val idx = SrcMap.Index.of(es)
    val im  = es.find(_.member == "srcmapdemo.Foo$Inner#im()").getOrElse(fail("nested member not recorded"))
    val cls = es.find(_.member == "srcmapdemo.Foo$Inner").getOrElse(fail("nested class not recorded"))
    assert(cls.start <= im.start && im.end <= cls.end, s"$im not inside $cls")
    assertEquals(idx.at("srcmapdemo.Foo", im.start).map(_.member), Some("srcmapdemo.Foo$Inner#im()"))
    assertEquals(idx.resolveFrame("srcmapdemo.Foo$Inner", im.start).map(_.member), Some("srcmapdemo.Foo$Inner#im()"))
  }

  test("the Java origin is relative to a root derived from the unit, with no flag set") {
    val (_, es) = emitWithMap()
    assert(es.forall(_.javaPath == "srcmapdemo/Foo.java"), es.map(_.javaPath).distinct.toString)
    assertEquals(es.find(_.member == "srcmapdemo.Foo#inc(Int)").map(_.javaLine), Some(30))
  }

  test("the unit itself spans the whole file, so a line between members still names the Java file") {
    val (text, es) = emitWithMap()
    val unit = es.find(_.member == "srcmapdemo.Foo").getOrElse(fail("no unit entry"))
    assertEquals(unit.start, 1)
    assertEquals(unit.end, text.linesIterator.size)
  }

  test("two emissions of the same program produce an identical map (determinism, R3)") {
    val (_, a) = emitWithMap()
    val (_, b) = emitWithMap()
    assertEquals(a.map(_.tsv), b.map(_.tsv))
  }
