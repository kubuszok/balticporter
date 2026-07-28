package com.badlogic.gdx.graphics.g3d.utils

class ShapeCache(maxVertices: scala.Int, maxIndices: scala.Int, attributes: com.badlogic.gdx.graphics.VertexAttributes, primitiveType: scala.Int) extends com.badlogic.gdx.utils.Disposable with com.badlogic.gdx.graphics.g3d.RenderableProvider {
  private var builder: com.badlogic.gdx.graphics.g3d.utils.MeshBuilder = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.MeshBuilder]
  private var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  private var building: scala.Boolean = false
  private final val id: java.lang.String = "id"
  private final val renderable: com.badlogic.gdx.graphics.g3d.Renderable = new com.badlogic.gdx.graphics.g3d.Renderable()
  def this() = {
    this(5000, 5000, new com.badlogic.gdx.graphics.VertexAttributes(scala.Array[com.badlogic.gdx.graphics.VertexAttribute](new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, "a_position"), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, "a_color"))), com.badlogic.gdx.graphics.GL20.GL_LINES)
  }
  this.mesh = new com.badlogic.gdx.graphics.Mesh(false, maxVertices, maxIndices, attributes)
  this.builder = new com.badlogic.gdx.graphics.g3d.utils.MeshBuilder()
  this.renderable.meshPart.mesh = this.mesh
  this.renderable.meshPart.primitiveType = primitiveType
  this.renderable.material = new com.badlogic.gdx.graphics.g3d.Material()
  def begin(): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder = {
    return this.begin(com.badlogic.gdx.graphics.GL20.GL_LINES)
  }
  def begin(primitiveType: scala.Int): com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder = {
    if (this.building) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call end() after calling begin()")
    } else ()
    this.building = true
    this.builder.begin(this.mesh.getVertexAttributes())
    this.builder.part(this.id, primitiveType, this.renderable.meshPart)
    return this.builder
  }
  def `end`(): scala.Unit = {
    if (!this.building) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call begin() prior to calling end()")
    } else ()
    this.building = false
    this.builder.`end`(this.mesh)
  }
  @java.lang.Override
  override def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    renderables.add(this.renderable)
  }
  def getMaterial(): com.badlogic.gdx.graphics.g3d.Material = {
    return this.renderable.material
  }
  def getWorldTransform(): com.badlogic.gdx.math.Matrix4 = {
    return this.renderable.worldTransform
  }
  @java.lang.Override
  override def dispose(): scala.Unit = {
    this.mesh.dispose()
  }
}