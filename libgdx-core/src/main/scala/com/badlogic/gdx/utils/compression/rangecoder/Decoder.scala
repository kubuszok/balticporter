package com.badlogic.gdx.utils.compression.rangecoder

class Decoder {
  var Range: scala.Int = 0
  var Code: scala.Int = 0
  var Stream: java.io.InputStream = null.asInstanceOf[java.io.InputStream]
  final def SetStream(stream: java.io.InputStream): scala.Unit = {
    this.Stream = stream
  }
  final def ReleaseStream(): scala.Unit = {
    this.Stream = null
  }
  final def Init(): scala.Unit = {
    this.Code = 0
    this.Range = -1
    { var i: scala.Int = 0; while (i < 5) { {
      this.Code = (this.Code << 8) | this.Stream.read()
    }; i = i + 1 } }
  }
  final def DecodeDirectBits(numTotalBits: scala.Int): scala.Int = {
    var result: scala.Int = 0
    { var i: scala.Int = numTotalBits; while (i != 0) { {
      this.Range = this.Range >>> 1
      val t: scala.Int = (this.Code - this.Range) >>> 31
      this.Code = this.Code - (this.Range & (t - 1))
      result = (result << 1) | (1 - t)
      if ((this.Range & Decoder.kTopMask) == 0) {
        this.Code = (this.Code << 8) | this.Stream.read()
        this.Range = this.Range << 8
      } else ()
    }; i = i - 1 } }
    return result
  }
  def DecodeBit(probs: scala.Array[scala.Short], index: scala.Int): scala.Int = {
    val prob: scala.Int = probs(index)
    val newBound: scala.Int = (this.Range >>> Decoder.kNumBitModelTotalBits) * prob
    if ((this.Code ^ -2147483648) < (newBound ^ -2147483648)) {
      this.Range = newBound
      probs(index) = (prob + ((Decoder.kBitModelTotal - prob) >>> Decoder.kNumMoveBits)).asInstanceOf[scala.Short]
      if ((this.Range & Decoder.kTopMask) == 0) {
        this.Code = (this.Code << 8) | this.Stream.read()
        this.Range = this.Range << 8
      } else ()
      return 0
    } else {
      this.Range = this.Range - newBound
      this.Code = this.Code - newBound
      probs(index) = (prob - (prob >>> Decoder.kNumMoveBits)).asInstanceOf[scala.Short]
      if ((this.Range & Decoder.kTopMask) == 0) {
        this.Code = (this.Code << 8) | this.Stream.read()
        this.Range = this.Range << 8
      } else ()
      return 1
    }
  }
}
object Decoder {
  final val kTopMask: scala.Int = ~((1 << 24) - 1)
  final val kNumBitModelTotalBits: scala.Int = 11
  final val kBitModelTotal: scala.Int = 1 << Decoder.kNumBitModelTotalBits
  final val kNumMoveBits: scala.Int = 5
  def InitBitModels(probs: scala.Array[scala.Short]): scala.Unit = {
    { var i: scala.Int = 0; while (i < probs.length) { {
      probs(i) = Decoder.kBitModelTotal >>> 1
    }; i = i + 1 } }
  }
}