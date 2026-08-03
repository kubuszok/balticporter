package balticporter.runner

import balticporter.tir.*

/** THE CONSERVATION CHECK ACROSS A RENAME — `CLAUDE.md` §4.56's two-namespace rule, applied to the
  * one artifact in the run that holds BOTH programs.
  *
  * `MarkerCheck.check` compares the frontend's output against the pipeline's, and a port's pipeline
  * ends with `PackageRenameTransform`. So the two programs hold the SAME declarations under
  * DIFFERENT names: the upstream ones and the emitted ones. Matched by `fullName`, the owned-unit
  * filter matches nothing on every renaming port, the minted set is empty, and the check reports a
  * confident ZERO for the life of that port — which is exactly the failure §4.56 records for
  * `dropped-types.tsv`, where a derived rule *"had never once fired on a renaming port"* while the
  * claim that it worked lived only in prose.
  *
  * A unit's `SymId` is what survives the pipeline unchanged; a rename moves `fullName` and never the
  * id. Written as two hand-built programs rather than as a port run, because the property is exactly
  * *the same ids under two names* and a fixture that parses Java would prove it only incidentally.
  */
class MarkerNamespaceSpec extends munit.FunSuite:

  private val clsId = SymId(0)
  private val defId = SymId(1)
  private val o     = Origin("com/demo/Plain.java", 2, 30)

  private def program(pkg: String, body: Option[Term]): Program =
    val cls  = Symbol(clsId, "Plain", s"$pkg.Plain", Flags(), SymId.None, TypeRepr.NoType)
    val mem  = Symbol(defId, "twice", s"$pkg.Plain#twice", Flags(), clsId, TypeRepr.NoType)
    val dd   = Tree.DefDef(defId, Nil, TypeTree(TypeRepr.NoType, o), body, o)
    val unit = Tree.ClassDef(clsId, Nil, scala.None, List(dd), o)
    new Program(List(unit), SymbolTable(List(cls, mem)), Xref.build(List(unit)), MemberIndex.empty)

  private val marked =
    Tree.Unportable.open(Tree.Literal(Constant.IntC(1), TypeRepr.NoType, o),
      UnportableKind.FrontendBlindSpot, scala.None, "the fixture's stand-in", TypeRepr.NoType, o)

  test("an ERASURE is still seen when the pipeline RENAMED the package underneath it") {
    val before = program("com.demo", Some(marked))
    // …and the same declarations, same ids, under the EMITTED names, with the marked subtree gone.
    val after  = program("org.port", Some(Tree.Literal(Constant.IntC(0), TypeRepr.NoType, o)))

    val fs = MarkerCheck.check(before, after, after.units)
    assertEquals(fs.map(_.kind), List("erased"),
      "the check compared two namespaces and reported nothing — §4.56's failure, reproduced")
    assertEquals(fs.head.owner, "com.demo.Plain#twice",
      "the owner is named as the marker was MINTED — the upstream name, which is what a policy key " +
        "and an ENGINE-LIMITS entry are both written in")
  }

  test("…and a DISCHARGE across the same rename is still not a finding") {
    val before = program("com.demo", Some(marked))
    val after  = program("org.port", Some(marked.resolved("some-phase", "answered")))
    assertEquals(MarkerCheck.check(before, after, after.units).filter(_.kind == "erased"), Nil)
  }

  test("the OPEN lane reads the FINAL program, so it is unaffected either way") {
    val before = program("com.demo", Some(marked))
    val after  = program("org.port", Some(marked))
    val fs     = MarkerCheck.check(before, after, after.units)
    assertEquals(fs.map(_.kind), List("open"))
    assertEquals(fs.head.owner, "org.port.Plain#twice",
      "an OPEN finding is about what this run is ABOUT TO EMIT, so it carries the emitted name")
  }
