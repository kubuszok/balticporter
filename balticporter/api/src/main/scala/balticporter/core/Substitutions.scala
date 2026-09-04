package balticporter.core

import balticporter.tir.{SymTag, Symbol}

import java.nio.file.{Files, Path}

/** Principled, typed directives for replacing constructs the port must NOT translate
  * mechanically with ready-made Scala. [[dropTypes]] excludes a whole type (a Scala def at the
  * same FQCN expected from [[inject]] or a dependency); [[dropMethods]] drops one `DefDef`;
  * [[inject]] copies ready-made Scala verbatim, surviving re-emit. Keys are fully-qualified. A key
  * matching NOTHING is a silent no-op — [[policyReport]]/[[matched]] exist to catch it. */
final case class Substitutions(
    dropTypes: Set[String] = Set.empty,
    dropMethods: Set[String] = Set.empty,
    inject: List[Path] = Nil,
):

  /** does policy drop this TYPE? PURE — see [[dropsMethod]]. */
  def dropsType(fqcn: String): Boolean = dropTypes.contains(fqcn)

  /** `owner#name` drops EVERY overload; `owner#name(P1,P2)` (erased simple param names) drops one
    * — overload precision is what makes constructors droppable (they all share `<init>`). PURE:
    * "did this key fire" is now `PolicyBinder`'s question, answered from the program and
    * `MemberIndex`, not a mutable tally two runs might share. */
  def dropsMethod(ownerFqcn: String, method: String, paramTypes: List[String] = Nil): Boolean =
    dropMethods.contains(s"$ownerFqcn#$method") ||
      dropMethods.contains(s"$ownerFqcn#$method(${paramTypes.mkString(",")})")

  /** every declared key, in the one grammar a report quotes them in. */
  def keys: Set[String] = dropTypes ++ dropMethods

object Substitutions:
  val none: Substitutions = Substitutions()

  /** every `.scala` file under `dir`, sorted; nothing when the directory does not exist. One body,
    * here rather than in a check, since three layers ask the same question of an injection root
    * (the run, `SubstitutionCheck`, `SurfaceFold`) and the last lives below the engine. */
  def scalaSources(dir: Path): List[Path] =
    import scala.jdk.CollectionConverters.*
    if !Files.exists(dir) then Nil
    else
      val walk = Files.walk(dir)
      try walk.iterator().asScala.filter(_.toString.endsWith(".scala")).toList.sorted
      finally walk.close()

  /** what a set of injection roots SUPPLIES: emitted FQN → the root-relative path it came from.
    * FQN is the relative path minus `.scala`, dots for separators. These are EMITTED names (a drop
    * key is UPSTREAM, so a comparison translates first, §4.56). A non-existent root supplies
    * NOTHING, consistent with the run's own copy loop. */
  def injectedSources(roots: List[Path]): List[(String, String)] =
    roots.filter(Files.exists(_)).flatMap { root =>
      scalaSources(root).map { src =>
        val rel = root.relativize(src).toString.replace('\\', '/')
        rel.stripSuffix(".scala").replace('/', '.') -> rel
      }
    }

/** Marks a symbol the port must NOT emit, so INTERMEDIATE layers can still see it. Kept in the
  * model with references resolved (dropping from the parse set would degrade the code around it);
  * tagged so a phase can rewrite a use into its replacement rather than leaving it as an ordinary
  * reference. Nothing at that FQN is emitted by the declaration itself — only what the
  * substitution supplies (injected Scala, a rewrite, or nothing if every use was rewritten away). */
final case class Substituted(fqn: String) extends SymTag

object Substituted:
  def of(sym: Symbol): Option[Substituted] = sym.tags.collectFirst { case s: Substituted => s }
  def tags(sym: Symbol): Boolean           = of(sym).isDefined
