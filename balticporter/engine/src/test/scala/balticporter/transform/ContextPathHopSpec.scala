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
