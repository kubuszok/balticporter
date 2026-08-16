package balticporter.corpus.flexmark

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.nio.file.Path

/** Migrate **flexmark-java** — milestone 1: the `flexmark` core module plus the eleven
  * `flexmark-util-*` split libraries (486 java files, 458 of them declaring a type).
  *
  * corpus/runMain balticporter.corpus.flexmark.FlexmarkMigrate [--determinism=full]
  *
  * ==This program is a `main` and a classpath, and that is all==
  * The port is `balticporter/corpus/ports/ssg-md/main.conf` — read that, not this file. What is here is
  * the `main` that names the configuration and gives the run its report identity
  * (`CheckReport.dir` is derived from the main class's simple name, so a per-port `main` is what
  * keeps `port-report/FlexmarkMigrate` a stable measurement baseline across a module rename —
  * CLAUDE.md §2.1), plus [[FlexmarkClasspath]], which is the one thing a conf cannot hold: a
  * classpath is produced by a resolver, and a config file naming a command to run would be the
  * strings-that-are-secretly-code the transform SPI exists to keep out (CLAUDE.md §1.5).
  *
  * ==Why flexmark is in the corpus==
  * It is the LARGEST java surface either reference repository has, and it is the second library
  * from outside the gdx/sge family. Four things no corpus library has exercised before:
  *
  *   1. **A MAVEN MULTI-MODULE TREE WITH ONE PACKAGE ROOT.** Fifty-three modules, all declaring
  *      under `com.vladsch.flexmark`, so the module split is a fact about `pom.xml` files and not
  *      about java. Every earlier port's `sourceRoot` was either a package root or a two-module
  *      ancestor; this one is a checkout with twelve `src/main/java` trees selected out of it, and
  *      it is the shape a later milestone's 29 DEPENDENT extension ports have to inherit.
  *   2. **A LIBRARY THAT IS ITSELF A PARSER.** flexmark is a CommonMark implementation: a
  *      character-level block/inline parser, a `BasedSequence` family that is 62 files of
  *      subsequence arithmetic, and a formatter. That is a density of `switch`, `break`,
  *      post-increment and `char` arithmetic no ported library has had — i.e. the whole of
  *      CLAUDE.md §4.4's control-flow half asked at once.
  *   3. **AN ANNOTATION-DRIVEN NULLABILITY SIGNAL.** 594 of the covered files import
  *      `org.jetbrains.annotations.{NotNull, Nullable}`, and the reference hand port turns them
  *      into a `Nullable[A]` wrapper in 300 files. `NullabilityTransform` was written with the
  *      observation that seven of eleven surveyed upstreams carry NO annotations; this is the
  *      first corpus library squarely in the annotated minority. It is NOT configured at
  *      milestone 1 — see the conf's D-md-5 for why a hypothesis about the hand port gets a
  *      number before it becomes a manifest entry (CLAUDE.md §3.5).
  *   4. **A REAL CONFORMANCE ORACLE.** Upstream ships six versions of the CommonMark spec (618 to
  *      652 examples each) as classpath resources, and four `FullOrigSpec*CoreTest` classes drive
  *      them through plain `@Test` methods — not `@RunWith(Parameterized.class)`, so they are
  *      inside `TestFrameworkTransform`'s mechanical reach as they stand. The reference hand port
  *      loads none of them. That is evidence about behaviour this project has never had for any
  *      library, and it belongs to a later milestone rather than to this one.
  *
  * ==Milestone 1 is the SKELETON and the WALL==
  * This port is not expected to compile. It exists so that the first error census is a MEASURED
  * number classified per CLAUDE.md §1 rather than an estimate, and the wall is worked down after
  * that census exists. `PROGRESS.md` §10.6 holds the numbers.
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

  /** flexmark's upstream checkout — a vendored tree under **ssg**, like liqp's and unlike the rest
    * of the corpus. Stated once here and once, conf-relatively, in `main.conf`; the two must agree,
    * and nothing compares them, so a change is a change in both places. */
  def upstream: Path = repoRoot.resolve("../ssg/original-src/flexmark-java").normalize

/** flexmark's FRONTEND classpath: one jar.
  *
  * `org.jetbrains:annotations` is compile-scope on every `flexmark-util-*` pom and 594 of the
  * covered files import `@NotNull`/`@Nullable` from it. The version is read from the poms that
  * DECLARE it (`flexmark-util-misc/pom.xml` and its siblings pin `24.0.1` inline; this tree has no
  * `dependencyManagement` entry for it), which is `AshleyClasspath`'s rule — guessing a modern
  * Mockito there cost a full cycle.
  *
  * ==Why it is here at all, given that nothing emitted names it==
  * The annotations are MARKERS: this milestone declares no `preservedAnnotations`, so not one of
  * them reaches the emitted Scala, and a reader could reasonably conclude the jar is unnecessary.
  * It is not. An annotation type Spoon cannot resolve does not fail the frontend — it resolves
  * WRONGLY, and every declaration carrying one is then modelled from a guess (CLAUDE.md §5.1). The
  * jar's job is finished before a single line is emitted, which is exactly why nothing downstream
  * could report its absence.
  *
  * ==What is deliberately NOT here==
  * `org.nibor.autolink`, which `flexmark-ext-autolink` needs — that module is milestone 2, and a
  * coordinate resolved for a scope this port does not parse is a coordinate nothing can contradict.
  * `com.ibm.icu`, `commons-io` and `org.jsoup` reach only the converter modules, which are out of
  * scope entirely.
  *
  * The mechanism — the `cs` invocation, the stream merge, the jar-line filter and the COORDINATE
  * FINGERPRINT that makes a version bump refetch rather than silently reuse — is
  * [[balticporter.corpus.ClasspathCache]], shared with every other port.
  */
object FlexmarkClasspath:

  /** exactly what the `flexmark-util-*` poms declare at compile scope. */
  val Coordinates: List[String] = List(
    "org.jetbrains:annotations:24.0.1",
  )

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/flexmark-classpath.txt")

  /** Guarantee the classpath file exists, resolving it once if it does not. Returns its path. */
  def ensure(repoRoot: Path): Path =
    ClasspathCache.ensure(cache(repoRoot), "ssg-md", Coordinates)
