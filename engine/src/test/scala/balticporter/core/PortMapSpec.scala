package balticporter.core

import balticporter.core.PortMap.Disposition
import balticporter.tir.SrcMap

import java.nio.file.Files

class PortMapSpec extends munit.FunSuite:

  private def member(unit: String, m: String) =
    SrcMap.Entry(unit, m, "def", 1, 2, s"${unit.replace('.', '/')}.java", 10, "d0")

  private def build(
      emitted: List[String] = Nil,
      members: List[SrcMap.Entry] = Nil,
      dropTypes: Set[String] = Set.empty,
      dropMethods: Set[String] = Set.empty,
      injected: Set[String] = Set.empty,
      bodies: Set[String] = Set.empty,
      renames: Map[String, String] = Map.empty,
  ) = PortMap.of("m", "eng", emitted, SrcMap.Recording(members), dropTypes, dropMethods, injected, bodies, renames)

  test("erase strips generic ARGUMENTS so a manifest key matches a srcmap key") {
    // The defect this exists for: SrcMap records `f(Class<T>)`, every manifest writes `f(Class)`,
    // and a map keyed one way but consulted the other misses SILENTLY — the body flag simply never
    // appeared. Nested and multi-argument forms included because a real signature has them.
    assertEquals(PortMap.erase("p.C#f(Class<T>)"), "p.C#f(Class)")
    assertEquals(PortMap.erase("p.C#f(Map<String,List<Integer>>,int)"), "p.C#f(Map,int)")
    assertEquals(PortMap.erase("p.C#f()"), "p.C#f()")
    assertEquals(PortMap.erase("p.C#field"), "p.C#field") // no parameter list at all
  }

  test("a DROPPED type and a SUBSTITUTED type are distinguished by what stands at the name") {
    // The distinction is the entire content of the entry for a dependent: one means "call it and
    // get a different implementation", the other means "every call must already be gone".
    val m = build(dropTypes = Set("p.Gone", "p.Replaced"), injected = Set("p.Replaced"))
    val byName = m.types.map(e => e.upstream -> e).toMap
    assertEquals(byName("p.Gone").disposition, Disposition.Dropped)
    assertEquals(byName("p.Gone").emitted, "")
    assertEquals(byName("p.Replaced").disposition, Disposition.Substituted)
    assertEquals(byName("p.Replaced").emitted, "p.Replaced")
  }

  test("an injected type that replaces nothing is ADDED, not Substituted") {
    val m = build(injected = Set("p.Helper"), dropTypes = Set.empty)
    assertEquals(m.types.map(e => (e.emitted, e.disposition)), List(("p.Helper", Disposition.Added)))
  }

  test("`upstream` comes from the JAVA ORIGIN, so it survives a non-invertible rename") {
    // The origin is ground truth. Inverting the rename works only while the rename is injective,
    // and a real one need not be: flattening two upstream packages onto one target makes
    // `port.ui.X` genuinely ambiguous, and every shared type then becomes unfindable to a
    // dependent — which looks the base up BY UPSTREAM NAME. Same rule as the provenance header
    // (CLAUDE.md §4.57): take the path from `Origin`, never reconstruct it from the FQN.
    val srcEntry = SrcMap.Entry("port.ui.Widget", "port.ui.Widget#draw(Batch)", "def", 1, 2,
      "up/stream/lib/ui/Widget.java", 10, "d0")
    val m = PortMap.of("m", "eng", List("port.ui.Widget"), SrcMap.Recording(List(srcEntry)),
      Set.empty, Set.empty, Set.empty, Set.empty, Map("up.stream.lib" -> "port"))
    val t = m.types.head
    assertEquals(t.upstream, "up.stream.lib.ui.Widget")
    assertEquals(t.emitted, "port.ui.Widget")
    assertEquals(t.disposition, Disposition.Renamed)
    // the member follows the same reversal — this is what makes the map usable as a lookup from a
    // dependent that has only ever seen the upstream names
    assertEquals(m.members.head.upstream, "up.stream.lib.ui.Widget#draw(Batch)")
  }

  test("an AMBIGUOUS reversal reports the emitted name rather than guessing") {
    // Two renames onto one target: the upstream name genuinely cannot be recovered. A wrong
    // upstream name in a PUBLISHED map is worse than an absent one, so it declines.
    val m = build(emitted = List("out.T"), renames = Map("a.x" -> "out", "b.y" -> "out"))
    assertEquals(m.types.head.upstream, "out.T")
    assertEquals(m.types.head.disposition, Disposition.Ported)
  }

  test("a hand-supplied BODY is flagged — the signature cannot show it") {
    val m = build(
      emitted = List("p.C"),
      members = List(member("p.C", "p.C#make(Class<T>)"), member("p.C", "p.C#plain()")),
      bodies  = Set("p.C#make(Class)"), // the MANIFEST spelling, not the srcmap one
    )
    val byName = m.members.map(e => e.upstream -> e).toMap
    assert(clue(byName("p.C#make(Class)")).body)
    assert(!byName("p.C#plain()").body)
  }

  test("render/read round-trips, and an unknown schema is REFUSED rather than mis-read") {
    val m = build(
      emitted = List("p.C"),
      members = List(member("p.C", "p.C#f(Class<T>)")),
      dropTypes = Set("p.Gone"),
      dropMethods = Set("p.C#old(int)"),
    )
    val text = PortMap.render(m)
    val tmp  = java.nio.file.Files.createTempDirectory("portmap")
    java.nio.file.Files.writeString(tmp.resolve("port-map.tsv"), text)
    val back = PortMap.read(tmp.resolve("port-map.tsv"))
    assertEquals(back.map(_.entries), Right(m.entries))
    assertEquals(back.map(_.module), Right("m"))

    val bumped = tmp.resolve("bumped.tsv")
    java.nio.file.Files.writeString(bumped, text.replace(s"schema=${PortMap.Schema}", "schema=999"))
    assert(clue(PortMap.read(bumped)).isLeft)
    assert(PortMap.read(tmp.resolve("absent.tsv")).isLeft)
  }

  // ---------------------------------------------------------------------------
  // R1 — the map goes stale against the base's emitted output
  // ---------------------------------------------------------------------------

  /** a base's Java tree: one file, one member attributed to it. */
  private def basePort(body: String) =
    val root = Files.createTempDirectory("portmap-base")
    val java = root.resolve("p/C.java")
    Files.createDirectories(java.getParent)
    Files.writeString(java, body)
    val m = PortMap.of("base", "eng", List("p.C"),
      SrcMap.Recording(List(member("p.C", "p.C#f()"))),
      Set.empty, Set.empty, Set.empty, Set.empty, Map.empty, sourceRoot = Some(root))
    (root, java, m)

  test("R1 FALSIFIER: change one base member's body and the map is reported STALE, not used") {
    // The design's own falsifying experiment, run: publish a map, change a base member's body,
    // consult the map from a dependent. It must SAY the map no longer describes the base rather
    // than reading an entry that describes a run which no longer exists.
    val (root, java, m) = basePort("package p; class C { int f() { return 1; } }")
    assert(m.sources.nonEmpty, "a map published with a source root carries a fingerprint")
    assertEquals(m.files, 1)
    assertEquals(PortMap.freshness(m, "eng", List(root)), PortMap.Freshness.Fresh)

    Files.writeString(java, "package p; class C { int f() { return 2; } }")
    PortMap.freshness(m, "eng", List(root)) match
      case PortMap.Freshness.Stale(r) => assert(clue(r).contains("has changed"))
      case other                      => fail(s"expected Stale, got $other")
  }

  test("a map published by a DIFFERENT ENGINE is stale — its entries describe another emitter") {
    val (root, _, m) = basePort("package p; class C { int f() { return 1; } }")
    PortMap.freshness(m, "eng-next", List(root)) match
      case PortMap.Freshness.Stale(r) => assert(clue(r).contains("eng-next"))
      case other                      => fail(s"expected Stale, got $other")
  }

  test("sources this run cannot see are UNVERIFIED, never `Stale` — the two are different answers") {
    // A path that resolves under no root contributes `?` to the digest and would therefore ALWAYS
    // compare unequal. Reporting that as staleness would cry wolf on every port whose resolution
    // roots do not cover the whole base; the honest answer is that freshness was not checked.
    val (_, _, m) = basePort("package p; class C { int f() { return 1; } }")
    PortMap.freshness(m, "eng", List(Files.createTempDirectory("elsewhere"))) match
      case PortMap.Freshness.Unverified(r) => assert(clue(r).contains("not under this run's resolution"))
      case other                           => fail(s"expected Unverified, got $other")
    // …and a map with no fingerprint at all (an older engine's) is likewise unverified, not stale.
    val bare = build(emitted = List("p.C"))
    assert(clue(PortMap.freshness(bare, "eng", Nil)).isInstanceOf[PortMap.Freshness.Unverified])
  }

  test("a SYNTHETIC origin is not a file and is left out of the fingerprint") {
    // Measured, not hypothesised: one member of libGDX core has origin `<synthetic>`, it can never
    // resolve under any root, and including it made the FIRST dependent run report the base's map
    // as unverifiable. A check whose first real firing is a false positive teaches its reader to
    // ignore it.
    val root = Files.createTempDirectory("portmap-synth")
    Files.createDirectories(root.resolve("p"))
    Files.writeString(root.resolve("p/C.java"), "package p; class C {}")
    val m = PortMap.of("base", "eng", List("p.C"),
      SrcMap.Recording(List(
        member("p.C", "p.C#f()"),
        SrcMap.Entry("p.C", "p.C#synth()", "def", 1, 2, "<synthetic>", 0, "d1"))),
      Set.empty, Set.empty, Set.empty, Set.empty, Map.empty, sourceRoot = Some(root))
    assertEquals(m.javaPaths, List("p/C.java"))
    assertEquals(m.files, 1)
    assertEquals(PortMap.freshness(m, "eng", List(root)), PortMap.Freshness.Fresh)
  }

  test("discovery keys on the map's OWN module header, prefers run-latest, and excludes the caller") {
    // R2 lives or dies on the exclusion: a module that read its own map would have its behaviour
    // depend on its previous output, and a port would stop being reproducible from sources plus
    // policy. The exclusion is by module NAME because that is what a `PortManifest` declares — a
    // report directory is named after the migration PROGRAM and the two need not agree.
    val reports = Files.createTempDirectory("port-report")
    def put(dir: String, run: String, module: String, marker: String): Unit =
      val d = reports.resolve(s"$dir/$run")
      Files.createDirectories(d)
      Files.writeString(d.resolve("port-map.tsv"),
        PortMap.render(PortMap.of(module, "eng", List(marker), SrcMap.Recording(Nil),
          Set.empty, Set.empty, Set.empty, Set.empty, Map.empty)))

    put("BaseMigrate", "baseline", "base", "p.Old")
    put("BaseMigrate", "run-latest", "base", "p.New")
    put("SelfMigrate", "baseline", "me", "p.Mine")

    val all = PortMap.discover(reports)
    assertEquals(all.map(_.module), List("base", "me"))
    val b = all.find(_.module == "base").get
    assertEquals(b.source, "run-latest")
    assertEquals(b.map.toOption.get.types.map(_.emitted), List("p.New"))

    assertEquals(PortMap.discover(reports, exclude = Set("me")).map(_.module), List("base"))
  }
