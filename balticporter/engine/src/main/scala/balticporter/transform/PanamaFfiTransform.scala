package balticporter.transform

import balticporter.tir.*

/** Replaces each JNI `native` method with a Project Panama (`java.lang.foreign`) downcall: a
  * private `MethodHandle` field built from the signature (each primitive → its `ValueLayout`,
  * everything else → `ADDRESS`), and a body invoking the handle and casting to the declared
  * return type. Detection is structural (`isNative`), so it finds the JNI surface rather than
  * being told where it is. First cut: JVM downcalls only (JDK 22+ `invokeExact`); a Scala Native
  * linker backend is a refinement point.
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
    val names = handleNames(program, natives)
    val handleSym: Map[SymId, SymId] = natives.iterator.map { m =>
      val owner = program.symbolOf(m).map(_.owner).getOrElse(SymId.None)
      val n     = names(m)
      m -> mint(n, n, Flags(isStatic = true, isPrivate = true), owner, mhRef)
    }.toMap

    natives.foreach { m =>
      program.symbolOf(m).foreach { s =>
        record(Decision(
          kind       = Decision.Kind.RetypedSignature,
          subject    = m,
          subjectFqn = s.fullName,
          detail = Map(
            "from" -> "java `native` (JNI), declared without a body",
            "to"   -> s"a Panama downcall through the generated `${names(m)}` handle",
            "why"  -> ("JNI glue is hand-written C on the JVM and absent from every other backend; " +
              "a `java.lang.foreign` downcall is derivable from the signature alone"),
          ),
          reason = Reason.Universal("jni-to-panama"),
          origin = Decision.originOf(program, s.id),
        ))
      }
    }

    // drop `native` from the rewritten methods — they now have a body
    val symbols0 = program.symbols.all.map(s =>
      if natives(s.id) then s.copy(flags = s.flags.copy(isNative = false)) else s)
    val symbols = SymbolTable(symbols0 ++ minted)
    given Program = program.rebuilt(symbols = symbols)

    val units = program.units.map(u => rewriteClass(u, natives, handleSym))
    program.rebuilt(units, symbols)

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

  /** The handle field's name for every native at once, keyed on a fact about the METHOD, never on
    * the frontend's mint counter (`ENGINE-LIMITS.md` M10 — a counter-keyed name moved 122 member
    * digests across untouched types once). A lone native of a name: `freeMemory$handle`. An
    * overload set: `copyJni$0$handle`, … ordered by erased signature, tiebroken by declaration
    * position — never by `FunctionDescriptor` (it erases to `ADDRESS` and collides) or by visit
    * order. [[invoke]] reads the name back off the minted symbol rather than re-deriving it. */
  private[balticporter] def handleNames(program: Program, natives: Set[SymId]): Map[SymId, String] =
    given Program = program
    def nameOf(m: SymId): String  = program.symbolOf(m).map(_.name).getOrElse("fn")
    def ownerOf(m: SymId): SymId  = program.symbolOf(m).map(_.owner).getOrElse(SymId.None)
    def sigOf(m: SymId): String   = program.symbolOf(m).map(_.info) match
      case Some(TypeRepr.MethodType(ps, r, _)) =>
        ps.map((_, t) => TirPrinter.tpe(t, TirPrinter.Style.canonical)).mkString(",") +
          ":" + TirPrinter.tpe(r, TirPrinter.Style.canonical)
      case _ => ""
    // tiebreak by source position, never by iteration order (which is the mint counter)
    def posOf(m: SymId): (String, Int, Int) =
      val o = Decision.originOf(program, m)
      (o.javaPath, o.line, o.col)
    natives.toList.groupBy(m => (ownerOf(m), nameOf(m))).flatMap { case ((_, n), ms) =>
      if ms.sizeIs == 1 then List(ms.head -> s"$n$$handle")
      else ms.sortBy(m => (sigOf(m), posOf(m))).zipWithIndex.map((m, i) => m -> s"$n$$$i$$handle")
    }

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
    // read the name off the minted symbol rather than re-deriving it, so the two cannot drift apart
    val h      = p.symbolOf(hs).map(_.name).getOrElse("fn$handle")
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
