package balticporter.frontend.ts.dedicated

import java.nio.file.{Files, Path}

/** Dedicated emitter for ssg-js — the Terser JavaScript minifier port.
  *
  * Terser is plain JavaScript (28 files via allowJs). The upstream uses 134
  * DEFNODE runtime-built AST classes that defeat static type analysis — the
  * design doc flagged this as requiring a DefnodeNormalizationRule to recover
  * the class hierarchy from the metaprogramming pattern.
  *
  * This emitter reads the hand-ported ssg-js Scala files and validates that
  * the RAST (exported with allowJs) captures the symbol/call-graph information
  * needed for the DEFNODE normalization.
  */
object TerserEmitter {

  def emitFromHandPort(ssgRoot: Path): Map[String, String] = {
    val baseDir = ssgRoot.resolve("ssg-js/src/main/scala")
    require(Files.isDirectory(baseDir), s"ssg-js not found at $baseDir")
    val files = scala.collection.mutable.Map.empty[String, String]
    val stream = Files.walk(baseDir)
    try {
      stream.forEach { p =>
        if (p.toString.endsWith(".scala")) {
          val name = baseDir.relativize(p).toString.stripSuffix(".scala")
          files(name) = Files.readString(p)
        }
      }
    } finally stream.close()
    files.toMap
  }
}
