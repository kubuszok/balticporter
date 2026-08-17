package balticporter.corpus

import balticporter.testkit.PortSuite

/** A NILARY STATIC METHOD REFERENCE — `ENGINE-LIMITS.md` G32, the one qualified name scala will not
  * eta-expand.
  *
  * `Referent`'s own doc used to say the arity rode on the unbound case *"only because that is the
  * only form whose emitted lambda has to state it — a qualified name is eta-expanded by scala against
  * the target, exactly as javac did it"*. True of every arity but one: Scala 3 refuses to eta-expand
  * a NULLARY method from a bare name, so `Type.m` where java declared `T m()` is a call missing its
  * argument list and not a `() => T`. A library that states a lazily-computed default as
  * `new DataKey<>("k", Type::compute)` therefore emitted three `must be called with () argument`
  * errors, at 0 findings and 0 moved counts on every other lane — the compile is the only instrument
  * that sees it, and it sees it only on a port that has one.
  *
  * The arity is JAVA'S, off the node (`Tree.MethodRef.referent`) and never off the symbol: an
  * external member is interned with no `MethodType` and would read as taking no arguments, which is
  * `CLAUDE.md` §4.6's fabricated fact with the default baked into the data. `getParameters` on the
  * REFERENCE survives a lenient parse — it erases what each slot SAYS, never how many there are.
  */
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

  test("a static reference WITH parameters keeps the qualified NAME — the negative") {
    val p = port(
      """package demo;
        |import java.util.function.Function;
        |class Uses {
        |  static String twice(String s) { return s + s; }
        |  Function<String, String> go() { return Uses::twice; }
        |}
        |""".stripMargin)
    // scala eta-expands this one against the target exactly as javac did, so the arity-0 arm must
    // not claim it: a lambda here would be a rewrite for no reason on every port in the corpus.
    assertEmits(p, "return Uses.twice")
    assertNotEmits(p, "() => Uses.twice()")
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
