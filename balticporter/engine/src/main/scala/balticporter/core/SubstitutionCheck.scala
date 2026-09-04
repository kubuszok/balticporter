package balticporter.core

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Post-emission check over `outDir` that [[Substitutions]] were carried out.
  *
  *   - [[emittedDroppedTypes]] (CHECK 1, run BEFORE injection) -- engine emitted a dropped type.
  *   - [[dangling]] (CHECK 2, run AFTER injection) -- dropped, unreplaced, still referenced.
  *
  * A dropped type with no replacement AND no remaining references is the success case. */
object SubstitutionCheck:

  enum Kind:
    /** CHECK 1 — the emitter wrote a file for a type the manifest dropped. */
    case Emitted
    /** CHECK 2 — dropped, unreplaced, still referenced. */
    case Dangling

  /** @param references how many emitted files still name the FQN (0 for [[Kind.Emitted]]). */
  final case class Finding(kind: Kind, fqn: String, references: Int):
    /** Render with the CLAUDE.md §1 classification at the end. */
    def render: String = kind match
      case Kind.Emitted =>
        s"$fqn is declared dropped but the engine EMITTED it" +
          "  [§1(a) engine: the emission skip did not fire — the mechanical translation is about " +
          "to shadow or collide with the replacement]"
      case Kind.Dangling =>
        s"$fqn is dropped, has no replacement, and is still referenced by $references file(s)" +
          "  [§1(b)/(c) per-library: supply an `inject` replacement at this FQN, or plug in a rule " +
          "that rewrites its uses away; the engine needs no change]"

  /** CHECK 1 -- dropped types the engine nevertheless wrote a file for. Run BEFORE injection. */
  def emittedDroppedTypes(outDir: Path, subs: Substitutions): List[Finding] =
    subs.dropTypes.toList.sorted
      .filter(fqn => Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")))
      .map(Finding(Kind.Emitted, _, 0))

  /** CHECK 2 -- dropped, unreplaced, still referenced. Run AFTER injection over the final tree. */
  def dangling(outDir: Path, subs: Substitutions): List[Finding] =
    if subs.dropTypes.isEmpty then Nil
    else
      val sources = scalaSources(outDir).map(p => withoutPorterNotes(Files.readString(p)))
      subs.dropTypes.toList.sorted.flatMap { fqn =>
        if Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")) then None // replaced
        else
          val refs = sources.count(_.contains(fqn))
          if refs == 0 then None else Some(Finding(Kind.Dangling, fqn, refs)) // rewritten away vs. dangling
      }

  /** Strip porter notes and recovery markers before checking references to a dropped type.
    * Only engine-written text is removed; upstream Javadoc mentioning the type still counts. */
  def withoutPorterNotes(text: String): String = balticporter.tir.TriviaMark.stripAll(text)

  /** Every `.scala` file under `dir`. Delegates to [[Substitutions.scalaSources]]. */
  def scalaSources(dir: Path): List[Path] = Substitutions.scalaSources(dir)
