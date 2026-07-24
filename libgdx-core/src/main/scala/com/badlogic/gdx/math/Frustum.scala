package com.badlogic.gdx.math

class Frustum {
  final val planes: scala.Array[com.badlogic.gdx.math.Plane] = new Array[com.badlogic.gdx.math.Plane](6)
  final val planePoints: scala.Array[com.badlogic.gdx.math.Vector3] = Array[com.badlogic.gdx.math.Vector3](new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3(), new com.badlogic.gdx.math.Vector3())
  protected final val planePointsArray: scala.Array[scala.Float] = new Array[scala.Float](8 * 3)
  def this() = {
    this()
    { var i: scala.Int = 0; while (i < 6) { {
      this.planes(i) = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(), 0)
    }; i = i + 1 } }
  }
  def update(inverseProjectionView: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    java.lang.System.arraycopy(Frustum.clipSpacePlanePointsArray, 0, this.planePointsArray, 0, Frustum.clipSpacePlanePointsArray.length)
    com.badlogic.gdx.math.Matrix4.prj(inverseProjectionView.`val`, this.planePointsArray, 0, 8, 3)
    { var i: scala.Int = 0; var j: scala.Int = 0; while (i < 8) { {
      val v: com.badlogic.gdx.math.Vector3 = this.planePoints(i)
      v.x = this.planePointsArray({ j += 1; j })
      v.y = this.planePointsArray({ j += 1; j })
      v.z = this.planePointsArray({ j += 1; j })
    }; i = i + 1 } }
    this.planes(0).set(this.planePoints(1), this.planePoints(0), this.planePoints(2))
    this.planes(1).set(this.planePoints(4), this.planePoints(5), this.planePoints(7))
    this.planes(2).set(this.planePoints(0), this.planePoints(4), this.planePoints(3))
    this.planes(3).set(this.planePoints(5), this.planePoints(1), this.planePoints(6))
    this.planes(4).set(this.planePoints(2), this.planePoints(3), this.planePoints(6))
    this.planes(5).set(this.planePoints(4), this.planePoints(0), this.planePoints(1))
  }
  def pointInFrustum(point: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    { var i: scala.Int = 0; while (i < this.planes.length) { {
      val result: com.badlogic.gdx.math.Plane#PlaneSide = this.planes(i).testPoint(point)
      if (result == com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def pointInFrustum(x: scala.Float, y: scala.Float, z: scala.Float): scala.Boolean = {
    { var i: scala.Int = 0; while (i < this.planes.length) { {
      val result: com.badlogic.gdx.math.Plane#PlaneSide = this.planes(i).testPoint(x, y, z)
      if (result == com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def sphereInFrustum(center: com.badlogic.gdx.math.Vector3, radius: scala.Float): scala.Boolean = {
    { var i: scala.Int = 0; while (i < 6) { {
      if ((((this.planes(i).normal.x * center.x) + (this.planes(i).normal.y * center.y)) + (this.planes(i).normal.z * center.z)) < ((-radius) - this.planes(i).d)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def sphereInFrustum(x: scala.Float, y: scala.Float, z: scala.Float, radius: scala.Float): scala.Boolean = {
    { var i: scala.Int = 0; while (i < 6) { {
      if ((((this.planes(i).normal.x * x) + (this.planes(i).normal.y * y)) + (this.planes(i).normal.z * z)) < ((-radius) - this.planes(i).d)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def sphereInFrustumWithoutNearFar(center: com.badlogic.gdx.math.Vector3, radius: scala.Float): scala.Boolean = {
    { var i: scala.Int = 2; while (i < 6) { {
      if ((((this.planes(i).normal.x * center.x) + (this.planes(i).normal.y * center.y)) + (this.planes(i).normal.z * center.z)) < ((-radius) - this.planes(i).d)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def sphereInFrustumWithoutNearFar(x: scala.Float, y: scala.Float, z: scala.Float, radius: scala.Float): scala.Boolean = {
    { var i: scala.Int = 2; while (i < 6) { {
      if ((((this.planes(i).normal.x * x) + (this.planes(i).normal.y * y)) + (this.planes(i).normal.z * z)) < ((-radius) - this.planes(i).d)) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def boundsInFrustum(bounds: com.badlogic.gdx.math.collision.BoundingBox): scala.Boolean = {
    { var i: scala.Int = 0; val len2: scala.Int = this.planes.length; while (i < len2) { {
      if (this.planes(i).testPoint(bounds.getCorner000(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(bounds.getCorner001(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(bounds.getCorner010(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(bounds.getCorner011(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(bounds.getCorner100(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(bounds.getCorner101(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(bounds.getCorner110(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(bounds.getCorner111(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      return false
    }; i = i + 1 } }
    return true
  }
  def boundsInFrustum(center: com.badlogic.gdx.math.Vector3, dimensions: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    return this.boundsInFrustum(center.x, center.y, center.z, dimensions.x / 2, dimensions.y / 2, dimensions.z / 2)
  }
  def boundsInFrustum(x: scala.Float, y: scala.Float, z: scala.Float, halfWidth: scala.Float, halfHeight: scala.Float, halfDepth: scala.Float): scala.Boolean = {
    { var i: scala.Int = 0; val len2: scala.Int = this.planes.length; while (i < len2) { {
      if (this.planes(i).testPoint(x + halfWidth, y + halfHeight, z + halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(x + halfWidth, y + halfHeight, z - halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(x + halfWidth, y - halfHeight, z + halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(x + halfWidth, y - halfHeight, z - halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(x - halfWidth, y + halfHeight, z + halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(x - halfWidth, y + halfHeight, z - halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(x - halfWidth, y - halfHeight, z + halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(x - halfWidth, y - halfHeight, z - halfDepth) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      return false
    }; i = i + 1 } }
    return true
  }
  def boundsInFrustum(obb: com.badlogic.gdx.math.collision.OrientedBoundingBox): scala.Boolean = {
    { var i: scala.Int = 0; val len2: scala.Int = this.planes.length; while (i < len2) { {
      if (this.planes(i).testPoint(obb.getCorner000(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(obb.getCorner001(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(obb.getCorner010(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(obb.getCorner011(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(obb.getCorner100(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(obb.getCorner101(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(obb.getCorner110(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      if (this.planes(i).testPoint(obb.getCorner111(Frustum.tmpV)) != com.badlogic.gdx.math.Plane.PlaneSide.Back) {
        /* continue */ ()
      } else ()
      return false
    }; i = i + 1 } }
    return true
  }
}
object Frustum {
  protected final val clipSpacePlanePoints: scala.Array[com.badlogic.gdx.math.Vector3] = Array[com.badlogic.gdx.math.Vector3](new com.badlogic.gdx.math.Vector3(-1, -1, -1), new com.badlogic.gdx.math.Vector3(1, -1, -1), new com.badlogic.gdx.math.Vector3(1, 1, -1), new com.badlogic.gdx.math.Vector3(-1, 1, -1), new com.badlogic.gdx.math.Vector3(-1, -1, 1), new com.badlogic.gdx.math.Vector3(1, -1, 1), new com.badlogic.gdx.math.Vector3(1, 1, 1), new com.badlogic.gdx.math.Vector3(-1, 1, 1))
  protected final val clipSpacePlanePointsArray: scala.Array[scala.Float] = new Array[scala.Float](8 * 3)
  private final val tmpV: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
}