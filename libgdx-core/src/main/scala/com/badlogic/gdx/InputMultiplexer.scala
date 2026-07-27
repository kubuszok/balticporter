package com.badlogic.gdx

class InputMultiplexer extends com.badlogic.gdx.InputProcessor {
  private var processors: com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.InputProcessor] = new com.badlogic.gdx.utils.SnapshotArray(4).asInstanceOf[com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.InputProcessor]]
  def this(processors: scala.Array[com.badlogic.gdx.InputProcessor]) = {
    this()
    this.processors.addAll(processors)
  }
  def addProcessor(index: scala.Int, processor: com.badlogic.gdx.InputProcessor): scala.Unit = {
    if (processor == null) {
      throw new java.lang.NullPointerException("processor cannot be null")
    } else ()
    this.processors.insert(index, processor)
  }
  def removeProcessor(index: scala.Int): com.badlogic.gdx.InputProcessor = {
    return this.processors.removeIndex(index)
  }
  def addProcessor(processor: com.badlogic.gdx.InputProcessor): scala.Unit = {
    if (processor == null) {
      throw new java.lang.NullPointerException("processor cannot be null")
    } else ()
    this.processors.add(processor)
  }
  def removeProcessor(processor: com.badlogic.gdx.InputProcessor): scala.Boolean = {
    return this.processors.removeValue(processor, true)
  }
  def size(): scala.Int = {
    return this.processors.size
  }
  def clear(): scala.Unit = {
    this.processors.clear()
  }
  def setProcessors(processors: scala.Array[com.badlogic.gdx.InputProcessor]): scala.Unit = {
    this.processors.clear()
    this.processors.addAll(processors)
  }
  def setProcessors(processors: com.badlogic.gdx.utils.Array[com.badlogic.gdx.InputProcessor]): scala.Unit = {
    this.processors.clear()
    this.processors.addAll(processors)
  }
  def getProcessors(): com.badlogic.gdx.utils.SnapshotArray[com.badlogic.gdx.InputProcessor] = {
    return this.processors
  }
  def keyDown(keycode: scala.Int): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].keyDown(keycode)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def keyUp(keycode: scala.Int): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].keyUp(keycode)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def keyTyped(character: scala.Char): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].keyTyped(character)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def touchDown(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].touchDown(screenX, screenY, pointer, button)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def touchUp(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].touchUp(screenX, screenY, pointer, button)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def touchCancelled(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].touchCancelled(screenX, screenY, pointer, button)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].touchDragged(screenX, screenY, pointer)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def mouseMoved(screenX: scala.Int, screenY: scala.Int): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].mouseMoved(screenX, screenY)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
  def scrolled(amountX: scala.Float, amountY: scala.Float): scala.Boolean = {
    val items: scala.Array[java.lang.Object] = this.processors.begin().asInstanceOf[scala.Array[java.lang.Object]]
    try {
      { var i: scala.Int = 0; val n: scala.Int = this.processors.size; while (i < n) { {
        if (items(i).asInstanceOf[com.badlogic.gdx.InputProcessor].scrolled(amountX, amountY)) {
          return true
        } else ()
      }; i = i + 1 } }
    } finally {
      this.processors.`end`()
    }
    return false
  }
}