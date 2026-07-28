package com.badlogic.gdx.graphics.g3d.loader

class ObjLoader(resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.ModelLoader[com.badlogic.gdx.graphics.g3d.loader.ObjLoader.ObjLoaderParameters](resolver$p) {
  final val verts: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray(300)
  final val norms: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray(300)
  final val uvs: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray(200)
  final val groups: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group](10)
  def this() = {
    this(null)
  }
  def loadModel(fileHandle: com.badlogic.gdx.files.FileHandle, flipV: scala.Boolean): com.badlogic.gdx.graphics.g3d.Model = {
    return this.loadModel(fileHandle, new com.badlogic.gdx.graphics.g3d.loader.ObjLoader.ObjLoaderParameters(flipV))
  }
  @java.lang.Override
  def loadModelData(file: com.badlogic.gdx.files.FileHandle, parameters: com.badlogic.gdx.graphics.g3d.loader.ObjLoader.ObjLoaderParameters): com.badlogic.gdx.graphics.g3d.model.data.ModelData = {
    return this.loadModelData(file, (parameters != null) && parameters.flipV)
  }
  def loadModelData(file: com.badlogic.gdx.files.FileHandle, flipV: scala.Boolean): com.badlogic.gdx.graphics.g3d.model.data.ModelData = {
    if (ObjLoader.logWarning) {
      com.badlogic.gdx.Gdx.app.error("ObjLoader", "Wavefront (OBJ) is not fully supported, consult the documentation for more information")
    } else ()
    var line: java.lang.String = null.asInstanceOf[java.lang.String]
    var tokens: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
    var firstChar: scala.Char = '\u0000'
    val mtl: com.badlogic.gdx.graphics.g3d.loader.MtlLoader = new com.badlogic.gdx.graphics.g3d.loader.MtlLoader()
    var activeGroup: com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group = new com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group("default")
    this.groups.add(activeGroup)
    val reader: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(file.read()), 4096)
    var id: scala.Int = 0
    try {
      while ({
        line = reader.readLine()
        line
      } != null) {
        tokens = line.split("\\s+")
        if (tokens.length < 1) {
          /* break */ ()
        } else ()
        if (tokens(0).length() == 0) {
          /* continue */ ()
        } else {
          if ({
            firstChar = tokens(0).toLowerCase().charAt(0)
            firstChar
          } == '#') {
            /* continue */ ()
          } else {
            if (firstChar == 'v') {
              if (tokens(0).length() == 1) {
                this.verts.add(java.lang.Float.parseFloat(tokens(1)))
                this.verts.add(java.lang.Float.parseFloat(tokens(2)))
                this.verts.add(java.lang.Float.parseFloat(tokens(3)))
              } else {
                if (tokens(0).charAt(1) == 'n') {
                  this.norms.add(java.lang.Float.parseFloat(tokens(1)))
                  this.norms.add(java.lang.Float.parseFloat(tokens(2)))
                  this.norms.add(java.lang.Float.parseFloat(tokens(3)))
                } else {
                  if (tokens(0).charAt(1) == 't') {
                    this.uvs.add(java.lang.Float.parseFloat(tokens(1)))
                    this.uvs.add(if (flipV) 1 - java.lang.Float.parseFloat(tokens(2)) else java.lang.Float.parseFloat(tokens(2)))
                  } else ()
                }
              }
            } else {
              if (firstChar == 'f') {
                var parts: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
                val faces: com.badlogic.gdx.utils.Array[java.lang.Integer] = activeGroup.faces;
                { var i: scala.Int = 1; while (i < (tokens.length - 2)) { {
                  parts = tokens(1).split("/")
                  faces.add(this.getIndex(parts(0), this.verts.size).asInstanceOf[java.lang.Integer])
                  if (parts.length > 2) {
                    if (i == 1) {
                      activeGroup.hasNorms = true
                    } else ()
                    faces.add(this.getIndex(parts(2), this.norms.size).asInstanceOf[java.lang.Integer])
                  } else ()
                  if ((parts.length > 1) && (parts(1).length() > 0)) {
                    if (i == 1) {
                      activeGroup.hasUVs = true
                    } else ()
                    faces.add(this.getIndex(parts(1), this.uvs.size).asInstanceOf[java.lang.Integer])
                  } else ()
                  parts = tokens({ i += 1; i }).split("/")
                  faces.add(this.getIndex(parts(0), this.verts.size).asInstanceOf[java.lang.Integer])
                  if (parts.length > 2) {
                    faces.add(this.getIndex(parts(2), this.norms.size).asInstanceOf[java.lang.Integer])
                  } else ()
                  if ((parts.length > 1) && (parts(1).length() > 0)) {
                    faces.add(this.getIndex(parts(1), this.uvs.size).asInstanceOf[java.lang.Integer])
                  } else ()
                  parts = tokens({ i += 1; i }).split("/")
                  faces.add(this.getIndex(parts(0), this.verts.size).asInstanceOf[java.lang.Integer])
                  if (parts.length > 2) {
                    faces.add(this.getIndex(parts(2), this.norms.size).asInstanceOf[java.lang.Integer])
                  } else ()
                  if ((parts.length > 1) && (parts(1).length() > 0)) {
                    faces.add(this.getIndex(parts(1), this.uvs.size).asInstanceOf[java.lang.Integer])
                  } else ()
                  activeGroup.numFaces = activeGroup.numFaces + 1
                }; i = i - 1 } }
              } else {
                if ((firstChar == 'o') || (firstChar == 'g')) {
                  if (tokens.length > 1) {
                    activeGroup = this.setActiveGroup(tokens(1))
                  } else {
                    activeGroup = this.setActiveGroup("default")
                  }
                } else {
                  if (tokens(0).equals("mtllib")) {
                    mtl.load(file.parent().child(tokens(1)))
                  } else {
                    if (tokens(0).equals("usemtl")) {
                      if (tokens.length == 1) {
                        activeGroup.materialName = "default"
                      } else {
                        activeGroup.materialName = tokens(1).replace('.', '_')
                      }
                    } else ()
                  }
                }
              }
            }
          }
        }
      }
      reader.close()
    } catch {
      case e: java.io.IOException => {
        return null
      }
    };
    { var i: scala.Int = 0; while (i < this.groups.size) { {
      if (this.groups.get(i).numFaces < 1) {
        this.groups.removeIndex(i)
        i = i - 1
      } else ()
    }; i = i + 1 } }
    if (this.groups.size < 1) {
      return null
    } else ()
    val numGroups: scala.Int = this.groups.size
    val data: com.badlogic.gdx.graphics.g3d.model.data.ModelData = new com.badlogic.gdx.graphics.g3d.model.data.ModelData();
    { var g: scala.Int = 0; while (g < numGroups) { {
      val group: com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group = this.groups.get(g)
      val faces: com.badlogic.gdx.utils.Array[java.lang.Integer] = group.faces
      val numElements: scala.Int = faces.size
      var numFaces: scala.Int = group.numFaces
      var hasNorms: scala.Boolean = group.hasNorms
      var hasUVs: scala.Boolean = group.hasUVs
      val finalVerts: scala.Array[scala.Float] = new scala.Array[scala.Float]((numFaces * 3) * ((3 + (if (hasNorms) 3 else 0)) + (if (hasUVs) 2 else 0)));
      { var i: scala.Int = 0; var vi: scala.Int = 0; while (i < numElements) { {
        var vertIndex: scala.Int = faces.get({ i += 1; i }) * 3
        finalVerts({ vi += 1; vi }) = this.verts.get({ vertIndex += 1; vertIndex })
        finalVerts({ vi += 1; vi }) = this.verts.get({ vertIndex += 1; vertIndex })
        finalVerts({ vi += 1; vi }) = this.verts.get(vertIndex)
        if (hasNorms) {
          var normIndex: scala.Int = faces.get({ i += 1; i }) * 3
          finalVerts({ vi += 1; vi }) = this.norms.get({ normIndex += 1; normIndex })
          finalVerts({ vi += 1; vi }) = this.norms.get({ normIndex += 1; normIndex })
          finalVerts({ vi += 1; vi }) = this.norms.get(normIndex)
        } else ()
        if (hasUVs) {
          var uvIndex: scala.Int = faces.get({ i += 1; i }) * 2
          finalVerts({ vi += 1; vi }) = this.uvs.get({ uvIndex += 1; uvIndex })
          finalVerts({ vi += 1; vi }) = this.uvs.get(uvIndex)
        } else ()
      };  } }
      val numIndices: scala.Int = if ((numFaces * 3) >= java.lang.Short.MAX_VALUE) 0 else numFaces * 3
      val finalIndices: scala.Array[scala.Short] = new scala.Array[scala.Short](numIndices)
      if (numIndices > 0) {
        { var i: scala.Int = 0; while (i < numIndices) { {
          finalIndices(i) = i.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
        }; i = i + 1 } }
      } else ()
      var attributes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute]()
      attributes.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE))
      if (hasNorms) {
        attributes.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.NORMAL_ATTRIBUTE))
      } else ()
      if (hasUVs) {
        attributes.add(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0"))
      } else ()
      val stringId: java.lang.String = java.lang.Integer.toString({ id += 1; id })
      val nodeId: java.lang.String = if ("default".equals(group.name)) "node" + stringId else group.name
      var meshId: java.lang.String = if ("default".equals(group.name)) "mesh" + stringId else group.name
      val partId: java.lang.String = if ("default".equals(group.name)) "part" + stringId else group.name
      val node: com.badlogic.gdx.graphics.g3d.model.data.ModelNode = new com.badlogic.gdx.graphics.g3d.model.data.ModelNode()
      node.id = nodeId
      node.meshId = meshId
      node.scale = new com.badlogic.gdx.math.Vector3(1, 1, 1)
      node.translation = new com.badlogic.gdx.math.Vector3()
      node.rotation = new com.badlogic.gdx.math.Quaternion()
      val pm: com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart()
      pm.meshPartId = partId
      pm.materialId = group.materialName
      node.parts = scala.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart](pm)
      val part: com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart = new com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart()
      part.id = partId
      part.indices = finalIndices
      part.primitiveType = com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
      val mesh: com.badlogic.gdx.graphics.g3d.model.data.ModelMesh = new com.badlogic.gdx.graphics.g3d.model.data.ModelMesh()
      mesh.id = meshId
      mesh.attributes = attributes.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.VertexAttribute](size)))
      mesh.vertices = finalVerts
      mesh.parts = scala.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart](part)
      data.nodes.add(node)
      data.meshes.add(mesh)
      val mm: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = mtl.getMaterial(group.materialName)
      data.materials.add(mm)
    }; g = g + 1 } }
    if (this.verts.size > 0) {
      this.verts.clear()
    } else ()
    if (this.norms.size > 0) {
      this.norms.clear()
    } else ()
    if (this.uvs.size > 0) {
      this.uvs.clear()
    } else ()
    if (this.groups.size > 0) {
      this.groups.clear()
    } else ()
    return data
  }
  private def setActiveGroup(name: java.lang.String): com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group = {
    for (group <- this.groups) {
      if (group.name.equals(name)) {
        return group
      } else ()
    }
    val group: com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group = new com.badlogic.gdx.graphics.g3d.loader.ObjLoader.Group(name)
    this.groups.add(group)
    return group
  }
  private def getIndex(index: java.lang.String, size: scala.Int): scala.Int = {
    if ((index == null) || (index.length() == 0)) {
      return 0
    } else ()
    val idx: scala.Int = java.lang.Integer.parseInt(index)
    if (idx < 0) {
      return size + idx
    } else {
      return idx - 1
    }
  }
}
object ObjLoader {
  export com.badlogic.gdx.assets.loaders.ModelLoader.{Group => _, ObjLoaderParameters => _, logWarning => _, *}
  var logWarning: scala.Boolean = false
  class ObjLoaderParameters extends com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters {
    var flipV: scala.Boolean = false
    def this(flipV: scala.Boolean) = {
      this()
      this.flipV = flipV
    }
  }
  object ObjLoaderParameters {
    export com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters.*
  }
  class Group(name$p: java.lang.String) {
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    var materialName: java.lang.String = null.asInstanceOf[java.lang.String]
    var faces: com.badlogic.gdx.utils.Array[java.lang.Integer] = null.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Integer]]
    var numFaces: scala.Int = 0
    var hasNorms: scala.Boolean = false
    var hasUVs: scala.Boolean = false
    var mat: com.badlogic.gdx.graphics.g3d.Material = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Material]
    this.name = name$p
    this.faces = new com.badlogic.gdx.utils.Array[java.lang.Integer](200)
    this.numFaces = 0
    this.mat = new com.badlogic.gdx.graphics.g3d.Material("")
    this.materialName = "default"
  }
}