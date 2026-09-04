package balticporter.tir

/** ONE opaque type a port wants — MINTED by the phase, or TARGETING an existing/injected type —
  * as a value (§4.45). MINT (default): synthesises `<fqn>.T` with `apply`/`unwrap`. EXISTING
  * (`ENGINE-LIMITS.md` O6): retargets to a type `Substitutions` already ships, via a companion
  * `apply`/unwrap contract. `hints`/`extraHints` are exact FQN seeds (§1c, renderable — O4);
  * `scope` FENCES propagation — an `extraHints` entry outside it is reported, never silent. */
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

  /** Whether the phase MINTS the opaque type or TARGETS an existing/injected one. Mint (default):
    * synthesises `object <fqn> { opaque type T = Prim; def apply; … }`. Existing
    * (`ENGINE-LIMITS.md` O6): the type already exists (injected, java replaced via
    * `Substitutions`); the phase retypes and coerces through its declared wrap/unwrap methods,
    * minting no companion. */
  sealed trait Target
  object Target:
    /** The phase mints the companion with `opaque type T`, `apply`, `unwrap`, and optional array
      * coercions. This is the default and the only form that existed before O6. */
    case object Mint extends Target

    /** The opaque type already EXISTS — an injected replacement — and the phase retypes to its FQN.
      * @param typeFqn the existing type's FQN, nested forms supported (the `$`/`#` restriction is
      *   about the MINT) @param wrapName the companion wrap method, default `"apply"`
      *   @param unwrapName the companion/extension unwrap method — no default, it names a fact
      *   about the injected file, not the engine. */
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

  /** The primitives an opaque type can be a view of. A CLOSED enum, so "cannot work" is
    * unrepresentable rather than a runtime check — all eight of Scala's value types work
    * (mechanism is `opaque type T = P` + `apply`/`unwrap`, indifferent to `P`). `Unit` is
    * deliberately absent (one inhabitant is not a domain value). [[fromScalaName]] is the loud
    * door for a caller holding a string. */
  enum Primitive(val scalaFqn: String, val boxedFqn: String):
    case Int     extends Primitive("scala.Int",     "java.lang.Integer")
    case Long    extends Primitive("scala.Long",    "java.lang.Long")
    case Float   extends Primitive("scala.Float",   "java.lang.Float")
    case Double  extends Primitive("scala.Double",  "java.lang.Double")
    case Byte    extends Primitive("scala.Byte",    "java.lang.Byte")
    case Short   extends Primitive("scala.Short",   "java.lang.Short")
    case Char    extends Primitive("scala.Char",    "java.lang.Character")
    case Boolean extends Primitive("scala.Boolean", "java.lang.Boolean")

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
