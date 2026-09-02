package balticporter.transform

/** [[RetargetBoundaryCheck]] — the Issue enum and its classification.
  *
  * Integration coverage lives in `ComparatorOrderingPortSpec` (corpus); this spec covers the
  * structural properties a unit test can see without a frontend.
  */
class RetargetBoundaryCheckSpec extends munit.FunSuite:

  // ---- Issue.classification covers every variant ----

  test("every Issue variant has a non-empty classification") {
    for v <- RetargetBoundaryCheck.Issue.values do
      assert(clue(RetargetBoundaryCheck.Issue.classification(v)).nonEmpty,
        s"Issue.$v has no classification")
  }

  test("IteratorRemove classification mentions ENGINE-LIMITS") {
    val c = RetargetBoundaryCheck.Issue.classification(RetargetBoundaryCheck.Issue.IteratorRemove)
    assert(clue(c).contains("ENGINE-LIMITS"), "IteratorRemove classification should cite ENGINE-LIMITS")
    assert(c.contains("UnsupportedOperationException"), "IteratorRemove should name the exception")
  }

  test("IteratorRemove classification mentions the approach -- removing iterator over the collection") {
    val c = RetargetBoundaryCheck.Issue.classification(RetargetBoundaryCheck.Issue.IteratorRemove)
    assert(clue(c).contains("removing iterator"), "IteratorRemove should name the approach")
  }

  // ---- empty retargeted map is a no-op by arithmetic (§1(a)) ----

  test("check with empty retargeted map returns Nil") {
    // RetargetBoundaryCheck.check short-circuits on `retargeted.isEmpty` before reading units.
    assertEquals(RetargetBoundaryCheck.check(null: balticporter.tir.Program, Nil, Map.empty), Nil)
  }

  // ---- Finding.report renders IteratorRemove ----

  test("Finding with IteratorRemove renders its issue name") {
    import balticporter.tir.*
    val f = RetargetBoundaryCheck.Finding(
      RetargetBoundaryCheck.Issue.IteratorRemove,
      "iterator remove",
      "com.example.Queue",
      "scala.collection.mutable.ArrayDeque",
      Origin.synthetic,
      SymId.None)
    assert(clue(f.render).contains("IteratorRemove"))
    assert(f.detail.contains("iterator remove"))
  }
