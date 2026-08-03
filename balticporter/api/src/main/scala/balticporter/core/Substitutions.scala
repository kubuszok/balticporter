package balticporter.core

import balticporter.tir.{SymTag, Symbol}

import java.nio.file.{Files, Path}

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
  *
  * A key that matches NOTHING is a silent no-op — the whole point of [[policyReport]]; see
  * [[PolicyReport]] for why that is the same defect class as a silent omission, and [[matched]]
  * for what recording it costs.
  */
final case class Substitutions(
    dropTypes: Set[String] = Set.empty,
    dropMethods: Set[String] = Set.empty,
    inject: List[Path] = Nil,
):

  /** does policy drop this TYPE? PURE — see [[dropsMethod]]. */
  def dropsType(fqcn: String): Boolean = dropTypes.contains(fqcn)

  /** `owner#name` drops EVERY overload of that name; `owner#name(P1,P2)` — the erased parameter
    * type SIMPLE names — drops exactly one. Overload precision is what makes constructors
    * droppable at all: a type's constructors all share the one name `<init>`, so the bare key
    * could only ever mean "drop them all".
    *
    * '''PURE, and it did not use to be.''' This value carried a mutable tally of the keys it had
    * been observed to satisfy, whose own scaladoc apologised for being a mutable field on a `case
    * class` that `copy()` silently empties, and for reporting "did this key EVER fire" when the
    * question asked was "did it fire for THIS translation". Which keys fired is now a question
    * `PolicyBinder` answers from the program and the frontend's `MemberIndex`, where a DROPPED
    * member still exists — so the answer is a fact about a run rather than a side effect
    * accumulated on a policy value that two runs might share. */
  def dropsMethod(ownerFqcn: String, method: String, paramTypes: List[String] = Nil): Boolean =
    dropMethods.contains(s"$ownerFqcn#$method") ||
      dropMethods.contains(s"$ownerFqcn#$method(${paramTypes.mkString(",")})")

  /** every declared key, in the one grammar a report quotes them in. */
  def keys: Set[String] = dropTypes ++ dropMethods

object Substitutions:
  val none: Substitutions = Substitutions()

  /** every `.scala` file under `dir`, sorted; nothing when the directory does not exist.
    *
    * One body, HERE rather than in the check that reads it, because three layers now ask the same
    * question of an injection root — the run (what to copy, and what to note), `SubstitutionCheck`
    * (is a dropped type replaced), and `SurfaceFold` (does a BASE ship a replacement at the name a
    * dependent wants to re-point) — and the last of those lives below the engine. */
  def scalaSources(dir: Path): List[Path] =
    import scala.jdk.CollectionConverters.*
    if !Files.exists(dir) then Nil
    else
      val walk = Files.walk(dir)
      try walk.iterator().asScala.filter(_.toString.endsWith(".scala")).toList.sorted
      finally walk.close()

  /** what a set of injection roots SUPPLIES: emitted FQN → the root-relative path it came from.
    *
    * The FQN is the relative path with its `.scala` dropped and its separators turned into dots —
    * the one derivation, since the file's own location is what decides the name it stands at. Note
    * these are EMITTED names (`PortMap` says the same thing at its own seam): an injection root
    * holds the port's namespace, while a drop key is UPSTREAM, so any comparison between the two
    * translates first (§4.56).
    *
    * A root that does not exist supplies NOTHING, which is not a lenient reading — it is the same
    * answer the run gives, since the copy loop skips it and no file lands at any of those names. */
  def injectedSources(roots: List[Path]): List[(String, String)] =
    roots.filter(Files.exists(_)).flatMap { root =>
      scalaSources(root).map { src =>
        val rel = root.relativize(src).toString.replace('\\', '/')
        rel.stripSuffix(".scala").replace('/', '.') -> rel
      }
    }

/** Marks a symbol the port must NOT emit, so INTERMEDIATE layers can still see it.
  *
  * A dropped type is deliberately kept in the model and its references stay resolved — dropping it
  * from the parse set instead would leave every use unresolved and silently degrade translation of
  * the code around it. But those uses must be recognisable as substitution targets rather than as
  * ordinary references, because a replacement is not necessarily name- or API-compatible: a
  * transform may need to rewrite `Json.readValue(…)` into a Kindlings codec call, or re-point a
  * type reference somewhere else entirely.
  *
  * So the frontend tags the symbol and any phase can detect it:
  * {{{
  * program.symbolOf(id).flatMap(Substituted.of) match
  *   case Some(Substituted(fqn)) => rewriteIntoReplacement(fqn)
  *   case None                   => leaveAlone
  * }}}
  *
  * The declaration itself is never emitted; what remains at that FQN is whatever the substitution
  * supplies — injected Scala, a rewrite performed by a phase, or nothing at all if every use was
  * rewritten away. Emission is verified against exactly that expectation.
  */
final case class Substituted(fqn: String) extends SymTag

object Substituted:
  def of(sym: Symbol): Option[Substituted] = sym.tags.collectFirst { case s: Substituted => s }
  def tags(sym: Symbol): Boolean           = of(sym).isDefined
