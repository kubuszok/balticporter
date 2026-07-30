/*
 * HANDWRITTEN OVERRIDE (whole-file, DESIGN.md §3.8) — replaces engine translation of
 * liqp/src/main/java/liqp/exceptions/LiquidException.java.
 *
 * Reason: the three Java constructors call three DIFFERENT super constructors
 * (RuntimeException(msg,cause) / (msg) / (msg,cause)) — the shape Scala cannot
 * express with auxiliaries (DESIGN.md §4 trap 2). Funnel: private-shape primary
 * (message, line, charPositionInLine, cause); the ctx-based ctor passes cause=null.
 * Divergence note: Java's super(message) leaves the cause UNINITIALIZED (initCause
 * callable later); this encoding fixes it to null. No liqp code calls initCause.
 *
 * Original license: MIT (see Liqp upstream)
 */
package liqp.exceptions

import liquid.parser.v4.LiquidParser
import org.antlr.v4.runtime.*
import org.antlr.v4.runtime.misc.IntervalSet

@SerialVersionUID(1L)
class LiquidException(message: String, val line: Int, val charPositionInLine: Int, cause: Throwable)
    extends RuntimeException(message, cause) {

  def this(e: RecognitionException) =
    this(
      LiquidException.createMessage(e),
      e.getOffendingToken().getLine(),
      e.getOffendingToken().getCharPositionInLine(),
      e,
    )

  def this(message: String, ctx: ParserRuleContext) =
    this(message, ctx.start.getLine(), ctx.start.getCharPositionInLine(), null)
}

object LiquidException {

  private def createMessage(e: RecognitionException): String = {
    val offendingToken = e.getOffendingToken()
    val inputLines: Array[String] = e.getInputStream().toString().split("\r?\n|\r")
    val errorLine: String = inputLines(offendingToken.getLine() - 1)

    val message = new StringBuilder(
      String.format("\nError on line %s, column %s:\n", offendingToken.getLine(), offendingToken.getCharPositionInLine())
    )

    message.append(errorLine).append("\n")

    var i: Int = 0
    while (i < offendingToken.getCharPositionInLine()) {
      message.append(" ")
      i += 1
    }

    message.append("^")

    if (e.isInstanceOf[InputMismatchException]) {
      val ime = e.asInstanceOf[InputMismatchException]
      return String.format(
        "%s\nmatched '%s' as token <%s>, expecting token <%s>",
        message,
        offendingToken.getText(),
        tokenName(offendingToken.getType()),
        tokenNames(ime.getExpectedTokens()),
      )
    }

    if (e.isInstanceOf[FailedPredicateException]) {
      val fpe = e.asInstanceOf[FailedPredicateException]
      return String.format(
        "%s\nfailed predicate '%s' after position %s",
        message,
        fpe.getPredicate(),
        offendingToken.getCharPositionInLine(),
      )
    }

    if (e.isInstanceOf[NoViableAltException] || e.isInstanceOf[LexerNoViableAltException]) {
      return String.format("%s\ncould not decide what path to take, at position %s", message, offendingToken.getCharPositionInLine())
    }

    message.toString + "\nAn unknown error occurred!"
  }

  private def tokenName(`type`: Int): String = {
    if (`type` < 0) "<EOF>" else LiquidParser.VOCABULARY.getSymbolicName(`type`)
  }

  private def tokenNames(types: IntervalSet): String = {
    val typeList: java.util.List[Integer] = types.toList()
    val expectedBuilder = new StringBuilder()

    val t$it: java.util.Iterator[Integer] = typeList.iterator()
    while (t$it.hasNext) {
      val t: Integer = t$it.next()
      expectedBuilder.append(tokenName(t)).append(" ")
    }

    expectedBuilder.toString().trim()
  }
}
