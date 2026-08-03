package balticporter.core

import balticporter.core.PortMap.Disposition
import balticporter.tir.SrcMap

class PortManifestConfigSpec extends munit.FunSuite:

  private def entry(kind: String, up: String, em: String, d: Disposition) =
    PortMap.Entry(kind, up, em, d)

  test("a dependent READS the base's drops instead of restating them") {
    // The point of the whole exercise: the reason a dependent restates a base's policy is that it
    // had no way to LEARN it. A published map removes that reason.
    val base = PortMap.Map0("base-core", "eng", List(
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
      entry("type", "com.acme.lib.ui.Widget", "port.ui.Widget", Disposition.Renamed),
      entry("type", "com.acme.lib.Batch", "port.Batch", Disposition.Renamed),
    ))
    val m = PortManifestConfig.fromPortMap("d", base)
    // the pair is the PREFIX, never a partial segment: `com.acme.lib -> port`, not
    // `com.acme.lib.Ba -> port.Ba` from the shared `tch`/`tch` suffix of Batch.
    assertEquals(m.packageRenames, Map("com.acme.lib" -> "port"))
  }

  test("render/parse round-trips the declarative half") {
    val m = PortManifest(
      name           = "base-core",
      governs        = Set("com.acme.lib"),
      dropTypes      = Set("p.Json", "p.Pools"),
      dropMethods    = Set("p.Array#toArray(Class)"),
      packageRenames = Map("com.acme.lib" -> "port"),
      // the PER-TYPE half is shared surface too, so it has to survive the same round trip — a
      // dependent reading a rendered manifest and losing it would silently disagree about a name.
      typeRenames        = Map("com.acme.lib.Map" -> "MapFilter"),
      subPackages        = Map("com.acme.lib.Impl" -> "internal"),
      flattenNestedTypes = Set("com.acme.lib.Conn$Directed"),
      allowPackageSplit  = Set("com.acme.lib.Impl"),
    )
    val back = PortManifestConfig.parse(PortManifestConfig.render(m), surface = Nil)
    assertEquals(back.map(_.name), Right("base-core"))
    assertEquals(back.map(_.governs), Right(m.governs))
    assertEquals(back.map(_.dropTypes), Right(m.dropTypes))
    assertEquals(back.map(_.dropMethods), Right(m.dropMethods))
    assertEquals(back.map(_.packageRenames), Right(m.packageRenames))
    assertEquals(back.map(_.typeRenames), Right(m.typeRenames))
    assertEquals(back.map(_.subPackages), Right(m.subPackages))
    assertEquals(back.map(_.flattenNestedTypes), Right(m.flattenNestedTypes))
    assertEquals(back.map(_.allowPackageSplit), Right(m.allowPackageSplit))
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
