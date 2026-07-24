package com.badlogic.gdx.math

class CumulativeDistribution[T] {
  private var values: com.badlogic.gdx.utils.Array[CumulativeValue[T]] = null.asInstanceOf[com.badlogic.gdx.utils.Array[CumulativeValue[T]]]
  def this() = {
    this()
    this.values = new com.badlogic.gdx.utils.Array[CumulativeValue[T]](false, 10, scala.Array[CumulativeValue].<init>)
  }
  def add(value: T, intervalSize: scala.Float): scala.Unit = {
    this.values.add(new CumulativeValue[T](value, 0, intervalSize))
  }
  def add(value: T): scala.Unit = {
    this.values.add(new CumulativeValue[T](value, 0, 0))
  }
  def generate(): scala.Unit = {
    var sum: scala.Float = 0
    { var i: scala.Int = 0; while (i < this.values.size) { {
      sum = sum + this.values.items(i).interval
      this.values.items(i).frequency = sum
    }; i = i + 1 } }
  }
  def generateNormalized(): scala.Unit = {
    var sum: scala.Float = 0
    { var i: scala.Int = 0; while (i < this.values.size) { {
      sum = sum + this.values.items(i).interval
    }; i = i + 1 } }
    var intervalSum: scala.Float = 0
    { var i: scala.Int = 0; while (i < this.values.size) { {
      intervalSum = intervalSum + (this.values.items(i).interval / sum)
      this.values.items(i).frequency = intervalSum
    }; i = i + 1 } }
  }
  def generateUniform(): scala.Unit = {
    val freq: scala.Float = 1.0f / this.values.size
    { var i: scala.Int = 0; while (i < this.values.size) { {
      this.values.items(i).interval = freq
      this.values.items(i).frequency = (i + 1) * freq
    }; i = i + 1 } }
  }
  def value(probability: scala.Float): T = {
    var value: CumulativeValue[T] = null
    var imax: scala.Int = this.values.size - 1
    var imin: scala.Int = 0
    var imid: scala.Int = 0
    while (imin <= imax) {
      imid = imin + ((imax - imin) / 2)
      value = this.values.items(imid)
      if (probability < value.frequency) {
        imax = imid - 1
      } else {
        if (probability > value.frequency) {
          imin = imid + 1
        } else {
          /* break */ ()
        }
      }
    }
    return this.values.items(imin).value
  }
  def value(): T = {
    return this.value(com.badlogic.gdx.math.MathUtils.random())
  }
  def size(): scala.Int = {
    return this.values.size
  }
  def getInterval(index: scala.Int): scala.Float = {
    return this.values.items(index).interval
  }
  def getValue(index: scala.Int): T = {
    return this.values.items(index).value
  }
  def setInterval(obj: T, intervalSize: scala.Float): scala.Unit = {
    for (value <- this.values) {
      if (value.value == obj) {
        value.interval = intervalSize
        return
      } else ()
    }
  }
  def setInterval(index: scala.Int, intervalSize: scala.Float): scala.Unit = {
    this.values.items(index).interval = intervalSize
  }
  def clear(): scala.Unit = {
    this.values.clear()
  }
  private class CumulativeValue[T] {
    var value: T = null.asInstanceOf[T]
    var frequency: scala.Float = 0.0f
    var interval: scala.Float = 0.0f
    def this(value: T, frequency: scala.Float, interval: scala.Float) = {
      this()
      this.value = value
      this.frequency = frequency
      this.interval = interval
    }
  }
}