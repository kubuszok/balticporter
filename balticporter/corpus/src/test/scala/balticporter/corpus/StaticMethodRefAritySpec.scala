package balticporter.corpus

import balticporter.testkit.PortSuite

/** A NILARY STATIC METHOD REFERENCE — `ENGINE-LIMITS.md` G32, the one qualified name scala will not
  * eta-expand. */
class StaticMethodRefAritySpec extends PortSuite:

  test("a NILARY static reference becomes a lambda that CALLS the method") {
    val p = port(
      """package demo;
        |import java.util.function.Supplier;
        |class Uses {
        |  static String compute() { return "x"; }
        |  Supplier<String> go() { return Uses::compute; }
        |}
        |""".stripMargin)
    assertEmits(p, "() => Uses.compute()")
    // …and NOT the bare qualified name, which is what the port emitted before and which scalac
    // reads as a call with its argument list left off.
    assertNotEmits(p, "return Uses.compute\n")
  }

  test("a static reference at an @FunctionalInterface target keeps the bare name — eta-expansion is safe there") {
    // JS-C52: `java.util.function.Function` carries `@FunctionalInterface` in its class file, so
    // the frontend reads the annotation and the emitter keeps the bare qualified name.
    val p = port(
      """package demo;
        |import java.util.function.Function;
        |class Uses {
        |  static String twice(String s) { return s + s; }
        |  Function<String, String> go() { return Uses::twice; }
        |}
        |""".stripMargin)
    // the target IS @FunctionalInterface — bare name, no explicit lambda
    assertEmits(p, "return Uses.twice")
    assertNotEmits(p, "=> Uses.twice(a0$)")
  }

  test("a static reference at a NON-@FunctionalInterface SAM becomes an explicit lambda") {
    // JS-C52: `Mapper` has no `@FunctionalInterface`, so scalac would warn on eta-expansion
    // under `-Werror`. The emitter produces an explicit lambda to avoid it.
    val p = port(
      """package demo;
        |interface Mapper { String map(String s); }
        |class Uses {
        |  static String upper(String s) { return s.toUpperCase(); }
        |  Mapper go() { return Uses::upper; }
        |}
        |""".stripMargin)
    assertEmits(p, "=> Uses.upper(a0$)")
    assertNotEmits(p, "return Uses.upper\n")
  }

  test("@FunctionalInterface is PRESERVED on the emitted trait — scalac uses it to suppress eta-expansion warnings") {
    // JS-C52: the annotation was in `ignoredAnnotations` and dropped. Now it is preserved so
    // interfaces that DECLARE it get the annotation in the emitted Scala, and scalac does not warn
    // when a method reference targets them.
    val p = port(
      """package demo;
        |@FunctionalInterface
        |interface Mapper { String map(String s); }
        |class Uses { Mapper go() { return Uses::upper; } static String upper(String s) { return s.toUpperCase(); } }
        |""".stripMargin)
    assertEmits(p, "@java.lang.FunctionalInterface")
    assertEmits(p, "trait Mapper")
    // the method reference targeting the annotated interface keeps the BARE NAME — no explicit
    // lambda needed, because the annotation silences the eta-expansion warning.
    assertEmits(p, "return Uses.upper")
    assertNotEmits(p, "=> Uses.upper(a0$)")
  }

  test("an UNBOUND INSTANCE reference to a nilary method is still the receiver lambda") {
    val p = port(
      """package demo;
        |import java.util.function.Function;
        |class Uses {
        |  String name() { return "n"; }
        |  Function<Uses, String> go() { return Uses::name; }
        |}
        |""".stripMargin)
    // the receiver becomes the SAM's first parameter (JLS 15.13.3), so this reference has arity 0
    // at the METHOD and arity 1 at the function — the arm above must not read the first number and
    // answer for the second.
    assertEmits(p, "self$")
    assertEmits(p, "self$.name()")
    assertNotEmits(p, "() => Uses.name()")
  }
