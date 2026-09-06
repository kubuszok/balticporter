package balticporter.corpus

import java.nio.file.Path

/** The libGDX core jar at the VENDORED tree's version, for a port that emits a SUBSET of `gdx/src`:
  * the few types the subset references outside itself resolve as class-file externals (K15), where a
  * whole-tree resolution root would ask a base contract for every type (ENGINE-LIMITS.md K43). */
object GdxCoreClasspath:
  val Coordinates: List[String] = List("com.badlogicgames.gdx:gdx:1.14.1")
  def cache(repoRoot: Path): Path = repoRoot.resolve("out/gdx-core-classpath.txt")
  def entries(repoRoot: Path): List[Path] =
    ClasspathCache.entries(cache(repoRoot), "gdx-core", Coordinates)
