package balticporter.frontend.ts

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path }
import scala.jdk.CollectionConverters.*

/** The one entry point from a library's exported syntax trees and its hand-written reference Scala to translated method bodies, and to the table saying where each emitted body came from.
  *
  * A library is a value the consumer supplies: its policy, its reader, and which syntax-tree files feed which reference file. The mechanism that applies bodies, records bodies.tsv and refuses is
  * here.
  */
object NonJavaBodies:

  /** Loads a syntax-tree file by its path relative to the library's directory; the `Left` says why it could not be read. */
  type Loader = String => Either[String, RastFile]

  /** One reference file and the syntax-tree files translated for it. `rast` is required, `extraRast` are read where present; paths are relative to the library's directories. */
  final case class Module(
    referenceSubPath: String,
    rast:             String,
    extraRast:        List[String],
    build:            List[RastFile] => ParityDerive.Bodies
  )

  /** A registered library. `modules` may need a file of its own first (a class hierarchy) and refuses when it cannot be read. */
  final case class Library(
    name:     String,
    policy:   ParityDerive.Policy,
    readRast: Path => RastFile,
    modules:  Loader => Either[String, List[Module]]
  )

  /** The translated bodies of one reference file, the syntax-tree files they came from, and the reason there are none when its module could not be built. */
  final case class FileBodies(bodies: ParityDerive.Bodies, rast: List[String], refusal: Option[String])

  sealed trait Result

  /** Nothing was built; `message` says why. */
  final case class Refused(library: String, reason: String) extends Result:
    def message: String = s"no translated bodies for '$library': $reason"

  /** Bodies per reference file, keyed by the file's path relative to `referenceDir` with `/` separators; a file with no entry has no module feeding it. */
  final case class Built(
    library:      String,
    policy:       ParityDerive.Policy,
    referenceDir: Path,
    files:        Map[String, FileBodies]
  ) extends Result:

    def bodiesFor(relativeFile: String): ParityDerive.Bodies =
      files.get(relativeFile).fold(ParityDerive.Bodies.empty)(_.bodies)

    private lazy val translatedNames: Set[String] = files.values.flatMap(_.bodies.names).toSet

    private def translatedSomewhere(member: String): Boolean =
      translatedNames(member) || policy.aliases.getOrElse(member, Nil).exists(translatedNames)

    /** One reference file derived, with its report rows. In a file no module feeds, a member whose name WAS translated elsewhere in the library is `unclassified`. */
    def deriveFile(relativeFile: String, referenceSource: String): (String, List[BodiesReport.Row]) =
      val entry  = files.get(relativeFile)
      val result = ParityDerive.derive(referenceSource, entry.fold(ParityDerive.Bodies.empty)(_.bodies), policy)
      val rows   = BodiesReport.rows(relativeFile, result).map { row =>
        if row.why != ParityDerive.Why.NoTranslatedBody then row
        else
          entry match
            case Some(FileBodies(_, _, Some(refusal)))   => row.copy(why = ParityDerive.Why.translatorRefusal(refusal))
            case None if translatedSomewhere(row.member) => row.copy(why = ParityDerive.Why.Unclassified)
            case _                                       => row
      }
      (result.emittedSource, rows)

    /** Derives every `.scala` file under `referenceDir` into the same relative path under `outDir`, and writes `bodies.tsv` and its summary into `reportDir`. */
    def derive(outDir: Path, reportDir: Path): Run =
      val written = List.newBuilder[Path]
      val rows    = List.newBuilder[BodiesReport.Row]
      for relative <- scalaFilesUnder(referenceDir) do
        val source           = new String(Files.readAllBytes(referenceDir.resolve(relative)), StandardCharsets.UTF_8)
        val (emitted, fRows) = deriveFile(relative, source)
        val target           = outDir.resolve(relative)
        Files.createDirectories(target.getParent)
        Files.write(target, emitted.getBytes(StandardCharsets.UTF_8))
        written += target
        rows ++= fRows
      val all = rows.result()
      Run(written.result(), all, BodiesReport.write(reportDir, all))

  /** What one derive run wrote. */
  final case class Run(written: List[Path], rows: List[BodiesReport.Row], summary: BodiesReport.Summary)

  /** Translated bodies for the given `library` value: `referenceDir` is the root of its hand-written Scala (any package directories included), `rastDir` the root of its exported syntax trees. */
  def build(library: Library, referenceDir: Path, rastDir: Path): Result =
    if !Files.isDirectory(referenceDir) then Refused(library.name, s"the reference directory does not exist: $referenceDir")
    else if !Files.isDirectory(rastDir) then Refused(library.name, s"the syntax-tree directory does not exist: $rastDir")
    else
      val cache = scala.collection.mutable.Map.empty[String, Either[String, RastFile]]
      val load: Loader = relative =>
        cache.getOrElseUpdate(
          relative, {
            val path = rastDir.resolve(relative)
            if !Files.isRegularFile(path) then Left("missing-rast")
            else
              // An export the reader rejects is a recorded refusal for the files it feeds, never a reason to fail the whole library.
              try Right(library.readRast(path))
              catch case _: com.github.plokhotnyuk.jsoniter_scala.core.JsonReaderException => Left("unreadable-rast")
          }
        )

      library.modules(load) match
        case Left(reason)   => Refused(library.name, reason)
        case Right(modules) =>
          val references = scalaFilesUnder(referenceDir)
          val anchor     = anchorOf(references, modules.map(_.referenceSubPath))
          val present    = references.toSet
          val perFile    = modules.flatMap { module =>
            val relative = anchor + module.referenceSubPath
            if !present(relative) then None
            else
              load(module.rast) match
                case Left(reason) => Some(relative -> FileBodies(ParityDerive.Bodies.empty, List(module.rast), Some(reason)))
                case Right(main)  =>
                  val extras = module.extraRast.flatMap(p => load(p).toOption.map(p -> _))
                  Some(relative -> FileBodies(module.build(main :: extras.map(_._2)), module.rast :: extras.map(_._1), None))
          }
          // Two modules naming one reference file feed it in table order.
          val files = perFile.groupMap(_._1)(_._2).map { case (file, parts) =>
            file -> FileBodies(
              parts.map(_.bodies).reduce(_ ++ _),
              parts.flatMap(_.rast),
              parts.flatMap(_.refusal).headOption.filter(_ => parts.forall(_.refusal.isDefined))
            )
          }
          Built(library.name, library.policy, referenceDir, files)

  /** The directory prefix under which the most module paths resolve — the reference root may or may not include package directories. Ties take the shortest prefix. */
  private def anchorOf(references: List[String], subPaths: List[String]): String =
    val votes = for
      sub <- subPaths
      ref <- references
      if ref == sub || ref.endsWith("/" + sub)
    yield ref.dropRight(sub.length)
    if votes.isEmpty then ""
    else votes.groupBy(identity).toList.map((prefix, vs) => (-vs.size, prefix.length, prefix)).min._3

  private def scalaFilesUnder(dir: Path): List[String] =
    val stream = Files.walk(dir)
    try stream.iterator().asScala.filter(p => Files.isRegularFile(p) && p.getFileName.toString.endsWith(".scala")).map(p => dir.relativize(p).toString.replace('\\', '/')).toList.sorted
    finally stream.close()
