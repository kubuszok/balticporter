package balticporter.tir

/** Typed IR (TIR) — the re-compiler's working representation.
  * Shaped like `scala.quoted.Quotes#reflect` but exposes `Origin`, `SymTag` and a whole-program
  * `XrefIndex`; every node carries a fully structured `TypeRepr`, resolved from Spoon.
  * DESIGN.md §2.
  */

/** Provenance to the original source. Our addition over Quotes' positions. */
final case class Origin(javaPath: String, line: Int, col: Int)
object Origin:
  val synthetic: Origin = Origin("<synthetic>", 0, 0)

/** WHICH `try` — see [[Tree.Try.id]] for why neither an `Origin` nor object identity can be that.
  *
  * A counter and nothing else: it is never printed, never emitted, never written to an artifact and
  * never compared across runs, so no output depends on the order tokens are handed out in. */
opaque type TryId = Long
object TryId:
  private val seq                = java.util.concurrent.atomic.AtomicLong(0L)
  def fresh(): TryId             = seq.incrementAndGet()
  extension (t: TryId) def raw: Long = t

/** WHICH `switch` — see [[Tree.Match.id]]. Same contract, same reasons, as [[TryId]]. */
opaque type MatchId = Long
object MatchId:
  private val seq                      = java.util.concurrent.atomic.AtomicLong(0L)
  def fresh(): MatchId                 = seq.incrementAndGet()
  extension (m: MatchId) def raw: Long = m

/** Stable symbol identity — interned, NEVER a string (the analog of `reflect.Symbol`
  * as a handle). Ergonomic queries hang off it as `(using Program)` extensions,
  * mirroring Quotes' `(using Quotes)` symbol methods. */
opaque type SymId = Int
object SymId:
  val None: SymId                   = -1
  def apply(i: Int): SymId          = i
  extension (s: SymId) def raw: Int = s

/** What a java METHOD REFERENCE's referenced executable declares — see [[Tree.MethodRef.referent]].
  * JLS 15.13.1: a `static` method is a qualified name; an instance method is unbound, receiver
  * becomes the SAM's first parameter. Both carry ARITY — scala does not eta-expand a nullary
  * method from a bare name. `ENGINE-LIMITS.md` G32.
  */
enum Referent:
  case Static(arity: Int)
  case Instance(arity: Int)

// ---------------------------------------------------------------------------
// Flags — mirrors `reflect.Flags` (superset of what we currently populate).
// ---------------------------------------------------------------------------
final case class Flags(
    isAbstract: Boolean = false,
    isFinal: Boolean = false,
    isSealed: Boolean = false,
    isTrait: Boolean = false,
    isModule: Boolean = false, // `object`
    isEnum: Boolean = false,
    /** True iff java declared this a `record` (JLS 8.10) AND every component in [[Symbol.components]]
      * survived (a `dropMethods`'d accessor clears it) — licenses synthesising the accessor,
      * canonical constructor, `equals`/`hashCode`/`toString`. Not derivable from `extends
      * java.lang.Record` (scalac emits no JVM record and accepts that clause on non-records too).
      */
    isRecord: Boolean = false,
    /** Java `@interface` — a declaration of an ANNOTATION type, not an ordinary interface. */
    isAnnotation: Boolean = false,
    isOpaque: Boolean = false, // `opaque type`

    isCase: Boolean = false,
    isImplicit: Boolean = false,
    isGiven: Boolean = false,
    isOverride: Boolean = false,
    isMutable: Boolean = false, // `var`
    isLazy: Boolean = false,
    isParam: Boolean = false,
    isVararg: Boolean = false, // Java `T...` → Scala `T*`
    isParamAccessor: Boolean = false,
    isPrivate: Boolean = false,
    isProtected: Boolean = false,
    /** Java's fourth access level — package-private (no modifier written). The JLS-effective level,
      * not modifier presence: an interface member implicitly `public`, an enum constructor
      * implicitly `private`, are not this. Exactly one of `isPrivate`/`isProtected`/
      * `isPackagePrivate` is set, or all three clear means public. DESIGN.md §8.7.
      */
    isPackagePrivate: Boolean = false,
    isStatic: Boolean = false, // JavaStatic
    isNative: Boolean = false, // Java `native` (JNI) — a Panama-FFI rewrite target
    isCovariant: Boolean = false,
    isContravariant: Boolean = false,
    /** the FRONTEND resolved a declaration for this type — in the source set, on the frontend
      * classpath, or from the class file. AFFIRMATIVE evidence, never a refutation (CLAUDE.md
      * §4.56): a symbol nothing set this on may be one a PHASE minted, and that is not a
      * class-file name. Set by `SpoonTirBuilder.typeSym`; read by `PackageRenameTransform`,
      * where only a resolved external keeps its own FQN. */
    isResolved: Boolean = false,
)

/** Open, extensible domain semantics attached to symbols by transforms. */
trait SymTag

// ---------------------------------------------------------------------------
// Trivia — the ORIGINAL COMMENTS, carried through the port.
// ---------------------------------------------------------------------------

/** Comment kinds preserved from the Java source. */
enum TriviaKind:
  case Line, Block, Javadoc

/** One comment, verbatim — delimiters included, sliced out of the original source buffer rather
  * than re-printed from a parsed model, so exact wording (licence text, `@param` alignment,
  * `<pre>` blocks) survives. CLAUDE.md §4.57. Deliberately NOT shared with the frozen
  * `balticporter.core.Trivia` (BIR path).
  */
final case class Trivia(kind: TriviaKind, text: String)

