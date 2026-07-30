package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Pipeline, UsageKind}
import balticporter.transform.CollectionsTransform

/** The java→scala collections transform: retypes every collection occurrence and rewrites
  * the common call shapes, whole-program and symbol-driven. Asserts both the xref (the old
  * type is vacated, the new one inherits its positions) and the emitted Scala. */
class CollectionsTransformSpec extends PortSuite:

  private val src =
    """package demo;
      |import java.util.*;
      |class Bag {
      |  private List<String> items = new ArrayList<String>();
      |  private Map<String, Integer> counts = new HashMap<String, Integer>();
      |  private Set<String> seen = new HashSet<String>();
      |  void add(String s) { items.add(s); counts.put(s, items.size()); seen.add(s); }
      |  String first() { return items.get(0); }
      |  Integer count(String s) { return counts.get(s); }
      |  void bump(String s) { counts.put(s, counts.getOrDefault(s, 0) + 1); }
      |  boolean known(String s) { return counts.containsKey(s) && seen.contains(s); }
      |  void drop(String s) { seen.remove(s); counts.remove(s); }
      |  void merge(List<String> more) { items.addAll(more); }
      |  boolean empty() { return items.isEmpty(); }
      |  void each() { for (String s : items) { first(); } }
      |}
      |""".stripMargin

  private val before = SpoonTir.fromSource(src)
  private val after  = Pipeline.run(before, List(new CollectionsTransform))
  private val out    = new TirEmitter(after).emit

  private def id(p: balticporter.tir.Program, full: String) =
    p.symbols.all.find(_.fullName == full).map(_.id)

  test("retypes every java.util.List occurrence to scala Buffer (whole-program)") {
    val listId   = id(before, "java.util.List").getOrElse(fail("no java.util.List"))
    // before: java.util.List is used (field type, type arg positions); after: vacated.
    assert(before.usagesOf(listId).nonEmpty)
    assertEquals(after.usagesOf(listId), Nil)
    // the scala Buffer symbol now carries usages.
    val bufId = id(after, "scala.collection.mutable.Buffer").getOrElse(fail("no Buffer symbol"))
    assert(after.usages(bufId).map(_.kind).contains(UsageKind.Tycon))
  }

  test("emits scala collection types and kind-aware rewritten calls") {
    assert(clue(out).contains("scala.collection.mutable.Buffer[java.lang.String]"))
    assert(out.contains("new scala.collection.mutable.ArrayBuffer["))
    assert(out.contains("scala.collection.mutable.HashMap["))
    assert(out.contains("scala.collection.mutable.HashSet["))
    assert(out.contains("this.items += s"))          // List.add     -> +=
    assert(out.contains("this.seen += s"))           // Set.add      -> +=
    // `Map.put` maps to scala's `put`, NOT `update`: java's returns the PREVIOUS value and
    // `update` returns Unit, so `if (map.put(k, v) != null)` became a comparison against Unit at
    // every site. This assertion tracked the superseded `update` shape and had been red since that
    // fix landed — a red engine test is a gate that has stopped reporting.
    assert(clue(out).contains("this.counts.put(s,"))       // Map.put -> put(_, _).getOrElse(null)
    assert(out.contains("this.items(0)"))            // List.get(i)  -> apply
    assert(out.contains("this.counts.getOrElse(s, null.asInstanceOf["))   // Map.get -> getOrElse(_, null: V)
    assert(out.contains("this.counts.getOrElse(s, 0.asInstanceOf["))      // getOrDefault -> getOrElse(_, d: V)
    assert(out.contains("this.counts.contains(s)"))  // containsKey  -> contains
    assert(out.contains("this.seen -= s"))           // Set.remove   -> -=
    // same reason as `put` above: java's `Map.remove` RETURNS the removed value, which `-=`
    // discards, so it maps to scala's `remove(_).getOrElse(null)`.
    assert(clue(out).contains("this.counts.remove(s)"))    // Map.remove -> remove(_).getOrElse(null)
    assert(out.contains("this.items ++= more"))      // addAll       -> ++=
    assert(out.contains("this.items.isEmpty\n") || out.contains("this.items.isEmpty "))  // drop ()
    assert(out.contains("for (s <- this.items)"))    // for-each over retyped collection
    assert(!out.contains("java.util."))              // nothing left un-migrated
  }

  // ---------------------------------------------------------------------------------------------
  // A CAST across the shim boundary. Both halves of one rule, and they must be tested together:
  // the phase may drop a cast ONLY when it has itself retyped the source out of the shim family.
  // Deciding that from the source type's NAME (`fullName.startsWith("java.")`) swept up
  // `java.lang.Object` and deleted a downcast that is correct — CLAUDE.md §4.56, met in a phase
  // that is not a renamer.
  // ---------------------------------------------------------------------------------------------

  // ---------------------------------------------------------------------------------------------
  // `List.remove` — java's TWO one-argument overloads, which do opposite things. Scala's `Buffer`
  // has only the index one, and `Integer2int` makes the by-VALUE call compile as index removal
  // (CLAUDE.md §4.4: valid scala meaning something else, no count moved). Verified against a real
  // run: `[10, 11, 12].remove(Integer.valueOf(1))` removes nothing in java and removed `11` here.
  // ---------------------------------------------------------------------------------------------

  private val removes =
    """package demo;
      |import java.util.*;
      |class R {
      |  void discard(List<Integer> xs)      { xs.remove(Integer.valueOf(1)); }
      |  boolean used(List<Integer> xs)      { return xs.remove(Integer.valueOf(1)); }
      |  void other(List<String> ss, String s) { ss.remove(s); }
      |  void index(List<String> ss)         { ss.remove(0); }
      |  String indexUsed(List<String> ss)   { return ss.remove(0); }
      |  void boolIndex(List<Boolean> bs)    { bs.remove(0); }
      |  void dequeValue(ArrayDeque<Integer> q) { q.remove(Integer.valueOf(3)); }
      |}
      |""".stripMargin

  test("List.remove(Object) is BY VALUE — java's overload, not scala's index removal") {
    val p = port(removes, new CollectionsTransform)
    // both positions get the faithful form: `transformApply` sees an `Apply`, not the statement it
    // sits in, so "the result is discarded" is not a fact available to the rewrite.
    assertEmits(p, "balticporter.runtime.JavaCollections.removeValue(xs, java.lang.Integer.valueOf(1))")
    assertEmits(p, "return balticporter.runtime.JavaCollections.removeValue(xs, java.lang.Integer.valueOf(1))")
    assertEmits(p, "balticporter.runtime.JavaCollections.removeValue(ss, s)")
    // an `ArrayDeque` has NO index overload, so java boxes and resolves `remove(Object)` — which is
    // why the discriminator cannot be "the argument is an int".
    assertEmits(p, "balticporter.runtime.JavaCollections.removeValue(q, java.lang.Integer.valueOf(3))")
  }

  test("List.remove(int) stays scala's index removal — same meaning, same result") {
    val p = port(removes, new CollectionsTransform)
    assertEmits(p, "ss.remove(0)")
    assertEmits(p, "return ss.remove(0)")
    // `List<Boolean>` is the shape that would break a result-type test that did not distinguish the
    // BOXED element from the primitive `boolean` java's `remove(Object)` returns.
    assertEmits(p, "bs.remove(0)")
    assertNotEmits(p, "removeValue(bs")
    assertNotEmits(p, "removeValue(ss, 0)")
  }

  test("a downcast FROM a type the phase does not retype is KEPT, retargeted at the shim") {
    // `Object` is not in the phase's type map, so nothing the phase did can stop the value from
    // being a shim instance at run time. Java's downcast stays a downcast.
    val p = port(
      """package demo;
        |import java.util.Collection;
        |class Casts<V> {
        |  Collection<V> narrow(Object o) { return (Collection<V>) o; }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "asInstanceOf[balticporter.runtime.JavaCollection[")
    // and it targets the SHIM, not the java type the port no longer produces.
    assertNotEmits(p, "asInstanceOf[java.util.Collection")
  }

  test("a cast the phase itself made unsatisfiable is dropped rather than emitted") {
    // `ArrayList` maps to `mutable.ArrayBuffer` and `Collection` maps to the shim, so after this
    // phase the value CANNOT be what the cast asks for. Dropping it turns a guaranteed runtime
    // `ClassCastException` into a compile error on the same line (ENGINE-LIMITS M6).
    val p = port(
      """package demo;
        |import java.util.ArrayList;
        |import java.util.Collection;
        |class Casts {
        |  Object widen(ArrayList<String> xs) { return (Collection<String>) xs; }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertNotEmits(p, "asInstanceOf[balticporter.runtime.JavaCollection[")
    assertNotEmits(p, "asInstanceOf[java.util.Collection")
  }
