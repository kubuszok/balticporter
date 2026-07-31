package balticporter.transform

import balticporter.tir.*

/** A JUnit suite → a CROSS-PLATFORM Scala suite.
  *
  * A ported test suite is the only behavioural evidence this engine can produce, and a JUnit one
  * runs on the JVM alone — neither Scala.js nor Scala Native has JUnit. Emitting Java's tests as
  * JUnit-in-Scala therefore yields a gate that cannot execute on the platforms the port EXISTS for,
  * while looking like full coverage: 221 discovered tests, zero of them runnable on the target.
  *
  * ==The assertion façade is GONE — nothing ships with the port==
  *
  * An earlier version rewrote `Assert.assertEquals(expected, actual)` to a same-shaped member of an
  * injected `balticporter.runtime.Asserts` object, because the direct mapping onto MUnit had
  * measured 1 -> 33 errors. Re-declaring shapes the engine can emit correctly is exactly what
  * injected sources are NOT for (they exist for semantics the target LACKS, like
  * `CollectionsTransform`'s removal-capable iterator), so the façade was deleted and the 33 closed
  * by two type-directed rules here plus one naming rule. Recorded because each was a guess that had
  * to be measured:
  *
  *   - 26 were `Can't compare these two types: Long / Int`. MUnit's `assertEquals[A, B]` infers
  *     both operands independently, so nothing drives Scala's numeric widening; java had already
  *     promoted them at the call. [[promote]] re-applies JAVA'S OWN promotion — widen the narrower
  *     operand to the wider, and `Char`/`Short` (which do not widen to each other) both to `Int`.
  *   - 6 were `Not found: assertEquals` / `fail` inside a java `static` test helper, which emits
  *     into the COMPANION object — a scope that does not extend the suite, so members inherited
  *     from `munit.FunSuite` are invisible there. The fix is NOT to move the helper onto the suite:
  *     MUnit declares every assertion on the `munit.Assertions` OBJECT as well, and an object
  *     member resolves identically from a suite body, a companion object, a nested class and a
  *     lambda. So every assertion is emitted fully qualified (CLAUDE.md §6), and the scope question
  *     disappears instead of being answered.
  *   - 1 was unrelated (a pre-existing error in the ported main sources).
  *
  * The remaining shapes are argument PERMUTATION, which is what a re-compiler is for: java's
  * `(expected, actual)` is MUnit's `(obtained, expected)`, java's leading `String message` is
  * MUnit's trailing `clue`, java's `delta` overloads are `assertEqualsFloat`/`assertEqualsDouble`,
  * and `assertArrayEquals` is a `.toSeq` comparison. Only ONE junit form has no MUnit counterpart —
  * `assertArrayEquals(expected, actual, delta)`, elementwise-with-tolerance — and it is emitted as
  * the loop it means, with both arrays bound to locals first so neither is re-evaluated.
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
  *   5. `munit.TestOptions` is nameable by FQN with no import (CLAUDE.md §6);
  *   6. the assertions exist as members of a stable OBJECT (`munit.Assertions`), with MUnit's own
  *      argument order, clue position and delta-member split — see [[munitCall]].
  *
  * 2–6 name MUnit types and members literally, in this file. 1 is a tree SHAPE, not a string — and
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

  import TestFrameworkTransform.{Finding, Fix, MinArity, NumericRank}

  def name: String = "junit->portable-suite"

  private val AssertClass = "org.junit.Assert"
  /** MUnit declares every assertion twice — on the `Assertions` TRAIT that `FunSuite` mixes in, and
    * on the `Assertions` OBJECT. Emitting through the object is what makes a java `static` test
    * helper translate at all: it lands in the companion object, which does not extend the suite, so
    * an inherited `assertEquals` is not in scope there. An object member is, from every scope. */
  private val MunitAssertions = "munit.Assertions"
  /** MUnit's own members this phase emits. `TestFrameworkTransform.MinArity` is the matching list
    * of the `org.junit.Assert` members mapped ONTO them; a junit name absent from it (`assertThat`)
    * is reported, never guessed at. */
  private val MunitMembers = Set("assertEquals", "assertNotEquals", "assert", "fail",
    "assertEqualsFloat", "assertEqualsDouble")
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
  /** MUnit's assertions on the `munit.Assertions` object, by simple name. */
  private var munitSyms: Map[String, SymId] = Map.empty
  /** `scala.Int` → the `toInt` member that widens to it; see [[promote]]. */
  private var widenSyms: Map[String, SymId] = Map.empty
  /** primitive/`Unit` type references, resolved from the program where it already has them. */
  private var primTypes: Map[String, TypeRepr] = Map.empty
  private var toSeqSym: SymId   = SymId.None
  private var indicesSym: SymId = SymId.None
  private var eqSym: SymId      = SymId.None
  private var neSym: SymId      = SymId.None
  /** distinguishes the locals of one emitted array-with-delta loop from the next. */
  private var nextTmp: Int = 0

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
    // fully qualified to an OBJECT rather than left to inheritance: see [[MunitAssertions]].
    munitSyms = MunitMembers.map(nm => nm -> mint(nm, MunitAssertions + "." + nm)).toMap
    // scala's own widening members and array views — a `Select`'s member renders by SIMPLE name, so
    // these are minted with no qualification at all.
    widenSyms  = NumericRank.keys.map(t => t -> mint("to" + t.stripPrefix("scala."), "to" + t.stripPrefix("scala."))).toMap
    toSeqSym   = mint("toSeq", "toSeq")
    indicesSym = mint("indices", "indices")
    // reference identity, NOT `==` — CLAUDE.md §4.4. The `scala.<op>#` prefix is the emitter's
    // marker for an operator, which renders infix instead of `.eq(x)`.
    eqSym = mint("eq", "scala.<op>#eq")
    neSym = mint("ne", "scala.<op>#ne")
    nextTmp = 0
    val byName = program.symbols.all.groupBy(_.fullName)
    def prim(fqn: String): TypeRepr =
      TypeRepr.TypeRef(TypeRepr.NoPrefix,
        byName.get(fqn).flatMap(_.headOption).map(_.id)
          .getOrElse(mint(fqn.substring(fqn.lastIndexOf('.') + 1), fqn)))
    primTypes = (NumericRank.keySet + "scala.Unit" + "scala.Boolean").map(t => t -> prim(t)).toMap
    unitSym = headSymOf(primTypes("scala.Unit"))

    val symbols0 = SymbolTable(program.symbols.all ++ added)
    given Program = program.rebuilt(symbols = symbols0)
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
    program.rebuilt(units, symbols)

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
        // `RuleScope.covers`, not `startsWith`: a bare prefix makes `org.hamcrest` cover
        // `org.hamcrestic`, which is §4.56's trap and the one the lint in this package exists to
        // stop. No corpus package is named that, and that is exactly why the site was worth fixing
        // rather than exempting — nothing would ever have reported it.
        val isHamcrest = RuleScope.covers(s.fullName, "org.hamcrest")
        val isAssertThat = s.name == "assertThat"
        if isHamcrest || isAssertThat then
          val what = if isAssertThat then "assertThat" else s.fullName
          program.usages(id).foreach(u => found += Finding(what, u.site.origin, Fix.EngineRule,
            "Hamcrest is a second assertion vocabulary (`assertThat(x, is(equalTo(y)))`); this " +
            "phase maps JUnit's `Assert` members only, and MUnit has no matcher algebra to map a " +
            "matcher ONTO. OUT OF SCOPE by decision, reported so it is not mistaken for coverage: " +
            "either keep this suite on the JVM/JUnit path with hamcrest on the test classpath, or " +
            "translate each matcher into the assertion it means."))
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

  // -------------------------------------------------------------------------
  // Assertions — org.junit.Assert onto munit.Assertions, by ARGUMENT TYPE
  // -------------------------------------------------------------------------

  /** `org.junit.Assert.assertX(…)` → the MUnit assertion that means the same thing.
    *
    * Java's shape is `(expected, actual)` with an OPTIONAL leading `String message`; MUnit's is
    * `(obtained, expected)` with an optional TRAILING `clue`. So both ends of the argument list
    * move, and which junit overload was resolved decides how — a `delta` third argument is a
    * different assertion in MUnit, not a third argument to the same one.
    *
    * The overload is read from the ARGUMENTS' static types, not from the callee's signature: every
    * TIR term carries a structured `TypeRepr` by construction, whereas a callee's parameter list is
    * only as good as the frontend's key encoding for an EXTERNAL symbol. Both were available here;
    * this is the one the IR contract guarantees.
    *
    * A member with no mapping (`assertThat`) is LEFT ALONE and reported. Rewriting it to something
    * plausible is the silent-miss this engine exists to prevent, and leaving it keeps the reference
    * to `org.junit` that [[PortabilityCheck]] already counts.
    *
    * NOTE for anyone unit-testing this against `SpoonTir.fromSource`: that path builds with
    * `noClasspath`, so a `import static org.junit.Assert.assertEquals` in a one-file snippet
    * resolves to `this.assertEquals(…)` and this hook never fires. Write `Assert.assertEquals(…)`
    * explicitly, or the test asserts against unrewritten output and passes for the wrong reason. A
    * model built over a whole source tree resolves the static import correctly. */
  override def transformApply(t: Tree.Apply)(using p: Program): Term = t.fun match
    case Tree.Select(recv, m, _, o) if recvIs(recv, AssertClass) =>
      val nm = p.symbolOf(m).map(_.name).getOrElse("")
      munitCall(nm, t.args, o).getOrElse {
        found += Finding(AssertClass + "." + nm, o, Fix.EngineRule,
          s"no MUnit counterpart is known for this `$nm` overload (${t.args.size} argument(s)), so " +
          "the call is left on org.junit — which compiles only with JUnit on the classpath and " +
          "cannot run on Scala.js / Native. Add the mapping to TestFrameworkTransform.munitCall.")
        t
      }
    case _ => t

  /** java's `(message?, expected, actual, delta?)` → MUnit's `(obtained, expected, delta?, clue?)`.
    *
    * `hasMsg` is decided STRUCTURALLY: a leading `String` is junit's message exactly when the call
    * carries more arguments than the member's minimal arity. That separates every junit overload
    * that exists — `assertEquals(String, Object, Object)` from `assertEquals(double, double,
    * double)`, and `assertEquals(a, b)` on two Strings from either — without naming one. */
  private def munitCall(nm: String, args: List[Term], o: Origin)(using p: Program): Option[Term] =
    MinArity.get(nm).flatMap { min =>
      val hasMsg = args.sizeIs > min && args.headOption.exists(a => nameOf(a.tpe) == "java.lang.String")
      val clue   = if hasMsg then List(args.head) else Nil
      val rest   = if hasMsg then args.tail else args
      (nm, rest) match
        // junit's `fail()` has no message; MUnit's `fail` requires one.
        case ("fail", Nil) =>
          Some(call("fail", List(clue.headOption.getOrElse(constTerm(Constant.StringC("failed"), "java.lang.String", o))), o))
        case ("assertTrue", List(c))  => Some(call("assert", c :: clue, o))
        // `assert(!c)` would need an operator node for one gain in readability; comparing against
        // the literal is the same assertion and reports the same way.
        case ("assertFalse", List(c)) => Some(call("assertEquals", c :: bool(false, o) :: clue, o))
        case ("assertNull", List(x))    => Some(call("assertEquals", x :: nul(o) :: clue, o))
        case ("assertNotNull", List(x)) => Some(call("assertNotEquals", x :: nul(o) :: clue, o))
        // REFERENCE identity — scala's `==` is java's `equals` (CLAUDE.md §4.4), so `assertEquals`
        // here would silently weaken every `assertSame` into an `assertEquals`.
        case ("assertSame", List(e, a))    => Some(call("assert", infix(a, eqSym, e, o) :: clue, o))
        case ("assertNotSame", List(e, a)) => Some(call("assert", infix(a, neSym, e, o) :: clue, o))
        case ("assertEquals" | "assertNotEquals", List(e, a)) =>
          val (a2, e2) = promote(a, e)
          Some(call(if nm == "assertEquals" then "assertEquals" else "assertNotEquals", a2 :: e2 :: clue, o))
        case ("assertEquals", List(e, a, delta)) =>
          Some(call(deltaMember(List(e, a, delta)), a :: e :: delta :: clue, o))
        case ("assertArrayEquals", List(e, a)) =>
          // `guarded` for the same reason as in `widen`: an operand that renders infix or as a
          // control-flow expression would bind `.toSeq` to its last branch.
          Some(call("assertEquals",
            select(guarded(a), toSeqSym, o) :: select(guarded(e), toSeqSym, o) :: clue, o))
        case ("assertArrayEquals", List(e, a, delta)) => arrayWithDelta(e, a, delta, clue, o)
        case _ => scala.None
    }

  /** MUnit splits java's one `assertEquals(…, delta)` by WIDTH, and its `delta` parameter is not
    * generic — so a `Double` operand anywhere forces the double form, exactly as java's own
    * overload resolution did. */
  private def deltaMember(operands: List[Term])(using p: Program): String =
    val floatRank = NumericRank("scala.Float")
    if operands.forall(x => NumericRank.getOrElse(nameOf(x.tpe), Int.MaxValue) <= floatRank)
    then "assertEqualsFloat" else "assertEqualsDouble"

  /** JAVA'S BINARY NUMERIC PROMOTION, re-applied.
    *
    * `assertEquals(int, long)` is legal java because the call promoted the `int` before comparing.
    * MUnit's `assertEquals[A, B]` infers each operand independently, so nothing drives scala's
    * widening and the pair is rejected — 26 of the 33 errors that once justified an injected
    * façade. Widening the NARROWER operand is the only safe direction; the reverse loses bits.
    *
    * `Char` and `Short` share a rank because neither widens to the other, which is why the
    * equal-rank case promotes both to `Int` rather than picking one. */
  private def promote(x: Term, y: Term)(using p: Program): (Term, Term) =
    val (tx, ty) = (nameOf(x.tpe), nameOf(y.tpe))
    (NumericRank.get(tx), NumericRank.get(ty)) match
      case (Some(rx), Some(ry)) if tx != ty =>
        val to = if rx > ry then tx else if ry > rx then ty else "scala.Int"
        (widen(x, tx, to, p), widen(y, ty, to, p))
      case _ => (x, y)

  private def widen(t: Term, from: String, to: String, p: Program): Term =
    if from == to then t else select(guarded(t)(using p), widenSyms(to), t.origin, primTypes(to))

  /** Parenthesize a receiver that would otherwise re-associate. `a * b` is a bare `Apply` in the
    * TIR but renders INFIX, so `.toLong` on it would attach to `b` — and `x >> 2.toLong` is not
    * `(x >> 2).toLong`. A `Block` with no statements is the TIR's only way to say "parenthesized". */
  private def guarded(t: Term)(using p: Program): Term = t match
    case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _)
        if p.symbolOf(m).exists(_.fullName.startsWith("scala.<op>#")) =>
      Tree.Block(Nil, t, t.tpe, t.origin)
    case _: Tree.If | _: Tree.Match | _: Tree.Lambda => Tree.Block(Nil, t, t.tpe, t.origin)
    case _                                           => t

  /** The ONE junit assertion with no MUnit counterpart: elementwise comparison with a tolerance.
    *
    * Emitted as the loop it means. Both arrays are bound to locals FIRST — the two operands are
    * arbitrary expressions (`polygon.getTransformedVertices()`), and naming each once is the
    * difference between java's one evaluation and one per element. The length check comes first,
    * as it does in junit, so a size mismatch reports as a size mismatch. */
  private def arrayWithDelta(e: Term, a: Term, delta: Term, clue: List[Term], o: Origin)
                            (using p: Program): Option[Term] =
    if a.tpe == TypeRepr.NoType || e.tpe == TypeRepr.NoType then scala.None
    else
      val n    = nextTmp; nextTmp += 1
      val int  = primTypes("scala.Int")
      val unit = primTypes("scala.Unit")
      val oS = mint(s"bpObtained$n", s"bpObtained$n", Flags(), a.tpe)
      val eS = mint(s"bpExpected$n", s"bpExpected$n", Flags(), e.tpe)
      val iS = mint(s"bpIndex$n", s"bpIndex$n", Flags(), int)
      def obtained = Tree.Ident(oS, a.tpe, o)
      def expected = Tree.Ident(eS, e.tpe, o)
      def at(arr: Term, t: TypeRepr) = Tree.ArrayAccess(arr, Tree.Ident(iS, int, o), elemOf(t), o)
      val lengths = call("assertEquals",
        Tree.ArrayLength(obtained, int, o) :: Tree.ArrayLength(expected, int, o) :: clue, o)
      val body = call(deltaMember(List(elemProbe(e, o), elemProbe(a, o), delta)),
        at(obtained, a.tpe) :: at(expected, e.tpe) :: delta :: clue, o)
      val loop = Tree.ForEach(Tree.ValDef(iS, TypeTree(int, o), None, o),
                              select(obtained, indicesSym, o), body, unit, o)
      Some(Tree.Block(
        List(Tree.ValDef(oS, TypeTree(a.tpe, o), Some(a), o),
             Tree.ValDef(eS, TypeTree(e.tpe, o), Some(e), o),
             lengths),
        loop, unit, o))

  /** a stand-in term carrying the array's ELEMENT type, so [[deltaMember]] picks the member from
    * the element width rather than from `Array`. */
  private def elemProbe(arr: Term, o: Origin)(using p: Program): Term =
    Tree.Literal(Constant.UnitC, elemOf(arr.tpe), o)

  private def elemOf(t: TypeRepr)(using p: Program): TypeRepr = t match
    case TypeRepr.AppliedType(tc, List(el)) if nameOf(tc) == "scala.Array" => el
    case _                                                                => TypeRepr.NoType

  private def call(member: String, args: List[Term], o: Origin): Term =
    val s = munitSyms(member)
    Tree.Apply(Tree.Ident(s, TypeRepr.NoType, o), args, s, TypeRepr.NoType, o)

  private def select(q: Term, m: SymId, o: Origin, tpe: TypeRepr = TypeRepr.NoType): Term =
    Tree.Select(q, m, tpe, o)

  /** `a eq b` — an operator application, which the emitter renders infix off the `scala.<op>#` tag. */
  private def infix(l: Term, op: SymId, r: Term, o: Origin): Term =
    Tree.Apply(Tree.Select(l, op, TypeRepr.NoType, o), List(r), op, primTypes("scala.Boolean"), o)

  private def constTerm(c: Constant, tpeName: String, o: Origin): Term =
    Tree.Literal(c, primTypes.getOrElse(tpeName, TypeRepr.NoType), o)
  private def bool(v: Boolean, o: Origin): Term = constTerm(Constant.BoolC(v), "scala.Boolean", o)
  private def nul(o: Origin): Term = Tree.Literal(Constant.NullC, TypeRepr.NoType, o)

  private def headSymOf(t: TypeRepr): SymId = t match
    case TypeRepr.TypeRef(_, s) => s
    case _                      => SymId.None

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
      // DECISION PROVENANCE, one row per TEST MEMBER — already declaration-level: a `@Test` method
      // is the unit this phase reshapes, and it stops being a method at all.
      //
      // What the row carries is the part an agent CANNOT read off the emitted file. `test("m") { … }`
      // plainly came from a `@Test`; that setup and teardown were INLINED into its body is exactly
      // what is invisible, and it is where §4.4's two silent defects lived — JUnit runs `@Before`
      // before every test on a fresh instance and `@After` whether or not the test threw, and MUnit
      // has neither. `detail` names them.
      //
      // Universal: JUnit's semantics against MUnit's are a fact about the two frameworks, identical
      // for every library. `suite`/`testMember` are constructor parameters and deliberately do NOT
      // make this `Configured` — the class doc records that they name the two least interesting
      // parts of a contract only MUnit satisfies, and a reason must say where the fix LIVES.
      record(Decision(
        kind       = Decision.Kind.RetypedSignature,
        subject    = d.symbol,
        subjectFqn = p.symbolOf(d.symbol).map(_.fullName).getOrElse(nm),
        detail = Map(
          "from"      -> "@org.junit.Test def",
          "to"        -> s"""$testMember("$nm") { … } registered on $suite""",
          "ignored"   -> (if ignored then "yes" else "no"),
          "intercept" -> expectsThrow.map(nameOf).getOrElse(""),
          "inlined"   -> (setups ++ teardowns).flatMap(s => p.symbolOf(s).map(_.name)).mkString(", "),
          "why"       -> ("a JUnit suite runs on the JVM alone; and MUnit has neither @Before " +
            "(which JUnit runs before EVERY test, on a fresh instance) nor @After (which it runs " +
            "whether or not the test threw), so both are inlined here and nothing else says so"),
        ),
        reason = Reason.Universal("test-framework"),
        origin = d.origin,
      ))
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

  /** Widening rank for java's BINARY NUMERIC PROMOTION: a value of rank r converts, without loss,
    * to any numeric type of higher rank. `Char` and `Short` share a rank because neither widens to
    * the other — java promotes that pair to `Int`, and so does [[TestFrameworkTransform.promote]].
    *
    * Deliberately NOT the emitter's copy of this table: that one exists to disambiguate an OVERLOAD
    * at emission, this one to rewrite a TREE. Sharing them would couple a transform to a backend. */
  val NumericRank: Map[String, Int] = Map(
    "scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2, "scala.Int" -> 3,
    "scala.Long" -> 4, "scala.Float" -> 5, "scala.Double" -> 6)

  /** How many arguments each `org.junit.Assert` member takes WITHOUT java's optional leading
    * `String message`. Everything above this count with a leading `String` is that message — which
    * is what separates `assertEquals(String, Object, Object)` from `assertEquals(double, double,
    * double)` without either being named. */
  val MinArity: Map[String, Int] = Map(
    "assertEquals" -> 2, "assertNotEquals" -> 2, "assertArrayEquals" -> 2,
    "assertSame" -> 2, "assertNotSame" -> 2, "assertTrue" -> 1, "assertFalse" -> 1,
    "assertNull" -> 1, "assertNotNull" -> 1, "fail" -> 0)
