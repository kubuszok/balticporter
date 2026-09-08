package balticporter.verify

import balticporter.tir.*
import balticporter.tir.Tree
import ApiParityCheck.SurfaceDecl

/** Derives SPELLING policy from a reference port's parsed surface (`ApiParityCheck.parseSurface`):
  * a java member is matched to the reference declaration at the same nesting path, name and
  * explicit arity, and the reference's spelling at each slot becomes a [[DerivedPolicy.Row]] —
  * an opaque slot, a nullable member, a parenless accessor. Signatures only: a row can never
  * describe behaviour. `PROGRESS.md` §13.31 step 1; the mechanism is §1(b), the tree its parameter. */
object ReferencePolicy:

  val LaneRows      = "derived(rows)"
  val LaneAmbiguous = "derived(ambiguous)"
  val LaneUnmatched = "derived(unmatched)"
  val Lanes: List[String] = List(LaneRows, LaneAmbiguous, LaneUnmatched)

  private val PrimitiveFqns = Set("scala.Int", "scala.Float", "scala.Long", "scala.Double",
                                  "scala.Short", "scala.Byte", "scala.Boolean", "scala.Char")
  private val NullWrappers  = Set("Nullable", "Option")

  final case class Result(policy: DerivedPolicy, findings: List[CheckReport.Finding])

  /** @param emitted the units this run emits (a base's units are never derived over, K51)
    * @param typeRenames upstream dotted FQN -> emitted simple name (`PortManifest.effectiveTypeRenames`)
    * @param flattenNestedTypes upstream nested FQNs emitted top-level
    * @param opaqueTargets target type FQNs of the deriving `OpaqueSpec`s (`OpaqueSpec.typeFqn`) */
  /** every reference path a java class may sit at: a static nested type lands in the hand port's
    * class OR its companion (`/Outer/Inner`, `/Outer$/Inner`), so each enclosing segment is tried
    * both ways; a java STATIC member is read under the innermost companion (`/Outer$`). */
  def classPaths(cls: Symbol, typeRenames: Map[String, String], flattenNestedTypes: Set[String]): List[String] =
    val fqn = cls.fullName
    val top = fqn.takeWhile(_ != '$')
    val nested = if fqn.length > top.length then fqn.drop(top.length + 1).split('$').toList.filter(_.nonEmpty) else Nil
    val topName = typeRenames.getOrElse(top, top.split('.').last)
    // a PROMOTED nested type is read at its own (possibly renamed) top-level name
    val segs = if nested.nonEmpty && flattenNestedTypes.contains(fqn) then List(typeRenames.getOrElse(fqn, nested.last)) else topName :: nested
    val enclosing = segs.dropRight(1)
    val variants = enclosing.foldLeft(List(List.empty[String])) { (acc, seg) =>
      acc.flatMap(pre => List(pre :+ seg, pre :+ (seg + "$")))
    }
    variants.map(pre => (pre :+ segs.last).map("/" + _).mkString)

  def derive(program: Program, reference: List[SurfaceDecl], emitted: Set[SymId],
             typeRenames: Map[String, String], flattenNestedTypes: Set[String],
             opaqueTargets: Set[String], packageRenames: Map[String, String] = Map.empty,
             /** the port's configured member renames (`C#m` -> new name): a java `len` the port spells
               * `length` is read at the reference's `length` */
             memberRenames: Map[String, String] = Map.empty): Result =
    // a declaration with trailing DEFAULTS answers every arity down to its required ones
    val refByKey = reference.flatMap(d => (0 to d.defaults).map(k => key(d.path, kindClass(d.kind), d.name, d.explicitArity - k) -> d))
      .groupBy(_._1).map((k, vs) => k -> vs.map(_._2).distinct)
    val refPaths = reference.map(_.path).toSet
    /** the emitted package of a java class, by the manifest's longest-prefix package rename. */
    def emittedPkg(javaFqn: String): String =
      val pkg = javaFqn.takeWhile(_ != '$').split('.').dropRight(1).mkString(".")
      packageRenames.toList.sortBy(-_._1.length).find((from, _) => pkg == from || pkg.startsWith(from + "."))
        .map((from, to) => to + pkg.drop(from.length)).getOrElse(pkg)
    /** candidates at a key, the reference's PACKAGE agreeing where any candidate's does — two
      * `ParticleEffect`s (g2d, g3d.particles) share one path and must not answer for each other. */
    def lookup(paths: List[String], kc: String, name: String, arity: Int, pkg: String): Option[List[SurfaceDecl]] =
      val all = paths.flatMap(p => refByKey.getOrElse(key(p, kc, name, arity), Nil))
      if all.isEmpty then scala.None
      else
        val same = all.filter(_.pkg == pkg)
        Some(if same.nonEmpty then same else all)
    /** a java accessor pair is ONE property in the hand port (the bean step's own convention): a
      * getter `getX()`/`isX()` is also read at `x` (a `def` or a `val`/`var`), a setter `setX(v)` at
      * `x_=` or the `var x` — the property's type is the slot's. */
    /** the accessor's remainder as java wrote it (`GL30Available` for `isGL30Available`). */
    def propertyRaw(name: String): Option[String] =
      if name.length > 3 && name.startsWith("get") && name(3).isUpper then Some(name.drop(3))
      else if name.length > 2 && name.startsWith("is") && name(2).isUpper then Some(name.drop(2))
      else if name.length > 3 && name.startsWith("set") && name(3).isUpper then Some(name.drop(3))
      else scala.None
    def decap(s: String) = if s.nonEmpty && s.head.isUpper then s.head.toLower + s.tail else s
    def propertyName(name: String): Option[String] = propertyRaw(name).map(decap)
    /** `GL30Available` -> `gl30Available`, `URLPath` -> `urlPath`: a leading acronym lowered whole,
      * keeping its last letter where the next word begins with one (the hand port's casing). */
    def lowerAcronym(s: String): String =
      val run = s.takeWhile(_.isUpper).length
      if run < 2 then s
      else if s.length > run && s(run).isLower then s.take(run - 1).toLowerCase + s.drop(run - 1)
      else s.take(run).toLowerCase + s.drop(run)
    def lookupMethod(paths: List[String], name: String, arity: Int, pkg: String): Option[List[SurfaceDecl]] =
      lookup(paths, "def", name, arity, pkg).orElse(propertyRaw(name).flatMap(raw =>
        List(decap(raw), lowerAcronym(raw)).distinct.iterator.map(prop => lookupProp(paths, name, arity, pkg, prop)).collectFirst { case Some(x) => x }))
    def lookupProp(paths: List[String], name: String, arity: Int, pkg: String, prop: String): Option[List[SurfaceDecl]] =
      Some(prop).flatMap { prop =>
        if arity == 0 && !name.startsWith("set") then
          lookup(paths, "def", prop, 0, pkg).orElse(lookup(paths, "prop", prop, 0, pkg))
        else if arity == 1 && name.startsWith("set") then
          lookup(paths, "def", prop + "_=", 1, pkg).orElse(lookup(paths, "prop", prop, 0, pkg))
            .orElse(lookup(paths, "def", prop, 1, pkg))
        // an indexed getter keeps the property name with its index: `getX(pointer)` is `x(pointer)`
        else if !name.startsWith("set") then lookup(paths, "def", prop, arity, pkg)
        else scala.None
      }
    // a target's simple name is what the reference WRITES; two targets sharing one are unreadable
    val targetBySimple: Map[String, String] =
      opaqueTargets.groupBy(_.split('.').last).collect { case (k, vs) if vs.size == 1 => k -> vs.head }
    val rows     = List.newBuilder[DerivedPolicy.Row]
    val findings = List.newBuilder[CheckReport.Finding]

    def finding(lane: String, kind: String, s: Symbol, detail: String): CheckReport.Finding =
      CheckReport.Finding(lane, kind, s.fullName, s.origin.javaPath, s.origin.line, detail)

    /** every reference path a java class may sit at: a static nested type lands in the hand port's
      * class OR its companion (`/Outer/Inner`, `/Outer$/Inner`), so each enclosing segment is tried
      * both ways; a java STATIC member is read under the innermost companion (`/Outer$`). */
    def classPaths(cls: Symbol): List[String] = ReferencePolicy.classPaths(cls, typeRenames, flattenNestedTypes)
    def memberPaths(paths: List[String], static: Boolean): List[String] =
      if static then paths.map(_ + "$") else paths

    def prim(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeRef(_, sym) => program.symbolOf(sym).exists(s => PrimitiveFqns(s.fullName))
      case _                        => false
    def isVoid(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeRef(_, sym) => program.symbolOf(sym).exists(_.fullName == "scala.Unit")
      case TypeRepr.NoType          => true
      case _                        => false
    def reference_(t: TypeRepr): Boolean = t match
      case TypeRepr.TypeRef(_, sym) => program.symbolOf(sym).exists(s => !PrimitiveFqns(s.fullName) && s.fullName != "scala.Unit")
      case _: TypeRepr.AppliedType  => true
      case _                        => false
    def simpleOf(refType: String): String =
      refType.takeWhile(c => c != '[' && c != ' ' && c != '|').split('.').last
    /** the emitted simple name of a java type, through the manifest's renames (retargets included). */
    def javaSimple(t: TypeRepr): Option[String] = t match
      case TypeRepr.TypeRef(_, s)      => program.symbolOf(s).map(sym => typeRenames.getOrElse(sym.fullName, sym.name))
      case TypeRepr.AppliedType(t2, _) => javaSimple(t2)
      case _                           => scala.None
    def refSimple(refType: String): String =
      val stripped = NullWrappers.foldLeft(refType.trim)((acc, w) =>
        if acc.startsWith(w + "[") && acc.endsWith("]") then acc.drop(w.length + 1).dropRight(1) else acc)
      simpleOf(stripped)
    /** among several reference candidates at one key, those whose parameter types match java's best
      * (`BitmapFont(data, Array<TextureRegion>, boolean)` against the primary taking a `DynamicArray`
      * and a secondary taking a `TextureRegion`); a tie keeps them all and agreement decides. */
    /** is this java parameter typed by a TYPE PARAMETER (of the method or its class)? The parity
      * parser canonicalises those to `$0`, `$1`… on the reference side. */
    def isTypeParamTyped(p: Tree.ValDef, tparams: Set[SymId]): Boolean = p.tpt.tpe match
      case TypeRepr.TypeRef(_, s) => tparams(s)
      case _                      => false
    def bestByParams(cands: List[SurfaceDecl], params: List[Tree.ValDef], tparams: Set[SymId] = Set.empty): List[SurfaceDecl] =
      if cands.size <= 1 then cands
      else
        val js = params.map(p => javaSimple(p.tpt.tpe))
        val tp = params.map(isTypeParamTyped(_, tparams))
        val scored = cands.map { c =>
          val rs = c.explicitParamTypes.map(refSimple)
          c -> js.zip(rs).zip(tp).count {
            case ((_, r), true) if r.startsWith("$") => true   // a type parameter matches the canonical `$N`
            case ((Some(j), r), _)                   => j == r
            case _                                   => false
          }
        }
        val top = scored.map(_._2).max
        scored.filter(_._2 == top).map(_._1)
    def nullWrapped(refType: String): Boolean =
      NullWrappers.exists(w => refType.startsWith(w + "[")) || refType.endsWith("| Null") || refType.endsWith("|Null")

    /** rows every candidate agrees on; disagreement is a counted ambiguity, never a guess. */
    def agree(s: Symbol, cands: List[SurfaceDecl], per: SurfaceDecl => List[DerivedPolicy.Row]): Unit =
      val each = cands.map(per(_).toSet)
      val common = each.reduce(_ intersect _)
      val union  = each.reduce(_ union _)
      common.toList.sortBy(_.upstream).foreach(rows += _)
      if union != common then
        findings += finding(LaneAmbiguous, "OverloadDisagrees", s,
          s"${cands.size} reference declarations at one key derive different spellings; only the agreed rows kept")

    /** the key a row is written under: descriptor-qualified where the java NAME is overloaded in
      * its class, so one overload's spelling never reaches another (`DerivedPolicy.keysOf`). */
    def rowKey(ms: Symbol, overloaded: Boolean): String =
      if overloaded then ms.descriptor.map(d => ms.fullName + "(" + d.render + ")").getOrElse(ms.fullName) else ms.fullName
    def paramKey(ms: Symbol, ps: Symbol, overloaded: Boolean): String =
      if overloaded then rowKey(ms, true) + "#" + ps.name else ps.fullName

    def methodRows(d: Tree.DefDef, ms: Symbol, overloaded: Boolean, r: SurfaceDecl): List[DerivedPolicy.Row] =
      val out = List.newBuilder[DerivedPolicy.Row]
      val params = d.paramss.flatten
      val isProp = kindClass(r.kind) == "prop"
      val refParamTypes = if isProp && params.size == 1 then List(r.resultType) else r.explicitParamTypes
      params.zip(refParamTypes).foreach { (p, refType) =>
        program.symbolOf(p.symbol).foreach { ps =>
          targetBySimple.get(simpleOf(refType)).filter(_ => prim(p.tpt.tpe))
            .foreach(t => out += DerivedPolicy.Row(DerivedPolicy.Family.OpaqueSlot, paramKey(ms, ps, overloaded), refType, t))
          if nullWrapped(refType) && reference_(p.tpt.tpe) then
            out += DerivedPolicy.Row(DerivedPolicy.Family.NullableMember, paramKey(ms, ps, overloaded), refType)
        }
      }
      val res = d.returnTpt.tpe
      // a setter read at the reference's `var`: its type is the PARAMETER's, not the setter's result
      val setterAtProp = isProp && params.size == 1
      if !setterAtProp then
        targetBySimple.get(simpleOf(r.resultType)).filter(_ => prim(res))
          .foreach(t => out += DerivedPolicy.Row(DerivedPolicy.Family.OpaqueSlot, rowKey(ms, overloaded), r.resultType, t))
        if nullWrapped(r.resultType) && reference_(res) then
          out += DerivedPolicy.Row(DerivedPolicy.Family.NullableMember, rowKey(ms, overloaded), r.resultType)
      if !isProp && r.parenless && params.isEmpty && !isVoid(res) && !ms.flags.isStatic then
        out += DerivedPolicy.Row(DerivedPolicy.Family.Parenless, rowKey(ms, overloaded), s"def ${r.name}: ${r.resultType}")
      // java declares it narrower than public, the reference ships it public: widened
      if r.accessLevel == "public" && (ms.flags.isProtected || ms.flags.isPackagePrivate) then
        out += DerivedPolicy.Row(DerivedPolicy.Family.Public, rowKey(ms, overloaded), s"public ${r.name}")
      // the reference gives the member a JVM name of its own (`@targetName("addLabel")`)
      if r.targetName.nonEmpty then
        out += DerivedPolicy.Row(DerivedPolicy.Family.TargetName, rowKey(ms, overloaded), s"@targetName ${r.targetName}", r.targetName)
      // the reference KEEPS the parens (`def size(): Int`): the detector must not drop them
      if !isProp && !r.parenless && params.isEmpty && !isVoid(res) && !ms.flags.isStatic then
        out += DerivedPolicy.Row(DerivedPolicy.Family.KeepParens, rowKey(ms, overloaded), s"def ${r.name}(): ${r.resultType}")
      out.result()

    /** the parameter slots only — a constructor has no result and no arity to drop. */
    def paramRows(d: Tree.DefDef, ms: Symbol, overloaded: Boolean, r: SurfaceDecl): List[DerivedPolicy.Row] =
      val out = List.newBuilder[DerivedPolicy.Row]
      d.paramss.flatten.zip(r.explicitParamTypes).foreach { (p, refType) =>
        program.symbolOf(p.symbol).foreach { ps =>
          targetBySimple.get(simpleOf(refType)).filter(_ => prim(p.tpt.tpe))
            .foreach(t => out += DerivedPolicy.Row(DerivedPolicy.Family.OpaqueSlot, paramKey(ms, ps, overloaded), refType, t))
          if nullWrapped(refType) && reference_(p.tpt.tpe) then
            out += DerivedPolicy.Row(DerivedPolicy.Family.NullableMember, paramKey(ms, ps, overloaded), refType)
        }
      }
      out.result()

    def fieldRows(v: Tree.ValDef, fs: Symbol, r: SurfaceDecl): List[DerivedPolicy.Row] =
      val out = List.newBuilder[DerivedPolicy.Row]
      targetBySimple.get(simpleOf(r.resultType)).filter(_ => prim(v.tpt.tpe))
        .foreach(t => out += DerivedPolicy.Row(DerivedPolicy.Family.OpaqueSlot, fs.fullName, r.resultType, t))
      if nullWrapped(r.resultType) && reference_(v.tpt.tpe) then
        out += DerivedPolicy.Row(DerivedPolicy.Family.NullableMember, fs.fullName, r.resultType)
      out.result()

    program.units.filter(u => emitted(u.symbol)).foreach { unit =>
      StandardTraversal.allClassDefs(unit)(using program).foreach { cd =>
        program.symbolOf(cd.symbol).foreach { cls =>
          val paths = classPaths(cls)
          val pkg   = emittedPkg(cls.fullName)
          val declaredHere = paths.exists(p => refPaths(p) || refPaths(p + "$"))
          if !declaredHere then
            findings += finding(LaneUnmatched, "NoReferenceType", cls, s"no reference declaration at `${paths.head}`")
          else
            // which java NAMES are overloaded here: their rows are keyed by descriptor
            val nameCounts = cd.body.collect { case d: Tree.DefDef => program.symbolOf(d.symbol).map(_.name) }.flatten
              .groupBy(identity).view.mapValues(_.size).toMap
            cd.body.foreach {
              case d: Tree.DefDef =>
                program.symbolOf(d.symbol).filter(s => !s.name.contains('$') && s.name != "<init>").foreach { ms =>
                  val overloaded = nameCounts.getOrElse(ms.name, 0) > 1
                  val n = d.paramss.flatten.size
                  val mp = memberPaths(paths, ms.flags.isStatic)
                  // the reference keeps the java accessor NAME as a def AND spells no property for it:
                  // the bean step must not fold it (a hand port keeping BOTH — `continuousRendering_=`
                  // beside a `setContinuousRendering` forwarder — still folds; the forwarder is an extra)
                  // PER ACCESSOR: a setter is kept when the reference spells `setX` and no `x_=`; a
                  // getter when it spells `getX`/`isX` and no `x` (a hand port keeps `setContinuousRendering`
                  // beside a parenless `continuousRendering` getter — the pair folds on the getter side only)
                  propertyName(ms.name).foreach { prop =>
                    val keptName = lookup(mp, "def", ms.name, n, pkg).isDefined
                    val isSetter = ms.name.startsWith("set") && n == 1
                    // a twin at the property name must be the SAME slot: `var minWidth: Nullable[Value]`
                    // beside `def getMinWidth: Float` is a field the reference exposes, not the getter
                    // folded — its type disagrees, so java's name is kept
                    val javaRes = javaSimple(d.returnTpt.tpe).getOrElse("")
                    def sameSlot(cands: Option[List[SurfaceDecl]]): Boolean =
                      cands.exists(_.exists(rd => n == 1 || javaRes.isEmpty || refSimple(rd.resultType) == javaRes))
                    val twin =
                      if isSetter then lookup(mp, "def", prop + "_=", 1, pkg).isDefined || lookup(mp, "prop", prop, 0, pkg).isDefined
                      else sameSlot(lookup(mp, "def", prop, n, pkg)) || (n == 0 && sameSlot(lookup(mp, "prop", prop, 0, pkg)))
                    if keptName && !twin then
                      rows += DerivedPolicy.Row(DerivedPolicy.Family.KeepName, rowKey(ms, overloaded), s"def ${ms.name}")
                  }
                  // the reference spells the accessor as a PROPERTY (`var x`) and has no def of the java name:
                  // the pair folds as if configured (the fluent setters a detector refuses included)
                  propertyName(ms.name).foreach { prop =>
                    // the reference spells the pair as a property: a `var`/`val`, or a parenless `def x`
                    // with a `def x_=` — either way the pair folds as if configured (a setter with
                    // behaviour included: the reference made it a property)
                    val javaNameGone = lookup(mp, "def", ms.name, n, pkg).isEmpty
                    val refProp = lookup(mp, "prop", prop, 0, pkg).isDefined
                    val refDefGetter = lookup(mp, "def", prop, 0, pkg).exists(_.exists(_.parenless))
                    val refDefSetter = lookup(mp, "def", prop + "_=", 1, pkg).isDefined
                    val asProp = javaNameGone && (refProp ||
                      (n == 0 && !ms.name.startsWith("set") && refDefGetter) ||
                      (n == 1 && ms.name.startsWith("set") && refDefSetter))
                    if asProp && n == 0 && !ms.name.startsWith("set") then
                      rows += DerivedPolicy.Row(DerivedPolicy.Family.Property, rowKey(ms, overloaded), s"var $prop", prop)
                    // a getter WITH parameters the reference spells under the property name, same
                    // parameter types (`x(pointer)` for `getX(int)`): a rename, not a property
                    else if javaNameGone && n >= 1 && !ms.name.startsWith("set") then
                      val acr = propertyRaw(ms.name).map(lowerAcronym).getOrElse(prop)
                      List(prop, acr).distinct.iterator
                        .map(nm => nm -> lookup(mp, "def", nm, n, pkg).toList.flatten)
                        .map((nm, cs) => nm -> bestByParams(cs, d.paramss.flatten, d.tparams.map(_.symbol).toSet))
                        .find(_._2.nonEmpty)
                        .foreach((nm, _) => rows += DerivedPolicy.Row(DerivedPolicy.Family.Rename, rowKey(ms, overloaded), s"def $nm", nm))
                    else if asProp && n == 1 && ms.name.startsWith("set") then
                      rows += DerivedPolicy.Row(DerivedPolicy.Family.PropertySetter, rowKey(ms, overloaded), s"var $prop", prop)
                    // the reference spells the property with its acronym LOWERED (`gl30Available`): the
                    // pair folds to that name — a row carrying the target, since the detector's own
                    // spelling keeps the acronym
                    val acr = propertyRaw(ms.name).map(lowerAcronym).getOrElse(prop)
                    if acr != prop && lookup(mp, "def", ms.name, n, pkg).isEmpty then
                      val refHas = (kind: String, nm: String, a: Int) => lookup(mp, kind, nm, a, pkg).isDefined
                      if n == 0 && !ms.name.startsWith("set") && (refHas("def", acr, 0) || refHas("prop", acr, 0)) then
                        rows += DerivedPolicy.Row(DerivedPolicy.Family.Property, rowKey(ms, overloaded), s"def $acr", acr)
                      else if n == 1 && ms.name.startsWith("set") && (refHas("def", acr + "_=", 1) || refHas("prop", acr, 0)) then
                        rows += DerivedPolicy.Row(DerivedPolicy.Family.PropertySetter, rowKey(ms, overloaded), s"def $acr", acr)
                  }
                  val renamedTo = memberRenames.get(ms.fullName).orElse(ms.descriptor.flatMap(dd => memberRenames.get(ms.fullName + "(" + dd.render + ")")))
                  lookupMethod(mp, ms.name, n, pkg).orElse(renamedTo.flatMap(lookupMethod(mp, _, n, pkg))) match
                    case Some(cands) =>
                      val tps = d.tparams.map(_.symbol).toSet ++ cd.tparams.map(_.symbol).toSet
                      agree(ms, bestByParams(cands, d.paramss.flatten, tps), methodRows(d, ms, overloaded, _))
                    case None =>
                      // a `Class<T>` parameter the reference turned into a `[T: ClassTag]` bound: its def
                      // sits at the java name (or the renamed one) one parameter short, the tag in its
                      // type parameters or using clause
                      val classParams = d.paramss.flatten.count(p => p.tpt.tpe match
                        case TypeRepr.AppliedType(TypeRepr.TypeRef(_, c), List(TypeRepr.TypeRef(_, t))) =>
                          program.symbolOf(c).exists(_.fullName == "java.lang.Class") && d.tparams.exists(_.symbol == t)
                        case _ => false)
                      if classParams > 0 then
                        val tagged = (ms.name :: renamedTo.toList).flatMap(nm => lookup(mp, "def", nm, n - classParams, pkg).toList.flatten)
                          .filter(rd => rd.typeParams.contains("ClassTag"))
                        if tagged.nonEmpty then
                          rows += DerivedPolicy.Row(DerivedPolicy.Family.ClassTagParam, rowKey(ms, overloaded),
                            s"def ${tagged.head.name}${tagged.head.typeParams}")
                }
                // a java constructor: the reference's constructors at the class, or its companion's
                // `apply` (the hand port's factory spelling) — parameter slots only
                program.symbolOf(d.symbol).filter(_.name == "<init>").foreach { ms =>
                  val n = d.paramss.flatten.size
                  val ctors  = paths.flatMap(p => refByKey.getOrElse(key(p, "ctor", "<init>", n), Nil))
                  val applys = memberPaths(paths, static = true).flatMap(p => refByKey.getOrElse(key(p, "def", "apply", n), Nil))
                  val cands  = (ctors ++ applys) match
                    case Nil => Nil
                    case all => val same = all.filter(_.pkg == pkg); if same.nonEmpty then same else all
                  if cands.nonEmpty then
                    val best = bestByParams(cands, d.paramss.flatten)
                    agree(ms, best, paramRows(d, ms, nameCounts.getOrElse("<init>", 0) > 1, _))
                    if best.forall(_.accessLevel == "public") && (ms.flags.isProtected || ms.flags.isPackagePrivate) then
                      rows += DerivedPolicy.Row(DerivedPolicy.Family.Public, rowKey(ms, nameCounts.getOrElse("<init>", 0) > 1), "public <init>")
                }
              case v: Tree.ValDef =>
                program.symbolOf(v.symbol).filter(s => !s.name.contains('$')).foreach { fs =>
                  val fmp = memberPaths(paths, fs.flags.isStatic)
                  lookup(fmp, "prop", fs.name, 0, pkg) match
                    case Some(cands) => agree(fs, cands, fieldRows(v, fs, _))
                    case None        =>
                      // the reference moved the field under an underscore name (`_fillX`), leaving
                      // java's name to the property: the field follows, and its type rows read there
                      lookup(fmp, "prop", "_" + fs.name, 0, pkg).foreach { cands =>
                        val fieldKey = fs.fullName + ":field"
                        agree(fs, cands, r => fieldRows(v, fs, r).map(_.copy(upstream = fieldKey)) :+
                          DerivedPolicy.Row(DerivedPolicy.Family.FieldName, fieldKey, s"${r.kind} _${fs.name}", "_" + fs.name))
                      }
                }
              case _ => ()
            }
        }
      }
    }
    val all = rows.result().distinct
    val rowFindings = all.map(r => CheckReport.Finding(LaneRows, r.family.toString, r.upstream, "", 0,
      if r.target.isEmpty then r.reference else s"${r.reference} -> ${r.target}"))
    Result(DerivedPolicy(all), rowFindings ++ findings.result())

  private def kindClass(kind: String): String = kind match
    case "val" | "var" | "param" => "prop"
    case other                    => other  // `def`, `ctor`, the type kinds

  private def key(path: String, kc: String, name: String, arity: Int): String = s"$path|$kc|$name/$arity"
