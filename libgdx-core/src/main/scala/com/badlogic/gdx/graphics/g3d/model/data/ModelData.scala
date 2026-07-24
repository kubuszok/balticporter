package com.badlogic.gdx.graphics.g3d.model.data

class ModelData {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  final val version: scala.Array[scala.Short] = new scala.Array[scala.Short](2)
  final val meshes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMesh] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMesh]()
  final val materials: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial]()
  final val nodes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNode] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNode]()
  final val animations: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation]()
  def addMesh(mesh: com.badlogic.gdx.graphics.g3d.model.data.ModelMesh): scala.Unit = {
    for (other <- this.meshes) {
      if (other.id.equals(mesh.id)) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(("Mesh with id '" + other.id) + "' already in model")
      } else ()
    }
    this.meshes.add(mesh)
  }
}