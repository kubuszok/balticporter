package balticporter.corpus.flexmark

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **flexmark-java** — milestone 1: `flexmark` core plus the eleven `flexmark-util-*` split libraries (486 java files, 458 declaring a type): `.../ports/ssg-md/main.conf`, plus
  * [[FlexmarkClasspath]]. LARGEST java surface either reference repository has, a first for a MAVEN MULTI-MODULE TREE WITH ONE PACKAGE ROOT. SKELETON milestone — not expected to compile, the first
  * error census is MEASURED.
  */
object FlexmarkMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, and where its upstream is, for the `main`s that name them.
  */
object FlexmarkPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/ssg-md").resolve(name)

  /** flexmark's upstream checkout -- a vendored tree under ssg, like liqp's. Stated once here and once, conf-relatively, in `main.conf`; the two must agree.
    */
  def upstream: Path = repoRoot.resolve("../ssg/original-src/flexmark-java").normalize

/** flexmark's FRONTEND classpath: one jar. `org.jetbrains:annotations` is compile-scope on every `flexmark-util-*` pom (594 files import `@NotNull`/`@Nullable`), version read from the poms that
  * DECLARE it — an unresolvable annotation resolves WRONGLY rather than failing. ONE list, not one per source set (a COMPILE-scope coordinate is visible everywhere). Mechanism is
  * [[balticporter.corpus.ClasspathCache]], shared with every port.
  */
object FlexmarkClasspath:

  /** exactly what the poms of the modules IN SCOPE declare at compile scope. A module joining a scope is a coordinate this list may grow to carry.
    */
  val Coordinates: List[String] = List(
    "org.jetbrains:annotations:24.0.1",
    "org.nibor.autolink:autolink:0.6.0"
  )

  /** the file the markdown configurations read as `@work/flexmark-classpath.txt`. */
  val FileName = "flexmark-classpath.txt"

  def cache(repoRoot: Path): Path = repoRoot.resolve("out").resolve(FileName)

  /** Guarantee the classpath file exists, resolving it once if it does not. Returns its path. */
  def ensure(repoRoot: Path): Path = ensureIn(repoRoot.resolve("out"))

  /** The same, in the directory the caller gives the configurations as their `work` root — a consumer's own `target/`, never this repository. */
  def ensureIn(work: Path): Path =
    ClasspathCache.ensure(work.resolve(FileName), "ssg-md", Coordinates)
