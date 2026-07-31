package balticporter.core

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** CLAUDE.md §5.4's rule, and the DUPLICATION scan that keeps it one rule.
  *
  * The behavioural half pins what `RealPath` does; the source-scan half pins that nothing else does
  * it. Both are needed and they catch different things: §5.4 became a rule because three separate
  * parts of the engine were bitten by the same symlink, and the REPAIRS were separate too — four
  * private helpers spelling one rule four ways, three of them with different exception policies.
  * A behavioural spec cannot see a fifth copy; a grep can, and helper duplication is exactly the
  * failure that actually happened.
  *
  * What is deliberately NOT built: a scan for a raw `startsWith` on paths. A path-ish receiver is
  * not syntactically distinguishable from an FQN prefix test (`fullName.startsWith("java.")` is the
  * §4.56 shape and is everywhere), so such a scan would be false positives that must be routinely
  * ignored — which is how a lint stops being read. The semantic half is an auditor hunt line
  * (`.claude/agents/porting-auditor.md`) instead.
  */
class RealPathSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------------------------
  // the rule
  // -------------------------------------------------------------------------------------------

  test("a symlinked spelling and the real one are ONE path — the whole point of §5.4") {
    // The `…/mylib/mylib/` layout `ProvenanceHeaderSpec` uses, for the same reason: the root's own
    // parent contains a directory of the same name, so a lexical comparison that happens to work
    // for one segment still fails here.
    val tmp  = Files.createTempDirectory("bp-realpath").toRealPath()
    val nest = tmp.resolve("mylib/mylib/com/example")
    Files.createDirectories(nest)
    val file = nest.resolve("Widget.java")
    Files.writeString(file, "// x")
    val root = tmp.resolve("mylib/mylib")
    val link = tmp.resolve("via-link")
    try Files.createSymbolicLink(link, root)
    catch case _: UnsupportedOperationException => assume(false, "filesystem without symlinks")

    // the lexical answer, which is the defect: the two spellings are unrelated
    assert(!file.normalize.startsWith(link.normalize))
    // …and the §5.4 answer
    assert(RealPath.startsWith(file, link))
    assertEquals(RealPath.of(link), RealPath.of(root))
    assertEquals(RealPath.relativize(link, file).toString.replace('\\', '/'), "com/example/Widget.java")
  }

  test("an ABSENT path falls back to lexical normalisation — a synthetic origin is not an error") {
    val absent = Path.of("/definitely/not/here/../here/File.java")
    assertEquals(RealPath.of(absent), Path.of("/definitely/not/here/File.java"))
  }

  test("…and the fallback is ABSOLUTE, so a `relativize` after it cannot throw") {
    // The check report's private copy fell back to a BARE `normalize`. A relative input then stayed
    // relative, `Path.relativize` threw "'other' is different type of Path", and the outer catch
    // returned the raw ABSOLUTE path the function's own doc promises never to emit.
    val rel = Path.of("some/relative/File.java")
    assert(clue(RealPath.of(rel)).isAbsolute)
    val root = Path.of("").toAbsolutePath
    RealPath.relativize(root, rel) // must not throw
  }

  test("ofExisting is FATAL on an absent input, and the message names the path (§5.1)") {
    val missing = Path.of("/definitely/not/here/Missing.java")
    val e = intercept[java.nio.file.NoSuchFileException](RealPath.ofExisting(missing, "declared source file"))
    assert(clue(e.getFile).contains("Missing.java"))
    assert(clue(e.getReason).contains("declared source file"))
  }

  test("ofExisting is `of` for a path that IS there") {
    val tmp = Files.createTempDirectory("bp-realpath-ok")
    assertEquals(RealPath.ofExisting(tmp, "temp"), RealPath.of(tmp))
  }

  // -------------------------------------------------------------------------------------------
  // the duplication scan — one rule, one implementation
  // -------------------------------------------------------------------------------------------

  /** every production `src/main/scala`, written into the test resources by build.sbt so the scan
    * does not depend on where the suite is run from (the device `PolicyKeyLintSpec` uses). */
  private def productionRoots: List[Path] =
    val is = Option(getClass.getClassLoader.getResourceAsStream("balticporter/production-source-dirs.txt"))
      .getOrElse(fail("balticporter/production-source-dirs.txt is missing — the Test resourceGenerator did not run"))
    val s = try new String(is.readAllBytes(), "UTF-8") finally is.close()
    s.linesIterator.map(_.trim).filter(_.nonEmpty).map(Path.of(_)).toList

  private def scalaFiles(root: Path): List[Path] =
    if !Files.isDirectory(root) then Nil
    else Files.walk(root).iterator().asScala.filter(p => p.toString.endsWith(".scala")).toList.sorted

  test("`.toRealPath(` appears in PRODUCTION code only inside RealPath.scala") {
    val roots = productionRoots
    assert(roots.nonEmpty, "no production source roots were recorded")
    val offenders = roots.flatMap(scalaFiles).filter { p =>
      p.getFileName.toString != "RealPath.scala" && Files.readString(p).contains(".toRealPath(")
    }
    assert(
      offenders.isEmpty,
      s"""${offenders.size} file(s) call `.toRealPath(` outside `balticporter.core.RealPath`:
         |${offenders.map("  " + _).mkString("\n")}
         |
         |§1(a) ENGINE. §5.4's rule has ONE implementation. Four private copies of it existed before
         |this spec, three with different exception policies and one with a fallback bug that made
         |the check report emit the absolute path it promises never to emit. Use `RealPath.of` /
         |`RealPath.startsWith` / `RealPath.relativize`, or `RealPath.ofExisting` where an absent
         |declared input must be fatal.""".stripMargin
    )
  }

  test("…and the scan is real: it sees the one legitimate call site") {
    val realPathFile = productionRoots.flatMap(scalaFiles).filter(_.getFileName.toString == "RealPath.scala")
    assertEquals(clue(realPathFile).size, 1)
    assert(Files.readString(realPathFile.head).contains(".toRealPath("))
  }
