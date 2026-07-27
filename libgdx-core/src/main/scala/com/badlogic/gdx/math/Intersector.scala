package com.badlogic.gdx.math

object Intersector {
  private final val v0: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val v1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val v2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val floatArray: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray()
  private final val floatArray2: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray()
  private final val ip: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val ep1: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val ep2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val s: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val e: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var v2a: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var v2b: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var v2c: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var v2d: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val p: com.badlogic.gdx.math.Plane = new com.badlogic.gdx.math.Plane(new com.badlogic.gdx.math.Vector3(), 0)
  private final val i: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val dir: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val start: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var best: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var tmp1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var tmp2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var tmp3: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var intersection: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def isPointInTriangle(point: com.badlogic.gdx.math.Vector3, t1: com.badlogic.gdx.math.Vector3, t2: com.badlogic.gdx.math.Vector3, t3: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    Intersector.v0.set(t1).sub(point)
    Intersector.v1.set(t2).sub(point)
    Intersector.v2.set(t3).sub(point)
    Intersector.v1.crs(Intersector.v2)
    Intersector.v2.crs(Intersector.v0)
    if (Intersector.v1.dot(Intersector.v2) < 0.0f) {
      return false
    } else ()
    Intersector.v0.crs(Intersector.v2.set(t2).sub(point))
    return Intersector.v1.dot(Intersector.v0) >= 0.0f
  }
  def isPointInTriangle(p: com.badlogic.gdx.math.Vector2, a: com.badlogic.gdx.math.Vector2, b: com.badlogic.gdx.math.Vector2, c: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    return Intersector.isPointInTriangle(p.x, p.y, a.x, a.y, b.x, b.y, c.x, c.y)
  }
  def isPointInTriangle(px: scala.Float, py: scala.Float, ax: scala.Float, ay: scala.Float, bx: scala.Float, by: scala.Float, cx: scala.Float, cy: scala.Float): scala.Boolean = {
    val px1: scala.Float = px - ax
    val py1: scala.Float = py - ay
    val side12: scala.Boolean = (((bx - ax) * py1) - ((by - ay) * px1)) > 0
    if (((((cx - ax) * py1) - ((cy - ay) * px1)) > 0) == side12) {
      return false
    } else ()
    if (((((cx - bx) * (py - by)) - ((cy - by) * (px - bx))) > 0) != side12) {
      return false
    } else ()
    return true
  }
  def intersectSegmentPlane(start: com.badlogic.gdx.math.Vector3, `end`: com.badlogic.gdx.math.Vector3, plane: com.badlogic.gdx.math.Plane, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    val dir: com.badlogic.gdx.math.Vector3 = Intersector.v0.set(`end`).sub(start)
    val denom: scala.Float = dir.dot(plane.getNormal())
    if (denom == 0.0f) {
      return false
    } else ()
    val t: scala.Float = (-(start.dot(plane.getNormal()) + plane.getD())) / denom
    if ((t < 0) || (t > 1)) {
      return false
    } else ()
    intersection.set(start).add(dir.scl(t))
    return true
  }
  def pointLineSide(linePoint1: com.badlogic.gdx.math.Vector2, linePoint2: com.badlogic.gdx.math.Vector2, point: com.badlogic.gdx.math.Vector2): scala.Int = {
    return java.lang.Math.signum(((linePoint2.x - linePoint1.x) * (point.y - linePoint1.y)) - ((linePoint2.y - linePoint1.y) * (point.x - linePoint1.x))).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def pointLineSide(linePoint1X: scala.Float, linePoint1Y: scala.Float, linePoint2X: scala.Float, linePoint2Y: scala.Float, pointX: scala.Float, pointY: scala.Float): scala.Int = {
    return java.lang.Math.signum(((linePoint2X - linePoint1X) * (pointY - linePoint1Y)) - ((linePoint2Y - linePoint1Y) * (pointX - linePoint1X))).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def isPointInPolygon(polygon: com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.Vector2], point: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    var last: com.badlogic.gdx.math.Vector2 = polygon.peek()
    val x: scala.Float = point.x
    val y: scala.Float = point.y
    var oddNodes: scala.Boolean = false;
    { var i: scala.Int = 0; while (i < polygon.size) { {
      val vertex: com.badlogic.gdx.math.Vector2 = polygon.get(i)
      if (((vertex.y < y) && (last.y >= y)) || ((last.y < y) && (vertex.y >= y))) {
        if ((vertex.x + (((y - vertex.y) / (last.y - vertex.y)) * (last.x - vertex.x))) < x) {
          oddNodes = !oddNodes
        } else ()
      } else ()
      last = vertex
    }; i = i + 1 } }
    return oddNodes
  }
  def isPointInPolygon(polygon: scala.Array[scala.Float], offset: scala.Int, count: scala.Int, x: scala.Float, y: scala.Float): scala.Boolean = {
    var oddNodes: scala.Boolean = false
    val sx: scala.Float = polygon(offset)
    val sy: scala.Float = polygon(offset + 1)
    var y1: scala.Float = sy
    var yi: scala.Int = offset + 3;
    { val n: scala.Int = offset + count; while (yi < n) { {
      val y2: scala.Float = polygon(yi)
      if (((y2 < y) && (y1 >= y)) || ((y1 < y) && (y2 >= y))) {
        val x2: scala.Float = polygon(yi - 1)
        if ((x2 + (((y - y2) / (y1 - y2)) * (polygon(yi - 3) - x2))) < x) {
          oddNodes = !oddNodes
        } else ()
      } else ()
      y1 = y2
    }; yi = yi + 2 } }
    if (((sy < y) && (y1 >= y)) || ((y1 < y) && (sy >= y))) {
      if ((sx + (((y - sy) / (y1 - sy)) * (polygon(yi - 3) - sx))) < x) {
        oddNodes = !oddNodes
      } else ()
    } else ()
    return oddNodes
  }
  def intersectPolygons(p1: com.badlogic.gdx.math.Polygon, p2: com.badlogic.gdx.math.Polygon, overlap: com.badlogic.gdx.math.Polygon): scala.Boolean = {
    if ((p1.getVertices().length == 0) || (p2.getVertices().length == 0)) {
      return false
    } else ()
    val ip: com.badlogic.gdx.math.Vector2 = Intersector.ip
    val ep1: com.badlogic.gdx.math.Vector2 = Intersector.ep1
    val ep2: com.badlogic.gdx.math.Vector2 = Intersector.ep2
    val s: com.badlogic.gdx.math.Vector2 = Intersector.s
    val e: com.badlogic.gdx.math.Vector2 = Intersector.e
    val floatArray: com.badlogic.gdx.utils.FloatArray = Intersector.floatArray
    val floatArray2: com.badlogic.gdx.utils.FloatArray = Intersector.floatArray2
    floatArray.clear()
    floatArray2.clear()
    floatArray2.addAll(p1.getTransformedVertices())
    val vertices2: scala.Array[scala.Float] = p2.getTransformedVertices();
    { var i: scala.Int = 0; val last: scala.Int = vertices2.length - 2; while (i <= last) { {
      ep1.set(vertices2(i), vertices2(i + 1))
      if (i < last) {
        ep2.set(vertices2(i + 2), vertices2(i + 3))
      } else {
        ep2.set(vertices2(0), vertices2(1))
      }
      if (floatArray2.size == 0) {
        return false
      } else ()
      s.set(floatArray2.get(floatArray2.size - 2), floatArray2.get(floatArray2.size - 1));
      { var j: scala.Int = 0; while (j < floatArray2.size) { {
        e.set(floatArray2.get(j), floatArray2.get(j + 1))
        val side: scala.Boolean = Intersector.pointLineSide(ep2, ep1, s) > 0
        if (Intersector.pointLineSide(ep2, ep1, e) > 0) {
          if (!side) {
            Intersector.intersectLines(s, e, ep1, ep2, ip)
            if (((floatArray.size < 2) || (floatArray.get(floatArray.size - 2) != ip.x)) || (floatArray.get(floatArray.size - 1) != ip.y)) {
              floatArray.add(ip.x)
              floatArray.add(ip.y)
            } else ()
          } else ()
          floatArray.add(e.x)
          floatArray.add(e.y)
        } else {
          if (side) {
            Intersector.intersectLines(s, e, ep1, ep2, ip)
            floatArray.add(ip.x)
            floatArray.add(ip.y)
          } else ()
        }
        s.set(e.x, e.y)
      }; j = j + 2 } }
      floatArray2.clear()
      floatArray2.addAll(floatArray)
      floatArray.clear()
    }; i = i + 2 } }
    if (((floatArray2.size >= 6) && (floatArray2.get(0) == floatArray2.get(floatArray2.size - 2))) && (floatArray2.get(1) == floatArray2.get(floatArray2.size - 1))) {
      floatArray2.setSize(floatArray2.size - 2)
    } else ()
    if (floatArray2.size >= 6) {
      if (overlap != null) {
        overlap.resetTransformations()
        if (overlap.getVertices().length == floatArray2.size) {
          java.lang.System.arraycopy(floatArray2.items, 0, overlap.getVertices(), 0, floatArray2.size)
        } else {
          overlap.setVertices(floatArray2.toArray())
        }
      } else ()
      return true
    } else ()
    return false
  }
  def intersectPolygons(polygon1: com.badlogic.gdx.utils.FloatArray, polygon2: com.badlogic.gdx.utils.FloatArray): scala.Boolean = {
    if (Intersector.isPointInPolygon(polygon1.items, 0, polygon1.size, polygon2.items(0), polygon2.items(1))) {
      return true
    } else ()
    if (Intersector.isPointInPolygon(polygon2.items, 0, polygon2.size, polygon1.items(0), polygon1.items(1))) {
      return true
    } else ()
    return Intersector.intersectPolygonEdges(polygon1, polygon2)
  }
  def intersectPolygonEdges(polygon1: com.badlogic.gdx.utils.FloatArray, polygon2: com.badlogic.gdx.utils.FloatArray): scala.Boolean = {
    val last1: scala.Int = polygon1.size - 2
    val last2: scala.Int = polygon2.size - 2
    val p1: scala.Array[scala.Float] = polygon1.items
    val p2: scala.Array[scala.Float] = polygon2.items
    var x1: scala.Float = p1(last1)
    var y1: scala.Float = p1(last1 + 1);
    { var i: scala.Int = 0; while (i <= last1) { {
      val x2: scala.Float = p1(i)
      val y2: scala.Float = p1(i + 1)
      var x3: scala.Float = p2(last2)
      var y3: scala.Float = p2(last2 + 1);
      { var j: scala.Int = 0; while (j <= last2) { {
        val x4: scala.Float = p2(j)
        val y4: scala.Float = p2(j + 1)
        if (Intersector.intersectSegments(x1, y1, x2, y2, x3, y3, x4, y4, null)) {
          return true
        } else ()
        x3 = x4
        y3 = y4
      }; j = j + 2 } }
      x1 = x2
      y1 = y2
    }; i = i + 2 } }
    return false
  }
  def distanceLinePoint(startX: scala.Float, startY: scala.Float, endX: scala.Float, endY: scala.Float, pointX: scala.Float, pointY: scala.Float): scala.Float = {
    val normalLength: scala.Float = com.badlogic.gdx.math.Vector2.len(endX - startX, endY - startY)
    return java.lang.Math.abs(((pointX - startX) * (endY - startY)) - ((pointY - startY) * (endX - startX))) / normalLength
  }
  def distanceSegmentPoint(startX: scala.Float, startY: scala.Float, endX: scala.Float, endY: scala.Float, pointX: scala.Float, pointY: scala.Float): scala.Float = {
    return Intersector.nearestSegmentPoint(startX, startY, endX, endY, pointX, pointY, Intersector.v2a).dst(pointX, pointY)
  }
  def distanceSegmentPoint(start: com.badlogic.gdx.math.Vector2, `end`: com.badlogic.gdx.math.Vector2, point: com.badlogic.gdx.math.Vector2): scala.Float = {
    return Intersector.nearestSegmentPoint(start, `end`, point, Intersector.v2a).dst(point)
  }
  def nearestSegmentPoint(start: com.badlogic.gdx.math.Vector2, `end`: com.badlogic.gdx.math.Vector2, point: com.badlogic.gdx.math.Vector2, nearest: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val length2: scala.Float = start.dst2(`end`)
    if (length2 == 0) {
      return nearest.set(start)
    } else ()
    val t: scala.Float = (((point.x - start.x) * (`end`.x - start.x)) + ((point.y - start.y) * (`end`.y - start.y))) / length2
    if (t <= 0) {
      return nearest.set(start)
    } else ()
    if (t >= 1) {
      return nearest.set(`end`)
    } else ()
    return nearest.set(start.x + (t * (`end`.x - start.x)), start.y + (t * (`end`.y - start.y)))
  }
  def nearestSegmentPoint(startX: scala.Float, startY: scala.Float, endX: scala.Float, endY: scala.Float, pointX: scala.Float, pointY: scala.Float, nearest: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val xDiff: scala.Float = endX - startX
    val yDiff: scala.Float = endY - startY
    val length2: scala.Float = (xDiff * xDiff) + (yDiff * yDiff)
    if (length2 == 0) {
      return nearest.set(startX, startY)
    } else ()
    val t: scala.Float = (((pointX - startX) * (endX - startX)) + ((pointY - startY) * (endY - startY))) / length2
    if (t <= 0) {
      return nearest.set(startX, startY)
    } else ()
    if (t >= 1) {
      return nearest.set(endX, endY)
    } else ()
    return nearest.set(startX + (t * (endX - startX)), startY + (t * (endY - startY)))
  }
  def intersectSegmentCircle(start: com.badlogic.gdx.math.Vector2, `end`: com.badlogic.gdx.math.Vector2, center: com.badlogic.gdx.math.Vector2, squareRadius: scala.Float): scala.Boolean = {
    Intersector.tmp.set(`end`.x - start.x, `end`.y - start.y, 0)
    Intersector.tmp1.set(center.x - start.x, center.y - start.y, 0)
    val l: scala.Float = Intersector.tmp.len()
    val u: scala.Float = Intersector.tmp1.dot(Intersector.tmp.nor())
    if (u <= 0) {
      Intersector.tmp2.set(start.x, start.y, 0)
    } else {
      if (u >= l) {
        Intersector.tmp2.set(`end`.x, `end`.y, 0)
      } else {
        Intersector.tmp3.set(Intersector.tmp.scl(u))
        Intersector.tmp2.set(Intersector.tmp3.x + start.x, Intersector.tmp3.y + start.y, 0)
      }
    }
    val x: scala.Float = center.x - Intersector.tmp2.x
    val y: scala.Float = center.y - Intersector.tmp2.y
    return ((x * x) + (y * y)) <= squareRadius
  }
  def intersectSegmentCircle(start: com.badlogic.gdx.math.Vector2, `end`: com.badlogic.gdx.math.Vector2, circle: com.badlogic.gdx.math.Circle, mtv: com.badlogic.gdx.math.Intersector.MinimumTranslationVector): scala.Boolean = {
    Intersector.v2a.set(`end`).sub(start)
    Intersector.v2b.set(circle.x - start.x, circle.y - start.y)
    val len: scala.Float = Intersector.v2a.len()
    val u: scala.Float = Intersector.v2b.dot(Intersector.v2a.nor())
    if (u <= 0) {
      Intersector.v2c.set(start)
    } else {
      if (u >= len) {
        Intersector.v2c.set(`end`)
      } else {
        Intersector.v2d.set(Intersector.v2a.scl(u))
        Intersector.v2c.set(Intersector.v2d).add(start)
      }
    }
    Intersector.v2a.set(Intersector.v2c.x - circle.x, Intersector.v2c.y - circle.y)
    if (mtv != null) {
      if (Intersector.v2a.equals(com.badlogic.gdx.math.Vector2.Zero)) {
        Intersector.v2d.set(`end`.y - start.y, start.x - `end`.x)
        mtv.normal.set(Intersector.v2d).nor()
        mtv.depth = circle.radius
      } else {
        mtv.normal.set(Intersector.v2a).nor()
        mtv.depth = circle.radius - Intersector.v2a.len()
      }
    } else ()
    return Intersector.v2a.len2() <= (circle.radius * circle.radius)
  }
  def intersectFrustumBounds(frustum: com.badlogic.gdx.math.Frustum, bounds: com.badlogic.gdx.math.collision.BoundingBox): scala.Boolean = {
    val boundsIntersectsFrustum: scala.Boolean = ((((((frustum.pointInFrustum(bounds.getCorner000(Intersector.tmp)) || frustum.pointInFrustum(bounds.getCorner001(Intersector.tmp))) || frustum.pointInFrustum(bounds.getCorner010(Intersector.tmp))) || frustum.pointInFrustum(bounds.getCorner011(Intersector.tmp))) || frustum.pointInFrustum(bounds.getCorner100(Intersector.tmp))) || frustum.pointInFrustum(bounds.getCorner101(Intersector.tmp))) || frustum.pointInFrustum(bounds.getCorner110(Intersector.tmp))) || frustum.pointInFrustum(bounds.getCorner111(Intersector.tmp))
    if (boundsIntersectsFrustum) {
      return true
    } else ()
    var frustumIsInsideBounds: scala.Boolean = false
    for (point <- frustum.planePoints) {
      frustumIsInsideBounds = frustumIsInsideBounds | bounds.contains(point)
    }
    return frustumIsInsideBounds
  }
  def intersectFrustumBounds(frustum: com.badlogic.gdx.math.Frustum, obb: com.badlogic.gdx.math.collision.OrientedBoundingBox): scala.Boolean = {
    var boundsIntersectsFrustum: scala.Boolean = false
    for (v <- obb.getVertices()) {
      boundsIntersectsFrustum = boundsIntersectsFrustum | frustum.pointInFrustum(v)
    }
    if (boundsIntersectsFrustum) {
      return true
    } else ()
    var frustumIsInsideBounds: scala.Boolean = false
    for (point <- frustum.planePoints) {
      frustumIsInsideBounds = frustumIsInsideBounds | obb.contains(point)
    }
    return frustumIsInsideBounds
  }
  def intersectRayRay(start1: com.badlogic.gdx.math.Vector2, direction1: com.badlogic.gdx.math.Vector2, start2: com.badlogic.gdx.math.Vector2, direction2: com.badlogic.gdx.math.Vector2): scala.Float = {
    val difx: scala.Float = start2.x - start1.x
    val dify: scala.Float = start2.y - start1.y
    val d1xd2: scala.Float = (direction1.x * direction2.y) - (direction1.y * direction2.x)
    if (d1xd2 == 0.0f) {
      return java.lang.Float.POSITIVE_INFINITY
    } else ()
    val d2sx: scala.Float = direction2.x / d1xd2
    val d2sy: scala.Float = direction2.y / d1xd2
    return (difx * d2sy) - (dify * d2sx)
  }
  def intersectRayPlane(ray: com.badlogic.gdx.math.collision.Ray, plane: com.badlogic.gdx.math.Plane, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    val denom: scala.Float = ray.direction.dot(plane.getNormal())
    if (denom != 0) {
      val t: scala.Float = (-(ray.origin.dot(plane.getNormal()) + plane.getD())) / denom
      if (t < 0) {
        return false
      } else ()
      if (intersection != null) {
        intersection.set(ray.origin).add(Intersector.v0.set(ray.direction).scl(t))
      } else ()
      return true
    } else {
      if (plane.testPoint(ray.origin) == com.badlogic.gdx.math.Plane.PlaneSide.OnPlane) {
        if (intersection != null) {
          intersection.set(ray.origin)
        } else ()
        return true
      } else {
        return false
      }
    }
  }
  def intersectLinePlane(x: scala.Float, y: scala.Float, z: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, plane: com.badlogic.gdx.math.Plane, intersection: com.badlogic.gdx.math.Vector3): scala.Float = {
    val direction: com.badlogic.gdx.math.Vector3 = Intersector.tmp.set(x2, y2, z2).sub(x, y, z)
    val origin: com.badlogic.gdx.math.Vector3 = Intersector.tmp2.set(x, y, z)
    val denom: scala.Float = direction.dot(plane.getNormal())
    if (denom != 0) {
      val t: scala.Float = (-(origin.dot(plane.getNormal()) + plane.getD())) / denom
      if (intersection != null) {
        intersection.set(origin).add(direction.scl(t))
      } else ()
      return t
    } else {
      if (plane.testPoint(origin) == com.badlogic.gdx.math.Plane.PlaneSide.OnPlane) {
        if (intersection != null) {
          intersection.set(origin)
        } else ()
        return 0
      } else ()
    }
    return -1
  }
  def intersectPlanes(a: com.badlogic.gdx.math.Plane, b: com.badlogic.gdx.math.Plane, c: com.badlogic.gdx.math.Plane, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    Intersector.tmp1.set(a.normal).crs(b.normal)
    Intersector.tmp2.set(b.normal).crs(c.normal)
    Intersector.tmp3.set(c.normal).crs(a.normal)
    val f: scala.Float = -a.normal.dot(Intersector.tmp2)
    if (java.lang.Math.abs(f) < com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
      return false
    } else ()
    Intersector.tmp1.scl(c.d)
    Intersector.tmp2.scl(a.d)
    Intersector.tmp3.scl(b.d)
    intersection.set((Intersector.tmp1.x + Intersector.tmp2.x) + Intersector.tmp3.x, (Intersector.tmp1.y + Intersector.tmp2.y) + Intersector.tmp3.y, (Intersector.tmp1.z + Intersector.tmp2.z) + Intersector.tmp3.z)
    intersection.scl(1 / f)
    return true
  }
  def intersectRayTriangle(ray: com.badlogic.gdx.math.collision.Ray, t1: com.badlogic.gdx.math.Vector3, t2: com.badlogic.gdx.math.Vector3, t3: com.badlogic.gdx.math.Vector3, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    val edge1: com.badlogic.gdx.math.Vector3 = Intersector.v0.set(t2).sub(t1)
    val edge2: com.badlogic.gdx.math.Vector3 = Intersector.v1.set(t3).sub(t1)
    val pvec: com.badlogic.gdx.math.Vector3 = Intersector.v2.set(ray.direction).crs(edge2)
    var det: scala.Float = edge1.dot(pvec)
    if (com.badlogic.gdx.math.MathUtils.isZero(det)) {
      Intersector.p.set(t1, t2, t3)
      if ((Intersector.p.testPoint(ray.origin) == com.badlogic.gdx.math.Plane.PlaneSide.OnPlane) && Intersector.isPointInTriangle(ray.origin, t1, t2, t3)) {
        if (intersection != null) {
          intersection.set(ray.origin)
        } else ()
        return true
      } else ()
      return false
    } else ()
    det = 1.0f / det
    val tvec: com.badlogic.gdx.math.Vector3 = Intersector.i.set(ray.origin).sub(t1)
    val u: scala.Float = tvec.dot(pvec) * det
    if ((u < 0.0f) || (u > 1.0f)) {
      return false
    } else ()
    val qvec: com.badlogic.gdx.math.Vector3 = tvec.crs(edge1)
    val v: scala.Float = ray.direction.dot(qvec) * det
    if ((v < 0.0f) || ((u + v) > 1.0f)) {
      return false
    } else ()
    val t: scala.Float = edge2.dot(qvec) * det
    if (t < 0) {
      return false
    } else ()
    if (intersection != null) {
      if (t <= com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
        intersection.set(ray.origin)
      } else {
        ray.getEndPoint(intersection, t)
      }
    } else ()
    return true
  }
  def intersectRaySphere(ray: com.badlogic.gdx.math.collision.Ray, center: com.badlogic.gdx.math.Vector3, radius: scala.Float, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    val len: scala.Float = ray.direction.dot(center.x - ray.origin.x, center.y - ray.origin.y, center.z - ray.origin.z)
    if (len < 0.0f) {
      return false
    } else ()
    val dst2: scala.Float = center.dst2(ray.origin.x + (ray.direction.x * len), ray.origin.y + (ray.direction.y * len), ray.origin.z + (ray.direction.z * len))
    val r2: scala.Float = radius * radius
    if (dst2 > r2) {
      return false
    } else ()
    if (intersection != null) {
      intersection.set(ray.direction).scl(len - java.lang.Math.sqrt(r2 - dst2).asInstanceOf[scala.Float]).add(ray.origin)
    } else ()
    return true
  }
  def intersectRayBounds(ray: com.badlogic.gdx.math.collision.Ray, box: com.badlogic.gdx.math.collision.BoundingBox, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    if (box.contains(ray.origin)) {
      if (intersection != null) {
        intersection.set(ray.origin)
      } else ()
      return true
    } else ()
    var lowest: scala.Float = 0
    var t: scala.Float = 0.0f
    var hit: scala.Boolean = false
    if ((ray.origin.x <= box.min$field.x) && (ray.direction.x > 0)) {
      t = (box.min$field.x - ray.origin.x) / ray.direction.x
      if (t >= 0) {
        Intersector.v2.set(ray.direction).scl(t).add(ray.origin)
        if (((((Intersector.v2.y >= box.min$field.y) && (Intersector.v2.y <= box.max$field.y)) && (Intersector.v2.z >= box.min$field.z)) && (Intersector.v2.z <= box.max$field.z)) && ((!hit) || (t < lowest))) {
          hit = true
          lowest = t
        } else ()
      } else ()
    } else ()
    if ((ray.origin.x >= box.max$field.x) && (ray.direction.x < 0)) {
      t = (box.max$field.x - ray.origin.x) / ray.direction.x
      if (t >= 0) {
        Intersector.v2.set(ray.direction).scl(t).add(ray.origin)
        if (((((Intersector.v2.y >= box.min$field.y) && (Intersector.v2.y <= box.max$field.y)) && (Intersector.v2.z >= box.min$field.z)) && (Intersector.v2.z <= box.max$field.z)) && ((!hit) || (t < lowest))) {
          hit = true
          lowest = t
        } else ()
      } else ()
    } else ()
    if ((ray.origin.y <= box.min$field.y) && (ray.direction.y > 0)) {
      t = (box.min$field.y - ray.origin.y) / ray.direction.y
      if (t >= 0) {
        Intersector.v2.set(ray.direction).scl(t).add(ray.origin)
        if (((((Intersector.v2.x >= box.min$field.x) && (Intersector.v2.x <= box.max$field.x)) && (Intersector.v2.z >= box.min$field.z)) && (Intersector.v2.z <= box.max$field.z)) && ((!hit) || (t < lowest))) {
          hit = true
          lowest = t
        } else ()
      } else ()
    } else ()
    if ((ray.origin.y >= box.max$field.y) && (ray.direction.y < 0)) {
      t = (box.max$field.y - ray.origin.y) / ray.direction.y
      if (t >= 0) {
        Intersector.v2.set(ray.direction).scl(t).add(ray.origin)
        if (((((Intersector.v2.x >= box.min$field.x) && (Intersector.v2.x <= box.max$field.x)) && (Intersector.v2.z >= box.min$field.z)) && (Intersector.v2.z <= box.max$field.z)) && ((!hit) || (t < lowest))) {
          hit = true
          lowest = t
        } else ()
      } else ()
    } else ()
    if ((ray.origin.z <= box.min$field.z) && (ray.direction.z > 0)) {
      t = (box.min$field.z - ray.origin.z) / ray.direction.z
      if (t >= 0) {
        Intersector.v2.set(ray.direction).scl(t).add(ray.origin)
        if (((((Intersector.v2.x >= box.min$field.x) && (Intersector.v2.x <= box.max$field.x)) && (Intersector.v2.y >= box.min$field.y)) && (Intersector.v2.y <= box.max$field.y)) && ((!hit) || (t < lowest))) {
          hit = true
          lowest = t
        } else ()
      } else ()
    } else ()
    if ((ray.origin.z >= box.max$field.z) && (ray.direction.z < 0)) {
      t = (box.max$field.z - ray.origin.z) / ray.direction.z
      if (t >= 0) {
        Intersector.v2.set(ray.direction).scl(t).add(ray.origin)
        if (((((Intersector.v2.x >= box.min$field.x) && (Intersector.v2.x <= box.max$field.x)) && (Intersector.v2.y >= box.min$field.y)) && (Intersector.v2.y <= box.max$field.y)) && ((!hit) || (t < lowest))) {
          hit = true
          lowest = t
        } else ()
      } else ()
    } else ()
    if (hit && (intersection != null)) {
      intersection.set(ray.direction).scl(lowest).add(ray.origin)
      if (intersection.x < box.min$field.x) {
        intersection.x = box.min$field.x
      } else {
        if (intersection.x > box.max$field.x) {
          intersection.x = box.max$field.x
        } else ()
      }
      if (intersection.y < box.min$field.y) {
        intersection.y = box.min$field.y
      } else {
        if (intersection.y > box.max$field.y) {
          intersection.y = box.max$field.y
        } else ()
      }
      if (intersection.z < box.min$field.z) {
        intersection.z = box.min$field.z
      } else {
        if (intersection.z > box.max$field.z) {
          intersection.z = box.max$field.z
        } else ()
      }
    } else ()
    return hit
  }
  def intersectRayBoundsFast(ray: com.badlogic.gdx.math.collision.Ray, box: com.badlogic.gdx.math.collision.BoundingBox): scala.Boolean = {
    return Intersector.intersectRayBoundsFast(ray, box.getCenter(Intersector.tmp1), box.getDimensions(Intersector.tmp2))
  }
  def intersectRayBoundsFast(ray: com.badlogic.gdx.math.collision.Ray, center: com.badlogic.gdx.math.Vector3, dimensions: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    val divX: scala.Float = 1.0f / ray.direction.x
    val divY: scala.Float = 1.0f / ray.direction.y
    val divZ: scala.Float = 1.0f / ray.direction.z
    var minx: scala.Float = ((center.x - (dimensions.x * 0.5f)) - ray.origin.x) * divX
    var maxx: scala.Float = ((center.x + (dimensions.x * 0.5f)) - ray.origin.x) * divX
    if (minx > maxx) {
      val t: scala.Float = minx
      minx = maxx
      maxx = t
    } else ()
    var miny: scala.Float = ((center.y - (dimensions.y * 0.5f)) - ray.origin.y) * divY
    var maxy: scala.Float = ((center.y + (dimensions.y * 0.5f)) - ray.origin.y) * divY
    if (miny > maxy) {
      val t: scala.Float = miny
      miny = maxy
      maxy = t
    } else ()
    var minz: scala.Float = ((center.z - (dimensions.z * 0.5f)) - ray.origin.z) * divZ
    var maxz: scala.Float = ((center.z + (dimensions.z * 0.5f)) - ray.origin.z) * divZ
    if (minz > maxz) {
      val t: scala.Float = minz
      minz = maxz
      maxz = t
    } else ()
    val min: scala.Float = java.lang.Math.max(java.lang.Math.max(minx, miny), minz)
    val max: scala.Float = java.lang.Math.min(java.lang.Math.min(maxx, maxy), maxz)
    return (max >= 0) && (max >= min)
  }
  def intersectRayOrientedBoundsFast(ray: com.badlogic.gdx.math.collision.Ray, obb: com.badlogic.gdx.math.collision.OrientedBoundingBox): scala.Boolean = {
    return Intersector.intersectRayOrientedBounds(ray, obb, null)
  }
  def intersectRayOrientedBoundsFast(ray: com.badlogic.gdx.math.collision.Ray, bounds: com.badlogic.gdx.math.collision.BoundingBox, transform: com.badlogic.gdx.math.Matrix4): scala.Boolean = {
    return Intersector.intersectRayOrientedBounds(ray, bounds, transform, null)
  }
  def intersectRayOrientedBounds(ray: com.badlogic.gdx.math.collision.Ray, obb: com.badlogic.gdx.math.collision.OrientedBoundingBox, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    val bounds: com.badlogic.gdx.math.collision.BoundingBox = obb.getBounds()
    val transform: com.badlogic.gdx.math.Matrix4 = obb.getTransform()
    return Intersector.intersectRayOrientedBounds(ray, bounds, transform, intersection)
  }
  def intersectRayOrientedBounds(ray: com.badlogic.gdx.math.collision.Ray, bounds: com.badlogic.gdx.math.collision.BoundingBox, transform: com.badlogic.gdx.math.Matrix4, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    var tMin: scala.Float = 0.0f
    var tMax: scala.Float = java.lang.Float.MAX_VALUE
    var t1: scala.Float = 0.0f
    var t2: scala.Float = 0.0f
    val oBBposition: com.badlogic.gdx.math.Vector3 = transform.getTranslation(Intersector.tmp)
    val delta: com.badlogic.gdx.math.Vector3 = oBBposition.sub(ray.origin)
    val xaxis: com.badlogic.gdx.math.Vector3 = Intersector.tmp1
    Intersector.tmp1.set(transform.`val`(com.badlogic.gdx.math.Matrix4.M00), transform.`val`(com.badlogic.gdx.math.Matrix4.M10), transform.`val`(com.badlogic.gdx.math.Matrix4.M20))
    var e: scala.Float = xaxis.dot(delta)
    var f: scala.Float = ray.direction.dot(xaxis)
    if (java.lang.Math.abs(f) > com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
      t1 = (e + bounds.min$field.x) / f
      t2 = (e + bounds.max$field.x) / f
      if (t1 > t2) {
        val w: scala.Float = t1
        t1 = t2
        t2 = w
      } else ()
      if (t2 < tMax) {
        tMax = t2
      } else ()
      if (t1 > tMin) {
        tMin = t1
      } else ()
      if (tMax < tMin) {
        return false
      } else ()
    } else {
      if ((((-e) + bounds.min$field.x) > 0.0f) || (((-e) + bounds.max$field.x) < 0.0f)) {
        return false
      } else ()
    }
    val yaxis: com.badlogic.gdx.math.Vector3 = Intersector.tmp2
    Intersector.tmp2.set(transform.`val`(com.badlogic.gdx.math.Matrix4.M01), transform.`val`(com.badlogic.gdx.math.Matrix4.M11), transform.`val`(com.badlogic.gdx.math.Matrix4.M21))
    e = yaxis.dot(delta)
    f = ray.direction.dot(yaxis)
    if (java.lang.Math.abs(f) > com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
      t1 = (e + bounds.min$field.y) / f
      t2 = (e + bounds.max$field.y) / f
      if (t1 > t2) {
        val w: scala.Float = t1
        t1 = t2
        t2 = w
      } else ()
      if (t2 < tMax) {
        tMax = t2
      } else ()
      if (t1 > tMin) {
        tMin = t1
      } else ()
      if (tMin > tMax) {
        return false
      } else ()
    } else {
      if ((((-e) + bounds.min$field.y) > 0.0f) || (((-e) + bounds.max$field.y) < 0.0f)) {
        return false
      } else ()
    }
    val zaxis: com.badlogic.gdx.math.Vector3 = Intersector.tmp3
    Intersector.tmp3.set(transform.`val`(com.badlogic.gdx.math.Matrix4.M02), transform.`val`(com.badlogic.gdx.math.Matrix4.M12), transform.`val`(com.badlogic.gdx.math.Matrix4.M22))
    e = zaxis.dot(delta)
    f = ray.direction.dot(zaxis)
    if (java.lang.Math.abs(f) > com.badlogic.gdx.math.MathUtils.FLOAT_ROUNDING_ERROR) {
      t1 = (e + bounds.min$field.z) / f
      t2 = (e + bounds.max$field.z) / f
      if (t1 > t2) {
        val w: scala.Float = t1
        t1 = t2
        t2 = w
      } else ()
      if (t2 < tMax) {
        tMax = t2
      } else ()
      if (t1 > tMin) {
        tMin = t1
      } else ()
      if (tMin > tMax) {
        return false
      } else ()
    } else {
      if ((((-e) + bounds.min$field.z) > 0.0f) || (((-e) + bounds.max$field.z) < 0.0f)) {
        return false
      } else ()
    }
    if (intersection != null) {
      ray.getEndPoint(intersection, tMin)
    } else ()
    return true
  }
  def intersectRayTriangles(ray: com.badlogic.gdx.math.collision.Ray, triangles: scala.Array[scala.Float], intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    var min_dist: scala.Float = java.lang.Float.MAX_VALUE
    var hit: scala.Boolean = false
    if ((triangles.length % 9) != 0) {
      throw new java.lang.RuntimeException("triangles array size is not a multiple of 9")
    } else ();
    { var i: scala.Int = 0; while (i < triangles.length) { {
      val result: scala.Boolean = Intersector.intersectRayTriangle(ray, Intersector.tmp1.set(triangles(i), triangles(i + 1), triangles(i + 2)), Intersector.tmp2.set(triangles(i + 3), triangles(i + 4), triangles(i + 5)), Intersector.tmp3.set(triangles(i + 6), triangles(i + 7), triangles(i + 8)), Intersector.tmp)
      if (result) {
        val dist: scala.Float = ray.origin.dst2(Intersector.tmp)
        if (dist < min_dist) {
          min_dist = dist
          Intersector.best.set(Intersector.tmp)
          hit = true
        } else ()
      } else ()
    }; i = i + 9 } }
    if (!hit) {
      return false
    } else {
      if (intersection != null) {
        intersection.set(Intersector.best)
      } else ()
      return true
    }
  }
  def intersectRayTriangles(ray: com.badlogic.gdx.math.collision.Ray, vertices: scala.Array[scala.Float], indices: scala.Array[scala.Short], vertexSize: scala.Int, intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    var min_dist: scala.Float = java.lang.Float.MAX_VALUE
    var hit: scala.Boolean = false
    if ((indices.length % 3) != 0) {
      throw new java.lang.RuntimeException("triangle list size is not a multiple of 3")
    } else ();
    { var i: scala.Int = 0; while (i < indices.length) { {
      val i1: scala.Int = indices(i) * vertexSize
      val i2: scala.Int = indices(i + 1) * vertexSize
      val i3: scala.Int = indices(i + 2) * vertexSize
      val result: scala.Boolean = Intersector.intersectRayTriangle(ray, Intersector.tmp1.set(vertices(i1), vertices(i1 + 1), vertices(i1 + 2)), Intersector.tmp2.set(vertices(i2), vertices(i2 + 1), vertices(i2 + 2)), Intersector.tmp3.set(vertices(i3), vertices(i3 + 1), vertices(i3 + 2)), Intersector.tmp)
      if (result) {
        val dist: scala.Float = ray.origin.dst2(Intersector.tmp)
        if (dist < min_dist) {
          min_dist = dist
          Intersector.best.set(Intersector.tmp)
          hit = true
        } else ()
      } else ()
    }; i = i + 3 } }
    if (!hit) {
      return false
    } else {
      if (intersection != null) {
        intersection.set(Intersector.best)
      } else ()
      return true
    }
  }
  def intersectRayTriangles(ray: com.badlogic.gdx.math.collision.Ray, triangles: scala.collection.mutable.Buffer[com.badlogic.gdx.math.Vector3], intersection: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    var min_dist: scala.Float = java.lang.Float.MAX_VALUE
    var hit: scala.Boolean = false
    if ((triangles.size % 3) != 0) {
      throw new java.lang.RuntimeException("triangle list size is not a multiple of 3")
    } else ();
    { var i: scala.Int = 0; while (i < triangles.size) { {
      val result: scala.Boolean = Intersector.intersectRayTriangle(ray, triangles(i), triangles(i + 1), triangles(i + 2), Intersector.tmp)
      if (result) {
        val dist: scala.Float = ray.origin.dst2(Intersector.tmp)
        if (dist < min_dist) {
          min_dist = dist
          Intersector.best.set(Intersector.tmp)
          hit = true
        } else ()
      } else ()
    }; i = i + 3 } }
    if (!hit) {
      return false
    } else {
      if (intersection != null) {
        intersection.set(Intersector.best)
      } else ()
      return true
    }
  }
  def intersectBoundsPlaneFast(box: com.badlogic.gdx.math.collision.BoundingBox, plane: com.badlogic.gdx.math.Plane): scala.Boolean = {
    return Intersector.intersectBoundsPlaneFast(box.getCenter(Intersector.tmp1), box.getDimensions(Intersector.tmp2).scl(0.5f), plane.normal, plane.d)
  }
  def intersectBoundsPlaneFast(center: com.badlogic.gdx.math.Vector3, halfDimensions: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3, distance: scala.Float): scala.Boolean = {
    val radius: scala.Float = ((halfDimensions.x * java.lang.Math.abs(normal.x)) + (halfDimensions.y * java.lang.Math.abs(normal.y))) + (halfDimensions.z * java.lang.Math.abs(normal.z))
    val s: scala.Float = normal.dot(center) - distance
    return java.lang.Math.abs(s) <= radius
  }
  def intersectLines(p1: com.badlogic.gdx.math.Vector2, p2: com.badlogic.gdx.math.Vector2, p3: com.badlogic.gdx.math.Vector2, p4: com.badlogic.gdx.math.Vector2, intersection: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    return Intersector.intersectLines(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y, intersection)
  }
  def intersectLines(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float, x4: scala.Float, y4: scala.Float, intersection: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    val d: scala.Float = ((y4 - y3) * (x2 - x1)) - ((x4 - x3) * (y2 - y1))
    if (d == 0) {
      return false
    } else ()
    if (intersection != null) {
      val ua: scala.Float = (((x4 - x3) * (y1 - y3)) - ((y4 - y3) * (x1 - x3))) / d
      intersection.set(x1 + ((x2 - x1) * ua), y1 + ((y2 - y1) * ua))
    } else ()
    return true
  }
  def intersectLinePolygon(p1: com.badlogic.gdx.math.Vector2, p2: com.badlogic.gdx.math.Vector2, polygon: com.badlogic.gdx.math.Polygon): scala.Boolean = {
    val vertices: scala.Array[scala.Float] = polygon.getTransformedVertices()
    val x1: scala.Float = p1.x
    val y1: scala.Float = p1.y
    val x2: scala.Float = p2.x
    val y2: scala.Float = p2.y
    val n: scala.Int = vertices.length
    var x3: scala.Float = vertices(n - 2)
    var y3: scala.Float = vertices(n - 1);
    { var i: scala.Int = 0; while (i < n) { {
      val x4: scala.Float = vertices(i)
      val y4: scala.Float = vertices(i + 1)
      val d: scala.Float = ((y4 - y3) * (x2 - x1)) - ((x4 - x3) * (y2 - y1))
      if (d != 0) {
        val yd: scala.Float = y1 - y3
        val xd: scala.Float = x1 - x3
        val ua: scala.Float = (((x4 - x3) * yd) - ((y4 - y3) * xd)) / d
        if ((ua >= 0) && (ua <= 1)) {
          return true
        } else ()
      } else ()
      x3 = x4
      y3 = y4
    }; i = i + 2 } }
    return false
  }
  def intersectRectangles(rectangle1: com.badlogic.gdx.math.Rectangle, rectangle2: com.badlogic.gdx.math.Rectangle, intersection: com.badlogic.gdx.math.Rectangle): scala.Boolean = {
    if (rectangle1.overlaps(rectangle2)) {
      intersection.x = java.lang.Math.max(rectangle1.x, rectangle2.x)
      intersection.width = java.lang.Math.min(rectangle1.x + rectangle1.width, rectangle2.x + rectangle2.width) - intersection.x
      intersection.y = java.lang.Math.max(rectangle1.y, rectangle2.y)
      intersection.height = java.lang.Math.min(rectangle1.y + rectangle1.height, rectangle2.y + rectangle2.height) - intersection.y
      return true
    } else ()
    return false
  }
  def intersectSegmentRectangle(startX: scala.Float, startY: scala.Float, endX: scala.Float, endY: scala.Float, rectangle: com.badlogic.gdx.math.Rectangle): scala.Boolean = {
    val rectangleEndX: scala.Float = rectangle.x + rectangle.width
    val rectangleEndY: scala.Float = rectangle.y + rectangle.height
    if (Intersector.intersectSegments(startX, startY, endX, endY, rectangle.x, rectangle.y, rectangle.x, rectangleEndY, null)) {
      return true
    } else ()
    if (Intersector.intersectSegments(startX, startY, endX, endY, rectangle.x, rectangle.y, rectangleEndX, rectangle.y, null)) {
      return true
    } else ()
    if (Intersector.intersectSegments(startX, startY, endX, endY, rectangleEndX, rectangle.y, rectangleEndX, rectangleEndY, null)) {
      return true
    } else ()
    if (Intersector.intersectSegments(startX, startY, endX, endY, rectangle.x, rectangleEndY, rectangleEndX, rectangleEndY, null)) {
      return true
    } else ()
    return rectangle.contains(startX, startY)
  }
  def intersectSegmentRectangle(start: com.badlogic.gdx.math.Vector2, `end`: com.badlogic.gdx.math.Vector2, rectangle: com.badlogic.gdx.math.Rectangle): scala.Boolean = {
    return Intersector.intersectSegmentRectangle(start.x, start.y, `end`.x, `end`.y, rectangle)
  }
  def intersectSegmentPolygon(p1: com.badlogic.gdx.math.Vector2, p2: com.badlogic.gdx.math.Vector2, polygon: com.badlogic.gdx.math.Polygon): scala.Boolean = {
    val vertices: scala.Array[scala.Float] = polygon.getTransformedVertices()
    val x1: scala.Float = p1.x
    val y1: scala.Float = p1.y
    val x2: scala.Float = p2.x
    val y2: scala.Float = p2.y
    val n: scala.Int = vertices.length
    var x3: scala.Float = vertices(n - 2)
    var y3: scala.Float = vertices(n - 1);
    { var i: scala.Int = 0; while (i < n) { {
      val x4: scala.Float = vertices(i)
      val y4: scala.Float = vertices(i + 1)
      val d: scala.Float = ((y4 - y3) * (x2 - x1)) - ((x4 - x3) * (y2 - y1))
      if (d != 0) {
        val yd: scala.Float = y1 - y3
        val xd: scala.Float = x1 - x3
        val ua: scala.Float = (((x4 - x3) * yd) - ((y4 - y3) * xd)) / d
        if ((ua >= 0) && (ua <= 1)) {
          val ub: scala.Float = (((x2 - x1) * yd) - ((y2 - y1) * xd)) / d
          if ((ub >= 0) && (ub <= 1)) {
            return true
          } else ()
        } else ()
      } else ()
      x3 = x4
      y3 = y4
    }; i = i + 2 } }
    return false
  }
  def intersectSegments(p1: com.badlogic.gdx.math.Vector2, p2: com.badlogic.gdx.math.Vector2, p3: com.badlogic.gdx.math.Vector2, p4: com.badlogic.gdx.math.Vector2, intersection: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    return Intersector.intersectSegments(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y, intersection)
  }
  def intersectSegments(x1: scala.Float, y1: scala.Float, x2: scala.Float, y2: scala.Float, x3: scala.Float, y3: scala.Float, x4: scala.Float, y4: scala.Float, intersection: com.badlogic.gdx.math.Vector2): scala.Boolean = {
    val d: scala.Float = ((y4 - y3) * (x2 - x1)) - ((x4 - x3) * (y2 - y1))
    if (d == 0) {
      return false
    } else ()
    val yd: scala.Float = y1 - y3
    val xd: scala.Float = x1 - x3
    val ua: scala.Float = (((x4 - x3) * yd) - ((y4 - y3) * xd)) / d
    if ((ua < 0) || (ua > 1)) {
      return false
    } else ()
    val ub: scala.Float = (((x2 - x1) * yd) - ((y2 - y1) * xd)) / d
    if ((ub < 0) || (ub > 1)) {
      return false
    } else ()
    if (intersection != null) {
      intersection.set(x1 + ((x2 - x1) * ua), y1 + ((y2 - y1) * ua))
    } else ()
    return true
  }
  def det(a: scala.Float, b: scala.Float, c: scala.Float, d: scala.Float): scala.Float = {
    return (a * d) - (b * c)
  }
  def detd(a: scala.Double, b: scala.Double, c: scala.Double, d: scala.Double): scala.Double = {
    return (a * d) - (b * c)
  }
  def overlaps(c1: com.badlogic.gdx.math.Circle, c2: com.badlogic.gdx.math.Circle): scala.Boolean = {
    return c1.overlaps(c2)
  }
  def overlaps(r1: com.badlogic.gdx.math.Rectangle, r2: com.badlogic.gdx.math.Rectangle): scala.Boolean = {
    return r1.overlaps(r2)
  }
  def overlaps(c: com.badlogic.gdx.math.Circle, r: com.badlogic.gdx.math.Rectangle): scala.Boolean = {
    var closestX: scala.Float = c.x
    var closestY: scala.Float = c.y
    if (c.x < r.x) {
      closestX = r.x
    } else {
      if (c.x > (r.x + r.width)) {
        closestX = r.x + r.width
      } else ()
    }
    if (c.y < r.y) {
      closestY = r.y
    } else {
      if (c.y > (r.y + r.height)) {
        closestY = r.y + r.height
      } else ()
    }
    closestX = closestX - c.x
    closestX = closestX * closestX
    closestY = closestY - c.y
    closestY = closestY * closestY
    return (closestX + closestY) < (c.radius * c.radius)
  }
  def overlapConvexPolygons(p1: com.badlogic.gdx.math.Polygon, p2: com.badlogic.gdx.math.Polygon): scala.Boolean = {
    return Intersector.overlapConvexPolygons(p1, p2, null)
  }
  def overlapConvexPolygons(p1: com.badlogic.gdx.math.Polygon, p2: com.badlogic.gdx.math.Polygon, mtv: com.badlogic.gdx.math.Intersector.MinimumTranslationVector): scala.Boolean = {
    return Intersector.overlapConvexPolygons(p1.getTransformedVertices(), p2.getTransformedVertices(), mtv)
  }
  def overlapConvexPolygons(verts1: scala.Array[scala.Float], verts2: scala.Array[scala.Float], mtv: com.badlogic.gdx.math.Intersector.MinimumTranslationVector): scala.Boolean = {
    return Intersector.overlapConvexPolygons(verts1, 0, verts1.length, verts2, 0, verts2.length, mtv)
  }
  def overlapConvexPolygons(verts1: scala.Array[scala.Float], offset1: scala.Int, count1: scala.Int, verts2: scala.Array[scala.Float], offset2: scala.Int, count2: scala.Int, mtv: com.badlogic.gdx.math.Intersector.MinimumTranslationVector): scala.Boolean = {
    var overlaps: scala.Boolean = false
    if (mtv != null) {
      mtv.depth = java.lang.Float.MAX_VALUE
      mtv.normal.setZero()
    } else ()
    overlaps = Intersector.overlapsOnAxisOfShape(verts2, offset2, count2, verts1, offset1, count1, mtv, true)
    if (overlaps) {
      overlaps = Intersector.overlapsOnAxisOfShape(verts1, offset1, count1, verts2, offset2, count2, mtv, false)
    } else ()
    if (!overlaps) {
      if (mtv != null) {
        mtv.depth = 0
        mtv.normal.setZero()
      } else ()
      return false
    } else ()
    return true
  }
  private def overlapsOnAxisOfShape(verts1: scala.Array[scala.Float], offset1: scala.Int, count1: scala.Int, verts2: scala.Array[scala.Float], offset2: scala.Int, count2: scala.Int, mtv: com.badlogic.gdx.math.Intersector.MinimumTranslationVector, shapesShifted: scala.Boolean): scala.Boolean = {
    val endA: scala.Int = offset1 + count1
    val endB: scala.Int = offset2 + count2;
    { var i: scala.Int = offset1; while (i < endA) { {
      val x1: scala.Float = verts1(i)
      val y1: scala.Float = verts1(i + 1)
      val x2: scala.Float = verts1((i + 2) % count1)
      val y2: scala.Float = verts1((i + 3) % count1)
      var axisX: scala.Float = y1 - y2
      var axisY: scala.Float = -(x1 - x2)
      val len: scala.Float = com.badlogic.gdx.math.Vector2.len(axisX, axisY)
      axisX = axisX / len
      axisY = axisY / len
      var minA: scala.Float = java.lang.Float.MAX_VALUE
      var maxA: scala.Float = -java.lang.Float.MAX_VALUE;
      { var v: scala.Int = offset1; while (v < endA) { {
        val p: scala.Float = (verts1(v) * axisX) + (verts1(v + 1) * axisY)
        minA = java.lang.Math.min(minA, p)
        maxA = java.lang.Math.max(maxA, p)
      }; v = v + 2 } }
      var minB: scala.Float = java.lang.Float.MAX_VALUE
      var maxB: scala.Float = -java.lang.Float.MAX_VALUE;
      { var v: scala.Int = offset2; while (v < endB) { {
        val p: scala.Float = (verts2(v) * axisX) + (verts2(v + 1) * axisY)
        minB = java.lang.Math.min(minB, p)
        maxB = java.lang.Math.max(maxB, p)
      }; v = v + 2 } }
      if ((maxA < minB) || (maxB < minA)) {
        return false
      } else {
        if (mtv != null) {
          var o: scala.Float = java.lang.Math.min(maxA, maxB) - java.lang.Math.max(minA, minB)
          val aContainsB: scala.Boolean = (minA < minB) && (maxA > maxB)
          val bContainsA: scala.Boolean = (minB < minA) && (maxB > maxA)
          var mins: scala.Float = 0
          var maxs: scala.Float = 0
          if (aContainsB || bContainsA) {
            mins = java.lang.Math.abs(minA - minB)
            maxs = java.lang.Math.abs(maxA - maxB)
            o = o + java.lang.Math.min(mins, maxs)
          } else ()
          if (mtv.depth > o) {
            mtv.depth = o
            var condition: scala.Boolean = false
            if (shapesShifted) {
              condition = minA < minB
              axisX = if (condition) axisX else -axisX
              axisY = if (condition) axisY else -axisY
            } else {
              condition = minA > minB
              axisX = if (condition) axisX else -axisX
              axisY = if (condition) axisY else -axisY
            }
            if (aContainsB || bContainsA) {
              condition = mins > maxs
              axisX = if (condition) axisX else -axisX
              axisY = if (condition) axisY else -axisY
            } else ()
            mtv.normal.set(axisX, axisY)
          } else ()
        } else ()
      }
    }; i = i + 2 } }
    return true
  }
  def splitTriangle(triangle: scala.Array[scala.Float], plane: com.badlogic.gdx.math.Plane, split: com.badlogic.gdx.math.Intersector.SplitTriangle): scala.Unit = {
    val stride: scala.Int = triangle.length / 3
    val r1: scala.Boolean = plane.testPoint(triangle(0), triangle(1), triangle(2)) == com.badlogic.gdx.math.Plane.PlaneSide.Back
    val r2: scala.Boolean = plane.testPoint(triangle(0 + stride), triangle(1 + stride), triangle(2 + stride)) == com.badlogic.gdx.math.Plane.PlaneSide.Back
    val r3: scala.Boolean = plane.testPoint(triangle(0 + (stride * 2)), triangle(1 + (stride * 2)), triangle(2 + (stride * 2))) == com.badlogic.gdx.math.Plane.PlaneSide.Back
    split.reset()
    if ((r1 == r2) && (r2 == r3)) {
      split.total = 1
      if (r1) {
        split.numBack = 1
        java.lang.System.arraycopy(triangle, 0, split.back, 0, triangle.length)
      } else {
        split.numFront = 1
        java.lang.System.arraycopy(triangle, 0, split.front, 0, triangle.length)
      }
      return
    } else ()
    split.total = 3
    split.numFront = ((if (r1) 0 else 1) + (if (r2) 0 else 1)) + (if (r3) 0 else 1)
    split.numBack = split.total - split.numFront
    split.setSide(!r1)
    var first: scala.Int = 0
    var second: scala.Int = stride
    if (r1 != r2) {
      Intersector.splitEdge(triangle, first, second, stride, plane, split.edgeSplit, 0)
      split.add(triangle, first, stride)
      split.add(split.edgeSplit, 0, stride)
      split.setSide(!split.getSide())
      split.add(split.edgeSplit, 0, stride)
    } else {
      split.add(triangle, first, stride)
    }
    first = stride
    second = stride + stride
    if (r2 != r3) {
      Intersector.splitEdge(triangle, first, second, stride, plane, split.edgeSplit, 0)
      split.add(triangle, first, stride)
      split.add(split.edgeSplit, 0, stride)
      split.setSide(!split.getSide())
      split.add(split.edgeSplit, 0, stride)
    } else {
      split.add(triangle, first, stride)
    }
    first = stride + stride
    second = 0
    if (r3 != r1) {
      Intersector.splitEdge(triangle, first, second, stride, plane, split.edgeSplit, 0)
      split.add(triangle, first, stride)
      split.add(split.edgeSplit, 0, stride)
      split.setSide(!split.getSide())
      split.add(split.edgeSplit, 0, stride)
    } else {
      split.add(triangle, first, stride)
    }
    if (split.numFront == 2) {
      java.lang.System.arraycopy(split.front, stride * 2, split.front, stride * 3, stride * 2)
      java.lang.System.arraycopy(split.front, 0, split.front, stride * 5, stride)
    } else {
      java.lang.System.arraycopy(split.back, stride * 2, split.back, stride * 3, stride * 2)
      java.lang.System.arraycopy(split.back, 0, split.back, stride * 5, stride)
    }
  }
  private def splitEdge(vertices: scala.Array[scala.Float], s: scala.Int, e: scala.Int, stride: scala.Int, plane: com.badlogic.gdx.math.Plane, split: scala.Array[scala.Float], offset: scala.Int): scala.Unit = {
    val t: scala.Float = Intersector.intersectLinePlane(vertices(s), vertices(s + 1), vertices(s + 2), vertices(e), vertices(e + 1), vertices(e + 2), plane, Intersector.intersection)
    split(offset + 0) = Intersector.intersection.x
    split(offset + 1) = Intersector.intersection.y
    split(offset + 2) = Intersector.intersection.z;
    { var i: scala.Int = 3; while (i < stride) { {
      val a: scala.Float = vertices(s + i)
      val b: scala.Float = vertices(e + i)
      split(offset + i) = a + (t * (b - a))
    }; i = i + 1 } }
  }
  def hasOverlap(axes: scala.Array[com.badlogic.gdx.math.Vector3], aVertices: scala.Array[com.badlogic.gdx.math.Vector3], bVertices: scala.Array[com.badlogic.gdx.math.Vector3]): scala.Boolean = {
    for (axis <- axes) {
      var minA: scala.Float = java.lang.Float.MAX_VALUE
      var maxA: scala.Float = -java.lang.Float.MAX_VALUE
      for (aVertex <- aVertices) {
        val p: scala.Float = aVertex.dot(axis)
        minA = java.lang.Math.min(minA, p)
        maxA = java.lang.Math.max(maxA, p)
      }
      var minB: scala.Float = java.lang.Float.MAX_VALUE
      var maxB: scala.Float = -java.lang.Float.MAX_VALUE
      for (bVertex <- bVertices) {
        val p: scala.Float = bVertex.dot(axis)
        minB = java.lang.Math.min(minB, p)
        maxB = java.lang.Math.max(maxB, p)
      }
      if ((maxA < minB) || (maxB < minA)) {
        return false
      } else ()
    }
    return true
  }
  class SplitTriangle(numAttributes: scala.Int) {
    var front: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    var back: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    var edgeSplit: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    var numFront: scala.Int = 0
    var numBack: scala.Int = 0
    var total: scala.Int = 0
    var frontCurrent: scala.Boolean = false
    var frontOffset: scala.Int = 0
    var backOffset: scala.Int = 0
    this.front = new scala.Array[scala.Float]((numAttributes * 3) * 2)
    this.back = new scala.Array[scala.Float]((numAttributes * 3) * 2)
    this.edgeSplit = new scala.Array[scala.Float](numAttributes)
    def toString(): java.lang.String = {
      return ((((((((("SplitTriangle [front=" + java.util.Arrays.toString(this.front)) + ", back=") + java.util.Arrays.toString(this.back)) + ", numFront=") + this.numFront) + ", numBack=") + this.numBack) + ", total=") + this.total) + "]"
    }
    def setSide(front: scala.Boolean): scala.Unit = {
      this.frontCurrent = front
    }
    def getSide(): scala.Boolean = {
      return this.frontCurrent
    }
    def add(vertex: scala.Array[scala.Float], offset: scala.Int, stride: scala.Int): scala.Unit = {
      if (this.frontCurrent) {
        java.lang.System.arraycopy(vertex, offset, this.front, this.frontOffset, stride)
        this.frontOffset = this.frontOffset + stride
      } else {
        java.lang.System.arraycopy(vertex, offset, this.back, this.backOffset, stride)
        this.backOffset = this.backOffset + stride
      }
    }
    def reset(): scala.Unit = {
      this.frontCurrent = false
      this.frontOffset = 0
      this.backOffset = 0
      this.numFront = 0
      this.numBack = 0
      this.total = 0
    }
  }
  class MinimumTranslationVector {
    var normal: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
    var depth: scala.Float = 0
  }
}