package balticporter.tir

/** Typed IR (TIR) — the re-compiler's working representation. See RECOMPILER.md.
  *
  * A Scala-shaped, whole-program, TYPED tree with a real SYMBOL model. Unlike the
  * BIR (a per-unit, string-oriented projection built to feed a pretty-printer), TIR:
  *   - carries a fully STRUCTURED `TType` on every node (no re-inference at emission,
  *     and never a flat string collapsing applied params / mixins / self-types),
  *   - resolves every reference to a stable `SymId` (enabling whole-program usage
  *     lookup and bump-resilient, symbol-keyed transforms — not textual diffs),
  *   - records `Origin` provenance back to the Java source,
  *   - is the substrate project-owned transformers run on BEFORE emission.
  */

/** Where a node came from. `javaPath`+position is the recompiler input; a Scala
  * position is attached later by emission. */
final case class Origin(javaPath: String, line: Int, col: Int)
object Origin:
  val synthetic: Origin = Origin("<synthetic>", 0, 0)

/** Stable symbol identity — an interned integer, NEVER a string. Usage lookup and
  * symbol-keyed patches depend on identity surviving upstream renames/reflows. */
opaque type SymId = Int
object SymId:
  def apply(i: Int): SymId          = i
  extension (s: SymId) def raw: Int = s

enum SymKind:
  case Package, Class, Trait, Object, Enum, Method, Ctor, Field, Param, Local, TypeParam

enum Vis:
  case Public, Protected, PackagePrivate, Private

enum Variance:
  case Invariant, Covariant, Contravariant

final case class Flags(
    vis: Vis = Vis.Public,
    isAbstract: Boolean = false,
    isFinal: Boolean = false,
    isStatic: Boolean = false,
    isOverride: Boolean = false,
    varargs: Boolean = false,
)

/** Open, extensible domain semantics attached to symbols by transforms — e.g. a
  * transform tags an `Int` field as `GLLayer` so downstream retyping finds every
  * flow of it. Information a Scala-2.13 semantic AST cannot carry. */
trait SymTag

// ---------------------------------------------------------------------------
// Types — a STRUCTURED algebra, faithful to Scala's type system. Nothing here
// collapses into a string; every constituent stays addressable so transforms can
// rewrite applied args, split/compose mixins, read self-types and F-bounds, etc.
// ---------------------------------------------------------------------------

enum PrimKind:
  case Boolean, Byte, Short, Char, Int, Long, Float, Double, Unit

/** A type parameter with its variance, bounds, and (for type constructors) its own
  * higher-kinded parameters. F-bounds live here: `T <: IRichSequence[T]` is a
  * `TypeParam` whose `bounds` hi references `T`'s own symbol. */
final case class TypeParam(sym: SymId, variance: Variance, bounds: TType.Bounds, hkParams: List[TypeParam] = Nil)

/** A method/constructor type: poly type params, value params, result. */
final case class MethodSig(typeParams: List[TypeParam], params: List[TType], result: TType)

enum TType:
  case Prim(kind: PrimKind)
  /** a named type; `prefix` present only for path-dependent forms (`p.T`, `Outer#Inner`). */
  case Named(sym: SymId, prefix: Option[TType] = None)
  /** applied type constructor: `tycon[args]`. args may be `Bounds` (wildcards). */
  case Applied(tycon: TType, args: List[TType])
  /** reference to a type-parameter symbol. */
  case TVar(sym: SymId)
  /** intersection / mixin composition: `A with B with C` (Scala 3 `A & B & C`). */
  case And(members: List[TType])
  /** union: `A | B`. */
  case Or(members: List[TType])
  /** type bounds — also the wildcard type `?`; `None` means Nothing / Any. */
  case Bounds(lo: Option[TType], hi: Option[TType])
  case Arr(elem: TType)
  /** `C.this` — the self reference; distinct from a plain `Named`. */
  case This(cls: SymId)
  /** `x.type` — a term's singleton type (path-dependent tracking). */
  case Singleton(term: SymId)
  /** a method's type. */
  case Method(sig: MethodSig)
  /** higher-kinded type lambda `[X] =>> body`. */
  case HKLambda(params: List[TypeParam], body: TType)
  /** by-name `=> T`. */
  case ByName(underlying: TType)
  case NoType

object TType:
  val AnyBounds: Bounds = Bounds(None, None)

// ---------------------------------------------------------------------------
// Symbols
// ---------------------------------------------------------------------------

/** A declaration's symbol. `tpe` is the value type (terms) / declared type (types) /
  * a `Method` type (methods). Owned/interned by the `SymbolTable`. */
