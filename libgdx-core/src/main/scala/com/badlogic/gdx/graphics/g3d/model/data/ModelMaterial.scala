package com.badlogic.gdx.graphics.g3d.model.data

class ModelMaterial {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  var `type`: MaterialType = null.asInstanceOf[MaterialType]
  var ambient: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var diffuse: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var specular: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var emissive: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var reflection: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var shininess: scala.Float = 0.0f
  var opacity: scala.Float = 1.0f
  var textures: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelTexture] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelTexture]]
  sealed abstract class MaterialType
  object MaterialType {
    case object Lambert extends MaterialType
    case object Phong extends MaterialType
    def values(): Array[MaterialType] = Array(Lambert, Phong)
  }
}