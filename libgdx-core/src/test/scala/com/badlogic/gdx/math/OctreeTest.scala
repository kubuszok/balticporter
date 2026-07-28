package com.badlogic.gdx.math

class OctreeTest {
  def testInsert(): scala.Unit = {
    val maxDepth: scala.Int = 2
    val maxItemsPerNode: scala.Int = 1
    val min: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(-5.0f, -5.0f, -5.0f)
    val max: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(5.0f, 5.0f, 5.0f)
    val octree: com.badlogic.gdx.math.Octree[com.badlogic.gdx.math.collision.BoundingBox] = new com.badlogic.gdx.math.Octree[com.badlogic.gdx.math.collision.BoundingBox](min, max, maxDepth, maxItemsPerNode, new com.badlogic.gdx.math.Octree.Collider[com.badlogic.gdx.math.collision.BoundingBox]() {
      override def intersects(nodeBounds: com.badlogic.gdx.math.collision.BoundingBox, geometry: com.badlogic.gdx.math.collision.BoundingBox): scala.Boolean = {
        return nodeBounds.intersects(geometry)
      }
      override def intersects(frustum: com.badlogic.gdx.math.Frustum, geometry: com.badlogic.gdx.math.collision.BoundingBox): scala.Boolean = {
        return false
      }
      final val tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
      override def intersects(ray: com.badlogic.gdx.math.collision.Ray, geometry: com.badlogic.gdx.math.collision.BoundingBox): scala.Float = {
        if (!com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, geometry, tmp)) {
          return tmp.dst2(ray.origin)
        } else ()
        return java.lang.Float.POSITIVE_INFINITY
      }
    })
    org.junit.Assert.assertTrue(octree.root.isLeaf())
    val box1: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox(new com.badlogic.gdx.math.Vector3(0, 0, 0), new com.badlogic.gdx.math.Vector3(1, 1, 1))
    octree.add(box1)
    val box2: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox(new com.badlogic.gdx.math.Vector3(2, 2, 2), new com.badlogic.gdx.math.Vector3(3, 3, 3))
    octree.add(box2)
    org.junit.Assert.assertFalse(octree.root.isLeaf())
    val result: com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.math.collision.BoundingBox] = new com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.math.collision.BoundingBox]()
    octree.getAll(result)
    org.junit.Assert.assertTrue(result.contains(box1))
    org.junit.Assert.assertTrue(result.contains(box2))
    org.junit.Assert.assertEquals(2, result.size)
    octree.remove(box2)
    result.clear()
    octree.getAll(result)
    org.junit.Assert.assertEquals(1, result.size)
    org.junit.Assert.assertTrue(result.contains(box1))
  }
}