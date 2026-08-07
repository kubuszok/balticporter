package balticporter.runtime

/** `JavaEnumMap` / `JavaEnumSet` — the ORDINAL-ORDER guarantee, which is the whole reason they are
  * shims rather than a mapping onto `mutable.HashMap`/`HashSet`.
  *
  * Availability is not what these tests are about; a stdlib map would have supplied that. What no
  * stdlib map supplies is java's documented iteration order, and an order regression has no compile
  * error, no check count and no other test — so the assertions here are all about ORDER, and each
  * one is written against a set INSERTED in the wrong order so that "it happens to come out right"
  * cannot pass it.
  */

/** A java-shaped enum — `extends java.lang.Enum` is what the shims' bound asks for, and it is what
  * a PORTED java enum emits, so the fixture is the shape the mapping will really meet. It has to be
  * top-level: scala only admits that parent in a static scope. */
enum Level extends java.lang.Enum[Level]:
  case Low, Mid, High

class JavaEnumCollectionsSpec extends munit.FunSuite:

  test("JavaEnumMap iterates in ORDINAL order, whatever the insertion order was") {
    val m = new JavaEnumMap[Level, String]
    m(Level.High) = "h"
    m(Level.Low)  = "l"
    m(Level.Mid)  = "m"
    assertEquals(m.keys.toList, List(Level.Low, Level.Mid, Level.High))
    assertEquals(m.values.toList, List("l", "m", "h"))
  }

  test("…and it IS a mutable.Map, so every slot the port retyped still takes it") {
    // `java.util.EnumMap implements Map`, and this port maps `java.util.Map` to `mutable.Map`: if
    // the shim were not one, java's own assignment would stop type-checking.
    val m: scala.collection.mutable.Map[Level, String] = new JavaEnumMap[Level, String]
    m.put(Level.Mid, "m")
    assertEquals(m.get(Level.Mid), Some("m"))
    assertEquals(m.remove(Level.Mid), Some("m"))
    assert(m.isEmpty)
  }

  test("a NULL key throws, as java's EnumMap does") {
    val m = new JavaEnumMap[Level, String]
    intercept[NullPointerException](m.put(null.asInstanceOf[Level], "x"))
  }

  // -- …and the OTHER HALF of java's null contract, which is NOT "throw everywhere" -------------
  //
  // `java.util.EnumMap` throws on a null key at `put` ONLY. Every QUERY answers absent — `get`
  // returns null, `containsKey` returns false, and `remove` returns null without touching the map
  // (`isValidKey` is the one gate, and it is a filter for the readers and a `typeCheck` NPE only
  // for the writer). A shim that throws at a query is LOUDER than java, which is the direction
  // that turns a caller's null-tolerant lookup into an exception, and the ordering is what would
  // have thrown it — so, like the `put` check, these are EXPLICIT: an empty map never consults a
  // comparator, so the divergence would appear only once something was in the map.

  test("a null QUERY answers absent, as java's does — never an exception out of the ordering") {
    val m = new JavaEnumMap[Level, String]
    m(Level.Mid) = "m"                      // NON-empty: an empty TreeMap never consults its ordering
    assertEquals(m.get(null.asInstanceOf[Level]), None)
    assertEquals(m.contains(null.asInstanceOf[Level]), false)
    assertEquals(m.remove(null.asInstanceOf[Level]), None)
    assertEquals(m.size, 1)                 // …and the map is untouched
  }

  test("…and on an EMPTY map too, so the two paths cannot answer differently") {
    val m = new JavaEnumMap[Level, String]
    assertEquals(m.get(null.asInstanceOf[Level]), None)
    assertEquals(m.contains(null.asInstanceOf[Level]), false)
    assertEquals(m.remove(null.asInstanceOf[Level]), None)
  }

  test("JavaEnumSet: adding null throws as java's EnumSet does — checked, not left to the ordering") {
    val s = new JavaEnumSet[Level]
    intercept[NullPointerException](s.addOne(null.asInstanceOf[Level]))
    // the FIRST insertion is the one a TreeSet accepts without ever comparing, which is why the
    // check has to be explicit rather than a consequence of the `Ordering`.
    assert(s.isEmpty)
  }

  test("…and its null QUERIES answer false, on a populated set and on an empty one") {
    val s = JavaEnumSet.of(Level.Mid)
    assertEquals(s.contains(null.asInstanceOf[Level]), false)
    assertEquals(s.remove(null.asInstanceOf[Level]), false)
    assertEquals(s.size, 1)
    val e = new JavaEnumSet[Level]
    assertEquals(e.contains(null.asInstanceOf[Level]), false)
    assertEquals(e.remove(null.asInstanceOf[Level]), false)
  }

  test("JavaEnumMap.from copies, and re-orders — the copy constructor's target") {
    val src = scala.collection.mutable.LinkedHashMap(Level.High -> "h", Level.Low -> "l")
    assertEquals(JavaEnumMap.from(src).keys.toList, List(Level.Low, Level.High))
  }

  test("JavaEnumMap.ofType ignores the class token java needed for its ordinal ARRAY") {
    val m = JavaEnumMap.ofType[Level, String](classOf[Level])
    m(Level.Low) = "l"
    assertEquals(m.size, 1)
  }

  test("JavaEnumSet iterates in ORDINAL order too") {
    val s = JavaEnumSet.of(Level.High, Level.Low)
    assertEquals(s.toList, List(Level.Low, Level.High))
    assert(s.isInstanceOf[scala.collection.mutable.Set[?]])
  }

  test("noneOf is empty and allOf is every constant IN ORDER — the token is load-bearing for one") {
    assert(JavaEnumSet.noneOf(classOf[Level]).isEmpty)
    assertEquals(JavaEnumSet.allOf(classOf[Level]).toList, List(Level.Low, Level.Mid, Level.High))
  }

  test("range is INCLUSIVE at both ends, and refuses an inverted one as java does") {
    assertEquals(JavaEnumSet.range(Level.Low, Level.Mid).toList, List(Level.Low, Level.Mid))
    assertEquals(JavaEnumSet.range(Level.Mid, Level.Mid).toList, List(Level.Mid))
    intercept[IllegalArgumentException](JavaEnumSet.range(Level.High, Level.Low))
  }

  test("complementOf reads the enum off the SET, so an empty one throws — java's own limitation") {
    assertEquals(JavaEnumSet.complementOf(JavaEnumSet.of(Level.Mid)).toList,
                 List(Level.Low, Level.High))
    intercept[IllegalArgumentException](JavaEnumSet.complementOf(new JavaEnumSet[Level]))
  }

  test("copyOf takes any collection and puts it back in ordinal order") {
    assertEquals(JavaEnumSet.copyOf(List(Level.High, Level.Low)).toList, List(Level.Low, Level.High))
  }

  test("the primitive-optional aliases ARE Option, which is what makes them a translation") {
    // Nothing is wrapped and nothing is copied: a value of the alias is an `Option` at every slot.
    val i: JavaOptionalInt    = Some(1)
    val l: JavaOptionalLong   = None
    val d: JavaOptionalDouble = Some(2.5)
    assertEquals(i.get, 1)
    assert(l.isEmpty)
    assertEquals(d.getOrElse(0.0), 2.5)
    val asOption: Option[Int] = i
    assertEquals(asOption, Some(1))
  }
