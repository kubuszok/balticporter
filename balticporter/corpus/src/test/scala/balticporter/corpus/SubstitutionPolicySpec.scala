package balticporter.corpus

import balticporter.core.{PolicyIssue, PolicyReport, Substitutions}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.PolicyBinder

/** The unmatched-key report END TO END — now derived from the BINDING rather than from a tally the
  * policy value accumulated while the frontend consulted it.
  *
  * The failure it closes is unchanged and is the whole reason it exists: a `dropTypes`/`dropMethods`
  * entry that matches nothing means the type is translated after all, the injected replacement
  * shadows nothing, and NOTHING says so. The migration's CHECK 2 catches the opposite case (a drop
  * that fired and left a dangling reference); this is the symmetric half.
  *
  * What changed is where the answer comes from. `Substitutions` carried a mutable tally whose own
  * scaladoc apologised for it — `copy()` emptied it, two source sets translated through one manifest
  * unioned their answers, and a report read before the frontend ran named every key. `PolicyBinder`
  * answers from the program plus the frontend's `MemberIndex`, where a DROPPED member still exists,
  * so the answer is a fact about a RUN. It also distinguishes an EXTERNAL-only match from a typo,
  * which the tally could not.
  */
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
