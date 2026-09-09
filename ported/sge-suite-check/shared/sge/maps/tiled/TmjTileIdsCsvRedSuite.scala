/*
 * Copyright 2025-2026 Mateusz Kubuszok
 * Licensed under the Apache License, Version 2.0
 *
 * ADAPTED for the port: the port's BaseTmjMapLoader.getTileIds takes
 * (JsonValue, Int, Int) instead of sge's TmjLayerJson. Test constructs
 * JsonValue via JsonReader.parse instead of the sge JSON AST.
 *
 * Red tests for ISS-780 (BaseTmjMapLoader.getTileIds silently substitutes gid 0
 * for unparseable CSV tile-data values).
 */
package sge
package maps
package tiled

import scala.util.{ Failure, Success, Try }

class TmjTileIdsCsvRedSuite extends munit.FunSuite {

  private val reader = new sge.utils.JsonReader()

  private def assertLoud(description: String, dataJson: String): Unit = {
    val element = reader.parse(dataJson)
    Try(BaseTmjMapLoader.getTileIds(element, 2, 2)) match {
      case Success(ids) =>
        fail(
          s"getTileIds silently accepted $description; decoded ids = ${ids.toList} " +
            "(LibGDX JsonValue.asIntArray throws instead of substituting gid 0)"
        )
      case Failure(_) => () // any loud failure is acceptable
    }
  }

  test("ISS-780: a non-numeric string tile value must throw, not decode to gid 0") {
    assertLoud(
      "a non-numeric string element",
      """[1, "not-a-number", 3, 4]"""
    )
  }

  test("ISS-780: a null tile value must throw, not decode to gid 0") {
    assertLoud(
      "a null element",
      """[1, null, 3, 4]"""
    )
  }

  test("ISS-780: an object tile value must throw, not decode to gid 0") {
    assertLoud(
      "an object element",
      """[1, {"x": 0}, 3, 4]"""
    )
  }

  test("ISS-780 control: a well-formed CSV data array decodes to the exact gids") {
    val element = reader.parse("[1, 2, 3, 4]")
    val ids = BaseTmjMapLoader.getTileIds(element, 2, 2)
    assertEquals(ids.toList, List(1, 2, 3, 4))
  }
}
