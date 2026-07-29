package balticporter.core

import java.nio.file.{Files, Path}
import balticporter.tir.Phase

/** A [[balticporter.tir.Phase]] whose OUTPUT references types from `balticporter.runtime`.
  *
  * This is the declaration that makes runtime delivery an ORCHESTRATOR decision rather than a
  * caller's. A phase that retypes code onto a support type says so once, here; the run then
  * derives — from the phases it actually ran — the build dependency, the vendored sources and the
  * emitter's external-parent table, all three from the same set (see [[RuntimePlan]]). A caller
  * cannot forget to add the dependency, because no caller adds it.
  *
  * CLAUDE.md §1(a): universal. "Emitted code may reference support types that are not in the Scala
  * stdlib, and the build must then be able to see them" is a fact about porting, not about any
  * library. WHICH types is the phase's business, and it is the phase that declares them.
  */
trait RequiresRuntime:
  self: Phase =>
  /** fully-qualified names under [[RuntimeArtifact.Package]] this phase's output can reference. */
  def runtimeTypes: Set[String]

/** How `balticporter-runtime` reaches a port. */
enum RuntimeMode:
  /** the default: the generated build declares `balticporter-runtime` as a library dependency, so
    * every module of a multi-module port links against ONE copy of each support type. */
  case Dependency

  /** the escape hatch for a zero-dependency port: the support sources are written into the port's
    * `src_managed` alongside the emitted code. Correct only for a SINGLE-module port — two ports
    * built this way define the same FQNs twice, which the Scala.js and Native linkers reject and
    * which the JVM tolerates only while the two copies happen to agree. */
  case Vendored

object RuntimeMode:
  /** the flag a migration program passes straight through from its `main`. The DEFAULT is the
    * dependency; vendoring has to be asked for by name, because it is the mode that reintroduces
    * the duplicate-definition problem when a second module of the same library is ported. */
  val VendoredFlag = "--vendored-runtime"

  def fromArgs(args: Seq[String]): RuntimeMode =
    if args.contains(VendoredFlag) then RuntimeMode.Vendored else RuntimeMode.Dependency

/** The published `balticporter-runtime` artifact, as the engine sees it.
  *
  * The support types emitted code links against are REAL SCALA in the `runtime` module — compiled,
  * tested and published — not string literals in a transform. The engine keeps a verbatim COPY of
  * those sources as resources (build.sbt's `Compile / resourceGenerators` in `core`) so that
  * [[RuntimeMode.Vendored]] can still write them out; the copy is generated from the module, never
  * maintained beside it. Two texts at one FQN is precisely the defect the published artifact
  * exists to prevent, and it would be no less a defect one level down.
  *
  * The artifact is VERSION-LOCKED to the engine: both come from `BuildVersion`, which build.sbt
  * generates. A port produced by engine X therefore resolves runtime X, and `EnginePin` records
  * which X that was.
  */
