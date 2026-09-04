package balticporter.transform

import balticporter.tir.*

/** A JUnit suite → a CROSS-PLATFORM Scala suite (MUnit). §1(b): `suite`/`testMember` are
  * constructor parameters, but MUnit's own contract (curried `test(name)(body)`, `.ignore`,
  * `intercept[E]`, assertions) is named literally, so pointing `suite` elsewhere does not compile.
  * `@Rule`/`@RunWith`/JUnit 5/TestNG/JUnit 3/Hamcrest are unsupported, reported with their §1
  * classification (an unrecognised annotation silently registers zero tests). */
final class TestFrameworkTransform(
    suite: String = TestFrameworkTransform.DefaultSuite,
    testMember: String = "test",
    /** Instance field FQNs (`Owner#name`) excluded from `bpFreshState`: a field the port DROPS
      * via `dropMethods` must not have its initialiser hoisted into the per-test reset, since the
      * initialiser may reference platform-unavailable types. Empty = no-op. */
    dropFields: Set[String] = Set.empty,
) extends Phase, balticporter.core.SurfacePolicy, PolicyBound:

  import TestFrameworkTransform.{Expect, ExpectMsg, Finding, Fix, FreshStateMember, InitBlockName,
                                 MinArity, NumericRank, Roots}

  def name: String = "junit->portable-suite"

  /** WHICH DECLARATIONS THIS RUN EMITS — a suite whose java superclass is a BASE module's test
    * class is a chain this run cannot write (`override def bpFreshState` would name a member only
    * the base's run could put there) and is refused and counted instead. `CLAUDE.md` §1.5.
    * [[RunScope.whole]] is the default, correct for a base or single-module port. */
  private var scope: RunScope = RunScope.whole

  def bindPolicy(binder: PolicyBinder): Unit = scope = binder.run

  /** SurfacePolicy: [[suite]] becomes the converted suite's parent, [[testMember]] every `@Test`
    * call, so two differently-configured modules emit different signatures. `ENGINE-LIMITS.md` CT9. */
  def surfaceFingerprint: String = s"suite=$suite,test=$testMember"

  /** JUnit's assertion statics live at THREE FQNs (JUnit 3's `Assert`/`TestCase` plus JUnit 4's),
    * all sharing `org.junit.Assert`'s contract — `(expected, actual)` with an optional leading
    * `String message`. A §1(a) fact about JUnit, not a constructor parameter. */
  private val AssertClasses = Set("org.junit.Assert", "junit.framework.Assert", "junit.framework.TestCase")
  /** MUnit declares assertions on both the `Assertions` TRAIT `FunSuite` mixes in and the
    * `Assertions` OBJECT; emitting through the object also resolves inside a companion (a java
    * `static` test helper), which does not extend the suite. */
  private val MunitAssertions = "munit.Assertions"
  /** MUnit's own members this phase emits; `MinArity` is the matching `org.junit.Assert` list —
    * a junit name absent from it (`assertThat`) is reported, never guessed at. */
  private val MunitMembers = Set("assertEquals", "assertNotEquals", "assert", "fail",
    "assertEqualsFloat", "assertEqualsDouble", "intercept")
  /** JUnit's OTHER spelling of `@Test(expected = …)`: `@Rule ExpectedException thrown` arms a
    * matcher at `thrown.expect(…)` and applies it to whatever the test throws from there on.
    * JUnit's own contract (`ExpectedExceptionStatement.evaluate`), so a §1(a) fact, not policy.
    * [[expectedException]] models it; deltas are enumerated there per CLAUDE.md §3. */
  private val ExpectedExceptionCls = "org.junit.rules.ExpectedException"
  private val RuleAnn        = "org.junit.Rule"
  private val ClassRuleAnn   = "org.junit.ClassRule"
  /** The one hamcrest member this phase names: `matches(Object)` is the `Matcher` CONTRACT, so
    * asserting it over the intercepted value is universal rather than per-matcher-class. */
  private val HamcrestMatcher = "org.hamcrest.Matcher"
  private val TestAnn        = "org.junit.Test"
  private val BeforeAnn      = "org.junit.Before"
  private val AfterAnn       = "org.junit.After"
  private val IgnoreAnn      = "org.junit.Ignore"
  private val BeforeClassAnn = "org.junit.BeforeClass"
  private val AfterClassAnn  = "org.junit.AfterClass"

  /** Annotations whose meaning this phase MOVES into emitted call sites; the annotation itself
    * must not survive emission. `@Test` is deliberately excluded — left on an abstract method it
    * is the residue the discovery count measures. */
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

  // symbol minting continues DURING the walk, so the counter and buffer are fields, not `run` locals.
  private var nextId: Int = 0
  private val added = collection.mutable.ListBuffer[Symbol]()
  /** declarations whose consumed annotation must be stripped from the emitted output. */
  private val consumed = collection.mutable.Set.empty[SymId]
  /** the `@Test` methods that SURVIVE as `def`s ([[virtualTests]]) and must lose that annotation. */
  private val consumedTests = collection.mutable.Set.empty[SymId]
  private val found = collection.mutable.ListBuffer[Finding]()
  private var suitesConverted = 0
  private var testsConverted  = 0
  /** `thrown.expect(…)` sites turned into `intercept`; declined ones are one [[Finding]] each,
    * naming the guard (`CLAUDE.md` §3). */
  private var rulesConverted  = 0

  /** JS-E07's citation state — set when [[promote]] widens an operand, read by
    * [[transformDefDef]] after the body it belongs to. Per-declaration, since the assertion
    * rewrite (`transformApply`) sees a call and never the enclosing member; cleared per declaration. */
  private var promotedHere = false

  /** A TEST-CLASS HIERARCHY. [[suiteAnchors]]: the suite parent goes at the ROOT of the
    * program-declared chain above the `@Test` declarer (scalac rejects a second class parent).
    * [[virtualTests]]: a `@Test` in an override relation stays a `def`, registering ONCE at the
    * top declarer as a call (a statement would duplicate and lose `super.m()`). Both computed
    * once per [[run]] over the whole program. */
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
  /** class → the NEAREST ANCESTOR that also declares one, i.e. `super.bpFreshState()`'s target.
    * Not necessarily the direct parent: a class the lowering could not reach is skipped over. */
  private var freshSuper: Map[SymId, SymId] = Map.empty
  /** class → the member a `test(…)` registration EMITTED IN IT may call: its own, or the nearest
    * inherited one. Distinct from [[freshSym]] since a virtual test registers at the TOP declarer,
    * which may hold no state of its own while its subclasses do. */
  private var freshCall: Map[SymId, SymId]  = Map.empty
  /** java `final` instance fields the reconstruction ASSIGNS, hence made `var`s — narrowed to the
    * fields the lowering really writes; every other field is emitted exactly as it was. */
  private val madeMutable = collection.mutable.Set.empty[SymId]
  private var suitesRebuilt = 0

  /** Constructs this phase could not translate, with their CLAUDE.md §1 classification. Empty
    * until [[run]] has executed. */
  def findings: List[Finding] = found.toList

  /** the program-declared SUPERCLASS of `cd`, if any. A parent this program does not declare, or
    * a TRAIT (java's `implements`), is not a candidate — §4.56. */
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

  /** Per-test RECONSTRUCTION: JUnit builds a fresh instance per `@Test` (JLS 12.5); MUnit has one
    * suite instance. Hoists that sequence into `override def bpFreshState()`, called ahead of
    * `@Before`, chaining `super.bpFreshState()` after zeroing this class's own fields
    * (`ENGINE-LIMITS.md` X4). Not reproduced (§3): object identity, a non-replayable constructor,
    * a field with no writable default, a base module's test class — each guarded and counted. */
  private def planFreshState(program: Program)(using p: Program): Unit =
    freshSym = Map.empty; freshSuper = Map.empty; freshCall = Map.empty
    // classes java rebuilds for one suite: the declarer and every program-declared class above it.
    def chainOf(s: SymId): List[Tree.ClassDef] = classDefs(s) :: classAncestry(classDefs(s))
    val declarers = testDeclarers.toList.filter(classDefs.contains).sortBy(_.raw)
    // a chain leaving the run's own emission is refused whole: the `override` names a member only
    // the base's run could have emitted.
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
    // a chain is active or absent, never per-class: a stateless suite gets no member, but where any
    // class in an active chain holds state, EVERY class the lowering can express gets one — a
    // virtual test's registration stands at the top declarer and cannot call a member missing there.
    val inSet = chains.filter(c => c.exists(cd => !blocked(cd.symbol) && holdsState(cd)))
                      .flatten.map(_.symbol).distinct.filterNot(blocked).toSet
    // settled before symbols are minted, since whether a member carries `override` depends on it.
    val supers = inSet.iterator.flatMap { s =>
      classAncestry(classDefs(s)).map(_.symbol).find(inSet).map(s -> _)
    }.toMap
    val unitT = primTypes("scala.Unit")
    freshSym = inSet.iterator.map { s =>
      // `MemberKey`, never `owner + "#" + name` (`PolicyKeyLintSpec`); a bare key since a nilary
      // synthesised member has no overload to distinguish.
      val fqn = p.symbolOf(s).map(o => MemberKey(o.fullName, FreshStateMember).render)
                  .getOrElse(FreshStateMember)
      s -> mint(FreshStateMember, fqn, Flags(isOverride = supers.contains(s)),
                TypeRepr.MethodType(Nil, unitT), owner = s)
    }.toMap
    freshSuper = supers.map((s, a) => s -> freshSym(a))
    // computed for every class in a chain, since a `@Test` may stand in one the lowering skipped.
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

  /** a field the ALLOCATION zeroes and step 4 initialises — never a `static` (java shares one
    * across every construction) and never a member ANOTHER PHASE MINTED (`Program.owns` is the
    * structural test, since a `ValDef` in an emitted body is not evidence java declared it —
    * CLAUDE.md §4.56). */
  private def instanceField(v: Tree.ValDef)(using p: Program): Boolean =
    p.owns(v.symbol) && p.symbolOf(v.symbol).exists { s =>
      !s.flags.isStatic && !s.flags.isGiven && !s.flags.isImplicit && !s.flags.isModule &&
        s.name.nonEmpty &&
        !isDroppedField(s)
    }

  private def isDroppedField(s: Symbol)(using p: Program): Boolean =
    if dropFields.isEmpty then false
    else p.symbolOf(s.owner).exists(o => dropFields.contains(MemberKey(o.fullName, s.name).render))

  private def isDroppedField2(v: Tree.ValDef)(using p: Program): Boolean =
    if dropFields.isEmpty then false
    else p.symbolOf(v.symbol).exists(isDroppedField)

  private def isInitBlock(d: Tree.DefDef)(using p: Program): Boolean =
    p.symbolOf(d.symbol).exists(_.name == InitBlockName)

  /** THE ONE CONSTRUCTOR THIS LOWERING MAY REPLAY, or the sentence the refusal reports. Matches
    * JUnit's own precondition (`validateOnlyOneConstructor` + `validateZeroArgConstructor`), so
    * the guard costs nothing on a class JUnit would run. */
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

  /** THE VALUE THE ALLOCATION LEAVES, as a term — the same answer `TirEmitter.defaultFor` writes
    * for an uninitialised java field, in the IR rather than in text. `None` where the type states
    * no writable default (a class type parameter or an opaque type): refused and counted, never
    * guessed (`CLAUDE.md` §4.6). */
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
    val hasDroppedFields = dropFields.nonEmpty && cd.body.exists {
      case v: Tree.ValDef => isDroppedField2(v)
      case _              => false
    }
    freshSym.get(cd.symbol) match
      case scala.None if hasDroppedFields =>
        cd.copy(body = cd.body.map {
          case v: Tree.ValDef if isDroppedField2(v) => v.copy(rhs = scala.None)
          case other                                => other
        })
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
          case v: Tree.ValDef if isDroppedField2(v) =>
            kept += v.copy(rhs = scala.None)
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
            // JLS 12.5 step 4 is ONE textual-order sequence, already sorted by the frontend
            // (§4.55); the member is CONSUMED rather than left for the emitter to inline again.
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

  /** the instance used as a VALUE — a `this` that is the QUALIFIER of a selection is a field/method
    * access and not an escape, so this is the difference of two standard walks. */
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
    // `munit.TestOptions("n").ignore`: `"n".ignore` needs MUnit's implicit String conversion.
    testOptionsSym = mint("TestOptions", "munit.TestOptions")
    ignoreSym      = mint("ignore", "ignore")
    munitSyms = MunitMembers.map(nm => nm -> mint(nm, MunitAssertions + "." + nm)).toMap
    // a `Select`'s member renders by SIMPLE name, so these are minted unqualified.
    widenSyms  = NumericRank.keys.map(t => t -> mint("to" + t.stripPrefix("scala."), "to" + t.stripPrefix("scala."))).toMap
    toSeqSym   = mint("toSeq", "toSeq")
    indicesSym = mint("indices", "indices")
    // reference identity, NOT `==` — CLAUDE.md §4.4. `scala.<op>#` is the emitter's infix marker.
    eqSym = mint("eq", "scala.<op>#eq")
    neSym = mint("ne", "scala.<op>#ne")
    matchesSym    = mint("matches", "matches")
    getMessageSym = mint("getMessage", "getMessage")
    containsSym   = mint("contains", "contains")
    // `Nil` is an Ident (§6, no imports) and so is QUALIFIED; the rest are ordinary selections.
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
    // both are facts about the whole program's class graph, settled before either walk starts.
    planHierarchy(program)
    planFreshState(program)
    survey(program)
    // the ASSERTION rewrite runs over every unit — a test HELPER declares no `@Test` and is where
    // assertions are often centralised — and resolves everywhere since assertions are emitted
    // fully qualified to the `munit.Assertions` OBJECT ([[MunitAssertions]]). Run once per unit
    // rather than per converted class: `mapClassDef` descends into nested classes on its own.
    val rewritten = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    // the CONVERSION (parent, registrations, lifecycle) stays gated on `@Test`: a helper that
    // gained the parent would register zero tests and claim to be one.
    val units = rewritten.map(convert)
    // `convert` mints more (the lifecycle overrides), so the table is rebuilt AFTER the walk.
    // a java `final` instance field the reconstruction ASSIGNS has to be a `var` (this suite is
    // constructed once); narrowed to the fields really written (§4.55's promotion-mutability rule).
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
          // a consumed annotation the frontend could not carry was still handled, so it must stop
          // being reported as an omission.
          droppedAnnotations = s.droppedAnnotations.filterNot(ConsumedAnns)))
    }
    report()
    program.rebuilt(units, symbols)

  /** `owner` is `SymId.None` for every mint EXCEPT the per-test reconstruction's: `Program.owns`
    * decides membership, and an unowned member publishes at the PACKAGE rather than its class. */
  private def mint(nm: String, full: String, flags: Flags = Flags(), info: TypeRepr = TypeRepr.NoType,
                   owner: SymId = SymId.None): SymId =
    val id = SymId(nextId); nextId += 1
    added += Symbol(id, nm, full, flags, owner, info)
    id

  private def report(): Unit =
    println(s"[$name] converted $suitesConverted suite(s), $testsConverted test(s); " +
            s"UNTRANSLATED test-framework constructs: ${found.size}")
    if suitesRebuilt > 0 then
      println(s"  fresh instance: $suitesRebuilt class(es) rebuild their instance state before " +
              "every test — JUnit constructs the test object per @Test and MUnit has ONE suite " +
              s"instance, so each class's field initialisers, instance initialiser blocks and " +
              s"constructor body are hoisted into `$FreshStateMember`")
    if rulesConverted > 0 then
      println(s"  ExpectedException @Rule: $rulesConverted `thrown.expect(…)` site(s) MODELLED — " +
              "the rule's matcher list armed in place and one try/catch over the whole test, which " +
              "is junit's own `ExpectedExceptionStatement` and reaches a site in a loop body")
    if found.nonEmpty then
      // grouped: one unhandled annotation is typically on every method of a suite.
      found.groupBy(_.construct).toList.sortBy(-_._2.size).foreach { (c, fs) =>
        println(s"  $c × ${fs.size} — (${fs.head.fix.label}) ${fs.head.advice}")
        fs.take(3).foreach(f => println(s"      ${f.where.javaPath}:${f.where.line}"))
      }

  // -------------------------------------------------------------------------
  // Survey — what this phase does NOT translate
  // -------------------------------------------------------------------------

  /** Every test-framework construct the phase leaves alone, recorded with its §1 classification —
    * all §1(a). An unrecognised annotation means the class is not converted at all, so it
    * registers ZERO tests, compiles, and reports success (`CLAUDE.md` §4.45). */
  private def survey(program: Program)(using p: Program): Unit =
    val roots = List("org.junit.", "org.junit.jupiter.", "org.testng.")
    program.symbols.all.foreach { s =>
      val names = s.annotations.map(a => nameOf(a.tpe) -> a.origin) ++
                  s.droppedAnnotations.map(_ -> s.origin)
      names.foreach { (fqn, o) =>
        if roots.exists(fqn.startsWith) && !HandledAnns(fqn) then
          val (fix, advice) = adviceFor(fqn)
          // `s.id` and not `o`: a DROPPED annotation's `origin` defaults to `Origin.synthetic`,
          // so `Finding.at` asks the owner chain instead.
          found += Finding(fqn, o, fix, advice, s.id)
      }
    }
    // JUnit 3 has no annotations: a suite is a `junit.framework.TestCase` subclass whose test
    // methods are named `testXxx`, so the PARENT is the signal.
    def scanParents(cd: Tree.ClassDef): Unit =
      cd.parents.foreach {
        case tt: TypeTree if nameOf(tt.tpe) == "junit.framework.TestCase" =>
          found += Finding("junit.framework.TestCase", cd.origin, Fix.EngineRule, at = cd.symbol, advice =
            "a JUnit 3 suite declares its tests by NAMING them `testXxx` on a `TestCase` subclass; " +
            "this phase keys off `@Test` and converts nothing, so the class emits as a plain class " +
            "and registers zero tests.")
        case _ => ()
      }
    // `allClassDefs`, not a `cd.body` recursion — a method-LOCAL class (`JS-C30`) stands in a
    // member's block and would be missed by the latter (§3).
    program.units.foreach(u => StandardTraversal.allClassDefs(u)(using program).foreach(scanParents))
    // Hamcrest: a second assertion vocabulary, reached via `org.junit.Assert.assertThat` or
    // `org.hamcrest.MatcherAssert`.
    program.referenced.foreach { id =>
      program.symbolOf(id).foreach { s =>
        // `RuleScope.covers`, not `startsWith` — a bare prefix covers `org.hamcrestic` too (§4.56).
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

  /** `org.junit.Assert.assertX(…)` → the MUnit assertion meaning the same thing. Java's
    * `(expected, actual)` with an optional leading `String message` becomes MUnit's `(obtained,
    * expected, clue)`; the overload is read from the ARGUMENTS' static types, not the callee's
    * signature. An unmapped member (`assertThat`) is left alone and reported. `SpoonTir.fromSource`
    * builds with `noClasspath`, so a static-import snippet never fires this hook. */
  override def transformApply(t: Tree.Apply)(using p: Program): Term = t.fun match
    case Tree.Select(recv, m, _, o) =>
      assertClassOf(recv) match
        case scala.None      => t
        case Some(assertCls) =>
          val nm = p.symbolOf(m).map(_.name).getOrElse("")
          munitCall(nm, t.args, o) match
            case Right(rewritten) => rewritten
            case Left(why)        =>
              // names the receiver the CALL had, not a canonical one, so an agent can find the site.
              found += Finding(assertCls + "." + nm, o, Fix.EngineRule,
                s"no MUnit counterpart is known for this `$nm` overload (${t.args.size} argument(s)), so " +
                s"the call is left on $assertCls — which compiles only with JUnit on the classpath and " +
                "cannot run on Scala.js / Native. Add the mapping to TestFrameworkTransform.munitCall." +
                (if why.isEmpty then "" else s" WHY THIS ONE: $why"))
              t
    case _ => t

  /** java's `(message?, expected, actual, delta?)` → MUnit's `(obtained, expected, delta?, clue?)`.
    * `hasMsg` is decided STRUCTURALLY: a leading `String` is junit's message exactly when the call
    * carries more arguments than the member's minimal arity. */
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
        case ("assertFalse", List(c)) => Right(call("assertEquals", c :: bool(false, o) :: clue, o))
        case ("assertNull", List(x))    => Right(call("assertEquals", x :: nul(o) :: clue, o))
        case ("assertNotNull", List(x)) => Right(call("assertNotEquals", x :: nul(o) :: clue, o))
        // REFERENCE identity — scala's `==` is java's `equals` (CLAUDE.md §4.4).
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
          // `guarded`: an infix or control-flow operand would bind `.toSeq` to its last branch.
          Right(call("assertEquals",
            select(guarded(a), toSeqSym, o) :: select(guarded(e), toSeqSym, o) :: clue, o))
        case ("assertArrayEquals", List(e, a, delta)) => arrayWithDelta(e, a, delta, clue, o).toRight("")
        // JUnit 4.13's `assertThrows` asserts what MUnit's `intercept[T] { … }` asserts. Two
        // refusals: the runnable must be a LAMBDA (a value or method reference would test whether
        // CONSTRUCTING it threw, not running it), and no leading `String message` (`intercept`
        // has no clue slot).
        case ("assertThrows", List(Tree.Literal(Constant.ClassOfC(ex), _, _), lam: Tree.Lambda))
            if clue.isEmpty && lam.params.isEmpty =>
          Right(intercept(munitSyms("intercept"), ex, lam.body, o))
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

  /** `intercept[E] { body }` — MUnit's assertion that the body throws. `sym` is a parameter since
    * there are two spellings: [[testCase]] uses the inherited one where `suite` is a parent, and
    * [[munitCall]] the `munit.Assertions` OBJECT one, for scopes that don't extend the suite
    * ([[MunitAssertions]]). */
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

  /** JAVA'S BINARY NUMERIC PROMOTION, re-applied. `assertEquals(int, long)` is legal java (the
    * call promoted `int` first); MUnit infers each operand independently, so nothing drives
    * scala's widening. Widening the NARROWER operand is the only safe direction; `Char`/`Short`
    * share a rank (neither widens to the other), so equal-rank promotes both to `Int`. */
  private def promote(x: Term, y: Term)(using p: Program): (Term, Term) =
    val (tx, ty) = (nameOf(x.tpe), nameOf(y.tpe))
    (NumericRank.get(tx), NumericRank.get(ty)) match
      case (Some(rx), Some(ry)) if tx != ty =>
        val to = if rx > ry then tx else if ry > rx then ty else "scala.Int"
        promotedHere = true
        (widen(x, tx, to, p), widen(y, ty, to, p))
      case _ => (x, y)

  /** JS-E07's CITATION — the catalog's third discharge surface (`DESIGN.md` §2.8). The traversal
    * is bottom-up, so a `DefDef` is reached after every `Apply` in its body and [[promotedHere]]
    * says whether promotion fired anywhere in this member. Returns the tree UNCHANGED. */
  override def transformDefDef(t: Tree.DefDef)(using p: Program): Tree.DefDef =
    citeIfPromoted(t.symbol)
    t

  /** …AND A FIELD INITIALISER IS NOT INSIDE A `DefDef` — its promotion must clear here too, or the
    * flag survives to the NEXT `DefDef` and cites the wrong member (`CLAUDE.md` §4.575). */
  override def transformValDef(t: Tree.ValDef)(using p: Program): Tree.ValDef =
    citeIfPromoted(t.symbol)
    t

  /** …and the CLASS BOUNDARY is the backstop: a class-body term neither hook above owns belongs to
    * its own type, and this fires only for what they do not cover. */
  override def transformClassDef(t: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    citeIfPromoted(t.symbol)
    t

  private def citeIfPromoted(sym: SymId)(using p: Program): Unit =
    if promotedHere then
      cite(balticporter.catalog.JS.E(7), p.symbolOf(sym).map(_.fullName).getOrElse(sym.toString))
      promotedHere = false

  private def widen(t: Term, from: String, to: String, p: Program): Term =
    if from == to then t else select(guarded(t)(using p), widenSyms(to), t.origin, primTypes(to))

  /** JAVA'S OTHER WIDENING, re-applied — the REFERENCE half of [[promote]]. Java's
    * `assertEquals(Object, Object)` widens every pair at the call; MUnit's `Compare[A, B]` rejects
    * two invariant `java.util.List`s at different element types. Written as the call's TYPE
    * ARGUMENTS. True exactly when both static types are SAME and not a ROOT. Refuses rather than
    * guesses on `NoType` or a PRIMITIVE (§4.4). */
  private def widened(x: Term, y: Term)(using p: Program): Boolean =
    val (sx, sy) = (shape(x.tpe), shape(y.tpe))
    sx.nonEmpty && sy.nonEmpty &&
      !isValueType(x.tpe) && !isValueType(y.tpe) &&
      (sx != sy || Roots(sx))

  private def isValueType(t: TypeRepr)(using p: Program): Boolean =
    val n = nameOf(t)
    NumericRank.contains(n) || n == "scala.Boolean" || n == "scala.Unit"

  /** A type's full structural name, type ARGUMENTS included — [[nameOf]] answers only the type
    * CONSTRUCTOR, which would make the pair [[widened]] exists for compare equal. */
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
    * Emitted as the loop it means. Both arrays are bound to locals FIRST — the operands are
    * arbitrary expressions, and naming each once is the difference between one evaluation and one
    * per element. The length check comes first, as in junit. */
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
    * too, so the walk is explicit rather than top-level only. Gates the CONVERSION alone (parent,
    * registrations, lifecycle inlining) — the assertion rewrite already ran over the whole unit
    * ([[run]]), since a `@Test`-less class is a HELPER, not a non-test. */
  private def convert(cd: Tree.ClassDef)(using p: Program): Tree.ClassDef =
    val nested = cd.body.map {
      case c: Tree.ClassDef => convert(c)
      case other            => other
    }
    // per-test RECONSTRUCTION: moves this class's own initialisation into its own member. Touches
    // only classes [[planFreshState]] admitted. TWO CLASSES, deliberately: `cd1` is what every
    // ANALYSIS below reads, `cd2` is what is EMITTED — a reference scan over `cd2` would count the
    // reconstruction's own assignments as usages (silently refused eleven fixtures once).
    val cd1 = cd.copy(body = nested)
    val cd2 = freshState(cd1)
    // the suite parent is the ANCHOR's, not the declarer's (see [[suiteAnchors]]): a class that
    // anchors a hierarchy but declares no `@Test` of its own gets the parent and nothing else.
    def withSuite(c: Tree.ClassDef): Tree.ClassDef =
      if !suiteAnchors(cd.symbol) then c
      else c.copy(parents = TypeTree(TypeRepr.TypeRef(TypeRepr.NoPrefix, suiteSym), cd.origin) :: c.parents)
    if !nested.exists(isAnnotated(_, TestAnn)) then withSuite(cd2)
    else
      // JUnit runs `@Before` before EVERY test on a FRESH instance; MUnit has neither, so this is
      // called at the head of each test body — reproducing per-test setup that ASSIGNS fields, but
      // not a field carrying state through its own INITIALISER rather than setup.
      val setups = cd2.body.collect {
        case d: Tree.DefDef if isAnnotated(d, BeforeAnn) => d.symbol
      }
      // `@After` runs after every test WHETHER OR NOT IT THREW, so it is emitted as
      // `try { setUp(); intercept[E]{ body } } finally { tearDown() }`, not a trailing call.
      val teardowns = cd2.body.collect {
        case d: Tree.DefDef if isAnnotated(d, AfterAnn) => d.symbol
      }
      // `@BeforeClass`/`@AfterClass` are java `static`; MUnit's `beforeAll`/`afterAll` call them
      // through the companion object.
      val classSetups = cd2.body.collect {
        case d: Tree.DefDef if isAnnotated(d, BeforeClassAnn) => d.symbol
      }
      val classTeardowns = cd2.body.collect {
        case d: Tree.DefDef if isAnnotated(d, AfterClassAnn) => d.symbol
      }
      consumed ++= setups ++ teardowns ++ classSetups ++ classTeardowns
      // matched on the field's own TYPE, not the annotation alone: `@Rule` is every rule class
      // junit has, and this phase translates exactly one of them.
      val ruleFields0 = cd2.body.collect {
        case v: Tree.ValDef
            if hasAnn(v.symbol, RuleAnn) && nameOf(v.tpt.tpe) == ExpectedExceptionCls => v.symbol
      }.toSet
      // the rule is MODELLABLE only where every arming is inside the test it governs, since
      // [[expectedException]]'s accumulator is a LOCAL of that test's frame — an arming from a
      // helper, initialiser or nested class would model FEWER matchers than java accumulated,
      // passing where java failed. Refuses the whole CLASS rather than the site, counted as a
      // DIFFERENCE of two standard walks (`CLAUDE.md` §3).
      val ruleFields =
        if ruleFields0.isEmpty then ruleFields0
        else
          def refs(n: Int, t: Term) = if isRuleRef(t, ruleFields0) then n + 1 else n
          // `cd1`: the class BEFORE the per-test reconstruction (see its binding above).
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
      // `@ClassRule` is REPORTED, never taken for `@Rule`: it wraps the whole class run, not each
      // test, so `intercept` in a test body is not its image.
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
      // a VIRTUAL `@Test` keeps its `def` (see [[virtualTests]]); the TOP declarer additionally
      // emits the one registration, whose body CALLS the method.
      val body = cd2.body.flatMap {
        case d: Tree.DefDef if isAnnotated(d, TestAnn) && virtualTests(d.symbol) =>
          // the `def` survives here, so its `@Test` must go explicitly — left on, it reads as a
          // suite that did not convert (`junit_residue`).
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

  /** `thrown.expect(E.class)` → an ARMING of the rule's own accumulator, plus one `try`/`catch`
    * around the whole test — junit's `ExpectedExceptionStatement` contract transcribed exactly,
    * since `intercept[E] { rest }` cannot express an arming's reach past a loop/block boundary
    * (`ENGINE-LIMITS.md` X5). Refused per §3, one [[Finding]] per site: a stray field reference, or
    * an unreadable overload. @return the body to emit, the OUTER wrapper, the `Decision` sentence. */
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
      // `StandardTraversal`, not a scan of the body's own statements — reaching a site in a loop
      // body is what this lowering is for (§3).
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

  /** ONE `thrown.expect(…)` / `thrown.expectMessage(…)` call, as junit's own append. The operand
    * is bound to a local FIRST (java evaluated it at the call), so a closure appended on a loop's
    * second iteration captures that iteration's matcher. A LITERAL needs no binding. */
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
    // recomputed rather than threaded from the guard pass — same node, same program, same function
    // (`expectAt`), so the same answer.
    val pred: Option[Term] = expectAt(nm, a).toOption.map {
      case Left(Expect.OfClass(t))   => Tree.InstanceOf(exRef, TypeTree(t, o), boolT, o)
      case Left(Expect.OfMatcher(m)) => invoke(bound(s"bpMatcher$k", m), matchesSym, List(exRef), o)
      case Right(ExpectMsg.Contains(t)) =>
        // hamcrest's `TypeSafeMatcher.matches` answers FALSE for a null item.
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

  /** WHICH `expect` overload java resolved, read from the CALLEE's own formal. The class form
    * additionally needs a LITERAL `classOf` — `intercept` takes a type ARGUMENT and a `Class`
    * value is not one, so a `Class`-typed variable is declined. */
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

  /** `@Test def m(): Unit = { … }` → `test("m") { … }`, a statement in the class body. An
    * `expected = classOf[E]` argument becomes `intercept[E] { … }` — never dropped, since running
    * the body bare would PASS while checking nothing. `@Ignore` becomes
    * `test(munit.TestOptions("m").ignore) { … }`, never enabled, since MUnit does not evaluate an
    * ignored body. */
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
      // `test("name") { … }` — TWO argument lists, modelled as nested `Apply` (`Apply.fun` is a
      // `Term`), following `quotes.reflect`.
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
      // the REGISTRATION'S CORE: normally the method's own body, inlined; for a VIRTUAL test it is
      // a CALL to the method that stayed a `def`, so a subclass override dispatches rather than
      // inlining one version. The `ExpectedException` model is REFUSED there and counted: its
      // matcher list is a LOCAL of the frame it governs ([[expectedException]]), which is no
      // longer the frame the registration builds.
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
      // JUnit's own nesting: afters(befores(expectException(invoke))) — `@Before` calls go INSIDE
      // the try so a setup that throws still runs teardown, and the expected-exception check goes
      // inside them both. AHEAD of `@Before`: JUnit's `createTest()` instance runs field
      // initialisers and the constructor body BEFORE setup hooks (probed against junit 4.13,
      // `ENGINE-LIMITS.md` X4). Absent where no instance state exists, or [[planFreshState]] declined.
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
      // JUnit's OUTERMOST statement, applied last (`methodBlock` wraps `withRules` around
      // `withAfters`) — identity where the class declares no such rule, or a guard declined it.
      val rhs = ruleWrap(rhs0)
      // DECISION PROVENANCE, one row per TEST MEMBER: `detail` names what an agent cannot read off
      // the emitted file — that setup/teardown were INLINED, and the fresh-instance rebuild.
      // Universal: JUnit's semantics against MUnit's are a fact about the two frameworks; `suite`/
      // `testMember` are parameters but deliberately do NOT make this `Configured` (class doc).
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
      // the METHOD'S OWN DOCUMENTATION: a `DefDef` carries `leading`, and the statement that
      // replaces it carries it via `Commented`, so the javadoc lands directly above `test("m")`.
      val call0 = Tree.Apply(head, List(rhs), testSym, TypeRepr.NoType, d.origin)
      if d.leading.isEmpty then call0 else Tree.Commented(d.leading, call0)

object TestFrameworkTransform:
  val DefaultSuite = "munit.FunSuite"
  /** the member each converted class declares to rebuild its own instance state before every test
    * — JUnit's `createTest()`, which MUnit has no counterpart for. `bp`-prefixed so it cannot
    * collide with a java member a suite declares. */
  val FreshStateMember = "bpFreshState"
  /** the frontend's name for a java INSTANCE INITIALISER BLOCK (`SpoonTir.classDef`), half of JLS
    * 12.5 step 4. */
  val InitBlockName = "<initblock>"
  /** MUnit's one-time hooks — a fixed contract, not a parameter (class doc). */
  val BeforeAllMember = "beforeAll"
  val AfterAllMember  = "afterAll"

  /** Which of CLAUDE.md §1's three kinds a gap is. An error an agent cannot classify as (a) an
    * engine bug, (b) a phase to configure or (c) a library rule to write costs it a full
    * investigation (§4.45), so every finding carries one. */
  enum Fix(val label: String):
    case EngineRule  extends Fix("a") // a Java/Scala fact — fix the engine, unparameterised
    case PhasePolicy extends Fix("b") // configure an existing phase for this library
    case LibraryRule extends Fix("c") // write a rule only this library could ever need

  /** WHICH of junit's two `ExpectedException.expect` overloads a call resolved to — read from the
    * callee's own formal, never from the argument's shape. */
  enum Expect:
    case OfClass(tpe: TypeRepr)
    case OfMatcher(matcher: Term)

  /** …and the same for `expectMessage`, whose `String` overload MEANS `containsString`. */
  enum ExpectMsg:
    case Contains(text: Term)
    case ByMatcher(matcher: Term)

  /** One test-framework construct this phase did not translate. `at` is a `SymId` rather than a
    * path (§4.56 — ownership decided structurally); a `Symbol`'s origin defaults to
    * `Origin.synthetic`, so reporting from `where` alone would drop every class-level annotation. */
  final case class Finding(construct: String, where: Origin, fix: Fix, advice: String,
                           at: SymId = SymId.None):
    def render: String = s"$construct — (${fix.label}) $advice  (${where.javaPath}:${where.line})"

    /** …as a row of the [[Refused]] lane. The KIND is the GUARD this site was declined at — CLAUDE.md
      * §3's refusal-enumeration rule. The OWNER is the caller's, from [[at]]'s owner chain. */
    def report(owner: String): CheckReport.Finding =
      CheckReport.Finding(Refused, construct, owner, where.javaPath, where.line,
                          s"(${fix.label}) $advice")

  /** THE REFUSAL POPULATION, as a lane (`CLAUDE.md` §3, §5) — required only of a run carrying the
    * phase. `(refused)`, not a bare name: it is a RESIDUE in the `idiom(refused)` family, and this
    * phase's failure mode is SILENT (an unrecognised annotation registers zero tests and reports
    * success), so nothing else can see what it declined. */
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
    * to any numeric type of higher rank. `Char` and `Short` share a rank since neither widens to
    * the other. Deliberately NOT the emitter's copy — that one disambiguates an OVERLOAD at
    * emission, this one rewrites a TREE. */
  val NumericRank: Map[String, Int] = Map(
    "scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2, "scala.Int" -> 3,
    "scala.Long" -> 4, "scala.Float" -> 5, "scala.Double" -> 6)

  /** The types every other type conforms to — MUnit's `Compare` resolves whatever the other operand
    * is, so its constraint is ALREADY VACUOUS there (see [[TestFrameworkTransform.widened]]).
    * Scala roots are here because a port's own retyping can put one on an operand. */
  val Roots: Set[String] =
    Set("java.lang.Object", "scala.Any", "scala.AnyRef", "scala.Matchable")

  /** How many arguments each `org.junit.Assert` member takes WITHOUT java's optional leading
    * `String message` — everything above this count with a leading `String` is that message. */
  val MinArity: Map[String, Int] = Map(
    "assertEquals" -> 2, "assertNotEquals" -> 2, "assertArrayEquals" -> 2,
    "assertSame" -> 2, "assertNotSame" -> 2, "assertTrue" -> 1, "assertFalse" -> 1,
    "assertNull" -> 1, "assertNotNull" -> 1, "fail" -> 0,
    // `assertThrows(Class<T>, ThrowingRunnable)`, so `hasMsg` separates it from its 3-arg overload.
    "assertThrows" -> 2)
