package com.badlogic.gdx.scenes.scene2d

trait EventListener {
  def handle(event: com.badlogic.gdx.scenes.scene2d.Event): scala.Boolean
}