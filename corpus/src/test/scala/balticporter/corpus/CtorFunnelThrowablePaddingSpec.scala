package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** A padded super-call slot is `null` because the NARROWER overload java called left it `null` —
  * true of every JDK `Throwable` position except one.
  *
  * `Throwable(Throwable cause)` is specified as `this(cause == null ? null : cause.toString(),
  * cause)`: it fills its OWN message. Padding it with `null` instead builds a different exception,
  * and a RUNTIME probe over the emitted `GdxRuntimeException` is what showed it —
  * `new GdxRuntimeException(cause).getMessage` returned `null` where the JDK's returned
  * `java.lang.IllegalStateException: boom`. Both emissions compile and no check count moves
  * (CLAUDE.md §4.4).
  *
  * The rendering is `java.util.Objects.toString(cause, null)`, which IS that conditional. It still
  * names the cause TWICE across the two slots, and a scala secondary constructor cannot bind a
  * value before its `this(...)` call — so a cause that cannot be read twice is REFUSED and counted
  * rather than evaluated twice.
  *
  * Every direction is pinned here, because a fix that widens is the real risk: the padding rule is
  * exact only for the JDK-throwable family, whose constructor set is fixed and documented. Guessing
  * outside it measured 0 -> 55 compile errors.
  */
class CtorFunnelThrowablePaddingSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |/** the JDK shape: three roots reaching `RuntimeException`'s fixed constructor set. */
      |class Boom extends RuntimeException {
      |  Boom(String m)              { super(m); }
      |  Boom(String m, Throwable c) { super(m, c); }
      |  Boom(Throwable c)           { super(c); }
      |}
      |class Holder { Throwable cause; Throwable next() { return null; } }
      |/** a cause that is re-readable but is NOT a bare ident. */
      |class Field extends RuntimeException {
      |  Field(String m, Throwable c) { super(m, c); }
      |  Field(Holder h)              { super(h.cause); }
      |}
      |/** a cause with a SIDE EFFECT: naming it twice would call `next()` twice and hand the two
      |  * slots different objects, so the message is refused and reported instead. */
      |class Call extends RuntimeException {
      |  Call(String m, Throwable c) { super(m, c); }
      |  Call(Holder h)              { super(h.next()); }
      |}
      |/** a PORTED exception's subclass — the widening risk in its natural form. Its parent's
      |  * constructor set is whatever the library wrote, so nothing here is a JDK fact. (A separate
      |  * base from `Boom`: a subclass reaching its parent with an argument-free `extends` is what
      |  * makes `Plans` WITHHOLD that parent's promotion, which would change `Boom` as well.) */
      |class Ported extends RuntimeException {
      |  Ported(String m, Throwable c) { super(m, c); }
      |  Ported(Throwable c)           { super(c); }
      |}
      |class Derived extends Ported {
      |  Derived(String m, Throwable c) { super(m, c); }
      |  Derived(Throwable c)           { super(c); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit

  test("`super(cause)` on a JDK throwable computes the message the JDK would have") {
    assert(clue(out).contains(
      "def this(c: java.lang.Throwable) = {\n    this(java.util.Objects.toString(c, null), c)"))
  }

  test("`super(message)` on the same class still pads the CAUSE with null") {
    // the padded slot is the Throwable, not the String: `Throwable(String)` really does leave the
    // cause unset, so this position is exactly what it always was.
    assert(clue(out).contains(
      "def this(m: java.lang.String) = {\n    this(m, null.asInstanceOf[java.lang.Throwable])"))
  }

  test("a re-readable cause need not be an ident — a field read is named in both slots") {
    assert(clue(out).contains(
      "this(java.util.Objects.toString(h.cause, null), h.cause)"))
  }

  test("an EFFECTFUL cause is REFUSED, not evaluated twice — and it is reported") {
    // `h.next()` in both slots would call it twice and pass two DIFFERENT throwables. Refusing
    // leaves the old null message, which is wrong — so it is counted rather than left silent.
    assert(clue(out).contains("this(null.asInstanceOf[java.lang.String], h.next())"))
    assertEquals(out.sliding("h.next()".length).count(_ == "h.next()"), 1)
    assertEquals(OmissionCheck.droppedCauseMessages(program).map(_.owner), List("demo.Call"))
  }

  test("the rule stops at the JDK family — a ported exception's subclass gains nothing") {
    // Two guards hold this line and both must: `jdkThrowableParent` refuses the rule, and `plan0`
    // never promotes a primary here in the first place (the roots reach two different overloads of
    // a parent whose constructor set the engine does not know), so the call is honestly `Dropped`.
    val derived = clue(out).linesIterator.dropWhile(!_.startsWith("class Derived")).takeWhile(_ != "}").mkString("\n")
    assert(!derived.contains("Objects.toString"))
    assert(derived.contains("this()"))
    // `Ported` is here too, and that is the pre-existing whole-program constraint, not this rule:
    // `Derived` reaches it with an argument-free `extends`, so `Plans` withholds ITS promotion and
    // both classes fall back to `this()`. Exactly the same list before and after the padding fix.
    assertEquals(OmissionCheck.droppedSuperArgs(program).map(_.owner).distinct,
                 List("demo.Ported", "demo.Derived"))
  }

  test("`droppedSuperArgs` never fires for the padded JDK calls — the ARGUMENTS all reach a slot") {
    // the two findings are different questions about the same call. The cause is delivered, so the
    // argument check is silent; only the message the JDK would have derived from it is missing.
    assertEquals(OmissionCheck.droppedSuperArgs(program).filter(_.owner == "demo.Call"), Nil)
    assertEquals(OmissionCheck.droppedCauseMessages(program).filter(_.owner == "demo.Derived"), Nil)
  }
