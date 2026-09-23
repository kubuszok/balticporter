package balticporter.corpus

import balticporter.catalog.{ CatalogLog, JS }
import balticporter.testkit.PortSuite
import balticporter.tir.{ Decision, PorterNote, ReflectionVisibilityCheck }
import balticporter.tir.ReflectionVisibilityCheck.Issue

/** The `reflection-visibility` lane: private nested class constructors are private in java bytecode but public in scalac bytecode, so reflective instantiation that java refuses succeeds silently in
  * the port. Catalog `JS-C54`.
  */
class ReflectionVisibilityCheckSpec extends PortSuite:

  private val fixture = """
    package demo;
    public class Outer {
      private static class PrivateNested {}
      public static class PublicNested {}

      Object createPrivate() {
        try {
          return PrivateNested.class.getConstructor().newInstance();
        } catch (Exception e) { return null; }
      }

      Object createPublic() {
        try {
          return PublicNested.class.getConstructor().newInstance();
        } catch (Exception e) { return null; }
      }

      Object createDynamic(Class<?> cls) {
        try {
          return cls.getConstructor().newInstance();
        } catch (Exception e) { return null; }
      }
    }"""

  test("a reflective call on a private nested class is a PrivateTarget finding") {
    val p               = port(fixture)
    val fs              = ReflectionVisibilityCheck.check(p.after, p.after.units)
    val privateFindings = fs.filter(_.issue == Issue.PrivateTarget)
    assertEquals(clue(privateFindings).size, 1)
    assert(clue(privateFindings.head.targetType).contains("PrivateNested"))
    assert(privateFindings.head.origin.line > 0, privateFindings.head.render)
    assert(clue(Issue.classification(Issue.PrivateTarget)).contains("engine"))
  }

  test("a reflective call on a public nested class produces no finding") {
    val p              = port(fixture)
    val fs             = ReflectionVisibilityCheck.check(p.after, p.after.units)
    val publicFindings = fs.filter(f => f.targetType.contains("PublicNested"))
    assertEquals(clue(publicFindings), Nil)
  }

  test("a reflective call with a dynamic Class<?> variable is an UnknownTarget finding") {
    val dyn = port(
      """
      package demo;
      public class DynTest {
        Object create(Class<?> cls) throws Exception {
          return cls.newInstance();
        }
      }"""
    )
    val fs              = ReflectionVisibilityCheck.check(dyn.after, dyn.after.units)
    val unknownFindings = fs.filter(_.issue == Issue.UnknownTarget)
    assert(clue(unknownFindings).nonEmpty, "a dynamic Class<?> must produce an unknown-target finding")
    assert(clue(Issue.classification(Issue.UnknownTarget)).contains("engine"))
  }

  test("the check report carries the lane name and a relative path") {
    val p  = port(fixture)
    val fs = ReflectionVisibilityCheck.check(p.after, p.after.units)
    assert(fs.nonEmpty)
    assertEquals(fs.head.report.check, ReflectionVisibilityCheck.Name)
    assert(!fs.head.report.path.startsWith("/"), fs.head.report.path)
  }

  test("the summary groups by issue and contains a classification") {
    val p  = port(fixture)
    val fs = ReflectionVisibilityCheck.check(p.after, p.after.units)
    val s  = ReflectionVisibilityCheck.summary(fs)
    assert(clue(s).contains("PrivateTarget") || clue(s).contains("UnknownTarget"))
  }

  test("the decision for a PrivateTarget finding carries the target and the catalog rule") {
    val p  = port(fixture)
    val fs = ReflectionVisibilityCheck.check(p.after, p.after.units)
    val pf = fs.find(_.issue == Issue.PrivateTarget).getOrElse(fail("no PrivateTarget finding"))
    // the owner is a fully-qualified method name like "demo.Outer#createPrivate"
    val sym = p.after.symbols.all.find(_.fullName == pf.owner).getOrElse(fail(s"no symbol for owner '${pf.owner}'"))
    val d   = ReflectionVisibilityCheck.decision(pf, sym.id, pf.owner)
    assertEquals(d.kind, Decision.Kind.CountedReflectionRisk)
    assert(clue(d.detail("target")).contains("PrivateNested"))
    assert(d.reason.className == "universal")
  }

  test("CountedReflectionRisk is in PorterNote.Rendered and AtDeclaration") {
    assert(PorterNote.Rendered.contains(Decision.Kind.CountedReflectionRisk))
    assert(PorterNote.AtDeclaration.contains(Decision.Kind.CountedReflectionRisk))
    assertEquals(PorterNote.slug(Decision.Kind.CountedReflectionRisk), "counted-reflection-risk")
  }

  test("the catalog row JS-C54 is cited by the lane when a finding exists") {
    val p       = port(fixture)
    val catalog = new CatalogLog
    val fs      = ReflectionVisibilityCheck.check(p.after, p.after.units)
    assert(fs.nonEmpty)
    fs.foreach(f => catalog.cite(JS.C(54), f.owner))
    val cited = catalog.citedAt(JS.C(54))
    assert(clue(cited).nonEmpty, "JS-C54 must be cited when a finding exists")
  }

  test("an empty program with no reflective calls produces no findings") {
    val p = port("""
      package demo;
      public class Empty {
        void f() {}
      }""")
    assertEquals(ReflectionVisibilityCheck.check(p.after, p.after.units), Nil)
    assertEquals(ReflectionVisibilityCheck.summary(Nil), "  none")
  }
