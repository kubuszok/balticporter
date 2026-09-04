package balticporter.core

import java.nio.file.Path

/** A construct the current engine version cannot translate faithfully. Always fatal:
  * there is no best-effort emission (anti-omission stance, DESIGN.md §3.4).
  */
final case class Unsupported(sourcePath: String, position: String, what: String)
    extends RuntimeException(s"$sourcePath:$position — unsupported construct: $what")

final case class FrontendConfig(
    /** root of the upstream Java sources (package dirs below it). */
    sourceRoot: Path,
    /** files to CONVERT, relative to sourceRoot. Order defines unit order. */
    files: List[String],
    /** dependency classpath for full resolution. */
    classpath: List[Path],
    /** additional source roots that participate in RESOLUTION but are not converted
      * (typically the whole vendored tree — source-over-jar avoids version skew). */
    resolutionRoots: List[Path] = Nil,
    /** paths under a resolution root that must NOT BE PARSED — relative to the root, matched at a
      * path SEPARATOR (§4.56), never a substring. Empty is the default and the no-op. Answers GWT
      * super-source trees that REDECLARE classes (Spoon otherwise refuses the whole model). Cannot
      * be worked around by pointing the root lower — breaks base-map joins (measured: 25 findings
      * vs 0). §1(b): mechanism universal, paths per-library. */
    resolutionExcludes: List[String] = Nil,
    /** WHICH ARGUMENT-BEARING ANNOTATION FAMILIES THIS PORT CLAIMS ON A TYPE — FQN prefixes,
      * §1(b), EMPTY is the default/no-op. A MARKER annotation needs nothing translated and is
      * always carried; one WITH ARGUMENTS needs its values translated, so this decides only
      * whether a family is WANTED — per-library (`ENGINE-LIMITS.md` T16). TYPE-level only: method/
      * parameter annotations already translate; see [[AnnotationPolicy]] for the matching rule. */
    preservedAnnotations: AnnotationPolicy = AnnotationPolicy.none,
)

/** WHICH annotation families a port claims, and the one question anything asks of it. A VALUE
  * rather than a `List[String]`: the match cuts only at a `Symbol.fullName` separator (CLAUDE.md
  * §4.56), so `com.foo` covers `com.foo.Bar`/`com.foo.Bar$Baz` but not `com.foobar.Bar`, written
  * once so two spellings (trailing dot or not) never quietly differ. `none` is the default no-op. */
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
  /** the empty parameter §1(b) requires: no family claimed, every argument-bearing annotation on a
    * TYPE reported through `omissions` exactly as it was before the policy existed. */
  val none: AnnotationPolicy = AnnotationPolicy()

trait Frontend:
  /** Parse + resolve, returning units in the order of `cfg.files`. */
  def parse(cfg: FrontendConfig): List[BUnit]
