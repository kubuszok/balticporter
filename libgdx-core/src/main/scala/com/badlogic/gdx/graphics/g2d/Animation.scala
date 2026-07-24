package com.badlogic.gdx.graphics.g2d

class Animation[T] {
  var keyFrames: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
  private var frameDuration: scala.Float = 0.0f
  private var animationDuration: scala.Float = 0.0f
  private var lastFrameNumber: scala.Int = 0
  private var lastStateTime: scala.Float = 0.0f
  private var playMode: com.badlogic.gdx.graphics.g2d.Animation.PlayMode = com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL
  def this(frameDuration: scala.Float, keyFrames: com.badlogic.gdx.utils.Array[? <: T]) = {
    this()
    this.frameDuration = frameDuration
    val frames: scala.Array[T] = java.util.Arrays.copyOf(keyFrames.items.asInstanceOf[scala.Array[java.lang.Object]], keyFrames.size).asInstanceOf[scala.Array[T]]
    this.setKeyFrames(frames)
  }
  def this(frameDuration: scala.Float, keyFrames: scala.Array[T]) = {
    this()
    this.frameDuration = frameDuration
    this.setKeyFrames(keyFrames)
  }
  def this(frameDuration: scala.Float, keyFrames: com.badlogic.gdx.utils.Array[? <: T], playMode: com.badlogic.gdx.graphics.g2d.Animation.PlayMode) = {
    this(frameDuration, keyFrames)
    this.setPlayMode(playMode)
  }
  def getKeyFrame(stateTime: scala.Float, looping: scala.Boolean): T = {
    val oldPlayMode: com.badlogic.gdx.graphics.g2d.Animation.PlayMode = this.playMode
    if (looping && ((this.playMode == com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL) || (this.playMode == com.badlogic.gdx.graphics.g2d.Animation.PlayMode.REVERSED))) {
      if (this.playMode == com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL) {
        this.playMode = com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP
      } else {
        this.playMode = com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP_REVERSED
      }
    } else {
      if ((!looping) && (!((this.playMode == com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL) || (this.playMode == com.badlogic.gdx.graphics.g2d.Animation.PlayMode.REVERSED)))) {
        if (this.playMode == com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP_REVERSED) {
          this.playMode = com.badlogic.gdx.graphics.g2d.Animation.PlayMode.REVERSED
        } else {
          this.playMode = com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP
        }
      } else ()
    }
    val frame: T = this.getKeyFrame(stateTime).asInstanceOf[T]
    this.playMode = oldPlayMode
    return frame
  }
  def getKeyFrame(stateTime: scala.Float): T = {
    val frameNumber: scala.Int = this.getKeyFrameIndex(stateTime)
    return this.keyFrames(frameNumber)
  }
  def getKeyFrameIndex(stateTime: scala.Float): scala.Int = {
    if (this.keyFrames.length == 1) {
      return 0
    } else ()
    var frameNumber: scala.Int = (stateTime / this.frameDuration).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    this.playMode match {
      case com.badlogic.gdx.graphics.g2d.Animation.PlayMode.NORMAL => {
        frameNumber = java.lang.Math.min(this.keyFrames.length - 1, frameNumber)
      }
      case com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP => {
        frameNumber = frameNumber % this.keyFrames.length
      }
      case com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP_PINGPONG => {
        frameNumber = frameNumber % ((this.keyFrames.length * 2) - 2)
        if (frameNumber >= this.keyFrames.length) {
          frameNumber = (this.keyFrames.length - 2) - (frameNumber - this.keyFrames.length)
        } else ()
      }
      case com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP_RANDOM => {
        var lastFrameNumber: scala.Int = (this.lastStateTime / this.frameDuration).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
        if (lastFrameNumber != frameNumber) {
          frameNumber = com.badlogic.gdx.math.MathUtils.random(this.keyFrames.length - 1)
        } else {
          frameNumber = this.lastFrameNumber
        }
      }
      case com.badlogic.gdx.graphics.g2d.Animation.PlayMode.REVERSED => {
        frameNumber = java.lang.Math.max((this.keyFrames.length - frameNumber) - 1, 0)
      }
      case com.badlogic.gdx.graphics.g2d.Animation.PlayMode.LOOP_REVERSED => {
        frameNumber = frameNumber % this.keyFrames.length
        frameNumber = (this.keyFrames.length - frameNumber) - 1
      }
    }
    this.lastFrameNumber = frameNumber
    this.lastStateTime = stateTime
    return frameNumber
  }
  def getKeyFrames(): scala.Array[T] = {
    return this.keyFrames
  }
  def setKeyFrames(keyFrames: scala.Array[T]): scala.Unit = {
    this.keyFrames = keyFrames
    this.animationDuration = keyFrames.length * this.frameDuration
  }
  def getPlayMode(): com.badlogic.gdx.graphics.g2d.Animation.PlayMode = {
    return this.playMode
  }
  def setPlayMode(playMode: com.badlogic.gdx.graphics.g2d.Animation.PlayMode): scala.Unit = {
    this.playMode = playMode
  }
  def isAnimationFinished(stateTime: scala.Float): scala.Boolean = {
    val frameNumber: scala.Int = (stateTime / this.frameDuration).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    return (this.keyFrames.length - 1) < frameNumber
  }
  def setFrameDuration(frameDuration: scala.Float): scala.Unit = {
    this.frameDuration = frameDuration
    this.animationDuration = this.keyFrames.length * frameDuration
  }
  def getFrameDuration(): scala.Float = {
    return this.frameDuration
  }
  def getAnimationDuration(): scala.Float = {
    return this.animationDuration
  }
}
object Animation {
  sealed abstract class PlayMode {
    def name(): java.lang.String = this.toString()
  }
  object PlayMode {
    case object NORMAL extends PlayMode
    case object REVERSED extends PlayMode
    case object LOOP extends PlayMode
    case object LOOP_REVERSED extends PlayMode
    case object LOOP_PINGPONG extends PlayMode
    case object LOOP_RANDOM extends PlayMode
    def values(): scala.Array[PlayMode] = scala.Array(NORMAL, REVERSED, LOOP, LOOP_REVERSED, LOOP_PINGPONG, LOOP_RANDOM)
    def valueOf(name: java.lang.String): PlayMode = name match {
      case "NORMAL" => NORMAL
      case "REVERSED" => REVERSED
      case "LOOP" => LOOP
      case "LOOP_REVERSED" => LOOP_REVERSED
      case "LOOP_PINGPONG" => LOOP_PINGPONG
      case "LOOP_RANDOM" => LOOP_RANDOM
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}