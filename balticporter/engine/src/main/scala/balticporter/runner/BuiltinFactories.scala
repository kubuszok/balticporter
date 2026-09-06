package balticporter.runner

import balticporter.catalog.Platform
import balticporter.tir.{ConfigError, ConfigView, Descriptor, OpaqueSpec, Phase, Reason, Remedy, RuleScope, TransformFactory}
import balticporter.transform.*

/** ServiceLoader-discovered [[TransformFactory]] registrations for the engine's own
  * §1(a) and §1(b) transforms. Each is a `final class` (ServiceLoader needs a public no-arg
  * constructor). A §1(c) rule ships in the consumer's repository. Predicates are expressed as
  * data (FQN sets) rather than code. See [[TransformRegistry.Reserved]] for `package-rename`. */
object BuiltinFactories:

  /** Every factory this module registers; a spec asserts agreement with the service file. */
  def all: List[TransformFactory] = List(
    new CollectionsFactory, new MutableParamsFactory, new PanamaFfiFactory,
    new TestFrameworkFactory, new StaticForwarderFactory, new ClassTableFactory,
    new TypeRedirectFactory, new MemberRenameFactory,
    new MethodBodyFactory, new AddMembersFactory, new CallSiteSubstitutionFactory,
    new PortMapMigrationFactory,
    new PrimitiveToOpaqueFactory, new GlobalsToImplicitsFactory, new BeanPropertyFactory,
    new NullabilityFactory, new PublicFieldAccessorFactory, new RemediationFactory,
    new ClassToTraitFactory, new RegistryFactory, new ElementWitnessFactory,
  )

// (a) — no policy; empty config object

final class MutableParamsFactory extends TransformFactory:
  def name = "mutable-params"
  def fromConfig(config: ConfigView): Phase = new MutableParamsTransform

final class PanamaFfiFactory extends TransformFactory:
  def name = "panama-ffi"
  def fromConfig(config: ConfigView): Phase = new PanamaFfiTransform()

// (b) — policy as data

/** `.conf` shape for `CollectionsTransform`: `scope`/`retarget`/`retargetRewrites` (key
  * `"member/arity"` or `"member/(descriptor)"`, values Rename/BoolDispatch/Construct/… variants),
  * `reifiedCarriers` (super-type tokens whose args stay in java's namespace, `ENGINE-LIMITS.md`
  * K20), `reflectiveSinks` (types reading runtime representations at `Object` slots, K21). */
final class CollectionsFactory extends TransformFactory:
  def name = "collections"
  def fromConfig(config: ConfigView): Phase =
    import CollectionsTransform.RetargetRewrite
    val retarget = config.stringMap("retarget").getOrElse(Map.empty)
    def parseRewrite(tbl: ConfigView, memberKey: String, mName: String): RetargetRewrite =
      if tbl.isObject(memberKey) then
        val c = tbl.requireChild(memberKey)
        if c.int("boolDispatch").isDefined then
          RetargetRewrite.BoolDispatch(
            c.int("boolDispatch").get,
            c.requireString("onTrue"),
            c.requireString("onFalse"))
        else if c.string("companion").isDefined then
          RetargetRewrite.Construct(
            c.requireString("companion"),
            c.requireString("factory"),
            dropTrailing = c.int("dropTrailing").getOrElse(0),
            fillTypeArgs = c.bool("fillTypeArgs").getOrElse(false),
            typeVarEvidence = c.string("typeVarEvidence"))
        else if c.string("forEach").isDefined then
          RetargetRewrite.ForEach(
            c.requireString("forEach"),
            c.int("arity").getOrElse(1))
        else if c.string("collect").isDefined then
          RetargetRewrite.Collect(
            c.requireString("collect"),
            c.string("into").getOrElse("lowlevel.util.DynamicArray"))
        else if c.strings("chain").isDefined then
          val members = c.strings("chain").get
          RetargetRewrite.Chain(
            members,
            parens = c.strings("parens").getOrElse(Nil).toSet,
            dropArgs = c.bool("dropArgs").getOrElse(false))
        else if c.string("fieldWrite").isDefined then
          RetargetRewrite.FieldWrite(
            mName,
            c.requireString("fieldWrite"))
        else if c.string("indexedField").isDefined then
          RetargetRewrite.IndexedField(
            c.requireString("indexedField"))
        else if c.string("template").isDefined then
          RetargetRewrite.Template(
            c.requireString("template"))
        else throw ConfigError(tbl.at(memberKey),
          "object entry must have 'boolDispatch', 'companion', 'forEach', 'collect', 'chain', 'fieldWrite', 'indexedField', or 'template'")
      else
        RetargetRewrite.Rename(tbl.requireString(memberKey))
    var rewrites = Map.empty[String, Map[(String, Int), RetargetRewrite]]
    var rewritesByDesc = Map.empty[String, Map[(String, Descriptor), RetargetRewrite]]
    config.child("retargetRewrites").foreach { rr =>
      rr.keys.foreach { srcFqn =>
        val tbl = rr.requireChild(srcFqn)
        var arityEntries = Map.empty[(String, Int), RetargetRewrite]
        var descEntries = Map.empty[(String, Descriptor), RetargetRewrite]
        tbl.keys.foreach { memberKey =>
          val parts = memberKey.split("/", 2)
          if parts.length != 2 then throw ConfigError(rr.at(srcFqn),
            s"retargetRewrites key '$memberKey' must be 'memberName/arity' or 'memberName/(descriptor)'")
          val mName = parts(0)
          val arityOrDesc = parts(1)
          val rw = parseRewrite(tbl, memberKey, mName)
          if arityOrDesc.startsWith("(") && arityOrDesc.endsWith(")") then
            val descStr = arityOrDesc.drop(1).dropRight(1)
            val params = if descStr.isEmpty then Nil
              else descStr.split(",").toList.map(Descriptor.paramOf)
            descEntries += (mName, Descriptor(params)) -> rw
          else
            val arity = try arityOrDesc.toInt catch
              case _: NumberFormatException => throw ConfigError(rr.at(srcFqn),
                s"retargetRewrites key '$memberKey': arity part must be an integer or (descriptor)")
            arityEntries += (mName, arity) -> rw
        }
        if arityEntries.nonEmpty then rewrites += srcFqn -> arityEntries
        if descEntries.nonEmpty then rewritesByDesc += srcFqn -> descEntries
      }
    }
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
      retargetRewritesByDesc = rewritesByDesc,
      retargetTypeArgs = typeArgs,
      reifiedCarriers  = config.strings("reifiedCarriers").getOrElse(Nil).toSet,
      reflectiveSinks  = config.strings("reflectiveSinks").getOrElse(Nil).toSet)

