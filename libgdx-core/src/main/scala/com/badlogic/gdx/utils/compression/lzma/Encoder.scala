package com.badlogic.gdx.utils.compression.lzma

class Encoder {
  var _state: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.StateInit()
  var _previousByte: scala.Byte = 0
  var _repDistances: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances)
  var _optimum: scala.Array[Optimal] = new Array[Optimal](Encoder.kNumOpts)
  var _matchFinder: com.badlogic.gdx.utils.compression.lz.BinTree = null
  var _rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder = new com.badlogic.gdx.utils.compression.rangecoder.Encoder()
  var _isMatch: scala.Array[scala.Short] = new Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax)
  var _isRep: scala.Array[scala.Short] = new Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var _isRepG0: scala.Array[scala.Short] = new Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var _isRepG1: scala.Array[scala.Short] = new Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var _isRepG2: scala.Array[scala.Short] = new Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates)
  var _isRep0Long: scala.Array[scala.Short] = new Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumStates << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax)
  var _posSlotEncoder: scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder] = new Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder](com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStates)
  var _posEncoders: scala.Array[scala.Short] = new Array[scala.Short](com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances - com.badlogic.gdx.utils.compression.lzma.Base.kEndPosModelIndex)
  var _posAlignEncoder: com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits)
  var _lenEncoder: LenPriceTableEncoder = new LenPriceTableEncoder()
  var _repMatchLenEncoder: LenPriceTableEncoder = new LenPriceTableEncoder()
  var _literalEncoder: LiteralEncoder = new LiteralEncoder()
  var _matchDistances: scala.Array[scala.Int] = new Array[scala.Int]((com.badlogic.gdx.utils.compression.lzma.Base.kMatchMaxLen * 2) + 2)
  var _numFastBytes: scala.Int = Encoder.kNumFastBytesDefault
  var _longestMatchLength: scala.Int = 0
  var _numDistancePairs: scala.Int = 0
  var _additionalOffset: scala.Int = 0
  var _optimumEndIndex: scala.Int = 0
  var _optimumCurrentIndex: scala.Int = 0
  var _longestMatchWasFound: scala.Boolean = false
  var _posSlotPrices: scala.Array[scala.Int] = new Array[scala.Int](1 << (com.badlogic.gdx.utils.compression.lzma.Base.kNumPosSlotBits + com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStatesBits))
  var _distancesPrices: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances << com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStatesBits)
  var _alignPrices: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kAlignTableSize)
  var _alignPriceCount: scala.Int = 0
  var _distTableSize: scala.Int = Encoder.kDefaultDictionaryLogSize * 2
  var _posStateBits: scala.Int = 2
  var _posStateMask: scala.Int = 4 - 1
  var _numLiteralPosStateBits: scala.Int = 0
  var _numLiteralContextBits: scala.Int = 3
  var _dictionarySize: scala.Int = 1 << Encoder.kDefaultDictionaryLogSize
  var _dictionarySizePrev: scala.Int = -1
  var _numFastBytesPrev: scala.Int = -1
  var nowPos64: scala.Long = 0L
  var _finished: scala.Boolean = false
  var _inStream: java.io.InputStream = null.asInstanceOf[java.io.InputStream]
  var _matchFinderType: scala.Int = Encoder.EMatchFinderTypeBT4
  var _writeEndMark: scala.Boolean = false
  var _needReleaseMFStream: scala.Boolean = false
  var reps: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances)
  var repLens: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances)
  var backRes: scala.Int = 0
  var processedInSize: scala.Array[scala.Long] = new Array[scala.Long](1)
  var processedOutSize: scala.Array[scala.Long] = new Array[scala.Long](1)
  var finished: scala.Array[scala.Boolean] = new Array[scala.Boolean](1)
  var properties: scala.Array[scala.Byte] = new Array[scala.Byte](Encoder.kPropSize)
  var tempPrices: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances)
  var _matchPriceCount: scala.Int = 0;
  { var i: scala.Int = 0; while (i < Encoder.kNumOpts) { {
    this._optimum(i) = new Optimal()
  }; i = i + 1 } };
  { var i: scala.Int = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStates) { {
    this._posSlotEncoder(i) = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumPosSlotBits)
  }; i = i + 1 } }
  def BaseInit(): scala.Unit = {
    this._state = com.badlogic.gdx.utils.compression.lzma.Base.StateInit()
    this._previousByte = 0.asInstanceOf[scala.Byte];
    { var i: scala.Int = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) { {
      this._repDistances(i) = 0
    }; i = i + 1 } }
  }
  def Create(): scala.Unit = {
    if (this._matchFinder == null) {
      val bt: com.badlogic.gdx.utils.compression.lz.BinTree = new com.badlogic.gdx.utils.compression.lz.BinTree()
      var numHashBytes: scala.Int = 4
      if (this._matchFinderType == Encoder.EMatchFinderTypeBT2) {
        numHashBytes = 2
      } else ()
      bt.SetType(numHashBytes)
      this._matchFinder = bt
    } else ()
    this._literalEncoder.Create(this._numLiteralPosStateBits, this._numLiteralContextBits)
    if ((this._dictionarySize == this._dictionarySizePrev) && (this._numFastBytesPrev == this._numFastBytes)) {
      return
    } else ()
    this._matchFinder.Create(this._dictionarySize, Encoder.kNumOpts, this._numFastBytes, com.badlogic.gdx.utils.compression.lzma.Base.kMatchMaxLen + 1)
    this._dictionarySizePrev = this._dictionarySize
    this._numFastBytesPrev = this._numFastBytes
  }
  def SetWriteEndMarkerMode(writeEndMarker: scala.Boolean): scala.Unit = {
    this._writeEndMark = writeEndMarker
  }
  def Init(): scala.Unit = {
    this.BaseInit()
    this._rangeEncoder.Init()
    com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._isMatch)
    com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._isRep0Long)
    com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._isRep)
    com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._isRepG0)
    com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._isRepG1)
    com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._isRepG2)
    com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._posEncoders)
    this._literalEncoder.Init();
    { var i: scala.Int = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStates) { {
      this._posSlotEncoder(i).Init()
    }; i = i + 1 } }
    this._lenEncoder.Init(1 << this._posStateBits)
    this._repMatchLenEncoder.Init(1 << this._posStateBits)
    this._posAlignEncoder.Init()
    this._longestMatchWasFound = false
    this._optimumEndIndex = 0
    this._optimumCurrentIndex = 0
    this._additionalOffset = 0
  }
  def ReadMatchDistances(): scala.Int = {
    var lenRes: scala.Int = 0
    this._numDistancePairs = this._matchFinder.GetMatches(this._matchDistances)
    if (this._numDistancePairs > 0) {
      lenRes = this._matchDistances(this._numDistancePairs - 2)
      if (lenRes == this._numFastBytes) {
        lenRes = lenRes + this._matchFinder.GetMatchLen(lenRes.asInstanceOf[scala.Int] - 1, this._matchDistances(this._numDistancePairs - 1), com.badlogic.gdx.utils.compression.lzma.Base.kMatchMaxLen - lenRes)
      } else ()
    } else ()
    this._additionalOffset = this._additionalOffset + 1
    return lenRes
  }
  def MovePos(num: scala.Int): scala.Unit = {
    if (num > 0) {
      this._matchFinder.Skip(num)
      this._additionalOffset = this._additionalOffset + num
    } else ()
  }
  def GetRepLen1Price(state: scala.Int, posState: scala.Int): scala.Int = {
    return com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isRepG0(state)) + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isRep0Long((state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState))
  }
  def GetPureRepPrice(repIndex: scala.Int, state: scala.Int, posState: scala.Int): scala.Int = {
    var price: scala.Int = 0
    if (repIndex == 0) {
      price = com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isRepG0(state))
      price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRep0Long((state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState))
    } else {
      price = com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRepG0(state))
      if (repIndex == 1) {
        price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isRepG1(state))
      } else {
        price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRepG1(state))
        price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice(this._isRepG2(state), repIndex - 2)
      }
    }
    return price
  }
  def GetRepPrice(repIndex: scala.Int, len: scala.Int, state: scala.Int, posState: scala.Int): scala.Int = {
    val price: scala.Int = this._repMatchLenEncoder.GetPrice(len - com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen, posState)
    return price + this.GetPureRepPrice(repIndex, state, posState)
  }
  def GetPosLenPrice(pos: scala.Int, len: scala.Int, posState: scala.Int): scala.Int = {
    var price: scala.Int = 0
    val lenToPosState: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.GetLenToPosState(len)
    if (pos < com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances) {
      price = this._distancesPrices((lenToPosState * com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances) + pos)
    } else {
      price = this._posSlotPrices((lenToPosState << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosSlotBits) + Encoder.GetPosSlot2(pos)) + this._alignPrices(pos & com.badlogic.gdx.utils.compression.lzma.Base.kAlignMask)
    }
    return price + this._lenEncoder.GetPrice(len - com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen, posState)
  }
  def Backward(cur$arg: scala.Int): scala.Int = {
    var cur: scala.Int = cur$arg
    this._optimumEndIndex = cur
    var posMem: scala.Int = this._optimum(cur).PosPrev
    var backMem: scala.Int = this._optimum(cur).BackPrev
    while ({ {
      if (this._optimum(cur).Prev1IsChar) {
        this._optimum(posMem).MakeAsChar()
        this._optimum(posMem).PosPrev = posMem - 1
        if (this._optimum(cur).Prev2) {
          this._optimum(posMem - 1).Prev1IsChar = false
          this._optimum(posMem - 1).PosPrev = this._optimum(cur).PosPrev2
          this._optimum(posMem - 1).BackPrev = this._optimum(cur).BackPrev2
        } else ()
      } else ()
      val posPrev: scala.Int = posMem
      val backCur: scala.Int = backMem
      backMem = this._optimum(posPrev).BackPrev
      posMem = this._optimum(posPrev).PosPrev
      this._optimum(posPrev).BackPrev = backCur
      this._optimum(posPrev).PosPrev = cur
      cur = posPrev
    }; cur > 0 }) ()
    this.backRes = this._optimum(0).BackPrev
    this._optimumCurrentIndex = this._optimum(0).PosPrev
    return this._optimumCurrentIndex
  }
  def GetOptimum(position$arg: scala.Int): scala.Int = {
    var position: scala.Int = position$arg
    if (this._optimumEndIndex != this._optimumCurrentIndex) {
      val lenRes: scala.Int = this._optimum(this._optimumCurrentIndex).PosPrev - this._optimumCurrentIndex
      this.backRes = this._optimum(this._optimumCurrentIndex).BackPrev
      this._optimumCurrentIndex = this._optimum(this._optimumCurrentIndex).PosPrev
      return lenRes
    } else ()
    this._optimumCurrentIndex = {
      this._optimumEndIndex = 0
      this._optimumEndIndex
    }
    var lenMain: scala.Int = 0
    var numDistancePairs: scala.Int = 0
    if (!this._longestMatchWasFound) {
      lenMain = this.ReadMatchDistances()
    } else {
      lenMain = this._longestMatchLength
      this._longestMatchWasFound = false
    }
    numDistancePairs = this._numDistancePairs
    var numAvailableBytes: scala.Int = this._matchFinder.GetNumAvailableBytes() + 1
    if (numAvailableBytes < 2) {
      this.backRes = -1
      return 1
    } else ()
    if (numAvailableBytes > com.badlogic.gdx.utils.compression.lzma.Base.kMatchMaxLen) {
      numAvailableBytes = com.badlogic.gdx.utils.compression.lzma.Base.kMatchMaxLen
    } else ()
    var repMaxIndex: scala.Int = 0
    var i: scala.Int = 0;
    { i = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) { {
      this.reps(i) = this._repDistances(i)
      this.repLens(i) = this._matchFinder.GetMatchLen(0 - 1, this.reps(i), com.badlogic.gdx.utils.compression.lzma.Base.kMatchMaxLen)
      if (this.repLens(i) > this.repLens(repMaxIndex)) {
        repMaxIndex = i
      } else ()
    }; i = i + 1 } }
    if (this.repLens(repMaxIndex) >= this._numFastBytes) {
      this.backRes = repMaxIndex
      val lenRes: scala.Int = this.repLens(repMaxIndex)
      this.MovePos(lenRes - 1)
      return lenRes
    } else ()
    if (lenMain >= this._numFastBytes) {
      this.backRes = this._matchDistances(numDistancePairs - 1) + com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances
      this.MovePos(lenMain - 1)
      return lenMain
    } else ()
    var currentByte: scala.Byte = this._matchFinder.GetIndexByte(0 - 1)
    var matchByte: scala.Byte = this._matchFinder.GetIndexByte(((0 - this._repDistances(0)) - 1) - 1)
    if (((lenMain < 2) && (currentByte != matchByte)) && (this.repLens(repMaxIndex) < 2)) {
      this.backRes = -1
      return 1
    } else ()
    this._optimum(0).State = this._state
    var posState: scala.Int = position & this._posStateMask
    this._optimum(1).Price = com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isMatch((this._state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState)) + this._literalEncoder.GetSubCoder(position, this._previousByte).GetPrice(!com.badlogic.gdx.utils.compression.lzma.Base.StateIsCharState(this._state), matchByte, currentByte)
    this._optimum(1).MakeAsChar()
    var matchPrice: scala.Int = com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isMatch((this._state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState))
    var repMatchPrice: scala.Int = matchPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRep(this._state))
    if (matchByte == currentByte) {
      val shortRepPrice: scala.Int = repMatchPrice + this.GetRepLen1Price(this._state, posState)
      if (shortRepPrice < this._optimum(1).Price) {
        this._optimum(1).Price = shortRepPrice
        this._optimum(1).MakeAsShortRep()
      } else ()
    } else ()
    var lenEnd: scala.Int = if (lenMain >= this.repLens(repMaxIndex)) lenMain else this.repLens(repMaxIndex)
    if (lenEnd < 2) {
      this.backRes = this._optimum(1).BackPrev
      return 1
    } else ()
    this._optimum(1).PosPrev = 0
    this._optimum(0).Backs0 = this.reps(0)
    this._optimum(0).Backs1 = this.reps(1)
    this._optimum(0).Backs2 = this.reps(2)
    this._optimum(0).Backs3 = this.reps(3)
    var len: scala.Int = lenEnd
    while ({ {
      this._optimum({ len -= 1; len }).Price = Encoder.kIfinityPrice
    }; len >= 2 }) ();
    { i = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) { {
      var repLen: scala.Int = this.repLens(i)
      if (repLen < 2) {
        /* continue */ ()
      } else ()
      val price: scala.Int = repMatchPrice + this.GetPureRepPrice(i, this._state, posState)
      while ({ {
        var curAndLenPrice: scala.Int = price + this._repMatchLenEncoder.GetPrice(repLen - 2, posState)
        var optimum: Optimal = this._optimum(repLen)
        if (curAndLenPrice < optimum.Price) {
          optimum.Price = curAndLenPrice
          optimum.PosPrev = 0
          optimum.BackPrev = i
          optimum.Prev1IsChar = false
        } else ()
      }; { repLen -= 1; repLen } >= 2 }) ()
    }; i = i + 1 } }
    var normalMatchPrice: scala.Int = matchPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isRep(this._state))
    len = if (this.repLens(0) >= 2) this.repLens(0) + 1 else 2
    if (len <= lenMain) {
      var offs: scala.Int = 0
      while (len > this._matchDistances(offs)) {
        offs = offs + 2
      };
      { ; while (true) { {
        val distance: scala.Int = this._matchDistances(offs + 1)
        var curAndLenPrice: scala.Int = normalMatchPrice + this.GetPosLenPrice(distance, len, posState)
        var optimum: Optimal = this._optimum(len)
        if (curAndLenPrice < optimum.Price) {
          optimum.Price = curAndLenPrice
          optimum.PosPrev = 0
          optimum.BackPrev = distance + com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances
          optimum.Prev1IsChar = false
        } else ()
        if (len == this._matchDistances(offs)) {
          offs = offs + 2
          if (offs == numDistancePairs) {
            /* break */ ()
          } else ()
        } else ()
      }; len = len + 1 } }
    } else ()
    var cur: scala.Int = 0
    while (true) {
      cur = cur + 1
      if (cur == lenEnd) {
        return this.Backward(cur)
      } else ()
      var newLen: scala.Int = this.ReadMatchDistances()
      numDistancePairs = this._numDistancePairs
      if (newLen >= this._numFastBytes) {
        this._longestMatchLength = newLen
        this._longestMatchWasFound = true
        return this.Backward(cur)
      } else ()
      position = position + 1
      var posPrev: scala.Int = this._optimum(cur).PosPrev
      var state: scala.Int = 0
      if (this._optimum(cur).Prev1IsChar) {
        posPrev = posPrev - 1
        if (this._optimum(cur).Prev2) {
          state = this._optimum(this._optimum(cur).PosPrev2).State
          if (this._optimum(cur).BackPrev2 < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) {
            state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateRep(state)
          } else {
            state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateMatch(state)
          }
        } else {
          state = this._optimum(posPrev).State
        }
        state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(state)
      } else {
        state = this._optimum(posPrev).State
      }
      if (posPrev == (cur - 1)) {
        if (this._optimum(cur).IsShortRep()) {
          state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateShortRep(state)
        } else {
          state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(state)
        }
      } else {
        var pos: scala.Int = 0
        if (this._optimum(cur).Prev1IsChar && this._optimum(cur).Prev2) {
          posPrev = this._optimum(cur).PosPrev2
          pos = this._optimum(cur).BackPrev2
          state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateRep(state)
        } else {
          pos = this._optimum(cur).BackPrev
          if (pos < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) {
            state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateRep(state)
          } else {
            state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateMatch(state)
          }
        }
        val opt: Optimal = this._optimum(posPrev)
        if (pos < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) {
          if (pos == 0) {
            this.reps(0) = opt.Backs0
            this.reps(1) = opt.Backs1
            this.reps(2) = opt.Backs2
            this.reps(3) = opt.Backs3
          } else {
            if (pos == 1) {
              this.reps(0) = opt.Backs1
              this.reps(1) = opt.Backs0
              this.reps(2) = opt.Backs2
              this.reps(3) = opt.Backs3
            } else {
              if (pos == 2) {
                this.reps(0) = opt.Backs2
                this.reps(1) = opt.Backs0
                this.reps(2) = opt.Backs1
                this.reps(3) = opt.Backs3
              } else {
                this.reps(0) = opt.Backs3
                this.reps(1) = opt.Backs0
                this.reps(2) = opt.Backs1
                this.reps(3) = opt.Backs2
              }
            }
          }
        } else {
          this.reps(0) = pos - com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances
          this.reps(1) = opt.Backs0
          this.reps(2) = opt.Backs1
          this.reps(3) = opt.Backs2
        }
      }
      this._optimum(cur).State = state
      this._optimum(cur).Backs0 = this.reps(0)
      this._optimum(cur).Backs1 = this.reps(1)
      this._optimum(cur).Backs2 = this.reps(2)
      this._optimum(cur).Backs3 = this.reps(3)
      val curPrice: scala.Int = this._optimum(cur).Price
      currentByte = this._matchFinder.GetIndexByte(0 - 1)
      matchByte = this._matchFinder.GetIndexByte(((0 - this.reps(0)) - 1) - 1)
      posState = position & this._posStateMask
      val curAnd1Price: scala.Int = (curPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isMatch((state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState))) + this._literalEncoder.GetSubCoder(position, this._matchFinder.GetIndexByte(0 - 2)).GetPrice(!com.badlogic.gdx.utils.compression.lzma.Base.StateIsCharState(state), matchByte, currentByte)
      val nextOptimum: Optimal = this._optimum(cur + 1)
      var nextIsChar: scala.Boolean = false
      if (curAnd1Price < nextOptimum.Price) {
        nextOptimum.Price = curAnd1Price
        nextOptimum.PosPrev = cur
        nextOptimum.MakeAsChar()
        nextIsChar = true
      } else ()
      matchPrice = curPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isMatch((state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState))
      repMatchPrice = matchPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRep(state))
      if ((matchByte == currentByte) && (!((nextOptimum.PosPrev < cur) && (nextOptimum.BackPrev == 0)))) {
        val shortRepPrice: scala.Int = repMatchPrice + this.GetRepLen1Price(state, posState)
        if (shortRepPrice <= nextOptimum.Price) {
          nextOptimum.Price = shortRepPrice
          nextOptimum.PosPrev = cur
          nextOptimum.MakeAsShortRep()
          nextIsChar = true
        } else ()
      } else ()
      var numAvailableBytesFull: scala.Int = this._matchFinder.GetNumAvailableBytes() + 1
      numAvailableBytesFull = java.lang.Math.min((Encoder.kNumOpts - 1) - cur, numAvailableBytesFull)
      numAvailableBytes = numAvailableBytesFull
      if (numAvailableBytes < 2) {
        /* continue */ ()
      } else ()
      if (numAvailableBytes > this._numFastBytes) {
        numAvailableBytes = this._numFastBytes
      } else ()
      if ((!nextIsChar) && (matchByte != currentByte)) {
        val t: scala.Int = java.lang.Math.min(numAvailableBytesFull - 1, this._numFastBytes)
        val lenTest2: scala.Int = this._matchFinder.GetMatchLen(0, this.reps(0), t)
        if (lenTest2 >= 2) {
          var state2: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(state)
          var posStateNext: scala.Int = (position + 1) & this._posStateMask
          val nextRepMatchPrice: scala.Int = (curAnd1Price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isMatch((state2 << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posStateNext))) + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRep(state2));
          {
            val offset: scala.Int = (cur + 1) + lenTest2
            while (lenEnd < offset) {
              this._optimum({ lenEnd += 1; lenEnd }).Price = Encoder.kIfinityPrice
            }
            var curAndLenPrice: scala.Int = nextRepMatchPrice + this.GetRepPrice(0, lenTest2, state2, posStateNext)
            var optimum: Optimal = this._optimum(offset)
            if (curAndLenPrice < optimum.Price) {
              optimum.Price = curAndLenPrice
              optimum.PosPrev = cur + 1
              optimum.BackPrev = 0
              optimum.Prev1IsChar = true
              optimum.Prev2 = false
            } else ()
          }
        } else ()
      } else ()
      var startLen: scala.Int = 2;
      { var repIndex: scala.Int = 0; while (repIndex < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) { {
        var lenTest: scala.Int = this._matchFinder.GetMatchLen(0 - 1, this.reps(repIndex), numAvailableBytes)
        if (lenTest < 2) {
          /* continue */ ()
        } else ()
        val lenTestTemp: scala.Int = lenTest
        while ({ {
          while (lenEnd < (cur + lenTest)) {
            this._optimum({ lenEnd += 1; lenEnd }).Price = Encoder.kIfinityPrice
          }
          var curAndLenPrice: scala.Int = repMatchPrice + this.GetRepPrice(repIndex, lenTest, state, posState)
          var optimum: Optimal = this._optimum(cur + lenTest)
          if (curAndLenPrice < optimum.Price) {
            optimum.Price = curAndLenPrice
            optimum.PosPrev = cur
            optimum.BackPrev = repIndex
            optimum.Prev1IsChar = false
          } else ()
        }; { lenTest -= 1; lenTest } >= 2 }) ()
        lenTest = lenTestTemp
        if (repIndex == 0) {
          startLen = lenTest + 1
        } else ()
        if (lenTest < numAvailableBytesFull) {
          val t: scala.Int = java.lang.Math.min((numAvailableBytesFull - 1) - lenTest, this._numFastBytes)
          val lenTest2: scala.Int = this._matchFinder.GetMatchLen(lenTest, this.reps(repIndex), t)
          if (lenTest2 >= 2) {
            var state2: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateRep(state)
            var posStateNext: scala.Int = (position + lenTest) & this._posStateMask
            val curAndLenCharPrice: scala.Int = ((repMatchPrice + this.GetRepPrice(repIndex, lenTest, state, posState)) + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isMatch((state2 << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posStateNext))) + this._literalEncoder.GetSubCoder(position + lenTest, this._matchFinder.GetIndexByte((lenTest - 1) - 1)).GetPrice(true, this._matchFinder.GetIndexByte((lenTest - 1) - (this.reps(repIndex) + 1)), this._matchFinder.GetIndexByte(lenTest - 1))
            state2 = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(state2)
            posStateNext = ((position + lenTest) + 1) & this._posStateMask
            val nextMatchPrice: scala.Int = curAndLenCharPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isMatch((state2 << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posStateNext))
            val nextRepMatchPrice: scala.Int = nextMatchPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRep(state2));
            {
              val offset: scala.Int = (lenTest + 1) + lenTest2
              while (lenEnd < (cur + offset)) {
                this._optimum({ lenEnd += 1; lenEnd }).Price = Encoder.kIfinityPrice
              }
              var curAndLenPrice: scala.Int = nextRepMatchPrice + this.GetRepPrice(0, lenTest2, state2, posStateNext)
              var optimum: Optimal = this._optimum(cur + offset)
              if (curAndLenPrice < optimum.Price) {
                optimum.Price = curAndLenPrice
                optimum.PosPrev = (cur + lenTest) + 1
                optimum.BackPrev = 0
                optimum.Prev1IsChar = true
                optimum.Prev2 = true
                optimum.PosPrev2 = cur
                optimum.BackPrev2 = repIndex
              } else ()
            }
          } else ()
        } else ()
      }; repIndex = repIndex + 1 } }
      if (newLen > numAvailableBytes) {
        newLen = numAvailableBytes;
        { numDistancePairs = 0; while (newLen > this._matchDistances(numDistancePairs)) { {
          ()
        }; numDistancePairs = numDistancePairs + 2 } }
        this._matchDistances(numDistancePairs) = newLen
        numDistancePairs = numDistancePairs + 2
      } else ()
      if (newLen >= startLen) {
        normalMatchPrice = matchPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isRep(state))
        while (lenEnd < (cur + newLen)) {
          this._optimum({ lenEnd += 1; lenEnd }).Price = Encoder.kIfinityPrice
        }
        var offs: scala.Int = 0
        while (startLen > this._matchDistances(offs)) {
          offs = offs + 2
        };
        { var lenTest: scala.Int = startLen; while (true) { {
          val curBack: scala.Int = this._matchDistances(offs + 1)
          var curAndLenPrice: scala.Int = normalMatchPrice + this.GetPosLenPrice(curBack, lenTest, posState)
          var optimum: Optimal = this._optimum(cur + lenTest)
          if (curAndLenPrice < optimum.Price) {
            optimum.Price = curAndLenPrice
            optimum.PosPrev = cur
            optimum.BackPrev = curBack + com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances
            optimum.Prev1IsChar = false
          } else ()
          if (lenTest == this._matchDistances(offs)) {
            if (lenTest < numAvailableBytesFull) {
              val t: scala.Int = java.lang.Math.min((numAvailableBytesFull - 1) - lenTest, this._numFastBytes)
              val lenTest2: scala.Int = this._matchFinder.GetMatchLen(lenTest, curBack, t)
              if (lenTest2 >= 2) {
                var state2: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateMatch(state)
                var posStateNext: scala.Int = (position + lenTest) & this._posStateMask
                val curAndLenCharPrice: scala.Int = (curAndLenPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._isMatch((state2 << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posStateNext))) + this._literalEncoder.GetSubCoder(position + lenTest, this._matchFinder.GetIndexByte((lenTest - 1) - 1)).GetPrice(true, this._matchFinder.GetIndexByte((lenTest - (curBack + 1)) - 1), this._matchFinder.GetIndexByte(lenTest - 1))
                state2 = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(state2)
                posStateNext = ((position + lenTest) + 1) & this._posStateMask
                val nextMatchPrice: scala.Int = curAndLenCharPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isMatch((state2 << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posStateNext))
                val nextRepMatchPrice: scala.Int = nextMatchPrice + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._isRep(state2))
                val offset: scala.Int = (lenTest + 1) + lenTest2
                while (lenEnd < (cur + offset)) {
                  this._optimum({ lenEnd += 1; lenEnd }).Price = Encoder.kIfinityPrice
                }
                curAndLenPrice = nextRepMatchPrice + this.GetRepPrice(0, lenTest2, state2, posStateNext)
                optimum = this._optimum(cur + offset)
                if (curAndLenPrice < optimum.Price) {
                  optimum.Price = curAndLenPrice
                  optimum.PosPrev = (cur + lenTest) + 1
                  optimum.BackPrev = 0
                  optimum.Prev1IsChar = true
                  optimum.Prev2 = true
                  optimum.PosPrev2 = cur
                  optimum.BackPrev2 = curBack + com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances
                } else ()
              } else ()
            } else ()
            offs = offs + 2
            if (offs == numDistancePairs) {
              /* break */ ()
            } else ()
          } else ()
        }; lenTest = lenTest + 1 } }
      } else ()
    }
  }
  def ChangePair(smallDist: scala.Int, bigDist: scala.Int): scala.Boolean = {
    val kDif: scala.Int = 7
    return (smallDist < (1 << (32 - kDif))) && (bigDist >= (smallDist << kDif))
  }
  def WriteEndMarker(posState: scala.Int): scala.Unit = {
    if (!this._writeEndMark) {
      return
    } else ()
    this._rangeEncoder.Encode(this._isMatch, (this._state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState, 1)
    this._rangeEncoder.Encode(this._isRep, this._state, 0)
    this._state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateMatch(this._state)
    val len: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen
    this._lenEncoder.Encode(this._rangeEncoder, len - com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen, posState)
    val posSlot: scala.Int = (1 << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosSlotBits) - 1
    val lenToPosState: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.GetLenToPosState(len)
    this._posSlotEncoder(lenToPosState).Encode(this._rangeEncoder, posSlot)
    val footerBits: scala.Int = 30
    val posReduced: scala.Int = (1 << footerBits) - 1
    this._rangeEncoder.EncodeDirectBits(posReduced >> com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits, footerBits - com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits)
    this._posAlignEncoder.ReverseEncode(this._rangeEncoder, posReduced & com.badlogic.gdx.utils.compression.lzma.Base.kAlignMask)
  }
  def Flush(nowPos: scala.Int): scala.Unit = {
    this.ReleaseMFStream()
    this.WriteEndMarker(nowPos & this._posStateMask)
    this._rangeEncoder.FlushData()
    this._rangeEncoder.FlushStream()
  }
  def CodeOneBlock(inSize: scala.Array[scala.Long], outSize: scala.Array[scala.Long], finished: scala.Array[scala.Boolean]): scala.Unit = {
    inSize(0) = 0
    outSize(0) = 0
    finished(0) = true
    if (this._inStream != null) {
      this._matchFinder.SetStream(this._inStream)
      this._matchFinder.Init()
      this._needReleaseMFStream = true
      this._inStream = null
    } else ()
    if (this._finished) {
      return
    } else ()
    this._finished = true
    val progressPosValuePrev: scala.Long = this.nowPos64
    if (this.nowPos64 == 0) {
      if (this._matchFinder.GetNumAvailableBytes() == 0) {
        this.Flush(this.nowPos64.asInstanceOf[scala.Int])
        return
      } else ()
      this.ReadMatchDistances()
      val posState: scala.Int = this.nowPos64.asInstanceOf[scala.Int] & this._posStateMask
      this._rangeEncoder.Encode(this._isMatch, (this._state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState, 0)
      this._state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(this._state)
      val curByte: scala.Byte = this._matchFinder.GetIndexByte(0 - this._additionalOffset)
      this._literalEncoder.GetSubCoder(this.nowPos64.asInstanceOf[scala.Int], this._previousByte).Encode(this._rangeEncoder, curByte)
      this._previousByte = curByte
      this._additionalOffset = this._additionalOffset - 1
      this.nowPos64 = this.nowPos64 + 1
    } else ()
    if (this._matchFinder.GetNumAvailableBytes() == 0) {
      this.Flush(this.nowPos64.asInstanceOf[scala.Int])
      return
    } else ()
    while (true) {
      val len: scala.Int = this.GetOptimum(this.nowPos64.asInstanceOf[scala.Int])
      var pos: scala.Int = this.backRes
      val posState: scala.Int = this.nowPos64.asInstanceOf[scala.Int] & this._posStateMask
      val complexState: scala.Int = (this._state << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsMax) + posState
      if ((len == 1) && (pos == (-1))) {
        this._rangeEncoder.Encode(this._isMatch, complexState, 0)
        val curByte: scala.Byte = this._matchFinder.GetIndexByte((0 - this._additionalOffset).asInstanceOf[scala.Int])
        val subCoder: Encoder2 = this._literalEncoder.GetSubCoder(this.nowPos64.asInstanceOf[scala.Int], this._previousByte)
        if (!com.badlogic.gdx.utils.compression.lzma.Base.StateIsCharState(this._state)) {
          val matchByte: scala.Byte = this._matchFinder.GetIndexByte((((0 - this._repDistances(0)) - 1) - this._additionalOffset).asInstanceOf[scala.Int])
          subCoder.EncodeMatched(this._rangeEncoder, matchByte, curByte)
        } else {
          subCoder.Encode(this._rangeEncoder, curByte)
        }
        this._previousByte = curByte
        this._state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateChar(this._state)
      } else {
        this._rangeEncoder.Encode(this._isMatch, complexState, 1)
        if (pos < com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances) {
          this._rangeEncoder.Encode(this._isRep, this._state, 1)
          if (pos == 0) {
            this._rangeEncoder.Encode(this._isRepG0, this._state, 0)
            if (len == 1) {
              this._rangeEncoder.Encode(this._isRep0Long, complexState, 0)
            } else {
              this._rangeEncoder.Encode(this._isRep0Long, complexState, 1)
            }
          } else {
            this._rangeEncoder.Encode(this._isRepG0, this._state, 1)
            if (pos == 1) {
              this._rangeEncoder.Encode(this._isRepG1, this._state, 0)
            } else {
              this._rangeEncoder.Encode(this._isRepG1, this._state, 1)
              this._rangeEncoder.Encode(this._isRepG2, this._state, pos - 2)
            }
          }
          if (len == 1) {
            this._state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateShortRep(this._state)
          } else {
            this._repMatchLenEncoder.Encode(this._rangeEncoder, len - com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen, posState)
            this._state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateRep(this._state)
          }
          val distance: scala.Int = this._repDistances(pos)
          if (pos != 0) {
            { var i: scala.Int = pos; while (i >= 1) { {
              this._repDistances(i) = this._repDistances(i - 1)
            }; i = i - 1 } }
            this._repDistances(0) = distance
          } else ()
        } else {
          this._rangeEncoder.Encode(this._isRep, this._state, 0)
          this._state = com.badlogic.gdx.utils.compression.lzma.Base.StateUpdateMatch(this._state)
          this._lenEncoder.Encode(this._rangeEncoder, len - com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen, posState)
          pos = pos - com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances
          val posSlot: scala.Int = Encoder.GetPosSlot(pos)
          val lenToPosState: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.GetLenToPosState(len)
          this._posSlotEncoder(lenToPosState).Encode(this._rangeEncoder, posSlot)
          if (posSlot >= com.badlogic.gdx.utils.compression.lzma.Base.kStartPosModelIndex) {
            val footerBits: scala.Int = ((posSlot >> 1) - 1).asInstanceOf[scala.Int]
            val baseVal: scala.Int = (2 | (posSlot & 1)) << footerBits
            val posReduced: scala.Int = pos - baseVal
            if (posSlot < com.badlogic.gdx.utils.compression.lzma.Base.kEndPosModelIndex) {
              com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder.ReverseEncode(this._posEncoders, (baseVal - posSlot) - 1, this._rangeEncoder, footerBits, posReduced)
            } else {
              this._rangeEncoder.EncodeDirectBits(posReduced >> com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits, footerBits - com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits)
              this._posAlignEncoder.ReverseEncode(this._rangeEncoder, posReduced & com.badlogic.gdx.utils.compression.lzma.Base.kAlignMask)
              this._alignPriceCount = this._alignPriceCount + 1
            }
          } else ()
          val distance: scala.Int = pos;
          { var i: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.kNumRepDistances - 1; while (i >= 1) { {
            this._repDistances(i) = this._repDistances(i - 1)
          }; i = i - 1 } }
          this._repDistances(0) = distance
          this._matchPriceCount = this._matchPriceCount + 1
        }
        this._previousByte = this._matchFinder.GetIndexByte((len - 1) - this._additionalOffset)
      }
      this._additionalOffset = this._additionalOffset - len
      this.nowPos64 = this.nowPos64 + len
      if (this._additionalOffset == 0) {
        if (this._matchPriceCount >= (1 << 7)) {
          this.FillDistancesPrices()
        } else ()
        if (this._alignPriceCount >= com.badlogic.gdx.utils.compression.lzma.Base.kAlignTableSize) {
          this.FillAlignPrices()
        } else ()
        inSize(0) = this.nowPos64
        outSize(0) = this._rangeEncoder.GetProcessedSizeAdd()
        if (this._matchFinder.GetNumAvailableBytes() == 0) {
          this.Flush(this.nowPos64.asInstanceOf[scala.Int])
          return
        } else ()
        if ((this.nowPos64 - progressPosValuePrev) >= (1 << 12)) {
          this._finished = false
          finished(0) = false
          return
        } else ()
      } else ()
    }
  }
  def ReleaseMFStream(): scala.Unit = {
    if ((this._matchFinder != null) && this._needReleaseMFStream) {
      this._matchFinder.ReleaseStream()
      this._needReleaseMFStream = false
    } else ()
  }
  def SetOutStream(outStream: java.io.OutputStream): scala.Unit = {
    this._rangeEncoder.SetStream(outStream)
  }
  def ReleaseOutStream(): scala.Unit = {
    this._rangeEncoder.ReleaseStream()
  }
  def ReleaseStreams(): scala.Unit = {
    this.ReleaseMFStream()
    this.ReleaseOutStream()
  }
  def SetStreams(inStream: java.io.InputStream, outStream: java.io.OutputStream, inSize: scala.Long, outSize: scala.Long): scala.Unit = {
    this._inStream = inStream
    this._finished = false
    this.Create()
    this.SetOutStream(outStream)
    this.Init();
    {
      this.FillDistancesPrices()
      this.FillAlignPrices()
    }
    this._lenEncoder.SetTableSize((this._numFastBytes + 1) - com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen)
    this._lenEncoder.UpdateTables(1 << this._posStateBits)
    this._repMatchLenEncoder.SetTableSize((this._numFastBytes + 1) - com.badlogic.gdx.utils.compression.lzma.Base.kMatchMinLen)
    this._repMatchLenEncoder.UpdateTables(1 << this._posStateBits)
    this.nowPos64 = 0
  }
  def Code(inStream: java.io.InputStream, outStream: java.io.OutputStream, inSize: scala.Long, outSize: scala.Long, progress: com.badlogic.gdx.utils.compression.ICodeProgress): scala.Unit = {
    this._needReleaseMFStream = false
    try {
      this.SetStreams(inStream, outStream, inSize, outSize)
      while (true) {
        this.CodeOneBlock(this.processedInSize, this.processedOutSize, this.finished)
        if (this.finished(0)) {
          return
        } else ()
        if (progress != null) {
          progress.SetProgress(this.processedInSize(0), this.processedOutSize(0))
        } else ()
      }
    } finally {
      this.ReleaseStreams()
    }
  }
  def WriteCoderProperties(outStream: java.io.OutputStream): scala.Unit = {
    this.properties(0) = ((((this._posStateBits * 5) + this._numLiteralPosStateBits) * 9) + this._numLiteralContextBits).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte];
    { var i: scala.Int = 0; while (i < 4) { {
      this.properties(1 + i) = (this._dictionarySize >> (8 * i)).asInstanceOf[scala.Byte].asInstanceOf[scala.Byte]
    }; i = i + 1 } }
    outStream.write(this.properties, 0, Encoder.kPropSize)
  }
  def FillDistancesPrices(): scala.Unit = {
    { var i: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.kStartPosModelIndex; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances) { {
      var posSlot: scala.Int = Encoder.GetPosSlot(i)
      val footerBits: scala.Int = ((posSlot >> 1) - 1).asInstanceOf[scala.Int]
      val baseVal: scala.Int = (2 | (posSlot & 1)) << footerBits
      this.tempPrices(i) = com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder.ReverseGetPrice(this._posEncoders, (baseVal - posSlot) - 1, footerBits, i - baseVal)
    }; i = i + 1 } };
    { var lenToPosState: scala.Int = 0; while (lenToPosState < com.badlogic.gdx.utils.compression.lzma.Base.kNumLenToPosStates) { {
      var posSlot: scala.Int = 0
      val encoder: com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder = this._posSlotEncoder(lenToPosState)
      val st: scala.Int = lenToPosState << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosSlotBits;
      { posSlot = 0; while (posSlot < this._distTableSize) { {
        this._posSlotPrices(st + posSlot) = encoder.GetPrice(posSlot)
      }; posSlot = posSlot + 1 } };
      { posSlot = com.badlogic.gdx.utils.compression.lzma.Base.kEndPosModelIndex; while (posSlot < this._distTableSize) { {
        this._posSlotPrices(st + posSlot) = this._posSlotPrices(st + posSlot) + ((((posSlot >> 1) - 1) - com.badlogic.gdx.utils.compression.lzma.Base.kNumAlignBits) << com.badlogic.gdx.utils.compression.rangecoder.Encoder.kNumBitPriceShiftBits)
      }; posSlot = posSlot + 1 } }
      val st2: scala.Int = lenToPosState * com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances
      var i: scala.Int = 0;
      { i = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kStartPosModelIndex) { {
        this._distancesPrices(st2 + i) = this._posSlotPrices(st + i)
      }; i = i + 1 } };
      { ; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumFullDistances) { {
        this._distancesPrices(st2 + i) = this._posSlotPrices(st + Encoder.GetPosSlot(i)) + this.tempPrices(i)
      }; i = i + 1 } }
    }; lenToPosState = lenToPosState + 1 } }
    this._matchPriceCount = 0
  }
  def FillAlignPrices(): scala.Unit = {
    { var i: scala.Int = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kAlignTableSize) { {
      this._alignPrices(i) = this._posAlignEncoder.ReverseGetPrice(i)
    }; i = i + 1 } }
    this._alignPriceCount = 0
  }
  def SetAlgorithm(algorithm: scala.Int): scala.Boolean = {
    return true
  }
  def SetDictionarySize(dictionarySize: scala.Int): scala.Boolean = {
    val kDicLogSizeMaxCompress: scala.Int = 29
    if ((dictionarySize < (1 << com.badlogic.gdx.utils.compression.lzma.Base.kDicLogSizeMin)) || (dictionarySize > (1 << kDicLogSizeMaxCompress))) {
      return false
    } else ()
    this._dictionarySize = dictionarySize
    var dicLogSize: scala.Int = 0;
    { dicLogSize = 0; while (dictionarySize > (1 << dicLogSize)) { {
      ()
    }; dicLogSize = dicLogSize + 1 } }
    this._distTableSize = dicLogSize * 2
    return true
  }
  def SetNumFastBytes(numFastBytes: scala.Int): scala.Boolean = {
    if ((numFastBytes < 5) || (numFastBytes > com.badlogic.gdx.utils.compression.lzma.Base.kMatchMaxLen)) {
      return false
    } else ()
    this._numFastBytes = numFastBytes
    return true
  }
  def SetMatchFinder(matchFinderIndex: scala.Int): scala.Boolean = {
    if ((matchFinderIndex < 0) || (matchFinderIndex > 2)) {
      return false
    } else ()
    val matchFinderIndexPrev: scala.Int = this._matchFinderType
    this._matchFinderType = matchFinderIndex
    if ((this._matchFinder != null) && (matchFinderIndexPrev != this._matchFinderType)) {
      this._dictionarySizePrev = -1
      this._matchFinder = null
    } else ()
    return true
  }
  def SetLcLpPb(lc: scala.Int, lp: scala.Int, pb: scala.Int): scala.Boolean = {
    if ((((((lp < 0) || (lp > com.badlogic.gdx.utils.compression.lzma.Base.kNumLitPosStatesBitsEncodingMax)) || (lc < 0)) || (lc > com.badlogic.gdx.utils.compression.lzma.Base.kNumLitContextBitsMax)) || (pb < 0)) || (pb > com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsEncodingMax)) {
      return false
    } else ()
    this._numLiteralPosStateBits = lp
    this._numLiteralContextBits = lc
    this._posStateBits = pb
    this._posStateMask = (1 << this._posStateBits) - 1
    return true
  }
  def SetEndMarkerMode(endMarkerMode: scala.Boolean): scala.Unit = {
    this._writeEndMark = endMarkerMode
  }
  class LiteralEncoder {
    var m_Coders: scala.Array[Encoder2] = null.asInstanceOf[scala.Array[Encoder2]]
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
      this.m_Coders = new Array[Encoder2](numStates);
      { var i: scala.Int = 0; while (i < numStates) { {
        this.m_Coders(i) = new Encoder2()
      }; i = i + 1 } }
    }
    def Init(): scala.Unit = {
      val numStates: scala.Int = 1 << (this.m_NumPrevBits + this.m_NumPosBits);
      { var i: scala.Int = 0; while (i < numStates) { {
        this.m_Coders(i).Init()
      }; i = i + 1 } }
    }
    def GetSubCoder(pos: scala.Int, prevByte: scala.Byte): Encoder2 = {
      return this.m_Coders(((pos & this.m_PosMask) << this.m_NumPrevBits) + ((prevByte & 255) >>> (8 - this.m_NumPrevBits)))
    }
    class Encoder2 {
      var m_Encoders: scala.Array[scala.Short] = new Array[scala.Short](768)
      def Init(): scala.Unit = {
        com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this.m_Encoders)
      }
      def Encode(rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder, symbol: scala.Byte): scala.Unit = {
        var context: scala.Int = 1;
        { var i: scala.Int = 7; while (i >= 0) { {
          val bit: scala.Int = (symbol >> i) & 1
          rangeEncoder.Encode(this.m_Encoders, context, bit)
          context = (context << 1) | bit
        }; i = i - 1 } }
      }
      def EncodeMatched(rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder, matchByte: scala.Byte, symbol: scala.Byte): scala.Unit = {
        var context: scala.Int = 1
        var same: scala.Boolean = true;
        { var i: scala.Int = 7; while (i >= 0) { {
          val bit: scala.Int = (symbol >> i) & 1
          var state: scala.Int = context
          if (same) {
            val matchBit: scala.Int = (matchByte >> i) & 1
            state = state + ((1 + matchBit) << 8)
            same = matchBit == bit
          } else ()
          rangeEncoder.Encode(this.m_Encoders, state, bit)
          context = (context << 1) | bit
        }; i = i - 1 } }
      }
      def GetPrice(matchMode: scala.Boolean, matchByte: scala.Byte, symbol: scala.Byte): scala.Int = {
        var price: scala.Int = 0
        var context: scala.Int = 1
        var i: scala.Int = 7
        if (matchMode) {
          { ; while (i >= 0) { {
            val matchBit: scala.Int = (matchByte >> i) & 1
            val bit: scala.Int = (symbol >> i) & 1
            price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice(this.m_Encoders(((1 + matchBit) << 8) + context), bit)
            context = (context << 1) | bit
            if (matchBit != bit) {
              i = i - 1
              /* break */ ()
            } else ()
          }; i = i - 1 } }
        } else ();
        { ; while (i >= 0) { {
          val bit: scala.Int = (symbol >> i) & 1
          price = price + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice(this.m_Encoders(context), bit)
          context = (context << 1) | bit
        }; i = i - 1 } }
        return price
      }
    }
  }
  class LenEncoder {
    var _choice: scala.Array[scala.Short] = new Array[scala.Short](2)
    var _lowCoder: scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder] = new Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder](com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesEncodingMax)
    var _midCoder: scala.Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder] = new Array[com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder](com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesEncodingMax)
    var _highCoder: com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumHighLenBits);
    { var posState: scala.Int = 0; while (posState < com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesEncodingMax) { {
      this._lowCoder(posState) = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenBits)
      this._midCoder(posState) = new com.badlogic.gdx.utils.compression.rangecoder.BitTreeEncoder(com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenBits)
    }; posState = posState + 1 } }
    def Init(numPosStates: scala.Int): scala.Unit = {
      com.badlogic.gdx.utils.compression.rangecoder.Encoder.InitBitModels(this._choice);
      { var posState: scala.Int = 0; while (posState < numPosStates) { {
        this._lowCoder(posState).Init()
        this._midCoder(posState).Init()
      }; posState = posState + 1 } }
      this._highCoder.Init()
    }
    def Encode(rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder, symbol$arg: scala.Int, posState: scala.Int): scala.Unit = {
      var symbol: scala.Int = symbol$arg
      if (symbol < com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols) {
        rangeEncoder.Encode(this._choice, 0, 0)
        this._lowCoder(posState).Encode(rangeEncoder, symbol)
      } else {
        symbol = symbol - com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols
        rangeEncoder.Encode(this._choice, 0, 1)
        if (symbol < com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenSymbols) {
          rangeEncoder.Encode(this._choice, 1, 0)
          this._midCoder(posState).Encode(rangeEncoder, symbol)
        } else {
          rangeEncoder.Encode(this._choice, 1, 1)
          this._highCoder.Encode(rangeEncoder, symbol - com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenSymbols)
        }
      }
    }
    def SetPrices(posState: scala.Int, numSymbols: scala.Int, prices: scala.Array[scala.Int], st: scala.Int): scala.Unit = {
      val a0: scala.Int = com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._choice(0))
      val a1: scala.Int = com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._choice(0))
      val b0: scala.Int = a1 + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice0(this._choice(1))
      val b1: scala.Int = a1 + com.badlogic.gdx.utils.compression.rangecoder.Encoder.GetPrice1(this._choice(1))
      var i: scala.Int = 0;
      { i = 0; while (i < com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols) { {
        if (i >= numSymbols) {
          return
        } else ()
        prices(st + i) = a0 + this._lowCoder(posState).GetPrice(i)
      }; i = i + 1 } };
      { ; while (i < (com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols + com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenSymbols)) { {
        if (i >= numSymbols) {
          return
        } else ()
        prices(st + i) = b0 + this._midCoder(posState).GetPrice(i - com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols)
      }; i = i + 1 } };
      { ; while (i < numSymbols) { {
        prices(st + i) = b1 + this._highCoder.GetPrice((i - com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols) - com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenSymbols)
      }; i = i + 1 } }
    }
  }
  class LenPriceTableEncoder extends LenEncoder {
    var _prices: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kNumLenSymbols << com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesBitsEncodingMax)
    var _tableSize: scala.Int = 0
    var _counters: scala.Array[scala.Int] = new Array[scala.Int](com.badlogic.gdx.utils.compression.lzma.Base.kNumPosStatesEncodingMax)
    def SetTableSize(tableSize: scala.Int): scala.Unit = {
      this._tableSize = tableSize
    }
    def GetPrice(symbol: scala.Int, posState: scala.Int): scala.Int = {
      return this._prices((posState * com.badlogic.gdx.utils.compression.lzma.Base.kNumLenSymbols) + symbol)
    }
    def UpdateTable(posState: scala.Int): scala.Unit = {
      this.SetPrices(posState, this._tableSize, this._prices, posState * com.badlogic.gdx.utils.compression.lzma.Base.kNumLenSymbols)
      this._counters(posState) = this._tableSize
    }
    def UpdateTables(numPosStates: scala.Int): scala.Unit = {
      { var posState: scala.Int = 0; while (posState < numPosStates) { {
        this.UpdateTable(posState)
      }; posState = posState + 1 } }
    }
    def Encode(rangeEncoder: com.badlogic.gdx.utils.compression.rangecoder.Encoder, symbol: scala.Int, posState: scala.Int): scala.Unit = {
      super.Encode(rangeEncoder, symbol, posState)
      if ({ this._counters(posState) -= 1; this._counters(posState) } == 0) {
        this.UpdateTable(posState)
      } else ()
    }
  }
  class Optimal {
    var State: scala.Int = 0
    var Prev1IsChar: scala.Boolean = false
    var Prev2: scala.Boolean = false
    var PosPrev2: scala.Int = 0
    var BackPrev2: scala.Int = 0
    var Price: scala.Int = 0
    var PosPrev: scala.Int = 0
    var BackPrev: scala.Int = 0
    var Backs0: scala.Int = 0
    var Backs1: scala.Int = 0
    var Backs2: scala.Int = 0
    var Backs3: scala.Int = 0
    def MakeAsChar(): scala.Unit = {
      this.BackPrev = -1
      this.Prev1IsChar = false
    }
    def MakeAsShortRep(): scala.Unit = {
      this.BackPrev = 0
      this.Prev1IsChar = false
    }
    def IsShortRep(): scala.Boolean = {
      return this.BackPrev == 0
    }
  }
}
object Encoder {
  final val EMatchFinderTypeBT2: scala.Int = 0
  final val EMatchFinderTypeBT4: scala.Int = 1
  final val kIfinityPrice: scala.Int = 268435455
  var g_FastPos: scala.Array[scala.Byte] = new Array[scala.Byte](1 << 11)
  final val kDefaultDictionaryLogSize: scala.Int = 22
  final val kNumFastBytesDefault: scala.Int = 32
  final val kNumLenSpecSymbols: scala.Int = com.badlogic.gdx.utils.compression.lzma.Base.kNumLowLenSymbols + com.badlogic.gdx.utils.compression.lzma.Base.kNumMidLenSymbols
  final val kNumOpts: scala.Int = 1 << 12
  final val kPropSize: scala.Int = 5
  def GetPosSlot(pos: scala.Int): scala.Int = {
    if (pos < (1 << 11)) {
      return Encoder.g_FastPos(pos)
    } else ()
    if (pos < (1 << 21)) {
      return Encoder.g_FastPos(pos >> 10) + 20
    } else ()
    return Encoder.g_FastPos(pos >> 20) + 40
  }
  def GetPosSlot2(pos: scala.Int): scala.Int = {
    if (pos < (1 << 17)) {
      return Encoder.g_FastPos(pos >> 6) + 12
    } else ()
    if (pos < (1 << 27)) {
      return Encoder.g_FastPos(pos >> 16) + 32
    } else ()
    return Encoder.g_FastPos(pos >> 26) + 52
  }
}