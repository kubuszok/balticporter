package com.badlogic.gdx.utils.compression.lz

class BinTree extends com.badlogic.gdx.utils.compression.lz.InWindow {
  var _cyclicBufferPos: scala.Int = 0
  var _cyclicBufferSize: scala.Int = 0
  var _matchMaxLen: scala.Int = 0
  var _son: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  var _hash: scala.Array[scala.Int] = null.asInstanceOf[scala.Array[scala.Int]]
  var _cutValue: scala.Int = 255
  var _hashMask: scala.Int = 0
  var _hashSizeSum: scala.Int = 0
  var HASH_ARRAY: scala.Boolean = true
  var kNumHashDirectBytes: scala.Int = 0
  var kMinMatchCheck: scala.Int = 4
  var kFixHashSize: scala.Int = BinTree.kHash2Size + BinTree.kHash3Size
  def SetType(numHashBytes: scala.Int): scala.Unit = {
    this.HASH_ARRAY = numHashBytes > 2
    if (this.HASH_ARRAY) {
      this.kNumHashDirectBytes = 0
      this.kMinMatchCheck = 4
      this.kFixHashSize = BinTree.kHash2Size + BinTree.kHash3Size
    } else {
      this.kNumHashDirectBytes = 2
      this.kMinMatchCheck = 2 + 1
      this.kFixHashSize = 0
    }
  }
  def Init(): scala.Unit = {
    super.Init();
    { var i: scala.Int = 0; while (i < this._hashSizeSum) { {
      this._hash(i) = BinTree.kEmptyHashValue
    }; i = i + 1 } }
    this._cyclicBufferPos = 0
    this.ReduceOffsets(-1)
  }
  def MovePos(): scala.Unit = {
    if ({ this._cyclicBufferPos += 1; this._cyclicBufferPos } >= this._cyclicBufferSize) {
      this._cyclicBufferPos = 0
    } else ()
    super.MovePos()
    if (_pos == BinTree.kMaxValForNormalize) {
      this.Normalize()
    } else ()
  }
  def Create(historySize: scala.Int, keepAddBufferBefore: scala.Int, matchMaxLen: scala.Int, keepAddBufferAfter: scala.Int): scala.Boolean = {
    if (historySize > (BinTree.kMaxValForNormalize - 256)) {
      return false
    } else ()
    this._cutValue = 16 + (matchMaxLen >> 1)
    val windowReservSize: scala.Int = ((((historySize + keepAddBufferBefore) + matchMaxLen) + keepAddBufferAfter) / 2) + 256
    super.Create(historySize + keepAddBufferBefore, matchMaxLen + keepAddBufferAfter, windowReservSize)
    this._matchMaxLen = matchMaxLen
    val cyclicBufferSize: scala.Int = historySize + 1
    if (this._cyclicBufferSize != cyclicBufferSize) {
      this._son = new Array[scala.Int]({
        this._cyclicBufferSize = cyclicBufferSize
        this._cyclicBufferSize
      } * 2)
    } else ()
    var hs: scala.Int = BinTree.kBT2HashSize
    if (this.HASH_ARRAY) {
      hs = historySize - 1
      hs = hs | (hs >> 1)
      hs = hs | (hs >> 2)
      hs = hs | (hs >> 4)
      hs = hs | (hs >> 8)
      hs = hs >> 1
      hs = hs | 65535
      if (hs > (1 << 24)) {
        hs = hs >> 1
      } else ()
      this._hashMask = hs
      hs = hs + 1
      hs = hs + this.kFixHashSize
    } else ()
    if (hs != this._hashSizeSum) {
      this._hash = new Array[scala.Int]({
        this._hashSizeSum = hs
        this._hashSizeSum
      })
    } else ()
    return true
  }
  def GetMatches(distances: scala.Array[scala.Int]): scala.Int = {
    var lenLimit: scala.Int = 0
    if ((_pos + this._matchMaxLen) <= _streamPos) {
      lenLimit = this._matchMaxLen
    } else {
      lenLimit = _streamPos - _pos
      if (lenLimit < this.kMinMatchCheck) {
        this.MovePos()
        return 0
      } else ()
    }
    var offset: scala.Int = 0
    val matchMinPos: scala.Int = if (_pos > this._cyclicBufferSize) _pos - this._cyclicBufferSize else 0
    val cur: scala.Int = _bufferOffset + _pos
    var maxLen: scala.Int = BinTree.kStartMaxLen
    var hashValue: scala.Int = 0
    var hash2Value: scala.Int = 0
    var hash3Value: scala.Int = 0
    if (this.HASH_ARRAY) {
      var temp: scala.Int = BinTree.CrcTable(_bufferBase(cur) & 255) ^ (_bufferBase(cur + 1) & 255)
      hash2Value = temp & (BinTree.kHash2Size - 1)
      temp = temp ^ ((_bufferBase(cur + 2) & 255).asInstanceOf[scala.Int] << 8)
      hash3Value = temp & (BinTree.kHash3Size - 1)
      hashValue = (temp ^ (BinTree.CrcTable(_bufferBase(cur + 3) & 255) << 5)) & this._hashMask
    } else {
      hashValue = (_bufferBase(cur) & 255) ^ ((_bufferBase(cur + 1) & 255).asInstanceOf[scala.Int] << 8)
    }
    var curMatch: scala.Int = this._hash(this.kFixHashSize + hashValue)
    if (this.HASH_ARRAY) {
      var curMatch2: scala.Int = this._hash(hash2Value)
      val curMatch3: scala.Int = this._hash(BinTree.kHash3Offset + hash3Value)
      this._hash(hash2Value) = _pos
      this._hash(BinTree.kHash3Offset + hash3Value) = _pos
      if (curMatch2 > matchMinPos) {
        if (_bufferBase(_bufferOffset + curMatch2) == _bufferBase(cur)) {
          distances({ offset += 1; offset }) = {
            maxLen = 2
            maxLen
          }
          distances({ offset += 1; offset }) = (_pos - curMatch2) - 1
        } else ()
      } else ()
      if (curMatch3 > matchMinPos) {
        if (_bufferBase(_bufferOffset + curMatch3) == _bufferBase(cur)) {
          if (curMatch3 == curMatch2) {
            offset = offset - 2
          } else ()
          distances({ offset += 1; offset }) = {
            maxLen = 3
            maxLen
          }
          distances({ offset += 1; offset }) = (_pos - curMatch3) - 1
          curMatch2 = curMatch3
        } else ()
      } else ()
      if ((offset != 0) && (curMatch2 == curMatch)) {
        offset = offset - 2
        maxLen = BinTree.kStartMaxLen
      } else ()
    } else ()
    this._hash(this.kFixHashSize + hashValue) = _pos
    var ptr0: scala.Int = (this._cyclicBufferPos << 1) + 1
    var ptr1: scala.Int = this._cyclicBufferPos << 1
    var len0: scala.Int = 0
    var len1: scala.Int = 0
    len0 = {
      len1 = this.kNumHashDirectBytes
      len1
    }
    if (this.kNumHashDirectBytes != 0) {
      if (curMatch > matchMinPos) {
        if (_bufferBase((_bufferOffset + curMatch) + this.kNumHashDirectBytes) != _bufferBase(cur + this.kNumHashDirectBytes)) {
          distances({ offset += 1; offset }) = {
            maxLen = this.kNumHashDirectBytes
            maxLen
          }
          distances({ offset += 1; offset }) = (_pos - curMatch) - 1
        } else ()
      } else ()
    } else ()
    var count: scala.Int = this._cutValue
    while (true) {
      if ((curMatch <= matchMinPos) || ({ count -= 1; count } == 0)) {
        this._son(ptr0) = {
          this._son(ptr1) = BinTree.kEmptyHashValue
          this._son(ptr1)
        }
        /* break */ ()
      } else ()
      val delta: scala.Int = _pos - curMatch
      val cyclicPos: scala.Int = (if (delta <= this._cyclicBufferPos) this._cyclicBufferPos - delta else (this._cyclicBufferPos - delta) + this._cyclicBufferSize) << 1
      val pby1: scala.Int = _bufferOffset + curMatch
      var len: scala.Int = java.lang.Math.min(len0, len1)
      if (_bufferBase(pby1 + len) == _bufferBase(cur + len)) {
        while ({ len += 1; len } != lenLimit) {
          if (_bufferBase(pby1 + len) != _bufferBase(cur + len)) {
            /* break */ ()
          } else ()
        }
        if (maxLen < len) {
          distances({ offset += 1; offset }) = {
            maxLen = len
            maxLen
          }
          distances({ offset += 1; offset }) = delta - 1
          if (len == lenLimit) {
            this._son(ptr1) = this._son(cyclicPos)
            this._son(ptr0) = this._son(cyclicPos + 1)
            /* break */ ()
          } else ()
        } else ()
      } else ()
      if ((_bufferBase(pby1 + len) & 255) < (_bufferBase(cur + len) & 255)) {
        this._son(ptr1) = curMatch
        ptr1 = cyclicPos + 1
        curMatch = this._son(ptr1)
        len1 = len
      } else {
        this._son(ptr0) = curMatch
        ptr0 = cyclicPos
        curMatch = this._son(ptr0)
        len0 = len
      }
    }
    this.MovePos()
    return offset
  }
  def Skip(num: scala.Int): scala.Unit = {
    while ({ {
      var lenLimit: scala.Int = 0
      if ((_pos + this._matchMaxLen) <= _streamPos) {
        lenLimit = this._matchMaxLen
      } else {
        lenLimit = _streamPos - _pos
        if (lenLimit < this.kMinMatchCheck) {
          this.MovePos()
          /* continue */ ()
        } else ()
      }
      val matchMinPos: scala.Int = if (_pos > this._cyclicBufferSize) _pos - this._cyclicBufferSize else 0
      val cur: scala.Int = _bufferOffset + _pos
      var hashValue: scala.Int = 0
      if (this.HASH_ARRAY) {
        var temp: scala.Int = BinTree.CrcTable(_bufferBase(cur) & 255) ^ (_bufferBase(cur + 1) & 255)
        val hash2Value: scala.Int = temp & (BinTree.kHash2Size - 1)
        this._hash(hash2Value) = _pos
        temp = temp ^ ((_bufferBase(cur + 2) & 255).asInstanceOf[scala.Int] << 8)
        val hash3Value: scala.Int = temp & (BinTree.kHash3Size - 1)
        this._hash(BinTree.kHash3Offset + hash3Value) = _pos
        hashValue = (temp ^ (BinTree.CrcTable(_bufferBase(cur + 3) & 255) << 5)) & this._hashMask
      } else {
        hashValue = (_bufferBase(cur) & 255) ^ ((_bufferBase(cur + 1) & 255).asInstanceOf[scala.Int] << 8)
      }
      var curMatch: scala.Int = this._hash(this.kFixHashSize + hashValue)
      this._hash(this.kFixHashSize + hashValue) = _pos
      var ptr0: scala.Int = (this._cyclicBufferPos << 1) + 1
      var ptr1: scala.Int = this._cyclicBufferPos << 1
      var len0: scala.Int = 0
      var len1: scala.Int = 0
      len0 = {
        len1 = this.kNumHashDirectBytes
        len1
      }
      var count: scala.Int = this._cutValue
      while (true) {
        if ((curMatch <= matchMinPos) || ({ count -= 1; count } == 0)) {
          this._son(ptr0) = {
            this._son(ptr1) = BinTree.kEmptyHashValue
            this._son(ptr1)
          }
          /* break */ ()
        } else ()
        val delta: scala.Int = _pos - curMatch
        val cyclicPos: scala.Int = (if (delta <= this._cyclicBufferPos) this._cyclicBufferPos - delta else (this._cyclicBufferPos - delta) + this._cyclicBufferSize) << 1
        val pby1: scala.Int = _bufferOffset + curMatch
        var len: scala.Int = java.lang.Math.min(len0, len1)
        if (_bufferBase(pby1 + len) == _bufferBase(cur + len)) {
          while ({ len += 1; len } != lenLimit) {
            if (_bufferBase(pby1 + len) != _bufferBase(cur + len)) {
              /* break */ ()
            } else ()
          }
          if (len == lenLimit) {
            this._son(ptr1) = this._son(cyclicPos)
            this._son(ptr0) = this._son(cyclicPos + 1)
            /* break */ ()
          } else ()
        } else ()
        if ((_bufferBase(pby1 + len) & 255) < (_bufferBase(cur + len) & 255)) {
          this._son(ptr1) = curMatch
          ptr1 = cyclicPos + 1
          curMatch = this._son(ptr1)
          len1 = len
        } else {
          this._son(ptr0) = curMatch
          ptr0 = cyclicPos
          curMatch = this._son(ptr0)
          len0 = len
        }
      }
      this.MovePos()
    }; { num -= 1; num } != 0 }) ()
  }
  def NormalizeLinks(items: scala.Array[scala.Int], numItems: scala.Int, subValue: scala.Int): scala.Unit = {
    { var i: scala.Int = 0; while (i < numItems) { {
      var value: scala.Int = items(i)
      if (value <= subValue) {
        value = BinTree.kEmptyHashValue
      } else {
        value = value - subValue
      }
      items(i) = value
    }; i = i + 1 } }
  }
  def Normalize(): scala.Unit = {
    val subValue: scala.Int = _pos - this._cyclicBufferSize
    this.NormalizeLinks(this._son, this._cyclicBufferSize * 2, subValue)
    this.NormalizeLinks(this._hash, this._hashSizeSum, subValue)
    this.ReduceOffsets(subValue)
  }
  def SetCutValue(cutValue: scala.Int): scala.Unit = {
    this._cutValue = cutValue
  }
}
object BinTree {
  final val kHash2Size: scala.Int = 1 << 10
  final val kHash3Size: scala.Int = 1 << 16
  final val kBT2HashSize: scala.Int = 1 << 16
  final val kStartMaxLen: scala.Int = 1
  final val kHash3Offset: scala.Int = BinTree.kHash2Size
  final val kEmptyHashValue: scala.Int = 0
  final val kMaxValForNormalize: scala.Int = (1 << 30) - 1
  private final val CrcTable: scala.Array[scala.Int] = new Array[scala.Int](256)
}