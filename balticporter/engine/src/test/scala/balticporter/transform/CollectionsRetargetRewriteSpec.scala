package balticporter.transform

/** The `retargetRewrites` parameter on [[CollectionsTransform]]: per-retarget member rewrites.
  * Tests the construction-time assertions, fingerprinting, and `MergeablePolicy` composition. */
class CollectionsRetargetRewriteSpec extends munit.FunSuite {

  import CollectionsTransform.RetargetRewrite
  import CollectionsTransform.RetargetRewrite.*

  // ---- CONSTRUCTION-TIME ASSERTIONS ----

  test("a retargetRewrites key with no matching retarget entry is refused") {
    val ex = intercept[IllegalArgumentException] {
      new CollectionsTransform(
        retargetRewrites = Map("com.example.Bits" -> Map(("get", 1) -> Rename("apply"))))
    }
    assert(clue(ex.getMessage).contains("com.example.Bits"))
    assert(ex.getMessage.contains("no matching retarget entry"))
  }

  test("a retargetRewrites key with a matching retarget entry is accepted") {
    val ct = new CollectionsTransform(
      retarget = Map("com.example.Bits" -> "scala.collection.mutable.BitSet"),
      retargetRewrites = Map("com.example.Bits" -> Map(("get", 1) -> Rename("apply"))))
    assertEquals(ct.retargetedTypes.size, 1)
  }

  test("empty retargetRewrites is accepted with or without retarget") {
    val ct1 = new CollectionsTransform()
    val ct2 = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"))
    // both should construct without error
    assert(ct1.retargetedTypes.isEmpty)
    assert(ct2.retargetedTypes.size == 1)
  }

  // ---- FINGERPRINTING ----

  test("empty retargetRewrites does not change the fingerprint") {
    val base = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"))
    val withEmpty = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map.empty)
    assertEquals(base.surfaceFingerprint, withEmpty.surfaceFingerprint)
  }

  test("non-empty retargetRewrites adds a retargetRewrites= segment") {
    val base = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"))
    val withRewrites = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(("get", 1) -> Rename("apply"))))
    assert(clue(withRewrites.surfaceFingerprint).contains("retargetRewrites="))
    assert(!base.surfaceFingerprint.contains("retargetRewrites="))
  }

  test("two instances with the same retargetRewrites have the same fingerprint") {
    val a = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(("get", 1) -> Rename("apply"))))
    val b = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(("get", 1) -> Rename("apply"))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("different retargetRewrites produce different fingerprints") {
    val a = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(("get", 1) -> Rename("apply"))))
    val b = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(("get", 1) -> Rename("head"))))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  // ---- MergeablePolicy ----

  test("mergeWith unions independent retargetRewrites sources") {
    val base = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X", "com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.a.X" -> Map(("get", 1) -> Rename("apply"))))
    val dep = new CollectionsTransform(
      retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(("set", 1) -> Rename("update"))))
    val merged = base.mergedWith(dep)
    assert(clue(merged).isRight)
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assert(ct.surfaceFingerprint.contains("retargetRewrites="))
  }

  test("mergeWith refuses same source with different rewrite tables") {
    val base = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("get", 1) -> Rename("apply"))))
    val dep = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("get", 1) -> Rename("head"))))
    val merged = base.mergedWith(dep)
    assert(clue(merged).isLeft)
    assert(merged.swap.toOption.get.contains("com.a.X"))
  }

  test("mergeWith accepts same source with identical rewrite tables") {
    val table = Map(("get", 1) -> Rename("apply"))
    val base = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> table))
    val dep = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> table))
    val merged = base.mergedWith(dep)
    assert(clue(merged).isRight)
  }

  // ---- RetargetRewrite ADT ----

  test("Rename toString is readable") {
    val r: RetargetRewrite = Rename("apply")
    assert(r.toString.contains("apply"))
  }

  test("BoolDispatch toString is readable") {
    val r: RetargetRewrite = BoolDispatch(1, "removeByRef", "removeByValue")
    assert(r.toString.contains("removeByRef"))
  }

  test("Construct toString is readable") {
    val r: RetargetRewrite = Construct("lowlevel.util.ObjectMap", "apply")
    assert(r.toString.contains("ObjectMap"))
    assert(r.toString.contains("apply"))
  }

  // ---- Construct fingerprinting ----

  test("Construct changes the fingerprint") {
    val base = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"))
    val withConstruct = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 0) -> Construct("com.example.Bar", "apply"))))
    assert(clue(withConstruct.surfaceFingerprint).contains("retargetRewrites="))
    assertNotEquals(base.surfaceFingerprint, withConstruct.surfaceFingerprint)
  }

  test("Construct with same values has the same fingerprint") {
    val a = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 0) -> Construct("com.example.Bar", "apply"))))
    val b = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 0) -> Construct("com.example.Bar", "apply"))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("different Construct factory methods produce different fingerprints") {
    val a = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 0) -> Construct("com.example.Bar", "apply"))))
    val b = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 0) -> Construct("com.example.Bar", "from"))))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  // ---- Construct merging ----

  test("mergeWith unions Construct entries from independent sources") {
    val base = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X", "com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.a.X" -> Map(("get", 1) -> Rename("apply"))))
    val dep = new CollectionsTransform(
      retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(
        ("<init>", 0) -> Construct("scala.Y", "apply"))))
    val merged = base.mergedWith(dep)
    assert(clue(merged).isRight)
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assert(ct.retargetRewrites.contains("com.a.X"))
    assert(ct.retargetRewrites.contains("com.b.Y"))
    assertEquals(ct.retargetRewrites("com.b.Y")(("<init>", 0)),
      Construct("scala.Y", "apply"))
  }

  // ---- subjects: retargetRewrites keys are covered by retarget keys ----

  test("subjects includes retarget keys but not retargetRewrites separately") {
    val ct = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("get", 1) -> Rename("apply"))))
    // retargetRewrites keys are a SUBSET of retarget keys by the construction-time assertion,
    // so subjects already covers them through the retarget entry.
    assert(ct.subjects.nonEmpty)
  }

  // ---- ForEach variant ----

  test("ForEach toString is readable") {
    assertEquals(ForEach("foreachEntry", 2).toString,
      "ForEach(foreachEntry,2)")
  }

  test("ForEach changes the fingerprint") {
    val base = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"))
    val withFE = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("entries", 0) -> ForEach("foreachEntry", 2))))
    assertNotEquals(base.surfaceFingerprint, withFE.surfaceFingerprint)
  }

  test("ForEach with same values has the same fingerprint") {
    val a = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("entries", 0) -> ForEach("foreachEntry", 2))))
    val b = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("entries", 0) -> ForEach("foreachEntry", 2))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("different ForEach target methods produce different fingerprints") {
    val a = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("entries", 0) -> ForEach("foreachEntry", 2))))
    val b = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("entries", 0) -> ForEach("foreachKey", 1))))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("mergeWith unions ForEach entries from independent sources") {
    val base = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X", "com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("entries", 0) -> ForEach("foreachEntry", 2))))
    val dep = new CollectionsTransform(
      retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(
        ("keys", 0) -> ForEach("foreachKey", 1))))
    val merged = base.mergedWith(dep)
    assert(clue(merged).isRight)
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assert(ct.retargetRewrites.contains("com.a.X"))
    assert(ct.retargetRewrites.contains("com.b.Y"))
    assertEquals(ct.retargetRewrites("com.a.X")(("entries", 0)),
      ForEach("foreachEntry", 2))
    assertEquals(ct.retargetRewrites("com.b.Y")(("keys", 0)),
      ForEach("foreachKey", 1))
  }
}
