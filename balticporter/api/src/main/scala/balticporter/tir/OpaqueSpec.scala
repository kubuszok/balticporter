package balticporter.tir

/** One opaque type a port wants — minted by the phase, targeting an existing/injected type, or re-emitting a java constants class at its own name — as a value. Mint (default): synthesises `<fqn>.T`
  * with `apply`/`unwrap`. Existing: retargets to a type `Substitutions` already ships, via a companion `apply`/unwrap contract. OwnClass: the class `fqn` becomes `opaque type` + `object`.
  * `hints`/`extraHints` are exact FQN seeds; `scope` fences propagation — an `extraHints` entry outside it is reported, never silent.
  */
final case class OpaqueSpec(
  /** the generated `object`'s fully-qualified name (Mint) or the java class being replaced (Existing). Used as the phase name key and fingerprint identifier in both forms.
    */
  fqn: String,
  /** the port's own seed set — exact FQNs matched against `Symbol.fullName`. */
  hints: Set[String] = Set.empty,
  /** what the opaque type is a view OF. */
  underlying: OpaqueSpec.Primitive = OpaqueSpec.Primitive.Int,
  /** fully-qualified names an agent adds after reading a compile error. */
  extraHints: Set[String] = Set.empty,
  /** where seeding and propagation may reach. `Everywhere()` fences nothing. */
  scope: RuleScope = RuleScope.Everywhere(),
  /** whether the phase mints a new opaque type or targets an existing/injected one. `Mint` (default) synthesises the companion; `Existing(typeFqn, wrapName, unwrapName)` retypes against a type that
    * already exists; `OwnClass(wrapName, unwrapName)` turns the java class `fqn` itself into the opaque type.
    */
  target: OpaqueSpec.Target = OpaqueSpec.Target.Mint,
  /** also seed from the run's [[DerivedPolicy]] — the slots the reference port spells at this spec's target type (`RunScope.derived`). Off is the no-op.
    */
  derive: Boolean = false,
  /** FQNs of one-type-parameter wrappers (a nullability carrier, `lowlevel.Nullable`) whose element may be the primitive or its boxed form: a symbol typed `Carrier[Prim]`/`Carrier[Boxed]` is taggable
    * and retypes to `Carrier[Opaque]`, coerced through the carrier's `map`. One level only; the phase then runs after the null model, which is what puts the carrier in the program; empty is the no-op
    * (no edge either).
    */
  carriers: Set[String] = Set.empty
):
  require(
    carriers.forall(c => c.nonEmpty && !c.startsWith(".") && !c.endsWith(".") && !c.contains("..")),
    s"OpaqueSpec.carriers must be fully-qualified type names: $carriers"
  )
  // Refused LOUDLY at construction, because every one of these produces emitted Scala that is
  // wrong in a way no count would show.
  require(fqn.nonEmpty, "OpaqueSpec.fqn must not be empty")
  require(!fqn.startsWith(".") && !fqn.endsWith(".") && !fqn.contains(".."), s"OpaqueSpec.fqn is not a valid path: '$fqn'")
  require(!fqn.split('.').exists(_.isEmpty), s"OpaqueSpec.fqn has an empty segment: '$fqn'")
  // The Mint form mints a TOP-LEVEL unit, so `#`/`$` in the FQN would claim a nesting the mint
  // cannot produce; the OwnClass form re-emits a top-level class as a top-level opaque type, and a
  // nested opaque type has no home. The Existing form has no such constraint — the target is
  // whatever the injected file declares, and nested FQNs like `sge.Input.Key` are legitimate.
  target match
    case OpaqueSpec.Target.Mint | _: OpaqueSpec.Target.OwnClass =>
      require(
        !fqn.contains('#') && !fqn.contains('$'),
        s"OpaqueSpec.fqn must name a TOP-LEVEL type, not a nested type or a member: '$fqn'"
      )
    case _ => ()

  /** whether this spec mints its own companion (true) or targets an existing type (false). */
  def isMint: Boolean = target == OpaqueSpec.Target.Mint

  /** whether this spec re-emits the java class `fqn` itself as the opaque type. */
  def isOwnClass: Boolean = target.isInstanceOf[OpaqueSpec.Target.OwnClass]

  /** the generated object's simple name. For Mint and OwnClass, derived from `fqn`; for Existing, from the target's type FQN.
    */
  def objectName: String = target match
    case OpaqueSpec.Target.Existing(t, _, _) => t.substring(t.lastIndexOf('.') + 1)
    case _                                   => fqn.substring(fqn.lastIndexOf('.') + 1)

  /** the package the object lives in — `""` for the default package. For Mint and OwnClass, derived from `fqn`; for Existing, from the target's type FQN.
    */
  def packageName: String =
    val n = target match
      case OpaqueSpec.Target.Existing(t, _, _) => t
      case _                                   => fqn
    if n.contains('.') then n.substring(0, n.lastIndexOf('.')) else ""

  /** the opaque type's fully-qualified name. For Mint, `<fqn>.T`; for Existing, the target's own FQN; for OwnClass, the class's own FQN (the type IS the name, not a member called `T`).
    */
  def typeFqn: String = target match
    case OpaqueSpec.Target.Mint              => s"$fqn.T"
    case OpaqueSpec.Target.Existing(t, _, _) => t
    case _: OpaqueSpec.Target.OwnClass => fqn

  /** the underlying primitive's Scala FQN, e.g. `scala.Int`. */
  def underlyingFqn: String = underlying.scalaFqn

