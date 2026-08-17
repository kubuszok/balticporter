package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** K5.7's OTHER half — a `Tuple2` is impossible as a PARENT and exact at a SLOT, and which of the
  * two a class gets is decided by a CAPABILITY the class either has or has not.
  *
  * `java.util.Map.Entry` maps to `scala.Tuple2`, so every USE of the interface is retyped to a pair
  * while a class IMPLEMENTING it keeps java's parent (`Tuple2` is final, takes its components in its
  * constructor and has no write-through member). The class's own value then meets the retyped slot —
  * `Map.Entry<K,V> getEntry(int)` — and the projection `(getKey, getValue)` there is a COPY, which
  * is precisely what K2 refuses: a later `setValue` on the copy succeeds and writes nothing.
  *
  * Except where there is no write to lose. `MapEntry.setValue` is `throw new
  * UnsupportedOperationException()` in flexmark's own source — java's own optional-operation refusal,
  * written by the library at the very member the copy would detach — so the value REALLY IS a
  * detached pair and the copy is exact. That is the whole licence, and it is a fact about the
  * LIBRARY's body rather than about the interface.
  *
  * The negatives are what make this a rule rather than a convenience, and the last of them is the
  * one this phase could most easily get wrong about itself: `refuseOnTarget` substitutes a throw at
  * exactly this member for an entry it BROKE, so a derivation reading the mapped tree would let the
  * phase's own refusal license its own projection — detaching an entry whose java writes through,
  * with a green compile and no count moving anywhere (`CLAUDE.md` §3).
  */
