/*
 * Derived from guacamole v0.3.6 — https://github.com/crykn/guacamole
 * Original files: de/damios/guacamole/gdx/log/{Logger,LoggerService}.java
 * Copyright 2020 damios; licensed under the Apache License, Version 2.0
 *
 * The hand-written half of the libgdx-screenmanager port — see `package.scala`.
 */
package sge.screen.guacamole

/** A level-gated logger over `Gdx.app.log` with `String.format` message interpolation.
  *
  * The `args` parameter is a Scala `Object*` because that is what the generated callers now pass.
  * `CLAUDE.md` §1 is explicit that a port's hand-written half takes its shape from the emitted
  * caller rather than choosing one: `ENGINE-LIMITS.md` K6.5's third case made the engine stop
  * materialising a vararg pack at an EXTERNAL callee, and a `TypeRedirectTransform` target is
  * external by construction — so `LOG.debug(fmt, a, b)` now arrives as three arguments where it
  * used to arrive as `(fmt, Array(a, b))`. An `Array[Object]` formal was right for exactly as long
  * as the emitter packed, and is a compile error the moment it spreads.
  */
final class Logger private[guacamole] (className: String) {

  private val classPrefix: String = String.format("[%s]: ", className)

  def trace(message: String, args: java.lang.Object*): Unit = {
    if (LoggerService.isTraceEnabled()) sge.Gdx.app.debug("TRACE", formatted(message, args))

  }
  def debug(message: String, args: java.lang.Object*): Unit = {
    if (LoggerService.isDebugEnabled()) sge.Gdx.app.debug("DEBUG", formatted(message, args))

  }
  def info(message: String, args: java.lang.Object*): Unit = {
    if (LoggerService.isInfoEnabled()) sge.Gdx.app.log("INFO", formatted(message, args))

  }
  def warn(message: String, args: java.lang.Object*): Unit = {
    if (LoggerService.isWarnEnabled()) sge.Gdx.app.log("WARN", formatted(message, args))

  }
  def error(message: String, args: java.lang.Object*): Unit = {
    if (LoggerService.isErrorEnabled()) sge.Gdx.app.error("ERROR", formatted(message, args))

  }
  private def formatted(message: String, args: Seq[java.lang.Object]): String = {
    classPrefix + (if (args == null || args.isEmpty) message else String.format(message, args*))

/** The level gate and the `Logger` factory.
  *
  * The level is read off libGDX's own `Gdx.app.getLogLevel()`, which is where upstream reads it
  * from too — so a port that never calls `setLogLevel` still logs exactly what the application
  * asked for, and there is no second, shim-local source of truth to drift from it. A null `app`
  * (every headless unit test constructing a `ScreenManager` before a backend exists) disables
  * logging rather than throwing: upstream's own `isDebugEnabled` is a plain comparison and would
  * NPE there, and a shim that turns a log statement into a crash is worse than one that is quiet.
  */
  }
}
object LoggerService {

  def getLogger(clazz: Class[?]): Logger = new Logger(clazz.getName)

  private def level: Int = if (sge.Gdx.app == null) -1 else sge.Gdx.app.getLogLevel()

  def isErrorEnabled(): Boolean = level >= sge.Application.LOG_ERROR
  def isWarnEnabled(): Boolean  = level >= sge.Application.LOG_ERROR
  def isInfoEnabled(): Boolean  = level >= sge.Application.LOG_INFO
  def isDebugEnabled(): Boolean = level >= sge.Application.LOG_DEBUG
  def isTraceEnabled(): Boolean = level >= sge.Application.LOG_DEBUG

}