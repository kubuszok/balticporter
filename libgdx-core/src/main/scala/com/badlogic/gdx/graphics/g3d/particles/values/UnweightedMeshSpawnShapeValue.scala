package com.badlogic.gdx.graphics.g3d.particles.values

final class UnweightedMeshSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue {
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var indices: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  private var positionOffset: scala.Int = 0
  private var vertexSize: scala.Int = 0
  private var vertexCount: scala.Int = 0
  private var triangleCount: scala.Int = 0
  def this(value: UnweightedMeshSpawnShapeValue) = {
    this()
    this.load(value)
  }
  def setMesh(mesh: com.badlogic.gdx.graphics.Mesh, model: com.badlogic.gdx.graphics.g3d.Model): scala.Unit = {
    super.setMesh(mesh, model)
    this.vertexSize = mesh.getVertexSize() / 4
    this.positionOffset = mesh.getVertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position).offset / 4
    val indicesCount: scala.Int = mesh.getNumIndices()
    if (indicesCount > 0) {
      this.indices = new scala.Array[scala.Short](indicesCount)
      mesh.getIndices(this.indices)
      this.triangleCount = this.indices.length / 3
    } else {
      this.indices = null
    }
    this.vertexCount = mesh.getNumVertices()
    this.vertices = new scala.Array[scala.Float](this.vertexCount * this.vertexSize)
    mesh.getVertices(this.vertices)
  }
  def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit = {
    if (this.indices == null) {
      val triangleIndex: scala.Int = com.badlogic.gdx.math.MathUtils.random(this.vertexCount - 3) * this.vertexSize
      val p1Offset: scala.Int = triangleIndex + this.positionOffset
      val p2Offset: scala.Int = p1Offset + this.vertexSize
      val p3Offset: scala.Int = p2Offset + this.vertexSize
      val x1: scala.Float = this.vertices(p1Offset)
      val y1: scala.Float = this.vertices(p1Offset + 1)
      val z1: scala.Float = this.vertices(p1Offset + 2)
      val x2: scala.Float = this.vertices(p2Offset)
      val y2: scala.Float = this.vertices(p2Offset + 1)
      val z2: scala.Float = this.vertices(p2Offset + 2)
      val x3: scala.Float = this.vertices(p3Offset)
      val y3: scala.Float = this.vertices(p3Offset + 1)
      val z3: scala.Float = this.vertices(p3Offset + 2)
      com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle.pick(x1, y1, z1, x2, y2, z2, x3, y3, z3, vector)
    } else {
      val triangleIndex: scala.Int = com.badlogic.gdx.math.MathUtils.random(this.triangleCount - 1) * 3
      val p1Offset: scala.Int = (this.indices(triangleIndex) * this.vertexSize) + this.positionOffset
      val p2Offset: scala.Int = (this.indices(triangleIndex + 1) * this.vertexSize) + this.positionOffset
      val p3Offset: scala.Int = (this.indices(triangleIndex + 2) * this.vertexSize) + this.positionOffset
      val x1: scala.Float = this.vertices(p1Offset)
      val y1: scala.Float = this.vertices(p1Offset + 1)
      val z1: scala.Float = this.vertices(p1Offset + 2)
      val x2: scala.Float = this.vertices(p2Offset)
      val y2: scala.Float = this.vertices(p2Offset + 1)
      val z2: scala.Float = this.vertices(p2Offset + 2)
      val x3: scala.Float = this.vertices(p3Offset)
      val y3: scala.Float = this.vertices(p3Offset + 1)
      val z3: scala.Float = this.vertices(p3Offset + 2)
      com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle.pick(x1, y1, z1, x2, y2, z2, x3, y3, z3, vector)
    }
  }
  def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new UnweightedMeshSpawnShapeValue(this)
  }
}