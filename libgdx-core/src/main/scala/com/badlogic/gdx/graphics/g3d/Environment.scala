package com.badlogic.gdx.graphics.g3d

class Environment extends com.badlogic.gdx.graphics.g3d.Attributes {
  var shadowMap: com.badlogic.gdx.graphics.g3d.environment.ShadowMap = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.ShadowMap]
  def add(lights: scala.Array[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]]): Environment = {
    for (light <- lights) {
      this.add(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]])
    }
    return this
  }
  def add(lights: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]]): Environment = {
    for (light <- lights) {
      this.add(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]])
    }
    return this
  }
  def add(light: com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]): Environment = {
    if (light.isInstanceOf[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight]) {
      this.add(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight])
    } else {
      if (light.isInstanceOf[com.badlogic.gdx.graphics.g3d.environment.PointLight]) {
        this.add(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.PointLight])
      } else {
        if (light.isInstanceOf[com.badlogic.gdx.graphics.g3d.environment.SpotLight]) {
          this.add(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.SpotLight])
        } else {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Unknown light type")
        }
      }
    }
    return this
  }
  def add(light: com.badlogic.gdx.graphics.g3d.environment.DirectionalLight): Environment = {
    var dirLights: com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute = this.get(com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute]
    if (dirLights == null) {
      this.set({
        dirLights = new com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute()
        dirLights
      })
    } else ()
    dirLights.lights.add(light)
    return this
  }
  def add(light: com.badlogic.gdx.graphics.g3d.environment.PointLight): Environment = {
    var pointLights: com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute = this.get(com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute]
    if (pointLights == null) {
      this.set({
        pointLights = new com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute()
        pointLights
      })
    } else ()
    pointLights.lights.add(light)
    return this
  }
  def add(light: com.badlogic.gdx.graphics.g3d.environment.SpotLight): Environment = {
    var spotLights: com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute = this.get(com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute]
    if (spotLights == null) {
      this.set({
        spotLights = new com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute()
        spotLights
      })
    } else ()
    spotLights.lights.add(light)
    return this
  }
  override def remove(lights: scala.Array[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]]): Environment = {
    for (light <- lights) {
      this.remove(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]])
    }
    return this
  }
  override def remove(lights: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]]): Environment = {
    for (light <- lights) {
      this.remove(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]])
    }
    return this
  }
  override def remove(light: com.badlogic.gdx.graphics.g3d.environment.BaseLight[?]): Environment = {
    if (light.isInstanceOf[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight]) {
      this.remove(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight])
    } else {
      if (light.isInstanceOf[com.badlogic.gdx.graphics.g3d.environment.PointLight]) {
        this.remove(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.PointLight])
      } else {
        if (light.isInstanceOf[com.badlogic.gdx.graphics.g3d.environment.SpotLight]) {
          this.remove(light.asInstanceOf[com.badlogic.gdx.graphics.g3d.environment.SpotLight])
        } else {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Unknown light type")
        }
      }
    }
    return this
  }
  override def remove(light: com.badlogic.gdx.graphics.g3d.environment.DirectionalLight): Environment = {
    if (this.has(com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type)) {
      val dirLights: com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute = this.get(com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute]
      dirLights.lights.removeValue(light, false)
      if (dirLights.lights.size == 0) {
        this.remove(com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type)
      } else ()
    } else ()
    return this
  }
  override def remove(light: com.badlogic.gdx.graphics.g3d.environment.PointLight): Environment = {
    if (this.has(com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type)) {
      val pointLights: com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute = this.get(com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute]
      pointLights.lights.removeValue(light, false)
      if (pointLights.lights.size == 0) {
        this.remove(com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type)
      } else ()
    } else ()
    return this
  }
  override def remove(light: com.badlogic.gdx.graphics.g3d.environment.SpotLight): Environment = {
    if (this.has(com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute.Type)) {
      val spotLights: com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute = this.get(com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute]
      spotLights.lights.removeValue(light, false)
      if (spotLights.lights.size == 0) {
        this.remove(com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute.Type)
      } else ()
    } else ()
    return this
  }
}