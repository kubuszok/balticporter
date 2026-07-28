package com.badlogic.gdx.math.collision

class CollisionTest extends balticporter.runtime.PortedSuite {
  testCase("testBoundingBox", {
    val b1: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox(com.badlogic.gdx.math.Vector3.Zero, new com.badlogic.gdx.math.Vector3(1, 1, 1))
    val b2: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox(new com.badlogic.gdx.math.Vector3(1, 1, 1), new com.badlogic.gdx.math.Vector3(2, 2, 2))
    balticporter.runtime.Asserts.assertTrue(b1.contains(com.badlogic.gdx.math.Vector3.Zero))
    balticporter.runtime.Asserts.assertTrue(b1.contains(b1))
    balticporter.runtime.Asserts.assertFalse(b1.contains(b2))
  })
  testCase("testOrientedBoundingBox", {
    val b1: com.badlogic.gdx.math.collision.OrientedBoundingBox = new com.badlogic.gdx.math.collision.OrientedBoundingBox(new com.badlogic.gdx.math.collision.BoundingBox(com.badlogic.gdx.math.Vector3.Zero, new com.badlogic.gdx.math.Vector3(1, 1, 1)))
    val b2: com.badlogic.gdx.math.collision.OrientedBoundingBox = new com.badlogic.gdx.math.collision.OrientedBoundingBox(new com.badlogic.gdx.math.collision.BoundingBox(new com.badlogic.gdx.math.Vector3(1, 1, 1), new com.badlogic.gdx.math.Vector3(2, 2, 2)))
    balticporter.runtime.Asserts.assertTrue(b1.contains(com.badlogic.gdx.math.Vector3.Zero))
    balticporter.runtime.Asserts.assertTrue(b1.contains(b1))
    balticporter.runtime.Asserts.assertFalse(b1.contains(b2))
  })
  testCase("testOrientedBoundingBoxCollision", {
    var b1: com.badlogic.gdx.math.collision.OrientedBoundingBox = new com.badlogic.gdx.math.collision.OrientedBoundingBox(new com.badlogic.gdx.math.collision.BoundingBox(com.badlogic.gdx.math.Vector3.Zero, new com.badlogic.gdx.math.Vector3(1, 1, 1)))
    var b2: com.badlogic.gdx.math.collision.OrientedBoundingBox = new com.badlogic.gdx.math.collision.OrientedBoundingBox(new com.badlogic.gdx.math.collision.BoundingBox(new com.badlogic.gdx.math.Vector3(1 + com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR, 1, 1), new com.badlogic.gdx.math.Vector3(2, 2, 2)))
    balticporter.runtime.Asserts.assertFalse(b1.intersects(b2))
    b1 = new com.badlogic.gdx.math.collision.OrientedBoundingBox(new com.badlogic.gdx.math.collision.BoundingBox(com.badlogic.gdx.math.Vector3.Zero, new com.badlogic.gdx.math.Vector3(1, 1, 1)))
    b2 = new com.badlogic.gdx.math.collision.OrientedBoundingBox(new com.badlogic.gdx.math.collision.BoundingBox(new com.badlogic.gdx.math.Vector3(0.5f, 0.5f, 0.5f), new com.badlogic.gdx.math.Vector3(2, 2, 2)))
    balticporter.runtime.Asserts.assertTrue(b1.intersects(b2))
  })
}