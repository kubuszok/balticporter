package com.badlogic.gdx.math

class FloatCounter extends com.badlogic.gdx.utils.Pool.Poolable {
  var count: scala.Int = 0
  var total: scala.Float = 0.0f
  var min: scala.Float = 0.0f
  var max: scala.Float = 0.0f
  var average: scala.Float = 0.0f
  var latest: scala.Float = 0.0f
  var value: scala.Float = 0.0f
  var mean: com.badlogic.gdx.math.WindowedMean = null.asInstanceOf[com.badlogic.gdx.math.WindowedMean]
  def this(windowSize: scala.Int) = {
    this()
    this.mean = if (windowSize > 1) new com.badlogic.gdx.math.WindowedMean(windowSize) else null
    this.reset()
  }
  def put(value: scala.Float): scala.Unit = {
    this.latest = value
    this.total = this.total + value
    this.count = this.count + 1
    this.average = this.total / this.count
    if (this.mean != null) {
      this.mean.addValue(value)
      this.value = this.mean.getMean()
    } else {
      this.value = this.latest
    }
    if ((this.mean == null) || this.mean.hasEnoughData()) {
      if (this.value < this.min) {
        this.min = this.value
      } else ()
      if (this.value > this.max) {
        this.max = this.value
      } else ()
    } else ()
  }
  def reset(): scala.Unit = {
    this.count = 0
    this.total = 0.0f
    this.min = java.lang.Float.MAX_VALUE
    this.max = -java.lang.Float.MAX_VALUE
    this.average = 0.0f
    this.latest = 0.0f
    this.value = 0.0f
    if (this.mean != null) {
      this.mean.clear()
    } else ()
  }
  def toString(): java.lang.String = {
    return (((((((((((((("FloatCounter{" + "count=") + this.count) + ", total=") + this.total) + ", min=") + this.min) + ", max=") + this.max) + ", average=") + this.average) + ", latest=") + this.latest) + ", value=") + this.value) + '}'
  }
}