package balticporter.runner

import balticporter.tir.{ConfigError, ConfigView}

import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue, ConfigValueType}

import scala.jdk.CollectionConverters.*

/** [[ConfigView]] over HOCON. Every read is recorded; [[unread]] reports keys nobody accessed
  * (catches typos that HOCON silently tolerates). Absent key = `None`; wrong shape = throws.
  * Containment is a tree walk, not a dot-split (keys may contain dots). */
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
    // Unquoted numbers/booleans accepted as text
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

  /** Shape probe; does NOT record a read (so the unread-key check is not defeated). */
  def isObject(key: String): Boolean =
    Option(obj.get(key)).exists(_.valueType == ConfigValueType.OBJECT)

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

  /** Mark a key as read without accessing it (used when loading a conf as a base). */
  def markRead(key: String): Unit = readKeys += key

  /** Every key in this subtree that no accessor asked for, deepest paths included, sorted. A key
    * in `readKeys` that this object does not have is harmless and not reported. */
  def unread: List[String] =
    val here = keys.filterNot(readKeys.contains).map(at)
    (here ++ handed.toList.flatMap(_.unread)).sorted

object HoconView:

  def apply(path: String, obj: ConfigObject): HoconView = new HoconView(path, obj)

  def root(config: Config): HoconView = new HoconView("", config.root)

  /** Parse a file, resolving substitutions against SYSTEM PROPERTIES only — never
    * `ConfigFactory.load`, which would pull in `reference.conf` from every jar on the classpath. A
    * port must be reproducible from its own file (CLAUDE.md §5). */
  def parse(file: java.nio.file.Path): Config =
    if !java.nio.file.Files.isRegularFile(file) then
      throw ConfigError(file.toString, "no such port configuration file")
    com.typesafe.config.ConfigFactory
      .parseFile(file.toFile)
      .resolveWith(com.typesafe.config.ConfigFactory.systemProperties)
