package com.badlogic.gdx.utils

trait Predicate[T] {
  def evaluate(arg0: T): scala.Boolean
}
object Predicate {
  class PredicateIterator[T] extends scala.collection.Iterator[T] {
    var iterator: scala.collection.Iterator[T] = null.asInstanceOf[scala.collection.Iterator[T]]
    var predicate: Predicate[T] = null.asInstanceOf[Predicate[T]]
    var `end`: scala.Boolean = false
    var peeked: scala.Boolean = false
    var next$field: T = null.asInstanceOf[T]
    def this(iterator: scala.collection.Iterator[T], predicate: Predicate[T]) = {
      this()
      this.set(iterator, predicate)
    }
    def this(iterable: scala.collection.Iterable[T], predicate: Predicate[T]) = {
      this(iterable.iterator, predicate)
    }
    def set(iterable: scala.collection.Iterable[T], predicate: Predicate[T]): scala.Unit = {
      this.set(iterable.iterator, predicate)
    }
    def set(iterator: scala.collection.Iterator[T], predicate: Predicate[T]): scala.Unit = {
      this.iterator = iterator
      this.predicate = predicate
      this.`end` = {
        this.peeked = false
        this.peeked
      }
      this.next$field = null.asInstanceOf[T]
    }
    def hasNext(): scala.Boolean = {
      if (this.`end`) {
        return false
      } else ()
      if (this.next$field != null) {
        return true
      } else ()
      this.peeked = true
      while (this.iterator.hasNext) {
        val n: T = this.iterator.next
        if (this.predicate.evaluate(n)) {
          this.next$field = n
          return true
        } else ()
      }
      this.`end` = true
      return false
    }
    def next(): T = {
      if ((this.next$field == null) && (!this.hasNext())) {
        return null.asInstanceOf[T]
      } else ()
      val result: T = this.next$field
      this.next$field = null.asInstanceOf[T]
      this.peeked = false
      return result
    }
    def remove(): scala.Unit = {
      if (this.peeked) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot remove between a call to hasNext() and next().")
      } else ()
      this.iterator.remove()
    }
  }
  class PredicateIterable[T] extends scala.collection.Iterable[T] {
    var iterable: scala.collection.Iterable[T] = null.asInstanceOf[scala.collection.Iterable[T]]
    var predicate: Predicate[T] = null.asInstanceOf[Predicate[T]]
    var iterator$field: com.badlogic.gdx.utils.Predicate.PredicateIterator[T] = null
    def this(iterable: scala.collection.Iterable[T], predicate: Predicate[T]) = {
      this()
      this.set(iterable, predicate)
    }
    def set(iterable: scala.collection.Iterable[T], predicate: Predicate[T]): scala.Unit = {
      this.iterable = iterable
      this.predicate = predicate
    }
    def iterator(): scala.collection.Iterator[T] = {
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