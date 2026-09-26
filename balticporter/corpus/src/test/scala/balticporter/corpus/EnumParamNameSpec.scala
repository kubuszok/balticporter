package balticporter.corpus

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.testkit.PortSuite
import balticporter.tir.{ OmissionCheck, Pipeline }

import java.nio.file.{ Files, Path }

/** A java enum constructor PARAMETER is not a member, so one named like a `java.lang.Enum` final (`name`) is emitted as a plain parameter and the enum keeps the scala 3 `enum` shape; `values` is
  * parenless on both enum shapes. Compiled and run: `name()` answers java's constant name, not the parameter.
  */
class EnumParamNameSpec extends PortSuite:

  private val src =
    """package en9;
      |public class Dithered {
      |  public enum DitherAlgorithm {
      |    NONE("None"), WREN("Wren");
      |    public final String legibleName;
      |    public static final DitherAlgorithm[] ALL = values();
      |    DitherAlgorithm(String name) { this.legibleName = name; }
      |    public String toString() { return legibleName; }
      |  }
      |  public enum Bodied {
      |    ONE { public int n() { return 1; } },
      |    TWO { public int n() { return 2; } };
      |    public abstract int n();
      |  }
      |  public static String run() {
      |    DitherAlgorithm w = DitherAlgorithm.valueOf("WREN");
      |    return w.name() + "," + w + "," + w.ordinal() + "," + DitherAlgorithm.values().length + "," +
      |      DitherAlgorithm.ALL.length + "," + Bodied.values().length + "," + Bodied.values()[1].n();
      |  }
      |}
      |""".stripMargin

  private lazy val program = Pipeline.run(SpoonTir.fromSource(src), Nil)
  private lazy val units   = { val em = new TirEmitter(program); program.units.map(em.emitUnit) }
  private lazy val out     = units.mkString("\n")

  test("a parameter named `name` that supersedes no field is a plain parameter, and the enum is a `java.lang.Enum`") {
    assert(clue(out).contains("enum DitherAlgorithm(name: java.lang.String) extends java.lang.Enum[DitherAlgorithm]"))
    assert(!out.contains("var name"))
    assertEquals(OmissionCheck.enumShapeRefusals(program).map(_.owner), List("en9.Dithered$Bodied"))
  }

  test("`values` is parenless at the call site of either shape, and declared so on the sealed one") {
    assert(clue(out).contains("en9.Dithered.DitherAlgorithm.values.length"))
    assert(out.contains("en9.Dithered.Bodied.values.length"))
    assert(out.contains("def values: scala.Array[Bodied]"))
    assert(!out.contains("values()"))
  }

  test("the port compiles and `name()` answers the constant's name, not the parameter") {
    val dir   = Files.createTempDirectory("enum-param-name-src")
    val files = units.zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val outDir = Files.createTempDirectory("enum-param-name-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: outDir.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n$out")
    val loader = new java.net.URLClassLoader(Array(outDir.toUri.toURL), getClass.getClassLoader)
    val got    = loader.loadClass("en9.Dithered").getMethod("run").invoke(null).asInstanceOf[String]
    assertEquals(got, "WREN,Wren,1,2,2,2,2")
  }
