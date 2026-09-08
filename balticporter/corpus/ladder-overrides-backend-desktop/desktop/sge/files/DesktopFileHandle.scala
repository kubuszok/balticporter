/*
 * sge's DesktopFileHandle (sge/src/main/scaladesktop/sge/files/DesktopFileHandle.scala): the port's
 * FileHandle carries the external storage path as a field given at construction, so the value is
 * assigned at the head of the body instead of passed to the super constructor (ADJUSTMENTS.tsv).
 */
package sge
package files

import java.io.File

class DesktopFileHandle(internalFile: File, fileType: FileType, externalPath: String) extends FileHandle(internalFile, fileType) {
  this.externalStoragePath = externalPath

  def this(fileName: String, fileType: FileType, externalPath: String) =
    this(new File(fileName), fileType, externalPath)

  override def child(name: String): FileHandle =
    if (internalFile.getPath().length() == 0) DesktopFileHandle(new File(name), fileType, externalStoragePath)
    else DesktopFileHandle(new File(internalFile, name), fileType, externalStoragePath)

  override def sibling(name: String): FileHandle = {
    if (internalFile.getPath().length() == 0) throw utils.SgeError.FileReadError(this, "Cannot get the sibling of the root.")
    DesktopFileHandle(new File(internalFile.getParent(), name), fileType, externalStoragePath)
  }

  override def parent(): FileHandle = {
    val p = internalFile.getParentFile()
    if (p == null) {
      if (fileType == FileType.Absolute) DesktopFileHandle(new File("/"), fileType, externalStoragePath)
      else DesktopFileHandle(new File(""), fileType, externalStoragePath)
    } else DesktopFileHandle(p, fileType, externalStoragePath)
  }

  override def file: File =
    if (fileType == FileType.External) new File(externalStoragePath, internalFile.getPath())
    else if (fileType == FileType.Local) new File(DesktopFileHandle.localPath, internalFile.getPath())
    else internalFile
}

object DesktopFileHandle {
  val localPath: String = new File("").getAbsolutePath() + File.separator
}
