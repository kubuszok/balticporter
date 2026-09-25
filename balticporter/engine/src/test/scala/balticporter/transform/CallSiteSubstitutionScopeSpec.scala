package balticporter.transform

import balticporter.core.{ ManifestAgreement, PolicyIssue, PortManifest }
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.runner.{ CallSiteSubstitutionFactory, HoconView }
import balticporter.tir.{ Pipeline, RuleScope }
import balticporter.transform.CallSiteSubstitutionTransform.Entry

/** [[CallSiteSubstitutionTransform]]'s per-entry SCOPE: an entry rewrites only the calls whose enclosing declaration its scope admits, several entries may share one callee, and the most specific
  * scope wins at each call — so a dependent can rewrite its own calls of a base member without editing the base's surface, and a port can give one callee different replacements in different places.
  */
class CallSiteSubstitutionScopeSpec extends munit.FunSuite:

  private val sources = List(
    "demo/Bits.java" ->
      """package demo;
        |public class Bits {
        |  public boolean containsAll(Bits o) { return true; }
        |  public int length() { return 0; }
        |}
        |""".stripMargin,
    "demo/Err.java" ->
      """package demo;
        |public class Err extends RuntimeException { public Err(String m) { super(m); } }
        |""".stripMargin,
    "demo/Core.java" ->
      """package demo;
        |public class Core {
        |  int size(Bits b) { return b.length(); }
        |  void fail() { throw new Err("core"); }
        |}
        |""".stripMargin,
    "dep/Loader.java" ->
      """package dep;
        |public class Loader {
        |  demo.Bits held = new demo.Bits();
        |  demo.Bits mk() { return held; }
        |  demo.Bits other() { return held; }
        |  int size(demo.Bits b) { return b.length(); }
        |  boolean covers() { return mk().containsAll(other()); }
        |  void load() {
        |    Runnable r = () -> { throw new demo.Err("lambda"); };
        |    throw new demo.Err("load");
        |  }
        |  void elsewhere() { throw new demo.Err("elsewhere"); }
        |}
        |""".stripMargin
  )

  private def emit(phase: CallSiteSubstitutionTransform): String =
    new TirEmitter(Pipeline.run(SpoonTir.fromSources(sources), List(phase))).emit

  private val length = "demo.Bits#length()"
  private val ctor   = "demo.Err#<init>(String)"

  // -------------------------------------------------------------------------------------------
  // the scope decides WHERE a call is rewritten
  // -------------------------------------------------------------------------------------------

  test("a SCOPED entry rewrites the calls inside its scope and leaves every other call as java wrote it") {
    val phase = new CallSiteSubstitutionTransform(List(Entry(length, "{recv}.lastOption.fold(0)(_ + 1)", RuleScope.Only(Set("dep")))))
    val out   = emit(phase)
    assert(clue(out).contains("b.lastOption.fold(0)(_ + 1)"))
    // the same callee, called from a declaration outside the scope, is untouched
    assert(clue(out).contains("b.length()"))
    assertEquals(phase.substituted, List(length -> 1))
    assertEquals(phase.policyReport.findings, Nil)
  }

  test("the MOST SPECIFIC scope wins: a member entry over a package entry over the unscoped default") {
    val phase = new CallSiteSubstitutionTransform(
      List(
        Entry(ctor, "demo.Errors.invalid({arg0})"),
        Entry(ctor, "demo.Errors.graphics({arg0})", RuleScope.Only(Set("dep"))),
        Entry(ctor, "demo.Errors.serial({arg0})", RuleScope.Only(Set("dep.Loader#load")))
      )
    )
    val out = emit(phase)
    assert(clue(out).contains("""demo.Errors.invalid("core")"""))
    assert(clue(out).contains("""demo.Errors.graphics("elsewhere")"""))
    assert(clue(out).contains("""demo.Errors.serial("load")"""))
    // a call inside a lambda is placed by the declaration that encloses the lambda
    assert(clue(out).contains("""demo.Errors.serial("lambda")"""))
    assertEquals(phase.substituted, List(ctor -> 4))
    assertEquals(phase.policyReport.findings, Nil)
  }

  test("a default with ONE member-scoped override of the same callee — the override only where it names") {
    val phase = new CallSiteSubstitutionTransform(
      List(
        Entry(ctor, "demo.Errors.invalid({arg0})"),
        Entry(ctor, "demo.Errors.serial({arg0})", RuleScope.Only(Set("dep.Loader#load")))
      )
    )
    val out = emit(phase)
    assert(clue(out).contains("""demo.Errors.serial("load")"""))
    assert(clue(out).contains("""demo.Errors.invalid("elsewhere")"""))
    assert(clue(out).contains("""demo.Errors.invalid("core")"""))
  }

  test("two EQUALLY specific entries with different templates are REFUSED — at bind time and at every call they meet") {
    val phase = new CallSiteSubstitutionTransform(
      List(
        Entry(ctor, "demo.Errors.a({arg0})", RuleScope.Only(Set("dep"))),
        Entry(ctor, "demo.Errors.b({arg0})", RuleScope.Only(Set("dep")))
      )
    )
    val out = emit(phase)
    assert(!out.contains("demo.Errors.a(") && !out.contains("demo.Errors.b("), out)
    val findings = phase.policyReport.findings
    assert(clue(findings).exists(f => f.key == ctor && f.issue == PolicyIssue.Malformed && f.detail.contains("same scope")))
    // …and each call they both claimed is counted, never silently left
    assertEquals(phase.refusals.map(_._1).distinct, List(ctor))
    assertEquals(phase.refusals.size, 3)
    // two unscoped defaults tie the same way
    val two = new CallSiteSubstitutionTransform(List(Entry(ctor, "demo.Errors.a({arg0})"), Entry(ctor, "demo.Errors.b({arg0})")))
    emit(two)
    assert(clue(two.policyReport.findings).exists(_.issue == PolicyIssue.Malformed))
  }

  test("a scope entry no rewritten call sat under is REPORTED, not silently ignored") {
    val phase = new CallSiteSubstitutionTransform(List(Entry(length, "{recv}.size", RuleScope.Only(Set("dep", "absent.pkg")))))
    emit(phase)
    assertEquals(phase.policyReport.findings.map(f => f.key -> f.issue), List("absent.pkg" -> PolicyIssue.NeverMatched))
  }

  // -------------------------------------------------------------------------------------------
  // the template is spliced once per hole, in the template's own order
  // -------------------------------------------------------------------------------------------

  test("a template binding the receiver FIRST evaluates it once, before the argument — java's order") {
    // The phase splices each hole where the template names it; ORDER and COUNT are the template's.
    // `{recv}.m({arg0})`-shaped text keeps java's receiver-then-argument order by itself; a
    // template that must name the argument first binds the receiver to a local first. A literal
    // brace is `{{`/`}}` in the template grammar; a bare `{` opens a hole.
    val phase = new CallSiteSubstitutionTransform(Map("demo.Bits#containsAll(Bits)" -> "{{ val bpThis = {recv}; {arg0}.subsetOf(bpThis) }}"))
    val out   = emit(phase)
    assertEquals(phase.policyReport.findings, Nil)
    assert(clue(out).contains("{ val bpThis = "))
    val at = out.indexOf("val bpThis = ")
    assert(at >= 0, out)
    val line = out.substring(at, out.indexOf('\n', at))
    assert(clue(line).indexOf("mk()") < line.indexOf("other()"))
    assertEquals(line.split("mk\\(\\)").length - 1, 1) // the receiver occurs once
  }

  test("the stdlib facts a bit-set template relies on differ from java's at the edges — the policy owns them") {
    val empty = scala.collection.mutable.BitSet()
    val some  = scala.collection.mutable.BitSet(3, 70)
    // `length()` on an empty set is 0 in java; `lastOption.fold(0)(_ + 1)` agrees
    assertEquals(empty.lastOption.fold(0)(_ + 1), 0)
    assertEquals(some.lastOption.fold(0)(_ + 1), 71)
    // a NEGATIVE start: java's `index >>> 6` makes the word huge and answers -1; `iteratorFrom`
    // starts at the first element instead — a template must guard a negative argument itself
    assertEquals(some.iteratorFrom(-1).nextOption().getOrElse(-1), 3)
    assertEquals(some.iteratorFrom(4).nextOption().getOrElse(-1), 70)
    assertEquals(some.iteratorFrom(71).nextOption().getOrElse(-1), -1)
    // a negative index: `contains` answers false as java's `get` does
    assert(!some.contains(-1))
  }

  // -------------------------------------------------------------------------------------------
  // the surface: fingerprint, merge, subject scope, the intrusion screen, the config shape
  // -------------------------------------------------------------------------------------------

  test("an unscoped table fingerprints exactly as before; a scope is rendered only where it says something") {
    val plain = new CallSiteSubstitutionTransform(Map(length -> "x"))
    assertEquals(plain.surfaceFingerprint, s"$length=${"x".hashCode.toHexString}")
    val scoped = new CallSiteSubstitutionTransform(List(Entry(length, "x", RuleScope.Only(Set("dep")))))
    assertEquals(scoped.surfaceFingerprint, s"$length=${"x".hashCode.toHexString}@only:dep")
    assertEquals(new CallSiteSubstitutionTransform().surfaceFingerprint, "")
  }

  test("MERGE: a dependent's scoped entry joins the base's default for one callee; an equal-scope rival refuses") {
    val base   = new CallSiteSubstitutionTransform(Map(ctor -> "demo.Errors.invalid({arg0})"))
    val dep    = new CallSiteSubstitutionTransform(List(Entry(ctor, "demo.Errors.serial({arg0})", RuleScope.Only(Set("dep.Loader#load")))))
    val merged = base.mergedWith(dep)
    assert(clue(merged).isRight)
    assertEquals(merged.toOption.get.phase.asInstanceOf[CallSiteSubstitutionTransform].entries.size, 2)
    assertEquals(merged.toOption.get.added, Set("demo.Err"))
    // restating the base's own entry is agreement, not a contribution
    assertEquals(
      base.mergedWith(new CallSiteSubstitutionTransform(Map(ctor -> "demo.Errors.invalid({arg0})"))).toOption.get.added,
      Set.empty[String]
    )
    // a second unscoped template for the same callee competes with the base's default
    assert(clue(base.mergedWith(new CallSiteSubstitutionTransform(Map(ctor -> "demo.Errors.other({arg0})")))).isLeft)
  }

  test("`subjectScope` is the union of a subject's `Only` entries, and unrestricted when any entry is") {
    val scoped = new CallSiteSubstitutionTransform(
      List(Entry(length, "a", RuleScope.Only(Set("dep"))), Entry("demo.Bits#containsAll(Bits)", "b", RuleScope.Only(Set("dep2"))))
    )
    assertEquals(scoped.subjectScope("demo.Bits"), RuleScope.Only(Set("dep", "dep2")))
    val mixed = new CallSiteSubstitutionTransform(List(Entry(length, "a", RuleScope.Only(Set("dep"))), Entry(length, "b")))
    assertEquals(mixed.subjectScope("demo.Bits"), RuleScope.everywhere)
  }

  private def dependentOf(p: CallSiteSubstitutionTransform): PortManifest =
    PortManifest("base", governs = Set("demo")).extendedBy(PortManifest("dep", surface = List(p)))

  test("INTRUSION screen: an unscoped entry on a base type fires; one scoped outside the base's claim passes") {
    val unscoped = dependentOf(new CallSiteSubstitutionTransform(Map(length -> "{recv}.size")))
    assertEquals(unscoped.surfaceFold.intrusions.map(_.subject), List("demo.Bits"))
    assertEquals(
      ManifestAgreement.check(Some(unscoped), Nil, foreignRoots = true).map(_.kind),
      List(ManifestAgreement.Kind.SurfaceIntrusion)
    )

    val outside = dependentOf(new CallSiteSubstitutionTransform(List(Entry(length, "{recv}.size", RuleScope.Only(Set("dep"))))))
    assertEquals(outside.surfaceFold.intrusions, Nil)
    assertEquals(ManifestAgreement.check(Some(outside), Nil, foreignRoots = true).map(_.kind), Nil)

    // a scope reaching INTO the base's claim can rewrite the base's own calls, so it still fires
    val inside = dependentOf(new CallSiteSubstitutionTransform(List(Entry(length, "{recv}.size", RuleScope.Only(Set("demo.Core"))))))
    assertEquals(inside.surfaceFold.intrusions.map(_.subject), List("demo.Bits"))
  }

  test("the `.conf` shape: `calls` stays the unscoped table, `scoped` adds entries with a scope each") {
    val conf = com.typesafe.config.ConfigFactory.parseString(
      s"""calls { "$ctor" = "demo.Errors.invalid({arg0})" }
         |scoped = [ { call = "$ctor", template = "demo.Errors.serial({arg0})", scope { only = ["dep.Loader#load"] } } ]
         |""".stripMargin
    )
    val phase = new CallSiteSubstitutionFactory().fromConfig(HoconView.root(conf)).asInstanceOf[CallSiteSubstitutionTransform]
    assertEquals(
      phase.entries.toSet,
      Set(
        Entry(ctor, "demo.Errors.invalid({arg0})"),
        Entry(ctor, "demo.Errors.serial({arg0})", RuleScope.Only(Set("dep.Loader#load")))
      )
    )
    assertEquals(phase.calls, Map(ctor -> "demo.Errors.invalid({arg0})"))
  }