object RuntimeArtifact:

  /** the single package every support type lives in. */
  val Package = "balticporter.runtime"

  val organization: String = BuildVersion.organization
  val artifact: String     = BuildVersion.runtimeArtifact
  val version: String      = BuildVersion.version

  /** Maven-ish coordinates, kept free of any build tool's types so `core` need not know sbt.
    * `crossScala` is `%%` — the runtime is a Scala library and is cross-versioned. */
  final case class Coordinates(organization: String, artifact: String, version: String, crossScala: Boolean = true)

  val coordinates: Coordinates = Coordinates(organization, artifact, version)

  private val ResourceDir = "balticporter/vendored-runtime"

  private def resource(name: String): Option[String] =
    Option(getClass.getClassLoader.getResourceAsStream(s"$ResourceDir/$name"))
      .map(is => try new String(is.readAllBytes(), "UTF-8") finally is.close())

  /** every FQN the published module carries → the module's own source text for it.
    *
    * Read from the vendored resources, whose contents are a build-time copy of
    * `runtime/src/main/scala`. `RuntimeArtifactSpec` asserts the two agree file for file. */
  lazy val vendored: Map[String, String] =
    val index = resource("index.txt").getOrElse(
      throw new IllegalStateException(
        s"$ResourceDir/index.txt is missing from the classpath — balticporter-core was built " +
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

  /** Close a set of required types over the references BETWEEN support types.
    *
    * `JavaIterable.iterator()` returns a `JavaIterator`: vendoring the first without the second
    * emits code that does not compile. A phase is not obliged to know the artifact's internal
    * shape, so the artifact closes the set itself. Textual, because the support sources are a
    * handful of files in one package and a simple-name occurrence is exactly the reference. */
  def closure(fqns: Set[String]): Set[String] =
    val known = vendored.keySet
    def step(acc: Set[String]): Set[String] =
      val next = acc.flatMap { fqn =>
        val src = vendored.getOrElse(fqn, "")
        known.filter(other => other != fqn && src.contains(other.substring(Package.length + 1)))
      }
      if next.subsetOf(acc) then acc else step(acc ++ next)
    step(fqns.filter(known.contains)) ++ fqns.filterNot(known.contains)

  /** CONCRETE instance members each support type brings, as `(name, param counts per list)` —
    * what `TirEmitter`'s `externalConcrete` needs to see a diamond against an injected parent.
    *
    * Still declared rather than derived. It CAN be derived from the module now that the module is
    * real, and `RuntimeMembersDerivationSpec` (in `verify`, which already has scalameta) does
    * exactly that and asserts this table equals the derivation. Promoting the derivation to
    * production code needs one of:
    *   - a scalameta dependency in `core` (it parses the vendored source; ~20 lines, and the spec
    *     is the implementation), or
    *   - a build-time generator that runs that parse and emits this table next to `BuildVersion`.
    * Reflection over the compiled classes is NOT sufficient: erasure cannot distinguish a nilary
    * `remove()` from a parameterless `remove`, and `List(0)` vs `Nil` is the whole content here. */
  val concreteMembers: Map[String, Set[(String, List[Int])]] = Map(
    s"$Package.JavaIterator" -> Set(("remove", List(0))),
    s"$Package.JavaIterable" -> Set.empty,
    // the DERIVED half of `JavaCollection` — what a ported class inherits without writing it, the
    // same members `java.util.AbstractCollection` supplies. Declaring them is what lets
    // `TirEmitter.diamondOverrides` see a conflict against this injected supertype; an empty set
    // here would make every one of them invisible to that check.
    s"$Package.JavaCollection" -> Set(
      ("containsAll", List(1)), ("addAll", List(1)), ("removeAll", List(1)),
      ("retainAll", List(1)), ("removeIf", List(1)), ("toArray", List(0)),
    ),
    // an `object` of statics — it is never a PARENT, so it contributes no inherited member and
    // `diamondOverrides` has nothing to see. Empty is the derivation's answer too, not a stub.
    s"$Package.JavaCollections" -> Set.empty,
  )

/** What a RUN owes the port it produced, derived from the phases that actually ran.
  *
  * One object owns both halves of runtime delivery — the build dependency and the vendored
  * sources — because they are mutually exclusive and a port that gets both, or neither, is broken
  * in a way no compile error names. `SbtGen.emitPort` takes the plan; `writeSources` writes what
  * the plan says to write, which under [[RuntimeMode.Dependency]] is nothing.
  */
final case class RuntimePlan(required: Set[String], mode: RuntimeMode):

  def isEmpty: Boolean = required.isEmpty

  /** the library dependency the generated build must declare, or `None` when nothing needs it or
    * the sources are being vendored instead. */
  def dependency: Option[RuntimeArtifact.Coordinates] =
    if isEmpty || mode == RuntimeMode.Vendored then None else Some(RuntimeArtifact.coordinates)

  /** FQN → source, for [[RuntimeMode.Vendored]]; empty otherwise. Closed over inter-type
    * references, so requiring `JavaIterable` also writes `JavaIterator`. */
  def sources: Map[String, String] =
    if isEmpty || mode == RuntimeMode.Dependency then Map.empty
    else RuntimeArtifact.closure(required).map(fqn => fqn -> RuntimeArtifact.sourceOf(fqn)).toMap

  /** `TirEmitter`'s `externalConcrete`: the concrete members of the support types this run put
    * into the program's parent positions. Derived from the same `required` set as the dependency,
    * so the emitter and the build can no longer disagree about what was injected — and passing it
    * is no longer something a caller can forget. */
  def concreteMembers: Map[String, Set[(String, List[Int])]] =
    RuntimeArtifact.concreteMembers.filter((fqn, _) => RuntimeArtifact.closure(required).contains(fqn))

  /** Write the vendored sources under `dir` (a port's `src_managed/main/scala`), one file per FQN.
    * Returns how many were written — 0 under [[RuntimeMode.Dependency]], by design. */
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
