package balticporter.vocab

import balticporter.core.*
import balticporter.core.BExpr.*

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Tier-2 vocabulary (DESIGN.md §3.7): diffable data tables mapping Java symbols
  * onto Scala counterparts. Two entry kinds for now:
  *
  *   type java.util.ArrayList -> scala.collection.mutable.ArrayBuffer
  *   method java.util.ArrayList#add -> append
  *
  * `type` rewrites every occurrence of the qualified name (type positions,
  * constructor calls, static owners, class literals, method references).
  * `method` renames calls resolved to `Owner#name` — matched against the
  * ORIGINAL owner, so tables read in upstream vocabulary.
  *
  * Tables are pure data; call-shape adaptations tables can't express are
  * Tier-3 passes (DESIGN.md §2.4).
  */
final case class Vocabulary(
    typeMap: Map[String, String],
    methodMap: Map[(String, String), String],
    /** nilary Java calls that are parameterless members in Scala: `getter O#m -> n`. */
    getterMap: Map[(String, String), String] = Map.empty,
):
  def isEmpty: Boolean = typeMap.isEmpty && methodMap.isEmpty && getterMap.isEmpty

  /** joins cache fingerprints — sorted, content-only. */
  def digestInput: String =
    (typeMap.toList.sorted.map((a, b) => s"type $a -> $b") ++
      methodMap.toList.sortBy(_._1).map { case ((o, m), n) => s"method $o#$m -> $n" } ++
      getterMap.toList.sortBy(_._1).map { case ((o, m), n) => s"getter $o#$m -> $n" })
      .mkString("\n")

  def ++(other: Vocabulary): Vocabulary =
    Vocabulary(typeMap ++ other.typeMap, methodMap ++ other.methodMap, getterMap ++ other.getterMap)

object Vocabulary:
  val empty: Vocabulary = Vocabulary(Map.empty, Map.empty)

  /** Parses the table format. Lines: `type A -> B`, `method O#m -> n`,
    * blank, or `#` comments. Anything else is a hard error (data must not
    * drift silently).
    */
  def parse(lines: List[String], where: String): Vocabulary =
    val types = Map.newBuilder[String, String]
    val methods = Map.newBuilder[(String, String), String]
    val getters = Map.newBuilder[(String, String), String]
    lines.zipWithIndex.foreach { (line0, i) =>
      val line = line0.trim
      if line.nonEmpty && !line.startsWith("#") then
        line.split("\\s+").toList match
          case "type" :: from :: "->" :: to :: Nil => types += from -> to
          case "method" :: sig :: "->" :: to :: Nil if sig.contains('#') =>
            val Array(owner, m) = sig.split('#')
            methods += (owner, m) -> to
          case "getter" :: sig :: "->" :: to :: Nil if sig.contains('#') =>
            val Array(owner, m) = sig.split('#')
            getters += (owner, m) -> to
          case _ =>
            throw new IllegalArgumentException(s"$where:${i + 1}: unparseable vocabulary line: $line0")
    }
    Vocabulary(types.result(), methods.result(), getters.result())

  def loadFile(p: Path): Vocabulary =
    parse(Files.readAllLines(p).asScala.toList, p.toString)

/** Applies a vocabulary to a unit: method renames first (matched on original
  * owners), then type renames everywhere. */
final class VocabPass(v: Vocabulary) extends BirPass:
  def id = "vocab/apply"
  def version = 1
  def run(unit: BUnit): BUnit =
    val renamed =
      if v.methodMap.isEmpty && v.getterMap.isEmpty then unit
      else
        unit.copy(types = unit.types.map(BirTransform.mapTypeDecl(_) {
          case c: Call if c.args.isEmpty && c.ownerQ.exists(o => v.getterMap.contains((o, c.name))) =>
            // nilary call -> parameterless Scala member (prints without parens)
            val n = v.getterMap((c.ownerQ.get, c.name))
            c.recv match
              case Recv.On(r)          => Select(r, n)
              case Recv.OnThis         => Select(This, n)
              case Recv.Static(owner)  => Ident(n, RefKind.StaticField(owner))
              case Recv.OnSuper        => c.copy(name = n)
          case c: Call if c.ownerQ.exists(o => v.methodMap.contains((o, c.name))) =>
            c.copy(name = v.methodMap((c.ownerQ.get, c.name)))
          case MethodRef(Left(owner), m) if v.methodMap.contains((owner, m)) =>
            MethodRef(Left(owner), v.methodMap((owner, m)))
          case e => e
        }))
    if v.typeMap.isEmpty then renamed
    else QNameMap(renamed)(q => v.typeMap.getOrElse(q, q))

/** Tier-3 example rule: prefix package rename (`liqp` -> `ported.liqp`),
  * rewriting the unit package and every reference into the renamed tree. */
final class PackageRenamePass(from: String, to: String) extends BirPass:
  def id = s"rename/$from->$to"
  def version = 1
  private def mapQ(q: String): String =
    if q == from then to
    else if q.startsWith(from + ".") then to + q.stripPrefix(from)
    else q
  def run(unit: BUnit): BUnit =
    QNameMap(unit)(mapQ).copy(pkg = mapQ(unit.pkg))
