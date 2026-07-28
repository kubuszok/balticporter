package com.badlogic.gdx.graphics.g2d

class TextureAtlas extends com.badlogic.gdx.utils.Disposable {
  private final val textures: com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.graphics.Texture] = new com.badlogic.gdx.utils.ObjectSet(4).asInstanceOf[com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.graphics.Texture]]
  private final val regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]]
  def this(data: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData) = {
    this()
    this.load(data)
  }
  def this(packFile: com.badlogic.gdx.files.FileHandle, imagesDir: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
    this(new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData(packFile, imagesDir, flip))
  }
  def this(packFile: com.badlogic.gdx.files.FileHandle, imagesDir: com.badlogic.gdx.files.FileHandle) = {
    this(packFile, imagesDir, false)
  }
  def this(packFile: com.badlogic.gdx.files.FileHandle) = {
    this(packFile, packFile.parent())
  }
  def this(internalPackFile: java.lang.String) = {
    this(com.badlogic.gdx.Gdx.files.internal(internalPackFile))
  }
  def this(packFile: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
    this(packFile, packFile.parent(), flip)
  }
  def load(data: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData): scala.Unit = {
    this.textures.ensureCapacity(data.pages.size)
    for (page <- data.pages) {
      if (page.texture == null) {
        page.texture = new com.badlogic.gdx.graphics.Texture(page.textureFile, page.format, page.useMipMaps)
      } else ()
      page.texture.setFilter(page.minFilter, page.magFilter)
      page.texture.setWrap(page.uWrap, page.vWrap)
      this.textures.add(page.texture)
    }
    this.regions.ensureCapacity(data.regions.size)
    for (region <- data.regions) {
      val atlasRegion: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(region.page.texture, region.left, region.top, if (region.rotate) region.height else region.width, if (region.rotate) region.width else region.height)
      atlasRegion.index = region.index
      atlasRegion.name = region.name
      atlasRegion.offsetX = region.offsetX
      atlasRegion.offsetY = region.offsetY
      atlasRegion.originalHeight = region.originalHeight
      atlasRegion.originalWidth = region.originalWidth
      atlasRegion.rotate = region.rotate
      atlasRegion.degrees = region.degrees
      atlasRegion.names = region.names
      atlasRegion.values = region.values
      if (region.flip) {
        atlasRegion.flip(false, true)
      } else ()
      this.regions.add(atlasRegion)
    }
  }
  def addRegion(name: java.lang.String, texture: com.badlogic.gdx.graphics.Texture, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int): com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = {
    this.textures.add(texture)
    val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(texture, x, y, width, height)
    region.name = name
    this.regions.add(region)
    return region
  }
  def addRegion(name: java.lang.String, textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion): com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = {
    this.textures.add(textureRegion.texture)
    val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(textureRegion)
    region.name = name
    this.regions.add(region)
    return region
  }
  def getRegions(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion] = {
    return this.regions
  }
  @com.badlogic.gdx.utils.Null
  def findRegion(name: java.lang.String): com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = {
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      if (this.regions.get(i).name.equals(name)) {
        return this.regions.get(i)
      } else ()
    }; i = i + 1 } }
    return null
  }
  @com.badlogic.gdx.utils.Null
  def findRegion(name: java.lang.String, index: scala.Int): com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = {
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = this.regions.get(i)
      if (!region.name.equals(name)) {
        /* continue */ ()
      } else ()
      if (region.index != index) {
        /* continue */ ()
      } else ()
      return region
    }; i = i + 1 } }
    return null
  }
  def findRegions(name: java.lang.String): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion] = {
    val matched: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion](((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion](size)));
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = this.regions.get(i)
      if (region.name.equals(name)) {
        matched.add(new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(region))
      } else ()
    }; i = i + 1 } }
    return matched
  }
  def createSprites(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite] = {
    val sprites: com.badlogic.gdx.utils.Array[?] = new com.badlogic.gdx.utils.Array(true, this.regions.size, ((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g2d.Sprite](size))).asInstanceOf[com.badlogic.gdx.utils.Array[?]];
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      sprites.asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.Object]].add(this.newSprite(this.regions.get(i)).asInstanceOf[java.lang.Object])
    }; i = i + 1 } }
    return sprites.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite]]
  }
  @com.badlogic.gdx.utils.Null
  def createSprite(name: java.lang.String): com.badlogic.gdx.graphics.g2d.Sprite = {
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      if (this.regions.get(i).name.equals(name)) {
        return this.newSprite(this.regions.get(i))
      } else ()
    }; i = i + 1 } }
    return null
  }
  @com.badlogic.gdx.utils.Null
  def createSprite(name: java.lang.String, index: scala.Int): com.badlogic.gdx.graphics.g2d.Sprite = {
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = this.regions.get(i)
      if (region.index != index) {
        /* continue */ ()
      } else ()
      if (!region.name.equals(name)) {
        /* continue */ ()
      } else ()
      return this.newSprite(this.regions.get(i))
    }; i = i + 1 } }
    return null
  }
  def createSprites(name: java.lang.String): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite] = {
    val matched: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite](((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g2d.Sprite](size)));
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = this.regions.get(i)
      if (region.name.equals(name)) {
        matched.add(this.newSprite(region))
      } else ()
    }; i = i + 1 } }
    return matched
  }
  private def newSprite(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion): com.badlogic.gdx.graphics.g2d.Sprite = {
    if ((region.packedWidth == region.originalWidth) && (region.packedHeight == region.originalHeight)) {
      if (region.rotate) {
        val sprite: com.badlogic.gdx.graphics.g2d.Sprite = new com.badlogic.gdx.graphics.g2d.Sprite(region)
        sprite.setBounds(0, 0, region.getRegionHeight(), region.getRegionWidth())
        sprite.rotate90(true)
        return sprite
      } else ()
      return new com.badlogic.gdx.graphics.g2d.Sprite(region)
    } else ()
    return new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasSprite(region)
  }
  @com.badlogic.gdx.utils.Null
  def createPatch(name: java.lang.String): com.badlogic.gdx.graphics.g2d.NinePatch = {
    { var i: scala.Int = 0; val n: scala.Int = this.regions.size; while (i < n) { {
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = this.regions.get(i)
      if (region.name.equals(name)) {
        val splits: scala.Array[scala.Int] = region.findValue("split")
        if (splits == null) {
          throw new java.lang.IllegalArgumentException("Region does not have ninepatch splits: " + name)
        } else ()
        val patch: com.badlogic.gdx.graphics.g2d.NinePatch = new com.badlogic.gdx.graphics.g2d.NinePatch(region, splits(0), splits(1), splits(2), splits(3))
        val pads: scala.Array[scala.Int] = region.findValue("pad")
        if (pads != null) {
          patch.setPadding(pads(0), pads(1), pads(2), pads(3))
        } else ()
        return patch
      } else ()
    }; i = i + 1 } }
    return null
  }
  def getTextures(): com.badlogic.gdx.utils.ObjectSet[com.badlogic.gdx.graphics.Texture] = {
    return this.textures
  }
  def dispose(): scala.Unit = {
    for (texture <- this.textures) {
      texture.dispose()
    }
    this.textures.clear(0)
  }
}
object TextureAtlas {
  class TextureAtlasData {
    final val pages: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]]
    final val regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region] = new com.badlogic.gdx.utils.Array().asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]]
    def this(packFile: com.badlogic.gdx.files.FileHandle, imagesDir: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean) = {
      this()
      this.load(packFile, imagesDir, flip)
    }
    def load(packFile: com.badlogic.gdx.files.FileHandle, imagesDir: com.badlogic.gdx.files.FileHandle, flip: scala.Boolean): scala.Unit = {
      val entry: scala.Array[java.lang.String] = new scala.Array[java.lang.String](5)
      val pageFields: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]] = new com.badlogic.gdx.utils.ObjectMap(15, 0.99f).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]]]
      pageFields.put("size", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]() {
        override def parse(page: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page): scala.Unit = {
          page.width = java.lang.Integer.parseInt(entry(1))
          page.height = java.lang.Integer.parseInt(entry(2))
        }
      })
      pageFields.put("format", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]() {
        override def parse(page: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page): scala.Unit = {
          page.format = com.badlogic.gdx.graphics.Pixmap.Format.valueOf(entry(1))
        }
      })
      pageFields.put("filter", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]() {
        override def parse(page: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page): scala.Unit = {
          page.minFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.valueOf(entry(1))
          page.magFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.valueOf(entry(2))
          page.useMipMaps = page.minFilter.isMipMap()
        }
      })
      pageFields.put("repeat", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]() {
        override def parse(page: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page): scala.Unit = {
          if (entry(1).indexOf('x') != (-1)) {
            page.uWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat
          } else ()
          if (entry(1).indexOf('y') != (-1)) {
            page.vWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.Repeat
          } else ()
        }
      })
      pageFields.put("pma", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]() {
        override def parse(page: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page): scala.Unit = {
          page.pma = entry(1).equals("true")
        }
      })
      val hasIndexes: scala.Array[scala.Boolean] = scala.Array[scala.Boolean](false)
      val regionFields: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]] = new com.badlogic.gdx.utils.ObjectMap(127, 0.99f).asInstanceOf[com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]]]
      regionFields.put("xy", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          region.left = java.lang.Integer.parseInt(entry(1))
          region.top = java.lang.Integer.parseInt(entry(2))
        }
      })
      regionFields.put("size", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          region.width = java.lang.Integer.parseInt(entry(1))
          region.height = java.lang.Integer.parseInt(entry(2))
        }
      })
      regionFields.put("bounds", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          region.left = java.lang.Integer.parseInt(entry(1))
          region.top = java.lang.Integer.parseInt(entry(2))
          region.width = java.lang.Integer.parseInt(entry(3))
          region.height = java.lang.Integer.parseInt(entry(4))
        }
      })
      regionFields.put("offset", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          region.offsetX = java.lang.Integer.parseInt(entry(1))
          region.offsetY = java.lang.Integer.parseInt(entry(2))
        }
      })
      regionFields.put("orig", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          region.originalWidth = java.lang.Integer.parseInt(entry(1))
          region.originalHeight = java.lang.Integer.parseInt(entry(2))
        }
      })
      regionFields.put("offsets", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          region.offsetX = java.lang.Integer.parseInt(entry(1))
          region.offsetY = java.lang.Integer.parseInt(entry(2))
          region.originalWidth = java.lang.Integer.parseInt(entry(3))
          region.originalHeight = java.lang.Integer.parseInt(entry(4))
        }
      })
      regionFields.put("rotate", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          val value: java.lang.String = entry(1)
          if (value.equals("true")) {
            region.degrees = 90
          } else {
            if (!value.equals("false")) {
              region.degrees = java.lang.Integer.parseInt(value)
            } else ()
          }
          region.rotate = region.degrees == 90
        }
      })
      regionFields.put("index", new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
        override def parse(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Unit = {
          region.index = java.lang.Integer.parseInt(entry(1))
          if (region.index != (-1)) {
            hasIndexes(0) = true
          } else ()
        }
      })
      val reader: java.io.BufferedReader = packFile.reader(1024)
      var line: java.lang.String = null
      try {
        line = reader.readLine()
        while ((line != null) && (line.trim().length() == 0)) {
          line = reader.readLine()
        }
        while (true) {
          if ((line == null) || (line.trim().length() == 0)) {
            /* break */ ()
          } else ()
          if (com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.readEntry(entry, line) == 0) {
            /* break */ ()
          } else ()
          line = reader.readLine()
        }
        var page: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page = null
        var names: com.badlogic.gdx.utils.Array[java.lang.String] = null
        var values: com.badlogic.gdx.utils.Array[scala.Array[scala.Int]] = null
        while (true) {
          if (line == null) {
            /* break */ ()
          } else ()
          if (line.trim().length() == 0) {
            page = null
            line = reader.readLine()
          } else {
            if (page == null) {
              page = new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page()
              page.name = line
              page.textureFile = imagesDir.child(line)
              while (true) {
                if (com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.readEntry(entry, {
                  line = reader.readLine()
                  line
                }) == 0) {
                  /* break */ ()
                } else ()
                val field: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[?] = pageFields.get(entry(0)).asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[?]]
                if (field != null) {
                  field.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[java.lang.Object]].parse(page.asInstanceOf[java.lang.Object])
                } else ()
              }
              this.pages.add(page)
            } else {
              val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region = new com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region()
              region.page = page
              region.name = line.trim()
              if (flip) {
                region.flip = true
              } else ()
              while (true) {
                val count: scala.Int = com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.readEntry(entry, {
                  line = reader.readLine()
                  line
                })
                if (count == 0) {
                  /* break */ ()
                } else ()
                val field: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[?] = regionFields.get(entry(0)).asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[?]]
                if (field != null) {
                  field.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Field[java.lang.Object]].parse(region.asInstanceOf[java.lang.Object])
                } else {
                  if (names == null) {
                    names = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[java.lang.String]]
                    values = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[scala.Array[scala.Int]]]
                  } else ()
                  names.add(entry(0))
                  val entryValues: scala.Array[scala.Int] = new scala.Array[scala.Int](count);
                  { var i: scala.Int = 0; while (i < count) { {
                    try {
                      entryValues(i) = java.lang.Integer.parseInt(entry(i + 1))
                    } catch {
                      case ignored: java.lang.NumberFormatException => {
                        ()
                      }
                    }
                  }; i = i + 1 } }
                  values.add(entryValues)
                }
              }
              if ((region.originalWidth == 0) && (region.originalHeight == 0)) {
                region.originalWidth = region.width
                region.originalHeight = region.height
              } else ()
              if ((names != null) && (names.size > 0)) {
                region.names = names.toArray(((size: scala.Int) => new scala.Array[java.lang.String](size)))
                region.values = values.toArray(((size: scala.Int) => new scala.Array[scala.Array[scala.Int]](size)))
                names.clear()
                values.clear()
              } else ()
              this.regions.add(region)
            }
          }
        }
      } catch {
        case ex: java.lang.Exception => {
          throw new com.badlogic.gdx.utils.GdxRuntimeException(("Error reading texture atlas file: " + packFile) + (if (line == null) "" else "\nLine: " + line), ex)
        }
      } finally {
        com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
      }
      if (hasIndexes(0)) {
        this.regions.sort(new java.util.Comparator[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]() {
          override def compare(region1: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region, region2: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region): scala.Int = {
            var i1: scala.Int = region1.index
            if (i1 == (-1)) {
              i1 = java.lang.Integer.MAX_VALUE
            } else ()
            var i2: scala.Int = region2.index
            if (i2 == (-1)) {
              i2 = java.lang.Integer.MAX_VALUE
            } else ()
            return i1 - i2
          }
        }.asInstanceOf[java.util.Comparator[? >: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region]])
      } else ()
    }
    def getPages(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page] = {
      return this.pages
    }
    def getRegions(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Region] = {
      return this.regions
    }
  }
  object TextureAtlasData {
    private def readEntry(entry: scala.Array[java.lang.String], line$arg: java.lang.String): scala.Int = {
      {
        var line: java.lang.String = line$arg
        if (line == null) {
          return 0
        } else ()
        line = line.trim()
        if (line.length() == 0) {
          return 0
        } else ()
        val colon: scala.Int = line.indexOf(':')
        if (colon == (-1)) {
          return 0
        } else ()
        entry(0) = line.substring(0, colon).trim();
        { var i: scala.Int = 1; var lastMatch: scala.Int = colon + 1; while (true) { {
          val comma: scala.Int = line.indexOf(',', lastMatch)
          if (comma == (-1)) {
            entry(i) = line.substring(lastMatch).trim()
            return i
          } else ()
          entry(i) = line.substring(lastMatch, comma).trim()
          lastMatch = comma + 1
          if (i == 4) {
            return 4
          } else ()
        }; i = i + 1 } }
      }
      throw new java.lang.RuntimeException("unreachable")
    }
    trait Field[T <: java.lang.Object] {
      def parse(`object`: T): scala.Unit
    }
    class Page {
      var name: java.lang.String = null.asInstanceOf[java.lang.String]
      var textureFile: com.badlogic.gdx.files.FileHandle = null.asInstanceOf[com.badlogic.gdx.files.FileHandle]
      var texture: com.badlogic.gdx.graphics.Texture = null.asInstanceOf[com.badlogic.gdx.graphics.Texture]
      var width: scala.Float = 0.0f
      var height: scala.Float = 0.0f
      var useMipMaps: scala.Boolean = false
      var format: com.badlogic.gdx.graphics.Pixmap.Format = com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888
      var minFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
      var magFilter: com.badlogic.gdx.graphics.Texture.TextureFilter = com.badlogic.gdx.graphics.Texture.TextureFilter.Nearest
      var uWrap: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
      var vWrap: com.badlogic.gdx.graphics.Texture.TextureWrap = com.badlogic.gdx.graphics.Texture.TextureWrap.ClampToEdge
      var pma: scala.Boolean = false
    }
    class Region {
      var page: com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData.Page]
      var name: java.lang.String = null.asInstanceOf[java.lang.String]
      var left: scala.Int = 0
      var top: scala.Int = 0
      var width: scala.Int = 0
      var height: scala.Int = 0
      var offsetX: scala.Float = 0.0f
      var offsetY: scala.Float = 0.0f
      var originalWidth: scala.Int = 0
      var originalHeight: scala.Int = 0
      var degrees: scala.Int = 0
      var rotate: scala.Boolean = false
      var index: scala.Int = -1
      var names: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
      var values: scala.Array[scala.Array[scala.Int]] = null.asInstanceOf[scala.Array[scala.Array[scala.Int]]]
      var flip: scala.Boolean = false
      @com.badlogic.gdx.utils.Null
      def findValue(name: java.lang.String): scala.Array[scala.Int] = {
        if (this.names != null) {
          { var i: scala.Int = 0; val n: scala.Int = this.names.length; while (i < n) { {
            if (name.equals(this.names(i))) {
              return this.values(i)
            } else ()
          }; i = i + 1 } }
        } else ()
        return null
      }
    }
  }
  class AtlasRegion extends com.badlogic.gdx.graphics.g2d.TextureRegion {
    var index: scala.Int = -1
    var name: java.lang.String = null.asInstanceOf[java.lang.String]
    var offsetX: scala.Float = 0.0f
    var offsetY: scala.Float = 0.0f
    var packedWidth: scala.Int = 0
    var packedHeight: scala.Int = 0
    var originalWidth: scala.Int = 0
    var originalHeight: scala.Int = 0
    var rotate: scala.Boolean = false
    var degrees: scala.Int = 0
    var names: scala.Array[java.lang.String] = null.asInstanceOf[scala.Array[java.lang.String]]
    var values: scala.Array[scala.Array[scala.Int]] = null.asInstanceOf[scala.Array[scala.Array[scala.Int]]]
    def this(texture: com.badlogic.gdx.graphics.Texture, x: scala.Int, y: scala.Int, width: scala.Int, height: scala.Int) = {
      this()
      this.texture = texture
      (this.setRegion: (scala.Int, scala.Int, scala.Int, scala.Int) => scala.Unit)(x, y, width, height)
      this.originalWidth = width
      this.originalHeight = height
      this.packedWidth = width
      this.packedHeight = height
    }
    def this(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion) = {
      this()
      this.setRegion(region)
      this.index = region.index
      this.name = region.name
      this.offsetX = region.offsetX
      this.offsetY = region.offsetY
      this.packedWidth = region.packedWidth
      this.packedHeight = region.packedHeight
      this.originalWidth = region.originalWidth
      this.originalHeight = region.originalHeight
      this.rotate = region.rotate
      this.degrees = region.degrees
      this.names = region.names
      this.values = region.values
    }
    def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
      this()
      this.setRegion(region)
      this.packedWidth = region.getRegionWidth()
      this.packedHeight = region.getRegionHeight()
      this.originalWidth = this.packedWidth
      this.originalHeight = this.packedHeight
    }
    @java.lang.Override
    def flip(x: scala.Boolean, y: scala.Boolean): scala.Unit = {
      super.flip(x, y)
      if (x) {
        this.offsetX = (this.originalWidth - this.offsetX) - this.getRotatedPackedWidth()
      } else ()
      if (y) {
        this.offsetY = (this.originalHeight - this.offsetY) - this.getRotatedPackedHeight()
      } else ()
    }
    def getRotatedPackedWidth(): scala.Float = {
      return if (this.rotate) this.packedHeight else this.packedWidth
    }
    def getRotatedPackedHeight(): scala.Float = {
      return if (this.rotate) this.packedWidth else this.packedHeight
    }
    @com.badlogic.gdx.utils.Null
    def findValue(name: java.lang.String): scala.Array[scala.Int] = {
      if (this.names != null) {
        { var i: scala.Int = 0; val n: scala.Int = this.names.length; while (i < n) { {
          if (name.equals(this.names(i))) {
            return this.values(i)
          } else ()
        }; i = i + 1 } }
      } else ()
      return null
    }
    def toString(): java.lang.String = {
      return this.name
    }
  }
  object AtlasRegion {
    export com.badlogic.gdx.graphics.g2d.TextureRegion.*
  }
  class AtlasSprite extends com.badlogic.gdx.graphics.g2d.Sprite {
    var region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]
    var originalOffsetX: scala.Float = 0.0f
    var originalOffsetY: scala.Float = 0.0f
    def this(region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion) = {
      this()
      this.region = new com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion(region)
      this.originalOffsetX = region.offsetX
      this.originalOffsetY = region.offsetY
      this.setRegion(region)
      this.setOrigin(region.originalWidth / 2.0f, region.originalHeight / 2.0f)
      val width: scala.Int = region.getRegionWidth()
      val height: scala.Int = region.getRegionHeight()
      if (region.rotate) {
        super.rotate90(true)
        super.setBounds(region.offsetX, region.offsetY, height, width)
      } else {
        super.setBounds(region.offsetX, region.offsetY, width, height)
      }
      this.setColor(1, 1, 1, 1)
    }
    def this(sprite: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasSprite) = {
      this()
      this.region = sprite.region
      this.originalOffsetX = sprite.originalOffsetX
      this.originalOffsetY = sprite.originalOffsetY
      this.set(sprite)
    }
    @java.lang.Override
    def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
      super.setPosition(x + this.region.offsetX, y + this.region.offsetY)
    }
    @java.lang.Override
    def setX(x: scala.Float): scala.Unit = {
      super.setX(x + this.region.offsetX)
    }
    @java.lang.Override
    def setY(y: scala.Float): scala.Unit = {
      super.setY(y + this.region.offsetY)
    }
    @java.lang.Override
    def setBounds(x: scala.Float, y: scala.Float, width: scala.Float, height: scala.Float): scala.Unit = {
      val widthRatio: scala.Float = width / this.region.originalWidth
      val heightRatio: scala.Float = height / this.region.originalHeight
      this.region.offsetX = this.originalOffsetX * widthRatio
      this.region.offsetY = this.originalOffsetY * heightRatio
      val packedWidth: scala.Int = if (this.region.rotate) this.region.packedHeight else this.region.packedWidth
      val packedHeight: scala.Int = if (this.region.rotate) this.region.packedWidth else this.region.packedHeight
      super.setBounds(x + this.region.offsetX, y + this.region.offsetY, packedWidth * widthRatio, packedHeight * heightRatio)
    }
    @java.lang.Override
    def setSize(width: scala.Float, height: scala.Float): scala.Unit = {
      this.setBounds(this.getX(), this.getY(), width, height)
    }
    @java.lang.Override
    def setOrigin(originX: scala.Float, originY: scala.Float): scala.Unit = {
      super.setOrigin(originX - this.region.offsetX, originY - this.region.offsetY)
    }
    @java.lang.Override
    def setOriginCenter(): scala.Unit = {
      super.setOrigin((width / 2) - this.region.offsetX, (height / 2) - this.region.offsetY)
    }
    @java.lang.Override
    def flip(x: scala.Boolean, y: scala.Boolean): scala.Unit = {
      if (this.region.rotate) {
        super.flip(y, x)
      } else {
        super.flip(x, y)
      }
      val oldOriginX: scala.Float = this.getOriginX()
      val oldOriginY: scala.Float = this.getOriginY()
      val oldOffsetX: scala.Float = this.region.offsetX
      val oldOffsetY: scala.Float = this.region.offsetY
      val widthRatio: scala.Float = this.getWidthRatio()
      val heightRatio: scala.Float = this.getHeightRatio()
      this.region.offsetX = this.originalOffsetX
      this.region.offsetY = this.originalOffsetY
      this.region.flip(x, y)
      this.originalOffsetX = this.region.offsetX
      this.originalOffsetY = this.region.offsetY
      this.region.offsetX = this.region.offsetX * widthRatio
      this.region.offsetY = this.region.offsetY * heightRatio
      this.translate(this.region.offsetX - oldOffsetX, this.region.offsetY - oldOffsetY)
      this.setOrigin(oldOriginX, oldOriginY)
    }
    @java.lang.Override
    def rotate90(clockwise: scala.Boolean): scala.Unit = {
      super.rotate90(clockwise)
      val oldOriginX: scala.Float = this.getOriginX()
      val oldOriginY: scala.Float = this.getOriginY()
      val oldOffsetX: scala.Float = this.region.offsetX
      val oldOffsetY: scala.Float = this.region.offsetY
      val widthRatio: scala.Float = this.getWidthRatio()
      val heightRatio: scala.Float = this.getHeightRatio()
      if (clockwise) {
        this.region.offsetX = oldOffsetY
        this.region.offsetY = ((this.region.originalHeight * heightRatio) - oldOffsetX) - (this.region.packedWidth * widthRatio)
      } else {
        this.region.offsetX = ((this.region.originalWidth * widthRatio) - oldOffsetY) - (this.region.packedHeight * heightRatio)
        this.region.offsetY = oldOffsetX
      }
      this.translate(this.region.offsetX - oldOffsetX, this.region.offsetY - oldOffsetY)
      this.setOrigin(oldOriginX, oldOriginY)
    }
    @java.lang.Override
    def getX(): scala.Float = {
      return super.getX() - this.region.offsetX
    }
    @java.lang.Override
    def getY(): scala.Float = {
      return super.getY() - this.region.offsetY
    }
    @java.lang.Override
    def getOriginX(): scala.Float = {
      return super.getOriginX() + this.region.offsetX
    }
    @java.lang.Override
    def getOriginY(): scala.Float = {
      return super.getOriginY() + this.region.offsetY
    }
    @java.lang.Override
    def getWidth(): scala.Float = {
      return (super.getWidth() / this.region.getRotatedPackedWidth()) * this.region.originalWidth
    }
    @java.lang.Override
    def getHeight(): scala.Float = {
      return (super.getHeight() / this.region.getRotatedPackedHeight()) * this.region.originalHeight
    }
    def getWidthRatio(): scala.Float = {
      return super.getWidth() / this.region.getRotatedPackedWidth()
    }
    def getHeightRatio(): scala.Float = {
      return super.getHeight() / this.region.getRotatedPackedHeight()
    }
    def getAtlasRegion(): com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = {
      return this.region
    }
    def toString(): java.lang.String = {
      return this.region.toString()
    }
  }
  object AtlasSprite {
    export com.badlogic.gdx.graphics.g2d.Sprite.*
  }
}