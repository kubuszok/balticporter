package balticporter.corpus

import java.nio.file.{Files, Path}

/** The cache every port's classpath is resolved through — and the ONE question it exists to ask.
  *
  * A cache keyed on "does the file exist?" answers a coordinate BUMP with the versions the port
  * used to declare, and the failure that follows is the worst kind this project has: an import that
  * resolves WRONGLY rather than failing, so the port emits nonsense and reports success (CLAUDE.md
  * §5.1). No count moves and the port still compiles, so nothing but this can see it.
  *
  * No `cs` here: what is under test is the freshness decision, which is pure file state.
  */
class ClasspathCacheSpec extends munit.FunSuite:

  private def tmp(name: String): Path =
    val d = Files.createTempDirectory("bp-classpath-cache")
    d.toFile.deleteOnExit()
    d.resolve(name)

  test("a line written for THESE coordinates is reused") {
    val f = tmp("cp.txt")
    val k = ClasspathCache.key(List("junit:junit:4.12"))
    ClasspathCache.write(f, "/jars/junit-4.12.jar", k)
    assert(ClasspathCache.fresh(f, k))
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