final case class Symbol(
    id: SymId,
    name: String,
    kind: SymKind,
    owner: Option[SymId],
    tpe: TType,
    flags: Flags,
    origin: Origin,
    tags: Set[SymTag] = Set.empty,
)

// ---------------------------------------------------------------------------
// Trees — typed Scala tree (the `tpd.Tree` analog). Every node has `tpe` + `origin`;
// declarations carry a `symbol` and full structure (type params WITH bounds, the
// mixin parent list, self-types); term references resolve to the symbol they use.
// Starter subset — extended as population/emission need more node kinds.
// ---------------------------------------------------------------------------

sealed trait Tree:
  def tpe: TType
  def origin: Origin

sealed trait TermTree extends Tree
sealed trait DefTree extends Tree:
  def symbol: SymId

object Tree:
  /** class / trait / object / enum. `parents` is the linearized mixin composition
    * (each may be an `Applied` type); `selfType` carries `self: S =>` and F-bounded
    * self annotations; `typeParams` carry variance + bounds. */
  final case class TypeDef(
      symbol: SymId,
      typeParams: List[TypeParam],
      parents: List[TType],
      selfType: Option[TType],
      members: List[Tree],
      tpe: TType,
      origin: Origin,
  ) extends DefTree

  final case class DefDef(
      symbol: SymId,
      typeParams: List[TypeParam],
      params: List[ValDef],
      resultTpe: TType,
      body: Option[TermTree],
      tpe: TType,
      origin: Origin,
  ) extends DefTree

  final case class ValDef(symbol: SymId, rhs: Option[TermTree], tpe: TType, origin: Origin) extends DefTree

  // ---- terms (each reference resolves to a SymId) ----
  final case class Ident(sym: SymId, tpe: TType, origin: Origin) extends TermTree
  final case class Select(qual: TermTree, sym: SymId, tpe: TType, origin: Origin) extends TermTree
  final case class Apply(fun: TermTree, targs: List[TType], args: List[TermTree], method: SymId, tpe: TType, origin: Origin)
      extends TermTree
  final case class New(cls: TType, args: List[TermTree], ctor: SymId, tpe: TType, origin: Origin) extends TermTree
  final case class Lambda(params: List[ValDef], body: TermTree, tpe: TType, origin: Origin) extends TermTree
  final case class Block(stats: List[Tree], expr: TermTree, tpe: TType, origin: Origin) extends TermTree
  final case class Lit(raw: String, tpe: TType, origin: Origin) extends TermTree
  /** an as-yet-unmodeled TERM, kept typed (a full structured `tpe`) so the tree stays
    * whole while the node set grows incrementally. Types are never opaque. */
  final case class Opaque(raw: String, tpe: TType, origin: Origin) extends TermTree

// ---------------------------------------------------------------------------
// Whole-program index + program
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

/** Whole-program cross-reference index — the substrate for scalafix-style queries:
  * symbol → its definition, symbol → every usage site (term refs AND type positions,
  * since types reference type-symbols). */
final class XrefIndex(
    private val defs: Map[SymId, DefTree],
    private val usages: Map[SymId, List[Tree]],
):
  def definitionOf(s: SymId): Option[DefTree] = defs.get(s)
  def usagesOf(s: SymId): List[Tree]          = usages.getOrElse(s, Nil)

/** The transform substrate: all units, the symbol table, the xref index. Passes
  * (the project-owned transformers) receive this, query it, and rewrite. */
final class Program(val units: List[Tree.TypeDef], val symbols: SymbolTable, val xref: XrefIndex):
  export xref.{definitionOf, usagesOf}
  def typeOf(t: Tree): TType = t.tpe

  def symbolOf(t: Tree): Option[SymId] = t match
    case d: DefTree                   => Some(d.symbol)
    case Tree.Ident(s, _, _)          => Some(s)
    case Tree.Select(_, s, _, _)      => Some(s)
    case Tree.Apply(_, _, _, m, _, _) => Some(m)
    case Tree.New(_, _, c, _, _)      => Some(c)
    case _                            => None

  /** methods that call `method` (a call-graph edge) — walked by globals→implicits. */
  def callersOf(method: SymId): List[SymId] =
    xref.usagesOf(method).flatMap(enclosingMethod)

  private def enclosingMethod(usage: Tree): Option[SymId] =
    symbolOf(usage)
      .flatMap(ownerChain)
      .find(id => symbols.get(id).exists(s => s.kind == SymKind.Method || s.kind == SymKind.Ctor))

  private def ownerChain(s: SymId): LazyList[SymId] =
    LazyList.unfold(Option(s)) {
      case Some(id) => Some((id, symbols.get(id).flatMap(_.owner)))
      case None     => None
    }
