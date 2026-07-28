package com.badlogic.gdx.graphics.g3d.particles.values

class GradientColorValue extends com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue {
  private var colors: scala.Array[scala.Float] = scala.Array[scala.Float](1, 1, 1)
  var timeline: scala.Array[scala.Float] = scala.Array[scala.Float](0)
  def getTimeline(): scala.Array[scala.Float] = {
    return this.timeline
  }
  def setTimeline(timeline: scala.Array[scala.Float]): scala.Unit = {
    this.timeline = timeline
  }
  def getColors(): scala.Array[scala.Float] = {
    return this.colors
  }
  def setColors(colors: scala.Array[scala.Float]): scala.Unit = {
    this.colors = colors
  }
  def getColor(percent: scala.Float): scala.Array[scala.Float] = {
    this.getColor(percent, GradientColorValue.temp, 0)
    return GradientColorValue.temp
  }
  def getColor(percent: scala.Float, out: scala.Array[scala.Float], index: scala.Int): scala.Unit = {
    var startIndex: scala.Int = 0
    var endIndex: scala.Int = -1
    val timeline: scala.Array[scala.Float] = this.timeline
    val n: scala.Int = timeline.length;
    { var i: scala.Int = 1; while (i < n) { {
      val t: scala.Float = timeline(i)
      if (t > percent) {
        endIndex = i
        /* break */ ()
      } else ()
      startIndex = i
    }; i = i + 1 } }
    val startTime: scala.Float = timeline(startIndex)
    startIndex = startIndex * 3
    val r1: scala.Float = this.colors(startIndex)
    val g1: scala.Float = this.colors(startIndex + 1)
    val b1: scala.Float = this.colors(startIndex + 2)
    if (endIndex == (-1)) {
      out(index) = r1
      out(index + 1) = g1
      out(index + 2) = b1
      return
    } else ()
    val factor: scala.Float = (percent - startTime) / (timeline(endIndex) - startTime)
    endIndex = endIndex * 3
    out(index) = r1 + ((this.colors(endIndex) - r1) * factor)
    out(index + 1) = g1 + ((this.colors(endIndex + 1) - g1) * factor)
    out(index + 2) = b1 + ((this.colors(endIndex + 2) - b1) * factor)
  }
  @java.lang.Override
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("colors", this.colors)
    json.writeValue("timeline", this.timeline)
  }
  @java.lang.Override
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.colors = json.readValue("colors", classOf[scala.Array[scala.Float]], jsonData)
    this.timeline = json.readValue("timeline", classOf[scala.Array[scala.Float]], jsonData)
  }
  def load(value: GradientColorValue): scala.Unit = {
    super.load(value)
    this.colors = new scala.Array[scala.Float](value.colors.length)
    java.lang.System.arraycopy(value.colors, 0, this.colors, 0, this.colors.length)
    this.timeline = new scala.Array[scala.Float](value.timeline.length)
    java.lang.System.arraycopy(value.timeline, 0, this.timeline, 0, this.timeline.length)
  }
}
object GradientColorValue {
  private var temp: scala.Array[scala.Float] = new scala.Array[scala.Float](3)
}