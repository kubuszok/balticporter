package balticporter.transform

import balticporter.tir.RuleScope

/** The `families` parameter on [[CollectionsTransform]]: additional collection families added
  * alongside the JDK defaults. Tests the collision check, fingerprinting, `MergeablePolicy`
  * composition, and per-entry scopes (D12). */
class CollectionsFamiliesSpec extends munit.FunSuite {

  import CollectionsTransform.Kind

  // ---- COLLISION CHECK ----

  test("a family key that collides with a JDK typeMap entry is refused at construction") {
    val ex = intercept[IllegalArgumentException] {
      new CollectionsTransform(
        families = Map("java.util.List" -> ("scala.collection.mutable.Buffer", Kind.Seq)))
    }
    assert(clue(ex.getMessage).contains("java.util.List"))
    assert(ex.getMessage.contains("JDK"))
  }

  test("a family key that collides with a retarget entry is refused at construction") {
    val ex = intercept[IllegalArgumentException] {
      new CollectionsTransform(
        retarget = Map("com.example.Foo" -> "com.example.Bar"),
        families = Map("com.example.Foo" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    }
    assert(clue(ex.getMessage).contains("com.example.Foo"))
    assert(ex.getMessage.contains("retarget"))
  }

  test("disjoint families and retarget entries are accepted") {
    val ct = new CollectionsTransform(
      retarget = Map("com.example.Foo" -> "com.example.Bar"),
      families = Map("com.example.Baz" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assert(ct.mappedTypes.contains("com.example.Baz"))
    assert(!ct.mappedTypes.contains("com.example.Foo")) // retarget is NOT in mappedTypes
  }

  // ---- typeMap MERGING ----

  test("families entries appear in mappedTypes alongside JDK entries") {
    val ct = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assert(ct.mappedTypes.contains("com.lib.Array"))
    assert(ct.mappedTypes.contains("java.util.List")) // JDK still present
  }

  test("targetOf returns the family's target for a family entry") {
    val ct = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assertEquals(ct.targetOf("com.lib.Array"), "scala.collection.mutable.ArrayBuffer")
  }

  test("retypedTargets includes family targets") {
    val ct = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assert(ct.retypedTargets.contains("scala.collection.mutable.ArrayBuffer"))
  }

  // ---- FINGERPRINTING ----

  test("an empty families parameter does not change the fingerprint") {
    val base = new CollectionsTransform()
    val withEmpty = new CollectionsTransform(families = Map.empty)
    assertEquals(base.surfaceFingerprint, withEmpty.surfaceFingerprint)
  }

  test("a non-empty families parameter adds a families= segment to the fingerprint") {
    val base = new CollectionsTransform()
    val withFamilies = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assert(clue(withFamilies.surfaceFingerprint).contains("families="))
    assert(!base.surfaceFingerprint.contains("families="))
  }

  test("two instances with the same families have the same fingerprint") {
    val a = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val b = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  test("different families produce different fingerprints") {
    val a = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val b = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.HashMap", Kind.Map)))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  // ---- PER-ENTRY SCOPES (D12) ----

  test("familyScopeOf defaults to Everywhere when no scope is declared") {
    val ct = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assertEquals(ct.familyScopeOf("com.lib.Array"), RuleScope.everywhere)
  }

  test("familyScopeOf returns the declared scope for a family entry") {
    val sc = RuleScope.Only(Set("com.lib.pkg"))
    val ct = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)),
      familyScopes = Map("com.lib.Array" -> sc))
    assertEquals(ct.familyScopeOf("com.lib.Array"), sc)
  }

  test("a different scope on the same family produces a different fingerprint") {
    val a = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)),
      familyScopes = Map("com.lib.Array" -> RuleScope.Only(Set("com.lib.pkg1"))))
    val b = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)),
      familyScopes = Map("com.lib.Array" -> RuleScope.Only(Set("com.lib.pkg2"))))
    assertNotEquals(a.surfaceFingerprint, b.surfaceFingerprint)
  }

  // ---- MergeablePolicy ----

  test("subjects includes family source FQNs") {
    val ct = new CollectionsTransform(
      families = Map("com.lib.Array" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    assert(ct.subjects.contains("com.lib.Array"))
  }

  test("subjects includes retarget source FQNs") {
    val ct = new CollectionsTransform(
      retarget = Map("com.lib.Foo" -> "com.lib.Bar"))
    assert(ct.subjects.contains("com.lib.Foo"))
  }

  test("subjects does NOT include JDK entries") {
    val ct = new CollectionsTransform()
    assert(!ct.subjects.contains("java.util.List"))
  }

  test("mergedWith unions disjoint families") {
    val base = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val dep = new CollectionsTransform(
      families = Map("com.lib.B" -> ("scala.collection.mutable.HashMap", Kind.Map)))
    val result = base.mergedWith(dep)
    assert(result.isRight, s"merge failed: ${result.left.getOrElse("")}")
    val merged = result.toOption.get
    val phase = merged.phase.asInstanceOf[CollectionsTransform]
    assert(phase.mappedTypes.contains("com.lib.A"))
    assert(phase.mappedTypes.contains("com.lib.B"))
    assertEquals(merged.added, Set("com.lib.B"))
  }

  test("mergedWith unions two `Only` scopes — a dependent widens its base's scope onto its own entry (K43)") {
    val base = new CollectionsTransform(scope = RuleScope.Only(Set("com.lib.base")))
    val dep  = new CollectionsTransform(scope = RuleScope.Only(Set("com.lib.dep")))
    val merged = base.mergedWith(dep).toOption.get.phase.asInstanceOf[CollectionsTransform]
    assertEquals(merged.scope, RuleScope.Only(Set("com.lib.base", "com.lib.dep")))
  }

  test("mergedWith refuses `Only` against `Everywhere`") {
    val base = new CollectionsTransform(scope = RuleScope.Only(Set("com.lib.base")))
    val dep  = new CollectionsTransform(scope = RuleScope.Everywhere(Set.empty))
    assert(base.mergedWith(dep).isLeft)
  }

  test("mergedWith refuses same source with different target") {
    val base = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val dep = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.HashMap", Kind.Map)))
    val result = base.mergedWith(dep)
    assert(result.isLeft, "merge should have been refused")
    assert(clue(result.left.getOrElse("")).contains("com.lib.A"))
  }

  test("mergedWith accepts same source with same target") {
    val base = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val dep = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val result = base.mergedWith(dep)
    assert(result.isRight, s"merge failed: ${result.left.getOrElse("")}")
    assertEquals(result.toOption.get.added, Set.empty[String]) // same key, nothing added
  }

  test("mergedWith refuses a cross-clash between families and retarget") {
    val base = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val dep = new CollectionsTransform(
      retarget = Map("com.lib.A" -> "scala.collection.mutable.HashMap"))
    val result = base.mergedWith(dep)
    assert(result.isLeft, "merge should have been refused for cross-clash")
    assert(clue(result.left.getOrElse("")).contains("com.lib.A"))
  }

  test("mergedWith refuses scope disagreement on the same family") {
    val base = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)),
      familyScopes = Map("com.lib.A" -> RuleScope.Only(Set("com.base.pkg"))))
    val dep = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)),
      familyScopes = Map("com.lib.A" -> RuleScope.Only(Set("com.dep.pkg"))))
    val result = base.mergedWith(dep)
    assert(result.isLeft, "merge should have been refused for scope disagreement")
    assert(clue(result.left.getOrElse("")).contains("scope"))
  }

  test("mergedWith unions disjoint retargets") {
    val base = new CollectionsTransform(
      retarget = Map("com.lib.A" -> "com.target.A"))
    val dep = new CollectionsTransform(
      retarget = Map("com.lib.B" -> "com.target.B"))
    val result = base.mergedWith(dep)
    assert(result.isRight, s"merge failed: ${result.left.getOrElse("")}")
  }

  test("mergedWith refuses a non-CollectionsTransform phase") {
    val ct = new CollectionsTransform()
    val other = new balticporter.transform.MemberRenameTransform()
    val result = ct.mergedWith(other)
    assert(result.isLeft)
    assert(clue(result.left.getOrElse("")).contains("not a `CollectionsTransform`"))
  }

  test("merged phase preserves JDK entries alongside families from both sides") {
    val base = new CollectionsTransform(
      families = Map("com.lib.A" -> ("scala.collection.mutable.ArrayBuffer", Kind.Seq)))
    val dep = new CollectionsTransform(
      families = Map("com.lib.B" -> ("scala.collection.mutable.HashMap", Kind.Map)))
    val merged = base.mergedWith(dep).toOption.get.phase.asInstanceOf[CollectionsTransform]
    // JDK entries still present
    assert(merged.mappedTypes.contains("java.util.List"))
    assert(merged.mappedTypes.contains("java.util.Map"))
    // Both families present
    assert(merged.mappedTypes.contains("com.lib.A"))
    assert(merged.mappedTypes.contains("com.lib.B"))
  }
}
