package balticporter.frontend.spoon

import balticporter.catalog.{DiffId, Area}

/** WHAT THE FRONTEND CLAIMS ABOUT EACH SPOON NODE KIND — the half of totality scalac cannot
  * enforce, since `CtElement` has no sealedness. `NodeKindTotalitySpec` asserts this registry
  * equals the derived kind set from the spoon-core jar, so an upgrade adding a kind fails the
  * build until classified here. Carries NO OBLIGATIONS — only what happens today. */
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
    /** reaches `SpoonTir.unsupported`, which throws -- the whole COMPILATION UNIT fails to
      * translate, not one node. Still right for blind spots INSIDE dispatching arms (a
      * `Constant` shape the literal arm doesn't know, an unresolvable try-with-resources
      * declaration, a bodyless lambda) -- none of which is a node KIND this census can hold. */
    case RefusedLoudly

    /** reaches a mint site producing a `Tree.Unportable` marker: refused PER SITE, the rest
      * of the unit translates, and the emission gate refuses to ship until the marker is
      * closed (`DESIGN.md` §6.4). Strictly better than [[RefusedLoudly]], strictly worse
      * than a lowering. */
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

  // THE EXCLUSION SET -- the `Ct*` types under spoon.reflect.{code,declaration} no Java
  // source produces (abstract supertypes, mixins, two enums). Hand-maintained rather than
  // reflective, so a Spoon upgrade produces a DIFF OF NAMES rather than a silently
  // different count.

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

  /** JUDGEMENT CALLS: each has a concrete `Impl` and a `CoreFactory.create*`, but no Java
    * parse ever mints one (programmatic-insertion API / refactoring containers). Listed
    * apart from a strict Impl-concreteness test, which would have wrongly included them. */
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
    // weaker than it reads: enumCase maps ctor arguments via expr(), bypassing coerceArgs'
    // JLS 15.12.4.2 conversions entirely (boxing, narrowing, vararg pack) -- see detail string.
    Kind("CtEnumValue", Positional("SpoonTir.enumCase, from classDef's getEnumValues — the constant's ctor ARGUMENTS bypass coerceArgs and the call dispatch entirely"), scala.None),
    Kind("CtImport", Positional("SpoonTir.harvestHeader — only for its comments"), scala.None),
    Kind("CtJavaDoc", Positional("SpoonTir.triviaOf, through the CtComment arm"), scala.None),
    // getTags is never called; the tag text survives only via the trivia harvest's
    // verbatim source slice (CLAUDE.md §4.58).
    Kind("CtJavaDocTag", Positional("SpoonTir.triviaOf — by verbatim source slice, with no node-level read"), scala.None),
    Kind("CtParameter", Positional("SpoonTir.execDef"), scala.None),
    // Consumed inside `classDef`'s record arm: a component is not a member the walk reaches, it is
    // the DECLARATION the three members javac derives are read from — the backing field, the
    // bare-name accessor and the canonical constructor's parameter at that position.
    Kind("CtRecordComponent", Positional("SpoonTir.recordComponents, from classDef"), Some(c(43))),
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
    // TEXT BLOCK is CtLiteral's subtype, so `literal` runs -- it was misfiled as
    // absorbed-silently before anyone checked WHICH string arrives (JLS 3.10.6's denoted
    // string; catalog JS-E18).
    "CtTextBlock" -> "SpoonTir.literal",
    // a scala `match` IS an expression, so this needed only the arm, not a new image;
    // CtSwitchExpression extends CtAbstractSwitch and NOT CtSwitch, so the statement arm
    // never caught it.
    "CtSwitchExpression" -> "SpoonTir.switchExpr",
    // every yield enters the STATEMENT dispatch; the TAIL one is peeled into the arm's
    // value (SpoonTir.armValue). Spoon also wraps an arrow-statement arm's expression in
    // one -- undone by SpoonTir.caseBody before dispatch, since JLS 14.21 says that
    // wrapper is not java.
    "CtYieldStatement" -> "SpoonTir.stmtKind, then SpoonTir.armValue (tail) / SpoonTir.caseBody (Spoon's arrow-statement wrapper)",
    // the case-label WRAPPER; Lowered even though half of what it can wrap is refused --
    // WHICH pattern it holds is each pattern kind's own row's business.
    "CtCasePattern" -> "SpoonTir.caseLabel",
    "CtNewArray" -> "SpoonTir.newArray", "CtNewClass" -> "SpoonTir.anonClass",
    "CtOperatorAssignment" -> "SpoonTir.stmtKind / exprNoCast", "CtReturn" -> "SpoonTir.stmtKind",
    "CtSuperAccess" -> "SpoonTir.exprNoCast", "CtSwitch" -> "SpoonTir.switchStmt",
    "CtSynchronized" -> "SpoonTir.stmtKind", "CtThisAccess" -> "SpoonTir.exprNoCast",
    "CtThrow" -> "SpoonTir.stmtKind", "CtTry" -> "SpoonTir.tryStmt",
    "CtTryWithResource" -> "SpoonTir.tryStmt", "CtTypeAccess" -> "SpoonTir.exprNoCast",
    "CtUnaryOperator" -> "SpoonTir.exprNoCast", "CtVariableRead" -> "SpoonTir.exprNoCast",
    "CtVariableWrite" -> "SpoonTir.exprNoCast", "CtWhile" -> "SpoonTir.stmtKind",
    "CtAnnotationType" -> "SpoonTir.typeFlags", "CtAnonymousExecutable" -> "SpoonTir.classDef",
    // TWO walks: the declaration walk (class reached from its declaring type) and stmtArm
    // (a method-LOCAL class, JS-C30) -- naming only the first made this claim read weaker
    // than it is.
    "CtClass" -> "SpoonTir.classDef, from the declaration walk and from SpoonTir.stmtArm for a method-LOCAL class",
    "CtConstructor" -> "SpoonTir.execDef",
    "CtEnum" -> "SpoonTir.classDef / enumCase", "CtField" -> "SpoonTir.classDef / fieldFlags",
    "CtInterface" -> "SpoonTir.typeFlags", "CtMethod" -> "SpoonTir.execDef",
    // CtAnnotationMethod extends CtMethod, so execDef always took it, but discarded the
    // element's DEFAULT (JLS 9.6.2) -- now read and rendered as a constructor parameter
    // (ENGINE-LIMITS.md T22).
    "CtAnnotationMethod" -> ("SpoonTir.execDef, with annotationDefault for JLS 9.6.2's `default` " +
                             "clause -> TirEmitter.classDef1's annotation arm, as a class parameter"),
  ).map((n, by) => Kind(n, Lowered(by), scala.None)) ++ List(
    // Lowered on a stricter reading: CtRecord extends CtClass so the class arm always
    // took it, but produced a class missing javac's derived members, a mis-ordered
    // canonical ctor and no nested-record ctor at all. All now derived from the
    // components (probed against javac).
    Kind("CtRecord",
      Lowered("SpoonTir.classDef — typeFlags.isRecord, recordComponents, createCanonicalConstructorIfMissing, " +
              "canonicalised and accessorBodies; TirEmitter.recordMembers writes equals/hashCode/toString/unapply"),
      Some(c(43))),
    // became lowerable once the row above derived an extractor over the ACCESSORS
    // (JLS 14.30.1). Reached only as a CASE LABEL; as an instanceof operand the whole
    // expression is refused (JS-G21 -- the binding's flow scope, not the pattern).
    Kind("CtRecordPattern",
      Lowered("SpoonTir.recordPattern, from caseLabel — Tree.RecordPattern, with Tree.BindPattern " +
              "at a component pattern JLS 14.30.2 makes unconditional. Marks per site where the " +
              "record is one this run does not MODEL: the extractor is derived into the emitted " +
              "record's companion, and a java record read out of a class file has none"),
      Some(s(10))),
  )

  /** kinds NOTHING reaches. Four are `AbsorbedSilently`: each extends a kind the frontend
    * DOES dispatch on, so an ordered `match` takes it at the supertype's arm and the
    * construct degrades with a green compile. `CtTextBlock` is the sharpest example. */
  val absent: List[Kind] = List(
    Kind("CtAnnotationFieldAccess", Absent(AbsorbedSilently, "extends CtVariableRead, not CtFieldAccess, so the variable arm takes it and the TARGET is dropped"), scala.None),

    // the marker for an instanceof PATTERN is minted at the WHOLE instanceof (a boolean
    // expression), not the type operand, which cannot carry a term marker -- same
    // refusal (ENGINE-LIMITS.md T18), smaller SIZE.
    Kind("CtTypePattern", Absent(MarkedUnportable, "reached as the instanceof right operand (JLS 15.20.2); the binding is FLOW-scoped and is refused, and the marker stands at the enclosing boolean expression"), Some(g(21))),

    // PROBED: no java source this parser accepts produces one. `_` in a TYPE PATTERN
    // position builds as a CtTypePattern named `_` (lowered normally); `_` nested in a
    // record pattern raises spoon's own JLSViolation or yields no node.
    Kind("CtUnnamedPattern", Absent(NeverVisited, "no source this parser accepts builds one: `_` in a TYPE PATTERN position is a CtTypePattern named `_`, and `_` nested in a record pattern raises spoon's own JLSViolation (instanceof) or yields no node (case label)"), Some(s(10))),

    Kind("CtPackage", Absent(NeverVisited, "the builder enters at the top-level types; package names are recovered from qualified names"), scala.None),
    Kind("CtPackageDeclaration", Absent(NeverVisited, "not read; its comments survive only through the positional file-header harvest"), scala.None),
    Kind("CtReceiverParameter", Absent(NeverVisited, "execDef reads getParameters, which excludes it; any annotation written on the receiver is dropped with it"), scala.None),
    Kind("CtModule", Absent(NeverVisited, "nothing walks the module tree; a module-info.java reaches nothing"), scala.None),
    Kind("CtModuleRequirement", Absent(NeverVisited, "as CtModule"), scala.None),
    Kind("CtPackageExport", Absent(NeverVisited, "as CtModule"), scala.None),
    Kind("CtProvidedService", Absent(NeverVisited, "as CtModule"), scala.None),
    Kind("CtUsedService", Absent(NeverVisited, "as CtModule"), scala.None),
  )

  /** every kind a Java source can produce — the set `NodeKindTotalitySpec` holds the jar to. */
  val registry: List[Kind] = lowered ++ positional ++ absent

  val byName: Map[String, Kind] = registry.map(k => k.name -> k).toMap

  // THE REFERENCE REGISTRY -- spoon.reflect.reference, kept apart from [[registry]] since it is
  // derived from a different jar scan (a merged total would answer two questions with one
  // number). Same TOTALITY ARGUMENT: `SpoonTir.tpe`'s match is ORDERED and its final arm is the
  // supertype's, so an added reference kind is silently absorbed as an ordinary class reference.

  /** the reference types that are supertypes or mixins, so no parse produces one.
    *
    * Hand-maintained for [[excluded]]'s reason and grouped by the test that put each here: two have
    * an `Impl` that is declared `abstract` and one has no `Impl` at all. */
  val refMarkersWithAbstractImpl: Set[String] = Set("CtReference", "CtVariableReference")

  /** no `Ct*Impl` exists anywhere under `spoon/support/reflect/reference/` — a pure mixin. */
  val refMarkersWithoutImpl: Set[String] = Set("CtActualTypeContainer")

  val refExcluded: Set[String] = refMarkersWithAbstractImpl ++ refMarkersWithoutImpl

  /** the FIVE reference kinds `SpoonTir.tpe` dispatches on, and the nine it does not. Same
    * [[Claim]] vocabulary: `Lowered` is a `tpe` arm, `Positional` is read by a parent's arm
    * without reaching `tpe`, `Absent` is unreached. No `MarkedUnportable`/`RefusedLoudly`
    * here (a reference has no body to refuse). */
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

  /** the REGISTRY name for a node the parser actually built. Keyed on Spoon's INTERFACE names
    * (`CtSwitchExpression`), not the impl class (`...Impl`) — resolved structurally, not by
    * stripping `Impl`. Among the interfaces a class carries, the MOST SPECIFIC registered one
    * wins. Falls back to the class's own simple name outside the registry, which
    * `NodeKindTotalitySpec` exists to fail on. */
  def nameOf(cls: Class[?]): String = nameIn(cls, byName)

  /** the same rule against the REFERENCE registry -- SpoonTir.tpe's key. Its own resolver
    * rather than a widened [[nameOf]]: a merged map would let a node kind answer for a
    * reference, and CtWildcardReferenceImpl extends CtTypeParameterReferenceImpl, so the
    * most-specific rule matters here too. */
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
