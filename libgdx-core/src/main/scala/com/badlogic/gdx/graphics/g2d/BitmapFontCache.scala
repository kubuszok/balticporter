package com.badlogic.gdx.graphics.g2d

class BitmapFontCache {
  var font: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
  var integer: scala.Boolean = false
  private final val layouts: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.GlyphLayout] = new com.badlogic.gdx.utils.Array(1).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.GlyphLayout]]
  private final val pooledLayouts: com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g2d.GlyphLayout] = new com.badlogic.gdx.utils.FlushablePool[com.badlogic.gdx.graphics.g2d.GlyphLayout]() {
    @java.lang.Override
    override def newObject(): ?T = {
      return new com.badlogic.gdx.graphics.g2d.GlyphLayout()
    }
  }
  private var glyphCount: scala.Int = 0
  private var x: scala.Float = 0.0f
  private var y: scala.Float = 0.0f
  private final val color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
  private var currentTint: scala.Float = 0.0f
  var pageVertices: scala.Array[scala.Array[scala.Float]] = null.asInstanceOf[scala.Array[scala.Array[scala.Float]]]
  var idx: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  var pageGlyphIndices: scala.Array[com.badlogic.gdx.utils.IntArray] = null.asInstanceOf[scala.Array[com.badlogic.gdx.utils.IntArray]]
  var tempGlyphCount: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, integer: scala.Boolean) = {
    this()
    this.font = font
    this.integer = integer
    val pageCount: scala.Int = font.regions.size
    if (pageCount == 0) {
      throw new java.lang.IllegalArgumentException("The specified font must contain at least one texture page.")
    } else ()
    this.pageVertices = new scala.Array[scala.Array[scala.Float]](pageCount)
    this.idx = new scala.Array[scala.Int](pageCount)
    if (pageCount > 1) {
      this.pageGlyphIndices = new scala.Array[com.badlogic.gdx.utils.IntArray](pageCount);
      { var i: scala.Int = 0; val n: scala.Int = this.pageGlyphIndices.length; while (i < n) { {
        this.pageGlyphIndices(i) = new com.badlogic.gdx.utils.IntArray()
      }; i = i + 1 } }
    } else ()
    this.tempGlyphCount = new scala.Array[scala.Int](pageCount)
  }
  def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont) = {
    this(font, font.usesIntegerPositions())
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    this.translate(x - this.x, y - this.y)
  }
  def translate(xAmount$arg: scala.Float, yAmount$arg: scala.Float): scala.Unit = {
    var xAmount: scala.Float = xAmount$arg
    var yAmount: scala.Float = yAmount$arg
    if ((xAmount == 0) && (yAmount == 0)) {
      return
    } else ()
    if (this.integer) {
      xAmount = java.lang.Math.round(xAmount)
      yAmount = java.lang.Math.round(yAmount)
    } else ()
    this.x = this.x + xAmount
    this.y = this.y + yAmount
    val pageVertices: scala.Array[scala.Array[scala.Float]] = this.pageVertices;
    { var i: scala.Int = 0; val n: scala.Int = pageVertices.length; while (i < n) { {
      val vertices: scala.Array[scala.Float] = pageVertices(i);
      { var ii: scala.Int = 0; val nn: scala.Int = this.idx(i); while (ii < nn) { {
        vertices(ii) = vertices(ii) + xAmount
        vertices(ii + 1) = vertices(ii + 1) + yAmount
      }; ii = ii + 5 } }
    }; i = i + 1 } }
  }
  def tint(tint: com.badlogic.gdx.graphics.Color): scala.Unit = {
    val newTint: scala.Float = tint.toFloatBits()
    if (this.currentTint == newTint) {
      return
    } else ()
    this.currentTint = newTint
    val pageVertices: scala.Array[scala.Array[scala.Float]] = this.pageVertices
    val tempColor: com.badlogic.gdx.graphics.Color = BitmapFontCache.tempColor
    val tempGlyphCount: scala.Array[scala.Int] = this.tempGlyphCount
    java.util.Arrays.fill(tempGlyphCount, 0);
    { var i: scala.Int = 0; val n: scala.Int = this.layouts.size; while (i < n) { {
      val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = this.layouts.get(i)
      val colors: com.badlogic.gdx.utils.IntArray = layout.colors
      var colorsIndex: scala.Int = 0
      var nextColorGlyphIndex: scala.Int = 0
      var glyphIndex: scala.Int = 0
      var lastColorFloatBits: scala.Float = 0;
      { var ii: scala.Int = 0; val nn: scala.Int = layout.runs.size; while (ii < nn) { {
        val run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = layout.runs.get(ii)
        val glyphs: scala.Array[java.lang.Object] = run.glyphs.items.asInstanceOf[scala.Array[java.lang.Object]];
        { var iii: scala.Int = 0; val nnn: scala.Int = run.glyphs.size; while (iii < nnn) { {
          if ({ glyphIndex += 1; glyphIndex } == nextColorGlyphIndex) {
            com.badlogic.gdx.graphics.Color.abgr8888ToColor(tempColor, colors.get({ colorsIndex += 1; colorsIndex }))
            lastColorFloatBits = tempColor.mul(tint).toFloatBits()
            nextColorGlyphIndex = if ({ colorsIndex += 1; colorsIndex } < colors.size) colors.get(colorsIndex) else -1
          } else ()
          val page: scala.Int = glyphs(iii).asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph].page
          val offset: scala.Int = (tempGlyphCount(page) * 20) + 2
          tempGlyphCount(page) = tempGlyphCount(page) + 1
          val vertices: scala.Array[scala.Float] = pageVertices(page)
          vertices(offset) = lastColorFloatBits
          vertices(offset + 5) = lastColorFloatBits
          vertices(offset + 10) = lastColorFloatBits
          vertices(offset + 15) = lastColorFloatBits
        }; iii = iii + 1 } }
      }; ii = ii + 1 } }
    }; i = i + 1 } }
  }
  def setAlphas(alpha: scala.Float): scala.Unit = {
    val alphaBits: scala.Int = (254 * alpha).asInstanceOf[scala.Int] << 24
    var prev: scala.Float = 0
    var newColor: scala.Float = 0;
    { var j: scala.Int = 0; val length: scala.Int = this.pageVertices.length; while (j < length) { {
      val vertices: scala.Array[scala.Float] = this.pageVertices(j);
      { var i: scala.Int = 2; val n: scala.Int = this.idx(j); while (i < n) { {
        val c: scala.Float = vertices(i)
        if ((c == prev) && (i != 2)) {
          vertices(i) = newColor
        } else {
          prev = c
          var rgba: scala.Int = com.badlogic.gdx.utils.NumberUtils.floatToIntColor(c)
          rgba = (rgba & 16777215) | alphaBits
          newColor = com.badlogic.gdx.utils.NumberUtils.intToFloatColor(rgba)
          vertices(i) = newColor
        }
      }; i = i + 5 } }
    }; j = j + 1 } }
  }
  def setColors(color: scala.Float): scala.Unit = {
    { var j: scala.Int = 0; val length: scala.Int = this.pageVertices.length; while (j < length) { {
      val vertices: scala.Array[scala.Float] = this.pageVertices(j);
      { var i: scala.Int = 2; val n: scala.Int = this.idx(j); while (i < n) { {
        vertices(i) = color
      }; i = i + 5 } }
    }; j = j + 1 } }
  }
  def setColors(tint: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.setColors(tint.toFloatBits())
  }
  def setColors(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    val intBits: scala.Int = ((((255 * a).asInstanceOf[scala.Int] << 24) | ((255 * b).asInstanceOf[scala.Int] << 16)) | ((255 * g).asInstanceOf[scala.Int] << 8)) | (255 * r).asInstanceOf[scala.Int]
    this.setColors(com.badlogic.gdx.utils.NumberUtils.intToFloatColor(intBits))
  }
  def setColors(tint: com.badlogic.gdx.graphics.Color, start: scala.Int, `end`: scala.Int): scala.Unit = {
    this.setColors(tint.toFloatBits(), start, `end`)
  }
  def setColors(color: scala.Float, start: scala.Int, `end`: scala.Int): scala.Unit = {
    if (this.pageVertices.length == 1) {
      val vertices: scala.Array[scala.Float] = this.pageVertices(0);
      { var i: scala.Int = (start * 20) + 2; val n: scala.Int = java.lang.Math.min(`end` * 20, this.idx(0)); while (i < n) { {
        vertices(i) = color
      }; i = i + 5 } }
      return
    } else ()
    val pageCount: scala.Int = this.pageVertices.length;
    { var i: scala.Int = 0; while (i < pageCount) { {
      val vertices: scala.Array[scala.Float] = this.pageVertices(i)
      val glyphIndices: com.badlogic.gdx.utils.IntArray = this.pageGlyphIndices(i);
      { var j: scala.Int = 0; val n: scala.Int = glyphIndices.size; while (j < n) { {
        val glyphIndex: scala.Int = glyphIndices.items(j)
        if (glyphIndex >= `end`) {
          /* break */ ()
        } else ()
        if (glyphIndex >= start) {
          val offset: scala.Int = (j * 20) + 2
          vertices(offset) = color
          vertices(offset + 5) = color
          vertices(offset + 10) = color
          vertices(offset + 15) = color
        } else ()
      }; j = j + 1 } }
    }; i = i + 1 } }
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(color)
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
  }
  def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch): scala.Unit = {
    val regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = this.font.getRegions();
    { var j: scala.Int = 0; val n: scala.Int = this.pageVertices.length; while (j < n) { {
      if (this.idx(j) > 0) {
        val vertices: scala.Array[scala.Float] = this.pageVertices(j)
        spriteBatch.draw(regions.get(j).getTexture(), vertices, 0, this.idx(j))
      } else ()
    }; j = j + 1 } }
  }
  def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch, start: scala.Int, `end`: scala.Int): scala.Unit = {
    if (this.pageVertices.length == 1) {
      spriteBatch.draw(this.font.getRegion().getTexture(), this.pageVertices(0), start * 20, (`end` - start) * 20)
      return
    } else ()
    val regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = this.font.getRegions();
    { var i: scala.Int = 0; val pageCount: scala.Int = this.pageVertices.length; while (i < pageCount) { {
      var offset: scala.Int = -1
      var count: scala.Int = 0
      val glyphIndices: com.badlogic.gdx.utils.IntArray = this.pageGlyphIndices(i);
      { var ii: scala.Int = 0; val n: scala.Int = glyphIndices.size; while (ii < n) { {
        val glyphIndex: scala.Int = glyphIndices.get(ii)
        if (glyphIndex >= `end`) {
          /* break */ ()
        } else ()
        if ((offset == (-1)) && (glyphIndex >= start)) {
          offset = ii
        } else ()
        if (glyphIndex >= start) {
          count = count + 1
        } else ()
      }; ii = ii + 1 } }
      if ((offset == (-1)) || (count == 0)) {
        /* continue */ ()
      } else ()
      spriteBatch.draw(regions.get(i).getTexture(), this.pageVertices(i), offset * 20, count * 20)
    }; i = i + 1 } }
  }
  def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch, alphaModulation: scala.Float): scala.Unit = {
    if (alphaModulation == 1) {
      this.draw(spriteBatch)
      return
    } else ()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    val oldAlpha: scala.Float = color.a
    color.a = color.a * alphaModulation
    this.setColors(color)
    this.draw(spriteBatch)
    color.a = oldAlpha
    this.setColors(color)
  }
  def clear(): scala.Unit = {
    this.x = 0
    this.y = 0
    this.pooledLayouts.flush()
    this.layouts.clear();
    { var i: scala.Int = 0; val n: scala.Int = this.idx.length; while (i < n) { {
      if (this.pageGlyphIndices != null) {
        this.pageGlyphIndices(i).clear()
      } else ()
      this.idx(i) = 0
    }; i = i + 1 } }
  }
  private def requireGlyphs(layout: com.badlogic.gdx.graphics.g2d.GlyphLayout): scala.Unit = {
    if (this.pageVertices.length == 1) {
      this.requirePageGlyphs(0, layout.glyphCount)
    } else {
      val tempGlyphCount: scala.Array[scala.Int] = this.tempGlyphCount
      java.util.Arrays.fill(tempGlyphCount, 0);
      { var i: scala.Int = 0; val n: scala.Int = layout.runs.size; while (i < n) { {
        val glyphs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = layout.runs.get(i).glyphs
        val glyphItems: scala.Array[java.lang.Object] = glyphs.items.asInstanceOf[scala.Array[java.lang.Object]];
        { var ii: scala.Int = 0; val nn: scala.Int = glyphs.size; while (ii < nn) { {
          tempGlyphCount(glyphItems(ii).asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph].page) = tempGlyphCount(glyphItems(ii).asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph].page) + 1
        }; ii = ii + 1 } }
      }; i = i + 1 } };
      { var i: scala.Int = 0; val n: scala.Int = tempGlyphCount.length; while (i < n) { {
        this.requirePageGlyphs(i, tempGlyphCount(i))
      }; i = i + 1 } }
    }
  }
  private def requirePageGlyphs(page: scala.Int, glyphCount: scala.Int): scala.Unit = {
    if (this.pageGlyphIndices != null) {
      if (glyphCount > this.pageGlyphIndices(page).items.length) {
        this.pageGlyphIndices(page).ensureCapacity(glyphCount - this.pageGlyphIndices(page).size)
      } else ()
    } else ()
    val vertexCount: scala.Int = this.idx(page) + (glyphCount * 20)
    val vertices: scala.Array[scala.Float] = this.pageVertices(page)
    if (vertices == null) {
      this.pageVertices(page) = new scala.Array[scala.Float](vertexCount)
    } else {
      if (vertices.length < vertexCount) {
        val newVertices: scala.Array[scala.Float] = new scala.Array[scala.Float](vertexCount)
        java.lang.System.arraycopy(vertices, 0, newVertices, 0, this.idx(page))
        this.pageVertices(page) = newVertices
      } else ()
    }
  }
  private def setPageCount(pageCount: scala.Int): scala.Unit = {
    val newPageVertices: scala.Array[scala.Array[scala.Float]] = new scala.Array[scala.Array[scala.Float]](pageCount)
    java.lang.System.arraycopy(this.pageVertices, 0, newPageVertices, 0, this.pageVertices.length)
    this.pageVertices = newPageVertices
    val newIdx: scala.Array[scala.Int] = new scala.Array[scala.Int](pageCount)
    java.lang.System.arraycopy(this.idx, 0, newIdx, 0, this.idx.length)
    this.idx = newIdx
    val newPageGlyphIndices: scala.Array[com.badlogic.gdx.utils.IntArray] = new scala.Array[com.badlogic.gdx.utils.IntArray](pageCount)
    var pageGlyphIndicesLength: scala.Int = 0
    if (this.pageGlyphIndices != null) {
      pageGlyphIndicesLength = this.pageGlyphIndices.length
      java.lang.System.arraycopy(this.pageGlyphIndices, 0, newPageGlyphIndices, 0, this.pageGlyphIndices.length)
    } else ();
    { var i: scala.Int = pageGlyphIndicesLength; while (i < pageCount) { {
      newPageGlyphIndices(i) = new com.badlogic.gdx.utils.IntArray()
    }; i = i + 1 } }
    this.pageGlyphIndices = newPageGlyphIndices
    this.tempGlyphCount = new scala.Array[scala.Int](pageCount)
  }
  private def addToCache(layout: com.badlogic.gdx.graphics.g2d.GlyphLayout, x: scala.Float, y: scala.Float): scala.Unit = {
    val runCount: scala.Int = layout.runs.size
    if (runCount == 0) {
      return
    } else ()
    if (this.pageVertices.length < this.font.regions.size) {
      this.setPageCount(this.font.regions.size)
    } else ()
    this.layouts.add(layout)
    this.requireGlyphs(layout)
    val colors: com.badlogic.gdx.utils.IntArray = layout.colors
    var colorsIndex: scala.Int = 0
    var nextColorGlyphIndex: scala.Int = 0
    var glyphIndex: scala.Int = 0
    var lastColorFloatBits: scala.Float = 0;
    { var i: scala.Int = 0; while (i < runCount) { {
      val run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = layout.runs.get(i)
      val glyphs: scala.Array[java.lang.Object] = run.glyphs.items.asInstanceOf[scala.Array[java.lang.Object]]
      val xAdvances: scala.Array[scala.Float] = run.xAdvances.items
      var gx: scala.Float = x + run.x
      val gy: scala.Float = y + run.y;
      { var ii: scala.Int = 0; val nn: scala.Int = run.glyphs.size; while (ii < nn) { {
        if ({ glyphIndex += 1; glyphIndex } == nextColorGlyphIndex) {
          lastColorFloatBits = com.badlogic.gdx.utils.NumberUtils.intToFloatColor(colors.get({ colorsIndex += 1; colorsIndex }))
          nextColorGlyphIndex = if ({ colorsIndex += 1; colorsIndex } < colors.size) colors.get(colorsIndex) else -1
        } else ()
        gx = gx + xAdvances(ii)
        this.addGlyph(glyphs(ii).asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph].asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph], gx, gy, lastColorFloatBits)
      }; ii = ii + 1 } }
    }; i = i + 1 } }
    this.currentTint = com.badlogic.gdx.graphics.Color.WHITE_FLOAT_BITS
  }
  private def addGlyph(glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph, x$arg: scala.Float, y$arg: scala.Float, color: scala.Float): scala.Unit = {
    var x: scala.Float = x$arg
    var y: scala.Float = y$arg
    val scaleX: scala.Float = this.font.data.scaleX
    val scaleY: scala.Float = this.font.data.scaleY
    x = x + (glyph.xoffset * scaleX)
    y = y + (glyph.yoffset * scaleY)
    var width: scala.Float = glyph.width * scaleX
    var height: scala.Float = glyph.height * scaleY
    val u: scala.Float = glyph.u
    val u2: scala.Float = glyph.u2
    val v: scala.Float = glyph.v
    val v2: scala.Float = glyph.v2
    if (this.integer) {
      x = java.lang.Math.round(x)
      y = java.lang.Math.round(y)
      width = java.lang.Math.round(width)
      height = java.lang.Math.round(height)
    } else ()
    val x2: scala.Float = x + width
    val y2: scala.Float = y + height
    val page: scala.Int = glyph.page
    var idx: scala.Int = this.idx(page)
    this.idx(page) = this.idx(page) + 20
    if (this.pageGlyphIndices != null) {
      this.pageGlyphIndices(page).add({ this.glyphCount += 1; this.glyphCount })
    } else ()
    val vertices: scala.Array[scala.Float] = this.pageVertices(page)
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v
    vertices({ idx += 1; idx }) = x
    vertices({ idx += 1; idx }) = y2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x2
    vertices({ idx += 1; idx }) = y2
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices({ idx += 1; idx }) = v2
    vertices({ idx += 1; idx }) = x2
    vertices({ idx += 1; idx }) = y
    vertices({ idx += 1; idx }) = color
    vertices({ idx += 1; idx }) = u2
    vertices(idx) = v
  }
  def setText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.clear()
    return this.addText(str, x, y, 0, str.length(), 0, com.badlogic.gdx.utils.Align.left, false)
  }
  def setText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.clear()
    return this.addText(str, x, y, 0, str.length(), targetWidth, halign, wrap)
  }
  def setText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float, start: scala.Int, `end`: scala.Int, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.clear()
    return this.addText(str, x, y, start, `end`, targetWidth, halign, wrap)
  }
  def setText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float, start: scala.Int, `end`: scala.Int, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean, truncate: java.lang.String): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.clear()
    return this.addText(str, x, y, start, `end`, targetWidth, halign, wrap, truncate)
  }
  def setText(layout: com.badlogic.gdx.graphics.g2d.GlyphLayout, x: scala.Float, y: scala.Float): scala.Unit = {
    this.clear()
    this.addText(layout, x, y)
  }
  def addText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    return this.addText(str, x, y, 0, str.length(), 0, com.badlogic.gdx.utils.Align.left, false, null)
  }
  def addText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    return this.addText(str, x, y, 0, str.length(), targetWidth, halign, wrap, null)
  }
  def addText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float, start: scala.Int, `end`: scala.Int, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    return this.addText(str, x, y, start, `end`, targetWidth, halign, wrap, null)
  }
  def addText(str: java.lang.CharSequence, x: scala.Float, y: scala.Float, start: scala.Int, `end`: scala.Int, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean, truncate: java.lang.String): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = this.pooledLayouts.obtain()
    layout.setText(this.font, str, start, `end`, this.color, targetWidth, halign, wrap, truncate)
    this.addText(layout, x, y)
    return layout
  }
  def addText(layout: com.badlogic.gdx.graphics.g2d.GlyphLayout, x: scala.Float, y: scala.Float): scala.Unit = {
    this.addToCache(layout, x, y + this.font.data.ascent)
  }
  def getX(): scala.Float = {
    return this.x
  }
  def getY(): scala.Float = {
    return this.y
  }
  def getFont(): com.badlogic.gdx.graphics.g2d.BitmapFont = {
    return this.font
  }
  def setUseIntegerPositions(use: scala.Boolean): scala.Unit = {
    this.integer = use
  }
  def usesIntegerPositions(): scala.Boolean = {
    return this.integer
  }
  def getPageCount(): scala.Int = {
    return this.pageVertices.length
  }
  def getVertices(): scala.Array[scala.Float] = {
    return this.getVertices(0)
  }
  def getVertices(page: scala.Int): scala.Array[scala.Float] = {
    return this.pageVertices(page)
  }
  def getVertexCount(page: scala.Int): scala.Int = {
    return this.idx(page)
  }
  def getLayouts(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.GlyphLayout] = {
    return this.layouts
  }
}
object BitmapFontCache {
  private final val tempColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 1, 1, 1)
}