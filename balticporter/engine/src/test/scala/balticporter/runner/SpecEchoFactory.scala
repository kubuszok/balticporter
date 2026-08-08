package balticporter.runner

import balticporter.catalog.FixKind
import balticporter.tir.{ConfigView, Phase, Remedy, RemedySource, TransformFactory}

/** A factory the ENGINE knows nothing about, registered the way a porting repository registers its
  * own §1(c) rule: a class plus one `META-INF/services` line, discovered on the classpath.
  *
  * It exists so that "discovery finds the built-ins" and "discovery finds a stranger's factory" are
  * two separate assertions. A registry that only ever saw classes from its own jar would pass every
  * test the engine could write and fail the first consumer.
  *
  * It also DECLARES a remedy, on the factory and on the phase alike — which is how a §1(c) rule
  * publishes a menu, and the only shape in which "this id exists but you did not enable the phase"
  * is distinguishable from "you typed the id wrong". The two declarations are the same value, so the
  * vocabulary folds them into one entry.
  */
final class SpecEchoFactory extends TransformFactory:
  def name: String = "spec-echo"

  override def remedies: List[Remedy] = List(SpecEchoFactory.Echo)

  def fromConfig(config: ConfigView): Phase =
    val tag = config.string("tag").getOrElse("")
    new Phase with RemedySource:
      def name: String            = s"spec-echo($tag)"
      def remedies: List[Remedy]  = List(SpecEchoFactory.Echo)

object SpecEchoFactory:
  val Echo: Remedy = Remedy(
    id = "spec-echo-remedy", lane = "spec-echo-lane", kind = "spec-echo-kind",
    emissionAffecting = true, fix = FixKind.LibraryRule,
    what = "a §1(c) rule's own menu entry, declared by the factory that would build it")
