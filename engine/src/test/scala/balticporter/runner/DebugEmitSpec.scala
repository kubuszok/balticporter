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

  test("the dump flags it sets are RESTORED — an unforked run must not leave one behind") {
    val keys = List("balticporter.dumpTirBefore", "balticporter.dumpTirAfter", "balticporter.dumpOnly")
    val before = keys.map(k => k -> Option(System.getProperty(k)))
    emit("--fqn", "p.Sample", "--fast", "--phases", "collections", "--dump-after", "collections")
    assertEquals(keys.map(k => k -> Option(System.getProperty(k))), before)
  }
