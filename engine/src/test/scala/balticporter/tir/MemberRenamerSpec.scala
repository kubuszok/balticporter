package balticporter.tir

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

/** [[MemberRenamer]] — a component renamed whole, or not at all.
  *
  * The assertions that matter are the negatives: a half-applied rename compiles, moves no count and
  * breaks a contract in somebody else's repository (DESIGN.md §8.5).
  */
class MemberRenamerSpec extends munit.FunSuite:

  private def sym(p: Program, fqn: String): SymId =
    p.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse(fail(s"no symbol named $fqn"))

  private def fqns(p: Program, ids: Iterable[SymId]): Set[String] =
    ids.flatMap(p.symbolOf).map(_.name).toSet

  private def run(java: String, requests: Program => List[MemberRenamer.Request],
                  onCollision: MemberRenamer.OnCollision = MemberRenamer.OnCollision.Refuse,
                  baseUnits: Program => Set[SymId] = _ => Set.empty)
      : (Program, Program, List[MemberRenamer.Refusal], DecisionLog) =
    val p   = SpoonTir.fromSource(java)
    val g   = OverrideGraph.build(p, baseUnits = baseUnits(p))
    val log = new DecisionLog
    val (out, refusals) = MemberRenamer.rename(p, g, requests(p), onCollision, log)
    (p, out.rebuilt(xref = Xref.build(out.units)), refusals, log)

  private def cfg(key: String) = Reason.Configured("spec-phase", key)

  private def emitted(p: Program): String = new TirEmitter(p).emit

  // -------------------------------------------------------------------------

  private val musicSrc =
    """
    interface Music { void setLooping(boolean v); boolean isLooping(); }
    class NoopMusic implements Music {
      public void setLooping(boolean v) {}
      public boolean isLooping() { return false; }
    }
    class Use {
      void go(Music m) { m.setLooping(true); }
      Music make() { return new Music() {
        public void setLooping(boolean v) {}
        public boolean isLooping() { return true; }
      }; }
    }
    """

  test("a rename moves the WHOLE component and every reference with it, in one table rewrite") {
    val (p, out, refusals, log) = run(musicSrc,
      pr => List(MemberRenamer.Request(sym(pr, "Music#setLooping"), "looping_=", cfg("Music#looping"), "Music#looping")))
    assertEquals(refusals, Nil)
    // every declaration of the component
    assertEquals(out.symbolOf(sym(p, "Music#setLooping")).get.name, "looping_=")
    assertEquals(out.symbolOf(sym(p, "NoopMusic#setLooping")).get.name, "looping_=")
    // …and the reference, for free: the emitter renders every reference through the symbol's name.
    val text = emitted(out)
    assert(clue(text).contains("looping_=(true)"), "the CALL SITE did not follow the symbol")
    assertNoDiff(text.linesIterator.count(_.contains("setLooping")).toString, "0")
    // one decision per renamed DECLARATION, carrying the caller's reason verbatim
    val ds = log.of(Decision.Kind.RenamedMember)
    assertEquals(ds.size, 3, s"expected one per declaration, got ${ds.map(_.subjectFqn)}")
    assert(ds.forall(_.reason == cfg("Music#looping")))
    assert(ds.forall(_.detail("to") == "looping_="))
  }

  test("`fullName` follows the rename, cut at the `#` separator (§4.56)") {
    val (p, out, _, _) = run(musicSrc,
      pr => List(MemberRenamer.Request(sym(pr, "Music#setLooping"), "looping_=", cfg("k"), "k")))
    assertEquals(out.symbolOf(sym(p, "Music#setLooping")).get.fullName, "Music#looping_=")
    assertEquals(out.symbolOf(sym(p, "NoopMusic#setLooping")).get.fullName, "NoopMusic#looping_=")
  }

  test("an ANCHORED component is refused whole, and NOTHING is renamed") {
    val (p, out, refusals, log) = run(
      """
      import java.util.Comparator;
      class ByName implements Comparator<String> {
        public int compare(String a, String b) { return 0; }
      }
      """,
      pr => List(MemberRenamer.Request(sym(pr, "ByName#compare"), "cmp", cfg("ByName#compare"), "ByName#compare")))
    assertEquals(refusals.size, 1)
    assert(clue(refusals.head.why).contains("java.util.Comparator"))
    assertEquals(refusals.head.anchors.map(_._1), Set("java.util.Comparator"))
    assertEquals(out.symbolOf(sym(p, "ByName#compare")).get.name, "compare")
    assertEquals(log.of(Decision.Kind.RenamedMember), Nil, "a refused rename records nothing")
  }

  test("a GROUP stands or falls together — half a property is not a property") {
    val (p, out, refusals, _) = run(
      """
      import java.util.Comparator;
      class Widget { public int getWidth() { return 1; } }
      class Sorted implements Comparator<String> { public int compare(String a, String b) { return 0; } }
      """,
      pr => List(
        // `Widget#getWidth` is renameable on its own; `Sorted#compare` is anchored, and the first
        // must go down with it because they share a group.
        MemberRenamer.Request(sym(pr, "Widget#getWidth"), "width", cfg("k"), "one-property"),
        MemberRenamer.Request(sym(pr, "Sorted#compare"), "cmp", cfg("k"), "one-property"),
      ))
    assertEquals(refusals.size, 2)
    assert(clue(refusals.map(_.why)).exists(_.contains("another request in group")))
    assertEquals(out.symbolOf(sym(p, "Widget#getWidth")).get.name, "getWidth", "nothing may be half-applied")
  }

  test("EFFECTIVE names, PARENTS-FIRST: a child is held against what its ancestor WILL be called") {
    // `Base#tag` is renamed to `label`; `Sub` already declares `label`, so the collision is only
    // visible if the child is tested against the ancestor's NEW name. Reading original names is
    // §4.55's recorded mistake.
    val (p, out, refusals, _) = run(
      """
      class Base { String tag() { return "t"; } }
      class Sub extends Base { String label() { return "l"; } }
      """,
      pr => List(MemberRenamer.Request(sym(pr, "Base#tag"), "label", cfg("Base#tag"), "Base#tag")))
    assertEquals(refusals.size, 1)
    assert(clue(refusals.head.why).contains("Sub#label"))
    assertEquals(out.symbolOf(sym(p, "Base#tag")).get.name, "tag")
  }

  test("`Refuse` refuses a collision with a FIELD too — the requested name is policy") {
    val (_, _, refusals, _) = run(
      """class Thing { private int width; public int getWidth() { return width; } }""",
      pr => List(MemberRenamer.Request(sym(pr, "Thing#getWidth"), "width", cfg("k"), "k")),
      MemberRenamer.OnCollision.Refuse)
    assertEquals(refusals.size, 1)
    assert(clue(refusals.head.why).contains("Thing#width"))
  }

  test("`DeferToEmitter` ACCEPTS a private-field clash — `resolveMemberClashes` moves the FIELD") {
    val (p, out, refusals, _) = run(
      """class Thing { private int width; public int getWidth() { return width; } }""",
      pr => List(MemberRenamer.Request(sym(pr, "Thing#getWidth"), "width", cfg("k"), "k")),
      MemberRenamer.OnCollision.DeferToEmitter)
    assertEquals(refusals, Nil)
    assertEquals(out.symbolOf(sym(p, "Thing#getWidth")).get.name, "width")
    // …and the emitter's own §4.55 pass does exactly what was deferred to it.
    val text = emitted(out)
    assert(clue(text).contains("width$field"), "the field did not move out of the way")
    assert(text.contains("def width"))
  }

  test("`DeferToEmitter` still REFUSES a clash with a METHOD — no pass moves one") {
    val (_, _, refusals, _) = run(
      """class Thing { public int width() { return 1; } public int getWidth() { return 2; } }""",
      pr => List(MemberRenamer.Request(sym(pr, "Thing#getWidth"), "width", cfg("k"), "k")),
      MemberRenamer.OnCollision.DeferToEmitter)
    assertEquals(refusals.size, 1)
    assert(clue(refusals.head.why).contains("not a member the emitter"))
  }

  test("`DeferToEmitter` REFUSES a clash with a STATIC field — that one lands in the companion") {
    val (_, _, refusals, _) = run(
      """class Thing { static int width = 1; public int getWidth() { return 2; } }""",
      pr => List(MemberRenamer.Request(sym(pr, "Thing#getWidth"), "width", cfg("k"), "k")),
      MemberRenamer.OnCollision.DeferToEmitter)
    assertEquals(refusals.size, 1)
  }

  test("`SuffixUntilFree` is §4.55's idiom — append `$` until the name is free") {
    val (p, out, refusals, _) = run(
      """class Thing { public int width() { return 1; } public int getWidth() { return 2; } }""",
      pr => List(MemberRenamer.Request(sym(pr, "Thing#getWidth"), "width", cfg("k"), "k")),
      MemberRenamer.OnCollision.SuffixUntilFree)
    assertEquals(refusals, Nil)
    assertEquals(out.symbolOf(sym(p, "Thing#getWidth")).get.name, "width$")
  }

  test("two requests claiming ONE symbol with two names refuse each other") {
    val (p, out, refusals, _) = run(
      """
      interface I { void m(); }
      class C implements I { public void m() {} }
      """,
      pr => List(
        MemberRenamer.Request(sym(pr, "I#m"), "a", cfg("k1"), "g1"),
        MemberRenamer.Request(sym(pr, "C#m"), "b", cfg("k2"), "g2"),
      ))
    assertEquals(refusals.size, 2)
    assert(refusals.exists(_.why.contains("a symbol has one name")))
    assertEquals(out.symbolOf(sym(p, "I#m")).get.name, "m")
  }

  test("two requests claiming one symbol with the SAME name are one rename, not a conflict") {
    val (p, out, refusals, log) = run(
      """
      interface I { void m(); }
      class C implements I { public void m() {} }
      """,
      pr => List(
        MemberRenamer.Request(sym(pr, "I#m"), "go", cfg("k1"), "g1"),
        MemberRenamer.Request(sym(pr, "C#m"), "go", cfg("k2"), "g2"),
      ))
    assertEquals(refusals, Nil)
    assertEquals(out.symbolOf(sym(p, "I#m")).get.name, "go")
    assertEquals(out.symbolOf(sym(p, "C#m")).get.name, "go")
  }

  test("a request naming an EXTERNAL symbol is refused — there is no declaration to rename") {
    val (_, _, refusals, _) = run(
      """class Thing { void go(String s) { s.length(); } }""",
      // found STRUCTURALLY: an interned external is a symbol with no unit above it (§4.56), which is
      // the same test `Program.owned` makes — never by spelling its name.
      pr => List(MemberRenamer.Request(
        pr.symbols.all.find(s => !pr.owns(s.id) && s.name == "length").map(_.id)
          .getOrElse(fail("the frontend interned no external `length`")),
        "len", cfg("k"), "k")))
    assertEquals(refusals.size, 1)
    assert(clue(refusals.head.why).contains("REFERENCES and does not DECLARE"))
  }

  test("an empty request list is a no-op that returns the SAME program") {
    val p = SpoonTir.fromSource("""class Thing { int x; }""")
    val (out, refusals) = MemberRenamer.rename(p, OverrideGraph.build(p), Nil,
      MemberRenamer.OnCollision.Refuse, new DecisionLog)
    assert(out eq p)
    assertEquals(refusals, Nil)
  }

  test("a BASE-owned component refuses, and says which module owns it") {
    val src =
      """
      interface Layer { int getDepth(); }
      class MyLayer implements Layer { public int getDepth() { return 1; } }
      """
    val p    = SpoonTir.fromSource(src)
    val base = p.units.find(u => p.symbolOf(u.symbol).exists(_.fullName == "Layer")).get.symbol
    val g    = OverrideGraph.build(p, baseUnits = Set(base))
    val (out, refusals) = MemberRenamer.rename(p, g,
      List(MemberRenamer.Request(sym(p, "MyLayer#getDepth"), "depth", cfg("k"), "k")),
      MemberRenamer.OnCollision.Refuse, new DecisionLog)
    assertEquals(refusals.size, 1)
    assert(clue(refusals.head.why).contains("resolution root"))
    assertEquals(out.symbolOf(sym(p, "MyLayer#getDepth")).get.name, "getDepth")
  }

  test("renaming reaches an ANONYMOUS body's implementation — the 156-site blind spot") {
    val (p, out, _, log) = run(musicSrc,
      pr => List(MemberRenamer.Request(sym(pr, "Music#isLooping"), "looping", cfg("k"), "k")))
    val text = emitted(out)
    assertEquals(text.linesIterator.count(_.contains("isLooping")), 0,
      s"an anonymous-class implementation kept the old name:\n$text")
    assertEquals(log.of(Decision.Kind.RenamedMember).size, 3)
  }
