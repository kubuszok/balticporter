package balticporter.core

import balticporter.core.PortMap.Disposition
import balticporter.tir.SrcMap

import java.nio.file.{Files, Path}

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

  test("the SEARCH PATH is several roots, nearest first — §4.45's consumer has no run tree") {
    // An agent in another repository points a published Baltic Porter at its own java. Its base's map
    // arrives from wherever that base was run, or unpacked from an artifact — never from a
    // `port-report/` tree of this checkout's shape. With one root the discovery finds nothing and
    // every base-surface question degrades to `Unknown` with no way to say otherwise.
    val here  = Files.createTempDirectory("pm-here")
    val there = Files.createTempDirectory("pm-there")
    def publish(under: java.nio.file.Path, module: String): Unit =
      val d = under.resolve(module).resolve("run-latest")
      Files.createDirectories(d)
      PortMap.write(d, PortMap.Map0(module, "eng", Nil))

    publish(here, "mine")
    publish(there, "the-base")

    assertEquals(PortMap.discoverIn(List(here), Set.empty).map(_.module), List("mine"))
    assertEquals(PortMap.discoverIn(List(here, there), Set.empty).map(_.module).sorted,
                 List("mine", "the-base"))
    // an extra root can only ADD a base, never shadow the run's own tree: first wins per module,
    // exactly as two directories under ONE root already do.
    publish(there, "mine")
    val both = PortMap.discoverIn(List(here, there), Set.empty)
    assertEquals(both.map(_.module).sorted, List("mine", "the-base"))
    assert(clue(both.find(_.module == "mine").map(_.path.toString))
             .exists(_.startsWith(RealPath.str(here))), RealPath.str(here))
    // …and `exclude` still holds, so a run cannot discover ITSELF as its own base
    assertEquals(PortMap.discoverIn(List(here, there), Set("mine")).map(_.module), List("the-base"))
    // a root that does not exist is not an error — an unset flag must be a no-op by arithmetic
    assertEquals(PortMap.discoverIn(List(here.resolve("nope")), Set.empty), Nil)
  }

  test("the search path comes from the PORT; the flag is the fallback, and it is CHOSEN not merged") {
    // A base's map decides emitted text, so which maps a run discovers is part of the run's identity
    // (§4.6's `reportPathRoot` lesson at an input that shapes the OUTPUT). Left to the flag alone, a
    // leftover `debug.properties` entry adds a base and two checkouts at the same commit emit
    // differently with every count identical — so a port that states its own IGNORES the flag.
    // Merging would leave exactly that failure in place for every port that had stated one.
    val prev = Option(System.getProperty("balticporter.baseReports"))
    try
      System.setProperty("balticporter.baseReports", "from-the-operator")
      assertEquals(PortMap.searchPath(Nil).map(_.getFileName.toString), List("from-the-operator"))
      assertEquals(PortMap.searchPath(List(Path.of("from-the-port"))).map(_.getFileName.toString),
                   List("from-the-port"))
    finally
      prev match
        case Some(v) => System.setProperty("balticporter.baseReports", v)
        case None    => System.clearProperty("balticporter.baseReports")
    // …and with neither, the run's own report root is the whole search path
    assertEquals(PortMap.searchPath(Nil), Nil)
  }

  test("`balticporter.baseReports` is the fallback flag, and it is one an accessor READS") {
    val prev = Option(System.getProperty("balticporter.baseReports"))
    try
      System.setProperty("balticporter.baseReports", List("a", "b").mkString(java.io.File.pathSeparator))
      assertEquals(balticporter.tir.DebugFlags.baseReports.map(_.getFileName.toString), List("a", "b"))
      // …and it is marked as the FALLBACK, because its effect is on EMITTED TEXT and a port is
      // supposed to state it — an operator has no other way to see that.
      assert(balticporter.tir.DebugFlags.PortSupplied.contains("balticporter.baseReports"))
      // …and it is in `known`, so `just debug-flags` cannot mark it as a key nothing will look up —
      // which is the one thing an operator cannot see any other way (§4.6).
      assert(balticporter.tir.DebugFlags.known.contains("balticporter.baseReports"))
      assert(clue(balticporter.tir.DebugFlags.active).exists(_.startsWith("baseReports=")))
    finally
      prev match
        case Some(v) => System.setProperty("balticporter.baseReports", v)
        case None    => System.clearProperty("balticporter.baseReports")
    assertEquals(balticporter.tir.DebugFlags.baseReports, Nil)
  }

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

  test("a RENAMING port still pairs a drop with its injection — the two are in different namespaces") {
    // CLAUDE.md §4.56. `dropTypes` is a manifest key, so it is UPSTREAM; `injected` is the set of
    // files the run WROTE, so it is EMITTED. Compared directly the test is false for every renaming
    // port, and `Substituted` had therefore never once been produced by one: libGDX drops
    // `com.badlogic.gdx.utils.Json`, injects `sge.utils.Json`, and its map carried `Dropped` beside
    // an unrelated-looking `Added` with nothing joining them. The first dependent to reference such
    // a replacement (gdx-gltf, on `Json`) was told the base "emits nothing at that name and nothing
    // replaces it" about a type it compiles against — 10 false findings.
    val m = build(
      dropTypes = Set("up.stream.Gone", "up.stream.Replaced"),
      injected  = Set("out.Replaced", "out.Helper"),
      renames   = Map("up.stream" -> "out"),
    )
    val byUpstream = m.types.filter(_.upstream.nonEmpty).map(e => e.upstream -> e).toMap

    assertEquals(byUpstream("up.stream.Replaced").disposition, Disposition.Substituted)
    assertEquals(byUpstream("up.stream.Replaced").emitted, "out.Replaced",
      "the emitted half of the row must be the name the injection actually ships under")

    // the drop with NO replacement is unaffected — that is the case the check exists for
    assertEquals(byUpstream("up.stream.Gone").disposition, Disposition.Dropped)
    assertEquals(byUpstream("up.stream.Gone").emitted, "")

    // …and the injection that replaces nothing is still an ADDITION, subtracted in the EMITTED
    // namespace so `out.Replaced` is not double-counted as one.
    assertEquals(m.types.filter(_.disposition == Disposition.Added).map(_.emitted), List("out.Helper"))
  }

  test("a rename that does not COVER a dropped name leaves it alone — cut only at a separator") {
    // The §4.56 prefix rule, at this join: `up.streaming` must not be rewritten by a `up.stream`
    // entry, or a drop would be paired with an injection that has nothing to do with it.
    val m = build(
      dropTypes = Set("up.streaming.Gone"),
      injected  = Set("out.Gone"),
      renames   = Map("up.stream" -> "out"),
    )
    val e = m.types.find(_.upstream == "up.streaming.Gone").get
    assertEquals(e.disposition, Disposition.Dropped)
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

  test("a source root that is a CHECKOUT — the leading directories are not package segments") {
    // A `sourceRoot` that is a multi-module checkout makes `javaPath` begin with the module and its
    // maven layout, and reading the whole of it as a package published
    // `mod.src.main.java.up.stream.lib.ui.Widget` for 9,261 of one port's 9,370 rows. Nothing in
    // that port could see it: the column is READ only by a dependent, and it had none.
    //
    // The declared package is a SUFFIX of the path-derived one by construction, so the rename's own
    // inverse says where it starts. Note this TRUNCATES the path and never overrides it — the test
    // below is the case where it must not fire.
    val srcEntry = SrcMap.Entry("port.ui.Widget", "port.ui.Widget#draw(Batch)", "def", 1, 2,
      "mod/src/main/java/up/stream/lib/ui/Widget.java", 10, "d0")
    val m = PortMap.of("m", "eng", List("port.ui.Widget"), SrcMap.Recording(List(srcEntry)),
      Set.empty, Set.empty, Set.empty, Set.empty, Map("up.stream.lib" -> "port"))
    assertEquals(m.types.head.upstream, "up.stream.lib.ui.Widget")
    assertEquals(m.members.head.upstream, "up.stream.lib.ui.Widget#draw(Batch)")
  }

  test("NEGATIVE: a BARE member key is not a package suffix — 102 libGDX rows say so") {
    // A promoted constructor parameter's `SrcMap` key carries no owner, so the emitted name is a
    // bare `list`. Every path-derived name ends with a bare name, so an unguarded suffix test
    // truncates the package away and publishes `list` — a different wrong answer from the
    // `com.badlogic.gdx.graphics.list` it replaced, and not a better one. The truncation needs the
    // unrenamed name to be QUALIFIED; a bare one says nothing about where the package starts.
    val srcEntry = SrcMap.Entry("port.ui.Widget", "list", "val", 1, 2, "up/stream/lib/ui/Widget.java", 10, "d0")
    val m = PortMap.of("m", "eng", List("port.ui.Widget"), SrcMap.Recording(List(srcEntry)),
      Set.empty, Set.empty, Set.empty, Set.empty, Map("up.stream.lib" -> "port"))
    assertEquals(m.members.head.upstream, "up.stream.lib.ui.list")
  }

  test("NEGATIVE: an AMBIGUOUS reversal still takes the ORIGIN, checkout-shaped path and all") {
    // The truncation may only fire where the rename inverts, and the whole point of reading the
    // origin is the case where it does not. Two renames onto one target: `unrename` declines and
    // answers the EMITTED name, which is not a suffix of the path-derived one, so the origin stands
    // — including its leading directories, which is the honest answer when nothing can say where
    // the package starts.
    val srcEntry = SrcMap.Entry("out.T", "out.T#m()", "def", 1, 2, "mod/src/main/java/a/x/T.java", 10, "d0")
    val m = PortMap.of("m", "eng", List("out.T"), SrcMap.Recording(List(srcEntry)),
      Set.empty, Set.empty, Set.empty, Set.empty, Map("a.x" -> "out", "b.y" -> "out"))
    assertEquals(m.types.head.upstream, "mod.src.main.java.a.x.T")
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
  // schema 3 — the base-surface contract (DESIGN.md §8.3)
  // ---------------------------------------------------------------------------

  test("schema 3: a type row carries what was EMITTED, and it round-trips") {
    val shape = balticporter.tir.Surface.TypeShape(
      form = "object", companion = true, statics = List("b", "a"),
      primary = Some(balticporter.tir.Descriptor(List(
        balticporter.tir.Param.Prim("int"), balticporter.tir.Param.Named("String")))),
      primaryKind = "synthesised-primary", primaryVis = "protected", disambiguator = "marker",
      parents = List("p.P"), flags = List("final", "abstract"), vis = "public")
    val m = PortMap.of("m", "eng", List("p.C"), SrcMap.Recording(Nil), Set.empty, Set.empty,
      Set.empty, Set.empty, Map.empty, typeShapes = Map("p.C" -> balticporter.tir.Surface.render(shape)))
    val row = m.types.find(_.emitted == "p.C").get
    assertEquals(row.typeShape, Some(shape.copy(statics = List("a", "b"), flags = List("abstract", "final"))))
    // the payload is sorted and in the porter-note grammar — the SAME grammar, not a ninth one.
    assert(clue(row.shape).startsWith("companion=yes disambiguator=marker flags="))

    val tmp = Files.createTempDirectory("portmap3")
    Files.writeString(tmp.resolve("port-map.tsv"), PortMap.render(m))
    assertEquals(PortMap.read(tmp.resolve("port-map.tsv")).map(_.entries), Right(m.entries))
  }

  test("NEGATIVE: a shape value containing WHITESPACE round-trips, and does not truncate the row") {
    // The pair list is whitespace-separated, so an unquoted value with a space is silently cut at
    // the first one — the defect that reported 594 porter notes as unbacked. One grammar means one
    // fix, and this is the assertion that it reached the second consumer.
    val shape = balticporter.tir.Surface.TypeShape(form = "class", tparams = "[A <: p.X, B]")
    val text  = balticporter.tir.Surface.render(shape)
    assert(clue(text).contains("\""), "a value with whitespace is QUOTED")
    assertEquals(balticporter.tir.Surface.parseType(text), Some(shape))
  }

  test("NEGATIVE: an OLDER schema degrades PER QUESTION — never wholesale, and never a crash") {
    // §8.3's rule: refusing a schema-2 map outright tells a dependent "your base is unusable" where
    // the truth is "your base is one engine version behind, and here are the questions I cannot ask
    // it". The row must still be READ; only its contract answer is absent.
    val m    = build(emitted = List("p.C"), members = List(member("p.C", "p.C#f()")))
    val text = PortMap.render(m)
    val tmp  = Files.createTempDirectory("portmap2")
    val old  = tmp.resolve("port-map.tsv")
    // a genuine schema-2 file: the header's version, no `policy=`, and eight columns per row.
    Files.writeString(old, text.replace(s"schema=${PortMap.Schema}", "schema=2")
      .replace("\tpolicy=", "\tlegacy=").split('\n')
      .map(l => if l.startsWith("#") then l.stripSuffix("\tshape") else l.stripSuffix("\t")).mkString("\n"))
    val back = PortMap.read(old)
    assert(clue(back).isRight, "an older schema is READ")
    val m0 = back.toOption.get
    assertEquals(m0.schema, 2)
    assertEquals(m0.types.size, 1)
    assertEquals(m0.types.head.typeShape, scala.None, "…and its contract answer is simply absent")
  }

  test("schema 3: the POLICY fingerprint makes a base MANIFEST edit visible, with every source digest matching") {
    // The whole reason the third fingerprint exists. `engine=` and `sources=` do not move when the
    // base's manifest changes, and the `shape` payload is full of policy outcomes — so without this
    // the map is `Fresh` and WRONG, which is D4's signature failure re-entering through the
    // artifact built to prevent it.
    val (root, _, m0) = basePort("package p; class C { int f() { return 1; } }")
    val m = m0.copy(policy = PortMap.policyDigest(List("rename[a->b]")))
    assertEquals(PortMap.freshness(m, "eng", List(root), PortMap.policyDigest(List("rename[a->b]"))),
                 PortMap.Freshness.Fresh)
    PortMap.freshness(m, "eng", List(root), PortMap.policyDigest(List("rename[a->c]"))) match
      case PortMap.Freshness.Stale(r) =>
        assert(clue(r).contains("MANIFEST has changed"))
        assert(clue(r).contains("every source file is unchanged"))
      case other => fail(s"expected Stale, got $other")
    // NEGATIVE: with no policy to compare against, nothing is claimed — a caller that holds no
    // manifest (a spec, a snippet) must not be told its base is stale.
    assertEquals(PortMap.freshness(m, "eng", List(root)), PortMap.Freshness.Fresh)
    // …and an EMPTY surface still digests to something, so `policy=""` can only mean "older engine".
    assertNotEquals(PortMap.policyDigest(Nil), "")
    PortMap.freshness(m0, "eng", List(root), PortMap.policyDigest(Nil)) match
      case PortMap.Freshness.Unverified(r) => assert(clue(r).contains("no policy fingerprint"))
      case other                           => fail(s"expected Unverified, got $other")
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
