package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{OmissionCheck, Pipeline}

/** The SLOTS of a synthesised primary — where each parameter's TYPE comes from, and which classes
  * may have one at all.
  *
  * The synthesis used to demand that one of the roots be NILARY, on the reasoning that a paramful
  * root could otherwise be promoted instead. It cannot, and the cost was silent: with several
  * paramful roots and no nilary one, `plan0` nominated NOTHING (`several.find(_.paramss.isEmpty)` is
  * `None`), the class came out `not-funnelled`, and EVERY root's `super(args)` was lowered to a bare
  * `this()` — the parent constructed with the wrong arguments, compiling perfectly. The condition
  * that actually makes the encoding work is that every root reaches ONE parent constructor, and it
  * has nothing to do with a nilary root; `ENGINE-LIMITS.md` C7's claim to the contrary is corrected.
  *
  * Both halves are here, because the positive alone would pass on a synthesis that fired everywhere:
  * roots reaching DIFFERENT parent constructors are the WALL, must NOT synthesise, and must stay
  * counted by `OmissionCheck` — one `extends` clause cannot make two different parent constructor
  * calls, and padding one to reach the other measured 0 -> 55 errors outside the JDK family.
  */
class SyntheticPrimarySlotsSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |class Widened {
      |  Object a; int b;
      |  Widened(Object a, int b) { this.a = a; this.b = b; }
      |}
      |/** TWO PARAMFUL ROOTS, no nilary one, both reaching `Widened(Object,int)`. The slot types are
      |  * the PARENT's formals (`Object`), never the argument types at the call (`String`, `Integer`)
      |  * — an argument is an expression whose type may be narrower than the formal, and a primary
      |  * built from one call's arguments cannot take the other's. */
      |class Narrowing extends Widened {
      |  Narrowing(String s)  { super(s, s.length()); }
      |  Narrowing(Integer i) { super(i, 1); }
      |}
      |class Overloaded {
      |  int n;
      |  Overloaded(int n) { this.n = n; }
      |  Overloaded(int n, boolean b) { this.n = b ? n : -n; }
      |}
      |/** THE WALL: two roots reaching two DIFFERENT parent constructors. One `extends` clause cannot
      |  * make both calls, so nothing is synthesised and the loss stays counted. */
      |class Wall extends Overloaded {
      |  Wall(int n)              { super(n); }
      |  Wall(int n, boolean b)   { super(n, b); }
      |}
      |""".stripMargin

  private val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private val out     = new TirEmitter(program).emit
  private val dropped = OmissionCheck.droppedSuperArgs(program)

  test("several PARAMFUL roots reaching ONE parent constructor synthesise — no nilary root needed") {
    assert(clue(out).contains(
      "class Narrowing protected (sup$0: java.lang.Object, sup$1: scala.Int) extends demo.Widened(sup$0, sup$1)"))
  }

  test("the slot types are the PARENT's FORMALS, not the argument types at the call site") {
    // `super(s, …)` passes a `String` and `super(i, 1)` an `Integer`; the slot is `Object`, which is
    // what `Widened` declares. Built from either call's argument types the primary could not take
    // the other's argument at all.
    assert(!clue(out).contains("class Narrowing protected (sup$0: java.lang.String"))
    assert(!out.contains("class Narrowing protected (sup$0: java.lang.Integer"))
  }

  test("every root's super arguments survive, positionally, and none is reported") {
    assert(clue(out).contains("def this(s: java.lang.String) = {"))
    assert(out.contains("this(s, s.length())"))
    assert(out.contains("this(i, 1)"))
    assertEquals(dropped.filter(_.owner == "demo.Narrowing"), Nil)
  }

  test("NEGATIVE — roots reaching DIFFERENT parent constructors do not synthesise") {
    assert(!clue(out).contains("class Wall protected ("))
  }

  test("NEGATIVE — and the wall invents no argument: no `extends` clause carries one") {
    // The fallback for a wall is the one the funnel already had — `this()` plus the parent
    // constructor's own statements REPLAYED where that is provably equivalent, or the arguments
    // counted where it is not. What must NOT happen is a single `extends Overloaded(…)` built by
    // padding one call to reach the other's overload: that is the guess measured at 0 -> 55 errors
    // outside the JDK-throwable family, and it compiles.
    assert(clue(out).contains("class Wall extends demo.Overloaded {"))
    assert(!out.contains("extends demo.Overloaded("))
    // every root here IS accounted for — by replay, not by synthesis — so nothing is reported, and
    // that silence is the correct answer rather than a missing one
    assertEquals(dropped.filter(_.owner == "demo.Wall"), Nil)
  }

  // ---- FIELD SLOTS: the leading `this.f = e` run, hoisted into the primary's parameter list ----

  private val fieldSrc =
    """package fields;
      |class Widened { Widened(Object a, int b) {} }
      |/** every gate at once: `n` and `tag` hoist and bind as `val`; `spare` hoists and stays a
      |  * `var` because a method writes it; the second root supplies the java DEFAULT for `spare`. */
      |class Clean extends Widened {
      |  private final int n;
      |  final String tag;
      |  int spare;
      |  Clean(String s, int k) {
      |    super(s, k);
      |    // the count is the caller's own
      |    this.n = k * 2;
      |    this.tag = s;
      |    this.spare = 1;
      |    System.out.println(s);
      |  }
      |  Clean(int k) { super(null, k); this.n = k; this.tag = "x"; }
      |  void bump() { this.spare = 9; }
      |}
      |/** ORDER-SAFETY: `hashed`'s value is a METHOD CALL, which a delegation argument list would
      |  * evaluate before `super(...)` and before the instance initialisers — java evaluated it after
      |  * both. Not a slot; the assignment stays where java put it. */
      |class Ordered extends Widened {
      |  private final int hashed;
      |  Ordered(String s) { super(s, 1); this.hashed = s.hashCode(); }
      |  Ordered(Integer i) { super(i, 2); this.hashed = 0; }
      |}
      |/** the class body must not DEPEND on a hoisted field: a slot moves the write to the field's
      |  * own declaration, which is where java ran the field's initialiser, so `b = a * 2` would read
      |  * the constructor's `a` where java read `0`. */
      |class Reader extends Widened {
      |  int a;
      |  int b = a * 2;
      |  Reader(int k) { super(null, k); this.a = k; }
      |  Reader(String s) { super(s, 0); this.a = 1; }
      |}
      |""".stripMargin

  private val fields = Pipeline.run(SpoonTir.fromSource(fieldSrc), Nil)
  private val fout   = new TirEmitter(fields).emit

  test("the leading `this.f = e` run becomes SLOTS, after the super slots and in declaration order") {
    assert(clue(fout).contains(
      "class Clean protected (sup$0: java.lang.Object, sup$1: scala.Int, " +
      "f$n: scala.Int, f$tag: java.lang.String, f$spare: scala.Int) extends fields.Widened(sup$0, sup$1)"))
    // each root delegates with its own values, and the consumed statements are gone from its body
    assert(fout.contains("this(s, k, k * 2, s, 1)"))
    assert(!fout.contains("this.n = k * 2"))
  }

  test("A1 — a slot binds as `val` exactly when NOTHING ELSE in the program writes the field") {
    // `n` and `tag`: written once per construction path, from a parameter, in the primary
    assert(clue(fout).contains("private final val n: scala.Int = f$n"))
    assert(fout.contains("final val tag: java.lang.String = f$tag"))
    // `spare`: a perfectly good SLOT whose field a method also writes. Reading slot-eligibility and
    // `val`-eligibility as ONE gate would have demoted it out of the slot set for no semantic
    // reason — `DESIGN.md` §8.2's correction, and this is the case that shows the difference.
    assert(fout.contains("var spare: scala.Int = f$spare"))
  }

  test("a root that does not assign a slot supplies the java DEFAULT at that slot") {
    assert(clue(fout).contains("this(null, k, k, \"x\", 0)"))
  }

  test("ORDER-SAFETY — a value that is not order-blind is NOT a slot, and nothing behind it is") {
    // `Ordered` hoists nothing: `s.hashCode()` is a method call, so its assignment stays a
    // post-delegation statement of its own secondary and the field stays a `var`.
    assert(!clue(fout).contains("f$hashed"))
    assert(fout.contains("this.hashed = s.hashCode()"))
    // …and the field keeps the placeholder every non-slot field keeps: scala's own word for the JVM
    // default at this type, which is what java put there (A1's residue).
    assert(fout.contains("private var hashed: scala.Int = scala.compiletime.uninitialized"))
  }

  test("NEGATIVE — a field a SIBLING INITIALISER reads is refused, because the slot moves the write") {
    // `int b = a * 2;` runs where java ran it — reading `a` BEFORE any constructor wrote it. Hoisting
    // `a` to its declaration would make `b` read the constructor's value instead, silently, with a
    // green compile and no count moved.
    assert(!clue(fout).contains("f$a"))
    assert(fout.contains("var b: scala.Int = this.a * 2"))
  }

  test("A1 counts the EMISSION's own writes too — a REPLAY into a subclass forces `var`") {
    // A replay lifts a PARENT constructor's statements into a subclass, so a parent field the funnel
    // hoisted into a slot and saw written exactly once in the java is written again, once per
    // replaying subclass, in code no source scan can see. Ashley's `EntitySystemMock.updates` is
    // java-`private` and assigned by one constructor — `val`-eligible by every test over the java —
    // and its two subclasses replay `super(updates)`. Measured 0 -> 4 `E052 Reassignment to val`
    // with the decision taken before the replays were known.
    val replayed =
      """package repl;
        |class Mock {
        |  private java.util.List<String> log;
        |  Mock() { }
        |  Mock(java.util.List<String> log) { this.log = log; }
        |}
        |/** roots reaching DIFFERENT parent constructors, so this class is a wall and its
        |  * `super(log)` is expressed by REPLAY — the parent's `this.log = log` lifted in here. */
        |class MockA extends Mock {
        |  MockA(java.util.List<String> log) { super(log); }
        |  MockA(int n)                      { super(); }
        |}
        |""".stripMargin
    val o3 = new TirEmitter(Pipeline.run(SpoonTir.fromSource(replayed), Nil)).emit
    assert(clue(o3).contains("f$log"))
    // the slot is real; what it must NOT be is a `val`, because the two replays assign it
    assert(!o3.contains("val log:"))
    assert(o3.contains("var log: java.util.List[java.lang.String] = f$log"))
  }

  test("§4.58 — a CONSUMED assignment's comment rides the delegation that replaced it") {
    // Every field slot has N roots contributing by construction, so the comment attaches to THIS
    // secondary's delegation. The funnel is the one place a statement disappears without a diff
    // showing where it went.
    val idx = fout.indexOf("// the count is the caller's own")
    assert(idx > 0, clue(fout))
    assert(fout.indexOf("this(s, k, k * 2, s, 1)", idx) > idx)
  }

  test("a COMMENT above a consumed `super(args)` rides the delegation that replaces it") {
    // §4.58: the call is consumed into a `this(...)`, and what somebody wrote ABOUT it is not. The
    // funnel is the one place a statement disappears without a diff showing where it went, so the
    // carriage is pinned rather than left to whichever harvest runs last.
    val commented =
      """package demo3;
        |class P { String label; P(String label) { this.label = label; } }
        |class Q extends P {
        |  Q(int k) {
        |    // the base wants a name, not an index
        |    super("n" + k);
        |  }
        |  Q(boolean b) { super(b ? "t" : "f"); }
        |}
        |""".stripMargin
    val p2  = Pipeline.run(SpoonTir.fromSource(commented), Nil)
    val o2  = new TirEmitter(p2).emit
    assert(clue(o2).contains("class Q protected (sup$0: java.lang.String) extends demo3.P(sup$0)"))
    assert(o2.contains("// the base wants a name, not an index"))
    // …and it sits above the delegation, not orphaned at the end of the constructor
    val idx = o2.indexOf("// the base wants a name, not an index")
    assert(o2.indexOf("this(", idx) > idx)
  }
