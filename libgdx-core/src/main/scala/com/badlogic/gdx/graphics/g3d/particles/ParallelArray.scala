package com.badlogic.gdx.graphics.g3d.particles

class ParallelArray(capacity$p: scala.Int) {
  var arrays: com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel] = null.asInstanceOf[com.badlogic.gdx.utils.Array[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel]]
  var capacity: scala.Int = 0
  var size: scala.Int = 0
  this.arrays = new com.badlogic.gdx.utils.Array[Channel](false, 2, ((size: scala.Int) => new scala.Array[com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel](size)))
  this.capacity = capacity$p
  this.size = 0
  def addChannel[T <: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel](channelDescriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor): T = {
    return this.addChannel(channelDescriptor, null)
  }
  def addChannel[T <: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel](channelDescriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor, initializer: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelInitializer[T]): T = {
    var channel: T = this.getChannel(channelDescriptor)
    if (channel == null) {
      channel = this.allocateChannel(channelDescriptor).asInstanceOf[T]
      if (initializer != null) {
        initializer.init(channel)
      } else ()
      this.arrays.add(channel)
    } else ()
    return channel
  }
  private def allocateChannel[T <: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel](channelDescriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor): T = {
    if (channelDescriptor.`type` == classOf[scala.Float]) {
      return new FloatChannel(channelDescriptor.id, channelDescriptor.count, this.capacity).asInstanceOf[T]
    } else {
      if (channelDescriptor.`type` == classOf[scala.Int]) {
        return new IntChannel(channelDescriptor.id, channelDescriptor.count, this.capacity).asInstanceOf[T]
      } else {
        return new ObjectChannel[T](channelDescriptor.id, channelDescriptor.count, this.capacity, channelDescriptor.arraySupplier).asInstanceOf[T]
      }
    }
  }
  def removeArray[T](id: scala.Int): scala.Unit = {
    this.arrays.removeIndex(this.findIndex(id))
  }
  private def findIndex(id: scala.Int): scala.Int = {
    { var i: scala.Int = 0; while (i < this.arrays.size) { {
      val array: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel = this.arrays.items(i)
      if (array.id == id) {
        return i
      } else ()
    }; i = i + 1 } }
    return -1
  }
  def addElement(values: scala.Array[java.lang.Object]): scala.Unit = {
    if (this.size == this.capacity) {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Capacity reached, cannot add other elements")
    } else ()
    var k: scala.Int = 0
    for (strideArray <- this.arrays) {
      strideArray.add(k, values)
      k = k + strideArray.strideSize
    }
    this.size = this.size + 1
  }
  def removeElement(index: scala.Int): scala.Unit = {
    val last: scala.Int = this.size - 1
    for (strideArray <- this.arrays) {
      strideArray.swap(index, last)
    }
    this.size = last
  }
  def getChannel[T <: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel](descriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor): T = {
    for (array <- this.arrays) {
      if (array.id == descriptor.id) {
        return array.asInstanceOf[T]
      } else ()
    }
    return null.asInstanceOf[T]
  }
  def clear(): scala.Unit = {
    this.arrays.clear()
    this.size = 0
  }
  def setCapacity(requiredCapacity: scala.Int): scala.Unit = {
    if (this.capacity != requiredCapacity) {
      for (channel <- this.arrays) {
        channel.setCapacity(requiredCapacity)
      }
      this.capacity = requiredCapacity
    } else ()
  }
  abstract class Channel(id$p: scala.Int, data$p: java.lang.Object, strideSize$p: scala.Int) {
    var id: scala.Int = 0
    var data: java.lang.Object = null.asInstanceOf[java.lang.Object]
    var strideSize: scala.Int = 0
    this.id = id$p
    this.strideSize = strideSize$p
    this.data = data$p
    def add(index: scala.Int, objects: scala.Array[java.lang.Object]): scala.Unit
    def swap(i: scala.Int, k: scala.Int): scala.Unit
    def setCapacity(requiredCapacity: scala.Int): scala.Unit
  }
  class FloatChannel(id$p: scala.Int, strideSize$p: scala.Int, size: scala.Int) extends Channel(id$p, new scala.Array[scala.Float](size * strideSize$p), strideSize$p) {
    var data: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    this.data = data.asInstanceOf[scala.Array[scala.Float]].asInstanceOf[scala.Array[scala.Float]]
    def add(index: scala.Int, objects: scala.Array[java.lang.Object]): scala.Unit = {
      { var i: scala.Int = strideSize * size; val c: scala.Int = i + strideSize; var k: scala.Int = 0; while (i < c) { {
        this.data(i) = objects(k).asInstanceOf[java.lang.Float]
      }; i = i + 1; k = k + 1 } }
    }
    def swap(i$arg: scala.Int, k$arg: scala.Int): scala.Unit = {
      var i: scala.Int = i$arg
      var k: scala.Int = k$arg
      var t: scala.Float = 0.0f
      i = strideSize * i
      k = strideSize * k;
      { val c: scala.Int = i + strideSize; while (i < c) { {
        t = this.data(i)
        this.data(i) = this.data(k)
        this.data(k) = t
      }; i = i + 1; k = k + 1 } }
    }
    def setCapacity(requiredCapacity: scala.Int): scala.Unit = {
      val newData: scala.Array[scala.Float] = new scala.Array[scala.Float](strideSize * requiredCapacity)
      java.lang.System.arraycopy(this.data, 0, newData, 0, java.lang.Math.min(this.data.length, newData.length))
      data = {
        this.data = newData
        this.data
      }
    }
  }
  class IntChannel(id$p: scala.Int, strideSize$p: scala.Int, size: scala.Int) extends Channel(id$p, new scala.Array[scala.Int](size * strideSize$p), strideSize$p) {
    var data: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    this.data = data.asInstanceOf[scala.Array[scala.Int]].asInstanceOf[scala.Array[scala.Int]]
    def add(index: scala.Int, objects: scala.Array[java.lang.Object]): scala.Unit = {
      { var i: scala.Int = strideSize * size; val c: scala.Int = i + strideSize; var k: scala.Int = 0; while (i < c) { {
        this.data(i) = objects(k).asInstanceOf[java.lang.Integer]
      }; i = i + 1; k = k + 1 } }
    }
    def swap(i$arg: scala.Int, k$arg: scala.Int): scala.Unit = {
      var i: scala.Int = i$arg
      var k: scala.Int = k$arg
      var t: scala.Int = 0
      i = strideSize * i
      k = strideSize * k;
      { val c: scala.Int = i + strideSize; while (i < c) { {
        t = this.data(i)
        this.data(i) = this.data(k)
        this.data(k) = t
      }; i = i + 1; k = k + 1 } }
    }
    def setCapacity(requiredCapacity: scala.Int): scala.Unit = {
      val newData: scala.Array[scala.Int] = new scala.Array[scala.Int](strideSize * requiredCapacity)
      java.lang.System.arraycopy(this.data, 0, newData, 0, java.lang.Math.min(this.data.length, newData.length))
      data = {
        this.data = newData
        this.data
      }
    }
  }
  class ObjectChannel[T](id$p: scala.Int, strideSize$p: scala.Int, size: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) extends Channel(id$p, arraySupplier.get(size * strideSize$p), strideSize$p) {
    var data: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
    this.data = data.asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
    def add(index: scala.Int, objects: scala.Array[java.lang.Object]): scala.Unit = {
      { var i: scala.Int = strideSize * size; val c: scala.Int = i + strideSize; var k: scala.Int = 0; while (i < c) { {
        this.data(i) = objects(k).asInstanceOf[T].asInstanceOf[T]
      }; i = i + 1; k = k + 1 } }
    }
    def swap(i$arg: scala.Int, k$arg: scala.Int): scala.Unit = {
      var i: scala.Int = i$arg
      var k: scala.Int = k$arg
      var t: T = null.asInstanceOf[T]
      i = strideSize * i
      k = strideSize * k;
      { val c: scala.Int = i + strideSize; while (i < c) { {
        t = this.data(i)
        this.data(i) = this.data(k)
        this.data(k) = t
      }; i = i + 1; k = k + 1 } }
    }
    def setCapacity(requiredCapacity: scala.Int): scala.Unit = {
      data = {
        this.data = java.util.Arrays.copyOf(this.data.asInstanceOf[scala.Array[java.lang.Object]], strideSize * requiredCapacity)
        this.data
      }
    }
  }
}
object ParallelArray {
  class ChannelDescriptor(id$p: scala.Int, arraySupplier$p: com.badlogic.gdx.utils.ArraySupplier[?], count$p: scala.Int) {
    var id: scala.Int = 0
    var `type`: java.lang.Class[?] = null.asInstanceOf[java.lang.Class[?]]
    var arraySupplier: com.badlogic.gdx.utils.ArraySupplier[?] = null.asInstanceOf[com.badlogic.gdx.utils.ArraySupplier[?]]
    var count: scala.Int = 0
    this.id = id$p
    this.arraySupplier = arraySupplier$p
    this.count = count$p
    this.`type` = arraySupplier$p.get(0).getClass().getComponentType()
  }
  trait ChannelInitializer[T <: com.badlogic.gdx.graphics.g3d.particles.ParallelArray#Channel] {
    def init(channel: T): scala.Unit
  }
}