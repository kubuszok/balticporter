package balticporter.frontend.ts

class CoverageLaneSpec extends munit.FunSuite:

  private def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.length <= 1 then s
    else parts.head + parts.tail.map(_.capitalize).mkString

  private val sampleBodies = List(
    ParityDerive.BodyEntry("methodA", "rast", "", 0),
    ParityDerive.BodyEntry("methodB", "rast", "", 0),
    ParityDerive.BodyEntry("methodC", "reference", "no-rast-symbol", 0),
    ParityDerive.BodyEntry("methodD", "reference", "uncompilable-pattern:stringTemplate(", 0),
    ParityDerive.BodyEntry("methodE", "reference", "", 0),
    ParityDerive.BodyEntry("methodF", "rast", "", 2)
  )

  test("analyze: correct counts"):
    val report = CoverageLane.analyze(sampleBodies, "TestModule")
    assertEquals(report.moduleName, "TestModule")
    assertEquals(report.totalMethods, 6)
    assertEquals(report.rastDerived, 3)
    assertEquals(report.referenceDerived, 3)
    assert(report.rastPercent > 49.0 && report.rastPercent < 51.0, s"Expected ~50%, got ${report.rastPercent}")

  test("analyze: byReason breakdown"):
    val report = CoverageLane.analyze(sampleBodies, "TestModule")
    assertEquals(report.byReason("no-rast-symbol"), 1)
    assertEquals(report.byReason("uncompilable-pattern:stringTemplate("), 1)
    assertEquals(report.byReason("unclassified"), 1)

  test("analyze: empty bodies"):
    val report = CoverageLane.analyze(Nil, "Empty")
    assertEquals(report.totalMethods, 0)
    assertEquals(report.rastDerived, 0)
    assertEquals(report.rastPercent, 0.0)

  test("formatReport: table output"):
    val reports = List(
      CoverageLane.analyze(sampleBodies, "ModuleA"),
      CoverageLane.analyze(sampleBodies.take(3), "ModuleB")
    )
    val output = CoverageLane.formatReport(reports)
    assert(output.contains("ModuleA"), "should contain ModuleA")
    assert(output.contains("ModuleB"), "should contain ModuleB")
    assert(output.contains("TOTAL"), "should contain TOTAL")

  test("checkRegressions: detects rast→reference flip"):
    val baseline = List(
      ParityDerive.BodyEntry("methodA", "rast", "", 0),
      ParityDerive.BodyEntry("methodB", "rast", "", 0),
      ParityDerive.BodyEntry("methodC", "reference", "no-rast-symbol", 0)
    )
    val current = List(
      ParityDerive.BodyEntry("methodA", "rast", "", 0),
      ParityDerive.BodyEntry("methodB", "reference", "uncompilable-pattern:x", 0),
      ParityDerive.BodyEntry("methodC", "reference", "no-rast-symbol", 0)
    )
    val regressions = CoverageLane.checkRegressions(baseline, current)
    assertEquals(regressions, List("methodB"))

  test("checkRegressions: no regression when stable"):
    val bodies      = sampleBodies
    val regressions = CoverageLane.checkRegressions(bodies, bodies)
    assertEquals(regressions, Nil)

  test("formatBodiesTsv: roundtrip"):
    val tsv = CoverageLane.formatBodiesTsv(sampleBodies)
    assert(tsv.startsWith("method_name\tsource\twhy\trefusal_count"), "should start with header")
    val parsed = CoverageLane.parseBodiesTsv(tsv)
    assertEquals(parsed.size, sampleBodies.size)
    assertEquals(parsed.head.methodName, "methodA")
    assertEquals(parsed.head.source, "rast")

  // -----------------------------------------------------------------------
  // BodyVerdicts
  // -----------------------------------------------------------------------

  test("autoClassify: classifies reference bodies"):
    val verdicts = BodyVerdicts.autoClassify(sampleBodies)
    assertEquals(verdicts.size, 3, "should classify 3 reference bodies")
    val byCategory = BodyVerdicts.countByCategory(verdicts)
    assertEquals(byCategory("structural-mismatch"), 1)
    assertEquals(byCategory("uncompilable-pattern"), 1)
    assertEquals(byCategory("unclassified"), 1)

  test("autoClassify: status classification"):
    val verdicts = BodyVerdicts.autoClassify(sampleBodies)
    val byStatus = BodyVerdicts.countByStatus(verdicts)
    assertEquals(byStatus("structural"), 1)
    assertEquals(byStatus("justified"), 1)
    assertEquals(byStatus("unjustified"), 1)

  test("formatVerdictsTsv: roundtrip"):
    val verdicts = BodyVerdicts.autoClassify(sampleBodies)
    val tsv      = BodyVerdicts.formatVerdictsTsv(verdicts)
    assert(tsv.startsWith("member\tstatus\tevidence\tcategory"))
    val parsed = BodyVerdicts.parseVerdictsTsv(tsv)
    assertEquals(parsed.size, verdicts.size)

  test("formatSummary: produces readable output"):
    val verdicts = BodyVerdicts.autoClassify(sampleBodies)
    val summary  = BodyVerdicts.formatSummary(verdicts)
    assert(summary.contains("Body verdicts: 3"))
    assert(summary.contains("structural"))
    assert(summary.contains("justified"))

  // -----------------------------------------------------------------------
  // Integration: run coverage on a real parity-derive
  // -----------------------------------------------------------------------

  private def loadReference(resource: String): Option[String] =
    val stream = getClass.getResourceAsStream(resource)
    if stream == null then None
    else
      try
        val content = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        stream.close()
        Some(content)
      catch
        case _: Exception =>
          stream.close()
          None

  private def loadRast(resource: String): Option[RastFile] =
    val stream = getClass.getResourceAsStream(resource)
    if stream == null then None
    else
      try
        val json = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        stream.close()
        Some(Rast.readFile(json))
      catch
        case _: Exception =>
          try stream.close()
          catch { case _: Exception => () }
          None

  test("integration: coverage report for Terser Common"):
    val refOpt  = loadReference("/reference/terser/compress/Common.scala")
    val rastOpt = loadRast("/rast/terser/lib/compress/common.rast.json")
    (refOpt, rastOpt) match
      case (Some(ref), Some(rast)) =>
        val fns     = balticporter.corpus.terser.TerserEmitter.extractFreeFunctions(rast)
        val bodyMap = scala.collection.mutable.Map.empty[String, List[(String, Int)]]
        for fn <- fns do
          val entry      = dedicated.DefmethodEntry("_free_", fn.name, fn.params, fn.bodyNode)
          val translated = dedicated.DefmethodBodyTranslator.translateBody(entry, Nil, "    ")
          val key        = snakeToCamel(fn.name)
          bodyMap(key) = bodyMap.getOrElse(key, Nil) :+ (translated.scalaBody, translated.refusalCount)

        val result = ParityDerive.derive(ref, bodyMap.toMap)
        val report = CoverageLane.analyze(result.bodies, "Common")
        println(CoverageLane.formatDetailedReport(report))

        val verdicts = BodyVerdicts.autoClassify(result.bodies)
        println(BodyVerdicts.formatSummary(verdicts))

        assert(report.totalMethods >= 10, s"Expected >= 10 methods, got ${report.totalMethods}")
        assert(report.rastDerived >= 5, s"Expected >= 5 RAST, got ${report.rastDerived}")

      case _ =>
        println("SKIP: reference or RAST not found")
