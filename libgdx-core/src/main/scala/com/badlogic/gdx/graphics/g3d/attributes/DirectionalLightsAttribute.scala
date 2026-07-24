package com.badlogic.gdx.graphics.g3d.attributes

class DirectionalLightsAttribute extends com.badlogic.gdx.graphics.g3d.Attribute(DirectionalLightsAttribute.Type) {
  var lights: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight]]
  def this(copyFrom: DirectionalLightsAttribute) = {
    this()
    this.lights.addAll(copyFrom.lights)
  }
  this.lights = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight](1)
  def copy(): DirectionalLightsAttribute = {
    return new DirectionalLightsAttribute(this)
  }
  def hashCode(): scala.Int = {
    var result: scala.Int = super.hashCode()
    for (light <- this.lights) {
      result = (1229 * result) + (if (light == null) 0 else light.hashCode())
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
object DirectionalLightsAttribute {
  export com.badlogic.gdx.graphics.g3d.Attribute.{Alias => _, Type => _, is => _, *}
  final val Alias: java.lang.String = "directionalLights"
  final val Type: scala.Long = com.badlogic.gdx.graphics.g3d.Attribute.register(DirectionalLightsAttribute.Alias)
  final def is(mask: scala.Long): scala.Boolean = {
    return (mask & DirectionalLightsAttribute.Type) == mask
  }
}