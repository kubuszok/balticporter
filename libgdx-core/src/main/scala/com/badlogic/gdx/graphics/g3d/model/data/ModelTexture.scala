package com.badlogic.gdx.graphics.g3d.model.data

class ModelTexture {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  var fileName: java.lang.String = null.asInstanceOf[java.lang.String]
  var uvTranslation: com.badlogic.gdx.math.Vector2 = null.asInstanceOf[com.badlogic.gdx.math.Vector2]
  var uvScaling: com.badlogic.gdx.math.Vector2 = null.asInstanceOf[com.badlogic.gdx.math.Vector2]
  var usage: scala.Int = 0
}
object ModelTexture {
  final val USAGE_UNKNOWN: scala.Int = 0
  final val USAGE_NONE: scala.Int = 1
  final val USAGE_DIFFUSE: scala.Int = 2
  final val USAGE_EMISSIVE: scala.Int = 3
  final val USAGE_AMBIENT: scala.Int = 4
  final val USAGE_SPECULAR: scala.Int = 5
  final val USAGE_SHININESS: scala.Int = 6
  final val USAGE_NORMAL: scala.Int = 7
  final val USAGE_BUMP: scala.Int = 8
  final val USAGE_TRANSPARENCY: scala.Int = 9
  final val USAGE_REFLECTION: scala.Int = 10
}