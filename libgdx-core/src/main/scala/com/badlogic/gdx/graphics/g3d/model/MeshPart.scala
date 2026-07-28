package com.badlogic.gdx.graphics.g3d.model

class MeshPart {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  var primitiveType: scala.Int = 0
  var offset: scala.Int = 0
  var size: scala.Int = 0
  var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  final val center: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val halfExtents: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var radius: scala.Float = -1
  def this(id: java.lang.String, mesh: com.badlogic.gdx.graphics.Mesh, offset: scala.Int, size: scala.Int, `type`: scala.Int) = {
    this()
    this.set(id, mesh, offset, size, `type`)
  }
  def this(copyFrom: MeshPart) = {
    this()
    this.set(copyFrom)
  }
  def set(other: MeshPart): MeshPart = {
    this.id = other.id
    this.mesh = other.mesh
    this.offset = other.offset
    this.size = other.size
    this.primitiveType = other.primitiveType
    this.center.set(other.center)
    this.halfExtents.set(other.halfExtents)
    this.radius = other.radius
    return this
  }
  def set(id: java.lang.String, mesh: com.badlogic.gdx.graphics.Mesh, offset: scala.Int, size: scala.Int, `type`: scala.Int): MeshPart = {
    this.id = id
    this.mesh = mesh
    this.offset = offset
    this.size = size
    this.primitiveType = `type`
    this.center.set(0, 0, 0)
    this.halfExtents.set(0, 0, 0)
    this.radius = -1.0f
    return this
  }
  def update(): scala.Unit = {
    this.mesh.calculateBoundingBox(MeshPart.bounds, this.offset, this.size)
    MeshPart.bounds.getCenter(this.center)
    MeshPart.bounds.getDimensions(this.halfExtents).scl(0.5f)
    this.radius = this.halfExtents.len()
  }
  def equals(other: MeshPart): scala.Boolean = {
    return (other == this) || (((((other != null) && (other.mesh == this.mesh)) && (other.primitiveType == this.primitiveType)) && (other.offset == this.offset)) && (other.size == this.size))
  }
  @java.lang.Override
  def equals(arg0: java.lang.Object): scala.Boolean = {
    if (arg0 == null) {
      return false
    } else ()
    if (arg0 == this) {
      return true
    } else ()
    if (!arg0.isInstanceOf[MeshPart]) {
      return false
    } else ()
    return this.equals(arg0.asInstanceOf[MeshPart].asInstanceOf[MeshPart])
  }
  def render(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, autoBind: scala.Boolean): scala.Unit = {
    this.mesh.render(shader, this.primitiveType, this.offset, this.size, autoBind)
  }
  def render(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit = {
    this.mesh.render(shader, this.primitiveType, this.offset, this.size)
  }
}
object MeshPart {
  private final val bounds: com.badlogic.gdx.math.collision.BoundingBox = new com.badlogic.gdx.math.collision.BoundingBox()
}