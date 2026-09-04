package balticporter.tir

import balticporter.core.ResourceTree

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

/** Classpath resources copied verbatim into the port's build product.
  *
  * Declared per-library via `ResourceTree` (DESIGN.md §8.22). The run copies each declared file,
  * reports undeclared files that emitted code names, and flags empty trees.
  * A declared file that is not there is FATAL (the caller's check). */
object PortResources:

  /** The `CheckReport` lane; conditional on the manifest declaring at least one tree. */
  val Name: String = "resources"

  enum Kind:
    /** The file was copied and the emitted code names this path. */
    case Shipped
    /** Emitted code names this path but the port did not ship it. */
    case NamedUnshipped
    /** Shipped, but no emitted literal names it. */
    case Unnamed
    /** A declared tree that ships no file at all. */
    case Empty

    def slug: String = this match
      case Shipped        => "shipped"
      case NamedUnshipped => "named-unshipped"
      case Unnamed        => "unnamed"
      case Empty          => "empty"

  /** One planned resource: source `root` and `/`-separated `path` used for both read and write. */
  final case class Res(root: Path, path: String):
    def source: Path = path.split('/').foldLeft(root)((p, seg) => p.resolve(seg))
    /** The classpath form a `getResourceAsStream` literal uses. */
    def absolute: String = "/" + path

  /** Plan every declared file. Pure -- no filesystem writes. */
  def plan(trees: List[ResourceTree]): List[Res] =
    trees.flatMap(t => t.files.map(f => Res(t.root, normalise(f))))

  /** Normalise a declared path: strip leading `/`, collapse `//`, remove `.` segments. */
  private def normalise(p: String): String =
    p.split('/').iterator.filter(s => s.nonEmpty && s != ".").mkString("/")

  /** Undeclared files under declared roots -- candidates for [[Kind.NamedUnshipped]].
    * Walked to ASK, never to SHIP (a scan would include upstream build files). */
  def candidates(trees: List[ResourceTree], declared: List[Res]): List[Res] =
    val have = declared.map(r => (r.root.toString, r.path)).toSet
    trees.distinctBy(_.root.toString).flatMap { t =>
      if !Files.isDirectory(t.root) then Nil
      else
        val walk = Files.walk(t.root)
        try
          walk.iterator().asScala
            .filter(Files.isRegularFile(_))
            .map(p => Res(t.root, normalise(t.root.relativize(p).toString.replace('\\', '/'))))
            .filterNot(r => have.contains((r.root.toString, r.path)))
            .toList.sortBy(_.path)
        finally walk.close()
    }

  /** One row per shipped resource plus each residue.
    * @param named whether the emitted program holds a string literal naming this classpath path
    *              (checked in both `/path` and bare forms; scoped to this module's own units).
    */
  def findings(shipped: List[Res], candidates: List[Res], trees: List[ResourceTree],
               named: String => Boolean): List[CheckReport.Finding] =
    def row(kind: Kind, owner: String, path: String, detail: String) =
      CheckReport.Finding(Name, kind.slug, owner, path, 0, detail)

    val empties = trees.filter(_.files.isEmpty).map { t =>
      row(Kind.Empty, CheckReport.relativise(t.root.toString),
        CheckReport.relativise(t.root.toString),
        "this tree declares no file, so the run ships nothing from it — which is indistinguishable " +
          "from the resource being absent, the failure this key exists to remove " +
          "[§1(b): name the files this module ships, or remove the entry]")
    }

    val unshipped = candidates.filter(r => named(r.path)).map { r =>
      row(Kind.NamedUnshipped, r.path, CheckReport.relativise(r.source.toString),
        s"the emitted code names `${r.path}` and this port does not ship it, although the file is " +
          s"under a resource root this manifest already declares. A classpath lookup is a STRING " +
          "LITERAL no rename may move (§4.56), so the path is correct and the COPY is what is " +
          "missing: absent, the lookup fails at first use with no compile error, no check count and " +
          "no member digest to say so " +
          "[§1(b): add it to that tree's `files`, or state why this port ships without it]")
    }

    val rows = shipped.map { r =>
      if named(r.path) then
        row(Kind.Shipped, r.path, CheckReport.relativise(r.source.toString),
          s"shipped verbatim as `${r.path}`, which the emitted code names")
      else
        row(Kind.Unnamed, r.path, CheckReport.relativise(r.source.toString),
          s"shipped verbatim as `${r.path}`, and no emitted literal names it — legitimate for a " +
            "resource another resource names (an atlas names its image, a skin its fonts) or one " +
            "this port's consumer reads, and identical to a stale entry, so the engine states it " +
            "and the port decides [§1(b): confirm against this module's `resources`]")
    }

    empties ++ unshipped ++ rows

  /** Copy the planned resources under `resourceRoot`, verbatim, returning what was written.
    * Not gated on the artifact layer (licence deliverable). Destination is `src_managed/`. */
  def write(rs: List[Res], resourceRoot: Path): List[Path] =
    rs.map { r =>
      val dst = r.path.split('/').foldLeft(resourceRoot)((p, seg) => p.resolve(seg))
      Files.createDirectories(dst.getParent)
      Files.copy(r.source, dst, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      dst
    }

  def summary(rs: List[Res]): String =
    if rs.isEmpty then "  none"
    else
      rs.groupBy(_.root.toString).toList.sortBy(_._1).map { (root, files) =>
        s"  ${CheckReport.relativise(root)} -> ${files.size} file(s), verbatim"
      }.mkString("\n")
