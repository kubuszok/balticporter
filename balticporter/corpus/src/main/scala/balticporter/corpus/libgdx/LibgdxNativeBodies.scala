package balticporter.corpus.libgdx

/** The bodies of libGDX core's 59 java `native` members on the JVM (PROGRESS.md §13.30 step 2):
  * each delegates to a port-written object injected with the `natives` step (`Gdx2DNative`,
  * `BufferUtilsNative`, `ETC1Native`); `Matrix4`'s three strided loops are written out over the
  * class's own single-vector statics. Keyed in `MethodBodyTransform`'s `owner#name(params)` grammar.
  */
object LibgdxNativeBodies {
  private val BU = "com.badlogic.gdx.utils.BufferUtils#"
  private val bu = "sge.utils.BufferUtilsNative."
  val bufferUtils: Map[String, String] = Map(
    s"${BU}freeMemory(ByteBuffer)"           -> s"${bu}freeMemory(buffer)",
    s"${BU}newDisposableByteBuffer(int)"     -> s"${bu}newDisposableByteBuffer(numBytes)",
    s"${BU}getBufferAddress(Buffer)"         -> s"${bu}getBufferAddress(buffer)",
    s"${BU}clear(ByteBuffer,int)"            -> s"${bu}clear(buffer, numBytes)",
    s"${BU}copyJni(float[],Buffer,int,int)"  -> s"${bu}copyJni(src, dst, numFloats, offset)",
  ) ++ List("byte", "char", "short", "int", "long", "float", "double", "Buffer").map(t =>
    s"${BU}copyJni(${if t == "Buffer" then t else t + "[]"},int,Buffer,int,int)" -> s"${bu}copyJni(src, srcOffset, dst, dstOffset, numBytes)")
    ++ (for k <- List("V4M4", "V3M4", "V2M4", "V3M3", "V2M3"); d <- List("Buffer", "float[]") yield
      s"${BU}transform${k}Jni($d,int,int,float[],int)" -> s"${bu}transform${k}Jni(data, strideInBytes, count, matrix, offsetInBytes)")
    ++ (for a <- List("Buffer", "float[]"); b <- List("Buffer", "float[]"); eps <- List(false, true) yield
      s"${BU}find($a,int,int,$b,int,int${if eps then ",float" else ""})" ->
        s"${bu}find(vertex, vertexOffsetInBytes, strideInBytes, vertices, verticesOffsetInBytes, numVertices${if eps then ", epsilon" else ""})")

  private val PX = "com.badlogic.gdx.graphics.g2d.Gdx2DPixmap#"
  private val px = "sge.graphics.g2d.Gdx2DNative."
  val gdx2d: Map[String, String] = Map(
    s"${PX}load(long[],byte[],int,int)"                 -> s"${px}load(nativeData, buffer, offset, len)",
    s"${PX}loadByteBuffer(long[],ByteBuffer,int,int)"   -> s"${px}loadByteBuffer(nativeData, buffer, offset, len)",
    s"${PX}newPixmap(long[],int,int,int)"               -> s"${px}newPixmap(nativeData, width, height, format)",
    s"${PX}free(long)"                                  -> s"${px}free(pixmap)",
    s"${PX}clear(long,int)"                             -> s"${px}clear(pixmap, color)",
    s"${PX}setPixel(long,int,int,int)"                  -> s"${px}setPixel(pixmap, x, y, color)",
    s"${PX}getPixel(long,int,int)"                      -> s"${px}getPixel(pixmap, x, y)",
    s"${PX}drawLine(long,int,int,int,int,int)"          -> s"${px}drawLine(pixmap, x, y, x2, y2, color)",
    s"${PX}drawRect(long,int,int,int,int,int)"          -> s"${px}drawRect(pixmap, x, y, width, height, color)",
    s"${PX}drawCircle(long,int,int,int,int)"            -> s"${px}drawCircle(pixmap, x, y, radius, color)",
    s"${PX}fillRect(long,int,int,int,int,int)"          -> s"${px}fillRect(pixmap, x, y, width, height, color)",
    s"${PX}fillCircle(long,int,int,int,int)"            -> s"${px}fillCircle(pixmap, x, y, radius, color)",
    s"${PX}fillTriangle(long,int,int,int,int,int,int,int)" -> s"${px}fillTriangle(pixmap, x1, y1, x2, y2, x3, y3, color)",
    s"${PX}drawPixmap(long,long,int,int,int,int,int,int,int,int)" -> s"${px}drawPixmap(src, dst, srcX, srcY, srcWidth, srcHeight, dstX, dstY, dstWidth, dstHeight)",
    s"${PX}setBlend(long,int)"                          -> s"${px}setBlend(src, blend)",
    s"${PX}setScale(long,int)"                          -> s"${px}setScale(src, scale)",
    s"${PX}getFailureReason()"                          -> s"${px}getFailureReason()",
  )

  private val ET = "com.badlogic.gdx.graphics.glutils.ETC1#"
  private val et = "sge.graphics.glutils.ETC1Native."
  val etc1: Map[String, String] = Map(
    s"${ET}getCompressedDataSize(int,int)"                       -> s"${et}getCompressedDataSize(width, height)",
    s"${ET}formatHeader(ByteBuffer,int,int,int)"                 -> s"${et}formatHeader(header, offset, width, height)",
    s"${ET}getWidthPKM(ByteBuffer,int)"                          -> s"${et}getWidthPKM(header, offset)",
    s"${ET}getHeightPKM(ByteBuffer,int)"                         -> s"${et}getHeightPKM(header, offset)",
    s"${ET}isValidPKM(ByteBuffer,int)"                           -> s"${et}isValidPKM(header, offset)",
    s"${ET}decodeImage(ByteBuffer,int,ByteBuffer,int,int,int,int)" -> s"${et}decodeImage(compressedData, offset, decodedData, offsetDec, width, height, pixelSize)",
    s"${ET}encodeImage(ByteBuffer,int,int,int,int)"              -> s"${et}encodeImage(imageData, offset, width, height, pixelSize)",
    s"${ET}encodeImagePKM(ByteBuffer,int,int,int,int)"           -> s"${et}encodeImagePKM(imageData, offset, width, height, pixelSize)",
  )

  /** java's strided loop over the class's own single-vector static (`Matrix4.java` 1300–1345). */
  private def strided(single: String): String =
    s"{ var i = 0; var o = offset; while (i < numVecs) { val v = scala.Array(vecs(o), vecs(o + 1), vecs(o + 2)); " +
      s"sge.math.Matrix4.$single(mat, v); vecs(o) = v(0); vecs(o + 1) = v(1); vecs(o + 2) = v(2); o += stride; i += 1 } }"
  private val M4 = "com.badlogic.gdx.math.Matrix4#"
  val matrix4: Map[String, String] = Map(
    s"${M4}mulVec(float[],float[],int,int,int)" -> strided("mulVec"),
    s"${M4}prj(float[],float[],int,int,int)"    -> strided("prj"),
    s"${M4}rot(float[],float[],int,int,int)"    -> strided("rot"),
  )

  val all: Map[String, String] = bufferUtils ++ gdx2d ++ etc1 ++ matrix4
}
