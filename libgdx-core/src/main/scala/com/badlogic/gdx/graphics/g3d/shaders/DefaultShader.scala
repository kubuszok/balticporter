package com.badlogic.gdx.graphics.g3d.shaders

class DefaultShader(renderable$p: com.badlogic.gdx.graphics.g3d.Renderable, config$p: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config, shaderProgram: com.badlogic.gdx.graphics.glutils.ShaderProgram) extends com.badlogic.gdx.graphics.g3d.shaders.BaseShader {
  var u_projTrans: scala.Int = 0
  var u_viewTrans: scala.Int = 0
  var u_projViewTrans: scala.Int = 0
  var u_cameraPosition: scala.Int = 0
  var u_cameraDirection: scala.Int = 0
  var u_cameraUp: scala.Int = 0
  var u_cameraNearFar: scala.Int = 0
  var u_time: scala.Int = 0
  var u_worldTrans: scala.Int = 0
  var u_viewWorldTrans: scala.Int = 0
  var u_projViewWorldTrans: scala.Int = 0
  var u_normalMatrix: scala.Int = 0
  var u_bones: scala.Int = 0
  var u_shininess: scala.Int = 0
  var u_opacity: scala.Int = 0
  var u_diffuseColor: scala.Int = 0
  var u_diffuseTexture: scala.Int = 0
  var u_diffuseUVTransform: scala.Int = 0
  var u_specularColor: scala.Int = 0
  var u_specularTexture: scala.Int = 0
  var u_specularUVTransform: scala.Int = 0
  var u_emissiveColor: scala.Int = 0
  var u_emissiveTexture: scala.Int = 0
  var u_emissiveUVTransform: scala.Int = 0
  var u_reflectionColor: scala.Int = 0
  var u_reflectionTexture: scala.Int = 0
  var u_reflectionUVTransform: scala.Int = 0
  var u_normalTexture: scala.Int = 0
  var u_normalUVTransform: scala.Int = 0
  var u_ambientTexture: scala.Int = 0
  var u_ambientUVTransform: scala.Int = 0
  var u_alphaTest: scala.Int = 0
  var u_ambientCubemap: scala.Int = 0
  var u_environmentCubemap: scala.Int = 0
  final val u_dirLights0color: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_dirLights[0].color"))
  final val u_dirLights0direction: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_dirLights[0].direction"))
  final val u_dirLights1color: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_dirLights[1].color"))
  final val u_pointLights0color: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_pointLights[0].color"))
  final val u_pointLights0position: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_pointLights[0].position"))
  final val u_pointLights0intensity: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_pointLights[0].intensity"))
  final val u_pointLights1color: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_pointLights[1].color"))
  final val u_spotLights0color: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights[0].color"))
  final val u_spotLights0position: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights[0].position"))
  final val u_spotLights0intensity: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights[0].intensity"))
  final val u_spotLights0direction: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights[0].direction"))
  final val u_spotLights0cutoffAngle: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights[0].cutoffAngle"))
  final val u_spotLights0exponent: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights[0].exponent"))
  final val u_spotLights1color: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights[1].color"))
  final val u_fogColor: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_fogColor"))
  final val u_shadowMapProjViewTrans: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_shadowMapProjViewTrans"))
  final val u_shadowTexture: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_shadowTexture"))
  final val u_shadowPCFOffset: scala.Int = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_shadowPCFOffset"))
  var dirLightsLoc: scala.Int = 0
  var dirLightsColorOffset: scala.Int = 0
  var dirLightsDirectionOffset: scala.Int = 0
  var dirLightsSize: scala.Int = 0
  var pointLightsLoc: scala.Int = 0
  var pointLightsColorOffset: scala.Int = 0
  var pointLightsPositionOffset: scala.Int = 0
  var pointLightsIntensityOffset: scala.Int = 0
  var pointLightsSize: scala.Int = 0
  var spotLightsLoc: scala.Int = 0
  var spotLightsColorOffset: scala.Int = 0
  var spotLightsPositionOffset: scala.Int = 0
  var spotLightsDirectionOffset: scala.Int = 0
  var spotLightsIntensityOffset: scala.Int = 0
  var spotLightsCutoffAngleOffset: scala.Int = 0
  var spotLightsExponentOffset: scala.Int = 0
  var spotLightsSize: scala.Int = 0
  var lighting: scala.Boolean = false
  var environmentCubemap: scala.Boolean = false
  var shadowMap: scala.Boolean = false
  final val ambientCubemap: com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap = new com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap()
  var directionalLights: scala.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight]]
  var pointLights: scala.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight]]
  var spotLights: scala.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight]]
  private var renderable: com.badlogic.gdx.graphics.g3d.Renderable = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Renderable]
  var attributesMask: scala.Long = 0L
  private var vertexMask: scala.Long = 0L
  private var textureCoordinates: scala.Int = 0
  private var boneWeightsLocations: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  var config: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config]
  private final val normalMatrix: com.badlogic.gdx.math.Matrix3 = new com.badlogic.gdx.math.Matrix3()
  private var time: scala.Float = 0.0f
  private var lightsSet: scala.Boolean = false
  private final val tmpV1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  val attributes$p: com.badlogic.gdx.graphics.g3d.Attributes = DefaultShader.combineAttributes(renderable$p)
  val boneWeights: scala.Int = renderable$p.meshPart.mesh.getVertexAttributes().getBoneWeights()
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config, prefix: java.lang.String, vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
    this(renderable, config, new com.badlogic.gdx.graphics.glutils.ShaderProgram(prefix + vertexShader, prefix + fragmentShader))
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config, prefix: java.lang.String) = {
    this(renderable, config, prefix, if (config.vertexShader != null) config.vertexShader else DefaultShader.getDefaultVertexShader(), if (config.fragmentShader != null) config.fragmentShader else DefaultShader.getDefaultFragmentShader())
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config) = {
    this(renderable, config, DefaultShader.createPrefix(renderable, config))
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable) = {
    this(renderable, new com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config())
  }
  this.config = config$p
  this.program = shaderProgram
  this.lighting = renderable$p.environment != null
  this.environmentCubemap = attributes$p.has(com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute.EnvironmentMap) || (this.lighting && attributes$p.has(com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute.EnvironmentMap))
  this.shadowMap = this.lighting && (renderable$p.environment.shadowMap != null)
  this.renderable = renderable$p
  this.attributesMask = attributes$p.getMask() | DefaultShader.optionalAttributes
  this.vertexMask = renderable$p.meshPart.mesh.getVertexAttributes().getMaskWithSizePacked()
  this.textureCoordinates = renderable$p.meshPart.mesh.getVertexAttributes().getTextureCoordinates()
  this.directionalLights = new scala.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight](if (this.lighting && (config$p.numDirectionalLights > 0)) config$p.numDirectionalLights else 0);
  { var i: scala.Int = 0; while (i < this.directionalLights.length) { {
    this.directionalLights(i) = new com.badlogic.gdx.graphics.g3d.environment.DirectionalLight()
  }; i = i + 1 } }
  this.pointLights = new scala.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight](if (this.lighting && (config$p.numPointLights > 0)) config$p.numPointLights else 0);
  { var i: scala.Int = 0; while (i < this.pointLights.length) { {
    this.pointLights(i) = new com.badlogic.gdx.graphics.g3d.environment.PointLight()
  }; i = i + 1 } }
  this.spotLights = new scala.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight](if (this.lighting && (config$p.numSpotLights > 0)) config$p.numSpotLights else 0);
  { var i: scala.Int = 0; while (i < this.spotLights.length) { {
    this.spotLights(i) = new com.badlogic.gdx.graphics.g3d.environment.SpotLight()
  }; i = i + 1 } }
  if ((!config$p.ignoreUnimplemented) && ((DefaultShader.implementedFlags & this.attributesMask) != this.attributesMask)) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException(("Some attributes not implemented yet (" + this.attributesMask) + ")")
  } else ()
  if ((renderable$p.bones != null) && (renderable$p.bones.length > config$p.numBones)) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException((("too many bones: " + renderable$p.bones.length) + ", max configured: ") + config$p.numBones)
  } else ()
  if (boneWeights > config$p.numBoneWeights) {
    throw new com.badlogic.gdx.utils.GdxRuntimeException((("too many bone weights: " + boneWeights) + ", max configured: ") + config$p.numBoneWeights)
  } else ()
  if (renderable$p.bones != null) {
    this.boneWeightsLocations = new scala.Array[scala.Int](config$p.numBoneWeights)
  } else ()
  this.u_projTrans = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.projTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.projTrans)
  this.u_viewTrans = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.viewTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.viewTrans)
  this.u_projViewTrans = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.projViewTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.projViewTrans)
  this.u_cameraPosition = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.cameraPosition, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.cameraPosition)
  this.u_cameraDirection = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.cameraDirection, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.cameraDirection)
  this.u_cameraUp = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.cameraUp, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.cameraUp)
  this.u_cameraNearFar = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.cameraNearFar, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.cameraNearFar)
  this.u_time = this.register(new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_time"))
  this.u_worldTrans = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.worldTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.worldTrans)
  this.u_viewWorldTrans = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.viewWorldTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.viewWorldTrans)
  this.u_projViewWorldTrans = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.projViewWorldTrans, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.projViewWorldTrans)
  this.u_normalMatrix = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.normalMatrix, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.normalMatrix)
  this.u_bones = if ((renderable$p.bones != null) && (config$p.numBones > 0)) this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.bones, new com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.Bones(config$p.numBones)) else -1
  this.u_shininess = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.shininess, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.shininess)
  this.u_opacity = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.opacity)
  this.u_diffuseColor = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.diffuseColor, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.diffuseColor)
  this.u_diffuseTexture = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.diffuseTexture, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.diffuseTexture)
  this.u_diffuseUVTransform = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.diffuseUVTransform, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.diffuseUVTransform)
  this.u_specularColor = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.specularColor, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.specularColor)
  this.u_specularTexture = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.specularTexture, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.specularTexture)
  this.u_specularUVTransform = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.specularUVTransform, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.specularUVTransform)
  this.u_emissiveColor = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.emissiveColor, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.emissiveColor)
  this.u_emissiveTexture = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.emissiveTexture, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.emissiveTexture)
  this.u_emissiveUVTransform = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.emissiveUVTransform, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.emissiveUVTransform)
  this.u_reflectionColor = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.reflectionColor, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.reflectionColor)
  this.u_reflectionTexture = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.reflectionTexture, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.reflectionTexture)
  this.u_reflectionUVTransform = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.reflectionUVTransform, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.reflectionUVTransform)
  this.u_normalTexture = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.normalTexture, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.normalTexture)
  this.u_normalUVTransform = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.normalUVTransform, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.normalUVTransform)
  this.u_ambientTexture = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.ambientTexture, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.ambientTexture)
  this.u_ambientUVTransform = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.ambientUVTransform, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.ambientUVTransform)
  this.u_alphaTest = this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.alphaTest)
  this.u_ambientCubemap = if (this.lighting) this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.ambientCube, new com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.ACubemap(config$p.numDirectionalLights, config$p.numPointLights)) else -1
  this.u_environmentCubemap = if (this.environmentCubemap) this.register(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Inputs.environmentCubemap, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.environmentCubemap) else -1
  def init(): scala.Unit = {
    var program: com.badlogic.gdx.graphics.glutils.ShaderProgram = this.program
    this.program = null
    this.init(program, this.renderable)
    this.renderable = null
    this.dirLightsLoc = this.loc(this.u_dirLights0color)
    this.dirLightsColorOffset = this.loc(this.u_dirLights0color) - this.dirLightsLoc
    this.dirLightsDirectionOffset = this.loc(this.u_dirLights0direction) - this.dirLightsLoc
    this.dirLightsSize = this.loc(this.u_dirLights1color) - this.dirLightsLoc
    if (this.dirLightsSize < 0) {
      this.dirLightsSize = 0
    } else ()
    this.pointLightsLoc = this.loc(this.u_pointLights0color)
    this.pointLightsColorOffset = this.loc(this.u_pointLights0color) - this.pointLightsLoc
    this.pointLightsPositionOffset = this.loc(this.u_pointLights0position) - this.pointLightsLoc
    this.pointLightsIntensityOffset = if (this.has(this.u_pointLights0intensity)) this.loc(this.u_pointLights0intensity) - this.pointLightsLoc else -1
    this.pointLightsSize = this.loc(this.u_pointLights1color) - this.pointLightsLoc
    if (this.pointLightsSize < 0) {
      this.pointLightsSize = 0
    } else ()
    this.spotLightsLoc = this.loc(this.u_spotLights0color)
    this.spotLightsColorOffset = this.loc(this.u_spotLights0color) - this.spotLightsLoc
    this.spotLightsPositionOffset = this.loc(this.u_spotLights0position) - this.spotLightsLoc
    this.spotLightsDirectionOffset = this.loc(this.u_spotLights0direction) - this.spotLightsLoc
    this.spotLightsIntensityOffset = if (this.has(this.u_spotLights0intensity)) this.loc(this.u_spotLights0intensity) - this.spotLightsLoc else -1
    this.spotLightsCutoffAngleOffset = this.loc(this.u_spotLights0cutoffAngle) - this.spotLightsLoc
    this.spotLightsExponentOffset = this.loc(this.u_spotLights0exponent) - this.spotLightsLoc
    this.spotLightsSize = this.loc(this.u_spotLights1color) - this.spotLightsLoc
    if (this.spotLightsSize < 0) {
      this.spotLightsSize = 0
    } else ()
    if (this.boneWeightsLocations != null) {
      { var i: scala.Int = 0; while (i < this.boneWeightsLocations.length) { {
        this.boneWeightsLocations(i) = program.getAttributeLocation(com.badlogic.gdx.graphics.glutils.ShaderProgram.BONEWEIGHT_ATTRIBUTE + i)
      }; i = i + 1 } }
    } else ()
  }
  def canRender(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Boolean = {
    if (renderable.bones != null) {
      if (renderable.bones.length > this.config.numBones) {
        return false
      } else ()
      if (renderable.meshPart.mesh.getVertexAttributes().getBoneWeights() > this.config.numBoneWeights) {
        return false
      } else ()
    } else ()
    if (renderable.meshPart.mesh.getVertexAttributes().getTextureCoordinates() != this.textureCoordinates) {
      return false
    } else ()
    val renderableMask: scala.Long = DefaultShader.combineAttributeMasks(renderable)
    return ((this.attributesMask == (renderableMask | DefaultShader.optionalAttributes)) && (this.vertexMask == renderable.meshPart.mesh.getVertexAttributes().getMaskWithSizePacked())) && ((renderable.environment != null) == this.lighting)
  }
  def compareTo(other: com.badlogic.gdx.graphics.g3d.Shader): scala.Int = {
    if (other == null) {
      return -1
    } else ()
    if (other == this) {
      return 0
    } else ()
    return 0
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    return obj.isInstanceOf[DefaultShader] && this.equals(obj.asInstanceOf[DefaultShader].asInstanceOf[DefaultShader])
  }
  def equals(obj: DefaultShader): scala.Boolean = {
    return obj == this
  }
  def begin(camera: com.badlogic.gdx.graphics.Camera, context: com.badlogic.gdx.graphics.g3d.utils.RenderContext): scala.Unit = {
    super.begin(camera, context)
    for (dirLight <- this.directionalLights) {
      dirLight.set(0, 0, 0, 0, -1, 0)
    }
    for (pointLight <- this.pointLights) {
      pointLight.set(0, 0, 0, 0, 0, 0, 0)
    }
    for (spotLight <- this.spotLights) {
      spotLight.set(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 1, 0)
    }
    this.lightsSet = false
    if (this.has(this.u_time)) {
      this.set(this.u_time, {
        this.time = this.time + com.badlogic.gdx.Gdx.graphics.getDeltaTime()
        this.time
      })
    } else ()
    if (this.boneWeightsLocations != null) {
      for (location <- this.boneWeightsLocations) {
        if (location >= 0) {
          com.badlogic.gdx.Gdx.gl.glVertexAttrib2f(location, 0, 0)
        } else ()
      }
    } else ()
  }
  def render(renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
    if (!combinedAttributes.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)) {
      context.setBlending(false, com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA)
    } else ()
    this.bindMaterial(combinedAttributes)
    if (this.lighting) {
      this.bindLights(renderable, combinedAttributes)
    } else ()
    super.render(renderable, combinedAttributes)
  }
  def `end`(): scala.Unit = {
    super.`end`()
  }
  def bindMaterial(attributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
    var cullFace: scala.Int = if (this.config.defaultCullFace == (-1)) DefaultShader.defaultCullFace else this.config.defaultCullFace
    var depthFunc: scala.Int = if (this.config.defaultDepthFunc == (-1)) DefaultShader.defaultDepthFunc else this.config.defaultDepthFunc
    var depthRangeNear: scala.Float = 0.0f
    var depthRangeFar: scala.Float = 1.0f
    var depthMask: scala.Boolean = true
    for (attr <- attributes) {
      val t: scala.Long = attr.`type`
      if (com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.is(t)) {
        context.setBlending(true, attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute].sourceFunction, attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute].destFunction)
        this.set(this.u_opacity, attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute].opacity)
      } else {
        if ((t & com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace) == com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace) {
          cullFace = attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.IntAttribute].value
        } else {
          if ((t & com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest) == com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest) {
            this.set(this.u_alphaTest, attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute].value)
          } else {
            if ((t & com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute.Type) == com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute.Type) {
              val dta: com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute = attr.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute]
              depthFunc = dta.depthFunc
              depthRangeNear = dta.depthRangeNear
              depthRangeFar = dta.depthRangeFar
              depthMask = dta.depthMask
            } else {
              if (!this.config.ignoreUnimplemented) {
                throw new com.badlogic.gdx.utils.GdxRuntimeException("Unknown material attribute: " + attr.toString())
              } else ()
            }
          }
        }
      }
    }
    context.setCullFace(cullFace)
    context.setDepthTest(depthFunc, depthRangeNear, depthRangeFar)
    context.setDepthMask(depthMask)
  }
  def bindLights(renderable: com.badlogic.gdx.graphics.g3d.Renderable, attributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
    val lights: com.badlogic.gdx.graphics.g3d.Environment = renderable.environment
    val dla: com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute = attributes.get(classOf[com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute], com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type)
    val dirs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight] = if (dla == null) null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight]] else dla.lights
    val pla: com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute = attributes.get(classOf[com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute], com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type)
    val points: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight] = if (pla == null) null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight]] else pla.lights
    val sla: com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute = attributes.get(classOf[com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute], com.badlogic.gdx.graphics.g3d.attributes.SpotLightsAttribute.Type)
    val spots: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight] = if (sla == null) null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.SpotLight]] else sla.lights
    if (this.dirLightsLoc >= 0) {
      { var i: scala.Int = 0; while (i < this.directionalLights.length) { {
        if ((dirs == null) || (i >= dirs.size)) {
          if (((this.lightsSet && (this.directionalLights(i).color.r == 0.0f)) && (this.directionalLights(i).color.g == 0.0f)) && (this.directionalLights(i).color.b == 0.0f)) {
            /* continue */ ()
          } else ()
          this.directionalLights(i).color.set(0, 0, 0, 1)
        } else {
          if (this.lightsSet && this.directionalLights(i).equals(dirs.get(i))) {
            /* continue */ ()
          } else {
            this.directionalLights(i).set(dirs.get(i))
          }
        }
        val idx: scala.Int = this.dirLightsLoc + (i * this.dirLightsSize)
        program.setUniformf(idx + this.dirLightsColorOffset, this.directionalLights(i).color.r, this.directionalLights(i).color.g, this.directionalLights(i).color.b)
        program.setUniformf(idx + this.dirLightsDirectionOffset, this.directionalLights(i).direction.x, this.directionalLights(i).direction.y, this.directionalLights(i).direction.z)
        if (this.dirLightsSize <= 0) {
          /* break */ ()
        } else ()
      }; i = i + 1 } }
    } else ()
    if (this.pointLightsLoc >= 0) {
      { var i: scala.Int = 0; while (i < this.pointLights.length) { {
        if ((points == null) || (i >= points.size)) {
          if (this.lightsSet && (this.pointLights(i).intensity == 0.0f)) {
            /* continue */ ()
          } else ()
          this.pointLights(i).intensity = 0.0f
        } else {
          if (this.lightsSet && this.pointLights(i).equals(points.get(i))) {
            /* continue */ ()
          } else {
            this.pointLights(i).set(points.get(i))
          }
        }
        val idx: scala.Int = this.pointLightsLoc + (i * this.pointLightsSize)
        program.setUniformf(idx + this.pointLightsColorOffset, this.pointLights(i).color.r * this.pointLights(i).intensity, this.pointLights(i).color.g * this.pointLights(i).intensity, this.pointLights(i).color.b * this.pointLights(i).intensity)
        program.setUniformf(idx + this.pointLightsPositionOffset, this.pointLights(i).position.x, this.pointLights(i).position.y, this.pointLights(i).position.z)
        if (this.pointLightsIntensityOffset >= 0) {
          program.setUniformf(idx + this.pointLightsIntensityOffset, this.pointLights(i).intensity)
        } else ()
        if (this.pointLightsSize <= 0) {
          /* break */ ()
        } else ()
      }; i = i + 1 } }
    } else ()
    if (this.spotLightsLoc >= 0) {
      { var i: scala.Int = 0; while (i < this.spotLights.length) { {
        if ((spots == null) || (i >= spots.size)) {
          if (this.lightsSet && (this.spotLights(i).intensity == 0.0f)) {
            /* continue */ ()
          } else ()
          this.spotLights(i).intensity = 0.0f
        } else {
          if (this.lightsSet && this.spotLights(i).equals(spots.get(i))) {
            /* continue */ ()
          } else {
            this.spotLights(i).set(spots.get(i))
          }
        }
        val idx: scala.Int = this.spotLightsLoc + (i * this.spotLightsSize)
        program.setUniformf(idx + this.spotLightsColorOffset, this.spotLights(i).color.r * this.spotLights(i).intensity, this.spotLights(i).color.g * this.spotLights(i).intensity, this.spotLights(i).color.b * this.spotLights(i).intensity)
        program.setUniformf(idx + this.spotLightsPositionOffset, this.spotLights(i).position)
        program.setUniformf(idx + this.spotLightsDirectionOffset, this.spotLights(i).direction)
        program.setUniformf(idx + this.spotLightsCutoffAngleOffset, this.spotLights(i).cutoffAngle)
        program.setUniformf(idx + this.spotLightsExponentOffset, this.spotLights(i).exponent)
        if (this.spotLightsIntensityOffset >= 0) {
          program.setUniformf(idx + this.spotLightsIntensityOffset, this.spotLights(i).intensity)
        } else ()
        if (this.spotLightsSize <= 0) {
          /* break */ ()
        } else ()
      }; i = i + 1 } }
    } else ()
    if (attributes.has(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Fog)) {
      this.set(this.u_fogColor, attributes.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Fog).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute].color)
    } else ()
    if ((lights != null) && (lights.shadowMap != null)) {
      this.set(this.u_shadowMapProjViewTrans, lights.shadowMap.getProjViewTrans())
      this.set(this.u_shadowTexture, lights.shadowMap.getDepthMap().asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]])
      this.set(this.u_shadowPCFOffset, 1.0f / (2.0f * lights.shadowMap.getDepthMap().asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[com.badlogic.gdx.graphics.GLTexture]].texture.getWidth()))
    } else ()
    this.lightsSet = true
  }
  def dispose(): scala.Unit = {
    program.dispose()
    super.dispose()
  }
  def getDefaultCullFace(): scala.Int = {
    return if (this.config.defaultCullFace == (-1)) DefaultShader.defaultCullFace else this.config.defaultCullFace
  }
  def setDefaultCullFace(cullFace: scala.Int): scala.Unit = {
    this.config.defaultCullFace = cullFace
  }
  def getDefaultDepthFunc(): scala.Int = {
    return if (this.config.defaultDepthFunc == (-1)) DefaultShader.defaultDepthFunc else this.config.defaultDepthFunc
  }
  def setDefaultDepthFunc(depthFunc: scala.Int): scala.Unit = {
    this.config.defaultDepthFunc = depthFunc
  }
}
object DefaultShader {
  export com.badlogic.gdx.graphics.g3d.shaders.BaseShader.{defaultVertexShader => _, defaultFragmentShader => _, implementedFlags => _, defaultCullFace => _, defaultDepthFunc => _, optionalAttributes => _, tmpAttributes => _, getDefaultVertexShader => _, getDefaultFragmentShader => _, and => _, or => _, combineAttributes => _, combineAttributeMasks => _, createPrefix => _, Config => _, Inputs => _, Setters => _, *}
  private var defaultVertexShader: java.lang.String = null
  private var defaultFragmentShader: java.lang.String = null
  var implementedFlags: scala.Long = (((com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type | com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse) | com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse) | com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular) | com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess
  var defaultCullFace: scala.Int = com.badlogic.gdx.graphics.GL20.GL_BACK
  var defaultDepthFunc: scala.Int = com.badlogic.gdx.graphics.GL20.GL_LEQUAL
  private final val optionalAttributes: scala.Long = com.badlogic.gdx.graphics.g3d.attributes.IntAttribute.CullFace | com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute.Type
  private final val tmpAttributes: com.badlogic.gdx.graphics.g3d.Attributes = new com.badlogic.gdx.graphics.g3d.Attributes()
  def getDefaultVertexShader(): java.lang.String = {
    if (DefaultShader.defaultVertexShader == null) {
      DefaultShader.defaultVertexShader = com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/default.vertex.glsl").readString()
    } else ()
    return DefaultShader.defaultVertexShader
  }
  def getDefaultFragmentShader(): java.lang.String = {
    if (DefaultShader.defaultFragmentShader == null) {
      DefaultShader.defaultFragmentShader = com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/default.fragment.glsl").readString()
    } else ()
    return DefaultShader.defaultFragmentShader
  }
  private final def and(mask: scala.Long, flag: scala.Long): scala.Boolean = {
    return (mask & flag) == flag
  }
  private final def or(mask: scala.Long, flag: scala.Long): scala.Boolean = {
    return (mask & flag) != 0
  }
  private final def combineAttributes(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Attributes = {
    DefaultShader.tmpAttributes.clear()
    if (renderable.environment != null) {
      DefaultShader.tmpAttributes.set(renderable.environment)
    } else ()
    if (renderable.material != null) {
      DefaultShader.tmpAttributes.set(renderable.material)
    } else ()
    return DefaultShader.tmpAttributes
  }
  private final def combineAttributeMasks(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Long = {
    var mask: scala.Long = 0
    if (renderable.environment != null) {
      mask = mask | renderable.environment.getMask()
    } else ()
    if (renderable.material != null) {
      mask = mask | renderable.material.getMask()
    } else ()
    return mask
  }
  def createPrefix(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config): java.lang.String = {
    val attributes: com.badlogic.gdx.graphics.g3d.Attributes = DefaultShader.combineAttributes(renderable)
    var prefix: java.lang.String = ""
    val attributesMask: scala.Long = attributes.getMask()
    val vertexMask: scala.Long = renderable.meshPart.mesh.getVertexAttributes().getMask()
    if (DefaultShader.and(vertexMask, com.badlogic.gdx.graphics.VertexAttributes.Usage.Position)) {
      prefix = prefix + "#define positionFlag\n"
    } else ()
    if (DefaultShader.or(vertexMask, com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked | com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorPacked)) {
      prefix = prefix + "#define colorFlag\n"
    } else ()
    if (DefaultShader.and(vertexMask, com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal)) {
      prefix = prefix + "#define binormalFlag\n"
    } else ()
    if (DefaultShader.and(vertexMask, com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent)) {
      prefix = prefix + "#define tangentFlag\n"
    } else ()
    if (DefaultShader.and(vertexMask, com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal)) {
      prefix = prefix + "#define normalFlag\n"
    } else ()
    if (DefaultShader.and(vertexMask, com.badlogic.gdx.graphics.VertexAttributes.Usage.Normal) || DefaultShader.and(vertexMask, com.badlogic.gdx.graphics.VertexAttributes.Usage.Tangent | com.badlogic.gdx.graphics.VertexAttributes.Usage.BiNormal)) {
      if (renderable.environment != null) {
        prefix = prefix + "#define lightingFlag\n"
        prefix = prefix + "#define ambientCubemapFlag\n"
        prefix = prefix + (("#define numDirectionalLights " + config.numDirectionalLights) + "\n")
        prefix = prefix + (("#define numPointLights " + config.numPointLights) + "\n")
        prefix = prefix + (("#define numSpotLights " + config.numSpotLights) + "\n")
        if (attributes.has(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Fog)) {
          prefix = prefix + "#define fogFlag\n"
        } else ()
        if (renderable.environment.shadowMap != null) {
          prefix = prefix + "#define shadowMapFlag\n"
        } else ()
        if (attributes.has(com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute.EnvironmentMap)) {
          prefix = prefix + "#define environmentCubemapFlag\n"
        } else ()
      } else ()
    } else ()
    val n: scala.Int = renderable.meshPart.mesh.getVertexAttributes().size();
    { var i: scala.Int = 0; while (i < n) { {
      val attr: com.badlogic.gdx.graphics.VertexAttribute = renderable.meshPart.mesh.getVertexAttributes().get(i)
      if (attr.usage == com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates) {
        prefix = prefix + (("#define texCoord" + attr.unit) + "Flag\n")
      } else ()
    }; i = i + 1 } }
    if (renderable.bones != null) {
      { var i: scala.Int = 0; while (i < config.numBoneWeights) { {
        prefix = prefix + (("#define boneWeight" + i) + "Flag\n")
      }; i = i + 1 } }
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) == com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Alias) + "Flag\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse) == com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.DiffuseAlias) + "Flag\n")
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.DiffuseAlias) + "Coord texCoord0\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Specular) == com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Specular) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.SpecularAlias) + "Flag\n")
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.SpecularAlias) + "Coord texCoord0\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal) == com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.NormalAlias) + "Flag\n")
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.NormalAlias) + "Coord texCoord0\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive) == com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.EmissiveAlias) + "Flag\n")
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.EmissiveAlias) + "Coord texCoord0\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection) == com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.ReflectionAlias) + "Flag\n")
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.ReflectionAlias) + "Coord texCoord0\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Ambient) == com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Ambient) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.AmbientAlias) + "Flag\n")
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.AmbientAlias) + "Coord texCoord0\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse) == com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.DiffuseAlias) + "Flag\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular) == com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.SpecularAlias) + "Flag\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Emissive) == com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Emissive) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.EmissiveAlias) + "Flag\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Reflection) == com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Reflection) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.ReflectionAlias) + "Flag\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess) == com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.ShininessAlias) + "Flag\n")
    } else ()
    if ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest) == com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest) {
      prefix = prefix + (("#define " + com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTestAlias) + "Flag\n")
    } else ()
    if ((renderable.bones != null) && (config.numBones > 0)) {
      prefix = prefix + (("#define numBones " + config.numBones) + "\n")
    } else ()
    return prefix
  }
  class Config {
    var vertexShader: java.lang.String = null
    var fragmentShader: java.lang.String = null
    var numDirectionalLights: scala.Int = 2
    var numPointLights: scala.Int = 5
    var numSpotLights: scala.Int = 0
    var numBones: scala.Int = 12
    var numBoneWeights: scala.Int = 4
    var ignoreUnimplemented: scala.Boolean = true
    var defaultCullFace: scala.Int = -1
    var defaultDepthFunc: scala.Int = -1
    def this(vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
      this()
      this.vertexShader = vertexShader
      this.fragmentShader = fragmentShader
    }
  }
  object Inputs {
    final val projTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_projTrans")
    final val viewTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_viewTrans")
    final val projViewTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_projViewTrans")
    final val cameraPosition: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_cameraPosition")
    final val cameraDirection: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_cameraDirection")
    final val cameraUp: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_cameraUp")
    final val cameraNearFar: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_cameraNearFar")
    final val worldTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_worldTrans")
    final val viewWorldTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_viewWorldTrans")
    final val projViewWorldTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_projViewWorldTrans")
    final val normalMatrix: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_normalMatrix")
    final val bones: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_bones")
    final val shininess: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_shininess", com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess)
    final val opacity: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_opacity", com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)
    final val diffuseColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_diffuseColor", com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse)
    final val diffuseTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_diffuseTexture", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse)
    final val diffuseUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_diffuseUVTransform", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse)
    final val specularColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_specularColor", com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular)
    final val specularTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_specularTexture", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Specular)
    final val specularUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_specularUVTransform", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Specular)
    final val emissiveColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_emissiveColor", com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Emissive)
    final val emissiveTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_emissiveTexture", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive)
    final val emissiveUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_emissiveUVTransform", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive)
    final val reflectionColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_reflectionColor", com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Reflection)
    final val reflectionTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_reflectionTexture", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection)
    final val reflectionUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_reflectionUVTransform", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection)
    final val normalTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_normalTexture", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal)
    final val normalUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_normalUVTransform", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal)
    final val ambientTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_ambientTexture", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Ambient)
    final val ambientUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_ambientUVTransform", com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Ambient)
    final val alphaTest: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_alphaTest")
    final val ambientCube: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_ambientCubemap")
    final val dirLights: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_dirLights")
    final val pointLights: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_pointLights")
    final val spotLights: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_spotLights")
    final val environmentCubemap: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Uniform("u_environmentCubemap")
  }
  object Setters {
    final val projTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.GlobalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.projection)
      }
    }
    final val viewTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.GlobalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.view)
      }
    }
    final val projViewTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.GlobalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.combined)
      }
    }
    final val cameraPosition: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.GlobalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.position.x, shader.camera.position.y, shader.camera.position.z, 1.1881f / (shader.camera.far * shader.camera.far))
      }
    }
    final val cameraDirection: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.GlobalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.direction)
      }
    }
    final val cameraUp: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.GlobalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.up)
      }
    }
    final val cameraNearFar: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.GlobalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, shader.camera.near, shader.camera.far)
      }
    }
    final val worldTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, renderable.worldTransform)
      }
    }
    final val viewWorldTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      final val temp: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, temp.set(shader.camera.view).mul(renderable.worldTransform))
      }
    }
    final val projViewWorldTrans: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      final val temp: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, temp.set(shader.camera.combined).mul(renderable.worldTransform))
      }
    }
    final val normalMatrix: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      private final val tmpM: com.badlogic.gdx.math.Matrix3 = new com.badlogic.gdx.math.Matrix3()
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, tmpM.set(renderable.worldTransform).inv().transpose())
      }
    }
    final val shininess: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.Shininess).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute].value)
      }
    }
    final val diffuseColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute].color)
      }
    }
    final val diffuseTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val unit: scala.Int = shader.context.textureBinder.bind(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute].textureDescription)
        shader.set(inputID, unit)
      }
    }
    final val diffuseUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val ta: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
        shader.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
      }
    }
    final val specularColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Specular).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute].color)
      }
    }
    final val specularTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val unit: scala.Int = shader.context.textureBinder.bind(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Specular).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute].textureDescription)
        shader.set(inputID, unit)
      }
    }
    final val specularUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val ta: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Specular).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
        shader.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
      }
    }
    final val emissiveColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Emissive).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute].color)
      }
    }
    final val emissiveTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val unit: scala.Int = shader.context.textureBinder.bind(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute].textureDescription)
        shader.set(inputID, unit)
      }
    }
    final val emissiveUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val ta: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Emissive).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
        shader.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
      }
    }
    final val reflectionColor: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        shader.set(inputID, combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Reflection).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute].color)
      }
    }
    final val reflectionTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val unit: scala.Int = shader.context.textureBinder.bind(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute].textureDescription)
        shader.set(inputID, unit)
      }
    }
    final val reflectionUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val ta: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Reflection).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
        shader.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
      }
    }
    final val normalTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val unit: scala.Int = shader.context.textureBinder.bind(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute].textureDescription)
        shader.set(inputID, unit)
      }
    }
    final val normalUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val ta: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Normal).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
        shader.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
      }
    }
    final val ambientTexture: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val unit: scala.Int = shader.context.textureBinder.bind(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Ambient).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute].textureDescription)
        shader.set(inputID, unit)
      }
    }
    final val ambientUVTransform: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        val ta: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Ambient).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
        shader.set(inputID, ta.offsetU, ta.offsetV, ta.scaleU, ta.scaleV)
      }
    }
    final val environmentCubemap: com.badlogic.gdx.graphics.g3d.shaders.BaseShader.Setter = new com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter() {
      override def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        if (combinedAttributes.has(com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute.EnvironmentMap)) {
          shader.set(inputID, shader.context.textureBinder.bind(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute.EnvironmentMap).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute].textureDescription))
        } else ()
      }
    }
    class Bones(numBones: scala.Int) extends com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter {
      var bones: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
      this.bones = new scala.Array[scala.Float](numBones * 16)
      def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        { var i: scala.Int = 0; while (i < this.bones.length) { {
          val idx: scala.Int = i / 16
          if (((renderable.bones == null) || (idx >= renderable.bones.length)) || (renderable.bones(idx) == null)) {
            java.lang.System.arraycopy(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.Bones.idtMatrix.`val`, 0, this.bones, i, 16)
          } else {
            java.lang.System.arraycopy(renderable.bones(idx).`val`, 0, this.bones, i, 16)
          }
        }; i = i + 16 } }
        shader.program.setUniformMatrix4fv(shader.loc(inputID), this.bones, 0, this.bones.length)
      }
    }
    object Bones {
      private final val idtMatrix: com.badlogic.gdx.math.Matrix4 = new com.badlogic.gdx.math.Matrix4()
    }
    class ACubemap(dirLightsOffset$p: scala.Int, pointLightsOffset$p: scala.Int) extends com.badlogic.gdx.graphics.g3d.shaders.BaseShader.LocalSetter {
      private final val cacheAmbientCubemap: com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap = new com.badlogic.gdx.graphics.g3d.environment.AmbientCubemap()
      var dirLightsOffset: scala.Int = 0
      var pointLightsOffset: scala.Int = 0
      this.dirLightsOffset = dirLightsOffset$p
      this.pointLightsOffset = pointLightsOffset$p
      def set(shader: com.badlogic.gdx.graphics.g3d.shaders.BaseShader, inputID: scala.Int, renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
        if (renderable.environment == null) {
          shader.program.setUniform3fv(shader.loc(inputID), com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.ACubemap.ones, 0, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.ACubemap.ones.length)
        } else {
          renderable.worldTransform.getTranslation(com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.ACubemap.tmpV1)
          if (combinedAttributes.has(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight)) {
            this.cacheAmbientCubemap.set(combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.AmbientLight).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute].color)
          } else ()
          if (combinedAttributes.has(com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type)) {
            val lights: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.DirectionalLight] = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute].lights;
            { var i: scala.Int = this.dirLightsOffset; while (i < lights.size) { {
              this.cacheAmbientCubemap.add(lights.get(i).color, lights.get(i).direction)
            }; i = i + 1 } }
          } else ()
          if (combinedAttributes.has(com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type)) {
            val lights: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.environment.PointLight] = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.PointLightsAttribute].lights;
            { var i: scala.Int = this.pointLightsOffset; while (i < lights.size) { {
              this.cacheAmbientCubemap.add(lights.get(i).color, lights.get(i).position, com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Setters.ACubemap.tmpV1, lights.get(i).intensity)
            }; i = i + 1 } }
          } else ()
          this.cacheAmbientCubemap.clamp()
          shader.program.setUniform3fv(shader.loc(inputID), this.cacheAmbientCubemap.data, 0, this.cacheAmbientCubemap.data.length)
        }
      }
    }
    object ACubemap {
      private final val ones: scala.Array[scala.Float] = scala.Array[scala.Float](1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1)
      private final val tmpV1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
    }
  }
}