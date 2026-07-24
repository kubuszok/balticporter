package balticporter.transform

import balticporter.tir.*

/** JNI `native` methods → Project Panama (`java.lang.foreign`) downcall bindings. The sge/ssg
  * case: replace hand-written JNI glue with generated Foreign Function & Memory API bindings
  * that work on both the JVM linker and (later) a Scala Native linker.
  *
  * For each `native` method the transform:
  *   - generates a private `MethodHandle` field — a `Linker.nativeLinker().downcallHandle(...)`
  *     over a `FunctionDescriptor` built from the signature (each Java primitive → its
  *     `ValueLayout`, everything else → `ADDRESS`), looked up by name in the default lookup;
  *   - replaces the (bodyless) `native` method with a body that invokes the handle and casts
  *     the result back to the declared type.
  *
  * The public method stays STRUCTURAL (its real params and return type drive the descriptor);
  * only the FFI plumbing — pure generated boilerplate keyed off the signature — is emitted as
  * synthesized [[Tree.Opaque]] glue. Detection is `isNative` (carried from Spoon's `native`
  * modifier), so it genuinely FINDS the JNI surface rather than being told where it is.
  *
  * First cut: JVM downcalls for primitive/pointer signatures. The `invokeExact` shape compiles
  * against `java.lang.foreign` (JDK 22+); an exact-typed per-arity invoker and the Scala Native
  * linker backend are the refinement points.
  */
final class PanamaFfiTransform(isNative: Symbol => Boolean = _.flags.isNative) extends Phase:
  def name = "jni->panama"

  private val minted = collection.mutable.ListBuffer[Symbol]()
  private var mhRef: TypeRepr = TypeRepr.NoType

  override def run(program: Program): Program =
    val natives = program.symbols.all
      .filter(s => s.info.isInstanceOf[TypeRepr.MethodType] && isNative(s)).map(_.id).toSet
    if natives.isEmpty then return program

    var next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    def mint(name: String, full: String, flags: Flags, owner: SymId, info: TypeRepr): SymId =
      val id = SymId(next); next += 1
      minted += Symbol(id, name, full, flags, owner, info)
      id
    val mhSym = mint("MethodHandle", "java.lang.invoke.MethodHandle", Flags(), SymId.None, TypeRepr.NoType)
    mhRef = TypeRepr.TypeRef(TypeRepr.NoPrefix, mhSym)
    val handleSym: Map[SymId, SymId] = natives.iterator.map { m =>
      val owner = program.symbolOf(m).map(_.owner).getOrElse(SymId.None)
      m -> mint(handleName(program, m), handleName(program, m), Flags(isStatic = true, isPrivate = true), owner, mhRef)
    }.toMap

    // drop the `native` modifier from the rewritten methods (they now have a body).
    val symbols0 = program.symbols.all.map(s =>
      if natives(s.id) then s.copy(flags = s.flags.copy(isNative = false)) else s)
    val symbols = SymbolTable(symbols0 ++ minted)
    given Program = new Program(program.units, symbols, program.xref)

    val units = program.units.map(u => rewriteClass(u, natives, handleSym))
    new Program(units, symbols, program.xref)

  private def rewriteClass(cd: Tree.ClassDef, natives: Set[SymId], handleSym: Map[SymId, SymId])(using Program): Tree.ClassDef =
    val o = Origin.synthetic
    val body = cd.body.flatMap {
      case d: Tree.DefDef if natives(d.symbol) =>
        val hs    = handleSym(d.symbol)
        val field = Tree.ValDef(hs, TypeTree(mhRef, o), Some(Tree.Opaque(downcall(d), mhRef, o)), o)
        val bound = d.copy(rhs = Some(Tree.Opaque(invoke(d, hs), d.returnTpt.tpe, o)))
        List(field, bound)
      case c: Tree.ClassDef => List(rewriteClass(c, natives, handleSym))
      case other            => List(other)
    }
    cd.copy(body = body)

  // ---- FFI codegen ----
  // include the method's SymId so OVERLOADED natives (same name, e.g. `copyJni(float[])` and
  // `copyJni(int[])`) get DISTINCT handle fields instead of colliding.
  private def handleName(p: Program, m: SymId): String = p.symbolOf(m).map(_.name).getOrElse("fn") + "$" + m.raw + "$handle"

  /** `Linker.nativeLinker().downcallHandle(lookup.find("name").orElseThrow(), descriptor)`. */
  private def downcall(d: Tree.DefDef)(using p: Program): String =
    val name = p.symbolOf(d.symbol).map(_.name).getOrElse("fn")
    val ret  = d.returnTpt.tpe
    val args = d.paramss.flatten.map(v => layout(v.tpt.tpe))
    val desc =
      if isVoid(ret) then s"java.lang.foreign.FunctionDescriptor.ofVoid(${args.mkString(", ")})"
      else s"java.lang.foreign.FunctionDescriptor.of(${(layout(ret) :: args).mkString(", ")})"
    "java.lang.foreign.Linker.nativeLinker().downcallHandle(" +
      s"""java.lang.foreign.Linker.nativeLinker().defaultLookup().find("$name").orElseThrow(), $desc)"""

  /** `handle.invokeExact(params).asInstanceOf[Ret]` (or a Unit-discarding block for void). */
  private def invoke(d: Tree.DefDef, hs: SymId)(using p: Program): String =
    val h      = handleName(p, d.symbol)
    val params = d.paramss.flatten.flatMap(v => p.symbolOf(v.symbol).map(_.name)).mkString(", ")
    val ret    = d.returnTpt.tpe
    if isVoid(ret) then s"{ $h.invokeExact($params); () }"
    else s"$h.invokeExact($params).asInstanceOf[${typeStr(ret)}]"

  private def layout(t: TypeRepr)(using p: Program): String = fullName(t) match
    case "scala.Int"     => "java.lang.foreign.ValueLayout.JAVA_INT"
    case "scala.Long"    => "java.lang.foreign.ValueLayout.JAVA_LONG"
    case "scala.Double"  => "java.lang.foreign.ValueLayout.JAVA_DOUBLE"
    case "scala.Float"   => "java.lang.foreign.ValueLayout.JAVA_FLOAT"
    case "scala.Boolean" => "java.lang.foreign.ValueLayout.JAVA_BOOLEAN"
    case "scala.Byte"    => "java.lang.foreign.ValueLayout.JAVA_BYTE"
    case "scala.Short"   => "java.lang.foreign.ValueLayout.JAVA_SHORT"
    case "scala.Char"    => "java.lang.foreign.ValueLayout.JAVA_CHAR"
    case _               => "java.lang.foreign.ValueLayout.ADDRESS" // pointers / objects → MemorySegment

  private def typeStr(t: TypeRepr)(using p: Program): String = fullName(t) match
    case "" => "scala.Any"
    case fn => fn
  private def isVoid(t: TypeRepr)(using p: Program): Boolean = fullName(t) == "scala.Unit"
  private def fullName(t: TypeRepr)(using p: Program): String = headSym(t).flatMap(p.symbolOf).map(_.fullName).getOrElse("")

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSym(tc)
    case _                           => None
