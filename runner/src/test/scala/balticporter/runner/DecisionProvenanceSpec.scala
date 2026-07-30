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
