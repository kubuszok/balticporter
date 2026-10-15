package balticporter.corpus

import balticporter.testkit.PortSuite
import balticporter.transform.NullabilityTransform
import balticporter.transform.NullabilityTransform.Target

import java.nio.file.{ Files, Path }

/** A `switch` on a wrapped value switches on the VALUE, as java's does: a boxed primitive is unboxed (an NPE on null) and a null reference selector throws, never reaching a wrapper-typed `match`. */
class NullabilitySwitchSelectorSpec extends PortSuite:

  private val unit =
    """package base;
      |import java.lang.annotation.*;
      |@Target({ElementType.FIELD, ElementType.PARAMETER}) @interface Null {}
      |public class Sel {
      |  public static int pick(@Null Integer m) {
      |    switch (m) { case 0: return 1; case 1: return 2; }
      |    return 3;
      |  }
      |  public static int name(@Null String s) {
      |    switch (s) { case "a": return 1; default: return 2; }
      |  }
      |  @Null public Integer camera;
      |  public static String store(@Null Integer c) {
      |    Sel s = new Sel(); s.camera = c;
      |    Box<Integer> b = new Box<Integer>(); b.put(s.camera);
      |    return String.valueOf(b.value);
      |  }
      |}
      |class Box<V> {
      |  @Null V value;
      |  void put(@Null V v) { value = v; }
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

  private lazy val ported = portAll(List("base/Sel.java" -> unit), new NullabilityTransform(Set("base.Null"), Target.Named("demo.Nullable")))

  private lazy val cls: Class[?] =
    val dir   = Files.createTempDirectory("nullable-switch-src")
    val files = (ported.after.units.map(ported.emitter.emitUnit) ++ List(nullable)).zipWithIndex.map { (text, n) =>
      val f = dir.resolve(s"U$n.scala"); Files.writeString(f, text); f.toString
    }
    val out    = Files.createTempDirectory("nullable-switch-out")
    val stdlib = List(classOf[scala.Option[?]], classOf[scala.deriving.Mirror]).map(c => Path.of(c.getProtectionDomain.getCodeSource.getLocation.toURI).toString).distinct
    val rep    = dotty.tools.dotc.Main.process(("-classpath" :: stdlib.mkString(java.io.File.pathSeparator) :: "-d" :: out.toString :: files).toArray)
    assert(!rep.hasErrors, s"the emission does not compile:\n${rep.allErrors.map(_.msg.message).mkString("\n")}\n${ported.out}")
    new java.net.URLClassLoader(Array(out.toUri.toURL), getClass.getClassLoader).loadClass("base.Sel")

  private def call(m: String, cl: Class[?], arg: AnyRef): Any =
    try cls.getMethod(m, cl).invoke(null, arg)
    catch { case e: java.lang.reflect.InvocationTargetException => e.getCause }

  test("a boxed primitive selector switches on its value, and a null one throws as java's unboxing does") {
    assertEquals(call("pick", classOf[Object], Integer.valueOf(1)), 2)
    assertEquals(call("pick", classOf[Object], Integer.valueOf(7)), 3)
    assert(clue(call("pick", classOf[Object], null)).isInstanceOf[NullPointerException])
  }

  test(
    "a wrapped primitive passed at a wrapped type-variable slot the receiver fills with the BOX keeps its value and its null"
  ) {
    assert(
      clue(ported.after.units.map(ported.emitter.emitUnit).mkString).contains("b.put(s.camera.asInstanceOf[demo.Nullable[java.lang.Integer]])")
    )
    assertEquals(call("store", classOf[Object], Integer.valueOf(4)), "4")
    assertEquals(call("store", classOf[Object], null), "null")
  }

  test("a reference selector switches on its value, and a null one throws") {
    assertEquals(call("name", classOf[String], "a"), 1)
    assertEquals(call("name", classOf[String], "b"), 2)
    assert(clue(call("name", classOf[String], null)).isInstanceOf[NullPointerException])
  }
