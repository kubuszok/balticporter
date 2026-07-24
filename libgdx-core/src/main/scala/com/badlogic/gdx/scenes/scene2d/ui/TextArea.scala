package com.badlogic.gdx.scenes.scene2d.ui

class TextArea extends com.badlogic.gdx.scenes.scene2d.ui.TextField {
  var linesBreak: com.badlogic.gdx.utils.IntArray = null.asInstanceOf[com.badlogic.gdx.utils.IntArray]
  private var lastText: java.lang.String = null.asInstanceOf[java.lang.String]
  var cursorLine: scala.Int = 0
  var firstLineShowing: scala.Int = 0
  private var linesShowing: scala.Int = 0
  var moveOffset: scala.Float = 0.0f
  private var prefRows: scala.Float = 0.0f
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this()
  }
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this()
  }
  def this(text: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle) = {
    this()
  }
  def initialize(): scala.Unit = {
    super.initialize()
    writeEnters = true
    this.linesBreak = new com.badlogic.gdx.utils.IntArray()
    this.cursorLine = 0
    this.firstLineShowing = 0
    this.moveOffset = -1
    this.linesShowing = 0
  }
  def letterUnderCursor(x$arg: scala.Float): scala.Int = {
    var x: scala.Float = x$arg
    if (this.linesBreak.size > 0) {
      if ((this.cursorLine * 2) >= this.linesBreak.size) {
        return text.length()
      } else {
        val glyphPositions: scala.Array[scala.Float] = this.glyphPositions.items
        val start: scala.Int = this.linesBreak.items(this.cursorLine * 2)
        x = x + glyphPositions(start)
        val `end`: scala.Int = this.linesBreak.items((this.cursorLine * 2) + 1)
        var i: scala.Int = start;
        { ; while (i < `end`) { {
          if (glyphPositions(i) > x) {
            /* break */ ()
          } else ()
        }; i = i + 1 } }
        if ((i > 0) && ((glyphPositions(i) - x) <= (x - glyphPositions(i - 1)))) {
          return i
        } else ()
        return java.lang.Math.max(0, i - 1)
      }
    } else {
      return 0
    }
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    textHeight = style.font.getCapHeight() - style.font.getDescent()
    if (text != null) {
      this.updateDisplayText()
    } else ()
    this.invalidateHierarchy()
  }
  def setPrefRows(prefRows: scala.Float): scala.Unit = {
    this.prefRows = prefRows
  }
  def getPrefHeight(): scala.Float = {
    if (this.prefRows <= 0) {
      return super.getPrefHeight()
    } else {
      var prefHeight: scala.Float = java.lang.Math.ceil(this.style.font.getLineHeight() * this.prefRows).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      if (this.style.background != null) {
        prefHeight = java.lang.Math.max((prefHeight + this.style.background.getBottomHeight()) + this.style.background.getTopHeight(), this.style.background.getMinHeight())
      } else ()
      return prefHeight
    }
  }
  def getLines(): scala.Int = {
    return (this.linesBreak.size / 2) + (if (this.newLineAtEnd()) 1 else 0)
  }
  def newLineAtEnd(): scala.Boolean = {
    return (text.length() != 0) && ((text.charAt(text.length() - 1) == com.badlogic.gdx.scenes.scene2d.ui.TextField.NEWLINE) || (text.charAt(text.length() - 1) == com.badlogic.gdx.scenes.scene2d.ui.TextField.CARRIAGE_RETURN))
  }
  def moveCursorLine(line: scala.Int): scala.Unit = {
    if (line < 0) {
      this.cursorLine = 0
      cursor = 0
      this.moveOffset = -1
    } else {
      if (line >= this.getLines()) {
        val newLine: scala.Int = this.getLines() - 1
        cursor = text.length()
        if ((line > this.getLines()) || (newLine == this.cursorLine)) {
          this.moveOffset = -1
        } else ()
        this.cursorLine = newLine
      } else {
        if (line != this.cursorLine) {
          if (this.moveOffset < 0) {
            this.moveOffset = if (this.linesBreak.size <= (this.cursorLine * 2)) 0 else glyphPositions.get(cursor) - glyphPositions.get(this.linesBreak.get(this.cursorLine * 2))
          } else ()
          this.cursorLine = line
          cursor = if ((this.cursorLine * 2) >= this.linesBreak.size) text.length() else this.linesBreak.get(this.cursorLine * 2)
          while (((cursor < text.length()) && (cursor <= (this.linesBreak.get((this.cursorLine * 2) + 1) - 1))) && ((glyphPositions.get(cursor) - glyphPositions.get(this.linesBreak.get(this.cursorLine * 2))) < this.moveOffset)) {
            cursor = cursor + 1
          }
          this.showCursor()
        } else ()
      }
    }
  }
  def updateCurrentLine(): scala.Unit = {
    val index: scala.Int = this.calculateCurrentLineIndex(cursor)
    val line: scala.Int = index / 2
    if (((((index % 2) == 0) || ((index + 1) >= this.linesBreak.size)) || (cursor != this.linesBreak.items(index))) || (this.linesBreak.items(index + 1) != this.linesBreak.items(index))) {
      if ((((line < (this.linesBreak.size / 2)) || (text.length() == 0)) || (text.charAt(text.length() - 1) == com.badlogic.gdx.scenes.scene2d.ui.TextField.NEWLINE)) || (text.charAt(text.length() - 1) == com.badlogic.gdx.scenes.scene2d.ui.TextField.CARRIAGE_RETURN)) {
        this.cursorLine = line
      } else ()
    } else ()
    this.updateFirstLineShowing()
  }
  def showCursor(): scala.Unit = {
    this.updateCurrentLine()
    this.updateFirstLineShowing()
  }
  def updateFirstLineShowing(): scala.Unit = {
    if (this.cursorLine != this.firstLineShowing) {
      val step: scala.Int = if (this.cursorLine >= this.firstLineShowing) 1 else -1
      while ((this.firstLineShowing > this.cursorLine) || (((this.firstLineShowing + this.linesShowing) - 1) < this.cursorLine)) {
        this.firstLineShowing = this.firstLineShowing + step
      }
    } else ()
  }
  private def calculateCurrentLineIndex(cursor: scala.Int): scala.Int = {
    var index: scala.Int = 0
    while ((index < this.linesBreak.size) && (cursor > this.linesBreak.items(index))) {
      index = index + 1
    }
    return index
  }
  def sizeChanged(): scala.Unit = {
    this.lastText = null
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.background
    val availableHeight: scala.Float = this.getHeight() - (if (background == null) 0 else background.getBottomHeight() + background.getTopHeight())
    this.linesShowing = java.lang.Math.floor(availableHeight / font.getLineHeight()).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def getTextY(font: com.badlogic.gdx.graphics.g2d.BitmapFont, background: com.badlogic.gdx.scenes.scene2d.utils.Drawable): scala.Float = {
    var textY: scala.Float = this.getHeight()
    if (background != null) {
      textY = textY - background.getTopHeight()
    } else ()
    if (font.usesIntegerPositions()) {
      textY = textY.asInstanceOf[scala.Int]
    } else ()
    return textY
  }
  def drawSelection(selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, x: scala.Float, y: scala.Float): scala.Unit = {
    var i: scala.Int = this.firstLineShowing * 2
    var offsetY: scala.Float = 0
    val minIndex: scala.Int = java.lang.Math.min(cursor, selectionStart)
    val maxIndex: scala.Int = java.lang.Math.max(cursor, selectionStart)
    val fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = font.getData()
    val lineHeight: scala.Float = this.style.font.getLineHeight()
    while (((i + 1) < this.linesBreak.size) && (i < ((this.firstLineShowing + this.linesShowing) * 2))) {
      val lineStart: scala.Int = this.linesBreak.get(i)
      val lineEnd: scala.Int = this.linesBreak.get(i + 1)
      if (!(((((minIndex < lineStart) && (minIndex < lineEnd)) && (maxIndex < lineStart)) && (maxIndex < lineEnd)) || ((((minIndex > lineStart) && (minIndex > lineEnd)) && (maxIndex > lineStart)) && (maxIndex > lineEnd)))) {
        val start: scala.Int = java.lang.Math.max(lineStart, minIndex)
        val `end`: scala.Int = java.lang.Math.min(lineEnd, maxIndex)
        var fontLineOffsetX: scala.Float = 0
        var fontLineOffsetWidth: scala.Float = 0
        val lineFirst: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = fontData.getGlyph(displayText.charAt(lineStart))
        if (lineFirst != null) {
          if (start == lineStart) {
            fontLineOffsetWidth = if (lineFirst.fixedWidth) 0 else ((-lineFirst.xoffset) * fontData.scaleX) - fontData.padLeft
          } else {
            fontLineOffsetX = if (lineFirst.fixedWidth) 0 else ((-lineFirst.xoffset) * fontData.scaleX) - fontData.padLeft
          }
        } else ()
        val selectionX: scala.Float = glyphPositions.get(start) - glyphPositions.get(lineStart)
        val selectionWidth: scala.Float = glyphPositions.get(`end`) - glyphPositions.get(start)
        selection.draw(batch, (x + selectionX) + fontLineOffsetX, (y - lineHeight) - offsetY, selectionWidth + fontLineOffsetWidth, font.getLineHeight())
      } else ()
      offsetY = offsetY + font.getLineHeight()
      i = i + 2
    }
  }
  def drawText(batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, x: scala.Float, y: scala.Float): scala.Unit = {
    var offsetY: scala.Float = (-(this.style.font.getLineHeight() - textHeight)) / 2;
    { var i: scala.Int = this.firstLineShowing * 2; while ((i < ((this.firstLineShowing + this.linesShowing) * 2)) && (i < this.linesBreak.size)) { {
      font.draw(batch, displayText, x, y + offsetY, this.linesBreak.items(i), this.linesBreak.items(i + 1), 0, com.badlogic.gdx.utils.Align.left, false)
      offsetY = offsetY - font.getLineHeight()
    }; i = i + 2 } }
  }
  def drawCursor(cursorPatch: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, x: scala.Float, y: scala.Float): scala.Unit = {
    cursorPatch.draw(batch, x + this.getCursorX(), y + this.getCursorY(), cursorPatch.getMinWidth(), font.getLineHeight())
  }
  def calculateOffsets(): scala.Unit = {
    super.calculateOffsets()
    if (!this.text.equals(this.lastText)) {
      this.lastText = text
      val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
      val maxWidthLine: scala.Float = this.getWidth() - (if (this.style.background != null) this.style.background.getLeftWidth() + this.style.background.getRightWidth() else 0)
      this.linesBreak.clear()
      var lineStart: scala.Int = 0
      var lastSpace: scala.Int = 0
      var lastCharacter: scala.Char = '\u0000'
      val layoutPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.graphics.g2d.GlyphLayout] = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.getPool(classOf[com.badlogic.gdx.graphics.g2d.GlyphLayout])
      val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = layoutPool.obtain();
      { var i: scala.Int = 0; while (i < text.length()) { {
        lastCharacter = text.charAt(i)
        if ((lastCharacter == com.badlogic.gdx.scenes.scene2d.ui.TextField.CARRIAGE_RETURN) || (lastCharacter == com.badlogic.gdx.scenes.scene2d.ui.TextField.NEWLINE)) {
          this.linesBreak.add(lineStart)
          this.linesBreak.add(i)
          lineStart = i + 1
        } else {
          lastSpace = if (this.continueCursor(i, 0)) lastSpace else i
          layout.setText(font, text.subSequence(lineStart, i + 1))
          if (layout.width > maxWidthLine) {
            if (lineStart >= lastSpace) {
              lastSpace = i - 1
            } else ()
            this.linesBreak.add(lineStart)
            this.linesBreak.add(lastSpace + 1)
            lineStart = lastSpace + 1
            lastSpace = lineStart
          } else ()
        }
      }; i = i + 1 } }
      layoutPool.free(layout)
      if (lineStart < text.length()) {
        this.linesBreak.add(lineStart)
        this.linesBreak.add(text.length())
      } else ()
      this.showCursor()
    } else ()
  }
  def createInputListener(): com.badlogic.gdx.scenes.scene2d.InputListener = {
    return new TextAreaListener()
  }
  def setSelection(selectionStart: scala.Int, selectionEnd: scala.Int): scala.Unit = {
    super.setSelection(selectionStart, selectionEnd)
    this.updateCurrentLine()
  }
  def moveCursor(forward: scala.Boolean, jump: scala.Boolean): scala.Unit = {
    val count: scala.Int = if (forward) 1 else -1
    val index: scala.Int = (this.cursorLine * 2) + count
    if ((((index >= 0) && ((index + 1) < this.linesBreak.size)) && (this.linesBreak.items(index) == cursor)) && (this.linesBreak.items(index + 1) == cursor)) {
      this.cursorLine = this.cursorLine + count
      if (jump) {
        super.moveCursor(forward, jump)
      } else ()
      this.showCursor()
    } else {
      super.moveCursor(forward, jump)
    }
    this.updateCurrentLine()
  }
  def continueCursor(index: scala.Int, offset: scala.Int): scala.Boolean = {
    val pos: scala.Int = this.calculateCurrentLineIndex(index + offset)
    return super.continueCursor(index, offset) && ((((pos < 0) || (pos >= (this.linesBreak.size - 2))) || (this.linesBreak.items(pos + 1) != index)) || (this.linesBreak.items(pos + 1) == this.linesBreak.items(pos + 2)))
  }
  def getCursorLine(): scala.Int = {
    return this.cursorLine
  }
  def getFirstLineShowing(): scala.Int = {
    return this.firstLineShowing
  }
  def getLinesShowing(): scala.Int = {
    return this.linesShowing
  }
  def getCursorX(): scala.Float = {
    var textOffset: scala.Float = 0
    val fontData: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = this.style.font.getData()
    if (!((cursor >= this.glyphPositions.size) || ((this.cursorLine * 2) >= this.linesBreak.size))) {
      val lineStart: scala.Int = this.linesBreak.items(this.cursorLine * 2)
      var glyphOffset: scala.Float = 0
      val lineFirst: com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph = fontData.getGlyph(displayText.charAt(lineStart))
      if (lineFirst != null) {
        glyphOffset = if (lineFirst.fixedWidth) 0 else ((-lineFirst.xoffset) * fontData.scaleX) - fontData.padLeft
      } else ()
      textOffset = (glyphPositions.get(cursor) - glyphPositions.get(lineStart)) + glyphOffset
    } else ()
    return textOffset + fontData.cursorX
  }
  def getCursorY(): scala.Float = {
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    return (-((this.cursorLine - this.firstLineShowing) + 1)) * font.getLineHeight()
  }
  class TextAreaListener extends com.badlogic.gdx.scenes.scene2d.ui.TextField#TextFieldClickListener {
    def setCursorPosition(x$arg: scala.Float, y$arg: scala.Float): scala.Unit = {
      var x: scala.Float = x$arg
      var y: scala.Float = y$arg
      moveOffset = -1
      val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = style.background
      val font: com.badlogic.gdx.graphics.g2d.BitmapFont = style.font
      var height: scala.Float = getHeight()
      if (background != null) {
        height = height - background.getTopHeight()
        x = x - background.getLeftWidth()
      } else ()
      x = java.lang.Math.max(0, x)
      if (background != null) {
        y = y - background.getTopHeight()
      } else ()
      cursorLine = java.lang.Math.floor((height - y) / font.getLineHeight()).asInstanceOf[scala.Int] + firstLineShowing
      cursorLine = java.lang.Math.max(0, java.lang.Math.min(cursorLine, getLines() - 1))
      super.setCursorPosition(x, y)
      updateCurrentLine()
    }
    def keyDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
      val result: scala.Boolean = super.keyDown(event, keycode)
      if (hasKeyboardFocus()) {
        var repeat: scala.Boolean = false
        val shift: scala.Boolean = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT) || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT)
        if (keycode == com.badlogic.gdx.Input.Keys.DOWN) {
          if (shift) {
            if (!hasSelection) {
              selectionStart = cursor
              hasSelection = true
            } else ()
          } else {
            clearSelection()
          }
          moveCursorLine(cursorLine + 1)
          repeat = true
        } else {
          if (keycode == com.badlogic.gdx.Input.Keys.UP) {
            if (shift) {
              if (!hasSelection) {
                selectionStart = cursor
                hasSelection = true
              } else ()
            } else {
              clearSelection()
            }
            moveCursorLine(cursorLine - 1)
            repeat = true
          } else {
            moveOffset = -1
          }
        }
        if (repeat) {
          this.scheduleKeyRepeatTask(keycode)
        } else ()
        showCursor()
        return true
      } else ()
      return result
    }
    def checkFocusTraversal(character: scala.Char): scala.Boolean = {
      return focusTraversal && (character == com.badlogic.gdx.scenes.scene2d.ui.TextField.TAB)
    }
    def keyTyped(event: com.badlogic.gdx.scenes.scene2d.InputEvent, character: scala.Char): scala.Boolean = {
      val result: scala.Boolean = super.keyTyped(event, character)
      showCursor()
      return result
    }
    def goHome(jump: scala.Boolean): scala.Unit = {
      if (jump) {
        cursor = 0
      } else {
        if ((cursorLine * 2) < linesBreak.size) {
          cursor = linesBreak.get(cursorLine * 2)
        } else ()
      }
    }
    def goEnd(jump: scala.Boolean): scala.Unit = {
      if (jump || (cursorLine >= getLines())) {
        cursor = text.length()
      } else {
        if (((cursorLine * 2) + 1) < linesBreak.size) {
          cursor = linesBreak.get((cursorLine * 2) + 1)
        } else ()
      }
    }
  }
  object TextAreaListener {
    export com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldClickListener.*
  }
}
object TextArea {
  export com.badlogic.gdx.scenes.scene2d.ui.TextField.*
}