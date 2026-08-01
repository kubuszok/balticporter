package balticporter.runner

import balticporter.tir.{ConfigError, ConfigView, OpaqueSpec, Phase, TransformFactory}
import balticporter.transform.*

/** The engine's own [[TransformFactory]] registrations — one per transform it ships that a config
  * file can name, and nothing else.
  *
  * ==Which transforms are here, and why exactly these==
  * A phase belongs here iff it is a CLAUDE.md §1(a) or §1(b) transform the engine ships. That is
  * the whole test, and it is what makes the list closed rather than a matter of taste:
  *
  *   - a (b) whose policy is string-shaped is registered — every one of them, so an agent holding a
  *     `.conf` can reach the engine's whole parameterised surface without dropping to Scala;
  *   - a (a) with no configuration is registered taking an EMPTY config object, because a config
  *     file still has to be able to put it in the pipeline (`mutable-params`, `panama-ffi`);
  *   - a (c) is NOT here and cannot be — it lives in the porting repository, ships its own factory,
  *     and is discovered on the consumer's classpath (`GdxSharedIteratorRule` is the worked one);
  *   - `package-rename` is refused by name, see [[TransformRegistry.Reserved]].
  *
  * ==Why these are classes and not `object`s==
  * `java.util.ServiceLoader` instantiates a provider through a public no-argument constructor, and
  * a Scala `object`'s constructor is private. Registration is therefore a top-level `final class`
  * per factory, listed in `META-INF/services/balticporter.tir.TransformFactory`. They are stateless,
  * so nothing depends on there being one instance.
  *
  * ==The one thing config cannot express, stated once==
  * Three of these phases take a `Symbol => Boolean` in Scala: `PrimitiveToOpaqueTransform.hints`,
  * `GlobalsToImplicitsTransform.isContext`, `PanamaFfiTransform.isNative`. A predicate is CODE, and
  * a config format that grew a way to write one would have become a scripting language with the
  * engine as its interpreter. So each is handled the same way:
  *
  *   - where the predicate has a universal default that needs no policy, config uses it
  *     (`isNative` is `_.flags.isNative` — a fact about Java, not about a library);
  *   - where the port must name things, config names them AS DATA and the factory closes over the
  *     data (`globals-to-implicits` takes `contextClasses`, a set of FQNs);
  *   - where the port genuinely needs an arbitrary predicate, config REFUSES and says so, naming
  *     the escape hatch — a factory of the port's own, in the port's own repository, in Scala
  *     (`primitive-to-opaque`'s `hints`).
  *
  * That third case is the §1.5 line held: the conf path constructs the same values the Scala path
  * constructs, and anything a value needs that config cannot express arrives as SPI-discovered
  * code — never as a string that is secretly code.
  */
object BuiltinFactories:

  /** every factory this module registers — the same list its service file names. A spec asserts the
    * two agree, because a factory added here and not there is reachable from a Scala embedder and
    * invisible to the config front door, which is the harder of the two failures to notice. */
  def all: List[TransformFactory] = List(
    new CollectionsFactory, new MutableParamsFactory, new PanamaFfiFactory,
    new TestFrameworkFactory, new StaticForwarderFactory, new ClassTableFactory,
    new TypeRedirectFactory, new MethodBodyFactory, new CallSiteSubstitutionFactory,
    new PortMapMigrationFactory,
    new PrimitiveToOpaqueFactory, new GlobalsToImplicitsFactory, new BeanPropertyFactory,
    new NullabilityFactory,
  )

// ---------------------------------------------------------------------------------------------
// (a) — no policy. The config object is empty; any key under it is caught by the loader's
// unread-key refusal without these classes doing anything.
// ---------------------------------------------------------------------------------------------

final class MutableParamsFactory extends TransformFactory:
  def name = "mutable-params"
  def fromConfig(config: ConfigView): Phase = new MutableParamsTransform

final class PanamaFfiFactory extends TransformFactory:
  def name = "panama-ffi"
  def fromConfig(config: ConfigView): Phase = new PanamaFfiTransform()

// ---------------------------------------------------------------------------------------------
// (b) — policy as data
// ---------------------------------------------------------------------------------------------

/** ```
  * { transform = "collections"
  *   scope { except = ["com.foo.Bridge"] }
  *   retarget { "java.util.Comparator" = "scala.math.Ordering" } }
  * ```
  *
  * `retarget` is the type-only half: java FQN → scala FQN, retyped everywhere and API-mapped
  * nowhere. Legal exactly where the scala target is usable wherever the java source was — see the
  * constructor parameter for the precondition and why the engine cannot check it.
  */
