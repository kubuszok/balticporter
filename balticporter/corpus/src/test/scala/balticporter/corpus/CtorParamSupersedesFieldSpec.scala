package balticporter.corpus

import balticporter.testkit.PortSuite

/** A PROMOTED constructor parameter keeps java's name — a parameter name is surface to a named argument — and where a field of that name exists and java's constructor only fills it, the parameter is
  * emitted AS that field.
  */
class CtorParamSupersedesFieldSpec extends PortSuite:

  test("a promoted parameter no member clashes with keeps java's name") {
    val p = port("package p;\nclass C {\n  int f;\n  C(int count) { this.f = count; }\n}\n")
    assertEmits(p, "(count: scala.Int)")
    assertNotEmits(p, "count$p")
  }

  test("a parameter that fills its same-named field is emitted as that field, with its modifiers and comment") {
    val p = port(
      "package p;\npublic class T {\n  /** the cap */\n  private float max;\n  public T(float max) { this.max = max; }\n  public float get() { return max; }\n}\n"
    )
    assertEmits(p, "/** the cap */")
    assertEmits(p, "var max: scala.Float\n)")
    assertNotEmits(p, "max$p")
    assertNotEmits(p, "this.max = max")
  }

  test("a final field filled from its parameter is a `val` parameter") {
    val p = port("package p;\npublic class V {\n  public final String name;\n  public V(String name) { this.name = name; }\n}\n")
    assertEmits(p, "val name: java.lang.String")
    assertNotEmits(p, "name$p")
  }

  test("a field read before java assigns it keeps the parameter apart: the merged field would hold the argument there") {
    val p = port("package p;\npublic class W {\n  int seen;\n  int x;\n  public W(int x) { this.seen = this.x; this.x = x; }\n}\n")
    assertEmits(p, "x$p: scala.Int")
    assertEmits(p, "this.x = x$p")
  }

  test("a superclass constructor that dispatches back into the object refuses: scala fills a class parameter before it runs") {
    val p = port(
      "package p;\nabstract class B {\n  int seen;\n  B() { seen = peek(); }\n  abstract int peek();\n}\n" +
        "class D extends B {\n  int x;\n  D(int x) { this.x = x; }\n  int peek() { return x; }\n}\n"
    )
    assertEmits(p, "x$p: scala.Int")
    assertEmits(p, "this.x = x$p")
  }

  test("a parameter java hands only to `super(...)` keeps its name beside the inherited field it fills") {
    val p = port(
      "package p;\npublic class B {\n  protected int child;\n  public B(int child) { this.child = child; }\n  public int get() { return child; }\n}\n" +
        "public class R extends B {\n  int n;\n  public R(int n, int child) { super(child); this.n = n * 2; }\n  public R(int child) { this(1, child); }\n}\n"
    )
    assertEmits(p, "(n$p: scala.Int, child: scala.Int) extends p.B(child)")
  }

  test("…but one the class body names is renamed: a class parameter captures `this.child`") {
    val p = port(
      "package p;\npublic class B {\n  protected int child;\n  public B(int child) { this.child = child; }\n}\n" +
        "public class S extends B {\n  public S(int child) { super(child); }\n  public int get() { return child; }\n}\n"
    )
    assertEmits(p, "(child$p: scala.Int) extends p.B(child$p)")
  }

  test("a field the constructor computes is a different member: the parameter is renamed, never merged") {
    val p = port("package p;\nclass U {\n  int x;\n  U(int x) { this.x = x * 2; }\n}\n")
    assertEmits(p, "x$p: scala.Int")
    assertEmits(p, "this.x = x$p * 2")
  }
