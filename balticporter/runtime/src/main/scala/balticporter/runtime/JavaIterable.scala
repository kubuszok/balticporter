package balticporter.runtime

/** `java.lang.Iterable`, as Scala — java's interface, not scala's collection.
  *
  * STANDALONE by necessity, not by preference. Java's `Iterable` and `Iterator` are
  * independent two- and three-method interfaces, and a class may implement BOTH — libGDX's
  * map and array iterators all do (`Entries extends MapIterator implements Iterable<Entry>,
  * Iterator<Entry>`, 14 such classes in gdx core alone). Modelled on
  * `scala.collection.{Iterable, Iterator}` that shape is not merely awkward, it is
  * ILLEGAL: `Iterator.iterator` is `final`, and `seq` arrives from both sides. No amount
  * of `override` recovers it, because the conflict is in the parents.
  *
  * Nor is java's `iterator()` scala's `iterator`: java's is nilary and returns a
  * REMOVAL-CAPABLE iterator, scala's is parameterless and returns one that cannot remove.
  * Declaring the java shape here is what lets a ported `override def iterator()` mean what
  * it meant in Java.
  *
  * Interop is restored by [[JavaIterable.asScala]] rather than by inheritance, which is
  * the direction that cannot conflict: an extension adds a view, a parent adds members.
  */
trait JavaIterable[A] {
  def iterator(): JavaIterator[A]
}

/** A SHIM THAT DELEGATES, saying what it delegates TO — `ENGINE-LIMITS.md` K19.
  *
  * A reified coercion at a shim target has to BUILD something: `mutable.Buffer` is not a
  * `JavaCollection` and no view can make it one, so the value that leaves
  * `JavaCollections.Reified.asCollection` is a different OBJECT from the one that arrived. Java's
  * cast was the IDENTITY, so every later reified question about that value was still a question
  * about the original class — `(Collection) list` then `instanceof List` is TRUE in java, and
  * false when asked of an opaque wrapper. That is `CLAUDE.md` §4.4's shape reached through a
  * retyping: valid Scala, right static types, wrong answer, no count.
  *
  * So the delegating factories carry this and `Reified` looks through it. Two things it is not:
  *
  *   - '''not a general "unwrap me" protocol.''' It says only what a later REIFIED question must be
  *     asked of. Nothing else in the engine or in emitted code reads it;
  *   - '''never on an UNMODIFIABLE wrapper.''' `Collections.unmodifiableList(l) instanceof List` is
  *     true in java and casting it back yields the VIEW, not the mutable original. A shim that
  *     reported its underlying there would hand a caller the very buffer the wrapper exists to
  *     protect — a silent write-through, which is a worse defect than the one this fixes.
  *
  * It does NOT restore reference identity, and nothing can: see K19 for the half that stays open.
  */
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
    * invokes the function, so the result is re-traversable.
    *
    * Inserted by the engine's retarget coercion mechanism at boundaries where a retarget
    * target (e.g. `DynamicArray`) reaches a `JavaIterable` slot. The target has its own
    * `def iterator: scala.collection.Iterator[A]` but does not extend any scala collection
    * trait, so `from(Iterable)` cannot accept it. This factory takes a closure that
    * re-creates the iterator on each call, preserving multi-use semantics. */
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
