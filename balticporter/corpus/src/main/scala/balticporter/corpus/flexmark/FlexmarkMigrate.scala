package balticporter.corpus.flexmark

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **flexmark-java** -- milestone 1: the `flexmark` core module plus the eleven
  * `flexmark-util-*` split libraries (486 java files, 458 of them declaring a type).
  *
  * corpus/runMain balticporter.corpus.flexmark.FlexmarkMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/ssg-md/main.conf` -- read that, not this file.
  * This is the `main` that names it and gives the run its report identity, plus
  * [[FlexmarkClasspath]] (a classpath is produced by a resolver, which a `.conf` cannot
  * hold, CLAUDE.md §1.5).
  *
  * The LARGEST java surface either reference repository has, and the second library from
  * outside the gdx/sge family. Four firsts: a MAVEN MULTI-MODULE TREE WITH ONE PACKAGE
  * ROOT (53 modules under `com.vladsch.flexmark`); A LIBRARY THAT IS ITSELF A PARSER (dense
  * `switch`/`break`/post-increment/`char` arithmetic, CLAUDE.md §4.4's control-flow half at
  * once); AN ANNOTATION-DRIVEN NULLABILITY SIGNAL (594 files import
  * `org.jetbrains.annotations`, not configured at milestone 1 -- see the conf's D-md-5,
  * CLAUDE.md §3.5); and A REAL CONFORMANCE ORACLE (six CommonMark spec versions as
  * classpath resources, driven by plain `@Test` methods -- a later milestone).
  *
  * Milestone 1 is the SKELETON and the WALL: not expected to compile, so the first error
  * census is MEASURED and classified per CLAUDE.md §1 (`PROGRESS.md` §10.6 holds the
  * numbers).
  */
object FlexmarkMigrate:

  def main(args: Array[String]): Unit =
    FlexmarkClasspath.ensure(FlexmarkPort.repoRoot)
    PortConfig.load(FlexmarkPort.conf("main.conf"), args.toSeq).execute()

/** Where this port's configuration lives, and where its upstream is, for the `main`s that name
  * them. */
object FlexmarkPort:

  def repoRoot: Path =
    Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize

  def conf(name: String): Path = repoRoot.resolve("balticporter/corpus/ports/ssg-md").resolve(name)

  /** flexmark's upstream checkout -- a vendored tree under ssg, like liqp's. Stated once
    * here and once, conf-relatively, in `main.conf`; the two must agree. */
  def upstream: Path = repoRoot.resolve("../ssg/original-src/flexmark-java").normalize

/** flexmark's FRONTEND classpath: one jar. `org.jetbrains:annotations` is compile-scope on
  * every `flexmark-util-*` pom (594 files import `@NotNull`/`@Nullable`); version read from
  * the poms that DECLARE it (`AshleyClasspath`'s rule -- guessing cost a full cycle once).
  *
  * Needed even though nothing emitted names it: an annotation type Spoon cannot resolve
  * resolves WRONGLY rather than failing (CLAUDE.md §5.1), so every declaration carrying one
  * would be modelled from a guess.
  *
  * `com.ibm.icu`/`commons-io`/`org.jsoup` reach only the out-of-scope converter modules.
  * `org.nibor.autolink` is here because milestone 2 parses `flexmark-ext-autolink`, at the
  * version its own pom declares (0.6.0).
  *
  * ONE list, not one per source set: a COMPILE-scope coordinate of one module is visible to
  * every source set resolving against that module (unlike a TEST-scope one), so a derived
  * `FlexmarkExtClasspath` would be a second object for the same principle this file already
  * states -- measured flat (`md-measure`: 0 moved digests) across the change.
  *
  * The mechanism is [[balticporter.corpus.ClasspathCache]], shared with every other port.
  */
object FlexmarkClasspath:

  /** exactly what the poms of the modules IN SCOPE declare at compile scope. A module
    * joining a scope is a coordinate this list may grow to carry. */
  val Coordinates: List[String] = List(
    "org.jetbrains:annotations:24.0.1",
    "org.nibor.autolink:autolink:0.6.0",
  )

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/flexmark-classpath.txt")

  /** Guarantee the classpath file exists, resolving it once if it does not. Returns its path. */
  def ensure(repoRoot: Path): Path =
    ClasspathCache.ensure(cache(repoRoot), "ssg-md", Coordinates)
