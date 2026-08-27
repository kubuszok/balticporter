package balticporter.tir

/** ONE opaque type a port wants — either MINTED by the phase or TARGETING an existing/injected type,
  * declared as a value.
  *
  * This is the configuration half of [[balticporter.tir.OpaqueSpec]]'s transform — the mechanism
  * (seed, propagate, retype, coerce at the boundary) is engine code and shared, and everything a
  * port has to say is here. A porting program in another repository (CLAUDE.md §4.45) constructs
  * one, which is why it lives in `api` and not beside the phase.
  *
  * ==Two forms: Mint and Existing==
  *
  * '''Mint''' (the default, today's behaviour): the phase synthesises a new companion object with
  * `opaque type T`, `apply`, `unwrap`, and optional array coercions. `fqn` names the generated
  * object and `<fqn>.T` is the opaque type's FQN.
  *
  * {{{
  * OpaqueSpec(
  *   fqn        = "sge.gl.GlHandle",           // object GlHandle; the type is GlHandle.T
  *   hints      = Set("com.example.GLTexture#glHandle"),
  *   underlying = OpaqueSpec.Primitive.Int,
  *   scope      = RuleScope.Only(Set("sge.gl")),
  * )
  * }}}
  *
  * '''Existing''' (`ENGINE-LIMITS.md` O6 CLOSED): the opaque type ALREADY EXISTS — an injected
  * replacement whose definition is supplied by `Substitutions` (drop + inject). The java CLASS the
  * opaque replaces is handled by `dropTypes`; the retype points at the existing type's FQN. The
  * contract the existing type must satisfy: a companion `apply(prim): OpaqueType` for wrapping and
  * a named method for unwrapping (an extension method callable as a companion method in FQN form).
  *
  * {{{
  * OpaqueSpec(
  *   fqn        = "com.badlogic.gdx.utils.Align",   // the java class being replaced
  *   target     = OpaqueSpec.Target.Existing(
  *     typeFqn    = "sge.utils.Align",               // the existing opaque type's FQN
  *     wrapName   = "apply",                         // Align(rawInt) — companion method
  *     unwrapName = "toInt",                         // Align.toInt(v) — extension/companion method
  *   ),
  *   hints      = Set("com.badlogic.gdx.scenes.scene2d.Actor#setOrigin(int)"),
  *   underlying = OpaqueSpec.Primitive.Int,
  * )
  * }}}
  *
  * Nested FQNs are supported for the Existing form: `sge.Input.Key` names a type member of
  * `object Input` and is NOT constrained to be a top-level unit (the mint form's `$`/`#`
  * restriction does not apply, because no unit is minted).
  *
  * ==The FQN in the Mint form is also the DEFINITION SITE, and that is not a shortcut==
  * `fqn` names the generated `object`, and the opaque type is [[typeFqn]] — `<fqn>.T`. The package
  * the object is minted into is the FQN's prefix, and the file follows from the package, because
  * Scala gives no other option: an `object com.foo.GlHandle` cannot be declared in a file that
  * belongs to package `c.d`. A second "where does it live" knob could therefore only ever hold a
  * value that agrees with this one or a value that is wrong, so there is no second knob. An FQN
  * with no `.` puts the object at the top level of the default package, which is what
  * `IntToOpaqueTransform("Layer", …)` did and is the default this replaces.
  *
  * ==Seeds and the fence==
  *   - [[hints]] is the port's own seed set — fully-qualified names matched against
  *     `Symbol.fullName`. This is §1(c) in its purest form, since WHICH `int`s are really a GL
  *     handle is knowledge about one library and nothing else. The set is RENDERABLE into the
  *     surface fingerprint, closing `ENGINE-LIMITS.md` §13 O4 (the predicate form that preceded
  *     this had no stable rendering and made two specs differing only in their seeds compare equal).
  *   - [[extraHints]] is the agent-in-the-loop escape hatch: when the emitted Scala fails to
  *     compile, an agent reads the error, adds the fully-qualified name of the missed declaration,
  *     and the next run re-propagates with it. Same format as `hints` — both are exact FQNs.
  *   - [[scope]] FENCES both. A `RuleScope` here bounds where the propagation may reach and which
  *     seeds may fire at all, so a port can say "these ints are handles, but only inside `sge.gl`"
  *     — which matters because a pure-move chain crosses type boundaries freely and one careless
  *     hint can pull half a library in. `RuleScope.Everywhere()` — the default — fences nothing.
  *
  * Note what the fence does to an [[extraHints]] entry outside it: NOTHING FIRES. That is
  * deliberate — a fence a named entry could step over is not a fence — but it is also the one way
  * this value can surprise its author, so the transform reports such an entry rather than leaving
  * the agent to wonder why its escape hatch did nothing.
  */
