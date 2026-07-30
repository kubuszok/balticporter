package balticporter.core

import balticporter.core.PortMap.Disposition
import balticporter.tir.SrcMap

class PortManifestConfigSpec extends munit.FunSuite:

  private def entry(kind: String, up: String, em: String, d: Disposition) =
    PortMap.Entry(kind, up, em, d)

  test("a dependent READS the base's drops instead of restating them") {
    // The point of the whole exercise: the reason a dependent restates a base's policy is that it
    // had no way to LEARN it. A published map removes that reason.
    val base = PortMap.Map0("libgdx-core", "eng", List(
      entry("type", "p.Json", "", Disposition.Dropped),
      entry("type", "p.Pools", "p.Pools", Disposition.Substituted),
      entry("type", "p.Kept", "p.Kept", Disposition.Ported),
      entry("member", "p.Array#toArray(Class)", "", Disposition.Dropped),
      entry("member", "p.Array#size()", "p.Array#size()", Disposition.Ported),
    ))
    val m = PortManifestConfig.fromPortMap("dependent", base)
    // Dropped and Substituted are BOTH drops to a dependent: each means "do not translate this
    // mechanically", and whether something stands at the name is the base's concern.
    assertEquals(m.dropTypes, Set("p.Json", "p.Pools"))
    assertEquals(m.dropMethods, Set("p.Array#toArray(Class)"))
    assert(!m.dropTypes.contains("p.Kept"))
  }

  test("a package rename is RECOVERED from renamed types, cut at a separator") {
    val base = PortMap.Map0("b", "eng", List(
      entry("type", "com.badlogic.gdx.ui.Widget", "sge.ui.Widget", Disposition.Renamed),
      entry("type", "com.badlogic.gdx.Batch", "sge.Batch", Disposition.Renamed),
    ))
    val m = PortManifestConfig.fromPortMap("d", base)
    // the pair is the PREFIX, never a partial segment: `com.badlogic.gdx -> sge`, not
    // `com.badlogic.gdx.Ba -> sge.Ba` from the shared `tch`/`tch` suffix of Batch.
    assertEquals(m.packageRenames, Map("com.badlogic.gdx" -> "sge"))
  }

  test("render/parse round-trips the declarative half") {
    val m = PortManifest(
      name           = "libgdx-core",
      governs        = Set("com.badlogic.gdx"),
      dropTypes      = Set("p.Json", "p.Pools"),
      dropMethods    = Set("p.Array#toArray(Class)"),
      packageRenames = Map("com.badlogic.gdx" -> "sge"),
    )
    val back = PortManifestConfig.parse(PortManifestConfig.render(m), surface = Nil)
    assertEquals(back.map(_.name), Right("libgdx-core"))
    assertEquals(back.map(_.governs), Right(m.governs))
    assertEquals(back.map(_.dropTypes), Right(m.dropTypes))
    assertEquals(back.map(_.dropMethods), Right(m.dropMethods))
    assertEquals(back.map(_.packageRenames), Right(m.packageRenames))
  }

  test("the SURFACE is never silently represented as empty") {
    // A phase is code and cannot round-trip. What must not happen is a config that omits it while
    // LOOKING complete — a reader would take "no phases" as policy. The count is stated, and
    // `parse` requires the caller to supply the phases rather than defaulting them.
    val m   = PortManifest(name = "m", surface = List(new balticporter.transform.MethodBodyTransform(Map("a#b" -> "c"))))
    val txt = PortManifestConfig.render(m)
    assert(clue(txt).contains("surface: 1 phase(s), NOT represented here"))
    assert(clue(txt).contains("method-body-substitution"))
    // and it is a comment, so parsing does not resurrect it as data
    assertEquals(PortManifestConfig.parse(txt, surface = Nil).map(_.surface.size), Right(0))
  }

  test("an unknown schema is REFUSED — the manifest shares the port map's version") {
    val txt = PortManifestConfig.render(PortManifest(name = "m"))
    assert(PortManifestConfig.parse(txt, Nil).isRight)
    assert(clue(PortManifestConfig.parse(txt.replace(s"schema=${PortMap.Schema}", "schema=999"), Nil)).isLeft)
    assert(PortManifestConfig.parse("no header\n", Nil).isLeft)
  }
