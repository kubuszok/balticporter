package sge.vfx.utils

/** `ValueArrayMap` is the port's `java.util.Map` boundary: a `HashMap` for lookup beside a libGDX
  * `Array` for ordering, kept in step by hand. `CollectionsTransform` retypes the map onto
  * `scala.collection.mutable.Map`, which is a DIFFERENT API with the same names — `get` returns an
  * `Option`, `put` returns the previous value as one, `remove` likewise — so every method here is a
  * rewrite, and a rewrite that returns the wrong thing compiles perfectly.
  *
  * The reference hand port does not have this class at all (`../sge/sge-extension/vfx` dropped it
  * with the pool it served), so there is no second opinion to compare against: these expectations
  * come from the upstream java.
  */
class ValueArrayMapSuite extends munit.FunSuite {

  private def of(pairs: (String, String)*): ValueArrayMap[String, String] = {
    val m = new ValueArrayMap[String, String]()
    pairs.foreach((k, v) => m.put(k, v))
    m

  }
  test("put then get round-trips, and a MISS is null — not an empty Option") {
    // java's `Map.get` returns null on a miss; the retyped `getOrElse(k, null)` must too. An
    // `Option` leaking out here would be a different type and a different contract.
    val m = of("a" -> "1")
    assertEquals(m.get("a"), "1")
    assertEquals(m.get("absent"), null)
  }

  test("values keep INSERTION order and are addressable by index") {
    val m = of("a" -> "1", "b" -> "2", "c" -> "3")
    assertEquals(m.size(), 3)
    assertEquals(m.getValueAt(0), "1")
    assertEquals(m.getValueAt(2), "3")
  }

  test("remove RETURNS the value that was there, and drops it from the ordered values too") {
    // `Map.remove` returns the previous value in java; scala's returns an `Option`. The port's
    // `.getOrElse(null)` is what keeps the java contract, and `values.removeValue` is what keeps
    // the two halves in step — a class that removed from the map alone would still `size()` right
    // and hand back a stale value from `getValueAt`.
    val m = of("a" -> "1", "b" -> "2")
    assertEquals(m.remove("a"), "1")
    assertEquals(m.size(), 1)
    assertEquals(m.getValueAt(0), "2")
    assertEquals(m.get("a"), null)
  }

  test("removing an ABSENT key returns null and touches nothing") {
    val m = of("a" -> "1")
    assertEquals(m.remove("zzz"), null)
    assertEquals(m.size(), 1)
  }

  test("findKey compares by IDENTITY — java's `entry.getValue() == value`") {
    // CLAUDE.md §4.4's first row, in the direction that is easy to get wrong: `==` on references
    // in java is `eq` in scala, and translating it to `==` would find an EQUAL-but-distinct value.
    val shared = new String("v".toCharArray)
    val equalButOther = new String("v".toCharArray)
    assert(shared != equalButOther || (shared ne equalButOther)) // distinct objects, equal content
    val m = new ValueArrayMap[String, String]()
    m.put("k", shared)
    assertEquals(m.findKey(shared), "k")
    assertEquals(m.findKey(equalButOther), null)
  }

  test("removeByValue goes through findKey, so it too is identity-based") {
    val shared = new String("v".toCharArray)
    val m = new ValueArrayMap[String, String]()
    m.put("k", shared)
    assertEquals(m.removeByValue(shared), shared)
    assertEquals(m.size(), 0)
  }

  test("contains is the map's `containsKey` — EQUALITY, not identity") {
    val m = of("hello" -> "1")
    assert(m.contains(new String("hello".toCharArray)))
    assert(!m.contains("nope"))
  }

  test("getKeys returns the SHARED scratch array — upstream says so, and callers rely on it") {
    // "Warning: returned array will be reused!" — two calls hand back the same instance, cleared
    // and refilled. A port that allocated a fresh array each time would be tidier and would change
    // an aliasing contract nothing else can see.
    val m = of("a" -> "1", "b" -> "2")
    val first  = m.getKeys()
    val second = m.getKeys()
    assert(first eq second)
    assertEquals(second.size, 2)
  }

  test("sort reorders the VALUES view and leaves lookup intact") {
    val m = of("a" -> "3", "b" -> "1", "c" -> "2")
    m.sort((l: String, r: String) => l.compareTo(r))
    assertEquals(m.getValueAt(0), "1")
    assertEquals(m.getValueAt(2), "3")
    assertEquals(m.get("a"), "3")
  }

  test("clear empties both halves") {
    val m = of("a" -> "1", "b" -> "2")
    m.clear()
    assertEquals(m.size(), 0)
    assertEquals(m.get("a"), null)
    assertEquals(m.getKeys().size, 0)
  }

  test("a capacity-constructed map behaves identically to a default one") {
    // the capacity reaches `new HashMap<>(n)`, which scala has no one-argument constructor for.
    val m = new ValueArrayMap[String, String](2)
    (1 to 32).foreach(i => m.put("k" + i, "v" + i))
    assertEquals(m.size(), 32)
    assertEquals(m.get("k17"), "v17")
    assertEquals(m.getValueAt(0), "v1")
  }

}