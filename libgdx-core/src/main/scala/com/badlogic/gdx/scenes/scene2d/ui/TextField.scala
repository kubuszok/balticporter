package com.badlogic.gdx.scenes.scene2d.ui

class TextField extends com.badlogic.gdx.scenes.scene2d.ui.Widget with com.badlogic.gdx.scenes.scene2d.utils.Disableable with com.badlogic.gdx.scenes.scene2d.ui.Styleable[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle] {
  var text: java.lang.String = null.asInstanceOf[java.lang.String]
  var cursor: scala.Int = 0
  var selectionStart: scala.Int = 0
  var hasSelection: scala.Boolean = false
  var writeEnters: scala.Boolean = false
  final val layout: com.badlogic.gdx.graphics.g2d.GlyphLayout = new com.badlogic.gdx.graphics.g2d.GlyphLayout()
  final val glyphPositions: com.badlogic.gdx.utils.FloatArray = new com.badlogic.gdx.utils.FloatArray()
  var style: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle]
  private var messageText: java.lang.String = null.asInstanceOf[java.lang.String]
  var displayText: java.lang.CharSequence = null.asInstanceOf[java.lang.CharSequence]
  var clipboard: com.badlogic.gdx.utils.Clipboard = null.asInstanceOf[com.badlogic.gdx.utils.Clipboard]
  var inputListener: com.badlogic.gdx.scenes.scene2d.InputListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.InputListener]
  var listener: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener]
  var filter: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter]
  var keyboard: com.badlogic.gdx.scenes.scene2d.ui.TextField.OnscreenKeyboard = TextField.DEFAULT_ONSCREEN_KEYBOARD
  var focusTraversal: scala.Boolean = true
  var onlyFontChars: scala.Boolean = true
  var disabled: scala.Boolean = false
  private var textHAlign: scala.Int = com.badlogic.gdx.utils.Align.left
  private var selectionX: scala.Float = 0.0f
  private var selectionWidth: scala.Float = 0.0f
  var undoText: java.lang.String = ""
  var lastChangeTime: scala.Long = 0L
  var passwordMode: scala.Boolean = false
  private var passwordBuffer: java.lang.StringBuilder = null.asInstanceOf[java.lang.StringBuilder]
  private var passwordCharacter: scala.Char = TextField.BULLET
  var fontOffset: scala.Float = 0.0f
  var textHeight: scala.Float = 0.0f
  var textOffset: scala.Float = 0.0f
  var renderOffset: scala.Float = 0.0f
  var visibleTextStart: scala.Int = 0
  var visibleTextEnd: scala.Int = 0
  private var maxLength: scala.Int = 0
  private var autocompleteOptions: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
  private var keyboardType: com.badlogic.gdx.Input.OnscreenKeyboardType = com.badlogic.gdx.Input.OnscreenKeyboardType.Default
  private var preventAutoCorrection: scala.Boolean = false
  var focused: scala.Boolean = false
  var cursorOn: scala.Boolean = false
  var blinkTime: scala.Float = 0.32f
  final val blinkTask: com.badlogic.gdx.utils.Timer.Task = new com.badlogic.gdx.utils.Timer.Task()
  final val keyRepeatTask: KeyRepeatTask = new KeyRepeatTask()
  var programmaticChangeEvents: scala.Boolean = false
  def this(text: java.lang.String, style: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle) = {
    this()
    this.setStyle(style)
    this.clipboard = com.badlogic.gdx.Gdx.app.getClipboard()
    this.initialize()
    this.setText(text)
    this.setSize(this.getPrefWidth(), this.getPrefHeight())
  }
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin, styleName: java.lang.String) = {
    this(text, skin.get(styleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle]))
  }
  def this(text: java.lang.String, skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this(text, skin.get(classOf[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle]))
  }
  def initialize(): scala.Unit = {
    this.addListener({
      this.inputListener = this.createInputListener()
      this.inputListener
    })
  }
  def createInputListener(): com.badlogic.gdx.scenes.scene2d.InputListener = {
    return new TextFieldClickListener()
  }
  def letterUnderCursor(x$arg: scala.Float): scala.Int = {
    var x: scala.Float = x$arg
    x = x - (((this.textOffset + this.fontOffset) - this.style.font.getData().cursorX) - this.glyphPositions.get(this.visibleTextStart))
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
    if (background != null) {
      x = x - this.style.background.getLeftWidth()
    } else ()
    val n: scala.Int = this.glyphPositions.size
    val glyphPositions: scala.Array[scala.Float] = this.glyphPositions.items;
    { var i: scala.Int = 1; while (i < n) { {
      if (glyphPositions(i) > x) {
        if ((glyphPositions(i) - x) <= (x - glyphPositions(i - 1))) {
          return i
        } else ()
        return i - 1
      } else ()
    }; i = i + 1 } }
    return n - 1
  }
  def isWordCharacter(c: scala.Char): scala.Boolean = {
    return java.lang.Character.isLetterOrDigit(c)
  }
  def wordUnderCursor(at: scala.Int): scala.Array[scala.Int] = {
    val text: java.lang.String = this.text
    val start: scala.Int = at
    var right: scala.Int = text.length()
    var left: scala.Int = 0
    var index: scala.Int = start
    if (at >= text.length()) {
      left = text.length()
      right = 0
    } else {
      { ; while (index < right) { {
        if (!this.isWordCharacter(text.charAt(index))) {
          right = index
          /* break */ ()
        } else ()
      }; index = index + 1 } };
      { index = start - 1; while (index > (-1)) { {
        if (!this.isWordCharacter(text.charAt(index))) {
          left = index + 1
          /* break */ ()
        } else ()
      }; index = index - 1 } }
    }
    return scala.Array[scala.Int](left, right)
  }
  def wordUnderCursor(x: scala.Float): scala.Array[scala.Int] = {
    return this.wordUnderCursor(this.letterUnderCursor(x))
  }
  def withinMaxLength(size: scala.Int): scala.Boolean = {
    return (this.maxLength <= 0) || (size < this.maxLength)
  }
  def setMaxLength(maxLength: scala.Int): scala.Unit = {
    this.maxLength = maxLength
  }
  def getMaxLength(): scala.Int = {
    return this.maxLength
  }
  def setAutocompleteOptions(autocompleteOptions: scala.Array[java.lang.String]): scala.Unit = {
    this.autocompleteOptions = autocompleteOptions
  }
  def setKeyboardType(keyboardType: com.badlogic.gdx.Input.OnscreenKeyboardType): scala.Unit = {
    this.keyboardType = keyboardType
  }
  def setPreventAutoCorrection(preventAutoCorrection: scala.Boolean): scala.Unit = {
    this.preventAutoCorrection = preventAutoCorrection
  }
  def setOnlyFontChars(onlyFontChars: scala.Boolean): scala.Unit = {
    this.onlyFontChars = onlyFontChars
  }
  def setStyle(style: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle): scala.Unit = {
    if (style == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    this.style = style
    this.textHeight = style.font.getCapHeight() - (style.font.getDescent() * 2)
    if (this.text != null) {
      this.updateDisplayText()
    } else ()
    this.invalidateHierarchy()
  }
  def getStyle(): com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle = {
    return this.style
  }
  def calculateOffsets(): scala.Unit = {
    var visibleWidth: scala.Float = this.getWidth()
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
    if (background != null) {
      visibleWidth = visibleWidth - (background.getLeftWidth() + background.getRightWidth())
    } else ()
    val glyphCount: scala.Int = this.glyphPositions.size
    val glyphPositions: scala.Array[scala.Float] = this.glyphPositions.items
    this.cursor = com.badlogic.gdx.math.MathUtils.clamp(this.cursor, 0, glyphCount - 1)
    val distance: scala.Float = glyphPositions(java.lang.Math.max(0, this.cursor - 1)) + this.renderOffset
    if (distance <= 0) {
      this.renderOffset = this.renderOffset - distance
    } else {
      val index: scala.Int = java.lang.Math.min(glyphCount - 1, this.cursor + 1)
      val minX: scala.Float = glyphPositions(index) - visibleWidth
      if ((-this.renderOffset) < minX) {
        this.renderOffset = -minX
      } else ()
    }
    var maxOffset: scala.Float = 0
    val width: scala.Float = glyphPositions(glyphCount - 1);
    { var i: scala.Int = glyphCount - 2; while (i >= 0) { {
      val x: scala.Float = glyphPositions(i)
      if ((width - x) > visibleWidth) {
        /* break */ ()
      } else ()
      maxOffset = x
    }; i = i - 1 } }
    if ((-this.renderOffset) > maxOffset) {
      this.renderOffset = -maxOffset
    } else ()
    this.visibleTextStart = 0
    var startX: scala.Float = 0;
    { var i: scala.Int = 0; while (i < glyphCount) { {
      if (glyphPositions(i) >= (-this.renderOffset)) {
        this.visibleTextStart = i
        startX = glyphPositions(i)
        /* break */ ()
      } else ()
    }; i = i + 1 } }
    var `end`: scala.Int = this.visibleTextStart + 1
    val endX: scala.Float = visibleWidth - this.renderOffset;
    { val n: scala.Int = java.lang.Math.min(this.displayText.length(), glyphCount); while (`end` <= n) { {
      if (glyphPositions(`end`) > endX) {
        /* break */ ()
      } else ()
    }; `end` = `end` + 1 } }
    this.visibleTextEnd = java.lang.Math.max(0, `end` - 1)
    if ((this.textHAlign & com.badlogic.gdx.utils.Align.left) == 0) {
      this.textOffset = ((visibleWidth - glyphPositions(this.visibleTextEnd)) - this.fontOffset) + startX
      if ((this.textHAlign & com.badlogic.gdx.utils.Align.center) != 0) {
        this.textOffset = java.lang.Math.round(this.textOffset * 0.5f)
      } else ()
    } else {
      this.textOffset = startX + this.renderOffset
    }
    if (this.hasSelection) {
      val minIndex: scala.Int = java.lang.Math.min(this.cursor, this.selectionStart)
      val maxIndex: scala.Int = java.lang.Math.max(this.cursor, this.selectionStart)
      val minX: scala.Float = java.lang.Math.max(glyphPositions(minIndex) - glyphPositions(this.visibleTextStart), -this.textOffset)
      val maxX: scala.Float = java.lang.Math.min(glyphPositions(maxIndex) - glyphPositions(this.visibleTextStart), visibleWidth - this.textOffset)
      this.selectionX = minX
      this.selectionWidth = (maxX - minX) - this.style.font.getData().cursorX
    } else ()
  }
  def getBackgroundDrawable(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (this.disabled && (this.style.disabledBackground != null)) {
      return this.style.disabledBackground
    } else ()
    if ((this.style.focusedBackground != null) && this.hasKeyboardFocus()) {
      return this.style.focusedBackground
    } else ()
    return this.style.background
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    var focused: scala.Boolean = this.hasKeyboardFocus()
    if ((focused != this.focused) || (focused && (!this.blinkTask.isScheduled()))) {
      this.focused = focused
      this.blinkTask.cancel()
      this.cursorOn = focused
      if (focused) {
        com.badlogic.gdx.utils.Timer.schedule(this.blinkTask, this.blinkTime, this.blinkTime)
      } else {
        this.keyRepeatTask.cancel()
      }
    } else {
      if (!focused) {
        this.cursorOn = false
      } else ()
    }
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    val fontColor: com.badlogic.gdx.graphics.Color = if (this.disabled && (this.style.disabledFontColor != null)) this.style.disabledFontColor else if (focused && (this.style.focusedFontColor != null)) this.style.focusedFontColor else this.style.fontColor
    val selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.selection
    val cursorPatch: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.style.cursor
    val background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.getBackgroundDrawable()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    val x: scala.Float = this.getX()
    val y: scala.Float = this.getY()
    val width: scala.Float = this.getWidth()
    val height: scala.Float = this.getHeight()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    var bgLeftWidth: scala.Float = 0
    var bgRightWidth: scala.Float = 0
    if (background != null) {
      this.drawBackground(background, batch, x, y, width, height)
      bgLeftWidth = background.getLeftWidth()
      bgRightWidth = background.getRightWidth()
    } else ()
    val textY: scala.Float = this.getTextY(font, background)
    this.calculateOffsets()
    if ((focused && this.hasSelection) && (selection != null)) {
      this.drawSelection(selection, batch, font, x + bgLeftWidth, y + textY)
    } else ()
    val yOffset: scala.Float = if (font.isFlipped()) -this.textHeight else 0
    if (this.displayText.length() == 0) {
      if (((!focused) || this.disabled) && (this.messageText != null)) {
        val messageFont: com.badlogic.gdx.graphics.g2d.BitmapFont = if (this.style.messageFont != null) this.style.messageFont else font
        if (this.style.messageFontColor != null) {
          messageFont.setColor(this.style.messageFontColor.r, this.style.messageFontColor.g, this.style.messageFontColor.b, (this.style.messageFontColor.a * color.a) * parentAlpha)
        } else {
          messageFont.setColor(0.7f, 0.7f, 0.7f, color.a * parentAlpha)
        }
        this.drawMessageText(batch, messageFont, x + bgLeftWidth, (y + textY) + yOffset, (width - bgLeftWidth) - bgRightWidth)
      } else ()
    } else {
      val data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = font.getData()
      var markupEnabled: scala.Boolean = data.markupEnabled
      data.markupEnabled = false
      font.setColor(fontColor.r, fontColor.g, fontColor.b, (fontColor.a * color.a) * parentAlpha)
      this.drawText(batch, font, x + bgLeftWidth, (y + textY) + yOffset)
      data.markupEnabled = markupEnabled
    }
    if (((!this.disabled) && this.cursorOn) && (cursorPatch != null)) {
      this.drawCursor(cursorPatch, batch, font, x + bgLeftWidth, y + textY)
    } else ()
  }
  def getTextY(font: com.badlogic.gdx.graphics.g2d.BitmapFont, background: com.badlogic.gdx.scenes.scene2d.utils.Drawable): scala.Float = {
    val height: scala.Float = this.getHeight()
    var textY: scala.Float = (this.textHeight / 2) + font.getDescent()
    if (background != null) {
      val bottom: scala.Float = background.getBottomHeight()
      textY = (textY + (((height - background.getTopHeight()) - bottom) / 2)) + bottom
    } else {
      textY = textY + (height / 2)
    }
    if (font.usesIntegerPositions()) {
      textY = textY.asInstanceOf[scala.Int]
    } else ()
    return textY
  }
  def drawBackground(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    background.draw(batch, x, y, width, height)
  }
  def drawSelection(selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, x: scala.Float, y: scala.Float): scala.Unit = {
    selection.draw(batch, ((x + this.textOffset) + this.selectionX) + this.fontOffset, (y - this.textHeight) - font.getDescent(), this.selectionWidth, this.textHeight)
  }
  def drawText(batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, x: scala.Float, y: scala.Float): scala.Unit = {
    font.draw(batch, this.displayText, x + this.textOffset, y, this.visibleTextStart, this.visibleTextEnd, 0, com.badlogic.gdx.utils.Align.left, false)
  }
  def drawMessageText(batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, x: scala.Float, y: scala.Float, maxWidth: scala.Float): scala.Unit = {
    font.draw(batch, this.messageText, x, y, 0, this.messageText.length(), maxWidth, this.textHAlign, false, "...")
  }
  def drawCursor(cursorPatch: com.badlogic.gdx.scenes.scene2d.utils.Drawable, batch: com.badlogic.gdx.graphics.g2d.Batch, font: com.badlogic.gdx.graphics.g2d.BitmapFont, x: scala.Float, y: scala.Float): scala.Unit = {
    cursorPatch.draw(batch, ((((x + this.textOffset) + this.glyphPositions.get(this.cursor)) - this.glyphPositions.get(this.visibleTextStart)) + this.fontOffset) + font.getData().cursorX, (y - this.textHeight) - font.getDescent(), cursorPatch.getMinWidth(), this.textHeight)
  }
  def updateDisplayText(): scala.Unit = {
    val font: com.badlogic.gdx.graphics.g2d.BitmapFont = this.style.font
    val data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = font.getData()
    val text: java.lang.String = this.text
    val textLength: scala.Int = text.length()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder();
    { var i: scala.Int = 0; while (i < textLength) { {
      val c: scala.Char = text.charAt(i)
      buffer.append(if (data.hasGlyph(c)) c else ' ')
    }; i = i + 1 } }
    val newDisplayText: java.lang.String = buffer.toString()
    if (this.passwordMode && data.hasGlyph(this.passwordCharacter)) {
      if (this.passwordBuffer == null) {
        this.passwordBuffer = new java.lang.StringBuilder(newDisplayText.length())
      } else ()
      if (this.passwordBuffer.length() > textLength) {
        this.passwordBuffer.setLength(textLength)
      } else {
        { var i: scala.Int = this.passwordBuffer.length(); while (i < textLength) { {
          this.passwordBuffer.append(this.passwordCharacter)
        }; i = i + 1 } }
      }
      this.displayText = this.passwordBuffer
    } else {
      this.displayText = newDisplayText
    }
    var markupEnabled: scala.Boolean = data.markupEnabled
    data.markupEnabled = false
    this.layout.setText(font, this.displayText.toString().replace('\r', ' ').replace('\n', ' '))
    data.markupEnabled = markupEnabled
    this.glyphPositions.clear()
    var x: scala.Float = 0
    if (this.layout.runs.size > 0) {
      val run: com.badlogic.gdx.graphics.g2d.GlyphLayout.GlyphRun = this.layout.runs.first()
      val xAdvances: com.badlogic.gdx.utils.FloatArray = run.xAdvances
      this.fontOffset = xAdvances.first();
      { var i: scala.Int = 1; val n: scala.Int = xAdvances.size; while (i < n) { {
        this.glyphPositions.add(x)
        x = x + xAdvances.get(i)
      }; i = i + 1 } }
    } else {
      this.fontOffset = 0
    }
    this.glyphPositions.add(x)
    this.visibleTextStart = java.lang.Math.min(this.visibleTextStart, this.glyphPositions.size - 1)
    this.visibleTextEnd = com.badlogic.gdx.math.MathUtils.clamp(this.visibleTextEnd, this.visibleTextStart, this.glyphPositions.size - 1)
    if (this.selectionStart > newDisplayText.length()) {
      this.selectionStart = textLength
    } else ()
  }
  def copy(): scala.Unit = {
    if (this.hasSelection && (!this.passwordMode)) {
      this.clipboard.setContents(this.text.substring(java.lang.Math.min(this.cursor, this.selectionStart), java.lang.Math.max(this.cursor, this.selectionStart)))
    } else ()
  }
  def cut(): scala.Unit = {
    this.cut(this.programmaticChangeEvents)
  }
  def cut(fireChangeEvent: scala.Boolean): scala.Unit = {
    if (this.hasSelection && (!this.passwordMode)) {
      this.copy()
      this.cursor = this.delete(fireChangeEvent)
      this.updateDisplayText()
    } else ()
  }
  def paste(content$arg: java.lang.String, fireChangeEvent: scala.Boolean): scala.Unit = {
    var content: java.lang.String = content$arg
    if (content == null) {
      return
    } else ()
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder()
    var textLength: scala.Int = this.text.length()
    if (this.hasSelection) {
      textLength = textLength - java.lang.Math.abs(this.cursor - this.selectionStart)
    } else ()
    val data: com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData = this.style.font.getData();
    { var i: scala.Int = 0; val n: scala.Int = content.length(); while (i < n) { {
      if (!this.withinMaxLength(textLength + buffer.length())) {
        /* break */ ()
      } else ()
      val c: scala.Char = content.charAt(i)
      if (!(this.writeEnters && ((c == TextField.NEWLINE) || (c == TextField.CARRIAGE_RETURN)))) {
        if ((c == '\r') || (c == '\n')) {
          /* continue */ ()
        } else ()
        if (this.onlyFontChars && (!data.hasGlyph(c))) {
          /* continue */ ()
        } else ()
        if ((this.filter != null) && (!this.filter.acceptChar(this, c))) {
          /* continue */ ()
        } else ()
      } else ()
      buffer.append(c)
    }; i = i + 1 } }
    content = buffer.toString()
    if (this.hasSelection) {
      this.cursor = this.delete(fireChangeEvent)
    } else ()
    if (fireChangeEvent) {
      this.changeText(this.text, this.insert(this.cursor, content, this.text))
    } else {
      this.text = this.insert(this.cursor, content, this.text)
    }
    this.updateDisplayText()
    this.cursor = this.cursor + content.length()
  }
  def insert(position: scala.Int, text: java.lang.CharSequence, to: java.lang.String): java.lang.String = {
    if (to.length() == 0) {
      return text.toString()
    } else ()
    return (to.substring(0, position) + text) + to.substring(position, to.length())
  }
  def delete(fireChangeEvent: scala.Boolean): scala.Int = {
    val from: scala.Int = this.selectionStart
    val to: scala.Int = this.cursor
    val minIndex: scala.Int = java.lang.Math.min(from, to)
    val maxIndex: scala.Int = java.lang.Math.max(from, to)
    val newText: java.lang.String = (if (minIndex > 0) this.text.substring(0, minIndex) else "") + (if (maxIndex < this.text.length()) this.text.substring(maxIndex, this.text.length()) else "")
    if (fireChangeEvent) {
      this.changeText(this.text, newText)
    } else {
      this.text = newText
    }
    this.clearSelection()
    return minIndex
  }
  def next(up: scala.Boolean): TextField = {
    {
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = this.getStage()
      if (stage == null) {
        return null
      } else ()
      var current: TextField = this
      val currentCoords: com.badlogic.gdx.math.Vector2 = current.getParent().localToStageCoordinates(TextField.tmp2.set(current.getX(), current.getY()))
      val bestCoords: com.badlogic.gdx.math.Vector2 = TextField.tmp1
      while (true) {
        var textField: TextField = current.findNextTextField(stage.getActors(), null, bestCoords, currentCoords, up)
        if (textField == null) {
          if (up) {
            currentCoords.set(-java.lang.Float.MAX_VALUE, -java.lang.Float.MAX_VALUE)
          } else {
            currentCoords.set(java.lang.Float.MAX_VALUE, java.lang.Float.MAX_VALUE)
          }
          textField = current.findNextTextField(stage.getActors(), null, bestCoords, currentCoords, up)
        } else ()
        if (textField == null) {
          return null
        } else ()
        if (stage.setKeyboardFocus(textField)) {
          textField.selectAll()
          return textField
        } else ()
        current = textField
        currentCoords.set(bestCoords)
      }
    }
    throw new java.lang.RuntimeException("unreachable")
  }
  private def findNextTextField(actors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Actor], best$arg: TextField, bestCoords: com.badlogic.gdx.math.Vector2, currentCoords: com.badlogic.gdx.math.Vector2, up: scala.Boolean): TextField = {
    var best: TextField = best$arg;
    { var i: scala.Int = 0; val n: scala.Int = actors.size; while (i < n) { {
      val actor: com.badlogic.gdx.scenes.scene2d.Actor = actors.get(i)
      if (actor.isInstanceOf[TextField]) {
        if (actor == this) {
          /* continue */ ()
        } else ()
        val textField: TextField = actor.asInstanceOf[TextField]
        if ((textField.isDisabled() || (!textField.focusTraversal)) || (!textField.ascendantsVisible())) {
          /* continue */ ()
        } else ()
        val actorCoords: com.badlogic.gdx.math.Vector2 = actor.getParent().localToStageCoordinates(TextField.tmp3.set(actor.getX(), actor.getY()))
        val below: scala.Boolean = (actorCoords.y != currentCoords.y) && ((actorCoords.y < currentCoords.y) ^ up)
        val right: scala.Boolean = (actorCoords.y == currentCoords.y) && ((actorCoords.x > currentCoords.x) ^ up)
        if ((!below) && (!right)) {
          /* continue */ ()
        } else ()
        var better: scala.Boolean = (best == null) || ((actorCoords.y != bestCoords.y) && ((actorCoords.y > bestCoords.y) ^ up))
        if (!better) {
          better = (actorCoords.y == bestCoords.y) && ((actorCoords.x < bestCoords.x) ^ up)
        } else ()
        if (better) {
          best = actor.asInstanceOf[TextField]
          bestCoords.set(actorCoords)
        } else ()
      } else {
        if (actor.isInstanceOf[com.badlogic.gdx.scenes.scene2d.Group]) {
          best = this.findNextTextField(actor.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Group].getChildren(), best, bestCoords, currentCoords, up)
        } else ()
      }
    }; i = i + 1 } }
    return best
  }
  def getDefaultInputListener(): com.badlogic.gdx.scenes.scene2d.InputListener = {
    return this.inputListener
  }
  def setTextFieldListener(listener: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldListener): scala.Unit = {
    this.listener = listener
  }
  def setTextFieldFilter(filter: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter): scala.Unit = {
    this.filter = filter
  }
  def getTextFieldFilter(): com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter = {
    return this.filter
  }
  def setFocusTraversal(focusTraversal: scala.Boolean): scala.Unit = {
    this.focusTraversal = focusTraversal
  }
  def getFocusTraversal(): scala.Boolean = {
    return this.focusTraversal
  }
  def getMessageText(): java.lang.String = {
    return this.messageText
  }
  def setMessageText(messageText: java.lang.String): scala.Unit = {
    this.messageText = messageText
  }
  def appendText(str$arg: java.lang.String): scala.Unit = {
    var str: java.lang.String = str$arg
    if (str == null) {
      str = ""
    } else ()
    this.clearSelection()
    this.cursor = this.text.length()
    this.paste(str, this.programmaticChangeEvents)
  }
  def setText(str$arg: java.lang.String): scala.Unit = {
    var str: java.lang.String = str$arg
    if (str == null) {
      str = ""
    } else ()
    if (str.equals(this.text)) {
      return
    } else ()
    this.clearSelection()
    val oldText: java.lang.String = this.text
    this.text = ""
    this.paste(str, false)
    if (this.programmaticChangeEvents) {
      this.changeText(oldText, this.text)
    } else ()
    this.cursor = 0
  }
  def getText(): java.lang.String = {
    return this.text
  }
  def changeText(oldText: java.lang.String, newText: java.lang.String): scala.Boolean = {
    if (newText.equals(oldText)) {
      return false
    } else ()
    this.text = newText
    val changeEvent: com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent = com.badlogic.gdx.scenes.scene2d.Actor.POOLS.obtain(classOf[com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent])
    val cancelled: scala.Boolean = this.fire(changeEvent)
    if (cancelled) {
      this.text = oldText
    } else ()
    com.badlogic.gdx.scenes.scene2d.Actor.POOLS.free(changeEvent)
    return !cancelled
  }
  def setProgrammaticChangeEvents(programmaticChangeEvents: scala.Boolean): scala.Unit = {
    this.programmaticChangeEvents = programmaticChangeEvents
  }
  def getProgrammaticChangeEvents(): scala.Boolean = {
    return this.programmaticChangeEvents
  }
  def getSelectionStart(): scala.Int = {
    return this.selectionStart
  }
  def getSelection(): java.lang.String = {
    return if (this.hasSelection) this.text.substring(java.lang.Math.min(this.selectionStart, this.cursor), java.lang.Math.max(this.selectionStart, this.cursor)) else ""
  }
  def setSelection(selectionStart$arg: scala.Int, selectionEnd$arg: scala.Int): scala.Unit = {
    var selectionStart: scala.Int = selectionStart$arg
    var selectionEnd: scala.Int = selectionEnd$arg
    if (selectionStart < 0) {
      throw new java.lang.IllegalArgumentException("selectionStart must be >= 0")
    } else ()
    if (selectionEnd < 0) {
      throw new java.lang.IllegalArgumentException("selectionEnd must be >= 0")
    } else ()
    selectionStart = java.lang.Math.min(this.text.length(), selectionStart)
    selectionEnd = java.lang.Math.min(this.text.length(), selectionEnd)
    if (selectionEnd == selectionStart) {
      this.clearSelection()
      return
    } else ()
    if (selectionEnd < selectionStart) {
      val temp: scala.Int = selectionEnd
      selectionEnd = selectionStart
      selectionStart = temp
    } else ()
    this.hasSelection = true
    this.selectionStart = selectionStart
    this.cursor = selectionEnd
  }
  def selectAll(): scala.Unit = {
    this.setSelection(0, this.text.length())
  }
  def clearSelection(): scala.Unit = {
    this.hasSelection = false
  }
  def setCursorPosition(cursorPosition: scala.Int): scala.Unit = {
    if (cursorPosition < 0) {
      throw new java.lang.IllegalArgumentException("cursorPosition must be >= 0")
    } else ()
    this.clearSelection()
    this.cursor = java.lang.Math.min(cursorPosition, this.text.length())
  }
  def getCursorPosition(): scala.Int = {
    return this.cursor
  }
  def getOnscreenKeyboard(): com.badlogic.gdx.scenes.scene2d.ui.TextField.OnscreenKeyboard = {
    return this.keyboard
  }
  def setOnscreenKeyboard(keyboard: com.badlogic.gdx.scenes.scene2d.ui.TextField.OnscreenKeyboard): scala.Unit = {
    this.keyboard = keyboard
  }
  def setClipboard(clipboard: com.badlogic.gdx.utils.Clipboard): scala.Unit = {
    this.clipboard = clipboard
  }
  def getPrefWidth(): scala.Float = {
    return 150
  }
  def getPrefHeight(): scala.Float = {
    var topAndBottom: scala.Float = 0
    var minHeight: scala.Float = 0
    if (this.style.background != null) {
      topAndBottom = java.lang.Math.max(topAndBottom, this.style.background.getBottomHeight() + this.style.background.getTopHeight())
      minHeight = java.lang.Math.max(minHeight, this.style.background.getMinHeight())
    } else ()
    if (this.style.focusedBackground != null) {
      topAndBottom = java.lang.Math.max(topAndBottom, this.style.focusedBackground.getBottomHeight() + this.style.focusedBackground.getTopHeight())
      minHeight = java.lang.Math.max(minHeight, this.style.focusedBackground.getMinHeight())
    } else ()
    if (this.style.disabledBackground != null) {
      topAndBottom = java.lang.Math.max(topAndBottom, this.style.disabledBackground.getBottomHeight() + this.style.disabledBackground.getTopHeight())
      minHeight = java.lang.Math.max(minHeight, this.style.disabledBackground.getMinHeight())
    } else ()
    return java.lang.Math.max(topAndBottom + this.textHeight, minHeight)
  }
  def setAlignment(alignment: scala.Int): scala.Unit = {
    this.textHAlign = alignment
  }
  def getAlignment(): scala.Int = {
    return this.textHAlign
  }
  def setPasswordMode(passwordMode: scala.Boolean): scala.Unit = {
    this.passwordMode = passwordMode
    this.updateDisplayText()
  }
  def isPasswordMode(): scala.Boolean = {
    return this.passwordMode
  }
  def setPasswordCharacter(passwordCharacter: scala.Char): scala.Unit = {
    this.passwordCharacter = passwordCharacter
    if (this.passwordMode) {
      this.updateDisplayText()
    } else ()
  }
  def setBlinkTime(blinkTime: scala.Float): scala.Unit = {
    this.blinkTime = blinkTime
  }
  def setDisabled(disabled: scala.Boolean): scala.Unit = {
    this.disabled = disabled
  }
  def isDisabled(): scala.Boolean = {
    return this.disabled
  }
  def moveCursor(forward: scala.Boolean, jump: scala.Boolean): scala.Unit = {
    val limit: scala.Int = if (forward) this.text.length() else 0
    val charOffset: scala.Int = if (forward) 0 else -1
    while ((if (forward) { this.cursor += 1; this.cursor } < limit else { this.cursor -= 1; this.cursor } > limit) && jump) {
      if (!this.continueCursor(this.cursor, charOffset)) {
        /* break */ ()
      } else ()
    }
  }
  def continueCursor(index: scala.Int, offset: scala.Int): scala.Boolean = {
    val c: scala.Char = this.text.charAt(index + offset)
    return this.isWordCharacter(c)
  }
  class KeyRepeatTask extends com.badlogic.gdx.utils.Timer.Task {
    var keycode: scala.Int = 0
    def run(): scala.Unit = {
      if (getStage() == null) {
        this.cancel()
        return
      } else ()
      inputListener.keyDown(null, this.keycode)
    }
  }
  class TextFieldClickListener extends com.badlogic.gdx.scenes.scene2d.utils.ClickListener {
    def clicked(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float): scala.Unit = {
      val count: scala.Int = this.getTapCount() % 4
      if (count == 0) {
        clearSelection()
      } else ()
      if (count == 2) {
        val array: scala.Array[scala.Int] = wordUnderCursor(x)
        setSelection(array(0), array(1))
      } else ()
      if (count == 3) {
        selectAll()
      } else ()
    }
    def touchDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Boolean = {
      if (!super.touchDown(event, x, y, pointer, button)) {
        return false
      } else ()
      if ((pointer == 0) && (button != 0)) {
        return false
      } else ()
      if (disabled) {
        return true
      } else ()
      this.setCursorPosition(x, y)
      selectionStart = cursor
      val stage: com.badlogic.gdx.scenes.scene2d.Stage = getStage()
      if (stage != null) {
        stage.setKeyboardFocus(this)
      } else ()
      keyboard.show(this)
      hasSelection = true
      return true
    }
    def touchDragged(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int): scala.Unit = {
      super.touchDragged(event, x, y, pointer)
      this.setCursorPosition(x, y)
    }
    def touchUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, x: scala.Float, y: scala.Float, pointer: scala.Int, button: scala.Int): scala.Unit = {
      if (selectionStart == cursor) {
        hasSelection = false
      } else ()
      super.touchUp(event, x, y, pointer, button)
    }
    def setCursorPosition(x: scala.Float, y: scala.Float): scala.Unit = {
      cursor = letterUnderCursor(x)
      cursorOn = focused
      blinkTask.cancel()
      if (focused) {
        com.badlogic.gdx.utils.Timer.schedule(blinkTask, blinkTime, blinkTime)
      } else ()
    }
    def goHome(jump: scala.Boolean): scala.Unit = {
      cursor = 0
    }
    def goEnd(jump: scala.Boolean): scala.Unit = {
      cursor = text.length()
    }
    def keyDown(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
      if (disabled) {
        return false
      } else ()
      cursorOn = focused
      blinkTask.cancel()
      if (focused) {
        com.badlogic.gdx.utils.Timer.schedule(blinkTask, blinkTime, blinkTime)
      } else ()
      if (!hasKeyboardFocus()) {
        return false
      } else ()
      var repeat: scala.Boolean = false
      val ctrl: scala.Boolean = com.badlogic.gdx.scenes.scene2d.utils.UIUtils.ctrl()
      val jump: scala.Boolean = ctrl && (!passwordMode)
      var handled: scala.Boolean = true
      if (ctrl) {
        keycode match {
          case com.badlogic.gdx.Input.Keys.V => {
            paste(clipboard.getContents(), true)
            repeat = true
          }
          case com.badlogic.gdx.Input.Keys.C | com.badlogic.gdx.Input.Keys.INSERT => {
            copy()
            return true
          }
          case com.badlogic.gdx.Input.Keys.X => {
            cut(true)
            return true
          }
          case com.badlogic.gdx.Input.Keys.A => {
            selectAll()
            return true
          }
          case com.badlogic.gdx.Input.Keys.Z => {
            val oldText: java.lang.String = text
            setText(undoText)
            undoText = oldText
            updateDisplayText()
            return true
          }
          case _ => {
            handled = false
          }
        }
      } else ()
      if (com.badlogic.gdx.scenes.scene2d.utils.UIUtils.shift()) {
        keycode match {
          case com.badlogic.gdx.Input.Keys.INSERT => {
            paste(clipboard.getContents(), true)
          }
          case com.badlogic.gdx.Input.Keys.FORWARD_DEL => {
            cut(true)
          }
        };
        {
          val temp: scala.Int = cursor;
          {
            keycode match {
              case com.badlogic.gdx.Input.Keys.LEFT => {
                moveCursor(false, jump)
                repeat = true
                handled = true
              }
              case com.badlogic.gdx.Input.Keys.RIGHT => {
                moveCursor(true, jump)
                repeat = true
                handled = true
              }
              case com.badlogic.gdx.Input.Keys.HOME => {
                this.goHome(jump)
                handled = true
              }
              case com.badlogic.gdx.Input.Keys.END => {
                this.goEnd(jump)
                handled = true
              }
            }
            /* break */ ()
          }
          if (!hasSelection) {
            selectionStart = temp
            hasSelection = true
          } else ()
        }
      } else {
        keycode match {
          case com.badlogic.gdx.Input.Keys.LEFT => {
            moveCursor(false, jump)
            clearSelection()
            repeat = true
            handled = true
          }
          case com.badlogic.gdx.Input.Keys.RIGHT => {
            moveCursor(true, jump)
            clearSelection()
            repeat = true
            handled = true
          }
          case com.badlogic.gdx.Input.Keys.HOME => {
            this.goHome(jump)
            clearSelection()
            handled = true
          }
          case com.badlogic.gdx.Input.Keys.END => {
            this.goEnd(jump)
            clearSelection()
            handled = true
          }
        }
      }
      cursor = com.badlogic.gdx.math.MathUtils.clamp(cursor, 0, text.length())
      if (repeat) {
        this.scheduleKeyRepeatTask(keycode)
      } else ()
      return handled
    }
    def scheduleKeyRepeatTask(keycode: scala.Int): scala.Unit = {
      if ((!keyRepeatTask.isScheduled()) || (keyRepeatTask.keycode != keycode)) {
        keyRepeatTask.keycode = keycode
        keyRepeatTask.cancel()
        com.badlogic.gdx.utils.Timer.schedule(keyRepeatTask, TextField.keyRepeatInitialTime, TextField.keyRepeatTime)
      } else ()
    }
    def keyUp(event: com.badlogic.gdx.scenes.scene2d.InputEvent, keycode: scala.Int): scala.Boolean = {
      if (disabled) {
        return false
      } else ()
      keyRepeatTask.cancel()
      return true
    }
    def checkFocusTraversal(character: scala.Char): scala.Boolean = {
      return focusTraversal && ((character == TextField.TAB) || (((character == TextField.CARRIAGE_RETURN) || (character == TextField.NEWLINE)) && (com.badlogic.gdx.scenes.scene2d.utils.UIUtils.isAndroid || com.badlogic.gdx.scenes.scene2d.utils.UIUtils.isIos)))
    }
    def keyTyped(event: com.badlogic.gdx.scenes.scene2d.InputEvent, character: scala.Char): scala.Boolean = {
      if (disabled) {
        return false
      } else ()
      character match {
        case TextField.BACKSPACE | TextField.TAB | TextField.NEWLINE | TextField.CARRIAGE_RETURN => {
          ()
        }
        case _ => {
          if (character < 32) {
            return false
          } else ()
        }
      }
      if (!hasKeyboardFocus()) {
        return false
      } else ()
      if (com.badlogic.gdx.scenes.scene2d.utils.UIUtils.isMac && com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SYM)) {
        return true
      } else ()
      if (this.checkFocusTraversal(character)) {
        val transferred: TextField = next(com.badlogic.gdx.scenes.scene2d.utils.UIUtils.shift())
        if (transferred == null) {
          keyboard.close()
        } else ()
      } else {
        val enter: scala.Boolean = (character == TextField.CARRIAGE_RETURN) || (character == TextField.NEWLINE)
        val delete: scala.Boolean = character == TextField.DELETE
        val backspace: scala.Boolean = character == TextField.BACKSPACE
        val add: scala.Boolean = if (enter) writeEnters else (!onlyFontChars) || style.font.getData().hasGlyph(character)
        val remove: scala.Boolean = backspace || delete
        if (add || remove) {
          val oldText: java.lang.String = text
          val oldCursor: scala.Int = cursor
          if (remove) {
            if (hasSelection) {
              cursor = delete(false)
            } else {
              if (backspace && (cursor > 0)) {
                text = text.substring(0, cursor - 1) + text.substring({ cursor -= 1; cursor })
                renderOffset = 0
              } else ()
              if (delete && (cursor < text.length())) {
                text = text.substring(0, cursor) + text.substring(cursor + 1)
              } else ()
            }
          } else ()
          if (add && (!remove)) {
            if (((!enter) && (filter != null)) && (!filter.acceptChar(this, character))) {
              return true
            } else ()
            if (!withinMaxLength(text.length() - (if (hasSelection) java.lang.Math.abs(cursor - selectionStart) else 0))) {
              return true
            } else ()
            if (hasSelection) {
              cursor = delete(false)
            } else ()
            val insertion: java.lang.String = if (enter) "\n" else java.lang.String.valueOf(character)
            text = insert({ cursor += 1; cursor }, insertion, text)
          } else ()
          val tempUndoText: java.lang.String = undoText
          if (changeText(oldText, text)) {
            val time: scala.Long = java.lang.System.currentTimeMillis()
            if ((time - 750) > lastChangeTime) {
              undoText = oldText
            } else ()
            lastChangeTime = time
            updateDisplayText()
          } else {
            if (!text.equals(oldText)) {
              cursor = oldCursor
            } else ()
          }
        } else ()
      }
      if (listener != null) {
        listener.keyTyped(this, character)
      } else ()
      return true
    }
  }
  object TextFieldClickListener {
    export com.badlogic.gdx.scenes.scene2d.utils.ClickListener.*
  }
}
object TextField {
  export com.badlogic.gdx.scenes.scene2d.ui.Widget.*
  final val BACKSPACE: scala.Char = 8.asInstanceOf[scala.Char]
  final val CARRIAGE_RETURN: scala.Char = '\r'
  final val NEWLINE: scala.Char = '\n'
  final val TAB: scala.Char = '\t'
  final val DELETE: scala.Char = 127.asInstanceOf[scala.Char]
  final val BULLET: scala.Char = 149.asInstanceOf[scala.Char]
  var DEFAULT_ONSCREEN_KEYBOARD: com.badlogic.gdx.scenes.scene2d.ui.TextField.OnscreenKeyboard = new com.badlogic.gdx.scenes.scene2d.ui.TextField.DefaultOnscreenKeyboard()
  private final val tmp1: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val tmp2: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  private final val tmp3: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var keyRepeatInitialTime: scala.Float = 0.4f
  var keyRepeatTime: scala.Float = 0.1f
  trait TextFieldListener {
    def keyTyped(textField: TextField, c: scala.Char): scala.Unit
  }
  trait TextFieldFilter {
    def acceptChar(textField: TextField, c: scala.Char): scala.Boolean
  }
  object TextFieldFilter {
    class DigitsOnlyFilter extends com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter {
      def acceptChar(textField: TextField, c: scala.Char): scala.Boolean = {
        return java.lang.Character.isDigit(c)
      }
    }
    object DigitsOnlyFilter {
      export com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldFilter.*
    }
  }
  trait OnscreenKeyboard {
    def show(textField: TextField): scala.Unit
    def close(): scala.Unit
  }
  class DefaultOnscreenKeyboard extends com.badlogic.gdx.scenes.scene2d.ui.TextField.OnscreenKeyboard {
    def show(textField: TextField): scala.Unit = {
      com.badlogic.gdx.Gdx.input.setOnscreenKeyboardVisible(true)
    }
    def close(): scala.Unit = {
      com.badlogic.gdx.Gdx.input.setOnscreenKeyboardVisible(false)
    }
  }
  class NativeOnscreenKeyboard extends com.badlogic.gdx.scenes.scene2d.ui.TextField.OnscreenKeyboard {
    def close(): scala.Unit = {
      com.badlogic.gdx.Gdx.input.closeTextInputField(false)
    }
    def show(textField: TextField): scala.Unit = {
      if (com.badlogic.gdx.Gdx.input.isTextInputFieldOpened()) {
        com.badlogic.gdx.Gdx.input.closeTextInputField(false, (confirmativeAction: scala.Boolean) => {
          this.openNativeInputField(textField)
          return true
        })
        return
      } else ()
      this.openNativeInputField(textField)
    }
    private def openNativeInputField(textField: TextField): scala.Unit = {
      val configuration: com.badlogic.gdx.input.NativeInputConfiguration = new com.badlogic.gdx.input.NativeInputConfiguration()
      val resolvedType: com.badlogic.gdx.Input.OnscreenKeyboardType = if (textField.passwordMode && (textField.keyboardType == com.badlogic.gdx.Input.OnscreenKeyboardType.Default)) com.badlogic.gdx.Input.OnscreenKeyboardType.Password else textField.keyboardType
      configuration.setType(resolvedType).setMaskInput(textField.passwordMode).setShowUnmaskButton(textField.passwordMode).setMaxLength(if (textField.maxLength <= 0) -1 else textField.maxLength).setMultiLine(textField.writeEnters).setPreventCorrection(textField.preventAutoCorrection || (resolvedType == com.badlogic.gdx.Input.OnscreenKeyboardType.Password)).setPlaceholder(if (textField.messageText == null) "" else textField.messageText).setAutoComplete(textField.autocompleteOptions)
      if (textField.filter != null) {
        configuration.setValidator((toCheck: java.lang.String) => {
          { var i: scala.Int = 0; while (i < toCheck.length()) { {
            if (!textField.filter.acceptChar(textField, toCheck.charAt(i))) {
              return false
            } else ()
          }; i = i + 1 } }
          return true
        })
      } else ()
      configuration.setCloseCallback((confirmativeAction: scala.Boolean) => {
        if (confirmativeAction) {
          val focused: TextField = textField.next(false)
          if (focused != null) {
            focused.getOnscreenKeyboard().show(focused)
            return true
          } else ()
        } else ()
        return false
      })
      configuration.setTextInputWrapper(new com.badlogic.gdx.input.TextInputWrapper())
      com.badlogic.gdx.Gdx.input.openTextInputField(configuration)
    }
  }
  class TextFieldStyle {
    var font: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
    var fontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var focusedFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var disabledFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var background: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var focusedBackground: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var disabledBackground: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var cursor: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    var messageFont: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
    var messageFontColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    def this(font: com.badlogic.gdx.graphics.g2d.BitmapFont, fontColor: com.badlogic.gdx.graphics.Color, cursor: com.badlogic.gdx.scenes.scene2d.utils.Drawable, selection: com.badlogic.gdx.scenes.scene2d.utils.Drawable, background: com.badlogic.gdx.scenes.scene2d.utils.Drawable) = {
      this()
      this.font = font
      this.fontColor = fontColor
      this.cursor = cursor
      this.selection = selection
      this.background = background
    }
    def this(style: com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle) = {
      this()
      this.font = style.font
      if (style.fontColor != null) {
        this.fontColor = new com.badlogic.gdx.graphics.Color(style.fontColor)
      } else ()
      if (style.focusedFontColor != null) {
        this.focusedFontColor = new com.badlogic.gdx.graphics.Color(style.focusedFontColor)
      } else ()
      if (style.disabledFontColor != null) {
        this.disabledFontColor = new com.badlogic.gdx.graphics.Color(style.disabledFontColor)
      } else ()
      this.background = style.background
      this.focusedBackground = style.focusedBackground
      this.disabledBackground = style.disabledBackground
      this.cursor = style.cursor
      this.selection = style.selection
      this.messageFont = style.messageFont
      if (style.messageFontColor != null) {
        this.messageFontColor = new com.badlogic.gdx.graphics.Color(style.messageFontColor)
      } else ()
    }
  }
}