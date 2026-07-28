package com.badlogic.gdx.scenes.scene2d.ui

class Skin extends com.badlogic.gdx.utils.Disposable {
  var resources: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]] = new com.badlogic.gdx.utils.ObjectMap().asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]]]
  var atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
  var scale$field: scala.Float = 1
  private final val jsonClassTags: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]] = new com.badlogic.gdx.utils.ObjectMap(Skin.defaultTagClasses.length).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]]]
  def this(skinFile: com.badlogic.gdx.files.FileHandle) = {
    this()
    val atlasFile: com.badlogic.gdx.files.FileHandle = skinFile.sibling(skinFile.nameWithoutExtension() + ".atlas")
    if (atlasFile.exists()) {
      this.atlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas(atlasFile)
      this.addRegions(this.atlas)
    } else ()
    this.load(skinFile)
  }
  def this(skinFile: com.badlogic.gdx.files.FileHandle, atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas) = {
    this()
    this.atlas = atlas
    this.addRegions(atlas)
    this.load(skinFile)
  }
  def this(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas) = {
    this()
    this.atlas = atlas
    this.addRegions(atlas)
  }
  def load(skinFile: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    try {
      this.getJsonLoader(skinFile).fromJson(classOf[Skin], skinFile)
    } catch {
      case ex: com.badlogic.gdx.utils.SerializationException => {
        throw new com.badlogic.gdx.utils.SerializationException("Error reading file: " + skinFile, ex)
      }
    }
  }
  def addRegions(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas): scala.Unit = {
    val regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion] = atlas.getRegions();
    { var i: scala.Int = 0; val n: scala.Int = regions.size; while (i < n) { {
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = regions.get(i)
      var name: java.lang.String = region.name
      if (region.index != (-1)) {
        name = name + ("_" + region.index)
      } else ()
      this.add(name, region, classOf[com.badlogic.gdx.graphics.g2d.TextureRegion])
    }; i = i + 1 } }
  }
  def add(name: java.lang.String, resource: java.lang.Object): scala.Unit = {
    this.add(name, resource, resource.getClass())
  }
  def add(name: java.lang.String, resource: java.lang.Object, `type`: java.lang.Class[?]): scala.Unit = {
    if (name == null) {
      throw new java.lang.IllegalArgumentException("name cannot be null.")
    } else ()
    if (resource == null) {
      throw new java.lang.IllegalArgumentException("resource cannot be null.")
    } else ()
    var typeResources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = this.resources.get(`type`)
    if (typeResources == null) {
      typeResources = new com.badlogic.gdx.utils.ObjectMap(if (((`type` == classOf[com.badlogic.gdx.graphics.g2d.TextureRegion]) || (`type` == classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])) || (`type` == classOf[com.badlogic.gdx.graphics.g2d.Sprite])) 256 else 64).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]]
      this.resources.put(`type`, typeResources)
    } else ()
    typeResources.put(name, resource)
  }
  def remove(name: java.lang.String, `type`: java.lang.Class[?]): scala.Unit = {
    if (name == null) {
      throw new java.lang.IllegalArgumentException("name cannot be null.")
    } else ()
    val typeResources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = this.resources.get(`type`)
    typeResources.remove(name)
  }
  def get[T <: java.lang.Object](`type`: java.lang.Class[T]): T = {
    return this.get("default", `type`).asInstanceOf[T]
  }
  def get[T <: java.lang.Object](name: java.lang.String, `type`: java.lang.Class[T]): T = {
    if (name == null) {
      throw new java.lang.IllegalArgumentException("name cannot be null.")
    } else ()
    if (`type` == null) {
      throw new java.lang.IllegalArgumentException("type cannot be null.")
    } else ()
    if (`type` == classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]) {
      return this.getDrawable(name).asInstanceOf[T]
    } else ()
    if (`type` == classOf[com.badlogic.gdx.graphics.g2d.TextureRegion]) {
      return this.getRegion(name).asInstanceOf[T]
    } else ()
    if (`type` == classOf[com.badlogic.gdx.graphics.g2d.NinePatch]) {
      return this.getPatch(name).asInstanceOf[T]
    } else ()
    if (`type` == classOf[com.badlogic.gdx.graphics.g2d.Sprite]) {
      return this.getSprite(name).asInstanceOf[T]
    } else ()
    val typeResources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = this.resources.get(`type`)
    if (typeResources == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((("No " + `type`.getName()) + " registered with name: ") + name)
    } else ()
    val resource: java.lang.Object = typeResources.get(name)
    if (resource == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException((("No " + `type`.getName()) + " registered with name: ") + name)
    } else ()
    return resource.asInstanceOf[T].asInstanceOf[T]
  }
  @com.badlogic.gdx.utils.Null
  def optional[T <: java.lang.Object](name: java.lang.String, `type`: java.lang.Class[T]): T = {
    if (name == null) {
      throw new java.lang.IllegalArgumentException("name cannot be null.")
    } else ()
    if (`type` == null) {
      throw new java.lang.IllegalArgumentException("type cannot be null.")
    } else ()
    val typeResources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = this.resources.get(`type`)
    if (typeResources == null) {
      return null.asInstanceOf[T]
    } else ()
    return typeResources.get(name).asInstanceOf[T].asInstanceOf[T]
  }
  def has(name: java.lang.String, `type`: java.lang.Class[?]): scala.Boolean = {
    val typeResources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = this.resources.get(`type`)
    if (typeResources == null) {
      return false
    } else ()
    return typeResources.containsKey(name)
  }
  @com.badlogic.gdx.utils.Null
  def getAll[T <: java.lang.Object](`type`: java.lang.Class[T]): com.badlogic.gdx.utils.ObjectMap[java.lang.String, T] = {
    return this.resources.get(`type`).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, T]]
  }
  def getColor(name: java.lang.String): com.badlogic.gdx.graphics.Color = {
    return this.get(name, classOf[com.badlogic.gdx.graphics.Color])
  }
  def getFont(name: java.lang.String): com.badlogic.gdx.graphics.g2d.BitmapFont = {
    return this.get(name, classOf[com.badlogic.gdx.graphics.g2d.BitmapFont])
  }
  def getRegion(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    var region: com.badlogic.gdx.graphics.g2d.TextureRegion = this.optional(name, classOf[com.badlogic.gdx.graphics.g2d.TextureRegion])
    if (region != null) {
      return region
    } else ()
    val texture: com.badlogic.gdx.graphics.Texture = this.optional(name, classOf[com.badlogic.gdx.graphics.Texture])
    if (texture == null) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("No TextureRegion or Texture registered with name: " + name)
    } else ()
    region = new com.badlogic.gdx.graphics.g2d.TextureRegion(texture)
    this.add(name, region, classOf[com.badlogic.gdx.graphics.g2d.TextureRegion])
    return region
  }
  @com.badlogic.gdx.utils.Null
  def getRegions(regionName: java.lang.String): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = {
    var regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = null
    var i: scala.Int = 0
    var region: com.badlogic.gdx.graphics.g2d.TextureRegion = this.optional((regionName + "_") + { i += 1; i }, classOf[com.badlogic.gdx.graphics.g2d.TextureRegion])
    if (region != null) {
      regions = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion]()
      while (region != null) {
        regions.add(region)
        region = this.optional((regionName + "_") + { i += 1; i }, classOf[com.badlogic.gdx.graphics.g2d.TextureRegion])
      }
    } else ()
    return regions
  }
  def getTiledDrawable(name: java.lang.String): com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable = {
    var tiled: com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable = this.optional(name, classOf[com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable])
    if (tiled != null) {
      return tiled
    } else ()
    tiled = new com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable(this.getRegion(name))
    tiled.setName(name)
    if (this.scale$field != 1) {
      this.scale(tiled)
      tiled.setScale(this.scale$field)
    } else ()
    this.add(name, tiled, classOf[com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable])
    return tiled
  }
  def getPatch(name: java.lang.String): com.badlogic.gdx.graphics.g2d.NinePatch = {
    var patch: com.badlogic.gdx.graphics.g2d.NinePatch = this.optional(name, classOf[com.badlogic.gdx.graphics.g2d.NinePatch])
    if (patch != null) {
      return patch
    } else ()
    try {
      val region: com.badlogic.gdx.graphics.g2d.TextureRegion = this.getRegion(name)
      if (region.isInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]) {
        val splits: scala.Array[scala.Int] = region.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion].findValue("split")
        if (splits != null) {
          patch = new com.badlogic.gdx.graphics.g2d.NinePatch(region, splits(0), splits(1), splits(2), splits(3))
          val pads: scala.Array[scala.Int] = region.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion].findValue("pad")
          if (pads != null) {
            patch.setPadding(pads(0), pads(1), pads(2), pads(3))
          } else ()
        } else ()
      } else ()
      if (patch == null) {
        patch = new com.badlogic.gdx.graphics.g2d.NinePatch(region)
      } else ()
      if (this.scale$field != 1) {
        patch.scale(this.scale$field, this.scale$field)
      } else ()
      this.add(name, patch, classOf[com.badlogic.gdx.graphics.g2d.NinePatch])
      return patch
    } catch {
      case ex: com.badlogic.gdx.utils.GdxRuntimeException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("No NinePatch, TextureRegion, or Texture registered with name: " + name)
      }
    }
  }
  def getSprite(name: java.lang.String): com.badlogic.gdx.graphics.g2d.Sprite = {
    var sprite: com.badlogic.gdx.graphics.g2d.Sprite = this.optional(name, classOf[com.badlogic.gdx.graphics.g2d.Sprite])
    if (sprite != null) {
      return sprite
    } else ()
    try {
      val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = this.getRegion(name)
      if (textureRegion.isInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]) {
        val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = textureRegion.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]
        if ((region.rotate || (region.packedWidth != region.originalWidth)) || (region.packedHeight != region.originalHeight)) {
          sprite = new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasSprite(region)
        } else ()
      } else ()
      if (sprite == null) {
        sprite = new com.badlogic.gdx.graphics.g2d.Sprite(textureRegion)
      } else ()
      if (this.scale$field != 1) {
        sprite.setSize(sprite.getWidth() * this.scale$field, sprite.getHeight() * this.scale$field)
      } else ()
      this.add(name, sprite, classOf[com.badlogic.gdx.graphics.g2d.Sprite])
      return sprite
    } catch {
      case ex: com.badlogic.gdx.utils.GdxRuntimeException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("No NinePatch, TextureRegion, or Texture registered with name: " + name)
      }
    }
  }
  def getDrawable(name: java.lang.String): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    var drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable = this.optional(name, classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])
    if (drawable != null) {
      return drawable
    } else ()
    try {
      val textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion = this.getRegion(name)
      if (textureRegion.isInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]) {
        val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = textureRegion.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]
        if (region.findValue("split") != null) {
          drawable = new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(this.getPatch(name))
        } else {
          if ((region.rotate || (region.packedWidth != region.originalWidth)) || (region.packedHeight != region.originalHeight)) {
            drawable = new com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable(this.getSprite(name))
          } else ()
        }
      } else ()
      if (drawable == null) {
        drawable = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(textureRegion)
        if (this.scale$field != 1) {
          this.scale(drawable)
        } else ()
      } else ()
    } catch {
      case ignored: com.badlogic.gdx.utils.GdxRuntimeException => {
        ()
      }
    }
    if (drawable == null) {
      val patch: com.badlogic.gdx.graphics.g2d.NinePatch = this.optional(name, classOf[com.badlogic.gdx.graphics.g2d.NinePatch])
      if (patch != null) {
        drawable = new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(patch)
      } else {
        val sprite: com.badlogic.gdx.graphics.g2d.Sprite = this.optional(name, classOf[com.badlogic.gdx.graphics.g2d.Sprite])
        if (sprite != null) {
          drawable = new com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable(sprite)
        } else {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("No Drawable, NinePatch, TextureRegion, Texture, or Sprite registered with name: " + name)
        }
      }
    } else ()
    if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable]) {
      drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable].setName(name)
    } else ()
    this.add(name, drawable, classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])
    return drawable
  }
  @com.badlogic.gdx.utils.Null
  def find(resource: java.lang.Object): java.lang.String = {
    if (resource == null) {
      throw new java.lang.IllegalArgumentException("style cannot be null.")
    } else ()
    val typeResources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = this.resources.get(resource.getClass())
    if (typeResources == null) {
      return null
    } else ()
    return typeResources.findKey(resource, true)
  }
  def newDrawable(name: java.lang.String): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    return this.newDrawable(this.getDrawable(name))
  }
  def newDrawable(name: java.lang.String, r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    return this.newDrawable(this.getDrawable(name), new com.badlogic.gdx.graphics.Color(r, g, b, a))
  }
  def newDrawable(name: java.lang.String, tint: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    return this.newDrawable(this.getDrawable(name), tint)
  }
  def newDrawable(drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable]) {
      return new com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable(drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable])
    } else ()
    if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable]) {
      return new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable])
    } else ()
    if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable]) {
      return new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable])
    } else ()
    if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable]) {
      return new com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable(drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable])
    } else ()
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Unable to copy, unknown drawable type: " + drawable.getClass())
  }
  def newDrawable(drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable, r: scala.Float, g: scala.Float, b: scala.Float, a: scala.Float): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    return this.newDrawable(drawable, new com.badlogic.gdx.graphics.Color(r, g, b, a))
  }
  def newDrawable(drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable, tint: com.badlogic.gdx.graphics.Color): com.badlogic.gdx.scenes.scene2d.utils.Drawable = {
    var newDrawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable = null.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]
    if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable]) {
      newDrawable = drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable].tint(tint)
    } else {
      if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable]) {
        newDrawable = drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable].tint(tint)
      } else {
        if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable]) {
          newDrawable = drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable].tint(tint)
        } else {
          throw new com.badlogic.gdx.utils.GdxRuntimeException("Unable to copy, unknown drawable type: " + drawable.getClass())
        }
      }
    }
    if (newDrawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable]) {
      val named: com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable = newDrawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable]
      if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable]) {
        named.setName(((drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable].getName() + " (") + tint) + ")")
      } else {
        named.setName((" (" + tint) + ")")
      }
    } else ()
    return newDrawable
  }
  def scale(drawble: com.badlogic.gdx.scenes.scene2d.utils.Drawable): scala.Unit = {
    drawble.setLeftWidth(drawble.getLeftWidth() * this.scale$field)
    drawble.setRightWidth(drawble.getRightWidth() * this.scale$field)
    drawble.setBottomHeight(drawble.getBottomHeight() * this.scale$field)
    drawble.setTopHeight(drawble.getTopHeight() * this.scale$field)
    drawble.setMinWidth(drawble.getMinWidth() * this.scale$field)
    drawble.setMinHeight(drawble.getMinHeight() * this.scale$field)
  }
  def setScale(scale: scala.Float): scala.Unit = {
    this.scale$field = scale
  }
  def setEnabled[V <: java.lang.Object](styleable: com.badlogic.gdx.scenes.scene2d.ui.Styleable[V], enabled: scala.Boolean): scala.Unit = {
    var style: V = styleable.getStyle().asInstanceOf[V]
    var name: java.lang.String = this.find(style.asInstanceOf[java.lang.Object])
    if (name == null) {
      return
    } else ()
    name = name.replace("-disabled", "") + (if (enabled) "" else "-disabled")
    style = this.get(name, style.getClass().asInstanceOf[java.lang.Class[V]]).asInstanceOf[V]
    styleable.setStyle(style)
  }
  @com.badlogic.gdx.utils.Null
  def getAtlas(): com.badlogic.gdx.graphics.g2d.TextureAtlas = {
    return this.atlas
  }
  override def dispose(): scala.Unit = {
    if (this.atlas != null) {
      this.atlas.dispose()
    } else ()
    for (entry <- this.resources.values()) {
      for (resource <- entry.values()) {
        if (resource.isInstanceOf[com.badlogic.gdx.utils.Disposable]) {
          resource.asInstanceOf[com.badlogic.gdx.utils.Disposable].dispose()
        } else ()
      }
    }
  }
  def getJsonLoader(skinFile: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.utils.Json = {
    val skin: Skin = this
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json() {
      private final val parentFieldName: java.lang.String = "parent"
      override def readValue[T <: java.lang.Object](`type`: java.lang.Class[T], elementType: java.lang.Class[?], jsonData: com.badlogic.gdx.utils.JsonValue): T = {
        if (((jsonData != null) && jsonData.isString()) && (!classOf[java.lang.CharSequence].isAssignableFrom(`type`))) {
          return Skin.this.get(jsonData.asString(), `type`).asInstanceOf[T]
        } else ()
        return super.readValue(`type`, elementType.asInstanceOf[java.lang.Class[?]], jsonData).asInstanceOf[T]
      }
      override def ignoreUnknownField(`type`: java.lang.Class[?], fieldName: java.lang.String): scala.Boolean = {
        return fieldName.equals(parentFieldName)
      }
      override def readFields(`object`: java.lang.Object, jsonMap: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
        if (jsonMap.has(parentFieldName)) {
          val parentName: java.lang.String = readValue(parentFieldName, classOf[java.lang.String], jsonMap)
          var parentType: java.lang.Class[?] = `object`.getClass().asInstanceOf[java.lang.Class[?]]
          while (true) {
            try {
              copyFields(Skin.this.get(parentName, parentType), `object`)
              /* break */ ()
            } catch {
              case ex: com.badlogic.gdx.utils.GdxRuntimeException => {
                parentType = parentType.getSuperclass().asInstanceOf[java.lang.Class[?]]
                if (parentType == classOf[java.lang.Object]) {
                  val se: com.badlogic.gdx.utils.SerializationException = new com.badlogic.gdx.utils.SerializationException("Unable to find parent resource with name: " + parentName)
                  se.addTrace(jsonMap.child$field.trace())
                  throw se
                } else ()
              }
            }
          }
        } else ()
        super.readFields(`object`, jsonMap)
      }
    }
    json.setTypeName(null)
    json.setUsePrototypes(false)
    json.setSerializer(classOf[Skin], new com.badlogic.gdx.utils.Json.ReadOnlySerializer[Skin]() {
      override def read(json: com.badlogic.gdx.utils.Json, typeToValueMap: com.badlogic.gdx.utils.JsonValue, ignored: java.lang.Class[?]): Skin = {
        { var valueMap: com.badlogic.gdx.utils.JsonValue = typeToValueMap.child$field; while (valueMap != null) { {
          try {
            var `type`: java.lang.Class[?] = json.getClass(valueMap.name()).asInstanceOf[java.lang.Class[?]]
            if (`type` == null) {
              `type` = com.badlogic.gdx.graphics.g3d.particles.AssetTypeRegistry.classFor(valueMap.name()).asInstanceOf[java.lang.Class[?]]
            } else ()
            readNamedObjects(json, `type`.asInstanceOf[java.lang.Class[?]], valueMap)
          } catch {
            case ex: com.badlogic.gdx.utils.reflect.ReflectionException => {
              throw new com.badlogic.gdx.utils.SerializationException(ex)
            }
          }
        }; valueMap = valueMap.next$field } }
        return skin
      }
      private def readNamedObjects(json: com.badlogic.gdx.utils.Json, `type`: java.lang.Class[?], valueMap: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
        val addType: java.lang.Class[?] = if (`type` == classOf[com.badlogic.gdx.scenes.scene2d.ui.Skin.TintedDrawable]) classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable] else `type`;
        { var valueEntry: com.badlogic.gdx.utils.JsonValue = valueMap.child$field; while (valueEntry != null) { {
          val `object`: java.lang.Object = json.readValue(`type`, valueEntry)
          if (`object` == null) {
            /* continue */ ()
          } else ()
          try {
            Skin.this.add(valueEntry.name$field, `object`, addType.asInstanceOf[java.lang.Class[?]])
            if ((addType != classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable]) && classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable].isAssignableFrom(addType.asInstanceOf[java.lang.Class[?]])) {
              Skin.this.add(valueEntry.name$field, `object`, classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])
            } else ()
          } catch {
            case ex: java.lang.Exception => {
              throw new com.badlogic.gdx.utils.SerializationException((("Error reading " + `type`.asInstanceOf[java.lang.Class[?]].getSimpleName()) + ": ") + valueEntry.name$field, ex)
            }
          }
        }; valueEntry = valueEntry.next$field } }
      }
    })
    json.setSerializer(classOf[com.badlogic.gdx.graphics.g2d.BitmapFont], new com.badlogic.gdx.utils.Json.ReadOnlySerializer[com.badlogic.gdx.graphics.g2d.BitmapFont]() {
      override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue, `type`: java.lang.Class[?]): com.badlogic.gdx.graphics.g2d.BitmapFont = {
        val path: java.lang.String = json.readValue("file", classOf[java.lang.String], jsonData)
        val scaledSize: scala.Float = json.readValue("scaledSize", classOf[scala.Float], (-1.0f).asInstanceOf[java.lang.Float], jsonData)
        val flip: java.lang.Boolean = json.readValue[java.lang.Boolean]("flip", classOf[java.lang.Boolean], false.asInstanceOf[java.lang.Boolean], jsonData)
        var markupEnabled: java.lang.Boolean = json.readValue[java.lang.Boolean]("markupEnabled", classOf[java.lang.Boolean], false.asInstanceOf[java.lang.Boolean], jsonData)
        val useIntegerPositions: java.lang.Boolean = json.readValue[java.lang.Boolean]("useIntegerPositions", classOf[java.lang.Boolean], true.asInstanceOf[java.lang.Boolean], jsonData)
        var fontFile: com.badlogic.gdx.files.FileHandle = skinFile.parent().child(path)
        if (!fontFile.exists()) {
          fontFile = com.badlogic.gdx.Gdx.files.internal(path)
        } else ()
        if (!fontFile.exists()) {
          throw new com.badlogic.gdx.utils.SerializationException("Font file not found: " + fontFile)
        } else ()
        val regionName: java.lang.String = fontFile.nameWithoutExtension()
        try {
          var font: com.badlogic.gdx.graphics.g2d.BitmapFont = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.BitmapFont]
          val regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureRegion] = skin.getRegions(regionName)
          if (regions != null) {
            font = new com.badlogic.gdx.graphics.g2d.BitmapFont(new com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData(fontFile, flip), regions, true)
          } else {
            val region: com.badlogic.gdx.graphics.g2d.TextureRegion = skin.optional(regionName, classOf[com.badlogic.gdx.graphics.g2d.TextureRegion])
            if (region != null) {
              font = new com.badlogic.gdx.graphics.g2d.BitmapFont(fontFile, region, flip)
            } else {
              val imageFile: com.badlogic.gdx.files.FileHandle = fontFile.parent().child(regionName + ".png")
              if (imageFile.exists()) {
                font = new com.badlogic.gdx.graphics.g2d.BitmapFont(fontFile, imageFile, flip)
              } else {
                font = new com.badlogic.gdx.graphics.g2d.BitmapFont(fontFile, flip)
              }
            }
          }
          font.getData().markupEnabled = markupEnabled
          font.setUseIntegerPositions(useIntegerPositions)
          if (scaledSize != (-1)) {
            font.getData().setScale(scaledSize / font.getCapHeight())
          } else ()
          return font
        } catch {
          case ex: java.lang.RuntimeException => {
            throw new com.badlogic.gdx.utils.SerializationException("Error loading bitmap font: " + fontFile, ex)
          }
        }
      }
    })
    json.setSerializer(classOf[com.badlogic.gdx.graphics.Color], new com.badlogic.gdx.utils.Json.ReadOnlySerializer[com.badlogic.gdx.graphics.Color]() {
      override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue, `type`: java.lang.Class[?]): com.badlogic.gdx.graphics.Color = {
        if (jsonData.isString()) {
          return Skin.this.get(jsonData.asString(), classOf[com.badlogic.gdx.graphics.Color])
        } else ()
        val hex: java.lang.String = json.readValue("hex", classOf[java.lang.String], null.asInstanceOf[java.lang.String], jsonData)
        if (hex != null) {
          return com.badlogic.gdx.graphics.Color.valueOf(hex)
        } else ()
        val r: scala.Float = json.readValue("r", classOf[scala.Float], 0.0f.asInstanceOf[java.lang.Float], jsonData)
        val g: scala.Float = json.readValue("g", classOf[scala.Float], 0.0f.asInstanceOf[java.lang.Float], jsonData)
        val b: scala.Float = json.readValue("b", classOf[scala.Float], 0.0f.asInstanceOf[java.lang.Float], jsonData)
        val a: scala.Float = json.readValue("a", classOf[scala.Float], 1.0f.asInstanceOf[java.lang.Float], jsonData)
        return new com.badlogic.gdx.graphics.Color(r, g, b, a)
      }
    })
    json.setSerializer(classOf[com.badlogic.gdx.scenes.scene2d.ui.Skin.TintedDrawable], new com.badlogic.gdx.utils.Json.ReadOnlySerializer() {
      override def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue, `type`: java.lang.Class[?]): java.lang.Object = {
        val name: java.lang.String = json.readValue("name", classOf[java.lang.String], jsonData)
        val color: com.badlogic.gdx.graphics.Color = json.readValue("color", classOf[com.badlogic.gdx.graphics.Color], jsonData)
        if (color == null) {
          throw new com.badlogic.gdx.utils.SerializationException("TintedDrawable missing color: " + jsonData)
        } else ()
        val drawable: com.badlogic.gdx.scenes.scene2d.utils.Drawable = Skin.this.newDrawable(name, color)
        if (drawable.isInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable]) {
          val named: com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable = drawable.asInstanceOf[com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable]
          named.setName(((((jsonData.name$field + " (") + name) + ", ") + color) + ")")
        } else ()
        return drawable
      }
    })
    for (entry <- this.jsonClassTags) {
      json.addClassTag(entry.key, entry.value.asInstanceOf[java.lang.Class[?]])
    }
    return json
  }
  def getJsonClassTags(): com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]] = {
    return this.jsonClassTags.asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]]]
  }
  locally {
    for (c <- Skin.defaultTagClasses) {
      this.jsonClassTags.put(c.getSimpleName(), c)
    }
  }
}
object Skin {
  private final val defaultTagClasses: scala.Array[java.lang.Class[?]] = scala.Array[java.lang.Class[?]](classOf[com.badlogic.gdx.graphics.g2d.BitmapFont], classOf[com.badlogic.gdx.graphics.Color], classOf[com.badlogic.gdx.scenes.scene2d.ui.Skin.TintedDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable], classOf[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton.ImageTextButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle])
  class TintedDrawable {
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    var color: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  }
}