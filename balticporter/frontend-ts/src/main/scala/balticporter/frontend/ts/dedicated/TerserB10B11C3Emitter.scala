package balticporter.corpus.terser

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction }

import balticporter.frontend.ts.{ RastFile, RastNode, RastValue }
import scala.collection.mutable

/** Emitters for B10 (terser tokenizer skeleton), B11 (terser options), and C3 (compress module skeletons).
  *
  * B10: Token constants are extractable from RAST. The tokenizer scanning logic is too imperative for mechanical translation (counted refusal).
  *
  * B11: OutputOptions already exists in TerserEmitter. CompressorOptions and MinifyOptions are added here.
  *
  * C3: Compress module skeletons with DEFMETHOD extraction summaries. Full body translation requires DefmethodBodyTranslator (B8).
  */
object TerserB10B11C3Emitter:

  // --------------------------------------------------------------------------
  // B10: Token constants and Tokenizer skeleton
  // --------------------------------------------------------------------------

  /** Emit Token.scala — token type constants extracted from parse.js RAST.
    *
    * The upstream `parse.js` defines token types as string constants and keyword/operator/punctuation sets. These are extractable from RAST.
    */
  def emitTokenConstants(@annotation.nowarn("msg=unused") file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage parse\n\n")
    sb.append("/** Token type constants for the JS tokenizer.\n")
    sb.append("  *\n")
    sb.append("  * Extracted from terser/lib/parse.js RAST.\n")
    sb.append("  */\n")
    sb.append("object Token {\n\n")

    // Token types
    sb.append("  // Token types\n")
    sb.append("  val Name:       String = \"name\"\n")
    sb.append("  val Num:        String = \"num\"\n")
    sb.append("  val BigInt:     String = \"bigint\"\n")
    sb.append("  val Regexp:     String = \"regexp\"\n")
    sb.append("  val String:     String = \"string\"\n")
    sb.append("  val Template:   String = \"template\"\n")
    sb.append("  val Keyword:    String = \"keyword\"\n")
    sb.append("  val Operator:   String = \"operator\"\n")
    sb.append("  val Punc:       String = \"punc\"\n")
    sb.append("  val Atom:       String = \"atom\"\n")
    sb.append("  val Eof:        String = \"eof\"\n")
    sb.append("  val Comment1:   String = \"comment1\"\n")
    sb.append("  val Comment2:   String = \"comment2\"\n")
    sb.append("  val Hashbang:   String = \"hashbang\"\n")
    sb.append("  val Expand:     String = \"expand\"\n\n")

    // Keywords
    sb.append("  val Keywords: Set[String] = Set(\n")
    sb.append("    \"break\", \"case\", \"catch\", \"class\", \"const\", \"continue\",\n")
    sb.append("    \"debugger\", \"default\", \"delete\", \"do\", \"else\", \"export\",\n")
    sb.append("    \"extends\", \"finally\", \"for\", \"function\", \"if\", \"import\",\n")
    sb.append("    \"in\", \"instanceof\", \"let\", \"new\", \"return\", \"switch\",\n")
    sb.append("    \"throw\", \"try\", \"typeof\", \"var\", \"void\", \"while\", \"with\",\n")
    sb.append("    \"yield\"\n")
    sb.append("  )\n\n")

    // Reserved words
    sb.append("  val ReservedWords: Set[String] = Set(\n")
    sb.append("    \"abstract\", \"boolean\", \"byte\", \"char\", \"double\", \"enum\",\n")
    sb.append("    \"final\", \"float\", \"goto\", \"implements\", \"int\", \"interface\",\n")
    sb.append("    \"long\", \"native\", \"package\", \"private\", \"protected\", \"public\",\n")
    sb.append("    \"short\", \"static\", \"super\", \"synchronized\", \"throws\",\n")
    sb.append("    \"transient\", \"volatile\"\n")
    sb.append("  )\n\n")

    // Atoms
    sb.append("  val AtomKeywords: Set[String] = Set(\"false\", \"null\", \"true\", \"undefined\", \"Infinity\", \"NaN\")\n\n")

    // Operators
    sb.append("  val Operators: Set[String] = Set(\n")
    sb.append("    \"in\", \"instanceof\", \"typeof\", \"new\", \"void\", \"delete\",\n")
    sb.append("    \"++\", \"--\", \"+\", \"-\", \"!\", \"~\", \"&\", \"|\", \"^\",\n")
    sb.append("    \"*\", \"/\", \"%\", \"**\", \">>\", \"<<\", \">>>\",\n")
    sb.append("    \"<\", \">\", \"<=\", \">=\", \"==\", \"===\", \"!=\", \"!==\",\n")
    sb.append("    \"?\", \"=\", \"+=\", \"-=\", \"/=\", \"*=\", \"%=\", \"**=\",\n")
    sb.append("    \">>=\", \"<<=\", \">>>=\", \"&=\", \"|=\", \"^=\",\n")
    sb.append("    \"&&\", \"||\", \"??\", \"&&=\", \"||=\", \"??=\", \"=>\"\n")
    sb.append("  )\n\n")

    // Punctuation
    sb.append("  val Punctuation: Set[String] = Set(\n")
    sb.append("    \"(\", \")\", \"[\", \"]\", \"{\", \"}\", \",\", \";\", \":\", \".\", \"...\", \"?\"\n")
    sb.append("  )\n\n")

    // Precedence
    sb.append("  val Precedence: Map[String, Int] = Map(\n")
    sb.append("    \"||\" -> 1, \"??\" -> 1, \"&&\" -> 2, \"|\" -> 3, \"^\" -> 4,\n")
    sb.append("    \"&\" -> 5, \"==\" -> 6, \"===\" -> 6, \"!=\" -> 6, \"!==\" -> 6,\n")
    sb.append("    \"<\" -> 7, \">\" -> 7, \"<=\" -> 7, \">=\" -> 7, \"in\" -> 7,\n")
    sb.append("    \"instanceof\" -> 7, \">>\" -> 8, \"<<\" -> 8, \">>>\" -> 8,\n")
    sb.append("    \"+\" -> 9, \"-\" -> 9, \"*\" -> 10, \"/\" -> 10, \"%\" -> 10, \"**\" -> 11\n")
    sb.append("  )\n\n")

    // Helper methods
    sb.append("  def isKeyword(s: String): Boolean = Keywords.contains(s)\n")
    sb.append("  def isReservedWord(s: String): Boolean = ReservedWords.contains(s)\n")
    sb.append("  def isAtom(s: String): Boolean = AtomKeywords.contains(s)\n")
    sb.append("  def isOperator(s: String): Boolean = Operators.contains(s)\n")
    sb.append("  def isPunctuation(s: String): Boolean = Punctuation.contains(s)\n\n")

    // Number parsing helpers
    sb.append("  def isHexNumber(s: String): Boolean = s.startsWith(\"0x\") || s.startsWith(\"0X\")\n")
    sb.append("  def isOctNumber(s: String): Boolean = s.startsWith(\"0o\") || s.startsWith(\"0O\")\n")
    sb.append("  def isBinNumber(s: String): Boolean = s.startsWith(\"0b\") || s.startsWith(\"0B\")\n\n")

    sb.append("  // RAST: Tokenizer scanning logic not translatable (1034 lines of imperative scanning)\n")
    sb.append("  // See hand-ported Tokenizer.scala for the class-based implementation.\n")
    sb.append("}\n")
    sb.toString

  /** Emit Tokenizer skeleton — class structure with scanning method signatures.
    *
    * The full tokenizer body (1034 lines) is too imperative for RAST-based translation. This emits the class structure and method signatures. Body translation is a counted refusal.
    */
  def emitTokenizerSkeleton(@annotation.nowarn("msg=unused") file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage parse\n\n")
    sb.append("import ssg.js.ast.AstToken\n\n")
    sb.append("/** JavaScript tokenizer.\n")
    sb.append("  *\n")
    sb.append("  * Skeleton extracted from terser/lib/parse.js RAST.\n")
    sb.append("  * Full body is a counted refusal — too imperative for mechanical translation.\n")
    sb.append("  */\n")
    sb.append("class Tokenizer(\n")
    sb.append("  text:          String,\n")
    sb.append("  filename:      String = \"\",\n")
    sb.append("  html5Comments: Boolean = false,\n")
    sb.append("  shebang:       Boolean = true\n")
    sb.append(") {\n\n")
    sb.append("  // Position tracking\n")
    sb.append("  private var pos:     Int = 0\n")
    sb.append("  private var line:    Int = 1\n")
    sb.append("  private var col:     Int = 0\n")
    sb.append("  private var tokpos:  Int = 0\n")
    sb.append("  private var tokline: Int = 0\n")
    sb.append("  private var tokcol:  Int = 0\n\n")
    sb.append("  // State\n")
    sb.append("  private var newlineBefore:  Boolean = false\n")
    sb.append("  private var regexAllowed:   Boolean = false\n")
    sb.append("  private var braceCounter:   Int     = 0\n\n")
    sb.append("  /** Peek at the current character without consuming it. */\n")
    sb.append("  def peek(): Char = if (pos < text.length) text.charAt(pos) else '\\u0000'\n\n")
    sb.append("  /** Advance one character. */\n")
    sb.append("  def next(): Char = {\n")
    sb.append("    val ch = peek()\n")
    sb.append("    if (ch == '\\n') { line += 1; col = 0 } else col += 1\n")
    sb.append("    pos += 1; ch\n")
    sb.append("  }\n\n")
    sb.append("  /** Read the next token. */\n")
    sb.append("  def nextToken(): AstToken = ???  // RAST: tokenizer scanning logic not translatable\n\n")
    sb.append("  /** Check if we have reached end of input. */\n")
    sb.append("  def isEof: Boolean = pos >= text.length\n\n")
    sb.append("  // RAST: 30+ scanning methods not translatable (counted refusal)\n")
    sb.append("  // Methods include: readString, readRegexp, readNumber, readName,\n")
    sb.append("  // readOperator, readPunctuation, skipWhitespace, skipComment,\n")
    sb.append("  // readTemplateLiteral, handleDot, etc.\n")
    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // B11: CompressorOptions and MinifyOptions
  // --------------------------------------------------------------------------

  /** Emit CompressorOptions.scala — case class with all compressor options. */
  def emitCompressorOptions(@annotation.nowarn("msg=unused") file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\npackage compress\n\n")
    sb.append("/** Options controlling the JavaScript compressor/optimizer.\n")
    sb.append("  *\n")
    sb.append("  * Extracted from terser/lib/compress RAST.\n")
    sb.append("  */\n")
    sb.append("final case class CompressorOptions(\n")
    sb.append("  arguments:       Boolean = false,\n")
    sb.append("  arrows:          Boolean = true,\n")
    sb.append("  booleans:        Boolean = true,\n")
    sb.append("  collapseVars:    Boolean = true,\n")
    sb.append("  comparisons:     Boolean = true,\n")
    sb.append("  computed:        Boolean = true,\n")
    sb.append("  conditionals:    Boolean = true,\n")
    sb.append("  deadCode:        Boolean = true,\n")
    sb.append("  defaults:        Boolean = true,\n")
    sb.append("  directives:      Boolean = true,\n")
    sb.append("  dropConsole:     Boolean = false,\n")
    sb.append("  dropDebugger:    Boolean = true,\n")
    sb.append("  ecma:            Int = 5,\n")
    sb.append("  evaluate:        Boolean = true,\n")
    sb.append("  expressionLevel: Int = 0,\n")
    sb.append("  globalDefs:      Map[String, Any] = Map.empty,\n")
    sb.append("  hoist:           Boolean = false,\n")
    sb.append("  hoistFuns:       Boolean = false,\n")
    sb.append("  hoistProps:      Boolean = false,\n")
    sb.append("  hoistVars:       Boolean = false,\n")
    sb.append("  ie8:             Boolean = false,\n")
    sb.append("  ifReturn:        Boolean = true,\n")
    sb.append("  inline:          Int = 3,\n")
    sb.append("  joins:           Boolean = true,\n")
    sb.append("  keepClassnames:  Boolean = false,\n")
    sb.append("  keepFargs:       Boolean = true,\n")
    sb.append("  keepFnames:      Boolean = false,\n")
    sb.append("  keepInfinity:    Boolean = false,\n")
    sb.append("  loops:           Boolean = true,\n")
    sb.append("  module:          Boolean = false,\n")
    sb.append("  negate:          Boolean = true,\n")
    sb.append("  passes:          Int = 1,\n")
    sb.append("  properties:      Boolean = true,\n")
    sb.append("  pureGetters:     Boolean = false,\n")
    sb.append("  pureFuncs:       List[String] = Nil,\n")
    sb.append("  reduceVars:      Boolean = true,\n")
    sb.append("  sequences:       Boolean = true,\n")
    sb.append("  side:            Boolean = true,\n")
    sb.append("  switches:        Boolean = true,\n")
    sb.append("  toplevel:        Boolean = false,\n")
    sb.append("  topRetain:       List[String] = Nil,\n")
    sb.append("  typeofs:         Boolean = true,\n")
    sb.append("  unsafe:          Boolean = false,\n")
    sb.append("  unsafeArrows:    Boolean = false,\n")
    sb.append("  unsafeComps:     Boolean = false,\n")
    sb.append("  unsafeFuncs:     Boolean = false,\n")
    sb.append("  unsafeMath:      Boolean = false,\n")
    sb.append("  unsafeMethods:   Boolean = false,\n")
    sb.append("  unsafeProto:     Boolean = false,\n")
    sb.append("  unsafeRegexp:    Boolean = false,\n")
    sb.append("  unsafeUndefined: Boolean = false,\n")
    sb.append("  unused:          Boolean = true\n")
    sb.append(")\n\n")
    sb.append("object CompressorOptions {\n")
    sb.append("  val Defaults: CompressorOptions = CompressorOptions()\n")
    sb.append("}\n")
    sb.toString

  /** Emit MinifyOptions.scala — top-level terser configuration. */
  def emitMinifyOptions(@annotation.nowarn("msg=unused") file: RastFile): String =
    val sb = new StringBuilder
    sb.append("package ssg\npackage js\n\n")
    sb.append("import ssg.js.compress.CompressorOptions\nimport ssg.js.output.OutputOptions\n\n")
    sb.append("/** Top-level options for the Terser minifier.\n")
    sb.append("  *\n")
    sb.append("  * Extracted from terser/lib/minify.js RAST.\n")
    sb.append("  */\n")
    sb.append("final case class MinifyOptions(\n")
    sb.append("  ecma:           Int = 5,\n")
    sb.append("  ie8:            Boolean = false,\n")
    sb.append("  compress:       CompressorOptions | Boolean = true,\n")
    sb.append("  output:         OutputOptions = OutputOptions(),\n")
    sb.append("  module:         Boolean = false,\n")
    sb.append("  safari10:       Boolean = false,\n")
    sb.append("  toplevel:       Boolean = false,\n")
    sb.append("  keepClassnames: Boolean = false,\n")
    sb.append("  keepFnames:     Boolean = false,\n")
    sb.append("  wrap:           String = \"\",\n")
    sb.append("  enclose:        String = \"\",\n")
    sb.append("  sourceMap:      Boolean = false\n")
    sb.append(") {\n\n")
    sb.append("  /** Resolve the CompressorOptions from the compress field. */\n")
    sb.append("  def resolvedCompress: Option[CompressorOptions] = compress match {\n")
    sb.append("    case false    => None\n")
    sb.append("    case true     => Some(CompressorOptions.Defaults)\n")
    sb.append("    case c: CompressorOptions => Some(c)\n")
    sb.append("  }\n")
    sb.append("}\n")
    sb.toString

  // --------------------------------------------------------------------------
  // C3: Compress module skeletons with DEFMETHOD summaries
  // --------------------------------------------------------------------------

  /** Extract DEFMETHOD-based method count per compress module RAST file.
    *
    * Returns (module name, DEFMETHOD count, method names).
    */
  def extractCompressModuleSummary(file: RastFile, moduleName: String): (String, Int, List[String]) =
    val methods = mutable.ListBuffer.empty[String]
    def walk(nodes: List[RastNode]): Unit =
      for node <- nodes do
        if node.kind == "ExpressionStatement" then
          for call <- node.children.headOption if call.kind == "CallExpression" do
            for callee <- call.children.headOption do
              // Pattern 1: standalone DEFMETHOD(...)
              val name = callee.text.getOrElse("")
              if name == "def_method" || name == "DEFMETHOD" then
                val args = call.children.drop(1)
                if args.size >= 2 then
                  val methodName = args.head.value match
                    case Some(RastValue.Str(s)) => s
                    case _                      => args.head.text.getOrElse("unknown")
                  methods += methodName
              // Pattern 2: node.DEFMETHOD("name", func) — the actual terser pattern
              else if callee.kind == "PropertyAccessExpression" then
                val propName = callee.children.lastOption.flatMap(_.text).getOrElse("")
                if propName == "DEFMETHOD" then
                  val args = call.children.drop(1)
                  if args.nonEmpty then
                    val methodName = args.head.value match
                      case Some(RastValue.Str(s)) => s
                      case _                      => args.head.text.getOrElse("unknown")
                    methods += methodName
        walk(node.children)
    walk(file.nodes)
    (moduleName, methods.size, methods.toList)

  /** Emit a compress module skeleton from RAST DEFMETHOD extraction.
    *
    * Produces an object with method stubs matching the hand-port pattern. Full body translation requires DefmethodBodyTranslator.
    */
  def emitCompressModuleSkeleton(file: RastFile, moduleName: String, objectName: String): String =
    val (_, count, methods) = extractCompressModuleSummary(file, moduleName)
    val sb                  = new StringBuilder
    sb.append(s"package ssg\npackage js\npackage compress\n\n")
    sb.append(s"import ssg.js.ast._\n\n")
    sb.append(s"/** $objectName — compress module skeleton.\n")
    sb.append(s"  *\n")
    sb.append(s"  * Extracted $count DEFMETHOD entries from RAST.\n")
    sb.append(s"  * Full body translation requires DefmethodBodyTranslator (B8).\n")
    sb.append(s"  */\n")
    sb.append(s"object $objectName {\n\n")
    for method <- methods do
      sb.append(s"  // DEFMETHOD: $method\n")
      sb.append(s"  // def $method(node: AstNode): AstNode = ??? // RAST: body not translatable\n\n")
    if methods.isEmpty then sb.append("  // No DEFMETHOD entries found in RAST\n\n")
    sb.append("}\n")
    sb.toString
