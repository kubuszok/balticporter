package com.badlogic.gdx.graphics.g3d.model

class NodeAnimation {
  var node: com.badlogic.gdx.graphics.g3d.model.Node = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.Node]
  var translation: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3]] = null
  var rotation: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Quaternion]] = null
  var scaling: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3]] = null
}