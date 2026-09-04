package balticporter.core

import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest

/** Content digests + the persistent action cache (DESIGN.md §3.12).
  * One file per action key under `<dir>/aa/<key>`. Advisory: deletion must reproduce identical output. */
object Digest:
  def bytes(data: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(data).map(b => f"$b%02x").mkString

  def string(s: String): String = bytes(s.getBytes("UTF-8"))

  def file(p: Path): String = bytes(Files.readAllBytes(p))

  /** stable digest of a list of (label, digest) pairs. */
  def combined(parts: List[(String, String)]): String =
    string(parts.sortBy(_._1).map((k, v) => s"$k=$v").mkString("\n"))

final class ActionCache(dir: Path, enabled: Boolean):
  def get(key: String): Option[String] =
    if !enabled then None
    else
      val p = dir.resolve(key.take(2)).resolve(key)
      if Files.exists(p) then Some(Files.readString(p)) else None

  def put(key: String, value: String): Unit =
    if enabled then
      val p = dir.resolve(key.take(2)).resolve(key)
      Files.createDirectories(p.getParent)
      val tmp = Files.createTempFile(p.getParent, ".tmp", "")
      Files.writeString(tmp, value)
      Files.move(tmp, p, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

object EngineFingerprint:
  /** Digest of the engine's compiled classes. Any rule change invalidates the cache. */
  lazy val value: String =
    val cl = getClass.getClassLoader
    val roots = List(
      classOf[BUnit],                      // core
      Class.forName("balticporter.core.BirTransform$"),
    ).map(_.getProtectionDomain.getCodeSource)
      .flatMap(cs => Option(cs).map(_.getLocation.toURI))
      .distinct
    // include emit + frontend class dirs when locatable
    val extra = List("balticporter.emit.ScalaPrinter$", "balticporter.frontend.spoon.SpoonFrontend")
      .flatMap(n => scala.util.Try(Class.forName(n)).toOption)
      .flatMap(c => Option(c.getProtectionDomain.getCodeSource).map(_.getLocation.toURI))
    val dirs = (roots ++ extra).distinct.map(Path.of(_)).filter(Files.exists(_))
    if sys.env.contains("BP_DEBUG") then dirs.foreach(d => System.err.println(s"[engine-fp] $d"))
    val parts = dirs.flatMap { d =>
      if Files.isDirectory(d) then
        val classFiles = Files.walk(d).iterator()
        val buf = List.newBuilder[(String, String)]
        classFiles.forEachRemaining { p =>
          if p.toString.endsWith(".class") then buf += (d.relativize(p).toString -> Digest.file(p))
        }
        buf.result()
      else
        // a jar -- key by content, not path (sbt 2.0 per-run bg-jobs paths)
        List("jar:" + d.getFileName.toString -> Digest.file(d))
    }
    Digest.combined(parts.sortBy(_._1))

object UnitDeps:
  /** Project-internal FQCNs a unit depends on -- the dependency edge set for cache keys. */
  def of(unit: BUnit, projectFqcns: Set[String]): Set[String] =
    val acc = collection.mutable.Set[String]()
    def addT(t: BType): Unit = t match
      case BType.Ref(q, as) =>
        val top = q.split('$').head
        if projectFqcns.contains(top) then acc += top
        as.foreach(addT)
      case BType.Arr(e)     => addT(e)
      case BType.Wild(u, l) => u.foreach(addT); l.foreach(addT)
      case _                => ()
    val collect: BExpr => BExpr = { e =>
      e match
        case c: BExpr.Call    => c.ownerQ.foreach(q => addT(BType.Ref(q, Nil)))
        case n: BExpr.New     => addT(n.tpe)
        case BExpr.Typed(_, t)      => addT(t)
        case BExpr.Cast(t, _)       => addT(t)
        case BExpr.InstanceOf(_, t) => addT(t)
        case BExpr.ClassLit(t)      => addT(t)
        case BExpr.Ident(_, RefKind.StaticField(o)) => addT(BType.Ref(o, Nil))
        case _ => ()
      e
    }
    def walkType(t: BTypeDecl): Unit =
      t.superClass.foreach(addT)
      t.interfaces.foreach(addT)
      (t.fields ++ t.staticFields).foreach { f => addT(f.tpe); f.init.foreach(BirTransform.mapExpr(_)(collect)) }
      (t.methods ++ t.staticMethods).foreach { m =>
        addT(m.ret); m.params.foreach(p => addT(p.tpe))
        BirTransform.mapMethod(m)(collect)
      }
      t.ctors.foreach(c => { c.params.foreach(p => addT(p.tpe)); BirTransform.mapCtor(c)(collect) })
      (t.staticInit ++ t.instanceInit).foreach(BirTransform.mapStmt(_)(collect))
      t.nested.foreach(walkType)
    unit.types.foreach(walkType)
    acc.toSet

object InterfaceHash:
  /** Digest of a unit's exported signature surface -- the early-cutoff key. */
  def of(unit: BUnit): String =
    def tpe(t: BType): String = t match
      case BType.Prim(n)      => n
      case BType.Ref(q, as)   => q + as.map(tpe).mkString("<", ",", ">")
      case BType.Arr(e)       => tpe(e) + "[]"
      case BType.TVar(n)      => "$" + n
      case BType.Wild(u, l)   => "?" + u.map(tpe).getOrElse("") + l.map(tpe).getOrElse("")
    def sigsOf(t: BTypeDecl, path: String): List[String] =
      val here = s"$path/${t.name}"
      val ms = (t.methods ++ t.staticMethods).filter(_.mods.vis != Vis.Private).map { m =>
        s"$here.${m.name}(${m.params.map(p => tpe(p.tpe) + (if p.varargs then "..." else "")).mkString(",")}):${tpe(m.ret)}:${m.mods.isStatic}"
      }
      val fs = (t.fields ++ t.staticFields).filter(_.mods.vis != Vis.Private).map { f =>
        s"$here.${f.name}:${tpe(f.tpe)}:${f.mods.isStatic}:${f.mods.isFinal}"
      }
      val cs = t.ctors.map(c => s"$here.<init>(${c.params.map(p => tpe(p.tpe)).mkString(",")})")
      val es = t.enumCases.map(c => s"$here#${c.name}")
      ms ++ fs ++ cs ++ es ++ t.nested.flatMap(sigsOf(_, here))
    Digest.string(unit.types.flatMap(sigsOf(_, unit.pkg)).sorted.mkString("\n"))

/** Action-cache keys for TIR emission. Three-part key: engine fingerprint, canonical TIR digest,
  * dependencies' interface hashes (early cutoff). ADVISORY: whole-program decisions (clash renames,
  * ctor funnel, diamond overrides) can invalidate units with no reference edge. */
object TirCacheKey:
  import balticporter.tir.*

  /** the top-level unit a symbol belongs to, or `SymId.None` for an external. */
  def unitOf(program: Program): Map[SymId, SymId] =
    val units = program.units.map(_.symbol).toSet
    val memo  = collection.mutable.Map.empty[SymId, SymId]
    def climb(s: SymId, fuel: Int): SymId =
      if s == SymId.None || fuel == 0 then SymId.None
      else if units(s) then s
      else memo.getOrElseUpdate(s, program.symbolOf(s).map(sym => climb(sym.owner, fuel - 1)).getOrElse(SymId.None))
    program.symbols.all.map(s => s.id -> climb(s.id, 64)).toMap

  /** Every symbol named anywhere in a unit. Walked with `StandardTraversal`. */
  def referencedIn(program: Program, unit: Tree.ClassDef): Set[SymId] =
    given Program = program
    val acc = collection.mutable.Set.empty[SymId]
    val scan = new Phase:
      def name: String = "tir-cache-key/scan"
      override def transformTerm(t: Term)(using Program): Term =
        program.symbolIn(t).foreach(acc += _); t
      override def transformType(t: TypeRepr)(using Program): TypeRepr =
        t match
          case TypeRepr.TypeRef(_, s) => acc += s
          case TypeRepr.TermRef(_, s) => acc += s
          case _                      => ()
        t
    StandardTraversal.mapClassDef(scan, unit)
    acc.toSet

  /** Digest of a unit's non-private signature surface -- the early-cutoff key. */
  def interfaceHash(program: Program, unit: Tree.ClassDef): String =
    given Program = program
    val owner = unitOf(program)
    val sigs = program.symbols.all.toList
      .filter(s => owner.getOrElse(s.id, SymId.None) == unit.symbol && !s.flags.isPrivate)
      .map(s => s"${s.fullName}:${TirPrinter.tpe(s.info, TirPrinter.Style.canonical)}:${s.flags}")
      .sorted
    Digest.string(sigs.mkString("\n"))

  /** `unit symbol -> action key` for every unit given. Includes the decision log digest because
    * porter notes are in the emitted text but not in the tree. */
  def forUnits(program: Program, units: List[Tree.ClassDef], decisions: List[Decision] = Nil): Map[SymId, String] =
    given Program = program
    val owner  = unitOf(program)
    val byId   = program.units.map(u => u.symbol -> u).toMap
    val ifaces = program.units.map(u => u.symbol -> interfaceHash(program, u)).toMap
    val notes  = Digest.string(
      decisions.filter(d => PorterNote.Rendered(d.kind)).map(_.tsv).sorted.mkString("\n"))
    units.map { u =>
      val deps = referencedIn(program, u).flatMap(owner.get).filter(d => d != SymId.None && d != u.symbol)
      val parts =
        ("engine" -> EngineFingerprint.value) ::
          ("notes" -> notes) ::
          ("self" -> TirPrinter.digest(u)) ::
          // Upstream java digest: the emitter's recovery backstop reads the source directly.
          ("java" -> javaDigest(u.origin.javaPath)) ::
          deps.toList.flatMap(d => byId.get(d).map(_ => s"dep:${nameOf(program, d)}" -> ifaces.getOrElse(d, ""))).sorted
      u.symbol -> Digest.combined(parts)
    }.toMap

  /** Upstream file digest, memoized per path. `""` when the file does not exist. */
  private val javaDigests = collection.concurrent.TrieMap.empty[String, String]

  private def javaDigest(path: String): String =
    if path.isEmpty then ""
    else javaDigests.getOrElseUpdate(path, {
      val p = java.nio.file.Path.of(path)
      if java.nio.file.Files.isRegularFile(p) then
        try Digest.string(java.nio.file.Files.readString(p)) catch case _: Throwable => ""
      else ""
    })

  private def nameOf(program: Program, s: SymId): String =
    program.symbolOf(s).map(_.fullName).getOrElse(s.toString)
