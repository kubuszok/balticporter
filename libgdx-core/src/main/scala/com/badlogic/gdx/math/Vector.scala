package com.badlogic.gdx.math

trait Vector[T <: Vector[T]] {
  def cpy(): T
  def len(): scala.Float
  def len2(): scala.Float
  def limit(limit: scala.Float): T
  def limit2(limit2: scala.Float): T
  def setLength(len: scala.Float): T
  def setLength2(len2: scala.Float): T
  def clamp(min: scala.Float, max: scala.Float): T
  def set(v: T): T
  def sub(v: T): T
  def nor(): T
  def add(v: T): T
  def dot(v: T): scala.Float
  def scl(scalar: scala.Float): T
  def scl(v: T): T
  def dst(v: T): scala.Float
  def dst2(v: T): scala.Float
  def lerp(target: T, alpha: scala.Float): T
  def interpolate(target: T, alpha: scala.Float, interpolator: com.badlogic.gdx.math.Interpolation): T
  def setToRandomDirection(): T
  def isUnit(): scala.Boolean
  def isUnit(margin: scala.Float): scala.Boolean
  def isZero(): scala.Boolean
  def isZero(margin: scala.Float): scala.Boolean
  def isOnLine(other: T, epsilon: scala.Float): scala.Boolean
  def isOnLine(other: T): scala.Boolean
  def isCollinear(other: T, epsilon: scala.Float): scala.Boolean
  def isCollinear(other: T): scala.Boolean
  def isCollinearOpposite(other: T, epsilon: scala.Float): scala.Boolean
  def isCollinearOpposite(other: T): scala.Boolean
  def isPerpendicular(other: T): scala.Boolean
  def isPerpendicular(other: T, epsilon: scala.Float): scala.Boolean
  def hasSameDirection(other: T): scala.Boolean
  def hasOppositeDirection(other: T): scala.Boolean
  def epsilonEquals(other: T, epsilon: scala.Float): scala.Boolean
  def mulAdd(v: T, scalar: scala.Float): T
  def mulAdd(v: T, mulVec: T): T
  def setZero(): T
}