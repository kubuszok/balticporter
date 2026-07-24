package com.badlogic.gdx.graphics.g3d.decals

class DecalBatch extends com.badlogic.gdx.utils.Disposable {
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var mesh: com.badlogic.gdx.graphics.Mesh = null.asInstanceOf[com.badlogic.gdx.graphics.Mesh]
  private final val groupList: com.badlogic.gdx.utils.SortedIntList[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]] = new com.badlogic.gdx.utils.SortedIntList[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]]()
  private var groupStrategy: com.badlogic.gdx.graphics.g3d.decals.GroupStrategy = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.decals.GroupStrategy]
  private final val groupPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]] = new com.badlogic.gdx.utils.Pool[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]](16)
  private final val usedGroups: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]](16)
  def this(size: scala.Int, groupStrategy: com.badlogic.gdx.graphics.g3d.decals.GroupStrategy) = {
    this()
    this.initialize(size)
    this.setGroupStrategy(groupStrategy)
  }
  def this(groupStrategy: com.badlogic.gdx.graphics.g3d.decals.GroupStrategy) = {
    this(DecalBatch.DEFAULT_SIZE, groupStrategy)
  }
  def setGroupStrategy(groupStrategy: com.badlogic.gdx.graphics.g3d.decals.GroupStrategy): scala.Unit = {
    this.groupStrategy = groupStrategy
  }
  def initialize(size: scala.Int): scala.Unit = {
    this.vertices = new Array[scala.Float](size * com.badlogic.gdx.graphics.g3d.decals.Decal.SIZE)
    var vertexDataType: com.badlogic.gdx.graphics.Mesh.VertexDataType = com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexArray
    if (com.badlogic.gdx.Gdx.gl30 != null) {
      vertexDataType = com.badlogic.gdx.graphics.Mesh.VertexDataType.VertexBufferObjectWithVAO
    } else ()
    this.mesh = new com.badlogic.gdx.graphics.Mesh(vertexDataType, false, size * 4, size * 6, new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0"))
    val indices: scala.Array[scala.Short] = new Array[scala.Short](size * 6)
    var v: scala.Int = 0;
    { var i: scala.Int = 0; while (i < indices.length) { {
      indices(i) = v.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 1) = (v + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 2) = (v + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 3) = (v + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 4) = (v + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      indices(i + 5) = (v + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    }; i = i + 6; v = v + 4 } }
    this.mesh.setIndices(indices)
  }
  def getSize(): scala.Int = {
    return this.vertices.length / com.badlogic.gdx.graphics.g3d.decals.Decal.SIZE
  }
  def add(decal: com.badlogic.gdx.graphics.g3d.decals.Decal): scala.Unit = {
    val groupIndex: scala.Int = this.groupStrategy.decideGroup(decal)
    var targetGroup: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal] = this.groupList.get(groupIndex)
    if (targetGroup == null) {
      targetGroup = this.groupPool.obtain()
      targetGroup.clear()
      this.usedGroups.add(targetGroup)
      this.groupList.insert(groupIndex, targetGroup)
    } else ()
    targetGroup.add(decal)
  }
  def flush(): scala.Unit = {
    this.render()
    this.clear()
  }
  def render(): scala.Unit = {
    this.groupStrategy.beforeGroups()
    for (group <- this.groupList) {
      this.groupStrategy.beforeGroup(group.index, group.value)
      val shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = this.groupStrategy.getGroupShader(group.index)
      this.render(shader, group.value)
      this.groupStrategy.afterGroup(group.index)
    }
    this.groupStrategy.afterGroups()
  }
  private def render(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, decals: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]): scala.Unit = {
    var lastMaterial: com.badlogic.gdx.graphics.g3d.decals.DecalMaterial = null
    var idx: scala.Int = 0
    for (decal <- decals) {
      if ((lastMaterial == null) || (!lastMaterial.equals(decal.getMaterial()))) {
        if (idx > 0) {
          this.flush(shader, idx)
          idx = 0
        } else ()
        decal.material.set()
        lastMaterial = decal.material
      } else ()
      decal.update()
      java.lang.System.arraycopy(decal.vertices, 0, this.vertices, idx, decal.vertices.length)
      idx = idx + decal.vertices.length
      if (idx == this.vertices.length) {
        this.flush(shader, idx)
        idx = 0
      } else ()
    }
    if (idx > 0) {
      this.flush(shader, idx)
    } else ()
  }
  def flush(shader: com.badlogic.gdx.graphics.glutils.ShaderProgram, verticesPosition: scala.Int): scala.Unit = {
    this.mesh.setVertices(this.vertices, 0, verticesPosition)
    this.mesh.render(shader, com.badlogic.gdx.graphics.GL20.GL_TRIANGLES, 0, verticesPosition / 4)
  }
  def clear(): scala.Unit = {
    this.groupList.clear()
    this.groupPool.freeAll(this.usedGroups)
    this.usedGroups.clear()
  }
  def dispose(): scala.Unit = {
    this.clear()
    this.vertices = null
    this.mesh.dispose()
  }
}
object DecalBatch {
  private final val DEFAULT_SIZE: scala.Int = 1000
}