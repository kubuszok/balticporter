package balticporter.runner

import balticporter.catalog.{ArtifactDep, CrossKind}
import balticporter.tir.DependencyCheck

import java.io.File
import java.nio.file.{Files, Path}
import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

/** WHAT AN ARTIFACT PROVIDES, read from the artifact.
  *
  * `DependencyCheck`'s 2×2 needs to know whether the emitted code references a declared coordinate,
  * and there is no structural link between `com.kubuszok:multiarch-serviceloader` and
  * `multiarch.serviceloader.ServiceProviders`. Deriving one from the other is a package-prefix guess
  * — §4.56's own hazard at a build coordinate, and precisely the reason `ENGINE-LIMITS.md` P8 stood
  * open. **The jar is the one authority on what the jar provides**, so this resolves the coordinate
  * and enumerates the class entries, and answers `Unverifiable` where it cannot.
  *
  * ==Which jar, and the limit that follows==
  * The **JVM** variant — `name_3` for a Scala or platform-crossed coordinate, the bare `name` for a
  * java one. For a `CrossKind.Platform` artifact that is one of three published jars, and the API
  * SURFACE is what these three share: `scala-java-time_sjs1_3` exists so that `java.time.Instant`
  * resolves on Scala.js, and it declares the same types the `_3` jar does. So the JVM listing is the
  * right approximation of "what this coordinate provides" — and it is an approximation, which is the
  * limit to state rather than to hide: a class published on ONE backend only is a class this reading
  * does not see. It costs an `Unused` cell where the true one is `Introduced`, which is a wrong
  * remove instruction, and nothing here can do better without resolving three jars for every
  * coordinate on every run. No corpus port has that shape; the day one does, this is the paragraph it
  * contradicts.
  *
  * ==The coordinate is built EXPLICITLY, never handed to `cs`'s defaults==
  * `cs fetch org::name:rev` picks the suffix from whatever Scala version that `cs` installation
  * defaults to, which is an ambient fact about the machine — two checkouts could read two different
  * jars and every count would agree. `org:name_3:rev` names one artifact and cannot drift.
  *
  * `--intransitive` is not an optimisation either: without it the resolution pulls the artifact's own
  * dependencies, `scala-library` first, and the provides-set becomes the whole of `scala.*` — after
  * which every port "references" every declared coordinate and the check answers `Covered` for
  * anything at all.
  *
  * ==Offline, and why a failure is not fatal here==
  * `ClasspathCache` — the same mechanism one module out, in `corpus`, which the engine cannot depend
  * on — makes a fetch failure FATAL, and rightly: a frontend with no classpath does not fail, it
  * resolves WRONGLY. Here the consequence is a single check column, so a failure is
  * [[DependencyCheck.Provides.Unverifiable]] and the run continues with that cell reported as the
  * unknown it is. What is NOT permitted is the empty set: `Known(Set.empty)` is indistinguishable
  * from "this artifact declares no class the port names", which is a remove instruction (§4.6).
  *
  * The CACHE follows `ClasspathCache`'s discipline for its reason — a cache keyed on existence alone
  * answers a question nobody asked after a coordinate bump — so the listing carries a sidecar holding
  * the exact invocation, and a listing whose sidecar does not match is refetched. It lives under
  * `<root>/.balticporter/`, which is gitignored and is where every other run capture goes (§3.7).
  */
