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
    val names = handleNames(program, natives)
    val handleSym: Map[SymId, SymId] = natives.iterator.map { m =>
      val owner = program.symbolOf(m).map(_.owner).getOrElse(SymId.None)
      val n     = names(m)
      m -> mint(n, n, Flags(isStatic = true, isPrivate = true), owner, mhRef)
    }.toMap

    // DECISION PROVENANCE: one row per NATIVE method, which is already declaration-level — the
    // unit of work here IS a method. What moves in the emitted file is the DECLARATION: a bodyless
    // `native` method becomes an ordinary one with a body, beside a private `MethodHandle` field
    // that has no java behind it at all. Universal: `native` is a java modifier and the JNI glue it
    // names exists on no backend this engine targets, so detection is `isNative` and no policy
    // decides which methods are involved.
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

    // drop the `native` modifier from the rewritten methods (they now have a body).
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

  /** THE HANDLE FIELD'S NAME, for every native at once — and it is keyed on a fact about the
    * METHOD, never on a mint counter. `ENGINE-LIMITS.md` M10.
    *
    * It used to be `<method>$<SymId.raw>$handle`. `SymId.raw` is the FRONTEND'S MINT COUNTER, so
    * the name held only for as long as nothing before that method interned one more symbol than it
    * used to — and a conditional gaining a conversion in an unrelated compilation unit moved 122
    * member digests across four types the change never touched. That is not a cosmetic problem:
    * `members.tsv` is the blast radius `CLAUDE.md` §5.1 makes available BEFORE a compile and calls
    * a stronger revert check than any count, and a name keyed on the counter is precisely what
    * defeats it. The general rule, which is why this is worth a comment this long: **no identifier
    * the engine EMITS may be keyed on a mint counter.**
    *
    * WHAT THE DISAMBIGUATOR IS FOR is the only question, and this transform already answered it:
    * two `native` methods sharing one name in one owner — `copyJni(float[]…)`, `copyJni(int[]…)` —
    * need distinct fields. So the key is what java itself overloads on, and the name says WHICH
    * OVERLOAD rather than which mint:
    *
    *   - the only native of that name in that owner: `freeMemory$handle`, and nothing can move it;
    *   - one of several: `copyJni$0$handle`, `copyJni$1$handle`, … ordered by the ERASED SIGNATURE,
    *     sorted — so the ordinal follows a fact about the class and not the order the frontend
    *     happened to visit them in. Adding or retyping an overload can renumber its siblings, and
    *     that is honest: it is a change to the class, not to an unrelated file.
    *
    * NOT the `FunctionDescriptor`, which is the near-miss worth naming: it erases every reference
    * to `ADDRESS`, so libGDX's three `copyJni` overloads share ONE descriptor between them and a
    * name keyed on it would collide. The rendering is `TirPrinter.tpe` at `Style.canonical` — the
    * existing total, id-free renderer — rather than a second one written here, for the same reason
    * this file now derives the name ONCE: [[invoke]] reads it back off the minted symbol instead of
    * re-deriving it, so there is no second copy to disagree. */
  private def handleNames(program: Program, natives: Set[SymId]): Map[SymId, String] =
    given Program = program
    def nameOf(m: SymId): String  = program.symbolOf(m).map(_.name).getOrElse("fn")
    def ownerOf(m: SymId): SymId  = program.symbolOf(m).map(_.owner).getOrElse(SymId.None)
    def sigOf(m: SymId): String   = program.symbolOf(m).map(_.info) match
      case Some(TypeRepr.MethodType(ps, r, _)) =>
        ps.map((_, t) => TirPrinter.tpe(t, TirPrinter.Style.canonical)).mkString(",") +
          ":" + TirPrinter.tpe(r, TirPrinter.Style.canonical)
      case _ => ""
    natives.toList.groupBy(m => (ownerOf(m), nameOf(m))).flatMap { case ((_, n), ms) =>
      if ms.sizeIs == 1 then List(ms.head -> s"$n$$handle")
      else ms.sortBy(sigOf).zipWithIndex.map((m, i) => m -> s"$n$$$i$$handle")
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
    // read the name off the MINTED SYMBOL rather than re-deriving it: one derivation, so the field
    // and the call that reads it cannot drift apart (the two used to be two calls of one function,
    // which is the same defect with a shorter fuse).
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
