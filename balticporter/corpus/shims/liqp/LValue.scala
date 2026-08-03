/*
 * Handwritten shim (Shim disposition, DESIGN.md §3.7) — NOT generated.
 * Minimal stand-in for liqp.LValue (out of the M0 20-file set); provides the
 * helper surface the in-set filters call. Replaced by the real translation
 * when LValue enters the ported set (M1).
 */
package liqp

abstract class LValue {

  def isInteger(value: Any): Boolean = value match {
    case _: Int | _: Long | _: java.math.BigInteger => true
    case _                                          => false
  }

  def canBeInteger(value: Any): Boolean = value match {
    case s: String => s.trim.matches("-?\\d+")
    case _         => isInteger(value)
  }

  def isNumber(value: Any): Boolean = value.isInstanceOf[Number]

  def canBeDouble(value: Any): Boolean = value match {
    case s: String => s.trim.matches("-?\\d+(\\.\\d+)?")
    case _         => isNumber(value)
  }

  def asNumber(value: Any): Number = value match {
    case n: Number => n
    case s: String => new java.math.BigDecimal(s.trim)
    case other     => throw new IllegalArgumentException("not a number: " + other)
  }

  def isString(value: Any): Boolean = value.isInstanceOf[String]

  def asString(value: Any, context: TemplateContext): String =
    if (value == null) "" else String.valueOf(value)
}

object LValue {
  def asFormattedNumber(bd: java.math.BigDecimal): Any = bd
}
