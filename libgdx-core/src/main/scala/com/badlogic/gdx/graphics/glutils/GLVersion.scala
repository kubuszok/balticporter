package com.badlogic.gdx.graphics.glutils

class GLVersion {
  private var majorVersion: scala.Int = 0
  private var minorVersion: scala.Int = 0
  private var releaseVersion: scala.Int = 0
  private var versionString: java.lang.String = null.asInstanceOf[java.lang.String]
  private var vendorString: java.lang.String = null.asInstanceOf[java.lang.String]
  private var rendererString: java.lang.String = null.asInstanceOf[java.lang.String]
  private var `type`: com.badlogic.gdx.graphics.glutils.GLVersion.Type = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.GLVersion.Type]
  private final val TAG: java.lang.String = "GLVersion"
  def this(appType: com.badlogic.gdx.Application.ApplicationType, versionString: java.lang.String, vendorString: java.lang.String, rendererString: java.lang.String) = {
    this()
    if (appType == com.badlogic.gdx.Application.ApplicationType.Android) {
      this.`type` = com.badlogic.gdx.graphics.glutils.GLVersion.Type.GLES
    } else {
      if (appType == com.badlogic.gdx.Application.ApplicationType.iOS) {
        this.`type` = com.badlogic.gdx.graphics.glutils.GLVersion.Type.GLES
      } else {
        if (appType == com.badlogic.gdx.Application.ApplicationType.Desktop) {
          this.`type` = com.badlogic.gdx.graphics.glutils.GLVersion.Type.OpenGL
        } else {
          if (appType == com.badlogic.gdx.Application.ApplicationType.Applet) {
            this.`type` = com.badlogic.gdx.graphics.glutils.GLVersion.Type.OpenGL
          } else {
            if (appType == com.badlogic.gdx.Application.ApplicationType.WebGL) {
              this.`type` = com.badlogic.gdx.graphics.glutils.GLVersion.Type.WebGL
            } else {
              this.`type` = com.badlogic.gdx.graphics.glutils.GLVersion.Type.NONE
            }
          }
        }
      }
    }
    if (this.`type` == com.badlogic.gdx.graphics.glutils.GLVersion.Type.GLES) {
      this.extractVersion("OpenGL ES (\\d(\\.\\d){0,2})", versionString)
    } else {
      if (this.`type` == com.badlogic.gdx.graphics.glutils.GLVersion.Type.WebGL) {
        this.extractVersion("WebGL (\\d(\\.\\d){0,2})", versionString)
      } else {
        if (this.`type` == com.badlogic.gdx.graphics.glutils.GLVersion.Type.OpenGL) {
          this.extractVersion("(\\d(\\.\\d){0,2})", versionString)
        } else {
          this.majorVersion = -1
          this.minorVersion = -1
          this.releaseVersion = -1
          vendorString = ""
          rendererString = ""
        }
      }
    }
    this.versionString = versionString
    this.vendorString = vendorString
    this.rendererString = rendererString
  }
  private def extractVersion(patternString: java.lang.String, versionString: java.lang.String): scala.Unit = {
    val pattern: java.util.regex.Pattern = java.util.regex.Pattern.compile(patternString)
    val matcher: java.util.regex.Matcher = pattern.matcher(versionString)
    val found: scala.Boolean = matcher.find()
    if (found) {
      val result: java.lang.String = matcher.group(1)
      val resultSplit: scala.Array[java.lang.String] = result.split("\\.")
      this.majorVersion = this.parseInt(resultSplit(0), 2)
      this.minorVersion = if (resultSplit.length < 2) 0 else this.parseInt(resultSplit(1), 0)
      this.releaseVersion = if (resultSplit.length < 3) 0 else this.parseInt(resultSplit(2), 0)
    } else {
      com.badlogic.gdx.Gdx.app.log(this.TAG, "Invalid version string: " + versionString)
      this.majorVersion = 2
      this.minorVersion = 0
      this.releaseVersion = 0
    }
  }
  private def parseInt(v: java.lang.String, defaultValue: scala.Int): scala.Int = {
    try {
      return java.lang.Integer.parseInt(v)
    } catch {
      case nfe: java.lang.NumberFormatException => {
        com.badlogic.gdx.Gdx.app.error("libGDX GL", (("Error parsing number: " + v) + ", assuming: ") + defaultValue)
        return defaultValue
      }
    }
  }
  def getType(): com.badlogic.gdx.graphics.glutils.GLVersion.Type = {
    return this.`type`
  }
  def getMajorVersion(): scala.Int = {
    return this.majorVersion
  }
  def getMinorVersion(): scala.Int = {
    return this.minorVersion
  }
  def getReleaseVersion(): scala.Int = {
    return this.releaseVersion
  }
  def getVersionString(): java.lang.String = {
    return this.versionString
  }
  def getVendorString(): java.lang.String = {
    return this.vendorString
  }
  def getRendererString(): java.lang.String = {
    return this.rendererString
  }
  def isVersionEqualToOrHigher(testMajorVersion: scala.Int, testMinorVersion: scala.Int): scala.Boolean = {
    return (this.majorVersion > testMajorVersion) || ((this.majorVersion == testMajorVersion) && (this.minorVersion >= testMinorVersion))
  }
  def getDebugVersionString(): java.lang.String = {
    return ((((((((((((("Type: " + this.`type`) + "\n") + "Version: ") + this.majorVersion) + ":") + this.minorVersion) + ":") + this.releaseVersion) + "\n") + "Vendor: ") + this.vendorString) + "\n") + "Renderer: ") + this.rendererString
  }
}
object GLVersion {
  sealed abstract class Type
  object Type {
    case object OpenGL extends Type
    case object GLES extends Type
    case object WebGL extends Type
    case object NONE extends Type
    def values(): Array[Type] = Array(OpenGL, GLES, WebGL, NONE)
  }
}