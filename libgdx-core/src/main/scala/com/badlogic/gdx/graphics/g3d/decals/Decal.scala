package com.badlogic.gdx.graphics.g3d.decals

class Decal {
  var value: scala.Int = 0
  var vertices: scala.Array[scala.Float] = new scala.Array[scala.Float](Decal.SIZE)
  var position: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  var rotation: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion()
  var scale: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2(1, 1)
  var color: com.badlogic.gdx.graphics.Color = new com.badlogic.gdx.graphics.Color()
  var transformationOffset: com.badlogic.gdx.math.Vector2 = null
  var dimensions: com.badlogic.gdx.math.Vector2 = new com.badlogic.gdx.math.Vector2()
  var material: com.badlogic.gdx.graphics.g3d.decals.DecalMaterial = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.decals.DecalMaterial]
  var updated: scala.Boolean = false
  def this(material: com.badlogic.gdx.graphics.g3d.decals.DecalMaterial) = {
    this()
    this.material = material
  }
  def this() = {
    this()
    this.material = new com.badlogic.gdx.graphics.g3d.decals.DecalMaterial()
  }
  def setColor(r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): scala.Unit = {
    this.color.set(r, g, b, a)
    val intBits: scala.Int = ((((255 * a).asInstanceOf[scala.Int] << 24) | ((255 * b).asInstanceOf[scala.Int] << 16)) | ((255 * g).asInstanceOf[scala.Int] << 8)) | (255 * r).asInstanceOf[scala.Int]
    val color: scala.Float = com.badlogic.gdx.utils.NumberUtils.intToFloatColor(intBits)
    this.vertices(Decal.C1) = color
    this.vertices(Decal.C2) = color
    this.vertices(Decal.C3) = color
    this.vertices(Decal.C4) = color
  }
  def setColor(tint: com.badlogic.gdx.graphics.Color): scala.Unit = {
    this.color.set(tint)
    val color: scala.Float = tint.toFloatBits()
    this.vertices(Decal.C1) = color
    this.vertices(Decal.C2) = color
    this.vertices(Decal.C3) = color
    this.vertices(Decal.C4) = color
  }
  def setPackedColor(color: scala.Float): scala.Unit = {
    com.badlogic.gdx.graphics.Color.abgr8888ToColor(this.color, color)
    this.vertices(Decal.C1) = color
    this.vertices(Decal.C2) = color
    this.vertices(Decal.C3) = color
    this.vertices(Decal.C4) = color
  }
  def setRotationX(angle: scala.Float): scala.Unit = {
    this.rotation.set(com.badlogic.gdx.math.Vector3.X, angle)
    this.updated = false
  }
  def setRotationY(angle: scala.Float): scala.Unit = {
    this.rotation.set(com.badlogic.gdx.math.Vector3.Y, angle)
    this.updated = false
  }
  def setRotationZ(angle: scala.Float): scala.Unit = {
    this.rotation.set(com.badlogic.gdx.math.Vector3.Z, angle)
    this.updated = false
  }
  def rotateX(angle: scala.Float): scala.Unit = {
    Decal.rotator.set(com.badlogic.gdx.math.Vector3.X, angle)
    this.rotation.mul(Decal.rotator)
    this.updated = false
  }
  def rotateY(angle: scala.Float): scala.Unit = {
    Decal.rotator.set(com.badlogic.gdx.math.Vector3.Y, angle)
    this.rotation.mul(Decal.rotator)
    this.updated = false
  }
  def rotateZ(angle: scala.Float): scala.Unit = {
    Decal.rotator.set(com.badlogic.gdx.math.Vector3.Z, angle)
    this.rotation.mul(Decal.rotator)
    this.updated = false
  }
  def setRotation(yaw: scala.Float, pitch: scala.Float, roll: scala.Float): scala.Unit = {
    this.rotation.setEulerAngles(yaw, pitch, roll)
    this.updated = false
  }
  def setRotation(dir: com.badlogic.gdx.math.Vector3, up: com.badlogic.gdx.math.Vector3): scala.Unit = {
    Decal.tmp.set(up).crs(dir).nor()
    Decal.tmp2.set(dir).crs(Decal.tmp).nor()
    this.rotation.setFromAxes(Decal.tmp.x, Decal.tmp2.x, dir.x, Decal.tmp.y, Decal.tmp2.y, dir.y, Decal.tmp.z, Decal.tmp2.z, dir.z)
    this.updated = false
  }
  def setRotation(q: com.badlogic.gdx.math.Quaternion): scala.Unit = {
    this.rotation.set(q)
    this.updated = false
  }
  def getRotation(): com.badlogic.gdx.math.Quaternion = {
    return this.rotation
  }
  def translateX(units: scala.Float): scala.Unit = {
    this.position.x = this.position.x + units
    this.updated = false
  }
  def setX(x: scala.Float): scala.Unit = {
    this.position.x = x
    this.updated = false
  }
  def getX(): scala.Float = {
    return this.position.x
  }
  def translateY(units: scala.Float): scala.Unit = {
    this.position.y = this.position.y + units
    this.updated = false
  }
  def setY(y: scala.Float): scala.Unit = {
    this.position.y = y
    this.updated = false
  }
  def getY(): scala.Float = {
    return this.position.y
  }
  def translateZ(units: scala.Float): scala.Unit = {
    this.position.z = this.position.z + units
    this.updated = false
  }
  def setZ(z: scala.Float): scala.Unit = {
    this.position.z = z
    this.updated = false
  }
  def getZ(): scala.Float = {
    return this.position.z
  }
  def translate(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    this.position.add(x, y, z)
    this.updated = false
  }
  def translate(trans: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.position.add(trans)
    this.updated = false
  }
  def setPosition(x: scala.Float, y: scala.Float, z: scala.Float): scala.Unit = {
    this.position.set(x, y, z)
    this.updated = false
  }
  def setPosition(pos: com.badlogic.gdx.math.Vector3): scala.Unit = {
    this.position.set(pos)
    this.updated = false
  }
  def getColor(): com.badlogic.gdx.graphics.Color = {
    return this.color
  }
  def getPosition(): com.badlogic.gdx.math.Vector3 = {
    return this.position
  }
  def setScaleX(scale: scala.Float): scala.Unit = {
    this.scale.x = scale
    this.updated = false
  }
  def getScaleX(): scala.Float = {
    return this.scale.x
  }
  def setScaleY(scale: scala.Float): scala.Unit = {
    this.scale.y = scale
    this.updated = false
  }
  def getScaleY(): scala.Float = {
    return this.scale.y
  }
  def setScale(scaleX: scala.Float, scaleY: scala.Float): scala.Unit = {
    this.scale.set(scaleX, scaleY)
    this.updated = false
  }
  def setScale(scale: scala.Float): scala.Unit = {
    this.scale.set(scale, scale)
    this.updated = false
  }
  def setWidth(width: scala.Float): scala.Unit = {
    this.dimensions.x = width
    this.updated = false
  }
  def getWidth(): scala.Float = {
    return this.dimensions.x
  }
  def setHeight(height: scala.Float): scala.Unit = {
    this.dimensions.y = height
    this.updated = false
  }
  def getHeight(): scala.Float = {
    return this.dimensions.y
  }
  def setDimensions(width: scala.Float, height: scala.Float): scala.Unit = {
    this.dimensions.set(width, height)
    this.updated = false
  }
  def getVertices(): scala.Array[scala.Float] = {
    this.update()
    return this.vertices
  }
  def update(): scala.Unit = {
    if (!this.updated) {
      this.resetVertices()
      this.transformVertices()
    } else ()
  }
  def transformVertices(): scala.Unit = {
    var x: scala.Float = 0.0f
    var y: scala.Float = 0.0f
    var z: scala.Float = 0.0f
    var w: scala.Float = 0.0f
    var tx: scala.Float = 0.0f
    var ty: scala.Float = 0.0f
    if (this.transformationOffset != null) {
      tx = -this.transformationOffset.x
      ty = -this.transformationOffset.y
    } else {
      tx = {
        ty = 0
        ty
      }
    }
    x = (this.vertices(Decal.X1) + tx) * this.scale.x
    y = (this.vertices(Decal.Y1) + ty) * this.scale.y
    z = this.vertices(Decal.Z1)
    this.vertices(Decal.X1) = ((this.rotation.w * x) + (this.rotation.y * z)) - (this.rotation.z * y)
    this.vertices(Decal.Y1) = ((this.rotation.w * y) + (this.rotation.z * x)) - (this.rotation.x * z)
    this.vertices(Decal.Z1) = ((this.rotation.w * z) + (this.rotation.x * y)) - (this.rotation.y * x)
    w = (((-this.rotation.x) * x) - (this.rotation.y * y)) - (this.rotation.z * z)
    this.rotation.conjugate()
    x = this.vertices(Decal.X1)
    y = this.vertices(Decal.Y1)
    z = this.vertices(Decal.Z1)
    this.vertices(Decal.X1) = (((w * this.rotation.x) + (x * this.rotation.w)) + (y * this.rotation.z)) - (z * this.rotation.y)
    this.vertices(Decal.Y1) = (((w * this.rotation.y) + (y * this.rotation.w)) + (z * this.rotation.x)) - (x * this.rotation.z)
    this.vertices(Decal.Z1) = (((w * this.rotation.z) + (z * this.rotation.w)) + (x * this.rotation.y)) - (y * this.rotation.x)
    this.rotation.conjugate()
    this.vertices(Decal.X1) = this.vertices(Decal.X1) + (this.position.x - tx)
    this.vertices(Decal.Y1) = this.vertices(Decal.Y1) + (this.position.y - ty)
    this.vertices(Decal.Z1) = this.vertices(Decal.Z1) + this.position.z
    x = (this.vertices(Decal.X2) + tx) * this.scale.x
    y = (this.vertices(Decal.Y2) + ty) * this.scale.y
    z = this.vertices(Decal.Z2)
    this.vertices(Decal.X2) = ((this.rotation.w * x) + (this.rotation.y * z)) - (this.rotation.z * y)
    this.vertices(Decal.Y2) = ((this.rotation.w * y) + (this.rotation.z * x)) - (this.rotation.x * z)
    this.vertices(Decal.Z2) = ((this.rotation.w * z) + (this.rotation.x * y)) - (this.rotation.y * x)
    w = (((-this.rotation.x) * x) - (this.rotation.y * y)) - (this.rotation.z * z)
    this.rotation.conjugate()
    x = this.vertices(Decal.X2)
    y = this.vertices(Decal.Y2)
    z = this.vertices(Decal.Z2)
    this.vertices(Decal.X2) = (((w * this.rotation.x) + (x * this.rotation.w)) + (y * this.rotation.z)) - (z * this.rotation.y)
    this.vertices(Decal.Y2) = (((w * this.rotation.y) + (y * this.rotation.w)) + (z * this.rotation.x)) - (x * this.rotation.z)
    this.vertices(Decal.Z2) = (((w * this.rotation.z) + (z * this.rotation.w)) + (x * this.rotation.y)) - (y * this.rotation.x)
    this.rotation.conjugate()
    this.vertices(Decal.X2) = this.vertices(Decal.X2) + (this.position.x - tx)
    this.vertices(Decal.Y2) = this.vertices(Decal.Y2) + (this.position.y - ty)
    this.vertices(Decal.Z2) = this.vertices(Decal.Z2) + this.position.z
    x = (this.vertices(Decal.X3) + tx) * this.scale.x
    y = (this.vertices(Decal.Y3) + ty) * this.scale.y
    z = this.vertices(Decal.Z3)
    this.vertices(Decal.X3) = ((this.rotation.w * x) + (this.rotation.y * z)) - (this.rotation.z * y)
    this.vertices(Decal.Y3) = ((this.rotation.w * y) + (this.rotation.z * x)) - (this.rotation.x * z)
    this.vertices(Decal.Z3) = ((this.rotation.w * z) + (this.rotation.x * y)) - (this.rotation.y * x)
    w = (((-this.rotation.x) * x) - (this.rotation.y * y)) - (this.rotation.z * z)
    this.rotation.conjugate()
    x = this.vertices(Decal.X3)
    y = this.vertices(Decal.Y3)
    z = this.vertices(Decal.Z3)
    this.vertices(Decal.X3) = (((w * this.rotation.x) + (x * this.rotation.w)) + (y * this.rotation.z)) - (z * this.rotation.y)
    this.vertices(Decal.Y3) = (((w * this.rotation.y) + (y * this.rotation.w)) + (z * this.rotation.x)) - (x * this.rotation.z)
    this.vertices(Decal.Z3) = (((w * this.rotation.z) + (z * this.rotation.w)) + (x * this.rotation.y)) - (y * this.rotation.x)
    this.rotation.conjugate()
    this.vertices(Decal.X3) = this.vertices(Decal.X3) + (this.position.x - tx)
    this.vertices(Decal.Y3) = this.vertices(Decal.Y3) + (this.position.y - ty)
    this.vertices(Decal.Z3) = this.vertices(Decal.Z3) + this.position.z
    x = (this.vertices(Decal.X4) + tx) * this.scale.x
    y = (this.vertices(Decal.Y4) + ty) * this.scale.y
    z = this.vertices(Decal.Z4)
    this.vertices(Decal.X4) = ((this.rotation.w * x) + (this.rotation.y * z)) - (this.rotation.z * y)
    this.vertices(Decal.Y4) = ((this.rotation.w * y) + (this.rotation.z * x)) - (this.rotation.x * z)
    this.vertices(Decal.Z4) = ((this.rotation.w * z) + (this.rotation.x * y)) - (this.rotation.y * x)
    w = (((-this.rotation.x) * x) - (this.rotation.y * y)) - (this.rotation.z * z)
    this.rotation.conjugate()
    x = this.vertices(Decal.X4)
    y = this.vertices(Decal.Y4)
    z = this.vertices(Decal.Z4)
    this.vertices(Decal.X4) = (((w * this.rotation.x) + (x * this.rotation.w)) + (y * this.rotation.z)) - (z * this.rotation.y)
    this.vertices(Decal.Y4) = (((w * this.rotation.y) + (y * this.rotation.w)) + (z * this.rotation.x)) - (x * this.rotation.z)
    this.vertices(Decal.Z4) = (((w * this.rotation.z) + (z * this.rotation.w)) + (x * this.rotation.y)) - (y * this.rotation.x)
    this.rotation.conjugate()
    this.vertices(Decal.X4) = this.vertices(Decal.X4) + (this.position.x - tx)
    this.vertices(Decal.Y4) = this.vertices(Decal.Y4) + (this.position.y - ty)
    this.vertices(Decal.Z4) = this.vertices(Decal.Z4) + this.position.z
    this.updated = true
  }
  def resetVertices(): scala.Unit = {
    val left: scala.Float = (-this.dimensions.x) / 2.0f
    val right: scala.Float = left + this.dimensions.x
    val top: scala.Float = this.dimensions.y / 2.0f
    val bottom: scala.Float = top - this.dimensions.y
    this.vertices(Decal.X1) = left
    this.vertices(Decal.Y1) = top
    this.vertices(Decal.Z1) = 0
    this.vertices(Decal.X2) = right
    this.vertices(Decal.Y2) = top
    this.vertices(Decal.Z2) = 0
    this.vertices(Decal.X3) = left
    this.vertices(Decal.Y3) = bottom
    this.vertices(Decal.Z3) = 0
    this.vertices(Decal.X4) = right
    this.vertices(Decal.Y4) = bottom
    this.vertices(Decal.Z4) = 0
    this.updated = false
  }
  def updateUVs(): scala.Unit = {
    val tr: com.badlogic.gdx.graphics.g2d.TextureRegion = this.material.textureRegion
    this.vertices(Decal.U1) = tr.getU()
    this.vertices(Decal.V1) = tr.getV()
    this.vertices(Decal.U2) = tr.getU2()
    this.vertices(Decal.V2) = tr.getV()
    this.vertices(Decal.U3) = tr.getU()
    this.vertices(Decal.V3) = tr.getV2()
    this.vertices(Decal.U4) = tr.getU2()
    this.vertices(Decal.V4) = tr.getV2()
  }
  def setTextureRegion(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    this.material.textureRegion = textureRegion
    this.updateUVs()
  }
  def getTextureRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.material.textureRegion
  }
  def setBlending(srcBlendFactor: scala.Int, dstBlendFactor: scala.Int): scala.Unit = {
    this.material.srcBlendFactor = srcBlendFactor
    this.material.dstBlendFactor = dstBlendFactor
  }
  def getMaterial(): com.badlogic.gdx.graphics.g3d.decals.DecalMaterial = {
    return this.material
  }
  def setMaterial(material: com.badlogic.gdx.graphics.g3d.decals.DecalMaterial): scala.Unit = {
    this.material = material
  }
  def lookAt(position: com.badlogic.gdx.math.Vector3, up: com.badlogic.gdx.math.Vector3): scala.Unit = {
    Decal.dir.set(position).sub(this.position).nor()
    this.setRotation(Decal.dir, up)
  }
}
object Decal {
  private final val VERTEX_SIZE: scala.Int = (3 + 1) + 2
  final val SIZE: scala.Int = 4 * Decal.VERTEX_SIZE
  private var tmp: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  private var tmp2: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val dir: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  final val X1: scala.Int = 0
  final val Y1: scala.Int = 1
  final val Z1: scala.Int = 2
  final val C1: scala.Int = 3
  final val U1: scala.Int = 4
  final val V1: scala.Int = 5
  final val X2: scala.Int = 6
  final val Y2: scala.Int = 7
  final val Z2: scala.Int = 8
  final val C2: scala.Int = 9
  final val U2: scala.Int = 10
  final val V2: scala.Int = 11
  final val X3: scala.Int = 12
  final val Y3: scala.Int = 13
  final val Z3: scala.Int = 14
  final val C3: scala.Int = 15
  final val U3: scala.Int = 16
  final val V3: scala.Int = 17
  final val X4: scala.Int = 18
  final val Y4: scala.Int = 19
  final val Z4: scala.Int = 20
  final val C4: scala.Int = 21
  final val U4: scala.Int = 22
  final val V4: scala.Int = 23
  var rotator: com.badlogic.gdx.math.Quaternion = new com.badlogic.gdx.math.Quaternion(0, 0, 0, 0)
  def newDecal(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion): Decal = {
    return Decal.newDecal(textureRegion.getRegionWidth(), textureRegion.getRegionHeight(), textureRegion, com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND, com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND)
  }
  def newDecal(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion, hasTransparency: scala.Boolean): Decal = {
    return Decal.newDecal(textureRegion.getRegionWidth(), textureRegion.getRegionHeight(), textureRegion, if (hasTransparency) com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA else com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND, if (hasTransparency) com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA else com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND)
  }
  def newDecal(width: scala.Float, height: scala.Float, textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion): Decal = {
    return Decal.newDecal(width, height, textureRegion, com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND, com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND)
  }
  def newDecal(width: scala.Float, height: scala.Float, textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion, hasTransparency: scala.Boolean): Decal = {
    return Decal.newDecal(width, height, textureRegion, if (hasTransparency) com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA else com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND, if (hasTransparency) com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA else com.badlogic.gdx.graphics.g3d.decals.DecalMaterial.NO_BLEND)
  }
  def newDecal(width: scala.Float, height: scala.Float, textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion, srcBlendFactor: scala.Int, dstBlendFactor: scala.Int): Decal = {
    val decal: Decal = new Decal()
    decal.setTextureRegion(textureRegion)
    decal.setBlending(srcBlendFactor, dstBlendFactor)
    decal.dimensions.x = width
    decal.dimensions.y = height
    decal.setColor(1, 1, 1, 1)
    return decal
  }
  def newDecal(width: scala.Float, height: scala.Float, textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion, srcBlendFactor: scala.Int, dstBlendFactor: scala.Int, material: com.badlogic.gdx.graphics.g3d.decals.DecalMaterial): Decal = {
    val decal: Decal = new Decal(material)
    decal.setTextureRegion(textureRegion)
    decal.setBlending(srcBlendFactor, dstBlendFactor)
    decal.dimensions.x = width
    decal.dimensions.y = height
    decal.setColor(1, 1, 1, 1)
    return decal
  }
}