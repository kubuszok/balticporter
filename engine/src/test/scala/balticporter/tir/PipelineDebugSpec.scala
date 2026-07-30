package balticporter.tir

/** The kill switch of CLAUDE.md §4.6, exercised. A flag that is wired but never used is exactly
  * the defect class §3 is about, so each of these asserts the OUTPUT changed, not that the flag
  * parsed. */
class PipelineDebugSpec extends munit.FunSuite:

  /** a phase that adds `by` to every integer literal — so "did this phase run" is readable off
    * the tree rather than off a log line. */
  private class Bump(val name: String, by: Int) extends Phase:
    override def transformTerm(t: Term)(using Program): Term = t match
      case l @ Tree.Literal(Constant.IntC(v), _, _) => l.copy(const = Constant.IntC(v + by))
      case other                                    => other

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

  private def withProps[A](kv: (String, String)*)(body: => A): A =
    val saved = kv.map((k, _) => k -> Option(System.getProperty(k)))
    kv.foreach((k, v) => System.setProperty(k, v))
    try body
    finally saved.foreach {
      case (k, Some(v))    => System.setProperty(k, v)
      case (k, scala.None) => System.clearProperty(k)
    }

  private def capture(body: => Unit): String =
    val buf = new java.io.ByteArrayOutputStream()
    Console.withOut(buf)(body)
    buf.toString

  private val phases = List(new Bump("bump-one", 1), new Bump("bump-ten", 10))

  test("baseline: both phases run") {
    // TinyProgram's literals are 0 and 1
    assertEquals(literals(Pipeline.run(TinyProgram.program, phases)), List(11, 12))
  }

  test("skipPhases drops exactly the named phase — one run, no source edit") {
    withProps("balticporter.skipPhases" -> "bump-ten") {
      val out = capture { assertEquals(literals(Pipeline.run(TinyProgram.program, phases)), List(1, 2)) }
      assert(out.contains("SKIPPED phase 'bump-ten'"), out)
    }
  }

  test("skipPhases=* is the whole-pipeline kill switch") {
    withProps("balticporter.skipPhases" -> "*") {
      capture { assertEquals(literals(Pipeline.run(TinyProgram.program, phases)), List(0, 1)) }
    }
  }

  test("a skipPhases name that matches NO phase is reported, and the available names listed") {
    withProps("balticporter.skipPhases" -> "bmup-ten") {
      val out = capture { Pipeline.run(TinyProgram.program, phases) }
      assert(out.contains("names no such phase: bmup-ten"), out)
      assert(out.contains("bump-one, bump-ten"), out)
    }
  }

  test("dumpTirAfter prints the TIR at that phase boundary, narrowed by dumpOnly") {
    withProps("balticporter.dumpTirAfter" -> "bump-one", "balticporter.dumpOnly" -> "p.Foo") {
      val out = capture { Pipeline.run(TinyProgram.program, phases) }
      assert(out.contains("===== TIR AFTER phase 'bump-one' [p.Foo] ====="), out)
      assert(out.contains("ClassDef class p.Foo#1"), out)
      assert(out.contains("Literal 1 : scala.Int"), out) // 0 has been bumped once, not yet ten
      assert(!out.contains("TIR BEFORE"), out)
    }
  }

  test("dumpTirBefore and dumpTirAfter bracket the same phase, and the tree between them DIFFERS") {
    withProps("balticporter.dumpTirBefore" -> "bump-ten", "balticporter.dumpTirAfter" -> "bump-ten",
              "balticporter.dumpOnly" -> "p.Foo") {
      val out = capture { Pipeline.run(TinyProgram.program, phases) }
      val before = out.split("===== end TIR BEFORE").head
      val after  = out.split("===== TIR AFTER").last
      assert(before.contains("Literal 1 : scala.Int"), before)
      assert(after.contains("Literal 11 : scala.Int"), after)
    }
  }

  test("dumpOnly naming a unit the program does not have says so, rather than printing nothing") {
    withProps("balticporter.dumpTirAfter" -> "bump-one", "balticporter.dumpOnly" -> "p.NotHere") {
      val out = capture { Pipeline.run(TinyProgram.program, phases) }
      assert(out.contains("<no unit named 'p.NotHere'"), out)
    }
  }

  test("with no flags set the pipeline prints nothing — a diagnostic that always prints is ignored") {
    assertEquals(capture { Pipeline.run(TinyProgram.program, phases) }, "")
  }
