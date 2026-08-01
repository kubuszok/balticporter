package balticporter.core

import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest

/** Content digests + the persistent action cache (DESIGN.md §3.12).
  *
  * One file per action key under `<dir>/aa/<key>`; values are the emitted unit
  * sources. Writes are atomic (temp + rename). The cache is advisory: deleting the
  * directory or running with the no-cache flag must reproduce byte-identical output.
  */
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
  /** Digest of the engine's own compiled classes — any rule change invalidates the
    * cache (the coarse, sound tier from DESIGN.md §3.12). Computed once per run.
    */
  lazy val value: String =
    val cl = getClass.getClassLoader
    val roots = List(
      classOf[BUnit],                      // core
      Class.forName("balticporter.core.BirTransform$"),
    ).map(_.getProtectionDomain.getCodeSource)
      .flatMap(cs => Option(cs).map(_.getLocation.toURI))
      .distinct
    // include emit + frontend class dirs when locatable via well-known classes
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
        // a jar — sbt 2.0 forked runs materialize module jars under per-run
        // bg-jobs paths, so the key must be path-independent (content decides)
        List("jar:" + d.getFileName.toString -> Digest.file(d))
    }
    Digest.combined(parts.sortBy(_._1))

object UnitDeps:
  /** project-internal fqcns a unit's translation depends on (resolved owners and
    * type references) — the dependency edge set for cache keys. */
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
  /** Digest of a unit's exported signature surface — the early-cutoff key
    * (mypy's interface-hash trick): dependents re-key only when signatures move.
    */
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

// ---------------------------------------------------------------------------
// The same three-part key, on the TIR.
// ---------------------------------------------------------------------------

/** Action-cache keys for TIR emission — the TIR counterpart of [[UnitDeps]] + [[InterfaceHash]].
  *
  * The BIR path has had incremental keys since M0 and the TIR path has had none, which is half of
  * why "there are two engines and each has half of what a consumer needs" (PROGRESS.md
  * §Publishability item 1.1). The key has the same three parts, for the same reasons:
  *
  *   - the ENGINE fingerprint ([[EngineFingerprint]]) — any rule change invalidates everything;
  *   - the unit's OWN content — here the canonical TIR render rather than the Java source digest,
  *     because on this path the tree is what emission consumes, and it has already been through
  *     the phases. `TirPrinter.Style.canonical` leaks no `SymId` and no `Origin`, so the digest is
  *     stable across interning order and across checkouts;
  *   - its dependencies' INTERFACE hashes — mypy's early cutoff. A method BODY changing in `A`
  *     re-emits `A` alone; a SIGNATURE changing in `A` re-emits `A` and everything that names it.
  *
  * ==What this key is NOT sound against==
  * Emission is not a per-unit function in one respect: [[balticporter.emit.TirEmitter]] computes
  * member-clash renames, the constructor funnel and diamond overrides over the WHOLE program in
  * its constructor. Those are whole-program decisions, and a change to a unit `U` that alters them
  * can alter the emitted text of a unit that does not reference `U` at all. The dependency edge
  * used here (`U` names a symbol owned by `V`) covers the ordinary cases — a rename propagates
  * along references — but not the pathological one where two unrelated units collide in a table.
  * So the cache is ADVISORY, exactly as the BIR one is: `Determinism` re-emits and byte-compares
  * on every run, and a port that deletes the cache directory must get identical output. Treat a
  * mismatch between cached and fresh output as an engine defect, not as a cache-tuning problem.
  */
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

  /** Every symbol NAMED anywhere in a unit — terms and types alike.
    *
    * Walked with `StandardTraversal`, never a private recursion: a node kind added later is then
    * covered for free, and two of the four silent correctness defects this project has found were
    * hand-rolled walks that stopped one node short (CLAUDE.md §3). `transformType` is what reaches
    * a reference buried in a signature, which `transformTerm` alone never sees. */
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

  /** A unit's exported signature surface, digested — the early-cutoff key. Non-private members
    * only: a private member's type cannot affect how another unit is emitted. */
  def interfaceHash(program: Program, unit: Tree.ClassDef): String =
    given Program = program
    val owner = unitOf(program)
    val sigs = program.symbols.all.toList
      .filter(s => owner.getOrElse(s.id, SymId.None) == unit.symbol && !s.flags.isPrivate)
      .map(s => s"${s.fullName}:${TirPrinter.tpe(s.info, TirPrinter.Style.canonical)}:${s.flags}")
      .sorted
    Digest.string(sigs.mkString("\n"))

  /** `unit symbol -> action key`, for every unit given. One pass over the program builds the
    * owner map and every interface hash; the per-unit key is then a fold over its dependencies.
    *
    * @param decisions the run's decisions, which are NOT in the tree and ARE in the emitted text:
    *   a porter note is rendered from the decision log, so two runs whose trees are identical and
    *   whose POLICY differs produce different files. Without this the cache would replay a
    *   pre-change emission and the notes would silently be the old ones — the same class of defect
    *   `TirPrinter.digest` records for trivia (`Style.identity`), one layer out. Digested as a
    *   whole rather than per unit: a decision names its subject by `SymId`, the mapping from
    *   subject to UNIT is what a rename can move, and over-invalidating an advisory cache costs a
    *   re-emit while under-invalidating it costs a wrong file.
    */
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
          // …and the UPSTREAM JAVA, which is in the emitted text and NOT in the tree: the emitter's
          // recovery backstop reads comments the frontend never harvested, so a source edit that
          // only moved one of those changes the file while every tree digest stays identical —
          // a cache HIT that re-serves the previous emission, the same defect `Style.identity`
          // records for trivia and `notes` for decisions, one channel further out.
          ("java" -> javaDigest(u.origin.javaPath)) ::
          deps.toList.flatMap(d => byId.get(d).map(_ => s"dep:${nameOf(program, d)}" -> ifaces.getOrElse(d, ""))).sorted
      u.symbol -> Digest.combined(parts)
    }.toMap

  /** the upstream file's own digest, once per path. `""` when there is nothing to read — the
    * backstop reads nothing then either, so the key is not pretending to cover it. */
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
