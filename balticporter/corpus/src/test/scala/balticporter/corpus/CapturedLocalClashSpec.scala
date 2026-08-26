package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.DecisionLog

/** A captured LOCAL that a nested class's member shadows — `TirEmitter.resolveCapturedLocalClashes`.
  *
  * The fourth face of CLAUDE.md §4.55, and the one that runs the other way: the name that has to
  * move is not the member but the capture. Java keeps methods and variables in separate namespaces,
  * so a method parameter `filter` and an anonymous class's `Response filter(a, b)` coexist and a
  * bare `filter` is unambiguously the parameter. Scala has one namespace and resolves
  * innermost-first, so the member wins and the capture becomes unnameable — `value filter is not a
  * member of (Item[?], Item[?]) => Response`, the compiler naming a function type nobody wrote.
  *
  * Measured on jbump's `World.check`, which is the shape the first test reproduces.
  *
  * The NEGATIVES carry as much weight as the positives here, and are why the pass is precise rather
  * than blanket: a local rename is invisible, but a PARAMETER rename changes the emitted signature,
  * so a pass that renamed on a name match alone would move public surface for no reason. Each
  * negative below names the over-approximation it forbids.
  */
class CapturedLocalClashSpec extends munit.FunSuite:

  private def emit(src: String): String = new TirEmitter(SpoonTir.fromSource(src)).emit

  /** the same emission, WITH the porter notes.
    *
    * An emitter renders only decisions its `notes` log already holds, and its own are a value fixed
    * at construction (`ownDecisions`) which the orchestrator records — so a bare emitter emits the
    * rename and no note for it, by design. This is `PortRun`'s two-emitter shape in miniature. */
  private def emitWithNotes(src: String): String =
    val p     = SpoonTir.fromSource(src)
    val log   = new DecisionLog
    log.recordAll(new TirEmitter(p).ownDecisions)
    new TirEmitter(p, notes = log).emit

  // -------------------------------------------------------------------------------------------
  // the defect
  // -------------------------------------------------------------------------------------------

  private val captured =
    """package demo;
      |interface Filter { String filter(String a); }
      |class World {
      |  String check(final Filter filter) {
      |    Filter inner = new Filter() {
      |      public String filter(String a) {
      |        if (filter == null) return a;
      |        return filter.filter(a);
      |      }
      |    };
      |    return inner.filter("x");
      |  }
      |}
      |""".stripMargin

  test("a captured parameter shadowed by the nested class's own member is renamed") {
    val out = emit(captured)
    assert(clue(out).contains("def check(filter$local: demo.Filter)"))
    // the MEMBER keeps java's name — it implements the interface and cannot move
    assert(out.contains("override def filter(a: java.lang.String)"))
  }

  test("…and every reference to it follows, because java resolved them STATICALLY") {
    val out = emit(captured)
    assert(clue(out).contains("filter$local == null"))
    assert(out.contains("filter$local.filter(a)"))
    // nothing is left naming the capture the way java did INSIDE the nested body, which is the
    // reference that did not compile
    assert(!out.contains("(filter == null)"))
  }

  test("the rename carries a porter note on the ENCLOSING METHOD (§4.575)") {
    val out = emitWithNotes(captured)
    // subject is the DECLARATION whose emitted form changed (§5.1) — a parameter has no `def` of
    // its own to sit above
    assert(clue(out).contains("clash=captured-local-vs-nested-member"))
    assert(out.contains("from=filter to=filter$local"))
    assert(out.indexOf("porter: renamed-member") < out.indexOf("def check("))
  }

  test("a local VARIABLE is caught too, not only a parameter") {
    val out = emit(
      """package demo;
        |interface Filter { String filter(String a); }
        |class W {
        |  Filter go() {
        |    final Filter filter = null;
        |    return new Filter() { public String filter(String a) { return filter.filter(a); } };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("filter$local"))
  }

  test("the shadowing member may be INHERITED rather than declared in the anonymous body") {
    // `Base.tag()` is what shadows the capture; the anonymous class declares only `run`.
    val out = emit(
      """package demo;
        |class Base { String tag() { return "b"; } void run() { } }
        |class W {
        |  void go(final String tag) {
        |    new Base() { public void run() { System.out.println(tag); } };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(tag$local: java.lang.String)"))
    assert(out.contains("println(tag$local)"))
  }

  // A METHOD-LOCAL named class (`class Holder { … }` as a statement) would shadow in exactly the
  // same way and is deliberately NOT tested here: the frontend refuses it outright
  // (`unsupported construct: statement CtClassImpl`, `SpoonTir.stmtKind`), so a test for it would
  // be a test of an engine gap one layer up, failing for a reason that has nothing to do with this
  // pass. The pass itself is indifferent — it reads `Tree.ClassDef` and `Tree.AnonClass` through
  // one traversal — so it will cover local classes on the day the frontend produces them.

  // -------------------------------------------------------------------------------------------
  // THE SECOND RULE — AMBIGUITY, which is not shadowing (ENGINE-LIMITS.md C16)
  //
  // Every test above has the body referencing the CAPTURE. Where the parent declares the name as a
  // FIELD, java binds the INHERITED MEMBER instead — probed against javac 22, and the same answer
  // through an anonymous body, a named local class and a grandparent — so no capture reference
  // exists and the first rule sees nothing. Scala 3 then calls the bare name AMBIGUOUS rather than
  // choosing either, and the outer declaration is what has to move.
  // -------------------------------------------------------------------------------------------

  private val ambiguous =
    """package demo;
      |class Base {
      |  final String html;
      |  Base(String html) { this.html = html; }
      |  void run() { }
      |}
      |class W {
      |  void go(final String html) {
      |    new Base(html) { public void run() { System.out.println(html); } };
      |  }
      |}
      |""".stripMargin

  test("a capture and an INHERITED FIELD of the same name — the capture moves") {
    val out = emit(ambiguous)
    assert(clue(out).contains("def go(html$local: java.lang.String)"))
  }

  test("…and the reference inside the body keeps naming the INHERITED member, as java bound it") {
    val out = emit(ambiguous)
    // the body's `html` is java's `this.html`; renaming the capture is what makes the bare name
    // resolve to it rather than to neither.
    assert(clue(out).contains("println(html)"))
    assert(!out.contains("println(html$local)"))
    // …while the constructor argument, which really IS the capture, follows the rename — the two
    // spellings sit four lines apart in one emitted method, which is the whole of the difference
    // between the outer declaration and the inherited member
    assert(clue(out).contains("html$local)"))
  }

  test("an inherited METHOD is ambiguous with an outer value too, though java's namespaces are not") {
    val out = emit(
      """package demo;
        |class Base { String tag() { return "b"; } void run() { } }
        |class W {
        |  void go(final String tag) {
        |    new Base() { public void run() { System.out.println(tag()); } };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(tag$local: java.lang.String)"))
  }

  test("the rename carries its OWN clash reason, so a reader can tell the two rules apart") {
    val out = emitWithNotes(ambiguous)
    assert(clue(out).contains("clash=captured-local-vs-inherited-member"))
    assert(out.contains("from=html to=html$local"))
  }

  test("a LOCAL is reached as well as a parameter — the capture need not be referenced at all") {
    val out = emit(
      """package demo;
        |class Base {
        |  final String html;
        |  Base(String html) { this.html = html; }
        |  void run() { }
        |}
        |class W {
        |  void go() {
        |    final String html = "x";
        |    new Base(html) { public void run() { System.out.println(html); } };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("html$local"))
  }

  // -------------------------------------------------------------------------------------------
  // NEGATIVES — a check that has never declined is not known to decide anything
  // -------------------------------------------------------------------------------------------

  test("NEGATIVE: MERE OUTER — the parent declares no such name, so nothing is ambiguous") {
    // The name is in an enclosing method's scope and NOWHERE ELSE. Probed: javac binds the capture
    // and scalac compiles it without complaint. Renaming here would move a parameter for nothing.
    val out = emit(
      """package demo;
        |class Base { void run() { } }
        |class W {
        |  void go(final String html) {
        |    new Base() { public void run() { System.out.println(html); } };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(html: java.lang.String)"))
    assert(!out.contains("html$local"))
  }

  test("NEGATIVE: the body DECLARES the name itself — scala's rule says INHERITED, and this is not") {
    // Probed: `new Bare { val html = …; … html … }` under an outer `html` compiles — the class's
    // own declaration wins, exactly as java's does. Only the first rule may fire here, and it
    // cannot, because nothing references the capture.
    val out = emit(
      """package demo;
        |class Base { void run() { } }
        |class W {
        |  void go(final String html) {
        |    new Base() {
        |      String html = "declared";
        |      public void run() { System.out.println(html); }
        |    };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(html: java.lang.String)"))
    assert(!out.contains("html$local"))
  }

  test("NEGATIVE: a method that encloses NO nested body moves nothing, whatever it inherits") {
    val out = emit(
      """package demo;
        |class Base { final String html; Base(String html) { this.html = html; } }
        |class W extends Base {
        |  W() { super("x"); }
        |  void go(final String html) { System.out.println(html); }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(html: java.lang.String)"))
    assert(!out.contains("$local"))
  }

  test("NEGATIVE: a capture the nested class does not shadow is untouched") {
    val out = emit(
      """package demo;
        |interface Filter { String filter(String a); }
        |class W {
        |  String go(final String prefix) {
        |    Filter f = new Filter() { public String filter(String a) { return prefix + a; } };
        |    return f.filter("x");
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(prefix: java.lang.String)"))
    assert(!out.contains("prefix$local"))
  }

  test("NEGATIVE: a name that COLLIDES but is never captured is untouched — the signature stays") {
    // `run` matches the anonymous class's member exactly, and nothing inside it reads the
    // parameter. Scala shadows a name nobody uses without complaint, so renaming here would move a
    // public parameter name for no reason at all.
    val out = emit(
      """package demo;
        |class W {
        |  void go(final Runnable run) {
        |    Runnable r = new Runnable() { public void run() { } };
        |    r.run();
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(run: java.lang.Runnable)"))
    assert(!out.contains("run$local"))
  }

  test("NEGATIVE: an enclosing FIELD is not this pass's business — java shadows it as scala does") {
    // The nested member wins inside the nested class in BOTH languages, and the enclosing field is
    // still reachable as `W.this.value`. Renaming it would change a real member's name.
    val out = emit(
      """package demo;
        |class W {
        |  int value = 1;
        |  int go() {
        |    java.util.concurrent.Callable<Integer> c =
        |      new java.util.concurrent.Callable<Integer>() { public Integer call() { return value; } };
        |    return value;
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("var value: scala.Int"))
    assert(!out.contains("value$local"))
  }

  test("NEGATIVE: a class with no nested class in sight moves nothing") {
    val out = emit(
      """package demo;
        |class W { int filter(int x) { return x; } int go(int filter) { return filter + 1; } }
        |""".stripMargin)
    assert(!clue(out).contains("$local"))
  }

  // -------------------------------------------------------------------------------------------
  // RESOLUTION ROOT — the parent's ClassDef is NOT in p.units (it comes from a resolution root,
  // as a dependent's base does). The fix: visibleMembers falls back to the symbol table.
  // -------------------------------------------------------------------------------------------

  /** Simulate a resolution-root parent: parse parent and child together, then remove the parent
    * from `units` while keeping all symbols. This is exactly the shape an ashley test creates:
    * `EntitySystem` from the main source set (resolution root) with an anonymous subclass in the
    * test method that has a local named `engine`. */
  private def emitWithResolutionRoot(parentSrc: String, childSrc: String): String =
    val full = SpoonTir.fromSources(List("Parent.java" -> parentSrc, "Child.java" -> childSrc))
    // remove the parent's ClassDef from units — simulates a resolution root where the parent is
    // in the symbol table but not in the traversed units
    val childUnits = full.units.filterNot { cd =>
      val n = full.symbolOf(cd.symbol).map(_.name).getOrElse("")
      n == "Base" || n == "Parent"
    }
    val narrowed = full.rebuilt(units = childUnits)
    new TirEmitter(narrowed).emit

  test("a parent from a RESOLUTION ROOT triggers the ambiguity rename") {
    // This is the E049 shape: the parent type (`Base`) is in the symbol table but NOT in p.units.
    // The anonymous subclass inherits `def x` from Base, and the enclosing method has a local `x`.
    // Before the fix, visibleMembers returned empty for resolution-root parents, so the ambiguity
    // was never detected.
    val out = emitWithResolutionRoot(
      """package demo;
        |class Base {
        |  String x() { return "b"; }
        |  void run() { }
        |}
        |""".stripMargin,
      """package demo;
        |class W {
        |  void go(final String x) {
        |    new Base() { public void run() { System.out.println(x()); } };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(x$local: java.lang.String)"))
  }

  test("a resolution-root parent's FIELD also triggers the ambiguity rename") {
    val out = emitWithResolutionRoot(
      """package demo;
        |class Parent {
        |  final String engine;
        |  Parent(String engine) { this.engine = engine; }
        |  void run() { }
        |}
        |""".stripMargin,
      """package demo;
        |class W {
        |  void go(final String engine) {
        |    new Parent(engine) { public void run() { System.out.println(engine); } };
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(engine$local: java.lang.String)"))
  }

  test("rule (1) also works for resolution-root parents: a captured local is renamed") {
    val out = emitWithResolutionRoot(
      """package demo;
        |interface Base { String filter(String a); }
        |""".stripMargin,
      """package demo;
        |class W {
        |  String go(final Base filter) {
        |    Base inner = new Base() {
        |      public String filter(String a) {
        |        return filter.filter(a);
        |      }
        |    };
        |    return inner.filter("x");
        |  }
        |}
        |""".stripMargin)
    assert(clue(out).contains("def go(filter$local: demo.Base)"))
  }