final class CollectionsFactory extends TransformFactory:
  def name = "collections"
  def fromConfig(config: ConfigView): Phase =
    new CollectionsTransform(
      TransformFactory.scopeOf(config),
      config.stringMap("retarget").getOrElse(Map.empty))

/** `{ transform = "test-framework", suite = "munit.FunSuite", testMember = "test" }` */
final class TestFrameworkFactory extends TransformFactory:
  def name = "test-framework"
  def fromConfig(config: ConfigView): Phase =
    new TestFrameworkTransform(
      suite      = config.string("suite").getOrElse(TestFrameworkTransform.DefaultSuite),
      testMember = config.string("testMember").getOrElse("test"),
    )

/** ```
  * { transform = "static-forwarder"
  *   forwarders = [ { wrapper = "…", receiver = "…", members = ["…"] } ] }
  * ```
  */
final class StaticForwarderFactory extends TransformFactory:
  def name = "static-forwarder"
  def fromConfig(config: ConfigView): Phase =
    new StaticForwarderTransform(config.children("forwarders").getOrElse(Nil).map(f =>
      StaticForwarderTransform.Forwarder(
        wrapper  = f.requireString("wrapper"),
        receiver = f.requireString("receiver"),
        members  = f.strings("members").getOrElse(
          throw ConfigError(f.at("members"), "required, and absent — a forwarder with no members " +
            "forwards nothing, which is a policy entry that can only ever be a mistake")).toSet,
      )))

/** `{ transform = "class-table", redirects { "a.B#forName" = "c.D#classFor" } }` */
final class ClassTableFactory extends TransformFactory:
  def name = "class-table"
  def fromConfig(config: ConfigView): Phase =
    new ClassTableTransform(config.stringMap("redirects").getOrElse(Map.empty))

/** ```
  * { transform = "type-redirect"
  *   redirects {
  *     "a.B" = "c.D"                                                  # the flat form
  *     "a.Disposable" = { to = "java.lang.AutoCloseable"               # …and the same entry with
  *                        memberRenames { dispose = "close" } }        #    the target's names
  *   } }
  * ```
  *
  * TWO SHAPES IN ONE MAP, and the flat one is not a legacy spelling: an entry whose target spells
  * every member the same way has nothing to say beyond `to`, and making it say
  * `{ to = "c.D" }` would rewrite every port that already writes the published form for no
  * information. The value is read as an object only when it IS one ([[ConfigView.isObject]]) — never
  * by catching the error the other reader would throw, which would turn a genuine shape mistake (a
  * list, a number) into a silent fallback.
  *
  * A `memberRenames` key is a member SEGMENT under its owner — `dispose`, or `dispose()` for the
  * nilary overload alone. The owner is the entry it is nested in, which is what makes a rename for
  * a type nothing redirects unwritable rather than merely reported.
  */
final class TypeRedirectFactory extends TransformFactory:
  def name = "type-redirect"
  def fromConfig(config: ConfigView): Phase =
    val entries = config.child("redirects").toList.flatMap(rs => rs.keys.map { k =>
      if !rs.isObject(k) then (k, rs.requireString(k), Map.empty[String, String])
      else
        val e = rs.requireChild(k)
        (k, e.requireString("to"), e.stringMap("memberRenames").getOrElse(Map.empty))
    })
    new TypeRedirectTransform(
      redirects     = entries.map((k, to, _) => k -> to).toMap,
      memberRenames = entries.collect { case (k, _, rn) if rn.nonEmpty => k -> rn }.toMap)

/** ```
  * { transform = "bean-properties"
  *   pairs { "a.B#opacity" = "getOpacity/setOpacity"
  *           "a.B#layers"  = "getLayers" } }
  * ```
  *
  * The key is the emitted PROPERTY in the upstream namespace; the value names the accessor(s)
  * explicitly. An absent `pairs` is an empty map, which makes the phase a structural no-op — the
  * §1(b) requirement that "turned off" needs no code path.
  */
final class BeanPropertyFactory extends TransformFactory:
  def name = "bean-properties"
  def fromConfig(config: ConfigView): Phase =
    new BeanPropertyTransform(config.stringMap("pairs").getOrElse(Map.empty))

/** `{ transform = "method-body", bodies { "a.B#m()" = "{ … }" } }` */
final class MethodBodyFactory extends TransformFactory:
  def name = "method-body"
  def fromConfig(config: ConfigView): Phase =
    new MethodBodyTransform(config.stringMap("bodies").getOrElse(Map.empty))

