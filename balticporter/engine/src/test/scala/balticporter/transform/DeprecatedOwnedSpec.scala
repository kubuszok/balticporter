package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A java `@Deprecated` member the port declares is scala's `@deprecated`, so its callers are warned as java's are; the port's own uses carry `@nowarn`, except inside a deprecated declaration, where
  * scalac reports none.
  */
class DeprecatedOwnedSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |class A {
      |  @Deprecated public int old() { return 1; }
      |  @Deprecated public int older() { return old(); }
      |  public int user() { return old(); }
      |}
      |@Deprecated class D { int m() { return new A().old(); } }
      |class E { Object make() { return new D(); } }
      |""".stripMargin

  private lazy val out: String =
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(new SuppressionPhase))
    new TirEmitter(after, notes = log).emit

  private def before(member: String): List[String] =
    val ls = out.linesIterator.toList
    val at = ls.indexWhere(_.contains(member))
    ls.slice(math.max(0, at - 6), at)

  test("a java @Deprecated member is emitted as scala's @deprecated") {
    assert(!clue(out).contains("@java.lang.Deprecated"), out)
    assert(before("def old()").exists(_.contains("@scala.deprecated")), out)
  }

  test("an undeprecated use takes @nowarn; a use inside a deprecated declaration does not") {
    assert(before("def user()").exists(_.contains("nowarn")), out)
    assert(!before("def older()").exists(_.contains("nowarn")), out)
    assert(!before("def m()").exists(_.contains("nowarn")), out)
    assert(before("def make()").exists(_.contains("nowarn")), out)
  }
