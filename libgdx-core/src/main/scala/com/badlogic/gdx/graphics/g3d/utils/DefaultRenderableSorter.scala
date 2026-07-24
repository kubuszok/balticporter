package com.badlogic.gdx.graphics.g3d.utils

class DefaultRenderableSorter extends com.badlogic.gdx.graphics.g3d.utils.RenderableSorter with java.util.Comparator[com.badlogic.gdx.graphics.g3d.Renderable] {
  private var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  private final val tmpV1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private final val tmpV2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def sort(camera: com.badlogic.gdx.graphics.Camera, renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    this.camera = camera
    renderables.sort(this)
  }
  private def getTranslation(worldTransform: com.badlogic.gdx.math.Matrix4, center: com.badlogic.gdx.math.Vector3, output: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    if (center.isZero()) {
      worldTransform.getTranslation(output)
    } else {
      if (!worldTransform.hasRotationOrScaling()) {
        worldTransform.getTranslation(output).add(center)
      } else {
        output.set(center).mul(worldTransform)
      }
    }
    return output
  }
  def compare(o1: com.badlogic.gdx.graphics.g3d.Renderable, o2: com.badlogic.gdx.graphics.g3d.Renderable): scala.Int = {
    val b1: scala.Boolean = o1.material.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) && o1.material.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute].blended
    val b2: scala.Boolean = o2.material.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) && o2.material.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute].blended
    if (b1 != b2) {
      return if (b1) 1 else -1
    } else ()
    this.getTranslation(o1.worldTransform, o1.meshPart.center, this.tmpV1)
    this.getTranslation(o2.worldTransform, o2.meshPart.center, this.tmpV2)
    val dst: scala.Float = (1000.0f * this.camera.position.dst2(this.tmpV1)).asInstanceOf[scala.Int] - (1000.0f * this.camera.position.dst2(this.tmpV2)).asInstanceOf[scala.Int]
    val result: scala.Int = if (dst < 0) -1 else if (dst > 0) 1 else 0
    return if (b1) -result else result
  }
}