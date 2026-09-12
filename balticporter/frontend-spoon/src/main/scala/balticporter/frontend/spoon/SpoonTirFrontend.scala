package balticporter.frontend.spoon

import balticporter.catalog.CatalogLog
import balticporter.core.{FrontendConfig, Language, Substitutions, TirFrontend}
import balticporter.tir.Program

/** Wraps the existing `SpoonTir.buildModel`+`fromTypes` path into the `TirFrontend` SPI,
  * so `PortRun` can take a frontend as a parameter rather than hard-wiring Spoon. */
class SpoonTirFrontend extends TirFrontend:
  def build(cfg: FrontendConfig, subs: Substitutions, catalog: CatalogLog,
            lenient: Boolean): Program =
    val types = SpoonTir.buildModel(cfg, lenient = lenient)
    SpoonTir.fromTypes(types, subs, catalog, cfg.preservedAnnotations, cfg.internTypes)

  def name: String = "java-spoon"

  def language: Language = Language.Java

  def defaultInclude: List[String] = List("**.java")
