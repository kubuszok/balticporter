package com.badlogic.gdx.scenes.scene2d.utils

object ScissorStack {
  private var scissors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.Rectangle] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.Rectangle]()
  var tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val viewport: com.badlogic.gdx.math.Rectangle = new com.badlogic.gdx.math.Rectangle()
  def pushScissors(scissor: com.badlogic.gdx.math.Rectangle): scala.Boolean = {
    ScissorStack.fix(scissor)
    if (ScissorStack.scissors.size == 0) {
      if ((scissor.width < 1) || (scissor.height < 1)) {
        return false
      } else ()
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST)
    } else {
      val parent: com.badlogic.gdx.math.Rectangle = ScissorStack.scissors.get(ScissorStack.scissors.size - 1)
      val minX: scala.Float = java.lang.Math.max(parent.x, scissor.x)
      val maxX: scala.Float = java.lang.Math.min(parent.x + parent.width, scissor.x + scissor.width)
      if ((maxX - minX) < 1) {
        return false
      } else ()
      val minY: scala.Float = java.lang.Math.max(parent.y, scissor.y)
      val maxY: scala.Float = java.lang.Math.min(parent.y + parent.height, scissor.y + scissor.height)
      if ((maxY - minY) < 1) {
        return false
      } else ()
      scissor.x = minX
      scissor.y = minY
      scissor.width = maxX - minX
      scissor.height = java.lang.Math.max(1, maxY - minY)
    }
    ScissorStack.scissors.add(scissor)
    com.badlogic.gdx.graphics.glutils.HdpiUtils.glScissor(scissor.x.asInstanceOf[scala.Int].asInstanceOf[scala.Int], scissor.y.asInstanceOf[scala.Int].asInstanceOf[scala.Int], scissor.width.asInstanceOf[scala.Int].asInstanceOf[scala.Int], scissor.height.asInstanceOf[scala.Int].asInstanceOf[scala.Int])
    return true
  }
  def popScissors(): com.badlogic.gdx.math.Rectangle = {
    val old: com.badlogic.gdx.math.Rectangle = ScissorStack.scissors.pop()
    if (ScissorStack.scissors.size == 0) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST)
    } else {
      val scissor: com.badlogic.gdx.math.Rectangle = ScissorStack.scissors.peek()
      com.badlogic.gdx.graphics.glutils.HdpiUtils.glScissor(scissor.x.asInstanceOf[scala.Int].asInstanceOf[scala.Int], scissor.y.asInstanceOf[scala.Int].asInstanceOf[scala.Int], scissor.width.asInstanceOf[scala.Int].asInstanceOf[scala.Int], scissor.height.asInstanceOf[scala.Int].asInstanceOf[scala.Int])
    }
    return old
  }
  @com.badlogic.gdx.utils.Null
  def peekScissors(): com.badlogic.gdx.math.Rectangle = {
    if (ScissorStack.scissors.size == 0) {
      return null
    } else ()
    return ScissorStack.scissors.peek()
  }
  private def fix(rect: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    rect.x = java.lang.Math.round(rect.x)
    rect.y = java.lang.Math.round(rect.y)
    rect.width = java.lang.Math.round(rect.width)
    rect.height = java.lang.Math.round(rect.height)
    if (rect.width < 0) {
      rect.width = -rect.width
      rect.x = rect.x - rect.width
    } else ()
    if (rect.height < 0) {
      rect.height = -rect.height
      rect.y = rect.y - rect.height
    } else ()
  }
  def calculateScissors(camera: com.badlogic.gdx.graphics.Camera, batchTransform: com.badlogic.gdx.math.Matrix4, area: com.badlogic.gdx.math.Rectangle, scissor: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    ScissorStack.calculateScissors(camera, 0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight(), batchTransform, area, scissor)
  }
  def calculateScissors(camera: com.badlogic.gdx.graphics.Camera, viewportX: scala.Float, viewportY: scala.Float, viewportWidth: scala.Float, viewportHeight: scala.Float, batchTransform: com.badlogic.gdx.math.Matrix4, area: com.badlogic.gdx.math.Rectangle, scissor: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    ScissorStack.tmp.set(area.x, area.y, 0)
    ScissorStack.tmp.mul(batchTransform)
    camera.project(ScissorStack.tmp, viewportX, viewportY, viewportWidth, viewportHeight)
    scissor.x = ScissorStack.tmp.x
    scissor.y = ScissorStack.tmp.y
    ScissorStack.tmp.set(area.x + area.width, area.y + area.height, 0)
    ScissorStack.tmp.mul(batchTransform)
    camera.project(ScissorStack.tmp, viewportX, viewportY, viewportWidth, viewportHeight)
    scissor.width = ScissorStack.tmp.x - scissor.x
    scissor.height = ScissorStack.tmp.y - scissor.y
  }
  def getViewport(): com.badlogic.gdx.math.Rectangle = {
    if (ScissorStack.scissors.size == 0) {
      ScissorStack.viewport.set(0, 0, com.badlogic.gdx.Gdx.graphics.getWidth(), com.badlogic.gdx.Gdx.graphics.getHeight())
      return ScissorStack.viewport
    } else {
      val scissor: com.badlogic.gdx.math.Rectangle = ScissorStack.scissors.peek()
      ScissorStack.viewport.set(scissor)
      return ScissorStack.viewport
    }
  }
}