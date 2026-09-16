package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `TypeRedirectTransform.memberRenames` for an EXTERNAL redirected type: the type binds no member of its own, so the hits are the owned overrides anchored on it, and the redirect that detaches
  * the parent is what licenses moving them (`MemberRenamer.Request.detachedParents`). `Comparable -> Ordered` with `compareTo -> compare` is the shape.
  */
class TypeRedirectExternalRenameSpec extends munit.FunSuite:

  private val java =
    """package com.demo;
      |public class Item implements Comparable<Item> {
      |  public int compareTo(Item o) { return 0; }
      |}
      |class Sub extends Item {
      |  @Override public int compareTo(Item o) { return 1; }
      |}
      |""".stripMargin

  private def emit(phase: TypeRedirectTransform): String =
    val before       = SpoonTir.fromSource(java, "Item.java")
    val (after, log) = Pipeline.runTraced(before, List(phase))
    new TirEmitter(after, notes = log).emit

  test("an external type's member renames over every owned override anchored on it, the redirect detaching the parent") {
    val out = emit(
      new TypeRedirectTransform(
        redirects = Map("java.lang.Comparable" -> "scala.math.Ordered"),
        memberRenames = Map("java.lang.Comparable" -> Map("compareTo" -> "compare"))
      )
    )
    assert(clue(out).contains("extends scala.math.Ordered[Item]"))
    assertEquals(clue(out).sliding("def compare(".length).count(_ == "def compare("), 2)
    assert(!out.contains("def compareTo(")) // the porter note still says `from=compareTo`
  }

  test("without a member rename the redirect keeps the java name — the anchor still holds it") {
    val out = emit(new TypeRedirectTransform(redirects = Map("java.lang.Comparable" -> "scala.math.Ordered")))
    assert(clue(out).contains("extends scala.math.Ordered[Item]"))
    assert(out.contains("def compareTo("))
  }
