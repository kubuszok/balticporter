package balticporter.frontend.spoon

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
      |  T pick(List<? extends T> more) { return best; }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src)

  /** first symbol whose fullName matches, or fail. */
  private def sym(full: String): SymId =
    program.symbols.all.find(_.fullName == full).map(_.id).getOrElse(fail(s"no symbol for $full"))

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
