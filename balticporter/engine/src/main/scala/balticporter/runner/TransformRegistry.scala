package balticporter.runner

import balticporter.tir.{ConfigError, ConfigView, Phase, TransformFactory}

import java.util.ServiceLoader
import scala.jdk.CollectionConverters.*

/** The [[TransformFactory]] instances visible on one classpath, and the only place a config file's
  * `transform = "…"` is turned into a [[Phase]]. The engine's own transforms register through the
  * SAME `META-INF/services/balticporter.tir.TransformFactory` mechanism a consumer's rule does —
  * no built-in table beside the service loader. `package-rename` is NOT constructible: it must run
  * after every other phase (§4.56), so `PortRun` takes it as MANIFEST DATA instead. */
final class TransformRegistry(val factories: List[TransformFactory]):

  private val byName: Map[String, TransformFactory] =
    factories.groupBy(_.name).map { (n, fs) =>
      if fs.sizeIs > 1 then
        throw ConfigError(s"transform '$n'",
          s"${fs.size} factories claim this name (${fs.map(_.getClass.getName).sorted.mkString(", ")}); " +
            "a transform name is published API and exactly one class may answer to it")
      n -> fs.head
    }

  /** every name a `.conf` may write here, sorted — what an unknown-name error lists. */
  def names: List[String] = byName.keys.toList.sorted

  def get(name: String): Option[TransformFactory] = byName.get(name)

  /** every REMEDY this classpath declares, whether or not a port enables the declaring phase — see
    * [[TransformFactory.remedies]] for why a factory answers for a phase it has not built.
    *
    * Assembled with the same refusal `byName` above uses, and by the same argument: a remedy id is
    * published API, so two different remedies claiming one id is a run that cannot say which
    * mechanism a port asked for. */
  lazy val remedies: balticporter.tir.RemedyVocabulary =
    factories.foldLeft(balticporter.tir.RemedyVocabulary.empty)((acc, f) =>
      acc ++ balticporter.tir.RemedyVocabulary.declared(f.getClass.getName, f.remedies))

  /** Build one surface entry. `where` is the config path, for the error. */
  def phase(name: String, config: ConfigView, where: String): Phase =
    TransformRegistry.Reserved.get(name) match
      case Some(why) => throw ConfigError(where, why)
      case scala.None =>
        byName.get(name) match
          case Some(f) => f.fromConfig(config)
          case scala.None =>
            throw ConfigError(where,
              s"unknown transform '$name'; discovered on this classpath: ${names.mkString(", ")}. " +
                "A transform this engine does not ship is registered by putting the consumer's own " +
                "`balticporter.tir.TransformFactory` implementation on the classpath with a " +
                "`META-INF/services/balticporter.tir.TransformFactory` entry (CLAUDE.md §1(c)).")

object TransformRegistry:

  /** names a surface list may never hold, each with the reason and the thing to write instead. */
  val Reserved: Map[String, String] = Map(
    "package-rename" ->
      ("`package-rename` is not a surface transform. It has to run AFTER every other phase — all " +
        "of their policy is written in the UPSTREAM namespace, and `runsAfter` cannot say \"after " +
        "everything\" (CLAUDE.md §4.56) — so it is MANIFEST DATA that `PortRun` appends last and " +
        "verifies. Write `manifest.packageRenames { \"upstream.prefix\" = \"port.prefix\" }` instead.")
  )

  /** every factory the given classloader can see. */
  def discover(loader: ClassLoader = getClass.getClassLoader): TransformRegistry =
    new TransformRegistry(ServiceLoader.load(classOf[TransformFactory], loader).asScala.toList)

  /** an explicit registry, for a spec or for an embedder that does not want classpath discovery. */
  def of(factories: TransformFactory*): TransformRegistry = new TransformRegistry(factories.toList)
