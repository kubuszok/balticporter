package balticporter.transform

import balticporter.core.{RequiresRuntime, RuntimeArtifact, SurfacePolicy}
import balticporter.tir.*

/** A JAVA `public` FIELD IS PART OF THE CLASS FILE'S PUBLIC SURFACE; A SCALA `var` IS NOT —
  * `ENGINE-LIMITS.md` K21 face 2.
  *
  * The source level is faithful and the class file is not. `class C { var a: Object }` emits a
  * PRIVATE field plus accessors named `a()` and `a_$eq()`, so `getClass.getFields` answers `[]` and
  * an accessor named `a()` is not a JavaBean `getA()`. Every framework that auto-detects a public
  * field or a bean property therefore sees NOTHING where java showed it a property.
  *
  * **And this is the face that does not throw.** Every property is absent, so every lookup is
  * `null`, and a library that coerces `null` to a default then answers correctly from data that is
  * not there — three assertions out of four, in the measured case. No compile error, no check
  * count, no exception: a suite is the only instrument that ever sees it, and it sees it once.
  *
  * ==What this phase does, and what it cannot==
  * It adds `getX()`/`setX(v)` beside the field, which is what a bean-convention framework reads.
  * **`getFields` still answers `[]`** — Scala 3 emits no JVM-public instance field for any
  * declaration form (probed: a class-body `var`, a `val`, and a `val` class parameter all emit a
  * private field plus accessors), so the port CANNOT be faithful at the class-file level and this
  * is the nearest expressible thing. A framework that reads FIELDS specifically is a limit, stated
  * in K21 rather than papered over.
  *
  * ==Why the getter BRIDGES, which is the half a call-site rule cannot reach==
  * K21 face 1 bridges a value the port hands OUT at an opaque slot. A reflective framework does not
  * only receive values — it CALLS BACK IN, through exactly the accessors this phase adds, and what
  * it gets back is whatever the field holds. A field typed `java.lang.Object` holding a collection
  * this port retyped hands the framework a `scala.collection.*` again, one hop past the bridge that
  * was supposed to have dealt with it. So the getter goes through the same run-time bridge, and for
  * the same reason: `Object` is all the static evidence there is. Measured at +3 tests over
  * `@BeanProperty`, whose generated getter cannot interpose anything.
  *
  * A field typed as anything else is returned as it is. That is exact for a primitive, a `String`
  * and a class the port emits; where the field's own type is one a retyping phase MOVED, the bean
  * property is a scala collection and this phase has no standing to say so (§4.56 — a phase may
  * only conclude from what IT did). No corpus port has such a field; K21 states the gap.
  *
  * ==Why it is a (b) and not a universal emission==
  * The FACT is universal. The REMEDY is not free: it adds two names per field to the emitted
  * surface, and `getX` collides with a java class that already declares one — which is a real
  * shape, not a hypothetical (a library whose `public ObjectMapper mapper` sits beside
  * `getMapper()`). Applied everywhere it would rewrite the surface of every port in the corpus to
  * fix the classes of the one that hands objects to a framework, and the reference hand ports emit
  * no such accessor anywhere. So it is scoped, `Only(Set.empty)` is the default and the no-op, and
  * a clash is REFUSED and counted rather than emitted as a compile error.
  */
