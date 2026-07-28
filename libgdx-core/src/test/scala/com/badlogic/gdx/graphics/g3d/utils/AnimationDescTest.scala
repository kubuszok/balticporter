package com.badlogic.gdx.graphics.g3d.utils

class AnimationDescTest {
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
  @org.junit.Test
  def testUpdateNominal(): scala.Unit = {
    org.junit.Assert.assertEquals(-1, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.75f, this.anim.update(0.75f), AnimationDescTest.epsilon)
  }
  @org.junit.Test
  def testUpdateJustEnd(): scala.Unit = {
    org.junit.Assert.assertEquals(-1, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.5f), AnimationDescTest.epsilon)
  }
  @org.junit.Test
  def testUpdateBigDelta(): scala.Unit = {
    org.junit.Assert.assertEquals(4.2f, this.anim.update(5.2f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(7.3f, this.anim.update(7.3f), AnimationDescTest.epsilon)
  }
  @org.junit.Test
  def testUpdateZeroDelta(): scala.Unit = {
    org.junit.Assert.assertEquals(-1, this.anim.update(0.0f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.0f, this.anim.time, AnimationDescTest.epsilon)
  }
  @org.junit.Test
  def testUpdateReverseNominal(): scala.Unit = {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(-1, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.75f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.75f, this.anim.update(0.75f), AnimationDescTest.epsilon)
  }
  @org.junit.Test
  def testUpdateReverseJustEnd(): scala.Unit = {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(-1, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0, this.anim.update(0.5f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(0.5f, this.anim.update(0.5f), AnimationDescTest.epsilon)
  }
  @org.junit.Test
  def testUpdateReverseBigDelta(): scala.Unit = {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(4.2f, this.anim.update(5.2f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(7.3f, this.anim.update(7.3f), AnimationDescTest.epsilon)
  }
  @org.junit.Test
  def testUpdateReverseZeroDelta(): scala.Unit = {
    this.anim.speed = -1
    this.anim.time = this.anim.duration
    org.junit.Assert.assertEquals(-1, this.anim.update(0.0f), AnimationDescTest.epsilon)
    org.junit.Assert.assertEquals(this.anim.duration, this.anim.time, AnimationDescTest.epsilon)
  }
}
object AnimationDescTest {
  private final val epsilon: scala.Float = 1.0E-6f
}