/** ```
  * { transform = "call-site-substitution"
  *   calls { "a.B#m(int,String)" = "c.D.n({recv}, {arg0})" } }
  * ```
  *
  * The key is the RESOLVED CALLEE and the value an expression template; `{recv}` and
  * `{arg0}`…`{argN}` are the call's own receiver and arguments. Named for the phase rather than
  * shortened to `call-site`, so the conf entry reads as what it does to the reader of a diff.
  */
final class CallSiteSubstitutionFactory extends TransformFactory:
  def name = "call-site-substitution"
  def fromConfig(config: ConfigView): Phase =
    new CallSiteSubstitutionTransform(config.stringMap("calls").getOrElse(Map.empty))

/** `{ transform = "port-map-migration", bases = ["base-core"] }`
  *
  * Named for the phase rather than shortened to `port-map`, which is a CHECK name in every run's
  * report — two identifiers that differ by nothing an agent can see is how a config key and a
  * finding get confused for each other.
  *
  * The policy is a list of BASE MODULE NAMES; the maps themselves are discovered from the
  * classpath and the report tree by `PortMap.published`, which is what makes this data rather than
  * a list of files a conf would have to keep in step with a build.
  */
final class PortMapMigrationFactory extends TransformFactory:
  def name = "port-map-migration"
  def fromConfig(config: ConfigView): Phase =
    PortMapTransform.forBases(config.strings("bases").getOrElse(
      throw ConfigError(config.at("bases"),
        "required, and absent — with no base module named there is no published map to read, and " +
          "the phase would report nothing while looking as though it had checked"))*)

/** ```
  * { transform = "primitive-to-opaque"
  *   fqn = "sge.gl.GlHandle", underlying = "Int"
  *   extraHints = ["sge.gl.GL20#glHandle"]
  *   scope { only = ["sge.gl"] } }
  * ```
  *
  * `hints` — `OpaqueSpec`'s own predicate, §1(c) in its purest form — is REFUSED here rather than
  * ignored. A port whose seeds cannot be listed by name registers its own factory and constructs
  * the `OpaqueSpec` with the predicate in Scala; that is one class in the port's repository, and it
  * is the only sanctioned way for behaviour to enter a run from configuration.
  *
  * `extraHints` is the field it fills, spelled exactly as `OpaqueSpec` spells it. A second, friendlier
  * name for the same set would be two homes for one policy, which is the cost DESIGN.md §5.6 warns
  * about, paid for nothing.
  */
final class PrimitiveToOpaqueFactory extends TransformFactory:
  def name = "primitive-to-opaque"
  def fromConfig(config: ConfigView): Phase =
    if config.keys.contains("hints") then
      throw ConfigError(config.at("hints"),
        "`OpaqueSpec.hints` is a `Symbol => Boolean` and a configuration file cannot hold one — a " +
          "predicate written as a string would be code the engine interprets, which is precisely " +
          "what this SPI exists to avoid (CLAUDE.md §1.5). List the seeds by fully-qualified name " +
          "in `extraHints`, or register a `balticporter.tir.TransformFactory` of your own that " +
          "builds the `OpaqueSpec` with the predicate in Scala.")
    new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn        = config.requireString("fqn"),
      hints      = _ => false,
      underlying = config.string("underlying")
                     .map(OpaqueSpec.Primitive.fromScalaName)
                     .getOrElse(OpaqueSpec.Primitive.Int),
      extraHints = config.strings("extraHints").getOrElse(Nil).toSet,
      scope      = TransformFactory.scopeOf(config),
    ))

/** ```
  * { transform    = "nullability"
  *   annotations  = ["com.foo.Null"]        # FQN set, UPSTREAM namespace; empty = no-op
  *   target       = "union"                 # "union" (T | Null) | "wrapper"
  *   wrapper      = "lowlevel.Nullable"     # required iff target = "wrapper", refused otherwise
  *   scope { except = ["com.foo.Bridge"] } }
  * ```
  *
  * `wrapper` is REFUSED under `target = "union"` rather than ignored: a config that names a wrapper
  * and gets a union has been silently overruled, which is the §1(b) failure this SPI exists to
  * prevent. It is read unconditionally so that the loader's unread-key check cannot fire on it
  * before this refusal does — the refusal names the actual mistake, "unknown key" does not.
  */
