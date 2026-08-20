package balticporter.tir

import balticporter.core.ResourceTree

import java.nio.file.{Files, Path}

/** THE CLASSPATH-RESOURCE DELIVERABLE, at each of its answers — and every one of them is silent in
  * production by construction.
  *
  * A resource that is absent, that arrives at a rewritten path, or that the port never declared
  * produces the SAME emitted Scala, the same compile, the same member digests and the same check
  * counts as one that is right: the failure is a lookup that returns nothing, in the CONSUMER's
  * build, at first use. So this file and the lane it exercises are the only evidence there can be
  * (`CLAUDE.md` §3 — a check reporting zero is only as good as its coverage), and the negatives
  * matter more than the positive:
  *
  *   - the bytes and the PATH must both come through UNTOUCHED, including for a port that renames
  *     everything — this is the half that separates the mechanism from [[ServiceProviders]], where
  *     both namespaces must move;
  *   - a path the emitted code NAMES and the port did not declare has to be REPORTED, or the
  *     mechanism ships a subset and says nothing;
  *   - a file under the declared root that the port did not declare and nothing names must NOT be
  *     shipped, because the upstream's own build files live there.
  *
  * The fixture is a real file tree rather than a corpus port: `plan`/`candidates`/`findings` are
  * pure functions of a declaration and a directory, so all of it is reachable here and only the
  * positive is reachable on any port this corpus has.
  */
