package balticporter.core

import java.nio.file.{Files, Path}
import balticporter.tir.Phase

/** A [[balticporter.tir.Phase]] whose output references types from `balticporter.runtime`.
  * [[RuntimePlan]] derives the build dependency and vendored sources from phases that declare this. */
trait RequiresRuntime:
  self: Phase =>
  /** fully-qualified names under [[RuntimeArtifact.Package]] this phase's output can reference. */
  def runtimeTypes: Set[String]

/** How `balticporter-runtime` reaches a port. */
enum RuntimeMode:
  /** Library dependency (default). */
  case Dependency
  /** Sources written into `src_managed`. Only correct for single-module ports. */
  case Vendored

object RuntimeMode:
  val VendoredFlag = "--vendored-runtime"

  def fromArgs(args: Seq[String]): RuntimeMode =
    if args.contains(VendoredFlag) then RuntimeMode.Vendored else RuntimeMode.Dependency

/** The published `balticporter-runtime` artifact. Support types are real compiled Scala;
  * the engine keeps a verbatim copy as resources for [[RuntimeMode.Vendored]].
  * Version-locked to the engine via `BuildVersion`. */
object RuntimeArtifact:

  /** the single package every support type lives in. */
  val Package = "balticporter.runtime"

  val organization: String = BuildVersion.organization
  val artifact: String     = BuildVersion.runtimeArtifact
  val version: String      = BuildVersion.version

  /** Maven-ish coordinates, build-tool-agnostic. `crossScala` = `%%`. */
  final case class Coordinates(organization: String, artifact: String, version: String, crossScala: Boolean = true)

  val coordinates: Coordinates = Coordinates(organization, artifact, version)

  private val ResourceDir = "balticporter/vendored-runtime"

  private def resource(name: String): Option[String] =
    Option(getClass.getClassLoader.getResourceAsStream(s"$ResourceDir/$name"))
      .map(is => try new String(is.readAllBytes(), "UTF-8") finally is.close())

  /** FQN -> source text, read from vendored resources. `RuntimeArtifactSpec` asserts agreement. */
  lazy val vendored: Map[String, String] =
    val index = resource("index.txt").getOrElse(
      throw new IllegalStateException(
        s"$ResourceDir/index.txt is missing from the classpath — balticporter-engine was built " +
          "without the runtime vendoring generator (see build.sbt)"
      )
    )
    index.linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { file =>
        val fqn = s"$Package.${file.stripSuffix(".scala")}"
        fqn -> resource(file).getOrElse(throw new IllegalStateException(s"$ResourceDir/$file listed but absent"))
      }
      .toMap

  def sourceOf(fqn: String): String =
    vendored.getOrElse(fqn, throw new NoSuchElementException(s"$fqn is not part of $artifact ${vendored.keySet.toList.sorted}"))

  /** Close `fqns` over inter-type references between support types. Textual. */
  def closure(fqns: Set[String]): Set[String] =
    val known = vendored.keySet
    def step(acc: Set[String]): Set[String] =
      val next = acc.flatMap { fqn =>
        val src = vendored.getOrElse(fqn, "")
        known.filter(other => other != fqn && src.contains(other.substring(Package.length + 1)))
      }
      if next.subsetOf(acc) then acc else step(acc ++ next)
    step(fqns.filter(known.contains)) ++ fqns.filterNot(known.contains)

  /** Concrete instance members each support type brings, as `(name, param counts per list)`.
    * Used by `TirEmitter.externalConcrete` for diamond detection.
    * Declared, not derived; `RuntimeMembersDerivationSpec` asserts agreement with the sources. */
  val concreteMembers: Map[String, Set[(String, List[Int])]] = Map(
    s"$Package.JavaIterator" -> Set(("remove", List(0))),
    // `JavaListIterator` brings nothing concrete; its key is needed for derivation completeness.
    s"$Package.JavaListIterator" -> Set.empty,
    s"$Package.JavaIterable" -> Set.empty,
    // `Wrapping` is a marker trait; abstract, so empty set. Needs its key for derivation. // ENGINE-LIMITS K19
    s"$Package.Wrapping" -> Set.empty,
    // Every concrete member of `JavaCollection` (all of `AbstractCollection` except `iterator()`/`size()`).
    s"$Package.JavaCollection" -> Set(
      ("isEmpty", List(0)), ("contains", List(1)), ("add", List(1)), ("remove", List(1)),
      ("clear", List(0)), ("containsAll", List(1)), ("addAll", List(1)), ("removeAll", List(1)),
      ("retainAll", List(1)), ("removeIf", List(1)), ("toArray", List(0)), ("toArray", List(1)),
    ),
    // NB `JavaCollections` is an object, never a parent -- no entry here.
  )

/** Runtime delivery plan derived from the phases that ran: the build dependency OR vendored sources
  * (mutually exclusive). */
final case class RuntimePlan(required: Set[String], mode: RuntimeMode):

  def isEmpty: Boolean = required.isEmpty

  /** the library dependency the generated build must declare, or `None` when nothing needs it or
    * the sources are being vendored instead. */
  def dependency: Option[RuntimeArtifact.Coordinates] =
    if isEmpty || mode == RuntimeMode.Vendored then None else Some(RuntimeArtifact.coordinates)

  /** FQN -> source, for [[RuntimeMode.Vendored]]; empty otherwise. Closed over inter-type refs. */
  def sources: Map[String, String] =
    if isEmpty || mode == RuntimeMode.Dependency then Map.empty
    else RuntimeArtifact.closure(required).map(fqn => fqn -> RuntimeArtifact.sourceOf(fqn)).toMap

  /** Concrete members of support types this run injected as parents. For `TirEmitter.externalConcrete`. */
  def concreteMembers: Map[String, Set[(String, List[Int])]] =
    RuntimeArtifact.concreteMembers.filter((fqn, _) => RuntimeArtifact.closure(required).contains(fqn))

  /** Write vendored sources under `dir`. Returns count written (0 under Dependency mode). */
  def writeSources(dir: Path): Int =
    sources.foreach { (fqn, src) =>
      val p = dir.resolve(fqn.replace('.', '/') + ".scala")
      Files.createDirectories(p.getParent)
      Files.writeString(p, src)
    }
    sources.size

object RuntimePlan:
  /** no phase asked for anything — the empty parameter that makes the mechanism a no-op. */
  val none: RuntimePlan = RuntimePlan(Set.empty, RuntimeMode.Dependency)

  /** THE orchestrator entry point: read the requirement off the phases that were run. */
  def of(phases: List[Phase], mode: RuntimeMode = RuntimeMode.Dependency): RuntimePlan =
    RuntimePlan(phases.collect { case r: RequiresRuntime => r.runtimeTypes }.flatten.toSet, mode)
