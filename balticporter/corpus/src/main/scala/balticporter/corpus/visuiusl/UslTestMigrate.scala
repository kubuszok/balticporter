package balticporter.corpus.visuiusl

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.nio.file.Path

/** Port USL's own JUnit suite (`usl/src/test/java`) through the same pipeline as its main sources.
  *
  *   corpus/runMain balticporter.corpus.visuiusl.UslTestMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/visui-usl/test.conf`; this `main` names it, ensures the
  * test classpath the conf points at exists, and gives the run its report identity. See
  * [[UslMigrate]] for why that is all a migration program is.
  *
  * ==2 files, 7 `@Test`, and SEVEN OF THE CHECKOUT'S NINE LIVE HERE==
  * VisUI's whole upstream checkout declares 9 real `@Test`. **Two** are in `ui/`
  * (`GreaterThanValidatorTest`, `LesserThanValidatorTest`) and the other **seven** are here — six
  * in `TemplateBasedParserTest` and one `@Ignore`d in `RemoteTest`. So the module the sibling port
  * deferred is the module that holds three quarters of the library's own behavioural evidence, and
  * `just usl-test-measure` re-derives both numbers on every run rather than trusting this comment.
  *
  * ==WHAT THE SUITE ACTUALLY ASSERTS, and why it is the best-shaped suite in the corpus==
  * All six live tests are one method with two arguments: parse a `.usl` resource, compare the JSON
  * against a `-expected.json` resource checked in beside it. Upstream wrote BOTH sides. So this is
  * not a suite that tests the library's incidental surface — it is a CONFORMANCE suite over the one
  * thing the library does, and every assertion in it is an end-to-end run of the lexer, the parser,
  * the style merger and the JSON writer together.
  *
  * That matters for what a failure MEANS here. §4.4's forms — post-increment read as a value (28
  * sites in these sources), a `break` that ran on, a reference `==` — are exactly the defects that
  * compile cleanly and produce *slightly different output*, and a line-by-line JSON comparison over
  * six real templates is the instrument that catches them. CLAUDE.md §3: assertions are the only
  * evidence of behaviour this project can have.
  *
  * ==THE `@Ignore`d ONE IS KEPT, and that is a decision rather than an oversight==
  * `RemoteTest.testRemote` downloads three USL templates over HTTP. It is `@Ignore`d upstream, and
  * `TestFrameworkTransform` renders `@Ignore` as MUnit's ignored test — so the port reproduces
  * java's own decision instead of taking its own. An `ignored` outcome is kept apart from a
  * `skipped` one precisely because the first is a DECISION and the second is PREVENTION (§5.1), and
  * dropping the file would have turned a recorded decision into a silent absence.
  *
  * A DEPENDENT of [[UslMigrate]]: it resolves against `usl/src/main/java`, never against the Scala
  * that port emitted, so the conf's `base = "main.conf"` inherits the base's rename and surface
  * phases rather than restating them (CLAUDE.md §1.5).
  */
object UslTestMigrate:

  def main(args: Array[String]): Unit =
    UslTestClasspath.ensure(UslPort.repoRoot)
    PortConfig.load(UslPort.conf("test.conf"), args.toSeq).execute()

/** USL's TEST-scope dependency, for shadow-class resolution only.
  *
  * JUnit 4 and nothing else — `usl/build.gradle` declares exactly
  * `testImplementation "junit:junit:$junitVersion"` and the root `build.gradle` binds
  * `junitVersion = '4.13.2'`. That version is used rather than a current one, which is the rule
  * `AshleyClasspath` records with its cost: Ashley's suite needs Mockito **1.10.19** because
  * `ComponentClassFactory` uses `org.mockito.asm`, removed in 2.x, and guessing a modern version
  * cost a full cycle. A port resolves what the library DECLARES.
  *
  * `TestFrameworkTransform` converts the JUnit surface to MUnit, so the jar is a frontend input
  * only and never reaches the emitted code — which is also why the ONE assertion this suite makes
  * (`Assert.assertEquals(String, Object, Object)`, JUnit's three-argument form with a message) has
  * to resolve here: an `import static org.junit.Assert.assertEquals` the frontend cannot resolve
  * does not fail, it resolves WRONGLY to an unqualified call on the enclosing test class, and the
  * port then emits nonsense and reports success.
  */
object UslTestClasspath:

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/usl-test-classpath.txt")

  /** the version `usl/build.gradle` + the root `build.gradle`'s `junitVersion` declare. */
  val Coordinates: List[String] = List("junit:junit:4.13.2")

  def ensure(repoRoot: Path): Path =
    ClasspathCache.ensure(cache(repoRoot), "usl-test", Coordinates)
