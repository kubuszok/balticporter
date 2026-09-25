package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `TypeClassParamsTransform`: a `Class<T>` parameter used to construct a `T` becomes a port-supplied type class clause, and every construction reads the instance. */
class TypeClassParamsTransformSpec extends munit.FunSuite:
  import TypeClassParamsTransform.*

  private val javaSrc =
    """package com.demo;
      |import java.util.HashMap;
      |class Foo {}
      |class Refl { static <T> T make (Class<T> c) throws Exception { return c.getDeclaredConstructor().newInstance(); } }
      |class PM {
      |  private final HashMap<Class<?>, Object> pools = new HashMap<>();
      |  public <T> T create (Class<T> c) {
      |    try { return c.newInstance(); } catch (InstantiationException | IllegalAccessException e) { return null; }
      |  }
      |  public <T> T obtain (Class<T> c) {
      |    Object cached = pools.get(c);
      |    if (cached == null) { cached = create(c); pools.put(c, cached); }
      |    return (T) cached;
      |  }
      |  public <T> T viaCtor (Class<T> c) throws Exception { return c.getConstructor().newInstance(); }
      |  public <T> T viaUtil (Class<T> c) throws Exception { return Refl.make(c); }
      |  public <T> T withArg (Class<T> c) throws Exception { return c.getConstructor(int.class).newInstance(1); }
      |  public <T> T guarded (Class<T> c) {
      |    try { return c.newInstance(); } catch (Exception e) { return null; }
      |  }
      |}
      |class User {
      |  void f (PM pm, Class<Foo> k) throws Exception {
      |    Foo a = pm.create(Foo.class);
      |    Foo b = pm.obtain(Foo.class);
      |    Foo c = pm.viaCtor(k);
      |    Foo d = pm.viaUtil(Foo.class);
      |    Foo e = pm.withArg(Foo.class);
      |    Foo g = pm.guarded(Foo.class);
      |  }
      |}
      |""".stripMargin

  private def port(rules: Rule*): (String, TypeClassParamsTransform) =
    val phase        = new TypeClassParamsTransform(rules.toList)
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Demo.java"), List(phase))
    (new TirEmitter(after, notes = log).emit, phase)

  /** from the first line containing `from` up to (not including) the next line containing `to`. */
  private def section(out: String, from: String, to: String): String =
    out.linesIterator.dropWhile(!_.contains(from)).toList match
      case head :: rest => (head :: rest.takeWhile(l => to.isEmpty || !l.contains(to))).mkString("\n")
      case Nil          => ""
  private val all =
    Set(
      "com.demo.PM#create",
      "com.demo.PM#obtain",
      "com.demo.PM#viaCtor",
      "com.demo.PM#viaUtil",
      "com.demo.PM#withArg",
      "com.demo.PM#guarded"
    )
  private val handles = Set("java.lang.InstantiationException", "java.lang.IllegalAccessException")

  test(
    "type-argument spelling: the parameter becomes a type class clause, every construction reads the instance, a literal caller names `[X]`"
  ) {
    val (out, phase) = port(Rule(all, "x.Factory", instantiators = Set("com.demo.Refl#make"), classValue = ClassValue.Tag, handles = handles))
    val pm           = section(out, "class PM", "class User")
    assert(clue(pm).contains("def create[T <: java.lang.Object](using x.Factory[T], scala.reflect.ClassTag[T]): T"))
    // the JDK's own construction; the `try` whose handlers are all declared is its body
    assert(pm.contains("return scala.Predef.summon[x.Factory[T]].create().asInstanceOf[T]"), pm)
    assert(!section(pm, "def create", "def obtain").contains("catch"), pm)
    // a read of the class beyond construction is answered by the tag; the delegating call names `[T]`
    assert(
      pm.contains(
        "val c: java.lang.Class[T] = scala.Predef.summon[scala.reflect.ClassTag[T]].runtimeClass.asInstanceOf[java.lang.Class[T]]"
      ),
      pm
    )
    assert(pm.contains("cached = this.create[T]"), pm)
    // a named instantiator is a construction too
    assert(section(pm, "def viaUtil", "def withArg").contains("summon[x.Factory[T]].create()"), pm)
    val user = section(out, "class User", "")
    assert(clue(user).contains("pm.create[com.demo.Foo]") && !user.contains("pm.create[com.demo.Foo]()"))
    assert(user.contains("pm.obtain[com.demo.Foo]"), user)
  }

  test(
    "refusals are counted and leave java's spelling: a `Class` value, a constructor with arguments, a handler the rule does not declare"
  ) {
    val (out, phase) = port(Rule(all, "x.Factory", instantiators = Set("com.demo.Refl#make"), classValue = ClassValue.Tag, handles = handles))
    val pm           = section(out, "class PM", "class User")
    val found        = phase.policyReport.findings.map(f => s"${f.key}: ${f.detail}")
    assert(pm.contains("def viaCtor[T <: java.lang.Object](c: java.lang.Class[T]): T"), pm)
    assert(found.exists(f => f.startsWith("com.demo.PM#viaCtor") && f.contains("passes a `Class` VALUE")), found.mkString("\n"))
    assert(pm.contains("def withArg[T <: java.lang.Object](c: java.lang.Class[T]): T"), pm)
    assert(found.exists(f => f.startsWith("com.demo.PM#withArg") && f.contains("reflectively")), found.mkString("\n"))
    // `catch (Exception e)` is not a declared failure of the construction: converted, the try kept, counted
    assert(section(pm, "def guarded", "}").contains("summon[x.Factory[T]].create()"), pm)
    assert(section(pm, "def guarded", "def ").contains("catch"), pm)
    assert(found.exists(_.contains("`handles` does not describe")), found.mkString("\n"))
    val user = section(out, "class User", "")
    assert(user.contains("pm.viaCtor(k)") && user.contains("pm.withArg(classOf[com.demo.Foo])"), user)
  }

  test(
    "keep-parameter spelling: the `Class` parameter stays beside the clause, callers read as java wrote them, a concrete `Class` value is accepted"
  ) {
    val (out, phase) = port(
      Rule(
        all - "com.demo.PM#withArg",
        "x.Factory",
        instantiators = Set("com.demo.Refl#make"),
        spelling = Spelling.KeepParameter,
        handles = handles
      )
    )
    val pm = section(out, "class PM", "class User")
    assert(clue(pm).contains("def create[T <: java.lang.Object](c: java.lang.Class[T])(using x.Factory[T]): T"))
    assert(pm.contains("def obtain[T <: java.lang.Object](c: java.lang.Class[T])(using x.Factory[T]): T"), pm)
    assert(pm.contains("cached = this.create(c)"), pm)
    assert(pm.contains("def viaCtor[T <: java.lang.Object](c: java.lang.Class[T])(using x.Factory[T]): T"), pm)
    assert(!pm.contains("ClassTag"), pm)
    val user = section(out, "class User", "")
    assert(clue(user).contains("pm.create(classOf[com.demo.Foo])") && user.contains("pm.viaCtor(k)"))
    assert(!phase.policyReport.findings.exists(_.detail.contains("VALUE")), phase.policyReport.findings.mkString("\n"))
  }

  test(
    "classValue = refuse: a member reading its class as a key refuses, and so does the member it delegates to, whose caller now passes a value"
  ) {
    val (out, phase) = port(Rule(Set("com.demo.PM#create", "com.demo.PM#obtain"), "x.Factory", handles = handles))
    val pm           = section(out, "class PM", "class User")
    assert(pm.contains("def obtain[T <: java.lang.Object](c: java.lang.Class[T]): T"), pm)
    assert(pm.contains("def create[T <: java.lang.Object](c: java.lang.Class[T]): T"), pm)
    val found = phase.policyReport.findings.map(f => s"${f.key}: ${f.detail}")
    assert(found.exists(f => f.startsWith("com.demo.PM#obtain") && f.contains("declares no `classValue`")), found.mkString("\n"))
    assert(found.exists(f => f.startsWith("com.demo.PM#create") && f.contains("passes a `Class` VALUE")), found.mkString("\n"))
  }

  test("classValue = member:<name> reads the class off the type class, with no second clause") {
    val (out, _) = port(
      Rule(
        Set("com.demo.PM#create", "com.demo.PM#obtain"),
        "x.Factory",
        classValue = ClassValue.Member("runtimeClass"),
        handles = handles
      )
    )
    val pm = section(out, "class PM", "class User")
    assert(clue(pm).contains("def obtain[T <: java.lang.Object](using x.Factory[T]): T"))
    assert(pm.contains("val c: java.lang.Class[T] = scala.Predef.summon[x.Factory[T]].runtimeClass"), pm)
  }

  test("an empty rule list, and a rule naming no member, emit byte-identical output") {
    val (bare, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Demo.java"), Nil)
    val baseline    = new TirEmitter(bare, notes = log).emit
    assertEquals(port()._1, baseline)
    assertEquals(port(Rule(Set.empty, "x.Factory"))._1, baseline)
  }

  test("the fingerprint is empty with no rules, carries every set field, and a member two rules convert refuses the merge") {
    assertEquals(new TypeClassParamsTransform().surfaceFingerprint, "")
    val r  = Rule(Set("a.B#m"), "x.F")
    val fp = new TypeClassParamsTransform(List(r)).surfaceFingerprint
    assert(fp.contains("x.F.create") && fp.contains("a.B#m") && !fp.contains("spelling") && !fp.contains("classValue"), fp)
    assert(
      new TypeClassParamsTransform(List(r.copy(spelling = Spelling.KeepParameter))).surfaceFingerprint.contains("spelling=keep-parameter")
    )
    assert(
      new TypeClassParamsTransform(List(r.copy(classValue = ClassValue.Tag))).surfaceFingerprint.contains("classValue=class-tag")
    )
    assert(new TypeClassParamsTransform(List(r)).mergedWith(new TypeClassParamsTransform(List(r.copy(typeClass = "y.G")))).isLeft)
    val merged = new TypeClassParamsTransform(List(r)).mergedWith(new TypeClassParamsTransform(List(Rule(Set("a.B#n"), "x.F"))))
    assertEquals(merged.map(_.added), Right(Set("a.B")))
  }
