package balticporter.tir

/** Typed IR (TIR) — the re-compiler's working representation. See RECOMPILER.md.
  *
  * A Scala-shaped, whole-program, TYPED tree with a real SYMBOL model. Unlike the
  * BIR (a per-unit, string-oriented projection built to feed a pretty-printer), TIR:
  *   - carries the resolved `TType` on every node (no re-inference at emission),
  *   - resolves every reference to a stable `SymId` (enabling whole-program usage
  *     lookup and bump-resilient, symbol-keyed transforms — not textual diffs),
  *   - records `Origin` provenance back to the Java source,
  *   - is the substrate project-owned transformers run on BEFORE emission.
  *
  * This file is the core model (build-order step 1). Population from Spoon (step 2)
  * and the emission backend (step 3) build on it.
  */

/** Where a node came from. `javaPath`+position is the recompiler input; a Scala
  * position is attached later by emission. Underpins "access the original source"
  * and bump-resilience. */
final case class Origin(javaPath: String, line: Int, col: Int)
object Origin:
  val synthetic: Origin = Origin("<synthetic>", 0, 0)

/** Stable symbol identity — an interned integer, NEVER a string. Usage lookup and
  * symbol-keyed patches depend on identity surviving upstream renames/reflows. */
opaque type SymId = Int
object SymId:
  def apply(i: Int): SymId       = i
  extension (s: SymId) def raw: Int = s

enum SymKind:
  case Package, Class, Trait, Object, Enum, Method, Ctor, Field, Param, Local, TypeParam

enum Vis:
  case Public, Protected, PackagePrivate, Private

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
  * flow of it. This is information a Scala-2.13 semantic AST cannot carry. */
trait SymTag

/** A declaration's symbol. `tpe` is the value type (terms) or declared type (types);
  * `signature` is set for methods/ctors. Owned/interned by the `SymbolTable`. */
final case class Symbol(
    id: SymId,
    name: String,
    kind: SymKind,
    owner: Option[SymId],
    tpe: TType,
    signature: Option[Signature],
    flags: Flags,
    origin: Origin,
    tags: Set[SymTag] = Set.empty,
)

final case class Signature(typeParams: List[SymId], params: List[SymId], ret: TType)

/** Scala type model — NOT Java's. Every tree node carries one, resolved from Spoon
  * (including F-bound instantiations and generic-method returns), so nothing is
  * re-inferred at emission. Type references point to a type SYMBOL, so "find all
  * usages of java.util.List" includes type positions, not just calls. */
enum TType:
  case Prim(name: String)                              // int, boolean, char, ...
  case Ref(sym: SymId, args: List[TType])              // C[args]
  case TVar(sym: SymId)                                // a type-parameter reference
  case Wild(lo: Option[TType], hi: Option[TType])      // ? / ? <: U / ? >: L
  case Arr(elem: TType)
  case Fun(params: List[TType], ret: TType)            // SAM / lambda target types
  case NoType                                          // statements, void

/** Typed Scala tree — the `tpd.Tree` analog. Every node has `tpe` + `origin`;
  * declarations carry a `symbol`; term references resolve to the symbol they use.
  * Starter subset — extended as population/emission need more node kinds. */
sealed trait Tree:
  def tpe: TType
  def origin: Origin

sealed trait TermTree extends Tree
sealed trait DefTree extends Tree:
  def symbol: SymId

object Tree:
  // ---- declarations (each introduces a symbol) ----
  final case class TypeDef(
      symbol: SymId,
      tparams: List[TypeDef],
      parents: List[TType],
      members: List[Tree],
      tpe: TType,
      origin: Origin,
  ) extends DefTree

  final case class DefDef(
      symbol: SymId,
      tparams: List[TypeDef],
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
  final case class Apply(fun: TermTree, args: List[TermTree], method: SymId, tpe: TType, origin: Origin) extends TermTree
  final case class New(cls: TType, args: List[TermTree], ctor: SymId, tpe: TType, origin: Origin) extends TermTree
  final case class Lambda(params: List[ValDef], body: TermTree, tpe: TType, origin: Origin) extends TermTree
  final case class Block(stats: List[Tree], expr: TermTree, tpe: TType, origin: Origin) extends TermTree
  final case class Lit(raw: String, tpe: TType, origin: Origin) extends TermTree
  /** an as-yet-unmodeled construct, kept typed so the tree stays whole while the
    * node set is grown incrementally. */
  final case class Opaque(raw: String, tpe: TType, origin: Origin) extends TermTree

/** Interned symbol store. Tagging returns a new table (immutable), so a transform's
  * domain annotations are threaded explicitly through the program it produces. */
final class SymbolTable(private val syms: Map[SymId, Symbol]):
  def apply(id: SymId): Symbol         = syms(id)
  def get(id: SymId): Option[Symbol]   = syms.get(id)
  def all: Iterable[Symbol]            = syms.values
  def withTag(id: SymId, tag: SymTag): SymbolTable =
    new SymbolTable(syms.updated(id, syms(id).copy(tags = syms(id).tags + tag)))
  def updated(sym: Symbol): SymbolTable = new SymbolTable(syms.updated(sym.id, sym))
object SymbolTable:
  def apply(syms: Iterable[Symbol]): SymbolTable = new SymbolTable(syms.map(s => s.id -> s).toMap)

/** Whole-program cross-reference index — the substrate for scalafix-style queries,
  * built once over all units: symbol → its definition, symbol → every usage site. */
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

  /** the symbol a tree defines or references, if any. */
  def symbolOf(t: Tree): Option[SymId] = t match
    case d: DefTree                => Some(d.symbol)
    case Tree.Ident(s, _, _)       => Some(s)
    case Tree.Select(_, s, _, _)   => Some(s)
    case Tree.Apply(_, _, m, _, _) => Some(m)
    case Tree.New(_, _, c, _, _)   => Some(c)
    case _                         => None

  /** methods that call `method` (a call-graph edge) — walked by globals→implicits. */
  def callersOf(method: SymId): List[SymId] =
    xref.usagesOf(method).flatMap(enclosingMethod)

  /** the nearest enclosing method/ctor symbol of a usage site, via symbol owners. */
  private def enclosingMethod(usage: Tree): Option[SymId] =
    symbolOf(usage).flatMap(ownerChain).find(id => symbols.get(id).exists(s => s.kind == SymKind.Method || s.kind == SymKind.Ctor))

  private def ownerChain(s: SymId): LazyList[SymId] =
    LazyList.unfold(Option(s)) {
      case Some(id) => Some((id, symbols.get(id).flatMap(_.owner)))
      case None     => None
    }
