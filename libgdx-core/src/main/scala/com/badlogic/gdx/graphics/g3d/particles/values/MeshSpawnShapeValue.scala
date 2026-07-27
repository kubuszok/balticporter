package com.badlogic.gdx.graphics.g3d.particles.values

abstract class MeshSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue {
  var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  var model: com.badlogic.gdx.graphics.g3d.Model = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Model]
  def this(value: MeshSpawnShapeValue) = {
    this()
  }
  def load(value: com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue): scala.Unit = {
    super.load(value)
    val spawnShapeValue: MeshSpawnShapeValue = value.asInstanceOf[MeshSpawnShapeValue]
    this.setMesh(spawnShapeValue.mesh, spawnShapeValue.model)
  }
  def setMesh(mesh: com.badlogic.gdx.graphics.Mesh, model: com.badlogic.gdx.graphics.g3d.Model): scala.Unit = {
    if (mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position) == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Mesh vertices must have Usage.Position")
    } else ()
    this.model = model
    this.mesh = mesh
  }
  def setMesh(mesh: com.badlogic.gdx.graphics.Mesh): scala.Unit = {
    this.setMesh(mesh, null)
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    if (this.model != null) {
      val saveData: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = data.createSaveData()
      saveData.saveAsset(manager.getAssetFileName(this.model), classOf[com.badlogic.gdx.graphics.g3d.Model])
      saveData.save("index", this.model.meshes.indexOf(this.mesh, true).asInstanceOf[java.lang.Integer])
    } else ()
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, data: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    val saveData: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = data.getSaveData()
    val descriptor: com.badlogic.gdx.assets.AssetDescriptor[?] = saveData.loadAsset().asInstanceOf[com.badlogic.gdx.assets.AssetDescriptor[?]]
    if (descriptor != null) {
      val model: com.badlogic.gdx.graphics.g3d.Model = manager.get(descriptor).asInstanceOf[com.badlogic.gdx.graphics.g3d.Model].asInstanceOf[com.badlogic.gdx.graphics.g3d.Model]
      this.setMesh(model.meshes.get(saveData.load("index").asInstanceOf[java.lang.Integer]), model)
    } else ()
  }
}
object MeshSpawnShapeValue {
  class Triangle {
    var x1: scala.Float = 0.0f
    var y1: scala.Float = 0.0f
    var z1: scala.Float = 0.0f
    var x2: scala.Float = 0.0f
    var y2: scala.Float = 0.0f
    var z2: scala.Float = 0.0f
    var x3: scala.Float = 0.0f
    var y3: scala.Float = 0.0f
    var z3: scala.Float = 0.0f
    def this(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, x3: scala.Float, y3: scala.Float, z3: scala.Float) = {
      this()
      this.x1 = x1
      this.y1 = y1
      this.z1 = z1
      this.x2 = x2
      this.y2 = y2
      this.z2 = z2
      this.x3 = x3
      this.y3 = y3
      this.z3 = z3
    }
    def pick(vector: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
      val a: scala.Float = com.badlogic.gdx.math.MathUtils.random()
      val b: scala.Float = com.badlogic.gdx.math.MathUtils.random()
      return vector.set((this.x1 + (a * (this.x2 - this.x1))) + (b * (this.x3 - this.x1)), (this.y1 + (a * (this.y2 - this.y1))) + (b * (this.y3 - this.y1)), (this.z1 + (a * (this.z2 - this.z1))) + (b * (this.z3 - this.z1)))
    }
  }
  object Triangle {
    def pick(x1: scala.Float, y1: scala.Float, z1: scala.Float, x2: scala.Float, y2: scala.Float, z2: scala.Float, x3: scala.Float, y3: scala.Float, z3: scala.Float, vector: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
      val a: scala.Float = com.badlogic.gdx.math.MathUtils.random()
      val b: scala.Float = com.badlogic.gdx.math.MathUtils.random()
      return vector.set((x1 + (a * (x2 - x1))) + (b * (x3 - x1)), (y1 + (a * (y2 - y1))) + (b * (y3 - y1)), (z1 + (a * (z2 - z1))) + (b * (z3 - z1)))
    }
  }
}