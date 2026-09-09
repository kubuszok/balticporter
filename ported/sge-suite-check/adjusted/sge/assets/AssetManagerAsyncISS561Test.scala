/*
 * SGE — AssetManager ASYNC load-path unit tests (ISS-561, batch F)
 *
 * ADAPTED for the port: Nullable.empty -> 2-arg AssetDescriptor constructor;
 * get(...).isEmpty -> get(..., false).isEmpty (port's 2-arg get throws).
 *
 * AssetManagerUnitTest.scala already covers the SYNCHRONOUS loader path
 * (SynchronousAssetLoader): load + finishLoading + get, ref-counting,
 * unload/dispose, progress reaching 1.0, queue removal, etc.
 *
 * This suite covers the ASYNC path it misses: an AsynchronousAssetLoader
 * driven by update()/finishLoading() through AssetLoadingTask. On JVM the
 * async part (loadAsync) genuinely runs on a background worker thread
 * (ConcurrencyOpsDesktop), and update() polls the future until it completes,
 * then runs loadSync on the calling thread. We pin EXACT phase order,
 * EXACT progress fractions, and EXACT ref-count/dispose behaviour so that
 * representative production mutations would fail.
 */
package sge
package assets

import java.util.concurrent.CopyOnWriteArrayList
import scala.jdk.CollectionConverters.*

import munit.FunSuite
import sge.assets.loaders.{ AsynchronousAssetLoader, FileHandleResolver }
import sge.files.{ FileHandle, FileType }
import lowlevel.util.DynamicArray

class AssetManagerAsyncISS561Test extends FunSuite {

  // ─── Test infrastructure ─────────────────────────────────────────────

  /** A trivial "asset" type for testing. Records dispose for ref-count tests. */
  final case class AsyncAsset(name: String, loadAsyncTag: String) extends AutoCloseable {
    @volatile var closed: Boolean = false
    override def close(): Unit    = closed = true
  }

  /** Resolver that returns a FileHandle wrapping the filename (no real I/O). */
  final private class StubResolver extends FileHandleResolver {
    override def resolve(fileName: String): FileHandle =
      FileHandle(new java.io.File(fileName), FileType.Absolute)
  }

  /** An asynchronous loader for AsyncAsset that RECORDS every phase it runs. */
  final private class RecordingAsyncLoader(
    resolver:   FileHandleResolver,
    val phases: CopyOnWriteArrayList[String],
    val dependencyOf: Map[String, String] = Map.empty
  ) extends AsynchronousAssetLoader[AsyncAsset, AssetLoaderParameters[AsyncAsset]](resolver) {

    @volatile private var asyncValue: String = ""

    override def getDependencies(
      fileName:  String,
      file:      FileHandle,
      parameter: AssetLoaderParameters[AsyncAsset]
    ): DynamicArray[AssetDescriptor[?]] = {
      phases.add(s"getDependencies:$fileName")
      dependencyOf.get(fileName) match {
        case Some(child) =>
          val arr = DynamicArray[AssetDescriptor[?]]()
          arr.add(new AssetDescriptor(child, classOf[AsyncAsset]))
          arr
        case None =>
          null.asInstanceOf[DynamicArray[AssetDescriptor[?]]]
      }
    }

    override def loadAsync(
      manager:   AssetManager,
      fileName:  String,
      file:      FileHandle,
      parameter: AssetLoaderParameters[AsyncAsset]
    ): Unit = {
      phases.add(s"loadAsync:$fileName")
      asyncValue = s"async($fileName)"
    }

    override def loadSync(
      manager:   AssetManager,
      fileName:  String,
      file:      FileHandle,
      parameter: AssetLoaderParameters[AsyncAsset]
    ): AsyncAsset = {
      phases.add(s"loadSync:$fileName")
      AsyncAsset(fileName, asyncValue)
    }
  }

  private def makeContext(): Sge = SgeTestFixture.testSge()

  // ─── 0. ISS-684 anchor: close() must NOT kill other managers' loads ──

