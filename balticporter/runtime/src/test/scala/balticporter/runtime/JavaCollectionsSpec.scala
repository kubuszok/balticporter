package balticporter.runtime

import scala.collection.mutable.{ArrayBuffer, Buffer, ListBuffer}

/** `JavaCollections`' BEHAVIOUR. Every member here exists because a java call has no scala
  * counterpart with the same MEANING, and every one of those differences is invisible to a compile
  * — which is why an emission spec (`CollectionsStaticsSpec`) is only half the gate. The other half
  * is here, and it was entirely missing: nothing called any of these methods.
  *
  * Each test names the divergence it would catch. A test that only asserted the obvious answer
  * would pass against the very implementation the doc says must not be written.
  */
class JavaCollectionsSpec extends munit.FunSuite:

  private val byNatural: java.util.Comparator[Int] = (a: Int, b: Int) => a - b

  // -------------------------------------------------------------------------------------------
  // sort / sortNatural / reverse — IN PLACE, keeping the caller's identity
  // -------------------------------------------------------------------------------------------

  test("sort is IN PLACE — the reference the caller kept sees the new order") {
    // A sorted COPY compiles, returns the right answer to nobody, and leaves the original in its
    // original order (§4.4, no compile error and no count moved). Java's `Collections.sort` mutates
    // and returns nothing, and every caller reads the list afterwards through the same reference.
    val xs: Buffer[Int] = ArrayBuffer(3, 1, 2)
    val alias = xs
    JavaCollections.sort(xs, byNatural)
    assertEquals(alias.toList, List(1, 2, 3))
    assert(alias eq xs)
  }

  test("…and on a Buffer that is NOT an IndexedSeq, which is why it is not `sortInPlaceWith`") {
    // `sortInPlaceWith` lives on `IndexedSeqOps`, and the port's `List`/`Collection` slots are plain
    // `Buffer` — which a `ListBuffer` also satisfies.
    val xs: Buffer[Int] = ListBuffer(3, 1, 2)
    JavaCollections.sort(xs, byNatural)
    assertEquals(xs.toList, List(1, 2, 3))
  }

  test("sort is STABLE, as java.util.Collections.sort is") {
    // Equal elements keep their relative order. `sortWith` on a `List` is a stable merge sort; a
    // different algorithm would be a silent behavioural change for any comparator that ties.
    val xs: Buffer[(Int, String)] = ArrayBuffer((1, "a"), (0, "x"), (1, "b"), (0, "y"))
    JavaCollections.sort(xs, (a: (Int, String), b: (Int, String)) => a._1 - b._1)
    assertEquals(xs.toList, List((0, "x"), (0, "y"), (1, "a"), (1, "b")))
  }

  test("sortNatural takes java's own `Comparable` bound — scala has no Ordering for an arbitrary A") {
    val xs: Buffer[java.lang.Integer] = ArrayBuffer(3, 1, 2).map(java.lang.Integer.valueOf)
    JavaCollections.sortNatural(xs)
    assertEquals(xs.toList.map(_.intValue), List(1, 2, 3))
  }

  test("reverse is in place too") {
    val xs: Buffer[Int] = ArrayBuffer(1, 2, 3)
    val alias = xs
    JavaCollections.reverse(xs)
    assertEquals(alias.toList, List(3, 2, 1))
  }

  test("swap exchanges TWO positions and leaves the rest — never a clear-and-refill") {
    // The buffer is aliased throughout, which is the case jbump's `Collisions.keySort` is in: it
    // swaps through a `List<?>` the caller still holds. Rebuilding the whole buffer would work and
    // would make an EMPTY intermediate state observable where java's two `set` calls never do.
    val xs: Buffer[Int] = ArrayBuffer(10, 11, 12, 13)
    val alias = xs
    JavaCollections.swap(xs, 0, 3)
    assertEquals(alias.toList, List(13, 11, 12, 10))
    assert(alias eq xs)
  }

  test("swap of a position with ITSELF is a no-op, as java's is") {
    val xs: Buffer[Int] = ArrayBuffer(1, 2, 3)
    JavaCollections.swap(xs, 1, 1)
    assertEquals(xs.toList, List(1, 2, 3))
  }

  test("swap out of range throws, as java's does — the bound is not silently widened") {
    val xs: Buffer[Int] = ArrayBuffer(1, 2, 3)
    intercept[IndexOutOfBoundsException](JavaCollections.swap(xs, 0, 3))
  }

  test("swap works on a Buffer that is not an IndexedSeq") {
    val xs: Buffer[Int] = ListBuffer(1, 2, 3)
    JavaCollections.swap(xs, 0, 2)
    assertEquals(xs.toList, List(3, 2, 1))
  }

  // -------------------------------------------------------------------------------------------
  // shuffle — JAVA'S ALGORITHM, which is the whole claim
  // -------------------------------------------------------------------------------------------

  test("shuffle produces java.util.Collections.shuffle's OWN permutation from the same seed") {
    // The fidelity claim, checked against the authority rather than restated: any other correct
    // shuffle consumes the `Random` differently and therefore permutes differently, and a seeded
    // shuffle is only ever written because the caller wants a reproducible one. simple-graphs'
    // `GraphTest` seeds `new Random(123)` and asserts on what follows.
    for seed <- List(0L, 1L, 42L, 123L) do
      for n <- List(0, 1, 2, 5, 17, 64) do
        val expected = new java.util.ArrayList[Integer]()
        (0 until n).foreach(i => expected.add(i))
        java.util.Collections.shuffle(expected, new java.util.Random(seed))

        val got: Buffer[Integer] = ArrayBuffer.from((0 until n).map(Integer.valueOf))
        JavaCollections.shuffle(got, new java.util.Random(seed))

        assertEquals(got.toList, expected.toArray.toList, s"seed=$seed n=$n")
  }

  test("shuffle is in place, and permutes rather than replacing") {
    val xs: Buffer[Int] = ArrayBuffer.from(0 until 20)
    val alias = xs
    JavaCollections.shuffle(xs, new java.util.Random(7))
    assert(alias eq xs)
    assertEquals(alias.toList.sorted, (0 until 20).toList)
  }

  // -------------------------------------------------------------------------------------------
  // removeValue — the RESULT and the DIRECTION of the equality
  // -------------------------------------------------------------------------------------------

  /** An asymmetric pair, in eight lines: `Accepting.equals(x)` is true for a `Rejecting` and
    * `Rejecting.equals(x)` is never true. Exactly the shape `java.sql.Timestamp` has against
    * `java.util.Date`, and the only shape that can tell the two directions apart. */
  private final class Accepting:
    override def equals(o: Any): Boolean = o.isInstanceOf[Rejecting]
    override def hashCode: Int           = 1
  private final class Rejecting:
    override def equals(o: Any): Boolean = false
    override def hashCode: Int           = 1

  test("removeValue asks the PROBE, as java's Collection.remove(Object) does — in BOTH directions") {
    // Java's `ArrayList.remove(Object o)` tests `o.equals(element)`. Only an asymmetric pair can
    // show which side is asked, and it has to be tested in both roles: an implementation that asked
    // the ELEMENT would get the opposite answer to each of these.
    val elementAccepts: Buffer[Any] = ArrayBuffer(new Accepting)
    val probeAccepts: Buffer[Any]   = ArrayBuffer(new Rejecting)

    // probe.equals(element) = true  -> java removes; asking the element would answer false.
    assert(JavaCollections.removeValue(probeAccepts, new Accepting))
    assertEquals(probeAccepts.size, 0)

    // probe.equals(element) = false -> java does NOT remove; asking the element would remove it.
    assert(!JavaCollections.removeValue(elementAccepts, new Rejecting))
    assertEquals(elementAccepts.size, 1)
  }

  test("MEASURED: scala's own `indexOf` asks the PROBE too — so the DIRECTION is not what made this a helper") {
    // `JavaCollections.removeValue`'s doc used to claim `xs.indexOf(o)` tests the ELEMENT's equals.
    // It does not: `SeqOps.indexOf(elem)` is `indexWhere(elem == _)`, so scala already agrees with
    // java here. Pinned as a fact rather than deleted, because it is the fact that makes the doc's
    // OTHER two reasons — the boolean RESULT and the explicit null arm — the real ones, and because
    // a future `indexWhere(_ == o)` written by hand WOULD diverge.
    val xs: Buffer[Any] = ArrayBuffer(new Rejecting)
    assertEquals(xs.indexOf(new Accepting), 0)          // the probe is asked
    assertEquals(xs.indexWhere(e => e == new Accepting), -1) // the element is asked — the other answer
  }

  test("removeValue returns WHETHER it removed — code branches on it, and `-=` returns the buffer") {
    val xs: Buffer[String] = ArrayBuffer("a", "b")
    assert(JavaCollections.removeValue(xs, "a"))
    assert(!JavaCollections.removeValue(xs, "zzz"))
    assertEquals(xs.toList, List("b"))
  }

  test("removeValue removes only the FIRST match, as java's does") {
    val xs: Buffer[String] = ArrayBuffer("a", "b", "a")
    assert(JavaCollections.removeValue(xs, "a"))
    assertEquals(xs.toList, List("b", "a"))
  }

  test("removeValue has java's explicit NULL arm") {
    val xs: Buffer[String] = ArrayBuffer("a", null, "b")
    assert(JavaCollections.removeValue(xs, null))
    assertEquals(xs.toList, List("a", "b"))
    assert(!JavaCollections.removeValue(xs, null))
  }

  // -------------------------------------------------------------------------------------------
  // Arrays.asList — the ELEMENT form only
  // -------------------------------------------------------------------------------------------

  test("asList carries the elements in order, and the empty call is a legal one") {
    assertEquals(JavaCollections.asList(1, 2, 3).toList, List(1, 2, 3))
    assertEquals(JavaCollections.asList[String]().toList, Nil)
    // the documented, PERMISSIVE divergence: java's list is fixed-size and this one is growable.
    // Code java would have rejected now runs, which cannot turn a correct program into a wrong one.
    val xs = JavaCollections.asList("a")
    xs += "b"
    assertEquals(xs.toList, List("a", "b"))
  }

  test("asList does NOT alias an array — the aliasing form goes to asListView, never here") {
    val arr = Array("a", "b")
    val xs  = JavaCollections.asList(arr*)
    arr(0) = "changed"
    assertEquals(xs.toList, List("a", "b"))
  }

  test("sortNatural takes JAVA's bound, so a Comparable<Object> element still sorts") {
    // java's signature is `<T extends Comparable<? super T>>`, not `Comparable<T>`. A
    // `List<Comparable<Object>>` — what a library sorting heterogeneous values declares — satisfies
    // java's bound and not the narrower one, and read `Found: Buffer[Comparable[Object]]`.
    val xs = scala.collection.mutable.ArrayBuffer[Comparable[Object]](
      new Comparable[Object] { def compareTo(o: Object): Int = 1 },
      new Comparable[Object] { def compareTo(o: Object): Int = -1 },
    )
    JavaCollections.sortNatural(xs)
    assertEquals(xs.size, 2)
    // …and the ordinary case is unchanged
    val ys = scala.collection.mutable.ArrayBuffer[java.lang.Integer](3, 1, 2)
    JavaCollections.sortNatural(ys)
    assertEquals(ys.toList.map(_.intValue), List(1, 2, 3))
  }

  test("noneMatch is java's, including `true` on an empty source") {
    assert(JavaCollections.noneMatch(List(1, 2, 3), (_: Int) > 5))
    assert(!JavaCollections.noneMatch(List(1, 2, 3), (_: Int) > 2))
    assert(JavaCollections.noneMatch(List.empty[Int], (_: Int) > 0))
  }

  // -------------------------------------------------------------------------------------------
  // addAll — java's read off an UNBOUNDED WILDCARD source
  // -------------------------------------------------------------------------------------------

  test("addAll appends in iteration order and returns java's `changed` boolean") {
    val dst = scala.collection.mutable.ArrayBuffer[Object]("a")
    assert(JavaCollections.addAll(dst, List[Object]("b", "c")))
    assertEquals(dst.toList, List[Object]("a", "b", "c"))
    // java's `addAll` returns FALSE for an empty source, and code branches on it
    assert(!JavaCollections.addAll(dst, Nil))
    assertEquals(dst.size, 3)
  }

  test("a WILDCARD-elemented source reaches an Object destination — the whole of F11") {
    // this is the call that does not type as `dst ++= src`: java's `List<?>` is
    // `List<? extends Object>`, scala's `?` is bounded by `Any`.
    val src: scala.collection.mutable.Buffer[?] = scala.collection.mutable.ArrayBuffer("x", 1)
    val dst = scala.collection.mutable.ArrayBuffer.empty[Object]
    assert(JavaCollections.addAll(dst, src))
    assertEquals(dst.toList, List[Object]("x", java.lang.Integer.valueOf(1)))
  }

  test("…and the cast is java's ERASURE, so nothing throws that java would not") {
    // `E` is erased, so `asInstanceOf[E]` is a no-op — exactly what java's own unchecked `addAll`
    // does. A value that does not belong shows up where java shows it: at the READ, not here.
    val src: scala.collection.mutable.Buffer[?] = scala.collection.mutable.ArrayBuffer(1, 2)
    val dst = scala.collection.mutable.ArrayBuffer.empty[String]
    assert(JavaCollections.addAll(dst, src))
    assertEquals(dst.size, 2)
  }

  test("addAll takes any IterableOnce source — a Set and a map's entries, as java's Collection does") {
    val dst = scala.collection.mutable.ArrayBuffer.empty[Object]
    JavaCollections.addAll(dst, scala.collection.mutable.Set[Object]("s"))
    assertEquals(dst.toList, List[Object]("s"))
    val dst2 = scala.collection.mutable.ArrayBuffer.empty[Object]
    JavaCollections.addAll(dst2, scala.collection.mutable.Map("k" -> 1))
    assertEquals(dst2.toList, List[Object](("k", 1)))
  }

  // -------------------------------------------------------------------------------------------
  // `java.util.Collection`'s BULK DEFAULTS at the SECOND target — the K29 receiver contract
  // -------------------------------------------------------------------------------------------
  //
  // `addAll`/`removeAll`/`retainAll` are what a java class INHERITS from `AbstractCollection` and
  // calls through `super`. The re-parenting removes them, so the phase supplies them — and the whole
  // point of the receiver being an intersection rather than a `Buffer` is that a definer is
  // re-parented onto EITHER `mutable.Buffer` or `mutable.Set`. Both are exercised below, and so is
  // the shape the emitted call actually has: a GENERIC class extending `mutable.Set`, calling the
  // helper on `this`. That third one is a TYPE-INFERENCE assertion as much as a behavioural one —
  // an intersection is a place scala's inference can decline, and a helper that does not infer at
  // the emitted shape is a helper the emitted call cannot use.

  /** the emitted shape itself: a class that DEFINES a set, re-parented onto `mutable.Set`, whose
    * `super.<default>` the phase rewrites to a helper standing on `this`. */
  private class Definer[E] extends scala.collection.mutable.Set[E]:
    private val items                            = ArrayBuffer.empty[E]
    def iterator: Iterator[E]                    = items.iterator
    def contains(e: E): Boolean                  = items.contains(e)
    def addOne(e: E): this.type                  = { if !items.contains(e) then items += e; this }
    def subtractOne(e: E): this.type             = { items -= e; this }
    override def clear(): Unit                   = items.clear()
    // …and these are what the phase emits in place of `super.<name>(c)`.
    def addAllJ(c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean    = JavaCollections.addAll(this, c)
    def removeAllJ(c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = JavaCollections.removeAll(this, c)
    def retainAllJ(c: scala.collection.IterableOnce[?] | JavaIterable[?]): Boolean = JavaCollections.retainAll(this, c)

  test("removeAll removes EVERY occurrence — which is what separates it from `removeValue`") {
    // java's `remove(Object)` removes the first match; its `removeAll` removes all of them. A helper
    // written over `-=` alone would answer the first question at a member that asks the second, and
    // the port would silently keep the duplicates.
    val xs: Buffer[String] = ArrayBuffer("a", "b", "a", "c", "a")
    assert(JavaCollections.removeAll(xs, List("a")))
    assertEquals(xs.toList, List("b", "c"))
    assert(!JavaCollections.removeAll(xs, List("z")), "java returns whether anything went")
    assert(!JavaCollections.removeAll(xs, Nil), "an empty argument removes nothing")
    assertEquals(xs.toList, List("b", "c"))
  }

  test("retainAll is removeAll's complement, and an EMPTY argument EMPTIES the receiver") {
    // read as "retain — so nothing to do", the empty case would be a no-op. Java's loop removes
    // every element `c` does not contain, and an empty `c` contains none of them.
    val xs: Buffer[String] = ArrayBuffer("a", "b", "a", "c")
    assert(JavaCollections.retainAll(xs, List("a", "c")))
    assertEquals(xs.toList, List("a", "a", "c"), "every retained occurrence stays")
    assert(!JavaCollections.retainAll(xs, List("a", "c", "z")), "nothing to drop is java's `false`")
    val ys: Buffer[String] = ArrayBuffer("a")
    assert(JavaCollections.retainAll(ys, Nil))
    assertEquals(ys.toList, Nil)
  }

  test("the equality DIRECTION is `c.contains(e)` — the OPPOSITE of containsAll's, and it is java's") {
    // `containsAll` asks `this.contains(o)` for each `o` of the argument, so the probe is the
    // ARGUMENT's element. These two ask `c.contains(e)` for each `e` of the receiver, so the probe
    // is the RECEIVER's. The two directions differ for any asymmetric `equals`, and a helper that
    // picked the wrong one compiles and moves no count.
    class Elem  extends AnyRef { override def equals(o: Any): Boolean = true  }
    class Probe extends AnyRef { override def equals(o: Any): Boolean = false }
    val xs: Buffer[AnyRef] = ArrayBuffer(new Elem)
    assert(JavaCollections.removeAll(xs, List(new Probe)), "elem.equals(probe) is what java asks here")
    assertEquals(xs.size, 0)
    // …and the other member really does read it the other way round, at the same pair.
    val ys: Buffer[AnyRef] = ArrayBuffer(new Elem)
    assertEquals(JavaCollections.containsAll(ys, List(new Probe)), false,
                 "containsAll asks the ARGUMENT's equals — the opposite direction, same pair")
  }

  test("…and java's null arm on both: a null element is matched by a null probe") {
    val xs: Buffer[String] = ArrayBuffer("a", null, "b")
    assert(JavaCollections.removeAll(xs, List(null)))
    assertEquals(xs.toList, List("a", "b"))
    val ys: Buffer[String] = ArrayBuffer("a", null)
    assert(JavaCollections.retainAll(ys, List(null)))
    assertEquals(ys.toList, List(null))
  }

  test("the BUFFER arm removes POSITIONALLY, so a live alias sees java's own sequence") {
    // `removeIf`'s doc states the rule these two inherit: an element is identified by its PLACE, not
    // by an `equals` a second element might also satisfy — and the removal happens through the
    // receiver the caller kept, never through a filtered copy.
    val xs: Buffer[Int] = ListBuffer(1, 2, 3, 4)
    val alias = xs
    assert(JavaCollections.removeAll(xs, List(2, 4)))
    assertEquals(alias.toList, List(1, 3))
    assert(alias eq xs)
  }

  test("the SET arm: same three members, at the other target a definer is re-parented onto") {
    val s = scala.collection.mutable.LinkedHashSet("a", "b", "c")
    assert(JavaCollections.removeAll(s, List("b")))
    assertEquals(s.toList, List("a", "c"))
    assert(JavaCollections.retainAll(s, List("c", "z")))
    assertEquals(s.toList, List("c"))
    assert(JavaCollections.addAll(s, List("d")))
    assertEquals(s.toList, List("c", "d"))
    // java's `Set.addAll` returns FALSE when nothing was absent — which is exactly when the size
    // does not move, so the one size test serves both targets.
    assert(!JavaCollections.addAll(s, List("d")))
    assertEquals(s.toList, List("c", "d"))
  }

  test("the EMITTED shape infers: a generic class extending mutable.Set, calling on `this`") {
    // If the intersection did not infer here, the phase's rewrite would not compile — and nothing in
    // an emission spec would say so, because the emitted TEXT would be exactly right.
    val d = new Definer[String]
    assert(d.addAllJ(List("a", "b", "c")))
    assertEquals(d.toList, List("a", "b", "c"))
    assert(d.removeAllJ(List("b")))
    assertEquals(d.toList, List("a", "c"))
    assert(d.retainAllJ(List("c")))
    assertEquals(d.toList, List("c"))
  }

  test("…and all three take the JAVA-SHAPED side of the union, which is why it is a union") {
    // The argument of an inherited bulk operation is the shim as often as it is a scala collection:
    // the declaration's own parameter is emitted `JavaCollection[?]` while the receiver is the class
    // this phase re-parented. A one-sided formal would reject exactly the site the helper is for.
    val shim: JavaIterable[String] = JavaCollection.from(ArrayBuffer("b", "z"))
    val xs: Buffer[String]         = ArrayBuffer("a", "b", "c")
    assert(JavaCollections.removeAll(xs, shim))
    assertEquals(xs.toList, List("a", "c"))
    val d = new Definer[String]
    assert(d.addAllJ(shim))
    assertEquals(d.toList, List("b", "z"))
    assert(d.retainAllJ(JavaCollection.from(ArrayBuffer("z"))))
    assertEquals(d.toList, List("z"))
  }

  // -------------------------------------------------------------------------------------------
  // Arrays.asList(T[]) — the ALIASING form, which is a live fixed-size VIEW and not a copy
  // -------------------------------------------------------------------------------------------

  test("asListView READS THROUGH — a write to the array is visible in the list") {
    val arr = Array("a", "b")
    val xs  = JavaCollections.asListView(arr)
    assertEquals(xs.toList, List("a", "b"))
    arr(0) = "changed"
    assertEquals(xs(0), "changed")
    assertEquals(xs.toList, List("changed", "b"))
  }

  test("…and WRITES THROUGH — `set` reaches the caller's array. This is the half a copy loses") {
    val arr = Array("a", "b")
    val xs  = JavaCollections.asListView(arr)
    xs(1) = "written"
    assertEquals(arr(1), "written")
  }

  test("asListView is FIXED-SIZE — every resizing operation throws, as java's does") {
    val arr = Array("a", "b")
    val xs  = JavaCollections.asListView(arr)
    assertEquals(xs.length, 2)
    intercept[UnsupportedOperationException](xs += "c")
    intercept[UnsupportedOperationException](xs.remove(0))
    intercept[UnsupportedOperationException](xs.insert(0, "z"))
    intercept[UnsupportedOperationException](xs.prepend("z"))
    intercept[UnsupportedOperationException](xs.clear())
    // …and none of them touched the array
    assertEquals(arr.toList, List("a", "b"))
  }

  test("asListView(null) throws AT THE CALL — java's `Objects.requireNonNull` in the constructor") {
    // `java.util.Arrays.asList(T[])` is `new ArrayList<>(a)`, whose constructor is
    // `a = Objects.requireNonNull(array)`: a null array is an NPE at the CALL, before the caller
    // holds anything. The view constructed lazily throws too — but at the first READ, an arbitrary
    // distance away, in whichever member happened to touch it first. Same exception, different
    // stack, different member, and the correlator anchors the failure on the wrong frame.
    //
    // A one-line difference in WHEN, which is the whole of what a faithful translation of a
    // fail-fast contract is.
    intercept[NullPointerException](JavaCollections.asListView(null))
  }

  test("asListView ITERATES the array, and a derived collection is an ordinary one") {
    val arr = Array(1, 2, 3)
    val xs  = JavaCollections.asListView(arr)
    assertEquals(xs.iterator.toList, List(1, 2, 3))
    assertEquals(xs.map(_ * 2).toList, List(2, 4, 6))
    // a MAPPED buffer is a fresh ArrayBuffer, so it is growable — java's `asList().stream()` is
    // the same shape: reading off a fixed-size view does not make the result fixed-size.
    val ys = xs.map(_ * 2)
    ys += 8
    assertEquals(ys.toList, List(2, 4, 6, 8))
    assertEquals(arr.toList, List(1, 2, 3))
  }

  test("asListView keeps the ARRAY's element type — java's own inference, not the erased one") {
    // the whole reason `CollectionsTransform.asListViewArg` strips the erasure coercion: an
    // `Array[String]` must yield a `Buffer[String]`, not a `Buffer[Object]`.
    val xs: scala.collection.mutable.Buffer[String] = JavaCollections.asListView(Array("a"))
    assertEquals(xs.head.length, 1)
  }

  test("an EMPTY array is a legal view, and still fixed-size") {
    val xs = JavaCollections.asListView(Array.empty[String])
    assertEquals(xs.length, 0)
    assert(xs.isEmpty)
    intercept[UnsupportedOperationException](xs += "a")
  }

  // -------------------------------------------------------------------------------------------
  // toArray — java's THREE-part contract, none of which a naive `xs.toArray` honours
  // -------------------------------------------------------------------------------------------

  test("toArray() allocates Object[] — NOT an array of the element's runtime class") {
    // scala's own `toArray` takes a `ClassTag[B]` and allocates on the ELEMENT's class, so a
    // `Buffer[String]` would hand back a `String[]` where java hands back an `Object[]`. Where the
    // result flows into an `Object` that type-checks and then throws `ArrayStoreException` on the
    // first non-String store — which java permits through an `Object[]`.
    val out = JavaCollections.toArray(ArrayBuffer("a", "b"))
    assertEquals(out.getClass.getComponentType, classOf[Object])
    assertEquals(out.toList, List[Object]("a", "b"))
    out(0) = Integer.valueOf(1) // an Object[] accepts this; a String[] would throw
    assertEquals(out(0), Integer.valueOf(1): Object)
  }

  test("toArray() is exactly `size` long and in ITERATION order") {
    assertEquals(JavaCollections.toArray(ArrayBuffer.empty[String]).length, 0)
    assertEquals(JavaCollections.toArray(ListBuffer(3, 1, 2)).toList.map(_.toString), List("3", "1", "2"))
  }

  test("toArray(a) FILLS the caller's array when the elements fit, and returns THAT array") {
    // java's contract, and the reason this is not an allocate-and-copy: a caller may pass an array
    // it still holds and read it afterwards. An implementation that always allocated would compile,
    // return the right elements and leave the caller's array untouched (§4.4).
    val a   = new Array[String](3)
    val out = JavaCollections.toArray(ArrayBuffer("x", "y"), a)
    assert(out eq a, "the caller's array must be the array returned when it fits")
    assertEquals(a(0), "x")
    assertEquals(a(1), "y")
  }

  test("…and writes the NULL TERMINATOR at index `size` when the array is longer") {
    val a = Array("p", "q", "r", "s")
    JavaCollections.toArray(ArrayBuffer("x", "y"), a)
    assertEquals(a(2), null, "java sets exactly index `size` to null")
    assertEquals(a(3), "s", "…and leaves the rest of the tail alone")
  }

  test("toArray(a) allocates on the argument's RUNTIME component type when it does not fit") {
    // `java.util.Arrays.copyOf` and not a fresh `Array[A]`: the element type is erased here, so a
    // fresh array would have component type `Object` and the caller's `T[]`-typed reference would
    // throw `ArrayStoreException` on its first store.
    val a: Array[Object] = new Array[String](0).asInstanceOf[Array[Object]]
    val out = JavaCollections.toArray(ArrayBuffer("x", "y"), a)
    assert(!(out eq a))
    assertEquals(out.length, 2)
    assertEquals(out.getClass.getComponentType, classOf[String])
    assertEquals(out.toList, List[Object]("x", "y"))
  }

  test("toArray(a) on an exact fit writes no terminator and does not grow") {
    val a   = new Array[String](2)
    val out = JavaCollections.toArray(ArrayBuffer("x", "y"), a)
    assert(out eq a)
    assertEquals(out.toList, List("x", "y"))
  }

  // -------------------------------------------------------------------------------------------
  // the IMMUTABLE producers — the half of the divergence a compile cannot see
  // -------------------------------------------------------------------------------------------

  test("emptyList/emptyMap/emptySet REFUSE mutation, as java's do") {
    // `mutable.ArrayBuffer.empty` would compile and be wrong in the one direction that matters:
    // the result of these factories is routinely stored in a shared static, and a caller that puts
    // into it gets `UnsupportedOperationException` in java and silently corrupts a global here.
    intercept[UnsupportedOperationException](JavaCollections.emptyList[String]() += "x")
    intercept[UnsupportedOperationException](JavaCollections.emptySet[String]() += "x")
    intercept[UnsupportedOperationException](JavaCollections.emptyMap[String, Int]().put("k", 1))
    intercept[UnsupportedOperationException](JavaCollections.emptyList[String]().clear())
    intercept[UnsupportedOperationException](JavaCollections.emptyMap[String, Int]() -= "k")
  }

  test("…and READ as ordinary scala collections") {
    assertEquals(JavaCollections.emptyList[String]().size, 0)
    assertEquals(JavaCollections.emptyList[String]().toList, Nil)
    assertEquals(JavaCollections.emptyMap[String, Int]().get("k"), None)
    assert(!JavaCollections.emptySet[String]().contains("x"))
    // a derived collection is an ordinary mutable one, as java's read operations produce
    assertEquals(JavaCollections.singletonList("a").map(_.toUpperCase).toList, List("A"))
  }

  test("emptyList/emptyMap/emptySet are SHARED, as java's are — reference identity is observable") {
    // java's `Collections.EMPTY_LIST` is one instance, and a java `xs == Collections.emptyList()`
    // is a REFERENCE comparison, which this engine emits as `eq` (§4.4). A fresh instance per call
    // would answer `false` where java answers `true`.
    assert(JavaCollections.emptyList[String]() eq JavaCollections.emptyList[Int]())
    assert(JavaCollections.emptyMap[String, Int]() eq JavaCollections.emptyMap[Int, Int]())
    assert(JavaCollections.emptySet[String]() eq JavaCollections.emptySet[Int]())
  }

  test("singletonList/singleton/singletonMap carry the one element and refuse mutation") {
    assertEquals(JavaCollections.singletonList("a").toList, List("a"))
    assertEquals(JavaCollections.singleton("a").toList, List("a"))
    assertEquals(JavaCollections.singletonMap("k", 1).get("k"), Some(1))
    intercept[UnsupportedOperationException](JavaCollections.singletonList("a") += "b")
    intercept[UnsupportedOperationException](JavaCollections.singletonList("a").update(0, "b"))
    intercept[UnsupportedOperationException](JavaCollections.singletonList("a").remove(0))
    intercept[UnsupportedOperationException](JavaCollections.singleton("a") -= "a")
    intercept[UnsupportedOperationException](JavaCollections.singletonMap("k", 1).put("j", 2))
    // …and are FRESH per call, as java's `new SingletonList<>(o)` is
    assert(!(JavaCollections.singletonList("a") eq JavaCollections.singletonList("a")))
  }

  test("unmodifiableList/Set/Map are LIVE VIEWS — a later change to the source is visible") {
    // The distinction that made these unmappable while the only candidates were the stdlib's: a
    // COPY compiles, returns the right elements now, and silently detaches every later change
    // (§4.4). Java's result reflects them; so does this.
    val xs: Buffer[String] = ArrayBuffer("a")
    val ro = JavaCollections.unmodifiableList(xs)
    xs += "b"
    assertEquals(ro.toList, List("a", "b"))
    val s = scala.collection.mutable.Set("a")
    val ros = JavaCollections.unmodifiableSet(s)
    s += "b"
    assert(ros.contains("b"))
    val m = scala.collection.mutable.Map("k" -> 1)
    val rom = JavaCollections.unmodifiableMap(m)
    m("j") = 2
    assertEquals(rom.get("j"), Some(2))
  }

  test("…and REFUSE every write, which is the other half — the identity would drop it silently") {
    val xs: Buffer[String] = ArrayBuffer("a")
    intercept[UnsupportedOperationException](JavaCollections.unmodifiableList(xs) += "b")
    intercept[UnsupportedOperationException](JavaCollections.unmodifiableList(xs).update(0, "b"))
    intercept[UnsupportedOperationException](JavaCollections.unmodifiableSet(scala.collection.mutable.Set("a")) += "b")
    intercept[UnsupportedOperationException](JavaCollections.unmodifiableMap(scala.collection.mutable.Map("k" -> 1)).put("j", 2))
  }

  // -------------------------------------------------------------------------------------------
  // subList / putIfAbsent — scala HAS both operations and both mean something else
  // -------------------------------------------------------------------------------------------

  test("subList WRITES THROUGH — `xs.slice` would compile and detach every one of these") {
    val xs: Buffer[Int] = ArrayBuffer(0, 1, 2, 3, 4)
    val sub = JavaCollections.subList(xs, 1, 4)
    assertEquals(sub.toList, List(1, 2, 3))
    sub(0) = 99
    assertEquals(xs.toList, List(0, 99, 2, 3, 4), "set through the view reaches the backing list")
    // …and the other direction: a change to the backing list is visible through the view.
    xs(2) = 77
    assertEquals(sub.toList, List(99, 77, 3))
  }

  test("…and `subList(a, b).clear()` removes THAT RANGE from the list, which is java's own idiom") {
    val xs: Buffer[Int] = ArrayBuffer(0, 1, 2, 3, 4)
    JavaCollections.subList(xs, 1, 4).clear()
    assertEquals(xs.toList, List(0, 4))
  }

  test("subList grows and shrinks the BACKING list, and its own end moves with it") {
    val xs: Buffer[Int] = ArrayBuffer(0, 1, 2, 3)
    val sub = JavaCollections.subList(xs, 1, 3)
    sub += 9
    assertEquals(xs.toList, List(0, 1, 2, 9, 3))
    assertEquals(sub.toList, List(1, 2, 9))
    assertEquals(sub.remove(0), 1)
    assertEquals(xs.toList, List(0, 2, 9, 3))
    assertEquals(sub.toList, List(2, 9))
  }

  test("subList keeps java's BOUNDS — a silently clamped range is a wrong answer, not a loud one") {
    val xs: Buffer[Int] = ArrayBuffer(0, 1, 2)
    intercept[IndexOutOfBoundsException](JavaCollections.subList(xs, -1, 2))
    intercept[IndexOutOfBoundsException](JavaCollections.subList(xs, 0, 4))
    intercept[IndexOutOfBoundsException](JavaCollections.subList(xs, 2, 1))
    assertEquals(JavaCollections.subList(xs, 1, 1).toList, Nil) // empty is legal
    intercept[IndexOutOfBoundsException](JavaCollections.subList(xs, 0, 2)(2))
  }

  test("putIfAbsent returns the PREVIOUS value — null on a successful insertion, unlike getOrElseUpdate") {
    // `getOrElseUpdate` returns the value now in the map, i.e. the NEW one on an insertion, so
    // every `if (m.putIfAbsent(k, v) == null)` would take the other branch with no compile error.
    val m = scala.collection.mutable.Map.empty[String, String]
    assertEquals(JavaCollections.putIfAbsent(m, "k", "v"), null)
    assertEquals(m("k"), "v")
    assertEquals(JavaCollections.putIfAbsent(m, "k", "w"), "v", "the previous value, and no overwrite")
    assertEquals(m("k"), "v")
  }

  test("…and a key mapped to NULL counts as absent, which is java's own default body") {
    val m = scala.collection.mutable.Map[String, String]("k" -> null)
    assertEquals(JavaCollections.putIfAbsent(m, "k", "v"), null)
    assertEquals(m("k"), "v")
  }

  // -------------------------------------------------------------------------------------------
  // Map.Entry's statics over the Tuple2 a Map.Entry becomes
  // -------------------------------------------------------------------------------------------

  test("comparingByKey / comparingByValue compare the right half of the pair") {
    val byLen: java.util.Comparator[String] = (a: String, b: String) => a.length - b.length
    val pairs = List(("bbb", 1), ("a", 3), ("bb", 2))
    assertEquals(pairs.sortWith(JavaCollections.comparingByKey[String, Int](byLen).compare(_, _) < 0).map(_._1),
                 List("a", "bb", "bbb"))
    val byInt: java.util.Comparator[Int] = (a: Int, b: Int) => a - b
    assertEquals(pairs.sortWith(JavaCollections.comparingByValue[String, Int](byInt).compare(_, _) < 0).map(_._2),
                 List(1, 2, 3))
  }

  // -------------------------------------------------------------------------------------------
  // the stream chain's surviving links
  // -------------------------------------------------------------------------------------------

  test("sortedWith is a COPY — java's stream operation does not mutate its source, unlike `sort`") {
    // The two differ because the java methods differ, not because one shape was convenient: the
    // collection a stream reads is often one the caller still holds.
    val xs: Buffer[Int] = ArrayBuffer(3, 1, 2)
    val out = JavaCollections.sortedWith(xs, byNatural)
    assertEquals(out.toList, List(1, 2, 3))
    assertEquals(xs.toList, List(3, 1, 2))
  }

  test("mapToDouble WIDENS — which is the only reason it is a named helper and not `.map`") {
    // Left as `.map(f).sum` a float-valued lambda sums in FLOAT: the same answer to within a few
    // ulps, which passes a tolerance assertion until the collection is large enough and then does
    // not. Declaring `A => Double` makes scala insert the widening java's `ToDoubleFunction` does,
    // at the same place.
    val fs: Buffer[Float]  = ArrayBuffer.fill(1000)(0.1f)
    val widened            = JavaCollections.mapToDouble(fs, f => f).sum
    val inFloat: Float     = fs.sum
    assertEquals(widened, fs.foldLeft(0.0)((a, f) => a + f.toDouble))
    assert(widened != inFloat.toDouble, s"float and double accumulation must differ: $widened vs ${inFloat.toDouble}")
  }

  test("intRange is HALF-OPEN, as java's IntStream.range is") {
    assertEquals(JavaCollections.intRange(0, 3).toList, List(0, 1, 2))
    assertEquals(JavaCollections.intRange(2, 2).toList, Nil)
    assertEquals(JavaCollections.intRange(5, 3).toList, Nil)
  }

  test("toSet collects to a Set — the target type is why it could not ride on toList's arm") {
    assertEquals(JavaCollections.toSet(ArrayBuffer(1, 2, 2, 3)).toList.sorted, List(1, 2, 3))
    assertEquals(JavaCollections.toSet(ArrayBuffer.empty[Int]).size, 0)
  }

  test("toMap(k, v) THROWS on a duplicate key, where a scala `.toMap` over pairs keeps the last") {
    // Java's two-argument collector merges with a remapping function that throws
    // `IllegalStateException`. A stream whose keys collide is a bug java reports loudly and the
    // naive `.map(x => k(x) -> v(x)).toMap` hides — no compile error, no count moved.
    val id: java.util.function.Function[String, String]  = (s: String) => s
    val len: java.util.function.Function[String, Int]    = (s: String) => s.length
    assertEquals(JavaCollections.toMap(ArrayBuffer("a", "bb"), id, len).toMap, Map("a" -> 1, "bb" -> 2))
    val head: java.util.function.Function[String, Char] = (s: String) => s.charAt(0)
    intercept[IllegalStateException](JavaCollections.toMap(ArrayBuffer("ax", "ay"), head, len))
  }

  test("toMap(k, v, merge) runs merge(EXISTING, INCOMING) — the order inverts every resolver") {
    // `(a, b) -> b` is last-wins and `(a, b) -> a` is first-wins; swapping the two arguments turns
    // each into the other with nothing in the compile to show for it.
    val head: java.util.function.Function[String, Char] = (s: String) => s.charAt(0)
    val id: java.util.function.Function[String, String] = (s: String) => s
    val last: java.util.function.BinaryOperator[String] = (_: String, b: String) => b
    val first: java.util.function.BinaryOperator[String] = (a: String, _: String) => a
    assertEquals(JavaCollections.toMap(ArrayBuffer("ax", "ay"), head, id, last).toMap, Map('a' -> "ay"))
    assertEquals(JavaCollections.toMap(ArrayBuffer("ax", "ay"), head, id, first).toMap, Map('a' -> "ax"))
  }

  test("…and a merge returning NULL REMOVES the mapping, which is Map.merge's documented behaviour") {
    val head: java.util.function.Function[String, Char] = (s: String) => s.charAt(0)
    val id: java.util.function.Function[String, String] = (s: String) => s
    val drop: java.util.function.BinaryOperator[String] = (_: String, _: String) => null
    assertEquals(JavaCollections.toMap(ArrayBuffer("ax", "ay"), head, id, drop).toMap, Map.empty[Char, String])
  }

  // -------------------------------------------------------------------------------------------
  // java's UNTYPED PROBE — the members declared over `Object` rather than over the element type.
  // Two receivers need them: a map whose type arguments are WILDCARDS (`K` is an unnameable
  // capture), and any retyped collection reached with java's own `Object` still on the argument —
  // an implementing class's `remove(Object o)`, or the frontend's G14 erasure coercion.
  // -------------------------------------------------------------------------------------------

  test("mapGet is java's `get(Object)`: the value, or NULL when absent") {
    val m = scala.collection.mutable.Map("a" -> "x")
    assertEquals(JavaCollections.mapGet(m, "a"), "x")
    assertEquals(JavaCollections.mapGet(m, "b"), null)
  }

  test("…and a key of a DIFFERENT type simply misses, exactly as java's Object-keyed lookup does") {
    // The whole reason the key is `Any`: java's `Map<String, ?>.get(anInteger)` compiles and
    // returns null. Scala's `Map[String, ?].getOrElse(anInt, …)` does not compile at all, so the
    // port has to keep java's shape or lose calls java accepted.
    val m = scala.collection.mutable.Map("1" -> "x")
    assertEquals(JavaCollections.mapGet(m, java.lang.Integer.valueOf(1)), null)
    assertEquals(JavaCollections.mapContainsKey(m, java.lang.Integer.valueOf(1)), false)
  }

  test("mapContainsKey is java's `containsKey(Object)`") {
    val m = scala.collection.mutable.Map("a" -> "x")
    assert(JavaCollections.mapContainsKey(m, "a"))
    assert(!JavaCollections.mapContainsKey(m, "b"))
  }

  test("mapRemove RETURNS the value that was there — java's contract, not scala's `-=`") {
    val m = scala.collection.mutable.Map("a" -> "x", "b" -> "y")
    assertEquals(JavaCollections.mapRemove(m, "a"), "x")
    assertEquals(JavaCollections.mapRemove(m, "a"), null) // …and null the second time
    assertEquals(m.toMap, Map("b" -> "y"))
  }

  test("mapGet reads the LIVE map, never a snapshot") {
    val m = scala.collection.mutable.Map.empty[String, String]
    assertEquals(JavaCollections.mapGet(m, "a"), null)
    m.put("a", "x")
    assertEquals(JavaCollections.mapGet(m, "a"), "x")
  }

  test("NO `checkcast` on the probe — the whole reason this is a helper and not a cast") {
    // `o.asInstanceOf[String]` is the translation that COMPILES and means something else: it throws
    // `ClassCastException` where java's `Map<String, ?>.get(anInteger)` answers `null`. The widening
    // here is of the PROBE POSITION only and is erased, so java's own lookup runs and misses.
    // CLAUDE.md §4.4 — a green compile says nothing about this cell.
    val m = scala.collection.mutable.Map("a" -> "x")
    assertEquals(JavaCollections.mapGet(m, new Object), null)
    assertEquals(JavaCollections.mapRemove(m, new Object), null)
    assertEquals(m.toMap, Map("a" -> "x"))
  }

  test("setContains is java's `Set.contains(Object)`, in java's own equality DIRECTION") {
    val s = scala.collection.mutable.Set("a", "b")
    assert(JavaCollections.setContains(s, "a"))
    assert(!JavaCollections.setContains(s, "c"))
    // a probe of an unrelated type MISSES rather than throwing — java's contract, and the cell a
    // narrowing cast would have got wrong.
    assert(!JavaCollections.setContains(s, java.lang.Integer.valueOf(1)))
    assert(!JavaCollections.setContains(s, null))
  }

  test("setRemove answers java's BOOLEAN — which `-=`, answering the receiver, cannot") {
    val s = scala.collection.mutable.Set("a", "b")
    assert(JavaCollections.setRemove(s, "a"))
    assert(!JavaCollections.setRemove(s, "a")) // …false the second time, as java's is
    assert(!JavaCollections.setRemove(s, java.lang.Integer.valueOf(1)))
    assertEquals(s.toSet, Set("b"))
  }

  test("into builds the FACTORY's collection and fills it — the target comes from the collector") {
    val out = JavaCollections.into(ArrayBuffer(1, 2, 3), () => ListBuffer.empty[Int])
    assertEquals(out.toList, List(1, 2, 3))
    assert(out.isInstanceOf[ListBuffer[?]])
    // a fresh one per call, as `Collectors.toCollection(Factory::new)` gives.
    val a = JavaCollections.into(ArrayBuffer(1), () => ListBuffer.empty[Int])
    val b = JavaCollections.into(ArrayBuffer(2), () => ListBuffer.empty[Int])
    assert(!(a eq b))
    assertEquals(a.toList, List(1))
  }

  // -------------------------------------------------------------------------------------------
  // Reified — `ENGINE-LIMITS.md` K18. Both representations, and the LIVENESS of the coercion.
  //
  // Nothing about these is visible to a compile: every one of them is an `isInstanceOf` or an
  // `asInstanceOf` that already type-checked and simply answered java's question about the wrong
  // set of classes. So this is the only place the semantics are stated as an assertion.
  // -------------------------------------------------------------------------------------------

  test("every predicate accepts BOTH representations — the port's and the producer's") {
    assert(JavaCollections.Reified.isMap(scala.collection.mutable.Map("a" -> 1)))
    assert(JavaCollections.Reified.isMap(new java.util.HashMap[String, Int]()))
    assert(JavaCollections.Reified.isBuffer(ArrayBuffer(1)))
    assert(JavaCollections.Reified.isBuffer(new java.util.ArrayList[Int]()))
    assert(JavaCollections.Reified.isSet(scala.collection.mutable.Set(1)))
    assert(JavaCollections.Reified.isSet(new java.util.HashSet[Int]()))
    assert(JavaCollections.Reified.isIterator(new java.util.ArrayList[Int]().iterator()))
  }

  test("a MAP is not a COLLECTION — the loose widening to scala.collection.Iterable is WRONG") {
    // The measured difference: a `mutable.Map` IS a `scala.collection.Iterable` while a
    // `java.util.Map` is NOT a `java.util.Collection`, so widening the scala side turns
    // `x instanceof Collection` true for a map — two liqp test failures worse (K18).
    assert(!JavaCollections.Reified.isCollection(scala.collection.mutable.Map("a" -> 1)))
    assert(!JavaCollections.Reified.isCollection(new java.util.HashMap[String, Int]()))
    assert(!JavaCollections.Reified.isIterable(scala.collection.mutable.Map("a" -> 1)))
  }

  test("isCollection reaches the mapped SUBTYPES' targets — the shim does not inherit java's relation") {
    // `java.util.List <: java.util.Collection` in java; `mutable.Buffer` is NOT a `JavaCollection`,
    // because the shim exists so a class can EXTEND `AbstractCollection` (CLAUDE.md §4.5). Without
    // this the port's own lists answer NO to a test java answered YES to.
    assert(JavaCollections.Reified.isCollection(ArrayBuffer(1)))
    assert(JavaCollections.Reified.isCollection(scala.collection.mutable.Set(1)))
    assert(JavaCollections.Reified.isCollection(new java.util.ArrayList[Int]()))
    assert(JavaCollections.Reified.isCollection(JavaCollection.from(ArrayBuffer(1))))
    assert(JavaCollections.Reified.isIterable(ArrayBuffer(1)))
  }

  test("nothing unrelated is accepted — a predicate that says yes to everything measures nothing") {
    assert(!JavaCollections.Reified.isMap("s"))
    assert(!JavaCollections.Reified.isBuffer("s"))
    assert(!JavaCollections.Reified.isSet(ArrayBuffer(1)))
    assert(!JavaCollections.Reified.isBuffer(scala.collection.mutable.Set(1)))
    assert(!JavaCollections.Reified.isCollection("s"))
    assert(!JavaCollections.Reified.isMap(null))
  }

  test("the coercion of a JAVA value is a LIVE view, not a copy") {
    // The reason the whole family is a view: the producer may still hold the collection, and a copy
    // would detach every later change in both directions.
    val jm = new java.util.HashMap[String, Int]()
    jm.put("a", 1)
    val sm = JavaCollections.Reified.asMap(jm).asInstanceOf[scala.collection.mutable.Map[String, Int]]
    assertEquals(sm("a"), 1)
    sm.put("b", 2)
    assertEquals(jm.get("b"), 2)
    jm.put("c", 3)
    assertEquals(sm("c"), 3)
  }

  test("…and the coercion of a value the PORT made is the identity") {
    val own: scala.collection.mutable.Map[String, Int] = scala.collection.mutable.Map("a" -> 1)
    assert(JavaCollections.Reified.asMap(own) eq own)
    val xs: Buffer[Int] = ArrayBuffer(1)
    assert(JavaCollections.Reified.asBuffer(xs) eq xs)
  }

  test("asCollection reaches the shim from EITHER side, and the java side is live too") {
    val jl = new java.util.ArrayList[Int]()
    jl.add(1)
    val c = JavaCollections.Reified.asCollection(jl).asInstanceOf[JavaCollection[Int]]
    assertEquals(c.size(), 1)
    c.add(2)
    assertEquals(jl.size(), 2)          // live, not a copy
    jl.add(3)
    assertEquals(c.size(), 3)
    // …and from the port's own buffer, through the factory that already existed.
    val xs: Buffer[Int] = ArrayBuffer(9)
    val c2 = JavaCollections.Reified.asCollection(xs).asInstanceOf[JavaCollection[Int]]
    c2.add(8)
    assertEquals(xs.toList, List(9, 8))
  }

  // -------------------------------------------------------------------------------------------
  // …AND THE COERCION'S OWN RESULT IS ASKED ABOUT AGAIN. `ENGINE-LIMITS.md` K19.
  //
  // A coercion at a shim target has to BUILD something — `mutable.Buffer` is not a
  // `JavaCollection` and no view can make it one — so the value that leaves `as*` is a different
  // OBJECT from the one that arrived. Java's cast was the identity, so every later reified
  // question about that value was still about the ORIGINAL class: `(Collection) list` then
  // `instanceof List` is TRUE in java, and answered on the wrapper alone it is false.
  //
  // The fixable half is exactly this chain, and the fix is that the shims say what they DELEGATE
  // to (`Wrapping`), so a later question can be asked of the value underneath. What no
  // implementation can fix is reference IDENTITY — see K19.
  // -------------------------------------------------------------------------------------------

  test("WRAP-THEN-RETEST: a coerced value still answers for what it was made of") {
    val xs: Buffer[Int] = ArrayBuffer(1)
    val c = JavaCollections.Reified.asCollection(xs)
    // java: `(Collection) list` leaves an ArrayList, so all three of these are TRUE
    assert(JavaCollections.Reified.isCollection(c))
    assert(JavaCollections.Reified.isBuffer(c), "the wrapper is still the buffer java would see")
    assert(JavaCollections.Reified.isIterable(c))
    assert(!JavaCollections.Reified.isSet(c), "and not something it never was")
  }

  test("…from the JAVA side too: the wrapper delegates to java's own collection") {
    val jl = new java.util.ArrayList[Int]()
    jl.add(1)
    val c = JavaCollections.Reified.asCollection(jl)
    assert(JavaCollections.Reified.isBuffer(c), "java's ArrayList is a List whatever we wrapped it in")
    val it = JavaCollections.Reified.asIterator(jl.iterator())
    assert(JavaCollections.Reified.isIterator(it))
  }

  test("…and the SECOND coercion hands back the value, not a cast that throws") {
    // The chain that made this a defect rather than an inaccuracy: `(Collection) x` then
    // `(List) x` is two casts of one object in java. With the wrapper opaque, the second one
    // reached `asInstanceOf[Buffer]` on a `JavaCollection` and threw.
    val xs: Buffer[Int] = ArrayBuffer(1)
    val c = JavaCollections.Reified.asCollection(xs)
    assert(JavaCollections.Reified.asBuffer(c) eq xs)
    val jl = new java.util.ArrayList[Int]()
    jl.add(7)
    val cj = JavaCollections.Reified.asCollection(jl)
    assertEquals(JavaCollections.Reified.asBuffer(cj).toList, List(7))
  }

  test("an UNMODIFIABLE wrapper does NOT unwrap — java's view is not the collection it guards") {
    // The line the unwrapping must not cross. `Collections.unmodifiableList(l) instanceof List` is
    // true in java, and casting it back yields the UNMODIFIABLE VIEW — never the mutable original.
    // A shim that reported its underlying here would hand a caller the very buffer the wrapper
    // exists to protect, which is a silent write-through and not a reified question at all.
    val xs: Buffer[Int] = ArrayBuffer(1)
    val u = JavaCollection.unmodifiableFrom(xs)
    assert(JavaCollections.Reified.isCollection(u))
    assert(!JavaCollections.Reified.isBuffer(u))
    intercept[UnsupportedOperationException](u.add(2))
  }

  test("asBuffer / asSet are live views of java's own collections") {
    val jl = new java.util.ArrayList[Int]()
    jl.add(1)
    val b = JavaCollections.Reified.asBuffer(jl).asInstanceOf[Buffer[Int]]
    b += 2
    assertEquals(jl.size(), 2)
    val js = new java.util.HashSet[Int]()
    js.add(1)
    val s = JavaCollections.Reified.asSet(js).asInstanceOf[scala.collection.mutable.Set[Int]]
    s += 2
    assertEquals(js.size(), 2)
  }

  // -------------------------------------------------------------------------
  // THE EGRESS DIRECTION — `ENGINE-LIMITS.md` K21 face 1
  // -------------------------------------------------------------------------

  private def jv(x: Any) = JavaCollections.Reified.toJavaValue(x)

  test("a retyped MAP leaves as java's own — which is what a reflective consumer reads") {
    val m = scala.collection.mutable.HashMap[String, Any]("key" -> "value")
    val out = jv(m).asInstanceOf[java.util.Map[Any, Any]]
    assertEquals(out.get("key"), "value": Any)
    assertEquals(out.size(), 1)
    assertEquals(out.entrySet().iterator().next().getKey, "key": Any)
  }

  test("…DEEP: `toJava` converts one level and a serialiser walks the whole tree") {
    val m = scala.collection.mutable.HashMap[String, Any](
      "in" -> scala.collection.mutable.HashMap[String, Any]("k" -> 1))
    val out = jv(m).asInstanceOf[java.util.Map[Any, Any]]
    assert(out.get("in").isInstanceOf[java.util.Map[?, ?]],
           "one level is exactly the refusal `coerce` records for a nested element type")
    assertEquals(out.get("in").asInstanceOf[java.util.Map[Any, Any]].get("k"), 1: Any)
  }

  test("a BUFFER and a SET leave as java's, elements converted on read") {
    val b = scala.collection.mutable.Buffer[Any](scala.collection.mutable.HashMap("k" -> 1))
    val jl = jv(b).asInstanceOf[java.util.List[Any]]
    assertEquals(jl.size(), 1)
    assert(jl.get(0).isInstanceOf[java.util.Map[?, ?]])
    val s = scala.collection.mutable.Set[Any](scala.collection.mutable.Buffer[Any](1))
    val js = jv(s).asInstanceOf[java.util.Set[Any]]
    assertEquals(js.size(), 1)
    assert(js.iterator().next().isInstanceOf[java.util.List[?]])
  }

  test("a MAP is not converted as an ITERABLE of pairs — the order `isCollection` is exact about") {
    val out = jv(scala.collection.mutable.HashMap("k" -> 1))
    assert(out.isInstanceOf[java.util.Map[?, ?]], clue(out.getClass.getName))
    assert(!out.isInstanceOf[java.util.Collection[?]],
           "a scala Map IS a scala Iterable, and java's Map is not a Collection at all")
  }

  test("IDENTITY for everything this engine did not put there") {
    val s: Any = "x"
    val jl = new java.util.ArrayList[Int]()
    val o  = new Object
    assert(jv(s) eq s.asInstanceOf[AnyRef])
    assert(jv(jl) eq jl, "a java collection the port never touched leaves as itself")
    assert(jv(o) eq o)
    assertEquals(jv(null), null)
  }

  test("an ARRAY keeps its identity when nothing inside it moved") {
    val untouched: Array[AnyRef] = Array("a", "b")
    assert(jv(untouched) eq untouched, "the spine has to be copied to convert it, so it is copied " +
      "only when an element actually moves")
    val moved: Array[AnyRef] = Array(scala.collection.mutable.HashMap("k" -> 1))
    val out = jv(moved).asInstanceOf[Array[AnyRef]]
    assert(out ne moved)
    assert(out(0).isInstanceOf[java.util.Map[?, ?]])
  }

  test("…and where an element DID move, the copy is DETACHED — stated, because it is not the view") {
    // The identity note's second half, asserted rather than left to read as the identity its first
    // half promises. An array has no view to build (java's `[]` is a bytecode instruction, not an
    // interface), so a converted array is a COPY: a write through it reaches nothing, which is not
    // even the read-only compromise the collection arms make — that one THROWS, this one accepts
    // the write and silently loses it.
    val moved: Array[AnyRef] = Array(scala.collection.mutable.HashMap("k" -> 1))
    val out = jv(moved).asInstanceOf[Array[AnyRef]]
    out(0) = "written"
    assert(moved(0).isInstanceOf[scala.collection.mutable.Map[?, ?]],
           "the port's own array does not see it — the detachment K15 refuses elsewhere")
  }

  test("a SELF-REFERENTIAL array terminates — the one arm with no view's laziness to stop it") {
    // `Object[] a = new Object[1]; a[0] = a;` is legal java, and this arm is EAGER. Every other arm
    // is a lazy view that re-enters per read and terminates by construction; only the array chain
    // needs a guard, and an array already on the path is returned AS IT ARRIVED because no detached
    // copy of a cyclic array exists.
    val a: Array[AnyRef] = new Array[AnyRef](2)
    a(0) = a
    a(1) = scala.collection.mutable.HashMap("k" -> 1)
    val out = jv(a).asInstanceOf[Array[AnyRef]]
    assert(out ne a, "the map moved, so the spine was copied")
    assert(out(0) eq a, "the cycle closes on the array as it arrived")
    assert(out(1).isInstanceOf[java.util.Map[?, ?]])

    // …and a MUTUAL cycle, which a one-element guard would not catch.
    val x: Array[AnyRef] = new Array[AnyRef](1)
    val y: Array[AnyRef] = new Array[AnyRef](1)
    x(0) = y; y(0) = x
    assert(jv(x) eq x, "nothing inside moved, so the original comes back")
  }

  test("a DELEGATING shim leaves as what it wraps, not as a bean around it") {
    val b = scala.collection.mutable.Buffer[Any](1, 2)
    val out = jv(JavaCollection.from(b))
    assert(out.isInstanceOf[java.util.Collection[?]], clue(out.getClass.getName))
    assertEquals(out.asInstanceOf[java.util.Collection[Any]].size(), 2)
  }

  test("optionalOrElse evaluates its default EXACTLY ONCE, and whatever the optional holds") {
    // `java.util.Optional.orElse(T other)` takes a VALUE. Java evaluates the argument expression
    // before the call, so a side-effecting default runs even on a PRESENT optional; scala's
    // `Option.getOrElse` takes it by name and runs it only on an empty one. Both directions are
    // asserted, because a helper that fixed one and not the other would still be a §4.4 defect.
    var runs = 0
    def d(): Int = { runs += 1; 7 }
    assertEquals(JavaCollections.optionalOrElse(Some(1), d()), 1)
    assertEquals(runs, 1, "java evaluates the default at a PRESENT optional too")
    assertEquals(JavaCollections.optionalOrElse(None, d()), 7)
    assertEquals(runs, 2, "…and exactly once at an empty one")
  }

  test("the view is READ-ONLY — a write java would have made is LOUD, never silent") {
    val out = jv(scala.collection.mutable.HashMap("k" -> 1)).asInstanceOf[java.util.Map[Any, Any]]
    intercept[UnsupportedOperationException](out.put("j", 2))
    val jl = jv(scala.collection.mutable.Buffer[Any](1)).asInstanceOf[java.util.List[Any]]
    intercept[UnsupportedOperationException](jl.add(2))
  }

  // -------------------------------------------------------------------------------------------
  // SE8's default methods on List / Map / Collection (`ENGINE-LIMITS.md` K23)
  //
  // Each of these has a scala member that LOOKS right, and each test asserts the cell where the two
  // answer differently — never the cell where they agree, which the wrong implementation would pass.
  // -------------------------------------------------------------------------------------------

  test("computeIfAbsent treats a key mapped to NULL as ABSENT, where getOrElseUpdate does not") {
    // java's own words: "if the specified key is not already associated with a value (or is mapped
    // to null)". `getOrElseUpdate` hands the `null` straight back and never runs the factory.
    val m = scala.collection.mutable.HashMap[String, String]("k" -> null)
    val f: java.util.function.Function[String, String] = (k: String) => "made:" + k
    assertEquals(JavaCollections.computeIfAbsent(m, "k", f), "made:k")
    assertEquals(m("k"), "made:k")
  }

  test("computeIfAbsent RECORDS NOTHING when the factory answers null — the next call re-runs it") {
    // "If the mapping function returns null, no mapping is recorded." `getOrElseUpdate` stores it,
    // so java re-runs the factory on the next call and the port would not.
    val m     = scala.collection.mutable.HashMap[String, String]()
    var calls = 0
    val f: java.util.function.Function[String, String] = (_: String) => { calls += 1; null }
    assertEquals(JavaCollections.computeIfAbsent(m, "k", f), null)
    assert(!m.contains("k"), "java records no mapping for a null result")
    JavaCollections.computeIfAbsent(m, "k", f)
    assertEquals(calls, 2, "…so the factory runs again")
  }

  test("computeIfAbsent returns the EXISTING value and does not run the factory") {
    val m     = scala.collection.mutable.HashMap("k" -> "old")
    var calls = 0
    val f: java.util.function.Function[String, String] = (_: String) => { calls += 1; "new" }
    assertEquals(JavaCollections.computeIfAbsent(m, "k", f), "old")
    assertEquals(calls, 0)
    assertEquals(m("k"), "old")
  }

  test("removeIf removes what the predicate ACCEPTS — the complement of filterInPlace — and says whether it did") {
    val xs: Buffer[String] = ArrayBuffer("a", "", "b", "")
    val empty: java.util.function.Predicate[String] = (s: String) => s.isEmpty
    assertEquals(JavaCollections.removeIf(xs, empty), true)
    assertEquals(xs.toList, List("a", "b"))
    // …and FALSE where nothing matched, which is what callers branch on (`-=` and `filterInPlace`
    // both return the collection, so a mapping onto either loses this).
    assertEquals(JavaCollections.removeIf(xs, empty), false)
    assertEquals(xs.toList, List("a", "b"))
  }

  test("removeIf identifies an element by POSITION, so an equals that ignores the field still removes the right one") {
    // flexmark's own shape: `trackedOffsets.removeIf(it -> it.getOffset() == n)` over a type whose
    // `equals` is not the offset. A by-value route (`-=`) would remove the FIRST equal element.
    final class Off(val n: Int) { override def equals(o: Any): Boolean = o.isInstanceOf[Off]
                                  override def hashCode(): Int        = 1 }
    val a, b = new Off(1)
    val c    = new Off(2)
    val xs: Buffer[Off] = ArrayBuffer(a, b, c)
    val two: java.util.function.Predicate[Off] = (o: Off) => o.n == 2
    assertEquals(JavaCollections.removeIf(xs, two), true)
    assert(xs.toList.map(_.n) == List(1, 1))
    assert((xs(0) eq a) && (xs(1) eq b), "the elements that stayed are the ones the predicate rejected")
  }

  test("removeIfSet is the SET spelling — a second name, because the two erase alike") {
    val xs = scala.collection.mutable.HashSet("a", "", "b")
    val empty: java.util.function.Predicate[String] = (s: String) => s.isEmpty
    assertEquals(JavaCollections.removeIfSet(xs, empty), true)
    assertEquals(xs.toList.sorted, List("a", "b"))
    assertEquals(JavaCollections.removeIfSet(xs, empty), false)
  }

  test("containsValue asks the PROBE's equals, as HashMap.containsValue does") {
    // The direction is the whole reason this is a helper: `exists(_._2 == v)` asks the STORED
    // value's `equals`, and the two agree for every symmetric one and for nothing else.
    class Probe extends AnyRef { override def equals(o: Any): Boolean = true }
    class Stored extends AnyRef { override def equals(o: Any): Boolean = false }
    val m = scala.collection.mutable.HashMap[String, AnyRef]("k" -> new Stored)
    assertEquals(JavaCollections.containsValue(m, new Probe), true, "probe.equals(stored) is what java asks")
    assertEquals(m.exists(_._2 == new Probe), false, "…and the stored value's equals answers the other way")
    // …and java's null arm: identity first, so a stored null is found by a null probe.
    val n = scala.collection.mutable.HashMap[String, AnyRef]("k" -> null)
    assertEquals(JavaCollections.containsValue(n, null), true)
    assertEquals(JavaCollections.containsValue(n, "x"), false)
  }

  test("containsAll is java's default — every element of the argument, and TRUE on an empty one") {
    val xs: Buffer[String] = ArrayBuffer("a", "b", "c")
    assertEquals(JavaCollections.containsAll(xs, List("a", "c")), true)
    assertEquals(JavaCollections.containsAll(xs, List("a", "z")), false)
    assertEquals(JavaCollections.containsAll(xs, Nil), true, "java's loop over an empty collection returns true")
    // duplicates in the argument are not a multiset question in java either — `contains` per element.
    assertEquals(JavaCollections.containsAll(xs, List("a", "a")), true)
  }

  test("ensureCapacity changes NOTHING a program can read, on either buffer kind") {
    // The one member here whose java behaviour is unobservable: a capacity hint. What must hold is
    // that it moves no element and changes no size — including on a buffer that has no capacity to
    // reserve, where doing nothing is exact rather than approximate.
    val ab = ArrayBuffer("a", "b")
    JavaCollections.ensureCapacity(ab, 64)
    assertEquals(ab.toList, List("a", "b"))
    val lb: Buffer[String] = ListBuffer("a", "b")
    JavaCollections.ensureCapacity(lb, 64)
    assertEquals(lb.toList, List("a", "b"))
  }

  test("spliterator reports JAVA'S OWN characteristics — the cell K23's refusal was about") {
    // The whole content of that fix, and the one thing no compile can check.
    // `buf.asJava.spliterator()` — the near miss the refusal rested on — reports NEITHER `ORDERED`
    // nor `SIZED` where the `ArrayList` java held reports both, so a consumer reading
    // `characteristics()` gets a different answer silently (CLAUDE.md §4.4). These assert the answer
    // java's OWN defaults give, which is what the two helpers reproduce.
    val ordered = JavaCollections.orderedSpliterator(ArrayBuffer("a", "b", "c"))
    assert(ordered.hasCharacteristics(java.util.Spliterator.ORDERED), "List.spliterator() passes ORDERED")
    assert(ordered.hasCharacteristics(java.util.Spliterator.SIZED),
           "…and `Spliterators.spliterator(Collection, int)` ORs in SIZED — the half `asJava` loses")
    assert(ordered.hasCharacteristics(java.util.Spliterator.SUBSIZED))
    assertEquals(ordered.estimateSize(), 3L)

    val distinct = JavaCollections.distinctSpliterator(scala.collection.mutable.Set("a", "b"))
    assert(distinct.hasCharacteristics(java.util.Spliterator.DISTINCT), "Set.spliterator() passes DISTINCT")
    assert(distinct.hasCharacteristics(java.util.Spliterator.SIZED))
    // …and NOT ORDERED, which is the difference between the two helpers and the reason there are two
    // names rather than one taking a characteristics constant.
    assert(!distinct.hasCharacteristics(java.util.Spliterator.ORDERED))
  }

  test("…and it TRAVERSES the collection, in the collection's own order") {
    // characteristics are a CLAIM about the traversal; this is the traversal. A spliterator that
    // reported ORDERED and handed back nothing would pass the test above.
    val seen = ArrayBuffer.empty[String]
    JavaCollections.orderedSpliterator(ArrayBuffer("a", "b", "c"))
      .forEachRemaining((s: String) => { seen += s; () })
    assertEquals(seen.toList, List("a", "b", "c"))
  }

  test("MEASURED: `asJava.spliterator()` agrees today — K23's recorded NEAR MISS does not reproduce") {
    // K23 refused `spliterator` and its stated evidence was that `buf.asJava.spliterator()` reports
    // NEITHER `ORDERED` nor `SIZED` where the `ArrayList` java held reports both. On scala 3.8.4 and
    // this JDK that is FALSE: the converter hands back a `java.util.List` wrapper whose
    // `spliterator()` is `List`'s own default, so it reports ORDERED, SIZED and SUBSIZED — exactly
    // what the two helpers above produce, characteristics `16464` either way.
    //
    // So the refusal rested on a measurement that no longer holds, and the honest record is this
    // assertion rather than the prose. It is pinned in the OTHER direction from the test it
    // replaces: if a future converter stopped agreeing, this says so, and the reason to state
    // java's answer rather than inherit it becomes the loud one instead of the quiet one.
    //
    // Why the helpers stay anyway: they make the characteristics follow JAVA'S DECLARATION at the
    // owner the receiver was typed by, which is a fact a reader can check against the JDK source,
    // instead of following what scala's converter happens to wrap the collection in. That is the
    // same argument §4.5 makes for a standalone shim over an inherited one, and it is deliberately
    // NOT the argument the refusal made.
    import scala.jdk.CollectionConverters.*
    val viaAsJava = ArrayBuffer("a", "b", "c").asJava.spliterator()
    assert(viaAsJava.hasCharacteristics(java.util.Spliterator.ORDERED),
           "the converter's wrapper DOES report ORDERED — K23's near miss is not reproducible")
    assert(viaAsJava.hasCharacteristics(java.util.Spliterator.SIZED),
           "…and SIZED")
    assertEquals(viaAsJava.characteristics(),
                 JavaCollections.orderedSpliterator(ArrayBuffer("a", "b", "c")).characteristics(),
                 "and it agrees with the helper exactly, which is what makes this a measurement " +
                 "about the REASON rather than about the answer")
  }

  test("sort is what `List.sort(cmp)` needs too — in place, stable, and on a non-indexed Buffer") {
    // SE8 made `Collections.sort(list, c)` delegate to `list.sort(c)`, so ONE helper is correct for
    // both by java's own definition — which is why the member arm reaches this and not a second one.
    val xs: Buffer[(Int, String)] = ListBuffer((2, "a"), (1, "b"), (2, "c"), (1, "d"))
    val byFirst: java.util.Comparator[(Int, String)] = (a, b) => a._1 - b._1
    JavaCollections.sort(xs, byFirst)
    assertEquals(xs.toList, List((1, "b"), (1, "d"), (2, "a"), (2, "c")), "stable: ties keep their order")
  }