final case class OpaqueSpec(
    /** the generated `object`'s fully-qualified name (Mint) or the java class being replaced
      * (Existing). Used as the phase name key and fingerprint identifier in both forms. */
    fqn: String,
    /** the port's own seed set — exact FQNs matched against `Symbol.fullName`. §1(c). */
    hints: Set[String] = Set.empty,
    /** what the opaque type is a view OF. */
    underlying: OpaqueSpec.Primitive = OpaqueSpec.Primitive.Int,
    /** fully-qualified names an agent adds after reading a compile error. */
    extraHints: Set[String] = Set.empty,
    /** where seeding and propagation may reach. `Everywhere()` fences nothing. */
    scope: RuleScope = RuleScope.Everywhere(),
    /** whether the phase MINTS a new opaque type or TARGETS an existing/injected one.
      * `Mint` (default) synthesises the companion; `Existing(typeFqn, wrapName, unwrapName)`
      * retypes against a type that already exists (`ENGINE-LIMITS.md` O6 CLOSED). */
    target: OpaqueSpec.Target = OpaqueSpec.Target.Mint,
):
  // Refused LOUDLY at construction, because every one of these produces emitted Scala that is
  // wrong in a way no count would show.
  require(fqn.nonEmpty, "OpaqueSpec.fqn must not be empty")
  require(!fqn.startsWith(".") && !fqn.endsWith(".") && !fqn.contains(".."),
    s"OpaqueSpec.fqn is not a valid path: '$fqn'")
  require(!fqn.split('.').exists(_.isEmpty), s"OpaqueSpec.fqn has an empty segment: '$fqn'")
  // The Mint form mints a TOP-LEVEL unit, so `#`/`$` in the FQN would claim a nesting the mint
  // cannot produce. The Existing form has no such constraint — the target is whatever the injected
  // file declares, and nested FQNs like `sge.Input.Key` are legitimate.
  target match
    case OpaqueSpec.Target.Mint =>
      require(!fqn.contains('#') && !fqn.contains('$'),
        s"OpaqueSpec.fqn must name a TOP-LEVEL object, not a nested type or a member: '$fqn'")
    case _ => ()

  /** whether this spec mints its own companion (true) or targets an existing type (false). */
  def isMint: Boolean = target == OpaqueSpec.Target.Mint

  /** the generated object's simple name. For Mint, derived from `fqn`; for Existing, from the
    * target's type FQN. */
  def objectName: String = target match
    case OpaqueSpec.Target.Mint => fqn.substring(fqn.lastIndexOf('.') + 1)
    case OpaqueSpec.Target.Existing(t, _, _) => t.substring(t.lastIndexOf('.') + 1)

  /** the package the object lives in — `""` for the default package. For Mint, derived from `fqn`;
    * for Existing, from the target's type FQN. */
  def packageName: String =
    val n = target match
      case OpaqueSpec.Target.Mint => fqn
      case OpaqueSpec.Target.Existing(t, _, _) => t
    if n.contains('.') then n.substring(0, n.lastIndexOf('.')) else ""

  /** the opaque type's fully-qualified name. For Mint, `<fqn>.T`; for Existing, the target's own
    * FQN (the type IS the name, not a member called `T`). */
  def typeFqn: String = target match
    case OpaqueSpec.Target.Mint => s"$fqn.T"
    case OpaqueSpec.Target.Existing(t, _, _) => t

  /** the underlying primitive's Scala FQN, e.g. `scala.Int`. */
  def underlyingFqn: String = underlying.scalaFqn

