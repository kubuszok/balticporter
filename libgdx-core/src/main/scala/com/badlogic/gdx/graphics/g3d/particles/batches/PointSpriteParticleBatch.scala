package com.badlogic.gdx.graphics.g3d.particles.batches

class PointSpriteParticleBatch extends com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch[com.badlogic.gdx.graphics.g3d.particles.renderers.PointSpriteControllerRenderData] {
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var renderable: com.badlogic.gdx.graphics.g3d.Renderable = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Renderable]
  protected var blendingAttribute: com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute]
  protected var depthTestAttribute: com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute]
  def this(capacity: scala.Int, shaderConfig: com.badlogic.gdx.graphics.g3d.particles.ParticleShader#Config, blendingAttribute: com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute, depthTestAttribute: com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute) = {
    this()
    if (!PointSpriteParticleBatch.pointSpritesEnabled) {
      PointSpriteParticleBatch.enablePointSprites()
    } else ()
    this.blendingAttribute = blendingAttribute
    this.depthTestAttribute = depthTestAttribute
    if (this.blendingAttribute == null) {
      this.blendingAttribute = new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(com.badlogic.gdx.graphics.GL20.GL_ONE, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f)
    } else ()
    if (this.depthTestAttribute == null) {
      this.depthTestAttribute = new com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute(com.badlogic.gdx.graphics.GL20.GL_LEQUAL, false)
    } else ()
    this.allocRenderable()
    this.ensureCapacity(capacity)
    this.renderable.shader = new com.badlogic.gdx.graphics.g3d.particles.ParticleShader(this.renderable, shaderConfig)
    this.renderable.shader.init()
  }
  def this(capacity: scala.Int, shaderConfig: com.badlogic.gdx.graphics.g3d.particles.ParticleShader#Config) = {
    this(capacity, shaderConfig, null, null)
  }
  def this(capacity: scala.Int) = {
    this(capacity, new com.badlogic.gdx.graphics.g3d.particles.ParticleShader#Config(com.badlogic.gdx.graphics.g3d.particles.ParticleShader.ParticleType.Point))
  }
  protected def allocParticlesData(capacity: scala.Int): scala.Unit = {
    this.vertices = new Array[scala.Float](capacity * PointSpriteParticleBatch.CPU_VERTEX_SIZE)
    if (this.renderable.meshPart.mesh != null) {
      this.renderable.meshPart.mesh.dispose()
    } else ()
    this.renderable.meshPart.mesh = new com.badlogic.gdx.graphics.Mesh(false, capacity, 0, PointSpriteParticleBatch.CPU_ATTRIBUTES)
  }
  protected def allocRenderable(): scala.Unit = {
    this.renderable = new com.badlogic.gdx.graphics.g3d.Renderable()
    this.renderable.meshPart.primitiveType = com.badlogic.gdx.graphics.GL20.GL_POINTS
    this.renderable.meshPart.offset = 0
    this.renderable.material = new com.badlogic.gdx.graphics.g3d.Material(this.blendingAttribute, this.depthTestAttribute, com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.createDiffuse(null.asInstanceOf[com.badlogic.gdx.graphics.Texture]))
  }
  def setTexture(texture: com.badlogic.gdx.graphics.Texture): scala.Unit = {
    val attribute: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = this.renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
    attribute.textureDescription.texture = texture
  }
  def getTexture(): com.badlogic.gdx.graphics.Texture = {
    val attribute: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = this.renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
    return attribute.textureDescription.texture
  }
  def getBlendingAttribute(): com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute = {
    return this.blendingAttribute
  }
  protected def flush(offsets: scala.Array[scala.Int]): scala.Unit = {
    var tp: scala.Int = 0
    for (data <- renderData) {
      val scaleChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.scaleChannel
      val regionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.regionChannel
      val positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.positionChannel
      val colorChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.colorChannel
      val rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.rotationChannel
      { var p: scala.Int = 0; while (p < data.controller.particles.size) { {
        val offset: scala.Int = offsets(tp) * PointSpriteParticleBatch.CPU_VERTEX_SIZE
        val regionOffset: scala.Int = p * regionChannel.strideSize
        val positionOffset: scala.Int = p * positionChannel.strideSize
        val colorOffset: scala.Int = p * colorChannel.strideSize
        val rotationOffset: scala.Int = p * rotationChannel.strideSize
        this.vertices(offset + PointSpriteParticleBatch.CPU_POSITION_OFFSET) = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_POSITION_OFFSET) + 1) = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_POSITION_OFFSET) + 2) = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
        this.vertices(offset + PointSpriteParticleBatch.CPU_COLOR_OFFSET) = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.RedOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_COLOR_OFFSET) + 1) = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.GreenOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_COLOR_OFFSET) + 2) = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.BlueOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_COLOR_OFFSET) + 3) = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset)
        this.vertices(offset + PointSpriteParticleBatch.CPU_SIZE_AND_ROTATION_OFFSET) = scaleChannel.data(p * scaleChannel.strideSize)
        this.vertices((offset + PointSpriteParticleBatch.CPU_SIZE_AND_ROTATION_OFFSET) + 1) = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CosineOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_SIZE_AND_ROTATION_OFFSET) + 2) = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.SineOffset)
        this.vertices(offset + PointSpriteParticleBatch.CPU_REGION_OFFSET) = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.UOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_REGION_OFFSET) + 1) = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VOffset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_REGION_OFFSET) + 2) = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.U2Offset)
        this.vertices((offset + PointSpriteParticleBatch.CPU_REGION_OFFSET) + 3) = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.V2Offset)
      }; p = p + 1; tp = tp + 1 } }
    }
    this.renderable.meshPart.size = bufferedParticlesCount
    this.renderable.meshPart.mesh.setVertices(this.vertices, 0, bufferedParticlesCount * PointSpriteParticleBatch.CPU_VERTEX_SIZE)
    this.renderable.meshPart.update()
  }
  def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    if (bufferedParticlesCount > 0) {
      renderables.add(pool.obtain().set(this.renderable))
    } else ()
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData#SaveData = resources.createSaveData("pointSpriteBatch")
    data.saveAsset(manager.getAssetFileName(this.getTexture()), classOf[java.lang.Class])
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData#SaveData = resources.getSaveData("pointSpriteBatch")
    if (data != null) {
      this.setTexture(manager.get(data.loadAsset()).asInstanceOf[com.badlogic.gdx.graphics.Texture])
    } else ()
  }
}
object PointSpriteParticleBatch {
  private var pointSpritesEnabled: scala.Boolean = false
  protected final val TMP_V1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  protected final val sizeAndRotationUsage: scala.Int = 1 << 9
  protected final val CPU_ATTRIBUTES: com.badlogic.gdx.graphics.VertexAttributes = new com.badlogic.gdx.graphics.VertexAttributes(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 4, "a_region"), new com.badlogic.gdx.graphics.VertexAttribute(PointSpriteParticleBatch.sizeAndRotationUsage, 3, "a_sizeAndRotation"))
  protected final val CPU_VERTEX_SIZE: scala.Int = (PointSpriteParticleBatch.CPU_ATTRIBUTES.vertexSize / 4).asInstanceOf[scala.Short]
  protected final val CPU_POSITION_OFFSET: scala.Int = (PointSpriteParticleBatch.CPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position).offset / 4).asInstanceOf[scala.Short]
  protected final val CPU_COLOR_OFFSET: scala.Int = (PointSpriteParticleBatch.CPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked).offset / 4).asInstanceOf[scala.Short]
  protected final val CPU_REGION_OFFSET: scala.Int = (PointSpriteParticleBatch.CPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates).offset / 4).asInstanceOf[scala.Short]
  protected final val CPU_SIZE_AND_ROTATION_OFFSET: scala.Int = (PointSpriteParticleBatch.CPU_ATTRIBUTES.findByUsage(PointSpriteParticleBatch.sizeAndRotationUsage).offset / 4).asInstanceOf[scala.Short]
  private def enablePointSprites(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_VERTEX_PROGRAM_POINT_SIZE)
    if (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) {
      com.badlogic.gdx.Gdx.gl.glEnable(34913)
    } else ()
    PointSpriteParticleBatch.pointSpritesEnabled = true
  }
}