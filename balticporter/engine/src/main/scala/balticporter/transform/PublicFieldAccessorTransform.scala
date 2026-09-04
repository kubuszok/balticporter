package balticporter.transform

import balticporter.core.{RequiresRuntime, RuntimeArtifact, SurfacePolicy}
import balticporter.tir.*

/** Adds `getX()`/`setX(v)` beside a java `public` instance field, since a Scala `var` emits a
  * PRIVATE JVM field — a reflective bean framework sees nothing (ENGINE-LIMITS K21 face 2: every
  * lookup silently reads `null`). The getter is typed `java.lang.Object`, bridged through
  * `Reified.toJavaValue` (a MINTED signature); the setter is not bridged. CLAUDE.md §1(b): scoped,
  * `Only(Set.empty)` no-op; a name clash is refused and counted. */
final class PublicFieldAccessorTransform(
    /** Which declarations are read reflectively. `Only(Set.empty)` (default) admits nothing;
      * `Everywhere(Set.empty)` is the whole port. Entries are FQNs cut at a `Symbol.fullName`
      * separator, so an enclosing type also reaches its nested and anonymous classes. */
    val scope: RuleScope = RuleScope.Only(Set.empty),
) extends Phase, RequiresRuntime, SurfacePolicy, PolicyBound:

  def name = "public-field->bean-accessors"

  /** The phase decides member names in the emitted surface (`SurfacePolicy`). */
  def surfaceFingerprint: String = scope.fingerprint

  /** The getter's bridge, declared only where the scope can admit something so an unconfigured
    * port acquires no unused runtime dependency. */
  def runtimeTypes: Set[String] =
    if scope.isUnrestricted || scope.entries.nonEmpty then Set(PublicFieldAccessorTransform.JavaCollectionsFqn)
    else Set.empty

  private var bound: Map[String, Binding[Unit]] = Map.empty

  def bindPolicy(binder: PolicyBinder): Unit =
    val setting = s"PublicFieldAccessorTransform(scope) ${scope.productPrefix} entry"
    bound = scope.entries.toList.sorted.map(e => e -> binder.bindScope(name, setting, e)).toMap

  // ---- per-run state ----

  private var next: Int = 0
  private val added = collection.mutable.ListBuffer[Symbol]()
  private var toJavaValueSym: SymId = SymId.None
  private var objectSym: SymId      = SymId.None

  /** `scala.Unit`, as this program spells it — `TypeRepr.NoType` would render `Any`. */
  private var unitTpe: TypeRepr = TypeRepr.NoType

  /** Every field this phase could not expose, and every type it was not asked about that has one
    * (`exposure`'s two rows). */
  private val refusals = collection.mutable.ListBuffer[BeanExposureCheck.Finding]()

  private def mint(nm: String, full: String, flags: Flags, owner: SymId, info: TypeRepr): SymId =
    val id = SymId(next); next += 1
    added += Symbol(id, nm, full, flags, owner, info)
    id

  override def run(program: Program): Program =
    next = program.symbols.all.map(_.id.raw).maxOption.getOrElse(-1) + 1
    added.clear()
    refusals.clear()
    toJavaValueSym = SymId.None
    // resolved where the program names it, minted otherwise — two symbols for one FQN would
    // compare unequal and print the same text
    objectSym = program.symbols.all.find(_.fullName == PublicFieldAccessorTransform.ObjectFqn)
      .map(_.id).getOrElse(mint("Object", PublicFieldAccessorTransform.ObjectFqn, Flags(),
                                SymId.None, TypeRepr.NoType))
    if scope.isUnrestricted || scope.entries.nonEmpty then
      toJavaValueSym = mint("toJavaValue", PublicFieldAccessorTransform.ToJavaValueFqn,
                            Flags(), SymId.None, TypeRepr.NoType)
    val unitSym = program.symbols.all.find(_.fullName == PublicFieldAccessorTransform.UnitFqn)
      .map(_.id).getOrElse(mint("Unit", PublicFieldAccessorTransform.UnitFqn, Flags(), SymId.None,
                                TypeRepr.NoType))
    unitTpe = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)
    given Program = program
    val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    program.rebuilt(units, SymbolTable(program.symbols.all ++ added))

  /** A named class — the ordinary case. */
  override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
    t.copy(body = exposed(t.symbol, t.body, t.origin))

  /** An anonymous class, reached through its `New` and never through `transformClassDef` — the
    * shape this phase exists for (`new Inspectable() { public Date a = …; }`). */
  override def transformNew(t: Tree.New)(using Program): Term =
    t.copy(anon = t.anon.map(a => a.copy(body = exposed(a.symbol, a.body, a.origin))))

  /** The body of one class, plus whatever accessors its java-public fields need. */
  private def exposed(cls: SymId, body: List[Statement], origin: Origin)(using p: Program): List[Statement] =
    val owner = p.symbolOf(cls)
    if owner.isEmpty then body
    else if !scope.includes(p, owner.get) then
      // not in scope, and it has java-public fields: the review list, one row per type
      val fields = publicFields(body)
      if fields.nonEmpty then
        refusals += BeanExposureCheck.Finding(
          BeanExposureCheck.Issue.Unexposed, owner.get.fullName,
          fields.flatMap(v => p.symbolOf(v.symbol)).map(_.name).sorted.mkString(","), origin)
      body
    else
      // the screen accumulates from inherited names too, so a member the port cannot read at all
      // (an out-of-program ancestor) is never overwritten
      val existing = collection.mutable.Set.from(memberNames(body) ++ inheritedNames(cls))
      val extra = publicFields(body).flatMap { v =>
        val fs   = p.symbolOf(v.symbol).get
        val bean = PublicFieldAccessorTransform.beanSuffix(fs.name)
        val want = List("get" + bean, "set" + bean, "is" + bean)
        if !PublicFieldAccessorTransform.invertible(fs.name) then
          // the property a bean reader would register is not this field's name — refused, not emitted
          refusals += BeanExposureCheck.Finding(
            BeanExposureCheck.Issue.NameUnreachable, MemberKey(owner.get.fullName, fs.name).render,
            s"get$bean would register the property `${PublicFieldAccessorTransform.decapitalize(bean)}`",
            v.origin)
          Nil
        else if want.exists(existing.contains) then
          // a class declaring both the field and its own accessor is ordinary; `is` refused beside
          // `get` too, since a reader seeing both reports a conflicting property
          refusals += BeanExposureCheck.Finding(
            BeanExposureCheck.Issue.NameTaken, MemberKey(owner.get.fullName, fs.name).render,
            want.filter(existing.contains).mkString(","), v.origin)
          Nil
        else
          val added = accessors(cls, v, fs, bean)
          existing ++= added.collect { case d: Tree.DefDef => p.symbolOf(d.symbol).map(_.name) }.flatten
          added
      }
      if extra.isEmpty then body else body ++ extra

  /** The fields a java `public` modifier put on the class file's surface — instance fields only
    * (a `static` field is not a bean property). */
  private def publicFields(body: List[Statement])(using p: Program): List[Tree.ValDef] =
    body.collect { case v: Tree.ValDef => v }.filter { v =>
      p.symbolOf(v.symbol).exists { s =>
        val f = s.flags
        !f.isPrivate && !f.isProtected && !f.isPackagePrivate && !f.isStatic && !f.isParam
      }
    }

  private def memberNames(body: List[Statement])(using p: Program): Set[String] =
    body.flatMap {
      case d: Tree.DefDef   => p.symbolOf(d.symbol).map(_.name)
      case v: Tree.ValDef   => p.symbolOf(v.symbol).map(_.name)
      case c: Tree.ClassDef => p.symbolOf(c.symbol).map(_.name)
      case _                => scala.None
    }.toSet

  /** Every member name `cls` inherits from an ancestor this program declares. Bounded to the
    * program on purpose (§4.56) — an ancestor's members outside it are a class file this pass
    * cannot read; K21 states that residue rather than guessing at it. */
  private def inheritedNames(cls: SymId)(using p: Program): Set[String] =
    def headSym(t: TypeRepr): Option[SymId] = t match
      case TypeRepr.TypeRef(_, s)      => Some(s)
      case TypeRepr.AppliedType(tc, _) => headSym(tc)
      case _                           => scala.None
    def go(s: SymId, seen: Set[SymId]): Set[String] =
      p.definitionOf(s) match
        case Some(cd: Tree.ClassDef) =>
          val parents = cd.parents.flatMap {
            case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe)
          }.filterNot(seen)
          memberNames(cd.body) ++ parents.flatMap(go(_, seen + s)).toSet
        case _ => Set.empty
    val direct = p.definitionOf(cls) match
      case Some(cd: Tree.ClassDef) =>
        cd.parents.flatMap { case tt: TypeTree => headSym(tt.tpe); case term: Term => headSym(term.tpe) }
      case _ => Nil
    direct.flatMap(go(_, Set(cls))).toSet

  /** `def getX(): java.lang.Object` and, for a java non-final field, `def setX(v: T): Unit`. */
  private def accessors(cls: SymId, v: Tree.ValDef, fs: Symbol, bean: String)(using p: Program): List[Statement] =
    val o    = v.origin
    val tpe  = v.tpt.tpe
    val self = Tree.This(cls, TypeRepr.ThisType(cls), o)
    val read = Tree.Select(self, fs.id, tpe, o)
    // always java.lang.Object — a minted signature bridged unconditionally (§4.56, class doc)
    val getTpe = if objectSym == SymId.None then tpe else TypeRepr.TypeRef(TypeRepr.NoPrefix, objectSym)
    val body =
      if toJavaValueSym != SymId.None then
        Tree.Apply(Tree.Ident(toJavaValueSym, TypeRepr.NoType, o), List(read), toJavaValueSym, getTpe, o)
      else read
    val ownerFqn = p.symbolOf(cls).map(_.fullName).getOrElse("")
    val getSym = mint("get" + bean, MemberKey(ownerFqn, "get" + bean).render, Flags(), cls,
                      TypeRepr.MethodType(Nil, getTpe))
    val getter = Tree.DefDef(getSym, List(Nil), TypeTree(getTpe, o), Some(body), o)
    val setter =
      if !fs.flags.isMutable then Nil
      else
        val pSym = mint("v", "v", Flags(isParam = true), SymId.None, tpe)
        val sSym = mint("set" + bean, MemberKey(ownerFqn, "set" + bean).render, Flags(), cls,
                        TypeRepr.MethodType(List("v" -> tpe), unitTpe))
        List(Tree.DefDef(sSym, List(List(Tree.ValDef(pSym, TypeTree(tpe, o), scala.None, o))),
                         TypeTree(unitTpe, o),
                         Some(Tree.Assign(Tree.Select(self, fs.id, tpe, o),
                                          Tree.Ident(pSym, tpe, o), TypeRepr.NoType, o)), o))
    record(Decision(
      kind       = Decision.Kind.BeanAccessor,
      subject    = fs.id,
      subjectFqn = MemberKey(ownerFqn, fs.name).render,
      detail = Map(
        "field"    -> fs.name,
        "accessor" -> (("get" + bean) :: setter.map(_ => "set" + bean)).mkString(","),
        "bridged"  -> (if body ne read then "yes" else "no"),
        "why"      -> ("java's `public` field is part of the class file's surface and scala emits " +
          "no public JVM field for any declaration form, so a framework auto-detecting one sees " +
          "nothing at all — these accessors are what a bean reader can see instead, and the getter " +
          "is typed `java.lang.Object` through the run-time bridge because its only reader is that " +
          "framework, reading the value and not the signature"),
      ),
      // the key is the manifest entry verbatim (§4.575); unrestricted has no entry, so name the declaration
      reason = Reason.Configured(name, scope.entryFor(fs.fullName).getOrElse(
        if scope.fingerprint.isEmpty then "scope (unrestricted)" else scope.fingerprint)),
      origin = o,
    ))
    getter :: setter

  /** What this run could not expose, and what it was never asked about, for `BeanExposureCheck`;
    * held to the units the run emits by its caller (D2). */
  def exposure(units: List[Tree.ClassDef]): List[BeanExposureCheck.Finding] =
    val paths = units.map(_.origin.javaPath).toSet
    refusals.toList.filter(f => paths.contains(f.origin.javaPath))
      .sortBy(f => (f.issue.toString, f.origin.javaPath, f.origin.line, f.subject))

