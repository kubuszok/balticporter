package balticporter.runner

import balticporter.catalog.FixKind
import balticporter.tir.{ConfigView, Phase, Remedy, RemedySource, TransformFactory}

/** A factory the ENGINE knows nothing about, registered the way a porting repository registers its
  * own §1(c) rule: a class plus one `META-INF/services` line, discovered on the classpath. */
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
