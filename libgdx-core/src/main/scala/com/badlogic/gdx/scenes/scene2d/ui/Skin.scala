package com.badlogic.gdx.scenes.scene2d.ui

class Skin extends com.badlogic.gdx.utils.Disposable {
  var resources: com.badlogic.gdx.utils.ObjectMap[java.lang.Class[?], com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object]] = new com.badlogic.gdx.utils.ObjectMap()
  var atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
  var scale$field: scala.Float = 1
  private final val jsonClassTags: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]] = new com.badlogic.gdx.utils.ObjectMap(Skin.defaultTagClasses.length)
  def this(skinFile: com.badlogic.gdx.files.FileHandle, atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas) = {
    this()
    this.atlas = atlas
    this.addRegions(atlas)
    this.load(skinFile)
  }
  def this(skinFile: com.badlogic.gdx.files.FileHandle) = {
    this()
    val atlasFile: com.badlogic.gdx.files.FileHandle = skinFile.sibling(skinFile.nameWithoutExtension() + ".atlas")
    if (atlasFile.exists()) {
      this.atlas = new com.badlogic.gdx.graphics.g2d.TextureAtlas(atlasFile)
      this.addRegions(this.atlas)
    } else ()
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
      typeResources = new com.badlogic.gdx.utils.ObjectMap(if (((`type` == classOf[com.badlogic.gdx.graphics.g2d.TextureRegion]) || (`type` == classOf[com.badlogic.gdx.scenes.scene2d.utils.Drawable])) || (`type` == classOf[com.badlogic.gdx.graphics.g2d.Sprite])) 256 else 64)
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
  def get[T](`type`: java.lang.Class[T]): T = {
    return this.get("default", `type`)
  }
  def get[T](name: java.lang.String, `type`: java.lang.Class[T]): T = {
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
    return resource.asInstanceOf[T]
  }
  def optional[T](name: java.lang.String, `type`: java.lang.Class[T]): T = {
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
    return typeResources.get(name).asInstanceOf[T]
  }
  def has(name: java.lang.String, `type`: java.lang.Class[?]): scala.Boolean = {
    val typeResources: com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Object] = this.resources.get(`type`)
    if (typeResources == null) {
      return false
    } else ()
    return typeResources.containsKey(name)
  }
  def getAll[T](`type`: java.lang.Class[T]): com.badlogic.gdx.utils.ObjectMap[java.lang.String, T] = {
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
  def setEnabled[V](styleable: com.badlogic.gdx.scenes.scene2d.ui.Styleable[V], enabled: scala.Boolean): scala.Unit = {
    var style: V = styleable.getStyle()
    var name: java.lang.String = this.find(style)
    if (name == null) {
      return
    } else ()
    name = name.replace("-disabled", "") + (if (enabled) "" else "-disabled")
    style = this.get(name, style.getClass().asInstanceOf[java.lang.Class[V]])
    styleable.setStyle(style)
  }
  def setEnabledReflection(actor: com.badlogic.gdx.scenes.scene2d.Actor, enabled: scala.Boolean): scala.Unit = {
    var method: com.badlogic.gdx.utils.reflect.Method = Skin.findMethod(actor.getClass(), "getStyle")
    if (method == null) {
      return
    } else ()
    var style: java.lang.Object = null.asInstanceOf[java.lang.Object]
    try {
      style = method.invoke(actor)
    } catch {
      case ignored: java.lang.Exception => {
        return
      }
    }
    var name: java.lang.String = this.find(style)
    if (name == null) {
      return
    } else ()
    name = name.replace("-disabled", "") + (if (enabled) "" else "-disabled")
    style = this.get(name, style.getClass())
    method = Skin.findMethod(actor.getClass(), "setStyle")
    if (method == null) {
      return
    } else ()
    try {
      method.invoke(actor, style)
    } catch {
      case ignored: java.lang.Exception => {
        ()
      }
    }
  }
  def getAtlas(): com.badlogic.gdx.graphics.g2d.TextureAtlas = {
    return this.atlas
  }
  def dispose(): scala.Unit = {
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
    val json: com.badlogic.gdx.utils.Json = new com.badlogic.gdx.utils.Json()
    json.setTypeName(null)
    json.setUsePrototypes(false)
    json.setSerializer(classOf[Skin], new com.badlogic.gdx.utils.Json.ReadOnlySerializer[Skin]())
    json.setSerializer(classOf[com.badlogic.gdx.graphics.g2d.BitmapFont], new com.badlogic.gdx.utils.Json.ReadOnlySerializer[com.badlogic.gdx.graphics.g2d.BitmapFont]())
    json.setSerializer(classOf[com.badlogic.gdx.graphics.Color], new com.badlogic.gdx.utils.Json.ReadOnlySerializer[com.badlogic.gdx.graphics.Color]())
    json.setSerializer(classOf[com.badlogic.gdx.scenes.scene2d.ui.Skin.TintedDrawable], new com.badlogic.gdx.utils.Json.ReadOnlySerializer())
    for (entry <- this.jsonClassTags) {
      json.addClassTag(entry.key, entry.value)
    }
    return json
  }
  def getJsonClassTags(): com.badlogic.gdx.utils.ObjectMap[java.lang.String, java.lang.Class[?]] = {
    return this.jsonClassTags
  }
}
object Skin {
  private final val defaultTagClasses: scala.Array[java.lang.Class[?]] = Array[java.lang.Class[?]](classOf[com.badlogic.gdx.graphics.g2d.BitmapFont], classOf[com.badlogic.gdx.graphics.Color], classOf[com.badlogic.gdx.scenes.scene2d.ui.Skin.TintedDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.SpriteDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable], classOf[com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable], classOf[com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.CheckBox.CheckBoxStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ImageButton.ImageButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ImageTextButton.ImageTextButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.ScrollPane.ScrollPaneStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Slider.SliderStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.TextTooltip.TextTooltipStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Touchpad.TouchpadStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle], classOf[com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle])
  private def findMethod(`type`: java.lang.Class[?], name: java.lang.String): com.badlogic.gdx.utils.reflect.Method = {
    val methods: scala.Array[com.badlogic.gdx.utils.reflect.Method] = com.badlogic.gdx.utils.reflect.ClassReflection.getMethods(`type`);
    { var i: scala.Int = 0; val n: scala.Int = methods.length; while (i < n) { {
      val method: com.badlogic.gdx.utils.reflect.Method = methods(i)
      if (method.getName().equals(name)) {
        return method
      } else ()
    }; i = i + 1 } }
    return null
  }
  class TintedDrawable {
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    var color: com.badlogic.gdx.graphics.Color = null.asInstanceOf[com.badlogic.gdx.graphics.Color]
  }
}