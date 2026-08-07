package balticporter.tir

import balticporter.catalog.{ApiRows, Platform, Verdict}

/** `PortabilityCheck` as a §1(b) phase — the TARGET SET is the parameter, and this is what stops the
  * parameterisation from being a lane reset.
  *
  * Three properties, and each of them is a way the change could have gone wrong silently:
  *
  *   - the DEFAULT reproduces the pre-parameter rule set EXACTLY. `PortManifest.targets` is all
  *     three platforms, so a port that declares nothing selects every rule and no baseline moves.
  *     A default of `Set(Jvm)` or `Set.empty` would have emptied the list on fifteen ports at once
  *     and collapsed `portability(all|emitted|injected)` to a floor in one commit;
  *   - the RE-SCOPING is real and observable, but only to a port that asks for it: eight rules leave
  *     the set when Scala.js is not a target, and they are the eight the survey measured as too
  *     broad for Scala Native 0.5.x;
  *   - the rule list and the CATALOG cannot drift apart. A rule may not claim a platform on which
  *     its own cited row says `Keep`, which is the direction that matters — a row corrected to
  *     `Keep` while a rule keeps firing is a registry that has stopped describing the engine.
  */
class PortabilityTargetsSpec extends munit.FunSuite:

  private val All = Platform.values.toSet

  test("the default target set selects EVERY rule — today's semantics, unchanged") {
    assertEquals(PortabilityCheck.rulesFor(All), PortabilityCheck.all)
    // …and the order is preserved, because `portability(all)`'s promoted baselines in thirteen
    // lanes are byte-identical rather than merely equal in count.
    assertEquals(PortabilityCheck.rulesFor(All).map(_.api), PortabilityCheck.all.map(_.api))
  }

  test("an EMPTY target set is the no-op §1(b) asks for") {
    assertEquals(PortabilityCheck.rulesFor(Set.empty), Nil)
    // and it is deliberately NOT what any port gets by default — that is `PortManifest.targets`.
    assertEquals(balticporter.core.PortManifest(name = "x").targets, All)
  }

  test("a JVM-only port asks nothing: every rule is about a NON-JVM backend") {
    assertEquals(PortabilityCheck.rulesFor(Set(Platform.Jvm)), Nil)
  }

  test("a JVM+Native port loses EXACTLY the eight re-scoped rules, and nothing else") {
    val jvmNative = Set(Platform.Jvm, Platform.ScalaNative)
    val dropped   = PortabilityCheck.all.filterNot(_.asks(jvmNative)).map(_.api).sorted
    assertEquals(dropped, List(
      "java.lang.ProcessBuilder",
      "java.lang.System#getProperty",
      "java.lang.Thread",
      "java.net.",
      "java.nio.channels.",
      "java.nio.file.",
      "java.util.concurrent.",
      "java.util.zip.",
    ))
    // the negative half: everything that stays is a rule Scala Native genuinely cannot answer —
    // reflection, class loading, javax, ServiceLoader, and the JUnit/Hamcrest test vocabulary.
    val kept = PortabilityCheck.rulesFor(jvmNative).map(_.api)
    assert(kept.contains("java.lang.reflect."))
    assert(kept.contains("java.util.ServiceLoader"))
    assert(kept.contains("org.junit."))
  }

  test("a Scala.js-only port keeps all of them — the re-scoping is about NATIVE") {
    assertEquals(PortabilityCheck.rulesFor(Set(Platform.ScalaJs)).size, PortabilityCheck.all.size)
  }

  test("no rule claims a platform its own catalog row calls Keep") {
    val bad = PortabilityCheck.all.flatMap { r =>
      PortabilityCheck.rowOf(r).toList.flatMap { row =>
        r.on.toList.sortBy(_.toString).collect {
          case p if row.verdict(p) == Verdict.Keep =>
            s"${r.api} claims $p, but ${row.id} (${row.fqn}) says Keep there"
        }
      }
    }
    assertEquals(bad, Nil, bad.mkString("\n"))
  }

  test("every cited row EXISTS — a citation naming nothing is worse than none") {
    val missing = PortabilityCheck.all.flatMap(r => r.at.filterNot(ApiRows.byId.contains).map(id => s"${r.api} -> $id"))
    assertEquals(missing, Nil, missing.mkString("\n"))
  }

  test("the guard has TEETH — a rule claiming a Keep platform is rejected") {
    // `java.nio.file.` cites JS-P10, whose Native verdict is Keep. The re-scoped rule claims JS
    // alone; the pre-re-scoping shape claimed both, and this is the assertion that would have
    // caught it.
    val row = ApiRows.byId(PortabilityCheck.all.find(_.api == "java.nio.file.").flatMap(_.at).get)
    assertEquals(row.verdict(Platform.ScalaNative), Verdict.Keep)
    assertEquals(row.verdict(Platform.ScalaJs).actionable, true)
  }

  test("a prefix rule cuts at a SEPARATOR — java.lang.Thread is not java.lang.ThreadLocal") {
    val thread = PortabilityCheck.all.find(_.api == "java.lang.Thread").get
    assert(PortabilityCheck.names(thread, "java.lang.Thread"))
    assert(!PortabilityCheck.names(thread, "java.lang.ThreadLocal"),
      "`startsWith` covered ThreadLocal, which Scala.js implements — §4.56's own hazard, live")
    val file = PortabilityCheck.all.find(_.api == "java.nio.file.").get
    assert(PortabilityCheck.names(file, "java.nio.file.Path"))
    assert(PortabilityCheck.names(file, "java.nio.file.attribute.FileTime"))
    assert(!PortabilityCheck.names(file, "java.nio.filesystem.Whatever"))
    // a nested type is reached across `$`, which is the separator the cut is written in terms of.
    val pb = PortabilityCheck.all.find(_.api == "java.lang.ProcessBuilder").get
    assert(PortabilityCheck.names(pb, "java.lang.ProcessBuilder$Redirect"))
  }
