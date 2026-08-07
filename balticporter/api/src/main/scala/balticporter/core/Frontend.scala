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
    /** WHICH ARGUMENT-BEARING ANNOTATION FAMILIES THIS PORT CLAIMS ON A TYPE — FQN prefixes,
      * §1(b) policy, and EMPTY IS THE DEFAULT AND THE NO-OP.
      *
      * A MARKER annotation (`@Override`, `@SafeVarargs`, `@Documented`) needs nothing translated
      * and is carried at every declaration unconditionally. One WITH ARGUMENTS needs its element
      * values translated, which is the ordinary expression path — so this decides nothing about
      * the mechanism and everything about whether a family is wanted.
      *
      * It is per-library because WHICH annotations are behaviour-bearing is a fact about a library
      * and its dependencies, never about java (`ENGINE-LIMITS.md` T16). The measured population is
      * mixed and mostly unwanted: over the whole corpus the type-level drops are 11
      * `@SuppressWarnings`, 3 `@java.lang.annotation.Target` on `@interface` declarations, 1
      * `@RunWith` on a suite a phase converts to MUnit, and ONE that decides behaviour — a
      * serializer a framework looks up on an interface. Carrying them all would emit a junit runner
      * onto a munit suite and `@Target` onto an annotation type; carrying none is what every port
      * did before this parameter existed, which is why `Nil` is the default rather than a choice.
      *
      * ==WHY THE TYPE and not every declaration==
      * Because that is the only site where the harvest CHANGED. A method's and a parameter's
      * annotations already translate (a `BodyTranslator` is in scope there), so gating them would
      * remove emitted text on ports that never asked; and a FIELD's would reduce `omissions`
      * without emitting anything at all, since `TirEmitter.annots` renders a class's and a method's
      * annotations and neither a field's nor a parameter's. A residue count that falls while
      * nothing is emitted is worse than the gap it hides.
      *
      * See [[AnnotationPolicy]] for the matching rule, which is §4.56's and not a `startsWith`. */
    preservedAnnotations: AnnotationPolicy = AnnotationPolicy.none,
)

/** WHICH annotation families a port claims, and the one question anything asks of it.
  *
  * A VALUE rather than a `List[String]` because the match is not a `startsWith`: a prefix must cut
  * only at a `Symbol.fullName` separator (CLAUDE.md §4.56), so `com.foo` covers `com.foo.Bar` and
  * `com.foo.Bar$Baz` and does NOT cover `com.foobar.Bar`. Written once here, a port may spell its
  * entry with or without the trailing dot and get the same answer; written at each reader, the two
  * spellings would quietly differ.
  *
  * `none` is the default everywhere and makes every consulting site a no-op by arithmetic.
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
  /** the empty parameter §1(b) requires: no family claimed, every argument-bearing annotation on a
    * TYPE reported through `omissions` exactly as it was before the policy existed. */
  val none: AnnotationPolicy = AnnotationPolicy()

trait Frontend:
  /** Parse + resolve, returning units in the order of `cfg.files`. */
  def parse(cfg: FrontendConfig): List[BUnit]
