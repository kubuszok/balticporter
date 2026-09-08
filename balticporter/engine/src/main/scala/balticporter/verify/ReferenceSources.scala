package balticporter.verify

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*
import scala.meta.*
import balticporter.tir.{RunScope, Symbol}

/** The reference port's declarations by PATH and NAME, each with its verbatim source — what
  * `AddMembersTransform.fromReference` splices. Paths follow `ApiParityCheck` (`/Outer/Inner`,
  * `/Outer$` for a companion), so a java type is found through `ReferencePolicy.classPaths`
  * (DESIGN.md §8.30). An import of the reference file is carried only where the member's text
  * mentions the imported name; wildcards are never carried. */
final class ReferenceSources(roots: List[Path], typeRenames: Map[String, String],
                             flattenNestedTypes: Set[String]) extends RunScope.ReferenceSourceLookup:
  import ReferenceSources.*

  /** reference files scalameta could not parse — reported, never guessed around. */
  val unparseable: collection.mutable.ListBuffer[String] = collection.mutable.ListBuffer.empty
  val index: Map[String, List[RunScope.ReferenceMember]] =
    val out = collection.mutable.Map.empty[String, List[RunScope.ReferenceMember]]
    val files = roots.flatMap { root =>
      if !Files.isDirectory(root) then Nil
      else Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".scala") && Files.isRegularFile(p)).toList.sorted
    }
    files.foreach { f =>
      val text = Files.readString(f)
      dialects.Scala3(Input.VirtualFile(f.toString, text)).parse[Source] match
        case Parsed.Success(tree) =>
          val imports = tree.collect { case i: Import => i }.flatMap(importLines)
          // a CHAINED package clause (`package sge` / `package math`) puts every outer package in
          // scope; the emitted file has one clause, so the outer ones ride as wildcard imports.
          val outer = packageChain(tree).dropRight(1).scanLeft(List.empty[String])(_ :+ _).drop(1)
            .map(segs => s"import ${segs.mkString(".")}.*")
          def add(path: String, name: String, kind: String, t: Tree): Unit =
            val src  = t.pos.text
            val used = outer ++ imports.filter((simple, _) => mentions(src, simple)).map(_._2).distinct
            val key  = s"$path/$name"
            out(key) = out.getOrElse(key, Nil) :+ RunScope.ReferenceMember(name, kind, path.endsWith("$"), src, used)
          def walkTemplate(templ: Template, path: String): Unit = templ.body.stats.foreach(walk(_, path))
          def walk(t: Tree, path: String): Unit = t match
            case s: Source       => s.stats.foreach(walk(_, path))
            case p: Pkg          => p.stats.foreach(walk(_, path))
            case p: Pkg.Object   => walkTemplate(p.templ, s"$path/${p.name.value}$$")
            case d: Defn.Class   => add(path, d.name.value, "class", d); walkTemplate(d.templ, s"$path/${d.name.value}")
            case d: Defn.Trait   => add(path, d.name.value, "trait", d); walkTemplate(d.templ, s"$path/${d.name.value}")
            case d: Defn.Enum    => add(path, d.name.value, "enum", d);  walkTemplate(d.templ, s"$path/${d.name.value}")
            case d: Defn.Object  => add(path, d.name.value, "object", d); walkTemplate(d.templ, s"$path/${d.name.value}$$")
            case d: Defn.Def     => add(path, d.name.value, "def", d)
            case d: Decl.Def     => add(path, d.name.value, "def", d)
            case d: Defn.Type    => add(path, d.name.value, "type", d)
            case d: Decl.Type    => add(path, d.name.value, "type", d)
            case d: Decl.Val     => d.pats.collect { case Pat.Var(n) => n.value }.foreach(add(path, _, "val", d))
            case d: Decl.Var     => d.pats.collect { case Pat.Var(n) => n.value }.foreach(add(path, _, "var", d))
            case d: Defn.Val     => d.pats.collect { case Pat.Var(n) => n.value }.foreach(add(path, _, "val", d))
            case d: Defn.Var     => d.pats.collect { case Pat.Var(n) => n.value }.foreach(add(path, _, "var", d))
            case d: Defn.ExtensionGroup =>
              d.body match
                case Term.Block(stats) => stats.collect { case m: Defn.Def => m.name.value }.foreach(add(path, _, "extension", d))
                case m: Defn.Def       => add(path, m.name.value, "extension", d)
                case _                 => ()
            case _ => ()
          walk(tree, "")
        case e: Parsed.Error => unparseable += s"${f}: ${e.message}"
    }
    out.toMap

  def membersOf(owner: Symbol, names: List[String]): List[RunScope.ReferenceMember] =
    val paths = ReferencePolicy.classPaths(owner, typeRenames, flattenNestedTypes)
    names.flatMap { n =>
      paths.flatMap(p => index.getOrElse(s"$p/$n", Nil) ++ index.getOrElse(s"$p$$/$n", Nil))
    }.distinct

object ReferenceSources:
  /** the file's package clauses, outermost first (`package a.b` then `package c` is `List("a.b", "c")`). */
  private def packageChain(tree: Tree): List[String] =
    def go(t: Tree): List[String] = t match
      case s: Source => s.stats.collectFirst { case p: Pkg => p }.map(go).getOrElse(Nil)
      case p: Pkg    => p.ref.syntax :: p.stats.collectFirst { case q: Pkg => q }.map(go).getOrElse(Nil)
      case _         => Nil
    go(tree)
  /** `(simple name, import line)` per importee; a wildcard yields nothing. */
  private def importLines(i: Import): List[(String, String)] =
    i.importers.flatMap { imp =>
      imp.importees.flatMap {
        case Importee.Name(n)      => List(n.value -> s"import ${imp.ref.syntax}.${n.value}")
        case Importee.Rename(f, t) => List(t.value -> s"import ${imp.ref.syntax}.{${f.value} => ${t.value}}")
        case _                     => Nil
      }
    }
  private def mentions(src: String, simple: String): Boolean =
    java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(simple) + "\\b").matcher(src).find()
