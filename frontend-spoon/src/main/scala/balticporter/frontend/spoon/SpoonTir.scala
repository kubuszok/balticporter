package balticporter.frontend.spoon

import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import spoon.Launcher
import spoon.reflect.declaration.*
import spoon.reflect.reference.*
import spoon.support.compiler.VirtualFile

import scala.jdk.CollectionConverters.*

/** Populates the typed IR ([[balticporter.tir]]) DIRECTLY from Spoon's resolved model —
  * the re-compiler's build-order step 2. Unlike the BIR frontend, nothing collapses to
  * strings: every declaration mints a stable-identity [[Symbol]], every type reference
  * resolves to a structured [[TypeRepr]] pointing at a symbol, and externals (JDK/library
  * types) are lazily interned so `usagesOf(java.util.List)` works even with no local
  * definition. [[Xref.build]] then indexes every usage by position.
  *
  * Scope: declarations, signatures, and TYPES — the substrate the whole-program transforms
  * query. Method BODIES are not yet translated (the BIR frontend still owns expression
  * fidelity); they surface as `rhs = None`, so term-level (Call) usages are absent for now.
  * Type-position tracing — the point of this pass — is complete, including class/method
  * type-parameter F-bounds.
  */
object SpoonTir:
  /** Build a [[Program]] from already-resolved top-level Spoon types. */
  def fromTypes(types: List[CtType[?]]): Program = new Builder().build(types)

  /** Convenience for tests / snippets: parse one in-memory source (no external classpath;
    * JDK types resolve by qualified name) and populate the TIR from its top-level types. */
  def fromSource(code: String, fileName: String = "Snippet.java"): Program =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    env.setNoClasspath(true)
    launcher.addInputResource(new VirtualFile(code, fileName))
    val model = launcher.buildModel()
    val tops  = model.getAllTypes.asScala.toList.filter(_.getDeclaringType == null)
    fromTypes(tops)

  // -------------------------------------------------------------------------
  /** Interns symbols by a stable string key (qualified names for types, `owner#member`
    * for members, `decl$$Name` for type params). One id per key, monotonic. */
  private final class Minter:
    private var next  = 0
    private val byKey = collection.mutable.Map[String, SymId]()
    private val syms  = collection.mutable.Map[SymId, Symbol]()

    def resolve(key: String): SymId =
      byKey.getOrElseUpdate(key, { val id = SymId(next); next += 1; id })

    def set(id: SymId, sym: Symbol): Unit = syms(id) = sym

    def define(key: String)(mk: SymId => Symbol): SymId =
      val id = resolve(key)
      syms(id) = mk(id)
      id

    /** Ensure a minimal stub exists for an external reference (never clobbers a real
      * definition, so define-after-reference wins). */
    def external(key: String, name: String): SymId =
      val id = resolve(key)
      if !syms.contains(id) then syms(id) = Symbol(id, name, key, Flags(), SymId.None, NoType)
      id

    def table: SymbolTable       = SymbolTable(syms.values)
    def idOf(key: String): SymId = byKey(key)

  private final class Builder:
    private val minter   = new Minter
    private val tpScopes = collection.mutable.ArrayDeque[Map[String, SymId]]()

    def build(types: List[CtType[?]]): Program =
      val units = types.map(classDef)
      new Program(units, minter.table, Xref.build(units))

    // ---- provenance ----
    private def originOf(el: CtElement): Origin =
      val p = el.getPosition
      if p != null && p.isValidPosition then
        Origin(Option(p.getFile).map(_.getPath).getOrElse("<unknown>"), p.getLine, p.getColumn)
      else Origin.synthetic

    private def tt(t: TypeRepr, el: CtElement): TypeTree = TypeTree(t, originOf(el))

    // ---- keys ----
    private def typeKey(t: CtTypeReference[?]): String = t.getQualifiedName
    private def memberKey(owner: SymId, sig: String): String = minterKeyOf(owner) + "#" + sig
    private def minterKeyOf(id: SymId): String = "@" + id.raw // members hang off their owner's id
    private def erasedSig(m: CtExecutable[?]): String =
      val ps = m.getParameters.asScala.toList
        .map(p => scala.util.Try(p.getType.getQualifiedName).getOrElse("?"))
        .mkString(",")
      s"($ps)"

    // ---- type parameter resolution ----
    private def resolveTypeParam(name: String): Option[SymId] =
      tpScopes.iterator.collectFirst { case m if m.contains(name) => m(name) }

    /** Mint ids for all formals FIRST (so bounds can self-reference — F-bounds), then
      * translate each bound with the frame in scope. Returns the frame and the TypeDefs. */
    private def mintTypeParams(declKey: String, owner: SymId, tps: List[CtTypeParameter]): (Map[String, SymId], List[Tree.TypeDef]) =
      val frame = tps.map(tp => tp.getSimpleName -> minter.resolve(declKey + "$$" + tp.getSimpleName)).toMap
      tpScopes.prepend(frame)
      val defs = tps.map { tp =>
        val id     = frame(tp.getSimpleName)
        val bounds = boundsOf(tp)
        minter.set(id, Symbol(id, tp.getSimpleName, declKey + "$$" + tp.getSimpleName, Flags(isParam = true), owner, bounds))
        Tree.TypeDef(id, tt(bounds, tp), originOf(tp))
      }
      tpScopes.remove(0)
      (frame, defs)

    private def boundsOf(tp: CtTypeParameter): TypeBounds =
      Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object").map(tpe) match
        case Some(hi) => TypeBounds(NoType, hi)
        case None     => TypeBounds(NoType, NoType)

    // ---- types ----
    private def tpe(tr: CtTypeReference[?]): TypeRepr = tr match
      case null => TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))
      case arr: CtArrayTypeReference[?] =>
        AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")), List(tpe(arr.getComponentType)))
      case inter: CtIntersectionTypeReference[?] =>
        inter.getBounds.asScala.toList.map(tpe).reduce(AndType(_, _))
      case w: CtWildcardReference =>
        val b = Option(w.getBoundingType).filter(_.getQualifiedName != "java.lang.Object").map(tpe)
        if w.isUpper then TypeBounds(NoType, b.getOrElse(NoType)) else TypeBounds(b.getOrElse(NoType), NoType)
      case tv: CtTypeParameterReference =>
        val id = resolveTypeParam(tv.getSimpleName).getOrElse(minter.external("?" + tv.getSimpleName, tv.getSimpleName))
        TypeRef(NoPrefix, id)
      case p if p.isPrimitive =>
        TypeRef(NoPrefix, minter.external("scala." + primName(p.getSimpleName), p.getSimpleName))
      case r =>
        val head = TypeRef(NoPrefix, typeSym(r))
        r.getActualTypeArguments.asScala.toList match
          case Nil  => head
          case args => AppliedType(head, args.map(tpe))

    /** id of a referenced class type — our own (already defined) or an external stub. */
    private def typeSym(r: CtTypeReference[?]): SymId = minter.external(typeKey(r), r.getSimpleName)

    private def primName(j: String): String = j match
      case "int"     => "Int";  case "long"    => "Long";  case "short"  => "Short"
      case "byte"    => "Byte"; case "char"    => "Char";  case "boolean" => "Boolean"
      case "float"   => "Float"; case "double" => "Double"; case "void"  => "Unit"
      case other     => other.capitalize

    // ---- declarations ----
    private def classDef(t: CtType[?]): Tree.ClassDef =
      val id   = defineType(t)
      val (frame, tpDefs) = mintTypeParams(typeKey(t.getReference), id, t.getFormalCtTypeParameters.asScala.toList)
      tpScopes.prepend(frame)
      val parents = superTypes(t)
      val fields = t.getFields.asScala.toList
        .filterNot(_.isInstanceOf[CtEnumValue[?]])
        .sortBy(posKey)
        .map(fieldDef(id, _))
      val ctors = t match
        case c: CtClass[?] => c.getConstructors.asScala.toList.sortBy(posKey).map(execDef(id, _, "<init>"))
        case _             => Nil
      val methods = t.getMethods.asScala.toList.sortBy(posKey).map(m => execDef(id, m, m.getSimpleName))
      val nested  = t.getNestedTypes.asScala.toList.sortBy(posKey).map(classDef)
      tpScopes.remove(0)
      Tree.ClassDef(id, parents, selfType = None, body = fields ++ ctors ++ methods ++ nested, origin = originOf(t), tparams = tpDefs)

    private def defineType(t: CtType[?]): SymId =
      val q = typeKey(t.getReference)
      minter.define(q)(id => Symbol(id, t.getSimpleName, q, typeFlags(t), ownerSym(t), TypeRef(NoPrefix, id)))

    private def ownerSym(t: CtType[?]): SymId =
      Option(t.getDeclaringType).map(dt => minter.external(typeKey(dt.getReference), dt.getSimpleName)).getOrElse(SymId.None)

    /** superclass (Extends, first) then interfaces (Mixin) — the parent linearization. */
    private def superTypes(t: CtType[?]): List[TypeTree] =
      val sc = t match
        case c: CtClass[?] => Option(c.getSuperclass).filter(_.getQualifiedName != "java.lang.Object")
        case _             => None
      (sc.toList ++ t.getSuperInterfaces.asScala.toList).map(tr => tt(tpe(tr), t))

    private def fieldDef(owner: SymId, f: CtField[?]): Tree.ValDef =
      val ft = tpe(f.getType)
      val id = minter.define(memberKey(owner, f.getSimpleName))(sid =>
        Symbol(sid, f.getSimpleName, f.getSimpleName, fieldFlags(f), owner, ft)
      )
      Tree.ValDef(id, tt(ft, f), rhs = None, origin = originOf(f))

    private def execDef(owner: SymId, m: CtExecutable[?], name: String): Tree.DefDef =
      val mkey = memberKey(owner, name + erasedSig(m))
      val id   = minter.resolve(mkey)
      val mtps = m match
        case ftd: CtFormalTypeDeclarer => ftd.getFormalCtTypeParameters.asScala.toList
        case _                         => Nil
      val (frame, tpDefs) = mintTypeParams(mkey, id, mtps)
      tpScopes.prepend(frame)
      val ps  = m.getParameters.asScala.toList
      val pvs = ps.map(paramDef(id, _))
      val ret = m match
        // a constructor's Spoon type is its declaring class; that is not a return
        // position, so don't record it as a member type — use Unit.
        case _: CtConstructor[?]      => TypeRef(NoPrefix, minter.external("scala.Unit", "Unit"))
        case named: CtTypedElement[?] => tpe(named.getType)
        case _                        => TypeRef(NoPrefix, minter.external("scala.Unit", "Unit"))
      tpScopes.remove(0)
      val sig = MethodType(ps.map(p => p.getSimpleName -> tpe(p.getType)), ret)
      minter.set(id, Symbol(id, name, name, execFlags(m), owner, sig))
      Tree.DefDef(id, paramss = List(pvs), returnTpt = tt(ret, m), rhs = None, origin = originOf(m), tparams = tpDefs)

    private def paramDef(owner: SymId, p: CtParameter[?]): Tree.ValDef =
      val pt = tpe(p.getType)
      val id = minter.define(minterKeyOf(owner) + "%" + p.getSimpleName)(sid =>
        Symbol(sid, p.getSimpleName, p.getSimpleName, Flags(isParam = true), owner, pt)
      )
      Tree.ValDef(id, tt(pt, p), rhs = None, origin = originOf(p))

    // ---- flags ----
    private def has(m: CtModifiable, k: ModifierKind): Boolean = m.hasModifier(k)
    import ModifierKind.*

    private def typeFlags(t: CtType[?]): Flags =
      val isTrait = t.isInstanceOf[CtInterface[?]]
      Flags(
        isAbstract = has(t, ABSTRACT) || isTrait,
        isFinal = has(t, FINAL),
        isTrait = isTrait,
        isEnum = t.isInstanceOf[CtEnum[?]],
        isPrivate = has(t, PRIVATE),
        isProtected = has(t, PROTECTED),
        isStatic = has(t, STATIC),
      )

    private def fieldFlags(f: CtField[?]): Flags =
      Flags(
        isFinal = has(f, FINAL),
        isMutable = !has(f, FINAL),
        isStatic = has(f, STATIC),
        isPrivate = has(f, PRIVATE),
        isProtected = has(f, PROTECTED),
      )

    private def execFlags(m: CtExecutable[?]): Flags = m match
      case mod: CtModifiable =>
        Flags(
          isAbstract = has(mod, ABSTRACT),
          isFinal = has(mod, FINAL),
          isStatic = has(mod, STATIC),
          isPrivate = has(mod, PRIVATE),
          isProtected = has(mod, PROTECTED),
        )
      case _ => Flags()

    private def posKey(el: CtElement): Int =
      val p = el.getPosition
      if p != null && p.isValidPosition then p.getSourceStart else Int.MaxValue
