package com.badlogic.gdx.graphics.g3d.utils

trait RenderableSorter {
  def sort(camera: com.badlogic.gdx.graphics.Camera, renderables: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.Renderable]): scala.Unit
}