package balticporter.corpus.simplegraphs

import balticporter.corpus.ClasspathCache
import balticporter.runner.PortConfig

import java.nio.file.{Files, Path}

/** Port simple-graphs' own JUnit suite (`src/test/java`) through the same pipeline as `src/main/java`.
  *
  *   corpus/runMain balticporter.corpus.simplegraphs.SimpleGraphsTestMigrate [--determinism=full]
  *
  * The port is `balticporter/corpus/ports/simplegraphs/test.conf`; this `main` names it, ensures the
  * test classpath exists, and gives the run its report identity. 7 files, 16 `@Test` -- the only
  * behavioural evidence this port can have (CLAUDE.md §3: compiling is not passing). Exercises the
  * translations this library forced into the engine from the outside: `Comparator` ordering,
  * `java.util.stream` chain collapse, a deliberately colliding `hashCode` (`BadHashInteger`,
  * testing `NodeMap`'s open addressing), and the COLLECTION surface K5 covers
  * (`containsAll`/`removeAll`/`retainAll`/`removeIf`/`toArray`). A DEPENDENT of
  * [[SimpleGraphsMigrate]]: resolves against `src/main/java`, never the emitted Scala, inheriting
  * the base's rename and surface phases via `base = "main.conf"` (CLAUDE.md §1.5).
  */
object SimpleGraphsTestMigrate:

  def main(args: Array[String]): Unit =
    SimpleGraphsClasspath.ensure(SimpleGraphsPort.repoRoot)
    PortConfig.load(SimpleGraphsPort.conf("test.conf"), args.toSeq).execute()

/** simple-graphs' TEST-scope dependency, for shadow-class resolution only. JUnit 4 and nothing
  * else -- `build.gradle` declares exactly `junit:junit:4.12` (`AshleyClasspath`'s rule: resolve
  * what the library DECLARES, never guess). `TestFrameworkTransform` converts the JUnit surface to
  * MUnit, so the jar is a frontend input only. Written to a FILE rather than inlined in the conf,
  * since a config naming a COMMAND to run would be the strings-that-are-secretly-code the
  * transform SPI exists to keep out (CLAUDE.md §1.5).
  */
object SimpleGraphsClasspath:

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/simplegraphs-test-classpath.txt")

  /** the version simple-graphs' own `build.gradle` declares. */
  val Coordinates: List[String] = List("junit:junit:4.12")

  /** Guarantee the cache file exists AND was resolved from the coordinates above, fetching once if
    * not. A failure is FATAL rather than an empty classpath: an unresolved
    * `import static org.junit.Assert.assertEquals` resolves WRONGLY rather than failing, and the
    * port emits nonsense and reports success. */
  def ensure(repoRoot: Path): Path =
    ClasspathCache.ensure(cache(repoRoot), "simple-graphs-test", Coordinates)
