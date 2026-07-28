package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}
import balticporter.transform.MutableParamsTransform

/** Java ANONYMOUS CLASSES, end to end.
  *
  * This exists because the body of every one of them was silently DISCARDED for the project's
  * entire history: `SpoonTir.ctorCall` read `CtConstructorCall` and never asked whether the node
  * was the `CtNewClass` subtype. The result compiled — a listener with no overrides is a valid
  * listener — so every libGDX button did nothing when clicked while the gate stayed green. A
  * regression here is invisible to a compile gate, which is exactly why it is pinned by a test.
  */
class AnonymousClassSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Listener {
      |  public void clicked(int x, int y) { }
      |  public int  tapCount() { return 0; }
      |}
      |class Widget {
      |  boolean disabled;
      |  Listener listener;
      |  void addListener(Listener l) { }
      |  boolean isDisabled() { return disabled; }
      |  void setChecked(boolean v) { }
      |  void register(final String label) {
      |    addListener(listener = new Listener() {
      |      int hits;
      |      public void clicked(int x, int y) {
      |        if (isDisabled()) return;
      |        hits = hits + 1;
      |        setChecked(label != null);
      |        disabled = false;
      |      }
      |      public int helper(int n) { n = n + 1; return n; }
      |    });
      |  }
      |  java.util.Comparator<String> byLength() {
      |    return new java.util.Comparator<String>() {
      |      public int compare(String a, String b) { return a.length() - b.length(); }
      |    };
      |  }
      |}
      |""".stripMargin

  private val raw     = SpoonTir.fromSource(src)
  private val program = Pipeline.run(raw, List(new MutableParamsTransform))
  private val out     = new TirEmitter(program).emit

  test("the anonymous class's body is emitted, not dropped") {
    assert(clue(out).contains("new demo.Listener()"))
    assert(out.contains("def clicked(x: scala.Int, y: scala.Int)"))
    assert(out.contains("def helper("))
    assert(out.contains("var hits: scala.Int"))          // an anonymous class's own field
  }

  test("a method redefining a CONCRETE inherited member carries `override`") {
    // Scala rejects it without one (E164) — and E164 is reported by RefChecks, which never runs
    // while any typer error remains, so nothing else in this project would catch its absence.
    assert(clue(out).contains("override def clicked("))
    assert(out.contains("override def compare("))        // permitted on an abstract member too
    assert(!out.contains("override def helper("))        // overrides nothing — must NOT carry it
  }

  test("an enclosing member reached from inside the body is qualified `Outer.this`") {
    // a bare `this` inside a Scala anonymous class is the ANONYMOUS instance, as in Java
    assert(clue(out).contains("Widget.this.isDisabled()"))
    assert(out.contains("Widget.this.setChecked("))
    assert(out.contains("Widget.this.disabled = false"))
  }

  test("a captured local is closed over, needing no lowering") {
    assert(clue(out).contains("label != null"))
  }

  test("a parameter reassigned inside an anonymous method still becomes a var") {
    // `MutableParamsTransform` walked class bodies by hand and never saw these
    assert(clue(out).contains("var n: scala.Int = n$arg"))
  }

  test("OmissionCheck reports nothing dropped for a fully translated body") {
    assertEquals(OmissionCheck.droppedAnonMembers(program), Nil)
  }
