package com.badlogic.gdx.utils

class Bits {
  var bits: scala.Array[scala.Long] = scala.Array[scala.Long](0)
  def this(nbits: scala.Int) = {
    this()
    this.checkCapacity(nbits >>> 6)
  }
  def this(bitsToCpy: Bits) = {
    this()
    this.bits = new scala.Array[scala.Long](bitsToCpy.bits.length)
    java.lang.System.arraycopy(bitsToCpy.bits, 0, this.bits, 0, bitsToCpy.bits.length)
  }
  def get(index: scala.Int): scala.Boolean = {
    val word: scala.Int = index >>> 6
    if (word >= this.bits.length) {
      return false
    } else ()
    return (this.bits(word) & (1L << (index & 63))) != 0L
  }
  def getAndClear(index: scala.Int): scala.Boolean = {
    val word: scala.Int = index >>> 6
    if (word >= this.bits.length) {
      return false
    } else ()
    val oldBits: scala.Long = this.bits(word)
    this.bits(word) = this.bits(word) & (~(1L << (index & 63)))
    return this.bits(word) != oldBits
  }
  def getAndSet(index: scala.Int): scala.Boolean = {
    val word: scala.Int = index >>> 6
    this.checkCapacity(word)
    val oldBits: scala.Long = this.bits(word)
    this.bits(word) = this.bits(word) | (1L << (index & 63))
    return this.bits(word) == oldBits
  }
  def set(index: scala.Int): scala.Unit = {
    val word: scala.Int = index >>> 6
    this.checkCapacity(word)
    this.bits(word) = this.bits(word) | (1L << (index & 63))
  }
  def flip(index: scala.Int): scala.Unit = {
    val word: scala.Int = index >>> 6
    this.checkCapacity(word)
    this.bits(word) = this.bits(word) ^ (1L << (index & 63))
  }
  private def checkCapacity(len: scala.Int): scala.Unit = {
    if (len >= this.bits.length) {
      val newBits: scala.Array[scala.Long] = new scala.Array[scala.Long](len + 1)
      java.lang.System.arraycopy(this.bits, 0, newBits, 0, this.bits.length)
      this.bits = newBits
    } else ()
  }
  def clear(index: scala.Int): scala.Unit = {
    val word: scala.Int = index >>> 6
    if (word >= this.bits.length) {
      return
    } else ()
    this.bits(word) = this.bits(word) & (~(1L << (index & 63)))
  }
  def clear(): scala.Unit = {
    java.util.Arrays.fill(this.bits, 0)
  }
  def numBits(): scala.Int = {
    return this.bits.length << 6
  }
  def length(): scala.Int = {
    val bits: scala.Array[scala.Long] = this.bits;
    { var word: scala.Int = bits.length - 1; while (word >= 0) { {
      val bitsAtWord: scala.Long = bits(word)
      if (bitsAtWord != 0) {
        { var bit: scala.Int = 63; while (bit >= 0) { {
          if ((bitsAtWord & (1L << (bit & 63))) != 0L) {
            return ((word << 6) + bit) + 1
          } else ()
        }; bit = bit - 1 } }
      } else ()
    }; word = word - 1 } }
    return 0
  }
  def notEmpty(): scala.Boolean = {
    return !this.isEmpty()
  }
  def isEmpty(): scala.Boolean = {
    val bits: scala.Array[scala.Long] = this.bits
    val length: scala.Int = bits.length;
    { var i: scala.Int = 0; while (i < length) { {
      if (bits(i) != 0L) {
        return false
      } else ()
    }; i = i + 1 } }
    return true
  }
  def nextSetBit(fromIndex: scala.Int): scala.Int = {
    val bits: scala.Array[scala.Long] = this.bits
    var word: scala.Int = fromIndex >>> 6
    val bitsLength: scala.Int = bits.length
    if (word >= bitsLength) {
      return -1
    } else ()
    var bitsAtWord: scala.Long = bits(word)
    if (bitsAtWord != 0) {
      { var i: scala.Int = fromIndex & 63; while (i < 64) { {
        if ((bitsAtWord & (1L << (i & 63))) != 0L) {
          return (word << 6) + i
        } else ()
      }; i = i + 1 } }
    } else ();
    { word = word + 1; while (word < bitsLength) { {
      if (word != 0) {
        bitsAtWord = bits(word)
        if (bitsAtWord != 0) {
          { var i: scala.Int = 0; while (i < 64) { {
            if ((bitsAtWord & (1L << (i & 63))) != 0L) {
              return (word << 6) + i
            } else ()
          }; i = i + 1 } }
        } else ()
      } else ()
    }; word = word + 1 } }
    return -1
  }
  def nextClearBit(fromIndex: scala.Int): scala.Int = {
    val bits: scala.Array[scala.Long] = this.bits
    var word: scala.Int = fromIndex >>> 6
    val bitsLength: scala.Int = bits.length
    if (word >= bitsLength) {
      return bits.length << 6
    } else ()
    var bitsAtWord: scala.Long = bits(word);
    { var i: scala.Int = fromIndex & 63; while (i < 64) { {
      if ((bitsAtWord & (1L << (i & 63))) == 0L) {
        return (word << 6) + i
      } else ()
    }; i = i + 1 } };
    { word = word + 1; while (word < bitsLength) { {
      if (word == 0) {
        return word << 6
      } else ()
      bitsAtWord = bits(word);
      { var i: scala.Int = 0; while (i < 64) { {
        if ((bitsAtWord & (1L << (i & 63))) == 0L) {
          return (word << 6) + i
        } else ()
      }; i = i + 1 } }
    }; word = word + 1 } }
    return bits.length << 6
  }
  def and(other: Bits): scala.Unit = {
    val commonWords: scala.Int = java.lang.Math.min(this.bits.length, other.bits.length);
    { var i: scala.Int = 0; while (commonWords > i) { {
      this.bits(i) = this.bits(i) & other.bits(i)
    }; i = i + 1 } }
    if (this.bits.length > commonWords) {
      { var i: scala.Int = commonWords; val s: scala.Int = this.bits.length; while (s > i) { {
        this.bits(i) = 0L
      }; i = i + 1 } }
    } else ()
  }
  def andNot(other: Bits): scala.Unit = {
    { var i: scala.Int = 0; val j: scala.Int = this.bits.length; val k: scala.Int = other.bits.length; while ((i < j) && (i < k)) { {
      this.bits(i) = this.bits(i) & (~other.bits(i))
    }; i = i + 1 } }
  }
  def or(other: Bits): scala.Unit = {
    val commonWords: scala.Int = java.lang.Math.min(this.bits.length, other.bits.length);
    { var i: scala.Int = 0; while (commonWords > i) { {
      this.bits(i) = this.bits(i) | other.bits(i)
    }; i = i + 1 } }
    if (commonWords < other.bits.length) {
      this.checkCapacity(other.bits.length);
      { var i: scala.Int = commonWords; val s: scala.Int = other.bits.length; while (s > i) { {
        this.bits(i) = other.bits(i)
      }; i = i + 1 } }
    } else ()
  }
  def xor(other: Bits): scala.Unit = {
    val commonWords: scala.Int = java.lang.Math.min(this.bits.length, other.bits.length);
    { var i: scala.Int = 0; while (commonWords > i) { {
      this.bits(i) = this.bits(i) ^ other.bits(i)
    }; i = i + 1 } }
    if (commonWords < other.bits.length) {
      this.checkCapacity(other.bits.length);
      { var i: scala.Int = commonWords; val s: scala.Int = other.bits.length; while (s > i) { {
        this.bits(i) = other.bits(i)
      }; i = i + 1 } }
    } else ()
  }
  def intersects(other: Bits): scala.Boolean = {
    val bits: scala.Array[scala.Long] = this.bits
    val otherBits: scala.Array[scala.Long] = other.bits;
    { var i: scala.Int = java.lang.Math.min(bits.length, otherBits.length) - 1; while (i >= 0) { {
      if ((bits(i) & otherBits(i)) != 0) {
        return true
      } else ()
    }; i = i - 1 } }
    return false
  }
  def containsAll(other: Bits): scala.Boolean = {
    val bits: scala.Array[scala.Long] = this.bits
    val otherBits: scala.Array[scala.Long] = other.bits
    val otherBitsLength: scala.Int = otherBits.length
    val bitsLength: scala.Int = bits.length;
    { var i: scala.Int = bitsLength; while (i < otherBitsLength) { {
      if (otherBits(i) != 0) {
        return false
      } else ()
    }; i = i + 1 } };
    { var i: scala.Int = java.lang.Math.min(bitsLength, otherBitsLength) - 1; while (i >= 0) { {
      if ((bits(i) & otherBits(i)) != otherBits(i)) {
        return false
      } else ()
    }; i = i - 1 } }
    return true
  }
  @java.lang.Override
  def hashCode(): scala.Int = {
    val word: scala.Int = this.length() >>> 6
    var hash: scala.Int = 0;
    { var i: scala.Int = 0; while (word >= i) { {
      hash = (127 * hash) + (this.bits(i) ^ (this.bits(i) >>> 32)).asInstanceOf[scala.Int]
    }; i = i + 1 } }
    return hash
  }
  @java.lang.Override
  def equals(obj: java.lang.Object): scala.Boolean = {
    if (this == obj) {
      return true
    } else ()
    if (obj == null) {
      return false
    } else ()
    if (this.getClass() != obj.getClass()) {
      return false
    } else ()
    val other: Bits = obj.asInstanceOf[Bits].asInstanceOf[Bits]
    val otherBits: scala.Array[scala.Long] = other.bits
    val commonWords: scala.Int = java.lang.Math.min(this.bits.length, otherBits.length);
    { var i: scala.Int = 0; while (commonWords > i) { {
      if (this.bits(i) != otherBits(i)) {
        return false
      } else ()
    }; i = i + 1 } }
    if (this.bits.length == otherBits.length) {
      return true
    } else ()
    return this.length() == other.length()
  }
}