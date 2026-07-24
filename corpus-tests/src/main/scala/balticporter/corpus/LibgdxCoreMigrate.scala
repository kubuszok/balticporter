package balticporter.corpus

import balticporter.core.FrontendConfig
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, Program}
import balticporter.transform.{CollectionsTransform, MutableParamsTransform}

import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** Migrate libGDX's CORE module (`gdx/src`, 605 types — the backend-agnostic, JDK-only heart
  * of libGDX) through the TIR to the `libgdx-core` sbt submodule, then compile it with
  * `sbt libgdx-core/compile`. This is the M6-scale target: a real, whole library recompiled.
  *
  *   corpus-tests/runMain balticporter.corpus.LibgdxCoreMigrate [--raw]
  *
  * `--raw` skips the transform pipeline (libGDX core uses its own collections, so the java
  * collections transform barely applies here; the port is essentially structural).
  */
object LibgdxCoreMigrate:

  def main(args: Array[String]): Unit =
    val raw      = args.contains("--raw")
    val repoRoot = Path.of(sys.props.getOrElse("balticporter.root", ".")).toAbsolutePath.normalize
    val base     = repoRoot.resolve("../sge/original-src/libgdx/gdx/src").normalize
    val files = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted

    println(s"[libgdx-core] building model over ${files.size} files…")
    val types   = SpoonTir.buildModel(FrontendConfig(base, files, Nil, Nil), lenient = true)
    val raw0    = SpoonTir.fromTypes(types)
    val program = if raw then raw0 else Pipeline.run(raw0, List(new CollectionsTransform, new MutableParamsTransform))
    println(s"[libgdx-core] TIR: ${program.units.size} units, ${program.symbols.all.size} symbols")

    val outDir = repoRoot.resolve("libgdx-core/src/main/scala")
    if Files.exists(outDir) then Files.walk(outDir).iterator().asScala.toList.reverse.foreach(Files.delete)
    Files.createDirectories(outDir)
    val emitter = new TirEmitter(program)
    var written = 0
    program.units.foreach { u =>
      val full = program.symbolOf(u.symbol).map(_.fullName).getOrElse("Unit")
      val rel  = full.replace('.', '/') + ".scala"
      val p    = outDir.resolve(rel)
      Files.createDirectories(p.getParent)
      Files.writeString(p, emitter.emitUnit(u))
      written += 1
    }
    println(s"[libgdx-core] wrote $written Scala files -> $outDir")
    println(s"[libgdx-core] now: sbt libgdx-core/compile")
