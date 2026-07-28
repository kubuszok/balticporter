package com.badlogic.gdx.utils

class JsonTest extends munit.FunSuite {
  test("testFromJsonObject")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: com.badlogic.gdx.utils.JsonValue = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key\":\"value\"}")
    assertEquals(value.getString("key"), "value")
  })
  test("testFromJsonArray")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: com.badlogic.gdx.utils.Array[java.lang.String] = json.fromJson(null, "[\"value1\",\"value2\"]")
    assertEquals(value.get(0), "value1")
    assertEquals(value.get(1), "value2")
  })
  test("testCharFromNumber")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: scala.Char = json.fromJson(classOf[scala.Char], "90")
    assertEquals(value, 'Z')
  })
  test("testReuseReader")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    var value: com.badlogic.gdx.utils.JsonValue = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key\":\"value\"}")
    assertEquals(value.getString("key"), "value")
    value = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key2\":\"value2\"}")
    assertEquals(value.getString("key2"), "value2")
  })
}