package com.badlogic.gdx.math

final class WindowedMean {
  var values: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var added_values: scala.Int = 0
  var last_value: scala.Int = 0
  var mean: scala.Float = 0
  var dirty: scala.Boolean = true
  def this(window_size: scala.Int) = {
    this()
    this.values = new Array[scala.Float](window_size)
  }
  def hasEnoughData(): scala.Boolean = {
    return this.added_values >= this.values.length
  }
  def clear(): scala.Unit = {
    this.added_values = 0
    this.last_value = 0;
    { var i: scala.Int = 0; while (i < this.values.length) { {
      this.values(i) = 0
    }; i = i + 1 } }
    this.dirty = true
  }
  def addValue(value: scala.Float): scala.Unit = {
    if (this.added_values < this.values.length) {
      this.added_values = this.added_values + 1
    } else ()
    this.values({ this.last_value += 1; this.last_value }) = value
    if (this.last_value > (this.values.length - 1)) {
      this.last_value = 0
    } else ()
    this.dirty = true
  }
  def getMean(): scala.Float = {
    if (this.hasEnoughData()) {
      if (this.dirty) {
        var mean: scala.Float = 0;
        { var i: scala.Int = 0; while (i < this.values.length) { {
          mean = mean + this.values(i)
        }; i = i + 1 } }
        this.mean = mean / this.values.length
        this.dirty = false
      } else ()
      return this.mean
    } else {
      return 0
    }
  }
  def getOldest(): scala.Float = {
    return if (this.added_values < this.values.length) this.values(0) else this.values(this.last_value)
  }
  def getLatest(): scala.Float = {
    return this.values(if ((this.last_value - 1) == (-1)) this.values.length - 1 else this.last_value - 1)
  }
  def standardDeviation(): scala.Float = {
    if (!this.hasEnoughData()) {
      return 0
    } else ()
    val mean: scala.Float = this.getMean()
    var sum: scala.Float = 0;
    { var i: scala.Int = 0; while (i < this.values.length) { {
      sum = sum + ((this.values(i) - mean) * (this.values(i) - mean))
    }; i = i + 1 } }
    return java.lang.Math.sqrt(sum / this.values.length).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
  }
  def getLowest(): scala.Float = {
    var lowest: scala.Float = java.lang.Float.MAX_VALUE;
    { var i: scala.Int = 0; while (i < this.values.length) { {
      lowest = java.lang.Math.min(lowest, this.values(i))
    }; i = i + 1 } }
    return lowest
  }
  def getHighest(): scala.Float = {
    var lowest: scala.Float = java.lang.Float.MIN_NORMAL;
    { var i: scala.Int = 0; while (i < this.values.length) { {
      lowest = java.lang.Math.max(lowest, this.values(i))
    }; i = i + 1 } }
    return lowest
  }
  def getValueCount(): scala.Int = {
    return this.added_values
  }
  def getWindowSize(): scala.Int = {
    return this.values.length
  }
  def getWindowValues(): scala.Array[scala.Float] = {
    val windowValues: scala.Array[scala.Float] = new Array[scala.Float](this.added_values)
    if (this.hasEnoughData()) {
      { var i: scala.Int = 0; while (i < windowValues.length) { {
        windowValues(i) = this.values((i + this.last_value) % this.values.length)
      }; i = i + 1 } }
    } else {
      java.lang.System.arraycopy(this.values, 0, windowValues, 0, this.added_values)
    }
    return windowValues
  }
}