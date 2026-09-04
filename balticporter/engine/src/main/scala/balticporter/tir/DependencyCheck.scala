package balticporter.tir

import balticporter.catalog.{ApiRow, ArtifactDep, DiffId, Platform}

/** Which third-party ARTIFACTS the port's declared backends need, and which of them its build does
  * not name. `PortabilityCheck` reports an API to STOP CALLING; half the catalog's platform
  * answers instead mean the API EXISTS off the JVM in an artifact the build must add (java.time,
  * java.util.Locale, DecimalFormat, MessageDigest, WeakReference), so reporting those as
  * unportability would tell the reader to remove a call a one-line dependency entry makes correct.
  * `Verdict.Depend` gets its own finding kind — a BUILD-GRAPH question, not a symbol-reference one.
  *
  * A finding is *a usage FIRED* ∧ *no declared dependency covers it* ∧ *no declared alternative*.
  * The usage is [[requirements]] (an ExternalUsage walk over Depend-verdict rules); the
  * alternative is read THROUGH `PortManifest.verdictOverrides` structurally, not as a second
  * filter that could disagree; the coverage is [[uncovered]], the only real filter. [[unneeded]]
  * is the opposite direction — a declared coordinate answering nothing — reported on `policy`
  * instead, since it is not a fact about this port's portability.
  *
  * THE 2×2: "does this coordinate answer anything" asked of one (post-pipeline) program is wrong
  * for a coordinate a phase redirected INTO — the redirect removes the very JDK usage the
  * coordinate answers, so the walk then finds nothing and wrongly recommends removing an artifact
  * the emitted code needs (ENGINE-LIMITS P8). The answer is TWO programs and a 2×2 cell:
  * {{{
  *                       EMITTED uses it?
  *                        yes            no
  *   ORIGINAL   yes   Covered        Stale        <- the port rewrote away its last usage
  *   uses it?    no   Introduced     Unused       <- copied, or an upstream change removed the call
  * }}}
  * The EMITTED column alone decides keep-or-remove (Covered/Introduced keep, Stale/Unused don't);
  * the ORIGINAL column decides the SENTENCE (an upstream call that vanished vs. an entry that
  * never had one). Both columns are one union ([[uses]]) over a different Program: a catalog row's
  * Depend naming the artifact ([[requirements]]); the artifact's OWN CLASS LIST answering a named
  * reference (read FROM the jar, never derived from the coordinate — §4.56's hazard at a build
  * coordinate); or the artifact's class list answering a name a phase SPLICED in as LITERAL TEXT
  * ([[splicedNames]], derived from the emitted program since a Tree.Opaque has no interned symbol
  * for ExternalUsage to see). [[Provides]] is THREE-valued: a jar that cannot be fetched is
  * `Unverifiable` ([[Cell.Unverifiable]], KEEPS, says why) rather than collapsing either way
  * (§4.6's fabricated fact). The catalog half is asked FIRST, so most ports resolve no jar at all.
  */
