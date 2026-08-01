package balticporter.core

import balticporter.tir.{Program, Surface, SymId, Tree}

/** [[Surface]] over THIS run's own units plus the contracts its bases published — the engine's one
  * implementation of `DESIGN.md` §8.3's view.
  *
  * ==The one structural climb==
  * Six independent copies of a fuel-bounded owner climb answered "mine or my base's?" before this,
  * all on the reporting side, none on the rewriting side, with different failure directions. This is
  * the climb, and the direction is specified: '''exhausting the fuel counts as NOT owned''', so the
  * run asks the contract and gets an honest [[Surface.Answer.Unknown]] rather than deciding on a
  * guess.
  *
  * Note this is a different question from `Program.owned`, which roots on `program.units` — ALL of
  * them, a dependent's base included — and is therefore a program-vs-JDK filter. Both are right for
  * what they are asked; only one of them can say "the base emitted this and I did not".
  *
  * ==The lookup is EMITTED name to EMITTED name==
  * A base symbol reaches this point AFTER the package rename, so its `fullName` is the name the base
  * emitted — and the map's `emitted` column is the same namespace. Nothing here translates a
  * namespace, which is the point: §4.56's rule is that an artifact joining POLICY to OBSERVED code
  * carries both names, and `PortMap.of` is where that join is made. This side only ever compares
  * observed to observed.
  *
  * The two namespaces agreeing is not an assumption either: a dependent inherits the base's rename
  * map by value (§1.5) and `ManifestAgreement` reports any divergence, so a base type whose emitted
  * name this run computes differently is already a finding before it gets here.
  */
final class PublishedSurface(
    program: Program,
    val ownedUnits: List[Tree.ClassDef],
    /** each base's published contract, module name → map. Empty is the ordinary single-module case
      * and makes every non-owned question `Unknown`, which is correct and, for a run with no base,
      * never consumed. */
    bases: List[(String, PortMap.Map0)] = Nil,
) extends Surface:

  private val roots: Set[SymId] = ownedUnits.map(_.symbol).toSet

  private lazy val ownedSyms: Set[SymId] =
    program.symbols.all.iterator.map(_.id).filter(rooted(_, 64)).toSet

  private def rooted(s: SymId, fuel: Int): Boolean =
    s != SymId.None && fuel > 0 &&
      (roots(s) || program.symbolOf(s).exists(sym => rooted(sym.owner, fuel - 1)))

  def owns(s: SymId): Boolean = ownedSyms(s)

  /** every base's type rows, by EMITTED name. First base wins on a collision, which cannot happen
    * for a well-formed chain — two bases emitting one FQN is a diagnosis `ManifestAgreement` makes
    * about the manifests, not something to resolve silently here. */
  private lazy val typeRows: Map[String, (String, PortMap.Entry)] =
    bases.flatMap { (mod, m) =>
      m.types.filter(_.emitted.nonEmpty).map(e => e.emitted -> (mod, e))
    }.reverse.toMap

  private lazy val memberRows: Map[String, (String, PortMap.Entry)] =
    bases.flatMap { (mod, m) =>
      m.members.filter(_.emitted.nonEmpty).map(e => e.emitted -> (mod, e))
    }.reverse.toMap

  def typeShape(s: SymId): Surface.Answer[Surface.TypeShape] =
    if owns(s) then Surface.Answer.Own
    else
      val fqn = program.symbolOf(s).map(_.fullName).getOrElse("")
      typeRows.get(fqn) match
        case Some((mod, e)) =>
          e.typeShape match
            case Some(sh) => Surface.Answer.Published(sh, mod)
            // A row with no payload is a map published by an older engine. Named as exactly that,
            // per question, rather than refused wholesale: "your base is one engine version behind
            // and here are the questions I cannot ask it" is actionable, and `Stale` is not.
            case scala.None =>
              Surface.Answer.Unknown(
                s"$mod publishes a row for $fqn with no contract payload — its map was published by " +
                  s"an engine before port-map schema ${PortMap.Schema}. Re-run that port with this engine",
                Some(mod))
        case scala.None =>
          Surface.Answer.Unknown(
            if bases.isEmpty then s"this run declares no base port, so nothing publishes a contract for $fqn"
            else s"no declared base publishes a contract row for $fqn " +
              s"(searched ${bases.map(_._1).sorted.mkString(", ")})",
            bases.map(_._1).headOption.filter(_ => bases.sizeIs == 1))

  def memberShape(s: SymId): Surface.Answer[Surface.MemberShape] =
    if owns(s) then Surface.Answer.Own
    else
      val fqn = program.symbolOf(s).map(_.fullName).getOrElse("")
      memberRows.get(fqn) match
        case Some((mod, e)) => Surface.Answer.Published(e.memberShape, mod)
        case scala.None =>
          Surface.Answer.Unknown(s"no declared base publishes a contract row for the member $fqn",
                                 bases.map(_._1).headOption.filter(_ => bases.sizeIs == 1))

  // A value ONE RUN owns, exactly like the decision log and the source map (§5.1): two runs in one
  // JVM sharing a gap list would attribute one run's refusals to the other.
  private val recorded = collection.mutable.ListBuffer.empty[Surface.Gap]
  def gaps: List[Surface.Gap]     = recorded.toList.distinct
  def gap(g: Surface.Gap): Unit   = recorded += g

