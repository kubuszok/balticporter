package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.tir.ClassInitTriggerCheck
import balticporter.tir.ClassInitTriggerCheck.Issue

/** K22's WATCHDOG, driven in both directions — the check that had never had a spec. */
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
    // `enum` and not `enum-class`: this enum is expressible as a scala 3 `enum extends
    // java.lang.Enum[E]` (`ENGINE-LIMITS.md` T21), and BOTH forms are in `notInstantiable` for the
    // one reason this test is about — the constants are companion members either way.
    assertEquals(clue(shapes.get("demo.E")).map(_.form), Some("enum"))
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, shapes.get),
      Nil)
  }

  test("an INTERFACE bearer is not a finding — nothing can `new` it, so java has no trigger to lose") {
    // JLS 9.1.1 keeps a `static { }` block out of an interface and says nothing about a FIELD, so an
    // interface field with a non-constant initialiser IS step-9 content and the census correctly
    // sees it. What does not exist is the DEFECT: java's only route into an interface's
    // initialisation is a use of a non-constant static it declares, which in scala is an access to
    // the companion — already exact. Read as a defect these were 4 findings on libGDX core.
    val p = port("""
      package demo;
      public interface I {
        java.util.List<String> ANY = new java.util.ArrayList<String>();
        int get();
      }""")
    p.out
    val shapes = p.emitter.emittedShapes.types
    assertEquals(clue(shapes.get("demo.I")).map(_.form), Some("trait"))
    assertEquals(ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, shapes.get), Nil)
  }

  test("…and an implementor's companion does NOT force it — item 7 stops at a default-less interface") {
    // "Initialising a class initialises its superclasses" is the half everybody quotes; the JLS
    // sentence continues "…as well as any superinterfaces that declare any default methods". An
    // edge to one that declares none is an edge java does not have, and a force across it is a
    // trigger java never had — which is the one thing this repair may not add.
    val p = portAll(List(
      "I.java" -> """
        package demo;
        public interface I {
          java.util.List<String> ANY = new java.util.ArrayList<String>();
          int get();
        }""",
      "C.java" -> """
        package demo;
        public class C implements I {
          public static int own = 2;
          public int get() { return own; }
        }"""))
    assertNotEmits(p, "val _ = demo.I")
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, p.emitter.forcedClassInits,
                                  p.emitter.emittedShapes.types.get),
      Nil)
  }

  // ---- the REFUSAL: java tolerates a cyclic class initialiser and a scala companion does not -----

  test("a MUTUAL initialisation cycle is REFUSED and counted, not forced") {
    // §4.4's `Vector3`/`Matrix4` shape, minimised. Java runs both initialisers with a cycle in the
    // graph and survives — JLS 12.4.2 step 3 lets a thread re-enter a class it is already
    // initialising — and a scala companion whose `MODULE$` has not been assigned yet does not.
    val p = portAll(List(
      "A.java" -> """
        package demo;
        public class A {
          static final B shared = new B();
          public int n;
        }""",
      "B.java" -> """
        package demo;
        public class B {
          static final A back = new A();
          public int m;
        }"""))
    assertNotEmits(p, "val _ = demo.A")
    assertNotEmits(p, "val _ = demo.B")
    val fs = ClassInitTriggerCheck.check(p.after, p.after.units, p.emitter.forcedClassInits,
                                         p.emitter.emittedShapes.types.get)
    assertEquals(clue(fs).map(_.issue).distinct, List(Issue.ReentrantRefused))
    assertEquals(fs.map(f => f.owner -> f.declarer).toSet, Set("demo.A" -> "demo.B", "demo.B" -> "demo.A"))
    assert(clue(Issue.classification(Issue.ReentrantRefused)).contains("§1(a)"))
    assert(clue(ClassInitTriggerCheck.summary(fs)).contains("ReentrantRefused"))
  }

  test("a SELF-edge is not re-entrance — dotty assigns `MODULE$` first, so java's own shape survives") {
    // The distinction that decides whether this is a refusal or the repair switched off: an
    // initialiser touching its OWN type is what every initialiser does, and `static { hits = 1; }`
    // is exactly that. Counted as a cycle it declined the trigger for every plain `static { }`
    // bearer in the corpus.
    val p = port("""
      package demo;
      public class S {
        public static int hits;
        static final S self = new S();
        public int n;
        static { hits = 1; }
      }""")
    assertEmits(p, "val _ = demo.S")
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, p.emitter.forcedClassInits,
                                  p.emitter.emittedShapes.types.get),
      Nil)
  }

  // ---- the CENSUS is JLS 12.4.2 STEP 9, not a node kind -----------------------------------------

  test("a REGISTRATION WRITTEN AS A FIELD is class-initialiser content and gets the trigger") {
    // The same defect as a `static { }` block and the same registration, written the way a library
    // that wants the value of the call actually writes it. Java's `<clinit>` runs static field
    // initialisers and `static { }` blocks as ONE sequence (JLS 12.4.2 step 9) and `new R`
    // initialises the class whichever of the two is there; a census keyed on the BLOCK sees only
    // one of them.
    val p = portAll(List(
      "Registry.java" -> """
        package demo;
        public class Registry {
          public static boolean register(String s) { return true; }
        }""",
      "R.java" -> """
        package demo;
        public class R {
          private static final boolean REGISTERED = Registry.register("r");
          public int n;
        }"""))
    p.out
    assertEmits(p, "val _ = demo.R")
    assertEquals(clue(p.emitter.forcedClassInits),
                 Set(p.idAfter("demo.R").get -> ClassInitTriggerCheck.Instantiation))
    val fs = ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, p.emitter.emittedShapes.types.get)
    assertEquals(clue(fs).filter(_.issue == Issue.Unforced).map(_.owner), List("demo.R"))
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, p.emitter.forcedClassInits,
                                  p.emitter.emittedShapes.types.get),
      Nil)
  }

  test("a CONSTANT-ONLY class gets nothing — java inlines the read, and so does this port (JS-C08)") {
    // The half that keeps `ENGINE-LIMITS.md` K22 safe against §4.4's `Vector3`/`Matrix4` cycle: a
    // java constant variable is inlined by javac and emitted `inline val` here, so reading one
    // triggers no initialisation in either language and the class owes no force. Widening the
    // census to step 9 must not reach these, or the repair adds a trigger java never had.
    val p = port("""
      package demo;
      public class K {
        public static final int A = 1;
        public static final String B = "b";
        public static int uninitialised;
        public int n;
      }""")
    p.out
    assertNotEmits(p, "val _ = demo.K")
    assertEquals(clue(p.emitter.forcedClassInits), Set.empty)
    assertEquals(
      ClassInitTriggerCheck.check(p.after, p.after.units, Set.empty, p.emitter.emittedShapes.types.get),
      Nil)
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
