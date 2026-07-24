package com.badlogic.gdx.utils

class Timer {
  final val tasks: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Timer.Task] = new com.badlogic.gdx.utils.Array(false, 8)
  var stopTimeMillis: scala.Long = 0L
  this.start()
  def postTask(task: com.badlogic.gdx.utils.Timer.Task): com.badlogic.gdx.utils.Timer.Task = {
    return this.scheduleTask(task, 0, 0, 0)
  }
  def scheduleTask(task: com.badlogic.gdx.utils.Timer.Task, delaySeconds: scala.Float): com.badlogic.gdx.utils.Timer.Task = {
    return this.scheduleTask(task, delaySeconds, 0, 0)
  }
  def scheduleTask(task: com.badlogic.gdx.utils.Timer.Task, delaySeconds: scala.Float, intervalSeconds: scala.Float): com.badlogic.gdx.utils.Timer.Task = {
    return this.scheduleTask(task, delaySeconds, intervalSeconds, -1)
  }
  def scheduleTask(task: com.badlogic.gdx.utils.Timer.Task, delaySeconds: scala.Float, intervalSeconds: scala.Float, repeatCount: scala.Int): com.badlogic.gdx.utils.Timer.Task = {
    Timer.threadLock.synchronized {
      this.synchronized {
        task.synchronized {
          if (task.timer != null) {
            throw new java.lang.IllegalArgumentException("The same task may not be scheduled twice.")
          } else ()
          task.timer = this
          val timeMillis: scala.Long = java.lang.System.nanoTime() / 1000000
          var executeTimeMillis: scala.Long = timeMillis + (delaySeconds * 1000).asInstanceOf[scala.Long]
          if (Timer.thread$field.pauseTimeMillis > 0) {
            executeTimeMillis = executeTimeMillis - (timeMillis - Timer.thread$field.pauseTimeMillis)
          } else ()
          task.executeTimeMillis = executeTimeMillis
          task.intervalMillis = (intervalSeconds * 1000).asInstanceOf[scala.Long].asInstanceOf[scala.Long]
          task.repeatCount = repeatCount
          this.tasks.add(task)
        }
      }
      Timer.threadLock.notifyAll()
    }
    return task
  }
  def stop(): scala.Unit = {
    Timer.threadLock.synchronized {
      if (Timer.thread().instances.removeValue(this, true)) {
        this.stopTimeMillis = java.lang.System.nanoTime() / 1000000
      } else ()
    }
  }
  def start(): scala.Unit = {
    Timer.threadLock.synchronized {
      val thread: com.badlogic.gdx.utils.Timer.TimerThread = Timer.thread()
      val instances: com.badlogic.gdx.utils.Array[Timer] = thread.instances
      if (instances.contains(this, true)) {
        return
      } else ()
      instances.add(this)
      if (this.stopTimeMillis > 0) {
        this.delay((java.lang.System.nanoTime() / 1000000) - this.stopTimeMillis)
        this.stopTimeMillis = 0
      } else ()
      Timer.threadLock.notifyAll()
    }
  }
  def clear(): scala.Unit = {
    Timer.threadLock.synchronized {
      val thread: com.badlogic.gdx.utils.Timer.TimerThread = Timer.thread()
      this.synchronized {
        thread.postedTasks.synchronized {
          { var i: scala.Int = 0; val n: scala.Int = this.tasks.size; while (i < n) { {
            val task: com.badlogic.gdx.utils.Timer.Task = this.tasks.get(i)
            thread.removePostedTask(task)
            task.reset()
          }; i = i + 1 } }
        }
        this.tasks.clear()
      }
    }
  }
  def isEmpty(): scala.Boolean = {
    return this.tasks.size == 0
  }
  def update(thread: com.badlogic.gdx.utils.Timer.TimerThread, timeMillis: scala.Long, waitMillis$arg: scala.Long): scala.Long = {
    var waitMillis: scala.Long = waitMillis$arg;
    { var i: scala.Int = 0; var n: scala.Int = this.tasks.size; while (i < n) { {
      val task: com.badlogic.gdx.utils.Timer.Task = this.tasks.get(i)
      task.synchronized {
        if (task.executeTimeMillis > timeMillis) {
          waitMillis = java.lang.Math.min(waitMillis, task.executeTimeMillis - timeMillis)
          /* continue */ ()
        } else ()
        if (task.repeatCount == 0) {
          task.timer = null
          this.tasks.removeIndex(i)
          i = i - 1
          n = n - 1
        } else {
          task.executeTimeMillis = timeMillis + task.intervalMillis
          waitMillis = java.lang.Math.min(waitMillis, task.intervalMillis)
          if (task.repeatCount > 0) {
            task.repeatCount = task.repeatCount - 1
          } else ()
        }
        thread.addPostedTask(task)
      }
    }; i = i + 1 } }
    return waitMillis
  }
  def delay(delayMillis: scala.Long): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.tasks.size; while (i < n) { {
      val task: com.badlogic.gdx.utils.Timer.Task = this.tasks.get(i)
      task.synchronized {
        task.executeTimeMillis = task.executeTimeMillis + delayMillis
      }
    }; i = i + 1 } }
  }
}
object Timer {
  final val threadLock: java.lang.Object = new java.lang.Object()
  var thread$field: com.badlogic.gdx.utils.Timer.TimerThread = null.asInstanceOf[com.badlogic.gdx.utils.Timer.TimerThread]
  def instance(): Timer = {
    Timer.threadLock.synchronized {
      val thread: com.badlogic.gdx.utils.Timer.TimerThread = Timer.thread()
      if (thread.instance == null) {
        thread.instance = new Timer()
      } else ()
      return thread.instance
    }
  }
  def thread(): com.badlogic.gdx.utils.Timer.TimerThread = {
    Timer.threadLock.synchronized {
      if ((Timer.thread$field == null) || (Timer.thread$field.files != com.badlogic.gdx.Gdx.files)) {
        if (Timer.thread$field != null) {
          Timer.thread$field.dispose()
        } else ()
        Timer.thread$field = new com.badlogic.gdx.utils.Timer.TimerThread()
      } else ()
      return Timer.thread$field
    }
  }
  def post(task: com.badlogic.gdx.utils.Timer.Task): com.badlogic.gdx.utils.Timer.Task = {
    return Timer.instance().postTask(task)
  }
  def schedule(task: com.badlogic.gdx.utils.Timer.Task, delaySeconds: scala.Float): com.badlogic.gdx.utils.Timer.Task = {
    return Timer.instance().scheduleTask(task, delaySeconds)
  }
  def schedule(task: com.badlogic.gdx.utils.Timer.Task, delaySeconds: scala.Float, intervalSeconds: scala.Float): com.badlogic.gdx.utils.Timer.Task = {
    return Timer.instance().scheduleTask(task, delaySeconds, intervalSeconds)
  }
  def schedule(task: com.badlogic.gdx.utils.Timer.Task, delaySeconds: scala.Float, intervalSeconds: scala.Float, repeatCount: scala.Int): com.badlogic.gdx.utils.Timer.Task = {
    return Timer.instance().scheduleTask(task, delaySeconds, intervalSeconds, repeatCount)
  }
  abstract class Task extends java.lang.Runnable {
    var app: com.badlogic.gdx.Application = null.asInstanceOf[com.badlogic.gdx.Application]
    var executeTimeMillis: scala.Long = 0L
    var intervalMillis: scala.Long = 0L
    var repeatCount: scala.Int = 0
    var timer: Timer = null.asInstanceOf[Timer]
    this.app = com.badlogic.gdx.Gdx.app
    if (this.app == null) {
      throw new java.lang.IllegalStateException("Gdx.app not available.")
    } else ()
    def run(): scala.Unit
    def cancel(): scala.Unit = {
      Timer.threadLock.synchronized {
        Timer.thread().removePostedTask(this)
        val timer: Timer = this.timer
        if (timer != null) {
          timer.synchronized {
            timer.tasks.removeValue(this, true)
            this.reset()
          }
        } else {
          this.reset()
        }
      }
    }
    def reset(): scala.Unit = {
      this.executeTimeMillis = 0
      this.timer = null
    }
    def isScheduled(): scala.Boolean = {
      return this.timer != null
    }
    def getExecuteTimeMillis(): scala.Long = {
      return this.executeTimeMillis
    }
  }
  class TimerThread extends java.lang.Runnable with com.badlogic.gdx.LifecycleListener {
    var files: com.badlogic.gdx.Files = null.asInstanceOf[com.badlogic.gdx.Files]
    var app: com.badlogic.gdx.Application = null.asInstanceOf[com.badlogic.gdx.Application]
    final val instances: com.badlogic.gdx.utils.Array[Timer] = new com.badlogic.gdx.utils.Array(1)
    var instance: Timer = null.asInstanceOf[Timer]
    var pauseTimeMillis: scala.Long = 0L
    final val postedTasks: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Timer.Task] = new com.badlogic.gdx.utils.Array(2)
    final val runTasks: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Timer.Task] = new com.badlogic.gdx.utils.Array(2)
    final val runPostedTasks$field: java.lang.Runnable = new java.lang.Runnable()
    val thread: java.lang.Thread = new java.lang.Thread(this, "Timer")
    this.files = com.badlogic.gdx.Gdx.files
    this.app = com.badlogic.gdx.Gdx.app
    this.app.addLifecycleListener(this)
    this.resume()
    thread.setDaemon(true)
    thread.start()
    def run(): scala.Unit = {
      while (true) {
        Timer.threadLock.synchronized {
          if ((Timer.thread$field != this) || (this.files != com.badlogic.gdx.Gdx.files)) {
            /* break */ ()
          } else ()
          var waitMillis: scala.Long = 5000
          if (this.pauseTimeMillis == 0) {
            val timeMillis: scala.Long = java.lang.System.nanoTime() / 1000000;
            { var i: scala.Int = 0; val n: scala.Int = this.instances.size; while (i < n) { {
              try {
                waitMillis = this.instances.get(i).update(this, timeMillis, waitMillis)
              } catch {
                case ex: java.lang.Throwable => {
                  throw new com.badlogic.gdx.utils.GdxRuntimeException("Task failed: " + this.instances.get(i).getClass().getName(), ex)
                }
              }
            }; i = i + 1 } }
          } else ()
          if ((Timer.thread$field != this) || (this.files != com.badlogic.gdx.Gdx.files)) {
            /* break */ ()
          } else ()
          try {
            if (waitMillis > 0) {
              Timer.threadLock.`wait`(waitMillis)
            } else ()
          } catch {
            case ignored: java.lang.InterruptedException => {
              ()
            }
          }
        }
      }
      this.dispose()
    }
    def runPostedTasks(): scala.Unit = {
      this.postedTasks.synchronized {
        this.runTasks.addAll(this.postedTasks)
        this.postedTasks.clear()
      }
      val items: scala.Array[java.lang.Object] = this.runTasks.items.asInstanceOf[scala.Array[java.lang.Object]];
      { var i: scala.Int = 0; val n: scala.Int = this.runTasks.size; while (i < n) { {
        items(i).asInstanceOf[com.badlogic.gdx.utils.Timer.Task].run()
      }; i = i + 1 } }
      this.runTasks.clear()
    }
    def addPostedTask(task: com.badlogic.gdx.utils.Timer.Task): scala.Unit = {
      this.postedTasks.synchronized {
        if (this.postedTasks.isEmpty()) {
          task.app.postRunnable(this.runPostedTasks$field)
        } else ()
        this.postedTasks.add(task)
      }
    }
    def removePostedTask(task: com.badlogic.gdx.utils.Timer.Task): scala.Unit = {
      this.postedTasks.synchronized {
        val items: scala.Array[java.lang.Object] = this.postedTasks.items.asInstanceOf[scala.Array[java.lang.Object]];
        { var i: scala.Int = this.postedTasks.size - 1; while (i >= 0) { {
          if (items(i) == task) {
            this.postedTasks.removeIndex(i)
          } else ()
        }; i = i - 1 } }
      }
    }
    def resume(): scala.Unit = {
      Timer.threadLock.synchronized {
        val delayMillis: scala.Long = (java.lang.System.nanoTime() / 1000000) - this.pauseTimeMillis;
        { var i: scala.Int = 0; val n: scala.Int = this.instances.size; while (i < n) { {
          this.instances.get(i).delay(delayMillis)
        }; i = i + 1 } }
        this.pauseTimeMillis = 0
        Timer.threadLock.notifyAll()
      }
    }
    def pause(): scala.Unit = {
      Timer.threadLock.synchronized {
        this.pauseTimeMillis = java.lang.System.nanoTime() / 1000000
        Timer.threadLock.notifyAll()
      }
    }
    def dispose(): scala.Unit = {
      Timer.threadLock.synchronized {
        this.postedTasks.synchronized {
          this.postedTasks.clear()
        }
        if (Timer.thread$field == this) {
          Timer.thread$field = null
        } else ()
        this.instances.clear()
        Timer.threadLock.notifyAll()
      }
      this.app.removeLifecycleListener(this)
    }
  }
}