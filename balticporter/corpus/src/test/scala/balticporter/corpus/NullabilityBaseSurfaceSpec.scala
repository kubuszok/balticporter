package balticporter.corpus

import balticporter.core.{FrontendConfig, ManifestAgreement, PolicyIssue, PortManifest, RealPath}
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*
import balticporter.transform.NullabilityTransform

import java.nio.file.{Files, Path}

/** A DEPENDENT's own annotation FQN, reaching its BASE's declarations — the one policy key that
  * selects a shared surface without naming any part of it. */
class NullabilityBaseSurfaceSpec extends munit.FunSuite:

  // -------------------------------------------------------------------------
  // a two-module Java tree: `base/` is resolved against, `dep/` is converted
  // -------------------------------------------------------------------------

  private val annotation =
    """package ann;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS)
      |@Target({ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
      |public @interface Nullable {}
      |""".stripMargin

  /** The base's Java carries the annotation — which is the whole point: a marker published by a
    * third party is one BOTH modules' sources may use, and the base's port did not consume it. */
  private val base = Map(
    "ann/Nullable.java" -> annotation,
    "p/Base.java" -> """package p;
      |public class Base {
      |  public @ann.Nullable String find(String key) { return null; }
      |  public @ann.Nullable String cached;
      |}""".stripMargin,
  )

  private val dep = Map(
    "q/Mine.java" -> """package q;
      |public class Mine extends p.Base {
      |  public @ann.Nullable String own() { return null; }
      |}""".stripMargin,
  )

  private def model(): (Program, Path) =
    val root = Files.createTempDirectory("nullability-base-surface")
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

  /** exactly what `PortRun.partitionUnits` computes — by ORIGIN, realpathed on both sides (§5.4). */
  private def emittedUnits(p: Program, root: Path): Set[SymId] =
    val mine = RealPath.str(root.resolve("dep"))
    p.units.filter(u => RealPath.str(Path.of(u.origin.javaPath)).startsWith(mine)).map(_.symbol).toSet

  private def baseManifest(nullability: Option[NullabilityTransform] = scala.None) =
    PortManifest(name = "base", governs = Set("p"), surface = nullability.toList)

  private def dependent(mine: NullabilityTransform, b: PortManifest = baseManifest()) =
    b.extendedBy(PortManifest(name = "dep", governs = Set("q"), surface = List(mine)))

  /** the run, with the RunScope a dependent's `PortRun` builds — the emitted units from the origin
    * split, and the contributed subjects from the manifest's own fold. */
  private def run(p: Program, root: Path, m: PortManifest): Program =
    val scope  = RunScope.of(emittedUnits(p, root), m.contributedSubjects)
    val binder = new PolicyBinder(p, p.members, scope)
    Pipeline.runTraced(p, m.effectiveSurface, binder)._1

  private def infoOf(p: Program, fqn: String): TypeRepr =
    p.symbols.all.find(_.fullName == fqn).map(_.info).getOrElse(fail(s"no symbol $fqn"))

  private def isUnion(t: TypeRepr): Boolean = t match
    case TypeRepr.MethodType(_, r, _) => isUnion(r)
    case TypeRepr.OrType(_, _)        => true
    case _                            => false

  // -------------------------------------------------------------------------
  // the screen
  // -------------------------------------------------------------------------

  test("a DEPENDENT's own annotation does NOT retype its BASE's declarations — refused and counted") {
    val (p, root) = model()
    val mine      = new NullabilityTransform(Set("ann.Nullable"))
    val m         = dependent(mine)
    val after     = run(p, root, m)

    // this module's OWN declaration moves — the phase is doing its job
    assert(isUnion(infoOf(after, "q.Mine#own")), "the dependent's own annotated return did not move")
    // …and the base's do not. They keep exactly the type the base's own run gave them.
    assert(!isUnion(infoOf(after, "p.Base#find")), "a BASE declaration was retyped by a dependent's key")
    assert(!isUnion(infoOf(after, "p.Base#cached")), "a BASE field was retyped by a dependent's key")

    // ONE finding per KEY — the string an agent edits — naming both refused declarations.
    val findings = mine.policyReport.of(PolicyIssue.Unverifiable)
    assertEquals(findings.map(_.key), List("ann.Nullable"))
    val detail = findings.head.detail
    assert(clue(detail).contains("2 declaration(s)"), "the refusal did not count what it refused")
    assert(detail.contains("p.Base#find") && detail.contains("p.Base#cached"))
  }

  test("…and NO decision is recorded about a base declaration — D2 governs provenance too") {
    val (p, root) = model()
    val mine      = new NullabilityTransform(Set("ann.Nullable"))
    val scope     = RunScope.of(emittedUnits(p, root), dependent(mine).contributedSubjects)
    val (_, log)  = Pipeline.runTraced(p, dependent(mine).effectiveSurface, new PolicyBinder(p, p.members, scope))
    val subjects  = log.all.map(_.subjectFqn).toSet
    assert(!subjects.exists(_.startsWith("p.Base")), clue(subjects))
    assert(subjects.contains("q.Mine#own"))
  }

  test("an INHERITED key is NOT screened — the base applied it to the same declarations itself") {
    // The base declares the annotation; the dependent inherits the instance and adds nothing. Every
    // retype the dependent makes on a base declaration is one the base's own run made identically,
    // so refusing it would refuse the composition the merge contract exists to allow.
    val (p, root) = model()
    val theirs    = new NullabilityTransform(Set("ann.Nullable"))
    val m         = baseManifest(Some(theirs)).extendedBy(PortManifest(name = "dep", governs = Set("q")))
    val after     = run(p, root, m)

    assert(isUnion(infoOf(after, "p.Base#find")), "an inherited key was refused on the base's own surface")
    assert(isUnion(infoOf(after, "q.Mine#own")))
    assertEquals(theirs.policyReport.of(PolicyIssue.Unverifiable), Nil)
  }

  test("a MERGED key the base also declares is not screened; the key the DEPENDENT added is") {
    val (p, root) = model()
    val theirs    = new NullabilityTransform(Set("ann.Nullable"))
    val mine      = new NullabilityTransform(Set("ann.Nullable", "ann.Missing"))
    val m         = baseManifest(Some(theirs)).extendedBy(
      PortManifest(name = "dep", governs = Set("q"), surface = List(mine)))
    // the fold composed the two into ONE instance, and recorded `ann.Missing` as this module's
    assertEquals(m.surfaceFold.ownKeys.get("nullability"), Some(Set("ann.Missing")))
    assertEquals(m.contributedSubjects("nullability"), Set("ann.Missing"))
    val after = run(p, root, m)
    assert(isUnion(infoOf(after, "p.Base#find")), "the shared key was refused after a merge")
  }

  test("a BASE port screens nothing — `RunScope.whole` is the identity, by arithmetic") {
    val (p, _)  = model()
    val alone   = new NullabilityTransform(Set("ann.Nullable"))
    val after   = Pipeline.runTraced(p, List(alone), new PolicyBinder(p, p.members))._1
    assert(isUnion(infoOf(after, "p.Base#find")))
    assertEquals(alone.policyReport.of(PolicyIssue.Unverifiable), Nil)
  }

  // -------------------------------------------------------------------------
  // the corollary: an unclaimed base namespace disables the screen that guards it
  // -------------------------------------------------------------------------

  test("a base that declares policy and claims NO namespace is REPORTED, non-fatally") {
    val silent = PortManifest(name = "base", dropTypes = Set("p.Gone"))
    val m      = silent.extendedBy(PortManifest(name = "dep", dropTypes = Set("p.Gone")))
    val fs     = ManifestAgreement.check(Some(m), Nil, foreignRoots = true)
      .filter(_.kind == ManifestAgreement.Kind.BaseNamespaceUnclaimed)
    assertEquals(fs.map(_.base), List("base"))
    assert(!ManifestAgreement.Kind.BaseNamespaceUnclaimed.fatal)
  }

  test("…and a base that CLAIMS one, or declares no policy at all, reports nothing") {
    def unclaimed(m: PortManifest) =
      ManifestAgreement.check(Some(m), Nil, foreignRoots = true)
        .count(_.kind == ManifestAgreement.Kind.BaseNamespaceUnclaimed)
    val claimed = PortManifest(name = "base", governs = Set("p"), dropTypes = Set("p.Gone"))
    assertEquals(unclaimed(claimed.extendedBy(PortManifest(name = "dep", dropTypes = Set("p.Gone")))), 0)
    // the EMPTY manifest — the documented way to say "this resolution root is not a ported module"
    // (CLAUDE.md §1.5) — has no policy to protect and is a statement, not a finding.
    assertEquals(unclaimed(PortManifest(name = "notaport").extendedBy(PortManifest(name = "dep"))), 0)
  }
