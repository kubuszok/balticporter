package com.badlogic.gdx.graphics

class Texture3D extends com.badlogic.gdx.graphics.GLTexture {
  private var data: com.badlogic.gdx.graphics.Texture3DData = null.asInstanceOf[com.badlogic.gdx.graphics.Texture3DData]
  var rWrap: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
  def this(data: com.badlogic.gdx.graphics.Texture3DData) = {
    this()
    if (com.badlogic.gdx.Gdx.gl30 == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Texture3D requires a device running with GLES 3.0 compatibilty")
    } else ()
    this.load(data)
    if (data.isManaged()) {
      Texture3D.addManagedTexture(com.badlogic.gdx.Gdx.app, this)
    } else ()
  }
  def this(width: scala.Int, height: scala.Int, depth: scala.Int, glFormat: scala.Int, glInternalFormat: scala.Int, glType: scala.Int) = {
    this(new com.badlogic.gdx.graphics.glutils.CustomTexture3DData(width, height, depth, 0, glFormat, glInternalFormat, glType))
  }
  private def load(data: com.badlogic.gdx.graphics.Texture3DData): scala.Unit = {
    if ((this.data != null) && (data.isManaged() != this.data.isManaged())) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("New data must have the same managed status as the old data")
    } else ()
    this.data = data
    this.bind()
    if (!data.isPrepared()) {
      data.prepare()
    } else ()
    data.consume3DData()
    this.setFilter(minFilter, magFilter)
    this.setWrap(uWrap, vWrap, this.rWrap)
    com.badlogic.gdx.Gdx.gl.glBindTexture(glTarget, 0)
  }
  def getData(): com.badlogic.gdx.graphics.Texture3DData = {
    return this.data
  }
  def upload(): scala.Unit = {
    this.bind()
    this.data.consume3DData()
  }
  def getWidth(): scala.Int = {
    return this.data.getWidth()
  }
  def getHeight(): scala.Int = {
    return this.data.getHeight()
  }
  def getDepth(): scala.Int = {
    return this.data.getDepth()
  }
  def isManaged(): scala.Boolean = {
    return this.data.isManaged()
  }
  def reload(): scala.Unit = {
    if (!this.isManaged()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Tried to reload an unmanaged TextureArray")
    } else ()
    glHandle = com.badlogic.gdx.Gdx.gl.glGenTexture()
    this.load(this.data)
  }
  def setWrap(u: com.badlogic.gdx.graphics.Texture.TextureWrap, v: com.badlogic.gdx.graphics.Texture.TextureWrap, r: com.badlogic.gdx.graphics.Texture.TextureWrap): scala.Unit = {
    this.rWrap = r
    super.setWrap(u, v)
    com.badlogic.gdx.Gdx.gl.glTexParameteri(glTarget, com.badlogic.gdx.graphics.GL30.GL_TEXTURE_WRAP_R, r.getGLEnum())
  }
  def unsafeSetWrap(u: com.badlogic.gdx.graphics.Texture.TextureWrap, v: com.badlogic.gdx.graphics.Texture.TextureWrap, r: com.badlogic.gdx.graphics.Texture.TextureWrap, force: scala.Boolean): scala.Unit = {
    this.unsafeSetWrap(u, v, force)
    if ((r != null) && (force || (this.rWrap != r))) {
      com.badlogic.gdx.Gdx.gl.glTexParameteri(glTarget, com.badlogic.gdx.graphics.GL30.GL_TEXTURE_WRAP_R, u.getGLEnum())
      this.rWrap = r
    } else ()
  }
  def unsafeSetWrap(u: com.badlogic.gdx.graphics.Texture.TextureWrap, v: com.badlogic.gdx.graphics.Texture.TextureWrap, r: com.badlogic.gdx.graphics.Texture.TextureWrap): scala.Unit = {
    this.unsafeSetWrap(u, v, r, false)
  }
}
object Texture3D {
  final val managedTexture3Ds: scala.collection.mutable.Map[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Texture3D]] = new scala.collection.mutable.HashMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[Texture3D]]()
  private def addManagedTexture(app: com.badlogic.gdx.Application, texture: Texture3D): scala.Unit = {
    var managedTextureArray: com.badlogic.gdx.utils.Array[Texture3D] = Texture3D.managedTexture3Ds.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture3D]])
    if (managedTextureArray == null) {
      managedTextureArray = new com.badlogic.gdx.utils.Array[Texture3D]()
    } else ()
    managedTextureArray.add(texture)
    Texture3D.managedTexture3Ds.update(app, managedTextureArray)
  }
  def clearAllTextureArrays(app: com.badlogic.gdx.Application): scala.Unit = {
    Texture3D.managedTexture3Ds -= app
  }
  def invalidateAllTextureArrays(app: com.badlogic.gdx.Application): scala.Unit = {
    val managedTextureArray: com.badlogic.gdx.utils.Array[Texture3D] = Texture3D.managedTexture3Ds.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture3D]])
    if (managedTextureArray == null) {
      return
    } else ();
    { var i: scala.Int = 0; while (i < managedTextureArray.size) { {
      val textureArray: Texture3D = managedTextureArray.get(i)
      textureArray.reload()
    }; i = i + 1 } }
  }
  def getManagedStatus(): java.lang.String = {
    val builder: java.lang.StringBuilder = new java.lang.StringBuilder()
    builder.append("Managed TextureArrays/app: { ")
    for (app <- Texture3D.managedTexture3Ds.keySet) {
      builder.append(Texture3D.managedTexture3Ds.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture3D]]).size)
      builder.append(" ")
    }
    builder.append("}")
    return builder.toString()
  }
  def getNumManagedTextures3D(): scala.Int = {
    return Texture3D.managedTexture3Ds.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[Texture3D]]).size
  }
}