class CollectionsDetachedEntrySpec extends PortSuite:

  /** the library's own refusal at `setValue`, and a slot typed at the interface. */
  private val refusing =
    """package demo;
      |import java.util.Map;
      |final class Pin<K, V> implements Map.Entry<K, V> {
      |  private final K k; private final V v;
      |  Pin(K k, V v) { this.k = k; this.v = v; }
      |  public K getKey()      { return k; }
      |  public V getValue()    { return v; }
      |  public V setValue(V x) { throw new UnsupportedOperationException(); }
      |}
      |class Holder<K, V> {
      |  Map.Entry<K, V> getEntry(K k, V v) { return new Pin<K, V>(k, v); }
      |}
      |""".stripMargin

  test("a DETACHED entry is projected at the retyped slot") {
    val p = port(refusing, new CollectionsTransform)
    // ONE evaluation of the `new`, which is why the projection is a helper rather than
    // `(x.getKey, x.getValue)` written at the site.
    assertEmits(p, "balticporter.runtime.JavaCollections.entryToPair(new demo.Pin[K, V](k, v))")
  }

  test("…and the class still keeps JAVA's parent — the two halves are about different things") {
    val p = port(refusing, new CollectionsTransform)
    // `Tuple2` cannot be a parent whatever the class's `setValue` does; the projection says nothing
    // about the `extends` clause and must not.
    assertEmits(p, "extends java.util.Map.Entry[K, V]")
  }

  test("TRANSITIVE — the `implements` clause may sit on an interface the library declares") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |interface Paired<K, V> extends Map.Entry<K, V> { K first(); }
        |final class Duo<K, V> implements Paired<K, V> {
        |  private final K k; private final V v;
        |  Duo(K k, V v) { this.k = k; this.v = v; }
        |  public K first()       { return k; }
        |  public K getKey()      { return k; }
        |  public V getValue()    { return v; }
        |  public V setValue(V x) { throw new IllegalStateException("setValue not supported"); }
        |}
        |class Holder<K, V> { Map.Entry<K, V> get(K k, V v) { return new Duo<K, V>(k, v); } }
        |""".stripMargin, new CollectionsTransform)
    // TWO facts at once, and both are flexmark's own shape. The interface hop is §4.56's fast-path
    // rule (`Pair implements Paired`, `Paired extends Map.Entry`), and the EXCEPTION CLASS is not
    // pinned: what licenses the projection is that no write can happen, and a body whose first act
    // is to throw cannot perform one whatever it throws — flexmark's `Pair` throws an
    // `IllegalStateException` for the same contract `MapEntry` spells with `UnsupportedOperation`.
    assertEmits(p, "balticporter.runtime.JavaCollections.entryToPair(new demo.Duo[K, V](k, v))")
  }

  test("NEGATIVE — an entry that WRITES THROUGH is not projected, and the seam stays") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |final class Cell<K, V> implements Map.Entry<K, V> {
        |  private final K k; private V v;
        |  Cell(K k, V v) { this.k = k; this.v = v; }
        |  public K getKey()      { return k; }
        |  public V getValue()    { return v; }
        |  public V setValue(V x) { V old = v; v = x; return old; }
        |}
        |class Holder<K, V> { Map.Entry<K, V> get(K k, V v) { return new Cell<K, V>(k, v); } }
        |""".stripMargin, new CollectionsTransform)
    // java runs this member and callers read the value back through the entry. A copy here compiles
    // and silently drops every later write — `CLAUDE.md` §4.4's defect class — so the honest answer
    // is the compile error the slot already had.
    assertNotEmits(p, "entryToPair")
  }

  test("NEGATIVE — a CONDITIONAL refusal is a write-through entry") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |final class Guarded<K, V> implements Map.Entry<K, V> {
        |  private final K k; private V v; private final boolean frozen;
        |  Guarded(K k, V v, boolean f) { this.k = k; this.v = v; this.frozen = f; }
        |  public K getKey()   { return k; }
        |  public V getValue() { return v; }
        |  public V setValue(V x) {
        |    if (frozen) throw new UnsupportedOperationException();
        |    V old = v; v = x; return old;
        |  }
        |}
        |class Holder<K, V> { Map.Entry<K, V> get(K k, V v) { return new Guarded<K, V>(k, v, false); } }
        |""".stripMargin, new CollectionsTransform)
    // the capability test is asked of the FIRST thing the body does, and not of whether a `throw`
    // appears in it: this member refuses for one receiver state and writes for another, so the class
    // writes through.
    assertNotEmits(p, "entryToPair")
  }

  test("NEGATIVE — a class that declares NO `setValue` DECLINES rather than being assumed") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |abstract class Half<K, V> implements Map.Entry<K, V> {
        |  private final K k;
        |  Half(K k) { this.k = k; }
        |  public K getKey() { return k; }
        |}
        |class Holder<K, V> { Map.Entry<K, V> get(Half<K, V> h) { return h; } }
        |""".stripMargin, new CollectionsTransform)
    // an abstract member says nothing about what an implementor does, and the conservative arm is
    // the one that leaves the seam. Reading it as "no write happens here" would project every
    // subclass, including one that writes.
    assertNotEmits(p, "entryToPair")
  }

  test("NEGATIVE — the phase's OWN substituted throw does not license its own projection") {
    val p = port(
      """package demo;
        |import java.util.Map;
        |final class Delegating<K, V> implements Map.Entry<K, V> {
        |  private final Map.Entry<K, V> inner;
        |  Delegating(Map.Entry<K, V> inner) { this.inner = inner; }
        |  public K getKey()      { return inner.getKey(); }
        |  public V getValue()    { return inner.getValue(); }
        |  public V setValue(V x) { return inner.setValue(x); }
        |}
        |class Holder<K, V> { Map.Entry<K, V> get(Delegating<K, V> d) { return d; } }
        |""".stripMargin, new CollectionsTransform)
    // `refuseOnTarget` replaces this body with java's own optional-operation exception, because the
    // mapping retyped `inner` to a `Tuple2` and REMOVED the call it delegated to. Read off the
    // MAPPED tree, that throw would read exactly like the library's own and would license a
    // projection of an entry java writes through — the capability is therefore read off the
    // ORIGINAL units, in `run`, before any body of this phase's is written.
    assertEmits(p, "throw new java.lang.UnsupportedOperationException")
    assertNotEmits(p, "entryToPair")
  }

  test("the tables agree: every UNINHERITABLE target the projection serves has an unsupported member") {
    // the projection's licence is a member the target CANNOT carry, so a target listed as
    // uninheritable with no such member would be one this derivation silently never fires for.
    CollectionsTransform.UninheritableTargets.foreach: tgt =>
      assert(CollectionsTransform.UnsupportedOnTarget.get(tgt).exists(_.nonEmpty),
             s"$tgt is uninheritable and names no unsupported member")
  }
