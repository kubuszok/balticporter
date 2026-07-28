package com.badlogic.gdx.graphics.g3d.environment

class DirectionalLight extends com.badlogic.gdx.graphics.g3d.environment.BaseLight[DirectionalLight] {
  final val direction: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  def setDirection(directionX: scala.Float, directionY: scala.Float, directionZ: scala.Float): DirectionalLight = {
    this.direction.set(directionX, directionY, directionZ)
    return this
  }
  def setDirection(direction: com.badlogic.gdx.math.Vector3): DirectionalLight = {
    this.direction.set(direction)
    return this
  }
  def set(copyFrom: DirectionalLight): DirectionalLight = {
    return this.set(copyFrom.color, copyFrom.direction)
  }
  def set(color: com.badlogic.gdx.graphics.Color, direction: com.badlogic.gdx.math.Vector3): DirectionalLight = {
    if (color != null) {
      this.color.set(color)
    } else ()
    if (direction != null) {
      this.direction.set(direction).nor()
    } else ()
    return this
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float, direction: com.badlogic.gdx.math.Vector3): DirectionalLight = {
    this.color.set(r, g, b, 1.0f)
    if (direction != null) {
      this.direction.set(direction).nor()
    } else ()
    return this
  }
  def set(color: com.badlogic.gdx.graphics.Color, dirX: scala.Float, dirY: scala.Float, dirZ: scala.Float): DirectionalLight = {
    if (color != null) {
      this.color.set(color)
    } else ()
    this.direction.set(dirX, dirY, dirZ).nor()
    return this
  }
  def set(r: scala.Float, g: scala.Float, b: scala.Float, dirX: scala.Float, dirY: scala.Float, dirZ: scala.Float): DirectionalLight = {
    this.color.set(r, g, b, 1.0f)
    this.direction.set(dirX, dirY, dirZ).nor()
    return this
  }
  @java.lang.Override
  override def equals(arg0: java.lang.Object): scala.Boolean = {
    return arg0.isInstanceOf[DirectionalLight] && this.equals(arg0.asInstanceOf[DirectionalLight].asInstanceOf[DirectionalLight])
  }
  def equals(other: DirectionalLight): scala.Boolean = {
    return (other != null) && ((other == this) || (color.equals(other.color) && this.direction.equals(other.direction)))
  }
}