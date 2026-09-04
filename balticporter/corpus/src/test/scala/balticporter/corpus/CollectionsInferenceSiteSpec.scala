package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** THE SEAM WITH NO HEAD TO COERCE AGAINST — `ENGINE-LIMITS.md` K26's first blindness, at the fix. */
class CollectionsInferenceSiteSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |class Key<T> { }
      |class Holder {
      |  <T> Holder set(Key<T> key, T value) { return this; }
      |}
      |class Uses {
      |  static final Key<Collection<String>> ITEMS = new Key<Collection<String>>();
      |  void go(Holder h, ArrayList<String> list, HashSet<String> set) {
      |    h.set(ITEMS, list);
      |    h.set(ITEMS, set);
      |  }
      |}
      |""".stripMargin

  test("a SEQ at a variable the KEY fixed to the shim is bridged at the inference site") {
    val p = port(src, new CollectionsTransform)
    // `Key<Collection<String>>` fixes `T` to the shim; `ArrayList` retyped to an `ArrayBuffer`.
    // Without the substitution the formal is a bare `T`, `coerce` sees no head and the call is a
    // compile error nothing counts.
    assertEmits(p, "balticporter.runtime.JavaCollection.from(list)")
  }

  test("…and a SET takes the SET factory — one substitution, the table's own arms below it") {
    val p = port(src, new CollectionsTransform)
    // Nothing here decides to wrap: the substituted formal goes through `coerce` exactly as a
    // written one does, so `Kind.Set` picks `fromSet` for the same reason it always did.
    assertEmits(p, "balticporter.runtime.JavaCollection.fromSet(set)")
  }

  test("NEGATIVE — a BARE formal binds NOTHING, so a call with no parameterised slot is untouched") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Sink { <T> void take(T value) { } }
        |class Uses { void go(Sink s, ArrayList<String> list) { s.take(list); } }
        |""".stripMargin, new CollectionsTransform)
    // Java bounds `T` from below here and nothing fixes it; reading the bare occurrence as a binder
    // would answer `T = ArrayBuffer[String]` and defeat the rule's own purpose.
    assertEmits(p, "s.take(list)")
    assertNotEmits(p, "JavaCollection.from(list)")
  }

  test("NEGATIVE — a CLASS's type parameter is not this call's to bind: the receiver fixed it") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Key<T> { }
        |class Box<V> { void put(Key<V> k, V v) { } }
        |class Uses {
        |  static final Key<Collection<String>> ITEMS = new Key<Collection<String>>();
        |  void go(Box<Collection<String>> b, ArrayList<String> list) { b.put(ITEMS, list); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // §4.56 at its sharpest: `V` here owns to the CLASS, so this call cannot bind it and reading it
    // as though it could would be a name test wearing a symbol's clothes. The seam stays the
    // counted refusal it was — closing it needs the RECEIVER's instantiation, a different
    // derivation — and `CollectionInternalCheck` declines on the very same test.
    assertEmits(p, "b.put(Uses.ITEMS, list)")
    assertNotEmits(p, "JavaCollection.from(list)")
  }

  test("NEGATIVE — a value that ALREADY is the shim gets nothing: the substitution is not the wrap") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Key<T> { }
        |class Holder { <T> Holder set(Key<T> key, T value) { return this; } }
        |class Uses {
        |  static final Key<Collection<String>> ITEMS = new Key<Collection<String>>();
        |  void go(Holder h, Collection<String> c) { h.set(ITEMS, c); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // Both ends of the slot are the shim, so `coerce` finds no source kind and answers the argument
    // it was given. The substitution fires and changes nothing, which is what makes it safe.
    assertEmits(p, "h.set(Uses.ITEMS, c)")
    assertNotEmits(p, "JavaCollection.from(c)")
  }
