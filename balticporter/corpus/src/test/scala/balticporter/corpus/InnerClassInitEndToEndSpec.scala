package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{ ClassInitTriggerCheck, Pipeline }
import balticporter.tir.ClassInitTriggerCheck.Issue

import java.nio.file.{ Files, Path }

/** The class-initialisation trigger of an INNER class: its companion is emitted inside the enclosing class's body, so the trigger names it by simple name — the package path does not exist. The port
  * is compiled and run: constructing the inner class runs its static initialiser, as java does.
  */
class InnerClassInitEndToEndSpec extends PortSuite:

  private val src =
    """package demo;
      |public class Outer {
      |  public static int hits;
      |  static int bump() { hits++; return hits; }
      |  public class Inner {
      |    static final long MASK = (1L << 53) - 1;
      |    static int seen = Outer.bump();
      |    public long mask() { return MASK; }
      |  }
      |  public class Sibling extends Inner {
      |    static int own = 3;
      |  }
      |  public static String run() {
      |    Outer o = new Outer();
      |    String before = "" + hits;
      |    Inner i = o.new Inner();
      |    return before + "," + hits + "," + i.mask();
      |  }
      |}
      |""".stripMargin

  private lazy val emitted: List[String] =
    val p  = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val em = new TirEmitter(p)
    p.units.map(em.emitUnit)

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("inner-class-init-e2e-src")
    val files = emitted.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("inner-class-init-e2e-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(
      !rep.hasErrors,
      s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${emitted.mkString("\n")}"
    )
    out

  test("an inner class's trigger names its companion by simple name, never through the package") {
    val text = emitted.mkString("\n")
    assert(text.contains("val _ = Inner"), text)
    assert(!text.contains("val _ = demo.Outer.Inner"), text)
  }

  test("the port compiles, and constructing the inner class runs its static initialiser") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val outer  = loader.loadClass("demo.Outer")
    val res    = outer.getMethod("run").invoke(null)
    assertEquals(res, s"0,1,${(1L << 53) - 1}")
  }

  test("a subclass outside the body that declares the ancestor's companion is a COUNTED refusal") {
    val p = port(
      """
      package demo;
      public class Outer {
        public class Inner {
          static int seen = 1;
          public int n;
        }
        public static class Far extends Inner {
          public static int own = 2;
          public Far(Outer o) { o.super(); }
        }
      }"""
    )
    p.out
    val shapes = p.emitter.emittedShapes.types
    val fs     = ClassInitTriggerCheck.check(p.after, p.after.units, p.emitter.forcedClassInits, shapes.get)
    assertEquals(clue(fs).map(f => f.issue -> f.owner), List(Issue.SubclassInitPathless -> "demo.Outer$Far"))
    assert(clue(Issue.classification(Issue.SubclassInitPathless)).contains("REFUSAL"))
  }
