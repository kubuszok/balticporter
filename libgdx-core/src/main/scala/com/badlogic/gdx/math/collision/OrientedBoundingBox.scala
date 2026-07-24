package com.badlogic.gdx.math.collision

class OrientedBoundingBox extends java.io.Serializable {
  private final val bounds: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox()
  final val transform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val inverseTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val axes: scala.Array[com.badlogic.gdx.math.Vector3] = new scala.Array[com.badlogic.gdx.math.Vector3](3)
  private final val vertices: scala.Array[com.badlogic.gdx.math.Vector3] = new scala.Array[com.badlogic.gdx.math.Vector3](8)
  def this(bounds: com.badlogic.gdx.math.collision.BoundingBox, transform: com.badlogic.gdx.math.Matrix4) = {
    this()
    this.bounds.set(bounds.min$field, bounds.max$field)
    this.transform.set(transform)
    this.init()
  }
  def this(bounds: com.badlogic.gdx.math.collision.BoundingBox) = {
    this()
    this.bounds.set(bounds.min$field, bounds.max$field)
    this.init()
  }
  this.bounds.clr()
  this.init()
  private def init(): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.axes.length) { {
      this.axes(i) = new com.badlogic.gdx.math.Vector3()
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < this.vertices.length) { {
      this.vertices(i) = new com.badlogic.gdx.math.Vector3()
    }; i = i + 1 } }
    this.update()
  }
  def getVertices(): scala.Array[com.badlogic.gdx.math.Vector3] = {
    return this.vertices
  }
  def getBounds(): com.badlogic.gdx.math.collision.BoundingBox = {
    return this.bounds
  }
  def setBounds(bounds: com.badlogic.gdx.math.collision.BoundingBox): scala.Unit = {
    this.bounds.set(bounds)
    bounds.getCorner000(this.vertices(0)).mul(this.transform)
    bounds.getCorner001(this.vertices(1)).mul(this.transform)
    bounds.getCorner010(this.vertices(2)).mul(this.transform)
    bounds.getCorner011(this.vertices(3)).mul(this.transform)
    bounds.getCorner100(this.vertices(4)).mul(this.transform)
    bounds.getCorner101(this.vertices(5)).mul(this.transform)
    bounds.getCorner110(this.vertices(6)).mul(this.transform)
    bounds.getCorner111(this.vertices(7)).mul(this.transform)
  }
  def getTransform(): com.badlogic.gdx.math.Matrix4 = {
    return this.transform
  }
  def setTransform(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.transform.set(transform)
    this.update()
  }
  def set(bounds: com.badlogic.gdx.math.collision.BoundingBox, transform: com.badlogic.gdx.math.Matrix4): OrientedBoundingBox = {
    this.setBounds(bounds)
    this.setTransform(transform)
    return this
  }
  def getCorner000(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(0))
  }
  def getCorner001(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(1))
  }
  def getCorner010(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(2))
  }
  def getCorner011(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(3))
  }
  def getCorner100(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(4))
  }
  def getCorner101(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(5))
  }
  def getCorner110(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(6))
  }
  def getCorner111(out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    return out.set(this.vertices(7))
  }
  def contains(v: com.badlogic.gdx.math.Vector3): scala.Boolean = {
    return this.contains(v, this.inverseTransform)
  }
  private def contains(v: com.badlogic.gdx.math.Vector3, invTransform: com.badlogic.gdx.math.Matrix4): scala.Boolean = {
    val localV: com.badlogic.gdx.math.Vector3 = OrientedBoundingBox.tmpVectors(0).set(v).mul(invTransform)
    return this.bounds.contains(localV)
  }
  def contains(b: com.badlogic.gdx.math.collision.BoundingBox): scala.Boolean = {
    val tmpVector: com.badlogic.gdx.math.Vector3 = OrientedBoundingBox.tmpVectors(0)
    return ((((((this.contains(b.getCorner000(tmpVector), this.inverseTransform) && this.contains(b.getCorner001(tmpVector), this.inverseTransform)) && this.contains(b.getCorner010(tmpVector), this.inverseTransform)) && this.contains(b.getCorner011(tmpVector), this.inverseTransform)) && this.contains(b.getCorner100(tmpVector), this.inverseTransform)) && this.contains(b.getCorner101(tmpVector), this.inverseTransform)) && this.contains(b.getCorner110(tmpVector), this.inverseTransform)) && this.contains(b.getCorner111(tmpVector), this.inverseTransform)
  }
  def contains(obb: OrientedBoundingBox): scala.Boolean = {
    return ((((((this.contains(obb.getCorner000(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform) && this.contains(obb.getCorner001(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform)) && this.contains(obb.getCorner010(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform)) && this.contains(obb.getCorner011(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform)) && this.contains(obb.getCorner100(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform)) && this.contains(obb.getCorner101(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform)) && this.contains(obb.getCorner110(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform)) && this.contains(obb.getCorner111(OrientedBoundingBox.tmpVectors(0)), this.inverseTransform)
  }
  def intersects(b: com.badlogic.gdx.math.collision.BoundingBox): scala.Boolean = {
    val aAxes: scala.Array[com.badlogic.gdx.math.Vector3] = this.axes
    OrientedBoundingBox.tempAxes(0) = aAxes(0)
    OrientedBoundingBox.tempAxes(1) = aAxes(1)
    OrientedBoundingBox.tempAxes(2) = aAxes(2)
    OrientedBoundingBox.tempAxes(3) = com.badlogic.gdx.math.Vector3.X
    OrientedBoundingBox.tempAxes(4) = com.badlogic.gdx.math.Vector3.Y
    OrientedBoundingBox.tempAxes(5) = com.badlogic.gdx.math.Vector3.Z
    OrientedBoundingBox.tempAxes(6) = OrientedBoundingBox.tmpVectors(0).set(aAxes(0)).crs(com.badlogic.gdx.math.Vector3.X)
    OrientedBoundingBox.tempAxes(7) = OrientedBoundingBox.tmpVectors(1).set(aAxes(0)).crs(com.badlogic.gdx.math.Vector3.Y)
    OrientedBoundingBox.tempAxes(8) = OrientedBoundingBox.tmpVectors(2).set(aAxes(0)).crs(com.badlogic.gdx.math.Vector3.Z)
    OrientedBoundingBox.tempAxes(9) = OrientedBoundingBox.tmpVectors(3).set(aAxes(1)).crs(com.badlogic.gdx.math.Vector3.X)
    OrientedBoundingBox.tempAxes(10) = OrientedBoundingBox.tmpVectors(4).set(aAxes(1)).crs(com.badlogic.gdx.math.Vector3.Y)
    OrientedBoundingBox.tempAxes(11) = OrientedBoundingBox.tmpVectors(5).set(aAxes(1)).crs(com.badlogic.gdx.math.Vector3.Z)
    OrientedBoundingBox.tempAxes(12) = OrientedBoundingBox.tmpVectors(6).set(aAxes(2)).crs(com.badlogic.gdx.math.Vector3.X)
    OrientedBoundingBox.tempAxes(13) = OrientedBoundingBox.tmpVectors(7).set(aAxes(2)).crs(com.badlogic.gdx.math.Vector3.Y)
    OrientedBoundingBox.tempAxes(14) = OrientedBoundingBox.tmpVectors(8).set(aAxes(2)).crs(com.badlogic.gdx.math.Vector3.Z)
    val aVertices: scala.Array[com.badlogic.gdx.math.Vector3] = this.getVertices()
    val bVertices: scala.Array[com.badlogic.gdx.math.Vector3] = this.getVertices(b)
    return com.badlogic.gdx.math.Intersector.hasOverlap(OrientedBoundingBox.tempAxes, aVertices, bVertices)
  }
  def intersects(obb: OrientedBoundingBox): scala.Boolean = {
    val aAxes: scala.Array[com.badlogic.gdx.math.Vector3] = this.axes
    val bAxes: scala.Array[com.badlogic.gdx.math.Vector3] = obb.axes
    OrientedBoundingBox.tempAxes(0) = aAxes(0)
    OrientedBoundingBox.tempAxes(1) = aAxes(1)
    OrientedBoundingBox.tempAxes(2) = aAxes(2)
    OrientedBoundingBox.tempAxes(3) = bAxes(0)
    OrientedBoundingBox.tempAxes(4) = bAxes(1)
    OrientedBoundingBox.tempAxes(5) = bAxes(2)
    OrientedBoundingBox.tempAxes(6) = OrientedBoundingBox.tmpVectors(0).set(aAxes(0)).crs(bAxes(0))
    OrientedBoundingBox.tempAxes(7) = OrientedBoundingBox.tmpVectors(1).set(aAxes(0)).crs(bAxes(1))
    OrientedBoundingBox.tempAxes(8) = OrientedBoundingBox.tmpVectors(2).set(aAxes(0)).crs(bAxes(2))
    OrientedBoundingBox.tempAxes(9) = OrientedBoundingBox.tmpVectors(3).set(aAxes(1)).crs(bAxes(0))
    OrientedBoundingBox.tempAxes(10) = OrientedBoundingBox.tmpVectors(4).set(aAxes(1)).crs(bAxes(1))
    OrientedBoundingBox.tempAxes(11) = OrientedBoundingBox.tmpVectors(5).set(aAxes(1)).crs(bAxes(2))
    OrientedBoundingBox.tempAxes(12) = OrientedBoundingBox.tmpVectors(6).set(aAxes(2)).crs(bAxes(0))
    OrientedBoundingBox.tempAxes(13) = OrientedBoundingBox.tmpVectors(7).set(aAxes(2)).crs(bAxes(1))
    OrientedBoundingBox.tempAxes(14) = OrientedBoundingBox.tmpVectors(8).set(aAxes(2)).crs(bAxes(2))
    return com.badlogic.gdx.math.Intersector.hasOverlap(OrientedBoundingBox.tempAxes, this.vertices, obb.vertices)
  }
  private def getVertices(b: com.badlogic.gdx.math.collision.BoundingBox): scala.Array[com.badlogic.gdx.math.Vector3] = {
    b.getCorner000(OrientedBoundingBox.tempVertices(0))
    b.getCorner001(OrientedBoundingBox.tempVertices(1))
    b.getCorner010(OrientedBoundingBox.tempVertices(2))
    b.getCorner011(OrientedBoundingBox.tempVertices(3))
    b.getCorner100(OrientedBoundingBox.tempVertices(4))
    b.getCorner101(OrientedBoundingBox.tempVertices(5))
    b.getCorner110(OrientedBoundingBox.tempVertices(6))
    b.getCorner111(OrientedBoundingBox.tempVertices(7))
    return OrientedBoundingBox.tempVertices
  }
  def mul(transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.transform.mul(transform)
    this.update()
  }
  private def update(): scala.Unit = {
    this.bounds.getCorner000(this.vertices(0)).mul(this.transform)
    this.bounds.getCorner001(this.vertices(1)).mul(this.transform)
    this.bounds.getCorner010(this.vertices(2)).mul(this.transform)
    this.bounds.getCorner011(this.vertices(3)).mul(this.transform)
    this.bounds.getCorner100(this.vertices(4)).mul(this.transform)
    this.bounds.getCorner101(this.vertices(5)).mul(this.transform)
    this.bounds.getCorner110(this.vertices(6)).mul(this.transform)
    this.bounds.getCorner111(this.vertices(7)).mul(this.transform)
    this.axes(0).set(this.transform.`val`(com.badlogic.gdx.math.Matrix4.M00), this.transform.`val`(com.badlogic.gdx.math.Matrix4.M10), this.transform.`val`(com.badlogic.gdx.math.Matrix4.M20)).nor()
    this.axes(1).set(this.transform.`val`(com.badlogic.gdx.math.Matrix4.M01), this.transform.`val`(com.badlogic.gdx.math.Matrix4.M11), this.transform.`val`(com.badlogic.gdx.math.Matrix4.M21)).nor()
    this.axes(2).set(this.transform.`val`(com.badlogic.gdx.math.Matrix4.M02), this.transform.`val`(com.badlogic.gdx.math.Matrix4.M12), this.transform.`val`(com.badlogic.gdx.math.Matrix4.M22)).nor()
    this.inverseTransform.set(this.transform).inv()
  }
}
object OrientedBoundingBox {
  private final val serialVersionUID: scala.Long = 3864065514676250557L
  private final val tempAxes: scala.Array[com.badlogic.gdx.math.Vector3] = new scala.Array[com.badlogic.gdx.math.Vector3](15)
  private final val tempVertices: scala.Array[com.badlogic.gdx.math.Vector3] = new scala.Array[com.badlogic.gdx.math.Vector3](8)
  private final val tmpVectors: scala.Array[com.badlogic.gdx.math.Vector3] = new scala.Array[com.badlogic.gdx.math.Vector3](9)
}