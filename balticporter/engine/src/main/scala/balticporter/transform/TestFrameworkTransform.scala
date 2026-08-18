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
) extends Phase, balticporter.core.SurfacePolicy, PolicyBound:

  import TestFrameworkTransform.{Expect, ExpectMsg, Finding, Fix, FreshStateMember, InitBlockName,
                                 MinArity, NumericRank, Roots}

  def name: String = "junit->portable-suite"

  /** WHICH DECLARATIONS THIS RUN EMITS — the one question the per-test reconstruction cannot answer
    * from its `Program` (see [[planFreshState]]).
    *
    * A dependent's model CONTAINS its base's units (`ENGINE-LIMITS.md` D2), so a suite whose java
    * superclass is a BASE module's test class is a chain this run can see and cannot write: the
    * `override def bpFreshState` it would emit names a member only the base's own run could have
    * put on that parent, and whether the base put one there is not derivable here. That chain is
    * REFUSED and counted rather than guessed at (`CLAUDE.md` §1.5). [[RunScope.whole]] is the
    * default and is exactly the truth for a base port, a single-module port and every spec. */
  private var scope: RunScope = RunScope.whole

  def bindPolicy(binder: PolicyBinder): Unit = scope = binder.run

  /** This phase SHAPES SIGNATURES — CLAUDE.md §1's obligation on a (b), which it owed and did not
    * pay. A converted suite gains [[suite]] as a PARENT and every `@Test` method becomes a
    * [[testMember]] call, so two modules configured differently emit two different type hierarchies
    * for the same Java. Without this, [[balticporter.core.PortManifest.fingerprint]] falls back to
    * the phase NAME and `junit->portable-suite(munit.FunSuite)` compares EQUAL to
    * `junit->portable-suite(utest.TestSuite)`: `SurfaceMissing` cannot see the difference, and a
    * same-name pair could neither be compared nor composed (`ENGINE-LIMITS.md` CT9 Face B's third
    * change, which makes such a pair a refusal rather than a silent double application).
    *
    * Both parameters, and only them: they are the whole of what a caller can vary. The rest of this
    * phase's contract is MUnit named literally in tree-building code (see the class doc), which is
    * not policy and cannot differ between two instances. */
  def surfaceFingerprint: String = s"suite=$suite,test=$testMember"

  /** JUnit's assertion statics live at THREE FQNs, and one table maps all of them.
    *
    * `junit.framework.Assert` is JUnit 3's assertion class; `junit.framework.TestCase` extends it,
    * so `import static junit.framework.TestCase.assertEquals` resolves to the same member and a
    * frontend may report either as the receiver (Spoon reports the executable's DECLARING type,
    * which is `Assert`; a frontend reporting the qualifier would say `TestCase`). Both are covered
    * because which one arrives is not this phase's fact to know.
    *
    * Note what this is NOT evidence of: a class reaching these members through a static import is
    * an ordinary JUnit-4 suite that happens to have imported JUnit 3's copy of `assertEquals` —
    * five of liqp's do — not a `TestCase` subclass. [[survey]]'s JUnit-3 scan keys off the PARENT
    * and correctly says nothing about them.
    *
    * Their contract is `org.junit.Assert`'s exactly: `(expected, actual)`, an optional leading
    * `String message`, the same minimal arities — JUnit 4's `Assert` was written as a superset of
    * this one. So the set is a §1(a) fact about JUnit, written into the engine. It must not become
    * a constructor parameter: an empty default would silently stop converting `org.junit.Assert`
    * too, and a per-library list of JUnit's own class names is policy nobody can get right twice. */
  private val AssertClasses = Set("org.junit.Assert", "junit.framework.Assert", "junit.framework.TestCase")
  /** MUnit declares every assertion twice — on the `Assertions` TRAIT that `FunSuite` mixes in, and
    * on the `Assertions` OBJECT. Emitting through the object is what makes a java `static` test
    * helper translate at all: it lands in the companion object, which does not extend the suite, so
    * an inherited `assertEquals` is not in scope there. An object member is, from every scope. */
  private val MunitAssertions = "munit.Assertions"
  /** MUnit's own members this phase emits. `TestFrameworkTransform.MinArity` is the matching list
    * of the `org.junit.Assert` members mapped ONTO them; a junit name absent from it (`assertThat`)
    * is reported, never guessed at. */
  private val MunitMembers = Set("assertEquals", "assertNotEquals", "assert", "fail",
    "assertEqualsFloat", "assertEqualsDouble", "intercept")
  /** JUnit's OTHER spelling of `@Test(expected = …)`, and the one with a state machine behind it.
    *
    * `@Rule public ExpectedException thrown = ExpectedException.none()` arms a matcher at the
    * `thrown.expect(…)` CALL and applies it to whatever the test throws from there on. There is no
    * shape to derive a general `@Rule` from — see [[adviceFor]] — but THIS rule's contract is
    * JUnit's own and is written down (`ExpectedExceptionStatement.evaluate`), so it is a §1(a) fact
    * about JUnit and MUnit and not policy. [[expectedException]] MODELS it — the matcher list armed
    * where java armed it and one `try`/`catch` over the whole test — and the behavioural
    * differences between java's shape and the emitted one are enumerated there, each one a guard, a
    * shape or a counted refusal (`CLAUDE.md` §3). */
  private val ExpectedExceptionCls = "org.junit.rules.ExpectedException"
  private val RuleAnn        = "org.junit.Rule"
  private val ClassRuleAnn   = "org.junit.ClassRule"
  /** the ONE hamcrest member this phase names, and it names it because `expect(Matcher)` is one of
    * junit's two overloads: `matches(Object)` is the `Matcher` CONTRACT, so asserting it over the
    * intercepted value is universal rather than one translation per matcher class. */
  private val HamcrestMatcher = "org.hamcrest.Matcher"
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
  /** `java.lang.Object` — the type java's `assertEquals(Object, Object)` widened to; see [[widened]]. */
  private var objType: TypeRepr = TypeRepr.NoType
  private var toSeqSym: SymId   = SymId.None
  private var indicesSym: SymId = SymId.None
  private var eqSym: SymId      = SymId.None
  private var neSym: SymId      = SymId.None
  /** members the `ExpectedException` translation emits: hamcrest's own `matches`, and the two
    * `java.lang.Throwable`/`java.lang.String` members junit's `expectMessage(String)` means. */
  private var matchesSym: SymId    = SymId.None
  private var getMessageSym: SymId = SymId.None
  private var containsSym: SymId   = SymId.None
  private var throwableType: TypeRepr = TypeRepr.NoType
  /** …and the members the RULE's own state machine is spelled with: junit accumulates matchers in a
    * list and requires all of them (`allOf`), so the emitted accumulator is a list and the check is
    * a `forall` over it. `Nil`/`:+`/`forall`/`isEmpty`/`nonEmpty`/`apply` are scala's; `&&` and `+`
    * are the two operators the message predicate and the failure clue need. */
  private var nilSym: SymId      = SymId.None
  private var appendSym: SymId   = SymId.None
  private var forallSym: SymId   = SymId.None
  private var isEmptySym: SymId  = SymId.None
  private var nonEmptySym: SymId = SymId.None
  private var applySym: SymId    = SymId.None
  private var andSym: SymId      = SymId.None
  private var plusSym: SymId     = SymId.None
  /** `(java.lang.Throwable) => scala.Boolean` and the list of it — a `MethodType` because that is
    * how this backend renders a function type, so no `scala.Function1` symbol has to be minted. */
  private var predType: TypeRepr     = TypeRepr.NoType
  private var predListType: TypeRepr = TypeRepr.NoType
  /** distinguishes the locals of one emitted array-with-delta loop from the next. */
  private var nextTmp: Int = 0

  // symbol minting has to continue DURING the walk (each converted suite needs its own
  // `beforeAll`/`afterAll` symbol), so the counter and the buffer are fields, not `run` locals.
  private var nextId: Int = 0
  private val added = collection.mutable.ListBuffer[Symbol]()
  /** declarations whose consumed annotation must be stripped from the emitted output. */
  private val consumed = collection.mutable.Set.empty[SymId]
  /** the `@Test` methods that SURVIVE as `def`s ([[virtualTests]]) and must lose that annotation. */
  private val consumedTests = collection.mutable.Set.empty[SymId]
  private val found = collection.mutable.ListBuffer[Finding]()
  private var suitesConverted = 0
  private var testsConverted  = 0
  /** `thrown.expect(…)` sites this run turned into an `intercept`; the DECLINED ones are one
    * [[Finding]] each, naming the guard (`CLAUDE.md` §3 — a count of conversions says nothing about
    * what was refused, and `refused = 0` is a bar met by converting nothing). */
  private var rulesConverted  = 0

  /** JS-E07's citation state — set when [[promote]] actually widened an operand, read by
    * [[transformDefDef]], which the bottom-up traversal reaches AFTER the body it belongs to.
    *
    * A flag and not a set of `Apply` nodes, because the citation is per DECLARATION (`CLAUDE.md`
    * §5.1) and the declaration is the frame this phase's rewrite has no other way to name: the
    * assertion rewrite is a `transformApply` hook, which sees a call and never the member it is in.
    * Cleared at each declaration, so a widening in one method cannot be attributed to the next. */
  private var promotedHere = false

  /** ==A TEST-CLASS HIERARCHY, which java has and this conversion had never met==
    *
    * Every suite this phase had converted before was a ROOT: a class declaring its own `@Test`s and
    * extending nothing the program declares. Java does not require that, and the first library to
    * write the ordinary shape — an abstract `RenderingTestCase`, an abstract `FullSpecTestCase`
    * declaring the one `@Test`, and four concrete subclasses that inherit it — breaks the
    * conversion in two independent ways, neither of which any per-class rule can see.
    *
    * '''THE PARENT.''' `munit.FunSuite` is a CLASS, and so is the java superclass. Prepended at the
    * class that declares the `@Test`, the emitted clause reads
    * `extends munit.FunSuite with RenderingTestCase` and scalac answers *class RenderingTestCase is
    * not a trait*. Scala has ONE superclass, so there is exactly one place the suite can go: the
    * ROOT of the chain of program-declared classes the `@Test` declarer belongs to. [[suiteAnchors]]
    * is that set, and for a class with no program-declared superclass it is the class itself —
    * which is every suite in the corpus before this one, so the rule is flat by construction.
    *
    * '''THE METHOD.''' A `@Test` becomes a `test("m") { … }` STATEMENT, and a statement does not
    * override anything: a subclass that overrides its parent's test method would emit a SECOND
    * registration under the same name (MUnit rejects a duplicate at run time — after the compile,
    * after every count) and a `super.m()` inside it names a member that no longer exists. Java runs
    * ONE test per concrete class and it is the override.
    *
    * So a `@Test` that participates in an override relation among program-declared classes stays a
    * `def` — [[virtualTests]] — and the registration is emitted ONCE, at the TOP declarer, as a CALL
    * to it. Virtual dispatch then reproduces java exactly: each concrete subclass registers the one
    * inherited test and runs its own override. The guard is structural (does an ancestor or a
    * descendant this program declares also declare a `@Test` at this name and arity), so a suite
    * with no test-class ancestry takes the statement form it always did.
    *
    * Both sets are computed ONCE per [[run]], over the whole program, because neither is a fact
    * about the class being converted — `convert` is handed one `ClassDef` and the answer is in its
    * ancestors and its descendants. */
  private var classDefs: Map[SymId, Tree.ClassDef] = Map.empty
  private var suiteAnchors: Set[SymId]             = Set.empty
  /** `@Test` methods that stay `def`s because java's own override relation reaches them. */
  private var virtualTests: Set[SymId]             = Set.empty
  /** of those, the ones whose class is the TOP declarer — the single registration site. */
  private var virtualRoots: Set[SymId]             = Set.empty
  /** the classes that declare at least one `@Test` — a suite, in this phase's sense. */
  private var testDeclarers: Set[SymId]            = Set.empty

  // ---- the per-test RECONSTRUCTION (`ENGINE-LIMITS.md` X4) — see [[planFreshState]] ------------

  /** class → the `bpFreshState` member IT declares. */
  private var freshSym: Map[SymId, SymId]   = Map.empty
  /** class → the NEAREST ANCESTOR that also declares one, which is `super.bpFreshState()`'s target
    * and the reason the member carries `override`. Not necessarily the direct parent: a class the
    * lowering could not reach is skipped, and the chain closes over it. */
  private var freshSuper: Map[SymId, SymId] = Map.empty
  /** class → the member a `test(…)` registration EMITTED IN IT may call: its own, or the nearest
    * one it inherits. Distinct from [[freshSym]] because a virtual test registers at the TOP
    * declarer, which may hold no state of its own while its subclasses do. */
  private var freshCall: Map[SymId, SymId]  = Map.empty
  /** the java `final` instance fields the reconstruction ASSIGNS, which is what makes them `var`s.
    * Java rebuilt the object, so its `final` was per-construction; scala has one object, so the
    * same per-test value needs a mutable slot. Narrowed to the fields the lowering really writes —
    * every other field is emitted exactly as it was. */
  private val madeMutable = collection.mutable.Set.empty[SymId]
  private var suitesRebuilt = 0

  /** Constructs this phase could not translate, with their CLAUDE.md §1 classification. Empty
    * until [[run]] has executed. A migrator that wants the number on every run can read it; `run`
    * already prints a one-line summary plus the details, so no wiring is required for it to be
    * LOUD. */
  def findings: List[Finding] = found.toList

  /** the program-declared SUPERCLASS of `cd`, if the program declares one.
    *
    * A parent this program does not declare is not a candidate — its `extends` clause is a fact
    * about a class file (§4.56) — and neither is a TRAIT, which is what java's `implements` becomes
    * and which scala is happy to mix in beside the suite. */
  private def classParentOf(cd: Tree.ClassDef)(using p: Program): Option[Tree.ClassDef] =
    cd.parents.iterator
      .map { case tt: TypeTree => headSymOf(tt.tpe); case t: Term => headSymOf(t.tpe) }
      .flatMap(classDefs.get)
      .find(c => !p.symbolOf(c.symbol).exists(_.flags.isTrait))

  /** every program-declared class STRICTLY above `cd`, nearest first. `seen` is a cycle guard: a
    * malformed hierarchy must not hang the pipeline, and answering the prefix it did see is the
    * conservative arm. */
  private def classAncestry(cd: Tree.ClassDef)(using p: Program): List[Tree.ClassDef] =
    def go(c: Tree.ClassDef, seen: Set[SymId]): List[Tree.ClassDef] =
      classParentOf(c) match
        case Some(pc) if !seen(pc.symbol) => pc :: go(pc, seen + pc.symbol)
        case _                            => Nil
    go(cd, Set(cd.symbol))

  /** the `@Test` methods a class DECLARES, keyed the way an override is decided here: by NAME and
    * ARITY. Deliberately looser than a descriptor and exact for this question — java cannot
    * overload a zero-argument test method, and every JUnit 4 `@Test` is zero-argument. */
  private def testKeys(cd: Tree.ClassDef)(using p: Program): Map[(String, Int), SymId] =
    cd.body.collect {
      case d: Tree.DefDef if isAnnotated(d, TestAnn) && d.rhs.nonEmpty =>
        (p.symbolOf(d.symbol).map(_.name).getOrElse(""), d.paramss.map(_.size).sum) -> d.symbol
    }.toMap

  /** Fill [[classDefs]], [[suiteAnchors]], [[virtualTests]] and [[virtualRoots]] for this run. */
  private def planHierarchy(program: Program)(using p: Program): Unit =
    classDefs = program.units.flatMap(StandardTraversal.allClassDefs).map(c => c.symbol -> c).toMap
    val keysOf  = classDefs.view.mapValues(testKeys).toMap
    val declares = keysOf.filter(_._2.nonEmpty).keySet
    testDeclarers = declares
    // THE ANCHOR: the topmost program-declared class above each `@Test` declarer, or itself.
    suiteAnchors = declares.map { s =>
      classAncestry(classDefs(s)).lastOption.map(_.symbol).getOrElse(s)
    }
    // THE VIRTUALS: a `@Test` whose (name, arity) another program-declared class in its own chain
    // also declares as a `@Test`. Computed from the ANCESTRY alone and then closed downwards, so
    // both sides of an override edge are in the set with one walk per class.
    val above = collection.mutable.Map.empty[SymId, Set[(String, Int)]]
    classDefs.keys.foreach { s =>
      above(s) = classAncestry(classDefs(s)).flatMap(a => keysOf(a.symbol).keys).toSet
    }
    val overridden = // keys an ANCESTOR declares and this class re-declares
      classDefs.keys.flatMap(s => keysOf(s).keySet.intersect(above(s)).map(k => s -> k)).toList
    val virtualKeys = overridden.map(_._2).toSet
    val vs = for
      (s, keys) <- keysOf.toList
      (k, sym)  <- keys
      // a key is virtual for THIS class only where the class shares a chain with the override —
      // two unrelated suites that happen to name a test the same way are not an override relation.
      if virtualKeys(k) && (above(s).contains(k) || classDefs.keys.exists(d =>
           keysOf(d).contains(k) && above(d).contains(k) && classAncestry(classDefs(d)).exists(_.symbol == s)))
    yield (s, k, sym)
    virtualTests = vs.map(_._3).toSet
    virtualRoots = vs.collect { case (s, k, sym) if !above(s).contains(k) => sym }.toSet

  // -------------------------------------------------------------------------
  // JUnit constructs a FRESH INSTANCE per @Test — the per-test RECONSTRUCTION
  // -------------------------------------------------------------------------

  /** ==JUNIT REBUILDS THE TEST OBJECT; MUNIT HAS ONE SUITE INSTANCE==
    *
    * `BlockJUnit4ClassRunner.methodBlock` opens with `createTest()` — a `newInstance` on the class's
    * ONE public constructor — for every `@Test`, so java runs the whole of JLS 12.5 once per test:
    * the object's fields are ZEROED by the allocation, the field initialisers and instance
    * initialiser blocks run as one sequence in TEXTUAL ORDER (step 4), then the constructor body
    * (step 5), then `@Before`. MUnit registers `test(name)(body)` on ONE instance, so a converted
    * suite's fields are built ONCE and every test after the first inherits the last one's state.
    *
    * Measured, before this lowering existed, at **4 of one suite's 10** — four instance fields with
    * their own initialisers, and the second test met a `BehaviorTree` that already had a root
    * (`ENGINE-LIMITS.md` X4). Discharging `@Before` correctly is what makes the gap VISIBLE and not
    * what causes it: the state java rebuilt is the CONSTRUCTOR's, and no `@Before` translation
    * reaches it. Nothing else can see it — 0 compile errors, 0 skipped, every check count flat,
    * `outcomes N of N emitted` — which is §3's whole argument for running the suite.
    *
    * ==THE LOWERING: java's own initialisation sequence, HOISTED OUT OF THE CLASS BODY==
    *
    * Each class in the closure below declares
    * {{{
    * override def bpFreshState(): Unit = { <zero MY fields>; super.bpFreshState(); <MY step 4>; <MY ctor body> }
    * }}}
    * and every converted test body opens with `bpFreshState()`, AHEAD of the `@Before` calls. The
    * class body no longer initialises anything: a field's initialiser MOVES into the member, its
    * declaration keeps only the JVM default the emitter already writes for an uninitialised java
    * field, and an instance initialiser block's statements move with them, in java's textual order.
    *
    * Three properties that are the reason for this exact shape, each verified against a real JUnit 4
    * run rather than reasoned about:
    *
    *  - '''zeroing precedes ALL initialisation, and that is what the chain order buys.''' Each class
    *    zeroes its own fields BEFORE delegating upward, so the sequence is
    *    `zero(C), zero(B), zero(A), init(A), init(B), init(C)` — exactly java's, where the
    *    allocation zeroes every field of every class in the hierarchy before the superclass
    *    constructor runs. It is not decoration: a superclass constructor that calls an overridden
    *    method reading a SUBCLASS field sees the DEFAULT in java (probed: `Base.ctor sees sub=null`
    *    on the second test, after the first had assigned it), and a chain that zeroed on the way
    *    down would show it the previous test's value — X4 itself, one level in.
    *  - '''the initialisation is HOISTED, never duplicated.''' Left in the class body it would run
    *    once at suite construction AND once per test, so a field initialiser with an effect outside
    *    the object would run N+1 times where java ran it N. Hoisting also puts it on java's side of
    *    `@BeforeClass`: junit runs the static hook BEFORE the first construction, and MUnit
    *    constructs the suite before `beforeAll()`, so an un-hoisted initialiser runs on the wrong
    *    side of it.
    *  - '''a `private` field is reset BY ITS OWN CLASS.''' That is why the member chains rather than
    *    being inlined at the concrete suite: a base test case's `private` field is not nameable from
    *    its subclass, and java's constructor chain is what runs each class's own step 4.
    *
    * ==WHAT THE LOWERING DOES NOT REPRODUCE==
    *
    * `CLAUDE.md` §3: each is (i) a structural GUARD, (ii) impossible by the SHAPE emitted, or (iii)
    * COUNTED — one [[Finding]] per site naming the guard.
    *
    *  1. '''OBJECT IDENTITY.''' SHAPE-LIMITED and the one irreducible difference: java allocated a
    *     new object per test and this resets one object's fields. Anything that OUTLIVES a test
    *     holding the instance — a listener the test registered, a static map it put `this` in —
    *     observes the reset where java left the old object alone. Counted where the instance is used
    *     as a VALUE at all (`fresh-state(instance-escape)`, one row per suite): a `this` in
    *     RECEIVER position is not an escape and is most of them, so the count is the population that
    *     could escape rather than a proof that one did.
    *  2. '''`static` FIELDS.''' GUARD — only non-static fields are zeroed and re-initialised. Java
    *     shares a static across every construction (probed: a static counter reads 1 in the second
    *     test after the first incremented it), so resetting one would be this defect inverted.
    *  3. '''A CONSTRUCTOR THIS CANNOT REPLAY.''' GUARD + COUNT (`fresh-state(constructor)`): the
    *     lowering fires only for a class with at most ONE constructor, nilary, with no `this(…)`
    *     delegation and no ARGUMENTS passed to `super(…)` — which is every JUnit 4 test class, since
    *     `validateOnlyOneConstructor`/`validateZeroArgConstructor` is junit's own precondition
    *     (probed: junit refuses the class outright otherwise). Anything else keeps its constructor
    *     AND its field initialisers exactly as they were: hoisting the fields out from under a
    *     constructor body this cannot move would leave that body reading defaults, which is a defect
    *     the lowering would have CAUSED.
    *  4. '''A FIELD WITH NO WRITABLE DEFAULT.''' GUARD + COUNT (`fresh-state(no-default)`): a field
    *     at a class TYPE PARAMETER or an opaque type has no default this can write, so the field is
    *     left out of the ZERO step and named. Writing `null` at such a type is a compile error and
    *     inventing a value is §4.6's fabricated fact.
    *  5. '''A BASE MODULE'S TEST CLASS IN THE CHAIN.''' GUARD + COUNT (`fresh-state(base-ancestor)`)
    *     — see [[scope]].
    *  6. '''AN EXTERNAL SUPERCLASS.''' SHAPE — a class file's fields are not this program's to
    *     rebuild, and such a suite never reaches this question: the anchor is the TOPMOST
    *     program-declared class, so an external class parent stands on the one class that gains
    *     `munit.FunSuite`, and `extends munit.FunSuite with X` where `X` is a class is *class X is
    *     not a trait* — loud, at the compiler, before any of this runs.
    *  7. '''`@Ignore`.''' SHAPE — MUnit does not evaluate an ignored body, so the prologue does not
    *     run; junit likewise fires `testIgnored` without ever calling `createTest`.
    *  8. '''A VIRTUAL `@Test`.''' SHAPE — the registration stands at the top declarer and calls the
    *     member, so `bpFreshState()` dispatches to the RUNTIME class's override, which is the
    *     concrete class junit would have constructed.
    */
  private def planFreshState(program: Program)(using p: Program): Unit =
    freshSym = Map.empty; freshSuper = Map.empty; freshCall = Map.empty
    // the classes java rebuilt for one suite: the declarer and every program-declared class above
    // it, because JLS 12.5 runs each of their step 4s on every construction.
    def chainOf(s: SymId): List[Tree.ClassDef] = classDefs(s) :: classAncestry(classDefs(s))
    val declarers = testDeclarers.toList.filter(classDefs.contains).sortBy(_.raw)
    // …and the ones this run may WRITE. A chain that leaves the run's own emission is refused whole:
    // the `override` the subclass needs names a member only the base's run could have emitted.
    val (mine, borrowed) = declarers.partition(s => chainOf(s).forall(c => scope.emitsSymbol(program, c.symbol)))
    borrowed.filter(s => scope.emitsSymbol(program, s)).foreach { s =>
      val cd = classDefs(s)
      found += Finding("fresh-state(base-ancestor)", cd.origin, Fix.EngineRule, at = s, advice =
        "this suite's java superclass is a test class ANOTHER MODULE emits, so the per-test " +
        "reconstruction JUnit performs by constructing a fresh instance cannot be written here: " +
        "the `override def " + FreshStateMember + "` this module would emit names a member only the " +
        "base's own run could have put on that parent, and nothing in this model says whether it " +
        "did (`CLAUDE.md` §1.5). Every field of this suite therefore keeps the previous test's " +
        "value, exactly as it did before the lowering existed (`ENGINE-LIMITS.md` X4). Move the " +
        "base test class into this module's source set, or keep this suite on the JVM/JUnit path.")
    }
    val chains = mine.map(chainOf)
    // WHICH classes the lowering can express, and which of them hold state java rebuilt.
    val reachable = chains.flatten.map(_.symbol).distinct
    val blocked   = collection.mutable.Set.empty[SymId]
    reachable.foreach { s =>
      ctorToReplay(classDefs(s)) match
        case Left(why) =>
          blocked += s
          val cd = classDefs(s)
          found += Finding("fresh-state(constructor)", cd.origin, Fix.EngineRule, at = s, advice =
            s"JUnit constructs a fresh instance before every `@Test`, so this class's field " +
            s"initialisers and constructor body run once per test; the lowering that reproduces " +
            s"that has to REPLAY the constructor, and this one $why. Its fields and its " +
            "constructor are therefore emitted exactly as they were and keep the previous test's " +
            "state (`ENGINE-LIMITS.md` X4); subclasses of it still rebuild their OWN state. " +
            "Note junit itself refuses a test class with more than one public constructor or with " +
            "a constructor taking arguments unless a `@RunWith` runner supplies them.")
        case Right(_) => ()
    }
    // A CHAIN IS EITHER ACTIVE OR ABSENT. A suite with no instance state at all needs no member and
    // gets none — otherwise every stateless suite in the corpus would grow an empty `def` and a
    // call in every test body. Where any class in the chain does hold state, EVERY class the
    // lowering can express gets the member, including the stateless ones: the registration for a
    // virtual test stands at the top declarer, and a member it cannot name is a call that does not
    // compile.
    val inSet = chains.filter(c => c.exists(cd => !blocked(cd.symbol) && holdsState(cd)))
                      .flatten.map(_.symbol).distinct.filterNot(blocked).toSet
    // …the chain's own edges, settled BEFORE the symbols are minted, because whether a member
    // carries `override` is exactly whether it has one.
    val supers = inSet.iterator.flatMap { s =>
      classAncestry(classDefs(s)).map(_.symbol).find(inSet).map(s -> _)
    }.toMap
    val unitT = primTypes("scala.Unit")
    freshSym = inSet.iterator.map { s =>
      val fqn = p.symbolOf(s).map(_.fullName + "#" + FreshStateMember).getOrElse(FreshStateMember)
      s -> mint(FreshStateMember, fqn, Flags(isOverride = supers.contains(s)),
                TypeRepr.MethodType(Nil, unitT), owner = s)
    }.toMap
    freshSuper = supers.map((s, a) => s -> freshSym(a))
    // …and WHICH member a registration emitted in a class may call: its own, or the nearest it
    // inherits. Computed for every class in a chain, because a `@Test` may stand in one the
    // lowering skipped.
    freshCall = reachable.iterator.flatMap { s =>
      (s :: classAncestry(classDefs(s)).map(_.symbol)).find(inSet).map(m => s -> freshSym(m))
    }.toMap

  /** does java rebuild anything here — an instance field, an instance initialiser block, or a
    * constructor body with a statement in it? */
  private def holdsState(cd: Tree.ClassDef)(using p: Program): Boolean =
    cd.body.exists {
      case v: Tree.ValDef  => instanceField(v)
      case d: Tree.DefDef  => isInitBlock(d) && d.rhs.nonEmpty
      case _               => false
    } || ctorToReplay(cd).toOption.flatten.exists(d => replayedStatements(d).nonEmpty)

  /** a field the ALLOCATION zeroes and step 4 initialises — never a `static`, which java shares
    * across every construction, and never a member ANOTHER PHASE MINTED.
    *
    * The second half is `CLAUDE.md` §4.56 read at a rewrite: this lowering may reason about what
    * JAVA DECLARED, and a `ValDef` in an emitted class body is not evidence of that — a phase that
    * threads a context injects `private given sge.Sge` as one, with a symbol the frontend never
    * interned and, in that case, no NAME to assign through. Zeroed and hoisted it emitted ` = null`
    * and a `given` with no right-hand side: two syntax errors on the first port that carried both
    * phases. `Program.owns` is the structural answer — an interned java field hangs off a unit
    * through its owner chain and a minted one does not — and the flag test beside it is the same
    * question asked of a phase that mints an OWNED member, which nothing does today. */
  private def instanceField(v: Tree.ValDef)(using p: Program): Boolean =
    p.owns(v.symbol) && p.symbolOf(v.symbol).exists { s =>
      !s.flags.isStatic && !s.flags.isGiven && !s.flags.isImplicit && !s.flags.isModule &&
        s.name.nonEmpty
    }

  private def isInitBlock(d: Tree.DefDef)(using p: Program): Boolean =
    p.symbolOf(d.symbol).exists(_.name == InitBlockName)

  /** THE ONE CONSTRUCTOR THIS LOWERING MAY REPLAY, or the sentence the refusal reports.
    *
    * Junit's own precondition is exactly this shape (`validateOnlyOneConstructor` +
    * `validateZeroArgConstructor`), so the guard costs nothing on a class junit would run. What it
    * refuses is a base test case a `@RunWith` runner constructs with arguments — where replaying is
    * impossible, since the arguments are the runner's — and a `super(…)` with arguments, whose
    * parent construction the emitter carries in the `extends` clause and which a replay must not
    * duplicate. */
  private def ctorToReplay(cd: Tree.ClassDef)(using p: Program): Either[String, Option[Tree.DefDef]] =
    cd.body.collect { case d: Tree.DefDef if p.symbolOf(d.symbol).exists(_.name == "<init>") => d } match
      case Nil          => Right(scala.None)
      case one :: Nil   =>
        if one.paramss.flatten.nonEmpty then Left("takes constructor parameters")
        else
          val stats = ctorStatements(one)
          if stats.exists(isThisDelegation) then Left("delegates to another constructor with `this(…)`")
          else if stats.exists(isSuperWithArgs) then Left("passes arguments to its superclass constructor")
          else Right(Some(one))
      case many         => Left(s"declares ${many.size} constructors")

  private def ctorStatements(d: Tree.DefDef): List[Statement] = d.rhs match
    case scala.None                             => Nil
    case Some(Tree.Block(stats, expr, _, _, _)) => stats ++ (expr match
      case Tree.Literal(Constant.UnitC, _, _) => Nil
      case t                                  => List(t))
    case Some(t)                                => List(t)

  /** the constructor body MINUS the delegation java writes at its head — the parent's construction
    * is carried by the emitted `extends` clause and by the chain, never by a replayed statement. */
  private def replayedStatements(d: Tree.DefDef)(using p: Program): List[Statement] =
    ctorStatements(d).filterNot(s => isSuperCall(s) || isThisDelegation(s))

  private def isSuperCall(s: Statement): Boolean = s match
    case Tree.Apply(Tree.Select(_: Tree.Super, _, _, _), _, _, _, _) => true
    case _                                                          => false

  private def isSuperWithArgs(s: Statement): Boolean = s match
    case Tree.Apply(Tree.Select(_: Tree.Super, _, _, _), args, _, _, _) => args.nonEmpty
    case _                                                              => false

  private def isThisDelegation(s: Statement)(using p: Program): Boolean = s match
    case Tree.Apply(Tree.Select(_: Tree.This, m, _, _), _, _, _, _) =>
      p.symbolOf(m).exists(_.name == "<init>")
    case _ => false

  /** THE VALUE THE ALLOCATION LEAVES, as a term.
    *
    * The same answer `TirEmitter.defaultFor` writes for an uninitialised java field, in the IR
    * rather than in text — the two are the same fact and this one has to be a `Term` because it
    * stands on the right of an assignment. `None` where the type STATES no default this can write:
    * a class TYPE PARAMETER (`null` does not conform) or an opaque type (`null` is not its shape).
    * Refused and counted, never guessed (`CLAUDE.md` §4.6). */
  private def defaultTerm(t: TypeRepr, o: Origin)(using p: Program): Option[Term] =
    def lit(c: Constant) = Some(Tree.Literal(c, t, o))
    nameOf(t) match
      case "scala.Int"     => lit(Constant.IntC(0))
      case "scala.Short"   => lit(Constant.ShortC(0))
      case "scala.Byte"    => lit(Constant.ByteC(0))
      case "scala.Long"    => lit(Constant.LongC(0L))
      case "scala.Float"   => lit(Constant.FloatC(0f))
      case "scala.Double"  => lit(Constant.DoubleC(0d))
      case "scala.Boolean" => lit(Constant.BoolC(false))
      case "scala.Char"    => lit(Constant.CharC(' '))
      case ""              => scala.None
      case _               =>
        val head = headSymOf(t match { case TypeRepr.AppliedType(tc, _) => tc; case x => x })
        val bad  = p.symbolOf(head).exists(_.flags.isOpaque) ||
                   p.definitionOf(head).exists(_.isInstanceOf[Tree.TypeDef])
        if bad then scala.None else Some(Tree.Literal(Constant.NullC, t, o))

  /** `f = <v>` — the field named bare, as every other member reference in a body of this class is. */
  private def assignField(f: SymId, tpe: TypeRepr, v: Term, o: Origin): Term =
    Tree.Assign(Tree.Ident(f, tpe, o), v, primTypes("scala.Unit"), o)

  /** THE REWRITE: the class's own initialisation moved out of its body and into [[freshSym]]'s
    * member. Returns the class unchanged where the lowering does not reach it. */
  private def freshState(cd: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    freshSym.get(cd.symbol) match
      case scala.None  => cd
      case Some(member) =>
        val o     = cd.origin
        val unitT = primTypes("scala.Unit")
        val ctor  = ctorToReplay(cd).toOption.flatten
        val zeroes = List.newBuilder[Statement]
        val inits  = List.newBuilder[Statement]
        val kept   = List.newBuilder[Statement]
        var fields = 0
        cd.body.foreach {
          case v: Tree.ValDef if instanceField(v) =>
            fields += 1
            defaultTerm(v.tpt.tpe, v.origin) match
              case Some(d)    =>
                zeroes += assignField(v.symbol, v.tpt.tpe, d, v.origin)
                madeMutable += v.symbol
              case scala.None =>
                found += Finding("fresh-state(no-default)", v.origin, Fix.EngineRule, at = v.symbol, advice =
                  "JUnit's fresh instance leaves this field at the JVM default before every test, " +
                  "and this field's type states no default that can be WRITTEN — a class type " +
                  "parameter takes no `null` and an opaque type is not a reference. The field is " +
                  "left out of the reset and keeps the previous test's value where a test assigns " +
                  "it (`ENGINE-LIMITS.md` X4); its own initialiser, if it has one, still re-runs.")
            v.rhs.foreach { r => inits += assignField(v.symbol, v.tpt.tpe, r, v.origin); madeMutable += v.symbol }
            kept += (if v.rhs.isEmpty then v else v.copy(rhs = scala.None))
          case d: Tree.DefDef if isInitBlock(d) =>
            // JLS 12.5 step 4 is ONE sequence in TEXTUAL ORDER, and the frontend has already sorted
            // the fields and the blocks into it (`CLAUDE.md` §4.55) — so this walk preserves it by
            // walking the body, and the member itself is CONSUMED rather than left for the emitter
            // to inline a second time.
            d.rhs.foreach(inits += _)
          case d: Tree.DefDef if ctor.exists(_.symbol == d.symbol) && replayedStatements(d).nonEmpty =>
            inits ++= replayedStatements(d)
            kept += d.copy(rhs = Some(Tree.Block(
              ctorStatements(d).filter(isSuperCall), unitLit(o), unitT, o)))
          case other => kept += other
        }
        val sup = freshSuper.get(cd.symbol).map { a =>
          Tree.Apply(Tree.Select(Tree.Super(cd.symbol, TypeRepr.NoType, o), a, TypeRepr.NoType, o),
                     Nil, a, unitT, o)
        }
        val stats = zeroes.result() ++ sup.toList ++ inits.result()
        val rhs   = Tree.Block(stats, unitLit(o), unitT, o)
        suitesRebuilt += 1
        // difference 1 — the one thing a reset cannot be: a NEW OBJECT.
        val escapes = instanceEscapes(cd)
        if escapes > 0 then
          found += Finding("fresh-state(instance-escape)", cd.origin, Fix.EngineRule, at = cd.symbol, advice =
            s"$escapes use(s) of this suite's own instance AS A VALUE (a `this` that is not the " +
            "receiver of a selection). JUnit allocated a NEW test object for every `@Test` and " +
            "this lowering resets ONE object's fields, so anything that outlives a test holding " +
            "this instance — a listener it registered, a static collection it was put in — sees " +
            "the reset where java saw the old object untouched. The field state itself is " +
            "reproduced exactly; object identity is not (`ENGINE-LIMITS.md` X4).")
        val above = classAncestry(cd).find(a => freshSym.contains(a.symbol))
        record(Decision(
          kind       = Decision.Kind.RebuiltPerTest,
          subject    = cd.symbol,
          subjectFqn = p.symbolOf(cd.symbol).map(_.fullName).getOrElse(""),
          detail = Map(
            "member" -> FreshStateMember,
            "fields" -> fields.toString,
            "ctor"   -> (if ctor.exists(d => replayedStatements(d).nonEmpty) then "replayed" else "empty"),
            "chains" -> above.flatMap(a => p.symbolOf(a.symbol).map(_.fullName)).getOrElse(""),
            "why"    -> ("JUnit constructs a FRESH instance of the test class before every @Test " +
              "(BlockJUnit4ClassRunner.createTest), so java ran this class's field initialisers, " +
              "its instance initialiser blocks and its constructor body once per test; MUnit has " +
              "ONE suite instance and would run them once. They are hoisted here and every test " +
              "body calls this member first"),
          ),
          reason = Reason.Universal("test-framework/fresh-instance"),
          origin = cd.origin,
        ))
        cd.copy(body = kept.result() :+
          Tree.DefDef(member, List(Nil), TypeTree(unitT, o), Some(rhs), o))

  private def unitLit(o: Origin): Term = Tree.Literal(Constant.UnitC, primTypes("scala.Unit"), o)

  /** the instance used as a VALUE, which is the only part of difference 1 anything can see. A `this`
    * that is the QUALIFIER of a selection is a field or a method access and is not an escape — it is
    * also most of them, so the two are counted as a difference of two standard walks rather than by
    * a predicate that would have to know every shape a receiver can take. */
  private def instanceEscapes(cd: Tree.ClassDef)(using p: Program): Int =
    def all(n: Int, t: Term)   = t match { case _: Tree.This => n + 1; case _ => n }
    def recvs(n: Int, t: Term) = t match
      case Tree.Select(_: Tree.This, _, _, _) => n + 1
      case _                                  => n
    StandardTraversal.scanClassDef(cd, 0)(all) - StandardTraversal.scanClassDef(cd, 0)(recvs)

  override def run(program: Program): Program =
    nextId = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    added.clear(); consumed.clear(); consumedTests.clear(); found.clear(); madeMutable.clear()
    suitesConverted = 0; testsConverted = 0; rulesConverted = 0; suitesRebuilt = 0
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
    // …and the three members the ExpectedException translation selects. Minted unqualified for the
    // reason the widening members above are: a `Select`'s member renders by SIMPLE name against
    // whatever the receiver's own type declares — `org.hamcrest.Matcher#matches`,
    // `java.lang.Throwable#getMessage`, `java.lang.String#contains`.
    matchesSym    = mint("matches", "matches")
    getMessageSym = mint("getMessage", "getMessage")
    containsSym   = mint("contains", "contains")
    // …and the rule's own state machine. `Nil` is QUALIFIED because it is an Ident rather than a
    // selection (§6 — this backend emits no imports); the rest are selections on a receiver whose
    // own type declares them, and the two operators carry the emitter's infix tag.
    nilSym      = mint("Nil", "scala.collection.immutable.Nil")
    appendSym   = mint(":+", "scala.<op>#:+")
    forallSym   = mint("forall", "forall")
    isEmptySym  = mint("isEmpty", "isEmpty")
    nonEmptySym = mint("nonEmpty", "nonEmpty")
    applySym    = mint("apply", "apply")
    andSym      = mint("&&", "scala.<op>#&&")
    plusSym     = mint("+", "scala.<op>#+")
    nextTmp = 0
    val byName = program.symbols.all.groupBy(_.fullName)
    def prim(fqn: String): TypeRepr =
      TypeRepr.TypeRef(TypeRepr.NoPrefix,
        byName.get(fqn).flatMap(_.headOption).map(_.id)
          .getOrElse(mint(fqn.substring(fqn.lastIndexOf('.') + 1), fqn)))
    primTypes = (NumericRank.keySet + "scala.Unit" + "scala.Boolean").map(t => t -> prim(t)).toMap
    objType = prim("java.lang.Object")
    throwableType = prim("java.lang.Throwable")
    predType = TypeRepr.MethodType(List("bpEx" -> throwableType), primTypes("scala.Boolean"))
    predListType = TypeRepr.AppliedType(
      TypeRepr.TypeRef(TypeRepr.NoPrefix, mint("List", "scala.collection.immutable.List")),
      List(predType))
    unitSym = headSymOf(primTypes("scala.Unit"))

    val symbols0 = SymbolTable(program.symbols.all ++ added)
    given Program = program.rebuilt(symbols = symbols0)
    // WHERE THE SUITE PARENT GOES and WHICH `@Test`s stay `def`s — both facts about the whole
    // program's class graph rather than about the class `convert` is handed, so both are settled
    // before either walk starts (see the fields' doc).
    planHierarchy(program)
    // …and WHOSE INSTANCE STATE JUNIT REBUILT PER TEST, which is a fact about the same class graph
    // and is settled here for the same reason (see [[planFreshState]]).
    planFreshState(program)
    survey(program)
    // TWO WALKS, and the split is the whole of what is gated on `@Test`.
    //
    // The ASSERTION rewrite runs over every unit: an assertion is an assertion wherever it is
    // written, and a test HELPER declares no `@Test` — that is what makes it a helper — while
    // being where a suite's assertions are most often centralised. Scoped to converted classes it
    // never VISITED those calls, so they emitted as `org.junit.Assert.*` into a port whose whole
    // point is to leave the JVM, and the phase's own counters said nothing because nothing was
    // counted.
    //
    // It was scoped for a stated reason that no longer holds: rewriting program-wide once produced
    // `Not found: assertTrue` in helper classes, because the members came from the suite's base
    // class and a helper has none. Assertions have been emitted fully qualified to the
    // `munit.Assertions` OBJECT since — see [[MunitAssertions]], which is the fix for exactly that
    // failure — so there is no scope in which they do not resolve, and the gate was a leftover.
    //
    // Running it ONCE per unit rather than once per converted class also removes a double walk:
    // `mapClassDef` descends into nested classes, so an outer suite re-walked its already-converted
    // nested one. Idempotent for the rewrites, NOT for the findings — an unmapped member inside a
    // nested suite was reported twice and the untranslated-construct headline over-counted.
    val rewritten = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    // …and the CONVERSION — the `munit.FunSuite` parent, the `test(name){body}` registrations, the
    // lifecycle inlining and the `beforeAll`/`afterAll` overrides — stays gated on the class
    // declaring a `@Test`, because that is what a suite IS. A helper that gained the parent would
    // register zero tests and claim to be one.
    val units = rewritten.map(convert)
    // `convert` mints more (the lifecycle overrides), so the table is rebuilt AFTER the walk.
    // …and a java `final` instance field the per-test reconstruction ASSIGNS has to be a `var`:
    // java's `final` was per-CONSTRUCTION and this suite is constructed once. Narrowed to the
    // fields really written (`CLAUDE.md` §4.55's own rule for a promotion's mutability).
    val symbols0m = madeMutable.foldLeft(SymbolTable(program.symbols.all ++ added)) { (t, id) =>
      t.get(id) match
        case scala.None => t
        case Some(s)    => t.updated(s.copy(flags = s.flags.copy(isMutable = true, isFinal = false)))
    }
    val symbols = (consumed ++ consumedTests).foldLeft(symbols0m) { (t, id) =>
      val gone = if consumedTests(id) then ConsumedAnns + TestAnn else ConsumedAnns
      t.get(id) match
        case scala.None => t
        case Some(s)    => t.updated(s.copy(
          annotations        = s.annotations.filterNot(a => gone(nameOf(a.tpe))),
          // a consumed annotation the FRONTEND could not carry (`@Ignore("why")` on a class, whose
          // arguments need an expression translator) was handled all the same, so it must stop
          // being reported as an omission.
          droppedAnnotations = s.droppedAnnotations.filterNot(ConsumedAnns)))
    }
    report()
    program.rebuilt(units, symbols)

  /** …`owner` is `SymId.None` for every name this phase mints EXCEPT the per-test reconstruction's,
    * and that exception is the point: `Program.owns` is what decides whether a member is this
    * program's, so an unowned one is published by `PortMap` at the PACKAGE — the first run emitted a
    * `com.badlogic.gdx…utils.bpFreshState()` row, a top-level name no file declares. A member added
    * to an emitted class is emitted surface and belongs to that class. */
  private def mint(nm: String, full: String, flags: Flags = Flags(), info: TypeRepr = TypeRepr.NoType,
                   owner: SymId = SymId.None): SymId =
    val id = SymId(nextId); nextId += 1
    added += Symbol(id, nm, full, flags, owner, info)
    id

  private def report(): Unit =
    println(s"[$name] converted $suitesConverted suite(s), $testsConverted test(s); " +
            s"UNTRANSLATED test-framework constructs: ${found.size}")
    if suitesRebuilt > 0 then
      // WHAT THE RECONSTRUCTION IS, stated where it is counted. A conversion count says nothing
      // about the one difference between the two frameworks that RUNS the tests differently.
      println(s"  fresh instance: $suitesRebuilt class(es) rebuild their instance state before " +
              "every test — JUnit constructs the test object per @Test and MUnit has ONE suite " +
              s"instance, so each class's field initialisers, instance initialiser blocks and " +
              s"constructor body are hoisted into `$FreshStateMember`")
    if rulesConverted > 0 then
      // WHAT THE CONVERSIONS ARE, stated where they are counted (`CLAUDE.md` §3). The residual
      // difference the `intercept` shape had to declare here — junit catches `Throwable`, MUnit's
      // `intercept` catches `NonFatal` — is GONE with that shape: the emitted `catch` is junit's
      // own. What remains is the enumeration in `expectedException`'s doc, every member of which is
      // a guard the refusal lane reports or a property of the shape.
      println(s"  ExpectedException @Rule: $rulesConverted `thrown.expect(…)` site(s) MODELLED — " +
              "the rule's matcher list armed in place and one try/catch over the whole test, which " +
              "is junit's own `ExpectedExceptionStatement` and reaches a site in a loop body")
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
          // `s.id` and not `o`: a DROPPED annotation is reported from the SYMBOL, whose `origin`
          // defaults to `Origin.synthetic`, so the path locates nothing and the D2 filter has to
          // ask the owner chain instead (see `Finding.at`).
          found += Finding(fqn, o, fix, advice, s.id)
      }
    }
    // JUnit 3 has no annotations at all: a suite is a `junit.framework.TestCase` subclass whose
    // test methods are named `testXxx`. Nothing above can see it, so the PARENT is the signal.
    def scanParents(cd: Tree.ClassDef): Unit =
      cd.parents.foreach {
        case tt: TypeTree if nameOf(tt.tpe) == "junit.framework.TestCase" =>
          found += Finding("junit.framework.TestCase", cd.origin, Fix.EngineRule, at = cd.symbol, advice =
            "a JUnit 3 suite declares its tests by NAMING them `testXxx` on a `TestCase` subclass; " +
            "this phase keys off `@Test` and converts nothing, so the class emits as a plain class " +
            "and registers zero tests.")
        case _ => ()
      }
    // `allClassDefs`, not a `cd.body` recursion: a class body is the type's MEMBERS, one node short
    // of java — a method-LOCAL class (`JS-C30`) stands in a member's block, and a JUnit-3 suite
    // declared as one would be reported by nothing.
    program.units.foreach(u => StandardTraversal.allClassDefs(u)(using program).foreach(scanParents))
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
          program.usages(id).foreach(u => found += Finding(what, u.site.origin, Fix.EngineRule, at = u.enclosing, advice =
            "Hamcrest is a second assertion vocabulary (`assertThat(x, is(equalTo(y)))`); this " +
            "phase maps JUnit's `Assert` members only, and MUnit has no matcher algebra to map a " +
            "matcher ONTO. OUT OF SCOPE by decision, reported so it is not mistaken for coverage: " +
            "either keep this suite on the JVM/JUnit path with hamcrest on the test classpath, or " +
            "translate each matcher into the assertion it means."))
      }
    }

  private def adviceFor(fqn: String): (Fix, String) = fqn match
    case "org.junit.Rule" | "org.junit.ClassRule" => (Fix.EngineRule,
      "a JUnit @Rule wraps every test in an arbitrary Statement (TemporaryFolder, Timeout, …); " +
      "there is no shape to derive it from. Replace it with an explicit fixture in the port's " +
      "hand-written `src/`, or keep this suite on the JVM/JUnit path. The rule FIELD is emitted as " +
      "an ordinary field and NEVER APPLIED. ONE rule class is the exception: an " +
      "`org.junit.rules.ExpectedException` is MODELLED — its `expect`/`expectMessage` calls become " +
      "armings of the matcher list junit itself accumulates, wherever they stand, and the test is " +
      "wrapped in junit's own `try`/`catch` — so the FIELD is dead rather than the assertion. Every " +
      "site this phase declined is reported separately, one row per site naming its guard.")
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
    case Tree.Select(recv, m, _, o) =>
      assertClassOf(recv) match
        case scala.None      => t
        case Some(assertCls) =>
          val nm = p.symbolOf(m).map(_.name).getOrElse("")
          munitCall(nm, t.args, o) match
            case Right(rewritten) => rewritten
            case Left(why)        =>
              // the finding names the receiver the CALL had, not a canonical one: reported under a
              // class the source never mentions, an agent cannot find the site. And where the
              // refusal has a REASON beyond "no mapping exists" — a shape this phase understands
              // and declines — that reason is the whole of what the reader needs, so `munitCall`
              // returns it rather than leaving it to be re-derived from the argument list here.
              found += Finding(assertCls + "." + nm, o, Fix.EngineRule,
                s"no MUnit counterpart is known for this `$nm` overload (${t.args.size} argument(s)), so " +
                s"the call is left on $assertCls — which compiles only with JUnit on the classpath and " +
                "cannot run on Scala.js / Native. Add the mapping to TestFrameworkTransform.munitCall." +
                (if why.isEmpty then "" else s" WHY THIS ONE: $why"))
              t
    case _ => t

  /** java's `(message?, expected, actual, delta?)` → MUnit's `(obtained, expected, delta?, clue?)`.
    *
    * `hasMsg` is decided STRUCTURALLY: a leading `String` is junit's message exactly when the call
    * carries more arguments than the member's minimal arity. That separates every junit overload
    * that exists — `assertEquals(String, Object, Object)` from `assertEquals(double, double,
    * double)`, and `assertEquals(a, b)` on two Strings from either — without naming one. */
  private def munitCall(nm: String, args: List[Term], o: Origin)(using p: Program): Either[String, Term] =
    MinArity.get(nm).toRight("").flatMap { min =>
      val hasMsg = args.sizeIs > min && args.headOption.exists(a => nameOf(a.tpe) == "java.lang.String")
      val clue   = if hasMsg then List(args.head) else Nil
      val rest   = if hasMsg then args.tail else args
      (nm, rest) match
        // junit's `fail()` has no message; MUnit's `fail` requires one.
        case ("fail", Nil) =>
          Right(call("fail", List(clue.headOption.getOrElse(constTerm(Constant.StringC("failed"), "java.lang.String", o))), o))
        case ("assertTrue", List(c))  => Right(call("assert", c :: clue, o))
        // `assert(!c)` would need an operator node for one gain in readability; comparing against
        // the literal is the same assertion and reports the same way.
        case ("assertFalse", List(c)) => Right(call("assertEquals", c :: bool(false, o) :: clue, o))
        case ("assertNull", List(x))    => Right(call("assertEquals", x :: nul(o) :: clue, o))
        case ("assertNotNull", List(x)) => Right(call("assertNotEquals", x :: nul(o) :: clue, o))
        // REFERENCE identity — scala's `==` is java's `equals` (CLAUDE.md §4.4), so `assertEquals`
        // here would silently weaken every `assertSame` into an `assertEquals`.
        case ("assertSame", List(e, a))    => Right(call("assert", infix(a, eqSym, e, o) :: clue, o))
        case ("assertNotSame", List(e, a)) => Right(call("assert", infix(a, neSym, e, o) :: clue, o))
        case ("assertEquals" | "assertNotEquals", List(e, a)) =>
          val (a2, e2) = promote(a, e)
          val m = if nm == "assertEquals" then "assertEquals" else "assertNotEquals"
          Right(if widened(a2, e2) then callAt(m, objType, a2 :: e2 :: clue, o)
                else call(m, a2 :: e2 :: clue, o))
        case ("assertEquals", List(e, a, delta)) =>
          Right(call(deltaMember(List(e, a, delta)), a :: e :: delta :: clue, o))
        case ("assertArrayEquals", List(e, a)) =>
          // `guarded` for the same reason as in `widen`: an operand that renders infix or as a
          // control-flow expression would bind `.toSeq` to its last branch.
          Right(call("assertEquals",
            select(guarded(a), toSeqSym, o) :: select(guarded(e), toSeqSym, o) :: clue, o))
        case ("assertArrayEquals", List(e, a, delta)) => arrayWithDelta(e, a, delta, clue, o).toRight("")
        // JUnit 4.13's `assertThrows(Class<T>, ThrowingRunnable)` asserts exactly what MUnit's
        // `intercept[T] { … }` asserts and returns the throwable exactly as it does — the same
        // construction [[testCase]] already builds for `@Test(expected = …)`, reached from the
        // assertion side instead of the annotation side. TWO restrictions, both of them refusals
        // rather than approximations:
        //
        //  - the runnable must be a LAMBDA. `intercept[E] { r }` EVALUATES `r` and never runs it,
        //    so a `ThrowingRunnable` value or a method reference would turn the assertion into a
        //    test of whether CONSTRUCTING the runnable threw — passing while checking nothing,
        //    which is the shape this phase exists to prevent. Deriving the invocation would mean
        //    naming `ThrowingRunnable#run`, a member no MUnit contract mentions;
        //  - no leading `String message`. `intercept[T](body: => Any)` has no clue slot, so
        //    junit's message has nowhere to go, and dropping a diagnostic the author wrote with
        //    nothing counting the loss is worse than leaving the call where `PortabilityCheck`
        //    already counts it.
        case ("assertThrows", List(Tree.Literal(Constant.ClassOfC(ex), _, _), lam: Tree.Lambda))
            if clue.isEmpty && lam.params.isEmpty =>
          Right(intercept(munitSyms("intercept"), ex, lam.body, o))
        // …and the two shapes this phase DOES understand and declines, each with the reason.
        case ("assertThrows", List(Tree.Literal(Constant.ClassOfC(_), _, _), _: Tree.Lambda))
            if clue.nonEmpty =>
          Left("MUnit's `intercept[T](body)` has no clue slot, so junit's leading `String message` " +
               "has nowhere to go. The 2-argument form IS translated; drop the message, or keep " +
               "this suite on the JVM/JUnit path.")
        case ("assertThrows", List(Tree.Literal(Constant.ClassOfC(_), _, _), _)) =>
          Left("the runnable must be a NO-ARGUMENT LAMBDA. `intercept[E] { r }` EVALUATES a " +
               "`ThrowingRunnable` value rather than running it, so the assertion would test " +
               "whether CONSTRUCTING it threw — passing while checking nothing. Inline the " +
               "runnable as `() -> …`.")
        case _ => Left("")
    }

  /** `intercept[E] { body }` — MUnit's assertion that the body throws.
    *
    * The SYMBOL is a parameter because there are two spellings and the difference is scope, not
    * taste. [[testCase]] builds this only inside a class that gains [[suite]] as a PARENT, where
    * `intercept` is inherited and in scope; [[munitCall]] builds it wherever an assertion is
    * written — a java `static` helper's companion object included, which extends nothing — so that
    * one goes through the `munit.Assertions` OBJECT for the reason [[MunitAssertions]] states. */
  private def intercept(sym: SymId, ex: TypeRepr, body: Term, o: Origin): Term =
    val fn = Tree.TypeApply(Tree.Ident(sym, TypeRepr.NoType, o), List(TypeTree(ex, o)),
                            TypeRepr.NoType, o)
    Tree.Apply(fn, List(body), sym, TypeRepr.NoType, o)

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
        promotedHere = true
        (widen(x, tx, to, p), widen(y, ty, to, p))
      case _ => (x, y)

  /** JS-E07's CITATION — the catalog's third discharge surface (`DESIGN.md` §2.8).
    *
    * A phase does not walk one node kind, so an obligation wrapper is the wrong shape for it; what
    * a phase owes is a row per DECLARATION it decided about. This hook is where that declaration is
    * finally in hand: the traversal is bottom-up, so a `DefDef` is reached after every `Apply` in
    * its body, and [[promotedHere]] is exactly "java's binary numeric promotion had to be
    * re-applied somewhere in this member".
    *
    * The tree is returned UNCHANGED. This hook exists to observe, and an observation that moved a
    * byte of output would be a coverage mechanism that changes what it measures. */
  override def transformDefDef(t: Tree.DefDef)(using p: Program): Tree.DefDef =
    citeIfPromoted(t.symbol)
    t

  /** …AND A FIELD INITIALISER IS NOT INSIDE A `DefDef`.
    *
    * The hook above reads a flag and clears it, which is only sound if EVERY declaration whose body
    * can hold a widening clears it. A field's initialiser can — `Runnable check = () ->
    * Assert.assertEquals(1, 2L)` puts the promotion inside a `ValDef`'s right-hand side — and the
    * `ValDef` did not clear, so the flag survived to the NEXT `DefDef` the traversal reached and the
    * citation named it. In the fixture that is the class's own `<init>`, a member the phase never
    * touched; with the field last in a class body it is a member of the NEXT class.
    *
    * A citation is a claim about which declaration a phase decided at. One that names the wrong
    * member sends an investigation to code with nothing in it, which is the single thing provenance
    * may not do (`CLAUDE.md` §4.575) — and nothing else can see it: the emitted text is identical,
    * every check count is identical, and `catalog(consulted)` counts the row either way. */
  override def transformValDef(t: Tree.ValDef)(using p: Program): Tree.ValDef =
    citeIfPromoted(t.symbol)
    t

  /** …and the CLASS BOUNDARY, which is the backstop rather than a third case.
    *
    * Whatever holds a widening that neither hook above owns — a class-body term some future lowering
    * introduces — belongs to the type it was written in and to no member of the next one. Citing the
    * class is honest ("this phase decided something in here") where leaking is not; and because both
    * hooks above run first, this fires only for what they do not cover. */
  override def transformClassDef(t: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    citeIfPromoted(t.symbol)
    t

  private def citeIfPromoted(sym: SymId)(using p: Program): Unit =
    if promotedHere then
      cite(balticporter.catalog.JS.E(7), p.symbolOf(sym).map(_.fullName).getOrElse(sym.toString))
      promotedHere = false

  private def widen(t: Term, from: String, to: String, p: Program): Term =
    if from == to then t else select(guarded(t)(using p), widenSyms(to), t.origin, primTypes(to))

  /** JAVA'S OTHER WIDENING, re-applied — the REFERENCE half of what [[promote]] does for numbers.
    *
    * Java has one `assertEquals(Object, Object)` and every reference pair went through it, WIDENED
    * at the call; MUnit's `assertEquals[A, B]` infers each operand independently and then demands a
    * `Compare[A, B]`, which needs the two types to relate. Two invariant `java.util.List`s at
    * different element types do not — `Can't compare these two types: java.util.List[Object] /
    * java.util.List[String]` (liqp `RenderSettingsTest`), the same fact as the 26 `Long / Int`
    * errors at the other overload.
    *
    * MUnit's constraint is a STRICTLY STRONGER check than java's, so the rule is to KEEP IT WHERE
    * IT IS A CHECK and write java's widening down everywhere else. It is a check exactly when the
    * two static types are the SAME and that type is not a ROOT — `Compare` is reflexive, and
    * `[Object, Object]` on a pair MUnit can already compare would throw the better diagnostic
    * away.
    *
    * **A ROOT on either side is the case that reads as "already relates" and is not.** MUnit's
    * `Compare[A, Object]` resolves whatever `A` is, so at a root operand the constraint is ALREADY
    * VACUOUS and the widening costs nothing — and it is the one place the TIR's own answer cannot
    * be trusted. An earlier phase's boundary bridge types its wrap as the FORMAL it was inserted
    * for, and java's `assertEquals(Object, Object)` formal is `java.lang.Object`: at liqp's
    * `RenderSettingsTest` BOTH operands are `JavaCollections.toJava(…)` nodes carrying
    * `java.lang.Object`, while the text they emit is `java.util.List[Object]` and
    * `java.util.List[String]`. Read as "same type, so MUnit can compare them" that pair declines
    * the widening and fails to compile; read as "a root, so there was never a check here" it takes
    * it. Note the phase concludes nothing about ANOTHER phase's rewrite — it reads its own operand
    * types and treats a root as the absence of information it is.
    *
    * Two guards, both refusals rather than approximations:
    *
    *   - `NoType` on either side is "the frontend could not say", and a rewrite is not made on a
    *     guess;
    *   - a PRIMITIVE on either side belongs to [[promote]], and widening a boxed pair to `Object`
    *     would silently change the comparison: scala's `==` on two boxed numbers is NUMERIC
    *     (`BoxesRunTime.equals`), where java's `Integer.equals(Long)` is `false`. That is a §4.4
    *     divergence and this rewrite must not open it.
    *
    * The widening is written as the call's TYPE ARGUMENTS rather than as a cast per operand: it is
    * one construct instead of two, it is exactly what java's signature said, and it leaves the
    * operands' own emitted text alone. A type-PARAMETER operand takes it too and is safe for the
    * reason `ENGINE-LIMITS.md` G24 measures — the port emits java's vacuous `T <: java.lang.Object`
    * bound literally, so such an operand conforms; the day G24's bound comes off, it stops. */
  private def widened(x: Term, y: Term)(using p: Program): Boolean =
    val (sx, sy) = (shape(x.tpe), shape(y.tpe))
    sx.nonEmpty && sy.nonEmpty &&
      !isValueType(x.tpe) && !isValueType(y.tpe) &&
      (sx != sy || Roots(sx))

  private def isValueType(t: TypeRepr)(using p: Program): Boolean =
    val n = nameOf(t)
    NumericRank.contains(n) || n == "scala.Boolean" || n == "scala.Unit"

  /** A type's full structural name, type ARGUMENTS included.
    *
    * [[nameOf]] deliberately answers the type CONSTRUCTOR, which is right everywhere else in this
    * phase and is exactly wrong here: the pair [[widened]] exists for differs ONLY in its
    * arguments, and read through `nameOf` the two sides compare equal. */
  private def shape(t: TypeRepr)(using p: Program): String = t match
    case TypeRepr.TypeRef(_, s)       => p.symbolOf(s).map(_.fullName).getOrElse("")
    case TypeRepr.AppliedType(tc, as) =>
      val head = shape(tc)
      if head.isEmpty then "" else head + as.map(shape).mkString("[", ",", "]")
    case TypeRepr.TypeBounds(_, _)    => "?"
    case _                            => ""

  /** `assertEquals[T, T](…)` — the call at an EXPLICIT type argument on both operands. */
  private def callAt(member: String, targ: TypeRepr, args: List[Term], o: Origin): Term =
    val s  = munitSyms(member)
    val fn = Tree.TypeApply(Tree.Ident(s, TypeRepr.NoType, o),
                            List(TypeTree(targ, o), TypeTree(targ, o)), TypeRepr.NoType, o)
    Tree.Apply(fn, args, s, TypeRepr.NoType, o)

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

  /** WHICH of [[AssertClasses]] a call's receiver names, or `None` for every other receiver. The
    * name is returned rather than a `Boolean` because the refusal path reports it. */
  private def assertClassOf(recv: Term)(using p: Program): Option[String] =
    val sym = recv match
      case Tree.Ident(s, _, _)     => Some(s)
      case Tree.Select(_, s, _, _) => Some(s)
      case _                       => scala.None
    sym.flatMap(p.symbolOf).map(_.fullName).filter(AssertClasses)

  /** A class is a SUITE when it declares at least one `@Test` member. Nested classes are converted
    * too — libGDX nests helper suites — so the walk is explicit rather than top-level only.
    *
    * This gate covers the CONVERSION alone: the parent, the registrations, the lifecycle inlining.
    * The assertion rewrite ran over the whole unit before this (see [[run]]), because a class with
    * no `@Test` is a test HELPER, not a non-test — and a helper is where a suite's assertions are
    * most often centralised. */
  private def convert(cd: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    val nested = cd.body.map {
      case c: Tree.ClassDef => convert(c)
      case other            => other
    }
    // THE PER-TEST RECONSTRUCTION, which MOVES this class's own initialisation into a member of its
    // own. It touches only the classes [[planFreshState]] admitted — which includes ancestors that
    // declare no `@Test` of their own, since java rebuilt their state too — and returns the class
    // untouched everywhere else.
    //
    // TWO CLASSES, AND THAT IS NOT A CONVENIENCE. `cd1` is what every ANALYSIS below reads and `cd2`
    // is what is emitted: the reconstruction writes ASSIGNMENTS to this class's fields, and a scan
    // that counts REFERENCES to a field would count them. `ExpectedException`'s `arming-outside-test`
    // guard is exactly such a scan — every reference to the rule field, less the ones inside `@Test`
    // bodies — so read through `cd2` it saw the synthesised `thrown = null` as an arming made in a
    // helper and refused the whole class, silently, on eleven of this phase's own fixtures.
    val cd1 = cd.copy(body = nested)
    val cd2 = freshState(cd1)
    // THE SUITE PARENT IS THE ANCHOR'S, NOT THE DECLARER'S (see [[suiteAnchors]]). A class that
    // anchors a hierarchy but declares no `@Test` of its own gets the parent and nothing else —
    // there is no registration to make and no lifecycle to inline, and every member it declares is
    // already whatever java wrote.
    def withSuite(c: Tree.ClassDef): Tree.ClassDef =
      if !suiteAnchors(cd.symbol) then c
      else c.copy(parents = TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, suiteSym), cd.origin) :: c.parents)
    if !nested.exists(isAnnotated(_, TestAnn)) then withSuite(cd2)
    else
      // NOTE there is no assertion walk here: it has already run over this whole unit (see `run`).
      // What is left is the SUITE conversion, which is what `@Test` gates.
      //
      // JUnit runs `@Before` before EVERY test, on a FRESH instance of the class. MUnit has
      // neither: one suite instance, and no such annotation — so the emitted `@Before def setUp`
      // was never called and `SortTest`'s `sortInstance` was null in all 19 of its tests. Nothing
      // failed to compile; the suite failed at run time, which is the only place this shows.
      //
      // Call it at the head of each test body. That reproduces java's per-test setup exactly
      // wherever setup ASSIGNS the fields it needs, which is the shape `@Before` exists for. It
      // does NOT reproduce JUnit's fresh instance, so a field carrying state through its own
      // INITIALISER rather than through setup still leaks between tests — recorded, not hidden.
      val setups = cd2.body.collect {
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
      val teardowns = cd2.body.collect {
        case d: Tree.DefDef if isAnnotated(d, AfterAnn) => d.symbol
      }
      // `@BeforeClass` / `@AfterClass` are JUnit's ONE-TIME hooks; MUnit spells them `beforeAll` /
      // `afterAll` on the suite. They are java `static`, so they emit into the companion object and
      // the override calls them through it (`Suite.setUpClass()`).
      val classSetups = cd2.body.collect {
        case d: Tree.DefDef if isAnnotated(d, BeforeClassAnn) => d.symbol
      }
      val classTeardowns = cd2.body.collect {
        case d: Tree.DefDef if isAnnotated(d, AfterClassAnn) => d.symbol
      }
      consumed ++= setups ++ teardowns ++ classSetups ++ classTeardowns
      // …and the `ExpectedException` @Rule FIELDS this class declares. Matched on the field's own
      // TYPE and not on the annotation alone: `@Rule` is every rule class junit has, and this phase
      // translates exactly one of them.
      val ruleFields0 = cd2.body.collect {
        case v: Tree.ValDef
            if hasAnn(v.symbol, RuleAnn) && nameOf(v.tpt.tpe) == ExpectedExceptionCls => v.symbol
      }.toSet
      // …and the rule is only MODELLABLE where every arming is inside the test it governs, because
      // the accumulator [[expectedException]] arms is a LOCAL of that test's own frame.
      //
      // The per-test guards ask that of the body they are handed and CANNOT ask it here: a helper
      // method, a field initialiser or a nested class arming the rule is a reference no test body
      // contains, so every test in the class reads as *never touches the rule* and is left alone —
      // silently, and with the arming still standing in emitted code that compiles and does nothing.
      // Refusing the CLASS rather than the sites is the conservative arm and is not tidiness: a
      // test that arms the rule ITSELF and also calls such a helper would be modelled with FEWER
      // matchers than java accumulated, which is the direction that PASSES where java FAILED.
      //
      // Counted as a DIFFERENCE of two standard walks — every reference the class holds, less every
      // reference its own `@Test` bodies hold — so growing a node kind cannot open a hole here
      // (`CLAUDE.md` §3). No corpus source declares the shape, which is exactly why an unstated
      // exclusion would never be found.
      val ruleFields =
        if ruleFields0.isEmpty then ruleFields0
        else
          def refs(n: Int, t: Term) = if isRuleRef(t, ruleFields0) then n + 1 else n
          // `cd1`: the class BEFORE the per-test reconstruction, see the comment at its binding.
          val all   = StandardTraversal.scanClassDef(cd1, 0)(refs)
          val mine  = cd1.body.collect { case d: Tree.DefDef if isAnnotated(d, TestAnn) => d.rhs }
                        .flatten.map(b => StandardTraversal.scanTerm(b, 0)(refs)).sum
          if all <= mine then ruleFields0
          else
            found += Finding(s"$ExpectedExceptionCls(arming-outside-test)", cd.origin, Fix.EngineRule, at = cd.symbol, advice =
              s"${all - mine} reference(s) to this suite's `ExpectedException` rule field stand " +
              "OUTSIDE its own `@Test` methods — in a helper, a field initialiser or a nested " +
              "class. The rule's matcher list is modelled as a local of the test it governs, so an " +
              "arming made anywhere else is not in that frame and would leave the test requiring " +
              "FEWER matchers than java accumulated. Every test in this suite is left alone rather " +
              "than modelled from an incomplete expectation; inline the arming into each test, or " +
              "keep this suite on the JVM/JUnit path.")
            Set.empty[SymId]
      // …and `@ClassRule` is REPORTED, never quietly taken for `@Rule`. A method rule wraps each
      // test and a CLASS rule wraps the whole class run, so the region an `expect` arms is a
      // different one and `intercept` in a test body is not its image. Nothing in the corpus writes
      // the shape, which is exactly why an unstated exclusion here would never be found.
      cd2.body.foreach {
        case v: Tree.ValDef
            if hasAnn(v.symbol, ClassRuleAnn) && nameOf(v.tpt.tpe) == ExpectedExceptionCls =>
          found += Finding(s"$ExpectedExceptionCls(class-rule)", v.origin, Fix.EngineRule, at = v.symbol, advice =
            "an `ExpectedException` declared as a `@ClassRule` wraps the WHOLE CLASS RUN, not each " +
            "test, so the region an `expect` call arms is not the one an `intercept` in a test body " +
            "wraps. The `@Rule` form IS translated; this one is left alone and the field is never " +
            "applied.")
        case _ => ()
      }
      // `@Ignore` on the CLASS disables every test it declares.
      val allIgnored = hasAnn(cd.symbol, IgnoreAnn)
      if allIgnored then consumed += cd.symbol
      // A VIRTUAL `@Test` keeps its `def` — java's own override relation reaches it, and a statement
      // overrides nothing (see [[virtualTests]]). The TOP declarer additionally emits the one
      // registration, whose body CALLS the method, so every concrete subclass runs its own override
      // exactly as JUnit does. Everything else takes the statement form this phase has always used.
      val body = cd2.body.flatMap {
        case d: Tree.DefDef if isAnnotated(d, TestAnn) && virtualTests(d.symbol) =>
          // …and its `@Test` goes, which no other converted test needs: everywhere else the METHOD
          // disappears and takes the annotation with it, so `ConsumedAnns` never had to name it.
          // Here the `def` survives, and a junit annotation left on an emitted scala method is both
          // meaningless and COUNTED — `junit_residue` reads it as a suite that did not convert.
          consumedTests += d.symbol
          if virtualRoots(d.symbol)
          then List(d, testCase(d, cd.symbol, setups, teardowns, allIgnored, ruleFields, viaCall = true))
          else List(d)
        case d: Tree.DefDef if isAnnotated(d, TestAnn) =>
          List(testCase(d, cd.symbol, setups, teardowns, allIgnored, ruleFields))
        case other                                     => List(other)
      }
      suitesConverted += 1
      withSuite(cd2).copy(
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

  // -------------------------------------------------------------------------
  // JUnit's ExpectedException @Rule — the RULE MODELLED, not a lexical wrap
  // -------------------------------------------------------------------------

  /** `thrown.expect(E.class)` → an ARMING of the rule's own accumulator, and one `try`/`catch`
    * around the whole converted test — the `@Test(expected = …)` row of `CLAUDE.md` §4.4 met at
    * JUnit's other spelling, and with the same failure one step louder: the rule field is emitted,
    * nothing applies it, so the expected throw propagates and MUnit records a FAILURE where java
    * recorded a pass. Measured at **37 of one suite's 40 failing tests**, every one of them the port
    * being RIGHT about the library and wrong about the harness.
    *
    * The mechanical image exists because `ExpectedException` is not an arbitrary `@Rule`: junit
    * WRITES ITS CONTRACT DOWN, and this lowering is that contract transcribed rather than
    * approximated —
    *
    *   - `expect(Class)`/`expect(Matcher)`/`expectMessage(…)` APPEND to a matcher list
    *     (`ExpectedException.matcherBuilder`), whatever position the call stands in;
    *   - `ExpectedExceptionStatement.evaluate` runs the base statement, catches `Throwable`, and —
    *     if any matcher is set at that moment — asserts the CONJUNCTION of them over what was
    *     thrown, rethrowing untouched where none is;
    *   - completing normally with a matcher set is `failDueToMissingException`.
    *
    * So the emitted shape is junit's own state machine, one `var` for the list and one for the
    * throwable:
    *
    * {{{
    * var bpExpected: List[(Throwable) => Boolean] = Nil
    * var bpCaught: java.lang.Throwable = null
    * try { … bpExpected = bpExpected :+ ((bpEx: Throwable) => bpEx.isInstanceOf[E]) … }
    * catch { case bpThrown: java.lang.Throwable => bpCaught = bpThrown }
    * if (bpCaught ne null) { if (bpExpected.isEmpty) throw bpCaught else assert(bpExpected.forall(…)) }
    * else if (bpExpected.nonEmpty) fail(…)
    * }}}
    *
    * ==WHY NOT `intercept[E] { rest }`==
    * That was this translation's first shape and it converted 20 of ssg-md's 37 sites. Java's rule
    * is armed from the `expect` CALL TO THE END OF THE TEST — across the remaining iterations of an
    * enclosing loop, out of every enclosing block, past everything after it — and `intercept` wraps
    * a LEXICAL region, so every site standing anywhere but at statement position in the test's own
    * body had to be declined (17 of 37, all of them in a `while` or `for` body). The two ways to
    * stretch the wrap were each a DIFFERENT PROGRAM — see `ENGINE-LIMITS.md` X5 — and the
    * accumulator is what removes the question rather than approximating it: an arming is a
    * statement, so it goes exactly where java wrote it and reaches exactly as far as java's did.
    *
    * ==THE DIFFERENCES BETWEEN JAVA'S SHAPE AND THIS ONE==
    *
    * `CLAUDE.md` §3: every member of this set is (i) a structural GUARD, (ii) made impossible by the
    * SHAPE emitted, or (iii) COUNTED. A count of conversions says nothing about what was declined,
    * so each guard below files ONE [[Finding]] PER SITE naming itself.
    *
    *  1. **POSITION.** SHAPE — an arming is a statement rewritten IN PLACE, so a call in a loop, an
    *     `if`, a lambda or a nested block arms exactly what java armed, and the state it arms lives
    *     in the test's own frame. This is the difference the `intercept` shape could not express and
    *     the whole reason this lowering exists.
    *  2. **ACCUMULATION.** SHAPE — java requires ALL matchers set at the moment of the throw
    *     (`allOf`); the list is the same list and `forall` is the same conjunction. An arming that
    *     executes twice appends twice, exactly as junit's does, which is why the state is a LIST and
    *     not a flag: keeping only the last matcher would PASS where java FAILED.
    *  3. **THE CATCH POLICY.** SHAPE — junit catches `Throwable` and so does this, the
    *     `AssertionError` an assertion inside the body throws included. That is the one delta the
    *     `intercept` shape had to COUNT (MUnit's `intercept` catches `NonFatal`), and it is gone.
    *  4. **A TRANSLATED JAVA JUMP UNDER THE CATCH.** `scala.util.boundary.Break` extends
    *     `RuntimeException`, so a `break` crossing this catch would be recorded as the test's
    *     outcome where java's jump is not an exception at all. SHAPE: the emitter puts its own
    *     re-throw arm ahead of the recorder wherever a jump really crosses (`CLAUDE.md` §4.4), and
    *     `break-catch` counts any it did not.
    *  5. **THE MATCHER OVERLOAD.** `expect(Matcher)` is not a class literal. SHAPE: `matches(Object)`
    *     is the `org.hamcrest.Matcher` CONTRACT, so the predicate is one translation for every
    *     matcher class rather than a table of them. Which overload java resolved is read from the
    *     CALLEE'S OWN FORMAL and never guessed from the argument (§4.6): an unreadable signature
    *     declines (`expect-overload`).
    *  6. **`expectMessage`.** An added matcher, not a different wrap — junit's `expectMessage(String)`
    *     is `hasMessage(containsString(s))`, and hamcrest's `TypeSafeMatcher` answers FALSE for a
    *     null item, so the predicate is `getMessage ne null && getMessage.contains(s)`.
    *     `expectMessage(Matcher)` is that matcher over `getMessage()`.
    *  7. **OPERAND EVALUATION.** Java evaluated the matcher expression AT THE `expect` CALL. SHAPE:
    *     each non-literal operand is bound to a local at the arming site, which is inside the loop
    *     body where java bound it, so a closure appended on the second iteration captures the second
    *     iteration's matcher.
    *  8. **ANY OTHER USE OF THE FIELD.** `expectCause`, `handleAssertionErrors`, a `thrown` passed as
    *     an argument or assigned — each is a rule state this lowering does not model. GUARD: every
    *     reference to the field must be the receiver of an `expect`/`expectMessage` call
    *     (`unsupported-member` / `unsupported-reference`). **And that question is asked of the CLASS
    *     as well as of each body**, because a reference in a helper, a field initialiser or a nested
    *     class is in NO test body and would otherwise leave every test reading as *never touches the
    *     rule* — see `arming-outside-test`, filed where the rule FIELDS are found.
    *  9. **`@After` AND `@Test(expected = …)` ORDERING.** JUnit's rules are the OUTERMOST statement
    *     (`BlockJUnit4ClassRunner.methodBlock` wraps `withRules` around `withAfters` around
    *     `withBefores` around `possiblyExpectingExceptions`). SHAPE: this wrap is applied to the
    *     converted body AFTER the teardown `try … finally` and the `@Test(expected)` `intercept`,
    *     so a teardown that throws is compared against the expectation exactly as java compares it,
    *     and an annotation-expected throw is swallowed BELOW the rule exactly as java swallows it.
    *     Both were guards under the `intercept` shape and neither is one now.
    * 10. **THE FAILURE TEXT.** Junit renders hamcrest's `Description`; this names the throwable that
    *     arrived. Not a behavioural difference — both fail, and neither passes where the other did.
    *
    * @return the body to emit, the wrapper to apply OUTSIDE everything else the conversion emits,
    *         and the sentence the test's `Decision` carries (empty where nothing was translated —
    *         a declined site records its own [[Finding]] instead). */
  private def expectedException(d: Tree.DefDef, body: Term, rules: Set[SymId])
                               (using p: Program): (Term, Term => Term, String) =
    val refs =
      if rules.isEmpty then 0
      else StandardTraversal.scanTerm(body, 0)((n, t) => if isRuleRef(t, rules) then n + 1 else n)
    if refs == 0 then (body, identity, "")
    else
      def refuse(guard: String, why: String): (Term, Term => Term, String) =
        found += Finding(s"$ExpectedExceptionCls($guard)", d.origin, Fix.EngineRule, why, d.symbol)
        (body, identity, "")
      // every call ON the rule field, WHEREVER it stands — `StandardTraversal` and not a scan of the
      // body's own statements, because reaching a site in a loop body is what this lowering is for
      // (CLAUDE.md §3: the walk is the standard one or a construct is silently not there).
      val calls = StandardTraversal.scanTerm(body, List.empty[(String, Tree.Apply)]) { (acc, t) =>
        ruleCallIn(t, rules).map(acc :+ _).getOrElse(acc)
      }
      val unknown = calls.map(_._1).filterNot(n => n == "expect" || n == "expectMessage").distinct.sorted
      if calls.sizeIs != refs then refuse("unsupported-reference",
        s"$refs reference(s) to the ExpectedException rule field in this test and only " +
        s"${calls.size} of them are the RECEIVER of a call on it. A `thrown` passed as an " +
        "argument, assigned, or returned reaches a rule state this lowering does not model — the " +
        "accumulator it arms is a local of the test it was armed in — so the whole method is left " +
        "alone rather than half-converted.")
      else if unknown.nonEmpty then refuse("unsupported-member",
        "this test reaches the ExpectedException rule through a member other than `expect` / " +
        s"`expectMessage` (${unknown.mkString(", ")}). Each is a rule state this translation does " +
        "not model, so the whole method is left alone rather than half-converted.")
      else
        val kinds = calls.map((nm, a) => expectAt(nm, a))
        kinds.collectFirst { case Left(g) => g } match
          case Some(g)    => refuse(g, OverloadAdvice(g))
          case scala.None => model(d, body, rules, calls, kinds.flatMap(_.toOption))

  /** WHICH of junit's four `expect`/`expectMessage` overloads a call resolved to, or the guard that
    * declines it. ONE function, because the rewrite recomputes what the guard pass validated — over
    * the same node, with the same program, so the two answers are the same answer. */
  private def expectAt(nm: String, a: Tree.Apply)
                      (using p: Program): Either[String, Either[Expect, ExpectMsg]] =
    if nm == "expect" then expectKind(a).map(Left(_)) else expectMsgKind(a).map(Right(_))

  /** the sentence each overload guard declines with — one table, so the refusal a run REPORTS and
    * the guard the code took cannot drift apart. */
  private val OverloadAdvice: Map[String, String] = Map(
    "expect-overload" ->
      ("junit's `expect` has two overloads — `expect(Class<? extends Throwable>)` and " +
       "`expect(Matcher<?>)`. Which one java resolved is read from the CALLEE's own formal, and " +
       s"this call's is neither a `java.lang.Class` at a literal `classOf` nor an " +
       s"`$HamcrestMatcher`. A guess here would be a fabricated fact (CLAUDE.md §4.6)."),
    "expect-message-overload" ->
      ("junit's `expectMessage` has two overloads — `expectMessage(String)` (which means " +
       "`containsString`) and `expectMessage(Matcher<String>)`. This call's formal is neither, so " +
       "which one java resolved cannot be read."))

  /** the conversion — the armings rewritten in place, and the rule's own `try`/`catch` as a WRAPPER
    * the caller applies outside the lifecycle nesting (difference 9 above). */
  private def model(d: Tree.DefDef, body: Term, rules: Set[SymId],
                    calls: List[(String, Tree.Apply)], kinds: List[Either[Expect, ExpectMsg]])
                   (using p: Program): (Term, Term => Term, String) =
    val o     = d.origin
    val unitT = primTypes("scala.Unit")
    val boolT = primTypes("scala.Boolean")
    val expected = mint("bpExpected", "bpExpected", Flags(isMutable = true), predListType)
    val caught   = mint("bpCaught", "bpCaught", Flags(isMutable = true), throwableType)
    def expectedRef = Tree.Ident(expected, predListType, o)
    def caughtRef   = Tree.Ident(caught, throwableType, o)

    // ---- the armings, rewritten WHERE JAVA WROTE THEM ----
    var site = 0
    val rewriter = new Phase:
      def name: String = "test-framework/expected-exception-arming"
      override def transformApply(t: Tree.Apply)(using Program): Term =
        ruleCallIn(t, rules) match
          case Some((nm, a)) => val k = site; site += 1; arming(nm, a, expected, k)
          case scala.None    => t
    val armed = StandardTraversal.mapTerm(rewriter, body)

    // ---- junit's own statement: run, catch `Throwable`, apply the accumulated matchers ----
    val thrown  = mint("bpThrown", "bpThrown", Flags(), throwableType)
    val catcher = Tree.CatchCase(
      Tree.ValDef(thrown, TypeTree(throwableType, o), scala.None, o),
      Tree.Assign(caughtRef, Tree.Ident(thrown, throwableType, o), unitT, o))
    val matched = Tree.Apply(Tree.Select(expectedRef, forallSym, TypeRepr.NoType, o),
                             List(predicateTest(caughtRef, o)), forallSym, boolT, o)
    val checked = Tree.If(
      infix(caughtRef, neSym, nul(o), o),
      Tree.If(Tree.Select(expectedRef, isEmptySym, boolT, o),
              // NOTHING was armed when it threw — junit rethrows, and so must this: a test that
              // fails for its own reason must not be reported as an expectation that missed.
              Tree.Throw(caughtRef, TypeRepr.NoType, o),
              // the clue names WHAT arrived, because the expectation is a list of closures and
              // junit's own text is a hamcrest `Description` this has no image of.
              call("assert", List(matched, infix(constTerm(Constant.StringC(
                "the exception thrown does not satisfy the expectation the java test armed: "),
                "java.lang.String", o), plusSym, caughtRef, o)), o),
              unitT, o),
      Tree.If(Tree.Select(expectedRef, nonEmptySym, boolT, o),
              call("fail", List(constTerm(Constant.StringC(
                "the java test armed an ExpectedException that nothing threw — junit fails the " +
                "test at this point (ExpectedException.failDueToMissingException)"),
                "java.lang.String", o)), o),
              Tree.Literal(Constant.UnitC, unitT, o), unitT, o),
      unitT, o)

    def wrap(inner: Term): Term =
      Tree.Block(
        List(Tree.ValDef(expected, TypeTree(predListType, o),
                         Some(Tree.Ident(nilSym, predListType, o)), o),
             Tree.ValDef(caught, TypeTree(throwableType, o), Some(nul(o)), o),
             Tree.Try(Nil, inner, List(catcher), scala.None, unitT, o),
             checked),
        Tree.Literal(Constant.UnitC, unitT, o), unitT, o)

    rulesConverted += calls.size
    val what = kinds.map {
      case Left(Expect.OfClass(t))       => s"expect(${nameOf(t)})"
      case Left(Expect.OfMatcher(_))     => "expect(<matcher>)"
      case Right(ExpectMsg.Contains(_))  => "expectMessage(<text>)"
      case Right(ExpectMsg.ByMatcher(_)) => "expectMessage(<matcher>)"
    }
    (armed, wrap,
     s"@Rule ExpectedException modelled — ${what.mkString(", ")} armed in place, one try/catch " +
     "over the whole test")

  /** `bpP => bpP.apply(bpCaught)` — the conjunction junit spells `allOf(matchers).matches(e)`. */
  private def predicateTest(caught: Term, o: Origin): Term =
    val p = mint("bpP", "bpP", Flags(), predType)
    Tree.Lambda(List(Tree.ValDef(p, TypeTree(predType, o), scala.None, o)),
                invoke(Tree.Ident(p, predType, o), applySym, List(caught), o),
                TypeRepr.NoType, o)

  /** ONE `thrown.expect(…)` / `thrown.expectMessage(…)` call, as junit's own append.
    *
    * The operand is bound to a local FIRST and the closure captures the local, because java
    * evaluated the matcher expression at the call: a binding inside a loop body is re-bound on every
    * iteration, so the second iteration's closure holds the second iteration's matcher. A LITERAL
    * needs no binding and gets none. */
  private def arming(nm: String, a: Tree.Apply, expected: SymId, k: Int)(using p: Program): Term =
    val o   = a.origin
    val pre = List.newBuilder[Statement]
    def bound(name: String, t: Term): Term = t match
      case _: Tree.Literal => t
      case _ =>
        val s = mint(name, name, Flags(), t.tpe)
        pre += Tree.ValDef(s, TypeTree(t.tpe, o), Some(t), o)
        Tree.Ident(s, t.tpe, o)
    val ex    = mint("bpEx", "bpEx", Flags(), throwableType)
    val exRef = Tree.Ident(ex, throwableType, o)
    val boolT = primTypes("scala.Boolean")
    // recomputed rather than threaded from the guard pass: same node, same program, same function
    // (`expectAt`), so it is the same answer — and the `Left` arm is the state the guards already
    // declined the whole method on, which is why it cannot arrive here.
    val pred: Option[Term] = expectAt(nm, a).toOption.map {
      case Left(Expect.OfClass(t))   => Tree.InstanceOf(exRef, TypeTree(t, o), boolT, o)
      case Left(Expect.OfMatcher(m)) => invoke(bound(s"bpMatcher$k", m), matchesSym, List(exRef), o)
      case Right(ExpectMsg.Contains(t)) =>
        // hamcrest's `TypeSafeMatcher.matches` answers FALSE for a null item, so junit's
        // `containsString` over a null message does not throw — it does not match.
        infix(infix(message(exRef, o), neSym, nul(o), o), andSym,
              invoke(message(exRef, o), containsSym, List(bound(s"bpMessage$k", t)), o), o)
      case Right(ExpectMsg.ByMatcher(m)) =>
        invoke(bound(s"bpMessage$k", m), matchesSym, List(message(exRef, o)), o)
    }
    pred match
      case scala.None => a
      case Some(cond) =>
        val lam = Tree.Lambda(List(Tree.ValDef(ex, TypeTree(throwableType, o), scala.None, o)),
                              cond, predType, o)
        val ref = Tree.Ident(expected, predListType, o)
        val app = Tree.Assign(ref,
          Tree.Apply(Tree.Select(ref, appendSym, predListType, o), List(lam), appendSym,
                     predListType, o),
          primTypes("scala.Unit"), o)
        pre.result() match
          case Nil   => app
          case stats => Tree.Block(stats, app, primTypes("scala.Unit"), o)

  private def message(e: Term, o: Origin): Term = invoke(e, getMessageSym, Nil, o)

  private def invoke(recv: Term, m: SymId, args: List[Term], o: Origin): Term =
    Tree.Apply(Tree.Select(recv, m, TypeRepr.NoType, o), args, m, TypeRepr.NoType, o)

  /** a REFERENCE to one of this class's `ExpectedException` rule fields — `thrown` or
    * `this.thrown`, and never the `expect` selection ON one, whose member symbol is junit's. */
  private def isRuleRef(t: Term, rules: Set[SymId]): Boolean = t match
    case Tree.Ident(s, _, _)     => rules(s)
    case Tree.Select(_, s, _, _) => rules(s)
    case _                       => false

  /** a call ON a rule field — its member's simple name and the call — WHEREVER it stands. */
  private def ruleCallIn(t: Term, rules: Set[SymId])
                        (using p: Program): Option[(String, Tree.Apply)] = t match
    case a @ Tree.Apply(Tree.Select(rcv, m, _, _), _, _, _, _) if isRuleRef(rcv, rules) =>
      Some(p.symbolOf(m).map(_.name).getOrElse("") -> a)
    case _ => scala.None

  /** WHICH `expect` overload java resolved, read from the CALLEE's own formal.
    *
    * The class form additionally needs a LITERAL `classOf`, because what `intercept` takes is a
    * type ARGUMENT and a `Class` value is not one. A `Class`-typed variable is therefore declined
    * rather than approximated. */
  private def expectKind(a: Tree.Apply)(using p: Program): Either[String, Expect] = a.args match
    case List(Tree.Literal(Constant.ClassOfC(t), _, _)) => Right(Expect.OfClass(t))
    case List(arg) => formalOf(a) match
      case Some(f) if nameOf(f) == HamcrestMatcher => Right(Expect.OfMatcher(arg))
      case _                                       => Left("expect-overload")
    case _ => Left("expect-overload")

  private def expectMsgKind(a: Tree.Apply)(using p: Program): Either[String, ExpectMsg] = a.args match
    case List(arg) => formalOf(a) match
      case Some(f) if nameOf(f) == "java.lang.String"  => Right(ExpectMsg.Contains(arg))
      case Some(f) if nameOf(f) == HamcrestMatcher     => Right(ExpectMsg.ByMatcher(arg))
      case _                                           => Left("expect-message-overload")
    case _ => Left("expect-message-overload")

  /** the callee's ONE declared parameter type, where the frontend could read the class file. An
    * external member with no signature answers `None`, which every caller here declines on. */
  private def formalOf(a: Tree.Apply)(using p: Program): Option[TypeRepr] =
    p.symbolOf(a.method).map(_.info).collect {
      case TypeRepr.MethodType(List((_, f)), _, _) => f
    }

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
  private def testCase(d: Tree.DefDef, owner: SymId, setups: List[SymId], teardowns: List[SymId],
                       allIgnored: Boolean, ruleFields: Set[SymId],
                       viaCall: Boolean = false)(using p: Program): Statement =
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
      // …and JUnit's OTHER spelling of the same assertion — the `ExpectedException` @Rule, whose
      // arming call stands INSIDE the body. `ruleNote` is empty where the class declares no such
      // rule, where this test never touches it, and where a guard declined the site.
      // THE REGISTRATION'S CORE. Normally the method's own body, inlined; for a VIRTUAL test it is
      // a CALL to the method that stayed a `def`, because the whole point of keeping it is that a
      // subclass may replace it and the registration must dispatch rather than inline one version.
      //
      // The `ExpectedException` model is REFUSED there and counted, rather than approximated: it
      // arms a list that is a LOCAL of the frame it governs (see [[expectedException]]), and the
      // arming now sits inside a method the registration merely calls — so the matcher list the
      // wrap would build is not the one the body appends to. One row per site naming the guard, and
      // zero rows on this corpus, which is exactly why an unstated exclusion would never be found.
      val (ruleBody, ruleWrap, ruleNote) =
        if !viaCall then expectedException(d, d.rhs.get, ruleFields)
        else
          if ruleFields.nonEmpty && StandardTraversal.scanTerm(d.rhs.get, 0)((n, t) =>
               if isRuleRef(t, ruleFields) then n + 1 else n) > 0 then
            found += Finding(s"$ExpectedExceptionCls(rule-in-overridden-test)", d.origin, Fix.EngineRule, at = d.symbol, advice =
              "this `@Test` arms an `ExpectedException` rule AND takes part in java's own override " +
              "relation, so it stays a `def` and the MUnit registration calls it. The rule's matcher " +
              "list is modelled as a local of the frame the registration builds, and the arming is " +
              "one frame further in — so the wrap would test an expectation the body never appended " +
              "to. The rule is left unapplied here; inline the test into each concrete subclass, or " +
              "keep this suite on the JVM/JUnit path.")
          (call(d.symbol, d.origin), identity[Term], "")
      val body0 = expectsThrow match
        case Some(exTpe) => intercept(interceptSym, exTpe, ruleBody, d.origin)
        case scala.None  => ruleBody
      // JUnit's own nesting: afters(befores(expectException(invoke))). So the `@Before` calls go
      // INSIDE the try — a setup that throws still runs teardown, as in java — and the
      // expected-exception check goes inside them both.
      // …and AHEAD of `@Before`, JUnit's `createTest()`: the fresh instance it builds for every test
      // is what runs the class's field initialisers and constructor body, and it runs before the
      // setup hooks (probed against junit 4.13, `ENGINE-LIMITS.md` X4). Absent where the suite holds
      // no instance state at all, and where a guard in [[planFreshState]] declined the class.
      val rebuild = freshCall.get(owner).toList.map(call(_, d.origin))
      val prologue = rebuild ++ setups.map(call(_, d.origin))
      val setUp =
        if prologue.isEmpty then body0
        else Tree.Block(prologue, body0, body0.tpe, d.origin)
      val rhs0 =
        if teardowns.isEmpty then setUp
        else Tree.Try(Nil, setUp, Nil,
                      Some(seq(teardowns.map(call(_, d.origin)), TypeRepr.NoType, d.origin)),
                      setUp.tpe, d.origin)
      // …and JUnit's OUTERMOST statement, applied last: `methodBlock` wraps `withRules` around
      // `withAfters`, so an `ExpectedException` compares a THROWING TEARDOWN against the
      // expectation, and swallows an annotation-expected throw below it. Identity where the class
      // declares no such rule, where this test never touches it, and where a guard declined it.
      val rhs = ruleWrap(rhs0)
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
          "rule"      -> ruleNote,
          "inlined"   -> (setups ++ teardowns).flatMap(s => p.symbolOf(s).map(_.name)).mkString(", "),
          "rebuilt"   -> (if rebuild.isEmpty then "no" else FreshStateMember),
          "why"       -> ("a JUnit suite runs on the JVM alone; and MUnit has neither @Before " +
            "(which JUnit runs before EVERY test, on a fresh instance) nor @After (which it runs " +
            "whether or not the test threw), so both are inlined here and nothing else says so — " +
            "and the FRESH INSTANCE itself has no MUnit counterpart either, so this body opens by " +
            "rebuilding the suite's own state"),
        ),
        reason = Reason.Universal("test-framework"),
        origin = d.origin,
      ))
      // …and the METHOD'S OWN DOCUMENTATION, which stops having a `def` to sit on the moment this
      // phase runs. A `DefDef` carries `leading`; the statement that replaces it is a TERM, and the
      // TIR's carrier for a statement's comments is the `Commented` wrapper — so the javadoc lands
      // directly above `test("m")`, which is exactly where java had it. Without this it is the
      // largest single category the recovery backstop has to put back (51 comments on one suite),
      // and a backstop placement is member-granular where this is exact.
      val call0 = Tree.Apply(head, List(rhs), testSym, TypeRepr.NoType, d.origin)
      if d.leading.isEmpty then call0 else Tree.Commented(d.leading, call0)

