package balticporter.core

import java.nio.file.{Files, Path, StandardCopyOption}
import java.security.MessageDigest

/** Content digests + the persistent action cache (PLAN.md §12).
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
    * cache (the coarse, sound tier from RESEARCH.md §4.6). Computed once per run.
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
    val parts = dirs.flatMap { d =>
      if Files.isDirectory(d) then
        val classFiles = Files.walk(d).iterator()
        val buf = List.newBuilder[(String, String)]
        classFiles.forEachRemaining { p =>
          if p.toString.endsWith(".class") then buf += (d.relativize(p).toString -> Digest.file(p))
        }
        buf.result()
      else List(d.toString -> Digest.file(d)) // a jar
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
