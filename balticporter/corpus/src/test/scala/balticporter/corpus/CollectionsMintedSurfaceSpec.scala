package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** THE SURFACE THE MINTED PARENT DECLARES — `ENGINE-LIMITS.md` K28.1's bridge. */
class CollectionsMintedSurfaceSpec extends PortSuite:

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
      |  public boolean add(E... rest)       { return items.add(rest[0]); }
      |  public boolean add(E e)             { return items.add(e); }
      |  public boolean containsAll(Collection<?> c) { return items.containsAll(c); }
      |  public boolean addAll(Collection<? extends E> c) { return items.addAll(c); }
      |  public boolean retainAll(Collection<?> c) { return items.retainAll(c); }
      |  public boolean removeAll(Collection<?> c) { return items.removeAll(c); }
      |  public void clear()                 { items.clear(); }
      |}
      |interface Widened<V> extends Map<String, V> { }
      |""".stripMargin

  test("a MAP class's clashing members are RENAMED and scala's are SYNTHESISED over them") {
    val p = port(src, new CollectionsTransform)
    // java's own member survives under a name nothing inherits…
    assertEmits(p, "def put$java(k: java.lang.String, v: V): V")
    assertEmits(p, "def get$java(o: java.lang.Object): V")
    // …and the parent's member is the delegation over it. `Option(x)` is what java's own `put`/`get`
    // document: null means ABSENT, which is exactly `MapOps`' `None`.
    assertEmits(p, "override def put(key: java.lang.String, value: V): scala.Option[V] = scala.Option(this.put$java(key, value))")
    assertEmits(p, "override def get(key: java.lang.String): scala.Option[V] = scala.Option(this.get$java(key))")
    // `Growable`/`Shrinkable`, spelled over java's own `put`/`remove` — no java member is named
    // either of these, so they ride on the two rows above rather than renaming anything themselves.
    assertEmits(p, "override def addOne(elem: scala.Tuple2[java.lang.String, V]): this.type")
    assertEmits(p, "this.put$java(elem._1, elem._2)")
    assertEmits(p, "override def subtractOne(key: java.lang.String): this.type")
    assertEmits(p, "this.remove$java(key)")
  }

  test("a MAP with no `iterator()` reaches scala's through java's OWN idiom, `entrySet().iterator`") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "override def iterator: scala.collection.Iterator[scala.Tuple2[java.lang.String, V]] = this.entrySet().iterator")
  }

  test("NEGATIVE — a delegate the emitted parent does NOT declare keeps java's name") {
    val p = port(src, new CollectionsTransform)
    // `entrySet` is a delegate (the row above uses it) and `mutable.Map` declares nothing of that
    // name, so nothing could capture `this.entrySet()` and renaming it would move emitted surface
    // for a hazard that does not exist. `CapturedByTarget` is what decides this, and it is why that
    // table is not `BridgedTarget`'s own key.
    assertNotEmits(p, "entrySet$java")
    // …the same, read at `containsKey`: scala's `Map` has no such member at all.
    assertNotEmits(p, "containsKey$java")
  }

  /** …and the one below it that THIS FIXTURE CANNOT PROVE, which is worth stating rather than
    * hiding (§4.59: a fixture only promotes a fact it can actually distinguish). */
  test("a VARARG overload is never the delegate while a fixed-arity one exists") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "override def addOne(elem: E): this.type")
    assertEmits(p, "this.add$java(elem)")
    // …and the pack keeps java's own name, because nothing bridged it.
    assertEmits(p, "def add(rest: scala.Array[E])")
  }

  test("a SET class owes `contains`/`addOne`/`subtractOne`/`iterator`, and the iterator is a VIEW") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "override def contains(elem: E): scala.Boolean = this.contains$java(elem)")
    assertEmits(p, "override def subtractOne(elem: E): this.type")
    // java's `iterator()` result is a shim, so the bridge is the shim's own scala VIEW — never a
    // copy, which would detach the traversal from the collection java was iterating.
    assertEmits(p, "override def iterator: scala.collection.Iterator[E] = this.iterator$java().asScala")
  }

  test("NEGATIVE — an INTERFACE that merely widens the java one is not the implementor") {
    // `Widened extends Map<String,V>` declares none of the delegates, so every row declines and the
    // type owes nothing: a java interface may leave members abstract, and the obligation lands on
    // whatever implements it.
    val p = port(
      """package demo;
        |import java.util.*;
        |interface Widened<V> extends Map<String, V> { }
        |""".stripMargin, new CollectionsTransform)
    // it DOES get the minted parent — that is `declaredParentKinds`' answer and is correct…
    assertEmits(p, "trait Widened[V <: java.lang.Object] extends scala.collection.mutable.Map[java.lang.String, V]")
    // …and it gets no bridge at all. A bridge here would delegate to members the type does not have.
    assertNotEmits(p, "override def get")
    assertNotEmits(p, "override def addOne")
    assertNotEmits(p, "$java")
  }
