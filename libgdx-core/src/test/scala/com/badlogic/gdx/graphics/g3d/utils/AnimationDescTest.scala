package com.badlogic.gdx.graphics.g3d.utils

class AnimationDescTest extends balticporter.runtime.PortedSuite {
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
  testCase("testUpdateNominal", {
    org.junit.Assert.assertEquals(-1, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.75f, this.anim.update(0.75f), AnimationDescTest.epsilon)
  })
  testCase("testUpdateJustEnd", {
    org.junit.Assert.assertEquals(-1, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.5f), AnimationDescTest.epsilon)
  })
  testCase("testUpdateBigDelta", {
    org.junit.Assert.assertEquals(4.2f, this.anim.update(5.2f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(7.3f, this.anim.update(7.3f), AnimationDescTest.epsilon)
  })
  testCase("testUpdateZeroDelta", {
    org.junit.Assert.assertEquals(-1, this.anim.update(0.0f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.0f, this.anim.time, AnimationDescTest.epsilon)
  })
  testCase("testUpdateReverseNominal", {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(-1, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.75f, this.anim.update(0.75f), AnimationDescTest.epsilon)
  })
  testCase("testUpdateReverseJustEnd", {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(-1, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.5f), AnimationDescTest.epsilon)
  })
  testCase("testUpdateReverseBigDelta", {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(4.2f, this.anim.update(5.2f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(7.3f, this.anim.update(7.3f), AnimationDescTest.epsilon)
  })
  testCase("testUpdateReverseZeroDelta", {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(-1, this.anim.update(0.0f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(this.anim.duration, this.anim.time, AnimationDescTest.epsilon)
  })
}
object AnimationDescTest {
  private final val epsilon: scala.Float = 1.0E-6f
}