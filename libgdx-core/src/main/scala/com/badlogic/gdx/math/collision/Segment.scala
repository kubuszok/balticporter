package com.badlogic.gdx.math.collision

class Segment extends java.io.Serializable {
  final val a: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val b: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def this(a: com.badlogic.gdx.math.Vector3, b: com.badlogic.gdx.math.Vector3) = {
    this()
    this.a.set(a)
    this.b.set(b)
  }
  def this(aX: scala.Float, aY: scala.Float, aZ: scala.Float, bX: scala.Float, bY: scala.Float, bZ: scala.Float) = {
    this()
    this.a.set(aX, aY, aZ)
    this.b.set(bX, bY, bZ)
  }
  def len(): scala.Float = {
    return this.a.dst(this.b)
  }
  def len2(): scala.Float = {
    return this.a.dst2(this.b)
  }
  @java.lang.Override
  override def equals(o: java.lang.Object): scala.Boolean = {
    if (o == this) {
      return true
    } else ()
    if ((o == null) || (o.getClass() != this.getClass())) {
      return false
    } else ()
    val s: Segment = o.asInstanceOf[Segment].asInstanceOf[Segment]
    return this.a.equals(s.a) && this.b.equals(s.b)
  }
  @java.lang.Override
  override def hashCode(): scala.Int = {
    val prime: scala.Int = 71
    var result: scala.Int = 1
    result = (prime * result) + this.a.hashCode()
    result = (prime * result) + this.b.hashCode()
    return result
  }
}
object Segment {
  private final val serialVersionUID: scala.Long = 2739667069736519602L
}