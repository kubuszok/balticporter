package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.CollectionsTransform

/** Java's UNTYPED PROBE — `Map.get`, `Map.containsKey`, `Map.remove`, `Collection.contains` and
  * `Set.remove` all take an `Object`, and the retyping moves the receiver to a scala collection
  * whose members are typed at the element.
  *
  * That the formal is `Object` is java's CONTRACT and not an accident of erasure: the lookup is by
  * VALUE, so a probe of an unrelated type is meant to miss rather than to fail to compile. There are
  * two ways such a probe reaches the slot and they look identical in the emitted text:
  *
  *   - a class that IMPLEMENTS `java.util.Map<String, T>` must DECLARE `remove(Object o)` and
  *     delegate to its retyped field — java's own parameter, with nothing to strip;
  *   - the frontend's erasure coercion (`typeParamToObject`, `ENGINE-LIMITS.md` G14) widened a
  *     type-parameter key to `Object` because that is what java's formal said. The mint is right for
  *     a call to a java `Map`; this phase moving the receiver is what invalidated it, which is
  *     `keyArg`'s own argument at the coercion `keyArg` cannot strip.
  *
  * ==Why a HELPER and not a cast==
  * `o.asInstanceOf[String]` is the translation that compiles and means something else: it inserts a
  * `checkcast` and throws `ClassCastException` where java's `map.get(anInteger)` answers `null`
  * (CLAUDE.md §4.4). The helpers widen the PROBE POSITION, which is erased, so java's own
  * `hashCode`/`equals` lookup runs and a wrong-typed probe misses exactly as java's does.
  *
  * ==And the guard is the one question a phase can answer with NO conformance oracle==
  * `java.lang.Object` is the TOP of java's reference hierarchy, so an argument at that type conforms
  * to a scala element type only where the element type is `Object` too. Everything else declines —
  * which is what the negatives below pin (`ENGINE-LIMITS.md` K24).
  */
