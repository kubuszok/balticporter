package balticporter.corpus

import java.io.File
import java.nio.file.{Files, Path}

/** The frontend classpath every port resolves once and CACHES, with the coordinates it was
  * resolved from recorded beside it (§1(b): five porting programs wrote this loop
  * independently). The resolver INVOCATION is written to a sidecar (`<cache>.coords`) and
  * compared before a cached line is reused — an unresolved import resolves WRONGLY rather than
  * failing (CLAUDE.md §5.1). A header INSIDE the file would be a phantom classpath entry instead. */
object ClasspathCache:

  /** the invocation a cached line must have been produced by, as ONE string. Order-sensitive on
    * purpose — `cs` resolves highest-version-wins, so the same set in a different order is not
    * guaranteed to be the same classpath. */
  def key(coordinates: List[String], extraArgs: List[String] = Nil): String =
    (extraArgs ++ coordinates).mkString(" ")

  private def sidecar(cache: Path): Path = cache.resolveSibling(cache.getFileName.toString + ".coords")

  /** is this cache reusable FOR THESE COORDINATES — a non-empty line, resolved from this exact
    * invocation, EVERY ENTRY OF WHICH STILL EXISTS? A file with no sidecar is refetched once
    * rather than trusted. The third conjunct guards against an evicted external jar cache
    * (`~/Library/Caches/Coursier`): a line whose jars are gone is not a cache hit. */
  def fresh(cache: Path, key: String): Boolean =
    Files.exists(cache) && Files.readString(cache).trim.nonEmpty &&
      Files.exists(sidecar(cache)) && Files.readString(sidecar(cache)).trim == key &&
      Files.readString(cache).trim.split(File.pathSeparator).forall(e => Files.exists(Path.of(e)))

  /** write the line and the invocation that produced it, in that order — a line without its
    * fingerprint is refetched, which is the safe direction to be interrupted in. */
  def write(cache: Path, line: String, key: String): Path =
    Files.createDirectories(cache.getParent)
    Files.writeString(cache, line)
    Files.writeString(sidecar(cache), key + "\n")
    cache

  /** `cs fetch --classpath`, filtered to the one line that holds jars. `cs` writes PROGRESS to
    * stderr and the classpath to stdout; merged here so a failure is reportable, then filtered.
    * A failure is FATAL rather than an empty classpath: an unresolved import resolves WRONGLY,
    * not fails. @param label the port, for the message an operator will read @param extraArgs
    * repositories/exclusions before the coordinates; part of the key. */
  def fetch(label: String, coordinates: List[String], extraArgs: List[String] = Nil): List[String] =
    val cmd  = List("cs", "fetch", "--classpath") ++ extraArgs ++ coordinates
    val proc = new ProcessBuilder(cmd*).redirectErrorStream(true).start()
    val raw  = new String(proc.getInputStream.readAllBytes()).trim
    val line = raw.linesIterator.filter(_.contains(".jar")).toList.lastOption.getOrElse("")
    if proc.waitFor() != 0 || line.isEmpty then
      throw new IllegalStateException(
        s"[$label] could not fetch the classpath (is `cs` installed?):\n${cmd.mkString(" ")}\n$raw")
    line.split(File.pathSeparator).filter(_.nonEmpty).toList

  /** fetch-once-and-cache, for the ports whose cache holds exactly what `cs` produced. A port whose
    * line carries MORE than the jars (liqp appends the parser directory it javac'd) composes the
    * three pieces above itself. */
  def ensure(cache: Path, label: String, coordinates: List[String],
             extraArgs: List[String] = Nil): Path =
    val k = key(coordinates, extraArgs)
    if fresh(cache, k) then cache
    else write(cache, fetch(label, coordinates, extraArgs).mkString(File.pathSeparator), k)

  /** …and as the entries a frontend config wants. */
  def entries(cache: Path, label: String, coordinates: List[String],
              extraArgs: List[String] = Nil): List[Path] =
    Files.readString(ensure(cache, label, coordinates, extraArgs)).trim
      .split(File.pathSeparator).filter(_.nonEmpty).map(Path.of(_)).toList
