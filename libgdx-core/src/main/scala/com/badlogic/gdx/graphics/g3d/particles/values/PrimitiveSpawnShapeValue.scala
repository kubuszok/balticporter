package com.badlogic.gdx.graphics.g3d.particles.values

abstract class PrimitiveSpawnShapeValue extends com.badlogic.gdx.graphics.g3d.particles.values.SpawnShapeValue {
  var spawnWidthValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
  var spawnHeightValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
  var spawnDepthValue: com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue]
  var spawnWidth: scala.Float = 0.0f
  var spawnWidthDiff: scala.Float = 0.0f
  var spawnHeight: scala.Float = 0.0f
  var spawnHeightDiff: scala.Float = 0.0f
  var spawnDepth: scala.Float = 0.0f
  var spawnDepthDiff: scala.Float = 0.0f
  var edges: scala.Boolean = false
  def this(value: PrimitiveSpawnShapeValue) = {
    this()
    this.spawnWidthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnHeightValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
    this.spawnDepthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  }
  this.spawnWidthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  this.spawnHeightValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  this.spawnDepthValue = new com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue()
  def setActive(active: scala.Boolean): scala.Unit = {
    super.setActive(active)
    this.spawnWidthValue.setActive(true)
    this.spawnHeightValue.setActive(true)
    this.spawnDepthValue.setActive(true)
  }
  def isEdges(): scala.Boolean = {
    return this.edges
  }
  def setEdges(edges: scala.Boolean): scala.Unit = {
    this.edges = edges
  }
  def getSpawnWidth(): com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = {
    return this.spawnWidthValue
  }
  def getSpawnHeight(): com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = {
    return this.spawnHeightValue
  }
  def getSpawnDepth(): com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue = {
    return this.spawnDepthValue
  }
  def setDimensions(width: scala.Float, height: scala.Float, depth: scala.Float): scala.Unit = {
    this.spawnWidthValue.setHigh(width)
    this.spawnHeightValue.setHigh(height)
    this.spawnDepthValue.setHigh(depth)
  }
  def start(): scala.Unit = {
    this.spawnWidth = this.spawnWidthValue.newLowValue()
    this.spawnWidthDiff = this.spawnWidthValue.newHighValue()
    if (!this.spawnWidthValue.isRelative()) {
      this.spawnWidthDiff = this.spawnWidthDiff - this.spawnWidth
    } else ()
    this.spawnHeight = this.spawnHeightValue.newLowValue()
    this.spawnHeightDiff = this.spawnHeightValue.newHighValue()
    if (!this.spawnHeightValue.isRelative()) {
      this.spawnHeightDiff = this.spawnHeightDiff - this.spawnHeight
    } else ()
    this.spawnDepth = this.spawnDepthValue.newLowValue()
    this.spawnDepthDiff = this.spawnDepthValue.newHighValue()
    if (!this.spawnDepthValue.isRelative()) {
      this.spawnDepthDiff = this.spawnDepthDiff - this.spawnDepth
    } else ()
  }
  def load(value: com.badlogic.gdx.graphics.g3d.particles.values.ParticleValue): scala.Unit = {
    super.load(value)
    val shape: PrimitiveSpawnShapeValue = value.asInstanceOf[PrimitiveSpawnShapeValue]
    this.edges = shape.edges
    this.spawnWidthValue.load(shape.spawnWidthValue)
    this.spawnHeightValue.load(shape.spawnHeightValue)
    this.spawnDepthValue.load(shape.spawnDepthValue)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    super.write(json)
    json.writeValue("spawnWidthValue", this.spawnWidthValue)
    json.writeValue("spawnHeightValue", this.spawnHeightValue)
    json.writeValue("spawnDepthValue", this.spawnDepthValue)
    json.writeValue("edges", this.edges.asInstanceOf[java.lang.Boolean])
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    super.read(json, jsonData)
    this.spawnWidthValue = json.readValue("spawnWidthValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
    this.spawnHeightValue = json.readValue("spawnHeightValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
    this.spawnDepthValue = json.readValue("spawnDepthValue", classOf[com.badlogic.gdx.graphics.g3d.particles.values.ScaledNumericValue], jsonData)
    this.edges = json.readValue("edges", classOf[scala.Boolean], jsonData)
  }
}
object PrimitiveSpawnShapeValue {
  final val TMP_V1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  sealed abstract class SpawnSide {
    def name(): java.lang.String = this.toString()
  }
  object SpawnSide {
    case object both extends SpawnSide
    case object top extends SpawnSide
    case object bottom extends SpawnSide
    def values(): scala.Array[SpawnSide] = scala.Array(both, top, bottom)
    def valueOf(name: java.lang.String): SpawnSide = name match {
      case "both" => both
      case "top" => top
      case "bottom" => bottom
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}