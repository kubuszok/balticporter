package balticporter.core

import balticporter.tir.{Program, Surface, SymId, Tree}

/** [[Surface]] over this run's own units plus the contracts its bases published.
  * Ownership is decided by a fuel-bounded climb rooted on `ownedUnits`; exhausting fuel
  * counts as NOT owned, yielding [[Surface.Answer.Unknown]]. Lookups use EMITTED names
  * on both sides. // DESIGN.md §8.3 */
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

  /** Every base's type rows, by EMITTED name. First base wins on collision. */
  private lazy val typeRows: Map[String, (String, PortMap.Entry)] =
    bases.flatMap { (mod, m) =>
      m.types.filter(_.emitted.nonEmpty).map(e => e.emitted -> (mod, e))
    }.reverse.toMap

  /** Every base's member rows, grouped by `owner#name` (the overload set).
    * Keyed on bare name because `Symbol.fullName` carries no parameter spelling.
    * `Dropped` rows are excluded: they have no emitted shape. */
  private lazy val memberRows: Map[String, List[(String, PortMap.Entry)]] =
    bases.flatMap { (mod, m) =>
      m.members
        .filter(e => e.emitted.nonEmpty && e.disposition != PortMap.Disposition.Dropped)
        .map(e => bareName(e.emitted) -> (mod, e))
    }.groupMap(_._1)(_._2)

  /** `owner#name` from a source-map member key, cutting off at `(`. */
  private def bareName(key: String): String =
    val i = key.indexOf('(')
    if i < 0 then key else key.substring(0, i)

  def typeShape(s: SymId): Surface.Answer[Surface.TypeShape] =
    if owns(s) then Surface.Answer.Own
    else
      val fqn = program.symbolOf(s).map(_.fullName).getOrElse("")
      typeRows.get(fqn) match
        case Some((mod, e)) =>
          e.typeShape match
            case Some(sh) => Surface.Answer.Published(sh, mod)
            // Row with no payload: map published by an older engine.
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

  /** Is this symbol an executable? Read from the definition, not `Symbol.descriptor`
    * (which is also `None` for unresolved externals). */
  private def isExecutable(s: SymId): Boolean =
    program.definitionOf(s) match
      case Some(_: Tree.DefDef) => true
      case Some(_)              => false
      case scala.None           => program.symbolOf(s).exists(_.descriptor.isDefined)

  /** Look up a member's shape by choosing rows matching the symbol's kind (field vs executable),
    * then requiring agreement across overloads. `Unknown` when overloads disagree. */
  def memberShape(s: SymId): Surface.Answer[Surface.MemberShape] =
    if owns(s) then Surface.Answer.Own
    else
      val fqn  = program.symbolOf(s).map(_.fullName).getOrElse("")
      val rows = memberRows.getOrElse(fqn, Nil)
      val mine = if isExecutable(s) then rows.filter((_, e) => e.emitted != fqn)
                 else rows.filter((_, e) => e.emitted == fqn)
      mine match
        case (mod, e) :: rest =>
          val shapes = (e :: rest.map(_._2)).map(_.memberShape).distinct
          if shapes.sizeIs == 1 then Surface.Answer.Published(shapes.head, mod)
          else
            Surface.Answer.Unknown(
              s"$mod publishes ${rest.size + 1} overloads of $fqn and they do not agree " +
                s"(${shapes.map(Surface.render).mkString("; ")}) — a symbol carries no parameter " +
                "spelling here, so the name cannot be resolved to one of them",
              Some(mod))
        case Nil =>
          Surface.Answer.Unknown(s"no declared base publishes a contract row for the member $fqn",
                                 bases.map(_._1).headOption.filter(_ => bases.sizeIs == 1))

  // Per-run mutable state, like the decision log and source map.
  private val recorded = collection.mutable.ListBuffer.empty[Surface.Gap]
  def gaps: List[Surface.Gap]     = recorded.toList.distinct
  def gap(g: Surface.Gap): Unit   = recorded += g

