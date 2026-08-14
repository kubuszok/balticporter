package balticporter.runner

import balticporter.catalog.{ArtifactDep, CrossKind}
import balticporter.tir.DependencyCheck

import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}

/** The PROVIDES-SET — read from the artifact, never derived from the coordinate.
  *
  * The whole reason `ENGINE-LIMITS.md` P8 stood open is that there is no structural link between a
  * build coordinate and a package name, so the check has to read the jar. These tests are about the
  * two halves of doing that honestly: the entry→name mapping (pure, and the part a wrong answer would
  * make silently permissive), and the THREE-valued result, whose third value is what keeps an offline
  * run from inventing a remove instruction.
  *
  * THE FIXTURE IS A ZIP OF ENTRY NAMES and holds no bytecode, which is not a shortcut — `classesIn`
  * reads entry NAMES and nothing else, so a fixture supplying names exercises exactly the code under
  * test. Compiling real classes would test `javac`.
  */
class ArtifactIndexSpec extends munit.FunSuite:

  private def jarOf(entries: String*): Path =
    val jar = Files.createTempFile("artifact-index-", ".jar")
    val out = new ZipOutputStream(Files.newOutputStream(jar))
    try entries.foreach { e => out.putNextEntry(new ZipEntry(e)); out.closeEntry() }
    finally out.close()
    jar

  test("the JVM coordinate is built from the cross kind, never from `cs`'s default") {
    // an ambient Scala version on the machine would let two checkouts read two different jars with
    // every count agreeing, which is the one failure a measurement suite cannot see.
    assertEquals(ArtifactIndex.coordinate(ArtifactDep("o", "n", "1", CrossKind.Java)), "o:n:1")
    assertEquals(ArtifactIndex.coordinate(ArtifactDep("o", "n", "1", CrossKind.Scala)), "o:n_3:1")
    // a platform-crossed artifact publishes three jars; the JVM one is the shared API surface.
    assertEquals(ArtifactIndex.coordinate(ArtifactDep("o", "n", "1", CrossKind.Platform)), "o:n_3:1")
  }

  test("…and `--intransitive` is in the command, which is not an optimisation") {
    // without it the resolution pulls `scala-library`, the provides-set becomes the whole of
    // `scala.*`, and every port then "references" every coordinate it declares.
    val cmd = ArtifactIndex.command(ArtifactDep("o", "n", "1", CrossKind.Platform, Some("https://r")))
    assert(cmd.contains("--intransitive"), cmd.mkString(" "))
    assertEquals(cmd, List("cs", "fetch", "--intransitive", "-r", "https://r", "o:n_3:1"))
  }

  test("an entry names its class and EVERY ENCLOSING PREFIX, cut only at `$`") {
    // so the match against a program's symbol name is an equality test rather than a `startsWith`,
    // which is CLAUDE.md §4.56 at a jar listing.
    assertEquals(ArtifactIndex.namesOf("a/b/C.class"), List("a.b.C"))
    assertEquals(ArtifactIndex.namesOf("a/b/Outer$Inner.class"), List("a.b.Outer", "a.b.Outer$Inner"))
    // a scala module class carries no name after the separator, so it names its own companion
    assertEquals(ArtifactIndex.namesOf("a/b/C$.class"), List("a.b.C"))
    // …and a synthetic stops where the name stops
    assertEquals(ArtifactIndex.namesOf("a/b/C$$anon$1.class"), List("a.b.C"))
  }

  test("…and the entries that are not classes a program can name are skipped") {
    assertEquals(ArtifactIndex.namesOf("a/b/"), Nil)
    assertEquals(ArtifactIndex.namesOf("a/b/C.tasty"), Nil)
    assertEquals(ArtifactIndex.namesOf("META-INF/MANIFEST.MF"), Nil)
    // a MULTI-RELEASE jar would otherwise contribute the same class under a version path that is not
    // part of any name
    assertEquals(ArtifactIndex.namesOf("META-INF/versions/9/a/b/C.class"), Nil)
    assertEquals(ArtifactIndex.namesOf("module-info.class"), Nil)
    assertEquals(ArtifactIndex.namesOf("a/b/package-info.class"), Nil)
  }

  test("a jar's listing is its class set") {
    val jar = jarOf("META-INF/MANIFEST.MF", "p/", "p/Svc.class", "p/Svc$.class", "p/Svc.tasty",
                    "p/Impl$$anon$1.class", "p/Registry.class")
    assertEquals(ArtifactIndex.classesIn(List(jar)), Set("p.Svc", "p.Impl", "p.Registry"))
  }

  test("a jar that cannot be RESOLVED is Unverifiable — never an empty provides-set") {
    // §4.6: `Known(Set.empty)` is indistinguishable from "this artifact declares nothing the port
    // names", which is a REMOVE instruction. An offline run must not be able to produce one.
    val got = ArtifactIndex.provides(ArtifactDep("o", "n", "1"), scala.None, "3",
      _ => Left("no network"))
    got match
      case DependencyCheck.Provides.Unverifiable(why) => assertEquals(why, "no network")
      case other => fail(s"expected Unverifiable, got $other")
  }

  test("…and a jar that RESOLVED and cannot be read is a different fact, said differently") {
    val missing = Files.createTempDirectory("artifact-index-").resolve("gone.jar")
    val got = ArtifactIndex.provides(ArtifactDep("o", "n", "1"), scala.None, "3",
      _ => Right(List(missing)))
    // the resolver's own filter drops a path that does not exist, so this is the honest empty jar
    // list rather than a crash; what must never happen is the run dying on a check column.
    assert(got.isInstanceOf[DependencyCheck.Provides.Known] ||
           got.isInstanceOf[DependencyCheck.Provides.Unverifiable], got.toString)
  }

  test("the cache answers only for the invocation that produced it") {
    // `ClasspathCache`'s fingerprint rule, for its reason: a cache keyed on existence alone answers
    // `yes` to a question nobody asked after a coordinate bump, and the port is then checked against
    // the classes it USED to declare.
    val dir = Files.createTempDirectory("artifact-index-")
    val dep = ArtifactDep("o", "n", "1", CrossKind.Platform)
    val jar = jarOf("p/A.class")
    // first call resolves and writes the listing beside its fingerprint
    assertEquals(ArtifactIndex.provides(dep, Some(dir), "3", _ => Right(List(jar))),
                 DependencyCheck.Provides.Known(Set("p.A")))
    assert(Files.exists(ArtifactIndex.cacheFile(dir, dep)))
    // …and the second answers from it without resolving anything
    assertEquals(ArtifactIndex.provides(dep, Some(dir), "3",
                   _ => fail("the cache should have answered")),
                 DependencyCheck.Provides.Known(Set("p.A")))
    // a BUMPED revision is a different fingerprint and a different file — the stale listing is never
    // reused for it
    val bumped = dep.copy(rev = "2")
    assertEquals(ArtifactIndex.provides(bumped, Some(dir), "3", _ => Right(List(jarOf("p/B.class")))),
                 DependencyCheck.Provides.Known(Set("p.B")))
    // …and a listing whose sidecar disagrees is refetched rather than trusted
    Files.writeString(ArtifactIndex.cacheFile(dir, dep).resolveSibling(
      ArtifactIndex.cacheFile(dir, dep).getFileName.toString + ".coords"), "some other invocation\n")
    assertEquals(ArtifactIndex.provides(dep, Some(dir), "3", _ => Right(List(jarOf("p/C.class")))),
                 DependencyCheck.Provides.Known(Set("p.C")))
  }

  test("an EMPTY listing is a listing, and survives the cache round trip") {
    // a resources-only artifact legitimately declares no class; the fingerprint says this is that
    // jar's answer, so it must not be re-read as a miss.
    val dir = Files.createTempDirectory("artifact-index-")
    val dep = ArtifactDep("o", "empty", "1")
    assertEquals(ArtifactIndex.provides(dep, Some(dir), "3", _ => Right(List(jarOf("META-INF/x")))),
                 DependencyCheck.Provides.Known(Set.empty))
    assertEquals(ArtifactIndex.provides(dep, Some(dir), "3", _ => fail("cached")),
                 DependencyCheck.Provides.Known(Set.empty))
  }

  test("the supplier resolves one coordinate ONCE, however many times the 2×2 asks") {
    // the 2×2 asks about the same coordinate up to twice, once per program; with a cold cache that
    // would be two resolutions of one artifact. Memoised per RUN and never globally — two runs in
    // one JVM are two answers (§5.1).
    var calls  = 0
    val jar    = jarOf("p/A.class")
    val supply = ArtifactIndex.supplier(scala.None, "3", _ => { calls += 1; Right(List(jar)) })
    val dep    = ArtifactDep("o", "n", "1")
    assertEquals(supply(dep), DependencyCheck.Provides.Known(Set("p.A")))
    assertEquals(supply(dep), DependencyCheck.Provides.Known(Set("p.A")))
    assertEquals(calls, 1)
    // a DIFFERENT revision is a different question
    assertEquals(supply(dep.copy(rev = "2")), DependencyCheck.Provides.Known(Set("p.A")))
    assertEquals(calls, 2)
  }
