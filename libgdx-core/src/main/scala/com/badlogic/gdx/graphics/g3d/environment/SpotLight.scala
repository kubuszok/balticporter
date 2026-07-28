package com.badlogic.gdx.graphics.g3d.environment

class SpotLight extends com.badlogic.gdx.graphics.g3d.environment.BaseLight[SpotLight] {
  final val position: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val direction: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var intensity: scala.Float = 0.0f
  var cutoffAngle: scala.Float = 0.0f
  var exponent: scala.Float = 0.0f
  def setPosition(positionX: scala.Float, positionY: scala.Float, positionZ: scala.Float): SpotLight = {
    this.position.set(positionX, positionY, positionZ)
    return this
  }
  def setPosition(position: com.badlogic.gdx.math.Vector3): SpotLight = {
    this.position.set(position)
    return this
  }
  def setDirection(directionX: scala.Float, directionY: scala.Float, directionZ: scala.Float): SpotLight = {
    this.direction.set(directionX, directionY, directionZ)
    return this
  }
  def setDirection(direction: com.badlogic.gdx.math.Vector3): SpotLight = {
    this.direction.set(direction)
    return this
  }
  def setIntensity(intensity: scala.Float): SpotLight = {
    this.intensity = intensity
    return this
  }
  def setCutoffAngle(cutoffAngle: scala.Float): SpotLight = {
    this.cutoffAngle = cutoffAngle
    return this
  }
  def setExponent(exponent: scala.Float): SpotLight = {
    this.exponent = exponent
    return this
  }
  def set(copyFrom: SpotLight): SpotLight = {
    return this.set(copyFrom.color, copyFrom.position, copyFrom.direction, copyFrom.intensity, copyFrom.cutoffAngle, copyFrom.exponent)
  }
  def set(color: com.badlogic.gdx.graphics.Color, position: com.badlogic.gdx.math.Vector3, direction: com.badlogic.gdx.math.Vector3, intensity: scala.Float, cutoffAngle: scala.Float, exponent: scala.Float): SpotLight = {
    if (color != null) {
      this.color.set(color)
    } else ()
    if (position != null) {
      this.position.set(position)
    } else ()
    if (direction != null) {
      this.direction.set(direction).nor()
    } else ()
    this.intensity = intensity
    this.cutoffAngle = cutoffAngle
    this.exponent = exponent
    return this
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float, position: com.badlogic.gdx.math.Vector3, direction: com.badlogic.gdx.math.Vector3, intensity: scala.Float, cutoffAngle: scala.Float, exponent: scala.Float): SpotLight = {
    this.color.set(r, g, b, 1.0f)
    if (position != null) {
      this.position.set(position)
    } else ()
    if (direction != null) {
      this.direction.set(direction).nor()
    } else ()
    this.intensity = intensity
    this.cutoffAngle = cutoffAngle
    this.exponent = exponent
    return this
  }
  def set(color: com.badlogic.gdx.graphics.Color, posX: scala.Float, posY: scala.Float, posZ: scala.Float, dirX: scala.Float, dirY: scala.Float, dirZ: scala.Float, intensity: scala.Float, cutoffAngle: scala.Float, exponent: scala.Float): SpotLight = {
    if (color != null) {
      this.color.set(color)
    } else ()
    this.position.set(posX, posY, posZ)
    this.direction.set(dirX, dirY, dirZ).nor()
    this.intensity = intensity
    this.cutoffAngle = cutoffAngle
    this.exponent = exponent
    return this
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float, posX: scala.Float, posY: scala.Float, posZ: scala.Float, dirX: scala.Float, dirY: scala.Float, dirZ: scala.Float, intensity: scala.Float, cutoffAngle: scala.Float, exponent: scala.Float): SpotLight = {
    this.color.set(r, g, b, 1.0f)
    this.position.set(posX, posY, posZ)
    this.direction.set(dirX, dirY, dirZ).nor()
    this.intensity = intensity
    this.cutoffAngle = cutoffAngle
    this.exponent = exponent
    return this
  }
  def setTarget(target: com.badlogic.gdx.math.Vector3): SpotLight = {
    this.direction.set(target).sub(this.position).nor()
    return this
  }
  @java.lang.Override
  def equals(obj: java.lang.Object): scala.Boolean = {
    return obj.isInstanceOf[SpotLight] && this.equals(obj.asInstanceOf[SpotLight].asInstanceOf[SpotLight])
  }
  def equals(other: SpotLight): scala.Boolean = {
    return (other != null) && ((other == this) || (((((color.equals(other.color) && this.position.equals(other.position)) && this.direction.equals(other.direction)) && com.badlogic.gdx.math.MathUtils.isEqual(this.intensity, other.intensity)) && com.badlogic.gdx.math.MathUtils.isEqual(this.cutoffAngle, other.cutoffAngle)) && com.badlogic.gdx.math.MathUtils.isEqual(this.exponent, other.exponent)))
  }
}