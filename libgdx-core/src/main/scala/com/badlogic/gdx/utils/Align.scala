package com.badlogic.gdx.utils

object Align {
  final val center: scala.Int = 1 << 0
  final val top: scala.Int = 1 << 1
  final val bottom: scala.Int = 1 << 2
  final val left: scala.Int = 1 << 3
  final val right: scala.Int = 1 << 4
  final val topLeft: scala.Int = Align.top | Align.left
  final val topRight: scala.Int = Align.top | Align.right
  final val bottomLeft: scala.Int = Align.bottom | Align.left
  final val bottomRight: scala.Int = Align.bottom | Align.right
  final def isLeft(align: scala.Int): scala.Boolean = {
    return (align & Align.left) != 0
  }
  final def isRight(align: scala.Int): scala.Boolean = {
    return (align & Align.right) != 0
  }
  final def isTop(align: scala.Int): scala.Boolean = {
    return (align & Align.top) != 0
  }
  final def isBottom(align: scala.Int): scala.Boolean = {
    return (align & Align.bottom) != 0
  }
  final def isCenterVertical(align: scala.Int): scala.Boolean = {
    return ((align & Align.top) == 0) && ((align & Align.bottom) == 0)
  }
  final def isCenterHorizontal(align: scala.Int): scala.Boolean = {
    return ((align & Align.left) == 0) && ((align & Align.right) == 0)
  }
  def toString(align: scala.Int): java.lang.String = {
    val buffer: java.lang.StringBuilder = new java.lang.StringBuilder(13)
    if ((align & Align.top) != 0) {
      buffer.append("top,")
    } else {
      if ((align & Align.bottom) != 0) {
        buffer.append("bottom,")
      } else {
        buffer.append("center,")
      }
    }
    if ((align & Align.left) != 0) {
      buffer.append("left")
    } else {
      if ((align & Align.right) != 0) {
        buffer.append("right")
      } else {
        buffer.append("center")
      }
    }
    return buffer.toString()
  }
}