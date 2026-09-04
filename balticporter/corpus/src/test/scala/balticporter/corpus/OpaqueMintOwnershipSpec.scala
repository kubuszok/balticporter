package balticporter.corpus

import balticporter.core.{FrontendConfig, PortManifest, RealPath}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*
import balticporter.transform.PrimitiveToOpaqueTransform

import java.nio.file.{Files, Path}

/** A phase that MINTS a top-level unit owes the same one-module answer an `inject` does —
  * `ENGINE-LIMITS.md` §13 O5, CLAUDE.md §1.5. */
class OpaqueMintOwnershipSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------
  // a two-module Java tree: `base/` is resolved against, `dep/` is converted
  // -------------------------------------------------------------------------

  /** the family's home: one tagged field, its getter, and the site that mints a raw value. */
  private val base = Map(
    "p/Gpu.java" -> """package p;
      |public class Gpu {
      |  private int handle;
      |  public Gpu(int handle) { this.handle = handle; }
      |  public int getHandle() { return handle; }
      |  public void reset() { handle = 0; }
      |}""".stripMargin,
  )

  /** a CONSUMER, whose own local is dragged into the seed set by a pure-move flow from the base's
    * tagged getter — the shape that makes a grown-set test wrong. */
  private val dep = Map(
    "q/Uses.java" -> """package q;
      |public class Uses {
      |  public int copy(p.Gpu g) {
      |    int h = g.getHandle();
      |    return h + 1;
      |  }
      |}""".stripMargin,
  )

  private def model(): (Program, Path) = modelWith(dep)

  private def modelWith(depFiles: Map[String, String]): (Program, Path) =
    val root = Files.createTempDirectory("opaque-mint-ownership")
    def put(under: Path, files: Map[String, String]) = files.foreach { (rel, body) =>
      val p = under.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, body)
    }
    put(root.resolve("base"), base)
    put(root.resolve("dep"), depFiles)
    val types = SpoonTir.buildModel(
      FrontendConfig(root.resolve("dep"), depFiles.keys.toList.sorted, Nil,
                     resolutionRoots = List(root.resolve("base"))), lenient = true)
    (SpoonTir.fromTypes(types), root)

  /** exactly what `PortRun.partitionUnits` computes — by ORIGIN, realpathed on both sides (§5.4). */
  private def emittedUnits(p: Program, root: Path, module: String): Set[SymId] =
    val mine = RealPath.str(root.resolve(module))
    p.units.filter(u => RealPath.str(Path.of(u.origin.javaPath)).startsWith(mine)).map(_.symbol).toSet

  /** the ONE instance both modules hold: a base declares it, a dependent inherits it through
    * `extendedBy` and cannot subtract it (§1.5). The hint names a BASE declaration. */
  private def phase = new PrimitiveToOpaqueTransform(OpaqueSpec(
    fqn   = "p.Handle",
    hints = Set("p.Gpu#handle"),
  ))

  private def manifest(ph: PrimitiveToOpaqueTransform): PortManifest =
    PortManifest(name = "base", governs = Set("p"), surface = List(ph))
      .extendedBy(PortManifest(name = "dep", governs = Set("q")))

  /** run the inherited surface with the `RunScope` a `PortRun` builds for `module`. */
  private def run(p: Program, root: Path, ph: PrimitiveToOpaqueTransform, module: String): Program =
    val m      = manifest(ph)
    val scope  = RunScope.of(emittedUnits(p, root, module), m.contributedSubjects)
    val binder = new PolicyBinder(p, p.members, scope)
    Pipeline.runTraced(p, m.effectiveSurface, binder)._1

  private def unitNames(p: Program): List[String] =
    p.units.flatMap(u => p.symbolOf(u.symbol).map(_.fullName)).sorted

  private def emitOf(p: Program, fqn: String): String =
    val u = p.units.find(x => p.symbolOf(x.symbol).exists(_.fullName == fqn))
      .getOrElse(fail(s"no unit $fqn in ${unitNames(p)}"))
    new TirEmitter(p).emitUnit(u)

  private def infoOf(p: Program, fqn: String): TypeRepr =
    p.symbols.all.find(_.fullName == fqn).map(_.info).getOrElse(fail(s"no symbol $fqn"))

  private def isOpaqueTyped(p: Program, t: TypeRepr): Boolean =
    def head(x: TypeRepr): Option[SymId] = x match
      case TypeRepr.TypeRef(_, s)       => Some(s)
      case TypeRepr.MethodType(_, r, _) => head(r)
      case TypeRepr.AppliedType(tc, _)  => head(tc)
      case _                            => scala.None
    head(t).flatMap(p.symbolOf).exists(_.flags.isOpaque)

  // -------------------------------------------------------------------------
  // MINT ONCE
  // -------------------------------------------------------------------------

  test("the module that owns the spec's HINTS mints the object") {
    val (p, root) = model()
    val after     = run(p, root, phase, "base")
    assert(clue(unitNames(after)).contains("p.Handle"))
    val emitted = emitOf(after, "p.Handle")
    assert(clue(emitted).contains("opaque type T = scala.Int"))
    assert(emitted.contains("def apply(v: scala.Int): Handle.T"))
    assert(emitted.contains("def unwrap(v: Handle.T): scala.Int"))
  }

  test("a DEPENDENT running the SAME inherited instance mints NOTHING") {
    val (p, root) = model()
    val after     = run(p, root, phase, "dep")
    assert(!clue(unitNames(after)).contains("p.Handle"),
      "the dependent wrote its own copy of a unit its base already emits — ENGINE-LIMITS §13 O5")
    // …and not by accident of the phase declining to run: the SEED set is non-empty, which is what
    // makes this a fence on the MINT and not a fence on the translation.
    assert(isOpaqueTyped(after, infoOf(after, "p.Gpu#handle")),
      "the dependent did not seed at all, so this proves nothing about the mint")
  }

  test("NEGATIVE: with no run scope the SAME dependent program mints — the fence is what stops it") {
    // `RunScope.whole` is the pre-fix behaviour and remains the truth for a base port, a
    // single-module port, a spec and `DebugEmit`. Running the dependent's own program under it
    // reproduces the O5 duplicate exactly, which is what makes the test above a measurement of the
    // fence rather than of some other difference between the two runs.
    val (p, root) = model()
    val ph        = phase
    val after     = Pipeline.runTraced(p, manifest(ph).effectiveSurface,
                                       new PolicyBinder(p, p.members, RunScope.whole))._1
    assert(clue(unitNames(after)).contains("p.Handle"))
  }

  // -------------------------------------------------------------------------
  // …AND THE DEPENDENT STILL COERCES
  // -------------------------------------------------------------------------

  test("a dependent that mints nothing still RETYPES and COERCES against the base's object") {
    val (p, root) = model()
    val after     = run(p, root, phase, "dep")

    // the base's declarations carry the family (the dependent re-derives them from the base's Java,
    // which is what `resolutionRoots` is for) …
    assert(isOpaqueTyped(after, infoOf(after, "p.Gpu#getHandle")))

    // …and the emitted dependent names the object it does NOT define — a fully-qualified reference
    // that resolves against the base's emitted output on the classpath (§6: FQNs, no imports). Its
    // own local moved with the family, and the arithmetic boundary got its coercion.
    val emitted = emitOf(after, "q.Uses")
    assert(clue(emitted).contains("val h: p.Handle.T"), "the dependent's own local did not retype")
    assert(emitted.contains("p.Handle.unwrap(h) + 1"), "the arithmetic boundary lost its coercion")
    assert(!emitted.contains("opaque type"), "the dependent emitted a definition, not a reference")
  }

  test("the GROWN seed set reaches the dependent's OWN unit — which is why the fence reads the HINTS") {
    // The trap, stated as a measurement rather than as an argument. `copy`'s local is a seed and it
    // lives in a unit the dependent EMITS, so `seeds.exists(owned)` is TRUE in the dependent — a
    // fence read off the grown set would mint there and reproduce O5 in full.
    val (p, root) = model()
    val ph        = phase
    val after     = run(p, root, ph, "dep")
    def unitOf(id: SymId, fuel: Int = 64): SymId = after.symbolOf(id) match
      case Some(s) if s.owner != SymId.None && fuel > 0 => unitOf(s.owner, fuel - 1)
      case _                                            => id
    val mine  = emittedUnits(after, root, "dep")
    val grown = ph.typeMapping.keySet
    assert(grown.exists(id => mine.contains(unitOf(id))),
      "the fixture no longer exercises the trap — no propagated seed is in a dependent-owned unit")
    // …while no HINT is, which is the difference the fence reads.
    val hinted = after.symbols.all.filter(s => ph.spec.hints(s.fullName)).map(_.id)
    assert(clue(hinted).nonEmpty)
    assert(hinted.forall(id => !mine.contains(unitOf(id))))
  }

  // -------------------------------------------------------------------------
  // …and a hint set that STRADDLES the two modules is refused, not resolved by `exists`
  // -------------------------------------------------------------------------

  /** the same two-module tree, plus a dependent declaration that the SAME HINT SET explicitly names.
    * With `hints` as a `Set[String]`, spanning is expressed by listing FQNs from BOTH modules —
    * `p.Gpu#handle` (base) and `q.Own#handle` (dependent). The test verifies that the fence rejects
    * this explicitly-stated straddling, the same shape the predicate form used to create silently. */
  private val depWithOwnField = dep + ("q/Own.java" -> """package q;
    |public class Own {
    |  private int handle;
    |  public int get() { return handle; }
    |}""".stripMargin)

  private def patternPhase = new PrimitiveToOpaqueTransform(OpaqueSpec(
    fqn   = "p.Handle",
    hints = Set("p.Gpu#handle", "q.Own#handle"),
  ))

  test("SPANNING hints FAIL THE RUN — `exists` would have minted in BOTH modules") {
    // Pre-fix this was silent: `mintsHere` is `hints.exists(owned)`, true in the dependent because
    // of `q.Own#handle` and true in the base because of `p.Gpu#handle`, so both modules write
    // `p.Handle.scala` — O5 in full, with the fence in place and answering. The belt behind it
    // (`PortRun.claimedSynthetic`) does not catch it either: with no published base map it ADMITS.
    val (p, root) = modelWith(depWithOwnField)
    val err = intercept[IllegalStateException](run(p, root, patternPhase, "dep"))
    assert(clue(err.getMessage).contains("MORE THAN ONE module"))
    assert(err.getMessage.contains("p.Gpu#handle"), "the side this module does NOT emit is named")
    assert(err.getMessage.contains("q.Own#handle"), "…and so is the side it does")
    assert(err.getMessage.contains("§1(c)"), "a refusal says which of §1's three kinds the fix is")
    assert(err.getMessage.contains("ENGINE-LIMITS.md` §13 O5"))
  }

  test("…and the BASE side of the same tree refuses too — neither module may decide alone") {
    val (p, root) = modelWith(depWithOwnField)
    val err = intercept[IllegalStateException](run(p, root, patternPhase, "base"))
    assert(clue(err.getMessage).contains("MORE THAN ONE module"))
  }

  test("NEGATIVE: the SAME pattern over ONE module is not a span — the rule is about the LINE") {
    // `q.Own` alone, with no base to straddle: `RunScope.whole`, every hint owned, mint proceeds.
    // Without this the refusal could be "a name pattern is banned", which it is not.
    val root  = Files.createTempDirectory("opaque-mint-one-module")
    depWithOwnField.foreach { (rel, body) =>
      val f = root.resolve(rel); Files.createDirectories(f.getParent); Files.writeString(f, body)
    }
    val types = SpoonTir.buildModel(
      FrontendConfig(root, depWithOwnField.keys.toList.sorted, Nil), lenient = true)
    val after = Pipeline.run(SpoonTir.fromTypes(types), List(patternPhase))
    assert(clue(unitNames(after)).contains("p.Handle"))
  }

  test("NEGATIVE: an EXACT-FQN hint over the two-module tree is unaffected — the corpus's shape") {
    // libGDX's own spec is `_.fullName == "…#glHandle"`, which cannot straddle. This is the
    // measurement behind "zero corpus movement" rather than an assertion about it.
    val (p, root) = modelWith(depWithOwnField)
    assert(!clue(unitNames(run(p, root, phase, "dep"))).contains("p.Handle"))
    assert(clue(unitNames(run(p, root, phase, "base"))).contains("p.Handle"))
  }

  // -------------------------------------------------------------------------
  // the identity: a BASE port screens nothing
  // -------------------------------------------------------------------------

  test("a SINGLE-MODULE port is unaffected — `RunScope.whole` is the identity, by arithmetic") {
    val root  = Files.createTempDirectory("opaque-mint-single")
    base.foreach { (rel, body) =>
      val p = root.resolve(rel); Files.createDirectories(p.getParent); Files.writeString(p, body)
    }
    val types = SpoonTir.buildModel(FrontendConfig(root, base.keys.toList.sorted, Nil), lenient = true)
    val p     = SpoonTir.fromTypes(types)
    val after = Pipeline.run(p, List(phase))
    assert(clue(unitNames(after)).contains("p.Handle"))
  }
