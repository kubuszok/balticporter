package balticporter.transform

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.Pipeline

/** A context member path may hop through a nullary METHOD (`graphics.getGL20()`): the getter the
  * port has not turned into a property yet. */
class ContextPathHopSpec extends munit.FunSuite:
  private val java =
    """package com.demo;
      |interface GL20 { void glClear (int mask); }
      |interface Graphics { GL20 getGL20 (); void setGL20 (GL20 gl); }
      |class Gdx {
      |  public static Graphics graphics;
      |  public static GL20 gl;
      |}
      |class User {
      |  void clear () { Gdx.gl.glClear(1); }
      |  void install (GL20 x) { Gdx.gl = x; }
      |}
      |""".stripMargin

  test("a `seg()` hop is emitted as a call on the previous hop") {
    val phase = new GlobalsToImplicitsTransform(holders = List(ContextHolder(
      holder   = "com.demo.Gdx",
      context  = ContextType.Injected("com.demo.Ctx"),
      members  = Map("graphics" -> "graphics", "gl" -> "graphics.getGL20()"),
      attach   = ContextAttach.Class,
      reader   = ContextReader.Summon,
      boundary = ContextBoundary.Refuse)))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(java, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains(".graphics.getGL20().glClear(1)"),
      out.linesIterator.filter(_.contains("glClear")).mkString("\n"))
    // a WRITE through the hop is the bean setter's call
    assert(out.contains(".graphics.setGL20(x)"),
      out.linesIterator.filter(l => l.contains("setGL20") || l.contains("getGL20 =")).mkString("\n"))
  }

  test("a read whose member an earlier phase WRAPPED is unwrapped at the hop") {
    val wrapped =
      """package com.demo;
        |class Box<T> { T orNull() { return null; } }
        |interface GL30 { void glClear (int mask); }
        |interface Graphics { Box<GL30> getGL30 (); }
        |class Gdx {
        |  public static Graphics graphics;
        |  public static GL30 gl30;
        |}
        |class User {
        |  boolean has () { return Gdx.gl30 != null; }
        |  void clear () { Gdx.gl30.glClear(1); }
        |}
        |""".stripMargin
    val phase = new GlobalsToImplicitsTransform(holders = List(ContextHolder(
      holder   = "com.demo.Gdx",
      context  = ContextType.Injected("com.demo.Ctx"),
      members  = Map("graphics" -> "graphics", "gl30" -> "graphics.gl30"),
      attach   = ContextAttach.Class,
      reader   = ContextReader.Summon,
      boundary = ContextBoundary.Refuse)))
    val (after, log) = Pipeline.runTraced(SpoonTir.fromSource(wrapped, "Demo.java"), List(phase))
    val out = new TirEmitter(after, notes = log).emit
    assert(clue(out).contains(".graphics.getGL30().orNull != null"))
    assert(out.contains(".graphics.getGL30().orNull.glClear(1)"))
  }
