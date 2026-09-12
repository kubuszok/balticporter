/**
 * Baltic Porter TypeScript Exporter — Phase 1.1
 *
 * Reads a TypeScript project via ts.createProgram + checker, extracts a
 * Resolved AST (RAST) with symbols, types, and resolved references,
 * and writes deterministic JSON to an output directory.
 *
 * The exporter is deliberately dumb: it serializes what the TS compiler
 * knows. All lowering decisions live in the Scala frontend-ts module
 * where the catalog, decisions, and refusal markers live.
 */

import * as ts from "typescript";
import * as fs from "fs";
import * as path from "path";
import * as crypto from "crypto";

// ---------------------------------------------------------------------------
// CLI
// ---------------------------------------------------------------------------

interface ExportArgs {
  project: string;   // path to tsconfig.json
  out: string;       // output directory for RAST JSON
  strict: boolean;   // force --strict (counted diagnostics)
}

function parseArgs(): ExportArgs {
  const args = process.argv.slice(2);
  let project = "";
  let out = "";
  let strict = false;
  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--project" && i + 1 < args.length) project = args[++i];
    else if (args[i] === "--out" && i + 1 < args.length) out = args[++i];
    else if (args[i] === "--strict") strict = true;
    else { console.error(`Unknown arg: ${args[i]}`); process.exit(1); }
  }
  if (!project || !out) {
    console.error("Usage: export --project <tsconfig> --out <dir> [--strict]");
    process.exit(1);
  }
  return { project, out, strict };
}

// ---------------------------------------------------------------------------
// RAST schema (v1)
// ---------------------------------------------------------------------------

interface RastFile {
  version: 1;
  path: string;
  sha256: string;
  nodes: RastNode[];
  symbols: Record<string, RastSymbol>;
  types: Record<string, RastType>;
}

interface RastNode {
  kind: string;
  kindCode: number;
  pos: [number, number]; // [line, col] 1-based
  children?: RastNode[];
  // present on declarations
  symbol?: string;       // symbol id
  // present on expressions/references
  type?: string;         // type id
  resolvedSymbol?: string;
  // present on literals
  value?: string | number | boolean;
  // present on identifiers
  text?: string;
  // operator for binary/prefix/postfix
  operator?: string;
  // flags
  flags?: string[];
  // leading comments
  comments?: RastComment[];
}

interface RastSymbol {
  name: string;
  flags: string[];
  declarationType?: string;
  parent?: string;
}

interface RastType {
  kind: string;
  text: string;
  // for object types: members
  members?: Record<string, string>;
  // for union/intersection
  types?: string[];
  // for type references
  target?: string;
  typeArguments?: string[];
  // for function types
  parameters?: Array<{ name: string; type: string; optional?: boolean }>;
  returnType?: string;
  // for array
  elementType?: string;
  // for literal types
  value?: string | number | boolean;
}

interface RastComment {
  kind: "line" | "block";
  text: string;
  pos: [number, number];
}

// ---------------------------------------------------------------------------
// Exporter
// ---------------------------------------------------------------------------

class TsExporter {
  private checker: ts.TypeChecker;
  private symbolMap = new Map<ts.Symbol, string>();
  private symbolCounter = 0;
  private typeMap = new Map<string, RastType>();
  private typeCounter = 0;
  private typeCache = new Map<ts.Type, string>();
  private sourceFile!: ts.SourceFile;

  constructor(private program: ts.Program) {
    this.checker = program.getTypeChecker();
  }

  exportFile(sf: ts.SourceFile): RastFile {
    this.sourceFile = sf;
    this.symbolMap.clear();
    this.symbolCounter = 0;
    this.typeMap.clear();
    this.typeCounter = 0;
    this.typeCache.clear();

    const content = sf.getFullText();
    const sha256 = crypto.createHash("sha256").update(content).digest("hex");

    const nodes = sf.statements.map(s => this.visitNode(s));
    const symbols: Record<string, RastSymbol> = {};
    for (const [sym, id] of this.symbolMap) {
      symbols[id] = this.exportSymbol(sym);
    }

    return {
      version: 1,
      path: sf.fileName,
      sha256,
      nodes,
      symbols,
      types: Object.fromEntries(this.typeMap),
    };
  }

