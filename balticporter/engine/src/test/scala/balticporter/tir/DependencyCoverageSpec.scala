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

  // ---- the 2×2 (ENGINE-LIMITS.md P8) --------------------------------------------------------
  //
  // "Does this coordinate answer anything?" was asked of ONE program, and the answer is exact for a
  // copied coordinate and WRONG for one a phase redirected INTO — the redirect removes the very JDK
  // usage the coordinate answers, so the artifact the port needs most reads as the one that fired on
  // nothing. All four cells below, plus the arm where the artifact's jar cannot be read at all.

  private val Time    = ArtifactDep("io.github.cquiroz", "scala-java-time", "2.6.0")
  private val Wrapper = ArtifactDep("org.example", "wrapper", "1.0")

  /** one requirement naming `dep`, as the catalog-row walk produces them. */
  private def req(dep: ArtifactDep) =
    val row = ApiRows.byId(DiffId(balticporter.catalog.Area.L, 60))
    DependencyCheck.Requirement(
      PortabilityCheck.all.find(_.api == "java.time.").get, row,
      Map(Platform.ScalaJs -> dep, Platform.ScalaNative -> dep),
      "java.time.Instant", Origin.synthetic, UsageKind.MemberType, SymId.None)

  /** an external TYPE row, as `ExternalUsage.all` produces one: `owner` is None for a type, so the
    * name the provides-set is matched against is `fullName` itself. */
  private def typeRow(fqn: String) =
    ExternalUsage.Row(SymId.None, fqn, scala.None, fqn.split('.').last, scala.None,
      List(Usage(UsageKind.MemberType, Tree.Literal(Constant.NullC, TypeRepr.NoType, Origin.synthetic))))

  private def known(fqns: String*): ArtifactDep => DependencyCheck.Provides =
    _ => DependencyCheck.Provides.Known(fqns.toSet)

  private def cellOf(before: List[DependencyCheck.Requirement], beforeExt: List[ExternalUsage.Row],
                     after: List[DependencyCheck.Requirement], afterExt: List[ExternalUsage.Row],
                     dep: ArtifactDep, provides: ArtifactDep => DependencyCheck.Provides) =
    DependencyCheck.declarations(List(dep), before, beforeExt, after, afterExt, provides).head

  test("2×2 yes/yes — COVERED: the ordinary case, and the one today's single walk already got right") {
    val d = cellOf(List(req(Time)), Nil, List(req(Time)), Nil, Time, known())
    assertEquals(d.cell, DependencyCheck.Cell.Covered)
    assert(d.cell.keep)
    assertEquals(DependencyCheck.unneeded(List(d)), Nil)
  }

  test("2×2 no/yes — INTRODUCED: a phase redirected INTO the artifact, and the entry must STAY") {
    // the pre-pipeline program uses nothing this artifact answers; the emitted program names one of
    // its classes outright, because a `type-redirect` put it there (DESIGN.md §8.19). Today's check
    // reports this as a coordinate that fired on nothing and tells the reader to remove it, which
    // emits a build that cannot resolve the code the redirect wrote.
    val emitted = List(typeRow("org.example.wrapper.Providers"))
    val d = cellOf(Nil, Nil, Nil, emitted, Wrapper, known("org.example.wrapper.Providers"))
    assertEquals(d.cell, DependencyCheck.Cell.Introduced)
    assert(d.cell.keep, "the coordinate the emitted code names must never be reported as removable")
    assertEquals(DependencyCheck.unneeded(List(d)), Nil)
    // …and it is now VISIBLE, which is the other half of P8: both usage lanes were blind to it.
    assertEquals(DependencyCheck.reportDeclared(List(d)).map(_.kind), List("introduced by translation"))
  }

  test("2×2 yes/no — STALE: the port rewrote away its last usage, and the remove instruction is right") {
    // the case the pre-pipeline-only walk would have LOST — which is why the fix is a pair of
    // programs and not a better single one.
    val d = cellOf(List(req(Time)), Nil, Nil, Nil, Time, known())
    assertEquals(d.cell, DependencyCheck.Cell.Stale)
    assert(!d.cell.keep)
    assertEquals(DependencyCheck.unneeded(List(d)).map(_._1), List(Time))
    assert(DependencyCheck.unneeded(List(d)).head._2.contains("rewrote away the last usage"))
  }

  test("2×2 no/no — UNUSED: copied from another module, and the remove instruction is right") {
    val d = cellOf(Nil, Nil, Nil, Nil, Time, known())
    assertEquals(d.cell, DependencyCheck.Cell.Unused)
    assert(!d.cell.keep)
    assertEquals(DependencyCheck.unneeded(List(d)).map(_._1), List(Time))
  }

  test("…and an UNREADABLE jar is a THIRD value, never a `no`") {
    // §4.6: a default the caller cannot tell from a real answer is a fabricated fact. Read as "the
    // artifact provides nothing" an offline run invents a remove instruction for a live coordinate;
    // read as "it provides everything" it silences every genuinely stale one. So the cell says so,
    // and it KEEPS — a run that knows less does not get to give an instruction.
    val d = cellOf(Nil, Nil, Nil, Nil, Wrapper,
      _ => DependencyCheck.Provides.Unverifiable("no network"))
    assertEquals(d.cell, DependencyCheck.Cell.Unverifiable)
    assert(d.cell.keep)
    assertEquals(DependencyCheck.unneeded(List(d)), Nil)
    assert(d.emitted.why.contains("no network"), d.emitted.why)
  }

  test("the CATALOG half is asked first, so a covered coordinate resolves NOTHING") {
    // not an optimisation: it is what keeps fourteen of fifteen ports off the network entirely, and
    // it is why this change cannot move a port that declares only catalog-answered artifacts.
    var asked = 0
    val d = cellOf(List(req(Time)), Nil, List(req(Time)), Nil, Time,
      { _ => asked += 1; DependencyCheck.Provides.Unverifiable("must not be reached") })
    assertEquals(d.cell, DependencyCheck.Cell.Covered)
    assertEquals(asked, 0)
  }

  test("the emitted column is a SUPERSET of the old test — a finding can only turn OFF") {
    // the flatness argument, mechanised: wherever the old `unneeded` was silent (a requirement named
    // the artifact) the new one is silent too, whatever the provides-set says.
    val silentBefore = List(req(Time))
    List(known(), known("anything.at.All"), (_: ArtifactDep) => DependencyCheck.Provides.Unverifiable("x"))
      .foreach { p =>
        assertEquals(DependencyCheck.unneeded(
          DependencyCheck.declarations(List(Time), Nil, Nil, silentBefore, Nil, p)), Nil)
      }
  }

  test("a NESTED class matches at a SEPARATOR and never by prefix") {
    // the provides-set holds every enclosing prefix, so the test is equality (§4.56). A jar declaring
    // `a.b.Outer` must not answer for `a.b.OuterThing`.
    val d = cellOf(Nil, Nil, Nil, List(typeRow("a.b.OuterThing")), Wrapper, known("a.b.Outer"))
    assertEquals(d.cell, DependencyCheck.Cell.Unused)
    val nested = cellOf(Nil, Nil, Nil, List(typeRow("a.b.Outer$Inner")), Wrapper,
      known("a.b.Outer", "a.b.Outer$Inner"))
    assertEquals(nested.cell, DependencyCheck.Cell.Introduced)
  }

  test("a MEMBER row is asked about its OWNER — the jar declares types, not members") {
    val member = ExternalUsage.Row(SymId.None, "org.example.wrapper.Providers#load",
      Some("org.example.wrapper.Providers"), "load", scala.None,
      List(Usage(UsageKind.Call, Tree.Literal(Constant.NullC, TypeRepr.NoType, Origin.synthetic))))
    val d = cellOf(Nil, Nil, Nil, List(member), Wrapper, known("org.example.wrapper.Providers"))
    assertEquals(d.cell, DependencyCheck.Cell.Introduced)
  }

  // ---- the THIRD evidence: a name a phase SPLICED, which no symbol table holds ----------------

  private def cellWithSpliced(after: List[ExternalUsage.Row], spliced: Set[String],
                              provides: ArtifactDep => DependencyCheck.Provides) =
    DependencyCheck.declarations(List(Wrapper), Nil, Nil, Nil, after,
      provides, splicedAfter = spliced).head

  test("a `call-site-substitution` ALONE answers the emitted column — the static-utility shape") {
    // The shape P8's fix cannot see through either of its two evidences: the port rewrites the CALL
    // and declares no TYPE, so the emitted program names the artifact on every line the template
    // wrote and interns NOTHING for it (`Tree.Opaque.raw` is text the engine deliberately does not
    // parse). Both halves read `No`, the cell is `Stale`, and the instruction says remove the
    // coordinate the emitted code cannot compile without — P8 re-entering through the other seam.
    // liqp is masked only because its `type-redirect` interns a symbol for the same artifact.
    val spliced = Set("org.example.wrapper.Providers.load")
    val d = cellWithSpliced(Nil, spliced, known("org.example.wrapper.Providers"))
    assertEquals(d.cell, DependencyCheck.Cell.Introduced)
    assert(d.cell.keep, "a coordinate only the spliced text names must never be reported removable")
    assertEquals(DependencyCheck.unneeded(List(d)), Nil)
    assert(clue(d.emitted.why).contains("spliced"))
    // …and with the same jar and no spliced name it is still `Unused`: the evidence is what moved
    // the cell, not the artifact being readable.
    assertEquals(cellWithSpliced(Nil, Set.empty, known("org.example.wrapper.Providers")).cell,
                 DependencyCheck.Cell.Unused)
  }

  test("a spliced name is cut at a SEPARATOR against the jar's own listing, never by prefix") {
    // the member half: `…Providers.load` is not a class, and only the artifact's listing can say
    // which prefix of it is (§4.56).
    assert(DependencyCheck.namesClass("a.b.Providers.load", Set("a.b.Providers")))
    assert(DependencyCheck.namesClass("a.b.Providers", Set("a.b.Providers")))
    assert(!DependencyCheck.namesClass("a.b.ProvidersThing.load", Set("a.b.Providers")))
    assert(!DependencyCheck.namesClass("a.b.Other", Set("a.b.Providers")))
  }

  test("…and the SPLICED half never answers the ORIGINAL column — the pre-pipeline tree has no text") {
    // asymmetric on purpose: passing it to both columns would answer `Covered` where the truth is
    // `Introduced`, and `Introduced` is the cell that has to SAY a phase put the artifact there.
    val d = cellWithSpliced(Nil, Set("org.example.wrapper.Providers.load"),
      known("org.example.wrapper.Providers"))
    assertEquals(d.cell, DependencyCheck.Cell.Introduced)
    assert(clue(d.original.why).contains("no reference"))
  }

  test("the dotted runs of a real substitution TEMPLATE, hole markers and all") {
    val tmpl = balticporter.transform.CallSiteSubstitutionTransform.Template
      .parse("org.example.wrapper.Providers.load({arg0}, {recv}.tag)").toOption.get
    val op = tmpl.splice(Some(Tree.Literal(Constant.NullC, TypeRepr.NoType, Origin.synthetic)),
      List(Tree.Literal(Constant.IntC(1), TypeRepr.NoType, Origin.synthetic)),
      TypeRepr.NoType, Origin.synthetic)
    val runs = DependencyCheck.dottedRuns(op.asInstanceOf[Tree.Opaque].raw)
    // the FQN survives whole; the NUL hole marker is not an identifier character, so a spliced TERM
    // can never be glued onto the literal name in front of it.
    assert(clue(runs).contains("org.example.wrapper.Providers.load"))
    assert(runs.forall(r => !r.contains(Tree.Opaque.Mark)))
  }

  test("…and the runs are read off the PROGRAM, so any phase that mints a `Tree.Opaque` is covered") {
    // derived and never asked of the phases (`CLAUDE.md` §1): a phase is the one thing that could be
    // wrong about what it introduced, and the tree simply has the node.
    val body = Tree.Opaque("org.example.wrapper.Providers.load()", TinyProgram.tInt, TinyProgram.O)
    val add  = TinyProgram.addDef.copy(rhs = Some(body))
    val foo  = TinyProgram.foo.copy(body = List(TinyProgram.countDef, add))
    val p    = new Program(List(foo), TinyProgram.symbols, Xref.build(List(foo)), MemberIndex.empty)
    assert(clue(DependencyCheck.splicedNames(p)).contains("org.example.wrapper.Providers.load"))
    // a program with no spliced text answers the empty set rather than anything derived from names.
    assertEquals(DependencyCheck.splicedNames(TinyProgram.program), Set.empty[String])
  }

  test("…and (unknown, yes) says UNKNOWN rather than asserting there was no original usage") {
    // the one pair the four cells could not spell. The columns are not asked the same way: the
    // emitted one can answer `Yes` from the CATALOG half, which needs no jar, and the original one
    // then falls through to a listing this run could not read. `Introduced`'s advice opens with
    // "no ORIGINAL usage names this artifact", which is exactly what is not known — §4.6's
    // fabricated fact in the column that decides nothing. Same KEEP; a different sentence.
    val d = DependencyCheck.declarations(List(Time), Nil, Nil, List(req(Time)), Nil,
      _ => DependencyCheck.Provides.Unverifiable("no network")).head
    assertEquals(d.cell, DependencyCheck.Cell.IntroducedOriginalUnknown)
    assert(d.cell.keep)
    assertEquals(DependencyCheck.unneeded(List(d)), Nil)
    assert(clue(d.cell.advice).contains("UNKNOWN"))
    assert(!clue(d.cell.advice).contains("no ORIGINAL usage"))
  }

  test("a port that declares NOTHING records an honest zero on the declared lane") {
    assertEquals(DependencyCheck.declarations(Nil, Nil, Nil, Nil, Nil, known()), Nil)
    assertEquals(DependencyCheck.reportDeclared(Nil), Nil)
  }

  test("…and a declaration that covers NOTHING is a policy finding, not silence") {
    // The direction the lane itself cannot show: coverage SUBTRACTS, so an entry naming an artifact
    // no requirement wants leaves `dependency-coverage` exactly where it was — 0 before, 0 after —
    // and the port ships a jar on every backend for a call it does not make.
    val time    = ArtifactDep("io.github.cquiroz", "scala-java-time", "2.6.0")
    val locales = ArtifactDep("io.github.cquiroz", "scala-java-locales", "1.5.4")
    def unneeded(declared: List[ArtifactDep], reqs: List[DependencyCheck.Requirement]) =
      DependencyCheck.unneeded(
        DependencyCheck.declarations(declared, reqs, Nil, reqs, Nil, known())).map(_._1)
    // the one the requirement names is NOT reported, whatever version the port pinned
    assertEquals(unneeded(List(time), List(req(time))), Nil)
    assertEquals(unneeded(List(time.copy(rev = "9.9.9")), List(req(time))), Nil)
    // the one nothing names IS, and only that one
    assertEquals(unneeded(List(time, locales), List(req(time))), List(locales))
    // a port that declares nothing has nothing to answer for — the empty parameter is the no-op
    assertEquals(unneeded(Nil, List(req(time))), Nil)
    // …and with no requirement at all, every declaration is one (the shape a port reaches after an
    // upstream change removes the last call, which nothing else in the run can see)
    assertEquals(unneeded(List(time), Nil), List(time))

    // and its CLASSIFICATION, which is `core`'s half: `NeverApplied` rather than either neighbour —
    // the entry is well formed and names a real artifact, and what did not happen is the requirement.
    val report = balticporter.core.PolicyReport.fromDependencies(List(locales -> "unused — remove it"))
    assertEquals(report.findings.map(_.issue), List(balticporter.core.PolicyIssue.NeverApplied))
    assertEquals(report.findings.map(_.key), List(locales.toString))
    assertEquals(report.findings.map(_.setting), List("PortManifest.dependencies"))
    // …and the DETAIL is the CHECK's sentence now, so the reader is told WHICH removable cell they
    // are in rather than one paragraph naming a blind spot nothing had closed.
    assertEquals(report.findings.map(_.detail), List("unused — remove it"))
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
