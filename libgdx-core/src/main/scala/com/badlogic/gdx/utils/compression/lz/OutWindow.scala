package com.badlogic.gdx.utils.compression.lz

class OutWindow {
  var _buffer: scala.Array[scala.Byte] = null.asInstanceOf[scala.Array[scala.Byte]]
  var _pos: scala.Int = 0
  var _windowSize: scala.Int = 0
  var _streamPos: scala.Int = 0
  var _stream: java.io.OutputStream = null.asInstanceOf[java.io.OutputStream]
  def Create(windowSize: scala.Int): scala.Unit = {
    if ((this._buffer == null) || (this._windowSize != windowSize)) {
      this._buffer = new scala.Array[scala.Byte](windowSize)
    } else ()
    this._windowSize = windowSize
    this._pos = 0
    this._streamPos = 0
  }
  def SetStream(stream: java.io.OutputStream): scala.Unit = {
    this.ReleaseStream()
    this._stream = stream
  }
  def ReleaseStream(): scala.Unit = {
    this.Flush()
    this._stream = null
  }
  def Init(solid: scala.Boolean): scala.Unit = {
    if (!solid) {
      this._streamPos = 0
      this._pos = 0
    } else ()
  }
  def Flush(): scala.Unit = {
    val size: scala.Int = this._pos - this._streamPos
    if (size == 0) {
      return
    } else ()
    this._stream.write(this._buffer, this._streamPos, size)
    if (this._pos >= this._windowSize) {
      this._pos = 0
    } else ()
    this._streamPos = this._pos
  }
  def CopyBlock(distance: scala.Int, len$arg: scala.Int): scala.Unit = {
    var len: scala.Int = len$arg
    var pos: scala.Int = (this._pos - distance) - 1
    if (pos < 0) {
      pos = pos + this._windowSize
    } else ();
    { ; while (len != 0) { {
      if (pos >= this._windowSize) {
        pos = 0
      } else ()
      this._buffer({ this._pos += 1; this._pos }) = this._buffer({ pos += 1; pos })
      if (this._pos >= this._windowSize) {
        this.Flush()
      } else ()
    }; len = len - 1 } }
  }
  def PutByte(b: scala.Byte): scala.Unit = {
    this._buffer({ this._pos += 1; this._pos }) = b
    if (this._pos >= this._windowSize) {
      this.Flush()
    } else ()
  }
  def GetByte(distance: scala.Int): scala.Byte = {
    var pos: scala.Int = (this._pos - distance) - 1
    if (pos < 0) {
      pos = pos + this._windowSize
    } else ()
    return this._buffer(pos)
  }
}