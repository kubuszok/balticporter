# TypeScript lowering plan — from RAST to compiling Scala, measured on path-data-parser

Target: the three files of `path-data-parser` (`parser.ts`, `absolutize.ts`, `normalize.ts`, 466
LOC, reference port `ssg-graphs-commons/.../rough/pathdata/`) compile from the exporter's RAST with
no hand edit, and the reference port's tests pass against the emitted code. This document lists
every lowering rule that stands between today's output and that gate, each as an independently
implementable, independently testable unit.

Inputs read for this plan: the three `.ts` sources, the three hand-ported `.scala` files, the RAST
fixtures under `frontend-ts/src/test/resources/rast/path-data-parser/`, `TsToScalaEmitter.scala`,
`TsMinter.scala`, `Rast.scala`, the exporter (`exporter/src/export.ts`), and the Scala the emitter
produces today (`sbt "frontend-ts/testOnly *TsMinterSpec -- *emitted*"`, 2 s).

---

## 0. Two findings that shape everything below

### 0.1 There are two pipelines, and neither reaches the compiler

```
exporter (ts) ──RAST json──┬──> TsMinter ──TIR──> (engine TirEmitter)     ← the real frontend path
                           └──> TsToScalaEmitter ──text──> .scala          ← the proof-of-concept path
```

`TsToScalaEmitter` reads RAST directly and produces text. It is where every observation in this
document was measured, and it is the only path producing anything readable today. But it is a
dead end as an engine component: nothing it emits goes through the phases, `members.tsv`,
`decisions.tsv`, porter notes, or the parity check, and it already contains a library name
(`Segment`/`PathToken` recognised by field set in `emitObjectLiteral`) — the one thing the
working rules forbid in an engine module.

`TsMinter` produces TIR, but the TIR it produces cannot be printed: every identifier is
`Ident(SymId.None, …)` (the name is gone), every binary expression is `Apply(left, List(right))`
with no operator, every member access is `Select(obj, SymId.None)` with no member, prefix and
postfix operators return their operand (`index++` becomes `index`), and no `Symbol` is ever
interned for a RAST symbol, so the engine's emitter has nothing to look names up in. The
spec's "0 unportable markers" measures only that the minter did not *refuse* — it says nothing
about fidelity.

