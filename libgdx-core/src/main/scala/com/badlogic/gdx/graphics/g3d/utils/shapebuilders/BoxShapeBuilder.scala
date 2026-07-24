package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class BoxShapeBuilder extends com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder
object BoxShapeBuilder {
  export com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.{build => _, *}
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, box: com.badlogic.gdx.math.collision.BoundingBox): scala.Unit = {
    builder.box(box.getCorner000(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()), box.getCorner010(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()), box.getCorner100(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()), box.getCorner110(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()), box.getCorner001(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()), box.getCorner011(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()), box.getCorner101(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()), box.getCorner111(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3()))
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.freeAll()
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, corner000: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner010: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner100: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner110: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner001: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner011: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner101: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo, corner111: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo): scala.Unit = {
    builder.ensureVertices(8)
    val i000: scala.Short = builder.vertex(corner000)
    val i100: scala.Short = builder.vertex(corner100)
    val i110: scala.Short = builder.vertex(corner110)
    val i010: scala.Short = builder.vertex(corner010)
    val i001: scala.Short = builder.vertex(corner001)
    val i101: scala.Short = builder.vertex(corner101)
    val i111: scala.Short = builder.vertex(corner111)
    val i011: scala.Short = builder.vertex(corner011)
    val primitiveType: scala.Int = builder.getPrimitiveType()
    if (primitiveType == com.badlogic.gdx.graphics.GL20.GL_LINES) {
      builder.ensureIndices(24)
      builder.rect(i000, i100, i110, i010)
      builder.rect(i101, i001, i011, i111)
      builder.index(i000, i001, i010, i011, i110, i111, i100, i101)
    } else {
      if (primitiveType == com.badlogic.gdx.graphics.GL20.GL_POINTS) {
        builder.ensureRectangleIndices(2)
        builder.rect(i000, i100, i110, i010)
        builder.rect(i101, i001, i011, i111)
      } else {
        builder.ensureRectangleIndices(6)
        builder.rect(i000, i100, i110, i010)
        builder.rect(i101, i001, i011, i111)
        builder.rect(i000, i010, i011, i001)
        builder.rect(i101, i111, i110, i100)
        builder.rect(i101, i100, i000, i001)
        builder.rect(i110, i111, i011, i010)
      }
    }
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, corner000: com.badlogic.gdx.math.Vector3, corner010: com.badlogic.gdx.math.Vector3, corner100: com.badlogic.gdx.math.Vector3, corner110: com.badlogic.gdx.math.Vector3, corner001: com.badlogic.gdx.math.Vector3, corner011: com.badlogic.gdx.math.Vector3, corner101: com.badlogic.gdx.math.Vector3, corner111: com.badlogic.gdx.math.Vector3): scala.Unit = {
    if ((builder.getAttributes().getMask() & (((com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal | com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal) | com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent) | com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates)) == 0) {
      BoxShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp1.set(corner000, null, null, null), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp2.set(corner010, null, null, null), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp3.set(corner100, null, null, null), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp4.set(corner110, null, null, null), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp5.set(corner001, null, null, null), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp6.set(corner011, null, null, null), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp7.set(corner101, null, null, null), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.vertTmp8.set(corner111, null, null, null))
    } else {
      builder.ensureVertices(24)
      builder.ensureRectangleIndices(6)
      var nor: com.badlogic.gdx.math.Vector3 = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(corner000).lerp(corner110, 0.5f).sub(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.set(corner001).lerp(corner111, 0.5f)).nor()
      builder.rect(corner000, corner010, corner110, corner100, nor)
      builder.rect(corner011, corner001, corner101, corner111, nor.scl(-1))
      nor = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(corner000).lerp(corner101, 0.5f).sub(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.set(corner010).lerp(corner111, 0.5f)).nor()
      builder.rect(corner001, corner000, corner100, corner101, nor)
      builder.rect(corner010, corner011, corner111, corner110, nor.scl(-1))
      nor = com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV1.set(corner000).lerp(corner011, 0.5f).sub(com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.tmpV2.set(corner100).lerp(corner111, 0.5f)).nor()
      builder.rect(corner001, corner011, corner010, corner000, nor)
      builder.rect(corner100, corner110, corner111, corner101, nor.scl(-1))
    }
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    BoxShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(-0.5f, -0.5f, -0.5f).mul(transform), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(-0.5f, 0.5f, -0.5f).mul(transform), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(0.5f, -0.5f, -0.5f).mul(transform), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(0.5f, 0.5f, -0.5f).mul(transform), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(-0.5f, -0.5f, 0.5f).mul(transform), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(-0.5f, 0.5f, 0.5f).mul(transform), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(0.5f, -0.5f, 0.5f).mul(transform), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(0.5f, 0.5f, 0.5f).mul(transform))
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.freeAll()
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, width: scala.Float, height: scala.Float, depth: scala.Float): scala.Unit = {
    BoxShapeBuilder.build(builder, 0, 0, 0, width, height, depth)
  }
  def build(builder: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder, x: scala.Float, y: scala.Float, z: scala.Float, width: scala.Float, height: scala.Float, depth: scala.Float): scala.Unit = {
    val hw: scala.Float = width * 0.5f
    val hh: scala.Float = height * 0.5f
    val hd: scala.Float = depth * 0.5f
    val x0: scala.Float = x - hw
    val y0: scala.Float = y - hh
    val z0: scala.Float = z - hd
    val x1: scala.Float = x + hw
    val y1: scala.Float = y + hh
    val z1: scala.Float = z + hd
    BoxShapeBuilder.build(builder, com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x0, y0, z0), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x0, y1, z0), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x1, y0, z0), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x1, y1, z0), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x0, y0, z1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x0, y1, z1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x1, y0, z1), com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.obtainV3().set(x1, y1, z1))
    com.badlogic.gdx.graphics.g3d.utils.shapebuilders.BaseShapeBuilder.freeAll()
  }
}