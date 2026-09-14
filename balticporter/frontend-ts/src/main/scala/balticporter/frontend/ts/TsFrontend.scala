package balticporter.frontend.ts

import balticporter.catalog.CatalogLog
import balticporter.core.{ FrontendConfig, Language, Substitutions, TirFrontend }
import balticporter.tir.Program

import java.nio.file.{ Files, Path }

/** TypeScript frontend — reads RAST v1 JSON and builds a TIR Program.
  *
  * Two modes:
  *   1. Pre-exported: `FrontendConfig.sourceRoot` points at a directory of `.rast.json` files
  *   2. Live export: invokes the Node.js exporter as a subprocess (Phase 1 follow-up)
  */
class TsFrontend extends TirFrontend:

  def name: String = "typescript"

  def language: Language = Language.TypeScript

  def defaultInclude: List[String] = List("**/*.ts")

  def build(cfg: FrontendConfig, subs: Substitutions, catalog: CatalogLog, lenient: Boolean): Program =
    val rastFiles = cfg.files.map { relPath =>
      val jsonPath = cfg.sourceRoot.resolve(relPath)
      require(Files.exists(jsonPath), s"RAST file not found: $jsonPath")
      Rast.readFile(jsonPath)
    }
    TsMinter.mint(rastFiles, subs, catalog)
