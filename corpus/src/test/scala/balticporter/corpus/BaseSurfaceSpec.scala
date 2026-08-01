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
