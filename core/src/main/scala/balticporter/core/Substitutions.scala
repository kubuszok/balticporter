package balticporter.core

import java.nio.file.Path

/** Principled, typed directives for replacing constructs the port must NOT
  * translate mechanically with ready-made Scala. A project declares one; the
  * engine applies it at the symbol layer (drops) and the migration injects the
  * provided sources verbatim. This is how a port opts a type or method OUT of
  * mechanical translation and supplies a hand-written or library-backed Scala
  * equivalent in its place — e.g. libGDX's `SharedLibraryLoader`, dropped
  * upstream in favour of a dedicated native-extraction library.
  *
  * The three seams:
  *   - [[dropTypes]]  — a whole type is not translated (excluded from the port
  *                      set); a Scala definition at the same FQCN is expected
  *                      from [[inject]] (or a dependency).
  *   - [[dropMethods]] — a single method is not translated (its `DefDef` is
  *                      dropped from the owning type), leaving the rest of the
  *                      type mechanically ported.
  *   - [[inject]]     — roots of ready-made Scala copied verbatim into the
  *                      emitted sources. Survives re-emit (the migration wipes
  *                      and regenerates its output each run), which is why an
  *                      injected replacement must be declared here rather than
  *                      hand-dropped into the output tree.
  *
  * Keys are fully-qualified: type FQCNs for [[dropTypes]], `owner#method` for
  * [[dropMethods]] (matching the member-key convention used across the engine).
  */
final case class Substitutions(
    dropTypes: Set[String] = Set.empty,
    dropMethods: Set[String] = Set.empty,
    inject: List[Path] = Nil,
):
  def dropsType(fqcn: String): Boolean = dropTypes.contains(fqcn)
  def dropsMethod(ownerFqcn: String, method: String): Boolean =
    dropMethods.contains(s"$ownerFqcn#$method")

object Substitutions:
  val none: Substitutions = Substitutions()
