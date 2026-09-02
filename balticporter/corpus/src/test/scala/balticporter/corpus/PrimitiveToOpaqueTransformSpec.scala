package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Flags, OpaqueSpec, Pipeline, PolicyBinder, RuleScope, RunScope, SymId, Symbol, TypeRepr}
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
    OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"), scope = scope)

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
    val ph  = new PrimitiveToOpaqueTransform(OpaqueSpec(fqn = "Layer", hints = Set("demo.noSuchField")))
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
      fqn = "Width", hints = Set("demo.Box#width"),
      underlying = OpaqueSpec.Primitive.Float))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(boxes), List(ph))).emit
    assert(clue(emitted).contains("opaque type T = scala.Float"))
    assert(emitted.contains("def apply(v: scala.Float): Width.T"))
    assert(emitted.contains("val width: Width.T = Width("))
    assert(emitted.contains("def getWidth(): Width.T"), "propagation is indifferent to the primitive")
  }

  test("the DEFINITION SITE is the spec's FQN — the object lands in that package") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "sge.gl.Layer", hints = Set("demo.Sprite#layer")))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(ph))).emit
    assert(clue(emitted).contains("package sge.gl"))
    assert(emitted.contains("object Layer"))
    assert(emitted.contains("sge.gl.Layer.T"), "every reference names the type by its FQN")
  }

  test("an fqn that cannot be a package path is REFUSED at construction, not silently emitted") {
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "", hints = Set("x")))
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "a..b", hints = Set("x")))
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "a.B#c", hints = Set("x")))
    intercept[IllegalArgumentException](OpaqueSpec(fqn = "a.B$C", hints = Set("x")))
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
      fqn = "Layer", hints = Set("demo.Sprite#layer")))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(clue(emitted).contains("var reading: Layer.T"), "one hint reached the other class")
  }

  test("RuleScope.Only fences it — the propagation stops at the boundary the port drew") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = Set("demo.Sprite#layer"),
      scope = RuleScope.Only(Set("demo.Sprite"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(clue(emitted).contains("val layer: Layer.T"))
    assert(emitted.contains("var reading: scala.Int"), "Meter is outside the fence and keeps the primitive")
  }

  test("RuleScope.Everywhere(except) fences it from the other side") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = Set("demo.Sprite#layer"),
      scope = RuleScope.Everywhere(Set("demo.Meter"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(clue(emitted).contains("val layer: Layer.T"))
    assert(emitted.contains("var reading: scala.Int"))
  }

  test("a hint OUTSIDE the fence does not fire — a fence a named entry could step over is not one") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = Set("demo.Meter#reading"),
      scope = RuleScope.Only(Set("demo.Sprite"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(ph))).emit
    assert(!clue(emitted).contains("opaque type"), "no seed fired, so nothing was minted")
  }

  // -------------------------------------------------------------------------
  // O1 — a seed reaching a boundary through a COMPOUND EXPRESSION
  // `ENGINE-LIMITS.md` §13 O1. The shape is the corpus's, not a constructed one: a null-guarding
  // ternary feeding an arithmetic operand and a local that correctly keeps the primitive.
  // -------------------------------------------------------------------------

  private val ternary =
    """package demo;
      |class Tex {
      |  private int handle = 0;
      |  public int getHandle() { return handle; }
      |}
      |class Desc {
      |  Tex tex;
      |  public int hash() {
      |    long result = 0;
      |    result = 811 * result + (tex == null ? 0 : tex.getHandle());
      |    return (int) result;
      |  }
      |  public int cmp(Desc o) {
      |    int h1 = tex == null ? 0 : tex.getHandle();
      |    int h2 = o.tex == null ? 0 : o.tex.getHandle();
      |    if (h1 != h2) return h1 - h2;
      |    return 0;
      |  }
      |}
      |""".stripMargin

  private val handleSpec = OpaqueSpec(fqn = "Handle", hints = Set("demo.Tex#handle"))
  private lazy val ternaryOut =
    new TirEmitter(Pipeline.run(SpoonTir.fromSource(ternary), List(new PrimitiveToOpaqueTransform(handleSpec)))).emit

  test("a seed reaching an ARITHMETIC operand through an `if` is coerced — not invisible") {
    // the `Apply` under the ternary is correctly typed `Handle.T`; the enclosing `Tree.If` is not,
    // because nothing retypes a composite node from its branches. Reading the If's own `tpe` — which
    // is what the phase used to do — sees a plain `Int` and inserts nothing, and the `+` then has no
    // overload for `Long + Handle.T`.
    assert(clue(ternaryOut).contains("Handle.unwrap(this.tex.getHandle())"))
  }

  test("…and the coercion goes INSIDE each branch, never around the carrier") {
    // The reference hand port writes `texture.map(_.textureObjectHandle.toInt).getOrElse(0)` — the
    // coercion at the leaf, the declaration reading as java wrote it. Wrapping the whole would also
    // be WRONG for a mixed carrier: an `if` with one branch of each type has no type a single
    // coercion could take, since an opaque type's bound outside its own object is `Any`.
    assert(!clue(ternaryOut).contains("Handle.unwrap(if"))
    assert(!ternaryOut.contains("Handle.unwrap((if"))
  }

  test("a DECLARATION that correctly kept the primitive is a BOUNDARY, and gets its coercion") {
    // `h1` is rightly NOT a seed: an `if` is not a pure move, so `FlowPropagation` builds no edge to
    // it and the local keeps `int`. That is precisely a boundary — which is exactly where a coercion
    // was owed and where none was inserted (2 of O1's 3 errors).
    assert(clue(ternaryOut).contains("val h1: scala.Int ="))
    assert(ternaryOut.contains("val h2: scala.Int ="))
    assertEquals(clue(ternaryOut.sliding("Handle.unwrap(".length)
      .count(_ == "Handle.unwrap(")), 3, "one per ternary: the operand, h1, h2")
  }

  test("a MIXED carrier flowing INTO a seed wraps the plain branch and leaves the seed one") {
    val mixed =
      """package demo;
        |class Tex {
        |  private int handle = 0;
        |  private int spare = 0;
        |  public void keep() { spare = handle; }
        |  public void reset(boolean b) { handle = b ? 0 : spare; }
        |}
        |""".stripMargin
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Handle", hints = Set("demo.Tex#handle")))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(mixed), List(ph))).emit
    // `spare = handle` IS a pure move, so `spare` is a seed and the `if` mixes the two types. Only
    // the plain branch is wrapped; wrapping the whole would hand `Handle.apply` an argument that is
    // already a `Handle.T` on one path.
    assert(clue(emitted).contains("Handle(0)"))
    assert(!emitted.contains("Handle(this.spare)"))
    assert(!emitted.contains("Handle(if"), "the coercion is at the leaf, not around the carrier")
  }

  test("a UNIFORM plain carrier is still coerced WHOLE — the distribution is for a mix") {
    val uniform =
      """package demo;
        |class Tex {
        |  private int handle = 0;
        |  public int other = 0;
        |  public void reset(boolean b) { handle = b ? 0 : other; }
        |}
        |""".stripMargin
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Handle", hints = Set("demo.Tex#handle")))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(uniform), List(ph))).emit
    // `other` never reaches `handle` by a pure move (an assignment through an `if` is not one), so
    // both branches are plain and the pre-O1 answer — one coercion around the carrier — is right.
    assert(clue(emitted).contains("Handle(if"))
  }

  // -------------------------------------------------------------------------
  // O2 — a retyped PARAMETER moves its METHOD's signature
  // `ENGINE-LIMITS.md` §13 O2. The TIR stores a parameter's type twice; the emitter reads the
  // `ValDef` and the constructor funnel reads the signature, so the two must not disagree.
  // -------------------------------------------------------------------------

  private val inherited =
    """package demo;
      |class Base {
      |  protected int target;
      |  protected int handle;
      |  Base(int target, int handle) { this.target = target; this.handle = handle; }
      |}
      |class Sub extends Base {
      |  Sub(int h) { super(1, h); }
      |  Sub(boolean b) { super(2, 3); }
      |}
      |""".stripMargin

  private def inheritedRun =
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Handle", hints = Set("demo.Base#handle")))
    val after = Pipeline.run(SpoonTir.fromSource(inherited), List(ph))
    (after, new TirEmitter(after).emit)

  test("a retyped ctor PARAMETER moves the ctor's `MethodType` slot — the two derivations agree") {
    val (after, _) = inheritedRun
    given balticporter.tir.Program = after
    val ctor = after.symbols.all.find(s =>
      s.fullName.startsWith("demo.Base#") && s.info.isInstanceOf[balticporter.tir.TypeRepr.MethodType] &&
        after.definitionOf(s.id).exists { case d: balticporter.tir.Tree.DefDef => d.paramss.flatten.sizeIs == 2; case _ => false })
      .getOrElse(fail("no 2-parameter member of demo.Base"))
    val d = after.definitionOf(ctor.id).collect { case d: balticporter.tir.Tree.DefDef => d }.get
    // the two readings of ONE slot: the `ValDef` the emitter renders, and the `MethodType` slot the
    // constructor funnel (and every published contract row) derives from.
    val fromValDef = d.paramss.flatten.map(_.tpt.tpe)
    val fromInfo   = ctor.info match
      case balticporter.tir.TypeRepr.MethodType(ps, _, _) => ps.map(_._2)
      case other => fail(s"not a MethodType: $other")
    assertEquals(clue(fromInfo), clue(fromValDef),
      "a retyping phase owes every derived signature that mentions the declaration it moved")
    assert(fromInfo.exists(t => balticporter.tir.TirPrinter.tpe(t, balticporter.tir.TirPrinter.Style.canonical).contains("Handle")))
  }

  test("…so a SYNTHESISED primary types its `sup$k` slot from the moved signature") {
    val (_, emitted) = inheritedRun
    // `Sub`'s two roots reach ONE parent constructor, so the funnel synthesises a primary taking the
    // PARENT's own formals — read from the signature on purpose, since an argument's type may be
    // narrower than the formal. Stale, slot 1 emitted `scala.Int` against a parent formal of
    // `Handle.T`, and every `def this(...) = this(...)` delegation then failed to resolve.
    assert(clue(emitted).contains("sup$1: Handle.T"), "the funnel read the parent's SIGNATURE")
    assert(!emitted.contains("sup$1: scala.Int"))
  }

  // -------------------------------------------------------------------------
  // O3 — an array-of-prim is now EXPRESSIBLE (O3 CLOSED). The seed reaches
  // `Array[Prim]` declarations, retypes them to `Array[Opaque.T]`, and mints
  // `wrapArray`/`unwrapArray` on the companion.
  // -------------------------------------------------------------------------

  test("O3 CLOSED: an int[] declaration seeded by a hint becomes Array[Opaque.T]") {
    val arrays =
      """package demo;
        |class Mesh {
        |  private int[] locations = new int[4];
        |  public int[] getLocations() { return locations; }
        |  public void bind(int[] locs) { }
        |}
        |""".stripMargin
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Loc", hints = Set("demo.Mesh#locations")))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(arrays), List(ph))).emit
    // the field is retyped to Array[Loc.T]
    assert(clue(emitted).contains("val locations: scala.Array[Loc.T]"))
    // wrapArray and unwrapArray are minted
    assert(emitted.contains("def wrapArray("))
    assert(emitted.contains("def unwrapArray("))
    // no unreachable report — this is now a legitimate seed
    assertEquals(clue(ph.policyReport.findings), Nil)
  }

  test("a hint naming a DEEPER container (e.g. List[int[]]) is still REPORTED — only Array[Prim] is expressible") {
    val nested =
      """package demo;
        |import java.util.List;
        |class Mesh {
        |  private List<int[]> batches;
        |}
        |""".stripMargin
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Loc", hints = Set("demo.Mesh#batches")))
    Pipeline.run(SpoonTir.fromSource(nested), List(ph))
    val fs = ph.policyReport.findings
    assertEquals(clue(fs).size, 1)
    assertEquals(fs.head.issue, balticporter.core.PolicyIssue.Malformed)
  }

  test("a hint the mechanism CAN reach reports nothing — empty policy in, empty report out") {
    val ph = new PrimitiveToOpaqueTransform(layerSpec())
    Pipeline.run(SpoonTir.fromSource(src), List(ph))
    assertEquals(clue(ph.policyReport.findings), Nil)
    // …and a hint naming a declaration with no `int` in it anywhere is an ordinary miss, not this.
    val other = new PrimitiveToOpaqueTransform(OpaqueSpec(fqn = "L", hints = Set("demo.noSuchField")))
    Pipeline.run(SpoonTir.fromSource(src), List(other))
    assertEquals(clue(other.policyReport.findings), Nil)
  }

  test("a report is THIS run's — a reused instance never carries the previous translation's") {
    val nested =
      """package demo;
        |import java.util.List;
        |class Mesh { private List<int[]> batches; }
        |""".stripMargin
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Loc", hints = Set("demo.Mesh#batches")))
    Pipeline.run(SpoonTir.fromSource(nested), List(ph))
    assertEquals(ph.policyReport.findings.size, 1)
    Pipeline.run(SpoonTir.fromSource(nested), List(ph))
    assertEquals(clue(ph.policyReport.findings).size, 1, "cleared at the head of each run, not accumulated")
  }

  // -------------------------------------------------------------------------
  // SurfacePolicy — two modules configuring this differently must NOT compare equal
  // -------------------------------------------------------------------------

  test("the fingerprint separates two differently-configured instances (§1.5)") {
    import balticporter.core.PortManifest.fingerprint
    def ph(s: OpaqueSpec) = new PrimitiveToOpaqueTransform(s)
    val base  = OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"))
    val same  = OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"))
    assertEquals(clue(fingerprint(ph(base))), fingerprint(ph(same)), "two ports that AGREE compare equal")

    // the fence is emitted SURFACE — a base whose `Meter` kept the primitive and a dependent whose
    // did not emit signatures that each compile alone and cannot compile together.
    assertNotEquals(fingerprint(ph(base)), fingerprint(ph(base.copy(scope = RuleScope.Only(Set("demo.Sprite"))))))
    assertNotEquals(fingerprint(ph(base.copy(scope = RuleScope.Only(Set("a"))))),
                    fingerprint(ph(base.copy(scope = RuleScope.Everywhere(Set("a"))))))
    // …and so are the primitive, the HINTS THEMSELVES (O4 CLOSED), and every agent-supplied extra hint.
    assertNotEquals(fingerprint(ph(base)), fingerprint(ph(base.copy(underlying = OpaqueSpec.Primitive.Long))))
    assertNotEquals(fingerprint(ph(base)), fingerprint(ph(base.copy(hints = Set("demo.Meter#reading")))),
      "O4: two specs differing in their hints must compare UNEQUAL — the predicate form could not see this")
    assertNotEquals(fingerprint(ph(base)), fingerprint(ph(base.copy(extraHints = Set("demo.Sprite#z")))))
    // order-independent, or two ports that agree compare unequal on a HashSet's iteration order
    assertEquals(fingerprint(ph(base.copy(hints = Set("b", "a")))),
                 fingerprint(ph(base.copy(hints = Set("a", "b")))))
    assertEquals(fingerprint(ph(base.copy(extraHints = Set("b", "a")))),
                 fingerprint(ph(base.copy(extraHints = Set("a", "b")))))
    // a DIFFERENT opaque type is a different phase NAME, so the two never meet in a fold at all
    assertNotEquals(fingerprint(ph(base)), fingerprint(ph(base.copy(fqn = "Other"))))
  }

  // -- two specs in one pipeline --------------------------------------------

  test("two specs COMPOSE when their propagated seed sets are disjoint") {
    val layers = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = Set("demo.Sprite#layer"),
      scope = RuleScope.Only(Set("demo.Sprite"))))
    val meters = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Reading", hints = Set("demo.Meter#reading"),
      scope = RuleScope.Only(Set("demo.Meter"))))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(twoTypes), List(layers, meters))).emit
    assert(clue(emitted).contains("val layer: Layer.T"))
    assert(emitted.contains("var reading: Reading.T"))
  }

  test("two specs whose seed sets OVERLAP FAIL THE RUN, naming the symbol and both specs") {
    // Unfenced, `Sprite#layer` flows through `Meter.take`'s parameter into `Meter#reading`, so both
    // specs claim `reading`. Silently, the second instance would find it already retyped, decline
    // it as ineligible, and emit a port with half of `Reading` missing — a green compile, no count
    // moved, and no row anywhere saying so.
    val layers = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Layer", hints = Set("demo.Sprite#layer")))
    val meters = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Reading", hints = Set("demo.Meter#reading")))
    val e = intercept[IllegalStateException](
      Pipeline.run(SpoonTir.fromSource(twoTypes), List(layers, meters)))
    assert(clue(e.getMessage).contains("demo.Meter#reading"))
    assert(e.getMessage.contains("Layer"))
    assert(e.getMessage.contains("Reading"))
    assert(e.getMessage.contains("§1(c)"), "the reader's first question is which repository the fix is in")
  }

  // -------------------------------------------------------------------------
  // O6 CLOSED — the Existing form: retype against an EXISTING/injected opaque type
  //
  // The opaque type already exists (an injected file declares it), and the java class it replaces
  // is handled by `Substitutions` (drop + inject). The phase retypes declarations to the existing
  // type's FQN and coerces through its declared wrap/unwrap methods. No companion is minted.
  // -------------------------------------------------------------------------

  private def existingSpec(scope: RuleScope = RuleScope.Everywhere()) =
    OpaqueSpec(
      fqn = "demo.Sprite",  // the java class being replaced (used as phase name key)
      target = OpaqueSpec.Target.Existing(
        typeFqn = "mylib.Layer",
        wrapName = "apply",
        unwrapName = "toInt",
      ),
      hints = Set("demo.Sprite#layer"),
      scope = scope,
    )

  test("O6: Existing form retypes declarations to the target's FQN, not to a minted `.T`") {
    val ph = new PrimitiveToOpaqueTransform(existingSpec())
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(ph))).emit
    // the type is `mylib.Layer`, NOT `mylib.Layer.T` and NOT `demo.Sprite.T`
    assert(clue(emitted).contains("var layer: mylib.Layer"))
    assert(emitted.contains("def getLayer(): mylib.Layer"))
    // the parameter type is propagated; the exact rendering depends on whether the parameter
    // symbol was also a seed (which it should be via flow propagation from `this.layer = layer`).
    assert(clue(emitted).contains("mylib.Layer"), "setLayer's parameter type is the opaque type")
    assert(emitted.contains("def setLayer("))
  }

  test("O6: Existing form does NOT mint a companion — no `opaque type T` in the output") {
    val ph = new PrimitiveToOpaqueTransform(existingSpec())
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(ph))).emit
    assert(!clue(emitted).contains("opaque type"), "no unit minted — the definition is the injected file")
    assert(!emitted.contains("object Layer"), "the companion is NOT synthesised")
  }

  test("O6: Existing form uses the specified wrap/unwrap names in coercions") {
    val ph = new PrimitiveToOpaqueTransform(existingSpec())
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(ph))).emit
    // wrap uses the spec's wrapName ("apply") — rendered as `mylib.Layer(0)`
    assert(clue(emitted).contains("mylib.Layer(0)"), "wrap coercion uses the companion's apply")
    // unwrap uses the spec's unwrapName ("toInt") — rendered as `mylib.Layer.toInt(…)`
    assert(emitted.contains("mylib.Layer.toInt("), "unwrap coercion uses the specified method name")
    // NOT the Mint form's `unwrap`:
    assert(!emitted.contains("mylib.Layer.unwrap("))
  }

  test("O6: Existing form propagation works the same way — getter/setter/local discovered") {
    val ph = new PrimitiveToOpaqueTransform(existingSpec())
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(ph))).emit
    assert(clue(emitted).contains("var layer: mylib.Layer"))
    assert(emitted.contains("def getLayer(): mylib.Layer"))
    assert(emitted.contains("def setLayer(layer: mylib.Layer)"))
    assert(emitted.contains("val l: mylib.Layer"))
  }

  test("O6: Existing form coerces compound expressions the same way as Mint") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "demo.Tex",
      target = OpaqueSpec.Target.Existing(typeFqn = "mylib.Handle", wrapName = "apply", unwrapName = "value"),
      hints = Set("demo.Tex#handle"),
    ))
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(ternary), List(ph))).emit
    assert(clue(emitted).contains("mylib.Handle.value("), "unwrap through compound expression")
  }

  test("O6: Existing form's fingerprint renders the target, not just the mint FQN") {
    import balticporter.core.PortManifest.fingerprint
    def ph(s: OpaqueSpec) = new PrimitiveToOpaqueTransform(s)
    val mint = OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"))
    val existing = OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"),
      target = OpaqueSpec.Target.Existing(typeFqn = "mylib.Layer", wrapName = "apply", unwrapName = "toInt"))

    // Mint and Existing must NOT compare equal — they are different targets
    assertNotEquals(clue(fingerprint(ph(mint))), fingerprint(ph(existing)))

    // two Existing specs with different targets must NOT compare equal
    val existing2 = OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"),
      target = OpaqueSpec.Target.Existing(typeFqn = "other.Layer", wrapName = "apply", unwrapName = "toInt"))
    assertNotEquals(clue(fingerprint(ph(existing))), fingerprint(ph(existing2)))

    // two Existing specs with different unwrap names must NOT compare equal
    val existing3 = OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"),
      target = OpaqueSpec.Target.Existing(typeFqn = "mylib.Layer", wrapName = "apply", unwrapName = "value"))
    assertNotEquals(clue(fingerprint(ph(existing))), fingerprint(ph(existing3)))

    // identical Existing specs compare equal
    val existingSame = OpaqueSpec(fqn = "Layer", hints = Set("demo.Sprite#layer"),
      target = OpaqueSpec.Target.Existing(typeFqn = "mylib.Layer", wrapName = "apply", unwrapName = "toInt"))
    assertEquals(clue(fingerprint(ph(existing))), fingerprint(ph(existingSame)))
  }

  test("O6: Existing form allows nested FQNs that the Mint form refuses") {
    // The Mint form refuses `$` and `#` because it mints a TOP-LEVEL unit. The Existing form has no
    // such constraint — the target is whatever the injected file declares.
    val nested = OpaqueSpec(
      fqn = "demo.Sprite",
      target = OpaqueSpec.Target.Existing(typeFqn = "sge.Input.Key", wrapName = "apply", unwrapName = "toInt"),
      hints = Set("demo.Sprite#layer"),
    )
    val ph = new PrimitiveToOpaqueTransform(nested)
    val emitted = new TirEmitter(Pipeline.run(SpoonTir.fromSource(src), List(ph))).emit
    assert(clue(emitted).contains("sge.Input.Key"), "nested FQN is rendered correctly")
  }

  test("O6: Existing target validation refuses empty fields") {
    intercept[IllegalArgumentException](
      OpaqueSpec.Target.Existing(typeFqn = "", wrapName = "apply", unwrapName = "toInt"))
    intercept[IllegalArgumentException](
      OpaqueSpec.Target.Existing(typeFqn = "sge.Align", wrapName = "", unwrapName = "toInt"))
    intercept[IllegalArgumentException](
      OpaqueSpec.Target.Existing(typeFqn = "sge.Align", wrapName = "apply", unwrapName = ""))
  }

  test("O6: decision provenance records §1(c) for Existing form too") {
    val ph  = new PrimitiveToOpaqueTransform(existingSpec())
    val log = Pipeline.runTraced(SpoonTir.fromSource(src), List(ph))._2
    val ds  = log.of(balticporter.tir.Decision.Kind.RetypedSignature)
    assert(clue(ds).nonEmpty)
    assert(ds.forall(_.reason == balticporter.tir.Reason.LibraryRule("primitive->opaque:demo.Sprite")))
    // the detail shows the target type, not the mint
    assert(ds.exists(_.detail("to").contains("mylib.Layer")))
  }

  // -------------------------------------------------------------------------
  // O8 — array ELEMENT coercion at three positions
  //
  // After O3 retypes `int[] locations` to `Array[Loc.T]`, an element read
  // `locations[i]` is already opaque.  The three shapes that arrived on
  // UniformLocation:
  //   (a) element READ in a non-seed context — must NOT double-wrap
  //   (b) element WRITE from an unpropagated value — must wrap the RHS
  //   (c) branch join — one branch is an element read, the other plain
  // -------------------------------------------------------------------------

  private val arrayElem =
    """package demo;
      |class Shader {
      |  private int[] locations = new int[4];
      |  private int handle;
      |  public int loc(int i) { return (i >= 0 && i < locations.length) ? locations[i] : -1; }
      |  public void setUniform(int loc, float v) { }
      |  public void apply(int i, float v) {
      |    if (locations[i] < 0) return;
      |    setUniform(locations[i], v);
      |  }
      |  public void init(int n) {
      |    locations = new int[n];
      |    for (int j = 0; j < n; j++) locations[j] = -1;
      |  }
      |  public void store(int i) { locations[i] = handle; }
      |}
      |""".stripMargin

  private def arrayElemRun =
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Loc", hints = Set("demo.Shader#handle", "demo.Shader#locations")))
    val after = Pipeline.run(SpoonTir.fromSource(arrayElem), List(ph))
    new TirEmitter(after).emit

  test("O8(a): an element READ from a retyped array is ALREADY opaque — no double-wrap") {
    val emitted = arrayElemRun
    // `setUniform(locations[i], v)` — the element is already `Loc.T`, so the argument
    // coercion at `setUniform`'s seeded parameter must NOT wrap it again.
    assert(!clue(emitted).contains("Loc(this.locations("), "element read must not be double-wrapped")
  }

  test("O8(b): an element WRITE from a plain value WRAPS the RHS") {
    val emitted = arrayElemRun
    // `locations[j] = -1` assigns a literal to an Array[Loc.T] element — wrap the RHS
    assert(clue(emitted).contains("Loc(-1)") || emitted.contains("Loc((-1"))
  }

  test("O8(c): a branch join with one opaque branch and one plain wraps only the plain branch") {
    val emitted = arrayElemRun
    // `return (i >= 0 && …) ? locations[i] : -1` in a seed method: the element-read branch
    // is already opaque, the `-1` branch is plain. Only the plain branch gets wrapped.
    assert(clue(emitted).contains("Loc(-1)") || emitted.contains("Loc((-1"))
    // the element-read branch is NOT wrapped:
    assert(!clue(emitted).contains("Loc(this.locations("), "the opaque branch stays as-is")
  }

  // -------------------------------------------------------------------------
  // O8 DEPENDENT BLAST (wave 2.11): a dependent calling a base method whose
  // parameter the base retyped must NOT unwrap its opaque argument — the
  // compiled class file ALSO has the opaque formal.
  // -------------------------------------------------------------------------

  private val depBase =
    """package demo;
      |class Shader {
      |  private int[] locations = new int[4];
      |  private int handle;
      |  public int loc(int i) { return locations[i]; }
      |  public void setUniform(int loc, float v) { }
      |  public void apply(int i, float v) { setUniform(locations[i], v); }
      |}
      |""".stripMargin

  private val depExt =
    """package demo;
      |class PBRShader extends Shader {
      |  private int u_lod;
      |  private int[] u_csm = new int[4];
      |  public void init() {
      |    u_lod = loc(0);
      |    for (int i = 0; i < 4; i++) u_csm[i] = loc(i);
      |  }
      |  public void render(float bias) { setUniform(u_lod, bias); }
      |  public void renderCsm(int i, float v) { setUniform(u_csm[i], v); }
      |}
      |""".stripMargin

  test("O8 dependent: a callee the base ALSO retyped does not unwrap — the compiled formal is opaque") {
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Loc", hints = Set("demo.Shader#handle", "demo.Shader#locations"),
      target = OpaqueSpec.Target.Existing(typeFqn = "Loc.T", wrapName = "apply", unwrapName = "toInt"),
    ))
    val p = SpoonTir.fromSources(List("Shader.java" -> depBase, "PBRShader.java" -> depExt))
    // build a RunScope where only PBRShader is "emitted" — Shader is a resolution root.
    // The base's port map says `setUniform(Loc,float)` — the base RETYPED the first param.
    val depUnit = p.units.find(u => p.symbolOf(u.symbol).exists(_.fullName == "demo.PBRShader")).get
    val baseUpstream = Set("demo.Shader#setUniform(Loc,float)")
    val scope   = RunScope.of(Set(depUnit.symbol), Map.empty, memberUpstream = baseUpstream)
    val binder  = new PolicyBinder(p, p.members, scope)
    val after   = Pipeline.runTraced(p, List(ph), binder)._1
    val emitted = new TirEmitter(after).emit
    // `setUniform(u_lod, bias)` — u_lod is propagated to Loc.T, the formal is ALSO Loc.T
    // in the base's compiled code. The argument must NOT be unwrapped.
    assert(!clue(emitted).contains("Loc.toInt(this.u_lod)"), "propagated arg at a base-retyped formal must not unwrap")
    // `setUniform(u_csm[i], v)` — the array element also flows through to the base's retyped formal
    assert(!emitted.contains("Loc.toInt(this.u_csm("), "array-element arg at a base-retyped formal must not unwrap")
  }

  test("O8 dependent: a callee the base did NOT retype still unwraps — Align's case (wave 2.8)") {
    // `bump` takes int, and only the DEPENDENT's propagation reaches it (via `render`'s
    // local). The base's own code has no flow edge to `bump`'s parameter. The argument
    // must be UNWRAPPED because the compiled formal is still `int`.
    // The base port map says `bump(int)` — no opaque FQN, so the base did NOT retype.
    val bumpBase =
      """package demo;
        |class Widget {
        |  private int align = 0;
        |  public int getAlign() { return align; }
        |  public void setAlign(int a) { align = a; }
        |  public void bump(int x) { align = align | x; }
        |}
        |""".stripMargin
    val bumpExt =
      """package demo;
        |class MyWidget extends Widget {
        |  private int local;
        |  public void init() { local = getAlign(); }
        |  public void apply() { bump(local); }
        |}
        |""".stripMargin
    val ph = new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn = "Al", hints = Set("demo.Widget#align")))
    val p = SpoonTir.fromSources(List("Widget.java" -> bumpBase, "MyWidget.java" -> bumpExt))
    val depUnit = p.units.find(u => p.symbolOf(u.symbol).exists(_.fullName == "demo.MyWidget")).get
    // base port map shows `bump(int)` — no opaque FQN, base kept it as Int
    val baseUpstream = Set("demo.Widget#bump(int)")
    val scope   = RunScope.of(Set(depUnit.symbol), Map.empty, memberUpstream = baseUpstream)
    val binder  = new PolicyBinder(p, p.members, scope)
    val after   = Pipeline.runTraced(p, List(ph), binder)._1
    val emitted = new TirEmitter(after).emit
    // `bump(local)` — local is propagated to Al.T, but bump's parameter is NOT in the base's
    // retyped set (port map says `int`). The argument must be unwrapped.
    assert(clue(emitted).contains("Al.unwrap(this.local)"),
      "propagated arg at a base-NOT-retyped formal must unwrap")
  }

  // -------------------------------------------------------------------------
  // O9: duplicate primitive symbol after CollectionsTransform.retargetFixedTypeSyms
  // -------------------------------------------------------------------------

  test("O9: seeding works when the program has a DUPLICATE scala.Int symbol (retarget FixedType)") {
    // CollectionsTransform.retargetFixedTypeSyms mints a second `scala.Int` symbol through `byScala`
    // when a FixedType("scala.Int") arg is resolved.  The opaque transform's `find` on
    // `"scala.Int"` was non-deterministic: it might bind `primSym` to the MINTED one (high SymId)
    // while every existing field's `info` references the ORIGINAL (low SymId).  Then `isPrim`
    // rejected every hint and the phase returned early with 0 seeds.
    //
    // This test reproduces the exact shape: parse a program, add a second `scala.Int` symbol at a
    // HIGH SymId, and verify the opaque transform still seeds from the original-SymId fields.
    val p0  = SpoonTir.fromSource(src)
    val origIntSym = p0.symbols.all.find(_.fullName == "scala.Int").get
    // mint a duplicate with a higher SymId
    val maxId = p0.symbols.all.map(_.id.raw).max
    val dupSym = Symbol(SymId(maxId + 100), "Int", "scala.Int", Flags(), SymId.None, TypeRepr.NoType)
    val p1  = p0.rebuilt(symbols = p0.symbols.updated(dupSym))
    // sanity: TWO symbols named scala.Int
    assertEquals(p1.symbols.all.count(_.fullName == "scala.Int"), 2)
    // the opaque transform must still find the original and seed from it
    val ph  = new PrimitiveToOpaqueTransform(layerSpec())
    val out = new TirEmitter(Pipeline.run(p1, List(ph))).emit
    assert(clue(out).contains("var layer: Layer.T"),
      "O9 regression: the field hint must be seeded despite a duplicate scala.Int symbol")
    assert(out.contains("def getLayer(): Layer.T"),
      "O9 regression: propagation must discover the getter from the seeded field")
  }
