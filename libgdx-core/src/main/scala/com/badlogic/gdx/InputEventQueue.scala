package com.badlogic.gdx

class InputEventQueue {
  private final val queue: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private final val processingQueue: com.badlogic.gdx.utils.IntArray = new com.badlogic.gdx.utils.IntArray()
  private var currentEventTime: scala.Long = 0L
  def drain(processor: com.badlogic.gdx.InputProcessor): scala.Unit = {
    this.synchronized {
      if (processor == null) {
        this.queue.clear()
        return
      } else ()
      this.processingQueue.addAll(this.queue)
      this.queue.clear()
    }
    val q: scala.Array[scala.Int] = this.processingQueue.items;
    { var i: scala.Int = 0; val n: scala.Int = this.processingQueue.size; while (i < n) { {
      val `type`: scala.Int = q({ i += 1; i })
      this.currentEventTime = (q({ i += 1; i }).asInstanceOf[scala.Long] << 32) | (q({ i += 1; i }) & 4294967295L)
      `type` match {
        case InputEventQueue.SKIP => {
          i = i + q(i)
        }
        case InputEventQueue.KEY_DOWN => {
          processor.keyDown(q({ i += 1; i }))
        }
        case InputEventQueue.KEY_UP => {
          processor.keyUp(q({ i += 1; i }))
        }
        case InputEventQueue.KEY_TYPED => {
          processor.keyTyped(q({ i += 1; i }).asInstanceOf[scala.Char].asInstanceOf[scala.Char])
        }
        case InputEventQueue.TOUCH_DOWN => {
          processor.touchDown(q({ i += 1; i }), q({ i += 1; i }), q({ i += 1; i }), q({ i += 1; i }))
        }
        case InputEventQueue.TOUCH_UP => {
          processor.touchUp(q({ i += 1; i }), q({ i += 1; i }), q({ i += 1; i }), q({ i += 1; i }))
        }
        case InputEventQueue.TOUCH_DRAGGED => {
          processor.touchDragged(q({ i += 1; i }), q({ i += 1; i }), q({ i += 1; i }))
        }
        case InputEventQueue.MOUSE_MOVED => {
          processor.mouseMoved(q({ i += 1; i }), q({ i += 1; i }))
        }
        case InputEventQueue.SCROLLED => {
          processor.scrolled(com.badlogic.gdx.utils.NumberUtils.intBitsToFloat(q({ i += 1; i })), com.badlogic.gdx.utils.NumberUtils.intBitsToFloat(q({ i += 1; i })))
        }
        case _ => {
          throw new java.lang.RuntimeException()
        }
      }
    };  } }
    this.processingQueue.clear()
  }
  private def next(nextType: scala.Int, i$arg: scala.Int): scala.Int = {
    var i: scala.Int = i$arg
    val q: scala.Array[scala.Int] = this.queue.items;
    { val n: scala.Int = this.queue.size; while (i < n) { {
      val `type`: scala.Int = q(i)
      if (`type` == nextType) {
        return i
      } else ()
      i = i + 3
      `type` match {
        case InputEventQueue.SKIP => {
          i = i + q(i)
        }
        case InputEventQueue.KEY_DOWN => {
          i = i + 1
        }
        case InputEventQueue.KEY_UP => {
          i = i + 1
        }
        case InputEventQueue.KEY_TYPED => {
          i = i + 1
        }
        case InputEventQueue.TOUCH_DOWN => {
          i = i + 4
        }
        case InputEventQueue.TOUCH_UP => {
          i = i + 4
        }
        case InputEventQueue.TOUCH_DRAGGED => {
          i = i + 3
        }
        case InputEventQueue.MOUSE_MOVED => {
          i = i + 2
        }
        case InputEventQueue.SCROLLED => {
          i = i + 2
        }
        case _ => {
          throw new java.lang.RuntimeException()
        }
      }
    };  } }
    return -1
  }
  private def queueTime(time: scala.Long): scala.Unit = {
    this.queue.add((time >> 32).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
    this.queue.add(time.asInstanceOf[scala.Int].asInstanceOf[scala.Int])
  }
  def keyDown(keycode: scala.Int, time: scala.Long): scala.Boolean = {
    this.queue.add(InputEventQueue.KEY_DOWN)
    this.queueTime(time)
    this.queue.add(keycode)
    return false
  }
  def keyUp(keycode: scala.Int, time: scala.Long): scala.Boolean = {
    this.queue.add(InputEventQueue.KEY_UP)
    this.queueTime(time)
    this.queue.add(keycode)
    return false
  }
  def keyTyped(character: scala.Char, time: scala.Long): scala.Boolean = {
    this.queue.add(InputEventQueue.KEY_TYPED)
    this.queueTime(time)
    this.queue.add(character)
    return false
  }
  def touchDown(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int, time: scala.Long): scala.Boolean = {
    this.queue.add(InputEventQueue.TOUCH_DOWN)
    this.queueTime(time)
    this.queue.add(screenX)
    this.queue.add(screenY)
    this.queue.add(pointer)
    this.queue.add(button)
    return false
  }
  def touchUp(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, button: scala.Int, time: scala.Long): scala.Boolean = {
    this.queue.add(InputEventQueue.TOUCH_UP)
    this.queueTime(time)
    this.queue.add(screenX)
    this.queue.add(screenY)
    this.queue.add(pointer)
    this.queue.add(button)
    return false
  }
  def touchDragged(screenX: scala.Int, screenY: scala.Int, pointer: scala.Int, time: scala.Long): scala.Boolean = {
    { var i: scala.Int = this.next(InputEventQueue.TOUCH_DRAGGED, 0); while (i >= 0) { {
      if (this.queue.get(i + 5) == pointer) {
        this.queue.set(i, InputEventQueue.SKIP)
        this.queue.set(i + 3, 3)
      } else ()
    }; i = this.next(InputEventQueue.TOUCH_DRAGGED, i + 6) } }
    this.queue.add(InputEventQueue.TOUCH_DRAGGED)
    this.queueTime(time)
    this.queue.add(screenX)
    this.queue.add(screenY)
    this.queue.add(pointer)
    return false
  }
  def mouseMoved(screenX: scala.Int, screenY: scala.Int, time: scala.Long): scala.Boolean = {
    { var i: scala.Int = this.next(InputEventQueue.MOUSE_MOVED, 0); while (i >= 0) { {
      this.queue.set(i, InputEventQueue.SKIP)
      this.queue.set(i + 3, 2)
    }; i = this.next(InputEventQueue.MOUSE_MOVED, i + 5) } }
    this.queue.add(InputEventQueue.MOUSE_MOVED)
    this.queueTime(time)
    this.queue.add(screenX)
    this.queue.add(screenY)
    return false
  }
  def scrolled(amountX: scala.Float, amountY: scala.Float, time: scala.Long): scala.Boolean = {
    this.queue.add(InputEventQueue.SCROLLED)
    this.queueTime(time)
    this.queue.add(com.badlogic.gdx.utils.NumberUtils.floatToIntBits(amountX))
    this.queue.add(com.badlogic.gdx.utils.NumberUtils.floatToIntBits(amountY))
    return false
  }
  def getCurrentEventTime(): scala.Long = {
    return this.currentEventTime
  }
}
object InputEventQueue {
  private final val SKIP: scala.Int = -1
  private final val KEY_DOWN: scala.Int = 0
  private final val KEY_UP: scala.Int = 1
  private final val KEY_TYPED: scala.Int = 2
  private final val TOUCH_DOWN: scala.Int = 3
  private final val TOUCH_UP: scala.Int = 4
  private final val TOUCH_DRAGGED: scala.Int = 5
  private final val MOUSE_MOVED: scala.Int = 6
  private final val SCROLLED: scala.Int = 7
}