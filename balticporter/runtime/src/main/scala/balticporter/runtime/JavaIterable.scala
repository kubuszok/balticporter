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
trait JavaIterable[A]:
  def iterator(): JavaIterator[A]

object JavaIterable:
  /** Adapt a plain scala collection to the java-shaped one. Inserted by the engine at call
    * sites where a shim-typed parameter meets a collection the port ITSELF mapped to scala
    * (`CharArray.appendAll(list)`). `remove()` stays at [[JavaIterator]]'s default —
    * `UnsupportedOperationException` — because a scala iterator genuinely cannot remove,
    * which is what java reports for a non-removable iterator too. */
  def from[A](xs: scala.collection.Iterable[A]): JavaIterable[A] = new JavaIterable[A]:
    def iterator(): JavaIterator[A] = JavaIterator.from(xs.iterator)

  extension [A](self: JavaIterable[A])
    /** A scala view of this java iterable — `for`, `map`, `foreach` and the rest. */
    def asScala: scala.collection.Iterable[A] = new scala.collection.Iterable[A]:
      def iterator: scala.collection.Iterator[A] = self.iterator().asScala
    /** so `for (x <- xs)` and `xs.foreach(f)` work directly on the java shape. Written as
      * the loop rather than delegating to the iterator: `JavaIterator` deliberately has no
      * `foreach` extension (see there), so there is nothing to delegate to. */
    def foreach(f: A => Unit): Unit =
      val it = self.iterator()
      while it.hasNext() do f(it.next())
