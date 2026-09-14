package balticporter.core

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*

/** Discovered `TirFrontend` implementations, keyed by [[TirFrontend.name]]. `.conf` writes `input.frontend = "<name>"` and this registry resolves it. Follows the same `ServiceLoader` pattern as
  * `TransformRegistry`.
  */
final class FrontendRegistry(val frontends: List[TirFrontend]):

  private val byName: Map[String, TirFrontend] =
    frontends.groupBy(_.name).map { (n, fs) =>
      if fs.sizeIs > 1 then
        throw new IllegalArgumentException(
          s"frontend '$n': ${fs.size} implementations claim this name " +
            s"(${fs.map(_.getClass.getName).sorted.mkString(", ")}); " +
            "a frontend name is published API and exactly one class may answer to it"
        )
      n -> fs.head
    }

  def names: List[String] = byName.keys.toList.sorted

  def get(name: String): TirFrontend =
    byName.getOrElse(
      name,
      throw new IllegalArgumentException(
        s"unknown frontend '$name'; discovered on this classpath: ${names.mkString(", ")}. " +
          "A frontend this engine does not ship is registered by putting the consumer's own " +
          "`balticporter.core.TirFrontend` implementation on the classpath with a " +
          "`META-INF/services/balticporter.core.TirFrontend` entry."
      )
    )

object FrontendRegistry:
  def discover(loader: ClassLoader = getClass.getClassLoader): FrontendRegistry =
    new FrontendRegistry(ServiceLoader.load(classOf[TirFrontend], loader).asScala.toList)

  def of(frontends: TirFrontend*): FrontendRegistry = new FrontendRegistry(frontends.toList)
