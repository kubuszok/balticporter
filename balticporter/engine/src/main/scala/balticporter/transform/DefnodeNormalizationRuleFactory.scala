package balticporter.transform

import balticporter.tir.*

final class DefnodeNormalizationRuleFactory extends TransformFactory:
  def name = "defnode-normalization"

  def fromConfig(config: ConfigView): Phase = DefnodeNormalizationRule.fromConfig(config)
