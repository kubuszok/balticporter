package com.badlogic.gdx.scenes.scene2d.ui

class Table extends com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup {
  private var columns: scala.Int = 0
  private var rows: scala.Int = 0
  private var implicitEndRow: scala.Boolean = false
  private final val cells: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]] = new com.badlogic.gdx.utils.Array(4).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]]
  private var cellDefaults: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  final val columnDefaults$field: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]] = new com.badlogic.gdx.utils.Array(2).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]]
  private var rowDefaults: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  private var sizeInvalid: scala.Boolean = true
  private var columnMinWidth: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var rowMinHeight: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var columnPrefWidth: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var rowPrefHeight: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var tableMinWidth: scala.Float = 0.0f
  private var tableMinHeight: scala.Float = 0.0f
  private var tablePrefWidth: scala.Float = 0.0f
  private var tablePrefHeight: scala.Float = 0.0f
  private var columnWidth: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var rowHeight: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var expandWidth: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var expandHeight: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var padTop$field: com.badlogic.gdx.scenes.scene2d.ui.Value = Table.backgroundTop
  var padLeft$field: com.badlogic.gdx.scenes.scene2d.ui.Value = Table.backgroundLeft
  var padBottom$field: com.badlogic.gdx.scenes.scene2d.ui.Value = Table.backgroundBottom
  var padRight$field: com.badlogic.gdx.scenes.scene2d.ui.Value = Table.backgroundRight
  var align$field: scala.Int = com.badlogic.gdx.utils.Align.center
  var debug$field: com.badlogic.gdx.scenes.scene2d.ui.Table.Debug = com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none
  var debugRects: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect]]
  var background$field: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
  var clip$field: scala.Boolean = false
  private var skin: com.badlogic.gdx.scenes.scene2d.ui.Skin = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Skin]
  var round: scala.Boolean = true
  def this(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin) = {
    this()
    this.skin = skin
    this.cellDefaults = this.obtainCell().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
    this.setTransform(false)
    this.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.childrenOnly)
  }
  private def obtainCell(): com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = {
    val cell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = Table.cellPool.obtain().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
    cell.setTable(this)
    return cell.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  }
  def draw(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float): scala.Unit = {
    this.validate()
    if (this.isTransform()) {
      this.applyTransform(batch, this.computeTransform())
      this.drawBackground(batch, parentAlpha, 0, 0)
      if (this.clip$field) {
        batch.flush()
        val padLeft: scala.Float = this.padLeft$field.get(this)
        val padBottom: scala.Float = this.padBottom$field.get(this)
        if (this.clipBegin(padLeft, padBottom, (this.getWidth() - padLeft) - this.padRight$field.get(this), (this.getHeight() - padBottom) - this.padTop$field.get(this))) {
          this.drawChildren(batch, parentAlpha)
          batch.flush()
          this.clipEnd()
        } else ()
      } else {
        this.drawChildren(batch, parentAlpha)
      }
      this.resetTransform(batch)
    } else {
      this.drawBackground(batch, parentAlpha, this.getX(), this.getY())
      super.draw(batch, parentAlpha)
    }
  }
  def drawBackground(batch: com.badlogic.gdx.graphics.g2d.Batch, parentAlpha: scala.Float, x: scala.Float, y: scala.Float): scala.Unit = {
    if (this.background$field == null) {
      return
    } else ()
    val color: com.badlogic.gdx.graphics.Color = this.getColor()
    batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
    this.background$field.draw(batch, x, y, this.getWidth(), this.getHeight())
  }
  def setBackground(drawableName: java.lang.String): scala.Unit = {
    if (this.skin == null) {
      throw new java.lang.IllegalStateException("Table must have a skin set to use this method.")
    } else ()
    this.setBackground(this.skin.getDrawable(drawableName))
  }
  def setBackground(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable): scala.Unit = {
    if (this.background$field == background) {
      return
    } else ()
    val padTopOld: scala.Float = this.getPadTop()
    val padLeftOld: scala.Float = this.getPadLeft()
    val padBottomOld: scala.Float = this.getPadBottom()
    val padRightOld: scala.Float = this.getPadRight()
    this.background$field = background
    val padTopNew: scala.Float = this.getPadTop()
    val padLeftNew: scala.Float = this.getPadLeft()
    val padBottomNew: scala.Float = this.getPadBottom()
    val padRightNew: scala.Float = this.getPadRight()
    if (((padTopOld + padBottomOld) != (padTopNew + padBottomNew)) || ((padLeftOld + padRightOld) != (padLeftNew + padRightNew))) {
      this.invalidateHierarchy()
    } else {
      if ((((padTopOld != padTopNew) || (padLeftOld != padLeftNew)) || (padBottomOld != padBottomNew)) || (padRightOld != padRightNew)) {
        this.invalidate()
      } else ()
    }
  }
  def background(background: com.badlogic.gdx.scenes.scene2d.utils.Drawable): Table = {
    this.setBackground(background)
    return this
  }
  def background(drawableName: java.lang.String): Table = {
    this.setBackground(drawableName)
    return this
  }
  def getBackground(): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    return this.background$field
  }
  def hit(x: scala.Float, y: scala.Float, touchable: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    if (this.clip$field) {
      if (touchable && (this.getTouchable() == com.badlogic.gdx.scenes.scene2d.Touchable.disabled)) {
        return null
      } else ()
      if ((((x < 0) || (x >= this.getWidth())) || (y < 0)) || (y >= this.getHeight())) {
        return null
      } else ()
    } else ()
    return super.hit(x, y, touchable)
  }
  def clip(): Table = {
    this.setClip(true)
    return this
  }
  def clip(enabled: scala.Boolean): Table = {
    this.setClip(enabled)
    return this
  }
  def setClip(enabled: scala.Boolean): scala.Unit = {
    this.clip$field = enabled
    this.setTransform(enabled)
    this.invalidate()
  }
  def getClip(): scala.Boolean = {
    return this.clip$field
  }
  def invalidate(): scala.Unit = {
    this.sizeInvalid = true
    super.invalidate()
  }
  def add[T <: com.badlogic.gdx.scenes.scene2d.Actor](actor: T): com.badlogic.gdx.scenes.scene2d.ui.Cell[T] = {
    val cell: com.badlogic.gdx.scenes.scene2d.ui.Cell[T] = this.obtainCell().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]]
    cell.actor = actor
    if (this.implicitEndRow) {
      this.implicitEndRow = false
      this.rows = this.rows - 1
      this.cells.peek().endRow = false
    } else ()
    val cellCount: scala.Int = this.cells.size
    if (cellCount > 0) {
      val lastCell: com.badlogic.gdx.scenes.scene2d.ui.Cell[T] = this.cells.peek().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]]
      if (!lastCell.endRow) {
        cell.column = lastCell.column + lastCell.colspan$field
        cell.row$field = lastCell.row$field
      } else {
        cell.column = 0
        cell.row$field = lastCell.row$field + 1
      }
      if (cell.row$field > 0) {
        val cells: scala.Array[java.lang.Object] = this.cells.items.asInstanceOf[scala.Array[java.lang.Object]];
        { var i: scala.Int = cellCount - 1; while (i >= 0) { {
          val other: com.badlogic.gdx.scenes.scene2d.ui.Cell[T] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]];
          { var column: scala.Int = other.column; val nn: scala.Int = column + other.colspan$field; while (column < nn) { {
            if (column == cell.column) {
              cell.cellAboveIndex = i
              /* break */ ()
            } else ()
          }; column = column + 1 } }
        }; i = i - 1 } }
      } else ()
    } else {
      cell.column = 0
      cell.row$field = 0
    }
    this.cells.add(cell)
    cell.set(this.cellDefaults.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]])
    if (cell.column < this.columnDefaults$field.size) {
      cell.merge(this.columnDefaults$field.get(cell.column).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]])
    } else ()
    cell.merge(this.rowDefaults.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]])
    if (actor != null) {
      this.addActor(actor)
    } else ()
    return cell
  }
  def add(actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor]): Table = {
    { var i: scala.Int = 0; val n: scala.Int = actors.length; while (i < n) { {
      this.add(actors(i))
    }; i = i + 1 } }
    return this
  }
  def add(text: java.lang.CharSequence): com.badlogic.gdx.scenes.scene2d.ui.Cell[com.badlogic.gdx.scenes.scene2d.ui.Label] = {
    if (this.skin == null) {
      throw new java.lang.IllegalStateException("Table must have a skin set to use this method.")
    } else ()
    return this.add(new com.badlogic.gdx.scenes.scene2d.ui.Label(text, this.skin))
  }
  def add(text: java.lang.CharSequence, labelStyleName: java.lang.String): com.badlogic.gdx.scenes.scene2d.ui.Cell[com.badlogic.gdx.scenes.scene2d.ui.Label] = {
    if (this.skin == null) {
      throw new java.lang.IllegalStateException("Table must have a skin set to use this method.")
    } else ()
    return this.add(new com.badlogic.gdx.scenes.scene2d.ui.Label(text, this.skin.get(labelStyleName, classOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle])))
  }
  def add(text: java.lang.CharSequence, fontName: java.lang.String, color: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.scenes.scene2d.ui.Cell[com.badlogic.gdx.scenes.scene2d.ui.Label] = {
    if (this.skin == null) {
      throw new java.lang.IllegalStateException("Table must have a skin set to use this method.")
    } else ()
    return this.add(new com.badlogic.gdx.scenes.scene2d.ui.Label(text, new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(this.skin.getFont(fontName), color)))
  }
  def add(text: java.lang.CharSequence, fontName: java.lang.String, colorName: java.lang.String): com.badlogic.gdx.scenes.scene2d.ui.Cell[com.badlogic.gdx.scenes.scene2d.ui.Label] = {
    if (this.skin == null) {
      throw new java.lang.IllegalStateException("Table must have a skin set to use this method.")
    } else ()
    return this.add(new com.badlogic.gdx.scenes.scene2d.ui.Label(text, new com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle(this.skin.getFont(fontName), this.skin.getColor(colorName))))
  }
  def add(): com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = {
    return this.add(null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.Actor]).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  }
  def stack(actors: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor]): com.badlogic.gdx.scenes.scene2d.ui.Cell[com.badlogic.gdx.scenes.scene2d.ui.Stack] = {
    val stack: com.badlogic.gdx.scenes.scene2d.ui.Stack = new com.badlogic.gdx.scenes.scene2d.ui.Stack()
    if (actors != null) {
      { var i: scala.Int = 0; val n: scala.Int = actors.length; while (i < n) { {
        stack.addActor(actors(i))
      }; i = i + 1 } }
    } else ()
    return this.add(stack)
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor): scala.Boolean = {
    return this.removeActor(actor, true)
  }
  def removeActor(actor: com.badlogic.gdx.scenes.scene2d.Actor, unfocus: scala.Boolean): scala.Boolean = {
    if (!super.removeActor(actor, unfocus)) {
      return false
    } else ()
    val cell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = this.getCell(actor).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
    if (cell != null) {
      cell.actor = null
    } else ()
    return true
  }
  def removeActorAt(index: scala.Int, unfocus: scala.Boolean): com.badlogic.gdx.scenes.scene2d.Actor = {
    var actor: com.badlogic.gdx.scenes.scene2d.Actor = super.removeActorAt(index, unfocus)
    val cell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = this.getCell(actor).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
    if (cell != null) {
      cell.actor = null
    } else ()
    return actor
  }
  def clearChildren(unfocus: scala.Boolean): scala.Unit = {
    val cells: scala.Array[java.lang.Object] = this.cells.items.asInstanceOf[scala.Array[java.lang.Object]];
    { var i: scala.Int = this.cells.size - 1; while (i >= 0) { {
      val cell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      val actor: com.badlogic.gdx.scenes.scene2d.Actor = cell.actor
      if (actor != null) {
        actor.remove()
      } else ()
    }; i = i - 1 } }
    Table.cellPool.freeAll(this.cells)
    this.cells.clear()
    this.rows = 0
    this.columns = 0
    if (this.rowDefaults != null) {
      Table.cellPool.free(this.rowDefaults)
    } else ()
    this.rowDefaults = null
    this.implicitEndRow = false
    super.clearChildren(unfocus)
  }
  def reset(): scala.Unit = {
    this.clearChildren()
    this.padTop$field = Table.backgroundTop
    this.padLeft$field = Table.backgroundLeft
    this.padBottom$field = Table.backgroundBottom
    this.padRight$field = Table.backgroundRight
    this.align$field = com.badlogic.gdx.utils.Align.center
    this.debug(com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none)
    this.cellDefaults.reset();
    { var i: scala.Int = 0; val n: scala.Int = this.columnDefaults$field.size; while (i < n) { {
      val columnCell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = this.columnDefaults$field.get(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      if (columnCell != null) {
        Table.cellPool.free(columnCell)
      } else ()
    }; i = i + 1 } }
    this.columnDefaults$field.clear()
  }
  def row(): com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = {
    if (this.cells.size > 0) {
      if (!this.implicitEndRow) {
        if (this.cells.peek().endRow) {
          return this.rowDefaults.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
        } else ()
        this.endRow()
      } else ()
      this.invalidate()
    } else ()
    this.implicitEndRow = false
    if (this.rowDefaults != null) {
      Table.cellPool.free(this.rowDefaults)
    } else ()
    this.rowDefaults = this.obtainCell().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
    this.rowDefaults.clear()
    return this.rowDefaults.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  }
  private def endRow(): scala.Unit = {
    val cells: scala.Array[java.lang.Object] = this.cells.items.asInstanceOf[scala.Array[java.lang.Object]]
    var rowColumns: scala.Int = 0;
    { var i: scala.Int = this.cells.size - 1; while (i >= 0) { {
      val cell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      if (cell.endRow) {
        /* break */ ()
      } else ()
      rowColumns = rowColumns + cell.colspan$field
    }; i = i - 1 } }
    this.columns = java.lang.Math.max(this.columns, rowColumns)
    this.rows = this.rows + 1
    this.cells.peek().endRow = true
  }
  def columnDefaults(column: scala.Int): com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = {
    var cell: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = if (this.columnDefaults$field.size > column) this.columnDefaults$field.get(column) else null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
    if (cell == null) {
      cell = this.obtainCell().asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      cell.clear()
      if (column >= this.columnDefaults$field.size) {
        { var i: scala.Int = this.columnDefaults$field.size; while (i < column) { {
          this.columnDefaults$field.add(null)
        }; i = i + 1 } }
        this.columnDefaults$field.add(cell)
      } else {
        this.columnDefaults$field.set(column, cell)
      }
    } else ()
    return cell.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  }
  def getCell[T <: com.badlogic.gdx.scenes.scene2d.Actor](actor: T): com.badlogic.gdx.scenes.scene2d.ui.Cell[T] = {
    if (actor == null) {
      throw new java.lang.IllegalArgumentException("actor cannot be null.")
    } else ()
    val cells: scala.Array[java.lang.Object] = this.cells.items.asInstanceOf[scala.Array[java.lang.Object]];
    { var i: scala.Int = 0; val n: scala.Int = this.cells.size; while (i < n) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[T] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]]
      if (c.actor == actor) {
        return c.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[T]]
      } else ()
    }; i = i + 1 } }
    return null
  }
  def getCells(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]] = {
    return this.cells.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]]
  }
  def getPrefWidth(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    val width: scala.Float = this.tablePrefWidth
    if (this.background$field != null) {
      return java.lang.Math.max(width, this.background$field.getMinWidth())
    } else ()
    return width
  }
  def getPrefHeight(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    val height: scala.Float = this.tablePrefHeight
    if (this.background$field != null) {
      return java.lang.Math.max(height, this.background$field.getMinHeight())
    } else ()
    return height
  }
  def getMinWidth(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.tableMinWidth
  }
  def getMinHeight(): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.tableMinHeight
  }
  def defaults(): com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = {
    return this.cellDefaults.asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
  }
  def pad(pad: com.badlogic.gdx.scenes.scene2d.ui.Value): Table = {
    if (pad == null) {
      throw new java.lang.IllegalArgumentException("pad cannot be null.")
    } else ()
    this.padTop$field = pad
    this.padLeft$field = pad
    this.padBottom$field = pad
    this.padRight$field = pad
    this.sizeInvalid = true
    return this
  }
  def pad(top: com.badlogic.gdx.scenes.scene2d.ui.Value, left: com.badlogic.gdx.scenes.scene2d.ui.Value, bottom: com.badlogic.gdx.scenes.scene2d.ui.Value, right: com.badlogic.gdx.scenes.scene2d.ui.Value): Table = {
    if (top == null) {
      throw new java.lang.IllegalArgumentException("top cannot be null.")
    } else ()
    if (left == null) {
      throw new java.lang.IllegalArgumentException("left cannot be null.")
    } else ()
    if (bottom == null) {
      throw new java.lang.IllegalArgumentException("bottom cannot be null.")
    } else ()
    if (right == null) {
      throw new java.lang.IllegalArgumentException("right cannot be null.")
    } else ()
    this.padTop$field = top
    this.padLeft$field = left
    this.padBottom$field = bottom
    this.padRight$field = right
    this.sizeInvalid = true
    return this
  }
  def padTop(padTop: com.badlogic.gdx.scenes.scene2d.ui.Value): Table = {
    if (padTop == null) {
      throw new java.lang.IllegalArgumentException("padTop cannot be null.")
    } else ()
    this.padTop$field = padTop
    this.sizeInvalid = true
    return this
  }
  def padLeft(padLeft: com.badlogic.gdx.scenes.scene2d.ui.Value): Table = {
    if (padLeft == null) {
      throw new java.lang.IllegalArgumentException("padLeft cannot be null.")
    } else ()
    this.padLeft$field = padLeft
    this.sizeInvalid = true
    return this
  }
  def padBottom(padBottom: com.badlogic.gdx.scenes.scene2d.ui.Value): Table = {
    if (padBottom == null) {
      throw new java.lang.IllegalArgumentException("padBottom cannot be null.")
    } else ()
    this.padBottom$field = padBottom
    this.sizeInvalid = true
    return this
  }
  def padRight(padRight: com.badlogic.gdx.scenes.scene2d.ui.Value): Table = {
    if (padRight == null) {
      throw new java.lang.IllegalArgumentException("padRight cannot be null.")
    } else ()
    this.padRight$field = padRight
    this.sizeInvalid = true
    return this
  }
  def pad(pad: scala.Float): Table = {
    this.pad(com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(pad))
    return this
  }
  def pad(top: scala.Float, left: scala.Float, bottom: scala.Float, right: scala.Float): Table = {
    this.padTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(top)
    this.padLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(left)
    this.padBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(bottom)
    this.padRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(right)
    this.sizeInvalid = true
    return this
  }
  def padTop(padTop: scala.Float): Table = {
    this.padTop$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padTop)
    this.sizeInvalid = true
    return this
  }
  def padLeft(padLeft: scala.Float): Table = {
    this.padLeft$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padLeft)
    this.sizeInvalid = true
    return this
  }
  def padBottom(padBottom: scala.Float): Table = {
    this.padBottom$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padBottom)
    this.sizeInvalid = true
    return this
  }
  def padRight(padRight: scala.Float): Table = {
    this.padRight$field = com.badlogic.gdx.scenes.scene2d.ui.Value.Fixed.valueOf(padRight)
    this.sizeInvalid = true
    return this
  }
  def align(align: scala.Int): Table = {
    this.align$field = align
    return this
  }
  def center(): Table = {
    this.align$field = com.badlogic.gdx.utils.Align.center
    return this
  }
  def top(): Table = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.top
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.bottom)
    return this
  }
  def left(): Table = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.left
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.right)
    return this
  }
  def bottom(): Table = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.bottom
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.top)
    return this
  }
  def right(): Table = {
    this.align$field = this.align$field | com.badlogic.gdx.utils.Align.right
    this.align$field = this.align$field & (~com.badlogic.gdx.utils.Align.left)
    return this
  }
  def setDebug(enabled: scala.Boolean): scala.Unit = {
    this.debug(if (enabled) com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.all else com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none)
  }
  def debug(): Table = {
    super.debug()
    return this
  }
  def debugAll(): Table = {
    super.debugAll()
    return this
  }
  def debugTable(): Table = {
    super.setDebug(true)
    if (this.debug$field != com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.table) {
      this.debug$field = com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.table
      this.invalidate()
    } else ()
    return this
  }
  def debugCell(): Table = {
    super.setDebug(true)
    if (this.debug$field != com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.cell) {
      this.debug$field = com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.cell
      this.invalidate()
    } else ()
    return this
  }
  def debugActor(): Table = {
    super.setDebug(true)
    if (this.debug$field != com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.actor) {
      this.debug$field = com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.actor
      this.invalidate()
    } else ()
    return this
  }
  def debug(debug: com.badlogic.gdx.scenes.scene2d.ui.Table.Debug): Table = {
    super.setDebug(debug != com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none)
    if (this.debug$field != debug) {
      this.debug$field = debug
      if (debug == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none) {
        this.clearDebugRects()
      } else {
        this.invalidate()
      }
    } else ()
    return this
  }
  def getTableDebug(): com.badlogic.gdx.scenes.scene2d.ui.Table.Debug = {
    return this.debug$field
  }
  def getPadTopValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padTop$field
  }
  def getPadTop(): scala.Float = {
    return this.padTop$field.get(this)
  }
  def getPadLeftValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padLeft$field
  }
  def getPadLeft(): scala.Float = {
    return this.padLeft$field.get(this)
  }
  def getPadBottomValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padBottom$field
  }
  def getPadBottom(): scala.Float = {
    return this.padBottom$field.get(this)
  }
  def getPadRightValue(): com.badlogic.gdx.scenes.scene2d.ui.Value = {
    return this.padRight$field
  }
  def getPadRight(): scala.Float = {
    return this.padRight$field.get(this)
  }
  def getPadX(): scala.Float = {
    return this.padLeft$field.get(this) + this.padRight$field.get(this)
  }
  def getPadY(): scala.Float = {
    return this.padTop$field.get(this) + this.padBottom$field.get(this)
  }
  def getAlign(): scala.Int = {
    return this.align$field
  }
  def getRow(y$arg: scala.Float): scala.Int = {
    var y: scala.Float = y$arg
    val n: scala.Int = this.cells.size
    if (n == 0) {
      return -1
    } else ()
    y = y + this.getPadTop()
    val cells: scala.Array[java.lang.Object] = this.cells.items.asInstanceOf[scala.Array[java.lang.Object]];
    { var i: scala.Int = 0; var row: scala.Int = 0; while (i < n) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells({ i += 1; i }).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      if ((c.actorY + c.computedPadTop) < y) {
        return row
      } else ()
      if (c.endRow) {
        row = row + 1
      } else ()
    };  } }
    return -1
  }
  def setSkin(skin: com.badlogic.gdx.scenes.scene2d.ui.Skin): scala.Unit = {
    this.skin = skin
  }
  def setRound(round: scala.Boolean): scala.Unit = {
    this.round = round
  }
  def getRows(): scala.Int = {
    return this.rows
  }
  def getColumns(): scala.Int = {
    return this.columns
  }
  def getRowHeight(rowIndex: scala.Int): scala.Float = {
    if (this.rowHeight == null) {
      return 0
    } else ()
    return this.rowHeight(rowIndex)
  }
  def getRowMinHeight(rowIndex: scala.Int): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.rowMinHeight(rowIndex)
  }
  def getRowPrefHeight(rowIndex: scala.Int): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.rowPrefHeight(rowIndex)
  }
  def getColumnWidth(columnIndex: scala.Int): scala.Float = {
    if (this.columnWidth == null) {
      return 0
    } else ()
    return this.columnWidth(columnIndex)
  }
  def getColumnMinWidth(columnIndex: scala.Int): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.columnMinWidth(columnIndex)
  }
  def getColumnPrefWidth(columnIndex: scala.Int): scala.Float = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    return this.columnPrefWidth(columnIndex)
  }
  private def ensureSize(array: scala.Array[scala.Float], size: scala.Int): scala.Array[scala.Float] = {
    if ((array == null) || (array.length < size)) {
      return new scala.Array[scala.Float](size)
    } else ()
    java.util.Arrays.fill(array, 0, size, 0)
    return array
  }
  private def computeSize(): scala.Unit = {
    this.sizeInvalid = false
    val cells: scala.Array[java.lang.Object] = this.cells.items.asInstanceOf[scala.Array[java.lang.Object]]
    val cellCount: scala.Int = this.cells.size
    if ((cellCount > 0) && (!cells(cellCount - 1).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].endRow)) {
      this.endRow()
      this.implicitEndRow = true
    } else ()
    val columns: scala.Int = this.columns
    val rows: scala.Int = this.rows
    var columnMinWidth: scala.Array[scala.Float] = {
      this.columnMinWidth = this.ensureSize(this.columnMinWidth, columns)
      this.columnMinWidth
    }
    var rowMinHeight: scala.Array[scala.Float] = {
      this.rowMinHeight = this.ensureSize(this.rowMinHeight, rows)
      this.rowMinHeight
    }
    var columnPrefWidth: scala.Array[scala.Float] = {
      this.columnPrefWidth = this.ensureSize(this.columnPrefWidth, columns)
      this.columnPrefWidth
    }
    var rowPrefHeight: scala.Array[scala.Float] = {
      this.rowPrefHeight = this.ensureSize(this.rowPrefHeight, rows)
      this.rowPrefHeight
    }
    var columnWidth: scala.Array[scala.Float] = {
      this.columnWidth = this.ensureSize(this.columnWidth, columns)
      this.columnWidth
    }
    var rowHeight: scala.Array[scala.Float] = {
      this.rowHeight = this.ensureSize(this.rowHeight, rows)
      this.rowHeight
    }
    var expandWidth: scala.Array[scala.Float] = {
      this.expandWidth = this.ensureSize(this.expandWidth, columns)
      this.expandWidth
    }
    var expandHeight: scala.Array[scala.Float] = {
      this.expandHeight = this.ensureSize(this.expandHeight, rows)
      this.expandHeight
    }
    var spaceRightLast: scala.Float = 0;
    { var i: scala.Int = 0; while (i < cellCount) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      val column: scala.Int = c.column
      val row: scala.Int = c.row$field
      val colspan: scala.Int = c.colspan$field
      val a: com.badlogic.gdx.scenes.scene2d.Actor = c.actor
      if ((c.expandY$field != 0) && (expandHeight(row) == 0)) {
        expandHeight(row) = c.expandY$field.floatValue()
      } else ()
      if (((colspan == 1) && (c.expandX$field != 0)) && (expandWidth(column) == 0)) {
        expandWidth(column) = c.expandX$field.floatValue()
      } else ()
      c.computedPadLeft = c.padLeft$field.get(a) + (if (column == 0) 0 else java.lang.Math.max(0, c.spaceLeft$field.get(a) - spaceRightLast))
      c.computedPadTop = c.padTop$field.get(a)
      if (c.cellAboveIndex != (-1)) {
        val above: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(c.cellAboveIndex).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
        c.computedPadTop = c.computedPadTop + java.lang.Math.max(0, c.spaceTop$field.get(a) - above.spaceBottom$field.get(a))
      } else ()
      val spaceRight: scala.Float = c.spaceRight$field.get(a)
      c.computedPadRight = c.padRight$field.get(a) + (if ((column + colspan) == columns) 0 else spaceRight)
      c.computedPadBottom = c.padBottom$field.get(a) + (if (row == (rows - 1)) 0 else c.spaceBottom$field.get(a))
      spaceRightLast = spaceRight
      var prefWidth: scala.Float = c.prefWidth$field.get(a)
      var prefHeight: scala.Float = c.prefHeight$field.get(a)
      var minWidth: scala.Float = c.minWidth$field.get(a)
      var minHeight: scala.Float = c.minHeight$field.get(a)
      val maxWidth: scala.Float = c.maxWidth$field.get(a)
      val maxHeight: scala.Float = c.maxHeight$field.get(a)
      if (prefWidth < minWidth) {
        prefWidth = minWidth
      } else ()
      if (prefHeight < minHeight) {
        prefHeight = minHeight
      } else ()
      if ((maxWidth > 0) && (prefWidth > maxWidth)) {
        prefWidth = maxWidth
      } else ()
      if ((maxHeight > 0) && (prefHeight > maxHeight)) {
        prefHeight = maxHeight
      } else ()
      if (this.round) {
        minWidth = java.lang.Math.ceil(minWidth).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        minHeight = java.lang.Math.ceil(minHeight).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        prefWidth = java.lang.Math.ceil(prefWidth).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        prefHeight = java.lang.Math.ceil(prefHeight).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      } else ()
      if (colspan == 1) {
        val hpadding: scala.Float = c.computedPadLeft + c.computedPadRight
        columnPrefWidth(column) = java.lang.Math.max(columnPrefWidth(column), prefWidth + hpadding)
        columnMinWidth(column) = java.lang.Math.max(columnMinWidth(column), minWidth + hpadding)
      } else ()
      val vpadding: scala.Float = c.computedPadTop + c.computedPadBottom
      rowPrefHeight(row) = java.lang.Math.max(rowPrefHeight(row), prefHeight + vpadding)
      rowMinHeight(row) = java.lang.Math.max(rowMinHeight(row), minHeight + vpadding)
    }; i = i + 1 } }
    var uniformMinWidth: scala.Float = 0
    var uniformMinHeight: scala.Float = 0
    var uniformPrefWidth: scala.Float = 0
    var uniformPrefHeight: scala.Float = 0;
    { var i: scala.Int = 0; while (i < cellCount) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      val column: scala.Int = c.column
      val expandX: scala.Int = c.expandX$field
      if (expandX != 0) {
        val nn: scala.Int = column + c.colspan$field;
        { var ii: scala.Int = column; while (ii < nn) { {
          if (expandWidth(ii) != 0) {
            /* break */ ()
          } else ()
        }; ii = ii + 1 } };
        { var ii: scala.Int = column; while (ii < nn) { {
          expandWidth(ii) = expandX
        }; ii = ii + 1 } }
      } else ()
      if ((c.uniformX$field == java.lang.Boolean.TRUE) && (c.colspan$field == 1)) {
        val hpadding: scala.Float = c.computedPadLeft + c.computedPadRight
        uniformMinWidth = java.lang.Math.max(uniformMinWidth, columnMinWidth(column) - hpadding)
        uniformPrefWidth = java.lang.Math.max(uniformPrefWidth, columnPrefWidth(column) - hpadding)
      } else ()
      if (c.uniformY$field == java.lang.Boolean.TRUE) {
        val vpadding: scala.Float = c.computedPadTop + c.computedPadBottom
        uniformMinHeight = java.lang.Math.max(uniformMinHeight, rowMinHeight(c.row$field) - vpadding)
        uniformPrefHeight = java.lang.Math.max(uniformPrefHeight, rowPrefHeight(c.row$field) - vpadding)
      } else ()
    }; i = i + 1 } }
    if ((uniformPrefWidth > 0) || (uniformPrefHeight > 0)) {
      { var i: scala.Int = 0; while (i < cellCount) { {
        val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
        if (((uniformPrefWidth > 0) && (c.uniformX$field == java.lang.Boolean.TRUE)) && (c.colspan$field == 1)) {
          val hpadding: scala.Float = c.computedPadLeft + c.computedPadRight
          columnMinWidth(c.column) = uniformMinWidth + hpadding
          columnPrefWidth(c.column) = uniformPrefWidth + hpadding
        } else ()
        if ((uniformPrefHeight > 0) && (c.uniformY$field == java.lang.Boolean.TRUE)) {
          val vpadding: scala.Float = c.computedPadTop + c.computedPadBottom
          rowMinHeight(c.row$field) = uniformMinHeight + vpadding
          rowPrefHeight(c.row$field) = uniformPrefHeight + vpadding
        } else ()
      }; i = i + 1 } }
    } else ();
    { var i: scala.Int = 0; while (i < cellCount) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      val colspan: scala.Int = c.colspan$field
      if (colspan == 1) {
        /* continue */ ()
      } else ()
      val column: scala.Int = c.column
      val a: com.badlogic.gdx.scenes.scene2d.Actor = c.actor
      var minWidth: scala.Float = c.minWidth$field.get(a)
      var prefWidth: scala.Float = c.prefWidth$field.get(a)
      val maxWidth: scala.Float = c.maxWidth$field.get(a)
      if (prefWidth < minWidth) {
        prefWidth = minWidth
      } else ()
      if ((maxWidth > 0) && (prefWidth > maxWidth)) {
        prefWidth = maxWidth
      } else ()
      if (this.round) {
        minWidth = java.lang.Math.ceil(minWidth).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        prefWidth = java.lang.Math.ceil(prefWidth).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      } else ()
      var spannedMinWidth: scala.Float = -(c.computedPadLeft + c.computedPadRight)
      var spannedPrefWidth: scala.Float = spannedMinWidth
      var totalExpandWidth: scala.Float = 0;
      { var ii: scala.Int = column; val nn: scala.Int = ii + colspan; while (ii < nn) { {
        spannedMinWidth = spannedMinWidth + columnMinWidth(ii)
        spannedPrefWidth = spannedPrefWidth + columnPrefWidth(ii)
        totalExpandWidth = totalExpandWidth + expandWidth(ii)
      }; ii = ii + 1 } }
      val extraMinWidth: scala.Float = java.lang.Math.max(0, minWidth - spannedMinWidth)
      val extraPrefWidth: scala.Float = java.lang.Math.max(0, prefWidth - spannedPrefWidth);
      { var ii: scala.Int = column; val nn: scala.Int = ii + colspan; while (ii < nn) { {
        val ratio: scala.Float = if (totalExpandWidth == 0) 1.0f / colspan else expandWidth(ii) / totalExpandWidth
        columnMinWidth(ii) = columnMinWidth(ii) + (extraMinWidth * ratio)
        columnPrefWidth(ii) = columnPrefWidth(ii) + (extraPrefWidth * ratio)
      }; ii = ii + 1 } }
    }; i = i + 1 } }
    val hpadding: scala.Float = this.padLeft$field.get(this) + this.padRight$field.get(this)
    val vpadding: scala.Float = this.padTop$field.get(this) + this.padBottom$field.get(this)
    this.tableMinWidth = hpadding
    this.tablePrefWidth = hpadding;
    { var i: scala.Int = 0; while (i < columns) { {
      this.tableMinWidth = this.tableMinWidth + columnMinWidth(i)
      this.tablePrefWidth = this.tablePrefWidth + columnPrefWidth(i)
    }; i = i + 1 } }
    this.tableMinHeight = vpadding
    this.tablePrefHeight = vpadding;
    { var i: scala.Int = 0; while (i < rows) { {
      this.tableMinHeight = this.tableMinHeight + rowMinHeight(i)
      this.tablePrefHeight = this.tablePrefHeight + java.lang.Math.max(rowMinHeight(i), rowPrefHeight(i))
    }; i = i + 1 } }
    this.tablePrefWidth = java.lang.Math.max(this.tableMinWidth, this.tablePrefWidth)
    this.tablePrefHeight = java.lang.Math.max(this.tableMinHeight, this.tablePrefHeight)
  }
  def layout(): scala.Unit = {
    if (this.sizeInvalid) {
      this.computeSize()
    } else ()
    val layoutWidth: scala.Float = this.getWidth()
    val layoutHeight: scala.Float = this.getHeight()
    val columns: scala.Int = this.columns
    val rows: scala.Int = this.rows
    val columnWidth: scala.Array[scala.Float] = this.columnWidth
    val rowHeight: scala.Array[scala.Float] = this.rowHeight
    val padLeft: scala.Float = this.padLeft$field.get(this)
    val hpadding: scala.Float = padLeft + this.padRight$field.get(this)
    val padTop: scala.Float = this.padTop$field.get(this)
    val vpadding: scala.Float = padTop + this.padBottom$field.get(this)
    var columnWeightedWidth: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    val totalGrowWidth: scala.Float = this.tablePrefWidth - this.tableMinWidth
    if (totalGrowWidth == 0) {
      columnWeightedWidth = this.columnMinWidth
    } else {
      var extraWidth: scala.Float = java.lang.Math.min(totalGrowWidth, java.lang.Math.max(0, layoutWidth - this.tableMinWidth))
      columnWeightedWidth = {
        Table.columnWeightedWidth = this.ensureSize(Table.columnWeightedWidth, columns)
        Table.columnWeightedWidth
      }
      val columnMinWidth: scala.Array[scala.Float] = this.columnMinWidth
      val columnPrefWidth: scala.Array[scala.Float] = this.columnPrefWidth;
      { var i: scala.Int = 0; while (i < columns) { {
        val growWidth: scala.Float = columnPrefWidth(i) - columnMinWidth(i)
        val growRatio: scala.Float = growWidth / totalGrowWidth
        columnWeightedWidth(i) = columnMinWidth(i) + (extraWidth * growRatio)
      }; i = i + 1 } }
    }
    var rowWeightedHeight: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    val totalGrowHeight: scala.Float = this.tablePrefHeight - this.tableMinHeight
    if (totalGrowHeight == 0) {
      rowWeightedHeight = this.rowMinHeight
    } else {
      rowWeightedHeight = {
        Table.rowWeightedHeight = this.ensureSize(Table.rowWeightedHeight, rows)
        Table.rowWeightedHeight
      }
      val extraHeight: scala.Float = java.lang.Math.min(totalGrowHeight, java.lang.Math.max(0, layoutHeight - this.tableMinHeight))
      val rowMinHeight: scala.Array[scala.Float] = this.rowMinHeight
      val rowPrefHeight: scala.Array[scala.Float] = this.rowPrefHeight;
      { var i: scala.Int = 0; while (i < rows) { {
        val growHeight: scala.Float = rowPrefHeight(i) - rowMinHeight(i)
        val growRatio: scala.Float = growHeight / totalGrowHeight
        rowWeightedHeight(i) = rowMinHeight(i) + (extraHeight * growRatio)
      }; i = i + 1 } }
    }
    val cells: scala.Array[java.lang.Object] = this.cells.items.asInstanceOf[scala.Array[java.lang.Object]]
    val cellCount: scala.Int = this.cells.size;
    { var i: scala.Int = 0; while (i < cellCount) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      var column: scala.Int = c.column
      val row: scala.Int = c.row$field
      val a: com.badlogic.gdx.scenes.scene2d.Actor = c.actor
      var spannedWeightedWidth: scala.Float = 0
      val colspan: scala.Int = c.colspan$field;
      { var ii: scala.Int = column; val nn: scala.Int = ii + colspan; while (ii < nn) { {
        spannedWeightedWidth = spannedWeightedWidth + columnWeightedWidth(ii)
      }; ii = ii + 1 } }
      val weightedHeight: scala.Float = rowWeightedHeight(row)
      var prefWidth: scala.Float = c.prefWidth$field.get(a)
      var prefHeight: scala.Float = c.prefHeight$field.get(a)
      val minWidth: scala.Float = c.minWidth$field.get(a)
      val minHeight: scala.Float = c.minHeight$field.get(a)
      val maxWidth: scala.Float = c.maxWidth$field.get(a)
      val maxHeight: scala.Float = c.maxHeight$field.get(a)
      if (prefWidth < minWidth) {
        prefWidth = minWidth
      } else ()
      if (prefHeight < minHeight) {
        prefHeight = minHeight
      } else ()
      if ((maxWidth > 0) && (prefWidth > maxWidth)) {
        prefWidth = maxWidth
      } else ()
      if ((maxHeight > 0) && (prefHeight > maxHeight)) {
        prefHeight = maxHeight
      } else ()
      c.actorWidth = java.lang.Math.min((spannedWeightedWidth - c.computedPadLeft) - c.computedPadRight, prefWidth)
      c.actorHeight = java.lang.Math.min((weightedHeight - c.computedPadTop) - c.computedPadBottom, prefHeight)
      if (colspan == 1) {
        columnWidth(column) = java.lang.Math.max(columnWidth(column), spannedWeightedWidth)
      } else ()
      rowHeight(row) = java.lang.Math.max(rowHeight(row), weightedHeight)
    }; i = i + 1 } }
    val expandWidth: scala.Array[scala.Float] = this.expandWidth
    val expandHeight: scala.Array[scala.Float] = this.expandHeight
    var totalExpand: scala.Float = 0;
    { var i: scala.Int = 0; while (i < columns) { {
      totalExpand = totalExpand + expandWidth(i)
    }; i = i + 1 } }
    if (totalExpand > 0) {
      var extra: scala.Float = layoutWidth - hpadding;
      { var i: scala.Int = 0; while (i < columns) { {
        extra = extra - columnWidth(i)
      }; i = i + 1 } }
      if (extra > 0) {
        var used: scala.Float = 0
        var lastIndex: scala.Int = 0;
        { var i: scala.Int = 0; while (i < columns) { {
          if (expandWidth(i) == 0) {
            /* continue */ ()
          } else ()
          val amount: scala.Float = (extra * expandWidth(i)) / totalExpand
          columnWidth(i) = columnWidth(i) + amount
          used = used + amount
          lastIndex = i
        }; i = i + 1 } }
        columnWidth(lastIndex) = columnWidth(lastIndex) + (extra - used)
      } else ()
    } else ()
    totalExpand = 0;
    { var i: scala.Int = 0; while (i < rows) { {
      totalExpand = totalExpand + expandHeight(i)
    }; i = i + 1 } }
    if (totalExpand > 0) {
      var extra: scala.Float = layoutHeight - vpadding;
      { var i: scala.Int = 0; while (i < rows) { {
        extra = extra - rowHeight(i)
      }; i = i + 1 } }
      if (extra > 0) {
        var used: scala.Float = 0
        var lastIndex: scala.Int = 0;
        { var i: scala.Int = 0; while (i < rows) { {
          if (expandHeight(i) == 0) {
            /* continue */ ()
          } else ()
          val amount: scala.Float = (extra * expandHeight(i)) / totalExpand
          rowHeight(i) = rowHeight(i) + amount
          used = used + amount
          lastIndex = i
        }; i = i + 1 } }
        rowHeight(lastIndex) = rowHeight(lastIndex) + (extra - used)
      } else ()
    } else ();
    { var i: scala.Int = 0; while (i < cellCount) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      val colspan: scala.Int = c.colspan$field
      if (colspan == 1) {
        /* continue */ ()
      } else ()
      var extraWidth: scala.Float = 0;
      { var column: scala.Int = c.column; val nn: scala.Int = column + colspan; while (column < nn) { {
        extraWidth = extraWidth + (columnWeightedWidth(column) - columnWidth(column))
      }; column = column + 1 } }
      extraWidth = extraWidth - java.lang.Math.max(0, c.computedPadLeft + c.computedPadRight)
      extraWidth = extraWidth / colspan
      if (extraWidth > 0) {
        { var column: scala.Int = c.column; val nn: scala.Int = column + colspan; while (column < nn) { {
          columnWidth(column) = columnWidth(column) + extraWidth
        }; column = column + 1 } }
      } else ()
    }; i = i + 1 } }
    var tableWidth: scala.Float = hpadding
    var tableHeight: scala.Float = vpadding;
    { var i: scala.Int = 0; while (i < columns) { {
      tableWidth = tableWidth + columnWidth(i)
    }; i = i + 1 } };
    { var i: scala.Int = 0; while (i < rows) { {
      tableHeight = tableHeight + rowHeight(i)
    }; i = i + 1 } }
    var align: scala.Int = this.align$field
    var x: scala.Float = padLeft
    if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
      x = x + (layoutWidth - tableWidth)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.left) == 0) {
        x = x + ((layoutWidth - tableWidth) / 2)
      } else ()
    }
    var y: scala.Float = padTop
    if ((align & com.badlogic.gdx.utils.Align.bottom) != 0) {
      y = y + (layoutHeight - tableHeight)
    } else {
      if ((align & com.badlogic.gdx.utils.Align.top) == 0) {
        y = y + ((layoutHeight - tableHeight) / 2)
      } else ()
    }
    var currentX: scala.Float = x
    var currentY: scala.Float = y;
    { var i: scala.Int = 0; while (i < cellCount) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = cells(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]].asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      var spannedCellWidth: scala.Float = 0;
      { var column: scala.Int = c.column; val nn: scala.Int = column + c.colspan$field; while (column < nn) { {
        spannedCellWidth = spannedCellWidth + columnWidth(column)
      }; column = column + 1 } }
      spannedCellWidth = spannedCellWidth - (c.computedPadLeft + c.computedPadRight)
      currentX = currentX + c.computedPadLeft
      val fillX: scala.Float = c.fillX$field
      val fillY: scala.Float = c.fillY$field
      if (fillX > 0) {
        c.actorWidth = java.lang.Math.max(spannedCellWidth * fillX, c.minWidth$field.get(c.actor))
        val maxWidth: scala.Float = c.maxWidth$field.get(c.actor)
        if (maxWidth > 0) {
          c.actorWidth = java.lang.Math.min(c.actorWidth, maxWidth)
        } else ()
      } else ()
      if (fillY > 0) {
        c.actorHeight = java.lang.Math.max(((rowHeight(c.row$field) * fillY) - c.computedPadTop) - c.computedPadBottom, c.minHeight$field.get(c.actor))
        val maxHeight: scala.Float = c.maxHeight$field.get(c.actor)
        if (maxHeight > 0) {
          c.actorHeight = java.lang.Math.min(c.actorHeight, maxHeight)
        } else ()
      } else ()
      align = c.align$field
      if ((align & com.badlogic.gdx.utils.Align.left) != 0) {
        c.actorX = currentX
      } else {
        if ((align & com.badlogic.gdx.utils.Align.right) != 0) {
          c.actorX = (currentX + spannedCellWidth) - c.actorWidth
        } else {
          c.actorX = currentX + ((spannedCellWidth - c.actorWidth) / 2)
        }
      }
      if ((align & com.badlogic.gdx.utils.Align.top) != 0) {
        c.actorY = c.computedPadTop
      } else {
        if ((align & com.badlogic.gdx.utils.Align.bottom) != 0) {
          c.actorY = (rowHeight(c.row$field) - c.actorHeight) - c.computedPadBottom
        } else {
          c.actorY = (((rowHeight(c.row$field) - c.actorHeight) + c.computedPadTop) - c.computedPadBottom) / 2
        }
      }
      c.actorY = ((layoutHeight - currentY) - c.actorY) - c.actorHeight
      if (this.round) {
        c.actorWidth = java.lang.Math.ceil(c.actorWidth).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        c.actorHeight = java.lang.Math.ceil(c.actorHeight).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        c.actorX = java.lang.Math.floor(c.actorX).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
        c.actorY = java.lang.Math.floor(c.actorY).asInstanceOf[scala.Float].asInstanceOf[scala.Float]
      } else ()
      if (c.actor != null) {
        c.actor.setBounds(c.actorX, c.actorY, c.actorWidth, c.actorHeight)
      } else ()
      if (c.endRow) {
        currentX = x
        currentY = currentY + rowHeight(c.row$field)
      } else {
        currentX = currentX + (spannedCellWidth + c.computedPadRight)
      }
    }; i = i + 1 } }
    val childrenArray: com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.Actor] = this.getChildren()
    val children: scala.Array[com.badlogic.gdx.scenes.scene2d.Actor] = childrenArray.items;
    { var i: scala.Int = 0; val n: scala.Int = childrenArray.size; while (i < n) { {
      val child: java.lang.Object = children(i)
      if (child.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout]) {
        child.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Layout].validate()
      } else ()
    }; i = i + 1 } }
    if (this.debug$field != com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.none) {
      this.addDebugRects(x, y, tableWidth - hpadding, tableHeight - vpadding)
    } else ()
  }
  private def addDebugRects(currentX$arg: scala.Float, currentY$arg: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
    var currentX: scala.Float = currentX$arg
    var currentY: scala.Float = currentY$arg
    this.clearDebugRects()
    if ((this.debug$field == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.table) || (this.debug$field == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.all)) {
      this.addDebugRect(0, 0, this.getWidth(), this.getHeight(), Table.debugTableColor)
      this.addDebugRect(currentX, this.getHeight() - currentY, width, -height, Table.debugTableColor)
    } else ()
    val x: scala.Float = currentX;
    { var i: scala.Int = 0; val n: scala.Int = this.cells.size; while (i < n) { {
      val c: com.badlogic.gdx.scenes.scene2d.ui.Cell[?] = this.cells.get(i).asInstanceOf[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]
      if ((this.debug$field == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.actor) || (this.debug$field == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.all)) {
        this.addDebugRect(c.actorX, c.actorY, c.actorWidth, c.actorHeight, Table.debugActorColor)
      } else ()
      var spannedCellWidth: scala.Float = 0;
      { var column: scala.Int = c.column; val nn: scala.Int = column + c.colspan$field; while (column < nn) { {
        spannedCellWidth = spannedCellWidth + this.columnWidth(column)
      }; column = column + 1 } }
      spannedCellWidth = spannedCellWidth - (c.computedPadLeft + c.computedPadRight)
      currentX = currentX + c.computedPadLeft
      if ((this.debug$field == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.cell) || (this.debug$field == com.badlogic.gdx.scenes.scene2d.ui.Table.Debug.all)) {
        val h: scala.Float = (this.rowHeight(c.row$field) - c.computedPadTop) - c.computedPadBottom
        val y: scala.Float = currentY + c.computedPadTop
        this.addDebugRect(currentX, this.getHeight() - y, spannedCellWidth, -h, Table.debugCellColor)
      } else ()
      if (c.endRow) {
        currentX = x
        currentY = currentY + this.rowHeight(c.row$field)
      } else {
        currentX = currentX + (spannedCellWidth + c.computedPadRight)
      }
    }; i = i + 1 } }
  }
  private def clearDebugRects(): scala.Unit = {
    if (this.debugRects == null) {
      this.debugRects = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect]]
    } else ()
    com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect.pool.freeAll(this.debugRects)
    this.debugRects.clear()
  }
  private def addDebugRect(x: scala.Float, y: scala.Float, w: scala.Float, h: scala.Float, color: com.badlogic.gdx.graphics.Color): scala.Unit = {
    val rect: com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect = com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect.pool.obtain()
    rect.color = color
    rect.set(x, y, w, h)
    this.debugRects.add(rect)
  }
  def drawDebug(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    if (this.isTransform()) {
      this.applyTransform(shapes, this.computeTransform())
      this.drawDebugRects(shapes)
      if (this.clip$field) {
        shapes.flush()
        var x: scala.Float = 0
        var y: scala.Float = 0
        var width: scala.Float = this.getWidth()
        var height: scala.Float = this.getHeight()
        if (this.background$field != null) {
          x = this.padLeft$field.get(this)
          y = this.padBottom$field.get(this)
          width = width - (x + this.padRight$field.get(this))
          height = height - (y + this.padTop$field.get(this))
        } else ()
        if (this.clipBegin(x, y, width, height)) {
          this.drawDebugChildren(shapes)
          this.clipEnd()
        } else ()
      } else {
        this.drawDebugChildren(shapes)
      }
      this.resetTransform(shapes)
    } else {
      this.drawDebugRects(shapes)
      super.drawDebug(shapes)
    }
  }
  def drawDebugBounds(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    ()
  }
  private def drawDebugRects(shapes: com.badlogic.gdx.graphics.glutils.ShapeRenderer): scala.Unit = {
    if ((this.debugRects == null) || (!this.getDebug())) {
      return
    } else ()
    shapes.set(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line)
    if (this.getStage() != null) {
      shapes.setColor(this.getStage().getDebugColor())
    } else ()
    var x: scala.Float = 0
    var y: scala.Float = 0
    if (!this.isTransform()) {
      x = this.getX()
      y = this.getY()
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = this.debugRects.size; while (i < n) { {
      val debugRect: com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect = this.debugRects.get(i)
      shapes.setColor(debugRect.color)
      shapes.rect(x + debugRect.x, y + debugRect.y, debugRect.width, debugRect.height)
    }; i = i + 1 } }
  }
  def getSkin(): com.badlogic.gdx.scenes.scene2d.ui.Skin = {
    return this.skin
  }
}
object Table {
  export com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup.{debugTableColor => _, debugCellColor => _, debugActorColor => _, cellPool => _, columnWeightedWidth => _, rowWeightedHeight => _, backgroundTop => _, backgroundLeft => _, backgroundBottom => _, backgroundRight => _, DebugRect => _, Debug => _, *}
  var debugTableColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(0, 0, 1, 1)
  var debugCellColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(1, 0, 0, 1)
  var debugActorColor: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color(0, 1, 0, 1)
  final val cellPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]] = new com.badlogic.gdx.utils.Pool[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]().asInstanceOf[com.badlogic.gdx.utils.Pool[com.badlogic.gdx.scenes.scene2d.ui.Cell[?]]]
  private var columnWeightedWidth: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  private var rowWeightedHeight: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
  var backgroundTop: com.badlogic.gdx.scenes.scene2d.ui.Value = new com.badlogic.gdx.scenes.scene2d.ui.Value()
  var backgroundLeft: com.badlogic.gdx.scenes.scene2d.ui.Value = new com.badlogic.gdx.scenes.scene2d.ui.Value()
  var backgroundBottom: com.badlogic.gdx.scenes.scene2d.ui.Value = new com.badlogic.gdx.scenes.scene2d.ui.Value()
  var backgroundRight: com.badlogic.gdx.scenes.scene2d.ui.Value = new com.badlogic.gdx.scenes.scene2d.ui.Value()
  class DebugRect extends com.badlogic.gdx.math.Rectangle {
    var color: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  }
  object DebugRect {
    export com.badlogic.gdx.math.Rectangle.{pool => _, *}
    var pool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect] = new com.badlogic.gdx.utils.DefaultPool[com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect](((() => new com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect()): com.badlogic.gdx.utils.DefaultPool.PoolSupplier[com.badlogic.gdx.scenes.scene2d.ui.Table.DebugRect]))
  }
  sealed abstract class Debug {
    def name(): java.lang.String = this.toString()
  }
  object Debug {
    case object none extends Debug
    case object all extends Debug
    case object table extends Debug
    case object cell extends Debug
    case object actor extends Debug
    def values(): scala.Array[Debug] = scala.Array(none, all, table, cell, actor)
    def valueOf(name: java.lang.String): Debug = name match {
      case "none" => none
      case "all" => all
      case "table" => table
      case "cell" => cell
      case "actor" => actor
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}