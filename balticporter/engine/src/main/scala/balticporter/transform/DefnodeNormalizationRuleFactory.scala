package balticporter.transform

import balticporter.tir.*

final class DefnodeNormalizationRuleFactory extends TransformFactory:
  def name = "defnode-normalization"

  def fromConfig(config: ConfigView): Phase =
    val scope = TransformFactory.scopeOf(config, default = RuleScope.Only(Set.empty))
    DefnodeNormalizationRule(scope)
