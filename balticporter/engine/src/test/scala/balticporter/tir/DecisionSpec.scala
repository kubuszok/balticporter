package balticporter.tir

import java.nio.file.Files

/** The DECISION-PROVENANCE CHANNEL at the level a phase sees it. */
class DecisionSpec extends munit.FunSuite:

  private def d(kind: Decision.Kind, fqn: String, reason: Reason, detail: Map[String, String] = Map.empty) =
    Decision(kind, SymId.None, fqn, detail, reason, Origin("/abs/src/p/Foo.java", 7, 1))

  /** a phase that decides something about every integer literal it sees. */
  private class Deciding(val name: String) extends Phase:
    override def transformTerm(t: Term)(using Program): Term = t match
      case l @ Tree.Literal(Constant.IntC(v), _, o) =>
        record(d(Decision.Kind.RetypedSignature, s"p.Foo.lit$v", Reason.Configured(name, s"literal=$v")).copy(origin = o))
        l
      case other => other

  private def withProps[A](kv: (String, String)*)(body: => A): A =
    val saved = kv.map((k, _) => k -> Option(System.getProperty(k)))
    kv.foreach((k, v) => System.setProperty(k, v))
    try body
    finally saved.foreach {
      case (k, Some(v))    => System.setProperty(k, v)
      case (k, scala.None) => System.clearProperty(k)
    }

  // -------------------------------------------------------------------------
  // the classification is a TYPE, not a sentence
  // -------------------------------------------------------------------------

  test("every reason names which of §1's three kinds a reader must act in") {
    assertEquals(Reason.Universal("java-arrays-are-covariant").className, "universal")
    assertEquals(Reason.Configured("substitutions", "com.demo.Widget").className, "configured")
    assertEquals(Reason.LibraryRule("gl-handle-opaque").className, "library-rule")
    assert(clue(Reason.Universal("x").section).startsWith("§1(a)"))
    assert(clue(Reason.Configured("p", "k").section).startsWith("§1(b)"))
    assert(clue(Reason.LibraryRule("x").section).startsWith("§1(c)"))
    // the phase and the key survive the one column they share — that pair IS the edit an agent
    // has to make, so losing either would make the record unactionable.
    assertEquals(Reason.parse("configured", Reason.Configured("substitutions", "com.demo.W").detail),
                 Reason.Configured("substitutions", "com.demo.W"))
    // …including a key that itself contains the separator: the split is at the FIRST colon.
    assertEquals(Reason.parse("configured", Reason.Configured("p", "a:b").detail), Reason.Configured("p", "a:b"))
  }

  // -------------------------------------------------------------------------
  // the log is a value ONE RUN owns
  // -------------------------------------------------------------------------

  test("runTraced hands back what the phases decided; run still returns only the program") {
    val phases = List(new Deciding("one"), new Deciding("two"))
    val (prog, log) = Pipeline.runTraced(TinyProgram.program, phases)
    assertEquals(prog.units.size, TinyProgram.program.units.size)
    // TinyProgram has two int literals, each seen by both phases
    assertEquals(log.size, 4)
    assertEquals(log.counts, Map(Decision.Kind.RetypedSignature -> 4))
    assertEquals(log.all.map(_.reason).collect { case Reason.Configured(p, _) => p }.distinct.sorted,
                 List("one", "two"))
    // the old entry point is untouched — every existing caller keeps compiling and keeps behaving
    assertEquals(Pipeline.run(TinyProgram.program, phases).units.size, prog.units.size)
  }

  test("a phase reused for a SECOND run reports that run's decisions only") {
    // `Determinism.Full` translates twice through the same phase instances, and a porting program
    // that ports two source sets does the same. A buffer that survived would report the first
    // run's decisions as the second's — the exact contamination §5.1 records for the global srcmap.
    val phase = new Deciding("once")
    val a = Pipeline.runTraced(TinyProgram.program, List(phase))._2
    val b = Pipeline.runTraced(TinyProgram.program, List(phase))._2
    assertEquals(a.size, 2)
    assertEquals(b.size, 2)
    assertEquals(phase.decisions.size, 0, "the pipeline DRAINS a phase's buffer; nothing outlives the run")
  }

  test("a SKIPPED phase records nothing — a decision it never made is not reported as made") {
    val phase = new Deciding("skipped-one")
    withProps("balticporter.skipPhases" -> "skipped-one") {
      val buf = new java.io.ByteArrayOutputStream()
      val log = Console.withOut(buf)(Pipeline.runTraced(TinyProgram.program, List(phase))._2)
      assertEquals(log.size, 0)
    }
  }

  // -------------------------------------------------------------------------
  // the artifact
  // -------------------------------------------------------------------------

  test("decisions.tsv is SORTED, so accumulation order — i.e. phase order — never reaches the diff") {
    val one = new DecisionLog
    val two = new DecisionLog
    val rows = List(
      d(Decision.Kind.DroppedType, "com.demo.Widget", Reason.Configured("substitutions", "com.demo.Widget")),
      d(Decision.Kind.RenamedPackage, "com.demo.Gadget", Reason.Configured("package-rename", "com.demo -> org.port")),
      d(Decision.Kind.InjectedMember, "com.demo.Widget", Reason.Configured("substitutions", "inject")),
    )
    rows.foreach(one.record)
    rows.reverse.foreach(two.record)
    val dir = Files.createTempDirectory("decisions")
    val a = Files.readString(Decision.write(dir.resolve("a"), one))
    val b = Files.readString(Decision.write(dir.resolve("b"), two))
    assertEquals(a, b)
    assert(clue(a).startsWith(Decision.Header))
    // DroppedType before InjectedMember before RenamedPackage — by kind, then by subject
    assertEquals(Decision.parseAll(dir.resolve("a/decisions.tsv")).map(_.kind),
                 List(Decision.Kind.DroppedType, Decision.Kind.InjectedMember, Decision.Kind.RenamedPackage))
  }

  test("an EMPTY log still writes a header-only file — nothing decided is not nothing run") {
    val dir = Files.createTempDirectory("decisions-empty")
    val p   = Decision.write(dir, new DecisionLog)
    assertEquals(Files.readString(p), Decision.Header + "\n")
    assertEquals(Decision.parseAll(p), Nil)
  }

  test("a row round-trips, and no SymId reaches the file") {
    val log = new DecisionLog
    log.record(Decision(Decision.Kind.DroppedMember, SymId(42), "com.demo.Widget#labels",
      Map("key" -> "com.demo.Widget#labels", "why" -> "replaced by a codec"),
      Reason.Configured("substitutions", "com.demo.Widget#labels"), Origin("/abs/src/com/demo/Widget.java", 12, 3)))
    val dir  = Files.createTempDirectory("decisions-rt")
    val text = Files.readString(Decision.write(dir, log))
    assert(!clue(text).contains("42"), "symbol ids are interning order and must never reach an artifact")
    val back = Decision.parseAll(dir.resolve("decisions.tsv"))
    assertEquals(back.map(_.kind), List(Decision.Kind.DroppedMember))
    assertEquals(back.head.subjectFqn, "com.demo.Widget#labels")
    assertEquals(back.head.reason, Reason.Configured("substitutions", "com.demo.Widget#labels"))
    assertEquals(back.head.detail("why"), "replaced by a codec")
    assertEquals(back.head.origin.line, 12)
    assertEquals(back.head.subject, SymId.None, "and it does not come back pretending to have one")
  }
