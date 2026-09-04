package balticporter.transform

import balticporter.core.{PortManifest, SurfaceFold}

/** MethodBodyTransform's MergeablePolicy — the fix for `ENGINE-LIMITS.md` D9 at this phase. */
class MethodBodyTransformMergeSpec extends munit.FunSuite:

  private def mbt(entries: (String, String)*) = new MethodBodyTransform(entries.toMap)

  private def base(surface: List[balticporter.tir.Phase]) =
    PortManifest("base", governs = Set("com.demo"), surface = surface)

  // ---- positive: independent keys union ----

  test("independent keys from base and dependent merge into one instance") {
    val b = base(List(mbt("com.demo.A#foo" -> "1 + 1")))
    val dep = b.extendedBy(PortManifest("dep", surface = List(mbt("com.dep.B#bar" -> "2 + 2"))))

    assertEquals(dep.surfaceFold.refusals, Nil)
    val eff = dep.effectiveSurface.collect { case t: MethodBodyTransform => t }
    assertEquals(clue(eff.size), 1)
    assertEquals(eff.head.bodies, Map("com.demo.A#foo" -> "1 + 1", "com.dep.B#bar" -> "2 + 2"))
  }

  test("same key with identical body text is accepted silently (idempotent restatement)") {
    val b = base(List(mbt("com.demo.A#foo" -> "same body")))
    val dep = b.extendedBy(PortManifest("dep", surface = List(mbt("com.demo.A#foo" -> "same body"))))

    assertEquals(dep.surfaceFold.refusals, Nil)
    val eff = dep.effectiveSurface.collect { case t: MethodBodyTransform => t }
    assertEquals(clue(eff.size), 1)
    assertEquals(eff.head.bodies.size, 1)
  }

  // ---- negative: same key with different body refuses ----

  test("same key with different body text refuses — the two are a conflict only a human resolves") {
    val b = base(List(mbt("com.demo.A#foo" -> "body1")))
    val dep = b.extendedBy(PortManifest("dep", surface = List(mbt("com.demo.A#foo" -> "body2"))))

    assert(clue(dep.surfaceFold.refusals).nonEmpty)
    assert(dep.surfaceFold.refusals.head.cause == SurfaceFold.Cause.Conflict)
    // two instances stay in the pipeline
    val eff = dep.effectiveSurface.collect { case t: MethodBodyTransform => t }
    assertEquals(clue(eff.size), 2)
  }

  // ---- mergedWith returns correct subjects ----

  test("mergedWith reports the ADDED subjects from the later instance") {
    val a = mbt("com.demo.A#foo" -> "1")
    val b = mbt("com.dep.B#bar" -> "2", "com.dep.C#baz" -> "3")

    val result = a.mergedWith(b)
    assert(result.isRight)
    val merged = result.toOption.get
    assertEquals(merged.added, Set("com.dep.B", "com.dep.C"))
  }

  test("mergedWith with no new subjects reports empty added set") {
    val a = mbt("com.demo.A#foo" -> "1")
    val b = mbt("com.demo.A#foo" -> "1") // same key, same body

    val result = a.mergedWith(b)
    assert(result.isRight)
    assertEquals(result.toOption.get.added, Set.empty[String])
  }

  // ---- subjects reports all keys ----

  test("subjects extracts leading FQN from all keys") {
    val t = mbt("com.demo.A#foo" -> "1", "com.demo.A#bar(int)" -> "2", "com.dep.B#baz" -> "3")
    assertEquals(t.subjects, Set("com.demo.A", "com.dep.B"))
  }

  // ---- empty is a no-op ----

  test("empty MethodBodyTransform merges with any other without conflict") {
    val empty = mbt()
    val full = mbt("com.demo.A#foo" -> "1")

    val result = empty.mergedWith(full)
    assert(result.isRight)
    assertEquals(result.toOption.get.phase.asInstanceOf[MethodBodyTransform].bodies,
      Map("com.demo.A#foo" -> "1"))
  }

  // ---- wrong phase type refuses ----

  test("mergedWith a non-MethodBodyTransform refuses") {
    val t = mbt("com.demo.A#foo" -> "1")
    assert(t.mergedWith(new balticporter.transform.MutableParamsTransform).isLeft)
  }

  // ---- the real shape: base with retarget bodies + dependent with its own body ----

  test("ashley shape: base with AssetManager bodies + dependent with Engine.createComponent") {
    val baseBodies = mbt(
      "com.demo.AssetManager#clear" -> "{ this.finish() }",
      "com.demo.AssetManager#getAssetFileName" -> "{ null }",
    )
    val depBodies = mbt(
      "com.dep.Engine#createComponent(Class)" -> "lowlevel.Nullable(factory.create(componentType))",
    )
    val b = base(List(baseBodies))
    val dep = b.extendedBy(PortManifest("dep", governs = Set("com.dep"), surface = List(depBodies)))

    assertEquals(dep.surfaceFold.refusals, Nil)
    val eff = dep.effectiveSurface.collect { case t: MethodBodyTransform => t }
    assertEquals(clue(eff.size), 1)
    assertEquals(eff.head.bodies.size, 3)
    assertEquals(eff.head.bodies("com.dep.Engine#createComponent(Class)"),
      "lowlevel.Nullable(factory.create(componentType))")
    assertEquals(eff.head.bodies("com.demo.AssetManager#clear"), "{ this.finish() }")
  }
