package balticporter.corpus

import balticporter.core.{FrontendConfig, PortMap, PublishedSurface}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{CtorFunnel, Program, Surface, SymId, Tree, TrivialSurface}

import java.nio.file.{Files, Path}

/** The BASE-SURFACE CONTRACT, end to end (`DESIGN.md` §8.3).
  *
  * Two modules, because none of what this closes is visible in one: the whole family — D2, D4, D5,
  * D6 — is a run answering a whole-program question over a program that CONTAINS its base, and a
  * single-module fixture is exactly the shape under which every one of those answers is right.
  */
class BaseSurfaceSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------
  // a two-module Java tree: `base/` is resolved against, `dep/` is converted
  // -------------------------------------------------------------------------

  private def model(base: Map[String, String], dep: Map[String, String]): (Program, Path) =
    val root = Files.createTempDirectory("base-surface")
    def put(under: Path, files: Map[String, String]) = files.foreach { (rel, body) =>
      val p = under.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, body)
    }
    put(root.resolve("base"), base)
    put(root.resolve("dep"), dep)
    val types = SpoonTir.buildModel(
      FrontendConfig(root.resolve("dep"), dep.keys.toList.sorted, Nil,
                     resolutionRoots = List(root.resolve("base"))), lenient = true)
    (SpoonTir.fromTypes(types), root)

  /** the units whose Java lives under `dep/` — what `PortRun.partitionUnits` computes, spelled the
    * same way (by ORIGIN, realpathed) so the fixture and the run agree about ownership. */
  private def ownedUnits(p: Program, root: Path): List[Tree.ClassDef] =
    val dep = balticporter.core.RealPath.str(root.resolve("dep"))
    p.units.filter(u => balticporter.core.RealPath.str(Path.of(u.origin.javaPath)).startsWith(dep))

  private def fqn(p: Program, s: SymId): String = p.symbolOf(s).map(_.fullName).getOrElse("?")

  private def typeOf(p: Program, name: String): SymId =
    p.units.find(u => fqn(p, u.symbol) == name).map(_.symbol).getOrElse(
      fail(s"no unit named $name in ${p.units.map(u => fqn(p, u.symbol))}"))

  /** a contract carrying exactly the rows a test names — the artifact, assembled the way a base
    * publishes it, so a row here is a row a real run writes. */
  private def contract(module: String, rows: (String, Surface.TypeShape)*): PortMap.Map0 =
    PortMap.of(module, "eng", rows.map(_._1).toList, balticporter.tir.SrcMap.Recording(Nil),
      Set.empty, Set.empty, Set.empty, Set.empty, Map.empty,
      typeShapes = rows.map((n, s) => n -> Surface.render(s)).toMap)

  // -------------------------------------------------------------------------
  // 1. the ONE structural climb
  // -------------------------------------------------------------------------

  private val basePkg = Map(
    "p/Base.java" -> """package p;
      |public class Base {
      |  protected final int n;
      |  public Base(int n) { this.n = n; }
      |  public Base() { this(7); }
      |}""".stripMargin,
    "p/Holder.java" -> """package p;
      |public class Holder { public static final int X = 1; public static int twice(int v) { return v * 2; } }
      |""".stripMargin,
  )

  test("owns() answers MINE-vs-BASE, which is a different question from Program.owned") {
    val (p, root) = model(basePkg, Map("q/Mine.java" -> "package q; public class Mine extends p.Base { }"))
    val surface   = new PublishedSurface(p, ownedUnits(p, root))
    val mine      = typeOf(p, "q.Mine")
    val theirs    = typeOf(p, "p.Base")

    assert(surface.owns(mine))
    assert(!surface.owns(theirs), "a base unit is IN the program and is not this run's to answer for")
    // …and that is exactly where `Program.owned` cannot help: it roots on `program.units`, ALL of
    // them, so it is a program-vs-JDK filter and says `true` for both. Both predicates are right
    // for what they are asked; only one can say "the base emitted this and I did not".
    assert(p.owns(mine) && p.owns(theirs), "Program.owned is program-vs-JDK, and says yes to both")

    // an EXTERNAL symbol — the frontend interns it with `owner = SymId.None` and no definition.
    val external = p.symbols.all.find(s => s.fullName.startsWith("java.lang.")).map(_.id)
    external.foreach(e => assert(!surface.owns(e), s"an interned external is owned by nobody here"))
    assert(!surface.owns(SymId.None))
  }

  test("a member of an owned type is owned; a member of a BASE type is not — the climb, not the unit") {
    val (p, root) = model(basePkg, Map("q/Mine.java" -> "package q; public class Mine { int k; void go() {} }"))
    val surface   = new PublishedSurface(p, ownedUnits(p, root))
    val mineGo    = p.symbols.all.find(_.fullName == "q.Mine#go").map(_.id).get
    val baseN     = p.symbols.all.find(_.fullName == "p.Base#n").map(_.id).get
    assert(surface.owns(mineGo))
    assert(!surface.owns(baseN))
  }

  test("NEGATIVE: with no base declared, every non-owned question is Unknown and NAMES that") {
    val (p, root) = model(basePkg, Map("q/Mine.java" -> "package q; public class Mine extends p.Base { }"))
    val surface   = new PublishedSurface(p, ownedUnits(p, root))
    surface.typeShape(typeOf(p, "p.Base")) match
      case Surface.Answer.Unknown(why, _) => assert(clue(why).contains("declares no base port"))
      case other                          => fail(s"expected Unknown, got $other")
    assertEquals(surface.typeShape(typeOf(p, "q.Mine")), Surface.Answer.Own)
  }

  test("a PUBLISHED row is read, and it is read EMITTED-name to EMITTED-name") {
    val (p, root) = model(basePkg, Map("q/Mine.java" -> "package q; public class Mine extends p.Base { }"))
    val shape     = Surface.TypeShape(form = "class", primary = Some(balticporter.tir.Descriptor(
      List(balticporter.tir.Param.Prim("int")))), primaryKind = "unique-root", primaryVis = "public")
    val surface   = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod", "p.Base" -> shape)))
    assertEquals(surface.typeShape(typeOf(p, "p.Base")), Surface.Answer.Published(shape, "base-mod"))
  }

  // -------------------------------------------------------------------------
  // 2. D4 — the funnel's fixpoint stops spanning the base
  // -------------------------------------------------------------------------

  /** The D4 shape exactly: the base's `Base` has a paramful root, and the DEPENDENT adds a subclass
    * whose `extends` clause passes no arguments. In the base's own run nothing puts `Base` into
    * `needNilary`; in the dependent's, this subclass does. */
  private val dependentSubclass = Map(
    "q/Mine.java" -> """package q;
      |public class Mine extends p.Base {
      |  public Mine() { super(3); }
      |}""".stripMargin,
    "q/Bare.java" -> "package q; public class Bare extends p.Base { }",
  )

  private def primaryOf(p: Program, plans: CtorFunnel.Plans, name: String): List[String] =
    val cd = p.units.find(u => fqn(p, u.symbol) == name).get
    plans(cd).primaryParams.map(v => p.symbolOf(v.symbol).map(_.name).getOrElse("?"))

  test("D4: a dependent's EXTRA subclass no longer demotes a base class's primary") {
    val (p, root) = model(basePkg, dependentSubclass)
    val owned     = ownedUnits(p, root)

    // the pre-D1 behaviour, reproduced: with the whole program as the surface — which is what a
    // dependent had — `Bare extends p.Base` puts `Base` in `needNilary` and the fixpoint strips its
    // parameters. The base emitted them, so the two modules cannot compile together, and NOTHING in
    // the dependent's run disagrees with itself about it.
    val whole = CtorFunnel.Plans(p, Some(TrivialSurface(p)))
    assertEquals(clue(primaryOf(p, whole, "p.Base")), Nil, "the whole-program fixpoint DEMOTES the base")

    // …and with the view, the fixpoint's domain is this run's own classes, so it cannot reach the
    // base at all.
    val scoped = CtorFunnel.Plans(p, Some(new PublishedSurface(p, owned)))
    assertEquals(clue(primaryOf(p, scoped, "p.Base")), List("n"))
  }

  test("D4 NEGATIVE: the fixpoint still demotes an OWNED class — the guard is a scope, not a removal") {
    val (p, root) = model(basePkg, Map(
      "q/Mid.java"  -> "package q; public class Mid { private final int k; public Mid(int k) { this.k = k; } }",
      "q/Leaf.java" -> "package q; public class Leaf extends q.Mid { }",
    ))
    val scoped = CtorFunnel.Plans(p, Some(new PublishedSurface(p, ownedUnits(p, root))))
    assertEquals(clue(primaryOf(p, scoped, "q.Mid")), Nil,
      "an argument-free `extends` from a class this run DOES emit must still withhold the promotion")
  }

  test("D4 CROSS-CHECK: a non-wall class whose published row disagrees is FATAL, as an engine bug") {
    // §8.11's pin: for a non-wall class the local derivation is a pure function of the base's Java,
    // so it and the row MUST agree while both modules run one engine. That they do not means the
    // engine that published the map and the one running now compute different signatures — which is
    // the drift a purely local derivation cannot see, and the reason the row is kept for classes
    // that need no seeding.
    val (p, root) = model(basePkg, Map("q/Mine.java" -> "package q; public class Mine { }"))
    val wrong     = Surface.TypeShape(form = "class", primary = Some(balticporter.tir.Descriptor(
      List(balticporter.tir.Param.Named("String")))), primaryKind = "unique-root")
    val surface = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod", "p.Base" -> wrong)))
    val plans = CtorFunnel.Plans(p, Some(surface))
    plans(p.units.head) // force

    val fatal = surface.gaps.filter(_.fatal)
    assertEquals(clue(fatal).size, 1)
    assertEquals(fatal.head.subject, "p.Base")
    assert(clue(fatal.head.why).contains("(String)"), "the message names the ROW it disagrees with")
    assert(clue(fatal.head.fix).startsWith("§1(a) ENGINE"), "…and which of §1's three kinds the fix is")
  }

  test("D4 CROSS-CHECK NEGATIVE: an AGREEING row is silent — the check is not noise") {
    val (p, root) = model(basePkg, Map("q/Mine.java" -> "package q; public class Mine { }"))
    val right = Surface.TypeShape(form = "class", primary = Some(balticporter.tir.Descriptor(
      List(balticporter.tir.Param.Prim("int")))), primaryKind = "unique-root")
    val surface = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod", "p.Base" -> right, "p.Holder" -> right.copy(
        primary = Some(balticporter.tir.Descriptor(Nil))))))
    val plans = CtorFunnel.Plans(p, Some(surface))
    plans(p.units.head)
    assertEquals(clue(surface.gaps).filter(_.fatal), Nil)
  }

  test("an Unknown about a class whose primary CANNOT drift is a finding, not a failure") {
    // The rule is per QUESTION. `p.Holder` has only the implicit nilary constructor, so its plan is
    // invariant under any set of subclasses and the local derivation IS the base's answer; saying so
    // is worth a row and is not worth failing a run for.
    val (p, root) = model(basePkg, Map("q/Mine.java" -> "package q; public class Mine { }"))
    val surface   = new PublishedSurface(p, ownedUnits(p, root), Nil)
    val plans     = CtorFunnel.Plans(p, Some(surface))
    plans(p.units.head)
    val holder = surface.gaps.find(_.subject == "p.Holder")
    assert(clue(holder).isDefined)
    assertEquals(holder.get.fatal, false)
    assert(clue(holder.get.fix).contains("does not depend on its"))
  }

  // -------------------------------------------------------------------------
  // 3. D6's cross-module face — attribution, because there is no local repair
  // -------------------------------------------------------------------------

  test("D6: naming a base type the base emitted as an `object` is a finding ATTRIBUTED to the base") {
    val (p, root) = model(basePkg, Map(
      "q/Uses.java" -> "package q; public class Uses { p.Holder h; }"))
    val collapsed = Surface.TypeShape(form = "object", primaryKind = "unique-root")
    val surface = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod", "p.Holder" -> collapsed)))
    val emitter = new TirEmitter(p, surfaceView = Some(surface))
    emitter.emit
    val gaps = emitter.surfaceGaps
    assertEquals(clue(gaps).map(_.subject), List("p.Holder"))
    assertEquals(gaps.head.module, Some("base-mod"))
    assert(clue(gaps.head.why).contains("bare `object`"))
    // …and NOT fatal: the base is emitted and gone, so there is no local repair — the contract buys
    // attribution and nothing more (§8.3's honest-scope statement).
    assertEquals(gaps.head.fatal, false)
    assert(clue(gaps.head.fix).contains("nothing in this module can repair it"))
  }

  test("D6 NEGATIVE: a base type published as a `class` produces NO finding") {
    val (p, root) = model(basePkg, Map(
      "q/Uses.java" -> "package q; public class Uses { p.Holder h; }"))
    val kept = Surface.TypeShape(form = "class", primaryKind = "unique-root")
    val surface = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod", "p.Holder" -> kept)))
    val emitter = new TirEmitter(p, surfaceView = Some(surface))
    emitter.emit
    assertEquals(clue(emitter.surfaceGaps), Nil)
  }

  // -------------------------------------------------------------------------
  // 3.5 D5 — a REPLAY may not widen a `private` member this run does not EMIT
  // -------------------------------------------------------------------------

  /** The D5 shape exactly. `p.Base(int)` runs `touch()`, which is `private` in the base; a dependent
    * subclass writes `super(3)`, which scala cannot express, so `replayFor` would lift `Base`'s
    * constructor body into `Mine` — and there `touch()` is not reachable. Within one module that is
    * repaired by widening; across the boundary the DECLARATION is in a file this run does not write.
    */
  private val privateBase = Map(
    "p/Base.java" -> """package p;
      |public class Base {
      |  protected int n;
      |  public Base() { }
      |  public Base(int n) { this.n = n; touch(); }
      |  private void touch() { this.n = this.n + 1; }
      |}""".stripMargin,
  )

  /** Two roots reaching DIFFERENT parent constructors, so the synthesis refuses and the class keeps
    * a nilary primary — which is what makes `Mine(int)`'s `super(a)` a SECONDARY's super call, the
    * only shape `replayFor` exists for (`ModelInstanceHack` in gdx-gltf, exactly). */
  private val privateHeir = Map(
    "q/Mine.java" -> """package q;
      |public class Mine extends p.Base {
      |  public Mine() { }
      |  public Mine(int a) { super(a); }
      |}""".stripMargin,
  )

  /** a published MEMBER row, keyed the way the source map keys one — `owner#name(params)` for an
    * executable, `owner#name` for a field. */
  private def withMembers(m: PortMap.Map0, rows: List[PortMap.Entry]): PortMap.Map0 =
    m.copy(entries = m.entries ++ rows)

  private def memberRow(key: String, vis: String): PortMap.Entry =
    PortMap.Entry("member", key, key, PortMap.Disposition.Ported,
                  shape = Surface.render(Surface.MemberShape(vis = vis)))

  private def memberId(p: Program, fq: String): SymId =
    p.symbols.all.find(_.fullName == fq).map(_.id).getOrElse(fail(s"no symbol $fq"))

  private def replayed(p: Program, s: Surface, cls: String): Boolean =
    val plans = CtorFunnel.Plans(p, Some(s))
    val cd    = p.units.find(u => fqn(p, u.symbol) == cls).get
    CtorFunnel.ctorsOf(p, cd.body).exists(d => plans.replayFor(cd, d).isDefined)

  test("D5: a replay reaching a BASE's `private` member is REFUSED, and the refusal is attributed") {
    val (p, root) = model(privateBase, privateHeir)
    // the pre-D5 behaviour, reproduced: with the whole program as the surface the owner has a TREE,
    // which is what `classOfSym(...).isDefined` asked, so the replay is accepted and the emitted
    // call to a `private` base member does not compile (4 errors on gdx-gltf).
    assert(replayed(p, TrivialSurface(p), "q.Mine"), "the whole-program answer ACCEPTS the replay")

    val published = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")),
                                     List(memberRow("p.Base#touch()", "private")))))
    assert(!replayed(p, published, "q.Mine"), "a `private` published member REFUSES the replay")
    val gap = published.gaps
    assertEquals(clue(gap).size, 1)
    assert(gap.head.subject.startsWith("p.Base#touch"), gap.head.subject)
    assertEquals(gap.head.module, Some("base-mod"))
    assertEquals(gap.head.fatal, false, "a withheld rewrite did not shape emitted text — only the BASE can fix it")
    assert(clue(gap.head.fix).contains("§1(a) ENGINE, in the BASE"), gap.head.fix)
  }

  test("D5 NEGATIVE: a member the base published PUBLIC is reachable, and no gap is recorded") {
    val (p, root) = model(privateBase, privateHeir)
    val published = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")),
                                     List(memberRow("p.Base#touch()", "public")))))
    assert(replayed(p, published, "q.Mine"))
    assertEquals(published.gaps, Nil)
  }

  test("D5: the WITHIN-module widening is untouched — the guard is a scope, not a removal") {
    // libGDX core makes 22 sound `WidenedVisibility` decisions of its own; a blanket refusal
    // regresses the base to fix the dependent (`PROGRESS.md` §8.5).
    val (p, root) = model(Map("z/Unused.java" -> "package z; public class Unused { }"),
                          privateBase ++ privateHeir)
    assert(replayed(p, new PublishedSurface(p, ownedUnits(p, root)), "q.Mine"),
      "both classes are THIS run's, so the widening is real and the replay stands")
  }

  test("a member lookup finds a METHOD row — the published key carries a descriptor and a symbol does not") {
    val (p, root) = model(privateBase, privateHeir)
    val rows = List(
      memberRow("p.Base#touch()", "private"),
      memberRow("p.Base#n", "public"),
    )
    val s = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")), rows)))
    // `Symbol.fullName` is `owner#name`; the row's key is `owner#name(params)`. Looking the one up
    // by the other found every FIELD and no METHOD, silently, from the day it was written.
    assertEquals(s.memberShape(memberId(p, "p.Base#touch")).published.map(_.vis), Some("private"))
    assertEquals(s.memberShape(memberId(p, "p.Base#n")).published.map(_.vis), Some("public"))
  }

  test("a FIELD and a METHOD of one name are TWO rows, and each symbol gets its own") {
    // §4.55's whole reason for existing, arriving at the lookup: java lets `FileHandle.file` be a
    // field AND `file()` a method, the field is renamed `file$field`, and the two rows therefore
    // DISAGREE by construction. Read as one overload set every renamed field in every base answered
    // `Unknown` — 272 of them on one dependent, each a false report about a row sitting right there.
    val (p, root) = model(privateBase, privateHeir)
    val rows = List(
      memberRow("p.Base#n", "public").copy(shape = Surface.render(Surface.MemberShape(name = "n$field"))),
      memberRow("p.Base#n()", "private"),
    )
    val s = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")), rows)))
    // the FIELD's key has no parentheses; a nilary METHOD's still does, so the two never collide
    assertEquals(s.memberShape(memberId(p, "p.Base#n")).published.map(_.name), Some("n$field"))
    assertEquals(s.gaps, Nil)
  }

  test("a `Dropped` member row is not a SHAPE — it may not make the overload set disagree") {
    // A shape answers "what did you EMIT this member as", and a member the base did not emit has
    // none. Read into the overload set anyway, one refused constructor makes every sibling overload
    // of that name answer `Unknown` — a row published to REMOVE a blind spot creating one, at the
    // members beside it. The `emitted.nonEmpty` filter used to hide this by accident: a policy drop
    // leaves that column empty, and an ENGINE refusal keeps it, because the owning type IS emitted.
    val (p, root) = model(privateBase, privateHeir)
    val rows = List(
      memberRow("p.Base#touch()", "private"),
      PortMap.Entry("member", "p.Base#touch(int)", "p.Base#touch(int)", PortMap.Disposition.Dropped,
                    shape = Surface.render(Surface.MemberShape(refusal = "some-rule(X1)"))),
    )
    val s = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")), rows)))
    assertEquals(s.memberShape(memberId(p, "p.Base#touch")).published.map(_.vis), Some("private"))
  }

  test("…and DISAGREEING overloads of one name are Unknown, never one of them picked") {
    val (p, root) = model(privateBase, privateHeir)
    val rows = List("private", "public").zipWithIndex.map { (v, i) =>
      memberRow(s"p.Base#touch(int$i)", v)
    }
    val s = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")), rows)))
    s.memberShape(memberId(p, "p.Base#touch")) match
      case Surface.Answer.Unknown(why, m) =>
        assert(clue(why).contains("do not agree"), why)
        assertEquals(m, Some("base-mod"))
      case other => fail(s"expected Unknown, got $other")
  }

  // -------------------------------------------------------------------------
  // 3.6 §4.55 — a DESCENDANT clash may not rename a field this run does not EMIT
  // -------------------------------------------------------------------------

  /** The face with **0 corpus sites**, which is exactly why it is pinned here rather than measured.
    *
    * `p.Base` declares a field `x`; the DEPENDENT declares `q.Heir extends p.Base` with a method
    * `x()`. §4.55's field-vs-method pass is whole-program — a field is renamed iff this class or any
    * DESCENDANT declares a method of that name — and a dependent's `Program` contains its base with
    * EXTRA descendants the base's own run never saw. So the dependent renames the BASE's field, and
    * every reference it emits spells `x$field` against a base that wrote `x`: it compiles alone and
    * cannot compile against the module it resolves against.
    */
  private val clashBase = Map(
    "p/Base.java" -> """package p;
      |public class Base { public int x; }""".stripMargin,
  )

  private val clashHeir = Map(
    "q/Heir.java" -> """package q;
      |public class Heir extends p.Base {
      |  public int x() { return 1; }
      |}""".stripMargin,
  )

  private def emittedWith(p: Program, s: Surface): String =
    new TirEmitter(p, surfaceView = Some(s)).emit

  test("§4.55: a DEPENDENT's method does not rename the BASE's field — 0 corpus sites, and this is why") {
    val (p, root) = model(clashBase, clashHeir)
    // the pre-contract answer, reproduced: with the whole program as the surface the base's field is
    // renamed by a descendant the base never saw.
    assert(clue(emittedWith(p, TrivialSurface(p))).contains("x$field"))

    // …and with the base's row read, the base's own answer is FOLLOWED: it published no `name=`, so
    // the field keeps java's name and this run renames nothing.
    val published = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")),
                                     List(memberRow("p.Base#x", "public")))))
    assert(!clue(emittedWith(p, published)).contains("x$field"))
    assertEquals(published.gaps, Nil)
  }

  test("…and where the BASE DID rename it, the dependent spells the base's name, not its own") {
    val (p, root) = model(clashBase, clashHeir)
    val rows = List(PortMap.Entry("member", "p.Base#x", "p.Base#x", PortMap.Disposition.Ported,
                                  shape = Surface.render(Surface.MemberShape(name = "x$renamedByTheBase"))))
    val published = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> withMembers(contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class")), rows)))
    val out = emittedWith(p, published)
    assert(clue(out).contains("x$renamedByTheBase"), out)
    assert(!out.contains("x$field"), out)
  }

  test("NEGATIVE: an OWNED field still moves — the guard is a scope, not a removal") {
    val (p, root) = model(clashBase, Map(
      "q/Own.java"  -> "package q; public class Own { public int y; }",
      "q/Sub.java"  -> "package q; public class Sub extends q.Own { public int y() { return 1; } }",
    ))
    val published = new PublishedSurface(p, ownedUnits(p, root))
    assert(clue(emittedWith(p, published)).contains("y$field"))
  }

  test("an UNKNOWN base keeps the local derivation and RECORDS the question") {
    val (p, root) = model(clashBase, clashHeir)
    val published = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod", "p.Base" -> Surface.TypeShape(form = "class"))))
    assert(clue(emittedWith(p, published)).contains("x$field"))
    assertEquals(clue(published.gaps).map(_.subject), List("p.Base#x"))
    assertEquals(published.gaps.head.fatal, false)
  }

  // -------------------------------------------------------------------------
  // 3.7 the `export` exclusion list reads the base's PUBLISHED `statics=`
  // -------------------------------------------------------------------------

  /** What `export Parent.{… => _, *}` must exclude is the set of names the PARENT'S COMPANION
    * actually delivers, and for a base parent that is a fact about the base's EMITTED output — a
    * static it renamed, a static the manifest dropped. Re-derived from the base's java the dependent
    * gets java's names back, which is right exactly when nothing moved. */
  private val staticBase = Map(
    "p/Holder.java" -> """package p;
      |public class Holder {
      |  public static final int LIMIT = 3;
      |  public static int twice(int v) { return v * 2; }
      |}""".stripMargin,
  )

  private val staticHeir = Map(
    "q/Uses.java" -> """package q;
      |public class Uses extends p.Holder {
      |  public static final int LIMIT = 9;
      |}""".stripMargin,
  )

  test("a base whose statics the manifest DROPPED delivers none, so no `export` is written at all") {
    val (p, root) = model(staticBase, staticHeir)
    // the base's java has two statics; its EMITTED output has none, because its manifest dropped
    // them. `export P.*` against a type with no companion is an error outright, and only the base's
    // row can say so — the java the dependent parsed says the opposite.
    val published = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod",
        "p.Holder" -> Surface.TypeShape(form = "class", companion = false, statics = Nil))))
    val out = emittedWith(p, published)
    assert(!clue(out).contains("export p.Holder."), out)
    assertEquals(published.gaps, Nil)
  }

  test("…and a base that DID emit them is re-exported, with the names it actually emitted") {
    val (p, root) = model(staticBase, staticHeir)
    val published = new PublishedSurface(p, ownedUnits(p, root),
      List("base-mod" -> contract("base-mod",
        "p.Holder" -> Surface.TypeShape(form = "class", companion = true,
                                        statics = List("LIMIT", "twice")))))
    assert(clue(emittedWith(p, published)).contains("export p.Holder."))
    assertEquals(published.gaps, Nil)
  }

  test("NEGATIVE: with no published row the local derivation stands, and the question is RECORDED") {
    val (p, root) = model(staticBase, staticHeir)
    val published = new PublishedSurface(p, ownedUnits(p, root))
    val out = emittedWith(p, published)
    assert(clue(out).contains("export p.Holder."), out)
    assert(clue(published.gaps).exists(_.subject == "p.Holder"), published.gaps.map(_.subject).mkString(", "))
    assert(published.gaps.forall(!_.fatal))
  }

  // -------------------------------------------------------------------------
  // 4. what the contract PUBLISHES — the emitter's own recording
  // -------------------------------------------------------------------------

  test("the contract records the COLLAPSE, which no other artifact can say") {
    // `members.tsv` records `Holder`'s kind as `class`; it emits as `object Holder`. That gap is
    // D6's cross-module face at its source, and it is the row a dependent reads.
    val root = Files.createTempDirectory("base-surface-own")
    Files.createDirectories(root.resolve("p"))
    Files.writeString(root.resolve("p/Holder.java"),
      "package p; public class Holder { public static final int X = 1; }")
    Files.writeString(root.resolve("p/Pair.java"),
      "package p; public class Pair { final int a; final int b; public Pair(int a, int b) { this.a = a; this.b = b; } }")
    val types = SpoonTir.buildModel(FrontendConfig(root, List("p/Holder.java", "p/Pair.java"), Nil), lenient = true)
    val p     = SpoonTir.fromTypes(types)
    val e     = new TirEmitter(p)
    assert(clue(e.emit).contains("object Holder"))
    val shapes = e.emittedShapes
    assertEquals(clue(shapes.types.get("p.Holder")).map(_.form), Some("object"))
    assertEquals(shapes.types.get("p.Pair").map(_.form), Some("class"))
    // …and the primary's slots in the DESCRIPTOR grammar — the spelling a manifest key uses, so a
    // contract row and a policy key are never in two grammars.
    assertEquals(shapes.types.get("p.Pair").flatMap(_.primary).map(_.render), Some("int,int"))
    assertEquals(shapes.types.get("p.Pair").map(_.primaryKind), Some("unique-root"))
  }
