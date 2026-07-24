package com.badlogic.gdx.graphics.g3d.particles

abstract class ParticleSorter {
  var camera: com.badlogic.gdx.graphics.Camera = null.asInstanceOf[com.badlogic.gdx.graphics.Camera]
  def sort[T <: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData](renderData: com.badlogic.gdx.utils.Array[T]): scala.Array[scala.Int]
  def setCamera(camera: com.badlogic.gdx.graphics.Camera): scala.Unit = {
    this.camera = camera
  }
  def ensureCapacity(capacity: scala.Int): scala.Unit = {
    ()
  }
}
object ParticleSorter {
  final val TMP_V1: com.badlogic.gdx.math.Vector3 = new com.badlogic.gdx.math.Vector3()
  class None extends ParticleSorter {
    var currentCapacity: scala.Int = 0
    var indices: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    def ensureCapacity(capacity: scala.Int): scala.Unit = {
      if (this.currentCapacity < capacity) {
        this.indices = new scala.Array[scala.Int](capacity);
        { var i: scala.Int = 0; while (i < capacity) { {
          this.indices(i) = i
        }; i = i + 1 } }
        this.currentCapacity = capacity
      } else ()
    }
    def sort[T <: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData](renderData: com.badlogic.gdx.utils.Array[T]): scala.Array[scala.Int] = {
      return this.indices
    }
  }
  class Distance extends ParticleSorter {
    private var distances: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    private var particleIndices: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    private var particleOffsets: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    private var currentSize: scala.Int = 0
    def ensureCapacity(capacity: scala.Int): scala.Unit = {
      if (this.currentSize < capacity) {
        this.distances = new scala.Array[scala.Float](capacity)
        this.particleIndices = new scala.Array[scala.Int](capacity)
        this.particleOffsets = new scala.Array[scala.Int](capacity)
        this.currentSize = capacity
      } else ()
    }
    def sort[T <: com.badlogic.gdx.graphics.g3d.particles.renderers.ParticleControllerRenderData](renderData: com.badlogic.gdx.utils.Array[T]): scala.Array[scala.Int] = {
      val `val`: scala.Array[scala.Float] = this.camera.view.`val`
      val cx: scala.Float = `val`(com.badlogic.gdx.math.Matrix4.M20)
      val cy: scala.Float = `val`(com.badlogic.gdx.math.Matrix4.M21)
      val cz: scala.Float = `val`(com.badlogic.gdx.math.Matrix4.M22)
      var count: scala.Int = 0
      var i: scala.Int = 0
      for (data <- renderData) {
        { var k: scala.Int = 0; val c: scala.Int = i + data.controller.particles.size; while (i < c) { {
          this.distances(i) = ((cx * data.positionChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.XOffset)) + (cy * data.positionChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.YOffset))) + (cz * data.positionChannel.data(k + com.badlogic.gdx.graphics.g3d.particles.ParticleChannels.ZOffset))
          this.particleIndices(i) = i
        }; i = i + 1; k = k + data.positionChannel.strideSize } }
        count = count + data.controller.particles.size
      }
      this.qsort(0, count - 1);
      { i = 0; while (i < count) { {
        this.particleOffsets(this.particleIndices(i)) = i
      }; i = i + 1 } }
      return this.particleOffsets
    }
    def qsort(si: scala.Int, ei: scala.Int): scala.Unit = {
      if (si < ei) {
        var tmp: scala.Float = 0.0f
        var tmpIndex: scala.Int = 0
        var particlesPivotIndex: scala.Int = 0
        if ((ei - si) <= 8) {
          { var i: scala.Int = si; while (i <= ei) { {
            { var j: scala.Int = i; while ((j > si) && (this.distances(j - 1) > this.distances(j))) { {
              tmp = this.distances(j)
              this.distances(j) = this.distances(j - 1)
              this.distances(j - 1) = tmp
              tmpIndex = this.particleIndices(j)
              this.particleIndices(j) = this.particleIndices(j - 1)
              this.particleIndices(j - 1) = tmpIndex
            }; j = j - 1 } }
          }; i = i + 1 } }
          return
        } else ()
        val pivot: scala.Float = this.distances(si)
        var i: scala.Int = si + 1
        particlesPivotIndex = this.particleIndices(si);
        { var j: scala.Int = si + 1; while (j <= ei) { {
          if (pivot > this.distances(j)) {
            if (j > i) {
              tmp = this.distances(j)
              this.distances(j) = this.distances(i)
              this.distances(i) = tmp
              tmpIndex = this.particleIndices(j)
              this.particleIndices(j) = this.particleIndices(i)
              this.particleIndices(i) = tmpIndex
            } else ()
            i = i + 1
          } else ()
        }; j = j + 1 } }
        this.distances(si) = this.distances(i - 1)
        this.distances(i - 1) = pivot
        this.particleIndices(si) = this.particleIndices(i - 1)
        this.particleIndices(i - 1) = particlesPivotIndex
        this.qsort(si, i - 2)
        this.qsort(i, ei)
      } else ()
    }
  }
}