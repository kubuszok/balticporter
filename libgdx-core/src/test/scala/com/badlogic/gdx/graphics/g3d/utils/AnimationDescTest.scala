package com.badlogic.gdx.graphics.g3d.utils

class AnimationDescTest extends munit.FunSuite {
  private var anim: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc]
  @org.junit.Before
  def setup(): scala.Unit = {
    this.anim = new com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc()
    this.anim.animation = new com.badlogic.gdx.graphics.g3d.model.Animation()
    this.anim.duration = 1.0f
    this.anim.listener = null
    this.anim.loopCount = 1
    this.anim.offset = 0.0f
    this.anim.speed = 1.0f
    this.anim.time = 0.0f
  }
  test("testUpdateNominal")({
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.75f), -1)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.75f), 0.5f)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.75f), 0.75f)
  })
  test("testUpdateJustEnd")({
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.5f), -1)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.5f), 0)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.5f), 0.5f)
  })
  test("testUpdateBigDelta")({
    assertEquals(AnimationDescTest.epsilon, this.anim.update(5.2f), 4.2f)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(7.3f), 7.3f)
  })
  test("testUpdateZeroDelta")({
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.0f), -1)
    assertEquals(AnimationDescTest.epsilon, this.anim.time, 0.0f)
  })
  test("testUpdateReverseNominal")({
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.75f), -1)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.75f), 0.5f)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.75f), 0.75f)
  })
  test("testUpdateReverseJustEnd")({
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.5f), -1)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.5f), 0)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.5f), 0.5f)
  })
  test("testUpdateReverseBigDelta")({
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    assertEquals(AnimationDescTest.epsilon, this.anim.update(5.2f), 4.2f)
    assertEquals(AnimationDescTest.epsilon, this.anim.update(7.3f), 7.3f)
  })
  test("testUpdateReverseZeroDelta")({
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    assertEquals(AnimationDescTest.epsilon, this.anim.update(0.0f), -1)
    assertEquals(AnimationDescTest.epsilon, this.anim.time, this.anim.duration)
  })
}
object AnimationDescTest {
  private final val epsilon: scala.Float = 1.0E-6f
}