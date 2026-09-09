/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * Adapted from sge's AssetManagerDescriptorRoutingIss830CoverageSuite.
 * Port's get(AssetDescriptor) delegates to get(fileName, type, required=true),
 * so a mismatched type throws GdxRuntimeException instead of returning empty.
 * The correct-type path returns the asset; the wrong-type path is tested via
 * get(fileName, type, required=false) to verify routing.
 */
package sge
package assets

import munit.FunSuite
import sge.assets.loaders.{ FileHandleResolver, SynchronousAssetLoader }
import sge.files.{ FileHandle, FileType }
import lowlevel.util.DynamicArray

class AssetManagerDescriptorRoutingIss830CoverageSuite extends FunSuite {

  final case class TestAsset(name: String)
  final case class OtherAsset(name: String)

  private class StubResolver extends FileHandleResolver {
    override def resolve(fileName: String): FileHandle = FileHandle(new java.io.File(fileName), FileType.Absolute)
  }

  private class TestAssetLoader(resolver: FileHandleResolver) extends SynchronousAssetLoader[TestAsset, AssetLoaderParameters[TestAsset]](resolver) {
    override def load(assetManager: AssetManager, fileName: String, file: FileHandle, parameter: AssetLoaderParameters[TestAsset]): TestAsset                        = TestAsset(fileName)
    override def getDependencies(fileName: String, file: FileHandle, parameter: AssetLoaderParameters[TestAsset]):                  DynamicArray[AssetDescriptor[?]] =
      null.asInstanceOf[DynamicArray[AssetDescriptor[?]]]
  }

  test("ISS-830 (coverage): get(AssetDescriptor) routes by the descriptor's declared type (AssetManager.java:180-181)") {
    given Sge    = SgeTestFixture.testSge()
    val resolver = StubResolver()
    val manager  = AssetManager(resolver, defaultLoaders = false)
    manager.setLoader(classOf[TestAsset], new TestAssetLoader(resolver))

    manager.load("foo.asset", classOf[TestAsset])
    manager.finishLoading()
    assert(manager.isLoaded("foo.asset"), "precondition: foo.asset loaded under TestAsset")

    // Correct type: descriptor routes to assets.get(TestAsset) -> found.
    val right = manager.get(new AssetDescriptor[TestAsset]("foo.asset", classOf[TestAsset]))
    assert(!right.isEmpty, "get(descriptor) with the matching type must return the asset")

    // Wrong type: same filename, different declared type. Routes to
    // assets.get(OtherAsset), which has no entry. Port's get(descriptor)
    // uses required=true, so it throws GdxRuntimeException.
    intercept[sge.utils.GdxRuntimeException] {
      manager.get(new AssetDescriptor[OtherAsset]("foo.asset", classOf[OtherAsset]))
    }

    // Verify the same routing via the non-throwing 3-arg get:
    val wrong = manager.get[OtherAsset]("foo.asset", classOf[OtherAsset], false)
    assert(
      wrong.isEmpty,
      "get with a mismatched declared type must NOT return the asset stored under a different type " +
        "(route-by-descriptor-type, AssetManager.java:180-181)"
    )

    manager.close()
  }
}
