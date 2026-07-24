package com.badlogic.gdx.graphics.g3d.environment

class DirectionalShadowLight extends com.badlogic.gdx.graphics.g3d.environment.DirectionalLight with com.badlogic.gdx.graphics.g3d.environment.ShadowMap with com.badlogic.gdx.utils.Disposable {
  var fbo: com.badlogic.gdx.graphics.glutils.FrameBuffer = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.FrameBuffer]
  var cam: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  var halfDepth: scala.Float = 0.0f
  var halfHeight: scala.Float = 0.0f
  final val tmpV: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var textureDesc: com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?] = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?]]
  def this(shadowMapWidth: scala.Int, shadowMapHeight: scala.Int, shadowViewportWidth: scala.Float, shadowViewportHeight: scala.Float, shadowNear: scala.Float, shadowFar: scala.Float) = {
    this()
    this.fbo = new com.badlogic.gdx.graphics.glutils.FrameBuffer(com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, shadowMapWidth, shadowMapHeight, true)
    this.cam = new com.badlogic.gdx.graphics.OrthographicCamera(shadowViewportWidth, shadowViewportHeight)
    this.cam.near = shadowNear
    this.cam.far = shadowFar
    this.halfHeight = shadowViewportHeight * 0.5f
    this.halfDepth = shadowNear + (0.5f * (shadowFar - shadowNear))
    this.textureDesc = new com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor()
    this.textureDesc.minFilter = {
      this.textureDesc.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
      this.textureDesc.magFilter
    }
    this.textureDesc.uWrap = {
      this.textureDesc.vWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
      this.textureDesc.vWrap
    }
  }
  def update(camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    this.update(this.tmpV.set(camera.direction).scl(this.halfHeight), camera.direction)
  }
  def update(center: com.badlogic.gdx.math.Vector3, forward: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.cam.position.set(direction).scl(-this.halfDepth).add(center)
    this.cam.direction.set(direction).nor()
    this.cam.normalizeUp()
    this.cam.update()
  }
  def begin(camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    this.update(camera)
    this.begin()
  }
  def begin(center: com.badlogic.gdx.math.Vector3, forward: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.update(center, forward)
    this.begin()
  }
  def begin(): scala.Unit = {
    val w: scala.Int = this.fbo.getWidth()
    val h: scala.Int = this.fbo.getHeight()
    this.fbo.begin()
    com.badlogic.gdx.Gdx.gl.glViewport(0, 0, w, h)
    com.badlogic.gdx.Gdx.gl.glClearColor(1, 1, 1, 1)
    com.badlogic.gdx.Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT | com.badlogic.gdx.graphics.GL20.GL_DEPTH_BUFFER_BIT)
    com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST)
    com.badlogic.gdx.Gdx.gl.glScissor(1, 1, w - 2, h - 2)
  }
  def `end`(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_SCISSOR_TEST)
    this.fbo.`end`()
  }
  def getFrameBuffer(): com.badlogic.gdx.graphics.glutils.FrameBuffer = {
    return this.fbo
  }
  def getCamera(): com.badlogic.gdx.graphics.Camera = {
    return this.cam
  }
  def getProjViewTrans(): com.badlogic.gdx.math.Matrix4 = {
    return this.cam.combined
  }
  def getDepthMap(): com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor[?] = {
    this.textureDesc.texture = this.fbo.getColorBufferTexture()
    return this.textureDesc
  }
  def dispose(): scala.Unit = {
    if (this.fbo != null) {
      this.fbo.dispose()
    } else ()
    this.fbo = null
  }
}