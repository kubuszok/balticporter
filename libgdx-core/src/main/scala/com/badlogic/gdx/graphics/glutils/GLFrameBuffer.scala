package com.badlogic.gdx.graphics.glutils

abstract class GLFrameBuffer[T <: com.badlogic.gdx.graphics.GLTexture] extends com.badlogic.gdx.utils.Disposable {
  var textureAttachments: com.badlogic.gdx.utils.Array[T] = new com.badlogic.gdx.utils.Array[T]()
  var framebufferHandle: scala.Int = 0
  var depthbufferHandle: scala.Int = 0
  var stencilbufferHandle: scala.Int = 0
  var depthStencilPackedBufferHandle: scala.Int = 0
  var hasDepthStencilPackedBuffer: scala.Boolean = false
  final val colorBufferHandles: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  var isMRT: scala.Boolean = false
  var bufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[? <: GLFrameBuffer[T]] = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[? <: GLFrameBuffer[T]]]
  private var defaultDrawBuffers: java.nio.IntBuffer = null.asInstanceOf[java.nio.IntBuffer]
  private var drawBuffersForTransfer: java.nio.IntBuffer = null.asInstanceOf[java.nio.IntBuffer]
  def this(bufferBuilder: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[? <: GLFrameBuffer[T]]) = {
    this()
    this.bufferBuilder = bufferBuilder
    this.build()
  }
  def getColorBufferTexture(): T = {
    return this.textureAttachments.first()
  }
  def getTextureAttachments(): com.badlogic.gdx.utils.Array[T] = {
    return this.textureAttachments
  }
  def createTexture(attachmentSpec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec): T
  def disposeColorTexture(colorTexture: T): scala.Unit
  def attachFrameBufferColorTexture(texture: T): scala.Unit
  def build(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    this.checkValidBuilder()
    if (!GLFrameBuffer.defaultFramebufferHandleInitialized) {
      GLFrameBuffer.defaultFramebufferHandleInitialized = true
      if (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.iOS) {
        val intbuf: java.nio.IntBuffer = java.nio.ByteBuffer.allocateDirect((16 * java.lang.Integer.SIZE) / 8).order(java.nio.ByteOrder.nativeOrder()).asIntBuffer()
        gl.glGetIntegerv(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_BINDING, intbuf)
        GLFrameBuffer.defaultFramebufferHandle = intbuf.get(0)
      } else {
        GLFrameBuffer.defaultFramebufferHandle = 0
      }
    } else ()
    this.framebufferHandle = gl.glGenFramebuffer()
    gl.glBindFramebuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, this.framebufferHandle)
    val width: scala.Int = this.bufferBuilder.width
    val height: scala.Int = this.bufferBuilder.height
    if (this.bufferBuilder.hasDepthRenderBuffer) {
      this.depthbufferHandle = gl.glGenRenderbuffer()
      gl.glBindRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.depthbufferHandle)
      if (this.bufferBuilder.samples > 0) {
        com.badlogic.gdx.Gdx.gl30.glRenderbufferStorageMultisample(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.samples, this.bufferBuilder.depthRenderBufferSpec.internalFormat, width, height)
      } else {
        gl.glRenderbufferStorage(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.depthRenderBufferSpec.internalFormat, width, height)
      }
    } else ()
    if (this.bufferBuilder.hasStencilRenderBuffer) {
      this.stencilbufferHandle = gl.glGenRenderbuffer()
      gl.glBindRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.stencilbufferHandle)
      if (this.bufferBuilder.samples > 0) {
        com.badlogic.gdx.Gdx.gl30.glRenderbufferStorageMultisample(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.samples, this.bufferBuilder.stencilRenderBufferSpec.internalFormat, width, height)
      } else {
        gl.glRenderbufferStorage(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.stencilRenderBufferSpec.internalFormat, width, height)
      }
    } else ()
    if (this.bufferBuilder.hasPackedStencilDepthRenderBuffer) {
      this.depthStencilPackedBufferHandle = gl.glGenRenderbuffer()
      gl.glBindRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.depthStencilPackedBufferHandle)
      if (this.bufferBuilder.samples > 0) {
        com.badlogic.gdx.Gdx.gl30.glRenderbufferStorageMultisample(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.samples, this.bufferBuilder.packedStencilDepthRenderBufferSpec.internalFormat, width, height)
      } else {
        gl.glRenderbufferStorage(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.packedStencilDepthRenderBufferSpec.internalFormat, width, height)
      }
      this.hasDepthStencilPackedBuffer = true
    } else ()
    this.isMRT = this.bufferBuilder.textureAttachmentSpecs.size > 1
    var colorAttachmentCounter: scala.Int = 0
    if (this.isMRT) {
      for (attachmentSpec <- this.bufferBuilder.textureAttachmentSpecs) {
        val texture: T = this.createTexture(attachmentSpec)
        this.textureAttachments.add(texture)
        if (attachmentSpec.isColorTexture()) {
          gl.glFramebufferTexture2D(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL30.GL_COLOR_ATTACHMENT0 + colorAttachmentCounter, com.badlogic.gdx.graphics.GL30.GL_TEXTURE_2D, texture.getTextureObjectHandle(), 0)
          colorAttachmentCounter = colorAttachmentCounter + 1
        } else {
          if (attachmentSpec.isDepth) {
            gl.glFramebufferTexture2D(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_DEPTH_ATTACHMENT, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, texture.getTextureObjectHandle(), 0)
          } else {
            if (attachmentSpec.isStencil) {
              gl.glFramebufferTexture2D(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_STENCIL_ATTACHMENT, com.badlogic.gdx.graphics.GL20.GL_TEXTURE_2D, texture.getTextureObjectHandle(), 0)
            } else ()
          }
        }
      }
    } else {
      if (this.bufferBuilder.textureAttachmentSpecs.size > 0) {
        val texture: T = this.createTexture(this.bufferBuilder.textureAttachmentSpecs.first())
        this.textureAttachments.add(texture)
        gl.glBindTexture(texture.glTarget, texture.getTextureObjectHandle())
      } else ()
    }
    for (colorBufferSpec <- this.bufferBuilder.colorRenderBufferSpecs) {
      val colorbufferHandle: scala.Int = gl.glGenRenderbuffer()
      gl.glBindRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, colorbufferHandle)
      if (this.bufferBuilder.samples > 0) {
        com.badlogic.gdx.Gdx.gl30.glRenderbufferStorageMultisample(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.samples, colorBufferSpec.internalFormat, width, height)
      } else {
        gl.glRenderbufferStorage(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, colorBufferSpec.internalFormat, width, height)
      }
      com.badlogic.gdx.Gdx.gl.glFramebufferRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_COLOR_ATTACHMENT0 + colorAttachmentCounter, com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, colorbufferHandle)
      this.colorBufferHandles.add(colorbufferHandle)
      colorAttachmentCounter = colorAttachmentCounter + 1
    }
    if (this.isMRT || (this.bufferBuilder.samples > 0)) {
      this.defaultDrawBuffers = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(colorAttachmentCounter);
      { var i: scala.Int = 0; while (i < colorAttachmentCounter) { {
        this.defaultDrawBuffers.put(com.badlogic.gdx.graphics.GL30.GL_COLOR_ATTACHMENT0 + i)
      }; i = i + 1 } }
      this.defaultDrawBuffers.asInstanceOf[java.nio.Buffer].position(0)
      com.badlogic.gdx.Gdx.gl30.glDrawBuffers(colorAttachmentCounter, this.defaultDrawBuffers)
    } else {
      if (this.bufferBuilder.textureAttachmentSpecs.size > 0) {
        this.attachFrameBufferColorTexture(this.textureAttachments.first())
      } else ()
    }
    if (this.bufferBuilder.hasDepthRenderBuffer) {
      gl.glFramebufferRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_DEPTH_ATTACHMENT, com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.depthbufferHandle)
    } else ()
    if (this.bufferBuilder.hasStencilRenderBuffer) {
      gl.glFramebufferRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_STENCIL_ATTACHMENT, com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.stencilbufferHandle)
    } else ()
    if (this.bufferBuilder.hasPackedStencilDepthRenderBuffer) {
      gl.glFramebufferRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL30.GL_DEPTH_STENCIL_ATTACHMENT, com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.depthStencilPackedBufferHandle)
    } else ()
    gl.glBindRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, 0)
    for (texture <- this.textureAttachments) {
      gl.glBindTexture(texture.glTarget, 0)
    }
    var result: scala.Int = gl.glCheckFramebufferStatus(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER)
    if ((((result == com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_UNSUPPORTED) && this.bufferBuilder.hasDepthRenderBuffer) && this.bufferBuilder.hasStencilRenderBuffer) && (com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_OES_packed_depth_stencil") || com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_EXT_packed_depth_stencil"))) {
      if (this.bufferBuilder.hasDepthRenderBuffer) {
        gl.glDeleteRenderbuffer(this.depthbufferHandle)
        this.depthbufferHandle = 0
      } else ()
      if (this.bufferBuilder.hasStencilRenderBuffer) {
        gl.glDeleteRenderbuffer(this.stencilbufferHandle)
        this.stencilbufferHandle = 0
      } else ()
      if (this.bufferBuilder.hasPackedStencilDepthRenderBuffer) {
        gl.glDeleteRenderbuffer(this.depthStencilPackedBufferHandle)
        this.depthStencilPackedBufferHandle = 0
      } else ()
      this.depthStencilPackedBufferHandle = gl.glGenRenderbuffer()
      this.hasDepthStencilPackedBuffer = true
      gl.glBindRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.depthStencilPackedBufferHandle)
      if (this.bufferBuilder.samples > 0) {
        com.badlogic.gdx.Gdx.gl30.glRenderbufferStorageMultisample(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.bufferBuilder.samples, GLFrameBuffer.GL_DEPTH24_STENCIL8_OES, width, height)
      } else {
        gl.glRenderbufferStorage(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, GLFrameBuffer.GL_DEPTH24_STENCIL8_OES, width, height)
      }
      gl.glBindRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, 0)
      gl.glFramebufferRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_DEPTH_ATTACHMENT, com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.depthStencilPackedBufferHandle)
      gl.glFramebufferRenderbuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, com.badlogic.gdx.graphics.GL20.GL_STENCIL_ATTACHMENT, com.badlogic.gdx.graphics.GL20.GL_RENDERBUFFER, this.depthStencilPackedBufferHandle)
      result = gl.glCheckFramebufferStatus(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER)
    } else ()
    gl.glBindFramebuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, GLFrameBuffer.defaultFramebufferHandle)
    if (result != com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_COMPLETE) {
      for (texture <- this.textureAttachments) {
        this.disposeColorTexture(texture)
      }
      if (this.hasDepthStencilPackedBuffer) {
        gl.glDeleteBuffer(this.depthStencilPackedBufferHandle)
      } else {
        if (this.bufferBuilder.hasDepthRenderBuffer) {
          gl.glDeleteRenderbuffer(this.depthbufferHandle)
        } else ()
        if (this.bufferBuilder.hasStencilRenderBuffer) {
          gl.glDeleteRenderbuffer(this.stencilbufferHandle)
        } else ()
      }
      gl.glDeleteFramebuffer(this.framebufferHandle)
      if (result == com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT) {
        throw new java.lang.IllegalStateException("Frame buffer couldn't be constructed: incomplete attachment")
      } else ()
      if (result == com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS) {
        throw new java.lang.IllegalStateException("Frame buffer couldn't be constructed: incomplete dimensions")
      } else ()
      if (result == com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT) {
        throw new java.lang.IllegalStateException("Frame buffer couldn't be constructed: missing attachment")
      } else ()
      if (result == com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER_UNSUPPORTED) {
        throw new java.lang.IllegalStateException("Frame buffer couldn't be constructed: unsupported combination of formats")
      } else ()
      if (result == com.badlogic.gdx.graphics.GL30.GL_FRAMEBUFFER_INCOMPLETE_MULTISAMPLE) {
        throw new java.lang.IllegalStateException("Frame buffer couldn't be constructed: multisample mismatch")
      } else ()
      throw new java.lang.IllegalStateException("Frame buffer couldn't be constructed: unknown error " + result)
    } else ()
    GLFrameBuffer.addManagedFrameBuffer(com.badlogic.gdx.Gdx.app, this)
  }
  private def checkValidBuilder(): scala.Unit = {
    if ((this.bufferBuilder.samples > 0) && (!com.badlogic.gdx.Gdx.graphics.isGL30Available())) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Framebuffer multisample requires GLES 3.0+")
    } else ()
    if ((this.bufferBuilder.samples > 0) && (this.bufferBuilder.textureAttachmentSpecs.size > 0)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Framebuffer multisample with texture attachments not yet supported")
    } else ()
    val runningGL30: scala.Boolean = com.badlogic.gdx.Gdx.graphics.isGL30Available()
    if (!runningGL30) {
      val supportsPackedDepthStencil: scala.Boolean = com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_OES_packed_depth_stencil") || com.badlogic.gdx.Gdx.graphics.supportsExtension("GL_EXT_packed_depth_stencil")
      if (this.bufferBuilder.hasPackedStencilDepthRenderBuffer && (!supportsPackedDepthStencil)) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Packed Stencil/Render render buffers are not available on GLES 2.0")
      } else ()
      if (this.bufferBuilder.textureAttachmentSpecs.size > 1) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Multiple render targets not available on GLES 2.0")
      } else ()
      for (spec <- this.bufferBuilder.textureAttachmentSpecs) {
        if (spec.isDepth) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Depth texture FrameBuffer Attachment not available on GLES 2.0")
        } else ()
        if (spec.isStencil) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Stencil texture FrameBuffer Attachment not available on GLES 2.0")
        } else ()
        if (spec.isFloat) {
          if (!com.badlogic.gdx.Gdx.graphics.supportsExtension("OES_texture_float")) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Float texture FrameBuffer Attachment not available on GLES 2.0")
          } else ()
        } else ()
      }
    } else ()
    if (this.bufferBuilder.hasPackedStencilDepthRenderBuffer) {
      if (this.bufferBuilder.hasDepthRenderBuffer || this.bufferBuilder.hasStencilRenderBuffer) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Frame buffer couldn't be constructed: packed stencil depth buffer cannot be specified together with separated depth or stencil buffer")
      } else ()
    } else ()
  }
  def dispose(): scala.Unit = {
    val gl: com.badlogic.gdx.graphics.GL20 = com.badlogic.gdx.Gdx.gl20
    for (texture <- this.textureAttachments) {
      this.disposeColorTexture(texture)
    }
    gl.glDeleteRenderbuffer(this.depthStencilPackedBufferHandle)
    gl.glDeleteRenderbuffer(this.depthbufferHandle)
    gl.glDeleteRenderbuffer(this.stencilbufferHandle)
    gl.glDeleteFramebuffer(this.framebufferHandle)
    if (GLFrameBuffer.buffers.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]]) != null) {
      GLFrameBuffer.buffers.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]]).removeValue(this, true)
    } else ()
  }
  def bind(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glBindFramebuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, this.framebufferHandle)
  }
  def begin(): scala.Unit = {
    this.bind()
    this.setFrameBufferViewport()
  }
  def setFrameBufferViewport(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glViewport(0, 0, this.bufferBuilder.width, this.bufferBuilder.height)
  }
  def `end`(): scala.Unit = {
    this.`end`(0, 0, com.badlogic.gdx.Gdx.graphics.getBackBufferWidth(), com.badlogic.gdx.Gdx.graphics.getBackBufferHeight())
  }
  def `end`(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
    GLFrameBuffer.unbind()
    com.badlogic.gdx.Gdx.gl20.glViewport(x, y, width, height)
  }
  def transfer(destination: GLFrameBuffer[T]): scala.Unit = {
    var copyBits: scala.Int = 0
    for (attachment <- destination.bufferBuilder.textureAttachmentSpecs) {
      if (attachment.isDepth && (this.bufferBuilder.hasDepthRenderBuffer || this.bufferBuilder.hasPackedStencilDepthRenderBuffer)) {
        copyBits = copyBits | com.badlogic.gdx.graphics.GL20.GL_DEPTH_BUFFER_BIT
      } else {
        if (attachment.isStencil && (this.bufferBuilder.hasStencilRenderBuffer || this.bufferBuilder.hasPackedStencilDepthRenderBuffer)) {
          copyBits = copyBits | com.badlogic.gdx.graphics.GL20.GL_STENCIL_BUFFER_BIT
        } else {
          if (this.colorBufferHandles.size > 0) {
            copyBits = copyBits | com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT
          } else ()
        }
      }
    }
    this.transfer(destination, copyBits)
  }
  def transfer(destination: GLFrameBuffer[T], copyBits$arg: scala.Int): scala.Unit = {
    var copyBits: scala.Int = copyBits$arg
    if (this.drawBuffersForTransfer == null) {
      com.badlogic.gdx.Gdx.gl.glGetIntegerv(com.badlogic.gdx.graphics.GL30.GL_MAX_COLOR_ATTACHMENTS, {
        this.drawBuffersForTransfer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(1)
        this.drawBuffersForTransfer
      })
      this.drawBuffersForTransfer = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(this.drawBuffersForTransfer.get(0))
    } else ()
    if ((destination.getWidth() != this.getWidth()) || (destination.getHeight() != this.getHeight())) {
      throw new java.lang.IllegalArgumentException("source and destination frame buffers must have same size.")
    } else ()
    com.badlogic.gdx.Gdx.gl.glBindFramebuffer(com.badlogic.gdx.graphics.GL30.GL_READ_FRAMEBUFFER, this.framebufferHandle)
    com.badlogic.gdx.Gdx.gl.glBindFramebuffer(com.badlogic.gdx.graphics.GL30.GL_DRAW_FRAMEBUFFER, destination.framebufferHandle)
    if ((copyBits & com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT) == com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT) {
      var totalColorAttachments: scala.Int = 0
      for (textureAttachmentSpec <- destination.bufferBuilder.textureAttachmentSpecs) {
        if (textureAttachmentSpec.isColorTexture()) {
          totalColorAttachments = totalColorAttachments + 1
        } else ()
      }
      var colorBufferIndex: scala.Int = 0
      this.drawBuffersForTransfer.clear()
      for (attachment <- destination.bufferBuilder.textureAttachmentSpecs) {
        if (attachment.isColorTexture()) {
          com.badlogic.gdx.Gdx.gl30.glReadBuffer(com.badlogic.gdx.graphics.GL30.GL_COLOR_ATTACHMENT0 + colorBufferIndex);
          { var i: scala.Int = 0; while (i < totalColorAttachments) { {
            if (colorBufferIndex == i) {
              this.drawBuffersForTransfer.put(com.badlogic.gdx.graphics.GL30.GL_COLOR_ATTACHMENT0 + i)
            } else {
              this.drawBuffersForTransfer.put(com.badlogic.gdx.graphics.GL30.GL_NONE)
            }
          }; i = i + 1 } }
          this.drawBuffersForTransfer.flip()
          com.badlogic.gdx.Gdx.gl30.glDrawBuffers(this.drawBuffersForTransfer.limit(), this.drawBuffersForTransfer)
          com.badlogic.gdx.Gdx.gl30.glBlitFramebuffer(0, 0, this.getWidth(), this.getHeight(), 0, 0, destination.getWidth(), destination.getHeight(), copyBits, com.badlogic.gdx.graphics.GL20.GL_NEAREST)
          copyBits = com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT
          colorBufferIndex = colorBufferIndex + 1
        } else ()
      }
    } else ()
    if (copyBits != com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT) {
      com.badlogic.gdx.Gdx.gl30.glBlitFramebuffer(0, 0, this.getWidth(), this.getHeight(), 0, 0, destination.getWidth(), destination.getHeight(), copyBits, com.badlogic.gdx.graphics.GL20.GL_NEAREST)
    } else ()
    if (destination.defaultDrawBuffers != null) {
      com.badlogic.gdx.Gdx.gl30.glDrawBuffers(destination.defaultDrawBuffers.limit(), destination.defaultDrawBuffers)
    } else ()
    com.badlogic.gdx.Gdx.gl.glBindFramebuffer(com.badlogic.gdx.graphics.GL30.GL_READ_FRAMEBUFFER, 0)
    com.badlogic.gdx.Gdx.gl.glBindFramebuffer(com.badlogic.gdx.graphics.GL30.GL_DRAW_FRAMEBUFFER, 0)
  }
  def getFramebufferHandle(): scala.Int = {
    return this.framebufferHandle
  }
  def getDepthBufferHandle(): scala.Int = {
    return this.depthbufferHandle
  }
  def getColorBufferHandle(n: scala.Int): scala.Int = {
    return this.colorBufferHandles.get(n)
  }
  def getStencilBufferHandle(): scala.Int = {
    return this.stencilbufferHandle
  }
  def getDepthStencilPackedBuffer(): scala.Int = {
    return this.depthStencilPackedBufferHandle
  }
  def getHeight(): scala.Int = {
    return this.bufferBuilder.height
  }
  def getWidth(): scala.Int = {
    return this.bufferBuilder.width
  }
}
object GLFrameBuffer {
  final val buffers: scala.collection.mutable.Map[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]] = new scala.collection.mutable.HashMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]]()
  final val GL_DEPTH24_STENCIL8_OES: scala.Int = 35056
  var defaultFramebufferHandle: scala.Int = 0
  var defaultFramebufferHandleInitialized: scala.Boolean = false
  def unbind(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl20.glBindFramebuffer(com.badlogic.gdx.graphics.GL20.GL_FRAMEBUFFER, GLFrameBuffer.defaultFramebufferHandle)
  }
  private def addManagedFrameBuffer(app: com.badlogic.gdx.Application, frameBuffer: GLFrameBuffer[?]): scala.Unit = {
    var managedResources: com.badlogic.gdx.utils.Array[GLFrameBuffer[?]] = GLFrameBuffer.buffers.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]])
    if (managedResources == null) {
      managedResources = new com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]()
    } else ()
    managedResources.add(frameBuffer)
    GLFrameBuffer.buffers.update(app, managedResources)
  }
  def invalidateAllFrameBuffers(app: com.badlogic.gdx.Application): scala.Unit = {
    if (com.badlogic.gdx.Gdx.gl20 == null) {
      return
    } else ()
    val bufferArray: com.badlogic.gdx.utils.Array[GLFrameBuffer[?]] = GLFrameBuffer.buffers.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]])
    if (bufferArray == null) {
      return
    } else ();
    { var i: scala.Int = 0; while (i < bufferArray.size) { {
      bufferArray.get(i).build()
    }; i = i + 1 } }
  }
  def clearAllFrameBuffers(app: com.badlogic.gdx.Application): scala.Unit = {
    GLFrameBuffer.buffers -= app
  }
  def getManagedStatus(builder: java.lang.StringBuilder): java.lang.StringBuilder = {
    builder.append("Managed buffers/app: { ")
    for (app <- GLFrameBuffer.buffers.keySet) {
      builder.append(GLFrameBuffer.buffers.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[GLFrameBuffer[?]]]).size)
      builder.append(" ")
    }
    builder.append("}")
    return builder
  }
  def getManagedStatus(): java.lang.String = {
    return GLFrameBuffer.getManagedStatus(new java.lang.StringBuilder()).toString()
  }
  class FrameBufferTextureAttachmentSpec {
    var internalFormat: scala.Int = 0
    var format: scala.Int = 0
    var `type`: scala.Int = 0
    var isFloat: scala.Boolean = false
    var isGpuOnly: scala.Boolean = false
    var isDepth: scala.Boolean = false
    var isStencil: scala.Boolean = false
    def this(internalformat: scala.Int, format: scala.Int, `type`: scala.Int) = {
      this()
      this.internalFormat = internalformat
      this.format = format
      this.`type` = `type`
    }
    def isColorTexture(): scala.Boolean = {
      return (!this.isDepth) && (!this.isStencil)
    }
  }
  class FrameBufferRenderBufferAttachmentSpec {
    var internalFormat: scala.Int = 0
    def this(internalFormat: scala.Int) = {
      this()
      this.internalFormat = internalFormat
    }
  }
  abstract class GLFrameBufferBuilder[U <: GLFrameBuffer[? <: com.badlogic.gdx.graphics.GLTexture]] {
    var width: scala.Int = 0
    var height: scala.Int = 0
    var samples: scala.Int = 0
    var textureAttachmentSpecs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec]()
    var colorRenderBufferSpecs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec]()
    var stencilRenderBufferSpec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec]
    var depthRenderBufferSpec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec]
    var packedStencilDepthRenderBufferSpec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec]
    var hasStencilRenderBuffer: scala.Boolean = false
    var hasDepthRenderBuffer: scala.Boolean = false
    var hasPackedStencilDepthRenderBuffer: scala.Boolean = false
    def this(width: scala.Int, height: scala.Int, samples: scala.Int) = {
      this()
      this.width = width
      this.height = height
      this.samples = samples
    }
    def this(width: scala.Int, height: scala.Int) = {
      this(width, height, 0)
    }
    def addColorTextureAttachment(internalFormat: scala.Int, format: scala.Int, `type`: scala.Int): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      this.textureAttachmentSpecs.add(new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec(internalFormat, format, `type`))
      return this
    }
    def addBasicColorTextureAttachment(format: com.badlogic.gdx.graphics.Pixmap.Format): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      val glFormat: scala.Int = com.badlogic.gdx.graphics.Pixmap.Format.toGlFormat(format)
      val glType: scala.Int = com.badlogic.gdx.graphics.Pixmap.Format.toGlType(format)
      return this.addColorTextureAttachment(glFormat, glFormat, glType)
    }
    def addFloatAttachment(internalFormat: scala.Int, format: scala.Int, `type`: scala.Int, gpuOnly: scala.Boolean): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      val spec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec(internalFormat, format, `type`)
      spec.isFloat = true
      spec.isGpuOnly = gpuOnly
      this.textureAttachmentSpecs.add(spec)
      return this
    }
    def addDepthTextureAttachment(internalFormat: scala.Int, `type`: scala.Int): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      val spec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec(internalFormat, com.badlogic.gdx.graphics.GL30.GL_DEPTH_COMPONENT, `type`)
      spec.isDepth = true
      this.textureAttachmentSpecs.add(spec)
      return this
    }
    def addStencilTextureAttachment(internalFormat: scala.Int, `type`: scala.Int): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      val spec: com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferTextureAttachmentSpec(internalFormat, com.badlogic.gdx.graphics.GL30.GL_STENCIL_ATTACHMENT, `type`)
      spec.isStencil = true
      this.textureAttachmentSpecs.add(spec)
      return this
    }
    def addDepthRenderBuffer(internalFormat: scala.Int): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      this.depthRenderBufferSpec = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec(internalFormat)
      this.hasDepthRenderBuffer = true
      return this
    }
    def addColorRenderBuffer(internalFormat: scala.Int): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      this.colorRenderBufferSpecs.add(new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec(internalFormat))
      return this
    }
    def addStencilRenderBuffer(internalFormat: scala.Int): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      this.stencilRenderBufferSpec = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec(internalFormat)
      this.hasStencilRenderBuffer = true
      return this
    }
    def addStencilDepthPackedRenderBuffer(internalFormat: scala.Int): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      this.packedStencilDepthRenderBufferSpec = new com.badlogic.gdx.graphics.glutils.GLFrameBuffer.FrameBufferRenderBufferAttachmentSpec(internalFormat)
      this.hasPackedStencilDepthRenderBuffer = true
      return this
    }
    def addBasicDepthRenderBuffer(): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      return this.addDepthRenderBuffer(com.badlogic.gdx.graphics.GL20.GL_DEPTH_COMPONENT16)
    }
    def addBasicStencilRenderBuffer(): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      return this.addStencilRenderBuffer(com.badlogic.gdx.graphics.GL20.GL_STENCIL_INDEX8)
    }
    def addBasicStencilDepthPackedRenderBuffer(): com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[U] = {
      return this.addStencilDepthPackedRenderBuffer(com.badlogic.gdx.graphics.GL30.GL_DEPTH24_STENCIL8)
    }
    def build(): U
  }
  class FrameBufferBuilder extends com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[com.badlogic.gdx.graphics.glutils.FrameBuffer] {
    def this(width: scala.Int, height: scala.Int) = {
      this()
    }
    def this(width: scala.Int, height: scala.Int, samples: scala.Int) = {
      this()
    }
    def build(): com.badlogic.gdx.graphics.glutils.FrameBuffer = {
      return new com.badlogic.gdx.graphics.glutils.FrameBuffer(this)
    }
  }
  class FloatFrameBufferBuilder extends com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[com.badlogic.gdx.graphics.glutils.FloatFrameBuffer] {
    def this(width: scala.Int, height: scala.Int) = {
      this()
    }
    def this(width: scala.Int, height: scala.Int, samples: scala.Int) = {
      this()
    }
    def build(): com.badlogic.gdx.graphics.glutils.FloatFrameBuffer = {
      return new com.badlogic.gdx.graphics.glutils.FloatFrameBuffer(this)
    }
  }
  class FrameBufferCubemapBuilder extends com.badlogic.gdx.graphics.glutils.GLFrameBuffer.GLFrameBufferBuilder[com.badlogic.gdx.graphics.glutils.FrameBufferCubemap] {
    def this(width: scala.Int, height: scala.Int) = {
      this()
    }
    def this(width: scala.Int, height: scala.Int, samples: scala.Int) = {
      this()
    }
    def build(): com.badlogic.gdx.graphics.glutils.FrameBufferCubemap = {
      return new com.badlogic.gdx.graphics.glutils.FrameBufferCubemap(this)
    }
  }
}