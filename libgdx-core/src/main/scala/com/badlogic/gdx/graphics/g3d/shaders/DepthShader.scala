package com.badlogic.gdx.graphics.g3d.shaders

class DepthShader extends com.badlogic.gdx.graphics.g3d.shaders.DefaultShader {
  var numBones: scala.Int = 0
  private var alphaTestAttribute: com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute]
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config, shaderProgram: com.badlogic.gdx.graphics.glutils.ShaderProgram) = {
    this()
    val attributes: com.badlogic.gdx.graphics.g3d.Attributes = DepthShader.combineAttributes(renderable)
    if ((renderable.bones != null) && (renderable.bones.length > config.numBones)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((("too many bones: " + renderable.bones.length) + ", max configured: ") + config.numBones)
    } else ()
    this.numBones = if (renderable.bones == null) 0 else config.numBones
    val boneWeights: scala.Int = renderable.meshPart.mesh.getVertexAttributes().getBoneWeights()
    if (boneWeights > config.numBoneWeights) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((("too many bone weights: " + boneWeights) + ", max configured: ") + config.numBoneWeights)
    } else ()
    this.alphaTestAttribute = new com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest, config.defaultAlphaTest)
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config, prefix: java.lang.String, vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
    this(renderable, config, new com.badlogic.gdx.graphics.glutils.ShaderProgram(prefix + vertexShader, prefix + fragmentShader))
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config, prefix: java.lang.String) = {
    this(renderable, config, prefix, if (config.vertexShader != null) config.vertexShader else DepthShader.getDefaultVertexShader(), if (config.fragmentShader != null) config.fragmentShader else DepthShader.getDefaultFragmentShader())
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config) = {
    this(renderable, config, DepthShader.createPrefix(renderable, config))
  }
  def this(renderable: com.badlogic.gdx.graphics.g3d.Renderable) = {
    this(renderable, new com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config())
  }
  def begin(camera: com.badlogic.gdx.graphics.Camera, context: com.badlogic.gdx.graphics.g3d.utils.RenderContext): scala.Unit = {
    super.begin(camera, context)
  }
  def `end`(): scala.Unit = {
    super.`end`()
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
    val attributes: com.badlogic.gdx.graphics.g3d.Attributes = DepthShader.combineAttributes(renderable)
    val isBlendedTextureShader: scala.Boolean = ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) == com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) && ((attributesMask & com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse) == com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse)
    val isBlendedTextureRenderable: scala.Boolean = attributes.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type) && attributes.has(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse)
    if (isBlendedTextureShader != isBlendedTextureRenderable) {
      return false
    } else ()
    return (renderable.bones != null) == (this.numBones > 0)
  }
  def render(renderable: com.badlogic.gdx.graphics.g3d.Renderable, combinedAttributes: com.badlogic.gdx.graphics.g3d.Attributes): scala.Unit = {
    if (combinedAttributes.has(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)) {
      val blending: com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute = combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute]
      combinedAttributes.remove(com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute.Type)
      val hasAlphaTest: scala.Boolean = combinedAttributes.has(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest)
      if (!hasAlphaTest) {
        combinedAttributes.set(this.alphaTestAttribute)
      } else ()
      if (blending.opacity >= combinedAttributes.get(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute].value) {
        super.render(renderable, combinedAttributes)
      } else ()
      if (!hasAlphaTest) {
        combinedAttributes.remove(com.badlogic.gdx.graphics.g3d.attributes.FloatAttribute.AlphaTest)
      } else ()
      combinedAttributes.set(blending)
    } else {
      super.render(renderable, combinedAttributes)
    }
  }
}
object DepthShader {
  private var defaultVertexShader: java.lang.String = null
  private var defaultFragmentShader: java.lang.String = null
  private final val tmpAttributes: com.badlogic.gdx.graphics.g3d.Attributes = new com.badlogic.gdx.graphics.g3d.Attributes()
  final def getDefaultVertexShader(): java.lang.String = {
    if (DepthShader.defaultVertexShader == null) {
      DepthShader.defaultVertexShader = com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/depth.vertex.glsl").readString()
    } else ()
    return DepthShader.defaultVertexShader
  }
  final def getDefaultFragmentShader(): java.lang.String = {
    if (DepthShader.defaultFragmentShader == null) {
      DepthShader.defaultFragmentShader = com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/graphics/g3d/shaders/depth.fragment.glsl").readString()
    } else ()
    return DepthShader.defaultFragmentShader
  }
  def createPrefix(renderable: com.badlogic.gdx.graphics.g3d.Renderable, config: com.badlogic.gdx.graphics.g3d.shaders.DepthShader.Config): java.lang.String = {
    var prefix: java.lang.String = com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.createPrefix(renderable, config)
    if (!config.depthBufferOnly) {
      prefix = prefix + "#define PackedDepthFlag\n"
    } else ()
    return prefix
  }
  private final def combineAttributes(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Attributes = {
    DepthShader.tmpAttributes.clear()
    if (renderable.environment != null) {
      DepthShader.tmpAttributes.set(renderable.environment)
    } else ()
    if (renderable.material != null) {
      DepthShader.tmpAttributes.set(renderable.material)
    } else ()
    return DepthShader.tmpAttributes
  }
  class Config extends com.badlogic.gdx.graphics.g3d.shaders.DefaultShader.Config {
    var depthBufferOnly: scala.Boolean = false
    var defaultAlphaTest: scala.Float = 0.5f
    def this(vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
      this()
    }
    defaultCullFace = com.badlogic.gdx.graphics.GL20.GL_FRONT
  }
}