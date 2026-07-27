package com.badlogic.gdx.utils.async

class AsyncExecutor(maxConcurrent: scala.Int, name: java.lang.String) extends com.badlogic.gdx.utils.Disposable {
  private var executor: java.util.concurrent.ExecutorService = null.asInstanceOf[java.util.concurrent.ExecutorService]
  def this(maxConcurrent: scala.Int) = {
    this(maxConcurrent, "AsyncExecutor-Thread")
  }
  this.executor = java.util.concurrent.Executors.newFixedThreadPool(maxConcurrent, new java.util.concurrent.ThreadFactory() {
    override def newThread(r: java.lang.Runnable): java.lang.Thread = {
      val thread: java.lang.Thread = new java.lang.Thread(r, name)
      thread.setDaemon(true)
      return thread
    }
  })
  def submit[T](task: com.badlogic.gdx.utils.async.AsyncTask[T]): com.badlogic.gdx.utils.async.AsyncResult[T] = {
    if (this.executor.isShutdown()) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot run tasks on an executor that has been shutdown (disposed)")
    } else ()
    return new com.badlogic.gdx.utils.async.AsyncResult[T](this.executor.submit(new java.util.concurrent.Callable[T]() {
      override def call(): T = {
        return task.call().asInstanceOf[T]
      }
    })).asInstanceOf[com.badlogic.gdx.utils.async.AsyncResult[T]]
  }
  def dispose(): scala.Unit = {
    this.executor.shutdown()
    try {
      this.executor.awaitTermination(java.lang.Long.MAX_VALUE, java.util.concurrent.TimeUnit.SECONDS)
    } catch {
      case e: java.lang.InterruptedException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Couldn't shutdown loading thread", e)
      }
    }
  }
}