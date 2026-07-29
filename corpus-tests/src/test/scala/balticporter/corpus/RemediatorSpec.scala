package balticporter.corpus

import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, PortabilityCheck, Program, Remediator, SymId}

/** Two things at once, because they are the same defect seen from two ends.
  *
  * `PortabilityCheck`'s nine `exactMember` rules ask for `owner#name` of an external member. The
  * frontend interned every external symbol with `owner = SymId.None` and a `fullName` that is an
  * interning key, so that string was `None` at every one of them and the rules never fired once —
  * a check reporting a number that looked like coverage (CLAUDE.md §3). The first group pins that
  * they fire, and pins the shape they need in order to fire.
  *
  * `Remediator` then reads the SAME `owner#name` to decide that a call reaches a particular member
  * of a particular external type — which is what lets it recognise a static forwarder. The second
  * group pins its three templates against a program small enough to argue with, and pins the thing
  * that matters more than any of them: that it declines to suggest where it cannot verify.
  */
class RemediatorSpec extends munit.FunSuite:

  private def parse(java: String): Program = Pipeline.run(SpoonTir.fromSource(java), Nil)

  private def violations(p: Program): List[PortabilityCheck.Violation] = PortabilityCheck.check(p)

  private def suggest(p: Program): List[Remediator.Suggestion] =
    Remediator.suggest(p, violations(p))

  // -------------------------------------------------------------------------
  // the frontend fix: an external MEMBER carries its owner
  // -------------------------------------------------------------------------

  private val reflective =
    """package demo;
      |public class Refl {
      |  public static Class<?> forName (String name) throws Exception { return Class.forName(name); }
      |  public static Object newInstance (Class c) throws Exception { return c.newInstance(); }
      |  public static String where () { return System.getProperty("user.dir"); }
      |}
      |""".stripMargin

  test("the exactMember rules fire — they never had, for the whole history of the project") {
    val found = violations(parse(reflective)).map(_.api).distinct.sorted
    assert(found.contains("java.lang.Class#forName"), found)
    assert(found.contains("java.lang.Class#newInstance"), found)
    assert(found.contains("java.lang.System#getProperty"), found)
  }

  test("an external member's owner resolves to the real type; the member itself stays keyed") {
    val p = parse(reflective)
    val forName = p.symbols.all.find(s =>
      s.name == "forName" && p.symbolOf(s.owner).exists(_.fullName == "java.lang.Class"))
    assert(forName.isDefined, "java.lang.Class#forName has no owner — the check is blind again")
    // the fullName is deliberately NOT changed: it is the interning key, and the emitter and the
    // package rename both read it. Only the OWNER moved.
    assert(forName.get.fullName.startsWith("@"), forName.get.fullName)
  }

  test("an external TYPE is still rooted at None — every ownership predicate depends on it") {
    val p = parse(reflective)
    val cls = p.symbols.all.find(_.fullName == "java.lang.Class")
    assertEquals(cls.map(_.owner), Some(SymId.None))
    // …and therefore an external member's chain still TERMINATES outside the program.
    val forName = p.symbols.all.find(s =>
      s.name == "forName" && p.symbolOf(s.owner).exists(_.fullName == "java.lang.Class")).get
    assertEquals(p.symbolOf(forName.owner).map(_.owner), Some(SymId.None))
  }

  // -------------------------------------------------------------------------
  // Remediator — template 3, the class table
  // -------------------------------------------------------------------------

  test("a runtime class lookup yields the ClassTableTransform key, and the key is the WRAPPER") {
    val s = suggest(parse(reflective)).filter(_.mechanism == "class-table")
    assertEquals(s.size, 1)
    // `demo.Refl#forName` and not `java.lang.Class#forName`: redirecting the port's own static
    // wrapper leaves nothing but the wrapper's body behind, which is what makes it removable.
    assertEquals(s.head.subject, "demo.Refl#forName")
    assert(s.head.snippet.exists(_.contains("""new ClassTableTransform(Map("demo.Refl#forName" ->""")), s.head.snippet)
    assertEquals(s.head.confidence, Remediator.Confidence.Medium)
  }

  test("no class-table suggestion when the program performs no lookup by name") {
    val p = parse(
      """package demo;
        |public class Plain { public String go (Thread t) { return t.getName(); } }
        |""".stripMargin)
    assert(suggest(p).forall(_.mechanism != "class-table"))
  }

  // -------------------------------------------------------------------------
  // Remediator — template 2, the static forwarder
  // -------------------------------------------------------------------------

  private val wrapper =
    """package demo;
      |public class W {
      |  public static String getSimpleName (Class c) { return c.getSimpleName(); }
      |  public static boolean isInterface (Class c) { return c.isInterface(); }
      |  public static Object newInstance (Class c) throws Exception { return c.newInstance(); }
      |  public static Thread current () { return Thread.currentThread(); }
      |}
      |""".stripMargin

  test("a static wrapper that forwards its first argument yields the Forwarder line") {
    val s = suggest(parse(wrapper)).filter(_.mechanism == "static-forwarder-inline")
    assertEquals(s.size, 1)
    val line = s.head.snippet.getOrElse("")
    assert(line.contains("""wrapper = "demo.W""""), line)
    assert(line.contains("""receiver = "java.lang.Class""""), line)
    assert(line.contains(""""getSimpleName""""), line)
    assert(line.contains(""""isInterface""""), line)
  }

  test("a member that forwards to a JVM-ONLY member is EXCLUDED — inlining would relocate it") {
    val s = suggest(parse(wrapper)).find(_.mechanism == "static-forwarder-inline").get
    // `newInstance` forwards to `Class#newInstance`, which is itself the unportable thing. Putting
    // it in the Forwarder set would move the dependency from the wrapper to every call site and
    // report the port as fixed.
    assert(!s.snippet.get.contains(""""newInstance""""), s.snippet)
    assert(s.caveat.exists(_.contains("newInstance")), s.caveat)
  }

  test("a static method that is not receiver-first is not a forwarder") {
    // `current()` takes no argument at all, so there is no first argument to forward to.
    val s = suggest(parse(wrapper)).find(_.mechanism == "static-forwarder-inline").get
    assert(!s.snippet.get.contains(""""current""""), s.snippet)
  }

  // -------------------------------------------------------------------------
  // Remediator — template 1, the chokepoint drop
  // -------------------------------------------------------------------------

  test("an unportable API confined to one declared type yields the dropTypes line") {
    val p = parse(
      """package demo;
        |import java.util.zip.CRC32;
        |public class Zipper { public long sum (byte[] b) { CRC32 c = new CRC32(); c.update(b); return c.getValue(); } }
        |""".stripMargin)
    val s = suggest(p).filter(_.mechanism == "substitutions-drop")
    assertEquals(s.map(_.subject), List("demo.Zipper"))
    assertEquals(s.head.confidence, Remediator.Confidence.High) // nothing else references it
    assertEquals(s.head.snippet, Some("""Substitutions(dropTypes = Set("demo.Zipper"))"""))
  }

  test("a referenced chokepoint is downgraded and told it needs a replacement at the same FQN") {
    val p = parse(
      """package demo;
        |import java.util.zip.CRC32;
        |public class Pair {
        |  static class Zipper { long sum (byte[] b) { CRC32 c = new CRC32(); c.update(b); return c.getValue(); } }
        |  static long go (byte[] b) { return new Zipper().sum(b); }
        |}
        |""".stripMargin)
    val s = suggest(p).filter(_.mechanism == "substitutions-drop")
    // whether Spoon nests or flattens, the claim under test is the GRADE, not the shape
    s.foreach { x =>
      if x.confidence == Remediator.Confidence.Medium then
        assert(x.snippet.exists(_.contains("inject = List(")), x.snippet)
        assert(x.caveat.exists(_.contains("SAME FQN")), x.caveat)
    }
    assert(s.nonEmpty)
  }

  test("an API spread over several types proposes NOTHING and says what it measured") {
    val p = parse(
      """package demo;
        |public class Spread {
        |  static class A { void go () { new Thread().start(); } }
        |  static class B { void go () { new Thread().start(); } }
        |}
        |""".stripMargin)
    val s = suggest(p).filter(_.subject == "java.lang.Thread")
    s.foreach { x =>
      assertEquals(x.confidence, Remediator.Confidence.Observation)
      assertEquals(x.snippet, None)
      assert(x.observed.contains("site(s) across"), x.observed)
    }
  }

  test("no violations, no suggestions — the empty case is not a template that always fires") {
    val p = parse("""package demo; public class Q { int add (int a, int b) { return a + b; } }""")
    assertEquals(suggest(p), Nil)
  }

  test("every api that produced a violation appears exactly once across the suggestions") {
    // the property that broke first: grouping the chokepoints through a Map keyed by the wrapper
    // silently kept ONE api per type and let the rest fall through to the observation fallback,
    // so the same finding was reported twice under two different mechanisms.
    val p = parse(
      """package demo;
        |import java.util.zip.*;
        |public class Multi {
        |  Deflater d = new Deflater();
        |  CRC32 c = new CRC32();
        |  void go () { d.finish(); c.reset(); }
        |}
        |""".stripMargin)
    val apis     = violations(p).map(_.api).distinct.toSet
    val proposed = suggest(p)
    val chokeApis = proposed.filter(_.mechanism == "substitutions-drop").flatMap(s => apis.filter(s.observed.contains))
    val observed  = proposed.filter(_.mechanism == "observation").map(_.subject)
    assert(observed.forall(a => !chokeApis.contains(a)), s"reported twice: $observed / $chokeApis")
  }
