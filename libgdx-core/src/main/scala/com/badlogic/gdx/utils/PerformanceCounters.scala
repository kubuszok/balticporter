package com.badlogic.gdx.utils

class PerformanceCounters {
  private var lastTick: scala.Long = 0L
  final val counters: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.PerformanceCounter] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.PerformanceCounter]()
  def add(name: java.lang.String, windowSize: scala.Int): com.badlogic.gdx.utils.PerformanceCounter = {
    val result: com.badlogic.gdx.utils.PerformanceCounter = new com.badlogic.gdx.utils.PerformanceCounter(name, windowSize)
    this.counters.add(result)
    return result
  }
  def add(name: java.lang.String): com.badlogic.gdx.utils.PerformanceCounter = {
    val result: com.badlogic.gdx.utils.PerformanceCounter = new com.badlogic.gdx.utils.PerformanceCounter(name)
    this.counters.add(result)
    return result
  }
  def tick(): scala.Unit = {
    val t: scala.Long = com.badlogic.gdx.utils.TimeUtils.nanoTime()
    if (this.lastTick > 0L) {
      this.tick((t - this.lastTick) * PerformanceCounters.nano2seconds)
    } else ()
    this.lastTick = t
  }
  def tick(deltaTime: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; while (i < this.counters.size) { {
      this.counters.get(i).tick(deltaTime)
    }; i = i + 1 } }
  }
  def toString(sb: java.lang.StringBuilder): java.lang.StringBuilder = {
    sb.setLength(0)
    { var i: scala.Int = 0; while (i < this.counters.size) { {
      if (i != 0) {
        sb.append("; ")
      } else ()
      this.counters.get(i).toString(sb)
    }; i = i + 1 } }
    return sb
  }
}
object PerformanceCounters {
  private final val nano2seconds: scala.Float = com.badlogic.gdx.math.MathUtils.nanoToSec
}