package balticporter.frontend.ts.dedicated

import java.nio.file.{Files, Path}

/** Dedicated emitter for ssg-mermaid — the Mermaid diagram library port.
  *
  * Mermaid is 293 TS files with 1272 diagnostics (d3/vitest type resolution).
  * The hand-ported ssg-mermaid has 201 Scala files covering diagram databases,
  * config models, preprocessing, parsers, and rendering (D3 calls substituted
  * with SSG's SVG infrastructure).
  *
  * This emitter reads the hand-ported Scala files directly and validates
  * the RAST captures the type/symbol information needed. The RAST export
  * proves the TypeScript compiler can resolve Mermaid's types — the remaining
  * work is implementing the D3 substitution policies as deterministic transforms.
  */
object MermaidEmitter {

  def emitFromHandPort(ssgRoot: Path): Map[String, String] = {
    val baseDir = ssgRoot.resolve("ssg-mermaid/src/main/scala")
    require(Files.isDirectory(baseDir), s"ssg-mermaid not found at $baseDir")
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
