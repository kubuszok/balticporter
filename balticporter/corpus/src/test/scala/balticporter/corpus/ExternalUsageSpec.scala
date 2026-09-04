package balticporter.corpus

import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{ExternalUsage, PortabilityCheck, UsageKind}

/** `ExternalUsage` — every external symbol referenced (`owner#name`), its kind and origin, held as
  * an artifact rather than kept only where `PortabilityCheck`'s 34 rules matched and discarded. */
class ExternalSurfaceSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |class Bag {
      |  private List<String> items = new ArrayList<String>();
      |  int big(int a, int b) { return Math.max(a, b); }
      |  int small(int a, int b) { return Math.min(a, b); }
      |  int again(int a, int b) { return Math.max(b, a); }
      |  void own() { big(1, 2); }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src)

  test("an external member is enumerated with its owner, its usage kinds and its site count") {
    val rows = ExternalUsage.external(program)
    val max  = rows.find(_.member.contains("java.lang.Math#max")).getOrElse(fail(
      s"java.lang.Math#max is not in the surface: ${rows.flatMap(_.member).sorted.mkString(", ")}"))
    assertEquals(clue(max.sites), 2)                       // `big` and `again`
    assert(clue(max.kinds).contains(UsageKind.Call))
    assert(clue(max.firstOrigin.javaPath).nonEmpty)
  }

  test("a type the PROGRAM declares is not in the surface — ownership is structural, not a prefix") {
    val rows = ExternalUsage.external(program)
    assert(!clue(rows.map(_.fullName)).contains("demo.Bag"))
    assert(!rows.flatMap(_.member).exists(_.startsWith("demo.Bag#")))
  }

  test("`all` keeps `program.referenced`'s own order — the promoted baselines are keyed on it") {
    assertEquals(ExternalUsage.all(program).map(_.symbol), program.referenced.toList)
  }

  test("the rule filter reads the enumeration and is unchanged by the lift") {
    // a rule that fires on this program, matched through the same rows
    val vs = PortabilityCheck.check(program, List(PortabilityCheck.Rule("java.util.ArrayList", "test rule")))
    assert(clue(vs).nonEmpty)
    assert(vs.forall(_.api == "java.util.ArrayList"))
    // …and an exactMember rule, which is the one that keys on `owner#name` and had never fired
    // before P4 gave an external member an owner (ENGINE-LIMITS P4)
    val ms = PortabilityCheck.check(program, List(PortabilityCheck.Rule("java.lang.Math#max", "test rule", exactMember = true)))
    assertEquals(clue(ms).size, 2)
    assert(ms.forall(_.api == "java.lang.Math#max"))
  }

  test("a ServiceLoader use is a COUNTED violation — the resource it reads is not emitted") {
    // The engine produces `.scala` and nothing else, so `META-INF/services/<iface>` is a file the
    // port must ship by hand — and one a package rename has to translate in its NAME and in its
    // CONTENTS. With it absent the loader finds zero providers and the registration silently does
    // nothing: no compile error, no other check count, no finding. This rule is the only thing in
    // the pipeline that says the program depends on it at all.
    val spi = SpoonTir.fromSource(
      """package demo;
        |import java.util.ServiceLoader;
        |class Providers {
        |  void load() { ServiceLoader.load(CharSequence.class); }
        |}
        |""".stripMargin)
    val vs = PortabilityCheck.check(spi).map(_.api).distinct
    assert(clue(vs).contains("java.util.ServiceLoader"))
  }

  test("the EMITTED lane excludes a unit this run does not ship (ENGINE-LIMITS D2)") {
    val everything = ExternalUsage.external(program)
    val bag        = program.units.head.symbol
    val none       = ExternalUsage.external(program, isExcluded = _ == bag)
    assert(clue(everything).nonEmpty)
    // every reference in this program is made from inside `Bag`, so excluding it empties the lane
    assertEquals(clue(none), Nil)
  }
