package com.badlogic.gdx.graphics

trait Cursor extends com.badlogic.gdx.utils.Disposable
object Cursor {
  sealed abstract class SystemCursor {
    def name(): java.lang.String = this.toString()
  }
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
    def values(): scala.Array[SystemCursor] = scala.Array(Arrow, Ibeam, Crosshair, Hand, HorizontalResize, VerticalResize, NWSEResize, NESWResize, AllResize, NotAllowed, None)
    def valueOf(name: java.lang.String): SystemCursor = name match {
      case "Arrow" => Arrow
      case "Ibeam" => Ibeam
      case "Crosshair" => Crosshair
      case "Hand" => Hand
      case "HorizontalResize" => HorizontalResize
      case "VerticalResize" => VerticalResize
      case "NWSEResize" => NWSEResize
      case "NESWResize" => NESWResize
      case "AllResize" => AllResize
      case "NotAllowed" => NotAllowed
      case "None" => None
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}