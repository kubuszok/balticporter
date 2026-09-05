package balticporter.transform

import balticporter.core.{PortManifest, SurfaceFold}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Decision, DecisionLog, Phase, Pipeline, PolicyBinder, Program, RewriteTrace, RuleScope, RunScope, SymId}

/** `RegistryTransform` — the §1(b) mechanism replacing reflective instantiation with a `Class`-keyed
  * registry (`ENGINE-LIMITS.md` P10). Every refusal kind is asserted, not sampled (CLAUDE.md §3). */
class RegistryTransformSpec extends munit.FunSuite:
  import RegistryTransform.*

  private val java =
    """package com.demo;
      |class Reflector {
      |  static <T> T newInstance(Class<T> c) { return null; }
      |  static Class<?> forName(String n) { return null; }
      |}
      |class Broken extends RuntimeException {}
      |class Other extends RuntimeException {}
      |class Widget { }
      |class Namer { static Object build(String s) { return null; } }
      |class NeedsArg { NeedsArg(int x) { } }
      |class ByString { Object make() { return Namer.build("x"); } }
      |class Facade { static Object read(Class<?> c) { throw new RuntimeException("no"); } }
      |class Maker {
      |  Object plain(Class<?> c) { return Reflector.newInstance(c); }
      |}
      |class Guarded {
      |  Object make(Class<?> c) {
      |    try { return Reflector.newInstance(c); } catch (Broken e) { return null; }
      |  }
      |}
      |class Elsewhere {
      |  Object make(Class<?> c) { return Reflector.newInstance(c); }
      |}
      |""".stripMargin

  private def parse(): Program = SpoonTir.fromSource(java, "Demo.java")

  private case class Ported(after: Program, out: String, log: DecisionLog)

  private def run(p: Phase): Ported =
    val (after, log) = Pipeline.runTraced(parse(), List(p))
    Ported(after, new TirEmitter(after, notes = log).emit, log)

  private val objectAt = Placement.Object("com.demo.ComponentFactories", Spelling("table", "register", "create"))

  private def entry(scope: RuleScope = RuleScope.Only(Set("com.demo")),
                    miss: Miss = Miss.Null,
                    handles: Set[String] = Set.empty,
                    seeds: List[String] = Nil,
                    placement: Placement = objectAt) =
    Registry("com.demo.Reflector#newInstance", placement, scope, seeds, handles, miss, Some("AnyRef"))

  private def phase(es: Registry*) = new RegistryTransform(es.toList)

  // ---- the no-op ------------------------------------------------------------------------------

  test("an empty spec is a no-op and contributes NO fingerprint segment") {
    val t = new RegistryTransform()
    assertEquals(t.surfaceFingerprint, "")
    assertEquals(t.findings, Nil)
    val before = parse()
    val after  = t.run(before)
    assertEquals(after.units.size, before.units.size)
  }

  test("a non-empty spec fingerprints its callee, placement, miss and bound") {
    val fp = phase(entry()).surfaceFingerprint
    assert(clue(fp).contains("com.demo.Reflector#newInstance"))
    assert(fp.contains("com.demo.ComponentFactories:table/register/create"))
    assert(fp.contains("Null"))
    assert(fp.contains("<:AnyRef"))
  }

  // ---- the rewrite ----------------------------------------------------------------------------

  test("a reflective call is redirected at the registry, which is minted as its own unit") {
    val r = run(phase(entry()))
    assert(clue(r.out).contains("com.demo.ComponentFactories.create(c)"))
    assert(!r.out.contains("Reflector.newInstance(c)"))
    assert(r.out.contains("object ComponentFactories"))
    assert(r.out.contains("private val table: scala.collection.mutable.HashMap[Class[?], () => ?]"))
    assert(r.out.contains("def register[T <: AnyRef](componentType: Class[T], factory: () => T): Unit"))
    assert(r.out.contains("def create[T <: AnyRef](componentType: Class[T]): T"))
  }

  test("the minted symbols are REAL symbols — the registry unit and its three members") {
    val r  = run(phase(entry()))
    val fq = r.after.symbols.all.map(_.fullName).toSet
    assert(fq.contains("com.demo.ComponentFactories"))
    assert(fq.contains("com.demo.ComponentFactories#create"))
    assert(fq.contains("com.demo.ComponentFactories#register"))
    assert(fq.contains("com.demo.ComponentFactories#table"))
  }

  test("one RedirectedCall per redirected declaration and one AddedMember per minted member") {
    val r  = run(phase(entry()))
    val ds = r.log.all
    assert(ds.exists(d => d.kind == Decision.Kind.RedirectedCall &&
      d.detail.get("from").contains("com.demo.Reflector#newInstance")))
    assertEquals(clue(ds.count(_.kind == Decision.Kind.AddedMember)), 3)
  }

  test("Placement.Member puts the registry on a type the port already emits (CT7: no ctor parameter)") {
    val r = run(phase(entry(placement =
      Placement.Member("com.demo.Maker", Spelling("factories", "reg", "mk")))))
    assert(clue(r.out).contains("protected val factories:"))
    assert(r.out.contains("def mk[T <: AnyRef](componentType: Class[T]): T"))
    assert(r.out.contains("this.mk(c)") || r.out.contains("com.demo.Maker.mk(c)") ||
           r.out.contains(".mk(c)"), clue(r.out))
    assert(!r.out.contains("object Maker {\n  protected val factories"))
  }

  test("a minted member is not an ORPHANED CALL — a text-spliced declaration has no `Definition`") {
    val r = run(phase(entry()))
    val orphans = RewriteTrace.check(r.after).filter(_.what == "call to a member with no declaration")
    assertEquals(clue(orphans.map(_.name)), Nil)
  }

  // ---- the miss arms --------------------------------------------------------------------------

  test("miss = Null answers null") {
    assert(clue(run(phase(entry(miss = Miss.Null))).out).contains("null.asInstanceOf[T]"))
  }

  test("miss = Throw raises the port's own exception, naming the key") {
    val r = run(phase(entry(miss = Miss.Throw("com.demo.Broken", "no factory for "))))
    assert(clue(r.out).contains("""throw new com.demo.Broken("no factory for " + componentType.getName())"""))
  }

  test("miss = JvmReflect falls back to the JVM, and every non-JVM target is COUNTED") {
    val p = phase(entry(miss = Miss.JvmReflect()))
    val r = run(p)
    assert(clue(r.out).contains("componentType.getConstructor().newInstance()"))
    // the default `RunScope` is every platform, so both non-JVM backends are counted.
    val rows = p.findings.filter(_.issue == RegistryCheck.Issue.JvmOnlyMiss)
    assertEquals(clue(rows).size, 2)
  }

  test("miss = JvmReflect(Throw) restates java's OWN answer where reflection itself fails") {
    val r = run(phase(entry(miss = Miss.JvmReflect(Miss.OnFailure.Throw("com.demo.Broken", "no ctor for ")))))
    assert(clue(r.out).contains("componentType.getConstructor().newInstance()"))
    assert(r.out.contains("""throw new com.demo.Broken("no ctor for " + componentType.getName())"""))
    // …and NOT the silent null the same arm answers by default.
    assert(!r.out.contains("java.lang.IllegalAccessException => null"), clue(r.out))
  }

  test("the default JvmReflect failure is `null`, and renders as the string it always did") {
    assertEquals(Miss.render(Miss.JvmReflect()), "JvmReflect")
    assertEquals(Miss.render(Miss.Null), "Null")
    assert(clue(phase(entry(miss = Miss.JvmReflect())).surfaceFingerprint).contains("/JvmReflect<:"))
    assert(Miss.render(Miss.JvmReflect(Miss.OnFailure.Throw("com.demo.Broken", "x")))
      .contains("Throw(com.demo.Broken,x)"))
    assert(clue(run(phase(entry(miss = Miss.JvmReflect()))).out).contains("null.asInstanceOf[T]"))
  }

  test("`handles` elides the try over a JvmReflect(Throw) arm, which now throws what java caught") {
    val p = phase(entry(handles = Set("com.demo.Broken"),
      miss = Miss.JvmReflect(Miss.OnFailure.Throw("com.demo.Broken", "no ctor for "))))
    val r = run(p)
    assert(!clue(r.out).contains("catch { case e"), clue(r.out))
    assertEquals(p.findings.count(_.issue == RegistryCheck.Issue.GuardedCall), 0)
  }

  // ---- composed with the NAME table -------------------------------------------------------------

  test("`newInstance(forName(s))` composes: the name table keys the registry (P10)") {
    val js = java.replace("Object plain(Class<?> c) { return Reflector.newInstance(c); }",
      "Object plain(Class<?> c) { return Reflector.newInstance(Reflector.forName(\"x\")); }")
    val reg   = phase(entry())
    val names = new ClassTableTransform(Map("com.demo.Reflector#forName" -> "com.demo.Names#classFor"))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(js, "Demo.java"), List(names, reg))
    val out = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains("com.demo.ComponentFactories.create(com.demo.Names.classFor("), clue(out))
    // …and the by-name REFUSAL is gone, because the argument is no longer a run-time name lookup.
    assertEquals(clue(reg.findings.count(_.issue == RegistryCheck.Issue.ByName)), 0)
  }

  // ---- seeds ----------------------------------------------------------------------------------

  test("a seed is registered at init, renamed with the type it names") {
    val p = phase(entry(seeds = List("com.demo.Widget")))
    val r = run(p)
    assert(clue(r.out).contains("locally { register(classOf[com.demo.Widget], () => new com.demo.Widget"))
    assertEquals(p.policyReport.findings.filter(_.key == "com.demo.Widget"), Nil)
  }

  test("a seed with no visible nilary constructor is reported, never silently dropped") {
    val p = phase(entry(seeds = List("com.demo.NeedsArg")))
    run(p)
    val f = p.policyReport.findings.filter(_.key == "com.demo.NeedsArg")
    assertEquals(clue(f).size, 1)
    assert(f.head.detail.contains("no visible no-argument constructor"), clue(f.head.detail))
    // …and a seed naming NOTHING is the binder's own never-matched row, a different instruction.
    val bad = phase(entry(seeds = List("com.demo.NotHere")))
    run(bad)
    assert(clue(bad.policyReport.findings.map(_.key)).contains("com.demo.NotHere"))
  }

  // ---- the refusals, each one counted ---------------------------------------------------------

  test("a call OUTSIDE the entry's scope is refused and counted") {
    val p = phase(entry(scope = RuleScope.Only(Set("com.demo.Maker"))))
    val r = run(p)
    val rows = p.findings.filter(_.issue == RegistryCheck.Issue.OutOfScope)
    assert(clue(rows).nonEmpty)
    // …and the java call SURVIVES where it was refused, loudly (§3).
    assert(clue(r.out).contains("Reflector.newInstance(c)"))
  }

  test("an argument that is not a Class value is refused and counted") {
    val p = new RegistryTransform(List(
      Registry("com.demo.Namer#build", objectAt, RuleScope.Only(Set("com.demo")))))
    val (after, log) = Pipeline.runTraced(parse(), List(p))
    assertEquals(clue(p.findings.count(_.issue == RegistryCheck.Issue.NonClassArg)), 1)
    // …and the java call SURVIVES where it was refused, loudly (§3).
    assert(new TirEmitter(after, notes = log).emit.contains("Namer.build(\"x\")"))
  }

  test("a class named by a STRING at run time is refused and counted") {
    val js = java.replace("Object plain(Class<?> c) { return Reflector.newInstance(c); }",
      "Object plain(Class<?> c) { return Reflector.newInstance(Reflector.forName(\"x\")); }")
    val p  = phase(entry())
    val (_, _) = Pipeline.runTraced(SpoonTir.fromSource(js, "Demo.java"), List(p))
    assert(clue(p.findings.filter(_.issue == RegistryCheck.Issue.ByName)).nonEmpty)
  }

  test("a reflective SELF-CLONE is refused with a non-reflective miss, and admitted with JvmReflect") {
    val js = java.replace("Object plain(Class<?> c) { return Reflector.newInstance(c); }",
      "Object plain(Class<?> c) { return Reflector.newInstance(getClass()); }")
    def fire(m: Miss) =
      val p = phase(entry(miss = m))
      Pipeline.runTraced(SpoonTir.fromSource(js, "Demo.java"), List(p))
      p.findings.count(_.issue == RegistryCheck.Issue.SelfClone)
    assert(clue(fire(Miss.Null)) > 0)
    assertEquals(clue(fire(Miss.JvmReflect())), 0)
  }

  test("a facade member is COUNTED at every call and never rewritten") {
    val js = java.replace("Object plain(Class<?> c) { return Reflector.newInstance(c); }",
      "Object plain(Class<?> c) { return Facade.read(c); }")
    val p  = new RegistryTransform(List(entry()), Set("com.demo.Facade#read"))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(js, "Demo.java"), List(p))
    assertEquals(clue(p.findings.count(_.issue == RegistryCheck.Issue.Facade)), 1)
    assert(new TirEmitter(after, notes = log).emit.contains("Facade.read(c)"))
  }

  // ---- the handler the rewrite made dead ------------------------------------------------------

  test("`handles` elides the try whose thrower this rewrite retired") {
    val p = phase(entry(handles = Set("com.demo.Broken")))
    val r = run(p)
    assert(!clue(r.out).contains("catch"), clue(r.out))
    assertEquals(p.findings.count(_.issue == RegistryCheck.Issue.GuardedCall), 0)
  }

  test("a try `handles` does not describe is LEFT as java wrote it, and counted") {
    val p = phase(entry(handles = Set("com.demo.Other")))
    val r = run(p)
    assert(clue(r.out).contains("com.demo.Broken"))
    assertEquals(clue(p.findings.count(_.issue == RegistryCheck.Issue.GuardedCall)), 1)
  }

  test("no `handles` at all leaves every try alone and counts it") {
    val p = phase(entry())
    run(p)
    assertEquals(clue(p.findings.count(_.issue == RegistryCheck.Issue.GuardedCall)), 1)
  }

  test("a module that does not EMIT the call site rewrites it, mints nothing and reports nothing") {
    val before = parse()
    val emitsNothing = new RunScope:
      def emits(unit: SymId): Boolean                     = false
      def contributed(phase: String): Option[Set[String]] = scala.None
    val p = phase(entry(miss = Miss.JvmReflect()))
    val (after, log) = Pipeline.runTraced(before, List(p),
      new PolicyBinder(before, before.members, emitsNothing))
    val out = new TirEmitter(after, notes = log).emit
    // the DEPENDENT's model of a base unit still moves — a model in which the base calls the
    // retired member reports the base's dropped type as this module's residue (D2)…
    assert(clue(out).contains("com.demo.ComponentFactories.create(c)"))
    // …but the unit belongs to the module that EMITS the site (O5), and so do the findings.
    assert(!out.contains("object ComponentFactories"))
    assertEquals(clue(p.findings), Nil)
    assertEquals(log.all.count(_.kind == Decision.Kind.AddedMember), 0)
  }

  // ---- merge ------------------------------------------------------------------------------------

  test("independent callees from base and dependent merge into one instance") {
    val b = PortManifest("base", governs = Set("com.demo"), surface = List(phase(entry())))
    val d = b.extendedBy(PortManifest("dep", surface = List(new RegistryTransform(List(
      Registry("com.demo.Namer#build",
        Placement.Object("com.demo.Names", Spelling("t", "r", "c")),
        RuleScope.Only(Set("com.demo"))))))))
    assertEquals(d.surfaceFold.refusals, Nil)
    val eff = d.effectiveSurface.collect { case r: RegistryTransform => r }
    assertEquals(clue(eff).size, 1)
    assertEquals(eff.head.entries.size, 2)
  }

  test("the same placement slot with a different entry REFUSES") {
    val b = PortManifest("base", governs = Set("com.demo"), surface = List(phase(entry())))
    val d = b.extendedBy(PortManifest("dep", surface = List(new RegistryTransform(List(
      Registry("com.demo.Namer#build", objectAt, RuleScope.Only(Set("com.demo"))))))))
    assert(clue(d.surfaceFold.refusals).nonEmpty)
    assertEquals(d.surfaceFold.refusals.head.cause, SurfaceFold.Cause.Conflict)
  }

  test("an unbound callee is reported, never a silent no-op") {
    val p = phase(Registry("com.demo.NoSuch#gone", objectAt, RuleScope.Only(Set("com.demo"))))
    Pipeline.runTraced(parse(), List(p))
    assert(clue(p.policyReport.findings.map(_.key)).contains("com.demo.NoSuch#gone"))
  }

  // ---- the lanes --------------------------------------------------------------------------------

  test("every Issue has its own lane, and the lane names are stable") {
    assertEquals(RegistryCheck.AllLanes.size, RegistryCheck.Issue.values.length)
    assert(RegistryCheck.AllLanes.contains("registry(non-class-arg)"))
    assert(RegistryCheck.AllLanes.contains("registry(jvm-only-miss)"))
    RegistryCheck.Issue.values.foreach(i =>
      assert(RegistryCheck.Issue.classification(i).nonEmpty, clue(i)))
  }
