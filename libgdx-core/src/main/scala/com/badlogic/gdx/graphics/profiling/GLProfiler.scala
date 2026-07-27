package com.badlogic.gdx.graphics.profiling

class GLProfiler(graphics$p: com.badlogic.gdx.Graphics) {
  private var graphics: com.badlogic.gdx.Graphics = null.asInstanceOf[com.badlogic.gdx.Graphics]
  private var glInterceptor: com.badlogic.gdx.graphics.profiling.GLInterceptor = null.asInstanceOf[com.badlogic.gdx.graphics.profiling.GLInterceptor]
  private var listener: com.badlogic.gdx.graphics.profiling.GLErrorListener = null.asInstanceOf[com.badlogic.gdx.graphics.profiling.GLErrorListener]
  private var enabled: scala.Boolean = false
  val gl32: com.badlogic.gdx.graphics.GL32 = graphics$p.getGL32()
  val gl31: com.badlogic.gdx.graphics.GL31 = graphics$p.getGL31()
  val gl30: com.badlogic.gdx.graphics.GL30 = graphics$p.getGL30()
  this.graphics = graphics$p
  if (gl32 != null) {
    this.glInterceptor = new com.badlogic.gdx.graphics.profiling.GL32Interceptor(this, gl32)
  } else {
    if (gl31 != null) {
      this.glInterceptor = new com.badlogic.gdx.graphics.profiling.GL31Interceptor(this, gl31)
    } else {
      if (gl30 != null) {
        this.glInterceptor = new com.badlogic.gdx.graphics.profiling.GL30Interceptor(this, gl30)
      } else {
        this.glInterceptor = new com.badlogic.gdx.graphics.profiling.GL20Interceptor(this, graphics$p.getGL20())
      }
    }
  }
  this.listener = com.badlogic.gdx.graphics.profiling.GLErrorListener.LOGGING_LISTENER
  def enable(): scala.Unit = {
    if (this.enabled) {
      return
    } else ()
    if (this.glInterceptor.isInstanceOf[com.badlogic.gdx.graphics.GL32]) {
      this.graphics.setGL32(this.glInterceptor.asInstanceOf[com.badlogic.gdx.graphics.GL32])
    } else ()
    if (this.glInterceptor.isInstanceOf[com.badlogic.gdx.graphics.GL31]) {
      this.graphics.setGL31(this.glInterceptor.asInstanceOf[com.badlogic.gdx.graphics.GL31])
    } else ()
    if (this.glInterceptor.isInstanceOf[com.badlogic.gdx.graphics.GL30]) {
      this.graphics.setGL30(this.glInterceptor.asInstanceOf[com.badlogic.gdx.graphics.GL30])
    } else ()
    this.graphics.setGL20(this.glInterceptor)
    com.badlogic.gdx.Gdx.gl32 = this.graphics.getGL32()
    com.badlogic.gdx.Gdx.gl31 = this.graphics.getGL31()
    com.badlogic.gdx.Gdx.gl30 = this.graphics.getGL30()
    com.badlogic.gdx.Gdx.gl20 = this.graphics.getGL20()
    com.badlogic.gdx.Gdx.gl = this.graphics.getGL20()
    this.enabled = true
  }
  def disable(): scala.Unit = {
    if (!this.enabled) {
      return
    } else ()
    if (this.glInterceptor.isInstanceOf[com.badlogic.gdx.graphics.profiling.GL32Interceptor]) {
      this.graphics.setGL32(this.glInterceptor.asInstanceOf[com.badlogic.gdx.graphics.profiling.GL32Interceptor].gl32)
    } else ()
    if (this.glInterceptor.isInstanceOf[com.badlogic.gdx.graphics.profiling.GL31Interceptor]) {
      this.graphics.setGL31(this.glInterceptor.asInstanceOf[com.badlogic.gdx.graphics.profiling.GL31Interceptor].gl31)
    } else ()
    if (this.glInterceptor.isInstanceOf[com.badlogic.gdx.graphics.profiling.GL30Interceptor]) {
      this.graphics.setGL30(this.glInterceptor.asInstanceOf[com.badlogic.gdx.graphics.profiling.GL30Interceptor].gl30)
    } else ()
    if (this.glInterceptor.isInstanceOf[com.badlogic.gdx.graphics.profiling.GL20Interceptor]) {
      this.graphics.setGL20(this.graphics.getGL20().asInstanceOf[com.badlogic.gdx.graphics.profiling.GL20Interceptor].gl20)
    } else ()
    com.badlogic.gdx.Gdx.gl32 = this.graphics.getGL32()
    com.badlogic.gdx.Gdx.gl31 = this.graphics.getGL31()
    com.badlogic.gdx.Gdx.gl30 = this.graphics.getGL30()
    com.badlogic.gdx.Gdx.gl20 = this.graphics.getGL20()
    com.badlogic.gdx.Gdx.gl = this.graphics.getGL20()
    this.enabled = false
  }
  def setListener(errorListener: com.badlogic.gdx.graphics.profiling.GLErrorListener): scala.Unit = {
    this.listener = errorListener
  }
  def getListener(): com.badlogic.gdx.graphics.profiling.GLErrorListener = {
    return this.listener
  }
  def isEnabled(): scala.Boolean = {
    return this.enabled
  }
  def getCalls(): scala.Int = {
    return this.glInterceptor.getCalls()
  }
  def getTextureBindings(): scala.Int = {
    return this.glInterceptor.getTextureBindings()
  }
  def getDrawCalls(): scala.Int = {
    return this.glInterceptor.getDrawCalls()
  }
  def getShaderSwitches(): scala.Int = {
    return this.glInterceptor.getShaderSwitches()
  }
  def getVertexCount(): com.badlogic.gdx.math.FloatCounter = {
    return this.glInterceptor.getVertexCount()
  }
  def reset(): scala.Unit = {
    this.glInterceptor.reset()
  }
}