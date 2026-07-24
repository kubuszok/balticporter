package com.badlogic.gdx.graphics.g3d.environment

class PointLight extends com.badlogic.gdx.graphics.g3d.environment.BaseLight[PointLight] {
  final val position: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var intensity: scala.Float = 0.0f
  def setPosition(positionX: scala.Float, positionY: scala.Float, positionZ: scala.Float): PointLight = {
    this.position.set(positionX, positionY, positionZ)
    return this
  }
  def setPosition(position: com.badlogic.gdx.math.Vector3): PointLight = {
    this.position.set(position)
    return this
  }
  def setIntensity(intensity: scala.Float): PointLight = {
    this.intensity = intensity
    return this
  }
  def set(copyFrom: PointLight): PointLight = {
    return this.set(copyFrom.color, copyFrom.position, copyFrom.intensity)
  }
  def set(color: com.badlogic.gdx.graphics.Color, position: com.badlogic.gdx.math.Vector3, intensity: scala.Float): PointLight = {
    if (color != null) {
      this.color.set(color)
    } else ()
    if (position != null) {
      this.position.set(position)
    } else ()
    this.intensity = intensity
    return this
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float, position: com.badlogic.gdx.math.Vector3, intensity: scala.Float): PointLight = {
    this.color.set(r, g, b, 1.0f)
    if (position != null) {
      this.position.set(position)
    } else ()
    this.intensity = intensity
    return this
  }
  def set(color: com.badlogic.gdx.graphics.Color, x: scala.Float, y: scala.Float, z: scala.Float, intensity: scala.Float): PointLight = {
    if (color != null) {
      this.color.set(color)
    } else ()
    this.position.set(x, y, z)
    this.intensity = intensity
    return this
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float, x: scala.Float, y: scala.Float, z: scala.Float, intensity: scala.Float): PointLight = {
    this.color.set(r, g, b, 1.0f)
    this.position.set(x, y, z)
    this.intensity = intensity
    return this
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    return obj.isInstanceOf[PointLight] && this.equals(obj.asInstanceOf[PointLight].asInstanceOf[PointLight])
  }
  def equals(other: PointLight): scala.Boolean = {
    return (other != null) && ((other == this) || ((color.equals(other.color) && this.position.equals(other.position)) && (this.intensity == other.intensity)))
  }
}