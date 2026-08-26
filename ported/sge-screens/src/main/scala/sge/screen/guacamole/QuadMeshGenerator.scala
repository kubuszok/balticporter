/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original file: de/damios/guacamole/gdx/graphics/QuadMeshGenerator.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

/** Builds the screen-filling quad every shader transition renders onto.
  *
  * The vertex layout is `Position` + `TexCoords(0)`, four vertices, in the order
  * bottom-left / top-left / bottom-right / top-right — which is a TRIANGLE STRIP order and is why
  * `ShaderTransition` renders with `GL_TRIANGLE_STRIP`. Reordering the four would compile and draw
  * nothing recognisable, so the array is reproduced index for index rather than rewritten into
  * something more Scala-shaped.
  *
  * `flipY` selects which of the two v coordinates is 1: a framebuffer texture is stored
  * bottom-up, so a transition sampling one without the flip renders every screen upside down.
  *
  * ==Why all three take `(using sge.Sge)`==
  * `sge.graphics.Mesh` is one of the classes the base port threads (DESIGN.md §8.4), so
  * constructing one takes the context. This is hand-written `src/` Scala the frontend never sees,
  * so the clause is added here by hand and carried down the three-deep delegation. The one caller,
  * `ShaderTransition.resize`, is an instance method of a threaded class, so the context is already
  * in scope there.
  */
object QuadMeshGenerator {

  def createFullScreenQuad(screenWidth: Int, screenHeight: Int, flipY: Boolean)(using sge.Sge): sge.graphics.Mesh = {
    createQuad(0, 0, screenWidth.toFloat, screenHeight.toFloat, flipY)

  }
  def createQuad(x: Float, y: Float, width: Float, height: Float, flipY: Boolean)(using sge.Sge): sge.graphics.Mesh = {
    createQuadFromCoordinates(x, y, x + width, y + height, flipY)

  /** Coordinate system: y-up. `(x1, y1)` is bottom-left, `(x2, y2)` top-right. */
  }
  def createQuadFromCoordinates(x1: Float, y1: Float, x2: Float, y2: Float, flipY: Boolean)(using sge.Sge): sge.graphics.Mesh = {
    val verts = Array[Float](
      // bottom left
      x1, y1, 0, 0, if (flipY) 0 else 1,
      // top left
      x1, y2, 0, 0, if (flipY) 1 else 0,
      // bottom right
      x2, y1, 0, 1, if (flipY) 0 else 1,
      // top right
      x2, y2, 0, 1, if (flipY) 1 else 0,
    )

    // The attribute list is an ARRAY, not a Scala vararg: the engine emits a Java `T...`
    // parameter as `Array[T]` (`TirEmitter.param`), so libGDX's own `Mesh(boolean, int, int,
    // VertexAttribute...)` is `Array[VertexAttribute]` in the port this compiles against.
    val mesh = new sge.graphics.Mesh(true, 4, 0,
      Array(sge.graphics.VertexAttribute.Position(), sge.graphics.VertexAttribute.TexCoords(0)))
    mesh.setVertices(verts)
    mesh

  }
}