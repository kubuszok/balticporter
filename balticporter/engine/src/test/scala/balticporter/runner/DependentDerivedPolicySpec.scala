package balticporter.runner

import balticporter.core.*
import balticporter.tir.*
import balticporter.transform.NullaryArityTransform

import java.nio.file.{ Files, Path }

/** A dependent with a derived policy of its OWN still binds the rows its base published: the base's spelling of a base member reaches the dependent's calls, and an own row that re-spells a base
  * member is refused and counted.
  */
class DependentDerivedPolicySpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private def withReport[A](dir: Path)(f: => A): A =
    val keys  = List("balticporter.report" -> "on", "balticporter.reportDir" -> dir.toString)
    val saved = keys.map((k, _) => k -> Option(System.getProperty(k)))
    keys.foreach((k, v) => System.setProperty(k, v))
    try f
    finally
      saved.foreach {
        case (k, Some(v))    => System.setProperty(k, v)
        case (k, scala.None) => System.clearProperty(k)
      }

  private val baseRow = DerivedPolicy.Row(DerivedPolicy.Family.Parenless, "com.demo.Holder#count", "def count: Int")
  private val ownRow  = DerivedPolicy.Row(DerivedPolicy.Family.Parenless, "com.demo2.Uses#mine", "def mine: Int")
  private val intrude = DerivedPolicy.Row(DerivedPolicy.Family.Parenless, "com.demo.Holder#other", "def other: Int")

  /** the base's java, the dependent's java, and the base's published report (port map + derived rows) where `PortMap.discover` finds it. */
  private def fixture(): (Path, Path, Path) =
    val root = Files.createTempDirectory("dependent-derived")
    val base = root.resolve("base")
    val dep  = root.resolve("dep")
    java(
      base,
      "com/demo/Holder.java",
      """package com.demo;
        |public class Holder {
        |  private int n;
        |  public int count() { return n; }
        |  public int other() { return n; }
        |}""".stripMargin
    )
    java(
      dep,
      "com/demo2/Uses.java",
      """package com.demo2;
        |public class Uses {
        |  public int read(com.demo.Holder h) { return h.count(); }
        |  public int both(com.demo.Holder h) { return h.other(); }
        |  public int mine() { return 1; }
        |}""".stripMargin
    )
    val published = root.resolve("basemod").resolve("run-latest")
    PortMap.write(
      published,
      PortMap.Map0(
        "basemod",
        EngineInfo.fingerprint,
        List(PortMap.Entry("type", "com.demo.Holder", "com.demo.Holder", PortMap.Disposition.Ported))
      )
    )
    DerivedPolicy.write(published.resolve("derived-policy.tsv"), DerivedPolicy(List(baseRow)))
    (root, base, dep)

  private def run(root: Path, base: Path, dep: Path, own: Option[List[DerivedPolicy.Row]]): (String, List[ManifestAgreement.Finding]) =
    val frozen = own.map { rows =>
      val p = root.resolve("own-derived-policy.tsv")
      DerivedPolicy.write(p, DerivedPolicy(rows))
      p
    }
    val m   = PortManifest("basemod", surface = List(new NullaryArityTransform(derive = true))).extendedBy(PortManifest("dependent", frozenDerivedPolicy = frozen))
    val rep = root.resolve("report")
    val r   = withReport(rep) {
      PortRun(
        "dependent",
        root.resolve("port"),
        SourceSet.Main,
        FrontendConfig(dep, List("com/demo2/Uses.java"), Nil, resolutionRoots = List(base)),
        Nil,
        manifest = Some(m)
      ).execute()
    }
    (Files.readString(r.outDir.resolve("com/demo2/Uses.scala")), r.report.manifest)

  test("with NO policy of its own a dependent binds the base's published rows") {
    val (root, base, dep) = fixture()
    val (uses, _)         = run(root, base, dep, scala.None)
    assert(clue(uses).contains("h.count") && !uses.contains("h.count()"), "the base's parenless spelling did not reach the call")
  }

  test("a dependent's OWN frozen file adds to the base's rows instead of replacing them") {
    val (root, base, dep) = fixture()
    val (uses, found)     = run(root, base, dep, Some(List(ownRow)))
    // the base's row still binds at the dependent's call…
    assert(!clue(uses).contains("h.count()"), "the base's published row was dropped once the dependent froze its own")
    // …and the dependent's own row binds at its own declaration
    assert(uses.contains("def mine: scala.Int"), clue(uses))
    assertEquals(found.filter(_.kind == ManifestAgreement.Kind.DerivedIntrusion), Nil)
  }

  test("an own row that re-spells a BASE member is refused and counted; the base's spelling binds") {
    val (root, base, dep) = fixture()
    val (uses, found)     = run(root, base, dep, Some(List(ownRow, intrude)))
    // `other` keeps the arity the base emitted it with — the dependent's row did not move the call
    assert(clue(uses).contains("h.other()"), "a dependent's row re-spelled a base member")
    val refused = found.filter(_.kind == ManifestAgreement.Kind.DerivedIntrusion)
    assertEquals(refused.map(_.subject), List("com.demo.Holder#other"), clue(found))
    assert(clue(refused.head.detail).contains("no row"))
    assert(!ManifestAgreement.Kind.DerivedIntrusion.fatal)
  }

  test("the composition is pure: base rows kept, own rows for own members kept, an own row at a base member refused") {
    val baseMember        = Set("com.demo.Holder#count", "com.demo.Holder#other")
    val identical         = baseRow
    val (merged, refused) = DerivedPolicy.inheriting(List(baseRow, baseRow), DerivedPolicy(List(ownRow, intrude, identical)), baseMember)
    assertEquals(merged.rows.toSet, Set(baseRow, ownRow))
    assertEquals(merged.rows.size, 2)
    assertEquals(refused.map(_.row), List(intrude))
    val contradicting           = DerivedPolicy.Row(DerivedPolicy.Family.KeepParens, "com.demo.Holder#count", "def count(): Int")
    val (_, refusedContradicts) = DerivedPolicy.inheriting(List(baseRow), DerivedPolicy(List(contradicting)), baseMember)
    assertEquals(refusedContradicts.map(r => (r.row, r.base)), List((contradicting, List(baseRow))))
  }
