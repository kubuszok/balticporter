package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** THE CLASH THE MINTED PARENT MADE — `CLAUDE.md` §4.5 read at a CALL rather than at a shim.
  *
  * §4.5's sentence is that a parent adds MEMBERS and an extension adds a view, and it is written
  * about the runtime shims. It governs what this phase does to a class the PROGRAM declares just as
  * exactly: `Ledger implements java.util.Map<K,V>` is emitted `extends
  * scala.collection.mutable.Map[K,V]`, so the class now inherits `remove(key: K): Option[V]` beside
  * the `remove(o: Object): V` java obliged it to declare (`ENGINE-LIMITS.md` K24 is why that member
  * stays: java's lookup is BY VALUE and a probe of an unrelated type is meant to miss).
  *
  * Java's candidate set at `ledger.remove("k")` was ONE member. Scala's is TWO and a `String`
  * matches both, so scalac reports `E051 Ambiguous overload` — at a call java resolved without
  * hesitating, in a port where nothing is wrong: the member has to stay and the parent is what makes
  * every retyped slot conform. `CLAUDE.md` §1 says an obligation the engine's own translation
  * created is not a port's to discharge, so the phase pins the call.
  *
  * ==The pin is java's own spelling==
  * `ledger.remove("k".asInstanceOf[java.lang.Object])` is the translation of `ledger.remove((Object)
  * "k")`, which is what a java programmer writes for the same disambiguation — the node kind the
  * frontend already builds for a cast, so no emitter arm and no `catalog` obligation moves. It works
  * because `java.lang.Object` conforms to the minted parent's `K`/`A` only where that parameter IS
  * `Object`, which is the refusal the last negative below pins.
  *
  * ==Why it must NOT be an over-approximation==
  * Ascribing every `Object`-formal argument would be correct and would move emitted text on every
  * port with such a call, which `CLAUDE.md` §5 has no instrument for. So four conjuncts, all of them
  * the phase's own record: the owner is a class THIS PHASE re-parented, onto a target that is not
  * standalone; the callee is a member the PROGRAM declares over exactly one `java.lang.Object`; the
  * minted parent declares that (name, arity) AT ITS TYPE PARAMETER; and the argument is not already
  * an `Object`.
  */
class CollectionsMintedParentClashSpec extends PortSuite:

  /** the shape, on all three kinds at once. Each class declares java's `Object`-formal member and
    * a CALLER of it, so the pin's effect is visible in the emitted caller. */
  private val src =
    """package demo;
      |import java.util.*;
      |class Ledger<V> implements Map<String, V> {
      |  private final HashMap<String, V> slots = new HashMap<String, V>();
      |  public V get(Object o)              { return slots.get(o); }
      |  public V remove(Object o)           { return slots.remove(o); }
      |  public boolean containsKey(Object o){ return slots.containsKey(o); }
      |  public int size()                   { return slots.size(); }
      |  public boolean isEmpty()            { return slots.isEmpty(); }
      |  public boolean containsValue(Object o) { return slots.containsValue(o); }
      |  public V put(String k, V v)         { return slots.put(k, v); }
      |  public void putAll(Map<? extends String, ? extends V> m) { slots.putAll(m); }
      |  public void clear()                 { slots.clear(); }
      |  public Set<String> keySet()         { return slots.keySet(); }
      |  public Collection<V> values()       { return slots.values(); }
      |  public Set<Map.Entry<String, V>> entrySet() { return slots.entrySet(); }
      |}
      |class Bag<E> implements Set<E> {
      |  private final HashSet<E> items = new HashSet<E>();
      |  public boolean remove(Object o)     { return items.remove(o); }
      |  public boolean contains(Object o)   { return items.contains(o); }
      |  public int size()                   { return items.size(); }
      |  public boolean isEmpty()            { return items.isEmpty(); }
      |  public Iterator<E> iterator()       { return items.iterator(); }
      |  public Object[] toArray()           { return items.toArray(); }
      |  public <T> T[] toArray(T[] a)       { return items.toArray(a); }
      |  public boolean add(E e)             { return items.add(e); }
      |  public boolean containsAll(Collection<?> c) { return items.containsAll(c); }
      |  public boolean addAll(Collection<? extends E> c) { return items.addAll(c); }
      |  public boolean retainAll(Collection<?> c) { return items.retainAll(c); }
      |  public boolean removeAll(Collection<?> c) { return items.removeAll(c); }
      |  public void clear()                 { items.clear(); }
      |}
      |class Callers {
      |  void useMap(Ledger<Integer> l, String k) { l.remove(k); l.get(k); l.containsKey(k); }
      |  void useSet(Bag<String> b, String e)     { b.remove(e); b.contains(e); }
      |}
      |""".stripMargin

  test("a MAP class's own `Object`-formal members are PINNED at every caller") {
    val p = port(src, new CollectionsTransform)
    // the minted parent is `mutable.Map[String, Integer]`, whose `get`/`remove`/`contains` all take
    // the KEY — so an unpinned `l.remove(k)` WOULD be ambiguous. K28.1's bridge removes the second
    // alternative instead of disambiguating it, so the call is re-pointed and no ascription is
    // emitted at all (see the header).
    assertEmits(p, "l.remove$java(k)")
    assertEmits(p, "l.get$java(k)")
    assertNotEmits(p, "l.remove(k.asInstanceOf[java.lang.Object])")
    // …`containsKey` is NOT in the table: scala's `mutable.Map` has no member of that name, so
    // there is nothing to be ambiguous with and the pin would move emitted text for nothing.
    assertEmits(p, "l.containsKey(k)")
  }

  test("a SET class's own `remove`/`contains` are pinned too, at the ELEMENT type") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "b.remove$java(e)")
    assertEmits(p, "b.contains$java(e)")
    assertNotEmits(p, "b.remove(e.asInstanceOf[java.lang.Object])")
  }

  test("the class's OWN body still delegates through the probe helpers — the pin is at the CALLER") {
    val p = port(src, new CollectionsTransform)
    // K24's helpers answer inside `Ledger`, where the receiver is a RETYPED java map rather than a
    // re-parented program class; the two mechanisms sit at different receivers and neither eats the
    // other's site.
    assertEmits(p, "balticporter.runtime.JavaCollections.mapRemove(this.slots, o)")
    assertEmits(p, "balticporter.runtime.JavaCollections.setRemove(this.items, o)")
  }

  test("NEGATIVE — a class with NO mapped parent is untouched, however its members are declared") {
    val p = port(
      """package demo;
        |class Registry {
        |  public Object remove(Object o) { return null; }
        |  public boolean contains(Object o) { return false; }
        |}
        |class Uses { void go(Registry r, String s) { r.remove(s); r.contains(s); } }
        |""".stripMargin, new CollectionsTransform)
    // nothing minted a parent here, so there is no second alternative and no ambiguity: the
    // conjunct that decides this is the phase's OWN record of what it re-parented (§4.56).
    assertEmits(p, "r.remove(s)")
    assertEmits(p, "r.contains(s)")
    assertNotEmits(p, "asInstanceOf[java.lang.Object]")
  }

  test("NEGATIVE — a STANDALONE target is no parent to clash with: java's own arity, by construction") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Cursor<E> implements Iterator<E> {
        |  public boolean hasNext() { return false; }
        |  public E next()          { return null; }
        |  public boolean holds(Object o) { return false; }
        |}
        |class Uses { void go(Cursor<String> c, String s) { c.holds(s); } }
        |""".stripMargin, new CollectionsTransform)
    // `java.util.Iterator` maps to the `JavaIterator` SHIM, which carries java's shape and declares
    // nothing at the element type — §4.5's whole reason for a standalone target. Counting it as a
    // re-parenting would pin calls against a parent that has no such member.
    assertNotEmits(p, "c.holds(s.asInstanceOf[java.lang.Object])")
  }

  test("NEGATIVE — an argument ALREADY typed `Object` needs no pin, and gets none") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Store<V> implements Map<String, V> {
        |  private final HashMap<String, V> slots = new HashMap<String, V>();
        |  public V get(Object o)              { return slots.get(o); }
        |  public V remove(Object o)           { return slots.remove(o); }
        |  public boolean containsKey(Object o){ return slots.containsKey(o); }
        |  public int size()                   { return slots.size(); }
        |  public boolean isEmpty()            { return slots.isEmpty(); }
        |  public boolean containsValue(Object o) { return slots.containsValue(o); }
        |  public V put(String k, V v)         { return slots.put(k, v); }
        |  public void putAll(Map<? extends String, ? extends V> m) { slots.putAll(m); }
        |  public void clear()                 { slots.clear(); }
        |  public Set<String> keySet()         { return slots.keySet(); }
        |  public Collection<V> values()       { return slots.values(); }
        |  public Set<Map.Entry<String, V>> entrySet() { return slots.entrySet(); }
        |}
        |class Uses { void go(Store<Integer> s, Object probe) { s.remove(probe); } }
        |""".stripMargin, new CollectionsTransform)
    // an `Object` at an `Object` slot already selects java's alternative uniquely; ascribing it to
    // its own type is emitted text for nothing, which is the over-approximation §5 cannot see. The
    // bridge renames the member either way, so what this negative still pins is the ABSENCE of the
    // ascription — which is what it was always about.
    assertEmits(p, "s.remove$java(probe)")
    assertNotEmits(p, "asInstanceOf[java.lang.Object])")
  }

  test("NEGATIVE — a KEY TYPE that IS `Object` is the one shape no ascription separates: REFUSED") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Any2Any implements Map<Object, Object> {
        |  private final HashMap<Object, Object> slots = new HashMap<Object, Object>();
        |  public Object get(Object o)         { return slots.get(o); }
        |  public Object remove(Object o)      { return slots.remove(o); }
        |  public boolean containsKey(Object o){ return slots.containsKey(o); }
        |  public int size()                   { return slots.size(); }
        |  public boolean isEmpty()            { return slots.isEmpty(); }
        |  public boolean containsValue(Object o) { return slots.containsValue(o); }
        |  public Object put(Object k, Object v) { return slots.put(k, v); }
        |  public void putAll(Map<?, ?> m)     { }
        |  public void clear()                 { slots.clear(); }
        |  public Set<Object> keySet()         { return slots.keySet(); }
        |  public Collection<Object> values()  { return slots.values(); }
        |  public Set<Map.Entry<Object, Object>> entrySet() { return slots.entrySet(); }
        |}
        |class Uses { void go(Any2Any m, String s) { m.remove(s); } }
        |""".stripMargin, new CollectionsTransform)
    // both alternatives WOULD take an `Object` and the pin could make neither unique — which is why
    // it refuses. The bridge answers this shape too, and by construction rather than by refusing:
    // renaming java's member leaves one alternative, so the `E051` this test was written to leave
    // LOUD is now closed rather than reported. The refusal path itself is unchanged and is what a
    // class whose rename is refused still takes.
    assertEmits(p, "m.remove$java(s)")
    assertNotEmits(p, "m.remove(s.asInstanceOf[java.lang.Object])")
  }
