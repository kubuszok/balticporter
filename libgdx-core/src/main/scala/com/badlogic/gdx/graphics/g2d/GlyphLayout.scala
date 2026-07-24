package com.badlogic.gdx.graphics.g2d

class GlyphLayout extends com.badlogic.gdx.utils.Pool.Poolable {
  final val runs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun] = new com.badlogic.gdx.utils.Array(1)
  final val colors: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray(2)
  var glyphCount: scala.Int = 0
  var width: scala.Float = 0.0f
  var height: scala.Float = 0.0f
  def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, str: java.lang.CharSequence, start: scala.Int, `end`: scala.Int, color: com.badlogic.gdx.graphics.Color, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean, truncate: java.lang.String) = {
    this()
    this.setText(font, str, start, `end`, color, targetWidth, halign, wrap, truncate)
  }
  def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, str: java.lang.CharSequence, color: com.badlogic.gdx.graphics.Color, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean) = {
    this()
    this.setText(font, str, color, targetWidth, halign, wrap)
  }
  def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, str: java.lang.CharSequence) = {
    this()
    this.setText(font, str)
  }
  def setText(font: com.badlogic.gdx.graphics.g2d.BitmapFont, str: java.lang.CharSequence): scala.Unit = {
    this.setText(font, str, 0, str.length(), font.getColor(), 0, com.badlogic.gdx.utils.Align.left, false, null)
  }
  def setText(font: com.badlogic.gdx.graphics.g2d.BitmapFont, str: java.lang.CharSequence, color: com.badlogic.gdx.graphics.Color, targetWidth: scala.Float, halign: scala.Int, wrap: scala.Boolean): scala.Unit = {
    this.setText(font, str, 0, str.length(), color, targetWidth, halign, wrap, null)
  }
  def setText(font: com.badlogic.gdx.graphics.g2d.BitmapFont, str: java.lang.CharSequence, start$arg: scala.Int, `end`: scala.Int, color: com.badlogic.gdx.graphics.Color, targetWidth$arg: scala.Float, halign: scala.Int, wrap: scala.Boolean, truncate: java.lang.String): scala.Unit = {
    var start: scala.Int = start$arg
    var targetWidth: scala.Float = targetWidth$arg
    this.reset()
    val fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = font.data
    if (start == `end`) {
      this.height = fontData.capHeight
      return
    } else ()
    if (wrap) {
      targetWidth = java.lang.Math.max(targetWidth, fontData.spaceXadvance * 3)
    } else ()
    val wrapOrTruncate: scala.Boolean = wrap || (truncate != null)
    var currentColor: scala.Int = color.toIntBits()
    var nextColor: scala.Int = currentColor
    this.colors.add(0, currentColor)
    val markupEnabled: scala.Boolean = fontData.markupEnabled
    if (markupEnabled) {
      GlyphLayout.colorStack.add(currentColor)
    } else ()
    var isLastRun: scala.Boolean = false
    var y: scala.Float = 0
    val down: scala.Float = fontData.down
    var lineRun: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = null
    var lastGlyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = null
    var runStart: scala.Int = start
    while (true) {
      var runEnd: scala.Int = 0
      var newline: scala.Boolean = false
      if (start == `end`) {
        if (runStart == `end`) {
          /* break */ ()
        } else ()
        runEnd = `end`
        isLastRun = true
      } else {
        str.charAt({ start += 1; start }) match {
          case '\n' => {
            runEnd = start - 1
            newline = true
          }
          case '[' => {
            if (markupEnabled) {
              val length: scala.Int = this.parseColorMarkup(str, start, `end`)
              if (length >= 0) {
                runEnd = start - 1
                start = start + (length + 1)
                if (start == `end`) {
                  isLastRun = true
                } else {
                  nextColor = GlyphLayout.colorStack.peek()
                }
                /* break */ ()
              } else ()
              if (length == (-2)) {
                start = start + 1
              } else ()
            } else ()
            /* continue */ ()
          }
          case _ => {
            /* continue */ ()
          }
        }
      };
      {
        val run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = GlyphLayout.glyphRunPool.obtain()
        run.x = 0
        run.y = y
        fontData.getGlyphs(run, str, runStart, runEnd, lastGlyph)
        this.glyphCount = this.glyphCount + run.glyphs.size
        if (nextColor != currentColor) {
          if (this.colors.get(this.colors.size - 2) == this.glyphCount) {
            this.colors.set(this.colors.size - 1, nextColor)
          } else {
            this.colors.add(this.glyphCount)
            this.colors.add(nextColor)
          }
          currentColor = nextColor
        } else ()
        if (run.glyphs.size == 0) {
          GlyphLayout.glyphRunPool.free(run)
          if (lineRun == null) {
            /* break */ ()
          } else ()
        } else {
          if (lineRun == null) {
            lineRun = run
            this.runs.add(lineRun)
          } else {
            lineRun.appendRun(run)
            GlyphLayout.glyphRunPool.free(run)
          }
        }
        if (newline || isLastRun) {
          this.setLastGlyphXAdvance(fontData, lineRun)
          lastGlyph = null
        } else {
          lastGlyph = lineRun.glyphs.peek()
        }
        if ((!wrapOrTruncate) || (lineRun.glyphs.size == 0)) {
          /* break */ ()
        } else ()
        if (newline || isLastRun) {
          var runWidth: scala.Float = lineRun.xAdvances.first() + lineRun.xAdvances.get(1);
          { var i: scala.Int = 2; while (i < lineRun.xAdvances.size) { {
            val glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = lineRun.glyphs.get(i - 1)
            val glyphWidth: scala.Float = this.getGlyphWidth(glyph, fontData)
            if (((runWidth + glyphWidth) - GlyphLayout.epsilon) <= targetWidth) {
              runWidth = runWidth + lineRun.xAdvances.items(i)
              /* continue */ ()
            } else ()
            if (truncate != null) {
              this.truncate(fontData, lineRun, targetWidth, truncate)
              /* break */ ()
            } else ()
            var wrapIndex: scala.Int = fontData.getWrapIndex(lineRun.glyphs, i)
            if (((wrapIndex == 0) && (lineRun.x == 0)) || (wrapIndex >= lineRun.glyphs.size)) {
              wrapIndex = i - 1
            } else ()
            lineRun = this.wrap(fontData, lineRun, wrapIndex)
            if (lineRun == null) {
              /* break */ ()
            } else ()
            this.runs.add(lineRun)
            y = y + down
            lineRun.x = 0
            lineRun.y = y
            runWidth = lineRun.xAdvances.first() + lineRun.xAdvances.get(1)
            i = 1
          }; i = i + 1 } }
        } else ()
      }
      if (newline) {
        lineRun = null
        lastGlyph = null
        if (runEnd == runStart) {
          y = y + (down * fontData.blankLineScale)
        } else {
          y = y + down
        }
      } else ()
      runStart = start
    }
    this.height = fontData.capHeight + java.lang.Math.abs(y)
    this.calculateWidths(fontData)
    this.alignRuns(targetWidth, halign)
    if (markupEnabled) {
      GlyphLayout.colorStack.clear()
    } else ()
  }
  private def calculateWidths(fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData): scala.Unit = {
    var width: scala.Float = 0
    val runsItems: scala.Array[java.lang.Object] = this.runs.items.asInstanceOf[scala.Array[java.lang.Object]];
    { var i: scala.Int = 0; val n: scala.Int = this.runs.size; while (i < n) { {
      val run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = runsItems(i).asInstanceOf[com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun]
      val xAdvances: scala.Array[scala.Float] = run.xAdvances.items
      var runWidth: scala.Float = run.x + xAdvances(0)
      var max: scala.Float = 0
      val glyphs: scala.Array[java.lang.Object] = run.glyphs.items.asInstanceOf[scala.Array[java.lang.Object]];
      { var ii: scala.Int = 0; val nn: scala.Int = run.glyphs.size; while (ii < nn) { {
        val glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = glyphs(ii).asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph]
        val glyphWidth: scala.Float = this.getGlyphWidth(glyph, fontData)
        max = java.lang.Math.max(max, runWidth + glyphWidth)
        ii = ii + 1
        runWidth = runWidth + xAdvances(ii)
      };  } }
      run.width = java.lang.Math.max(runWidth, max) - run.x
      width = java.lang.Math.max(width, run.x + run.width)
    }; i = i + 1 } }
    this.width = width
  }
  private def alignRuns(targetWidth: scala.Float, halign: scala.Int): scala.Unit = {
    if ((halign & com.badlogic.gdx.utils.Align.left) == 0) {
      val center: scala.Boolean = (halign & com.badlogic.gdx.utils.Align.center) != 0
      val runsItems: scala.Array[java.lang.Object] = this.runs.items.asInstanceOf[scala.Array[java.lang.Object]];
      { var i: scala.Int = 0; val n: scala.Int = this.runs.size; while (i < n) { {
        val run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = runsItems(i).asInstanceOf[com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun]
        run.x = run.x + (if (center) 0.5f * (targetWidth - run.width) else targetWidth - run.width)
      }; i = i + 1 } }
    } else ()
  }
  private def truncate(fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData, run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun, targetWidth$arg: scala.Float, truncate: java.lang.String): scala.Unit = {
    var targetWidth: scala.Float = targetWidth$arg
    var glyphCount: scala.Int = run.glyphs.size
    val truncateRun: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = GlyphLayout.glyphRunPool.obtain()
    fontData.getGlyphs(truncateRun, truncate, 0, truncate.length(), null)
    var truncateWidth: scala.Float = 0
    if (truncateRun.xAdvances.size > 0) {
      this.setLastGlyphXAdvance(fontData, truncateRun)
      val xAdvances: scala.Array[scala.Float] = truncateRun.xAdvances.items;
      { var i: scala.Int = 1; val n: scala.Int = truncateRun.xAdvances.size; while (i < n) { {
        truncateWidth = truncateWidth + xAdvances(i)
      }; i = i + 1 } }
    } else ()
    targetWidth = targetWidth - truncateWidth
    var count: scala.Int = 0
    var width: scala.Float = run.x
    val xAdvances: scala.Array[scala.Float] = run.xAdvances.items
    while (count < run.xAdvances.size) {
      val xAdvance: scala.Float = xAdvances(count)
      width = width + xAdvance
      if (width > targetWidth) {
        /* break */ ()
      } else ()
      count = count + 1
    }
    if (count > 1) {
      run.glyphs.truncate(count - 1)
      run.xAdvances.truncate(count)
      this.setLastGlyphXAdvance(fontData, run)
      if (truncateRun.xAdvances.size > 0) {
        run.xAdvances.addAll(truncateRun.xAdvances, 1, truncateRun.xAdvances.size - 1)
      } else ()
    } else {
      run.glyphs.clear()
      run.xAdvances.clear()
      run.xAdvances.addAll(truncateRun.xAdvances)
    }
    val droppedGlyphCount: scala.Int = glyphCount - run.glyphs.size
    if (droppedGlyphCount > 0) {
      this.glyphCount = this.glyphCount - droppedGlyphCount
      if (fontData.markupEnabled) {
        while ((this.colors.size > 2) && (this.colors.get(this.colors.size - 2) >= this.glyphCount)) {
          this.colors.size = this.colors.size - 2
        }
      } else ()
    } else ()
    run.glyphs.addAll(truncateRun.glyphs)
    this.glyphCount = this.glyphCount + truncate.length()
    GlyphLayout.glyphRunPool.free(truncateRun)
  }
  private def wrap(fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData, first: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun, wrapIndex: scala.Int): com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = {
    val glyphs2: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = first.glyphs
    var glyphCount: scala.Int = first.glyphs.size
    val xAdvances2: com.badlogic.gdx.utils.FloatArray = first.xAdvances
    var firstEnd: scala.Int = wrapIndex;
    { ; while (firstEnd > 0) { {
      if (!fontData.isWhitespace(glyphs2.get(firstEnd - 1).id.asInstanceOf[scala.Char])) {
        /* break */ ()
      } else ()
    }; firstEnd = firstEnd - 1 } }
    var secondStart: scala.Int = wrapIndex;
    { ; while (secondStart < glyphCount) { {
      if (!fontData.isWhitespace(glyphs2.get(secondStart).id.asInstanceOf[scala.Char])) {
        /* break */ ()
      } else ()
    }; secondStart = secondStart + 1 } }
    var second: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = null
    if (secondStart < glyphCount) {
      second = GlyphLayout.glyphRunPool.obtain()
      val glyphs1: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = second.glyphs
      glyphs1.addAll(glyphs2, 0, firstEnd)
      glyphs2.removeRange(0, secondStart - 1)
      first.glyphs = glyphs1
      second.glyphs = glyphs2
      val xAdvances1: com.badlogic.gdx.utils.FloatArray = second.xAdvances
      xAdvances1.addAll(xAdvances2, 0, firstEnd + 1)
      xAdvances2.removeRange(1, secondStart)
      xAdvances2.items(0) = this.getLineOffset(glyphs2, fontData)
      first.xAdvances = xAdvances1
      second.xAdvances = xAdvances2
      val firstGlyphCount: scala.Int = first.glyphs.size
      val secondGlyphCount: scala.Int = second.glyphs.size
      val droppedGlyphCount: scala.Int = (glyphCount - firstGlyphCount) - secondGlyphCount
      this.glyphCount = this.glyphCount - droppedGlyphCount
      if (fontData.markupEnabled && (droppedGlyphCount > 0)) {
        val reductionThreshold: scala.Int = this.glyphCount - secondGlyphCount;
        { var i: scala.Int = this.colors.size - 2; while (i >= 2) { {
          val colorChangeIndex: scala.Int = this.colors.get(i)
          if (colorChangeIndex <= reductionThreshold) {
            /* break */ ()
          } else ()
          this.colors.set(i, colorChangeIndex - droppedGlyphCount)
        }; i = i - 2 } }
      } else ()
    } else {
      glyphs2.truncate(firstEnd)
      xAdvances2.truncate(firstEnd + 1)
      val droppedGlyphCount: scala.Int = secondStart - firstEnd
      if (droppedGlyphCount > 0) {
        this.glyphCount = this.glyphCount - droppedGlyphCount
        if (fontData.markupEnabled && (this.colors.get(this.colors.size - 2) > this.glyphCount)) {
          val lastColor: scala.Int = this.colors.peek()
          while (this.colors.get(this.colors.size - 2) > this.glyphCount) {
            this.colors.size = this.colors.size - 2
          }
          this.colors.set(this.colors.size - 2, this.glyphCount)
          this.colors.set(this.colors.size - 1, lastColor)
        } else ()
      } else ()
    }
    if (firstEnd == 0) {
      GlyphLayout.glyphRunPool.free(first)
      this.runs.pop()
    } else {
      this.setLastGlyphXAdvance(fontData, first)
    }
    return second
  }
  private def setLastGlyphXAdvance(fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData, run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun): scala.Unit = {
    val last: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = run.glyphs.peek()
    if (!last.fixedWidth) {
      run.xAdvances.items(run.xAdvances.size - 1) = this.getGlyphWidth(last, fontData)
    } else ()
  }
  private def getGlyphWidth(glyph: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph, fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData): scala.Float = {
    return ((if (glyph.fixedWidth) glyph.xadvance else glyph.width + glyph.xoffset) * fontData.scaleX) - fontData.padRight
  }
  private def getLineOffset(glyphs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph], fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData): scala.Float = {
    val first: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = glyphs.first()
    return (if (first.fixedWidth) 0 else (-first.xoffset) * fontData.scaleX) - fontData.padLeft
  }
  private def parseColorMarkup(str: java.lang.CharSequence, start: scala.Int, `end`: scala.Int): scala.Int = {
    if (start == `end`) {
      return -1
    } else ()
    str.charAt(start) match {
      case '#' => {
        var color: scala.Int = 0;
        { var i: scala.Int = start + 1; while (i < `end`) { {
          val ch: scala.Char = str.charAt(i)
          if (ch == ']') {
            if ((i < (start + 2)) || (i > (start + 9))) {
              /* break */ ()
            } else ()
            if ((i - start) < 8) {
              color = (color << ((9 - (i - start)) << 2)) | 255
            } else ()
            GlyphLayout.colorStack.add(java.lang.Integer.reverseBytes(color))
            return i - start
          } else ()
          color = (color << 4) + ch
          if ((ch >= '0') && (ch <= '9')) {
            color = color - '0'
          } else {
            if ((ch >= 'A') && (ch <= 'F')) {
              color = color - ('A' - 10)
            } else {
              if ((ch >= 'a') && (ch <= 'f')) {
                color = color - ('a' - 10)
              } else {
                /* break */ ()
              }
            }
          }
        }; i = i + 1 } }
        return -1
      }
      case '[' => {
        return -2
      }
      case ']' => {
        if (GlyphLayout.colorStack.size > 1) {
          GlyphLayout.colorStack.pop()
        } else ()
        return 0
      }
    };
    { var i: scala.Int = start + 1; while (i < `end`) { {
      val ch: scala.Char = str.charAt(i)
      if (ch != ']') {
        /* continue */ ()
      } else ()
      var color: com.badlogic.gdx.graphics.Color = com.badlogic.gdx.graphics.Colors.get(str.subSequence(start, i).toString())
      if (color == null) {
        return -1
      } else ()
      GlyphLayout.colorStack.add(color.toIntBits())
      return i - start
    }; i = i + 1 } }
    return -1
  }
  def reset(): scala.Unit = {
    GlyphLayout.glyphRunPool.freeAll(this.runs)
    this.runs.clear()
    this.colors.clear()
    this.glyphCount = 0
    this.width = 0
    this.height = 0
  }
  def toString(): java.lang.String = {
    if (this.runs.size == 0) {
      return ""
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(128)
    buffer.append(this.width)
    buffer.append('x')
    buffer.append(this.height)
    buffer.append('\n');
    { var i: scala.Int = 0; val n: scala.Int = this.runs.size; while (i < n) { {
      buffer.append(this.runs.get(i).toString())
      buffer.append('\n')
    }; i = i + 1 } }
    buffer.setLength(buffer.length() - 1)
    return buffer.toString()
  }
}
object GlyphLayout {
  private final val glyphRunPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun] = new com.badlogic.gdx.utils.DefaultPool[com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun](com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun.<init>)
  private final val colorStack: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray(4)
  private final val epsilon: scala.Float = 1.0E-4f
  class GlyphRun extends com.badlogic.gdx.utils.Pool.Poolable {
    var glyphs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = new com.badlogic.gdx.utils.Array()
    var xAdvances: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray()
    var x: scala.Float = 0.0f
    var y: scala.Float = 0.0f
    var width: scala.Float = 0.0f
    def appendRun(run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun): scala.Unit = {
      this.glyphs.addAll(run.glyphs)
      if (this.xAdvances.notEmpty()) {
        this.xAdvances.size = this.xAdvances.size - 1
      } else ()
      this.xAdvances.addAll(run.xAdvances)
    }
    def reset(): scala.Unit = {
      this.glyphs.clear()
      this.xAdvances.clear()
    }
    def toString(): java.lang.String = {
      val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(this.glyphs.size + 32)
      val glyphs: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph] = this.glyphs;
      { var i: scala.Int = 0; val n: scala.Int = glyphs.size; while (i < n) { {
        val g: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = glyphs.get(i)
        buffer.append(g.id.asInstanceOf[scala.Char])
      }; i = i + 1 } }
      buffer.append(", ")
      buffer.append(this.x)
      buffer.append(", ")
      buffer.append(this.y)
      buffer.append(", ")
      buffer.append(this.width)
      return buffer.toString()
    }
  }
}