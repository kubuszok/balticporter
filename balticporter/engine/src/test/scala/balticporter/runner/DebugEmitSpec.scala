package balticporter.runner

import java.nio.file.{Files, Path}

/** `just debug-emit`, end to end: a Java source tree in, the named type's TIR and emitted Scala
  * out, at the phase boundary asked for. */
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

  test("…and the phases `PortRun` WEAVES are nameable too — the SPI is not the whole pipeline") {
    // An idiom phase is §1(a), so it reaches no `TransformFactory` — a knob on an (a) is the shape
    // §1 forbids. Resolved through the registry alone the diagnostic answered "unknown transform"
    // about phases that run in EVERY port and cannot be turned off, which is exactly §4.6's promise
    // ("is this phase even responsible" costs one run and no diff) failing for the two phases an
    // operator cannot switch off any other way.
    assertEquals(DebugEmit.phasesFor(List("sam-anon->lambda")).map(_.map(_.name)),
                 Right(List("sam-anon->lambda")))
    assertEquals(DebugEmit.phasesFor(List("collections", "sam-anon->lambda")).map(_.map(_.name)),
                 Right(List("java-collections->scala", "sam-anon->lambda")))
  }

  test("…named by PortRun's own list, so the two doors cannot drift — and each call is a FRESH phase") {
    // The `panama`/`panama-ffi` lesson at one more door: a copy of the woven list here would model a
    // pipeline the run does not have. And a phase carries the buffers it fills, so handing two
    // `--phases` runs one instance would make each file the other's rows.
    val woven = PortRun.wovenIdiomPhases.map(_.name)
    assertEquals(DebugEmit.phasesFor(woven).map(_.map(_.name)), Right(woven))
    val a = DebugEmit.phasesFor(List("sam-anon->lambda")).toOption.get.head
    val b = DebugEmit.phasesFor(List("sam-anon->lambda")).toOption.get.head
    assert(!(a eq b), "two resolutions must not share one phase instance")
  }

  test("…and an unknown name LISTS them beside the SPI's, or it sends the reader after a factory\n" +
       "     that does not exist") {
    val why = DebugEmit.phasesFor(List("no-such-phase")).swap.getOrElse("")
    assert(clue(why).contains("sam-anon->lambda"), "the woven half")
    assert(why.contains("collections"), "…and the SPI half")
  }

  test("the dump flags it sets are RESTORED — an unforked run must not leave one behind") {
    val keys = List("balticporter.dumpTirBefore", "balticporter.dumpTirAfter", "balticporter.dumpOnly")
    val before = keys.map(k => k -> Option(System.getProperty(k)))
    emit("--fqn", "p.Sample", "--fast", "--phases", "collections", "--dump-after", "collections")
    assertEquals(keys.map(k => k -> Option(System.getProperty(k))), before)
  }
