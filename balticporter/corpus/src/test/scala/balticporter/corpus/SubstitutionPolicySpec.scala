package balticporter.corpus

import balticporter.core.{PolicyIssue, PolicyReport, Substitutions}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.PolicyBinder

/** The unmatched-key report END TO END — now derived from the BINDING rather than from a tally the
  * policy value accumulated while the frontend consulted it. */
class SubstitutionPolicySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Reflect {
      |  static String nameOf(Class c) { return c.getName(); }
      |  void write(String s) { }
      |  void write(int i) { }
      |  Reflect(int a) { }
      |  Reflect(long a) { }
      |}
      |class Keeper { }
      |""".stripMargin

  /** translate, then ask the binder about every declared key — the shape `PortRun` uses. */
  private def report(subs: Substitutions): PolicyReport =
    val p      = SpoonTir.fromSource(src, subs = subs)
    val binder = new PolicyBinder(p, p.members)
    subs.dropTypes.toList.sorted.foreach(k => binder.bindType("substitutions", "Substitutions.dropTypes", k))
    subs.dropMethods.toList.sorted.foreach(k => binder.bindMembers("substitutions", "Substitutions.dropMethods", k))
    PolicyReport.fromBindings(binder.bindings)

  test("a typo'd dropTypes key is REPORTED; the key that fired is not") {
    val r = report(Substitutions(dropTypes = Set("demo.Reflect", "demo.Reflct"))) // second is the typo
    assertEquals(r.keys, Set("demo.Reflct"))
    assertEquals(r.findings.map(_.issue), List(PolicyIssue.NeverMatched))
    // and the line tells an agent in another repository what KIND of fix this is (CLAUDE.md §4.45)
    assert(clue(r.render).contains("§1(b)"))
  }

  test("dropMethods: bare, overload-precise and constructor keys are each credited or reported") {
    val r = report(Substitutions(dropMethods = Set(
      "demo.Reflect#write",             // fires: both overloads
      "demo.Reflect#nameOf(Class)",     // fires: overload-precise
      "demo.Reflect#<init>(int)",       // fires: one constructor
      "demo.Reflect#<init>(double)",    // never: no such constructor
      "demo.Reflect#raed",              // never: typo
      "demo.Missing#gone",              // never: the owner is not in the port at all
    )))
    assertEquals(r.keys,
      Set("demo.Reflect#<init>(double)", "demo.Reflect#raed", "demo.Missing#gone"))
    // THE PROPERTY THE INDEX EXISTS FOR: every one of the three keys that FIRED names a member the
    // frontend has already removed, so nothing in the program can be asked about it.
  }

  test("a policy whose every key fires reports NOTHING — the check must not cry wolf") {
    val r = report(Substitutions(
      dropTypes   = Set("demo.Keeper"),
      dropMethods = Set("demo.Reflect#write", "demo.Reflect#<init>(long)"),
    ))
    assertEquals(r.render, "  none")
  }
