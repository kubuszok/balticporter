package balticporter.transform

import balticporter.tir.*

/** `java.util` collections → `scala.collection.mutable`. A whole-program [[Phase]] — the
  * prototype of the sge/ssg "java collections → scala" production transform.
  *
  *   - `transformType` retypes every collection OCCURRENCE (field, param, return, type
  *     argument, `new`, local) — driven by the symbol table, so it hits every position the
  *     xref knows about, not just the ones a printer happened to annotate.
  *   - `transformApply` rewrites the common call shapes: `size()`/`isEmpty()` drop their
  *     parens, `get(i)` becomes `apply(i)`, `add(x)` becomes `+= x`, `put(k,v)` becomes
  *     `update(k,v)` — guarded by the receiver's (already-retyped) collection type.
  *
  * New scala symbols are interned into the table in `run`, so the emitter (which reads names
  * from the table) prints `scala.collection.mutable.Buffer[X]` and `xs += x` by construction.
  */
final class CollectionsTransform extends Phase:
  def name = "java-collections->scala"

  /** java fully-qualified name → scala fully-qualified name. */
  private val typeMap: Map[String, String] = Map(
    "java.util.List"       -> "scala.collection.mutable.Buffer",
    "java.util.ArrayList"  -> "scala.collection.mutable.ArrayBuffer",
    "java.util.LinkedList" -> "scala.collection.mutable.ListBuffer",
    "java.util.Map"        -> "scala.collection.mutable.Map",
    "java.util.HashMap"    -> "scala.collection.mutable.HashMap",
    "java.util.Set"        -> "scala.collection.mutable.Set",
    "java.util.HashSet"    -> "scala.collection.mutable.HashSet",
    "java.util.Collection" -> "scala.collection.mutable.Iterable",
  )

  // prepared in `run`, read by the hooks.
  private var remap: Map[SymId, SymId] = Map.empty
  private var scalaColls: Set[SymId]   = Set.empty
  private var opPlusEq: SymId          = SymId.None
  private var updateSym: SymId         = SymId.None

  override def run(program: Program): Program =
    val added = collection.mutable.ListBuffer[Symbol]()
    var next  = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String): SymId =
      val id = SymId(next); next += 1
      added += Symbol(id, name, full, Flags(), SymId.None, TypeRepr.NoType)
      id
    remap = program.symbols.all.flatMap { s =>
      typeMap.get(s.fullName).map(sc => s.id -> mint(sc.substring(sc.lastIndexOf('.') + 1), sc))
    }.toMap
    scalaColls = remap.values.toSet
    opPlusEq   = mint("+=", "scala.<op>#+=") // rendered infix by the emitter
    updateSym  = mint("update", "update")

    val symbols = SymbolTable(program.symbols.all ++ added)
    given Program = new Program(program.units, symbols, program.xref)
    val units    = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    val symbols2 = StandardTraversal.mapSymbols(this, symbols) // retype signatures too
    new Program(units, symbols2, program.xref)

  override def transformType(t: TypeRepr)(using Program): TypeRepr = t match
    case TypeRepr.TypeRef(prefix, s) if remap.contains(s) => TypeRepr.TypeRef(prefix, remap(s))
    case other                                            => other

  override def transformApply(t: Tree.Apply)(using Program): Term = t.fun match
    case Tree.Select(recv, m, _, so) if onCollection(recv) =>
      (methodName(m), t.args) match
        case ("size", Nil) | ("isEmpty", Nil) => Tree.Select(recv, m, t.tpe, t.origin)            // drop `()`
        case ("get", List(i))                 => Tree.Apply(recv, List(i), m, t.tpe, t.origin)    // xs(i)
        case ("add", List(x))                 => infix(recv, opPlusEq, List(x), t, so)            // xs += x
        case ("put", List(k, v))              => Tree.Apply(Tree.Select(recv, updateSym, TypeRepr.NoType, so), List(k, v), updateSym, t.tpe, t.origin)
        case _                                => t
    case _ => t

  private def infix(recv: Term, op: SymId, args: List[Term], t: Tree.Apply, so: Origin): Term =
    Tree.Apply(Tree.Select(recv, op, TypeRepr.NoType, so), args, op, t.tpe, t.origin)

  private def methodName(m: SymId)(using p: Program): String = p.symbolOf(m).map(_.name).getOrElse("")

  /** the receiver's (already-retyped, bottom-up) head type is one of our scala collections. */
  private def onCollection(recv: Term): Boolean = headSym(recv.tpe).exists(scalaColls)

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None
