package balticporter.frontend.spoon

import balticporter.core.{FrontendConfig, Substitutions}
import balticporter.tir.*

import java.nio.file.Files

/** [[PolicyBinder]] — every way a declared key can FAIL to name what its author meant, and the
  * different instruction each failure owes its reader.
  *
  * The positives are cheap and the negatives are the design. A binder that only ever answers about
  * keys that bind is a `Map.get`, and `Map.get` is what the engine had eighteen copies of.
  *
  * Over a REAL source tree, for `DescriptorSpec`'s reason.
  */
class PolicyBinderSpec extends munit.FunSuite:

  private def tree(subs: Substitutions)(files: (String, String)*): Program =
    val root = Files.createTempDirectory("binder")
    files.foreach { (rel, src) =>
      val p = root.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, src)
    }
    SpoonTir.fromTypes(SpoonTir.buildModel(FrontendConfig(root, files.map(_._1).toList, Nil)), subs)

  private val source = "com/demo/Shop.java" ->
    """package com.demo;
      |import java.util.List;
      |public class Shop {
      |  public int count;
      |  public Shop() { }
      |  public Shop(int n) { }
      |  public Object make(Class<?> c) { return null; }
      |  public Object make(String name) { return null; }
      |  public void copy(int[] src) { }
      |  public void of(String... parts) { }
      |  public String use(List<String> xs) { return xs.get(0); }
      |  @Override public boolean equals(Object o) { return false; }
      |}""".stripMargin

  private def binderOf(p: Program) = new PolicyBinder(p, p.members)
  private def bind(p: Program, key: String, need: Ownership = Ownership.Owned) =
    binderOf(p).bindMember("spec", "setting", key, need)
  private def bindAll(p: Program, key: String, need: Ownership = Ownership.Owned) =
    binderOf(p).bindMembers("spec", "setting", key, need)

  // -------------------------------------------------------------------------
  // the positives
  // -------------------------------------------------------------------------

  test("an overload-PRECISE key binds to exactly one symbol; the BARE key binds to the whole set") {
    val p = tree(Substitutions.none)(source)
    val one = bind(p, "com.demo.Shop#make(Class)")
    assert(clue(one).isBound)
    val oneSym = one.toOption.flatMap(_.sym).get
    assertEquals(p.symbolOf(oneSym).flatMap(_.descriptor).map(_.render), Some("Class"))

    val all = bindAll(p, "com.demo.Shop#make")
    assertEquals(all.toOption.map(_.size), Some(2))
    // …and the precise one is a REFINEMENT of the bare one, not a different member.
    assert(all.toOption.get.flatMap(_.sym).contains(oneSym))
  }

  test("a FIELD key binds with no descriptor and produces NO finding — `owner#name` is its whole identity") {
    val p = tree(Substitutions.none)(source)
    val b = binderOf(p)
    assert(b.bindMember("spec", "setting", "com.demo.Shop#count").isBound)
    assertEquals(b.unbound, Nil)
  }

  test("a CONSTRUCTOR key binds precisely — which is the only way a constructor is droppable at all") {
    val p = tree(Substitutions.none)(source)
    assert(bind(p, "com.demo.Shop#<init>(int)").isBound)
    assert(bind(p, "com.demo.Shop#<init>()").isBound)
    assertNotEquals(bind(p, "com.demo.Shop#<init>(int)").toOption.flatMap(_.sym),
                    bind(p, "com.demo.Shop#<init>()").toOption.flatMap(_.sym))
  }

  // -------------------------------------------------------------------------
  // the negatives — one instruction each
  // -------------------------------------------------------------------------

  test("an AMBIGUOUS key fails to bind and the finding LISTS the candidates, rendered with descriptors") {
    val p = tree(Substitutions.none)(source)
    val b = bind(p, "com.demo.Shop#make")
    assertEquals(b.why, Some(NotBound.Ambiguous(
      List("com.demo.Shop#make(Class)", "com.demo.Shop#make(String)"))))
    // the message must be the string an agent EDITS (§4.575) — so the candidates appear verbatim.
    assert(clue(b.why.get.detail).contains("com.demo.Shop#make(Class)"))
    assert(clue(b.why.get.detail).contains("com.demo.Shop#make(String)"))
  }

  test("an ARRAY key binds from BOTH spellings' one true form — and `Array` does NOT bind") {
    val p = tree(Substitutions.none)(source)
    assert(clue(bind(p, "com.demo.Shop#copy(int[])")).isBound)
    // THE TRAP. The engine's own key grammar spelled this member `copy(Array)` because the TIR
    // renders `int[]` as `AppliedType(scala.Array, [Int])` and the key took the TYCON's name. If
    // that ever binds again, the two spellings are interchangeable and the divergence is back.
    assertEquals(bind(p, "com.demo.Shop#copy(Array)").why, Some(NotBound.NeverMatched))
  }

  test("a VARARG binds as the ARRAY it is, and that key does NOT also bind the 1-argument overload") {
    val p = tree(Substitutions.none)(source)
    assert(clue(bind(p, "com.demo.Shop#of(String[])")).isBound)
    assertEquals(bind(p, "com.demo.Shop#of(String)").why, Some(NotBound.NeverMatched))
  }

  test("`equals` binds as `Object` and NEVER as `Any` — the frontend's retype is invisible to policy") {
    val p = tree(Substitutions.none)(source)
    assert(clue(bind(p, "com.demo.Shop#equals(Object)")).isBound)
    assertEquals(bind(p, "com.demo.Shop#equals(Any)").why, Some(NotBound.NeverMatched))
  }

  test("a DROPPED member's key BINDS — through the index, with no Symbol anywhere in the program") {
    val p = tree(Substitutions(dropMethods = Set("com.demo.Shop#make(Class)")))(source)
    // it FIRED: `bindMembers` binds, with an empty symbol set, and reports nothing.
    val b = bindAll(p, "com.demo.Shop#make(Class)")
    assert(clue(b).isBound)
    assertEquals(b.toOption.map(_.flatMap(_.sym)), Some(Nil))     // nothing to point at …
    assertEquals(b.toOption.map(_.map(_.dropped)), Some(List(true))) // … because it was DROPPED
    assertEquals(binderOf(p).unbound, Nil)
    // …and the program genuinely has no such member, which is what makes the line above the point.
    val owner = p.symbols.all.find(_.fullName == "com.demo.Shop").map(_.id).get
    assertEquals(p.symbols.all.filter(s => s.name == "make" && s.owner == owner)
      .flatMap(_.descriptor.map(_.render)).toSet, Set("String"))
  }

  /** a second unit that CALLS the member the test above drops — the reference side, which is where
    * a dropped member still has a symbol. */
  private val caller = "com/demo/Till.java" ->
    """package com.demo;
      |public class Till {
      |  public Object buy(Shop s) { return s.make(Object.class); }
      |}""".stripMargin

  test("bindCallee reaches a DROPPED member's CALL SITES, which is the one thing bindMembers cannot") {
    // `ENGINE-LIMITS.md` D7 in one assertion. The DECLARATION is gone and the key still fired, so
    // the declaration-side answer is "bound, nothing to point at" — correct, and useless to a phase
    // that rewrites CALLS, which is exactly the phase a dropped-and-still-called member needs.
    val p = tree(Substitutions(dropMethods = Set("com.demo.Shop#make(Class)")))(source, caller)
    assertEquals(bindAll(p, "com.demo.Shop#make(Class)").toOption.map(_.flatMap(_.sym)), Some(Nil))

    val b   = binderOf(p)
    val hit = b.bindCallee("spec", "setting", "com.demo.Shop#make(Class)")
    assert(clue(hit).isBound)
    assert(hit.toOption.flatMap(_.sym).isDefined)
    // …and the DROP is still visible, so a phase can say "this callee has no declaration to return
    // to" without asking the index a second question.
    assertEquals(hit.toOption.map(_.dropped), Some(true))
    // the refusal this path has to suppress, and the reason it needs its own entry point: the
    // structural engine-minted test cannot tell a reference-side interning from a synthetic member.
    assertEquals(b.unbound, Nil)
  }

  test("bindCallee does NOT change the ordinary path — a LIVE member still binds through the index") {
    val p = tree(Substitutions.none)(source, caller)
    val b = binderOf(p)
    val viaCallee = b.bindCallee("spec", "setting", "com.demo.Shop#make(Class)")
    val viaMember = bind(p, "com.demo.Shop#make(Class)")
    assertEquals(viaCallee.toOption.flatMap(_.sym), viaMember.toOption.flatMap(_.sym))
    assertEquals(viaCallee.toOption.map(_.dropped), Some(false))
    assertEquals(b.unbound, Nil)
  }

  test("bindCallee requires EXACTNESS: a bare key naming two overloads is Ambiguous, never one") {
    val p = tree(Substitutions.none)(source, caller)
    val b = binderOf(p).bindCallee("spec", "setting", "com.demo.Shop#make")
    assertEquals(b.why, Some(NotBound.Ambiguous(List("com.demo.Shop#make(Class)", "com.demo.Shop#make(String)"))))
  }

  test("an EXTERNAL-only entry reports ExternalOnly WITH WHY — never Bound, and never a typo") {
    val p = tree(Substitutions.none)(source)
    val b = binderOf(p)
    // `java.util.List` is interned by the frontend on first reference and matches a scope entry
    // perfectly. The phase would then rewrite nothing at all while the entry counted as fired —
    // the §1(b) silent no-op wearing the costume of a working knob.
    val scoped = b.bindScope("spec", "CollectionsTransform(scope)", "java.util.List")
    assertEquals(scoped.why, Some(NotBound.ExternalOnly("java.util.List")))
    assert(clue(scoped.why.get.detail).contains("REFERENCES and does not DECLARE"))
    // and a type this program DECLARES binds, so the refusal is about ownership and not about JDK-ness.
    assert(b.bindScope("spec", "CollectionsTransform(scope)", "com.demo.Shop").isBound)
  }

  // -------------------------------------------------------------------------
  // the QUALIFIED spelling a report shows — the descriptor trap, removed
  // -------------------------------------------------------------------------

  test("a key whose parameters are QUALIFIED binds to the member it obviously names — OWNED") {
    // The grammar is SIMPLE names and every report a key is copied out of shows the qualified ones.
    // Compared by equality this key named nothing and the binder said `never matched` about a member
    // that is right there — the failure two ports document in a comment and neither can fix.
    val p    = tree(Substitutions.none)(source)
    val hit  = bind(p, "com.demo.Shop#make(java.lang.Class)")
    assert(clue(hit).isBound)
    assertEquals(hit.toOption.flatMap(_.sym).flatMap(p.symbolOf).flatMap(_.descriptor).map(_.render),
                 Some("Class"))
    // …and it is still the OVERLOAD it names, not the set: the other one is not admitted.
    assertEquals(bind(p, "com.demo.Shop#make(java.lang.String)").toOption
                   .flatMap(_.sym).flatMap(p.symbolOf).flatMap(_.descriptor).map(_.render),
                 Some("String"))
  }

  test("…and EXTERNAL, which is where the trap was actually met") {
    // An external member's `Symbol.fullName` IS its interning key, so the row a port reads prints
    // `…#identityHashCode(java.lang.Object)` — the qualified spelling — while its `descriptor` holds
    // the simple one. Both spellings must reach the same symbol, or the engine's own report is a
    // key nobody can use.
    val p = tree(Substitutions.none)(
      source,
      "com/demo/Uses.java" ->
        """package com.demo;
          |public class Uses {
          |  public int h(Object o) { return System.identityHashCode(o); }
          |}""".stripMargin)
    def bindExt(k: String) =
      binderOf(p).bindMember("spec", "setting", k, Ownership.External).toOption.flatMap(_.sym)
    val simple    = bindExt("java.lang.System#identityHashCode(Object)")
    val qualified = bindExt("java.lang.System#identityHashCode(java.lang.Object)")
    assert(clue(simple).isDefined)
    assertEquals(qualified, simple)
  }

  test("a MALFORMED key is named at the `<`, never silently NeverMatched") {
    val p = tree(Substitutions.none)(source)
    val b = bind(p, "com.demo.Shop#make(Class<?>)")
    assert(clue(b.why).exists(_.isInstanceOf[NotBound.Malformed]))
    assert(clue(b.why.get.detail).contains("type ARGUMENT"))
    assert(clue(b.why.get.detail).contains("index"))
  }

  test("a key naming an ENGINE-MINTED member is SyntheticTarget — the opposite of a typo") {
    val p0 = tree(Substitutions.none)(source)
    // The engine mints members the frontend never saw (a synthetic constructor is the case this
    // exists for). It does not exist yet, so it is hand-minted here — which is the honest test:
    // the binder's rule is STRUCTURAL ("the frontend walked this owner and did not record this
    // member"), so it does not need to know which phase minted it.
    val owner = p0.symbols.all.find(_.fullName == "com.demo.Shop").get
    val id    = SymId(p0.symbols.all.map(_.id.raw).max + 1)
    val minted = Symbol(id, "$synthetic", "com.demo.Shop#$synthetic", Flags(), owner.id,
      TypeRepr.MethodType(Nil, TypeRepr.NoType), descriptor = Some(Descriptor.empty))
    val p = p0.rebuilt(symbols = p0.symbols.updated(minted))

    val b = new PolicyBinder(p, p.members).bindMember("spec", "setting", "com.demo.Shop#$synthetic")
    assertEquals(b.why, Some(NotBound.SyntheticTarget("com.demo.Shop#$synthetic")))
    // The two readings that must never be confused: `NeverMatched` says "your key is a typo" and
    // sends its author looking for a member; this says "the engine created it and policy has no
    // standing to address it", which is engine work if it is anyone's.
    assert(clue(b.why.get.detail).contains("ENGINE minted"))
    assertNotEquals(b.why, Some(NotBound.NeverMatched))
  }

  test("a `static { }` block's key BINDS — an initialiser is a member the frontend read out of Java") {
    val p = tree(Substitutions.none)("com/demo/Init.java" ->
      """package com.demo;
        |public class Init {
        |  public static int a;
        |  static { a = 1; }
        |}""".stripMargin)
    // The counterpart to the `SyntheticTarget` test below, and the reason that test needs this one
    // beside it: the refusal is STRUCTURAL, so anything the frontend walks and the index does not
    // record is refused as engine-minted. A hand-written static initialiser is the case that
    // actually occurs — gdx-vfx keys `MethodBodyTransform` on one — and it must BIND.
    val b = binderOf(p).bindMembers("spec", "setting", "com.demo.Init#<clinit>")
    assert(clue(b).isBound)
    assertEquals(b.toOption.map(_.size), Some(1))
    assertNotEquals(b.why, Some(NotBound.SyntheticTarget("com.demo.Init#<clinit>")))
  }

  test("a key naming nothing at all is NeverMatched, and an EMPTY policy binds nothing and reports nothing") {
    val p = tree(Substitutions.none)(source)
    assertEquals(bind(p, "com.demo.Shop#nosuch").why, Some(NotBound.NeverMatched))
    assertEquals(bind(p, "com.demo.Nosuch#m").why, Some(NotBound.NeverMatched))
    val empty = binderOf(p)
    assertEquals(empty.bindings, Nil)
    assertEquals(empty.unbound, Nil)
  }

  test("every binding carries the (phase, setting) that asked for it — the never-fired report's row") {
    val p = tree(Substitutions.none)(source)
    val b = binderOf(p)
    b.bindMember("method-body-substitution", "MethodBodyTransform", "com.demo.Shop#nosuch")
    assertEquals(b.unbound.map(r => (r.phase, r.setting, r.entry)),
      List(("method-body-substitution", "MethodBodyTransform", "com.demo.Shop#nosuch")))
  }

  test("a TYPE key binds by ownership: declared binds, referenced-only is ExternalOnly") {
    val p = tree(Substitutions.none)(source)
    val b = binderOf(p)
    assert(b.bindType("spec", "dropTypes", "com.demo.Shop").isBound)
    assertEquals(b.bindType("spec", "dropTypes", "java.util.List").why,
      Some(NotBound.ExternalOnly("java.util.List")))
    // …and a rule whose entire subject IS external says so explicitly, rather than the default
    // silently admitting one.
    assert(b.bindType("spec", "portability", "java.util.List", Ownership.External).isBound)
  }
