package com.badlogic.gdx.maps.tiled.tiles

class AnimatedTiledMapTile extends com.badlogic.gdx.maps.tiled.TiledMapTile {
  private var id: scala.Int = 0
  private var blendMode: com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode = com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode.ALPHA
  private var properties: com.badlogic.gdx.maps.MapProperties = null.asInstanceOf[com.badlogic.gdx.maps.MapProperties]
  private var objects: com.badlogic.gdx.maps.MapObjects = null.asInstanceOf[com.badlogic.gdx.maps.MapObjects]
  private var frameTiles: scala.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile] = null.asInstanceOf[scala.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile]]
  private var animationIntervals: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  private var loopDuration: scala.Int = 0
  def this(interval: scala.Float, frameTiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile]) = {
    this()
    this.frameTiles = new scala.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile](frameTiles.size)
    this.loopDuration = frameTiles.size * (interval * 1000.0f).asInstanceOf[scala.Int]
    this.animationIntervals = new scala.Array[scala.Int](frameTiles.size);
    { var i: scala.Int = 0; while (i < frameTiles.size) { {
      this.frameTiles(i) = frameTiles.get(i)
      this.animationIntervals(i) = (interval * 1000.0f).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    }; i = i + 1 } }
  }
  def this(intervals: com.badlogic.gdx.utils.IntArray, frameTiles: com.badlogic.gdx.utils.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile]) = {
    this()
    this.frameTiles = new scala.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile](frameTiles.size)
    this.animationIntervals = intervals.toArray()
    this.loopDuration = 0;
    { var i: scala.Int = 0; while (i < intervals.size) { {
      this.frameTiles(i) = frameTiles.get(i)
      this.loopDuration = this.loopDuration + intervals.get(i)
    }; i = i + 1 } }
  }
  def getId(): scala.Int = {
    return this.id
  }
  def setId(id: scala.Int): scala.Unit = {
    this.id = id
  }
  def getBlendMode(): com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode = {
    return this.blendMode
  }
  def setBlendMode(blendMode: com.badlogic.gdx.maps.tiled.TiledMapTile.BlendMode): scala.Unit = {
    this.blendMode = blendMode
  }
  def getCurrentFrameIndex(): scala.Int = {
    var currentTime: scala.Int = (AnimatedTiledMapTile.lastTiledMapRenderTime % this.loopDuration).asInstanceOf[scala.Int].asInstanceOf[scala.Int];
    { var i: scala.Int = 0; while (i < this.animationIntervals.length) { {
      val animationInterval: scala.Int = this.animationIntervals(i)
      if (currentTime <= animationInterval) {
        return i
      } else ()
      currentTime = currentTime - animationInterval
    }; i = i + 1 } }
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Could not determine current animation frame in AnimatedTiledMapTile.  This should never happen.")
  }
  def getCurrentFrame(): com.badlogic.gdx.maps.tiled.TiledMapTile = {
    return this.frameTiles(this.getCurrentFrameIndex())
  }
  def getTextureRegion(): com.badlogic.gdx.graphics.g2d.TextureRegion = {
    return this.getCurrentFrame().getTextureRegion()
  }
  def setTextureRegion(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot set the texture region of AnimatedTiledMapTile.")
  }
  def getOffsetX(): scala.Float = {
    return this.getCurrentFrame().getOffsetX()
  }
  def setOffsetX(offsetX: scala.Float): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot set offset of AnimatedTiledMapTile.")
  }
  def getOffsetY(): scala.Float = {
    return this.getCurrentFrame().getOffsetY()
  }
  def setOffsetY(offsetY: scala.Float): scala.Unit = {
    throw new com.badlogic.gdx.utils.GdxRuntimeException("Cannot set offset of AnimatedTiledMapTile.")
  }
  def getAnimationIntervals(): scala.Array[scala.Int] = {
    return this.animationIntervals
  }
  def setAnimationIntervals(intervals: scala.Array[scala.Int]): scala.Unit = {
    if (intervals.length == this.animationIntervals.length) {
      this.animationIntervals = intervals
      this.loopDuration = 0;
      { var i: scala.Int = 0; while (i < intervals.length) { {
        this.loopDuration = this.loopDuration + intervals(i)
      }; i = i + 1 } }
    } else {
      throw new com.badlogic.gdx.utils.GdxRuntimeException(((("Cannot set " + intervals.length) + " frame intervals. The given int[] must have a size of ") + this.animationIntervals.length) + ".")
    }
  }
  def getProperties(): com.badlogic.gdx.maps.MapProperties = {
    if (this.properties == null) {
      this.properties = new com.badlogic.gdx.maps.MapProperties()
    } else ()
    return this.properties
  }
  def getObjects(): com.badlogic.gdx.maps.MapObjects = {
    if (this.objects == null) {
      this.objects = new com.badlogic.gdx.maps.MapObjects()
    } else ()
    return this.objects
  }
  def getFrameTiles(): scala.Array[com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile] = {
    return this.frameTiles
  }
}
object AnimatedTiledMapTile {
  export com.badlogic.gdx.maps.tiled.TiledMapTile.*
  private var lastTiledMapRenderTime: scala.Long = 0
  private final val initialTimeOffset: scala.Long = com.badlogic.gdx.utils.TimeUtils.millis()
  def updateAnimationBaseTime(): scala.Unit = {
    AnimatedTiledMapTile.lastTiledMapRenderTime = com.badlogic.gdx.utils.TimeUtils.millis() - AnimatedTiledMapTile.initialTimeOffset
  }
}