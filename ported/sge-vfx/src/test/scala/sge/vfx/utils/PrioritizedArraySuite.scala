package sge.vfx.utils

/** gdx-vfx ships NO test suite — `@Test` over its whole upstream checkout is 0 — so this file and
  * its siblings are the port's ONLY behavioural evidence (CLAUDE.md §3: a green compile says
  * nothing about behaviour). They are hand-written, they live in `src/` because `src_managed/` is a
  * build product (§5.5), and every expectation below is read off the UPSTREAM JAVA rather than off
  * the emitted Scala — a test written from the output can only ever confirm the output.
  *
  * `PrioritizedArray` is the port's densest §4.4 target. It is the class the engine spent four
  * compile errors on, and three of its behaviours translate to valid Scala meaning something else
  * if any of them is got wrong:
  *
  *   - the iterator advances with `index++` USED AS A VALUE (`getValueAt(index++)`), which yields
  *     the value BEFORE the update — the off-by-one that ships silently;
  *   - `add` routes the wrapper through a RAW static `Pool<Wrapper>`, whose result java converts
  *     unchecked into `Wrapper<T>`;
  *   - `remove(int)` and `remove(T)` are two java OVERLOADS with opposite meanings, and a
  *     `PrioritizedArray[Integer]` is where scala's own numeric conversions could silently pick the
  *     wrong one.
  *
  * The reference hand port is NOT a model here: `../sge/sge-extension/vfx` eliminated the pool, the
  * `ValueArrayMap` and the dual iterator outright, so it has nothing to say about any of the three
  * (CLAUDE.md §3.5 — a skip is not a solution).
  */
class PrioritizedArraySuite extends munit.FunSuite {

  private def of(pairs: (String, Int)*): PrioritizedArray[String] = {
    val a = new PrioritizedArray[String]()
    pairs.foreach((item, p) => a.add(item, p))
    a

  }
  private def items(a: PrioritizedArray[String]): List[String] = {
    (0 until a.size).map(a.get).toList

  }
  test("items are ordered by ASCENDING priority, whatever order they arrive in") {
    assertEquals(items(of("low" -> 10, "high" -> 1, "mid" -> 5)), List("high", "mid", "low"))
  }

  test("add without a priority uses 0 — the one-argument overload delegates") {
    val a = new PrioritizedArray[String]()
    a.add("first")
    a.add("second")
    assertEquals(a.size, 2)
    assertEquals(items(a), List("first", "second"))
  }

  test("the sort is STABLE: equal priorities keep insertion order") {
    // `Array.sort(Comparator)` is a stable sort in libGDX, and the comparator only sees `priority`,
    // so anything else would reorder equals. Pinned because an unstable sort is invisible until a
    // caller depends on registration order — which is what a priority list is FOR.
    assertEquals(items(of("a" -> 0, "b" -> 0, "c" -> 0)), List("a", "b", "c"))
  }

  test("setPriority re-sorts in place") {
    val a = of("movable" -> 10, "fixed" -> 5)
    assertEquals(a.get(0), "fixed")
    a.setPriority("movable", 1)
    assertEquals(items(a), List("movable", "fixed"))
  }

  test("remove(T) removes BY VALUE and leaves the rest ordered") {
    val a = of("a" -> 1, "b" -> 2, "c" -> 3)
    a.remove("b")
    assertEquals(items(a), List("a", "c"))
    assertEquals(a.size, 2)
  }

  test("remove(T) on an absent item is a no-op, not a throw") {
    // java: `items.remove(key)` returns null and the `if (wrapper != null)` guard skips the free.
    val a = of("a" -> 1)
    a.remove("nope")
    assertEquals(items(a), List("a"))
  }

  test("remove(int) removes BY INDEX — the overload java resolved, on a numeric element type") {
    // CLAUDE.md §4.4's `list.remove(anInteger)` trap, in this library's own API: with `T = Integer`
    // both overloads accept an `Int`-shaped argument in scala, and only the STATIC type of the
    // argument separates them. `remove(1: Int)` must delete the element AT index 1.
    val a = new PrioritizedArray[java.lang.Integer]()
    a.add(java.lang.Integer.valueOf(10), 0)
    a.add(java.lang.Integer.valueOf(11), 1)
    a.add(java.lang.Integer.valueOf(12), 2)
    a.remove(1)
    assertEquals(a.size, 2)
    assertEquals(a.get(0), java.lang.Integer.valueOf(10))
    assertEquals(a.get(1), java.lang.Integer.valueOf(12))
  }

