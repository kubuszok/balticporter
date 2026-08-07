package balticporter.corpus

import balticporter.testkit.PortSuite

/** THE ABSORBED-SILENTLY KINDS, PROBED — what each one really emits today.
  *
  * `SpoonKinds.Absence.AbsorbedSilently` is a SUSPICION and not a finding: it says a supertype's
  * arm takes the node and no arm knows the construct was there, which is a reason to look and not a
  * defect by itself. Both outcomes have now happened. `CtTextBlock` is the worked example of one —
  * `TextBlockSpec` asked which string arrives, the answer was JLS 3.10.6's own, and the kind is now
  * `Lowered` with a `NonDiff` catalog row. `CtRecord` is the worked example of the other: looking
  * found FOUR defects at once, they were fixed, and what the probe used to pin here is now
  * `RecordSpec` asserting the repair (`JS-C43`).
  *
  * What is left on this list is the one below, and the assertion PINS THE DEFECT — written to FAIL
  * when it is fixed, which is what makes it a measurement rather than a description: a probe whose
  * expectations move with the code says nothing.
  *
  * No corpus library declares an `@interface` with elements, so nothing but a fixture can see it,
  * and the classification the kind carries in `SpoonKinds` is what the probe showed rather than
  * what it was assumed to be.
  */
class AbsorbedProbeSpec extends PortSuite:

  test("an @interface's ELEMENTS are dropped entirely — not just their `default` clauses") {
    // `CtAnnotationMethod`'s classification said `execDef` emits an ordinary abstract method and
    // only `getDefaultExpression` is lost. The probe says otherwise: the emitted annotation class
    // has NO members at all, so both the element and its default are gone.
    val p = port("package p;\npublic @interface Tag {\n  String value() default \"none\";\n  int n() default 3;\n}\n")
    assertEmits(p, "class Tag extends scala.annotation.StaticAnnotation")
    assertNotEmits(p, "def value")
    assertNotEmits(p, "def n")

    // WHY IT MATTERS NOW rather than in the abstract: `ENGINE-LIMITS.md` T16 has just made a TYPE's
    // argument-bearing annotation carryable, and an emitted `@p.Tag(value = "x")` needs the emitted
    // `Tag` to take that argument. For an EXTERNAL annotation — the case T16 was built for, and the
    // only one any corpus port has — scalac reads the java class file and this does not arise; for
    // a PORTED `@interface` it is a compile error the moment a port claims its own family.
  }
