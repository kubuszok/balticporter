package com.badlogic.gdx.graphics.g3d.loader

class MtlLoader {
  var materials: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial]()
  def load(file: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    var line: java.lang.String = null.asInstanceOf[java.lang.String]
    var tokens: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
    val currentMaterial: com.badlogic.gdx.graphics.g3d.loader.MtlLoader.ObjMaterial = new com.badlogic.gdx.graphics.g3d.loader.MtlLoader.ObjMaterial()
    if ((file == null) || (!file.exists())) {
      return
    } else ()
    val reader: java.io.BufferedReader = new java.io.BufferedReader(new java.io.InputStreamReader(file.read()), 4096)
    try {
      while ({
        line = reader.readLine()
        line
      } != null) {
        if ((line.length() > 0) && (line.charAt(0) == '\t')) {
          line = line.substring(1).trim()
        } else ()
        tokens = line.split("\\s+")
        if (tokens(0).length() == 0) {
          /* continue */ ()
        } else {
          if (tokens(0).charAt(0) == '#') {
            /* continue */ ()
          } else {
            val key: java.lang.String = tokens(0).toLowerCase()
            if (key.equals("newmtl")) {
              val mat: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = currentMaterial.build()
              this.materials.add(mat)
              if (tokens.length > 1) {
                currentMaterial.materialName = tokens(1)
                currentMaterial.materialName = currentMaterial.materialName.replace('.', '_')
              } else {
                currentMaterial.materialName = "default"
              }
              currentMaterial.reset()
            } else {
              if (key.equals("ka")) {
                currentMaterial.ambientColor = this.parseColor(tokens)
              } else {
                if (key.equals("kd")) {
                  currentMaterial.diffuseColor = this.parseColor(tokens)
                } else {
                  if (key.equals("ks")) {
                    currentMaterial.specularColor = this.parseColor(tokens)
                  } else {
                    if (key.equals("tr") || key.equals("d")) {
                      currentMaterial.opacity = java.lang.Float.parseFloat(tokens(1))
                    } else {
                      if (key.equals("ns")) {
                        currentMaterial.shininess = java.lang.Float.parseFloat(tokens(1))
                      } else {
                        if (key.equals("map_d")) {
                          currentMaterial.alphaTexFilename = file.parent().child(tokens(1)).path()
                        } else {
                          if (key.equals("map_ka")) {
                            currentMaterial.ambientTexFilename = file.parent().child(tokens(1)).path()
                          } else {
                            if (key.equals("map_kd")) {
                              currentMaterial.diffuseTexFilename = file.parent().child(tokens(1)).path()
                            } else {
                              if (key.equals("map_ks")) {
                                currentMaterial.specularTexFilename = file.parent().child(tokens(1)).path()
                              } else {
                                if (key.equals("map_ns")) {
                                  currentMaterial.shininessTexFilename = file.parent().child(tokens(1)).path()
                                } else ()
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
      reader.close()
    } catch {
      case e: java.io.IOException => {
        return
      }
    }
    val mat: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = currentMaterial.build()
    this.materials.add(mat)
    return
  }
  private def parseColor(tokens: scala.Array[java.lang.String]): com.badlogic.gdx.graphics.Color = {
    val r: scala.Float = java.lang.Float.parseFloat(tokens(1))
    val g: scala.Float = java.lang.Float.parseFloat(tokens(2))
    val b: scala.Float = java.lang.Float.parseFloat(tokens(3))
    var a: scala.Float = 1
    if (tokens.length > 4) {
      a = java.lang.Float.parseFloat(tokens(4))
    } else ()
    return new com.badlogic.gdx.graphics.Color(r, g, b, a)
  }
  def getMaterial(name: java.lang.String): com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = {
    for (m <- this.materials) {
      if (m.id.equals(name)) {
        return m
      } else ()
    }
    val mat: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = new com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial()
    mat.id = name
    mat.diffuse = new com.badlogic.gdx.graphics.Color(com.badlogic.gdx.graphics.Color.WHITE)
    this.materials.add(mat)
    return mat
  }
}
object MtlLoader {
  private class ObjMaterial {
    var materialName: java.lang.String = "default"
    var ambientColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var diffuseColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var specularColor: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
    var opacity: scala.Float = 0.0f
    var shininess: scala.Float = 0.0f
    var alphaTexFilename: java.lang.String = null.asInstanceOf[java.lang.String]
    var ambientTexFilename: java.lang.String = null.asInstanceOf[java.lang.String]
    var diffuseTexFilename: java.lang.String = null.asInstanceOf[java.lang.String]
    var shininessTexFilename: java.lang.String = null.asInstanceOf[java.lang.String]
    var specularTexFilename: java.lang.String = null.asInstanceOf[java.lang.String]
    def this() = {
      this()
      this.reset()
    }
    def build(): com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = {
      val mat: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial = new com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial()
      mat.id = this.materialName
      mat.ambient = if (this.ambientColor == null) null else new com.badlogic.gdx.graphics.Color(this.ambientColor)
      mat.diffuse = new com.badlogic.gdx.graphics.Color(this.diffuseColor)
      mat.specular = new com.badlogic.gdx.graphics.Color(this.specularColor)
      mat.opacity = this.opacity
      mat.shininess = this.shininess
      this.addTexture(mat, this.alphaTexFilename, com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_TRANSPARENCY)
      this.addTexture(mat, this.ambientTexFilename, com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_AMBIENT)
      this.addTexture(mat, this.diffuseTexFilename, com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_DIFFUSE)
      this.addTexture(mat, this.specularTexFilename, com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_SPECULAR)
      this.addTexture(mat, this.shininessTexFilename, com.badlogic.gdx.graphics.g3d.model.data.ModelTexture.USAGE_SHININESS)
      return mat
    }
    private def addTexture(mat: com.badlogic.gdx.graphics.g3d.model.data.ModelMaterial, texFilename: java.lang.String, usage: scala.Int): scala.Unit = {
      if (texFilename != null) {
        val tex: com.badlogic.gdx.graphics.g3d.model.data.ModelTexture = new com.badlogic.gdx.graphics.g3d.model.data.ModelTexture()
        tex.usage = usage
        tex.fileName = texFilename
        if (mat.textures == null) {
          mat.textures = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.model.data.ModelTexture](1)
        } else ()
        mat.textures.add(tex)
      } else ()
    }
    def reset(): scala.Unit = {
      this.ambientColor = null
      this.diffuseColor = com.badlogic.gdx.graphics.Color.WHITE
      this.specularColor = com.badlogic.gdx.graphics.Color.WHITE
      this.opacity = 1.0f
      this.shininess = 0.0f
      this.alphaTexFilename = null
      this.ambientTexFilename = null
      this.diffuseTexFilename = null
      this.shininessTexFilename = null
      this.specularTexFilename = null
    }
  }
}