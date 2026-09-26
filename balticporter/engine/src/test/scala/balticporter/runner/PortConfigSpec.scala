package balticporter.runner

import balticporter.core.{ FrontendConfig, ManifestAgreement, PortManifest, Provenance, RuntimeMode }
import balticporter.tir.{ ConfigError, Descriptor, Param, RuleScope }
import balticporter.transform.{ BeanPropertyTransform, CollectionsTransform, MethodBodyTransform, MutableParamsTransform, TestFrameworkTransform, TypeRedirectTransform }

import java.nio.file.{ Files, Path }

/** The config front door, held to ONE property: it constructs the same values the Scala path constructs.
  */
class PortConfigSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------------------------

  private def fixture(conf: String, extra: Map[String, String] = Map.empty): Path =
    val root = Files.createTempDirectory("portconf")
    val src  = root.resolve("java/com/demo")
    Files.createDirectories(src)
    Files.writeString(
      src.resolve("Widget.java"),
      "package com.demo;\npublic class Widget { public java.util.List<String> labels() { return null; } }\n"
    )
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
        |  resolutionExcludes = ["com/demo/emu"]
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
        |""".stripMargin
    )
    val dir = f.getParent

    val fromConf = PortConfig.load(f)
    val byHand   = PortRun(
      label = "demo",
      portRoot = dir.resolve("out"),
      sourceSet = SourceSet.Test,
      // `resolutionExcludes` is RELATIVE to whichever root contains it and is NOT resolved against
      // the config's directory — the same string has to answer for every root, exactly as
      // `includeGlobs`/`excludeGlobs` are relative to `sourceRoot`. Spelled here so the round trip
      // fails if the loader ever starts resolving it as a path.
      frontend = FrontendConfig(
        dir.resolve("java"),
        List("com/demo/Gadget.java", "com/demo/Widget.java"),
        Nil,
        List(dir.resolve("java")),
        List("com/demo/emu")
      ),
      phases = Nil,
      manifest = Some(
        PortManifest(
          name = "demo",
          governs = Set("com.demo"),
          dropTypes = Set("com.demo.Gone"),
          dropMethods = Set("com.demo.Widget#gone()"),
          packageRenames = Map("com.demo" -> "port.demo"),
          typeRenames = Map("com.demo.Widget" -> "Gizmo"),
          subPackages = Map("com.demo.Gadget" -> "internal"),
          flattenNestedTypes = Set("com.demo.Widget$Inner"),
          allowPackageSplit = Set("com.demo.Gadget"),
          surface = List(new CollectionsTransform, new MutableParamsTransform)
        )
      ),
      provenance = Some(Provenance("demo-lib", "abc123", "MIT", "src/main/java", dir.resolve("java").toString)),
      runtimeMode = RuntimeMode.Vendored,
      nextStep = "compile it"
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
    // the way the agreement check between modules compares them: the declarative half verbatim,
    // the surface by FINGERPRINT — which is exactly the identity that decides whether two modules agree.
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

  // named path roots: a configuration taken out of a jar is told where its inputs and outputs are

  private val Rooted =
    """label = "demo"
      |roots  { upstream = ".", work = "scratch" }
      |input  { sourceRoot = "@upstream/java" }
      |output { portRoot = "@work/out", sourceSet = "main" }
      |manifest { name = "demo" }
      |""".stripMargin

  test("a path under a named root resolves against the root's declared default, relative to the conf") {
    val conf = fixture(Rooted)
    val run  = PortConfig.load(conf)
    assertEquals(run.frontend.sourceRoot, conf.getParent.resolve("java").normalize)
    assertEquals(run.portRoot, conf.getParent.resolve("scratch/out").normalize)
  }

  test("the caller's override of a named root wins, and only moves the paths under that root") {
    val conf      = fixture(Rooted)
    val elsewhere = Files.createTempDirectory("portconf-out")
    val run       = PortConfig.load(conf, roots = Map("work" -> elsewhere))
    assertEquals(run.portRoot, elsewhere.toAbsolutePath.normalize.resolve("out"))
    assertEquals(run.frontend.sourceRoot, conf.getParent.resolve("java").normalize)
  }

  test("an override for a root the conf does not declare is refused by name") {
    val e = intercept[balticporter.tir.ConfigError](PortConfig.load(fixture(Rooted), roots = Map("upstrem" -> Path.of("/tmp"))))
    assert(clue(e.getMessage).contains("upstrem"))
    assert(clue(e.getMessage).contains("upstream, work"))
  }

  test("a path under a root nobody declared is refused by name") {
    val conf = fixture(Rooted.replace("@work/out", "@wrok/out"))
    val e    = intercept[balticporter.tir.ConfigError](PortConfig.load(conf))
    assert(clue(e.getMessage).contains("wrok"))
  }

  test("a source root that is a symbolic link is walked through the link") {
    val conf = fixture(Minimal)
    val dir  = conf.getParent
    Files.move(dir.resolve("java"), dir.resolve("java-real"))
    Files.createSymbolicLink(dir.resolve("java"), dir.resolve("java-real"))
    val run = PortConfig.load(conf)
    assertEquals(run.frontend.files, List("com/demo/Gadget.java", "com/demo/Widget.java"))
  }

  test("a source root under which nothing is selected is refused, not converted as an empty port") {
    val conf = fixture(Minimal.replace("input  { sourceRoot = \"java\" }", "input  { sourceRoot = \"java\", includeGlobs = [\"**.kt\"] }"))
    val e    = intercept[balticporter.tir.ConfigError](PortConfig.load(conf))
    assert(clue(e.getMessage).contains("converts nothing"))
  }

  test("classpathCoordinates without a classpathFile is refused: the resolved classpath has to be kept somewhere") {
    val conf = fixture(
      Minimal.replace("input  { sourceRoot = \"java\" }", "input  { sourceRoot = \"java\", classpathCoordinates = [\"g:a:1\"] }")
    )
    val e = intercept[balticporter.tir.ConfigError](PortConfig.load(conf))
    assert(clue(e.getMessage).contains("classpathFile"))
  }

  test("classpathCoordinates reuses a classpathFile that still records these coordinates and whose jars exist") {
    val conf = fixture(
      Minimal.replace(
        "input  { sourceRoot = \"java\" }",
        "input  { sourceRoot = \"java\", classpathCoordinates = [\"g:a:1\"], classpathFile = \"cp/demo.txt\" }"
      )
    )
    val jar = Files.createFile(conf.getParent.resolve("a-1.jar"))
    val out = Files.createDirectories(conf.getParent.resolve("cp")).resolve("demo.txt")
    ClasspathCache.write(out, jar.toString, ClasspathCache.key(List("g:a:1")))
    val run = PortConfig.load(conf)
    assertEquals(run.frontend.classpath.map(_.getFileName.toString), List("a-1.jar"))
  }

  test("a conf with no roots and no overrides loads exactly as before") {
    val run = PortConfig.load(fixture(Minimal), roots = Map.empty)
    assertEquals(run.frontend.sourceRoot.getFileName.toString, "java")
  }

  test("a CLI --determinism flag beats the file; the file beats the default") {
    val f = fixture(Minimal + "determinism = \"off\"\n")
    assertEquals(PortConfig.load(f).determinism, Determinism.Off)
    assertEquals(PortConfig.load(f, Seq("--determinism=full")).determinism, Determinism.Full)
    assertEquals(PortConfig.load(fixture(Minimal)).determinism, Determinism.Emission)
  }

  test("a scope reaches the phase, and an empty one is the pre-scope default") {
    def scopeOf(conf: String) = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c.scope }.get
    assertEquals(
      scopeOf(
        Minimal.replace("""manifest { name = "demo" }""", """manifest { name = "demo", surface = [ { transform = "collections" } ] }""")
      ),
      RuleScope.Everywhere(): RuleScope
    )
    assertEquals(
      scopeOf(
        Minimal.replace(
          """manifest { name = "demo" }""",
          """manifest { name = "demo", surface = [ { transform = "collections", scope { except = ["com.demo.Bridge"] } } ] }"""
        )
      ),
      RuleScope.Everywhere(Set("com.demo.Bridge")): RuleScope
    )
    assertEquals(
      scopeOf(
        Minimal.replace(
          """manifest { name = "demo" }""",
          """manifest { name = "demo", surface = [ { transform = "collections", scope { only = ["com.demo"] } } ] }"""
        )
      ),
      RuleScope.Only(Set("com.demo")): RuleScope
    )
  }

  test("a method-body group reaches the phase, and no group is the shared instance") {
    def groupsOf(conf: String) =
      PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collect { case m: MethodBodyTransform => m.group }
    assertEquals(
      groupsOf(
        Minimal.replace(
          """manifest { name = "demo" }""",
          """manifest { name = "demo", surface = [ { transform = "method-body", bodies { "com.demo.Widget#labels" = "{ null }" } } ] }"""
        )
      ),
      List("")
    )
    // two instances in ONE manifest is exactly what a group is for: without it the second entry
    // would be a second unnamed instance of the same phase.
    assertEquals(
      groupsOf(
        Minimal.replace(
          """manifest { name = "demo" }""",
          """manifest { name = "demo", surface = [
            |  { transform = "method-body", bodies { "com.demo.Widget#labels" = "{ null }" } },
            |  { transform = "method-body", group = "late", bodies { "com.demo.Gadget#toString" = "{ \"g\" }" } }
            |] }""".stripMargin
        )
      ),
      List("", "late")
    )
  }

  // -------------------------------------------------------------------------------------------
  // retargetRewrites config parsing
  // -------------------------------------------------------------------------------------------

  test("retargetRewrites with Rename entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" { "get/1" = "apply", "set/1" = "addOne" } }
        |} ] }""".stripMargin
    )
    val ct = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    assertEquals(ct.retargetRewrites.size, 1)
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("get", 1)), CollectionsTransform.RetargetRewrite.Rename("apply"))
    assertEquals(tbl(("set", 1)), CollectionsTransform.RetargetRewrite.Rename("addOne"))
  }

  test("retargetRewrites with BoolDispatch entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "removeValue/2" { boolDispatch = 1, onTrue = "removeByRef", onFalse = "removeByVal" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("removeValue", 2)), CollectionsTransform.RetargetRewrite.BoolDispatch(1, "removeByRef", "removeByVal"))
  }

  test("retargetRewrites with Construct entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "lowlevel.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "<init>/0" { companion = "lowlevel.X", factory = "apply" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("<init>", 0)), CollectionsTransform.RetargetRewrite.Construct("lowlevel.X", "apply"))
  }

  test("retargetRewrites with Construct entries parse dropTrailing and fillTypeArgs") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "lowlevel.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "<init>/4" { companion = "lowlevel.X", factory = "apply", dropTrailing = 2, fillTypeArgs = true }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(
      tbl(("<init>", 4)),
      CollectionsTransform.RetargetRewrite.Construct("lowlevel.X", "apply", dropTrailing = 2, fillTypeArgs = true)
    )
  }

  test("retargetRewrites with ForEach entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "entries/0" { forEach = "foreachEntry", arity = 2 }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("entries", 0)), CollectionsTransform.RetargetRewrite.ForEach("foreachEntry", 2))
  }

  test("retargetRewrites with ForEach defaults arity to 1") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "keys/0" { forEach = "foreachKey" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("keys", 0)), CollectionsTransform.RetargetRewrite.ForEach("foreachKey", 1))
  }

  test("retargetRewrites with Collect entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "keys/0" { collect = "foreachKey", into = "lowlevel.util.DynamicArray" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("keys", 0)), CollectionsTransform.RetargetRewrite.Collect("foreachKey", "lowlevel.util.DynamicArray"))
  }

  test("retargetRewrites with Collect defaults into to DynamicArray") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "values/0" { collect = "foreachValue" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("values", 0)), CollectionsTransform.RetargetRewrite.Collect("foreachValue", "lowlevel.util.DynamicArray"))
  }

  test("retargetRewrites with Chain entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "iterator/0" { chain = ["orderedItems", "iterator"], parens = ["orderedItems"] }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(
      tbl(("iterator", 0)),
      CollectionsTransform.RetargetRewrite.Chain(List("orderedItems", "iterator"), parens = Set("orderedItems"))
    )
  }

  test("retargetRewrites with Chain and dropArgs are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "toArray/1" { chain = ["toArray"], dropArgs = true }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("toArray", 1)), CollectionsTransform.RetargetRewrite.Chain(List("toArray"), dropArgs = true))
  }

  test("retargetRewrites with FieldWrite entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "size/0" { fieldWrite = "truncate" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("size", 0)), CollectionsTransform.RetargetRewrite.FieldWrite("size", "truncate"))
  }

  test("retargetRewrites with IndexedField entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "items/0" { indexedField = "items" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("items", 0)), CollectionsTransform.RetargetRewrite.IndexedField("items"))
  }

  test("retargetRewrites with Template entries are parsed from config") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "incr/2" { template = "{ val i = $0; $recv(i) = $recv(i) + $1 }" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(tbl(("incr", 2)), CollectionsTransform.RetargetRewrite.Template("{ val i = $0; $recv(i) = $recv(i) + $1 }"))
  }

  test("retargetRewrites with descriptor key are parsed into retargetRewritesByDesc") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "<init>/(int)" = "apply"
        |    "<init>/(Array)" { companion = "scala.X", factory = "from" }
        |  } }
        |} ] }""".stripMargin
    )
    val ct = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    assert(ct.retargetRewrites.get("com.demo.Widget").forall(_.isEmpty))
    val tbl = ct.retargetRewritesByDesc("com.demo.Widget")
    assertEquals(tbl(("<init>", Descriptor(List(Param.Prim("int"))))), CollectionsTransform.RetargetRewrite.Rename("apply"))
    assertEquals(
      tbl(("<init>", Descriptor(List(Param.Named("Array"))))),
      CollectionsTransform.RetargetRewrite.Construct("scala.X", "from")
    )
  }

  test("retargetRewrites mixes arity and descriptor keys for the same source") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "<init>/0" = "apply"
        |    "<init>/(int)" = "apply"
        |  } }
        |} ] }""".stripMargin
    )
    val ct       = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val arityTbl = ct.retargetRewrites("com.demo.Widget")
    assertEquals(arityTbl(("<init>", 0)), CollectionsTransform.RetargetRewrite.Rename("apply"))
    val descTbl = ct.retargetRewritesByDesc("com.demo.Widget")
    assertEquals(descTbl(("<init>", Descriptor(List(Param.Prim("int"))))), CollectionsTransform.RetargetRewrite.Rename("apply"))
  }

  test("retargetRewrites with multi-param descriptor key") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |  retargetRewrites { "com.demo.Widget" {
        |    "<init>/(boolean,int)" = "apply"
        |  } }
        |} ] }""".stripMargin
    )
    val ct  = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    val tbl = ct.retargetRewritesByDesc("com.demo.Widget")
    assertEquals(
      tbl(("<init>", Descriptor(List(Param.Prim("boolean"), Param.Prim("int"))))),
      CollectionsTransform.RetargetRewrite.Rename("apply")
    )
  }

  test("empty retargetRewrites is the default when not specified") {
    val conf = Minimal.replace(
      """manifest { name = "demo" }""",
      """manifest { name = "demo", surface = [ { transform = "collections",
        |  retarget { "com.demo.Widget" = "scala.X" }
        |} ] }""".stripMargin
    )
    val ct = PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collectFirst { case c: CollectionsTransform => c }.get
    assert(ct.retargetRewrites.isEmpty)
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
        |""".stripMargin,
      Map("base.conf" -> base)
    )

    val m      = PortConfig.load(f).manifest.get
    val byHand = PortManifest(
      name = "base",
      dropTypes = Set("com.demo.Gone"),
      packageRenames = Map("com.demo" -> "port.demo"),
      surface = List(new CollectionsTransform),
      inject = List(Path.of("java"))
    ).extendedBy(PortManifest(name = "dep", surface = List(new TestFrameworkTransform())))

    assertEquals(m.name, "dep")
    assertEquals(m.baseChain.map(_.name), byHand.baseChain.map(_.name))
    assertEquals(m.effectiveDropTypes, byHand.effectiveDropTypes)
    assertEquals(m.effectivePackageRenames, byHand.effectivePackageRenames)
    // base phases first, then this module's own — the order `effectiveSurface` guarantees
    assertEquals(fingerprints(m), fingerprints(byHand))
    // `inject` is not inherited: exactly one module ships each replacement file, so a
    // dependent that inherited it would define the same FQN twice.
    assertEquals(m.inject, Nil)
    assertEquals(m.substitutions.inject, Nil)
  }

  test("`providedSources` resolves like `inject`, reaches the substitutions, and is not inherited") {
    val base =
      """label = "base"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "base", dropTypes = ["com.demo.Gone"], providedSources = ["hand/src"] }
        |""".stripMargin
    val f = fixture(
      """label = "dependent"
        |base  = "base.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "test" }
        |manifest { name = "dep", providedSources = ["dep/src"] }
        |""".stripMargin,
      Map("base.conf" -> base)
    )
    val m   = PortConfig.load(f).manifest.get
    val own = List(f.getParent.resolve("dep/src").normalize)
    assertEquals(m.providedSources, own)
    assertEquals(m.substitutions.providedSources, own)
    assertEquals(m.ownDrops.providedSources, own)
    assertEquals(m.baseChain.map(_.providedSources), List(List(f.getParent.resolve("hand/src").normalize)))
    // absent is the no-op
    assertEquals(PortManifest("x").providedSources, Nil)
  }

  // platform rows: ready-made Scala exactly one row compiles, for a replacement whose ANSWER
  // differs per platform

  test("`platformDirs` resolves each row's paths like every other path, and only that row's") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest {
        |  name = "demo"
        |  inject = ["java"]
        |  platformDirs { jvm = ["rows/jvm"], js = ["rows/js", "rows/shared-js"], native = ["rows/native"] }
        |}
        |""".stripMargin
    )
    val m = PortConfig.load(f).manifest.get
    assertEquals(m.platformDirs.keySet, Set("jvm", "js", "native"))
    assertEquals(m.platformDirs("jvm"), List(f.getParent.resolve("rows/jvm").normalize))
    assertEquals(m.platformDirs("js"), List(f.getParent.resolve("rows/js").normalize, f.getParent.resolve("rows/shared-js").normalize))
    // the shared row is `inject` and is untouched by any of this
    assertEquals(m.inject, List(f.getParent.resolve("java").normalize))
  }

  test("a `platformDirs` row nobody compiles is refused by name, with the rows that exist") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo", platformDirs { scalajvm = ["rows/jvm"] } }
        |""".stripMargin
    )
    val e = intercept[balticporter.tir.ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("scalajvm"))
    assert(clue(e.getMessage).contains("js, jvm, native"))
  }

  test("an absent or empty `platformDirs` is the no-op empty map") {
    val absent = PortConfig
      .load(
        fixture(
          """label = "demo"
            |input  { sourceRoot = "java" }
            |output { portRoot = "out", sourceSet = "main" }
            |manifest { name = "demo" }
            |""".stripMargin
        )
      )
      .manifest
      .get
    assertEquals(absent.platformDirs, Map.empty[String, List[Path]])
    val empty = PortConfig
      .load(
        fixture(
          """label = "demo"
            |input  { sourceRoot = "java" }
            |output { portRoot = "out", sourceSet = "main" }
            |manifest { name = "demo", platformDirs {} }
            |""".stripMargin
        )
      )
      .manifest
      .get
    assertEquals(empty.platformDirs, Map.empty[String, List[Path]])
  }

  test("`platformDirs` is NOT inherited — a row's file is shipped by exactly one module") {
    val base =
      """label = "base"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "base", platformDirs { jvm = ["rows/jvm"] } }
        |""".stripMargin
    val f = fixture(
      """label = "dependent"
        |base  = "base.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "dep" }
        |""".stripMargin,
      Map("base.conf" -> base)
    )
    assertEquals(PortConfig.load(f).manifest.get.platformDirs, Map.empty[String, List[Path]])
  }

  test("`rowSources` reads each row's upstream trees, root conf-relative and files as written") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest {
        |  name = "demo"
        |  rowSources { js = [ { root = "emu", files = ["com/demo/Clock.java", "com/demo/util/*.java"] }, { root = "emu2" } ] }
        |}
        |""".stripMargin
    )
    val m = PortConfig.load(f).manifest.get
    assertEquals(
      m.rowSources,
      Map(
        "js" -> List(
          balticporter.core.RowSource(f.getParent.resolve("emu").normalize, List("com/demo/Clock.java", "com/demo/util/*.java")),
          balticporter.core.RowSource(f.getParent.resolve("emu2").normalize, Nil)
        )
      )
    )
  }

  test("a `rowSources` row nobody compiles is refused by name at load") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo", rowSources { browser = [ { root = "emu" } ] } }
        |""".stripMargin
    )
    val e = intercept[balticporter.tir.ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("browser"))
    assert(clue(e.getMessage).contains("js, jvm, native"))
  }

  test("`rowSources` is absent by default and NOT inherited — it is this module's build") {
    val base =
      """label = "base"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "base", rowSources { js = [ { root = "emu" } ] } }
        |""".stripMargin
    val f = fixture(
      """label = "dependent"
        |base  = "base.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "dep" }
        |""".stripMargin,
      Map("base.conf" -> base)
    )
    assertEquals(PortConfig.load(f).manifest.get.rowSources, Map.empty[String, List[balticporter.core.RowSource]])
  }

  test("a base cycle is refused by name") {
    val f = fixture(
      """label = "a"
        |base  = "b.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "a" }
        |""".stripMargin,
      Map(
        "b.conf" ->
          """base = "b.conf"
            |manifest { name = "b" }
            |""".stripMargin
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("base chain"))
  }

  test(
    "…and through a SYMLINK too — comparing paths through toRealPath, where the failure is a CRASH and not a wrong number"
  ) {
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
    Files.writeString(
      root.resolve("b.conf"),
      """base = "via/b.conf"
        |manifest { name = "b" }
        |""".stripMargin
    )
    Files.writeString(
      root.resolve("port.conf"),
      """label = "a"
        |base  = "b.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "a" }
        |""".stripMargin
    )
    val e = intercept[ConfigError](PortConfig.load(root.resolve("port.conf")))
    assert(clue(e.getMessage).contains("base chain"))
  }

  test("a base contributes its MANIFEST and nothing else — its build halves are not junk") {
    // The base conf below carries a full `input`/`output`/`provenance`; none of it is this
    // dependent's build, and reporting it as an unread key would make every real two-module
    // port unloadable.
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
        |""".stripMargin,
      Map("base.conf" -> base)
    )
    assertEquals(PortConfig.load(f).manifest.get.baseChain.map(_.name), List("base"))
  }

  test("`baseReports` is the PORT's, resolved against the conf and reaching BOTH readers") {
    // Which base maps a run discovers decides EMITTED TEXT (the constructor plan, the class-vs-object
    // question, renamed field names, the `export` lists), so it belongs to the port and not to an
    // operator's `debug.properties` — an input that shapes output must come from the port.
    val prev = Option(System.getProperty("balticporter.baseReports"))
    try
      System.clearProperty("balticporter.baseReports")
      val base =
        """label = "base"
          |input  { sourceRoot = "java" }
          |output { portRoot = "out", sourceSet = "main" }
          |baseReports = ["not-this-one"]
          |manifest { name = "base" }
          |""".stripMargin
      val f = fixture(
        """label = "dep"
          |base  = "base.conf"
          |baseReports = ["published", "elsewhere"]
          |input  { sourceRoot = "java" }
          |output { portRoot = "out", sourceSet = "test" }
          |manifest { name = "dep" }
          |""".stripMargin,
        Map("base.conf" -> base)
      )
      val m = PortConfig.load(f).manifest.get
      // resolved against THE CONF FILE, like every other path a conf holds
      assertEquals(m.baseReports.map(_.getFileName.toString), List("published", "elsewhere"))
      assert(m.baseReports.forall(_.isAbsolute), m.baseReports.toString)
      // …and NOT inherited: a base's own `baseReports` says where ITS bases published, which is a
      // fact about that module's build and none of this run's business.
      assertEquals(m.baseChain.map(_.baseReports), List(Nil))
      // …and ANCHORED, so a `{ transform = "port-map-migration" }` entry — which loads its maps at
      // CONSTRUCTION time, through a factory that takes nothing but its own view — reads the same
      // value `PortRun` will. One value, both readers.
      assertEquals(balticporter.tir.DebugFlags.baseReports.map(_.getFileName.toString), List("published", "elsewhere"))
    finally
      prev match
        case Some(v) => System.setProperty("balticporter.baseReports", v)
        case None    => System.clearProperty("balticporter.baseReports")
  }

  // -------------------------------------------------------------------------------------------
  test("`type-redirect` reads BOTH entry shapes out of one map, and the flat one is unchanged") {
    // The flat form is published — every port that writes it must keep working — so the entry that
    // grew `memberRenames` spells itself as an object BESIDE it, in the same map. The identity
    // compared is `surfaceFingerprint`, because that is what decides whether two modules agree
    // about the emitted surface, and because an entry with no renames must still render exactly
    // what it always did or every base/dependent pair predating this feature disagrees.
    def fp(entries: String) = PortConfig
      .load(
        fixture(
          Minimal.replace(
            """manifest { name = "demo" }""",
            s"""manifest { name = "demo", surface = [ { transform = "type-redirect", redirects { $entries } } ] }"""
          )
        )
      )
      .manifest
      .get
      .effectiveSurface
      .collectFirst { case t: TypeRedirectTransform => t.surfaceFingerprint }
      .get

    assertEquals(fp(""""a.B" = "c.D""""), "a.B->c.D")
    assertEquals(fp("""  "a.B" = { to = "c.D" }  """), "a.B->c.D")
    assertEquals(
      fp(
        """  "a.B" = "c.D"
          |  "a.Disposable" = { to = "java.lang.AutoCloseable"
          |                     memberRenames { dispose = "close" } }  """.stripMargin
      ),
      "a.B->c.D,a.Disposable->java.lang.AutoCloseable[dispose=close]"
    )
  }

  test("`bean-properties` reads BOTH entry shapes out of one map, and the TARGET is in the fingerprint") {
    // The same compatible extension as `type-redirect`'s, one phase over — and the assertion that
    // matters is the LAST one: two entries that name the same accessors and ask for different
    // SHAPES must not compare equal, or `SurfaceMissing` cannot see the difference and a same-name
    // pair can be neither compared nor composed.
    def fp(entries: String) = PortConfig
      .load(
        fixture(
          Minimal.replace(
            """manifest { name = "demo" }""",
            s"""manifest { name = "demo", surface = [ { transform = "bean-properties", pairs { $entries } } ] }"""
          )
        )
      )
      .manifest
      .get
      .effectiveSurface
      .collectFirst { case t: BeanPropertyTransform => t.surfaceFingerprint }
      .get

    assertEquals(fp(""""a.B#x" = "getX/setX""""), "a.B#x=getX/setX>def-pair")
    assertEquals(fp("""  "a.B#x" = { accessors = "getX/setX" }  """), "a.B#x=getX/setX>def-pair")
    assertEquals(fp("""  "a.B#x" = { accessors = "getX/setX", target = "var" }  """), "a.B#x=getX/setX>var")
    assertNotEquals(fp(""""a.B#x" = "getX/setX""""), fp("""  "a.B#x" = { accessors = "getX/setX", target = "var" }  """))
  }

  test("…and a `target` outside the closed set names every spelling rather than defaulting") {
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "bean-properties",
          |  pairs { "a.B#x" = { accessors = "getX", target = "lazy-val" } } } ] }""".stripMargin
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("def-pair"))
    assert(clue(e.getMessage).contains("val"))
  }

  test("a misspelt key INSIDE a bean-properties entry fails the run — the shape probe is not a read") {
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "bean-properties",
          |  pairs { "a.B#x" = { accessor = "getX" } } } ] }""".stripMargin
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("accessor"))
  }

  test("`base` composes a phase's POLICY through the SAME fold — no second truth on the conf path") {
    // The hole was identical on both paths, so the fix has to be: `base = "…"` IS `extendedBy`,
    // and the merge lives on the manifest rather than in the run.
    // Nothing was added to `PortConfig` for this test to pass, which is the point of it.
    val base =
      """label = "base"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest {
        |  name = "base"
        |  governs = ["com.demo"]
        |  dropTypes = ["com.demo.Gone"]
        |  surface = [ { transform = "type-redirect", redirects { "com.demo.Gone" = "port.Kept" } } ]
        |}
        |""".stripMargin
    val f = fixture(
      """label = "dependent"
        |base  = "base.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "test" }
        |manifest { name = "dep"
        |  surface = [ { transform = "type-redirect", redirects { "other.Legacy" = "dep.Own" } } ] }
        |""".stripMargin,
      Map("base.conf" -> base)
    )

    val m = PortConfig.load(f).manifest.get
    assertEquals(m.effectiveSurface.map(_.name), List("type-redirect"))
    assertEquals(
      m.effectiveSurface.collectFirst { case t: TypeRedirectTransform => t.surfaceFingerprint }.get,
      "com.demo.Gone->port.Kept,other.Legacy->dep.Own"
    )
    assertEquals(m.surfaceFold.refusals, Nil)
    assertEquals(m.surfaceFold.ownKeys, Map("type-redirect" -> Set("other.Legacy")))
  }

  test("…and two confs that DISAGREE about one key are refused there too") {
    val base =
      """label = "base"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "base", governs = [ "a" ]
        |  surface = [ { transform = "type-redirect", redirects { "a.B" = "c.D" } } ] }
        |""".stripMargin
    val f = fixture(
      """label = "dependent"
        |base  = "base.conf"
        |input  { sourceRoot = "java" }
        |output { portRoot = "out", sourceSet = "test" }
        |manifest { name = "dep"
        |  surface = [ { transform = "type-redirect", redirects { "a.B" = "c.OTHER" } } ] }
        |""".stripMargin,
      Map("base.conf" -> base)
    )

    val m = PortConfig.load(f).manifest.get
    assertEquals(m.effectiveSurface.size, 2, "a refused merge leaves the pre-merge pipeline")
    val fs = ManifestAgreement.check(Some(m), Nil, foreignRoots = true)
    assertEquals(fs.map(_.kind), List(ManifestAgreement.Kind.SurfaceDivergence))
    assert(clue(fs.head.detail).contains("c.OTHER"))
  }

  test("a misspelt key INSIDE a redirect entry fails the run — the shape probe is not a read") {
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "type-redirect",
          |  redirects { "a.B" = { to = "c.D", memberRename { x = "y" } } } } ] }""".stripMargin
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("memberRename"))
  }

  // -------------------------------------------------------------------------------------------
  // refusals — every one of these is silent under plain HOCON
  // -------------------------------------------------------------------------------------------

  test("an unknown transform names every factory the classpath actually offers") {
    val f = fixture(
      Minimal.replace("""manifest { name = "demo" }""", """manifest { name = "demo", surface = [ { transform = "collectionz" } ] }""")
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("unknown transform 'collectionz'"))
    assert(clue(e.getMessage).contains("collections"))
    assert(clue(e.getMessage).contains("META-INF/services"))
  }

  test("`package-rename` as a surface entry is refused BY NAME, pointing at the manifest field") {
    // Not "unknown transform": a port told that would reasonably conclude the feature is missing.
    // It is not missing — it is manifest DATA, because it must run after every other phase and
    // `runsAfter` cannot say "after everything".
    val f = fixture(
      Minimal.replace("""manifest { name = "demo" }""", """manifest { name = "demo", surface = [ { transform = "package-rename" } ] }""")
    )
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
        |""".stripMargin
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("input.resolutionRootz"))
    assert(clue(e.getMessage).contains("manifest.dropType"))
  }

  test("a key nobody read fails INSIDE a surface entry too") {
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "test-framework", suit = "munit.FunSuite" } ] }"""
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("surface[0].suit"))
  }

  test("a value of the wrong SHAPE is an error, never a quiet widening") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java", files = "com/demo/Widget.java" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo" }
        |""".stripMargin
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("expected a list of strings"))
  }

  test("`hints` is a list of FQNs — a plain string is a shape error (O4 CLOSED)") {
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [
          |  { transform = "primitive-to-opaque", fqn = "port.Handle", hints = "not-a-list" } ] }""".stripMargin
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("expected a list"))
  }

  test("`primitive-to-opaque` reads `target = own-class` and its coercion names; absent, the target is the mint") {
    def spec(entry: String) =
      val conf = Minimal.replace("""manifest { name = "demo" }""", s"""manifest { name = "demo", surface = [ $entry ] }""")
      PortConfig.load(fixture(conf)).manifest.get.effectiveSurface.collect { case p: balticporter.transform.PrimitiveToOpaqueTransform => p.spec.target }
    assertEquals(
      spec("""{ transform = "primitive-to-opaque", fqn = "com.demo.Gadget", target = "own-class", unwrapName = "toInt" }"""),
      List(balticporter.tir.OpaqueSpec.Target.OwnClass("apply", "toInt"))
    )
    assertEquals(spec("""{ transform = "primitive-to-opaque", fqn = "port.Handle" }"""), List(balticporter.tir.OpaqueSpec.Target.Mint))
  }

  test("a scope declaring both directions is refused") {
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [
          |  { transform = "collections", scope { except = ["a"], only = ["b"] } } ] }""".stripMargin
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.getMessage).contains("never both"))
  }

  test("`files` and `includeGlobs` together have no honest reading") {
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java", files = ["com/demo/Widget.java"], includeGlobs = ["**.java"] }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo" }
        |""".stripMargin
    )
    intercept[ConfigError](PortConfig.load(f))
  }

  test("a declared classpathFile that is not there is FATAL, never an empty classpath") {
    // A missing input must fail loudly, and it bites harder here: an unresolved `org.junit.Assert`
    // import does not fail the frontend, it resolves WRONGLY.
    val f = fixture(
      """label = "demo"
        |input  { sourceRoot = "java", classpathFile = "nope.txt" }
        |output { portRoot = "out", sourceSet = "main" }
        |manifest { name = "demo" }
        |""".stripMargin
    )
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
    // …and a stranger's, registered exactly the way a porting repository registers a library-specific rule.
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
    val urls     = getClass.getClassLoader.getResources("META-INF/services/balticporter.tir.TransformFactory")
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
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", surface = [ { transform = "spec-echo", tag = "hello" } ] }"""
      )
    )
    assertEquals(PortConfig.load(f).manifest.get.effectiveSurface.map(_.name), List("spec-echo(hello)"))
  }

  // -------------------------------------------------------------------------------------------
  // per-location REMEDY SELECTION (`resolutions`)
  // -------------------------------------------------------------------------------------------

  test("`resolutions` is read as data and reaches the manifest") {
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest {
          |  name    = "demo"
          |  surface = [ { transform = "spec-echo", tag = "hi" } ]
          |  resolutions { "com.demo.Widget#labels" = "spec-echo-remedy" }
          |}""".stripMargin
      )
    )
    assertEquals(PortConfig.load(f).manifest.get.resolutions, Map("com.demo.Widget#labels" -> "spec-echo-remedy"))
  }

  test("an UNKNOWN remedy id is refused at LOAD, with the alternatives listed") {
    // The loud door. A value silently ignored here is a port that selected a remedy and got none,
    // which reads exactly like a port that never asked — the silent no-op this whole front door
    // exists to prevent.
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", resolutions { "com.demo.Widget#labels" = "no-such-remedy" } }"""
      )
    )
    val e = intercept[ConfigError](PortConfig.load(f))
    assert(clue(e.why).contains("no-such-remedy"))
    assert(clue(e.why).contains("spec-echo-remedy"))
  }

  test("…but a KNOWN id whose phase this port did not enable is NOT a load error") {
    // The distinction only a factory-side declaration can make: the id exists on this classpath, so
    // the mistake is a missing `surface` entry and not a typo. It is reported at RUN time as a
    // policy finding naming the phase, and refusing it here would send the reader hunting for a
    // spelling mistake in a correct id.
    val f = fixture(
      Minimal.replace(
        """manifest { name = "demo" }""",
        """manifest { name = "demo", resolutions { "com.demo.Widget#labels" = "spec-echo-remedy" } }"""
      )
    )
    assertEquals(PortConfig.load(f).manifest.get.resolutions, Map("com.demo.Widget#labels" -> "spec-echo-remedy"))
  }

  test("a factory DECLARES its phase's menu, so the registry knows it without building anything") {
    val r = TransformRegistry.of(new SpecEchoFactory)
    assertEquals(r.remedies.ids, List("spec-echo-remedy"))
    assertEquals(r.remedies.get("spec-echo-remedy").map(_.lane), Some("spec-echo-lane"))
  }

  test("`class-table`'s `tables` reach the phase, and no `tables` is the table the port supplies") {
    def tablesOf(surface: String) =
      PortConfig
        .load(fixture(Minimal.replace("""manifest { name = "demo" }""", s"""manifest { name = "demo", surface = [ $surface ] }""")))
        .manifest
        .get
        .effectiveSurface
        .collectFirst { case c: balticporter.transform.ClassTableTransform => c.tables }
        .get
    assertEquals(tablesOf("""{ transform = "class-table", redirects { "com.demo.R#forName" = "com.demo.T#classFor" } }"""), Nil)
    assertEquals(
      tablesOf(
        """{ transform = "class-table", redirects { "com.demo.R#forName" = "com.demo.T#classFor" },
          |  tables = [ { object = "com.demo.T", seeds = ["com.demo.Widget"], construct = "make", missing { throw = "port.Oops", notFound = "nf: " } } ] }""".stripMargin
      ),
      List(
        balticporter.transform.ClassTableTransform.Table(
          "com.demo.T",
          List("com.demo.Widget"),
          "classFor",
          Some("make"),
          Some(balticporter.transform.ClassTableTransform.Missing("port.Oops", "nf: ", ""))
        )
      )
    )
  }
