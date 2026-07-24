package com.badlogic.gdx.graphics.g3d.model.data

class ModelMaterial {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  var `type`: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial.MaterialType = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial.MaterialType]
  var ambient: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var diffuse: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var specular: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var emissive: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var reflection: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  var shininess: scala.Float = 0.0f
  var opacity: scala.Float = 1.0f
  var textures: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelTexture] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelTexture]]
}
object ModelMaterial {
  sealed abstract class MaterialType {
    def name(): java.lang.String = this.toString()
  }
  object MaterialType {
    case object Lambert extends MaterialType
    case object Phong extends MaterialType
    def values(): scala.Array[MaterialType] = scala.Array(Lambert, Phong)
    def valueOf(name: java.lang.String): MaterialType = name match {
      case "Lambert" => Lambert
      case "Phong" => Phong
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}