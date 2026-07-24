package com.badlogic.gdx.utils

trait BaseJsonReader {
  def parse(input: java.io.InputStream): com.badlogic.gdx.utils.JsonValue
  def parse(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.JsonValue
}