package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Decision, Pipeline, RuleScope }

/** A field java leaves to its default is still the JVM's null while an ancestor's constructor runs, and the wrapper reads that null as PRESENT: a method the ancestor's constructor reaches reads the
  * wrapper's empty instead, and a field it writes there keeps the written value.
  */
class NullabilityPreInitSpec extends munit.FunSuite:
  private val javaSrc =
    """package com.demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS) @interface Null {}
      |class Menu {}
      |class Base {
      |  Base () { setUp(); }
      |  void setUp () {}
      |  boolean over () { return false; }
      |}
      |class Item extends Base {
      |  @Null Menu menu;
      |  @Null Menu written;
      |  @Null Menu later;
      |  Item () { super(); }
      |  @Override void setUp () { check(); written = new Menu(); }
      |  void check () { over(); }
      |  @Override boolean over () { return menu == null; }
      |  boolean idle () { return later == null && menu == null; }
      |}
      |""".stripMargin

  private lazy val ported =
    val phase = new NullabilityTransform(
      annotations = Set("com.demo.Null"),
      target = NullabilityTransform.Target.Named("demo.Nullable"),
      scope = RuleScope.Everywhere(Set.empty)
    )
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Demo.java"), List(phase))
    (new TirEmitter(after, notes = log).emit, log)

  private def body(out: String, member: String): String =
    val at = out.indexOf(if member.contains(' ') then member else s"def $member")
    assert(at >= 0, s"no `$member` in\n$out")
    out.substring(at, out.indexOf("\n  }", at))

  test("a method the ancestor's constructor reaches through an override reads the empty for the JVM's null") {
    val (out, _) = ported
    assert(
      clue(body(out, "override def over")).contains("(if (this.menu.asInstanceOf[scala.AnyRef] eq null) demo.Nullable.empty[com.demo.Menu] else this.menu).isEmpty"),
      out
    )
  }

  test("a method no construction reaches reads the field as written") {
    val (out, _) = ported
    val idle     = body(out, "idle")
    assert(!clue(idle).contains("asInstanceOf[scala.AnyRef] eq null"), out)
  }

  test(
    "a field the ancestor's constructor writes keeps that value: its initialiser takes the empty only over the JVM's null"
  ) {
    val (out, _) = ported
    assert(
      clue(out).contains(
        "var written: demo.Nullable[com.demo.Menu] = (if (this.written.asInstanceOf[scala.AnyRef] eq null) demo.Nullable.empty[com.demo.Menu] else this.written)"
      ),
      out
    )
    assert(out.contains("var later: demo.Nullable[com.demo.Menu] = demo.Nullable.empty"), out)
  }

  test("each guarded method is a decision, a write alone is none") {
    val (_, log) = ported
    val guarded  = log.all.filter(_.kind == Decision.Kind.GuardedPreInitRead).map(_.subjectFqn).sorted
    assertEquals(guarded, List("com.demo.Item#over"))
  }

  test("no defaulted wrapped field reached early is no guard at all") {
    val phase = new NullabilityTransform(
      annotations = Set("com.demo.Null"),
      target = NullabilityTransform.Target.Union,
      scope = RuleScope.Everywhere(Set.empty)
    )
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Demo.java"), List(phase))
    assert(!clue(new TirEmitter(after, notes = log).emit).contains("eq null) "))
    assert(log.all.forall(_.kind != Decision.Kind.GuardedPreInitRead))
  }
