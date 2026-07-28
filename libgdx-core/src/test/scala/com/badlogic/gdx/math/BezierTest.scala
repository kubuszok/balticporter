package com.badlogic.gdx.math

class BezierTest extends munit.FunSuite {
  var `type`: com.badlogic.gdx.math.BezierTest.ImportType = null.asInstanceOf[com.badlogic.gdx.math.BezierTest.ImportType]
  var useSetter: scala.Boolean = false
  private var bezier: com.badlogic.gdx.math.Bezier[com.badlogic.gdx.math.Vector2] = null.asInstanceOf[com.badlogic.gdx.math.Bezier[com.badlogic.gdx.math.Vector2]]
  @org.junit.Before
  def setup(): scala.Unit = {
    this.bezier = null
  }
  def create(points: scala.Array[com.badlogic.gdx.math.Vector2]): scala.Array[com.badlogic.gdx.math.Vector2] = {
    if (this.useSetter) {
      this.bezier = new com.badlogic.gdx.math.Bezier[com.badlogic.gdx.math.Vector2]()
      if (this.`type` == com.badlogic.gdx.math.BezierTest.ImportType.LibGDXArrays) {
        this.bezier.set(new com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.Vector2](points), 0, points.length)
      } else {
        if (this.`type` == com.badlogic.gdx.math.BezierTest.ImportType.JavaArrays) {
          this.bezier.set(points, 0, points.length)
        } else {
          this.bezier.set(points)
        }
      }
    } else {
      if (this.`type` == com.badlogic.gdx.math.BezierTest.ImportType.LibGDXArrays) {
        this.bezier = new com.badlogic.gdx.math.Bezier[com.badlogic.gdx.math.Vector2](new com.badlogic.gdx.utils.Array[com.badlogic.gdx.math.Vector2](points), 0, points.length)
      } else {
        if (this.`type` == com.badlogic.gdx.math.BezierTest.ImportType.JavaArrays) {
          this.bezier = new com.badlogic.gdx.math.Bezier[com.badlogic.gdx.math.Vector2](points, 0, points.length)
        } else {
          this.bezier = new com.badlogic.gdx.math.Bezier[com.badlogic.gdx.math.Vector2](points)
        }
      }
    }
    return points
  }
  test("testLinear2D")({
    val points: scala.Array[com.badlogic.gdx.math.Vector2] = this.create(scala.Array[com.badlogic.gdx.math.Vector2](new com.badlogic.gdx.math.Vector2(0, 0), new com.badlogic.gdx.math.Vector2(1, 1)))
    val len: scala.Float = this.bezier.approxLength(2)
    assertEquals(BezierTest.epsilonApprimations, len, java.lang.Math.sqrt(2))
    val d: com.badlogic.gdx.math.Vector2 = this.bezier.derivativeAt(new com.badlogic.gdx.math.Vector2(), 0.5f)
    assertEquals(BezierTest.epsilon, d.x, 1)
    assertEquals(BezierTest.epsilon, d.y, 1)
    val v: com.badlogic.gdx.math.Vector2 = this.bezier.valueAt(new com.badlogic.gdx.math.Vector2(), 0.5f)
    assertEquals(BezierTest.epsilon, v.x, 0.5f)
    assertEquals(BezierTest.epsilon, v.y, 0.5f)
    val t: scala.Float = this.bezier.approximate(new com.badlogic.gdx.math.Vector2(0.5f, 0.5f))
    assertEquals(BezierTest.epsilonApprimations, t, 0.5f)
    val l: scala.Float = this.bezier.locate(new com.badlogic.gdx.math.Vector2(0.5f, 0.5f))
    assertEquals(BezierTest.epsilon, t, 0.5f)
  })
}
object BezierTest {
  private var epsilon: scala.Float = java.lang.Float.MIN_NORMAL
  private var epsilonApprimations: scala.Float = 1.0E-6f
  @org.junit.runners.Parameterized.Parameters(name = "imported type {0} use setter {1}")
  def parameters(): scala.collection.mutable.Buffer[scala.Array[java.lang.Object]] = {
    val parameters: scala.collection.mutable.Buffer[scala.Array[java.lang.Object]] = new scala.collection.mutable.ArrayBuffer[scala.Array[java.lang.Object]]()
    for (`type` <- com.badlogic.gdx.math.BezierTest.ImportType.values()) {
      parameters += scala.Array[java.lang.Object](`type`, true.asInstanceOf[java.lang.Boolean])
      parameters += scala.Array[java.lang.Object](`type`, false.asInstanceOf[java.lang.Boolean])
    }
    return parameters
  }
  sealed abstract class ImportType {
    def name(): java.lang.String = this.toString()
  }
  object ImportType {
    case object LibGDXArrays extends ImportType
    case object JavaArrays extends ImportType
    case object JavaVarArgs extends ImportType
    def values(): scala.Array[ImportType] = scala.Array(LibGDXArrays, JavaArrays, JavaVarArgs)
    def valueOf(name: java.lang.String): ImportType = name match {
      case "LibGDXArrays" => LibGDXArrays
      case "JavaArrays" => JavaArrays
      case "JavaVarArgs" => JavaVarArgs
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}