package balticporter.frontend.spoon

import balticporter.catalog.{DiffId, Area}

/** WHAT THE FRONTEND CLAIMS ABOUT EACH SPOON NODE KIND — the half of totality scalac cannot give.
  *
  * The TIR side is already total and the compiler enforces it: `Tree` is a sealed trait and the
  * emitter's dispatch ends with no default arm, so a new node kind is a compile error. The JAVA side
  * has no such guarantee. `CtElement` is a Java interface hierarchy with no sealedness, so a `match`
  * over it is exhaustive only by inspection, and the arms are ordered — which means a kind that
  * extends another kind is absorbed by its SUPERTYPE's arm, silently, and looks handled from every
  * angle except the emitted output.
  *
  * This registry is that inspection, written down. `NodeKindTotalitySpec` derives the kinds from the
  * spoon-core jar and asserts that the derived set and this one are EQUAL — so a Spoon upgrade that
  * adds a node kind fails the build until somebody says, here, what the frontend does with it. That
  * is the feature and it is also the only thing in the design that will annoy an unrelated
  * dependency bump.
  *
  * IT CARRIES NO OBLIGATIONS. A [[Claim]] says what happens to a kind today and nothing about what a
  * lowering arm OWES. The obligation mechanism — consult every difference the catalog attaches to
  * this kind, and fail when an arm returns without consulting one — needs a wrapper at the dispatch
  * and a log to record into, and neither exists. A registry that carried unenforced obligations
  * would read as a guarantee the engine does not make.
  */
