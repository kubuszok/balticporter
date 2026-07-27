package com.badlogic.gdx.graphics.g3d.utils

class BaseAnimationController(target$p: com.badlogic.gdx.graphics.g3d.ModelInstance) {
  private final val transformPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform] = new com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform]()
  private var applying: scala.Boolean = false
  var target: com.badlogic.gdx.graphics.g3d.ModelInstance = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.ModelInstance]
  this.target = target$p
  def begin(): scala.Unit = {
    if (this.applying) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("You must call end() after each call to being()")
    } else ()
    this.applying = true
  }
  def apply(animation: com.badlogic.gdx.graphics.g3d.model.Animation, time: scala.Float, weight: scala.Float): scala.Unit = {
    if (!this.applying) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("You must call begin() before adding an animation")
    } else ()
    BaseAnimationController.applyAnimation(BaseAnimationController.transforms, this.transformPool, weight, animation, time)
  }
  def `end`(): scala.Unit = {
    if (!this.applying) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("You must call begin() first")
    } else ()
    for (entry <- BaseAnimationController.transforms.entries()) {
      entry.value.toMatrix4(entry.key.localTransform)
      this.transformPool.free(entry.value)
    }
    BaseAnimationController.transforms.clear()
    this.target.calculateTransforms()
    this.applying = false
  }
  def applyAnimation(animation: com.badlogic.gdx.graphics.g3d.model.Animation, time: scala.Float): scala.Unit = {
    if (this.applying) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call end() first")
    } else ()
    BaseAnimationController.applyAnimation(null, null, 1.0f, animation, time)
    this.target.calculateTransforms()
  }
  def applyAnimations(anim1: com.badlogic.gdx.graphics.g3d.model.Animation, time1: scala.Float, anim2: com.badlogic.gdx.graphics.g3d.model.Animation, time2: scala.Float, weight: scala.Float): scala.Unit = {
    if ((anim2 == null) || (weight == 0.0f)) {
      this.applyAnimation(anim1, time1)
    } else {
      if ((anim1 == null) || (weight == 1.0f)) {
        this.applyAnimation(anim2, time2)
      } else {
        if (this.applying) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Call end() first")
        } else {
          this.begin()
          this.apply(anim1, time1, 1.0f)
          this.apply(anim2, time2, weight)
          this.`end`()
        }
      }
    }
  }
  def removeAnimation(animation: com.badlogic.gdx.graphics.g3d.model.Animation): scala.Unit = {
    for (nodeAnim <- animation.nodeAnimations) {
      nodeAnim.node.isAnimated = false
    }
  }
}
object BaseAnimationController {
  private final val transforms: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform] = new com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform]()
  private final val tmpT: com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = new com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform()
  final def getFirstKeyframeIndexAtTime[T](arr: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[T]], time: scala.Float): scala.Int = {
    val lastIndex: scala.Int = arr.size - 1
    if (((lastIndex <= 0) || (time < arr.get(0).keytime)) || (time > arr.get(lastIndex).keytime)) {
      return 0
    } else ()
    var minIndex: scala.Int = 0
    var maxIndex: scala.Int = lastIndex
    while (minIndex < maxIndex) {
      val i: scala.Int = (minIndex + maxIndex) / 2
      if (time > arr.get(i + 1).keytime) {
        minIndex = i + 1
      } else {
        if (time < arr.get(i).keytime) {
          maxIndex = i - 1
        } else {
          return i
        }
      }
    }
    return minIndex
  }
  private final def getTranslationAtTime(nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation, time: scala.Float, out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    if (nodeAnim.translation == null) {
      return out.set(nodeAnim.node.translation)
    } else ()
    if (nodeAnim.translation.size == 1) {
      return out.set(nodeAnim.translation.get(0).value)
    } else ()
    var index: scala.Int = BaseAnimationController.getFirstKeyframeIndexAtTime(nodeAnim.translation, time)
    val firstKeyframe: com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[?] = nodeAnim.translation.get(index).asInstanceOf[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[?]]
    out.set(firstKeyframe.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.Object]].value.asInstanceOf[com.badlogic.gdx.math.Vector3].asInstanceOf[com.badlogic.gdx.math.Vector3])
    if ({ index += 1; index } < nodeAnim.translation.size) {
      val secondKeyframe: com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3] = nodeAnim.translation.get(index)
      val t: scala.Float = (time - firstKeyframe.keytime) / (secondKeyframe.keytime - firstKeyframe.keytime)
      out.lerp(secondKeyframe.value, t)
    } else ()
    return out
  }
  private final def getRotationAtTime(nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation, time: scala.Float, out: com.badlogic.gdx.math.Quaternion): com.badlogic.gdx.math.Quaternion = {
    if (nodeAnim.rotation == null) {
      return out.set(nodeAnim.node.rotation)
    } else ()
    if (nodeAnim.rotation.size == 1) {
      return out.set(nodeAnim.rotation.get(0).value)
    } else ()
    var index: scala.Int = BaseAnimationController.getFirstKeyframeIndexAtTime(nodeAnim.rotation, time)
    val firstKeyframe: com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[?] = nodeAnim.rotation.get(index).asInstanceOf[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[?]]
    out.set(firstKeyframe.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.Object]].value.asInstanceOf[com.badlogic.gdx.math.Quaternion].asInstanceOf[com.badlogic.gdx.math.Quaternion])
    if ({ index += 1; index } < nodeAnim.rotation.size) {
      val secondKeyframe: com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Quaternion] = nodeAnim.rotation.get(index)
      val t: scala.Float = (time - firstKeyframe.keytime) / (secondKeyframe.keytime - firstKeyframe.keytime)
      out.slerp(secondKeyframe.value, t)
    } else ()
    return out
  }
  private final def getScalingAtTime(nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation, time: scala.Float, out: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.math.Vector3 = {
    if (nodeAnim.scaling == null) {
      return out.set(nodeAnim.node.scale)
    } else ()
    if (nodeAnim.scaling.size == 1) {
      return out.set(nodeAnim.scaling.get(0).value)
    } else ()
    var index: scala.Int = BaseAnimationController.getFirstKeyframeIndexAtTime(nodeAnim.scaling, time)
    val firstKeyframe: com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[?] = nodeAnim.scaling.get(index).asInstanceOf[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[?]]
    out.set(firstKeyframe.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[java.lang.Object]].value.asInstanceOf[com.badlogic.gdx.math.Vector3].asInstanceOf[com.badlogic.gdx.math.Vector3])
    if ({ index += 1; index } < nodeAnim.scaling.size) {
      val secondKeyframe: com.badlogic.gdx.graphics.g3d.model.NodeKeyframe[com.badlogic.gdx.math.Vector3] = nodeAnim.scaling.get(index)
      val t: scala.Float = (time - firstKeyframe.keytime) / (secondKeyframe.keytime - firstKeyframe.keytime)
      out.lerp(secondKeyframe.value, t)
    } else ()
    return out
  }
  private final def getNodeAnimationTransform(nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation, time: scala.Float): com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = {
    val transform: com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = BaseAnimationController.tmpT
    BaseAnimationController.getTranslationAtTime(nodeAnim, time, transform.translation)
    BaseAnimationController.getRotationAtTime(nodeAnim, time, transform.rotation)
    BaseAnimationController.getScalingAtTime(nodeAnim, time, transform.scale)
    return transform
  }
  private final def applyNodeAnimationDirectly(nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation, time: scala.Float): scala.Unit = {
    val node: com.badlogic.gdx.graphics.g3d.model.Node = nodeAnim.node
    node.isAnimated = true
    val transform: com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = BaseAnimationController.getNodeAnimationTransform(nodeAnim, time)
    transform.toMatrix4(node.localTransform)
  }
  private final def applyNodeAnimationBlending(nodeAnim: com.badlogic.gdx.graphics.g3d.model.NodeAnimation, out: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform], alpha: scala.Float, time: scala.Float): scala.Unit = {
    val node: com.badlogic.gdx.graphics.g3d.model.Node = nodeAnim.node
    node.isAnimated = true
    val transform: com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = BaseAnimationController.getNodeAnimationTransform(nodeAnim, time)
    val t: com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = out.get(node, null)
    if (t != null) {
      if (alpha > 0.999999f) {
        t.set(transform)
      } else {
        t.lerp(transform, alpha)
      }
    } else {
      if (alpha > 0.999999f) {
        out.put(node, pool.obtain().set(transform))
      } else {
        out.put(node, pool.obtain().set(node.translation, node.rotation, node.scale).lerp(transform, alpha))
      }
    }
  }
  def applyAnimation(out: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.model.Node, com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform], alpha: scala.Float, animation: com.badlogic.gdx.graphics.g3d.model.Animation, time: scala.Float): scala.Unit = {
    if (out == null) {
      for (nodeAnim <- animation.nodeAnimations) {
        BaseAnimationController.applyNodeAnimationDirectly(nodeAnim, time)
      }
    } else {
      for (node <- out.keys()) {
        node.isAnimated = false
      }
      for (nodeAnim <- animation.nodeAnimations) {
        BaseAnimationController.applyNodeAnimationBlending(nodeAnim, out, pool, alpha, time)
      }
      for (e <- out.entries()) {
        if (!e.key.isAnimated) {
          e.key.isAnimated = true
          e.value.lerp(e.key.translation, e.key.rotation, e.key.scale, alpha)
        } else ()
      }
    }
  }
  final class Transform extends com.badlogic.gdx.utils.Pool.Poolable {
    final val translation: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
    final val rotation: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
    final val scale: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3(1, 1, 1)
    def idt(): com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = {
      this.translation.set(0, 0, 0)
      this.rotation.idt()
      this.scale.set(1, 1, 1)
      return this
    }
    def set(t: com.badlogic.gdx.math.Vector3, r: com.badlogic.gdx.math.Quaternion, s: com.badlogic.gdx.math.Vector3): com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = {
      this.translation.set(t)
      this.rotation.set(r)
      this.scale.set(s)
      return this
    }
    def set(other: com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform): com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = {
      return this.set(other.translation, other.rotation, other.scale)
    }
    def lerp(target: com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform, alpha: scala.Float): com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = {
      return this.lerp(target.translation, target.rotation, target.scale, alpha)
    }
    def lerp(targetT: com.badlogic.gdx.math.Vector3, targetR: com.badlogic.gdx.math.Quaternion, targetS: com.badlogic.gdx.math.Vector3, alpha: scala.Float): com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.Transform = {
      this.translation.lerp(targetT, alpha)
      this.rotation.slerp(targetR, alpha)
      this.scale.lerp(targetS, alpha)
      return this
    }
    def toMatrix4(out: com.badlogic.gdx.math.Matrix4): com.badlogic.gdx.math.Matrix4 = {
      return out.set(this.translation, this.rotation, this.scale)
    }
    def reset(): scala.Unit = {
      this.idt()
    }
    def toString(): java.lang.String = {
      return (((this.translation.toString() + " - ") + this.rotation.toString()) + " - ") + this.scale.toString()
    }
  }
}