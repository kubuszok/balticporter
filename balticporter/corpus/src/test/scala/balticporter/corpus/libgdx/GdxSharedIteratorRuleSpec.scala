package balticporter.corpus.libgdx

import balticporter.testkit.PortSuite

/** How a §1(c) rule is TESTED — from the porting repository, with the engine's testkit, on a Java
  * snippet. The third of the three things [[GdxSharedIteratorRule]] exists to demonstrate. */
class GdxSharedIteratorRuleSpec extends PortSuite:

  /** The shape of libGDX's own `Array`, reduced to what the rule reads: the FQN and an
    * `iterator()`. The rule keys on the fully-qualified name, so the package must be real. */
  private val gdxArray =
    """package com.badlogic.gdx.utils;
      |public class Array<T> implements Iterable<T> {
      |  public java.util.Iterator<T> iterator() { return null; }
      |}""".stripMargin

  private def rule = new GdxSharedIteratorRule

  test("nested iteration over the SAME libGDX collection is reported") {
    val r = rule
    port(
      s"""$gdxArray
         |class Scene {
         |  com.badlogic.gdx.utils.Array<String> actors;
         |  void collide() {
         |    for (String a : actors) {
         |      for (String b : actors) { System.out.println(a + b); }
         |    }
         |  }
         |}""".stripMargin,
      r,
    )
    assertEquals(r.findings.size, 1)
    val f = r.findings.head
    assertEquals(f.collection, "com.badlogic.gdx.utils.Array")
    assert(clue(f.render).contains("the outer loop ends early"))
  }

  test("nested iteration over DIFFERENT collections is not a hazard") {
    val r = rule
    port(
      s"""$gdxArray
         |class Scene {
         |  com.badlogic.gdx.utils.Array<String> actors;
         |  com.badlogic.gdx.utils.Array<String> others;
         |  void collide() {
         |    for (String a : actors) {
         |      for (String b : others) { System.out.println(a + b); }
         |    }
         |  }
         |}""".stripMargin,
      r,
    )
    assertEquals(r.findings, Nil)
  }

  test("nesting over a JDK collection is not this library's problem") {
    val r = rule
    port(
      """import java.util.List;
        |class Scene {
        |  List<String> actors;
        |  void collide() {
        |    for (String a : actors) { for (String b : actors) { System.out.println(a + b); } }
        |  }
        |}""".stripMargin,
      r,
    )
    assertEquals(r.findings, Nil)
  }

  test("a single loop is fine — the cached iterator is only a hazard when it is REUSED") {
    val r = rule
    port(
      s"""$gdxArray
         |class Scene {
         |  com.badlogic.gdx.utils.Array<String> actors;
         |  void draw() { for (String a : actors) { System.out.println(a); } }
         |}""".stripMargin,
      r,
    )
    assertEquals(r.findings, Nil)
  }

  test("the rule is an ANALYSIS: it must not change a single byte of the emitted Scala") {
    val java =
      s"""$gdxArray
         |class Scene {
         |  com.badlogic.gdx.utils.Array<String> actors;
         |  void collide() {
         |    for (String a : actors) { for (String b : actors) { System.out.println(a + b); } }
         |  }
         |}""".stripMargin
    assertEquals(port(java, rule).out, port(java).out)
  }
