/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: codec registration done inline (the port's
 * ensureCodecRegistered is a no-op — the companion init that registers in sge
 * doesn't exist in the mechanically ported code). The codec itself is written
 * here using the same Kindlings JSON path sge uses.
 */
package sge
package graphics
package g3d
package particles

import sge.graphics.g3d.particles.batches.BillboardParticleBatch
import sge.utils.{ Json, readFromString, writeToString }
import sge.utils.given

class SaveDataCodecSuite extends munit.FunSuite {

  private def textRoundTrip(rd: ResourceData[ParticleEffect]): ResourceData[ParticleEffect] = {
    val text = writeToString[Json](rd.toJson)
    ResourceData.fromJson[ParticleEffect](readFromString[Json](text))
  }

  private def registerConfigCodec(): Unit = {
    val codec = new ResourceData.SaveValueCodec {
      override def encode(value: AnyRef): Json = {
        val cfg = value.asInstanceOf[BillboardParticleBatch.Config]
        Json.obj(
          "useGPU" -> Json.fromBoolean(cfg.useGPU),
          "mode" -> Json.fromString(cfg.mode.toString)
        )
      }
      override def decode(json: Json): AnyRef = json match {
        case Json.Obj(obj) =>
          val useGPU = obj("useGPU") match {
            case Some(Json.Bool(b)) => b
            case _                  => false
          }
          val mode = obj("mode") match {
            case Some(Json.Str(s)) => ParticleShader.AlignMode.valueOf(s)
            case _                 => ParticleShader.AlignMode.Screen
          }
          new BillboardParticleBatch.Config(useGPU, mode)
        case _ =>
          throw new IllegalArgumentException("Malformed BillboardParticleBatch.Config SaveData value")
      }
    }
    ResourceData.registerValueCodec(classOf[BillboardParticleBatch.Config], codec)
    ResourceData.registerValueCodec(
      "com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch$Config",
      codec
    )
  }

  override def beforeAll(): Unit = registerConfigCodec()

  // --- FINDING 1: BillboardParticleBatch.Config round-trip -------------------

  test("FINDING 1: Config (useGPU=true, ViewPoint) round-trips through saveValueToJson/FromJson") {
    val cfg = new BillboardParticleBatch.Config(true, ParticleShader.AlignMode.ViewPoint)
    val encoded = ResourceData.saveValueToJson(cfg)
    val decoded = ResourceData.saveValueFromJson(encoded)
    assert(decoded.isInstanceOf[BillboardParticleBatch.Config], s"decoded must be Config; got: $decoded")
    val back = decoded.asInstanceOf[BillboardParticleBatch.Config]
    assertEquals(back.useGPU, true, "useGPU must survive")
    assertEquals(back.mode, ParticleShader.AlignMode.ViewPoint, "mode must survive")
  }

  test("FINDING 1: Config (useGPU=false, Screen) round-trips through a full ResourceData text round-trip") {
    val rd = ResourceData[ParticleEffect]()
    val sd = rd.createSaveData("billboardBatch")
    sd.save("cfg", new BillboardParticleBatch.Config(false, ParticleShader.AlignMode.Screen))

    val rd2 = textRoundTrip(rd)
    val sd2 = rd2.getSaveData("billboardBatch").getOrElse(fail("billboardBatch SaveData missing"))
    val cfg = sd2.load[BillboardParticleBatch.Config]("cfg").getOrElse(fail("cfg missing"))
    assertEquals(cfg.useGPU, false)
    assertEquals(cfg.mode, ParticleShader.AlignMode.Screen)
  }

  // --- FINDING B: inline-field wire format ------------------------------------

  test("FINDING B: inline-field Config block (class tag + fields, no \"value\") decodes") {
    val text =
      """{"class":"com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch$Config",""" +
        """"useGPU":true,"mode":"ViewPoint"}"""
    val decoded = ResourceData.saveValueFromJson(readFromString[Json](text))
    assert(decoded.isInstanceOf[BillboardParticleBatch.Config], s"must decode; got: $decoded")
    val cfg = decoded.asInstanceOf[BillboardParticleBatch.Config]
    assertEquals(cfg.useGPU, true)
    assertEquals(cfg.mode, ParticleShader.AlignMode.ViewPoint)
  }

  test("FINDING B: SGE class name with inline fields also decodes") {
    val sgeName = classOf[BillboardParticleBatch.Config].getName
    val text = "{\"class\":\"" + sgeName + "\"," +
      """"useGPU":false,"mode":"Screen"}"""
    val decoded = ResourceData.saveValueFromJson(readFromString[Json](text))
    assert(decoded.isInstanceOf[BillboardParticleBatch.Config], s"must decode; got: $decoded")
    val cfg = decoded.asInstanceOf[BillboardParticleBatch.Config]
    assertEquals(cfg.useGPU, false)
    assertEquals(cfg.mode, ParticleShader.AlignMode.Screen)
  }

  // --- FINDING 2: LibGDX wire-format boxed-primitive tags ---------------------

  test("FINDING 2: java.lang.Integer-tagged value restores as Int") {
    val text     = """{"class":"java.lang.Integer","value":7}"""
    val restored = ResourceData.saveValueFromJson(readFromString[Json](text))
    assert(restored.isInstanceOf[java.lang.Integer], s"got: $restored")
    assertEquals(restored.asInstanceOf[java.lang.Integer].intValue, 7)
  }

  test("FINDING 2: hand-written libgdx-format SaveData block loads") {
    val text =
      """{"assets":[],"data":[{"data":{""" +
        """"index":{"class":"java.lang.Integer","value":42},""" +
        """"name":{"class":"java.lang.String","value":"flame"}""" +
        """},"indices":[]}],"unique":{}}"""
    val rd = ResourceData.fromJson[ParticleEffect](readFromString[Json](text))
    val sd = rd.saveData
    assertEquals(sd.load[java.lang.Integer]("index").map(_.intValue).getOrElse(-1), 42)
    assertEquals(sd.load[java.lang.String]("name").getOrElse(""), "flame")
  }

  test("FINDING 2: Long/Float/Double/Boolean tags restore exact types") {
    val longV = ResourceData.saveValueFromJson(readFromString[Json]("""{"class":"java.lang.Long","value":9000000000}"""))
    assert(longV.isInstanceOf[java.lang.Long], s"got: $longV")
    assertEquals(longV.asInstanceOf[java.lang.Long].longValue, 9000000000L)

    val floatV = ResourceData.saveValueFromJson(readFromString[Json]("""{"class":"java.lang.Float","value":1.5}"""))
    assert(floatV.isInstanceOf[java.lang.Float], s"got: $floatV")
    assertEquals(floatV.asInstanceOf[java.lang.Float].floatValue, 1.5f)

    val doubleV = ResourceData.saveValueFromJson(readFromString[Json]("""{"class":"java.lang.Double","value":2.25}"""))
    assert(doubleV.isInstanceOf[java.lang.Double], s"got: $doubleV")
    assertEquals(doubleV.asInstanceOf[java.lang.Double].doubleValue, 2.25)

    val boolV = ResourceData.saveValueFromJson(readFromString[Json]("""{"class":"java.lang.Boolean","value":true}"""))
    assert(boolV.isInstanceOf[java.lang.Boolean], s"got: $boolV")
    assertEquals(boolV.asInstanceOf[java.lang.Boolean].booleanValue, true)
  }
}
