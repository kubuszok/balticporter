package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

import java.nio.file.{ Files, Path }

/** `boxed == (int) other` is NUMERIC equality (JLS 15.21.1): the cast makes one operand primitive. Read off the operand's declared type the comparison was emitted `eq`, which compared two
  * references (right only inside the `Integer` cache) and which scala 3.9 refuses to compile. Compiled under `-Werror` and run outside the cache, against javac's answer.
  */
class BoxedCastEqualitySpec extends munit.FunSuite:

  private val src =
    """package demo;
      |public class Eq {
      |  public static String run() {
      |    Integer a = 1000;
      |    Integer b = 1000;
      |    boolean num = a == (int) b;
      |    boolean ref = a == b;
      |    return num + "," + ref;
      |  }
      |}
      |""".stripMargin

  private lazy val emitted: List[String] =
    val p  = Pipeline.run(SpoonTir.fromSource(src), Nil)
    val em = new TirEmitter(p)
    p.units.map(em.emitUnit)

  private lazy val compiled: Path =
    val dir   = Files.createTempDirectory("boxed-cast-eq-src")
    val files = emitted.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("boxed-cast-eq-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val args   = "-Werror" :: "-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files
    val rep    = dotty.tools.dotc.Main.process(args.toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${emitted.mkString("\n")}")
    out

  test("a cast to a primitive on one side keeps java's numeric comparison; two boxes stay a reference test") {
    val loader = new java.net.URLClassLoader(Array(compiled.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("demo.Eq").getMethod("run").invoke(null).asInstanceOf[String]
    // javac's own output for the same source
    assertEquals(got, "true,false")
  }
