/*
 * Port-written after sge's DesktopFileHandle (sge/src/main/scaladesktop/sge/files/DesktopFileHandle.scala)
 * and libGDX's Lwjgl3FileHandle: the port's FileHandle takes the Sge context (external storage is
 * read from it) where sge's takes the external path (PROGRESS.md §13.30 step 3, ADJUSTMENTS.tsv).
 */
package sge
package files

import sge.Files.FileType
import java.io.File

class DesktopFileHandle(internalFile: File, fileType: FileType)(using ctx: Sge) extends FileHandle(internalFile, fileType) {
  def this(fileName: String, fileType: FileType)(using Sge) = this(new File(fileName), fileType)

  override def child(name: String): FileHandle =
    if (internalFile.getPath().length() == 0) new DesktopFileHandle(new File(name), fileType)
    else new DesktopFileHandle(new File(internalFile, name), fileType)

  override def sibling(name: String): FileHandle = {
    if (internalFile.getPath().length() == 0) throw utils.SgeError.FileReadError(this, "Cannot get the sibling of the root.")
    new DesktopFileHandle(new File(internalFile.getParent(), name), fileType)
  }

  override def parent(): FileHandle = {
    val p = internalFile.getParentFile()
    if (p == null) {
      if (fileType == FileType.Absolute) new DesktopFileHandle(new File("/"), fileType)
      else new DesktopFileHandle(new File(""), fileType)
    } else new DesktopFileHandle(p, fileType)
  }

  override def file(): File =
    if (fileType == FileType.External) new File(ctx.files.externalStoragePath, internalFile.getPath())
    else if (fileType == FileType.Local) new File(DesktopFileHandle.localPath, internalFile.getPath())
    else internalFile
}

object DesktopFileHandle {
  val localPath: String = new File("").getAbsolutePath() + File.separator
}
