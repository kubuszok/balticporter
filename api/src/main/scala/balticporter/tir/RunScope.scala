package balticporter.tir

/** What the RUN knows about ITSELF, and a PHASE cannot derive from the `Program` it is handed.
  *
  * ==The two questions, and why neither is answerable from the program==
  * A dependent module's `Program` CONTAINS its base (`resolutionRoots` parses the base's Java), so
  * [[Program.owned]] — which roots its climb on `program.units`, all of them — is a
  * '''program-vs-JDK''' filter and answers `true` for every base symbol. That is exactly right for
  * what it is asked there (a rename must not rewrite the JDK), and it is not the question a phase
  * has when it is about to REWRITE a declaration: *does this run emit that declaration at all?*
  * `ENGINE-LIMITS.md` D2 records five instances of the same shape on the REPORTING side and names
  * the rewriting side as the one none of them reached. [[emits]] is that predicate, and only the run
  * can compute it — it is decided from the unit's `Origin` against the run's own source and
  * resolution roots, realpathed on both sides (CLAUDE.md §5.4), and nothing in the TIR carries it.
  *
  * The second question is the same one, asked of POLICY rather than of code. After
  * `balticporter.core.SurfaceFold` merges a base's instance of a phase with a dependent's, the
  * phase that RUNS holds both modules' tables and cannot tell them apart — and "which of these keys
  * did MY manifest contribute?" is the question that decides whether a rewrite is this module's to
  * make. [[contributed]] is the fold's own answer (`SurfaceFold.ownKeys`), handed down.
  *
  * ==Why it arrives on the BINDER==
  * `PolicyBinder` is already THE object the run hands every `PolicyBound` phase before the pipeline
  * starts, once per translation, and it already carries the `Program`. A second channel would be a
  * second thing a caller must remember, and `Pipeline.runTraced`'s own scaladoc records what
  * happens then: a phase run without it takes a silently different code path.
  *
  * ==The default is the pre-existing behaviour, exactly==
  * [[RunScope.whole]] answers "this run emits everything" and "no phase's policy is scoped", which
  * is the truth for a BASE port, for a single-module port, for a spec and for `DebugEmit` — so a
  * consumer cannot take a different code path under test than it does in a port, which is how a
  * mechanism ships untested (the same argument `TrivialSurface` carries).
  */
trait RunScope:

  /** Does this run EMIT the declarations of this top-level unit? `false` for a unit the run merely
    * resolved against — a base module's. Asked with the unit symbol, which every phase can reach by
    * climbing a symbol's owner chain. */
  def emits(unit: SymId): Boolean

  /** The shared-surface SUBJECTS the folded manifest itself contributed to this phase's effective
    * policy — `balticporter.core.SurfaceFold.ownKeys`, or, for a phase this module declares that no
    * base has a counterpart for, all of its own subjects.
    *
    * `None` means "this module declares no instance of this phase", which is the no-filter answer:
    * every key the phase holds is a base's, so every rewrite it makes is one the base's own run
    * made identically and there is nothing for this module to be held to.
    */
  def contributed(phase: String): Option[Set[String]]

object RunScope:

  /** the whole program is this run's, and no phase's policy is scoped — the default everywhere. */
  val whole: RunScope = new RunScope:
    def emits(unit: SymId): Boolean                     = true
    def contributed(phase: String): Option[Set[String]] = scala.None

  /** @param emitted the top-level unit symbols this run converts.
    * @param own     phase name → the subjects THIS manifest contributed to that phase's policy. */
  def of(emitted: Set[SymId], own: Map[String, Set[String]]): RunScope = new RunScope:
    def emits(unit: SymId): Boolean                     = emitted(unit)
    def contributed(phase: String): Option[Set[String]] = own.get(phase)
