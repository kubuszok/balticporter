package com.badlogic.gdx.math

class Octree[T](minimum: com.badlogic.gdx.math.Vector3, maximum: com.badlogic.gdx.math.Vector3, maxDepth: scala.Int, maxItemsPerNode$p: scala.Int, collider$p: com.badlogic.gdx.math.Octree.Collider[T]) {
  var maxItemsPerNode: scala.Int = 0
  final val nodePool: com.badlogic.gdx.utils.Pool[OctreeNode] = new com.badlogic.gdx.utils.Pool[OctreeNode]() {
    @java.lang.Override
    override def newObject(): OctreeNode = {
      return new OctreeNode()
    }
  }
  var root: OctreeNode = null.asInstanceOf[OctreeNode]
  var collider: com.badlogic.gdx.math.Octree.Collider[T] = null.asInstanceOf[com.badlogic.gdx.math.Octree.Collider[T]]
  val realMin: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(java.lang.Math.min(minimum.x, maximum.x), java.lang.Math.min(minimum.y, maximum.y), java.lang.Math.min(minimum.z, maximum.z))
  val realMax: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(java.lang.Math.max(minimum.x, maximum.x), java.lang.Math.max(minimum.y, maximum.y), java.lang.Math.max(minimum.z, maximum.z))
  this.root = this.createNode(realMin, realMax, maxDepth)
  this.collider = collider$p
  this.maxItemsPerNode = maxItemsPerNode$p
  def createNode(min: com.badlogic.gdx.math.Vector3, max: com.badlogic.gdx.math.Vector3, level: scala.Int): OctreeNode = {
    val node: OctreeNode = this.nodePool.obtain()
    node.bounds.set(min, max)
    node.level = level
    node.leaf = true
    return node
  }
  def add(`object`: T): scala.Unit = {
    this.root.add(`object`)
  }
  def remove(`object`: T): scala.Unit = {
    this.root.remove(`object`)
  }
  def update(`object`: T): scala.Unit = {
    this.root.remove(`object`)
    this.root.add(`object`)
  }
  def getAll(resultSet: com.badlogic.gdx.utils.ObjectSet[T]): com.badlogic.gdx.utils.ObjectSet[T] = {
    this.root.getAll(resultSet)
    return resultSet
  }
  def query(aabb: com.badlogic.gdx.math.collision.BoundingBox, result: com.badlogic.gdx.utils.ObjectSet[T]): com.badlogic.gdx.utils.ObjectSet[T] = {
    this.root.query(aabb, result)
    return result
  }
  def query(frustum: com.badlogic.gdx.math.Frustum, result: com.badlogic.gdx.utils.ObjectSet[T]): com.badlogic.gdx.utils.ObjectSet[T] = {
    this.root.query(frustum, result)
    return result
  }
  def rayCast(ray: com.badlogic.gdx.math.collision.Ray, result: com.badlogic.gdx.math.Octree.RayCastResult[T]): T = {
    result.distance = result.maxDistanceSq
    this.root.rayCast(ray, result)
    return result.geometry
  }
  def getNodesBoxes(boxes: com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.math.collision.BoundingBox]): com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.math.collision.BoundingBox] = {
    this.root.getBoundingBox(boxes)
    return boxes
  }
  class OctreeNode {
    var level: scala.Int = 0
    final val bounds: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox()
    var leaf: scala.Boolean = false
    private var children: scala.Array[OctreeNode] = null.asInstanceOf[scala.Array[OctreeNode]]
    private final val geometries: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array[T](java.lang.Math.min(16, Octree.this.maxItemsPerNode))
    private def split(): scala.Unit = {
      val midx: scala.Float = (this.bounds.max$field.x + this.bounds.min$field.x) * 0.5f
      val midy: scala.Float = (this.bounds.max$field.y + this.bounds.min$field.y) * 0.5f
      val midz: scala.Float = (this.bounds.max$field.z + this.bounds.min$field.z) * 0.5f
      val deeperLevel: scala.Int = this.level - 1
      this.leaf = false
      if (this.children == null) {
        this.children = new scala.Array[OctreeNode](8)
      } else ()
      this.children(0) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(this.bounds.min$field.x, midy, midz), new com.badlogic.gdx.math.Vector3(midx, this.bounds.max$field.y, this.bounds.max$field.z), deeperLevel)
      this.children(1) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(midx, midy, midz), new com.badlogic.gdx.math.Vector3(this.bounds.max$field.x, this.bounds.max$field.y, this.bounds.max$field.z), deeperLevel)
      this.children(2) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(midx, midy, this.bounds.min$field.z), new com.badlogic.gdx.math.Vector3(this.bounds.max$field.x, this.bounds.max$field.y, midz), deeperLevel)
      this.children(3) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(this.bounds.min$field.x, midy, this.bounds.min$field.z), new com.badlogic.gdx.math.Vector3(midx, this.bounds.max$field.y, midz), deeperLevel)
      this.children(4) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(this.bounds.min$field.x, this.bounds.min$field.y, midz), new com.badlogic.gdx.math.Vector3(midx, midy, this.bounds.max$field.z), deeperLevel)
      this.children(5) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(midx, this.bounds.min$field.y, midz), new com.badlogic.gdx.math.Vector3(this.bounds.max$field.x, midy, this.bounds.max$field.z), deeperLevel)
      this.children(6) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(midx, this.bounds.min$field.y, this.bounds.min$field.z), new com.badlogic.gdx.math.Vector3(this.bounds.max$field.x, midy, midz), deeperLevel)
      this.children(7) = Octree.this.createNode(new com.badlogic.gdx.math.Vector3(this.bounds.min$field.x, this.bounds.min$field.y, this.bounds.min$field.z), new com.badlogic.gdx.math.Vector3(midx, midy, midz), deeperLevel)
      for (child <- this.children) {
        for (geometry <- this.geometries) {
          child.add(geometry)
        }
      }
      this.geometries.clear()
    }
    private def merge(): scala.Unit = {
      this.clearChildren()
      this.leaf = true
    }
    private def free(): scala.Unit = {
      this.geometries.clear()
      if (!this.leaf) {
        this.clearChildren()
      } else ()
      Octree.this.nodePool.free(this)
    }
    private def clearChildren(): scala.Unit = {
      { var i: scala.Int = 0; while (i < 8) { {
        this.children(i).free()
        this.children(i) = null
      }; i = i + 1 } }
    }
    def add(geometry: T): scala.Unit = {
      if (!Octree.this.collider.intersects(this.bounds, geometry)) {
        return
      } else ()
      if (!this.leaf) {
        for (child <- this.children) {
          child.add(geometry)
        }
      } else {
        if ((this.geometries.size >= Octree.this.maxItemsPerNode) && (this.level > 0)) {
          this.split()
          for (child <- this.children) {
            child.add(geometry)
          }
        } else {
          this.geometries.add(geometry)
        }
      }
    }
    def remove(`object`: T): scala.Boolean = {
      if (!this.leaf) {
        var removed: scala.Boolean = false
        for (node <- this.children) {
          removed = removed | node.remove(`object`)
        }
        if (removed) {
          val geometrySet: com.badlogic.gdx.utils.ObjectSet[T] = new com.badlogic.gdx.utils.ObjectSet[T]()
          for (node <- this.children) {
            node.getAll(geometrySet)
          }
          if (geometrySet.size <= Octree.this.maxItemsPerNode) {
            for (geometry <- geometrySet) {
              this.geometries.add(geometry)
            }
            this.merge()
          } else ()
        } else ()
        return removed
      } else ()
      return this.geometries.removeValue(`object`, true)
    }
    def isLeaf(): scala.Boolean = {
      return this.leaf
    }
    def query(aabb: com.badlogic.gdx.math.collision.BoundingBox, result: com.badlogic.gdx.utils.ObjectSet[T]): scala.Unit = {
      if (!aabb.intersects(this.bounds)) {
        return
      } else ()
      if (!this.leaf) {
        for (node <- this.children) {
          node.query(aabb, result)
        }
      } else {
        for (geometry <- this.geometries) {
          if (Octree.this.collider.intersects(aabb, geometry)) {
            result.add(geometry)
          } else ()
        }
      }
    }
    def query(frustum: com.badlogic.gdx.math.Frustum, result: com.badlogic.gdx.utils.ObjectSet[T]): scala.Unit = {
      if (!com.badlogic.gdx.math.Intersector.intersectFrustumBounds(frustum, this.bounds)) {
        return
      } else ()
      if (!this.leaf) {
        for (node <- this.children) {
          node.query(frustum, result)
        }
      } else {
        for (geometry <- this.geometries) {
          if (Octree.this.collider.intersects(frustum, geometry)) {
            result.add(geometry)
          } else ()
        }
      }
    }
    def rayCast(ray: com.badlogic.gdx.math.collision.Ray, result: com.badlogic.gdx.math.Octree.RayCastResult[T]): scala.Unit = {
      val intersect: scala.Boolean = com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, this.bounds, Octree.tmp)
      if (!intersect) {
        return
      } else {
        val dst2: scala.Float = Octree.tmp.dst2(ray.origin)
        if (dst2 >= result.maxDistanceSq) {
          return
        } else ()
      }
      if (!this.leaf) {
        for (child <- this.children) {
          child.rayCast(ray, result)
        }
      } else {
        for (geometry <- this.geometries) {
          var distance: scala.Float = Octree.this.collider.intersects(ray, geometry)
          if ((result.geometry == null) || (distance < result.distance)) {
            result.geometry = geometry
            result.distance = distance
          } else ()
        }
      }
    }
    def getAll(resultSet: com.badlogic.gdx.utils.ObjectSet[T]): scala.Unit = {
      if (!this.leaf) {
        for (child <- this.children) {
          child.getAll(resultSet)
        }
      } else ()
      resultSet.addAll(this.geometries.asInstanceOf[com.badlogic.gdx.utils.Array[? <: T]])
    }
    def getBoundingBox(bounds: com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.math.collision.BoundingBox]): scala.Unit = {
      if (!this.leaf) {
        for (node <- this.children) {
          node.getBoundingBox(bounds)
        }
      } else ()
      bounds.add(this.bounds)
    }
  }
}
object Octree {
  final val tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  trait Collider[T] {
    def intersects(nodeBounds: com.badlogic.gdx.math.collision.BoundingBox, geometry: T): scala.Boolean
    def intersects(frustum: com.badlogic.gdx.math.Frustum, geometry: T): scala.Boolean
    def intersects(ray: com.badlogic.gdx.math.collision.Ray, geometry: T): scala.Float
  }
  class RayCastResult[T] {
    var geometry: T = null.asInstanceOf[T]
    var distance: scala.Float = 0.0f
    var maxDistanceSq: scala.Float = java.lang.Float.MAX_VALUE
  }
}