  test("remove(T) on the SAME numeric element type removes by value, not by index") {
    val a = new PrioritizedArray[java.lang.Integer]()
    a.add(java.lang.Integer.valueOf(10), 0)
    a.add(java.lang.Integer.valueOf(11), 1)
    a.add(java.lang.Integer.valueOf(12), 2)
    a.remove(java.lang.Integer.valueOf(11))
    assertEquals(a.size, 2)
    assertEquals(a.get(0), java.lang.Integer.valueOf(10))
    assertEquals(a.get(1), java.lang.Integer.valueOf(12))
  }

  test("contains follows the backing map — equal keys, not identical ones") {
    // `ValueArrayMap.contains` is `map.containsKey`, so it is java's `equals`. Stated because the
    // reference hand port made it IDENTITY (`eq`) and that is a different function; upstream's is
    // what this port must reproduce.
    val a = of("hello" -> 1)
    assert(a.contains("hello"))
    assert(a.contains(new String("hello".toCharArray)))
    assert(!a.contains("world"))
  }

  test("clear empties it and leaves it usable") {
    val a = of("a" -> 1, "b" -> 2)
    a.clear()
    assertEquals(a.size, 0)
    a.add("c", 1)
    assertEquals(items(a), List("c"))
  }

  test("the ITERATOR yields every element once, in priority order — `index++` is post-increment") {
    // `next()` is `array.items.getValueAt(index++).item`. Read as a pre-increment it skips the
    // first element and runs off the end; read as a post-increment it is what java does. Neither
    // reading moves a compile-error count (CLAUDE.md §4.4).
    val a  = of("low" -> 10, "high" -> 1, "mid" -> 5)
    val it = a.iterator()
    val seen = List.newBuilder[String]
    while (it.hasNext()) seen += it.next()
    assertEquals(seen.result(), List("high", "mid", "low"))
  }

  test("a `for` over it sees the same sequence — the JavaIterable extension, not a second protocol") {
    val a = of("low" -> 10, "high" -> 1)
    val seen = List.newBuilder[String]
    for x <- a do seen += x
    assertEquals(seen.result(), List("high", "low"))
  }

  test("the iterator is REUSED, and a second call resets it to the start") {
    // upstream: "the same iterator instance is returned each time this method is called". Two
    // consecutive full walks must both see everything — the alternating iterator1/iterator2 dance
    // exists precisely to make that true.
    val a = of("a" -> 1, "b" -> 2)
    def walk(): List[String] = {
      val it = a.iterator()
      val b  = List.newBuilder[String]
      while (it.hasNext()) b += it.next()
      b.result()
    }
    assertEquals(walk(), List("a", "b"))
    assertEquals(walk(), List("a", "b"))
  }

  test("an exhausted iterator throws NoSuchElementException rather than returning null") {
    val a  = of("only" -> 1)
    val it = a.iterator()
    assertEquals(it.next(), "only")
    intercept[java.util.NoSuchElementException](it.next())
  }

  test("the constructor's capacity argument does not change observable behaviour") {
    // the capacity reaches `new HashMap<>(capacity)`, which has no one-argument scala constructor —
    // the port supplies java's own default load factor. A wrong load factor is not a compile error.
    val a = new PrioritizedArray[String](1)
    (1 to 40).foreach(i => a.add("item" + i, 41 - i))
    assertEquals(a.size, 40)
    assertEquals(a.get(0), "item40")
    assertEquals(a.get(39), "item1")
  }

  test("toString and toString(separator) delegate to the wrapper's `item[priority]` form") {
    val a = of("x" -> 2, "y" -> 1)
    assertEquals(a.toString(), "[y[1], x[2]]")
    assertEquals(a.toString(" | "), "y[1] | x[2]")
  }

}