package com.badlogic.gdx.graphics.g2d

class DistanceFieldFont extends com.badlogic.gdx.graphics.g2d.BitmapFont {
  private var distanceFieldSmoothing: scala.Float = 0.0f
  def this(fontFile: com.badlogic.gdx.files.FileHandle, imageFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean, integer: scala.Boolean) = {
    this()
  }
  def this(data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData, pageRegions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion], integer: scala.Boolean) = {
    this()
  }
  def this(data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData, region: com.badlogic.gdx.graphics.g2d.TextureRegion, integer: scala.Boolean) = {
    this()
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, imageFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
    this()
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, region: com.badlogic.gdx.graphics.g2d.TextureRegion, flip: scala.Boolean) = {
    this()
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
    this()
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this()
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle) = {
    this()
  }
  def load(data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData): scala.Unit = {
    super.load(data)
    val regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = this.getRegions()
    for (region <- regions) {
      region.getTexture().setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.Linear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear)
    }
  }
  def newFontCache(): com.badlogic.gdx.graphics.g2d.BitmapFontCache = {
    return new com.badlogic.gdx.graphics.g2d.DistanceFieldFont.DistanceFieldFontCache(this, integer)
  }
  def getDistanceFieldSmoothing(): scala.Float = {
    return this.distanceFieldSmoothing
  }
  def setDistanceFieldSmoothing(distanceFieldSmoothing: scala.Float): scala.Unit = {
    this.distanceFieldSmoothing = distanceFieldSmoothing
  }
}
object DistanceFieldFont {
  export com.badlogic.gdx.graphics.g2d.BitmapFont.{createDistanceFieldShader => _, DistanceFieldFontCache => _, *}
  def createDistanceFieldShader(): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    val vertexShader: java.lang.String = ((((((((((((((((((((((("attribute vec4 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "attribute vec4 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "attribute vec2 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "uniform mat4 u_projTrans;\n") + "varying vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "\n") + "void main() {\n") + "\tv_color = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "\tv_color.a = v_color.a * (255.0/254.0);\n") + "\tv_texCoords = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "\tgl_Position =  u_projTrans * ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "}\n"
    val fragmentShader: java.lang.String = (((((((((((((((((("#ifdef GL_ES\n" + "\tprecision mediump float;\n") + "\tprecision mediump int;\n") + "#endif\n") + "\n") + "uniform sampler2D u_texture;\n") + "uniform float u_smoothing;\n") + "varying vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "\n") + "void main() {\n") + "\tif (u_smoothing > 0.0) {\n") + "\t\tfloat smoothing = 0.25 / u_smoothing;\n") + "\t\tfloat distance = texture2D(u_texture, v_texCoords).a;\n") + "\t\tfloat alpha = smoothstep(0.5 - smoothing, 0.5 + smoothing, distance);\n") + "\t\tgl_FragColor = vec4(v_color.rgb, alpha * v_color.a);\n") + "\t} else {\n") + "\t\tgl_FragColor = v_color * texture2D(u_texture, v_texCoords);\n") + "\t}\n") + "}\n"
    val shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = new com.badlogic.gdx.graphics.glutils.ShaderProgram(vertexShader, fragmentShader)
    if (!shader.isCompiled()) {
      throw new java.lang.IllegalArgumentException("Error compiling distance field shader: " + shader.getLog())
    } else ()
    return shader
  }
  private class DistanceFieldFontCache extends com.badlogic.gdx.graphics.g2d.BitmapFontCache {
    def this(font: DistanceFieldFont, integer: scala.Boolean) = {
      this()
    }
    def this(font: DistanceFieldFont) = {
      this()
    }
    private def getSmoothingFactor(): scala.Float = {
      val font: DistanceFieldFont = super.getFont().asInstanceOf[DistanceFieldFont]
      return font.getDistanceFieldSmoothing() * font.getScaleX()
    }
    private def setSmoothingUniform(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch, smoothing: scala.Float): scala.Unit = {
      spriteBatch.flush()
      spriteBatch.getShader().setUniformf("u_smoothing", smoothing)
    }
    def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch): scala.Unit = {
      this.setSmoothingUniform(spriteBatch, this.getSmoothingFactor())
      super.draw(spriteBatch)
      this.setSmoothingUniform(spriteBatch, 0)
    }
    def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch, start: scala.Int, `end`: scala.Int): scala.Unit = {
      this.setSmoothingUniform(spriteBatch, this.getSmoothingFactor())
      super.draw(spriteBatch, start, `end`)
      this.setSmoothingUniform(spriteBatch, 0)
    }
  }
  object DistanceFieldFontCache {
    export com.badlogic.gdx.graphics.g2d.BitmapFontCache.*
  }
}