  private visitNode(node: ts.Node): RastNode {
    const sf = this.sourceFile;
    const { line, character } = sf.getLineAndCharacterOfPosition(node.getStart(sf));
    const result: RastNode = {
      kind: ts.SyntaxKind[node.kind],
      kindCode: node.kind,
      pos: [line + 1, character + 1],
    };

    // Symbol for declarations
    const sym = this.getNodeSymbol(node);
    if (sym) result.symbol = this.internSymbol(sym);

    // Type for expressions
    if (this.isExpression(node) || ts.isVariableDeclaration(node) || ts.isParameter(node)) {
      try {
        const type = this.checker.getTypeAtLocation(node);
        result.type = this.internType(type);
      } catch { /* some nodes don't have types */ }
    }

    // Resolved reference for identifiers
    if (ts.isIdentifier(node)) {
      result.text = node.text;
      const refSym = this.checker.getSymbolAtLocation(node);
      if (refSym) result.resolvedSymbol = this.internSymbol(refSym);
    }

    // Literal values
    if (ts.isStringLiteral(node)) result.value = node.text;
    else if (ts.isNumericLiteral(node)) result.value = Number(node.text);
    else if (node.kind === ts.SyntaxKind.TrueKeyword) result.value = true;
    else if (node.kind === ts.SyntaxKind.FalseKeyword) result.value = false;

    // Operators
    if (ts.isBinaryExpression(node))
      result.operator = ts.SyntaxKind[node.operatorToken.kind];
    if (ts.isPrefixUnaryExpression(node))
      result.operator = ts.SyntaxKind[node.operator];
    if (ts.isPostfixUnaryExpression(node))
      result.operator = ts.SyntaxKind[node.operator];

    // Flags
    const flags = this.nodeFlags(node);
    if (flags.length > 0) result.flags = flags;

    // Comments
    const comments = this.leadingComments(node);
    if (comments.length > 0) result.comments = comments;

    // Children (only significant ones)
    const children = this.visitChildren(node);
    if (children.length > 0) result.children = children;

    return result;
  }

  private visitChildren(node: ts.Node): RastNode[] {
    const children: RastNode[] = [];
    node.forEachChild(child => {
      children.push(this.visitNode(child));
    });
    return children;
  }

  private getNodeSymbol(node: ts.Node): ts.Symbol | undefined {
    if (ts.isFunctionDeclaration(node) || ts.isClassDeclaration(node) ||
        ts.isInterfaceDeclaration(node) || ts.isTypeAliasDeclaration(node) ||
        ts.isEnumDeclaration(node) || ts.isModuleDeclaration(node) ||
        ts.isVariableDeclaration(node) || ts.isParameter(node) ||
        ts.isPropertyDeclaration(node) || ts.isMethodDeclaration(node) ||
        ts.isPropertySignature(node) || ts.isMethodSignature(node) ||
        ts.isGetAccessorDeclaration(node) || ts.isSetAccessorDeclaration(node) ||
        ts.isConstructorDeclaration(node) || ts.isEnumMember(node)) {
      return this.checker.getSymbolAtLocation(
        (node as any).name ?? node
      );
    }
    return undefined;
  }

  private internSymbol(sym: ts.Symbol): string {
    let id = this.symbolMap.get(sym);
    if (!id) {
      id = `s${this.symbolCounter++}`;
      this.symbolMap.set(sym, id);
    }
    return id;
  }

  private exportSymbol(sym: ts.Symbol): RastSymbol {
    const flags: string[] = [];
    if (sym.flags & ts.SymbolFlags.ExportValue) flags.push("export");
    if (sym.flags & ts.SymbolFlags.Function) flags.push("function");
    if (sym.flags & ts.SymbolFlags.Class) flags.push("class");
    if (sym.flags & ts.SymbolFlags.Interface) flags.push("interface");
    if (sym.flags & ts.SymbolFlags.TypeAlias) flags.push("typeAlias");
    if (sym.flags & ts.SymbolFlags.Enum) flags.push("enum");
    if (sym.flags & ts.SymbolFlags.Variable) flags.push("variable");
    if (sym.flags & ts.SymbolFlags.Property) flags.push("property");
    if (sym.flags & ts.SymbolFlags.Method) flags.push("method");

    const result: RastSymbol = { name: sym.name, flags };

    // declared type
    try {
      const dt = this.checker.getDeclaredTypeOfSymbol(sym);
      if (dt.flags !== ts.TypeFlags.Any)
        result.declarationType = this.internType(dt);
    } catch { /* not all symbols have a declared type */ }

    // parent
    const parent = (sym as any).parent as ts.Symbol | undefined;
    if (parent && parent.name !== "__global") {
      result.parent = this.internSymbol(parent);
    }

    return result;
  }

