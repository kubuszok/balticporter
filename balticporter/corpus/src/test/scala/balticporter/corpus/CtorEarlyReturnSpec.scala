package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

import java.nio.file.{ Files, Path }

/** A java `return` in a secondary constructor leaves a boundary around the constructor's own statements: scala's `return` belongs to a method, and Scala.js cannot link one in a constructor. The port
  * is COMPILED under `-Werror` and run: every early exit leaves exactly where java's did, through a broad `catch` too, and a lambda's own `return` stays its own.
  */
class CtorEarlyReturnSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Family {
      |  public int n;
      |  public Runnable r;
      |  public Family() { n = 0; }
      |  public Family(String[] in, int k) {
      |    this();
      |    if (in == null) return;
      |    for (int i = 0; i < k; i++) { if (in[i] == null) return; n++; }
      |    r = () -> { if (n > 1000) return; n += 1000; };
      |    try { n += 10; if (k > 3) return; n += 100; } catch (RuntimeException e) { n = -1; }
      |  }
      |  public static String run() {
      |    Family a = new Family(null, 1);
      |    Family b = new Family(new String[] { "x", null, "y" }, 3);
      |    Family c = new Family(new String[] { "x", "y", "z", "w" }, 4);
      |    Family d = new Family(new String[] { "x" }, 1);
      |    d.r.run();
      |    return a.n + "," + b.n + "," + c.n + "," + d.n;
      |  }
      |}
      |""".stripMargin

  private lazy val emitted: List[String] =
    val p  = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val em = new TirEmitter(p)
    p.units.map(em.emitUnit)

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("ctor-early-return-src")
    val files = emitted.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("ctor-early-return-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-Werror" :: "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(
      !rep.hasErrors,
      s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${emitted.mkString("\n")}"
    )
    out

  private def ctorText: String =
    val all   = emitted.mkString("\n")
    val start = all.indexOf("def this(in:")
    assert(start >= 0, s"no secondary constructor in:\n$all")
    all.substring(start, all.indexOf("\n  }", start) + 4)

  test("the constructor's own returns leave a named boundary, and a jump through the broad catch is re-thrown") {
    val t = ctorText
    assert(clue(t).contains(s"scala.util.boundary[scala.Unit] { ${TirEmitter.CtorReturn} ?=>"))
    assert(t.contains(s"scala.util.boundary.break(())(using ${TirEmitter.CtorReturn})"), t)
    assert(t.contains(s"case ${TirEmitter.BreakGuard}: scala.util.boundary.Break[?] => throw ${TirEmitter.BreakGuard}"), t)
  }

  test("every early exit leaves where java's did") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Family").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "0,1,14,1111")
  }
