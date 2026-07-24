package com.badlogic.gdx.graphics

class TextureArray extends com.badlogic.gdx.graphics.GLTexture {
  private var data: com.badlogic.gdx.graphics.TextureArrayData = null.asInstanceOf[com.badlogic.gdx.graphics.TextureArrayData]
  def this(data: com.badlogic.gdx.graphics.TextureArrayData) = {
    this()
    if (com.badlogic.gdx.Gdx.gl30 == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("TextureArray requires a device running with GLES 3.0 compatibilty")
    } else ()
    this.load(data)
    if (data.isManaged()) {
      TextureArray.addManagedTexture(com.badlogic.gdx.Gdx.app, this)
    } else ()
  }
  def this(useMipMaps: scala.Boolean, format: com.badlogic.gdx.graphics.Pixmap.Format, files: scala.Array[com.badlogic.gdx.files.FileHandle]) = {
    this(com.badlogic.gdx.graphics.TextureArrayData.Factory.loadFromFiles(format, useMipMaps, files))
  }
  def this(useMipMaps: scala.Boolean, files: scala.Array[com.badlogic.gdx.files.FileHandle]) = {
    this(useMipMaps, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, files)
  }
  def this(internalPaths: scala.Array[java.lang.String]) = {
    this(TextureArray.getInternalHandles(internalPaths))
  }
  def this(files: scala.Array[com.badlogic.gdx.files.FileHandle]) = {
    this(false, files)
  }
  private def load(data: com.badlogic.gdx.graphics.TextureArrayData): scala.Unit = {
    if ((this.data != null) && (data.isManaged() != this.data.isManaged())) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("New data must have the same managed status as the old data")
    } else ()
    this.data = data
    this.bind()
    com.badlogic.gdx.Gdx.gl30.glTexImage3D(com.badlogic.gdx.graphics.GL30.GL_TEXTURE_2D_ARRAY, 0, data.getInternalFormat(), data.getWidth(), data.getHeight(), data.getDepth(), 0, data.getInternalFormat(), data.getGLType(), null)
    if (!data.isPrepared()) {
      data.prepare()
    } else ()
    data.consumeTextureArrayData()
    this.setFilter(minFilter, magFilter)
    this.setWrap(uWrap, vWrap)
    com.badlogic.gdx.Gdx.gl.glBindTexture(glTarget, 0)
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
}
object TextureArray {
  export com.badlogic.gdx.graphics.GLTexture.{managedTextureArrays => _, getInternalHandles => _, addManagedTexture => _, clearAllTextureArrays => _, invalidateAllTextureArrays => _, getManagedStatus => _, getNumManagedTextureArrays => _, *}
  final val managedTextureArrays: scala.collection.mutable.Map[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[TextureArray]] = new scala.collection.mutable.HashMap[com.badlogic.gdx.Application, com.badlogic.gdx.utils.Array[TextureArray]]()
  private def getInternalHandles(internalPaths: scala.Array[java.lang.String]): scala.Array[com.badlogic.gdx.files.FileHandle] = {
    val handles: scala.Array[com.badlogic.gdx.files.FileHandle] = new scala.Array[com.badlogic.gdx.files.FileHandle](internalPaths.length);
    { var i: scala.Int = 0; while (i < internalPaths.length) { {
      handles(i) = com.badlogic.gdx.Gdx.files.internal(internalPaths(i))
    }; i = i + 1 } }
    return handles
  }
  private def addManagedTexture(app: com.badlogic.gdx.Application, texture: TextureArray): scala.Unit = {
    var managedTextureArray: com.badlogic.gdx.utils.Array[TextureArray] = TextureArray.managedTextureArrays.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[TextureArray]])
    if (managedTextureArray == null) {
      managedTextureArray = new com.badlogic.gdx.utils.Array[TextureArray]()
    } else ()
    managedTextureArray.add(texture)
    TextureArray.managedTextureArrays.update(app, managedTextureArray)
  }
  def clearAllTextureArrays(app: com.badlogic.gdx.Application): scala.Unit = {
    TextureArray.managedTextureArrays -= app
  }
  def invalidateAllTextureArrays(app: com.badlogic.gdx.Application): scala.Unit = {
    val managedTextureArray: com.badlogic.gdx.utils.Array[TextureArray] = TextureArray.managedTextureArrays.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[TextureArray]])
    if (managedTextureArray == null) {
      return
    } else ();
    { var i: scala.Int = 0; while (i < managedTextureArray.size) { {
      val textureArray: TextureArray = managedTextureArray.get(i)
      textureArray.reload()
    }; i = i + 1 } }
  }
  def getManagedStatus(): java.lang.String = {
    val builder: java.lang.StringBuilder = new java.lang.StringBuilder()
    builder.append("Managed TextureArrays/app: { ")
    for (app <- TextureArray.managedTextureArrays.keySet) {
      builder.append(TextureArray.managedTextureArrays.getOrElse(app, null.asInstanceOf[com.badlogic.gdx.utils.Array[TextureArray]]).size)
      builder.append(" ")
    }
    builder.append("}")
    return builder.toString()
  }
  def getNumManagedTextureArrays(): scala.Int = {
    return TextureArray.managedTextureArrays.getOrElse(com.badlogic.gdx.Gdx.app, null.asInstanceOf[com.badlogic.gdx.utils.Array[TextureArray]]).size
  }
}