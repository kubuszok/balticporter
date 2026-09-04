package balticporter.runtime

import scala.collection.mutable.{ArrayBuffer, Buffer, Set as MSet}

/** `JavaCollection`'s BEHAVIOUR — the shim a port's `Collection`/`AbstractCollection` slots become,
  * and the four factories `CollectionsTransform.coerce` reaches for. */
class JavaCollectionSpec extends munit.FunSuite:

  private def coll(xs: String*): JavaCollection[String] = JavaCollection.from(ArrayBuffer.from(xs))

  // -------------------------------------------------------------------------------------------
  // from — a LIVE view of the buffer, in both directions
  // -------------------------------------------------------------------------------------------

  test("from is a LIVE view — a copy would silently detach every aliased write") {
    // `.asScala` on a nested collection COPIES and turns a view into a snapshot; this is the same
    // hazard from the other side, so it is deliberately backed by the ORIGINAL buffer.
    val b: Buffer[String] = ArrayBuffer("a")
    val c = JavaCollection.from(b)
    b += "b"                       // written through the buffer …
    assertEquals(c.size(), 2)      // … seen through the shim
    c.add("z")                     // and back the other way
    assertEquals(b.toList, List("a", "b", "z"))
    assert(c.remove("a"))
    assertEquals(b.toList, List("b", "z"))
    c.clear()
    assertEquals(b.toList, Nil)
  }

  test("from's `add` always answers true — that is `List` semantics, not `Set` semantics") {
    val b: Buffer[String] = ArrayBuffer("a")
    val c = JavaCollection.from(b)
    assert(c.add("a"))
    assertEquals(b.toList, List("a", "a"))
  }

  // -------------------------------------------------------------------------------------------
  // fromSet — a live view too, with java's `Set` semantics rather than its `List` ones
  // -------------------------------------------------------------------------------------------

  test("fromSet is LIVE, and `add` answers whether the set CHANGED") {
    val s: MSet[String] = MSet("a")
    val c = JavaCollection.fromSet(s)
    assert(!c.add("a"))                 // already present — a `List` would answer true
    assert(c.add("b"))
    assertEquals(s.toList.sorted, List("a", "b"))
    s += "c"                            // live in the other direction
    assertEquals(c.size(), 3)
    assert(c.remove("c"))
    assertEquals(s.toList.sorted, List("a", "b"))
  }

  test("fromSet's contains/remove ask the PROBE, as java.util.AbstractCollection does") {
    val s: MSet[Any] = MSet(new Rejecting)
    val c = JavaCollection.fromSet(s)
    // `s.contains(probe)` would go through the SET's hashing and equality and answer false; this
    // wrapper scans and asks `probe.equals(element)`, which is java's own contract.
    assert(c.contains(new Accepting))
    assert(c.remove(new Accepting))
    assertEquals(s.size, 0)
  }

  /** the asymmetric pair `JavaCollectionsSpec` uses, for the same reason: only an asymmetric
    * `equals` can show WHICH side a containment test asks. */
  private final class Accepting:
    override def equals(o: Any): Boolean = o.isInstanceOf[Rejecting]
    override def hashCode: Int           = 1
  private final class Rejecting:
    override def equals(o: Any): Boolean = false
    override def hashCode: Int           = 1

  // -------------------------------------------------------------------------------------------
  // the read-only wrappers — and that they really do reject
  // -------------------------------------------------------------------------------------------

  test("unmodifiableFrom REJECTS every mutator — `Map.values()` in java is a view that does") {
    val c = JavaCollection.unmodifiableFrom(List("a", "b"))
    assertEquals(c.size(), 2)
    assert(c.contains("a"))
    intercept[UnsupportedOperationException](c.add("c"))
    intercept[UnsupportedOperationException](c.remove("a"))
    intercept[UnsupportedOperationException](c.clear())
  }

  test("unmodifiable carries java's `? <: T` WIDENING — dropping the call would drop the widening") {
    // `<T> Collection<T> unmodifiableCollection(Collection<? extends T>)` is where a
    // `Collection<Sub>` becomes the `Collection<Base>` a method declares it returns, and
    // `JavaCollection` — like java's own `Collection` — is INVARIANT. Erasing this to the identity
    // would not type-check at the call site at all.
    class Base
    class Sub extends Base
    val subs: JavaCollection[Sub]  = JavaCollection.from(ArrayBuffer(new Sub))
    val bases: JavaCollection[Base] = JavaCollection.unmodifiable[Base](subs)
    assertEquals(bases.size(), 1)
    intercept[UnsupportedOperationException](bases.add(new Base))
  }

  test("unmodifiable is a VIEW of the wrapped collection, not a snapshot") {
    val b: Buffer[String] = ArrayBuffer("a")
    val ro = JavaCollection.unmodifiable[String](JavaCollection.from(b))
    b += "b"
    assertEquals(ro.size(), 2)
  }

  // -------------------------------------------------------------------------------------------
  // the trait's own concrete members
  // -------------------------------------------------------------------------------------------

  test("`add` on the BASE throws, because java.util.AbstractCollection.add does") {
    // Not abstract: a subclass that does not override it really does reject `add`, and making it
    // abstract would demand code the source never contained (ENGINE-LIMITS K5).
    val c = new JavaCollection[String]:
      def iterator(): JavaIterator[String] = JavaIterator.from(Iterator("a"))
      def size(): Int                      = 1
    intercept[UnsupportedOperationException](c.add("b"))
    // …while `contains`/`isEmpty` are CONCRETE, which is `AbstractCollection`'s own split.
    assert(c.contains("a"))
    assert(!c.isEmpty())
  }

  test("containsAll / addAll / removeAll / retainAll") {
    val b: Buffer[String] = ArrayBuffer("a", "b", "c")
    val c = JavaCollection.from(b)
    assert(c.containsAll(coll("a", "c")))
    assert(!c.containsAll(coll("a", "z")))
    assert(c.addAll(coll("d")))
    assertEquals(b.toList, List("a", "b", "c", "d"))
    assert(c.removeAll(coll("a", "d")))
    assertEquals(b.toList, List("b", "c"))
    assert(c.retainAll(coll("c")))
    assertEquals(b.toList, List("c"))
  }

  test("the from-backed iterator can REMOVE — which is what makes removeAll/retainAll/removeIf work") {
    // Found by calling them: `AbstractCollection` implements all three as iterate-and-remove, and
    // `JavaIterator.from` hands back the THROWING default — so a wrapper documented as LIVE threw
    // `UnsupportedOperationException` on three of its members, with a green compile and no count
    // moved. Java's own `ArrayList.iterator()` supports removal; a shim standing in for one must,
    // and that is the whole reason `JavaIterator` carries `remove` at all.
    val b: Buffer[String] = ArrayBuffer("a", "b", "c")
    val it = JavaCollection.from(b).iterator()
    // java's `ArrayList.Itr` contract, both error cases included.
    intercept[IllegalStateException](it.remove())
    it.next()
    it.remove()
    intercept[IllegalStateException](it.remove())
    assertEquals(b.toList, List("b", "c"))
    // …and the traversal continues correctly across the removal.
    assertEquals(it.next(), "b")
    assertEquals(it.next(), "c")
    assert(!it.hasNext())
  }

  test("the fromSet-backed iterator can REMOVE too, over a snapshot") {
    val s: MSet[String] = MSet("a", "b")
    val it = JavaCollection.fromSet(s).iterator()
    intercept[IllegalStateException](it.remove())
    it.next()
    it.remove()
    it.next()
    it.remove()
    assert(!it.hasNext())
    assertEquals(s.toList, Nil)
  }

  test("an UNMODIFIABLE view's iterator REFUSES removal — the wrapped one now allows it") {
    // Delegating the iterator would let a caller remove through a view that rejects `remove`: the
    // read-only guarantee gone, with a green compile. A fix in one place opening a hole in another
    // is why this is asserted beside it.
    val b: Buffer[String] = ArrayBuffer("a")
    val ro = JavaCollection.unmodifiable[String](JavaCollection.from(b))
    val it = ro.iterator()
    assertEquals(it.next(), "a")
    intercept[UnsupportedOperationException](it.remove())
    assertEquals(b.toList, List("a"))
    // …and `unmodifiableFrom`'s never could.
    intercept[UnsupportedOperationException] {
      val u = JavaCollection.unmodifiableFrom(List("a")).iterator()
      u.next()
      u.remove()
    }
  }

  test("removeIf takes JAVA's Predicate signature — a ported class OVERRIDES it") {
    // `Predicate<? super A>` rather than `A => Boolean`, because scala requires an override's
    // parameter type to match EXACTLY; mapping it to `Function1` moves the disagreement rather
    // than removing it (ENGINE-LIMITS K6).
    val b: Buffer[String] = ArrayBuffer("a", "bb", "ccc")
    val c = JavaCollection.from(b)
    assert(c.removeIf((s: String) => s.length > 1))
    assertEquals(b.toList, List("a"))
    assert(!c.removeIf((s: String) => s.length > 1))
  }

  // -------------------------------------------------------------------------------------------
  // toArray — java's contract to the letter, including the null terminator
  // -------------------------------------------------------------------------------------------

  test("toArray() copies the elements in iteration order") {
    assertEquals(coll("a", "b").toArray().toList, List[Object]("a", "b"))
    assertEquals(coll().toArray().length, 0)
  }

  test("toArray(T[]) fills the CALLER's array when it is long enough, and NULLS the element past the last") {
    // That trailing null is how a java caller distinguishes the used prefix; without it the method
    // would answer correctly and still be unusable the way java uses it.
    val given_ = new Array[String](4)
    java.util.Arrays.fill(given_.asInstanceOf[Array[Object]], "old")
    val out = coll("a", "b").toArray(given_)
    assert(out eq given_)
    assertEquals(out.toList, List("a", "b", null, "old"))
  }

  test("toArray(T[]) allocates a NEW array of the same component type when the caller's is short") {
    val out = coll("a", "b", "c").toArray(new Array[String](1))
    assertEquals(out.toList, List("a", "b", "c"))
    if PlatformArrays.reifiesComponentType then
      assertEquals(out.getClass.getComponentType, classOf[String])
  }

  // -------------------------------------------------------------------------------------------
  // the scala side: filtered and asScalaBuffer
  // -------------------------------------------------------------------------------------------

  test("filtered takes java's Predicate and keeps the buffer's order — the stream chain's middle link") {
    val out = JavaCollection.filtered(ArrayBuffer("a", "bb", "ccc"), (s: String) => s.length > 1)
    assertEquals(out.toList, List("bb", "ccc"))
  }

  test("asScalaBuffer drains the shim's iterator — a COPY, which the collapse's doc says it is") {
    val b: Buffer[String] = ArrayBuffer("a", "b")
    val out = JavaCollection.from(b).asScalaBuffer
    assertEquals(out.toList, List("a", "b"))
    out += "c"
    assertEquals(b.toList, List("a", "b"))
  }

  test("a JavaCollection IS a JavaIterable, so `for` works through the inherited extension") {
    val c: JavaIterable[String] = coll("a", "b")
    var seen = List.empty[String]
    for x <- c do seen = x :: seen
    assertEquals(seen.reverse, List("a", "b"))
  }
