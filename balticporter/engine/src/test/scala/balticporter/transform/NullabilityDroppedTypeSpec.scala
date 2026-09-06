package balticporter.transform

import balticporter.core.Substitutions
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, RuleScope}

/** A DROPPED type's members are read literally by the nullability phase: an injection stands at the
  * name with signatures the phase cannot see (CLAUDE.md §4.56, K15). */
class NullabilityDroppedTypeSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |import java.lang.annotation.*;
      |@Retention(RetentionPolicy.CLASS) @interface Null {}
      |class Json {
      |  public @Null <T> T readValue (String name, Class<T> type) { return null; }
      |  public void write (@Null String value) {}
      |}
      |class User {
      |  @Null String held;
      |  String read (Json json) { return json.readValue("x", String.class); }
      |  void push (Json json) { json.write(held); }
      |}
      |""".stripMargin

  private def emitted(drop: Boolean): String =
    val subs  = if drop then Substitutions(dropTypes = Set("com.demo.Json")) else Substitutions.none
    val phase = new NullabilityTransform(annotations = Set("com.demo.Null"),
      target = NullabilityTransform.Target.Named("demo.Nullable"), scope = RuleScope.Everywhere(Set.empty))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java", subs = subs), List(phase))
    new TirEmitter(after, notes = log).emit

  test("a dropped type's `@Null` result is not unwrapped at the caller, and its `@Null` formal is read literally") {
    val out  = emitted(drop = true)
    val user = out.linesIterator.dropWhile(!_.contains("class User")).mkString("\n")
    assert(!user.contains("readValue(\"x\", classOf[java.lang.String]).orNull"), user)
    assert(user.contains("json.write(this.held.orNull)") || user.contains("json.write(held.orNull)"), user)
  }

  test("the same type emitted (not dropped) is retyped and its callers unwrap") {
    val out  = emitted(drop = false)
    val user = out.linesIterator.dropWhile(!_.contains("class User")).mkString("\n")
    assert(user.contains(".orNull"), user)
  }
