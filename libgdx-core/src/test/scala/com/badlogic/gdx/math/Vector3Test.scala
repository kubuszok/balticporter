package com.badlogic.gdx.math

class Vector3Test {
  @org.junit.Test
  def testToString(): scala.Unit = {
    org.junit.Assert.assertEquals("(-5.0,42.00055,44444.32)", new com.badlogic.gdx.math.Vector3(-5.0f, 42.00055f, 44444.32f).toString())
  }
  @org.junit.Test
  def testFromString(): scala.Unit = {
    org.junit.Assert.assertEquals(new com.badlogic.gdx.math.Vector3(-5.0f, 42.00055f, 44444.32f), new com.badlogic.gdx.math.Vector3().fromString("(-5,42.00055,44444.32)"))
  }
}