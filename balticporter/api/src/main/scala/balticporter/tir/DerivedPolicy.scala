package balticporter.tir

/** Spelling policy READ OFF A REFERENCE PORT's tree rather than hand-listed: which primitive slots
  * are an opaque type, which members are nullable, which nullary accessors are parenless. A value
  * (§1b) a phase consumes only when its own `derive` switch is on; empty is the no-op. Rows are
  * keyed by the UPSTREAM symbol `fullName` and carry the reference's own spelling for the report
  * (`derived-policy.tsv`). `PROGRESS.md` §13.31 step 1. */
final case class DerivedPolicy(rows: List[DerivedPolicy.Row],
                               /** upstream key -> the symbol it named when the rows were RESOLVED, before any
                                 * phase ran: a phase that renames the member (bean properties) cannot lose the
                                 * row, since a `SymId` survives every rename. Empty until [[resolved]]. */
                               ids: Map[String, SymId] = Map.empty):
  import DerivedPolicy.*

  def isEmpty: Boolean = rows.isEmpty

  /** the rows bound to the program's symbols by every key a symbol answers to ([[DerivedPolicy.keysOf]]). */
  def resolved(program: Program): DerivedPolicy =
    val wanted = rows.map(_.upstream).toSet
    val found = program.symbols.all.iterator.flatMap(s => keysOf(program, s).filter(wanted).map(_ -> s.id)).toMap
    copy(ids = found)
  /** the symbols the rows of a family name, where resolved. */
  private def idsOf(rs: Iterator[Row]): Set[SymId] = rs.flatMap(r => ids.get(r.upstream)).toSet
  def opaqueSeedIds(targetFqn: String): Set[SymId] =
    idsOf(rows.iterator.filter(r => r.family == Family.OpaqueSlot && r.target == targetFqn))
  def nullableIds: Set[SymId]  = idsOf(rows.iterator.filter(_.family == Family.NullableMember))
  def parenlessIds: Set[SymId] = idsOf(rows.iterator.filter(_.family == Family.Parenless))
  def keepNameIds: Set[SymId]  = idsOf(rows.iterator.filter(_.family == Family.KeepName))

  /** upstream symbol fullNames (parameters `C#m#p`, methods and fields `C#m`) the reference spells
    * at the opaque type `targetFqn` (an `OpaqueSpec.Target.Existing` FQN, or a Mint's `fqn`). */
  def opaqueSeeds(targetFqn: String): Set[String] =
    rows.iterator.filter(r => r.family == Family.OpaqueSlot && r.target == targetFqn).map(_.upstream).toSet

  /** upstream member fullNames the reference returns wrapped in its null model. */
  def nullableMembers: Set[String] =
    rows.iterator.filter(_.family == Family.NullableMember).map(_.upstream).toSet

  /** upstream nullary methods the reference declares WITHOUT `()`. */
  def parenless: Set[String] =
    rows.iterator.filter(_.family == Family.Parenless).map(_.upstream).toSet

  /** content digest: the fingerprint segment a deriving phase contributes — a reference edit that
    * changes a derived row changes the emitted surface, so it must move the port map. */
  lazy val digest: String =
    TirPrinter.sha256(rows.sortBy(r => (r.family.toString, r.upstream, r.target)).map(_.tsv).mkString("\n")).take(12)

object DerivedPolicy:
  val empty: DerivedPolicy = DerivedPolicy(Nil)

  enum Family:
    case OpaqueSlot, NullableMember, Parenless,
      /** a java accessor the reference keeps under its OWN name (`setInputProcessor` beside
        * `inputProcessor`): the bean step leaves the pair alone */
      KeepName

  /** @param upstream the java symbol's `fullName` @param reference the hand port's spelling at
    * that slot (`Seconds`, `Nullable[Texture]`, `def x: T`) @param target the opaque target FQN
    * for `OpaqueSlot`, empty otherwise. */
  final case class Row(family: Family, upstream: String, reference: String, target: String = ""):
    def tsv: String = s"$family\t$upstream\t$reference\t$target"

  val Header = "#family\tupstream\treference\ttarget"

  /** every key a symbol answers to: its bare `fullName`, and — for a method with a descriptor, or a
    * parameter of one — the descriptor-qualified spelling (`C#m(long)`, `C#m(long)#p`), which is
    * how a row tells two OVERLOADS apart (`Attributes#get(long)` is nullable, `get(DynamicArray,long)`
    * is not). A phase matches a policy key against this set, never against `fullName` alone. */
  def keysOf(program: Program, s: Symbol): Set[String] =
    def qualified(m: Symbol): Option[String] = m.descriptor.map(d => m.fullName + "(" + d.render + ")")
    val own = qualified(s).toSet
    val asParam =
      if s.flags.isParam then program.symbolOf(s.owner).flatMap(qualified).map(_ + "#" + s.name).toSet
      else Set.empty
    Set(s.fullName) ++ own ++ asParam

  /** the rows a BASE published (`derived-policy.tsv` in its report): a dependent reads its base's
    * derived spellings as facts, never re-derives them (§1.5). Absent file = empty. */
  def read(path: java.nio.file.Path): DerivedPolicy =
    if !java.nio.file.Files.isRegularFile(path) then empty
    else
      val rows = scala.io.Source.fromFile(path.toFile).getLines().filterNot(l => l.startsWith("#") || l.isBlank).toList.flatMap { l =>
        l.split('\t') match
          case Array(f, up, ref, tgt) => Family.values.find(_.toString == f).map(Row(_, up, ref, tgt))
          case Array(f, up, ref)      => Family.values.find(_.toString == f).map(Row(_, up, ref, ""))
          case _                      => scala.None
      }
      DerivedPolicy(rows)
