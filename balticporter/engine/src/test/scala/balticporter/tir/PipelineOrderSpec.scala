package balticporter.tir

/** `Pipeline.order` orders INSTANCES, not names — `ENGINE-LIMITS.md` CT9 Face B.
  *
  * The defect this pins was silent from the day merging existed: the sort ran over a
  * `name -> phase` map, so two same-name instances collapsed to the LATER one and the other never
  * ran. Nothing could see it — the run emitted, every check counted the same, and the missing
  * phase's decisions were missing because the phase was.
  *
  * Two same-name instances is precisely what a DECLINED or REFUSED merge leaves in the effective
  * pipeline (`SurfaceFold`, DESIGN.md §8.13), which is the pre-merge behaviour the merge contract
  * deliberately keeps. So this is asserted the way §3 asks: on the OUTPUT of running them, never on
  * the list's shape alone.
  */
class PipelineOrderSpec extends munit.FunSuite:

  /** adds `by` to every integer literal, so "did this instance run" is readable off the tree. */
  private class Bump(val name: String, by: Int) extends Phase:
    override def transformTerm(t: Term)(using Program): Term = t match
      case l @ Tree.Literal(Constant.IntC(v), _, _) => l.copy(const = Constant.IntC(v + by))
      case other                                    => other

  private class Named(val name: String, override val runsAfter: Set[String] = Set.empty,
                      override val runsBefore: Set[String] = Set.empty) extends Phase

  private def literals(p: Program): List[Int] =
    val out = collection.mutable.ListBuffer[Int]()
    val collect = new Phase:
      def name = "collect"
      override def transformTerm(t: Term)(using Program): Term =
        t match
          case Tree.Literal(Constant.IntC(v), _, _) => out += v; t
          case _                                    => t
    given Program = p
    p.units.foreach(u => StandardTraversal.mapClassDef(collect, u))
    out.toList.sorted

  // -------------------------------------------------------------------------------------------
  // the defect
  // -------------------------------------------------------------------------------------------

  test("TWO instances of one NAME both survive the ordering — the silent drop, caught") {
    val a = new Bump("dup", 1)
    val b = new Bump("dup", 10)
    // by NAME this list is one phase and the later instance won; by INSTANCE it is two
    assertEquals(Pipeline.order(List(a, b)).map(_ eq a), List(true, false))
    assertEquals(Pipeline.order(List(a, b)).size, 2)
  }

  test("…and BOTH of them RUN — the tree is the evidence, not the list") {
    // TinyProgram's literals are 0 and 1. Only the later instance running reads 10/11; both
    // running reads 11/12, which is the number that was missing.
    val out = Pipeline.run(TinyProgram.program, List(new Bump("dup", 1), new Bump("dup", 10)))
    assertEquals(literals(out), List(11, 12))
  }

  test("an ordering edge NAMES a phase, so it constrains EVERY instance of that name") {
    // "after `dup`" cannot mean "after one of the two `dup`s and possibly before the other": an
    // edge that under-constrains is a schedule nobody declared.
    val a    = new Bump("dup", 1)
    val b    = new Bump("dup", 10)
    val last = new Named("last", runsAfter = Set("dup"))
    assertEquals(Pipeline.order(List(a, last, b)).map(_.name), List("dup", "dup", "last"))
  }

  test("a cycle is still a cycle, and it still names the phases") {
    val e = intercept[IllegalStateException] {
      Pipeline.order(List(new Named("a", runsAfter = Set("b")), new Named("b", runsAfter = Set("a"))))
    }
    assert(clue(e.getMessage).contains("cycle"))
    assert(e.getMessage.contains("a"))
    assert(e.getMessage.contains("b"))
  }

  // -------------------------------------------------------------------------------------------
  // …and nothing else moves. Every port in the corpus has distinct phase names, so the ordering
  // this produces for them has to be the one it always produced (CLAUDE.md §5: zero movement).
  // -------------------------------------------------------------------------------------------

  test("distinct names order exactly as before: stable in declaration order, successors by name") {
    val ps = List(new Named("z"), new Named("m", runsBefore = Set("z")), new Named("a"),
                  new Named("k", runsAfter = Set("a")))
    assertEquals(Pipeline.order(ps).map(_.name), List("m", "a", "z", "k"))
  }

  test("an edge naming a phase this pipeline does not have is ignored, as it always was") {
    val ps = List(new Named("a", runsAfter = Set("absent")), new Named("b", runsBefore = Set("absent")))
    assertEquals(Pipeline.order(ps).map(_.name), List("a", "b"))
  }

  test("a phase that declares itself its own predecessor is a cycle, not a no-op") {
    val e = intercept[IllegalStateException](Pipeline.order(List(new Named("a", runsAfter = Set("a")))))
    assert(clue(e.getMessage).contains("cycle"))
  }
