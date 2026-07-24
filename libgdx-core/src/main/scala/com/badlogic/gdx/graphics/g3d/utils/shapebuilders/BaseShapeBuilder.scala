package com.badlogic.gdx.graphics.g3d.utils.shapebuilders

class BaseShapeBuilder
object BaseShapeBuilder {
  final val tmpColor0: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  final val tmpColor1: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  final val tmpColor2: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  final val tmpColor3: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  final val tmpColor4: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  final val tmpV0: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpV1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpV2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpV3: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpV4: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpV5: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpV6: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val tmpV7: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val vertTmp0: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp1: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp2: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp3: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp4: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp5: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp6: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp7: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val vertTmp8: com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo = new com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder.VertexInfo()
  final val matTmp1: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val vectorPool: com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.math.Vector3] = new com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.math.Vector3]()
  private final val matrices4Pool: com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.math.Matrix4] = new com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.math.Matrix4]()
  def obtainV3(): com.badlogic.gdx.math.Vector3 = {
    return BaseShapeBuilder.vectorPool.obtain()
  }
  def obtainM4(): com.badlogic.gdx.math.Matrix4 = {
    val result: com.badlogic.gdx.math.Matrix4 = BaseShapeBuilder.matrices4Pool.obtain()
    return result
  }
  def freeAll(): scala.Unit = {
    BaseShapeBuilder.vectorPool.flush()
    BaseShapeBuilder.matrices4Pool.flush()
  }
}