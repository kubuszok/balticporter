package com.badlogic.gdx.utils.compression

class CRC {
  var _value: scala.Int = -1
  def Init(): scala.Unit = {
    this._value = -1
  }
  def Update(data: scala.Array[scala.Byte], offset: scala.Int, size: scala.Int): scala.Unit = {
    { var i: scala.Int = 0; while (i < size) { {
      this._value = CRC.Table((this._value ^ data(offset + i)) & 255) ^ (this._value >>> 8)
    }; i = i + 1 } }
  }
  def Update(data: scala.Array[scala.Byte]): scala.Unit = {
    val size: scala.Int = data.length;
    { var i: scala.Int = 0; while (i < size) { {
      this._value = CRC.Table((this._value ^ data(i)) & 255) ^ (this._value >>> 8)
    }; i = i + 1 } }
  }
  def UpdateByte(b: scala.Int): scala.Unit = {
    this._value = CRC.Table((this._value ^ b) & 255) ^ (this._value >>> 8)
  }
  def GetDigest(): scala.Int = {
    return this._value ^ (-1)
  }
}
object CRC {
  var Table: scala.Array[scala.Int] = new scala.Array[scala.Int](256)
  locally {
    { var i: scala.Int = 0; while (i < 256) { {
      var r: scala.Int = i;
      { var j: scala.Int = 0; while (j < 8) { {
        if ((r & 1) != 0) {
          r = (r >>> 1) ^ -306674912
        } else {
          r = r >>> 1
        }
      }; j = j + 1 } }
      CRC.Table(i) = r
    }; i = i + 1 } }
  }
}