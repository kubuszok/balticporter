package com.badlogic.gdx.graphics.g3d

class Model extends com.badlogic.gdx.utils.Disposable {
  final val materials: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Material] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Material]]
  final val nodes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Node] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Node]]
  final val animations: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Animation] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Animation]]
  final val meshes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Mesh]]
  final val meshParts: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.MeshPart] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.MeshPart]]
  final val disposables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Disposable] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Disposable]]
  private var nodePartBones: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.model.NodePart, com.badlogic.gdx.utils.ArrayMap[java.lang.String, com.badlogic.gdx.math.Matrix4]] = new com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.model.NodePart, com.badlogic.gdx.utils.ArrayMap[java.lang.String, com.badlogic.gdx.math.Matrix4]]()
  def this(modelData: com.badlogic.gdx.graphics.g3d.model.data.ModelData, textureProvider: com.badlogic.gdx.graphics.g3d.utils.TextureProvider) = {
    this()
    this.load(modelData, textureProvider)
  }
  def this(modelData: com.badlogic.gdx.graphics.g3d.model.data.ModelData) = {
    this(modelData, new com.badlogic.gdx.graphics.g3d.utils.TextureProvider.FileTextureProvider())
  }
  def load(modelData: com.badlogic.gdx.graphics.g3d.model.data.ModelData, textureProvider: com.badlogic.gdx.graphics.g3d.utils.TextureProvider): scala.Unit = {
    this.loadMeshes(modelData.meshes)
    this.loadMaterials(modelData.materials, textureProvider)
    this.loadNodes(modelData.nodes)
    this.loadAnimations(modelData.animations)
    this.calculateTransforms()
  }
  def loadAnimations(modelAnimations: scala.collection.Iterable[com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation]): scala.Unit = {
    for (anim <- modelAnimations) {
      val animation: com.badlogic.gdx.graphics.g3d.model.Animation = new com.badlogic.gdx.graphics.g3d.model.Animation()
      animation.id = anim.id
      for (nanim <- anim.nodeAnimations) {
        var node: com.badlogic.gdx.graphics.g3d.model.Node = this.getNode(nanim.nodeId)
        if (node == null) {
          /* continue */ ()
        } else ()
        val nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation = new com.badlogic.gdx.graphics.g3d.model.NodeAnimation()
        nodeAnim.node = node
        if (nanim.translation != null) {
          nodeAnim.translation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3]]()
          nodeAnim.translation.ensureCapacity(nanim.translation.size)
          for (kf <- nanim.translation) {
            if (kf.keytime > animation.duration) {
              animation.duration = kf.keytime
            } else ()
            nodeAnim.translation.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3](kf.keytime, new com.badlogic.gdx.math.Vector3(if (kf.value == null) node.translation else kf.value)))
          }
        } else ()
        if (nanim.rotation != null) {
          nodeAnim.rotation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Quaternion]]()
          nodeAnim.rotation.ensureCapacity(nanim.rotation.size)
          for (kf <- nanim.rotation) {
            if (kf.keytime > animation.duration) {
              animation.duration = kf.keytime
            } else ()
            nodeAnim.rotation.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Quaternion](kf.keytime, new com.badlogic.gdx.math.Quaternion(if (kf.value == null) node.rotation else kf.value)))
          }
        } else ()
        if (nanim.scaling != null) {
          nodeAnim.scaling = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3]]()
          nodeAnim.scaling.ensureCapacity(nanim.scaling.size)
          for (kf <- nanim.scaling) {
            if (kf.keytime > animation.duration) {
              animation.duration = kf.keytime
            } else ()
            nodeAnim.scaling.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3](kf.keytime, new com.badlogic.gdx.math.Vector3(if (kf.value == null) node.scale else kf.value)))
          }
        } else ()
        if ((((nodeAnim.translation != null) && (nodeAnim.translation.size > 0)) || ((nodeAnim.rotation != null) && (nodeAnim.rotation.size > 0))) || ((nodeAnim.scaling != null) && (nodeAnim.scaling.size > 0))) {
          animation.nodeAnimations.add(nodeAnim)
        } else ()
      }
      if (animation.nodeAnimations.size > 0) {
        this.animations.add(animation)
      } else ()
    }
  }
  def loadNodes(modelNodes: scala.collection.Iterable[com.badlogic.gdx.graphics.g3d.model.data.ModelNode]): scala.Unit = {
    this.nodePartBones.clear()
    for (node <- modelNodes) {
      this.nodes.add(this.loadNode(node))
    }
    for (e <- this.nodePartBones.entries()) {
      if (e.key.invBoneBindTransforms == null) {
        e.key.invBoneBindTransforms = new com.badlogic.gdx.utils.ArrayMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.math.Matrix4](((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.model.Node](size)), ((size: scala.Int) => new scala.Array[com.badlogic.gdx.math.Matrix4](size)))
      } else ()
      e.key.invBoneBindTransforms.clear()
      for (b <- e.value.entries()) {
        e.key.invBoneBindTransforms.put(this.getNode(b.key), new com.badlogic.gdx.math.Matrix4(b.value).inv())
      }
    }
  }
  def loadNode(modelNode: com.badlogic.gdx.graphics.g3d.model.data.ModelNode): com.badlogic.gdx.graphics.g3d.model.Node = {
    val node: com.badlogic.gdx.graphics.g3d.model.Node = new com.badlogic.gdx.graphics.g3d.model.Node()
    node.id = modelNode.id
    if (modelNode.translation != null) {
      node.translation.set(modelNode.translation)
    } else ()
    if (modelNode.rotation != null) {
      node.rotation.set(modelNode.rotation)
    } else ()
    if (modelNode.scale != null) {
      node.scale.set(modelNode.scale)
    } else ()
    if (modelNode.parts != null) {
      for (modelNodePart <- modelNode.parts) {
        var meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart = null
        var meshMaterial: com.badlogic.gdx.graphics.g3d.Material = null
        if (modelNodePart.meshPartId != null) {
          for (part <- this.meshParts) {
            if (modelNodePart.meshPartId.equals(part.id)) {
              meshPart = part
              /* break */ ()
            } else ()
          }
        } else ()
        if (modelNodePart.materialId != null) {
          for (material <- this.materials) {
            if (modelNodePart.materialId.equals(material.id)) {
              meshMaterial = material
              /* break */ ()
            } else ()
          }
        } else ()
        if ((meshPart == null) || (meshMaterial == null)) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid node: " + node.id)
        } else ()
        val nodePart: com.badlogic.gdx.graphics.g3d.model.NodePart = new com.badlogic.gdx.graphics.g3d.model.NodePart()
        nodePart.meshPart = meshPart
        nodePart.material = meshMaterial
        node.parts.add(nodePart)
        if (modelNodePart.bones != null) {
          this.nodePartBones.put(nodePart, modelNodePart.bones)
        } else ()
      }
    } else ()
    if (modelNode.children != null) {
      for (child <- modelNode.children) {
        node.addChild(this.loadNode(child))
      }
    } else ()
    return node
  }
  def loadMeshes(meshes: scala.collection.Iterable[com.badlogic.gdx.graphics.g3d.model.data.ModelMesh]): scala.Unit = {
    for (mesh <- meshes) {
      this.convertMesh(mesh)
    }
  }
  def convertMesh(modelMesh: com.badlogic.gdx.graphics.g3d.model.data.ModelMesh): scala.Unit = {
    var numIndices: scala.Int = 0
    for (part <- modelMesh.parts) {
      numIndices = numIndices + part.indices.length
    }
    val hasIndices: scala.Boolean = numIndices > 0
    val attributes: com.badlogic.gdx.graphics.VertexAttributes = new com.badlogic.gdx.graphics.VertexAttributes(modelMesh.attributes)
    val numVertices: scala.Int = modelMesh.vertices.length / (attributes.vertexSize / 4)
    var mesh: com.badlogic.gdx.graphics.Mesh = new com.badlogic.gdx.graphics.Mesh(true, numVertices, numIndices, attributes)
    this.meshes.add(mesh)
    this.disposables.add(mesh)
    com.badlogic.gdx.utils.BufferUtils.copy(modelMesh.vertices, mesh.getVerticesBuffer(true), modelMesh.vertices.length, 0)
    var offset: scala.Int = 0
    val indicesBuffer: java.nio.ShortBuffer = mesh.getIndicesBuffer(true)
    indicesBuffer.asInstanceOf[java.nio.Buffer].clear()
    for (part <- modelMesh.parts) {
      val meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart = new com.badlogic.gdx.graphics.g3d.model.MeshPart()
      meshPart.id = part.id
      meshPart.primitiveType = part.primitiveType
      meshPart.offset = offset
      meshPart.size = if (hasIndices) part.indices.length else numVertices
      meshPart.mesh = mesh
      if (hasIndices) {
        indicesBuffer.put(part.indices)
      } else ()
      offset = offset + meshPart.size
      this.meshParts.add(meshPart)
    }
    indicesBuffer.asInstanceOf[java.nio.Buffer].position(0)
    for (part <- this.meshParts) {
      part.update()
    }
  }
  def loadMaterials(modelMaterials: scala.collection.Iterable[com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial], textureProvider: com.badlogic.gdx.graphics.g3d.utils.TextureProvider): scala.Unit = {
    for (mtl <- modelMaterials) {
      this.materials.add(this.convertMaterial(mtl, textureProvider))
    }
  }
  def convertMaterial(mtl: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial, textureProvider: com.badlogic.gdx.graphics.g3d.utils.TextureProvider): com.badlogic.gdx.graphics.g3d.Material = {
    val result: com.badlogic.gdx.graphics.g3d.Material = new com.badlogic.gdx.graphics.g3d.Material()
    result.id = mtl.id
    if (mtl.ambient != null) {
      result.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Ambient, mtl.ambient))
    } else ()
    if (mtl.diffuse != null) {
      result.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse, mtl.diffuse))
    } else ()
    if (mtl.specular != null) {
      result.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular, mtl.specular))
    } else ()
    if (mtl.emissive != null) {
      result.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Emissive, mtl.emissive))
    } else ()
    if (mtl.reflection != null) {
      result.set(new com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Reflection, mtl.reflection))
    } else ()
    if (mtl.shininess > 0.0f) {
      result.set(new com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess, mtl.shininess))
    } else ()
    if (mtl.opacity != 1.0f) {
      result.set(new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, mtl.opacity))
    } else ()
    val textures: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Texture]()
    if (mtl.textures != null) {
      for (tex <- mtl.textures) {
        var texture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
        if (textures.containsKey(tex.fileName)) {
          texture = textures.get(tex.fileName)
        } else {
          texture = textureProvider.load(tex.fileName)
          textures.put(tex.fileName, texture)
          this.disposables.add(texture)
        }
        val descriptor: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?] = new com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor(texture)
        descriptor.minFilter = texture.getMinFilter()
        descriptor.magFilter = texture.getMagFilter()
        descriptor.uWrap = texture.getUWrap()
        descriptor.vWrap = texture.getVWrap()
        val offsetU: scala.Float = if (tex.uvTranslation == null) 0.0f else tex.uvTranslation.x
        val offsetV: scala.Float = if (tex.uvTranslation == null) 0.0f else tex.uvTranslation.y
        val scaleU: scala.Float = if (tex.uvScaling == null) 1.0f else tex.uvScaling.x
        val scaleV: scala.Float = if (tex.uvScaling == null) 1.0f else tex.uvScaling.y
        tex.usage match {
          case com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_DIFFUSE => {
            result.set(new com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse, descriptor, offsetU, offsetV, scaleU, scaleV))
          }
          case com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_SPECULAR => {
            result.set(new com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Specular, descriptor, offsetU, offsetV, scaleU, scaleV))
          }
          case com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_BUMP => {
            result.set(new com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Bump, descriptor, offsetU, offsetV, scaleU, scaleV))
          }
          case com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_NORMAL => {
            result.set(new com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal, descriptor, offsetU, offsetV, scaleU, scaleV))
          }
          case com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_AMBIENT => {
            result.set(new com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Ambient, descriptor, offsetU, offsetV, scaleU, scaleV))
          }
          case com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_EMISSIVE => {
            result.set(new com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive, descriptor, offsetU, offsetV, scaleU, scaleV))
          }
          case com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_REFLECTION => {
            result.set(new com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection, descriptor, offsetU, offsetV, scaleU, scaleV))
          }
        }
      }
    } else ()
    return result
  }
  def manageDisposable(disposable: com.badlogic.gdx.utils.Disposable): scala.Unit = {
    if (!this.disposables.contains(disposable, true)) {
      this.disposables.add(disposable)
    } else ()
  }
  def getManagedDisposables(): scala.collection.Iterable[com.badlogic.gdx.utils.Disposable] = {
    return this.disposables
  }
  def dispose(): scala.Unit = {
    for (disposable <- this.disposables) {
      disposable.dispose()
    }
  }
  def calculateTransforms(): scala.Unit = {
    val n: scala.Int = this.nodes.size;
    { var i: scala.Int = 0; while (i < n) { {
      this.nodes.get(i).calculateTransforms(true)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < n) { {
      this.nodes.get(i).calculateBoneTransforms(true)
    }; i = i + 1 } }
  }
  def calculateBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox): com.badlogic.gdx.math.collision.BoundingBox = {
    out.inf()
    return this.extendBoundingBox(out)
  }
  def extendBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox): com.badlogic.gdx.math.collision.BoundingBox = {
    val n: scala.Int = this.nodes.size;
    { var i: scala.Int = 0; while (i < n) { {
      this.nodes.get(i).extendBoundingBox(out)
    }; i = i + 1 } }
    return out
  }
  def getAnimation(id: java.lang.String): com.badlogic.gdx.graphics.g3d.model.Animation = {
    return this.getAnimation(id, true)
  }
  def getAnimation(id: java.lang.String, ignoreCase: scala.Boolean): com.badlogic.gdx.graphics.g3d.model.Animation = {
    val n: scala.Int = this.animations.size
    var animation: com.badlogic.gdx.graphics.g3d.model.Animation = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.Animation]
    if (ignoreCase) {
      { var i: scala.Int = 0; while (i < n) { {
        if ({
          animation = this.animations.get(i)
          animation
        }.id.equalsIgnoreCase(id)) {
          return animation
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < n) { {
        if ({
          animation = this.animations.get(i)
          animation
        }.id.equals(id)) {
          return animation
        } else ()
      }; i = i + 1 } }
    }
    return null
  }
  def getMaterial(id: java.lang.String): com.badlogic.gdx.graphics.g3d.Material = {
    return this.getMaterial(id, true)
  }
  def getMaterial(id: java.lang.String, ignoreCase: scala.Boolean): com.badlogic.gdx.graphics.g3d.Material = {
    val n: scala.Int = this.materials.size
    var material: com.badlogic.gdx.graphics.g3d.Material = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Material]
    if (ignoreCase) {
      { var i: scala.Int = 0; while (i < n) { {
        if ({
          material = this.materials.get(i)
          material
        }.id.equalsIgnoreCase(id)) {
          return material
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < n) { {
        if ({
          material = this.materials.get(i)
          material
        }.id.equals(id)) {
          return material
        } else ()
      }; i = i + 1 } }
    }
    return null
  }
  def getNode(id: java.lang.String): com.badlogic.gdx.graphics.g3d.model.Node = {
    return this.getNode(id, true)
  }
  def getNode(id: java.lang.String, recursive: scala.Boolean): com.badlogic.gdx.graphics.g3d.model.Node = {
    return this.getNode(id, recursive, false)
  }
  def getNode(id: java.lang.String, recursive: scala.Boolean, ignoreCase: scala.Boolean): com.badlogic.gdx.graphics.g3d.model.Node = {
    return com.badlogic.gdx.graphics.g3d.model.Node.getNode(this.nodes, id, recursive, ignoreCase)
  }
}