/** A Java annotation on a declaration — `@Test`, `@Override`, `@Deprecated`, `@Null`.
  * `args` are its element values, by name (a single-element `@A(x)` is named `value`), carried as
  * real `Term`s rather than text — a dropped argument is the same silent-omission defect as a
  * dropped annotation (e.g. a `@Test` that fails to survive runs zero tests and reports success).
  */
final case class Annot(tpe: TypeRepr, args: List[(String, Term)], origin: Origin)

/** A declaration's symbol record (the analog of `reflect.Symbol`'s backing data).
  * `info` is its type: a value/field type, a `MethodType`/`PolyType` for methods, a
  * `TypeBounds` for type params/abstract types, or the class `TypeRef` for classes.
  * Cross-references (declarations, members, usages) are answered by `Program`, so a
  * plain record stays serializable while the graph lives in the tables. */
final case class Symbol(
    id: SymId,
    name: String,
    fullName: String,
    flags: Flags,
    owner: SymId,           // SymId.None at the root
    info: TypeRepr,
    // no `privateWithin`: a `private[p]` qualifier is derived from the emitter's current package, not carried as a symbol (DESIGN.md §8.7).
    origin: Origin = Origin.synthetic,
    tags: Set[SymTag] = Set.empty,
    /** the declaration's Java annotations, in source order. See [[Annot]] — losing these is a
      * silent correctness defect, not a formatting one. */
    annotations: List[Annot] = Nil,
    /** annotations the frontend could NOT carry, by name — reported by `OmissionCheck` rather
      * than discarded, so a gap here is a number on every run. */
    droppedAnnotations: List[String] = Nil,
    /** The member's parameter spelling — the half of member identity [[fullName]] does not carry;
      * see [[Descriptor]] for the grammar. `None` means: not an executable, OR an unresolvable
      * external member (a real gap), OR every formal is unnameable ([[Descriptor.total]]). Never
      * folded into [[fullName]] or printed by `TirPrinter.canonical` — see [[MemberKey]].
      */
    descriptor: Option[Descriptor] = None,
    /** Java's `permits` clause (JLS 8.1.1.2) — subtype `SymId`s, interned rather than named so a
      * permitted type the parse never saw resolves to an external stub instead of a rename-sensitive
      * string (CLAUDE.md §4.56). Empty for a non-sealed type or an inferred clause. Not a [[Flags]]
      * (a class-header clause, not a modifier), though it travels with `Flags.isSealed`.
      */
    permits: List[SymId] = Nil,
    /** Java's record components (JLS 8.10.1), in declaration order — required because `equals`/
      * `hashCode`/`toString`/deconstruction all read them positionally, so this cannot be a `Set`
      * (sorted by source position at harvest). Interned rather than name-keyed: field, accessor and
      * component share one java name, and emission renames the field (`x` -> `x$field`).
      */
    components: List[RecordComponent] = Nil,
)

/** One java record component (JLS 8.10.1). `field` and `accessor` are carried separately because
  * java reads different ones: `equals`/`hashCode`/`toString` read the FIELD, a record pattern
  * deconstructs through the ACCESSOR (JLS 14.30.1) — an overridden accessor can make them diverge.
  * @param name the component's java name, before any clash resolution.
  */
final case class RecordComponent(name: String, field: SymId, accessor: SymId)

object Symbol:

  /** The `fullName` prefix a frontend mints for a type variable it could not resolve a binder for
    * (e.g. a diamond's inferred argument). `?` is unambiguous since no java identifier can start
    * with it. Read through [[isUnresolvedTypeVar]] by both frontend and emitter — such a symbol must
    * never reach emitted output. `ENGINE-LIMITS.md` G2.
    */
  val UnresolvedTypeVarPrefix = "?"

  def isUnresolvedTypeVar(fullName: String): Boolean = fullName.startsWith(UnresolvedTypeVarPrefix)

// ---------------------------------------------------------------------------
// Constants — mirrors `reflect.Constant`.
// ---------------------------------------------------------------------------
enum Constant:
  case BoolC(v: Boolean)
  case ByteC(v: Byte)
  case ShortC(v: Short)
  case CharC(v: Char)
  case IntC(v: Int)
  case LongC(v: Long)
  case FloatC(v: Float)
  case DoubleC(v: Double)
  case StringC(v: String)
  case NullC
  case UnitC
  case ClassOfC(tpe: TypeRepr)

