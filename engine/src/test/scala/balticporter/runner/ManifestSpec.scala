package balticporter.runner

import balticporter.core.*
import balticporter.core.ManifestAgreement.Kind
import balticporter.tir.RuleScope
import balticporter.transform.{ClassTableTransform, CollectionsTransform, MutableParamsTransform, StaticForwarderTransform, TypeRedirectTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Cross-port composition, over a genuine TWO-PORT fixture.
  *
  * The libGDX corpus already has a real dependent port (`LibgdxTestMigrate` resolves against
  * `gdx/src` and converts `gdx/test`), and the three deliberate perturbations were measured there.
  * What it cannot give is a case small enough to assert on line by line, or one that runs without
  * a vendored copy of libGDX on disk — so the same properties are pinned here, on two tiny Java
  * trees where `other` resolves against `base` exactly the way an extension module resolves against
  * the module it extends.
  */
class ManifestSpec extends munit.FunSuite:

  private def writeJava(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  /** two modules: `com.demo` (the base) and `com.demo2` (a dependent that references it). */
  private def twoModules(): (Path, Path, Path) =
    val root = Files.createTempDirectory("manifest")
    val base = root.resolve("base")
    writeJava(base, "com/demo/Widget.java",
      """package com.demo;
        |public class Widget {
        |  public int size;
        |  public java.util.List<String> labels() { return null; }
        |}""".stripMargin)
    writeJava(base, "com/demo/Gadget.java",
      """package com.demo;
        |public class Gadget { public Widget w = new Widget(); }""".stripMargin)
    val dep = root.resolve("dep")
    writeJava(dep, "com/demo2/Uses.java",
      """package com.demo2;
        |import com.demo.Widget;
        |public class Uses { public Widget w = new Widget(); }""".stripMargin)
    (root, base, dep)

  private def emitted(out: Path): List[String] =
    if !Files.exists(out) then Nil
    else Files.walk(out).iterator().asScala.filter(_.toString.endsWith(".scala"))
      .map(p => out.relativize(p).toString.replace('\\', '/')).toList.sorted

  /** the agreement findings that say the two modules DISAGREE, as opposed to the operational notes
    * about how the check was answered.
    *
    * A unit-test JVM has `CheckReport` off, so no `PortRun` here publishes a port map and every
    * declared base is reported `BaseMapMissing` — correctly: the agreement really was re-derived
    * from the manifest rather than read off the base's output. Filtering it keeps each assertion
    * about what it is testing, and the note itself is pinned once, where it is the subject. */
  private def disagreements(r: PortResult): List[ManifestAgreement.Finding] =
    r.report.manifest.filterNot(f =>
      f.kind == Kind.BaseMapMissing || f.kind == Kind.BaseMapStale || f.kind == Kind.BaseMapUnverified)

  private def runBase(root: Path, base: Path, m: PortManifest): PortResult =
    PortRun("base", root.resolve("port-base"), SourceSet.Main,
      FrontendConfig(base, List("com/demo/Widget.java", "com/demo/Gadget.java"), Nil), Nil,
      manifest = Some(m)).execute()

  private def runDependent(root: Path, base: Path, dep: Path, m: PortManifest): PortResult =
    PortRun("dependent", root.resolve("port-dep"), SourceSet.Main,
      FrontendConfig(dep, List("com/demo2/Uses.java"), Nil, resolutionRoots = List(base)), Nil,
      manifest = Some(m)).execute()

  /** the run ABORTS on a fatal disagreement, having first printed every finding to stderr — so
    * what a caller sees is captured here rather than reconstructed. */
  private def caught(f: => Any): String =
    val buf = new java.io.ByteArrayOutputStream
    val old = System.err
    val msg =
      try
        System.setErr(new java.io.PrintStream(buf, true))
        intercept[RuntimeException](f).getMessage
      finally System.setErr(old)
    s"$msg\n${buf.toString}"

  // -------------------------------------------------------------------------
  // the value and its composition operation
  // -------------------------------------------------------------------------

  test("extendedBy composes drops, renames and surface — and does NOT inherit `inject`") {
    val phase = new CollectionsTransform
    val own   = new CollectionsTransform
    val core = PortManifest("core",
      dropTypes      = Set("com.demo.Widget"),
      dropMethods    = Set("com.demo.Widget#labels"),
      packageRenames = Map("com.demo" -> "org.port"),
      surface        = List(phase),
      inject         = List(Path.of("/base/overrides")))
    val ext = core.extendedBy(PortManifest("ext",
      dropTypes = Set("com.demo2.Own"), surface = List(own), inject = List(Path.of("/ext/overrides"))))

    assertEquals(ext.effectiveDropTypes, Set("com.demo.Widget", "com.demo2.Own"))
    assertEquals(ext.effectiveDropMethods, Set("com.demo.Widget#labels"))
    assertEquals(ext.effectivePackageRenames, Map("com.demo" -> "org.port"))
    assertEquals(ext.effectiveSurface.map(_ eq phase), List(true, false))
    assertEquals(ext.effectiveSurface.size, 2)
    // the drop is inherited, the REPLACEMENT is not: exactly one module ships each file, or the
    // dependent emits a second definition of the same FQN.
    assertEquals(ext.inject, List(Path.of("/ext/overrides")))
    assertEquals(ext.substitutions.inject, List(Path.of("/ext/overrides")))
    // and the dependent is not answerable for replacing what its base dropped
    assertEquals(ext.ownDrops.dropTypes, Set("com.demo2.Own"))
    assertEquals(ext.baseChain.map(_.name), List("core"))
  }

  test("a chain composes transitively, nearest declaration last") {
    val a = PortManifest("a", packageRenames = Map("com.demo" -> "a"))
    val b = a.extendedBy(PortManifest("b", dropTypes = Set("x")))
    val c = b.extendedBy(PortManifest("c", dropTypes = Set("y")))
    assertEquals(c.baseChain.map(_.name), List("a", "b"))
    assertEquals(c.effectiveDropTypes, Set("x", "y"))
    assertEquals(c.effectivePackageRenames, Map("com.demo" -> "a"))
  }

  test("`mirroring` declares a base to be CHECKED against and inherits nothing") {
    val core = PortManifest("core", dropTypes = Set("com.demo.Widget"))
    val ext  = PortManifest("ext").mirroring(core)
    assertEquals(ext.baseChain.map(_.name), List("core"))
    assertEquals(ext.effectiveDropTypes, Set.empty[String])
  }

  test("a manifest and a raw policy are mutually exclusive — a run may never hold two policies") {
    val (root, base, _) = twoModules()
    val e = intercept[IllegalArgumentException] {
      PortRun("base", root.resolve("p"), SourceSet.Main,
        FrontendConfig(base, List("com/demo/Widget.java"), Nil),
        phases = List(new CollectionsTransform),
        manifest = Some(PortManifest("core"))).execute()
    }
    assert(clue(e.getMessage).contains("SUPPLIES"))
  }

  // -------------------------------------------------------------------------
  // (i) matching manifests compose and AGREE
  // -------------------------------------------------------------------------

  test("matching manifests: the dependent inherits, emits only its own units, and agrees") {
    val (root, base, dep) = twoModules()
    val core = PortManifest("core",
      governs = Set("com.demo"), surface = List(new CollectionsTransform),
      packageRenames = Map("com.demo" -> "org.port"))
    val b = runBase(root, base, core)
    assertEquals(emitted(b.outDir), List("org/port/Gadget.scala", "org/port/Widget.scala"))
    assertEquals(b.report.manifest, Nil, "a BASE port has no shared surface, so the check is a no-op")

    val d = runDependent(root, base, dep, core.extendedBy(PortManifest("ext", governs = Set("com.demo2"))))
    assertEquals(emitted(d.outDir), List("com/demo2/Uses.scala"))
    // The base declares shared-surface policy and — in a unit-test JVM, where `CheckReport` is off —
    // publishes no port map, so the agreement below is RE-DERIVED and says so. That note is the
    // whole point of it: the fallback to the weaker check is never silent. Everything else agrees.
    assertEquals(disagreements(d), Nil)
    assertEquals(d.report.manifest.map(_.kind), List(ManifestAgreement.Kind.BaseMapMissing))
    // the shared surface really was carried across: the dependent's reference names the base's
    // RENAMED type, which is the whole thing this item exists to keep true
    assert(clue(Files.readString(d.outDir.resolve("com/demo2/Uses.scala"))).contains("org.port.Widget"))
  }

  test("a dropped base type is inherited: tagged here, not emitted here, and not this module's to replace") {
    val (root, base, dep) = twoModules()
    val inject = root.resolve("base-overrides")
    writeJava(inject, "com/demo/Widget.scala", "package com.demo\nclass Widget { def size: Int = 0 }")
    val core = PortManifest("core", governs = Set("com.demo"),
      dropTypes = Set("com.demo.Widget"), inject = List(inject))
    runBase(root, base, core)

    val d = runDependent(root, base, dep, core.extendedBy(PortManifest("ext")))
    assertEquals(disagreements(d), Nil)
    // nothing injected here, and CHECK 2 does not hold this module to a replacement its base ships
    assertEquals(d.injected, 0)
    assertEquals(d.report.substitution, Nil)
  }

  // -------------------------------------------------------------------------
  // (ii) a deliberately mismatched manifest is CAUGHT
  // -------------------------------------------------------------------------

  test("MISMATCH: divergent package rename") {
    val (root, base, dep) = twoModules()
    val core = PortManifest("core", governs = Set("com.demo"), packageRenames = Map("com.demo" -> "org.port"))
    val drift = PortManifest("ext", packageRenames = Map("com.demo" -> "org.somewhere.else")).mirroring(core)
    val msg = caught(runDependent(root, base, dep, drift))
    assert(clue(msg).contains("fatal"))
    // stated twice: from the declarations, and from what the run actually emitted the type as
    assert(clue(msg).contains("RenameDivergence"))
    assert(clue(msg).contains("SurfaceNameDivergence"))
  }

  test("MISMATCH: the base leaves the shared namespace in place and the dependent moves it") {
    val (root, base, dep) = twoModules()
    val core  = PortManifest("core", governs = Set("com.demo"))
    val drift = PortManifest("ext", packageRenames = Map("com.demo" -> "org.port")).mirroring(core)
    assert(clue(caught(runDependent(root, base, dep, drift))).contains("RenameOverride"))
  }

  test("MISMATCH: a type dropped by the base and not by the dependent") {
    val (root, base, dep) = twoModules()
    val inject = root.resolve("base-overrides")
    writeJava(inject, "com/demo/Widget.scala", "package com.demo\nclass Widget { def size: Int = 0 }")
    val core  = PortManifest("core", governs = Set("com.demo"),
      dropTypes = Set("com.demo.Widget"), inject = List(inject))
    val drift = PortManifest("ext").mirroring(core)
    val msg = caught(runDependent(root, base, dep, drift))
    // the STATIC layer sees the missing declaration…
    assert(clue(msg).contains("MissingDrop"))
    // …and the DYNAMIC layer sees what it caused: a resolution-root type tagged `Substituted` in
    // the base port and modelled as an ordinary type here. That sentence is the audit's own.
    assert(clue(msg).contains("TagMissing"))
  }

  test("MISMATCH: a type the base emits, dropped by the dependent") {
    val (root, base, dep) = twoModules()
    val core  = PortManifest("core", governs = Set("com.demo"))
    val inject = root.resolve("ext-overrides")
    writeJava(inject, "com/demo/Widget.scala", "package com.demo\nclass Widget { def size: Int = 0 }")
    val drift = PortManifest("ext", dropTypes = Set("com.demo.Widget"), inject = List(inject)).mirroring(core)
    val msg = caught(runDependent(root, base, dep, drift))
    assert(clue(msg).contains("ExtraDrop"))
    assert(clue(msg).contains("TagUnexpected"))
  }

  test("MISMATCH: divergent collection retyping") {
    val (root, base, dep) = twoModules()
    val core  = PortManifest("core", governs = Set("com.demo"), surface = List(new CollectionsTransform))
    val drift = PortManifest("ext").mirroring(core)
    val msg = caught(runDependent(root, base, dep, drift))
    assert(clue(msg).contains("SurfaceMissing"))
    assert(clue(msg).contains("java-collections->scala"))
  }

  test("MERGE: a base and a dependent that each configure a `type-redirect` now COMPOSE, and run") {
    // `ENGINE-LIMITS.md` D9's first row, from the other side: before the merge contract this pair
    // was 1 fatal `SurfaceDivergence` and the base could not gain the phase at all. Both keys name
    // types the base DROPS, which is what the `governs` screen requires of an added subject
    // (DESIGN.md §8.13).
    val (root, base, dep) = twoModules()
    val core = PortManifest("core", governs = Set("com.demo"),
      dropTypes = Set("com.demo.Widget", "com.demo.Gadget"),
      surface   = List(new TypeRedirectTransform(Map("com.demo.Widget" -> "com.demo2.MyWidget"))))
    val ext = core.extendedBy(PortManifest("ext",
      surface = List(new TypeRedirectTransform(Map("com.demo.Gadget" -> "com.demo2.MyGadget")))))

    // ONE phase in the effective pipeline, holding BOTH tables
    assertEquals(ext.effectiveSurface.map(_.name), List("type-redirect"))
    assertEquals(
      ext.effectiveSurface.collectFirst { case t: TypeRedirectTransform => t.redirects }.get,
      Map("com.demo.Widget" -> "com.demo2.MyWidget", "com.demo.Gadget" -> "com.demo2.MyGadget"))

    val d = runDependent(root, base, dep, ext)
    assert(clue(disagreements(d)).forall(!_.kind.fatal))
    // …and the merged table is the one that RAN: this module's reference to the base's dropped type
    // is re-pointed, from an entry the BASE declared
    assert(clue(Files.readString(d.outDir.resolve("com/demo2/Uses.scala"))).contains("com.demo2.MyWidget"))
  }

  test("MISMATCH: one phase, twice, configured differently") {
    val (root, base, dep) = twoModules()
    val core  = PortManifest("core", governs = Set("com.demo"),
      surface = List(new ClassTableTransform(Map("com.demo.Widget#of" -> "com.demo.T#classFor"))))
    val drift = core.extendedBy(PortManifest("ext",
      surface = List(new ClassTableTransform(Map("com.demo.Widget#of" -> "com.demo.OTHER#classFor")))))
    val msg = caught(runDependent(root, base, dep, drift))
    assert(clue(msg).contains("SurfaceDivergence"))
  }

  test("MISMATCH: a dependent port that names no base at all") {
    val (root, base, dep) = twoModules()
    // no manifest whatsoever — the state every port in this repository was in before this check
    val bare = caught(PortRun("dependent", root.resolve("p1"), SourceSet.Main,
      FrontendConfig(dep, List("com/demo2/Uses.java"), Nil, resolutionRoots = List(base)), Nil).execute())
    assert(clue(bare).contains("NoBaseDeclared"))
    // a manifest that declares no `bases` is no better, and says so
    val empty = caught(runDependent(root, base, dep, PortManifest("ext")))
    assert(clue(empty).contains("NoBaseDeclared"))
  }

  test("self-resolution is not a dependency: a port resolving against its OWN root needs no base") {
    val (root, base, _) = twoModules()
    val r = PortRun("base", root.resolve("p"), SourceSet.Main,
      FrontendConfig(base, List("com/demo/Widget.java"), Nil, resolutionRoots = List(base)), Nil).execute()
    assertEquals(r.report.manifest, Nil)
  }

  // -------------------------------------------------------------------------
  // the check as a pure function — every branch, without a filesystem
  // -------------------------------------------------------------------------

  private val core = PortManifest("core", governs = Set("com.demo"),
    dropTypes = Set("com.demo.Widget"), packageRenames = Map("com.demo" -> "org.port"))

  test("an agreeing dependent produces nothing, on either layer") {
    val renameOnly = PortManifest("core", governs = Set("com.demo"), packageRenames = Map("com.demo" -> "org.port"))
    val m = renameOnly.extendedBy(PortManifest("ext"))
    val shared = List(
      ManifestAgreement.SharedType("com.demo.Widget", "org.port.Widget", substituted = false),
      ManifestAgreement.SharedType("com.demo.Gadget", "org.port.Gadget", substituted = false))
    assertEquals(ManifestAgreement.check(Some(m), shared, foreignRoots = true), Nil)
  }

  test("a base port is not asked to agree with anything") {
    assertEquals(ManifestAgreement.check(Some(core), Nil, foreignRoots = false), Nil)
    assertEquals(ManifestAgreement.check(None, Nil, foreignRoots = false), Nil)
  }

  test("an inherited key that never fired is reported, and is NOT fatal") {
    val m = core.extendedBy(PortManifest("ext"))
    val fs = ManifestAgreement.check(Some(m), Nil, foreignRoots = true)
    assertEquals(fs.map(_.kind), List(Kind.InheritedKeyNeverFired))
    assertEquals(fs.map(_.base), List("core"))
    assert(!Kind.InheritedKeyNeverFired.fatal)
  }

  test("every finding renders its §1 classification — an agent must not have to investigate to act") {
    Kind.values.foreach(k => assert(clue(k.classification).contains("§1"), k.toString))
  }

  test("a fingerprint is stable across two equal policies and separates two different ones") {
    def fwd(ms: Set[String]) = new StaticForwarderTransform(List(
      StaticForwarderTransform.Forwarder("com.demo.W", "java.lang.Class", ms)))
    assertEquals(PortManifest.fingerprint(fwd(Set("a", "b"))), PortManifest.fingerprint(fwd(Set("b", "a"))))
    assertNotEquals(PortManifest.fingerprint(fwd(Set("a"))), PortManifest.fingerprint(fwd(Set("a", "b"))))
    // a phase that does NOT declare its policy is compared by name only — the documented blind spot
    assertEquals(PortManifest.fingerprint(new MutableParamsTransform), "reassigned-params->var")
    // …and one that DOES carries it, even when the policy is the default: the collections phase
    // takes a `RuleScope`, and two modules scoping it differently emit signatures that each compile
    // alone and cannot compile together, which is precisely what `SurfacePolicy` is for (§1.5). The
    // default renders empty, so a port that sets no scope compares as it always did.
    assertEquals(PortManifest.fingerprint(new CollectionsTransform), "java-collections->scala[]")
    assertNotEquals(
      PortManifest.fingerprint(new CollectionsTransform),
      PortManifest.fingerprint(new CollectionsTransform(RuleScope.Everywhere(Set("com.demo.Bridge")))),
    )
  }

  test("`renamed` cuts only at a separator — com.demo must not cover com.demonstrate") {
    assertEquals(core.renamed("com.demo.Widget"), "org.port.Widget")
    assertEquals(core.renamed("com.demo.Outer$Inner"), "org.port.Outer$Inner")
    assertEquals(core.renamed("com.demonstrate.X"), "com.demonstrate.X")
  }
