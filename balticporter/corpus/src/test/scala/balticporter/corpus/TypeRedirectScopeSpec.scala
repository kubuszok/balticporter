package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{ Decision, RuleScope }
import balticporter.transform.TypeRedirectTransform

/** `TypeRedirectTransform` RETYPES declarations, so a retyping phase owes it a `RuleScope` — and this suite is what says the scope is real rather than a constructor parameter nobody reads.
  */
class TypeRedirectScopeSpec extends PortSuite:

  private val sources = List(
    "com/base/Holder.java" ->
      """package com.base;
        |import com.demo.Slot;
        |public class Holder {
        |  public Slot slot;
        |  public Slot get() { return slot; }
        |}
        |""".stripMargin,
    "com/demo/Slot.java" ->
      """package com.demo;
        |public class Slot {}
        |""".stripMargin,
    "com/demo/Uses.java" ->
      """package com.demo;
        |public class Uses {
        |  public Slot mine;
        |  public Slot pick() { return mine; }
        |}
        |""".stripMargin
  )

  private def redirect(scope: RuleScope) = new TypeRedirectTransform(
    redirects = Map("com.demo.Slot" -> "com.demo.Replacement"),
    scopes = if scope.isUnrestricted then Map.empty else Map("com.demo.Slot" -> scope)
  )

  test("UNSCOPED, the redirect reaches the OTHER module's declarations — the pre-scope behaviour") {
    // Stated as a positive rather than left implicit: this is the behaviour every port that
    // predates the parameter has, and the default must keep producing it exactly.
    val p = portAll(sources, redirect(RuleScope.everywhere))
    assertEmits(p, "com.demo.Replacement")
    assertNotEmits(p, "com.demo.Slot")
  }

  test("SCOPED to one package, a declaration OUTSIDE it keeps the original type") {
    val p = portAll(sources, redirect(RuleScope.Only(Set("com.demo"))))
    // the dependent's own declaration moved…
    assertEmits(p, "var mine: com.demo.Replacement")
    // …and the other module's did not, which is the whole point: its signature is a fact its own
    // port already published and this run may not re-derive a different one.
    assertEmits(p, "var slot: com.demo.Slot")
  }

  test("…and a DECISION is recorded only for the declarations that really moved") {
    // A `RetypedSignature` row for a declaration the scope held back would claim a signature change
    // that did not happen, and its porter note would say so in the emitted file — which is worse
    // than no note, and is what `NoteCoverageCheck` fails a run for in either direction.
    val p = portAll(sources, redirect(RuleScope.Only(Set("com.demo"))))
    assertDecides(p, Decision.Kind.RetypedSignature, "com.demo.Uses")
    assertNotDecides(p, Decision.Kind.RetypedSignature, "com.base.Holder")
  }

  test("an entry with no scope is unrestricted — a scope is PER ENTRY, so the two coexist") {
    // THE SHAPE A MERGE PRODUCES: a base states a whole-program redirect and a dependent states a
    // package-scoped one, `surfaceFold` folds them into ONE phase, and a single scope on that phase
    // could not serve both. Keyed by the redirect source they simply do not interact.
    val p = portAll(
      sources,
      new TypeRedirectTransform(
        redirects = Map("com.demo.Slot" -> "com.demo.Replacement", "com.base.Holder" -> "com.demo.Bag"),
        scopes = Map("com.demo.Slot" -> RuleScope.Only(Set("com.demo")))
      )
    )
    assertEmits(p, "var mine: com.demo.Replacement") // scoped entry, inside its scope
    assertEmits(p, "var slot: com.demo.Slot") // scoped entry, outside it
    assertNotEmits(p, "com.base.Holder") // unscoped entry, everywhere
  }

  test("the two instances MERGE where they scope different sources, and REFUSE the same one") {
    val base      = new TypeRedirectTransform(Map("com.base.Holder" -> "com.demo.Bag"))
    val dependent = new TypeRedirectTransform(redirects = Map("com.demo.Slot" -> "com.demo.Replacement"), scopes = Map("com.demo.Slot" -> RuleScope.Only(Set("com.demo"))))
    // different sources — nothing to disagree about, and reading the base's ABSENT entry as
    // "everywhere" would have reported a conflict between a scope and a redirect that does not exist
    assert(clue(base.mergedWith(dependent)).isRight)
    // the same source, two scopes — the refusal, because a scope does not compose
    val rival = new TypeRedirectTransform(redirects = Map("com.demo.Slot" -> "com.demo.Replacement"), scopes = Map("com.demo.Slot" -> RuleScope.Only(Set("com.other"))))
    assert(clue(dependent.mergedWith(rival)).isLeft)
  }

  test("the scope is part of the SURFACE fingerprint — two modules scoping differently are not equal") {
    val unscoped = new TypeRedirectTransform(Map("com.demo.Slot" -> "com.demo.Replacement"))
    val scoped   = new TypeRedirectTransform(redirects = Map("com.demo.Slot" -> "com.demo.Replacement"), scopes = Map("com.demo.Slot" -> RuleScope.Only(Set("com.demo"))))
    assertNotEquals(scoped.surfaceFingerprint, unscoped.surfaceFingerprint)
    // …and a port that states NO scope fingerprints exactly as it did before the parameter existed,
    // or every published port map moves for a change that shifted no signature.
    assertEquals(unscoped.surfaceFingerprint, "com.demo.Slot->com.demo.Replacement")
  }

  test("a scope entry that names nothing is REPORTED, not silently ignored") {
    val phase = new TypeRedirectTransform(redirects = Map("com.demo.Slot" -> "com.demo.Replacement"), scopes = Map("com.demo.Slot" -> RuleScope.Only(Set("com.absent"))))
    portAll(sources, phase)
    val findings = phase.policyReport.findings
    assertEquals(clue(findings).size, 1)
    assertEquals(findings.head.key, "com.absent")
  }

  // -------------------------------------------------------------------------------------------
  // A scoped redirect of a base-emitted type retypes the DEPENDENT's own fields and call sites
  // while leaving the base's declarations untouched — the shape a dependent module's own policy
  // needs (e.g. re-pointing a base's bit-set type at the stdlib's BitSet).
  // -------------------------------------------------------------------------------------------

  /** A base type with `get(int)`, `set(int)`, `isEmpty()` — the shape of a bit-set or similar container that a dependent might want to redirect to a stdlib type.
    */
  private val baseSources = List(
    "com/base/Bits.java" ->
      """package com.base;
        |public class Bits {
        |  public boolean get(int i) { return false; }
        |  public void set(int i) {}
        |  public boolean isEmpty() { return true; }
        |}
        |""".stripMargin,
    "com/dep/Entity.java" ->
      """package com.dep;
        |import com.base.Bits;
        |public class Entity {
        |  public Bits componentBits = new Bits();
        |  public Bits getComponentBits() { return componentBits; }
        |  public boolean has(int idx) { return componentBits.get(idx); }
        |}
        |""".stripMargin
  )

  test("a SCOPED redirect retypes the dependent's own fields and accessors, leaves the base's type declaration unchanged") {
    val phase = new TypeRedirectTransform(
      redirects = Map("com.base.Bits" -> "scala.collection.mutable.BitSet"),
      memberRenames = Map("com.base.Bits" -> Map("get" -> "contains")),
      scopes = Map("com.base.Bits" -> RuleScope.Only(Set("com.dep")))
    )
    val p = portAll(baseSources, phase)
    // the dependent's own field and accessor use the redirect target
    assertEmits(p, "var componentBits: scala.collection.mutable.BitSet")
    assertEmits(p, "def getComponentBits(): scala.collection.mutable.BitSet")
    // the base's own TYPE declaration is untouched — it still declares `class Bits`, not BitSet.
    // The redirect is scoped to `com.dep`, so `com.base.Bits`'s type references stay as `Bits`.
    assertEmits(p, "class Bits")
    // The member rename fires on the override COMPONENT program-wide (the base's `get` is the
    // declaration site), which is correct: in a dependent's run the rename precedes the redirect
    // and applies to the whole component, then the redirect only moves types within the scope.
    // The base's own independent run would not have this rename — that is why the rename is
    // per-run, and the TYPE redirect is what the scope governs.
    assertEmits(p, "def contains(")
    assertEmits(p, "def set(")
    assertEmits(p, "def isEmpty(")
  }

  test("a scoped redirect with memberRenames rewrites call sites inside the scope") {
    val phase = new TypeRedirectTransform(
      redirects = Map("com.base.Bits" -> "scala.collection.mutable.BitSet"),
      memberRenames = Map("com.base.Bits" -> Map("get" -> "contains")),
      scopes = Map("com.base.Bits" -> RuleScope.Only(Set("com.dep")))
    )
    val p = portAll(baseSources, phase)
    // the member rename fires within the scope: `componentBits.get(idx)` becomes `.contains(idx)`
    assertEmits(p, ".contains(")
    // decisions are recorded only for declarations in the scope
    assertDecides(p, Decision.Kind.RetypedSignature, "com.dep.Entity")
    assertNotDecides(p, Decision.Kind.RetypedSignature, "com.base.Bits")
  }

  test("`subjectScope` returns the entry's scope from `scopeOf`") {
    val phase = new TypeRedirectTransform(
      redirects = Map("com.base.Bits" -> "scala.collection.mutable.BitSet"),
      scopes = Map("com.base.Bits" -> RuleScope.Only(Set("com.dep")))
    )
    assertEquals(phase.subjectScope("com.base.Bits"), RuleScope.Only(Set("com.dep")))
    // an entry with no declared scope returns everywhere
    assertEquals(phase.subjectScope("com.other.X"), RuleScope.everywhere)
  }
