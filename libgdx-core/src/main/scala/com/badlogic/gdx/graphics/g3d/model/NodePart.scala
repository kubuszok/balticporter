package com.badlogic.gdx.graphics.g3d.model

class NodePart {
  var meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.MeshPart]
  var material: com.badlogic.gdx.graphics.g3d.Material = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Material]
  var invBoneBindTransforms: com.badlogic.gdx.utils.ArrayMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.math.Matrix4] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.math.Matrix4]]
  var bones: scala.Array[com.badlogic.gdx.math.Matrix4] = null.asInstanceOf[scala.Array[com.badlogic.gdx.math.Matrix4]]
  var enabled: scala.Boolean = true
  def this(meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart, material: com.badlogic.gdx.graphics.g3d.Material) = {
    this()
    this.meshPart = meshPart
    this.material = material
  }
  def setRenderable(out: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Renderable = {
    out.material = this.material
    out.meshPart.set(this.meshPart)
    out.bones = this.bones
    return out
  }
  def copy(): NodePart = {
    return new NodePart().set(this)
  }
  protected def set(other: NodePart): NodePart = {
    this.meshPart = new com.badlogic.gdx.graphics.g3d.model.MeshPart(other.meshPart)
    this.material = other.material
    this.enabled = other.enabled
    if (other.invBoneBindTransforms == null) {
      this.invBoneBindTransforms = null
      this.bones = null
    } else {
      if (this.invBoneBindTransforms == null) {
        this.invBoneBindTransforms = new com.badlogic.gdx.utils.ArrayMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.math.Matrix4](true, other.invBoneBindTransforms.size, scala.Array[com.badlogic.gdx.graphics.g3d.model.Node].<init>, scala.Array[com.badlogic.gdx.math.Matrix4].<init>)
      } else {
        this.invBoneBindTransforms.clear()
      }
      this.invBoneBindTransforms.putAll(other.invBoneBindTransforms)
      if ((this.bones == null) || (this.bones.length != this.invBoneBindTransforms.size)) {
        this.bones = new Array[com.badlogic.gdx.math.Matrix4](this.invBoneBindTransforms.size)
      } else ()
      { var i: scala.Int = 0; while (i < this.bones.length) { {
        if (this.bones(i) == null) {
          this.bones(i) = new com.badlogic.gdx.math.Matrix4()
        } else ()
      }; i = i + 1 } }
    }
    return this
  }
}