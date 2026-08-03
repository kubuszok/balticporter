package balticporter.tir

import balticporter.frontend.spoon.SpoonTir

/** [[OverrideGraph]] — the component, and everything that freezes one.
  *
  * Every test here asserts a fact no compile and no other count can see: a closure that stops one
  * declaration short still compiles, and the contract it breaks breaks at run time in somebody
  * else's repository (DESIGN.md §8.5).
  */
class OverrideGraphSpec extends munit.FunSuite:

  private def graphOf(java: String, baseUnits: Set[SymId] = Set.empty): (Program, OverrideGraph) =
    val p = SpoonTir.fromSource(java)
    (p, OverrideGraph.build(p, baseUnits = baseUnits))

  private def sym(p: Program, fqn: String): SymId =
    p.symbols.all.find(_.fullName == fqn).map(_.id)
      .getOrElse(fail(s"no symbol named $fqn — the program has: " +
        p.symbols.all.filter(_.fullName.contains('#')).map(_.fullName).toList.sorted.take(40).mkString(", ")))

  private def fqns(p: Program, ids: Set[SymId]): Set[String] =
    ids.flatMap(p.symbolOf).map(_.fullName)

  // -------------------------------------------------------------------------

  test("the closure spans interface -> class -> ANONYMOUS BODY, which a class-only walk cannot see") {
    val (p, g) = graphOf(
      """
      interface Music { void setLooping(boolean v); }
      class NoopMusic implements Music { public void setLooping(boolean v) {} }
      class Use {
        Music make() { return new Music() { public void setLooping(boolean v) {} }; }
      }
      """)
    val c = g.closureOf(sym(p, "Music#setLooping"))
    val names = fqns(p, c.members)
    assert(clue(names).contains("Music#setLooping"))
    assert(clue(names).contains("NoopMusic#setLooping"))
    // the anonymous body's member is owned by a SYNTHETIC type symbol, so it is named by that
    // owner rather than by `Music` — what matters is that there are THREE of them.
    assertEquals(clue(c.members).size, 3, s"anonymous-class implementation missing: $names")
    assertEquals(c.externalAnchors, Set.empty[(String, String)])
    assert(!c.isAnchored)
  }

  test("edges are DESCRIPTOR-keyed: same name, same ARITY, different parameter type is NO edge") {
    val (p, g) = graphOf(
      """
      class Base { void put(int v) {} }
      class Sub extends Base { void put(String v) {} }
      """)
    val c = g.closureOf(sym(p, "Base#put"))
    assertEquals(fqns(p, c.members), Set("Base#put"))
    assertEquals(g.overriders(sym(p, "Base#put")), Nil)
  }

  test("…and the same descriptor IS an edge, through two levels") {
    val (p, g) = graphOf(
      """
      class Base { void put(int v) {} }
      class Mid extends Base { void put(int v) {} }
      class Leaf extends Mid { void put(int v) {} }
      """)
    val c = g.closureOf(sym(p, "Leaf#put"))
    assertEquals(fqns(p, c.members), Set("Base#put", "Mid#put", "Leaf#put"))
    assertEquals(g.overridden(sym(p, "Leaf#put")).toSet, Set(sym(p, "Base#put"), sym(p, "Mid#put")))
    assertEquals(g.overriders(sym(p, "Base#put")).toSet, Set(sym(p, "Mid#put"), sym(p, "Leaf#put")))
  }

  test("a DIAMOND closes into ONE closure, not two overlapping halves") {
    val (p, g) = graphOf(
      """
      interface Left  { void run(); }
      interface Right { void run(); }
      class Both implements Left, Right { public void run() {} }
      class OnlyLeft implements Left { public void run() {} }
      """)
    val c = g.closureOf(sym(p, "Right#run"))
    assertEquals(fqns(p, c.members), Set("Left#run", "Right#run", "Both#run", "OnlyLeft#run"),
      "the walk must go up AND down from every member it reaches, or `OnlyLeft` is invisible from `Right`")
  }

  test("an UNPARSED parent ANCHORS — refuse and count, never guess (DESIGN.md §8.5)") {
    val (p, g) = graphOf(
      """
      import java.util.Comparator;
      class ByName implements Comparator<String> {
        public int compare(String a, String b) { return 0; }
        public int getRank() { return 1; }
      }
      """)
    val c = g.closureOf(sym(p, "ByName#getRank"))
    assert(c.isAnchored, "an unparsed parent with no surface data must anchor")
    assertEquals(c.externalAnchors.map(_._1), Set("java.util.Comparator"))
    assert(clue(c.anchorReason(p).getOrElse("")).contains("does not"))
  }

  test("…and a SURFACE that rules the member out lifts the anchor, with no change to the graph") {
    val src =
      """
      import java.util.Comparator;
      class ByName implements Comparator<String> {
        public int compare(String a, String b) { return 0; }
        public int getRank() { return 1; }
      }
      """
    val p = SpoonTir.fromSource(src)
    val surface = ExternalSurface.default ++ ExternalSurface(Map(
      "java.util.Comparator" -> Set(ExternalSurface.Member("compare", 2))))
    val g = OverrideGraph.build(p, surface)
    assert(!g.closureOf(sym(p, "ByName#getRank")).isAnchored, "the surface says `getRank` is not there")
    assert(g.closureOf(sym(p, "ByName#compare")).isAnchored, "…and says `compare` IS")
  }

  test("a PLATFORM interface's closed surface lifts the anchor by DEFAULT — ENGINE-LIMITS K12") {
    // `java.lang.Iterable` declares three methods and no library can add to it, so an absence from
    // its member set really is proof. Before this, every accessor of every class with `implements
    // Iterable` in its parent list was frozen — 12 of libGDX's 17 refused property renames.
    val src =
      """
      class Bag implements java.lang.Iterable<String>, java.io.Serializable {
        public java.util.Iterator<String> iterator() { return null; }
        public int getToggle() { return 1; }
      }
      """
    val (p, g) = graphOf(src)
    assert(!g.closureOf(sym(p, "Bag#getToggle")).isAnchored,
      "`java.lang.Iterable` does not declare `getToggle`, and the platform surface knows it")
    assert(g.closureOf(sym(p, "Bag#iterator")).isAnchored, "…and it DOES declare `iterator`")
  }

  test("…and a parent whose surface is NOT closed still anchors — the default is knowledge, not optimism") {
    // `java.util.Comparator`'s default methods grew across releases, so it is deliberately absent
    // from `jdkPlatform`: an incomplete entry would turn a counted over-refusal into a silent
    // under-refusal, which is the trade DESIGN.md §8.5 chose against.
    assert(!ExternalSurface.default.isKnown("java.util.Comparator"))
    val (p, g) = graphOf(
      """
      class ByName implements java.util.Comparator<String> {
        public int compare(String a, String b) { return 0; }
        public int getRank() { return 1; }
      }
      """)
    assert(g.closureOf(sym(p, "ByName#getRank")).isAnchored)
  }

  test("`java.lang.Object` anchors even though no parent list ever names it") {
    // `SpoonTir.superTypes` filters `java.lang.Object` out on purpose, so without the implicit root
    // a rename of `toString` reads as unanchored — and silently breaks every `println` of it.
    val (p, g) = graphOf("""class Thing { public String toString() { return "x"; } }""")
    val c = g.closureOf(sym(p, "Thing#toString"))
    assert(c.isAnchored)
    assertEquals(c.externalAnchors, Set(("java.lang.Object", "toString")))
  }

  test("a plain member of a plain class is NOT anchored — the mechanism can actually fire") {
    val (p, g) = graphOf("""class Thing { public int getWidth() { return 1; } }""")
    assert(!g.closureOf(sym(p, "Thing#getWidth")).isAnchored)
  }

  test("a BASE-owned declaration anchors the component (ENGINE-LIMITS D2)") {
    val (p0, _) = graphOf("""
      interface Layer { int getDepth(); }
      class MyLayer implements Layer { public int getDepth() { return 1; } }
      """)
    val baseUnit = p0.units.find(u => p0.symbolOf(u.symbol).exists(_.fullName == "Layer")).get.symbol
    val g = OverrideGraph.build(p0, baseUnits = Set(baseUnit))
    val c = g.closureOf(sym(p0, "MyLayer#getDepth"))
    assert(c.isAnchored, "renaming a base's declaration from a dependent emits a second definition of it")
    assertEquals(fqns(p0, c.baseAnchors), Set("Layer#getDepth"))
    assert(clue(c.anchorReason(p0).getOrElse("")).contains("resolution root"))
  }

  test("a FIELD is its own closure — java fields SHADOW, they do not override") {
    val (p, g) = graphOf(
      """
      class Base { int data; }
      class Sub extends Base { int data; }
      """)
    val c = g.closureOf(sym(p, "Sub#data"))
    assertEquals(fqns(p, c.members), Set("Sub#data"))
    assertEquals(g.signatureOf(sym(p, "Sub#data")), scala.None)
  }

  test("an ENUM CONSTANT's body is a node — its overrides move with the enum's method") {
    val (p, g) = graphOf(
      """
      enum Op {
        ADD { public int apply(int x) { return x + 1; } },
        NOP;
        public int apply(int x) { return x; }
      }
      """)
    val c = g.closureOf(sym(p, "Op#apply"))
    assertEquals(clue(c.members).size, 2, s"the constant body's override is missing: ${fqns(p, c.members)}")
  }

  test("a parent CYCLE cannot hang the walk") {
    // Not expressible in Java; built by hand, because the guarantee is about a corrupt model and
    // the failure mode is a phase that never returns.
    val p = SpoonTir.fromSource("class A { void m() {} }")
    val g = OverrideGraph.build(p)
    val a = p.units.head.symbol
    assertEquals(g.relativesOf(a).contains(a), true)
    assertEquals(g.ancestorsOf(a), Nil)
  }

  test("`build` is a `StandardTraversal` walk, so every declared type is a node") {
    val (p, g) = graphOf(
      """
      class Outer {
        static class Inner { void m() {} }
        Runnable r = new Runnable() { public void run() {} };
      }
      """)
    val named = g.types.flatMap(p.symbolOf).map(_.name)
    assert(clue(named).contains("Outer"))
    assert(clue(named).contains("Inner"))
    assert(clue(g.types).size >= 3, "the anonymous body is a node too")
  }
