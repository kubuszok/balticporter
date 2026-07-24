package com.badlogic.gdx.utils

class AtomicQueue[T] {
  private final val writeIndex: java.util.concurrent.atomic.AtomicInteger = new java.util.concurrent.atomic.AtomicInteger()
  private final val readIndex: java.util.concurrent.atomic.AtomicInteger = new java.util.concurrent.atomic.AtomicInteger()
  private var queue: java.util.concurrent.atomic.AtomicReferenceArray[T] = null.asInstanceOf[java.util.concurrent.atomic.AtomicReferenceArray[T]]
  def this(capacity: scala.Int) = {
    this()
    this.queue = new java.util.concurrent.atomic.AtomicReferenceArray(capacity)
  }
  private def next(idx: scala.Int): scala.Int = {
    return (idx + 1) % this.queue.length()
  }
  def put(value: T): scala.Boolean = {
    val write: scala.Int = this.writeIndex.get()
    val read: scala.Int = this.readIndex.get()
    val next: scala.Int = this.next(write)
    if (next == read) {
      return false
    } else ()
    this.queue.set(write, value)
    this.writeIndex.set(next)
    return true
  }
  def poll(): T = {
    val read: scala.Int = this.readIndex.get()
    val write: scala.Int = this.writeIndex.get()
    if (read == write) {
      return null.asInstanceOf[T]
    } else ()
    val value: T = this.queue.get(read).asInstanceOf[T]
    this.readIndex.set(this.next(read))
    return value
  }
}