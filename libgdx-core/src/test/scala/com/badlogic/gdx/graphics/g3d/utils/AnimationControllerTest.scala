package com.badlogic.gdx.graphics.g3d.utils

class AnimationControllerTest extends balticporter.runtime.PortedSuite {
  testCase("testGetFirstKeyframeIndexAtTimeNominal", {
    val keyFrames: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String]]()
    keyFrames.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String](0.0f, "1st"))
    keyFrames.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String](3.0f, "2nd"))
    keyFrames.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String](12.0f, "3rd"))
    keyFrames.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String](13.0f, "4th"))
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, -1.0f))
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 0.0f))
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 2.0f))
    balticporter.runtime.Asserts.assertEquals(1, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 9.0f))
    balticporter.runtime.Asserts.assertEquals(2, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 12.5f))
    balticporter.runtime.Asserts.assertEquals(2, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 13.0f))
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 14.0f))
  })
  testCase("testGetFirstKeyframeIndexAtTimeSingleKey", {
    val keyFrames: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String]]()
    keyFrames.add(new com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String](10.0f, "1st"))
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 9.0f))
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 10.0f))
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 11.0f))
  })
  testCase("testGetFirstKeyframeIndexAtTimeEmpty", {
    val keyFrames: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.String]]()
    balticporter.runtime.Asserts.assertEquals(0, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.getFirstKeyframeIndexAtTime(keyFrames, 3.0f))
  })
  testCase("testEndUpActionAtDurationTime", {
    val loop: com.badlogic.gdx.graphics.g3d.model.Animation = new com.badlogic.gdx.graphics.g3d.model.Animation()
    loop.id = "loop"
    loop.duration = 1.0f
    val action: com.badlogic.gdx.graphics.g3d.model.Animation = new com.badlogic.gdx.graphics.g3d.model.Animation()
    action.id = "action"
    action.duration = 0.2f
    val modelInstance: com.badlogic.gdx.graphics.g3d.ModelInstance = new com.badlogic.gdx.graphics.g3d.ModelInstance(new com.badlogic.gdx.graphics.g3d.Model())
    modelInstance.animations.add(loop)
    modelInstance.animations.add(action)
    val animationController: com.badlogic.gdx.graphics.g3d.utils.AnimationController = new com.badlogic.gdx.graphics.g3d.utils.AnimationController(modelInstance)
    animationController.setAnimation("loop", -1)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
    animationController.update(1)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
    animationController.update(0.01f)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
    animationController.action("action", 1, 1.0f, null, 0.0f)
    AnimationControllerTest.assertSameAnimation(action, animationController.current)
    animationController.update(0.2f)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
  })
  testCase("testEndUpActionAtDurationTimeReverse", {
    val loop: com.badlogic.gdx.graphics.g3d.model.Animation = new com.badlogic.gdx.graphics.g3d.model.Animation()
    loop.id = "loop"
    loop.duration = 1.0f
    val action: com.badlogic.gdx.graphics.g3d.model.Animation = new com.badlogic.gdx.graphics.g3d.model.Animation()
    action.id = "action"
    action.duration = 0.2f
    val modelInstance: com.badlogic.gdx.graphics.g3d.ModelInstance = new com.badlogic.gdx.graphics.g3d.ModelInstance(new com.badlogic.gdx.graphics.g3d.Model())
    modelInstance.animations.add(loop)
    modelInstance.animations.add(action)
    val animationController: com.badlogic.gdx.graphics.g3d.utils.AnimationController = new com.badlogic.gdx.graphics.g3d.utils.AnimationController(modelInstance)
    animationController.setAnimation("loop", -1, -1.0f, null)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
    animationController.update(1)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
    animationController.update(0.01f)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
    animationController.action("action", 1, -1.0f, null, 0.0f)
    AnimationControllerTest.assertSameAnimation(action, animationController.current)
    animationController.update(0.2f)
    AnimationControllerTest.assertSameAnimation(loop, animationController.current)
  })
}
object AnimationControllerTest {
  private def assertSameAnimation(expected: com.badlogic.gdx.graphics.g3d.model.Animation, actual: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc): scala.Unit = {
    if (!expected.id.equals(actual.animation.id)) {
      balticporter.runtime.Asserts.fail((("expected: " + expected.id) + ", actual: ") + actual.animation.id)
    } else ()
  }
}