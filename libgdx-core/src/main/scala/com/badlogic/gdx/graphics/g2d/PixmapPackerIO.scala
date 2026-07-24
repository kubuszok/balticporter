package com.badlogic.gdx.graphics.g2d

class PixmapPackerIO {
  def save(file: com.badlogic.gdx.files.FileHandle, packer: com.badlogic.gdx.graphics.g2d.PixmapPacker): scala.Unit = {
    this.save(file, packer, new com.badlogic.gdx.graphics.g2d.PixmapPackerIO.SaveParameters())
  }
  def save(file: com.badlogic.gdx.files.FileHandle, packer: com.badlogic.gdx.graphics.g2d.PixmapPacker, parameters: com.badlogic.gdx.graphics.g2d.PixmapPackerIO.SaveParameters): scala.Unit = {
    val writer: java.io.Writer = file.writer(false)
    var index: scala.Int = 0
    for (page <- packer.pages) {
      if (page.rects.size > 0) {
        val pageFile: com.badlogic.gdx.files.FileHandle = file.sibling(((file.nameWithoutExtension() + "_") + { index += 1; index }) + parameters.format.getExtension())
        parameters.format match {
          case com.badlogic.gdx.graphics.g2d.PixmapPackerIO.ImageFormat.CIM => {
            com.badlogic.gdx.graphics.PixmapIO.writeCIM(pageFile, page.image)
          }
          case com.badlogic.gdx.graphics.g2d.PixmapPackerIO.ImageFormat.PNG => {
            com.badlogic.gdx.graphics.PixmapIO.writePNG(pageFile, page.image)
          }
        }
        writer.write("\n")
        writer.write(pageFile.name() + "\n")
        writer.write(((("size: " + page.image.getWidth()) + ",") + page.image.getHeight()) + "\n")
        writer.write(("format: " + packer.pageFormat.name()) + "\n")
        writer.write(((("filter: " + parameters.minFilter.name()) + ",") + parameters.magFilter.name()) + "\n")
        writer.write("repeat: none" + "\n")
        for (name <- page.rects.keys()) {
          var imageIndex: scala.Int = -1
          var imageName: java.lang.String = name
          if (parameters.useIndexes) {
            val matcher: java.util.regex.Matcher = com.badlogic.gdx.graphics.g2d.PixmapPacker.indexPattern.matcher(imageName)
            if (matcher.matches()) {
              imageName = matcher.group(1)
              imageIndex = java.lang.Integer.parseInt(matcher.group(2))
            } else ()
          } else ()
          writer.write(imageName + "\n")
          val rect: com.badlogic.gdx.graphics.g2d.PixmapPacker.PixmapPackerRectangle = page.rects.get(name)
          writer.write("  rotate: false" + "\n")
          writer.write(((("  xy: " + rect.getX()) + ",") + rect.getY()) + "\n")
          writer.write(((("  size: " + rect.getWidth()) + ",") + rect.getHeight()) + "\n")
          if (rect.splits != null) {
            writer.write(((((((("  split: " + rect.splits(0)) + ", ") + rect.splits(1)) + ", ") + rect.splits(2)) + ", ") + rect.splits(3)) + "\n")
            if (rect.pads != null) {
              writer.write(((((((("  pad: " + rect.pads(0)) + ", ") + rect.pads(1)) + ", ") + rect.pads(2)) + ", ") + rect.pads(3)) + "\n")
            } else ()
          } else ()
          writer.write(((("  orig: " + rect.originalWidth) + ", ") + rect.originalHeight) + "\n")
          writer.write(((("  offset: " + rect.offsetX) + ", ") + ((rect.originalHeight - rect.getHeight()) - rect.offsetY)) + "\n")
          writer.write(("  index: " + imageIndex) + "\n")
        }
      } else ()
    }
    writer.close()
  }
}
object PixmapPackerIO {
  sealed abstract class ImageFormat {
    private var `extension`: java.lang.String = null.asInstanceOf[java.lang.String]
    def getExtension(): java.lang.String = {
      return this.`extension`
    }
  }
  object ImageFormat {
    case object CIM extends ImageFormat(".cim")
    case object PNG extends ImageFormat(".png")
    def values(): Array[ImageFormat] = Array(CIM, PNG)
  }
  class SaveParameters {
    var format: com.badlogic.gdx.graphics.g2d.PixmapPackerIO.ImageFormat = com.badlogic.gdx.graphics.g2d.PixmapPackerIO.ImageFormat.PNG
    var minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
    var useIndexes: scala.Boolean = false
  }
}