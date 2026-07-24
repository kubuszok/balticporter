package com.badlogic.gdx.math.collision

class BoundingBox extends java.io.Serializable {
  final val min$field: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val max$field: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val cnt: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val dim: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def this(bounds: BoundingBox) = {
    this()
    this.set(bounds)
  }
  def this(minimum: com.badlogic.gdx.math.Vector3, maximum: com.badlogic.gdx.math.Vector3) = {
    this()
    this.set(minimum, maximum)
  }
  this.clr()
  def getCenter(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.cnt)
  }
  def getCenterX(): scala.Float = {
    return this.cnt.x
  }
  def getCenterY(): scala.Float = {
    return this.cnt.y
  }
  def getCenterZ(): scala.Float = {
    return this.cnt.z
  }
  def getCorner000(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.min$field.x, this.min$field.y, this.min$field.z)
  }
  def getCorner001(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.min$field.x, this.min$field.y, this.max$field.z)
  }
  def getCorner010(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.min$field.x, this.max$field.y, this.min$field.z)
  }
  def getCorner011(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.min$field.x, this.max$field.y, this.max$field.z)
  }
  def getCorner100(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.max$field.x, this.min$field.y, this.min$field.z)
  }
  def getCorner101(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.max$field.x, this.min$field.y, this.max$field.z)
  }
  def getCorner110(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.max$field.x, this.max$field.y, this.min$field.z)
  }
  def getCorner111(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.max$field.x, this.max$field.y, this.max$field.z)
  }
  def getDimensions(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.dim)
  }
  def getWidth(): scala.Float = {
    return this.dim.x
  }
  def getHeight(): scala.Float = {
    return this.dim.y
  }
  def getDepth(): scala.Float = {
    return this.dim.z
  }
  def getMin(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.min$field)
  }
  def getMax(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.max$field)
  }
  def set(bounds: BoundingBox): BoundingBox = {
    return this.set(bounds.min$field, bounds.max$field)
  }
  def set(minimum: com.badlogic.gdx.math.Vector3, maximum: com.badlogic.gdx.math.Vector3): BoundingBox = {
    this.min$field.set(if (minimum.x < maximum.x) minimum.x else maximum.x, if (minimum.y < maximum.y) minimum.y else maximum.y, if (minimum.z < maximum.z) minimum.z else maximum.z)
    this.max$field.set(if (minimum.x > maximum.x) minimum.x else maximum.x, if (minimum.y > maximum.y) minimum.y else maximum.y, if (minimum.z > maximum.z) minimum.z else maximum.z)
    this.update()
    return this
  }
  def update(): scala.Unit = {
    this.cnt.set(this.min$field).add(this.max$field).scl(0.5f)
    this.dim.set(this.max$field).sub(this.min$field)
  }
  def set(points: scala.Array[com.badlogic.gdx.math.Vector3]): BoundingBox = {
    this.inf()
    for (l_point <- points) {
      this.ext(l_point)
    }
    return this
  }
  def set(points: scala.collection.mutable.Buffer[com.badlogic.gdx.math.Vector3]): BoundingBox = {
    this.inf()
    for (l_point <- points) {
      this.ext(l_point)
    }
    return this
  }
  def inf(): BoundingBox = {
    this.min$field.set(java.lang.Float.POSITIVE_INFINITY, java.lang.Float.POSITIVE_INFINITY, java.lang.Float.POSITIVE_INFINITY)
    this.max$field.set(java.lang.Float.NEGATIVE_INFINITY, java.lang.Float.NEGATIVE_INFINITY, java.lang.Float.NEGATIVE_INFINITY)
    this.cnt.set(0, 0, 0)
    this.dim.set(0, 0, 0)
    return this
  }
  def ext(point: com.badlogic.gdx.math.Vector3): BoundingBox = {
    return this.set(this.min$field.set(BoundingBox.min(this.min$field.x, point.x), BoundingBox.min(this.min$field.y, point.y), BoundingBox.min(this.min$field.z, point.z)), this.max$field.set(java.lang.Math.max(this.max$field.x, point.x), java.lang.Math.max(this.max$field.y, point.y), java.lang.Math.max(this.max$field.z, point.z)))
  }
  def clr(): BoundingBox = {
    return this.set(this.min$field.set(0, 0, 0), this.max$field.set(0, 0, 0))
  }
  def isValid(): scala.Boolean = {
    return ((this.min$field.x <= this.max$field.x) && (this.min$field.y <= this.max$field.y)) && (this.min$field.z <= this.max$field.z)
  }
  def ext(a_bounds: BoundingBox): BoundingBox = {
    return this.set(this.min$field.set(BoundingBox.min(this.min$field.x, a_bounds.min$field.x), BoundingBox.min(this.min$field.y, a_bounds.min$field.y), BoundingBox.min(this.min$field.z, a_bounds.min$field.z)), this.max$field.set(BoundingBox.max(this.max$field.x, a_bounds.max$field.x), BoundingBox.max(this.max$field.y, a_bounds.max$field.y), BoundingBox.max(this.max$field.z, a_bounds.max$field.z)))
  }
  def ext(center: com.badlogic.gdx.math.Vector3, radius: scala.Float): BoundingBox = {
    return this.set(this.min$field.set(BoundingBox.min(this.min$field.x, center.x - radius), BoundingBox.min(this.min$field.y, center.y - radius), BoundingBox.min(this.min$field.z, center.z - radius)), this.max$field.set(BoundingBox.max(this.max$field.x, center.x + radius), BoundingBox.max(this.max$field.y, center.y + radius), BoundingBox.max(this.max$field.z, center.z + radius)))
  }
  def ext(bounds: BoundingBox, transform: com.badlogic.gdx.math.Matrix4): BoundingBox = {
    this.ext(BoundingBox.tmpVector.set(bounds.min$field.x, bounds.min$field.y, bounds.min$field.z).mul(transform))
    this.ext(BoundingBox.tmpVector.set(bounds.min$field.x, bounds.min$field.y, bounds.max$field.z).mul(transform))
    this.ext(BoundingBox.tmpVector.set(bounds.min$field.x, bounds.max$field.y, bounds.min$field.z).mul(transform))
    this.ext(BoundingBox.tmpVector.set(bounds.min$field.x, bounds.max$field.y, bounds.max$field.z).mul(transform))
    this.ext(BoundingBox.tmpVector.set(bounds.max$field.x, bounds.min$field.y, bounds.min$field.z).mul(transform))
    this.ext(BoundingBox.tmpVector.set(bounds.max$field.x, bounds.min$field.y, bounds.max$field.z).mul(transform))
    this.ext(BoundingBox.tmpVector.set(bounds.max$field.x, bounds.max$field.y, bounds.min$field.z).mul(transform))
    this.ext(BoundingBox.tmpVector.set(bounds.max$field.x, bounds.max$field.y, bounds.max$field.z).mul(transform))
    return this
  }
  def mul(transform: com.badlogic.gdx.math.Matrix4): BoundingBox = {
    val x0: scala.Float = this.min$field.x
    val y0: scala.Float = this.min$field.y
    val z0: scala.Float = this.min$field.z
    val x1: scala.Float = this.max$field.x
    val y1: scala.Float = this.max$field.y
    val z1: scala.Float = this.max$field.z
    this.inf()
    this.ext(BoundingBox.tmpVector.set(x0, y0, z0).mul(transform))
    this.ext(BoundingBox.tmpVector.set(x0, y0, z1).mul(transform))
    this.ext(BoundingBox.tmpVector.set(x0, y1, z0).mul(transform))
    this.ext(BoundingBox.tmpVector.set(x0, y1, z1).mul(transform))
    this.ext(BoundingBox.tmpVector.set(x1, y0, z0).mul(transform))
    this.ext(BoundingBox.tmpVector.set(x1, y0, z1).mul(transform))
    this.ext(BoundingBox.tmpVector.set(x1, y1, z0).mul(transform))
    this.ext(BoundingBox.tmpVector.set(x1, y1, z1).mul(transform))
    return this
  }
  def contains(b: BoundingBox): scala.Boolean = {
    return (!this.isValid()) || ((((((this.min$field.x <= b.min$field.x) && (this.min$field.y <= b.min$field.y)) && (this.min$field.z <= b.min$field.z)) && (this.max$field.x >= b.max$field.x)) && (this.max$field.y >= b.max$field.y)) && (this.max$field.z >= b.max$field.z))
  }
  def contains(obb: com.badlogic.gdx.math.collision.OrientedBoundingBox): scala.Boolean = {
    return ((((((this.contains(obb.getCorner000(BoundingBox.tmpVector)) && this.contains(obb.getCorner001(BoundingBox.tmpVector))) && this.contains(obb.getCorner010(BoundingBox.tmpVector))) && this.contains(obb.getCorner011(BoundingBox.tmpVector))) && this.contains(obb.getCorner100(BoundingBox.tmpVector))) && this.contains(obb.getCorner101(BoundingBox.tmpVector))) && this.contains(obb.getCorner110(BoundingBox.tmpVector))) && this.contains(obb.getCorner111(BoundingBox.tmpVector))
  }
  def intersects(b: BoundingBox): scala.Boolean = {
    if (!this.isValid()) {
      return false
    } else ()
    val lx: scala.Float = java.lang.Math.abs(this.cnt.x - b.cnt.x)
    val sumx: scala.Float = (this.dim.x / 2.0f) + (b.dim.x / 2.0f)
    val ly: scala.Float = java.lang.Math.abs(this.cnt.y - b.cnt.y)
    val sumy: scala.Float = (this.dim.y / 2.0f) + (b.dim.y / 2.0f)
    val lz: scala.Float = java.lang.Math.abs(this.cnt.z - b.cnt.z)
    val sumz: scala.Float = (this.dim.z / 2.0f) + (b.dim.z / 2.0f)
    return ((lx <= sumx) && (ly <= sumy)) && (lz <= sumz)
  }
  def contains(v: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    return (((((this.min$field.x <= v.x) && (this.max$field.x >= v.x)) && (this.min$field.y <= v.y)) && (this.max$field.y >= v.y)) && (this.min$field.z <= v.z)) && (this.max$field.z >= v.z)
  }
  def toString(): java.lang.String = {
    return ((("[" + this.min$field) + "|") + this.max$field) + "]"
  }
  def ext(x: scala.Float, y: scala.Float, z: scala.Float): BoundingBox = {
    return this.set(this.min$field.set(BoundingBox.min(this.min$field.x, x), BoundingBox.min(this.min$field.y, y), BoundingBox.min(this.min$field.z, z)), this.max$field.set(BoundingBox.max(this.max$field.x, x), BoundingBox.max(this.max$field.y, y), BoundingBox.max(this.max$field.z, z)))
  }
}
object BoundingBox {
  private final val serialVersionUID: scala.Long = -1286036817192127343L
  private final val tmpVector: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final def min(a: scala.Float, b: scala.Float): scala.Float = {
    return if (a > b) b else a
  }
  final def max(a: scala.Float, b: scala.Float): scala.Float = {
    return if (a > b) a else b
  }
}