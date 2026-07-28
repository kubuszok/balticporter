package com.badlogic.gdx.files

abstract class FileHandleStream(path$p: java.lang.String) extends com.badlogic.gdx.files.FileHandle(new java.io.File(path$p), com.badlogic.gdx.Files.FileType.Absolute) {
  override def isDirectory(): scala.Boolean = {
    return false
  }
  override def length(): scala.Long = {
    return 0
  }
  override def exists(): scala.Boolean = {
    return true
  }
  override def child(name: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def sibling(name: java.lang.String): com.badlogic.gdx.files.FileHandle = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def parent(): com.badlogic.gdx.files.FileHandle = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def read(): java.io.InputStream = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def write(overwrite: scala.Boolean): java.io.OutputStream = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def list(): scala.Array[com.badlogic.gdx.files.FileHandle] = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def mkdirs(): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def delete(): scala.Boolean = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def deleteDirectory(): scala.Boolean = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def copyTo(dest: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def moveTo(dest: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def emptyDirectory(): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
  override def emptyDirectory(preserveTree: scala.Boolean): scala.Unit = {
    throw new java.lang.UnsupportedOperationException()
  }
}
object FileHandleStream {
  export com.badlogic.gdx.files.FileHandle.*
}