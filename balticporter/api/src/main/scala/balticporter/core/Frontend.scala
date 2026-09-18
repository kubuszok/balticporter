package balticporter.core

import java.nio.file.Path

/** A construct the current engine version cannot translate faithfully. Always fatal: there is no best-effort emission.
  */
final case class Unsupported(sourcePath: String, position: String, what: String) extends RuntimeException(s"$sourcePath:$position — unsupported construct: $what")

final case class FrontendConfig(
  /** root of the upstream Java sources (package dirs below it). */
  sourceRoot: Path,
  /** files to CONVERT, relative to sourceRoot. Order defines unit order. */
  files: List[String],
  /** dependency classpath for full resolution. */
  classpath: List[Path],
  /** additional source roots that participate in RESOLUTION but are not converted (typically the whole vendored tree — source-over-jar avoids version skew).
    */
  resolutionRoots: List[Path] = Nil,
  /** paths under a resolution root that must not be parsed — relative to the root, matched at a path separator, never a substring. Empty is the default and the no-op. Answers super-source trees that
    * redeclare classes (Spoon otherwise refuses the whole model); cannot be worked around by pointing the root lower, which breaks base-map joins. Mechanism is universal, the paths are per-library.
    */
  resolutionExcludes: List[String] = Nil,
  /** which argument-bearing annotation families this port claims on a type — FQN prefixes, empty is the default/no-op. A marker annotation needs nothing translated and is always carried; one with
    * arguments needs its values translated, so this decides only whether a family is wanted, per library. Type-level only: method/parameter annotations already translate; see [[AnnotationPolicy]] for
    * the matching rule.
    */
  preservedAnnotations: AnnotationPolicy = AnnotationPolicy.none,
  /** Extra type FQNs to intern from the classpath — `isFinal` and parents read from the class file so a downstream phase (e.g. `CollectionsTransform.mint`) inherits them. Mechanism is universal, the
    * FQNs are per-library; empty default is the no-op.
    */
  internTypes: Set[String] = Set.empty
)

/** Which annotation families a port claims, and the one question anything asks of it. A value rather than a `List[String]`: the match cuts only at a `Symbol.fullName` separator, so `com.foo` covers
  * `com.foo.Bar`/`com.foo.Bar$Baz` but not `com.foobar.Bar`, written once so two spellings (trailing dot or not) never quietly differ. `none` is the default no-op.
  */
final case class AnnotationPolicy(prefixes: List[String] = Nil):
  def isEmpty: Boolean = prefixes.isEmpty

  /** does this port claim the family this annotation FQN is in? */
  def claims(fqn: String): Boolean = prefixes.exists { p =>
    val q = p.stripSuffix(".").stripSuffix("$")
    fqn == q || fqn.startsWith(q + ".") || fqn.startsWith(q + "$")
  }

  /** rendered for a fingerprint or a report — sorted, so two equal policies compare equal. */
  def render: String = prefixes.sorted.mkString(",")

object AnnotationPolicy:
  /** the empty, no-op parameter: no family claimed, every argument-bearing annotation on a type reported through `omissions` exactly as it was before the policy existed.
    */
  val none: AnnotationPolicy = AnnotationPolicy()

trait Frontend:
  /** Parse + resolve, returning units in the order of `cfg.files`. */
  def parse(cfg: FrontendConfig): List[BUnit]

/** Source language — decides the catalog prefix (`DiffId`) and the default file globs. */
enum Language(val prefix: String):
  case Java extends Language("JS")
  case TypeScript extends Language("TS")
  case Dart extends Language("DT")
  case JavaScript extends Language("JX")

/** TIR-level frontend SPI — a second frontend can exist without touching the Java/Spoon path. The stable frontend boundary is a `Program`, not a parsed model. Registered through
  * `META-INF/services/balticporter.core.TirFrontend` and discovered by [[FrontendRegistry]]; `.conf` names the frontend with `input.frontend = "<name>"`.
  */
trait TirFrontend:
  def build(cfg: FrontendConfig, subs: Substitutions, catalog: balticporter.catalog.CatalogLog, lenient: Boolean): balticporter.tir.Program

  /** Stable name for `.conf` lookup — e.g. `"java-spoon"`, `"typescript"`. */
  def name:           String
  def language:       Language
  def defaultInclude: List[String]
