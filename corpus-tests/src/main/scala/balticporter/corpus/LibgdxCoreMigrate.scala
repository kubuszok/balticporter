package balticporter.corpus

import balticporter.core.{FrontendConfig, Substitutions}
import balticporter.emit.TirEmitter
import balticporter.frontend.spoon.SpoonTir
import balticporter.tir.{Pipeline, Program}
import balticporter.transform.{CollectionsTransform, MutableParamsTransform, PanamaFfiTransform}

import java.nio.file.{Files, Path, StandardCopyOption}
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
    // Typed substitution manifest: constructs sge dropped upstream (and their
    // ready-made Scala replacements). `SharedLibraryLoader`/`Os` were removed in
    // favour of a dedicated native-extraction library; the corpus still references
    // them, so we inject standalone Scala at those FQNs (see corpus-tests/libgdx-
    // overrides). dropTypes/dropMethods are the seams for opting an in-source type
    // or method out of mechanical translation when a replacement is supplied here.
    val overridesRoot = repoRoot.resolve("corpus-tests/libgdx-overrides")
    val subs = Substitutions(
      dropTypes = Set.empty,
      dropMethods = Set.empty,
      inject = List(overridesRoot),
    )

    val allFiles = Files.walk(base).iterator().asScala
      .filter(p => p.toString.endsWith(".java"))
      .map(p => base.relativize(p).toString)
      .filterNot(f => f.endsWith("package-info.java") || f.endsWith("module-info.java"))
      .toList.sorted
    // a dropped type's source is not translated (its replacement is injected instead)
    val files = allFiles.filterNot(f => subs.dropsType(f.stripSuffix(".java").replace('/', '.')))

    println(s"[libgdx-core] building model over ${files.size} files…")
    val types   = SpoonTir.buildModel(FrontendConfig(base, files, Nil, Nil), lenient = true)
    val raw0    = SpoonTir.fromTypes(types, subs)
    val program = if raw then raw0
                  else Pipeline.run(raw0, List(new CollectionsTransform, new MutableParamsTransform, new PanamaFfiTransform()))
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
    // inject ready-made Scala verbatim (survives the wipe-and-regenerate above):
    // new types with no Java counterpart, or whole-file replacements for dropped ones.
    var injected = 0
    subs.inject.filter(Files.exists(_)).foreach { root =>
      Files.walk(root).iterator().asScala
        .filter(p => p.toString.endsWith(".scala"))
        .foreach { src =>
          val rel = root.relativize(src).toString
          val dst = outDir.resolve(rel)
          Files.createDirectories(dst.getParent)
          Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
          injected += 1
        }
    }
    println(s"[libgdx-core] wrote $written Scala files ($injected injected) -> $outDir")
    println(s"[libgdx-core] now: sbt libgdx-core/compile")
