package balticporter.core

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Did the [[Substitutions]] manifest actually happen, in the FILES that were written?
  *
  * The other three checks (`OmissionCheck`, `PortabilityCheck`, `RewriteTrace`) read the TIR. This
  * one cannot: a substitution is only half a TIR fact. The engine drops the declaration from the
  * model, but what stands at that FQN afterwards is a FILE — injected Scala copied verbatim, or
  * nothing at all — and whether the port is coherent is a question about the output tree. So this
  * check runs over `outDir` after emission, and it is the reason it lives here rather than in
  * `balticporter.tir`.
  *
  * Two independent failures, each caught by its own half:
  *
  *   - [[emittedDroppedTypes]] (CHECK 1) — the engine emitted a type the manifest said to drop.
  *     That is an ENGINE fault: the emission skip did not fire, and the injected replacement (if
  *     any) is about to be overwritten by, or to collide with, the mechanical translation. Must be
  *     read BEFORE injection, or an injected replacement is indistinguishable from a leak.
  *   - [[dangling]] (CHECK 2) — a dropped type has no replacement at its FQN AND the final code
  *     still names it. The substitution was declared and not carried out; shipping is a compile
  *     error at best and a dependency on a type this port claims not to have at worst.
  *
  * Note what CHECK 2 does NOT flag: a dropped type with no replacement and no remaining references
  * is the SUCCESS case — every use was rewritten away by a phase. That distinction is the whole
  * value of the check, and it is why "references" is counted rather than merely tested.
  *
  * This was inline in one porting program for its whole life, which is exactly why a second porting
  * program in the same repository never ran it (CLAUDE.md §4.45 — the consumer is an agent
  * elsewhere, and a check it has to copy is a check it will not have). Lifting it changes no
  * behaviour: the predicates, the ordering and the counts are the originals.
  */
object SubstitutionCheck:

  enum Kind:
    /** CHECK 1 — the emitter wrote a file for a type the manifest dropped. */
    case Emitted
    /** CHECK 2 — dropped, unreplaced, still referenced. */
    case Dangling

  /** @param references how many emitted files still name the FQN (0 for [[Kind.Emitted]]). */
  final case class Finding(kind: Kind, fqn: String, references: Int):
    /** Ends in the CLAUDE.md §1 classification, because that is what the reader has to act on and
      * the two kinds are NOT the same kind of fault (§4.45). */
    def render: String = kind match
      case Kind.Emitted =>
        s"$fqn is declared dropped but the engine EMITTED it" +
          "  [§1(a) engine: the emission skip did not fire — the mechanical translation is about " +
          "to shadow or collide with the replacement]"
      case Kind.Dangling =>
        s"$fqn is dropped, has no replacement, and is still referenced by $references file(s)" +
          "  [§1(b)/(c) per-library: supply an `inject` replacement at this FQN, or plug in a rule " +
          "that rewrites its uses away; the engine needs no change]"

  /** CHECK 1 — dropped types the engine nevertheless wrote a file for.
    *
    * Run this BEFORE the injection copy, so a file at that path can only have come from the
    * emitter. Sorted, so a report is stable run to run. */
  def emittedDroppedTypes(outDir: Path, subs: Substitutions): List[Finding] =
    subs.dropTypes.toList.sorted
      .filter(fqn => Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")))
      .map(Finding(Kind.Emitted, _, 0))

  /** CHECK 2 — dropped types with neither a replacement nor a rewrite.
    *
    * Run this AFTER injection, over the FINAL tree: a file at the FQN's path now means the
    * substitution supplied a replacement, and its absence means every use had to have been
    * rewritten away. Textual, deliberately: injected sources never pass through the TIR, so there
    * is no symbol to ask. */
  def dangling(outDir: Path, subs: Substitutions): List[Finding] =
    if subs.dropTypes.isEmpty then Nil
    else
      val sources = scalaSources(outDir).map(Files.readString)
      subs.dropTypes.toList.sorted.flatMap { fqn =>
        if Files.exists(outDir.resolve(fqn.replace('.', '/') + ".scala")) then None // replaced
        else
          val refs = sources.count(_.contains(fqn))
          if refs == 0 then None else Some(Finding(Kind.Dangling, fqn, refs)) // rewritten away vs. dangling
      }

  /** every `.scala` file under `dir`, or nothing when the directory does not exist. */
  def scalaSources(dir: Path): List[Path] =
    if !Files.exists(dir) then Nil
    else Files.walk(dir).iterator().asScala.filter(_.toString.endsWith(".scala")).toList.sorted
