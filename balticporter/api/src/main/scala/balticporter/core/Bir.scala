package balticporter.core

// =============================================================================
// FROZEN — the BIR path. New work goes on the TIR.
//
// Two substrates exist. This is the older one: a resolved-but-untyped Java IR
// (`BUnit`/`BType`/`BExpr`) with its own frontend (`SpoonFrontend`) and its own
// printer (`ScalaPrinter`), driven by `runner/M0Pipeline`. `DESIGN.md` §2.2 declares
// it the wrong substrate: it has no symbol table, no cross-reference index and no
// phase model, so a rewrite cannot ask "who else uses this" and every rule is a
// local pattern match.
//
// The TIR (`api/.../tir/Tir.scala`, `frontend-spoon/SpoonTir`, `engine/.../TirEmitter`)
// carries every CLAUDE.md §3/§4.4 lesson, all of the checks, all of the production
// transforms and the MUnit conversion. Since `runner/PortRun` it also carries the
// operational machinery this path was the only home for: the action cache
// (`TirCacheKey`, in this file's neighbour `Cache.scala`), determinism by
// double-translation, `SbtGen` wiring and provenance.
//
// It is NOT deleted, and must not be. SEVEN corpus programs still translate through
// it — LiqpCorpus, LiqpM0, XwikiProject, XwikiSurvey, FlexmarkCorpus,
// BumpDemo, VocabDemo — covering liqp and
// xwiki/flexmark, which are ssg's Java libraries.
// (It was nine: jbump's scout, `JbumpCorpus`, was retired when jbump became a real
// TIR port, and `LiqpProject` — the BIR whole-corpus ASSEMBLY for liqp, with its own
// sbt emission, its own JUnit4 test translation and one hand-written override —
// went the same way once `balticporter/corpus/ports/liqp/{main,test}.conf` and `just liqp-measure`
// did all of it through the TIR and MEASURED it. That is the shape of this list's
// retirement — a library leaves it by being ported properly, not by having its scout
// deleted. liqp's two remaining scouts survey; they do not assemble.)
// (SpoonTirEmitProject is NOT a dependent: it translates through the TIR and touches
// this path only for `M0Pipeline.compileGate` — do not put it on a migration work list.)
// Moving them to
// the TIR is a separate, measured piece of work (PROGRESS.md §Publishability 1.1); until
// it is done, deleting this deletes their ports.
//
// So: fix what those callers need, add nothing. A new rule, check or emission
// feature belongs on the TIR, where the whole engine can see it.
// =============================================================================

/** Comment kinds preserved from source. Text is stored verbatim including delimiters. */
enum TriviaKind:
  case Line, Block, Javadoc

final case class Trivia(kind: TriviaKind, text: String)

/** Java types, post-resolution: every Ref carries a fully-qualified name. */
sealed trait BType
object BType:
  final case class Prim(name: String) extends BType // void int long double float boolean char byte short
  final case class Ref(qname: String, args: List[BType]) extends BType
  final case class Arr(elem: BType) extends BType
  final case class TVar(name: String) extends BType
  final case class Wild(upper: Option[BType], lower: Option[BType]) extends BType

  val Void: BType = Prim("void")
  val ObjectQ = "java.lang.Object"
  def isObject(t: BType): Boolean = t match
    case Ref(ObjectQ, _) => true
    case _               => false

enum Vis:
  case Public, Protected, PackagePrivate, Private

/** A preserved annotation (e.g. JUnit's @Test(expected = X.class)). */
final case class BAnnotation(qname: String, args: List[(String, BExpr)])

final case class Mods(
    vis: Vis = Vis.Public,
    isAbstract: Boolean = false,
    isFinal: Boolean = false,
    isStatic: Boolean = false,
    isOverride: Boolean = false,
    annotations: List[BAnnotation] = Nil,
)

enum BTypeKind:
  case Class, Interface, Enum

/** One Java enum constant → `case Name extends E(args)`. Constant-specific class
  * bodies are not yet mechanized. */
final case class BEnumCase(leading: List[Trivia], name: String, args: List[BExpr])

/** How an identifier reference resolves. */
enum RefKind:
  case Local
  /** varargs: the parameter was declared `T...` in Java. */
  case Param(varargs: Boolean)
  /** field on `this` (or an enclosing instance). */
  case OwnField
  /** field on an enclosing instance from inside an inner class: `Outer.this.f`. */
  case OuterField(outer: String)
  /** field on an enclosing instance from inside an ANONYMOUS/local class, which has
    * no name for `Outer.this` — emitted bare, resolved by Scala's lexical scoping. */
  case EnclosingField
  /** static field: qname of the owner. */
  case StaticField(owner: String)

