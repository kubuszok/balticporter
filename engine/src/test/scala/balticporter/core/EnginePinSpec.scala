package balticporter.core

import java.nio.file.Files
import balticporter.transform.CollectionsTransform

/** A port declares the engine that produced it, and the declaration is checkable. */
class EnginePinSpec extends munit.FunSuite:

  test("the pin records the running engine and the runtime it delivered") {
    val pin = EnginePin.current(RuntimePlan.of(List(new CollectionsTransform)))
    assertEquals(pin.engineVersion, EngineInfo.version)
    assertEquals(pin.runtimeVersion, RuntimeArtifact.version)
    assertEquals(pin.runtimeMode, "dependency")
  }

  test("a port that needed no runtime says so") {
    val pin = EnginePin.current(RuntimePlan.none)
    assertEquals(pin.runtimeMode, "none")
    assertEquals(pin.runtimeVersion, "")
  }

  test("render/parse round-trips, and the rendering is timestamp-free") {
    val pin = EnginePin.current(RuntimePlan.of(List(new CollectionsTransform), RuntimeMode.Vendored))
    assertEquals(EnginePin.parse(pin.render), Some(pin))
    // byte-determinism: two renderings of the same pin are the same text
    assertEquals(pin.render, EnginePin.current(RuntimePlan.of(List(new CollectionsTransform), RuntimeMode.Vendored)).render)
  }

  test("check passes on a pin this engine wrote, and NAMES both versions when it does not") {
    val dir = Files.createTempDirectory("bp-pin")
    try
      assertEquals(EnginePin.check(dir), Right(None)) // no pin is not an error
      EnginePin.write(dir, EnginePin.current(RuntimePlan.none))
      assert(EnginePin.check(dir).isRight)

      EnginePin.write(dir, EnginePin.current(RuntimePlan.none).copy(engineVersion = "0.0.1-ancient"))
      val err = EnginePin.check(dir).swap.getOrElse(fail("a stale pin must be reported"))
      assert(err.contains("0.0.1-ancient"), clue(err))
      assert(err.contains(EngineInfo.version), clue(err))
    finally
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)
  }
