package balticporter.runner

import balticporter.catalog.Platform
import balticporter.tir.{ConfigError, ConfigView, OpaqueSpec, Phase, Remedy, RuleScope, TransformFactory}
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
  * Two of these phases take a `Symbol => Boolean` in Scala:
  * `GlobalsToImplicitsTransform.isContext`, `PanamaFfiTransform.isNative`. A predicate is CODE, and
  * a config format that grew a way to write one would have become a scripting language with the
  * engine as its interpreter. So each is handled the same way:
  *
  *   - where the predicate has a universal default that needs no policy, config uses it
  *     (`isNative` is `_.flags.isNative` — a fact about Java, not about a library);
  *   - where the port must name things, config names them AS DATA and the factory closes over the
  *     data (`globals-to-implicits` takes `contextClasses`, a set of FQNs;
  *     `primitive-to-opaque` takes `hints`, a set of FQNs — O4 CLOSED);
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
    new TypeRedirectFactory, new MemberRenameFactory,
    new MethodBodyFactory, new CallSiteSubstitutionFactory,
    new PortMapMigrationFactory,
    new PrimitiveToOpaqueFactory, new GlobalsToImplicitsFactory, new BeanPropertyFactory,
    new NullabilityFactory, new PublicFieldAccessorFactory, new RemediationFactory,
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
  *   retarget { "java.util.Comparator" = "scala.math.Ordering" }
  *   retargetRewrites {
  *     "com.example.Bits" {
  *       "get/1" = "apply"                               # Rename
  *       "set/1" = "addOne"                              # Rename
  *       "removeValue/2" {                               # BoolDispatch
  *         boolDispatch = 1
  *         onTrue = "removeValueByRef"
  *         onFalse = "removeValue"
  *       }
  *       "<init>/0" {                                    # Construct
  *         companion = "lowlevel.util.ObjectMap"
  *         factory = "apply"
  *       }
  *     }
  *   }
  *   reifiedCarriers = ["com.fasterxml.jackson.core.type.TypeReference"]
  *   reflectiveSinks = ["com.fasterxml.jackson.databind.ObjectMapper"] }
  * ```
  *
  * `retarget` is the type-only half: java FQN → scala FQN, retyped everywhere and API-mapped
  * nowhere. Legal exactly where the scala target is usable wherever the java source was — see the
  * constructor parameter for the precondition and why the engine cannot check it.
  *
  * `retargetRewrites` maps per-retarget member rewrites. The outer key is the source FQN (must
  * match a `retarget` key). Each inner key is `"memberName/arity"`. A string value is a `Rename`;
  * an object with `boolDispatch` is a `BoolDispatch`; an object with `companion` + `factory` is a
  * `Construct` (construction rewrite: `new Source(args)` -> `Target.factory(args)`).
  *
  * `reifiedCarriers` names the external generic types whose type ARGUMENTS a third party reads back
  * out of the class file at run time and constructs from — a super-type token
  * (`ENGINE-LIMITS.md` K20). Those arguments stay in java's namespace and the value is bridged where
  * it is used. `java.lang.Class` is included by the engine and needs no entry.
  *
  * `reflectiveSinks` is the same third party at the OTHER end of the same call
  * (`ENGINE-LIMITS.md` K21 face 1): an external type that reads the RUNTIME REPRESENTATION of a
  * value handed to it at a `java.lang.Object` slot. Arguments there are bridged through
  * `JavaCollections.Reified.toJavaValue`. Nothing is included by the engine — java guarantees no
  * such type — and the `OpaqueEgress` boundary rows are the review list a port picks entries from.
  */
final class CollectionsFactory extends TransformFactory:
  def name = "collections"
  def fromConfig(config: ConfigView): Phase =
    import CollectionsTransform.RetargetRewrite
    val retarget = config.stringMap("retarget").getOrElse(Map.empty)
    val rewrites = config.child("retargetRewrites").map { rr =>
      rr.keys.map { srcFqn =>
        val tbl = rr.requireChild(srcFqn)
        srcFqn -> tbl.keys.map { memberArity =>
          val parts = memberArity.split("/", 2)
          if parts.length != 2 then throw ConfigError(rr.at(srcFqn),
            s"retargetRewrites key '$memberArity' must be 'memberName/arity'")
          val (mName, arity) = (parts(0), parts(1).toInt)
          val rw =
            if tbl.isObject(memberArity) then
              val c = tbl.requireChild(memberArity)
              // Distinguish BoolDispatch from Construct by the presence of "boolDispatch" vs
              // "companion".  Both are objects; the key that is present decides the variant.
              if c.int("boolDispatch").isDefined then
                RetargetRewrite.BoolDispatch(
                  c.int("boolDispatch").get,
                  c.requireString("onTrue"),
                  c.requireString("onFalse"))
              else if c.string("companion").isDefined then
                RetargetRewrite.Construct(
                  c.requireString("companion"),
                  c.requireString("factory"),
                  fillTypeArgs = c.bool("fillTypeArgs").getOrElse(false))
              else throw ConfigError(tbl.at(memberArity),
                "object entry must have either 'boolDispatch' (BoolDispatch) or 'companion' (Construct)")
            else
              RetargetRewrite.Rename(tbl.requireString(memberArity))
          (mName, arity) -> rw
        }.toMap
      }.toMap
    }.getOrElse(Map.empty)
    val typeArgs = config.child("retargetTypeArgs").map { ta =>
      import CollectionsTransform.RetargetArg
      ta.keys.map { srcFqn =>
        val argList = ta.strings(srcFqn).getOrElse(
          throw ConfigError(ta.at(srcFqn),
            "expected a list of arg mappings, e.g. [\"arg(0)\", \"scala.Int\"]"))
        srcFqn -> argList.map { v =>
          if v.startsWith("arg(") && v.endsWith(")") then
            RetargetArg.SourceArg(v.drop(4).dropRight(1).toInt)
          else
            RetargetArg.FixedType(v)
        }
      }.toMap
    }.getOrElse(Map.empty)
    new CollectionsTransform(
      scope            = TransformFactory.scopeOf(config),
      retarget         = retarget,
      retargetRewrites = rewrites,
      retargetTypeArgs = typeArgs,
      reifiedCarriers  = config.strings("reifiedCarriers").getOrElse(Nil).toSet,
      reflectiveSinks  = config.strings("reflectiveSinks").getOrElse(Nil).toSet)

/** ```
  * { transform = "public-field-accessors", scope { only = ["com.foo.Model"] } }
  * ```
  *
  * Java's `public` field is part of the class file's surface and scala emits no public JVM field
  * for it, so a framework that auto-detects one sees nothing (`ENGINE-LIMITS.md` K21 face 2). This
  * adds `getX`/`setX` beside such a field for the declarations named — the entries reach nested and
  * ANONYMOUS classes, which is the usual shape. No scope admits nothing, which is the default.
  */
final class PublicFieldAccessorFactory extends TransformFactory:
  def name = "public-field-accessors"
  def fromConfig(config: ConfigView): Phase =
    new PublicFieldAccessorTransform(
      TransformFactory.scopeOf(config, default = RuleScope.Only(Set.empty)))

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

/** ```
  * { transform = "remediation"
  *   classTables { "a.B#forName" = "c.D#classFor" }
  *   targets = ["jvm", "js", "native"] }
  * ```
  *
  * THE PORTABILITY MENU (`RemediationTransform`). It takes no location list: WHICH locations is a
  * `resolutions` selection and lives in the manifest, because a selection spans several producers
  * and is compared across a chain (`DESIGN.md` §8.16). What is here is the one value a template
  * cannot compute — the destination table for `class-table` — and the target set the questions are
  * asked for.
  *
  * `remedies` restates the phase's own menu, and the duplication is the point: a `resolutions` entry
  * is validated at LOAD, before a pipeline exists, so a TYPO and a port that picked a real remedy
  * and forgot the `surface` line need different answers, and only a declaration that costs no
  * construction can tell them apart.
  */
final class RemediationFactory extends TransformFactory:
  def name = RemediationTransform.Name
  override def remedies: List[Remedy] = RemediationTransform.Remedies
  /** `targets` is NOT a key here, deliberately: which backends the module is ported for is
    * `PortManifest.targets`, the phase reads it off the run (`RunScope.platform`), and a second
    * spelling under this transform could disagree with the lane the run reports. A `.conf` that
    * writes one is caught by the loader's unread-key refusal, which is the right sentence. */
  def fromConfig(config: ConfigView): Phase =
    new RemediationTransform(classTables = config.stringMap("classTables").getOrElse(Map.empty))

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
  *     "x.reflect.Field" = { to = "com.foo.ai.TaskField"               # …and a DEPENDENT's own
  *                           scope { only = ["com.foo.ai"] } }         #    slice of the program
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
      if !rs.isObject(k) then (k, rs.requireString(k), Map.empty[String, String], RuleScope.everywhere)
      else
        val e = rs.requireChild(k)
        // `Everywhere(Set.empty)` — this phase's own default and its pre-scope code path, which is
        // what `scopeOf` takes rather than assuming one (CLAUDE.md §1). PER ENTRY, because the merge
        // is: a base's whole-program redirect and a dependent's package-scoped one live in one
        // folded instance.
        (k, e.requireString("to"), e.stringMap("memberRenames").getOrElse(Map.empty),
         TransformFactory.scopeOf(e))
    })
    new TypeRedirectTransform(
      redirects     = entries.map((k, to, _, _) => k -> to).toMap,
      memberRenames = entries.collect { case (k, _, rn, _) if rn.nonEmpty => k -> rn }.toMap,
      scopes        = entries.collect { case (k, _, _, sc) if !sc.isUnrestricted => k -> sc }.toMap)

/** ```
  * { transform = "bean-properties"
  *   pairs { "a.B#opacity" = "getOpacity/setOpacity"                       # def-pair, unchanged
  *           "a.B#layers"  = "getLayers"
  *           "a.B#name"    = { accessors = "getName/setName", target = "var" }
  *           "a.B#props"   = { accessors = "getProps",        target = "val" } } }
  * ```
  *
  * The key is the emitted PROPERTY in the upstream namespace; the value names the accessor(s)
  * explicitly. An absent `pairs` is an empty map, which makes the phase a structural no-op — the
  * §1(b) requirement that "turned off" needs no code path.
  *
  * TWO SHAPES IN ONE MAP, exactly as [[TypeRedirectFactory]]'s `redirects` carries them, and for
  * the same reason: an entry that wants the default shape has nothing to say beyond its accessors,
  * and making it write `{ accessors = "…" }` would rewrite every port that already publishes the
  * flat form for no information. `target` defaults to `def-pair`, so a config written before this
  * key existed constructs exactly the phase it constructed before. The value is read as an object
  * only when it IS one ([[ConfigView.isObject]]) — a PROBE that does not count as a read, so a
  * misspelling INSIDE an entry still reaches the unread-key refusal — never by catching the error
  * the other reader would throw, which would turn a genuine shape mistake into a silent fallback.
  */
final class BeanPropertyFactory extends TransformFactory:
  def name = "bean-properties"
  def fromConfig(config: ConfigView): Phase =
    import balticporter.transform.BeanPropertyTransform.Target
    val entries = config.child("pairs").toList.flatMap(ps => ps.keys.map { k =>
      if !ps.isObject(k) then (k, ps.requireString(k), Target.DefPair)
      else
        val e = ps.requireChild(k)
        (k, e.requireString("accessors"),
          e.enumerated("target", Target.byConfigName).getOrElse(Target.DefPair))
    })
    new BeanPropertyTransform(
      pairs   = entries.map((k, v, _) => k -> v).toMap,
      targets = entries.collect { case (k, _, t) if t != Target.DefPair => k -> t }.toMap,
      scope   = TransformFactory.scopeOf(config, default = RuleScope.Only(Set.empty)))

/** ```
  * { transform = "member-rename"
  *   renames { "a.VisWindow#close"   = "closeWindow"      # every overload of `close`
  *             "a.Stream#close(int)" = "closeAt" } }      # exactly one of them
  * ```
  *
  * The key is a MEMBER KEY in the upstream namespace and the value is a BARE MEMBER NAME — no
  * nesting, and deliberately no second shape. A `type-redirect` entry nests its `memberRenames`
  * under the type it redirects because the owner is already named there and a rename for an
  * un-redirected type must be unwritable; here the owner is the key's own first half, so a nested
  * form would be a second spelling of one act (`CLAUDE.md` §5's one-policy-one-spelling rule).
  *
  * An absent `renames` is an empty map, which makes the phase a structural no-op — the §1(b)
  * requirement that "turned off" needs no code path.
  */
final class MemberRenameFactory extends TransformFactory:
  def name = MemberRenameTransform.Name
  def fromConfig(config: ConfigView): Phase =
    new MemberRenameTransform(config.stringMap("renames").getOrElse(Map.empty))

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
  * `hints` is a `Set[String]` of exact FQNs matched against `Symbol.fullName` — the port's own
  * seeds, §1(c) in its purest form. Both `hints` and `extraHints` are fully-qualified names and
  * both reach the surface fingerprint (O4 CLOSED); the two are kept apart so that a port's
  * own policy (which an agent reviews once) and an agent's additions (which arrive after a compile
  * failure) are visibly different artifacts.
  */
final class PrimitiveToOpaqueFactory extends TransformFactory:
  def name = "primitive-to-opaque"
  def fromConfig(config: ConfigView): Phase =
    new PrimitiveToOpaqueTransform(OpaqueSpec(
      fqn        = config.requireString("fqn"),
      hints      = config.strings("hints").getOrElse(Nil).toSet,
      underlying = config.string("underlying")
                     .map(OpaqueSpec.Primitive.fromScalaName)
                     .getOrElse(OpaqueSpec.Primitive.Int),
      extraHints = config.strings("extraHints").getOrElse(Nil).toSet,
      scope      = TransformFactory.scopeOf(config),
    ))

/** ```
  * { transform    = "nullability"
  *   annotations  = ["com.foo.Null"]        # FQN set, UPSTREAM namespace; empty = no-op
  *   target       = "union"                 # "union" (T | Null) | "named" | "option"
  *   wrapper      = "lowlevel.Nullable"     # required iff target = "named", refused otherwise
  *   scope { except = ["com.foo.Bridge"] } }
  * ```
  *
  * `wrapper` is REFUSED under `target = "union"` or `target = "option"` rather than ignored: a
  * config that names a wrapper and gets a union has been silently overruled, which is the §1(b)
  * failure this SPI exists to prevent. It is read unconditionally so that the loader's unread-key
  * check cannot fire on it before this refusal does — the refusal names the actual mistake,
  * "unknown key" does not. `target = "option"` uses `scala.Option` and needs no wrapper FQN.
  */
final class NullabilityFactory extends TransformFactory:
  def name = NullabilityTransform.Name
  def fromConfig(config: ConfigView): Phase =
    val wrapper = config.string("wrapper")
    val targetKey = config.enumerated("target",
      Map("union" -> "union", "named" -> "named", "wrapper" -> "named", "option" -> "option"))
      .getOrElse("union")
    val target = (targetKey, wrapper) match
      case ("named", Some(w))  => NullabilityTransform.Target.Named(w)
      case ("named", scala.None) =>
        throw ConfigError(config.at("wrapper"),
          "required when `target = \"named\"`, and absent — the engine ships no default wrapper " +
            "because two hand ports of one ecosystem chose differently (`T | Null` and `Nullable[T]`), " +
            "so it has no standing to pick. Name a type satisfying the four-member contract " +
            "(apply, empty, extension get, extension isEmpty).")
      case ("option", Some(_)) =>
        throw ConfigError(config.at("wrapper"),
          "a wrapper is named but `target` is `option`, which uses `scala.Option` — remove the " +
            "`wrapper` key, or say `target = \"named\"`")
      case ("option", scala.None) => NullabilityTransform.Target.OptionTarget
      case (_, Some(_)) =>
        throw ConfigError(config.at("wrapper"),
          "a wrapper is named but `target` is `union`, which would ignore it — say " +
            "`target = \"named\"`, or remove this key")
      case (_, scala.None) => NullabilityTransform.Target.Union
    new NullabilityTransform(
      annotations     = config.strings("annotations").getOrElse(Nil).toSet,
      target          = target,
      scope           = TransformFactory.scopeOf(config),
      nullableMembers = config.strings("nullableMembers").getOrElse(Nil).toSet,
    )

