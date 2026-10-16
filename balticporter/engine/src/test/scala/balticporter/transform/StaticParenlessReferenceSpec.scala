package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.*

/** A java static nilary getter the reference port declares WITHOUT `()` on its companion takes that arity, call sites included; one the reference keeps with `()`, or does not spell, keeps java's. */
class StaticParenlessReferenceSpec extends munit.FunSuite {

  test("a static the reference spells parenless drops its parens, and its callers follow") {
    val src =
      """package com.demo;
        |public class Os {
        |  private static final boolean MAC = true;
        |  public static boolean isMac () { return MAC; }
        |  public static boolean isUnix () { return !MAC; }
        |}
        |class User { boolean f() { return Os.isMac() && !Os.isUnix(); } }
        |""".stripMargin
    val reference =
      """package com.demo
        |object Os { def isMac: Boolean = true; def isUnix(): Boolean = false }
        |class User { def f(): Boolean = Os.isMac && !Os.isUnix() }
        |""".stripMargin
    val dir = java.nio.file.Files.createTempDirectory("staticparenless")
    java.nio.file.Files.writeString(dir.resolve("Os.scala"), reference)
    val decls        = balticporter.verify.ApiParityCheck.parseSurface(List(dir)).toOption.get
    val program      = SpoonTir.fromSource(src, "Os.java")
    val derived      = balticporter.verify.ReferencePolicy.derive(program, decls, program.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val scope        = RunScope.of(program.units.map(_.symbol).toSet, Map.empty, derivedPolicy = derived.policy.resolved(program))
    val phase        = new NullaryArityTransform(derive = true)
    val (after, log) = Pipeline.runTraced(program, List(phase), new PolicyBinder(program, program.members, scope))
    val out          = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains("def isMac: scala.Boolean"), out)
    assert(out.contains("def isUnix(): scala.Boolean"), out)
    assert(out.contains("Os.isMac &&"), out)
    assert(out.contains("Os.isUnix()"), out)
  }
}
