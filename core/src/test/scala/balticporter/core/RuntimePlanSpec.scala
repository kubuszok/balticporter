package balticporter.core

import java.nio.file.Files
import balticporter.tir.Phase
import balticporter.transform.CollectionsTransform

/** The derivation that makes runtime delivery impossible to forget: a run reads its requirement
  * off the PHASES it ran, and the same plan drives the build dependency, the vendored sources and
  * the emitter's external-parent table. Nothing here is passed in by a caller.
  */
class RuntimePlanSpec extends munit.FunSuite:

  private class Inert extends Phase:
    def name = "inert"

  test("no phase requires anything -> the mechanism is a no-op") {
    val plan = RuntimePlan.of(List(new Inert))
    assert(plan.isEmpty)
    assertEquals(plan.dependency, None)
    assertEquals(plan.sources, Map.empty[String, String])
    assertEquals(plan.concreteMembers, Map.empty[String, Set[(String, List[Int])]])
  }

  test("a RequiresRuntime phase in the list yields the dependency, without the caller naming it") {
    val plan = RuntimePlan.of(List(new Inert, new CollectionsTransform))
    assertEquals(plan.dependency, Some(RuntimeArtifact.coordinates))
    assertEquals(plan.dependency.map(_.crossScala), Some(true))
    // the dependency mode ships NO sources — that is the point
    assertEquals(plan.sources, Map.empty[String, String])
  }

  test("--vendored-runtime is the named opt-out; the dependency is the default") {
    assertEquals(RuntimeMode.fromArgs(Nil), RuntimeMode.Dependency)
    assertEquals(RuntimeMode.fromArgs(Seq("--other")), RuntimeMode.Dependency)
    assertEquals(RuntimeMode.fromArgs(Seq("--vendored-runtime")), RuntimeMode.Vendored)
  }

  test("vendored mode ships the sources and NO dependency — they are mutually exclusive") {
    val plan = RuntimePlan.of(List(new CollectionsTransform), RuntimeMode.Vendored)
    assertEquals(plan.dependency, None)
    assertEquals(plan.sources.keySet, CollectionsTransform.runtimeTypes)
  }

  test("concreteMembers is derived from the same set as the dependency") {
    val plan = RuntimePlan.of(List(new CollectionsTransform))
    // this is what TirEmitter's `externalConcrete` needs; forgetting it disables diamond detection
    assertEquals(plan.concreteMembers, CollectionsTransform.runtimeConcreteMembers)
    assert(plan.concreteMembers(s"${RuntimeArtifact.Package}.JavaIterator").contains(("remove", List(0))))
  }

  test("writeSources lays the vendored types out by FQN, and writes nothing under Dependency") {
    val dir = Files.createTempDirectory("bp-runtime-plan")
    try
      assertEquals(RuntimePlan.of(List(new CollectionsTransform)).writeSources(dir), 0)
      assert(!Files.exists(dir.resolve("balticporter/runtime/JavaIterator.scala")))

      val n = RuntimePlan.of(List(new CollectionsTransform), RuntimeMode.Vendored).writeSources(dir)
      assertEquals(n, 2)
      val written = Files.readString(dir.resolve("balticporter/runtime/JavaIterator.scala"))
      assertEquals(written, RuntimeArtifact.sourceOf(s"${RuntimeArtifact.Package}.JavaIterator"))
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }

  test("CollectionsTransform's legacy accessors still answer, off the published sources") {
    // corpus migration programs call these two by name; they must keep working unchanged.
    assertEquals(CollectionsTransform.runtimeSources.keySet, CollectionsTransform.runtimeTypes)
    assertEquals(
      CollectionsTransform.runtimeSources(CollectionsTransform.JavaIteratorFqn),
      RuntimeArtifact.sourceOf(CollectionsTransform.JavaIteratorFqn),
    )
    assertEquals(CollectionsTransform.runtimeConcreteMembers.keySet, CollectionsTransform.runtimeTypes)
  }