class PortResourcesSpec extends munit.FunSuite:

  private val tmp = FunFixture[Path](
    setup = _ => Files.createTempDirectory("bp-res"),
    teardown = dir =>
      if Files.isDirectory(dir) then
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(Files.deleteIfExists(_)),
  )

  private def resource(dir: Path, path: String, body: String): Path =
    val f = path.split('/').foldLeft(dir)((p, s) => p.resolve(s))
    Files.createDirectories(f.getParent)
    Files.writeString(f, body)
    f

  private def kinds(fs: List[CheckReport.Finding]): List[(String, String)] =
    fs.map(f => (f.kind, f.owner)).sorted

  // -------------------------------------------------------------------------------------------
  // the copy: bytes and path, both untouched
  // -------------------------------------------------------------------------------------------

  tmp.test("the file is copied VERBATIM to the path the emitted code names") { dir =>
    resource(dir, "p/q/skin.json", """{ "font": "p/q/default.fnt" }""")
    val out  = dir.resolve("out")
    val plan = PortResources.plan(List(ResourceTree(dir, List("p/q/skin.json"))))
    val List(w) = PortResources.write(plan, out): @unchecked
    assertEquals(w, out.resolve("p").resolve("q").resolve("skin.json"))
    assertEquals(Files.readString(w), """{ "font": "p/q/default.fnt" }""")
  }

  tmp.test("…and the CONTENT is not rewritten either, on a port that renames the very namespace it names") { dir =>
    // The one thing this mechanism must never do, and the whole difference from a descriptor. The
    // body here holds the upstream package as a STRING, which is exactly what a skin, an atlas or a
    // properties table holds — and what `ServiceProviders` would (correctly, for a descriptor) move.
    resource(dir, "p/q/table.properties", "key=p.q.Widget\n")
    val out = dir.resolve("out")
    PortResources.write(PortResources.plan(List(ResourceTree(dir, List("p/q/table.properties")))), out)
    assertEquals(Files.readString(out.resolve("p/q/table.properties")), "key=p.q.Widget\n")
  }

  tmp.test("BINARY bytes survive — a resource is not text") { dir =>
    val bytes = Array[Byte](0x89.toByte, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0xFF.toByte)
    val f = dir.resolve("p").resolve("img.png")
    Files.createDirectories(f.getParent)
    Files.write(f, bytes)
    val out = dir.resolve("out")
    PortResources.write(PortResources.plan(List(ResourceTree(dir, List("p/img.png")))), out)
    assert(java.util.Arrays.equals(Files.readAllBytes(out.resolve("p/img.png")), bytes))
  }

  tmp.test("a declared path is normalised, so a leading slash names the same file rather than the filesystem root") { dir =>
    // `getResourceAsStream` is written with a leading `/` and a classpath file handle without one;
    // a port that pasted the literal from its own java would otherwise resolve the COPY against `/`.
    resource(dir, "p/q/x.txt", "x")
    val plan = PortResources.plan(List(ResourceTree(dir, List("/p/q/x.txt"))))
    assertEquals(plan.map(_.path), List("p/q/x.txt"))
    assertEquals(plan.head.source, dir.resolve("p").resolve("q").resolve("x.txt"))
    assertEquals(plan.head.absolute, "/p/q/x.txt")
  }

  // -------------------------------------------------------------------------------------------
  // the residues
  // -------------------------------------------------------------------------------------------

  tmp.test("a path the emitted code NAMES and this port does not ship is reported — the defect itself") { dir =>
    resource(dir, "p/q/shipped.json", "{}")
    resource(dir, "p/q/forgotten.properties", "k=v")
    val trees = List(ResourceTree(dir, List("p/q/shipped.json")))
    val plan  = PortResources.plan(trees)
    val cands = PortResources.candidates(trees, plan)
    val fs = PortResources.findings(plan, cands, trees,
      named = Set("p/q/shipped.json", "p/q/forgotten.properties"))
    assertEquals(kinds(fs), List(("named-unshipped", "p/q/forgotten.properties"),
                                 ("shipped", "p/q/shipped.json")))
    assert(fs.exists(f => f.kind == "named-unshipped" && f.detail.contains("§1(b)")))
  }

  tmp.test("…and it is asked of BOTH spellings, because a lookup is written either way") { dir =>
    resource(dir, "p/q/one.txt", "1")
    val trees = List(ResourceTree(dir, Nil))
    val cands = PortResources.candidates(trees, Nil)
    // the run's own predicate: the literal carries the leading slash, the declared path does not.
    val named: String => Boolean = p => Set("/p/q/one.txt").contains(p) || Set("/p/q/one.txt").contains("/" + p)
    val fs = PortResources.findings(Nil, cands, trees, named)
    assert(fs.exists(f => f.kind == "named-unshipped" && f.owner == "p/q/one.txt"))
  }

  tmp.test("an UNDECLARED file nothing names is NOT reported and NOT shipped — the upstream build's own files") { dir =>
    // The measured case this design turns on: an upstream resource root also holds files belonging
    // to the upstream BUILD (a cross-compiler module definition, a native-toolchain config). A scan
    // would ship them; a declaration does not, and the lane does not nag about them either.
    resource(dir, "p/q/skin.json", "{}")
    resource(dir, "build/toolchain.xml", "<config/>")
    val trees = List(ResourceTree(dir, List("p/q/skin.json")))
    val plan  = PortResources.plan(trees)
    val cands = PortResources.candidates(trees, plan)
    assertEquals(cands.map(_.path), List("build/toolchain.xml"))
    val fs = PortResources.findings(plan, cands, trees, named = Set("p/q/skin.json"))
    assertEquals(kinds(fs), List(("shipped", "p/q/skin.json")))
    val out = dir.resolve("out")
    PortResources.write(plan, out)
    assert(!Files.exists(out.resolve("build/toolchain.xml")))
  }

  tmp.test("a SHIPPED resource no literal names is stated, never repaired — an atlas names its own image") { dir =>
    resource(dir, "p/q/skin.json", "{}")
    resource(dir, "p/q/skin.png", "img")
    val trees = List(ResourceTree(dir, List("p/q/skin.json", "p/q/skin.png")))
    val plan  = PortResources.plan(trees)
    val fs = PortResources.findings(plan, PortResources.candidates(trees, plan), trees,
      named = Set("p/q/skin.json"))
    assertEquals(kinds(fs), List(("shipped", "p/q/skin.json"), ("unnamed", "p/q/skin.png")))
  }

  tmp.test("a tree that declares NO file is a finding — it is indistinguishable from an absent resource") { dir =>
    resource(dir, "p/q/x.txt", "x")
    val trees = List(ResourceTree(dir, Nil))
    val fs = PortResources.findings(Nil, PortResources.candidates(trees, Nil), trees, named = _ => false)
    assertEquals(fs.count(_.kind == "empty"), 1)
    // …and emphatically NOT read as "everything under the root", which is the scan this refuses.
    assertEquals(PortResources.plan(trees), Nil)
  }

  tmp.test("the POSITIVE row exists, so a lane cannot hold its bar at zero by shipping nothing") { dir =>
    resource(dir, "p/q/x.txt", "x")
    val trees = List(ResourceTree(dir, List("p/q/x.txt")))
    val plan  = PortResources.plan(trees)
    val fs = PortResources.findings(plan, Nil, trees, named = Set("p/q/x.txt"))
    assertEquals(fs.map(_.kind), List("shipped"))
    assertEquals(fs.head.check, PortResources.Name)
  }

  tmp.test("candidates over a root that is not a directory is empty rather than a crash") { dir =>
    val fs = PortResources.candidates(List(ResourceTree(dir.resolve("nope"), Nil)), Nil)
    assertEquals(fs, Nil)
  }