object OpaqueSpec:

  /** Whether the phase MINTS the opaque type or TARGETS an existing/injected one.
    *
    * ==Mint (default)==
    * The phase synthesises `object <fqn> { opaque type T = Prim; def apply(v: Prim): T; … }`.
    * This is the behaviour that existed before O6 and the one every configured spec uses today.
    *
    * ==Existing (`ENGINE-LIMITS.md` O6 CLOSED)==
    * The opaque type already exists — an injected file declares it, and the java class it replaces
    * is handled by `Substitutions` (drop + inject). The phase retypes declarations to the existing
    * type's FQN and coerces through its declared wrap/unwrap methods. No companion is minted.
    *
    * The contract the existing type must satisfy (the same shape
    * `NullabilityTransform.Target.Named` states for its five members):
    *   - a companion method `<wrapName>(prim): OpaqueType` — wraps a raw primitive into the type
    *   - a companion method or extension `<unwrapName>(opaque): Prim` — unwraps back to the
    *     primitive, callable in FQN form as `Companion.unwrapName(value)` */
  sealed trait Target
  object Target:
    /** The phase mints the companion with `opaque type T`, `apply`, `unwrap`, and optional array
      * coercions. This is the default and the only form that existed before O6. */
    case object Mint extends Target

    /** The opaque type already EXISTS — an injected replacement — and the phase retypes to its FQN.
      *
      * @param typeFqn the existing opaque type's fully-qualified name, e.g. `"sge.utils.Align"` or
      *   `"sge.Input.Key"` (nested FQNs are supported — the `$`/`#` restriction is about the MINT,
      *   not about a target somebody already wrote).
      * @param wrapName the companion method that wraps the primitive, e.g. `"apply"` so the emitted
      *   code reads `Align(rawInt)`. Default `"apply"`.
      * @param unwrapName the companion method or extension that unwraps back to the primitive, e.g.
      *   `"toInt"` so the emitted code reads `Align.toInt(value)`. No default — the caller must
      *   name it, because the method name is a fact about the injected file, not about the engine. */
    final case class Existing(
        typeFqn: String,
        wrapName: String = "apply",
        unwrapName: String,
    ) extends Target:
      require(typeFqn.nonEmpty, "OpaqueSpec.Target.Existing.typeFqn must not be empty")
      require(!typeFqn.startsWith(".") && !typeFqn.endsWith(".") && !typeFqn.contains(".."),
        s"OpaqueSpec.Target.Existing.typeFqn is not a valid path: '$typeFqn'")
      require(wrapName.nonEmpty, "OpaqueSpec.Target.Existing.wrapName must not be empty")
      require(unwrapName.nonEmpty, "OpaqueSpec.Target.Existing.unwrapName must not be empty")

      /** the companion object's FQN — the last `.`-separated segment is the type, so the companion
        * shares the same FQN (Scala's companion is at the same path as the type). */
      def companionFqn: String = typeFqn

  /** The primitives an opaque type can be a view of.
    *
    * A CLOSED enum rather than a string, so "this primitive cannot work" is unrepresentable instead
    * of being a runtime check somebody has to remember to write. All eight of Scala's value types
    * that a Java primitive maps to are here and all eight work: the mechanism is `opaque type T =
    * P` plus `apply`/`unwrap`, which is indifferent to `P`, and the coercion sites are decided from
    * the TIR rather than from arithmetic that only `Int` has. `Unit` is deliberately absent — a
    * domain value with one inhabitant is not a domain value.
    *
    * [[fromScalaName]] is the loud door for a caller holding a string. */
  enum Primitive(val scalaFqn: String):
    case Int     extends Primitive("scala.Int")
    case Long    extends Primitive("scala.Long")
    case Float   extends Primitive("scala.Float")
    case Double  extends Primitive("scala.Double")
    case Byte    extends Primitive("scala.Byte")
    case Short   extends Primitive("scala.Short")
    case Char    extends Primitive("scala.Char")
    case Boolean extends Primitive("scala.Boolean")

  object Primitive:
    /** `"scala.Int"` / `"Int"` → [[Primitive.Int]]. Anything else THROWS, naming what is
      * available — a silently-ignored primitive would leave the phase inert with nothing said,
      * which is the §1(b) silent-no-op failure one layer down. */
    def fromScalaName(name: String): Primitive =
      val n = name.stripPrefix("scala.")
      Primitive.values.find(_.toString == n).getOrElse(
        throw new IllegalArgumentException(
          s"'$name' is not a primitive an opaque type can be a view of; available: " +
            Primitive.values.map(_.scalaFqn).mkString(", ")))
