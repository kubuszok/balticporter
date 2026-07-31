package balticporter.corpus

import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{ExternalUsage, PortabilityCheck, UsageKind}

/** The enumeration `PortabilityCheck` used to perform inline and discard — `ExternalUsage`.
  *
  * The check walked every referenced symbol, resolved its `owner#name`, held every recorded usage
  * with its kind and its origin, and then kept only the hits of its 34 rules. Everything else went,
  * which is why no artifact of what a port depends on OUTSIDE ITSELF existed anywhere and why every
  * question about the JDK surface was answered by grepping emitted text — a method that cannot see
  * an instance call on a kept receiver at all.
  *
  * Two properties are load-bearing and neither is visible to a count:
  *
  *   - the rules still see exactly what they saw, IN ORDER. `portability(all)` is a promoted
  *     baseline in thirteen lanes, and a reordering diffs as every row removed and re-added while
  *     every count stays identical;
  *   - "external" is STRUCTURAL (`Program.owns`, CLAUDE.md §4.56) and never a name test, so a
  *     dependent port's base module — whose units are in the program because it RESOLVES against
  *     them — is owned exactly as its own types are.
  */
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

  test("the EMITTED lane excludes a unit this run does not ship (ENGINE-LIMITS D2)") {
    val everything = ExternalUsage.external(program)
    val bag        = program.units.head.symbol
    val none       = ExternalUsage.external(program, isExcluded = _ == bag)
    assert(clue(everything).nonEmpty)
    // every reference in this program is made from inside `Bag`, so excluding it empties the lane
    assertEquals(clue(none), Nil)
  }
