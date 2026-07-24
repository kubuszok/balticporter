package com.badlogic.gdx.scenes.scene2d

class Group extends com.badlogic.gdx.scenes.scene2d.Actor with com.badlogic.gdx.scenes.scene2d.utils.Cullable {
  final val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = new com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor](true, 4, scala.Array[com.badlogic.gdx.scenes.scene2d.Actor].<init>)
  private final val worldTransform: com.badlogic.gdx.math.Affine2 = new com.badlogic.gdx.math.Affine2()
  private final val computedTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  private final val oldTransform: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
  var transform: scala.Boolean = true
  private var cullingArea: com.badlogic.gdx.math.Rectangle = null.asInstanceOf[com.badlogic.gdx.math.Rectangle]
  def act(delta: scala.Float): scala.Unit = {
    super.act(delta)
    val actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.children.begin()
    { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
      actors(i).act(delta)
    }; i = i + 1 } }
    this.children.`end`()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    if (this.transform) {
      this.applyTransform(batch, this.computeTransform())
    } else ()
    this.drawChildren(batch, parentAlpha)
    if (this.transform) {
      this.resetTransform(batch)
    } else ()
  }
  protected def drawChildren(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha$arg: scala.Float): scala.Unit = {
    var parentAlpha: scala.Float = parentAlpha$arg
    parentAlpha = parentAlpha * this.color.a
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = this.children
    val actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = children.begin()
    val cullingArea: com.badlogic.gdx.math.Rectangle = this.cullingArea
    if (cullingArea != null) {
      val cullLeft: scala.Float = cullingArea.x
      val cullRight: scala.Float = cullLeft + cullingArea.width
      val cullBottom: scala.Float = cullingArea.y
      val cullTop: scala.Float = cullBottom + cullingArea.height
      if (this.transform) {
        { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
          val child: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
          if (!child.isVisible()) {
            /* continue */ ()
          } else ()
          val cx: scala.Float = child.x
          val cy: scala.Float = child.y
          if ((((cx <= cullRight) && (cy <= cullTop)) && ((cx + child.width) >= cullLeft)) && ((cy + child.height) >= cullBottom)) {
            child.draw(batch, parentAlpha)
          } else ()
        }; i = i + 1 } }
      } else {
        val offsetX: scala.Float = x
        val offsetY: scala.Float = y
        x = 0
        y = 0
        { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
          val child: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
          if (!child.isVisible()) {
            /* continue */ ()
          } else ()
          val cx: scala.Float = child.x
          val cy: scala.Float = child.y
          if ((((cx <= cullRight) && (cy <= cullTop)) && ((cx + child.width) >= cullLeft)) && ((cy + child.height) >= cullBottom)) {
            child.x = cx + offsetX
            child.y = cy + offsetY
            child.draw(batch, parentAlpha)
            child.x = cx
            child.y = cy
          } else ()
        }; i = i + 1 } }
        x = offsetX
        y = offsetY
      }
    } else {
      if (this.transform) {
        { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
          val child: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
          if (!child.isVisible()) {
            /* continue */ ()
          } else ()
          child.draw(batch, parentAlpha)
        }; i = i + 1 } }
      } else {
        val offsetX: scala.Float = x
        val offsetY: scala.Float = y
        x = 0
        y = 0
        { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
          val child: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
          if (!child.isVisible()) {
            /* continue */ ()
          } else ()
          val cx: scala.Float = child.x
          val cy: scala.Float = child.y
          child.x = cx + offsetX
          child.y = cy + offsetY
          child.draw(batch, parentAlpha)
          child.x = cx
          child.y = cy
        }; i = i + 1 } }
        x = offsetX
        y = offsetY
      }
    }
    children.`end`()
  }
  def drawDebug(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    this.drawDebugBounds(shapes)
    if (this.transform) {
      this.applyTransform(shapes, this.computeTransform())
    } else ()
    this.drawDebugChildren(shapes)
    if (this.transform) {
      this.resetTransform(shapes)
    } else ()
  }
  protected def drawDebugChildren(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    val children: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = this.children
    val actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = children.begin()
    if (this.transform) {
      { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
        val child: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
        if (!child.isVisible()) {
          /* continue */ ()
        } else ()
        if ((!child.getDebug()) && (!child.isInstanceOf[Group])) {
          /* continue */ ()
        } else ()
        child.drawDebug(shapes)
      }; i = i + 1 } }
      shapes.flush()
    } else {
      val offsetX: scala.Float = x
      val offsetY: scala.Float = y
      x = 0
      y = 0
      { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
        val child: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
        if (!child.isVisible()) {
          /* continue */ ()
        } else ()
        if ((!child.getDebug()) && (!child.isInstanceOf[Group])) {
          /* continue */ ()
        } else ()
        val cx: scala.Float = child.x
        val cy: scala.Float = child.y
        child.x = cx + offsetX
        child.y = cy + offsetY
        child.drawDebug(shapes)
        child.x = cx
        child.y = cy
      }; i = i + 1 } }
      x = offsetX
      y = offsetY
    }
    children.`end`()
  }
  protected def computeTransform(): com.badlogic.gdx.math.Matrix4 = {
    val worldTransform: com.badlogic.gdx.math.Affine2 = this.worldTransform
    val originX: scala.Float = this.originX
    val originY: scala.Float = this.originY
    worldTransform.setToTrnRotScl(x + originX, y + originY, rotation, scaleX, scaleY)
    if ((originX != 0) || (originY != 0)) {
      worldTransform.translate(-originX, -originY)
    } else ()
    var parentGroup: Group = parent
    while (parentGroup != null) {
      if (parentGroup.transform) {
        /* break */ ()
      } else ()
      parentGroup = parentGroup.parent
    }
    if (parentGroup != null) {
      worldTransform.preMul(parentGroup.worldTransform)
    } else ()
    this.computedTransform.set(worldTransform)
    return this.computedTransform
  }
  protected def applyTransform(batch: com.badlogic.gdx.graphics.g2d.Batch, transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.oldTransform.set(batch.getTransformMatrix())
    batch.setTransformMatrix(transform)
  }
  protected def resetTransform(batch: com.badlogic.gdx.graphics.g2d.Batch): scala.Unit = {
    batch.setTransformMatrix(this.oldTransform)
  }
  protected def applyTransform(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer, transform: com.badlogic.gdx.math.Matrix4): scala.Unit = {
    this.oldTransform.set(shapes.getTransformMatrix())
    shapes.setTransformMatrix(transform)
    shapes.flush()
  }
  protected def resetTransform(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    shapes.setTransformMatrix(this.oldTransform)
  }
  def setCullingArea(cullingArea: com.badlogic.gdx.math.Rectangle): scala.Unit = {
    this.cullingArea = cullingArea
  }
  def getCullingArea(): com.badlogic.gdx.math.Rectangle = {
    return this.cullingArea
  }
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    if (touchable && (this.getTouchable() == com.badlogic.gdx.scenes.scene2d.Touchable.disabled)) {
      return null
    } else ()
    if (!this.isVisible()) {
      return null
    } else ()
    val point: com.badlogic.gdx.math.Vector2 = Group.tmp
    val childrenArray: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.children.items
    { var i: scala.Int = this.children.size - 1; while (i >= 0) { {
      val child: com.badlogic.gdx.scenes.scene2d.Actor = childrenArray(i)
      child.parentToLocalCoordinates(point.set(x, y))
      val hit: com.badlogic.gdx.scenes.scene2d.Actor = child.hit(point.x, point.y, touchable)
      if (hit != null) {
        return hit
      } else ()
    }; i = i - 1 } }
    return super.hit(x, y, touchable)
  }
  protected def childrenChanged(): scala.Unit = {
    ()
  }
  def addActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (actor.parent != null) {
      if (actor.parent == this) {
        return
      } else ()
      actor.parent.removeActor(actor, false)
    } else ()
    this.children.add(actor)
    actor.setParent(this)
    actor.setStage(this.getStage())
    this.childrenChanged()
  }
  def addActorAt(index: scala.Int, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (actor.parent != null) {
      if (actor.parent == this) {
        return
      } else ()
      actor.parent.removeActor(actor, false)
    } else ()
    if (index >= this.children.size) {
      this.children.add(actor)
    } else {
      this.children.insert(index, actor)
    }
    actor.setParent(this)
    actor.setStage(this.getStage())
    this.childrenChanged()
  }
  def addActorBefore(actorBefore: com.badlogic.gdx.scenes.scene2d.Actor, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (actor.parent != null) {
      if (actor.parent == this) {
        return
      } else ()
      actor.parent.removeActor(actor, false)
    } else ()
    val index: scala.Int = this.children.indexOf(actorBefore, true)
    this.children.insert(index, actor)
    actor.setParent(this)
    actor.setStage(this.getStage())
    this.childrenChanged()
  }
  def addActorAfter(actorAfter: com.badlogic.gdx.scenes.scene2d.Actor, actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    if (actor.parent != null) {
      if (actor.parent == this) {
        return
      } else ()
      actor.parent.removeActor(actor, false)
    } else ()
    val index: scala.Int = this.children.indexOf(actorAfter, true)
    if ((index == this.children.size) || (index == (-1))) {
      this.children.add(actor)
    } else {
      this.children.insert(index + 1, actor)
    }
    actor.setParent(this)
    actor.setStage(this.getStage())
    this.childrenChanged()
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    return this.removeActor(actor, true)
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor, unfocus: scala.Boolean): scala.Boolean = {
    val index: scala.Int = this.children.indexOf(actor, true)
    if (index == (-1)) {
      return false
    } else ()
    this.removeActorAt(index, unfocus)
    return true
  }
  def removeActorAt(index: scala.Int, unfocus: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    val actor: com.badlogic.gdx.scenes.scene2d.Actor = this.children.removeIndex(index)
    val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
    if (stage != null) {
      if (unfocus) {
        stage.unfocus(actor)
      } else ()
      stage.actorRemoved(actor)
    } else ()
    actor.setParent(null)
    actor.setStage(null)
    this.childrenChanged()
    return actor
  }
  def clearChildren(): scala.Unit = {
    this.clearChildren(true)
  }
  def clearChildren(unfocus: scala.Boolean): scala.Unit = {
    val actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.children.begin()
    { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
      val child: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
      if (unfocus) {
        val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
        if (stage != null) {
          stage.unfocus(child)
        } else ()
      } else ()
      child.setStage(null)
      child.setParent(null)
    }; i = i + 1 } }
    this.children.`end`()
    this.children.clear()
    this.childrenChanged()
  }
  def clear(): scala.Unit = {
    super.clear()
    this.clearChildren(true)
  }
  def clear(unfocus: scala.Boolean): scala.Unit = {
    super.clear()
    this.clearChildren(unfocus)
  }
  def findActor[T <: com.badlogic.gdx.scenes.scene2d.Actor](name: java.lang.String): T = {
    val children: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.children
    { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
      if (name.equals(children.get(i).getName())) {
        return children.get(i).asInstanceOf[T]
      } else ()
    }; i = i + 1 } }
    { var i: scala.Int = 0; val n: scala.Int = children.size; while (i < n) { {
      val child: com.badlogic.gdx.scenes.scene2d.Actor = children.get(i)
      if (child.isInstanceOf[Group]) {
        val actor: com.badlogic.gdx.scenes.scene2d.Actor = child.asInstanceOf[Group].findActor(name)
        if (actor != null) {
          return actor.asInstanceOf[T]
        } else ()
      } else ()
    }; i = i + 1 } }
    return null
  }
  protected def setStage(stage: com.badlogic.gdx.scenes.scene2d.Stage): scala.Unit = {
    super.setStage(stage)
    val childrenArray: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.children.items
    { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
      childrenArray(i).setStage(stage)
    }; i = i + 1 } }
  }
  def swapActor(first: scala.Int, second: scala.Int): scala.Boolean = {
    val maxIndex: scala.Int = this.children.size
    if ((first < 0) || (first >= maxIndex)) {
      return false
    } else ()
    if ((second < 0) || (second >= maxIndex)) {
      return false
    } else ()
    this.children.swap(first, second)
    return true
  }
  def swapActor(first: com.badlogic.gdx.scenes.scene2d.Actor, second: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    val firstIndex: scala.Int = this.children.indexOf(first, true)
    val secondIndex: scala.Int = this.children.indexOf(second, true)
    if ((firstIndex == (-1)) || (secondIndex == (-1))) {
      return false
    } else ()
    this.children.swap(firstIndex, secondIndex)
    return true
  }
  def getChild(index: scala.Int): com.badlogic.gdx.scenes.scene2d.Actor = {
    return this.children.get(index)
  }
  def getChildren(): com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.scenes.scene2d.Actor] = {
    return this.children
  }
  def hasChildren(): scala.Boolean = {
    return this.children.size > 0
  }
  def setTransform(transform: scala.Boolean): scala.Unit = {
    this.transform = transform
  }
  def isTransform(): scala.Boolean = {
    return this.transform
  }
  def localToDescendantCoordinates(descendant: com.badlogic.gdx.scenes.scene2d.Actor, localCoords: com.badlogic.gdx.math.Vector2): com.badlogic.gdx.math.Vector2 = {
    val parent: Group = descendant.parent
    if (parent == null) {
      throw new java.lang.IllegalArgumentException("Actor is not a descendant: " + descendant)
    } else ()
    if (parent != this) {
      this.localToDescendantCoordinates(parent, localCoords)
    } else ()
    descendant.parentToLocalCoordinates(localCoords)
    return localCoords
  }
  def setDebug(enabled: scala.Boolean, recursively: scala.Boolean): scala.Unit = {
    this.setDebug(enabled)
    if (recursively) {
      for (child <- this.children) {
        if (child.isInstanceOf[Group]) {
          child.asInstanceOf[Group].setDebug(enabled, recursively)
        } else {
          child.setDebug(enabled)
        }
      }
    } else ()
  }
  def debugAll(): Group = {
    this.setDebug(true, true)
    return this
  }
  def toString(): java.lang.String = {
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(128)
    this.toString(buffer, 1)
    buffer.setLength(buffer.length() - 1)
    return buffer.toString()
  }
  def toString(buffer: java.lang.StringBuilder, indent: scala.Int): scala.Unit = {
    buffer.append(super.toString())
    buffer.append('\n')
    val actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.children.begin()
    { var i: scala.Int = 0; val n: scala.Int = this.children.size; while (i < n) { {
      { var ii: scala.Int = 0; while (ii < indent) { {
        buffer.append("|  ")
      }; ii = ii + 1 } }
      val actor: com.badlogic.gdx.scenes.scene2d.Actor = actors(i)
      if (actor.isInstanceOf[Group]) {
        actor.asInstanceOf[Group].toString(buffer, indent + 1)
      } else {
        buffer.append(actor)
        buffer.append('\n')
      }
    }; i = i + 1 } }
    this.children.`end`()
  }
}
object Group {
  private final val tmp: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
}