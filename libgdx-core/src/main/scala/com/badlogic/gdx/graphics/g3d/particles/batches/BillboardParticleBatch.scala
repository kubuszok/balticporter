package com.badlogic.gdx.graphics.g3d.particles.batches

class BillboardParticleBatch extends com.badlogic.gdx.graphics.g3d.particles.batches.BufferedParticleBatch[com.badlogic.gdx.graphics.g3d.particles.renderers.BillboardControllerRenderData] {
  private var renderablePool: RenderablePool = null.asInstanceOf[RenderablePool]
  private var renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]]
  private var vertices: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var indices: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  private var currentVertexSize: scala.Int = 0
  private var currentAttributes: com.badlogic.gdx.graphics.VertexAttributes = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes]
  var useGPU: scala.Boolean = false
  var mode: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode = com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode.Screen
  var texture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
  var blendingAttribute: com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute]
  var depthTestAttribute: com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute]
  var shader: com.badlogic.gdx.graphics.g3d.Shader = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.Shader]
  def this(mode: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode, useGPU: scala.Boolean, capacity: scala.Int, blendingAttribute: com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute, depthTestAttribute: com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute) = {
    this()
    this.renderables = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]()
    this.renderablePool = new RenderablePool()
    this.blendingAttribute = blendingAttribute
    this.depthTestAttribute = depthTestAttribute
    if (this.blendingAttribute == null) {
      this.blendingAttribute = new com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute(com.badlogic.gdx.graphics.GL20.GL_ONE, com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f)
    } else ()
    if (this.depthTestAttribute == null) {
      this.depthTestAttribute = new com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute(com.badlogic.gdx.graphics.GL20.GL_LEQUAL, false)
    } else ()
    this.allocIndices()
    this.initRenderData()
    this.ensureCapacity(capacity)
    this.setUseGpu(useGPU)
    this.setAlignMode(mode)
  }
  def this(mode: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode, useGPU: scala.Boolean, capacity: scala.Int) = {
    this(mode, useGPU, capacity, null, null)
  }
  def this(capacity: scala.Int) = {
    this(com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode.Screen, false, capacity)
  }
  def allocParticlesData(capacity: scala.Int): scala.Unit = {
    this.vertices = new Array[scala.Float]((this.currentVertexSize * 4) * capacity)
    this.allocRenderables(capacity)
  }
  def allocRenderable(): com.badlogic.gdx.graphics.g3d.Renderable = {
    val renderable: com.badlogic.gdx.graphics.g3d.Renderable = new com.badlogic.gdx.graphics.g3d.Renderable()
    renderable.meshPart.primitiveType = com.badlogic.gdx.graphics.GL20.GL_TRIANGLES
    renderable.meshPart.offset = 0
    renderable.material = new com.badlogic.gdx.graphics.g3d.Material(this.blendingAttribute, this.depthTestAttribute, com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.createDiffuse(this.texture))
    renderable.meshPart.mesh = new com.badlogic.gdx.graphics.Mesh(false, BillboardParticleBatch.MAX_VERTICES_PER_MESH, BillboardParticleBatch.MAX_PARTICLES_PER_MESH * 6, this.currentAttributes)
    renderable.meshPart.mesh.setIndices(this.indices)
    renderable.shader = this.shader
    return renderable
  }
  private def allocIndices(): scala.Unit = {
    val indicesCount: scala.Int = BillboardParticleBatch.MAX_PARTICLES_PER_MESH * 6
    this.indices = new Array[scala.Short](indicesCount);
    { var i: scala.Int = 0; var vertex: scala.Int = 0; while (i < indicesCount) { {
      this.indices(i) = vertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      this.indices(i + 1) = (vertex + 1).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      this.indices(i + 2) = (vertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      this.indices(i + 3) = (vertex + 2).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      this.indices(i + 4) = (vertex + 3).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
      this.indices(i + 5) = vertex.asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    }; i = i + 6; vertex = vertex + 4 } }
  }
  private def allocRenderables(capacity: scala.Int): scala.Unit = {
    val meshCount: scala.Int = com.badlogic.gdx.math.MathUtils.ceil(capacity / BillboardParticleBatch.MAX_PARTICLES_PER_MESH)
    val free: scala.Int = this.renderablePool.getFree()
    if (free < meshCount) {
      { var i: scala.Int = 0; val left: scala.Int = meshCount - free; while (i < left) { {
        this.renderablePool.free(this.renderablePool.newObject())
      }; i = i + 1 } }
    } else ()
  }
  def getShader(renderable: com.badlogic.gdx.graphics.g3d.Renderable): com.badlogic.gdx.graphics.g3d.Shader = {
    val shader: com.badlogic.gdx.graphics.g3d.Shader = if (this.useGPU) new com.badlogic.gdx.graphics.g3d.particles.ParticleShader(renderable, new com.badlogic.gdx.graphics.g3d.particles.ParticleShader.Config(this.mode)) else new com.badlogic.gdx.graphics.g3d.shaders.DefaultShader(renderable)
    shader.init()
    return shader
  }
  private def allocShader(): scala.Unit = {
    val newRenderable: com.badlogic.gdx.graphics.g3d.Renderable = this.allocRenderable()
    this.shader = {
      newRenderable.shader = this.getShader(newRenderable)
      newRenderable.shader
    }
    this.renderablePool.free(newRenderable)
  }
  private def clearRenderablesPool(): scala.Unit = {
    this.renderablePool.freeAll(this.renderables);
    { var i: scala.Int = 0; val free: scala.Int = this.renderablePool.getFree(); while (i < free) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderablePool.obtain()
      renderable.meshPart.mesh.dispose()
    }; i = i + 1 } }
    this.renderables.clear()
  }
  def setVertexData(): scala.Unit = {
    if (this.useGPU) {
      this.currentAttributes = BillboardParticleBatch.GPU_ATTRIBUTES
      this.currentVertexSize = BillboardParticleBatch.GPU_VERTEX_SIZE
    } else {
      this.currentAttributes = BillboardParticleBatch.CPU_ATTRIBUTES
      this.currentVertexSize = BillboardParticleBatch.CPU_VERTEX_SIZE
    }
  }
  private def initRenderData(): scala.Unit = {
    this.setVertexData()
    this.clearRenderablesPool()
    this.allocShader()
    this.resetCapacity()
  }
  def setAlignMode(mode: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode): scala.Unit = {
    if (mode != this.mode) {
      this.mode = mode
      if (this.useGPU) {
        this.initRenderData()
        this.allocRenderables(bufferedParticlesCount)
      } else ()
    } else ()
  }
  def getAlignMode(): com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode = {
    return this.mode
  }
  def setUseGpu(useGPU: scala.Boolean): scala.Unit = {
    if (this.useGPU != useGPU) {
      this.useGPU = useGPU
      this.initRenderData()
      this.allocRenderables(bufferedParticlesCount)
    } else ()
  }
  def isUseGPU(): scala.Boolean = {
    return this.useGPU
  }
  def setTexture(texture: com.badlogic.gdx.graphics.Texture): scala.Unit = {
    this.renderablePool.freeAll(this.renderables)
    this.renderables.clear();
    { var i: scala.Int = 0; val free: scala.Int = this.renderablePool.getFree(); while (i < free) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderablePool.obtain()
      val attribute: com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute = renderable.material.get(com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute.Diffuse).asInstanceOf[com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute]
      attribute.textureDescription.texture = texture
    }; i = i + 1 } }
    this.texture = texture
  }
  def getTexture(): com.badlogic.gdx.graphics.Texture = {
    return this.texture
  }
  def getBlendingAttribute(): com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute = {
    return this.blendingAttribute
  }
  def begin(): scala.Unit = {
    super.begin()
    this.renderablePool.freeAll(this.renderables)
    this.renderables.clear()
  }
  private def fillVerticesGPU(particlesOffset: scala.Array[scala.Int]): scala.Unit = {
    var tp: scala.Int = 0
    for (data <- renderData) {
      val scaleChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.scaleChannel
      val regionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.regionChannel
      val positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.positionChannel
      val colorChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.colorChannel
      val rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.rotationChannel;
      { var p: scala.Int = 0; val c: scala.Int = data.controller.particles.size; while (p < c) { {
        var baseOffset: scala.Int = (particlesOffset(tp) * this.currentVertexSize) * 4
        val scale: scala.Float = scaleChannel.data(p * scaleChannel.strideSize)
        val regionOffset: scala.Int = p * regionChannel.strideSize
        val positionOffset: scala.Int = p * positionChannel.strideSize
        val colorOffset: scala.Int = p * colorChannel.strideSize
        val rotationOffset: scala.Int = p * rotationChannel.strideSize
        val px: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        val py: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        val pz: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
        val u: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.UOffset)
        val v: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VOffset)
        val u2: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.U2Offset)
        val v2: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.V2Offset)
        val sx: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfWidthOffset) * scale
        val sy: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfHeightOffset) * scale
        val r: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.RedOffset)
        val g: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.GreenOffset)
        val b: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.BlueOffset)
        val a: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset)
        val cosRotation: scala.Float = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CosineOffset)
        val sinRotation: scala.Float = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.SineOffset)
        BillboardParticleBatch.putVertex(this.vertices, baseOffset, px, py, pz, u, v2, -sx, -sy, cosRotation, sinRotation, r, g, b, a)
        baseOffset = baseOffset + this.currentVertexSize
        BillboardParticleBatch.putVertex(this.vertices, baseOffset, px, py, pz, u2, v2, sx, -sy, cosRotation, sinRotation, r, g, b, a)
        baseOffset = baseOffset + this.currentVertexSize
        BillboardParticleBatch.putVertex(this.vertices, baseOffset, px, py, pz, u2, v, sx, sy, cosRotation, sinRotation, r, g, b, a)
        baseOffset = baseOffset + this.currentVertexSize
        BillboardParticleBatch.putVertex(this.vertices, baseOffset, px, py, pz, u, v, -sx, sy, cosRotation, sinRotation, r, g, b, a)
        baseOffset = baseOffset + this.currentVertexSize
      }; p = p + 1; tp = tp + 1 } }
    }
  }
  private def fillVerticesToViewPointCPU(particlesOffset: scala.Array[scala.Int]): scala.Unit = {
    var tp: scala.Int = 0
    for (data <- renderData) {
      val scaleChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.scaleChannel
      val regionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.regionChannel
      val positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.positionChannel
      val colorChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.colorChannel
      val rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.rotationChannel;
      { var p: scala.Int = 0; val c: scala.Int = data.controller.particles.size; while (p < c) { {
        var baseOffset: scala.Int = (particlesOffset(tp) * this.currentVertexSize) * 4
        val scale: scala.Float = scaleChannel.data(p * scaleChannel.strideSize)
        val regionOffset: scala.Int = p * regionChannel.strideSize
        val positionOffset: scala.Int = p * positionChannel.strideSize
        val colorOffset: scala.Int = p * colorChannel.strideSize
        val rotationOffset: scala.Int = p * rotationChannel.strideSize
        val px: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        val py: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        val pz: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
        val u: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.UOffset)
        val v: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VOffset)
        val u2: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.U2Offset)
        val v2: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.V2Offset)
        val sx: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfWidthOffset) * scale
        val sy: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfHeightOffset) * scale
        val r: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.RedOffset)
        val g: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.GreenOffset)
        val b: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.BlueOffset)
        val a: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset)
        val cosRotation: scala.Float = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CosineOffset)
        val sinRotation: scala.Float = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.SineOffset)
        val look: com.badlogic.gdx.math.Vector3 = BillboardParticleBatch.TMP_V3.set(this.camera.position).sub(px, py, pz).nor()
        val right: com.badlogic.gdx.math.Vector3 = BillboardParticleBatch.TMP_V1.set(this.camera.up).crs(look).nor()
        val up: com.badlogic.gdx.math.Vector3 = BillboardParticleBatch.TMP_V2.set(look).crs(right)
        right.scl(sx)
        up.scl(sy)
        if (cosRotation != 1) {
          BillboardParticleBatch.TMP_M3.setToRotation(look, cosRotation, sinRotation)
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((-BillboardParticleBatch.TMP_V1.x) - BillboardParticleBatch.TMP_V2.x, (-BillboardParticleBatch.TMP_V1.y) - BillboardParticleBatch.TMP_V2.y, (-BillboardParticleBatch.TMP_V1.z) - BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(BillboardParticleBatch.TMP_V1.x - BillboardParticleBatch.TMP_V2.x, BillboardParticleBatch.TMP_V1.y - BillboardParticleBatch.TMP_V2.y, BillboardParticleBatch.TMP_V1.z - BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u2, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(BillboardParticleBatch.TMP_V1.x + BillboardParticleBatch.TMP_V2.x, BillboardParticleBatch.TMP_V1.y + BillboardParticleBatch.TMP_V2.y, BillboardParticleBatch.TMP_V1.z + BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u2, v, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((-BillboardParticleBatch.TMP_V1.x) + BillboardParticleBatch.TMP_V2.x, (-BillboardParticleBatch.TMP_V1.y) + BillboardParticleBatch.TMP_V2.y, (-BillboardParticleBatch.TMP_V1.z) + BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u, v, r, g, b, a)
        } else {
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(((-BillboardParticleBatch.TMP_V1.x) - BillboardParticleBatch.TMP_V2.x) + px, ((-BillboardParticleBatch.TMP_V1.y) - BillboardParticleBatch.TMP_V2.y) + py, ((-BillboardParticleBatch.TMP_V1.z) - BillboardParticleBatch.TMP_V2.z) + pz), u, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((BillboardParticleBatch.TMP_V1.x - BillboardParticleBatch.TMP_V2.x) + px, (BillboardParticleBatch.TMP_V1.y - BillboardParticleBatch.TMP_V2.y) + py, (BillboardParticleBatch.TMP_V1.z - BillboardParticleBatch.TMP_V2.z) + pz), u2, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((BillboardParticleBatch.TMP_V1.x + BillboardParticleBatch.TMP_V2.x) + px, (BillboardParticleBatch.TMP_V1.y + BillboardParticleBatch.TMP_V2.y) + py, (BillboardParticleBatch.TMP_V1.z + BillboardParticleBatch.TMP_V2.z) + pz), u2, v, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(((-BillboardParticleBatch.TMP_V1.x) + BillboardParticleBatch.TMP_V2.x) + px, ((-BillboardParticleBatch.TMP_V1.y) + BillboardParticleBatch.TMP_V2.y) + py, ((-BillboardParticleBatch.TMP_V1.z) + BillboardParticleBatch.TMP_V2.z) + pz), u, v, r, g, b, a)
        }
      }; p = p + 1; tp = tp + 1 } }
    }
  }
  private def fillVerticesToScreenCPU(particlesOffset: scala.Array[scala.Int]): scala.Unit = {
    val look: com.badlogic.gdx.math.Vector3 = BillboardParticleBatch.TMP_V3.set(this.camera.direction).scl(-1)
    val right: com.badlogic.gdx.math.Vector3 = BillboardParticleBatch.TMP_V4.set(this.camera.up).crs(look).nor()
    val up: com.badlogic.gdx.math.Vector3 = this.camera.up
    var tp: scala.Int = 0
    for (data <- renderData) {
      val scaleChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.scaleChannel
      val regionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.regionChannel
      val positionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.positionChannel
      val colorChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.colorChannel
      val rotationChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = data.rotationChannel;
      { var p: scala.Int = 0; val c: scala.Int = data.controller.particles.size; while (p < c) { {
        var baseOffset: scala.Int = (particlesOffset(tp) * this.currentVertexSize) * 4
        val scale: scala.Float = scaleChannel.data(p * scaleChannel.strideSize)
        val regionOffset: scala.Int = p * regionChannel.strideSize
        val positionOffset: scala.Int = p * positionChannel.strideSize
        val colorOffset: scala.Int = p * colorChannel.strideSize
        val rotationOffset: scala.Int = p * rotationChannel.strideSize
        val px: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)
        val py: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset)
        val pz: scala.Float = positionChannel.data(positionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset)
        val u: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.UOffset)
        val v: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VOffset)
        val u2: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.U2Offset)
        val v2: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.V2Offset)
        val sx: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfWidthOffset) * scale
        val sy: scala.Float = regionChannel.data(regionOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfHeightOffset) * scale
        val r: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.RedOffset)
        val g: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.GreenOffset)
        val b: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.BlueOffset)
        val a: scala.Float = colorChannel.data(colorOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.AlphaOffset)
        val cosRotation: scala.Float = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.CosineOffset)
        val sinRotation: scala.Float = rotationChannel.data(rotationOffset + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.SineOffset)
        BillboardParticleBatch.TMP_V1.set(right).scl(sx)
        BillboardParticleBatch.TMP_V2.set(up).scl(sy)
        if (cosRotation != 1) {
          BillboardParticleBatch.TMP_M3.setToRotation(look, cosRotation, sinRotation)
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((-BillboardParticleBatch.TMP_V1.x) - BillboardParticleBatch.TMP_V2.x, (-BillboardParticleBatch.TMP_V1.y) - BillboardParticleBatch.TMP_V2.y, (-BillboardParticleBatch.TMP_V1.z) - BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(BillboardParticleBatch.TMP_V1.x - BillboardParticleBatch.TMP_V2.x, BillboardParticleBatch.TMP_V1.y - BillboardParticleBatch.TMP_V2.y, BillboardParticleBatch.TMP_V1.z - BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u2, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(BillboardParticleBatch.TMP_V1.x + BillboardParticleBatch.TMP_V2.x, BillboardParticleBatch.TMP_V1.y + BillboardParticleBatch.TMP_V2.y, BillboardParticleBatch.TMP_V1.z + BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u2, v, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((-BillboardParticleBatch.TMP_V1.x) + BillboardParticleBatch.TMP_V2.x, (-BillboardParticleBatch.TMP_V1.y) + BillboardParticleBatch.TMP_V2.y, (-BillboardParticleBatch.TMP_V1.z) + BillboardParticleBatch.TMP_V2.z).mul(BillboardParticleBatch.TMP_M3).add(px, py, pz), u, v, r, g, b, a)
        } else {
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(((-BillboardParticleBatch.TMP_V1.x) - BillboardParticleBatch.TMP_V2.x) + px, ((-BillboardParticleBatch.TMP_V1.y) - BillboardParticleBatch.TMP_V2.y) + py, ((-BillboardParticleBatch.TMP_V1.z) - BillboardParticleBatch.TMP_V2.z) + pz), u, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((BillboardParticleBatch.TMP_V1.x - BillboardParticleBatch.TMP_V2.x) + px, (BillboardParticleBatch.TMP_V1.y - BillboardParticleBatch.TMP_V2.y) + py, (BillboardParticleBatch.TMP_V1.z - BillboardParticleBatch.TMP_V2.z) + pz), u2, v2, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set((BillboardParticleBatch.TMP_V1.x + BillboardParticleBatch.TMP_V2.x) + px, (BillboardParticleBatch.TMP_V1.y + BillboardParticleBatch.TMP_V2.y) + py, (BillboardParticleBatch.TMP_V1.z + BillboardParticleBatch.TMP_V2.z) + pz), u2, v, r, g, b, a)
          baseOffset = baseOffset + this.currentVertexSize
          BillboardParticleBatch.putVertex(this.vertices, baseOffset, BillboardParticleBatch.TMP_V6.set(((-BillboardParticleBatch.TMP_V1.x) + BillboardParticleBatch.TMP_V2.x) + px, ((-BillboardParticleBatch.TMP_V1.y) + BillboardParticleBatch.TMP_V2.y) + py, ((-BillboardParticleBatch.TMP_V1.z) + BillboardParticleBatch.TMP_V2.z) + pz), u, v, r, g, b, a)
        }
      }; p = p + 1; tp = tp + 1 } }
    }
  }
  def flush(offsets: scala.Array[scala.Int]): scala.Unit = {
    if (this.useGPU) {
      this.fillVerticesGPU(offsets)
    } else {
      if (this.mode == com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode.Screen) {
        this.fillVerticesToScreenCPU(offsets)
      } else {
        if (this.mode == com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode.ViewPoint) {
          this.fillVerticesToViewPointCPU(offsets)
        } else ()
      }
    }
    var addedVertexCount: scala.Int = 0
    val vCount: scala.Int = bufferedParticlesCount * 4;
    { var v: scala.Int = 0; while (v < vCount) { {
      addedVertexCount = java.lang.Math.min(vCount - v, BillboardParticleBatch.MAX_VERTICES_PER_MESH)
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderablePool.obtain()
      renderable.meshPart.size = (addedVertexCount / 4) * 6
      renderable.meshPart.mesh.setVertices(this.vertices, this.currentVertexSize * v, this.currentVertexSize * addedVertexCount)
      renderable.meshPart.update()
      this.renderables.add(renderable)
    }; v = v + addedVertexCount } }
  }
  def getRenderables(renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable], pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit = {
    for (renderable <- this.renderables) {
      renderables.add(pool.obtain().set(renderable))
    }
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.createSaveData("billboardBatch")
    data.save("cfg", new com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch.Config(this.useGPU, this.mode))
    data.saveAsset(manager.getAssetFileName(this.texture), classOf[com.badlogic.gdx.graphics.Texture])
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.getSaveData("billboardBatch")
    if (data != null) {
      this.setTexture(manager.get(data.loadAsset()).asInstanceOf[com.badlogic.gdx.graphics.Texture])
      val cfg: com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch.Config = data.load("cfg").asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.batches.BillboardParticleBatch.Config]
      this.setUseGpu(cfg.useGPU)
      this.setAlignMode(cfg.mode)
    } else ()
  }
  private class RenderablePool extends com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g3d.Renderable] {
    def newObject(): com.badlogic.gdx.graphics.g3d.Renderable = {
      return allocRenderable()
    }
  }
}
object BillboardParticleBatch {
  final val TMP_V1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_V2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_V3: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_V4: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_V5: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_V6: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val TMP_M3: com.badlogic.gdx.math.Matrix3 = new com.badlogic.gdx.math.Matrix3()
  final val sizeAndRotationUsage: scala.Int = 1 << 9
  final val directionUsage: scala.Int = 1 << 10
  private final val GPU_ATTRIBUTES: com.badlogic.gdx.graphics.VertexAttributes = new com.badlogic.gdx.graphics.VertexAttributes(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0"), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(BillboardParticleBatch.sizeAndRotationUsage, 4, "a_sizeAndRotation"))
  private final val CPU_ATTRIBUTES: com.badlogic.gdx.graphics.VertexAttributes = new com.badlogic.gdx.graphics.VertexAttributes(new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position, 3, com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates, 2, com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE + "0"), new com.badlogic.gdx.graphics.VertexAttribute(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked, 4, com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE))
  private final val GPU_POSITION_OFFSET: scala.Int = (BillboardParticleBatch.GPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position).offset / 4).asInstanceOf[scala.Short]
  private final val GPU_UV_OFFSET: scala.Int = (BillboardParticleBatch.GPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates).offset / 4).asInstanceOf[scala.Short]
  private final val GPU_SIZE_ROTATION_OFFSET: scala.Int = (BillboardParticleBatch.GPU_ATTRIBUTES.findByUsage(BillboardParticleBatch.sizeAndRotationUsage).offset / 4).asInstanceOf[scala.Short]
  private final val GPU_COLOR_OFFSET: scala.Int = (BillboardParticleBatch.GPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked).offset / 4).asInstanceOf[scala.Short]
  private final val GPU_VERTEX_SIZE: scala.Int = BillboardParticleBatch.GPU_ATTRIBUTES.vertexSize / 4
  private final val CPU_POSITION_OFFSET: scala.Int = (BillboardParticleBatch.CPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.Position).offset / 4).asInstanceOf[scala.Short]
  private final val CPU_UV_OFFSET: scala.Int = (BillboardParticleBatch.CPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates).offset / 4).asInstanceOf[scala.Short]
  private final val CPU_COLOR_OFFSET: scala.Int = (BillboardParticleBatch.CPU_ATTRIBUTES.findByUsage(com.badlogic.gdx.graphics.VertexAttributes.Usage.ColorUnpacked).offset / 4).asInstanceOf[scala.Short]
  private final val CPU_VERTEX_SIZE: scala.Int = BillboardParticleBatch.CPU_ATTRIBUTES.vertexSize / 4
  private final val MAX_PARTICLES_PER_MESH: scala.Int = java.lang.Short.MAX_VALUE / 4
  private final val MAX_VERTICES_PER_MESH: scala.Int = BillboardParticleBatch.MAX_PARTICLES_PER_MESH * 4
  private def putVertex(vertices: scala.Array[scala.Float], offset: scala.Int, x: scala.Float, y: scala.Float, z: scala.Float, u: scala.Float, v: scala.Float, scaleX: scala.Float, scaleY: scala.Float, cosRotation: scala.Float, sinRotation: scala.Float, r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    vertices(offset + BillboardParticleBatch.GPU_POSITION_OFFSET) = x
    vertices((offset + BillboardParticleBatch.GPU_POSITION_OFFSET) + 1) = y
    vertices((offset + BillboardParticleBatch.GPU_POSITION_OFFSET) + 2) = z
    vertices(offset + BillboardParticleBatch.GPU_UV_OFFSET) = u
    vertices((offset + BillboardParticleBatch.GPU_UV_OFFSET) + 1) = v
    vertices(offset + BillboardParticleBatch.GPU_SIZE_ROTATION_OFFSET) = scaleX
    vertices((offset + BillboardParticleBatch.GPU_SIZE_ROTATION_OFFSET) + 1) = scaleY
    vertices((offset + BillboardParticleBatch.GPU_SIZE_ROTATION_OFFSET) + 2) = cosRotation
    vertices((offset + BillboardParticleBatch.GPU_SIZE_ROTATION_OFFSET) + 3) = sinRotation
    vertices(offset + BillboardParticleBatch.GPU_COLOR_OFFSET) = r
    vertices((offset + BillboardParticleBatch.GPU_COLOR_OFFSET) + 1) = g
    vertices((offset + BillboardParticleBatch.GPU_COLOR_OFFSET) + 2) = b
    vertices((offset + BillboardParticleBatch.GPU_COLOR_OFFSET) + 3) = a
  }
  private def putVertex(vertices: scala.Array[scala.Float], offset: scala.Int, p: com.badlogic.gdx.math.Vector3, u: scala.Float, v: scala.Float, r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    vertices(offset + BillboardParticleBatch.CPU_POSITION_OFFSET) = p.x
    vertices((offset + BillboardParticleBatch.CPU_POSITION_OFFSET) + 1) = p.y
    vertices((offset + BillboardParticleBatch.CPU_POSITION_OFFSET) + 2) = p.z
    vertices(offset + BillboardParticleBatch.CPU_UV_OFFSET) = u
    vertices((offset + BillboardParticleBatch.CPU_UV_OFFSET) + 1) = v
    vertices(offset + BillboardParticleBatch.CPU_COLOR_OFFSET) = r
    vertices((offset + BillboardParticleBatch.CPU_COLOR_OFFSET) + 1) = g
    vertices((offset + BillboardParticleBatch.CPU_COLOR_OFFSET) + 2) = b
    vertices((offset + BillboardParticleBatch.CPU_COLOR_OFFSET) + 3) = a
  }
  class Config {
    var useGPU: scala.Boolean = false
    var mode: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode]
    def this(useGPU: scala.Boolean, mode: com.badlogic.gdx.graphics.g3d.particles.ParticleShader.AlignMode) = {
      this()
      this.useGPU = useGPU
      this.mode = mode
    }
  }
}