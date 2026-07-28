package com.badlogic.gdx.graphics.g3d.attributes

class SpotLightsAttribute extends com.badlogic.gdx.graphics.g3d.Attribute(SpotLightsAttribute.Type) {
  var lights: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight]]
  def this(copyFrom: SpotLightsAttribute) = {
    this()
    this.lights.addAll(copyFrom.lights.asInstanceOf[com.badlogic.gdx.utils.Array[? <: com.badlogic.gdx.graphics.g3d.environment.SpotLight]])
  }
  this.lights = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight](1)
  def copy(): SpotLightsAttribute = {
    return new SpotLightsAttribute(this)
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    for (light <- this.lights) {
      result = (1237 * result) + (if (light == null) 0 else light.hashCode())
    }
    return result
  }
  def compareTo(o: com.badlogic.gdx.graphics.g3d.Attribute): scala.Int = {
    if (`type` != o.`type`) {
      return if (`type` < o.`type`) -1 else 1
    } else ()
    return 0
  }
}
object SpotLightsAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{Alias => _, Type => _, is => _, *}
  final val Alias: java.lang.String = "spotLights"
  final val Type: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(SpotLightsAttribute.Alias)
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & SpotLightsAttribute.Type) == mask
  }
}