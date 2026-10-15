package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** A member reached through a CAST `this` (`((Sub) this).f`) keeps the cast: the cast is what makes a subclass member visible from the superclass's body. */
class ThisCastReceiverSpec extends munit.FunSuite:

  private val out = new TirEmitter(
    SpoonTir.fromSource(
      """package demo;
        |class Base {
        |  String show() { return ((Sub) this).flag ? "y" + ((Sub) this).name() : "n"; }
        |  boolean own() { return this.mine; }
        |  boolean mine;
        |}
        |class Sub extends Base {
        |  boolean flag;
        |  String name() { return "s"; }
        |}
        |""".stripMargin
    )
  ).emit

  test("a field read through a cast `this` keeps the cast") {
    assert(clue(out).contains("this.asInstanceOf[demo.Sub].flag"))
  }

  test("a call through a cast `this` keeps the cast") {
    assert(clue(out).contains("this.asInstanceOf[demo.Sub].name()"))
  }

  test("an uncast `this.f` stays a plain `this.f`") {
    assert(clue(out).contains("this.mine"))
    assert(!out.contains("this.asInstanceOf[demo.Base]"))
  }
