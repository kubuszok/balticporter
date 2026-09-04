package balticporter.corpus

import java.nio.file.{Files, Path}

/** The cache every port's classpath is resolved through — and the ONE question it exists to ask. */
class ClasspathCacheSpec extends munit.FunSuite:

  private def tmp(name: String): Path =
    val d = Files.createTempDirectory("bp-classpath-cache")
    d.toFile.deleteOnExit()
    d.resolve(name)

  /** a jar that EXISTS, because freshness now asks that of every entry */
  private def jar(name: String): String =
    val f = Files.createTempFile("bp-classpath-cache", name); f.toFile.deleteOnExit(); f.toString

  test("a line written for THESE coordinates is reused") {
    val f = tmp("cp.txt")
    val k = ClasspathCache.key(List("junit:junit:4.12"))
    ClasspathCache.write(f, jar("junit-4.12.jar"), k)
    assert(ClasspathCache.fresh(f, k))
  }

  test("a line whose jar has been EVICTED from the resolver cache is NOT reused — the coordinates agree and the file is gone") {
    val f = tmp("cp.txt")
    val k = ClasspathCache.key(List("com.github.tommyettinger:regexodus:0.1.21"))
    val gone = jar("regexodus-0.1.21.jar")
    ClasspathCache.write(f, jar("kept.jar") + java.io.File.pathSeparator + gone, k)
    assert(ClasspathCache.fresh(f, k))
    Files.delete(Path.of(gone))
    assert(!ClasspathCache.fresh(f, k), "one missing entry is a miss: Spoon refuses the whole model on it")
  }

  test("a line written for DIFFERENT coordinates is NOT reused — the whole point") {
    val f = tmp("cp.txt")
    ClasspathCache.write(f, "/jars/junit-4.12.jar", ClasspathCache.key(List("junit:junit:4.12")))
    // the version bump a port makes in its `Coordinates` list, and nothing else
    assert(!ClasspathCache.fresh(f, ClasspathCache.key(List("junit:junit:4.13.2"))))
  }

  test("…and so is a line written with different RESOLVER ARGS — an exclusion is a classpath too") {
    val f = tmp("cp.txt")
    val coords = List("com.github.crykn.guacamole:gdx:v0.3.6")
    ClasspathCache.write(f, "/jars/g.jar", ClasspathCache.key(coords, List("-r", "https://jitpack.io")))
    assert(!ClasspathCache.fresh(f, ClasspathCache.key(coords)))
  }

  test("a cache from BEFORE this check — a line with no fingerprint — is refetched, never trusted") {
    val f = tmp("cp.txt")
    Files.createDirectories(f.getParent)
    Files.writeString(f, "/jars/junit-4.12.jar")
    assert(!ClasspathCache.fresh(f, ClasspathCache.key(List("junit:junit:4.12"))))
  }

  test("an EMPTY line is not fresh however it was fingerprinted") {
    val f = tmp("cp.txt")
    val k = ClasspathCache.key(List("junit:junit:4.12"))
    ClasspathCache.write(f, "   ", k)
    assert(!ClasspathCache.fresh(f, k))
  }

  test("the fingerprint is ORDER-SENSITIVE — `cs` resolves highest-version-wins across the set") {
    assertNotEquals(
      ClasspathCache.key(List("a:a:1", "b:b:2")),
      ClasspathCache.key(List("b:b:2", "a:a:1")),
    )
  }

  test("the cache FILE holds only the classpath — a header there would be a bogus entry") {
    // `PortConfig.classpathFile` splits the whole text on the path separator, so the fingerprint
    // lives in a sidecar and never in the file a conf reads.
    val f = tmp("cp.txt")
    ClasspathCache.write(f, "/jars/a.jar:/jars/b.jar", ClasspathCache.key(List("a:a:1")))
    assertEquals(Files.readString(f), "/jars/a.jar:/jars/b.jar")
  }
