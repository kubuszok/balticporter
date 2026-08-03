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

  /** every base's member rows, grouped by the member's `owner#name` — the OVERLOAD SET.
    *
    * A member row's `emitted` column is the source map's key, which carries the parameter spelling
    * for an executable (`…#copyNodes(Array<Node>)`) and nothing for a field (`…#file`). A `SymId`
    * carries neither: `Symbol.fullName` is `owner#name` and the descriptor is a separate field
    * (`Symbol.descriptor`), deliberately never folded into the name. So a lookup by `fullName` alone
    * found every FIELD row and no METHOD row at all — which is why [[memberShape]] answered
    * `Unknown` for exactly the questions `ENGINE-LIMITS.md` D5 needs it for, silently, from the day
    * it was written.
    *
    * Grouped rather than re-spelt. Rebuilding the emitter's parameter spelling here would be a
    * SECOND derivation of the key, free to drift from `TirEmitter.memberKey` — the failure this
    * whole view exists to stop. The overload set is what the name honestly identifies, and
    * [[memberShape]] says so when its members disagree.
    *
    * A `Dropped` row is EXCLUDED, and the reason is the question this map answers: "what shape did
    * you EMIT this member in". A member the base did not emit has no shape, so a row for one is not
    * a quieter answer, it is a different fact — and read into the overload set it makes the set
    * disagree with itself and turns a `Published` answer into `Unknown` for every sibling overload.
    * The `emitted.nonEmpty` filter used to do this by accident, because every `Dropped` row a policy
    * drop produced had an empty `emitted` column; an engine REFUSAL keeps that column, since the
    * owning type is emitted and only the member is gone. So the predicate now says what it means. */
  private lazy val memberRows: Map[String, List[(String, PortMap.Entry)]] =
    bases.flatMap { (mod, m) =>
      m.members
        .filter(e => e.emitted.nonEmpty && e.disposition != PortMap.Disposition.Dropped)
        .map(e => bareName(e.emitted) -> (mod, e))
    }.groupMap(_._1)(_._2)

  /** `owner#name` from a source-map member key — the parameter spelling cut off at its `(`, which is
    * a separator the grammar guarantees is not in an owner or a member name (§4.56). */
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

  /** Is this symbol an EXECUTABLE? The published key carries a parameter spelling for one and not
    * for a field, and `owner#name` names BOTH when java put a field and a method there — which §4.55
    * exists precisely because java allows.
    *
    * Read from the DEFINITION and not from `Symbol.descriptor`: `descriptor` is also `None` for an
    * external member the frontend could not resolve, so a `None` there conflates "a field" with "we
    * do not know", and the two want opposite answers below. */
  private def isExecutable(s: SymId): Boolean =
    program.definitionOf(s) match
      case Some(_: Tree.DefDef) => true
      case Some(_)              => false
      case scala.None           => program.symbolOf(s).exists(_.descriptor.isDefined)

  /** …and the row is chosen by WHAT THE SYMBOL IS, then by agreement across overloads.
    *
    * A FIELD's key is exactly `owner#name` — no parentheses, which a nilary method's key
    * (`owner#name()`) still has — so a field is an EXACT lookup and can never be confused with the
    * method beside it. An EXECUTABLE has no parameter spelling on this side (`Symbol.descriptor` is
    * a symbol PROPERTY and deliberately not part of the name, §8.1), so what it can honestly ask
    * about is the OVERLOAD SET; where those agree that is the answer for each of them, and where
    * they differ the answer is `Unknown` rather than one of them picked.
    *
    * Splitting the two is not a refinement. Read as one set, `FileHandle#file` — a field §4.55
    * renamed to `file$field` beside the method `file()` that forced the rename — is two rows that
    * DISAGREE by construction, so every renamed field in every base answered `Unknown`: **272 of
    * them on one dependent**, each one a false report about a row that was sitting right there. */
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

  // A value ONE RUN owns, exactly like the decision log and the source map (§5.1): two runs in one
  // JVM sharing a gap list would attribute one run's refusals to the other.
  private val recorded = collection.mutable.ListBuffer.empty[Surface.Gap]
  def gaps: List[Surface.Gap]     = recorded.toList.distinct
  def gap(g: Surface.Gap): Unit   = recorded += g

