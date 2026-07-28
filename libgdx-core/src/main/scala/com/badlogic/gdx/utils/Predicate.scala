package com.badlogic.gdx.utils

trait Predicate[T <: java.lang.Object] {
  def evaluate(arg0: T): scala.Boolean
}
object Predicate {
  class PredicateIterator[T <: java.lang.Object](iterator$p: balticporter.runtime.JavaIterator[T], predicate$p: Predicate[T]) extends balticporter.runtime.JavaIterator[T] {
    var iterator: balticporter.runtime.JavaIterator[T] = null.asInstanceOf[balticporter.runtime.JavaIterator[T]]
    var predicate: Predicate[T] = null.asInstanceOf[Predicate[T]]
    var `end`: scala.Boolean = false
    var peeked: scala.Boolean = false
    var next$field: T = null.asInstanceOf[T]
    def this(iterable: balticporter.runtime.JavaIterable[T], predicate: Predicate[T]) = {
      this(iterable.iterator, predicate)
    }
    this.set(iterator$p, predicate$p)
    def set(iterable: balticporter.runtime.JavaIterable[T], predicate: Predicate[T]): scala.Unit = {
      this.set(iterable.iterator, predicate)
    }
    def set(iterator: balticporter.runtime.JavaIterator[T], predicate: Predicate[T]): scala.Unit = {
      this.iterator = iterator
      this.predicate = predicate
      this.`end` = {
        this.peeked = false
        this.peeked
      }
      this.next$field = null.asInstanceOf[T]
    }
    @java.lang.Override
    override def hasNext(): scala.Boolean = {
      if (this.`end`) {
        return false
      } else ()
      if (this.next$field != null) {
        return true
      } else ()
      this.peeked = true
      while (this.iterator.hasNext) {
        val n: T = this.iterator.next.asInstanceOf[T]
        if (this.predicate.evaluate(n)) {
          this.next$field = n
          return true
        } else ()
      }
      this.`end` = true
      return false
    }
    @java.lang.Override
    override def next(): ?E = {
      if ((this.next$field == null) && (!this.hasNext())) {
        return null.asInstanceOf[T]
      } else ()
      val result: T = this.next$field
      this.next$field = null.asInstanceOf[T]
      this.peeked = false
      return result
    }
    @java.lang.Override
    override def remove(): scala.Unit = {
      if (this.peeked) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot remove between a call to hasNext() and next().")
      } else ()
      this.iterator.remove()
    }
  }
  class PredicateIterable[T <: java.lang.Object](iterable$p: balticporter.runtime.JavaIterable[T], predicate$p: Predicate[T]) extends balticporter.runtime.JavaIterable[T] {
    var iterable: balticporter.runtime.JavaIterable[T] = null.asInstanceOf[balticporter.runtime.JavaIterable[T]]
    var predicate: Predicate[T] = null.asInstanceOf[Predicate[T]]
    var iterator$field: com.badlogic.gdx.utils.Predicate.PredicateIterator[T] = null
    this.set(iterable$p, predicate$p)
    def set(iterable: balticporter.runtime.JavaIterable[T], predicate: Predicate[T]): scala.Unit = {
      this.iterable = iterable
      this.predicate = predicate
    }
    @java.lang.Override
    override def iterator(): balticporter.runtime.JavaIterator[T] = {
      if (com.badlogic.gdx.utils.Collections.allocateIterators) {
        return new com.badlogic.gdx.utils.Predicate.PredicateIterator[T](this.iterable.iterator, this.predicate)
      } else ()
      if (this.iterator$field == null) {
        this.iterator$field = new com.badlogic.gdx.utils.Predicate.PredicateIterator[T](this.iterable.iterator, this.predicate)
      } else {
        this.iterator$field.set(this.iterable.iterator, this.predicate)
      }
      return this.iterator$field
    }
  }
}