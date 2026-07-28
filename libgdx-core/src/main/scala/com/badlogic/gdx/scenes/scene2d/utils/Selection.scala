package com.badlogic.gdx.scenes.scene2d.utils

class Selection[T <: java.lang.Object] extends com.badlogic.gdx.scenes.scene2d.utils.Disableable with balticporter.runtime.JavaIterable[T] {
  private var actor: com.badlogic.gdx.scenes.scene2d.Actor = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]
  final val selected: com.badlogic.gdx.utils.OrderedSet[T] = new com.badlogic.gdx.utils.OrderedSet[T]().asInstanceOf[com.badlogic.gdx.utils.OrderedSet[T]]
  private final val old: com.badlogic.gdx.utils.OrderedSet[T] = new com.badlogic.gdx.utils.OrderedSet[T]().asInstanceOf[com.badlogic.gdx.utils.OrderedSet[T]]
  var isDisabled$field: scala.Boolean = false
  private var toggle: scala.Boolean = false
  var multiple: scala.Boolean = false
  var required: scala.Boolean = false
  private var programmaticChangeEvents: scala.Boolean = true
  var lastSelected: T = null.asInstanceOf[T]
  def setActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Unit = {
    this.actor = actor
  }
  def choose(item: T): scala.Unit = {
    if (item == null) {
      throw new java.lang.IllegalArgumentException("item cannot be null.")
    } else ()
    if (this.isDisabled$field) {
      return
    } else ()
    this.snapshot()
    try {
      if ((this.toggle || com.badlogic.gdx.scenes.scene2d.utils.UIUtils.ctrl()) && this.selected.contains(item)) {
        if (this.required && (this.selected.size == 1)) {
          return
        } else ()
        this.selected.remove(item)
        this.lastSelected = null.asInstanceOf[T]
      } else {
        var modified: scala.Boolean = false
        if ((!this.multiple) || ((!this.toggle) && (!com.badlogic.gdx.scenes.scene2d.utils.UIUtils.ctrl()))) {
          if ((this.selected.size == 1) && this.selected.contains(item)) {
            return
          } else ()
          modified = this.selected.size > 0
          this.selected.clear(8)
        } else ()
        if ((!this.selected.add(item)) && (!modified)) {
          return
        } else ()
        this.lastSelected = item
      }
      if (this.fireChangeEvent()) {
        this.revert()
      } else {
        this.changed()
      }
    } finally {
      this.cleanup()
    }
  }
  @java.lang.Deprecated
  def hasItems(): scala.Boolean = {
    return this.selected.size > 0
  }
  def notEmpty(): scala.Boolean = {
    return this.selected.size > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.selected.size == 0
  }
  def size(): scala.Int = {
    return this.selected.size
  }
  def items(): com.badlogic.gdx.utils.OrderedSet[T] = {
    return this.selected
  }
  @com.badlogic.gdx.utils.Null
  def first(): T = {
    return if (this.selected.size == 0) null.asInstanceOf[T] else this.selected.first()
  }
  def snapshot(): scala.Unit = {
    this.old.clear(this.selected.size)
    this.old.addAll(this.selected)
  }
  def revert(): scala.Unit = {
    this.selected.clear(this.old.size)
    this.selected.addAll(this.old)
  }
  def cleanup(): scala.Unit = {
    this.old.clear(32)
  }
  def set(item: T): scala.Unit = {
    if (item == null) {
      throw new java.lang.IllegalArgumentException("item cannot be null.")
    } else ()
    if ((this.selected.size == 1) && (this.selected.first() == item)) {
      return
    } else ()
    this.snapshot()
    this.selected.clear(8)
    this.selected.add(item)
    if (this.programmaticChangeEvents && this.fireChangeEvent()) {
      this.revert()
    } else {
      this.lastSelected = item
      this.changed()
    }
    this.cleanup()
  }
  def setAll(items: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    var added: scala.Boolean = false
    this.snapshot()
    this.lastSelected = null.asInstanceOf[T]
    this.selected.clear(items.size);
    { var i: scala.Int = 0; val n: scala.Int = items.size; while (i < n) { {
      val item: T = items.get(i).asInstanceOf[T]
      if (item == null) {
        throw new java.lang.IllegalArgumentException("item cannot be null.")
      } else ()
      if (this.selected.add(item)) {
        added = true
      } else ()
    }; i = i + 1 } }
    if (added) {
      if (this.programmaticChangeEvents && this.fireChangeEvent()) {
        this.revert()
      } else {
        if (items.size > 0) {
          this.lastSelected = items.peek().asInstanceOf[T]
          this.changed()
        } else ()
      }
    } else ()
    this.cleanup()
  }
  def add(item: T): scala.Unit = {
    if (item == null) {
      throw new java.lang.IllegalArgumentException("item cannot be null.")
    } else ()
    if (!this.selected.add(item)) {
      return
    } else ()
    if (this.programmaticChangeEvents && this.fireChangeEvent()) {
      this.selected.remove(item)
    } else {
      this.lastSelected = item
      this.changed()
    }
  }
  def addAll(items: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    var added: scala.Boolean = false
    this.snapshot();
    { var i: scala.Int = 0; val n: scala.Int = items.size; while (i < n) { {
      val item: T = items.get(i).asInstanceOf[T]
      if (item == null) {
        throw new java.lang.IllegalArgumentException("item cannot be null.")
      } else ()
      if (this.selected.add(item)) {
        added = true
      } else ()
    }; i = i + 1 } }
    if (added) {
      if (this.programmaticChangeEvents && this.fireChangeEvent()) {
        this.revert()
      } else {
        this.lastSelected = items.peek().asInstanceOf[T]
        this.changed()
      }
    } else ()
    this.cleanup()
  }
  def remove(item: T): scala.Unit = {
    if (item == null) {
      throw new java.lang.IllegalArgumentException("item cannot be null.")
    } else ()
    if (!this.selected.remove(item)) {
      return
    } else ()
    if (this.programmaticChangeEvents && this.fireChangeEvent()) {
      this.selected.add(item)
    } else {
      this.lastSelected = null.asInstanceOf[T]
      this.changed()
    }
  }
  def removeAll(items: com.badlogic.gdx.utils.Array[T]): scala.Unit = {
    var removed: scala.Boolean = false
    this.snapshot();
    { var i: scala.Int = 0; val n: scala.Int = items.size; while (i < n) { {
      val item: T = items.get(i).asInstanceOf[T]
      if (item == null) {
        throw new java.lang.IllegalArgumentException("item cannot be null.")
      } else ()
      if (this.selected.remove(item)) {
        removed = true
      } else ()
    }; i = i + 1 } }
    if (removed) {
      if (this.programmaticChangeEvents && this.fireChangeEvent()) {
        this.revert()
      } else {
        this.lastSelected = null.asInstanceOf[T]
        this.changed()
      }
    } else ()
    this.cleanup()
  }
  def clear(): scala.Unit = {
    if (this.selected.size == 0) {
      this.lastSelected = null.asInstanceOf[T]
      return
    } else ()
    this.snapshot()
    this.selected.clear(8)
    if (this.programmaticChangeEvents && this.fireChangeEvent()) {
      this.revert()
    } else {
      this.lastSelected = null.asInstanceOf[T]
      this.changed()
    }
    this.cleanup()
  }
  def changed(): scala.Unit = {
    ()
  }
  def fireChangeEvent(): scala.Boolean = {
    if (this.actor == null) {
      return false
    } else ()
    val changeEvent: com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent])
    try {
      return this.actor.fire(changeEvent)
    } finally {
      com.badlogic.gdx.scenes.scene2d.Actor.POOLS.free(changeEvent)
    }
  }
  def contains(item: T): scala.Boolean = {
    if (item == null) {
      return false
    } else ()
    return this.selected.contains(item)
  }
  @com.badlogic.gdx.utils.Null
  def getLastSelected(): T = {
    if (this.lastSelected != null) {
      return this.lastSelected
    } else {
      if (this.selected.size > 0) {
        return this.selected.first().asInstanceOf[T]
      } else ()
    }
    return null.asInstanceOf[T]
  }
  def iterator(): balticporter.runtime.JavaIterator[T] = {
    return this.selected.iterator()
  }
  def toArray(): com.badlogic.gdx.utils.Array[T] = {
    return this.selected.iterator().toArray()
  }
  def toArray(array: com.badlogic.gdx.utils.Array[T]): com.badlogic.gdx.utils.Array[T] = {
    return this.selected.iterator().toArray(array)
  }
  def setDisabled(isDisabled: scala.Boolean): scala.Unit = {
    this.isDisabled$field = isDisabled
  }
  def isDisabled(): scala.Boolean = {
    return this.isDisabled$field
  }
  def getToggle(): scala.Boolean = {
    return this.toggle
  }
  def setToggle(toggle: scala.Boolean): scala.Unit = {
    this.toggle = toggle
  }
  def getMultiple(): scala.Boolean = {
    return this.multiple
  }
  def setMultiple(multiple: scala.Boolean): scala.Unit = {
    this.multiple = multiple
  }
  def getRequired(): scala.Boolean = {
    return this.required
  }
  def setRequired(required: scala.Boolean): scala.Unit = {
    this.required = required
  }
  def setProgrammaticChangeEvents(programmaticChangeEvents: scala.Boolean): scala.Unit = {
    this.programmaticChangeEvents = programmaticChangeEvents
  }
  def getProgrammaticChangeEvents(): scala.Boolean = {
    return this.programmaticChangeEvents
  }
  def toString(): java.lang.String = {
    return this.selected.toString()
  }
}