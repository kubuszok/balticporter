package com.badlogic.gdx.graphics.g3d.particles.values

final class WeightMeshSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue {
  private var distribution: com.badlogic.gdx.math.CumulativeDistribution[com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle] = null.asInstanceOf[com.badlogic.gdx.math.CumulativeDistribution[com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle]]
  def this(value: WeightMeshSpawnShapeValue) = {
    this()
    this.distribution = new com.badlogic.gdx.math.CumulativeDistribution[com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle]()
    this.load(value)
  }
  this.distribution = new com.badlogic.gdx.math.CumulativeDistribution[com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle]()
  def init(): scala.Unit = {
    this.calculateWeights()
  }
  def calculateWeights(): scala.Unit = {
    this.distribution.clear()
    val attributes: com.badlogic.gdx.graphics.VertexAttributes = mesh.getVertexAttributes()
    val indicesCount: scala.Int = mesh.getNumIndices()
    val vertexCount: scala.Int = mesh.getNumVertices()
    val vertexSize: scala.Int = (attributes.vertexSize / 4).asInstanceOf[scala.Short]
    val positionOffset: scala.Int = (attributes.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position).offset / 4).asInstanceOf[scala.Short]
    val vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](vertexCount * vertexSize)
    mesh.getVertices(vertices)
    if (indicesCount > 0) {
      val indices: scala.Array[scala.Short] = new scala.Array[scala.Short](indicesCount)
      mesh.getIndices(indices);
      { var i: scala.Int = 0; while (i < indicesCount) { {
        val p1Offset: scala.Int = (indices(i) * vertexSize) + positionOffset
        val p2Offset: scala.Int = (indices(i + 1) * vertexSize) + positionOffset
        val p3Offset: scala.Int = (indices(i + 2) * vertexSize) + positionOffset
        val x1: scala.Float = vertices(p1Offset)
        val y1: scala.Float = vertices(p1Offset + 1)
        val z1: scala.Float = vertices(p1Offset + 2)
        val x2: scala.Float = vertices(p2Offset)
        val y2: scala.Float = vertices(p2Offset + 1)
        val z2: scala.Float = vertices(p2Offset + 2)
        val x3: scala.Float = vertices(p3Offset)
        val y3: scala.Float = vertices(p3Offset + 1)
        val z3: scala.Float = vertices(p3Offset + 2)
        val area: scala.Float = java.lang.Math.abs((((x1 * (y2 - y3)) + (x2 * (y3 - y1))) + (x3 * (y1 - y2))) / 2.0f)
        this.distribution.add(new com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle(x1, y1, z1, x2, y2, z2, x3, y3, z3), area)
      }; i = i + 3 } }
    } else {
      { var i: scala.Int = 0; while (i < vertexCount) { {
        val p1Offset: scala.Int = i + positionOffset
        val p2Offset: scala.Int = p1Offset + vertexSize
        val p3Offset: scala.Int = p2Offset + vertexSize
        val x1: scala.Float = vertices(p1Offset)
        val y1: scala.Float = vertices(p1Offset + 1)
        val z1: scala.Float = vertices(p1Offset + 2)
        val x2: scala.Float = vertices(p2Offset)
        val y2: scala.Float = vertices(p2Offset + 1)
        val z2: scala.Float = vertices(p2Offset + 2)
        val x3: scala.Float = vertices(p3Offset)
        val y3: scala.Float = vertices(p3Offset + 1)
        val z3: scala.Float = vertices(p3Offset + 2)
        val area: scala.Float = java.lang.Math.abs((((x1 * (y2 - y3)) + (x2 * (y3 - y1))) + (x3 * (y1 - y2))) / 2.0f)
        this.distribution.add(new com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle(x1, y1, z1, x2, y2, z2, x3, y3, z3), area)
      }; i = i + vertexSize } }
    }
    this.distribution.generateNormalized()
  }
  def spawnAux(vector: com.badlogic.gdx.math.Vector3, percent: scala.Float): scala.Unit = {
    val t: com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.Triangle = this.distribution.value()
    val a: scala.Float = com.badlogic.gdx.math.MathUtils.random()
    val b: scala.Float = com.badlogic.gdx.math.MathUtils.random()
    vector.set((t.x1 + (a * (t.x2 - t.x1))) + (b * (t.x3 - t.x1)), (t.y1 + (a * (t.y2 - t.y1))) + (b * (t.y3 - t.y1)), (t.z1 + (a * (t.z2 - t.z1))) + (b * (t.z3 - t.z1)))
  }
  def copy(): com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue = {
    return new WeightMeshSpawnShapeValue(this)
  }
}
object WeightMeshSpawnShapeValue {
  export com.badlogic.gdx.graphics.g3d.particles.values.MeshSpawnShapeValue.*
}