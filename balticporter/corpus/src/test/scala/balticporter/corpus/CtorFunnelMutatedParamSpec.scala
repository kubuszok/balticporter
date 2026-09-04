package balticporter.corpus

import balticporter.testkit.PortSuite

/** A PROMOTED constructor parameter the java constructor ASSIGNS TO. */
class CtorFunnelMutatedParamSpec extends PortSuite:

  test("a promoted parameter the constructor ASSIGNS TO is emitted `private var`") {
    val p = port("package p;\nclass C {\n  int f;\n  C(int x) { x = x * 2; this.f = x; }\n}\n")
    assertEmits(p, "(private var x$p: scala.Int)")
    assertEmits(p, "x$p = x$p * 2")
    assertEmits(p, "this.f = x$p")
  }

  test("…and one it only READS is untouched — the header does not gain a member for nothing") {
    // The narrowing is the point: rendered `var` unconditionally this would put a private field on
    // every promoted parameter in every port, which is a JVM shape change for a defect that fires
    // at the assignment alone.
    val p = port("package p;\nclass D {\n  int f;\n  D(int x) { this.f = x * 2; }\n}\n")
    assertEmits(p, "(x$p: scala.Int)")
    assertNotEmits(p, "var x$p")
  }

  test("…and only the parameter that is assigned, not its neighbours") {
    val p = port("package p;\nclass E {\n  int f; int g;\n  E(int x, int y) { x = x + 1; this.f = x; this.g = y; }\n}\n")
    assertEmits(p, "(private var x$p: scala.Int, y$p: scala.Int)")
  }

  test("a COMPOUND assignment and an INCREMENT are writes too — every one is a `Tree.Assign`") {
    // The frontend desugars `x *= 2`, `x++` and `--x` into `Tree.Assign`, which is what makes the
    // scan complete rather than a list of syntactic forms somebody remembered.
    val compound = port("package p;\nclass F {\n  int f;\n  F(int x) { x *= 2; this.f = x; }\n}\n")
    assertEmits(compound, "(private var x$p: scala.Int)")
    val incr = port("package p;\nclass G {\n  int f;\n  G(int x) { x++; this.f = x; }\n}\n")
    assertEmits(incr, "(private var x$p: scala.Int)")
  }

  test("a SECONDARY constructor's own parameter is a real local and needs nothing") {
    // Only the PROMOTED constructor's parameters become class parameters; a `def this(…)` keeps
    // scala's own method parameters, which are not vals in the same sense… and are still immutable,
    // so a secondary that assigns its parameter is a separate shape this does NOT claim to fix.
    // Asserted so the boundary is visible: the class parameter is the one that moved.
    val p = port("package p;\nclass H {\n  int f;\n  H(int x) { this.f = x; }\n  H() { this(1); }\n}\n")
    assertEmits(p, "(x$p: scala.Int)")
    assertNotEmits(p, "var x$p")
  }
