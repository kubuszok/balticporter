package balticporter.runner

import balticporter.tir.{ConfigError, ConfigView}

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue, ConfigValueType}

import scala.jdk.CollectionConverters.*

/** [[ConfigView]] over HOCON, and the reason the config front door can be strict.
  *
  * ==Every read is RECORDED, so a key nobody read fails the run==
  * HOCON tolerates junk: a document with `dropTypes` where the schema says `dropType` parses
  * perfectly, resolves perfectly, and silently ports a library without the drop. That is the §1(b)
  * silent no-op exactly — the failure `PolicyReport` had to be built to catch for manifest keys —
  * and on the config path there is nothing else to catch it, because a mistyped key produces no
  * finding, no count, and no compile error.
  *
  * So the view keeps the set of keys an accessor was called with, and every child view it hands out
  * is remembered. [[unread]] then walks that tree and names every key nobody looked at, with its
  * full dotted path. It is a TREE walk rather than a path-string comparison on purpose: a key may
  * itself contain dots (`packageRenames { "com.foo" = "sge" }`), and any scheme that decided
  * containment by splitting a path on `.` would be the §4.56 mistake in a new place.
  *
  * A whole object read as a map or handed to a factory counts every key under it as read — the
  * reader of that object is answerable for its contents, not this class.
  *
  * ==Absent vs malformed==
  * An absent key is `None`, because a default is a legitimate answer. A key present with the wrong
  * shape THROWS: `files = "Foo.java"` where a list is meant is never anything but a mistake, and
  * widening it silently is how a scalar typo becomes a valid document.
  */
final class HoconView private (val path: String, obj: ConfigObject) extends ConfigView:

  private val readKeys = collection.mutable.LinkedHashSet.empty[String]
  private val handed   = collection.mutable.ListBuffer.empty[HoconView]

  def keys: List[String] = obj.keySet.asScala.toList.sorted

  private def raw(key: String): Option[ConfigValue] =
    readKeys += key
    Option(obj.get(key)).filter(_.valueType != ConfigValueType.NULL)

  private def wrongType(key: String, want: String, got: ConfigValue): Nothing =
    throw ConfigError(at(key), s"expected $want, found ${got.valueType.name.toLowerCase}")

  def string(key: String): Option[String] = raw(key).map {
    case v if v.valueType == ConfigValueType.STRING  => v.unwrapped.asInstanceOf[String]
    // a number or a boolean written unquoted is still a scalar the author meant as text
    case v if v.valueType == ConfigValueType.NUMBER  => String.valueOf(v.unwrapped)
    case v if v.valueType == ConfigValueType.BOOLEAN => String.valueOf(v.unwrapped)
    case v                                           => wrongType(key, "a string", v)
  }

  def int(key: String): Option[Int] = raw(key).map {
    case v if v.valueType == ConfigValueType.NUMBER => v.unwrapped.asInstanceOf[Number].intValue
    case v                                          => wrongType(key, "a number", v)
  }

  def bool(key: String): Option[Boolean] = raw(key).map {
    case v if v.valueType == ConfigValueType.BOOLEAN => v.unwrapped.asInstanceOf[Boolean]
    case v                                           => wrongType(key, "a boolean", v)
  }

  def strings(key: String): Option[List[String]] = raw(key).map {
    case l: ConfigList =>
      l.asScala.toList.zipWithIndex.map {
        case (v, _) if v.valueType == ConfigValueType.STRING => v.unwrapped.asInstanceOf[String]
        case (v, i) =>
          throw ConfigError(s"${at(key)}[$i]", s"expected a string, found ${v.valueType.name.toLowerCase}")
      }
    case v => wrongType(key, "a list of strings", v)
  }

  def stringMap(key: String): Option[Map[String, String]] = raw(key).map {
    case o: ConfigObject =>
      o.asScala.toMap.map {
        case (k, v) if v.valueType == ConfigValueType.STRING => k -> v.unwrapped.asInstanceOf[String]
        case (k, v) =>
          throw ConfigError(s"${at(key)}.$k", s"expected a string, found ${v.valueType.name.toLowerCase}")
      }
    case v => wrongType(key, "an object of string values", v)
  }

  def child(key: String): Option[ConfigView] = raw(key).map {
    case o: ConfigObject => hand(at(key), o)
    case v               => wrongType(key, "an object", v)
  }

  def children(key: String): Option[List[ConfigView]] = raw(key).map {
    case l: ConfigList =>
      l.asScala.toList.zipWithIndex.map {
        case (o: ConfigObject, i) => hand(s"${at(key)}[$i]", o)
        case (v, i) =>
          throw ConfigError(s"${at(key)}[$i]", s"expected an object, found ${v.valueType.name.toLowerCase}")
      }
    case v => wrongType(key, "a list of objects", v)
  }

  private def hand(p: String, o: ConfigObject): HoconView =
    val v = new HoconView(p, o)
    handed += v
    v

  /** Declare a key CONSIDERED READ without reading it.
    *
    * The one caller is a conf loaded as a `base`: §1.5 says a dependent inherits the shared SURFACE
    * and nothing else, so the base file's `input`, `output`, `provenance` and `runtimeMode` are
    * deliberately not this run's business and must not be reported as junk. They are still checked
    * — by the run that loads that same file as its own configuration, which is the run they belong
    * to. */
  def markRead(key: String): Unit = readKeys += key

  /** Every key in this subtree that no accessor asked for, deepest paths included, sorted.
    *
    * `readKeys` may name a key this object does not have (a reader asking for an optional value);
    * that is harmless and is not reported — the question here is only about keys that EXIST and
    * that nobody read. */
  def unread: List[String] =
    val here = keys.filterNot(readKeys.contains).map(at)
    (here ++ handed.toList.flatMap(_.unread)).sorted

object HoconView:

  def apply(path: String, obj: ConfigObject): HoconView = new HoconView(path, obj)

  def root(config: Config): HoconView = new HoconView("", config.root)

  /** Parse a file, resolving substitutions against SYSTEM PROPERTIES only.
    *
    * `ConfigFactory.load` would also pull in `reference.conf` from every jar on the classpath and
    * the whole environment, which is how a port's configuration becomes a function of which
    * libraries happen to be on the classpath. A port must be reproducible from its own file
    * (CLAUDE.md §5), so the resolution sources are exactly two and both are visible: the file
    * itself, and `-D` system properties for the values an operator genuinely supplies (the measure
    * lanes' `balticporter.root` is the worked example).
    */
  def parse(file: java.nio.file.Path): Config =
    if !java.nio.file.Files.isRegularFile(file) then
      throw ConfigError(file.toString, "no such port configuration file")
    com.typesafe.config.ConfigFactory
      .parseFile(file.toFile)
      .resolveWith(com.typesafe.config.ConfigFactory.systemProperties)
