package sge
package screen

import sge.graphics.g2d.TextureRegion
import sge.math.Interpolation
import sge.screen.transition.TimedTransition

class TimedTransitionSuite extends munit.FunSuite {

  class TestTimedTransition(
    dur:    Float,
    interp: Interpolation = null
  ) extends TimedTransition(dur, interp) {
    var lastProgress: Float = -1f
    var renderCount:  Int   = 0

    override def render(delta: Float, lastScreen: TextureRegion, currScreen: TextureRegion, progress: Float): Unit = {
      lastProgress = progress
      renderCount += 1
    }

    override def resize(width: Int, height: Int): Unit = ()
    override def close():                         Unit = ()
  }

  private val dummyRegion: TextureRegion = new TextureRegion()

  test("progress increases with delta") {
    val t = new TestTimedTransition(1.0f)
    t.show()
    t.render(0.25f, dummyRegion, dummyRegion)
    assert(t.lastProgress > 0f, s"progress should be > 0, was ${t.lastProgress}")
    val first = t.lastProgress
    t.render(0.25f, dummyRegion, dummyRegion)
    assert(t.lastProgress > first, s"progress should increase, was ${t.lastProgress}")
  }

  test("progress clamps at 1.0") {
    val t = new TestTimedTransition(0.5f)
    t.show()
    t.render(1.0f, dummyRegion, dummyRegion)
    assertEqualsFloat(t.lastProgress, 1.0f, 0.001f)
  }

  test("isDone returns true when timePassed >= duration") {
    val t = new TestTimedTransition(0.5f)
    t.show()
    assert(!t.done)
    t.render(0.3f, dummyRegion, dummyRegion)
    assert(!t.done)
    t.render(0.3f, dummyRegion, dummyRegion)
    assert(t.done)
  }

  test("interpolation is applied to progress") {
    val squareInterp: Interpolation = new Interpolation {
      override def apply(a: Float): Float = a * a
    }
    val t = new TestTimedTransition(1.0f, squareInterp)
    t.show()
    t.render(0.5f, dummyRegion, dummyRegion)
    assertEqualsFloat(t.lastProgress, 0.25f, 0.01f)
  }

  test("show() resets timePassed to 0") {
    val t = new TestTimedTransition(1.0f)
    t.show()
    t.render(0.8f, dummyRegion, dummyRegion)
    assert(t.lastProgress > 0.5f)
    t.show()
    assert(!t.done)
    t.render(0.1f, dummyRegion, dummyRegion)
    assertEqualsFloat(t.lastProgress, 0.1f, 0.01f)
  }

  test("duration must be positive") {
    intercept[IllegalArgumentException] {
      new TestTimedTransition(0f)
    }
    intercept[IllegalArgumentException] {
      new TestTimedTransition(-1f)
    }
  }

  private def assertEqualsFloat(actual: Float, expected: Float, delta: Float)(using munit.Location): Unit =
    assert(Math.abs(actual - expected) <= delta, s"expected $expected +/- $delta but got $actual")
}
