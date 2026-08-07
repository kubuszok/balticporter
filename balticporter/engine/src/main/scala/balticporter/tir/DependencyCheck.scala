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
  * ==Empty is the honest state, not a hole==
  * No port in this corpus declares a dependency or an override, so every requirement is reported.
  * That is the number this lane exists to make visible: a port that compiles on the one backend
  * somebody happened to test, against an artifact list nobody has written.
  */
object DependencyCheck:

  val Name = "dependency-coverage"

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

  /** Violations occurring in code this run actually EMITS — the same D2 filter every other check
    * carries, for the same measured reason: a dependent's program holds its base's units, and an
    * artifact a base's declaration needs is the BASE's to add. */
  def inEmittedCode(program: Program, reqs: List[Requirement], isExcluded: SymId => Boolean): List[Requirement] =
    reqs.filterNot(r => PortabilityCheck.owningType(program, r.enclosing).exists(isExcluded))

  def report(reqs: List[Requirement], declared: List[ArtifactDep])(using program: Program): List[CheckReport.Finding] =
    reqs.map { r =>
      val want = r.deps.toList.sortBy(_._1.toString).map((p, d) => s"$p needs $d").mkString("; ")
      CheckReport.Finding(Name, r.api,
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