object OpaqueSpec:

  /** Whether the phase mints the opaque type or targets an existing/injected one. Mint (default): synthesises `object <fqn> { opaque type T = Prim; def apply; … }`. Existing: the type already exists
    * (injected, java replaced via `Substitutions`); the phase retypes and coerces through its declared wrap/unwrap methods, minting no companion.
    */
  sealed trait Target
  object Target:
    /** The phase mints the companion with `opaque type T`, `apply`, `unwrap`, and optional array coercions. This is the default form.
      */
    case object Mint extends Target

    /** The opaque type already EXISTS — an injected replacement — and the phase retypes to its FQN.
      * @param typeFqn
      *   the existing type's FQN, nested forms supported (the `$`/`#` restriction is about the MINT) @param wrapName the companion wrap method, default `"apply"`
      * @param unwrapName
      *   the companion/extension unwrap method — no default, it names a fact about the injected file, not the engine.
      */
    final case class Existing(
      typeFqn:    String,
      wrapName:   String = "apply",
      unwrapName: String
    ) extends Target:
      require(typeFqn.nonEmpty, "OpaqueSpec.Target.Existing.typeFqn must not be empty")
      require(
        !typeFqn.startsWith(".") && !typeFqn.endsWith(".") && !typeFqn.contains(".."),
        s"OpaqueSpec.Target.Existing.typeFqn is not a valid path: '$typeFqn'"
      )
      require(wrapName.nonEmpty, "OpaqueSpec.Target.Existing.wrapName must not be empty")
      require(unwrapName.nonEmpty, "OpaqueSpec.Target.Existing.unwrapName must not be empty")

      /** the companion object's FQN — the last `.`-separated segment is the type, so the companion shares the same FQN (Scala's companion is at the same path as the type).
        */
      def companionFqn: String = typeFqn

    /** The java CONSTANTS CLASS `fqn` itself becomes the opaque type, at its own name: `opaque type N = Prim` plus `object N` holding its statics. Its `static final` primitives are typed `N`; a
      * static method taking the primitive FIRST becomes an extension on `N`, its calls rewritten `a.m(…)`. Refused and counted when the class has instance members, is constructed, subclassed or named
      * as a type; the coercions are two members minted into the object under these names.
      */
    final case class OwnClass(wrapName: String = "apply", unwrapName: String = "unwrap") extends Target:
      require(
        wrapName.nonEmpty && unwrapName.nonEmpty && wrapName != unwrapName,
        s"OpaqueSpec.Target.OwnClass needs two distinct, non-empty member names: '$wrapName', '$unwrapName'"
      )

  /** What a `Target.OwnClass` conversion leaves on the symbols it re-shapes, for the emitter to render. */
  enum OwnClassTag extends SymTag:
    /** on the class: emitted as `opaque type N = underlying` plus `object N` with the class's statics. */
    case Opaque(underlying: TypeRepr)

    /** on a static method: its first parameter is the receiver of an `extension`; calls outside the object read `a.m(…)`. */
    case Extension

    /** on a constant: `inline def c: N = <literal at underlying>`, so reading it runs no initialiser, as javac's inlining does. */
    case InlineConstant(underlying: TypeRepr)

    /** on the two minted coercions: `inline def`, so wrapping or unwrapping a constant does not initialise the object either. */
    case InlineCoercion

  object OwnClassTag:
    def opaqueOf(s:         Symbol): Option[TypeRepr] = s.tags.collectFirst { case Opaque(u) => u }
    def isExtension(s:      Symbol): Boolean          = s.tags.contains(Extension)
    def inlineConstantOf(s: Symbol): Option[TypeRepr] = s.tags.collectFirst { case InlineConstant(u) => u }
    def isInlineCoercion(s: Symbol): Boolean          = s.tags.contains(InlineCoercion)

  /** The primitives an opaque type can be a view of. A CLOSED enum, so "cannot work" is unrepresentable rather than a runtime check — all eight of Scala's value types work (mechanism is
    * `opaque type T = P` + `apply`/`unwrap`, indifferent to `P`). `Unit` is deliberately absent (one inhabitant is not a domain value). [[fromScalaName]] is the loud door for a caller holding a
    * string.
    */
  enum Primitive(val scalaFqn: String, val boxedFqn: String):
    case Int extends Primitive("scala.Int", "java.lang.Integer")
    case Long extends Primitive("scala.Long", "java.lang.Long")
    case Float extends Primitive("scala.Float", "java.lang.Float")
    case Double extends Primitive("scala.Double", "java.lang.Double")
    case Byte extends Primitive("scala.Byte", "java.lang.Byte")
    case Short extends Primitive("scala.Short", "java.lang.Short")
    case Char extends Primitive("scala.Char", "java.lang.Character")
    case Boolean extends Primitive("scala.Boolean", "java.lang.Boolean")

  object Primitive:
    /** `"scala.Int"` / `"Int"` → [[Primitive.Int]]. Anything else throws, naming what is available — a silently-ignored primitive would leave the phase inert with nothing said.
      */
    def fromScalaName(name: String): Primitive =
      val n = name.stripPrefix("scala.")
      Primitive.values
        .find(_.toString == n)
        .getOrElse(
          throw new IllegalArgumentException(
            s"'$name' is not a primitive an opaque type can be a view of; available: " +
              Primitive.values.map(_.scalaFqn).mkString(", ")
          )
        )
