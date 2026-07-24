package com.badlogic.gdx.utils

class TextFormatter {
  private var messageFormat: java.text.MessageFormat = null.asInstanceOf[java.text.MessageFormat]
  private var buffer: java.lang.StringBuilder = null.asInstanceOf[java.lang.StringBuilder]
  def this(locale: java.util.Locale, useMessageFormat: scala.Boolean) = {
    this()
    this.buffer = new java.lang.StringBuilder()
    if (useMessageFormat) {
      this.messageFormat = new java.text.MessageFormat("", locale)
    } else ()
  }
  def format(pattern: java.lang.String, args: scala.Array[java.lang.Object]): java.lang.String = {
    if (this.messageFormat != null) {
      this.messageFormat.applyPattern(this.replaceEscapeChars(pattern))
      return this.messageFormat.format(args)
    } else ()
    return this.simpleFormat(pattern, args)
  }
  private def replaceEscapeChars(pattern: java.lang.String): java.lang.String = {
    this.buffer.setLength(0)
    var changed: scala.Boolean = false
    val len: scala.Int = pattern.length();
    { var i: scala.Int = 0; while (i < len) { {
      val ch: scala.Char = pattern.charAt(i)
      if (ch == '\'') {
        changed = true
        this.buffer.append("''")
      } else {
        if (ch == '{') {
          var j: scala.Int = i + 1
          while ((j < len) && (pattern.charAt(j) == '{')) {
            j = j + 1
          }
          var escaped: scala.Int = (j - i) / 2
          if (escaped > 0) {
            changed = true
            this.buffer.append('\'')
            while ({ {
              this.buffer.append('{')
            }; { escaped -= 1; escaped } > 0 }) ()
            this.buffer.append('\'')
          } else ()
          if (((j - i) % 2) != 0) {
            this.buffer.append('{')
          } else ()
          i = j - 1
        } else {
          this.buffer.append(ch)
        }
      }
    }; i = i + 1 } }
    return if (changed) this.buffer.toString() else pattern
  }
  private def simpleFormat(pattern: java.lang.String, args: scala.Array[java.lang.Object]): java.lang.String = {
    this.buffer.setLength(0)
    var changed: scala.Boolean = false
    var placeholder: scala.Int = -1
    val patternLength: scala.Int = pattern.length();
    { var i: scala.Int = 0; while (i < patternLength) { {
      val ch: scala.Char = pattern.charAt(i)
      if (placeholder < 0) {
        if (ch == '{') {
          changed = true
          if (((i + 1) < patternLength) && (pattern.charAt(i + 1) == '{')) {
            this.buffer.append(ch)
            i = i + 1
          } else {
            placeholder = 0
          }
        } else {
          this.buffer.append(ch)
        }
      } else {
        if (ch == '}') {
          if (placeholder >= args.length) {
            throw new java.lang.IllegalArgumentException("Argument index out of bounds: " + placeholder)
          } else ()
          if (pattern.charAt(i - 1) == '{') {
            throw new java.lang.IllegalArgumentException("Missing argument index after a left curly brace")
          } else ()
          if (args(placeholder) == null) {
            this.buffer.append("null")
          } else {
            this.buffer.append(args(placeholder).toString())
          }
          placeholder = -1
        } else {
          if ((ch < '0') || (ch > '9')) {
            throw new java.lang.IllegalArgumentException(("Unexpected '" + ch) + "' while parsing argument index")
          } else ()
          placeholder = (placeholder * 10) + (ch - '0')
        }
      }
    }; i = i + 1 } }
    if (placeholder >= 0) {
      throw new java.lang.IllegalArgumentException("Unmatched braces in the pattern.")
    } else ()
    return if (changed) this.buffer.toString() else pattern
  }
}