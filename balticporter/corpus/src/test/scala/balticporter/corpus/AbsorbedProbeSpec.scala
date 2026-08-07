package balticporter.corpus

import balticporter.testkit.PortSuite

/** THE ABSORBED-SILENTLY KINDS, PROBED — what each one really emits today.
  *
  * `SpoonKinds.Absence.AbsorbedSilently` is a SUSPICION and not a finding: it says a supertype's
  * arm takes the node and no arm knows the construct was there, which is a reason to look and not a
  * defect by itself. `CtTextBlock` is the worked example of the other outcome — `TextBlockSpec`
  * asked which string arrives, the answer was JLS 3.10.6's own, and the kind is now `Lowered` with a
  * `NonDiff` catalog row.
  *
  * These two are the ones where looking found something, and the assertions below PIN THE DEFECT.
  * They are written to FAIL when it is fixed, which is what makes them a measurement rather than a
  * description: a probe whose expectations move with the code says nothing.
  *
  * Neither construct has a single site in any ported module — records need SE16 and no corpus
  * library declares an `@interface` with elements — so nothing but a fixture can see either, and
  * the classification each carries in `SpoonKinds` is now what the probe showed rather than what it
  * was assumed to be.
  */
class AbsorbedProbeSpec extends PortSuite:

  test("a RECORD emits a class extending java.lang.Record with its three abstract members UNIMPLEMENTED") {
    // The good half, and it is most of the construct: Spoon exposes the components as FIELDS and the
    // accessors as METHODS, so the canonical constructor, the backing state and `x()`/`y()` all
    // arrive. `JS-C43`'s own sentence — "a record is silently degraded to a plain class" — is
    // therefore not what happens.
    val p = port("package p;\npublic record Point(int x, int y) {\n  int sum() { return x + y; }\n}\n")
    assertEmits(p, "extends java.lang.Record")
    assertEmits(p, "def x(): scala.Int")
    assertEmits(p, "def y(): scala.Int")

    // …and the defect, which is LOUD and arrives late. `java.lang.Record` declares `equals`,
    // `hashCode` and `toString` ABSTRACT, and javac generates all three for a record; nothing here
    // does, so the emitted class does not implement its parent and is not concrete. §3 is why that
    // matters: `RefChecks` does not run while any typer error remains, so a port carrying a record
    // learns this on the day it reaches zero errors and not before.
    assertNotEmits(p, "override def equals")
    assertNotEmits(p, "override def hashCode")
    assertNotEmits(p, "override def toString")
  }

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
