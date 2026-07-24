package com.badlogic.gdx.graphics.g3d.particles.values

class ScaledNumericValue extends com.badlogic.gdx.graphics.g3d.particles.values.RangedNumericValue {
  private var scaling: scala.Array[scala.Float] = scala.Array[scala.Float](1)
  var timeline: scala.Array[scala.Float] = scala.Array[scala.Float](0)
  private var highMin: scala.Float = 0.0f
  private var highMax: scala.Float = 0.0f
  private var relative: scala.Boolean = false
  def newHighValue(): scala.Float = {
    return this.highMin + ((this.highMax - this.highMin) * com.badlogic.gdx.math.MathUtils.random())
  }
  def setHigh(value: scala.Float): scala.Unit = {
    this.highMin = value
    this.highMax = value
  }
  def setHigh(min: scala.Float, max: scala.Float): scala.Unit = {
    this.highMin = min
    this.highMax = max
  }
  def getHighMin(): scala.Float = {
    return this.highMin
  }
  def setHighMin(highMin: scala.Float): scala.Unit = {
    this.highMin = highMin
  }
  def getHighMax(): scala.Float = {
    return this.highMax
  }
  def setHighMax(highMax: scala.Float): scala.Unit = {
    this.highMax = highMax
  }
  def getScaling(): scala.Array[scala.Float] = {
    return this.scaling
  }
  def setScaling(values: scala.Array[scala.Float]): scala.Unit = {
    this.scaling = values
  }
  def getTimeline(): scala.Array[scala.Float] = {
    return this.timeline
  }
  def setTimeline(timeline: scala.Array[scala.Float]): scala.Unit = {
    this.timeline = timeline
  }
  def isRelative(): scala.Boolean = {
    return this.relative
  }
  def setRelative(relative: scala.Boolean): scala.Unit = {
    this.relative = relative
  }
  def getScale(percent: scala.Float): scala.Float = {
    var endIndex: scala.Int = -1
    val n: scala.Int = this.timeline.length;
    { var i: scala.Int = 1; while (i < n) { {
      val t: scala.Float = this.timeline(i)
      if (t > percent) {
        endIndex = i
        /* break */ ()
      } else ()
    }; i = i + 1 } }
    if (endIndex == (-1)) {
      return this.scaling(n - 1)
    } else ()
    val startIndex: scala.Int = endIndex - 1
    val startValue: scala.Float = this.scaling(startIndex)
    val startTime: scala.Float = this.timeline(startIndex)
    return startValue + ((this.scaling(endIndex) - startValue) * ((percent - startTime) / (this.timeline(endIndex) - startTime)))
  }
  def load(value: ScaledNumericValue): scala.Unit = {
    super.load(value)
    this.highMax = value.highMax
    this.highMin = value.highMin
    this.scaling = new scala.Array[scala.Float](value.scaling.length)
    java.lang.System.arraycopy(value.scaling, 0, this.scaling, 0, this.scaling.length)
    this.timeline = new scala.Array[scala.Float](value.timeline.length)
    java.lang.System.arraycopy(value.timeline, 0, this.timeline, 0, this.timeline.length)
    this.relative = value.relative
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("highMin", this.highMin.asInstanceOf[java.lang.Float])
    json.writeValue("highMax", this.highMax.asInstanceOf[java.lang.Float])
    json.writeValue("relative", this.relative.asInstanceOf[java.lang.Boolean])
    json.writeValue("scaling", this.scaling)
    json.writeValue("timeline", this.timeline)
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.highMin = json.readValue("highMin", classOf[scala.Float], jsonData)
    this.highMax = json.readValue("highMax", classOf[scala.Float], jsonData)
    this.relative = json.readValue("relative", classOf[scala.Boolean], jsonData)
    this.scaling = json.readValue("scaling", classOf[scala.Array[scala.Float]], jsonData)
    this.timeline = json.readValue("timeline", classOf[scala.Array[scala.Float]], jsonData)
  }
}