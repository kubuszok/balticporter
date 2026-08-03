package balticporter.tir

/** ONE opaque type a port wants minted, declared as a value.
  *
  * This is the configuration half of [[balticporter.tir.OpaqueSpec]]'s transform — the mechanism
  * (seed, propagate, retype, coerce at the boundary) is engine code and shared, and everything a
  * port has to say is here. A porting program in another repository (CLAUDE.md §4.45) constructs
  * one, which is why it lives in `api` and not beside the phase.
  *
  * {{{
  * OpaqueSpec(
  *   fqn        = "sge.gl.GlHandle",           // object GlHandle; the type is GlHandle.T
  *   hints      = s => s.fullName.endsWith("#glHandle"),
  *   underlying = OpaqueSpec.Primitive.Int,
  *   scope      = RuleScope.Only(Set("sge.gl")),
  * )
  * }}}
  *
  * ==The FQN is also the DEFINITION SITE, and that is not a shortcut==
  * `fqn` names the generated `object`, and the opaque type is [[typeFqn]] — `<fqn>.T`. The package
  * the object is minted into is the FQN's prefix, and the file follows from the package, because
  * Scala gives no other option: an `object com.foo.GlHandle` cannot be declared in a file that
  * belongs to package `c.d`. A second "where does it live" knob could therefore only ever hold a
  * value that agrees with this one or a value that is wrong, so there is no second knob. An FQN
  * with no `.` puts the object at the top level of the default package, which is what
  * `IntToOpaqueTransform("Layer", …)` did and is the default this replaces.
  *
  * ==Seeds and the fence==
  *   - [[hints]] is the port's own predicate — §1(c) in its purest form, since WHICH `int`s are
  *     really a GL handle is knowledge about one library and nothing else.
  *   - [[extraHints]] is the agent-in-the-loop escape hatch: when the emitted Scala fails to
  *     compile, an agent reads the error, adds the fully-qualified name of the missed declaration,
  *     and the next run re-propagates with it.
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
    /** the generated `object`'s fully-qualified name; the opaque type is `<fqn>.T`. */
    fqn: String,
    /** the port's own seed predicate — §1(c). */
    hints: Symbol => Boolean,
    /** what the opaque type is a view OF. */
    underlying: OpaqueSpec.Primitive = OpaqueSpec.Primitive.Int,
    /** fully-qualified names an agent adds after reading a compile error. */
    extraHints: Set[String] = Set.empty,
    /** where seeding and propagation may reach. `Everywhere()` fences nothing. */
    scope: RuleScope = RuleScope.Everywhere(),
):
  // Refused LOUDLY at construction, because every one of these produces emitted Scala that is
  // wrong in a way no count would show: a name that cannot be a package path puts the object in a
  // file whose `package` clause does not parse, and a `#`/`$` path claims a nesting the mint cannot
  // produce (the object is minted as a TOP-LEVEL unit).
  require(fqn.nonEmpty, "OpaqueSpec.fqn must name the object to generate")
  require(!fqn.startsWith(".") && !fqn.endsWith(".") && !fqn.contains(".."),
    s"OpaqueSpec.fqn is not a package path: '$fqn'")
  require(!fqn.contains('#') && !fqn.contains('$'),
    s"OpaqueSpec.fqn must name a TOP-LEVEL object, not a nested type or a member: '$fqn'")
  require(!fqn.split('.').exists(_.isEmpty), s"OpaqueSpec.fqn has an empty segment: '$fqn'")

  /** the generated object's simple name. */
  def objectName: String = fqn.substring(fqn.lastIndexOf('.') + 1)

  /** the package the object and its companion are minted into — `""` for the default package. */
  def packageName: String = if fqn.contains('.') then fqn.substring(0, fqn.lastIndexOf('.')) else ""

  /** the opaque type's fully-qualified name. */
  def typeFqn: String = s"$fqn.T"

  /** the underlying primitive's Scala FQN, e.g. `scala.Int`. */
  def underlyingFqn: String = underlying.scalaFqn

object OpaqueSpec:

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
