package balticporter.transform

import balticporter.tir.*

/** The two tables `CollectionsTransform.strippedOverrides` decides an `override` modifier from,
  * asserted against what they are quoting — `ENGINE-LIMITS.md` K28.
  *
  * ==Why a spec and not a comment==
  * The strip's own errors are loud (too small a keep-list and scalac says `needs "override"
  * modifier`; too large and the `E037` stays), so the corpus is a real instrument for the ROWS. It
  * is not an instrument for the two things below, and neither of them moves a count:
  *
  *   - **the shim rows quote `balticporter/runtime`, which the engine does not compile against.**
  *     `build.sbt` keeps the engine off the runtime on purpose, so a member added to `JavaIterator`
  *     tomorrow leaves this table saying the old surface, and the only symptom is an `override` that
  *     is stripped or kept wrongly on some future port. Nothing in this repository holds the two in
  *     step except this file;
  *   - **the ONE descriptor-keyed row.** A java `List` declares `remove` twice — by index and by
  *     value — and only the first survives onto a `mutable.Buffer`. Keyed on arity alone the table
  *     keeps the modifier on both, which is an `E038` the strip was written to remove; keyed on the
  *     wrong descriptor spelling it strips both, which is a modifier scala requires. The pair is
  *     asserted here because a corpus that happens to contain only one of the two would pass either
  *     way.
  */
class MintedParentSurfaceSpec extends munit.FunSuite:

  import CollectionsTransform.{OverridesShim, OverridesTarget}

  private def sig(name: String, params: Param*): OverrideGraph.Signature =
    OverrideGraph.Signature(name, Some(Descriptor(params.toList)), params.size, approximate = false)

  private def declares(row: Set[ExternalSurface.Member], s: OverrideGraph.Signature): Boolean =
    row.exists(_.matches(s))

  // -------------------------------------------------------------------------------------------
  // the KEEP side — a member here keeps `override`, and every one of these is a row the ssg-md
  // census reports as compiling or as an `E164` (which no modifier repairs).
  // -------------------------------------------------------------------------------------------

  test("a Map target declares the members java's Map shares with it") {
    val row = OverridesTarget(CollectionsTransform.Kind.Map.toString)
    List("size", "isEmpty", "clear", "keySet", "keys", "values", "iterator")
      .foreach(n => assert(declares(row, sig(n)), s"$n should keep its override on a scala Map"))
    assert(declares(row, sig("put", Param.Named("K"), Param.Named("V"))))
  }

  test("a Set target declares `add`, which is the one java member whose scala twin takes the ELEMENT") {
    val row = OverridesTarget(CollectionsTransform.Kind.Set.toString)
    assert(declares(row, sig("add", Param.Named("E"))))
    List("size", "isEmpty", "clear", "iterator").foreach(n => assert(declares(row, sig(n))))
  }

  test("a shim declares JAVA's own arity, which is what a ported override was written with") {
    assert(declares(OverridesShim(CollectionsTransform.JavaIterableFqn), sig("iterator")))
    assert(declares(OverridesShim(CollectionsTransform.JavaIteratorFqn), sig("hasNext")))
    assert(declares(OverridesShim(CollectionsTransform.JavaIteratorFqn), sig("next")))
    assert(declares(OverridesShim(CollectionsTransform.JavaIteratorFqn), sig("remove")))
  }

  // -------------------------------------------------------------------------------------------
  // the STRIP side — the negatives. Each of these was a live `E037`/`E038` row on ssg-md, and a
  // table that answered YES to any of them would leave that error exactly where it was.
  // -------------------------------------------------------------------------------------------

  test("a Map target declares NONE of java's five Map-only members — the E037 family") {
    val row = OverridesTarget(CollectionsTransform.Kind.Map.toString)
    List("containsKey", "containsValue", "entrySet", "putAll", "forEach").foreach(n =>
      assert(!declares(row, sig(n, Param.Named("Object"))) && !declares(row, sig(n)),
             s"$n has no counterpart on a scala Map and its override must be stripped"))
  }

  test("a Map target does NOT declare java's `get`/`remove`, which take Object where scala takes K") {
    val row = OverridesTarget(CollectionsTransform.Kind.Map.toString)
    assert(!declares(row, sig("get", Param.Named("Object"))))
    assert(!declares(row, sig("remove", Param.Named("Object"))))
  }

  test("a Set target does not declare the four java Collection bulk members") {
    val row = OverridesTarget(CollectionsTransform.Kind.Set.toString)
    List("containsAll", "removeAll", "retainAll", "addAll").foreach(n =>
      assert(!declares(row, sig(n, Param.Named("Collection")))))
    assert(!declares(row, sig("toArray")))
    assert(!declares(row, sig("contains", Param.Named("Object"))))
    assert(!declares(row, sig("remove", Param.Named("Object"))))
  }

  test("a shim declares NOTHING beyond java's own interface — the absence really is proof") {
    assert(!declares(OverridesShim(CollectionsTransform.JavaIterableFqn), sig("forEach", Param.Named("Consumer"))))
    assert(!declares(OverridesShim(CollectionsTransform.JavaIterableFqn), sig("spliterator")))
    assert(!declares(OverridesShim(CollectionsTransform.JavaIteratorFqn),
                     sig("forEachRemaining", Param.Named("Consumer"))))
  }

  // -------------------------------------------------------------------------------------------
  // the row that needs a DESCRIPTOR, both directions at once
  // -------------------------------------------------------------------------------------------

  test("`remove` on a Buffer keeps the INDEX overload and strips the VALUE one") {
    List(CollectionsTransform.Kind.Seq, CollectionsTransform.Kind.Stack).foreach { k =>
      val row = OverridesTarget(k.toString)
      assert(declares(row, sig("remove", Param.Prim("int"))),
             s"$k: java's remove(int) IS scala's Buffer.remove(Int) and must keep its override")
      assert(!declares(row, sig("remove", Param.Named("Object"))),
             s"$k: java's by-value remove has no counterpart on a Buffer — E038 if the modifier stays")
    }
  }

  test("a Buffer target strips every java List member scala spells otherwise") {
    val row = OverridesTarget(CollectionsTransform.Kind.Seq.toString)
    assert(!declares(row, sig("get", Param.Prim("int"))))          // scala's is `apply`
    assert(!declares(row, sig("set", Param.Prim("int"), Param.Named("E")))) // scala's is `update`
    assert(!declares(row, sig("add", Param.Named("E"))))           // scala's is `addOne`/`append`
    assert(!declares(row, sig("sort", Param.Named("Comparator")))) // scala's is `sortInPlace`
    List("listIterator", "spliterator", "subList", "replaceAll", "containsAll", "indexOf",
         "lastIndexOf", "toArray").foreach(n => assert(!declares(row, sig(n))))
  }

  // -------------------------------------------------------------------------------------------
  // …and the tables' own shape
  // -------------------------------------------------------------------------------------------

  test("every shim row is keyed on a target the phase actually mints") {
    OverridesShim.keys.foreach(k =>
      assert(CollectionsTransform.standaloneTargets(k), s"$k is not a standalone target of this phase"))
  }

  test("no row exists for a kind that cannot BE a parent — Entry is uninheritable, Opt is an alias") {
    assert(!OverridesTarget.contains(CollectionsTransform.Kind.Entry.toString))
    assert(!OverridesTarget.contains(CollectionsTransform.Kind.Opt.toString))
  }
