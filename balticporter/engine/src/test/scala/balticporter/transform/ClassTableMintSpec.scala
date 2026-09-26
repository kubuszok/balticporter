package balticporter.transform

import balticporter.core.{ MergeablePolicy, PortManifest, SurfaceFold }
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ Decision, DecisionLog, Phase, Pipeline, PolicyBinder, Program, RewriteTrace, RuleScope, RunScope, SymId }

/** `ClassTableTransform`'s MINTED table: the object is written from a seed list, the call site keeps the shape it had when the port supplied the table itself, and every seed the table cannot name or
  * build is refused and reported.
  */
class ClassTableMintSpec extends munit.FunSuite:
  import ClassTableTransform.{ Missing, Table }

  private val java =
    """package com.demo;
      |class Reflect {
      |  static Class<?> forName(String n) { return null; }
      |  static Object make(String n) { return null; }
      |}
      |public class Widget { }
      |public class NeedsArg { public NeedsArg(int x) { } }
      |public abstract class Shape { }
      |public class Hidden { private Hidden() { } }
      |class Alpha { Object go(String s) { return Reflect.forName(s); } }
      |class Beta  { Object go(String s) { return Reflect.make(s); } }
      |""".stripMargin

  private val Placement = "com.demo.Names"
  private val redirects = Map("com.demo.Reflect#forName" -> s"$Placement#classFor", "com.demo.Reflect#make" -> s"$Placement#newInstanceFor")

  private def parse(): Program = SpoonTir.fromSource(java, "Demo.java")

  private case class Ported(after: Program, out: String, log: DecisionLog)

  private def run(p: Phase): Ported =
    val (after, log) = Pipeline.runTraced(parse(), List(p))
    Ported(after, new TirEmitter(after, notes = log).emit, log)

  private def tbl(seeds: List[String] = List("com.demo.Widget"), construct: Option[String] = Some("newInstanceFor"), missing: Option[Missing] = None) =
    Table(Placement, seeds, "classFor", construct, missing)

  private def phase(t: Table*) = new ClassTableTransform(redirects, RuleScope.everywhere, t.toList)

  private def refusals(p: ClassTableTransform, key: String) = p.policyReport.findings.filter(_.key == key)

  // ---- the no-op --------------------------------------------------------------------------------

  test("no table is the old mode: nothing minted, the same emission and the same fingerprint") {
    val old  = new ClassTableTransform(redirects)
    val none = phase()
    assertEquals(none.surfaceFingerprint, old.surfaceFingerprint)
    assert(!clue(none.surfaceFingerprint).contains("tables"))
    assertEquals(run(none).out, run(old).out)
    assert(!run(none).out.contains("object Names"))
  }

  // ---- minting ----------------------------------------------------------------------------------

  test("a declared table is minted as an object listing its seeds by class literal") {
    val r = run(phase(tbl()))
    assert(clue(r.out).contains("object Names"), clue(r.out))
    assert(r.out.contains("private val bpClassTable: scala.collection.immutable.Map[java.lang.String, java.lang.Class[?]]"))
    assert(r.out.contains("scala.collection.immutable.List[java.lang.Class[?]](classOf[com.demo.Widget])"))
    assert(r.out.contains("def classFor(name: java.lang.String): java.lang.Class[?]"))
    assert(r.out.contains("(classOf[com.demo.Widget].getName, () => new com.demo.Widget)"))
    assert(r.out.contains("def newInstanceFor(name: java.lang.String): java.lang.Object"))
  }

  test("the call site is the one the old mode wrote") {
    val minted = run(phase(tbl())).out
    val old    = run(new ClassTableTransform(redirects)).out
    List("com.demo.Names.classFor(s)", "com.demo.Names.newInstanceFor(s)").foreach { site =>
      assert(clue(old).contains(site))
      assert(clue(minted).contains(site))
    }
    assert(!minted.contains("Reflect.forName(s)"))
  }

  test("the minted members are declarations: no orphaned call, one AddedMember per member") {
    val r = run(phase(tbl()))
    assertEquals(clue(RewriteTrace.check(r.after).filter(_.what == "call to a member with no declaration").map(_.name)), Nil)
    assertEquals(
      r.log.all.filter(_.kind == Decision.Kind.AddedMember).flatMap(_.detail.get("member")).sorted,
      List("classFor", "newInstanceFor")
    )
  }

  test("an unknown name throws java's ClassNotFoundException, or the library's wrapper around it") {
    val bare = run(phase(tbl())).out
    assert(clue(bare).contains("throw new java.lang.ClassNotFoundException(name)"))
    val wrapped = run(phase(tbl(missing = Some(Missing("com.demo.Oops", "Class not found: ", "Could not instantiate: "))))).out
    assert(
      clue(wrapped).contains("""throw new com.demo.Oops("Class not found: " + name, new java.lang.ClassNotFoundException(name))""")
    )
    assert(
      wrapped.contains("""throw new com.demo.Oops("Could not instantiate: " + name, new java.lang.InstantiationException(name))""")
    )
    assert(!wrapped.substring(wrapped.indexOf("object Names")).contains("null"), clue(wrapped))
  }

  // ---- refusals ---------------------------------------------------------------------------------

  test("a seed naming no class is refused and reported") {
    val p = phase(tbl(seeds = List("com.demo.Widget", "com.demo.NotHere")))
    val r = run(p)
    assertEquals(clue(refusals(p, "com.demo.NotHere")).size, 1)
    assert(!r.out.contains("NotHere"), clue(r.out))
  }

  test("a seed with no callable no-argument constructor is refused for construction and still listed by name") {
    val p   = phase(tbl(seeds = List("com.demo.Widget", "com.demo.NeedsArg", "com.demo.Shape", "com.demo.Hidden")))
    val out = run(p).out
    List("com.demo.NeedsArg", "com.demo.Shape", "com.demo.Hidden").foreach { s =>
      assertEquals(clue(refusals(p, s)).size, 1)
      assert(clue(out).contains(s"classOf[$s]"))
      assert(!out.contains(s"() => new $s"), clue(out))
    }
    assert(refusals(p, "com.demo.NeedsArg").head.detail.contains("no no-argument constructor"))
    assert(refusals(p, "com.demo.Shape").head.detail.contains("abstract"))
    // …and a lookup-only table asks nothing of a constructor.
    val lookupOnly = phase(tbl(seeds = List("com.demo.NeedsArg"), construct = None))
    run(lookupOnly)
    assertEquals(refusals(lookupOnly, "com.demo.NeedsArg"), Nil)
  }

  test("a redirect into a minted table naming a member it does not mint is reported and skipped") {
    val p = new ClassTableTransform(Map("com.demo.Reflect#forName" -> s"$Placement#other"), RuleScope.everywhere, List(tbl()))
    val r = run(p)
    assert(clue(p.policyReport.findings.map(_.detail).mkString).contains("mints only"))
    assert(r.out.contains("Reflect.forName(s)"), clue(r.out))
  }

  test("a placement the program already declares mints nothing and is reported") {
    val p = new ClassTableTransform(
      Map("com.demo.Reflect#forName" -> "com.demo.Widget#classFor"),
      RuleScope.everywhere,
      List(tbl().copy(placement = "com.demo.Widget"))
    )
    run(p)
    assertEquals(clue(refusals(p, "com.demo.Widget")).map(_.setting), List("ClassTableTransform(tables).placement"))
  }

  test("a module that does not emit the call sites rewrites them and mints nothing") {
    val before       = parse()
    val emitsNothing = new RunScope:
      def emits(unit:        SymId):  Boolean             = false
      def contributed(phase: String): Option[Set[String]] = scala.None
    val p            = phase(tbl())
    val (after, log) = Pipeline.runTraced(before, List(p), new PolicyBinder(before, before.members, emitsNothing))
    val out          = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains("com.demo.Names.classFor(s)"))
    assert(!out.contains("object Names"))
  }

  // ---- surface ----------------------------------------------------------------------------------

  test("the placement, the member names, the seeds and the miss are fingerprinted") {
    val fp = phase(tbl(missing = Some(Missing("com.demo.Oops", "nf: ")))).surfaceFingerprint
    assert(clue(fp).contains(";tables:com.demo.Names:classFor/newInstanceFor(com.demo.Widget)!com.demo.Oops(nf: |)"))
    assertNotEquals(phase(tbl(seeds = List("com.demo.Shape"))).surfaceFingerprint, phase(tbl()).surfaceFingerprint)
  }

  test("two different tables at one placement refuse to merge; an identical one is idempotent") {
    def merged(a: ClassTableTransform, b: ClassTableTransform) = a.mergedWith(b).map { case MergeablePolicy.Merged(p, _) => p.asInstanceOf[ClassTableTransform] }
    assert(merged(phase(tbl()), phase(tbl(seeds = List("com.demo.Shape")))).isLeft)
    assertEquals(merged(phase(tbl()), phase(tbl())).map(_.tables.size), Right(1))
    val bad = PortManifest("base", governs = Set("com.demo"), surface = List(phase(tbl()))).extendedBy(PortManifest("dep", surface = List(phase(tbl(seeds = Nil)))))
    assertEquals(clue(bad.surfaceFold.refusals).map(_.cause), List(SurfaceFold.Cause.Conflict))
  }
