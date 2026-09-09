/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port:
 *   - MemoryFileHandle gains (using Sge) clause (port's FileHandleStream requires Sge context)
 *   - SaveData.load[Int] -> load[java.lang.Integer] (port's T <: Object bound)
 *   - Nullable usage unchanged (port uses lowlevel.Nullable)
 *
 * Red tests for ISS-507 (3D particle effect save AND load are dead) and
 * ISS-550 (ResourceData SaveData round-trip corrupts non-string values).
 */
package sge
package graphics
package g3d
package particles

import java.io.{ ByteArrayInputStream, InputStream, StringWriter, Writer }
import java.nio.charset.StandardCharsets

import sge.assets.AssetManager
import sge.assets.loaders.FileHandleResolver
import sge.files.{ FileHandle, FileHandleStream }
import sge.graphics.g3d.particles.ParticleEffectLoader.{ ParticleEffectLoadParameter, ParticleEffectSaveParameter }
import sge.graphics.g3d.particles.batches.ParticleBatch
import sge.graphics.g3d.particles.emitters.RegularEmitter
import sge.graphics.g3d.particles.influencers.{ ColorInfluencer, ScaleInfluencer, SpawnInfluencer }
import sge.graphics.g3d.particles.renderers.BillboardRenderer
import lowlevel.Nullable
import lowlevel.util.DynamicArray
import sge.utils.{ Json, readFromString, writeToString }
import sge.utils.given

class ParticleEffectIoRedSuite extends munit.FunSuite {

  // --- Headless fixture ------------------------------------------------------

  /** In-memory "file": writeString goes through writer(), so capturing the Writer is enough to
    * observe the saved JSON; read() serves it back for the loader's readJson. */
  final private class MemoryFileHandle(path: String)(using Sge) extends FileHandleStream(path) {

    var content: String = ""

    override def read(): InputStream =
      new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))

    override def writer(append: Boolean, charset: Nullable[String]): Writer =
      new StringWriter() {
        override def close(): Unit = {
          content = this.toString
          super.close()
        }
      }
  }

  /** Never consulted: the minimal effect saves no assets. */
  private val resolver: FileHandleResolver = new FileHandleResolver {
    def resolve(fileName: String): FileHandle = throw new UnsupportedOperationException
  }

  /** One controller: RegularEmitter + 3 asset-free influencers + BillboardRenderer. */
  private def makeEffect()(using Sge): ParticleEffect = {
    val emitter = new RegularEmitter()
    emitter.minParticleCount = 11
    emitter.maxParticleCount = 23
    emitter.durationValue.setLow(3000f)
    emitter.continuous = false
    val controller = ParticleController(
      "iss507-emitter-ctrl",
      emitter,
      new BillboardRenderer(),
      new ColorInfluencer.Single(),
      new ScaleInfluencer(),
      new SpawnInfluencer()
    )
    ParticleEffect(controller)
  }

  /** Runs the documented save path and returns the serialized JSON text. */
  private def saveToString(effect: ParticleEffect, file: MemoryFileHandle)(using Sge): String = {
    val loader  = new ParticleEffectLoader(resolver)
    val manager = AssetManager(resolver, defaultLoaders = false)
    loader.save(effect, new ParticleEffectSaveParameter(file, manager))
    file.content
  }

  /** ResourceData round-trip via toJson/fromJson. */
  private def textRoundTrip(rd: ResourceData[ParticleEffect]): ResourceData[ParticleEffect] = {
    val text = writeToString[Json](rd.toJson)
    ResourceData.fromJson[ParticleEffect](readFromString[Json](text))
  }

  // --- ISS-507 ---------------------------------------------------------------

  test("ISS-507: save writes the effect definition (controller name + emitter fields), not just assets/data/unique") {
    given Sge = SgeTestFixture.testSge()

    val serialized = saveToString(makeEffect(), new MemoryFileHandle("iss507-save.pfx"))
    assert(
      serialized.contains("iss507-emitter-ctrl"),
      s"saved JSON must contain the effect definition (controller name); got: $serialized"
    )
    assert(
      serialized.contains("minParticleCount"),
      s"saved JSON must contain the emitter configuration; got: $serialized"
    )
  }

  test("ISS-507: save -> loadSync round-trip restores controller count and emitter config") {
    given Sge = SgeTestFixture.testSge()

    val fileName = "iss507-roundtrip.pfx"
    val file     = new MemoryFileHandle(fileName)
    val loader   = new ParticleEffectLoader(resolver)
    val manager  = AssetManager(resolver, defaultLoaders = false)
    loader.save(makeEffect(), new ParticleEffectSaveParameter(file, manager))

    val loadParam = new ParticleEffectLoadParameter(Nullable.empty[DynamicArray[ParticleBatch[?]]])
    val deps = loader.getDependencies(fileName, file, loadParam)
    assertEquals(deps.size, 0, "minimal effect must have no asset dependencies")

    val loaded = loader.loadSync(manager, fileName, file, loadParam)

    assertEquals(loaded.controllers.size, 1, "round-trip must preserve the controller count")
    val controller = loaded.controllers(0)
    assertEquals(controller.name, "iss507-emitter-ctrl", "round-trip must preserve the controller name")
    assert(
      controller.emitter.isInstanceOf[RegularEmitter],
      s"round-trip must preserve the emitter type; got: ${controller.emitter}"
    )
    val emitter = controller.emitter.asInstanceOf[RegularEmitter]
    assertEquals(emitter.minParticleCount, 11, "round-trip must preserve minParticleCount")
    assertEquals(emitter.maxParticleCount, 23, "round-trip must preserve maxParticleCount")
    assertEquals(emitter.durationValue.lowMin, 3000f, "round-trip must preserve the emitter duration")
    assert(!emitter.continuous, "round-trip must preserve the continuous flag")
    assertEquals(controller.influencers.size, 3, "round-trip must preserve the influencer count")
  }

  // --- ISS-550 ---------------------------------------------------------------

  test("ISS-550: SaveData Int value round-trips as an Int") {
    val rd = ResourceData[ParticleEffect]()
    val sd = rd.createSaveData()
    sd.save("index", Integer.valueOf(7))

    val sd2 = textRoundTrip(rd).saveData
    // port's T <: Object bound requires java.lang.Integer
    val restored: Int = sd2.load[java.lang.Integer]("index").map(_.intValue).getOrElse(-1)
    assertEquals(restored, 7, "Integer SaveData value must survive the round-trip as an Int")
  }

  test(
    "ISS-550: SaveData nested int-array payload round-trips structurally"
  ) {
    val rd = ResourceData[ParticleEffect]()
    val sd = rd.createSaveData()
    val first = DynamicArray[Int]()
    first.add(0)
    first.add(2)
    val second = DynamicArray[Int]()
    second.add(1)
    val nested = DynamicArray[DynamicArray[Int]]()
    nested.add(first)
    nested.add(second)
    sd.save("indices", nested.asInstanceOf[AnyRef])

    val sd2 = textRoundTrip(rd).saveData
    val restored: Nullable[DynamicArray[DynamicArray[Int]]] = sd2.load("indices")
    val arrays = restored.getOrElse(fail("\"indices\" payload missing after round-trip"))
    assertEquals(arrays.size, 2, "outer array size must survive the round-trip")
    assertEquals(arrays(0).size, 2)
    assertEquals(arrays(0)(0), 0)
    assertEquals(arrays(0)(1), 2)
    assertEquals(arrays(1).size, 1)
    assertEquals(arrays(1)(0), 1)
  }

  // --- Control (green at red commit) -----------------------------------------

  test("ISS-507 control (green at red commit): ResourceData.fromJson still parses a minimal assets-only file") {
    val text =
      """{"assets":[{"filename":"smoke.png","type":"sge.graphics.Texture"}],"data":[{"data":{"key":"flame"},"indices":[0]}],"unique":{}}"""
    val rd = ResourceData.fromJson[ParticleEffect](readFromString[Json](text))
    assertEquals(rd.assets.size, 1, "one shared asset must be parsed")
    assertEquals(rd.assets(0).filename, "smoke.png")
    assertEquals(rd.assets(0).`type`: Class[?], classOf[sge.graphics.Texture]: Class[?])
    val sd = rd.saveData
    assertEquals(sd.load[java.lang.String]("key").getOrElse(""), "flame", "String SaveData values already round-trip")
    val descriptor = sd.loadAsset()
    assertEquals(descriptor.map(_.fileName).getOrElse(""), "smoke.png", "asset indices must resolve to descriptors")
  }
}