/** `{ transform = "public-field-accessors", scope { only = ["com.foo.Model"] } }`
  *
  * Adds `getX`/`setX` beside public fields for the scoped declarations. // ENGINE-LIMITS K21 face 2
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

/** `{ transform = "static-forwarder", forwarders = [ { wrapper, receiver, members } ] }` */
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

/** `{ transform = "remediation", classTables { "a.B#forName" = "c.D#classFor" } }`
  *
  * Portability menu. Location list is `manifest.resolutions`; `classTables` is the destination
  * table for `class-table`. `remedies` restates the menu for load-time validation. */
final class RemediationFactory extends TransformFactory:
  def name = RemediationTransform.Name
  override def remedies: List[Remedy] = RemediationTransform.Remedies
  // `targets` is read from `PortManifest.targets` via `RunScope.platform`, not from this transform.
  def fromConfig(config: ConfigView): Phase =
    new RemediationTransform(classTables = config.stringMap("classTables").getOrElse(Map.empty))

/** `{ transform = "class-table", redirects { "a.B#forName" = "c.D#classFor" }, scope { … } }`
  *
  * A REDIRECT, so its scope defaults to the unrestricted `Everywhere(Set.empty)` it ran under
  * before it had one (`.claude/rules/phases.md`). */
final class ClassTableFactory extends TransformFactory:
  def name = "class-table"
  def fromConfig(config: ConfigView): Phase =
    new ClassTableTransform(config.stringMap("redirects").getOrElse(Map.empty),
      TransformFactory.scopeOf(config, default = RuleScope.everywhere))

/** `.conf` shape for `type-redirect`: `redirects` maps upstream FQN to either a bare string (flat
  * form) or an object carrying `to`, `memberRenames`, `scope`. */
final class TypeRedirectFactory extends TransformFactory:
  def name = "type-redirect"
  def fromConfig(config: ConfigView): Phase =
    val entries = config.child("redirects").toList.flatMap(rs => rs.keys.map { k =>
      if !rs.isObject(k) then (k, rs.requireString(k), Map.empty[String, String], RuleScope.everywhere)
      else
        val e = rs.requireChild(k)
        // Per-entry scope: a base's whole-program redirect and a dependent's scoped one fold together.
        (k, e.requireString("to"), e.stringMap("memberRenames").getOrElse(Map.empty),
         TransformFactory.scopeOf(e))
    })
    new TypeRedirectTransform(
      redirects     = entries.map((k, to, _, _) => k -> to).toMap,
      memberRenames = entries.collect { case (k, _, rn, _) if rn.nonEmpty => k -> rn }.toMap,
      scopes        = entries.collect { case (k, _, _, sc) if !sc.isUnrestricted => k -> sc }.toMap)

