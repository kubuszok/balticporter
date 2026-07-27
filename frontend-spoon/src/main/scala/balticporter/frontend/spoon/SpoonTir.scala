package balticporter.frontend.spoon

import balticporter.core.{FrontendConfig, Substitutions}
import balticporter.tir.*
import balticporter.tir.TypeRepr.*

import spoon.Launcher
import spoon.reflect.code.*
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
  * Scope: declarations, signatures, TYPES, and method BODIES — the full substrate the
  * whole-program transforms query. Bodies translate to TIR terms with every reference
  * resolved to a `SymId` (see [[Builder.BodyTranslator]]), so `usagesOf`/`callersOf` are
  * real over actual code. Type-position tracing includes class/method type-parameter
  * F-bounds. The whole liqp corpus (135 types) translates with no `Unsupported`.
  */
object SpoonTir:
  /** Build a [[Program]] from already-resolved top-level Spoon types. */
  def fromTypes(types: List[CtType[?]], subs: Substitutions = Substitutions.none): Program =
    new Builder(subs).build(types)

  /** Build the Spoon model over a whole closure and return its top-level types. Full
    * classpath by default (like the BIR frontend); `lenient` uses noClasspath mode so a
    * library with unconfigured external deps still parses (types resolve where possible,
    * unresolved ones degrade to unmapped references — fine for construct coverage). */
  def buildModel(cfg: FrontendConfig, lenient: Boolean = false): List[CtType[?]] =
    val launcher = new Launcher
    val env      = launcher.getEnvironment
    env.setComplianceLevel(21)
    env.setCommentEnabled(false)
    env.setNoClasspath(lenient)
    env.setSourceClasspath(cfg.classpath.map(_.toString).toArray)
    if cfg.resolutionRoots.nonEmpty then
      cfg.resolutionRoots.foreach(r => launcher.addInputResource(r.toString))
      val covered = cfg.resolutionRoots.map(_.toRealPath())
      cfg.files
        .map(f => cfg.sourceRoot.resolve(f).toRealPath())
        .filterNot(abs => covered.exists(abs.startsWith))
        .foreach(abs => launcher.addInputResource(abs.toString))
    else cfg.files.foreach(f => launcher.addInputResource(cfg.sourceRoot.resolve(f).toString))
    launcher.buildModel().getAllTypes.asScala.toList.filter(_.getDeclaringType == null)

  /** Translate each top-level type in ISOLATION (fresh symbol space), returning per-type
    * success (symbol count) or the failure. Used to MEASURE corpus coverage — which
    * constructs still hit `Unsupported` — without one bad file sinking the batch. */
  def coverage(types: List[CtType[?]]): List[(String, Either[Throwable, Int])] =
    types.map { t =>
      t.getQualifiedName -> scala.util.Try(new Builder().build(List(t)).symbols.all.size).toEither
    }

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

    def table: SymbolTable        = SymbolTable(syms.values)
    def idOf(key: String): SymId  = byKey(key)
    def fullNameOf(id: SymId): String = syms.get(id).map(_.fullName).getOrElse("?")

  private final class Builder(subs: Substitutions = Substitutions.none):
    private val minter   = new Minter
    private val tpScopes = collection.mutable.ArrayDeque[Map[String, SymId]]()
    private val selfRawStack = collection.mutable.ArrayDeque[(SymId, List[SymId])]()
    /** Type params LEGALLY in scope at the current point, respecting static-nested boundaries: a
      * static nested class / interface / enum cannot see its enclosing type's params, unlike a
      * non-static inner class. Distinct from `tpScopes`/`resolveTypeParam`, which keep every
      * enclosing frame for reference resolution — name-directed raw-fill must NOT emit a param the
      * emitted Scala can't see (that produced `Not found: type T` inside static-nested `SaveData`). */
    private val tpAccessible = collection.mutable.ArrayDeque[Map[String, SymId]]()
    private def accessibleTp(name: String): Option[SymId] = tpAccessible.headOption.flatMap(_.get(name))
    /** A nested type captures its enclosing type's params iff it is a NON-static inner class. */
    private def capturesEnclosing(t: CtType[?]): Boolean =
      t.getDeclaringType != null && t.isInstanceOf[CtClass[?]] && !t.hasModifier(ModifierKind.STATIC)
    private var inStatic = false
    private def withStatic[A](s: Boolean)(f: => A): A =
      val prev = inStatic; inStatic = s
      try f finally inStatic = prev

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
      Option(tp.getSuperclass).filter(_.getQualifiedName != "java.lang.Object").map(fbound) match
        case Some(hi) => TypeBounds(NoType, hi)
        case None     => TypeBounds(NoType, NoType)

    /** Reconstruct a raw generic type's args from IN-SCOPE type parameters of the same NAME
      * (wildcards for the rest): `Node` under `Tree[N,V]` → `[N, V, ?]`, `Node` under
      * `Node[N,V,A]` → `[N, V, A]`, nested `Entries` under `ObjectMap[K,V]` → `[K, V]`, a
      * libgdx `Array` param → `[T]`. This preserves the self-reference / enclosing
      * instantiation that a plain wildcard fill erases — the erasure is what turns
      * `node.parent`, `this.entries1`, `array.items` into path-dependent captures that unify
      * with nothing. Returns None for a non-generic (arity-0) type. */
    private def nameFilledArgs(r: CtTypeReference[?], resolve: String => Option[SymId]): Option[List[TypeRepr]] =
      val formals = try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                    catch { case _: Throwable => Nil }
      if formals.isEmpty then None
      else Some(formals.map { f =>
        resolve(f.getSimpleName).map(pid => TypeRef(NoPrefix, pid)).getOrElse(TypeBounds(NoType, NoType))
      })

    private def objectT: TypeRepr = TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))

    /** The ERASURE of a type variable — its first bound with nested variables erased, else Object:
      * `T` → `Object`, `P extends AssetLoaderParameters<T>` → `AssetLoaderParameters[Object]`.
      * Used ONLY to build CASTS at wildcard-receiver call sites (see `erasedReceiverView`); it must
      * never drive a DECLARATION's type — declaring raw fields/params erased instead of wildcard
      * breaks assignment of concrete generic values (`Array[Object]` refuses `Array[String]`) and
      * was measured catastrophic (+277). */
    private def erasureOfFormal(f: CtTypeParameter, seen: Set[String], depth: Int): TypeRepr =
      Option(f.getSuperclass).filter(_.getQualifiedName != "java.lang.Object") match
        case None    => objectT
        case Some(b) => erasedType(b, seen + f.getSimpleName, depth)

    private def erasedType(b: CtTypeReference[?], seen: Set[String], depth: Int): TypeRepr =
      if depth <= 0 then objectT
      else b match
        case _: CtTypeParameterReference  => objectT
        case arr: CtArrayTypeReference[?] =>
          AppliedType(TypeRef(NoPrefix, minter.external("scala.Array", "Array")),
                      List(erasedType(arr.getComponentType, seen, depth)))
        case inter: CtIntersectionTypeReference[?] =>
          inter.getBounds.asScala.toList.headOption.map(erasedType(_, seen, depth)).getOrElse(objectT)
        case _: CtWildcardReference => objectT
        case p if p.isPrimitive     => tpe(p)
        case r =>
          val head = TypeRef(NoPrefix, typeSym(r))
          r.getActualTypeArguments.asScala.toList match
            case Nil =>
              val formals = try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                            catch { case _: Throwable => Nil }
              if formals.isEmpty then head
              else AppliedType(head, formals.map { ff =>
                // `seen` breaks F-bounded cycles (`N extends Node<N,…>`); depth bounds the rest
                if seen(ff.getSimpleName) then objectT else erasureOfFormal(ff, seen, depth - 1)
              })
            case args => AppliedType(head, args.map(a => erasedType(a, seen, depth - 1)))

    /** the erasure a DECLARED formal is seen at through an erased receiver: a bare type variable
      * resolves through its declaration (so `P` → `AssetLoaderParameters[Object]`, not `Object`). */
    private def erasedFormal(f: CtTypeReference[?]): TypeRepr = f match
      case tv: CtTypeParameterReference =>
        val d = try Option(tv.getDeclaration) catch { case _: Throwable => None }
        d.map(erasureOfFormal(_, Set.empty, 2)).getOrElse(objectT)
      case other => erasedType(other, Set.empty, 2)

    /** does this type mention any of `names` as a type variable (directly or in its arguments)? */
    private def mentionsTypeVar(tr: CtTypeReference[?], names: Set[String]): Boolean = tr match
      case tv: CtTypeParameterReference => names(tv.getSimpleName)
      case arr: CtArrayTypeReference[?] => mentionsTypeVar(arr.getComponentType, names)
      case _ =>
        try tr.getActualTypeArguments.asScala.exists(mentionsTypeVar(_, names))
        catch { case _: Throwable => false }

    /** Translate a type-parameter bound. A RAW generic bound (`N extends Node`) is Java's idiom
      * for a self-referential (F-)bound; name-directed fill (see [[nameFilledArgs]]) rebuilds it
      * — the decl's own params are already in scope here (minted before bounds are translated) —
      * rather than erasing to `Node[?, ?, ?]`. Non-raw / array / intersection / type-var bounds
      * defer to `tpe`. */
    private def fbound(tr: CtTypeReference[?]): TypeRepr =
      val isRawGeneric = !tr.isInstanceOf[CtTypeParameterReference] &&
        !tr.isInstanceOf[CtArrayTypeReference[?]] && !tr.isInstanceOf[CtIntersectionTypeReference[?]] &&
        !tr.isInstanceOf[CtWildcardReference] && !tr.isPrimitive && tr.getActualTypeArguments.isEmpty
      // bounds are translated inside `mintTypeParams` with the decl's own frame freshly in scope
      // (no static-nested boundary crossed), so `resolveTypeParam` is the right, complete source.
      (if isRawGeneric then nameFilledArgs(tr, resolveTypeParam) else None) match
        case Some(args) => AppliedType(TypeRef(NoPrefix, typeSym(tr)), args)
        case None       => tpe(tr)

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
          case Nil =>
            // a RAW use of a generic type — Java allows it, Scala requires arguments. Fill the
            // declared arity with wildcards (`Class` → `Class[?]`), so the reference type-checks.
            // (Wildcards beat `Object` overall: a raw value more often flows INTO a generic slot
            // than needs a concrete arg. The residual raw-into-type-param sites are cast below.)
            val arity = try Option(r.getTypeDeclaration).map(_.getFormalCtTypeParameters.size).getOrElse(0)
                        catch { case _: Throwable => 0 }
            // a raw use of the class we're currently INSIDE, in a NON-static member (where the class's
            // own type params are in scope): fill with them (`ArrayMap[K,V]`) instead of wildcards, so
            // member accesses stay on the enclosing instantiation rather than a path-dependent capture.
            selfRawStack.headOption match
              case Some((cls, params)) if !inStatic && cls == typeSym(r) && params.nonEmpty && params.sizeIs == arity =>
                AppliedType(head, params.map(p => TypeRef(NoPrefix, p)))
              case _ =>
                // outside the enclosing class: reconstruct args from same-named in-scope params
                // (nested `Entries` → `Entries[K,V]`, param `Array` → `Array[T]`) so member
                // projections stay path-INdependent. Gated to non-static contexts — a companion
                // object can't see the class's type params. Falls back to wildcards.
                if arity <= 0 then head
                else if inStatic then AppliedType(head, List.fill(arity)(TypeBounds(NoType, NoType)))
                else nameFilledArgs(r, accessibleTp).map(args => AppliedType(head, args))
                       .getOrElse(AppliedType(head, List.fill(arity)(TypeBounds(NoType, NoType))))
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
      selfRawStack.prepend(id -> t.getFormalCtTypeParameters.asScala.toList.map(tp => frame(tp.getSimpleName)))
      val enclosingAcc = if capturesEnclosing(t) then tpAccessible.headOption.getOrElse(Map.empty) else Map.empty
      tpAccessible.prepend(enclosingAcc ++ frame)
      val savedStatic = inStatic; inStatic = false // a class body isn't a static context for its instance members
      val parents = superTypes(t)
      val fields = t.getFields.asScala.toList
        .filterNot(_.isInstanceOf[CtEnumValue[?]])
        .sortBy(posKey)
        .map(fieldDef(id, _))
      // include enum constructors too — the emitter folds their PARAMS into the sealed class's primary
      // constructor so each constant (`Nearest(GL_NEAREST)`) has a matching parameter to pass to.
      val ctors = t match
        case c: CtClass[?] => c.getConstructors.asScala.toList.sortBy(posKey).map(execDef(id, _, "<init>"))
        case _             => Nil
      // Substitutions.dropMethods: a method opted out of mechanical translation (a ready Scala
      // equivalent is injected in its place). Keyed `owner#method` by simple name.
      val methods = t.getMethods.asScala.toList.sortBy(posKey)
        .filterNot(m => subs.dropsMethod(t.getQualifiedName, m.getSimpleName))
        .map(m => execDef(id, m, m.getSimpleName))
      val nested  = t.getNestedTypes.asScala.toList.sortBy(posKey).map(classDef)
      val enumCases = t match
        case e: CtEnum[?] => e.getEnumValues.asScala.toList.map(enumCase(id, _))
        case _            => Nil
      tpScopes.remove(0)
      selfRawStack.remove(0); tpAccessible.remove(0); inStatic = savedStatic
      Tree.ClassDef(id, parents, selfType = None, body = fields ++ ctors ++ methods ++ nested,
        origin = originOf(t), tparams = tpDefs, enumCases = enumCases)

    /** a Java enum constant → `EnumCase`: its ctor args, and any per-constant method overrides
      * (from its anonymous-class body), each keyed under the CONSTANT so it doesn't collide
      * with the enum's abstract method of the same name. */
    private def enumCase(enumId: SymId, v: CtEnumValue[?]): Tree.EnumCase =
      val caseId = minter.define(memberKey(enumId, v.getSimpleName))(sid =>
        Symbol(sid, v.getSimpleName, qualified(enumId, v.getSimpleName), Flags(isStatic = true), enumId, TypeRef(NoPrefix, enumId))
      )
      val bt = new BodyTranslator(enumId, enumId)
      val (args, body) = v.getDefaultExpression match
        case nc: CtNewClass[?] =>
          val a = nc.getArguments.asScala.toList.map(bt.exprOf)
          val b = Option(nc.getAnonymousClass).toList.flatMap(_.getTypeMembers.asScala.toList).collect {
            case m: CtMethod[?] => execDef(caseId, m, m.getSimpleName)
          }
          (a, b)
        case cc: CtConstructorCall[?] => (cc.getArguments.asScala.toList.map(bt.exprOf), Nil)
        case _                        => (Nil, Nil)
      Tree.EnumCase(caseId, args, body, originOf(v))

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

    private def fieldDef(owner: SymId, f: CtField[?]): Tree.ValDef = withStatic(fieldFlags(f).isStatic) {
      val ft = tpe(f.getType)
      val id = minter.define(memberKey(owner, f.getSimpleName))(sid =>
        Symbol(sid, f.getSimpleName, qualified(owner, f.getSimpleName), fieldFlags(f), owner, ft)
      )
      // a field initializer is a real expression: translate it so its usages are traced,
      // attributed to the field (not a method).
      val rhs = Option(f.getDefaultExpression).map(e => new BodyTranslator(id, owner).coercedExprOf(f.getType, e))
      Tree.ValDef(id, tt(ft, f), rhs = rhs, origin = originOf(f))
    }

    private def execDef(owner: SymId, m: CtExecutable[?], name: String): Tree.DefDef = withStatic(execFlags(m).isStatic) {
      val mkey = memberKey(owner, name + erasedSig(m))
      val id   = minter.resolve(mkey)
      val mtps = m match
        case ftd: CtFormalTypeDeclarer => ftd.getFormalCtTypeParameters.asScala.toList
        case _                         => Nil
      val (frame, tpDefs) = mintTypeParams(mkey, id, mtps)
      tpScopes.prepend(frame)
      // a method sees its class's accessible params (unless static — gated at the fill site) plus
      // its own; `withStatic` already carries `inStatic` for this exec.
      tpAccessible.prepend(tpAccessible.headOption.getOrElse(Map.empty) ++ frame)
      val bt = new BodyTranslator(id, owner)
      val ps = m.getParameters.asScala.toList
      val pvs = ps.map { p =>
        val pt  = tpe(p.getType)
        val pid = minter.define(minterKeyOf(id) + "%" + p.getSimpleName)(sid =>
          Symbol(sid, p.getSimpleName, qualified(id, p.getSimpleName), Flags(isParam = true, isVararg = p.isVarArgs), id, pt)
        )
        bt.registerVar(p, pid)
        Tree.ValDef(pid, tt(pt, p), rhs = None, origin = originOf(p))
      }
      val ret = m match
        // a constructor's Spoon type is its declaring class; that is not a return
        // position, so don't record it as a member type — use Unit.
        case _: CtConstructor[?]      => unitT
        case named: CtTypedElement[?] => tpe(named.getType)
        case _                        => unitT
      val sig = MethodType(ps.map(p => p.getSimpleName -> tpe(p.getType)), ret)
      minter.set(id, Symbol(id, name, qualified(owner, name), execFlags(m), owner, sig))
      // translate the body (with param + type-param scope in place) — this is what makes
      // Call / field-ref usages and `callersOf` real. Abstract/interface methods have none.
      val body = Option(m.getBody).map(b => bt.methodBody(b))
      tpScopes.remove(0); tpAccessible.remove(0)
      Tree.DefDef(id, paramss = List(pvs), returnTpt = tt(ret, m), rhs = body, origin = originOf(m), tparams = tpDefs)
    }

    private def qualified(owner: SymId, member: String): String = minter.fullNameOf(owner) + "#" + member
    private def unitT: TypeRepr = TypeRef(NoPrefix, minter.external("scala.Unit", "Unit"))

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
          isNative = has(mod, NATIVE),
        )
      case _ => Flags()

    private def posKey(el: CtElement): Int =
      val p = el.getPosition
      if p != null && p.isValidPosition then p.getSourceStart else Int.MaxValue

    private def simpleName(q: String): String =
      val afterDot = q.substring(q.lastIndexOf('.') + 1)
      afterDot.substring(afterDot.lastIndexOf('$') + 1)

    private def unsupported(el: CtElement, what: String): Nothing =
      val p    = el.getPosition
      val line = if p != null && p.isValidPosition then p.getLine.toString else "?"
      val path = if p != null && p.isValidPosition && p.getFile != null then p.getFile.getPath else "<snippet>"
      throw balticporter.core.Unsupported(path, line, what)

    // -----------------------------------------------------------------------
    /** Translates one method/ctor/field-initializer body into TIR terms, resolving every
      * reference to a `SymId`. Covered: locals, assignments, `if`/`while`/`return`/`throw`,
      * blocks, method calls, constructor calls, field/variable access, `this`, casts,
      * ternary, operators (as `x.op(y)` — the quotes.reflect shape), literals. Constructs
      * not yet modeled (for-loops, switch, try, lambdas, arrays, method refs) fail loudly
      * via `Unsupported`, the same anti-omission stance as the BIR frontend — the body node
      * set grows the same way the BIR one did.
      *
      * `classId` is the enclosing class (for `this`); `methodId` owns locals. */
    private final class BodyTranslator(methodId: SymId, classId: SymId):
      private val varIds  = new java.util.IdentityHashMap[CtVariable[?], SymId]()
      private val nameIds = collection.mutable.Map[String, SymId]()

      def registerVar(v: CtVariable[?], id: SymId): Unit =
        varIds.put(v, id); nameIds(v.getSimpleName) = id

      private def nothingT = TypeRef(NoPrefix, minter.external("scala.Nothing", "Nothing"))
      private def selfT    = TypeRef(NoPrefix, classId)
      private def ty(e: CtTypedElement[?]): TypeRepr = Option(e.getType).map(tpe).getOrElse(NoType)
      private def thisTerm(el: CtElement): Term  = Tree.This(classId, selfT, originOf(el))
      private def superTerm(el: CtElement): Term = Tree.Super(classId, selfT, originOf(el))
      /** true when a `this`-access targets THIS class (not an enclosing one) — only then does
        * it need qualifying; an outer `Outer.this.x` resolves bare in Scala. */
      private def isOwnThis(ta: CtThisAccess[?]): Boolean =
        Option(ta.getType).map(_.getQualifiedName).forall(_ == minter.fullNameOf(classId))

      /** entry: a method/ctor block → a TIR `Block` (statements, Unit result). */
      def methodBody(b: CtBlock[?]): Term =
        Tree.Block(b.getStatements.asScala.toList.map(stmt), unit(b), unitT, originOf(b))

      def exprOf(e: CtExpression[?]): Term = expr(e)
      /** translate an initializer, coercing it to `target` (null → type param, narrowing, etc.). */
      def coercedExprOf(target: CtTypeReference[?], e: CtExpression[?]): Term = coerce(target, e, expr(e))

      private def unit(el: CtElement): Term = Tree.Literal(Constant.UnitC, unitT, originOf(el))

      private def blockTerm(s: CtStatement): Term = s match
        case null          => Tree.Block(Nil, Tree.Literal(Constant.UnitC, unitT, Origin.synthetic), unitT, Origin.synthetic)
        case b: CtBlock[?] => Tree.Block(b.getStatements.asScala.toList.map(stmt), unit(b), unitT, originOf(b))
        case single        => Tree.Block(List(stmt(single)), unit(single), unitT, originOf(single))

      // ---- statements ----
      private def stmt(s: CtStatement): Statement = s match
        case v: CtLocalVariable[?] =>
          val vt = tpe(v.getType)
          val id = defineLocal(v, vt) // sets isMutable when the local is reassigned
          val rhs = Option(v.getDefaultExpression).map(e => coerce(v.getType, e, expr(e)))
          Tree.ValDef(id, tt(vt, v), rhs, originOf(v))
        case a: CtOperatorAssignment[?, ?] =>
          // Java compound assignment narrows implicitly: `int += float` means `= (int)(i + f)`.
          val lhs = expr(a.getAssigned)
          val res = binApply(opText(a.getKind), lhs, expr(a.getAssignment), ty(a))
          val lt  = a.getAssigned.getType
          val rt  = try a.getAssignment.getType catch { case _: Throwable => null }
          // Java binary numeric promotion lifts byte/short/char operands to at least `int`, so the
          // op result (rank ≥ int) may be wider than the target — e.g. `byte += byte` computes an
          // `int`. Narrow back to the target whenever `max(rhsRank, intRank) > targetRank`.
          val narrow = lt != null && lt.isPrimitive && rt != null && rt.isPrimitive &&
            primRank.get(lt.getSimpleName).exists(l => primRank.get(rt.getSimpleName).exists(r => math.max(r, primRank("int")) > l))
          val out = if narrow then Tree.Typed(res, tt(tpe(lt), a), tpe(lt), originOf(a)) else res
          Tree.Assign(lhs, out, unitT, originOf(a))
        case a: CtAssignment[?, ?] =>
          val tgt = Option(a.getAssigned.getType)
          val rhs = a.getAssignment
          Tree.Assign(expr(a.getAssigned), tgt.map(coerce(_, rhs, expr(rhs))).getOrElse(expr(rhs)), unitT, originOf(a))
        case i: CtIf =>
          val elze = Option(i.getElseStatement).map(blockTerm).getOrElse(unit(i))
          Tree.If(expr(i.getCondition), blockTerm(i.getThenStatement), elze, unitT, originOf(i))
        case r: CtReturn[?] =>
          // coerce the returned value to the method's declared return type (null → type param, etc.).
          val target = Option(r.getParent(classOf[CtMethod[?]])).flatMap(m => Option(m.getType))
          val ret = Option(r.getReturnedExpression).map(e => target.map(tp => coerce(tp, e, expr(e))).getOrElse(expr(e)))
          Tree.Return(ret, nothingT, originOf(r))
        case w: CtWhile =>
          Tree.While(expr(w.getLoopingExpression), blockTerm(w.getBody), unitT, originOf(w))
        case t: CtThrow =>
          Tree.Throw(expr(t.getThrownExpression), nothingT, originOf(t))
        case b: CtBlock[?]      => blockTerm(b)
        case inv: CtInvocation[?] => expr(inv)
        case cc: CtConstructorCall[?] => ctorCall(cc)
        case f: CtForEach =>
          val v  = f.getVariable
          val vt = tpe(v.getType)
          val id = defineLocal(v, vt)
          Tree.ForEach(Tree.ValDef(id, tt(vt, v), None, originOf(v)), expr(f.getExpression), blockTerm(f.getBody), unitT, originOf(f))
        case f: CtFor =>
          val init = f.getForInit.asScala.toList.map(stmt)
          val cond = Option(f.getExpression).map(expr)
          val upd  = f.getForUpdate.asScala.toList.map(stmt)
          Tree.For(init, cond, upd, blockTerm(f.getBody), unitT, originOf(f))
        case t: CtTryWithResource =>
          val res = t.getResources.asScala.toList.collect { case lv: CtLocalVariable[?] =>
            val rt = tpe(lv.getType)
            Tree.ValDef(defineLocal(lv, rt), tt(rt, lv), Option(lv.getDefaultExpression).map(expr), originOf(lv))
          }
          tryStmt(t, res)
        case t: CtTry             => tryStmt(t, Nil)
        case s: CtSwitch[?]       => switchStmt(s)
        case b: CtBreak           => Tree.Break(Option(b.getTargetLabel), nothingT, originOf(b))
        case c: CtContinue        => Tree.Continue(Option(c.getTargetLabel), nothingT, originOf(c))
        case a: CtAssert[?]       => Tree.Assert(expr(a.getAssertExpression), Option(a.getExpression).map(expr), unitT, originOf(a))
        case d: CtDo              => Tree.DoWhile(blockTerm(d.getBody), expr(d.getLoopingExpression), unitT, originOf(d))
        case y: CtSynchronized    => Tree.Synchronized(expr(y.getExpression), blockTerm(y.getBlock), unitT, originOf(y))
        case u: CtUnaryOperator[?] =>
          import UnaryOperatorKind.*
          val one = Tree.Literal(Constant.IntC(1), ty(u), originOf(u))
          u.getKind match
            case POSTINC | PREINC => val t = expr(u.getOperand); Tree.Assign(t, incNarrow(u.getOperand, binApply("+", t, one, ty(u))), unitT, originOf(u))
            case POSTDEC | PREDEC => val t = expr(u.getOperand); Tree.Assign(t, incNarrow(u.getOperand, binApply("-", t, one, ty(u))), unitT, originOf(u))
            case _                => expr(u)
        case other => unsupported(other, s"statement ${other.getClass.getSimpleName}")

      private def defineLocal(v: CtVariable[?], vt: TypeRepr): SymId =
        val key = "@" + methodId.raw + "$L$" + v.getSimpleName + "#" + posKey(v)
        val mut = v.isInstanceOf[CtLocalVariable[?]] && isReassigned(v)
        val id  = minter.define(key)(sid => Symbol(sid, v.getSimpleName, v.getSimpleName, Flags(isMutable = mut), methodId, vt))
        registerVar(v, id)
        id

      /** does the enclosing method body write to `v` after its declaration? (then it's a `var`). */
      private def isReassigned(v: CtVariable[?]): Boolean =
        val scope = v.getParent(classOf[CtExecutable[?]])
        scope != null && writesToVar(scope, v.getSimpleName)

      private def writesToVar(scope: CtElement, name: String): Boolean =
        val assigns = scope.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtAssignment[?, ?]])).asScala
        val unaries = scope.getElements(new spoon.reflect.visitor.filter.TypeFilter(classOf[CtUnaryOperator[?]])).asScala
        assigns.exists { a =>
          a.getAssigned match { case w: CtVariableWrite[?] => w.getVariable.getSimpleName == name; case _ => false }
        } || unaries.exists { u =>
          import UnaryOperatorKind.*
          Set(POSTINC, POSTDEC, PREINC, PREDEC).contains(u.getKind) &&
            (u.getOperand match { case va: CtVariableAccess[?] => va.getVariable.getSimpleName == name; case _ => false })
        }

      /** Java permits two implicit conversions Scala forbids: array covariance (`Sub[]` → a
        * `Super[]` slot) and `null` → a type parameter. Insert an explicit `asInstanceOf` so the
        * ported assignment/initializer type-checks. */
      private val primRank = Map("byte" -> 1, "short" -> 2, "char" -> 2, "int" -> 3, "long" -> 4, "float" -> 5, "double" -> 6)

      private def coerce(target: CtTypeReference[?], e: CtExpression[?], t: Term, arrayCov: Boolean = true, tpToObject: Boolean = true): Term =
        val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
        val et     = try e.getType catch { case _: Throwable => null }
        val narrowing = target.isPrimitive && et != null && et.isPrimitive &&
          primRank.get(target.getSimpleName).exists(tr => primRank.get(et.getSimpleName).exists(_ > tr))
        // a primitive flowing into a concrete REFERENCE slot (`Object`, `Number`, …) is Java
        // autoboxing — Scala won't box into every such position, so make it explicit.
        val boxing = et != null && et.isPrimitive && !target.isPrimitive &&
          !target.isInstanceOf[CtTypeParameterReference] && !target.isInstanceOf[CtArrayTypeReference[?]]
        // a value erased to `Object` (a generic method's result) flowing into a more specific
        // slot — Java inserts an unchecked downcast; Scala needs it explicit.
        val downcast = et != null && et.getQualifiedName == "java.lang.Object" &&
          !target.isPrimitive && target.getQualifiedName != "java.lang.Object"
        // a boxed wrapper flowing into a PRIMITIVE slot is Java auto-UNBOXING (possibly with a widening,
        // `Integer`→`float`); Scala does neither implicitly, so emit the explicit `n.floatValue()`
        // (every `Number` wrapper carries all the `xxxValue()` accessors; `Boolean`/`Character` their own).
        // Only a CROSS-type unbox (`Integer`→`float`) needs this — a same-type unbox (`Integer`→`int`)
        // is already handled by Scala's `Predef.Integer2int`, and forcing `.intValue()` there only
        // perturbs surrounding resolution.
        if et != null && !et.isPrimitive && target.isPrimitive && wrapperOf.values.toSet(et.getQualifiedName)
          && wrapperOf.get(target.getSimpleName).exists(_ != et.getQualifiedName) then
          return unbox(t, target.getSimpleName, e)
        // a type-parameter value flowing into a genuinely-`Object` slot (a return/assignment/var-init
        // where the target type is really `java.lang.Object`, not an erased formal — call args are
        // handled by `typeParamToObject` off the DECLARED formal, so this stays off that path):
        // Java erases `T` to `Object`; Scala's unbounded `T <: Any` does not conform. Cast it.
        val tpObj = tpToObject && et != null && et.isInstanceOf[CtTypeParameterReference] &&
          target.getQualifiedName == "java.lang.Object"
        val cast =
          tpObj ||                                                                // T → Object (non-arg)
          (isNull && target.isInstanceOf[CtTypeParameterReference]) ||             // null → type param
          (arrayCov && target.isInstanceOf[CtArrayTypeReference[?]] && et != null &&  // array covariance
            et.isInstanceOf[CtArrayTypeReference[?]] && target.getQualifiedName != et.getQualifiedName) ||
          narrowing ||                                                            // int → short/byte/char
          boxing ||                                                               // int → Object/Number
          downcast                                                                // Object → specific
        // Box to the primitive's WRAPPER (`int` → `java.lang.Integer`), not the (often Object-erased)
        // formal: the wrapper is what Java autoboxing yields and it satisfies both the erased `Object`
        // slot AND a real `Integer`/`Number` one — where casting straight to `Object` fails an
        // `Integer` parameter that Spoon erased at the call reference.
        val ct = if boxing then boxedPrimitive(et.getSimpleName) else tpe(target)
        if cast then Tree.Typed(t, tt(ct, e), ct, originOf(e)) else t

      private val wrapperOf = Map(
        "byte" -> "java.lang.Byte", "short" -> "java.lang.Short", "char" -> "java.lang.Character",
        "int" -> "java.lang.Integer", "long" -> "java.lang.Long", "float" -> "java.lang.Float",
        "double" -> "java.lang.Double", "boolean" -> "java.lang.Boolean")
      private val valueMethod = Map(
        "int" -> "intValue", "long" -> "longValue", "float" -> "floatValue", "double" -> "doubleValue",
        "short" -> "shortValue", "byte" -> "byteValue", "boolean" -> "booleanValue", "char" -> "charValue")
      /** `wrapper.<prim>Value()` — explicit unboxing of a boxed number/boolean/char to a primitive. */
      private def unbox(t: Term, prim: String, e: CtElement): Term =
        val primT = TypeRef(NoPrefix, minter.external("scala." + primName(prim), prim))
        valueMethod.get(prim) match
          case Some(vm) =>
            val vsym = minter.external("java.lang.Number#" + vm, vm)
            Tree.Apply(Tree.Select(t, vsym, NoType, originOf(e)), Nil, vsym, primT, originOf(e))
          case None => t
      private def boxedPrimitive(prim: String): TypeRepr =
        wrapperOf.get(prim) match
          case Some(fqn) => TypeRef(NoPrefix, minter.external(fqn, simpleName(fqn)))
          case None      => TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))

      /** coerce each argument to its formal parameter type (Java autoboxing / numeric narrowing
        * that Scala won't do implicitly). Skipped when arities differ (varargs spread etc.). */
      private def coerceArgs(ex: CtExecutableReference[?], argEs: List[CtExpression[?]]): List[Term] =
        // Array covariance at call args is DISABLED for OUR OWN methods — Spoon erases a generic
        // array formal (`T[]`) to `Object[]`, and casting the arg to `Array[Object]` breaks the
        // (overloaded) Scala method that actually wants the invariant `Array[T]`. But for EXTERNAL
        // (JDK/library) callees there is no `Array[T]` Scala overload — the real method genuinely
        // takes `Object[]` (Java erasure), so `Arrays.fill(items: Array[T], …)` / `copyOf` need the
        // `items.asInstanceOf[Array[Object]]` erasure cast. Enable covariance only for those.
        // A JDK/library method's declaration is a SHADOW type (reconstructed from bytecode/reflection);
        // our own source types are non-shadow. (`getExecutableDeclaration` is non-null even for JDK
        // methods under noClasspath, so isShadow — not null-ness — is the reliable external signal.)
        val external = Option(ex.getExecutableDeclaration) match
          case None    => true
          case Some(d) => Option(d.getParent(classOf[CtType[?]])).forall(_.isShadow)
        val formals = ex.getParameters.asScala.toList
        // Under noClasspath, an executable REFERENCE erases a generic formal `T` to `Object`, so
        // `coerce` sees `null → Object` (legal) and skips the cast — yet the emitted method keeps
        // the real `T`, where `null → T` fails. Consult the DECLARATION's un-erased formals to
        // recover the type parameter and cast the null there (`set(null.asInstanceOf[T])`).
        val declFormals: Int => Option[CtTypeReference[?]] =
          val ps = Option(ex.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
          i => ps.flatMap(l => if i < l.size then Option(l(i)) else None)
        if formals.size == argEs.size then
          argEs.zipWithIndex.map { (e, i) =>
            val c = nullToTypeParam(e, declFormals(i), coerce(formals(i), e, expr(e), arrayCov = external, tpToObject = false))
            typeParamToObject(e, declFormals(i), c)
          }
        else argEs.map(expr)

      /** `null` passed to a callee slot whose real (un-erased) formal is a type parameter — cast it,
        * so the ported call type-checks (`m(null)` → `m(null.asInstanceOf[T])`). The dominant case is
        * a self-call inside the generic class, where `T` is in scope at the call site. */
      private def nullToTypeParam(e: CtExpression[?], declFormal: Option[CtTypeReference[?]], t: Term): Term =
        val isNull = e match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
        declFormal match
          // only when the callee's type parameter actually RESOLVES in scope here (a self-call inside
          // the same generic class) — otherwise `tpe` yields the `?T` unresolved stub, invalid syntax.
          case Some(tp: CtTypeParameterReference) if isNull && resolveTypeParam(tp.getSimpleName).isDefined =>
            Tree.Typed(t, tt(tpe(tp), e), tpe(tp), originOf(e))
          case _ => t

      /** A type-parameter-typed value flowing into a slot whose real formal is concretely
        * `java.lang.Object` (`Json.writeValue(String, Object, …)`): Java erases `T` to `Object`, but
        * Scala's unbounded `T <: Any` does NOT conform to `Object`. Cast (`resource.asInstanceOf[Object]`).
        * Gated on the DECLARED formal being Object — NOT a type parameter erased to Object — so we
        * never break our own `foo(x: T)` methods (whose real Scala signature keeps the invariant `T`). */
      private def typeParamToObject(e: CtExpression[?], declFormal: Option[CtTypeReference[?]], t: Term): Term =
        val et = try e.getType catch { case _: Throwable => null }
        declFormal match
          case Some(f) if !f.isInstanceOf[CtTypeParameterReference] && f.getQualifiedName == "java.lang.Object"
                       && et != null && et.isInstanceOf[CtTypeParameterReference] =>
            val obj = TypeRef(NoPrefix, minter.external("java.lang.Object", "Object"))
            Tree.Typed(t, tt(obj, e), obj, originOf(e))
          case _ => t

      private def tryStmt(t: CtTry, resources: List[Tree.ValDef]): Term =
        val catches = t.getCatchers.asScala.toList.map { c =>
          val p  = c.getParameter
          val pt = p.getMultiTypes.asScala.toList match
            case Nil    => tpe(p.getType)
            case multi  => multi.map(tpe).reduce(OrType(_, _))
          val id = defineLocal(p, pt)
          Tree.CatchCase(Tree.ValDef(id, tt(pt, p), None, originOf(p)), blockTerm(c.getBody))
        }
        Tree.Try(resources, blockTerm(t.getBody), catches, Option(t.getFinalizer).map(blockTerm), unitT, originOf(t))

      /** Java switch → TIR `Match`. Empty (grouping) cases merge their labels into the next;
        * genuine fallthrough is lowered by TAIL DUPLICATION — a non-terminated case's body is
        * its own statements followed by the next case's closure (the same faithful lowering
        * the BIR frontend uses, RESEARCH §4.2), so no `Unsupported`. */
      private def switchStmt(s: CtSwitch[?]): Term =
        val cases = s.getCases.asScala.toList
        def stmtsOf(c: CtCase[?]): List[CtStatement] = c.getStatements.asScala.toList match
          case List(b: CtBlock[?]) => b.getStatements.asScala.toList
          case l                   => l
        // per case: (body without a trailing break, terminated?)
        val split = cases.map { c =>
          val raw = stmtsOf(c)
          raw.reverse match
            case (_: CtBreak) :: rest => (rest.reverse, true)
            case _                    => (raw, raw.lastOption.exists { case _: CtReturn[?] | _: CtThrow => true; case _ => false })
        }
        val closures = new Array[List[CtStatement]](cases.length)
        for i <- cases.indices.reverse do
          val (body, terminated) = split(i)
          closures(i) = if terminated || i == cases.length - 1 then body else body ++ closures(i + 1)
        val out     = List.newBuilder[Tree.CaseDef]
        var pending = List.empty[Term]
        cases.zipWithIndex.foreach { case (c, idx) =>
          val labels    = c.getCaseExpressions.asScala.toList.map(expr)
          val isDefault = labels.isEmpty
          val isLast    = idx == cases.length - 1
          if split(idx)._1.isEmpty && stmtsOf(c).isEmpty && !isDefault && !isLast then pending = pending ++ labels
          else
            out += Tree.CaseDef(pending ++ labels, None, Tree.Block(closures(idx).map(stmt), unit(c), unitT, originOf(c)), isDefault)
            pending = Nil
        }
        Tree.Match(expr(s.getSelector), out.result(), unitT, originOf(s))

      // ---- expressions ----
      private def expr(e: CtExpression[?]): Term =
        val core = exprNoCast(e)
        e.getTypeCasts.asScala.toList.foldRight(core) { (t, acc) =>
          val ct = tpe(t); Tree.Typed(acc, tt(ct, e), ct, originOf(e))
        }

      private def exprNoCast(e: CtExpression[?]): Term = e match
        case l: CtLiteral[?]      => literal(l)
        case f: CtFieldRead[?]    => fieldAccess(f.getVariable, f.getTarget, e)
        case f: CtFieldWrite[?]   => fieldAccess(f.getVariable, f.getTarget, e)
        case _: CtThisAccess[?]   => thisTerm(e)
        case v: CtVariableRead[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
        case v: CtVariableWrite[?] => Tree.Ident(resolveVar(v.getVariable), ty(e), originOf(e))
        case inv: CtInvocation[?] => invocation(inv)
        case cc: CtConstructorCall[?] => ctorCall(cc)
        case a: CtArrayRead[?]  => Tree.ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression), ty(e), originOf(e))
        case a: CtArrayWrite[?] => Tree.ArrayAccess(expr(a.getTarget), expr(a.getIndexExpression), ty(e), originOf(e))
        case na: CtNewArray[?]  => newArray(na)
        case l: CtLambda[?]     => lambda(l)
        case mr: CtExecutableReferenceExpression[?, ?] => methodRef(mr)
        case b: CtBinaryOperator[?] =>
          if b.getKind == BinaryOperatorKind.INSTANCEOF then
            val tp = b.getRightHandOperand match
              case ta: CtTypeAccess[?] => tpe(ta.getAccessedType)
              case other               => unsupported(other, "instanceof right operand")
            Tree.InstanceOf(expr(b.getLeftHandOperand), tt(tp, b), ty(b), originOf(b))
          else if b.getKind == BinaryOperatorKind.PLUS && isStringConcat(b) && !isStringTyped(b.getLeftHandOperand) then
            // Java string concatenation with a non-String LEFT operand (`obj + "s"`): Scala has no
            // `+` on `obj`, so stringify the left (`String.valueOf(obj) + "s"`).
            binApply("+", stringify(expr(b.getLeftHandOperand), b), expr(b.getRightHandOperand), ty(b))
          else binApply(opText(b.getKind), expr(b.getLeftHandOperand), expr(b.getRightHandOperand), ty(b))
        case u: CtUnaryOperator[?] =>
          import UnaryOperatorKind.*
          u.getKind match
            case NOT     => unApply("unary_!", expr(u.getOperand), ty(u))
            case NEG     => unApply("unary_-", expr(u.getOperand), ty(u))
            case POS     => unApply("unary_+", expr(u.getOperand), ty(u))
            case COMPL   => unApply("unary_~", expr(u.getOperand), ty(u))
            case POSTINC => Tree.IncDec(expr(u.getOperand), "+", post = true, ty(u), originOf(u))
            case POSTDEC => Tree.IncDec(expr(u.getOperand), "-", post = true, ty(u), originOf(u))
            case PREINC  => Tree.IncDec(expr(u.getOperand), "+", post = false, ty(u), originOf(u))
            case PREDEC  => Tree.IncDec(expr(u.getOperand), "-", post = false, ty(u), originOf(u))
        // assignment used as a VALUE (`return a = v`, `while ((line = read()) != null)`):
        // Java yields the assigned value, Scala's `=` is Unit — lower to `{ lhs = rhs; lhs }`.
        case a: CtOperatorAssignment[?, ?] =>
          val lhs = expr(a.getAssigned)
          val st  = Tree.Assign(lhs, binApply(opText(a.getKind), lhs, expr(a.getAssignment), ty(a)), unitT, originOf(a))
          Tree.Block(List(st), lhs, ty(a), originOf(a))
        case a: CtAssignment[?, ?] =>
          val lhs = expr(a.getAssigned)
          val st  = Tree.Assign(lhs, expr(a.getAssignment), unitT, originOf(a))
          Tree.Block(List(st), lhs, ty(a), originOf(a))
        case c: CtConditional[?] =>
          // Java `b ? x : null` typed as the type parameter `V`; Scala infers `x.type | Null`, which
          // won't satisfy a `V` slot. Cast a null branch to the conditional's own type so the ternary
          // stays `V`. Guarded: only when that type resolves (never emit the `?T` unresolved stub).
          val ct = ty(c)
          def branch(be: CtExpression[?]): Term =
            val t      = expr(be)
            val isNull = be match { case l: CtLiteral[?] => l.getValue == null; case _ => false }
            if isNull && ct != NoType && condTypeResolves(c) then Tree.Typed(t, tt(ct, be), ct, originOf(be)) else t
          Tree.If(expr(c.getCondition), branch(c.getThenExpression), branch(c.getElseExpression), ct, originOf(c))
        case ta: CtTypeAccess[?] => Tree.Literal(Constant.ClassOfC(tpe(ta.getAccessedType)), ty(e), originOf(e))
        case other => unsupported(other, s"expression ${other.getClass.getSimpleName}")

      /** the conditional's static type is safe to ascribe onto a null branch — a concrete type, or a
        * type parameter that actually resolves in scope (not the `?T` unresolved stub). */
      private def condTypeResolves(c: CtConditional[?]): Boolean =
        (try c.getType catch { case _: Throwable => null }) match
          case null                         => false
          case tp: CtTypeParameterReference => resolveTypeParam(tp.getSimpleName).isDefined
          case _                            => true

      private def literal(l: CtLiteral[?]): Term =
        val c: Constant = l.getValue match
          case null                    => Constant.NullC
          case b: java.lang.Boolean    => Constant.BoolC(b)
          case ch: java.lang.Character => Constant.CharC(ch)
          case s: java.lang.String     => Constant.StringC(s)
          case n: java.lang.Integer    => Constant.IntC(n)
          case n: java.lang.Long       => Constant.LongC(n)
          case n: java.lang.Double     => Constant.DoubleC(n)
          case n: java.lang.Float      => Constant.FloatC(n)
          case n: java.lang.Byte       => Constant.ByteC(n)
          case n: java.lang.Short      => Constant.ShortC(n)
          case other                   => unsupported(l, s"literal ${other.getClass.getSimpleName}")
        Tree.Literal(c, ty(l), originOf(l))

      private def resolveVar(ref: CtVariableReference[?]): SymId =
        val decl = Option(ref.getDeclaration).orNull
        if decl != null && varIds.containsKey(decl) then varIds.get(decl)
        else nameIds.getOrElse(ref.getSimpleName, minter.external("?var$" + ref.getSimpleName, ref.getSimpleName))

      private def newArray(na: CtNewArray[?]): Term =
        val elemT = na.getType match
          case arr: CtArrayTypeReference[?] => tpe(arr.getComponentType)
          case t                            => tpe(t)
        val inits = na.getElements.asScala.toList
        val dims  = na.getDimensionExpressions.asScala.toList
        val et    = tt(elemT, na)
        if inits.nonEmpty || dims.isEmpty then Tree.NewArray(et, Nil, Some(inits.map(expr)), ty(na), originOf(na))
        else Tree.NewArray(et, dims.map(expr), None, ty(na), originOf(na))

      private def lambda(l: CtLambda[?]): Term =
        val pvs = l.getParameters.asScala.toList.map { p =>
          val pt = tpe(p.getType)
          Tree.ValDef(defineLocal(p, pt), tt(pt, p), None, originOf(p))
        }
        val body =
          if l.getExpression != null then expr(l.getExpression)
          else if l.getBody != null then blockTerm(l.getBody)
          else unsupported(l, "lambda without body")
        Tree.Lambda(pvs, body, ty(l), originOf(l))

      private def methodRef(mr: CtExecutableReferenceExpression[?, ?]): Term =
        val mid = methodSym(mr.getExecutable)
        val qual: Either[TypeTree, Term] = mr.getTarget match
          case ta: CtTypeAccess[?] => Left(tt(tpe(ta.getAccessedType), mr))
          case t                   => Right(expr(t))
        Tree.MethodRef(qual, mid, ty(mr), originOf(mr))

      private def fieldAccess(ref: CtFieldReference[?], target: CtExpression[?], at: CtExpression[?]): Term =
        if ref.getSimpleName == "class" then
          // `Foo.class` → `classOf[Foo]`: the argument is the ACCESSED type (`Foo`), not the type
          // of the `.class` expression (`java.lang.Class[Foo]`, which is what `ty(at)` gives).
          val accessed = target match { case ta: CtTypeAccess[?] => tpe(ta.getAccessedType); case _ => ty(at) }
          Tree.Literal(Constant.ClassOfC(accessed), ty(at), originOf(at))
        else if ref.getSimpleName == "length" && Option(ref.getDeclaringType).exists(_.isInstanceOf[CtArrayTypeReference[?]]) then
          Tree.ArrayLength(expr(target), ty(at), originOf(at))
        else
          val fid = fieldSym(ref)
          target match
            case ta: CtTypeAccess[?]                 => staticFieldAccess(ta, ref, fid, at) // static (re-qualify if inherited)
            // fields: `super.f`, an outer `Outer.this.f`, and implicit `f` all resolve as a BARE
            // name in Scala (inherited or enclosing). Only an OWN `this.f` needs qualifying.
            case _: CtSuperAccess[?]                 => Tree.Ident(fid, ty(at), originOf(at))
            case null                                => Tree.Ident(fid, ty(at), originOf(at))
            case ta: CtThisAccess[?] if !isOwnThis(ta) => Tree.Ident(fid, ty(at), originOf(at))
            case _: CtThisAccess[?]                  => Tree.Select(thisTerm(at), fid, ty(at), originOf(at))
            case other                               => Tree.Select(expr(other), fid, ty(at), originOf(at))

      /** A Java static field read through a SUBCLASS (`Rotational3D.TMP_V3`, where `TMP_V3` is declared
        * in an ancestor `DynamicsModifier`) — Scala companion objects don't inherit statics, so resolve
        * the field to its real DECLARING type and qualify by that (`DynamicsModifier.TMP_V3`). Falls back
        * to the written qualifier when the declaring type can't be located. */
      private def staticFieldAccess(ta: CtTypeAccess[?], ref: CtFieldReference[?], fid: SymId, at: CtExpression[?]): Term =
        val name  = ref.getSimpleName
        val ownerT = declaringStaticType(ta.getAccessedType, name)
        ownerT match
          case Some(t) if t.getQualifiedName != ta.getAccessedType.getQualifiedName =>
            val ownerId = minter.external(t.getQualifiedName, simpleName(t.getQualifiedName))
            val fid2    = minter.external(memberKey(ownerId, name), name)
            Tree.Select(Tree.Ident(ownerId, TypeRef(NoPrefix, ownerId), originOf(at)), fid2, ty(at), originOf(at))
          case _ => Tree.Select(typeTerm(ta, at), fid, ty(at), originOf(at))

      /** the type that DECLARES a static field `name`, walking the accessed type's superclass chain
        * (source types only; degrades to None on shadow/unresolved types). */
      private def declaringStaticType(accessed: CtTypeReference[?], name: String): Option[CtType[?]] =
        var t: CtType[?] = try accessed.getTypeDeclaration catch { case _: Throwable => null }
        val seen = collection.mutable.Set[String]()
        while t != null && seen.add(t.getQualifiedName) do
          if (try t.getFields.asScala.exists(_.getSimpleName == name) catch { case _: Throwable => false }) then return Some(t)
          t = try Option(t.getSuperclass).flatMap(s => Option(s.getTypeDeclaration)).orNull catch { case _: Throwable => null }
        None

      private def fieldSym(ref: CtFieldReference[?]): SymId =
        val ownerQ = Option(ref.getFieldDeclaration).flatMap(fd => Option(fd.getDeclaringType)).map(_.getQualifiedName)
          .orElse(Option(ref.getDeclaringType).map(_.getQualifiedName))
          .getOrElse("java.lang.Object")
        val ownerId = minter.external(ownerQ, simpleName(ownerQ))
        minter.external(memberKey(ownerId, ref.getSimpleName), ref.getSimpleName)

      /** Java's WILDCARD/RAW-receiver calls. When the receiver's static type leaves its arguments
        * unknown (raw use, or wildcards), Scala gives every member access a fresh CAPTURE — so a
        * value read off one such receiver never conforms to a formal of another
        * (`assetDesc.params` : `AssetLoaderParameters[?1.T]` into `asyncLoader.loadAsync`'s
        * `asyncLoader.P`). Java's own view of such a call is the ERASED one, performed unchecked.
        * Emit exactly that: cast the RECEIVER to its erased instantiation and each argument whose
        * declared formal mentions one of the receiver's type variables to that formal's erasure —
        * both use the same erasure rules, so they agree. Declarations keep their wildcards, which
        * is what preserves assignment of concrete generic values.
        *
        * Gated to calls that genuinely DEPEND on the receiver's type variables; a wildcard receiver
        * whose callee ignores them needs no cast. Returns the erased receiver type + the receiver's
        * type-variable names. */
      private def erasedReceiverView(inv: CtInvocation[?]): Option[(TypeRepr, Set[String])] =
        val ex = inv.getExecutable
        if ex.isConstructor then None
        else inv.getTarget match
          case null => None
          case _: CtSuperAccess[?] | _: CtTypeAccess[?] | _: CtThisAccess[?] => None
          case t =>
            val rt = try t.getType catch { case _: Throwable => null }
            if rt == null || rt.isPrimitive || rt.isInstanceOf[CtArrayTypeReference[?]]
               || rt.isInstanceOf[CtTypeParameterReference] || rt.isInstanceOf[CtWildcardReference] then None
            else
              val formals = try Option(rt.getTypeDeclaration).map(_.getFormalCtTypeParameters.asScala.toList).getOrElse(Nil)
                            catch { case _: Throwable => Nil }
              val actuals = rt.getActualTypeArguments.asScala.toList
              val unknown = formals.nonEmpty && (actuals.isEmpty || actuals.exists(_.isInstanceOf[CtWildcardReference]))
              val names   = formals.map(_.getSimpleName).toSet
              val depends =
                try Option(ex.getExecutableDeclaration)
                      .map(_.getParameters.asScala.toList.map(_.getType))
                      .exists(_.exists(p => p != null && mentionsTypeVar(p, names)))
                catch { case _: Throwable => false }
              if unknown && depends then
                Some(AppliedType(TypeRef(NoPrefix, typeSym(rt)), formals.map(erasureOfFormal(_, Set.empty, 2))) -> names)
              else None

      /** cast each argument whose DECLARED formal mentions a receiver type variable to that
        * formal's erasure, matching the erased receiver the call is now made through. */
      private def eraseDependentArgs(
          ex: CtExecutableReference[?], argEs: List[CtExpression[?]], args: List[Term], names: Set[String],
      ): List[Term] =
        val ps = try Option(ex.getExecutableDeclaration).map(_.getParameters.asScala.toList.map(_.getType))
                 catch { case _: Throwable => None }
        ps match
          case Some(l) if l.sizeIs == args.size && argEs.sizeIs == args.size =>
            args.zipWithIndex.map { (t, i) =>
              val f = l(i)
              if f == null || !mentionsTypeVar(f, names) then t
              else
                val et = erasedFormal(f)
                Tree.Typed(t, tt(et, argEs(i)), et, t.origin)
            }
          case _ => args

      private def invocation(inv: CtInvocation[?]): Term =
        val ex   = inv.getExecutable
        val mid  = methodSym(ex)
        val argEs = inv.getArguments.asScala.toList
        val erasedRecv = erasedReceiverView(inv)
        val args = erasedRecv match
          case Some((_, names)) => eraseDependentArgs(ex, argEs, coerceArgs(ex, argEs), names)
          case None             => coerceArgs(ex, argEs)
        val o    = originOf(inv)
        val fun: Term =
          if ex.isConstructor then
            // super()/this() delegation — target class ≠ enclosing ⇒ super (Spoon often nulls the target).
            val superCtor = inv.getTarget.isInstanceOf[CtSuperAccess[?]] ||
              Option(ex.getDeclaringType).map(_.getQualifiedName).exists(_ != minter.fullNameOf(classId))
            Tree.Select(if superCtor then superTerm(inv) else thisTerm(inv), mid, NoType, o)
          else inv.getTarget match
            case _: CtSuperAccess[?]  => Tree.Select(superTerm(inv), mid, NoType, o)
            case ta: CtTypeAccess[?]  => Tree.Select(typeTerm(ta, inv), mid, NoType, o) // static call
            // implicit (no target): a BARE reference resolves an own OR an ENCLOSING member
            // (Scala inner classes see the outer's members by simple name). Explicit `this.m`
            // stays qualified — it's used precisely to defeat param/local shadowing.
            case null                                  => Tree.Ident(mid, NoType, o)
            case ta: CtThisAccess[?] if !isOwnThis(ta) => Tree.Ident(mid, NoType, o) // outer method → bare
            case _: CtThisAccess[?]                    => Tree.Select(thisTerm(inv), mid, NoType, o)
            case t =>
              val recv = expr(t)
              // wildcard/raw receiver whose callee depends on its type vars → call through the
              // ERASED view (Java's own), so the formals stop being per-access captures.
              val recv2 = erasedRecv match
                case Some((et, _)) => Tree.Typed(recv, tt(et, t), et, originOf(t))
                case None          => recv
              Tree.Select(recv2, mid, NoType, o)
        Tree.Apply(pinTypeArgs(fun, inv, o), args, mid, ty(inv), o)

      /** Java resolves a generic call's type arguments (often by inference — e.g. `props.get("k",
        * Integer.class)` binds `T=Integer`); Scala re-infers from the EXPECTED type, which can pick
        * a different `T` (here `Int`, forcing `Class[Int]` on the arg → E007). Pin the Java-resolved
        * arguments as an explicit `m[Targs](...)` so inference matches Java and unboxing conversions
        * (`Integer` → `Int`) apply at the result. Conservative: only when every argument is a fully
        * concrete class type — wildcards / type-variables can be unresolved under noClasspath. */
      private def pinTypeArgs(fun: Term, inv: CtInvocation[?], o: Origin): Term =
        if inv.getExecutable.isConstructor then return fun
        val formals = Option(inv.getExecutable.getExecutableDeclaration).collect {
          case m: CtMethod[?] => m.getFormalCtTypeParameters.size
        }.getOrElse(0)
        val actuals = inv.getActualTypeArguments.asScala.toList
        // Restricted to boxed-primitive wrappers: that is the whole beneficial case — a `Class<T>: T`
        // call whose `T` Scala would mis-infer to the primitive (demanding `Class[Int]`) when Java
        // bound it to the wrapper (`Class[Integer]`). Pinning `T=Integer` fixes it and lets
        // `Predef.Integer2int` unbox the result. Pinning other kinds (raw generics → `Array[?]`,
        // path-dependent types) only disturbs overload resolution, so leave those to free inference.
        if formals > 0 && actuals.nonEmpty && actuals.sizeIs == formals && actuals.forall(isBoxedWrapper)
          && !inv.getArguments.asScala.exists(isPrimitiveClassLiteral)
        then Tree.TypeApply(fun, actuals.map(a => tt(tpe(a), inv)), NoType, o)
        else fun

      /** `int.class` etc. — Java types a primitive class literal as `Class<Integer>` (boxed), but we
        * emit it as `classOf[scala.Int]` (`Class[Int]`). Baseline inference binds a `Class<T>` param's
        * `T` to the primitive and matches; pinning `T` to the boxed wrapper would break that. So a
        * call carrying one of these must keep inference free — don't pin its type arguments. */
      private def isPrimitiveClassLiteral(e: CtExpression[?]): Boolean = e match
        case fr: CtFieldRead[?] if fr.getVariable.getSimpleName == "class" =>
          fr.getTarget match
            case ta: CtTypeAccess[?] => try ta.getAccessedType.isPrimitive catch { case _: Throwable => false }
            case _                   => false
        case _ => false

      private val boxedWrappers = Set(
        "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
        "java.lang.Character", "java.lang.Boolean", "java.lang.Float", "java.lang.Double")
      private def isBoxedWrapper(t: CtTypeReference[?]): Boolean =
        try boxedWrappers(t.getQualifiedName) catch { case _: Throwable => false }

      private def ctorCall(cc: CtConstructorCall[?]): Term =
        val t    = tpe(cc.getType)
        val cid  = methodSym(cc.getExecutable)
        val args = coerceArgs(cc.getExecutable, cc.getArguments.asScala.toList)
        Tree.Apply(Tree.New(tt(t, cc), t, originOf(cc)), args, cid, t, originOf(cc))

      /** SymId of a called executable — via its declaration (keyed identically to how we
        * define our own methods, so call sites and defs share one symbol) or, for
        * unresolved externals, by its reference. */
      private def methodSym(ex: CtExecutableReference[?]): SymId =
        Option(ex.getExecutableDeclaration) match
          case Some(decl) =>
            val (q, s) = declType(decl)
            val ownerId = minter.external(q, s)
            val nm      = if decl.isInstanceOf[CtConstructor[?]] then "<init>" else decl.getSimpleName
            minter.external(memberKey(ownerId, nm + erasedSig(decl)), nm)
          case None =>
            val ownerQ  = Option(ex.getDeclaringType).map(_.getQualifiedName).getOrElse("java.lang.Object")
            val ownerId = minter.external(ownerQ, simpleName(ownerQ))
            val nm      = if ex.isConstructor then "<init>" else ex.getSimpleName
            val sig     = ex.getParameters.asScala.toList.map(p => scala.util.Try(p.getQualifiedName).getOrElse("?")).mkString(",")
            minter.external(memberKey(ownerId, s"$nm($sig)"), nm)

      private def declType(decl: CtExecutable[?]): (String, String) = decl match
        case tm: CtTypeMember if tm.getDeclaringType != null => (tm.getDeclaringType.getQualifiedName, tm.getDeclaringType.getSimpleName)
        case _ =>
          val t = decl.getParent(classOf[CtType[?]])
          if t != null then (t.getQualifiedName, t.getSimpleName) else ("java.lang.Object", "Object")

      private def typeTerm(ta: CtTypeAccess[?], at: CtElement): Term =
        val q  = ta.getAccessedType.getQualifiedName
        val id = minter.external(q, simpleName(q))
        Tree.Ident(id, TypeRef(NoPrefix, id), originOf(at))

      // operators as `recv.op(args)` — the quotes.reflect shape (no dedicated node).
      private def opId(op: String): SymId = minter.external("scala.<op>#" + op, op)
      /** `i++`/`i--` on a byte/short/char narrows (`i = (short)(i + 1)`) — cast the result back. */
      private def incNarrow(opnd: CtExpression[?], res: Term): Term =
        val ot = try opnd.getType catch { case _: Throwable => null }
        if ot != null && ot.isPrimitive && Set("byte", "short", "char").contains(ot.getSimpleName)
        then Tree.Typed(res, tt(tpe(ot), opnd), tpe(ot), originOf(opnd)) else res

      private def isStringConcat(b: CtBinaryOperator[?]): Boolean =
        try b.getType.getQualifiedName == "java.lang.String" catch { case _: Throwable => false }
      private def isStringTyped(e: CtExpression[?]): Boolean =
        try e.getType.getQualifiedName == "java.lang.String" catch { case _: Throwable => false }
      /** `java.lang.String.valueOf(t)` — make a non-String operand a String for concatenation. */
      private def stringify(t: Term, el: CtElement): Term =
        val strSym = minter.external("java.lang.String", "String")
        val vSym   = minter.external("java.lang.String#valueOf", "valueOf")
        Tree.Apply(Tree.Select(Tree.Ident(strSym, TypeRef(NoPrefix, strSym), originOf(el)), vSym, NoType, originOf(el)),
          List(t), vSym, TypeRef(NoPrefix, strSym), originOf(el))

      private def binApply(op: String, l: Term, r: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(l, opId(op), NoType, l.origin), List(r), opId(op), resT, l.origin)
      private def unApply(op: String, o: Term, resT: TypeRepr): Term =
        Tree.Apply(Tree.Select(o, opId(op), NoType, o.origin), Nil, opId(op), resT, o.origin)

      private def opText(k: BinaryOperatorKind): String =
        import BinaryOperatorKind.*
        k match
          case PLUS => "+"; case MINUS => "-"; case MUL => "*"; case DIV => "/"; case MOD => "%"
          case AND => "&&"; case OR => "||"; case BITAND => "&"; case BITOR => "|"; case BITXOR => "^"
          case EQ => "=="; case NE => "!="; case LT => "<"; case LE => "<="; case GT => ">"; case GE => ">="
          case SL => "<<"; case SR => ">>"; case USR => ">>>"
          case other => "?" + other
