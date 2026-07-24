package com.badlogic.gdx.graphics.g3d.model.data

class ModelNodePart {
  var materialId: java.lang.String = null.asInstanceOf[java.lang.String]
  var meshPartId: java.lang.String = null.asInstanceOf[java.lang.String]
  var bones: com.badlogic.gdx.utils.ArrayMap[java.lang.String, com.badlogic.gdx.math.Matrix4] = null.asInstanceOf[com.badlogic.gdx.utils.ArrayMap[java.lang.String, com.badlogic.gdx.math.Matrix4]]
  var uvMapping: scala.Array[scala.Array[scala.Int]] = null.asInstanceOf[scala.Array[scala.Array[scala.Int]]]
}