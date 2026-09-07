/*
 * Port replacement for sge's JVM LogPlatform (sge/src/main/scalajvm/sge/utils/LogPlatform.scala),
 * which logs through scribe. The port carries no logging dependency: the same seven members over
 * the JDK's System.Logger (PROGRESS.md §13.30 step 1, ADJUSTMENTS.tsv).
 */
package sge
package utils

private[sge] object LogPlatform {
  private val logger: System.Logger = System.getLogger("sge")
  import System.Logger.Level

  def info(msg: => String): Unit  = if logger.isLoggable(Level.INFO) then logger.log(Level.INFO, msg)
  def warn(msg: => String): Unit  = if logger.isLoggable(Level.WARNING) then logger.log(Level.WARNING, msg)
  def error(msg: => String): Unit = if logger.isLoggable(Level.ERROR) then logger.log(Level.ERROR, msg)
  def error(msg: => String, t: Throwable): Unit =
    if logger.isLoggable(Level.ERROR) then logger.log(Level.ERROR, msg, t)
  def debug(msg: => String): Unit = if logger.isLoggable(Level.DEBUG) then logger.log(Level.DEBUG, msg)
  def trace(msg: => String): Unit = if logger.isLoggable(Level.TRACE) then logger.log(Level.TRACE, msg)
  def isDebugEnabled: Boolean     = logger.isLoggable(Level.DEBUG)
}
