package com.badlogic.gdx.utils

class PooledLinkedList[T] {
  private var head: Item[T] = null.asInstanceOf[Item[T]]
  private var tail: Item[T] = null.asInstanceOf[Item[T]]
  var iter$field: Item[T] = null.asInstanceOf[Item[T]]
  private var curr: Item[T] = null.asInstanceOf[Item[T]]
  var size$field: scala.Int = 0
  private var pool: com.badlogic.gdx.utils.Pool[Item[T]] = null.asInstanceOf[com.badlogic.gdx.utils.Pool[Item[T]]]
  def this(maxPoolSize: scala.Int) = {
    this()
    this.pool = new com.badlogic.gdx.utils.Pool[Item[T]](16, maxPoolSize)
  }
  def add(`object`: T): scala.Unit = {
    val item: Item[T] = this.pool.obtain()
    item.payload = `object`
    item.next = null
    item.prev = null
    if (this.head == null) {
      this.head = item
      this.tail = item
      this.size$field = this.size$field + 1
      return
    } else ()
    item.prev = this.tail
    this.tail.next = item
    this.tail = item
    this.size$field = this.size$field + 1
  }
  def addFirst(`object`: T): scala.Unit = {
    val item: Item[T] = this.pool.obtain()
    item.payload = `object`
    item.next = this.head
    item.prev = null
    if (this.head != null) {
      this.head.prev = item
    } else {
      this.tail = item
    }
    this.head = item
    this.size$field = this.size$field + 1
  }
  def size(): scala.Int = {
    return this.size$field
  }
  def iter(): scala.Unit = {
    this.iter$field = this.head
  }
  def iterReverse(): scala.Unit = {
    this.iter$field = this.tail
  }
  def next(): T = {
    if (this.iter$field == null) {
      return null
    } else ()
    val payload: T = this.iter$field.payload
    this.curr = this.iter$field
    this.iter$field = this.iter$field.next
    return payload
  }
  def previous(): T = {
    if (this.iter$field == null) {
      return null
    } else ()
    val payload: T = this.iter$field.payload
    this.curr = this.iter$field
    this.iter$field = this.iter$field.prev
    return payload
  }
  def remove(): scala.Unit = {
    if (this.curr == null) {
      return
    } else ()
    this.size$field = this.size$field - 1
    val c: Item[T] = this.curr
    val n: Item[T] = this.curr.next
    val p: Item[T] = this.curr.prev
    this.pool.free(this.curr)
    this.curr = null
    if (this.size$field == 0) {
      this.head = null
      this.tail = null
      return
    } else ()
    if (c == this.head) {
      n.prev = null
      this.head = n
      return
    } else ()
    if (c == this.tail) {
      p.next = null
      this.tail = p
      return
    } else ()
    p.next = n
    n.prev = p
  }
  def removeLast(): T = {
    if (this.tail == null) {
      return null
    } else ()
    val payload: T = this.tail.payload
    this.size$field = this.size$field - 1
    val p: Item[T] = this.tail.prev
    this.pool.free(this.tail)
    if (this.size$field == 0) {
      this.head = null
      this.tail = null
    } else {
      this.tail = p
      this.tail.next = null
    }
    return payload
  }
  def clear(): scala.Unit = {
    this.iter()
    var v: T = null
    while ({
      v = this.next()
      v
    } != null) {
      this.remove()
    }
  }
  final class Item[T] {
    var payload: T = null.asInstanceOf[T]
    var next: Item[T] = null.asInstanceOf[Item[T]]
    var prev: Item[T] = null.asInstanceOf[Item[T]]
  }
}