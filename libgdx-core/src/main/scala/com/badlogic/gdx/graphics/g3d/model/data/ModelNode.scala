package com.badlogic.gdx.graphics.g3d.model.data

class ModelNode {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  var translation: com.badlogic.gdx.math.Vector3 = null.asInstanceOf[com.badlogic.gdx.math.Vector3]
  var rotation: com.badlogic.gdx.math.Quaternion = null.asInstanceOf[com.badlogic.gdx.math.Quaternion]
  var scale: com.badlogic.gdx.math.Vector3 = null.asInstanceOf[com.badlogic.gdx.math.Vector3]
  var meshId: java.lang.String = null.asInstanceOf[java.lang.String]
  var parts: scala.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart]]
  var children: scala.Array[ModelNode] = null.asInstanceOf[scala.Array[ModelNode]]
}