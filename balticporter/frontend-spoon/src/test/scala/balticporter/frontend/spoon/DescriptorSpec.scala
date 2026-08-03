package balticporter.frontend.spoon

import balticporter.core.FrontendConfig
import balticporter.tir.*

import java.nio.file.{Files, Path}

/** `Symbol.descriptor` as the FRONTEND derives it — over a REAL SOURCE TREE, never through
  * `SpoonTir.fromSource`.
  *
  * That is not a stylistic preference and it is the reason this file exists rather than a handful of
  * cases bolted onto `SpoonTirSpec`. The convenience parse path sets `setNoClasspath(true)`, so an
  * executable reference resolves through the REFERENCE's formals, which a lenient parse erases
  * systematically: `<T> void m(T x)` reads `m(java.lang.Object)`. A descriptor spec written there
  * would assert the erased answer and PASS — for the wrong reason, and it would keep passing after
  * the derivation stopped working. The production frontend runs `setNoClasspath(false)`, and this
  * spec runs the same way.
  */
class DescriptorSpec extends munit.FunSuite:

  private def tree(files: (String, String)*): Program =
    val root = Files.createTempDirectory("descriptor")
    files.foreach { (rel, src) =>
      val p = root.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, src)
    }
    val cfg = FrontendConfig(root, files.map(_._1).toList, Nil)
    SpoonTir.fromTypes(SpoonTir.buildModel(cfg))

  /** every DECLARED member of `owner`, as `name -> rendered descriptor` (absent = no descriptor). */
  private def members(p: Program, owner: String): Map[String, Option[String]] =
    val id = p.symbols.all.find(_.fullName == owner).map(_.id).getOrElse(fail(s"no type $owner"))
    p.symbols.all.filter(_.owner == id).map(s => s.name -> s.descriptor.map(_.render)).toMap

  // -------------------------------------------------------------------------
  // the ordinary cases
  // -------------------------------------------------------------------------

  test("every declared executable carries its SOURCE-LEVEL parameter spelling; a FIELD carries none") {
    val p = tree("com/demo/Widget.java" ->
      """package com.demo;
        |public class Widget {
        |  public int size;
        |  public Widget() { }
        |  public Widget(int size) { this.size = size; }
        |  public String label(int n, String sep) { return sep + n; }
        |  public String label(java.lang.Class<?> c) { return c.getName(); }
        |}""".stripMargin)
    val ms = members(p, "com.demo.Widget")
    // a constructor is `<init>`; both overloads share the name and the symbol table holds both,
    // so read the SET of spellings rather than one.
    val sym = p.symbols.all.filter(s => s.name == "<init>" && p.symbolOf(s.owner).exists(_.fullName == "com.demo.Widget"))
    assertEquals(sym.flatMap(_.descriptor.map(_.render)).toSet, Set("", "int"))
    val labels = p.symbols.all
      .filter(s => s.name == "label" && p.symbolOf(s.owner).exists(_.fullName == "com.demo.Widget"))
      .flatMap(_.descriptor.map(_.render)).toSet
    assertEquals(labels, Set("int,String", "Class"))
    // A FIELD has no descriptor, and that is the COMPLETE answer: `owner#name` is its whole
    // identity. Reporting it as unresolved would produce a finding for every field in the program.
    assertEquals(ms("size"), scala.None)
  }

  test("a GENERIC parameter keeps its SIMPLE name — the spelling is erased source, not a JVM descriptor") {
    val p = tree("com/demo/Box.java" ->
      """package com.demo;
        |import java.util.List;
        |public class Box<T> {
        |  public void put(T item) { }
        |  public void putAll(List<T> items) { }
        |  public <X> void other(X x, Class<X> c) { }
        |}""".stripMargin)
    assertEquals(members(p, "com.demo.Box")("put"), Some("T"))
    assertEquals(members(p, "com.demo.Box")("putAll"), Some("List"))
    assertEquals(members(p, "com.demo.Box")("other"), Some("X,Class"))
  }

  // -------------------------------------------------------------------------
  // D-a — an ARRAY, and a VARARG, which is the same thing
  // -------------------------------------------------------------------------

  test("an ARRAY parameter spells `int[]`, and a VARARG spells the array it is — both from the parser") {
    val p = tree("com/demo/Owner.java" ->
      """package com.demo;
        |public class Owner {
        |  public void copy(int[] src) { }
        |  public void copy2(int[][] src) { }
        |  public void of(String... parts) { }
        |  public void of1(String part) { }
        |}""".stripMargin)
    val ms = members(p, "com.demo.Owner")
    // THE DIVERGENCE, closed at the source. The TIR renders `int[]` as
    // `AppliedType(scala.Array, [Int])`, and a key built from the TYCON's name spelled this member
    // `copy(Array)` — invisible to the manifest key `copy(int[])` that the frontend's own drop test
    // has always matched. One grammar now, and it is Java's.
    assertEquals(ms("copy"), Some("int[]"))
    assertEquals(ms("copy2"), Some("int[][]"))
    // a vararg IS an array reference, so it spells as one — and does NOT collide with the
    // single-argument overload beside it.
    assertEquals(ms("of"), Some("String[]"))
    assertEquals(ms("of1"), Some("String"))
    assertNotEquals(ms("of"), ms("of1"))
  }

  // -------------------------------------------------------------------------
  // D-b — `equals(Object)`, and the ORDER it is read in
  // -------------------------------------------------------------------------

  test("`equals` binds as `Object`, NEVER as `Any` — the descriptor is read BEFORE the retyping") {
    val p = tree("com/demo/Point.java" ->
      """package com.demo;
        |public class Point {
        |  public int x;
        |  @Override public boolean equals(Object o) { return o instanceof Point; }
        |  public boolean same(Object o) { return o == this; }
        |}""".stripMargin)
    val id  = p.symbols.all.find(_.fullName == "com.demo.Point").map(_.id).get
    val eq  = p.symbols.all.find(s => s.name == "equals" && s.owner == id).get

    // The frontend deliberately retypes a 1-argument `equals(Object)`'s parameter to `scala.Any`,
    // because Scala's `Object.equals` takes `Any` and `equals(Object)` would CLASH with it rather
    // than override it. So `info` says `Any` …
    val infoParam = eq.info match
      case TypeRepr.MethodType(List((_, TypeRepr.TypeRef(_, s))), _, _) => p.symbolOf(s).map(_.fullName)
      case other                                                        => fail(s"not a 1-arg method type: $other")
    assertEquals(infoParam, Some("scala.Any"))

    // … and the DESCRIPTOR says `Object`, which is what every manifest in existence writes. This
    // assertion IS the ordering claim: the only way to get `Object` out of this member is to have
    // read it from the parser before the retype, and the assertion above proves the retype happened.
    assertEquals(eq.descriptor.map(_.render), Some("Object"))
    // an ordinary `Object` parameter is untouched by any of this, and must still say `Object`.
    assertEquals(members(p, "com.demo.Point")("same"), Some("Object"))
    // …and the engine-side fallback, read from `info`, is the one place the old answer survives.
    // It is asserted rather than fixed: `ofInfo` may only conclude what its own input says, and its
    // input has already been retyped. Nothing in production consults it for a declared member.
    assertEquals(Descriptor.ofInfo(p, eq).map(_.render), Some("Any"))
  }

  // -------------------------------------------------------------------------
  // the two derivations agree — everywhere except the one place they cannot
  // -------------------------------------------------------------------------

  test("Descriptor-from-Spoon and Descriptor-from-info agree on EVERY executable — `equals` excepted") {
    val p = tree("com/demo/Wide.java" ->
      """package com.demo;
        |import java.util.List;
        |public class Wide<T> {
        |  public Wide(int a, String b) { }
        |  public void prims(int a, long b, short c, byte d, char e, boolean f, float g, double h) { }
        |  public void arrays(int[] a, String[][] b, T[] c) { }
        |  public void generics(List<String> a, Class<?> b, T c) { }
        |  public void varargs(String first, Object... rest) { }
        |  public void none() { }
        |  @Override public boolean equals(Object o) { return false; }
        |}""".stripMargin)
    val owner = p.symbols.all.find(_.fullName == "com.demo.Wide").map(_.id).get
    val execs = p.symbols.all.filter(s => s.owner == owner && s.descriptor.isDefined).toList
    assert(clue(execs.size) >= 7)
    val disagreements = execs.collect {
      case s if Descriptor.ofInfo(p, s).map(_.render) != s.descriptor.map(_.render) =>
        (s.name, s.descriptor.map(_.render), Descriptor.ofInfo(p, s).map(_.render))
    }
    // `equals` is the ONE divergence and it is DECLARED here rather than reconciled: the design
    // rejects deriving the descriptor from `info`, and this is the measurement that says why.
    assertEquals(disagreements, List(("equals", Some("Object"), Some("Any"))))
  }

  // -------------------------------------------------------------------------
  // the boundary the design cannot remove, asserted so it stays visible
  // -------------------------------------------------------------------------

  test("an EXTERNAL member the parser RESOLVED carries a descriptor; the JDK-typed owner is external") {
    val p = tree("com/demo/Caller.java" ->
      """package com.demo;
        |public class Caller {
        |  public String go(String s) { return s.substring(1, 2); }
        |}""".stripMargin)
    // `java.lang.String#substring(int,int)` is not declared by this program; the frontend interns it
    // on first reference, and with a full classpath the declaration resolves — so the descriptor is
    // available and is the parser's, exactly as for a declared member.
    val sub = p.symbols.all.filter(_.name == "substring").toList
    val spellings = sub.map(s => s.descriptor.map(_.render))
    assert(clue(spellings).contains(Some("int,int")))
  }
