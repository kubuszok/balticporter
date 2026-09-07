/*
 * Port-written after sge's DesktopFiles (sge/src/main/scaladesktop/sge/files/DesktopFiles.scala):
 * every handle takes the Sge context, which exists only after the application built it, so the
 * context is read at each call, never captured (PROGRESS.md §13.30 step 3, ADJUSTMENTS.tsv).
 */
package sge
package files

import sge.Files.FileType
import java.io.File

final class DesktopFiles(context: () => Sge) extends sge.Files {
  private def handle(path: String, fileType: FileType): FileHandle = {
    given Sge = context()
    new DesktopFileHandle(path, fileType)
  }
  override def getFileHandle(path: String, fileType: FileType): FileHandle = handle(path, fileType)
  override def classpath(path: String): FileHandle = handle(path, FileType.Classpath)
  override def internal(path: String): FileHandle  = handle(path, FileType.Internal)
  override def external(path: String): FileHandle  = handle(path, FileType.External)
  override def absolute(path: String): FileHandle  = handle(path, FileType.Absolute)
  override def local(path: String): FileHandle     = handle(path, FileType.Local)
  override def externalStoragePath: String         = DesktopFiles.externalPath
  override def externalStorageAvailable: Boolean   = true
  override def localStoragePath: String            = DesktopFileHandle.localPath
  override def localStorageAvailable: Boolean      = true
}

object DesktopFiles {
  val externalPath: String = System.getProperty("user.home") + File.separator
}
