package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.NullabilityTransform
import balticporter.transform.NullabilityTransform.Target

import java.nio.file.{ Files, Path }

/** A compound assignment to a wrapped field reads the value, applies the operator and writes the result back wrapped, as java does: a string concatenation reads `null` as `"null"`, a numeric one
  * unboxes (an NPE on null).
  */
class NullabilityCompoundAssignSpec extends PortSuite:

  private val W = "demo.Nullable"

  private val unit =
    """package base;
      |import java.lang.annotation.*;
      |@Target({ElementType.FIELD}) @interface Null {}
      |public class Paths {
      |  @Null public String root;
      |  @Null public Integer count;
      |  public String run() {
      |    root = "a";
      |    root += "/";
      |    String first = root;
      |    root = null;
      |    root += "/";
      |    count = 2;
      |    count *= 3;
      |    return first + ":" + root + ":" + count;
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

  private lazy val ported = portAll(List("base/Paths.java" -> unit), new NullabilityTransform(Set("base.Null"), Target.Named(W)))

  test("a compound assignment to a wrapped field opens the wrapper, applies the operator and wraps the result") {
    val dir   = Files.createTempDirectory("nullable-compound-src")
    val files = (ported.after.units.map(ported.emitter.emitUnit) ++ List(nullable)).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("nullable-compound-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    val loader = new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader)
    val cls    = loader.loadClass("base.Paths")
    assertEquals(cls.getMethod("run").invoke(cls.getConstructor().newInstance()).asInstanceOf[String], "a/:null/:6")
  }
