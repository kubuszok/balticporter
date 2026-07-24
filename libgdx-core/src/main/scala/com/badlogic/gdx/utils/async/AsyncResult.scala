package com.badlogic.gdx.utils.async

class AsyncResult[T] {
  private var future: java.util.concurrent.Future[T] = null.asInstanceOf[java.util.concurrent.Future[T]]
  def this(future: java.util.concurrent.Future[T]) = {
    this()
    this.future = future
  }
  def isDone(): scala.Boolean = {
    return this.future.isDone()
  }
  def get(): T = {
    try {
      return this.future.get()
    } catch {
      case ex: java.lang.InterruptedException => {
        return null.asInstanceOf[T]
      }
      case ex: java.util.concurrent.ExecutionException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException(ex.getCause())
      }
    }
  }
}