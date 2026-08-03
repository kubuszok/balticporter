package balticporter.runner

import java.nio.file.{Files, Path}

/** `just debug-emit`, end to end: a Java source tree in, the named type's TIR and emitted Scala
  * out, at the phase boundary asked for.
  *
  * The entry point had no test at all, which for a DIAGNOSTIC is the worst place to have none: it
  * is used when something else is already wrong, and a tool that silently prints the wrong thing
  * (an unrecognised `--dump-after` alias printing nothing reads exactly like "the phase changed
  * nothing") sends its operator after the wrong defect. Each assertion below is on the OUTPUT.
  */
class DebugEmitSpec extends munit.FunSuite:

  /** one small tree, modelled once per test — Spoon is the expensive part. */
  private val sample: Path =
    val d = Files.createTempDirectory("bp-debug-emit")
    Files.createDirectories(d.resolve("p"))
    Files.writeString(d.resolve("p/Sample.java"),
      """package p;
        |
        |import java.util.ArrayList;
        |import java.util.List;
        |
        |public class Sample {
        |    public List<String> items = new ArrayList<String>();
        |
        |    public int count() {
        |        return items.size();
        |    }
        |}
        |""".stripMargin)
    d

  private def emit(args: String*): String =
    val buf = new java.io.ByteArrayOutputStream()
    Console.withOut(buf)(DebugEmit.main(("--root" +: sample.toString +: args).toArray))
    buf.toString

  test("--fqn --scala prints the named type's TIR and its EMITTED SCALA") {
    val out = emit("--fqn", "p.Sample", "--scala", "--fast")
    assert(out.contains("===== TIR p.Sample ====="), out)
    assert(out.contains("===== SCALA p.Sample ====="), out)
    val scala = out.substring(out.indexOf("===== SCALA"))
    // the emitted text of that type, not a summary of it
    assert(scala.contains("class Sample"), scala)
    assert(scala.contains("count"), scala)
  }

  test("no --fqn LISTS the model rather than printing nothing") {
    val out = emit("--fast")
    assert(out.contains("p.Sample"), out)
    assert(out.contains("no --fqn given"), out)
  }

  test("--dump-after brackets the phase — and the CLI alias is translated to the phase's own name") {
    // `collections` is the alias; the phase calls itself `java-collections->scala`, and an
    // untranslated alias would match no phase and print NOTHING at all.
    val out = emit("--fqn", "p.Sample", "--fast", "--phases", "collections", "--dump-after", "collections")
    assert(out.contains("===== TIR AFTER phase 'java-collections->scala' [p.Sample] ====="), out)
    assert(!out.contains("TIR BEFORE"), out)
  }

  test("--dump-before and --dump-after bracket the same phase, and the TIR between them DIFFERS") {
    val out = emit("--fqn", "p.Sample", "--fast", "--phases", "collections",
      "--dump-before", "collections", "--dump-after", "collections")
    val i = out.indexOf("===== TIR AFTER phase")
    assert(i > 0, out)
    val before = out.substring(0, i)
    val after  = out.substring(i)
    assert(before.contains("===== TIR BEFORE phase 'java-collections->scala' [p.Sample] ====="), before)
    // the whole point of the boundary: java.util.List on one side, the shim on the other
    assert(before.contains("java.util.List"), before)
    assert(!after.contains("java.util.List"), after)
  }

  // -------------------------------------------------------------------------
  // --phases resolves through the SPI — ONE name→phase truth with a port's .conf
  // -------------------------------------------------------------------------

  test("a phase is named EXACTLY as a `.conf` names it — the private registry had already diverged") {
    // It said `panama`; the config front door says `panama-ffi`. Two doors, two names for one
    // phase, and an agent that reads a `.conf` and types the name into the diagnostic is told
    // "unknown phase". The registry is gone, so the two cannot disagree again.
    assertEquals(DebugEmit.phasesFor(List("panama-ffi")).map(_.map(_.name)), Right(List("jni->panama")))
    assert(DebugEmit.phasesFor(List("panama")).isLeft, "the OLD spelling is not a phase name anywhere")
    DebugEmit.phasesFor(List("panama")).left.foreach { why =>
      assert(clue(why).contains("panama-ffi"), "…and the refusal LISTS what is available")
    }
  }

  test("resolution widened to every default-constructible phase, in the order given") {
    assertEquals(
      DebugEmit.phasesFor(List("mutable-params", "collections")).map(_.map(_.name)),
      Right(List("reassigned-params->var", "java-collections->scala")))
  }

  test("a phase that takes POLICY is refused, and told where policy lives") {
    // `primitive-to-opaque` cannot be built from nothing — this tool reads no port `.conf` on
    // purpose (a second assembly path would be free to drift from `PortRun`'s). The refusal is the
    // factory's OWN error, so a new required key needs nothing here.
    val why = DebugEmit.phasesFor(List("primitive-to-opaque")).swap.getOrElse("")
    assert(clue(why).contains("takes POLICY"))
    assert(why.contains("PortRun"))
    assert(why.contains("fqn"), "the factory's own message says WHICH key it wanted")
  }

  test("a RESERVED name keeps its specific refusal rather than becoming 'unknown phase'") {
    val why = DebugEmit.phasesFor(List("package-rename")).swap.getOrElse("")
    assert(clue(why).contains("packageRenames"), "the thing to write instead")
  }

  test("the dump flags it sets are RESTORED — an unforked run must not leave one behind") {
    val keys = List("balticporter.dumpTirBefore", "balticporter.dumpTirAfter", "balticporter.dumpOnly")
    val before = keys.map(k => k -> Option(System.getProperty(k)))
    emit("--fqn", "p.Sample", "--fast", "--phases", "collections", "--dump-after", "collections")
    assertEquals(keys.map(k => k -> Option(System.getProperty(k))), before)
  }
