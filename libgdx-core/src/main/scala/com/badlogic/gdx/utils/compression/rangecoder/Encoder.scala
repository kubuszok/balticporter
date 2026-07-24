package com.badlogic.gdx.utils.compression.rangecoder

class Encoder {
  var Stream: java.io.OutputStream = null.asInstanceOf[java.io.OutputStream]
  var Low: scala.Long = 0L
  var Range: scala.Int = 0
  var _cacheSize: scala.Int = 0
  var _cache: scala.Int = 0
  var _position: scala.Long = 0L
  def SetStream(stream: java.io.OutputStream): scala.Unit = {
    this.Stream = stream
  }
  def ReleaseStream(): scala.Unit = {
    this.Stream = null
  }
  def Init(): scala.Unit = {
    this._position = 0
    this.Low = 0
    this.Range = -1
    this._cacheSize = 1
    this._cache = 0
  }
  def FlushData(): scala.Unit = {
    { var i: scala.Int = 0; while (i < 5) { {
      this.ShiftLow()
    }; i = i + 1 } }
  }
  def FlushStream(): scala.Unit = {
    this.Stream.flush()
  }
  def ShiftLow(): scala.Unit = {
    val LowHi: scala.Int = (this.Low >>> 32).asInstanceOf[scala.Int].asInstanceOf[scala.Int]
    if ((LowHi != 0) || (this.Low < 4278190080L)) {
      this._position = this._position + this._cacheSize
      var temp: scala.Int = this._cache
      while ({ {
        this.Stream.write(temp + LowHi)
        temp = 255
      }; { this._cacheSize -= 1; this._cacheSize } != 0 }) ()
      this._cache = this.Low.asInstanceOf[scala.Int] >>> 24
    } else ()
    this._cacheSize = this._cacheSize + 1
    this.Low = (this.Low & 16777215) << 8
  }
  def EncodeDirectBits(v: scala.Int, numTotalBits: scala.Int): scala.Unit = {
    { var i: scala.Int = numTotalBits - 1; while (i >= 0) { {
      this.Range = this.Range >>> 1
      if (((v >>> i) & 1) == 1) {
        this.Low = this.Low + this.Range
      } else ()
      if ((this.Range & Encoder.kTopMask) == 0) {
        this.Range = this.Range << 8
        this.ShiftLow()
      } else ()
    }; i = i - 1 } }
  }
  def GetProcessedSizeAdd(): scala.Long = {
    return (this._cacheSize + this._position) + 4
  }
  def Encode(probs: scala.Array[scala.Short], index: scala.Int, symbol: scala.Int): scala.Unit = {
    val prob: scala.Int = probs(index)
    val newBound: scala.Int = (this.Range >>> Encoder.kNumBitModelTotalBits) * prob
    if (symbol == 0) {
      this.Range = newBound
      probs(index) = (prob + ((Encoder.kBitModelTotal - prob) >>> Encoder.kNumMoveBits)).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    } else {
      this.Low = this.Low + (newBound & 4294967295L)
      this.Range = this.Range - newBound
      probs(index) = (prob - (prob >>> Encoder.kNumMoveBits)).asInstanceOf[scala.Short].asInstanceOf[scala.Short]
    }
    if ((this.Range & Encoder.kTopMask) == 0) {
      this.Range = this.Range << 8
      this.ShiftLow()
    } else ()
  }
}
object Encoder {
  final val kTopMask: scala.Int = ~((1 << 24) - 1)
  final val kNumBitModelTotalBits: scala.Int = 11
  final val kBitModelTotal: scala.Int = 1 << Encoder.kNumBitModelTotalBits
  final val kNumMoveBits: scala.Int = 5
  final val kNumMoveReducingBits: scala.Int = 2
  final val kNumBitPriceShiftBits: scala.Int = 6
  private var ProbPrices: scala.Array[scala.Int] = new scala.Array[scala.Int](Encoder.kBitModelTotal >>> Encoder.kNumMoveReducingBits)
  def InitBitModels(probs: scala.Array[scala.Short]): scala.Unit = {
    { var i: scala.Int = 0; while (i < probs.length) { {
      probs(i) = (Encoder.kBitModelTotal >>> 1).asInstanceOf[scala.Short]
    }; i = i + 1 } }
  }
  def GetPrice(Prob: scala.Int, symbol: scala.Int): scala.Int = {
    return Encoder.ProbPrices((((Prob - symbol) ^ (-symbol)) & (Encoder.kBitModelTotal - 1)) >>> Encoder.kNumMoveReducingBits)
  }
  def GetPrice0(Prob: scala.Int): scala.Int = {
    return Encoder.ProbPrices(Prob >>> Encoder.kNumMoveReducingBits)
  }
  def GetPrice1(Prob: scala.Int): scala.Int = {
    return Encoder.ProbPrices((Encoder.kBitModelTotal - Prob) >>> Encoder.kNumMoveReducingBits)
  }
}