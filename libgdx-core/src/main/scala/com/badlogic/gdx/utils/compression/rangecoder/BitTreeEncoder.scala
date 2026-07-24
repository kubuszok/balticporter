package com.badlogic.gdx.utils.compression.rangecoder

class BitTreeEncoder {
  var Models: scala.Array[scala.Short] = null.asInstanceOf[scala.Array[scala.Short]]
  var NumBitLevels: scala.Int = 0
  def this(numBitLevels: scala.Int) = {
    this()
    this.NumBitLevels = numBitLevels
    this.Models = new scala.Array[scala.Short](1 << numBitLevels)
  }
  def Init(): scala.Unit = {
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.Models)
  }
  def Encode(rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder, symbol: scala.Int): scala.Unit = {
    var m: scala.Int = 1;
    { var bitIndex: scala.Int = this.NumBitLevels; while (bitIndex != 0) { {
      bitIndex = bitIndex - 1
      val bit: scala.Int = (symbol >>> bitIndex) & 1
      rangeEncoder.Encode(this.Models, m, bit)
      m = (m << 1) | bit
    };  } }
  }
  def ReverseEncode(rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder, symbol$arg: scala.Int): scala.Unit = {
    var symbol: scala.Int = symbol$arg
    var m: scala.Int = 1;
    { var i: scala.Int = 0; while (i < this.NumBitLevels) { {
      val bit: scala.Int = symbol & 1
      rangeEncoder.Encode(this.Models, m, bit)
      m = (m << 1) | bit
      symbol = symbol >> 1
    }; i = i + 1 } }
  }
  def GetPrice(symbol: scala.Int): scala.Int = {
    var price: scala.Int = 0
    var m: scala.Int = 1;
    { var bitIndex: scala.Int = this.NumBitLevels; while (bitIndex != 0) { {
      bitIndex = bitIndex - 1
      val bit: scala.Int = (symbol >>> bitIndex) & 1
      price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice(this.Models(m), bit)
      m = (m << 1) + bit
    };  } }
    return price
  }
  def ReverseGetPrice(symbol$arg: scala.Int): scala.Int = {
    var symbol: scala.Int = symbol$arg
    var price: scala.Int = 0
    var m: scala.Int = 1;
    { var i: scala.Int = this.NumBitLevels; while (i != 0) { {
      val bit: scala.Int = symbol & 1
      symbol = symbol >>> 1
      price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice(this.Models(m), bit)
      m = (m << 1) | bit
    }; i = i - 1 } }
    return price
  }
}
object BitTreeEncoder {
  def ReverseGetPrice(Models: scala.Array[scala.Short], startIndex: scala.Int, NumBitLevels: scala.Int, symbol$arg: scala.Int): scala.Int = {
    var symbol: scala.Int = symbol$arg
    var price: scala.Int = 0
    var m: scala.Int = 1;
    { var i: scala.Int = NumBitLevels; while (i != 0) { {
      val bit: scala.Int = symbol & 1
      symbol = symbol >>> 1
      price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice(Models(startIndex + m), bit)
      m = (m << 1) | bit
    }; i = i - 1 } }
    return price
  }
  def ReverseEncode(Models: scala.Array[scala.Short], startIndex: scala.Int, rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder, NumBitLevels: scala.Int, symbol$arg: scala.Int): scala.Unit = {
    var symbol: scala.Int = symbol$arg
    var m: scala.Int = 1;
    { var i: scala.Int = 0; while (i < NumBitLevels) { {
      val bit: scala.Int = symbol & 1
      rangeEncoder.Encode(Models, startIndex + m, bit)
      m = (m << 1) | bit
      symbol = symbol >> 1
    }; i = i + 1 } }
  }
}