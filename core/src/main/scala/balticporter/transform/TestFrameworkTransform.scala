package balticporter.transform

import balticporter.tir.*

/** A JUnit suite → a CROSS-PLATFORM Scala suite.
  *
  * A ported test suite is the only behavioural evidence this engine can produce, and a JUnit one
  * runs on the JVM alone — neither Scala.js nor Scala Native has JUnit. Emitting Java's tests as
  * JUnit-in-Scala therefore yields a gate that cannot execute on the platforms the port EXISTS for,
  * while looking like full coverage: 221 discovered tests, zero of them runnable on the target.
  *
  * ==The assertion façade is a SCAFFOLD, not part of the design==
  *
  * `Assert.assertEquals(expected, actual)` is rewritten to a same-shaped member of an injected
  * `object` ([[TestFrameworkTransform.runtimeSources]]) rather than to MUnit's
  * `assertEquals(obtained, expected)`, because the direct mapping measured 1 -> 33 errors: MUnit's
  * `assertEquals` is type-constrained (`B <:< A`) and java's `assertEquals(Object, Object)` is not.
  *
  * That is NOT a justification, and the file must not read as though it were. The 33 broke down as
  * 26 mixed-numeric comparisons the transform can widen from static types it already has, 6 java
  * `static` helpers wrongly emitted into the companion object where the suite's instance members
  * are invisible, and 1 unrelated. Every MUnit shape was probed and compiles. So the façade is
  * SHAPE ADAPTATION, which this project forbids shipping, and deleting it needs exactly two
  * type-directed changes here: widen the narrower operand of a mixed-numeric comparison, and stop
  * emitting a test class's java statics into the companion. Nothing then ships with the port.
  * Nothing below should add members to it.
  *
  * ==The honest §1 label: (b) with EXACTLY ONE implemented policy value==
  *
  * `suite` and `testMember` are constructor parameters, and that used to be described as making
  * the target framework configurable. It does not. The trees this phase builds encode a fixed
  * CONTRACT that only MUnit satisfies:
  *
  *   1. registration is CURRIED — `test(name)(body)`, two argument lists;
  *   2. the name slot accepts a `munit.TestOptions`, and `.ignore` on one disables the test;
  *   3. `intercept[E] { … }` is inherited from the suite and asserts that the body throws;
  *   4. one-time setup/teardown are `override def beforeAll()` / `afterAll()` on the suite;
  *   5. `munit.TestOptions` is nameable by FQN with no import (CLAUDE.md §6).
  *
  * 2–5 name MUnit types and members literally, in this file. 1 is a tree SHAPE, not a string — and
  * note what it is NOT: the TIR expresses currying perfectly well as a nested `Apply`, because
  * `Apply.fun` is a `Term`. An earlier version believed otherwise and injected a `PortedSuite` base
  * class with an un-curried `testCase(name, body)` forwarder; that class has been DELETED and the
  * belief retracted. There is no missing IR node here. What is missing is a seam.
  *
  * So: pointing `suite` at `utest.TestSuite` emits code that does not compile. A second target
  * needs 1–5 to become tree-BUILDING policy — a `TestTarget` supplying the registration, ignore,
  * intercept and lifecycle SHAPES — not more `String` parameters, and it needs its own answer for
  * the assertions. Until one exists this is a (b) whose policy set has one member, and the
  * parameters name only the two least interesting parts of it.
  *
  * ==What is NOT translated — reported, never dropped silently==
  *
  * `@Rule`, `@RunWith`, JUnit 5, TestNG, JUnit 3's `TestCase` and Hamcrest's `assertThat` have no
  * translation here. Each is emitted as a [[TestFrameworkTransform.Finding]] carrying its
  * CLAUDE.md §1 classification, and `run` prints them, because the failure mode of all of them is
  * a suite that compiles, discovers nothing (or asserts nothing) and reports success.
  *
  * @param suite      fully-qualified base class the ported suites extend
  * @param testMember curried `(name)(body)` registration member on that base class
  */
