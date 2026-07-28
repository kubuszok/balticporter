package com.badlogic.gdx.utils

class JsonTest {
  @org.junit.Test
  def testFromJsonObject(): scala.Unit = {
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: com.badlogic.gdx.utils.JsonValue = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key\":\"value\"}")
    org.junit.Assert.assertEquals("value", value.getString("key"))
  }
  @org.junit.Test
  def testFromJsonArray(): scala.Unit = {
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: com.badlogic.gdx.utils.Array[java.lang.String] = json.fromJson(null, "[\"value1\",\"value2\"]")
    org.junit.Assert.assertEquals("value1", value.get(0))
    org.junit.Assert.assertEquals("value2", value.get(1))
  }
  @org.junit.Test
  def testCharFromNumber(): scala.Unit = {
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    val value: scala.Char = json.fromJson(classOf[scala.Char], "90")
    org.junit.Assert.assertEquals('Z', value)
  }
  @org.junit.Test
  def testReuseReader(): scala.Unit = {
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    var value: com.badlogic.gdx.utils.JsonValue = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key\":\"value\"}")
    org.junit.Assert.assertEquals("value", value.getString("key"))
    value = json.fromJson(null, classOf[com.badlogic.gdx.utils.JsonValue], "{\"key2\":\"value2\"}")
    org.junit.Assert.assertEquals("value2", value.getString("key2"))
  }
}