object PublicFieldAccessorTransform:
  private val JavaCollectionsFqn = s"${RuntimeArtifact.Package}.JavaCollections"
  private val ToJavaValueFqn     = s"$JavaCollectionsFqn.Reified.toJavaValue"
  private val ObjectFqn          = "java.lang.Object"
  private val UnitFqn            = "scala.Unit"

  /** The JavaBeans capitalisation, not `capitalize`: a name whose first two characters are both
    * upper case keeps its spelling (`URL` -> `getURL`), matching what `Introspector.decapitalize`
    * inverts. */
  def beanSuffix(field: String): String =
    if field.isEmpty then field
    else if field.length > 1 && field.charAt(0).isUpper && field.charAt(1).isUpper then field
    else field.head.toUpper.toString + field.tail

  /** `java.beans.Introspector.decapitalize`, spelled out — reproduced rather than called since it
    * is the half `beanSuffix` is checked against. Not its inverse everywhere: they compose to the
    * identity except a `lowerUpper` name (see `invertible`). */
  def decapitalize(bean: String): String =
    if bean.isEmpty then bean
    else if bean.length > 1 && bean.charAt(0).isUpper && bean.charAt(1).isUpper then bean
    else bean.head.toLower.toString + bean.tail

  /** Does the round trip `decapitalize(beanSuffix(name)) == name` hold? A `lowerUpper` field like
    * `eMail` fails it (`getEMail` decapitalises to `EMail`), registering under a name nobody asks
    * for — refused rather than emitted as false coverage. */
  def invertible(field: String): Boolean = decapitalize(beanSuffix(field)) == field
