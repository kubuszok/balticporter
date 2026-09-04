package balticporter.corpus.flexmark

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.io.File
import java.nio.file.{Files, Path}

/** Port **flexmark-util**'s own JUnit suite — 52 files, **730 plain `@Test`** — through the
  * same pipeline as milestone 1's twelve modules: `.../ports/ssg-md/test.conf`. The port's
  * FIRST behavioural evidence (§3: an error count is typer-only), living in the `flexmark-util`
  * AGGREGATOR module. Its two refusals (`@Rule ExpectedException`, nine `@RunWith(Suite.class)`)
  * are reported with §1's classification, as a DEPENDENT of [[FlexmarkMigrate]] (§1.5). */
object FlexmarkTestMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkTestClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("test.conf"), args.toSeq).execute()

/** flexmark's TEST frontend classpath: everything [[FlexmarkClasspath]] resolves, plus JUnit —
  * exactly ONE test-scope coordinate (`junit:junit`, version pinned by the parent pom's
  * `dependencyManagement`); `hamcrest-core` arrives transitively and is deliberately NOT named.
  * The MAIN classpath is included since `resolutionRoots` is flexmark's Java source, delegated
  * to [[FlexmarkClasspath]]'s `ensure`, never duplicated. Written to a FILE, not inlined (§1.5). */
object FlexmarkTestClasspath:

  /** the one test-scope coordinate the poms declare. hamcrest-core 1.3 is its transitive. */
  val TestCoordinates: List[String] = List("junit:junit:4.13.2")

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/flexmark-test-classpath.txt")

  /** Guarantee the test classpath file exists and AGREES with the main one, building it if
    * not: the cached line is reusable only if it still STARTS with the main classpath,
    * entry for entry, AND was resolved from the test coordinates this port declares NOW. */
  def ensure(repoRoot: Path): Path =
    val mainEntries =
      Files
        .readString(FlexmarkClasspath.ensure(repoRoot))
        .trim
        .split(File.pathSeparator)
        .filter(_.nonEmpty)
        .toList
    val out      = cache(repoRoot)
    val key      = ClasspathCache.key(TestCoordinates)
    val existing = if Files.exists(out) then Files.readString(out).trim else ""
    val carried  = existing.split(File.pathSeparator).filter(_.nonEmpty).toList
    if ClasspathCache.fresh(out, key) && carried.startsWith(mainEntries) &&
       carried.sizeIs > mainEntries.size
    then out
    else
      val junit = ClasspathCache.fetch("ssg-md-test", TestCoordinates)
      // `distinct` rather than a union: the ORDER is the resolution order `cs` produced, and the
      // main entries come first because they are what the resolution roots are read against.
      ClasspathCache.write(out, (mainEntries ++ junit).distinct.mkString(File.pathSeparator), key)
