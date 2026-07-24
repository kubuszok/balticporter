package com.badlogic.gdx.files

abstract class FileHandleStream extends com.badlogic.gdx.files.FileHandle {
  def this(path: java.lang.String) = {
    this()
  }
  def isDirectory(): scala.Boolean = {
    return false
  }
  def length(): scala.Long = {
    return 0
  }
  def exists(): scala.Boolean = {
    return true
  }
  def child(name: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    throw new java.lang.UnsupportedOperationException()
  }
  def sibling(name: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    throw new java.lang.UnsupportedOperationException()
  }
  def parent(): com.badlogic.gdx.files.FileHandle = {
    throw new java.lang.UnsupportedOperationException()
  }
  def read(): java.io.InputStream = {
    throw new java.lang.UnsupportedOperationException()
  }
  def write(overwrite: scala.Boolean): java.io.OutputStream = {
    throw new java.lang.UnsupportedOperationException()
  }
  def list(): scala.Array[com.badlogic.gdx.files.FileHandle] = {
    throw new java.lang.UnsupportedOperationException()
  }
  def mkdirs(): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  def delete(): scala.Boolean = {
    throw new java.lang.UnsupportedOperationException()
  }
  def deleteDirectory(): scala.Boolean = {
    throw new java.lang.UnsupportedOperationException()
  }
  def copyTo(dest: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  def moveTo(dest: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  def emptyDirectory(): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  def emptyDirectory(preserveTree: scala.Boolean): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
}