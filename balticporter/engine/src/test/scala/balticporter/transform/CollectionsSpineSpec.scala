package balticporter.transform

import balticporter.tir.*

/** The one assumption `CollectionsTransform.restoreExcluded` makes about the traversal, asserted. */
class CollectionsSpineSpec extends munit.FunSuite:

  private val o  = Origin("X.java", 1, 1)
  private def st(n: Int): Statement = Tree.ValDef(SymId(n), TypeTree(TypeRepr.NoType, o), scala.None, o)

  test("equal lengths zip, position by position") {
    val a = List(st(1), st(2))
    val b = List(st(3), st(4))
    assertEquals(CollectionsTransform.spine(a, b, SymId(9)), List(a(0) -> b(0), a(1) -> b(1)))
  }

  test("a body that GREW under the traversal is fatal, and says why position-splicing needs the pair") {
    val e = intercept[IllegalStateException](CollectionsTransform.spine(List(st(1)), List(st(2), st(3)), SymId(9)))
    assert(clue(e.getMessage).contains("1 member(s) before, 2 after"))
    assert(e.getMessage.contains("BY POSITION"))
  }

  test("a body that SHRANK is fatal too — the direction that silently drops the tail") {
    val e = intercept[IllegalStateException](CollectionsTransform.spine(List(st(1), st(2)), List(st(3)), SymId(9)))
    assert(clue(e.getMessage).contains("2 member(s) before, 1 after"))
  }
