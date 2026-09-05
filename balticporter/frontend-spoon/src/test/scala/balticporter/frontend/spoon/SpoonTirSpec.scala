package balticporter.frontend.spoon

import balticporter.core.AnnotationPolicy
import balticporter.tir.*
import balticporter.tir.TypeRepr.*

/** Proves build-order step 2: the TIR is populated from REAL Spoon resolution, and the
  * kinded whole-program xref traces every type usage — external types, a class
  * type-parameter F-bound, member types, mixins — and still responds to a phase rewrite. */
class SpoonTirSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |import java.util.List;
      |interface Marker {}
      |class Base {}
      |class Holder<T extends Comparable<T>> extends Base implements Marker {
      |  List<T> items;
      |  T best;
      |  int size;
      |  T pick(List<? extends T> more) { return best; }
      |  void reset() { size = 0; }
      |  void run() {
      |    reset();
      |    T x = pick(items);
      |    if (size < 10) { size = size + 1; }
      |  }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src)

  /** first symbol whose fullName matches, or fail. */
  private def sym(full: String): SymId =
    program.symbols.all.find(_.fullName == full).map(_.id).getOrElse(fail(s"no symbol for $full"))

  /** a method/field symbol by owner-qualified name `demo.Holder#name`. */
  private def member(name: String): SymId =
    program.symbols.all.find(_.fullName == name).map(_.id).getOrElse(fail(s"no member $name"))

  private def kinds(s: SymId): Set[UsageKind] = program.usages(s).map(_.kind).toSet

  test("populates symbols from Spoon: our types are defined, JDK types are external") {
    assert(program.definitionOf(sym("demo.Holder")).isDefined)
    assert(program.definitionOf(sym("demo.Base")).isDefined)
    // java.util.List and java.lang.Comparable are referenced but not defined locally.
    assertEquals(program.definitionOf(sym("java.util.List")), None)
    assert(program.usagesOf(sym("java.util.List")).nonEmpty)
  }

  test("traces external types across their positions") {
    // Base is the primary supertype; Marker is a mixin.
    assertEquals(kinds(sym("demo.Base")), Set(UsageKind.Extends))
    assertEquals(kinds(sym("demo.Marker")), Set(UsageKind.Mixin))
    // List appears only as an applied constructor (field type and param type).
    assertEquals(kinds(sym("java.util.List")), Set(UsageKind.Tycon))
    // Comparable appears only inside T's bound, as the applied constructor there.
    assertEquals(kinds(sym("java.lang.Comparable")), Set(UsageKind.Tycon))
  }

  test("traces a class type-parameter F-bound across every position it flows to") {
    val t = sym("demo.Holder$$T")
    // T is: an arg of Comparable[T] in its OWN bound, the `best` member type,
    // an arg of List[T], and the wildcard bound in List[? extends T].
    assertEquals(kinds(t), Set(UsageKind.TypeArg, UsageKind.MemberType, UsageKind.Bound))
    // it is a genuine F-bound: T occurs inside its own declared bound.
    assert(program.usagesOf(t, UsageKind.TypeArg).nonEmpty)
  }

  test("xref responds after a phase rewrites java.util.List -> scala List") {
    val listId = sym("java.util.List")
    val target = SymId(9999)

    val swap = new Phase:
      def name = "juList->scalaList"
      override def transformType(x: TypeRepr)(using Program): TypeRepr = x match
        case TypeRef(p, s) if s == listId => TypeRef(p, target)
        case other                        => other

    val after = Pipeline.run(program, List(swap))
    assertEquals(after.usagesOf(listId), Nil)                                  // old symbol vacated
    assertEquals(after.usages(target).map(_.kind).toSet, Set(UsageKind.Tycon)) // new symbol inherits
  }

  test("translates bodies: method calls and field refs become traced usages") {
    val pick  = member("demo.Holder#pick")
    val reset = member("demo.Holder#reset")
    val best  = member("demo.Holder#best")
    val items = member("demo.Holder#items")
    // run() calls reset() and pick() — both recorded as Call usages.
    assert(program.usagesOf(pick, UsageKind.Call).nonEmpty)
    assert(program.usagesOf(reset, UsageKind.Call).nonEmpty)
    // pick()'s body reads the `best` field; run() reads `items`.
    assert(program.usagesOf(best, UsageKind.TermRef).nonEmpty)
    assert(program.usagesOf(items, UsageKind.TermRef).nonEmpty)
  }

  test("callersOf is a real call-graph edge over translated bodies") {
    val run   = member("demo.Holder#run")
    val pick  = member("demo.Holder#pick")
    val reset = member("demo.Holder#reset")
    // run is the sole caller of both pick and reset; pick has no callers.
    assertEquals(program.callersOf(pick), List(run))
    assertEquals(program.callersOf(reset), List(run))
    assertEquals(program.callersOf(run), Nil)
  }

  // -------------------------------------------------------------------------
  // ANNOTATIONS on every declaration kind — the parameter half was missing.

  private val annotated = SpoonTir.fromSource(
    """package demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS)
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |@interface Null {}
      |@interface Tag { String value(); }
      |class Ann {
      |  @Null Object field;
      |  @Null Object ret(@Null Object a, Object b, @Tag("x") Object c) { return a; }
      |  void varargs(@Null Object... rest) {}
      |}
      |""".stripMargin)

  /** A PARAMETER cannot be found by `fullName` — the frontend qualifies it against its method
    * before the method's own record is set, so its name is the minter's placeholder (see
    * `RuleScope`). Reached through the method's definition instead, which is how every phase
    * reaches one. */
  private def paramSym(method: String, param: String): Symbol =
    val m = annotated.symbols.all.find(_.fullName == method).getOrElse(fail(s"no method $method"))
    val d = annotated.definitionOf(m.id).collect { case d: Tree.DefDef => d }.getOrElse(fail(s"no def $method"))
    d.paramss.flatten.map(_.symbol).flatMap(annotated.symbolOf).find(_.name == param)
      .getOrElse(fail(s"no parameter $param of $method"))

  private def annotsOf(s: Symbol): List[String] =
    s.annotations.flatMap(a => a.tpe match
      case TypeRef(_, x) => annotated.symbolOf(x).map(_.fullName)
      case _             => None)

  private def annots(full: String): List[String] =
    annotsOf(annotated.symbols.all.find(_.fullName == full).getOrElse(fail(s"no symbol $full")))

  test("a PARAMETER's annotations are harvested — the (a) gap a nullability rule needs closed") {
    assertEquals(annotsOf(paramSym("demo.Ann#ret", "a")), List("demo.Null"))
    assertEquals(annotsOf(paramSym("demo.Ann#ret", "b")), Nil)
    assertEquals(annotsOf(paramSym("demo.Ann#varargs", "rest")), List("demo.Null"))
  }

  test("field and method annotations are unchanged by the parameter harvest") {
    assertEquals(annots("demo.Ann#field"), List("demo.Null"))
    assertEquals(annots("demo.Ann#ret"), List("demo.Null"))
  }

  test("an ARGUMENT-CARRYING parameter annotation is carried whole, not dropped") {
    // the body translator is in scope for a parameter exactly as it is for a method, so `@Tag("x")`
    // keeps its argument instead of being reported as an annotation the frontend could not carry —
    // emitting `@Tag` where Java wrote `@Tag("x")` would be a different annotation.
    val c = paramSym("demo.Ann#ret", "c")
    assertEquals(annotsOf(c), List("demo.Tag"))
    assertEquals(c.droppedAnnotations, Nil)
    assertEquals(c.annotations.head.args.map(_._1), List("value"))
  }

  // -------------------------------------------------------------------------
  // …and the TYPE, which had no translator at all — `ENGINE-LIMITS.md` T16.

  private val typeAnnotated =
    """package demo;
      |@interface Ser { Class<?> using(); }
      |@Ser(using = Ser.class)
      |interface Model {}
      |@interface Marker {}
      |@Marker
      |class Plain {}
      |""".stripMargin

  private def typeAnns(p: Program, full: String): (List[String], List[String]) =
    val s = p.symbols.all.find(_.fullName == full).getOrElse(fail(s"no symbol $full"))
    (s.annotations.flatMap(a => a.tpe match
       case TypeRef(_, x) => p.symbolOf(x).map(_.fullName)
       case _             => None),
     s.droppedAnnotations)

  test("a TYPE's argument-bearing annotation is DROPPED when the port claims no family") {
    // §1(b): the empty parameter is the no-op, and the no-op is what every port did before the
    // translator existed. The drop is REPORTED — `omissions` is where a port reads it.
    val p = SpoonTir.fromSource(typeAnnotated)
    assertEquals(typeAnns(p, "demo.Model"), (Nil, List("demo.Ser")))
  }

  test("…and CARRIED, with its arguments, when the port claims the family") {
    val p = SpoonTir.fromSource(typeAnnotated, annotations = AnnotationPolicy(List("demo.")))
    val (carried, dropped) = typeAnns(p, "demo.Model")
    assertEquals(carried, List("demo.Ser"))
    assertEquals(dropped, Nil)
    val a = p.symbols.all.find(_.fullName == "demo.Model").get.annotations.head
    assertEquals(a.args.map(_._1), List("using"))
  }

  test("a MARKER on a type needs no policy — it never needed a translator") {
    // The asymmetry is the whole reason the policy exists: a marker is carried unconditionally at
    // every declaration kind (`@SafeVarargs` is a catalog row that depends on it), and only the
    // ones whose ELEMENT VALUES have to be translated are a question at all.
    val p = SpoonTir.fromSource(typeAnnotated)
    assertEquals(typeAnns(p, "demo.Plain"), (List("demo.Marker"), Nil))
  }

  test("the policy cuts at a SEPARATOR, never on a bare prefix (§4.56)") {
    val p = AnnotationPolicy(List("com.foo"))
    assert(p.claims("com.foo.Bar"))
    assert(p.claims("com.foo.Bar$Baz"))
    assert(p.claims("com.foo"))
    assert(!p.claims("com.foobar.Bar"), "a prefix is not a structural fact about anything")
    assert(!AnnotationPolicy.none.claims("com.foo.Bar"), "the empty policy claims nothing")
    // …and a port may write the trailing dot or not and get the same answer.
    assertEquals(AnnotationPolicy(List("com.foo.")).claims("com.foo.Bar"), p.claims("com.foo.Bar"))
  }

  // ---- isFinal on external types (K18) ----

  test("an external FINAL class (java.lang.String) is interned with isFinal = true") {
    val p = SpoonTir.fromSource(
      """package demo;
        |class T { String s; }
        |""".stripMargin)
    val strSym = p.symbols.all.find(_.fullName == "java.lang.String")
    assert(clue(strSym).isDefined)
    assert(clue(strSym.get.flags.isFinal), "String is a final class")
  }

  test("an external NON-FINAL class (java.util.ArrayList) is interned with isFinal = false") {
    val p = SpoonTir.fromSource(
      """package demo;
        |import java.util.ArrayList;
        |class T { ArrayList<String> xs; }
        |""".stripMargin)
    val alSym = p.symbols.all.find(_.fullName == "java.util.ArrayList")
    assert(clue(alSym).isDefined)
    assert(!clue(alSym.get.flags.isFinal), "ArrayList is not final")
  }

  test("an external INTERFACE (java.lang.CharSequence) is interned with isFinal = false") {
    val p = SpoonTir.fromSource(
      """package demo;
        |class T { CharSequence cs; }
        |""".stripMargin)
    val csSym = p.symbols.all.find(_.fullName == "java.lang.CharSequence")
    assert(clue(csSym).isDefined)
    assert(!clue(csSym.get.flags.isFinal), "CharSequence is an interface, never final")
  }
