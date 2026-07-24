package com.badlogic.gdx.utils.compression.lz

class InWindow {
  var _bufferBase: scala.Array[scala.Byte] = null.asInstanceOf[scala.Array[scala.Byte]]
  var _stream: java.io.InputStream = null.asInstanceOf[java.io.InputStream]
  var _posLimit: scala.Int = 0
  var _streamEndWasReached: scala.Boolean = false
  var _pointerToLastSafePosition: scala.Int = 0
  var _bufferOffset: scala.Int = 0
  var _blockSize: scala.Int = 0
  var _pos: scala.Int = 0
  var _keepSizeBefore: scala.Int = 0
  var _keepSizeAfter: scala.Int = 0
  var _streamPos: scala.Int = 0
  def MoveBlock(): scala.Unit = {
    var offset: scala.Int = (this._bufferOffset + this._pos) - this._keepSizeBefore
    if (offset > 0) {
      offset = offset - 1
    } else ()
    val numBytes: scala.Int = (this._bufferOffset + this._streamPos) - offset
    { var i: scala.Int = 0; while (i < numBytes) { {
      this._bufferBase(i) = this._bufferBase(offset + i)
    }; i = i + 1 } }
    this._bufferOffset = this._bufferOffset - offset
  }
  def ReadBlock(): scala.Unit = {
    if (this._streamEndWasReached) {
      return
    } else ()
    while (true) {
      val size: scala.Int = ((0 - this._bufferOffset) + this._blockSize) - this._streamPos
      if (size == 0) {
        return
      } else ()
      val numReadBytes: scala.Int = this._stream.read(this._bufferBase, this._bufferOffset + this._streamPos, size)
      if (numReadBytes == (-1)) {
        this._posLimit = this._streamPos
        val pointerToPostion: scala.Int = this._bufferOffset + this._posLimit
        if (pointerToPostion > this._pointerToLastSafePosition) {
          this._posLimit = this._pointerToLastSafePosition - this._bufferOffset
        } else ()
        this._streamEndWasReached = true
        return
      } else ()
      this._streamPos = this._streamPos + numReadBytes
      if (this._streamPos >= (this._pos + this._keepSizeAfter)) {
        this._posLimit = this._streamPos - this._keepSizeAfter
      } else ()
    }
  }
  def Free(): scala.Unit = {
    this._bufferBase = null
  }
  def Create(keepSizeBefore: scala.Int, keepSizeAfter: scala.Int, keepSizeReserv: scala.Int): scala.Unit = {
    this._keepSizeBefore = keepSizeBefore
    this._keepSizeAfter = keepSizeAfter
    val blockSize: scala.Int = (keepSizeBefore + keepSizeAfter) + keepSizeReserv
    if ((this._bufferBase == null) || (this._blockSize != blockSize)) {
      this.Free()
      this._blockSize = blockSize
      this._bufferBase = new Array[scala.Byte](this._blockSize)
    } else ()
    this._pointerToLastSafePosition = this._blockSize - keepSizeAfter
  }
  def SetStream(stream: java.io.InputStream): scala.Unit = {
    this._stream = stream
  }
  def ReleaseStream(): scala.Unit = {
    this._stream = null
  }
  def Init(): scala.Unit = {
    this._bufferOffset = 0
    this._pos = 0
    this._streamPos = 0
    this._streamEndWasReached = false
    this.ReadBlock()
  }
  def MovePos(): scala.Unit = {
    this._pos = this._pos + 1
    if (this._pos > this._posLimit) {
      val pointerToPostion: scala.Int = this._bufferOffset + this._pos
      if (pointerToPostion > this._pointerToLastSafePosition) {
        this.MoveBlock()
      } else ()
      this.ReadBlock()
    } else ()
  }
  def GetIndexByte(index: scala.Int): scala.Byte = {
    return this._bufferBase((this._bufferOffset + this._pos) + index)
  }
  def GetMatchLen(index: scala.Int, distance$arg: scala.Int, limit$arg: scala.Int): scala.Int = {
    var distance: scala.Int = distance$arg
    var limit: scala.Int = limit$arg
    if (this._streamEndWasReached) {
      if (((this._pos + index) + limit) > this._streamPos) {
        limit = this._streamPos - (this._pos + index)
      } else ()
    } else ()
    distance = distance + 1
    val pby: scala.Int = (this._bufferOffset + this._pos) + index
    var i: scala.Int = 0
    { i = 0; while ((i < limit) && (this._bufferBase(pby + i) == this._bufferBase((pby + i) - distance))) { {
      ()
    }; i = i + 1 } }
    return i
  }
  def GetNumAvailableBytes(): scala.Int = {
    return this._streamPos - this._pos
  }
  def ReduceOffsets(subValue: scala.Int): scala.Unit = {
    this._bufferOffset = this._bufferOffset + subValue
    this._posLimit = this._posLimit - subValue
    this._pos = this._pos - subValue
    this._streamPos = this._streamPos - subValue
  }
}