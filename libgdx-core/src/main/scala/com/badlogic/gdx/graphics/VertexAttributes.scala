package com.badlogic.gdx.graphics

final class VertexAttributes extends scala.collection.Iterable[com.badlogic.gdx.graphics.VertexAttribute] with java.lang.Comparable[VertexAttributes] {
  private var attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = null.asInstanceOf[scala.Array[com.badlogic.gdx.graphics.VertexAttribute]]
  var vertexSize: scala.Int = 0
  private var mask: scala.Long = -1
  private var boneWeightUnits: scala.Int = -1
  private var textureCoordinates: scala.Int = -1
  private var iterable: com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterable[com.badlogic.gdx.graphics.VertexAttribute] = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterable[com.badlogic.gdx.graphics.VertexAttribute]]
  def this(attributes: scala.Array[com.badlogic.gdx.graphics.VertexAttribute]) = {
    this()
    if (attributes.length == 0) {
      throw new java.lang.IllegalArgumentException("attributes must be >= 1")
    } else ()
    val list: scala.Array[com.badlogic.gdx.graphics.VertexAttribute] = new scala.Array[com.badlogic.gdx.graphics.VertexAttribute](attributes.length);
    { var i: scala.Int = 0; while (i < attributes.length) { {
      list(i) = attributes(i)
    }; i = i + 1 } }
    this.attributes = list
    this.vertexSize = this.calculateOffsets()
  }
  def getOffset(usage: scala.Int, defaultIfNotFound: scala.Int): scala.Int = {
    val vertexAttribute: com.badlogic.gdx.graphics.VertexAttribute = this.findByUsage(usage)
    if (vertexAttribute == null) {
      return defaultIfNotFound
    } else ()
    return vertexAttribute.offset / 4
  }
  def getOffset(usage: scala.Int): scala.Int = {
    return this.getOffset(usage, 0)
  }
  def findByUsage(usage: scala.Int): com.badlogic.gdx.graphics.VertexAttribute = {
    val len: scala.Int = this.size();
    { var i: scala.Int = 0; while (i < len) { {
      if (this.get(i).usage == usage) {
        return this.get(i)
      } else ()
    }; i = i + 1 } }
    return null
  }
  private def calculateOffsets(): scala.Int = {
    var count: scala.Int = 0;
    { var i: scala.Int = 0; while (i < this.attributes.length) { {
      val attribute: com.badlogic.gdx.graphics.VertexAttribute = this.attributes(i)
      attribute.offset = count
      count = count + attribute.getSizeInBytes()
    }; i = i + 1 } }
    return count
  }
  def size(): scala.Int = {
    return this.attributes.length
  }
  def get(index: scala.Int): com.badlogic.gdx.graphics.VertexAttribute = {
    return this.attributes(index)
  }
  def toString(): java.lang.String = {
    val builder: java.lang.StringBuilder = new java.lang.StringBuilder()
    builder.append("[");
    { var i: scala.Int = 0; while (i < this.attributes.length) { {
      builder.append("(")
      builder.append(this.attributes(i).alias)
      builder.append(", ")
      builder.append(this.attributes(i).usage)
      builder.append(", ")
      builder.append(this.attributes(i).numComponents)
      builder.append(", ")
      builder.append(this.attributes(i).offset)
      builder.append(")")
      builder.append("\n")
    }; i = i + 1 } }
    builder.append("]")
    return builder.toString()
  }
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (obj == this) {
      return true
    } else ()
    if (!obj.isInstanceOf[VertexAttributes]) {
      return false
    } else ()
    val other: VertexAttributes = obj.asInstanceOf[VertexAttributes].asInstanceOf[VertexAttributes]
    if (this.attributes.length != other.attributes.length) {
      return false
    } else ();
    { var i: scala.Int = 0; while (i < this.attributes.length) { {
      if (!this.attributes(i).equals(other.attributes(i))) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def hashCode(): scala.Int = {
    var result: scala.Long = 61 * this.attributes.length;
    { var i: scala.Int = 0; while (i < this.attributes.length) { {
      result = (result * 61) + this.attributes(i).hashCode()
    }; i = i + 1 } }
    return (result ^ (result >> 32)).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
  }
  def getMask(): scala.Long = {
    if (this.mask == (-1)) {
      var result: scala.Long = 0;
      { var i: scala.Int = 0; while (i < this.attributes.length) { {
        result = result | this.attributes(i).usage
      }; i = i + 1 } }
      this.mask = result
    } else ()
    return this.mask
  }
  def getMaskWithSizePacked(): scala.Long = {
    return this.getMask() | (this.attributes.length.asInstanceOf[scala.Long] << 32)
  }
  def getBoneWeights(): scala.Int = {
    if (this.boneWeightUnits < 0) {
      this.boneWeightUnits = 0;
      { var i: scala.Int = 0; while (i < this.attributes.length) { {
        val a: com.badlogic.gdx.graphics.VertexAttribute = this.attributes(i)
        if (a.usage == com.badlogic.gdx.graphics.VertexAttributes.Usage.BoneWeight) {
          this.boneWeightUnits = java.lang.Math.max(this.boneWeightUnits, a.unit + 1)
        } else ()
      }; i = i + 1 } }
    } else ()
    return this.boneWeightUnits
  }
  def getTextureCoordinates(): scala.Int = {
    if (this.textureCoordinates < 0) {
      this.textureCoordinates = 0;
      { var i: scala.Int = 0; while (i < this.attributes.length) { {
        val a: com.badlogic.gdx.graphics.VertexAttribute = this.attributes(i)
        if (a.usage == com.badlogic.gdx.graphics.VertexAttributes.Usage.TextureCoordinates) {
          this.textureCoordinates = java.lang.Math.max(this.textureCoordinates, a.unit + 1)
        } else ()
      }; i = i + 1 } }
    } else ()
    return this.textureCoordinates
  }
  def compareTo(o: VertexAttributes): scala.Int = {
    if (this.attributes.length != o.attributes.length) {
      return this.attributes.length - o.attributes.length
    } else ()
    val m1: scala.Long = this.getMask()
    val m2: scala.Long = o.getMask()
    if (m1 != m2) {
      return if (m1 < m2) -1 else 1
    } else ();
    { var i: scala.Int = this.attributes.length - 1; while (i >= 0) { {
      val va0: com.badlogic.gdx.graphics.VertexAttribute = this.attributes(i)
      val va1: com.badlogic.gdx.graphics.VertexAttribute = o.attributes(i)
      if (va0.usage != va1.usage) {
        return va0.usage - va1.usage
      } else ()
      if (va0.unit != va1.unit) {
        return va0.unit - va1.unit
      } else ()
      if (va0.numComponents != va1.numComponents) {
        return va0.numComponents - va1.numComponents
      } else ()
      if (va0.normalized != va1.normalized) {
        return if (va0.normalized) 1 else -1
      } else ()
      if (va0.`type` != va1.`type`) {
        return va0.`type` - va1.`type`
      } else ()
    }; i = i - 1 } }
    return 0
  }
  def iterator(): scala.collection.Iterator[com.badlogic.gdx.graphics.VertexAttribute] = {
    if (this.iterable == null) {
      this.iterable = new com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterable[com.badlogic.gdx.graphics.VertexAttribute](this.attributes)
    } else ()
    return this.iterable.iterator()
  }
}
object VertexAttributes {
  object Usage {
    final val Position: scala.Int = 1
    final val ColorUnpacked: scala.Int = 2
    final val ColorPacked: scala.Int = 4
    final val Normal: scala.Int = 8
    final val TextureCoordinates: scala.Int = 16
    final val Generic: scala.Int = 32
    final val BoneWeight: scala.Int = 64
    final val Tangent: scala.Int = 128
    final val BiNormal: scala.Int = 256
  }
  class ReadonlyIterator[T] extends scala.collection.Iterator[T] with scala.collection.Iterable[T] {
    private var array: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
    var index: scala.Int = 0
    var valid: scala.Boolean = true
    def this(array: scala.Array[T]) = {
      this()
      this.array = array
    }
    def hasNext(): scala.Boolean = {
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.index < this.array.length
    }
    def next(): T = {
      if (this.index >= this.array.length) {
        throw new java.util.NoSuchElementException(java.lang.String.valueOf(this.index))
      } else ()
      if (!this.valid) {
        throw new com.badlogic.gdx.utils.GdxRuntimeException("#iterator() cannot be used nested.")
      } else ()
      return this.array({ this.index += 1; this.index })
    }
    def remove(): scala.Unit = {
      throw new com.badlogic.gdx.utils.GdxRuntimeException("Remove not allowed.")
    }
    def reset(): scala.Unit = {
      this.index = 0
    }
    def iterator(): scala.collection.Iterator[T] = {
      return this
    }
  }
  class ReadonlyIterable[T] extends scala.collection.Iterable[T] {
    private var array: scala.Array[T] = null.asInstanceOf[scala.Array[T]]
    private var iterator1: com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T] = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T]]
    private var iterator2: com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T] = null.asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T]]
    def this(array: scala.Array[T]) = {
      this()
      this.array = array
    }
    def iterator(): scala.collection.Iterator[T] = {
      if (com.badlogic.gdx.utils.Collections.allocateIterators) {
        return new com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T](this.array).asInstanceOf[scala.collection.Iterator[T]]
      } else ()
      if (this.iterator1 == null) {
        this.iterator1 = new com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T](this.array).asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T]]
        this.iterator2 = new com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T](this.array).asInstanceOf[com.badlogic.gdx.graphics.VertexAttributes.ReadonlyIterator[T]]
      } else ()
      if (!this.iterator1.valid) {
        this.iterator1.index = 0
        this.iterator1.valid = true
        this.iterator2.valid = false
        return this.iterator1.asInstanceOf[scala.collection.Iterator[T]]
      } else ()
      this.iterator2.index = 0
      this.iterator2.valid = true
      this.iterator1.valid = false
      return this.iterator2.asInstanceOf[scala.collection.Iterator[T]]
    }
  }
}