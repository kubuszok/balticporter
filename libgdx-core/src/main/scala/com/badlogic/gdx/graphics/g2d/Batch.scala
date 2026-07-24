package com.badlogic.gdx.graphics.g2d

trait Batch extends com.badlogic.gdx.utils.Disposable {
  def begin(): scala.Unit
  def `end`(): scala.Unit
  def setColor(tint: com.badlogic.gdx.graphics.Color): scala.Unit
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit
  def getColor(): com.badlogic.gdx.graphics.Color
  def setPackedColor(packedColor: scala.Float): scala.Unit
  def getPackedColor(): scala.Float
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int, flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, srcX: scala.Int, srcY: scala.Int, srcWidth: scala.Int, srcHeight: scala.Int): scala.Unit
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float, u: scala.Float, v: scala.Float, u2: scala.Float, v2: scala.Float): scala.Unit
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float): scala.Unit
  def draw(texture: com.badlogic.gdx.graphics.Texture, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit
  def draw(texture: com.badlogic.gdx.graphics.Texture, spriteVertices: scala.Array[scala.Float], offset: scala.Int, count: scala.Int): scala.Unit
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float): scala.Unit
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float): scala.Unit
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, x: scala.Float, y: scala.Float, originX: scala.Float, originY: scala.Float, width: scala.Float, height: scala.Float, scaleX: scala.Float, scaleY: scala.Float, rotation: scala.Float, clockwise: scala.Boolean): scala.Unit
  def draw(region: com.badlogic.gdx.graphics.g2d.TextureRegion, width: scala.Float, height: scala.Float, transform: com.badlogic.gdx.math.Affine2): scala.Unit
  def flush(): scala.Unit
  def disableBlending(): scala.Unit
  def enableBlending(): scala.Unit
  def setBlendFunction(srcFunc: scala.Int, dstFunc: scala.Int): scala.Unit
  def setBlendFunctionSeparate(srcFuncColor: scala.Int, dstFuncColor: scala.Int, srcFuncAlpha: scala.Int, dstFuncAlpha: scala.Int): scala.Unit
  def getBlendSrcFunc(): scala.Int
  def getBlendDstFunc(): scala.Int
  def getBlendSrcFuncAlpha(): scala.Int
  def getBlendDstFuncAlpha(): scala.Int
  def getProjectionMatrix(): com.badlogic.gdx.math.Matrix4
  def getTransformMatrix(): com.badlogic.gdx.math.Matrix4
  def setProjectionMatrix(projection: com.badlogic.gdx.math.Matrix4): scala.Unit
  def setTransformMatrix(transform: com.badlogic.gdx.math.Matrix4): scala.Unit
  def setShader(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram): scala.Unit
  def getShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram
  def isBlendingEnabled(): scala.Boolean
  def isDrawing(): scala.Boolean
}
object Batch {
  final val X1: scala.Int = 0
  final val Y1: scala.Int = 1
  final val C1: scala.Int = 2
  final val U1: scala.Int = 3
  final val V1: scala.Int = 4
  final val X2: scala.Int = 5
  final val Y2: scala.Int = 6
  final val C2: scala.Int = 7
  final val U2: scala.Int = 8
  final val V2: scala.Int = 9
  final val X3: scala.Int = 10
  final val Y3: scala.Int = 11
  final val C3: scala.Int = 12
  final val U3: scala.Int = 13
  final val V3: scala.Int = 14
  final val X4: scala.Int = 15
  final val Y4: scala.Int = 16
  final val C4: scala.Int = 17
  final val U4: scala.Int = 18
  final val V4: scala.Int = 19
}