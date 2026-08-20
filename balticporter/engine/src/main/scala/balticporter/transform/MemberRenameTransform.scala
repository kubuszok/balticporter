package balticporter.transform

import balticporter.core.{MergeablePolicy, PolicyFinding, PolicyIssue, PolicyReport, PolicySource, SurfacePolicy}
import balticporter.tir.*

/** RENAME A MEMBER THE PORT NAMES, to a name the PORT CHOOSES — the manifest's way to reach
  * [[MemberRenamer]].
  *
  * ==The gap, and why it is exactly one sentence wide==
  * [[MemberRenamer]] is a complete rename machine: it expands a request through the override
  * COMPONENT, refuses whole where the component reaches a declaration this program cannot move,
  * reads EFFECTIVE names PARENTS-FIRST (CLAUDE.md §4.55), and records one `Reason.Configured`
  * decision per renamed declaration. Every one of those five features exists FOR a policy rename —
  * its own scaladoc says so — and until now no manifest could ask for one. The only policy caller
  * is [[TypeRedirectTransform]], whose new name is not chosen at all: it is DICTATED by the redirect
  * target (`Disposable#dispose` must become `close`, because that is what `java.lang.AutoCloseable`
  * calls it).
  *
  * That leaves one configuration with no spelling, and it is the ordinary one for a widget toolkit.
  * A base redirects `Disposable -> java.lang.AutoCloseable` with `dispose -> close`; a DEPENDENT
  * declares a class that implements `Disposable` and ALREADY HAS a `close()` of its own — a window's
  * close-button handler, a stream wrapper's `close`, anything. One emitted class cannot declare
  * `close()` twice, so [[MemberRenamer.OnCollision.Refuse]] refuses the component whole, which is
  * that mode's stated contract and the correct answer: the engine may not invent which of two
  * members keeps a name. `java.lang.AutoCloseable#close` is not negotiable, so the member that has
  * to move is the LIBRARY'S OWN — and that is a statement only the port can make. This is where it
  * makes it.
  *
  * ==Kind==
  * CLAUDE.md §1(b). The MECHANISM — expand the component, screen the world, screen the collisions,
  * rewrite the symbol table once — is a fact about Java and Scala and is [[MemberRenamer]]'s, not
  * this phase's. WHICH member and WHAT it is called are facts about one library. An empty map is a
  * structural no-op: [[run]] returns its input before building a graph.
  *
  * ==Configuration==
  * {{{
  * { transform = "member-rename"
  *   renames { "com.foo.VisWindow#close"      = "closeWindow"     # every overload of `close`
  *             "com.foo.Stream#close(int)"    = "closeAt" } }     # exactly one of them
  * }}}
  * The KEY is a [[MemberKey]] in the UPSTREAM namespace, like every policy key (§4.56 — the package
  * rename runs last). The VALUE is a bare member name and nothing else: this phase changes NAMES and
  * never shapes, so a value carrying a `#`, a parameter list or a dot is refused as malformed rather
  * than read as some other act.
  *
  * ==`OnCollision.Refuse`, and the reason is the OPPOSITE of `bean-properties`'==
  * [[BeanPropertyTransform]] passes `DeferToEmitter`, because a bean property's name is FORCED by
  * java's own `getX`/`setX` and the port has no freedom to spell it differently — so where the name
  * lands on a private field, moving the FIELD is the only way to deliver what the entry could not
  * have said otherwise. Here the new name is the port's free choice. A collision therefore has a
  * one-edit answer the port already holds — pick another name — and taking it silently by moving a
  * THIRD member the entry never named would be this engine doing exactly what the refusal it is
  * built on declines to do. So a collision is REFUSED and REPORTED with the colliders named, which
  * is the string an agent edits (§4.575).
  *
  * ==BASE-ANCHORED, unlike [[TypeRedirectTransform]]'s rename, and that difference is load-bearing==
  * A dependent's `Program` contains its base (`ENGINE-LIMITS.md` D2). `TypeRedirectTransform`
  * deliberately builds its graph with NO `baseUnits`, because its rename is one the BASE ITSELF
  * DECLARED and already performed in its own run: renaming the base's declaration in this run's
  * symbol table is what makes the dependent's override come out under the name the base emitted.
  * No such agreement exists here. A free-form rename of a component that reaches a base declaration
  * would emit an `override` of a member the base does not have — two ports that each compile alone
  * and cannot compile together (§1.5), and NOT visible to `SurfaceIntrusion`, whose screen reads the
  * KEY's owner and would pass a key naming the dependent's own class whose component climbs into the
  * base. So the graph is built with the units this run does not emit as `baseUnits`
  * ([[RunScope.emits]]), and such a component is refused with the base declaration named.
  * `RunScope.whole` is the answer for a base port and for every spec, so this costs a
  * single-module port nothing.
  *
  * ==Ordering, and it is DECLARATION POSITION that carries it rather than the edge==
  * BEFORE `type-redirect`, which is the worked case: a rename that FREES a name has to happen before
  * the phase whose own rename would collide with it. `package-rename` is last as always. Nothing
  * else — and the edges this phase does NOT declare are the load-bearing half.
  *
  * `Pipeline.order` is a min-heap on DECLARATION INDEX, so a `runsBefore` edge onto a phase declared
  * EARLIER than this one does not merely constrain that phase, it POSTPONES it past every
  * unconstrained phase in between — the failure `Pipeline.order`'s own scaladoc records, and this
  * phase reproduced it exactly. Declared at the END of a dependent's effective surface (which is
  * where an unmerged dependent phase lands) with a `runsBefore("java-collections->scala")` copied
  * from [[BeanPropertyTransform]], it pushed `type-redirect` past `globals->implicits`; the
  * threading analysis then read a PRE-redirect ancestry, `sge-visui`'s `context-seam` moved 42 -> 41
  * with **0 emitted bytes changed**, and the phase was SKIPPED for that measurement. So:
  *
  *   - the collections edge is not declared, because it is not needed. [[BeanPropertyTransform]]
  *     wants it so that a pair's DESCRIPTOR is matched against java's own spelling; here every key
  *     is resolved by the BINDER before the pipeline starts, and what runs is a rename of NAMES,
  *     which no retyping can disagree with;
  *   - the `type-redirect` edge IS declared, because correctness comes first — and it is made free
  *     by giving this phase a declaration position ahead of that phase. A base that redirects
  *     declares an empty instance of this phase at that position ([[MemberRenameTransform]] with no
  *     entries is a structural no-op), and a dependent's own table MERGES into it, which
  *     `SurfaceFold` places at the BASE's position: "a merge changes a table, never an ordering".
  *     That is what `MergeablePolicy` is for, and it is the only way a DEPENDENT can put a phase
  *     early in a pipeline it did not write.
  *
  * ==This phase changes emitted SIGNATURES, so it is SHARED SURFACE==
  * It implements `SurfacePolicy`, and [[MergeablePolicy]] beside it: a base and a dependent may each
  * hold renames, the tables are keyed on INDEPENDENT members, and two modules that name one member
  * with two different names is a rewrite whose outcome depends on which manifest was read.
  */
