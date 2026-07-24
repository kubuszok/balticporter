package com.badlogic.gdx.utils.compression.rangecoder

class BitTreeDecoder {
  var Models: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  var NumBitLevels: scala.Int = 0
  def this(numBitLevels: scala.Int) = {
    this()
    this.NumBitLevels = numBitLevels
    this.Models = new Array[scala.Short](1 << numBitLevels)
  }
  def Init(): scala.Unit = {
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.Models)
  }
  def Decode(rangeDecoder: com.badlogic.gdx.utils.compression.rangecoder.Decoder): scala.Int = {
    var m: scala.Int = 1
    { var bitIndex: scala.Int = this.NumBitLevels; while (bitIndex != 0) { {
      m = (m << 1) + rangeDecoder.DecodeBit(this.Models, m)
    }; bitIndex = bitIndex - 1 } }
    return m - (1 << this.NumBitLevels)
  }
  def ReverseDecode(rangeDecoder: com.badlogic.gdx.utils.compression.rangecoder.Decoder): scala.Int = {
    var m: scala.Int = 1
    var symbol: scala.Int = 0
    { var bitIndex: scala.Int = 0; while (bitIndex < this.NumBitLevels) { {
      val bit: scala.Int = rangeDecoder.DecodeBit(this.Models, m)
      m = m << 1
      m = m + bit
      symbol = symbol | (bit << bitIndex)
    }; bitIndex = bitIndex + 1 } }
    return symbol
  }
}
object BitTreeDecoder {
  def ReverseDecode(Models: scala.Array[scala.Short], startIndex: scala.Int, rangeDecoder: com.badlogic.gdx.utils.compression.rangecoder.Decoder, NumBitLevels: scala.Int): scala.Int = {
    var m: scala.Int = 1
    var symbol: scala.Int = 0
    { var bitIndex: scala.Int = 0; while (bitIndex < NumBitLevels) { {
      val bit: scala.Int = rangeDecoder.DecodeBit(Models, startIndex + m)
      m = m << 1
      m = m + bit
      symbol = symbol | (bit << bitIndex)
    }; bitIndex = bitIndex + 1 } }
    return symbol
  }
}