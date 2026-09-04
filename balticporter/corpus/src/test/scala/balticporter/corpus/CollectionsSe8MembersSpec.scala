package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** SE8's DEFAULT METHODS on `List`, `Map` and `Collection` — the members every library written since
  * 2014 uses as readily as `get`, and which the tables did not have. */
class CollectionsSe8MembersSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |import java.util.function.*;
      |class Se8 {
      |  void sortList(List<String> xs, Comparator<String> c) { xs.sort(c); }
      |  List<String> compute(Map<String, List<String>> m, String k) {
      |    return m.computeIfAbsent(k, (key) -> new ArrayList<String>());
      |  }
      |  List<String> computeVal(Map<String, List<String>> m, String k, Function<String, List<String>> f) {
      |    return m.computeIfAbsent(k, f);
      |  }
      |  boolean dropList(List<String> xs)          { return xs.removeIf((s) -> s.isEmpty()); }
      |  boolean dropSet(Set<String> xs)            { return xs.removeIf((s) -> s.isEmpty()); }
      |  boolean hasVal(Map<String, String> m, Object o)   { return m.containsValue(o); }
      |  boolean hasAll(List<String> xs, Collection<String> c) { return xs.containsAll(c); }
      |  boolean dropAll(List<String> xs, Collection<String> c)  { return xs.removeAll(c); }
      |  boolean keepAll(List<String> xs, Collection<String> c)  { return xs.retainAll(c); }
      |  boolean dropAllSet(Set<String> xs, Collection<String> c) { return xs.removeAll(c); }
      |  boolean keepAllSet(Set<String> xs, Collection<String> c) { return xs.retainAll(c); }
      |  void reserve(ArrayList<String> xs, int n)  { xs.ensureCapacity(n); }
      |}
      |""".stripMargin

  test("`List.sort` reaches the SAME helper the `Collections.sort` static does") {
    val p = port(src, new CollectionsTransform)
    // one helper for both, because SE8 made the static delegate to the member — java's own
    // definition, so two entries would be two things to keep in step over one behaviour.
    // the comparator arrives ASCRIBED to java's own wildcard formal — the frontend's coercion off
    // `List.sort(Comparator<? super E>)` — which is exactly the shape the helper's signature was
    // written to take, and the reason it is `Comparator[? >: A]` rather than `Comparator[A]`.
    assertEmits(p, "balticporter.runtime.JavaCollections.sort(xs, c.asInstanceOf[java.util.Comparator[? >: java.lang.String]])")
    assertNotEmits(p, "xs.sort(")
  }

  test("`Map.computeIfAbsent` maps — with a LAMBDA and with a `Function` the caller was handed") {
    val p = port(src, new CollectionsTransform)
    // NOT `getOrElseUpdate`: java treats a key mapped to `null` as ABSENT and records nothing when
    // the factory answers `null`. Both are silent at a green compile.
    // the LAMBDA form: scalac SAM-converts it at the wildcard-applied formal.
    assertEmits(p, "balticporter.runtime.JavaCollections.computeIfAbsent(m, k, (key: java.lang.String) =>")
    // …and the VALUE form, which a `K => V` formal would have rejected: flexmark's
    // `Parsing.getCachedPattern` takes the factory as a parameter and forwards it, and the frontend
    // ascribes it to java's own `Function<? super K, ? extends V>` on the way.
    assertEmits(p, "computeIfAbsent(m, k, f.asInstanceOf[java.util.function.Function[? >: java.lang.String,")
    assertNotEmits(p, ".computeIfAbsent(k")
  }

  test("`removeIf` picks by the receiver's KIND — a list helper and a set helper, never an overload") {
    val p = port(src, new CollectionsTransform)
    // two names because the two erase alike, and because the emitted call should say which one it
    // meant rather than leaving it to a run-time dispatch.
    assertEmits(p, "balticporter.runtime.JavaCollections.removeIf(xs,")
    assertEmits(p, "balticporter.runtime.JavaCollections.removeIfSet(xs,")
    assertNotEmits(p, "xs.removeIf(")
  }

  test("`containsValue`/`containsAll` map — the two whose equality DIRECTION is java's") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.containsValue(m, o)")
    assertEmits(p, "balticporter.runtime.JavaCollections.containsAll(xs, c)")
    assertNotEmits(p, "m.containsValue(")
  }

  test("`removeAll`/`retainAll` map at BOTH kinds — the two bulk mutators that had no arm") {
    val p = port(src, new CollectionsTransform)
    // ONE helper per member across both kinds, unlike `removeIf`: the receiver contract these take
    // is java's own `Collection` contract, which a `Buffer` and a `Set` both satisfy, so there is
    // nothing for a second name to disambiguate. What they must NOT become is the nearest scala
    // member: `--=` is `subtractAll`, ONE occurrence per element of the argument where java removes
    // every occurrence, and `filterInPlace` keeps the complement and answers the collection.
    assertEmits(p, "balticporter.runtime.JavaCollections.removeAll(xs, c)")
    assertEmits(p, "balticporter.runtime.JavaCollections.retainAll(xs, c)")
    assertNotEmits(p, "xs --= c")
    assertNotEmits(p, "xs.filterInPlace(")
    assertNotEmits(p, "xs.removeAll(c)")
    assertNotEmits(p, "xs.retainAll(c)")
  }

  test("`ensureCapacity` maps — the one member here with NO observable java behaviour") {
    val p = port(src, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.ensureCapacity(xs, n)")
  }

  test("NEGATIVE — the same member names on a receiver the phase did NOT retype are untouched") {
    // §4.56: the arms are keyed on the receiver's KIND, so a library's own `sort`/`removeIf` is not
    // a JDK member and must survive verbatim. Without the phase there is no kind at all, which is
    // the same question asked the other way.
    val p = port(
      """package demo2;
        |class Own {
        |  static class Bag { void sort(Object c) {} boolean removeIf(Object p) { return false; }
        |                     boolean removeAll(Object c) { return false; }
        |                     boolean retainAll(Object c) { return false; } }
        |  void use(Bag b) { b.sort(null); b.removeIf(null); b.removeAll(null); b.retainAll(null); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "b.sort(")
    assertEmits(p, "b.removeIf(")
    assertEmits(p, "b.removeAll(")
    assertEmits(p, "b.retainAll(")
    assertNotEmits(p, "JavaCollections.sort(b")
    assertNotEmits(p, "JavaCollections.removeAll(b")
    assertNotEmits(p, "JavaCollections.retainAll(b")
  }

  test("REFUSED and CITED — `spliterator` is a protocol, and `listIterator` turned out not to be") {
    // The refusal is DATA (`JdkSurfaceCheck.Refusals`) rather than an absent arm, so a reader who
    // meets the compile error finds the reason and its citation instead of a wall. A refusal that
    // exists only as a missing `case` is indistinguishable from a mapping nobody has written yet.
    val refused = balticporter.tir.JdkSurfaceCheck.Refusals.map(_.api).toSet
    assert(!clue(refused).contains("java.util.List#listIterator"),
           "the `listIterator` refusal is STALE — the phase answers for it (ENGINE-LIMITS K23)")
    assert(!clue(refused).contains("java.util.List#spliterator"),
           "the `List#spliterator` refusal is STALE — the phase answers for it (ENGINE-LIMITS K23)")
    assert(!clue(refused).contains("java.util.Set#spliterator"),
           "the `Set#spliterator` refusal is STALE — the phase answers for it (ENGINE-LIMITS K23)")
    assert(clue(refused).contains("java.util.Collection#spliterator"))
    val why = balticporter.tir.JdkSurfaceCheck.Refusals.filter(_.api.endsWith("#spliterator"))
    assert(why.forall(_.cite.contains("K23")))
    assert(why.forall(_.why.contains("PARALLEL-DECOMPOSITION")))
  }