/** `.conf` shape for `bean-properties`: `pairs` maps a member key to either an accessor-pair
  * string or an object adding `target` (def-pair | var | val). Empty `pairs` is a no-op. */
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
  *   renames { "a.VisWindow#close" = "closeWindow", "a.Stream#close(int)" = "closeAt" } }
  * ```
  * Key is member key in upstream namespace; value is bare target name. Empty = no-op. */
final class MemberRenameFactory extends TransformFactory:
  def name = MemberRenameTransform.Name
  def fromConfig(config: ConfigView): Phase =
    new MemberRenameTransform(config.stringMap("renames").getOrElse(Map.empty))

/** `{ transform = "method-body", bodies { "a.B#m()" = "{ … }" } }` */
final class MethodBodyFactory extends TransformFactory:
  def name = "method-body"
  def fromConfig(config: ConfigView): Phase =
    new MethodBodyTransform(config.stringMap("bodies").getOrElse(Map.empty))

/** `{ transform = "add-members", members { "owner.Fqn" = [ { name, arity, source, why? } ] } }`
  *
  * Each spec has `name`, `arity`, `source` (verbatim Scala), optional `why`. Owner is upstream FQN. */
final class AddMembersFactory extends TransformFactory:
  def name = "add-members"
  def fromConfig(config: ConfigView): Phase =
    val raw = config.children("members").getOrElse(Nil)
    val entries = raw.flatMap { obj =>
      val owner = obj.requireString("owner")
      val specs = obj.children("specs").getOrElse(Nil).map { s =>
        AddMembersTransform.MemberSpec(
          name   = s.requireString("name"),
          arity  = s.int("arity").getOrElse(0),
          source = s.requireString("source"),
          reason = Reason.Configured("add-members", s"$owner#${s.requireString("name")}"),
          why    = s.string("why"),
        )
      }
      if specs.nonEmpty then List(owner -> specs) else Nil
    }
    new AddMembersTransform(entries.toMap)

/** `{ transform = "call-site-substitution", calls { "a.B#m(int,String)" = "c.D.n({recv}, {arg0})" } }`
  *
  * Key is resolved callee; value is expression template with `{recv}`, `{arg0}`...`{argN}`. */
final class CallSiteSubstitutionFactory extends TransformFactory:
  def name = "call-site-substitution"
  def fromConfig(config: ConfigView): Phase =
    new CallSiteSubstitutionTransform(config.stringMap("calls").getOrElse(Map.empty))

/** `{ transform = "port-map-migration", bases = ["base-core"] }`
  *
  * Takes base module names; maps are discovered by `PortMap.published`. */
final class PortMapMigrationFactory extends TransformFactory:
  def name = "port-map-migration"
  def fromConfig(config: ConfigView): Phase =
    PortMapTransform.forBases(config.strings("bases").getOrElse(
      throw ConfigError(config.at("bases"),
        "required, and absent — with no base module named there is no published map to read, and " +
          "the phase would report nothing while looking as though it had checked"))*)

/** `.conf` shape for `primitive-to-opaque`: `fqn`, `underlying`, `hints`/`extraHints` (exact FQN
  * seeds, both reach the surface fingerprint), `scope`. */
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

/** `.conf` shape for `nullability`: `annotations`, `target` (union|named|option), `wrapper`
  * (required iff `target = "named"`, refused otherwise), `scope`. Empty `annotations` = no-op. */
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

/** `.conf` shape for `globals-to-implicits`: `holders` (each an entry with `holder`, `context`
  * — `inject`/`mint` — `members`, `attach`/`reader`/`boundary`, `sites`, `selfSupplied`,
  * `retain`, `cache`, `promoteToClass`, `scope`). No `context` block = an extension (dependent's
  * per-declaration keys only). `holders` is required; absent is refused. */
final class GlobalsToImplicitsFactory extends TransformFactory:
  def name = "globals-to-implicits"

  def fromConfig(config: ConfigView): Phase =
    val hs = config.children("holders").getOrElse(
      throw ConfigError(config.at("holders"),
        "required, and absent — with no holder named, the phase would find none and do nothing, " +
          "which is the §1(b) silent no-op this engine refuses"))
    // No `context` block = extension (dependent's per-declaration keys only). // ENGINE-LIMITS CT8
    val (exts, full) = hs.partition(_.child("context").isEmpty)
    val rg = config.stringMap("requiredGivens").getOrElse(Map.empty)
    new GlobalsToImplicitsTransform(full.map(holder), exts.map(extension), rg)

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

/** `{ transform = "class-to-trait", specs { "a.Pool" { params = [ { index, name } ] } } }` */
final class ClassToTraitFactory extends TransformFactory:
  def name = ClassToTraitTransform.Name
  def fromConfig(config: ConfigView): Phase =
    val specs = config.child("specs") match
      case Some(s) =>
        s.keys.map { fqn =>
          val paramViews = s.child(fqn).flatMap(_.children("params")).getOrElse(Nil)
          val params = paramViews.map { pc =>
            ClassToTraitTransform.ParamMapping(
              pc.int("index").getOrElse(throw ConfigError(pc.at("index"), "required, and absent")),
              pc.requireString("name"),
            )
          }
          fqn -> params
        }.toMap
      case None => Map.empty
    new ClassToTraitTransform(specs)

/** `{ transform = "registry", facadeMembers = [...], entries = [ { callee, placement { … }, scope,
  * seeds, handles, miss, bound } ] }`
  *
  * `placement` carries `object` OR `member` (the FQN) plus `table`/`register`/`create`; `miss` is
  * `"null"`, `"jvm-reflect"`, or `{ throw = "fqn", message = "…" }`. Empty `entries` is a no-op. */
final class RegistryFactory extends TransformFactory:
  def name = RegistryTransform.Name
  def fromConfig(config: ConfigView): Phase =
    val entries = config.children("entries").getOrElse(Nil).map { e =>
      val p  = e.requireChild("placement")
      val sp = RegistryTransform.Spelling(
        table    = p.requireString("table"),
        register = p.requireString("register"),
        create   = p.requireString("create"))
      val placement = (p.string("object"), p.string("member")) match
        case (Some(_), Some(_)) => throw ConfigError(p.path,
          "a placement is `object` OR `member`, never both — one mints a top-level object, the " +
            "other puts the registry on a type the port already emits")
        case (Some(o), scala.None) => RegistryTransform.Placement.Object(o, sp)
        case (scala.None, Some(m)) => RegistryTransform.Placement.Member(m, sp)
        case _ => throw ConfigError(p.path, "a placement declares `object` or `member`")
      val miss = e.child("miss") match
        // `jvmReflect` REFLECTS and answers this on a reflective failure; the bare form THROWS
        // for every unregistered key (`ENGINE-LIMITS.md` P10).
        case Some(t) => t.child("jvmReflect") match
          case Some(j) => RegistryTransform.Miss.JvmReflect(RegistryTransform.Miss.OnFailure.Throw(
            j.requireString("throw"), j.string("message").getOrElse("")))
          case scala.None =>
            RegistryTransform.Miss.Throw(t.requireString("throw"), t.string("message").getOrElse(""))
        case scala.None => e.enumerated("miss", Map(
          "null"        -> RegistryTransform.Miss.Null,
          "jvm-reflect" -> RegistryTransform.Miss.JvmReflect(RegistryTransform.Miss.OnFailure.Null),
        )).getOrElse(RegistryTransform.Miss.Null)
      RegistryTransform.Registry(
        callee    = e.requireString("callee"),
        placement = placement,
        // this phase MINTS, so its no-op — and its default — is `Only(Set.empty)` (§1(b)).
        scope     = TransformFactory.scopeOf(e, default = RuleScope.Only(Set.empty)),
        seeds     = e.strings("seeds").getOrElse(Nil),
        handles   = e.strings("handles").getOrElse(Nil).toSet,
        miss      = miss,
        bound     = e.string("bound"))
    }
    new RegistryTransform(entries, config.strings("facadeMembers").getOrElse(Nil).toSet)

/** `{ transform = "type-class-array", witness = "lowlevel.MkArray",
  *    members { create = "create", … }, subjects { "a.B" = [0, 1] }, dropBound = [ "a.B" ],
  *    scope { only = [ … ] } }` — empty `subjects` is the no-op (CLAUDE.md §1(b)). */
final class ElementWitnessFactory extends TransformFactory:
  def name = "type-class-array"
  def fromConfig(config: ConfigView): Phase =
    val subjects = config.child("subjects").map { c =>
      c.keys.map(k => k -> c.strings(k).getOrElse(Nil).map(_.trim.toInt)).toMap
    }.getOrElse(Map.empty)
    val m = config.child("members").map { c =>
      ElementWitnessTransform.Members(
        create       = c.string("create").getOrElse("create"),
        copyOf       = c.string("copyOf").getOrElse("copyOf"),
        copyOfRange  = c.string("copyOfRange").getOrElse("copyOfRange"),
        nullOut      = c.string("nullOut").getOrElse("nullOut"),
        nullOutRange = c.string("nullOutRange").getOrElse("nullOutRange"))
    }.getOrElse(ElementWitnessTransform.Members.Default)
    new ElementWitnessTransform(
      witness      = config.string("witness").getOrElse(""),
      members      = m,
      subjectTypes = subjects,
      dropBound    = config.strings("dropBound").getOrElse(Nil).toSet,
      scope        = TransformFactory.scopeOf(config, default = RuleScope.Only(Set.empty)))
