package balticporter.corpus

import balticporter.catalog.JS
import balticporter.testkit.PortSuite

/** JS-E20 — `int.class` is statically `Class<Integer>` (JLS 15.8.2), so a `Class<T>` slot takes it
  * where `classOf[Int]` (`Class[Int]`) is refused. */
class PrimitiveClassLiteralSpec extends PortSuite:

  private val java =
    """package p;
      |class T {
      |  static <V> V read(Class<V> type) { return null; }
      |  Object i() { return read(int.class); }
      |  Object f() { return read(float.class); }
      |  Object v() { return void.class; }
      |  Object boxed() { return read(Integer.class); }
      |  Object str() { return read(String.class); }
      |  Object arr() { return read(float[].class); }
      |}
      |""".stripMargin

  private lazy val ported = port(java)

  test("a primitive class literal is emitted at java's static type, keeping the runtime object") {
    assertEmits(ported, "classOf[scala.Int].asInstanceOf[java.lang.Class[java.lang.Integer]]")
    assertEmits(ported, "classOf[scala.Float].asInstanceOf[java.lang.Class[java.lang.Float]]")
    assertEmits(ported, "classOf[scala.Unit].asInstanceOf[java.lang.Class[java.lang.Void]]")
  }

  test("a REFERENCE class literal is untouched — its java type is already `Class<That>`") {
    assertEmits(ported, "classOf[java.lang.Integer]")
    assertEmits(ported, "classOf[java.lang.String]")
    assertNotEmits(ported, "classOf[java.lang.String].asInstanceOf")
    assertNotEmits(ported, "classOf[java.lang.Integer].asInstanceOf")
  }

  test("an ARRAY of a primitive is a reference type in java too — no ascription") {
    assertNotEmits(ported, "scala.Array[scala.Float]].asInstanceOf")
  }

  test("the difference is consulted at the render site, and fires") {
    assertConsults(ported, JS.E(20), fired = true)
  }
