package balticporter.runner

import balticporter.core.{ FrontendConfig, PortManifest, Provenance, RealPath, RowSource }
import balticporter.tir.CheckReport

import java.nio.file.{ FileSystems, Files, Path }
import scala.jdk.CollectionConverters.*

/** Per-row upstream java (`PortManifest.rowSources`): which main files each row's files shadow, the frontend a row translates with, and the refusals. A row file shadows the main file at the same
  * package path; the types it declares must be the ones that file declares, with the same emitted surface, because the shared code compiles once against every row.
  */
object RowOverrides:

  /** the lane every refusal is counted in; recorded only for a port that declares row sources. */
  val Name = "row-source"

  /** one row file and the main file it replaces on that row. `main` is the `FrontendConfig.files` entry, `rel` the package path both share. */
  final case class Shadow(row: String, file: Path, rel: String, main: String, root: RowSource)

  final case class Plan(rows: Map[String, List[Shadow]]):
    def isEmpty: Boolean = rows.values.forall(_.isEmpty)

    /** main `files` entries some row shadows. */
    lazy val shadowedMain: Set[String] = rows.values.flatten.map(_.main).toSet

    /** the rows that replace one main file. */
    def overriding(main: String): Set[String] = rows.collect { case (r, ss) if ss.exists(_.main == main) => r }.toSet

  object Plan:
    val empty: Plan = Plan(Map.empty)

  /** the row names a port declares: a platform row whose platform the port targets. */
  def declaredRows(targets: Set[balticporter.catalog.Platform]): Set[String] =
    PortManifest.PlatformRows.collect { case (row, p) if targets(p) => row }.toSet

  /** Expand every declared entry and match it against the main set. Findings are refusals: an undeclared row, an entry matching no file, a row file with no main counterpart. */
  def plan(sources: Map[String, List[RowSource]], frontend: FrontendConfig, rows: Set[String]): (Plan, List[CheckReport.Finding]) =
    val mainByRel = frontend.files.map(f => normal(f) -> f).toMap
    val findings  = List.newBuilder[CheckReport.Finding]
    val planned   = sources.toList.sortBy(_._1).map { (row, trees) =>
      if !rows(row) then
        findings += finding(
          "undeclared-row",
          row,
          "",
          s"`$row` is not a row this port builds; declared rows: ${rows.toList.sorted.mkString(", ")}. A tree nothing compiles would ship nothing"
        )
      row -> trees.flatMap { tree =>
        expand(tree) match
          case Left(why)   => findings += finding("missing", row, tree.root.toString, why); Nil
          case Right(rels) =>
            rels.flatMap { rel =>
              mainByRel.get(rel) match
                case Some(main) => List(Shadow(row, tree.root.resolve(rel), rel, main, tree))
                case scala.None =>
                  findings += finding(
                    "no-main-type",
                    row,
                    rel,
                    s"row `$row` declares `$rel`, which the main source set does not; a row may only replace a type the shared code already has"
                  )
                  Nil
            }
      }
    }
    (Plan(planned.filter(_._2.nonEmpty).toMap), findings.result())

  /** One row file's emitted types against the main file's, by emitted FQN → text: a type only the row declares, a type the row lacks, and every reachable member whose signature differs. */
  def compare(s: Shadow, main: Map[String, String], row: Map[String, String]): List[CheckReport.Finding] =
    val onlyRow = (row.keySet -- main.keySet).toList.sorted.map { n =>
      finding("no-main-type", s.row, s.rel, s"`$n` is declared by the row file and by no main file at `${s.main}`")
    }
    val onlyMain = (main.keySet -- row.keySet).toList.sorted.map { n =>
      finding("surface", s.row, s.rel, s"`$n` is absent on row `${s.row}`, and the shared code compiles against it")
    }
    val differing = (main.keySet intersect row.keySet).toList.sorted.flatMap { n =>
      (RowSurface.of(main(n), n), RowSurface.of(row(n), s"${s.row}:$n")) match
        case (Right(a), Right(b)) => RowSurface.diff(a, b).map(d => finding("surface", s.row, s.rel, d.render))
        case (a, b)               => (a.left.toSeq ++ b.left.toSeq).toList.map(e => finding("surface", s.row, s.rel, s"emitted text does not parse, so its surface cannot be compared: $e"))
    }
    onlyRow ++ onlyMain ++ differing

  /** The files one tree declares, package-relative and sorted; `Left` when the root is absent or an entry matches nothing. */
  def expand(tree: RowSource): Either[String, List[String]] =
    if !Files.isDirectory(tree.root) then Left(s"row source root ${tree.root} is not a directory")
    else
      val all =
        val walk = Files.walk(tree.root, java.nio.file.FileVisitOption.FOLLOW_LINKS)
        try walk.iterator.asScala.filter(p => Files.isRegularFile(p) && p.toString.endsWith(".java")).map(p => normal(tree.root.relativize(p).toString)).toList.sorted
        finally walk.close()
      if tree.files.isEmpty then Right(all)
      else
        val picked = tree.files.map { entry =>
          val m = FileSystems.getDefault.getPathMatcher("glob:" + normal(entry))
          entry -> all.filter(rel => m.matches(Path.of(rel)))
        }
        picked.collect { case (entry, Nil) => entry } match
          case Nil     => Right(picked.flatMap(_._2).distinct.sorted)
          case missing => Left(s"declared row source(s) matching no file under ${tree.root}: ${missing.mkString(", ")}")

  /** The frontend one row translates with: each shadowed main file replaced IN PLACE by its row file, and excluded from any resolution root holding it, so the program declares each type once. */
  def frontendFor(frontend: FrontendConfig, shadows: List[Shadow]): FrontendConfig =
    val byMain   = shadows.map(s => s.main -> RealPath.of(s.file).toString).toMap
    val excludes = for
      s <- shadows
      r <- frontend.resolutionRoots
      if Files.exists(r)
      abs = RealPath.of(frontend.sourceRoot.resolve(s.main))
      rr  = RealPath.of(r)
      if abs.startsWith(rr)
    yield normal(rr.relativize(abs).toString)
    frontend.copy(
      files = frontend.files.map(f => byMain.getOrElse(f, f)),
      resolutionExcludes = (frontend.resolutionExcludes ++ excludes).distinct
    )

  /** The header provenance for one row's translation: each row tree's files are named by the path a reader finds them at upstream. */
  def provenanceFor(p: Provenance, shadows: List[Shadow]): Provenance =
    p.copy(extraRoots = shadows.map(_.root).distinct.map(t => RealPath.of(t.root).toString -> prefixOf(p, t)))

  /** where a reader finds one row file: the path its header names, or the report-relative path without provenance. */
  def shownPath(p: Option[Provenance], s: Shadow): String = p match
    case Some(pr) =>
      val pre = prefixOf(pr, s.root)
      if pre.isEmpty then s.rel else s"$pre/${s.rel}"
    case scala.None => CheckReport.relativise(s.file.toString)

  /** a tree's header prefix: its root relative to the upstream checkout the main prefix implies (the main source root minus `sourcePathPrefix`), or none when the tree lies outside it. */
  private def prefixOf(p: Provenance, tree: RowSource): String =
    val marker = p.sourcePathPrefix.stripSuffix("/")
    val root   = if p.sourceRoot.isEmpty then "" else RealPath.of(Path.of(p.sourceRoot)).toString.replace('\\', '/')
    val top    = if marker.nonEmpty && root.endsWith("/" + marker) then Some(root.stripSuffix("/" + marker)) else scala.None
    val at     = RealPath.of(tree.root)
    top.map(Path.of(_)).filter(at.startsWith).map(t => normal(t.relativize(at).toString)).getOrElse("")

  def finding(kind: String, row: String, path: String, detail: String): CheckReport.Finding =
    CheckReport.Finding(Name, kind, row, path, 0, detail)

  private def normal(rel: String): String = Path.of(rel).normalize.toString.replace('\\', '/')
