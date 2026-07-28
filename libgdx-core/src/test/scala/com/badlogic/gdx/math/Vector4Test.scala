package com.badlogic.gdx.math

class Vector4Test {
  @org.junit.Test
  def testToString(): scala.Unit = {
    org.junit.Assert.assertEquals("(-5.0,42.00055,44444.32,-1.975)", new com.badlogic.gdx.math.Vector4(-5.0f, 42.00055f, 44444.32f, -1.975f).toString())
  }
  @org.junit.Test
  def testFromString(): scala.Unit = {
    org.junit.Assert.assertEquals(new com.badlogic.gdx.math.Vector4(-5.0f, 42.00055f, 44444.32f, -1.975f), new com.badlogic.gdx.math.Vector4().fromString("(-5,42.00055,44444.32,-1.9750)"))
  }
}