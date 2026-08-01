package balticporter.runner

import balticporter.core.{FrontendConfig, PortManifest, Provenance, RuntimeMode}
import balticporter.tir.{ConfigError, RuleScope}
import balticporter.transform.{CollectionsTransform, MutableParamsTransform, TestFrameworkTransform,
  TypeRedirectTransform}

import java.nio.file.{Files, Path}

/** The config front door, held to ONE property: it constructs the same values the Scala path
  * constructs.
  *
  * That is the whole §1.5 defence, so it is what the round-trip tests assert — field by field
  * against a hand-built `PortRun`, not against a golden string. A snapshot of the rendered
  * configuration would pass while the loader built something else entirely.
  *
  * The other half is the refusals. HOCON accepts any document it can parse, so every one of these
  * mistakes is silent by default: an unknown transform, a misspelt key, a `hints` predicate written
  * as data, a `package-rename` in the surface list. Each has a test, because each is exactly the
  * §1(b) no-op the engine refuses everywhere else.
  */
class PortConfigSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------------------------

  private def fixture(conf: String, extra: Map[String, String] = Map.empty): Path =
    val root = Files.createTempDirectory("portconf")
    val src  = root.resolve("java/com/demo")
    Files.createDirectories(src)
    Files.writeString(src.resolve("Widget.java"),
      "package com.demo;\npublic class Widget { public java.util.List<String> labels() { return null; } }\n")
    Files.writeString(src.resolve("Gadget.java"), "package com.demo;\npublic class Gadget {}\n")
    Files.writeString(src.resolve("package-info.java"), "package com.demo;\n")
    extra.foreach((name, text) => Files.writeString(root.resolve(name), text))
    val f = root.resolve("port.conf")
    Files.writeString(f, conf)
    f

  private val Minimal =
    """label = "demo"
      |input  { sourceRoot = "java" }
      |output { portRoot = "out", sourceSet = "main" }
      |manifest { name = "demo" }
      |""".stripMargin

  private def fingerprints(m: PortManifest): List[String] =
    m.effectiveSurface.map(PortManifest.fingerprint)

  // -------------------------------------------------------------------------------------------
  // the round trip
  // -------------------------------------------------------------------------------------------

  test("a conf builds the SAME PortRun a hand-written main builds") {
    val f = fixture(
      """label = "demo"
        |input {
        |  sourceRoot = "java"
        |  resolutionRoots = ["java"]
        |}
        |output { portRoot = "out", sourceSet = "test" }
        |manifest {
        |  name    = "demo"
        |  governs = ["com.demo"]
        |  dropTypes   = ["com.demo.Gone"]
        |  dropMethods = ["com.demo.Widget#gone()"]
        |  packageRenames { "com.demo" = "port.demo" }
        |  typeRenames    { "com.demo.Widget" = "Gizmo" }
        |  subPackages    { "com.demo.Gadget" = "internal" }
        |  flattenNestedTypes = ["com.demo.Widget$Inner"]
        |  allowPackageSplit  = ["com.demo.Gadget"]
        |  surface = [ { transform = "collections" }, { transform = "mutable-params" } ]
        |}
        |provenance {
        |  upstreamName     = "demo-lib"
        |  upstreamCommit   = "abc123"
        |  originalLicense  = "MIT"
        |  sourcePathPrefix = "src/main/java"
        |}
        |runtimeMode = "vendored"
        |nextStep    = "compile it"
        |""".stripMargin)
    val dir = f.getParent

    val fromConf = PortConfig.load(f)
    val byHand = PortRun(
      label     = "demo",
      portRoot  = dir.resolve("out"),
      sourceSet = SourceSet.Test,
      frontend  = FrontendConfig(dir.resolve("java"),
                    List("com/demo/Gadget.java", "com/demo/Widget.java"),
                    Nil, List(dir.resolve("java"))),
      phases    = Nil,
      manifest  = Some(PortManifest(
        name           = "demo",
        governs        = Set("com.demo"),
        dropTypes      = Set("com.demo.Gone"),
        dropMethods    = Set("com.demo.Widget#gone()"),
        packageRenames = Map("com.demo" -> "port.demo"),
        typeRenames        = Map("com.demo.Widget" -> "Gizmo"),
        subPackages        = Map("com.demo.Gadget" -> "internal"),
        flattenNestedTypes = Set("com.demo.Widget$Inner"),
        allowPackageSplit  = Set("com.demo.Gadget"),
        surface        = List(new CollectionsTransform, new MutableParamsTransform),
      )),
      provenance = Some(Provenance("demo-lib", "abc123", "MIT", "src/main/java",
                     dir.resolve("java").toString)),
      runtimeMode = RuntimeMode.Vendored,
      nextStep    = "compile it",
    )

    assertEquals(fromConf.label, byHand.label)
    assertEquals(fromConf.portRoot, byHand.portRoot)
    assertEquals(fromConf.sourceSet, byHand.sourceSet)
    assertEquals(fromConf.frontend, byHand.frontend)
    assertEquals(fromConf.provenance, byHand.provenance)
    assertEquals(fromConf.runtimeMode, byHand.runtimeMode)
    assertEquals(fromConf.determinism, byHand.determinism)
    assertEquals(fromConf.project, byHand.project)
    assertEquals(fromConf.nextStep, byHand.nextStep)
    assertEquals(fromConf.phases, Nil)

    // A `Phase` is a class instance and has no structural equality, so the manifests are compared
    // the way §1.5's own agreement check compares them: the declarative half verbatim, the surface
    // by FINGERPRINT — which is exactly the identity that decides whether two modules agree.
    val (a, b) = (fromConf.manifest.get, byHand.manifest.get)
    assertEquals(a.name, b.name)
    assertEquals(a.governs, b.governs)
    assertEquals(a.effectiveDropTypes, b.effectiveDropTypes)
    assertEquals(a.effectiveDropMethods, b.effectiveDropMethods)
    assertEquals(a.effectivePackageRenames, b.effectivePackageRenames)
    // the PER-TYPE half of the same phase: config and hand-written value construct ONE manifest,
    // so a knob the reader forgets is a silent policy difference between the two front doors.
    assertEquals(a.perTypeDestinations, b.perTypeDestinations)
    assertEquals(a.effectiveAllowPackageSplit, b.effectiveAllowPackageSplit)
    assertEquals(fingerprints(a), fingerprints(b))
  }

  test("package-info.java and module-info.java are excluded by DEFAULT, and the list is sorted") {
    // Every migration program in the corpus filtered these by hand; a default that did not would
    // make the conf path silently emit two files the Scala path never did.
    val files = PortConfig.load(fixture(Minimal)).frontend.files
    assertEquals(files, List("com/demo/Gadget.java", "com/demo/Widget.java"))
  }

  test("paths resolve against THE CONF FILE, not the working directory") {
    val f = PortConfig.load(fixture(Minimal))
    assert(f.frontend.sourceRoot.isAbsolute)
    assertEquals(f.frontend.sourceRoot.getFileName.toString, "java")
    assertEquals(f.frontend.sourceRoot.getParent, f.portRoot.getParent)
  }

  test("a CLI --determinism flag beats the file; the file beats the default") {
    val f = fixture(Minimal + "determinism = \"off\"\n")
    assertEquals(PortConfig.load(f).determinism, Determinism.Off)
    assertEquals(PortConfig.load(f, Seq("--determinism=full")).determinism, Determinism.Full)
    assertEquals(PortConfig.load(fixture(Minimal)).determinism, Determinism.Emission)
  }

  test("a scope reaches the phase, and an empty one is the pre-scope default") {
    def scopeOf(conf: String) = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface
      .collectFirst { case c: CollectionsTransform => c.scope }.get
    assertEquals(
      scopeOf(Minimal.replace("""manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "collections" } ] }""")),
      RuleScope.Everywhere(): RuleScope)
    assertEquals(
      scopeOf(Minimal.replace("""manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "collections", scope { except = ["com.demo.Bridge"] } } ] }""")),
      RuleScope.Everywhere(Set("com.demo.Bridge")): RuleScope)
    assertEquals(
      scopeOf(Minimal.replace("""manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "collections", scope { only = ["com.demo"] } } ] }""")),
      RuleScope.Only(Set("com.demo")): RuleScope)
  }

  // -------------------------------------------------------------------------------------------
  // inheritance
  // -------------------------------------------------------------------------------------------

  test("`base` is `extendedBy`, and it inherits the SURFACE and the drops but not `inject`") {
    val base =
      """label = "base"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest {
        |  name = "base"
        |  dropTypes = ["com.demo.Gone"]
        |  packageRenames { "com.demo" = "port.demo" }
        |  surface = [ { transform = "collections" } ]
        |  inject  = ["java"]
        |}
        |""".stripMargin
    val f = fixture(
      """label = "dependent"
        |base  = "base.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "test" }
        |manifest { name = "dep", surface = [ { transform = "test-framework" } ] }
        |""".stripMargin, Map("base.conf" -> base))

    val m = PortConfig.load(f).manifest.get
    val byHand = PortManifest(
      name = "base", dropTypes = Set("com.demo.Gone"),
      packageRenames = Map("com.demo" -> "port.demo"),
      surface = List(new CollectionsTransform), inject = List(Path.of("java")),
    ).extendedBy(PortManifest(name = "dep", surface = List(new TestFrameworkTransform())))

    assertEquals(m.name, "dep")
    assertEquals(m.baseChain.map(_.name), byHand.baseChain.map(_.name))
    assertEquals(m.effectiveDropTypes, byHand.effectiveDropTypes)
    assertEquals(m.effectivePackageRenames, byHand.effectivePackageRenames)
    // base phases first, then this module's own — the order `effectiveSurface` guarantees
    assertEquals(fingerprints(m), fingerprints(byHand))
    // `inject` is the field §1.5 puts on the must-DIFFER side: exactly one module ships each
    // replacement file, so a dependent that inherited it would define the same FQN twice.
    assertEquals(m.inject, Nil)
    assertEquals(m.substitutions.inject, Nil)
  }

  test("a base cycle is refused by name") {
    val f = fixture(
      """label = "a"
        |base  = "b.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "a" }
        |""".stripMargin,
      Map("b.conf" ->
        """base = "b.conf"
          |manifest { name = "b" }
          |""".stripMargin))
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("base chain"))
  }

  test("…and through a SYMLINK too — §5.4, where the failure is a CRASH and not a wrong number") {
    // `resolvePath` is lexical BY DESIGN (the class doc: a conf-relative path resolves to the same
    // place either way), which is right for RESOLUTION and wrong for COMPARISON. Spelled through a
    // link — a git worktree reaching a sibling checkout is the normal case — the two names of one
    // file compare unequal at every hop, so the cycle below is never detected and `readManifest`
    // recurses until the stack goes. A `StackOverflowError` instead of the ConfigError above.
    val root = Files.createTempDirectory("portconf-link")
    Files.createDirectories(root.resolve("java/com/demo"))
    Files.writeString(root.resolve("java/com/demo/Widget.java"), "package com.demo;\npublic class Widget {}\n")
    // b.conf names ITSELF through a symlinked directory that points back at the conf's own dir
    try Files.createSymbolicLink(root.resolve("via"), root)
    catch case _: UnsupportedOperationException => assume(false, "filesystem without symlinks")
    Files.writeString(root.resolve("b.conf"),
      """base = "via/b.conf"
        |manifest { name = "b" }
        |""".stripMargin)
    Files.writeString(root.resolve("port.conf"),
      """label = "a"
        |base  = "b.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "a" }
        |""".stripMargin)
    val e = intercept[ConfigError](PortConfig.load(root.resolve("port.conf")))
    assert(clue(e.getMessage).contains("base chain"))
  }

  test("a base contributes its MANIFEST and nothing else — its build halves are not junk") {
    // The base conf below carries a full `input`/`output`/`provenance`; none of it is this run's
    // business (§1.5's must-differ column), and reporting it as an unread key would make every
    // real two-module port unloadable.
    val base =
      """label = "base"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |provenance { upstreamName = "x", originalLicense = "MIT", sourcePathPrefix = "s" }
        |runtimeMode = "vendored"
        |nextStep = "…"
        |manifest { name = "base" }
        |""".stripMargin
    val f = fixture(
      """label = "dep"
        |base  = "base.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "test" }
        |manifest { name = "dep" }
        |""".stripMargin, Map("base.conf" -> base))
    assertEquals(PortConfig.load(f).manifest.get.baseChain.map(_.name), List("base"))
  }

  // -------------------------------------------------------------------------------------------
  test("`type-redirect` reads BOTH entry shapes out of one map, and the flat one is unchanged") {
    // The flat form is published — every port that writes it must keep working — so the entry that
    // grew `memberRenames` spells itself as an object BESIDE it, in the same map. The identity
    // compared is `surfaceFingerprint`, because that is what decides whether two modules agree
    // about the emitted surface (§1.5), and because an entry with no renames must still render
    // exactly what it always did or every base/dependent pair predating this feature disagrees.
    def fp(entries: String) = PortConfig.load(fixture(Minimal.replace(
      """manifest { name = "demo" }""",
      s"""manifest { name = "demo", surface = [ { transform = "type-redirect", redirects { $entries } } ] }"""
    ))).manifest.get.effectiveSurface.collectFirst { case t: TypeRedirectTransform => t.surfaceFingerprint }.get

    assertEquals(fp(""""a.B" = "c.D""""), "a.B->c.D")
    assertEquals(fp("""  "a.B" = { to = "c.D" }  """), "a.B->c.D")
    assertEquals(
      fp("""  "a.B" = "c.D"
           |  "a.Disposable" = { to = "java.lang.AutoCloseable"
           |                     memberRenames { dispose = "close" } }  """.stripMargin),
      "a.B->c.D,a.Disposable->java.lang.AutoCloseable[dispose=close]")
  }

  test("a misspelt key INSIDE a redirect entry fails the run — the shape probe is not a read") {
    val f = fixture(Minimal.replace("""manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "type-redirect",
        |  redirects { "a.B" = { to = "c.D", memberRename { x = "y" } } } } ] }""".stripMargin))
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("memberRename"))
  }

  // -------------------------------------------------------------------------------------------
  // refusals — every one of these is silent under plain HOCON
  // -------------------------------------------------------------------------------------------

  test("an unknown transform names every factory the classpath actually offers") {
    val f = fixture(Minimal.replace("""manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collectionz" } ] }"""))
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("unknown transform 'collectionz'"))
    assert(clue(e.getMessage).contains("collections"))
    assert(clue(e.getMessage).contains("META-INF/services"))
  }

  test("`package-rename` as a surface entry is refused BY NAME, pointing at the manifest field") {
    // Not "unknown transform": a port told that would reasonably conclude the feature is missing.
    // It is not missing — it is manifest DATA, because it must run after every other phase and
    // `runsAfter` cannot say "after everything" (CLAUDE.md §4.56).
    val f = fixture(Minimal.replace("""manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "package-rename" } ] }"""))
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("manifest.packageRenames"))
    assert(!clue(e.getMessage).contains("unknown transform"))
  }

  test("a key nobody read fails the run, at its full path") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java", resolutionRootz = ["java"] }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo", dropType = ["com.demo.Gone"] }
        |""".stripMargin)
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("input.resolutionRootz"))
    assert(clue(e.getMessage).contains("manifest.dropType"))
  }

  test("a key nobody read fails INSIDE a surface entry too") {
    val f = fixture(Minimal.replace("""manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "test-framework", suit = "munit.FunSuite" } ] }"""))
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("surface[0].suit"))
  }

  test("a value of the wrong SHAPE is an error, never a quiet widening") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java", files = "com/demo/Widget.java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo" }
        |""".stripMargin)
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("expected a list of strings"))
  }

  test("`hints` refuses to be data, and names the escape hatch") {
    val f = fixture(Minimal.replace("""manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [
        |  { transform = "primitive-to-opaque", fqn = "port.Handle", hints = "x => true" } ] }""".stripMargin))
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("TransformFactory"))
  }

  test("a scope declaring both directions is refused") {
    val f = fixture(Minimal.replace("""manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [
        |  { transform = "collections", scope { except = ["a"], only = ["b"] } } ] }""".stripMargin))
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("never both"))
  }

  test("`files` and `includeGlobs` together have no honest reading") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java", files = ["com/demo/Widget.java"], includeGlobs = ["**.java"] }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo" }
        |""".stripMargin)
    intercept[ConfigError](PortConfig.load(f))
  }

  test("a declared classpathFile that is not there is FATAL, never an empty classpath") {
    // §5.1's missing-input rule, and it bites harder here: an unresolved `org.junit.Assert` import
    // does not fail the frontend, it resolves WRONGLY.
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java", classpathFile = "nope.txt" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo" }
        |""".stripMargin)
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("classpathFile"))
  }

  test("a missing conf file is named, not treated as an empty document") {
    intercept[ConfigError](PortConfig.load(Path.of("/nowhere/absent.conf")))
  }

  // -------------------------------------------------------------------------------------------
  // discovery
  // -------------------------------------------------------------------------------------------

  test("discovery finds the engine's built-ins AND a factory the engine knows nothing about") {
    val found = TransformRegistry.discover().names
    BuiltinFactories.all.map(_.name).foreach(n => assert(found.contains(n), s"$n was not discovered"))
    // …and a stranger's, registered exactly the way a porting repository registers a §1(c) rule.
    // A registry that only ever saw classes from its own jar would pass every test the engine can
    // write and fail the first consumer.
    assert(clue(found).contains("spec-echo"))
  }

  test("the service file and `BuiltinFactories.all` name the same classes") {
    // A factory in one and not the other is reachable from a Scala embedder and invisible to the
    // config front door, or the reverse — and neither shows up as a failure anywhere else.
    // EVERY such resource on the classpath, not the first one: the test source set contributes a
    // second file, and `getResourceAsStream` would silently return whichever came first.
    val declared = collection.mutable.ListBuffer.empty[String]
    val urls = getClass.getClassLoader.getResources("META-INF/services/balticporter.tir.TransformFactory")
    while urls.hasMoreElements do
      val src = scala.io.Source.fromURL(urls.nextElement())
      try declared ++= src.getLines().map(_.trim).filter(l => l.nonEmpty && !l.startsWith("#"))
      finally src.close()
    // …minus the deliberate stranger the test source set registers.
    val engineDeclared = declared.toList.filterNot(_.endsWith(".SpecEchoFactory"))
    assertEquals(engineDeclared.sorted, BuiltinFactories.all.map(_.getClass.getName).sorted)
  }

  test("two factories claiming one name is refused, not resolved") {
    intercept[ConfigError](TransformRegistry.of(new SpecEchoFactory, new SpecEchoFactory).names)
  }

  test("a factory built from config reaches the pipeline with its config applied") {
    val f = fixture(Minimal.replace("""manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "spec-echo", tag = "hello" } ] }"""))
    assertEquals(PortConfig.load(f).manifest.get.effectiveSurface.map(_.name), List("spec-echo(hello)"))
  }
