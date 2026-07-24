package com.badlogic.gdx.utils.compression.lzma

class Decoder {
  var m_OutWindow: com.badlogic.gdx.utils.compression.lz.OutWindow = new com.badlogic.gdx.utils.compression.lz.OutWindow()
  var m_RangeDecoder: com.badlogic.gdx.utils.compression.rangecoder.Decoder = new com.badlogic.gdx.utils.compression.rangecoder.Decoder()
  var m_IsMatchDecoders: scala.Array[scala.Short] = new scala.Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax)
  var m_IsRepDecoders: scala.Array[scala.Short] = new scala.Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var m_IsRepG0Decoders: scala.Array[scala.Short] = new scala.Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var m_IsRepG1Decoders: scala.Array[scala.Short] = new scala.Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var m_IsRepG2Decoders: scala.Array[scala.Short] = new scala.Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var m_IsRep0LongDecoders: scala.Array[scala.Short] = new scala.Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax)
  var m_PosSlotDecoder: scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder] = new scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder](com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStates)
  var m_PosDecoders: scala.Array[scala.Short] = new scala.Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances - com.badlogic.gdx.utils.compression.lzma.Base.kEndPosModelIndex)
  var m_PosAlignDecoder: com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits)
  var m_LenDecoder: LenDecoder = new LenDecoder()
  var m_RepLenDecoder: LenDecoder = new LenDecoder()
  var m_LiteralDecoder: LiteralDecoder = new LiteralDecoder()
  var m_DictionarySize: scala.Int = -1
  var m_DictionarySizeCheck: scala.Int = -1
  var m_PosStateMask: scala.Int = 0;
  { var i: scala.Int = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStates) { {
    this.m_PosSlotDecoder(i) = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumPosSlotBits)
  }; i = i + 1 } }
  def SetDictionarySize(dictionarySize: scala.Int): scala.Boolean = {
    if (dictionarySize < 0) {
      return false
    } else ()
    if (this.m_DictionarySize != dictionarySize) {
      this.m_DictionarySize = dictionarySize
      this.m_DictionarySizeCheck = java.lang.Math.max(this.m_DictionarySize, 1)
      this.m_OutWindow.Create(java.lang.Math.max(this.m_DictionarySizeCheck, 1 << 12))
    } else ()
    return true
  }
  def SetLcLpPb(lc: scala.Int, lp: scala.Int, pb: scala.Int): scala.Boolean = {
    if (((lc > com.badlogic.gdx.utils.compression.lzma.Base.kNumLitContextBitsMax) || (lp > 4)) || (pb > com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax)) {
      return false
    } else ()
    this.m_LiteralDecoder.Create(lp, lc)
    val numPosStates: scala.Int = 1 << pb
    this.m_LenDecoder.Create(numPosStates)
    this.m_RepLenDecoder.Create(numPosStates)
    this.m_PosStateMask = numPosStates - 1
    return true
  }
  def Init(): scala.Unit = {
    this.m_OutWindow.Init(false)
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_IsMatchDecoders)
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_IsRep0LongDecoders)
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_IsRepDecoders)
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_IsRepG0Decoders)
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_IsRepG1Decoders)
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_IsRepG2Decoders)
    com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_PosDecoders)
    this.m_LiteralDecoder.Init()
    var i: scala.Int = 0;
    { i = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStates) { {
      this.m_PosSlotDecoder(i).Init()
    }; i = i + 1 } }
    this.m_LenDecoder.Init()
    this.m_RepLenDecoder.Init()
    this.m_PosAlignDecoder.Init()
    this.m_RangeDecoder.Init()
  }
  def Code(inStream: java.io.InputStream, outStream: java.io.OutputStream, outSize: scala.Long): scala.Boolean = {
    this.m_RangeDecoder.SetStream(inStream)
    this.m_OutWindow.SetStream(outStream)
    this.Init()
    var state: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.StateInit()
    var rep0: scala.Int = 0
    var rep1: scala.Int = 0
    var rep2: scala.Int = 0
    var rep3: scala.Int = 0
    var nowPos64: scala.Long = 0
    var prevByte: scala.Byte = 0.asInstanceOf[scala.Byte]
    while ((outSize < 0) || (nowPos64 < outSize)) {
      val posState: scala.Int = nowPos64.asInstanceOf[scala.Int] & this.m_PosStateMask
      if (this.m_RangeDecoder.DecodeBit(this.m_IsMatchDecoders, (state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState) == 0) {
        val decoder2: Decoder2 = this.m_LiteralDecoder.GetDecoder(nowPos64.asInstanceOf[scala.Int].asInstanceOf[scala.Int], prevByte)
        if (!com.badlogic.gdx.utils.compression.lzma.Base.StateIsCharState(state)) {
          prevByte = decoder2.DecodeWithMatchByte(this.m_RangeDecoder, this.m_OutWindow.GetByte(rep0))
        } else {
          prevByte = decoder2.DecodeNormal(this.m_RangeDecoder)
        }
        this.m_OutWindow.PutByte(prevByte)
        state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(state)
        nowPos64 = nowPos64 + 1
      } else {
        var len: scala.Int = 0
        if (this.m_RangeDecoder.DecodeBit(this.m_IsRepDecoders, state) == 1) {
          len = 0
          if (this.m_RangeDecoder.DecodeBit(this.m_IsRepG0Decoders, state) == 0) {
            if (this.m_RangeDecoder.DecodeBit(this.m_IsRep0LongDecoders, (state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState) == 0) {
              state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateShortRep(state)
              len = 1
            } else ()
          } else {
            var distance: scala.Int = 0
            if (this.m_RangeDecoder.DecodeBit(this.m_IsRepG1Decoders, state) == 0) {
              distance = rep1
            } else {
              if (this.m_RangeDecoder.DecodeBit(this.m_IsRepG2Decoders, state) == 0) {
                distance = rep2
              } else {
                distance = rep3
                rep3 = rep2
              }
              rep2 = rep1
            }
            rep1 = rep0
            rep0 = distance
          }
          if (len == 0) {
            len = this.m_RepLenDecoder.Decode(this.m_RangeDecoder, posState) + com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen
            state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateRep(state)
          } else ()
        } else {
          rep3 = rep2
          rep2 = rep1
          rep1 = rep0
          len = com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen + this.m_LenDecoder.Decode(this.m_RangeDecoder, posState)
          state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateMatch(state)
          val posSlot: scala.Int = this.m_PosSlotDecoder(com.badlogic.gdx.utils.compression.lzma.Base.GetLenToPosState(len)).Decode(this.m_RangeDecoder)
          if (posSlot >= com.badlogic.gdx.utils.compression.lzma.Base.kStartPosModelIndex) {
            val numDirectBits: scala.Int = (posSlot >> 1) - 1
            rep0 = (2 | (posSlot & 1)) << numDirectBits
            if (posSlot < com.badlogic.gdx.utils.compression.lzma.Base.kEndPosModelIndex) {
              rep0 = rep0 + com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder.ReverseDecode(this.m_PosDecoders, (rep0 - posSlot) - 1, this.m_RangeDecoder, numDirectBits)
            } else {
              rep0 = rep0 + (this.m_RangeDecoder.DecodeDirectBits(numDirectBits - com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits) << com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits)
              rep0 = rep0 + this.m_PosAlignDecoder.ReverseDecode(this.m_RangeDecoder)
              if (rep0 < 0) {
                if (rep0 == (-1)) {
                  /* break */ ()
                } else ()
                return false
              } else ()
            }
          } else {
            rep0 = posSlot
          }
        }
        if ((rep0 >= nowPos64) || (rep0 >= this.m_DictionarySizeCheck)) {
          return false
        } else ()
        this.m_OutWindow.CopyBlock(rep0, len)
        nowPos64 = nowPos64 + len
        prevByte = this.m_OutWindow.GetByte(0)
      }
    }
    this.m_OutWindow.Flush()
    this.m_OutWindow.ReleaseStream()
    this.m_RangeDecoder.ReleaseStream()
    return true
  }
  def SetDecoderProperties(properties: scala.Array[scala.Byte]): scala.Boolean = {
    if (properties.length < 5) {
      return false
    } else ()
    val `val`: scala.Int = properties(0) & 255
    val lc: scala.Int = `val` % 9
    val remainder: scala.Int = `val` / 9
    val lp: scala.Int = remainder % 5
    val pb: scala.Int = remainder / 5
    var dictionarySize: scala.Int = 0;
    { var i: scala.Int = 0; while (i < 4) { {
      dictionarySize = dictionarySize + ((properties(1 + i).asInstanceOf[scala.Int] & 255) << (i * 8))
    }; i = i + 1 } }
    if (!this.SetLcLpPb(lc, lp, pb)) {
      return false
    } else ()
    return this.SetDictionarySize(dictionarySize)
  }
  class LenDecoder {
    var m_Choice: scala.Array[scala.Short] = new scala.Array[scala.Short](2)
    var m_LowCoder: scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder] = new scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder](com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesMax)
    var m_MidCoder: scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder] = new scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder](com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesMax)
    var m_HighCoder: com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumHighLenBits)
    var m_NumPosStates: scala.Int = 0
    def Create(numPosStates: scala.Int): scala.Unit = {
      { ; while (this.m_NumPosStates < numPosStates) { {
        this.m_LowCoder(this.m_NumPosStates) = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenBits)
        this.m_MidCoder(this.m_NumPosStates) = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeDecoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenBits)
      }; this.m_NumPosStates = this.m_NumPosStates + 1 } }
    }
    def Init(): scala.Unit = {
      com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_Choice);
      { var posState: scala.Int = 0; while (posState < this.m_NumPosStates) { {
        this.m_LowCoder(posState).Init()
        this.m_MidCoder(posState).Init()
      }; posState = posState + 1 } }
      this.m_HighCoder.Init()
    }
    def Decode(rangeDecoder: com.badlogic.gdx.utils.compression.rangecoder.Decoder, posState: scala.Int): scala.Int = {
      if (rangeDecoder.DecodeBit(this.m_Choice, 0) == 0) {
        return this.m_LowCoder(posState).Decode(rangeDecoder)
      } else ()
      var symbol: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols
      if (rangeDecoder.DecodeBit(this.m_Choice, 1) == 0) {
        symbol = symbol + this.m_MidCoder(posState).Decode(rangeDecoder)
      } else {
        symbol = symbol + (com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenSymbols + this.m_HighCoder.Decode(rangeDecoder))
      }
      return symbol
    }
  }
  class LiteralDecoder {
    var m_Coders: scala.Array[Decoder2] = null.asInstanceOf[scala.Array[Decoder2]]
    var m_NumPrevBits: scala.Int = 0
    var m_NumPosBits: scala.Int = 0
    var m_PosMask: scala.Int = 0
    def Create(numPosBits: scala.Int, numPrevBits: scala.Int): scala.Unit = {
      if (((this.m_Coders != null) && (this.m_NumPrevBits == numPrevBits)) && (this.m_NumPosBits == numPosBits)) {
        return
      } else ()
      this.m_NumPosBits = numPosBits
      this.m_PosMask = (1 << numPosBits) - 1
      this.m_NumPrevBits = numPrevBits
      val numStates: scala.Int = 1 << (this.m_NumPrevBits + this.m_NumPosBits)
      this.m_Coders = new scala.Array[Decoder2](numStates);
      { var i: scala.Int = 0; while (i < numStates) { {
        this.m_Coders(i) = new Decoder2()
      }; i = i + 1 } }
    }
    def Init(): scala.Unit = {
      val numStates: scala.Int = 1 << (this.m_NumPrevBits + this.m_NumPosBits);
      { var i: scala.Int = 0; while (i < numStates) { {
        this.m_Coders(i).Init()
      }; i = i + 1 } }
    }
    def GetDecoder(pos: scala.Int, prevByte: scala.Byte): Decoder2 = {
      return this.m_Coders(((pos & this.m_PosMask) << this.m_NumPrevBits) + ((prevByte & 255) >>> (8 - this.m_NumPrevBits)))
    }
    class Decoder2 {
      var m_Decoders: scala.Array[scala.Short] = new scala.Array[scala.Short](768)
      def Init(): scala.Unit = {
        com.badlogic.gdx.utils.compression.rangecoder.Decoder.InitBitModels(this.m_Decoders)
      }
      def DecodeNormal(rangeDecoder: com.badlogic.gdx.utils.compression.rangecoder.Decoder): scala.Byte = {
        var symbol: scala.Int = 1
        while ({ {
          symbol = (symbol << 1) | rangeDecoder.DecodeBit(this.m_Decoders, symbol)
        }; symbol < 256 }) ()
        return symbol.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      }
      def DecodeWithMatchByte(rangeDecoder: com.badlogic.gdx.utils.compression.rangecoder.Decoder, matchByte$arg: scala.Byte): scala.Byte = {
        var matchByte: scala.Byte = matchByte$arg
        var symbol: scala.Int = 1
        while ({ {
          val matchBit: scala.Int = (matchByte >> 7) & 1
          matchByte = (matchByte << 1).asInstanceOf[scala.Byte]
          val bit: scala.Int = rangeDecoder.DecodeBit(this.m_Decoders, ((1 + matchBit) << 8) + symbol)
          symbol = (symbol << 1) | bit
          if (matchBit != bit) {
            while (symbol < 256) {
              symbol = (symbol << 1) | rangeDecoder.DecodeBit(this.m_Decoders, symbol)
            }
            /* break */ ()
          } else ()
        }; symbol < 256 }) ()
        return symbol.asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
      }
    }
  }
}