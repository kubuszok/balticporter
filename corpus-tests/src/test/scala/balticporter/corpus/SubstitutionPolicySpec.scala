package balticporter.corpus

import balticporter.core.{PolicyIssue, Substitutions}
import balticporter.frontend.spoon.SpoonTir

/** The unmatched-key report END TO END: the tally is written by the FRONTEND as it consults the
  * policy, so what it measures is which keys the translation actually satisfied — not which keys
  * look plausible.
  *
  * The failure it closes is a `dropTypes`/`dropMethods` entry that matches nothing: the type is
  * translated after all, the injected replacement shadows nothing, and NOTHING says so. The
  * migration's existing CHECK 2 catches the opposite case (a drop that fired and left a dangling
  * reference); there was no check for a drop that never fired.
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

  test("a typo'd dropTypes key is REPORTED; the key that fired is not") {
    val subs = Substitutions(
      dropTypes = Set("demo.Reflect", "demo.Reflct"), // second is the typo
    )
    SpoonTir.fromSource(src, subs = subs)

    assertEquals(subs.unmatchedTypes, Set("demo.Reflct"))
    assertEquals(subs.policyReport.keys, Set("demo.Reflct"))
    assertEquals(subs.policyReport.findings.map(_.issue), List(PolicyIssue.NeverMatched))
    // and the line tells an agent in another repository what KIND of fix this is (CLAUDE.md §4.45)
    assert(clue(subs.policyReport.render).contains("§1(b)"))
  }

  test("dropMethods: bare, overload-precise and constructor keys are each credited or reported") {
    val subs = Substitutions(dropMethods = Set(
      "demo.Reflect#write",             // fires: both overloads
      "demo.Reflect#nameOf(Class)",     // fires: overload-precise
      "demo.Reflect#<init>(int)",       // fires: one constructor
      "demo.Reflect#<init>(double)",    // never: no such constructor
      "demo.Reflect#raed",              // never: typo
      "demo.Missing#gone",              // never: the owner is not in the port at all
    ))
    SpoonTir.fromSource(src, subs = subs)

    assertEquals(
      subs.unmatchedMethods,
      Set("demo.Reflect#<init>(double)", "demo.Reflect#raed", "demo.Missing#gone"),
    )
  }

  test("a policy whose every key fires reports NOTHING — the check must not cry wolf") {
    val subs = Substitutions(
      dropTypes = Set("demo.Keeper"),
      dropMethods = Set("demo.Reflect#write", "demo.Reflect#<init>(long)"),
    )
    SpoonTir.fromSource(src, subs = subs)
    assertEquals(subs.policyReport.render, "  none")
  }
