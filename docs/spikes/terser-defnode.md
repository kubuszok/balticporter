# Spike: Terser DEFNODE Normalization

## Problem

Terser's AST hierarchy is built at runtime via 134 `DEFNODE(type, props, ctor, methods, base)`
calls in `lib/ast.js`. No static frontend can see these as classes — they are function calls that
construct prototypes dynamically.

## DEFNODE anatomy

```javascript
function DEFNODE(type, props, ctor, methods, base = AST_Node) {
    if (!props) props = [];
    else props = props.split(/\s+/);
    var self_props = props;
    if (base && base.PROPS) props = props.concat(base.PROPS);
    const proto = base && Object.create(base.prototype);
    // ... installs methods on prototype, sets PROPS, TYPE, etc.
}
```

Each call declares:
- `type`: class name (string)
- `props`: space-separated field names (inherited from base)
- `ctor`: constructor function (assigns props from an options object)
- `methods`: object literal with method implementations
- `base`: parent class (default `AST_Node`)

## Hierarchy (134 classes)

Top-level structure (depth ≤ 3):

```
AST_Node
├── AST_Statement
│   ├── AST_Debugger
│   ├── AST_Directive
│   ├── AST_SimpleStatement
│   ├── AST_Block → AST_BlockStatement
│   ├── AST_EmptyStatement
│   ├── AST_StatementWithBody
│   │   ├── AST_LabeledStatement
│   │   ├── AST_IterationStatement → AST_DWLoop → AST_Do, AST_While
│   │   │                          → AST_For, AST_ForIn → AST_ForOf
│   │   └── AST_With
│   ├── AST_Scope → AST_Toplevel, AST_Lambda → AST_Accessor/Function/Arrow/Defun
│   ├── AST_Jump → AST_Exit → AST_Return/Throw
│   │            → AST_LoopControl → AST_Break/Continue
│   ├── AST_If
│   ├── AST_Switch → AST_SwitchBranch → AST_Default/Case
│   ├── AST_Try, AST_Catch, AST_Finally
│   ├── AST_Definitions → AST_Var/Let/Const
│   ├── AST_Export, AST_Import
│   └── AST_Class
├── AST_Token (leaf)
├── AST_Expansion, AST_Destructuring
├── AST_PropAccess → AST_Dot, AST_Sub
├── AST_Unary → AST_UnaryPrefix/Postfix
├── AST_Binary → AST_Assign
├── AST_Conditional, AST_Array, AST_Object
├── AST_Symbol → AST_SymbolDeclaration → AST_SymbolVar/Let/Const/...
│             → AST_SymbolRef, AST_SymbolExport/Import/...
├── AST_Constant → AST_String/Number/RegExp/Atom → AST_Null/True/False/...
└── AST_Call → AST_New
```

## Normalization rule design

A `DefnodeNormalizationRule` is a §1(c) rule using a §1(b) mechanism. It:

1. **Recognizes** `DEFNODE(type, props, ctor, methods, base)` call patterns in JavaScript AST
2. **Extracts** the class name, property names, parent class, and method implementations
3. **Emits** an explicit `ClassDef` in TIR with:
   - Fields derived from `props` (space-split string → `ValDef` per name)
   - Constructor from `ctor` function body
   - Methods from `methods` object literal → `DefDef` per property
   - Inheritance from `base` argument → `extends` clause
4. **Removes** the `DEFNODE` call and `var AST_X =` binding from the program

## Feasibility

**Recognizable**: The `DEFNODE` pattern is highly regular — 134 calls all follow the exact same
shape. A pattern-matching rule can extract the 4 arguments deterministically.

**Props are strings**: `"init condition step"` → `List("init", "condition", "step")` → 3 fields.
Types are ALL `Object` (no typing in the source), but the reference port knows the types.

**Methods are object literals**: Each `methods` argument is an object literal whose keys become
method names and whose values become method bodies. Straightforward extraction.

**Base is a variable**: Always `AST_X` from a prior `DEFNODE` call (except root `AST_Node`).
The normalization must process in declaration order to resolve the parent.

## Blockers

1. **No types**: Terser is untyped JavaScript. Field types are ALL `Object`. A
   `NumberSplitTransform`-like flow analysis or the reference port's types are needed.
2. **134 classes in one file**: `ast.js` is 3,500+ lines. The emitter would produce 134 Scala files.
3. **Method bodies use `this.TYPE`**: A prototype-check pattern `this.TYPE === "If"` that
   the normalized class would express as `isInstanceOf[AST_If]`.
4. **`_walk` / `_children_backwards`**: Visitor methods that reference child props by name —
   the normalized class needs generated traversal.

## Recommendation

The DEFNODE rule is feasible as a project-specific recognizer (§1(c)). The hierarchy extraction
is deterministic and mechanical. The blocker is the untyped method bodies — these need either
the JS frontend with `allowJs` + JSDoc, or type inference from the reference port.

**Stop rule**: Implement the hierarchy extractor only. Do not attempt to type method bodies
until the JS frontend's type inference is validated on a simpler target.

## Metrics

- 134 `DEFNODE` calls
- ~3,500 LOC in `ast.js`
- Props: 0–8 fields per class, ~250 total field declarations
- Methods: ~400 method implementations across all classes
- Inheritance depth: max 6 (AST_Node → Statement → IterationStatement → DWLoop → Do)
