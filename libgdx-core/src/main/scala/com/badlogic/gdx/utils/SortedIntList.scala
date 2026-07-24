package com.badlogic.gdx.utils

class SortedIntList[E] extends scala.collection.Iterable[com.badlogic.gdx.utils.SortedIntList.Node[E]] {
  private var nodePool: com.badlogic.gdx.utils.SortedIntList.NodePool[E] = new com.badlogic.gdx.utils.SortedIntList.NodePool[E]()
  var iterator$field: Iterator = null.asInstanceOf[Iterator]
  var size$field: scala.Int = 0
  var first: com.badlogic.gdx.utils.SortedIntList.Node[E] = null.asInstanceOf[com.badlogic.gdx.utils.SortedIntList.Node[E]]
  def insert(index: scala.Int, value: E): E = {
    if (this.first != null) {
      var c: com.badlogic.gdx.utils.SortedIntList.Node[E] = this.first
      while ((c.n != null) && (c.n.index <= index)) {
        c = c.n
      }
      if (index > c.index) {
        c.n = this.nodePool.obtain(c, c.n, value, index)
        if (c.n.n != null) {
          c.n.n.p = c.n
        } else ()
        this.size$field = this.size$field + 1
      } else {
        if (index < c.index) {
          val newFirst: com.badlogic.gdx.utils.SortedIntList.Node[E] = this.nodePool.obtain(null, this.first, value, index)
          this.first.p = newFirst
          this.first = newFirst
          this.size$field = this.size$field + 1
        } else {
          c.value = value
        }
      }
    } else {
      this.first = this.nodePool.obtain(null, null, value, index)
      this.size$field = this.size$field + 1
    }
    return null.asInstanceOf[E]
  }
  def get(index: scala.Int): E = {
    var `match`: E = null.asInstanceOf[E]
    if (this.first != null) {
      var c: com.badlogic.gdx.utils.SortedIntList.Node[E] = this.first
      while ((c.n != null) && (c.index < index)) {
        c = c.n
      }
      if (c.index == index) {
        `match` = c.value
      } else ()
    } else ()
    return `match`
  }
  def clear(): scala.Unit = {
    { ; while (this.first != null) { {
      this.nodePool.free(this.first)
    }; this.first = this.first.n } }
    this.size$field = 0
  }
  def size(): scala.Int = {
    return this.size$field
  }
  def notEmpty(): scala.Boolean = {
    return this.size$field > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size$field == 0
  }
  def iterator(): scala.collection.Iterator[com.badlogic.gdx.utils.SortedIntList.Node[E]] = {
    if (com.badlogic.gdx.utils.Collections.allocateIterators) {
      return new Iterator()
    } else ()
    if (this.iterator$field == null) {
      return {
        this.iterator$field = new Iterator()
        this.iterator$field
      }
    } else ()
    return this.iterator$field.reset()
  }
  class Iterator extends scala.collection.Iterator[com.badlogic.gdx.utils.SortedIntList.Node[E]] {
    private var position: com.badlogic.gdx.utils.SortedIntList.Node[E] = null.asInstanceOf[com.badlogic.gdx.utils.SortedIntList.Node[E]]
    private var previousPosition: com.badlogic.gdx.utils.SortedIntList.Node[E] = null.asInstanceOf[com.badlogic.gdx.utils.SortedIntList.Node[E]]
    this.reset()
    def hasNext(): scala.Boolean = {
      return this.position != null
    }
    def next(): com.badlogic.gdx.utils.SortedIntList.Node[E] = {
      this.previousPosition = this.position
      this.position = this.position.n
      return this.previousPosition
    }
    def remove(): scala.Unit = {
      if (this.previousPosition != null) {
        if (this.previousPosition == first) {
          first = this.position
        } else {
          this.previousPosition.p.n = this.position
          if (this.position != null) {
            this.position.p = this.previousPosition.p
          } else ()
        }
        size$field = size$field - 1
      } else ()
    }
    def reset(): Iterator = {
      this.position = first
      this.previousPosition = null
      return this
    }
  }
}
object SortedIntList {
  class Node[E] {
    var p: com.badlogic.gdx.utils.SortedIntList.Node[E] = null.asInstanceOf[com.badlogic.gdx.utils.SortedIntList.Node[E]]
    var n: com.badlogic.gdx.utils.SortedIntList.Node[E] = null.asInstanceOf[com.badlogic.gdx.utils.SortedIntList.Node[E]]
    var value: E = null.asInstanceOf[E]
    var index: scala.Int = 0
  }
  class NodePool[E] extends com.badlogic.gdx.utils.Pool[com.badlogic.gdx.utils.SortedIntList.Node[E]] {
    def newObject(): com.badlogic.gdx.utils.SortedIntList.Node[E] = {
      return new com.badlogic.gdx.utils.SortedIntList.Node[E]()
    }
    def obtain(p: com.badlogic.gdx.utils.SortedIntList.Node[E], n: com.badlogic.gdx.utils.SortedIntList.Node[E], value: E, index: scala.Int): com.badlogic.gdx.utils.SortedIntList.Node[E] = {
      val newNode: com.badlogic.gdx.utils.SortedIntList.Node[E] = super.obtain()
      newNode.p = p
      newNode.n = n
      newNode.value = value
      newNode.index = index
      return newNode
    }
  }
}