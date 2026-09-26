package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.NullabilityTransform
import balticporter.transform.NullabilityTransform.Target

import java.nio.file.{ Files, Path }

/** A DEREFERENCE of a composite whose value is a wrapped field — an assignment used as a value — unwraps the value the composite yields, with `.get`: java dereferences it directly and throws on null.
  * A base declares the annotated field; the dependent's unit reads through it.
  */
class NullabilityCompositeDerefSpec extends PortSuite:

  private val W = "demo.Nullable"

  private val baseUnit =
    """package base;
      |import java.lang.annotation.*;
      |@Target({ElementType.FIELD}) @interface Null {}
      |public class Floats {
      |  public float[] items;
      |  public Floats(int n) { items = new float[n]; }
      |  public float[] ensure(int n) { return items; }
      |}
      |public class Palette {
      |  @Null public Floats cur;
      |}
      |""".stripMargin

  private val dependentUnit =
    """package dep;
      |import base.*;
      |public class Dither {
      |  public static String run(Palette p) {
      |    float[] a;
      |    if (p.cur == null) a = (p.cur = new Floats(3)).items; else a = p.cur.ensure(3);
      |    int n = (p.cur = new Floats(4)).items.length;
      |    boolean set = (p.cur = new Floats(5)) != null;
      |    return a.length + ":" + n + ":" + set + ":" + p.cur.items.length;
      |  }
      |}
      |""".stripMargin

  private val nullable =
    """package demo
      |opaque type Nullable[A] = A | Null
      |object Nullable {
      |  def apply[A](a: A): Nullable[A] = a
      |  def empty[A]: Nullable[A] = null
      |  extension [A](n: Nullable[A]) {
      |    def get: A = if (n == null) throw new NullPointerException("empty") else n.asInstanceOf[A]
      |    def orNull: A = n.asInstanceOf[A]
      |    def isEmpty: Boolean = n == null
      |  }
      |}
      |""".stripMargin

  private val driver =
    """package dep
      |object Drive { def run(): String = Dither.run(new base.Palette()) }
      |""".stripMargin

  private lazy val ported =
    portAll(List("base/Palette.java" -> baseUnit, "dep/Dither.java" -> dependentUnit), new NullabilityTransform(Set("base.Null"), Target.Named(W)))

  private def compileAndRun(): String =
    val dir   = Files.createTempDirectory("nullable-composite-src")
    val files = (ported.after.units.map(ported.emitter.emitUnit) ++ List(nullable, driver)).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("nullable-composite-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    val loader = new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    loader.loadClass("dep.Drive").getMethod("run").invoke(null).asInstanceOf[String]

  test("an assignment's value is dereferenced through `.get` at the field it yields") {
    assertEmits(ported, "p.cur.get\n    }.items")
    assertNotEmits(ported, "p.cur\n    }.items")
  }

  test("an assignment's value tested against null reads the wrapper's own emptiness") {
    assertEmits(ported, "p.cur.isEmpty")
  }

  test("the port compiles and runs as java did") {
    assertEquals(compileAndRun(), "3:4:true:5")
  }
