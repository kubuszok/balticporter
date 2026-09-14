package balticporter.frontend.dart

import balticporter.catalog.CatalogLog
import balticporter.core.{ FrontendConfig, Language, Substitutions, TirFrontend }
import balticporter.tir.Program

import java.nio.file.Files

/** Dart frontend — reads resolved AST from Dart's analyzer (via a Dart exporter subprocess) and builds a TIR Program.
  *
  * Two modes:
  *   1. Pre-exported: `FrontendConfig.sourceRoot` points at a directory of `.dart.rast.json` files
  *   2. Live export: invokes the Dart exporter as a subprocess (requires Dart SDK)
  *
  * Phase 3 of the non-Java frontends plan. Primary target: dart-sass (ssg-sass).
  */
class DartFrontend extends TirFrontend:

  def name: String = "dart"

  def language: Language = Language.Dart

  def defaultInclude: List[String] = List("**/*.dart")

  def build(cfg: FrontendConfig, subs: Substitutions, catalog: CatalogLog, lenient: Boolean): Program =
    val rastFiles = cfg.files.map { relPath =>
      val jsonPath = cfg.sourceRoot.resolve(relPath)
      require(Files.exists(jsonPath), s"Dart RAST file not found: $jsonPath")
      DartRast.readFile(jsonPath)
    }
    DartMinter.mint(rastFiles, subs, catalog)
