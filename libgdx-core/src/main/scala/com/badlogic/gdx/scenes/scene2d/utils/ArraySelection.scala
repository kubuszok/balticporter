package com.badlogic.gdx.scenes.scene2d.utils

class ArraySelection[T](array$p: com.badlogic.gdx.utils.Array[T]) extends com.badlogic.gdx.scenes.scene2d.utils.Selection[T] {
  private var array: com.badlogic.gdx.utils.Array[T] = null.asInstanceOf[com.badlogic.gdx.utils.Array[T]]
  private var rangeSelect: scala.Boolean = true
  private var rangeStart: T = null.asInstanceOf[T]
  this.array = array$p
  def choose(item: T): scala.Unit = {
    if (item == null) {
      throw new java.lang.IllegalArgumentException("item cannot be null.")
    } else ()
    if (isDisabled$field) {
      return
    } else ()
    if ((!this.rangeSelect) || (!multiple)) {
      super.choose(item)
      return
    } else ()
    if ((this.selected.size > 0) && com.badlogic.gdx.scenes.scene2d.utils.UIUtils.shift()) {
      val rangeStartIndex: scala.Int = if (this.rangeStart == null) -1 else this.array.indexOf(this.rangeStart, false)
      if (rangeStartIndex != (-1)) {
        val oldRangeStart: T = this.rangeStart
        this.snapshot()
        var start: scala.Int = rangeStartIndex
        var `end`: scala.Int = this.array.indexOf(item, false)
        if (start > `end`) {
          val temp: scala.Int = `end`
          `end` = start
          start = temp
        } else ()
        if (!com.badlogic.gdx.scenes.scene2d.utils.UIUtils.ctrl()) {
          selected.clear(8)
        } else ();
        { var i: scala.Int = start; while (i <= `end`) { {
          selected.add(this.array.get(i))
        }; i = i + 1 } }
        if (this.fireChangeEvent()) {
          this.revert()
        } else {
          this.changed()
        }
        this.rangeStart = oldRangeStart
        this.cleanup()
        return
      } else ()
    } else ()
    super.choose(item)
    this.rangeStart = item
  }
  def changed(): scala.Unit = {
    this.rangeStart = null.asInstanceOf[T]
  }
  def getRangeSelect(): scala.Boolean = {
    return this.rangeSelect
  }
  def setRangeSelect(rangeSelect: scala.Boolean): scala.Unit = {
    this.rangeSelect = rangeSelect
  }
  def validate(): scala.Unit = {
    val array: com.badlogic.gdx.utils.Array[T] = this.array
    if (array.size == 0) {
      this.clear()
      return
    } else ()
    var changed: scala.Boolean = false;
    { val iter: scala.collection.Iterator[T] = this.items().iterator(); while (iter.hasNext) { {
      val selected: T = iter.next.asInstanceOf[T]
      if (!array.contains(selected, false)) {
        iter.remove()
        changed = true
      } else ()
    };  } }
    if (required && (this.selected.size == 0)) {
      this.set(array.first())
    } else {
      if (changed) {
        this.changed()
      } else ()
    }
  }
}