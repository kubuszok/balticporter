package balticporter.tir

import java.nio.file.{Files, Path}

/** Every external member this program references, with usages.
  *
  * Shared by `PortabilityCheck` (rule matching) and [[JdkSurfaceCheck]] (classification).
  * [[all]] preserves `program.referenced` order (promoted baselines depend on it).
  * "External" is `!program.owns(id)` (structural, not by name). // CLAUDE.md §4.56 */
object ExternalUsage:

  /** One referenced member or type, with its usages. */
  final case class Row(
      symbol: SymId,
      /** The symbol's `fullName` (interning key for members; real FQN for types). */
      fullName: String,
      /** Declaring type's `fullName`, or `None` for a type or unresolved member. */
      owner: Option[String],
      name: String,
      /** Parameter descriptor, or `None` for a field, type, or unresolved external. */
      descriptor: Option[Descriptor],
      usages: List[Usage],
  ):
    /** `owner#name`, or `None` where the owner is unresolved. */
    def member: Option[String] = owner.map(o => s"$o#$name")

    /** `owner#name(P1,P2)` with descriptor, falling back to bare `owner#name`. */
    def key: Option[String] = member.map(m => m + descriptor.fold("")(d => s"(${d.render})"))

    def kinds: List[UsageKind] = usages.map(_.kind).distinct.sortBy(_.toString)
    def sites: Int             = usages.size
    def firstOrigin: Origin    = usages.headOption.map(_.site.origin).getOrElse(Origin.synthetic)

  /** Every referenced symbol (owned or not), in `program.referenced` order.
    * Callers wanting only externals use [[external]]. */
  def all(program: Program): List[Row] =
    program.referenced.toList.flatMap { id =>
      program.symbolOf(id).map { sym =>
        Row(id, sym.fullName, program.symbolOf(sym.owner).map(_.fullName), sym.name, sym.descriptor,
            program.usages(id))
      }
    }

  /** External symbols only, filtered by `isExcluded` (D2 ownership filter). */
  def external(program: Program, isExcluded: SymId => Boolean = _ => false): List[Row] =
    all(program)
      .filterNot(r => program.owns(r.symbol))
      .flatMap { r =>
        val kept = r.usages.filterNot(u => PortabilityCheck.owningType(program, u.enclosing).exists(isExcluded))
        Option.when(kept.nonEmpty)(r.copy(usages = kept))
      }

  /** Write `external-surface.tsv` with both `emitted` and `all` lanes.
    * Gated on the artifact layer by the caller. */
  def write(dir: Path, allRows: List[Row], emitted: List[Row], relativise: String => String): Path =
    val p = dir.resolve("external-surface.tsv")
    Files.createDirectories(dir)
    val emittedKeys = emitted.map(_.symbol).toSet
    val header = "#scope\tmember\tkinds\tsites\tfile\tline\n"
    def rows(scope: String, rs: List[Row]) =
      rs.sortBy(r => (r.key.getOrElse(r.fullName), r.symbol.raw)).map { r =>
        val o = r.firstOrigin
        s"$scope\t${r.key.getOrElse(r.fullName)}\t${r.kinds.mkString("|")}\t${r.sites}\t" +
          s"${relativise(o.javaPath)}\t${o.line}\n"
      }
    Files.writeString(p, header + (rows("emitted", emitted) ++ rows("all", allRows.filterNot(r => emittedKeys(r.symbol)))).mkString)
    p
