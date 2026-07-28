package com.badlogic.gdx.math

class Vector3Test extends balticporter.runtime.PortedSuite {
  testCase("testToString", {
    balticporter.runtime.Asserts.assertEquals("(-5.0,42.00055,44444.32)", new com.badlogic.gdx.math.Vector3(-5.0f, 42.00055f, 44444.32f).toString())
  })
  testCase("testFromString", {
    balticporter.runtime.Asserts.assertEquals(new com.badlogic.gdx.math.Vector3(-5.0f, 42.00055f, 44444.32f), new com.badlogic.gdx.math.Vector3().fromString("(-5,42.00055,44444.32)"))
  })
}