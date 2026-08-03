package balticporter.runner

import java.nio.file.{Files, Path}

/** [[VendoredCommit]] against a REAL git repository built in a temp dir — the helper shells out,
  * so a mock would test the string formatter and skip the only part that can break. Negative case
  * included: a check (here, a fallback) that has never fired is not known to work (CLAUDE.md §3).
  */
class VendoredCommitSpec extends munit.FunSuite:

  private def sh(cwd: Path, cmd: String*): Unit =
    val p = new ProcessBuilder(cmd*).directory(cwd.toFile).redirectErrorStream(true).start()
    val out = new String(p.getInputStream.readAllBytes())
    assert(p.waitFor() == 0, s"${cmd.mkString(" ")} failed:\n$out")

  private val repo = FunFixture[Path](
    setup = { _ =>
      val dir = Files.createTempDirectory("vendored-commit-spec")
      sh(dir, "git", "init", "-q")
      sh(dir, "git", "config", "user.email", "spec@invalid")
      sh(dir, "git", "config", "user.name", "spec")
      Files.createDirectories(dir.resolve("lib/src"))
      Files.writeString(dir.resolve("lib/src/A.java"), "class A {}")
      sh(dir, "git", "add", "."); sh(dir, "git", "commit", "-qm", "vendor lib")
      dir
    },
    teardown = { dir =>
      def rm(p: Path): Unit =
        if Files.isDirectory(p, java.nio.file.LinkOption.NOFOLLOW_LINKS) then
          Files.list(p).forEach(rm); Files.delete(p)
        else Files.delete(p)
      rm(dir)
    },
  )

  repo.test("a clean subtree pins repo@hash and names the subtree") { dir =>
    val line = VendoredCommit.of(dir.resolve("lib/src"))
    val hash = { // the commit the line must name: the last one touching the subtree
      val p = new ProcessBuilder("git", "log", "-1", "--format=%H").directory(dir.toFile).start()
      new String(p.getInputStream.readAllBytes()).trim
    }
    assertEquals(line, s"${dir.getFileName}@$hash (last change to lib/src)")
  }

  repo.test("last-touch is stable across commits elsewhere in the repo") { dir =>
    val before = VendoredCommit.of(dir.resolve("lib/src"))
    Files.writeString(dir.resolve("unrelated.txt"), "churn")
    sh(dir, "git", "add", "."); sh(dir, "git", "commit", "-qm", "unrelated")
    assertEquals(VendoredCommit.of(dir.resolve("lib/src")), before)
  }

  repo.test("a dirty subtree says so") { dir =>
    Files.writeString(dir.resolve("lib/src/A.java"), "class A { int x; }")
    val line = VendoredCommit.of(dir.resolve("lib/src"))
    assert(clue(line).contains("+dirty"), "an edited working file must be stated, not absorbed")
  }

  repo.test("an origin remote is carried into the line") { dir =>
    sh(dir, "git", "remote", "add", "origin", "https://example.invalid/lib.git")
    val line = VendoredCommit.of(dir.resolve("lib/src"))
    assert(clue(line).endsWith("(last change to lib/src; origin https://example.invalid/lib.git)"))
  }

  test("outside any git repository, say 'commit unknown' — never invent an anchor") {
    val dir = Files.createTempDirectory("vendored-commit-nogit")
    try assertEquals(VendoredCommit.of(dir.resolve("lib")), "vendored at lib; commit unknown")
    finally Files.deleteIfExists(dir.resolve("lib")); Files.deleteIfExists(dir)
  }
