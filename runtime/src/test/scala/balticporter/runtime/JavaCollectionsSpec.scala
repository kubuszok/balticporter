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

  test("asList does NOT alias an array — the aliasing form never reaches here (asListArgs refuses it)") {
    val arr = Array("a", "b")
    val xs  = JavaCollections.asList(arr*)
    arr(0) = "changed"
    assertEquals(xs.toList, List("a", "b"))
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
