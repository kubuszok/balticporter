package balticporter.frontend.ts

class VitestToMunitEmitterSpec extends munit.FunSuite:

  private def loadRast(resource: String): RastFile =
    val stream = getClass.getResourceAsStream(resource)
    assert(stream != null, s"Resource not found: $resource")
    val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
    stream.close()
    Rast.readFile(json)

  private val defaultConfig = dedicated.VitestToMunitEmitter.EmitConfig(
    packageName = "test.generated",
    className = "GeneratedSuite",
  )

  // -----------------------------------------------------------------------
  // info.spec: 4 it blocks, no nesting, resolves.not.toThrow, rejects.toThrow
  // -----------------------------------------------------------------------

  test("info spec: emits 4 tests"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/info.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assertEquals(result.testCount, 4, s"Expected 4 tests, got ${result.testCount}")

  test("info spec: contains class header"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/info.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("class GeneratedSuite extends munit.FunSuite"),
      "should contain class definition")
    assert(result.scala.contains("package test.generated"),
      "should contain package declaration")

  test("info spec: test names include describe prefix"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/info.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("info > should handle an info definition"),
      s"should contain prefixed test name, got:\n${result.scala}")

  test("info spec: resolves.not.toThrow becomes call without assertion"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/info.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    // The pattern: await expect(parser.parse(str)).resolves.not.toThrow()
    // should NOT become intercept, should just call the expression
    assert(result.scala.contains("parser.parse(str)"),
      s"should contain parser.parse(str), got:\n${result.scala}")

  test("info spec: rejects.toThrow becomes intercept"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/info.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("intercept[Exception]"),
      s"should contain intercept, got:\n${result.scala}")

  // -----------------------------------------------------------------------
  // pie.spec: nested describe, beforeEach, toBe, toBeTruthy, toStrictEqual,
  //           it.todo, rejects.toThrowError, toEqual
  // -----------------------------------------------------------------------

  test("pie spec: emits correct test count"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    // pie has: 11 parse tests + 3 config tests = 14 total
    assert(result.testCount >= 13 && result.testCount <= 15,
      s"Expected ~14 tests, got ${result.testCount}")

  test("pie spec: nested describe names"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("pie > parse > should handle very simple pie"),
      s"should have nested describe in name, got:\n${result.scala}")
    assert(result.scala.contains("pie > config > getConfig"),
      s"should have config describe in name, got:\n${result.scala}")

  test("pie spec: it.todo becomes .ignore"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains(".ignore)"),
      s"should contain .ignore for it.todo, got:\n${result.scala}")
    assert(result.ignoredCount >= 2,
      s"Expected >= 2 ignored tests, got ${result.ignoredCount}")

  test("pie spec: beforeEach hoisted"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    // beforeEach(() => db.clear()) should appear in test bodies
    assert(result.scala.contains("db.clear()"),
      s"should contain db.clear() from beforeEach, got:\n${result.scala}")

  test("pie spec: toBe emits assertEquals"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("assertEquals("),
      s"should contain assertEquals, got:\n${result.scala}")

  test("pie spec: toBeTruthy emits assert"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    // expect(db.getShowData()).toBeTruthy()
    assert(result.scala.contains("assert(db.getShowData())"),
      s"should contain assert for toBeTruthy, got:\n${result.scala}")

  test("pie spec: toStrictEqual emits assertEquals"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("assertEquals(db.getConfig()"),
      s"should contain assertEquals for toStrictEqual, got:\n${result.scala}")

  // -----------------------------------------------------------------------
  // packet.spec: beforeEach, toMatchInlineSnapshot,
  //              toThrowErrorMatchingInlineSnapshot
  // -----------------------------------------------------------------------

  test("packet spec: emits correct test count"):
    val rast = loadRast("/rast/mermaid/src/diagrams/packet/packet.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assertEquals(result.testCount, 9, s"Expected 9 tests, got ${result.testCount}")

  test("packet spec: toMatchInlineSnapshot emits assertEquals.toString"):
    val rast = loadRast("/rast/mermaid/src/diagrams/packet/packet.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains(".toString"),
      s"should contain .toString for snapshot, got:\n${result.scala}")

  test("packet spec: toThrowErrorMatchingInlineSnapshot emits intercept"):
    val rast = loadRast("/rast/mermaid/src/diagrams/packet/packet.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("intercept[Exception]"),
      s"should contain intercept, got:\n${result.scala}")
    assert(result.scala.contains("getMessage.contains"),
      s"should contain getMessage.contains, got:\n${result.scala}")

  test("packet spec: beforeEach with clear()"):
    val rast = loadRast("/rast/mermaid/src/diagrams/packet/packet.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.scala.contains("clear()"),
      s"should contain clear() from beforeEach, got:\n${result.scala}")

  // -----------------------------------------------------------------------
  // Assertion counting
  // -----------------------------------------------------------------------

  test("assertion counts are recorded"):
    val rast = loadRast("/rast/mermaid/src/diagrams/pie/pie.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    assert(result.assertionCounts.nonEmpty,
      s"should have assertion counts recorded")
    // pie has many assertEquals from toBe
    assert(result.assertionCounts.getOrElse("assertEquals", 0) > 0,
      s"should have assertEquals count > 0, got ${result.assertionCounts}")

  // -----------------------------------------------------------------------
  // Diagnostic: print emitted output for manual inspection
  // -----------------------------------------------------------------------

  test("info spec: print emitted output"):
    val rast = loadRast("/rast/mermaid/src/diagrams/info/info.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    println("=== info.spec.ts -> MUnit ===")
    println(result.scala)
    println(s"Tests: ${result.testCount}, Ignored: ${result.ignoredCount}")
    println(s"Assertions: ${result.assertionCounts}")

  test("packet spec: print emitted output"):
    val rast = loadRast("/rast/mermaid/src/diagrams/packet/packet.spec.rast.json")
    val result = dedicated.VitestToMunitEmitter.emit(rast, defaultConfig)
    println("=== packet.spec.ts -> MUnit ===")
    println(result.scala)
    println(s"Tests: ${result.testCount}, Ignored: ${result.ignoredCount}")
    println(s"Assertions: ${result.assertionCounts}")
