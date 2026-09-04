package balticporter.tir

/** A phase that a CONFIG FILE can name — the third front door to a port (beside a Scala `main` and
  * an embedding library); `PortConfigMain` reads a `.conf` and runs the port. NOT a plugin-loading
  * mechanism: a §1(c) rule is still CODE the consumer compiles, resolved via `ServiceLoader` — no
  * classname-in-a-string, no predicate in config. Config type is [[ConfigView]], not HOCON `Config`
  * (`balticporter-api` depends on nothing). `fromConfig` must throw for anything unhonoured; unread keys FAIL the load. */
trait TransformFactory:

  /** the stable, kebab-case name a `.conf` writes. */
  def name: String

  /** build the phase from its own config object. Throws [[ConfigError]] on anything unhonourable. */
  def fromConfig(config: ConfigView): Phase

  /** The REMEDIES the phase this factory builds can carry out — declared HERE too, and the
    * duplication is the point: a `resolutions` typo is validated at LOAD before a pipeline exists,
    * while a real id whose phase isn't enabled needs a different message, and only a no-construction
    * declaration can tell them apart. Must state the SAME values as the phase's own [[RemedySource]]
    * — a copy that drifts refuses loudly. `Nil` is the default and today's honest answer. */
  def remedies: List[Remedy] = Nil

object TransformFactory:

  /** THE shared grammar for a [[RuleScope]] in config: `scope { except = [...] }` is
    * `Everywhere(except)`, `scope { only = [...] }` is `Only(include)`, absent is
    * `Everywhere()` (no-op). Both at once REFUSES (no meaning to hold); an empty `only = []` is
    * honoured as written ("only these, and there are none" — see [[RuleScope.Only]]). */
  def scopeOf(config: ConfigView, key: String = "scope",
              default: RuleScope = RuleScope.Everywhere()): RuleScope =
    config.child(key) match
      // …`default` is the phase's OWN no-op and not always the unrestricted one. A phase that
      // RETYPES declarations is unrestricted by default and its scope is an opt-OUT; a phase that
      // ADDS members is the other way round — "everything" would rewrite a port's surface for a
      // key nobody wrote — so its no-op is `Only(Set.empty)`. §1(b) asks that the DEFAULT be the
      // no-op, never that every phase spell it the same way.
      case scala.None => default
      case Some(s) =>
        (s.strings("except"), s.strings("only")) match
          case (Some(_), Some(_)) =>
            throw ConfigError(s.path, "a scope declares `except` OR `only`, never both — " +
              "`Everywhere(except)` and `Only(include)` point in opposite directions and there is " +
              "no value that is both (CLAUDE.md §1(b), DESIGN.md §2.1.1)")
          case (Some(e), scala.None) => RuleScope.Everywhere(e.toSet)
          case (scala.None, Some(o)) => RuleScope.Only(o.toSet)
          case (scala.None, scala.None) =>
            throw ConfigError(s.path, "a scope object declares `except` or `only`; " +
              "omit the object entirely for the unrestricted default")

/** A read-only view over one object of a port's configuration. Minimal on purpose (see
  * [[TransformFactory]]). Returns `None` for an ABSENT key, THROWS [[ConfigError]] for the wrong
  * shape — different mistakes. An implementation must RECORD every key read, so the loader can
  * fail on a key nobody read: HOCON tolerates junk silently, and this is the config path's
  * `PolicyReport` equivalent. */
trait ConfigView:

  /** this object's dotted path from the file's root, for error messages. `""` at the root. */
  def path: String

  /** the keys PRESENT at this level, in declaration order where the format preserves it. */
  def keys: List[String]

  def string(key: String): Option[String]
  def int(key: String): Option[Int]
  def bool(key: String): Option[Boolean]

  /** a list of strings. A single string is NOT silently widened to a one-element list: a config
    * that means a list says so, and quiet coercion is how a scalar typo becomes a valid document. */
  def strings(key: String): Option[List[String]]

  /** an object read wholly as `key -> string`; every one of its keys counts as read. */
  def stringMap(key: String): Option[Map[String, String]]

  /** a nested object. */
  def child(key: String): Option[ConfigView]

  /** is the value at `key` an OBJECT? — the one shape question a reader may ask before reading.
    * Exists for COMPATIBLE EXTENSION of a settled key (`redirects { "a.B" = { to = "c.D", … } }`
    * beside the plain-string form) — catching `string`/`child`'s `ConfigError` would turn a genuine
    * shape error into a silent fallback. A PROBE, not a read: does not mark the key read. Absent
    * key is `false`. */
  def isObject(key: String): Boolean

  /** a list of nested objects. */
  def children(key: String): Option[List[ConfigView]]

  /** `<path>.<key>`, the string an agent edits — quote it in every error (CLAUDE.md §4.575). */
  final def at(key: String): String = if path.isEmpty then key else s"$path.$key"

  final def requireString(key: String): String =
    string(key).getOrElse(throw ConfigError(at(key), "required, and absent"))

  final def requireChild(key: String): ConfigView =
    child(key).getOrElse(throw ConfigError(at(key), "required, and absent"))

  /** a value from a CLOSED set of spellings, named in the error — the loud door of
    * `OpaqueSpec.Primitive.fromScalaName`, generalised. */
  final def enumerated[A](key: String, alternatives: Map[String, A]): Option[A] =
    string(key).map(v => alternatives.getOrElse(v, throw ConfigError(at(key),
      s"'$v' is not one of ${alternatives.keys.toList.sorted.mkString(", ")}")))

/** Anything wrong with a port's configuration, located at the key an agent has to edit. One
  * exception type, not a findings list: a configuration that does not parse produces no port at
  * all, so there is nothing to report findings ABOUT. */
final case class ConfigError(where: String, why: String)
    extends RuntimeException(s"port config: $where: $why")
