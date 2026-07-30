package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OpaqueSpec, Pipeline, RuleScope}
import balticporter.transform.PrimitiveToOpaqueTransform

/** The primitive → opaque-type transform: a semantically-tagged primitive becomes an `opaque type`
  * with a synthesized companion, retyped everywhere it flows, wrapped at construction and unwrapped
  * where consumed as a plain value. Asserts the emitted Scala at each boundary.
  *
  * The first half is the `Int` case as it has always behaved, unchanged by the generalisation; the
  * second half is what the generalisation added — another primitive, an explicit definition site, a
  * scope fencing the propagation, and two specs colliding.
  */
class PrimitiveToOpaqueTransformSpec extends munit.FunSuite:

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
  private def layerSpec(scope: RuleScope = RuleScope.Everywhere()) =
    OpaqueSpec(fqn = "Layer", hints = s => s.name == "layer" && !s.flags.isParam, scope = scope)

  private val transform = new PrimitiveToOpaqueTransform(layerSpec())
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

  // -------------------------------------------------------------------------
  // decision provenance
  // -------------------------------------------------------------------------

  test("every retyped DECLARATION leaves a §1(c) row — nothing else says which int was tagged") {
    // `Pipeline.run` drains each phase's buffer into a log it discards, so the trace variant is
    // the one a spec can read (a phase instance reused across two translations must not report the
    // first run's decisions as the second's).
    val ph  = new PrimitiveToOpaqueTransform(layerSpec())
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(ph))._2
    val ds  = log.of(balticporter.tir.Decision.Kind.RetypedSignature)

    // (c) — CLAUDE.md §1's canonical library rule. WHICH primitives are really a domain value is
    // knowledge about one library, so the row must send its reader to that library's own rule and
    // not to a manifest key or to the engine.
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == balticporter.tir.Reason.LibraryRule("primitive->opaque:Layer")))
    assertEquals(ds.head.reason.section, "§1(c) LIBRARY RULE")

    val by = ds.map(d => d.subjectFqn.substring(d.subjectFqn.lastIndexOf('#') + 1)).toSet
    // the HINT and a member propagation discovered from it
    assert(clue(by).contains("layer"))
    assert(by.contains("getLayer"))
    // …and NOT the local `l` or the `setLayer` parameter: both are seeds, and both live inside a
    // method whose own row already carries the move (`Decision.isDeclaration`).
    assert(ds.forall(!_.subjectFqn.endsWith("#l")), clue(ds.map(_.subjectFqn)))
    assert(ds.map(_.detail("to")).forall(_.contains("Layer")), clue(ds.map(_.detail("to"))))
  }

  test("a program with no tagged int records nothing — an unmatched hint is silent as well as inert") {
    val ph  = new PrimitiveToOpaqueTransform(OpaqueSpec(fqn = "Layer", hints = _.name == "noSuchField"))
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(ph))._2
    assertEquals(log.all, Nil)
  }

  test("wraps construction, unwraps consumption") {
    assert(out.contains("var layer: Layer.T = Layer(0)"))                 // literal wrapped
    assert(out.contains("this.layer = Layer(Layer.unwrap(this.layer) + 1)")) // arith unwrap + assign wrap
    assert(out.contains("Layer.unwrap(this.layer) > Layer.unwrap(other.getLayer())")) // comparison unwrap
    assert(out.contains("this.order(Layer.unwrap(l))"))                  // array index unwrap (of the local)
  }

  // -------------------------------------------------------------------------
  // …and what the generalisation added
  // -------------------------------------------------------------------------

  private val boxes =
    """package demo;
      |class Box {
      |  private float width = 0f;
      |  public float getWidth() { return width; }
      |}
      |""".stripMargin

  test("a NON-Int primitive works the same way — the mechanism never depended on Int") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Width", hints = s => s.name == "width" && !s.flags.isParam,
      underlying = OpaqueSpec.Primitive.Float))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(boxes), List(ph))).emit
    assert(clue(emitted).contains("opaque type T = scala.Float"))
    assert(emitted.contains("def apply(v: scala.Float): Width.T"))
    assert(emitted.contains("var width: Width.T = Width("))
    assert(emitted.contains("def getWidth(): Width.T"), "propagation is indifferent to the primitive")
  }

  test("the DEFINITION SITE is the spec's FQN — the object lands in that package") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "sge.gl.Layer", hints = s => s.name == "layer" && !s.flags.isParam))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(ph))).emit
    assert(clue(emitted).contains("package sge.gl"))
    assert(emitted.contains("object Layer"))
    assert(emitted.contains("sge.gl.Layer.T"), "every reference names the type by its FQN")
  }

  test("an fqn that cannot be a package path is REFUSED at construction, not silently emitted") {
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "", hints = _ => true))
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "a..b", hints = _ => true))
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "a.B#c", hints = _ => true))
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "a.B$C", hints = _ => true))
  }

  test("a primitive an opaque type cannot be a view of is REFUSED loudly, naming what is available") {
    val e = intercept[IllegalArgumentException](OpaqueSpec.Primitive.fromScalaName("java.lang.String"))
    assert(clue(e.getMessage).contains("scala.Int"))
    assertEquals(OpaqueSpec.Primitive.fromScalaName("scala.Long"), OpaqueSpec.Primitive.Long)
    assertEquals(OpaqueSpec.Primitive.fromScalaName("Char"), OpaqueSpec.Primitive.Char)
  }

  // -- the scope fences propagation -----------------------------------------

  private val twoTypes =
    """package demo;
      |class Sprite {
      |  private int layer = 0;
      |  public int getLayer() { return layer; }
      |  public void feed(Meter m) { m.take(layer); }
      |}
      |class Meter {
      |  private int reading = 0;
      |  public void take(int v) { reading = v; }
      |}
      |""".stripMargin

  test("an unfenced propagation crosses type boundaries — which is exactly why a fence exists") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = s => s.fullName == "demo.Sprite#layer"))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(clue(emitted).contains("var reading: Layer.T"), "one hint reached the other class")
  }

  test("RuleScope.Only fences it — the propagation stops at the boundary the port drew") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = s => s.fullName == "demo.Sprite#layer",
      scope = RuleScope.Only(Set("demo.Sprite"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(clue(emitted).contains("var layer: Layer.T"))
    assert(emitted.contains("var reading: scala.Int"), "Meter is outside the fence and keeps the primitive")
  }

  test("RuleScope.Everywhere(except) fences it from the other side") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = s => s.fullName == "demo.Sprite#layer",
      scope = RuleScope.Everywhere(Set("demo.Meter"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(clue(emitted).contains("var layer: Layer.T"))
    assert(emitted.contains("var reading: scala.Int"))
  }

  test("a hint OUTSIDE the fence does not fire — a fence a named entry could step over is not one") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = s => s.fullName == "demo.Meter#reading",
      scope = RuleScope.Only(Set("demo.Sprite"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(!clue(emitted).contains("opaque type"), "no seed fired, so nothing was minted")
  }

  // -- two specs in one pipeline --------------------------------------------

  test("two specs COMPOSE when their propagated seed sets are disjoint") {
    val layers = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = s => s.fullName == "demo.Sprite#layer",
      scope = RuleScope.Only(Set("demo.Sprite"))))
    val meters = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Reading", hints = s => s.fullName == "demo.Meter#reading",
      scope = RuleScope.Only(Set("demo.Meter"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(layers, meters))).emit
    assert(clue(emitted).contains("var layer: Layer.T"))
    assert(emitted.contains("var reading: Reading.T"))
  }

  test("two specs whose seed sets OVERLAP FAIL THE RUN, naming the symbol and both specs") {
    // Unfenced, `Sprite#layer` flows through `Meter.take`'s parameter into `Meter#reading`, so both
    // specs claim `reading`. Silently, the second instance would find it already retyped, decline
    // it as ineligible, and emit a port with half of `Reading` missing — a green compile, no count
    // moved, and no row anywhere saying so.
    val layers = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = s => s.fullName == "demo.Sprite#layer"))
    val meters = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Reading", hints = s => s.fullName == "demo.Meter#reading"))
    val e = intercept[IllegalStateException](
      Pipeline.run(SpoonTir.fromSource(twoTypes), List(layers, meters)))
    assert(clue(e.getMessage).contains("demo.Meter#reading"))
    assert(e.getMessage.contains("Layer"))
    assert(e.getMessage.contains("Reading"))
    assert(e.getMessage.contains("§1(c)"), "the reader's first question is which repository the fix is in")
  }
