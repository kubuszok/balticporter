package balticporter.runtime

/** `java.lang.Iterable`, as Scala — `scala.collection.Iterable` whose `iterator` is the
  * removal-capable [[JavaIterator]] that java's `Iterable.iterator()` is declared to
  * return. Iteration is unaffected (it IS a `scala.collection.Iterable`, so `for (x <- xs)`,
  * `map`, `foreach` all work); what it adds back is the guarantee java gives, that the
  * iterator you get can remove from the collection you got it from.
  */
trait JavaIterable[A] extends scala.collection.Iterable[A]:
  def iterator: JavaIterator[A]
