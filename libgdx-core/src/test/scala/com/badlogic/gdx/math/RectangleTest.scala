package com.badlogic.gdx.math

class RectangleTest extends balticporter.runtime.PortedSuite {
  testCase("testToString", {
    balticporter.runtime.Asserts.assertEquals("[5.0,-4.1,0.03,-0.02]", new com.badlogic.gdx.math.Rectangle(5.0f, -4.1f, 0.03f, -0.02f).toString())
  })
  testCase("testFromString", {
    balticporter.runtime.Asserts.assertEquals(new com.badlogic.gdx.math.Rectangle(5.0f, -4.1f, 0.03f, -0.02f), new com.badlogic.gdx.math.Rectangle().fromString("[5.0,-4.1,0.03,-0.02]"))
  })
}