object DependencyCheck:

  /** the RESIDUE lane: requirements that survived BOTH filters — this module's own emitted code
    * (D2), and no declared dependency covering them. The number a reader acts on. */
  val Name = "dependency-coverage"

  /** the ENUMERATION behind it — every requirement the walk found, before either filter
    * (`portability(all|emitted)`'s reason one check over: a residue of zero and an empty walk are
    * the same row, and a dependent port is where they come apart). NOT spelled `(emitted)`: the
    * residue passes TWO filters (belonging to the base, or covered by a declared dependency). */
  val All = "dependency-coverage(all)"

  /** the THIRD artifact of the family, counting DECLARATIONS rather than usage sites: `policy = 0`
    * is a bar a port can hold by DECLARING NOTHING, and the two lanes above cannot show it (they
    * enumerate JDK usages, so a redirected-into artifact has no row — ENGINE-LIMITS P8). One row
    * per declared coordinate, naming its [[Cell]]; the `policy` residue is the subtraction. A run
    * that declares nothing records `0`, a fact about that port
    * and not an exemption (`jdk-surface`'s own reason). */
  val Declared = "dependency-coverage(declared)"

  val Classification: String =
    "[§1(b) PER-LIBRARY, in the port's manifest] the API exists off the JVM, in an artifact this " +
      "build does not name. Add it to `PortManifest.dependencies`, or record the port's own answer " +
      "with a `verdictOverrides` entry — a shim, a vendored subset, or an accepted refusal. This is " +
      "NOT a call to remove."

  /** one referenced API whose platform answer is an artifact. */
  final case class Requirement(
      rule: PortabilityCheck.Rule,
      row: ApiRow,
      /** the declared targets that need it, and the artifact each names — two platforms can want
        * DIFFERENT artifacts for one API, so this is a map and not one coordinate. */
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

  /** same enumeration and matcher as `PortabilityCheck.check`, over the complementary rule half. */
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

  /** filters requirements already covered by a declared artifact, matched on org+name — never the
    * revision: a port pinning a different version has already answered the question. */
  def uncovered(reqs: List[Requirement], declared: List[ArtifactDep]): List[Requirement] =
    val have = declared.map(d => (d.org, d.name)).toSet
    reqs.filterNot(r => r.deps.values.forall(d => have((d.org, d.name))))

  /** what an artifact says it provides, read from the jar and nothing else. THREE-valued:
    * [[Unverifiable]] is a jar this run could not read (no network, dropped snapshot, no `cs`) — not
    * a coordinate that provides nothing, since collapsing it either way fabricates an answer (§4.6).
    * [[Known]] holds CLASS names as `Symbol.fullName` spells them, every enclosing prefix of a
    * nested entry included, so a match is equality and never `startsWith` (§4.56). */
  enum Provides:
    case Known(classes: Set[String])
    case Unverifiable(why: String)

  /** WHICH of [[uses]]' three evidences answered — a value, never re-derived from the `why` prose.
    * Decides whether a JVM compile of the emitted code needs the coordinate on its classpath:
    * [[Catalog]] answers a JDK API the JVM already has (a JS-only need), the other two mean the
    * emitted code NAMES the artifact directly. */
  enum Evidence:
    case Catalog, Classes, Spliced

  /** the answer ONE program gives about ONE artifact — three-valued for [[Provides]]'s reason, and
    * carrying its own evidence for the reader to check. */
  enum Answer(val why: String):
    case Yes(override val why: String, evidence: Evidence) extends Answer(why)
    case No extends Answer("no reference this run can see")
    case Unknown(override val why: String) extends Answer(why)

  /** one cell of the 2×2 — see the object's own doc for the table.
    *
    * @param keep whether the coordinate must STAY. The emitted column alone decides it; the original
    *   column decides which sentence the reader gets. */
  enum Cell(val label: String, val keep: Boolean, val advice: String):
    case Covered extends Cell("covered", true,
      "the API this coordinate answers is called in this module's own emitted code — nothing to do")
    case Introduced extends Cell("introduced by translation", true,
      "no ORIGINAL usage names this artifact and the EMITTED code references it directly: a surface " +
        "phase of this port — a `type-redirect` or a `call-site-substitution` — rewrote into it, " +
        "which is how a `Verdict.Depend` is answered (DESIGN.md §8.19). KEEP the entry: removing it " +
        "emits a build that cannot resolve the code the redirect wrote")
    case Stale extends Cell("stale", false,
      "the ORIGINAL code used the API this coordinate answers and the EMITTED code does not — this " +
        "port dropped or rewrote away the last usage, and the artifact is no longer needed. Remove " +
        "the entry, unless the remaining usage is in a hand-written source this run does not walk")
    case Unused extends Cell("unused", false,
      "no API this module emits needs this artifact on any platform it targets, and none of its " +
        "original code did either — a coordinate copied from another module, or one whose last call " +
        "an upstream change removed. Remove the entry, unless the usage is in a hand-written source " +
        "this run does not walk")
    /** [[Introduced]] with the ORIGINAL column UNKNOWN: the emitted column answered `Yes` from the
      * CATALOG half (no jar needed) while the original column's jar could not be read, so its own
      * sentence would assert what the run does not know (§4.6). KEEP is unchanged either way. */
    case IntroducedOriginalUnknown extends Cell("introduced by translation (original unknown)", true,
      "the EMITTED code needs this coordinate — that half is answered and it KEEPS. Whether the " +
        "ORIGINAL code needed it is UNKNOWN, not `no`: this run could not read the artifact's own " +
        "class list, and the original column is the one that would have distinguished a coordinate " +
        "a phase introduced from one that was always right. No instruction depends on it")
    case Unverifiable extends Cell("unverifiable", true,
      "this run could not read the artifact's own class list, so whether the emitted code references " +
        "it is UNKNOWN — not `no`. No instruction is given, because the two honest ones contradict " +
        "each other and guessing between them is a fabricated fact (CLAUDE.md §4.6)")

  /** one declared coordinate, its cell, and the evidence for each half of it. */
  final case class Declaration(dep: ArtifactDep, cell: Cell, original: Answer, emitted: Answer):
    def render: String = s"$dep — ${cell.label} (original: ${original.why}; emitted: ${emitted.why})"

  /** every dotted name a phase SPLICED into this program as literal text — the third evidence, and
    * the one no symbol table holds. A `Tree.Opaque` is ready-made Scala the engine deliberately does
    * not parse, so every symbol-keyed check reads past it. Walked with `StandardTraversal` over the
    * PROGRAM rather than asked of the phases (`CLAUDE.md` §1 — `Rewrite.accountedBy`).
    *
    * Returns the MAXIMAL dotted run, uncut — `a.b.C.member` and not `a.b.C` — because which prefix
    * is a CLASS only the artifact's own listing can answer (§4.56). [[namesClass]] does the cutting. */
  def splicedNames(program: Program): Set[String] =
    given Program = program
    program.units.foldLeft(Set.empty[String]) { (acc, u) =>
      StandardTraversal.scanClassDef(u, acc) {
        case (a, o: Tree.Opaque) => a ++ dottedRuns(o.raw)
        case (a, _)              => a
      }
    }

  /** the maximal `ident(.ident)+` runs of a piece of ready-made Scala — a CANDIDATE list, each tested
    * for equality against a jar's own class listing.
    *
    * The hole marker (`Tree.Opaque.Mark`, NUL) is an IDENTIFIER-IGNORABLE control character (JLS
    * 3.8), so `Character.isJavaIdentifierPart` answers true for it and it would otherwise glue onto
    * the literal name in front of it. Exclude the whole ignorable CLASS, not just the marker. */
  private[tir] def dottedRuns(raw: String): Set[String] =
    val out = Set.newBuilder[String]
    val cur = new StringBuilder
    def part(c: Char) = Character.isJavaIdentifierPart(c) && !Character.isIdentifierIgnorable(c)
    def flush(): Unit =
      val s = cur.toString.stripSuffix(".")
      if s.contains('.') && !s.head.isDigit then out += s
      cur.setLength(0)
    raw.foreach { c =>
      if part(c) || (c == '.' && cur.nonEmpty) then cur.append(c)
      else flush()
    }
    flush()
    out.result()

  /** does `name` — or any prefix of it cut at a `.` — name a class this artifact declares? Equality
    * at every cut, never `startsWith` (§4.56): `classes` already holds each enclosing prefix, and a
    * spliced name routinely reaches PAST the class into a member. */
  private[tir] def namesClass(name: String, classes: Set[String]): Boolean =
    var i   = name.length
    var hit = classes(name)
    while !hit && i > 0 do
      i = name.lastIndexOf('.', i - 1)
      if i > 0 then hit = classes(name.substring(0, i))
    hit

  /** does THIS program use THIS artifact — the derivation both columns of the 2×2 read. The union of
    * the THREE evidences, catalog half first: a `Depend` row, a reference to a class the artifact
    * declares, or a name a phase SPLICED in as literal text.
    *
    * `external` is the program's own EXTERNAL usage rows, held to this module's emitted code by the
    * caller's D2 predicate — the same list `jdk-surface` reads, never a second walk (§3). `spliced`
    * is [[splicedNames]] over the same program, `Set.empty` for the pre-pipeline one. */
  def uses(dep: ArtifactDep, reqs: List[Requirement], external: List[ExternalUsage.Row],
           provides: ArtifactDep => Provides, spliced: Set[String] = Set.empty): Answer =
    val byCatalog = reqs.filter(_.deps.values.exists(d => (d.org, d.name) == (dep.org, dep.name)))
    if byCatalog.nonEmpty then
      val apis = byCatalog.map(_.api).distinct.sorted
      Answer.Yes(s"${byCatalog.size} site(s) at ${apis.take(3).mkString(", ")}" +
        (if apis.sizeIs > 3 then s" (+${apis.size - 3} more)" else "") + " — a catalog `Depend` row names it",
        Evidence.Catalog)
    else
      provides(dep) match
        case Provides.Unverifiable(why) => Answer.Unknown(why)
        case Provides.Known(classes) =>
          // a member row is asked about its OWNER (a jar declares types); a type row is its own
          // name. Equality against a set holding every enclosing prefix — never a prefix TEST.
          val hits = external.filter(r => classes(r.owner.getOrElse(r.fullName)))
          if hits.nonEmpty then
            val names = hits.map(r => r.owner.getOrElse(r.fullName)).distinct.sorted
            Answer.Yes(s"${hits.map(_.sites).sum} reference(s) to ${names.take(3).mkString(", ")}" +
              (if names.sizeIs > 3 then s" (+${names.size - 3} more)" else "") +
              " — the artifact's own class list declares them", Evidence.Classes)
          else
            // the same listing read against SPLICED TEXT, which has no symbol to be a row.
            val text = spliced.filter(namesClass(_, classes)).toList.sorted
            if text.isEmpty then Answer.No
            else
              Answer.Yes(s"${text.size} spliced name(s) — ${text.take(3).mkString(", ")}" +
                (if text.sizeIs > 3 then s" (+${text.size - 3} more)" else "") +
                " — written as literal Scala by a surface phase, which interns no symbol",
                Evidence.Spliced)

  /** the 2×2 itself: one [[Declaration]] per declared coordinate.
    *
    * The PRE-pipeline halves are BY NAME and forced exactly once, here — never per declaration, since
    * a by-name parameter read inside the `map` would re-walk the program per coordinate.
    *
    * @param before this module's own requirements over the PRE-pipeline program
    * @param beforeExternal …and its external usage rows
    * @param after  the same over the program the run EMITS — [[unneeded]]'s old input, unchanged
    * @param splicedAfter [[splicedNames]] over the EMITTED program only: the pre-pipeline program
    *   holds no spliced text
    */
  def declarations(declared: List[ArtifactDep],
                   before: => List[Requirement], beforeExternal: => List[ExternalUsage.Row],
                   after: List[Requirement], afterExternal: List[ExternalUsage.Row],
                   provides: ArtifactDep => Provides,
                   splicedAfter: Set[String] = Set.empty): List[Declaration] =
    if declared.isEmpty then Nil else
      val originalReqs = before
      val originalExt  = beforeExternal
      declared.map { d =>
        val emitted = uses(d, after, afterExternal, provides, splicedAfter)
        // asked only where it can change the SENTENCE — an emitted `Unknown` gives no instruction
        // whatever the original says.
        val original = emitted match
          case Answer.Unknown(_) => Answer.Unknown("not asked — the emitted column is unverifiable")
          case _                 => uses(d, originalReqs, originalExt, provides)
        val cell = (original, emitted) match
          case (_, Answer.Unknown(_))             => Cell.Unverifiable
          case (Answer.Yes(_, _), Answer.Yes(_, _)) => Cell.Covered
          case (Answer.Yes(_, _), _)                => Cell.Stale
          case (Answer.Unknown(_), Answer.Yes(_, _)) => Cell.IntroducedOriginalUnknown
          case (_, Answer.Yes(_, _))                => Cell.Introduced
          case _                              => Cell.Unused
        Declaration(d, cell, original, emitted)
      }

  /** the same filter read backwards: a declared artifact no requirement in this module's own emitted
    * code names. Reported as `NeverApplied` (not `NeverMatched`): the entry is well formed and names
    * a real artifact, but no requirement fired — the reader checks whether the usage went away or
    * lives somewhere this walk cannot see.
    *
    * The SUBTRACTION of [[declarations]] rather than a second derivation: rows whose [[Cell]] does
    * not [[Cell.keep]], carrying that cell's sentence. A separate filter could disagree with it. */
  def unneeded(decls: List[Declaration]): List[(ArtifactDep, String)] =
    decls.filterNot(_.cell.keep).map { d =>
      d.dep -> (s"${d.cell.label} — ${d.cell.advice}" +
        s"  [original: ${d.original.why}; emitted: ${d.emitted.why}]")
    }

  /** violations in code this run actually EMITS — the same D2 filter every other check carries: an
    * artifact a base's declaration needs is the BASE's to add. */
  def inEmittedCode(program: Program, reqs: List[Requirement], isExcluded: SymId => Boolean): List[Requirement] =
    reqs.filterNot(r => PortabilityCheck.owningType(program, r.enclosing).exists(isExcluded))

  /** @param lane which of the two counts these rows are recorded as — [[All]] or [[Name]] — so a
    *   hard-coded name does not merge both counts into one bucket in `findings.tsv`. */
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

  /** the [[Declared]] lane — one row per declared coordinate, whatever its cell, reported apart from
    * the residue (trivia family's reason).
    *
    * There is no `Origin` for a build coordinate and inventing one would be a fabricated fact, so the
    * `path` column carries the manifest FIELD and the line is 0 (`PolicyReport`'s own convention). */
  def reportDeclared(decls: List[Declaration]): List[CheckReport.Finding] =
    decls.map { d =>
      CheckReport.Finding(Declared, d.cell.label, d.dep.toString,
        balticporter.core.PolicyReport.DependencySetting, 0,
        s"original: ${d.original.why} | emitted: ${d.emitted.why} — ${d.cell.advice}")
    }

  /** the same rows as an ARTIFACT a BUILD can read (`run-latest/dependencies.tsv`) — one value, one
    * spelling, so a measure lane derives its classpath flags from the run rather than duplicating
    * the manifest by hand (`CLAUDE.md` §1.5).
    *
    * `coordinate` is the EXPLICIT jvm form (`org:name_3:rev`), never `cs`'s or scala-cli's `::`
    * shorthand, which picks an ambient suffix that can differ between checkouts.
    *
    * `onClasspath` is DERIVED here because only the run has the evidence: a coordinate whose emitted
    * evidence is the CATALOG half answers a JDK API the JVM already has, so a jvm compile does not
    * need it; a coordinate the emitted code NAMES does. Unknown takes the INCLUDING arm — an unneeded
    * jar costs a resolution, a missing one a wall of unrelated errors. */
  val DeclaredHeader = "#org\tname\trev\tcross\tresolver\tcoordinate\tonClasspath\tcell\twhy"

  def declaredTsv(decls: List[Declaration], coordinateOf: ArtifactDep => String): List[String] =
    decls.map { d =>
      val onCp = d.emitted match
        case Answer.Yes(_, Evidence.Catalog) => false
        case _                               => true
      List(d.dep.org, d.dep.name, d.dep.rev, d.dep.cross.toString.toLowerCase,
           d.dep.resolver.getOrElse(""), coordinateOf(d.dep),
           if onCp then "yes" else "no", d.cell.label, d.emitted.why).mkString("\t")
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
