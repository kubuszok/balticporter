package com.badlogic.gdx.utils

class PerformanceCounter {
  private var startTime: scala.Long = 0L
  private var lastTick: scala.Long = 0L
  var time: com.badlogic.gdx.math.FloatCounter = null.asInstanceOf[com.badlogic.gdx.math.FloatCounter]
  var load: com.badlogic.gdx.math.FloatCounter = null.asInstanceOf[com.badlogic.gdx.math.FloatCounter]
  var name: java.lang.String = null.asInstanceOf[java.lang.String]
  var current: scala.Float = 0.0f
  var valid: scala.Boolean = false
  def this(name: java.lang.String, windowSize: scala.Int) = {
    this()
    this.name = name
    this.time = new com.badlogic.gdx.math.FloatCounter(windowSize)
    this.load = new com.badlogic.gdx.math.FloatCounter(1)
  }
  def this(name: java.lang.String) = {
    this(name, 5)
  }
  def tick(): scala.Unit = {
    val t: scala.Long = com.badlogic.gdx.utils.TimeUtils.nanoTime()
    if (this.lastTick > 0L) {
      this.tick((t - this.lastTick) * PerformanceCounter.nano2seconds)
    } else ()
    this.lastTick = t
  }
  def tick(delta: scala.Float): scala.Unit = {
    if (!this.valid) {
      com.badlogic.gdx.Gdx.app.error("PerformanceCounter", "Invalid data, check if you called PerformanceCounter#stop()")
      return
    } else ()
    this.time.put(this.current)
    val currentLoad: scala.Float = if (delta == 0.0f) 0.0f else this.current / delta
    this.load.put(if (delta > 1.0f) currentLoad else (delta * currentLoad) + ((1.0f - delta) * this.load.latest))
    this.current = 0.0f
    this.valid = false
  }
  def start(): scala.Unit = {
    this.startTime = com.badlogic.gdx.utils.TimeUtils.nanoTime()
    this.valid = false
  }
  def stop(): scala.Unit = {
    if (this.startTime > 0L) {
      this.current = this.current + ((com.badlogic.gdx.utils.TimeUtils.nanoTime() - this.startTime) * PerformanceCounter.nano2seconds)
      this.startTime = 0L
      this.valid = true
    } else ()
  }
  def reset(): scala.Unit = {
    this.time.reset()
    this.load.reset()
    this.startTime = 0L
    this.lastTick = 0L
    this.current = 0.0f
    this.valid = false
  }
  def toString(): java.lang.String = {
    val sb: java.lang.StringBuilder = new java.lang.StringBuilder()
    return this.toString(sb).toString()
  }
  def toString(sb: java.lang.StringBuilder): java.lang.StringBuilder = {
    sb.append(this.name).append(": [time: ").append(this.time.value).append(", load: ").append(this.load.value).append("]")
    return sb
  }
}
object PerformanceCounter {
  private final val nano2seconds: scala.Float = com.badlogic.gdx.math.MathUtils.nanoToSec
}