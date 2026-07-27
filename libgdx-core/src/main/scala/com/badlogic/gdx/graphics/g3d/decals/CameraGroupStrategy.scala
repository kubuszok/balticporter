package com.badlogic.gdx.graphics.g3d.decals

class CameraGroupStrategy extends com.badlogic.gdx.graphics.g3d.decals.GroupStrategy with com.badlogic.gdx.utils.Disposable {
  var arrayPool: com.badlogic.gdx.utils.Pool[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]] = new com.badlogic.gdx.utils.Pool[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]](16) {
    override def newObject(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal] = {
      return new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]]
    }
  }
  var usedArrays: com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]]()
  var materialGroups: com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.decals.DecalMaterial, com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]] = new com.badlogic.gdx.utils.ObjectMap[com.badlogic.gdx.graphics.g3d.decals.DecalMaterial, com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]]()
  var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  var shader: com.badlogic.gdx.graphics.glutils.ShaderProgram = null.asInstanceOf[com.badlogic.gdx.graphics.glutils.ShaderProgram]
  private var cameraSorter: java.util.Comparator[com.badlogic.gdx.graphics.g3d.decals.Decal] = null.asInstanceOf[java.util.Comparator[com.badlogic.gdx.graphics.g3d.decals.Decal]]
  def this(camera: com.badlogic.gdx.graphics.Camera) = {
    this()
    this.camera = camera
    this.cameraSorter = new java.util.Comparator[com.badlogic.gdx.graphics.g3d.decals.Decal]() {
      override def compare(o1: com.badlogic.gdx.graphics.g3d.decals.Decal, o2: com.badlogic.gdx.graphics.g3d.decals.Decal): scala.Int = {
        val dist1: scala.Float = CameraGroupStrategy.this.camera.position.dst(o1.position)
        val dist2: scala.Float = CameraGroupStrategy.this.camera.position.dst(o2.position)
        return java.lang.Math.signum(dist2 - dist1).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
      }
    }
    this.createDefaultShader()
  }
  def this(camera: com.badlogic.gdx.graphics.Camera, sorter: java.util.Comparator[com.badlogic.gdx.graphics.g3d.decals.Decal]) = {
    this()
    this.camera = camera
    this.cameraSorter = sorter
    this.createDefaultShader()
  }
  def setCamera(camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    this.camera = camera
  }
  def getCamera(): com.badlogic.gdx.graphics.Camera = {
    return this.camera
  }
  def decideGroup(decal: com.badlogic.gdx.graphics.g3d.decals.Decal): scala.Int = {
    return if (decal.getMaterial().isOpaque()) CameraGroupStrategy.GROUP_OPAQUE else CameraGroupStrategy.GROUP_BLEND
  }
  def beforeGroup(group: scala.Int, contents: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal]): scala.Unit = {
    if (group == CameraGroupStrategy.GROUP_BLEND) {
      com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      com.badlogic.gdx.Gdx.gl.glDepthMask(false)
      contents.sort(this.cameraSorter)
    } else {
      { var i: scala.Int = 0; val n: scala.Int = contents.size; while (i < n) { {
        val decal: com.badlogic.gdx.graphics.g3d.decals.Decal = contents.get(i)
        var materialGroup: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.decals.Decal] = this.materialGroups.get(decal.material)
        if (materialGroup == null) {
          materialGroup = this.arrayPool.obtain()
          materialGroup.clear()
          this.usedArrays.add(materialGroup)
          this.materialGroups.put(decal.material, materialGroup)
        } else ()
        materialGroup.add(decal)
      }; i = i + 1 } }
      contents.clear()
      for (materialGroup <- this.materialGroups.values()) {
        contents.addAll(materialGroup)
      }
      this.materialGroups.clear()
      this.arrayPool.freeAll(this.usedArrays)
      this.usedArrays.clear()
    }
  }
  def afterGroup(group: scala.Int): scala.Unit = {
    if (group == CameraGroupStrategy.GROUP_BLEND) {
      com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND)
      com.badlogic.gdx.Gdx.gl.glDepthMask(true)
    } else ()
  }
  def beforeGroups(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST)
    this.shader.bind()
    this.shader.setUniformMatrix("u_projectionViewMatrix", this.camera.combined)
    this.shader.setUniformi("u_texture", 0)
  }
  def afterGroups(): scala.Unit = {
    com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_DEPTH_TEST)
  }
  private def createDefaultShader(): scala.Unit = {
    val vertexShader: java.lang.String = (((((((((((((((((((((((("attribute vec4 " + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "attribute vec4 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "attribute vec2 ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "uniform mat4 u_projectionViewMatrix;\n") + "varying vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "\n") + "void main()\n") + "{\n") + "   v_color = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.COLOR_ATTRIBUTE) + ";\n") + "   v_color.a = v_color.a * (255.0/254.0);\n") + "   v_texCoords = ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.TEXCOORD_ATTRIBUTE) + "0;\n") + "   gl_Position =  u_projectionViewMatrix * ") + com.badlogic.gdx.graphics.glutils.ShaderProgram.POSITION_ATTRIBUTE) + ";\n") + "}\n"
    val fragmentShader: java.lang.String = (((((((("#ifdef GL_ES\n" + "precision mediump float;\n") + "#endif\n") + "varying vec4 v_color;\n") + "varying vec2 v_texCoords;\n") + "uniform sampler2D u_texture;\n") + "void main()\n") + "{\n") + "  gl_FragColor = v_color * texture2D(u_texture, v_texCoords);\n") + "}"
    this.shader = new com.badlogic.gdx.graphics.glutils.ShaderProgram(vertexShader, fragmentShader)
    if (!this.shader.isCompiled()) {
      throw new java.lang.IllegalArgumentException("couldn't compile shader: " + this.shader.getLog())
    } else ()
  }
  def getGroupShader(group: scala.Int): com.badlogic.gdx.graphics.glutils.ShaderProgram = {
    return this.shader
  }
  def dispose(): scala.Unit = {
    if (this.shader != null) {
      this.shader.dispose()
    } else ()
  }
}
object CameraGroupStrategy {
  private final val GROUP_OPAQUE: scala.Int = 0
  private final val GROUP_BLEND: scala.Int = 1
}