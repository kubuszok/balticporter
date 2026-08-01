package balticporter.transform

import balticporter.core.PolicyIssue
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** `type-redirect`'s MEMBER RENAMES — a target that spells the member differently (DESIGN.md §8.5).
  *
  * The assertions that matter here are the two design rulings, and both are negatives:
  *
  *   - the rename runs against the PRE-redirect override graph, INSIDE the one phase. The
  *     `ordering` tests construct the wrong order and measure what it destroys: the component
  *     splits into singletons, and the whole-or-none guarantee then guarantees nothing — an
  *     anchored declaration stops refusing the rest, and half a hierarchy is renamed. That
  *     compiles, and no count moves for it.
  *   - the new name must exist on the TARGET. A rename to a name the target does not declare emits
  *     code calling a method that is not there, three lanes downstream in somebody else's
  *     repository.
  */
class TypeRedirectMemberRenameSpec extends munit.FunSuite:

  // ---- fixtures ------------------------------------------------------------------------------

  /** An interface, two implementors, a sub-implementor and an external caller — the shape a
    * `Disposable`-style redirect actually meets. NOTHING here has an unparsed parent, so the
    * component is movable and every refusal below is caused by the thing it names. */
  private val clean =
    """package com.demo;
      |
      |interface Disposable {
      |  /** frees it. */
      |  void dispose();
      |}
      |
      |class Buffer implements Disposable {
      |  public void dispose() {}
      |}
      |
      |class Pooled implements Disposable {
      |  public void dispose() {}
      |}
      |
      |class Sub extends Buffer {
      |  public void dispose() {}
      |}
      |
      |class Client {
      |  void go(Disposable d) { d.dispose(); }
      |}
      |""".stripMargin

  /** …and the same hierarchy with ONE implementor that also implements a type this program never
    * parsed. `java.util.EventListener` is not in `ExternalSurface.jdkPlatform` — deliberately, its
    * surface being no business of the engine's — so it is UNKNOWN, and unknown anchors. */
  private val anchored = clean.replace(
    "class Pooled implements Disposable {",
    "class Pooled implements Disposable, java.util.EventListener {")

  private def parse(java: String): Program = SpoonTir.fromSource(java, "Demo.java")

  private def phase(renames: Map[String, String],
                    to: String = "java.lang.AutoCloseable"): TypeRedirectTransform =
    new TypeRedirectTransform(
      redirects     = Map("com.demo.Disposable" -> to),
      memberRenames = if renames.isEmpty then Map.empty else Map("com.demo.Disposable" -> renames))

  /** the BEFORE program is handed back with everything else on purpose: a rename rewrites
    * `fullName` too, so "what is `com.demo.Buffer#dispose` called now" can only be asked of the
    * SYMBOL the pre-phase program named, never of the post-phase name table. */
  private def run(java: String, p: TypeRedirectTransform): Ported =
    val before       = parse(java)
    val (after, log) = Pipeline.runTraced(before, List(p))
    Ported(before, after, new TirEmitter(after, notes = log).emit, log)

  private case class Ported(before: Program, after: Program, out: String, log: DecisionLog):
    def nameOf(fqn: String): Option[String] = nameIn(before, after, fqn)

  private def nameIn(before: Program, after: Program, fqn: String): Option[String] =
    after.symbolOf(sym(before, fqn)).map(_.name)

  private def sym(p: Program, fqn: String): SymId =
    p.symbols.all.find(_.fullName == fqn).map(_.id).getOrElse(fail(s"no symbol named $fqn"))

  /** the emitted CODE with the porter notes stripped — see the §4.575 note at its one use. */
  private def code(out: String): String =
    out.linesIterator.filterNot(l => l.contains(PorterNote.Marker) || l.trim.startsWith("—")).mkString("\n")

  // ---- 1. the happy path ---------------------------------------------------------------------

  test("every declaration of the component takes the TARGET's name, and the redirect follows") {
    val ph = phase(Map("dispose" -> "close"))
    val r  = run(clean, ph)
    val out = r.out

    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)

    // the interface, both implementors and the sub-implementor — all of a component, or none
    List("com.demo.Disposable#dispose", "com.demo.Buffer#dispose",
         "com.demo.Pooled#dispose", "com.demo.Sub#dispose")
      .foreach(f => assertEquals(r.nameOf(f), Some("close"), f))

    // …and the CALL SITE, for free: the emitter renders every reference through the symbol's name
    assert(clue(out).contains("d.close()"), "the call site did not follow the symbol")
    // …and NOTHING is still called `dispose` — read off the CODE, with the notes stripped, because
    // a note names the upstream member on purpose (§4.575's `from=`) and a text search that forgets
    // that reports a phantom (`SubstitutionCheck.dangling`'s first run with notes).
    assert(!code(out).contains("dispose"), s"`dispose` survives somewhere:\n$out")

    // the redirect itself still happened
    assert(out.contains("java.lang.AutoCloseable"), out)

    // one RenamedMember decision per renamed declaration, each carrying the manifest entry
    val renamed = r.log.all.filter(_.kind == Decision.Kind.RenamedMember)
    assertEquals(clue(renamed).size, 4)
    assert(renamed.forall(_.reason == Reason.Configured("type-redirect", "com.demo.Disposable#dispose -> close")))
    // …and the note is beside the code (§4.575), after the upstream comment, never before it
    assert(clue(out).contains("/* porter: renamed-member"), out)
    assert(out.contains("phase=type-redirect"))
    val doc  = out.indexOf("frees it.")
    val note = out.indexOf("/* porter:", doc)
    assert(doc >= 0 && note > doc, s"the note displaced the upstream trivia:\n$out")
  }

  test("a bound overload can be named precisely — the descriptor form works from day one") {
    val ph = new TypeRedirectTransform(
      redirects     = Map("com.demo.Disposable" -> "java.lang.AutoCloseable"),
      memberRenames = Map("com.demo.Disposable" -> Map("dispose()" -> "close")))
    val r = run(clean, ph)
    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    assertEquals(r.nameOf("com.demo.Sub#dispose"), Some("close"))
  }

  // ---- 2. the ORDERING negative — the ruling this task exists for ----------------------------

  test("ORDERING: after the redirect the component is SINGLETONS — the guarantee has nothing left") {
    val before = parse(clean)
    val pre    = OverrideGraph.build(before)
    assertEquals(clue(pre.closureOf(sym(before, "com.demo.Buffer#dispose")).members).size, 4,
      "the fixture must have a four-declaration component, or this test proves nothing")

    // redirect FIRST, exactly as a two-phase-in-series pipeline would
    val (after, _) = Pipeline.runTraced(before, List(phase(Map.empty)))
    val post       = OverrideGraph.build(after)
    val split      = post.closureOf(sym(after, "com.demo.Buffer#dispose")).members

    // `Buffer` and `Sub` still have an OWNED parent edge between them; the interface and the other
    // implementor are gone from the component, because every parent edge to `Disposable` now
    // points at `java.lang.AutoCloseable`, which this program does not declare.
    assert(!split.contains(sym(after, "com.demo.Disposable#dispose")),
      "the interface's declaration is still in the component — the fixture did not redirect")
    assert(!split.contains(sym(after, "com.demo.Pooled#dispose")),
      "the second implementor is still in the component")
    assertEquals(clue(post.closureOf(sym(after, "com.demo.Pooled#dispose")).members).size, 1)
    assertEquals(clue(post.closureOf(sym(after, "com.demo.Disposable#dispose")).members).size, 1)
  }

  test("ORDERING: renaming AFTER a redirect renames HALF a hierarchy, and reports success") {
    // The same split, with the consequence that makes it a correctness defect rather than a
    // curiosity: `Pooled` is anchored by an unparsed parent. Pre-redirect that refuses the WHOLE
    // component. Post-redirect it refuses only itself, and the other three declarations move.
    val before = parse(anchored)
    val log    = new DecisionLog

    val preGraph = OverrideGraph.build(before)
    val preReq   = List(MemberRenamer.Request(sym(before, "com.demo.Buffer#dispose"), "close",
      Reason.Configured("type-redirect", "k"), "k"))
    val (preOut, preRefusals) = MemberRenamer.rename(before, preGraph, preReq,
      MemberRenamer.OnCollision.Refuse, log)
    assertEquals(clue(preRefusals).size, 1, "the anchored component must refuse WHOLE")
    assert(preRefusals.head.why.contains("java.util.EventListener"), preRefusals.head.render)
    assertEquals(nameIn(before, preOut, "com.demo.Buffer#dispose"), Some("dispose"), "nothing moved")

    val (after, _) = Pipeline.runTraced(before, List(phase(Map.empty)))
    val postGraph  = OverrideGraph.build(after)
    val postReq    = List(MemberRenamer.Request(sym(after, "com.demo.Buffer#dispose"), "close",
      Reason.Configured("type-redirect", "k"), "k"))
    val (postOut, postRefusals) = MemberRenamer.rename(after, postGraph, postReq,
      MemberRenamer.OnCollision.Refuse, new DecisionLog)

    assertEquals(clue(postRefusals), Nil, "the anchor is no longer visible from this component")
    assertEquals(nameIn(after, postOut, "com.demo.Buffer#dispose"), Some("close"))
    assertEquals(nameIn(after, postOut, "com.demo.Sub#dispose"), Some("close"))
    // …and these two did NOT move, which is the broken program: an `AutoCloseable` that does not
    // implement `close`, and an interface declaring a method nothing implements.
    assertEquals(nameIn(after, postOut, "com.demo.Pooled#dispose"), Some("dispose"))
    assertEquals(nameIn(after, postOut, "com.demo.Disposable#dispose"), Some("dispose"))
  }

  test("the PHASE gets it right: the anchored component refuses WHOLE, and is counted") {
    val ph = phase(Map("dispose" -> "close"))
    val r  = run(anchored, ph)

    List("com.demo.Disposable#dispose", "com.demo.Buffer#dispose",
         "com.demo.Pooled#dispose", "com.demo.Sub#dispose")
      .foreach(f => assertEquals(r.nameOf(f), Some("dispose"), s"$f moved under an anchor"))

    val fs = ph.policyReport.of(PolicyIssue.Unverifiable)
    assertEquals(clue(fs).size, 1, ph.policyReport.render)
    assertEquals(fs.head.key, "com.demo.Disposable#dispose -> close")
    assert(fs.head.detail.contains("java.util.EventListener"), fs.head.render)

    val scopedOut = r.log.all.filter(_.kind == Decision.Kind.ScopedOut)
    assertEquals(clue(scopedOut).size, 1)
    assertEquals(scopedOut.head.detail.get("refused"), Some("member-rename"))
    // the redirect itself is NOT refused — a rename that could not run does not un-redirect a type
    assert(clue(r.out).contains("java.lang.AutoCloseable"), r.out)
  }

  // ---- 3. the TARGET has to declare the new name ---------------------------------------------

  test("a rename to a name the target does NOT declare is refused, with what the target has") {
    val ph = phase(Map("dispose" -> "shutdown"))
    val r  = run(clean, ph)

    assertEquals(r.nameOf("com.demo.Buffer#dispose"), Some("dispose"))
    val fs = ph.policyReport.of(PolicyIssue.Unverifiable)
    assertEquals(clue(fs).size, 1, ph.policyReport.render)
    assert(fs.head.detail.contains("`java.lang.AutoCloseable` does not declare `shutdown`"), fs.head.render)
    assert(fs.head.detail.contains("close"), "the refusal must say what the target DOES have")
    assertEquals(r.log.all.count(_.kind == Decision.Kind.ScopedOut), 1)
  }

  test("an UNKNOWN target cannot refuse anything — the target compiler stays the gate") {
    // The ordinary case: a shape-compatible type the port ships itself, which this engine has never
    // seen. Refusing there would make the mechanism unusable for every redirect that is not to a
    // JDK platform type.
    val ph = phase(Map("dispose" -> "release"), to = "sge.util.Releasable")
    val r  = run(clean, ph)
    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    assertEquals(r.nameOf("com.demo.Buffer#dispose"), Some("release"))
  }

  // ---- 4. the OWNED-AND-PARSED redirect ------------------------------------------------------

  test("the source type may be one this program DECLARES — statics twin, members rename") {
    // No existing port redirects a type it owns and parses; `Disposable` is exactly that case, and
    // the twin machinery's contract covers it. What the port must do IN ADDITION is drop the type:
    // the declaration is still emitted, because a redirect re-points REFERENCES and never deletes a
    // declaration. That is asserted here so the enablement cannot be surprised by it.
    val java =
      """package com.demo;
        |
        |interface Disposable {
        |  int LIMIT = 4;
        |  void dispose();
        |}
        |
        |class Buffer implements Disposable {
        |  public void dispose() {}
        |  int cap() { return Disposable.LIMIT; }
        |}
        |""".stripMargin
    val ph  = phase(Map("dispose" -> "close"))
    val r   = run(java, ph)
    val out = r.out

    assertEquals(ph.policyReport.findings, Nil, ph.policyReport.render)
    assertEquals(r.nameOf("com.demo.Buffer#dispose"), Some("close"))
    assertEquals(r.nameOf("com.demo.Disposable#dispose"), Some("close"))
    // the interface CONSTANT reaches its twin, owned by the target
    assert(clue(out).contains("java.lang.AutoCloseable.LIMIT"), out)
    assert(out.contains("extends java.lang.AutoCloseable"), out)
    // …and the upstream declaration is still there. `Substitutions.dropTypes` is the port's job.
    assert(out.contains("trait Disposable"), s"the owned declaration vanished, which this phase does not do:\n$out")
  }

  // ---- 5. the never-fired half ---------------------------------------------------------------

  test("a rename naming a member that does not exist reports through the BINDER") {
    val ph = phase(Map("release" -> "close"))
    val r  = run(clean, ph)
    val fs = ph.policyReport.of(PolicyIssue.NeverMatched)
    assertEquals(clue(fs).size, 1, ph.policyReport.render)
    assertEquals(fs.head.key, "com.demo.Disposable#release")
    assert(!r.out.contains("close"), "NEVER INVENT A MEMBER")
    assertEquals(r.nameOf("com.demo.Buffer#dispose"), Some("dispose"))
  }

  test("a `memberRenames` block for a type nothing REDIRECTS is malformed, not a silent no-op") {
    val ph = new TypeRedirectTransform(
      redirects     = Map("com.demo.Disposable" -> "java.lang.AutoCloseable"),
      memberRenames = Map("com.demo.Buffer" -> Map("dispose" -> "close")))
    val r  = run(clean, ph)
    val fs = ph.policyReport.of(PolicyIssue.Malformed)
    assertEquals(clue(fs).size, 1, ph.policyReport.render)
    assertEquals(fs.head.key, "com.demo.Buffer")
    assertEquals(r.nameOf("com.demo.Buffer#dispose"), Some("dispose"))
  }

  test("the member SEGMENT grammar is `MemberKey`'s own — the splice lives in one file") {
    // The reader never builds `owner + "#" + member` itself (`PolicyKeyLintSpec`), so the composition
    // is `MemberKey.parseIn` and it answers with the same grammar every other key is held to.
    assertEquals(MemberKey.parseIn("a.B", "dispose").map(_.render), Right("a.B#dispose"))
    assertEquals(MemberKey.parseIn("a.B", "dispose()").map(_.render), Right("a.B#dispose()"))
    assertEquals(MemberKey.parseIn("a.B", "m(int,String)").map(_.render), Right("a.B#m(int,String)"))
    // a segment that names its OWN owner is a policy author writing the key twice, not a key
    assert(MemberKey.parseIn("a.B", "a.B#dispose").isLeft)
    // …and the malformed key REPORTED is the segment the author wrote, not the spliced string
    assertEquals(MemberKey.parseIn("a.B", "m(Class<T>)").left.map(_.key), Left("m(Class<T>)"))
  }

  test("a malformed member segment names itself, and does not bind") {
    val ph = phase(Map("dispose(Class<T>)" -> "close"))
    run(clean, ph)
    val fs = ph.policyReport.of(PolicyIssue.Malformed)
    assertEquals(clue(fs).size, 1, ph.policyReport.render)
    assertEquals(fs.head.key, "dispose(Class<T>)")
  }

  // ---- 6. the never-fired PATH: default-off --------------------------------------------------

  test("no `memberRenames` is byte-identical to the phase before this feature existed") {
    val plain = new TirEmitter(parse(clean)).emit
    val off = run(clean, new TypeRedirectTransform(Map.empty))
    assertEquals(off.out, plain)
    assertEquals(off.log.all, Nil)

    // …and a redirect WITHOUT renames emits exactly what a redirect alone always emitted
    val redirectOnly = run(clean, phase(Map.empty)).out
    val withEmpty    = run(clean, new TypeRedirectTransform(
      Map("com.demo.Disposable" -> "java.lang.AutoCloseable"), Map("com.demo.Disposable" -> Map.empty))).out
    assertEquals(withEmpty, redirectOnly)
  }

  test("the SURFACE FINGERPRINT is unchanged for an entry with no renames (§1.5)") {
    assertEquals(new TypeRedirectTransform(Map("a.B" -> "c.D")).surfaceFingerprint, "a.B->c.D")
    assertEquals(
      new TypeRedirectTransform(Map("a.B" -> "c.D"), Map("a.B" -> Map("x" -> "y", "p" -> "q")))
        .surfaceFingerprint,
      "a.B->c.D[p=q,x=y]")
  }
