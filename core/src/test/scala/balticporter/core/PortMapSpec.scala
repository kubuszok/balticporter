package balticporter.core

import balticporter.core.PortMap.Disposition
import balticporter.tir.SrcMap

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

  test("a package rename is REVERSED, so `upstream` is the name a dependent's Java still uses") {
    val m = build(
      emitted = List("sge.ui.Widget"),
      members = List(member("sge.ui.Widget", "sge.ui.Widget#draw(Batch)")),
      renames = Map("com.badlogic.gdx" -> "sge"),
    )
    val t = m.types.head
    assertEquals(t.upstream, "com.badlogic.gdx.ui.Widget")
    assertEquals(t.emitted, "sge.ui.Widget")
    assertEquals(t.disposition, Disposition.Renamed)
    // the member follows the same reversal — this is what makes the map usable as a lookup from a
    // dependent that has only ever seen the upstream names
    assertEquals(m.members.head.upstream, "com.badlogic.gdx.ui.Widget#draw(Batch)")
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