object SpoonKinds:

  /** what the frontend does with a node of this kind. */
  enum Claim:
    /** a named arm lowers it; `by` is the symbol that does */
    case Lowered(by: String)
    /** never dispatched on: consumed inside a PARENT's arm, which is correct and is not a gap.
      * `by` is the consuming symbol, so the claim can be checked rather than believed */
    case Positional(by: String)
    /** nothing lowers it. [[Absence]] says what happens instead, which is the whole diagnostic
      * value — a construct that is refused loudly and one that is silently absorbed are the same
      * size on this list and nothing like each other in a port */
    case Absent(how: Absence, detail: String)

  /** the three ways a kind can be unhandled, in descending order of how loudly it fails. */
  enum Absence:
    /** reaches `SpoonTir.unsupported`, which throws — so the whole COMPILATION UNIT fails to
      * translate, not one node. That is the honest cost of one `record` in a 135-file library.
      *
      * What is left here after `DESIGN.md` §6.2's marker landed are the refusal points whose SHAPE
      * a term-level marker cannot take: a `Constant`, a `ValDef`, the type operand of an
      * `instanceof`. Each is a real mint site and each wants a marker of its own kind; none of them
      * is one an expression wrapper can stand in for, and pretending otherwise would put a term
      * where the tree needs a declaration */
    case RefusedLoudly

    /** reaches a mint site that produces a `Tree.Unportable` marker: the node is refused PER SITE,
      * the rest of the unit translates, and the emission gate refuses to ship the port until the
      * marker is closed or the declaration that uses it is dropped (`DESIGN.md` §6.4).
      *
      * Strictly better than [[RefusedLoudly]] and strictly worse than a lowering. The port still
      * does not ship, which is the point — but the failure is now the size of the construct rather
      * than the size of the file, and adopting a new syntax family becomes an incremental measured
      * step instead of an all-or-nothing one */
    case MarkedUnportable
    /** a SUPERTYPE's arm takes it and the construct degrades with no error, no moved count and
      * nothing in the emitted file to say a Java construct had ever been there. The dangerous one */
    case AbsorbedSilently
    /** the walk never reaches it: the builder enters at the top-level types, so nothing under the
      * package tree or the module tree is visited at all */
    case NeverVisited

  /** a kind, its claim, and the catalog row that names the difference — a POINTER, not a consult.
    * Nothing is discharged by writing one down; it exists so the two artifacts can be joined when a
    * run reports "this kind was refused" and a reader asks which known difference that is. */
  final case class Kind(name: String, claim: Claim, catalog: Option[DiffId])

  private def s(n: Int) = DiffId(Area.S, n)
  private def c(n: Int) = DiffId(Area.C, n)
  private def g(n: Int) = DiffId(Area.G, n)

  // -------------------------------------------------------------------------------------------
  // THE EXCLUSION SET — hand-maintained, and deliberately so.
  //
  // These are the `Ct*` types under `spoon.reflect.{code,declaration}` that NO Java source
  // produces: the abstract supertypes and the mixin interfaces, plus the two members of those
  // packages that are `enum`s rather than node interfaces.
  //
  // A reflective predicate (`isInterface && a concrete Impl exists && …`) would be cheaper and is
  // WRONG here, for one reason: it makes the exclusion invisible. The single thing this whole
  // mechanism exists to surface is what somebody decided not to handle, so a Spoon upgrade must
  // produce a DIFF OF NAMES rather than a silently different count. The names are grouped by the
  // test that put them here, so a reader can re-derive any line without re-deriving the list.
  // -------------------------------------------------------------------------------------------

  /** no `Ct*Impl` exists anywhere under `spoon/support/reflect/` — a pure mixin. */
  val markersWithoutImpl: Set[String] = Set(
    "CtAbstractInvocation", "CtAbstractSwitch", "CtBodyHolder", "CtCFlowBreak", "CtLabelledFlowBreak",
    "CtPattern", "CtRHSReceiver", "CtResource",
    "CtCodeSnippet", "CtFormalTypeDeclarer", "CtModifiable", "CtModuleDirective", "CtMultiTypedElement",
    "CtSealable", "CtShadowable", "CtTypeInformation", "CtTypeMember", "CtTypedElement", "CtVariable",
  )

  /** an `Impl` exists and is declared `abstract` — a supertype in the node hierarchy. */
  val markersWithAbstractImpl: Set[String] = Set(
    "CtArrayAccess", "CtCodeElement", "CtExpression", "CtFieldAccess", "CtLoop", "CtStatement",
    "CtTargetedExpression", "CtVariableAccess",
    "CtElement", "CtExecutable", "CtNamedElement", "CtType",
  )

  /** JUDGEMENT CALLS, named as such: each has a concrete `Impl` and a `CoreFactory.create*`, and no
    * Java source parse ever mints one. The snippets are the programmatic-insertion API; a
    * `CtStatementList` is the supertype of `CtCase` and a refactoring container, never a parse node.
    * A strict Impl-concreteness test would have put all three in the producible set, which is why
    * they are listed apart rather than folded in. */
  val markersByJudgement: Set[String] = Set(
    "CtCodeSnippetExpression", "CtCodeSnippetStatement", "CtStatementList",
  )

  /** `enum`s that live in these packages and are not node kinds at all. */
  val notNodeKinds: Set[String] = Set("CtAnnotatedElementType", "CtImportKind")

  val excluded: Set[String] =
    markersWithoutImpl ++ markersWithAbstractImpl ++ markersByJudgement ++ notNodeKinds

  // -------------------------------------------------------------------------------------------
  // THE REGISTRY — every kind a Java source can produce, and what the frontend does with it.
  // -------------------------------------------------------------------------------------------

  import Claim.*
  import Absence.*

  /** kinds the frontend never dispatches on because a PARENT's arm consumes them. Not a gap —
    * but each one names the consuming symbol, because "handled positionally" is otherwise a claim
    * with nothing behind it, and one of these turned out to be weaker than it reads (see
    * `CtJavaDocTag`). */
  val positional: List[Kind] = List(
    Kind("CtAnnotation", Positional("SpoonTir.annotationsOf"), scala.None),
    Kind("CtCase", Positional("SpoonTir.switchStmt"), scala.None),
    Kind("CtCatch", Positional("SpoonTir.tryStmt"), scala.None),
    Kind("CtCatchVariable", Positional("SpoonTir.tryStmt, then SpoonTir.defineLocal"), scala.None),
    Kind("CtCompilationUnit", Positional("SpoonTir.harvestHeader"), scala.None),
    // …and this one is weaker than it reads, in the same way `CtJavaDocTag` is. `enumCase` maps the
    // constant's constructor ARGUMENTS with `bt.exprOf` — `expr` on each argument — so the
    // `CtNewClass`/`CtConstructorCall` that holds them never enters the expression dispatch at all.
    // The consequence is not a missing consult, it is a missing TRANSLATION: java performs its
    // method-invocation conversion at those arguments (JLS 15.12.4.2) and this path performs none —
    // no boxing, no narrowing, no vararg pack — while `coerceArgs`, which does all three and owes
    // the `everyCall` rows, is never reached. Wiring the consult alone would discharge an
    // obligation the arm does not meet, which is the one failure `CatalogLog` says it cannot
    // detect; wiring the arm properly means the anonymous-class rows attached to that same scope,
    // one of which (`CtAnonymousExecutable` in a constant's body) this walk drops outright — it
    // collects `CtField` and `CtMethod` and nothing else. Recorded here, where the claim is, rather
    // than half-instrumented.
    Kind("CtEnumValue", Positional("SpoonTir.enumCase, from classDef's getEnumValues — the constant's ctor ARGUMENTS bypass coerceArgs and the call dispatch entirely"), scala.None),
    Kind("CtImport", Positional("SpoonTir.harvestHeader — only for its comments"), scala.None),
    Kind("CtJavaDoc", Positional("SpoonTir.triviaOf, through the CtComment arm"), scala.None),
    // NOT a node-level read: `getTags` is never called. The tag text survives only because the
    // trivia harvest slices the comment's raw source span verbatim (CLAUDE.md §4.58's "slice from
    // the buffer, never re-print"), which is the right answer and is not the same as handling it.
    Kind("CtJavaDocTag", Positional("SpoonTir.triviaOf — by verbatim source slice, with no node-level read"), scala.None),
    Kind("CtParameter", Positional("SpoonTir.execDef"), scala.None),
    Kind("CtTypeParameter", Positional("SpoonTir.mintTypeParams / boundsOf / erasureOfFormal"), scala.None),
  )

  /** kinds a named arm lowers. */
  val lowered: List[Kind] = List(
    "CtArrayRead" -> "SpoonTir.exprNoCast", "CtArrayWrite" -> "SpoonTir.exprNoCast",
    "CtAssert" -> "SpoonTir.stmtKind", "CtAssignment" -> "SpoonTir.exprNoCast / stmtKind",
    "CtBinaryOperator" -> "SpoonTir.exprNoCast", "CtBlock" -> "SpoonTir.blockOf",
    "CtBreak" -> "SpoonTir.stmtKind", "CtComment" -> "SpoonTir.triviaOf",
    "CtConditional" -> "SpoonTir.exprNoCast", "CtConstructorCall" -> "SpoonTir.exprNoCast",
    "CtContinue" -> "SpoonTir.stmtKind", "CtDo" -> "SpoonTir.stmtKind",
    "CtExecutableReferenceExpression" -> "SpoonTir.methodRef", "CtFieldRead" -> "SpoonTir.fieldAccess",
    "CtFieldWrite" -> "SpoonTir.fieldAccess", "CtFor" -> "SpoonTir.stmtKind",
    "CtForEach" -> "SpoonTir.stmtKind", "CtIf" -> "SpoonTir.stmtKind",
    "CtInvocation" -> "SpoonTir.invocation", "CtLambda" -> "SpoonTir.exprNoCast",
    "CtLiteral" -> "SpoonTir.literal", "CtLocalVariable" -> "SpoonTir.defineLocal",
    // A TEXT BLOCK is `CtLiteral`'s subtype and `literal` is the arm that runs, so it is LOWERED
    // and was only ever filed as absorbed-silently because nobody had asked WHICH STRING arrives.
    // `TextBlockSpec` asked: `getValue` hands back JLS 3.10.6's denoted string — incidental
    // whitespace stripped, terminators normalised, escapes applied — and the emitter re-escapes it
    // (L1), so the SHAPE changes and the VALUE does not. Catalog `JS-E18` records the non-difference
    // with that spec as its evidence.
    "CtTextBlock" -> "SpoonTir.literal",
    // JLS 15.28 — a scala `match` IS an expression, so the image was `Tree.Match` all along and
    // what was missing was the arm. `CtSwitchExpression` extends `CtExpression` and
    // `CtAbstractSwitch` and NOT `CtSwitch`, which is exactly why the statement arm could never
    // have caught it.
    "CtSwitchExpression" -> "SpoonTir.switchExpr",
    // …and its jump. Every `yield` enters the STATEMENT dispatch and this arm mints a `Tree.Yield`;
    // the TAIL one is then peeled into the arm's value by `SpoonTir.armValue`, because that is what
    // a scala `match` arm already means. Spoon also wraps an arrow-form STATEMENT arm's expression
    // in one, which JLS 14.21 says is not java — `SpoonTir.caseBody` undoes that before the
    // dispatch sees it.
    "CtYieldStatement" -> "SpoonTir.stmtKind, then SpoonTir.armValue (tail) / SpoonTir.caseBody (Spoon's arrow-statement wrapper)",
    "CtNewArray" -> "SpoonTir.newArray", "CtNewClass" -> "SpoonTir.anonClass",
    "CtOperatorAssignment" -> "SpoonTir.stmtKind / exprNoCast", "CtReturn" -> "SpoonTir.stmtKind",
    "CtSuperAccess" -> "SpoonTir.exprNoCast", "CtSwitch" -> "SpoonTir.switchStmt",
    "CtSynchronized" -> "SpoonTir.stmtKind", "CtThisAccess" -> "SpoonTir.exprNoCast",
    "CtThrow" -> "SpoonTir.stmtKind", "CtTry" -> "SpoonTir.tryStmt",
    "CtTryWithResource" -> "SpoonTir.tryStmt", "CtTypeAccess" -> "SpoonTir.exprNoCast",
    "CtUnaryOperator" -> "SpoonTir.exprNoCast", "CtVariableRead" -> "SpoonTir.exprNoCast",
    "CtVariableWrite" -> "SpoonTir.exprNoCast", "CtWhile" -> "SpoonTir.stmtKind",
    "CtAnnotationType" -> "SpoonTir.typeFlags", "CtAnonymousExecutable" -> "SpoonTir.classDef",
    // TWO walks, and naming only the first is what made this claim weaker than it read: the
    // declaration walk takes a class reached from its declaring type, and `stmtArm` takes the one
    // that stands in a member's BLOCK — java's method-LOCAL class, `JS-C30`.
    "CtClass" -> "SpoonTir.classDef, from the declaration walk and from SpoonTir.stmtArm for a method-LOCAL class",
    "CtConstructor" -> "SpoonTir.execDef",
    "CtEnum" -> "SpoonTir.classDef / enumCase", "CtField" -> "SpoonTir.classDef / fieldFlags",
    "CtInterface" -> "SpoonTir.typeFlags", "CtMethod" -> "SpoonTir.execDef",
  ).map((n, by) => Kind(n, Lowered(by), scala.None))

  /** kinds NOTHING reaches. The list the whole mechanism exists to keep honest.
    *
    * Four of these are `AbsorbedSilently`, and those are the ones to read first: each extends a kind
    * the frontend DOES dispatch on, so an ordered `match` takes it at the supertype's arm and the
    * construct degrades with a green compile. `CtTextBlock` is the sharpest — it extends
    * `CtLiteral`, so a Java 15 text block collapses into an ordinary string constant whose payload
    * holds raw newlines, and correctness then rests entirely on the emitter's re-escaping. */
  val absent: List[Kind] = List(
    Kind("CtAnnotationFieldAccess", Absent(AbsorbedSilently, "extends CtVariableRead, not CtFieldAccess, so the variable arm takes it and the TARGET is dropped"), scala.None),
    Kind("CtRecord", Absent(AbsorbedSilently, "extends CtClass, so classDef treats it as a plain class and typeFlags has no isRecord — and PROBED (AbsorbedProbeSpec) the result is worse than a plain class: the components and accessors DO arrive, and the emitted class extends java.lang.Record without implementing its three abstract members, which RefChecks rejects on the day the port reaches zero typer errors"), Some(c(43))),
    Kind("CtAnnotationMethod", Absent(AbsorbedSilently, "extends CtMethod — and PROBED (AbsorbedProbeSpec) the ELEMENT ITSELF is dropped, not merely its `default` clause: an emitted @interface has no members at all, so a ported annotation cannot take the argument T16 now lets a type carry"), scala.None),

    Kind("CtTypePattern", Absent(RefusedLoudly, "reached as the instanceof right operand, which refuses — and that site is one of the four whose SHAPE a term-level marker cannot take"), Some(g(21))),
    Kind("CtRecordPattern", Absent(RefusedLoudly, "the instanceof right operand still THROWS — a term marker cannot stand where the tree wants a type operand; as a CASE LABEL it now mints a marker instead, and the loudest reachable outcome is what this claim states"), Some(s(10))),
    Kind("CtCasePattern", Absent(MarkedUnportable, "a CtExpression, not a CtPattern, so switchStmt's case-expression map refuses it"), Some(s(10))),
    Kind("CtUnnamedPattern", Absent(RefusedLoudly, "both pattern paths above — the case-label one marks, the instanceof one still throws"), Some(s(10))),

    Kind("CtPackage", Absent(NeverVisited, "the builder enters at the top-level types; package names are recovered from qualified names"), scala.None),
    Kind("CtPackageDeclaration", Absent(NeverVisited, "not read; its comments survive only through the positional file-header harvest"), scala.None),
    Kind("CtReceiverParameter", Absent(NeverVisited, "execDef reads getParameters, which excludes it; any annotation written on the receiver is dropped with it"), scala.None),
    Kind("CtRecordComponent", Absent(NeverVisited, "getRecordComponents is never called — see CtRecord"), Some(c(43))),
    Kind("CtModule", Absent(NeverVisited, "nothing walks the module tree; a module-info.java reaches nothing"), scala.None),
    Kind("CtModuleRequirement", Absent(NeverVisited, "as CtModule"), scala.None),
    Kind("CtPackageExport", Absent(NeverVisited, "as CtModule"), scala.None),
    Kind("CtProvidedService", Absent(NeverVisited, "as CtModule"), scala.None),
    Kind("CtUsedService", Absent(NeverVisited, "as CtModule"), scala.None),
  )

  /** every kind a Java source can produce — the set `NodeKindTotalitySpec` holds the jar to. */
  val registry: List[Kind] = lowered ++ positional ++ absent

  val byName: Map[String, Kind] = registry.map(k => k.name -> k).toMap

  // -------------------------------------------------------------------------------------------
  // THE REFERENCE REGISTRY — `spoon.reflect.reference`, and the FOURTH obligation surface's keys.
  //
  // A THIRD package, kept apart from [[registry]] rather than folded into it, and the split is
  // the same one `NodeKindTotalitySpec`'s two directions are about: the node registry is derived
  // from `spoon.reflect.{code,declaration}` and its total is asserted against that jar scan, so a
  // fourteen-name addition would make one number answer for two questions and leave a Spoon
  // upgrade's diff unreadable. Two registries, two scans, two diffs.
  //
  // The TOTALITY ARGUMENT is identical and is what makes this worth having at all:
  // `CtTypeReference` is an ordinary interface with four sub-interfaces, `SpoonTir.tpe`'s match is
  // ORDERED, and its final `case r` is the supertype's arm — so a reference kind Spoon adds
  // tomorrow is absorbed there silently and renders as an ordinary class reference. That is the
  // `CtTextBlock` shape one package over, at the surface where a wrong answer is a TYPE.
  // -------------------------------------------------------------------------------------------

  /** the reference types that are supertypes or mixins, so no parse produces one.
    *
    * Hand-maintained for [[excluded]]'s reason and grouped by the test that put each here: two have
    * an `Impl` that is declared `abstract` and one has no `Impl` at all. */
  val refMarkersWithAbstractImpl: Set[String] = Set("CtReference", "CtVariableReference")

  /** no `Ct*Impl` exists anywhere under `spoon/support/reflect/reference/` — a pure mixin. */
  val refMarkersWithoutImpl: Set[String] = Set("CtActualTypeContainer")

  val refExcluded: Set[String] = refMarkersWithAbstractImpl ++ refMarkersWithoutImpl

  /** the FIVE reference kinds `SpoonTir.tpe` dispatches on, and the nine it does not.
    *
    * The claim vocabulary is [[Claim]]'s, unchanged, and it reads the same way: `Lowered` is an arm
    * of the type dispatch, `Positional` is a reference a PARENT's arm reads without ever handing it
    * to `tpe`, and `Absent` is one nothing reaches. What differs from the node registry is which
    * answers are even possible here — a reference has no body to refuse, so there is no
    * `MarkedUnportable` and no `RefusedLoudly` on this list, and `AbsorbedSilently` is reserved for
    * exactly the failure the totality spec exists to catch. */
  val references: List[Kind] = List(
    Kind("CtTypeReference", Lowered("SpoonTir.tpe's final arm — a class type, raw-filled when its actuals are empty"), scala.None),
    Kind("CtArrayTypeReference", Lowered("SpoonTir.tpe's array arm -> scala.Array[component]"), scala.None),
    Kind("CtIntersectionTypeReference", Lowered("SpoonTir.tpe's intersection arm -> AndType"), scala.None),
    Kind("CtTypeParameterReference", Lowered("SpoonTir.tpe's type-variable arm, through resolveTypeParam"), Some(g(12))),
    // ORDER matters and is why this is a separate row rather than a note: `CtWildcardReferenceImpl`
    // EXTENDS `CtTypeParameterReferenceImpl`, so the wildcard arm has to come first in the match and
    // `nameOf`'s most-specific rule has to answer `CtWildcardReference` here. Registered as its own
    // kind, both facts are checkable instead of believed.
    Kind("CtWildcardReference", Lowered("SpoonTir.tpe's wildcard arm — bounds, and the `? super Object` collapse"), Some(g(1))),

    Kind("CtExecutableReference", Positional("SpoonTir.invocation / methodSym / coerceArgs — read for its declaration and its formals, never lowered as a type"), scala.None),
    Kind("CtFieldReference", Positional("SpoonTir.fieldAccess, through fieldSym"), scala.None),
    Kind("CtLocalVariableReference", Positional("SpoonTir.resolveVar, from the variable-read arm"), scala.None),
    Kind("CtParameterReference", Positional("SpoonTir.resolveVar, from the variable-read arm"), scala.None),
    Kind("CtCatchVariableReference", Positional("SpoonTir.resolveVar — the binder itself is minted by tryStmt"), scala.None),
    Kind("CtUnboundVariableReference", Positional("SpoonTir.resolveVar's fallback, which mints an external `?var$<name>` symbol — the reference Spoon builds when it cannot resolve the declaration"), scala.None),
    Kind("CtPackageReference", Positional("SpoonTir.typeKey reads getQualifiedName, which carries the package; the reference itself is never visited"), scala.None),

    Kind("CtModuleReference", Absent(NeverVisited, "nothing walks the module tree — see CtModule"), scala.None),
    Kind("CtTypeMemberWildcardImportReference", Absent(NeverVisited, "an `import static X.*` target; SpoonTir.harvestHeader reads a CtImport only for its comments, and fully-qualified emission removes the question the import answered"), Some(c(5))),
  )

  val byRefName: Map[String, Kind] = references.map(k => k.name -> k).toMap

  /** the REGISTRY name for a node the parser actually built.
    *
    * The registry is keyed on Spoon's INTERFACE names (`CtSwitchExpression`) and the parser hands
    * back its own implementations (`CtSwitchExpressionImpl`), so a marker minted from a live node
    * would name a class this list has never heard of — and the join between "the run refused n
    * constructs" and "here is what the frontend does with that kind" is the whole reason both
    * artifacts exist.
    *
    * Resolved structurally rather than by stripping `Impl`: an implementation may sit two classes
    * below its interface, and the string test would then answer a name nobody registered. The
    * `Impl` shortcut is tried FIRST only because it is exact when it matches — a hit is checked
    * against [[byName]], never assumed. Among the interfaces a class carries, the MOST SPECIFIC
    * registered one wins: `CtSwitchExpressionImpl` implements both `CtSwitchExpression` and
    * `CtExpression`, and answering the supertype would say the node is one the frontend handles.
    *
    * Falls back to the class's own simple name, which is honest: a kind outside the registry is
    * exactly what `NodeKindTotalitySpec` exists to fail on, and inventing a registered name for it
    * here would hide that. */
  def nameOf(cls: Class[?]): String = nameIn(cls, byName)

  /** the same rule against the REFERENCE registry — `SpoonTir.tpe`'s key, and the fourth obligation
    * surface's.
    *
    * Its own resolver rather than a widened [[nameOf]], because the two registries answer two
    * questions and a merged map would let a node kind answer for a reference. The most-specific
    * rule matters more here than it does there: `CtWildcardReferenceImpl` extends
    * `CtTypeParameterReferenceImpl`, so a shortcut answering the first registered supertype would
    * key every wildcard as a type variable and hand it the wrong arm's obligations. */
  def refNameOf(cls: Class[?]): String = nameIn(cls, byRefName)

  /** ONE resolver for both registries (`ENGINE-LIMITS.md` F8): the `Impl` shortcut, the structural
    * walk and the most-specific tie-break are the same three rules whichever taxonomy is asking,
    * and a copy is the one that would not gain the next fix. */
  private def nameIn(cls: Class[?], known: Map[String, Kind]): String =
    val stripped = cls.getSimpleName.stripSuffix("Impl")
    if known.contains(stripped) then stripped
    else
      def all(c: Class[?]): List[Class[?]] =
        if c == null then Nil
        else c.getInterfaces.toList.flatMap(i => i :: all(i)) ++ all(c.getSuperclass)
      val candidates = all(cls).distinct.filter(c => known.contains(c.getSimpleName))
      candidates
        .find(c => candidates.forall(d => (d eq c) || !c.isAssignableFrom(d)))
        .map(_.getSimpleName)
        .getOrElse(stripped)

  /** the kinds nothing reaches, by how loudly they fail. Printed by the spec; the ONLY honest way
    * to state the size of the gap, because a total that mixes a loud refusal with a silent
    * absorption is a number that hides the half that matters. */
  def absentBy(how: Absence): List[String] =
    absent.collect { case Kind(n, Claim.Absent(h, _), _) if h == how => n }.sorted