// ---------------------------------------------------------------------------
// TypeRepr — the STRUCTURED type algebra, mirroring `reflect.TypeRepr`. Every
// constituent stays addressable: transforms can rewrite applied args, split/compose
// mixins (And/Or), read self-types (ThisType) and F-bounds (TypeBounds on params).
// ---------------------------------------------------------------------------
sealed trait TypeRepr
object TypeRepr:
  case object NoPrefix                                                      extends TypeRepr
  case object NoType                                                        extends TypeRepr
  final case class ConstantType(value: Constant)                           extends TypeRepr
  /** named type reference to a type symbol (`prefix#sym`); prefix `NoPrefix` when plain. */
  final case class TypeRef(prefix: TypeRepr, sym: SymId)                    extends TypeRepr
  /** singleton / path-dependent: a term's type (`p.type`, the prefix of `p.T`). */
  final case class TermRef(prefix: TypeRepr, sym: SymId)                    extends TypeRepr
  /** `C.this` — self reference (needed for self-types and path-dependence). */
  final case class ThisType(cls: SymId)                                     extends TypeRepr
  final case class SuperType(thistpe: TypeRepr, supertpe: TypeRepr)         extends TypeRepr
  /** applied type constructor `tycon[args]`; args may be `TypeBounds` (wildcards). */
  final case class AppliedType(tycon: TypeRepr, args: List[TypeRepr])       extends TypeRepr
  /** intersection / mixin: `A & B` (`A with B`). */
  final case class AndType(left: TypeRepr, right: TypeRepr)                 extends TypeRepr
  /** union: `A | B`. */
  final case class OrType(left: TypeRepr, right: TypeRepr)                  extends TypeRepr
  final case class ByNameType(underlying: TypeRepr)                         extends TypeRepr
  /** bounds `>: lo <: hi` — also the wildcard type `?` (a `TypeBounds` arg). */
  final case class TypeBounds(low: TypeRepr, hi: TypeRepr)                  extends TypeRepr
  /** structural refinement `parent { type/def name: info }`. */
  final case class Refinement(parent: TypeRepr, name: String, info: TypeRepr) extends TypeRepr
  /** method type `(params): result`; contextual/implicit flag for `using` clauses. */
  final case class MethodType(params: List[(String, TypeRepr)], result: TypeRepr, isImplicit: Boolean = false)
      extends TypeRepr
  /** poly method type `[tparams]: result` with per-param bounds. */
  final case class PolyType(params: List[(String, TypeBounds)], result: TypeRepr) extends TypeRepr
  /** higher-kinded type lambda `[X <: U] =>> body`. */
  final case class TypeLambda(params: List[(String, TypeBounds)], body: TypeRepr) extends TypeRepr
  /** reference to the i-th parameter of an enclosing Method/Poly/TypeLambda binder. */
  final case class ParamRef(binder: TypeRepr, idx: Int)                     extends TypeRepr

  val AnyBounds: TypeBounds = TypeBounds(NoType, NoType) // NoType lo/hi ⇒ Nothing/Any

// ---------------------------------------------------------------------------
// Trees — mirrors `reflect.Tree`: Tree > Statement > {Definition, Term}; TypeTree
// (a syntactic type carrying its `TypeRepr`) is a sibling. Not every Tree has a
// `tpe` (Definitions carry a `symbol`, Terms a `tpe`) — same split as Quotes.
// ---------------------------------------------------------------------------
sealed trait Tree:
  def origin: Origin

sealed trait Statement extends Tree

sealed trait Definition extends Statement:
  def symbol: SymId

sealed trait Term extends Statement:
  def tpe: TypeRepr

/** a syntactic type occurrence (`reflect.TypeTree`) — carries its resolved
  * `TypeRepr` and an `Origin`, bridging tree positions and the type algebra. */
final case class TypeTree(tpe: TypeRepr, origin: Origin) extends Tree