final class MemberRenameTransform(val renames: Map[String, String] = Map.empty)
    extends Phase, PolicySource, SurfacePolicy, MergeablePolicy, PolicyBound:

  def name: String = MemberRenameTransform.Name

  /** see the class note: exactly two, and the ones that are ABSENT are the measured half. */
  override def runsBefore: Set[String] = Set("type-redirect", "package-rename")

  /** the renames, sorted and rendered — two modules that agree must compare equal (§1.5). */
  def surfaceFingerprint: String =
    renames.toList.sorted.map((k, v) => s"$k=$v").mkString(",")

  /** every type this instance's policy is KEYED on — a key's owner, through the one cut
    * `MergeablePolicy.subjectOf` owns. */
  def subjects: Set[String] = renames.keySet.map(MergeablePolicy.subjectOf)

  /** THE MERGE CONTRACT (DESIGN.md §8.13). Independent member keys union; ONE member with two names
    * refuses.
    *
    * Compared by PARSED NAME and not by map key, which is [[TypeRedirectTransform.mergedWith]]'s own
    * recorded lesson: `X#close` and `X#close()` are two strings and may be one member — a bare key is
    * every overload — so a raw-key intersection merges a genuine disagreement cleanly and the drift
    * then arrives at [[MemberRenamer]] as its NON-FATAL two-claimants refusal, where the contract
    * owes a fatal `SurfaceDivergence`. [[MemberKey.mayNameSame]] is the question, and over-refusal is
    * the safe direction: a pair refused is a pair a port spells once.
    */
  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case o: MemberRenameTransform =>
      val clashes = for
        (theirs, to)   <- o.renames.toList
        (mine, to2)    <- renames.toList
        if MemberKey.mayNameSame(mine, theirs) && to != to2
      yield (mine, to2, theirs, to)
      if clashes.nonEmpty then
        Left(clashes.sorted.distinct.map { (mine, to2, theirs, to) =>
          val how =
            if mine == theirs then s""""$mine""""
            else s""""$mine" and "$theirs", which may be ONE member (a bare key is every overload)"""
          s"""both modules rename $how, to "$to2" and "$to""""
        }.mkString("; ") +
          " — two answers for one member is a rewrite whose outcome depends on which manifest was read")
      else
        Right(MergeablePolicy.Merged(
          new MemberRenameTransform(renames ++ o.renames),
          (o.renames.keySet -- renames.keySet).map(MergeablePolicy.subjectOf)))
    case other =>
      Left(s"`${other.name}` is not a `MemberRenameTransform`, so there is no table to compose")

  // ---- policy, bound before the pipeline starts ---------------------------------------------

  /** the entries that PARSED and BOUND — one row per declaration each key named. */
  private var boundRenames: List[MemberRenameTransform.Entry] = Nil
  private var records: List[PolicyBinder.Record]              = Nil

  /** malformed keys and values — findings about the KEY, so a module that did not declare it is not
    * told about it. */
  private var ownFindings: List[PolicyFinding] = Nil

  /** what the RUN knows about itself: which units it emits. Read at [[run]] to base-anchor the
    * graph, and defaulted to [[RunScope.whole]] for a phase constructed in a spec. */
  private var scope: RunScope = RunScope.whole

  def bindPolicy(binder: PolicyBinder): Unit =
    scope = binder.run
    val bad = collection.mutable.ListBuffer.empty[PolicyFinding]
    boundRenames = renames.toList.sortBy(_._1).flatMap { (key, newName) =>
      def refuse(what: String): Nil.type =
        bad += PolicyFinding(name, MemberRenameTransform.Setting, key, PolicyIssue.Malformed, what)
        Nil
      MemberKey.parse(key) match
        case Left(m)                                       => refuse(m.what)
        case Right(_) if newName.isEmpty                   => refuse("the new name is empty, which names nothing")
        case Right(_) if !MemberRenameTransform.isPlain(newName) =>
          refuse(s"`$newName` is not a bare member name — this phase changes NAMES and never " +
            "shapes, so a value carrying a `#`, a parameter list, a `.` or whitespace names an act " +
            "it does not perform (an arity change is `bean-properties`; a re-point is `type-redirect`)")
        case Right(mk) =>
          // `flatMap(_.sym)` and not `map`: a hit with no symbol is a member the port DROPPED, which
          // the binder counts as having FIRED and which there is nothing left to rename. Empty hits
          // are therefore a no-op rather than a finding — `TypeRedirectTransform`'s own reading.
          val hits = binder.bindMembers(name, MemberRenameTransform.Setting, mk.render)
            .toOption.getOrElse(Nil).flatMap(_.sym)
          List(MemberRenameTransform.Entry(mk.render, newName, hits))
    }
    ownFindings = bad.toList
    records = binder.recordsFor(name)

  /** refusals THIS RUN made — reset at the head of every run, because a phase instance is reused
    * across two translations (`Determinism.Full`) and the first run's refusals are not the
    * second's. */
  private var runFindings: List[PolicyFinding] = Nil

  /** Declared keys that named nothing, malformed entries, and every refusal this run made.
    *
    * The refusals are `About.ThisRun` and the rest are `About.TheKey`, which is the split
    * `PolicyFinding.About` exists for: a key inherited from a base is correct there and is not a
    * string this module can edit, while a component this run could not move is a fact only this
    * module's program has. Sending the second reader to a manifest they do not own is the §4.45
    * failure the classification prevents.
    */
  def policyReport: PolicyReport =
    PolicyReport.fromBindings(records) ++ PolicyReport(ownFindings ++ runFindings)

  // ---- the rename ----------------------------------------------------------------------------

  override def run(program: Program): Program =
    runFindings = Nil
    val live = boundRenames.filter(_.hits.nonEmpty)
    if live.isEmpty then program
    else
      // the units this run does NOT emit — see the class note. `RunScope.whole` yields the empty
      // set, which is exactly the single-module and spec answer.
      val baseUnits = program.units.map(_.symbol).filterNot(scope.emits).toSet
      val graph     = OverrideGraph.build(program, baseUnits = baseUnits)
      val requests  = live.flatMap(e => e.hits.map(h =>
        MemberRenamer.Request(h, e.newName, Reason.Configured(name, e.key), e.key, e.key)))
      val (out, refusals) = MemberRenamer.rename(
        program, graph, requests, MemberRenamer.OnCollision.Refuse, decisions)
      refusals.map(_.request.key).distinct.foreach { k =>
        val why = refusals.find(_.request.key == k).map(_.why).getOrElse("refused")
        runFindings :+= PolicyFinding(name, MemberRenameTransform.Setting, k, PolicyIssue.Unverifiable,
          s"$why — so this rename did NOT happen and the member keeps its upstream name",
          PolicyFinding.About.ThisRun)
      }
      out

object MemberRenameTransform:

  val Name: String = "member-rename"

  /** the knob, spelled the way a manifest holds it — one string, so a finding and the config
    * front door cannot drift. */
  val Setting: String = "MemberRenameTransform(renames)"

  /** one entry that parsed and bound: the key's canonical [[MemberKey.render]], the new name, and
    * every declaration the key named. */
  private[transform] final case class Entry(key: String, newName: String, hits: List[SymId])

  /** is this value a bare member name? Deliberately a WHITELIST of what a Scala member name may be
    * here rather than a blacklist of the separators that would make it something else: a blacklist
    * is a list somebody has to keep complete, and the failure mode is a value that reaches the
    * symbol table and emits text nobody can parse. */
  private[transform] def isPlain(v: String): Boolean =
    v.nonEmpty && !v.head.isDigit && v.forall(c => c.isLetterOrDigit || c == '_' || c == '$')
