package balticporter.corpus

import java.nio.file.Path

/** The `gdx-jnigen-loader` jar libGDX's own build declares (`gdx/build.gradle`): upstream moved
  * `com.badlogic.gdx.utils.SharedLibraryLoader` out of `gdx/src` into that artifact, so a port of
  * `gdx/src` alone reaches it only through the frontend classpath. Without it Spoon resolves no
  * declaration and the symbol reads UNRESOLVED (`Flags.isUnresolved`). ONE definition. */
object JnigenClasspath:

  val Coordinates: List[String] = List("com.badlogicgames.gdx:gdx-jnigen-loader:2.5.2")

  def cache(repoRoot: Path): Path = repoRoot.resolve("out/jnigen-classpath.txt")

  def entries(repoRoot: Path): List[Path] =
    ClasspathCache.entries(cache(repoRoot), "jnigen", Coordinates)
