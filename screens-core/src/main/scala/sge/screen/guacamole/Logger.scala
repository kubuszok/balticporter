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
  * The `args` parameter is an `Array[Object]` rather than a Scala `Object*` deliberately: the
  * engine emits a Java `T...` parameter as `Array[T]` and a call site as a positional
  * `scala.Array[java.lang.Object](…)` (see `TirEmitter.param`). Declared as varargs here, every
  * emitted `LOG.debug(msg, Array(…))` would be a type error — and the fix would be in the shim,
  * which is the wrong place: the shim's job is to match what the emitter produces.
  */
final class Logger private[guacamole] (className: String):

  private val classPrefix: String = String.format("[%s]: ", className)

  def trace(message: String, args: Array[Object]): Unit =
    if LoggerService.isTraceEnabled() then sge.Gdx.app.debug("TRACE", formatted(message, args))

  def debug(message: String, args: Array[Object]): Unit =
    if LoggerService.isDebugEnabled() then sge.Gdx.app.debug("DEBUG", formatted(message, args))

  def info(message: String, args: Array[Object]): Unit =
    if LoggerService.isInfoEnabled() then sge.Gdx.app.log("INFO", formatted(message, args))

  def warn(message: String, args: Array[Object]): Unit =
    if LoggerService.isWarnEnabled() then sge.Gdx.app.log("WARN", formatted(message, args))

  def error(message: String, args: Array[Object]): Unit =
    if LoggerService.isErrorEnabled() then sge.Gdx.app.error("ERROR", formatted(message, args))

  private def formatted(message: String, args: Array[Object]): String =
    classPrefix + (if args == null || args.isEmpty then message else String.format(message, args*))

/** The level gate and the `Logger` factory.
  *
  * The level is read off libGDX's own `Gdx.app.getLogLevel()`, which is where upstream reads it
  * from too — so a port that never calls `setLogLevel` still logs exactly what the application
  * asked for, and there is no second, shim-local source of truth to drift from it. A null `app`
  * (every headless unit test constructing a `ScreenManager` before a backend exists) disables
  * logging rather than throwing: upstream's own `isDebugEnabled` is a plain comparison and would
  * NPE there, and a shim that turns a log statement into a crash is worse than one that is quiet.
  */
object LoggerService:

  def getLogger(clazz: Class[?]): Logger = new Logger(clazz.getName)

  private def level: Int = if sge.Gdx.app == null then -1 else sge.Gdx.app.getLogLevel()

  def isErrorEnabled(): Boolean = level >= sge.Application.LOG_ERROR
  def isWarnEnabled(): Boolean  = level >= sge.Application.LOG_ERROR
  def isInfoEnabled(): Boolean  = level >= sge.Application.LOG_INFO
  def isDebugEnabled(): Boolean = level >= sge.Application.LOG_DEBUG
  def isTraceEnabled(): Boolean = level >= sge.Application.LOG_DEBUG
