package balticporter.frontend.dart

import balticporter.catalog.CatalogLog
import balticporter.core.{FrontendConfig, Language, Substitutions, TirFrontend}
import balticporter.tir.Program

/** Dart frontend — reads resolved AST from Dart's analyzer (via a Dart exporter subprocess)
  * and builds a TIR Program.
  *
  * Requires: Dart SDK installed (`dart` on PATH), `package:analyzer` dependency.
  * The exporter uses Dart's official semantic analyzer to extract resolved declarations,
  * types, nullability, constructor/factory resolution, extension resolution, mixins,
  * named/optional parameters, enums, and pattern matching.
  *
  * Phase 3 of the non-Java frontends plan. Primary target: dart-sass (ssg-sass). */
class DartFrontend extends TirFrontend:

  def name: String = "dart"

  def language: Language = Language.Dart

  def defaultInclude: List[String] = List("**/*.dart")

  def build(cfg: FrontendConfig, subs: Substitutions, catalog: CatalogLog,
            lenient: Boolean): Program =
    throw new UnsupportedOperationException(
      "Dart frontend requires the Dart SDK (`dart` on PATH) and a Dart exporter. " +
      "See docs/non-java-frontends.md Phase 3 for the implementation plan.")
