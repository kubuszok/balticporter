package balticporter.corpus.roughjs

import balticporter.frontend.ts.dedicated.{DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction}

/** Dedicated emitter for roughjs math.ts → RoughMath.scala + Random class. */
object RoughMathEmitter {
  def emit(): String = {
    val sb = new StringBuilder
    sb.append("package ssg.graphs.commons.rough\n\n")

    sb.append("object RoughMath {\n\n")
    sb.append("  private val unseededGenerator: scala.util.Random = new scala.util.Random()\n\n")
    sb.append("  private[rough] def unseededRandom(): Double =\n")
    sb.append("    unseededGenerator.nextDouble()\n\n")
    sb.append("  def randomSeed(): Int =\n")
    sb.append("    Math.floor(unseededRandom() * 2147483648.0).toInt\n")
    sb.append("}\n\n")

    sb.append("final class Random(private var seed: Int) {\n\n")
    sb.append("  def next(): Double =\n")
    sb.append("    if (seed != 0) {\n")
    sb.append("      seed = 48271 * seed\n")
    sb.append("      (0x7fffffff & seed) / 2147483648.0\n")
    sb.append("    } else {\n")
    sb.append("      RoughMath.unseededRandom()\n")
    sb.append("    }\n")
    sb.append("}\n")
    sb.toString
  }
}
