package balticporter.corpus.demo

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline
import balticporter.transform.IntToOpaqueTransform

/** Demonstrates the Int → opaque-type transform end-to-end: a class with a semantically-tagged
  * `int layer` becomes an `opaque type Layer.T = Int` with a synthesized companion, retyped
  * everywhere it flows, wrapped at construction and unwrapped where consumed as a plain int.
  *
  *   corpus/runMain balticporter.corpus.demo.OpaqueDemo
  */
object OpaqueDemo:

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

  /** the ONLY hint is the field `layer`. Everything else — `getLayer`'s return, `setLayer`'s
    * param, and the local `l` in `slot` — is DISCOVERED by flow propagation. */
  private val transform = new IntToOpaqueTransform("Layer", s => s.name == "layer" && !s.flags.isParam)

  def main(args: Array[String]): Unit =
    val before = SpoonTir.fromSource(src)
    println("// ===== BEFORE =====\n")
    println(new TirEmitter(before).emit)

    val after = Pipeline.run(before, List(transform))
    println("\n// ===== AFTER (int -> opaque type Layer) =====\n")
    println(new TirEmitter(after).emit)
