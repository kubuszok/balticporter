package com.badlogic.gdx.utils.compression.lzma

object Base {
  final val kNumRepDistances: scala.Int = 4
  final val kNumStates: scala.Int = 12
  final val kNumPosSlotBits: scala.Int = 6
  final val kDicLogSizeMin: scala.Int = 0
  final val kNumLenToPosStatesBits: scala.Int = 2
  final val kNumLenToPosStates: scala.Int = 1 << Base.kNumLenToPosStatesBits
  final val kMatchMinLen: scala.Int = 2
  final val kNumAlignBits: scala.Int = 4
  final val kAlignTableSize: scala.Int = 1 << Base.kNumAlignBits
  final val kAlignMask: scala.Int = Base.kAlignTableSize - 1
  final val kStartPosModelIndex: scala.Int = 4
  final val kEndPosModelIndex: scala.Int = 14
  final val kNumPosModels: scala.Int = Base.kEndPosModelIndex - Base.kStartPosModelIndex
  final val kNumFullDistances: scala.Int = 1 << (Base.kEndPosModelIndex / 2)
  final val kNumLitPosStatesBitsEncodingMax: scala.Int = 4
  final val kNumLitContextBitsMax: scala.Int = 8
  final val kNumPosStatesBitsMax: scala.Int = 4
  final val kNumPosStatesMax: scala.Int = 1 << Base.kNumPosStatesBitsMax
  final val kNumPosStatesBitsEncodingMax: scala.Int = 4
  final val kNumPosStatesEncodingMax: scala.Int = 1 << Base.kNumPosStatesBitsEncodingMax
  final val kNumLowLenBits: scala.Int = 3
  final val kNumMidLenBits: scala.Int = 3
  final val kNumHighLenBits: scala.Int = 8
  final val kNumLowLenSymbols: scala.Int = 1 << Base.kNumLowLenBits
  final val kNumMidLenSymbols: scala.Int = 1 << Base.kNumMidLenBits
  final val kNumLenSymbols: scala.Int = (Base.kNumLowLenSymbols + Base.kNumMidLenSymbols) + (1 << Base.kNumHighLenBits)
  final val kMatchMaxLen: scala.Int = (Base.kMatchMinLen + Base.kNumLenSymbols) - 1
  final def StateInit(): scala.Int = {
    return 0
  }
  final def StateUpdateChar(index: scala.Int): scala.Int = {
    if (index < 4) {
      return 0
    } else ()
    if (index < 10) {
      return index - 3
    } else ()
    return index - 6
  }
  final def StateUpdateMatch(index: scala.Int): scala.Int = {
    return if (index < 7) 7 else 10
  }
  final def StateUpdateRep(index: scala.Int): scala.Int = {
    return if (index < 7) 8 else 11
  }
  final def StateUpdateShortRep(index: scala.Int): scala.Int = {
    return if (index < 7) 9 else 11
  }
  final def StateIsCharState(index: scala.Int): scala.Boolean = {
    return index < 7
  }
  final def GetLenToPosState(len$arg: scala.Int): scala.Int = {
    var len: scala.Int = len$arg
    len = len - Base.kMatchMinLen
    if (len < Base.kNumLenToPosStates) {
      return len
    } else ()
    return (Base.kNumLenToPosStates - 1).asInstanceOf[scala.Int]
  }
}