sealed trait BExpr
object BExpr:
  enum LitKind:
    case IntL, LongL, DoubleL, FloatL, BoolL, CharL, StringL, NullL
  /** raw is the literal exactly as it should print in Scala (delimiters included for strings/chars). */
  final case class Lit(kind: LitKind, raw: String) extends BExpr
  final case class Ident(name: String, kind: RefKind) extends BExpr
  case object This extends BExpr
  /** Field selection on an explicit receiver. */
  final case class Select(recv: BExpr, name: String) extends BExpr
  /** `arr.length` on an array. */
  final case class ArrayLength(arr: BExpr) extends BExpr
  final case class ArrayAccess(arr: BExpr, idx: BExpr) extends BExpr

  enum Recv:
    case OnThis
    case OnSuper
    case Static(owner: String)
    case On(expr: BExpr)
  /** One formal parameter of the *resolved* target (so call-site adaptations can see array/varargs slots). */
  final case class Formal(tpe: BType, varargs: Boolean)
  final case class Call(
      recv: Recv,
      name: String,
      args: List[BExpr],
      formals: Option[List[Formal]],
      /** fqcn of the resolved declaring type (drives JDK-vs-translated call-site adaptations). */
      ownerQ: Option[String],
  ) extends BExpr
  /** anonBody: Java anonymous subclass — `Some(empty)` for the super-type-token
    * pattern `new X(...) {}`, `Some(members)` for bodies with fields/methods.
    * formals: resolved ctor signature (drives call-site adaptations). */
  final case class New(
      tpe: BType.Ref,
      args: List[BExpr],
      anonBody: Option[BAnonBody] = None,
      formals: Option[List[Formal]] = None,
  ) extends BExpr
  /** init: instance-initializer blocks (the double-brace idiom) — plain statements
    * in the Scala anonymous body. */
  final case class BAnonBody(fields: List[BField], methods: List[BMethod], init: List[BStmt] = Nil)
  /** dims for `new T[n]`; init for `new T[]{...}` / `{...}` initializers. */
  final case class NewArray(elem: BType, dims: List[BExpr], init: Option[List[BExpr]]) extends BExpr

  /** stringConcat: resolved statically by the frontend (any operand of `+` is a String). */
  final case class Binary(op: String, l: BExpr, r: BExpr, stringConcat: Boolean = false) extends BExpr
  final case class Unary(op: String, e: BExpr, prefix: Boolean = true) extends BExpr
  final case class Ternary(c: BExpr, t: BExpr, e: BExpr) extends BExpr
  final case class Cast(tpe: BType, e: BExpr) extends BExpr
  final case class InstanceOf(e: BExpr, tpe: BType) extends BExpr
  /** `Foo.class` */
  final case class ClassLit(tpe: BType) extends BExpr

  /** Static type of an expression as the frontend resolved it; attached where rules need it. */
  final case class Typed(e: BExpr, tpe: BType) extends BExpr

  /** Java lambda → Scala lambda. Left = block body (statements), Right = expression
    * body. paramTypes carries the resolved SAM parameter types when available, so the
    * printer can annotate them where Scala can't infer against an overloaded target. */
  final case class Lambda(params: List[String], body: Either[List[BStmt], BExpr], paramTypes: List[BType] = Nil)
      extends BExpr

  /** Java method reference → eta-expandable Scala selection: `Owner.m` (static) or
    * `recv.m` (bound instance). SAM conversion supplies the target type. */
  final case class MethodRef(prefix: Either[String, BExpr], name: String) extends BExpr

  /** Java assignment in expression position → `{ lhs = rhs; lhs }`. */
  final case class AssignExpr(lhs: BExpr, rhs: BExpr) extends BExpr

  /** Java `x++`/`x--` (post=true) or `++x`/`--x` (post=false) in expression position. */
  final case class IncDecExpr(target: BExpr, op: String, post: Boolean) extends BExpr

  /** Constructor reference `X::new` → `((p0, ...) => new X(p0, ...))`. */
  final case class CtorRef(tpe: BType.Ref, formals: List[BType]) extends BExpr

  /** Unbound instance method reference `Type::m` → explicit lambda
    * `(r: Type, p1: A1, ...) => r.m(p1, ...)` — receiver + formal types from the
    * resolved executable. */
  final case class UnboundMethodRef(recvType: BType, name: String, formals: List[BType]) extends BExpr

