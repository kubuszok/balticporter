package balticporter.runner

import balticporter.core.*
import balticporter.tir.*

import java.nio.file.{Files, Path}

/** `decisions.tsv` end to end — the channel that answers "HOW did the porter arrive at this code?"
  * for an agent in another repository (CLAUDE.md §4.45).
  *
  * The properties pinned here are the ones that make the artifact usable rather than merely
  * present: every row carries the §1 classification and, for a `Configured` one, the MANIFEST KEY
  * verbatim — the string an agent edits to change the outcome; a run that decided nothing still
  * writes a header, because "no policy" and "the run never got there" are different facts; and two
  * identical runs produce identical bytes, since a provenance artifact nobody can diff is a log.
  */
class DecisionProvenanceSpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private def fixture(): (Path, Path) =
    val root = Files.createTempDirectory("decisions-run")
    val src  = root.resolve("java")
    java(src, "com/demo/Widget.java",
      """package com.demo;
        |public class Widget {
        |  public int size;
        |  public String label() { return "w" + size; }
        |}""".stripMargin)
    java(src, "com/demo/Gadget.java",
      """package com.demo;
        |public class Gadget {
        |  public Widget w = new Widget();
        |}""".stripMargin)
    (root, src)

  /** the artifact layer, into a directory of this test's own — `PortRunSpec.withReport`, which is
    * also what keeps a suite from publishing artifacts into the repository. */
  private def withReport[A](dir: Path)(f: => A): A =
    val keys  = List("balticporter.report" -> "on", "balticporter.reportDir" -> dir.toString)
    val saved = keys.map((k, _) => k -> Option(System.getProperty(k)))
    keys.foreach((k, v) => System.setProperty(k, v))
    try f
    finally saved.foreach {
      case (k, Some(v))    => System.setProperty(k, v)
      case (k, scala.None) => System.clearProperty(k)
    }

  private def run(root: Path, src: Path, files: List[String] = Nil)(f: PortRun => PortRun = identity): PortResult =
    val fs = if files.nonEmpty then files else List("com/demo/Widget.java", "com/demo/Gadget.java")
    f(PortRun(
      label     = "demo",
      portRoot  = root.resolve("port"),
      sourceSet = SourceSet.Main,
      frontend  = FrontendConfig(src, fs, Nil),
      phases    = Nil,
    )).execute()

  private def decisions(rep: Path): List[Decision] =
    Decision.parseAll(rep.resolve("run-latest/decisions.tsv"))

  private def widgetReplacement(root: Path, pkg: String): Path =
    val inject = root.resolve("overrides")
    java(inject, "com/demo/Widget.scala", s"package $pkg\nclass Widget { def label(): String = \"w\" }")
    inject

  /** A wrapper whose statics are what the redirect phases are configured against, plus one class
    * that calls each of them from a DIFFERENT method — so "one row per declaration" is a claim the
    * fixture can actually distinguish from "one row per site". */
  private def redirectFixture(): (Path, Path, List[String]) =
    val (root, src) = fixture()
    java(src, "com/demo/Reflect.java",
      """package com.demo;
        |public class Reflect {
        |  public static Class<?> forName(String n) { return null; }
        |  public static String nameOf(Class<?> c) { return null; }
        |}""".stripMargin)
    java(src, "com/demo/Uses.java",
      """package com.demo;
        |public class Uses {
        |  public Class<?> lookUp(String n) { return Reflect.forName(n); }
        |  public String describe(Class<?> c) { return Reflect.nameOf(c) + Reflect.nameOf(c); }
        |}""".stripMargin)
    (root, src, List("com/demo/Widget.java", "com/demo/Gadget.java",
                     "com/demo/Reflect.java", "com/demo/Uses.java"))

  // -------------------------------------------------------------------------

  test("a drop, an injection and a rename each leave a row naming the policy entry that produced it") {
    val (root, src) = fixture()
    val rep    = root.resolve("report")
    val inject = widgetReplacement(root, "sge")
    withReport(rep) {
      run(root, src)(_.copy(
        subs           = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject)),
        packageRenames = Map("com.demo" -> "sge")))
    }
    val ds = decisions(rep)
    // every decision is classified — that is the mandatory half of the record
    assert(ds.nonEmpty)
    assert(ds.forall(_.reason.className == "configured"), clue(ds.map(_.reason.className).distinct))

    val drop = ds.filter(_.kind == Decision.Kind.DroppedType)
    assertEquals(drop.map(_.subjectFqn), List("com.demo.Widget"))
    // the key is the manifest entry VERBATIM: the string to delete to undo this decision
    assertEquals(drop.head.reason, Reason.Configured("substitutions", "com.demo.Widget"))
    assertEquals(drop.head.detail("fired"), "yes")
    assertEquals(drop.head.detail("own"), "yes")
    // …and BOTH namespaces, because policy is upstream and the rename runs last (§4.56)
    assertEquals(drop.head.detail("emitted"), "sge.Widget")
    // the subject is anchored on the Java file it was decided about
    assert(clue(drop.head.origin.javaPath).endsWith("com/demo/Widget.java"))

    val inj = ds.filter(_.kind == Decision.Kind.InjectedMember)
    // the FQN of an injected file is derived from its PATH under the injection root — the same
    // rule the port map uses, and the reason an injected file must sit where its FQN says.
    assertEquals(inj.map(_.subjectFqn), List("com.demo.Widget"))
    assertEquals(inj.head.detail("file"), "com/demo/Widget.scala")
    assertEquals(inj.head.reason, Reason.Configured("substitutions", "inject"))

    val renamed = ds.filter(_.kind == Decision.Kind.RenamedPackage)
    assertEquals(renamed.map(_.subjectFqn).sorted, List("com.demo.Gadget", "com.demo.Widget"))
    assertEquals(renamed.map(_.detail("to")).sorted, List("sge.Gadget", "sge.Widget"))
    assertEquals(renamed.head.reason, Reason.Configured("package-rename", "com.demo -> sge"))
    // a rename is recorded per UNIT, not per symbol: a member moved because its type did
    assertEquals(renamed.size, 2)
  }

  test("a declared key that never fired is still recorded — and says so") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src)(_.copy(subs = Substitutions(dropMethods = Set("com.demo.Widget#nope", "com.demo.Widget#label"))))
    }
    val ms = decisions(rep).filter(_.kind == Decision.Kind.DroppedMember).sortBy(_.subjectFqn)
    assertEquals(ms.map(_.subjectFqn), List("com.demo.Widget#label", "com.demo.Widget#nope"))
    assertEquals(ms.map(_.detail("fired")), List("yes", "no"))
    // a drop is anchored on its OWNER's Java file, which is what makes it navigable
    assert(ms.forall(_.origin.javaPath.endsWith("com/demo/Widget.java")), clue(ms.map(_.origin.javaPath)))
  }

  test("a NESTED type's drop is anchored on the file it lives in, not on `<synthetic>`") {
    // A nested type is not a compilation unit and has no origin of its own. Reported as synthetic,
    // the row is unnavigable for the sake of a `$` — and libGDX drops constructors on exactly such
    // types (`ParallelArray$ChannelDescriptor`).
    val (root, src) = fixture()
    java(src, "com/demo/Outer.java",
      """package com.demo;
        |public class Outer {
        |  public static class Inner { public int f() { return 1; } }
        |}""".stripMargin)
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src, files = List("com/demo/Outer.java")) {
        _.copy(subs = Substitutions(dropMethods = Set("com.demo.Outer$Inner#f")))
      }
    }
    val m = decisions(rep).filter(_.kind == Decision.Kind.DroppedMember)
    assertEquals(m.map(_.subjectFqn), List("com.demo.Outer$Inner#f"))
    assertEquals(m.head.detail("fired"), "yes")
    assert(clue(m.head.origin.javaPath).endsWith("com/demo/Outer.java"))
  }

  test("a type nobody dropped has no row — the log records decisions, not the whole program") {
    val (root, src) = fixture()
    val rep    = root.resolve("report")
    val inject = widgetReplacement(root, "com.demo")
    withReport(rep) {
      run(root, src)(_.copy(subs = Substitutions(dropTypes = Set("com.demo.Widget"), inject = List(inject))))
    }
    val ds = decisions(rep)
    assertEquals(ds.filter(_.kind == Decision.Kind.DroppedType).map(_.subjectFqn), List("com.demo.Widget"))
    assert(!ds.exists(_.subjectFqn == "com.demo.Gadget"), clue(ds.map(_.render)))
    // no rename was configured, so the phase is a no-op and records nothing — an empty policy
    // makes a phase silent as well as inert
    assertEquals(ds.count(_.kind == Decision.Kind.RenamedPackage), 0)
  }

  test("an EMPTY manifest writes a header-only artifact, not a missing file") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep)(run(root, src)())
    val p = rep.resolve("run-latest/decisions.tsv")
    assert(Files.isRegularFile(p), "a run that decided nothing must still say so")
    assertEquals(Files.readString(p), Decision.Header + "\n")
  }

  // -------------------------------------------------------------------------
  // the REDIRECT family — a call or a type re-pointed by a (b) policy entry
  // -------------------------------------------------------------------------

  test("a re-pointed call records one row per DECLARATION, naming the entry that fired") {
    val (root, src, files) = redirectFixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src, files)(_.copy(phases = List(
        new balticporter.transform.ClassTableTransform(Map(
          "com.demo.Reflect#forName" -> "com.demo.Table#classFor")),
        new balticporter.transform.StaticForwarderTransform(List(
          balticporter.transform.StaticForwarderTransform.Forwarder(
            wrapper = "com.demo.Reflect", receiver = "java.lang.Class", members = Set("nameOf")))),
      )))
    }
    val rs = decisions(rep).filter(_.kind == Decision.Kind.RedirectedCall)

    val table = rs.filter(_.reason == Reason.Configured("class-table",
      "com.demo.Reflect#forName -> com.demo.Table#classFor"))
    assertEquals(clue(table).size, 1)
    assert(clue(table.head.subjectFqn).startsWith("com.demo.Uses#lookUp"))
    assertEquals(table.head.detail("key"), "com.demo.Reflect#forName")
    assertEquals(table.head.detail("to"), "com.demo.Table#classFor")
    assert(clue(table.head.origin.javaPath).endsWith("com/demo/Uses.java"))

    // TWO calls in ONE method, and therefore ONE row. That is the property the channel is built
    // on: a site-level rewrite is visible in the emitted diff already, and restating it per
    // occurrence buries every decision that is not a redirect.
    val fwd = rs.filter(_.reason.className == "configured").filterNot(table.contains)
    assertEquals(clue(fwd).size, 1)
    assertEquals(fwd.head.reason,
      Reason.Configured("static-forwarder-inline", "com.demo.Reflect#nameOf -> java.lang.Class#nameOf"))
    assert(clue(fwd.head.subjectFqn).startsWith("com.demo.Uses#describe"))
    assertEquals(fwd.head.detail("key"), "com.demo.Reflect")
  }

  test("a program the redirect phases do not touch records nothing — an empty policy is silent") {
    val (root, src, files) = redirectFixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src, files)(_.copy(phases = List(
        // configured against names this program does not have: the mechanism is identical and
        // nothing fires, so nothing is recorded
        new balticporter.transform.ClassTableTransform(Map("com.other.X#y" -> "com.other.T#z")),
        new balticporter.transform.StaticForwarderTransform(Nil),
        new balticporter.transform.TypeRedirectTransform(Map.empty),
      )))
    }
    assertEquals(decisions(rep), Nil)
  }

  test("a re-pointed TYPE records a RetypedSignature — nothing was called") {
    val (root, src, files) = redirectFixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src, files)(_.copy(phases = List(
        new balticporter.transform.TypeRedirectTransform(Map("com.demo.Widget" -> "com.demo.Slab")))))
    }
    val rs = decisions(rep).filter(_.kind == Decision.Kind.RetypedSignature)
    // `Gadget.w` is declared `Widget` — a TYPE occurrence, which no body seam could reach
    assert(clue(rs).exists(_.subjectFqn.startsWith("com.demo.Gadget#w")), clue(rs.map(_.render)))
    assertEquals(rs.head.reason, Reason.Configured("type-redirect", "com.demo.Widget -> com.demo.Slab"))
    assertEquals(rs.head.detail("key"), "com.demo.Widget")
    assert(rs.forall(_.detail("to") == "com.demo.Slab"))
  }

  test("two identical runs of the REDIRECT family produce byte-identical decisions.tsv") {
    val (root, src, files) = redirectFixture()
    def once(rep: Path): String =
      withReport(rep) {
        run(root, src, files)(_.copy(phases = List(
          new balticporter.transform.ClassTableTransform(Map(
            "com.demo.Reflect#forName" -> "com.demo.Table#classFor")),
          new balticporter.transform.StaticForwarderTransform(List(
            balticporter.transform.StaticForwarderTransform.Forwarder(
              wrapper = "com.demo.Reflect", receiver = "java.lang.Class", members = Set("nameOf")))),
          new balticporter.transform.TypeRedirectTransform(Map("com.demo.Widget" -> "com.demo.Slab")),
        )))
      }
      Files.readString(rep.resolve("run-latest/decisions.tsv"))
    assertEquals(once(root.resolve("r1")), once(root.resolve("r2")))
  }

  // -------------------------------------------------------------------------
  // the SUBSTITUTION family — a body replaced, a definition with no Java behind it
  // -------------------------------------------------------------------------

  test("a replaced BODY records the member and the key — nothing else can say the signature lies") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src)(_.copy(phases = List(
        new balticporter.transform.MethodBodyTransform(Map(
          "com.demo.Widget#label" -> """"replaced"""",
          "com.demo.Widget#nope"  -> "()")))))
    }
    val bs = decisions(rep).filter(_.kind == Decision.Kind.SubstitutedBody)
    // one row per member REPLACED — a key that fired nowhere replaced nothing, and `PolicyReport`
    // is what reports it; this channel records acts, not intentions
    assertEquals(bs.map(_.subjectFqn), List("com.demo.Widget#label"))
    assertEquals(bs.head.reason, Reason.Configured("method-body-substitution", "com.demo.Widget#label"))
    assertEquals(bs.head.detail("key"), "com.demo.Widget#label")
    assert(clue(bs.head.origin.javaPath).endsWith("com/demo/Widget.java"))
  }

  test("a VENDORED support type is (a) and a supportSources entry is (b) — the same act, two fixes") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src)(_.copy(
        phases         = List(new balticporter.transform.CollectionsTransform),
        runtimeMode    = RuntimeMode.Vendored,
        supportSources = Map("com.demo.Prop" -> "package com.demo\nobject Prop")))
    }
    val inj = decisions(rep).filter(_.kind == Decision.Kind.InjectedMember)

    val vendored = inj.filter(_.reason == Reason.Universal("runtime-vendoring"))
    assert(clue(vendored).nonEmpty)
    assert(vendored.forall(_.subjectFqn.startsWith("balticporter.runtime.")), clue(vendored.map(_.subjectFqn)))
    assertEquals(vendored.head.detail("mode"), "Vendored")

    val support = inj.filter(_.reason.className == "configured")
    assertEquals(support.map(_.subjectFqn), List("com.demo.Prop"))
    assertEquals(support.head.reason, Reason.Configured("support-sources", "com.demo.Prop"))
    assertEquals(support.head.detail("file"), "com/demo/Prop.scala")
  }

  test("under RuntimeMode.Dependency nothing is vendored, so nothing is recorded as injected") {
    // The honest answer, and the reason this is recorded from what was WRITTEN rather than from
    // what the plan requires: a support type reached through a build dependency is not a
    // definition in this port's output at all.
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src)(_.copy(phases = List(new balticporter.transform.CollectionsTransform)))
    }
    assertEquals(decisions(rep).count(_.kind == Decision.Kind.InjectedMember), 0)
  }

  test("two identical runs of the SUBSTITUTION family produce byte-identical decisions.tsv") {
    val (root, src) = fixture()
    def once(rep: Path): String =
      withReport(rep) {
        run(root, src)(_.copy(
          phases         = List(
            new balticporter.transform.CollectionsTransform,
            new balticporter.transform.MethodBodyTransform(Map("com.demo.Widget#label" -> """"x""""))),
          runtimeMode    = RuntimeMode.Vendored,
          supportSources = Map("com.demo.Prop" -> "package com.demo\nobject Prop")))
      }
      Files.readString(rep.resolve("run-latest/decisions.tsv"))
    assertEquals(once(root.resolve("r1")), once(root.resolve("r2")))
  }

  // -------------------------------------------------------------------------
  // the RETYPE family — a declaration whose emitted SIGNATURE moved
  // -------------------------------------------------------------------------

  /** A class whose members carry a JDK collection in every position a retyping reaches, plus one
    * reassigned parameter — so "one row per declaration, not per parameter" is a claim the fixture
    * can distinguish. */
  private def retypeFixture(): (Path, Path, List[String]) =
    val (root, src) = fixture()
    java(src, "com/demo/Bag.java",
      """package com.demo;
        |import java.util.List;
        |import java.util.ArrayList;
        |public class Bag {
        |  public List<String> items = new ArrayList<String>();
        |  public List<String> pick(List<String> from, int n) { n = n + 1; return from; }
        |  public int plain(int k) { return k; }
        |}""".stripMargin)
    (root, src, List("com/demo/Widget.java", "com/demo/Gadget.java", "com/demo/Bag.java"))

  test("a retyped DECLARATION records once, with both types — and a PARAMETER does not add a row") {
    val (root, src, files) = retypeFixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src, files)(_.copy(phases = List(new balticporter.transform.CollectionsTransform)))
    }
    val rs = decisions(rep).filter(_.reason == Reason.Universal("collections-retype"))
    assert(clue(rs).forall(_.kind == Decision.Kind.RetypedSignature))

    val field = rs.filter(_.subjectFqn == "com.demo.Bag#items")
    assertEquals(clue(field).size, 1)
    assert(clue(field.head.detail("from")).contains("java.util.List"))
    assert(clue(field.head.detail("to")).contains("scala.collection.mutable.Buffer"))

    // `pick` takes a `List` AND returns one, and it is ONE row: a method's `info` is a MethodType
    // carrying its parameter types, so the parameter's own retyping already moved this signature.
    assertEquals(clue(rs.filter(_.subjectFqn == "com.demo.Bag#pick")).size, 1)
    assert(rs.forall(!_.subjectFqn.endsWith("#from")), clue(rs.map(_.subjectFqn)))
    // a member the phase did not touch is not a row
    assert(!rs.exists(_.subjectFqn == "com.demo.Bag#plain"), clue(rs.map(_.subjectFqn)))
  }

  test("a reassigned parameter records once per METHOD, naming the parameters that moved") {
    val (root, src, files) = retypeFixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src, files)(_.copy(phases = List(new balticporter.transform.MutableParamsTransform)))
    }
    val rs = decisions(rep).filter(_.reason == Reason.Universal("reassigned-param-to-var"))
    assertEquals(rs.map(_.subjectFqn), List("com.demo.Bag#pick"))
    assertEquals(rs.head.kind, Decision.Kind.RetypedSignature)
    assertEquals(rs.head.detail("params"), "n")
    assert(clue(rs.head.origin.javaPath).endsWith("com/demo/Bag.java"))
  }

  test("a program with no JDK collection and no reassigned parameter records nothing") {
    val (root, src) = fixture()
    val rep = root.resolve("report")
    withReport(rep) {
      run(root, src)(_.copy(phases = List(
        new balticporter.transform.CollectionsTransform,
        new balticporter.transform.MutableParamsTransform)))
    }
    assertEquals(decisions(rep), Nil)
  }

  test("two identical runs of the RETYPE family produce byte-identical decisions.tsv") {
    val (root, src, files) = retypeFixture()
    def once(rep: Path): String =
      withReport(rep) {
        run(root, src, files)(_.copy(phases = List(
          new balticporter.transform.CollectionsTransform,
          new balticporter.transform.MutableParamsTransform)))
      }
      Files.readString(rep.resolve("run-latest/decisions.tsv"))
    assertEquals(once(root.resolve("r1")), once(root.resolve("r2")))
  }

  // -------------------------------------------------------------------------
  // the CONSTRUCTOR FUNNEL — not a phase; the run records what emission consulted
  // -------------------------------------------------------------------------

  private def ctorFixture(): (Path, Path, List[String]) =
    val (root, src) = fixture()
    java(src, "com/demo/Base.java",
      """package com.demo;
        |public class Base {
        |  public Base(int n, boolean b) { }
        |}""".stripMargin)
    // ONE constructor that becomes the primary: java's own structure, unchanged. No row.
    java(src, "com/demo/Plain.java",
      """package com.demo;
        |public class Plain extends Base {
        |  public Plain(int n) { super(n, true); }
        |}""".stripMargin)
    // SEVERAL roots reaching the SAME parent constructor with different arguments: neither can be
    // the primary, so a primary taking the PARENT's parameters is synthesised.
    java(src, "com/demo/Two.java",
      """package com.demo;
        |public class Two extends Base {
        |  public Two() { super(0, false); }
        |  public Two(int n) { super(n + 1, true); }
        |}""".stripMargin)
    (root, src, List("com/demo/Widget.java", "com/demo/Gadget.java",
                     "com/demo/Base.java", "com/demo/Plain.java", "com/demo/Two.java"))

  test("a funnelled class records its SHAPE and its promoted signature; a trivial one records nothing") {
    val (root, src, files) = ctorFixture()
    val rep = root.resolve("report")
    withReport(rep)(run(root, src, files)())
    val fs = decisions(rep).filter(_.kind == Decision.Kind.FunnelledCtor)

    // `Plain` has one constructor and it became the primary — java's structure survived, so there
    // is no decision to report. A row for it would be noise on every class in a library.
    assert(!fs.exists(_.subjectFqn == "com.demo.Plain"), clue(fs.map(_.render)))

    val two = fs.filter(_.subjectFqn == "com.demo.Two")
    assertEquals(clue(fs).map(_.subjectFqn), List("com.demo.Two"))
    assertEquals(two.head.reason, Reason.Universal("ctor-funnel"))
    assertEquals(two.head.detail("shape"), "synthesised-primary")
    assertEquals(two.head.detail("constructors"), "2")
    // the parameters are the PARENT constructor's, in its order — that is what makes both java
    // constructors expressible as secondaries
    assertEquals(two.head.detail("primary"), "(sup$0: scala.Int, sup$1: scala.Boolean)")
    assert(clue(two.head.origin.javaPath).endsWith("com/demo/Two.java"))
  }

  test("two identical runs record identical funnel rows") {
    val (root, src, files) = ctorFixture()
    def once(rep: Path): String =
      withReport(rep)(run(root, src, files)())
      Files.readString(rep.resolve("run-latest/decisions.tsv"))
    assertEquals(once(root.resolve("r1")), once(root.resolve("r2")))
  }

  // -------------------------------------------------------------------------
  // a DEPENDENT publishes its OWN decisions and no others (ENGINE-LIMITS D2)
  // -------------------------------------------------------------------------

  /** two source trees: `base/` is only RESOLVED against, `dep/` is what the run converts — the
    * structural shape of every dependent port. */
  private def dependentFixture(): (Path, Path, Path) =
    val root = Files.createTempDirectory("decisions-dep")
    val base = root.resolve("base")
    val dep  = root.resolve("dep")
    java(base, "com/base/Holder.java",
      """package com.base;
        |import java.util.List;
        |import java.util.ArrayList;
        |public class Holder {
        |  public List<String> items = new ArrayList<String>();
        |  public List<String> all() { return items; }
        |}""".stripMargin)
    java(dep, "com/dep/Uses.java",
      """package com.dep;
        |import java.util.List;
        |public class Uses {
        |  public List<String> mine = null;
        |  public List<String> read(com.base.Holder h) { return h.all(); }
        |}""".stripMargin)
    (root, base, dep)

  test("a dependent's decisions.tsv holds ITS declarations only — the base's are WITHHELD") {
    val (root, base, dep) = dependentFixture()
    val rep = root.resolve("report")
    withReport(rep) {
      PortRun(
        label     = "dep",
        portRoot  = root.resolve("port"),
        sourceSet = SourceSet.Main,
        frontend  = FrontendConfig(dep, List("com/dep/Uses.java"), Nil, resolutionRoots = List(base)),
        phases    = Nil, // a manifest SUPPLIES the phases; passing both would give the run two policies
        // resolution roots outside this run's own tree ARE a dependent port, and one that declares
        // no base is itself a fatal finding (§1.5) — so the shared surface arrives as a value.
        manifest  = Some(
          PortManifest(
            name           = "base",
            surface        = List(new balticporter.transform.CollectionsTransform),
            packageRenames = Map("com.base" -> "port.base"),
          ).extendedBy(PortManifest(
            name           = "dep",
            packageRenames = Map("com.dep" -> "port.dep"),
          ))),
      ).execute()
    }
    val ds = decisions(rep)
    assert(clue(ds).nonEmpty)

    // The base's `Holder` is in this run's Program — it is parsed, and CollectionsTransform retyped
    // its `items` field and its `all()` return exactly as the base's own run does. Those rows
    // belong to the module that EMITS the declaration; publishing them here would put the base's
    // decisions in a file whose reader is looking for this module's, and this module cannot change
    // one of them. A report a repository cannot act on is not its report.
    assert(!ds.exists(_.subjectFqn.contains("Holder")), clue(ds.map(_.render)))
    assert(!ds.exists(_.subjectFqn.startsWith("com.base")), clue(ds.map(_.subjectFqn)))

    // …and this module's own are all there: the retyped field, the retyped method, the rename.
    assert(ds.exists(d => d.subjectFqn == "com.dep.Uses#mine" && d.kind == Decision.Kind.RetypedSignature))
    assert(ds.exists(d => d.subjectFqn == "com.dep.Uses#read" && d.kind == Decision.Kind.RetypedSignature))
    assertEquals(ds.filter(_.kind == Decision.Kind.RenamedPackage).map(_.subjectFqn), List("com.dep.Uses"))
  }

  test("a BASE port withholds nothing — the filter is scoped, not a blanket") {
    // Same phase, same java, no resolution roots: every unit is this run's own, so nothing is
    // withheld and `Holder`'s rows appear — in the port that emits Holder.
    val (root, base, _) = dependentFixture()
    val rep = root.resolve("report")
    withReport(rep) {
      PortRun(
        label     = "base",
        portRoot  = root.resolve("baseport"),
        sourceSet = SourceSet.Main,
        frontend  = FrontendConfig(base, List("com/base/Holder.java"), Nil),
        phases    = List(new balticporter.transform.CollectionsTransform),
      ).execute()
    }
    val ds = decisions(rep)
    assert(ds.exists(_.subjectFqn == "com.base.Holder#items"), clue(ds.map(_.subjectFqn)))
  }

  test("two identical runs produce byte-identical decisions.tsv") {
    val (root, src) = fixture()
    val inject = widgetReplacement(root, "com.demo")
    def once(rep: Path): String =
      withReport(rep) {
        run(root, src)(_.copy(subs = Substitutions(
          dropTypes   = Set("com.demo.Widget"),
          dropMethods = Set("com.demo.Gadget#nope"),
          inject      = List(inject))))
      }
      Files.readString(rep.resolve("run-latest/decisions.tsv"))
    assertEquals(once(root.resolve("r1")), once(root.resolve("r2")))
  }
