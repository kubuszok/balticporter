package com.badlogic.gdx

trait Files {
  def getFileHandle(path: java.lang.String, `type`: FileType): com.badlogic.gdx.files.FileHandle
  def classpath(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def internal(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def external(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def absolute(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def local(path: java.lang.String): com.badlogic.gdx.files.FileHandle
  def getExternalStoragePath(): java.lang.String
  def isExternalStorageAvailable(): scala.Boolean
  def getLocalStoragePath(): java.lang.String
  def isLocalStorageAvailable(): scala.Boolean
  sealed abstract class FileType
  object FileType {
    case object Classpath extends FileType
    case object Internal extends FileType
    case object External extends FileType
    case object Absolute extends FileType
    case object Local extends FileType
    def values(): Array[FileType] = Array(Classpath, Internal, External, Absolute, Local)
  }
}