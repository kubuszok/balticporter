package balticporter.corpus

import balticporter.emit.{ InjectedSurface, TirEmitter }
import balticporter.emit.InjectedSurface.MemberSig
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A call to a member of a dropped type takes the arity its INJECTED replacement declares — read off the file the port ships, under the replacement's emitted name — with no `externalParenless` entry
  * restating it.
  */
class InjectedArityCallSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Holder {
      |  public int size() { return 0; }
      |  public String name() { return ""; }
      |  public int count(int from) { return from; }
      |}
      |public class User {
      |  int a(Holder h) { return h.size(); }
      |  String b(Holder h) { return h.name(); }
      |  int c(Holder h) { return h.count(1); }
      |  java.util.function.ToIntFunction<Holder> unbound() { return Holder::size; }
      |  java.util.function.IntSupplier bound(Holder h) { return h::size; }
      |}
      |public class Sub extends Holder {
      |  int implicitThis() { return size(); }
      |  Object fromInner() { return new Object() { int n() { return size(); } }; }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)

  /** the replacement as a port ships it: under ANOTHER package, reached through the dropped type's alias. */
  private val injected = InjectedSurface
    .Surface(
      List(
        MemberSig("lib.Holder", "size", Nil, hasParens = false, Some("Int")),
        MemberSig("lib.Holder", "name", List(Nil), hasParens = true, Some("String")),
        MemberSig("lib.Holder", "count", List(List(InjectedSurface.ParamType("Int"))), hasParens = true, Some("Int"))
      ).groupBy(m => (m.ownerFqn, m.name, m.arity))
    )
    .withAliases(Map("demo.Holder" -> "lib.Holder"))

  private def user(out: String): String = out.linesIterator.dropWhile(!_.contains("class User")).mkString("\n")

  test("a member the replacement declares parenless is called without `()`") {
    val out = user(new TirEmitter(program, injectedSurface = injected).emit)
    assert(clue(out).contains("h.size"))
    assert(!out.contains("h.size()"))
  }

  test("NEGATIVE: a member the replacement declares with `()`, or with parameters, keeps them") {
    val out = user(new TirEmitter(program, injectedSurface = injected).emit)
    assert(clue(out).contains("h.name()"))
    assert(out.contains("h.count(1)"))
  }

  test("an implicit-`this` call from a subclass, or from a class nested in it, takes the replacement's arity too") {
    val out = new TirEmitter(program, injectedSurface = injected).emit
    val sub = out.linesIterator.dropWhile(!_.contains("class Sub")).take(12).mkString("\n")
    assertEquals(clue(sub).linesIterator.count(_.contains("size")), 2)
    assert(!sub.contains("size()"))
  }

  test("a method reference, unbound or bound, reads the replacement's arity") {
    val out = user(new TirEmitter(program, injectedSurface = injected).emit)
    assert(!clue(out).contains("self$.size()"))
    assert(out.contains("self$.size"))
    assert(out.contains("recv$.size"), "a bound reference to a parenless member wraps the receiver once")
  }

  test("NEGATIVE: with no replacement the call keeps java's parens") {
    assert(clue(user(new TirEmitter(program).emit)).contains("h.size()"))
  }
