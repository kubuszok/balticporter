package com.badlogic.gdx.graphics.g2d

class PixmapPacker(pageWidth$p: scala.Int, pageHeight$p: scala.Int, pageFormat$p: com.badlogic.gdx.graphics.Pixmap.Format, padding$p: scala.Int, duplicateBorder$p: scala.Boolean, stripWhitespaceX$p: scala.Boolean, stripWhitespaceY$p: scala.Boolean, packStrategy$p: com.badlogic.gdx.graphics.g2d.PixmapPacker.PackStrategy) extends com.badlogic.gdx.utils.Disposable {
  var packToTexture: scala.Boolean = false
  var disposed: scala.Boolean = false
  var pageWidth: scala.Int = 0
  var pageHeight: scala.Int = 0
  var pageFormat: com.badlogic.gdx.graphics.Pixmap.Format = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap.Format]
  var padding: scala.Int = 0
  var duplicateBorder: scala.Boolean = false
  var stripWhitespaceX: scala.Boolean = false
  var stripWhitespaceY: scala.Boolean = false
  var alphaThreshold: scala.Int = 0
  var transparentColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(0.0f, 0.0f, 0.0f, 0.0f)
  final val pages: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.PixmapPacker.Page] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.PixmapPacker.Page]]
  var packStrategy: com.badlogic.gdx.graphics.g2d.PixmapPacker.PackStrategy = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.PackStrategy]
  private var c: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  def this(pageWidth: scala.Int, pageHeight: scala.Int, pageFormat: com.badlogic.gdx.graphics.Pixmap.Format, padding: scala.Int, duplicateBorder: scala.Boolean) = {
    this(pageWidth, pageHeight, pageFormat, padding, duplicateBorder, false, false, new com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy())
  }
  def this(pageWidth: scala.Int, pageHeight: scala.Int, pageFormat: com.badlogic.gdx.graphics.Pixmap.Format, padding: scala.Int, duplicateBorder: scala.Boolean, packStrategy: com.badlogic.gdx.graphics.g2d.PixmapPacker.PackStrategy) = {
    this(pageWidth, pageHeight, pageFormat, padding, duplicateBorder, false, false, packStrategy)
  }
  this.pageWidth = pageWidth$p
  this.pageHeight = pageHeight$p
  this.pageFormat = pageFormat$p
  this.padding = padding$p
  this.duplicateBorder = duplicateBorder$p
  this.stripWhitespaceX = stripWhitespaceX$p
  this.stripWhitespaceY = stripWhitespaceY$p
  this.packStrategy = packStrategy$p
  def sort(images: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Pixmap]): scala.Unit = {
    this.packStrategy.sort(images)
  }
  def pack(image: com.badlogic.gdx.graphics.Pixmap): com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle = {
    return this.pack(null, image)
  }
  def pack(name$arg: java.lang.String, image$arg: com.badlogic.gdx.graphics.Pixmap): com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle = {
    var name: java.lang.String = name$arg
    var image: com.badlogic.gdx.graphics.Pixmap = image$arg
    if (this.disposed) {
      return null
    } else ()
    if ((name != null) && (this.getRect(name) != null)) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Pixmap has already been packed with name: " + name)
    } else ()
    var rect: com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle]
    var pixmapToDispose: com.badlogic.gdx.graphics.Pixmap = null
    if ((name != null) && name.endsWith(".9")) {
      rect = new com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle(0, 0, image.getWidth() - 2, image.getHeight() - 2)
      pixmapToDispose = new com.badlogic.gdx.graphics.Pixmap(image.getWidth() - 2, image.getHeight() - 2, image.getFormat())
      pixmapToDispose.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
      rect.splits = this.getSplits(image)
      rect.pads = this.getPads(image, rect.splits)
      pixmapToDispose.drawPixmap(image, 0, 0, 1, 1, image.getWidth() - 1, image.getHeight() - 1)
      image = pixmapToDispose
      name = name.split("\\.")(0)
    } else {
      if (this.stripWhitespaceX || this.stripWhitespaceY) {
        val originalWidth: scala.Int = image.getWidth()
        val originalHeight: scala.Int = image.getHeight()
        var top: scala.Int = 0
        var bottom: scala.Int = image.getHeight()
        if (this.stripWhitespaceY) {
          { var y: scala.Int = 0; while (y < image.getHeight()) { {
            { var x: scala.Int = 0; while (x < image.getWidth()) { {
              val pixel: scala.Int = image.getPixel(x, y)
              val alpha: scala.Int = pixel & 255
              if (alpha > this.alphaThreshold) {
                /* break */ ()
              } else ()
            }; x = x + 1 } }
            top = top + 1
          }; y = y + 1 } };
          { var y: scala.Int = image.getHeight(); while ({ y -= 1; y } >= top) { {
            { var x: scala.Int = 0; while (x < image.getWidth()) { {
              val pixel: scala.Int = image.getPixel(x, y)
              val alpha: scala.Int = pixel & 255
              if (alpha > this.alphaThreshold) {
                /* break */ ()
              } else ()
            }; x = x + 1 } }
            bottom = bottom - 1
          };  } }
        } else ()
        var left: scala.Int = 0
        var right: scala.Int = image.getWidth()
        if (this.stripWhitespaceX) {
          { var x: scala.Int = 0; while (x < image.getWidth()) { {
            { var y: scala.Int = top; while (y < bottom) { {
              val pixel: scala.Int = image.getPixel(x, y)
              val alpha: scala.Int = pixel & 255
              if (alpha > this.alphaThreshold) {
                /* break */ ()
              } else ()
            }; y = y + 1 } }
            left = left + 1
          }; x = x + 1 } };
          { var x: scala.Int = image.getWidth(); while ({ x -= 1; x } >= left) { {
            { var y: scala.Int = top; while (y < bottom) { {
              val pixel: scala.Int = image.getPixel(x, y)
              val alpha: scala.Int = pixel & 255
              if (alpha > this.alphaThreshold) {
                /* break */ ()
              } else ()
            }; y = y + 1 } }
            right = right - 1
          };  } }
        } else ()
        val newWidth: scala.Int = right - left
        val newHeight: scala.Int = bottom - top
        pixmapToDispose = new com.badlogic.gdx.graphics.Pixmap(newWidth, newHeight, image.getFormat())
        pixmapToDispose.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
        pixmapToDispose.drawPixmap(image, 0, 0, left, top, newWidth, newHeight)
        image = pixmapToDispose
        rect = new com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle(0, 0, newWidth, newHeight, left, top, originalWidth, originalHeight)
      } else {
        rect = new com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle(0, 0, image.getWidth(), image.getHeight())
      }
    }
    if ((rect.getWidth() > this.pageWidth) || (rect.getHeight() > this.pageHeight)) {
      if (name == null) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Page size too small for pixmap.")
      } else ()
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Page size too small for pixmap: " + name)
    } else ()
    var page: com.badlogic.gdx.graphics.g2d.PixmapPacker.Page = this.packStrategy.pack(this, name, rect.bounds)
    if (name != null) {
      page.rects.put(name, rect)
      page.addedRects.add(name)
    } else ()
    val rectX: scala.Int = rect.getX()
    val rectY: scala.Int = rect.getY()
    val rectWidth: scala.Int = rect.getWidth()
    val rectHeight: scala.Int = rect.getHeight()
    if (((this.packToTexture && (!this.duplicateBorder)) && (page.texture != null)) && (!page.dirty)) {
      page.texture.bind()
      com.badlogic.gdx.Gdx.gl.glTexSubImage2D(page.texture.glTarget, 0, rectX, rectY, rectWidth, rectHeight, image.getGLFormat(), image.getGLType(), image.getPixels())
    } else {
      page.dirty = true
    }
    page.image.drawPixmap(image, rectX, rectY)
    if (this.duplicateBorder) {
      val imageWidth: scala.Int = image.getWidth()
      val imageHeight: scala.Int = image.getHeight()
      page.image.drawPixmap(image, 0, 0, 1, 1, rectX - 1, rectY - 1, 1, 1)
      page.image.drawPixmap(image, imageWidth - 1, 0, 1, 1, rectX + rectWidth, rectY - 1, 1, 1)
      page.image.drawPixmap(image, 0, imageHeight - 1, 1, 1, rectX - 1, rectY + rectHeight, 1, 1)
      page.image.drawPixmap(image, imageWidth - 1, imageHeight - 1, 1, 1, rectX + rectWidth, rectY + rectHeight, 1, 1)
      page.image.drawPixmap(image, 0, 0, imageWidth, 1, rectX, rectY - 1, rectWidth, 1)
      page.image.drawPixmap(image, 0, imageHeight - 1, imageWidth, 1, rectX, rectY + rectHeight, rectWidth, 1)
      page.image.drawPixmap(image, 0, 0, 1, imageHeight, rectX - 1, rectY, 1, rectHeight)
      page.image.drawPixmap(image, imageWidth - 1, 0, 1, imageHeight, rectX + rectWidth, rectY, 1, rectHeight)
    } else ()
    if (pixmapToDispose != null) {
      pixmapToDispose.dispose()
    } else ()
    rect.page = page
    return rect
  }
  def getPages(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.PixmapPacker.Page] = {
    return this.pages
  }
  def getRect(name: java.lang.String): com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle = {
    for (page <- this.pages) {
      val rect: com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle = page.rects.get(name)
      if (rect != null) {
        return rect
      } else ()
    }
    return null
  }
  def getPage(name: java.lang.String): com.badlogic.gdx.graphics.g2d.PixmapPacker.Page = {
    for (page <- this.pages) {
      if (page.rects.get(name) != null) {
        return page
      } else ()
    }
    return null
  }
  def getPageIndex(name: java.lang.String): scala.Int = {
    { var i: scala.Int = 0; while (i < this.pages.size) { {
      if (this.pages.get(i).rects.get(name) != null) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def dispose(): scala.Unit = {
    for (page <- this.pages) {
      if (page.texture == null) {
        page.image.dispose()
      } else ()
    }
    this.disposed = true
  }
  def generateTextureAtlas(minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, useMipMaps: scala.Boolean): com.badlogic.gdx.graphics.g2d.TextureAtlas = {
    val atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas()
    this.updateTextureAtlas(atlas, minFilter, magFilter, useMipMaps)
    return atlas
  }
  def updateTextureAtlas(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas, minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, useMipMaps: scala.Boolean): scala.Unit = {
    this.updateTextureAtlas(atlas, minFilter, magFilter, useMipMaps, true)
  }
  def updateTextureAtlas(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas, minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, useMipMaps: scala.Boolean, useIndexes: scala.Boolean): scala.Unit = {
    this.updatePageTextures(minFilter, magFilter, useMipMaps)
    for (page <- this.pages) {
      if (page.addedRects.size > 0) {
        for (name <- page.addedRects) {
          val rect: com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle = page.rects.get(name)
          val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(page.texture, rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight())
          if (rect.splits != null) {
            region.names = scala.Array[java.lang.String]("split", "pad")
            region.values = scala.Array[scala.Array[scala.Int]](rect.splits, rect.pads)
          } else ()
          var imageIndex: scala.Int = -1
          var imageName: java.lang.String = name
          if (useIndexes) {
            val matcher: java.util.regex.Matcher = PixmapPacker.indexPattern.matcher(imageName)
            if (matcher.matches()) {
              imageName = matcher.group(1)
              imageIndex = java.lang.Integer.parseInt(matcher.group(2))
            } else ()
          } else ()
          region.name = imageName
          region.index = imageIndex
          region.offsetX = rect.offsetX
          region.offsetY = (rect.originalHeight - rect.getHeight()) - rect.offsetY
          region.originalWidth = rect.originalWidth
          region.originalHeight = rect.originalHeight
          atlas.getRegions().add(region)
        }
        page.addedRects.clear()
        atlas.getTextures().add(page.texture)
      } else ()
    }
  }
  def updateTextureRegions(regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion], minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, useMipMaps: scala.Boolean): scala.Unit = {
    this.updatePageTextures(minFilter, magFilter, useMipMaps)
    while (regions.size < this.pages.size) {
      regions.add(new com.badlogic.gdx.graphics.g2d.TextureRegion(this.pages.get(regions.size).texture))
    }
  }
  def updatePageTextures(minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, useMipMaps: scala.Boolean): scala.Unit = {
    for (page <- this.pages) {
      page.updateTexture(minFilter, magFilter, useMipMaps)
    }
  }
  def getPageWidth(): scala.Int = {
    return this.pageWidth
  }
  def setPageWidth(pageWidth: scala.Int): scala.Unit = {
    this.pageWidth = pageWidth
  }
  def getPageHeight(): scala.Int = {
    return this.pageHeight
  }
  def setPageHeight(pageHeight: scala.Int): scala.Unit = {
    this.pageHeight = pageHeight
  }
  def getPageFormat(): com.badlogic.gdx.graphics.Pixmap.Format = {
    return this.pageFormat
  }
  def setPageFormat(pageFormat: com.badlogic.gdx.graphics.Pixmap.Format): scala.Unit = {
    this.pageFormat = pageFormat
  }
  def getPadding(): scala.Int = {
    return this.padding
  }
  def setPadding(padding: scala.Int): scala.Unit = {
    this.padding = padding
  }
  def getDuplicateBorder(): scala.Boolean = {
    return this.duplicateBorder
  }
  def setDuplicateBorder(duplicateBorder: scala.Boolean): scala.Unit = {
    this.duplicateBorder = duplicateBorder
  }
  def getPackToTexture(): scala.Boolean = {
    return this.packToTexture
  }
  def setPackToTexture(packToTexture: scala.Boolean): scala.Unit = {
    this.packToTexture = packToTexture
  }
  def getTransparentColor(): com.badlogic.gdx.graphics.Color = {
    return this.transparentColor
  }
  def setTransparentColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.transparentColor.set(color)
  }
  private def getSplits(raster: com.badlogic.gdx.graphics.Pixmap): scala.Array[scala.Int] = {
    var startX: scala.Int = this.getSplitPoint(raster, 1, 0, true, true)
    var endX: scala.Int = this.getSplitPoint(raster, startX, 0, false, true)
    var startY: scala.Int = this.getSplitPoint(raster, 0, 1, true, false)
    var endY: scala.Int = this.getSplitPoint(raster, 0, startY, false, false)
    this.getSplitPoint(raster, endX + 1, 0, true, true)
    this.getSplitPoint(raster, 0, endY + 1, true, false)
    if ((((startX == 0) && (endX == 0)) && (startY == 0)) && (endY == 0)) {
      return null
    } else ()
    if (startX != 0) {
      startX = startX - 1
      endX = (raster.getWidth() - 2) - (endX - 1)
    } else {
      endX = raster.getWidth() - 2
    }
    if (startY != 0) {
      startY = startY - 1
      endY = (raster.getHeight() - 2) - (endY - 1)
    } else {
      endY = raster.getHeight() - 2
    }
    return scala.Array[scala.Int](startX, endX, startY, endY)
  }
  private def getPads(raster: com.badlogic.gdx.graphics.Pixmap, splits: scala.Array[scala.Int]): scala.Array[scala.Int] = {
    val bottom: scala.Int = raster.getHeight() - 1
    val right: scala.Int = raster.getWidth() - 1
    var startX: scala.Int = this.getSplitPoint(raster, 1, bottom, true, true)
    var startY: scala.Int = this.getSplitPoint(raster, right, 1, true, false)
    var endX: scala.Int = 0
    var endY: scala.Int = 0
    if (startX != 0) {
      endX = this.getSplitPoint(raster, startX + 1, bottom, false, true)
    } else ()
    if (startY != 0) {
      endY = this.getSplitPoint(raster, right, startY + 1, false, false)
    } else ()
    this.getSplitPoint(raster, endX + 1, bottom, true, true)
    this.getSplitPoint(raster, right, endY + 1, true, false)
    if ((((startX == 0) && (endX == 0)) && (startY == 0)) && (endY == 0)) {
      return null
    } else ()
    if ((startX == 0) && (endX == 0)) {
      startX = -1
      endX = -1
    } else {
      if (startX > 0) {
        startX = startX - 1
        endX = (raster.getWidth() - 2) - (endX - 1)
      } else {
        endX = raster.getWidth() - 2
      }
    }
    if ((startY == 0) && (endY == 0)) {
      startY = -1
      endY = -1
    } else {
      if (startY > 0) {
        startY = startY - 1
        endY = (raster.getHeight() - 2) - (endY - 1)
      } else {
        endY = raster.getHeight() - 2
      }
    }
    val pads: scala.Array[scala.Int] = scala.Array[scala.Int](startX, endX, startY, endY)
    if ((splits != null) && java.util.Arrays.equals(pads, splits)) {
      return null
    } else ()
    return pads
  }
  private def getSplitPoint(raster: com.badlogic.gdx.graphics.Pixmap, startX: scala.Int, startY: scala.Int, startPoint: scala.Boolean, xAxis: scala.Boolean): scala.Int = {
    val rgba: scala.Array[scala.Int] = new scala.Array[scala.Int](4)
    var next: scala.Int = if (xAxis) startX else startY
    val `end`: scala.Int = if (xAxis) raster.getWidth() else raster.getHeight()
    val breakA: scala.Int = if (startPoint) 255 else 0
    var x: scala.Int = startX
    var y: scala.Int = startY
    while (next != `end`) {
      if (xAxis) {
        x = next
      } else {
        y = next
      }
      val colint: scala.Int = raster.getPixel(x, y)
      this.c.set(colint)
      rgba(0) = (this.c.r * 255).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      rgba(1) = (this.c.g * 255).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      rgba(2) = (this.c.b * 255).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      rgba(3) = (this.c.a * 255).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      if (rgba(3) == breakA) {
        return next
      } else ()
      if ((!startPoint) && ((((rgba(0) != 0) || (rgba(1) != 0)) || (rgba(2) != 0)) || (rgba(3) != 255))) {
        java.lang.System.out.println(((((java.lang.String.valueOf(x) + "  ") + y) + " ") + rgba) + " ")
      } else ()
      next = next + 1
    }
    return 0
  }
}
object PixmapPacker {
  var indexPattern: java.util.regex.Pattern = java.util.regex.Pattern.compile("(.+)_(\\d+)$")
  class Page(packer: PixmapPacker) {
    var rects: com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle] = new com.badlogic.gdx.utils.OrderedMap().asInstanceOf[com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle]]
    var image: com.badlogic.gdx.graphics.Pixmap = null.asInstanceOf[com.badlogic.gdx.graphics.Pixmap]
    var texture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
    final val addedRects: com.badlogic.gdx.utils.Array[java.lang.String] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.String]]
    var dirty: scala.Boolean = false
    this.image = new com.badlogic.gdx.graphics.Pixmap(packer.pageWidth, packer.pageHeight, packer.pageFormat)
    this.image.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None)
    this.image.setColor(packer.getTransparentColor())
    this.image.fill()
    def getPixmap(): com.badlogic.gdx.graphics.Pixmap = {
      return this.image
    }
    def getRects(): com.badlogic.gdx.utils.OrderedMap[java.lang.String, com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle] = {
      return this.rects
    }
    def getTexture(): com.badlogic.gdx.graphics.Texture = {
      return this.texture
    }
    def updateTexture(minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter, useMipMaps: scala.Boolean): scala.Boolean = {
      if (this.texture != null) {
        if (!this.dirty) {
          return false
        } else ()
        this.texture.load(this.texture.getTextureData())
      } else {
        this.texture = new com.badlogic.gdx.graphics.Texture(new com.badlogic.gdx.graphics.glutils.PixmapTextureData(this.image, this.image.getFormat(), useMipMaps, false, true))
        this.texture.setFilter(minFilter, magFilter)
      }
      this.dirty = false
      return true
    }
  }
  trait PackStrategy {
    def sort(images: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Pixmap]): scala.Unit
    def pack(packer: PixmapPacker, name: java.lang.String, bounds: com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds): com.badlogic.gdx.graphics.g2d.PixmapPacker.Page
  }
  class GuillotineStrategy extends com.badlogic.gdx.graphics.g2d.PixmapPacker.PackStrategy {
    var comparator: java.util.Comparator[com.badlogic.gdx.graphics.Pixmap] = null.asInstanceOf[java.util.Comparator[com.badlogic.gdx.graphics.Pixmap]]
    def sort(pixmaps: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Pixmap]): scala.Unit = {
      if (this.comparator == null) {
        this.comparator = new java.util.Comparator[com.badlogic.gdx.graphics.Pixmap]()
      } else ()
      pixmaps.sort(this.comparator)
    }
    def pack(packer: PixmapPacker, name: java.lang.String, bounds: com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds): com.badlogic.gdx.graphics.g2d.PixmapPacker.Page = {
      var page: com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.GuillotinePage = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.GuillotinePage]
      if (packer.pages.size == 0) {
        page = new com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.GuillotinePage(packer)
        packer.pages.add(page)
      } else {
        page = packer.pages.peek().asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.GuillotinePage]
      }
      val padding: scala.Int = packer.padding
      bounds.width = bounds.width + padding
      bounds.height = bounds.height + padding
      var node: com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node = this.insert(page.root, bounds)
      if (node == null) {
        page = new com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.GuillotinePage(packer)
        packer.pages.add(page)
        node = this.insert(page.root, bounds)
      } else ()
      node.full = true
      bounds.set(node.rect.x, node.rect.y, node.rect.width - padding, node.rect.height - padding)
      return page
    }
    private def insert(node: com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node, rect: com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds): com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node = {
      if (((!node.full) && (node.leftChild != null)) && (node.rightChild != null)) {
        var newNode: com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node = this.insert(node.leftChild, rect)
        if (newNode == null) {
          newNode = this.insert(node.rightChild, rect)
        } else ()
        return newNode
      } else {
        if (node.full) {
          return null
        } else ()
        if ((node.rect.width == rect.width) && (node.rect.height == rect.height)) {
          return node
        } else ()
        if ((node.rect.width < rect.width) || (node.rect.height < rect.height)) {
          return null
        } else ()
        node.leftChild = new com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node()
        node.rightChild = new com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node()
        val deltaWidth: scala.Int = node.rect.width - rect.width
        val deltaHeight: scala.Int = node.rect.height - rect.height
        if (deltaWidth > deltaHeight) {
          node.leftChild.rect.x = node.rect.x
          node.leftChild.rect.y = node.rect.y
          node.leftChild.rect.width = rect.width
          node.leftChild.rect.height = node.rect.height
          node.rightChild.rect.x = node.rect.x + rect.width
          node.rightChild.rect.y = node.rect.y
          node.rightChild.rect.width = node.rect.width - rect.width
          node.rightChild.rect.height = node.rect.height
        } else {
          node.leftChild.rect.x = node.rect.x
          node.leftChild.rect.y = node.rect.y
          node.leftChild.rect.width = node.rect.width
          node.leftChild.rect.height = rect.height
          node.rightChild.rect.x = node.rect.x
          node.rightChild.rect.y = node.rect.y + rect.height
          node.rightChild.rect.width = node.rect.width
          node.rightChild.rect.height = node.rect.height - rect.height
        }
        return this.insert(node.leftChild, rect)
      }
    }
  }
  object GuillotineStrategy {
    final class Node {
      var leftChild: com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node]
      var rightChild: com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node]
      final val rect: com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds = new com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds()
      var full: scala.Boolean = false
    }
    class GuillotinePage(packer: PixmapPacker) extends com.badlogic.gdx.graphics.g2d.PixmapPacker.Page(packer) {
      var root: com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node]
      this.root = new com.badlogic.gdx.graphics.g2d.PixmapPacker.GuillotineStrategy.Node()
      this.root.rect.x = packer.padding
      this.root.rect.y = packer.padding
      this.root.rect.width = packer.pageWidth - (packer.padding * 2)
      this.root.rect.height = packer.pageHeight - (packer.padding * 2)
    }
  }
  class SkylineStrategy extends com.badlogic.gdx.graphics.g2d.PixmapPacker.PackStrategy {
    var comparator: java.util.Comparator[com.badlogic.gdx.graphics.Pixmap] = null.asInstanceOf[java.util.Comparator[com.badlogic.gdx.graphics.Pixmap]]
    def sort(images: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.Pixmap]): scala.Unit = {
      if (this.comparator == null) {
        this.comparator = new java.util.Comparator[com.badlogic.gdx.graphics.Pixmap]()
      } else ()
      images.sort(this.comparator)
    }
    def pack(packer: PixmapPacker, name: java.lang.String, rect: com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds): com.badlogic.gdx.graphics.g2d.PixmapPacker.Page = {
      val padding: scala.Int = packer.padding
      val pageWidth: scala.Int = packer.pageWidth - (padding * 2)
      val pageHeight: scala.Int = packer.pageHeight - (padding * 2)
      val rectWidth: scala.Int = rect.width + padding
      val rectHeight: scala.Int = rect.height + padding;
      { var i: scala.Int = 0; val n: scala.Int = packer.pages.size; while (i < n) { {
        val page: com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage = packer.pages.get(i).asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage]
        var bestRow: com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row = null;
        { var ii: scala.Int = 0; val nn: scala.Int = page.rows.size - 1; while (ii < nn) { {
          val row: com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row = page.rows.get(ii)
          if ((row.x + rectWidth) >= pageWidth) {
            /* continue */ ()
          } else ()
          if ((row.y + rectHeight) >= pageHeight) {
            /* continue */ ()
          } else ()
          if (rectHeight > row.height) {
            /* continue */ ()
          } else ()
          if ((bestRow == null) || (row.height < bestRow.height)) {
            bestRow = row
          } else ()
        }; ii = ii + 1 } }
        if (bestRow == null) {
          val row: com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row = page.rows.peek()
          if ((row.y + rectHeight) >= pageHeight) {
            /* continue */ ()
          } else ()
          if ((row.x + rectWidth) < pageWidth) {
            row.height = java.lang.Math.max(row.height, rectHeight)
            bestRow = row
          } else {
            if (((row.y + row.height) + rectHeight) < pageHeight) {
              bestRow = new com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row()
              bestRow.y = row.y + row.height
              bestRow.height = rectHeight
              page.rows.add(bestRow)
            } else ()
          }
        } else ()
        if (bestRow != null) {
          rect.x = bestRow.x
          rect.y = bestRow.y
          bestRow.x = bestRow.x + rectWidth
          return page
        } else ()
      }; i = i + 1 } }
      val page: com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage = new com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage(packer)
      packer.pages.add(page)
      val row: com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row = new com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row()
      row.x = padding + rectWidth
      row.y = padding
      row.height = rectHeight
      page.rows.add(row)
      rect.x = padding
      rect.y = padding
      return page
    }
  }
  object SkylineStrategy {
    class SkylinePage(packer: PixmapPacker) extends com.badlogic.gdx.graphics.g2d.PixmapPacker.Page(packer) {
      var rows: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.PixmapPacker.SkylineStrategy.SkylinePage.Row]]
    }
    object SkylinePage {
      class Row {
        var x: scala.Int = 0
        var y: scala.Int = 0
        var height: scala.Int = 0
      }
    }
  }
  class Bounds {
    var x: scala.Int = 0
    var y: scala.Int = 0
    var width: scala.Int = 0
    var height: scala.Int = 0
    def this(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int) = {
      this()
      this.x = x
      this.y = y
      this.width = width
      this.height = height
    }
    def set(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): scala.Unit = {
      this.x = x
      this.y = y
      this.width = width
      this.height = height
    }
  }
  class PixmapPackerRectangle {
    var page: com.badlogic.gdx.graphics.g2d.PixmapPacker.Page = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.Page]
    var splits: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    var pads: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    var offsetX: scala.Int = 0
    var offsetY: scala.Int = 0
    var originalWidth: scala.Int = 0
    var originalHeight: scala.Int = 0
    var bounds: com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds]
    def this(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int) = {
      this()
      this.bounds = new com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds(x, y, width, height)
      this.offsetX = 0
      this.offsetY = 0
      this.originalWidth = width
      this.originalHeight = height
    }
    def this(x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int, left: scala.Int, top: scala.Int, originalWidth: scala.Int, originalHeight: scala.Int) = {
      this()
      this.bounds = new com.badlogic.gdx.graphics.g2d.PixmapPacker.Bounds(x, y, width, height)
      this.offsetX = left
      this.offsetY = top
      this.originalWidth = originalWidth
      this.originalHeight = originalHeight
    }
    def getX(): scala.Int = {
      return this.bounds.x
    }
    def getY(): scala.Int = {
      return this.bounds.y
    }
    def getWidth(): scala.Int = {
      return this.bounds.width
    }
    def getHeight(): scala.Int = {
      return this.bounds.height
    }
  }
}