sealed trait BStmtK
object BStmtK:
  final case class LocalVar(name: String, tpe: BType, init: Option[BExpr], effectivelyFinal: Boolean) extends BStmtK
  final case class ExprStmt(e: BExpr) extends BStmtK
  /** op: None for plain `=`, Some("+") for `+=` etc. */
  final case class Assign(lhs: BExpr, rhs: BExpr, op: Option[String]) extends BStmtK
  final case class If(cond: BExpr, thenB: List[BStmt], elseB: Option[List[BStmt]]) extends BStmtK
  final case class Return(e: Option[BExpr]) extends BStmtK
  final case class Throw(e: BExpr) extends BStmtK
  final case class While(cond: BExpr, body: List[BStmt]) extends BStmtK
  /** Java do/while → `while ({ body; cond }) ()` (exact semantics). */
  final case class DoWhile(body: List[BStmt], cond: BExpr) extends BStmtK
  /** Java `assert cond : msg` → Scala assert (NOTE: Java asserts are off by default
    * at runtime; Scala's are on — a documented, deliberate divergence). */
  final case class Assert(cond: BExpr, msg: Option[BExpr]) extends BStmtK
  final case class Block(body: List[BStmt]) extends BStmtK
  final case class Try(body: List[BStmt], catches: List[BCatch], fin: Option[List[BStmt]]) extends BStmtK
  /** `scala.util.boundary { ... }` — target for translated break/continue.
    * label: names the Label context param, letting an inner boundary's break
    * target this one explicitly (the mixed break+continue encoding). */
  final case class Boundary(body: List[BStmt], label: Option[String] = None) extends BStmtK
  /** `scala.util.boundary.break()` — exits the nearest enclosing Boundary,
    * or the named one when `label` is set. */
  final case class LoopBreak(label: Option[String] = None) extends BStmtK
  /** From a fallthrough-free Java switch statement. isDefault cases have empty exprs. */
  final case class Match(scrutinee: BExpr, cases: List[BCase]) extends BStmtK
  /** Java synchronized(lock){...} → lock.synchronized{...} (same monitors). */
  final case class Synchronized(lock: BExpr, body: List[BStmt]) extends BStmtK
  /** Java local class declared inside a method body. */
  final case class LocalType(t: BTypeDecl) extends BStmtK
  case object Empty extends BStmtK

final case class BCatch(param: String, types: List[BType], body: List[BStmt])
final case class BCase(exprs: List[BExpr], isDefault: Boolean, body: List[BStmt])

final case class BStmt(leading: List[Trivia], k: BStmtK)

final case class BTypeParam(name: String, upper: Option[BType])
final case class BParam(name: String, tpe: BType, varargs: Boolean)

final case class BField(
    leading: List[Trivia],
    mods: Mods,
    tpe: BType,
    name: String,
    init: Option[BExpr],
)

/** Constructor: the frontend splits off the explicit super()/this() invocation from the body. */
final case class BCtor(
    leading: List[Trivia],
    mods: Mods,
    params: List[BParam],
    superArgs: Option[List[BExpr]],
    thisArgs: Option[List[BExpr]],
    body: List[BStmt],
    /** the resolved param types of the super()/this() target ctor — disambiguates
      * overloads of the same arity (ContentNode has three arity-1 ctors); None when
      * there is no explicit super/this call. */
    callTargetTypes: Option[List[BType]] = None,
)

final case class BMethod(
    leading: List[Trivia],
    mods: Mods,
    tparams: List[BTypeParam],
    name: String,
    params: List[BParam],
    ret: BType,
    body: Option[List[BStmt]],
    /** parameter names that are reassigned inside the body (Java params are mutable, Scala's are not). */
    assignedParams: Set[String],
)

final case class BTypeDecl(
    leading: List[Trivia],
    mods: Mods,
    kind: BTypeKind,
    name: String,
    tparams: List[BTypeParam],
    superClass: Option[BType.Ref],
    interfaces: List[BType],
    fields: List[BField],
    ctors: List[BCtor],
    methods: List[BMethod],
    /** static members — emitted into the companion object. */
    staticFields: List[BField],
    staticMethods: List[BMethod],
    /** enum constants (kind == Enum only). */
    enumCases: List[BEnumCase] = Nil,
    /** `static { ... }` blocks → companion object body (source order). */
    staticInit: List[BStmt] = Nil,
    /** instance initializer blocks → class body before ctor statements. */
    instanceInit: List[BStmt] = Nil,
    nested: List[BTypeDecl],
    /** extracted `private static final long serialVersionUID = N` if present. */
    serialVersionUID: Option[Long],
    /** non-static nested classes — emitted in the class body (Scala nested classes
      * are inner by default, capturing the outer instance). */
    inner: List[BTypeDecl] = Nil,
)

/** One translation unit = one Java compilation unit. */
final case class BUnit(
    /** path relative to the upstream source root, e.g. "liqp/filters/Abs.java". */
    sourcePath: String,
    pkg: String,
    types: List[BTypeDecl],
    /** every comment found in the source file, for the preservation invariant. */
    allComments: List[Trivia],
)