/** {{{
  * { transform = "globals-to-implicits"
  *   holders = [{
  *     holder  = "com.foo.Gdx"
  *     context = { inject = "sge.Sge" }                  # or { mint = "com.foo.Sge" }
  *     members = { app = "application", gl = "graphics.gl20" }
  *     attach = "method", reader = "summon", boundary = "refuse"
  *     sites  = { "com.foo.Utils#<clinit>" = "lazy-init" }
  *     selfSupplied = { "com.foo.FooTest" = "com.foo.TestFixture.ctx()" }
  *     retain       = { "com.foo.Widget" = "fooContext" }
  *     cache        = { "com.foo.Boot" = "fooContext" }
  *     promoteToClass = [ "com.foo.Viewport" ]
  *     scope { except = [ … ] } }] }
  *
  * # …and in a DEPENDENT, an EXTENSION: no `context` block, per-declaration keys only
  * { transform = "globals-to-implicits"
  *   holders = [{ holder = "com.foo.Gdx"
  *                sites  = { "com.dep.Utils#<clinit>" = "lazy-init" } }] }
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
    // A HOLDER ENTRY WITH NO `context` BLOCK IS AN EXTENSION — the per-declaration half of a holder
    // the BASE declares (`ENGINE-LIMITS.md` CT8). The absence of `context` is the signal because
    // it is the one key with no default: a dependent has nothing to say about the context TYPE, and
    // any shared-surface key written inside such a block is an unread key the loader already
    // refuses. §1.5 is then structural rather than a convention on both sides of the front door.
    val (exts, full) = hs.partition(_.child("context").isEmpty)
    new GlobalsToImplicitsTransform(full.map(holder), exts.map(extension))

  private def extension(c: ConfigView): ContextHolderExtension =
    ContextHolderExtension(
      holder       = c.requireString("holder"),
      sites        = sites(c),
      selfSupplied = c.stringMap("selfSupplied").getOrElse(Map.empty),
      retain       = c.stringMap("retain").getOrElse(Map.empty),
      cache        = c.stringMap("cache").getOrElse(Map.empty),
    )

  private def sites(c: ConfigView): Map[String, ContextSite] =
    c.stringMap("sites").getOrElse(Map.empty).map((k, v) =>
      k -> ContextSite.fromToken(v).getOrElse(throw ConfigError(c.at("sites"),
        s"'$v' is not one of ${ContextSite.values.map(_.token).sorted.mkString(", ")}")))

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
      sites = sites(c),
      selfSupplied = c.stringMap("selfSupplied").getOrElse(Map.empty),
      retain = c.stringMap("retain").getOrElse(Map.empty),
      cache = c.stringMap("cache").getOrElse(Map.empty),
      promoteToClass = c.strings("promoteToClass").getOrElse(Nil).toSet,
      scope = TransformFactory.scopeOf(c),
    )
