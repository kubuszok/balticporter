package com.badlogic.gdx.graphics.g2d

class ParticleEffect extends com.badlogic.gdx.utils.Disposable {
  private var emitters: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.ParticleEmitter] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.ParticleEmitter]]
  private var bounds: com.badlogic.gdx.math.collision.BoundingBox = null.asInstanceOf[com.badlogic.gdx.math.collision.BoundingBox]
  private var ownsTexture: scala.Boolean = false
  var xSizeScale: scala.Float = 1.0f
  var ySizeScale: scala.Float = 1.0f
  var motionScale: scala.Float = 1.0f
  def this(effect: ParticleEffect) = {
    this()
    this.emitters = new com.badlogic.gdx.utils.Array(true, effect.emitters.size).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.ParticleEmitter]];
    { var i: scala.Int = 0; val n: scala.Int = effect.emitters.size; while (i < n) { {
      this.emitters.add(this.newEmitter(effect.emitters.get(i)))
    }; i = i + 1 } }
  }
  this.emitters = new com.badlogic.gdx.utils.Array(8).asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.ParticleEmitter]]
  def start(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).start()
    }; i = i + 1 } }
  }
  def reset(): scala.Unit = {
    this.reset(true, true)
  }
  def reset(resetScaling: scala.Boolean): scala.Unit = {
    this.reset(resetScaling, true)
  }
  def reset(resetScaling: scala.Boolean, start: scala.Boolean): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).reset(start)
    }; i = i + 1 } }
    if (resetScaling && (((this.xSizeScale != 1.0f) || (this.ySizeScale != 1.0f)) || (this.motionScale != 1.0f))) {
      this.scaleEffect(1.0f / this.xSizeScale, 1.0f / this.ySizeScale, 1.0f / this.motionScale)
      this.xSizeScale = {
        this.ySizeScale = {
          this.motionScale = 1.0f
          this.motionScale
        }
        this.ySizeScale
      }
    } else ()
  }
  def update(delta: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).update(delta)
    }; i = i + 1 } }
  }
  def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).draw(spriteBatch)
    }; i = i + 1 } }
  }
  def draw(spriteBatch: com.badlogic.gdx.graphics.g2d.Batch, delta: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).draw(spriteBatch, delta)
    }; i = i + 1 } }
  }
  def allowCompletion(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).allowCompletion()
    }; i = i + 1 } }
  }
  def isComplete(): scala.Boolean = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.emitters.get(i)
      if (!emitter.isComplete()) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def setDuration(duration: scala.Int): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.emitters.get(i)
      emitter.setContinuous(false)
      emitter.duration = duration
      emitter.durationTimer = 0
    }; i = i + 1 } }
  }
  def setPosition(x: scala.Float, y: scala.Float): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).setPosition(x, y)
    }; i = i + 1 } }
  }
  def setFlip(flipX: scala.Boolean, flipY: scala.Boolean): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).setFlip(flipX, flipY)
    }; i = i + 1 } }
  }
  def flipY(): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).flipY()
    }; i = i + 1 } }
  }
  def getEmitters(): com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.ParticleEmitter] = {
    return this.emitters
  }
  def findEmitter(name: java.lang.String): com.badlogic.gdx.graphics.g2d.ParticleEmitter = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.emitters.get(i)
      if (emitter.getName().equals(name)) {
        return emitter
      } else ()
    }; i = i + 1 } }
    return null
  }
  def preAllocateParticles(): scala.Unit = {
    for (emitter <- this.emitters) {
      emitter.preAllocateParticles()
    }
  }
  def save(output: java.io.Writer): scala.Unit = {
    var index: scala.Int = 0;
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.emitters.get(i)
      if ({ index += 1; index } > 0) {
        output.write("\n")
      } else ()
      emitter.save(output)
    }; i = i + 1 } }
  }
  def load(effectFile: com.badlogic.gdx.files.FileHandle, imagesDir: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    this.loadEmitters(effectFile)
    this.loadEmitterImages(imagesDir)
  }
  def load(effectFile: com.badlogic.gdx.files.FileHandle, atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas): scala.Unit = {
    this.load(effectFile, atlas, null)
  }
  def load(effectFile: com.badlogic.gdx.files.FileHandle, atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas, atlasPrefix: java.lang.String): scala.Unit = {
    this.loadEmitters(effectFile)
    this.loadEmitterImages(atlas, atlasPrefix)
  }
  def loadEmitters(effectFile: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    val input: java.io.InputStream = effectFile.read()
    this.emitters.clear()
    var reader: java.io.BufferedReader = null
    try {
      reader = new java.io.BufferedReader(new java.io.InputStreamReader(input), 512)
      while (true) {
        val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.newEmitter(reader)
        this.emitters.add(emitter)
        if (reader.readLine() == null) {
          /* break */ ()
        } else ()
      }
    } catch {
      case ex: java.io.IOException => {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("Error loading effect: " + effectFile, ex)
      }
    } finally {
      com.badlogic.gdx.utils.StreamUtils.closeQuietly(reader)
    }
  }
  def loadEmitterImages(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas): scala.Unit = {
    this.loadEmitterImages(atlas, null)
  }
  def loadEmitterImages(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas, atlasPrefix: java.lang.String): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.emitters.get(i)
      if (emitter.getImagePaths().size == 0) {
        /* continue */ ()
      } else ()
      val sprites: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite]()
      for (imagePath <- emitter.getImagePaths()) {
        var imageName: java.lang.String = new java.io.File(imagePath.replace('\\', '/')).getName()
        val lastDotIndex: scala.Int = imageName.lastIndexOf('.')
        if (lastDotIndex != (-1)) {
          imageName = imageName.substring(0, lastDotIndex)
        } else ()
        if (atlasPrefix != null) {
          imageName = atlasPrefix + imageName
        } else ()
        val sprite: com.badlogic.gdx.graphics.g2d.Sprite = atlas.createSprite(imageName)
        if (sprite == null) {
          throw new java.lang.IllegalArgumentException("Atlas is missing region: " + imageName)
        } else ()
        sprites.add(sprite)
      }
      emitter.setSprites(sprites)
    }; i = i + 1 } }
  }
  def loadEmitterImages(imagesDir: com.badlogic.gdx.files.FileHandle): scala.Unit = {
    this.ownsTexture = true
    val loadedSprites: com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g2d.Sprite] = new com.badlogic.gdx.utils.ObjectMap[java.lang.String, com.badlogic.gdx.graphics.g2d.Sprite](this.emitters.size);
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.emitters.get(i)
      if (emitter.getImagePaths().size == 0) {
        /* continue */ ()
      } else ()
      val sprites: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite] = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g2d.Sprite]()
      for (imagePath <- emitter.getImagePaths()) {
        val imageName: java.lang.String = new java.io.File(imagePath.replace('\\', '/')).getName()
        var sprite: com.badlogic.gdx.graphics.g2d.Sprite = loadedSprites.get(imageName)
        if (sprite == null) {
          sprite = new com.badlogic.gdx.graphics.g2d.Sprite(this.loadTexture(imagesDir.child(imageName)))
          loadedSprites.put(imageName, sprite)
        } else ()
        sprites.add(sprite)
      }
      emitter.setSprites(sprites)
    }; i = i + 1 } }
  }
  def newEmitter(reader: java.io.BufferedReader): com.badlogic.gdx.graphics.g2d.ParticleEmitter = {
    return new com.badlogic.gdx.graphics.g2d.ParticleEmitter(reader)
  }
  def newEmitter(emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter): com.badlogic.gdx.graphics.g2d.ParticleEmitter = {
    return new com.badlogic.gdx.graphics.g2d.ParticleEmitter(emitter)
  }
  def loadTexture(file: com.badlogic.gdx.files.FileHandle): com.badlogic.gdx.graphics.Texture = {
    return new com.badlogic.gdx.graphics.Texture(file, false)
  }
  def dispose(): scala.Unit = {
    if (!this.ownsTexture) {
      return
    } else ();
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      val emitter: com.badlogic.gdx.graphics.g2d.ParticleEmitter = this.emitters.get(i)
      for (sprite <- emitter.getSprites()) {
        sprite.getTexture().dispose()
      }
    }; i = i + 1 } }
  }
  def getBoundingBox(): com.badlogic.gdx.math.collision.BoundingBox = {
    if (this.bounds == null) {
      this.bounds = new com.badlogic.gdx.math.collision.BoundingBox()
    } else ()
    var bounds: com.badlogic.gdx.math.collision.BoundingBox = this.bounds
    bounds.inf()
    for (emitter <- this.emitters) {
      bounds.ext(emitter.getBoundingBox())
    }
    return bounds
  }
  def scaleEffect(scaleFactor: scala.Float): scala.Unit = {
    this.scaleEffect(scaleFactor, scaleFactor, scaleFactor)
  }
  def scaleEffect(scaleFactor: scala.Float, motionScaleFactor: scala.Float): scala.Unit = {
    this.scaleEffect(scaleFactor, scaleFactor, motionScaleFactor)
  }
  def scaleEffect(xSizeScaleFactor: scala.Float, ySizeScaleFactor: scala.Float, motionScaleFactor: scala.Float): scala.Unit = {
    this.xSizeScale = this.xSizeScale * xSizeScaleFactor
    this.ySizeScale = this.ySizeScale * ySizeScaleFactor
    this.motionScale = this.motionScale * motionScaleFactor
    for (particleEmitter <- this.emitters) {
      particleEmitter.scaleSize(xSizeScaleFactor, ySizeScaleFactor)
      particleEmitter.scaleMotion(motionScaleFactor)
    }
  }
  def setEmittersCleanUpBlendFunction(cleanUpBlendFunction: scala.Boolean): scala.Unit = {
    { var i: scala.Int = 0; val n: scala.Int = this.emitters.size; while (i < n) { {
      this.emitters.get(i).setCleansUpBlendFunction(cleanUpBlendFunction)
    }; i = i + 1 } }
  }
}