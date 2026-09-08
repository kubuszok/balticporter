package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, PolicyBinder, RuleScope, RunScope}
import balticporter.verify.{ApiParityCheck, ReferencePolicy}

/** `(T) null` at a slot the retyping WRAPPED is the empty wrapper, in a constructor delegation as
  * anywhere else (`super(data, (Array<TextureRegion>) null, integer)`). */
class NullabilityNullCastSpec extends munit.FunSuite:
  private val javaSrc =
    """package com.demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS) @interface Null {}
      |class Regions {}
      |class Font {
      |  public Font (String data, @Null Regions regions, boolean integer) { }
      |  public Font (String data, boolean integer) { this(data, (Regions) null, integer); }
      |}
      |class Sub extends Font {
      |  Sub (String data) { super(data, (Regions) null, true); }
      |}
      |""".stripMargin

  test("a cast null at a wrapped constructor slot becomes the empty wrapper") {
    val phase = new NullabilityTransform(annotations = Set("com.demo.Null"),
      target = NullabilityTransform.Target.Named("demo.Nullable"), scope = RuleScope.Everywhere(Set.empty))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(javaSrc, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(!clue(out).contains("(null: demo.Nullable"), out)
    assert(out.contains("demo.Nullable.empty"), out)
  }

  test("the same through a DERIVED nullable slot (the reference's `Nullable[Regions]` parameter)") {
    val reference =
      """package com.demo
        |import lowlevel.Nullable
        |class Regions
        |class Font(val data: String, regions: Nullable[Regions], val integer: Boolean) {
        |  def this(data: String, integer: Boolean) = this(data, Nullable.empty, integer)
        |}
        |""".stripMargin
    val dir = java.nio.file.Files.createTempDirectory("nullcast")
    java.nio.file.Files.writeString(dir.resolve("Font.scala"), reference)
    val decls   = ApiParityCheck.parseSurface(List(dir)).toOption.get
    val program = SpoonTir.fromSource(javaSrc, "Demo.java")
    val derived = ReferencePolicy.derive(program, decls, program.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    assert(clue(derived.policy.rows.map(_.upstream)).exists(_.contains("regions")))
    val scope = RunScope.of(program.units.map(_.symbol).toSet, Map.empty, derivedPolicy = derived.policy.resolved(program))
    val phase = new NullabilityTransform(annotations = Set.empty,
      target = NullabilityTransform.Target.Named("demo.Nullable"), scope = RuleScope.Everywhere(Set.empty), deriveMembers = true)
    val (after, log) = Pipeline.runTraced(program, List(phase), new PolicyBinder(program, program.members, scope))
    val out = new TirEmitter(after, notes = log).emit
    assert(!clue(out).contains("(null: demo.Nullable"), out)
    assert(out.contains("demo.Nullable.empty"), out)
  }

  test("a derived getter's setter keeps the reference's plain parameter") {
    val src =
      """package com.demo;
        |class GL {}
        |interface Graphics {
        |  GL getGL30 ();
        |  void setGL30 (GL gl);
        |}
        |""".stripMargin
    val reference =
      """package com.demo
        |import lowlevel.Nullable
        |class GL
        |trait Graphics { def gl30: Nullable[GL]; def gl30_=(value: GL): Unit }
        |""".stripMargin
    val dir = java.nio.file.Files.createTempDirectory("setterplain")
    java.nio.file.Files.writeString(dir.resolve("Graphics.scala"), reference)
    val decls   = ApiParityCheck.parseSurface(List(dir)).toOption.get
    val program = SpoonTir.fromSource(src, "Demo.java")
    val derived = ReferencePolicy.derive(program, decls, program.units.map(_.symbol).toSet, Map.empty, Set.empty, Set.empty)
    val scope = RunScope.of(program.units.map(_.symbol).toSet, Map.empty, derivedPolicy = derived.policy.resolved(program))
    val bean  = new BeanPropertyTransform(derive = true)
    val nulls = new NullabilityTransform(annotations = Set.empty,
      target = NullabilityTransform.Target.Named("demo.Nullable"), scope = RuleScope.Everywhere(Set.empty), deriveMembers = true)
    val (after, log) = Pipeline.runTraced(program, List(bean, nulls), new PolicyBinder(program, program.members, scope))
    val out = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains("def gl30: demo.Nullable[com.demo.GL]"))
    assert(out.contains("def gl30_=(gl: com.demo.GL): scala.Unit"), out)
  }