final class PublicFieldAccessorTransform(
    /** WHICH declarations are read reflectively. `Only(Set.empty)` — the default — admits nothing,
      * which is §1(b)'s no-op with no code path. `Everywhere(Set.empty)` is the whole port, which
      * is the honest answer for a module whose classes are handed to a template engine or a
      * serialiser wholesale; the entries are matched by FQN and cut only at a `Symbol.fullName`
      * separator, so naming an enclosing type reaches its nested and ANONYMOUS classes — and an
      * anonymous class is the common shape here, since `new Inspectable() { public Date a; }` is
      * how a framework's caller writes one. */
    val scope: RuleScope = RuleScope.Only(Set.empty),
) extends Phase, RequiresRuntime, SurfacePolicy, PolicyBound:

  def name = "public-field->bean-accessors"

  /** the phase decides MEMBER NAMES in the emitted surface, which is `SurfacePolicy`'s case in its
    * plainest form: a base that exposes `getA` and a dependent that does not are two modules whose
    * signatures for one shared class disagree. */
  def surfaceFingerprint: String = scope.fingerprint

  /** the getter's bridge is `JavaCollections.Reified.toJavaValue`. Declared only where the scope can
    * admit something, so a port that leaves the phase in its pipeline unconfigured does not acquire
    * a runtime dependency it never uses. */
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

  /** `scala.Unit`, as this program spells it — a java setter returns `void`, and a `TypeRepr.NoType`
    * return renders `Any`, which is a bean setter no reader is looking at and a lie about what the
    * member does. */
  private var unitTpe: TypeRepr = TypeRepr.NoType

  /** every field this phase could NOT expose, and every type it was not asked about that has one —
    * [[PublicFieldAccessorTransform.exposure]]'s two rows. */
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
    objectSym = program.symbols.all.find(_.fullName == PublicFieldAccessorTransform.ObjectFqn)
      .map(_.id).getOrElse(SymId.None)
    // minted like every other reference into the runtime: nothing in a java program declares it,
    // and the emitter prints a minted symbol's `fullName` verbatim (§6 — fully qualified, no import).
    if scope.isUnrestricted || scope.entries.nonEmpty then
      toJavaValueSym = mint("toJavaValue", PublicFieldAccessorTransform.ToJavaValueFqn,
                            Flags(), SymId.None, TypeRepr.NoType)
    // …resolved where the program names it and minted where it does not, for the reason
    // `CollectionsTransform.named` gives: two symbols for one FQN print the same text and compare
    // unequal, which is how a later reader ends up asking about a type this run has twice.
    val unitSym = program.symbols.all.find(_.fullName == PublicFieldAccessorTransform.UnitFqn)
      .map(_.id).getOrElse(mint("Unit", PublicFieldAccessorTransform.UnitFqn, Flags(), SymId.None,
                                TypeRepr.NoType))
    unitTpe = TypeRepr.TypeRef(TypeRepr.NoPrefix, unitSym)
    given Program = program
    val units = program.units.map(u => StandardTraversal.mapClassDef(this, u))
    program.rebuilt(units, SymbolTable(program.symbols.all ++ added))

  /** a NAMED class — the ordinary case. */
  override def transformClassDef(t: Tree.ClassDef)(using Program): Tree.ClassDef =
    t.copy(body = exposed(t.symbol, t.body, t.origin))

  /** …and an ANONYMOUS one, which the traversal reaches through its `New` and never through
    * [[transformClassDef]] (see `StandardTraversal.mapTerm`). It is the SHAPE this phase exists for
    * — a framework's caller writes `new Inspectable() { public Date a = …; }` — so a phase that
    * only overrode the hook above would be a phase that does nothing on the measured case, with no
    * error anywhere (§3). */
  override def transformNew(t: Tree.New)(using Program): Term =
    t.copy(anon = t.anon.map(a => a.copy(body = exposed(a.symbol, a.body, a.origin))))

  /** the body of one class, plus whatever accessors its java-public fields need. Takes the three
    * things a `ClassDef` and an `AnonClass` both have and nothing else, because they are two node
    * types and the answer is one. */
  private def exposed(cls: SymId, body: List[Statement], origin: Origin)(using p: Program): List[Statement] =
    val owner = p.symbolOf(cls)
    if owner.isEmpty then body
    else if !scope.includes(p, owner.get) then
      // …NOT in scope, and it has java-public fields: the review list. Same shape and same reason as
      // `CollectionBoundaryCheck.Issue.OpaqueEgress` — one row per TYPE, because the question a
      // port answers is "is this class read reflectively?" and it is asked once per class.
      val fields = publicFields(body)
      if fields.nonEmpty then
        refusals += BeanExposureCheck.Finding(
          BeanExposureCheck.Issue.Unexposed, owner.get.fullName,
          fields.flatMap(v => p.symbolOf(v.symbol)).map(_.name).sorted.mkString(","), origin)
      body
    else
      val existing = memberNames(body)
      val extra = publicFields(body).flatMap { v =>
        val fs   = p.symbolOf(v.symbol).get
        val bean = PublicFieldAccessorTransform.beanSuffix(fs.name)
        val want = List("get" + bean, "set" + bean, "is" + bean)
        if want.exists(existing.contains) then
          // …a java class that declares BOTH a public field and its own accessor is ordinary, and
          // emitting a second `getX` is an error the port cannot recover from. `is` is refused
          // beside `get` because a bean reader that sees both reports a conflicting property, which
          // is a worse answer than the absent one this refusal leaves.
          refusals += BeanExposureCheck.Finding(
            BeanExposureCheck.Issue.NameTaken, MemberKey(owner.get.fullName, fs.name).render,
            want.filter(existing.contains).mkString(","), v.origin)
          Nil
        else accessors(cls, v, fs, bean)
      }
      if extra.isEmpty then body else body ++ extra

  /** the fields a java `public` modifier put on the class file's surface.
    *
    * INSTANCE fields only: a java `static` field is not a bean property and no bean reader looks
    * for one, so exposing it would be surface for nobody. And the flags are java's — exactly one of
    * the three access booleans is set, or the declaration is public (see `Flags`). */
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

  /** `def getX(): T` and, for a java NON-FINAL field, `def setX(v: T): Unit`.
    *
    * A `final` field is a `val` and has no setter to give — java had none either, and a bean reader
    * that only reads is served by the getter alone. */
  private def accessors(cls: SymId, v: Tree.ValDef, fs: Symbol, bean: String)(using p: Program): List[Statement] =
    val o    = v.origin
    val tpe  = v.tpt.tpe
    val self = Tree.This(cls, TypeRepr.ThisType(cls), o)
    val read = Tree.Select(self, fs.id, tpe, o)
    val body =
      // …the bridge, and ONLY where the field's type is the one that says nothing. See the class
      // doc: at any other type the value is what the field's type claims, and a cast back through
      // the bridge would be a claim this phase cannot make.
      if toJavaValueSym != SymId.None && objectSym != SymId.None &&
         headSym(tpe).contains(objectSym) then
        Tree.Apply(Tree.Ident(toJavaValueSym, TypeRepr.NoType, o), List(read), toJavaValueSym, tpe, o)
      else read
    val ownerFqn = p.symbolOf(cls).map(_.fullName).getOrElse("")
    val getSym = mint("get" + bean, MemberKey(ownerFqn, "get" + bean).render, Flags(), cls,
                      TypeRepr.MethodType(Nil, tpe))
    val getter = Tree.DefDef(getSym, List(Nil), TypeTree(tpe, o), Some(body), o)
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
          "nothing at all — these accessors are what a bean reader can see instead"),
      ),
      // …the KEY is the manifest entry VERBATIM where there is one (§4.575: it is the string an
      // agent edits). An UNRESTRICTED scope has no entry and its fingerprint is empty, so the key
      // names the declaration instead — `key=""` would tell a reader nothing at all.
      reason = Reason.Configured(name, scope.entryFor(fs.fullName).getOrElse(
        if scope.fingerprint.isEmpty then "scope (unrestricted)" else scope.fingerprint)),
      origin = o,
    ))
    getter :: setter

  private def headSym(t: TypeRepr): Option[SymId] = t match
    case TypeRepr.TypeRef(_, s)     => Some(s)
    case TypeRepr.AppliedType(c, _) => headSym(c)
    case _                          => scala.None

  /** what this run could not expose, and what it was never asked about — for [[BeanExposureCheck]].
    * Held to the units the run EMITS by its caller, exactly as every other boundary count is
    * (`ENGINE-LIMITS.md` D2). */
  def exposure(units: List[Tree.ClassDef]): List[BeanExposureCheck.Finding] =
    val paths = units.map(_.origin.javaPath).toSet
    refusals.toList.filter(f => paths.contains(f.origin.javaPath))
      .sortBy(f => (f.issue.toString, f.origin.javaPath, f.origin.line, f.subject))

object PublicFieldAccessorTransform:
  private val JavaCollectionsFqn = s"${RuntimeArtifact.Package}.JavaCollections"
  private val ToJavaValueFqn     = s"$JavaCollectionsFqn.Reified.toJavaValue"
  private val ObjectFqn          = "java.lang.Object"
  private val UnitFqn            = "scala.Unit"

  /** the JavaBeans capitalisation, which is NOT `capitalize`. A name whose first two characters are
    * both upper case keeps its spelling (`URL` → `getURL`), because that is what
    * `java.beans.Introspector.decapitalize` inverts and therefore what every bean reader will look
    * for. Getting it wrong emits an accessor the framework does not recognise — which is the defect
    * this phase exists to remove, one letter along. */
  def beanSuffix(field: String): String =
    if field.isEmpty then field
    else if field.length > 1 && field.charAt(0).isUpper && field.charAt(1).isUpper then field
    else field.head.toUpper.toString + field.tail