**Decision this plan takes:** the durable home for every lowering rule is the minter (RAST to
TIR), because the engine's `TirEmitter` already lowers what the text emitter gets wrong or
skips — C-style `for` to `while`, `break`/`continue` to `boundary`, `IncDec`, `switch`
fallthrough duplication and the fall-out arm, operators as `Apply(Select(l, opId(op)), List(r))`,
reassigned constructor parameters — and because only TIR reaches the checks. The text emitter is
kept as a *harness* for the next few weeks (2 s turnaround against scalac) with a stop rule: once
the minter path compiles the three files, `TsToScalaEmitter` is deleted. Rules are therefore
split into an **analysis** half (a pure function over RAST: "is this condition a truthiness
test?", "which parameters are reassigned?", "what is the callback arity?") shared by both paths
through one `TsLowering` object, and a **construction** half written per path. Section 5 lists
the minter foundation work that must land before any rule can be constructed on the TIR side.

### 0.2 The exporter hands over tokens as children, and that is the single largest cause of `???`

`node.forEachChild` visits `operatorToken`, `questionToken`, `colonToken`,
`equalsGreaterThanToken`, `dotDotDotToken` and modifiers, so the RAST has

```
BinaryExpression      children = [lhs, FirstAssignment, rhs]
ConditionalExpression children = [cond, QuestionToken, then, ColonToken, else]
ArrowFunction         children = [Parameter…, EqualsGreaterThanToken, body]
Parameter             children = [Identifier, QuestionToken?, type?, DotDotDotToken?]
```

Every positional access in both the minter and the emitter (`children(1)`, `children(2)`,
`find(_.kind != "Parameter")`) picks a token. This is rule R1 and it goes first; several `???`s
disappear with it and several rules below assume it.

Also from the exporter: `ts.SyntaxKind[kind]` returns the *alias* name for duplicated enum
values, so the RAST spells `VariableStatement` as `FirstStatement`, `EqualsToken` as
`FirstAssignment`, `LessThanToken` as `FirstBinaryOperator`, `NumericLiteral` as
`FirstLiteralToken`, `PlusEqualsToken` as `FirstCompoundAssignment`, `TemplateTail` as
`LastTemplateToken`. Rule R2 canonicalises in the exporter; until then every consumer carries
`case "FirstStatement" | "VariableStatement"` twins and misses the ones nobody noticed yet
(`FirstBinaryOperator` is handled, `FirstCompoundAssignment` reaches `tsOpToScala` by luck).

---

## 1. What the emitter produces today, and why it does not compile

Grouped by cause, with the count of distinct sites in the three files. Every row maps to a rule
in section 3.

| # | symptom in the emitted Scala | cause | sites | rule |
|---|---|---|---|---|
| 1 | `??? /* QuestionToken */`, `??? /* EqualsGreaterThanToken */`, `data.map((d, i) => ???)` | token children picked as operands | 4 | R1 |
| 2 | `val f2old: Double = ???` (also `x2old`, `y2old`) | initializer that is an `Identifier` is filtered out by `findInitializer`/`findInit` | 3 | R3 |
| 3 | `def isType(...): Any`, `def rotate(...): Any` | return type read off `node.type` (absent); it is on the name `Identifier`'s function type | 2 | R4 |
| 4 | `val tokens: Vector[PathToken] = ArrayBuffer.empty`, `tokens += key`, `out += …`, `params(params.length) = …` on `Vector` | arrays retyped immutable while every use mutates | 9 decls | R9, R10 |
| 5 | `tokens(index)`, `tokens(i)` with `index: Double` | `number` is `Double`, `apply` wants `Int` | 8 | R11 |
| 6 | `tokens(tokens.length) = PathToken(…)` | JS append idiom on a `Vector` | 4 | R9 |
| 7 | `tokens += data(0), "" + data(1), …`; `tokens += data*` | `push` with n arguments / spread | 3 | R30 |
| 8 | `Vector(cx, cy) = data`; `val data: Any = ??? /* ArrayBindingPattern */` | array destructuring assignment / declaration | 8 | R21 |
| 9 | `(4 / 3) * r1` | integer division where JS divides doubles | 2 | R8 |
| 10 | `"^(([-+]?[0-9]+(\.[0-9]*)?…".r` | `\.` is not a Scala string escape | 1 (of 3 regexes) | R32 |
| 11 | `d.`match`(…)`, `_m.group(1)` (unbound) | `String.prototype.match` + `RegExp.$1` legacy statics | 3 + 7 | R32 |
| 12 | `val r1: [?, ?][Double, Double] = rotate(…)`; `return Vector(X, Y)`; `r1(0)` | tuple type exported as a `reference` with text `[?, ?]` | 4 | R12 |
| 13 | `recursive: Vector[Double]` with a 9-argument call site; `if (recursive)` | optional parameter, truthiness on it | 1 + 2 | R13, R24 |
| 14 | `??? /* typeof PARAMS(mode) */ == "number"` | `typeof` | 1 | R28 |
| 15 | `case "C" =>` (empty arm) then `case "c" =>` (body) | empty cases must merge into one alternative | 5 groups | R20 |
| 16 | `// C-style for loop { // TODO: VariableDeclarationList … // TODO: PostfixUnaryExpression }` | `for` init is a `VariableDeclarationList`, update is an expression | 2 | R18 |
| 17 | `{ index += 1; index }` as a statement | post-increment in statement position | 3 | R19 |
| 18 | `"" + data(1), data(2)` — the `,` tail of `` `${data[1]},` `` is gone | `TemplateSpan` literal tail dropped | 4 | R26 |
| 19 | `Segment(mode, params)` / `PathToken(COMMAND, …)` | recognised by field NAMES hard-coded in the engine | 30 | R15 |
| 20 | `if (sweepFlag && (f1 > f2))`, `!sweepFlag`, `(if (((i % 2))) …)` | truthiness on `number` | 5 | R24 |
| 21 | `d = d.substring(…)` where `d` is a parameter | parameter reassigned (also `x1 y1 x2 y2 r1 r2`) | 7 | R23 |
| 22 | `val curves: Vector[Any] = Vector.empty` then `return curves` as `number[][]` | evolving array type (`any[]`/`never[]` at the declaration) | 1 | R17 |
| 23 | `curves.forEach(curve => { … ; })`, `Vector(m2, m3, m4).concat(params)` | Array vocabulary | 2 | R30 |
| 24 | `Math.asin(((… / r2)).toFixed(9).toDouble)`, `_m.group(1).toDouble`, `"" + x` | Number vocabulary (`toFixed`, `parseFloat`, unary `+`, number-to-string) | 6 | R33 |
| 25 | `while ((d != ""))` + newline + indented body | Scala 3 opens no indentation region after `)`; `while … do` or braces needed | all loops | R6 |
| 26 | `    final case class PathToken(`type`: TokenType, …)` | double indent; `TokenType` alias never emitted | 1 | R6, R7 |
| 27 | `def tokenize`, `def isType`, `val COMMAND` public | no `ExportKeyword` should mean `private` | 8 | R5 |
| 28 | `val PARAMS: Map[String, Int] = Map("A" -> 7, …)` | index-signature type recognised by TEXT (`rt.text.contains("[key: string]")`), value type guessed `Int` | 1 | R11 |
| 29 | `return` at the end of every body | harmless in a `def`; illegal under a `for` comprehension or lambda | 0 today | R22 |
| 30 | `new RuntimeException(…)` | acceptable; recorded as a vocabulary decision | 3 | R34 |

Not visible in the text but wrong at run time: `"" + 1.0` prints `1.0` where JS prints `1`
(serialize's output), `+text` on a non-number string throws where JS yields `NaN`, and
`PARAMS(mode)` throws where JS yields `undefined`. Section 4 lists these.

---

## 2. Representation decisions (made once, every rule reads them)

These are the answers to "what Scala type/shape does construct X take". They are the
TypeScript-frontend analogue of the Java table in the working rules and are universal (a fact
about TypeScript and Scala), never per-library.

| TS | Scala | why | the reference port did | recorded as |
|---|---|---|---|---|
| `T[]`, `Array<T>`, `[]` literal | `scala.collection.mutable.ArrayBuffer[T]` everywhere — parameters, returns, fields, locals | JS arrays are mutable and aliased; `push`, `arr[arr.length] = v`, `m2[0] = …` all mutate | `Vector` for params/returns/fields, `ArrayBuffer` for accumulating locals, `.toVector` at return | api-parity divergence `ArrayRepresentation`; a later `CollectionsTransform` family may retarget provably-unmutated positions to `Vector` |
| `[...a]` | `ArrayBuffer.from(a)` | a shallow copy is what the spread does | dropped (Vector is immutable) | none |
| `number`, number literal types, unions of number literals | `Double` | JS has one number type; `0 \| 1 \| 2` is still a double at run time | `Int` where semantically integral | `NumberSplitTransform` later retypes seeded slots to `Int`; until then R10 coerces at index slots |
| `[A, B]` tuple type | `(A, B)` | fixed arity, positional | same | none |
| `p?: T` parameter | `p: T \| Null = null` | the nullability floor the frontends document already chose; the call site may omit it | `Option[T] = None` | api-parity divergence `OptionalParameterShape` |
| `{ [key: string]: V }` | `scala.collection.mutable.Map[String, V]` | a JS object is mutable | immutable `Map` | divergence `IndexSignatureMutability`; same retarget remark as arrays |
| `interface` with only property signatures, not extended, not implemented | `final case class` (fields `val`; `var` where the program assigns the property) | a data record; case class gives `equals`/`toString` the reference has | same | none |
| other `interface` | `trait`; object literal against it instantiates an anonymous class (`new T { … }`) | as the frontends document says | — | `StructuralTypeNamed` when a nominal type had to be minted |
| `type A = …` alias | top-level `type A = …` | Scala 3 allows top-level type aliases | inlined | none |
| non-exported declaration | `private` | module scope | `private` | none |
| `new Error(m)` | `new RuntimeException(m)`; `e.message` → `e.getMessage` | JDK throwable with a message | a dedicated `PathDataParseError` | divergence `ErrorClass` |
| `x === y` | `==` when both static types are primitive/literal/string; `eq` otherwise | Scala's `==` is `equals`; JS's `===` is identity on objects | `==` on strings | none |
| conditions on non-boolean | `balticporter.runtime.js.Truthy(x)`; exact forms where the static type allows (`!= null` for nullable references, `.nonEmpty` for `string`) | JS truthiness | `!= 0`, `!= null`, `isDefined` | `TruthinessRuntime` counted per helper use |
| statement layout | braces, `while (c) { … }`, `if (c) { … } else { … }`, `for (x <- xs) { … }` | no reliance on Scala 3 indentation regions | braces | none |

---

## 3. The rules

Each rule: TS input, today's output, required output, what the RAST provides, where it lives
(**E** exporter, **A** shared analysis in `TsLowering`, **M** minter construction, **T** text
emitter construction, **R** runtime helper), dependencies, size (S = under a day, M = one to
three days, L = a week), and the test that proves it. "Test" always means: one RAST fixture
(export a five-line `.ts` under `src/test/resources/rast/rules/<rule>.ts`), one expected
Scala string, asserted through the emitter now and through `TirPrinter.canonical` once the
minter constructs it.

### Wave 0 — structural honesty (the emitter stops lying about the tree)

#### R1 TokenChildren — punctuation and modifiers are not children
- **RAST today**: see 0.2.
- **Fix (E)**: in `visitChildren`, skip a child when `ts.isTokenKind(child.kind)` and it is
  punctuation, an assignment/binary operator token, `QuestionToken`/`ColonToken`/
  `EqualsGreaterThanToken`/`DotDotDotToken`, or a modifier (`ts.isModifier`). Keep literal
  tokens, `Identifier`, `this`/`super`/`true`/`false`/`null`, and type keywords (`NumberKeyword`
  as an annotation is a token too). Record what was dropped as flags: `optional` on a Parameter
  or PropertySignature with a `questionToken`, `rest` for `dotDotDotToken`; modifiers already
  land in `flags`.
- **Fix (A)**, until every fixture is re-exported: `TsLowering.operands(node)` = children with
  `kindCode` in the punctuation range removed. Both paths call it instead of `children`.
- **Deps**: none. **Size**: S. **Files**: all three.
- **Test**: `a ? b : c` → three operands; `(d, i) => d` → two parameters and one body.

#### R2 CanonicalKindNames — `FirstStatement` is `VariableStatement`
- **Fix (E)**: a table of the seven aliases (`FirstStatement`, `FirstAssignment`,
  `FirstCompoundAssignment`, `FirstBinaryOperator`, `FirstLiteralToken`, `FirstTemplateToken`,
  `LastTemplateToken`) to canonical names, applied in `visitNode` and in `operator`. Delete
  every `"FirstStatement" | "VariableStatement"` twin in Scala afterwards.
- **Deps**: none. **Size**: S. **Files**: all.

#### R3 VarDeclInitializer — the initializer is positional, not "the first non-identifier child"
- **TS**: `const f2old = f2;` **today**: `val f2old: Double = ???` **required**: `val f2old: Double = f2`.
- **RAST**: `VariableDeclaration.children = [nameOrPattern, typeAnnotation?, initializer?]`.
- **Fix (A)**: `TsLowering.declParts(decl)` = (binding, annotation, initializer) by position
  after R1: first child is the binding (Identifier or pattern), a following child whose kind is
  a type node is the annotation, the last remaining child is the initializer. Same for
  `Parameter` (default value) and `PropertyDeclaration`.
- **Deps**: R1. **Size**: S. **Files**: normalize (three sites), and every fixture's `let x = y`.

#### R4 InferredSignature — read a function's type off its name
- **TS**: `function isType(token: PathToken, type: number) { return token.type === type; }`
  **today**: `: Any` **required**: `: Boolean`.
- **RAST**: `FunctionDeclaration.type` is absent; the name `Identifier` child carries the
  function type (`t204 = (token: PathToken, type: number) => boolean`), with `returnType`.
  Same for `rotate` (`[number, number]`), and for arrow functions (`ArrowFunction.type`).
- **Fix (A)**: `TsLowering.signatureOf(fn)` = the checker type on the name identifier, falling
  back to the syntactic annotation. The minter's `resolveTypeTree(node)` for the return type
  and the emitter's `resolveFunctionReturnType` both call it. A `void` return is `Unit`.
- **Deps**: none. **Size**: S. **Files**: parser (`isType`), normalize (`rotate`).

#### R5 ExportVisibility
- **TS**: `function tokenize…` (no `export`) **required**: `private def tokenize…`; same for
  `const COMMAND`, `interface PathToken`, `function degToRad/rotate/arcToCubicCurves`.
- **RAST**: `flags` contains `ExportKeyword` on exported declarations.
- **Fix (M/T)**: `Flags(isPrivate = true)` on the symbol / `private` modifier. A private
  nested type referenced from a private member is legal; a private type in a *public*
  signature is a checker error in TS already, so no new refusal.
- **Deps**: none. **Size**: S.

#### R6 BlockLayout — braces, and one indentation
- **Today**: `while ((d != ""))\n      if …` (Scala 3 does not open a region after `)`),
  `else\n  if` chains, double-indented nested case class.
- **Fix (T)**: every compound statement renders with braces; `else if` on one line;
  nested declarations indented once. On the minter path this is free (`TirEmitter`).
- **Deps**: none. **Size**: S. Also drop the redundant parentheses around every binary
  expression (`(((x1 - x2)) / 2)`), emitting them only when precedence differs — cosmetic, but
  the reference port is what the parity check diffs against.

#### R7 TypeAliasDeclaration
- **TS**: `type TokenType = 0 | 1 | 2;` **today**: nothing (the `PathToken` field references an
  undefined `TokenType`) **required**: `private type TokenType = Double` at top level (R8 decides
  the right-hand side; a string-literal union is `String`; an object literal type is a minted
  case class per R16; anything else is `Any` and counted `TypeAliasWidened`).
- **RAST**: `TypeAliasDeclaration` with `symbol.declarationType` → the union type.
- **Fix (M/T)**: `Tree.TypeDef`. **Deps**: R8. **Size**: S.

### Wave 1 — the type model

#### R8 NumberIsDouble, and literal-only arithmetic
- **Decision**: every `number`, number literal type and literal-union type is `Double`; a
  numeric literal renders as an Int literal (`0`, `7`) and relies on Scala's `Int` to `Double`
  widening, EXCEPT when both operands of `/`, `%`, `*`, `+`, `-` are of literal type in the
  RAST (kind `numberLiteral`), where it renders `4.0 / 3`. This is the one place where
  Int-only arithmetic silently differs from JS (`4 / 3 * r1`, twice in normalize; also `1 / 0`
  throws where JS gives `Infinity`).
- **Today**: `val sign: Int = …`, `PARAMS: Map[String, Int]`, `(4 / 3) * r1`.
- **Required**: `val sign: Double = if (largeArcFlag == sweepFlag) -1 else 1`, `4.0 / 3 * r1`.
- **Fix (A)**: `TsLowering.scalaTypeOf(rastType)` is the single type mapping (replaces the
  emitter's `rastTypeToScala` and the minter's `rastTypeToRepr`); it maps `numberLiteral`,
  `union of numberLiteral` and `number` to `Double`. `TsLowering.literalOnlyArithmetic(bin)`
  decides the literal spelling. Delete the "all-number-literal union is Int" arm.
- **Deps**: none. **Size**: S. **Files**: all.

#### R9 ArrayIsArrayBuffer
- **TS** / **today** / **required**:
  - `const tokens: PathToken[] = new Array();` / `Vector[PathToken] = ArrayBuffer.empty` /
    `val tokens: ArrayBuffer[PathToken] = ArrayBuffer.empty[PathToken]`
  - `const out: Segment[] = [];` / `Vector[Segment] = Vector.empty` / `ArrayBuffer.empty[Segment]`
  - `[cx, cy]` / `Vector(cx, cy)` / `ArrayBuffer(cx, cy)`
  - `[...data]` / `Vector(data*)` / `ArrayBuffer.from(data)`
  - `[cx1, cy1, ...data]` / `Vector(cx1, cy1, data*)` (a spread must be the only argument) /
    `ArrayBuffer(cx1, cy1) ++ data`
  - `tokens[tokens.length] = v` / `tokens(tokens.length) = v` (out of range on ArrayBuffer) /
    `tokens += v` when the index is `<same receiver>.length`; otherwise `tokens(i) = v`
    (an index beyond the end throws where JS extends — counted `ArrayHoleWrite`, not repaired)
  - `new Array(n)` / — / `ArrayBuffer.fill(n.toInt)(null)` refused for a primitive element:
    counted `ArrayConstructorLength`
  - `number[][]` / `Vector[Vector[Double]]` / `ArrayBuffer[ArrayBuffer[Double]]`
- **RAST**: type kind `array` with `elementType`; `SpreadElement` child of
  `ArrayLiteralExpression`; the `new Array()` call has type `any[]` (`t20`) — take the element
  type from the declaration's annotation (R3), never from the `new`.
- **Fix (A)** for the append-index test and the spread partitioning; **(M)** `NewArray` with
  the ArrayBuffer type, `Apply(Select(recv, +=))`; **(T)** the strings above.
- **Deps**: R8 (element type), R1. **Size**: M. **Files**: all.

#### R10 IndexCoercion — `arr[e]` when `e` is a `Double`
- **TS**: `tokens[index]`, `tokens[i]`, `params[i + 1][0]` **today**: `tokens(index)` (type
  error) **required**: `tokens(index.toInt)`, `params((i + 1).toInt)(0)`; an integer literal
  index stays `data(0)`.
- **Fix (A)**: `TsLowering.indexNeedsToInt(idx)` = the index expression's RAST type is
  `number`/`numberLiteral` and the expression is not an integer literal. **(T)** append
  `.toInt`; **(M)** `Apply(Select(idx, toInt))`. This is the boundary coercion that
  `NumberSplitTransform` later removes when it retypes the counter to `Int`; until that phase
  exists, this is what compiles.
- **Deps**: R8, R9. **Size**: S. **Files**: parser (8 sites), normalize (6).

#### R11 IndexSignatureMap
- **TS**: `const PARAMS: { [key: string]: number } = { A: 7, … }`, `PARAMS[token.text]`
  **today**: `Map[String, Int]` by text-sniffing, reads compile by luck
  **required**: `private val PARAMS: scala.collection.mutable.Map[String, Double] =
  scala.collection.mutable.Map("A" -> 7, …)`; `PARAMS(token.text)`.
- **RAST today**: `t13 = { kind: "object", text: "{ [key: string]: number; }", members: {} }` —
  the index signature is invisible. **Fix (E)**: for an object type, export
  `stringIndex: <typeId>` from `checker.getIndexInfoOfType(t, IndexKind.String)` and
  `numberIndex` likewise. Add both to `RastType`.
- **Fix (A)**: `scalaTypeOf` maps an object type with only a string index to
  `mutable.Map[String, V]`; an `ObjectLiteralExpression` whose contextual type (R15) is such a
  type becomes `Map(k -> v, …)`; `obj[k]` on it reads `obj(k)`; `obj[k] = v` writes `obj(k) = v`;
  `k in obj` is `obj.contains(k)`; `delete obj[k]` is `obj.remove(k)`.
- A read of a missing key throws where JS answers `undefined`: counted `IndexSignatureMiss` at
  every read whose result flows into a non-truthiness position (here: `paramsCount =
  PARAMS[token.text]`, three sites). A read under `typeof` is R28's.
- **Deps**: R8, R15. **Size**: M. **Files**: parser.

#### R12 TupleType
- **TS**: `function rotate(…): [number, number] { …; return [X, Y]; }`, `const r1 = rotate(…);
  r1[0]`, `[x1, y1] = rotate(…)`.
- **Today**: `: Any`, `return Vector(X, Y)`, `val r1: [?, ?][Double, Double]`, `r1(0)`.
- **Required**: `: (Double, Double)`, `return (X, Y)`, `val r1: (Double, Double) = rotate(…)`,
  `r1._1`, and destructuring from a tuple (R21) reads `._1`/`._2`.
- **RAST today**: `{ kind: "reference", text: "[number, number]", target: "t16" }` with
  `t16.text == "[?, ?]"`. **Fix (E)**: `checker.isTupleType(t)` → `{ kind: "tuple",
  elementTypes: [...] }`. **(A)**: `scalaTypeOf` renders `(A, B)`; an `ArrayLiteralExpression`
  whose own RAST type is a tuple renders as a tuple literal; `ElementAccessExpression` with a
  tuple receiver and an integer literal index renders `._n`; a non-literal index on a tuple is
  refused (`TupleDynamicIndex`).
- **Deps**: R1, R9. **Size**: M. **Files**: normalize.

#### R13 OptionalParameter
- **TS**: `recursive?: number[]` and a 9-argument call **today**: `recursive: Vector[Double]`
  (arity error) **required**: `recursive: ArrayBuffer[Double] | Null = null`.
- **RAST**: `Parameter.type = number[] | undefined` (`t82`), and after R1 the flag `optional`.
  A parameter with a default value (`x = 3`) keeps the default (R3's initializer).
- **Fix (M)**: `ValDef.rhs = Some(Literal(NullC))` — the frontends document already lists
  "a parameter `ValDef.rhs` that is a real `Term` renders as `= <expr>`" as an emitter change
  (item 1 of its emitter list). **(T)**: the string. Uses of the parameter are covered by R24
  (`if (recursive)` becomes `recursive != null`); reads inside the guarded branch compile
  without explicit nulls.
- **Deps**: R1, R9, R24. **Size**: S.

#### R14 UnionAndLiteralTypes
- Already right for the corpus (`(string | number)[]` → `ArrayBuffer[String | Double]`); this
  rule only records the rendering order (types sorted by name, `Null` last) so two exports
  fingerprint equal, and `boolean` (the checker's `true | false`) collapses to `Boolean`.
- **Size**: S.

#### R15 ObjectLiteralContextual — the engine stops knowing what a `Segment` is
- **TS**: `out.push({ key: 'M', data: [...data] })`, `tokens[tokens.length] = { type: COMMAND,
  text: RegExp.$1 }`, `const segment: Segment = { key: mode, data: params }`.
- **Today**: `Segment("M", Vector(data*))`, decided by `fieldNames.contains("key") &&
  fieldNames.contains("data")` — a library's field names hard-coded in `frontend-ts`.
- **Required**: `Segment(key = "M", data = ArrayBuffer.from(data))` (named arguments, so the
  case class's parameter order does not matter), `PathToken(`type` = COMMAND, text = …)`.
- **RAST today**: the literal's own type is the anonymous object type (`t196 = { type: 0; text:
  string }`), not `PathToken`. **Fix (E)**: export `contextualType:
  intern(checker.getContextualType(node))` on `ObjectLiteralExpression` and
  `ArrayLiteralExpression`. For the three shapes above it is `PathToken`, `Segment` (from
  `push`'s parameter) and `Segment` (from the annotation).
- **Fix (A)**: `TsLowering.nominalTarget(literal)` = the contextual type's symbol when it is an
  interface/class the program declares (its `declarationType` symbol has `flags: [interface]`
  and a `parent` that is a module file) — else `None`. **(M)**: `Apply(New(T), NamedArg…)`
  (`Tree.NamedArg` is item 2 of the frontends document's emitter list; until it exists, pass
  arguments in the interface's declaration order). `None` with an index-signature contextual
  type is R11's `Map(...)`; `None` otherwise mints a case class named from the sorted property
  set (`Point_x_y`), counted `StructuralTypeNamed`, one per distinct shape per program.
- **Deps**: R1, R9, R16. **Size**: M. **Files**: all (30 sites).

#### R16 InterfaceToCaseClass
- **TS**: `interface PathToken { type: TokenType; text: string }`, `export interface Segment {
  key: string; data: number[] }` **today**: `final case class …` at the wrong indentation, with
  `val`. **Required**: `final private case class PathToken(`type`: TokenType, text: String)`
  nested in the module object; `final case class Segment(key: String, data:
  ArrayBuffer[Double])` at top level.
- **Rule**: an interface with only `PropertySignature` members, no `extends`, and no class in
  the program implementing it, is a case class; a field is `var` iff the program contains an
  assignment whose left side is a `PropertyAccessExpression` resolving to that property symbol
  (`resolvedSymbol` on the member identifier) — `readonly` forces `val`. Any other interface is
  a `trait` with abstract `def`s (methods) and `val`/`var`s (properties); an object literal
  against it is `new T { … }` per R15.
- **RAST**: `PropertySignature.symbol` → `symbols[s].parent` is the interface; a
  `ReadonlyKeyword` flag.
- **Fix (M)**: `ClassDef` with `Flags(isCase = true, isFinal = true)` and constructor `ValDef`s.
- **Deps**: R8, R9. **Size**: S (case class) / M (trait + anonymous instance path).

#### R17 EvolvingArrayType
- **TS**: `const curves = [];` … `curves.push([…])` … `return curves;` (declared type
  `number[][]`) **today**: `val curves: Vector[Any]` **required**: `val curves:
  ArrayBuffer[ArrayBuffer[Double]] = ArrayBuffer.empty`.
- **RAST**: the declaration's type is `any[]` (`t119`); every later reference to symbol
  `s124` carries the evolved type (`return curves` → `t80 = number[][]`).
- **Fix (A)**: `TsLowering.evolvedType(decl)` = when the declared type is `any[]`/`never[]`
  with no annotation, the RAST type of the LAST reference to the declaration's symbol in the
  enclosing function that is not itself `any[]`/`never[]`; none found → `ArrayBuffer[Any]` and
  counted `EvolvingArrayUnresolved`.
- **Deps**: R9. **Size**: M. **Files**: normalize.

### Wave 2 — statements and expressions

#### R18 ForLoop
- **TS**: `for (let i = index; i < index + paramsCount; i++) { … }`, `for (let i = 0; i <
  params.length; i += 3) { … }` **today**: TODO comments **required**:
  ```scala
  { var i: Double = index
    while (i < index + paramsCount) { <body>; i += 1 } }
  ```
  (with R10's `.toInt` at `tokens(i.toInt)`). A `continue` in the body needs a `boundary`
  around the body ahead of the update; `break` a `boundary` around the loop — `TirEmitter`
  already does both for `Tree.For`.
- **RAST**: children `[VariableDeclarationList | expression, cond, update, Block]` — the
  init is a bare `VariableDeclarationList` (not a statement), the update is an expression.
  Either may be absent, so classify by kind, not position.
- **Fix (M)**: `Tree.For(init = ValDefs, cond, update = List(term), body)` — the minter's
  arm exists but drops the update (`Nil`) and misses the bare list; fix both. **(T)**: the
  emitter's `emitForLoop` must handle a `VariableDeclarationList` and an expression update.
- **Deps**: R1, R19. **Size**: M. **Files**: parser, normalize.

#### R19 IncDecStatement
- **TS**: `index++;` (statement) vs `x = i++` (value) **today**: `{ index += 1; index }` in
  both **required**: `index += 1` as a statement; `{ val p = index; index += 1; p }` for a
  post-increment value; `{ index += 1; index }` for a pre-increment value. The value forms are
  the working rules' post-increment row; only the statement form is new.
- **Fix (M)**: `Tree.IncDec(target, op, post)` — `TirEmitter` renders statement vs value
  position already; the minter currently discards the operator (`mintExpr` returns the
  operand). **(T)**: statement-position check in `emitStatement`.
- **Deps**: none. **Size**: S.

#### R20 SwitchCases
- **TS**:
  ```ts
  switch (key) { case 'C': case 'c': tokens.push(…); break; default: tokens.push(...data); break; }
  ```
  **today**: `case "C" =>` (empty, so `'C'` does nothing) then `case "c" => …`; `// break`
  **required**: `case "C" | "c" => …`; `case _ => …`; a switch without `default` gains
  `case _ => ()` (JS falls out); a case whose sole statement is a `Block` (`case 'c': { … }`)
  emits the block's statements; a trailing unlabelled `break` is stripped; a non-empty case
  that falls through duplicates the next case's tail into the arm (the Java rule); a `break`
  in the middle of an arm needs a named `boundary` around the arm.
- **RAST**: `CaseClause.children = [label, stmt…]`, `DefaultClause.children = [stmt…]`;
  `BreakStatement` last.
- **Fix (M)**: `Tree.Match` with `CaseDef(labels = List(...))` grouping consecutive empty
  clauses; `TirEmitter.matchStr` does fallthrough duplication and the fall-out arm when told
  the switch is a statement. **(T)**: `emitSwitch` grouping.
- **Deps**: R1. **Size**: M. **Files**: all.

#### R21 ArrayDestructuring
- **TS** / **today** / **required**:
  - `[cx, cy] = data;` / `Vector(cx, cy) = data` / `cx = data(0); cy = data(1)`
  - `[x1, y1] = rotate(x1, y1, -angleRad);` / same / `val bp$t1 = rotate(x1, y1, -angleRad);
    x1 = bp$t1._1; y1 = bp$t1._2` (tuple right side per R12; a temp whenever the right side is
    not a plain identifier, so it is evaluated once)
  - `const [x, y] = data;` / `val data: Any = ??? /* ArrayBindingPattern */` / `val x: Double =
    data(0); val y: Double = data(1)`
  - `const { key, data } = seg` (object pattern in a declaration) / — / `val key = seg.key; val
    data = seg.data`
  - a pattern with a rest element, a default, or a nested pattern: refused, counted
    `DestructuringShape` (none in the corpus).
- **RAST**: assignment form is `BinaryExpression(=)` with an `ArrayLiteralExpression` left
  operand; declaration form is `VariableDeclaration` whose first child is
  `ArrayBindingPattern`/`ObjectBindingPattern` of `BindingElement`s. `nameOf` currently picks
  the RHS identifier as the declaration's name — R3's `declParts` fixes that.
- **Fix (A)**: `TsLowering.destructure(lhsPattern, rhsType)` → list of (target, projection)
  where projection is `Index(n)` for arrays and `TupleN(n)` for tuples; **(M)** a `Block` of
  `Assign`s / `ValDef`s; **(T)** the strings.
- **Deps**: R1, R3, R9, R12. **Size**: M. **Files**: absolutize (5), normalize (7).

#### R22 ForOf — including a body that jumps
- **TS**: `for (const { key, data } of segments) { … }` **today**: `for (_item <- segments)`
  + `val key = _item.key` — compiles. **Required**: the same with braces and a name that cannot
  clash (`bp$seg`), or `for (Segment(key, data) <- segments)` when the element type is a case
  class (nicer, and what the reference port's shape suggests); when the body contains `return`,
  `break` or `continue`, a `for` comprehension is a lambda and `return` is illegal, so lower to
  `val bp$it = segments.iterator; while (bp$it.hasNext) { val bp$seg = bp$it.next(); … }` with
  the loop-level `boundary` the working rules prescribe.
- **Fix (A)**: `TsLowering.bodyJumps(block)`; **(M)**: `Tree.ForEach` (the engine emitter
  decides the lowering); **(T)**: both forms.
- **Deps**: R1, R21. **Size**: S (plain) / M (jumping body). **Files**: all (no jumping body
  in the corpus; `return` under a `while` is fine).

#### R23 ReassignedParam
- **TS**: `function tokenize(d: string) { … d = d.substr(…) … }`; in `arcToCubicCurves`
  `x1 y1 x2 y2 r1 r2` are reassigned (directly and through destructuring).
- **Today**: `d = d.substring(…)` — assignment to a `val` parameter.
- **Required**: `private def tokenize(d$in: String): … = { var d: String = d$in; … }`. The
  reference port spells it `d0`; the name is a parity detail, the shape is the rule.
- **Fix (A)**: `TsLowering.reassignedParams(fn)` = parameter symbols that appear as the target
  of an assignment, a compound assignment, an `IncDec`, or a destructuring element, anywhere in
  the body including nested arrow functions (a closure capturing a `var` is fine in Scala).
  **(M)**: rename the parameter symbol, prepend a mutable `ValDef` to the body — the same shape
  `ScalaPrinter.reassignedParams` gives constructor parameters; reuse its naming. **(T)**: same.
- **Deps**: R21 (destructuring targets). **Size**: M. **Files**: parser (1), normalize (6).

#### R24 Truthiness
- **TS** / **today** / **required**:
  - `if (d.match(re))` / `if (d.`match`(…))` / `if (JsRegExp.matchOf(d, re) != null)` (R32
    gives a nullable reference; exact test)
  - `if (recursive)` / `if (recursive)` / `if (recursive != null)`
  - `sweepFlag && f1 > f2` / same / `Truthy.num(sweepFlag) && f1 > f2`
  - `!sweepFlag` / `!sweepFlag` / `!Truthy.num(sweepFlag)`
  - `(i % 2) ? a : b` / `(if (((i % 2))) …)` / `if (Truthy.num(i % 2)) a else b`
  - `a || b` as a VALUE (not in the corpus) / — / `{ val t = a; if (Truthy(t)) t else b }`
- **Rule (A)**: `TsLowering.asCondition(expr)` — an operand in a boolean position (`if`,
  `while`, `for` condition, `?:` test, `!`, `&&`, `||`) whose static type is not `boolean`:
  `string` → `x.nonEmpty` (exact: `""` is the only falsy string); a nullable reference type
  (`T | undefined`, `T | null`, `RegExpMatchArray | null`) → `x != null`; `number` →
  `Truthy.num(x)` (`0`, `-0` and `NaN` are falsy; `!= 0` alone is wrong for `NaN`); `any`/
  `unknown`/union of primitives → `Truthy(x)`; each helper use counted `TruthinessRuntime`.
- **Runtime (R)**: `Truthy` exists; add `Truthy.num(d: Double): Boolean` to avoid boxing.
- **Deps**: R1, R8. **Size**: M. **Files**: parser (3), absolutize (3), normalize (6).

#### R25 StrictEquality
- **TS**: `token.type === type`, `d !== ''`, `mode === 'BOD'` **today**: `==`/`!=` (right by
  luck: all string/number) **required**: `==` when both operands' static types are
  `string`/`number`/`boolean`/literal/`null`/`undefined`, `eq`/`ne` when both are object types,
  `==` against a `null` literal. `==`/`!=` (loose) between differing primitive types is
  refused (`LooseEquality`).
- **Fix (A)**: `TsLowering.equalityOp(bin)`. **Size**: S.

#### R26 TemplateLiteral
- **TS**: `` `${data[1]},` ``, `` `${parseFloat(RegExp.$1)}` `` **today**: `"" + data(1)` (the
  `,` is lost) **required**: `JsNumber.toString(data(1)) + ","`, `JsNumber.toString(JsNumber.parseFloat(JsRegExp.$1))`.
- **RAST**: `TemplateExpression.children = [TemplateHead, TemplateSpan…]`, each `TemplateSpan
  .children = [expr, TemplateMiddle | TemplateTail]`; the head/tail `value` is the literal text
  (empty string exported as absent `value`).
- **Fix (A)**: `TsLowering.templateParts(node)` = alternating literal/expression list with
  every tail kept, empty literals dropped; a `number`-typed part is wrapped in
  `JsNumber.toString` (R33), any other non-string part in `JsValue.str`. **(M)**: `Apply(+)`
  chain; **(T)**: the same string.
- **Deps**: R1, R33. **Size**: S. **Files**: parser (4).

#### R27 ArrowFunction and FunctionExpression
- **TS**: `(d, i) => (i % 2) ? … : …`, `function (curve) { out.push(…); }` **today**: `(d, i)
  => ??? /* EqualsGreaterThanToken */`; `curve => { out += …; }` **required**: `(d, i) => if
  (…) d + cy else d + cx`; `curve => { out += Segment(…) }`.
- **Rule (A)**: after R1 the body is the last child; an expression body is the lambda's
  result; a block body is a `Block`; a `return` inside a block-bodied lambda that is not the
  last statement needs `boundary` (the lambda cannot `return`); a `function` expression that
  uses `this` is refused (`DynamicThis`, the frontends document's row); `arguments` likewise.
  Parameter types come from the arrow's own function type (`t35`), so `(d, i)` is
  `(Double, Double)` — but see R30 for the receiver-driven arity rewrite that makes `i` an
  `Int`.
- **Fix (M)**: `Tree.Lambda` (exists). **Deps**: R1, R4. **Size**: S.

#### R28 TypeofTest
- **TS**: `typeof PARAMS[mode] === 'number'` **today**: `??? /* typeof … */ == "number"`
  **required**: `PARAMS.contains(mode)` — the operand is an index-signature read whose value
  type is exactly the tested tag, so the test is presence. General form: `typeof x === "T"` →
  `JsTypeof(x) == "T"` with a runtime `JsTypeof(x: Any): String` returning `"number"` for
  `Double`/`Int`/`Long`, `"string"`, `"boolean"`, `"function"` for `FunctionN`, `"object"` for
  `null` and everything else; `typeof x === "undefined"` → `x == null`. Counted `TypeofObject`
  at every use of the runtime helper (`"object"` and `"undefined"` cannot be distinguished).
- **RAST**: `TypeOfExpression.children = [operand]`; the operand `ElementAccessExpression`'s
  receiver type is the index-signature object (R11).
- **Deps**: R11. **Size**: M (S for the presence special case). **Files**: parser.

### Wave 3 — vocabulary: JS library members, dispatched by RESOLVED SYMBOL

Today the emitter dispatches on the rendered text (`s.endsWith(".push")`), which fires on any
member called `push` and misses a `push` reached through a variable named `Math`. The RAST
resolves every member: `d.match` → `resolvedSymbol: s36` with `symbols.s36 = { name: "match",
parent: s78 }` and `symbols.s78 = { name: "String", flags: [interface, variable] }`. The
dispatch key is therefore `(parent.name, member.name)` — `("String", "match")`,
`("Array", "push")`, `("Math", "asin")`, `("RegExpConstructor", "$1")`, and for free functions
`("", "parseFloat")` — read off `symbols`, never off text. **Fix (E)**: also export the
declaring file for a library symbol (`lib.es5.d.ts` and friends) as `lib: true`, so a
user-defined `push` on a user class is never mistaken for `Array.prototype.push`.

Each row below is one entry in a `TsVocab` table (`(owner, member) → rewrite`), the TypeScript
analogue of the JDK vocabulary the Java path carries. Every rewrite is counted under a `TS-L`
lane by row so a port can see which library members it depends on.

#### R30 ArrayMethods
| member | TS | required Scala | note |
|---|---|---|---|
| `push(v)` statement | `tokens.push(key)` | `tokens += key` | |
| `push(a, b, …)` statement | `tokens.push(data[0], `${…}`, …)` | `tokens ++= Seq[String \| Double](data(0), …)` | the element type is the receiver's, spelled explicitly so the union is not widened |
| `push(...xs)` | `tokens.push(...data)` | `tokens ++= data` | a spread argument |
| `push(…)` as a value | — | `{ tokens += v; tokens.length }` | JS returns the new length |
| `length` | `tokens.length` | `tokens.length` | an `Int`; `arr.length = 0` (truncation) → `arr.clear()`; other writes refused |
| `map(f)` arity 1 | `xs.map(x => …)` | `xs.map(x => …)` | |
| `map((x, i) => …)` arity 2 | `data.map((d, i) => …)` | `data.zipWithIndex.map((d, i) => …)` | `i` is now an `Int`; `i % 2` stays exact |
| `map((x, i, arr) => …)` arity 3 | — | refused unless the receiver is a plain identifier, then `arr` is bound to it | `CallbackArity` |
| `forEach(f)` | `curves.forEach(function (curve) { … })` | `curves.foreach(curve => { … })` | a `return` inside the callback is `boundary` (R27) |
| `concat(a)` | `[m2, m3, m4].concat(params)` | `ArrayBuffer(m2, m3, m4) ++ params` | an array-typed argument is flattened one level (JS semantics); a non-array argument is `:+ a` |
| `join(sep)` | `tokens.join(' ')` | `tokens.iterator.map(JsValue.str).mkString(" ")` | number elements print JS-style (R33); `join()` with no argument uses `","` |
| `indexOf`, `slice`, `splice`, `filter`, `reduce`, `some`, `every`, `reverse`, `sort` | (not in this corpus; in rough.js) | one row each when reached | `sort()` with no comparator is a STRING sort in JS — refuse without a comparator |

- **Fix (A)**: `TsLowering.callbackArity(arg)`; **(M)**: `Apply` chains; **(T)**: strings.
- **Deps**: R1, R9, R27. **Size**: M. **Files**: all.

#### R31 StringMethods
| member | TS | required | note |
|---|---|---|---|
| `substr(start)` | `d.substr(n)` | `d.substring(n.toInt)` | |
| `substr(start, len)` | — | `d.substring(s, s + len)` (temps if not identifiers) | `substr` and `substring` differ in the second argument |
| `length` | | `d.length` | |
| `match(re)` | `d.match(/…/)` | R32 | |
| `charAt`, `indexOf`, `split`, `replace`, `trim`, `toLowerCase`, `startsWith` | rough.js | one row each; `split` with a regex and `replace` with `$1` in the replacement have JS-specific semantics — count | |
| `s[i]` | — | `s.charAt(i.toInt).toString` | JS yields a one-character string, `undefined` out of range |

- **Size**: S for this corpus.

#### R32 RegExp — literals and the legacy static match state
- **TS**: `d.match(/^([ \t\r\n,]+)/)`, `RegExp.$1`, `/^(([-+]?[0-9]+(\.[0-9]*)?|…)/`.
- **Today**: `"^([ \t\r\n,]+)".r` (a literal tab, harmless), `"…(\.[0-9]*)…".r` (invalid escape
  — the compile error), `d.`match`(…)`, `_m.group(1)` unbound.
- **Required**:
  - literal: `"^(([-+]?[0-9]+(\\.[0-9]*)?|[-+]?\\.[0-9]+)([eE][-+]?[0-9]+)?)".r` — the body
    with every backslash doubled and `"` escaped; flags `i`/`m`/`s` become `(?i)`/`(?m)`/`(?s)`
    prefixes; `g` is not a pattern flag but changes `match`'s result (all matches, no groups) —
    honoured in the `match` rewrite below; `u`/`y`/`d` refused (`RegExpFlag`). Hoisting the
    regex to a `private val` in the module object is an optimisation; JS also constructs the
    literal per evaluation, so inline is exact.
  - `s.match(re)` (non-global) → `JsRegExp.matchOf(s, re)`: a runtime function returning
    `JsMatch | Null` and, on success, recording the match in `JsRegExp`'s legacy statics.
    `s.match(re)` with `g` → `JsRegExp.matchAll(s, re)` (`ArrayBuffer[String] | Null`).
  - `RegExp.$1` … `$9`, `RegExp.lastMatch` → `JsRegExp.$1` etc., reading the recorded match.
    The JS statics are process-global; the runtime keeps them in a `ThreadLocal` on the JVM
    (documented, counted once per program as `RegExpLegacyStatics`).
  - `re.test(s)`, `re.exec(s)`, `s.replace(re, …)`, `s.split(re)`: one row each when reached.
- **Runtime (R)**: `balticporter.runtime.js.JsRegExp` (`matchOf`, `matchAll`, `$1..$9`,
  `lastMatch`), `JsMatch` (`apply(i)`, `index`, `input`, `length`) — JS's `RegExpMatchArray`
  shape, a standalone class per the working rules (never a Scala collection trait).
- **Dialect**: JS and Java regex syntax agree on everything these three patterns use; the
  differences (`\d` Unicode, `$` at a trailing newline, named-group syntax, lookbehind
  limits) are listed in the row's note and counted `RegExpDialect` when a pattern uses one
  of the differing constructs (a static scan of the pattern text).
- **Deps**: R24 (the call's result is tested for truthiness), R31. **Size**: M. **Files**: parser.

#### R33 JsNumber — parsing and printing numbers the JS way
| TS | today | required | semantics |
|---|---|---|---|
| `parseFloat(s)` | `s.toDouble` | `JsNumber.parseFloat(s)` | longest numeric PREFIX, `NaN` if none; `toDouble` throws |
| `+s` (unary plus) | `s.toDouble` | `JsNumber.toNumber(s)` | whole string, trimmed; `""` is `0`; `NaN` otherwise |
| `x.toFixed(n)` | `x.toFixed(9)` | `JsNumber.toFixed(x, 9)` | ECMA-262 `Number.prototype.toFixed`: half-up, `.` separator, `n` digits; the reference port's `FormatUtil.toFixed` is the model |
| `` `${x}` ``, `"" + x`, `String(x)`, `join` | `"" + x` | `JsNumber.toString(x)` | integral values print without `.0`; exponent form beyond 1e21 / below 1e-6 |
| `Number(s)`, `parseInt(s, r)`, `isNaN(x)` | — | one row each when reached | |

- **Runtime (R)**: `balticporter.runtime.js.JsNumber`; `JsValue.str(x: Any)` dispatches to it
  for numbers and to `String.valueOf` otherwise (`null` prints `"null"`; `undefined` is not
  representable).
- **Deps**: none. **Size**: M (the printing algorithm is the work; the reference port's
  `jsNum` covers the SVG range and states its fallback). **Files**: parser, normalize.

#### R34 MathAndError
- `Math.abs/sqrt/sin/cos/tan/asin/atan2/floor/ceil/min/max/pow/PI` → `java.lang.Math.*`
  (same IEEE semantics within ULPs; `Math.round` differs — JS rounds half toward +∞ and returns
  a double, Java returns a `long` — so `Math.round(x)` → `JsMath.round(x)`; `Math.max()` with
  no arguments is `-Infinity` in JS). One row each; only the ones used are emitted.
- `new Error(m)` → `new RuntimeException(m)`; `err.message` → `err.getMessage`; `throw x` where
  `x` is not a `Throwable` (JS allows any value) → `throw new JsThrown(x)`, counted.
- **Size**: S.

#### R35 CrossModuleReference and the barrel
- A reference whose `resolvedSymbol.parent` is another file's module symbol (`s76 =
  "…/parser"`) is qualified with that module's object name: `Parser.parsePath(...)`. A type
  emitted at top level (`Segment`) needs no qualification inside one package; across the
  `modulePackages` boundary the package prefix is emitted (fully qualified, no imports).
- `index.ts` (`export { parsePath, serialize } from './parser.js'`) → `object PathDataParser {
  export Parser.{parsePath, serialize}; … }` — the reference port's exact shape. Today the
  emitter skips `index`.
- **Size**: S.

---

## 4. Behavioural rows — right text, wrong run

These do not stop the compile; they stop the reference tests. Each is either covered by a rule
above or is a counted divergence.

| construct | JS | naive Scala | handled by |
|---|---|---|---|
| `tokens.join(' ')` with numbers | `"1 2.5"` | `"1.0 2.5"` | R33 through R30's `join` row |
| `` `${parseFloat(x)}` `` | `"1"` | `"1.0"` | R26 + R33 |
| `+numbeToken.text` on a bad token | `NaN` | throws | R33 (`toNumber`) |
| `PARAMS[mode]` for an unknown `mode` | `undefined`, then `typeof … === 'number'` is false | throws `NoSuchElementException` | R28 handles the `typeof` site exactly; the two bare reads are counted `IndexSignatureMiss` (unreachable here — the tokenizer only emits known letters) |
| `switch` without `default` | falls out | `MatchError` | R20's fall-out arm |
| `4 / 3 * r1` | `1.333…*r1` | `1 * r1` | R8 |
| `sweepFlag && …` with `sweepFlag = NaN` | false | `NaN != 0` is true | R24 (`Truthy.num`) |
| `[cx, cy] = data` when `data` has one element | `cy = undefined` | throws `IndexOutOfBounds` | counted `DestructuringShort` at every array destructuring (the reference port throws too) |
| `new Error(msg)` class identity | `Error` | `RuntimeException` | R34, divergence `ErrorClass` |
| `arr[arr.length + 5] = v` | extends with holes | throws | R9, counted `ArrayHoleWrite` |
| regex `RegExp.$1` after a FAILED match | keeps the previous match | same (recorded only on success) | R32 |

---

## 5. The TIR path — what the minter needs before any rule is constructed there

In order; each is a prerequisite for printing anything, independent of the rules above.

| # | work | why | size |
|---|---|---|---|
| M0 | **Intern symbols.** One TIR `Symbol` per RAST symbol id, owner from `parent` (a module file symbol becomes the module object's symbol; `__object` parents become the minted case class), flags from `flags` (`function`/`variable`/`property`/`interface`/`typeAlias`), `fullName` = module object + `#` + name for owned symbols and `<lib owner>#<name>` (`String#match`, `Array#push`, `Math#asin`, `<global>#parseFloat`) for library symbols. Every `Ident`, `Select`, `Apply.method` and `ValDef.symbol` carries the interned id (from `resolvedSymbol` / `symbol`). Today every one is `SymId.None`. | the engine's emitter renders a name only through the symbol table | M |
| M1 | **Operators.** `BinaryExpression` → `Apply(Select(l, opId(op)), List(r), opId(op), tpe)` with the `scala.<op>#+` external-symbol convention `SpoonTirBodyExprs.opId` uses (move `opId` into `api` so both frontends share it); `=` → `Tree.Assign`; `+=` etc. → `Assign(lhs, Apply(+))`; prefix `-`/`!`/`~` → `unApply`; `++`/`--` → `Tree.IncDec`. Today the operator is lost. | | S |
| M2 | **Types.** `rastTypeToRepr` returns `TypeRef(NoPrefix, SymId.None)` for `string`, `number` and `boolean` alike — three different types, one representation. Intern `scala.Double`, `java.lang.String`, `scala.Boolean`, `scala.collection.mutable.ArrayBuffer` (applied), `scala.Tuple2`, `scala.Null`, and the program's own class symbols as `TypeRef`s; unions as `OrType`; functions as `MethodType`. `TsLowering.scalaTypeOf` (R8) is the single decision; the minter maps its answer to `TypeRepr`. | | M |
| M3 | **Member access.** `Select(obj, sym)` with the resolved member; `ArrayAccess` only when the receiver type is an array; an index-signature receiver becomes `Apply(Select(map, apply))`. | R11, R30 | S |
| M4 | **Object literals** → `Apply(New(T), args)` (R15) with `Tree.NamedArg` once it exists; until then positional in declaration order. Today: `Unportable`. | R15 | S |
| M5 | **Statements the minter drops**: `ForStatement.update` (emitted `Nil`), `PrefixUnary`/`PostfixUnary` operators, `TypeOfExpression` (emits the literal `"object"` — a wrong ANSWER, not a marker; replace by R28 or an `Unportable`), `DeleteExpression` (emits `true`), `ObjectBindingPattern`/`ArrayBindingPattern` (R21). Every construct the minter cannot lower must be an `Unportable` marker, never a plausible literal. | the spec's "0 unportable" is only meaningful when refusals are marked | S |
| M6 | **Module object per file.** Today each `FunctionDeclaration` mints its own `<name>$module` class and each `VariableStatement` mints a fresh `<file>$module` (four of them for `parser.ts`, one per `const`), so `parser.ts` becomes ten units — which is where the spec's "15 units for 4 files" comes from (10 + 1 + 4 + 0). One `ClassDef` per file with `Flags(isModule = true)`, named after the file stem (capitalised), holding every top-level function, variable, private interface and alias; exported interfaces/case classes are sibling top-level units. | R35, and `members.tsv` lists members per unit | S |

After M0–M6 the engine's emitter prints the TIR, and R6, R18 (`For`), R19 (`IncDec`), R20
(fallthrough duplication, fall-out arm), R22 (`ForEach`), R23 (reassigned parameters, the
constructor mechanism generalised to methods) come from `TirEmitter` and its existing lowering
rather than from new code. What remains minter-side is the analysis half of each rule.

---

## 6. Order of work, with what each step unblocks

Sizes: S under a day, M one to three days, L a week. Dependencies are the rule numbers.

| step | rules | unblocks | compiles after this? |
|---|---|---|---|
| 1 | R1, R2, R3, R4, R5, R6, R7 (all S) | every positional access; the `???`s from tokens and identifier initializers | no — types still wrong |
| 2 | R8, R9, R10, R14 | the array/number model; most type errors | no — object literals, switch, for |
| 3 | R11 (E+A), R15 (E+A), R16 | index signatures and object literals without library names in the engine | no |
| 4 | R18, R19, R20, R26, R27 | `for`, `++`, `switch`, templates, arrows | **parser.ts compiles except the regex/vocab sites** |
| 5 | R24, R25, R28, R23 | truthiness, `typeof`, reassigned `d` | |
| 6 | R30 (push/length/join/map/forEach/concat), R31 (substr), R32, R33, R34 | vocabulary | **parser.ts compiles** |
| 7 | R21, R30's arity-2 `map` | destructuring, `(d, i) =>` | **absolutize.ts compiles** |
| 8 | R12, R13, R17, R22 | tuples, optional parameter, evolving arrays | **normalize.ts compiles** |
| 9 | R35, behavioural rows (section 4), R33's printing algorithm | `index.ts` barrel; the reference port's tests | **reference tests run** |
| 10 | M0–M6, then re-point every rule's construction half at TIR | the engine path; delete `TsToScalaEmitter` | the same three files through `TirEmitter` |

What each file needs:

- **parser.ts alone**: R1–R11, R15, R16, R18–R20, R23–R26, R28, R30 (`push` 1/n/spread,
  `length`, `join`), R31 (`substr`), R32, R33 (`parseFloat`, `toNumber`, `toString`), R34.
- **absolutize.ts adds**: R21 (assignment form), R27, R30 (`map` arity 2), R24 on `i % 2`, R9's
  spread copy and `ArrayBuffer(a, b) ++ rest`.
- **normalize.ts adds**: R12, R13, R17, R21 (declaration form, tuple right side, parameters as
  targets), R23 (six parameters), R24 (`&&`/`!`/optional), R30 (`forEach`, `concat`), R33
  (`toFixed`), R8's literal arithmetic, R18's `i += 3` update, and a local `const r1` that
  shadows the (now `var`) parameter `r1` inside the loop block — legal Scala, no rule, but the
  reference port renamed it (`ra`/`rb`/`rc`); the parity check will report the name.

Rough totals: wave 0 is 7×S; wave 1 is 3×S + 6×M; wave 2 is 5×S + 6×M; wave 3 is 2×S + 4×M;
the minter foundation is 4×S + 2×M. Roughly four to five engineer-weeks to the reference tests
through the text emitter, plus two for the minter foundation and the re-pointing.

---

## 7. Gates and tests

1. **Per rule**: a `.ts` snippet under `frontend-ts/src/test/resources/rast/rules/`, exported
   once (the exporter is deterministic — two exports byte-equal), and an expected Scala
   string. `TsLoweringSpec` asserts the analysis half directly (`asCondition`,
   `reassignedParams`, `destructure`, `callbackArity`, `templateParts`, `evolvedType`); the
   emitter spec asserts the text.
2. **Compile gate**: a scratch sbt subproject (the ports-as-subprojects layout, generated into
   `src_managed/`) whose `sourceGenerators` runs the emitter over the three fixtures; the
   lane is red until scalac is clean, and `errors.tsv` by member is the iteration table. Do
   not read the emitted files to find the failing member.
3. **Behaviour gate**: the reference port's tests for `pathdata` (in `ssg-graphs-commons`)
   compiled against the emitted objects with the parity name table applied per receiver
   (`Vector` → `ArrayBuffer`, `Option` → `| Null`, `tokenType` → `type`); every failure is a
   row in section 4 or a bug, never a hand edit.
4. **Parity**: `ApiParityCheck` against the hand port, every divergence classified — the
   expected list is the "recorded as" column of section 2 plus `PathDataParseError`,
   `boundary` vs `return`, and the renamed loop locals.
5. **The engine-purity grep** from the working rules, extended with `Segment|PathToken|pathdata`,
   runs green over `frontend-ts` once R15 lands.

---

## 8. Not in scope, deliberately

- `NumberSplitTransform` (`Double` → `Int` where the reference has it): R10's `.toInt` is the
  compile-first stand-in; the phase needs the flow substrate the frontends document names.
- A `Vector` retarget for provably unmutated arrays: a `CollectionsTransform` family, after the
  phase can read TS types.
- Comments: the RAST carries `comments` on nodes (2 in absolutize); emitting them is the
  trivia mechanism's job once the minter path exists (`TriviaCheck` compares text to text).
- Classes, getters/setters, generics, `async`, enums, namespaces, `for…in`, optional chaining,
  `??`: none in path-data-parser; the frontends document's lowering table has their rows.
