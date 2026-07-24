package com.badlogic.gdx.utils.compression

object Lzma {
  def compress(in: java.io.InputStream, out: java.io.OutputStream): scala.Unit = {
    val params: com.badlogic.gdx.utils.compression.Lzma.CommandLine = new com.badlogic.gdx.utils.compression.Lzma.CommandLine()
    var eos: scala.Boolean = false
    if (params.Eos) {
      eos = true
    } else ()
    val encoder: com.badlogic.gdx.utils.compression.lzma.Encoder = new com.badlogic.gdx.utils.compression.lzma.Encoder()
    if (!encoder.SetAlgorithm(params.Algorithm)) {
      throw new java.lang.RuntimeException("Incorrect compression mode")
    } else ()
    if (!encoder.SetDictionarySize(params.DictionarySize)) {
      throw new java.lang.RuntimeException("Incorrect dictionary size")
    } else ()
    if (!encoder.SetNumFastBytes(params.Fb)) {
      throw new java.lang.RuntimeException("Incorrect -fb value")
    } else ()
    if (!encoder.SetMatchFinder(params.MatchFinder)) {
      throw new java.lang.RuntimeException("Incorrect -mf value")
    } else ()
    if (!encoder.SetLcLpPb(params.Lc, params.Lp, params.Pb)) {
      throw new java.lang.RuntimeException("Incorrect -lc or -lp or -pb value")
    } else ()
    encoder.SetEndMarkerMode(eos)
    encoder.WriteCoderProperties(out)
    var fileSize: scala.Long = 0L
    if (eos) {
      fileSize = -1
    } else {
      if ({
        fileSize = in.available()
        fileSize
      } == 0) {
        fileSize = -1
      } else ()
    };
    { var i: scala.Int = 0; while (i < 8) { {
      out.write((fileSize >>> (8 * i)).asInstanceOf[scala.Int] & 255)
    }; i = i + 1 } }
    encoder.Code(in, out, -1, -1, null)
  }
  def decompress(in: java.io.InputStream, out: java.io.OutputStream): scala.Unit = {
    val propertiesSize: scala.Int = 5
    val properties: scala.Array[scala.Byte] = new Array[scala.Byte](propertiesSize)
    if (in.read(properties, 0, propertiesSize) != propertiesSize) {
      throw new java.lang.RuntimeException("input .lzma file is too short")
    } else ()
    val decoder: com.badlogic.gdx.utils.compression.lzma.Decoder = new com.badlogic.gdx.utils.compression.lzma.Decoder()
    if (!decoder.SetDecoderProperties(properties)) {
      throw new java.lang.RuntimeException("Incorrect stream properties")
    } else ()
    var outSize: scala.Long = 0;
    { var i: scala.Int = 0; while (i < 8) { {
      val v: scala.Int = in.read()
      if (v < 0) {
        throw new java.lang.RuntimeException("Can't read stream size")
      } else ()
      outSize = outSize | (v.asInstanceOf[scala.Long] << (8 * i))
    }; i = i + 1 } }
    if (!decoder.Code(in, out, outSize)) {
      throw new java.lang.RuntimeException("Error in data stream")
    } else ()
  }
  class CommandLine {
    var Command: scala.Int = -1
    var NumBenchmarkPasses: scala.Int = 10
    var DictionarySize: scala.Int = 1 << 23
    var DictionarySizeIsDefined: scala.Boolean = false
    var Lc: scala.Int = 3
    var Lp: scala.Int = 0
    var Pb: scala.Int = 2
    var Fb: scala.Int = 128
    var FbIsDefined: scala.Boolean = false
    var Eos: scala.Boolean = false
    var Algorithm: scala.Int = 2
    var MatchFinder: scala.Int = 1
    var InFile: java.lang.String = null.asInstanceOf[java.lang.String]
    var OutFile: java.lang.String = null.asInstanceOf[java.lang.String]
  }
  object CommandLine {
    final val kEncode: scala.Int = 0
    final val kDecode: scala.Int = 1
    final val kBenchmak: scala.Int = 2
  }
}