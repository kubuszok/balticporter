package com.badlogic.gdx.graphics.g3d.particles

class ParallelArray {
  var arrays: com.badlogic.gdx.utils.Array[Channel] = null.asInstanceOf[com.badlogic.gdx.utils.Array[Channel]]
  var capacity: scala.Int = 0
  var size: scala.Int = 0
  def this(capacity: scala.Int) = {
    this()
    this.arrays = new com.badlogic.gdx.utils.Array[Channel](false, 2, (() => new scala.Array[Channel]()))
    this.capacity = capacity
    this.size = 0
  }
  def addChannel[T <: Channel](channelDescriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor): T = {
    return this.addChannel(channelDescriptor, null)
  }
  def addChannel[T <: Channel](channelDescriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor, initializer: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelInitializer[T]): T = {
    var channel: T = this.getChannel(channelDescriptor)
    if (channel == null) {
      channel = this.allocateChannel(channelDescriptor)
      if (initializer != null) {
        initializer.init(channel)
      } else ()
      this.arrays.add(channel)
    } else ()
    return channel
  }
  private def allocateChannel[T <: Channel](channelDescriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor): T = {
    if (channelDescriptor.`type` == classOf[scala.Float]) {
      return new FloatChannel(channelDescriptor.id, channelDescriptor.count, this.capacity).asInstanceOf[T]
    } else {
      if (channelDescriptor.`type` == classOf[scala.Int]) {
        return new IntChannel(channelDescriptor.id, channelDescriptor.count, this.capacity).asInstanceOf[T]
      } else {
        return new ObjectChannel(channelDescriptor.id, channelDescriptor.count, this.capacity, channelDescriptor.arraySupplier).asInstanceOf[T]
      }
    }
  }
  def removeArray[T](id: scala.Int): scala.Unit = {
    this.arrays.removeIndex(this.findIndex(id))
  }
  private def findIndex(id: scala.Int): scala.Int = {
    { var i: scala.Int = 0; while (i < this.arrays.size) { {
      val array: Channel = this.arrays.items(i)
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
  def getChannel[T <: Channel](descriptor: com.badlogic.gdx.graphics.g3d.particles.ParallelArray.ChannelDescriptor): T = {
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
  abstract class Channel {
    var id: scala.Int = 0
    var data: java.lang.Object = null.asInstanceOf[java.lang.Object]
    var strideSize: scala.Int = 0
    def this(id: scala.Int, data: java.lang.Object, strideSize: scala.Int) = {
      this()
      this.id = id
      this.strideSize = strideSize
      this.data = data
    }
    def add(index: scala.Int, objects: scala.Array[java.lang.Object]): scala.Unit
    def swap(i: scala.Int, k: scala.Int): scala.Unit
    def setCapacity(requiredCapacity: scala.Int): scala.Unit
  }
  class FloatChannel extends Channel {
    var data: scala.Array[scala.Float] = null.asInstanceOf[scala.Array[scala.Float]]
    def this(id: scala.Int, strideSize: scala.Int, size: scala.Int) = {
      this()
      this.data = data.asInstanceOf[scala.Array[scala.Float]].asInstanceOf[scala.Array[scala.Float]]
    }
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
  class IntChannel extends Channel {
    var data: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
    def this(id: scala.Int, strideSize: scala.Int, size: scala.Int) = {
      this()
      this.data = data.asInstanceOf[scala.Array[scala.Int]].asInstanceOf[scala.Array[scala.Int]]
    }
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
  class ObjectChannel[T] extends Channel {
    var data: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
    def this(id: scala.Int, strideSize: scala.Int, size: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[scala.Array[T]]) = {
      this()
      this.data = data.asInstanceOf[scala.Array[T]].asInstanceOf[scala.Array[T]]
    }
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
  class ChannelDescriptor {
    var id: scala.Int = 0
    var `type`: java.lang.Class[?] = null.asInstanceOf[java.lang.Class[?]]
    var arraySupplier: com.badlogic.gdx.utils.ArraySupplier[?] = null.asInstanceOf[com.badlogic.gdx.utils.ArraySupplier[?]]
    var count: scala.Int = 0
    def this(id: scala.Int, `type`: java.lang.Class[?], count: scala.Int) = {
      this()
      this.id = id
      this.`type` = `type`
      this.count = count
      this.arraySupplier = (size: scala.Int) => com.badlogic.gdx.utils.reflect.ArrayReflection.newInstance(`type`, size)
    }
    def this(id: scala.Int, arraySupplier: com.badlogic.gdx.utils.ArraySupplier[?], count: scala.Int) = {
      this()
      this.id = id
      this.arraySupplier = arraySupplier
      this.count = count
      this.`type` = arraySupplier.get(0).getClass().getComponentType()
    }
  }
  trait ChannelInitializer[T <: Channel] {
    def init(channel: T): scala.Unit
  }
}