  test("ISS-684: closing one AssetManager must NOT break async loading on another (shared executor must not be shut down)") {
    given Sge    = makeContext()
    val resolver = StubResolver()

    val phasesA  = CopyOnWriteArrayList[String]()
    val managerA = AssetManager(resolver, defaultLoaders = false)
    managerA.setLoader(classOf[AsyncAsset], new RecordingAsyncLoader(resolver, phasesA))
    managerA.load("a-first.async", classOf[AsyncAsset])
    managerA.finishLoading()
    assert(managerA.isLoaded("a-first.async"), "manager A must load its asset before close()")
    managerA.close()

    val phasesB  = CopyOnWriteArrayList[String]()
    val managerB = AssetManager(resolver, defaultLoaders = false)
    managerB.setLoader(classOf[AsyncAsset], new RecordingAsyncLoader(resolver, phasesB))
    managerB.load("b-second.async", classOf[AsyncAsset])

    managerB.finishLoading()

    assert(managerB.isLoaded("b-second.async"), "manager B's async asset must load after manager A was closed")
    val asset = managerB.apply[AsyncAsset]("b-second.async", classOf[AsyncAsset])
    assertEquals(asset.name, "b-second.async")
    assertEquals(asset.loadAsyncTag, "async(b-second.async)", "loadAsync must have run on a live executor for manager B")
    assertEquals(
      phasesB.asScala.count(_ == "loadAsync:b-second.async"),
      1,
      "manager B's loadAsync must run exactly once on a live executor"
    )

    managerB.close()
  }

  // ─── 1. load() queues; asset not available until updated ─────────────

  test("ISS561: async load queues but asset is NOT finished/available until update() drives it") {
    given Sge    = makeContext()
    val resolver = StubResolver()
    val phases   = CopyOnWriteArrayList[String]()
    val manager  = AssetManager(resolver, defaultLoaders = false)
    manager.setLoader(classOf[AsyncAsset], new RecordingAsyncLoader(resolver, phases))

    manager.load("a.async", classOf[AsyncAsset])
    assert(!manager.isFinished, "queued async asset must leave manager not finished")
    assertEquals(manager.isLoaded("a.async"), false)
    assert(manager.get[AsyncAsset]("a.async", classOf[AsyncAsset]).isEmpty, "get must be empty before update")
    assert(manager.contains("a.async"), "contains must be true for a queued asset")
    assertEquals(phases.size, 0, "no loader phase may run from load() alone")

    manager.finishLoading()
    manager.close()
  }

  // ─── 2. update() drives loadAsync THEN loadSync, producing the asset ──

  test("ISS561: update() runs loadAsync BEFORE loadSync and get() returns the exact produced asset") {
    given Sge    = makeContext()
    val resolver = StubResolver()
    val phases   = CopyOnWriteArrayList[String]()
    val manager  = AssetManager(resolver, defaultLoaders = false)
    manager.setLoader(classOf[AsyncAsset], new RecordingAsyncLoader(resolver, phases))

    manager.load("hero.async", classOf[AsyncAsset])

    var iterations = 0
    var done       = false
    while (!done && iterations < 1000) {
      done = manager.update()
      iterations += 1
      if (!done) Thread.sleep(2)
    }
    assert(done, s"async load did not finish in $iterations iterations")
    assert(
      iterations >= 2,
      s"async load must take >= 2 update() calls (was $iterations); one-shot load means loadAsync was skipped or run on the calling thread"
    )

    assert(manager.isFinished, "manager must be finished after draining the queue")
    assert(manager.isLoaded("hero.async"))

    val recorded = phases.asScala.toList
    assertEquals(
      recorded,
      List("getDependencies:hero.async", "loadAsync:hero.async", "loadSync:hero.async"),
      s"phase order wrong: $recorded"
    )
    assert(
      recorded.indexOf("loadAsync:hero.async") < recorded.indexOf("loadSync:hero.async"),
      "loadAsync must run strictly before loadSync"
    )

    val asset = manager.apply[AsyncAsset]("hero.async", classOf[AsyncAsset])
    assertEquals(asset.name, "hero.async")
    assertEquals(asset.loadAsyncTag, "async(hero.async)", "loadSync must consume the value loadAsync produced")

    manager.close()
  }

  // ─── 3. progress goes 0 -> 0.5 -> 1.0 across two async assets ────────

  test("ISS561: getProgress advances 0 -> 0.5 -> 1.0 across two async assets") {
    given Sge    = makeContext()
    val resolver = StubResolver()
    val phases   = CopyOnWriteArrayList[String]()
    val manager  = AssetManager(resolver, defaultLoaders = false)
    manager.setLoader(classOf[AsyncAsset], new RecordingAsyncLoader(resolver, phases))

    manager.load("first.async", classOf[AsyncAsset])
    manager.load("second.async", classOf[AsyncAsset])

    assertEqualsFloat(manager.progress, 0.0f, 0.0f)

    manager.finishLoadingAsset[AsyncAsset]("first.async")
    assert(manager.isLoaded("first.async"))
    assertEquals(manager.isLoaded("second.async"), false, "second asset must still be pending")
    assertEqualsFloat(manager.progress, 0.5f, 0.0f)

    manager.finishLoading()
    assert(manager.isLoaded("second.async"))
    assertEqualsFloat(manager.progress, 1.0f, 0.0f)
    assertEquals(manager.loadedAssets, 2)

    manager.close()
  }

