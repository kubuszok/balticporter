package balticporter.tir

import balticporter.catalog.{ApiRow, ArtifactDep, DiffId, Platform}

/** Which third-party ARTIFACTS the port's declared backends need, and which of them its build does
  * not name.
  *
  * ==Why this is not a portability finding==
  * `PortabilityCheck` reports an API the port must stop calling. Half of the catalog's platform
  * answers are not that: `java.time`, `java.util.Locale`, `java.text.DecimalFormat`,
  * `MessageDigest`, `WeakReference` all EXIST off the JVM — in an artifact the build has to add.
  * Reported as unportability, the finding is unanswerable: the reader is told to remove a call that
  * a one-line `libraryDependencies` entry makes correct, and the two mistakes it invites are the
  * expensive ones (rewriting working code, or baselining the lane and ignoring it). So a
  * `Verdict.Depend` gets a finding KIND of its own, and it is a BUILD-GRAPH question rather than a
  * symbol-reference one.
  *
  * ==The three conjuncts, and where each of them lives==
  * A finding is *a usage FIRED* ∧ *no declared dependency covers it* ∧ *the port declared no
  * alternative*. Two of the three are structural rather than filters, and that is deliberate:
  *
  *   - the USAGE is [[requirements]] — the same `ExternalUsage` walk `PortabilityCheck` performs,
  *     over the rules whose cited row answers `Depend` on a platform this port targets. A row
  *     nothing references produces nothing;
  *   - the ALTERNATIVE is read THROUGH `PortManifest.verdictOverrides`, in
  *     [[balticporter.catalog.ApiRow.verdictOn]]. A port that declared it ships its own shim,
  *     vendors a subset or accepts the refusal has changed the verdict, so the row is no longer
  *     `Depend` and never becomes a requirement at all. Written as a second FILTER this conjunct
  *     could disagree with the first — one of them reading the override and the other not — which
  *     is the shape of a check that reports a row it has already excused;
  *   - the COVERAGE is [[uncovered]], and it is the only real filter: does anything in
  *     `PortManifest.dependencies` name this artifact?
  *
  * Each conjunct is still separately OBSERVABLE, which is what the finding's detail says: how many
  * dependencies the port declares, and which catalog row and platform went unanswered. A reader who
  * cannot tell "declared nothing" from "declared the wrong thing" has to go and look.
  *
  * ==What the corpus declares, and what the lane reads once it does==
  * Three ports answer this lane now — libGDX core (`scala-java-locales`, for `I18NBundle`'s 37
  * `Locale` sites) and liqp's two source sets (`scala-java-time` and `scala-java-locales`, plus
  * `scala-java-time-tzdb` on the suite, which is the one module that calls `ZoneId.of(String)`).
  * Their residue is 0 and their `(all)` enumeration is unchanged, which is exactly the shape a
  * DRAINED lane has here: coverage subtracts from the residue and never from the walk.
  *
  * The remaining ports report an honest 0 for the OTHER reason — every requirement their program
  * holds belongs to a base, and D2 removes it. That is why both lanes are recorded: with only the
  * residue, a port that declared the artifacts and a port whose requirements are all its base's read
  * the same number.
  *
  * ==…and the direction coverage cannot show==
  * Because coverage SUBTRACTS, a declaration that answers nothing leaves both numbers where they
  * were. [[unneeded]] is that half, and it is reported on the `policy` lane rather than here: it is a
  * declared key that fired on nothing, which is not a fact about this port's PORTABILITY at all.
  */
