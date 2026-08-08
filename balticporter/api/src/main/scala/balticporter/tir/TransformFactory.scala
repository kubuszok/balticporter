package balticporter.tir

/** A phase that a CONFIG FILE can name — the third front door to a port, beside a Scala `main` and
  * a library that embeds the engine.
  *
  * ==What this is for, and what it deliberately is not==
  * The consumer of this framework is an agent in another repository (CLAUDE.md §4.45). Writing a
  * Scala migration program is the full-strength door and stays the recommended one; but every
  * corpus migration program in this repository turned out to be hard-coded configuration plus
  * `PortRun`, and an agent that only needs THAT should not have to write a build, a `main` and an
  * import list to get it. `balticporter.runner.PortConfigMain` reads a `.conf` and runs the port;
  * this trait is how a phase becomes nameable from one.
  *
  * It is NOT a plugin-loading mechanism for arbitrary behaviour, and the difference matters. A §1(c)
  * rule is still CODE that the consumer compiles — `GdxSharedIteratorRule` in the corpus is the
  * worked example. What a factory adds is one sanctioned indirection: a STABLE NAME resolving,
  * through `java.util.ServiceLoader`, to a class the consumer's own build already put on the
  * classpath. There is no classname-in-a-string, no reflective construction of a lambda, and no way
  * for a config file to express a predicate. A port that needs a predicate — `OpaqueSpec.hints` is
  * the canonical one — writes a factory that closes over it, in its own repository, in Scala.
  *
  * ==Why the config type is [[ConfigView]] and not a HOCON `Config`==
  * `balticporter-api` depends on NOTHING (DESIGN.md §3.2), and that property is what makes a §1(c)
  * rule cost one dependency that drags in no emitter, no orchestrator and no Spoon. A
  * `com.typesafe.config.Config` in this signature would put a config library into every rule
  * author's compile classpath to state a contract that needs five accessors. So the engine adapts
  * HOCON to [[ConfigView]] and the SPI stays dependency-free.
  *
  * ==The contract==
  *   - [[name]] is STABLE and kebab-case. It is what a `.conf` writes and what an error message
  *     lists, so changing it breaks every consumer's config file — treat it as published API. It is
  *     deliberately NOT `Phase.name`: those are report identities and contain characters a config
  *     key should not (`java-collections->scala`, `junit->portable-suite`).
  *   - [[fromConfig]] gets the transform's OWN config object — the surface entry minus its
  *     `transform` key — and returns a configured [[Phase]]. It must throw [[ConfigError]] (or let
  *     one propagate) for anything it cannot honour: a value it silently ignores is the §1(b)
  *     silent no-op this whole mechanism exists to prevent.
  *   - Every key the factory reads is recorded by the view, and the loader FAILS the run on a key
  *     nobody read. A factory therefore does not need to validate for unknown keys; it needs only
  *     to read the ones it honours.
  *   - Registration is a `META-INF/services/balticporter.tir.TransformFactory` line naming a class
  *     with a no-argument constructor.
  */
trait TransformFactory:

  /** the stable, kebab-case name a `.conf` writes. */
  def name: String

  /** build the phase from its own config object. Throws [[ConfigError]] on anything unhonourable. */
  def fromConfig(config: ConfigView): Phase

  /** The REMEDIES the phase this factory builds can carry out — declared HERE as well as on the
    * phase, and the duplication is the point.
    *
    * A `resolutions` entry is validated at LOAD, before a pipeline exists, and the two mistakes a
    * port can make there need different answers: a TYPO is refused with the alternatives listed,
    * while a port that picked a real remedy and forgot to enable the phase that offers it must be
    * told to add the phase — a `ConfigError` there would send it hunting for a spelling mistake in a
    * correct id. Only a declaration that costs no construction can tell them apart, and a factory is
    * the one thing on the classpath that speaks for a phase without building one.
    *
    * State the SAME values the phase's own [[RemedySource]] returns. The vocabulary treats one
    * remedy declared twice as one entry and refuses two DIFFERENT remedies claiming one id, so a
    * copy that drifts is a loud failure rather than a silent second menu.
    *
    * `Nil` is the default and the honest answer for a factory whose phase offers no menu — which is
    * every factory this engine ships today. */
  def remedies: List[Remedy] = Nil

object TransformFactory:

  /** THE shared grammar for a [[RuleScope]] in config, so every retyping rule spells it the same
    * way and a third-party factory gets it for free:
    *
    * {{{
    * scope { except = ["com.foo.Bridge"] }   // RuleScope.Everywhere(except)
    * scope { only   = ["com.foo.gl"] }       // RuleScope.Only(include)
    * // absent                               // RuleScope.Everywhere() — the no-op default
    * }}}
    *
    * Both halves at once is REFUSED rather than resolved: `Everywhere(except) ∩ Only(include)` has
    * no meaning the enum can hold, and picking one silently would fence a port somewhere it did not
    * ask for. An empty `only = []` is honoured as written — "only these, and there are none" is a
    * statement a port can make on purpose (see [[RuleScope.Only]]).
    */
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

/** A read-only view over one object of a port's configuration.
  *
  * Minimal on purpose — see [[TransformFactory]] for why this exists instead of a HOCON `Config`.
  * Every accessor returns `None` for an ABSENT key and THROWS [[ConfigError]] for a key that is
  * present with the wrong shape, because those are different mistakes: the first may be a legitimate
  * default, the second is never anything but an error.
  *
  * An implementation must RECORD every key an accessor is called with, so that the loader can fail
  * on a key nobody read. That is not an optional nicety: HOCON tolerates junk silently, a typo'd
  * key is exactly the §1(b) no-op `PolicyReport` had to be built to catch, and the config path has
  * no `PolicyReport` — it has this.
  */
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
    *
    * It exists for the COMPATIBLE EXTENSION of a settled key. `redirects { "a.B" = "c.D" }` is a
    * published shape and every port that writes it must keep working, so the entry that grew a
    * second field spells it `"a.B" = { to = "c.D", … }` and the two forms live in one map. Deciding
    * between them by CATCHING the [[ConfigError]] that `string` or `child` throws would turn a
    * genuine shape error (a list, a number) into a silent fallback, which is the §1(b) no-op this
    * whole front door refuses.
    *
    * It is a PROBE and not a read: asking does not mark the key read, because a factory that probes
    * and then honours nothing must still be caught by the unread-key refusal. An absent key is
    * `false` — there is no value there to be an object. */
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

/** Anything wrong with a port's configuration, located at the key an agent has to edit.
  *
  * One exception type rather than a findings list, and deliberately: a configuration that does not
  * parse produces no port at all, so there is nothing to report findings ABOUT. Every other
  * diagnostic in this engine is about emitted code and stays a finding.
  */
final case class ConfigError(where: String, why: String)
    extends RuntimeException(s"port config: $where: $why")
