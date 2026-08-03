package balticporter.corpus

import java.io.File
import java.nio.file.{Files, Path}

/** The frontend classpath every port resolves once and CACHES — with the coordinates it was
  * resolved from recorded beside it.
  *
  * ==Why one mechanism==
  * Five porting programs wrote this loop independently (liqp and its test port, simple-graphs,
  * ashley, gltf, screenmanager), each with the same two lessons pasted into a comment: merge `cs`'s
  * streams so a failure is reportable, then keep only the line holding a jar, because taking the
  * whole output once cached `Downloading https…` as a classpath entry. A lesson that has to be
  * pasted is a mechanism that has not been written (CLAUDE.md §1(b)): the MECHANICS are identical
  * and only the COORDINATES differ, so the coordinates are the parameter and this is the mechanism.
  *
  * ==THE FINGERPRINT, which is the reason this file exists==
  * Every one of those loops asked one question — "does the cache file exist?" — and a cache keyed
  * on nothing else answers `yes` to a question nobody asked: after a coordinate BUMP the stale
  * line is reused, silently, and the port is resolved against the versions it used to declare. It
  * fails as the worst kind of failure this project has: an import that resolves WRONGLY rather than
  * one that fails, so the port emits nonsense and reports success (CLAUDE.md §5.1). Nothing else
  * would catch it — no count moves, and the port compiles.
  *
  * So the resolver INVOCATION — coordinates, repositories, exclusions, in order — is written to a
  * sidecar (`<cache>.coords`) and compared before the line is reused. A sidecar rather than a
  * header INSIDE the file, because the file is read by `PortConfig.classpathFile`, which splits the
  * whole text on the path separator: a comment line there is a classpath entry that does not exist.
  */
object ClasspathCache:

  /** the invocation a cached line must have been produced by, as ONE string. Order-sensitive on
    * purpose — `cs` resolves highest-version-wins, so the same set in a different order is not
    * guaranteed to be the same classpath. */
  def key(coordinates: List[String], extraArgs: List[String] = Nil): String =
    (extraArgs ++ coordinates).mkString(" ")

  private def sidecar(cache: Path): Path = cache.resolveSibling(cache.getFileName.toString + ".coords")

  /** is this cache reusable FOR THESE COORDINATES — a non-empty line, resolved from this exact
    * invocation? A file with no sidecar is a line from before this check existed, and is refetched
    * once rather than trusted. */
  def fresh(cache: Path, key: String): Boolean =
    Files.exists(cache) && Files.readString(cache).trim.nonEmpty &&
      Files.exists(sidecar(cache)) && Files.readString(sidecar(cache)).trim == key

  /** write the line and the invocation that produced it, in that order — a line without its
    * fingerprint is refetched, which is the safe direction to be interrupted in. */
  def write(cache: Path, line: String, key: String): Path =
    Files.createDirectories(cache.getParent)
    Files.writeString(cache, line)
    Files.writeString(sidecar(cache), key + "\n")
    cache

  /** `cs fetch --classpath`, filtered to the one line that holds jars.
    *
    * `cs` writes PROGRESS to stderr and the classpath to stdout; merged here so a failure is
    * reportable, then filtered — taking the whole output once cached `Downloading https…` as a
    * classpath entry and the frontend refused the run with "Downloading https does not exist".
    *
    * A failure is FATAL rather than an empty classpath, for the reason above: an
    * `import static org.junit.Assert.assertEquals` the frontend cannot resolve does not fail, it
    * resolves WRONGLY — to an unqualified call on the enclosing class — so a port built with no
    * classpath emits nonsense and reports success.
    *
    * @param label the port, for the message an operator will read
    * @param extraArgs repositories, exclusions — anything before the coordinates; part of the key
    */
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
