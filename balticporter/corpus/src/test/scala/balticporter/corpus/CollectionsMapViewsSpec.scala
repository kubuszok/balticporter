package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** java's two `Set`-typed VIEWS of a map — `keySet()` and `entrySet()` — at the type the retyping
  * declares. */
class CollectionsMapViewsSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |class Views {
      |  final Map<String, Integer> m = new HashMap<String, Integer>();
      |  Set<String> names()                     { return m.keySet(); }
      |  Set<Map.Entry<String, Integer>> pairs()  { return m.entrySet(); }
      |  Collection<String> asCollection()       { return m.keySet(); }
      |  void take(Set<String> s)                { }
      |  void pass()                             { take(m.keySet()); }
      |  Set<String> either(boolean b)           { return b ? m.keySet() : new HashSet<String>(); }
      |  int count()                             { return m.keySet().size(); }
      |  void bump() {
      |    for (Map.Entry<String, Integer> e : m.entrySet()) { e.setValue(e.getValue() + 1); }
      |  }
      |}
      |""".stripMargin

  test("a `keySet()` RESULT is the live view, at the retyped `mutable.Set` the declaration says") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.keySetView(this.m)")
    // …and the shape that used to be emitted is gone: `m.keySet` is a `scala.collection.Set`, which
    // is what made a RETURN at the declared type a compile error the phase could not see.
    assertNotEmits(p, "return this.m.keySet\n")
  }

  test("an `entrySet()` RESULT is a `Set` of pairs, not the map") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.entrySetView(this.m)")
    assertNotEmits(p, "return this.m\n")
  }

  test("a `Collection`-typed result composes with the SHIM bridge — one wrap over the other") {
    val p = port(src, new CollectionsTransform)
    // the view is a `Kind.Set` value like any other, so `coerce` reaches its ordinary `fromSet`
    // factory. That composition is the whole argument for fixing this at the rewrite: the seam this
    // used to leave was `CollectionBoundaryCheck`'s standing "shim against scala" example.
    assertEmits(p,
      "balticporter.runtime.JavaCollection.fromSet(balticporter.runtime.JavaCollections.keySetView(this.m))")
  }

  test("an ARGUMENT slot takes it with nothing added") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "this.take(balticporter.runtime.JavaCollections.keySetView(this.m))")
  }

  test("…and so does a BRANCH of a conditional, which no slot-level answer can reach") {
    val p = port(src, new CollectionsTransform)
    // `coerce` sees the `if` and not its arms, so an answer written at the slot would have left this
    // one exactly as it was. The rewrite is inside the branch, so there is nothing to distribute.
    assertEmits(p, "if (b) balticporter.runtime.JavaCollections.keySetView(this.m) else")
  }

  test("a plain READ off the view keeps java's own members — no slot, no wrap") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.keySetView(this.m).size")
  }

  test("`setValue` in a `for` over `entrySet()` STILL writes through the map") {
    val p = port(src, new CollectionsTransform)
    // the loop source is now the view, so the write-through has to read the map out of it. Asserted
    // as the emitted `put`, because that is the only evidence there is: dropped, this compiles, and
    // the write reaches a detached pair.
    assertEmits(p, "for (e <- balticporter.runtime.JavaCollections.entrySetView(this.m))")
    assertEmits(p, "this.m.put(e._1,")
    assertNotEmits(p, "e.setValue(")
  }

  // ---- the NEGATIVES: a receiver this phase did not retype is none of its business (§4.56) ----

  private val ownSrc =
    """package demo;
      |import java.util.Set;
      |class Own {
      |  Set<String> keySet()  { return null; }
      |  Set<String> entrySet() { return null; }
      |}
      |class UsesOwn {
      |  Set<String> viaOwn(Own o)   { return o.keySet(); }
      |  Set<String> pairsOwn(Own o) { return o.entrySet(); }
      |}
      |""".stripMargin

  test("a same-NAMED member on a type the phase never retyped is left alone") {
    val p = port(ownSrc, new CollectionsTransform)
    // `Own` is neither a mapped JDK type nor a subtype of one, so it has no `Kind` and the rewrite
    // is never offered the call. A name test would have taken both of these.
    assertEmits(p, "o.keySet()")
    assertEmits(p, "o.entrySet()")
    assertNotEmits(p, "keySetView")
    assertNotEmits(p, "entrySetView")
  }
