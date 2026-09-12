package balticporter.runtime.js

/** JavaScript truthiness semantics for TS→Scala lowering.
  *
  * JS treats `null`, `undefined`, `0`, `NaN`, `""`, and `false` as falsy;
  * everything else is truthy. Scala has no implicit boolean coercion, so
  * narrowing conditions in generated code call this helper. */
object Truthy:
  def apply(value: Any): Boolean = value match
    case null          => false
    case b: Boolean    => b
    case i: Int        => i != 0
    case l: Long       => l != 0L
    case d: Double     => d != 0.0 && !d.isNaN
    case f: Float      => f != 0.0f && !f.isNaN
    case s: String     => s.nonEmpty
    case _: Unit       => false
    case _             => true
