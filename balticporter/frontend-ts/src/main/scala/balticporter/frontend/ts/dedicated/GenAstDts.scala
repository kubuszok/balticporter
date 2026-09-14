package balticporter.corpus.terser

import balticporter.frontend.ts.dedicated.{DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction}

import balticporter.frontend.ts.Rast

/** Generates a Terser ast.d.ts from the DEFNODE hierarchy in an existing RAST.
  *
  * Usage: `sbt "frontend-ts/runMain balticporter.frontend.ts.dedicated.GenAstDts <rast> <out>"`
  *
  * The RAST must be an existing ast.rast.json (from a prior ts-export-terser run).
  * The output is a .d.ts file placed alongside Terser's lib/ast.js so that
  * subsequent TS exports resolve `this.x` accesses to typed fields.
  */
object GenAstDts:
  def main(args: Array[String]): Unit =
    val rastPath = args.headOption.getOrElse {
      System.err.println("Usage: GenAstDts <ast.rast.json> [output.d.ts]")
      sys.exit(1)
    }
    val outPath = args.lift(1).getOrElse("/dev/stdout")

    val json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(rastPath)))
    val rast = Rast.readFile(json)
    val hierarchy = TerserEmitter.extractHierarchy(rast)
    val dts = AstDtsGenerator.generate(hierarchy, AstDtsGenerator.commonDefmethodDecls)

    if outPath == "/dev/stdout" then
      println(dts)
    else
      java.nio.file.Files.writeString(java.nio.file.Paths.get(outPath), dts)
      println(s"[GenAstDts] Wrote ${dts.length} chars (${dts.linesIterator.size} lines) to $outPath")
