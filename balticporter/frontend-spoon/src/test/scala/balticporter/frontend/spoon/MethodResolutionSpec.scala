package balticporter.frontend.spoon

import balticporter.tir.*

/** G34 — under `noClasspath`, Spoon's `getExecutableDeclaration` can resolve to an UNRELATED type's
  * method that happens to share the name.  The frontend must validate that the declaration's owner
  * is the receiver's static type or a supertype (an inherited method), and fall through when it is
  * not. */
class MethodResolutionSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |interface A { int getType(); }
      |class B implements A { public int getType() { return 0; } }
      |class C extends B {}
      |class F { public String getType() { return ""; } }
      |class User {
      |  void useA(A a) { a.getType(); }
      |  void useC(C c) { c.getType(); }
      |  void useF(F f) { f.getType(); }
      |}
      |""".stripMargin

  private val program = SpoonTir.fromSource(src)
  private given Program = program

  private def sym(full: String): SymId =
    program.symbols.all.find(_.fullName == full).map(_.id).getOrElse(fail(s"no symbol: $full"))

  /** Collect all Apply.method SymIds from a DefDef body. */
  private def calleeIds(callerFull: String): Set[SymId] =
    val callerId = sym(callerFull)
    program.definitionOf(callerId) match
      case Some(d: Tree.DefDef) =>
        StandardTraversal.scanTerm(d.rhs.getOrElse(fail("no body")), Set.empty[SymId]) {
          case (acc, a: Tree.Apply) => acc + a.method
          case (acc, _)             => acc
        }
      case other => fail(s"expected DefDef for $callerFull, got $other")

  /** Resolve the owning type's qualified name for a method symbol. */
  private def ownerQ(s: SymId): String =
    program.symbolOf(s).map(_.fullName).getOrElse("?") match
      case full if full.contains('#') => full.substring(0, full.indexOf('#'))
      case full                       => full

  test("direct call on interface binds the interface's own method") {
    val ids = calleeIds("demo.User#useA")
    val owners = ids.map(ownerQ)
    assert(
      owners.exists(q => q == "demo.A" || q == "demo.B"),
      s"useA's callee owners should include demo.A or demo.B, got: $owners"
    )
    assert(
      !owners.contains("demo.F"),
      s"useA's callee owners should NOT include demo.F, got: $owners"
    )
  }

  test("inherited method call binds through the hierarchy") {
    val ids = calleeIds("demo.User#useC")
    val owners = ids.map(ownerQ)
    assert(
      owners.exists(q => q == "demo.A" || q == "demo.B"),
      s"useC's callee owners should include demo.A or demo.B, got: $owners"
    )
    assert(
      !owners.contains("demo.F"),
      s"useC's callee owners should NOT include demo.F, got: $owners"
    )
  }

  test("call on unrelated type keeps its own binding") {
    val ids = calleeIds("demo.User#useF")
    val owners = ids.map(ownerQ)
    assert(
      owners.contains("demo.F"),
      s"useF's callee owners should include demo.F, got: $owners"
    )
    assert(
      !owners.exists(q => q == "demo.A" || q == "demo.B"),
      s"useF's callee owners should NOT include demo.A or demo.B, got: $owners"
    )
  }
