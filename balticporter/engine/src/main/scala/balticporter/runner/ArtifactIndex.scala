package balticporter.runner

import balticporter.catalog.{ArtifactDep, CrossKind}
import balticporter.tir.DependencyCheck

import java.io.File
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** Resolves an [[ArtifactDep]] via `cs fetch --intransitive` and enumerates the JVM jar's class
  * entries to answer [[DependencyCheck.Provides]]. Returns `Unverifiable` on failure. Coordinate
  * is built explicitly (`name_3`, not `cs`'s default). Cache is fingerprinted by the exact
  * invocation; stale sidecars trigger a refetch. // ENGINE-LIMITS P8
  */
object ArtifactIndex:

  /** JVM artifact id from the coordinate's `CrossKind`. */
  def coordinate(d: ArtifactDep, scalaBinary: String = "3"): String =
    val artifact = d.cross match
      case CrossKind.Java              => d.name
      case CrossKind.Scala | CrossKind.Platform => s"${d.name}_$scalaBinary"
    s"${d.org}:$artifact:${d.rev}"

  /** Full `cs` invocation; also the cache fingerprint.
    * `--intransitive` must come AFTER `-r` flags (`cs` reads everything after it as a module). */
  def command(d: ArtifactDep, scalaBinary: String = "3"): List[String] =
    List("cs", "fetch") ++ d.resolver.toList.flatMap(r => List("-r", r)) ++
      List("--intransitive", coordinate(d, scalaBinary))

  /** Classes named by a jar entry as `Symbol.fullName`, plus enclosing prefixes (cut at `$`).
    * Skips `META-INF/`, `module-info`, `package-info`. Pure function for testability. */
  def namesOf(entry: String): List[String] =
    if !entry.endsWith(".class") || entry.startsWith("META-INF/") then Nil
    else
      val path   = entry.dropRight(".class".length)
      val simple = path.substring(path.lastIndexOf('/') + 1)
      if simple == "module-info" || simple == "package-info" then Nil
      else
        val fqn = path.replace('/', '.')
        fqn.split('$').toList.takeWhile(_.nonEmpty) match
          case Nil     => Nil
          case h :: ts => ts.scanLeft(h)((acc, p) => s"$acc$$$p")

  /** …over every jar handed in. */
  def classesIn(jars: List[Path]): Set[String] =
    jars.iterator.flatMap { j =>
      val zip = new ZipFile(j.toFile)
      try zip.entries.asScala.toList.flatMap(e => namesOf(e.getName))
      finally zip.close()
    }.toSet

  /** Cache file path for a coordinate. Freshness is decided by the fingerprint sidecar. */
  def cacheFile(dir: Path, d: ArtifactDep): Path =
    dir.resolve(s"${d.org}.${d.name}.${d.rev}".replaceAll("[^A-Za-z0-9._-]", "_") + ".classes")

  private def sidecar(cache: Path): Path = cache.resolveSibling(cache.getFileName.toString + ".coords")

  /** What this coordinate provides. Cached when fingerprint matches; `resolve` is injectable for testing. */
  def provides(d: ArtifactDep, cacheDir: Option[Path], scalaBinary: String = "3",
               resolve: List[String] => Either[String, List[Path]] = fetch): DependencyCheck.Provides =
    val cmd = command(d, scalaBinary)
    val key = cmd.mkString(" ")
    val hit = cacheDir.map(dir => cacheFile(dir, d)).filter { c =>
      Files.exists(c) && Files.exists(sidecar(c)) && Files.readString(sidecar(c)).trim == key
    }
    hit match
      case Some(c) =>
        // An empty cached listing is valid (e.g. a resources-only artifact).
        DependencyCheck.Provides.Known(Files.readString(c).linesIterator.filter(_.nonEmpty).toSet)
      case scala.None =>
        resolve(cmd) match
          case Left(why) => DependencyCheck.Provides.Unverifiable(why)
          case Right(jars) =>
            // A jar that resolved but cannot be read is Unverifiable, not empty. // CLAUDE.md §4.6
            try
              val classes = classesIn(jars)
              cacheDir.foreach(dir => writeCache(dir, d, classes, key))
              DependencyCheck.Provides.Known(classes)
            catch
              case NonFatal(e) => DependencyCheck.Provides.Unverifiable(
                s"resolved ${jars.size} jar(s) and could not read them: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** Write is guarded separately: a cache write failure must not affect the check result. */
  private def writeCache(dir: Path, d: ArtifactDep, classes: Set[String], key: String): Unit =
    try
      val c = cacheFile(dir, d)
      Files.createDirectories(dir)
      Files.writeString(c, classes.toList.sorted.mkString("\n"))
      // Listing first, fingerprint second: interrupted = refetched (safe direction).
      Files.writeString(sidecar(c), key + "\n")
    catch case NonFatal(_) => ()

  /** Run `cs fetch --intransitive`, filter to existing `.jar` paths.
    * Lines are paths (may contain spaces); progress lines filtered by existence check. */
  def fetch(cmd: List[String]): Either[String, List[Path]] =
    try
      val proc = new ProcessBuilder(cmd*).redirectErrorStream(true).start()
      val raw  = new String(proc.getInputStream.readAllBytes()).trim
      val jars = raw.linesIterator.map(_.trim).filter(_.endsWith(".jar")).flatMap(asPath).toList
      if proc.waitFor() != 0 || jars.isEmpty then
        Left(s"could not resolve `${cmd.mkString(" ")}` (is `cs` installed, and is this machine " +
          s"online?): ${raw.linesIterator.toList.takeRight(3).mkString(" / ")}")
      else Right(jars)
    catch
      case NonFatal(e) =>
        Left(s"could not run `${cmd.mkString(" ")}`: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** A printed line as a local jar path, or `None` for progress/URL lines. */
  private[runner] def asPath(line: String): Option[Path] =
    try Option(Path.of(line)).filter(Files.isRegularFile(_))
    catch case NonFatal(_) => scala.None

  /** Memoised supplier for `DependencyCheck.declarations`. Keyed on full command string
    * (includes cross kind and resolver) so two different coordinates never share a result. */
  def supplier(cacheDir: Option[Path], scalaBinary: String = "3",
               resolve: List[String] => Either[String, List[Path]] = fetch): ArtifactDep => DependencyCheck.Provides =
    val memo = scala.collection.mutable.Map.empty[String, DependencyCheck.Provides]
    d => memo.getOrElseUpdate(command(d, scalaBinary).mkString(" "),
                              provides(d, cacheDir, scalaBinary, resolve))

  /** Default cache dir: `<root>/.balticporter/artifact-index`. Always `Some`. */
  def defaultCacheDir: Option[Path] =
    Some(balticporter.tir.DebugFlags.root.resolve(".balticporter").resolve("artifact-index"))