object TestFrameworkTransform:
  val DefaultSuite = "munit.FunSuite"
  /** the member each converted class declares to rebuild its own instance state before every test —
    * JUnit's `createTest()`, which MUnit has no counterpart for. `bp`-prefixed like every other name
    * this phase mints, so it cannot collide with a java member a suite declares. */
  val FreshStateMember = "bpFreshState"
  /** the frontend's name for a java INSTANCE INITIALISER BLOCK (`SpoonTir.classDef`) — a synthetic
    * executable member, and half of JLS 12.5 step 4. Named here because this phase MOVES one, and
    * a phase that matched the string inline would be the second spelling of one fact. */
  val InitBlockName = "<initblock>"
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

  /** WHICH of junit's two `ExpectedException.expect` overloads a call resolved to — the answer read
    * from the callee's own formal, never from the argument's shape. */
  enum Expect:
    case OfClass(tpe: TypeRepr)
    case OfMatcher(matcher: Term)

  /** …and the same for `expectMessage`, whose `String` overload MEANS `containsString`. */
  enum ExpectMsg:
    case Contains(text: Term)
    case ByMatcher(matcher: Term)

  /** One test-framework construct this phase did not translate.
    *
    * `at` is the DECLARATION the construct sits on, and it is a `SymId` rather than a path because
    * that is what the D2 filter has to be asked (`CLAUDE.md` §4.56 — ownership is decided
    * STRUCTURALLY, never from a string). It is not decoration: `Origin` is the only other locator
    * here and a `Symbol`'s origin DEFAULTS TO `Origin.synthetic`, so a construct reported from a
    * symbol rather than from a tree node carries `<synthetic>` as its path. Filtered on the path,
    * exactly those rows vanish — measured at 11 of 30 surviving on one port, with the 19 missing
    * being every CLASS-LEVEL annotation (`@RunWith`, `@Suite.SuiteClasses`), which is the largest
    * standing refusal in the corpus and the one this lane exists to make visible. */
  final case class Finding(construct: String, where: Origin, fix: Fix, advice: String,
                           at: SymId = SymId.None):
    def render: String = s"$construct — (${fix.label}) $advice  (${where.javaPath}:${where.line})"

    /** …as a row of the [[Refused]] lane. The KIND is the construct, which is the GUARD this site
      * was declined at — `CLAUDE.md` §3's refusal-enumeration rule wants the guard named and not a
      * total, because a count of conversions says nothing about what was left alone.
      *
      * The OWNER is the caller's, because the caller is the one that climbed [[at]]'s owner chain
      * to the top-level unit and therefore already holds the emitted name. */
    def report(owner: String): CheckReport.Finding =
      CheckReport.Finding(Refused, construct, owner, where.javaPath, where.line,
                          s"(${fix.label}) $advice")

  /** THE REFUSAL POPULATION, as a lane — `CLAUDE.md` §3 and §5, at the phase that has the largest
    * standing one in this corpus.
    *
    * `run` has printed these to stdout since the phase was written, grouped by construct, and stdout
    * is not an artifact: no baseline diffs it, so a refusal that appeared, moved owner or changed its
    * advice reached nobody, and the only place the population was written down was a PROSE row in
    * `PROGRESS.md` that somebody had to keep in step by hand. That is the arrangement §5's
    * `findings.tsv` paragraph exists to refuse — every number that reaches stdout must reach the
    * artifact — and it is worse here than for most, because this phase's failure mode is SILENT: an
    * unrecognised annotation means the class is not converted at all, so it registers ZERO tests,
    * compiles, and reports success.
    *
    * `(refused)` and not a bare name, deliberately: the spelling says it is a RESIDUE lane in the
    * `idiom(refused)` family and not a defect count, and it is required only OF A RUN THAT CARRIES
    * THE PHASE (`PortRun.requiredChecks`) — a port with no test source set records nothing here, and
    * requiring it of every port would fail every one of them. */
  val Refused: String = "test-framework(refused)"

  /** the one-line classification every lane with a §1 answer prints beside its count. */
  val Classification: String =
    "  [§1(a) engine: every row is a fact about JUnit/TestNG and scala, identical for every library " +
      "— none of them is fixed by configuring this phase or by a library-specific rule. A refused " +
      "construct is NOT a compile error: the class converts to ZERO tests, compiles, and reports " +
      "success, so this lane is the only instrument there is.]"

  /** one line per construct, with the count and one example site — the shape a reader scans. */
  def summary(fs: Seq[Finding]): String =
    if fs.isEmpty then "  (none)"
    else fs.groupBy(_.construct).toList.sortBy(g => (-g._2.size, g._1)).map { (c, gs) =>
      s"  $c × ${gs.size} — (${gs.head.fix.label}) ${gs.head.where.javaPath}:${gs.head.where.line}"
    }.mkString("\n")

  /** Widening rank for java's BINARY NUMERIC PROMOTION: a value of rank r converts, without loss,
    * to any numeric type of higher rank. `Char` and `Short` share a rank because neither widens to
    * the other — java promotes that pair to `Int`, and so does [[TestFrameworkTransform.promote]].
    *
    * Deliberately NOT the emitter's copy of this table: that one exists to disambiguate an OVERLOAD
    * at emission, this one to rewrite a TREE. Sharing them would couple a transform to a backend. */
  val NumericRank: Map[String, Int] = Map(
    "scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2, "scala.Int" -> 3,
    "scala.Long" -> 4, "scala.Float" -> 5, "scala.Double" -> 6)

  /** The types every other type conforms to — where MUnit's `Compare` resolves whatever the other
    * operand is, so its constraint is ALREADY VACUOUS and there is no check to preserve.
    *
    * Read [[TestFrameworkTransform.widened]] for why that makes a root a reason TO widen rather
    * than a reason not to. `java.lang.Object` is the one java's `assertEquals` formal produces; the
    * Scala roots are here because a port's own retyping can put one on an operand. */
  val Roots: Set[String] =
    Set("java.lang.Object", "scala.Any", "scala.AnyRef", "scala.Matchable")

  /** How many arguments each `org.junit.Assert` member takes WITHOUT java's optional leading
    * `String message`. Everything above this count with a leading `String` is that message — which
    * is what separates `assertEquals(String, Object, Object)` from `assertEquals(double, double,
    * double)` without either being named. */
  val MinArity: Map[String, Int] = Map(
    "assertEquals" -> 2, "assertNotEquals" -> 2, "assertArrayEquals" -> 2,
    "assertSame" -> 2, "assertNotSame" -> 2, "assertTrue" -> 1, "assertFalse" -> 1,
    "assertNull" -> 1, "assertNotNull" -> 1, "fail" -> 0,
    // JUnit 4.13's `assertThrows(Class<T>, ThrowingRunnable)`, and its 3-arg message overload —
    // which is HERE so that `hasMsg` separates the two, and refused in `munitCall` because MUnit's
    // `intercept` has no clue slot to put the message in.
    "assertThrows" -> 2)
