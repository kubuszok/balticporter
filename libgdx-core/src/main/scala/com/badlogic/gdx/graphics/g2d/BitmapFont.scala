package com.badlogic.gdx.graphics.g2d

class BitmapFont extends com.badlogic.gdx.utils.Disposable {
  var data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData]
  var regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion]]
  private var cache: com.badlogic.gdx.graphics.g2d.BitmapFontCache = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFontCache]
  private var flipped: scala.Boolean = false
  var integer: scala.Boolean = false
  var ownsTexture$field: scala.Boolean = false
  def this(data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData, pageRegions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion], integer: scala.Boolean) = {
    this()
    this.flipped = data.flipped
    this.data = data
    this.integer = integer
    if ((pageRegions == null) || (pageRegions.size == 0)) {
      if (data.imagePaths == null) {
        throw new java.lang.IllegalArgumentException("If no regions are specified, the font data must have an images path.")
      } else ()
      val n: scala.Int = data.imagePaths.length
      this.regions = new com.badlogic.gdx.utils.Array(n);
      { var i: scala.Int = 0; while (i < n) { {
        var file: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
        if (data.fontFile == null) {
          file = com.badlogic.gdx.Gdx.files.internal(data.imagePaths(i))
        } else {
          file = com.badlogic.gdx.Gdx.files.getFileHandle(data.imagePaths(i), data.fontFile.`type`())
        }
        this.regions.add(new com.badlogic.gdx.graphics.g2d.TextureRegion(new com.badlogic.gdx.graphics.Texture(file, false)))
      }; i = i + 1 } }
      this.ownsTexture$field = true
    } else {
      this.regions = pageRegions
      this.ownsTexture$field = false
    }
    this.cache = this.newFontCache()
    this.load(data)
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, imageFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean, integer: scala.Boolean) = {
    this(new com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData(fontFile, flip), new com.badlogic.gdx.graphics.g2d.TextureRegion(new com.badlogic.gdx.graphics.Texture(imageFile, false)), integer)
    this.ownsTexture$field = true
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, region: com.badlogic.gdx.graphics.g2d.TextureRegion, flip: scala.Boolean) = {
    this(new com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData(fontFile, flip), region, true)
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, imageFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
    this(fontFile, imageFile, flip, true)
  }
  def this(data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData, region: com.badlogic.gdx.graphics.g2d.TextureRegion, integer: scala.Boolean) = {
    this(data, if (region != null) com.badlogic.gdx.utils.Array.`with`(region) else null, integer)
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
    this(fontFile, region, false)
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
    this(new com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData(fontFile, flip), null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureRegion], true)
  }
  def this(flip: scala.Boolean) = {
    this(com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.fnt"), com.badlogic.gdx.Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.png"), flip, true)
  }
  def this(fontFile: com.badlogic.gdx.files.FileHandle) = {
    this(fontFile, false)
  }
  def load(data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData): scala.Unit = {
    for (page <- data.glyphs) {
      if (page == null) {
        /* continue */ ()
      } else ()
      for (glyph <- page) {
        if (glyph != null) {
          data.setGlyphRegion(glyph, this.regions.get(glyph.page))
        } else ()
      }
    }
    if (data.missingGlyph != null) {
      data.setGlyphRegion(data.missingGlyph, this.regions.get(data.missingGlyph.page))
    } else ()
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, str: java.lang.CharSequence, x: scala.Float, y: scala.Float): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.cache.clear()
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = this.cache.addText(str, x, y)
    this.cache.draw(batch)
    return layout
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, str: java.lang.CharSequence, x: scala.Float, y: scala.Float, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.cache.clear()
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = this.cache.addText(str, x, y, targetWidth, halign, wrap)
    this.cache.draw(batch)
    return layout
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, str: java.lang.CharSequence, x: scala.Float, y: scala.Float, start: scala.Int, `end`: scala.Int, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.cache.clear()
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = this.cache.addText(str, x, y, start, `end`, targetWidth, halign, wrap)
    this.cache.draw(batch)
    return layout
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, str: java.lang.CharSequence, x: scala.Float, y: scala.Float, start: scala.Int, `end`: scala.Int, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean, truncate: java.lang.String): com.badlogic.gdx.graphics.g2d.GlyphLayout = {
    this.cache.clear()
    val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = this.cache.addText(str, x, y, start, `end`, targetWidth, halign, wrap, truncate)
    this.cache.draw(batch)
    return layout
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, layout: com.badlogic.gdx.graphics.g2d.GlyphLayout, x: scala.Float, y: scala.Float): scala.Unit = {
    this.cache.clear()
    this.cache.addText(layout, x, y)
    this.cache.draw(batch)
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.cache.getColor()
  }
  def setColor(color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.cache.getColor().set(color)
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.cache.getColor().set(r, g, b, a)
  }
  def getScaleX(): scala.Float = {
    return this.data.scaleX
  }
  def getScaleY(): scala.Float = {
    return this.data.scaleY
  }
  def getRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.regions.first()
  }
  def getRegions(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = {
    return this.regions
  }
  def getRegion(index: scala.Int): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.regions.get(index)
  }
  def getLineHeight(): scala.Float = {
    return this.data.lineHeight
  }
  def getSpaceXadvance(): scala.Float = {
    return this.data.spaceXadvance
  }
  def getXHeight(): scala.Float = {
    return this.data.xHeight
  }
  def getCapHeight(): scala.Float = {
    return this.data.capHeight
  }
  def getAscent(): scala.Float = {
    return this.data.ascent
  }
  def getDescent(): scala.Float = {
    return this.data.descent
  }
  def isFlipped(): scala.Boolean = {
    return this.flipped
  }
  def dispose(): scala.Unit = {
    if (this.ownsTexture$field) {
      { var i: scala.Int = 0; while (i < this.regions.size) { {
        this.regions.get(i).getTexture().dispose()
      }; i = i + 1 } }
    } else ()
  }
  def setFixedWidthGlyphs(glyphs: java.lang.CharSequence): scala.Unit = {
    val data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = this.data
    var maxAdvance: scala.Int = 0;
    { var index: scala.Int = 0; val `end`: scala.Int = glyphs.length(); while (index < `end`) { {
      val g: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = data.getGlyph(glyphs.charAt(index))
      if ((g != null) && (g.xadvance > maxAdvance)) {
        maxAdvance = g.xadvance
      } else ()
    }; index = index + 1 } };
    { var index: scala.Int = 0; val `end`: scala.Int = glyphs.length(); while (index < `end`) { {
      val g: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = data.getGlyph(glyphs.charAt(index))
      if (g == null) {
        /* continue */ ()
      } else ()
      g.xoffset = g.xoffset + ((maxAdvance - g.xadvance) / 2)
      g.xadvance = maxAdvance
      g.kerning = null
      g.fixedWidth = true
    }; index = index + 1 } }
  }
  def setUseIntegerPositions(integer: scala.Boolean): scala.Unit = {
    this.integer = integer
    this.cache.setUseIntegerPositions(integer)
  }
  def usesIntegerPositions(): scala.Boolean = {
    return this.integer
  }
  def getCache(): com.badlogic.gdx.graphics.g2d.BitmapFontCache = {
    return this.cache
  }
  def getData(): com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = {
    return this.data
  }
  def ownsTexture(): scala.Boolean = {
    return this.ownsTexture$field
  }
  def setOwnsTexture(ownsTexture: scala.Boolean): scala.Unit = {
    this.ownsTexture$field = ownsTexture
  }
  def newFontCache(): com.badlogic.gdx.graphics.g2d.BitmapFontCache = {
    return new com.badlogic.gdx.graphics.g2d.BitmapFontCache(this, this.integer)
  }
  def toString(): java.lang.String = {
    return if (this.data.name != null) this.data.name else super.toString()
  }
}
object BitmapFont {
  private final val LOG2_PAGE_SIZE: scala.Int = 9
  private final val PAGE_SIZE: scala.Int = 1 << BitmapFont.LOG2_PAGE_SIZE
  private final val PAGES: scala.Int = 65536 / BitmapFont.PAGE_SIZE
  def indexOf(text: java.lang.CharSequence, ch: scala.Char, start$arg: scala.Int): scala.Int = {
    var start: scala.Int = start$arg
    val n: scala.Int = text.length();
    { ; while (start < n) { {
      if (text.charAt(start) == ch) {
        return start
      } else ()
    }; start = start + 1 } }
    return n
  }
  class Glyph {
    var id: scala.Int = 0
    var srcX: scala.Int = 0
    var srcY: scala.Int = 0
    var width: scala.Int = 0
    var height: scala.Int = 0
    var u: scala.Float = 0.0f
    var v: scala.Float = 0.0f
    var u2: scala.Float = 0.0f
    var v2: scala.Float = 0.0f
    var xoffset: scala.Int = 0
    var yoffset: scala.Int = 0
    var xadvance: scala.Int = 0
    var kerning: scala.Array[scala.Array[scala.Byte]] = null.asInstanceOf[scala.Array[scala.Array[scala.Byte]]]
    var fixedWidth: scala.Boolean = false
    var page: scala.Int = 0
    def getKerning(ch: scala.Char): scala.Int = {
      if (this.kerning != null) {
        val page: scala.Array[scala.Byte] = this.kerning(ch >>> BitmapFont.LOG2_PAGE_SIZE)
        if (page != null) {
          return page(ch & (BitmapFont.PAGE_SIZE - 1))
        } else ()
      } else ()
      return 0
    }
    def setKerning(ch: scala.Int, value: scala.Int): scala.Unit = {
      if (this.kerning == null) {
        this.kerning = new scala.Array[scala.Array[scala.Byte]](BitmapFont.PAGES)
      } else ()
      var page: scala.Array[scala.Byte] = this.kerning(ch >>> BitmapFont.LOG2_PAGE_SIZE)
      if (page == null) {
        this.kerning(ch >>> BitmapFont.LOG2_PAGE_SIZE) = {
          page = new scala.Array[scala.Byte](BitmapFont.PAGE_SIZE)
          page
        }
      } else ()
      page(ch & (BitmapFont.PAGE_SIZE - 1)) = value.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
    }
    def toString(): java.lang.String = {
      return java.lang.Character.toString(this.id.asInstanceOf[scala.Char])
    }
  }
  class BitmapFontData {
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    var imagePaths: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
    var fontFile: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
    var flipped: scala.Boolean = false
    var padTop: scala.Float = 0.0f
    var padRight: scala.Float = 0.0f
    var padBottom: scala.Float = 0.0f
    var padLeft: scala.Float = 0.0f
    var lineHeight: scala.Float = 0.0f
    var capHeight: scala.Float = 1
    var ascent: scala.Float = 0.0f
    var descent: scala.Float = 0.0f
    var down: scala.Float = 0.0f
    var blankLineScale: scala.Float = 1
    var scaleX: scala.Float = 1
    var scaleY: scala.Float = 1
    var markupEnabled: scala.Boolean = false
    var cursorX: scala.Float = 0.0f
    final val glyphs: scala.Array[scala.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph]] = new scala.Array[scala.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph]](BitmapFont.PAGES)
    var missingGlyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph]
    var spaceXadvance: scala.Float = 0.0f
    var xHeight: scala.Float = 1
    var breakChars: scala.Array[scala.Char] = null.asInstanceOf[scala.Array[scala.Char]]
    var xChars: scala.Array[scala.Char] = scala.Array[scala.Char]('x', 'e', 'a', 'o', 'n', 's', 'r', 'c', 'u', 'm', 'v', 'w', 'z')
    var capChars: scala.Array[scala.Char] = scala.Array[scala.Char]('M', 'N', 'B', 'D', 'C', 'E', 'F', 'K', 'A', 'G', 'H', 'I', 'J', 'L', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z')
    def this(fontFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
      this()
      this.fontFile = fontFile
      this.flipped = flip
      this.load(fontFile, flip)
    }
    def load(fontFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean): scala.Unit = {
      if (this.imagePaths != null) {
        throw new java.lang.IllegalStateException("Already loaded.")
      } else ()
      this.name = fontFile.nameWithoutExtension()
      val reader: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(fontFile.read()), 512)
      try {
        var line: java.lang.String = reader.readLine()
        if (line == null) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("File is empty.")
        } else ()
        line = line.substring(line.indexOf("padding=") + 8)
        val padding: scala.Array[java.lang.String] = line.substring(0, line.indexOf(' ')).split(",", 4)
        if (padding.length != 4) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid padding.")
        } else ()
        this.padTop = java.lang.Integer.parseInt(padding(0))
        this.padRight = java.lang.Integer.parseInt(padding(1))
        this.padBottom = java.lang.Integer.parseInt(padding(2))
        this.padLeft = java.lang.Integer.parseInt(padding(3))
        val padY: scala.Float = this.padTop + this.padBottom
        line = reader.readLine()
        if (line == null) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Missing common header.")
        } else ()
        val common: scala.Array[java.lang.String] = line.split(" ", 9)
        if (common.length < 3) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid common header.")
        } else ()
        if (!common(1).startsWith("lineHeight=")) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Missing: lineHeight")
        } else ()
        this.lineHeight = java.lang.Integer.parseInt(common(1).substring(11))
        if (!common(2).startsWith("base=")) {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Missing: base")
        } else ()
        val baseLine: scala.Float = java.lang.Integer.parseInt(common(2).substring(5))
        var pageCount: scala.Int = 1
        if (((common.length >= 6) && (common(5) != null)) && common(5).startsWith("pages=")) {
          try {
            pageCount = java.lang.Math.max(1, java.lang.Integer.parseInt(common(5).substring(6)))
          } catch {
            case ignored: java.lang.NumberFormatException => {
              ()
            }
          }
        } else ()
        this.imagePaths = new scala.Array[java.lang.String](pageCount);
        { var p: scala.Int = 0; while (p < pageCount) { {
          line = reader.readLine()
          if (line == null) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Missing additional page definitions.")
          } else ()
          var matcher: java.util.regex.Matcher = java.util.regex.Pattern.compile(".*id=(\\d+)").matcher(line)
          if (matcher.find()) {
            var id: java.lang.String = matcher.group(1)
            try {
              val pageID: scala.Int = java.lang.Integer.parseInt(id)
              if (pageID != p) {
                throw new com.badlogic.gdx.utils.GdxRuntimeException("Page IDs must be indices starting at 0: " + id)
              } else ()
            } catch {
              case ex: java.lang.NumberFormatException => {
                throw new com.badlogic.gdx.utils.GdxRuntimeException("Invalid page id: " + id, ex)
              }
            }
          } else ()
          matcher = java.util.regex.Pattern.compile(".*file=\"?([^\"]+)\"?").matcher(line)
          if (!matcher.find()) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Missing: file")
          } else ()
          val fileName: java.lang.String = matcher.group(1)
          this.imagePaths(p) = fontFile.parent().child(fileName).path().replaceAll("\\\\", "/")
        }; p = p + 1 } }
        this.descent = 0
        while (true) {
          line = reader.readLine()
          if (line == null) {
            /* break */ ()
          } else ()
          if (line.startsWith("kernings ")) {
            /* break */ ()
          } else ()
          if (line.startsWith("metrics ")) {
            /* break */ ()
          } else ()
          if (!line.startsWith("char ")) {
            /* continue */ ()
          } else ()
          val glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = new com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph()
          val tokens: java.util.StringTokenizer = new java.util.StringTokenizer(line, " =")
          tokens.nextToken()
          tokens.nextToken()
          val ch: scala.Int = java.lang.Integer.parseInt(tokens.nextToken())
          if (ch <= 0) {
            this.missingGlyph = glyph
          } else {
            if (ch <= java.lang.Character.MAX_VALUE) {
              this.setGlyph(ch, glyph)
            } else {
              /* continue */ ()
            }
          }
          glyph.id = ch
          tokens.nextToken()
          glyph.srcX = java.lang.Integer.parseInt(tokens.nextToken())
          tokens.nextToken()
          glyph.srcY = java.lang.Integer.parseInt(tokens.nextToken())
          tokens.nextToken()
          glyph.width = java.lang.Integer.parseInt(tokens.nextToken())
          tokens.nextToken()
          glyph.height = java.lang.Integer.parseInt(tokens.nextToken())
          tokens.nextToken()
          glyph.xoffset = java.lang.Integer.parseInt(tokens.nextToken())
          tokens.nextToken()
          if (flip) {
            glyph.yoffset = java.lang.Integer.parseInt(tokens.nextToken())
          } else {
            glyph.yoffset = -(glyph.height + java.lang.Integer.parseInt(tokens.nextToken()))
          }
          tokens.nextToken()
          glyph.xadvance = java.lang.Integer.parseInt(tokens.nextToken())
          if (tokens.hasMoreTokens()) {
            tokens.nextToken()
          } else ()
          if (tokens.hasMoreTokens()) {
            try {
              glyph.page = java.lang.Integer.parseInt(tokens.nextToken())
            } catch {
              case ignored: java.lang.NumberFormatException => {
                ()
              }
            }
          } else ()
          if ((glyph.width > 0) && (glyph.height > 0)) {
            this.descent = java.lang.Math.min(baseLine + glyph.yoffset, this.descent)
          } else ()
        }
        this.descent = this.descent + this.padBottom
        while (true) {
          line = reader.readLine()
          if (line == null) {
            /* break */ ()
          } else ()
          if (!line.startsWith("kerning ")) {
            /* break */ ()
          } else ()
          val tokens: java.util.StringTokenizer = new java.util.StringTokenizer(line, " =")
          tokens.nextToken()
          tokens.nextToken()
          val first: scala.Int = java.lang.Integer.parseInt(tokens.nextToken())
          tokens.nextToken()
          val second: scala.Int = java.lang.Integer.parseInt(tokens.nextToken())
          if ((((first < 0) || (first > java.lang.Character.MAX_VALUE)) || (second < 0)) || (second > java.lang.Character.MAX_VALUE)) {
            /* continue */ ()
          } else ()
          val glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = this.getGlyph(first.asInstanceOf[scala.Char])
          tokens.nextToken()
          val amount: scala.Int = java.lang.Integer.parseInt(tokens.nextToken())
          if (glyph != null) {
            glyph.setKerning(second, amount)
          } else ()
        }
        var hasMetricsOverride: scala.Boolean = false
        var overrideAscent: scala.Float = 0
        var overrideDescent: scala.Float = 0
        var overrideDown: scala.Float = 0
        var overrideCapHeight: scala.Float = 0
        var overrideLineHeight: scala.Float = 0
        var overrideSpaceXAdvance: scala.Float = 0
        var overrideXHeight: scala.Float = 0
        if ((line != null) && line.startsWith("metrics ")) {
          hasMetricsOverride = true
          val tokens: java.util.StringTokenizer = new java.util.StringTokenizer(line, " =")
          tokens.nextToken()
          tokens.nextToken()
          overrideAscent = java.lang.Float.parseFloat(tokens.nextToken())
          tokens.nextToken()
          overrideDescent = java.lang.Float.parseFloat(tokens.nextToken())
          tokens.nextToken()
          overrideDown = java.lang.Float.parseFloat(tokens.nextToken())
          tokens.nextToken()
          overrideCapHeight = java.lang.Float.parseFloat(tokens.nextToken())
          tokens.nextToken()
          overrideLineHeight = java.lang.Float.parseFloat(tokens.nextToken())
          tokens.nextToken()
          overrideSpaceXAdvance = java.lang.Float.parseFloat(tokens.nextToken())
          tokens.nextToken()
          overrideXHeight = java.lang.Float.parseFloat(tokens.nextToken())
        } else ()
        var spaceGlyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = this.getGlyph(' ')
        if (spaceGlyph == null) {
          spaceGlyph = new com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph()
          spaceGlyph.id = ' '
          var xadvanceGlyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = this.getGlyph('l')
          if (xadvanceGlyph == null) {
            xadvanceGlyph = this.getFirstGlyph()
          } else ()
          spaceGlyph.xadvance = xadvanceGlyph.xadvance
          this.setGlyph(' ', spaceGlyph)
        } else ()
        if (spaceGlyph.width == 0) {
          spaceGlyph.width = ((this.padLeft + spaceGlyph.xadvance) + this.padRight).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
          spaceGlyph.xoffset = (-this.padLeft).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
        } else ()
        this.spaceXadvance = spaceGlyph.xadvance
        var xGlyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = null
        for (xChar <- this.xChars) {
          xGlyph = this.getGlyph(xChar)
          if (xGlyph != null) {
            /* break */ ()
          } else ()
        }
        if (xGlyph == null) {
          xGlyph = this.getFirstGlyph()
        } else ()
        this.xHeight = xGlyph.height - padY
        var capGlyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = null
        for (capChar <- this.capChars) {
          capGlyph = this.getGlyph(capChar)
          if (capGlyph != null) {
            /* break */ ()
          } else ()
        }
        if (capGlyph == null) {
          for (page <- this.glyphs) {
            if (page == null) {
              /* continue */ ()
            } else ()
            for (glyph <- page) {
              if (((glyph == null) || (glyph.height == 0)) || (glyph.width == 0)) {
                /* continue */ ()
              } else ()
              this.capHeight = java.lang.Math.max(this.capHeight, glyph.height)
            }
          }
        } else {
          this.capHeight = capGlyph.height
        }
        this.capHeight = this.capHeight - padY
        this.ascent = baseLine - this.capHeight
        this.down = -this.lineHeight
        if (flip) {
          this.ascent = -this.ascent
          this.down = -this.down
        } else ()
        if (hasMetricsOverride) {
          this.ascent = overrideAscent
          this.descent = overrideDescent
          this.down = overrideDown
          this.capHeight = overrideCapHeight
          this.lineHeight = overrideLineHeight
          this.spaceXadvance = overrideSpaceXAdvance
          this.xHeight = overrideXHeight
        } else ()
      } catch {
        case ex: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Error loading font file: " + fontFile, ex)
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
      }
    }
    def setGlyphRegion(glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph, region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
      val texture: com.badlogic.gdx.graphics.Texture = region.getTexture()
      val invTexWidth: scala.Float = 1.0f / texture.getWidth()
      val invTexHeight: scala.Float = 1.0f / texture.getHeight()
      var offsetX: scala.Float = 0
      var offsetY: scala.Float = 0
      var u: scala.Float = region.u
      var v: scala.Float = region.v
      val regionWidth: scala.Float = region.getRegionWidth()
      val regionHeight: scala.Float = region.getRegionHeight()
      if (region.isInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]) {
        val atlasRegion: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = region.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]
        offsetX = atlasRegion.offsetX
        offsetY = (atlasRegion.originalHeight - atlasRegion.packedHeight) - atlasRegion.offsetY
      } else ()
      var x: scala.Float = glyph.srcX
      var x2: scala.Float = glyph.srcX + glyph.width
      var y: scala.Float = glyph.srcY
      var y2: scala.Float = glyph.srcY + glyph.height
      if (offsetX > 0) {
        x = x - offsetX
        if (x < 0) {
          glyph.width = glyph.width + x
          glyph.xoffset = glyph.xoffset - x
          x = 0
        } else ()
        x2 = x2 - offsetX
        if (x2 > regionWidth) {
          glyph.width = glyph.width - (x2 - regionWidth)
          x2 = regionWidth
        } else ()
      } else ()
      if (offsetY > 0) {
        y = y - offsetY
        if (y < 0) {
          glyph.height = glyph.height + y
          if (glyph.height < 0) {
            glyph.height = 0
          } else ()
          y = 0
        } else ()
        y2 = y2 - offsetY
        if (y2 > regionHeight) {
          val amount: scala.Float = y2 - regionHeight
          glyph.height = glyph.height - amount
          glyph.yoffset = glyph.yoffset + amount
          y2 = regionHeight
        } else ()
      } else ()
      glyph.u = u + (x * invTexWidth)
      glyph.u2 = u + (x2 * invTexWidth)
      if (this.flipped) {
        glyph.v = v + (y * invTexHeight)
        glyph.v2 = v + (y2 * invTexHeight)
      } else {
        glyph.v2 = v + (y * invTexHeight)
        glyph.v = v + (y2 * invTexHeight)
      }
    }
    def setLineHeight(height: scala.Float): scala.Unit = {
      this.lineHeight = height * this.scaleY
      this.down = if (this.flipped) this.lineHeight else -this.lineHeight
    }
    def setGlyph(ch: scala.Int, glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph): scala.Unit = {
      var page: scala.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = this.glyphs(ch / BitmapFont.PAGE_SIZE)
      if (page == null) {
        this.glyphs(ch / BitmapFont.PAGE_SIZE) = {
          page = new scala.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph](BitmapFont.PAGE_SIZE)
          page
        }
      } else ()
      page(ch & (BitmapFont.PAGE_SIZE - 1)) = glyph
    }
    def getFirstGlyph(): com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = {
      for (page <- this.glyphs) {
        if (page == null) {
          /* continue */ ()
        } else ()
        for (glyph <- page) {
          if (((glyph == null) || (glyph.height == 0)) || (glyph.width == 0)) {
            /* continue */ ()
          } else ()
          return glyph
        }
      }
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No glyphs found.")
    }
    def hasGlyph(ch: scala.Char): scala.Boolean = {
      if (this.missingGlyph != null) {
        return true
      } else ()
      return this.getGlyph(ch) != null
    }
    def getGlyph(ch: scala.Char): com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = {
      val page: scala.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = this.glyphs(ch / BitmapFont.PAGE_SIZE)
      if (page != null) {
        return page(ch & (BitmapFont.PAGE_SIZE - 1))
      } else ()
      return null
    }
    def getGlyphs(run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun, str: java.lang.CharSequence, start$arg: scala.Int, `end`: scala.Int, lastGlyph$arg: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph): scala.Unit = {
      var start: scala.Int = start$arg
      var lastGlyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = lastGlyph$arg
      val max: scala.Int = `end` - start
      if (max == 0) {
        return
      } else ()
      val markupEnabled: scala.Boolean = this.markupEnabled
      val scaleX: scala.Float = this.scaleX
      val glyphs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = run.glyphs
      val xAdvances: com.badlogic.gdx.utils.FloatArray = run.xAdvances
      glyphs.ensureCapacity(max)
      run.xAdvances.ensureCapacity(max + 1)
      while ({ {
        val ch: scala.Char = str.charAt({ start += 1; start })
        if (ch == '\r') {
          /* continue */ ()
        } else ()
        var glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = this.getGlyph(ch)
        if (glyph == null) {
          if (this.missingGlyph == null) {
            /* continue */ ()
          } else ()
          glyph = this.missingGlyph
        } else ()
        glyphs.add(glyph)
        xAdvances.add(if (lastGlyph == null) if (glyph.fixedWidth) 0 else ((-glyph.xoffset) * scaleX) - this.padLeft else (lastGlyph.xadvance + lastGlyph.getKerning(ch)) * scaleX)
        lastGlyph = glyph
        if (((markupEnabled && (ch == '[')) && (start < `end`)) && (str.charAt(start) == '[')) {
          start = start + 1
        } else ()
      }; start < `end` }) ()
      if (lastGlyph != null) {
        val lastGlyphWidth: scala.Float = if (lastGlyph.fixedWidth) lastGlyph.xadvance * scaleX else ((lastGlyph.width + lastGlyph.xoffset) * scaleX) - this.padRight
        xAdvances.add(lastGlyphWidth)
      } else ()
    }
    def getWrapIndex(glyphs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph], start: scala.Int): scala.Int = {
      var i: scala.Int = start - 1
      val glyphsItems: scala.Array[java.lang.Object] = glyphs.items.asInstanceOf[scala.Array[java.lang.Object]]
      var ch: scala.Char = glyphsItems(i).asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph].id.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
      if (this.isWhitespace(ch)) {
        return i
      } else ()
      if (this.isBreakChar(ch)) {
        i = i - 1
      } else ();
      { ; while (i > 0) { {
        ch = glyphsItems(i).asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph].id.asInstanceOf[scala.Char].asInstanceOf[scala.Char]
        if (this.isWhitespace(ch) || this.isBreakChar(ch)) {
          return i + 1
        } else ()
      }; i = i - 1 } }
      return 0
    }
    def isBreakChar(c: scala.Char): scala.Boolean = {
      if (this.breakChars == null) {
        return false
      } else ()
      for (br <- this.breakChars) {
        if (c == br) {
          return true
        } else ()
      }
      return false
    }
    def isWhitespace(c: scala.Char): scala.Boolean = {
      c match {
        case '\n' | '\r' | '\t' | ' ' => {
          return true
        }
        case _ => {
          return false
        }
      }
    }
    def getImagePath(index: scala.Int): java.lang.String = {
      return this.imagePaths(index)
    }
    def getImagePaths(): scala.Array[java.lang.String] = {
      return this.imagePaths
    }
    def getFontFile(): com.badlogic.gdx.files.FileHandle = {
      return this.fontFile
    }
    def setScale(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
      if (scaleX == 0) {
        throw new java.lang.IllegalArgumentException("scaleX cannot be 0.")
      } else ()
      if (scaleY == 0) {
        throw new java.lang.IllegalArgumentException("scaleY cannot be 0.")
      } else ()
      val x: scala.Float = scaleX / this.scaleX
      val y: scala.Float = scaleY / this.scaleY
      this.lineHeight = this.lineHeight * y
      this.spaceXadvance = this.spaceXadvance * x
      this.xHeight = this.xHeight * y
      this.capHeight = this.capHeight * y
      this.ascent = this.ascent * y
      this.descent = this.descent * y
      this.down = this.down * y
      this.padLeft = this.padLeft * x
      this.padRight = this.padRight * x
      this.padTop = this.padTop * y
      this.padBottom = this.padBottom * y
      this.scaleX = scaleX
      this.scaleY = scaleY
    }
    def setScale(scaleXY: scala.Float): scala.Unit = {
      this.setScale(scaleXY, scaleXY)
    }
    def scale(amount: scala.Float): scala.Unit = {
      this.setScale(this.scaleX + amount, this.scaleY + amount)
    }
    def toString(): java.lang.String = {
      return if (this.name != null) this.name else super.toString()
    }
  }
}