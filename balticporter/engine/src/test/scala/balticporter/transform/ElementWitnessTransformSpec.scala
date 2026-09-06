package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{DecisionLog, Phase, Pipeline, Program, RuleScope}

/** `ElementWitnessTransform` — the §1(b) mechanism moving an element-typed array onto a type-class
  * WITNESS and dropping java's implicit `Object` bound. Every refusal kind is asserted, not
  * sampled (CLAUDE.md §3). */
class ElementWitnessTransformSpec extends munit.FunSuite:
  import ElementWitnessTransform.*

  private val java =
    """package com.demo;
      |import java.util.Arrays;
      |class Bag<T> {
      |  T[] items;
      |  int size;
      |  Bag (int capacity) { items = (T[])new Object[capacity]; }
      |  Bag (Bag<? extends T> other) { items = Arrays.copyOf(other.items, other.size); }
      |  Bag (T[] src, int start, int count) { items = Arrays.copyOfRange(src, start, start + count); }
      |  void grow (int n) { items = Arrays.copyOf(items, n); }
      |  T pop () { size--; T v = items[size]; items[size] = null; return v; }
      |  void clear () { Arrays.fill(items, 0, size, null); size = 0; }
      |  void wipe () { Arrays.fill(items, null); }
      |  boolean has (T value) {
      |    for (int i = 0; i < size; i++) if (items[i] == value) return true;
      |    return false;
      |  }
      |  Bag (Class<?> type, int n) { items = (T[])java.lang.reflect.Array.newInstance(type, n); }
      |  <V> V[] toArray (Class<V> type) { return (V[])java.lang.reflect.Array.newInstance(type, size); }
      |  static <T> Bag<T> of (int n) { return new Bag<T>(n); }
      |}
      |class Table<K> {
      |  K[] keyTable;
      |  Table (int n) { keyTable = (K[])new Object[n]; }
      |  int find (K key) {
      |    int i = 0;
      |    while (keyTable[i] != null) i++;
      |    if (key != null) return i;
      |    return -1;
      |  }
      |}
      |class Loose<E> {
      |  E[] slots;
      |  Loose (int n) { slots = (E[])new Object[n]; }
      |}
      |class Raw {
      |  static void take (Bag raw) {}
      |  static void call (Bag<String> b) { take(b); }
      |  static Object literal () { return Bag.class; }
      |}
      |""".stripMargin

  private def parse(): Program = SpoonTir.fromSource(java, "Demo.java")

  private case class Ported(after: Program, out: String, log: DecisionLog)

  private def run(p: Phase): Ported =
    val (after, log) = Pipeline.runTraced(parse(), List(p))
    Ported(after, new TirEmitter(after, notes = log).emit, log)

  private val Witness = "demo.MkArray"

  private def phase(
      subjects: Map[String, List[Int]] = Map("com.demo.Bag" -> List(0), "com.demo.Table" -> List(0)),
      unbound: Set[String] = Set("com.demo.Bag"),
      boxed: Option[String] = Some("demo.MkArray.boxed[{elem}]"),
  ) = new ElementWitnessTransform(
    witness = Witness, subjectTypes = subjects, dropBound = unbound, boxedWitness = boxed)

  private val witnessPhase = phase()
  private lazy val ported: Ported = run(witnessPhase)

  // ---- the no-op ------------------------------------------------------------------------------

  test("an empty subject map is a no-op and contributes NO fingerprint segment") {
    val t = new ElementWitnessTransform(witness = Witness)
    assert(t.isNoOp)
    assertEquals(t.surfaceFingerprint, "")
    val before = parse()
    val after  = t.run(before)
    assertEquals(after.units.size, before.units.size)
    assertEquals(t.refusals(after, after.units), Nil)
  }

  test("a witness with no subject is still a no-op; a subject with no witness is too") {
    assert(new ElementWitnessTransform(witness = Witness).isNoOp)
    assert(new ElementWitnessTransform(subjectTypes = Map("com.demo.Bag" -> List(0))).isNoOp)
  }

  test("a configured instance fingerprints the witness, its members, the subjects and the drop") {
    val fp = phase().surfaceFingerprint
    assert(clue(fp).contains("witness=demo.MkArray"))
    assert(fp.contains("com.demo.Bag[0]"))
    assert(fp.contains("com.demo.Table[0]"))
    assert(fp.contains("unbound=com.demo.Bag"))
    assert(fp.contains("boxed=demo.MkArray.boxed[{elem}]"))
    assert(fp.contains("create/copyOf/copyOfRange/nullOut/nullOutRange"))
  }

  test("the phase names the lane that counts its residue") {
    assertEquals(phase().accountedBy, Set(ElementWitnessCheck.Name))
  }

  // ---- what the fill owes (a raw formal filled to `Object`, a class literal's payload) ---------
  test("a raw formal filled to `C[Object]` gets java's unchecked conversion at the call, counted") {
    assert(ported.out.contains("Raw.take(b.asInstanceOf[com.demo.Bag[java.lang.Object]])"),
      ported.out.linesIterator.filter(l => l.contains("take") || l.contains("def call")).mkString("\n"))
    val rows = witnessPhase.refusals(ported.after, ported.after.units)
    assert(rows.exists(r => r.issue == ElementWitnessCheck.Issue.RawConversion && r.subject == "com.demo.Raw"),
      rows.map(r => s"${r.issue} ${r.subject}").mkString(", "))
  }

  test("a class literal's payload is filled like every other position this phase unbound") {
    assert(clue(ported.out).contains("classOf[com.demo.Bag[java.lang.Object]]"))
  }

  // ---- the bound ------------------------------------------------------------------------------

  test("a dropBound subject loses java's implicit Object bound; one that kept it still has it") {
    assert(clue(ported.out).contains("class Bag[T]"))
    assert(!ported.out.contains("class Bag[T <: java.lang.Object]"))
    assert(ported.out.contains("class Table[K <: java.lang.Object]"))
  }

  test("the drop is recorded as a decision naming the element parameter") {
    val d = ported.log.all.filter(_.subjectFqn == "com.demo.Bag")
      .filter(_.kind == balticporter.tir.Decision.Kind.RetypedSignature)
    assert(clue(d).nonEmpty)
    assert(d.exists(_.detail.get("elements").contains("T")))
  }

  // ---- the rewrites ---------------------------------------------------------------------------

  test("`(T[]) new Object[n]` becomes the witness's `create`") {
    assert(clue(ported.out).contains(s"summon[$Witness[T]].create("))
  }

  test("`Arrays.copyOf` and `copyOfRange` at an element-typed slot become the witness's own") {
    assert(clue(ported.out).contains(s"summon[$Witness[T]].copyOf("))
    assert(ported.out.contains(s"summon[$Witness[T]].copyOfRange("))
  }

  test("a release write `x[i] = null` becomes `nullOut`, and `Arrays.fill(…, null)` `nullOutRange`") {
    assert(clue(ported.out).contains(s"summon[$Witness[T]].nullOut("))
    assert(ported.out.contains(s"summon[$Witness[T]].nullOutRange("))
    // the whole-array form reads the receiver's own length; the four-argument one java's bounds
    assert(ported.out.contains(".nullOutRange((this.items.asInstanceOf[scala.Array[T]]), 0, this.size)"))
  }

  test("a generic FACTORY constructing a subject at its own parameter takes the clause") {
    assert(clue(ported.out).contains(s"def of[T](n: scala.Int)(using $Witness[T])"))
  }

  test("reference identity on a bound-dropped operand is ascribed to AnyRef — `eq` lives there") {
    assert(clue(ported.out).contains("asInstanceOf[scala.AnyRef] eq"))
  }

  // ---- the refusals ---------------------------------------------------------------------------

  private def findingsOf(t: ElementWitnessTransform, p: Ported) = t.refusals(p.after, p.after.units)

  test("a `null` sentinel at an element slot of a bound-KEPT subject is counted, never rewritten") {
    val t = phase()
    val r = run(t)
    val fs = findingsOf(t, r).filter(_.issue == ElementWitnessCheck.Issue.OccupancySentinel)
    assert(clue(fs).nonEmpty)
    assert(fs.forall(_.subject == "com.demo.Table"))
    // …and the site is left exactly as it was
    assert(clue(r.out).contains("keyTable(i) != null"))
  }

  test("an element-typed creation in a declaration the policy does not name is counted") {
    val t = phase()
    val fs = findingsOf(t, run(t)).filter(_.issue == ElementWitnessCheck.Issue.NonSubject)
    assertEquals(clue(fs).map(_.subject).distinct, List("com.demo.Loose"))
  }

  test("a creation REFLECTED out of a `Class` argument is counted, and the java text kept") {
    val t = phase()
    val r = run(t)
    val fs = findingsOf(t, r).filter(_.issue == ElementWitnessCheck.Issue.UnhandledCreation)
    assert(clue(fs).exists(_.subject == "com.demo.Bag"))
    assert(r.out.contains("java.lang.reflect.Array.newInstance"))
  }

  test("a creation at a METHOD's own parameter is not an element position, and is not claimed") {
    // `<V> V[] toArray(Class<V>)`: `V` is nobody's element type, so the phase neither rewrites the
    // site nor counts it — the row would name a declaration no policy key can reach.
    val t = phase()
    val fs = findingsOf(t, run(t)).filter(_.issue == ElementWitnessCheck.Issue.UnhandledCreation)
    assert(clue(fs).forall(f => f.detail.contains("`Class` argument")))
  }

  test("every refusal kind carries a §1 classification a reader can act on") {
    ElementWitnessCheck.Issue.values.foreach { i =>
      val c = ElementWitnessCheck.Issue.classification(i)
      assert(clue(c).contains("§1"), s"$i has no classification")
    }
  }

  // ---- policy ---------------------------------------------------------------------------------

  test("a subject with an empty index list is reported malformed rather than silently ignored") {
    val t = new ElementWitnessTransform(witness = Witness, subjectTypes = Map("com.demo.Bag" -> Nil))
    val binder = new balticporter.tir.PolicyBinder(parse(), parse().members)
    t.bindPolicy(binder)
    assert(clue(t.policyReport.findings).exists(_.key == "com.demo.Bag"))
  }

  test("the CONSTRUCTOR half is derived from the same value, one entry per element position") {
    assertEquals(
      constructorGivens(Map("a.B" -> List(0), "a.C" -> List(0, 1)), Witness),
      Map("a.B" -> "demo.MkArray:0", "a.C" -> "demo.MkArray:0|demo.MkArray:1"))
    assertEquals(constructorGivens(Map("a.B" -> List(0)), ""), Map.empty)
  }

  // ---- the merge contract ---------------------------------------------------------------------

  test("independent subjects UNION and the dropped bounds with them") {
    val a = new ElementWitnessTransform(witness = Witness, subjectTypes = Map("a.B" -> List(0)),
                                        dropBound = Set("a.B"))
    val b = new ElementWitnessTransform(witness = Witness, subjectTypes = Map("a.C" -> List(1)),
                                        dropBound = Set("a.C"))
    a.mergedWith(b) match
      case Right(m) =>
        val t = m.phase.asInstanceOf[ElementWitnessTransform]
        assertEquals(t.subjectTypes, Map("a.B" -> List(0), "a.C" -> List(1)))
        assertEquals(t.dropBound, Set("a.B", "a.C"))
        assertEquals(m.added, Set("a.C"))
      case Left(why) => fail(s"independent subjects must compose: $why")
  }

  test("one subject at two different index lists REFUSES, and says which") {
    val a = new ElementWitnessTransform(witness = Witness, subjectTypes = Map("a.B" -> List(0)))
    val b = new ElementWitnessTransform(witness = Witness, subjectTypes = Map("a.B" -> List(0, 1)))
    a.mergedWith(b) match
      case Left(why) => assert(clue(why).contains("a.B"))
      case Right(_)  => fail("two index lists for one subject is a choice, not a composition")
  }

  test("two witness TYPES refuse: the emitted constructors take a clause of one") {
    val a = new ElementWitnessTransform(witness = "x.Mk", subjectTypes = Map("a.B" -> List(0)))
    val b = new ElementWitnessTransform(witness = "y.Mk", subjectTypes = Map("a.C" -> List(0)))
    assert(a.mergedWith(b).isLeft)
  }

  test("a different phase is not composable with this one") {
    assert(phase().mergedWith(new MutableParamsTransform).isLeft)
  }

  test("the shared-surface subjects cover the subjects, the drops and the scope") {
    val t = new ElementWitnessTransform(witness = Witness, subjectTypes = Map("a.B" -> List(0)),
                                        dropBound = Set("a.C"), scope = RuleScope.Only(Set("a.d")))
    assertEquals(t.subjects, Set("a.B", "a.C", "a.d"))
  }
