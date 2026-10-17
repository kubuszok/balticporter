package balticporter.frontend.ts.dedicated

import balticporter.frontend.ts.{ RastNode, RastValue }

/** JS string methods whose Scala namesakes answer differently: JS clamps an index or answers `undefined`, `""` or `NaN` past an end where Java throws, pads with a fill of several characters,
  * replaces the first occurrence of a literal, and throws a RangeError for a negative repeat count. Each is written out with JS's answer or refused.
  */
private[dedicated] trait BodyStrings extends BodyScope:

  /** The methods [[translateStringMethod]] renders on a `String` receiver. */
  protected val writtenOutStringMethods: Set[String] = Set("charAt", "charCodeAt", "substring", "repeat", "padStart", "padEnd")

  /** `recv.method(args)` for one of [[writtenOutStringMethods]], evaluating the receiver and then the arguments once, in order. */
  protected def translateStringMethod(node: RastNode, recv: RastNode, recvText: String, method: String, args: List[RastNode], argTexts: List[String]): String =
    def countArg(a: RastNode) = intLiteral(a).isDefined || exprType(a).contains("Int")
    if !exprType(recv).contains("String") then refuse(if method.startsWith("pad") || method == "repeat" then "js-string-pad" else "js-string-index")
    method match
      case "charAt" | "charCodeAt" =>
        if args.size > 1 then refuse("js-string-index")
        val idx = args.headOption.getOrElse(RastNode("NumericLiteral", 0, node.pos, value = Some(RastValue.Num(0))))
        stringCharAt(recv, idx, if method == "charAt" then "\"\"" else "Double.NaN", code = method == "charCodeAt")
      // JS clamps both bounds into the string (NaN is 0) and swaps them when the first is larger
      case "substring" =>
        if args.isEmpty || args.size > 2 then refuse("js-string-index")
        val bounds = args.zip(argTexts).map { (a, text) =>
          val t = exprType(a)
          if !(intLiteral(a).isDefined || t.exists(Set("Int", "Double"))) then refuse("js-string-index")
          // JS truncates toward zero and reads NaN as 0, as `toInt` does; an infinity saturates and is clamped alike
          if t.contains("Double") then s"($text).toInt" else text
        }
        val (s, a, b) = (freshLabel("s"), freshLabel("a"), freshLabel("b"))
        def clamp(v: String) = s"math.min(math.max($v, 0), $s.length)"
        s"{ val $s: String = $recvText; val $a: Int = ${clamp(bounds.headOption.getOrElse("0"))}; val $b: Int = ${bounds.lift(1).fold(s"$s.length")(clamp)}; " +
          s"$s.substring(math.min($a, $b), math.max($a, $b)) }"
      // JS throws a RangeError for a negative count, where Scala's `*` answers ""; an absent count is zero
      case "repeat" =>
        if args.size != 1 || !args.forall(countArg) then refuse("js-string-pad")
        if args.headOption.forall(intLiteral(_).isDefined) then s"$recvText * ${argTexts.headOption.getOrElse("0")}"
        else
          val n = freshLabel("n")
          s"$recvText * { val $n: Int = ${argTexts.head}; if ($n < 0) throw new RuntimeException(\"Invalid count value: \" + $n); $n }"
      // JS repeats a fill of several characters and cuts it to the missing length, and a "" fill pads nothing
      case _ =>
        if args.isEmpty || args.size > 2 || !args.headOption.exists(countArg) then refuse("js-string-pad")
        // a string value may be null standing for `undefined` (a space fill) or be JS's own null (the fill "null"): only a literal fill reads alike
        if args.lift(1).exists(_.kind != "StringLiteral") then refuse("js-string-pad")
        val (s, w, f, k) = (freshLabel("s"), freshLabel("w"), freshLabel("f"), freshLabel("k"))
        val pad          = s"($f * ($k / $f.length + 1)).substring(0, $k)"
        s"{ val $s: String = $recvText; val $w: Int = ${argTexts.headOption.getOrElse("0")}; val $f: String = ${argTexts.lift(1).getOrElse("\" \"")}; " +
          s"if ($w <= $s.length || $f.isEmpty) $s else { val $k = $w - $s.length; ${if method == "padStart" then s"$pad + $s" else s"$s + $pad"} } }"

  /** A JS string's character at `idx` as a one-character string, `absent` past either end (`undefined` for `s[i]`, `""` for `charAt`), or its code as a number (`NaN` past an end) when `code`.
    * The index must be an `Int`: a fractional one names no character for `s[i]`.
    */
  protected def stringCharAt(recv: RastNode, idx: RastNode, absent: String, code: Boolean = false): String =
    if !(intLiteral(idx).isDefined || exprType(idx).contains("Int")) then refuse("js-string-index")
    val (s, i) = (freshLabel("s"), freshLabel("i"))
    val ch     = if code then s"$s.charAt($i).toDouble" else s"$s.charAt($i).toString"
    s"{ val $s: String = ${translateExpr(recv)}; val $i: Int = ${translateExpr(idx)}; if ($i >= 0 && $i < $s.length) $ch else $absent }"

  /** JS `s.replace(p, r)`: a string pattern replaces its FIRST occurrence (`replaceAll`: every one), a `/regex/` its first match unless flagged `g`. Only a literal replacement is rendered: `$n` names
    * a group in both languages for a regex, every other `$` form and a backslash differ.
    */
  protected def translateStringReplace(objExpr: String, all: Boolean, args: List[RastNode]): String =
    def literal(n: RastNode): Option[String] = n.value.collect { case RastValue.Str(s) => s }.filter(_ => n.kind == "StringLiteral")
    args match
      case List(pat, rep) if literal(rep).isDefined =>
        val r = literal(rep).get
        pat.kind match
          case "StringLiteral" =>
            if r.contains('$') || r.contains('\\') then refuse("replace-replacement-pattern")
            val p = translateExpr(pat)
            if all then s"$objExpr.replace($p, ${translateExpr(rep)})"
            else s"$objExpr.replaceFirst(java.util.regex.Pattern.quote($p), ${translateExpr(rep)})"
          case "RegularExpressionLiteral" =>
            if r.contains('\\') || r.replaceAll("\\$[0-9]", "").contains('$') then refuse("replace-replacement-pattern")
            val flags = pat.value.collect { case RastValue.Str(s) => s }.orElse(pat.text).getOrElse("").reverse.takeWhile(_ != '/')
            val re    = translateExpr(pat)
            if all || flags.contains('g') then s"$re.replaceAllIn($objExpr, ${translateExpr(rep)})"
            else s"$re.replaceFirstIn($objExpr, ${translateExpr(rep)})"
          case _ =>
            refuse("replace-pattern-unknown")
            s"$objExpr.replace(${translateExpr(pat)}, ${translateExpr(rep)})"
      case _ =>
        refuse("replace-replacement-unknown")
        s"$objExpr.replace(${args.map(translateExpr).mkString(", ")})"
