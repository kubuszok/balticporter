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
  def derive(program: Program, reference: List[SurfaceDecl], emitted: Set[SymId],
             typeRenames: Map[String, String], flattenNestedTypes: Set[String],
             opaqueTargets: Set[String], packageRenames: Map[String, String] = Map.empty): Result =
    val refByKey = reference.groupBy(d => key(d.path, kindClass(d.kind), d.name, d.explicitArity))
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
    def propertyName(name: String): Option[String] =
      def decap(s: String) = if s.nonEmpty && s.head.isUpper then s.head.toLower + s.tail else s
      if name.length > 3 && name.startsWith("get") && name(3).isUpper then Some(decap(name.drop(3)))
      else if name.length > 2 && name.startsWith("is") && name(2).isUpper then Some(decap(name.drop(2)))
      else if name.length > 3 && name.startsWith("set") && name(3).isUpper then Some(decap(name.drop(3)))
      else scala.None
    def lookupMethod(paths: List[String], name: String, arity: Int, pkg: String): Option[List[SurfaceDecl]] =
      lookup(paths, "def", name, arity, pkg).orElse(propertyName(name).flatMap { prop =>
        if arity == 0 && !name.startsWith("set") then
          lookup(paths, "def", prop, 0, pkg).orElse(lookup(paths, "prop", prop, 0, pkg))
        else if arity == 1 && name.startsWith("set") then
          lookup(paths, "def", prop + "_=", 1, pkg).orElse(lookup(paths, "prop", prop, 0, pkg))
            .orElse(lookup(paths, "def", prop, 1, pkg))
        // an indexed getter keeps the property name with its index: `getX(pointer)` is `x(pointer)`
        else if !name.startsWith("set") then lookup(paths, "def", prop, arity, pkg)
        else scala.None
      })
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
    def classPaths(cls: Symbol): List[String] =
      val fqn = cls.fullName
      val top = fqn.takeWhile(_ != '$')
      val nested = if fqn.length > top.length then fqn.drop(top.length + 1).split('$').toList.filter(_.nonEmpty) else Nil
      val topName = typeRenames.getOrElse(top, top.split('.').last)
      val segs = if nested.nonEmpty && flattenNestedTypes.contains(fqn) then List(nested.last) else topName :: nested
      val enclosing = segs.dropRight(1)
      val variants = enclosing.foldLeft(List(List.empty[String])) { (acc, seg) =>
        acc.flatMap(pre => List(pre :+ seg, pre :+ (seg + "$")))
      }
      variants.map(pre => (pre :+ segs.last).map("/" + _).mkString)
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
      targetBySimple.get(simpleOf(r.resultType)).filter(_ => prim(res))
        .foreach(t => out += DerivedPolicy.Row(DerivedPolicy.Family.OpaqueSlot, rowKey(ms, overloaded), r.resultType, t))
      if nullWrapped(r.resultType) && reference_(res) then
        out += DerivedPolicy.Row(DerivedPolicy.Family.NullableMember, rowKey(ms, overloaded), r.resultType)
      if !isProp && r.parenless && params.isEmpty && !isVoid(res) && !ms.flags.isStatic then
        out += DerivedPolicy.Row(DerivedPolicy.Family.Parenless, rowKey(ms, overloaded), s"def ${r.name}: ${r.resultType}")
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
                  lookupMethod(memberPaths(paths, ms.flags.isStatic), ms.name, d.paramss.flatten.size, pkg) match
                    case Some(cands) => agree(ms, cands, methodRows(d, ms, overloaded, _))
                    case None        => ()
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
                  if cands.nonEmpty then agree(ms, cands, paramRows(d, ms, nameCounts.getOrElse("<init>", 0) > 1, _))
                }
              case v: Tree.ValDef =>
                program.symbolOf(v.symbol).filter(s => !s.name.contains('$')).foreach { fs =>
                  lookup(memberPaths(paths, fs.flags.isStatic), "prop", fs.name, 0, pkg) match
                    case Some(cands) => agree(fs, cands, fieldRows(v, fs, _))
                    case None        => ()
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
