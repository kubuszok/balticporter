package balticporter.transform

import balticporter.core.{MergeablePolicy, SurfacePolicy}
import balticporter.tir.*

/** §1(b) idiom phase: a literal-discriminated object-type union becomes a sealed trait with
  * case classes, and narrowing sites become `match` arms. The policy says WHICH unions and
  * whether fields are `var`; the mechanism (discriminator field, narrowing, member access)
  * is language-independent.
  *
  * Stub — Phase 2.2 of the non-Java frontends plan. The no-op default is `Only(Set.empty)`:
  * a scope that names nothing converts nothing, meeting §1(b)'s empty-parameter-is-a-no-op
  * obligation. */
final class DiscriminatedUnionTransform(
    val scope: RuleScope,
    val mutableFields: Boolean = false,
) extends Phase, SurfacePolicy, MergeablePolicy:
  def name = "discriminated-union"

  def surfaceFingerprint: String =
    val entries = scope.entries
    if entries.isEmpty then "" else s"scope=${entries.toList.sorted.mkString(",")}"

  def subjects: Set[String] = scope.entries

  def mergedWith(later: Phase): Either[String, MergeablePolicy.Merged] = later match
    case d: DiscriminatedUnionTransform =>
      (scope, d.scope) match
        case (RuleScope.Only(a), RuleScope.Only(b)) =>
          val merged = a ++ b
          Right(MergeablePolicy.Merged(
            DiscriminatedUnionTransform(RuleScope.Only(merged), mutableFields || d.mutableFields),
            merged))
        case _ => Left(s"`$name` non-Only scopes cannot merge")
    case other => Left(s"`$name` cannot merge with ${other.getClass.getSimpleName}")

  override def run(program: Program): Program = program
