package com.badlogic.gdx.graphics

object Colors {
  private final val map: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Color] = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Color]()
  def getColors(): com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.Color] = {
    return Colors.map
  }
  def get(name: java.lang.String): com.badlogic.gdx.graphics.Color = {
    return Colors.map.get(name)
  }
  def put(name: java.lang.String, color: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.graphics.Color = {
    return Colors.map.put(name, color)
  }
  def reset(): scala.Unit = {
    Colors.map.clear()
    Colors.map.put("CLEAR", com.badlogic.gdx.graphics.Color.CLEAR)
    Colors.map.put("CLEAR_WHITE", com.badlogic.gdx.graphics.Color.CLEAR_WHITE)
    Colors.map.put("BLACK", com.badlogic.gdx.graphics.Color.BLACK)
    Colors.map.put("WHITE", com.badlogic.gdx.graphics.Color.WHITE)
    Colors.map.put("LIGHT_GRAY", com.badlogic.gdx.graphics.Color.LIGHT_GRAY)
    Colors.map.put("GRAY", com.badlogic.gdx.graphics.Color.GRAY)
    Colors.map.put("DARK_GRAY", com.badlogic.gdx.graphics.Color.DARK_GRAY)
    Colors.map.put("BLUE", com.badlogic.gdx.graphics.Color.BLUE)
    Colors.map.put("NAVY", com.badlogic.gdx.graphics.Color.NAVY)
    Colors.map.put("ROYAL", com.badlogic.gdx.graphics.Color.ROYAL)
    Colors.map.put("SLATE", com.badlogic.gdx.graphics.Color.SLATE)
    Colors.map.put("SKY", com.badlogic.gdx.graphics.Color.SKY)
    Colors.map.put("CYAN", com.badlogic.gdx.graphics.Color.CYAN)
    Colors.map.put("TEAL", com.badlogic.gdx.graphics.Color.TEAL)
    Colors.map.put("GREEN", com.badlogic.gdx.graphics.Color.GREEN)
    Colors.map.put("CHARTREUSE", com.badlogic.gdx.graphics.Color.CHARTREUSE)
    Colors.map.put("LIME", com.badlogic.gdx.graphics.Color.LIME)
    Colors.map.put("FOREST", com.badlogic.gdx.graphics.Color.FOREST)
    Colors.map.put("OLIVE", com.badlogic.gdx.graphics.Color.OLIVE)
    Colors.map.put("YELLOW", com.badlogic.gdx.graphics.Color.YELLOW)
    Colors.map.put("GOLD", com.badlogic.gdx.graphics.Color.GOLD)
    Colors.map.put("GOLDENROD", com.badlogic.gdx.graphics.Color.GOLDENROD)
    Colors.map.put("ORANGE", com.badlogic.gdx.graphics.Color.ORANGE)
    Colors.map.put("BROWN", com.badlogic.gdx.graphics.Color.BROWN)
    Colors.map.put("TAN", com.badlogic.gdx.graphics.Color.TAN)
    Colors.map.put("FIREBRICK", com.badlogic.gdx.graphics.Color.FIREBRICK)
    Colors.map.put("RED", com.badlogic.gdx.graphics.Color.RED)
    Colors.map.put("SCARLET", com.badlogic.gdx.graphics.Color.SCARLET)
    Colors.map.put("CORAL", com.badlogic.gdx.graphics.Color.CORAL)
    Colors.map.put("SALMON", com.badlogic.gdx.graphics.Color.SALMON)
    Colors.map.put("PINK", com.badlogic.gdx.graphics.Color.PINK)
    Colors.map.put("MAGENTA", com.badlogic.gdx.graphics.Color.MAGENTA)
    Colors.map.put("PURPLE", com.badlogic.gdx.graphics.Color.PURPLE)
    Colors.map.put("VIOLET", com.badlogic.gdx.graphics.Color.VIOLET)
    Colors.map.put("MAROON", com.badlogic.gdx.graphics.Color.MAROON)
  }
}