package balticporter.frontend.spoon

import balticporter.core.{FrontendConfig, Substitutions}
import balticporter.tir.*

import java.nio.file.Files

/** The [[MemberIndex]] the frontend publishes — and the ONE property that cannot be got anywhere
  * else: '''a DROPPED member is still an answer.'''
  *
  * By the time any phase runs, a member removed by `Substitutions.dropMethods` has no `SymId`, no
  * `Symbol`, no `DefDef` and no row in the symbol table — the frontend filters the executable out
  * BEFORE the method symbol is minted. So a key naming it cannot be resolved against a `Program` at
  * all, and a binder that only asked the program would report every drop that WORKED as a typo. The
  * index is where the answer still exists, and that is why it is stage one.
  *
  * Written against a REAL SOURCE TREE for the reason `DescriptorSpec` states.
  */
class MemberIndexSpec extends munit.FunSuite:

  private def tree(subs: Substitutions)(files: (String, String)*): Program =
    val root = Files.createTempDirectory("memberindex")
    files.foreach { (rel, src) =>
      val p = root.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, src)
    }
    val cfg = FrontendConfig(root, files.map(_._1).toList, Nil)
    SpoonTir.fromTypes(SpoonTir.buildModel(cfg), subs)

  private val source = "com/demo/Reflect.java" ->
    """package com.demo;
      |public class Reflect {
      |  public Reflect() { }
      |  public Reflect(int n, Class<?> c) { }
      |  public Object make(Class<?> c) { return null; }
      |  public Object make(String name) { return null; }
      |  public int keep(int n) { return n; }
      |}""".stripMargin

  test("every executable the frontend WALKED is in the index, dropped or not") {
    val p = tree(Substitutions.none)(source)
    val keys = p.members.all.map(_._1.render).toSet
    assert(clue(keys).contains("com.demo.Reflect#<init>()"))
    assert(keys.contains("com.demo.Reflect#<init>(int,Class)"))
    assert(keys.contains("com.demo.Reflect#make(Class)"))
    assert(keys.contains("com.demo.Reflect#make(String)"))
    assert(keys.contains("com.demo.Reflect#keep(int)"))
    // …and each one that SURVIVED carries the symbol the program declares for it.
    val keep = p.members.exact(MemberKey.of("com.demo.Reflect#keep(int)")).get
    assert(keep.sym.isDefined)
    assertEquals(keep.dropped, false)
    assertEquals(p.symbolOf(keep.sym.get).map(_.name), Some("keep"))
  }

  test("a DROPPED member is in the index with NO symbol — and nothing in the program has its name") {
    val p = tree(Substitutions(dropMethods = Set(
      "com.demo.Reflect#make(Class)",              // one overload of two
      "com.demo.Reflect#<init>(int,Class)",        // a constructor, droppable only precisely
    )))(source)

    val dropped = p.members.exact(MemberKey.of("com.demo.Reflect#make(Class)")).get
    assertEquals(dropped.sym, scala.None)   // THE POINT: nothing to resolve against the program
    assertEquals(dropped.dropped, true)
    val ctor = p.members.exact(MemberKey.of("com.demo.Reflect#<init>(int,Class)")).get
    assertEquals(ctor.dropped, true)

    // …and the program really does not have them: the only `make` left is the `String` overload,
    // and the only constructor left is the no-argument one. This is the assertion that makes the
    // one above mean something — without it the index could be recording a member that is still
    // there and nobody would know.
    val owner = p.symbols.all.find(_.fullName == "com.demo.Reflect").map(_.id).get
    val makes = p.symbols.all.filter(s => s.name == "make" && s.owner == owner).flatMap(_.descriptor.map(_.render))
    assertEquals(makes.toSet, Set("String"))
    val ctors = p.symbols.all.filter(s => s.name == "<init>" && s.owner == owner).flatMap(_.descriptor.map(_.render))
    assertEquals(ctors.toSet, Set(""))
    // the surviving overload is NOT marked dropped — a bare key would have taken both.
    assertEquals(p.members.exact(MemberKey.of("com.demo.Reflect#make(String)")).map(_.dropped), Some(false))
  }

  test("a BARE key drops every overload, and the index says so for each") {
    val p = tree(Substitutions(dropMethods = Set("com.demo.Reflect#make")))(source)
    val both = p.members.overloads("com.demo.Reflect", "make")
    assertEquals(both.size, 2)
    assert(both.forall(_._2.dropped), clue(both.map(x => x._1.render -> x._2.dropped)))
    assert(both.forall(_._2.sym.isEmpty))
  }

  test("the index names the TYPES the frontend walked — the set a member key's owner must be in") {
    val p = tree(Substitutions.none)(source)
    assert(p.members.types.contains("com.demo.Reflect"))
    assert(!p.members.types.contains("java.lang.Object"))
  }

  test("`overloads` is the ambiguity report's input and is stably ordered") {
    val p = tree(Substitutions.none)(source)
    assertEquals(p.members.overloads("com.demo.Reflect", "make").map(_._1.render),
      List("com.demo.Reflect#make(Class)", "com.demo.Reflect#make(String)"))
  }
