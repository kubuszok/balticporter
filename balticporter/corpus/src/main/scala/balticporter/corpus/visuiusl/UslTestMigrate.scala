package balticporter.corpus.visuiusl

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.nio.file.Path

/** Port USL's own JUnit suite (`usl/src/test/java`) through the same pipeline as its main sources.
  *
  *   corpus/runMain balticporter.corpus.visuiusl.UslTestMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/visui-usl/test.conf`; this `main` names it and gives the
  * run its report identity. 2 files, 7 `@Test` -- seven of VisUI's whole checkout's nine live
  * `@Test`s (the other two are in `ui/`), so the module the sibling port deferred holds most of the
  * library's own behavioural evidence.
  *
  * The best-shaped suite in the corpus: six live tests are one method (parse a `.usl` resource,
  * compare against a `-expected.json` resource upstream also wrote), a CONFORMANCE suite over an
  * end-to-end run of the lexer, parser, style merger and JSON writer together -- exactly the
  * instrument that catches §4.4's forms compiling cleanly into *slightly different output*.
  *
  * `RemoteTest.testRemote` (downloads templates over HTTP) is kept `@Ignore`d, reproducing java's
  * own decision (`ignored` is a DECISION, `skipped` is PREVENTION, §5.1) rather than dropping it.
  *
  * A DEPENDENT of [[UslMigrate]]: resolves against `usl/src/main/java`, inheriting the base's
  * rename and surface phases via `base = "main.conf"` (CLAUDE.md §1.5).
  */
object UslTestMigrate:

  def main(args: Array[String]): Unit =
    UslTestClasspath.ensure(UslPort.repoRoot)
    PortConfig.load(UslPort.conf("test.conf"), args.toSeq).execute()

/** USL's TEST-scope dependency, for shadow-class resolution only. JUnit 4 and nothing else --
  * `usl/build.gradle` + the root `build.gradle`'s `junitVersion` pin `4.13.2` (`AshleyClasspath`'s
  * read-the-declaration rule). `TestFrameworkTransform` converts the JUnit surface to MUnit, so
  * the jar is a frontend input only; an unresolved `import static ...assertEquals` would resolve
  * WRONGLY rather than failing.
  */
object UslTestClasspath:

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/usl-test-classpath.txt")

  /** the version `usl/build.gradle` + the root `build.gradle`'s `junitVersion` declare. */
  val Coordinates: List[String] = List("junit:junit:4.13.2")

  def ensure(repoRoot: Path): Path =
    ClasspathCache.ensure(cache(repoRoot), "usl-test", Coordinates)