  // ─── 4. ref-counting + dispose with the async path ───────────────────

  test("ISS561: loading the same async asset twice loads it ONCE; dispose only at ref-count 0") {
    given Sge    = makeContext()
    val resolver = StubResolver()
    val phases   = CopyOnWriteArrayList[String]()
    val manager  = AssetManager(resolver, defaultLoaders = false)
    manager.setLoader(classOf[AsyncAsset], new RecordingAsyncLoader(resolver, phases))

    manager.load("shared.async", classOf[AsyncAsset])
    manager.load("shared.async", classOf[AsyncAsset])
    manager.finishLoading()

    val loadAsyncCount = phases.asScala.count(_ == "loadAsync:shared.async")
    assertEquals(loadAsyncCount, 1, "the actual async load must run exactly once for a shared asset")
    assertEquals(manager.referenceCount("shared.async"), 2)

    val asset = manager.apply[AsyncAsset]("shared.async", classOf[AsyncAsset])
    assertEquals(asset.closed, false)

    manager.unload("shared.async")
    assertEquals(manager.referenceCount("shared.async"), 1)
    assert(manager.isLoaded("shared.async"), "asset must survive first unload")
    assertEquals(asset.closed, false, "dispose must NOT run while ref count > 0")

    manager.unload("shared.async")
    assertEquals(manager.loadedAssets, 0)
    assertEquals(asset.closed, true, "dispose must run when ref count reaches 0")

    manager.close()
  }

  // ─── 5. dependency declared via getDependencies loads first (async) ──

  test("ISS561: an async asset with a dependency loads the dependency FIRST") {
    given Sge    = makeContext()
    val resolver = StubResolver()
    val phases   = CopyOnWriteArrayList[String]()
    val manager  = AssetManager(resolver, defaultLoaders = false)
    manager.setLoader(
      classOf[AsyncAsset],
      new RecordingAsyncLoader(resolver, phases, dependencyOf = Map("parent.async" -> "child.async"))
    )

    manager.load("parent.async", classOf[AsyncAsset])
    manager.finishLoading()

    assert(manager.isLoaded("parent.async"))
    assert(manager.isLoaded("child.async"), "dependency must be loaded")

    val recorded = phases.asScala.toList
    assertEquals(
      recorded.count(_ == "getDependencies:child.async"),
      1,
      s"child must resolve dependencies exactly once and declare none: $recorded"
    )
    assert(
      !recorded.exists(p => p.startsWith("getDependencies:") && p != "getDependencies:parent.async" && p != "getDependencies:child.async"),
      s"only parent.async and child.async may resolve dependencies: $recorded"
    )

    val childDone  = recorded.indexOf("loadSync:child.async")
    val parentDone = recorded.indexOf("loadSync:parent.async")
    assert(childDone >= 0, s"dependency was never loaded via loadSync: $recorded")
    assert(parentDone >= 0, s"parent was never loaded via loadSync: $recorded")
    assert(childDone < parentDone, s"dependency must finish loading before the parent: $recorded")

    val parentAsset = manager.apply[AsyncAsset]("parent.async", classOf[AsyncAsset])
    assertEquals(parentAsset.name, "parent.async")
    assertEquals(parentAsset.loadAsyncTag, "async(parent.async)", "parent loadSync must consume parent loadAsync's value")

    val deps = manager.dependencies("parent.async")
    assert(deps.isDefined, "parent must record its dependency")

    manager.close()
  }

  // ─── 6. unload while queued (async) cancels it cleanly ───────────────

  test("ISS561: unloading a queued async asset removes it without loading") {
    given Sge    = makeContext()
    val resolver = StubResolver()
    val phases   = CopyOnWriteArrayList[String]()
    val manager  = AssetManager(resolver, defaultLoaders = false)
    manager.setLoader(classOf[AsyncAsset], new RecordingAsyncLoader(resolver, phases))

    manager.load("doomed.async", classOf[AsyncAsset])
    manager.unload("doomed.async")
    manager.finishLoading()

    assertEquals(manager.loadedAssets, 0)
    assertEquals(manager.isLoaded("doomed.async"), false)
    assertEquals(phases.asScala.count(_.startsWith("loadSync")), 0, "a queued-then-unloaded asset must never run loadSync")

    manager.close()
  }
}