final class TestFrameworkTransform(
    suite: String = TestFrameworkTransform.DefaultSuite,
    testMember: String = "test",
) extends Phase:

  import TestFrameworkTransform.{Finding, Fix}

  def name: String = "junit->portable-suite"

  private val AssertClass = "org.junit.Assert"
  private val AssertsObject = "balticporter.runtime.Asserts"
  private val AssertMembers = Set("assertEquals", "assertNotEquals", "assertTrue", "assertFalse",
    "assertNull", "assertNotNull", "assertSame", "assertArrayEquals", "fail")
  private val TestAnn        = "org.junit.Test"
  private val BeforeAnn      = "org.junit.Before"
  private val AfterAnn       = "org.junit.After"
  private val IgnoreAnn      = "org.junit.Ignore"
  private val BeforeClassAnn = "org.junit.BeforeClass"
  private val AfterClassAnn  = "org.junit.AfterClass"

  /** Annotations whose meaning this phase MOVES into emitted call sites. Once moved, the
    * annotation itself must not survive into the output: `@org.junit.Before` on a retained
    * `setUp` re-imports a JVM-only library into the very suite this phase exists to make
    * cross-platform, and reads as though JUnit still drives it. `@Test` is deliberately NOT in
    * this set — a `@Test` left on an abstract method is the residue the discovery count measures. */
  private val ConsumedAnns = Set(BeforeAnn, AfterAnn, BeforeClassAnn, AfterClassAnn, IgnoreAnn)

  /** every JUnit-4 annotation this phase understands; anything else under a test-framework
    * package is reported rather than assumed harmless. */
  private val HandledAnns = ConsumedAnns + TestAnn

  private var suiteSym: SymId  = SymId.None
  private var testSym: SymId   = SymId.None
  private var interceptSym: SymId = SymId.None
  private var testOptionsSym: SymId = SymId.None
  private var ignoreSym: SymId = SymId.None
  private var unitSym: SymId   = SymId.None
  /** `org.junit.Assert.assertX` → the façade's own `assertX`, by simple name. */
  private var assertSyms: Map[String, SymId] = Map.empty

  // symbol minting has to continue DURING the walk (each converted suite needs its own
  // `beforeAll`/`afterAll` symbol), so the counter and the buffer are fields, not `run` locals.
  private var nextId: Int = 0
  private val added = collection.mutable.ListBuffer[Symbol]()
  /** declarations whose consumed annotation must be stripped from the emitted output. */
  private val consumed = collection.mutable.Set.empty[SymId]
  private val found = collection.mutable.ListBuffer[Finding]()
  private var suitesConverted = 0
  private var testsConverted  = 0

  /** Constructs this phase could not translate, with their CLAUDE.md §1 classification. Empty
    * until [[run]] has executed. A migrator that wants the number on every run can read it; `run`
    * already prints a one-line summary plus the details, so no wiring is required for it to be
    * LOUD. */
  def findings: List[Finding] = found.toList

  override def run(program: Program): Program =
    nextId = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    added.clear(); consumed.clear(); found.clear()
    suitesConverted = 0; testsConverted = 0
    suiteSym = mint(suite.substring(suite.lastIndexOf('.') + 1), suite)
    testSym  = mint(testMember, testMember)  // MUnit's own `test`, applied CURRIED
    interceptSym = mint("intercept", "intercept") // MUnit's own, inherited from the suite
    // `munit.TestOptions("n").ignore` rather than `"n".ignore`: the latter needs MUnit's implicit
    // String conversion, and this phase emits fully-qualified references with no imports (§6).
    testOptionsSym = mint("TestOptions", "munit.TestOptions")
    ignoreSym      = mint("ignore", "ignore")
    // keyed by the member's SIMPLE name: a static call renders as `<receiver FQN>.<name>`, so the
    // member symbol itself is not keyed by the owner's FQN and cannot be found that way.
    // fully-qualified to an OBJECT, not inherited from the base class. A java `static` helper
    // emits into the COMPANION object, which does not extend the suite, so inherited assertions are
    // invisible there (`Not found: assertTrue`). An object member resolves the same from both.
    assertSyms = AssertMembers.map(nm => nm -> mint(nm, AssertsObject + "." + nm)).toMap
    unitSym = program.symbols.all.find(_.fullName == "scala.Unit").map(_.id)
      .getOrElse(mint("Unit", "scala.Unit"))

    val symbols0 = SymbolTable(program.symbols.all ++ added)
    given Program = new Program(program.units, symbols0, program.xref)
    survey(program)
    val units = program.units.map(convert)
    // `convert` mints more (the lifecycle overrides), so the table is rebuilt AFTER the walk.
    val symbols = consumed.foldLeft(SymbolTable(program.symbols.all ++ added)) { (t, id) =>
      t.get(id) match
        case scala.None => t
        case Some(s)    => t.updated(s.copy(
          annotations        = s.annotations.filterNot(a => ConsumedAnns(nameOf(a.tpe))),
          // a consumed annotation the FRONTEND could not carry (`@Ignore("why")` on a class, whose
          // arguments need an expression translator) was handled all the same, so it must stop
          // being reported as an omission.
          droppedAnnotations = s.droppedAnnotations.filterNot(ConsumedAnns)))
    }
    report()
    new Program(units, symbols, program.xref)

  private def mint(nm: String, full: String, flags: Flags = Flags(), info: TypeRepr = TypeRepr.NoType): SymId =
    val id = SymId(nextId); nextId += 1
    added += Symbol(id, nm, full, flags, SymId.None, info)
    id

  private def report(): Unit =
    println(s"[$name] converted $suitesConverted suite(s), $testsConverted test(s); " +
            s"UNTRANSLATED test-framework constructs: ${found.size}")
    if found.nonEmpty then
      // grouped, because one unhandled annotation is typically on every method of a suite.
      found.groupBy(_.construct).toList.sortBy(-_._2.size).foreach { (c, fs) =>
        println(s"  $c × ${fs.size} — (${fs.head.fix.label}) ${fs.head.advice}")
        fs.take(3).foreach(f => println(s"      ${f.where.javaPath}:${f.where.line}"))
      }

  // -------------------------------------------------------------------------
  // Survey — what this phase does NOT translate
  // -------------------------------------------------------------------------

  /** Every test-framework construct the phase leaves alone, recorded with its §1 classification.
    *
    * All of these are (a): a fact about JUnit/TestNG and Scala, identical for every library, so
    * none of them is fixed by configuring a phase or by a library-specific rule. Saying so is the
    * point — an error an agent cannot classify costs it a full investigation (CLAUDE.md §4.45).
    *
    * The failure mode is uniform and quiet: an unrecognised annotation means the class is not
    * converted at all, so it registers ZERO tests, compiles, and reports success. */
  private def survey(program: Program)(using p: Program): Unit =
    val roots = List("org.junit.", "org.junit.jupiter.", "org.testng.")
    program.symbols.all.foreach { s =>
      val names = s.annotations.map(a => nameOf(a.tpe) -> a.origin) ++
                  s.droppedAnnotations.map(_ -> s.origin)
      names.foreach { (fqn, o) =>
        if roots.exists(fqn.startsWith) && !HandledAnns(fqn) then
          val (fix, advice) = adviceFor(fqn)
          found += Finding(fqn, o, fix, advice)
      }
    }
    // JUnit 3 has no annotations at all: a suite is a `junit.framework.TestCase` subclass whose
    // test methods are named `testXxx`. Nothing above can see it, so the PARENT is the signal.
    def scanParents(cd: Tree.ClassDef): Unit =
      cd.parents.foreach {
        case tt: TypeTree if nameOf(tt.tpe) == "junit.framework.TestCase" =>
          found += Finding("junit.framework.TestCase", cd.origin, Fix.EngineRule,
            "a JUnit 3 suite declares its tests by NAMING them `testXxx` on a `TestCase` subclass; " +
            "this phase keys off `@Test` and converts nothing, so the class emits as a plain class " +
            "and registers zero tests.")
        case _ => ()
      }
      cd.body.foreach { case c: Tree.ClassDef => scanParents(c); case _ => () }
    program.units.foreach(scanParents)
    // Hamcrest: a whole second assertion vocabulary, reached either through the deprecated
    // `org.junit.Assert.assertThat` or through `org.hamcrest.MatcherAssert`.
    program.referenced.foreach { id =>
      program.symbolOf(id).foreach { s =>
        val isHamcrest = s.fullName.startsWith("org.hamcrest")
        val isAssertThat = s.name == "assertThat"
        if isHamcrest || isAssertThat then
          val what = if isAssertThat then "assertThat" else s.fullName
          program.usages(id).foreach(u => found += Finding(what, u.site.origin, Fix.EngineRule,
            "Hamcrest is a second assertion vocabulary (`assertThat(x, is(equalTo(y)))`); the " +
            "injected façade declares JUnit's `Assert` members only, and nothing translates a " +
            "matcher. OUT OF SCOPE by decision, reported so it is not mistaken for coverage: " +
            "either keep this suite on the JVM/JUnit path with hamcrest on the test classpath, or " +
            "add `assertThat` plus matcher shims to the façade."))
      }
    }

  private def adviceFor(fqn: String): (Fix, String) = fqn match
    case "org.junit.Rule" | "org.junit.ClassRule" => (Fix.EngineRule,
      "a JUnit @Rule wraps every test in an arbitrary Statement (TemporaryFolder, ExpectedException, " +
      "Timeout, …); there is no shape to derive it from. Replace it with an explicit fixture in the " +
      "port's hand-written `src/`, or keep this suite on the JVM/JUnit path. The rule field is " +
      "emitted as an ordinary field and NEVER APPLIED.")
    case "org.junit.runner.RunWith" => (Fix.EngineRule,
      "a custom runner (Parameterized, Suite, Enclosed) changes how tests are ENUMERATED, so the " +
      "converted suite runs a different SET of tests from java's — it converts as though the runner " +
      "were absent. No translation exists.")
    case f if f.startsWith("org.junit.jupiter.") => (Fix.EngineRule,
      "JUnit 5 annotations are not recognised, so this class converts to ZERO tests while compiling " +
      "and reporting success. The shapes match JUnit 4's one for one (@Test, @BeforeEach, @AfterEach, " +
      "@Disabled, @BeforeAll, @AfterAll) — extending this phase's annotation names is the fix.")
    case f if f.startsWith("org.testng.") => (Fix.EngineRule,
      "TestNG annotations are not recognised, so this class converts to ZERO tests while compiling " +
      "and reporting success. Note TestNG is ALSO invisible to `PortabilityCheck`, whose only " +
      "test-framework rules are `org.junit.` and `junit.framework.` — nothing else reports it.")
    case _ => (Fix.EngineRule,
      "an unrecognised test-framework annotation: it is carried into the output verbatim (which " +
      "needs the framework on the classpath) and whatever it configured does not happen.")

  /** `org.junit.Assert.assertEquals(a, b)` → `assertEquals(a, b)`, resolving to the façade member
    * inherited from the base suite. The arguments do not move — that is the whole point of
    * re-declaring java's shapes there rather than rewriting 872 call sites into MUnit's own
    * `(obtained, expected)` order. Same mechanism as [[StaticForwarderTransform]]: a wrapper's
    * statics become plain members. */
  override def transformApply(t: Tree.Apply)(using p: Program): Term = t.fun match
    case Tree.Select(recv, m, _, o) if recvIs(recv, AssertClass) =>
      val nm = p.symbolOf(m).map(_.name).getOrElse("")
      assertSyms.get(nm).map(id => t.copy(fun = Tree.Ident(id, TypeRepr.NoType, o), method = id)).getOrElse(t)
    case _ => t

  private def recvIs(recv: Term, fqn: String)(using p: Program): Boolean = recv match
    case Tree.Ident(s, _, _)     => p.symbolOf(s).exists(_.fullName == fqn)
    case Tree.Select(_, s, _, _) => p.symbolOf(s).exists(_.fullName == fqn)
    case _                       => false

  /** A class is a SUITE when it declares at least one `@Test` member. Nested classes are converted
    * too — libGDX nests helper suites — so the walk is explicit rather than top-level only. */
  private def convert(cd: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    val nested = cd.body.map {
      case c: Tree.ClassDef => convert(c)
      case other            => other
    }
    val cd2 = cd.copy(body = nested)
    if !nested.exists(isAnnotated(_, TestAnn)) then cd2
    else
      // Rewrite `Assert.assertX` to the façade member ONLY inside a class that becomes a suite —
      // it is the base class that supplies those members. Rewriting program-wide (via a traversal
      // in `run`) un-qualified the calls in helper classes that never gained the base class, and
      // they failed with `Not found: assertTrue`.
      val mapped = StandardTraversal.mapClassDef(this, cd2)
      // JUnit runs `@Before` before EVERY test, on a FRESH instance of the class. MUnit has
      // neither: one suite instance, and no such annotation — so the emitted `@Before def setUp`
      // was never called and `SortTest`'s `sortInstance` was null in all 19 of its tests. Nothing
      // failed to compile; the suite failed at run time, which is the only place this shows.
      //
      // Call it at the head of each test body. That reproduces java's per-test setup exactly
      // wherever setup ASSIGNS the fields it needs, which is the shape `@Before` exists for. It
      // does NOT reproduce JUnit's fresh instance, so a field carrying state through its own
      // INITIALISER rather than through setup still leaks between tests — recorded, not hidden.
      val setups = mapped.body.collect {
        case d: Tree.DefDef if isAnnotated(d, BeforeAnn) => d.symbol
      }
      // `@After` is the SAME defect on the release side, and it is the one that hides best: the
      // teardown simply never runs, every test still passes, and state leaks into the next one.
      //
      // JUnit runs it after every test AND WHETHER OR NOT THE TEST THREW — `RunAfters` wraps
      // `RunBefores`, which wraps the expected-exception check, which wraps the invocation. So a
      // trailing call appended to the body is wrong exactly where teardown matters most: a failing
      // test would skip it and poison every later test in the suite. `try … finally` is the only
      // shape with java's semantics, and it nests in java's own order —
      // `try { setUp(); intercept[E]{ body } } finally { tearDown() }`.
      val teardowns = mapped.body.collect {
        case d: Tree.DefDef if isAnnotated(d, AfterAnn) => d.symbol
      }
      // `@BeforeClass` / `@AfterClass` are JUnit's ONE-TIME hooks; MUnit spells them `beforeAll` /
      // `afterAll` on the suite. They are java `static`, so they emit into the companion object and
      // the override calls them through it (`Suite.setUpClass()`).
      val classSetups = mapped.body.collect {
        case d: Tree.DefDef if isAnnotated(d, BeforeClassAnn) => d.symbol
      }
      val classTeardowns = mapped.body.collect {
        case d: Tree.DefDef if isAnnotated(d, AfterClassAnn) => d.symbol
      }
      consumed ++= setups ++ teardowns ++ classSetups ++ classTeardowns
      // `@Ignore` on the CLASS disables every test it declares.
      val allIgnored = hasAnn(cd.symbol, IgnoreAnn)
      if allIgnored then consumed += cd.symbol
      val body = mapped.body.flatMap {
        case d: Tree.DefDef if isAnnotated(d, TestAnn) => List(testCase(d, setups, teardowns, allIgnored))
        case other                                     => List(other)
      }
      suitesConverted += 1
      cd2.copy(parents = TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, suiteSym), cd.origin) :: cd2.parents,
               body = body ++ lifecycle(TestFrameworkTransform.BeforeAllMember, classSetups, cd.origin)
                           ++ lifecycle(TestFrameworkTransform.AfterAllMember, classTeardowns, cd.origin))

  /** `@BeforeClass static void x()` → `override def beforeAll(): Unit = { Suite.x() }`.
    *
    * Empty input ⇒ no member, so a suite without the annotation is untouched. */
  private def lifecycle(member: String, targets: List[SymId], o: Origin): List[Statement] =
    if targets.isEmpty then Nil
    else
      val unit = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)
      val sym  = mint(member, member, Flags(isOverride = true), TypeRepr.MethodType(Nil, unit))
      List(Tree.DefDef(sym, List(Nil), TypeTree(unit, o), Some(seq(targets.map(call(_, o)), unit, o)), o))

  /** the calls as one term — a `Block` when there is more than one, since `Tree` has no
    * statement-sequence node and a bare `List` would have to be flattened by the emitter. */
  private def seq(calls: List[Term], tpe: TypeRepr, o: Origin): Term = calls match
    case one :: Nil => one
    case many       => Tree.Block(many.init, many.last, tpe, o)

  private def call(s: SymId, o: Origin): Term =
    Tree.Apply(Tree.Ident(s, TypeRepr.NoType, o), Nil, s, TypeRepr.NoType, o)

  private def isAnnotated(s: Statement, fqn: String)(using p: Program): Boolean = s match
    case d: Tree.DefDef => hasAnn(d.symbol, fqn)
    case _              => false

  private def hasAnn(s: SymId, fqn: String)(using p: Program): Boolean =
    p.symbolOf(s).exists(sy =>
      sy.annotations.exists(a => nameOf(a.tpe) == fqn) || sy.droppedAnnotations.contains(fqn))

  private def nameOf(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.TypeRef(_, s)      => p.symbolOf(s).map(_.fullName).getOrElse("")
    case TypeRepr.AppliedType(tc, _) => nameOf(tc)
    case _                           => ""

  /** `@Test def m(): Unit = { … }` → `test("m") { … }`, a statement in the class body.
    *
    * An `expected = classOf[E]` argument becomes `intercept[E] { … }` — NOT dropped. A test that
    * asserts an exception and instead runs the body bare would PASS while checking nothing, which
    * is the silent-omission shape this engine exists to prevent. Until `intercept` is wired the
    * method is left alone, so such a test stays a compile error rather than a false green.
    *
    * `@Ignore` becomes `test(munit.TestOptions("m").ignore) { … }`. Emitting the test ENABLED — as
    * this phase did until the annotation was read — is worse than dropping it: an upstream "we
    * know this one is broken" turns into either a red gate for a defect nobody introduced, or a
    * green one that means nothing. MUnit does not evaluate an ignored body, so the by-name
    * argument keeps the code compiling without running it. */
  private def testCase(d: Tree.DefDef, setups: List[SymId], teardowns: List[SymId],
                       allIgnored: Boolean)(using p: Program): Statement =
    val nm = p.symbolOf(d.symbol).map(_.name).getOrElse("test")
    val expectsThrow: Option[TypeRepr] = p.symbolOf(d.symbol).flatMap(_.annotations
      .filter(a => nameOf(a.tpe) == TestAnn)
      .flatMap(_.args.collect { case ("expected", Tree.Literal(Constant.ClassOfC(t), _, _)) => t })
      .headOption)
    if d.rhs.isEmpty then d
    else
      testsConverted += 1
      // `test("name") { … }` — TWO argument lists, modelled the way `quotes.reflect` does: nested
      // `Apply`, since `Apply.fun` is itself a `Term`. An earlier version routed around this via an
      // un-curried forwarder in an injected base class, on the false belief that the IR could not
      // express currying. The IR follows quotes/BeTASTy and models any correct scala tree; the
      // forwarder was a scaffold built over a gap that did not exist.
      val lit: Term = Tree.Literal(Constant.StringC(nm), TypeRepr.NoType, d.origin)
      val ignored   = allIgnored || hasAnn(d.symbol, IgnoreAnn)
      val nameTerm  =
        if !ignored then lit
        else
          val opts = Tree.Apply(Tree.Ident(testOptionsSym, TypeRepr.NoType, d.origin), List(lit),
                                testOptionsSym, TypeRepr.NoType, d.origin)
          Tree.Select(opts, ignoreSym, TypeRepr.NoType, d.origin)
      val head = Tree.Apply(Tree.Ident(testSym, TypeRepr.NoType, d.origin), List(nameTerm),
                            testSym, TypeRepr.NoType, d.origin)
      // `@Test(expected = classOf[E])` asserts that the body THROWS. Run bare it would pass while
      // checking nothing — the silent-omission shape this engine exists to prevent — so it becomes
      // MUnit's `intercept[E] { … }`, which asserts exactly what java asserted.
      val body0 = expectsThrow match
        case Some(exTpe) =>
          val fn = Tree.TypeApply(Tree.Ident(interceptSym, TypeRepr.NoType, d.origin),
                                  List(TypeTree(exTpe, d.origin)), TypeRepr.NoType, d.origin)
          Tree.Apply(fn, List(d.rhs.get), interceptSym, TypeRepr.NoType, d.origin)
        case scala.None => d.rhs.get
      // JUnit's own nesting: afters(befores(expectException(invoke))). So the `@Before` calls go
      // INSIDE the try — a setup that throws still runs teardown, as in java — and the
      // expected-exception check goes inside them both.
      val setUp =
        if setups.isEmpty then body0
        else Tree.Block(setups.map(call(_, d.origin)), body0, body0.tpe, d.origin)
      val rhs =
        if teardowns.isEmpty then setUp
        else Tree.Try(Nil, setUp, Nil,
                      Some(seq(teardowns.map(call(_, d.origin)), TypeRepr.NoType, d.origin)),
                      setUp.tpe, d.origin)
      Tree.Apply(head, List(rhs), testSym, TypeRepr.NoType, d.origin)

object TestFrameworkTransform:
  val DefaultSuite = "munit.FunSuite"
  /** MUnit's one-time hooks — see the class doc on why these are a fixed contract and not a
    * parameter. */
  val BeforeAllMember = "beforeAll"
  val AfterAllMember  = "afterAll"

  /** Which of CLAUDE.md §1's three kinds a gap is. An error an agent cannot classify as (a) an
    * engine bug, (b) a phase to configure or (c) a library rule to write costs it a full
    * investigation (§4.45), so every finding carries one. */
  enum Fix(val label: String):
    case EngineRule  extends Fix("a") // a Java/Scala fact — fix the engine, unparameterised
    case PhasePolicy extends Fix("b") // configure an existing phase for this library
    case LibraryRule extends Fix("c") // write a rule only this library could ever need

  /** One test-framework construct this phase did not translate. */
  final case class Finding(construct: String, where: Origin, fix: Fix, advice: String):
    def render: String = s"$construct — (${fix.label}) $advice  (${where.javaPath}:${where.line})"

  /** Only the ASSERTIONS remain injected: java's argument order and loose typing differ from
    * MUnit's `(obtained, expected)` with `B <:< A`. That is still shape-adaptation the transform
    * should do itself — see LIBGDX-PORT-STATUS.md — so this too is interim. */
  val AssertsObjectFqn = "balticporter.runtime.Asserts"

  /** The façade: JUnit's assertions with JAVA's argument order and loose typing, over MUnit. */
  val runtimeSources: Map[String, String] = Map(
    AssertsObjectFqn ->
      """package balticporter.runtime
        |
        |/** JUnit's assertions, in JAVA's argument order and with java's loose typing.
        |  *
        |  * An OBJECT, not members of a base class: a java `static` helper emits into the COMPANION
        |  * object, which does not extend the suite, so inherited assertions are invisible exactly
        |  * where java put half of them.
        |  *
        |  * INTERIM. Re-declaring shapes the engine could emit correctly is not what injected
        |  * sources are for — they exist for semantics the target language LACKS. MUnit's own
        |  * `assertEquals(obtained, expected)` differs from java's only by argument order and a
        |  * `B <:< A` constraint, both of which the transform can resolve because it knows the
        |  * operand types. See LIBGDX-PORT-STATUS.md.
        |  */
        |object Asserts:
        |  private def check(cond: Boolean, msg: => String): Unit =
        |    if !cond then throw new AssertionError(msg)
        |
        |  def fail(): Nothing                = throw new AssertionError("failed")
        |  def fail(message: String): Nothing = throw new AssertionError(message)
        |
        |  def assertEquals(expected: Any, actual: Any): Unit =
        |    check(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(message: String, expected: Any, actual: Any): Unit =
        |    check(expected == actual, message)
        |  def assertEquals(expected: Long, actual: Long): Unit =
        |    check(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(expected: Double, actual: Double): Unit =
        |    check(expected == actual, s"expected <$expected> but was <$actual>")
        |  def assertEquals(expected: Double, actual: Double, delta: Double): Unit =
        |    check(math.abs(expected - actual) <= delta, s"expected <$expected> but was <$actual>")
        |  def assertEquals(message: String, expected: Double, actual: Double, delta: Double): Unit =
        |    check(math.abs(expected - actual) <= delta, message)
        |  def assertNotEquals(unexpected: Any, actual: Any): Unit =
        |    check(unexpected != actual, s"did not expect <$unexpected>")
        |
        |  def assertTrue(b: Boolean): Unit                   = check(b, "expected true")
        |  def assertTrue(message: String, b: Boolean): Unit  = check(b, message)
        |  def assertFalse(b: Boolean): Unit                  = check(!b, "expected false")
        |  def assertFalse(message: String, b: Boolean): Unit = check(!b, message)
        |  def assertNull(o: Any): Unit                       = check(o == null, s"expected null, was <$o>")
        |  def assertNotNull(o: Any): Unit                    = check(o != null, "expected non-null")
        |  def assertSame(expected: AnyRef, actual: AnyRef): Unit =
        |    check(expected eq actual, "expected the same instance")
        |
        |  def assertArrayEquals(expected: Array[Byte], actual: Array[Byte]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Short], actual: Array[Short]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Int], actual: Array[Int]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Long], actual: Array[Long]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Char], actual: Array[Char]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Object], actual: Array[Object]): Unit =
        |    check(expected.sameElements(actual), "arrays differ")
        |  def assertArrayEquals(expected: Array[Float], actual: Array[Float], delta: Float): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
        |          "arrays differ")
        |  def assertArrayEquals(message: String, expected: Array[Float], actual: Array[Float],
        |                        delta: Float): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)
        |  def assertArrayEquals(expected: Array[Double], actual: Array[Double], delta: Double): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta),
        |          "arrays differ")
        |  def assertArrayEquals(message: String, expected: Array[Double], actual: Array[Double],
        |                        delta: Double): Unit =
        |    check(expected.length == actual.length &&
        |            expected.indices.forall(i => math.abs(expected(i) - actual(i)) <= delta), message)
        |""".stripMargin,
  )
