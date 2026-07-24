package com.badlogic.gdx.graphics.g3d

class Renderable {
  final val worldTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  final val meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart = new com.badlogic.gdx.graphics.g3d.model.MeshPart()
  var material: com.badlogic.gdx.graphics.g3d.Material = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Material]
  var environment: com.badlogic.gdx.graphics.g3d.Environment = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Environment]
  var bones: scala.Array[com.badlogic.gdx.math.Matrix4] = null.asInstanceOf[scala.Array[com.badlogic.gdx.math.Matrix4]]
  var shader: com.badlogic.gdx.graphics.g3d.Shader = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Shader]
  var userData: java.lang.Object = null.asInstanceOf[java.lang.Object]
  def set(renderable: Renderable): Renderable = {
    this.worldTransform.set(renderable.worldTransform)
    this.material = renderable.material
    this.meshPart.set(renderable.meshPart)
    this.bones = renderable.bones
    this.environment = renderable.environment
    this.shader = renderable.shader
    this.userData = renderable.userData
    return this
  }
}