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
  *
  * ==THE 2×2, and why one program cannot answer it==
  * "Does this coordinate answer anything?" was asked of ONE program — the post-pipeline one — and
  * that is exact for a coordinate copied from another module and WRONG for a coordinate a phase
  * redirected INTO. A `Verdict.Depend` is answered by declaring the artifact *and redirecting into
  * it* (`DESIGN.md` §8.19), and the redirect is what removes the JDK usage — so after it runs the
  * walk finds nothing, [[unneeded]] fires, and its instruction says *remove the entry*, which emits a
  * build that cannot resolve the code the redirect just wrote (`ENGINE-LIMITS.md` P8).
  *
  * The answer is not a better single walk. It is TWO programs, and the cell is the pair:
  *
  * {{{
  *                       EMITTED uses it?
  *                        yes            no
  *   ORIGINAL   yes   Covered        Stale        ← the port rewrote away its last usage
  *   uses it?    no   Introduced     Unused       ← copied, or an upstream change removed the call
  * }}}
  *
  * Read the two axes for what each is FOR, because they are not symmetric in effect:
  *
  *   - **the EMITTED column alone decides keep-or-remove.** `Covered` and `Introduced` both keep;
  *     `Stale` and `Unused` are both a coordinate whose jar the build ships for nothing. That is the
  *     whole of the instruction, and it is why running the old walk over the PRE-pipeline program
  *     was rejected as the fix: it answers the ORIGINAL column, which decides nothing;
  *   - **the ORIGINAL column decides the SENTENCE.** `Stale` and `Unused` want different
  *     investigations (an upstream call that went away, against an entry that never had one), and
  *     `Introduced` has to say *a phase of this port put this artifact in the emitted code* or the
  *     reader has no way to tell it from `Covered` — which is the row `dependency-coverage(all)`
  *     could never hold, because it enumerates JDK usages and a redirected one is gone.
  *
  * ==What each column READS — one derivation, two programs==
  * Both columns are the same union, computed by [[uses]] against a different `Program`:
  *
  *   1. a CATALOG ROW's `Depend` names this artifact, and the walk found the JDK API that row is
  *      about. This is [[requirements]] — today's whole answer — and on the post-pipeline program it
  *      is exactly what [[unneeded]] read before, which is what makes this change provably
  *      one-directional: the emitted column is a SUPERSET of the old test, so a finding can only
  *      turn off and never on;
  *   2. …or the artifact's OWN CLASS LIST answers a reference the program makes by name. That is the
  *      half a redirect needs, and it is READ FROM THE ARTIFACT — never derived from the coordinate.
  *      There is no structural link between `org:name` and a package, so deriving one would be
  *      §4.56's hazard at a build coordinate; the jar is the one authority on what it provides.
  *   3. …or the artifact's class list answers a name a phase SPLICED into the program as LITERAL
  *      TEXT. That third source is not a refinement of the second: an `ExternalUsage` row needs an
  *      INTERNED SYMBOL, and a `Tree.Opaque` has none — its `raw` is ready-made Scala the frontend
  *      never resolved. So a `Depend` answered by a `call-site-substitution` ALONE — the static
  *      utility shape, where the port rewrites the CALL and declares no type — makes an emitted
  *      program that names the artifact on every line the template wrote and reads `No` on both
  *      halves above, lands in `Cell.Stale`, and is told to REMOVE the coordinate its own emitted
  *      code cannot compile without. That is `ENGINE-LIMITS.md` P8's defect re-entering through the
  *      other seam, and it is masked wherever a `type-redirect` runs beside the substitution and
  *      interns a symbol for the same artifact.
  *
  *      It is DERIVED FROM THE EMITTED PROGRAM ([[splicedNames]]) and never asked of the phases,
  *      which is §1's own split read at a check: a phase could be wrong about what it introduced or
  *      silently stop maintaining the answer, while the tree simply HAS the `Tree.Opaque` nodes —
  *      so every phase that mints one is covered, including the ones written after this paragraph.
  *      And it feeds the EMITTED column only: the pre-pipeline program holds no spliced text by
  *      construction, so answering the original column from it would be a fabricated fact (§4.6).
  *
  * [[Provides]] is therefore THREE-valued, and the third value is not a default. A jar that cannot be
  * fetched is `Unverifiable`, which is neither "provides it" nor "does not" — collapsed either way it
  * is §4.6's fabricated fact, silencing a real stale coordinate or inventing a remove instruction for
  * a live one. The cell is [[Cell.Unverifiable]], it KEEPS, and it says why.
  *
  * Note the order the union is evaluated in is load-bearing rather than an optimisation: the catalog
  * half is asked FIRST, so the artifact's jar is consulted only for a coordinate the old check would
  * already have reported. Fourteen of the corpus's fifteen ports therefore resolve nothing at all.
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

  /** …and the THIRD artifact of the family, which counts DECLARATIONS rather than usage sites.
    *
    * The trivia family's argument one lane over: `policy = 0` on this seam is a bar a port can hold
    * by DECLARING NOTHING, and the two lanes above cannot show it either — they enumerate the JDK
    * usages a coordinate would answer, so an artifact a phase redirected INTO has no row anywhere in
    * the run. Both numbers were blind to liqp's `multiarch-serviceloader` for the life of that port
    * (`ENGINE-LIMITS.md` P8).
    *
    * So the positive is reported apart: one row per declared coordinate, naming its [[Cell]] and the
    * evidence for each half of it, and the `policy` residue is the subtraction — the rows whose cell
    * does not [[Cell.keep]]. A run that declares nothing records `0`, which is a fact about that port
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

  /** What an ARTIFACT says it provides, read from the artifact and from nothing else.
    *
    * THREE-valued, and the third value is the point. A coordinate whose jar this run could not read
    * — no network, a repository that has dropped a snapshot, a `cs` that is not installed — is not a
    * coordinate that provides nothing. Collapsed to [[Known]]`(Set.empty)` it would produce a remove
    * instruction for an artifact the port may well need; collapsed the other way it would silence
    * every genuinely stale coordinate on an offline run. Neither is an answer, so [[Unverifiable]]
    * says so and carries the invocation that failed.
    *
    * [[Known]] holds CLASS names as `Symbol.fullName` spells them — `.` between packages and the
    * top-level type, `$` before a nested one — with every enclosing prefix of a nested entry present
    * beside it, so a match is an equality test and never a `startsWith` (§4.56). */
  enum Provides:
    case Known(classes: Set[String])
    case Unverifiable(why: String)

  /** the answer ONE program gives about ONE artifact — three-valued for [[Provides]]'s reason, and
    * carrying its own evidence because "yes" and "yes, for a reason the reader can check" are not
    * the same row in a report somebody has to act on. */
  enum Answer(val why: String):
    case Yes(override val why: String) extends Answer(why)
    case No extends Answer("no reference this run can see")
    case Unknown(override val why: String) extends Answer(why)

  /** one cell of the 2×2 — see the object's own doc for the table.
    *
    * @param keep whether the coordinate must STAY in the build. The emitted column alone decides it;
    *   the original column decides which sentence the reader gets. */
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
    case Unverifiable extends Cell("unverifiable", true,
      "this run could not read the artifact's own class list, so whether the emitted code references " +
        "it is UNKNOWN — not `no`. No instruction is given, because the two honest ones contradict " +
        "each other and guessing between them is a fabricated fact (CLAUDE.md §4.6)")

  /** one declared coordinate, its cell, and the evidence for each half of it. */
  final case class Declaration(dep: ArtifactDep, cell: Cell, original: Answer, emitted: Answer):
    def render: String = s"$dep — ${cell.label} (original: ${original.why}; emitted: ${emitted.why})"

  /** EVERY DOTTED NAME A PHASE SPLICED INTO THIS PROGRAM AS LITERAL TEXT — the third evidence, and
    * the one no symbol table holds.
    *
    * A `Tree.Opaque` is ready-made Scala: a call-site substitution's template, a replaced method
    * body, a generated FFI downcall. The engine deliberately does not parse it (`Tree.Opaque`'s own
    * doc), so its `raw` interns nothing and every check keyed on symbols reads past it — including
    * the `ExternalUsage` walk the emitted column's second evidence is built on.
    *
    * Walked with `StandardTraversal` and read off the PROGRAM rather than asked of the phases, for
    * the reason `Rewrite.accountedBy` is DERIVED rather than declared (`CLAUDE.md` §1): a phase is
    * the one thing that could be wrong about what it introduced, and the tree simply has the nodes.
    * A phase that mints a `Tree.Opaque` tomorrow is covered without knowing this exists.
    *
    * What comes back is the MAXIMAL dotted run, uncut — `a.b.C.member` and not `a.b.C` — because
    * which prefix of it is a CLASS is a question only the artifact's own listing can answer, and
    * cutting it here would be a guess at a name (§4.56). [[namesClass]] does the cutting, at a
    * separator, against a set that already holds every enclosing prefix. */
  def splicedNames(program: Program): Set[String] =
    given Program = program
    program.units.foldLeft(Set.empty[String]) { (acc, u) =>
      StandardTraversal.scanClassDef(u, acc) {
        case (a, o: Tree.Opaque) => a ++ dottedRuns(o.raw)
        case (a, _)              => a
      }
    }

  /** the maximal `ident(.ident)+` runs of a piece of ready-made Scala. Not a parse and not meant to
    * be one: it is a CANDIDATE list, every member of which is then tested for equality against a
    * jar's own class listing, so a run that is not a name matches nothing and costs one lookup.
    *
    * THE HOLE MARKER IS AN IDENTIFIER CHARACTER, which is the one thing about this that is not
    * obvious. `Tree.Opaque.Mark` is NUL, chosen precisely because it cannot occur in Scala source —
    * and `Character.isJavaIdentifierPart` answers TRUE for it, along with every other
    * IDENTIFIER-IGNORABLE control character (JLS 3.8 lets an identifier contain them). Read through
    * that predicate alone the marker glues onto the literal name in front of it, so
    * `…Providers.load(<NUL>0<NUL>` is one run, matches no class, and the whole evidence is silently
    * empty for every template that has a hole — which is every template worth writing. The
    * ignorable set is excluded rather than the marker alone, because what makes this wrong is the
    * CLASS of character and not the one this engine happens to have picked. */
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

  /** does `name` — or any prefix of it cut at a `.` — name a class this artifact declares?
    *
    * Equality against the listing at every cut, never a `startsWith` (§4.56): `classes` already
    * holds each enclosing prefix of a nested entry, and a spliced name routinely reaches PAST the
    * class into a member (`…PlatformServiceLoader.load`). */
  private[tir] def namesClass(name: String, classes: Set[String]): Boolean =
    var i   = name.length
    var hit = classes(name)
    while !hit && i > 0 do
      i = name.lastIndexOf('.', i - 1)
      if i > 0 then hit = classes(name.substring(0, i))
    hit

  /** does THIS program use THIS artifact — the one derivation both columns of the 2×2 read.
    *
    * The union of the THREE evidences, catalog half first (see the object doc for why that order is
    * load-bearing): a `Depend` row naming this artifact that the walk answered, a reference the
    * program makes to a class the artifact itself declares, or a name a phase SPLICED into it as
    * literal text.
    *
    * `external` is the program's own EXTERNAL usage rows, already held to this module's emitted code
    * by the caller's D2 predicate — the same list `jdk-surface` reads, never a second walk (§3).
    * `spliced` is [[splicedNames]] over the same program, and is `Set.empty` for the pre-pipeline
    * one because no phase has run there. */
  def uses(dep: ArtifactDep, reqs: List[Requirement], external: List[ExternalUsage.Row],
           provides: ArtifactDep => Provides, spliced: Set[String] = Set.empty): Answer =
    val byCatalog = reqs.filter(_.deps.values.exists(d => (d.org, d.name) == (dep.org, dep.name)))
    if byCatalog.nonEmpty then
      val apis = byCatalog.map(_.api).distinct.sorted
      Answer.Yes(s"${byCatalog.size} site(s) at ${apis.take(3).mkString(", ")}" +
        (if apis.sizeIs > 3 then s" (+${apis.size - 3} more)" else "") + " — a catalog `Depend` row names it")
    else
      provides(dep) match
        case Provides.Unverifiable(why) => Answer.Unknown(why)
        case Provides.Known(classes) =>
          // the TYPE is the thing a jar declares, so a member row is asked about its OWNER; a type
          // row IS its own name. Equality against a set that already holds every enclosing prefix —
          // never a prefix TEST, which is the hazard this whole derivation exists to avoid.
          val hits = external.filter(r => classes(r.owner.getOrElse(r.fullName)))
          if hits.nonEmpty then
            val names = hits.map(r => r.owner.getOrElse(r.fullName)).distinct.sorted
            Answer.Yes(s"${hits.map(_.sites).sum} reference(s) to ${names.take(3).mkString(", ")}" +
              (if names.sizeIs > 3 then s" (+${names.size - 3} more)" else "") +
              " — the artifact's own class list declares them")
          else
            // …and the same listing read against SPLICED TEXT, which has no symbol to be a row.
            val text = spliced.filter(namesClass(_, classes)).toList.sorted
            if text.isEmpty then Answer.No
            else
              Answer.Yes(s"${text.size} spliced name(s) — ${text.take(3).mkString(", ")}" +
                (if text.sizeIs > 3 then s" (+${text.size - 3} more)" else "") +
                " — written as literal Scala by a surface phase, which interns no symbol")

  /** the 2×2 itself: one [[Declaration]] per declared coordinate.
    *
    * The PRE-pipeline halves are BY NAME and forced exactly once, here: twelve of the corpus's
    * fifteen ports declare no dependency at all, and a second whole-program walk none of them can
    * use is a walk that could only ever go wrong. Forced once and not per declaration, because a
    * by-name parameter read inside the `map` would re-walk the program per coordinate.
    *
    * @param before this module's own requirements over the PRE-pipeline program
    * @param beforeExternal …and its external usage rows
    * @param after  the same over the program the run EMITS — [[unneeded]]'s old input, unchanged
    * @param splicedAfter [[splicedNames]] over the EMITTED program, and over that one only: the
    *   pre-pipeline program holds no spliced text, so there is no original column to feed
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
        // whatever the original column says, so there is nothing for a second walk to decide.
        val original = emitted match
          case Answer.Unknown(_) => Answer.Unknown("not asked — the emitted column is unverifiable")
          case _                 => uses(d, originalReqs, originalExt, provides)
        val cell = (original, emitted) match
          case (_, Answer.Unknown(_))         => Cell.Unverifiable
          case (Answer.Yes(_), Answer.Yes(_)) => Cell.Covered
          case (Answer.Yes(_), _)             => Cell.Stale
          case (_, Answer.Yes(_))             => Cell.Introduced
          case _                              => Cell.Unused
        Declaration(d, cell, original, emitted)
      }

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
    * see, and the detail says so rather than the check guessing.
    *
    * IT IS NOW THE SUBTRACTION OF [[declarations]] and not a second derivation of the same fact: the
    * rows whose [[Cell]] does not [[Cell.keep]], carrying that cell's own sentence so the reader is
    * told WHICH of the two removable cells they are in. Written as its own filter it would be free to
    * disagree with the lane beside it — the shape the three-conjunct doc above already refuses. */
  def unneeded(decls: List[Declaration]): List[(ArtifactDep, String)] =
    decls.filterNot(_.cell.keep).map { d =>
      d.dep -> (s"${d.cell.label} — ${d.cell.advice}" +
        s"  [original: ${d.original.why}; emitted: ${d.emitted.why}]")
    }

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

  /** the [[Declared]] lane — one row per declared coordinate, whatever its cell.
    *
    * The POSITIVE, reported apart from the residue for the trivia family's reason: a run that
    * declares nothing and a run whose every declaration is answered read the same `policy` number,
    * and a coordinate a phase redirected into has no row on either usage lane at all.
    *
    * There is no `Origin` for a build coordinate and inventing one would be a fabricated fact, so the
    * `path` column carries the manifest FIELD — the thing a reader edits — and the line is 0, which
    * is `PolicyReport`'s own answer to the same question. */
  def reportDeclared(decls: List[Declaration]): List[CheckReport.Finding] =
    decls.map { d =>
      CheckReport.Finding(Declared, d.cell.label, d.dep.toString,
        balticporter.core.PolicyReport.DependencySetting, 0,
        s"original: ${d.original.why} | emitted: ${d.emitted.why} — ${d.cell.advice}")
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
