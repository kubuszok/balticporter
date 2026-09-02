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

  // ---- Construct.dropTrailing ----

  test("Construct with dropTrailing=0 has the same fingerprint as Construct without it") {
    val a = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 0) -> Construct("com.example.Bar", "apply"))))
    val b = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 0) -> Construct("com.example.Bar", "apply", dropTrailing = 0))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("Construct with dropTrailing>0 changes the fingerprint") {
    val a = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 4) -> Construct("com.example.Bar", "apply"))))
    val b = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      retargetRewrites = Map("com.example.Foo" -> Map(
        ("<init>", 4) -> Construct("com.example.Bar", "apply", dropTrailing = 2))))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("Construct toString includes dropTrailing when non-zero") {
    val r = Construct("lowlevel.util.ArrayMap", "apply", dropTrailing = 2)
    assert(r.toString.contains("2"))
  }

  // ---- forEach pool: monotonic, no wrap (M10 shape) ----

  test("ForEach pool pre-allocates 64 entries to prevent modular wrap") {
    // The pool was 8 with `forEachSeq % 8`; at nesting > 8 the names wrapped and the inner
    // lambda silently shadowed the outer's captures (M10's shape). Now the pool is 64 with a
    // require guard instead of wrap.
    val ct = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("entries", 0) -> ForEach("foreachEntry", 2))))
    assert(ct.retargetRewrites.nonEmpty)
  }

  test("mergeWith unions Construct entries with dropTrailing from independent sources") {
    val base = new CollectionsTransform(
      retarget = Map("com.a.X" -> "scala.X", "com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("<init>", 0) -> Construct("scala.X", "apply"))))
    val dep = new CollectionsTransform(
      retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(
        ("<init>", 4) -> Construct("scala.Y", "apply", dropTrailing = 2))))
    val merged = base.mergedWith(dep)
    assert(clue(merged).isRight)
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assertEquals(ct.retargetRewrites("com.b.Y")(("<init>", 4)),
      Construct("scala.Y", "apply", dropTrailing = 2))
  }

  // ---- Collect ----

  test("Collect toString is readable") {
    assertEquals(Collect("foreachKey", "lowlevel.util.DynamicArray").toString,
      "Collect(foreachKey,lowlevel.util.DynamicArray)")
  }

  test("Collect changes the fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("keys", 0) -> Collect("foreachKey", "lowlevel.util.DynamicArray"))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("Collect with same values has the same fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("keys", 0) -> Collect("foreachKey", "lowlevel.util.DynamicArray"))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("keys", 0) -> Collect("foreachKey", "lowlevel.util.DynamicArray"))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("mergeWith unions Collect entries from independent sources") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("keys", 0) -> Collect("foreachKey", "lowlevel.util.DynamicArray"))))
    val b = new CollectionsTransform(retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(("keys", 0) -> Collect("foreachKey", "lowlevel.util.DynamicArray"))))
    val merged = a.mergedWith(b)
    assert(merged.isRight, s"merge refused: ${merged.left.getOrElse("")}")
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assertEquals(ct.retargetRewrites("com.a.X")(("keys", 0)), Collect("foreachKey", "lowlevel.util.DynamicArray"))
    assertEquals(ct.retargetRewrites("com.b.Y")(("keys", 0)), Collect("foreachKey", "lowlevel.util.DynamicArray"))
  }

  // ---- Chain ----

  test("Chain toString is readable") {
    assertEquals(Chain(List("orderedItems", "iterator")).toString, "Chain(List(orderedItems, iterator),Set(),false)")
  }

  test("Chain changes the fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("iterator", 0) -> Chain(List("orderedItems", "iterator")))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("Chain with same values has the same fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("iterator", 0) -> Chain(List("orderedItems", "iterator")))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("iterator", 0) -> Chain(List("orderedItems", "iterator")))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("mergeWith unions Chain entries from independent sources") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("iterator", 0) -> Chain(List("orderedItems", "iterator")))))
    val b = new CollectionsTransform(retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(("values", 0) -> Chain(List("getValues")))))
    val merged = a.mergedWith(b)
    assert(merged.isRight, s"merge refused: ${merged.left.getOrElse("")}")
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assertEquals(ct.retargetRewrites("com.a.X")(("iterator", 0)), Chain(List("orderedItems", "iterator")))
    assertEquals(ct.retargetRewrites("com.b.Y")(("values", 0)), Chain(List("getValues")))
  }

  // ---- per-source minting: two sources, one target, distinct rewrite tables ----

  test("two sources sharing a target with DIFFERENT rewrite tables are accepted") {
    // Array and Queue both map to DynamicArray, but Array has BoolDispatch entries Queue does not.
    // Both must be accepted, and their fingerprints must include both tables.
    val ct = new CollectionsTransform(
      retarget = Map("com.a.Array" -> "lls.DynamicArray",
                     "com.a.Queue" -> "lls.DynamicArray"),
      retargetRewrites = Map(
        "com.a.Array" -> Map(
          ("get", 1)          -> Rename("apply"),
          ("removeValue", 2)  -> BoolDispatch(1, "removeValueByRef", "removeValue")),
        "com.a.Queue" -> Map(
          ("get", 1)      -> Rename("apply"),
          ("addLast", 1)  -> Rename("add"))))
    // construction succeeds: both sources accepted
    assertEquals(ct.retargetRewrites.size, 2)
    // fingerprint includes both rewrite tables
    assert(clue(ct.surfaceFingerprint).contains("retargetRewrites="))
  }

  test("two sources sharing a target with IDENTICAL rewrite tables are accepted") {
    val tbl = Map(("get", 1) -> Rename("apply"), ("notEmpty", 0) -> Chain(List("nonEmpty")))
    val ct = new CollectionsTransform(
      retarget = Map("com.a.X" -> "lls.DA", "com.a.Y" -> "lls.DA"),
      retargetRewrites = Map("com.a.X" -> tbl, "com.a.Y" -> tbl))
    assertEquals(ct.retargetRewrites.size, 2)
  }

  test("two sources sharing a target where only ONE has a rewrite table are accepted") {
    val ct = new CollectionsTransform(
      retarget = Map("com.a.X" -> "lls.DA", "com.a.Y" -> "lls.DA"),
      retargetRewrites = Map("com.a.X" -> Map(("get", 1) -> Rename("apply"))))
    assertEquals(ct.retargetRewrites.size, 1)
  }

  // ---- FieldWrite ----

  test("FieldWrite toString is readable") {
    assertEquals(FieldWrite("size", "setSize").toString, "FieldWrite(size,setSize)")
  }

  test("FieldWrite changes the fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("size", 0) -> FieldWrite("size", "setSize"))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("FieldWrite with same values has the same fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("size", 0) -> FieldWrite("size", "setSize"))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("size", 0) -> FieldWrite("size", "setSize"))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("FieldWrite can coexist with other variants at the SAME source") {
    // FieldWrite at ("size", 0) and Rename at ("get", 1) — different keys, same source
    val ct = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("size", 0) -> FieldWrite("size", "setSize"),
        ("get", 1)  -> Rename("apply"))))
    assertEquals(ct.retargetRewrites("com.a.X").size, 2)
  }

  test("mergeWith unions FieldWrite entries from independent sources") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("size", 0) -> FieldWrite("size", "setSize"))))
    val b = new CollectionsTransform(retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(("size", 0) -> FieldWrite("size", "clear"))))
    val merged = a.mergedWith(b)
    assert(merged.isRight, s"merge refused: ${merged.left.getOrElse("")}")
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assertEquals(ct.retargetRewrites("com.a.X")(("size", 0)), FieldWrite("size", "setSize"))
    assertEquals(ct.retargetRewrites("com.b.Y")(("size", 0)), FieldWrite("size", "clear"))
  }

  // ---- IndexedField ----

  test("IndexedField toString is readable") {
    assertEquals(IndexedField("items").toString, "IndexedField(items)")
  }

  test("IndexedField changes the fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("items", 0) -> IndexedField("items"))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("IndexedField with same values has the same fingerprint") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("items", 0) -> IndexedField("items"))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("items", 0) -> IndexedField("items"))))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("IndexedField can coexist with Rename and FieldWrite at the same source") {
    val ct = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("items", 0) -> IndexedField("items"),
        ("size", 0)  -> FieldWrite("size", "setSize"),
        ("get", 1)   -> Rename("apply"))))
    assertEquals(ct.retargetRewrites("com.a.X").size, 3)
  }

  test("mergeWith unions IndexedField entries from independent sources") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(("items", 0) -> IndexedField("items"))))
    val b = new CollectionsTransform(retarget = Map("com.b.Y" -> "scala.Y"),
      retargetRewrites = Map("com.b.Y" -> Map(("data", 0) -> IndexedField("data"))))
    val merged = a.mergedWith(b)
    assert(merged.isRight, s"merge refused: ${merged.left.getOrElse("")}")
    val ct = merged.toOption.get.phase.asInstanceOf[CollectionsTransform]
    assertEquals(ct.retargetRewrites("com.a.X")(("items", 0)), IndexedField("items"))
    assertEquals(ct.retargetRewrites("com.b.Y")(("data", 0)), IndexedField("data"))
  }

  // ---- Construct.dropTrailing: supplier-derived element type ----
  // The engine's retargetConstruct derives element types from a dropped supplier (MethodRef)
  // when the constructor type is raw or Object-applied; this is a §1(a) fact about raw types
  // and ArraySupplier. The spec tests construction-time properties only; the runtime derivation
  // is exercised through the SortTest/TextureAtlas end-to-end gate (gdx-test-measure).

  test("Construct with dropTrailing > 0 fingerprints differently from dropTrailing = 0") {
    val with0 = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("<init>", 3) -> Construct("scala.X", "apply", dropTrailing = 0))))
    val with1 = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("<init>", 3) -> Construct("scala.X", "apply", dropTrailing = 1))))
    assertNotEquals(with0.surfaceFingerprint, with1.surfaceFingerprint)
  }

  test("Construct.dropTrailing value is accessible for traceability") {
    val c = Construct("scala.X", "apply", dropTrailing = 2)
    assertEquals(c.dropTrailing, 2)
    // The toString includes the numeric value (position-based rendering).
    assert(clue(c.toString).contains("2"))
  }

  // ---- Template: slot-derived element type ----
  // A Template with $T0 resolves the type argument from the receiver's applied type at render
  // time. The spec verifies construction — runtime rendering is an integration concern.

  test("Template with $T0 at an <init> entry is accepted") {
    val ct = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("<init>", 1) -> Template("$Target.from[$T0]($0)"))))
    assertEquals(ct.retargetRewrites("com.a.X").size, 1)
  }

  test("Template fingerprint changes with the expression") {
    val a = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("<init>", 1) -> Template("$Target.from[$T0]($0)"))))
    val b = new CollectionsTransform(retarget = Map("com.a.X" -> "scala.X"),
      retargetRewrites = Map("com.a.X" -> Map(
        ("<init>", 1) -> Template("$Target.apply[$T0]($0)"))))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }
}
