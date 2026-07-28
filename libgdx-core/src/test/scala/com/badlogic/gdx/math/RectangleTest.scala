package com.badlogic.gdx.math

class RectangleTest {
  @org.junit.Test
  def testToString(): scala.Unit = {
    org.junit.Assert.assertEquals("[5.0,-4.1,0.03,-0.02]", new com.badlogic.gdx.math.Rectangle(5.0f, -4.1f, 0.03f, -0.02f).toString())
  }
  @org.junit.Test
  def testFromString(): scala.Unit = {
    org.junit.Assert.assertEquals(new com.badlogic.gdx.math.Rectangle(5.0f, -4.1f, 0.03f, -0.02f), new com.badlogic.gdx.math.Rectangle().fromString("[5.0,-4.1,0.03,-0.02]"))
  }
}