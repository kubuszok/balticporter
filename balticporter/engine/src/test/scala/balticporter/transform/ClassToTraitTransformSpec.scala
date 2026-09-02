package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** `class-to-trait` -- rewrite a nominated abstract class into a trait and transform every
  * subclass (named and anonymous) to use `override val` members instead of constructor arguments.
  *
  * CLAUDE.md section 1(b). The mechanism belongs in the engine, the policy (which class, which
  * params) belongs in the port. Empty specs = no-op.
  *
  * Three subclass shapes are exercised:
  *  - a NAMED subclass calling `super(args)` -- the widest constructor's args are read
  *  - an ANONYMOUS `new Pool(args) { ... }` -- args from the Apply node
  *  - a NILARY anonymous `new Pool() { ... }` -- args from the nilary constructor's defaults
  *  - a PARTIAL-ARGS case: `new Pool(a) { ... }` where the widest constructor takes 2 params --
  *    the first arg is the actual, the second falls back to the nilary constructor's default
  */
class ClassToTraitTransformSpec extends munit.FunSuite:

  // ---- fixtures ------------------------------------------------------------------------------

  /** An abstract class with three constructors delegating to the widest, and several subclass
    * shapes: named, anonymous with args, anonymous nilary, anonymous with partial args. */
  private val poolLike =
    """package com.demo;
      |
      |abstract class Pool<T> {
      |  protected int initialCapacity;
      |  protected int max;
      |
      |  public Pool() { this(16, Integer.MAX_VALUE); }
      |  public Pool(int initialCapacity) { this(initialCapacity, Integer.MAX_VALUE); }
      |  public Pool(int initialCapacity, int max) {
      |    this.initialCapacity = initialCapacity;
      |    this.max = max;
      |  }
      |
      |  abstract protected T newObject();
      |  public T obtain() { return newObject(); }
      |}
      |
      |class ConcretePool extends Pool<String> {
      |  public ConcretePool(int cap, int mx) { super(cap, mx); }
      |  protected String newObject() { return ""; }
      |}
      |
      |class DefaultPool extends Pool<String> {
      |  public DefaultPool() { super(); }
      |  protected String newObject() { return "default"; }
      |}
      |
      |class PartialPool extends Pool<String> {
      |  public PartialPool(int cap) { super(cap); }
      |  protected String newObject() { return "partial"; }
      |}
      |
      |class Client {
      |  Pool<String> fullArgs = new Pool<String>(10, 20) {
      |    protected String newObject() { return "full"; }
      |  };
      |  Pool<String> nilaryAnon = new Pool<String>() {
      |    protected String newObject() { return "nil"; }
      |  };
      |  Pool<String> partialAnon = new Pool<String>(42) {
      |    protected String newObject() { return "partial-anon"; }
      |  };
      |}
      |""".stripMargin

  private val mappings = List(
    ClassToTraitTransform.ParamMapping(0, "initialCapacity"),
    ClassToTraitTransform.ParamMapping(1, "max"),
  )

  private def parse(java: String): Program = SpoonTir.fromSource(java, "Demo.java")

  private case class Ported(before: Program, after: Program, out: String, log: DecisionLog)

  private def run(java: String, p: Phase): Ported =
    val before       = parse(java)
    val (after, log) = Pipeline.runTraced(before, List(p))
    Ported(before, after, new TirEmitter(after, notes = log).emit, log)

  private def sym(p: Program, fqn: String): SymId =
    p.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse(fail(s"no symbol named $fqn"))

  // ---- 1. the no-op --------------------------------------------------------------------------

  test("empty specs is a structural no-op") {
    val ph     = new ClassToTraitTransform()
    val before = parse(poolLike)
    val (after, _) = Pipeline.runTraced(before, List(ph))
    assertEquals(after.units.map(_.symbol), before.units.map(_.symbol))
    assertEquals(ph.surfaceFingerprint, "")
  }

  // ---- 2. named subclass with full args ------------------------------------------------------

  test("named subclass with full super args gets override vals") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val r = run(poolLike, ph)
    assert(r.out.contains("override val initialCapacity"), "should have override val initialCapacity")
    assert(r.out.contains("override val max"), "should have override val max")
    // ConcretePool calls super(cap, mx) -- both mapped
    val concrete = r.out.linesIterator.dropWhile(!_.contains("class ConcretePool")).takeWhile(!_.contains("class Default")).mkString("\n")
    assert(concrete.contains("override val initialCapacity"), s"ConcretePool should have initialCapacity: $concrete")
    assert(concrete.contains("override val max"), s"ConcretePool should have max: $concrete")
  }

  // ---- 3. named subclass nilary (defaults) ---------------------------------------------------

  test("named subclass with nilary super gets defaults") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val r = run(poolLike, ph)
    val defaultPool = r.out.linesIterator.dropWhile(!_.contains("class DefaultPool")).takeWhile(!_.contains("class Partial")).mkString("\n")
    assert(defaultPool.contains("override val initialCapacity"), s"DefaultPool should have initialCapacity: $defaultPool")
    assert(defaultPool.contains("override val max"), s"DefaultPool should have max: $defaultPool")
  }

  // ---- 4. named subclass with partial args ---------------------------------------------------

  test("named subclass with partial super args falls back to defaults for the rest") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val r = run(poolLike, ph)
    val partialPool = r.out.linesIterator.dropWhile(!_.contains("class PartialPool")).takeWhile(!_.contains("class Client")).mkString("\n")
    assert(partialPool.contains("override val initialCapacity"), s"PartialPool should have initialCapacity: $partialPool")
    assert(partialPool.contains("override val max"), s"PartialPool should have max (from default): $partialPool")
  }

  // ---- 5. anonymous subclass with args -------------------------------------------------------

  test("anonymous new Pool(10, 20) { ... } gets override vals from actual args") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val r = run(poolLike, ph)
    // The full-args anonymous class should have both override vals
    val fullArgs = r.out.linesIterator.dropWhile(!_.contains("fullArgs")).take(10).mkString("\n")
    assert(fullArgs.contains("override val initialCapacity"), s"fullArgs anon should have initialCapacity: $fullArgs")
    assert(fullArgs.contains("override val max"), s"fullArgs anon should have max: $fullArgs")
  }

  // ---- 6. anonymous nilary subclass ----------------------------------------------------------

  test("anonymous new Pool() { ... } gets override vals from defaults") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val r = run(poolLike, ph)
    val nilaryAnon = r.out.linesIterator.dropWhile(!_.contains("nilaryAnon")).take(10).mkString("\n")
    assert(nilaryAnon.contains("override val initialCapacity"), s"nilaryAnon should have initialCapacity: $nilaryAnon")
    assert(nilaryAnon.contains("override val max"), s"nilaryAnon should have max: $nilaryAnon")
  }

  // ---- 7. anonymous partial-args subclass ----------------------------------------------------

  test("anonymous new Pool(42) { ... } gets first from arg, second from default") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val r = run(poolLike, ph)
    val partialAnon = r.out.linesIterator.dropWhile(!_.contains("partialAnon")).take(10).mkString("\n")
    assert(partialAnon.contains("override val initialCapacity"), s"partialAnon should have initialCapacity: $partialAnon")
    assert(partialAnon.contains("override val max"), s"partialAnon should have max (from default): $partialAnon")
  }

  // ---- 8. SurfacePolicy + MergeablePolicy ----------------------------------------------------

  test("fingerprint is stable and includes all specs") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    assert(ph.surfaceFingerprint.contains("com.demo.Pool"), "fingerprint should name the type")
    assert(ph.surfaceFingerprint.contains("initialCapacity"), "fingerprint should name the val")
  }

  test("merge of same specs succeeds") {
    val a = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val b = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    assert(a.mergedWith(b).isRight, "identical specs should merge")
  }

  test("merge of different specs for same type refuses") {
    val a = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val different = List(ClassToTraitTransform.ParamMapping(0, "cap"))
    val b = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> different))
    assert(a.mergedWith(b).isLeft, "different mappings for same type should refuse")
  }

  test("merge of disjoint specs unions") {
    val a = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val other = List(ClassToTraitTransform.ParamMapping(0, "size"))
    val b = new ClassToTraitTransform(specs = Map("com.other.Queue" -> other))
    val result = a.mergedWith(b)
    assert(result.isRight, "disjoint specs should merge")
    result.foreach { merged =>
      val ct = merged.phase.asInstanceOf[ClassToTraitTransform]
      assertEquals(ct.specs.size, 2, "merged should have both entries")
    }
  }

  // ---- 9. decision recording -----------------------------------------------------------------

  test("decisions are recorded for the nominated type and each subclass") {
    val ph = new ClassToTraitTransform(specs = Map("com.demo.Pool" -> mappings))
    val r = run(poolLike, ph)
    val decisions = r.log.all
    assert(decisions.nonEmpty, "should have recorded decisions")
    // The nominated type itself should have a decision
    val poolDecisions = decisions.filter(_.subjectFqn.contains("Pool"))
    assert(poolDecisions.nonEmpty, "should have a decision for Pool")
  }
