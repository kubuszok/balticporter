package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{Pipeline, Program}
import balticporter.transform.{CollectionBoundaryCheck, CollectionsTransform}

/** A REIFIED occurrence of a retyped type — `ENGINE-LIMITS.md` K18, catalog `JS-G48`. */
class CollectionsReifiedSpec extends PortSuite:

  private def ported(source: String): (CollectionsTransform, Program, String) =
    val ph    = new CollectionsTransform
    val after = Pipeline.run(SpoonTir.fromSource(source), List(ph))
    (ph, after, new TirEmitter(after).emit)

  private val Reified = "balticporter.runtime.JavaCollections.Reified"

  // -------------------------------------------------------------------------
  // 1-3. the translation
  // -------------------------------------------------------------------------

  test("a type TEST at a mapped type asks about BOTH representations, not about scala's alone") {
    val (_, _, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  boolean isMap(Object v)  { return v instanceof Map; }
        |  boolean isList(Object v) { return v instanceof List; }
        |  boolean isSet(Object v)  { return v instanceof Set; }
        |}
        |""".stripMargin)
    assert(clue(out).contains(s"$Reified.isMap("))
    assert(out.contains(s"$Reified.isBuffer("))
    assert(out.contains(s"$Reified.isSet("))
    assert(!out.contains("isInstanceOf[scala.collection.mutable."),
           "no bare test against a mapping target survives — that is the whole defect")
  }

  test("a DOWNCAST from Object becomes the coercion, and java's own cast is KEPT around it") {
    val (_, _, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  int size(Object v) { return ((Map<String, Object>) v).size(); }
        |}
        |""".stripMargin)
    assert(clue(out).contains(s"$Reified.asMap("))
    assert(out.contains("asInstanceOf[scala.collection.mutable.Map[java.lang.String, java.lang.Object]]"),
           "java's cast is unchecked in its type arguments and the emitted cast says so")
  }

  test("the SHIM targets are reified positions too — Collection, Iterable, Iterator") {
    val (_, _, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  boolean isColl(Object v) { return v instanceof Collection; }
        |  boolean isIter(Object v) { return v instanceof Iterable; }
        |  boolean isItr(Object v)  { return v instanceof Iterator; }
        |  int n(Object v) { return ((Collection<?>) v).size(); }
        |}
        |""".stripMargin)
    assert(clue(out).contains(s"$Reified.isCollection("))
    assert(out.contains(s"$Reified.isIterable("))
    assert(out.contains(s"$Reified.isIterator("))
    assert(out.contains(s"$Reified.asCollection("))
  }

  // -------------------------------------------------------------------------
  // 4-5. what the phase VOUCHES for, and the one exception
  // -------------------------------------------------------------------------

  test("an UPCAST of a value this phase RETYPED is not a reified question and is left alone") {
    val (_, _, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  private Map<String, Object> own = new HashMap<String, Object>();
        |  Map<? extends String, ? extends Object> widen() { return (Map<? extends String, ? extends Object>) own; }
        |}
        |""".stripMargin)
    assert(!clue(out).contains(s"$Reified.asMap("),
           "the operand is a declaration this phase retyped: the representation is known")
  }

  test("…and an EXTERNAL PRODUCER is NOT vouched for, however its node type reads") {
    val (_, _, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  Map<String, Object> read(java.util.Properties p) {
        |    return (Map<String, Object>) p.clone();
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains(s"$Reified.asMap("),
           "the value is whatever the class file makes; the node's type only reads as a target " +
             "because transformType is position-blind")
  }

  test("a type the PROGRAM DECLARES is vouched for by OWNERSHIP — every instance of it is the port's") {
    // The shape that made this a rule rather than a nicety: libGDX's `Queue.iterator()` casts a
    // class the port emits to the shim that class already implements, and a coercion there is an
    // identity call on a hot path — 9 members, every one of them a `for` loop's iterator.
    val (_, _, out) = ported(
      """package demo;
        |import java.util.*;
        |class Own implements Iterator<String> {
        |  public boolean hasNext() { return false; }
        |  public String next() { return null; }
        |}
        |class T {
        |  Iterator<String> it() { return (Iterator<String>) new Own(); }
        |}
        |""".stripMargin)
    assert(!clue(out).contains(s"$Reified.asIterator("),
           "the operand's type is declared by this program, so its representation is not in question")
  }

  test("a `null` cast has no runtime object to be about and is left exactly as it was") {
    val (_, _, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  Map<String, Object> none() { return (Map<String, Object>) null; }
        |}
        |""".stripMargin)
    assert(!clue(out).contains(s"$Reified.asMap("))
  }

  // -------------------------------------------------------------------------
  // 6. the refusal, and it is COUNTED
  // -------------------------------------------------------------------------

  test("a CONCRETE target is refused and counted — no live view can BE a mutable.HashMap") {
    val (ph, program, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  int size(Object v) { return ((HashMap<String, Object>) v).size(); }
        |}
        |""".stripMargin)
    assert(!clue(out).contains(s"$Reified.as"), "nothing was approximated")
    val reified = ph.boundary(program)
      .filter(_.issue == CollectionBoundaryCheck.Issue.ReifiedOccurrence)
    assertEquals(clue(reified).size, 1)
    assert(reified.head.detail.contains("reified cast"))
    assert(CollectionBoundaryCheck.Issue.classification(CollectionBoundaryCheck.Issue.ReifiedOccurrence)
             .contains("§1(a)"))
  }

  // -------------------------------------------------------------------------
  // 7. …and the target the phase did NOT retype, which reads as nothing at all

  test("a reified test at an UNMAPPED JDK SUPERTYPE of a mapped type is refused and COUNTED") {
    val (ph, program, out) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  boolean fast(List<String> xs) { return xs instanceof RandomAccess; }
        |}
        |""".stripMargin)
    assert(clue(out).contains("isInstanceOf[java.util.RandomAccess]"), "left exactly as java wrote it")
    val reified = ph.boundary(program)
      .filter(_.issue == CollectionBoundaryCheck.Issue.ReifiedOccurrence)
    assertEquals(clue(reified).size, 1)
    assert(reified.head.detail.contains("reified type test"))
  }

  test("…and a CAST to one, which is the same question at the other node kind") {
    val (ph, program, _) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  Object sorted(Object v) { return (SortedMap<String, Object>) v; }
        |}
        |""".stripMargin)
    val reified = ph.boundary(program)
      .filter(_.issue == CollectionBoundaryCheck.Issue.ReifiedOccurrence)
    assertEquals(clue(reified).size, 1)
    assert(reified.head.detail.contains("reified cast"))
  }

  test("NEGATIVE — an ordinary JDK type that no mapped type inherits counts NOTHING") {
    // The direction that makes the count mean something. `java.lang.String` and
    // `java.lang.Runnable` are not supertypes of anything this phase retypes, so a test or a cast
    // at one is an ordinary reified question the retyping did not move — and a check that reported
    // it would be a check nobody could act on.
    val (ph, program, _) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  boolean isStr(Object v) { return v instanceof String; }
        |  Runnable run(Object v)  { return (Runnable) v; }
        |  List<String> keep(List<String> xs) { return xs; }
        |}
        |""".stripMargin)
    assertEquals(clue(ph.boundary(program))
      .count(_.issue == CollectionBoundaryCheck.Issue.ReifiedOccurrence), 0)
  }

  test("NEGATIVE — `java.lang.Object` is in every closure and is never a divergence") {
    // Everything is an `Object` in scala too, so the answer did not move. Excluded by construction
    // rather than by luck, because it is in the supertype closure of every mapped type.
    val (ph, program, _) = ported(
      """package demo;
        |import java.util.*;
        |class T {
        |  boolean isObj(Object v) { return v instanceof Object; }
        |}
        |""".stripMargin)
    assertEquals(clue(ph.boundary(program))
      .count(_.issue == CollectionBoundaryCheck.Issue.ReifiedOccurrence), 0)
  }

  // -------------------------------------------------------------------------
  // the catalog row is CITED, per declaration
  // -------------------------------------------------------------------------

  test("JS-G48 is cited at every declaration whose reified occurrence moved, and at no other") {
    val program = SpoonTir.fromSource(
      """package demo;
        |import java.util.*;
        |class T {
        |  boolean a(Object v) { return v instanceof Map; }
        |  boolean b(Object v) { return v instanceof List; }
        |  boolean untouched(Object v) { return v instanceof String; }
        |}
        |""".stripMargin)
    val catalog = new balticporter.catalog.CatalogLog
    Pipeline.runTraced(program, List(new CollectionsTransform),
                       new balticporter.tir.PolicyBinder(program, program.members), catalog)
    val cited = catalog.citedAt(balticporter.catalog.JS.G(48))
    assertEquals(clue(cited).size, 2)
    assert(cited.exists(_.contains("#a")))
    assert(cited.exists(_.contains("#b")))
    assert(!cited.exists(_.contains("untouched")),
           "a citation is per DECLARATION, and this one's test is not at a mapped type")
  }
