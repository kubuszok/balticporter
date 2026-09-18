package balticporter.corpus.terser

import balticporter.frontend.ts.dedicated.{ DefmethodBodyTranslator, DefmethodEntry, DefnodeClass, FreeFunction }

import scala.collection.mutable

/** Generates TypeScript declaration files (.d.ts) from a DEFNODE hierarchy and per-property type inference derived from a hand-ported reference.
  *
  * The generated declarations let the TypeScript checker type `this.x` accesses inside DEFMETHOD bodies, replacing the blanket `any` with concrete field types.
  */
object AstDtsGenerator:

  /** A reference field with its JS name and TypeScript type. */
  final case class DerivedField(
    jsName: String, // original JS field name (snake_case), e.g. "block_scope"
    tsType: String // TypeScript type for the .d.ts, e.g. "AST_Scope | null"
  )

  // --------------------------------------------------------------------------
  // Scala-type to TypeScript-type mapping
  // --------------------------------------------------------------------------

  /** Convert a Scala type string (from the reference port) to TypeScript. */
  def scalaTypeToTs(scalaType: String): String =
    scalaType.trim match
      case "Boolean"                           => "boolean"
      case "Int" | "Double" | "Float" | "Long" => "number"
      case "String"                            => "string"
      case "Any"                               => "any"
      case s"ArrayBuffer[$inner]"              => s"${scalaTypeToTs(inner)}[]"
      case s"$a | Null"                        => s"${scalaTypeToTs(a)} | null"
      case s"Null | $a"                        => s"${scalaTypeToTs(a)} | null"
      case s"$a | $b"                          => s"${scalaTypeToTs(a)} | ${scalaTypeToTs(b)}"
      case s"mutable.Map[$k, $v]"              => s"Map<${scalaTypeToTs(k)}, ${scalaTypeToTs(v)}>"
      case s"mutable.Set[$e]"                  => s"Set<${scalaTypeToTs(e)}>"
      case s"List[$inner]"                     => s"${scalaTypeToTs(inner)}[]"
      case other                               =>
        // Convert AstXxx -> AST_Xxx
        if other.startsWith("Ast") && other.length > 3 && other(3).isUpper then "AST_" + other.drop(3)
        else other

  // --------------------------------------------------------------------------
  // Property type inference (mirrors TerserEmitter.inferPropertyType)
  // --------------------------------------------------------------------------

  /** Infer the TypeScript type for a DEFNODE property.
    *
    * This mirrors the inference in TerserEmitter.inferPropertyType but returns a TypeScript type string directly. The logic is the same: map (propName, className) to a TypeScript type based on known
    * patterns from the reference port.
    */
  def inferTsFieldType(propName: String, className: String): DerivedField =
    val baseName = if propName.startsWith("_") then propName.drop(1) else propName

    // Boolean properties
    if Set("static", "logical", "optional", "await", "async").contains(baseName) ||
      baseName.startsWith("is_") ||
      baseName.startsWith("uses_")
    then DerivedField(propName, "boolean")
    // String properties
    else if Set("operator", "quote", "raw").contains(baseName) then DerivedField(propName, "string")
    // Int properties
    else if baseName == "annotations" || baseName == "cname" then DerivedField(propName, "number")
    // Body is array only on AST_Block
    else if baseName == "body" && className == "AST_Block" then DerivedField(propName, "AST_Node[]")
    // Array properties
    else if Set("args", "argnames", "elements", "properties", "expressions", "segments", "definitions", "names", "references").contains(baseName) then DerivedField(propName, "AST_Node[]")
    else if Set("imported_names", "exported_names").contains(baseName) then DerivedField(propName, "AST_Node[] | null")
    // Name is string for symbol and label classes
    else if baseName == "name" &&
      (className.contains("Symbol") ||
        className == "AST_Label" || className == "AST_LabelRef")
    then DerivedField(propName, "string")
    // Value is string for directive and template segment
    else if baseName == "value" &&
      (className == "AST_Directive" || className == "AST_TemplateSegment")
    then DerivedField(propName, "string")
    // Property as union type for prop access
    else if baseName == "property" then DerivedField(propName, "string | AST_Node")
    // Key as union type for object property classes
    else if baseName == "key" && className != "AST_PrivateIn" then DerivedField(propName, "string | AST_Node")
    // Scope-related types
    else if Set("block_scope", "scope", "parent_scope").contains(baseName) then DerivedField(propName, "AST_Scope | null")
    // Definition reference
    else if baseName == "thedef" then DerivedField(propName, "SymbolDef | null")
    else if baseName == "mangled_name" then DerivedField(propName, "string | null")
    // Scope data structures
    else if Set("variables", "globals").contains(baseName) then DerivedField(propName, "Map<string, SymbolDef>")
    else if baseName == "enclosed" then DerivedField(propName, "SymbolDef[]")
    else if baseName == "mangled_names" then DerivedField(propName, "Set<string>")
    // Default: single node reference
    else DerivedField(propName, "AST_Node | null")

  // --------------------------------------------------------------------------
  // .d.ts generation
  // --------------------------------------------------------------------------

  /** Generate a complete `.d.ts` string declaring all DEFNODE classes.
    *
    * Each class gets typed fields from `inferTsFieldType`, a `TYPE` string constant, and an extends clause matching the hierarchy. DEFMETHOD declarations for the major families (scope analysis,
    * equivalence, size estimation) are added as interface augmentations on `AST_Node`.
    */
  def generate(
    hierarchy:         List[TerserEmitter.DefnodeClass],
    defmethodFamilies: Map[String, List[DefmethodDecl]] = Map.empty,
    referenceFields:   Map[String, List[DerivedField]] = Map.empty
  ): String =
    val sb     = new StringBuilder
    val byName = hierarchy.map(c => c.varName -> c).toMap

    sb.append("// Auto-generated from DEFNODE hierarchy and reference port types.\n")
    sb.append("// Do not edit — regenerate with AstDtsGenerator.generate().\n\n")

    // Token class — exported so import { AST_Token } from "./ast" resolves
    sb.append("export declare class AST_Token {\n")
    sb.append("  type: string;\n")
    sb.append("  value: string;\n")
    sb.append("  line: number;\n")
    sb.append("  col: number;\n")
    sb.append("  pos: number;\n")
    sb.append("  nlb: boolean;\n")
    sb.append("  quote: string;\n")
    sb.append("  comments_before: AST_Token[];\n")
    sb.append("  comments_after: AST_Token[];\n")
    sb.append("  file: string;\n")
    sb.append("}\n\n")

    // SymbolDef forward declaration
    sb.append("export declare class SymbolDef {\n")
    sb.append("  name: string;\n")
    sb.append("  orig: AST_Symbol[];\n")
    sb.append("  init: AST_Node | null;\n")
    sb.append("  scope: AST_Scope;\n")
    sb.append("  references: AST_Symbol[];\n")
    sb.append("  global: boolean;\n")
    sb.append("  export: number;\n")
    sb.append("  mangled_name: string | null;\n")
    sb.append("  undeclared: boolean;\n")
    sb.append("  id: number;\n")
    sb.append("  chained: boolean;\n")
    sb.append("  direct_access: boolean;\n")
    sb.append("  escaped: number;\n")
    sb.append("  recursive_refs: number;\n")
    sb.append("  assignments: number;\n")
    sb.append("  replaced: number;\n")
    sb.append("  single_use: any;\n")
    sb.append("  fixed: any;\n")
    sb.append("  eliminated: number;\n")
    sb.append("  should_replace: any;\n")
    sb.append("}\n\n")

    // Emit each DEFNODE class
    for cls <- hierarchy do emitClassDecl(sb, cls, byName, referenceFields)

    // Emit DEFMETHOD augmentations (module-level interface merging)
    for (className, methods) <- defmethodFamilies do
      if methods.nonEmpty then
        sb.append(s"// DEFMETHOD augmentations for $className\n")
        sb.append(s"export interface $className {\n")
        for m <- methods do
          val paramStr = m.params.map(p => s"${p.name}: ${p.tsType}").mkString(", ")
          sb.append(s"  ${m.methodName}($paramStr): ${m.returnType};\n")
        sb.append("}\n\n")

    // Non-class exports from ast.js: walkers, annotations, utilities
    sb.append("// Walker classes and functions\n")
    sb.append("export declare class TreeWalker {\n")
    sb.append("  constructor(callback: (node: AST_Node, descend: () => void) => any);\n")
    sb.append("  directives: Record<string, any>;\n")
    sb.append("  find_parent(type: any): AST_Node | undefined;\n")
    sb.append("  find_scope(): AST_Scope | undefined;\n")
    sb.append("  has_directive(dir: string): boolean;\n")
    sb.append("  loopcontrol_target(node: AST_Node): AST_Node | undefined;\n")
    sb.append("  parent(n?: number): AST_Node | undefined;\n")
    sb.append("  pop(): void;\n")
    sb.append("  push(node: AST_Node): void;\n")
    sb.append("  self(): AST_Node;\n")
    sb.append("  stack: AST_Node[];\n")
    sb.append("}\n\n")

    sb.append("export declare class TreeTransformer extends TreeWalker {\n")
    sb.append("  constructor(\n")
    sb.append(
      "    before: (node: AST_Node, descend: (node: AST_Node, tw: TreeTransformer) => void, in_list: boolean) => AST_Node | undefined,\n"
    )
    sb.append("    after?: (node: AST_Node, in_list: boolean) => AST_Node | undefined,\n")
    sb.append("  );\n")
    sb.append("  before: any;\n")
    sb.append("  after: any;\n")
    sb.append("}\n\n")

    sb.append("export declare function walk(node: AST_Node, visitor: (node: AST_Node) => any): void;\n")
    sb.append("export declare function walk_abort(node: AST_Node, visitor: (node: AST_Node) => any): boolean;\n")
    sb.append("export declare function walk_body(node: AST_Node, visitor: TreeWalker): void;\n")
    sb.append(
      "export declare function walk_parent(node: AST_Node, cb: (node: AST_Node, info: any) => any, initial_stack?: AST_Node[]): void;\n\n"
    )

    sb.append("// Annotation constants\n")
    sb.append("export declare const _INLINE: number;\n")
    sb.append("export declare const _NOINLINE: number;\n")
    sb.append("export declare const _PURE: number;\n")
    sb.append("export declare const _KEY: number;\n")
    sb.append("export declare const _MANGLEPROP: number;\n")

    sb.toString

  /** Emit one class declaration. */
  private def emitClassDecl(
    sb:              StringBuilder,
    cls:             TerserEmitter.DefnodeClass,
    byName:          Map[String, TerserEmitter.DefnodeClass],
    referenceFields: Map[String, List[DerivedField]] = Map.empty
  ): Unit =
    val extendsClause = cls.base match
      case Some(parent) if byName.contains(parent) => s" extends $parent"
      case _                                       => ""

    sb.append(s"export declare class ${cls.varName}$extendsClause {\n")

    // TYPE constant
    if !cls.isAbstract then sb.append(s"  TYPE: \"${cls.typeName}\";\n")

    // start/end tokens (root node only -- these are AST_Token, not AST_Node)
    val isRoot = cls.base.isEmpty || cls.varName == "AST_Node"
    if isRoot then
      sb.append("  start: AST_Token | null;\n")
      sb.append("  end: AST_Token | null;\n")

    // Build lookup from reference fields for this class (snake_case name -> tsType)
    val refLookup: Map[String, String] = referenceFields.getOrElse(cls.varName, Nil).map(f => f.jsName -> f.tsType).toMap

    // Self-properties: use reference type when available and more specific than
    // heuristic; fall back to heuristic when the reference type is just `any`
    val skipProps = if isRoot then Set("start", "end") else Set.empty[String]
    for prop <- cls.selfProps if !skipProps.contains(prop) do
      val heuristic = inferTsFieldType(prop, cls.varName)
      val field     = refLookup.get(prop) match
        case Some(tsType) if !isLessSpecific(tsType, heuristic.tsType) =>
          DerivedField(prop, tsType)
        case _ => heuristic
      sb.append(s"  ${field.jsName}: ${field.tsType};\n")

    // DEFNODE methods declared in the constructor object
    for method <- cls.methods do sb.append(s"  $method(...args: any[]): any;\n")

    sb.append("}\n\n")

  // --------------------------------------------------------------------------
  // DEFMETHOD declarations
  // --------------------------------------------------------------------------

  /** A DEFMETHOD declaration for the `.d.ts` interface augmentation. */
  final case class DefmethodDecl(
    methodName: String,
    params:     List[DefmethodParam],
    returnType: String
  )

  final case class DefmethodParam(
    name:   String,
    tsType: String
  )

  /** Build DEFMETHOD declarations for common families.
    *
    * These are the well-known method families that scope.js, equivalent-to.js, size.js, and other files add to the AST classes via DEFMETHOD.
    */
  def commonDefmethodDecls: Map[String, List[DefmethodDecl]] =
    Map(
      "AST_Node" -> List(
        DefmethodDecl("figure_out_scope", List(DefmethodParam("options", "any")), "void"),
        DefmethodDecl("equivalent_to", List(DefmethodParam("node", "AST_Node")), "boolean"),
        DefmethodDecl("shallow_cmp", List(DefmethodParam("other", "AST_Node")), "boolean"),
        DefmethodDecl("_size", List(DefmethodParam("info", "any")), "number"),
        DefmethodDecl("size", List(DefmethodParam("compressor", "any"), DefmethodParam("stack", "any")), "number"),
        DefmethodDecl("is_string", List(DefmethodParam("compressor", "any")), "boolean"),
        DefmethodDecl("is_number", List(DefmethodParam("compressor", "any")), "boolean"),
        DefmethodDecl("is_boolean", List(DefmethodParam("compressor", "any")), "boolean"),
        DefmethodDecl("is_nullish", List(DefmethodParam("compressor", "any")), "boolean | 0"),
        DefmethodDecl("has_side_effects", List(DefmethodParam("compressor", "any")), "boolean"),
        DefmethodDecl("may_throw_on_access", List(DefmethodParam("compressor", "any")), "boolean"),
        DefmethodDecl("_eval", List(DefmethodParam("compressor", "any"), DefmethodParam("ignore_side_effects", "any")), "any"),
        DefmethodDecl("is_constant_expression", List(DefmethodParam("scope", "AST_Scope")), "boolean"),
        DefmethodDecl(
          "drop_side_effect_free",
          List(DefmethodParam("compressor", "any"), DefmethodParam("first_in_statement", "any")),
          "AST_Node | null"
        ),
        DefmethodDecl("may_throw", List(DefmethodParam("compressor", "any")), "boolean"),
        DefmethodDecl("_dot_throw", List(DefmethodParam("compressor", "any")), "boolean"),
        DefmethodDecl("aborts", Nil, "AST_Node | null"),
        DefmethodDecl("_do_print", List(DefmethodParam("output", "any")), "void"),
        DefmethodDecl("print", List(DefmethodParam("output", "any")), "void"),
        DefmethodDecl("needs_parens", List(DefmethodParam("output", "any")), "boolean"),
        DefmethodDecl("add_source_map", List(DefmethodParam("output", "any")), "void")
      ),
      "AST_Scope" -> List(
        DefmethodDecl("def_variable", List(DefmethodParam("symbol", "AST_Symbol"), DefmethodParam("init", "AST_Node | null")), "SymbolDef"),
        DefmethodDecl("def_function", List(DefmethodParam("symbol", "AST_Symbol"), DefmethodParam("init", "AST_Node | null")), "SymbolDef"),
        DefmethodDecl("find_variable", List(DefmethodParam("name", "string | AST_Symbol")), "SymbolDef | undefined"),
        DefmethodDecl("next_mangled", List(DefmethodParam("options", "any"), DefmethodParam("ext", "any")), "string"),
        DefmethodDecl("is_block_scope", Nil, "boolean")
      )
    )

  /** Count the total number of typed fields in a generated `.d.ts`. */
  def countTypedFields(hierarchy: List[TerserEmitter.DefnodeClass]): Int =
    hierarchy.map(_.selfProps.size).sum

  // --------------------------------------------------------------------------
  // Reference port field extraction
  // --------------------------------------------------------------------------

  /** A field parsed from a reference port Scala source file. */
  final case class ParsedField(
    className: String, // Scala class name, e.g. "AstCall"
    fieldName: String, // Scala field name, e.g. "expression"
    scalaType: String // Scala type, e.g. "AstNode | Null"
  )

  /** Parse field declarations from a Scala source string.
    *
    * Looks for `var`/`val` field declarations within class/trait bodies. Returns a list of (className, fieldName, scalaType) tuples.
    */
  def parseFieldsFromScala(source: String): List[ParsedField] =
    val result       = mutable.ListBuffer.empty[ParsedField]
    val classOrTrait = """(?:class|trait)\s+(Ast\w+)""".r
    val fieldDecl    = """\s+(?:var|val)\s+(\w+)\s*:\s*(.+?)\s*=""".r

    var currentClass = ""
    for line <- source.linesIterator do
      classOrTrait.findFirstMatchIn(line).foreach { m =>
        currentClass = m.group(1)
      }
      if currentClass.nonEmpty then
        fieldDecl.findFirstMatchIn(line).foreach { m =>
          val fieldName = m.group(1)
          val scalaType = m.group(2).trim
          result += ParsedField(currentClass, fieldName, scalaType)
        }

    result.toList

  /** Convert a Scala class name (AstXxx) back to JS DEFNODE name (AST_Xxx). */
  def scalaNameToDefnode(scalaName: String): String =
    if scalaName.startsWith("Ast") && scalaName.length > 3 then "AST_" + scalaName.drop(3)
    else scalaName

  /** Build a field type map from parsed reference fields.
    *
    * Returns className (JS) -> list of DerivedField.
    */
  def buildFieldTypeMap(parsedFields: List[ParsedField]): Map[String, List[DerivedField]] =
    parsedFields.groupBy(f => scalaNameToDefnode(f.className)).map { case (jsClass, fields) =>
      jsClass -> fields.map { f =>
        // Convert field name from camelCase back to snake_case for the JS side
        val jsFieldName = camelToSnake(f.fieldName)
        DerivedField(jsFieldName, scalaTypeToTs(f.scalaType))
      }
    }

  /** True when `refType` is strictly less specific than `heuristicType`. E.g. `any`, `any[]`, `Map<string, any>` are less specific than a concrete type when the heuristic doesn't use `any`.
    */
  private def isLessSpecific(refType: String, heuristicType: String): Boolean =
    // If the reference type contains `any` anywhere and the heuristic doesn't,
    // prefer the heuristic — the reference lost type information
    val refHasAny       = refType.contains("any")
    val heuristicHasAny = heuristicType.contains("any")
    refHasAny && !heuristicHasAny

  /** Convert camelCase to snake_case. */
  private def camelToSnake(s: String): String =
    val sb = new StringBuilder
    for (i <- 0 until s.length)
      val c = s(i)
      if c.isUpper && i > 0 then
        sb.append('_')
        sb.append(c.toLower)
      else sb.append(c)
    sb.toString

  /** Generate a `.d.ts` with field types derived from reference port sources.
    *
    * Combines hierarchy extraction, reference field parsing, and `.d.ts` generation. When a DEFNODE property has a matching field in the reference, the reference type is used (via `scalaTypeToTs`);
    * otherwise `inferTsFieldType` provides the heuristic fallback.
    */
  def generateFromReference(
    hierarchy:        List[TerserEmitter.DefnodeClass],
    referenceSources: List[String]
  ): String =
    val allFields = referenceSources.flatMap(parseFieldsFromScala)
    val fieldMap  = buildFieldTypeMap(allFields)
    generate(hierarchy, commonDefmethodDecls, fieldMap)

  /** Derivation metrics: how many DEFNODE fields are covered by reference types vs falling back to the heuristic.
    */
  final case class DerivationMetrics(
    totalFields:       Int,
    referenceDerived:  Int,
    heuristicFallback: Int
  )

  /** Count how many fields use reference-derived types vs heuristic. */
  def countDerivedVsHeuristic(
    hierarchy:       List[TerserEmitter.DefnodeClass],
    referenceFields: Map[String, List[DerivedField]]
  ): DerivationMetrics =
    var total         = 0
    var fromRef       = 0
    var fromHeuristic = 0
    for cls <- hierarchy do
      val isRoot    = cls.base.isEmpty || cls.varName == "AST_Node"
      val skipProps = if isRoot then Set("start", "end") else Set.empty[String]
      val refLookup = referenceFields.getOrElse(cls.varName, Nil).map(_.jsName).toSet
      for prop <- cls.selfProps if !skipProps.contains(prop) do
        total += 1
        if refLookup.contains(prop) then fromRef += 1
        else fromHeuristic += 1
    DerivationMetrics(total, fromRef, fromHeuristic)
