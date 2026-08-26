package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** [[BeanPropertyTransform]] auto-detection — the `scope` parameter that scans a program for bean
  * accessor pairs following the Java bean convention, complementing the explicit `pairs` map. */
class BeanPropertyTransformSpec extends munit.FunSuite:

  // ---- pure helpers -------------------------------------------------------------------------

  test("propertyNameOf: getOpacity -> opacity") {
    assertEquals(BeanPropertyTransform.propertyNameOf("getOpacity"), Some("opacity"))
  }

  test("propertyNameOf: getName -> name") {
    assertEquals(BeanPropertyTransform.propertyNameOf("getName"), Some("name"))
  }

  test("propertyNameOf: getURL -> URL (two uppercase chars keep case)") {
    assertEquals(BeanPropertyTransform.propertyNameOf("getURL"), Some("URL"))
  }

  test("propertyNameOf: isReady -> ready") {
    assertEquals(BeanPropertyTransform.propertyNameOf("isReady"), Some("ready"))
  }

  test("propertyNameOf: isBig -> big") {
    assertEquals(BeanPropertyTransform.propertyNameOf("isBig"), Some("big"))
  }

  test("propertyNameOf: get -> None (too short)") {
    assertEquals(BeanPropertyTransform.propertyNameOf("get"), scala.None)
  }

  test("propertyNameOf: is -> None (too short)") {
    assertEquals(BeanPropertyTransform.propertyNameOf("is"), scala.None)
  }

  test("propertyNameOf: getx -> None (lowercase after prefix)") {
    assertEquals(BeanPropertyTransform.propertyNameOf("getx"), scala.None)
  }

  test("propertyNameOf: compute -> None (no prefix)") {
    assertEquals(BeanPropertyTransform.propertyNameOf("compute"), scala.None)
  }

  test("decapitalize: Opacity -> opacity") {
    assertEquals(BeanPropertyTransform.decapitalize("Opacity"), "opacity")
  }

  test("decapitalize: URL -> URL (two uppercase)") {
    assertEquals(BeanPropertyTransform.decapitalize("URL"), "URL")
  }

  test("decapitalize: X -> x (single char)") {
    assertEquals(BeanPropertyTransform.decapitalize("X"), "x")
  }

  test("decapitalize: empty -> empty") {
    assertEquals(BeanPropertyTransform.decapitalize(""), "")
  }

  // ---- end-to-end auto-detection ------------------------------------------------------------

  private case class Ran(before: Program, after: Program, phase: BeanPropertyTransform,
                         log: DecisionLog, idioms: IdiomLog = IdiomLog.discarding):
    def out: String = new TirEmitter(after).emit
    def named(fqn: String): Option[Symbol] = after.symbols.all.find(_.fullName == fqn)

  private def ran(java: String, phase: BeanPropertyTransform): Ran =
    val before   = SpoonTir.fromSource(java)
    val idioms   = new IdiomLog
    val rewrites = RewriteLog()
    val (after, log) = Pipeline.runTraced(before, List(phase),
      new PolicyBinder(before, before.members), balticporter.catalog.CatalogLog.discarding,
      rewrites, idioms)
    Ran(before, after, phase, log, idioms)

  private def nameOf(r: Ran, fqn: String): String =
    r.before.symbols.all.find(_.fullName == fqn).map(_.id)
      .flatMap(r.after.symbolOf).map(_.name).getOrElse(s"<no $fqn>")

  private def detectedConverted(r: Ran): List[IdiomCandidate] =
    r.idioms.all.filter(c => c.kind == IdiomKind.BeanDetect && c.verdict == IdiomVerdict.Converted)

  private def detectedRefused(r: Ran): List[IdiomCandidate] =
    r.idioms.all.filter(c => c.kind == IdiomKind.BeanDetect && c.verdict.lane == "refused")

  // -------------------------------------------------------------------------------------------
  // the no-op default
  // -------------------------------------------------------------------------------------------

  test("scope = Only(Set.empty) is a no-op — identical to an empty pairs map") {
    val src = """
      class Thing {
        private int w;
        public int getW() { return w; }
        public void setW(int v) { this.w = v; }
      }
    """
    val phase = new BeanPropertyTransform(scope = RuleScope.Only(Set.empty))
    val before = SpoonTir.fromSource(src)
    assert(phase.run(before) eq before, "Only(Set.empty) must return the same program")
  }

  // -------------------------------------------------------------------------------------------
  // auto-detection positives
  // -------------------------------------------------------------------------------------------

  test("a get/set pair in a scoped type is auto-detected and renamed") {
    val r = ran(
      """
      class Layer {
        private float opacity = 1.0f;
        public float getOpacity() { return opacity; }
        public void setOpacity(float o) { this.opacity = o; }
      }
      class Use {
        void go(Layer l) { l.setOpacity(l.getOpacity() + 1.0f); }
      }
      """,
      new BeanPropertyTransform(scope = RuleScope.Only(Set("Layer"))))
    assert(clue(r.out).contains("def opacity"))
    assert(r.out.contains("def opacity_="))
    assert(r.out.contains("l.opacity = l.opacity + 1.0f"))
    assertEquals(detectedConverted(r).size, 1)
    assert(detectedConverted(r).head.subject == "Layer#opacity")
  }

  test("a get-only method in scope is auto-detected as a getter-only property") {
    val r = ran(
      """
      class Info {
        public String getName() { return "x"; }
      }
      class Use { void go(Info i) { String s = i.getName(); } }
      """,
      new BeanPropertyTransform(scope = RuleScope.Only(Set("Info"))))
    assert(clue(r.out).contains("def name"))
    assert(clue(r.out).contains("i.name"))
    assert(!r.out.contains("_="))
    assertEquals(detectedConverted(r).size, 1)
  }

  test("an `is*` boolean getter is detected") {
    val r = ran(
      """
      class Flag {
        private boolean ready;
        public boolean isReady() { return ready; }
      }
      class Use { void go(Flag f) { boolean b = f.isReady(); } }
      """,
      new BeanPropertyTransform(scope = RuleScope.Only(Set("Flag"))))
    assert(clue(r.out).contains("def ready"))
    assertEquals(detectedConverted(r).size, 1)
  }

  test("a type OUTSIDE the scope is not scanned") {
    val r = ran(
      """
      class Inside { public int getW() { return 0; } }
      class Outside { public int getH() { return 0; } }
      """,
      new BeanPropertyTransform(scope = RuleScope.Only(Set("Inside"))))
    assert(clue(r.out).contains("def w"))
    assertEquals(nameOf(r, "Outside#getH"), "getH", "outside type must not be touched")
    assertEquals(detectedConverted(r).size, 1)
  }

  // -------------------------------------------------------------------------------------------
  // configured pairs override detection
  // -------------------------------------------------------------------------------------------

  test("a configured pair at the same key WINS over auto-detection") {
    val r = ran(
      """
      class Layer {
        private float opacity = 1.0f;
        public float getOpacity() { return opacity; }
        public void setOpacity(float o) { this.opacity = o; }
        public int getW() { return 0; }
      }
      """,
      new BeanPropertyTransform(
        pairs = Map("Layer#opacity" -> "getOpacity/setOpacity"),
        scope = RuleScope.Only(Set("Layer"))))
    // opacity was handled by the configured path, not auto-detection
    assert(clue(r.out).contains("def opacity"))
    // w was auto-detected
    assert(clue(r.out).contains("def w"))
    // only w is in the BeanDetect lane; opacity is in the configured path
    val detected = detectedConverted(r)
    assertEquals(detected.size, 1)
    assertEquals(detected.head.subject, "Layer#w")
  }

  // -------------------------------------------------------------------------------------------
  // refusals
  // -------------------------------------------------------------------------------------------

  test("a STATIC getter in scope is refused") {
    val r = ran(
      """
      class Cfg {
        public static int getW() { return 0; }
      }
      """,
      new BeanPropertyTransform(scope = RuleScope.Only(Set("Cfg"))))
    assertEquals(nameOf(r, "Cfg#getW"), "getW")
    val refused = detectedRefused(r)
    assertEquals(refused.size, 1)
    assert(clue(refused.head.verdict.render).contains("Static"))
  }

  test("a void getter in scope is refused") {
    val r = ran(
      """
      class Cfg {
        public void getW() {}
      }
      """,
      new BeanPropertyTransform(scope = RuleScope.Only(Set("Cfg"))))
    assertEquals(nameOf(r, "Cfg#getW"), "getW")
    val refused = detectedRefused(r)
    assertEquals(refused.size, 1)
    assert(clue(refused.head.verdict.render).contains("VoidGetter"))
  }

  test("a FLUENT setter in scope refuses the pair") {
    val r = ran(
      """
      class Builder {
        private int w;
        public int getW() { return w; }
        public Builder setW(int v) { this.w = v; return this; }
      }
      """,
      new BeanPropertyTransform(scope = RuleScope.Only(Set("Builder"))))
    assertEquals(nameOf(r, "Builder#getW"), "getW")
    val refused = detectedRefused(r)
    assertEquals(refused.size, 1)
    assert(clue(refused.head.verdict.render).contains("FluentSetter"))
  }

  // -------------------------------------------------------------------------------------------
  // fingerprint
  // -------------------------------------------------------------------------------------------

  test("surfaceFingerprint includes scope when non-default") {
    val a = new BeanPropertyTransform(scope = RuleScope.Only(Set("com.foo")))
    assert(clue(a.surfaceFingerprint).contains("detect="))
    assert(a.surfaceFingerprint.contains("only:com.foo"))
  }

  test("surfaceFingerprint omits scope segment at the default") {
    val a = new BeanPropertyTransform()
    assertEquals(a.surfaceFingerprint, "")
    val b = new BeanPropertyTransform(Map("a#x" -> "getX"))
    assert(!clue(b.surfaceFingerprint).contains("detect="))
  }

  test("surfaceFingerprint includes both pairs and scope") {
    val a = new BeanPropertyTransform(Map("a#x" -> "getX"),
      scope = RuleScope.Only(Set("com.foo")))
    assert(clue(a.surfaceFingerprint).contains("a#x="))
    assert(a.surfaceFingerprint.contains("detect=only:com.foo"))
  }

  // -------------------------------------------------------------------------------------------
  // idiomKinds
  // -------------------------------------------------------------------------------------------

  test("idiomKinds includes BeanDetect only when scope is active") {
    val noScope = new BeanPropertyTransform()
    assertEquals(noScope.idiomKinds, Set(IdiomKind.BeanCollapse))
    val withScope = new BeanPropertyTransform(scope = RuleScope.Only(Set("com.foo")))
    assertEquals(withScope.idiomKinds, Set(IdiomKind.BeanCollapse, IdiomKind.BeanDetect))
  }

  // -------------------------------------------------------------------------------------------
  // merge
  // -------------------------------------------------------------------------------------------

  test("mergedWith composes two Only scopes by union") {
    val a = new BeanPropertyTransform(scope = RuleScope.Only(Set("com.a")))
    val b = new BeanPropertyTransform(scope = RuleScope.Only(Set("com.b")))
    val merged = a.mergedWith(b)
    assert(merged.isRight)
    val mp = merged.toOption.get.phase.asInstanceOf[BeanPropertyTransform]
    assert(clue(mp.surfaceFingerprint).contains("com.a"))
    assert(mp.surfaceFingerprint.contains("com.b"))
  }

  test("mergedWith refuses mixed Only/Everywhere scopes") {
    val a = new BeanPropertyTransform(scope = RuleScope.Only(Set("com.a")))
    val b = new BeanPropertyTransform(scope = RuleScope.Everywhere(Set("com.b")))
    val merged = a.mergedWith(b)
    assert(merged.isLeft)
    assert(clue(merged.swap.toOption.get).contains("disagrees"))
  }

  // -------------------------------------------------------------------------------------------
  // subjects
  // -------------------------------------------------------------------------------------------

  test("subjects includes scope entries") {
    val a = new BeanPropertyTransform(
      pairs = Map("com.foo.Bar#opacity" -> "getOpacity"),
      scope = RuleScope.Only(Set("com.baz")))
    assert(a.subjects.contains("com.foo.Bar"))
    assert(a.subjects.contains("com.baz"))
  }
