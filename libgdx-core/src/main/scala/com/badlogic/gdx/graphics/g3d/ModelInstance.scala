package com.badlogic.gdx.graphics.g3d

class ModelInstance extends com.badlogic.gdx.graphics.g3d.RenderableProvider {
  final val materials: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Material] = new com.badlogic.gdx.utils.Array()
  final val nodes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Node] = new com.badlogic.gdx.utils.Array()
  final val animations: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Animation] = new com.badlogic.gdx.utils.Array()
  var model: com.badlogic.gdx.graphics.g3d.Model = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Model]
  var transform: com.badlogic.gdx.math.Matrix4 = null.asInstanceOf[com.badlogic.gdx.math.Matrix4]
  var userData: java.lang.Object = null.asInstanceOf[java.lang.Object]
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4, nodeId: java.lang.String, recursive: scala.Boolean, parentTransform: scala.Boolean, mergeTransform: scala.Boolean, shareKeyframes: scala.Boolean) = {
    this()
    this.model = model
    this.transform = if (transform == null) new com.badlogic.gdx.math.Matrix4() else transform
    var copy: com.badlogic.gdx.graphics.g3d.model.Node = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.Node]
    val node: com.badlogic.gdx.graphics.g3d.model.Node = model.getNode(nodeId, recursive)
    this.nodes.add({
      copy = node.copy()
      copy
    })
    if (mergeTransform) {
      this.transform.mul(if (parentTransform) node.globalTransform else node.localTransform)
      copy.translation.set(0, 0, 0)
      copy.rotation.idt()
      copy.scale.set(1, 1, 1)
    } else {
      if (parentTransform && copy.hasParent()) {
        this.transform.mul(node.getParent().globalTransform)
      } else ()
    }
    this.invalidate()
    this.copyAnimations(model.animations, shareKeyframes)
    this.calculateTransforms()
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4, rootNodeIds: com.badlogic.gdx.utils.Array[java.lang.String], shareKeyframes: scala.Boolean) = {
    this()
    this.model = model
    this.transform = if (transform == null) new com.badlogic.gdx.math.Matrix4() else transform
    this.copyNodes(model.nodes, rootNodeIds)
    this.copyAnimations(model.animations, shareKeyframes)
    this.calculateTransforms()
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4, rootNodeIds: scala.Array[java.lang.String]) = {
    this()
    this.model = model
    this.transform = if (transform == null) new com.badlogic.gdx.math.Matrix4() else transform
    if (rootNodeIds == null) {
      this.copyNodes(model.nodes)
    } else {
      this.copyNodes(model.nodes, rootNodeIds)
    }
    this.copyAnimations(model.animations, ModelInstance.defaultShareKeyframes)
    this.calculateTransforms()
  }
  def this(copyFrom: ModelInstance, transform: com.badlogic.gdx.math.Matrix4, shareKeyframes: scala.Boolean) = {
    this()
    this.model = copyFrom.model
    this.transform = if (transform == null) new com.badlogic.gdx.math.Matrix4() else transform
    this.copyNodes(copyFrom.nodes)
    this.copyAnimations(copyFrom.animations, shareKeyframes)
    this.calculateTransforms()
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4, nodeId: java.lang.String, recursive: scala.Boolean, parentTransform: scala.Boolean, mergeTransform: scala.Boolean) = {
    this(model, transform, nodeId, recursive, parentTransform, mergeTransform, ModelInstance.defaultShareKeyframes)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4, nodeId: java.lang.String, parentTransform: scala.Boolean, mergeTransform: scala.Boolean) = {
    this(model, transform, nodeId, true, parentTransform, mergeTransform)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, nodeId: java.lang.String, recursive: scala.Boolean, parentTransform: scala.Boolean, mergeTransform: scala.Boolean) = {
    this(model, null, nodeId, recursive, parentTransform, mergeTransform)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4, nodeId: java.lang.String, mergeTransform: scala.Boolean) = {
    this(model, transform, nodeId, false, false, mergeTransform)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, nodeId: java.lang.String, parentTransform: scala.Boolean, mergeTransform: scala.Boolean) = {
    this(model, null, nodeId, true, parentTransform, mergeTransform)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, x: scala.Float, y: scala.Float, z: scala.Float) = {
    this(model)
    this.transform.setToTranslation(x, y, z)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, nodeId: java.lang.String, mergeTransform: scala.Boolean) = {
    this(model, null, nodeId, false, false, mergeTransform)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4, rootNodeIds: com.badlogic.gdx.utils.Array[java.lang.String]) = {
    this(model, transform, rootNodeIds, ModelInstance.defaultShareKeyframes)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, rootNodeIds: scala.Array[java.lang.String]) = {
    this(model, null, rootNodeIds)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, rootNodeIds: com.badlogic.gdx.utils.Array[java.lang.String]) = {
    this(model, null, rootNodeIds)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, position: com.badlogic.gdx.math.Vector3) = {
    this(model)
    this.transform.setToTranslation(position)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model, transform: com.badlogic.gdx.math.Matrix4) = {
    this(model, transform, null.asInstanceOf[scala.Array[java.lang.String]])
  }
  def this(copyFrom: ModelInstance, transform: com.badlogic.gdx.math.Matrix4) = {
    this(copyFrom, transform, ModelInstance.defaultShareKeyframes)
  }
  def this(model: com.badlogic.gdx.graphics.g3d.Model) = {
    this(model, null.asInstanceOf[scala.Array[java.lang.String]])
  }
  def this(copyFrom: ModelInstance) = {
    this(copyFrom, copyFrom.transform.cpy())
  }
  def copy(): ModelInstance = {
    return new ModelInstance(this)
  }
  private def copyNodes(nodes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Node]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: com.badlogic.gdx.graphics.g3d.model.Node = nodes.get(i)
      this.nodes.add(node.copy())
    }; i = i + 1 } }
    this.invalidate()
  }
  private def copyNodes(nodes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Node], nodeIds: scala.Array[java.lang.String]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: com.badlogic.gdx.graphics.g3d.model.Node = nodes.get(i)
      for (nodeId <- nodeIds) {
        if (nodeId.equals(node.id)) {
          this.nodes.add(node.copy())
          /* break */ ()
        } else ()
      }
    }; i = i + 1 } }
    this.invalidate()
  }
  private def copyNodes(nodes: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.Node], nodeIds: com.badlogic.gdx.utils.Array[java.lang.String]): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = nodes.size; while (i < n) { {
      val node: com.badlogic.gdx.graphics.g3d.model.Node = nodes.get(i)
      for (nodeId <- nodeIds) {
        if (nodeId.equals(node.id)) {
          this.nodes.add(node.copy())
          /* break */ ()
        } else ()
      }
    }; i = i + 1 } }
    this.invalidate()
  }
  private def invalidate(node: com.badlogic.gdx.graphics.g3d.model.Node): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = node.parts.size; while (i < n) { {
      val part: com.badlogic.gdx.graphics.g3d.model.NodePart = node.parts.get(i)
      val bindPose: com.badlogic.gdx.utils.ArrayMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.math.Matrix4] = part.invBoneBindTransforms
      if (bindPose != null) {
        { var j: scala.Int = 0; while (j < bindPose.size) { {
          bindPose.keys$field(j) = this.getNode(bindPose.keys$field(j).id)
        }; j = j + 1 } }
      } else ()
      if (!this.materials.contains(part.material, true)) {
        val midx: scala.Int = this.materials.indexOf(part.material, false)
        if (midx < 0) {
          this.materials.add({
            part.material = part.material.copy()
            part.material
          })
        } else {
          part.material = this.materials.get(midx)
        }
      } else ()
    }; i = i + 1 } }
    { var i: scala.Int = 0; val n: scala.Int = node.getChildCount(); while (i < n) { {
      this.invalidate(node.getChild(i))
    }; i = i + 1 } }
  }
  private def invalidate(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.nodes.size; while (i < n) { {
      this.invalidate(this.nodes.get(i))
    }; i = i + 1 } }
  }
  def copyAnimations(source: scala.collection.Iterable[com.badlogic.gdx.graphics.g3d.model.Animation]): scala.Unit = {
    for (anim <- source) {
      this.copyAnimation(anim, ModelInstance.defaultShareKeyframes)
    }
  }
  def copyAnimations(source: scala.collection.Iterable[com.badlogic.gdx.graphics.g3d.model.Animation], shareKeyframes: scala.Boolean): scala.Unit = {
    for (anim <- source) {
      this.copyAnimation(anim, shareKeyframes)
    }
  }
  def copyAnimation(sourceAnim: com.badlogic.gdx.graphics.g3d.model.Animation): scala.Unit = {
    this.copyAnimation(sourceAnim, ModelInstance.defaultShareKeyframes)
  }
  def copyAnimation(sourceAnim: com.badlogic.gdx.graphics.g3d.model.Animation, shareKeyframes: scala.Boolean): scala.Unit = {
    val animation: com.badlogic.gdx.graphics.g3d.model.Animation = new com.badlogic.gdx.graphics.g3d.model.Animation()
    animation.id = sourceAnim.id
    animation.duration = sourceAnim.duration
    for (nanim <- sourceAnim.nodeAnimations) {
      var node: com.badlogic.gdx.graphics.g3d.model.Node = this.getNode(nanim.node.id)
      if (node == null) {
        /* continue */ ()
      } else ()
      val nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation = new com.badlogic.gdx.graphics.g3d.model.NodeAnimation()
      nodeAnim.node = node
      if (shareKeyframes) {
        nodeAnim.translation = nanim.translation
        nodeAnim.rotation = nanim.rotation
        nodeAnim.scaling = nanim.scaling
      } else {
        if (nanim.translation != null) {
          nodeAnim.translation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3]]()
          for (kf <- nanim.translation) {
            nodeAnim.translation.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3](kf.keytime, kf.value))
          }
        } else ()
        if (nanim.rotation != null) {
          nodeAnim.rotation = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Quaternion]]()
          for (kf <- nanim.rotation) {
            nodeAnim.rotation.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Quaternion](kf.keytime, kf.value))
          }
        } else ()
        if (nanim.scaling != null) {
          nodeAnim.scaling = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3]]()
          for (kf <- nanim.scaling) {
            nodeAnim.scaling.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3](kf.keytime, kf.value))
          }
        } else ()
      }
      if (((nodeAnim.translation != null) || (nodeAnim.rotation != null)) || (nodeAnim.scaling != null)) {
        animation.nodeAnimations.add(nodeAnim)
      } else ()
    }
    if (animation.nodeAnimations.size > 0) {
      this.animations.add(animation)
    } else ()
  }
  def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    for (node <- this.nodes) {
      this.getRenderables(node, renderables, pool)
    }
  }
  def getRenderable(out: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Renderable = {
    return this.getRenderable(out, this.nodes.get(0))
  }
  def getRenderable(out: com.badlogic.gdx.graphics.g3d.Renderable, node: com.badlogic.gdx.graphics.g3d.model.Node): com.badlogic.gdx.graphics.g3d.Renderable = {
    return this.getRenderable(out, node, node.parts.get(0))
  }
  def getRenderable(out: com.badlogic.gdx.graphics.g3d.Renderable, node: com.badlogic.gdx.graphics.g3d.model.Node, nodePart: com.badlogic.gdx.graphics.g3d.model.NodePart): com.badlogic.gdx.graphics.g3d.Renderable = {
    nodePart.setRenderable(out)
    if ((nodePart.bones == null) && (this.transform != null)) {
      out.worldTransform.set(this.transform).mul(node.globalTransform)
    } else {
      if (this.transform != null) {
        out.worldTransform.set(this.transform)
      } else {
        out.worldTransform.idt()
      }
    }
    out.userData = this.userData
    return out
  }
  protected def getRenderables(node: com.badlogic.gdx.graphics.g3d.model.Node, renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    if (node.parts.size > 0) {
      for (nodePart <- node.parts) {
        if (nodePart.enabled) {
          renderables.add(this.getRenderable(pool.obtain(), node, nodePart))
        } else ()
      }
    } else ()
    for (child <- node.getChildren()) {
      this.getRenderables(child, renderables, pool)
    }
  }
  def calculateTransforms(): scala.Unit = {
    val n: scala.Int = this.nodes.size
    { var i: scala.Int = 0; while (i < n) { {
      this.nodes.get(i).calculateTransforms(true)
    }; i = i + 1 } }
    { var i: scala.Int = 0; while (i < n) { {
      this.nodes.get(i).calculateBoneTransforms(true)
    }; i = i + 1 } }
  }
  def calculateBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox): com.badlogic.gdx.math.collision.BoundingBox = {
    out.inf()
    return this.extendBoundingBox(out)
  }
  def extendBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox): com.badlogic.gdx.math.collision.BoundingBox = {
    val n: scala.Int = this.nodes.size
    { var i: scala.Int = 0; while (i < n) { {
      this.nodes.get(i).extendBoundingBox(out)
    }; i = i + 1 } }
    return out
  }
  def getAnimation(id: java.lang.String): com.badlogic.gdx.graphics.g3d.model.Animation = {
    return this.getAnimation(id, false)
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
object ModelInstance {
  var defaultShareKeyframes: scala.Boolean = true
}