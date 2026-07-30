package balticporter.corpus.demo

import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir

import java.nio.file.{Files, Path}

/** Emit a single Java file through the TIR to Scala source, to eyeball the emission backend.
  *
  *   corpus/runMain balticporter.corpus.demo.SpoonTirEmit [path/to/File.java]
  *
  * Default: a small liqp filter. Uses noClasspath parsing (structure over full resolution).
  */
object SpoonTirEmit:

  def main(args: Array[String]): Unit =
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val file = args.headOption.map { a =>
      val p = Path.of(a); if p.isAbsolute then p else repoRoot.resolve(a).normalize
    }.getOrElse(repoRoot.resolve("../ssg/original-src/liqp/src/main/java/liqp/filters/Upcase.java").normalize)
    val code    = Files.readString(file)
    val program = SpoonTir.fromSource(code, file.getFileName.toString)
    println(s"// ---- ${file.getFileName} → Scala (${program.units.size} unit(s), ${program.symbols.all.size} symbols) ----\n")
    println(new TirEmitter(program).emit)
