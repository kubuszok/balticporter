package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Decision, Pipeline, PorterNote, Reason}
import balticporter.transform.CollectionsTransform

/** A MINTED PARENT ANOTHER MINTED PARENT SUBSUMES is dropped — `ENGINE-LIMITS.md` K28.1.
  *
  * ==The difference this is licensed by==
  * Java's `Map` and `Iterable` are independent interfaces and a class implements BOTH, relating them
  * at ONE MEMBER spelled two ways: `Map` has `entrySet().iterator()`, `Iterable` has `iterator()`,
  * and the class writes the second. Mapped onto `scala.collection.mutable.Map` AND onto the
  * `JavaIterable` shim, the emitted class declares `iterator(): JavaIterator[…]` under a parent
  * declaring `iterator: Iterator[…]` — one name at two arities, which scala's single namespace
  * cannot hold. `CLAUDE.md` §4.5's sentence arriving at a MINTING rather than at a shim's design: no
  * repair at the member helps, because the conflict is in the parents.
  *
  * ==Why the NEGATIVES are the whole of the difficulty==
  * The claim a dropped parent makes is *the surviving target already carries this relation*, and it
  * is wrong in two directions that no compile of THIS fixture would show:
  *
  *   - a shim the target does NOT subsume. `mutable.Buffer` is not a `JavaCollection` and no scala
  *     collection is (that is what a STANDALONE target means), so dropping that clause is an
  *     immediate `Not Found` at fourteen members. `SubsumesShim` has exactly one shim on its
  *     right-hand side for that reason;
  *   - a shim the target subsumes at a DIFFERENT ELEMENT. `implements Map<K,V>, Iterable<String>` is
  *     ordinary java — java's `Map` declares no `iterator()` at all, so nothing forces the element —
  *     and dropping the clause there would silently change what `for (x <- xs)` yields, with a green
  *     compile and no count moving anywhere. That is the direction with NO instrument, which is why
  *     the element agreement is asked rather than assumed.
  *
  * The port that motivated it is measured where the drop actually fires: three classes, 25 member
  * digests, `ENGINE-LIMITS.md` K28.1.
  */
class CollectionsSubsumedParentSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |public class OMap<K, V> implements Map<K, V>, Iterable<Map.Entry<K, V>> {
      |  public Iterator<Map.Entry<K, V>> iterator() { return null; }
      |  public int size() { return 0; }
      |  public boolean isEmpty() { return true; }
      |  public boolean containsKey(Object k) { return false; }
      |  public boolean containsValue(Object v) { return false; }
      |  public V get(Object k) { return null; }
      |  public V put(K k, V v) { return null; }
      |  public V remove(Object k) { return null; }
      |  public void putAll(Map<? extends K, ? extends V> m) { }
      |  public void clear() { }
      |  public Set<K> keySet() { return null; }
      |  public Collection<V> values() { return null; }
      |  public Set<Map.Entry<K, V>> entrySet() { return null; }
      |}
      |class OSet<E> implements Set<E>, Iterable<E> {
      |  public Iterator<E> iterator() { return null; }
      |  public int size() { return 0; }
      |  public boolean isEmpty() { return true; }
      |  public boolean contains(Object o) { return false; }
      |  public Object[] toArray() { return null; }
      |  public <T> T[] toArray(T[] a) { return null; }
      |  public boolean add(E e) { return false; }
      |  public boolean remove(Object o) { return false; }
      |  public boolean containsAll(Collection<?> c) { return false; }
      |  public boolean addAll(Collection<? extends E> c) { return false; }
      |  public boolean retainAll(Collection<?> c) { return false; }
      |  public boolean removeAll(Collection<?> c) { return false; }
      |  public void clear() { }
      |}
      |class Wrong<K, V> implements Map<K, V>, Iterable<String> {
      |  public Iterator<String> iterator() { return null; }
      |  public int size() { return 0; }
      |  public boolean isEmpty() { return true; }
      |  public boolean containsKey(Object k) { return false; }
      |  public boolean containsValue(Object v) { return false; }
      |  public V get(Object k) { return null; }
      |  public V put(K k, V v) { return null; }
      |  public V remove(Object k) { return null; }
      |  public void putAll(Map<? extends K, ? extends V> m) { }
      |  public void clear() { }
      |  public Set<K> keySet() { return null; }
      |  public Collection<V> values() { return null; }
      |  public Set<Map.Entry<K, V>> entrySet() { return null; }
      |}
      |abstract class Both<E> implements List<E>, Collection<E> { }
      |abstract class Plain<E> implements Iterable<E> { }
      |""".stripMargin

  private def ported: String =
    val after = Pipeline.run(SpoonTir.fromSource(src), List(new CollectionsTransform()))
    new TirEmitter(after).emit

  private def decisions: List[Decision] =
    Pipeline.runTraced(SpoonTir.fromSource(src), List(new CollectionsTransform()))._2
      .of(Decision.Kind.SubsumedParent)

  // -------------------------------------------------------------------------
  // the positives
  // -------------------------------------------------------------------------

  test("a Map class implementing Iterable<Map.Entry> emits ONE parent, the scala Map") {
    val out = ported
    assert(out.contains("class OMap[K <: java.lang.Object, V <: java.lang.Object] extends scala.collection.mutable.Map[K, V] {"),
           s"OMap kept the subsumed shim parent\n--- emitted ---\n$out")
  }

  test("…and a Set class the same, at the other kind") {
    val out = ported
    assert(out.contains("class OSet[E <: java.lang.Object] private[demo] () extends scala.collection.mutable.Set[E] {"),
           s"OSet kept the subsumed shim parent\n--- emitted ---\n$out")
  }

  test("the drop is a RECORDED decision, universal, naming what took the relation over") {
    val ds = decisions.filter(_.subjectFqn.endsWith("OMap"))
    assertEquals(clue(ds).size, 1)
    assert(ds.head.reason.isInstanceOf[Reason.Universal], clue(ds.head.reason).toString)
    val pairs = PorterNote.pairs(ds.head).toMap
    assertEquals(pairs.get("subsumed-by"), Some("scala.collection.mutable.Map"))
    assert(clue(pairs.getOrElse("dropped", "")).startsWith("balticporter.runtime.JavaIterable"))
    // …and it is RENDERED at the declaration. A dropped clause is text that is simply ABSENT, so
    // the java `implements` line reads as untranslated and nothing local says why (§4.575).
    assert(PorterNote.Rendered.contains(Decision.Kind.SubsumedParent))
    assert(PorterNote.AtDeclaration.contains(Decision.Kind.SubsumedParent))
  }

  // -------------------------------------------------------------------------
  // the negatives — each one a drop that would be wrong, and only one of them loud
  // -------------------------------------------------------------------------

  test("NEGATIVE — a DIFFERENT element is a relation the target does not carry, and is silent") {
    val out = ported
    assert(out.contains("class Wrong[K <: java.lang.Object, V <: java.lang.Object] private[demo] () extends scala.collection.mutable.Map[K, V] with balticporter.runtime.JavaIterable[java.lang.String]"),
           s"Wrong lost an Iterable<String> clause a mutable.Map does not answer for\n--- emitted ---\n$out")
    assert(!clue(decisions.map(_.subjectFqn)).exists(_.endsWith("Wrong")))
  }

  test("NEGATIVE — a shim the target does NOT subsume stays, whatever else the class extends") {
    val out = ported
    assert(out.contains("balticporter.runtime.JavaCollection[E]"),
           s"Both lost its java.util.Collection clause, which no scala collection is a subtype of" +
             s"\n--- emitted ---\n$out")
    assert(!clue(decisions.map(_.subjectFqn)).exists(_.endsWith("Both")))
  }

  test("NEGATIVE — a shim with NO kind parent beside it has nothing to be subsumed by") {
    val out = ported
    assert(out.contains("class Plain[E <: java.lang.Object] private[demo] () extends balticporter.runtime.JavaIterable[E]"),
           s"Plain lost the only parent it had\n--- emitted ---\n$out")
    assert(!clue(decisions.map(_.subjectFqn)).exists(_.endsWith("Plain")))
  }
