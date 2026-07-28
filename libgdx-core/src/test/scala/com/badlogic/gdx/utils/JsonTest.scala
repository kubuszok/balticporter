package com.badlogic.gdx.utils

class JsonTest extends munit.FunSuite {
  test("testFromJsonObject")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: com.badlogic.gdx.utils.JsonValue = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key\":\"value\"}")
    balticporter.runtime.Asserts.assertEquals("value", value.getString("key"))
  })
  test("testFromJsonArray")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: com.badlogic.gdx.utils.Array[java.lang.String] = json.fromJson(null, "[\"value1\",\"value2\"]")
    balticporter.runtime.Asserts.assertEquals("value1", value.get(0))
    balticporter.runtime.Asserts.assertEquals("value2", value.get(1))
  })
  test("testCharFromNumber")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: scala.Char = json.fromJson(classOf[scala.Char], "90")
    balticporter.runtime.Asserts.assertEquals('Z', value)
  })
  test("testReuseReader")({
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    var value: com.badlogic.gdx.utils.JsonValue = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key\":\"value\"}")
    balticporter.runtime.Asserts.assertEquals("value", value.getString("key"))
    value = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key2\":\"value2\"}")
    balticporter.runtime.Asserts.assertEquals("value2", value.getString("key2"))
  })
}