package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class RenderableShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object RenderableShapeBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.{FLOAT_BYTES => _, RenderablePool => _, buildNormals => _, ensureIndicesCapacity => _, ensureVerticesCapacity => _, indices => _, maxVerticeInIndices => _, minVerticeInIndices => _, renderables => _, renderablesPool => _, vertices => _, *}
  private var indices: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private final val renderablesPool: com.badlogic.gdx.graphics.g3d.utils.shapebuilders.RenderableShapeBuilder.RenderablePool = new com.badlogic.gdx.graphics.g3d.utils.shapebuilders.RenderableShapeBuilder.RenderablePool()
  private final val renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]()
  private final val FLOAT_BYTES: scala.Int = 4
  def buildNormals(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, renderableProvider: com.badlogic.gdx.graphics.g3d.RenderableProvider, vectorSize: scala.Float): scala.Unit = {
    RenderableShapeBuilder.buildNormals(builder, renderableProvider, vectorSize, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor0.set(0, 0, 1, 1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor1.set(1, 0, 0, 1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpColor2.set(0, 1, 0, 1))
  }
  def buildNormals(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, renderableProvider: com.badlogic.gdx.graphics.g3d.RenderableProvider, vectorSize: scala.Float, normalColor: com.badlogic.gdx.graphics.Color, tangentColor: com.badlogic.gdx.graphics.Color, binormalColor: com.badlogic.gdx.graphics.Color): scala.Unit = {
    renderableProvider.getRenderables(RenderableShapeBuilder.renderables, RenderableShapeBuilder.renderablesPool)
    for (renderable <- RenderableShapeBuilder.renderables) {
      RenderableShapeBuilder.buildNormals(builder, renderable, vectorSize, normalColor, tangentColor, binormalColor)
    }
    RenderableShapeBuilder.renderablesPool.flush()
    RenderableShapeBuilder.renderables.clear()
  }
  def buildNormals(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, renderable: com.badlogic.gdx.graphics.g3d.Renderable, vectorSize: scala.Float, normalColor: com.badlogic.gdx.graphics.Color, tangentColor: com.badlogic.gdx.graphics.Color, binormalColor: com.badlogic.gdx.graphics.Color): scala.Unit = {
    val mesh: com.badlogic.gdx.graphics.Mesh = renderable.meshPart.mesh
    var positionOffset: scala.Int = -1
    if (mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position) != null) {
      positionOffset = mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position).offset / RenderableShapeBuilder.FLOAT_BYTES
    } else ()
    var normalOffset: scala.Int = -1
    if (mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal) != null) {
      normalOffset = mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal).offset / RenderableShapeBuilder.FLOAT_BYTES
    } else ()
    var tangentOffset: scala.Int = -1
    if (mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent) != null) {
      tangentOffset = mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent).offset / RenderableShapeBuilder.FLOAT_BYTES
    } else ()
    var binormalOffset: scala.Int = -1
    if (mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal) != null) {
      binormalOffset = mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal).offset / RenderableShapeBuilder.FLOAT_BYTES
    } else ()
    val attributesSize: scala.Int = mesh.getVertexSize() / RenderableShapeBuilder.FLOAT_BYTES
    var verticesOffset: scala.Int = 0
    var verticesQuantity: scala.Int = 0
    if (mesh.getNumIndices() > 0) {
      RenderableShapeBuilder.ensureIndicesCapacity(mesh.getNumIndices())
      mesh.getIndices(renderable.meshPart.offset, renderable.meshPart.size, RenderableShapeBuilder.indices, 0)
      val minVertice: scala.Short = RenderableShapeBuilder.minVerticeInIndices()
      val maxVertice: scala.Short = RenderableShapeBuilder.maxVerticeInIndices()
      verticesOffset = minVertice
      verticesQuantity = maxVertice - minVertice
    } else {
      verticesOffset = renderable.meshPart.offset
      verticesQuantity = renderable.meshPart.size
    }
    RenderableShapeBuilder.ensureVerticesCapacity(verticesQuantity * attributesSize)
    mesh.getVertices(verticesOffset * attributesSize, verticesQuantity * attributesSize, RenderableShapeBuilder.vertices, 0);
    { var i: scala.Int = verticesOffset; while (i < verticesQuantity) { {
      val id: scala.Int = i * attributesSize
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0.set(RenderableShapeBuilder.vertices(id + positionOffset), RenderableShapeBuilder.vertices((id + positionOffset) + 1), RenderableShapeBuilder.vertices((id + positionOffset) + 2))
      if (normalOffset != (-1)) {
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(RenderableShapeBuilder.vertices(id + normalOffset), RenderableShapeBuilder.vertices((id + normalOffset) + 1), RenderableShapeBuilder.vertices((id + normalOffset) + 2))
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.set(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0).add(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.scl(vectorSize))
      } else ()
      if (tangentOffset != (-1)) {
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV3.set(RenderableShapeBuilder.vertices(id + tangentOffset), RenderableShapeBuilder.vertices((id + tangentOffset) + 1), RenderableShapeBuilder.vertices((id + tangentOffset) + 2))
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV4.set(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0).add(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV3.scl(vectorSize))
      } else ()
      if (binormalOffset != (-1)) {
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV5.set(RenderableShapeBuilder.vertices(id + binormalOffset), RenderableShapeBuilder.vertices((id + binormalOffset) + 1), RenderableShapeBuilder.vertices((id + binormalOffset) + 2))
        com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV6.set(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0).add(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV5.scl(vectorSize))
      } else ()
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0.mul(renderable.worldTransform)
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.mul(renderable.worldTransform)
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV4.mul(renderable.worldTransform)
      com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV6.mul(renderable.worldTransform)
      if (normalOffset != (-1)) {
        builder.setColor(normalColor)
        builder.line(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2)
      } else ()
      if (tangentOffset != (-1)) {
        builder.setColor(tangentColor)
        builder.line(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV4)
      } else ()
      if (binormalOffset != (-1)) {
        builder.setColor(binormalColor)
        builder.line(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV0, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV6)
      } else ()
    }; i = i + 1 } }
  }
  private def ensureVerticesCapacity(capacity: scala.Int): scala.Unit = {
    if ((RenderableShapeBuilder.vertices == null) || (RenderableShapeBuilder.vertices.length < capacity)) {
      RenderableShapeBuilder.vertices = new scala.Array[scala.Float](capacity)
    } else ()
  }
  private def ensureIndicesCapacity(capacity: scala.Int): scala.Unit = {
    if ((RenderableShapeBuilder.indices == null) || (RenderableShapeBuilder.indices.length < capacity)) {
      RenderableShapeBuilder.indices = new scala.Array[scala.Short](capacity)
    } else ()
  }
  private def minVerticeInIndices(): scala.Short = {
    var min: scala.Short = 32767.asInstanceOf[scala.Short].asInstanceOf[scala.Short];
    { var i: scala.Int = 0; while (i < RenderableShapeBuilder.indices.length) { {
      if (RenderableShapeBuilder.indices(i) < min) {
        min = RenderableShapeBuilder.indices(i)
      } else ()
    }; i = i + 1 } }
    return min
  }
  private def maxVerticeInIndices(): scala.Short = {
    var max: scala.Short = (-32768).asInstanceOf[scala.Short].asInstanceOf[scala.Short];
    { var i: scala.Int = 0; while (i < RenderableShapeBuilder.indices.length) { {
      if (RenderableShapeBuilder.indices(i) > max) {
        max = RenderableShapeBuilder.indices(i)
      } else ()
    }; i = i + 1 } }
    return max
  }
  class RenderablePool extends com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g3d.Renderable] {
    def newObject(): com.badlogic.gdx.graphics.g3d.Renderable = {
      return new com.badlogic.gdx.graphics.g3d.Renderable()
    }
    def obtain(): com.badlogic.gdx.graphics.g3d.Renderable = {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = super.obtain()
      renderable.environment = null
      renderable.material = null
      renderable.meshPart.set("", null, 0, 0, 0)
      renderable.shader = null
      renderable.userData = null
      return renderable
    }
  }
  object RenderablePool {
    export com.badlogic.gdx.utils.FlushablePool.*
  }
}