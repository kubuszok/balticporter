package com.badlogic.gdx.math

class Bresenham2 {
  private final val points: com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.GridPoint2] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.GridPoint2]()
  private final val pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.math.GridPoint2] = new com.badlogic.gdx.utils.Pool[com.badlogic.gdx.math.GridPoint2]()
  def line(start: com.badlogic.gdx.math.GridPoint2, `end`: com.badlogic.gdx.math.GridPoint2): com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.GridPoint2] = {
    return this.line(start.x, start.y, `end`.x, `end`.y)
  }
  def line(startX: scala.Int, startY: scala.Int, endX: scala.Int, endY: scala.Int): com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.GridPoint2] = {
    this.pool.freeAll(this.points)
    this.points.clear()
    return this.line(startX, startY, endX, endY, this.pool, this.points)
  }
  def line(startX$arg: scala.Int, startY$arg: scala.Int, endX: scala.Int, endY: scala.Int, pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.math.GridPoint2], output: com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.GridPoint2]): com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.GridPoint2] = {
    var startX: scala.Int = startX$arg
    var startY: scala.Int = startY$arg
    val w: scala.Int = endX - startX
    val h: scala.Int = endY - startY
    var dx1: scala.Int = 0
    var dy1: scala.Int = 0
    var dx2: scala.Int = 0
    var dy2: scala.Int = 0
    if (w < 0) {
      dx1 = -1
      dx2 = -1
    } else {
      if (w > 0) {
        dx1 = 1
        dx2 = 1
      } else ()
    }
    if (h < 0) {
      dy1 = -1
    } else {
      if (h > 0) {
        dy1 = 1
      } else ()
    }
    var longest: scala.Int = java.lang.Math.abs(w)
    var shortest: scala.Int = java.lang.Math.abs(h)
    if (longest < shortest) {
      longest = java.lang.Math.abs(h)
      shortest = java.lang.Math.abs(w)
      if (h < 0) {
        dy2 = -1
      } else {
        if (h > 0) {
          dy2 = 1
        } else ()
      }
      dx2 = 0
    } else ()
    val shortest2: scala.Int = shortest << 1
    val longest2: scala.Int = longest << 1
    var numerator: scala.Int = 0;
    { var i: scala.Int = 0; while (i <= longest) { {
      val point: com.badlogic.gdx.math.GridPoint2 = pool.obtain()
      point.set(startX, startY)
      output.add(point)
      numerator = numerator + shortest2
      if (numerator > longest) {
        numerator = numerator - longest2
        startX = startX + dx1
        startY = startY + dy1
      } else {
        startX = startX + dx2
        startY = startY + dy2
      }
    }; i = i + 1 } }
    return output
  }
}