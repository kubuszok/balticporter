package com.badlogic.gdx.graphics.g3d.attributes

class PointLightsAttribute extends com.badlogic.gdx.graphics.g3d.Attribute(PointLightsAttribute.Type) {
  var lights: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight]]
  def this(copyFrom: PointLightsAttribute) = {
    this()
    this.lights.addAll(copyFrom.lights.asInstanceOf[com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.graphics.g3d.environment.PointLight]])
  }
  this.lights = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight](1)
  @java.lang.Override
  def copy(): PointLightsAttribute = {
    return new PointLightsAttribute(this)
  }
  @java.lang.Override
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    for (light <- this.lights) {
      result = (1231 * result) + (if (light == null) 0 else light.hashCode())
    }
    return result
  }
  @java.lang.Override
  def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return if (`type` < o.`type`) -1 else 1
    } else ()
    return 0
  }
}
object PointLightsAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{Alias => _, Type => _, is => _, *}
  final val Alias: java.lang.String = "pointLights"
  final val Type: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(PointLightsAttribute.Alias)
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & PointLightsAttribute.Type) == mask
  }
}