package com.badlogic.gdx.graphics.g3d.utils

class AnimationController extends com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController {
  final val animationPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc] = new com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc]()
  var current: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc]
  var queued: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc]
  var queuedTransitionTime: scala.Float = 0.0f
  var previous: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc]
  var transitionCurrentTime: scala.Float = 0.0f
  var transitionTargetTime: scala.Float = 0.0f
  var inAction: scala.Boolean = false
  var paused: scala.Boolean = false
  var allowSameAnimation: scala.Boolean = false
  private var justChangedAnimation: scala.Boolean = false
  def this(target: com.badlogic.gdx.graphics.g3d.ModelInstance) = {
    this()
  }
  private def obtain(anim: com.badlogic.gdx.graphics.g3d.model.Animation, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    if (anim == null) {
      return null
    } else ()
    val result: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = this.animationPool.obtain()
    result.animation = anim
    result.listener = listener
    result.loopCount = loopCount
    result.speed = speed
    result.offset = offset
    result.duration = if (duration < 0) anim.duration - offset else duration
    result.time = if (speed < 0) result.duration else 0.0f
    return result
  }
  private def obtain(id: java.lang.String, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    if (id == null) {
      return null
    } else ()
    val anim: com.badlogic.gdx.graphics.g3d.model.Animation = target.getAnimation(id)
    if (anim == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Unknown animation: " + id)
    } else ()
    return this.obtain(anim, offset, duration, loopCount, speed, listener)
  }
  private def obtain(anim: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.obtain(anim.animation, anim.offset, anim.duration, anim.loopCount, anim.speed, anim.listener)
  }
  def update(delta: scala.Float): scala.Unit = {
    if (this.paused) {
      return
    } else ()
    if ((this.previous != null) && ({
      this.transitionCurrentTime = this.transitionCurrentTime + delta
      this.transitionCurrentTime
    } >= this.transitionTargetTime)) {
      this.removeAnimation(this.previous.animation)
      this.justChangedAnimation = true
      this.animationPool.free(this.previous)
      this.previous = null
    } else ()
    if (this.justChangedAnimation) {
      target.calculateTransforms()
      this.justChangedAnimation = false
    } else ()
    if (((this.current == null) || (this.current.loopCount == 0)) || (this.current.animation == null)) {
      return
    } else ()
    val remain: scala.Float = this.current.update(delta)
    if ((remain >= 0.0f) && (this.queued != null)) {
      this.inAction = false
      this.animate(this.queued, this.queuedTransitionTime)
      this.queued = null
      if (remain > 0.0f) {
        this.update(remain)
      } else ()
      return
    } else ()
    if (this.previous != null) {
      this.applyAnimations(this.previous.animation, this.previous.offset + this.previous.time, this.current.animation, this.current.offset + this.current.time, this.transitionCurrentTime / this.transitionTargetTime)
    } else {
      this.applyAnimation(this.current.animation, this.current.offset + this.current.time)
    }
  }
  def setAnimation(id: java.lang.String): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.setAnimation(id, 1, 1.0f, null)
  }
  def setAnimation(id: java.lang.String, loopCount: scala.Int): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.setAnimation(id, loopCount, 1.0f, null)
  }
  def setAnimation(id: java.lang.String, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.setAnimation(id, 1, 1.0f, listener)
  }
  def setAnimation(id: java.lang.String, loopCount: scala.Int, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.setAnimation(id, loopCount, 1.0f, listener)
  }
  def setAnimation(id: java.lang.String, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.setAnimation(id, 0.0f, -1.0f, loopCount, speed, listener)
  }
  def setAnimation(id: java.lang.String, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.setAnimation(this.obtain(id, offset, duration, loopCount, speed, listener))
  }
  def setAnimation(anim: com.badlogic.gdx.graphics.g3d.model.Animation, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.setAnimation(this.obtain(anim, offset, duration, loopCount, speed, listener))
  }
  def setAnimation(anim: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    if (this.current == null) {
      this.current = anim
    } else {
      if (((!this.allowSameAnimation) && (anim != null)) && (this.current.animation == anim.animation)) {
        anim.time = this.current.time
      } else {
        this.removeAnimation(this.current.animation)
      }
      this.animationPool.free(this.current)
      this.current = anim
    }
    this.justChangedAnimation = true
    return anim
  }
  def animate(id: java.lang.String, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.animate(id, 1, 1.0f, null, transitionTime)
  }
  def animate(id: java.lang.String, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.animate(id, 1, 1.0f, listener, transitionTime)
  }
  def animate(id: java.lang.String, loopCount: scala.Int, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.animate(id, loopCount, 1.0f, listener, transitionTime)
  }
  def animate(id: java.lang.String, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.animate(id, 0.0f, -1.0f, loopCount, speed, listener, transitionTime)
  }
  def animate(id: java.lang.String, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.animate(this.obtain(id, offset, duration, loopCount, speed, listener), transitionTime)
  }
  def animate(anim: com.badlogic.gdx.graphics.g3d.model.Animation, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.animate(this.obtain(anim, offset, duration, loopCount, speed, listener), transitionTime)
  }
  def animate(anim: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    if ((this.current == null) || (this.current.loopCount == 0)) {
      this.current = anim
    } else {
      if (this.inAction) {
        this.queue(anim, transitionTime)
      } else {
        if (((!this.allowSameAnimation) && (anim != null)) && (this.current.animation == anim.animation)) {
          anim.time = this.current.time
          this.animationPool.free(this.current)
          this.current = anim
        } else {
          if (this.previous != null) {
            this.removeAnimation(this.previous.animation)
            this.animationPool.free(this.previous)
          } else ()
          this.previous = this.current
          this.current = anim
          this.transitionCurrentTime = 0.0f
          this.transitionTargetTime = transitionTime
        }
      }
    }
    return anim
  }
  def queue(id: java.lang.String, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.queue(id, 0.0f, -1.0f, loopCount, speed, listener, transitionTime)
  }
  def queue(id: java.lang.String, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.queue(this.obtain(id, offset, duration, loopCount, speed, listener), transitionTime)
  }
  def queue(anim: com.badlogic.gdx.graphics.g3d.model.Animation, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.queue(this.obtain(anim, offset, duration, loopCount, speed, listener), transitionTime)
  }
  def queue(anim: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    if ((this.current == null) || (this.current.loopCount == 0)) {
      this.animate(anim, transitionTime)
    } else {
      if (this.queued != null) {
        this.animationPool.free(this.queued)
      } else ()
      this.queued = anim
      this.queuedTransitionTime = transitionTime
      if (this.current.loopCount < 0) {
        this.current.loopCount = 1
      } else ()
    }
    return anim
  }
  def action(id: java.lang.String, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.action(id, 0, -1.0f, loopCount, speed, listener, transitionTime)
  }
  def action(id: java.lang.String, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.action(this.obtain(id, offset, duration, loopCount, speed, listener), transitionTime)
  }
  def action(anim: com.badlogic.gdx.graphics.g3d.model.Animation, offset: scala.Float, duration: scala.Float, loopCount: scala.Int, speed: scala.Float, listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    return this.action(this.obtain(anim, offset, duration, loopCount, speed, listener), transitionTime)
  }
  def action(anim: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc, transitionTime: scala.Float): com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = {
    if (anim.loopCount < 0) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("An action cannot be continuous")
    } else ()
    if ((this.current == null) || (this.current.loopCount == 0)) {
      this.animate(anim, transitionTime)
    } else {
      val toQueue: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc = if (this.inAction) null else this.obtain(this.current)
      this.inAction = false
      this.animate(anim, transitionTime)
      this.inAction = true
      if (toQueue != null) {
        this.queue(toQueue, transitionTime)
      } else ()
    }
    return anim
  }
}
object AnimationController {
  export com.badlogic.gdx.graphics.g3d.utils.BaseAnimationController.*
  trait AnimationListener {
    def onEnd(animation: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc): scala.Unit
    def onLoop(animation: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationDesc): scala.Unit
  }
  class AnimationDesc {
    var listener: com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.AnimationController.AnimationListener]
    var animation: com.badlogic.gdx.graphics.g3d.model.Animation = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.model.Animation]
    var speed: scala.Float = 0.0f
    var time: scala.Float = 0.0f
    var offset: scala.Float = 0.0f
    var duration: scala.Float = 0.0f
    var loopCount: scala.Int = 0
    def update(delta: scala.Float): scala.Float = {
      if ((this.loopCount != 0) && (this.animation != null)) {
        var loops: scala.Int = 0
        val diff: scala.Float = this.speed * delta
        if (!com.badlogic.gdx.math.MathUtils.isZero(this.duration)) {
          this.time = this.time + diff
          if (this.speed < 0) {
            var invTime: scala.Float = this.duration - this.time
            loops = java.lang.Math.abs(invTime / this.duration).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
            invTime = java.lang.Math.abs(invTime % this.duration)
            this.time = this.duration - invTime
          } else {
            loops = java.lang.Math.abs(this.time / this.duration).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
            this.time = java.lang.Math.abs(this.time % this.duration)
          }
        } else {
          loops = 1
        };
        { var i: scala.Int = 0; while (i < loops) { {
          if (this.loopCount > 0) {
            this.loopCount = this.loopCount - 1
          } else ()
          if ((this.loopCount != 0) && (this.listener != null)) {
            this.listener.onLoop(this)
          } else ()
          if (this.loopCount == 0) {
            val result: scala.Float = (((loops - 1) - i) * this.duration) + (if (diff < 0.0f) this.duration - this.time else this.time)
            this.time = if (diff < 0.0f) 0.0f else this.duration
            if (this.listener != null) {
              this.listener.onEnd(this)
            } else ()
            return result
          } else ()
        }; i = i + 1 } }
        return -1
      } else {
        return delta
      }
    }
  }
}