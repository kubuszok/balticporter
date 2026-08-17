package balticporter.frontend.spoon

import scala.jdk.CollectionConverters.*

import spoon.Launcher
import spoon.reflect.declaration.*
import spoon.reflect.reference.*
import spoon.support.compiler.VirtualFile

/** THE ONE CLASSIFICATION OF A TYPE REFERENCE — `CLAUDE.md` §4.56's match-arm rule, pinned.
  *
  * Spoon's `CtWildcardReference` EXTENDS `CtTypeParameterReference`, so `case tv:
  * CtTypeParameterReference` claims every `?` and any wildcard arm written under it is DEAD. That
  * is not a bug a count can find: the wrong answer is the conservative one, so no port emits
  * anything wrong, nothing moves, and the only symptom is a rule that fires nowhere
  * (`ENGINE-LIMITS.md` G21 — thirteen such matches at once, ten answer-changing).
  *
  * Three things are asserted here and each has a different failure mode:
  *
  *   1. the STRUCTURAL FACT the whole taxonomy rests on, read off the class hierarchy rather than
  *      assumed. §4.56 says to `javap` the interface; this is that, as a test, so a Spoon upgrade
  *      that changed the hierarchy would say so instead of silently making the arm order pointless;
  *   2. `TypeShape.of`'s ARM ORDER — a wildcard classifies as `Wildcard` and never as `Variable`.
  *      Verified failing by swapping the two arms in `of`, which is the exact edit that reintroduces
  *      the defect;
  *   3. the two PROJECTIONS `ref`/`args`, which are what let a migrated caller treat several kinds
  *      alike and still reproduce the `case r =>` it used to fall into. A projection that stopped
  *      agreeing with `getActualTypeArguments` would move answers at every one of those callers
  *      with nothing else to see it.
  */
class TypeShapeSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |import java.util.List;
      |import java.util.Map;
      |class Shapes<T extends Number> {
      |  T bare;
      |  List<?> unbounded;
      |  List<? extends Number> upper;
      |  List<? super Number> lower;
      |  Class<?> nestedWildcard;
      |  Map<String, Integer> applied;
      |  List raw;
      |  String concrete;
      |  int prim;
      |  String[] arr;
      |  T[] varArr;
      |  <U extends Number & Comparable<U>> void inter(U u) {}
      |}
      |""".stripMargin

  private lazy val cls: CtType[?] =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    env.setNoClasspath(true)
    launcher.addInputResource(new VirtualFile(src, "Shapes.java"))
    launcher.buildModel().getAllTypes.asScala.head

  private def fieldRef(name: String): CtTypeReference[?] =
    cls.getFields.asScala.find(_.getSimpleName == name)
      .getOrElse(fail(s"no field $name in the fixture")).getType

  /** the FIRST type argument of a field's type — the position a wildcard is writable at. */
  private def argRef(name: String): CtTypeReference[?] =
    fieldRef(name).getActualTypeArguments.asScala.headOption
      .getOrElse(fail(s"field $name has no type argument"))

  test("the STRUCTURAL FACT: Spoon's CtWildcardReference IS a CtTypeParameterReference") {
    // The premise of every arm order in `SpoonTir`. Read off the hierarchy, never assumed — if a
    // Spoon upgrade separated the two, most of the wildcard arms would become plain alternatives
    // and this file's whole argument would need re-reading.
    assert(classOf[CtTypeParameterReference].isAssignableFrom(classOf[CtWildcardReference]),
           "CtWildcardReference no longer extends CtTypeParameterReference — re-read ENGINE-LIMITS.md G21")
    // …and the converse is what makes the order matter rather than being a free choice.
    assert(!classOf[CtWildcardReference].isAssignableFrom(classOf[CtTypeParameterReference]))
  }

  test("ARM ORDER: every wildcard classifies as Wildcard, never as Variable") {
    // The negative this spec exists for. Swap `of`'s first two reference arms and all four fail.
    List("unbounded", "upper", "lower").foreach { f =>
      SpoonTir.TypeShape.of(argRef(f)) match
        case SpoonTir.TypeShape.Wildcard(_, _, _) => ()
        case other => fail(s"$f's argument classified as $other, not Wildcard")
    }
    // …and the NESTED one, which is the position `ENGINE-LIMITS.md` G21 is about: `Class<?>` is a
    // type this port can write, and its argument is the `?` the variable arm used to claim.
    SpoonTir.TypeShape.of(argRef("nestedWildcard")) match
      case SpoonTir.TypeShape.Wildcard(_, _, _) => ()
      case other => fail(s"Class<?>'s argument classified as $other, not Wildcard")
  }

  test("a real type VARIABLE still classifies as Variable — the arm order costs nothing") {
    SpoonTir.TypeShape.of(fieldRef("bare")) match
      case SpoonTir.TypeShape.Variable(tv) => assertEquals(tv.getSimpleName, "T")
      case other                           => fail(s"T classified as $other")
  }

  test("the WILDCARD's own two fields carry java's direction, which no other kind can state") {
    (SpoonTir.TypeShape.of(argRef("upper")), SpoonTir.TypeShape.of(argRef("lower"))) match
      case (SpoonTir.TypeShape.Wildcard(_, ub, upIsUpper), SpoonTir.TypeShape.Wildcard(_, lb, loIsUpper)) =>
        assert(upIsUpper, "? extends Number read as a LOWER bound")
        assert(!loIsUpper, "? super Number read as an UPPER bound")
        assertEquals(ub.map(_.getQualifiedName), Some("java.lang.Number"))
        assertEquals(lb.map(_.getQualifiedName), Some("java.lang.Number"))
      case other => fail(s"bounded wildcards classified as $other")
  }

  test("the remaining kinds each land in their own case, and nothing falls into Named by accident") {
    SpoonTir.TypeShape.of(null) match
      case SpoonTir.TypeShape.Absent => ()
      case other                     => fail(s"null classified as $other")
    SpoonTir.TypeShape.of(fieldRef("prim")) match
      case SpoonTir.TypeShape.Prim(_) => ()
      case other                      => fail(s"int classified as $other")
    SpoonTir.TypeShape.of(fieldRef("arr")) match
      case SpoonTir.TypeShape.Arr(_, c) => assertEquals(c.getQualifiedName, "java.lang.String")
      case other                        => fail(s"String[] classified as $other")
    // an array OF a type variable: the component is the variable, so a caller that recurses on the
    // component reaches the variable arm and not the array one.
    SpoonTir.TypeShape.of(fieldRef("varArr")) match
      case SpoonTir.TypeShape.Arr(_, c) =>
        assert(SpoonTir.TypeShape.of(c).isInstanceOf[SpoonTir.TypeShape.Variable], "T[]'s component is not a Variable")
      case other => fail(s"T[] classified as $other")
    List("concrete", "applied", "raw").foreach { f =>
      SpoonTir.TypeShape.of(fieldRef(f)) match
        case SpoonTir.TypeShape.Named(_, _) => ()
        case other                          => fail(s"$f classified as $other, not Named")
    }
  }

  test("an INTERSECTION bound classifies apart from Named — two callers answer it differently") {
    val u = cls.getMethods.asScala.find(_.getSimpleName == "inter")
      .getOrElse(fail("no inter method")).getFormalCtTypeParameters.asScala.head
    // `U extends Number & Comparable<U>`: Spoon models the bound as an intersection reference.
    SpoonTir.TypeShape.of(u.getSuperclass) match
      case SpoonTir.TypeShape.Intersection(_, bounds) =>
        assert(bounds.sizeIs >= 2, s"only ${bounds.size} bound(s) on an intersection")
      case other => fail(s"U's intersection bound classified as $other")
  }

  test("PROJECTIONS: `args` is exactly what getActualTypeArguments says, for every kind") {
    // The property the migrated catch-alls rest on. Written as a comparison against Spoon rather
    // than against a literal, because what a caller falling into `case r =>` used to compute IS
    // this call and nothing else.
    val refs = List("bare", "unbounded", "applied", "raw", "concrete", "prim", "arr", "nestedWildcard")
      .map(fieldRef) ++ List(argRef("unbounded"), argRef("upper"))
    refs.foreach { r =>
      val shape = SpoonTir.TypeShape.of(r)
      assertEquals(shape.args.map(_.getQualifiedName), r.getActualTypeArguments.asScala.toList.map(_.getQualifiedName),
                   s"`args` disagrees with Spoon for ${r.getQualifiedName}")
      assert(shape.ref eq r, s"`ref` is not the reference classified, for ${r.getQualifiedName}")
    }
    assertEquals(SpoonTir.TypeShape.of(null).args, Nil)
    assertEquals(SpoonTir.TypeShape.of(null).ref, null)
  }
