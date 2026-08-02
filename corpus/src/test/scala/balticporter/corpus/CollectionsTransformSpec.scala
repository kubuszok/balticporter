package balticporter.corpus

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.{PortSuite, Ported}
import balticporter.tir.{Phase, Pipeline, UsageKind}
import balticporter.transform.{CollectionBoundaryCheck, CollectionsTransform}

import java.nio.file.Files

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

  // ---------------------------------------------------------------------------------------------
  // The `java.util.stream` COLLAPSE, and what it is keyed on. Audited as "keys on the receiver's
  // WRITTEN type rather than its retyped kind" and DISPROVED — see `CollectionsTransform.collapsed`
  // for the argument. These pin the two halves of it so a change in the frontend's member
  // resolution reports here instead of the chain silently ceasing to translate.
  // ---------------------------------------------------------------------------------------------

  /** every spelling a `stream()` receiver can have, including a program class of its own. */
  private val streamReceivers =
    """package demo;
      |import java.util.*;
      |import java.util.stream.*;
      |import java.util.function.Predicate;
      |class Own extends AbstractCollection<String> {
      |  public Iterator<String> iterator() { return null; }
      |  public int size() { return 0; }
      |}
      |class S {
      |  List<String> f;
      |  List<String> get() { return f; }
      |  List<String> r01(List<String> c, Predicate<String> p)             { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r02(ArrayList<String> c, Predicate<String> p)        { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r03(Set<String> c, Predicate<String> p)              { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r04(HashSet<String> c, Predicate<String> p)          { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r05(LinkedList<String> c, Predicate<String> p)       { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r06(ArrayDeque<String> c, Predicate<String> p)       { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r07(Deque<String> c, Predicate<String> p)            { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r08(Queue<String> c, Predicate<String> p)            { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r09(Collection<String> c, Predicate<String> p)       { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r10(TreeSet<String> c, Predicate<String> p)          { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r11(LinkedHashSet<String> c, Predicate<String> p)    { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r12(Own c, Predicate<String> p)                      { return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> r13(AbstractCollection<String> c, Predicate<String> p){ return c.stream().filter(p).collect(Collectors.toList()); }
      |  List<String> chained(Predicate<String> p)                         { return get().stream().filter(p).collect(Collectors.toList()); }
      |  List<String> cast(Object o, Predicate<String> p)                  { return ((List<String>) o).stream().filter(p).collect(Collectors.toList()); }
      |  List<String> mapValues(Map<String,String> m, Predicate<String> p) { return m.values().stream().filter(p).collect(Collectors.toList()); }
      |}
      |""".stripMargin

  test("the collapse reaches EVERY receiver spelling — 13 declared types, a chained call and a cast") {
    val p = port(streamReceivers, new CollectionsTransform)
    // One arm keyed on `java.util.Collection#stream` serves all of them because that is the
    // DECLARING type of the method, which is what the frontend resolves — not the receiver's
    // written type. Every one becomes the collapsed `filtered(…asScalaBuffer, …)`.
    assertEquals(clue(p.out).sliding("JavaCollection.filtered(".length)
                   .count(_ == "JavaCollection.filtered("), 16)
    // and nothing survives as a java stream call.
    assertNotEmits(p, "java.util.stream.Collectors.toList()")
    assertNotEmits(p, ".stream()")
    // …and the ACCESSOR each receiver reaches the chain through is decided by what that receiver
    // IS, not by one entry in a table applied to every kind (see `streamSource`). Emitting
    // `asScalaBuffer` unconditionally was three uncompilable sites on liqp that no check saw,
    // because the collapse fired and nothing reported an untranslated chain.
    assertEmits(p, "c.asScalaBuffer")      // a `Collection`/`AbstractCollection` slot IS the shim
    assertEmits(p, "c.toBuffer")           // a `Set` copies — every collapsed operation takes a Buffer
    assertEmitsMatch(p, """filtered\(c, p\.""")  // a `List`/`Deque`/`Queue` slot already IS the sequence
    // `Own extends AbstractCollection<T>` keeps ITS OWN type, which this phase never minted — the
    // accessor comes from the DECLARING type's target, and it is right because `Own` really does
    // extend `JavaCollection` after the retyping.
    assertEmitsMatch(p, """(?s)def r12\(c: demo\.Own.*?filtered\(c\.asScalaBuffer""")
  }

  test("a chain whose receiver the phase did NOT retype is left alone — and must be") {
    // `"…".lines()` is a `java.util.stream.Stream` with no collection behind it; rewriting its
    // `filter` on the method name alone measured 0 -> 1 on libGDX's test port (ENGINE-LIMITS K6).
    val p = port(
      """package demo;
        |import java.util.*;
        |import java.util.stream.*;
        |import java.util.function.Predicate;
        |class S {
        |  List<String> lines(String s, Predicate<String> p) {
        |    return s.lines().filter(p).collect(Collectors.toList());
        |  }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertNotEmits(p, "JavaCollection.filtered(")
    assertEmits(p, "s.lines().filter(")
  }

  test("a Stream-typed SLOT is the one shape the collapse cannot reach — and it fails LOUDLY") {
    // The value really is a `Buffer` and the declaration really says `Stream`, because the stream
    // family is deliberately not retyped (K6). `collapsed` answering `false` here is correct: the
    // DECLARATION is what has no translation, and making the guard say `true` would rewrite the
    // operation while leaving the slot in place — moving the error, not closing it. Measured: the
    // emission below is 2 compile errors, so the refusal is loud (ENGINE-LIMITS M6).
    val p = port(
      """package demo;
        |import java.util.*;
        |import java.util.stream.*;
        |import java.util.function.Predicate;
        |class S {
        |  List<String> f;
        |  List<String> viaLocal(Predicate<String> p) {
        |    Stream<String> st = f.stream();
        |    return st.filter(p).collect(Collectors.toList());
        |  }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    // the SOURCE still collapses — it is the slot that does not follow. `f` is a `java.util.List`,
    // so it retypes to a `Buffer` and IS the sequence: no accessor is added, because
    // `asScalaBuffer` is the SHIM's and this receiver is not one (see `streamSource`).
    assertEmits(p, "val st: java.util.stream.Stream[java.lang.String] = this.f\n")
    // … and the operation is therefore NOT rewritten, which is what makes the mismatch visible.
    assertNotEmits(p, "JavaCollection.filtered(")
  }

  // ---------------------------------------------------------------------------------------------
  // `coerce` — the scala-collection-into-a-shim-slot seam. Its COVERAGE is what these pin: the
  // four slot kinds (argument, declaration, assignment, RETURN) crossed with the source kinds
  // (Seq, Set, Map), plus the two cells that are deliberately REFUSED. The doc on `coerce` used to
  // claim one seam covered every slot and two of the six cells were open.
  // ---------------------------------------------------------------------------------------------

  private val slots =
    """package demo;
      |import java.util.*;
      |class C {
      |  Collection<String> fld;
      |  void take(Collection<String> c) {}
      |  void takeIt(Iterable<String> i) {}
      |  Collection<String> retNew()                          { return new ArrayList<String>(); }
      |  Collection<String> retVar(List<String> xs)           { return xs; }
      |  Iterable<String> retIterable(List<String> xs)        { return xs; }
      |  Collection<String> retIf(List<String> xs, boolean b) { if (b) return xs; return null; }
      |  Collection<String> retSet(Set<String> s)             { return s; }
      |  Iterable<String> retSetIterable(Set<String> s)       { return s; }
      |  void argSet(Set<String> s)    { take(s); }
      |  void argSetIt(Set<String> s)  { takeIt(s); }
      |  void declSet(Set<String> s)   { Collection<String> c = s; }
      |  void assignSet(Set<String> s) { fld = s; }
      |  Collection<String> lambdaInside(List<String> xs) {
      |    Runnable r = () -> { List<String> inner = xs; return; };
      |    return xs;
      |  }
      |}
      |""".stripMargin

  test("RETURN is a shim-typed slot exactly as a formal is — including inside an `if`") {
    val p = port(slots, new CollectionsTransform)
    assertEmits(p, "return balticporter.runtime.JavaCollection.from(new scala.collection.mutable.ArrayBuffer")
    assertEmits(p, "return balticporter.runtime.JavaCollection.from(xs)")
    assertEmits(p, "return balticporter.runtime.JavaIterable.from(xs)")
    // the walk follows a statement-carrying node …
    assertEmits(p, "      return balticporter.runtime.JavaCollection.from(xs)")
    // … and `return null` is left alone: `coerce` fires only on a source the phase itself retyped.
    assertEmits(p, "return null")
  }

  test("a return inside a LAMBDA is NOT coerced against the enclosing method's type") {
    // The walk stops at every node that opens its own return scope. Descending would coerce an
    // inner `return` against a type it has nothing to do with — the one way this walk could be
    // wrong SILENTLY, as against merely missing a coercion (which is a compile error).
    val p = port(slots, new CollectionsTransform)
    assertEmits(p, "val inner: scala.collection.mutable.Buffer[java.lang.String] = xs")
    assertNotEmits(p, "val inner: balticporter.runtime.JavaCollection")
  }

  test("a Kind.Set source reaches BOTH shims, in all four slots") {
    val p = port(slots, new CollectionsTransform)
    // `java.util.Set` IS a `java.util.Collection`, so a `mutable.Set` must reach a Collection slot.
    // A DISTINCT NAME, never an overload of `from`: every candidate is a `scala.collection.Iterable`.
    assertEmits(p, "return balticporter.runtime.JavaCollection.fromSet(s)")   // return
    assertEmits(p, "this.take(balticporter.runtime.JavaCollection.fromSet(s))") // argument
    assertEmits(p, "val c: balticporter.runtime.JavaCollection[java.lang.String] = balticporter.runtime.JavaCollection.fromSet(s)") // declaration
    assertEmits(p, "this.fld = balticporter.runtime.JavaCollection.fromSet(s)") // assignment
    // …and `JavaIterable.from` already takes a `scala.collection.Iterable`, so the iterable target
    // needs nothing added for a set.
    assertEmits(p, "this.takeIt(balticporter.runtime.JavaIterable.from(s))")
    assertEmits(p, "return balticporter.runtime.JavaIterable.from(s)")
  }

  test("a Kind.Map source bridges to JavaIterable and is REFUSED at JavaCollection") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class M {
        |  void takeIt(Iterable<Map.Entry<String,String>> it) {}
        |  void takeColl(Collection<Map.Entry<String,String>> c) {}
        |  void argIt(Map<String,String> m)   { takeIt(m.entrySet()); }
        |  void argColl(Map<String,String> m) { takeColl(m.entrySet()); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    // a scala `Map[K, V]` IS an `Iterable[(K, V)]` — which is exactly what java's `entrySet()` view
    // is, and the `entrySet` rewrite hands back the map itself.
    assertEmits(p, "this.takeIt(balticporter.runtime.JavaIterable.from(m))")
    // …but there is no `Collection` view of a map, and inventing one would have to reproduce
    // `entrySet().remove(e)` removing a mapping only when KEY AND VALUE both match. Refused, so it
    // fails to compile at the slot (ENGINE-LIMITS M6) rather than being guessed.
    assertEmits(p, "this.takeColl(m)")
    assertNotEmits(p, "JavaCollection.from(m)")
    assertNotEmits(p, "JavaCollection.fromSet(m)")
  }

  test("a `keySet()` source is REFUSED — its node type overstates the scala the emitter prints") {
    // `m.keySet` is a `scala.collection.Set`, not the retyped `mutable.Set` the node claims — the
    // same disagreement `transformValDef`'s keySet arm already encodes. Wrapping on a type the
    // phase knows the value does not have would emit a call naming the WRAPPER instead of the
    // boundary; measured, the unwrapped form says `Found: scala.collection.Set[String] / Required:
    // JavaCollection[String]`, which is the error a reader needs.
    val p = port(
      """package demo;
        |import java.util.*;
        |class K {
        |  void take(Collection<String> c) {}
        |  void argMapKeys(Map<String,String> m) { take(m.keySet()); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "this.take(m.keySet)")
    assertNotEmits(p, "fromSet(m.keySet)")
  }

  // ---------------------------------------------------------------------------------------------
  // `Arrays.asList` — the engine's VARARG CONVENTION met by the one rewritten static whose runtime
  // counterpart is a scala vararg. A java `T...` parameter is emitted as `Array[T]` and the
  // frontend materialises the pack at the call, which is right for every in-program vararg method
  // and wrong for `JavaCollections.asList[A](xs: A*)`.
  // ---------------------------------------------------------------------------------------------

  private val asList =
    """package demo;
      |import java.util.*;
      |class A {
      |  List<Integer> elems()             { return Arrays.asList(1, 2, 3); }
      |  List<String> whole(String[] xs)   { return Arrays.asList(xs); }
      |  List<String[]> two(String[] xs)   { return Arrays.asList(xs, xs); }
      |  List<String> none()               { return Arrays.asList(); }
      |  List<String> one(String s)        { return Arrays.asList(s); }
      |  static <T> T[] pack(T... xs)      { return xs; }
      |  String[] callPack(String a, String b) { return pack(a, b); }
      |}
      |""".stripMargin

  test("an ELEMENT pack is opened back into separate arguments — never passed as one array") {
    val p = port(asList, new CollectionsTransform)
    // two ARRAY elements: correct, translatable java that emitted the pack unspread and failed
    // E007. Behaviour verified by running it — size 2, both elements `eq` to the argument.
    assertEmits(p, "balticporter.runtime.JavaCollections.asList(xs, xs)")
    assertNotEmits(p, "asList(scala.Array[scala.Array[")
    // one element: the frontend packs a single non-primitive argument too.
    assertEmits(p, "balticporter.runtime.JavaCollections.asList(s)")
    assertNotEmits(p, "asList(scala.Array[java.lang.String](s))")
    // …and the pack arrives here in the EXTERNAL-callee shape (`Tree.Repeated`, not
    // `Tree.NewArray`), because `java.util.Arrays.asList` is a class file: read as one ordinary
    // argument that node carries an ARRAY type and fell into the aliasing refusal, so the rewrite
    // did not happen and the two element forms above emitted the JDK call under a retyped return
    // type. Both halves were green alone. Nothing but this composition can see it.
    assertNotEmits(p, "return java.util.Arrays.asList(xs, xs)")
    assertNotEmits(p, "return java.util.Arrays.asList(s)")
    // the two shapes the frontend already emitted as bare elements are unchanged — it declines to
    // pack primitives, which is the only reason `asList(1, 2, 3)` was ever right.
    assertEmits(p, "balticporter.runtime.JavaCollections.asList(1, 2, 3)")
    assertEmits(p, "balticporter.runtime.JavaCollections.asList()")
  }

  test("the whole-ARRAY aliasing form is REFUSED, and the refusal keeps the JDK name") {
    val p = port(asList, new CollectionsTransform)
    // java returns a LIVE VIEW of the caller's array; spreading it would silently copy what java
    // aliases (§4.4). The rewrite does not happen at all, so the emitted text says which call was
    // not translated — measured `Found: java.util.List[Array[Object]] / Required: Buffer[String]`.
    assertEmits(p, "return java.util.Arrays.asList(xs.asInstanceOf[scala.Array[java.lang.Object]])")
    assertNotEmits(p, "JavaCollections.asList(xs.asInstanceOf")
  }

  test("an IN-PROGRAM vararg method still receives the materialised array — the convention holds") {
    val p = port(asList, new CollectionsTransform)
    // the pack is only opened for the one helper declared `A*`; a java `T...` parameter is still
    // emitted as `Array[T]` and still fed the array.
    assertEmits(p, "def pack[T <: java.lang.Object](xs: scala.Array[T])")
    assertEmits(p, "A.pack(scala.Array[java.lang.String](a, b))")
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

  /** the same rule one slot along: a WIDENING the java formal required, which the retyped scala
    * formal does not accept. Java declares `Map.get`/`remove`/`containsKey` over `Object`, so a
    * TYPE-VARIABLE key arrives at this phase already wrapped in `asInstanceOf[java.lang.Object]`
    * — correct for the java call, and `Found: Object / Required: K` once the receiver is a scala
    * `Map[K, V]`. ENGINE-LIMITS K5.6: a phase that retypes owns the coercions around what it moved. */
  private val genericMap =
    """package demo;
      |import java.util.HashMap;
      |import java.util.Map;
      |class Registry<K, V> {
      |  private final Map<K, V> m = new HashMap<K, V>();
      |  V get(K key) { return m.get(key); }
      |  V drop(K key) { return m.remove(key); }
      |  boolean has(K key) { return m.containsKey(key); }
      |  void set(K key, V value) { m.put(key, value); }
      |  V getOr(K key, V d) { return m.getOrDefault(key, d); }
      |}
      |""".stripMargin

  test("a TYPE-VARIABLE key loses the java Object widening — the scala member takes K") {
    val p = port(genericMap, new CollectionsTransform)
    assertNotEmits(p, "key.asInstanceOf[java.lang.Object]")
    assertEmits(p, "this.m.getOrElse(key,")
    assertEmits(p, "this.m.remove(key)")
    assertEmits(p, "this.m.contains(key)")
    assertEmits(p, "this.m.put(key, value)")
  }

  // ---------------------------------------------------------------------------------------------
  // `Collection.toArray()` and `toArray(T[])`. Left alone, NEITHER binds to a `toArray` at all:
  // scala's is PARENLESS, so `xs.toArray()` parses as `xs.toArray.apply()` — an array INDEX — and
  // the error names `method apply in class Array`. Both go to a `JavaCollections` helper because
  // java's CONTRACT (Object[] component type; fill-the-argument-if-it-fits; the null terminator)
  // is what a naive `xs.toArray` silently breaks — §4.4, and pinned in `JavaCollectionsSpec`.
  // ---------------------------------------------------------------------------------------------

  test("toArray() and toArray(T[]) go to the runtime helper, on Seq and on Set alike") {
    val p = port(
      """package demo;
        |import java.util.*;
        |class Arrays2 {
        |  private final List<String> xs = new ArrayList<String>();
        |  private final Set<String> ys = new HashSet<String>();
        |  Object[] all() { return xs.toArray(); }
        |  Object[] allSet() { return ys.toArray(); }
        |  Object[] into(Object[] a) { return xs.toArray(a); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "balticporter.runtime.JavaCollections.toArray(this.xs)")
    assertEmits(p, "balticporter.runtime.JavaCollections.toArray(this.ys)")
    assertEmits(p, "balticporter.runtime.JavaCollections.toArray(this.xs, a)")
    // and nothing binds to scala's parenless `toArray`, which is what produced the `apply` error.
    assertNotEmits(p, "this.xs.toArray")
  }

  test("the ERASURE cast on a toArray(T[]) argument is stripped — the helper infers java's own T") {
    // Java declares `<T> T[] toArray(T[] a)`, erased formal `Object[]`, so the frontend wraps the
    // argument in `asInstanceOf[Array[Object]]` (G14). `JavaCollections.toArray[A]` infers `A` FROM
    // the argument, so with the cast left on it hands back an `Array[Object]` where java's call —
    // which inferred `T = String` from the UNERASED argument — produced a `String[]`; scala's
    // arrays are invariant, so that is a compile error the rewrite itself made.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Erased {
        |  private final List<String> xs = new ArrayList<String>();
        |  String[] typed() { return xs.toArray(new String[xs.size()]); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "balticporter.runtime.JavaCollections.toArray(this.xs, new scala.Array[java.lang.String](")
    assertNotEmits(p, "asInstanceOf[scala.Array[java.lang.Object]]")
  }

  // ---------------------------------------------------------------------------------------------
  // A class that EXTENDS a mapped JDK collection. K5 closed this family for the SHIM targets; it
  // stayed open wherever the parent becomes a REAL scala collection, because the receiver's type is
  // then the class's own and `kindOf` has no key for it. `this.get(k)` bound to scala's `Map.get`
  // and returned an `Option` where java returned the value — a rewrite that silently did not run.
  // ---------------------------------------------------------------------------------------------

  test("a call INHERITED from a mapped collection is rewritten — the kind comes from the declaring type") {
    val p = port(
      """package demo;
        |import java.util.HashMap;
        |class Row extends HashMap<String, Integer> {
        |  Integer at(String k)      { return this.get(k); }
        |  void copyIn(HashMap<String, Integer> m) { this.putAll(m); }
        |  boolean here(String k)    { return this.containsKey(k); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "this.getOrElse(k,")
    assertEmits(p, "this ++= m")
    assertEmits(p, "this.contains(k)")
  }

  test("…and EVERY rewrite declines on a `super` receiver — a blanket refusal, because E040 is worse") {
    // Scala admits `super` in exactly one position, as the qualifier of a member selection. Three
    // of the arms put it somewhere else — `entrySet` returns the receiver alone (`for (e <-
    // super)`), the `Seq` `get` makes it a function (`super(i)`), and `+=`/`++=` render INFIX
    // (`super ++= m`, measured as an E040 on liqp) — and a syntax error is strictly worse than the
    // type error it replaces. Which arms render infix is a fact about the EMITTER, so the refusal
    // is blanket rather than a carve-out this phase cannot keep in step.
    val p = port(
      """package demo;
        |import java.util.HashMap;
        |import java.util.Map;
        |class Rows extends HashMap<String, Integer> {
        |  Integer at(String k) { return super.get(k); }
        |  void copyIn(HashMap<String, Integer> m) { super.putAll(m); }
        |  void walk() { for (Map.Entry<String, Integer> e : super.entrySet()) { } }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "super.get(k)")
    assertEmits(p, "super.putAll(m)")
    assertEmits(p, "super.entrySet()")
    assertNotEmits(p, "super ++=")
    assertNotEmits(p, "super.getOrElse")
  }

  test("`subList` and `putIfAbsent` go to the helper — scala HAS both and both mean something else") {
    // `slice` COPIES where java's `subList` is a write-through view, and `getOrElseUpdate` returns
    // the value now in the map where java's `putIfAbsent` returns the PREVIOUS one. Both compile
    // and both are §4.4; the contracts are pinned in `JavaCollectionsSpec`.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Ranges {
        |  private final List<String> xs = new ArrayList<String>();
        |  private final Map<String, String> m = new HashMap<String, String>();
        |  List<String> head(int n)  { return xs.subList(0, n); }
        |  String once(String k, String v) { return m.putIfAbsent(k, v); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "balticporter.runtime.JavaCollections.subList(this.xs, 0, n)")
    assertEmits(p, "balticporter.runtime.JavaCollections.putIfAbsent(this.m, k, v)")
    assertNotEmits(p, "this.xs.slice")
    assertNotEmits(p, "getOrElseUpdate")
  }

  // ---------------------------------------------------------------------------------------------
  // The EXTERNAL CALLEE seam. A method the program does not declare has a signature in a CLASS
  // FILE, which no phase can move — while `transformType` moved the call NODE's type, so both sides
  // read the same scala collection and every check comparing node types reports zero. Measured on
  // liqp: 15 compile errors at one third-party package against 0 findings.
  //
  // These use `java.util.Collections` and `java.lang.System` as stand-ins for a third party, because
  // §1's enforcement rule forbids naming a ported library here and the mechanism does not care
  // which class file it is: what it keys on is "the program does not declare this method".
  // ---------------------------------------------------------------------------------------------

  /** a port whose frontend also sees COMPILED CLASS FILES.
    *
    * The JDK alone cannot pose two of the questions this seam has to answer — a third party's method
    * declared to return a CONCRETE `java.util.ArrayList`, and one declared to return a
    * `java.util.Map.Entry` — because every JDK member of that shape is owned by a type the mapping
    * already covers and is excluded before the arm is reached. So the fixture compiles its own
    * class file and hands the directory to the frontend as a classpath, exactly the way
    * `ExternalSignatureSpec` builds its partially-resolvable one. `ext.*` is a fixture package, not
    * a library (§1's enforcement rule): what the mechanism keys on is "the program does not declare
    * this method". */
  private def portAgainst(ext: List[(String, String)], java: String, phases: Phase*): Ported =
    val root = Files.createTempDirectory("collections-external")
    val cls  = root.resolve("classes")
    Files.createDirectories(cls)
    val files = ext.map { (name, code) =>
      val f = root.resolve("jsrc").resolve(name)
      Files.createDirectories(f.getParent)
      Files.writeString(f, code)
      f.toString
    }
    val javac = javax.tools.ToolProvider.getSystemJavaCompiler
    assertEquals(javac.run(null, null, null, List("-d", cls.toString) ++ files*), 0,
                 "the fixture's own java did not compile")
    val srcRoot = root.resolve("src")
    Files.createDirectories(srcRoot.resolve("demo"))
    Files.writeString(srcRoot.resolve("demo/Snippet.java"), java)
    val before = SpoonTir.fromTypes(
      SpoonTir.buildModel(FrontendConfig(srcRoot, List("demo/Snippet.java"), List(cls)), lenient = true))
    Ported(before, Pipeline.run(before, phases.toList), phases.toList, Map("Snippet.java" -> java))

  test("an EXTERNAL producer is wrapped, so the value really becomes what its node already claims") {
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Ext {
        |  Map<String, String> env() { return System.getenv(); }
        |}
        |""".stripMargin, ph)
    // the wrap needs no evidence of WHICH java type it was: `fromJava` is overloaded and scalac
    // resolves it against the real static type from the class file.
    assertEmits(p, "balticporter.runtime.JavaCollections.fromJava(java.lang.System.getenv())")
  }

  test("…and a call the phase does NOT retype is left completely alone — the negative test") {
    // Nothing about "external" licenses a wrap. Only a node whose type THIS PHASE produced is a
    // seam; a `String`, an `int` or a third-party type of its own is not, and a rule that fired on
    // "the callee is external" would wrap every call in the program.
    val p = port(
      """package demo;
        |class Plain {
        |  String greet() { return java.lang.System.getProperty("user.name").trim(); }
        |  int size(String s) { return s.length(); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertNotEmits(p, "fromJava")
  }

  test("…nor is a JDK COLLECTION member, whose receiver this phase already retyped") {
    // `java.util.Map#keySet` is an external method returning `java.util.Set`, and its value IS
    // already a scala set because the RECEIVER moved. Wrapping it would convert something that was
    // never java's. The guard is the callee's OWNER being one of the phase's own types.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Own {
        |  private final Map<String, String> m = new HashMap<String, String>();
        |  Set<String> keys() { return m.keySet(); }
        |  int n() { return m.size(); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertNotEmits(p, "fromJava")
  }

  test("…nor a GENERIC PASS-THROUGH, where the port's own value went in and came back out") {
    // The node's type is evidence of two different things. Where the callee's result is a real
    // `java.util.List`, the node says `Buffer` because this phase MOVED it. Where the result is a
    // TYPE VARIABLE, the node says `Buffer` because the CALLER handed it one — and wrapping that
    // converts a value that was never java's. Measured as 7 sites on liqp
    // (`fromJava(java.util.Objects.requireNonNull(aScalaMap))`, an E134 naming the helper rather
    // than the boundary). With no external signature there is no way to ask "is the result a type
    // variable", so it is answered structurally: the result type already occurs on the INPUT side.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Through {
        |  private final ThreadLocal<Map<String, Object>> local = new ThreadLocal<Map<String, Object>>();
        |  Map<String, Object> checked(Map<String, Object> m) { return Objects.requireNonNull(m); }
        |  Map<String, Object> here() { return local.get(); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertNotEmits(p, "fromJava")
  }

  test("…nor a target NO CONVERTER PRODUCES — the wrap is gated on what `fromJava` can actually make") {
    // `kindOf` holds EVERY mapping target — `ArrayBuffer`, `ArrayDeque`, `mutable.TreeMap`,
    // `Tuple2` — while `fromJava` produces exactly five shapes (`Buffer`, `Set`, `Map`,
    // `JavaIterator`, `JavaIterable`). Wrapping toward the rest emits a call whose RESULT does not
    // meet the node's own claim, and the error then names the HELPER instead of the boundary:
    // `E134 None of the overloaded alternatives of method fromJava`. `liveWrappable` is the phase's
    // own record of which targets a live view exists for, read in the direction the phase moved
    // them (§4.56); everything else is a counted refusal, exactly as `JavaCollection` already was.
    val ph = new CollectionsTransform
    val p  = portAgainst(
      List("ext/Prod.java" ->
        """package ext;
          |public class Prod {
          |  public java.util.ArrayList<String> made() { return new java.util.ArrayList<String>(); }
          |  public java.util.Map.Entry<String, String> pair() { return null; }
          |}""".stripMargin),
      """package demo;
        |class Uses {
        |  java.util.ArrayList<String> made(ext.Prod p) { return p.made(); }
        |  java.util.Map.Entry<String, String> pair(ext.Prod p) { return p.pair(); }
        |}
        |""".stripMargin, ph)
    // a CONCRETE list: the node claims `ArrayBuffer`, and `fromJava` makes a `Buffer`.
    // a `Map.Entry`: the node claims `Tuple2`, and `fromJava` has no overload at all.
    assertNotEmits(p, "fromJava")
    val fs = ph.boundary(p.after).filter(_.issue == CollectionBoundaryCheck.Issue.ExternalCallee)
    assertEquals(clue(fs).count(_.slot.startsWith("external result")), 2,
                 "both refusals must be counted — an uncounted refusal is indistinguishable from no seam")
  }

  test("a CONCRETE collection head at the callee's declared result disproves the pass-through guess") {
    // The structural guess — "the result type already occurs on the INPUT side" — is also the shape
    // of every non-identity `List`→`List` third-party utility (`reverse`, `sorted`, `filtered`), and
    // there the value crossing the call really is java's. Suppressing the wrap there ALSO recorded
    // nothing, which is the pre-K15 state at the very calls K15 was built for.
    //
    // Where the class file can be read the guess is not needed: a MethodType is all-or-none, so a
    // member whose result is a type VARIABLE is signature-less by construction (`ExternalSignatureSpec`)
    // — and a readable result whose HEAD is a type this phase maps is therefore a real java
    // collection, whatever the argument types happen to be. The phase's own table answers it (§4.56).
    // The RECEIVER half of the guess is what this fixture aims at, because it is the half nothing
    // else moves: a bridged ARGUMENT stops being a scala collection before the guess reads it, while
    // a receiver's type is never bridged. A generic third-party holder instantiated at a collection
    // makes every concrete-returning member of it read as a pass-through.
    val ph = new CollectionsTransform
    val p  = portAgainst(
      List("ext/Holder.java" ->
        """package ext;
          |public class Holder<T> {
          |  public T get() { return null; }
          |  public java.util.List<String> names() { return new java.util.ArrayList<String>(); }
          |}""".stripMargin),
      """package demo;
        |import java.util.*;
        |class Names {
        |  private final ext.Holder<List<String>> holder = new ext.Holder<List<String>>();
        |  List<String> names() { return holder.names(); }
        |  List<String> value()  { return holder.get(); }
        |}
        |""".stripMargin, ph)
    // `names()` — the class file SAYS `java.util.List`, so the value crossing the call is java's.
    assertEmits(p, "balticporter.runtime.JavaCollections.fromJava(this.holder.names())")
    // `get()` — a type-variable result, so the member is signature-less and the guess is right.
    assertNotEmits(p, "fromJava(this.holder.get())")
    assertEquals(ph.boundary(p.after)
                   .count(_.slot.startsWith("external result (unverified pass-through")), 1)
  }

  test("…and where the STRUCTURAL GUESS is all there is, the suppression is COUNTED in its own lane") {
    // `Objects.requireNonNull(m)` and `ThreadLocal<Map<K,V>>.get()` are signature-less — a
    // type-variable result leaves the member at `NoType` — so nothing can decide whether the value
    // crossing the call was ever java's, and the wrap stays suppressed. What may NOT happen is the
    // early exit taking the count with it: a suppression nobody counted is indistinguishable from a
    // seam that does not exist (M6), and it is a DIFFERENT fact from "the argument's fit could not
    // be verified" — the two must never be confusable, so it gets its own slot.
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Through2 {
        |  private final ThreadLocal<Map<String, Object>> local = new ThreadLocal<Map<String, Object>>();
        |  Map<String, Object> checked(Map<String, Object> m) { return Objects.requireNonNull(m); }
        |  Map<String, Object> here() { return local.get(); }
        |}
        |""".stripMargin, ph)
    assertNotEmits(p, "fromJava")
    val fs = ph.boundary(p.after).filter(_.issue == CollectionBoundaryCheck.Issue.ExternalCallee)
    assertEquals(clue(fs).count(_.slot.startsWith("external result (unverified pass-through")), 2)
    // …and it is NOT the cannot-verify-argument lane, which is about a different slot of the call.
    assert(fs.forall(f => f.slot.startsWith("external result") || f.slot.startsWith("argument")))
  }

  test("a call this phase REWRITES gets its arguments BARE — no wrap may precede the rewrite") {
    // `list.addAll(other.list)` becomes `list ++= other.list`, and `++=` wants an `IterableOnce`.
    // `java.util.List#addAll`'s formal is `java.util.Collection`, which `remap` reads as the SHIM —
    // so the moment external formals became readable, the argument pass wrapped it first and the
    // rewrite then emitted `list ++= JavaCollection.from(other.list)`, which is not an
    // `IterableOnce` at all. Measured at 4 errors on a port that had 0, with every check count flat
    // and 8 member digests moved: nothing but the compiler could see it, and no spec looked.
    //
    // Two rules keep it shut and both are asserted here: the shim wrap is for callees the PROGRAM
    // OWNS (a class file cannot name a `balticporter.runtime` type), and the java-formal bridge
    // runs after the rewrites.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Bag2 {
        |  final List<String> items = new ArrayList<String>();
        |  final Set<String> seen = new HashSet<String>();
        |  void merge(Bag2 other) { items.addAll(other.items); seen.addAll(other.seen); }
        |  // …and this is LOAD-BEARING, not decoration: `wrapIterableArgs` short-circuits when the
        |  // program names no `java.lang.Iterable`, because the shim is minted on demand. Without a
        |  // mention the pass never runs and this fixture passes for the wrong reason — it did.
        |  void feed(Iterable<String> xs) { for (String s : xs) { items.add(s); } }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "this.items ++= other.items")
    assertEmits(p, "this.seen ++= other.seen")
    assertNotEmits(p, "JavaCollection.from")
    assertNotEmits(p, "JavaCollections.toJava")
  }

  test("an external CONSUMER slot whose formal is READABLE is bridged, not counted") {
    // The consumer half, which used to be unanswerable: an argument whose formal lives in a class
    // file. The frontend interned every external member with NO signature — 1157 on liqp, not one
    // `MethodType` — so nothing could decide whether the argument fitted and the only honest
    // answer was a cannot-verify count. `SpoonTir` now records what a class file can be read for
    // scope-free, so `String.join`'s `java.lang.Iterable` formal is visible and the port's `Buffer`
    // reaches it through a LIVE view instead of through a compile error.
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Hand {
        |  private final List<String> xs = new ArrayList<String>();
        |  String joined() { return String.join(",", xs); }
        |}
        |""".stripMargin, ph)
    assertEmits(p, "balticporter.runtime.JavaCollections.toJava(this.xs)")
    assertEquals(ph.boundary(p.after).count(_.issue == CollectionBoundaryCheck.Issue.ExternalCallee), 0)
  }

  test("\u2026and so is one at java's UNIVERSAL formal, which the type checker cannot object to") {
    // The seam with no compile error behind it, and therefore the one nothing was looking for. A
    // retyped collection at a `java.lang.Object` formal CONFORMS \u2014 `mutable.Map` is an `AnyRef` \u2014
    // so the port compiles and hands reflective third-party code a value java handed a `HashMap`.
    // `toString`, `instanceof` and every serializer see something else: an ObjectMapper's
    // `convertValue`/`writeValueAsString`, a `String.valueOf`, a `println`. \u00a74.4's exact shape.
    //
    // `toJava` is the FAITHFUL answer rather than a compromise, and that is what licenses inserting
    // one where nothing is broken: java's value at that slot really WAS a java collection, so the
    // live view restores what the callee is entitled to see, both directions still shared.
    //
    // Naming `java.lang.Object` is not \u00a74.56's forbidden name test: it is not a claim about a
    // library's type, it is java's universal supertype \u2014 the one slot at which EVERY value conforms
    // and therefore the one at which conformance proves nothing.
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Plain2 {
        |  private final List<String> xs = new ArrayList<String>();
        |  private final Map<String, Object> m = new HashMap<String, Object>();
        |  String shown() { return String.valueOf(xs); }
        |  void log() { System.out.println(m); }
        |  // \u2026and a value the phase did NOT retype is untouched at the same kind of slot.
        |  String plain(String s) { return String.valueOf(s); }
        |}
        |""".stripMargin, ph)
    assertEmits(p, "java.lang.String.valueOf(balticporter.runtime.JavaCollections.toJava(this.xs))")
    assertEmits(p, "println(balticporter.runtime.JavaCollections.toJava(this.m))")
    assertEmits(p, "java.lang.String.valueOf(s)")
    // a bridged slot is not a residue.
    assertEquals(ph.boundary(p.after).count(_.issue == CollectionBoundaryCheck.Issue.ExternalCallee), 0)
  }

  test("\u2026and where it CANNOT be bridged, the universal formal produces a ROW where it produced nothing") {
    // `asJava` converts ONE level, so a `Map[String, Buffer[String]]` at a universal formal would
    // emit a view that lies one type argument in \u2014 the same refusal the mapped-formal direction
    // already makes. What must not follow is silence: `sideOf` put `java.lang.Object` on no side of
    // the boundary at all, so the pair fell through the match and the seam this whole finding is
    // about was reported by nothing.
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Deep {
        |  private final Map<String, List<String>> deep = new HashMap<String, List<String>>();
        |  String shown() { return String.valueOf(deep); }
        |}
        |""".stripMargin, ph)
    assertNotEmits(p, "toJava")
    val fs = ph.boundary(p.after).filter(_.issue == CollectionBoundaryCheck.Issue.ExternalCallee)
    assertEquals(clue(fs).count(_.expected == "java.lang.Object"), 1)
  }

  test("\u2026and an OWNED callee's universal formal is left alone \u2014 the negative test") {
    // The callee is scala the port EMITS, so the value it should receive is the scala collection.
    // Bridging there would hand a ported method a `java.util.List` its own body no longer expects,
    // and the row would be one nobody could act on. `bridgeJavaFormals` runs only where the formals
    // stay java's, which is exactly the three cases `keepsJavaFormals` names.
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Own3 {
        |  private final List<String> xs = new ArrayList<String>();
        |  void take(Object o) { }
        |  void go() { take(xs); }
        |}
        |""".stripMargin, ph)
    assertEmits(p, "this.take(this.xs)")
    assertNotEmits(p, "toJava")
    assertEquals(ph.boundary(p.after).count(_.issue == CollectionBoundaryCheck.Issue.ExternalCallee), 0)
  }

  test("\u2026and a class file with NO readable signature is still COUNTED, with its \u00a71 kind") {
    // The half that must never quietly become zero. Where the callee's declaration cannot be
    // reconstructed there is no formal at any slot, so nothing can decide whether the argument
    // fits and a cannot-verify count is the honest answer (M6) \u2014 a check that reads 0 because it
    // stopped looking is exactly the failure CLAUDE.md \u00a71(b) names. The real classpath fixture
    // that puts a member in that state lives in `ExternalSignatureSpec`; asserted here is that the
    // arm keys on the ABSENT `MethodType` and that its classification still reaches a reader.
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Hand3 {
        |  private final List<String> xs = new ArrayList<String>();
        |  void go(demo.Unknown u) { u.take(xs); }
        |}
        |""".stripMargin, ph)
    val fs = ph.boundary(p.after).filter(_.issue == CollectionBoundaryCheck.Issue.ExternalCallee)
    assert(clue(fs).nonEmpty, "an argument at a signature-less external callee must be counted")
    assert(clue(fs.head.slot).contains("no signature"))
    assert(clue(CollectionBoundaryCheck.Issue.classification(CollectionBoundaryCheck.Issue.ExternalCallee))
             .contains("\u00a71(a)"))
  }

  // ---------------------------------------------------------------------------------------------
  // A map whose type arguments are WILDCARDS — K10's rule at the other kind of unnameable key.
  // ---------------------------------------------------------------------------------------------

  test("a `Map<?, ?>` receiver takes java's three Object-keyed members, never scala's K-keyed ones") {
    // Java declares `get`, `containsKey` and `remove` over `Object`, so all three are legal on a
    // `Map<?, ?>` and no capture is involved. Scala declares the same three over `K`, so the
    // ordinary rewrite emits a key at an unnameable capture (`Found: String / Required: map.K`)
    // and — for `get` — a `null` ascribed to the equally unnameable `V`, which renders as a bare
    // `?` in a TERM position and is not syntax. Measured on liqp at 10 and 8 errors, from the same
    // nine call sites.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Wild {
        |  Object read(Map<?, ?> m, String k) { return m.get(k); }
        |  boolean has(Map<?, ?> m, String k) { return m.containsKey(k); }
        |  Object drop(Map<?, ?> m, String k) { return m.remove(k); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertEmits(p, "balticporter.runtime.JavaCollections.mapGet(m, k)")
    assertEmits(p, "balticporter.runtime.JavaCollections.mapContainsKey(m, k)")
    assertEmits(p, "balticporter.runtime.JavaCollections.mapRemove(m, k)")
    // the thing that made this a SYNTAX error rather than a type error, gone:
    assertNotEmits(p, "asInstanceOf[?]")
  }

  test("…and a FULLY-TYPED map keeps the scala members — the negative test") {
    // The helper is not a wider `get`; it is the answer to an unnameable capture. Where `K` and `V`
    // are ordinary types the scala member is exact, reads better, and is what every other port in
    // the corpus emits — routing it through a helper would move emitted text everywhere for no
    // gain and would hide the `getOrElse` shape the rest of this file asserts.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Typed {
        |  private final Map<String, Integer> m = new HashMap<String, Integer>();
        |  Integer read(String k) { return m.get(k); }
        |  boolean has(String k) { return m.containsKey(k); }
        |  Integer drop(String k) { return m.remove(k); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertNotEmits(p, "mapGet")
    assertNotEmits(p, "mapContainsKey")
    assertNotEmits(p, "mapRemove")
    assertEmits(p, "this.m.getOrElse(k, null.asInstanceOf[java.lang.Integer])")
    assertEmits(p, "this.m.contains(k)")
  }

  test("…and ONE wildcard is enough, on either side of the map") {
    // `Map<String, ?>` has a nameable key and an unnameable value, so `containsKey` would have been
    // fine and `get`'s null default would not. `Map<?, String>` is the mirror. Both go to the
    // helpers: the condition is a wildcard ANYWHERE in what this phase rendered, because the two
    // faces of the failure sit in different argument positions of the same call.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Half {
        |  Object v(Map<String, ?> m) { return m.get("k"); }
        |  Object k(Map<?, String> m) { return m.get("k"); }
        |}
        |""".stripMargin, new CollectionsTransform)
    assertNotEmits(p, "getOrElse")
    assertEmits(p, "balticporter.runtime.JavaCollections.mapGet(m, \"k\")")
  }

  // ---------------------------------------------------------------------------------------------
  // A PARENT the target cannot BE.
  // ---------------------------------------------------------------------------------------------

  test("a class that IMPLEMENTS Map.Entry keeps JAVA's parent, and the refusal is COUNTED") {
    // `Map.Entry` is a pair, and `Tuple2` is exact for every USE of one — which is why `entrySet()`
    // can hand back the map itself. As a PARENT it is impossible three times over: `Tuple2` is
    // final, has no `setValue`, and takes its two components in its constructor. So the parent
    // stays java's — the class really does implement `java.util.Map.Entry`, whose three members it
    // declares — and the seam moves to the slots where the port hands such a class to a `Tuple2`,
    // which is where a reader can act on it (M6).
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.Map;
        |class Holder {
        |  static final class Pair<K, V> implements Map.Entry<K, V> {
        |    private final Map.Entry<K, V> e;
        |    Pair(Map.Entry<K, V> e) { this.e = e; }
        |    public K getKey() { return e.getKey(); }
        |    public V getValue() { return e.getValue(); }
        |    public V setValue(V v) { return null; }
        |  }
        |}
        |""".stripMargin, ph)
    assertEmits(p, "extends java.util.Map.Entry[K, V]")
    assertNotEmits(p, "extends scala.Tuple2")
    val fs = ph.boundary(p.after).filter(_.issue == CollectionBoundaryCheck.Issue.InexpressibleParent)
    assertEquals(clue(fs).size, 1)
    assert(clue(fs.head.slot).contains("parent"))
    assert(clue(CollectionBoundaryCheck.Issue.classification(
             CollectionBoundaryCheck.Issue.InexpressibleParent)).contains("§1(a)"))
  }

  test("…and a USE of Map.Entry is still a Tuple2 — the negative test") {
    // The refusal is about the PARENT position and nothing else. An `entrySet()` walk and a
    // declared entry both keep the pair, which is what makes `getKey`/`getValue` translate to
    // `_1`/`_2`. A rule that fired on the TYPE rather than on the position would undo the mapping.
    val ph = new CollectionsTransform
    val p  = port(
      """package demo;
        |import java.util.*;
        |class Uses2 {
        |  private final Map<String, Integer> m = new HashMap<String, Integer>();
        |  int first() { for (Map.Entry<String, Integer> e : m.entrySet()) { return e.getValue(); } return 0; }
        |  Integer of(Map.Entry<String, Integer> e) { return e.getValue(); }
        |}
        |""".stripMargin, ph)
    // the DECLARED entry moved…
    assertEmits(p, "def of(e: scala.Tuple2[java.lang.String, java.lang.Integer])")
    // …and both `getValue` calls became the pair's accessor, which only holds if it did.
    assertEmits(p, "return e._2")
    assertNotEmits(p, "java.util.Map.Entry")
    assertEquals(ph.boundary(p.after).count(_.issue == CollectionBoundaryCheck.Issue.InexpressibleParent), 0)
  }

  test("a CAPACITY hint at a hashed collection gains java's own default load factor") {
    // scala's `mutable.HashMap` declares `()` and `(Int, Double)` and nothing in between, so java's
    // one-argument capacity constructor lands on no overload. Java's own definition of that
    // constructor is `(initialCapacity, DEFAULT_LOAD_FACTOR)`, and scala's companion publishes the
    // same 0.75 — so this is a translation, not an approximation.
    val p = port(
      """package demo;
        |import java.util.*;
        |class Sized {
        |  Map<String, Integer> m = new HashMap<String, Integer>(64);
        |  Set<String> s = new HashSet<String>(8);
        |  List<String> l = new ArrayList<String>(4);
        |  Map<String, Integer> tuned = new HashMap<String, Integer>(64, 0.9f);
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "new scala.collection.mutable.HashMap[java.lang.String, java.lang.Integer](64, scala.collection.mutable.HashMap.defaultLoadFactor)")
    assertEmits(p, "new scala.collection.mutable.HashSet[java.lang.String](8, scala.collection.mutable.HashSet.defaultLoadFactor)")
    // the SEQUENCE targets are the ones the note in `copyConstructor` is right about: scala's
    // `ArrayBuffer(Int)` means what java's `ArrayList(int)` means, so nothing is added.
    assertEmits(p, "new scala.collection.mutable.ArrayBuffer[java.lang.String](4)")
    // …and java's own two-argument form needs nothing: scala widens the Float to the Double.
    assertEmits(p, "new scala.collection.mutable.HashMap[java.lang.String, java.lang.Integer](64, 0.9f)")
  }

  test("…but a key that is NOT the map's key type keeps whatever it had — the strip is structural") {
    // Java's `Map.get(Object)` accepts anything, so a port CAN meet a key the scala member cannot
    // take. Stripping unconditionally would emit a call that silently claims a type the value does
    // not have; the strip is keyed on what lies UNDER the cast already being `K` (CLAUDE.md §4.56 —
    // structural, naming no type), so an `Object` key is passed through as it arrived and the
    // boundary stays where a reader can see it.
    val p = port(
      """package demo;
        |import java.util.HashMap;
        |import java.util.Map;
        |class Loose {
        |  private final Map<String, Integer> m = new HashMap<String, Integer>();
        |  Integer any(Object o) { return m.get(o); }
        |}
        |""".stripMargin,
      new CollectionsTransform,
    )
    assertEmits(p, "this.m.getOrElse(o,")
  }