object Tree:
  // ---- definitions ----
  /** class / trait / object / enum. `parents` are the (typed) super constructors/mixins;
    * `selfType` carries `self: S =>`; `tparams` are the class's own type params as `TypeDef`s
    * whose `rhs` is a `TypeBounds`, so an F-bound like `class C[T <: IRich[T]]` is walkable.
    */
  final case class ClassDef(
      symbol: SymId,
      parents: List[Term | TypeTree],
      selfType: Option[TypeTree],
      body: List[Statement],
      origin: Origin,
      tparams: List[TypeDef] = Nil,
      enumCases: List[EnumCase] = Nil,
      /** the type's OWN comments — its Javadoc and anything else written directly above it. */
      leading: List[Trivia] = Nil,
      /** Comments belonging to the FILE rather than this type — everything above `package` plus
        * anything on the imports (where a licence notice lives). Kept separate from `leading`
        * because emitting it there would land below the `package` clause. Non-empty only on a
        * top-level unit; a two-type java file becomes two Scala files, each carrying the notice.
        */
      unitLeading: List[Trivia] = Nil,
  ) extends Definition

  /** one Java enum constant — `NAME(ctorArgs) { body }`. Lowered to a Scala `case object`
    * extending the enum's sealed class. `body` carries per-constant method overrides. */
  final case class EnumCase(symbol: SymId, ctorArgs: List[Term], body: List[Statement], origin: Origin,
                            /** the constant's own comments. */
                            leading: List[Trivia] = Nil)

  /** type alias / abstract type member / type parameter (`type T = …` / `type T <: U`).
    * For a type parameter, `rhs` carries a `TypeBounds`. */
  final case class TypeDef(symbol: SymId, rhs: TypeTree, origin: Origin) extends Definition

  final case class DefDef(
      symbol: SymId,
      paramss: List[List[ValDef]],
      returnTpt: TypeTree,
      rhs: Option[Term],
      origin: Origin,
      tparams: List[TypeDef] = Nil,
      /** the method's/constructor's own comments — Javadoc above the declaration. */
      leading: List[Trivia] = Nil,
  ) extends Definition

  final case class ValDef(symbol: SymId, tpt: TypeTree, rhs: Option[Term], origin: Origin,
                          /** the field's/local's own comments. */
                          leading: List[Trivia] = Nil) extends Definition

  // ---- terms (each reference resolves to a SymId) ----
  final case class Ident(sym: SymId, tpe: TypeRepr, origin: Origin)                     extends Term
  final case class Select(qual: Term, sym: SymId, tpe: TypeRepr, origin: Origin)        extends Term
  final case class Literal(const: Constant, tpe: TypeRepr, origin: Origin)              extends Term
  final case class This(cls: SymId, tpe: TypeRepr, origin: Origin)                      extends Term
  /** `super` (receiver of `super.m(...)` / `super(...)`) — distinct from `this` so the
    * backend can emit `super`-dispatch and constructor delegation correctly. */
  final case class Super(cls: SymId, tpe: TypeRepr, origin: Origin)                     extends Term
  /** The body of a java anonymous class — `new Base(args) { members }`. `symbol` is a synthetic
    * type that owns the members; `body` carries fields/methods/init blocks; `dropped` names any
    * member the frontend could not translate, for [[OmissionCheck]]. An empty `body` is meaningful
    * (`new Base(){}`); `New.anon = None` means "not anonymous at all".
    */
  final case class AnonClass(symbol: SymId, body: List[Statement], origin: Origin, dropped: List[String] = Nil,
                             /** What the class file says about the type this anonymous class named
                               * — see [[Sam]] for why the frontend answers this rather than the
                               * acting phase. Defaults to [[Sam.Answer.unknown]], the conservative
                               * arm: an unasked tree refuses loudly in `idiom(refused)`.
                               */
                             sam: Sam.Answer = Sam.Answer.unknown)

  /** `new T` / `new T { … }`. `anon` is present exactly when Java wrote an anonymous-class body
    * (a `CtNewClass`); its members are Scala's anonymous-class-expression members. Dropping it
    * is the failure this node's shape exists to prevent — a listener that compiles and does
    * nothing. */
  final case class New(tpt: TypeTree, tpe: TypeRepr, origin: Origin, anon: Option[AnonClass] = None) extends Term
  final case class Apply(fun: Term, args: List[Term], method: SymId, tpe: TypeRepr, origin: Origin) extends Term
  final case class TypeApply(fun: Term, targs: List[TypeTree], tpe: TypeRepr, origin: Origin)       extends Term
  /** @param compound `Some((op, narrowOpt))` for a compound assignment (`lhs op= rhs`): `op` is
    *   the operator, `narrowOpt` the implicit narrowing type when the result casts back to the
    *   lvalue's type (JLS 15.26.2, e.g. `byte += int`). `rhs` is the right operand only; `lhs`
    *   appears once in the node. `None` for a plain assignment.
    */
  final case class Assign(lhs: Term, rhs: Term, tpe: TypeRepr, origin: Origin,
                          compound: Option[(String, Option[TypeRepr])] = None)          extends Term
  /** @param trailing comments after the block's last statement — the one position a frontend that
    *   folds comments onto the FOLLOWING statement cannot carry otherwise (e.g. an empty override
    *   body whose only content is `// Do nothing by default.`). Placed exactly at end-of-block,
    *   never hoisted to a surviving node. `ENGINE-LIMITS.md` V1.
    */
  final case class Block(stats: List[Statement], expr: Term, tpe: TypeRepr, origin: Origin,
                         trailing: List[Trivia] = Nil) extends Term
  /** Anonymous function (`reflect.Closure`/`Block(DefDef,Closure)` simplified).
    * @param resultTpt the SAM method's OWN result type (never the interface's `tpe`) — needed
    *   since java lambda bodies allow `return` (leaves the lambda), restored via a nested `def`
    *   needing a result type. `None`: falls back to void-lambda detection, refuses the rest,
    *   counted (`ENGINE-LIMITS.md` M6). */
  final case class Lambda(params: List[ValDef], body: Term, tpe: TypeRepr, origin: Origin,
                          resultTpt: Option[TypeTree] = None) extends Term
  final case class If(cond: Term, thenp: Term, elsep: Term, tpe: TypeRepr, origin: Origin) extends Term
  final case class Typed(expr: Term, tpt: TypeTree, tpe: TypeRepr, origin: Origin)      extends Term
  /** varargs sequence (`reflect.Repeated`). */
  final case class Repeated(elems: List[Term], tpe: TypeRepr, origin: Origin)           extends Term
  /** `expr*` — an array passed through a repeated (`T...`) parameter as one argument. The mirror
    * of [[Repeated]]: a class-file callee reads `T...` as varargs, so a bare array would conform as
    * ONE element; the spread is the faithful form (`ENGINE-LIMITS.md` K6.5). `tpe` is the array's
    * own type — `*` is a fact about position, not about the type.
    */
  final case class Spread(expr: Term, tpe: TypeRepr, origin: Origin)                    extends Term
  /** `return e` — imperative early exit (Java bodies). `tpe` is Nothing. */
  final case class Return(expr: Option[Term], tpe: TypeRepr, origin: Origin)            extends Term
  /** `while (cond) body` — imperative loop (Java bodies). `tpe` is Unit. */
  final case class While(cond: Term, body: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `throw e`. `tpe` is Nothing. */
  final case class Throw(expr: Term, tpe: TypeRepr, origin: Origin)                     extends Term
  /** `x instanceof T` — the tested type is a real type usage. */
  final case class InstanceOf(expr: Term, tpt: TypeTree, tpe: TypeRepr, origin: Origin) extends Term
  /** `array(index)` element access. */
  final case class ArrayAccess(array: Term, index: Term, tpe: TypeRepr, origin: Origin) extends Term
  /** `array.length`. */
  final case class ArrayLength(array: Term, tpe: TypeRepr, origin: Origin)              extends Term
  /** `new T[dims]` and/or `new T[]{ init }`; `init` present for a brace initializer. */
  final case class NewArray(elem: TypeTree, dims: List[Term], init: Option[List[Term]], tpe: TypeRepr, origin: Origin) extends Term
  /** `for (binding : iterable) body` (Java enhanced-for). */
  final case class ForEach(binding: ValDef, iterable: Term, body: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `for (init; cond; update) body` (Java classic-for). */
  final case class For(init: List[Statement], cond: Option[Term], update: List[Statement], body: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `try (resources) body catch cases finally fin`. `resources` are try-with-resources bindings,
    * auto-closed by the backend.
    * @param id a token identifying THIS `try`, minted at construction, carried through `copy` —
    *   neither `Origin` nor object identity survives synthesis/rebuild reliably, so checks can
    *   ask "did you guard this one". */
  final case class Try(resources: List[ValDef], body: Term, catches: List[CatchCase],
                       finalizer: Option[Term], tpe: TypeRepr, origin: Origin,
                       id: TryId = TryId.fresh()) extends Term:
    // structural equality EXCLUDING `id`: every existing comparison keeps meaning, and the token is
    // used where it is meant to be used — as an explicit key.
    override def equals(o: Any): Boolean = o match
      case t: Try => t.resources == resources && t.body == body && t.catches == catches &&
                     t.finalizer == finalizer && t.tpe == tpe && t.origin == origin
      case _      => false
    override def hashCode: Int = (resources, body, catches, finalizer, tpe, origin).hashCode
  /** one `catch (param) body`; `param.tpt` may be an `OrType` for multi-catch. */
  final case class CatchCase(param: ValDef, body: Term)
  /** `scrutinee match { cases }` (from a java switch). Carries an identity token for the same
    * reason [[Try]] does — the `switch-null` check asks "did you guard THIS one", and neither
    * `Origin` nor object identity survives synthesis/rebuild reliably.
    */
  final case class Match(scrutinee: Term, cases: List[CaseDef], tpe: TypeRepr, origin: Origin,
                         /** Was this a switch EXPRESSION (JLS 15.28) or STATEMENT (14.11) in java?
                           * One `Tree.Match` renders both, but they bind `yield` differently (JLS
                           * 14.21 targets the innermost switch EXPRESSION, skipping a nested
                           * statement switch). `tpe` cannot stand in — a statement switch's `Unit`
                           * type is coincidental, not this fact. */
                         isExpr: Boolean = false,
                         id: MatchId = MatchId.fresh()) extends Term:
    // structural equality EXCLUDING `id`, exactly as `Try` does — every existing comparison keeps
    // meaning and the token is used only where it is meant to be, as an explicit key. `isExpr` is
    // IN, because two matches that bind a `yield` differently are not the same node.
    override def equals(o: Any): Boolean = o match
      case m: Match => m.scrutinee == scrutinee && m.cases == cases && m.tpe == tpe &&
                       m.origin == origin && m.isExpr == isExpr
      case _        => false
    override def hashCode: Int = (scrutinee, cases, tpe, origin, isExpr).hashCode
  /** one case: `labels` are the constant patterns (empty ⇒ `default`/`case _`). */
  final case class CaseDef(labels: List[Term], guard: Option[Term], body: Term, isDefault: Boolean)
  /** A method value `qualifier :: method` (`Foo::bar`, `x::baz`, `Foo::new`). [[referent]] is
    * what the PARSER read off the executable, kept off the symbol because an external member's
    * `Flags`/`info` are absent or `NoType` wherever a slot cannot be named scope-free — reading
    * those as "not static"/"no arguments" would be fabricated (CLAUDE.md §4.6). */
  final case class MethodRef(qualifier: Either[TypeTree, Term], method: SymId, tpe: TypeRepr,
                             origin: Origin, referent: Referent) extends Term
  /** `break` / `break label` — loop/switch exit. `tpe` is Nothing. */
  final case class Break(label: Option[String], tpe: TypeRepr, origin: Origin)          extends Term
  /** `continue` / `continue label`. `tpe` is Nothing. */
  final case class Continue(label: Option[String], tpe: TypeRepr, origin: Origin)        extends Term

  /** `yield v` from a switch expression's arm, at a NON-tail position (JLS 14.21) — a tail
    * `yield` is peeled into the arm's result term instead. Models an abrupt completion from
    * arbitrary depth, emitted as a value-carrying `scala.util.boundary` around the arm; `tpe` is
    * `Nothing`. Never in a switch STATEMENT's arm — Spoon's `CtYieldStatement` is unwrapped there.
    */
  final case class Yield(value: Term, tpe: TypeRepr, origin: Origin)                     extends Term

  /** `case String s ->` — java's type pattern as a case label (JLS 14.11.1, 14.30.1). A `Term`
    * because [[CaseDef.labels]] is a list of them; BINDS a variable rather than ascribing one
    * that exists. `bind` is interned against the enclosing `CtCase` (java allows two arms to
    * reuse a name). See [[RecordPattern]], [[BindPattern]] for the other pattern nodes. */
  final case class TypePattern(bind: SymId, tpt: TypeTree, tpe: TypeRepr, origin: Origin) extends Term

  /** `case Point(int x, int y) ->` — java's record pattern (JLS 14.30.1). Deconstructs through
    * the record's accessors via `JS-C43`'s generated `unapply`, exact even when overridden
    * (`ENGINE-LIMITS.md` T19, T20). `patterns` are component patterns in order, per JLS 14.30.2.
    * `tpt` is the record's type, used to name the extractor via the companion. */
  final case class RecordPattern(tpt: TypeTree, patterns: List[Term], tpe: TypeRepr, origin: Origin) extends Term

  /** `case Point(x, y)`'s `x` — a component binding with NO type test (JLS 14.30.2's unconditional
    * pattern). Its own node rather than a trivial [[TypePattern]] because the two differ at a
    * `null` component (`case One(s)` binds it, `case One(s: String)` does not match). Valid only
    * inside a [[RecordPattern]].
    */
  final case class BindPattern(bind: SymId, tpe: TypeRepr, origin: Origin) extends Term

  /** `name: stmt` — a java label on a NON-loop statement, the target of `break name`. A wrapper
    * rather than an `Option` field, since the backend emits a real construct around the
    * statement. Loops carry their label in their own `label` field instead, since `continue L`
    * needs a boundary elsewhere (around the body, not the loop). `tpe` is `Unit`. */
  final case class Labeled(name: String, stmt: Term, tpe: TypeRepr, origin: Origin)      extends Term
  /** `assert cond` / `assert cond : msg`. `tpe` is Unit. */
  final case class Assert(cond: Term, msg: Option[Term], tpe: TypeRepr, origin: Origin)  extends Term
  /** `i++` / `++i` / `i--` / `--i` as an expression; `op` is `"+"`/`"-"`, `post` the position. */
  final case class IncDec(target: Term, op: String, post: Boolean, tpe: TypeRepr, origin: Origin) extends Term
  /** `do body while (cond)`. `tpe` is Unit. */
  final case class DoWhile(body: Term, cond: Term, tpe: TypeRepr, origin: Origin, label: Option[String] = None) extends Term
  /** `synchronized (lock) body`. `tpe` is Unit. */
  final case class Synchronized(lock: Term, body: Term, tpe: TypeRepr, origin: Origin)   extends Term

  /** A construct with no faithful Scala image, recorded IN the tree per-site rather than failing
    * the whole unit (`DESIGN.md` §6.2). `Open` is never shipped in deliverable mode; best-effort
    * renders [[inner]] in comment fences, untouched by any phase that does not discharge it.
    * @param inner the approximation @param kind the taxonomy ([[UnportableKind]]) @param diff the
    *   optional catalog row this instances @param what one line in the mint site's own words */
  final case class Unportable(inner: Term, kind: UnportableKind, state: MarkerState,
                              diff: Option[balticporter.catalog.DiffId], what: String,
                              tpe: TypeRepr, origin: Origin) extends Term:

    /** discharged BY A NAMED PHASE, with what it did. The replacement term is the caller's to
      * build; this records that the marker was ANSWERED rather than deleted. */
    def resolved(byPhase: String, how: String): Unportable =
      copy(state = MarkerState.Resolved(byPhase, how))

    /** The identity a conservation check compares two programs on — origin plus the mint site's
      * own words, since object identity and `SymId` don't survive a phase rebuild. Distinguishing
      * only because [[Unportable.open]] refuses a synthetic origin (which would collapse every
      * marker onto one key).
      */
    def markerKey: String = s"${kind.label}@${origin.javaPath}:${origin.line}:${origin.col}|$what"

  object Unportable:

    /** MINT one. Refuses a synthetic origin — §6.2's rule that *a marker must point at real Java*,
      * and the precondition [[Unportable.markerKey]] depends on. A mint site with no position has
      * to keep whatever loud answer it had; that is a worse outcome for one node and a truthful
      * one, where a marker nothing can locate is neither. */
    def open(inner: Term, kind: UnportableKind, diff: Option[balticporter.catalog.DiffId],
             what: String, tpe: TypeRepr, origin: Origin): Unportable =
      require(origin != Origin.synthetic && origin.javaPath.nonEmpty,
        s"Unportable.open: a marker must point at real Java, and this one has no position ($what)")
      Unportable(inner, kind, MarkerState.Open, diff, what, tpe, origin)

    /** the fence a BEST-EFFORT emission wraps an open marker in. Comment-shaped and deterministic:
      * a comment cannot change program shape, which is the whole reason the fence is admissible
      * (§6.4), and determinism is what lets `diff -r` of two run directories mean anything. */
    def fence(m: Unportable): (String, String) =
      (s"/* balticporter:unportable ${m.kind.label}${m.diff.fold("")(d => s" $d")} — ${safe(m.what)} */",
       "/* balticporter:end-unportable */")

    /** a fence may never OPEN or CLOSE a comment: Scala block comments NEST (§4.58), so a block
      * opener in the mint site's own words would swallow the rest of the file. This very scaladoc
      * failed to compile the first time it was written, which is the shortest available argument
      * that the rule is not theoretical. */
    def safe(s: String): String = s.replace("/*", "/ *").replace("*/", "* /")

  /** An as-yet-unmodeled TERM, kept typed so the tree stays whole (types are never opaque, only
    * terms). `raw` is closed Scala text; a call-site substitution may carry NUMBERED HOLES
    * ([[Opaque.hole]]) with `holes` supplying one term per index, so later phases and xref still
    * reach the spliced arguments ([[spliced]]). `holes = Nil` is the closed default. */
  final case class Opaque(raw: String, tpe: TypeRepr, origin: Origin, holes: List[Term] = Nil,
                          /** `Some(name)`: spliced into the COMPANION rather than the class body,
                            * under this name. A spliced member has no symbol, so its home AND its
                            * name — which the inherited-statics export must exclude — ride on the
                            * node (`CLAUDE.md` §1(b)). */
                          companionMember: Option[String] = None)
      extends Term:

    /** `raw` with each hole replaced by `render` of the term it names.
      *
      * A hole index with no term is left as the marker rather than dropped or defaulted: it can
      * only arise from a malformed construction, and a marker that survives into the output is a
      * compile error naming the file, where a silent drop would be a wrong program that compiles. */
    def spliced(render: Term => String): String =
      if holes.isEmpty then raw
      else
        val sb = new StringBuilder
        var i  = 0
        while i < raw.length do
          val c = raw.charAt(i)
          if c != Opaque.Mark then { sb.append(c); i += 1 }
          else
            val close = raw.indexOf(Opaque.Mark.toInt, i + 1)
            val idx   = if close < 0 then scala.None else raw.substring(i + 1, close).toIntOption
            idx.filter(holes.indices.contains) match
              case Some(n) => sb.append(render(holes(n))); i = close + 1
              case scala.None => sb.append(c); i += 1
        sb.toString

  object Opaque:
    /** The hole delimiter: NUL, which cannot occur in Scala source and therefore needs no escape
      * grammar. A printable delimiter would need one, and an escape grammar over ready-made Scala
      * is a second parser for text the engine deliberately does not parse. */
    val Mark: Char = 0.toChar

    /** the marker for hole `i`, as it appears inside [[Opaque.raw]]. */
    def hole(i: Int): String = s"$Mark$i$Mark"

    /** `parts` interleaved with holes `0 … parts.size - 2`. `parts` always has one more element
      * than `holes`; a mismatch is a construction bug and is refused here rather than emitted. */
    def spliced(parts: List[String], holes: List[Term], tpe: TypeRepr, origin: Origin): Opaque =
      require(parts.size == holes.size + 1,
        s"Opaque.spliced: ${parts.size} literal parts for ${holes.size} holes — expected ${holes.size + 1}")
      Opaque(parts.head + parts.tail.zipWithIndex.map((p, i) => hole(i) + p).mkString, tpe, origin, holes)

  /** A statement with the comments written above it — exists purely to carry [[Trivia]] for a
    * STATEMENT. Transparent by design: `tpe`/`origin` delegate, [[StandardTraversal]] rebuilds
    * the wrapper. NOT transparent to a pattern match on statement shape — match through
    * [[Uncommented]] instead, or a comment above e.g. `super(…)` silently drops it (CLAUDE.md §4.4). */
  final case class Commented(leading: List[Trivia], stmt: Term) extends Term:
    def tpe: TypeRepr  = stmt.tpe
    def origin: Origin = stmt.origin

  /** See THROUGH any number of [[Commented]] wrappers — the extractor form, for a pattern match on
    * a statement's shape: `case Tree.Uncommented(Tree.Apply(fun, args, _, _, _)) => …`. Matches
    * every term, wrapped or not, so it is a drop-in around an existing pattern. */
  object Uncommented:
    def unapply(t: Term): Some[Term] = Some(uncomment(t))

  /** the statement itself, with its comment wrappers removed. */
  @annotation.tailrec
  def uncomment(t: Term): Term = t match
    case Commented(_, inner) => uncomment(inner)
    case other               => other

  /** the trivia on `t`, outermost first — `Nil` for an unwrapped statement. */
  def triviaOn(t: Term): List[Trivia] = t match
    case Commented(l, inner) => l ++ triviaOn(inner)
    case _                   => Nil

  /** put `original`'s comments back on a `rewritten` replacement for it. A rewrite that builds a
    * fresh statement (rather than `copy`-ing) drops the wrapper, and the comment goes with it. */
  def recomment(original: Term, rewritten: Term): Term =
    triviaOn(original) match
      case Nil => rewritten
      case ts  => Commented(ts, uncomment(rewritten))

// ---------------------------------------------------------------------------
// Whole-program index + program — our layer BEYOND Quotes.
// ---------------------------------------------------------------------------

/** Interned symbol store. Tagging returns a new table (immutable). */
final class SymbolTable(private val syms: Map[SymId, Symbol]):
  def apply(id: SymId): Symbol       = syms(id)
  def get(id: SymId): Option[Symbol] = syms.get(id)
  def all: Iterable[Symbol]          = syms.values
  def withTag(id: SymId, tag: SymTag): SymbolTable =
    new SymbolTable(syms.updated(id, syms(id).copy(tags = syms(id).tags + tag)))
  def updated(sym: Symbol): SymbolTable = new SymbolTable(syms.updated(sym.id, sym))
object SymbolTable:
  def apply(syms: Iterable[Symbol]): SymbolTable = new SymbolTable(syms.map(s => s.id -> s).toMap)

/** The POSITION a symbol is used in — so a query can ask "used as a mixin / type
  * argument / member type / bound / …", not merely "used". This is what makes the
  * sge/ssg rewrites tractable: java→scala collections retype every `MemberType` and
  * `TypeArg` site; Int→opaque retypes `TypeArg`/`MemberType` flows; a mixin swap edits
  * `Mixin`/`Extends`. Type positions are first-class, not just call sites. */
enum UsageKind:
  case Call        // invoked as a method (`Apply.method`)
  case TermRef     // referenced as a term/value/object (`Ident`/`Select`)
  case Instantiate // `new T(...)` — the constructed type
  case Extends     // primary supertype in a class's parent list
  case Mixin       // an additional mixin parent, or a member of an `A & B` intersection
  case SelfType    // a class self-type `self: S =>`
  case TypeArg     // a type argument in `F[Arg]` — i.e. a type-PARAMETER position
  case Tycon       // the constructor being applied in `F[...]`
  case Bound       // inside a `TypeBounds` — a type-param bound (incl. F-bound) or wildcard
  case MemberType  // the declared type of a field / val / param / def-result
  case TypeRefPos  // any other type-reference position (prefix, alias rhs, ascription)

/** One recorded use of a symbol: WHERE (`kind`), at which tree node (`site`), and inside
  * which enclosing definition (`enclosing` — the nearest containing method/field/class
  * symbol). `enclosing` is what makes `callersOf` a real call-graph edge. */
final case class Usage(kind: UsageKind, site: Tree, enclosing: SymId = SymId.None)

/** Whole-program cross-reference index: symbol → its definition, and symbol → every usage site,
  * kinded by position (term refs and every type position). Built by [[Xref.build]] over the tree
  * and re-derived after each phase rewrite — the pipeline rebuilds it between phases.
  */
final class XrefIndex(
    private val defs: Map[SymId, Definition],
    private val usagesBySym: Map[SymId, List[Usage]],
):
  def definitionOf(s: SymId): Option[Definition] = defs.get(s)
  /** all usage sites of `s`, any position. */
  def usagesOf(s: SymId): List[Tree] = usagesBySym.getOrElse(s, Nil).map(_.site)
  /** kinded usages of `s` — ask "where, and how". */
  def usages(s: SymId): List[Usage] = usagesBySym.getOrElse(s, Nil)
  /** usage sites of `s` restricted to one position kind. */
  def usagesOf(s: SymId, kind: UsageKind): List[Tree] =
    usagesBySym.getOrElse(s, Nil).collect { case Usage(`kind`, site, _) => site }
  /** every symbol that has at least one recorded usage. */
  def referenced: Set[SymId] = usagesBySym.keySet

/** The transform substrate: all units, the symbol table, the xref index. Project-
  * owned transformers receive this, query it (Quotes-familiar), and rewrite. */
final class Program(
    val units: List[Tree.ClassDef],
    val symbols: SymbolTable,
    val xref: XrefIndex,
    /** What the frontend saw, dropped members included — see [[MemberIndex]]. A required
      * parameter, not defaulted: `Pipeline.runTraced` rebuilds a fresh `Program` after every
      * phase, so a default would silently drop this at every boundary. [[rebuilt]] is the way a
      * phase returns a program.
      */
    val members: MemberIndex,
    /** Classpath types interned by the frontend for ancestry resolution (K18). NOT emitted —
      * excluded from [[units]]. Carried through [[rebuilt]] and included in every xref rebuild
      * so `definitionOf` and `OverrideGraph` see them. Empty default is the no-op. */
    val internedDefs: List[Tree.ClassDef] = Nil,
    /** JLS 9.4.3 `default` methods of EXTERNAL interface parents, read off the class file by the
      * frontend: parent FQN -> `(name, param counts per clause)`. The emitter's diamond forwarder
      * ASKS this instead of guessing which external parents are concrete (`ENGINE-LIMITS.md` K39).
      * Empty default is the no-op. */
    val internedDefaults: Map[String, Set[(String, List[Int])]] = Map.empty,
):
  export xref.{definitionOf, usagesOf, usages, referenced}

  def symbolOf(id: SymId): Option[Symbol] = symbols.get(id)

  /** Rebuild after a rewrite, CARRYING EVERYTHING THE CALLER DID NOT CHANGE.
    *
    * `new Program` is for the frontend and the pipeline; a phase uses this. The difference is not
    * brevity: a phase that constructs a `Program` positionally has to remember every whole-program
    * value that exists today, and the one it forgets is dropped in silence. */
  def rebuilt(
      units: List[Tree.ClassDef] = this.units,
      symbols: SymbolTable = this.symbols,
      xref: XrefIndex = this.xref,
      members: MemberIndex = this.members,
  ): Program = new Program(units, symbols, xref, members, internedDefs, internedDefaults)

  /** Symbols this program declares, vs. externals interned lazily on first reference — CLAUDE.md
    * §4.56's "decide ownership structurally, never by name". Owned iff climbing the `owner` chain
    * reaches a [[units]] symbol. Used before any prefix rename and by any `RuleScope`-taking rule.
    * Fuel-bounded: a corrupt owner cycle counts as not-owned rather than hanging.
    */
  lazy val owned: Set[SymId] =
    val roots = units.map(_.symbol).toSet
    def rooted(s: SymId, fuel: Int): Boolean =
      s != SymId.None && fuel > 0 && (roots(s) || symbols.get(s).exists(sym => rooted(sym.owner, fuel - 1)))
    symbols.all.collect { case s if rooted(s.id, 64) => s.id }.toSet

  /** does THIS program declare `id`? See [[owned]]. */
  def owns(id: SymId): Boolean = owned(id)

  /** the symbol a tree defines or references, if any (`reflect`-style `.symbol`). */
  def symbolIn(t: Tree): Option[SymId] = t match
    case d: Definition                   => Some(d.symbol)
    case Tree.Ident(s, _, _)             => Some(s)
    case Tree.Select(_, s, _, _)         => Some(s)
    case Tree.Apply(_, _, m, _, _)       => Some(m)
    case _                               => scala.None

  /** methods that call `method` (a call-graph edge) — walked by globals→implicits. Uses
    * each call usage's recorded `enclosing` definition, climbing to the nearest method. */
  def callersOf(method: SymId): List[SymId] =
    xref
      .usages(method)
      .collect { case Usage(UsageKind.Call, _, enc) if enc != SymId.None => enc }
      .flatMap(enclosingMethod)
      .distinct

  private def enclosingMethod(from: SymId): Option[SymId] =
    ownerChain(from).find(id => symbols.get(id).exists(s => s.info.isInstanceOf[TypeRepr.MethodType | TypeRepr.PolyType]))

  private def ownerChain(s: SymId): LazyList[SymId] =
    LazyList.unfold[SymId, SymId](s) { id =>
      if id == SymId.None then scala.None
      else Some((id, symbols.get(id).map(_.owner).getOrElse(SymId.None)))
    }
