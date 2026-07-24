package com.badlogic.gdx.utils

class BinaryHeap[T <: com.badlogic.gdx.utils.BinaryHeap.Node] {
  var size: scala.Int = 0
  private var nodes: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = null.asInstanceOf[scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node]]
  private var isMaxHeap: scala.Boolean = false
  def this(capacity: scala.Int, isMaxHeap: scala.Boolean) = {
    this()
    this.isMaxHeap = isMaxHeap
    this.nodes = new scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node](capacity)
  }
  def add(node: T): T = {
    if (this.size == this.nodes.length) {
      val newNodes: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = new scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node](this.size << 1)
      java.lang.System.arraycopy(this.nodes, 0, newNodes, 0, this.size)
      this.nodes = newNodes
    } else ()
    node.index = this.size
    this.nodes(this.size) = node
    this.up({ this.size += 1; this.size })
    return node
  }
  def add(node: T, value: scala.Float): T = {
    node.value = value
    return this.add(node)
  }
  def contains(node: T, identity: scala.Boolean): scala.Boolean = {
    if (node == null) {
      throw new java.lang.IllegalArgumentException("node cannot be null.")
    } else ()
    if (identity) {
      for (n <- this.nodes) {
        if (n == node) {
          return true
        } else ()
      }
    } else {
      for (other <- this.nodes) {
        if (other.equals(node)) {
          return true
        } else ()
      }
    }
    return false
  }
  def peek(): T = {
    if (this.size == 0) {
      throw new java.lang.IllegalStateException("The heap is empty.")
    } else ()
    return this.nodes(0).asInstanceOf[T]
  }
  def pop(): T = {
    val removed: com.badlogic.gdx.utils.BinaryHeap.Node = this.nodes(0)
    if ({ this.size -= 1; this.size } > 0) {
      this.nodes(0) = this.nodes(this.size)
      this.nodes(this.size) = null
      this.down(0)
    } else {
      this.nodes(0) = null
    }
    return removed.asInstanceOf[T]
  }
  def remove(node: T): T = {
    if ({ this.size -= 1; this.size } > 0) {
      val moved: com.badlogic.gdx.utils.BinaryHeap.Node = this.nodes(this.size)
      this.nodes(this.size) = null
      this.nodes(node.index) = moved
      if ((moved.value < node.value) ^ this.isMaxHeap) {
        this.up(node.index)
      } else {
        this.down(node.index)
      }
    } else {
      this.nodes(0) = null
    }
    return node
  }
  def notEmpty(): scala.Boolean = {
    return this.size > 0
  }
  def isEmpty(): scala.Boolean = {
    return this.size == 0
  }
  def clear(): scala.Unit = {
    java.util.Arrays.fill(this.nodes.asInstanceOf[scala.Array[java.lang.Object]], 0, this.size, null)
    this.size = 0
  }
  def setValue(node: T, value: scala.Float): scala.Unit = {
    val oldValue: scala.Float = node.value
    node.value = value
    if ((value < oldValue) ^ this.isMaxHeap) {
      this.up(node.index)
    } else {
      this.down(node.index)
    }
  }
  private def up(index$arg: scala.Int): scala.Unit = {
    var index: scala.Int = index$arg
    val nodes: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = this.nodes
    val node: com.badlogic.gdx.utils.BinaryHeap.Node = nodes(index)
    val value: scala.Float = node.value
    while (index > 0) {
      val parentIndex: scala.Int = (index - 1) >> 1
      val parent: com.badlogic.gdx.utils.BinaryHeap.Node = nodes(parentIndex)
      if ((value < parent.value) ^ this.isMaxHeap) {
        nodes(index) = parent
        parent.index = index
        index = parentIndex
      } else {
        /* break */ ()
      }
    }
    nodes(index) = node
    node.index = index
  }
  private def down(index$arg: scala.Int): scala.Unit = {
    var index: scala.Int = index$arg
    val nodes: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = this.nodes
    val size: scala.Int = this.size
    val node: com.badlogic.gdx.utils.BinaryHeap.Node = nodes(index)
    val value: scala.Float = node.value
    while (true) {
      val leftIndex: scala.Int = 1 + (index << 1)
      if (leftIndex >= size) {
        /* break */ ()
      } else ()
      val rightIndex: scala.Int = leftIndex + 1
      val leftNode: com.badlogic.gdx.utils.BinaryHeap.Node = nodes(leftIndex)
      val leftValue: scala.Float = leftNode.value
      var rightNode: com.badlogic.gdx.utils.BinaryHeap.Node = null.asInstanceOf[com.badlogic.gdx.utils.BinaryHeap.Node]
      var rightValue: scala.Float = 0.0f
      if (rightIndex >= size) {
        rightNode = null
        rightValue = if (this.isMaxHeap) -java.lang.Float.MAX_VALUE else java.lang.Float.MAX_VALUE
      } else {
        rightNode = nodes(rightIndex)
        rightValue = rightNode.value
      }
      if ((leftValue < rightValue) ^ this.isMaxHeap) {
        if ((leftValue == value) || ((leftValue > value) ^ this.isMaxHeap)) {
          /* break */ ()
        } else ()
        nodes(index) = leftNode
        leftNode.index = index
        index = leftIndex
      } else {
        if ((rightValue == value) || ((rightValue > value) ^ this.isMaxHeap)) {
          /* break */ ()
        } else ()
        nodes(index) = rightNode
        if (rightNode != null) {
          rightNode.index = index
        } else ()
        index = rightIndex
      }
    }
    nodes(index) = node
    node.index = index
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (!obj.isInstanceOf[BinaryHeap[?]]) {
      return false
    } else ()
    val other: BinaryHeap[?] = obj.asInstanceOf[BinaryHeap[?]].asInstanceOf[BinaryHeap[?]]
    if (other.size != this.size) {
      return false
    } else ()
    val nodes1: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = this.nodes
    val nodes2: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = other.nodes;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      if (nodes1(i).value != nodes2(i).value) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def hashCode(): scala.Int = {
    var h: scala.Int = 1
    val nodes: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = this.nodes;
    { var i: scala.Int = 0; val n: scala.Int = this.size; while (i < n) { {
      h = (h * 31) + java.lang.Float.floatToIntBits(nodes(i).value)
    }; i = i + 1 } }
    return h
  }
  def toString(): java.lang.String = {
    if (this.size == 0) {
      return "[]"
    } else ()
    val nodes: scala.Array[com.badlogic.gdx.utils.BinaryHeap.Node] = this.nodes
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(32)
    buffer.append('[')
    buffer.append(nodes(0).value);
    { var i: scala.Int = 1; while (i < this.size) { {
      buffer.append(", ")
      buffer.append(nodes(i).value)
    }; i = i + 1 } }
    buffer.append(']')
    return buffer.toString()
  }
}
object BinaryHeap {
  class Node {
    var value: scala.Float = 0.0f
    var index: scala.Int = 0
    def this(value: scala.Float) = {
      this()
      this.value = value
    }
    def getValue(): scala.Float = {
      return this.value
    }
    def toString(): java.lang.String = {
      return java.lang.Float.toString(this.value)
    }
  }
}