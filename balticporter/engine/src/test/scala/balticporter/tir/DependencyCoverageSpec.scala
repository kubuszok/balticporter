package balticporter.tir

import balticporter.catalog.{ApiRows, ArtifactDep, DiffId, Platform, Verdict}

/** The `dependency-coverage` lane, and the line it draws against `portability(*)`.
  *
  * Half of the catalog's platform answers are `Depend`: the API exists off the JVM, in an artifact
  * the build has to add. Routing those into the portability lane makes the finding unanswerable —
  * the reader is told to remove a call that one `libraryDependencies` line makes correct — so the
  * two lanes partition the rule list, and the tests below are about that partition holding in both
  * directions.
  */
class DependencyCoverageSpec extends munit.FunSuite:

  private val All = Platform.values.toSet

  test("the two lanes PARTITION the rule list — every rule is on exactly one") {
    val unportable = PortabilityCheck.rulesFor(All)
    val needsDep   = PortabilityCheck.dependencyRulesFor(All)
    assertEquals((unportable.toSet & needsDep.toSet), Set.empty[PortabilityCheck.Rule])
    assertEquals(unportable.size + needsDep.size, PortabilityCheck.all.size)
    // …and the union is the whole list, so a rule cannot fall off both lanes by being classified
    // into neither — which is the failure that would leave a family silently unchecked again.
    assertEquals((unportable ++ needsDep).toSet, PortabilityCheck.all.toSet)
  }

  test("no rule is MIXED — the classification is never ambiguous under the default targets") {
    // A rule whose targets disagree about the KIND of answer (Depend on one, Refuse on the other)
    // would have to be reported on both lanes or arbitrarily on one. None exists today; this is
    // what makes "all Depend, else unportable" an exact classification rather than a rounding.
    val mixed = PortabilityCheck.all.flatMap { r =>
      PortabilityCheck.rowOf(r).toList.flatMap { row =>
        val actionable = r.on.filter(p => row.verdictOn(p).actionable)
        val deps       = actionable.filter(p => row.verdictOn(p).dependency.isDefined)
        Option.when(deps.nonEmpty && deps != actionable)(s"${r.api}: $deps of $actionable are Depend")
      }
    }
    assertEquals(mixed, Nil, mixed.mkString("\n"))
  }

  test("the whole time/locale DEPENDENCY family is on the dependency lane and nowhere else") {
    val deps = PortabilityCheck.dependencyRulesFor(All).map(_.api).toSet
    List("java.time.", "java.time.ZoneId#of", "java.util.Locale", "java.text.DecimalFormat",
         "java.text.SimpleDateFormat", "java.util.Currency", "java.security.MessageDigest",
         "java.lang.ref.WeakReference").foreach(a => assert(deps.contains(a), s"$a is not on the lane"))
    val unportable = PortabilityCheck.rulesFor(All).map(_.api).toSet
    List("java.time.", "java.util.Locale", "java.security.MessageDigest")
      .foreach(a => assert(!unportable.contains(a), s"$a is reported as an unportability"))
  }

  test("…and the REFUSALS stay on the portability lane") {
    val unportable = PortabilityCheck.rulesFor(All).map(_.api).toSet
    List("java.text.MessageFormat", "java.text.Collator", "java.text.BreakIterator",
         "java.util.Calendar", "java.util.TimeZone", "java.lang.reflect.")
      .foreach(a => assert(unportable.contains(a), s"$a left the portability lane"))
  }

  test("a verdictOverride moves a row OFF the dependency lane — the third conjunct, structurally") {
    val time = DiffId(balticporter.catalog.Area.L, 60)
    val mine = Map(time -> Map(Platform.ScalaJs -> Verdict.Shim("our own"),
                               Platform.ScalaNative -> Verdict.Shim("our own")))
    assert(PortabilityCheck.dependencyRulesFor(All).exists(_.at.contains(time)))
    assert(!PortabilityCheck.dependencyRulesFor(All, mine).exists(_.at.contains(time)),
      "a port that says it ships its own shim must not be told to add the artifact")
    // …and it lands on the portability lane instead, because a Shim is still work the port owes.
    assert(PortabilityCheck.rulesFor(All, mine).exists(_.at.contains(time)))
    // an override to Keep silences it on BOTH lanes — the port's own statement that it accepts the
    // JDK type there, which is a decision and not a hole. `by`, the availability FACT, is untouched.
    val keep = Map(time -> Map(Platform.ScalaJs -> Verdict.Keep, Platform.ScalaNative -> Verdict.Keep))
    assert(!PortabilityCheck.dependencyRulesFor(All, keep).exists(_.at.contains(time)))
    assert(!PortabilityCheck.rulesFor(All, keep).exists(_.at.contains(time)))
    assertEquals(ApiRows.byId(time).by(Platform.ScalaNative), balticporter.catalog.Availability.Absent)
  }

  test("a declared dependency COVERS its requirements, matched on org and name and not on version") {
    val dep = ArtifactDep("io.github.cquiroz", "scala-java-time", "2.6.0")
    val row = ApiRows.byId(DiffId(balticporter.catalog.Area.L, 60))
    val req = DependencyCheck.Requirement(
      PortabilityCheck.all.find(_.api == "java.time.").get, row,
      Map(Platform.ScalaJs -> dep, Platform.ScalaNative -> dep),
      "java.time.Instant", Origin.synthetic, UsageKind.MemberType, SymId.None)
    assertEquals(DependencyCheck.uncovered(List(req), Nil).size, 1)
    assertEquals(DependencyCheck.uncovered(List(req), List(dep)), Nil)
    // a different REVISION still covers: the catalog's `rev` is the version the survey checked, not
    // a floor, and a lane that policed versions is one nobody asked for.
    assertEquals(DependencyCheck.uncovered(List(req), List(dep.copy(rev = "9.9.9"))), Nil)
    // a different ARTIFACT does not.
    assertEquals(DependencyCheck.uncovered(List(req), List(dep.copy(name = "something-else"))).size, 1)
  }

  test("…and a declaration that covers NOTHING is a policy finding, not silence") {
    // The direction the lane itself cannot show: coverage SUBTRACTS, so an entry naming an artifact
    // no requirement wants leaves `dependency-coverage` exactly where it was — 0 before, 0 after —
    // and the port ships a jar on every backend for a call it does not make.
    val time    = ArtifactDep("io.github.cquiroz", "scala-java-time", "2.6.0")
    val locales = ArtifactDep("io.github.cquiroz", "scala-java-locales", "1.5.4")
    val row     = ApiRows.byId(DiffId(balticporter.catalog.Area.L, 60))
    val req = DependencyCheck.Requirement(
      PortabilityCheck.all.find(_.api == "java.time.").get, row,
      Map(Platform.ScalaJs -> time, Platform.ScalaNative -> time),
      "java.time.Instant", Origin.synthetic, UsageKind.MemberType, SymId.None)
    // the one the requirement names is NOT reported, whatever version the port pinned
    assertEquals(DependencyCheck.unneeded(List(req), List(time)), Nil)
    assertEquals(DependencyCheck.unneeded(List(req), List(time.copy(rev = "9.9.9"))), Nil)
    // the one nothing names IS, and only that one
    assertEquals(DependencyCheck.unneeded(List(req), List(time, locales)), List(locales))
    // a port that declares nothing has nothing to answer for — the empty parameter is the no-op
    assertEquals(DependencyCheck.unneeded(List(req), Nil), Nil)
    // …and with no requirement at all, every declaration is one (the shape a port reaches after an
    // upstream change removes the last call, which nothing else in the run can see)
    assertEquals(DependencyCheck.unneeded(Nil, List(time)), List(time))

    // and its CLASSIFICATION, which is `core`'s half: `NeverApplied` rather than either neighbour —
    // the entry is well formed and names a real artifact, and what did not happen is the requirement.
    val report = balticporter.core.PolicyReport.fromDependencies(List(locales))
    assertEquals(report.findings.map(_.issue), List(balticporter.core.PolicyIssue.NeverApplied))
    assertEquals(report.findings.map(_.key), List(locales.toString))
    assertEquals(report.findings.map(_.setting), List("PortManifest.dependencies"))
  }

  test("an empty target set makes BOTH lanes no-ops") {
    assertEquals(PortabilityCheck.rulesFor(Set.empty), Nil)
    assertEquals(PortabilityCheck.dependencyRulesFor(Set.empty), Nil)
  }

  test("a JVM-only port needs no artifact") {
    assertEquals(PortabilityCheck.dependencyRulesFor(Set(Platform.Jvm)), Nil)
  }

  test("a Native-only port is told about java.time and NOT about WeakReference") {
    val nat = PortabilityCheck.dependencyRulesFor(Set(Platform.Jvm, Platform.ScalaNative)).map(_.api).toSet
    assert(nat.contains("java.time."))
    assert(nat.contains("java.security.MessageDigest"))
    // Native's WeakReference is GC-integrated and needs nothing; only Scala.js needs the artifact.
    assert(!nat.contains("java.lang.ref.WeakReference"))
    assert(!nat.contains("java.security.SecureRandom"))
  }