final class NullabilityFactory extends TransformFactory:
  def name = NullabilityTransform.Name
  def fromConfig(config: ConfigView): Phase =
    val wrapper = config.string("wrapper")
    val union   = config.enumerated("target", Map("union" -> false, "wrapper" -> true)).getOrElse(false)
    val target = (union, wrapper) match
      case (true, Some(w))  => NullabilityTransform.Target.Wrapper(w)
      case (true, scala.None) =>
        throw ConfigError(config.at("wrapper"),
          "required when `target = \"wrapper\"`, and absent — the engine ships no default wrapper " +
            "because two hand ports of one ecosystem chose differently (`T | Null` and `Nullable[T]`), " +
            "so it has no standing to pick. Name a type satisfying the four-member contract " +
            "(apply, empty, extension get, extension isEmpty).")
      case (false, Some(_)) =>
        throw ConfigError(config.at("wrapper"),
          "a wrapper is named but `target` is `union`, which would ignore it — say " +
            "`target = \"wrapper\"`, or remove this key")
      case (false, scala.None) => NullabilityTransform.Target.Union
    new NullabilityTransform(
      annotations = config.strings("annotations").getOrElse(Nil).toSet,
      target      = target,
      scope       = TransformFactory.scopeOf(config),
    )

/** {{{
  * { transform = "globals-to-implicits"
  *   holders = [{
  *     holder  = "com.foo.Gdx"
  *     context = { inject = "sge.Sge" }                  # or { mint = "com.foo.Sge" }
  *     members = { app = "application", gl = "graphics.gl20" }
  *     attach = "method", reader = "summon", boundary = "refuse"
  *     sites  = { "com.foo.Utils#<clinit>" = "lazy-init" }
  *     promoteToClass = [ "com.foo.Viewport" ]
  *     scope { except = [ … ] } }] }
  * }}}
  *
  * The whole policy is DATA, which is why this phase (unlike `primitive-to-opaque`'s seeds) needs no
  * escape hatch: what a port has to say is which class is the ambient context, what its counterpart
  * is called and which of its fields map where, and all three are names. An absent `holders` is
  * REFUSED rather than defaulted — with no holder the phase would thread nothing at all, which is
  * the §1(b) silent no-op this engine exists to remove.
  */
final class GlobalsToImplicitsFactory extends TransformFactory:
  def name = "globals-to-implicits"

  def fromConfig(config: ConfigView): Phase =
    val hs = config.children("holders").getOrElse(
      throw ConfigError(config.at("holders"),
        "required, and absent — with no holder named, the phase would find none and do nothing, " +
          "which is the §1(b) silent no-op this engine refuses"))
    new GlobalsToImplicitsTransform(hs.map(holder))

  private def holder(c: ConfigView): ContextHolder =
    val ctx = c.requireChild("context")
    val contextType = (ctx.string("inject"), ctx.string("mint")) match
      case (Some(f), None) => ContextType.Injected(f)
      case (None, Some(f)) => ContextType.Minted(f)
      case (Some(_), Some(_)) => throw ConfigError(ctx.at("inject"),
        "`inject` and `mint` are the two answers to one question — the port supplies the context " +
          "type, or the engine synthesises it. Declare exactly one")
      case (None, None) => throw ConfigError(ctx.path,
        "declare `inject = \"<fqn>\"` (a context type this port wrote) or `mint = \"<fqn>\"` (one " +
          "the engine synthesises, with a mutable member per mapped static)")
    ContextHolder(
      holder  = c.requireString("holder"),
      context = contextType,
      members = c.stringMap("members").getOrElse(Map.empty),
      attach  = c.enumerated("attach", ContextAttach.values.map(v => v.token -> v).toMap)
                  .getOrElse(ContextAttach.Method),
      reader  = c.enumerated("reader", ContextReader.values.map(v => v.token -> v).toMap)
                  .getOrElse(ContextReader.Summon),
      boundary = c.enumerated("boundary", ContextBoundary.values.map(v => v.token -> v).toMap)
                  .getOrElse(ContextBoundary.Refuse),
      sites = c.stringMap("sites").getOrElse(Map.empty).map((k, v) =>
        k -> ContextSite.fromToken(v).getOrElse(throw ConfigError(c.at("sites"),
          s"'$v' is not one of ${ContextSite.values.map(_.token).sorted.mkString(", ")}"))),
      promoteToClass = c.strings("promoteToClass").getOrElse(Nil).toSet,
      scope = TransformFactory.scopeOf(c),
    )
