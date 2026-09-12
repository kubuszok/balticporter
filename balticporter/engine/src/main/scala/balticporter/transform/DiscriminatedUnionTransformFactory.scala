package balticporter.transform

import balticporter.tir.*

final class DiscriminatedUnionTransformFactory extends TransformFactory:
  def name = "discriminated-union"

  def fromConfig(config: ConfigView): Phase =
    val scope = TransformFactory.scopeOf(config, default = RuleScope.Only(Set.empty))
    val mutable = config.bool("mutableFields").getOrElse(false)
    DiscriminatedUnionTransform(scope, mutable)