  private internType(type: ts.Type): string {
    const cached = this.typeCache.get(type);
    if (cached) return cached;

    const id = `t${this.typeCounter++}`;
    this.typeCache.set(type, id);

    const rast = this.exportType(type);
    this.typeMap.set(id, rast);
    return id;
  }

  private exportType(type: ts.Type): RastType {
    const text = this.checker.typeToString(type);

    // Union
    if (type.isUnion()) {
      return {
        kind: "union",
        text,
        types: type.types.map(t => this.internType(t)),
      };
    }

    // Intersection
    if (type.isIntersection()) {
      return {
        kind: "intersection",
        text,
        types: type.types.map(t => this.internType(t)),
      };
    }

    // Literal types
    if (type.isStringLiteral()) return { kind: "stringLiteral", text, value: type.value };
    if (type.isNumberLiteral()) return { kind: "numberLiteral", text, value: type.value };
    if (type.flags & ts.TypeFlags.BooleanLiteral) {
      return { kind: "booleanLiteral", text, value: text === "true" };
    }

    // Array
    if (this.checker.isArrayType(type)) {
      const typeArgs = (type as ts.TypeReference).typeArguments;
      return {
        kind: "array",
        text,
        elementType: typeArgs?.[0] ? this.internType(typeArgs[0]) : undefined,
      };
    }

    // Function/callable
    const sigs = this.checker.getSignaturesOfType(type, ts.SignatureKind.Call);
    if (sigs.length > 0 && !(type.flags & ts.TypeFlags.Object && (type as ts.ObjectType).objectFlags & ts.ObjectFlags.Class)) {
      const sig = sigs[0];
      return {
        kind: "function",
        text,
        parameters: sig.parameters.map(p => ({
          name: p.name,
          type: this.internType(this.checker.getTypeOfSymbolAtLocation(p, p.valueDeclaration!)),
          optional: !!(p.flags & ts.SymbolFlags.Optional),
        })),
        returnType: this.internType(sig.getReturnType()),
      };
    }

    // Type reference (generic instantiation)
    if (type.flags & ts.TypeFlags.Object) {
      const objType = type as ts.ObjectType;
      if (objType.objectFlags & ts.ObjectFlags.Reference) {
        const ref = type as ts.TypeReference;
        const target = ref.target;
        const typeArgs = this.checker.getTypeArguments(ref);
        if (typeArgs.length > 0) {
          return {
            kind: "reference",
            text,
            target: this.internType(target),
            typeArguments: typeArgs.map(t => this.internType(t)),
          };
        }
      }
    }

    // Primitive / other
    if (type.flags & ts.TypeFlags.String) return { kind: "string", text };
    if (type.flags & ts.TypeFlags.Number) return { kind: "number", text };
    if (type.flags & ts.TypeFlags.Boolean) return { kind: "boolean", text };
    if (type.flags & ts.TypeFlags.Void) return { kind: "void", text };
    if (type.flags & ts.TypeFlags.Null) return { kind: "null", text };
    if (type.flags & ts.TypeFlags.Undefined) return { kind: "undefined", text };
    if (type.flags & ts.TypeFlags.Never) return { kind: "never", text };
    if (type.flags & ts.TypeFlags.Any) return { kind: "any", text };

    // Object type with members
    if (type.flags & ts.TypeFlags.Object) {
      const members: Record<string, string> = {};
      for (const prop of type.getProperties()) {
        try {
          const propType = this.checker.getTypeOfSymbolAtLocation(prop, prop.valueDeclaration ?? this.sourceFile);
          members[prop.name] = this.internType(propType);
        } catch { /* skip unresolvable */ }
      }
      return { kind: "object", text, members };
    }

    return { kind: "other", text };
  }

