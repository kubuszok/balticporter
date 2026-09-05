package balticporter.corpus

import java.nio.file.Path

/** The lls jar every port that retargets to `lowlevel.util.*` needs on its frontend classpath,
  * so `FrontendConfig.internTypes` can read `isFinal` and parents from the class file (K18).
  * ONE definition; every migrator calls `entries`. */
object LlsClasspath:

  val Coordinates: List[String] = List("com.kubuszok:lls_3:0.3.0")

  /** Exclude the Scala stdlib — adding it to Spoon's source classpath confuses ECJ's resolution
    * and produces 218 errors where the test port expected 0. Only the lls jar is needed. */
  private val ExcludeArgs: List[String] = List(
    "--exclude", "org.scala-lang:scala3-library_3",
    "--exclude", "org.scala-lang:scala-library",
  )

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/lls-classpath.txt")

  def entries(repoRoot: Path): List[Path] =
    ClasspathCache.entries(cache(repoRoot), "lls", Coordinates, ExcludeArgs)
