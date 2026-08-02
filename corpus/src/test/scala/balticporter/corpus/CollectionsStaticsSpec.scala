package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** The seams of `CollectionsTransform` that `CollectionsTransformSpec` does not reach — the
  * `staticRewrite` TABLE, the copy constructor, the blanket shim refusal and the per-kind rewrites
  * that had no spec at all.
  *
  * Written because an inventory of the transform against its tests found 17 of 29 seams unasserted,
  * including every entry of the static-utility table. A rewrite with no spec is a rewrite that can
  * be deleted, mis-keyed or silently stop matching with nothing to say so — and the entire table is
  * keyed on `owner#name` strings the frontend produces, which is exactly the shape that stops
  * matching when a frontend changes (`CollectionsTransform.collapsed`'s doc says so about the
  * collapse; it is no less true of the rest).
  *
  * These pin EMISSION. The behaviour of what they emit is pinned in `runtime/src/test`.
  */
class CollectionsStaticsSpec extends PortSuite:

  // -------------------------------------------------------------------------------------------
  // java.util.Collections / Arrays / Map.Entry — the receiver-less utilities
  // -------------------------------------------------------------------------------------------

  private val statics =
    """package demo;
      |import java.util.*;
      |class St {
      |  void sortCmp(List<String> xs, Comparator<String> c) { Collections.sort(xs, c); }
      |  void sortNat(List<String> xs)                       { Collections.sort(xs); }
      |  void rev(List<String> xs)                           { Collections.reverse(xs); }
      |  void shuf(List<String> xs, Random r)                { Collections.shuffle(xs, r); }
      |  void sw(List<?> xs, int i, int j)                   { Collections.swap(xs, i, j); }
      |  Collection<String> unmod(Collection<String> c)      { return Collections.unmodifiableCollection(c); }
      |  Comparator<Map.Entry<String,Integer>> byKey(Comparator<String> c)  { return Map.Entry.comparingByKey(c); }
      |  Comparator<Map.Entry<String,Integer>> byVal(Comparator<Integer> c) { return Map.Entry.comparingByValue(c); }
      |}
      |""".stripMargin

  test("every `java.util.Collections` static in the table rewrites onto the runtime object") {
    val p = port(statics, new CollectionsTransform)
    // IN PLACE, both of them — java mutates the argument and returns nothing, and a sorted COPY
    // would leave every caller reading the original order (§4.4, no compile error).
    assertEmits(p, "balticporter.runtime.JavaCollections.sort(xs, c)")
    // the natural-ordering overload is a DIFFERENT helper: scala needs an `Ordering` where java
    // resolves through `Comparable`, so the arity is the discriminator and the target is not `sort`.
    assertEmits(p, "balticporter.runtime.JavaCollections.sortNatural(xs)")
    assertEmits(p, "balticporter.runtime.JavaCollections.reverse(xs)")
    assertEmits(p, "balticporter.runtime.JavaCollections.shuffle(xs, r)")
    // `swap` through a WILDCARD list, which is the shape it is actually written in: java's own
    // signature is `swap(List<?>, int, int)` and jbump's `Collisions.keySort` calls it that way.
    assertEmits(p, "balticporter.runtime.JavaCollections.swap(xs, i, j)")
    // …and nothing survives naming the JDK class, which is how these reached the compiler before
    // the table existed (`Required: java.util.List[T]` against a `Buffer` the port produced).
    assertNotEmits(p, "java.util.Collections.")
  }

  test("the whole `unmodifiable*` family maps — each onto a read-only VIEW, never a copy") {
    val p = port(statics, new CollectionsTransform)
    // `Collection` goes to the SHIM's own `unmodifiable`, because a `Collection`-typed slot is a
    // shim slot; the other three go to the runtime's `Frozen*` views of the retyped scala shape.
    assertEmits(p, "balticporter.runtime.JavaCollection.unmodifiable(c)")
    val p2 = port(
      """package demo;
        |import java.util.*;
        |class N {
        |  List<String> rl(List<String> xs)          { return Collections.unmodifiableList(xs); }
        |  Set<String> rs(Set<String> s)             { return Collections.unmodifiableSet(s); }
        |  Map<String, Integer> rm(Map<String, Integer> m) { return Collections.unmodifiableMap(m); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p2, "balticporter.runtime.JavaCollections.unmodifiableList(xs)")
    assertEmits(p2, "balticporter.runtime.JavaCollections.unmodifiableSet(s)")
    assertEmits(p2, "balticporter.runtime.JavaCollections.unmodifiableMap(m)")
    assertNotEmits(p2, "java.util.Collections.")
  }

  test("the IMMUTABLE producers rewrite onto helpers that KEEP the immutability") {
    // The PRODUCER direction of the retype: a value the JDK hands back at a slot this phase moved.
    // Nothing coerces it and nothing can — the JDK object is not a scala collection — so the
    // rewrite has to produce the scala value in the first place.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Empty {
        |  List<String> el()                 { return Collections.emptyList(); }
        |  Map<String, Integer> em()         { return Collections.emptyMap(); }
        |  Set<String> es()                  { return Collections.emptySet(); }
        |  List<String> sl(String x)         { return Collections.singletonList(x); }
        |  Set<String> ss(String x)          { return Collections.singleton(x); }
        |  Map<String, Integer> sm(String k, Integer v) { return Collections.singletonMap(k, v); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "balticporter.runtime.JavaCollections.emptyList()")
    assertEmits(p, "balticporter.runtime.JavaCollections.emptyMap()")
    assertEmits(p, "balticporter.runtime.JavaCollections.emptySet()")
    assertEmits(p, "balticporter.runtime.JavaCollections.singletonList(x)")
    assertEmits(p, "balticporter.runtime.JavaCollections.singleton(x)")
    assertEmits(p, "balticporter.runtime.JavaCollections.singletonMap(k, v)")
    assertNotEmits(p, "java.util.Collections.")
  }

  test("`Map.Entry`'s statics come along, because `Map.Entry` became a `Tuple2`") {
    // Without these the call survives to the compiler naming `java.util.Map.Entry` — a type the
    // port no longer produces anywhere.
    val p = port(statics, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.comparingByKey(c)")
    assertEmits(p, "balticporter.runtime.JavaCollections.comparingByValue(c)")
    assertNotEmits(p, "java.util.Map.Entry.comparing")
  }

  // -------------------------------------------------------------------------------------------
  // java.util.stream — the links of the chain the collapse spec does not cover
  // -------------------------------------------------------------------------------------------

  private val streams =
    """package demo;
      |import java.util.*;
      |import java.util.stream.*;
      |class Sm {
      |  List<String> sorted(List<String> xs, Comparator<String> c) { return xs.stream().sorted(c).collect(Collectors.toList()); }
      |  double total(List<String> xs)     { return xs.stream().mapToDouble(s -> s.length()).sum(); }
      |  List<Integer> range(int n)        { return IntStream.range(0, n).mapToObj(i -> i).collect(Collectors.toList()); }
      |  Collection<String> into(List<String> xs) { return xs.stream().collect(Collectors.toCollection(ArrayList::new)); }
      |}
      |""".stripMargin

  test("`Stream.sorted(cmp)` is `sortedWith` — a NAME, because scala's `sorted` takes an Ordering") {
    val p = port(streams, new CollectionsTransform)
    // Left unmapped, `sorted(cmp)` binds to scala's own `Buffer.sorted` and fails with `Required:
    // Ordering[…]` — an error naming neither streams nor comparators. Had the element types lined
    // up it would have been worse: a silently different order.
    assertEmits(p, "balticporter.runtime.JavaCollections.sortedWith(")
    assertNotEmits(p, ".sorted(c)")
  }

  test("`mapToDouble(f).sum()` — the WIDENING is why it is a named helper and not a bare `.map`") {
    val p = port(streams, new CollectionsTransform)
    // `A => Double` makes scala insert the widening java's `ToDoubleFunction` performs; left as
    // `.map(f).sum` a `float`-returning lambda sums in FLOAT, which passes a tolerance assertion
    // until the collection is large enough.
    assertEmits(p, "balticporter.runtime.JavaCollections.mapToDouble(")
    // `sum` is scala's own name and PARENLESS, so it is a `Select`, not an `Apply`.
    assertEmitsMatch(p, """mapToDouble\([^\n]*\)\.sum""")
    assertNotEmits(p, ".sum()")
  }

  test("`IntStream.range` is a stream SOURCE nothing can collapse, so it has its own arm") {
    val p = port(streams, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.intRange(0, n)")
    // …and the chain proceeds normally from it: `mapToObj` collapses onto the range's `map`.
    assertEmitsMatch(p, """intRange\(0, n\)\.map""")
    assertNotEmits(p, "java.util.stream.IntStream")
  }

  test("`Collectors.toCollection(f)` is `into` — the target is read out of the collector's factory") {
    val p = port(streams, new CollectionsTransform)
    // The one terminal that cannot end at the receiver the way `toList` does: its target lives
    // INSIDE the collector, as a factory. `toSet`/`toMap` are still unmapped on purpose.
    assertEmits(p, "balticporter.runtime.JavaCollections.into(")
    assertNotEmits(p, "java.util.stream.Collectors.toCollection")
  }

  // -------------------------------------------------------------------------------------------
  // the COPY CONSTRUCTOR — two java constructors, one scala constructor
  // -------------------------------------------------------------------------------------------

  private val copies =
    """package demo;
      |import java.util.*;
      |class Cp {
      |  List<String> copy(List<String> xs)               { return new ArrayList<String>(xs); }
      |  Set<String> copySet(Set<String> s)               { return new HashSet<String>(s); }
      |  Map<String,String> copyMap(Map<String,String> m) { return new HashMap<String,String>(m); }
      |  List<String> capacity()                          { return new ArrayList<String>(10); }
      |  List<String> empty()                             { return new ArrayList<String>(); }
      |}
      |""".stripMargin

  test("a collection ARGUMENT is a COPY and goes through the companion's `from`") {
    val p = port(copies, new CollectionsTransform)
    assertEmits(p, "scala.collection.mutable.ArrayBuffer.from(xs)")
    assertEmits(p, "scala.collection.mutable.HashSet.from(s)")
    assertEmits(p, "scala.collection.mutable.HashMap.from(m)")
    // the `from` is the TARGET's, not the argument's: `new HashMap<>(aTreeMap)` is a `HashMap`.
    assertNotEmits(p, "new scala.collection.mutable.ArrayBuffer[java.lang.String](xs)")
  }

  test("an INT argument is a capacity hint and maps by accident — it must stay a constructor") {
    val p = port(copies, new CollectionsTransform)
    // `new ArrayBuffer(10)` means what `new ArrayList<>(10)` means; only the collection-argument
    // form needed a rule, which is what makes the gate "is the argument a collection".
    assertEmits(p, "new scala.collection.mutable.ArrayBuffer[java.lang.String](10)")
    assertNotEmits(p, "ArrayBuffer.from(10)")
  }

  // -------------------------------------------------------------------------------------------
  // the BLANKET shim refusal, and its one exception
  // -------------------------------------------------------------------------------------------

  private val onShim =
    """package demo;
      |import java.util.*;
      |import java.util.function.Consumer;
      |class Sh {
      |  void addTo(Collection<String> c, String s)   { c.add(s); }
      |  void addAllTo(Collection<String> c, Collection<String> d) { c.addAll(d); }
      |  int size(Collection<String> c)               { return c.size(); }
      |  void each(Collection<String> c, Consumer<String> f) { c.forEach(f); }
      |  boolean has(Collection<String> c, String s)  { return c.contains(s); }
      |}
      |""".stripMargin

  test("EVERY scala-shaped rewrite declines on a SHIM receiver — it is a blanket refusal, not a list") {
    val p = port(onShim, new CollectionsTransform)
    // The shims carry JAVA's arity and java's own member names. Guarding per rewrite failed twice:
    // `add`/`addAll` still became `+=`/`++=` against a `JavaCollection` that has neither.
    assertEmits(p, "c.add(s)")
    assertNotEmits(p, "c += s")
    // (the argument carries the emitter's cast to the declared `? <: A` formal; what this pins is
    // that the CALL is still `addAll`.)
    assertEmits(p, "c.addAll(d")
    assertNotEmits(p, "c ++= d")
    // …and `parenless` must not strip `()` there either: `it.hasNext` against `def hasNext()` was
    // 24 measured errors.
    assertEmits(p, "c.size()")
    assertNotEmits(p, "return c.size\n")
  }

  test("`forEach` is the ONE exception, and it is listed ABOVE the guard so it cannot be added by omission") {
    val p = port(onShim, new CollectionsTransform)
    // java 8's `forEach(Consumer)` has no counterpart on the shim itself — `JavaIterable` supplies
    // `foreach` as an EXTENSION, which is the whole point of the family (§4.5). Left alone this is
    // a call to a member that does not exist.
    assertEmits(p, "c.foreach(f)")
    assertNotEmits(p, "c.forEach(")
  }

  // -------------------------------------------------------------------------------------------
  // the per-kind rewrites with no spec: iterator, values, entrySet, deque ends, parenless
  // -------------------------------------------------------------------------------------------

  private val kinds =
    """package demo;
      |import java.util.*;
      |class Kd {
      |  Iterator<String> it(List<String> xs)          { return xs.iterator(); }
      |  Collection<String> vals(Map<String,String> m) { return m.values(); }
      |  Set<String> keys(Map<String,String> m)        { return m.keySet(); }
      |  int size(List<String> xs)                     { return xs.size(); }
      |  String poll(Queue<String> q)                  { return q.poll(); }
      |  String peek(Queue<String> q)                  { return q.peek(); }
      |  void first(Deque<String> d, String s)         { d.addFirst(s); }
      |  void last(Deque<String> d, String s)          { d.addLast(s); }
      |  String prev(Map<String,String> m, String k)   { return m.put(k, "v"); }
      |  String gone(Map<String,String> m, String k)   { return m.remove(k); }
      |}
      |""".stripMargin

  test("`iterator()` on a scala collection is wrapped — the DECLARATION asks for the removal-capable shim") {
    val p = port(kinds, new CollectionsTransform)
    // Invisible in the TIR (both sides read as the shim; only the emitted scala disagrees), so it
    // is decided on PROVENANCE: a scala collection's iterator is a scala one. Widening is free and
    // `remove()` correctly throws, since this iterator has no removal to offer.
    assertEmits(p, "balticporter.runtime.JavaIterator.from(xs.iterator)")
    assertNotEmits(p, "return xs.iterator\n")
  }

  test("`Map.values()` is wrapped READ-ONLY — java's view rejects `add`, so that is java's behaviour") {
    val p = port(kinds, new CollectionsTransform)
    // The node's `tpe` is the retyped `Collection<V>`, so it CLAIMS to be a shim and `coerce`
    // correctly declines; wrapping here restores "a node describes the expression it emits".
    assertEmits(p, "balticporter.runtime.JavaCollection.unmodifiableFrom(m.values)")
  }

  test("`parenless` strips `()` on a scala receiver — `size`, `keySet` and the rest") {
    val p = port(kinds, new CollectionsTransform)
    assertEmits(p, "m.keySet")
    assertNotEmits(p, "m.keySet()")
    assertEmits(p, "xs.size")
    assertNotEmits(p, "xs.size()")
  }

  test("`poll`/`peek` go through an Option — java returns NULL on empty where scala's THROW") {
    val p = port(kinds, new CollectionsTransform)
    // A direct mapping turns "the queue was empty" into an exception: a behavioural change with no
    // compile error. `orNull` is PARAMETERLESS (implicit `Null <:< A`), so it is a `Select`.
    assertEmits(p, "q.removeHeadOption().orNull")
    assertEmits(p, "q.headOption.orNull")
    assertNotEmits(p, "q.poll")
    assertNotEmits(p, "q.peek")
  }

  test("a deque's two ENDS keep their ends") {
    val p = port(kinds, new CollectionsTransform)
    assertEmits(p, "d.prepend(s)")
    assertEmits(p, "d += s")
  }

  test("`Map.put`/`remove` RETURN THE PREVIOUS VALUE — `update`/`-=` discard it") {
    val p = port(kinds, new CollectionsTransform)
    // `if (map.put(k, v) != null)` became a comparison against `Unit` at every site. Scala's own
    // `put`/`remove` keep it as an `Option`, so `getOrElse(null: V)` restores java's contract.
    assertEmits(p, """m.put(k, "v").getOrElse(null.asInstanceOf[""")
    assertEmits(p, "m.remove(k).getOrElse(null.asInstanceOf[")
    assertNotEmits(p, "m.update(k,")
  }
