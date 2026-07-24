package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.IntToOpaqueTransform

/** The Int → opaque-type transform: a semantically-tagged `int` becomes an `opaque type` with
  * a synthesized companion, retyped everywhere it flows, wrapped at construction and unwrapped
  * where consumed as a plain int. Asserts the emitted Scala at each boundary. */
class IntToOpaqueTransformSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Sprite {
      |  private int[] order = new int[16];
      |  private int layer = 0;
      |  public int getLayer() { return layer; }
      |  public void setLayer(int layer) { this.layer = layer; }
      |  public void bump() { layer = layer + 1; }
      |  public boolean above(Sprite other) { return layer > other.getLayer(); }
      |  public int slot() { int l = layer; return order[l]; }
      |}
      |""".stripMargin

  // the ONLY hint is the `layer` FIELD; everything else is discovered by flow propagation.
  private val transform = new IntToOpaqueTransform("Layer", s => s.name == "layer" && !s.flags.isParam)
  private val before = SpoonTir.fromSource(src)
  private val after  = Pipeline.run(before, List(transform))
  private val out    = new TirEmitter(after).emit

  test("synthesizes the opaque type + companion") {
    assert(clue(out).contains("opaque type T = scala.Int"))
    assert(out.contains("object Layer"))
    assert(out.contains("def apply(v: scala.Int): Layer.T"))
    assert(out.contains("def unwrap(v: Layer.T): scala.Int"))
  }

  test("propagation discovers getter/setter/local from the field-only hint") {
    assert(out.contains("var layer: Layer.T"))          // the hint
    assert(out.contains("def getLayer(): Layer.T"))     // discovered: `return layer`
    assert(out.contains("def setLayer(layer: Layer.T)"))// discovered: `this.layer = layer`
    assert(out.contains("val l: Layer.T = this.layer")) // discovered: local `int l = layer`
  }

  test("wraps construction, unwraps consumption") {
    assert(out.contains("var layer: Layer.T = Layer(0)"))                 // literal wrapped
    assert(out.contains("this.layer = Layer(Layer.unwrap(this.layer) + 1)")) // arith unwrap + assign wrap
    assert(out.contains("Layer.unwrap(this.layer) > Layer.unwrap(other.getLayer())")) // comparison unwrap
    assert(out.contains("this.order(Layer.unwrap(l))"))                  // array index unwrap (of the local)
  }
