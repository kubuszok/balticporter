package balticporter.corpus

import balticporter.runner.PortRun
import balticporter.testkit.PortSuite
import balticporter.tir.*

/** THE TWO WALKS THAT WERE STILL COUNTING A CLASS BODY, and what each one's blindness costs.
  *
  * A METHOD-LOCAL class is a `BlockStatement` (JLS 14.3, catalog `JS-C30`), not a type member, so a
  * `cd.body.foreach { case c: Tree.ClassDef => … }` recursion answers *there is no nested type
  * here* about a type the program declares. `StandardTraversal.allClassDefs` exists precisely so
  * that answer is given once; twenty-eight recursions moved onto it and these two did not.
  *
  * The two consequences are not the same size:
  *
  *   - `PortRun.declaredSymbols` builds the SUBJECT SET `NoteCoverageCheck` joins on, and a
  *     decision about a subject outside it is EXCLUDED DELIBERATELY — a policy key that matched
  *     nothing, a type another module owns, an injected FQN with no `SymId`. So a local class's
  *     members were not an uncovered finding, they were a silent EXEMPTION from note coverage that
  *     reads exactly like the three legitimate ones. That is the shape `CLAUDE.md` §3 is about: no
  *     count moves, the output compiles, and the check reports a confident zero;
  *   - `MarkerCheck.inventory` still FOUND every marker (the enclosing member's term scan reaches
  *     straight through a local class) and sited it on the enclosing METHOD. The counts were right
  *     and the attribution was not — a reader is sent to the wrong declaration.
  *
  * Both are asserted here because both are invisible to every other instrument: `markers` is 0 on
  * all fifteen ports and `porter-notes` is 0 on all fifteen, so neither fix can move a number.
  */
class LocalClassSubjectsSpec extends PortSuite:

  private val src =
    """package demo;
      |class Holder {
      |  int field;
      |  int outer() {
      |    class Local {
      |      int inner;
      |      int twice() { return inner * 2; }
      |    }
      |    return new Local().twice();
      |  }
      |}
      |""".stripMargin

  private lazy val ported = port(src)

  private def subjects: Set[String] =
    given Program = ported.after
    val into = collection.mutable.Set.empty[SymId]
    ported.after.units.foreach(u => PortRun.declaredSymbols(u, into))
    into.flatMap(s => ported.after.symbolOf(s).map(_.name)).toSet

  test("a METHOD-LOCAL class is a note-coverage SUBJECT — outside the set it is a silent exemption") {
    assert(clue(subjects).contains("Local"))
  }

  test("…and so are its MEMBERS, which is where a decision would actually land") {
    assert(clue(subjects).contains("inner"))
    assert(clue(subjects).contains("twice"))
  }

  test("…and the walk still holds what it always did — the type and its own members") {
    val s = subjects
    assert(s.contains("Holder"))
    assert(s.contains("field"))
    assert(s.contains("outer"))
  }

  // -- the marker half: the count was right, the ATTRIBUTION was not ----------------------------

  /** wraps the LOCAL class's own method body in an open marker and nothing else — `Tree.Unportable`
    * refuses a synthetic origin, so the marker takes the body's real one. */
  private class MintInLocal extends Phase:
    def name: String = "test/mint-in-local"
    override def transformDefDef(d: Tree.DefDef)(using p: Program): Tree.DefDef =
      if !p.symbolOf(d.symbol).exists(_.name == "twice") then d
      else d.copy(rhs = d.rhs.map(r =>
        Tree.Unportable.open(r, UnportableKind.FrontendBlindSpot, scala.None,
          "a planted refusal", r.tpe, r.origin)))

  test("a marker inside a method-local class is sited on THAT class's member, not on the enclosing method") {
    val p     = port(src, new MintInLocal)
    val sited = MarkerCheck.inventory(p.after, p.after.units)
    assertEquals(sited.size, 1, clue(sited.map(_.ownerFqn)).toString)
    // `twice` is the local class's own method; before this, the enclosing `outer` claimed it,
    // because a method-local class is not in `cd.body` and the enclosing member's term scan
    // reaches through it.
    assertEquals(p.after.symbolOf(sited.head.owner).map(_.name), Some("twice"))
  }

  test("…and it is counted EXACTLY ONCE — the cross-class claim must not double-site it") {
    val p = port(src, new MintInLocal)
    assertEquals(MarkerCheck.inventory(p.after, p.after.units).size, 1)
  }