object DependencyCheck:

  /** the RESIDUE lane: requirements that survived BOTH filters — this module's own emitted code
    * (D2), and no declared dependency covering them. The number a reader acts on. */
  val Name = "dependency-coverage"

  /** …and the ENUMERATION behind it — every requirement the walk found, before either filter.
    *
    * `portability(all|emitted)`'s reason, one check over: a residue of zero and a walk that found
    * nothing are the same row, and a dependent port is exactly where they come apart. A dependent's
    * program holds its BASE's units, so `inEmittedCode` legitimately removes every requirement that
    * belongs to the base — and the honest `0` it then reports is indistinguishable from a rule list
    * that matched nothing, a target set that emptied it, or a walk that broke. With both lanes the
    * difference is one subtraction.
    *
    * NOT spelled `(emitted)`, because the residue passes TWO filters and naming one of them would
    * hide the other: a requirement can leave this port's number by belonging to the base OR by being
    * covered by a declared dependency, and those are different facts about the port. */
  val All = "dependency-coverage(all)"

  val Classification: String =
    "[§1(b) PER-LIBRARY, in the port's manifest] the API exists off the JVM, in an artifact this " +
      "build does not name. Add it to `PortManifest.dependencies`, or record the port's own answer " +
      "with a `verdictOverrides` entry — a shim, a vendored subset, or an accepted refusal. This is " +
      "NOT a call to remove."

  /** one referenced API whose platform answer is an artifact. */
  final case class Requirement(
      rule: PortabilityCheck.Rule,
      row: ApiRow,
      /** the declared targets that need it, and the artifact each of them names. Two platforms can
        * want DIFFERENT artifacts for one API — `MessageDigest` is `scala-crypto` on Scala.js and
        * `scala-native-crypto` on Native — so this is a map and not one coordinate. */
      deps: Map[Platform, ArtifactDep],
      api: String,
      origin: Origin,
      kind: UsageKind,
      enclosing: SymId,
  ):
    def id: DiffId = row.id
    def render: String =
      s"$api — needs ${deps.toList.sortBy(_._1.toString).map((p, d) => s"$p: $d").mkString(", ")}" +
        s"  (${origin.javaPath}:${origin.line})"

  /** THE walk. Identical in mechanism to `PortabilityCheck.check` — same enumeration, same matcher —
    * over the complementary half of the rule list. */
  def requirements(
      program: Program,
      targets: Set[Platform],
      overrides: Map[DiffId, Map[Platform, balticporter.catalog.Verdict]] = Map.empty,
  ): List[Requirement] =
    val rules = PortabilityCheck.dependencyRulesFor(targets, overrides)
    if rules.isEmpty then Nil
    else
      ExternalUsage.all(program).flatMap { row =>
        val hit = rules.find { r =>
          if r.exactMember then row.member.contains(r.api) else PortabilityCheck.names(r, row.fullName)
        }
        hit match
          case scala.None => Nil
          case Some(r) =>
            val api  = if r.exactMember then row.member.getOrElse(r.api) else row.fullName
            val apiRow = PortabilityCheck.rowOf(r).get // `dependencyRulesFor` only yields cited rules
            val deps = targets.toList.flatMap(p => apiRow.verdictOn(p, overrides).dependency.map(p -> _)).toMap
            row.usages.map(u => Requirement(r, apiRow, deps, api, u.site.origin, u.kind, u.enclosing))
      }

  /** …and the one genuine FILTER: an artifact the port's build already names.
    *
    * Matched on organisation and artifact NAME, never on the revision: a port pinning a different
    * version has answered the question, and telling it otherwise would make this lane a version
    * police nobody asked for — the catalog's `rev` is the version the survey checked, not a floor. */
  def uncovered(reqs: List[Requirement], declared: List[ArtifactDep]): List[Requirement] =
    val have = declared.map(d => (d.org, d.name)).toSet
    reqs.filterNot(r => r.deps.values.forall(d => have((d.org, d.name))))

  /** …and the SAME FILTER READ BACKWARDS: a declared artifact no requirement in this module's own
    * emitted code names.
    *
    * [[uncovered]] is the residue a port ACTS on; this is the one nothing could see. A
    * `dependencies` entry is a §1(b) policy key like any other, and a key that fires on nothing is
    * exactly what `PolicyReport` exists to report — a coordinate copied from another module,
    * surviving an upstream change that removed the last call, or answering a row this port has since
    * overridden. Silently accepted it costs a resolution and a jar on every backend, and the lane it
    * was written for reads a clean `0` either way, because coverage subtracts and never adds.
    *
    * It is `NeverApplied` and not `NeverMatched`: the entry is well formed and names a real
    * artifact, and what did not happen is the REQUIREMENT — the reader's action is to find out
    * whether the usage went away or whether it lives somewhere this walk cannot see, which is a
    * question the two neighbours would send them to the wrong place for.
    *
    * Asked of the EMITTED requirements ([[inEmittedCode]]) rather than of the whole walk, for D2's
    * own reason in the other direction: a dependent's program holds its base's units, so reading the
    * unfiltered list would credit a dependent's declaration for a call only its base makes — the
    * artifact its own build does not need. A hand-written source set is the one thing this cannot
    * see, and the detail says so rather than the check guessing. */
  def unneeded(reqs: List[Requirement], declared: List[ArtifactDep]): List[ArtifactDep] =
    val wanted = reqs.flatMap(_.deps.values).map(d => (d.org, d.name)).toSet
    declared.filterNot(d => wanted((d.org, d.name)))

  /** Violations occurring in code this run actually EMITS — the same D2 filter every other check
    * carries, for the same measured reason: a dependent's program holds its base's units, and an
    * artifact a base's declaration needs is the BASE's to add. */
  def inEmittedCode(program: Program, reqs: List[Requirement], isExcluded: SymId => Boolean): List[Requirement] =
    reqs.filterNot(r => PortabilityCheck.owningType(program, r.enclosing).exists(isExcluded))

  /** @param lane which of the two counts these rows are being recorded as — [[All]] or [[Name]].
    *   A parameter rather than a constant for `PortabilityCheck.Violation.report`'s reason: the
    *   rows are the same shape, the lane is the caller's question, and a finding that hard-coded
    *   one name would put both counts in one bucket in `findings.tsv`. */
  def report(reqs: List[Requirement], declared: List[ArtifactDep], lane: String = Name)
            (using program: Program): List[CheckReport.Finding] =
    reqs.map { r =>
      val want = r.deps.toList.sortBy(_._1.toString).map((p, d) => s"$p needs $d").mkString("; ")
      CheckReport.Finding(lane, r.api,
        program.symbolOf(r.enclosing).map(_.fullName).getOrElse("?"),
        CheckReport.relativise(r.origin.javaPath), r.origin.line,
        s"${r.kind} — $want. This port declares ${declared.size} dependency/ies and no " +
          s"`verdictOverrides` entry for ${r.id}: ${r.rule.why}")
    }

  /** grouped one-line summary, most-referenced first — `PortabilityCheck.summary`'s shape, because
    * an operator reads the two lines together. */
  def summary(reqs: List[Requirement]): String =
    if reqs.isEmpty then "  none"
    else
      reqs.groupBy(r => (r.api, r.deps)).toList.sortBy(-_._2.size)
        .map { case ((api, deps), rs) =>
          s"  $api: ${rs.size} site(s) — ${deps.toList.sortBy(_._1.toString).map((p, d) => s"$p: $d").mkString(", ")}"
        }
        .mkString("\n")
