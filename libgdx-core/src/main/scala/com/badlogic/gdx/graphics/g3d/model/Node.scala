package com.badlogic.gdx.graphics.g3d.model

class Node {
  var id: java.lang.String = null.asInstanceOf[java.lang.String]
  var inheritTransform: scala.Boolean = true
  var isAnimated: scala.Boolean = false
  final val translation: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val rotation: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion(0, 0, 0, 1)
  final val scale: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(1, 1, 1)
  final val localTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  final val globalTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  var parts: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodePart] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodePart](2)
  var parent: Node = null.asInstanceOf[Node]
  private final val children: com.badlogic.gdx.utils.Array[Node] = new com.badlogic.gdx.utils.Array[Node](2)
  def calculateLocalTransform(): com.badlogic.gdx.math.Matrix4 = {
    if (!this.isAnimated) {
      this.localTransform.set(this.translation, this.rotation, this.scale)
    } else ()
    return this.localTransform
  }
  def calculateWorldTransform(): com.badlogic.gdx.math.Matrix4 = {
    if (this.inheritTransform && (this.parent != null)) {
      this.globalTransform.set(this.parent.globalTransform).mul(this.localTransform)
    } else {
      this.globalTransform.set(this.localTransform)
    }
    return this.globalTransform
  }
  def calculateTransforms(recursive: scala.Boolean): scala.Unit = {
    this.calculateLocalTransform()
    this.calculateWorldTransform()
    if (recursive) {
      for (child <- this.children) {
        child.calculateTransforms(true)
      }
    } else ()
  }
  def calculateBoneTransforms(recursive: scala.Boolean): scala.Unit = {
    for (part <- this.parts) {
      if (((part.invBoneBindTransforms == null) || (part.bones == null)) || (part.invBoneBindTransforms.size != part.bones.length)) {
        /* continue */ ()
      } else ()
      val n: scala.Int = part.invBoneBindTransforms.size;
      { var i: scala.Int = 0; while (i < n) { {
        part.bones(i).set(part.invBoneBindTransforms.keys$field(i).globalTransform).mul(part.invBoneBindTransforms.values$field(i))
      }; i = i + 1 } }
    }
    if (recursive) {
      for (child <- this.children) {
        child.calculateBoneTransforms(true)
      }
    } else ()
  }
  def calculateBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox): com.badlogic.gdx.math.collision.BoundingBox = {
    out.inf()
    return this.extendBoundingBox(out)
  }
  def calculateBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox, transform: scala.Boolean): com.badlogic.gdx.math.collision.BoundingBox = {
    out.inf()
    return this.extendBoundingBox(out, transform)
  }
  def extendBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox): com.badlogic.gdx.math.collision.BoundingBox = {
    return this.extendBoundingBox(out, true)
  }
  def extendBoundingBox(out: com.badlogic.gdx.math.collision.BoundingBox, transform: scala.Boolean): com.badlogic.gdx.math.collision.BoundingBox = {
    val partCount: scala.Int = this.parts.size;
    { var i: scala.Int = 0; while (i < partCount) { {
      val part: com.badlogic.gdx.graphics.g3d.model.NodePart = this.parts.get(i)
      if (part.enabled) {
        val meshPart: com.badlogic.gdx.graphics.g3d.model.MeshPart = part.meshPart
        if (transform) {
          meshPart.mesh.extendBoundingBox(out, meshPart.offset, meshPart.size, this.globalTransform)
        } else {
          meshPart.mesh.extendBoundingBox(out, meshPart.offset, meshPart.size)
        }
      } else ()
    }; i = i + 1 } }
    val childCount: scala.Int = this.children.size;
    { var i: scala.Int = 0; while (i < childCount) { {
      this.children.get(i).extendBoundingBox(out)
    }; i = i + 1 } }
    return out
  }
  def attachTo[T <: Node](parent: T): scala.Unit = {
    parent.addChild(this)
  }
  def detach(): scala.Unit = {
    if (this.parent != null) {
      this.parent.removeChild(this)
      this.parent = null
    } else ()
  }
  def hasChildren(): scala.Boolean = {
    return (this.children != null) && (this.children.size > 0)
  }
  def getChildCount(): scala.Int = {
    return this.children.size
  }
  def getChild(index: scala.Int): Node = {
    return this.children.get(index)
  }
  def getChild(id: java.lang.String, recursive: scala.Boolean, ignoreCase: scala.Boolean): Node = {
    return Node.getNode(this.children, id, recursive, ignoreCase)
  }
  def addChild[T <: Node](child: T): scala.Int = {
    return this.insertChild(-1, child)
  }
  def addChildren[T <: Node](nodes: balticporter.runtime.JavaIterable[T]): scala.Int = {
    return this.insertChildren(-1, nodes)
  }
  def insertChild[T <: Node](index$arg: scala.Int, child: T): scala.Int = {
    var index: scala.Int = index$arg;
    { var p: Node = this; while (p != null) { {
      if (p == child) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot add a parent as a child")
      } else ()
    }; p = p.getParent() } }
    var p: Node = child.getParent()
    if ((p != null) && (!p.removeChild(child))) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Could not remove child from its current parent")
    } else ()
    if ((index < 0) || (index >= this.children.size)) {
      index = this.children.size
      this.children.add(child)
    } else {
      this.children.insert(index, child)
    }
    child.parent = this
    return index
  }
  def insertChildren[T <: Node](index$arg: scala.Int, nodes: balticporter.runtime.JavaIterable[T]): scala.Int = {
    var index: scala.Int = index$arg
    if ((index < 0) || (index > this.children.size)) {
      index = this.children.size
    } else ()
    var i: scala.Int = index
    for (child <- nodes) {
      this.insertChild({ i += 1; i }, child)
    }
    return index
  }
  def removeChild[T <: Node](child: T): scala.Boolean = {
    if (!this.children.removeValue(child, true)) {
      return false
    } else ()
    child.parent = null
    return true
  }
  def getChildren(): balticporter.runtime.JavaIterable[Node] = {
    return this.children
  }
  def getParent(): Node = {
    return this.parent
  }
  def hasParent(): scala.Boolean = {
    return this.parent != null
  }
  def copy(): Node = {
    return new Node().set(this)
  }
  def set(other: Node): Node = {
    this.detach()
    this.id = other.id
    this.isAnimated = other.isAnimated
    this.inheritTransform = other.inheritTransform
    this.translation.set(other.translation)
    this.rotation.set(other.rotation)
    this.scale.set(other.scale)
    this.localTransform.set(other.localTransform)
    this.globalTransform.set(other.globalTransform)
    this.parts.clear()
    for (nodePart <- other.parts) {
      this.parts.add(nodePart.copy())
    }
    this.children.clear()
    for (child <- other.getChildren()) {
      this.addChild(child.copy())
    }
    return this
  }
}
object Node {
  def getNode(nodes: com.badlogic.gdx.utils.Array[Node], id: java.lang.String, recursive: scala.Boolean, ignoreCase: scala.Boolean): Node = {
    val n: scala.Int = nodes.size
    var node: Node = null.asInstanceOf[Node]
    if (ignoreCase) {
      { var i: scala.Int = 0; while (i < n) { {
        if ({
          node = nodes.get(i)
          node
        }.id.equalsIgnoreCase(id)) {
          return node
        } else ()
      }; i = i + 1 } }
    } else {
      { var i: scala.Int = 0; while (i < n) { {
        if ({
          node = nodes.get(i)
          node
        }.id.equals(id)) {
          return node
        } else ()
      }; i = i + 1 } }
    }
    if (recursive) {
      { var i: scala.Int = 0; while (i < n) { {
        if ({
          node = Node.getNode(nodes.get(i).children, id, true, ignoreCase)
          node
        } != null) {
          return node
        } else ()
      }; i = i + 1 } }
    } else ()
    return null
  }
}