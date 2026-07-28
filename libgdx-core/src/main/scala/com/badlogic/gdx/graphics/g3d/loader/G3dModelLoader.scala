package com.badlogic.gdx.graphics.g3d.loader

class G3dModelLoader(reader$p: com.badlogic.gdx.utils.BaseJsonReader, resolver$p: com.badlogic.gdx.assets.loaders.FileHandleResolver) extends com.badlogic.gdx.assets.loaders.ModelLoader[com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters](resolver$p) {
  var reader: com.badlogic.gdx.utils.BaseJsonReader = null.asInstanceOf[com.badlogic.gdx.utils.BaseJsonReader]
  final val tempQ: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
  def this(reader: com.badlogic.gdx.utils.BaseJsonReader) = {
    this(reader, null)
  }
  this.reader = reader$p
  @java.lang.Override
  override def loadModelData(fileHandle: com.badlogic.gdx.files.FileHandle, parameters: com.badlogic.gdx.assets.loaders.ModelLoader.ModelParameters): com.badlogic.gdx.graphics.g3d.model.data.ModelData = {
    return this.parseModel(fileHandle)
  }
  def parseModel(handle: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.graphics.g3d.model.data.ModelData = {
    val json: com.badlogic.gdx.utils.JsonValue = this.reader.parse(handle)
    val model: com.badlogic.gdx.graphics.g3d.model.data.ModelData = new com.badlogic.gdx.graphics.g3d.model.data.ModelData()
    val version: com.badlogic.gdx.utils.JsonValue = json.require("version")
    model.version(0) = version.getShort(0)
    model.version(1) = version.getShort(1)
    if ((model.version(0) != G3dModelLoader.VERSION_HI) || (model.version(1) != G3dModelLoader.VERSION_LO)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Model version not supported")
    } else ()
    model.id = json.getString("id", "")
    this.parseMeshes(model, json)
    this.parseMaterials(model, json, handle.parent().path())
    this.parseNodes(model, json)
    this.parseAnimations(model, json)
    return model
  }
  def parseMeshes(model: com.badlogic.gdx.graphics.g3d.model.data.ModelData, json: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val meshes: com.badlogic.gdx.utils.JsonValue = json.get("meshes")
    if (meshes != null) {
      model.meshes.ensureCapacity(meshes.size$field);
      { var mesh: com.badlogic.gdx.utils.JsonValue = meshes.child$field; while (mesh != null) { {
        val jsonMesh: com.badlogic.gdx.graphics.g3d.model.data.ModelMesh = new com.badlogic.gdx.graphics.g3d.model.data.ModelMesh()
        var id: java.lang.String = mesh.getString("id", "")
        jsonMesh.id = id
        var attributes: com.badlogic.gdx.utils.JsonValue = mesh.require("attributes")
        jsonMesh.attributes = this.parseAttributes(attributes)
        jsonMesh.vertices = mesh.require("vertices").asFloatArray()
        val meshParts: com.badlogic.gdx.utils.JsonValue = mesh.require("parts")
        var parts: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart]();
        { var meshPart: com.badlogic.gdx.utils.JsonValue = meshParts.child$field; while (meshPart != null) { {
          val jsonPart: com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart = new com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart()
          val partId: java.lang.String = meshPart.getString("id", null)
          if (partId == null) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Not id given for mesh part")
          } else ()
          for (other <- parts) {
            if (other.id.equals(partId)) {
              throw new com.badlogic.gdx.utils.GdxRuntimeException(("Mesh part with id '" + partId) + "' already in defined")
            } else ()
          }
          jsonPart.id = partId
          val `type`: java.lang.String = meshPart.getString("type", null)
          if (`type` == null) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException(("No primitive type given for mesh part '" + partId) + "'")
          } else ()
          jsonPart.primitiveType = this.parseType(`type`)
          jsonPart.indices = meshPart.require("indices").asShortArray()
          parts.add(jsonPart)
        }; meshPart = meshPart.next$field } }
        jsonMesh.parts = parts.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMeshPart](size)))
        model.meshes.add(jsonMesh)
      }; mesh = mesh.next$field } }
    } else ()
  }
  def parseType(`type`: java.lang.String): scala.Int = {
    if (`type`.equals("TRIANGLES")) {
      return com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
    } else {
      if (`type`.equals("LINES")) {
        return com.badlogic.gdx.graphics.GL20.GL_LINES
      } else {
        if (`type`.equals("POINTS")) {
          return com.badlogic.gdx.graphics.GL20.GL_POINTS
        } else {
          if (`type`.equals("TRIANGLE_STRIP")) {
            return com.badlogic.gdx.graphics.GL20.GL_TRIANGLE_STRIP
          } else {
            if (`type`.equals("LINE_STRIP")) {
              return com.badlogic.gdx.graphics.GL20.GL_LINE_STRIP
            } else {
              throw new com.badlogic.gdx.utils.GdxRuntimeException(("Unknown primitive type '" + `type`) + "', should be one of triangle, trianglestrip, line, linestrip or point")
            }
          }
        }
      }
    }
  }
  def parseAttributes(attributes: com.badlogic.gdx.utils.JsonValue): scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = {
    val vertexAttributes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.VertexAttribute]()
    var unit: scala.Int = 0
    var blendWeightCount: scala.Int = 0;
    { var value: com.badlogic.gdx.utils.JsonValue = attributes.child$field; while (value != null) { {
      val attribute: java.lang.String = value.asString()
      val attr: java.lang.String = attribute.asInstanceOf[java.lang.String]
      if (attr.equals("POSITION")) {
        vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.Position())
      } else {
        if (attr.equals("NORMAL")) {
          vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.Normal())
        } else {
          if (attr.equals("COLOR")) {
            vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.ColorUnpacked())
          } else {
            if (attr.equals("COLORPACKED")) {
              vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.ColorPacked())
            } else {
              if (attr.equals("TANGENT")) {
                vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.Tangent())
              } else {
                if (attr.equals("BINORMAL")) {
                  vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.Binormal())
                } else {
                  if (attr.startsWith("TEXCOORD")) {
                    vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.TexCoords({ unit += 1; unit }))
                  } else {
                    if (attr.startsWith("BLENDWEIGHT")) {
                      vertexAttributes.add(com.badlogic.gdx.graphics.VertexAttribute.BoneWeight({ blendWeightCount += 1; blendWeightCount }))
                    } else {
                      throw new com.badlogic.gdx.utils.GdxRuntimeException(("Unknown vertex attribute '" + attr) + "', should be one of position, normal, uv, tangent or binormal")
                    }
                  }
                }
              }
            }
          }
        }
      }
    }; value = value.next$field } }
    return vertexAttributes.toArray(((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.VertexAttribute](size)))
  }
  def parseMaterials(model: com.badlogic.gdx.graphics.g3d.model.data.ModelData, json: com.badlogic.gdx.utils.JsonValue, materialDir: java.lang.String): scala.Unit = {
    val materials: com.badlogic.gdx.utils.JsonValue = json.get("materials")
    if (materials == null) {
      ()
    } else {
      model.materials.ensureCapacity(materials.size$field);
      { var material: com.badlogic.gdx.utils.JsonValue = materials.child$field; while (material != null) { {
        val jsonMaterial: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = new com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial()
        var id: java.lang.String = material.getString("id", null)
        if (id == null) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Material needs an id.")
        } else ()
        jsonMaterial.id = id
        var diffuse: com.badlogic.gdx.utils.JsonValue = material.get("diffuse")
        if (diffuse != null) {
          jsonMaterial.diffuse = this.parseColor(diffuse)
        } else ()
        var ambient: com.badlogic.gdx.utils.JsonValue = material.get("ambient")
        if (ambient != null) {
          jsonMaterial.ambient = this.parseColor(ambient)
        } else ()
        var emissive: com.badlogic.gdx.utils.JsonValue = material.get("emissive")
        if (emissive != null) {
          jsonMaterial.emissive = this.parseColor(emissive)
        } else ()
        var specular: com.badlogic.gdx.utils.JsonValue = material.get("specular")
        if (specular != null) {
          jsonMaterial.specular = this.parseColor(specular)
        } else ()
        var reflection: com.badlogic.gdx.utils.JsonValue = material.get("reflection")
        if (reflection != null) {
          jsonMaterial.reflection = this.parseColor(reflection)
        } else ()
        jsonMaterial.shininess = material.getFloat("shininess", 0.0f)
        jsonMaterial.opacity = material.getFloat("opacity", 1.0f)
        var textures: com.badlogic.gdx.utils.JsonValue = material.get("textures")
        if (textures != null) {
          { var texture: com.badlogic.gdx.utils.JsonValue = textures.child$field; while (texture != null) { {
            val jsonTexture: com.badlogic.gdx.graphics.g3d.model.data.ModelTexture = new com.badlogic.gdx.graphics.g3d.model.data.ModelTexture()
            val textureId: java.lang.String = texture.getString("id", null)
            if (textureId == null) {
              throw new com.badlogic.gdx.utils.GdxRuntimeException("Texture has no id.")
            } else ()
            jsonTexture.id = textureId
            var fileName: java.lang.String = texture.getString("filename", null)
            if (fileName == null) {
              throw new com.badlogic.gdx.utils.GdxRuntimeException("Texture needs filename.")
            } else ()
            jsonTexture.fileName = (materialDir + (if ((materialDir.length() == 0) || materialDir.endsWith("/")) "" else "/")) + fileName
            jsonTexture.uvTranslation = this.readVector2(texture.get("uvTranslation"), 0.0f, 0.0f)
            jsonTexture.uvScaling = this.readVector2(texture.get("uvScaling"), 1.0f, 1.0f)
            val textureType: java.lang.String = texture.getString("type", null)
            if (textureType == null) {
              throw new com.badlogic.gdx.utils.GdxRuntimeException("Texture needs type.")
            } else ()
            jsonTexture.usage = this.parseTextureUsage(textureType)
            if (jsonMaterial.textures == null) {
              jsonMaterial.textures = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelTexture]()
            } else ()
            jsonMaterial.textures.add(jsonTexture)
          }; texture = texture.next$field } }
        } else ()
        model.materials.add(jsonMaterial)
      }; material = material.next$field } }
    }
  }
  def parseTextureUsage(value: java.lang.String): scala.Int = {
    if (value.equalsIgnoreCase("AMBIENT")) {
      return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_AMBIENT
    } else {
      if (value.equalsIgnoreCase("BUMP")) {
        return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_BUMP
      } else {
        if (value.equalsIgnoreCase("DIFFUSE")) {
          return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_DIFFUSE
        } else {
          if (value.equalsIgnoreCase("EMISSIVE")) {
            return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_EMISSIVE
          } else {
            if (value.equalsIgnoreCase("NONE")) {
              return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_NONE
            } else {
              if (value.equalsIgnoreCase("NORMAL")) {
                return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_NORMAL
              } else {
                if (value.equalsIgnoreCase("REFLECTION")) {
                  return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_REFLECTION
                } else {
                  if (value.equalsIgnoreCase("SHININESS")) {
                    return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_SHININESS
                  } else {
                    if (value.equalsIgnoreCase("SPECULAR")) {
                      return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_SPECULAR
                    } else {
                      if (value.equalsIgnoreCase("TRANSPARENCY")) {
                        return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_TRANSPARENCY
                      } else ()
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
    return com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_UNKNOWN
  }
  def parseColor(colorArray: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.graphics.Color = {
    if (colorArray.size$field >= 3) {
      return new com.badlogic.gdx.graphics.Color(colorArray.getFloat(0), colorArray.getFloat(1), colorArray.getFloat(2), 1.0f)
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Expected Color values <> than three.")
    }
  }
  def readVector2(vectorArray: com.badlogic.gdx.utils.JsonValue, x: scala.Float, y: scala.Float): com.badlogic.gdx.math.Vector2 = {
    if (vectorArray == null) {
      return new com.badlogic.gdx.math.Vector2(x, y)
    } else {
      if (vectorArray.size$field == 2) {
        return new com.badlogic.gdx.math.Vector2(vectorArray.getFloat(0), vectorArray.getFloat(1))
      } else {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Expected Vector2 values <> than two.")
      }
    }
  }
  def parseNodes(model: com.badlogic.gdx.graphics.g3d.model.data.ModelData, json: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNode] = {
    val nodes: com.badlogic.gdx.utils.JsonValue = json.get("nodes")
    if (nodes != null) {
      model.nodes.ensureCapacity(nodes.size$field);
      { var node: com.badlogic.gdx.utils.JsonValue = nodes.child$field; while (node != null) { {
        model.nodes.add(this.parseNodesRecursively(node))
      }; node = node.next$field } }
    } else ()
    return model.nodes
  }
  def parseNodesRecursively(json: com.badlogic.gdx.utils.JsonValue): com.badlogic.gdx.graphics.g3d.model.data.ModelNode = {
    val jsonNode: com.badlogic.gdx.graphics.g3d.model.data.ModelNode = new com.badlogic.gdx.graphics.g3d.model.data.ModelNode()
    var id: java.lang.String = json.getString("id", null)
    if (id == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Node id missing.")
    } else ()
    jsonNode.id = id
    var translation: com.badlogic.gdx.utils.JsonValue = json.get("translation")
    if ((translation != null) && (translation.size$field != 3)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Node translation incomplete")
    } else ()
    jsonNode.translation = if (translation == null) null.asInstanceOf[com.badlogic.gdx.math.Vector3] else new com.badlogic.gdx.math.Vector3(translation.getFloat(0), translation.getFloat(1), translation.getFloat(2))
    var rotation: com.badlogic.gdx.utils.JsonValue = json.get("rotation")
    if ((rotation != null) && (rotation.size$field != 4)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Node rotation incomplete")
    } else ()
    jsonNode.rotation = if (rotation == null) null.asInstanceOf[com.badlogic.gdx.math.Quaternion] else new com.badlogic.gdx.math.Quaternion(rotation.getFloat(0), rotation.getFloat(1), rotation.getFloat(2), rotation.getFloat(3))
    var scale: com.badlogic.gdx.utils.JsonValue = json.get("scale")
    if ((scale != null) && (scale.size$field != 3)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Node scale incomplete")
    } else ()
    jsonNode.scale = if (scale == null) null.asInstanceOf[com.badlogic.gdx.math.Vector3] else new com.badlogic.gdx.math.Vector3(scale.getFloat(0), scale.getFloat(1), scale.getFloat(2))
    var meshId: java.lang.String = json.getString("mesh", null)
    if (meshId != null) {
      jsonNode.meshId = meshId
    } else ()
    val materials: com.badlogic.gdx.utils.JsonValue = json.get("parts")
    if (materials != null) {
      jsonNode.parts = new scala.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart](materials.size$field)
      var i: scala.Int = 0;
      { var material: com.badlogic.gdx.utils.JsonValue = materials.child$field; while (material != null) { {
        val nodePart: com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodePart()
        var meshPartId: java.lang.String = material.getString("meshpartid", null)
        var materialId: java.lang.String = material.getString("materialid", null)
        if ((meshPartId == null) || (materialId == null)) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Node " + id) + " part is missing meshPartId or materialId")
        } else ()
        nodePart.materialId = materialId
        nodePart.meshPartId = meshPartId
        var bones: com.badlogic.gdx.utils.JsonValue = material.get("bones")
        if (bones != null) {
          nodePart.bones = new com.badlogic.gdx.utils.ArrayMap[java.lang.String, com.badlogic.gdx.math.Matrix4](true, bones.size$field, ((size: scala.Int) => new scala.Array[java.lang.String](size)), ((size: scala.Int) => new scala.Array[com.badlogic.gdx.math.Matrix4](size)))
          var j: scala.Int = 0;
          { var bone: com.badlogic.gdx.utils.JsonValue = bones.child$field; while (bone != null) { {
            val nodeId: java.lang.String = bone.getString("node", null)
            if (nodeId == null) {
              throw new com.badlogic.gdx.utils.GdxRuntimeException("Bone node ID missing")
            } else ()
            val transform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
            var `val`: com.badlogic.gdx.utils.JsonValue = bone.get("translation")
            if ((`val` != null) && (`val`.size$field >= 3)) {
              transform.translate(`val`.getFloat(0), `val`.getFloat(1), `val`.getFloat(2))
            } else ()
            `val` = bone.get("rotation")
            if ((`val` != null) && (`val`.size$field >= 4)) {
              transform.rotate(this.tempQ.set(`val`.getFloat(0), `val`.getFloat(1), `val`.getFloat(2), `val`.getFloat(3)))
            } else ()
            `val` = bone.get("scale")
            if ((`val` != null) && (`val`.size$field >= 3)) {
              transform.scale(`val`.getFloat(0), `val`.getFloat(1), `val`.getFloat(2))
            } else ()
            nodePart.bones.put(nodeId, transform)
          }; bone = bone.next$field; j = j + 1 } }
        } else ()
        jsonNode.parts(i) = nodePart
      }; material = material.next$field; i = i + 1 } }
    } else ()
    var children: com.badlogic.gdx.utils.JsonValue = json.get("children")
    if (children != null) {
      jsonNode.children = new scala.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNode](children.size$field)
      var i: scala.Int = 0;
      { var child: com.badlogic.gdx.utils.JsonValue = children.child$field; while (child != null) { {
        jsonNode.children(i) = this.parseNodesRecursively(child)
      }; child = child.next$field; i = i + 1 } }
    } else ()
    return jsonNode
  }
  def parseAnimations(model: com.badlogic.gdx.graphics.g3d.model.data.ModelData, json: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    val animations: com.badlogic.gdx.utils.JsonValue = json.get("animations")
    if (animations == null) {
      return
    } else ()
    model.animations.ensureCapacity(animations.size$field);
    { var anim: com.badlogic.gdx.utils.JsonValue = animations.child$field; while (anim != null) { {
      val nodes: com.badlogic.gdx.utils.JsonValue = anim.get("bones")
      if (nodes == null) {
        /* continue */ ()
      } else ()
      val animation: com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation = new com.badlogic.gdx.graphics.g3d.model.data.ModelAnimation()
      model.animations.add(animation)
      animation.nodeAnimations.ensureCapacity(nodes.size$field)
      animation.id = anim.getString("id");
      { var node: com.badlogic.gdx.utils.JsonValue = nodes.child$field; while (node != null) { {
        val nodeAnim: com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodeAnimation()
        animation.nodeAnimations.add(nodeAnim)
        nodeAnim.nodeId = node.getString("boneId")
        val keyframes: com.badlogic.gdx.utils.JsonValue = node.get("keyframes")
        if ((keyframes != null) && keyframes.isArray()) {
          { var keyframe: com.badlogic.gdx.utils.JsonValue = keyframes.child$field; while (keyframe != null) { {
            var keytime: scala.Float = keyframe.getFloat("keytime", 0.0f) / 1000.0f
            var translation: com.badlogic.gdx.utils.JsonValue = keyframe.get("translation")
            if ((translation != null) && (translation.size$field == 3)) {
              if (nodeAnim.translation == null) {
                nodeAnim.translation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]]()
              } else ()
              val tkf: com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3] = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]()
              tkf.keytime = keytime
              tkf.value = new com.badlogic.gdx.math.Vector3(translation.getFloat(0), translation.getFloat(1), translation.getFloat(2))
              nodeAnim.translation.add(tkf)
            } else ()
            var rotation: com.badlogic.gdx.utils.JsonValue = keyframe.get("rotation")
            if ((rotation != null) && (rotation.size$field == 4)) {
              if (nodeAnim.rotation == null) {
                nodeAnim.rotation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Quaternion]]()
              } else ()
              val rkf: com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Quaternion] = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Quaternion]()
              rkf.keytime = keytime
              rkf.value = new com.badlogic.gdx.math.Quaternion(rotation.getFloat(0), rotation.getFloat(1), rotation.getFloat(2), rotation.getFloat(3))
              nodeAnim.rotation.add(rkf)
            } else ()
            val scale: com.badlogic.gdx.utils.JsonValue = keyframe.get("scale")
            if ((scale != null) && (scale.size$field == 3)) {
              if (nodeAnim.scaling == null) {
                nodeAnim.scaling = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]]()
              } else ()
              val skf: com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3] = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.graphics.g3d.Model]().asInstanceOf[com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]]
              skf.keytime = keytime
              skf.value = new com.badlogic.gdx.math.Vector3(scale.getFloat(0), scale.getFloat(1), scale.getFloat(2))
              nodeAnim.scaling.add(skf)
            } else ()
          }; keyframe = keyframe.next$field } }
        } else {
          val translationKF: com.badlogic.gdx.utils.JsonValue = node.get("translation")
          if ((translationKF != null) && translationKF.isArray()) {
            nodeAnim.translation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]]()
            nodeAnim.translation.ensureCapacity(translationKF.size$field);
            { var keyframe: com.badlogic.gdx.utils.JsonValue = translationKF.child$field; while (keyframe != null) { {
              val kf: com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3] = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]()
              nodeAnim.translation.add(kf)
              kf.keytime = keyframe.getFloat("keytime", 0.0f) / 1000.0f
              var translation: com.badlogic.gdx.utils.JsonValue = keyframe.get("value")
              if ((translation != null) && (translation.size$field >= 3)) {
                kf.value = new com.badlogic.gdx.math.Vector3(translation.getFloat(0), translation.getFloat(1), translation.getFloat(2))
              } else ()
            }; keyframe = keyframe.next$field } }
          } else ()
          val rotationKF: com.badlogic.gdx.utils.JsonValue = node.get("rotation")
          if ((rotationKF != null) && rotationKF.isArray()) {
            nodeAnim.rotation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Quaternion]]()
            nodeAnim.rotation.ensureCapacity(rotationKF.size$field);
            { var keyframe: com.badlogic.gdx.utils.JsonValue = rotationKF.child$field; while (keyframe != null) { {
              val kf: com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Quaternion] = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Quaternion]()
              nodeAnim.rotation.add(kf)
              kf.keytime = keyframe.getFloat("keytime", 0.0f) / 1000.0f
              var rotation: com.badlogic.gdx.utils.JsonValue = keyframe.get("value")
              if ((rotation != null) && (rotation.size$field >= 4)) {
                kf.value = new com.badlogic.gdx.math.Quaternion(rotation.getFloat(0), rotation.getFloat(1), rotation.getFloat(2), rotation.getFloat(3))
              } else ()
            }; keyframe = keyframe.next$field } }
          } else ()
          val scalingKF: com.badlogic.gdx.utils.JsonValue = node.get("scaling")
          if ((scalingKF != null) && scalingKF.isArray()) {
            nodeAnim.scaling = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]]()
            nodeAnim.scaling.ensureCapacity(scalingKF.size$field);
            { var keyframe: com.badlogic.gdx.utils.JsonValue = scalingKF.child$field; while (keyframe != null) { {
              val kf: com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3] = new com.badlogic.gdx.graphics.g3d.model.data.ModelNodeKeyframe[com.badlogic.gdx.math.Vector3]()
              nodeAnim.scaling.add(kf)
              kf.keytime = keyframe.getFloat("keytime", 0.0f) / 1000.0f
              var scaling: com.badlogic.gdx.utils.JsonValue = keyframe.get("value")
              if ((scaling != null) && (scaling.size$field >= 3)) {
                kf.value = new com.badlogic.gdx.math.Vector3(scaling.getFloat(0), scaling.getFloat(1), scaling.getFloat(2))
              } else ()
            }; keyframe = keyframe.next$field } }
          } else ()
        }
      }; node = node.next$field } }
    }; anim = anim.next$field } }
  }
}
object G3dModelLoader {
  export com.badlogic.gdx.assets.loaders.ModelLoader.{VERSION_HI => _, VERSION_LO => _, *}
  final val VERSION_HI: scala.Short = 0.asInstanceOf[scala.Short]
  final val VERSION_LO: scala.Short = 1.asInstanceOf[scala.Short]
}