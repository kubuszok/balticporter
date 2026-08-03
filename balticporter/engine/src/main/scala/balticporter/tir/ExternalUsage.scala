package balticporter.tir

import java.nio.file.{Files, Path}

/** Every EXTERNAL member this program references — the enumeration that already ran, kept.
  *
  * ==Why it exists==
  * `PortabilityCheck.checkAll` walks `program.referenced`, resolves each symbol's `owner#name` and
  * holds every recorded usage with its `UsageKind` and its origin — and then throws all of it away
  * except the hits of its 34 rules. No artifact of what a port depends on outside itself existed
  * anywhere, so every question about the JDK surface ("what do we call that we cannot ship?", "what
  * would enabling this phase cover?") was answered by grepping emitted text, which cannot see an
  * instance call on a kept receiver at all.
  *
  * This is that enumeration lifted out of the rule filter, with ZERO new traversal:
  * `PortabilityCheck` now reads it and applies its rules to the rows, and
  * [[JdkSurfaceCheck]] reads the same rows and classifies them.
  *
  * ==Order is part of the contract==
  * [[all]] preserves `program.referenced.toList`'s order and each symbol's usage order exactly,
  * because `portability(all)`'s findings are a promoted baseline in thirteen lanes and a reordering
  * would diff as every row removed and re-added while every count stayed the same.
  *
  * ==Ownership is STRUCTURAL==
  * "External" is `!program.owns(id)` (CLAUDE.md §4.56), never a name test: a DEPENDENT port's
  * program contains its base's units, so the base's types are owned here exactly as the port's own
  * are, and neither appears in this surface.
  */
object ExternalUsage:

  /** one referenced member (or type), with everything the enumeration saw about it. */
  final case class Row(
      symbol: SymId,
      /** the symbol's own `fullName`. For an external MEMBER this is an interning key rather than a
        * meaningful name — which is why [[member]] exists — but for an external TYPE it is the
        * type's real FQN, and that is what the portability rules match on. */
      fullName: String,
      /** the DECLARING type's `fullName`, when the frontend resolved one. `None` for a type (which
        * has no declaring member) and for a member the frontend interned without an owner. */
      owner: Option[String],
      name: String,
      /** the parameter spelling, when the frontend recorded one — D1's lesson: arity is not enough
        * and a name is not an overload. `None` for a field, a type, or an unresolved external. */
      descriptor: Option[Descriptor],
      usages: List[Usage],
  ):
    /** `owner#name` — the identification an external member has, and the one a policy table is
      * written in. `None` where the owner is unresolved. */
    def member: Option[String] = owner.map(o => s"$o#$name")

    /** `owner#name(P1,P2)` — the same with the overload, for the artifact. Falls back to the bare
      * form, which is what a member with no recorded descriptor honestly is. */
    def key: Option[String] = member.map(m => m + descriptor.fold("")(d => s"(${d.render})"))

    def kinds: List[UsageKind] = usages.map(_.kind).distinct.sortBy(_.toString)
    def sites: Int             = usages.size
    def firstOrigin: Origin    = usages.headOption.map(_.site.origin).getOrElse(Origin.synthetic)

  /** EVERY referenced symbol, owned or not, in `program.referenced`'s own order.
    *
    * The unfiltered form, because that is what `PortabilityCheck` has always applied its rules to
    * and the promoted baselines are that answer. Callers wanting the surface proper use
    * [[external]]. */
  def all(program: Program): List[Row] =
    program.referenced.toList.flatMap { id =>
      program.symbolOf(id).map { sym =>
        Row(id, sym.fullName, program.symbolOf(sym.owner).map(_.fullName), sym.name, sym.descriptor,
            program.usages(id))
      }
    }

  /** …restricted to what this port DECLARES A DEPENDENCY ON: symbols the program does not own,
    * referenced from code this run actually emits.
    *
    * `isExcluded` is the filter `PortabilityCheck.inEmittedCode` carries and for the same measured
    * reason (ENGINE-LIMITS D2): a dependent's program holds its base's units, and a reference made
    * inside one of those is the BASE's dependency, reported by a repository that cannot act on it.
    * A row whose every usage is excluded disappears rather than surviving with `sites = 0`. */
  def external(program: Program, isExcluded: SymId => Boolean = _ => false): List[Row] =
    all(program)
      .filterNot(r => program.owns(r.symbol))
      .flatMap { r =>
        val kept = r.usages.filterNot(u => PortabilityCheck.owningType(program, u.enclosing).exists(isExcluded))
        Option.when(kept.nonEmpty)(r.copy(usages = kept))
      }

  /** `external-surface.tsv` — one row per referenced external member, both lanes.
    *
    * Two lanes with a `scope` column rather than two files, because the pair is the same split the
    * check family already publishes (`portability(all)` beside `portability(emitted)`) and the
    * question "did a substitution move this dependency out of the deliverable" is only answerable
    * with both in front of you.
    *
    * Written by the RUN, and therefore GATED ON THE ARTIFACT LAYER by its caller — §5.1's rule
    * without exception, and the reason this takes a directory instead of finding one. */
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
