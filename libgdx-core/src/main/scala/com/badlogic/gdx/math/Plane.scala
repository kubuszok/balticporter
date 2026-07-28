package com.badlogic.gdx.math

class Plane extends java.io.Serializable {
  final val normal: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var d: scala.Float = 0
  def this(normal: com.badlogic.gdx.math.Vector3, d: scala.Float) = {
    this()
    this.normal.set(normal).nor()
    this.d = d
  }
  def this(normal: com.badlogic.gdx.math.Vector3, point: com.badlogic.gdx.math.Vector3) = {
    this()
    this.normal.set(normal).nor()
    this.d = -this.normal.dot(point)
  }
  def this(point1: com.badlogic.gdx.math.Vector3, point2: com.badlogic.gdx.math.Vector3, point3: com.badlogic.gdx.math.Vector3) = {
    this()
    this.set(point1, point2, point3)
  }
  def set(point1: com.badlogic.gdx.math.Vector3, point2: com.badlogic.gdx.math.Vector3, point3: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.normal.set(point1).sub(point2).crs(point2.x - point3.x, point2.y - point3.y, point2.z - point3.z).nor()
    this.d = -point1.dot(this.normal)
  }
  def set(nx: scala.Float, ny: scala.Float, nz: scala.Float, d: scala.Float): scala.Unit = {
    this.normal.set(nx, ny, nz)
    this.d = d
  }
  def distance(point: com.badlogic.gdx.math.Vector3): scala.Float = {
    return this.normal.dot(point) + this.d
  }
  def testPoint(point: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Plane.PlaneSide = {
    val dist: scala.Float = this.normal.dot(point) + this.d
    if (dist == 0) {
      return com.badlogic.gdx.math.Plane.PlaneSide.OnPlane
    } else {
      if (dist < 0) {
        return com.badlogic.gdx.math.Plane.PlaneSide.Back
      } else {
        return com.badlogic.gdx.math.Plane.PlaneSide.Front
      }
    }
  }
  def testPoint(x: scala.Float, y: scala.Float, z: scala.Float): com.badlogic.gdx.math.Plane.PlaneSide = {
    val dist: scala.Float = this.normal.dot(x, y, z) + this.d
    if (dist == 0) {
      return com.badlogic.gdx.math.Plane.PlaneSide.OnPlane
    } else {
      if (dist < 0) {
        return com.badlogic.gdx.math.Plane.PlaneSide.Back
      } else {
        return com.badlogic.gdx.math.Plane.PlaneSide.Front
      }
    }
  }
  def isFrontFacing(direction: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    val dot: scala.Float = this.normal.dot(direction)
    return dot <= 0
  }
  def getNormal(): com.badlogic.gdx.math.Vector3 = {
    return this.normal
  }
  def getD(): scala.Float = {
    return this.d
  }
  def set(point: com.badlogic.gdx.math.Vector3, normal: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.normal.set(normal)
    this.d = -point.dot(normal)
  }
  def set(pointX: scala.Float, pointY: scala.Float, pointZ: scala.Float, norX: scala.Float, norY: scala.Float, norZ: scala.Float): scala.Unit = {
    this.normal.set(norX, norY, norZ)
    this.d = -(((pointX * norX) + (pointY * norY)) + (pointZ * norZ))
  }
  def set(plane: Plane): scala.Unit = {
    this.normal.set(plane.normal)
    this.d = plane.d
  }
  override def toString(): java.lang.String = {
    return (this.normal.toString() + ", ") + this.d
  }
}
object Plane {
  private final val serialVersionUID: scala.Long = -1240652082930747866L
  sealed abstract class PlaneSide {
    def name(): java.lang.String = this.toString()
  }
  object PlaneSide {
    case object OnPlane extends PlaneSide
    case object Back extends PlaneSide
    case object Front extends PlaneSide
    def values(): scala.Array[PlaneSide] = scala.Array(OnPlane, Back, Front)
    def valueOf(name: java.lang.String): PlaneSide = name match {
      case "OnPlane" => OnPlane
      case "Back" => Back
      case "Front" => Front
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}