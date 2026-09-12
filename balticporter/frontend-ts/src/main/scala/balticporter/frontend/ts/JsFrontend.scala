package balticporter.frontend.ts

import balticporter.catalog.CatalogLog
import balticporter.core.{FrontendConfig, Language, Substitutions, TirFrontend}
import balticporter.tir.Program

/** JavaScript frontend — uses the TypeScript compiler's `allowJs` mode to
  * type-check plain JavaScript sources. The exporter is the same Node.js
  * `export.ts` with `--allowJs` added to the tsconfig.
  *
  * This enables semantic analysis of JavaScript codebases like Terser that
  * are too dynamic for a pure syntax parser but have enough structure for
  * the TS checker to infer useful types. Unresolvable sites produce
  * `any` types, which the minter records as `Unportable`. */
class JsFrontend extends TirFrontend:

  def name: String = "javascript"

  def language: Language = Language.JavaScript

  def defaultInclude: List[String] = List("**/*.js", "**/*.mjs")

  def build(cfg: FrontendConfig, subs: Substitutions, catalog: CatalogLog,
            lenient: Boolean): Program =
    val rastFiles = cfg.files.map { relPath =>
      val jsonPath = cfg.sourceRoot.resolve(relPath)
      require(java.nio.file.Files.exists(jsonPath), s"RAST file not found: $jsonPath")
      Rast.readFile(jsonPath)
    }
    TsMinter.mint(rastFiles, subs, catalog)
