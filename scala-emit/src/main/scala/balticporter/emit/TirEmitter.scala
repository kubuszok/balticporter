package balticporter.emit

import balticporter.tir.*

/** Emission backend: the TRANSFORMED typed TIR → Scala 3 source (RECOMPILER.md step 3,
  * "source pretty-printing"). Because every node carries its resolved `TypeRepr` and every
  * reference a `SymId`, the emitter inserts the right form by construction — it looks names
  * up in the `Program`'s symbol table rather than re-deriving them, so the inference/diamond
  * bugs of the string-printer era cannot occur.
  *
  * First cut: covers the whole node set. A few Java-only control forms have no direct Scala
  * surface and are lowered approximately (marked inline) — `break`/`continue` (need
  * `boundary`), do-while (dropped in Scala 3), and inc/dec used as a value; those are the
  * emitter's known refinement points, not populator gaps.
  */
final class TirEmitter(source: Program):
  // normalize away Java member-name clashes (a field `x` alongside a method `x()`) before
  // rendering — Scala forbids them; renaming the field symbol propagates to every reference.
  private val prepared =
    TirEmitter.funnelParamRenames(TirEmitter.resolveFieldShadowing(TirEmitter.resolveMemberClashes(source)))
  /** which Java constructor becomes each class's Scala primary, and which `super(args)` can be
    * replayed as statements — whole-program decisions. */
  private val plans = CtorFunnel.Plans(prepared)
  // a replayed parent constructor's statements execute one level down, so the private members
  // they reach must be visible there. Widening only rewrites symbol FLAGS — the trees `plans`
  // was computed over are untouched, so it still applies.
  private val program = TirEmitter.widen(prepared, plans.widenedMembers)

  def emit: String = program.units.map(emitUnit).mkString("\n\n")

  /** types declared in the unit currently being rendered (in scope by simple name). */
  private var currentDeclared: Set[SymId] = Set.empty
  /** the class whose body is being rendered — a constructor's funnel plan is looked up by it. */
  private var currentClass: Option[Tree.ClassDef] = None

  def emitUnit(cd: Tree.ClassDef): String =
    currentDeclared = declaredTypes(cd)
    val body = classDef(cd, 0)
    val full = sym(cd.symbol).fullName
    val pkg  = if full.contains('.') then s"package ${full.substring(0, full.lastIndexOf('.'))}\n\n" else ""
    pkg + body

  /** every type symbol that appears as a parent (extends/mixin) anywhere in the program — an
    * all-static class in this set must stay a `class`, since an `object` can't be extended. */
  private lazy val extendedTypes: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => None
    def scan(cd: Tree.ClassDef): Unit =
      cd.parents.foreach {
        case tt: TypeTree => headSym(tt.tpe).foreach(acc += _)
        case term: Term   => headSym(term.tpe).foreach(acc += _)
      }
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan)
    acc.toSet

  private def declaredTypes(cd: Tree.ClassDef): Set[SymId] =
    val acc = collection.mutable.Set[SymId](cd.symbol)
    cd.body.foreach { case c: Tree.ClassDef => acc ++= declaredTypes(c); case _ => () }
    acc.toSet

  /** head symbols of a class's parent types (extends + mixins). */
  private def parentSymsOf(cd: Tree.ClassDef): List[SymId] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _ => None
    cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe) }

  /** our-own types that have at least one `static` member (so a companion `object` holds it). */
  private lazy val typesWithStatics: Set[SymId] =
    val acc = collection.mutable.Set[SymId]()
    def scan(cd: Tree.ClassDef): Unit =
      if cd.body.exists { case d: Definition => sym(d.symbol).flags.isStatic; case _ => false } then acc += cd.symbol
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan)
    acc.toSet

  /** each type → the names of the `static` members it DECLARES itself. */
  private lazy val ownStaticsBySym: Map[SymId, Set[String]] =
    val m = collection.mutable.Map[SymId, Set[String]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = cd.body.collect { case d: Definition if sym(d.symbol).flags.isStatic => esc(sym(d.symbol).name) }.toSet
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan); m.toMap

  /** every static name a companion re-export of `s` delivers, mapped to the type that DECLARES it —
    * `s`'s own statics, then its ancestors' (nearest declaration wins, as in Java). The owner is
    * what makes two exports comparable: the same name from the same declaring type is the same
    * constant arriving twice (a diamond — `GL30Interceptor extends GLInterceptor with GL30`, where
    * `GLInterceptor` implements `GL20` and `GL30` extends it), which Scala rejects as a duplicate
    * definition; the same name from DIFFERENT types is a real redeclaration and must not be merged. */
  private def staticOwnersOf(s: SymId, seen: Set[SymId] = Set.empty): Map[String, SymId] =
    if seen(s) then Map.empty
    else
      val inherited = parentsBySym.getOrElse(s, Nil)
        .foldLeft(Map.empty[String, SymId])((acc, p) => staticOwnersOf(p, seen + s) ++ acc)
      inherited ++ ownStaticsBySym.getOrElse(s, Set.empty).map(_ -> s).toMap

  /** each type → its parent symbols (whole program). */
  private lazy val parentsBySym: Map[SymId, List[SymId]] =
    val m = collection.mutable.Map[SymId, List[SymId]]()
    def scan(cd: Tree.ClassDef): Unit =
      m(cd.symbol) = parentSymsOf(cd); cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    program.units.foreach(scan); m.toMap

  /** does this type OR any ancestor have static members? (so its companion carries or re-exports
    * them — the export chain must pass THROUGH intermediates that add no statics of their own). */
  private def staticsReachable(s: SymId, seen: Set[SymId] = Set.empty): Boolean =
    !seen(s) && (typesWithStatics(s) || parentsBySym.getOrElse(s, Nil).exists(p => staticsReachable(p, seen + s)))

  /** every strict ancestor of `s`. */
  private def ancestorsOf(s: SymId, seen: Set[SymId] = Set.empty): Set[SymId] =
    parentsBySym.getOrElse(s, Nil).filterNot(seen).foldLeft(Set.empty[SymId]) { (acc, p) =>
      acc + p ++ ancestorsOf(p, seen + s + p)
    }

  // ---- names ----
  private def sym(id: SymId): Symbol = program.symbolOf(id).getOrElse(Symbol(id, "?", "?", Flags(), SymId.None, TypeRepr.NoType))
  private def local(id: SymId): String = esc(sym(id).name)

  private val keywords = Set(
    "type", "object", "val", "var", "def", "class", "trait", "enum", "given", "match", "case",
    "if", "else", "while", "do", "for", "yield", "then", "with", "extends", "new", "this", "super",
    "null", "true", "false", "import", "package", "override", "final", "abstract", "sealed", "private",
    "protected", "implicit", "lazy", "return", "throw", "try", "catch", "finally", "forSome", "using",
    "export", "inline", "opaque", "transparent", "derives", "extension", "macro", "end", "as", "wait",
  )
  /** backtick an identifier that collides with a Scala keyword. */
  private def esc(name: String): String = if keywords(name) then s"`$name`" else name
  /** a TYPE symbol's rendered name. FULLY QUALIFIED by default — for the structural Java→Scala
    * phase we emit fully-qualified references and generate NO imports, which deletes the entire
    * import-decision bug class (import-vs-projection, shadowing, static-receiver qualification):
    * a reference is now a context-free function of the symbol's owner chain. Only two things
    * stay unqualified: type params, and a type declared in THIS unit (in scope by simple name).
    * Human-readable imports are a separate, optional beautification backend, not a correctness
    * prerequisite. (A later refinement handles givens/extensions, which FQN genuinely can't name.) */
  private def typeSym(id: SymId): String =
    val s = sym(id)
    if tparamSubst.contains(id) then tpe(tparamSubst(id)) // ctor type param → its bound
    else if s.flags.isParam then esc(s.name)
    // a Java `static` nested class is lowered into the enclosing type's companion `object`, so it
    // is named through the value path `Outer.Inner` — NOT by simple name (companion members aren't
    // in the class's scope) and NOT `Outer#Inner` (a type projection can't reach a companion member).
    else if s.flags.isStatic && s.fullName.contains('$') then nestedPath(id)
    // a Java INNER (non-static) class is a PATH-dependent type in Scala: named by simple name inside
    // the enclosing class it means `this.Inner`, so the same Java type reached through two different
    // instances (`pa.Channel` vs `ParallelArray#Channel` from another file) never unifies, and a
    // method bounded `<T extends Channel>` cannot accept an initializer written against the outer
    // view. Name it by PROJECTION everywhere instead — one type for all instances. `extends` and
    // `new` need an instantiable/stable name, so those two positions opt out (see `namedInner`).
    else if program.definitionOf(id).isDefined && currentDeclared(id) then
      if namedInner || !isInnerClass(id) then esc(s.name) // declared here — in scope
      else nestedPath(id)
    else if program.symbolOf(s.owner).exists(_.flags.isModule) then s"${typeValue(s.owner)}.${esc(s.name)}" // object's type member → path-dependent `O.T`
    // An inner class of an ANCESTOR is an INHERITED member type, in scope by its simple name
    // anywhere inside the subclass — `class TextArea extends TextField` sees
    // `TextFieldClickListener` exactly as Java did. The projection is not merely verbose here, it
    // is illegal: `TextField#TextFieldClickListener` needs `TextField` to be an immutable path.
    else if inheritedNested(s.owner) then esc(s.name)
    else nestedPath(id)                                             // non-static inner class elsewhere → `Outer#Inner`

  /** is `owner` an ancestor of some class we are currently rendering inside? */
  private def inheritedNested(owner: SymId): Boolean =
    owner != SymId.None && classStack.exists(c => c != owner && ancestorsOf(c).contains(owner))

  /** the path to a NESTED type, choosing a separator PER LEVEL: `.` where that level is a Java
    * `static` nested class (lowered into the enclosing companion `object`, so reachable only through
    * the value path) and `#` where it is a genuine inner class (a type projection). A blanket
    * `fullName.replace('$', '#')` gets a MIXED chain wrong — `ModelInfluencer.Random` is static and
    * holds the inner `ModelInstancePool`, so the type is `ModelInfluencer.Random#ModelInstancePool`
    * while `ModelInfluencer#Random#ModelInstancePool` names nothing at all. Falls back to the
    * blanket form whenever an owner symbol is unknown, so this can only ever add precision. */
  private def nestedPath(id: SymId): String =
    def go(x: SymId): Option[String] =
      val sx = sym(x)
      if !sx.fullName.contains('$') then Some(sx.fullName)
      else if sx.owner == SymId.None || program.symbolOf(sx.owner).isEmpty then None
      else go(sx.owner).map(p => p + (if sx.flags.isStatic then "." else "#") + esc(sx.name))
    // The fallback fires exactly when an owner is UNKNOWN, which for a type we do not define means
    // an external/JDK one. Name those with `.`: a Java nested type is reached as `Outer.Inner` in
    // Scala, and a `#` projection is not even available — it needs the prefix to be an immutable
    // path, which a bare external class name is not (`java.nio.channels.FileChannel#MapMode`).
    go(id).getOrElse:
      val sep = if program.definitionOf(id).isEmpty then '.' else '#'
      sym(id).fullName.replace('$', sep)

  /** a NON-static nested class of one of our own NON-GENERIC classes (not of a companion `object`).
    * A generic enclosing class is excluded: `Octree#OctreeNode` is not a legal projection — the
    * prefix would need type arguments, which the reference does not carry. */
  private def isInnerClass(id: SymId): Boolean =
    val s = sym(id)
    !s.flags.isStatic && s.owner != SymId.None && s.fullName.contains('$') &&
      !program.symbolOf(s.owner).exists(_.flags.isModule) &&
      program.definitionOf(s.owner).exists { case c: Tree.ClassDef => c.tparams.isEmpty; case _ => false }

  /** inside an `extends` clause or a `new`, where a type projection is not legal — render inner
    * classes by their simple (in-scope) name there. */
  private var namedInner = false
  private def byName[A](f: => String): String =
    val prev = namedInner; namedInner = true
    try f finally namedInner = prev

  private def ind(n: Int): String = "  " * n

  // ---- definitions ----
  /** the classes currently being rendered, outermost first. Lets a `Tree.This` naming an ENCLOSING
    * class render Java's qualified `Outer.this` rather than a bare `this` (which names the inner one). */
  private val classStack = collection.mutable.ArrayDeque[SymId]()

  private def classDef(cd: Tree.ClassDef, i: Int): String =
    val outer = currentClass
    currentClass = Some(cd)
    classStack.append(cd.symbol)
    try classDef0(cd, i) finally { classStack.removeLast(); currentClass = outer }

  /** `this` in Scala always names the INNERMOST class, where Java's `Outer.this` names an enclosing
    * one. Qualify by simple name when the symbol is an enclosing class actually being rendered
    * around this point; anything else (an inherited/unknown owner) keeps the bare `this`.
    *
    * A SUPERTYPE is never qualified even when it also encloses: libGDX nests subclasses inside their
    * own base (`DynamicsModifier.FaceDirection extends DynamicsModifier`), and constructor replay
    * moves the base's `this` statements into the subclass body — there the bare `this` is exactly
    * right, while `DynamicsModifier.this` would name the companion object. */
  private def thisRef(s: SymId): String =
    val inner = classStack.lastOption
    if inner.contains(s) || !classStack.contains(s) || inner.exists(inheritsFrom(_, s)) then "this"
    else s"${esc(sym(s).name)}.this"

  /** is `child` `anc`, or a (transitive) subtype of it, among our own definitions? */
  private def inheritsFrom(child: SymId, anc: SymId): Boolean =
    val seen = collection.mutable.Set[SymId]()
    def go(c: SymId): Boolean =
      c == anc || (seen.add(c) && program.definitionOf(c).collect { case cd: Tree.ClassDef =>
        parentSymsOf(cd).exists(go)
      }.getOrElse(false))
    go(child)

  private def classDef0(cd: Tree.ClassDef, i: Int): String =
    if sym(cd.symbol).flags.isEnum then return enumDef(cd, i)
    val s  = sym(cd.symbol)
    val kw =
      if s.flags.isModule then "object"
      else if s.flags.isTrait then "trait"
      else "class"
    val tps     = if cd.tparams.isEmpty then "" else "[" + cd.tparams.map(typeParam).mkString(", ") + "]"
    // lower Java constructors: `CtorFunnel` nominates one to become Scala's PRIMARY. Its body is
    // inlined (those statements run at construction), its `super(args)` moves into the `extends`
    // clause (which also fixes parents that need constructor arguments), and its PARAMETERS become
    // the class's parameters. Every other constructor stays a `def this(...)` delegating to it.
    val plan    = if s.flags.isModule then CtorFunnel.Plan.none else plans(cd)
    val (loweredBody, superArgs) = (lowerCtors(cd.body, plan), plan.superArgs)
    val pparams = plan.primaryParams
    val prim    = if pparams.isEmpty then "" else s"(${pparams.map(param).mkString(", ")})"
    val superTpe = cd.parents.headOption.map { case tt: TypeTree => tt.tpe; case t: Term => t.tpe }
    val parents = cd.parents.map(parent).filter(_.nonEmpty) match
      case Nil                          => Nil
      case h :: t if superArgs.nonEmpty =>
        val as = superArgs.zipWithIndex.map((a, n) => superArg(superTpe.getOrElse(TypeRepr.NoType), a, n, i))
        s"$h(${as.mkString(", ")})" :: t
      case all                          => all
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    // an all-static utility class (no instance state, no supertype) is just an `object` — so its
    // static members and nested types live together and see each other by simple name.
    val hasInstanceState = cd.body.exists {
      case d: Tree.DefDef => sym(d.symbol).name != "<init>" && !sym(d.symbol).flags.isStatic
      case v: Tree.ValDef => !sym(v.symbol).flags.isStatic
      case _              => false
    }
    // an all-static class can only collapse to an `object` if nobody EXTENDS it (you can't extend
    // an object) — otherwise it stays a `class` with its statics in a companion object.
    if kw == "class" && parents.isEmpty && cd.body.nonEmpty && !hasInstanceState && pparams.isEmpty && !extendedTypes(cd.symbol) then
      val members = cd.body.filterNot { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
      val ob = orderBody(members, pparams.nonEmpty).map(stat(_, i + 1)).filter(_.nonEmpty).mkString("\n")
      return s"${ind(i)}object ${esc(s.name)}$tps {\n$ob\n${ind(i)}}"
    // Java statics have no instance home in Scala — they move to the companion object.
    val (statics, instance) = if s.flags.isModule then (Nil, loweredBody) else loweredBody.partition(isStatic)
    val self    = cd.selfType.map(st => s"${ind(i + 1)}self: ${tpe(st.tpe)} =>\n").getOrElse("")
    val body    = joinStats(orderBody(instance, pparams.nonEmpty).map(stat(_, i + 1)).filter(_.nonEmpty))
    val open    = if body.isEmpty && self.isEmpty then "" else s" {\n$self$body\n${ind(i)}}"
    val abs     = if kw == "class" && s.flags.isAbstract then "abstract " else ""
    // Scala (unlike Java) forbids a NON-private member from referring to a `private` type in its
    // signature — a public `Values extends MapIterator` / field `pool: ModelInstancePool` where the
    // referent is private is an error. Java nested classes leak this way constantly; drop the class's
    // `private` (visibility-widening is always compile-safe) so those references type-check.
    // A Java `@interface` is an ANNOTATION TYPE. Emitted as an ordinary interface it becomes a
    // trait, and then nothing can be annotated with it — 161 errors' worth of `@Null` in this
    // corpus alone. Scala's equivalent is a class extending `StaticAnnotation`.
    val cls     =
      if s.flags.isAnnotation then
        s"${annots(s, i)}${ind(i)}class ${esc(s.name)}$tps$prim extends scala.annotation.StaticAnnotation"
      else s"${annots(s, i)}${ind(i)}${mods(s.flags.copy(isPrivate = false))}$abs$kw ${esc(s.name)}$tps$prim$ext$open"
    // Java interface/parent CONSTANTS are `static`, so they live in the parent's companion object
    // — which Scala does NOT inherit. Re-export each static-bearing parent's companion so an
    // inherited constant accessed via a subclass (`GL30.GL_LUMINANCE`, declared in `GL20`) resolves.
    // exclude the class's OWN static names from the re-export (a subtype may redeclare a parent
    // constant — OpenGL's GL31 vs GL30 — which would otherwise be a duplicate/conflicting export).
    val ownStaticNames = statics.collect { case d: Definition => esc(sym(d.symbol).name) }.distinct
    // Two exports must not both deliver the same name. `GL20Interceptor extends GLInterceptor with
    // GL20` and `GLInterceptor` itself implements `GL20`, so `GLInterceptor`'s companion ALREADY
    // re-exports `GL20`'s constants by this rule — a second `export GL20.*` is a duplicate
    // definition, not extra reach. Drop a parent another exported parent wholly subsumes, and for
    // the DIAMOND that remains (`GLInterceptor` and `GL30` meeting at `GL20`) exclude, from each
    // later export, every name an earlier one already delivered FROM THE SAME DECLARING TYPE. The
    // same-owner test is what keeps this safe: a genuine redeclaration (`GL31` shadowing a `GL30`
    // constant) has a different owner, so it is never silently merged away.
    val exported       = parentSymsOf(cd).filter(p => staticsReachable(p))
    val kept           = exported.filterNot(p => exported.exists(q => q != p && ancestorsOf(q).contains(p)))
    val delivered      = kept.map(staticOwnersOf(_))
    val extraExcl      = Array.fill(kept.size)(Set.empty[String])
    delivered.flatMap(_.keys).distinct.foreach { n =>
      val at = delivered.indices.filter(j => delivered(j).contains(n)).toList
      if at.sizeIs > 1 then
        val owners = at.map(delivered(_)(n)).distinct
        // Same owner everywhere ⇒ the SAME constant arriving twice; keep the first export and drop
        // the rest. Different owners ⇒ a real redeclaration, and the one Java resolves to is the
        // most specific — the owner that descends from all the others. Incomparable owners are
        // ambiguous in Java too, so keep the first and let the redeclaration be the loser rather
        // than guess.
        val winner = at.find(j => owners.forall(o => o == delivered(j)(n) || ancestorsOf(delivered(j)(n)).contains(o)))
          .getOrElse(at.head)
        at.filter(_ != winner).foreach(j => extraExcl(j) = extraExcl(j) + n)
    }
    val parentExports  = kept.zipWithIndex.map { (p, j) =>
      val excluded = (ownStaticNames.toSet ++ extraExcl(j)).toList.sorted
      val sel      = if excluded.isEmpty then "*" else s"{${excluded.map(_ + " => _").mkString(", ")}, *}"
      s"${ind(i + 1)}export ${typeValue(p)}.$sel"
    }
    if statics.isEmpty && parentExports.isEmpty then cls
    else
      val sb = (parentExports ++ orderBody(statics).map(stat(_, i + 1)).filter(_.nonEmpty)).mkString("\n")
      s"$cls\n${ind(i)}object ${esc(s.name)} {\n$sb\n${ind(i)}}"

  /** Java enum → `sealed abstract class Name <parents-minus-Enum> { members }` plus a
    * companion `object` holding each constant as a `case object` and a `values` array. */
  private def enumDef(cd: Tree.ClassDef, i: Int): String =
    val s       = sym(cd.symbol)
    val name    = esc(s.name)
    val parents = cd.parents.map(parent).filter(p => p.nonEmpty && !p.startsWith("java.lang.Enum"))
    val ext     = if parents.isEmpty then "" else " extends " + parents.mkString(" with ")
    val (statics, instance0) = cd.body.partition(isStatic)
    // A Java enum constructor's PARAMS become the sealed class's primary constructor params (as `var`
    // fields), so `case object Nearest extends TextureFilter(GL_NEAREST)` has somewhere to pass its
    // arg. Drop the constructor itself and any field that a param supersedes (same name).
    val ctorParams = instance0.collectFirst { case d: Tree.DefDef if sym(d.symbol).name == "<init>" => d.paramss.flatten }.getOrElse(Nil)
    val paramNames = ctorParams.map(v => sym(v.symbol).name).toSet
    val instance   = instance0.filterNot {
      case d: Tree.DefDef => sym(d.symbol).name == "<init>"
      case v: Tree.ValDef => paramNames(sym(v.symbol).name)
      case _              => false
    }
    val eprimary = if ctorParams.isEmpty then "" else s"(${ctorParams.map(v => s"var ${esc(sym(v.symbol).name)}: ${tpe(v.tpt.tpe)}").mkString(", ")})"
    // Java's final `Enum.name()` — a `case object`'s `toString` IS its declared name (= the Java
    // constant name), so `name()` returns it. Skip if the enum already declares a `name` member.
    val hasName = instance.exists { case d: Definition => sym(d.symbol).name == "name"; case _ => false }
    val nameM   = if hasName then Nil else List(s"${ind(i + 1)}def name(): java.lang.String = this.toString()")
    val members = orderBody(instance).map(stat(_, i + 1)).filter(_.nonEmpty) ++ nameM
    val cbody   = members.mkString("\n")
    val cls     = s"${ind(i)}sealed abstract class $name$eprimary$ext" + (if cbody.isEmpty then "" else s" {\n$cbody\n${ind(i)}}")
    val cases = cd.enumCases.map { ec =>
      val cn   = esc(sym(ec.symbol).name)
      val args = if ec.ctorArgs.isEmpty then "" else s"(${ec.ctorArgs.map(term(_, i + 1)).mkString(", ")})"
      val body = if ec.body.isEmpty then "" else s" {\n${ec.body.map(stat(_, i + 2)).mkString("\n")}\n${ind(i + 1)}}"
      s"${ind(i + 1)}case object $cn extends $name$args$body"
    }
    // `def` (not `val`) so Java's `E.values()` call site type-checks; also a no-paren read works.
    val values = s"${ind(i + 1)}def values(): scala.Array[$name] = scala.Array(${cd.enumCases.map(ec => esc(sym(ec.symbol).name)).mkString(", ")})"
    // Java's `Enum.valueOf(String)` — resolve a constant by name (throws like the JDK on no match).
    val vArms  = cd.enumCases.map(ec => esc(sym(ec.symbol).name)).map(n => s"""${ind(i + 2)}case "$n" => $n""").mkString("\n")
    val valueOf = s"${ind(i + 1)}def valueOf(name: java.lang.String): $name = name match {\n$vArms\n${ind(i + 2)}case _ => throw new java.lang.IllegalArgumentException(name)\n${ind(i + 1)}}"
    val objBody = (cases :+ values :+ valueOf) ++ statics.map(stat(_, i + 1)).filter(_.nonEmpty)
    s"$cls\n${ind(i)}object $name {\n${objBody.mkString("\n")}\n${ind(i)}}"

  // a Java `static` nested class has no instance home in Scala → it moves to the companion
  // `object` alongside static vals/defs. A non-static inner class stays in the class body.
  /** Replace the constructor `CtorFunnel` promoted to Scala's PRIMARY by its own body statements
    * — they run at construction, which is where a Scala class body runs them too. Its `super(args)`
    * has already been lifted into the `extends` clause and its parameters into the class's
    * parameter list; every other constructor stays a secondary `def this(...)`. */
  private def lowerCtors(body: List[Statement], plan: CtorFunnel.Plan): List[Statement] =
    plan.primary match
      case None    => body
      case Some(c) => body.flatMap { case d: Tree.DefDef if d.symbol == c.symbol => plan.primaryBody; case s => List(s) }

  /** a Java `static { … }` / instance `{ … }` initializer block, carried as a synthetic member. */
  private def isInitBlock(d: Tree.DefDef): Boolean =
    val n = sym(d.symbol).name
    n == "<clinit>" || n == "<initblock>"

  private def isStatic(s: Statement): Boolean = s match
    case d: Tree.ClassDef => sym(d.symbol).flags.isStatic
    case d: Definition    => sym(d.symbol).flags.isStatic
    case _                => false

  /** Scala secondary constructors must delegate to a PRECEDING constructor, so order fields first,
    * then constructors in DELEGATION-TOPOLOGICAL order (each ctor's `this(args)` target emitted
    * before it), then everything else. Arity is not a reliable proxy — a 3-arg convenience ctor can
    * delegate to a 1-arg one (`Texture(pixmap,fmt,mip)` → `Texture(data)`), so we follow the actual
    * `this(...)` edges, keyed by the target ctor's own symbol. */
  private def orderBody(body: List[Statement], paramfulPrimary: Boolean = false): List[Statement] =
    def isCtor(s: Statement) = s match { case d: Tree.DefDef => sym(d.symbol).name == "<init>"; case _ => false }
    // the peer ctor this one delegates to via a leading `this(args)` (NOT super, NOT the no-arg
    // primary) — its symbol identifies the exact target constructor.
    def delegateTarget(d: Tree.DefDef): Option[SymId] = d.rhs match
      case Some(Tree.Block((Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) :: _, _, _, _))
          if sym(m).name == "<init>" && args.nonEmpty && !r.isInstanceOf[Tree.Super] => Some(m)
      case _ => None
    // a no-arg constructor whose body is only super/this delegation is degenerate — Scala's
    // implicit primary constructor already is no-arg, and `def this() = this()` self-recurses.
    // Only when the primary IS no-arg: against a PARAMFUL primary a `C() { this(16); }` is the
    // only thing that makes `new C()` legal at all, so it must be emitted.
    def degenerate(d: Tree.DefDef): Boolean =
      !paramfulPrimary && d.paramss.flatten.isEmpty && (d.rhs match
        case Some(Tree.Block(stats, _, _, _)) =>
          stats.forall { case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) => sym(m).name == "<init>"; case _ => false }
        case _ => true)
    val ctorList = body.collect { case d: Tree.DefDef if isCtor(d) && !degenerate(d) => d }
    val bySym    = ctorList.map(d => d.symbol -> d).toMap
    // DFS post-order = topological order (a target is appended before its caller); `inProgress`
    // breaks any (illegal) cycle so a malformed chain can't loop forever.
    val ordered    = collection.mutable.ListBuffer[Tree.DefDef]()
    val visited    = collection.mutable.Set[SymId]()
    val inProgress = collection.mutable.Set[SymId]()
    def visit(d: Tree.DefDef): Unit =
      if !visited(d.symbol) && !inProgress(d.symbol) then
        inProgress += d.symbol
        delegateTarget(d).flatMap(bySym.get).foreach(visit)
        inProgress -= d.symbol
        visited += d.symbol
        ordered += d
    ctorList.foreach(visit)
    val fields = body.collect { case v: Tree.ValDef => v }
    val rest   = body.filterNot(s => isCtor(s) || s.isInstanceOf[Tree.ValDef])
    fields ++ ordered.toList ++ rest

  private def typeParam(td: Tree.TypeDef): String =
    val name = esc(sym(td.symbol).name)
    td.rhs.tpe match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => name
      case TypeRepr.TypeBounds(lo, hi) =>
        val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
        val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
        s"$name$l$h"
      case other => s"$name <: ${tpe(other)}"

  /** The parent's promoted-constructor formal at position `n`, with the parent's OWN type
    * parameters replaced by whatever the `extends` clause supplies for them — `MapIterator<V>`'s
    * `IntMap<V>` seen from `Entries[V] extends MapIterator[V]` is `IntMap[V]`, `V` now being
    * `Entries`'. `None` when the parent is external, has no promoted constructor, or is applied at
    * a different arity than it declares. */
  private def superFormal(parent: TypeRepr, n: Int): Option[TypeRepr] =
    val actuals = parent match
      case TypeRepr.AppliedType(_, as) => as
      case _                           => Nil
    for
      tycon <- headSymOf(parent)
      pcd   <- program.definitionOf(tycon).collect { case c: Tree.ClassDef => c }
      if pcd.tparams.sizeIs == actuals.size
      p     <- plans(pcd).primaryParams.lift(n)
    yield substTp(p.tpt.tpe, pcd.tparams.map(_.symbol).zip(actuals).toMap)

  /** An argument lifted into the `extends` clause.
    *
    * The argument kept the wildcard fill its DECLARATION was rendered with (`map$p: IntMap[?]`),
    * but the parent's constructor asks for that same Java raw type read in another position, where
    * a wildcard could not survive. Both are the same type to Java, which passed it unchecked; this
    * writes the conversion down.
    *
    * WHICH type to name is decided by the parent, not by the argument alone. Where the parent's
    * own wildcards were eliminated to reach the `extends` clause ([[deWildcarded]]) — `Keys extends
    * MapIterator` becoming `MapIterator[AnyRef]` — the same elimination is right. But a parent
    * applied to NAMED arguments never lost anything: `Entries[V] extends MapIterator[V]` asks for
    * `IntMap[V]`, and eliminating the argument's wildcard independently produced
    * `IntMap[Object]` — the right shape at the wrong type, which scalac rejects. So take the
    * parent's formal under its actual instantiation whenever it is available, and fall back to
    * the isolated elimination only for a parent we cannot see into. */
  private def superArg(parent: TypeRepr, a: Term, n: Int, i: Int): String =
    if !hasWildcardArg(a.tpe) then term(a, i)
    else
      val target = superFormal(parent, n).filterNot(hasWildcardArg).map(tpe)
        .getOrElse(deWildcarded(a.tpe, named = false))
      s"${term(a, i)}.asInstanceOf[$target]"

  private def parent(p: Term | TypeTree): String = p match
    case tt: TypeTree  => parentTpe(tt.tpe)
    case t: Term  => parentTpe(t.tpe)

  /** a parent type in an `extends` clause: a wildcard type argument (`Foo[?, ?]`, from a raw
    * generic supertype) is ILLEGAL here — replace each `?` with its upper bound (or `AnyRef`). */
  /** Only the HEAD is a `namedInner` position. A type ARGUMENT of the parent is an ordinary type
    * position: the simple name of an inner class is NOT in scope in an `extends` clause
    * (`ParticleEffectPool extends Pool[PooledEffect]` → `Not found: type PooledEffect`), while the
    * projection that `typeSym` would otherwise give is both legal and correct there. */
  private def parentTpe(t: TypeRepr): String = deWildcarded(t, named = true)

  /** the head symbol of a (possibly applied) type. */
  private def headSymOf(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)      => Some(s)
    case TypeRepr.AppliedType(tc, _) => headSymOf(tc)
    case _                           => None

  /** a type's own parameters paired with their declared upper bounds (`NoType` when unbounded). */
  private def declBounds(tycon: SymId): List[(SymId, TypeRepr)] =
    program.definitionOf(tycon).collect { case c: Tree.ClassDef =>
      c.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _                                                   => TypeRepr.NoType))
    }.getOrElse(Nil)

  /** does this type mention the given symbol anywhere — the F-bound test (`N <: Node[N,V,A]`)? */
  private def mentionsSym(t: TypeRepr, s: SymId): Boolean = t match
    case TypeRepr.TypeRef(_, x)             => x == s
    case TypeRepr.AppliedType(tc, as)       => mentionsSym(tc, s) || as.exists(mentionsSym(_, s))
    case TypeRepr.TypeBounds(lo, hi)        => mentionsSym(lo, s) || mentionsSym(hi, s)
    case TypeRepr.AndType(l, r)             => mentionsSym(l, s) || mentionsSym(r, s)
    case TypeRepr.OrType(l, r)              => mentionsSym(l, s) || mentionsSym(r, s)
    case _                                  => false

  private def substTp(t: TypeRepr, m: Map[SymId, TypeRepr]): TypeRepr = t match
    case TypeRepr.TypeRef(_, s) if m.contains(s) => m(s)
    case TypeRepr.AppliedType(tc, as)            => TypeRepr.AppliedType(substTp(tc, m), as.map(substTp(_, m)))
    case other                                   => other

  /** Render a type with every WILDCARD argument eliminated — illegal in an `extends` clause, and
    * illegal as the target of a cast.
    *
    * A wildcard becomes its own written bound, else the type PARAMETER's declared upper bound, else
    * `AnyRef`. Consulting the declaration is what the plain `AnyRef` fill got wrong: it produced
    * `extends ParticleControllerRenderer[AnyRef, AnyRef]` for a class whose parameters are
    * `D <: ParticleControllerRenderData, T <: ParticleBatch[D]`, which fails its own bounds.
    * Arguments resolve LEFT TO RIGHT with the earlier choices substituted in, because a later bound
    * may name an earlier parameter — as `T <: ParticleBatch[D]` does.
    *
    * `named` selects the head's rendering. It is a Boolean rather than the `byName` combinator
    * passed as a function because `byName` sets a mutable flag AROUND evaluating its by-name
    * argument; handing it to a strict `String => String` parameter evaluates the head first and
    * silently loses the flag (which turned `extends Channel` into `extends ParallelArray#Channel`). */
  private def deWildcarded(t: TypeRepr, named: Boolean): String =
    def head(f: => String): String = if named then byName(f) else f
    t match
      case TypeRepr.AppliedType(tc, args) =>
        val bounds = headSymOf(tc).map(declBounds).getOrElse(Nil)
        val (as, _) = args.zipWithIndex.foldLeft((List.empty[String], Map.empty[SymId, TypeRepr])) {
          case ((acc, m), (a, i)) =>
            // An F-BOUNDED parameter (`N extends Node<N,V,A>`) cannot be eliminated at all: no
            // finite type satisfies `N <: Node[N,V,A]` except a real subclass. `Node[Object, …]`
            // fails the bound, and so does every unrolling — `Node[Node[Object,…], …]` needs its
            // argument to be the very type being defined, and `Node` is invariant. Java has the
            // same bound and simply does not check it at an erased use; Scala does. The one type
            // that works is the WILDCARD, which asserts only that SOME type satisfies the bound —
            // verified against scalac before writing this. So an F-bounded slot stays `?`.
            val fBounded = bounds.lift(i).exists((p, hi) => hi != TypeRepr.NoType && mentionsSym(hi, p))
            val chosen: Option[TypeRepr] = a match
              case _: TypeRepr.TypeBounds if fBounded                  => scala.None
              case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => Some(substTp(hi, m))
              case _: TypeRepr.TypeBounds =>
                bounds.lift(i).map(_._2).filter(_ != TypeRepr.NoType).map(substTp(_, m))
              case other => Some(other)
            val rendered =
              if chosen.isEmpty && fBounded then "?" else chosen.map(tpe).getOrElse("scala.AnyRef")
            val m2 = (bounds.lift(i), chosen) match
              case (Some((p, _)), Some(c)) => m + (p -> c)
              case _                       => m
            (acc :+ rendered, m2)
        }
        s"${head(tpe(tc))}[${as.mkString(", ")}]"
      case _ => head(tpe(t))

  /** does this type carry a wildcard argument anywhere? */
  private def hasWildcardArg(t: TypeRepr): Boolean = t match
    case _: TypeRepr.TypeBounds      => true
    case TypeRepr.AppliedType(tc, a) => hasWildcardArg(tc) || a.exists(hasWildcardArg)
    case _                           => false

  private def stat(s: Statement, i: Int): String = s match
    case c: Tree.ClassDef => classDef(c, i)
    // a Java initializer block is carried as a synthetic member; emit its BODY inline rather than
    // a `def`, since a block in a class/object body runs at initialisation — where Java runs it
    // too — and `orderBody` has already placed it after the field declarations it fills.
    // `locally` is REQUIRED, not decoration: a bare `{ … }` on the line after a field initialised
    // with `new T(…)` is parsed as that constructor's anonymous-class body
    // (`new Array[Float](n) { … }`), which fails as "anonymous class cannot extend final class".
    case d: Tree.DefDef if isInitBlock(d) => d.rhs.map(r => s"${ind(i)}locally ${term(r, i)}").getOrElse("")
    case d: Tree.DefDef   => defDef(d, i)
    case v: Tree.ValDef   => valDef(v, i)
    case t: Tree.TypeDef  => s"${ind(i)}${if sym(t.symbol).flags.isOpaque then "opaque " else ""}type ${esc(sym(t.symbol).name)} = ${tpe(t.rhs.tpe)}"
    case t: Term     => ind(i) + term(t, i)

  /** ctor type-parameter substitution (Scala secondary ctors can't be generic) → their bounds. */
  private var tparamSubst: Map[SymId, TypeRepr] = Map.empty

  private def defDef(d: Tree.DefDef, i: Int): String =
    val s     = sym(d.symbol)
    val isCtor = s.name == "<init>"
    val name  = if isCtor then "this" else esc(s.name)
    // a Java generic constructor (`<T extends X> C(...)`) has no Scala form — secondary ctors can't
    // be generic. Drop the type params and substitute each with its upper bound throughout the ctor.
    val savedSubst = tparamSubst
    if isCtor && d.tparams.nonEmpty then
      tparamSubst = savedSubst ++ d.tparams.map(tp => tp.symbol -> (tp.rhs.tpe match
        case TypeRepr.TypeBounds(_, hi) if hi != TypeRepr.NoType => hi
        case _ => TypeRepr.NoType)).toMap // unbounded → `Any`
    val tps   = if isCtor || d.tparams.isEmpty then "" else "[" + d.tparams.map(typeParam).mkString(", ") + "]"
    val pss   = d.paramss.map(paramClause).mkString
    val ret   = if isCtor then "" else s": ${tpe(d.returnTpt.tpe)}"
    // a Java `while(true){ … return … }` idiom: the loop never falls through, but Scala types
    // `while(true)` as Unit, so a non-Unit method needs an unreachable tail after it.
    val needsUnreachable = !isCtor && !isUnitType(d.returnTpt.tpe) && d.rhs.exists(endsInInfiniteLoop)
    val rhs =
      if isCtor then s" = ${ctorBody(d, i)}"
      else d.rhs.map(r =>
        if needsUnreachable then s" = {\n${ind(i + 1)}${term(r, i + 1)}\n${ind(i + 1)}throw new java.lang.RuntimeException(\"unreachable\")\n${ind(i)}}"
        else s" = ${term(r, i)}").getOrElse("")
    tparamSubst = savedSubst // restore (ctor type-param substitution was local to this def)
    s"${annots(s, i)}${ind(i)}${mods(s.flags)}def $name$tps$pss$ret$rhs"

  private def isUnitType(t: TypeRepr): Boolean = t match
    case TypeRepr.TypeRef(_, s) => sym(s).fullName == "scala.Unit"
    case _ => false
  /** the method body is (or ends in) an infinite `while(true)` / `for(;;)`. */
  private def endsInInfiniteLoop(t: Term): Boolean = t match
    case Tree.While(Tree.Literal(Constant.BoolC(true), _, _), _, _, _) => true
    case Tree.For(_, None, _, _, _, _)                                 => true
    case Tree.Block(stats, e, _, _) =>
      endsInInfiniteLoop(e) || (e match {
        case Tree.Literal(Constant.UnitC, _, _) => stats.lastOption.collect { case x: Term => x }.exists(endsInInfiniteLoop)
        case _ => false
      })
    case _ => false

  /** A Scala secondary constructor must delegate to `this(...)` first — never `super(...)`.
    * Keep a Java `this(args)` delegation. A leading `super(args)` becomes `this()` followed by
    * the parent constructor's own statements, when `CtorFunnel` has established that the two
    * together run exactly what `super(args)` ran; where it has not, the arguments are still lost
    * and `OmissionCheck` still counts them. */
  private def ctorBody(cdef: Tree.DefDef, i: Int): String =
    val stats  = CtorFunnel.stmtsOf(cdef)
    val replay = currentClass.flatMap(plans.replayFor(_, cdef)).getOrElse(Nil)
    val (deleg, rest) = stats match
      case (Tree.Apply(Tree.Select(r, m, _, _), args, _, _, _)) :: tl if sym(m).name == "<init>" =>
        val d = r match
          case _: Tree.Super => "this()"
          case _             => s"this(${args.map(term(_, i + 1)).mkString(", ")})"
        (d, tl)
      case all => ("this()", all)
    val lines = (ind(i + 1) + deleg) :: (replay ++ rest).map(stat(_, i + 1)).filter(_.trim.nonEmpty)
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** a parameter clause; a clause of `given` params renders as a Scala 3 `using` clause. */
  private def paramClause(ps: List[Tree.ValDef]): String =
    if ps.nonEmpty && ps.forall(p => sym(p.symbol).flags.isGiven) then s"(using ${ps.map(param).mkString(", ")})"
    else s"(${ps.map(param).mkString(", ")})"

  // NOTE: Java `T...` → Scala `T*` is deferred — it also needs array-spread (`arr: _*`) at call
  // sites and overload-aware resolution, else `f(array)` calls break. Emitting the param type
  // as `Array[T]` keeps varargs callable positionally via the array.
  private def param(v: Tree.ValDef): String = s"${esc(sym(v.symbol).name)}: ${tpe(v.tpt.tpe)}"

  private def valDef(v: Tree.ValDef, i: Int): String =
    val s = sym(v.symbol)
    if s.flags.isGiven then
      return s"${ind(i)}given ${esc(s.name)}: ${tpe(v.tpt.tpe)}${v.rhs.map(r => s" = ${term(r, i)}").getOrElse("")}"
    v.rhs match
      case Some(r) =>
        val kw = if s.flags.isMutable then "var" else "val"
        val m  = if kw == "var" then mods(s.flags).replace("final ", "") else mods(s.flags)
        s"${ind(i)}$m$kw ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${term(r, i)}"
      case None =>
        // an uninitialized Java field: a `var` defaulted so constructors can assign it (a bare
        // `val x: T` is an abstract member and won't compile in a class). `final var` is
        // contradictory in Scala, so `final` is dropped here.
        s"${ind(i)}${mods(s.flags).replace("final ", "")}var ${esc(s.name)}: ${tpe(v.tpt.tpe)} = ${defaultFor(v.tpt.tpe)}"

  private def defaultFor(t: TypeRepr): String = t match
    case TypeRepr.TypeRef(_, s) => sym(s).fullName match
        case "scala.Int" | "scala.Short" | "scala.Byte" => "0"
        case "scala.Long"                               => "0L"
        case "scala.Float"                              => "0.0f"
        case "scala.Double"                             => "0.0"
        case "scala.Boolean"                            => "false"
        case "scala.Char"                               => "'\\u0000'"
        case "scala.Unit"                               => "()"
        case _                                          => s"null.asInstanceOf[${tpe(t)}]"
    case _ => s"null.asInstanceOf[${tpe(t)}]"

  /** A declaration's Java annotations, rendered ahead of it.
    *
    * FULLY QUALIFIED like every other reference this phase emits, so `@Test` becomes
    * `@org.junit.Test` and needs no import. Losing these is a silent correctness defect: a JUnit
    * suite whose `@Test` did not survive runs ZERO tests and reports SUCCESS. */
  private def annots(s: Symbol, i: Int): String =
    if s.annotations.isEmpty then ""
    else s.annotations.map { a =>
      val args = if a.args.isEmpty then ""
                 // Java's single-element `@A(x)` names its value `value`; Scala takes it positionally.
                 else if a.args.sizeIs == 1 && a.args.head._1 == "value" then s"(${term(a.args.head._2, i)})"
                 else s"(${a.args.map((k, v) => s"$k = ${term(v, i)}").mkString(", ")})"
      s"${ind(i)}@${tpe(a.tpe)}$args\n"
    }.mkString

  private def mods(f: Flags): String =
    val parts = List(
      if f.isPrivate then "private " else "",
      // Java `protected` (package + any-instance-in-subclass) is MORE permissive than Scala
      // `protected` (this-instance only), so a faithful port emits it as public — loosening
      // visibility can only remove access errors, never introduce them.
      "",
      // `private override` is illegal in scala, and the pair is contradictory: a PRIVATE java
      // method is invisible to subclasses, so it overrides nothing — a name/arity agreement with an
      // inherited member is coincidence. `private` is the faithful half; drop the modifier.
      if f.isOverride && !f.isPrivate then "override " else "",
      if f.isFinal then "final " else "",
      if f.isSealed then "sealed " else "",
      if f.isImplicit then "implicit " else "",
      if f.isLazy then "lazy " else "",
    )
    parts.mkString

  // ---- terms ----
  /** true when an `Ident`'s symbol is actually a TYPE used as a value (a static-access
    * receiver like `Float.compare`) — those must render as the (qualified) type name. */
  private def isTypeRef(id: SymId): Boolean = program.definitionOf(id) match
    case Some(_: Tree.ClassDef) => true
    case Some(_)                => false
    case None                   => val f = sym(id).fullName; f.contains('.') && !f.contains('#') && sym(id).info == TypeRepr.NoType

  /** a type used as a VALUE (static-access receiver) — dotted path, never a `#` projection
    * (which is type-position-only syntax). */
  private def typeValue(id: SymId): String =
    val s = sym(id)
    // a static nested type lives in the companion `object`, so name it through the value path
    // `Outer.Inner` even from inside `Outer` (companion members aren't in the class's scope).
    if s.flags.isStatic && s.fullName.contains('$') then s.fullName.replace('$', '.')
    else if currentDeclared(id) || inheritedNested(s.owner) then esc(s.name)
    else s.fullName.replace('$', '.')

  /** a static member lives in the companion `object`; even inside its own class it must be
    * named `Owner.member`, since a Scala class doesn't see its companion's members unqualified. */
  private def staticRef(s: SymId): String =
    val sm = sym(s)
    if sm.flags.isStatic && sm.owner != SymId.None && program.symbolOf(sm.owner).exists(_.info.isInstanceOf[TypeRepr.TypeRef])
    then s"${typeValue(sm.owner)}.${esc(sm.name)}"
    else if shadowedByCompanionStatic(s) then s"this.${esc(sm.name)}"
    else local(s)

  /** Does a bare reference to this INSTANCE member collide with a static of the same name that the
    * enclosing companion carries or re-exports?
    *
    * `DepthShader.Config` inherits an instance field `defaultCullFace` and writes it bare, exactly
    * as Java did — but `object DepthShader` also holds a static `defaultCullFace`, and Scala reports
    * the bare name as ambiguous between the two. Java had no such clash: statics and instance
    * fields live in one namespace there, and the inherited instance field simply wins.
    *
    * `this.` says what Java meant. Decided from the TIR symbol — the reference resolves to an
    * instance member of an ANCESTOR — rather than from the frontend, which cannot see it: Spoon
    * does not resolve this reference to a `CtFieldWrite` under noClasspath at all. */
  private def shadowedByCompanionStatic(s: SymId): Boolean =
    val sm = sym(s)
    !sm.flags.isStatic && sm.owner != SymId.None && sm.info != TypeRepr.NoType &&
      !sm.info.isInstanceOf[TypeRepr.MethodType] && !sm.info.isInstanceOf[TypeRepr.PolyType] &&
      classStack.lastOption.exists { cur =>
        // an INHERITED member (declaring it here would shadow the static on its own)
        cur != sm.owner && ancestorsOf(cur).contains(sm.owner) &&
          classStack.exists(c => staticOwnersOf(c).contains(esc(sm.name)))
      }

  private def term(t: Term, i: Int): String = t match
    case Tree.Ident(s, _, _)            => if isTypeRef(s) then typeValue(s) else staticRef(s)
    case Tree.Literal(c, _, _)          => constant(c)
    case Tree.This(s, _, _)             => thisRef(s)
    case Tree.Super(_, _, _)            => "super"
    case Tree.Select(q, s, _, _)        => s"${term(q, i)}.${local(s)}"
    case Tree.New(tpt, _, _, anon)      => s"new ${ctorTpe(tpt.tpe)}${anonBody(anon, i)}"
    case Tree.Apply(fun, args, _, _, _) => applyStr(fun, args, i)
    case Tree.TypeApply(fun, targs, _, _) => s"${term(fun, i)}[${targs.map(a => tpe(a.tpe)).mkString(", ")}]"
    case Tree.Assign(l, r, _, _)        => s"${term(l, i)} = ${term(r, i)}"
    case Tree.Block(stats, expr, _, _)  => block(stats, expr, i)
    case Tree.Lambda(ps, body, _, _)    => s"(${ps.map(param).mkString(", ")}) => ${term(body, i)}"
    case Tree.If(c, th, el, _, _)       => s"if (${term(c, i)}) ${term(th, i)} else ${term(el, i)}"
    case Tree.Typed(e, tpt, _, _)       => s"${operand(e, i)}.asInstanceOf[${tpe(tpt.tpe)}]" // Java cast
    case Tree.Repeated(es, _, _)        => es.map(term(_, i)).mkString(", ")
    case Tree.Return(e, _, _)           => "return" + e.map(x => " " + term(x, i)).getOrElse("")
    case Tree.While(c, b, _, _)         => s"while (${term(c, i)}) ${term(b, i)}"
    case Tree.Throw(e, _, _)            => s"throw ${term(e, i)}"
    case Tree.InstanceOf(e, tpt, _, _)  => s"${term(e, i)}.isInstanceOf[${tpe(tpt.tpe)}]"
    case Tree.ArrayAccess(a, idx, _, _) => s"${term(a, i)}(${term(idx, i)})"
    case Tree.ArrayLength(a, _, _)      => s"${term(a, i)}.length"
    case Tree.NewArray(el, dims, init, _, _) =>
      init match
        // `scala.Array`, fully qualified: a bare `Array` collides with libGDX's own
        // `com.badlogic.gdx.utils.Array` inside that package (same-package name resolution).
        case Some(es) => s"scala.Array[${tpe(el.tpe)}](${es.map(term(_, i)).mkString(", ")})"
        // Java `new T[a][b]` gives every dimension a size; Scala's `new Array` takes only ONE, so a
        // MULTI-dimension allocation lowers to `Array.ofDim[base](a, b)`. A single dim (incl. partial
        // `new T[a][]`) stays `new Array[elem](a)`.
        case None if dims.sizeIs > 1 => s"scala.Array.ofDim[${tpe(baseElem(el.tpe))}](${dims.map(term(_, i)).mkString(", ")})"
        case None     => s"new scala.Array[${tpe(el.tpe)}](${dims.map(term(_, i)).mkString(", ")})"
    case Tree.ForEach(b, it, body, _, _) => s"for (${esc(sym(b.symbol).name)} <- ${term(it, i)}) ${term(body, i)}"
    case Tree.For(init, cond, upd, body, _, _) =>
      val is = init.map(stat(_, 0)).mkString("; ")
      val c  = cond.map(term(_, i)).getOrElse("true")
      val bodyWithUpd = s"{ ${term(body, i)}; ${upd.map(stat(_, 0)).mkString("; ")} }"
      s"{ $is; while ($c) $bodyWithUpd }"
    case Tree.Try(res, body, catches, fin, _, _) => tryStr(res, body, catches, fin, i)
    case Tree.Match(scr, cases, _, _)   => matchStr(scr, cases, i)
    case Tree.MethodRef(q, s, mrT, _)   =>
      val isCtor = sym(s).name == "<init>" // `Type::new` → a factory function `() => new Type()`
      q match
        // an ARRAY constructor reference `T[]::new` is an `IntFunction[T[]]` — `(size) => new T[size]`
        // (Scala arrays need a length), NOT a no-arg supplier. One-layer element = the array's row type.
        // a constructor reference must name an INSTANTIABLE type: `new T[?]()` is rejected
        // ("type argument must be fully defined"), so route through `ctorTpe`, which drops
        // wildcard arguments and lets Scala infer them — and erase a wildcard array element to
        // `Object`, which is what Java's raw `T[]::new` means anyway.
        case Left(tt) if isCtor => tt.tpe match
          case TypeRepr.AppliedType(TypeRepr.TypeRef(_, as), List(el)) if sym(as).fullName == "scala.Array" =>
            val elem = el match
              case _: TypeRepr.TypeBounds => "java.lang.Object"
              case other                  => tpe(other)
            s"((size: scala.Int) => new scala.Array[$elem](size))"
          case _ => samAscribed(s"(() => new ${ctorTpe(tt.tpe)}())", mrT, tt.tpe)
        case Left(tt)           => s"${tpe(tt.tpe)}.${local(s)}"
        case Right(e)           => s"${term(e, i)}.${local(s)}"
    case Tree.Break(_, _, _)            => "/* break */ ()"    // TODO: scala.util.boundary
    case Tree.Continue(_, _, _)         => "/* continue */ ()" // TODO: scala.util.boundary
    case Tree.Assert(c, m, _, _)        => s"assert(${term(c, i)}${m.map(x => ", " + term(x, i)).getOrElse("")})"
    case Tree.IncDec(tgt, op, _, _, _)  => s"{ ${term(tgt, i)} $op= 1; ${term(tgt, i)} }" // yields the value
    case Tree.DoWhile(b, c, _, _)       => s"while ({ ${term(b, i)}; ${term(c, i)} }) ()" // Scala 3 has no do-while
    case Tree.Synchronized(l, b, _, _)  => s"${term(l, i)}.synchronized ${term(b, i)}"
    case Tree.Opaque(raw, _, _)         => raw

  /** A Java constructor reference (`Foo::new`) is typed by the TARGET functional interface Java
    * resolved, not by `Foo`. Emitted bare, `() => new Foo()` is a `Function0`, which Scala
    * SAM-converts to ANY single-abstract-method type — so an overload set offering two of them
    * (`PoolManager.addPool(Class, Pool)` vs `(Class, PoolSupplier)`) becomes AMBIGUOUS where
    * Java's was not. Re-state the resolved target as an ascription.
    *
    * Strictly guarded, because the ascription is only sound when the frontend really gave us the
    * functional interface: the type must be concrete (no type variables, no `NoType`) and must not
    * be the constructed type itself. Anything else falls back to the bare lambda — the previous
    * behaviour — so this can only ever narrow, never mis-type. */
  private def samAscribed(fn: String, target: TypeRepr, ctor: TypeRepr): String =
    def headOf(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headOf(tc)
      case _                           => None
    def concrete(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeRef(_, s)       => !sym(s).flags.isParam
      case TypeRepr.AppliedType(tc, as) => concrete(tc) && as.forall(arg)
      case _                            => false
    // a bare `?` is a legal type ARGUMENT (`PoolSupplier[Array[?]]`) though not a legal type
    def arg(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => true
      case other                                                 => concrete(other)
    val ok = concrete(target) && headOf(target) != headOf(ctor)
    if ok then s"($fn: ${tpe(target)})" else fn

  private def applyStr(fun: Term, args: List[Term], i: Int): String = fun match
    case Tree.New(tpt, _, _, anon) =>
      s"new ${ctorTpe(tpt.tpe)}(${args.map(term(_, i)).mkString(", ")})${anonBody(anon, i)}"
    // operators (populator tags them `scala.<op>#…`) render infix / prefix, not `.op(x)`.
    case Tree.Select(recv, m, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      val op = sym(m).name
      if op.startsWith("unary_") then s"${op.stripPrefix("unary_")}${operand(recv, i)}"
      else s"${operand(recv, i)} $op ${args.map(operand(_, i)).mkString(", ")}"
    case Tree.Select(recv, m, _, _) if sym(m).name == "<init>" =>
      val kw = recv match { case _: Tree.Super => "super"; case _ => "this" }
      s"$kw(${args.map(term(_, i)).mkString(", ")})"
    case Tree.Select(_, m, _, _) if numericOverloadAscription(m).isDefined =>
      s"(${term(fun, i)}: ${numericOverloadAscription(m).get})(${args.map(term(_, i)).mkString(", ")})"
    case _ => s"${term(fun, i)}(${args.map(term(_, i)).mkString(", ")})"

  /** widening rank — a value of rank r converts implicitly to any numeric type of higher rank.
    * `Char` and `Short` share a rank because neither widens to the other. */
  private val numericRank = Map("scala.Byte" -> 1, "scala.Short" -> 2, "scala.Char" -> 2,
                                "scala.Int" -> 3, "scala.Long" -> 4, "scala.Float" -> 5,
                                "scala.Double" -> 6)

  /** Java resolves an overload by EXACT match; Scala widens numerics first and then finds no
    * most-specific alternative.
    *
    * `Sprite.setRegion(int, int, int, int)` and `setRegion(float, float, float, float)` are both
    * applicable to four `Int` arguments — Java simply picks the `int` one, Scala reports an
    * ambiguity. Ascribing the method's function type names the alternative Java chose:
    * `(this.setRegion: (Int, Int, Int, Int) => Unit)(x, y, w, h)`.
    *
    * Fires only where the clash actually exists: a sibling of the same name and arity is WEAKLY
    * WIDER at every position and strictly wider at one, so the very same arguments reach it by
    * widening. `append(int)` beside `append(char)` is not that shape — `char` does not absorb an
    * `int` — and needs no help. Checking the direction is what keeps this from ascribing every
    * numeric call in the program (measured: 175 sites where 1 was ambiguous). */
  private def numericOverloadAscription(m: SymId): Option[String] =
    def numericParams(d: Tree.DefDef): Option[List[TypeRepr]] =
      val ps = d.paramss.flatten.map(_.tpt.tpe)
      Option.when(ps.nonEmpty && ps.forall(p => headSymOf(p).exists(s => numericRank.contains(sym(s).fullName))))(ps)
    def rank(t: TypeRepr): Int = headSymOf(t).flatMap(s => numericRank.get(sym(s).fullName)).getOrElse(0)
    def absorbs(wider: List[TypeRepr], here: List[TypeRepr]): Boolean =
      wider.sizeIs == here.size &&
        wider.zip(here).forall((w, h) => w == h || rank(w) > rank(h)) &&
        wider.zip(here).exists((w, h) => w != h)
    for
      d      <- program.definitionOf(m).collect { case d: Tree.DefDef => d }
      ps     <- numericParams(d)
      owner  <- program.definitionOf(sym(m).owner).collect { case c: Tree.ClassDef => c }
      if owner.body.exists {
        case o: Tree.DefDef =>
          o.symbol != m && sym(o.symbol).name == sym(m).name &&
            numericParams(o).exists(absorbs(_, ps))
        case _ => false
      }
    yield s"(${ps.map(tpe).mkString(", ")}) => ${tpe(d.returnTpt.tpe)}"

  /** A Java anonymous class's body → Scala's anonymous-class expression `new Base(args) { … }`.
    *
    * The anonymous class's own symbol is pushed on `classStack` while its members render, which is
    * what makes `thisRef` qualify an enclosing reference as `Outer.this.m`: inside a Scala
    * anonymous class the bare `this` is the anonymous instance, exactly as in Java, so an enclosing
    * member reached implicitly must be named through the outer instance. Captured locals need no
    * lowering at all — Scala closes over them where javac had to synthesise ctor parameters.
    *
    * `Some(Nil)` (the super-type-token idiom `new Base(){}`) still renders the braces: `new Base()`
    * and `new Base(){}` are DIFFERENT types, and only the latter has the reified supertype. */
  private def anonBody(anon: Option[Tree.AnonClass], i: Int): String = anon match
    case None    => ""
    case Some(a) =>
      classStack.append(a.symbol)
      val members = try a.body.map(stat(_, i + 1)).filter(_.trim.nonEmpty) finally classStack.removeLast()
      if members.isEmpty then " {}" else s" {\n${joinStats(members)}\n${ind(i)}}"

  /** parenthesize a term when it is an operand, where bare juxtaposition would misparse:
    * an operator application (precedence) and any control-flow expression — `if`/`match`
    * as an operand (`a + if (c) x else y`) needs parens or Scala reads "end of statement". */
  private def operand(t: Term, i: Int): String = t match
    case Tree.Apply(Tree.Select(_, m, _, _), _, _, _, _) if sym(m).fullName.startsWith("scala.<op>#") =>
      s"(${term(t, i)})"
    case _: Tree.If | _: Tree.Match | _: Tree.Lambda => s"(${term(t, i)})"
    case _ => term(t, i)

  private def block(stats: List[Statement], expr: Term, i: Int): String =
    // drop a redundant trailing `()` when the block already has statements (Java void bodies).
    val tail = expr match
      case Tree.Literal(Constant.UnitC, _, _) if stats.nonEmpty => Nil
      case _                                                    => List(ind(i + 1) + term(expr, i + 1))
    val lines = (stats.map(stat(_, i + 1)) ++ tail).filter(_.trim.nonEmpty)
    s"{\n${joinStats(lines)}\n${ind(i)}}"

  /** join block statements, terminating one with `;` when the NEXT begins with `{` — otherwise
    * Scala greedily reads `new T(a)\n{ … }` as an anonymous-class body rather than two statements. */
  private def joinStats(lines: List[String]): String = lines match
    case Nil => ""
    case h :: t =>
      val sb = new StringBuilder(h)
      t.foreach { l => if l.trim.startsWith("{") then sb.append(";"); sb.append("\n").append(l) }
      sb.toString

  private def tryStr(res: List[Tree.ValDef], body: Term, catches: List[Tree.CatchCase], fin: Option[Term], i: Int): String =
    val r  = res.map(v => s"${ind(i + 1)}${valDef(v, 0)}\n").mkString
    val cs = catches.map(c => s"${ind(i + 1)}case ${esc(sym(c.param.symbol).name)}: ${tpe(c.param.tpt.tpe)} => ${term(c.body, i + 1)}").mkString("\n")
    val cl = if catches.isEmpty then "" else s" catch {\n$cs\n${ind(i)}}"
    val fl = fin.map(f => s" finally ${term(f, i)}").getOrElse("")
    s"try ${term(body, i)}$cl$fl" // resources: r prepended when the backend lowers auto-close

  private def matchStr(scr: Term, cases: List[Tree.CaseDef], i: Int): String =
    val cs = cases.map { c =>
      val pat = if c.isDefault then "_" else c.labels.map(term(_, i)).mkString(" | ")
      s"${ind(i + 1)}case $pat => ${term(c.body, i + 1)}"
    }.mkString("\n")
    s"${term(scr, i)} match {\n$cs\n${ind(i)}}"

  // ---- types ----
  /** a type in `new` position: `new Foo[?]` is illegal (you can't instantiate a wildcard), so
    * when a raw generic type carries wildcard args, drop them and let Scala infer the arguments
    * from the expected type (`new Foo(...)`). */
  /** As in `parentTpe`, only the HEAD is a `namedInner` position — the arguments are ordinary. */
  private def ctorTpe(t: TypeRepr): String = t match
    case TypeRepr.AppliedType(tc, args) if args.exists(_.isInstanceOf[TypeRepr.TypeBounds]) => byName(tpe(tc))
    case TypeRepr.AppliedType(tc, args) => s"${byName(tpe(tc))}[${args.map(tpe).mkString(", ")}]"
    case _ => byName(tpe(t))

  /** strip `scala.Array[...]` layers to the base element type (for `Array.ofDim[base](dims)`). */
  private def baseElem(t: TypeRepr): TypeRepr = t match
    case TypeRepr.AppliedType(TypeRepr.TypeRef(_, s), List(e)) if sym(s).fullName == "scala.Array" => baseElem(e)
    case _ => t

  private def tpe(t: TypeRepr): String = t match
    case TypeRepr.NoType | TypeRepr.NoPrefix   => "Any"
    case TypeRepr.TypeRef(_, s)                => typeSym(s)
    case TypeRepr.TermRef(_, s)                => s"${typeSym(s)}.type"
    case TypeRepr.ThisType(_)                  => "this.type"
    case TypeRepr.SuperType(_, sup)            => tpe(sup)
    case TypeRepr.ConstantType(c)              => constant(c)
    case TypeRepr.AppliedType(tc, as)          => s"${tpe(tc)}[${as.map(tpe).mkString(", ")}]"
    case TypeRepr.AndType(l, r)                => s"${tpe(l)} & ${tpe(r)}"
    case TypeRepr.OrType(l, r)                 => s"${tpe(l)} | ${tpe(r)}"
    case TypeRepr.ByNameType(u)                => s"=> ${tpe(u)}"
    case TypeRepr.TypeBounds(TypeRepr.NoType, TypeRepr.NoType) => "?"
    case TypeRepr.TypeBounds(lo, hi) =>
      val l = if lo == TypeRepr.NoType then "" else s" >: ${tpe(lo)}"
      val h = if hi == TypeRepr.NoType then "" else s" <: ${tpe(hi)}"
      s"?$l$h"
    case TypeRepr.Refinement(p, _, _)          => tpe(p)
    case TypeRepr.MethodType(ps, res, _)       => s"(${ps.map((_, pt) => tpe(pt)).mkString(", ")}) => ${tpe(res)}"
    case TypeRepr.PolyType(_, res)             => tpe(res)
    case TypeRepr.TypeLambda(ps, body)         => s"[${ps.map(_._1).mkString(", ")}] =>> ${tpe(body)}"
    case TypeRepr.ParamRef(_, _)               => "?"

  // ---- constants ----
  private def constant(c: Constant): String = c match
    case Constant.BoolC(v)   => v.toString
    case Constant.ByteC(v)   => v.toString
    case Constant.ShortC(v)  => v.toString
    case Constant.IntC(v)    => v.toString
    case Constant.LongC(v)   => s"${v}L"
    case Constant.FloatC(v)  => s"${v}f"
    case Constant.DoubleC(v) => v.toString
    case Constant.CharC('\'') => "'\\''" // a single-quote char must be escaped inside `'…'`
    case Constant.CharC(v)   => s"'${escape(v.toString)}'"
    case Constant.StringC(v) => "\"" + escape(v) + "\""
    case Constant.NullC      => "null"
    case Constant.UnitC      => "()"
    case Constant.ClassOfC(t) => s"classOf[${tpe(t)}]"

  private def escape(s: String): String =
    s.flatMap {
      case '\\' => "\\\\"; case '"' => "\\\""; case '\n' => "\\n"; case '\r' => "\\r"; case '\t' => "\\t"
      case c    => c.toString
    }

object TirEmitter:
  /** Drop `private` from the given members. Java lets a parent constructor write its own private
    * fields; when those statements are REPLAYED one level down (see `CtorFunnel.replayFor`) they
    * execute in the subclass, where `private` no longer reaches. Widening visibility can only
    * remove access errors, never introduce one, and never changes behaviour. */
  def widen(p: Program, members: Set[SymId]): Program =
    if members.isEmpty then p
    else
      val syms = p.symbols.all.map(s =>
        if members(s.id) then s.copy(flags = s.flags.copy(isPrivate = false)) else s
      )
      new Program(p.units, SymbolTable(syms), p.xref)

  /** Promoting a constructor to Scala's primary widens the SCOPE of everything it declares: its
    * parameters become class parameters and its top-level locals become class members, both
    * visible to the whole body instead of to the constructor alone. That is the only hazard in
    * the promotion, and it has two faces — a name shared with one of the class's own members is
    * a double definition, and a name shared with an INHERITED member silently captures every
    * unqualified read of it (`this.viewport = viewport` still works; a bare `viewport` no longer
    * means the field). Suffixing `$p` removes both: parameters are positional and the locals are
    * unreachable from outside, so the rename is invisible everywhere it matters.
    */
  def funnelParamRenames(p: Program): Program =
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def parentSyms(cd: Tree.ClassDef): List[SymId] =
      def hs(t: TypeRepr): Option[SymId] = t match
        case TypeRepr.TypeRef(_, s)      => Some(s)
        case TypeRepr.AppliedType(tc, _) => hs(tc)
        case _                           => scala.None
      cd.parents.flatMap { case tt: TypeTree => hs(tt.tpe); case t: Term => hs(t.tpe) }
    val declOf   = collection.mutable.Map[SymId, Tree.ClassDef]()
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
    p.units.foreach(index)
    def ownNames(cd: Tree.ClassDef): Set[String] =
      cd.body.collect {
        case d: Tree.DefDef if nm(d.symbol) != "<init>" => nm(d.symbol)
        case v: Tree.ValDef                             => nm(v.symbol)
        case c: Tree.ClassDef                           => nm(c.symbol)
      }.toSet
    def visibleNames(cd: Tree.ClassDef, seen: Set[SymId] = Set.empty): Set[String] =
      if seen(cd.symbol) then Set.empty
      else ownNames(cd) ++ parentSyms(cd).flatMap(declOf.get).flatMap(visibleNames(_, seen + cd.symbol))
    val plans = CtorFunnel.Plans(p)
    def scan(cd: Tree.ClassDef): Unit =
      val plan = plans(cd)
      if plan.primary.isDefined then
        val taken = collection.mutable.Set.from(visibleNames(cd))
        // the promoted constructor's params, then the top-level locals of its body (nested
        // blocks keep their own scope and never reach the class body)
        val widened = plan.primaryParams ++ plan.primaryBody.collect { case v: Tree.ValDef => v }
        widened.foreach { v =>
          val n = nm(v.symbol)
          if taken(n) then
            var fresh = n + "$p"
            while taken(fresh) do fresh += "$"
            taken += fresh
            renames(v.symbol) = fresh
        }
      cd.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () }
    p.units.foreach(scan)
    if renames.isEmpty then p
    else new Program(p.units, SymbolTable(p.symbols.all.map(s => renames.get(s.id).map(n => s.copy(name = n)).getOrElse(s))), p.xref)

  /** Rename any field that SHADOWS an inherited member.
    *
    * Java fields shadow rather than override, and are resolved by the STATIC type of the receiver:
    * `ParallelArray.Channel` declares `Object data` and `FloatChannel extends Channel` declares
    * `float[] data`, so both objects exist and `((Channel) fc).data` and `fc.data` are different
    * storage. Scala has no such thing — a `var` cannot be overridden at all, let alone at an
    * incompatible type — and there is no rendering that keeps one name.
    *
    * So the shadowing field gets a fresh name. That is exact rather than approximate precisely
    * BECAUSE Java resolved these statically: every reference in the TIR already points at the
    * symbol Java chose, so renaming the symbol re-points exactly the references Java meant and no
    * others. Confirmed against the reference port, which does the same by hand and says why:
    * "renamed to floatData/intData/objectData … Java shadowed the field; Scala can't".
    *
    * Fields shadowing an inherited METHOD (`TextField`'s `layout` field under `Widget.layout()`)
    * are the same defect through Java's separate namespaces for the two, and are renamed here too.
    * Statics are exempt: they land in the companion, which inherits nothing. */
  def resolveFieldShadowing(p: Program): Program =
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    val declOf  = collection.mutable.Map[SymId, Tree.ClassDef]()
    def index(cd: Tree.ClassDef): Unit =
      declOf(cd.symbol) = cd
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => index(c); case _ => () })
    p.units.foreach(index)
    def instanceMembers(cd: Tree.ClassDef): Set[String] =
      cd.body.collect {
        case d: Tree.DefDef if nm(d.symbol) != "<init>" && !p.symbolOf(d.symbol).exists(_.flags.isStatic) => nm(d.symbol)
        case v: Tree.ValDef if !p.symbolOf(v.symbol).exists(_.flags.isStatic)                             => nm(v.symbol)
      }.toSet
    def inherited(cd: Tree.ClassDef, seen: Set[SymId] = Set.empty): Set[String] =
      cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case t: Term => headSym(t.tpe) }
        .filterNot(seen)
        .flatMap(declOf.get)
        .flatMap(pcd => instanceMembers(pcd) ++ inherited(pcd, seen + cd.symbol))
        .toSet
    def scan(cd: Tree.ClassDef): Unit =
      val shadowed = inherited(cd)
      cd.body.foreach {
        case v: Tree.ValDef if shadowed(nm(v.symbol)) && !p.symbolOf(v.symbol).exists(_.flags.isStatic) =>
          renames(v.symbol) = nm(v.symbol) + "$shadow"
        case c: Tree.ClassDef => scan(c)
        case _                => ()
      }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () })
    p.units.foreach(scan)
    if renames.isEmpty then p
    else
      // same visibility relaxation as `resolveMemberClashes`: a renamed field must stay reachable
      // from wherever java read it.
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      new Program(p.units, SymbolTable(syms), p.xref)

  /** Rename any field whose simple name collides with a method in the same class (legal in
    * Java, illegal in Scala) by suffixing `$field`. Renaming the symbol propagates to every
    * reference, since the emitter reads names from the symbol table. */
  def resolveMemberClashes(p: Program): Program =
    val renames = collection.mutable.Map[SymId, String]()
    def nm(id: SymId): String = p.symbolOf(id).map(_.name).getOrElse("")
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s) => Some(s); case TypeRepr.AppliedType(tc, _) => headSym(tc); case _ => None
    // per-class method names, and the parent edges — a Java field can coexist with a same-named
    // METHOD in a SUBCLASS (`hasNext` field + `hasNext()` from Iterator), which Scala forbids.
    val methodsOf = collection.mutable.Map[SymId, Set[String]]()
    val childrenOf = collection.mutable.Map[SymId, List[SymId]]().withDefaultValue(Nil)
    def index(cd: Tree.ClassDef): Unit =
      methodsOf(cd.symbol) = cd.body.collect { case d: Tree.DefDef => nm(d.symbol) }.toSet
      cd.parents.foreach { case tt: TypeTree => headSym(tt.tpe).foreach(pp => childrenOf(pp) = cd.symbol :: childrenOf(pp)); case _ => () }
      cd.body.foreach { case c: Tree.ClassDef => index(c); case _ => () }
    p.units.foreach(index)
    def selfOrDescMethods(c: SymId, seen: Set[SymId] = Set.empty): Set[String] =
      if seen(c) then Set.empty
      else methodsOf.getOrElse(c, Set.empty) ++ childrenOf(c).flatMap(ch => selfOrDescMethods(ch, seen + c))
    def scan(cd: Tree.ClassDef): Unit =
      val clashNames = selfOrDescMethods(cd.symbol)
      cd.body.foreach {
        case v: Tree.ValDef if clashNames(nm(v.symbol)) => renames(v.symbol) = nm(v.symbol) + "$field"
        case c: Tree.ClassDef                           => scan(c)
        case _                                           => ()
      }
      cd.enumCases.foreach(_.body.foreach { case c: Tree.ClassDef => scan(c); case _ => () })
    p.units.foreach(scan)
    if renames.isEmpty then p
    else
      // also relax visibility: Java lets the enclosing class read a nested class's private
      // field (`point.x`); Scala does not, so a renamed clash-field must stay accessible.
      val syms = p.symbols.all.map(s =>
        renames.get(s.id).map(n => s.copy(name = n, flags = s.flags.copy(isPrivate = false, isProtected = false))).getOrElse(s)
      )
      new Program(p.units, SymbolTable(syms), p.xref)
