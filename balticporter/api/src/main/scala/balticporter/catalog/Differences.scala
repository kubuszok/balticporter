package balticporter.catalog

import Area.*
import Severity.*
import Status.*
import Twin.*
import FixKind.*
import Attaches.*

/** The language half of the catalog -- `JS-{E,S,C,G}`. Every status is RE-DERIVED against
  * the engine (via `scripts/catalog-status.sh`, then the cited SYMBOL, then a fixture) --
  * never copied from a document. The count is DERIVED ([[all]]`.size`), written nowhere.
  */
object Differences:

  private def eId(n: Int) = DiffId(E, n)
  private def sId(n: Int) = DiffId(S, n)
  private def cId(n: Int) = DiffId(C, n)
  private def gId(n: Int) = DiffId(G, n)

  private def el(id: String) = EngineLimit(id)

  // ATTACHMENT -- three shared "not instrumented yet" answers, one per area whose
  // obligations are not declared. Written down rather than defaulted, so a row added
  // tomorrow gets no claim nobody made; `catalog(unmechanised)` counts them.

  /** the four `Tree` kinds a LOOP is, as one attachment — a chain, not a list (a list is per-library
    * policy, rejected by `DifferenceTakesNoParameterSpec`). Both `JS-S01`/`JS-S03` route through
    * `TirEmitter.loopWithJumps`, so the family is named once here rather than twice. */

  private val everyLoop: Attaches =
    Both(Rendered("While"), Both(Rendered("For"), Both(Rendered("ForEach"), Rendered("DoWhile"))))

  /** the three DECLARATION kinds an emitted member is one of, as one attachment. The four
    * VISIBILITY rows (JS-C47..50) are one decision (`Visibility.decide` +
    * `TirEmitter.declVisibility`), so all three carry it (`ENGINE-LIMITS.md` F8). */
  private val everyDeclaration: Attaches =
    Both(Rendered("ClassDef"), Both(Rendered("DefDef"), Rendered("ValDef")))

  /** a STATIC MEMBER reached through a TYPE NAME, at both field dispatches.
    *
    * `Foo.CONST` is a `CtFieldRead` and `Foo.CONST = x` a `CtFieldWrite`, and both reach
    * `SpoonTir.fieldAccess`. Java inherits a static through `extends` AND through `implements`, so
    * the re-qualification a companion needs is owed at either. */
  private val everyStaticFieldRead: Attaches =
    Both(Lowered("CtFieldRead", Dispatch.Expression), Lowered("CtFieldWrite", Dispatch.Expression))

  /** every dispatch at which a value flows into a DECLARED TYPE — java's assignment conversion
    * (JLS 5.2), through `SpoonTir.coerce`, but a slot is not one node kind: seven distinct kinds
    * reach it (e.g. a field initialiser). Convergence point: `SpoonTir.slotConsults`, stated ONCE
    * (`ENGINE-LIMITS.md` F8). */

  /** the CALL dispatches, as one attachment — an invocation and a `new` resolve ONE
    * method-invocation conversion (JLS 15.12.4.2), reaching `SpoonTir.coerceArgs`. `CtNewClass` is
    * named separately since `SpoonKinds.nameOf` answers the MOST SPECIFIC registered interface. */
  private val everyCall: Attaches =
    Both(Lowered("CtInvocation", Dispatch.Expression),
      Both(Lowered("CtConstructorCall", Dispatch.Expression),
           Lowered("CtNewClass", Dispatch.Expression)))

  private val everySlot: Attaches =
    Both(everyCall,
      Both(Lowered("CtNewArray", Dispatch.Expression),
        Both(Lowered("CtLocalVariable", Dispatch.Statement),
          Both(Lowered("CtField", Dispatch.Declaration),
            Both(Lowered("CtAssignment", Dispatch.Either),
                 Lowered("CtReturn", Dispatch.Statement))))))

  /** the WILDCARD arm of the frontend's type dispatch (`SpoonTir.tpe`'s
    * `CtWildcardReference` branch). Named once because three rows own that one arm.
    * `SpoonKinds.refNameOf` answers `CtWildcardReference` here, not the type-parameter kind
    * its implementation extends (`ReferenceKindTotalitySpec` pins the pair). */
  private val everyWildcard: Attaches = LoweredType("CtWildcardReference")

  /** the PLAIN reference arm — `SpoonTir.tpe`'s final `case r`, and its primitive fast path.
    *
    * The two arms are ONE Spoon kind (`CtTypeReference`), so a consult written in the general arm
    * alone is a hole at every `int`: the rule is stated once (`SpoonTir.rawUseConsults`) and called
    * from both, which is `slotConsults`' shape at the type surface. */
  private val everyPlainReference: Attaches = LoweredType("CtTypeReference")

  // -------------------------------------------------------------------------------------------
  // EXPRESSIONS — JLS 15, 5.6
  // -------------------------------------------------------------------------------------------

  val expressions: List[Difference] = List(
    Difference(eId(1), "`==`/`!=` are REFERENCE identity in Java; Scala's `==` calls `equals`",
      "JLS 15.21.3", "SLS 12.1 — `eq`/`ne` are the reference test on `AnyRef`; `==` forwards to `equals`",
      Silent, Handled, Rule44, Universal, "SpoonTir.referenceIdentity", Lowered("CtBinaryOperator", Dispatch.Expression)),
    Difference(eId(2), "postfix/prefix `++`/`--` USED AS A VALUE yields the value before the update — the VALUE only; the operand's single evaluation is JS-E17",
      "JLS 15.14.2, 15.15.1", "UNCITED — Scala has no increment operator; `x += 1` has type `Unit`",
      Silent, Handled, Rule44, Universal, "SpoonTir.exprNoCast POSTINC/PREINC -> Tree.IncDec; TirEmitter's Tree.IncDec arm", Lowered("CtUnaryOperator", Dispatch.Expression)),
    Difference(eId(3), "compound assignment NARROWS implicitly to the left-hand type — STATEMENT position; the lvalue's single evaluation is JS-E17",
      "JLS 15.26.2", "SLS 6.12.4 — `x op= e` is `x = x op e`, with no implicit narrowing",
      Silent, Handled, Predicted, Universal, "SpoonTir.stmtKind's CtOperatorAssignment arm, via SpoonTir.primRank", Lowered("CtOperatorAssignment", Dispatch.Statement)),
    Difference(eId(4), "compound assignment NARROWS implicitly to the left-hand type — EXPRESSION position; the lvalue's single evaluation is JS-E17",
      "JLS 15.26.2", "SLS 6.12.4 — as JS-E03; the two dispatches share one predicate",
      Loud, Handled, el("F8"), Universal,
      "SpoonTir.exprArm's CtOperatorAssignment arm, via the same SpoonTir.compoundNarrow JS-E03 consults", Lowered("CtOperatorAssignment", Dispatch.Expression)),
    Difference(eId(5), "the conditional operator's type is computed, not the syntactic lub of its branches",
      "JLS 15.25.2", "UNCITED — an `if` expression types as the lub of its branches",
      Mixed, Handled, el("K17"), Universal,
      "SpoonTir.promotedBranch converts each OPERAND to java's own computed type, beside the null-branch ascription in the CtConditional arm",
      Lowered("CtConditional", Dispatch.Expression)),
    Difference(eId(6), "a primitive cast is a CONVERSION in Java and an assertion in Scala once a phase has retyped the value",
      "JLS 5.5", "UNCITED — `asInstanceOf` converts at a statically primitive type and checks otherwise",
      // JS-G34 also consults the Tree.Typed emitter arm; the checkable cell is a primitive
      // target over a wrapper of a DIFFERENT primitive, reachable only by a RETYPING phase,
      // and so COUNTED rather than repaired here.
      Silent, Partial("the FRONTEND's two readings are fixed — a cast expression's own type at the slot that boxes it, and a wrapper operand at a primitive target — but a value some later PHASE retypes after the frontend decided is still unrepaired: the emitter COUNTS that cell (`cast-conversion`) and no corpus site has ever produced one"),
      el("K17"), Universal, "SpoonTir.expr via SpoonTir.castOf for the cast itself, and SpoonTir.coerce + SpoonTir.uncheckedGeneric reading SpoonTir.castType for the slot; TirEmitter's Tree.Typed arm renders what those decided and CastConversionCheck counts what a later phase moved under it",
      Rendered("Typed")),
    Difference(eId(7), "binary numeric promotion does not reach a GENERIC call boundary",
      "JLS 5.6.2", "UNCITED — weak conformance does not apply at a type-parameter slot",
      Loud, Partial("operators are free and the test-assertion shape is closed; the general call boundary is open"),
      el("X2"), Parameterised, "TestFrameworkTransform.promote", Cited("test-framework")),
    Difference(eId(8), "`&`/`|`/`^` on `boolean` are non-short-circuit and `&&`/`||` are not",
      "JLS 15.22.2", "UNCITED — the same distinction exists, with the same bytecode",
      NoImpact, NonDiff("verified same bytecode both sides"), NoTwin, NoFix, "SpoonTir.opText BITAND/BITOR/BITXOR vs AND/OR", NoObligation("a checked NON-difference: `&`/`|`/`^` and `&&`/`||` keep java's distinction, same bytecode")),
    Difference(eId(9), "a shift distance is masked to the operand's width",
      "JLS 15.19", "UNCITED — the shift operators mask identically on the JVM",
      NoImpact, NonDiff("shared JVM instructions"), NoTwin, NoFix, "SpoonTir.opText SL/SR/USR", NoObligation("a checked NON-difference: the shift operators mask identically on the JVM")),
    Difference(eId(10), "integer division and remainder, and the floating-point edge cases",
      "JLS 15.17", "UNCITED — shared JVM instructions",
      NoImpact, NonDiff("shared JVM instructions"), NoTwin, NoFix, "SpoonTir.opText DIV/MOD", NoObligation("a checked NON-difference: shared JVM instructions for division and remainder")),
    Difference(eId(11), "an array store evaluates its operands before the bounds check",
      "JLS 15.26.1, 10.4", "UNCITED — the same evaluation order",
      NoImpact, NonDiff("verified on both compilers"), NoTwin, NoFix, "SpoonTir.exprNoCast's CtArrayWrite arm -> Tree.ArrayAccess", NoObligation("a checked NON-difference: the same evaluation order on both compilers")),
    Difference(eId(12), "the receiver's null check happens after the arguments are evaluated",
      "JLS 15.12.4.1, 15.12.4.4", "UNCITED — the same order",
      NoImpact, NonDiff("the order is preserved by construction"), NoTwin, NoFix, "SpoonTir.invocation", NoObligation("a checked NON-difference: the receiver/argument order is preserved by construction")),
    Difference(eId(13), "`instanceof` on `null` is false and never throws",
      "JLS 15.20.2", "SLS 12.1 — `isInstanceOf` is false for `null`",
      NoImpact, Handled, NoTwin, Universal, "SpoonTir.exprNoCast INSTANCEOF -> Tree.InstanceOf", NoObligation("satisfied by construction: `Tree.InstanceOf` renders `isInstanceOf`, false for null exactly as java is")),
    Difference(eId(14), "string concatenation with a NON-`String` left operand",
      "JLS 15.18.1", "UNCITED — `+` on a non-`String` left operand is that type's own `+`",
      Loud, Handled, Predicted, Universal, "SpoonTir.stringify, gated on isStringConcat && !isStringTyped(left)", Lowered("CtBinaryOperator", Dispatch.Expression)),
    Difference(eId(15), "an assignment IS an expression, with the assigned value",
      "JLS 15.26", "SLS 6.12.4 — an assignment has type `Unit`",
      NoImpact, Handled, InCode("SpoonTir.exprNoCast's CtAssignment arm carries the argument in a comment"),
      Universal, "SpoonTir.exprNoCast's CtAssignment arm -> Tree.Block(List(Assign), lhs)", Lowered("CtAssignment", Dispatch.Expression)),
    // JS-E02/E03/E04 cover the VALUE and NARROWING; this row covers how many times the LVALUE is
    // evaluated (F7, CLOSED) — the emitter binds each non-trivial lvalue subexpression to a
    // temporary so it evaluates once. Simple lvalues keep the direct form.
    Difference(eId(17), "a compound assignment and `++`/`--` evaluate the LVALUE ONCE — its array reference, its index, its target",
      "JLS 15.26.2, 15.14.2, 15.15.1",
      "SLS 6.12.4 — `l op= r` expands to `l = l op r`, and every occurrence of `l` is evaluated",
      Silent, Handled, el("F7"), Universal,
      "SpoonTir.stmtArm's CtOperatorAssignment arm and exprArm's twin consult; TirEmitter.termArm's Assign and IncDec arms bind non-trivial lvalues",
      Lowered("CtOperatorAssignment", Dispatch.Either)),
    // A row that exists because a SUSPICION was priced, and the price was zero. `CtTextBlock` was
    // filed as ABSORBED SILENTLY — `SpoonKinds`' own "dangerous one" — on the true observation that
    // it subclasses `CtLiteral` and no arm knows it was there. What that could not settle is WHICH
    // STRING arrives, which is the entire question, and a probe settled it in one run.
    Difference(eId(18), "a TEXT BLOCK denotes the PROCESSED string, not its source text",
      "JLS 3.10.6",
      "SLS 1.3.6 — a Scala string literal has no incidental-whitespace rule and needs none: the value the frontend holds is already java's",
      NoImpact,
      NonDiff("the frontend reads `CtLiteral.getValue`, which Spoon has already resolved through " +
        "JLS 3.10.6 — incidental whitespace stripped, line terminators normalised, escapes " +
        "applied — so a `Constant.StringC` holding exactly the denoted string reaches the TIR. The " +
        "emitter then re-escapes it (ENGINE-LIMITS L1), so the SHAPE changes (one `\"…\"` with " +
        "`\\n` where java wrote three lines) and the VALUE does not. PROBED, not assumed"),
      Predicted, NoFix,
      "SpoonTir.literal reads getValue and the frontend calls getOriginalSourceFragment nowhere; TirEmitter.escape puts every newline back; TextBlockSpec",
      NoObligation("a checked NON-difference: the two languages denote the same string, so no arm has a decision to take")),
    Difference(eId(19), "a BOXING CONSTRUCTOR — `new Double(v)` — is deprecated for removal; the JDK names `valueOf`",
      "JDK 9 `@Deprecated(since=\"9\", forRemoval=true)` on every `java.lang` wrapper constructor",
      "UNCITED — scalac's `-deprecation` under the reference build's `-Werror` makes each site an error (12 in one port)",
      Silent, Handled, Predicted, Universal,
      "SpoonTir.ctorCall: isBoxedWrapper && one argument -> `<Wrapper>.valueOf(arg)`; the ONE delta is JS-E01's cache (a `new` is a distinct object, `valueOf` may not be) — no corpus site compares a fresh box by identity; BoxingCtorSpec",
      Lowered("CtConstructorCall", Dispatch.Expression)),
  )

  // -------------------------------------------------------------------------------------------
  // STATEMENTS AND CONTROL FLOW — JLS 14, 16
  // -------------------------------------------------------------------------------------------

  val statements: List[Difference] = List(
    Difference(sId(1), "an unlabelled `break`/`continue` binds LEXICALLY to the innermost enclosing loop or switch",
      "JLS 14.15, 14.16", "UNCITED — `boundary`/`break` resolve the innermost `Label` in implicit scope",
      Silent, Handled, el("F1"), Universal, "Jumps.breaksOut/continuesIn/jumpsTo; TirEmitter.loopWithJumps", everyLoop),
    Difference(sId(2), "a LABEL sits on any statement, not only on a loop",
      "JLS 14.7", "UNCITED — Scala has no labelled statement; a named `boundary` is the image",
      Silent, Handled, el("F1"), Universal, "Tir.Tree.Labeled; TirEmitter's Tree.Labeled arm and labelNeedsBoundary", Rendered("Labeled")),
    Difference(sId(3), "a `boundary` the emitter INTERPOSES steals the enclosing loop's un-annotated jumps",
      "JLS 14.15", "UNCITED — `boundary.break` with no `using` resolves the innermost `Label`",
      Silent, Handled, el("F2"), Universal, "TirEmitter.interposes feeding TirEmitter.loopWithJumps", everyLoop),
    Difference(sId(4), "switch FALLTHROUGH runs the next case's statements",
      "JLS 14.11.3", "UNCITED — a `match` arm never falls through",
      Silent, Handled, el("F3"), Universal, "SpoonTir.switchStmt's tail-duplication closures", Lowered("CtSwitch", Dispatch.Statement)),
    Difference(sId(5), "a `switch` with no `default` FALLS OUT; a `match` with no `case _` throws",
      "JLS 14.11.3", "UNCITED — an unmatched `match` throws `MatchError`",
      Silent, Handled, Rule44, Universal,
      "SpoonTir.switchStmt's synthesised fall-out arm, gated on SpoonTir.isEnhanced — which asks " +
        "BOTH of the enhanced-switch disjuncts, the labels AND (through " +
        "SpoonTir.selectorOutsideClassicSet) the selector's own type, because a qualified enum " +
        "constant is an enhanced switch no label betrays",
      Lowered("CtSwitch", Dispatch.Statement)),
    Difference(sId(6), "an unlabelled `break` in the MIDDLE of a case ends the CASE",
      "JLS 14.15", "UNCITED — a `match` arm cannot be left early",
      Silent, Handled, el("F3"), Universal, "TirEmitter.matchStr and TirEmitter.caseNeedsBoundary", Rendered("Match")),
    Difference(sId(7), "only an UNLABELLED trailing `break` terminates a case; a labelled one leaves the LOOP",
      "JLS 14.15", "UNCITED — no counterpart; the two must be told apart before stripping",
      Silent, Handled, Rule44, Universal, "SpoonTir.switchStmt's `case (b: CtBreak) :: _ if b.getTargetLabel == null`", Lowered("CtSwitch", Dispatch.Statement)),
    Difference(sId(8), "a `null` selector throws NPE IMPLICITLY — a classic switch has no `case null` to opt out with",
      "JLS 14.11.2", "UNCITED — `null` matches no literal pattern and reaches the last arm",
      Silent, Handled, el("F6"), Universal, "TirEmitter.matchStr's synthesised `case null` throw; SwitchNullCheck", Rendered("Match")),
    // `Tree.Yield` is the one new node needed: a non-tail `yield` completes the switch expression
    // abruptly from depth, rendered as a value-carrying `boundary` around the ARM. `Handled`, not
    // `Partial`: the one cell where the languages disagree is EXHAUSTIVENESS (JLS 15.28.1), and
    // both throw at the same place for the same reason, class only differing.
    Difference(sId(9), "switch EXPRESSIONS and `yield`",
      "JLS 15.28, 14.21", "UNCITED — a `match` is already an expression, so the image exists",
      Loud, Handled,
      Predicted, Universal,
      "SpoonTir.switchExpr — no fall-out arm, since the JLS makes a switch expression exhaustive; " +
        "`yield` peeled at the tail and carried as Tree.Yield elsewhere, which " +
        "TirEmitter.matchStr wraps in a value-carrying arm boundary",
      Lowered("CtSwitchExpression", Dispatch.Expression)),
    // java deconstructs a record through its ACCESSORS (JLS 14.30.1), scala through `unapply`;
    // `JS-C43` derives one over the accessors, so the record half is an ordinary constructor
    // pattern. JLS 14.30.2's UNCONDITIONAL component pattern (matches `null` where a narrowing
    // typed pattern does not) is a separate node (`Tree.BindPattern`/`Tree.TypePattern`), verified
    // against javac.
    Difference(sId(10), "pattern and record switch, with sealed exhaustiveness",
      "JLS 14.11.1, 14.30", "UNCITED — a scala typed pattern is the image of one half and a constructor pattern over JS-C43's derived extractor is the image of the other",
      Loud, Partial("the TYPE pattern, its `when` guard, `case null`, `case null, default` and the " +
        "RECORD pattern — nested, and with JLS 14.30.2's unconditional/narrowing split — all lower " +
        "exactly. Two cells are not claimed: a component whose declared type the parser cannot " +
        "resolve takes the NARROWING arm, which differs from java only at a `null` component under " +
        "a widening pattern; and EXHAUSTIVENESS, where java checks a sealed switch and scala does " +
        "not — both throw where the guarantee fails at run time and only the class differs, which " +
        "is JS-S09's cell read at the other switch"),
      Predicted, Universal,
      "SpoonTir.caseLabel -> Tree.TypePattern and CaseDef.guard; SpoonTir.recordPattern -> " +
        "Tree.RecordPattern with Tree.BindPattern at an unconditional component; TirEmitter's three " +
        "pattern arms, the record one naming the extractor through the COMPANION's value path",
      Both(Lowered("CtSwitch", Dispatch.Statement), Lowered("CtSwitchExpression", Dispatch.Expression))),
    Difference(sId(11), "a translated CATCH swallows a translated JUMP — `boundary.Break` is a `RuntimeException`",
      "JLS 14.15, 14.20", "UNCITED — `scala.util.boundary.Break` extends `RuntimeException`",
      Silent, Handled, el("F4"), Universal, "TirEmitter.tryStr's guard, via Jumps.catchesBreak; BreakCatchCheck", Rendered("Try")),
    Difference(sId(12), "a `finally` completing abruptly DISCARDS the try's own abrupt completion",
      "JLS 14.20.2", "UNCITED — the same rule; what is missing is a test of it",
      Silent, Partial("no fixture in the corpus has a `finally` that is itself the SOURCE of the abrupt completion"),
      Predicted, Universal, "TirEmitter.tryStr's `fl`", Rendered("Try")),
    Difference(sId(13), "try-with-resources closes on ANY completion, in reverse order, before this try's own catch",
      "JLS 14.20.3", "UNCITED — no counterpart statement; `Using` is not one (it is a lambda)",
      Silent, Handled, el("F5"), Universal, "TirEmitter.tryStr -> resourceStr; TryResourceCheck", Rendered("Try")),
    Difference(sId(14), "multi-catch `A | B`",
      "JLS 14.20", "UNCITED — a union type in the pattern",
      NoImpact, Handled, Predicted, Universal, "SpoonTir.tryStmt's getMultiTypes reduce to OrType", Lowered("CtTry", Dispatch.Statement)),
    Difference(sId(15), "the enhanced-for expression is evaluated ONCE, and arrays and `Iterable` differ",
      "JLS 14.14.2", "UNCITED — the same, by construction of the emitted loop",
      NoImpact, Handled, NoTwin, Universal, "SpoonTir's CtForEach -> Tree.ForEach; TirEmitter emits the iterable once", Rendered("ForEach")),
    Difference(sId(16), "the enhanced-for BINDING may be reassigned in the body, and may be declared at a supertype",
      "JLS 14.14.2", "UNCITED — a `for` binding is a `val`",
      Loud, Handled, el("K7"), Universal, "TirEmitter's Tree.ForEach arm — widenedBinding and reassignsBinding", Rendered("ForEach")),
    Difference(sId(17), "the classic `for`'s UPDATE runs on `continue`, and its `ForInit` scopes to the loop",
      "JLS 14.14.1", "UNCITED — `while` has no update clause, so it has to be placed",
      Silent, Handled, Predicted, Universal, "TirEmitter's Tree.For arm — the update is outside the per-iteration boundary", Rendered("For")),
    Difference(sId(18), "`do`-`while` — Scala 3 removed it",
      "JLS 14.13", "UNCITED — Scala 3 has no `do`-`while`; `while ({ body; cond }) ()` is the image",
      Silent, Handled, Predicted, Universal, "SpoonTir's CtDo -> Tree.DoWhile; TirEmitter's Tree.DoWhile arm", Both(Lowered("CtDo", Dispatch.Statement), Rendered("DoWhile"))),
    Difference(sId(19), "definite assignment for LOCALS — Java rejects a read before assignment",
      "JLS 16", "UNCITED — Scala requires an initialiser instead, so the analysis has no image",
      Mixed, Partial("the FIELD half is closed; the local half is unexamined — an uninitialised local silently takes a default"),
      el("C10"), Universal, "TirEmitter.valDefStr's fieldOfAClass gate and defaultFor", Rendered("ValDef")),
    Difference(sId(20), "redeclaring a local in a nested block",
      "JLS 14.4.3", "UNCITED — Scala permits the shadowing Java rejects",
      NoImpact, NonDiff("javac rejects the input shape, so it never reaches the engine"), NoTwin, NoFix, "SpoonTir.defineLocal", NoObligation("a checked NON-difference: javac REJECTS the input shape, so a local redeclared in a nested block never reaches the engine and there is no site to decide anything at")),
    Difference(sId(21), "`return` inside a lambda body returns from the LAMBDA, not from the method",
      "JLS 15.27.2", "UNCITED — a `return` in a Scala lambda is a non-local return from the enclosing method",
      Mixed, Handled, Rule44, Universal, "TirEmitter's Tree.Lambda arm and returnsIn -> a nested `def`", Rendered("Lambda")),
    Difference(sId(22), "`synchronized` as a statement",
      "JLS 14.19", "UNCITED — `.synchronized`, with the same monitor bytecode",
      NoImpact, Handled, NoTwin, Universal, "SpoonTir's CtSynchronized -> Tree.Synchronized", Lowered("CtSynchronized", Dispatch.Statement)),
    Difference(sId(23), "`assert` is enabled at RUN TIME by `-ea` and OFF by default, so the condition is not evaluated",
      "JLS 14.10", "UNCITED — Scala's `assert` runs unless elided at COMPILE time by `-Xelide-below`",
      Silent, Open, Predicted, Universal,
      "SpoonTir's CtAssert -> Tir.Tree.Assert -> TirEmitter's unconditional `assert(...)`; no decision, no note, no check", Rendered("Assert")),
    Difference(sId(24), "`throw null`",
      "JLS 14.18", "UNCITED — the same `athrow`",
      NoImpact, NonDiff("shared JVM instruction"), NoTwin, NoFix, "SpoonTir's CtThrow -> Tree.Throw", NoObligation("a checked NON-difference: `throw null` is the same `athrow` instruction on both sides — a JVM fact with no decision to take")),
    Difference(sId(25), "Java REJECTS unreachable code and Scala allows it — composed with `break`",
      "JLS 14.21", "UNCITED — Scala has no unreachable-statement rule",
      Silent, Handled, InCode("TirEmitter.endsInInfiniteLoop states the composition in its own comment"),
      Universal, "TirEmitter.endsInInfiniteLoop, each arm excluding a loop that breaks out", Rendered("DefDef")),
    Difference(sId(26), "a `return` inside an enhanced-for body becomes a NON-LOCAL RETURN in Scala's `.foreach` desugaring",
      "JLS 14.14.2", "UNCITED — Scala's `for (x <- xs) { ... }` desugars to `.foreach(x => ...)` and `return` inside the lambda is a non-local return, deprecated/errored under `-Werror`",
      Mixed, Handled, Predicted, Universal,
      "TirEmitter's Tree.ForEach arm: returnsIn(body) triggers while-loop lowering with scala-style .iterator/.hasNext/.next()",
      Rendered("ForEach")),
  )

  // -------------------------------------------------------------------------------------------
  // CLASSES, MEMBERS, INITIALIZATION, VISIBILITY — JLS 8, 9, 12, 6.6
  // -------------------------------------------------------------------------------------------

  val classes: List[Difference] = List(
    Difference(cId(1), "a Java `static` is INHERITED by every subclass; a Scala companion inherits nothing",
      "JLS 8.2, 8.4.8", "UNCITED — a companion object has no inheritance relation to its class's parents",
      Loud, Handled, el("T14"), Universal, "SpoonTir.staticCallQualifier, reading the minter's owner rather than the written name", Lowered("CtInvocation", Dispatch.Expression)),
    Difference(cId(2), "interface constants are inherited through `implements`",
      "JLS 9.3, 8.1.5", "UNCITED — as JS-C01, through the interface edge",
      Loud, Handled, el("T14"), Universal, "SpoonTir.declaringStaticType's BFS over the whole inheritance closure", Both(Lowered("CtInvocation", Dispatch.Expression), everyStaticFieldRead)),
    Difference(cId(3), "static HIDING is resolved by the STATIC type of the qualifier",
      "JLS 8.4.8.2", "UNCITED — a companion re-export must not merge a redeclaration away",
      Silent, Handled, el("T14"), Universal, "SpoonTir.declaringStaticType (nearest declarer wins); TirEmitter.staticOwnersOf excludes the class's own static names", Rendered("ClassDef")),
    Difference(cId(4), "a subclass field SHADOWS a superclass field — two storage cells, not one",
      "JLS 8.3, 6.4.1", "UNCITED — a same-named Scala member is an OVERRIDE, virtually dispatched",
      Silent, Handled, NoTwin, Universal,
      "TirEmitter.resolveFieldShadowing — the trigger is NAME membership in the inherited instance members, so a same-type field fires too", Cited("field-shadowing")),
    Difference(cId(5), "a static nested constant reached through a SUBCLASS's name, and `import static`",
      "JLS 6.5.6, 7.5.3", "UNCITED — fully-qualified emission removes the question",
      NoImpact, Handled, el("T14"), Universal, "SpoonTir.nestedPath and SpoonTir.staticFieldAccess", everyStaticFieldRead),
    Difference(cId(6), "`anInstance.staticMethod()` evaluates the receiver FOR ITS SIDE EFFECTS and discards it",
      "JLS 15.12.4.1", "UNCITED — a companion call has no receiver slot to put the expression in",
      Mixed, Handled, NoTwin, Universal,
      "TirEmitter.staticThroughInstance with effectFree — emits `{ recv; Owner.m(args) }` where the receiver can have effects", Rendered("Apply")),
    // Read `NonDiff` until K22 measured it wrong: scala's object-access trigger does NOT fire for
    // every JLS 12.4.1 case — `new T` and a subclass's own initialisation touch no member, so a
    // class initialiser lands in the companion and never runs.
    Difference(cId(7), "JLS's class-initialisation TRIGGER list vs Scala's uniform accessor trigger",
      "JLS 12.4.1", "UNCITED — a Scala object initialises on first access to any member",
      Silent,
      // Both port-visible triggers are FORCED; what is left is two refusals and one approximation,
      // and each is named because a reader's question is whether THEIR path is covered. This
      // sentence said "the SUBCLASS trigger is counted and not yet forced" for as long as the
      // subclass trigger HAD been forced, which is a status line that outlived its own commit.
      Partial("the REFLECTIVE trigger (item 6) is refused — a reflective load of the emitted " +
        "class does not touch its module, and the load lives in the port's CONSUMER; a companion " +
        "whose initialisation is a MUTUAL CYCLE is refused and counted (JS-C10); and the ORDER a " +
        "force fires in is approximate where two initialisers meet through a third party"),
      el("K22"), Universal,
      "TirEmitter.forceCompanion and its `hasClinit` call site; ClassInitTriggerCheck.stepNine", Rendered("ClassDef")),
    Difference(cId(8), "a CONSTANT VARIABLE is inlined, so reading it never triggers class initialisation",
      "JLS 4.12.4, 13.1", "UNCITED — `inline val` is the image; a typed `val` triggers the initialiser",
      Silent, Handled, Rule44, Universal, "TirEmitter.isJavaConstant and its `inline val` rendering", Rendered("ValDef")),
    Difference(cId(9), "several `static { }` blocks and static field initialisers run in ONE textual order",
      "JLS 12.4.2", "UNCITED — an object body is one sequence, so the order has to survive the frontend",
      Silent, Handled, el("C12"), Universal,
      "SpoonTir.classDef's merged position-key sort over fields and init blocks; TirEmitter.orderBody's isStep4, which covers `<clinit>`", Rendered("ClassDef")),
    // Read `NonDiff("shared JVM mechanism")` until K22 face 2 measured it wrong: a scala COMPANION
    // is not the shared mechanism java's class is — the JVM lets a re-entrant thread read a
    // half-initialised class's statics, but a module in a MUTUAL cycle has no `MODULE$` yet.
    Difference(cId(10), "circular class initialisation delivers DEFAULT values on the same thread",
      "JLS 12.4.2", "UNCITED — a scala module in a MUTUAL cycle has no MODULE$ yet, and throws",
      Loud,
      Partial("a self-cycle is exact — dotty assigns `MODULE$` first — and a MUTUAL one is " +
        "refused and counted: JS-C07's instantiation trigger is not attached where forcing the " +
        "companion would re-enter an initialisation in progress"),
      el("K22"), Universal,
      "ClassInitTriggerCheck.reentrantBearers; ClassInitTriggerCheck.Issue.ReentrantRefused", Rendered("ClassDef")),
    Difference(cId(11), "a superclass is initialised before its subclass",
      "JLS 12.4.2", "UNCITED — the same JVM mechanism",
      NoImpact, NonDiff("shared JVM mechanism"), NoTwin, NoFix, "no symbol — a JVM fact", NoObligation("a checked NON-difference: a superclass is initialised before its subclass by the same JVM mechanism on both sides, and no emitted text decides it")),
    Difference(cId(12), "a FORWARD REFERENCE to a later field is a Java compile error and a silent default read in Scala",
      "JLS 8.3.3", "UNCITED — Scala reads the field's default rather than rejecting",
      Silent, Open, Predicted, Universal,
      "nothing detects it; order preservation is the only mitigation, and TirEmitter.orderBody's `forward reference` note is about promoted LOCALS", Rendered("ValDef")),
    Difference(cId(13), "JLS 12.5's instance-creation sequence, whole",
      "JLS 12.5", "UNCITED — a Scala class body IS its constructor, so the sequence is an emission order",
      Silent, Partial("steps 2-4 are closed; the residue is what JS-C15 and JS-C19 carry"),
      el("C12"), Universal, "CtorFunnel; SpoonTir.classDef's step-4 sort; TirEmitter.orderBody", Rendered("ClassDef")),
    Difference(cId(14), "the superclass constructor runs before this class's field initialisers",
      "JLS 12.5", "UNCITED — the same order, once a primary constructor exists to host it",
      NoImpact, Handled, el("C1"), Universal, "CtorFunnel's primary promotion; TirEmitter.lowerCtors splicing plan.primaryBody", Rendered("ClassDef")),
    Difference(cId(15), "a virtual call from a constructor sees UNINITIALISED subclass state",
      "JLS 12.5", "UNCITED — the identical hazard",
      NoImpact, NonDiff("an identical hazard on both sides"), NoTwin, NoFix, "no symbol — a JVM fact", NoObligation("a checked NON-difference: a virtual call from a constructor sees uninitialised subclass state identically on both sides")),
    Difference(cId(16), "instance initialiser blocks",
      "JLS 8.6", "UNCITED — a statement in the class body is the image",
      Silent, Handled, NoTwin, Universal, "SpoonTir.classDef's CtAnonymousExecutable harvest; TirEmitter.isInitBlock", Rendered("DefDef")),
    Difference(cId(17), "double-brace initialisation is an anonymous subclass plus an instance initialiser",
      "JLS 8.6, 15.9.5", "UNCITED — the anonymous class carries its captures the same way",
      Silent, Partial("the structural capture is closed; the interaction with a collections retype was measured but not confirmed closed"),
      el("T1"), Universal, "SpoonTir.anonClass's non-static CtAnonymousExecutable arm", Lowered("CtNewClass", Dispatch.Expression)),
    Difference(cId(18), "field initialisers and instance-init blocks are ONE step-4 sequence in textual order, not two buckets",
      "JLS 12.5", "UNCITED — the same sequence, which a frontend that groups by NODE KIND has already lost",
      Silent, Handled, el("C12"), Universal,
      "SpoonTir.classDef's `(fields ++ initBlocks).sortBy(posKey)`; TirEmitter.orderBody's isStep4", Rendered("ClassDef")),
    Difference(cId(19), "`this(...)` / `super(...)` in a secondary constructor — Scala's secondary constructors cannot call `super`",
      "JLS 8.8.7.1", "UNCITED — only a primary constructor may call the superclass constructor",
      Loud, Handled, el("C3"), Universal, "CtorFunnel's promotion; TirEmitter.orderBody's delegateTarget post-order; OmissionCheck counts the residue", Rendered("ClassDef")),
    Difference(cId(20), "the implicit `super()` and the default constructor",
      "JLS 8.8.7, 8.8.9", "UNCITED — a Scala class always has a primary constructor",
      Loud, Handled, el("C8"), Universal, "TirEmitter.orderBody's paramfulPrimary; CtorFunnel.delegationOnlyNilary", Rendered("ClassDef")),
    Difference(cId(21), "constructor overload resolution, against Scala's textual-anteriority rule",
      "JLS 8.8.8, 15.9.3", "UNCITED — an overload defined later in the file is not visible to an earlier one",
      Loud, Partial("the delegation order answers anteriority; the applicability of a SYNTHESISED primary against a narrower real constructor is open"),
      el("C8"), Universal, "TirEmitter.orderBody's delegation-topological sort", Rendered("ClassDef")),
    Difference(cId(22), "Java's THREE-PHASE applicability (strict, then loose, then varargs) has no Scala counterpart",
      "JLS 15.12.2", "UNCITED — Scala resolves in one phase, with implicit conversions and defaults in scope",
      Silent,
      Partial("the RISK is counted at every rendered call whose candidate set spans one of java's phase boundaries; WHICH member scala then binds is not modelled and cannot be without a resolver"),
      el("T17"), Universal,
      "OverloadRiskCheck.analyse (the phase-boundary predicate) and TirEmitter's JS-C22 consult at the Apply arm; two narrow FACES are closed beside it — Visibility.decide restores javac's candidate set, TirEmitter.numericOverloadAscription closes exact-match-vs-widening",
      Rendered("Apply")),
    Difference(cId(23), "most-specific tie-break: Scala prefers a NON-GENERIC alternative and Java does not",
      "JLS 15.12.2.5", "UNCITED — the relative-weight rule prefers the non-polymorphic alternative",
      Silent,
      Partial("the RISK is counted where an applicable candidate is generic and another is not; which alternative scala's relative-weight rule then prefers is not modelled"),
      el("T17"), Universal,
      "OverloadRiskCheck.Issue.GenericTieBreak and TirEmitter's JS-C23 consult at the Apply arm — reported apart from JS-C22 because the JLS clause is its own and so would any fix be",
      Rendered("Apply")),
    Difference(cId(25), "`override` is mandatory in Scala and absent in Java",
      "JLS 8.4.8.1", "UNCITED — an override without the modifier is an error",
      Loud, Handled, NoTwin, Universal, "SpoonTir.overridesInherited setting Flags.isOverride; TirEmitter.mods", Rendered("DefDef")),
    Difference(cId(27), "covariant return types",
      "JLS 8.4.8.3", "UNCITED — the same rule",
      NoImpact, NonDiff("the same rule on both sides"), NoTwin, NoFix, "no symbol — a shared rule", NoObligation("a checked NON-difference: covariant return types are the same rule on both sides")),
    Difference(cId(28), "bridge methods",
      "JLS 13.1", "UNCITED — a source-to-source port never sees one",
      NoImpact, NonDiff("bridges are a binary artefact and this engine emits source"), NoTwin, NoFix, "no symbol", NoObligation("a checked NON-difference: a bridge method is a binary artefact and this engine emits source")),
    Difference(cId(29), "a static NESTED class and an INNER class are different types, and only one is path-dependent",
      "JLS 8.1.3", "UNCITED — `Outer#Inner` is the type projection; `outer.Inner` is the path-dependent type",
      Mixed, Handled, NoTwin, Universal,
      "TirEmitter.tpe's TypeRef arm, through typeSym's cascade — nestedPath for a static nested type, a projection for an inner one, with namedInner opting out at `extends`/`new`",
      RenderedType("TypeRef")),
    // `Tree.ClassDef` is a `Statement` and always was (T9's exit note); the gap was every
    // whole-program recursion walking `cd.body` alone, which cannot reach a class standing in a
    // member's BLOCK. Two things a local class asks a nested one does not: java's qualified name
    // carries a BINARY disambiguator, and the owner is an EXECUTABLE (naming by simple name, not
    // a method projection).
    Difference(cId(30), "method-LOCAL named classes",
      "JLS 14.3", "UNCITED — Scala has a direct counterpart; only the capture wiring is missing",
      Loud, Handled, el("T9"), Universal,
      "SpoonTir.stmtArm's CtClass arm -> SpoonTir.classDef with the enclosing EXECUTABLE as owner " +
        "and SpoonTir.localName for java's source name; the anonymous-class body wiring reused " +
        "verbatim for captures and for `this`; StandardTraversal.allClassDefs, which is what every " +
        "whole-program pass walks to reach it at all",
      Lowered("CtClass", Dispatch.Statement)),
    Difference(cId(31), "anonymous class construction and capture",
      "JLS 15.9.5", "UNCITED — the same construct, with a synthesised name",
      Silent, Handled, el("T1"), Universal, "SpoonTir.anonClass", Lowered("CtNewClass", Dispatch.Expression)),
    Difference(cId(32), "a captured local must be EFFECTIVELY FINAL in Java",
      "JLS 4.12.4", "UNCITED — Scala captures a `var` too, so it is a superset",
      NoImpact, NonDiff("Scala accepts strictly more"), NoTwin, NoFix, "no symbol", NoObligation("a checked NON-difference: Scala captures a `var` too, so it accepts strictly more than java does")),
    Difference(cId(33), "the interface default-method diamond is resolved by JLS's two rules, not by trait linearization",
      "JLS 9.4.1", "UNCITED — linearization picks the last mixin, which is not Java's answer",
      Silent, Partial("rule 1 — class beats interface — is closed; rule 2 — the MOST SPECIFIC interface — agrees only by luck of mixin order"),
      el("T7"), Universal, "TirEmitter.diamondOverrides, whose `sup` is the superclass and whose mixins are all in the tail", Rendered("ClassDef")),
    Difference(cId(34), "interface statics are not inherited AT ALL, not even by an implementor",
      "JLS 8.4.8, 9.3", "UNCITED — a corollary of JS-C01 at the interface edge",
      Loud, Handled, el("T14"), Universal, "TirEmitter's companion re-export lists", Rendered("ClassDef")),
    Difference(cId(35), "interface `private` methods",
      "JLS 9.4", "UNCITED — a private trait member is the image",
      NoImpact, NonDiff("a direct counterpart exists"), NoTwin, NoFix, "no symbol", NoObligation("a checked NON-difference: a private trait member is a direct counterpart of an interface private method")),
    Difference(cId(36), "an interface field is implicitly `public static final`",
      "JLS 9.3", "UNCITED — a corollary of JS-C01, JS-C02 and JS-C08",
      Loud, Handled, el("T14"), Universal, "SpoonTir.fieldFlags with SpoonTir.declaringStaticType's interface edge", Rendered("ValDef")),
    Difference(cId(37), "`name()`, `values()` and `valueOf` are SYNTHESISED on every Java enum",
      "JLS 8.9.3",
      "UNCITED — a Scala 3 `enum` has a different, non-identical surface: it inherits `name()` from " +
        "`java.lang.Enum` and its desugaring supplies `values`/`valueOf`, but `values` is PARENLESS " +
        "where java writes `values()`",
      Loud, Handled, el("T21"), Universal,
      "TirEmitter.scalaEnumDef writes none of the four (the parent and the desugaring have them) and " +
        "applyStr0 drops the parens at a `values()` call; sealedEnumDef's nameM, values and valueOf " +
        "supply all four for an enum EnumShape refuses",
      Rendered("ClassDef")),
    Difference(cId(38), "a promoted enum constructor parameter IS a member, and `name` collides with `Enum.name()`",
      "JLS 8.9.2", "UNCITED — a constructor parameter becomes a class member",
      Loud, Handled, el("T11"), Universal,
      "TirEmitter.sealedEnumDef's hasName, and EnumShape.Reserved — which is the same collision read " +
        "at the PARENT, where java.lang.Enum's final members make it a refusal rather than a skip",
      Rendered("ClassDef")),
    Difference(cId(39), "`ordinal()` is part of every Java enum's surface, mentioned or not",
      "JLS 8.9.3", "UNCITED — nothing supplies it unless it is emitted",
      Loud, Handled, el("T13"), Universal,
      "TirEmitter.sealedEnumDef's hasOrdinal; scalaEnumDef inherits it from java.lang.Enum",
      Rendered("ClassDef")),
    Difference(cId(40), "enum constants with PER-CONSTANT class bodies",
      "JLS 8.9.1", "UNCITED — an anonymous subclass per constant",
      Silent, Partial("fields and methods are collected; instance-init blocks and nested types are not, so the step-4 order inside a constant body is unreached — and a constant body is now also what makes the enum inexpressible as a scala 3 `enum` (T21), so such an enum is not a `java.lang.Enum` either"),
      el("T8"), Universal,
      "SpoonTir.enumCase, which collects only CtField and CtMethod; EnumShape.refusal, which reads " +
        "the same bodies to decide the enum's SHAPE",
      Rendered("ClassDef")),
    Difference(cId(42), "`EnumMap`/`EnumSet` GUARANTEE declaration-order iteration",
      "JLS 8.9 with the java.util contract",
      "SLS 5.3 — the guarantee is carried by the SHIM's own ordering, not by any stdlib collection",
      Silent, Handled, Predicted, Universal,
      "CollectionsTransform.typeMap sends both to balticporter.runtime.JavaEnumMap/JavaEnumSet, which order by ordinal; JavaEnumCollectionsSpec asserts the order against an insertion order that would expose a stdlib map",
      // `Cited` and not `LoweredType`: the difference is discharged by a TABLE ENTRY, so there is
      // no per-site decision an arm could consult about. A type reference to `EnumMap` is lowered
      // by the same one arm that lowers every other reference, and making that arm owe this row
      // would demand a consult at every type in every program.
      Cited("collections")),
    // LOWERED as a plain final class with javac's four members written out (§4.4's record row),
    // not a `case class`. `Partial`, not `Handled`: scalac emits no JVM `Record` attribute; a
    // record pattern is a matching PROCESS and `unapply` a FUNCTION (accessors run past the first
    // failure); an accessor's exception arrives raw, not wrapped in `MatchException`.
    Difference(cId(43), "Java `record`",
      "JLS 8.10", "UNCITED — a case class differs in accessor naming, in three facets of `toString`, in `hashCode`, in float equality and in what its extractor reads",
      Loud,
      Partial("the declaration, the components, the canonical and compact constructors, the " +
        "accessors and javac's own equals/hashCode/toString are reproduced exactly — probed " +
        "value by value against `javac`. What no image can carry is the REFLECTIVE record — scalac " +
        "emits no JVM record, so `Class.isRecord` is false and `getRecordComponents` is null on the " +
        "emitted class, and a framework that discovers records reflectively sees none — and the two " +
        "cells where a record pattern is a matching PROCESS and a tuple `unapply` is a FUNCTION: " +
        "every accessor runs where java stops at the first failing component, and an accessor's " +
        "exception arrives raw where java wraps it"),
      Predicted, Universal,
      "SpoonTir.typeFlags's isRecord, SpoonTir.recordComponents/canonicalised/accessorBodies, and " +
        "TirEmitter.recordMembers, which writes equals/hashCode/toString over the FIELDS and an " +
        "`unapply` over the ACCESSORS — java's own split, and the reason a case class cannot be the image",
      Rendered("ClassDef")),
    // java seals by NAMING its subclasses anywhere in the module, scala by CONTAINING them in one
    // file. Where they coincide the image is EXACT; where they do not the residue is RECORDED
    // rather than approximated (`Partial`, not `Handled`: the port still ships the widening).
    Difference(cId(44), "`sealed` / `non-sealed` / `permits`",
      "JLS 8.1.1.2, 9.1.1.4",
      "UNCITED — Scala has no `non-sealed` and no explicit `permits`; `sealed` restricts extension to the declaring FILE",
      Silent,
      Partial("a seal whose subtypes all land in ONE emitted file is reproduced exactly; one that " +
        "reaches another file — or whose permitted set this program does not declare, which takes " +
        "the same answer for the same reason — ships OPEN, because scala has no `permits` to name " +
        "it with. That half is a counted `WidenedSeal` decision and not a translation"),
      InCode("TirEmitter.sealOf, which states the file-scope condition it can and cannot meet"), Universal,
      "SpoonTir.typeFlags's isSealed carries java's raw modifier and SpoonTir.permittedTypes the clause itself, INTERNED; TirEmitter.sealOf keeps the seal only where the program's own subtypes account for every permitted type AND all land in this file, and records Decision.Kind.WidenedSeal otherwise",
      Rendered("ClassDef")),
    Difference(cId(45), "a `final` field's JMM safe-publication guarantee",
      "JLS 17.5", "UNCITED — a `val` carries the same guarantee",
      NoImpact, Handled, NoTwin, Universal, "SpoonTir.fieldFlags — isFinal/isMutable off the FINAL modifier", Rendered("ValDef")),
    Difference(cId(46), "Java has TWO name namespaces and Scala has one",
      "JLS 6.5", "UNCITED — one namespace, resolved innermost-first",
      Loud, Handled, NoTwin, Universal, "TirEmitter.resolveMemberClashes and TirEmitter.resolveFieldShadowing; MemberRenamer for policy renames", Cited("member-clash")),
    Difference(cId(47), "package-private becomes `private[pkg]`",
      "JLS 6.6.1", "UNCITED — a qualified `private` is the image",
      Loud, Handled, el("T12"), Universal, "Visibility.decide's isPackagePrivate branch; TirEmitter.vis", everyDeclaration),
    Difference(cId(48), "Java's `protected` also grants SAME-PACKAGE access, and accessibility is an input to overload resolution",
      "JLS 6.6.2, 15.12.1", "UNCITED — Scala's `protected` grants no package access",
      Loud, Handled, el("T12"), Universal, "Visibility.decide's isProtected branch, with overrideTarget and qualifierResolves", everyDeclaration),
    Difference(cId(49), "Java's `private` reaches the whole enclosing TOP-LEVEL class; Scala's reaches only the template",
      "JLS 6.6.1", "UNCITED — a qualified `private[TopLevel]` is the image",
      Loud, Handled, NoTwin, Universal,
      "TirEmitter.privateQualifier — Visibility.decide returns a bare private and the emitter qualifies it at a nested owner", everyDeclaration),
    Difference(cId(50), "Java's default access is package-private and Scala's is public — INVERTED",
      "JLS 6.6.1", "UNCITED — emitting nothing means public, which is the wrong default",
      Silent, Handled, el("T12"), Universal, "Visibility.decide — the same branch as JS-C47", everyDeclaration),
    // the promotion that makes `super(args)` expressible (JS-C19) moves the body into the CLASS
    // BODY, where scala has no method to return from. A local `def` is the image (JS-S21's own
    // reason): nothing to name, no interposed construct can capture the jump, and
    // `boundary.Break extends RuntimeException` would otherwise be swallowed by a broad `catch`
    // (JS-S12) java's `return` is never caught by.
    Difference(cId(51), "a `return` in a CONSTRUCTOR body, whose promotion puts it in the class body",
      "JLS 14.17, 8.8.7", "UNCITED — a scala class body is not a method, so `return` is rejected outright",
      Loud, Handled, NoTwin, Universal,
      "TirEmitter.classBodyStats + returnsIn -> a local `def` around plan.primaryBody", Rendered("ClassDef")),
    Difference(cId(52), "`@FunctionalInterface` governs Scala's eta-expansion warning — a static method reference at a non-annotated SAM warns under `-Werror`",
      "JLS 9.8", "SLS — Scala SAM conversion warns when the target type lacks `@FunctionalInterface`",
      Loud, Handled, Predicted, Universal,
      "SpoonFrontend.preservedAnnotations carries @FunctionalInterface; TirEmitter's static MethodRef arm emits an explicit lambda to avoid eta-expansion entirely",
      Rendered("MethodRef")),
    // The complement of JS-C04, which repairs a shadowing DECLARATION this program contains. Here
    // nothing shadows and the loss is still real: the emitted `val` is an accessor a CONSUMER may
    // override, and superclass code would then read the subclass's answer where java read the field.
    Difference(cId(53), "a java `final` FIELD carries two facts onto a `val`: no reassignment, and no OVERRIDE",
      "JLS 8.3, 8.3.1.2 — a field is HIDDEN, never overridden, so a field read is never dynamically dispatched",
      "SLS 5.2 — `final` on a member definition forbids overriding; a bare `val` is an accessor a subclass may override",
      Silent, Handled, Rule44, Universal,
      "TirEmitterMembers.valDef's JS-C53 consult; TirEmitterMembers.mods' isFinal branch, which carries java's FINAL modifier onto the emitted val",
      Rendered("ValDef")),
  )

  // -------------------------------------------------------------------------------------------
  // GENERICS, ARRAYS, ERASURE, BOXING, VARARGS, METHOD REFERENCES — JLS 4, 5, 10, 15.12/15.13, 18
  // -------------------------------------------------------------------------------------------

  val generics: List[Difference] = List(
    // The row is decided at BOTH ends of the pipeline and the two halves are different decisions:
    // the frontend chooses the IMAGE (a `TypeBounds`, or nothing at all where the bound says
    // `Object`) and the emitter chooses the GRAMMAR (`? >: lo <: hi`, with an unresolved bound
    // dropped rather than printed). Attached to either alone it would read as coverage the other
    // half does not have.
    Difference(gId(1), "use-site wildcards, with the matching bound grammar",
      "JLS 4.5.1", "UNCITED — `?` with `<:`/`>:` bounds",
      NoImpact, Handled, el("G2"), Universal,
      "SpoonTir.tpe's CtWildcardReference branch; TirEmitter.tpe's two TypeBounds arms render the grammar",
      Both(everyWildcard, RenderedType("TypeBounds"))),
    Difference(gId(2), "CAPTURE CONVERSION relates two uses of one wildcard in a single expression",
      "JLS 5.1.10", "UNCITED — Scala captures per use, so the two uses are unrelated",
      Loud, Open, el("G24"), Universal, "no rewrite exists; the fix is local (bind to a named local) and none is synthesised", everyWildcard),
    Difference(gId(3), "`? super Object` has exactly one inhabitant",
      "JLS 4.5.1", "UNCITED — the corresponding Scala bound is not the same set",
      Loud, Handled, el("G23"), Universal, "SpoonTir.tpe's `!w.isUpper && isObj` branch", everyWildcard),
    Difference(gId(4), "a captured wildcard on ITERATION has no nameable type",
      "JLS 5.1.10, 14.14.2", "UNCITED — the capture cannot be written, so an alias plus a widening cast is the image",
      Loud, Handled, el("K7"), Universal, "TirEmitter.widenedBinding in the Tree.ForEach arm", Rendered("ForEach")),
    // Decided ABOVE `TirEmitter.tpe`: `deWildcardedArgs` REPLACES the wildcard before any type is
    // rendered, so the `TypeBounds` arm never sees this slot — `JS-G39`'s rule (decision belongs to
    // the CONSUMING node, here the `extends` clause's owner).
    Difference(gId(5), "a wildcard in an `extends` clause takes the parameter's DECLARED bound",
      "JLS 4.5.1, 8.1.4", "UNCITED — no wildcard is legal in a parent, so the bound has to be filled",
      Loud, Handled, el("G7"), Universal,
      "TirEmitter.deWildcardedArgs — a wildcard argument takes its own written bound, else the type PARAMETER's declared upper bound, else AnyRef, resolving left to right so a later bound can name an earlier parameter",
      Rendered("ClassDef")),
    // The name-directed parent-instantiation fill (`inheritedTp`) answers `None` unconditionally
    // since the sge-design revert. What keeps a parent and its overrides in agreement today is
    // `rawParentAlignment`, a whole-program pass — the citation surface, not a dispatch.
    Difference(gId(6), "a de-wildcarded raw PARENT and its overrides must agree",
      "JLS 4.8, 8.4.8.1", "UNCITED — an override is checked against the parent as emitted",
      Loud, Handled, el("G6"), Universal,
      "TirEmitter.rawParentAlignment — deWildcardedArgs decides the parent's arguments and the SAME substitution re-renders the overriding parameters, so agreement is by construction rather than by two rules coinciding",
      Cited("raw-parent-alignment")),
    Difference(gId(7), "a RAW type erases the REFERENCE's generics, not the class's",
      "JLS 4.8", "UNCITED — `[?]` everywhere is the only fill that round-trips across an override",
      Loud, Handled, el("G2"), Universal, "SpoonTir.tpe's empty-actuals branch", everyPlainReference),
    // The evidence named `uncheckedGeneric`'s `inOverridingMember` save-and-restore, whose only
    // reader is the dead `inheritedTp` above — so it explained nothing about what the port emits.
    // The scope-dependence that is LIVE is the frame the raw fill reads: a companion body cannot
    // name the class's own parameters (`inStatic`) and a use nested inside the declaring type can
    // (`nestedInScope`), so one java type renders `[?]` in one member and `[K, V]` in another.
    Difference(gId(8), "the same raw Java type renders DIFFERENTLY per scope, and correctly so",
      "JLS 4.8", "UNCITED — the rendering depends on whether the scope is an override",
      Silent, Handled, el("G3"), Universal,
      "SpoonTir.tpe's empty-actuals branch reading inStatic and nestedInScope — a static frame fills with wildcards and a nested use with the enclosing instantiation's own names",
      everyPlainReference),
    Difference(gId(9), "UNCHECKED CONVERSION at a raw type is legal in Java and is not in Scala",
      "JLS 5.1.9", "UNCITED — an explicit cast is the image",
      Loud, Handled, el("G12"), Universal, "SpoonTir.uncheckedGeneric emitting Tree.Typed, deciding on RENDERED types", everySlot),
    Difference(gId(10), "a RAW anonymous class WITH a body",
      "JLS 4.8, 15.9.5", "UNCITED — no faithful Scala image exists",
      Loud, Refused("the parent's fill and the body's own uses cannot be made consistent"), el("G10"), Universal,
      "no symbol — a refusal by design; SpoonTir.ctorCall is where the shape is recognisable",
      Lowered("CtNewClass", Dispatch.Expression)),
    Difference(gId(11), "a partially-nameable F-BOUNDED raw fill",
      "JLS 4.5, 4.8", "UNCITED — a genuine expressiveness limit; four closing attempts measured worse",
      Loud, Refused("no consistent fill exists for a partially-nameable F-bound"), el("G8"), Universal,
      "TirEmitter.deWildcardedArgs's `fBounded` slot, which STAYS `?` — no finite instantiation satisfies `N <: Node[N,…]` and every unrolling fails the same bound, so the wildcard's weaker claim is the only one scalac accepts; SpoonTir.erasureOfFormal's `seen` cut is the frontend half of the same cycle",
      Rendered("ClassDef")),
    // Both ends again, and here the two are a MINT and a REFUSAL TO PRINT: the frontend finds that
    // the variable resolves to no binder in scope and mints a marker, and the emitter's whole
    // standing obligation is that the marker never reaches the output — `?E` is not a type and does
    // not even lex.
    Difference(gId(12), "a diamond's inference variable has NO NAMEABLE type",
      "JLS 15.9.1, 18", "UNCITED — the marker must never be printed",
      Loud, Handled, el("G2"), Universal,
      "SpoonTir.tpe's CtTypeParameterReference branch minting Symbol.UnresolvedTypeVarPrefix; TirEmitter.typeSym and isUnresolvedTypeVar rendering `?` instead",
      Both(LoweredType("CtTypeParameterReference"), RenderedType("TypeRef"))),
    Difference(gId(13), "Java arrays are COVARIANT, with a runtime `ArrayStoreException`; Scala's are invariant",
      "JLS 4.10.3, 10.10", "UNCITED — `Array[T]` is invariant, so a cast is needed at the use",
      Loud, Handled, el("G1"), Universal, "SpoonTir.coerce's arrayCov clause, through SpoonTir.arrayCovSlot", everySlot),
    Difference(gId(14), "a primitive at a generic slot boxes to the WRAPPER, not to the formal",
      "JLS 5.1.7, 5.3", "UNCITED — the target type of the boxing is the wrapper class",
      Mixed, Partial("scalar and array-initialiser positions are covered; a component that is a bare TYPE PARAMETER is excluded by construction and untested"),
      el("G16"), Universal, "SpoonTir.coerce's boxing predicate, through SpoonTir.boxingSlot, and boxedPrimitive", everySlot),
    Difference(gId(15), "GENERIC ARRAY CREATION is illegal in Java, so only the cast idiom reaches the engine",
      "JLS 15.10.1", "UNCITED — the same restriction, reached through JS-G13's generality",
      NoImpact, Handled, el("G1"), Universal, "SpoonTir.newArray with coerce's arrayCov",
      Lowered("CtNewArray", Dispatch.Expression)),
    Difference(gId(16), "Scala's `Array[T]` needs a `ClassTag` to be CONSTRUCTED",
      "JLS 15.10.1", "UNCITED — `Array[T]` construction requires a `ClassTag[T]` in scope",
      Loud, Open, Predicted, Universal,
      "TirEmitter's Tree.NewArray arm is where the construction is written and where the tag would " +
        "have to be; RewriteTrace's scaladoc names this as the canonical hand-built rewrite, and " +
        "nothing synthesises it. LATENT rather than dormant: java forbids `new T[n]`, and the one " +
        "path that MINTS a NewArray (SpoonTir.varargPack) resolves a concrete element type first",
      Rendered("NewArray")),
    Difference(gId(17), "`.length`, an array's `Class` object, and `T[]::new`",
      "JLS 10.7, 15.13", "UNCITED — `.length` is a method and the array constructor reference is a lambda",
      NoImpact, Handled, el("K8"), Universal, "SpoonTir's array-length branch -> Tree.ArrayLength; TirEmitter's Tree.MethodRef array-ctor branch",
      Both(Rendered("ArrayLength"), Rendered("MethodRef"))),
    Difference(gId(18), "under `noClasspath` a REFERENCE erases and a DECLARATION does not",
      "JLS 4.6", "UNCITED — a producer/consumer erasure conflict the frontend has to resolve",
      Loud, Handled, el("G14"), Universal, "SpoonTir.isExternalCallee driving coerceArgsFixed's erasure cast", everyCall),
    Difference(gId(19), "post-erasure overload clash",
      "JLS 4.6, 8.4.2", "UNCITED — the same erasure, and javac already rejected the clash",
      NoImpact, NonDiff("javac enforced it before the source reached the engine"), el("D1"), NoFix,
      "no symbol; RewriteTrace.callArity is the only arity check",
      NoObligation("a checked NON-difference: javac rejected the clash before the source reached the engine, so no arm has a decision to take")),
    Difference(gId(20), "an unchecked cast's TIMING — a cast that only becomes impossible AFTER a retyping",
      "JLS 5.5, 5.1.9", "UNCITED — the JLS timing is automatic; the retyping interaction is not",
      Silent, Partial("per-phase discipline rather than one central check; the producer side is counted, not coerced"),
      el("K5.6"), Universal, "RetargetBoundaryCheck counts; nothing coerces",
      Unmechanised("the row is per-phase DISCIPLINE across every retyping phase rather than one " +
        "mechanism, so there is no dispatch and no single phase that could own the citation; what " +
        "measures it is the `collection-retarget` lane (`RetargetBoundaryCheck`), which runs beside " +
        "the obligation log and not through it")),
    // The SE16 half is REFUSED rather than absent, and the two words are not interchangeable: an
    // arm exists, it has read the pattern, and what it does is mint a marker with the reason on it.
    // The reason is `ENGINE-LIMITS.md` T18 — java's binding is FLOW-scoped (JLS 6.3.1), so no
    // lexical `val` placement is faithful and a hoisted `var` diverges under capture — and the one
    // shape with an exact image is named there rather than half-built here.
    Difference(gId(21), "`instanceof` is restricted to REIFIABLE types, and SE16 added a pattern binding",
      "JLS 4.7, 15.20.2", "UNCITED — Scala patterns bind, but the binding's SCOPE has no image",
      Mixed, Partial("the reifiable-type restriction is a non-difference; the SE16 pattern BINDING " +
        "is REFUSED per site with a marker — java flow-scopes the binding and scala has no " +
        "expression that binds outside itself"),
      el("T18"), Universal,
      "Tir.Tree.InstanceOf; SpoonTir's instanceof arm marks a CtPattern operand rather than throwing",
      Rendered("InstanceOf")),
    Difference(gId(22), "a raw member access through an ERASED RECEIVER types the CALL, not just the receiver",
      "JLS 4.8", "UNCITED — the receiver view and the member's type through it must be produced together",
      Loud, Handled, el("G21"), Universal, "SpoonTir.erasedRecvResult; ErasedReceiverResultSpec",
      Lowered("CtInvocation", Dispatch.Expression)),
    // Both were misattributions found by re-reading the entry: `ENGINE-LIMITS.md` G23 is the
    // wildcard-bound entry and says nothing about unboxing. Unboxing `null` throws NPE in both
    // languages the same way (scala via `Predef.Integer2int`) — a CROSS-TYPE unbox is JS-E06/K17's
    // fact, not this one.
    Difference(gId(24), "unboxing `null` throws NPE",
      "JLS 5.1.8", "UNCITED — the same unboxing, through the same wrapper method",
      NoImpact, NonDiff("scala unboxes through an instance method on the wrapper, so a null unbox is an NPE on both sides"),
      NoTwin, NoFix, "SpoonTir.coerce's unbox clause, via wrapperOf/valueMethod",
      NoObligation("a checked NON-difference: both languages unbox through an instance method on the wrapper, so a null unbox throws NPE in either")),
    Difference(gId(28), "the `Int`/`Integer` boundary, and scalac's own boxing",
      "JLS 5.1.7", "UNCITED — `Predef.Integer2int` applies where Java unboxed",
      NoImpact, NonDiff("scalac boxes at the same points"), NoTwin, NoFix, "SpoonTir.coerce's same-type-unbox comment, which defers deliberately",
      NoObligation("a checked NON-difference: scalac boxes at the same points, which is why the same-type unbox is deliberately not written out")),
    Difference(gId(29), "diamond inference in the ordinary case",
      "JLS 15.9.1", "UNCITED — Scala infers the same arguments where they are determined",
      NoImpact, Handled, el("G2"), Universal, "SpoonTir.pinTypeArgs",
      Lowered("CtInvocation", Dispatch.Expression)),
    Difference(gId(30), "an UNCONSTRAINED method type parameter: Java infers its BOUND and Scala infers `Nothing`",
      "JLS 18.1.3", "UNCITED — an unconstrained inference variable resolves to the lower bound",
      Loud, Handled, el("G22"), Universal,
      "SpoonTir.pinUnconstrainedTypeArgs, whose reach is bounded by ENGINE-LIMITS G24's still-open vacuous-bound case",
      Lowered("CtInvocation", Dispatch.Expression)),
    Difference(gId(31), "a POLY EXPRESSION must never be cast as a whole",
      // UNCITED for the same reason JS-G33 is, and deliberately not papered over: the behaviour is
      // PROBED (scala 3.8.4 SAM-converts a function literal at a wildcard-applied, a contravariant
      // and a bare-wildcard slot) and no Scala 3 reference page for the eligibility rule was
      // located. A citation invented to move `catalog(uncited)` would be worse than the gap.
      "JLS 15.2", "UNCITED — no Scala 3 reference page located; the SAM-conversion behaviour is probed, not cited",
      // MIXED, and both directions are measured: casting a method reference to a callee's own
      // variable is a compile error (`ENGINE-LIMITS.md` G12), while casting a LAMBDA into a
      // functional interface compiles perfectly and throws at run time (K17 face 1, 27 tests).
      Mixed, Handled, el("K17"), Universal,
      "SpoonTir.polyExpression, the ONE predicate, and SpoonTir.polyArgsUncast, which answers for it at both call dispatches",
      Lowered("CtInvocation", Dispatch.Expression)),
    Difference(gId(32), "a callee's OWN type variables never resolve at the caller",
      "JLS 18.5.1", "UNCITED — the callee's variables are not in scope at the call site",
      Loud, Handled, el("G12"), Universal, "SpoonTir.uncheckedGeneric's tpResolvable/calleeBounded decline", everyCall),
    Difference(gId(33), "SAM conversion eligibility",
      "JLS 9.8, 15.27.3", "UNCITED — no Scala 3 reference page located for the eligibility rule",
      Loud, Handled, Predicted, Universal, "TirEmitter.samAscribed", Rendered("MethodRef")),
    Difference(gId(34), "a Java INTERSECTION in a cast becomes Scala's `&`",
      "JLS 4.9, 15.16", "UNCITED — `A & B` is the image",
      NoImpact, Handled, Predicted, Universal, "SpoonTir.tpe's CtIntersectionTypeReference branch; TirEmitter.tpe's AndType arm",
      Rendered("Typed")),
    Difference(gId(35), "Scala CHECKS an F-bound where javac does not",
      "JLS 4.4", "UNCITED — the bound is checked at every use, so a naive erasure is rejected",
      Loud, Handled, el("G9"), Universal, "SpoonTir.erasureOfFormal's `seen` cycle cut",
      Both(Rendered("ClassDef"), Rendered("DefDef"))),
    Difference(gId(36), "an override's TYPE-PARAMETER BOUNDS must be copied from the parent",
      "JLS 8.4.4, 8.4.2", "UNCITED — an override's own bounds must match the parent's, not be re-derived",
      Loud, Open, el("G19"), Universal, "no symbol copies parent bounds; SpoonTir.erasureOfFormal is per-declaration",
      Rendered("DefDef")),
    Difference(gId(37), "`T...` is an `Array[T]`, materialised at the port's OWN call sites",
      "JLS 8.4.1, 15.12.4.2", "UNCITED — a declared vararg becomes an array parameter, so callers must pack",
      Loud, Handled, el("K6.5"), Universal, "SpoonTir.varargPack -> Tree.NewArray for an in-program callee", everyCall),
    Difference(gId(38), "a vararg slot ALREADY holding an array must not be re-packed — and neither a primitive array nor an under-dimensioned one forwards",
      "JLS 15.12.4.2", "UNCITED — ASSIGNABILITY to the parameter's array type decides, never `is an array`: a primitive array is assignable to nothing but its own, and at a `T[]...` slot a one-dimensional argument is assignable to the COMPONENT only",
      Silent, Handled, el("G26"), Universal,
      "SpoonTir.varargHoldsArray — componentAgrees, carrying both the primitive test and the arity test `dims(arg) >= dims(comp) + 1`; shared with SpoonTir.callConsults so the consult and the translation cannot disagree",
      everyCall),
    Difference(gId(39), "an EXTERNAL callee's `T...` is read by scalac as a REPEATED parameter",
      "JLS 15.12.4.2", "UNCITED — a class file's `T...` is a repeated parameter, so a bare array conforms as ONE element",
      Silent, Handled, el("K6.5"), Universal, "Tir.Tree.Repeated, emitted when SpoonTir.isExternalCallee holds",
      // …and the emitter half attaches at `Apply`, NOT `Repeated`: `TirEmitter.argTerms` FLATTENS
      // a `Tree.Repeated` argument before the dispatch sees it, so the node never enters `term` and
      // an attachment there would be unfalsifiable coverage. `SpoonKinds.Claim.Positional` seen from
      // the other end — decided where the flattening is, the enclosing `Apply`.
      Both(everyCall, Rendered("Apply"))),
    Difference(gId(40), "the COMPOSITION of JS-G38 and JS-G39 — an array forwarded through an external vararg slot",
      "JLS 15.12.4.2", "UNCITED — only a spec over the composition finds it",
      Loud, Handled, el("K6.5"), Universal, "SpoonTir.passedThrough -> Tir.Tree.Spread",
      Both(everyCall, Rendered("Spread"))),
    // `SpoonTir.annotationsOf` has NO ignore list and carries every MARKER annotation, so
    // `@SafeVarargs` reaches the emitted file onto a vararg JS-G37 already turned into a plain
    // `Array` parameter, where scalac neither checks nor derives from it.
    Difference(gId(41), "`@SafeVarargs` and heap pollution",
      "JLS 9.6.4.7, 4.12.2", "UNCITED — no `@SafeVarargs` and no unchecked-vararg warning exist, so the unsoundness is carried over unmarked",
      // NOT `Handled`, and not `Open` either. The port reproduces java's heap pollution EXACTLY —
      // there is nothing to translate and a phase that "fixed" it would emit a different program —
      // so the missing half is java's CONVERSATION about it: javac's warning at the declaration and
      // the annotation that answers it. That half now has a number instead of a silence.
      Silent, Partial("java's unsoundness is reproduced exactly and needs no translation; what has " +
        "no scala image is the ACKNOWLEDGEMENT — javac warns at a non-reifiable vararg and scalac " +
        "does not, and the `@SafeVarargs` that answers the warning is emitted onto a method that is " +
        "not even variadic in scala. Counted per declaration rather than translated"),
      Predicted, Universal,
      "SpoonTir.annotationsOf carries the marker and TirEmitter.annots renders it; HeapPollutionCheck counts both the acknowledged and the unacknowledged declarations",
      Rendered("DefDef")),
    Difference(gId(42), "a generic vararg's COMPONENT element type has four layered sources",
      "JLS 15.12.4.2, 18", "UNCITED — the DECLARATION is the source that is correct in all four",
      Loud, Handled, el("G1"), Universal, "SpoonTir.varargPack's elemRef, in its four documented steps", everyCall),
    Difference(gId(43), "METHOD REFERENCES are five forms sharing one syntax — static, unbound, bound, constructor, array constructor",
      "JLS 15.13", "UNCITED — each form is a different lambda, and `Flags.isStatic` is the discriminator",
      Loud, Handled, el("K8"), Universal, "SpoonTir.methodRef -> Tir.Tree.MethodRef; TirEmitter's Tree.MethodRef arm",
      Both(Lowered("CtExecutableReferenceExpression", Dispatch.Expression), Rendered("MethodRef"))),
    Difference(gId(48), "a REIFIED type occurrence asks about a RUNTIME OBJECT, so a retyping moves the question and not the answer",
      "JLS 15.20.2, 5.5, 4.7",
      "SLS 12.1 — `isInstanceOf`/`asInstanceOf` test the ERASED runtime class, exactly as java does",
      // `Partial`, not `Handled` — java's question is answered over BOTH representations wherever
      // a live view exists; where the target is a CONCRETE retyped type there is no view, so the
      // port ships java's question asked of the wrong classes, counted not translated.
      // `ENGINE-LIMITS.md` K18, rule (i) exempts a partial row that states which half is missing.
      Silent, Partial("a reified occurrence whose target is a CONCRETE retyped type — a hash map, a " +
        "buffer, a tuple — has no live view to answer over, so it is REFUSED and counted " +
        "(CollectionBoundaryCheck.Issue.ReifiedOccurrence) rather than answered, and one corpus " +
        "port's last remaining test failure is exactly that refusal"),
      el("K18"), Universal,
      "CollectionsTransform.reifiedTest/reifiedCast -> JavaCollections.Reified; the concrete-target " +
        "refusal is CollectionBoundaryCheck.Issue.ReifiedOccurrence",
      Cited("collections")),
    // A SUBSIGNATURE is java's licence to override a generic method with its own ERASURE (JS-G07,
    // JS-G14, read at an OVERRIDE EDGE): scala has no rule letting a method with no type parameters
    // override one with them. `Loud` at both ends — `E038` at the narrowing declaration, `needs to
    // be abstract` at every concrete class below it.
    Difference(gId(49), "an UNCHECKED override: java lets a method's ERASURE be a SUBSIGNATURE of a generic one",
      "JLS 8.4.2, 8.4.8.1", "UNCITED — scala has no subsignature rule; a method with no type parameters cannot override one with them",
      Loud, Handled, el("G8.10"), Universal,
      "SpoonTir.unwritableResultVars erasing an F-BOUNDED, RESULT-ONLY method type parameter to its own " +
        "bound at the DECLARATION, which is the only instantiation ENGINE-LIMITS G8 could not refute",
      LoweredType("CtTypeParameterReference")),
  )

  /** every language row. THE COUNT IS DERIVED — it is not written down anywhere, here or in any
    * document, because a hand-written total is what `PortabilityCheck`'s phantom "34 rules" was. */
  val all: List[Difference] = expressions ++ statements ++ classes ++ generics

  val byId: Map[DiffId, Difference] = all.map(d => d.id -> d).toMap

  // -------------------------------------------------------------------------------------------
  // THE ATTACHMENT INDEX — what a dispatch OWES, and what a lane may claim.
  // -------------------------------------------------------------------------------------------

  /** which rows the frontend's lowering dispatch owes a consult for, keyed on (Spoon kind,
    * dispatch). `Dispatch.Either` expands to both; a kind nothing attaches to answers `Nil`, the
    * fast path [[Lowering.of]] takes. */

  /** a row's attachment, FLATTENED — `Both` is a tree and every index below wants its leaves. One
    * function, so the three readers (lowering index, rendering index, `mechanised`) never disagree. */
  def leaves(a: Attaches): List[Attaches] = a match
    case Attaches.Both(x, y) => leaves(x) ++ leaves(y)
    case one                 => List(one)

  private val owed: Map[(String, Dispatch), List[DiffId]] =
    all
      .flatMap(d => leaves(d.attaches).collect { case Attaches.Lowered(k, disp) =>
        val at = disp match
          case Dispatch.Either => List(Dispatch.Statement, Dispatch.Expression)
          case one             => List(one)
        at.map(a => (k, a) -> d.id)
      }.flatten)
      .groupMap(_._1)(_._2)

  def owedAt(kind: String, dispatch: Dispatch): List[DiffId] = owed.getOrElse((kind, dispatch), Nil)

  /** …and the EMITTER's half, keyed on the `Tree` kind. No `Dispatch`: see [[Rendering]]. */
  private val owedRender: Map[String, List[DiffId]] =
    all
      .flatMap(d => leaves(d.attaches).collect { case Attaches.Rendered(k) => k -> d.id })
      .groupMap(_._1)(_._2)

  def owedAtRender(kind: String): List[DiffId] = owedRender.getOrElse(kind, Nil)

  /** every `Tree` kind any row attaches to — what a spec compares against the IR's own node set, so
    * a row naming a kind that does not exist is caught rather than silently owed by nothing. */
  def renderedKinds: Set[String] = owedRender.keySet

  /** …and the TYPE surface's two halves, keyed on the Spoon reference kind and on the `TypeRepr`
    * case's `productPrefix`. Two indexes rather than one for the reason [[Typing]] is two entry
    * points: the keys are two vocabularies, and a shared map would let a misspelt reference name
    * silently answer for a `TypeRepr` case (and the reverse). */
  private val owedLowerType: Map[String, List[DiffId]] =
    all
      .flatMap(d => leaves(d.attaches).collect { case Attaches.LoweredType(k) => k -> d.id })
      .groupMap(_._1)(_._2)

  def owedAtLowerType(kind: String): List[DiffId] = owedLowerType.getOrElse(kind, Nil)

  private val owedRenderType: Map[String, List[DiffId]] =
    all
      .flatMap(d => leaves(d.attaches).collect { case Attaches.RenderedType(k) => k -> d.id })
      .groupMap(_._1)(_._2)

  def owedAtRenderType(kind: String): List[DiffId] = owedRenderType.getOrElse(kind, Nil)

  /** every Spoon REFERENCE kind any row attaches to — held against `SpoonKinds.references` by
    * `ReferenceKindTotalitySpec`, for `renderedKinds`' reason. */
  def loweredTypeKinds: Set[String] = owedLowerType.keySet

  /** every `TypeRepr` case any row attaches to — held against the class files by
    * `EmissionFieldCoverageSpec`. */
  def renderedTypeKinds: Set[String] = owedRenderType.keySet

  /** rows whose discharge surface EXISTS — the only rows an "unreached" claim may be made about. A
    * `Both` row counts as mechanised when EVERY leaf is. Stated as the COMPLEMENT of the two honest
    * negatives rather than a list of surfaces, so a surface added tomorrow is included by
    * construction (`just catalog-coverage`'s own filter once named only what existed, CLAUDE.md §4.56). */
  def mechanised: List[Difference] = all.filter(d => leaves(d.attaches).forall {
    case _: Attaches.Unmechanised | _: Attaches.NoObligation => false
    case _                                                   => true
  })

  /** rows whose discharge surface is NOT built, which is the number that says "we are not measuring
    * these". Reported in its own lane rather than folded into a total (§3.6's rule about a number
    * that hides the half that matters, applied to coverage). */
  def unmechanised: List[Difference] =
    all.filter(d => leaves(d.attaches).exists(_.isInstanceOf[Attaches.Unmechanised]))

  /** ids that were ABSORBED and are therefore out of circulation forever (see [[Retired]]).
    *
    * Each `why` is the ABSORBED entry's own argument, not a later rationalisation: a brief that
    * says "already detailed under SG14" or "see C4" has settled its own row, and recording that
    * sentence is what makes the retirement auditable rather than a deletion. */
  val retired: List[Retired] = List(
    Retired(eId(16), Some(cId(8)), "constant-variable inlining — the CAUSE is class-init triggering, so the class chapter owns it"),
    Retired(cId(24), None, "hiding vs overriding is a SUMMARY of JS-C01/C02/C03/C06; it became a section header, not a row"),
    Retired(cId(26), Some(cId(4)), "a field read is a virtual call — the brief's own status line is `see C4`"),
    Retired(cId(41), Some(sId(5)), "`switch` over an enum is JS-S05's fall-out rule applied to an enum selector"),
    Retired(gId(23), Some(eId(1)), "the Integer cache is a corollary of the shipped `==` -> `eq` rule, with no boxed-specific code"),
    Retired(gId(25), Some(eId(1)), "boxed `==` is the same corollary, read at the other operand kind"),
    Retired(gId(26), Some(gId(14)), "explicit boxing to a wrapper — the brief's own text: `already detailed under SG14`"),
    Retired(gId(27), Some(eId(5)), "conditional-operator boxing is one JLS section with JS-E05, which holds both facets"),
    Retired(gId(44), Some(gId(43)), "the unbound-instance method-reference form; JS-G43 holds all five as one row"),
    Retired(gId(45), Some(gId(43)), "the bound-instance form; see JS-G44"),
    Retired(gId(46), Some(gId(43)), "the constructor and array-constructor forms; see JS-G44"),
    Retired(gId(47), Some(gId(43)), "the arity rule shared by all five forms; see JS-G44"),
  )

/** catalog ids, spelled the way a lowering arm spells them: `JS.E(3)` is `JS-E03`. A FUNCTION
  * rather than 126 named vals — citing an id the registry lacks is caught at the CONSULT
  * (`CatalogCheck.consultsOpenRows`) rather than by a missing `val`, as `SpoonKinds` does for
  * node kinds. */
object JS:
  def E(n: Int): DiffId = DiffId(Area.E, n)
  def S(n: Int): DiffId = DiffId(Area.S, n)
  def C(n: Int): DiffId = DiffId(Area.C, n)
  def G(n: Int): DiffId = DiffId(Area.G, n)
