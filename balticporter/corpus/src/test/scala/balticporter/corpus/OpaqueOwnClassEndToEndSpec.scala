package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{ OpaqueSpec, Pipeline }
import balticporter.transform.PrimitiveToOpaqueTransform

import java.nio.file.{ Files, Path }

/** `OpaqueSpec.Target.OwnClass` end to end: the emitted opaque type, its object and a caller are COMPILED by the Scala compiler and RUN, answering what the java answers — including that reading a
  * literal constant does not initialise the class.
  */
class OpaqueOwnClassEndToEndSpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Probe { public static int hits = 0; public static Object hit() { hits++; return null; } }
      |public class Align {
      |  static public final int center = 1;
      |  static public final int top = 1 << 1;
      |  static public final int left = 1 << 3;
      |  static public final int topLeft = top | left;
      |  static final Object marker = Probe.hit();
      |  private Align() {}
      |  static public final boolean isLeft(int align) { return (align & left) != 0; }
      |  static public final boolean has(int align, int flags) { return (align & flags) == flags; }
      |  static public String toString(int align) { return isLeft(align) ? "left" : "center"; }
      |}
      |public class Use {
      |  private int align = Align.center;
      |  public static String run() {
      |    int before = Probe.hits;
      |    int c = Align.center;
      |    int afterConstant = Probe.hits;
      |    Use u = new Use();
      |    u.align = Align.topLeft;
      |    boolean l = Align.isLeft(u.align);
      |    boolean h = Align.has(u.align, Align.top);
      |    String s = Align.toString(u.align) + "/" + Align.toString(c);
      |    return before + "," + afterConstant + "," + Probe.hits + "," + c + "," + l + "," + h + "," + s;
      |  }
      |}
      |""".stripMargin

  private lazy val compiled: Path =
    val ph    = new PrimitiveToOpaqueTransform(OpaqueSpec(fqn = "demo.Align", hints = Set("demo.Use#align"), target = OpaqueSpec.Target.OwnClass()))
    val p     = Pipeline.run(SpoonTir.fromSource(src), List(ph))
    val em    = new TirEmitter(p)
    val dir   = Files.createTempDirectory("own-class-e2e-src")
    val files = p.units.zipWithIndex.map { (u, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, em.emitUnit(u)); f.toString
    }
    val out      = Files.createTempDirectory("own-class-e2e-out")
    val stdlib   = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val reporter = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(
      !reporter.hasErrors,
      s"the emission does not compile:\n${reporter.allErrors.map(_.msg.message).mkString("\n")}\n${files.map(f => Files.readString(Path.of(f))).mkString("\n")}"
    )
    out

  test("the emitted opaque type, its object and its caller compile, and run with java's answers") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Use").getMethod("run").invoke(null).asInstanceOf[String]
    // java: reading the literal constant initialises nothing (0,0); the first static CALL does (1)
    assertEquals(got, "0,0,1,1,true,true,left/center")
  }
