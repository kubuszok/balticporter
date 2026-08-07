package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.{ClassInitTriggerCheck, Surface}
import balticporter.tir.ClassInitTriggerCheck.Issue

/** K22's WATCHDOG, driven in both directions — the check that had never had a spec.
  *
  * `class-init-trigger` reads 0 on every port in the corpus, which is the number a check reports
  * both when the repair works and when the census cannot see the defect. The only way to tell those
  * apart is to run it with an EMPTY forced set, which is the un-repaired engine on the same trees,
  * and that is what every cell below does first. Its `Unforced` lane had never fired anywhere:
  * measured on fifteen ports it read 0 from the first run, so nothing in the corpus proved the lane
  * could produce a row at all.
  */
class ClassInitTriggerSpec extends PortSuite:

  /** a `static { }` block on a class with instance state — no all-static collapse, so the block
    * lands in a companion and `new C` touches nothing. */
  private val registering = """
    package demo;
    public class C {
      public static int hits;
      public int n;
      static { hits = 1; }
      public C(int n) { this.n = n; }
    }"""

  test("un-repaired, the check REPORTS the block — owner, form, origin and a §1 classification") {
    val p  = port(registering)
    p.out
    val fs = ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, p.emitter.emittedShapes.types.get)
    val un = fs.filter(_.issue == Issue.Unforced)
    assertEquals(clue(un).size, 1)
    assertEquals(un.head.owner, "demo.C")
    assertEquals(un.head.declarer, "demo.C")
    assertEquals(un.head.form, "class")
    assert(un.head.origin.line > 0, un.head.render)
    assert(clue(Issue.classification(Issue.Unforced)).contains("§1(a)"))
    assert(clue(ClassInitTriggerCheck.summary(un)).contains("Unforced"))
    assertEquals(un.head.report.check, ClassInitTriggerCheck.Name)
    assert(!un.head.report.path.startsWith("/"), un.head.report.path)
  }

  test("with what the emitter actually forced, the same program reads 0") {
    val p = port(registering)
    p.out
    assertEquals(clue(p.emitter.forcedClassInits), Set(p.idAfter("demo.C").get -> ClassInitTriggerCheck.Instantiation))
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, p.emitter.forcedClassInits,
                                  p.emitter.emittedShapes.types.get),
      Nil)
  }

  test("…and the forced line is the FULLY QUALIFIED `val _ =`, at the head of the class body") {
    val p = port(registering)
    assertEmits(p, "val _ = demo.C")
    val force = p.out.indexOf("val _ = demo.C")
    val field = p.out.indexOf("var n:")
    assert(force > 0 && field > 0, p.out)
    assert(force < field, s"the trigger must precede every field initialiser\n${p.out}")
  }

  test("a NESTED bearer names its companion path with `.`, never the JVM's `$`") {
    // `Symbol.fullName` separates a nested type with `$` (§4.56) and no Scala path may spell one —
    // `demo.Outer$Inner` is a single identifier to the parser and resolves to nothing.
    val p = port("""
      package demo;
      public class Outer {
        public static class Inner {
          public static int hits;
          public int n;
          static { hits = 1; }
        }
      }""")
    assertEmits(p, "val _ = demo.Outer.Inner")
    assertNotEmits(p, "val _ = demo.Outer$Inner")
  }

  test("an all-static class COLLAPSES to an `object` — every route in touches it, so 0 un-repaired") {
    val p = port("""
      package demo;
      public class Util {
        public static int hits;
        static { hits = 1; }
        public static int get() { return hits; }
      }""")
    p.out
    val shapes = p.emitter.emittedShapes.types
    assertEquals(clue(shapes.get("demo.Util")).map(_.form), Some("object"))
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, shapes.get),
      Nil)
  }

  test("an ENUM is self-initialising too — its constants ARE companion members") {
    val p = port("""
      package demo;
      public enum E {
        A, B;
        public static int hits;
        static { hits = 1; }
      }""")
    p.out
    val shapes = p.emitter.emittedShapes.types
    assertEquals(clue(shapes.get("demo.E")).map(_.form), Some("enum-class"))
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, shapes.get),
      Nil)
  }

  test("a TRAIT bearer is REPORTED, not silently exempted — a trait body statement runs more often") {
    // A java interface may not declare a static initialiser (JLS 9.1.1), so this shape cannot be
    // written in java at all — which is exactly why the check must be driven at its `shapeOf`
    // parameter. Emitted as a `trait`, the class has no constructor to carry the force and a body
    // statement would run at EVERY implementor's initialisation, which is more than java does. The
    // emitter therefore emits nothing and the check is the thing that says so.
    val p = port(registering)
    p.out
    val fs = ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty,
      _ => Some(Surface.TypeShape(form = "trait", companion = true)))
    assertEquals(clue(fs).map(_.issue).distinct, List(Issue.Unforced))
    assertEquals(fs.head.form, "trait")
  }

  test("the SUBCLASS trigger: a companion whose ancestor bears the block, and what closes it") {
    val p = portAll(List(
      "Base.java" -> """
        package demo;
        public class Base {
          public static int hits;
          public int n;
          static { hits = 1; }
        }""",
      "Sub.java" -> """
        package demo;
        public class Sub extends Base {
          public static int own = 2;
          public int m;
        }"""))
    p.out
    val shapes = p.emitter.emittedShapes.types
    val un = ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, shapes.get)
    val sub = un.filter(_.issue == Issue.SubclassInitUnforced)
    assertEquals(clue(sub).size, 1)
    assertEquals(sub.head.owner, "demo.Sub")
    assertEquals(sub.head.declarer, "demo.Base")
    assert(clue(Issue.classification(Issue.SubclassInitUnforced)).contains("§1(a)"))
    // …and the emitter closes it, from `Sub`'s own companion, naming the ANCESTOR.
    assertEmits(p, "val _ = demo.Base")
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, p.emitter.forcedClassInits, shapes.get),
      Nil)
  }
