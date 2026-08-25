package balticporter.runtime

import scala.collection.mutable.ArrayBuffer

/** The `JavaCollections` members whose SIGNATURE names a JDK type Scala.js does not implement.
  *
  * `java.util.Spliterator` and `java.util.Spliterators` are absent from `scalajs-javalib` entirely
  * — not a missing method on a type that exists, the TYPE. Scala.js compiles against the real JDK
  * and checks availability at LINK time, so `orderedSpliterator` / `distinctSpliterator` compile
  * on every row of the matrix and refuse to link on the JS one the moment anything reaches them.
  * Scala Native implements both, and links these fine; they are here rather than in
  * `src/test/scalanative` because a test dir per platform-that-happens-to-work is a list that goes
  * stale, while "the JVM is the row that has everything" does not.
  *
  * They are NOT moved out of `JavaCollectionsSpec` because they are less important. They are moved
  * because the alternative is a JS suite that does not link at all, which would take the other 170
  * assertions with it — and a suite that cannot run is the one thing worse than a residue that is
  * written down (`PROGRESS.md` §13, Phase 0).
  */
class JavaCollectionsSpliteratorSpec extends munit.FunSuite:

  test("spliterator reports JAVA'S OWN characteristics — the cell K23's refusal was about") {
    // The whole content of that fix, and the one thing no compile can check.
    // `buf.asJava.spliterator()` — the near miss the refusal rested on — reports NEITHER `ORDERED`
    // nor `SIZED` where the `ArrayList` java held reports both, so a consumer reading
    // `characteristics()` gets a different answer silently (CLAUDE.md §4.4). These assert the answer
    // java's OWN defaults give, which is what the two helpers reproduce.
    val ordered = JavaCollections.orderedSpliterator(ArrayBuffer("a", "b", "c"))
    assert(ordered.hasCharacteristics(java.util.Spliterator.ORDERED), "List.spliterator() passes ORDERED")
    assert(ordered.hasCharacteristics(java.util.Spliterator.SIZED),
           "…and `Spliterators.spliterator(Collection, int)` ORs in SIZED — the half `asJava` loses")
    assert(ordered.hasCharacteristics(java.util.Spliterator.SUBSIZED))
    assertEquals(ordered.estimateSize(), 3L)

    val distinct = JavaCollections.distinctSpliterator(scala.collection.mutable.Set("a", "b"))
    assert(distinct.hasCharacteristics(java.util.Spliterator.DISTINCT), "Set.spliterator() passes DISTINCT")
    assert(distinct.hasCharacteristics(java.util.Spliterator.SIZED))
    // …and NOT ORDERED, which is the difference between the two helpers and the reason there are two
    // names rather than one taking a characteristics constant.
    assert(!distinct.hasCharacteristics(java.util.Spliterator.ORDERED))
  }

  test("…and it TRAVERSES the collection, in the collection's own order") {
    // characteristics are a CLAIM about the traversal; this is the traversal. A spliterator that
    // reported ORDERED and handed back nothing would pass the test above.
    val seen = ArrayBuffer.empty[String]
    JavaCollections.orderedSpliterator(ArrayBuffer("a", "b", "c"))
      .forEachRemaining((s: String) => { seen += s; () })
    assertEquals(seen.toList, List("a", "b", "c"))
  }

  test("MEASURED: `asJava.spliterator()` agrees today — K23's recorded NEAR MISS does not reproduce") {
    // K23 refused `spliterator` and its stated evidence was that `buf.asJava.spliterator()` reports
    // NEITHER `ORDERED` nor `SIZED` where the `ArrayList` java held reports both. On scala 3.8.4 and
    // this JDK that is FALSE: the converter hands back a `java.util.List` wrapper whose
    // `spliterator()` is `List`'s own default, so it reports ORDERED, SIZED and SUBSIZED — exactly
    // what the two helpers above produce, characteristics `16464` either way.
    //
    // So the refusal rested on a measurement that no longer holds, and the honest record is this
    // assertion rather than the prose. It is pinned in the OTHER direction from the test it
    // replaces: if a future converter stopped agreeing, this says so, and the reason to state
    // java's answer rather than inherit it becomes the loud one instead of the quiet one.
    //
    // Why the helpers stay anyway: they make the characteristics follow JAVA'S DECLARATION at the
    // owner the receiver was typed by, which is a fact a reader can check against the JDK source,
    // instead of following what scala's converter happens to wrap the collection in. That is the
    // same argument §4.5 makes for a standalone shim over an inherited one, and it is deliberately
    // NOT the argument the refusal made.
    import scala.jdk.CollectionConverters.*
    val viaAsJava = ArrayBuffer("a", "b", "c").asJava.spliterator()
    assert(viaAsJava.hasCharacteristics(java.util.Spliterator.ORDERED),
           "the converter's wrapper DOES report ORDERED — K23's near miss is not reproducible")
    assert(viaAsJava.hasCharacteristics(java.util.Spliterator.SIZED),
           "…and SIZED")
    assertEquals(viaAsJava.characteristics(),
                 JavaCollections.orderedSpliterator(ArrayBuffer("a", "b", "c")).characteristics(),
                 "and it agrees with the helper exactly, which is what makes this a measurement " +
                 "about the REASON rather than about the answer")
  }
