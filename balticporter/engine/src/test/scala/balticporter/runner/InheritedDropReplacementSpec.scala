package balticporter.runner

import balticporter.core.*
import balticporter.tir.*
import balticporter.transform.CollectionsTransform

import java.nio.file.{ Files, Path }

/** A base drops a type and retargets it onto a replacement, scoped to its own package. The replacement is inherited with the drop: a dependent's own declarations of the dropped type take it too,
  * while the dependent's other collections stay as the base's scope leaves them.
  */
class InheritedDropReplacementSpec extends munit.FunSuite:

  private def java(dir: Path, rel: String, src: String): Unit =
    val p = dir.resolve(rel)
    Files.createDirectories(p.getParent)
    Files.writeString(p, src)

  private def fixture(): (Path, Path, Path) =
    val root = Files.createTempDirectory("inherited-drop")
    val base = root.resolve("base")
    val dep  = root.resolve("dep")
    java(
      base,
      "com/base/Floats.java",
      """package com.base;
        |public class Floats {
        |  public float[] items = new float[4];
        |  public int size;
        |  public float get(int i) { return items[i]; }
        |}""".stripMargin
    )
    java(
      base,
      "com/base/Holder.java",
      """package com.base;
        |public class Holder {
        |  public Floats values = new Floats();
        |}""".stripMargin
    )
    java(
      dep,
      "com/dep/Uses.java",
      """package com.dep;
        |import com.base.Floats;
        |public class Uses {
        |  protected Floats errors = new Floats();
        |  public java.util.List<String> names = null;
        |  public float first(Floats f) { Floats local = f; return local.get(0); }
        |}""".stripMargin
    )
    (root, base, dep)

  private def baseManifest(retarget: Boolean) = PortManifest(
    name = "base",
    governs = Set("com.base"),
    dropTypes = Set("com.base.Floats"),
    surface = List(
      new CollectionsTransform(
        scope = RuleScope.Only(Set("com.base")),
        retarget = if retarget then Map("com.base.Floats" -> "scala.collection.mutable.ArrayBuffer") else Map.empty,
        retargetTypeArgs = if retarget then Map("com.base.Floats" -> List(CollectionsTransform.RetargetArg.FixedType("scala.Float"))) else Map.empty
      )
    )
  )

  private def run(root: Path, base: Path, dep: Path, retarget: Boolean = true): String =
    val r = PortRun(
      "dep",
      root.resolve("port"),
      SourceSet.Main,
      FrontendConfig(dep, List("com/dep/Uses.java"), Nil, resolutionRoots = List(base)),
      Nil,
      manifest = Some(baseManifest(retarget).extendedBy(PortManifest("dep", governs = Set("com.dep"))))
    ).execute()
    Files.readString(r.outDir.resolve("com/dep/Uses.scala"))

  test("the base's replacement of a type it DROPS reaches the dependent's own declarations, outside the base's scope") {
    val (root, base, dep) = fixture()
    val uses              = run(root, base, dep)
    // the field, the parameter and the local: every own declaration of the dropped type
    assert(!clue(uses).contains("com.base.Floats"), "a dependent declaration kept a type the base drops and nothing declares")
    assert(uses.contains("errors: scala.collection.mutable.ArrayBuffer[scala.Float]"), clue(uses))
    assert(uses.contains("f: scala.collection.mutable.ArrayBuffer[scala.Float]"), clue(uses))
  }

  test("…and ONLY that entry: the dependent's JDK collection stays where the base's scope leaves it") {
    val (root, base, dep) = fixture()
    val uses              = run(root, base, dep)
    assert(
      clue(uses).contains("names: java.util.List[java.lang.String]"),
      "the inherited drop widened the whole phase's scope, not its own entry"
    )
  }

  test("a scoped TYPE REDIRECT of a dropped type is inherited with the drop the same way") {
    val (root, base, dep) = fixture()
    val m                 = PortManifest(
      name = "base",
      governs = Set("com.base"),
      dropTypes = Set("com.base.Floats"),
      surface = List(
        new balticporter.transform.TypeRedirectTransform(
          redirects = Map("com.base.Floats" -> "com.base.FloatSeq"),
          scopes = Map("com.base.Floats" -> RuleScope.Only(Set("com.base")))
        )
      )
    ).extendedBy(PortManifest("dep", governs = Set("com.dep")))
    val r = PortRun(
      "dep",
      root.resolve("port"),
      SourceSet.Main,
      FrontendConfig(dep, List("com/dep/Uses.java"), Nil, resolutionRoots = List(base)),
      Nil,
      manifest = Some(m)
    ).execute()
    val uses = Files.readString(r.outDir.resolve("com/dep/Uses.scala"))
    assert(!clue(uses).contains("com.base.Floats"), "a dependent declaration kept a type the base drops and nothing declares")
    assert(uses.contains("errors: com.base.FloatSeq"), clue(uses))
    assert(uses.contains("f: com.base.FloatSeq"), clue(uses))
  }

  test("NEGATIVE: with no retarget for the dropped type nothing moves — the pass is a no-op without a replacement") {
    val (root, base, dep) = fixture()
    val uses              = run(root, base, dep, retarget = false)
    assert(clue(uses).contains("com.base.Floats"))
  }
