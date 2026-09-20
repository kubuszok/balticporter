package balticporter.corpus.liqp

import java.nio.file.{ Files, Path }

import scala.jdk.CollectionConverters.*

/** Where liqp's frontend classpath is BUILT, for a caller that has none of this repository's layout: everything written lands under the `work` directory the configurations are given, the upstream is
  * the caller's own checkout, and the javac stub is read from wherever the port's configurations ship.
  */
class LiqpClasspathLocationSpec extends munit.FunSuite:

  private def tmp(name: String): Path = Files.createTempDirectory(name)

  private def entries(under: Path): List[String] =
    if !Files.isDirectory(under) then Nil
    else
      val s = Files.walk(under)
      try s.iterator.asScala.filter(Files.isRegularFile(_)).map(under.relativize(_).toString).toList.sorted
      finally s.close()

  test("a missing generated parser is refused by PATH, and names the caller's own upstream") {
    val work     = tmp("liqp-work")
    val upstream = tmp("liqp-upstream")
    val corpus   = tmp("liqp-corpus")
    val e        = intercept[IllegalStateException](LiqpClasspath.ensureIn(work, upstream, corpus))
    assert(clue(e.getMessage).contains(upstream.resolve("target/generated-sources/antlr4").toString))
    // the regenerate hint names the caller's checkout too, not this repository's sibling
    assert(e.getMessage.contains(s"cd $upstream"))
  }

  test("…and nothing is written outside `work` while it refuses") {
    val work     = tmp("liqp-work")
    val upstream = tmp("liqp-upstream")
    val corpus   = tmp("liqp-corpus")
    intercept[IllegalStateException](LiqpClasspath.ensureIn(work, upstream, corpus))
    assertEquals(clue(entries(upstream)), Nil)
    assertEquals(clue(entries(corpus)), Nil)
  }

  test("a missing javac stub is refused by PATH, under the CORPUS root the caller gave") {
    val work     = tmp("liqp-work")
    val upstream = tmp("liqp-upstream")
    val corpus   = tmp("liqp-corpus")
    val gen      = upstream.resolve("target/generated-sources/antlr4/liquid/parser/v4")
    Files.createDirectories(gen)
    Files.writeString(gen.resolve("LiquidLexer.java"), "package liquid.parser.v4; class LiquidLexer {}")
    val e = intercept[IllegalStateException](LiqpClasspath.ensureIn(work, upstream, corpus))
    assert(clue(e.getMessage).contains(corpus.resolve("ports/liqp/javac-stub").toString))
  }

  test("the stub ships BESIDE the configurations, so a corpus ROOT is all a caller needs") {
    // the stub has to sit under the same root `main.conf` does, or a jar that unpacks the
    // configurations leaves javac with nothing to resolve the rewritten references against
    val corpus = LiqpPort.repoRoot.resolve("balticporter/corpus")
    assert(Files.isDirectory(corpus.resolve("ports/liqp")), s"no liqp configurations under $corpus")
    assert(Files.isDirectory(LiqpClasspath.stubSourcesIn(corpus)), s"no javac stub under $corpus")
  }

  test("everything the build writes is named under the caller's `work`, and the wrappers agree") {
    // the repository wrapper is exactly `ensureIn` at `<repo>/out`
    assertEquals(LiqpClasspath.cache(Path.of("/repo")), Path.of("/repo/out").resolve(LiqpClasspath.FileName))
    assertEquals(LiqpClasspath.parserClasses(Path.of("/repo")), Path.of("/repo/out/liqp-parser-classes"))
    assertEquals(LiqpClasspath.parserSources(Path.of("/repo")), Path.of("/repo/out/liqp-parser-src"))
    assertEquals(LiqpTestClasspath.cache(Path.of("/repo")), Path.of("/repo/out").resolve(LiqpTestClasspath.FileName))
    // …and the three names are relative to `work`, so a consumer's target/ holds all of them
    val work = tmp("liqp-work")
    assertEquals(LiqpClasspath.cache(work.getParent.resolve(work.getFileName)).getFileName.toString, LiqpClasspath.FileName)
  }

  test("the TEST classpath is derived from the main one, in the same `work` directory") {
    val work     = tmp("liqp-work")
    val upstream = tmp("liqp-upstream")
    val corpus   = tmp("liqp-corpus")
    // it reaches the main one first, so the same missing-parser refusal is what a caller sees
    val e = intercept[IllegalStateException](LiqpTestClasspath.ensureIn(work, upstream, corpus))
    assert(clue(e.getMessage).contains(upstream.toString))
    assertEquals(clue(entries(work)), Nil)
  }
