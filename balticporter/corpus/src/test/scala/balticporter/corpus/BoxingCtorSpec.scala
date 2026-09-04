package balticporter.corpus

import balticporter.testkit.PortSuite

/** JS-E19 — a deprecated boxing constructor becomes the wrapper's `valueOf`. */
class BoxingCtorSpec extends PortSuite:

  private val java =
    """package p;
      |class T {
      |  Object d() { return new Double(1.5); }
      |  Object l(long v) { return new Long(v); }
      |  Object s() { return new Integer("7"); }
      |  Object o() { return new Object(); }
      |}
      |""".stripMargin

  private lazy val ported = port(java)

  test("each wrapper's one-argument constructor is its `valueOf`, fully qualified") {
    assertEmits(ported, "java.lang.Double.valueOf(1.5)")
    assertEmits(ported, "java.lang.Long.valueOf(v)")
    assertEmits(ported, "java.lang.Integer.valueOf(\"7\")")
    assertNotEmits(ported, "new java.lang.Double")
  }

  test("a non-wrapper `new` is untouched") {
    assertEmits(ported, "new java.lang.Object()")
  }
