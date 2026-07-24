package com.badlogic.gdx

trait Files {
  def getFileHandle(path: java.lang.String, `type`: com.badlogic.gdx.Files.FileType): com.badlogic.gdx.files.FileHandle
  def classpath(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def internal(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def external(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def absolute(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def local(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def getExternalStoragePath(): java.lang.String
  def isExternalStorageAvailable(): scala.Boolean
  def getLocalStoragePath(): java.lang.String
  def isLocalStorageAvailable(): scala.Boolean
}
object Files {
  sealed abstract class FileType {
    def name(): java.lang.String = this.toString()
  }
  object FileType {
    case object Classpath extends FileType
    case object Internal extends FileType
    case object External extends FileType
    case object Absolute extends FileType
    case object Local extends FileType
    def values(): scala.Array[FileType] = scala.Array(Classpath, Internal, External, Absolute, Local)
    def valueOf(name: java.lang.String): FileType = name match {
      case "Classpath" => Classpath
      case "Internal" => Internal
      case "External" => External
      case "Absolute" => Absolute
      case "Local" => Local
      case _ => throw new java.lang.IllegalArgumentException(name)
    }
  }
}