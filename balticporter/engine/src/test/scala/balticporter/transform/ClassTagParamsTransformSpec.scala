package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** `ClassTagParamsTransform`: a `Class<T>` parameter becomes a `ClassTag[T]` context clause, the
  * body reads the class off the tag, an owned call passing `X.class` names `[X]` (DESIGN.md §8.30). */
class ClassTagParamsTransformSpec extends munit.FunSuite:
  private val javaSrc =
    """package com.demo;
      |import java.util.HashMap;
      |import java.util.function.Supplier;
      |class Foo {}
      |class PM {
      |  private final HashMap<Class<?>, Object> pools = new HashMap<>();
      |  public <T> void add (Class<T> c, Supplier<T> s) { pools.put(c, s); }
      |  public <T> T get (Class<T> c) { return (T)pools.get(c); }
      |  public <T> T obtain (Class<T> c) { return (T)pools.get(c); }
      |}
      |class User {
      |  void f (PM pm, Class<Foo> k) {
      |    pm.add(Foo.class, () -> new Foo());
      |    pm.get(k);
      |    Foo o = pm.obtain(Foo.class);
      |  }
      |}
      |""".stripMargin

  test("the parameter becomes a clause, the body reads the tag, a literal caller names the type argument") {
    val phase = new ClassTagParamsTransform(members = Set("com.demo.PM#add", "com.demo.PM#get", "com.demo.PM#obtain"))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    val pm = out.linesIterator.dropWhile(!_.contains("class PM")).takeWhile(!_.contains("class User")).mkString("\n")
    assert(clue(pm).contains("def add[T <: java.lang.Object](s: java.util.function.Supplier[T])(using scala.reflect.ClassTag[T]): scala.Unit"))
    assert(pm.contains("val c: java.lang.Class[T] = scala.Predef.summon[scala.reflect.ClassTag[T]].runtimeClass.asInstanceOf[java.lang.Class[T]]"))
    // `get` is called with a Class VALUE: refused, counted, left as java wrote it
    assert(pm.contains("def get[T <: java.lang.Object](c: java.lang.Class[T]): T"), pm)
    assert(phase.policyReport.findings.exists(_.detail.contains("passes a `Class` VALUE")), phase.policyReport.findings.mkString("\n"))
    val user = out.linesIterator.dropWhile(!_.contains("class User")).mkString("\n")
    assert(clue(user).contains("pm.add[com.demo.Foo](() => new com.demo.Foo())"))
    assert(user.contains("pm.get(k)"))
    // the only value clause went: the call is the type application alone, no `()`
    assert(pm.contains("def obtain[T <: java.lang.Object](using scala.reflect.ClassTag[T]): T"), pm)
    assert(user.contains("pm.obtain[com.demo.Foo]") && !user.contains("pm.obtain[com.demo.Foo]()"), user)
  }

  test("the fingerprint is the switch and the members, empty when neither is set") {
    assertEquals(new ClassTagParamsTransform().surfaceFingerprint, "")
    assert(new ClassTagParamsTransform(derive = true).surfaceFingerprint.contains("derive=reference"))
    assert(new ClassTagParamsTransform(members = Set("a.B#m")).surfaceFingerprint.contains("a.B#m"))
  }
