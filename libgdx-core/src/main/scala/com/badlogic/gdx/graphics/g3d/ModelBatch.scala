package com.badlogic.gdx.graphics.g3d

class ModelBatch(context$p: com.badlogic.gdx.graphics.g3d.utils.RenderContext, shaderProvider$p: com.badlogic.gdx.graphics.g3d.utils.ShaderProvider, sorter$p: com.badlogic.gdx.graphics.g3d.utils.RenderableSorter) extends com.badlogic.gdx.utils.Disposable {
  var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  final val renderablesPool: com.badlogic.gdx.graphics.g3d.ModelBatch.RenderablePool = new com.badlogic.gdx.graphics.g3d.ModelBatch.RenderablePool()
  final val renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]()
  var context: com.badlogic.gdx.graphics.g3d.utils.RenderContext = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.RenderContext]
  private var ownContext: scala.Boolean = false
  var shaderProvider: com.badlogic.gdx.graphics.g3d.utils.ShaderProvider = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.ShaderProvider]
  var sorter: com.badlogic.gdx.graphics.g3d.utils.RenderableSorter = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.utils.RenderableSorter]
  def this(context: com.badlogic.gdx.graphics.g3d.utils.RenderContext, shaderProvider: com.badlogic.gdx.graphics.g3d.utils.ShaderProvider) = {
    this(context, shaderProvider, null)
  }
  def this(context: com.badlogic.gdx.graphics.g3d.utils.RenderContext, sorter: com.badlogic.gdx.graphics.g3d.utils.RenderableSorter) = {
    this(context, null, sorter)
  }
  def this(context: com.badlogic.gdx.graphics.g3d.utils.RenderContext) = {
    this(context, null, null)
  }
  def this(shaderProvider: com.badlogic.gdx.graphics.g3d.utils.ShaderProvider, sorter: com.badlogic.gdx.graphics.g3d.utils.RenderableSorter) = {
    this(null, shaderProvider, sorter)
  }
  def this(sorter: com.badlogic.gdx.graphics.g3d.utils.RenderableSorter) = {
    this(null, null, sorter)
  }
  def this(shaderProvider: com.badlogic.gdx.graphics.g3d.utils.ShaderProvider) = {
    this(null, shaderProvider, null)
  }
  def this(vertexShader: com.badlogic.gdx.files.FileHandle, fragmentShader: com.badlogic.gdx.files.FileHandle) = {
    this(null, new com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider(vertexShader, fragmentShader), null)
  }
  def this(vertexShader: java.lang.String, fragmentShader: java.lang.String) = {
    this(null, new com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider(vertexShader, fragmentShader), null)
  }
  def this() = {
    this(null, null, null)
  }
  this.sorter = if (sorter$p == null) new com.badlogic.gdx.graphics.g3d.utils.DefaultRenderableSorter() else sorter$p
  this.ownContext = context$p == null
  this.context = if (context$p == null) new com.badlogic.gdx.graphics.g3d.utils.RenderContext(new com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder(com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder.LRU, 1)) else context$p
  this.shaderProvider = if (shaderProvider$p == null) new com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider() else shaderProvider$p
  def begin(cam: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    if (this.camera != null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call end() first.")
    } else ()
    this.camera = cam
    if (this.ownContext) {
      this.context.begin()
    } else ()
  }
  def setCamera(cam: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    if (this.camera == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Call begin() first.")
    } else ()
    if (this.renderables.size > 0) {
      this.flush()
    } else ()
    this.camera = cam
  }
  def getCamera(): com.badlogic.gdx.graphics.Camera = {
    return this.camera
  }
  def ownsRenderContext(): scala.Boolean = {
    return this.ownContext
  }
  def getRenderContext(): com.badlogic.gdx.graphics.g3d.utils.RenderContext = {
    return this.context
  }
  def getShaderProvider(): com.badlogic.gdx.graphics.g3d.utils.ShaderProvider = {
    return this.shaderProvider
  }
  def getRenderableSorter(): com.badlogic.gdx.graphics.g3d.utils.RenderableSorter = {
    return this.sorter
  }
  def flush(): scala.Unit = {
    this.sorter.sort(this.camera, this.renderables)
    var currentShader: com.badlogic.gdx.graphics.g3d.Shader = null;
    { var i: scala.Int = 0; while (i < this.renderables.size) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderables.get(i)
      if (currentShader != renderable.shader) {
        if (currentShader != null) {
          currentShader.`end`()
        } else ()
        currentShader = renderable.shader
        currentShader.begin(this.camera, this.context)
      } else ()
      currentShader.render(renderable)
    }; i = i + 1 } }
    if (currentShader != null) {
      currentShader.`end`()
    } else ()
    this.renderablesPool.flush()
    this.renderables.clear()
  }
  def `end`(): scala.Unit = {
    this.flush()
    if (this.ownContext) {
      this.context.`end`()
    } else ()
    this.camera = null
  }
  def render(renderable: com.badlogic.gdx.graphics.g3d.Renderable): scala.Unit = {
    renderable.shader = this.shaderProvider.getShader(renderable)
    this.renderables.add(renderable)
  }
  def render(renderableProvider: com.badlogic.gdx.graphics.g3d.RenderableProvider): scala.Unit = {
    val offset: scala.Int = this.renderables.size
    renderableProvider.getRenderables(this.renderables, this.renderablesPool);
    { var i: scala.Int = offset; while (i < this.renderables.size) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderables.get(i)
      renderable.shader = this.shaderProvider.getShader(renderable)
    }; i = i + 1 } }
  }
  def render[T <: com.badlogic.gdx.graphics.g3d.RenderableProvider](renderableProviders: balticporter.runtime.JavaIterable[T]): scala.Unit = {
    for (renderableProvider <- renderableProviders) {
      this.render(renderableProvider)
    }
  }
  def render(renderableProvider: com.badlogic.gdx.graphics.g3d.RenderableProvider, environment: com.badlogic.gdx.graphics.g3d.Environment): scala.Unit = {
    val offset: scala.Int = this.renderables.size
    renderableProvider.getRenderables(this.renderables, this.renderablesPool);
    { var i: scala.Int = offset; while (i < this.renderables.size) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderables.get(i)
      renderable.environment = environment
      renderable.shader = this.shaderProvider.getShader(renderable)
    }; i = i + 1 } }
  }
  def render[T <: com.badlogic.gdx.graphics.g3d.RenderableProvider](renderableProviders: balticporter.runtime.JavaIterable[T], environment: com.badlogic.gdx.graphics.g3d.Environment): scala.Unit = {
    for (renderableProvider <- renderableProviders) {
      this.render(renderableProvider, environment)
    }
  }
  def render(renderableProvider: com.badlogic.gdx.graphics.g3d.RenderableProvider, shader: com.badlogic.gdx.graphics.g3d.Shader): scala.Unit = {
    val offset: scala.Int = this.renderables.size
    renderableProvider.getRenderables(this.renderables, this.renderablesPool);
    { var i: scala.Int = offset; while (i < this.renderables.size) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderables.get(i)
      renderable.shader = shader
      renderable.shader = this.shaderProvider.getShader(renderable)
    }; i = i + 1 } }
  }
  def render[T <: com.badlogic.gdx.graphics.g3d.RenderableProvider](renderableProviders: balticporter.runtime.JavaIterable[T], shader: com.badlogic.gdx.graphics.g3d.Shader): scala.Unit = {
    for (renderableProvider <- renderableProviders) {
      this.render(renderableProvider, shader)
    }
  }
  def render(renderableProvider: com.badlogic.gdx.graphics.g3d.RenderableProvider, environment: com.badlogic.gdx.graphics.g3d.Environment, shader: com.badlogic.gdx.graphics.g3d.Shader): scala.Unit = {
    val offset: scala.Int = this.renderables.size
    renderableProvider.getRenderables(this.renderables, this.renderablesPool);
    { var i: scala.Int = offset; while (i < this.renderables.size) { {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = this.renderables.get(i)
      renderable.environment = environment
      renderable.shader = shader
      renderable.shader = this.shaderProvider.getShader(renderable)
    }; i = i + 1 } }
  }
  def render[T <: com.badlogic.gdx.graphics.g3d.RenderableProvider](renderableProviders: balticporter.runtime.JavaIterable[T], environment: com.badlogic.gdx.graphics.g3d.Environment, shader: com.badlogic.gdx.graphics.g3d.Shader): scala.Unit = {
    for (renderableProvider <- renderableProviders) {
      this.render(renderableProvider, environment, shader)
    }
  }
  @java.lang.Override
  def dispose(): scala.Unit = {
    this.shaderProvider.dispose()
  }
}
object ModelBatch {
  class RenderablePool extends com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g3d.Renderable] {
    @java.lang.Override
    def newObject(): com.badlogic.gdx.graphics.g3d.Renderable = {
      return new com.badlogic.gdx.graphics.g3d.Renderable()
    }
    @java.lang.Override
    def obtain(): com.badlogic.gdx.graphics.g3d.Renderable = {
      val renderable: com.badlogic.gdx.graphics.g3d.Renderable = super.obtain()
      renderable.environment = null
      renderable.material = null
      renderable.meshPart.set("", null, 0, 0, 0)
      renderable.shader = null
      renderable.userData = null
      return renderable
    }
  }
  object RenderablePool {
    export com.badlogic.gdx.utils.FlushablePool.*
  }
}