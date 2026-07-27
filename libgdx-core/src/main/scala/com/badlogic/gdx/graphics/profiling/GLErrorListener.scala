package com.badlogic.gdx.graphics.profiling

trait GLErrorListener {
  def onError(error: scala.Int): scala.Unit
}
object GLErrorListener {
  final val LOGGING_LISTENER: GLErrorListener = new GLErrorListener() {
    override def onError(error: scala.Int): scala.Unit = {
      var place: java.lang.String = null
      try {
        val stack: scala.Array[java.lang.StackTraceElement] = java.lang.Thread.currentThread().getStackTrace();
        { var i: scala.Int = 0; while (i < stack.length) { {
          if ("check".equals(stack(i).getMethodName())) {
            if ((i + 1) < stack.length) {
              val glMethod: java.lang.StackTraceElement = stack(i + 1)
              place = glMethod.getMethodName()
            } else ()
            /* break */ ()
          } else ()
        }; i = i + 1 } }
      } catch {
        case ignored: java.lang.Exception => {
          ()
        }
      }
      if (place != null) {
        com.badlogic.gdx.Gdx.app.error("GLProfiler", (("Error " + com.badlogic.gdx.graphics.profiling.GLInterceptor.resolveErrorNumber(error)) + " from ") + place)
      } else {
        com.badlogic.gdx.Gdx.app.error("GLProfiler", ("Error " + com.badlogic.gdx.graphics.profiling.GLInterceptor.resolveErrorNumber(error)) + " at: ", new java.lang.Exception())
      }
    }
  }
  final val THROWING_LISTENER: GLErrorListener = new GLErrorListener() {
    override def onError(error: scala.Int): scala.Unit = {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("GLProfiler: Got GL error " + com.badlogic.gdx.graphics.profiling.GLInterceptor.resolveErrorNumber(error))
    }
  }
}