package balticporter.runner

import balticporter.tir.{ConfigView, Phase, TransformFactory}

/** A factory the ENGINE knows nothing about, registered the way a porting repository registers its
  * own §1(c) rule: a class plus one `META-INF/services` line, discovered on the classpath.
  *
  * It exists so that "discovery finds the built-ins" and "discovery finds a stranger's factory" are
  * two separate assertions. A registry that only ever saw classes from its own jar would pass every
  * test the engine could write and fail the first consumer.
  */
final class SpecEchoFactory extends TransformFactory:
  def name: String = "spec-echo"

  def fromConfig(config: ConfigView): Phase =
    val tag = config.string("tag").getOrElse("")
    new Phase:
      def name: String = s"spec-echo($tag)"