object ArtifactIndex:

  /** the JVM artifact id, built from the coordinate's own `CrossKind` and never from `cs`'s default. */
  def coordinate(d: ArtifactDep, scalaBinary: String = "3"): String =
    val artifact = d.cross match
      case CrossKind.Java              => d.name
      case CrossKind.Scala | CrossKind.Platform => s"${d.name}_$scalaBinary"
    s"${d.org}:$artifact:${d.rev}"

  /** …and the whole invocation, which is also the cache fingerprint.
    *
    * `--intransitive` comes AFTER the repositories and not before, which is not style: `cs` reads
    * everything after that flag as a MODULE, so `--intransitive -r <url>` fails with
    * *malformed module: -r* — a resolution failure, which this object reports honestly as
    * `Unverifiable` and which is therefore exactly the shape that would have shipped as a permanent
    * unknown on the one port that needs the answer. Measured: liqp's coordinate read `unverifiable`
    * on the first live run with the flags the other way round. */
  def command(d: ArtifactDep, scalaBinary: String = "3"): List[String] =
    List("cs", "fetch") ++ d.resolver.toList.flatMap(r => List("-r", r)) ++
      List("--intransitive", coordinate(d, scalaBinary))

  /** every class an entry NAMES, as `Symbol.fullName` spells it, with each enclosing prefix beside it.
    *
    * Pure, and separated from the zip reading precisely so it can be tested without one. Three things
    * it does and one it deliberately does not:
    *
    *   - `META-INF/` is skipped whole. It carries no class a program can name, and `META-INF/versions/`
    *     (a multi-release jar) would otherwise contribute the SAME class twice under a version path
    *     that is not part of any name;
    *   - `module-info` and `package-info` are skipped by SIMPLE name: neither is a type a source can
    *     reference, and `module-info` in particular has no package at all;
    *   - a NESTED entry contributes its own name and every enclosing prefix, cut only at `$`. That is
    *     what lets the match be an equality test rather than a `startsWith`, which is the entire point
    *     (§4.56). A scala module class `Foo$` yields `Foo`, and a synthetic `Foo$$anon$1` yields `Foo`
    *     and stops — the empty segment after the first `$` is where a name stops being a name.
    *
    * What it does NOT do is decide whether an entry is "public API". A jar publishes what it
    * publishes; a check that guessed at visibility from a class file would be inventing the fact the
    * whole object exists to read. */
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

  /** the cache file for a coordinate, under the gitignored scratch root. `_` for every character a
    * coordinate may hold that a file name may not — the fingerprint sidecar is what decides
    * freshness, so two coordinates colliding on a name is a refetch and never a wrong answer. */
  def cacheFile(dir: Path, d: ArtifactDep): Path =
    dir.resolve(s"${d.org}.${d.name}.${d.rev}".replaceAll("[^A-Za-z0-9._-]", "_") + ".classes")

  private def sidecar(cache: Path): Path = cache.resolveSibling(cache.getFileName.toString + ".coords")

  /** WHAT THIS COORDINATE PROVIDES — from the cache when its fingerprint matches, else from `cs`.
    *
    * @param cacheDir where the listing lives; `None` resolves on every call, which is what a caller
    *   with no writable root should get rather than a silent miss
    * @param resolve the RESOLVER, a parameter for one reason: the arm that matters most here is the
    *   one that CANNOT resolve, and a spec that had to be offline to reach it is a spec nothing runs
    *   (§5.1's rule about `assume`-guarded specs, met by construction instead). The default is the
    *   real `cs` invocation and no caller in the engine passes anything else.
    */
  def provides(d: ArtifactDep, cacheDir: Option[Path], scalaBinary: String = "3",
               resolve: List[String] => Either[String, List[Path]] = fetch): DependencyCheck.Provides =
    val cmd = command(d, scalaBinary)
    val key = cmd.mkString(" ")
    val hit = cacheDir.map(dir => cacheFile(dir, d)).filter { c =>
      Files.exists(c) && Files.exists(sidecar(c)) && Files.readString(sidecar(c)).trim == key
    }
    hit match
      case Some(c) =>
        // an EMPTY cached listing is a listing, not an absence: a jar can legitimately declare no
        // class (a resources-only artifact), and the fingerprint says this is that jar's answer.
        DependencyCheck.Provides.Known(Files.readString(c).linesIterator.filter(_.nonEmpty).toSet)
      case scala.None =>
        resolve(cmd) match
          case Left(why) => DependencyCheck.Provides.Unverifiable(why)
          case Right(jars) =>
            // the ONE lookup where an absent value is normal is the FETCH, above. A jar that resolved
            // and cannot be READ is a different fact and says so — it is not "provides nothing", and
            // it is not silence either (§4.6).
            try
              val classes = classesIn(jars)
              cacheDir.foreach(dir => writeCache(dir, d, classes, key))
              DependencyCheck.Provides.Known(classes)
            catch
              case NonFatal(e) => DependencyCheck.Provides.Unverifiable(
                s"resolved ${jars.size} jar(s) and could not read them: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** …and the write is guarded ON ITS OWN, because a cache that cannot be written is not an answer
    * about the artifact. Folded into the read's `try` it would turn a perfectly good listing into an
    * `Unverifiable` on a read-only checkout — a diagnostic switch deciding a check's answer. */
  private def writeCache(dir: Path, d: ArtifactDep, classes: Set[String], key: String): Unit =
    try
      val c = cacheFile(dir, d)
      Files.createDirectories(dir)
      Files.writeString(c, classes.toList.sorted.mkString("\n"))
      // listing first, fingerprint second — a listing without its fingerprint is refetched, which is
      // the safe direction to be interrupted in (`ClasspathCache`'s own rule).
      Files.writeString(sidecar(c), key + "\n")
    catch case NonFatal(_) => ()

  /** `cs fetch --intransitive`, filtered to the jars — `ClasspathCache.fetch`'s two lessons, which
    * are not this object's to re-learn: merge the streams so a failure is reportable, then keep only
    * the lines that hold a jar, because the progress output once cached `Downloading https…` as a
    * classpath entry. */
  def fetch(cmd: List[String]): Either[String, List[Path]] =
    try
      val proc = new ProcessBuilder(cmd*).redirectErrorStream(true).start()
      val raw  = new String(proc.getInputStream.readAllBytes()).trim
      val jars = raw.linesIterator.filter(l => l.endsWith(".jar") && !l.contains(' ')).toList
      if proc.waitFor() != 0 || jars.isEmpty then
        Left(s"could not resolve `${cmd.mkString(" ")}` (is `cs` installed, and is this machine " +
          s"online?): ${raw.linesIterator.toList.takeRight(3).mkString(" / ")}")
      else Right(jars.map(Path.of(_)).filter(Files.exists(_)))
    catch
      case NonFatal(e) =>
        Left(s"could not run `${cmd.mkString(" ")}`: ${e.getClass.getSimpleName}: ${e.getMessage}")

  /** the whole supplier a run hands `DependencyCheck.declarations`, memoised per RUN.
    *
    * Memoised because the 2×2 asks about the same coordinate up to twice (once per program) and a
    * cold cache would then resolve it twice; a `Map` built here rather than a global for the reason
    * every other run-scoped table has one — two runs in one JVM are two answers (§5.1). */
  def supplier(cacheDir: Option[Path], scalaBinary: String = "3",
               resolve: List[String] => Either[String, List[Path]] = fetch): ArtifactDep => DependencyCheck.Provides =
    val memo = scala.collection.mutable.Map.empty[(String, String, String), DependencyCheck.Provides]
    d => memo.getOrElseUpdate((d.org, d.name, d.rev), provides(d, cacheDir, scalaBinary, resolve))

  /** the scratch directory a run caches into — `<root>/.balticporter/artifact-index`, the gitignored
    * location every other run capture already uses (§3.7), anchored on the same `root` the debug
    * flags are. Always `Some`: [[writeCache]] degrades on its own, so a root that cannot be written
    * costs a refetch and never an answer. */
  def defaultCacheDir: Option[Path] =
    Some(balticporter.tir.DebugFlags.root.resolve(".balticporter").resolve("artifact-index"))
