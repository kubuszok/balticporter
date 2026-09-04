package balticporter.runtime

/** `java.lang.Iterable`, as Scala — java's interface, not scala's collection. */
trait JavaIterable[A] {
  def iterator(): JavaIterator[A]
}

/** A SHIM THAT DELEGATES, saying what it delegates TO. */
trait Wrapping {
  /** the value this shim reads and writes THROUGH — never a copy of it. */
  def wrapped: Any
}

object JavaIterable {
  /** Adapt a plain scala collection to the java-shaped one. Inserted by the engine at call
    * sites where a shim-typed parameter meets a collection the port ITSELF mapped to scala
    * (`CharArray.appendAll(list)`). `remove()` stays at [[JavaIterator]]'s default —
    * `UnsupportedOperationException` — because a scala iterator genuinely cannot remove,
    * which is what java reports for a non-removable iterator too. */
  def from[A](xs: scala.collection.Iterable[A]): JavaIterable[A] = new JavaIterable[A] with Wrapping {
    def wrapped: Any = xs
    def iterator(): JavaIterator[A] = JavaIterator.from(xs.iterator)
  }

  /** Adapt an iterator-producing function into a JavaIterable view. Each call to `iterator()`
    * invokes the function, so the result is re-traversable. */
  def fromIterator[A](mkIterator: () => scala.collection.Iterator[A]): JavaIterable[A] = {
    new JavaIterable[A] with Wrapping {
      def wrapped: Any = mkIterator
      def iterator(): JavaIterator[A] = JavaIterator.from(mkIterator())
    }
  }

  extension [A](self: JavaIterable[A]) {
    /** A scala view of this java iterable — `for`, `map`, `foreach` and the rest. */
    def asScala: scala.collection.Iterable[A] = new scala.collection.Iterable[A] {
      def iterator: scala.collection.Iterator[A] = self.iterator().asScala
    }
    /** so `for (x <- xs)` and `xs.foreach(f)` work directly on the java shape. Written as
      * the loop rather than delegating to the iterator: `JavaIterator` deliberately has no
      * `foreach` extension (see there), so there is nothing to delegate to. */
    def foreach(f: A => Unit): Unit = {
      val it = self.iterator()
      while it.hasNext() do f(it.next())
    }
  }
}
