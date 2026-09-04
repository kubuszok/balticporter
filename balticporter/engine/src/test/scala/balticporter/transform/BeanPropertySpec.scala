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

  private case class Ran(before: Program, after: Program, phase: BeanPropertyTransform,
                         log: DecisionLog, idioms: IdiomLog = IdiomLog.discarding,
                         rewrites: RewriteLog = RewriteLog.discarding):
    def out: String = new TirEmitter(after).emit
    def refusals: List[String] = phase.policyReport.findings.map(_.detail)
    def named(fqn: String): Option[Symbol] = after.symbols.all.find(_.fullName == fqn)

  private def run(java: String, pairs: (String, String)*): Ran =
    ran(java, new BeanPropertyTransform(pairs.toMap))

  /** THE IDIOM LOG IS DRAINED AT THE PHASE BOUNDARY, so a fixture that reads the phase's own buffer
    * afterwards reads an empty one — `Pipeline.runTraced` clears it precisely so a phase reused
    * across two translations cannot report the first run's candidates as the second's. Every
    * fixture therefore owns the log it asserts on, which is also the shape a run has. */
  private def ran(java: String, phase: BeanPropertyTransform): Ran =
    val before   = SpoonTir.fromSource(java)
    val idioms   = new IdiomLog
    val rewrites = RewriteLog()
    val (after, log) = Pipeline.runTraced(before, List(phase),
      new PolicyBinder(before, before.members), balticporter.catalog.CatalogLog.discarding,
      rewrites, idioms)
    Ran(before, after, phase, log, idioms, rewrites)

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
    assertEquals(a.surfaceFingerprint, "a#x=getX/setX>def-pair,b#y=getY>def-pair")
    assertEquals(new BeanPropertyTransform().surfaceFingerprint, "")
  }

  test("…and two configurations differing ONLY in `target` do NOT compare equal (CT9)") {
    val d = new BeanPropertyTransform(Map("a#x" -> "getX/setX"))
    val v = new BeanPropertyTransform(Map("a#x" -> "getX/setX"),
                                      Map("a#x" -> BeanPropertyTransform.Target.Var))
    assertNotEquals(d.surfaceFingerprint, v.surfaceFingerprint)
  }

  // -------------------------------------------------------------------------------------------
  // THE `var`/`val` COLLAPSE — DESIGN.md §8.5. One positive per shape, and one negative per guard:
  // an idiom transform's safety argument IS its refusal enumeration, so a guard with no fixture is
  // a claim nothing checks (`CLAUDE.md` §3).
  // -------------------------------------------------------------------------------------------

  private def collapse(java: String, target: BeanPropertyTransform.Target,
                       pairs: (String, String)*): Ran =
    ran(java, new BeanPropertyTransform(pairs.toMap, pairs.map((k, _) => k -> target).toMap))

  /** the guard a run declined every configured pair under — the `idiom(refused)` row's own string,
    * read from the log the run owns rather than re-derived, because the phase is the one place that
    * holds both halves at the moment it files (§4.6, K2.5). */
  private def guards(r: Ran): List[String] =
    r.idioms.all.collect { case IdiomCandidate(_, IdiomVerdict.Refused(g, _), _, _, _) => g }

  private def converted(r: Ran): Int =
    r.idioms.all.count(_.verdict == IdiomVerdict.Converted)

  private val varSrc =
    """
    class Layer {
      private String name = "";
      public String getName() { return name; }
      public void setName(String n) { this.name = n; }
      void rename() { this.name = this.name + "!"; }
    }
    class Use {
      void go(Layer l) { l.setName(l.getName() + "x"); }
    }
    """

  test("a trivial get/set pair over one field becomes a public `var`, and BOTH accessors go") {
    val r = collapse(varSrc, BeanPropertyTransform.Target.Var, "Layer#name" -> "getName/setName")
    assertEquals(r.phase.policyReport.findings, Nil, r.phase.policyReport.render)
    assertEquals(converted(r), 1)
    assert(clue(r.out).contains("var name: java.lang.String = \"\""))
    assertEquals(r.out.linesIterator.count(_.contains("def name")), 0)
    // …and the `$field` noise the emitter's §4.55 pass used to leave behind is GONE with it: there
    // is no method called `name` any more, so there is no clash to resolve.
    assert(!clue(r.out).contains("name$field"))
  }

  test("the collapse RETYPES the surviving getter — `Patch.retyped`, never a drop plus a mint") {
    // The two emit the same text and only one of them is visible to anything: a drop-plus-mint has
    // no `info` on either side of the phase, so `Pipeline.runTraced` records nothing, the phase
    // owes no `accountedBy` lane, and every unrewritten usage is invisible. Asserted on the PATCH
    // and not on the emitted text, which is exactly the distinction that makes it worth pinning.
    val r = collapse(varSrc, BeanPropertyTransform.Target.Var, "Layer#name" -> "getName/setName")
    val before = r.before
    val patch = r.rewrites.all.find(_.phase == "bean-properties")
    assert(clue(patch).isDefined, "a collapse that records no patch owes no lane and counts nothing")
    assertEquals(patch.get.accountedBy, Set(IdiomCheck.Residue))
    val getter = before.symbols.all.find(_.fullName == "Layer#getName").get.id
    assert(clue(patch.get.retyped).contains(getter))
  }

  test("the in-class field reads and writes route through the property, and so do the call sites") {
    val r = collapse(varSrc, BeanPropertyTransform.Target.Var, "Layer#name" -> "getName/setName")
    assert(clue(r.out).contains("this.name = this.name + \"!\""))
    assert(clue(r.out).contains("l.name = l.name + \"x\""))
    // …and NOTHING is left unrewritten: the residue lane is the phase's own check on that claim.
    assertEquals(r.phase.candidates.all.count(_.verdict.lane == "residue"), 0)
  }

  test("a get-only entry over storage NOTHING writes becomes a `val`") {
    val r = collapse(
      """
      class Map0 {
        private String props = "p";
        public String getProps() { return props; }
      }
      """, BeanPropertyTransform.Target.Val, "Map0#props" -> "getProps")
    assertEquals(converted(r), 1)
    assert(clue(r.out).contains("val props: java.lang.String = \"p\""))
  }

  test("a COMPUTED getter is refused — collapsing it would change what a read computes") {
    val r = collapse(
      """
      class L {
        private float o = 1.0f;
        private L parent;
        public float getO() { if (parent != null) return o * parent.getO(); return o; }
        public void setO(float v) { this.o = v; }
      }
      """, BeanPropertyTransform.Target.Var, "L#o" -> "getO/setO")
    assertEquals(guards(r), List("ComputedBody"))
    assert(clue(r.out).contains("def o"), "a refused collapse degenerates to the def-pair")
  }

  test("a VALIDATING setter is refused for the same reason, at the other accessor") {
    val r = collapse(
      """
      class L {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { if (v < 0) throw new RuntimeException("no"); this.w = v; }
      }
      """, BeanPropertyTransform.Target.Var, "L#w" -> "getW/setW")
    assertEquals(guards(r), List("ComputedBody"))
  }

  test("a getter and a setter over DIFFERENT fields are refused — there is no single `var`") {
    val r = collapse(
      """
      class L {
        private int a; private int b;
        public int getA() { return a; }
        public void setA(int v) { this.b = v; }
      }
      """, BeanPropertyTransform.Target.Var, "L#a" -> "getA/setA")
    assertEquals(guards(r), List("SplitFields"))
  }

  test("a SUBCLASS overriding the accessor refuses the whole component — a `var` has no override") {
    val r = collapse(
      """
      class B {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { this.w = v; }
      }
      class S extends B {
        public int getW() { return 7; }
      }
      """, BeanPropertyTransform.Target.Var, "B#w" -> "getW/setW")
    assert(clue(guards(r)).contains("OverriddenBelow") || clue(guards(r)).contains("ConcreteRelative"))
  }

  test("a `val`'s decision records the SECOND reflective fact — its backing field is `final` and\n" +
       "     java's was not") {
    // §8.5's guard 5 records that the JVM METHOD NAMES move. A `val` moves one more thing, at the
    // FIELD and in the other direction: `MutableStorage` asks for a declaration initialiser and no
    // assignment IN THIS PROGRAM — never for java's `final` keyword, deliberately — so the java
    // field routinely was not final and the emitted one is. A reflective writer (`setAccessible` +
    // `Field.set`) that worked against java's does not work against this one.
    val r = collapse(
      """
      class V {
        private final java.lang.String n = "x";
        public java.lang.String getN() { return n; }
      }
      """, BeanPropertyTransform.Target.Val, "V#n" -> "getN")
    val d = r.log.all.find(_.kind == Decision.Kind.CollapsedProperty).get
    assertEquals(d.detail.get("form"), Some("val"))
    assert(clue(d.detail("why")).contains("`final` on the JVM"))
    assert(d.detail("why").contains("setAccessible"))
  }

  test("…and a `var`'s does NOT — its field is not final, and an untrue note is worse than none") {
    val r = collapse(varSrc, BeanPropertyTransform.Target.Var, "Layer#name" -> "getName/setName")
    val d = r.log.all.find(_.kind == Decision.Kind.CollapsedProperty).get
    assertEquals(d.detail.get("form"), Some("var"))
    assert(!clue(d.detail("why")).contains("final"))
  }

  test("…and one TWO levels down does too — the direction test was on DIRECT parents only") {
    // `overriddenBelow` asked whether the other declaration's owner names THIS owner as a parent —
    // one hop. A re-declaration two levels down names the class in BETWEEN, so it answered "nothing
    // below" about a subclass that really does re-declare the member, and the collapse emitted a
    // `var` under it. Made ABSTRACT so `concreteRelative` cannot catch it by accident: the belt is
    // the whole of the test, and a fixture the other guard also declines would prove nothing.
    val r = collapse(
      """
      class B {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { this.w = v; }
      }
      class M extends B { }
      abstract class S extends M {
        public abstract int getW();
      }
      """, BeanPropertyTransform.Target.Var, "B#w" -> "getW/setW")
    assertEquals(clue(guards(r)), List("OverriddenBelow"))
  }

  test("an INTERFACE above with no subclass below COLLAPSES, and the trait keeps its abstract pair") {
    val r = collapse(
      """
      interface HasW {
        int getW();
        void setW(int v);
      }
      class B implements HasW {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { this.w = v; }
      }
      """, BeanPropertyTransform.Target.Var, "B#w" -> "getW/setW")
    assertEquals(converted(r), 1)
    assert(clue(r.out).contains("def w: scala.Int"), "the interface keeps the abstract getter")
    assert(clue(r.out).contains("def w_="), "…and the abstract setter")
    assert(clue(r.out).contains("var w: scala.Int"), "…which the class's `var` implements")
  }

  test("a CONCRETE accessor above is refused — a `var` implements an abstract member, never an\n" +
       "     override of a concrete one") {
    val r = collapse(
      """
      class P {
        public int getW() { return 0; }
        public void setW(int v) {}
      }
      class C extends P {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { this.w = v; }
      }
      """, BeanPropertyTransform.Target.Var, "C#w" -> "getW/setW")
    assertEquals(guards(r), List("ConcreteRelative"))
  }

  test("a PRIVATE field with PUBLIC accessors takes the ACCESSORS' visibility, never the field's") {
    // The field is the implementation and the pair is the surface: taking the field's `private`
    // would silently narrow the port's API with a green compile.
    val r = collapse(varSrc, BeanPropertyTransform.Target.Var, "Layer#name" -> "getName/setName")
    assert(!clue(r.out).linesIterator.exists(l => l.contains("var name") && l.contains("private")))
  }

  test("`target = \"var\"` on a GET-ONLY entry is refused — it would publish a writer java lacked") {
    val r = collapse(
      """
      class L { private int w = 1; public int getW() { return w; } }
      """, BeanPropertyTransform.Target.Var, "L#w" -> "getW")
    assertEquals(guards(r), List("VarWithoutSetter"))
  }

  test("`target = \"val\"` on a pair WITH a setter is refused — it would delete a writer java had") {
    val r = collapse(varSrc, BeanPropertyTransform.Target.Val, "Layer#name" -> "getName/setName")
    assertEquals(guards(r), List("ValWithSetter"))
  }

  test("`target = \"val\"` over storage SOMETHING writes is refused, whatever java's `final` says") {
    val r = collapse(
      """
      class L {
        private int w = 1;
        public int getW() { return w; }
        void bump() { this.w = this.w + 1; }
      }
      """, BeanPropertyTransform.Target.Val, "L#w" -> "getW")
    assertEquals(guards(r), List("MutableStorage"))
  }

  test("…and `val` over storage the CONSTRUCTOR fills is refused too — the keyword would be the\n" +
       "     constructor funnel's answer and this phase cannot see it") {
    val r = collapse(
      """
      class L {
        private final int w;
        public L(int v) { this.w = v; }
        public int getW() { return w; }
      }
      """, BeanPropertyTransform.Target.Val, "L#w" -> "getW")
    assertEquals(guards(r), List("MutableStorage"))
  }

  test("a pair the port has NOT asked for is a counted DENOMINATOR row, not a silence") {
    // `refused = 0` is a bar a run could hold by converting nothing, so an entry with no `target`
    // still files — and it files the guard that says the collapse COULD have applied, which is the
    // number a maintainer deciding whether to widen an enablement reads.
    val r = run(varSrc, "Layer#name" -> "getName/setName")
    assertEquals(guards(r), List("NotRequested"))
    assertEquals(converted(r), 0)
  }

  test("a pair the DEF-PAIR path refused files `PairRefused`, never a collapse verdict") {
    val r = collapse(
      """
      class Builder {
        private int w;
        public int getW() { return w; }
        public Builder setW(int v) { this.w = v; return this; }
      }
      """, BeanPropertyTransform.Target.Var, "Builder#w" -> "getW/setW")
    assertEquals(guards(r), List("PairRefused"))
  }

  test("declaring the collapse AND `public-field-accessors` over one type is a contradiction") {
    // K21 face 2 PUTS java-bean names on a field for a reflective framework to find; the collapse
    // TAKES them off. The two policies are asked for separately and only the run sees both, so the
    // refusal is what stops a port getting neither.
    val r = ran(varSrc, new BeanPropertyTransform(Map("Layer#name" -> "getName/setName"),
                          Map("Layer#name" -> BeanPropertyTransform.Target.Var),
                          RuleScope.Only(Set("Layer"))))
    assertEquals(guards(r), List("ExposedField"))
    assert(clue(r.out).contains("def name"), "a refused collapse degenerates to the def-pair")
  }

  test("every collapse guard has a WHY that says whether the refusal is permanent") {
    BeanCollapse.Guard.values.foreach { g =>
      assert(clue(g.why).length > 40, s"$g")
    }
  }

  test("a collapsed property carries a PORTER NOTE naming the JVM methods that moved") {
    val r = collapse(varSrc, BeanPropertyTransform.Target.Var, "Layer#name" -> "getName/setName")
    val d = r.log.all.filter(_.kind == Decision.Kind.CollapsedProperty)
    assertEquals(clue(d).size, 1)
    assertEquals(d.head.detail("was"), "getName() setName()")
    assertEquals(d.head.detail("form"), "var")
  }