  private isExpression(node: ts.Node): boolean {
    return ts.isExpression(node) || ts.isCallExpression(node) ||
           ts.isPropertyAccessExpression(node) || ts.isElementAccessExpression(node);
  }

  private nodeFlags(node: ts.Node): string[] {
    const flags: string[] = [];
    const mods = ts.canHaveModifiers(node) ? ts.getModifiers(node) : undefined;
    if (mods) {
      for (const m of mods) {
        flags.push(ts.SyntaxKind[m.kind]);
      }
    }
    if (ts.isVariableDeclaration(node)) {
      const parent = node.parent;
      if (ts.isVariableDeclarationList(parent)) {
        if (parent.flags & ts.NodeFlags.Const) flags.push("const");
        else if (parent.flags & ts.NodeFlags.Let) flags.push("let");
        else flags.push("var");
      }
    }
    return flags;
  }

  private leadingComments(node: ts.Node): RastComment[] {
    const sf = this.sourceFile;
    const fullText = sf.getFullText();
    const comments: RastComment[] = [];
    const ranges = ts.getLeadingCommentRanges(fullText, node.pos);
    if (ranges) {
      for (const r of ranges) {
        const text = fullText.substring(r.pos, r.end);
        const { line, character } = sf.getLineAndCharacterOfPosition(r.pos);
        comments.push({
          kind: r.kind === ts.SyntaxKind.SingleLineCommentTrivia ? "line" : "block",
          text,
          pos: [line + 1, character + 1],
        });
      }
    }
    return comments;
  }
}

// ---------------------------------------------------------------------------
// Main
// ---------------------------------------------------------------------------

function main() {
  const args = parseArgs();

  const configPath = path.resolve(args.project);
  const configFile = ts.readConfigFile(configPath, ts.sys.readFile);
  if (configFile.error) {
    console.error(`Error reading ${configPath}: ${ts.flattenDiagnosticMessageText(configFile.error.messageText, "\n")}`);
    process.exit(1);
  }

  const configDir = path.dirname(configPath);
  const parsed = ts.parseJsonConfigFileContent(configFile.config, ts.sys, configDir);

  if (args.strict) {
    parsed.options.strict = true;
  }

  const program = ts.createProgram(parsed.fileNames, parsed.options);
  const exporter = new TsExporter(program);

  // Report diagnostics (counted, not fatal)
  const diagnostics = ts.getPreEmitDiagnostics(program);
  if (diagnostics.length > 0) {
    console.error(`[ts-exporter] ${diagnostics.length} diagnostic(s):`);
    for (const d of diagnostics) {
      const msg = ts.flattenDiagnosticMessageText(d.messageText, "\n");
      if (d.file && d.start !== undefined) {
        const { line, character } = d.file.getLineAndCharacterOfPosition(d.start);
        console.error(`  ${d.file.fileName}:${line + 1}:${character + 1} - ${msg}`);
      } else {
        console.error(`  ${msg}`);
      }
    }
  }

  // Export each source file (not .d.ts, not node_modules)
  const outDir = path.resolve(args.out);
  fs.mkdirSync(outDir, { recursive: true });

  let count = 0;
  for (const sf of program.getSourceFiles()) {
    if (sf.isDeclarationFile) continue;
    if (sf.fileName.includes("node_modules")) continue;

    const rast = exporter.exportFile(sf);
    const relPath = path.relative(configDir, sf.fileName)
      .replace(/\.tsx?$/, ".rast.json");
    const outPath = path.join(outDir, relPath);
    fs.mkdirSync(path.dirname(outPath), { recursive: true });
    fs.writeFileSync(outPath, JSON.stringify(rast, null, 2) + "\n");
    count++;
  }

  console.log(`[ts-exporter] Exported ${count} file(s) to ${outDir}`);
  if (diagnostics.length > 0) {
    console.log(`[ts-exporter] ${diagnostics.length} diagnostic(s) reported`);
  }
}

main();
