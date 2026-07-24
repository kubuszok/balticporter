package com.badlogic.gdx.graphics.g3d.particles.influencers

abstract class RegionInfluencer extends com.badlogic.gdx.graphics.g3d.particles.influencers.Influencer {
  var regions: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion]]
  var regionChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
  var atlasName: java.lang.String = null.asInstanceOf[java.lang.String]
  def this(regionsCount: scala.Int) = {
    this()
    this.regions = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion](false, regionsCount, (() => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion]()))
  }
  def this(regions: scala.Array[com.badlogic.gdx.graphics.g2d.TextureRegion]) = {
    this()
    this.setAtlasName(null)
    this.regions = new com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion](false, regions.length, (() => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion]()))
    this.add(regions)
  }
  def this(texture: com.badlogic.gdx.graphics.Texture) = {
    this(new com.badlogic.gdx.graphics.g2d.TextureRegion(texture))
  }
  def this(regionInfluencer: RegionInfluencer) = {
    this(regionInfluencer.regions.size)
    this.regions.ensureCapacity(regionInfluencer.regions.size);
    { var i: scala.Int = 0; while (i < regionInfluencer.regions.size) { {
      this.regions.add(new com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion(regionInfluencer.regions.get(i).asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion]))
    }; i = i + 1 } }
  }
  def this() = {
    this(1)
    val aspectRegion: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion = new com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion()
    aspectRegion.u = {
      aspectRegion.v = 0
      aspectRegion.v
    }
    aspectRegion.u2 = {
      aspectRegion.v2 = 1
      aspectRegion.v2
    }
    aspectRegion.halfInvAspectRatio = 0.5f
    this.regions.add(aspectRegion)
  }
  def setAtlasName(atlasName: java.lang.String): scala.Unit = {
    this.atlasName = atlasName
  }
  def add(regions: scala.Array[com.badlogic.gdx.graphics.g2d.TextureRegion]): scala.Unit = {
    this.regions.ensureCapacity(regions.length)
    for (region <- regions) {
      this.regions.add(new com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion(region))
    }
  }
  def clear(): scala.Unit = {
    this.atlasName = null
    this.regions.clear()
  }
  def load(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    super.load(manager, resources)
    val data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.getSaveData(RegionInfluencer.ASSET_DATA)
    if (data == null) {
      return
    } else ()
    var atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas = null.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
    atlas = manager.get(data.loadAsset()).asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas].asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas]
    for (atr <- this.regions) {
      atr.updateUV(atlas)
    }
  }
  def save(manager: com.badlogic.gdx.assets.AssetManager, resources: com.badlogic.gdx.graphics.g3d.particles.ResourceData[?]): scala.Unit = {
    super.save(manager, resources)
    if (this.atlasName != null) {
      var data: com.badlogic.gdx.graphics.g3d.particles.ResourceData.SaveData = resources.getSaveData(RegionInfluencer.ASSET_DATA)
      if (data == null) {
        data = resources.createSaveData(RegionInfluencer.ASSET_DATA)
      } else ()
      data.saveAsset(this.atlasName, classOf[com.badlogic.gdx.graphics.g2d.TextureAtlas])
    } else ()
  }
  def allocateChannels(): scala.Unit = {
    this.regionChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.TextureRegion)
  }
  def write(json: com.badlogic.gdx.utils.Json): scala.Unit = {
    json.writeValue("regions", this.regions, classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion])
  }
  def read(json: com.badlogic.gdx.utils.Json, jsonData: com.badlogic.gdx.utils.JsonValue): scala.Unit = {
    this.regions.clear()
    this.regions.addAll(json.readValue("regions", classOf[com.badlogic.gdx.utils.Array[?]], classOf[com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion], jsonData))
  }
}
object RegionInfluencer {
  private final val ASSET_DATA: java.lang.String = "atlasAssetData"
  class Single extends RegionInfluencer {
    def this(regionInfluencer: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Single) = {
      this()
    }
    def this(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
      this()
    }
    def this(texture: com.badlogic.gdx.graphics.Texture) = {
      this()
    }
    def init(): scala.Unit = {
      val region: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion = this.regions.items(0);
      { var i: scala.Int = 0; val c: scala.Int = this.controller.emitter.maxParticleCount * this.regionChannel.strideSize; while (i < c) { {
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.UOffset) = region.u
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VOffset) = region.v
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.U2Offset) = region.u2
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.V2Offset) = region.v2
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfWidthOffset) = 0.5f
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfHeightOffset) = region.halfInvAspectRatio
      }; i = i + this.regionChannel.strideSize } }
    }
    def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Single = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Single(this)
    }
  }
  class Random extends RegionInfluencer {
    def this(regionInfluencer: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Random) = {
      this()
    }
    def this(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
      this()
    }
    def this(texture: com.badlogic.gdx.graphics.Texture) = {
      this()
    }
    def activateParticles(startIndex: scala.Int, count: scala.Int): scala.Unit = {
      { var i: scala.Int = startIndex * this.regionChannel.strideSize; val c: scala.Int = i + (count * this.regionChannel.strideSize); while (i < c) { {
        val region: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion = regions.random()
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.UOffset) = region.u
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VOffset) = region.v
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.U2Offset) = region.u2
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.V2Offset) = region.v2
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfWidthOffset) = 0.5f
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfHeightOffset) = region.halfInvAspectRatio
      }; i = i + this.regionChannel.strideSize } }
    }
    def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Random = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Random(this)
    }
  }
  class Animated extends RegionInfluencer {
    var lifeChannel: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel = null.asInstanceOf[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#FloatChannel]
    def this(regionInfluencer: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Animated) = {
      this()
    }
    def this(textureRegion: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
      this()
    }
    def this(texture: com.badlogic.gdx.graphics.Texture) = {
      this()
    }
    def allocateChannels(): scala.Unit = {
      super.allocateChannels()
      this.lifeChannel = this.controller.particles.addChannel(com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.Life)
    }
    def update(): scala.Unit = {
      { var i: scala.Int = 0; var l: scala.Int = com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.LifePercentOffset; val c: scala.Int = this.controller.particles.size * this.regionChannel.strideSize; while (i < c) { {
        val region: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion = regions.get((this.lifeChannel.data(l) * (this.regions.size - 1)).asInstanceOf[scala.Int].asInstanceOf[scala.Int])
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.UOffset) = region.u
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.VOffset) = region.v
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.U2Offset) = region.u2
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.V2Offset) = region.v2
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfWidthOffset) = 0.5f
        this.regionChannel.data(i + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.HalfHeightOffset) = region.halfInvAspectRatio
      }; i = i + this.regionChannel.strideSize; l = l + this.lifeChannel.strideSize } }
    }
    def copy(): com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Animated = {
      return new com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.Animated(this)
    }
  }
  class AspectTextureRegion {
    var u: scala.Float = 0.0f
    var v: scala.Float = 0.0f
    var u2: scala.Float = 0.0f
    var v2: scala.Float = 0.0f
    var halfInvAspectRatio: scala.Float = 0.0f
    var imageName: java.lang.String = null.asInstanceOf[java.lang.String]
    def this(aspectTextureRegion: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion) = {
      this()
      this.set(aspectTextureRegion)
    }
    def this(region: com.badlogic.gdx.graphics.g2d.TextureRegion) = {
      this()
      this.set(region)
    }
    def set(region: com.badlogic.gdx.graphics.g2d.TextureRegion): scala.Unit = {
      this.u = region.getU()
      this.v = region.getV()
      this.u2 = region.getU2()
      this.v2 = region.getV2()
      this.halfInvAspectRatio = 0.5f * (region.getRegionHeight().asInstanceOf[scala.Float] / region.getRegionWidth())
      if (region.isInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion]) {
        this.imageName = region.asInstanceOf[com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion].name
      } else ()
    }
    def set(aspectTextureRegion: com.badlogic.gdx.graphics.g3d.particles.influencers.RegionInfluencer.AspectTextureRegion): scala.Unit = {
      this.u = aspectTextureRegion.u
      this.v = aspectTextureRegion.v
      this.u2 = aspectTextureRegion.u2
      this.v2 = aspectTextureRegion.v2
      this.halfInvAspectRatio = aspectTextureRegion.halfInvAspectRatio
      this.imageName = aspectTextureRegion.imageName
    }
    def updateUV(atlas: com.badlogic.gdx.graphics.g2d.TextureAtlas): scala.Unit = {
      if (this.imageName == null) {
        return
      } else ()
      val region: com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion = atlas.findRegion(this.imageName)
      this.u = region.getU()
      this.v = region.getV()
      this.u2 = region.getU2()
      this.v2 = region.getV2()
      this.halfInvAspectRatio = 0.5f * (region.getRegionHeight().asInstanceOf[scala.Float] / region.getRegionWidth())
    }
  }
}