class CollectionsObjectProbeSpec extends PortSuite:

  /** face 1 — the IMPLEMENTING side. `Ledger` is java's `Map<String, T>`, so its own signatures are
    * java's erased ones and every body delegates to a field this phase retyped. */
  private val implementing =
    """package demo;
      |import java.util.*;
      |class Ledger<T> implements Map<String, T> {
      |  private final HashMap<String, T> slots = new HashMap<String, T>();
      |  public T get(Object o)              { return slots.get(o); }
      |  public T remove(Object o)           { return slots.remove(o); }
      |  public boolean containsKey(Object o){ return slots.containsKey(o); }
      |  public int size()                   { return slots.size(); }
      |  public boolean isEmpty()            { return slots.isEmpty(); }
      |  public boolean containsValue(Object o) { return slots.containsValue(o); }
      |  public T put(String k, T v)         { return slots.put(k, v); }
      |  public void putAll(Map<? extends String, ? extends T> m) { slots.putAll(m); }
      |  public void clear()                 { slots.clear(); }
      |  public Set<String> keySet()         { return slots.keySet(); }
      |  public Collection<T> values()       { return slots.values(); }
      |  public Set<Map.Entry<String, T>> entrySet() { return slots.entrySet(); }
      |}
      |""".stripMargin

  /** face 2 — the FRONTEND's coercion, at a call whose receiver is an ordinary retyped map. The key
    * is a type parameter, so `typeParamToObject` widens it off `Map.get(Object)`'s declared formal.
    *
    * …and face 3 beside it, the SET half: `Set.remove`/`contains` are declared over `Object` for the
    * same reason `Map.get` is. */
  private val coerced =
    """package demo;
      |import java.util.*;
      |class Probes {
      |  <P> String lookup(Map<String, String> m, P probe)   { return m.get(probe); }
      |  <P> String drop(Map<String, String> m, P probe)     { return m.remove(probe); }
      |  <P> boolean has(Map<String, String> m, P probe)     { return m.containsKey(probe); }
      |  <P> boolean holds(Set<String> s, P probe)           { return s.contains(probe); }
      |  <P> boolean discard(Set<String> s, P probe)         { return s.remove(probe); }
      |}
      |""".stripMargin

  test("face 1 — a class IMPLEMENTING `java.util.Map` delegates java's own `Object` parameter") {
    val p = port(implementing, new CollectionsTransform)
    // the parameter really IS `Object`, so there is no coercion to strip and the ordinary rewrite
    // would emit `slots.getOrElse(o, …)` against a `Map[String, T]`: Found `Object`, Required
    // `String`. The three helpers take the probe as `Any` and never name the key type.
    assertEmits(p, "balticporter.runtime.JavaCollections.mapGet(this.slots, o)")
    assertEmits(p, "balticporter.runtime.JavaCollections.mapRemove(this.slots, o)")
    assertEmits(p, "balticporter.runtime.JavaCollections.mapContainsKey(this.slots, o)")
    // …and nothing survives naming a member that would narrow the probe.
    assertNotEmits(p, "this.slots.getOrElse(o")
    assertNotEmits(p, "this.slots.contains(o)")
  }

  test("face 2 — the FRONTEND's `Object` coercion at a type-parameter key takes the same helpers") {
    val p = port(coerced, new CollectionsTransform)
    // `keyArg` runs first and cannot strip this one: what the cast wraps is a `P`, and the map's key
    // type is `String`. The mint is G14-correct for a call to a java `Map`; the phase that moved the
    // receiver is the one that owes the answer (CLAUDE.md §4.56).
    assertEmits(p, "balticporter.runtime.JavaCollections.mapGet(m, probe.asInstanceOf[java.lang.Object])")
    assertEmits(p, "balticporter.runtime.JavaCollections.mapRemove(m, probe.asInstanceOf[java.lang.Object])")
    assertEmits(p, "balticporter.runtime.JavaCollections.mapContainsKey(m, probe.asInstanceOf[java.lang.Object])")
  }

  test("face 3 — the SET half, `contains` and `remove`, and `remove` answers java's `boolean`") {
    val p = port(coerced, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.setContains(s, probe.asInstanceOf[java.lang.Object])")
    assertEmits(p, "balticporter.runtime.JavaCollections.setRemove(s, probe.asInstanceOf[java.lang.Object])")
    // `-=` answers the RECEIVER, so it cannot be what a `boolean`-returning java call becomes.
    assertNotEmits(p, "s -= probe")
  }

  test("NEGATIVE — an ordinary key is untouched: `keyArg` strips, or there was nothing to strip") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Plain {
        |  String byName(Map<String, String> m)    { return m.get("alpha"); }
        |  boolean hasName(Map<String, String> m)  { return m.containsKey("alpha"); }
        |  boolean holds(Set<String> s, String e)  { return s.contains(e); }
        |  boolean drops(Set<String> s, String e)  { return s.remove(e); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // the whole point of the guard: a probe that FITS goes on taking the ordinary rewrite, so the
    // emitted text of every port that has no such seam is byte-for-byte what it was.
    assertEmits(p, "m.getOrElse(\"alpha\"")
    assertEmits(p, "m.contains(\"alpha\")")
    assertEmits(p, "s.contains(e)")
    assertEmits(p, "s -= e")
    assertNotEmits(p, "JavaCollections.mapGet(")
    assertNotEmits(p, "JavaCollections.setContains(")
    assertNotEmits(p, "JavaCollections.setRemove(")
  }

  test("NEGATIVE — a key type that IS `Object` needs no helper, because the probe already fits") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Anywhere {
        |  Object at(Map<Object, Object> m, Object o) { return m.get(o); }
        |  boolean held(Set<Object> s, Object o)      { return s.contains(o); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // `Object` at an `Object` slot conforms, so widening it would be a helper call for nothing —
    // and the guard asks exactly that question rather than "is the argument an `Object`".
    assertNotEmits(p, "JavaCollections.mapGet(")
    assertNotEmits(p, "JavaCollections.setContains(")
  }

  test("NEGATIVE — the same member names on a receiver this phase did NOT retype are untouched") {
    val p = port(
      """package demo;
        |class Roster {
        |  boolean contains(Object o) { return false; }
        |  Object get(Object o)       { return null; }
        |}
        |class UseRoster {
        |  boolean ask(Roster r, Object o) { return r.contains(o); }
        |  Object fetch(Roster r, Object o) { return r.get(o); }
        |}
        |""".stripMargin, new CollectionsTransform)
    // §4.56: the arms are keyed on the receiver's KIND, which is this phase's own record. A
    // library's own `contains(Object)` is not a JDK member and the probe question never arises.
    assertEmits(p, "r.contains(o)")
    assertEmits(p, "r.get(o)")
    assertNotEmits(p, "JavaCollections.setContains(")
    assertNotEmits(p, "JavaCollections.mapGet(")
  }
