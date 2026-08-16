package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** K26's `DeclaredSubtype` half, CLOSED AT THE SLOT — a value the PROGRAM declares, meeting a shim.
  *
  * `typeMap` sends `java.util.Collection` to a STANDALONE shim (`CLAUDE.md` §4.5 says it must) and
  * every java SUBTYPE of it to a `scala.collection.*` type, so java's `Set <: Collection` has no
  * image. `coerce` bridges a value at such a slot wherever a factory exists — and it reads the
  * source's kind out of `kindOf`, which is keyed on this phase's own SCALA TARGETS and therefore
  * answers NOTHING for a type the program declares.
  *
  * That is the exact blindness `CollectionInternalCheck.Issue.DeclaredSubtype` exists to COUNT
  * (`ENGINE-LIMITS.md` K26): `OrderedSet implements java.util.Set` handed to its own
  * `retainAll(Collection<?>)` matched no factory, and the residue reached the compiler as a bare
  * `Found: … / Required: …`. The class really IS a `mutable.Set` at that slot, because THIS PHASE
  * made it one, so `JavaCollection.fromSet` conforms and the seam closes where the lane names it.
  *
  * ==The two conjuncts that keep it from wrapping correct code==
  *   - a class that ALREADY carries the wanted shim among its parents conforms and gets nothing —
  *     and `JavaCollection extends JavaIterable`, so a `Collection`-parented class satisfies the
  *     iterable slot too;
  *   - a class this phase never re-parented is not a party to the edge at all, and its seam stays
  *     the honest compile error it was.
  */
class CollectionsDeclaredSubtypeSpec extends PortSuite:

  private val setSubtype =
    """package demo;
      |import java.util.*;
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
      |  void go(Bag<String> a, Bag<String> b) { a.retainAll(b); a.addAll(b); }
      |}
      |""".stripMargin

  test("a program class emitted onto `mutable.Set` bridges into its OWN `Collection`-typed slot") {
    val p = port(setSubtype, new CollectionsTransform)
    // java's `Set <: Collection` carried this value; the mapping sends the two ends to a
    // `scala.collection.*` type and a standalone shim, so the edge has no image and the value has
    // to be wrapped at the slot rather than left to a subtyping that no longer exists.
    assertEmits(p, "a.retainAll(balticporter.runtime.JavaCollection.fromSet(b))")
    assertEmits(p, "a.addAll(balticporter.runtime.JavaCollection.fromSet(b))")
  }

  test("a LIST subtype takes the Seq factory, from the same record") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Roll<E> extends ArrayList<E> { }
        |class Sink {
        |  void take(Collection<String> c) { }
        |  void go(Roll<String> r) { take(r); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // the slot is a formal THIS PROGRAM DECLARES, which is where `coerce` is reached at all: a
    // call at a symbol the PHASE MINTED carries no signature, and K26 records that third blindness
    // with the number the operand-only arm measured (2 rows, 1 of them false).
    assertEmits(p, "this.take(balticporter.runtime.JavaCollection.from(r))")
  }

  test("TRANSITIVE — the `implements` clause may sit on an ancestor this library declares") {
    val p = port(
      """package demo;
        |import java.util.*;
        |abstract class Base<E> implements Set<E> {
        |  public boolean remove(Object o)     { return false; }
        |  public boolean contains(Object o)   { return false; }
        |  public int size()                   { return 0; }
        |  public boolean isEmpty()            { return true; }
        |  public Iterator<E> iterator()       { return null; }
        |  public Object[] toArray()           { return null; }
        |  public <T> T[] toArray(T[] a)       { return null; }
        |  public boolean add(E e)             { return false; }
        |  public boolean containsAll(Collection<?> c) { return false; }
        |  public boolean addAll(Collection<? extends E> c) { return false; }
        |  public boolean retainAll(Collection<?> c) { return false; }
        |  public boolean removeAll(Collection<?> c) { return false; }
        |  public void clear()                 { }
        |}
        |class Leaf<E> extends Base<E> { }
        |class Callers { void go(Base<String> a, Leaf<String> b) { a.retainAll(b); } }
        |""".stripMargin, new CollectionsTransform)
    // §4.56's fast-path rule: a test written for the shape in front of you answers for that shape
    // and silently declines for every one added since. `Leaf` is re-parented exactly as much as
    // `Base` is — through it.
    assertEmits(p, "a.retainAll(balticporter.runtime.JavaCollection.fromSet(b))")
  }

  test("NEGATIVE — a class that ALREADY carries the wanted shim conforms and is not wrapped") {
    val p = port(
      """package demo;
        |import java.util.*;
        |interface Feed<E> extends Collection<E> { }
        |class Sink { void go(Collection<String> c, Feed<String> f) { c.addAll(f); } }
        |""".stripMargin, new CollectionsTransform)
    // `Feed` is emitted `extends JavaCollection[E]`, so it IS the slot's type. A wrap here would be
    // a factory call around a value that already conforms — the over-approximation `CLAUDE.md` §5
    // has no instrument for.
    assertNotEmits(p, "JavaCollection.from(f)")
    assertNotEmits(p, "JavaCollection.fromSet(f)")
  }

  test("NEGATIVE — a class with NO mapped parent is not a party to the edge, and keeps its error") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Loose<E> { }
        |class Sink { void go(Collection<String> c, Loose<String> l) { c.add(l); } }
        |""".stripMargin, new CollectionsTransform)
    // nothing re-parented `Loose`, so the phase has no standing to say what it is (§4.56) and
    // guessing a factory would be a wrap that cannot compile.
    assertNotEmits(p, "JavaCollection.from(l)")
    assertNotEmits(p, "JavaCollection.fromSet(l)")
  }
