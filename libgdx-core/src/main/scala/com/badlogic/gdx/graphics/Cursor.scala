package com.badlogic.gdx.graphics

trait Cursor extends com.badlogic.gdx.utils.Disposable
object Cursor {
  sealed abstract class SystemCursor
  object SystemCursor {
    case object Arrow extends SystemCursor
    case object Ibeam extends SystemCursor
    case object Crosshair extends SystemCursor
    case object Hand extends SystemCursor
    case object HorizontalResize extends SystemCursor
    case object VerticalResize extends SystemCursor
    case object NWSEResize extends SystemCursor
    case object NESWResize extends SystemCursor
    case object AllResize extends SystemCursor
    case object NotAllowed extends SystemCursor
    case object None extends SystemCursor
    def values(): Array[SystemCursor] = Array(Arrow, Ibeam, Crosshair, Hand, HorizontalResize, VerticalResize, NWSEResize, NESWResize, AllResize, NotAllowed, None)
  }
}