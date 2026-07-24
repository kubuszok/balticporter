package com.badlogic.gdx

object Version {
  final val VERSION: java.lang.String = "1.14.1".toString()
  var MAJOR: scala.Int = 0
  var MINOR: scala.Int = 0
  var REVISION: scala.Int = 0
  def isHigher(major: scala.Int, minor: scala.Int, revision: scala.Int): scala.Boolean = {
    return Version.isHigherEqual(major, minor, revision + 1)
  }
  def isHigherEqual(major: scala.Int, minor: scala.Int, revision: scala.Int): scala.Boolean = {
    if (Version.MAJOR != major) {
      return Version.MAJOR > major
    } else ()
    if (Version.MINOR != minor) {
      return Version.MINOR > minor
    } else ()
    return Version.REVISION >= revision
  }
  def isLower(major: scala.Int, minor: scala.Int, revision: scala.Int): scala.Boolean = {
    return Version.isLowerEqual(major, minor, revision - 1)
  }
  def isLowerEqual(major: scala.Int, minor: scala.Int, revision: scala.Int): scala.Boolean = {
    if (Version.MAJOR != major) {
      return Version.MAJOR < major
    } else ()
    if (Version.MINOR != minor) {
      return Version.MINOR < minor
    } else ()
    return Version.REVISION <= revision
  }
}