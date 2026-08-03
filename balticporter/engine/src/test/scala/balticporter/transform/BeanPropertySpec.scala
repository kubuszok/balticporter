package balticporter.transform

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** [[BeanPropertyTransform]] — the positives, and one negative per refusal DESIGN.md §8.5 names.
  *
  * Every negative asserts TWO things: the pair is untouched, and the refusal is COUNTED. A silent
  * skip and a counted one look identical in the emitted file and are opposite facts about the port.
  */
class BeanPropertySpec extends munit.FunSuite:

  private case class Ran(before: Program, after: Program, phase: BeanPropertyTransform, log: DecisionLog):
    def out: String = new TirEmitter(after).emit
    def refusals: List[String] = phase.policyReport.findings.map(_.detail)
    def named(fqn: String): Option[Symbol] = after.symbols.all.find(_.fullName == fqn)

  private def run(java: String, pairs: (String, String)*): Ran =
    val before = SpoonTir.fromSource(java)
    val phase  = new BeanPropertyTransform(pairs.toMap)
    val (after, log) = Pipeline.runTraced(before, List(phase))
    Ran(before, after, phase, log)

  /** what the member the UPSTREAM called `fqn` is called now. Resolved by SYMBOL — a rename moves
    * `fullName` too (§4.56), so looking the result up by the old name finds nothing and reads as a
    * missing member rather than as a successful rename. */
  private def nameOf(r: Ran, fqn: String): String =
    r.before.symbols.all.find(_.fullName == fqn).map(_.id)
      .flatMap(r.after.symbolOf).map(_.name).getOrElse(s"<no $fqn>")

  // -------------------------------------------------------------------------------------------
  // positives
  // -------------------------------------------------------------------------------------------

  private val layerSrc =
    """
    class MapLayer {
      private float opacity = 1.0f;
      public float getOpacity() { return opacity; }
      public void setOpacity(float o) { this.opacity = o; }
    }
    class Use {
      void go(MapLayer l) {
        l.setOpacity(l.getOpacity() + 1.0f);
        float f = l.getOpacity();
      }
    }
    """

  test("a get/set pair becomes `def x` / `def x_=`, with the BODIES kept verbatim") {
    val r = run(layerSrc, "MapLayer#opacity" -> "getOpacity/setOpacity")
    assertEquals(r.phase.policyReport.findings, Nil, r.phase.policyReport.render)
    assert(clue(r.out).contains("def opacity: scala.Float"))
    assert(r.out.contains("def opacity_="))
    // the emitter's own §4.55 pass moved the FIELD out of the way — the `DeferToEmitter` contract.
    assert(clue(r.out).contains("opacity$field"))
    assertEquals(r.out.linesIterator.count(_.contains("getOpacity")), 0)
    assertEquals(r.out.linesIterator.count(_.contains("setOpacity")), 0)
  }

  test("the getter loses its EMPTY PARAMETER CLAUSE — `def opacity`, never `def opacity()`") {
    val r = run(layerSrc, "MapLayer#opacity" -> "getOpacity/setOpacity")
    assert(clue(r.out).contains("def opacity: "), "a java nilary is `List(Nil)` and renders `()`")
    assert(!r.out.contains("def opacity()"))
  }

  test("call sites: a READ becomes a selection and a WRITE becomes an assignment") {
    val r = run(layerSrc, "MapLayer#opacity" -> "getOpacity/setOpacity")
    assert(clue(r.out).contains("val f: scala.Float = l.opacity"))
    assert(clue(r.out).contains("l.opacity = "))
  }

  test("the COMPOUND form `o.setX(o.getX() + 1)` needs no special case — the traversal is bottom-up") {
    val r = run(layerSrc, "MapLayer#opacity" -> "getOpacity/setOpacity")
    assert(clue(r.out).contains("l.opacity = l.opacity + 1.0f"))
  }

  test("a call qualified by `this` renders `this.x = this.x + 1` on both sides") {
    val r = run(
      """
      class Thing {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { this.w = v; }
        void bump() { setW(getW() + 1); }
      }
      """, "Thing#w" -> "getW/setW")
    assertEquals(r.phase.policyReport.findings, Nil, r.phase.policyReport.render)
    assert(clue(r.out).contains("this.w = this.w + 1"))
  }

  test("a GET-ONLY entry converts the getter alone") {
    val r = run(
      """
      class Map1 { public String getProperties() { return "p"; } }
      class Use { void go(Map1 m) { String s = m.getProperties(); } }
      """, "Map1#properties" -> "getProperties")
    assertEquals(r.phase.policyReport.findings, Nil, r.phase.policyReport.render)
    assert(clue(r.out).contains("def properties: "))
    assert(r.out.contains("m.properties"))
    assert(!r.out.contains("_="))
  }

  test("an INTERFACE pair propagates into every implementor AND every anonymous body") {
    val r = run(
      """
      interface Drawable { float getLeftWidth(); void setLeftWidth(float w); }
      class BaseDrawable implements Drawable {
        private float lw;
        public float getLeftWidth() { return lw; }
        public void setLeftWidth(float w) { this.lw = w; }
      }
      class Use {
        Drawable make() { return new Drawable() {
          public float getLeftWidth() { return 0f; }
          public void setLeftWidth(float w) {}
        }; }
        void go(Drawable d) { d.setLeftWidth(d.getLeftWidth()); }
      }
      """, "Drawable#leftWidth" -> "getLeftWidth/setLeftWidth")
    assertEquals(r.phase.policyReport.findings, Nil, r.phase.policyReport.render)
    assertEquals(r.out.linesIterator.count(_.contains("LeftWidth")), 0,
      s"an implementor or the anonymous body kept the java name:\n${r.out}")
    assertEquals(r.log.of(Decision.Kind.RenamedMember).size, 6,
      "3 declarations x 2 accessors, one decision each")
  }

  test("an N-DEEP override chain is renamed ATOMICALLY, or not at all") {
    val r = run(
      """
      class A { public int getV() { return 0; } }
      class B extends A { public int getV() { return 1; } }
      class C extends B { public int getV() { return 2; } }
      class D extends C { public int getV() { return 3; } }
      """, "A#v" -> "getV")
    assertEquals(r.phase.policyReport.findings, Nil, r.phase.policyReport.render)
    List("A", "B", "C", "D").foreach(t => assertEquals(nameOf(r, s"$t#getV"), "v", s"$t did not move"))
    assertEquals(r.log.of(Decision.Kind.RenamedMember).size, 4)
  }

  test("only the NILARY overload converts — `getX(int)` is left exactly as it is") {
    val r = run(
      """
      class Grid {
        public int getCell() { return 0; }
        public int getCell(int i) { return i; }
      }
      class Use { void go(Grid g) { int a = g.getCell(); int b = g.getCell(3); } }
      """, "Grid#cell" -> "getCell")
    assertEquals(r.phase.policyReport.findings, Nil, r.phase.policyReport.render)
    assert(clue(r.out).contains("g.cell"))
    assert(clue(r.out).contains("g.getCell(3)"), "the parameterised overload must not move")
  }

  test("the decision carries `Reason.Configured` with the MANIFEST ENTRY as the key (§4.575)") {
    val r  = run(layerSrc, "MapLayer#opacity" -> "getOpacity/setOpacity")
    val ds = r.log.of(Decision.Kind.RenamedMember)
    assertEquals(ds.size, 2)
    assert(ds.forall(_.reason == Reason.Configured("bean-properties", "MapLayer#opacity")))
  }

  // -------------------------------------------------------------------------------------------
  // the no-op, which is the §1(b) gate
  // -------------------------------------------------------------------------------------------

  test("EMPTY pairs is a structural no-op — byte-identical output, and the SAME program back") {
    val plain = SpoonTir.fromSource(layerSrc)
    val phase = new BeanPropertyTransform()
    assert(phase.run(plain) eq plain, "an empty policy must not build a graph or rebuild a program")
    val r = run(layerSrc)
    assertEquals(r.out, new TirEmitter(SpoonTir.fromSource(layerSrc)).emit)
    assertEquals(r.log.all, Nil)
    assertEquals(r.phase.policyReport.findings, Nil)
  }

  // -------------------------------------------------------------------------------------------
  // negatives — each: the pair is untouched, and the refusal is COUNTED
  // -------------------------------------------------------------------------------------------

  private def assertUntouched(r: Ran, fqn: String, was: String)(using munit.Location): Unit =
    assertEquals(nameOf(r, fqn), was, "the accessor moved despite the refusal")
    assert(r.phase.policyReport.nonEmpty, "the refusal was not counted — a silent skip")
    assertEquals(r.log.of(Decision.Kind.RenamedMember), Nil)
    assert(r.log.of(Decision.Kind.ScopedOut).nonEmpty, "a refusal must leave a decision row")

  test("an accessor overriding an UNPARSED (JDK) member refuses the WHOLE pair") {
    val r = run(
      """
      import java.util.Comparator;
      class Sorted implements Comparator<String> {
        private int rank;
        public int compare(String a, String b) { return 0; }
        public int getRank() { return rank; }
        public void setRank(int v) { this.rank = v; }
      }
      """, "Sorted#rank" -> "getRank/setRank")
    assertUntouched(r, "Sorted#getRank", "getRank")
    assertEquals(nameOf(r, "Sorted#setRank"), "setRank", "half a property is not a property")
    assert(clue(r.refusals.mkString("\n")).contains("java.util.Comparator"))
  }

  test("a FLUENT setter refuses — `o.x = v` is Unit and a chain has no assignment rendering") {
    val r = run(
      """
      class Builder {
        private int w;
        public int getW() { return w; }
        public Builder setW(int v) { this.w = v; return this; }
      }
      """, "Builder#w" -> "getW/setW")
    assertUntouched(r, "Builder#getW", "getW")
    assert(clue(r.refusals.mkString("\n")).contains("FLUENT"))
  }

  test("a SET-ONLY entry refuses — the assignment's LHS names the GETTER, and there is none") {
    val r = run(
      """class Thing { private int w; public void setW(int v) { this.w = v; } }""",
      "Thing#w" -> "/setW")
    assertEquals(nameOf(r, "Thing#setW"), "setW")
    assertEquals(r.phase.policyReport.of(PolicyIssue.Malformed).size, 1)
    assert(clue(r.refusals.mkString("\n")).contains("nothing to put on an LHS"))
  }

  test("a VALUE-POSITION accessor reference refuses — an eta-expanded accessor is not the SAM") {
    val r = run(
      """
      import java.util.function.Supplier;
      class Thing {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { this.w = v; }
        Supplier<Integer> read() { return this::getW; }
      }
      """, "Thing#w" -> "getW/setW")
    assertUntouched(r, "Thing#getW", "getW")
    assert(clue(r.refusals.mkString("\n")).contains("VALUE position"))
  }

  test("a STATIC accessor refuses — a companion property is out of scope (v1)") {
    val r = run(
      """
      class Cfg {
        private static int w;
        public static int getW() { return w; }
        public static void setW(int v) { w = v; }
      }
      """, "Cfg#w" -> "getW/setW")
    assertUntouched(r, "Cfg#getW", "getW")
    assert(clue(r.refusals.mkString("\n")).contains("STATIC"))
  }

  test("a collision with an existing METHOD named `x` refuses — no §4.55 pass moves a method") {
    val r = run(
      """
      class Thing {
        public int width() { return 1; }
        public int getWidth() { return 2; }
        public void setWidth(int v) {}
      }
      """, "Thing#width" -> "getWidth/setWidth")
    assertUntouched(r, "Thing#getWidth", "getWidth")
    assert(clue(r.refusals.mkString("\n")).contains("not a member the emitter"))
  }

  test("an entry naming an accessor that does NOT exist is a binder finding, never a synthesis") {
    val r = run("""class Thing { public int getW() { return 1; } }""", "Thing#w" -> "getW/setW")
    assertEquals(nameOf(r, "Thing#getW"), "getW", "a half-bound pair must not half-apply")
    val fs = r.phase.policyReport.findings
    assertEquals(clue(fs).count(_.issue == PolicyIssue.NeverMatched), 1)
    assert(fs.exists(_.key == "Thing#setW"))
    assert(!r.out.contains("def w_="), "NEVER INVENT A MEMBER")
  }

  test("an entry naming a type this program does not DECLARE reports through the binder") {
    // The `RuleScope`/`Ownership.Owned` rule: an entry naming a JDK type matches the interned
    // external perfectly, the phase rewrites nothing, and without this it counts as having fired.
    val r = run("""class Thing { void go(String s) { s.length(); } }""",
      "java.lang.String#len" -> "length")
    assertEquals(r.log.of(Decision.Kind.RenamedMember), Nil)
    val fs = r.phase.policyReport.findings
    assertEquals(clue(fs).size, 1)
    assertEquals(fs.head.issue, PolicyIssue.NeverMatched)
    assert(clue(fs.head.detail).contains("REFERENCES and does not DECLARE") ||
           clue(fs.head.detail).contains("silently did not run"))
  }

  test("a MALFORMED key is reported as malformed, not as a typo") {
    val r = run(layerSrc, "MapLayer.opacity" -> "getOpacity")
    assertEquals(r.phase.policyReport.of(PolicyIssue.Malformed).size, 1)
    assert(clue(r.refusals.mkString).contains("no `#`"))
  }

  test("a key carrying a PARAMETER LIST is malformed — the key names the emitted PROPERTY") {
    val r = run(layerSrc, "MapLayer#opacity()" -> "getOpacity")
    assertEquals(r.phase.policyReport.of(PolicyIssue.Malformed).size, 1)
    assert(clue(r.refusals.mkString).contains("no parameter list"))
  }

  test("a setter whose parameter is NOT the getter's type is not a pair, and is refused") {
    val r = run(
      """
      class Thing {
        public int getW() { return 1; }
        public void setW(String v) {}
      }
      """, "Thing#w" -> "getW/setW")
    assertUntouched(r, "Thing#getW", "getW")
    assert(clue(r.refusals.mkString("\n")).contains("not a pair"))
  }

  test("`surfaceFingerprint` is sorted and stable — two modules that agree compare equal") {
    val a = new BeanPropertyTransform(Map("b#y" -> "getY", "a#x" -> "getX/setX"))
    val b = new BeanPropertyTransform(Map("a#x" -> "getX/setX", "b#y" -> "getY"))
    assertEquals(a.surfaceFingerprint, b.surfaceFingerprint)
    assertEquals(a.surfaceFingerprint, "a#x=getX/setX,b#y=getY")
    assertEquals(new BeanPropertyTransform().surfaceFingerprint, "")
  }
