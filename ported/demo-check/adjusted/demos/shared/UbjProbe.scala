/*
 * demo-run probe (not a demo): parses asset-showcase's models with the port's JSON and UBJSON readers
 * and prints what the readers see, so a model-loading failure can be read without a window.
 */
package demos.shared

object UbjProbe {
  def main(args: Array[String]): Unit = {
    val cl = getClass.getClassLoader
    def bytes(path: String): Array[Byte] = { val in = cl.getResourceAsStream(path); try in.readAllBytes() finally in.close() }
    val g3dj = new String(bytes("models/octahedron.g3dj"), "UTF-8")
    val j = new sge.utils.JsonReader().parse(g3dj)
    println("G3DJ ok: " + j.child.fold("?")(_.name.fold("?")(identity)) + " ... size=" + j.size)
    val data = bytes("models/octahedron.g3db")
    println(s"G3DB bytes=${data.length} head=" + data.take(24).map(b => f"${b & 0xff}%02x").mkString(" ") + "  as chars: " + new String(data.take(24).map(b => if (b >= 32 && b < 127) b.toChar else '.')))
    for (old <- List(true, false)) {
      try {
        val r = new sge.utils.UBJsonReader(); r.oldFormat = old
        val u = r.parse(new java.io.DataInputStream(new java.io.ByteArrayInputStream(data)))
        println(s"G3DB oldFormat=$old ok: " + u.child.fold("?")(_.name.fold("?")(identity)) + " ... size=" + u.size)
      } catch { case t: Throwable => println(s"G3DB oldFormat=$old FAILED: " + t) }
    }
    println("DEMO-RUN-FRAMES 1")
  }
}
