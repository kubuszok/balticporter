# Non-Java frontends for Baltic Porter — TypeScript, Dart, JavaScript, Ruby

Design document. Status: proposal, nothing built. Scope: can the engine regenerate SSG's
non-Java ports (`ssg-sass`, `ssg-katex`, `ssg-mermaid`, `ssg-js`, `ssg-minify`, and the rough.js
family under `ssg-graphs-commons`) deterministically from their pinned upstream sources, and what
has to change in the engine for that to be true.

The short answer, argued below:

- **TypeScript: yes, for the typed, DOM-free parts.** The TS compiler API gives a fully resolved
  program (symbols, declared and flow-narrowed types, chosen overload per call), which is exactly
  the input the engine's typed IR (TIR) was designed around. The IR needs three new tree nodes, a
  set of new refusal kinds and one new mechanism (integer inference for `number`); the emitter
  needs three small extensions. The rough.js family
  (~3.3k LOC of strict, DOM-free TS with a hand port to compare against) is the right first target.
- **Dart: yes, and it is the better-typed of the two,** but the largest by far (dart-sass is
  ~60k LOC before dropping the JS/embedded/CLI trees). Dart's analyzer gives resolved ASTs of the same
  quality as Spoon's; the IR needs named arguments, a lowering for named/factory constructors,
  extensions and `late`, and Dart 3 patterns. sass-spec (13.9k cases) is a differential oracle
  no other corpus module has.
- **JavaScript (Terser): no, not mechanically.** Terser defines its AST classes at runtime through
  a `DEFNODE` macro; no static frontend sees their members. A library-specific rule could unfold
  the macro, but the members would still be untyped. Keep the hand port.
- **Ruby (jekyll-minifier): no.** Untyped, and the module is a thin wrapper; the minifiers it wraps
  are ports of Java originals, which the existing Java frontend could take instead.
- **Mermaid: partially.** Diagram databases and utilities are ordinary TS; renderers are d3 and the
  parsers are jison/langium output. Out of the first four phases.

Sections: 1 what the engine has today · 2 the IR audit · 3 the SSG corpus · 4 the TypeScript
frontend · 5 the Dart frontend · 6 JavaScript, Ruby, Mermaid · 7 the phased plan · 8 open
decisions and where each is recorded.

---

## 1. What the engine has today, read from the code

Paths are under `balticporter/` in this repository.

### 1.1 There are two frontend paths, and only the frozen one has an interface

- `api/src/main/scala/balticporter/core/Frontend.scala` declares `trait Frontend { def parse(cfg:
  FrontendConfig): List[BUnit] }`. `BUnit` is the **frozen BIR** (`core/Bir.scala`, header: "FROZEN
  — the BIR path. New work goes on the TIR"). Only `runner/M0Pipeline.scala` still calls it.
- The TIR path has **no interface**. `runner/PortRun.scala` (`translateOnce`, ~line 1657) calls
  `SpoonTir.buildModel(frontendConfig, lenient)` and then `SpoonTir.fromTypes(types, subs, catalog,
  annotations, internTypes)` directly, on Spoon's `CtType` values. `engine` depends on
  `frontend-spoon` for this reason (`DESIGN.md` §3.2 records it: "a second frontend is added beside
  Spoon, never behind it").
- `FrontendConfig` is Java-shaped: `sourceRoot`, `files`, `classpath`, `resolutionRoots`,
  `resolutionExcludes`, `preservedAnnotations`, `internTypes`. `PortConfig.DefaultInclude` is
  `**.java`.

So the first deliverable of any second frontend is the interface the first one never needed: a
`TirFrontend` that returns a `Program`, with the run-owned inputs (`Substitutions`, `CatalogLog`,
`AnnotationPolicy`, `internTypes`) passed in. Section 7, Phase 0.

### 1.2 What a frontend has to produce

A `Program` (`api/.../tir/Tir.scala`): `units: List[Tree.ClassDef]`, a `SymbolTable`, an
`XrefIndex` (derived by `Xref.build`, nothing for a frontend to do), a `MemberIndex` (every
executable the frontend walked, dropped ones included — required, not defaulted), optionally
`internedDefs` (classpath types interned for ancestry) and `internedDefaults` (external interface
default methods).

Symbols are interned by a string key (`frontend-spoon/.../SpoonTirMinter.scala`): a qualified name
for a type, `owner#member` for a member, `decl$$Name` for a type parameter; externals are minted
lazily as stubs with `owner = SymId.None` for a type and the owning type's id for a member.
Ownership is structural: a symbol is the program's iff climbing `owner` reaches a unit symbol
(`Program.owned`). Member identity is `Symbol.fullName` plus a `Descriptor` — the source-level
spelling of the parameter types, `int,String,Class[]` — that is what every policy key in a manifest
is written against (`tir/MemberKey.scala`).

Every term carries a resolved `TypeRepr`; every reference carries a `SymId`. The `TypeRepr` algebra
already has `OrType`, `AndType`, `ConstantType` (literal types), `Refinement`, `MethodType`,
`PolyType`, `TypeLambda`, `TypeBounds` — it is a Scala 3 type algebra, not a Java one. The `Tree`
set, by contrast, is Java's statement language plus Scala's expression language: `For`, `ForEach`,
`DoWhile`, `Labeled`, `Break`, `Continue`, `Synchronized`, `Assert`, `IncDec`, `Yield`,
`InstanceOf`, `ArrayAccess`, `NewArray`, `MethodRef`, `TypePattern`, `RecordPattern`.

Two nodes are the frontend's escape hatches and both are what a new frontend will lean on first:
`Tree.Unportable` (a per-site refusal with a closed taxonomy, `tir/Unportable.scala`, counted by
`MarkerCheck`) and `Tree.Opaque` (closed Scala text with numbered holes). A new frontend also owes a
**kind registry**: `SpoonKinds` lists every parser node kind and what the frontend does with it
(`Lowered`/`Positional`/`Absent(how)`), and a spec asserts the registry equals the parser jar's
kind set, so a parser upgrade cannot add a silently absorbed construct.

### 1.3 What is Java-specific in the engine, and how much of it

Surprisingly little is Java-specific by construction; most of it is Java-specific by naming.

| where | Java-specific fact | cost to generalise |
|---|---|---|
| `Origin.javaPath` | field name only; 202 uses in 63 files | none now (keep the name, document it); rename later in one mechanical commit |
| `PortConfig.DefaultInclude = "**.java"` | default source glob | per-frontend default |
| `FrontendConfig.classpath` | Java resolution input | add a frontend-specific `options: Map[String, String]`; TS/Dart ignore `classpath` |
| `catalog.DiffId` renders `JS-<area><nn>` | the difference catalog is Java-vs-Scala | add a language to `DiffId` (`TS-`, `DT-`); rows for TS/Dart are new areas of the same registry |
| `SpoonKinds` totality spec | per parser | each frontend ships its own registry and totality spec (TS: `SyntaxKind` names; Dart: AST node class names) |
| `Sam.Answer` on `AnonClass` | JLS 9.8 functional-interface question | TS/Dart have first-class functions; the frontend answers `No("not java")` and never mints `AnonClass` for a lambda |
| `Flags.isPackagePrivate`, `Flags.isRecord`, `Symbol.permits`, `Symbol.components` | JLS-specific | unused by the new frontends; harmless |
| `TirEmitter` statics-to-companion, `<clinit>` triggers, `CtorFunnel` | JLS 12.4/8.8 | TS `static` and Dart `static` have the same companion shape; TS has one constructor so the funnel is a no-op; Dart's named constructors are LOWERED before the funnel sees them (§5) |
| `PortabilityCheck`, `JS-L`/`JS-P` API rows | the JDK surface | TS: `lib.es*.d.ts` surface needs its own rows (`TS-L`); Dart: `dart:core` (`DT-L`) |
| the `Descriptor` grammar (`Param.Prim("int")`) | Java's primitive spelling | TS spells `number`/`string`/`boolean`; Dart `int`/`double`/`bool`; the grammar is "the source spelling", so each frontend spells its own and a manifest key is written in that language's spelling — no change |

The emitter is language-neutral over the TIR with three gaps a TS/Dart program hits immediately
(a parameter default that is a real `Term`, an object-literal instance of a trait, and named
arguments) — listed in §2 under "proposed change".

---

## 2. The TIR audit

Legend. **In TIR**: the construct has a node or a type today. **Java-specific**: the node's shape or
semantics is Java's, not the target language's. **Needed for TS / Dart**: what the corpus uses (§3
has the counts). **Proposed change**: `none` / `lowering` (the frontend maps it onto existing nodes,
counted where the mapping is lossy) / `node` (a new `Tree` case) / `type` (a new `TypeRepr` case) /
`flag` / `emitter` / `phase` (a reusable §1(b) transform with a per-library policy).

| Concern | Already in TIR? | Java-specific? | Needed for TS? | Needed for Dart? | Proposed change |
|---|---|---|---|---|---|
| Symbols, interning | yes — `Symbol`, `SymId`, key grammar `pkg.T`, `T#m`, `decl$$X` | key grammar assumes packages | yes | yes | none. TS: a module file's top-level declarations are owned by a minted module `object` (see "modules"); a class keeps the package the port's `modulePackages` map assigns to its directory. Dart: `library` = unit; `part` files fold into it |
| Ownership (`Program.owned`) | yes, structural via `owner` | no | yes | yes | none |
| Externals (lib / node_modules / SDK) | yes — lazy stubs, `owner = None` for types | no | yes (`lib.es2020.d.ts`, `lib.dom.d.ts`, dependency `.d.ts`) | yes (`dart:core`, `package:` deps) | none; each frontend interns from its resolver. `Flags.isResolved` = the checker resolved a declaration |
| Classes | `ClassDef` + `Flags` | `isPackagePrivate`, `isRecord` unused | yes | yes | none. TS `abstract`, `static`, `private/protected/#x` map to flags; TS parameter properties → `isParamAccessor` |
| Interfaces / traits | `ClassDef(isTrait)` | no | yes (`interface` is structural; Scala trait is nominal) | yes (`abstract class`, `implements` of a class's implicit interface) | lowering + catalog rows. TS: an `interface` becomes a `trait`; an object literal typed as that interface becomes `new Trait { … }` (emitter gap, below); `implements` of a concrete Dart CLASS has no Scala image → `Unportable` kind `ImplicitInterface`, counted |
| Structural / object-literal types `{ a: number; b?: string }` | `TypeRepr.Refinement` exists; emitter renders only the parent | n/a | yes (rough.js `Options`, every helper's point/segment records) | rare (records `({int a})`) | **lowering**: the frontend MINTS a nominal trait per distinct anonymous object type, named deterministically (declared alias name where one exists, else `<Owner>$Shape<n>` by first-use order), recorded as a `Decision` (`StructuralTypeNamed`); a type used once at a call boundary and never named is the same mint. Never emit Scala structural refinements (`Selectable` reflection is not available on JS/Native) |
| Methods / functions | `DefDef` | no | yes | yes | none for methods. Top-level `function`/`const` → members of the module object. Function *declarations* are hoisted in JS — order in the module object does not matter in Scala either. Generators (`function*`) and `async` → `Unportable` (`Generator`, `Async`), counted |
| Constructors | `DefDef("<init>")` + `CtorFunnel` | funnel is JLS-shaped | yes (one ctor; overload signatures are declarations only) | yes (named ctors, factories, redirecting, initializer lists, `this.x` params) | **lowering** (Dart): a named or factory constructor becomes a companion `def name(...)`; the primary stays the unnamed generative one; initializer lists become field initialisers in the body head; `this.x` formals → `isParamAccessor`. The funnel then sees a Java-like single-primary graph. Call sites `Foo.name(x)` → `Apply(Select(Foo$, name))` |
| Fields / properties | `ValDef`, `isMutable`, `isLazy` | no | yes (`readonly`, `get`/`set` accessors, optional `x?: T`) | yes (`final`, `const`, `late`, getters/setters) | `get x()` → parenless `DefDef` (`paramss = Nil`); `set x(v)` → `DefDef("x_=")`; both already renderable (the bean-property phase emits this shape). Dart `late final x = e` → `isLazy`; `late var x;` → `var x: T = null.asInstanceOf[T]` **counted** (`LateWithoutInitialiser`); `const` → `final val` where the rhs is a literal, else `val` |
| Local variables | `ValDef` | no | yes (`var` hoisting, destructuring, `const` in loops) | yes (destructuring) | **lowering**: JS `var` is function-scoped — hoist the declaration to the enclosing function's block head, keep the assignment in place, counted (`VarHoisted`); destructuring → temp + selections. `let` in a `for` closure-captures per iteration in JS, Scala's `for`/`while` translation does too when the body is a lambda-free block; a closure capturing a loop `let` is counted (`LoopLetCapture`) |
| Generic parameters | `TypeDef` with `TypeBounds` | no | yes (`T extends U = D` — defaults) | yes (`<T extends num>`) | **lowering**: a default type argument has no Scala image; the checker already applies it at every use, so the frontend reads instantiated types and drops the default on the declaration; counted per declaration (`TypeParamDefaultDropped`) |
| Generic applications | `AppliedType`, `TypeApply` | no | yes | yes | none. Where a generic CALL's arguments were inferred, emit no `TypeApply` and let scalac infer; count the site where the TS-inferred argument is a union or literal type (scalac may widen) — `InferredTypeArgRisk` |
| Unions | `OrType`, emitter renders `A \| B` | no | yes — the central TS feature | yes (`T?` only) | **type** stays; **narrowing** is new: the exporter records the flow-narrowed type at every identifier occurrence; where it differs from the declared type the frontend inserts `Typed(expr, narrowed)`/`asInstanceOf` at the use, counted (`NarrowingMaterialised`). A discriminated union of object types (`kind: "a" \| "b"`) is a candidate for a §1(b) idiom phase `DiscriminatedUnionTransform` → sealed trait + case classes + `match`; not required for correctness |
| Intersections | `AndType` | no | rare (`A & B` on options) | no | none |
| Literal types `"a" \| "b"`, `1 \| 2` | `ConstantType(Constant)` | no | yes | no | none; Scala 3 has literal types. An `enum`-like literal union is left as is; an idiom phase may later lift it |
| Nullability | `OrType(T, Null)` floor + `NullabilityTransform` (`Union`/`Named`/`Option` targets) | the annotations half is | yes (`T \| null \| undefined`, optional `?`) | yes (`T?` — exact, sound) | **lowering**: `null` and `undefined` both → `scala.Null` (one runtime value); a site that distinguishes them (`=== undefined` vs `=== null`, `void 0`) is counted (`UndefinedDistinguished`). `?.` → `if (t == null) null else t.m` with a temp; `??`/`??=` → `if`. The existing phase lifts to SSG's `Nullable[T]` (target `Named`) — no new mechanism. Dart's `!` → `.nn` |
| `any` / `unknown` / `dynamic` | `Any` type exists; no dynamic member access | n/a | katex/mermaid yes; rough.js almost none | dart-sass: some | `unknown` → `Any` (exact: only tests/casts allowed). A MEMBER ACCESS or CALL on `any`/`dynamic` → `Unportable(DynamicMember)`, counted per site; a value of type `any` flowing into a typed slot → `asInstanceOf`, counted (`AnyCoerced`). No `scala.Dynamic` shim: reflection is not portable |
| Overloads | full JLS machinery, `OverloadDivergence` | yes | TS: overload SIGNATURES + one body | Dart: none | **lowering** (TS): emit only the implementation signature (its union-typed parameters ARE the runtime contract); drop the overload declarations, record one `Decision` per dropped signature. The Java three-phase-resolution risk lane does not apply |
| Named / default parameters | `MethodType.params` carry names; `ValDef.rhs` on a param renders only when `Opaque` | Java has neither | defaults yes, named no | both, heavily (`{required int a, int b = 0}`) | **node** `Tree.NamedArg(name, value)` (Quotes has it) + `Xref`/`StandardTraversal` arms; **emitter**: render a param's `rhs` as `= <term>` (today only `Opaque`), render `NamedArg` as `name = value`. A TS default that reads an earlier parameter is legal in Scala too. Optional `x?: T` with no default → default `null` under the union floor |
| Rest / varargs | `isVararg`, `Repeated`, `Spread` | array-vs-vararg rules | yes (`...args`, spread calls) | no | none; `f(...arr)` → `Spread` |
| Function types | `AppliedType(FunctionN, …)` | no | yes, incl. optional params `(a, b?) => c` | yes, incl. named-param function types | **lowering**: optional parameters in a function TYPE → `FunctionN` over `B \| Null`, call sites that omit the argument get `null` inserted, counted (`OptionalParamInFunctionType`). A callable-with-properties interface, and a Dart function type with named parameters → `Unportable(CallableShape)`, counted |
| Closures | `Lambda` (with `resultTpt`) | `return` inside lambda is Java's | yes | yes | none for arrows and Dart closures. A JS `function` expression that uses `this` has dynamic `this` → `Unportable(DynamicThis)`, counted; one that does not is a plain lambda |
| `this` | `This` | no | yes | yes | none |
| Modules / namespaces | package = unit's dotted path; `Flags.isModule` for `object` | packages | yes (ES modules, `export default`, `namespace`) | yes (`library`, `part`, `import … as p show/hide`) | **lowering**: one TS module → one Scala unit: `object <fileStem>` owning top-level values/functions/aliases, plus each exported class/interface as its own top-level type in the port-assigned package. `export default X` → member `default` renamed to the stem. `namespace N` → nested object. Import resolution is entirely the exporter's; fully-qualified emission (`CLAUDE.md` §6) means no `import` is ever generated |
| Extension methods | none | n/a | no | yes (`extension E on T`) | **lowering**: `E` becomes `object E` with methods taking the receiver first; call sites are emitted QUALIFIED (`E.m(recv, …)`), which is always valid under FQN emission (a Scala `extension` needs to be in scope, and nothing is imported). A later beautification backend may restore `recv.m(…)`. No new node |
| Mixins | `ClassDef.selfType`, parents list | no | no (TS mixin functions are runtime class factories → `Unportable(RuntimeMixin)`) | yes (`mixin M on B`, `with`) | none: `mixin M on B` → `trait M { self: B => }`; Dart's `with M1, M2` linearises exactly as Scala's `with M1 with M2` (last is outermost, `super` chains inward). A CLASS used as a mixin (pre-Dart-3 form) is counted (`ClassAsMixin`) |
| Enums | `EnumCase(ctorArgs, body)` — Java's model | yes (constants with bodies) | yes (numeric/string enums, `const enum`) | yes (enhanced enums) | none for Dart (same shape as Java). TS: `enum E { A = 1 }` → `enum E(val value: Int)`; string enums the same over `String`; `const enum` is inlined by the checker — the frontend reads the constant value. REVERSE mapping `E[1]` → `Unportable(EnumReverseMap)`, counted |
| Pattern matching | `Match`/`CaseDef`, `TypePattern`, `RecordPattern`, `BindPattern`, `Labeled` | switch-shaped | `switch` on literals; `switch (true)` | Dart 3: switch expressions, `if-case`, object/list/record patterns, `||` patterns, guards `when`, relational patterns | TS: none (`switch(true)` → if-chain lowering, counted). Dart: **node** `Tree.AltPattern(List[Term])` for `\|`, **node** `Tree.SeqPattern(elems, rest)` for list patterns; an object pattern `Foo(:var x, y: p)` lowers to `TypePattern` + guard + bindings (no `unapply` minted); map patterns → `Unportable`. Exhaustiveness: a Dart switch statement over a sealed type is exhaustive; keep the fall-out arm rule from `CLAUDE.md` §4.4 and read it off the selector's type |
| Dynamic calls, `typeof`, `in`, `instanceof` | `InstanceOf` | no | yes | `is`/`as` yes | `instanceof`/`is` → `InstanceOf`; `as` → `Typed`. `typeof x === "string"` → `isInstanceOf[String]`; `"number"` → `Double` (or `Int` after §4.5); `"boolean"`; `"function"` → `isInstanceOf[Function?]` refuses (arity unknown) → counted; `"object"` (includes `null`) → `x != null && !prim` lowering; `"undefined"` → `== null`. `k in obj` → `Unportable(InOperator)` unless `obj` has an index signature (then a Map `contains`) |
| Numbers | `Int`/`Long`/`Double` … | Java's eight primitives | `number` only (IEEE double; `\|0`, indexes, lengths are integers by USE) | `int`/`double`/`num` (exact) | **phase** `NumberSplitTransform(seeds, scope)` — a §1(b) mechanism on the existing `FlowPropagation` substrate (the one `PrimitiveToOpaqueTransform` uses): seed `Int` at every array index, `.length`, `charCodeAt` argument, bitwise-operator operand, `for` counter, and every slot the REFERENCE port spells `Int` (`ParityRef` derivation, exactly as `OpaqueSpec.derive` does); propagate along pure-move flows; retype; coerce `.toInt`/`.toDouble` at seams, counted. Default policy `Only(Set.empty)` = everything stays `Double`, which is CORRECT and unidiomatic. Dart needs none of this |
| Strings, templates | `StringC`; no interpolation node | n/a | template literals | interpolation `'$a ${b}'`, multi-line | **lowering** to `+` concatenation (exact: both languages call `toString`; Scala's `+` on `String` calls `String.valueOf`). Tagged templates → `Unportable(TaggedTemplate)` |
| Equality | `==` → `eq`/`equals` rules | yes (JLS 15.21) | `===`/`!==` (identity for objects, value for primitives), `==` (coercing) | `==` calls `operator ==` (value), `identical()` | TS: `===` on reference types → `eq`; on primitives/literals → `==`; JS loose `==` with two operands of the same primitive type → `==`; otherwise `Unportable(LooseEquality)`, counted. Dart: `==` → `==` (Scala's calls `equals`, matching Dart's `operator ==`), `identical` → `eq` |
| Truthiness | none | n/a | yes (`if (x)`, `x && y`, `!x` on non-booleans) | no (Dart conditions are `bool`) | **lowering**: a condition whose static type is `Boolean` stays; `T \| Null` with `T` a reference type → `x != null`; a `number`/`string`/mixed operand → `balticporter.runtime.js.Truthy(x)` (a universal JS-semantics helper in `runtime/`, like the Java collection shims: a language fact, not a library's). `a \|\| b` in VALUE position (defaulting idiom) → `if (Truthy(a)) a else b` with a temp |
| Arrays, `Map`, `Set`, object-as-dictionary | `CollectionsTransform(families, retarget, rewrites…)` | the JDK family table is a constant | yes | yes (`List`, `Map`, `Set`, `Iterable`) | **policy, not mechanism**: a JS/Dart standard-library FAMILY TABLE for the existing collections phase (`Array<T>` → `ArrayBuffer[T]` with `push`/`splice`/`slice`/`indexOf` rewrites; `Map` → `mutable.LinkedHashMap` (insertion order is observable in JS); index-signature objects → `mutable.Map[String, T]` with `obj[k]` → `apply`/`update`). The table is a §1(a) constant of the LANGUAGE, shipped with the frontend, exactly as the JDK table is |
| Regex | JDK `Pattern` rows | JDK | yes | yes | vocabulary rows (`TS-L`, `DT-L`) onto `java.util.regex` with the known divergences counted (`/g` + `replace`, sticky `y`, `\d` Unicode class, lookbehind on Native) — SSG's `docs/contributing/cross-platform-regex.md` is the reference list |
| Imports / module resolution | frontend-resolved; FQN emission | Java classpath | the exporter's (tsconfig `paths`, `node_modules`) | the analyzer's (`package_config.json`) | none in the IR. External DEPENDENCIES take the same five dispositions as Java's (`DESIGN.md` §3.7): `PortInRepo` (points-on-curve for roughjs), `MapTo` (vocabulary), `Shim`, `PlatformProvided` (`lib.es*`), `Drop` (`lib.dom`) |
| Source positions | `Origin(javaPath, line, col)` | field name | yes | yes | none now; the exporter emits line/col from the checker's `getLineAndCharacterOfPosition`; `Origin.javaPath` carries the `.ts`/`.dart` path |
| Comments | `Trivia(kind, text)` sliced verbatim; `TriviaCheck` text-to-text | JSDoc = `Javadoc` kind | yes (`//`, `/* */`, `/** */`) | yes (`//`, `///` doc, `/* */`) | none: the exporter emits comment RANGES (`getLeadingCommentRanges`, Dart `Token.precedingComments`); the Scala side slices the source buffer itself (the rule that a comment is never re-printed holds). `///` is a `Line` comment; `TriviaCheck` is language-neutral |
| Annotations / decorators | `Annot(tpe, args)` | Java annotations | decorators: not in the corpus | `@override`, `@protected`, `@visibleForTesting`, `@internal` | Dart metadata → `Annot`; `@override` → `isOverride`; `@protected` → `isProtected`; `@visibleForTesting` → a `SymTag` the visibility phase reads. TS decorators → `Unportable(Decorator)` (none used) |
| Labels, jumps | `Labeled`, `Break`, `Continue` (+ label) | no | yes (`outer: for`) | yes | none |
| `try`/`catch`/`finally` | `Try`, `CatchCase` with typed param | typed multi-catch | untyped `catch (e)` | `on E catch (e, st)` | TS: `catch (e)` → `case e: Throwable`; Dart `on E catch` → typed; `rethrow` → `throw e`; the `boundary.Break` re-throw arm rule from `CLAUDE.md` §4.4 applies unchanged |
| `for … in`, `for … of`, `for (;;)` | `ForEach`, `For` | no | yes | yes (`for-in`, C-style) | `for (k in obj)` → keys of the Map lowering; `for (x of arr)` → `ForEach` |
| Cascades (`a..b()..c()`) | none | n/a | no | yes | **lowering**: temp + statements, no node |
| Operators overloading | method call | n/a | no | yes (`operator +`, `[]`, `==`) | none: `operator +` → `def +`; `operator []` → `def apply`, `[]=` → `update` |
| Records / tuples | none | n/a | tuples `[number, number]` | records `(int, String)`, named records | `AppliedType(TupleN)`; element access `t[0]` → `_1`; named records → minted case class (same mint as structural types), counted |
| Getters on external types (`.length`) | `externalParenless` set on the emitter | JVM class-file facts | `arr.length`, `str.length` | `list.length` | vocabulary rows; the `externalParenless` mechanism already exists |

Counting the "proposed change" column: new nodes: `NamedArg`, `AltPattern`, `SeqPattern` (3);
new `Unportable` kinds: ~14 (each a closed enum case with remedies, `tir/Unportable.scala`); emitter:
param defaults as terms, `NamedArg`, `new Trait { … }` for object literals (3); one new §1(b)
phase (`NumberSplitTransform`) and one optional idiom phase (`DiscriminatedUnionTransform`); two
frontend language tables (collections families, regex/string vocabulary); one `runtime/` helper
object (`js.Truthy`). No change to `Symbol`, `TypeRepr`, `Program`, `Phase`, `StandardTraversal`
beyond the three node arms.

---

## 3. The SSG corpus

Everything below was measured on the submodules under `/Users/dev/Workspaces/kubuszok/ssg/original-src/`
and the hand ports under `ssg-*/src/main/scala/`. LOC are physical lines (`wc -l`) of the source
tree with tests, `dist`, `node_modules` and docs excluded. Percentages in the last table are
estimates of upstream LOC, each with its reason.

### 3.1 Summary

| upstream | language | pinned | source files / LOC | typing | hand port files / LOC | port layout vs upstream |
|---|---|---|---|---|---|---|
| roughjs | TS | 4.6.6 (`56a2762`, untagged, 174 past v3.1.0) | 17 / 1,572 | `strict`, 0 `any` | 24 / 3,900 (with the four helpers) | 1:1 by file; `canvas.ts` dropped |
| path-data-parser | TS | 0.1.0 (`93d3fa8`) | 4 / 466 | strict, 0 `any` | in the above | 1:1 |
| points-on-curve | TS | 0.2.0 (`4824147`) | 2 / 177 | strict, 0 `any` | in the above | 1:1 |
| points-on-path | TS | 0.2.1 (`7693ef0`) | 1 / 69 | strict, 0 `any` | in the above | 1:1 |
| hachure-fill | TS | 0.5.2 (`80e47ba`) | 1 / 173 | strict, 0 `any` | in the above | 1:1 |
| KaTeX | **TS** (not Flow) | 0.16.45 (`90de979` = tag) | 94 / 22,877 (`src/` 19,811 + `katex.ts` 250 + `contrib/` 2,816) | `strict`; 7 `: any`, 13 `as any`, 2 `@ts-ignore`, 7 `unknown`; 3 legacy `.js` data files under `allowJs` | 94 / 23,246 | 1:1 by directory (`functions/` 46→47 files) |
| dart-sass | Dart | 1.99.0, SDK `>=3.6.0` | 368 / 61,804 (`lib/src`) | null-safe; 39 `dynamic` | 132 / 47,591 | by directory; 36% of file names match (AST leaf files consolidated ~4:1) |
| Mermaid | TS + JS | 11.0.0 (`2cfdd162`) | core 193 `.ts` / 22,107 + 76 `.js` / 21,326 + 16 `.jison` / 3,749 = 47,182 | `strict` nominally; 133 `any`, 109 `@ts-ignore`/`-nocheck`/`-expect-error` | 201 / 35,232 | 20 of the port's 30 diagram directories trace to the pinned upstream |
| Terser | JS | 5.46.1 | 26 / 26,264 (`lib/`) + `tools/domprops.js` 9,195 (data) | untyped; 134 `DEFNODE`, 100 `DEFMETHOD` | 52 / 42,450 (`DomProps.scala` alone 9,277) | by file; ratio 1.26× once the data table is excluded |
| jekyll-minifier | Ruby | 0.2.2 | 1 / 1,190 (glue) | untyped | `ssg-minify`: 16 / 3,555 | the algorithms live in gems: `cssminify2` (vendored, 1,804 LOC Ruby), `htmlcompressor` and `json-minify` (NOT vendored) |

Two documentation discrepancies were found and must be resolved by measurement before they are
used as gates: `ssg-sass`'s `README`/`docs/architecture/sass-port.md` claim 13,865/13,902 sass-spec
cases passing, while its own `SHORTCUTS.md`/`SASS_SPEC_REPORT.md` (dated 2026-04-07) report 3,768 of
11,797 single-file cases; and `ssg-mermaid`'s "31 diagram types" include four that Mermaid added
after 11.0.0 and six that are SSG's own inventions (headers say so), i.e. a third of that module
was never a translation of anything in `original-src/`.

### 3.2 The rough.js family (the prototype target)

- Pure ESM, no import cycles, `lib: ["es2017", "dom"]` in roughjs only. DOM API use is confined to
  `canvas.ts` (153 LOC), `svg.ts` (134), `rough.ts` (22) — exactly the three files the port drops
  or substitutes. The other 14 roughjs files and all four helpers are arithmetic over arrays.
- Shapes: 13 `interface`, 2 `type`, 10 `class`, 31 top-level functions, 8 arrows, **0 generics**,
  8 union sites (string-literal unions `OpType`/`OpSetType`, one DOM union, one structural
  `Polygon | Polygon[]`), 69 optional fields — 27 of them the all-optional `Options` interface, with
  `ResolvedOptions extends Options` promoting 20 to required.
- Metaprogramming: `Object.assign` ×5 (all the options-merge idiom), spread ×23, nothing else.
- Substitutions the hand port made, and what the engine will do instead:

| upstream | hand port | engine (§4.4) | parity verdict |
|---|---|---|---|
| `document.createElementNS` / `setAttribute` chains in `svg.ts` | `SvgElement.g().withAttr(…)` string builder (`ssg.graphs.commons.svg`) | `svg.ts` dropped, `inject` the hand-written `RoughSVG.scala` | substitution, recorded |
| `interface Options { roughness?: number; … }` | `final case class Options(roughness: Option[Double] = None, …)` | `trait Options { def roughness: Double \| Null … }` then the nullability phase → `Nullable`/`Option` per manifest | API spelling diverges; classify (`case class` vs trait is an idiom-phase target, not a frontend decision) |
| `Object.assign({}, this.defaultOptions, options)` | `base.copy(roughness = o.roughness.getOrElse(base.roughness), …)` (one line per field) | spread lowering: per-property copy into a `new ResolvedOptions { … }` | same behaviour, different spelling |
| `type OpType = 'move' \| 'bcurveTo' \| 'lineTo'` | `enum OpType(val value: String)` | literal union kept (`"move" \| "bcurveTo" \| "lineTo"`) | idiom phase candidate (literal union → enum) |
| `Point = [number, number]`, `p[0]` | `final case class Point(x: Double, y: Double)`, `.x` (four unrelated `Point`s kept separate) | `Tuple2[Double, Double]`, `_1` | idiom phase candidate; the port's choice is a recorded decision |
| `typeof polygons[0][0] === 'number'` (union sniffing in hachure-fill) | `Polygon \| Vector[Polygon]` with a head-element test; the port DROPS the truthiness sub-condition (a first vertex of `0`/`-0`/`NaN` is misclassified upstream) | `Truthy(...)` lowering reproduces java-rule behaviour: upstream's bug is reproduced, counted as `TruthinessRuntime` | the port's deviation is a `divergence-verdicts.tsv` row; the engine's default contract is upstream behaviour |
| in-place `p[0] = …` on tuples aliased across lists | mutable case class fields | `ArrayBuffer`/tuple retarget keeps aliasing | none |

Convention split worth recording: the rough port uses `Option` 80× and `Nullable` 8×; the KaTeX
port uses `Nullable` 338× and `Option` 2×. Both are reachable from the same frontend output through
the nullability phase's `target` (`Named` vs `OptionTarget`) — it is a per-port manifest line, and
the two hand ports chose differently.

### 3.3 KaTeX

- TypeScript at the pinned tag (the Flow era ended long before 0.16.45). `tsconfig`: `strict`,
  `verbatimModuleSyntax`, target ES2023, `lib: ["es2023", "dom"]`; `allowJs` for three data files
  (`fontMetricsData.js` 2,077 LOC — one object literal; `unicodeAccents.js`, `unicodeSymbols.js`).
- LOC: `src/` top-level 13,136 (largest: `Parser.ts` 1,056, `macros.ts` 1,032, `symbols.ts` 894,
  `delimiter.ts` 828, `buildCommon.ts` 775, `domTree.ts` 636), `src/functions/` 5,234 across 46
  files, `src/environments/` 1,441 (`array.ts` is 1,130 of it), `contrib/` 2,816 (auto-render,
  copy-tex, render-a11y-string — DOM-facing, drop).
- Shapes: 66 `type` aliases, 11 `interface`, 21 `class`, 40 top-level functions, 174 arrows, 26
  `export default`, 67 generic sites, 171 union sites, 209 optional fields. The central type,
  `AnyParseNode`, is a MAPPED type `ParseNodeTypes[NodeType]` that the checker resolves to a ~70-
  member discriminated union keyed on `type: "…"` — the `DiscriminatedUnionTransform` case, and the
  hand port's `sealed trait AnyParseNode` + 70 `final case class ParseNodeXxx(var mode, var loc, …)`
  (mutable fields, because macro expansion rewrites nodes in place).
- Metaprogramming: `Object.keys` ×15, spread ×143, `instanceof` ×39, `arguments` ×32 (legacy
  varargs → `Unportable(ArgumentsObject)`, counted), 183 regex literals + 3 `new RegExp`, 0
  `eval`/`Proxy`/`Symbol()`.
- DOM: 18 `document.`/`window.` sites, all in `katex.ts` (the render-to-DOM entry points);
  `domTree.ts`/`mathMLTree.ts` build a DOM-independent value tree already — the port keeps that.
- Import cycles: `Parser ⇄ defineFunction`, `Parser ⇄ defineEnvironment`, `parseNode ⇄
  environments/array` — harmless for a whole-program frontend.
- Hand port: `Nullable` everywhere (338), `mutable.Map[String, V]` for every `Record<string, V>`
  (94 sites — object-as-hashtable, correctly told apart from fixed-shape records), 109 case classes,
  9 sealed roots, 7 enums; `FontWeight = "textbf" | "textmd" | ""` kept as `type FontWeight =
  String` because `""` flows into string concatenation. `Options.extend(extension: Partial<OptionsData>)`
  became a method with 11 defaulted parameters rather than `copy`, because `Options` is a class with
  a cache field — the same `Object.assign` idiom rendered two different ways by the same porter,
  which is exactly the kind of decision a frontend cannot take without a policy line.
- Tests: KaTeX's suite is Jest (`test/katex-spec.js`, plus the screenshotter, which is out). A Jest
  policy for the test-framework phase (`describe`/`it`/`expect(x).toBe(y)` → MUnit) is a Phase 2
  deliverable; it is a (b) table, the JUnit one is the precedent.

### 3.4 dart-sass

- `lib/src`: 368 files, 61,804 LOC. By directory: `visitor/` 13,849 (26 files — evaluator,
  serializer), `parse/` 7,390 (9), `ast/` 7,275 (128 small files), `value/` 7,059 (39),
  `js/` 4,679 (52, `@JS()` externs — out), `functions/` 3,368 (7), `extend/` 2,475 (6),
  `embedded/` 2,026 (16, protobuf host protocol — out), `importer/` 1,652 (16), `executable/`
  1,469 (7, CLI — out), `util/` 1,507 (20), `io/` 591, `logger/` 424, `callable/` 393, `module/`
  382. In scope after the three drops: **≈53,600 LOC, 293 files**. No `part` files, no `.g.dart`.
- Feature census (in `lib/src`): `with` ×612 and 68 `mixin` declarations, 71 `extension`, 71
  `factory`, 116 `operator`, 56 `late`, 39 `dynamic`, 13 `{required`, 16 `typedef`, 3 `sealed`,
  7 records, `switch (` ×351, `is` ×2,456 / `as` ×876, 131 `@protected`, 0 `covariant`; `async`
  ×225 and `Future<` ×164 — concentrated in the `async_*` twins of the evaluator/callable/importer
  files (see §5.3: the port takes the generated sync twins).
- Runtime deps: `args`, `async`, `charcode`, `collection`, `meta`, `path`, `pool`, `pub_semver`,
  `source_maps`, `protobuf` (embedded only), `js`/`node_interop` (js only), `cli_pkg`/`cli_repl`/
  `http` (CLI only). `collection`/`charcode`/`path`/`source_maps` need vocabulary rows or shims.
- Hand port: 132 files / 47,591 LOC, 125 with a "Ported from:" header, 130 marked full-port;
  drops `js/`, `embedded/`, `executable/`, `callable/async.dart`, `importer/js_to_dart/`. Recorded
  idioms: `extension … on Iterable<E>` → Scala `extension [E](it: Iterable[E])`; `mixin X on Y` →
  `trait X extends Y` (the engine will emit `self: Y =>`, which is the faithful form — a parity
  divergence to classify); `{required bool js}` → `js: Boolean = false` (a default the upstream
  did not have — `unjustified` unless a verdict says otherwise); `const factory Logger.stderr(…) =
  StderrLogger` → companion `val` + class.
- sass-spec: 3,015 `.hrx` archives; the port's runner unpacks 11,797 single-file cases and skips
  multi-file (`@use`/`@forward`) archives. HRX is a `<===` delimited multi-file text format; the
  runner already has the unpacker, and the engine's external-spec lane (§5.3) reuses it.

### 3.5 Mermaid, Terser, jekyll-minifier (measured, not targeted)

- **Mermaid** core: `src/diagrams/` is 29,848 LOC / 164 files; of it renderer + `svgDraw` + styles
  13,603 LOC (46%), db + parser + `.jison` 9,940 (33%), types/detectors/glue ~6,300. d3
  `.select`/`.selectAll` ×63, `document.` ×36, `window.` ×45, 31 files touch `dagre`; runtime deps
  `d3`, `d3-sankey`, `dagre-d3-es`, `cytoscape`, `dompurify`, `khroma`, `stylis`, `roughjs`,
  `katex`, `marked`, `dayjs`, `lodash-es`, `uuid`, `@mermaid-js/parser` (langium). The hand port
  replaced d3 with an SVG string builder and reimplemented a Sugiyama layout in
  `ssg-graphs-commons`; `khroma`/`stylis`/`dompurify`/`cytoscape` have no counterpart.
- **Terser**: `compress/index.js` 4,128, `parse.js` 3,629, `ast.js` 3,475, `output.js` 2,537,
  `mozilla-ast.js` 2,098, `tighten-body.js` 1,531, `inference.js` 1,131, `scope.js` 1,068. The class
  system is `DEFNODE` (`Object.create(base.prototype)`, `SUBCLASSES` push, `ctor.DEFMETHOD`), 134
  definitions and 100 retroactive `DEFMETHOD` injections, 179 `.prototype` uses, 0 `eval`. The hand
  port rendered each as `class AstIf extends AstNode with AstStatementWithBody { var condition:
  AstNode | Null = null … }` with `walkChildren`/`transformDescend` overrides in place of the
  visitor callbacks threaded through prototypes.
- **jekyll-minifier**: 1,190 LOC of hook registration, option parsing and dispatch to gems;
  `define_method` ×4, `send` ×6, no `method_missing`. `cssminify2` (vendored) is a Ruby port of the
  YUI CSS compressor; `htmlcompressor` and `json-minify` are absent from the repo, and
  `HtmlMinifier.scala`/`JsonMinifier.scala` were written from behaviour. `JsMinifier.scala` delegates
  to `ssg-js`.

### 3.6 Estimates

Percent of upstream LOC. "Translatable" = statement-for-statement given the resolved AST plus the
§4.4 lowerings; "substitution" = a deterministic runtime shim or vocabulary row (`Math`, strings,
regex, collections, JS number coercion); "out of scope" = what the port drops by manifest;
"problematic" = no faithful mechanical form (dynamic typing, runtime class construction, d3).

| upstream | translatable | substitution | out of scope | problematic | why |
|---|---|---|---|---|---|
| rough.js family (2,457) | ~70% | ~15% | ~10% (`canvas.ts` + DOM slivers of `svg.ts`/`rough.ts`) | ~5% | strict, generic-free arithmetic; the hard spots are `Options` merging, number-to-string coercion (`o.strokeWidth + ''`), `%`/`Math.round` sign semantics, and the one union-sniffing site — each a listed lowering |
| KaTeX (22,877) | ~55% | ~20% | ~2% (`katex.ts` DOM sites, `cli.js`, `contrib/auto-render`) | ~23% | the `AnyParseNode` mapped union and its in-place mutation (an idiom-phase target with a mutable-field policy), 13 `as any`, `Partial<T>` extend patterns decided per call site, 32 `arguments`, 183 regexes |
| dart-sass (61,804; 53,600 in scope) | ~60% of in-scope | ~20% | ~13% (`js/`, `embedded/`, `executable/`) | ~7% | best-typed input in the corpus; the residue is `implements` of concrete classes, `late` without initialiser, `dynamic` (39), named-parameter function types, and `dart:collection`/`package:collection` semantics |
| Mermaid core (47,182) | ~30% (dbs, types, utils) | ~10% | ~45% (renderers, d3, styles, jison/langium output) | ~15% (`any`/`@ts-ignore` density, runtime plugin registration) | half is rendering the port rewrote from scratch |
| Terser (26,264) | ~10% (`utils/`, `sourcemap.js`, data) | ~5% | ~2% (`cli.js`) | ~83% | every AST member is runtime-defined and untyped |
| jekyll-minifier (1,190 + cssminify2 1,804) | ~0% mechanically (untyped) | — | — | 100% | no static frontend exists for Ruby; the algorithms' Java originals do

---

## 4. The TypeScript frontend

### 4.1 Shape: an exporter in TypeScript, a reader in Scala

The engine is a Scala 3 program; the only complete TypeScript type checker is the one in the
`typescript` npm package. There is no JVM port of it, and re-implementing TS type resolution is the
mistake `DESIGN.md` §1.2 rules out for Java ("a real compiler frontend with full type attribution
is non-negotiable"). So the frontend is two halves:

```
balticporter/frontend-ts/
  exporter/                 # npm package, TypeScript, pinned `typescript` version
    package.json            # "typescript": "5.x.y" exact; no other runtime deps
    src/export.ts           # ts.createProgram → walk → RAST JSON
    src/kinds.ts            # emits the SyntaxKind name table for the totality spec
    test/                   # vitest: fixtures → expected JSON (checked in)
  src/main/scala/balticporter/frontend/ts/
    TsFrontend.scala        # TirFrontend: runs the exporter (or reads a pre-exported dir), builds Program
    Rast.scala              # the JSON model (jsoniter-scala codecs; already a build dependency)
    TsKinds.scala           # kind registry, mirrors SpoonKinds; NodeKindTotalitySpec against exporter's table
    TsMinter.scala          # symbol/type interning (the SpoonTirMinter pattern)
    TsLowerDecls.scala      # modules, classes, interfaces, enums, aliases, structural-type mint
    TsLowerStmts.scala      # blocks, loops, switch, try, var hoisting, destructuring
    TsLowerExprs.scala      # calls, narrowing, optional chaining, truthiness, template literals
    TsVocab.scala           # lib.es* family table for CollectionsTransform; TS-L rows
```

`frontend-ts` depends on `api` only, like `frontend-spoon`; `engine` gains a dependency on it, and
`PortRun` takes a `TirFrontend` (Phase 0). The exporter is invoked as a subprocess (`node
exporter/dist/export.js --project <tsconfig> --out <dir> [--strict]`) by `TsFrontend`, OR the run
is pointed at a directory of pre-exported JSON (`input.rast = "…"`) — the second form is what the
Scala test suite uses, so `sbt test` never needs `node`.

Determinism: the exporter sorts every map, assigns ids in a fixed walk order, and writes a
`producer` header (`typescript` version, exporter version, effective compiler options, SHA-256 of
every input file). The Scala side records the header in `counts.tsv` (the `jdk_guard` pattern: a
version change is a measurement input, never silently absorbed).

### 4.2 What the exporter reads off the checker

For each `SourceFile` in the program that lies under the port's source root (the rest are
resolution-only, exactly `resolutionRoots`):

| need | TS compiler API | notes |
|---|---|---|
| declarations, kinds | `ts.forEachChild`, `node.kind` → `ts.SyntaxKind[kind]` | the kind NAME is what `TsKinds` registers |
| symbol of a declaration or reference | `checker.getSymbolAtLocation(nameNode)`; aliases through `checker.getAliasedSymbol` | ids are assigned by the exporter from the symbol's FIRST declaration position (file, pos); an ambient symbol (from `lib.*.d.ts` / `node_modules`) gets a `lib` field and no unit |
| qualified name | `checker.getFullyQualifiedName(sym)` gives `"path/to/file".Name` for module-scoped symbols | the reader turns it into `<package>.<stem>$.<Name>` or `<package>.<Name>` per §2 "modules" |
| declared type of a symbol | `checker.getTypeOfSymbolAtLocation(sym, decl)`; type-level: `checker.getDeclaredTypeOfSymbol` | |
| type of every expression | `checker.getTypeAtLocation(expr)` — this is the FLOW type at that position | recorded as `type`; the declared type of the referenced symbol is recorded separately as `declType` so the reader can tell narrowing happened |
| resolved overload at a call / `new` | `checker.getResolvedSignature(call)` → `sig.getDeclaration()` | the descriptor of the chosen signature is what `Apply.method` points at; for an implementation-only method (§2 "overloads") the reader re-points at the implementation |
| type arguments of a resolved generic call | not public API: `(checker as any).getTypeArgumentsForResolvedSignature(sig)` exists as an internal in recent 5.x releases and is gated on the pinned version | when the internal is absent the exporter emits none and the reader counts `InferredTypeArgRisk` for the site — a version-gated fact, recorded in the header |
| contextual type of a lambda / object literal | `checker.getContextualType(expr)` | this is what names the trait an object literal instantiates |
| type structure | `type.flags` (`TypeFlags.Union/Intersection/StringLiteral/NumberLiteral/Object/TypeParameter/…`), `type.isUnion()`, `checker.getTypeArguments(ref)`, `checker.getPropertiesOfType`, `checker.getSignaturesOfType`, `checker.getIndexInfosOfType`, `checker.isArrayType`/`isTupleType` | every type is emitted ONCE into a `types` table keyed by an exporter-assigned id (a `Map<ts.Type, number>`); unions are flattened as the checker flattens them; an object type records its properties' symbol ids so the structural-type mint can hash it |
| enum constant values | `checker.getConstantValue(member)` | |
| comments | `ts.getLeadingCommentRanges(text, node.getFullStart())`, `getTrailingCommentRanges` | ranges only; the reader slices the buffer |
| positions | `sf.getLineAndCharacterOfPosition(pos)` | 1-based on the Scala side, like Spoon's |
| diagnostics | `program.getSemanticDiagnostics()` | non-empty is FATAL by default (the port compiles upstream with upstream's own tsconfig); the `--strict` override (below) makes the strict-mode errors a COUNTED lane instead |

**Strictness is an input.** With `strictNullChecks` off, `null`/`undefined` vanish from every type
and the union floor is unreadable. The exporter therefore runs with `strict: true` forced on
(`--strict`), and every diagnostic that this produces beyond upstream's own tsconfig is recorded per
declaration as a `strictness` finding — the TS analogue of the JDK guard: it tells the reader how
much of the nullability the emitted port asserts was checked by the upstream compiler and how much
the frontend forced.

### 4.3 The interchange format (RAST — resolved AST), version 1

One JSON document per exported program (gzip optional), schema-versioned:

```jsonc
{
  "schema": 1,
  "producer": { "typescript": "5.6.3", "exporter": "0.1.0", "node": "24.12.0",
                "options": { "strict": true, "target": "ES2020", "module": "ESNext" },
                "kinds": ["Unknown", "EndOfFileToken", …]          // full SyntaxKind name table
              },
  "files":   [ { "id": 0, "path": "src/core.ts", "sha256": "…", "unit": true },
               { "id": 1, "path": "node_modules/typescript/lib/lib.es5.d.ts", "sha256": "…", "unit": false } ],
  "symbols": [ { "id": 12, "name": "Options", "qualified": "\"src/core\".Options",
                 "kind": "Interface",                              // ts.SymbolFlags name(s), primary first
                 "flags": ["Export"], "owner": 3, "decl": { "file": 0, "pos": 1201, "end": 2210 },
                 "type": 77, "declType": 77 } ],
  "types":   [ { "id": 77, "kind": "Object", "symbol": 12, "props": [13, 14, 15], "calls": [], "index": [] },
               { "id": 78, "kind": "Union", "members": [2, 5] },
               { "id": 2,  "kind": "Primitive", "name": "number" },
               { "id": 5,  "kind": "Literal", "value": "solid", "base": "string" },
               { "id": 90, "kind": "Reference", "target": 40, "args": [2] },        // Array<number>
               { "id": 91, "kind": "Function", "sigs": [7] },
               { "id": 92, "kind": "TypeParameter", "symbol": 50, "constraint": 77, "default": null } ],
  "sigs":    [ { "id": 7, "decl": 60, "params": [ { "name": "x", "type": 2, "optional": false, "rest": false, "hasDefault": true } ],
                 "ret": 78, "typeParams": [50], "typeArgs": [] } ],
  "units":   [ { "file": 0, "body": [ /* nodes */ ] } ]
}
```

A node:

```jsonc
{ "k": "PropertyAccessExpression",           // SyntaxKind name — the registry key
  "pos": 340, "end": 352, "line": 12, "col": 8,
  "sym": 44,                                   // referenced/declared symbol, if any
  "type": 78, "declType": 78,                  // flow type here, and the symbol's declared type
  "sig": 7,                                    // for CallExpression / NewExpression: resolved signature
  "ctx": 77,                                   // contextual type, for ObjectLiteral / ArrowFunction
  "const": "solid",                            // getConstantValue, for enum members / const enums
  "lead": [[300, 338, "Block"]], "trail": [],  // comment ranges in the file buffer
  "c": { "expression": { … }, "name": { … } } // children by the TS property name, arrays as arrays
}
```

Why JSON and not protobuf/TASTy/a binary: the exporter has one consumer, the fixtures must be
reviewable in a diff, and a schema bump is a version number, not a codegen step. The reader uses
the jsoniter-scala codecs already on the build's classpath; a 3k-LOC TS library exports to roughly
6–10 MB of JSON, which is far below what the Java side already holds in memory for one Spoon model.

Why the node keeps the TS shape (`k` = `SyntaxKind`, children by TS property name) rather than
pre-lowering to TIR in the exporter: the LOWERING is where every difference-catalog decision is
taken (`CLAUDE.md` §4.4's table is the model), and those decisions must be in Scala where the
catalog, `Decision`, `CatalogLog`, `Unportable` and the specs live. The exporter is deliberately
dumb: it serialises what the checker knows and nothing it decided.

### 4.4 Lowering — the decisions that are TypeScript's, not Java's

Each row is a catalog row in a new area (`TS-E`, `TS-S`, `TS-C`, `TS-G`, `TS-L`), with the same
obligations the Java rows carry (attached at a dispatch, consulted or unreached, twin in
`ENGINE-LIMITS.md`). The ones that are not the plain arm-by-arm translation:

| construct | lowering | counted as |
|---|---|---|
| `var x` inside a block | declaration hoisted to the enclosing function's block head (`var x: T = null`), assignment stays | `VarHoisted` (decision) |
| `if (x)`, `!x`, `a && b`, `a \|\| b` with a non-`Boolean` operand | `Truthy(x)` from `runtime` (`js.Truthy`), unless the static type makes `x != null` exact | `TruthinessRuntime` when the helper is used |
| `a \|\| b` as a DEFAULTING value | `{ val t = a; if (Truthy(t)) t else b }` | same |
| `a?.b`, `a?.[i]`, `a?.()` | temp + `if (t == null) null else …`; the result type is `R \| Null` | `OptionalChain` |
| `a ?? b`, `a ??= b` | temp + `if (t != null) t else b` | none (exact) |
| `x === y` | `eq` on reference types, `==` on primitive/literal types | none |
| `x == y` | `==` when both operands' static types are the same primitive; else refuse | `LooseEquality` (`Unportable`) |
| `typeof x === "…"` | per §2 "dynamic calls" | `TypeofObject`, `TypeofFunction` |
| narrowed identifier (flow type ≠ declared type) | `Typed`/`asInstanceOf` at the use; when the narrowing is `!= null` under the union floor, the nullability phase handles it | `NarrowingMaterialised` |
| object literal with a contextual interface type | `new <Trait> { override val a = …; … }`; excess/missing properties are checker errors already | none; `StructuralTypeNamed` when the trait had to be minted |
| spread `{ …a, …b }`, `Object.assign(a, b)` with a NOMINAL contextual type | per-property copy into the minted trait's instance: `new T { val p = if (b has p) b.p else a.p … }` — requires every property statically known; else refuse | `SpreadRefused` |
| `arr[i]`, `obj[k]`, `str[i]`, `tuple[0]` | by RECEIVER TYPE: array → `apply`/`update` on the retargeted `ArrayBuffer`; index signature → `Map` ops; string → `charAt`; tuple literal index → `_n` | none |
| `for (const k in obj)` | keys of the index-signature Map; on a class instance → refuse | `ForInOnObject` |
| `switch (true) { case cond: }` | if-chain | `SwitchTrue` |
| template literal `` `a${b}` `` | `+` concatenation | none |
| `class extends Base` where `Base` is an expression (mixin factory) | refuse | `RuntimeMixin` |
| `function` expression using `this` | refuse | `DynamicThis` |
| `x as T` / `<T>x` | `Typed` (a checker-validated cast: `asInstanceOf` only when the target is not a supertype) | none |
| non-null assertion `x!` | `.nn` (the nullability phase's own spelling) | none |
| `export default function …` / `export =` | member named after the module stem | none |
| getters/setters | parenless `def x` / `def x_=(v)` | none |
| overload signatures | dropped; implementation kept | `OverloadSignatureDropped` (decision) |
| `number` | `Double`; `NumberSplitTransform` retypes to `Int` where seeded (§2) | the phase's own lanes |
| `Math.*`, `Number.*`, `String.prototype.*`, `Array.prototype.*`, `Map`, `Set`, `JSON`, `RegExp` | vocabulary rows onto `scala.math`, `java.lang.String`, the retargeted collections, `java.util.regex` | `TS-L` portability lanes, as for the JDK |
| anything from `lib.dom.d.ts` | `PlatformHostileApi` — the port drops the file or substitutes (rough.js: `canvas.ts`, `svg.ts`, `rough.ts`) | the existing kind |

### 4.5 What the emitter needs

1. A parameter `ValDef.rhs` that is a real `Term` renders as `= <expr>` (today only `Opaque` does).
2. `Tree.NamedArg` renders `name = expr`; `StandardTraversal.mapTerm`, `Xref.build`,
   `TirPrinter` gain the arm.
3. `Tree.New(tpt, anon = Some(AnonClass(body = vals…)))` over a TRAIT parent already renders as
   `new T { … }`; the object-literal lowering reuses it (members are `override val`s). Nothing new,
   but the arm gets the catalog citation for `TS-C` "object literal instantiates a nominal trait".

### 4.6 Prototype: the rough.js family

Target, in order: `path-data-parser` → `points-on-curve` → `points-on-path` → `hachure-fill` →
`roughjs/src` minus its DOM files (`rough.ts`, `canvas.ts`, `svg.ts`). Reference port:
`ssg-graphs-commons/src/main/scala/…/rough/**` (its module id names the port: `ported/ssg-graphs-commons`).

Deliverables and their gates (each is one lane, read as `iterate-lane` says):

| step | deliverable | gate |
|---|---|---|
| P1.1 | exporter over `path-data-parser` (1 file, ~300 LOC); fixture JSON checked in | `TsKinds` totality spec green against the exporter's kind table; determinism: two exports byte-equal |
| P1.2 | `TsFrontend` builds a `Program`; `TirPrinter.canonical` of it is a golden file | `members.tsv` lists every function; `MarkerCheck` 0 open markers |
| P1.3 | emit; compile in `ported/ssg-graphs-commons` (JVM row) | `errors.tsv` by member; `api-parity` against the hand port with `ParityRef(compare = true)`, every divergence classified |
| P1.4 | the hand port's tests for these four libraries run against the emitted code | tests decide; each failure is a catalog row or a bug, never a hand edit |
| P1.5 | `roughjs/src/{core,geometry,fillers/*,renderer,generator}.ts` | as above, plus `NumberSplitTransform` measured before/after (`Double` everywhere compiles; `Int` where the reference has it) |
| P1.6 | `Options` (the large optional-fields interface) through the structural-type mint and the nullability phase to `Nullable` | 0 `StructuralTypeNamed` on `Options` (it is declared), N on the helpers' point/segment shapes, each named in `decisions.tsv` |

The `.conf` for it (Phase 0's grammar):

```hocon
label = "ssg-graphs-commons-rough"
input {
  frontend    = "typescript"
  sourceRoot  = "../ssg/original-src/roughjs"
  include     = ["src/**.ts"]
  exclude     = ["src/rough.ts", "src/canvas.ts", "src/svg.ts"]
  options { project = "tsconfig.json", strict = true,
            modulePackages = { "src" = "ssg.graphs.rough", "src/fillers" = "ssg.graphs.rough.fillers" } }
  resolutionRoots = ["../ssg/original-src/points-on-curve", "../ssg/original-src/path-data-parser", …]
}
output { portRoot = "ported/ssg-graphs-commons", sourceSet = "main" }
manifest {
  name = "ssg-graphs-commons"
  dropTypes = ["\"src/rough\".RoughGenerator … "]      # DOM-facing
  surface = [ { phase = "nullability", target = "named:lowlevel.Nullable" },
              { phase = "collections", families = "typescript" },
              { phase = "number-split", derive = true } ]
  parity { module = "../ssg/ssg-graphs-commons", compare = true }
}
```

---

## 5. The Dart frontend

### 5.1 The analyzer

`package:analyzer` (the same library `dart analyze`, the IDE server and `build_runner` use)
resolves a package fully: `AnalysisContextCollection(includedPaths: [pkgRoot])` →
`context.currentSession.getResolvedUnit(path)` → `ResolvedUnitResult.unit`, a `CompilationUnit`
in which every `Expression` carries `staticType: DartType` and every identifier its `element`
(`analyzer` ≥ 7 uses the `Element2` API; pin one version). Resolution reads the package's own
`package_config.json`, so the exporter runs after `dart pub get` inside the upstream checkout —
the analogue of Spoon's classpath. Requirements this machine does not meet today: a Dart SDK (3.x)
on the path (`dart` is absent); Phase 3 starts with `just doctor` learning to install one.

What it gives that the TS checker does not: sound nullability by construction (`NullabilitySuffix`
on every type), no `any` unless the source says `dynamic`, no flow-typing surprises beyond
promotion of local variables (which the analyzer reports as the identifier's `staticType`, same
recording as TS), one-constructor-per-name, no overloads, no truthiness, integer/double split in the
source (`int`, `double`, `num`).

The exporter is `frontend-dart/exporter/bin/export.dart`, producing the same RAST document shape
with `k` = the AST node class name (`ClassDeclaration`, `MethodInvocation`, …), the analyzer's
`DartType` serialised into the same `types` table (`InterfaceType` → `Reference`, `FunctionType` →
`Function` with `named`/`optionalPositional` parameter groups, `RecordType`, `TypeParameterType`,
`DynamicType`, `NeverType`, `VoidType`; `NullabilitySuffix.question` → a `Union` with `null`),
and `Token.precedingComments` → comment ranges. `DartKinds` registers every `AstNode` subclass name
(the analyzer's `AstVisitor` interface enumerates them — the totality source).

### 5.2 IR additions Dart needs beyond TS's

| feature | translation | IR change |
|---|---|---|
| named parameters `{required int a, int b = 0}` at declarations | Scala parameters with defaults; `required` = no default | none |
| named ARGUMENTS `f(a: 1)` | `Tree.NamedArg` | node (shared with TS defaults) |
| optional positional `[int a = 0]` | defaults | emitter (param default) |
| factory / named constructors, redirecting constructors, initializer lists | lowering to companion factories + one primary (§2) | none; a `Decision` per lowered constructor (`ConstructorLowered`) |
| `late` | `isLazy` where an initialiser exists; counted `var` otherwise | none |
| extensions | qualified static calls (§2) | none |
| mixins, `on` constraints | `selfType` | none |
| `implements` of a concrete class | refuse | `Unportable(ImplicitInterface)` |
| cascades | temp + statements | none |
| string interpolation | concatenation | none |
| `is`/`as`/`is!` | `InstanceOf`/`Typed` | none |
| Dart 3 patterns | `AltPattern`, `SeqPattern` nodes; object patterns lowered | node ×2 |
| switch expressions | `Match(isExpr = true)` | none |
| records | `TupleN`; named records → minted case class | none; `StructuralTypeNamed` |
| `async`/`await`/`Stream` | out of scope for dart-sass (the sync tree is what the port takes — see 5.3); elsewhere `Unportable(Async)` | kind |
| operators | `def +` etc.; `operator []=` → `update` | none |
| `covariant` parameters | dropped, counted (`CovariantParam`) | decision |
| `part`/`part of` | the unit is the LIBRARY; parts fold in with their own `Origin`s | none |
| `_private` names | `isPrivate` at LIBRARY scope — Scala's `private` is class-scoped, so a library-private member used from another class in the same library becomes `private[<package>]` (the emitter already derives the qualifier from the package) | none |
| `dynamic` | as `any` (§2) | kinds shared with TS |
| `int` | `Int` — but Dart `int` is 64-bit; `Long` where the reference port spells it (derive) | none; a `DT-E` row |

### 5.3 dart-sass scope

In scope: 293 files, ≈53,600 LOC (§3.4) — `lib/src` minus `js/`, `embedded/`, `executable/`.
The feature counts that size the lowering work: 612 mixin applications, 71 extensions, 71 factory
constructors, 116 operator overloads, 56 `late`, 351 `switch`, 13 `required` named parameters
(named parameters without `required` are far more common; the census counted the keyword), 39
`dynamic`, 7 records, 3 `sealed`. Two structural facts decide the rest of the scope:

- **dart-sass generates its synchronous evaluator from the asynchronous one** (`tool/grind/
  synchronize.dart` turns `async_evaluate.dart` into `evaluate.dart`, both checked in). The port
  takes the generated SYNC files as its source, which is what `ssg-sass` did (`docs/architecture/
  sass-port.md` lists `callable/async.dart` as out of scope). The frontend therefore never sees
  `async`/`await` in the ported tree; a `Future` that still appears is a counted refusal.
- The hand port's out-of-scope list is a `dropTypes`/exclude list, not a judgement the engine makes:
  `lib/src/embedded/**` (protobuf, isolates), `lib/src/js/**` (JS interop), `lib/src/executable/**`
  (CLI), `lib/src/importer/js_to_dart/**`. The rest — `ast/`, `parse/`, `visitor/`, `value/`,
  `functions/`, `extend/`, `importer/` (filesystem/package), `util/`, `logger/` — is the port.
- The differential oracle is sass-spec (13,902 cases; the hand port passes 13,865). A regenerated
  `ssg-sass` is gated on the SAME number, run through SSG's existing runner adapter
  (`.rescale/runners.yaml`), which means the port's test lane is a spec runner rather than a
  converted unit suite — a new `SourceSet`-level option in `PortRun` ("external spec runner").

---

## 6. JavaScript, Ruby, Mermaid — what not to build

### 6.1 Terser (`ssg-js`)

`lib/ast.js` defines every node class through `DEFNODE(name, props, methods, base)`, a runtime
class factory; `lib/compress/*.js` attaches behaviour through `def_*` helper macros on prototypes.
No static tool — not the TS checker with `allowJs`/`checkJs`, not Flow — can see the members of a
class built that way, so every property access in ~22k LOC is untyped. A library-specific rule could
unfold `DEFNODE` calls syntactically (they are regular), but the fields would still be `any` and the
whole compressor would go out through `DynamicMember` refusals. Verdict: no frontend can produce
a port here that is better than the hand port, and the hand port's own gap analysis
(`docs/architecture/terser-port.md`: `compress/index.js` at 0.26×, `sourcemap.js` and
`mozilla-ast.js` missing) is a manual work list, not a frontend gap.

### 6.2 jekyll-minifier (`ssg-minify`)

Ruby, untyped, and a wrapper: the module's real content is the minifiers the gem calls —
`htmlcompressor` (a Ruby port of the Java `htmlcompressor`), `cssminify2` (a Ruby port of the YUI
CSS compressor, itself Java). If those minifiers are ever regenerated, the Java originals are the
source to point the EXISTING frontend at; the Ruby is a wrapper the port already rewrote as
`ssg.minify.Minifier`. Verdict: no Ruby frontend.

### 6.3 Mermaid (`ssg-mermaid`)

TypeScript, but three kinds of code in one package: (i) diagram databases, types and utilities —
ordinary strict TS, translatable; (ii) renderers — d3 selections, dagre/elk layout, DOM
measurement (`getBBox`), which the hand port replaced with its own SVG builder and layout engines in
`ssg-graphs-commons`; (iii) parsers — jison-generated JS for most diagrams (an LALR driver plus
tables) and langium-generated TS for the newer ones. (i) is Phase 4 material for the TS frontend
with the renderers dropped and substituted; (iii) has one deterministic path worth noting: a jison
parser is a fixed ~600-line driver plus a table literal, so a `runtime/` port of the driver once
and the tables as data would regenerate every jison parser mechanically. That is a separate,
optional Phase 4 item, not a frontend.

---

## 7. The phased plan

Durations are estimates for one engineer who knows the engine; each phase ends on a measured gate,
and every number below is reproduced by a `just` lane before it is written anywhere (`CLAUDE.md`
§5). One subject per wave, `before->after` in every commit subject.

### Phase 0 — the frontend seam (≈1 week)

Goal: a second frontend can exist without touching Spoon, and nothing measurable moves.

| # | deliverable | where | gate |
|---|---|---|---|
| 0.1 | `trait TirFrontend { def build(cfg: FrontendConfig, subs: Substitutions, catalog: CatalogLog, lenient: Boolean): Program; def language: Language; def defaultInclude: List[String]; def kinds: KindRegistry }` | `api/.../core/Frontend.scala` (beside the frozen `Frontend`) | compiles; the frozen BIR trait untouched |
| 0.2 | `SpoonTirFrontend` wrapping `SpoonTir.buildModel` + `fromTypes`; `PortRun(frontend: TirFrontend = SpoonTirFrontend())`; `translateOnce` calls it | `frontend-spoon`, `runner/PortRun.scala` | `just measure-all`: every baseline byte-identical (fingerprints carry no new `policy=` segment — the no-op rule) |
| 0.3 | `.conf`: `input.frontend = "java-spoon"` (default) and `input.options { … }` as `FrontendConfig.options: Map[String, String]` (empty default); `FrontendRegistry` by `ServiceLoader`, the `TransformRegistry` pattern | `runner/PortConfig.scala`, `api` | a conf naming an unknown frontend fails with the registry's list |
| 0.4 | `DiffId(lang, area, n)` with `Lang.Java` default rendering `JS-`; `TS-`/`DT-` reserved; `Differences.retired` unchanged | `api/.../catalog/Catalog.scala` | `just catalog` output identical for Java rows |
| 0.5 | `upstream_guard` and `PortConfig.DefaultInclude` read the frontend's `defaultInclude` | `scripts/_lib.sh`, `Justfile` | `counts.tsv` `upstream` rows unchanged on every port |
| 0.6 | `DESIGN.md` §3.2 rewritten for the seam; `Origin.javaPath` documented as "the upstream source path, any language" | docs | — |

### Phase 1 — TypeScript frontend and the rough.js prototype (≈4 weeks)

| # | deliverable | gate |
|---|---|---|
| 1.1 | `frontend-ts/exporter`: `export.ts` over `ts.createProgram`, RAST v1 writer, `--strict`, `kinds` table, SHA-256 per file; vitest fixtures (`fixtures/*.ts` → `*.rast.json`, checked in); `just ts-export`/`just ts-fixtures` recipes; `typescript` pinned exact | two exports byte-equal; fixture JSON reviewed in the diff |
| 1.2 | `frontend-ts` Scala: RAST codecs (jsoniter-scala), `TsKinds` registry + `NodeKindTotalitySpec` against the exporter's kind table, `TsMinter` | totality spec green; the registry lists every `SyntaxKind` as `Lowered`/`Positional`/`Absent(how)` |
| 1.3 | lowering for the subset the four helpers use: modules → objects, functions, `const`/`let`/`var` (hoisting), arrays/tuples, arithmetic, `for`/`while`/`if`/`switch`, template literals, arrows, optional params + defaults, union floor for `null`/`undefined`, `Truthy`, narrowing materialisation, structural-type mint | `path-data-parser` → `Program` with 0 open markers; `TirPrinter.canonical` golden |
| 1.4 | IR/emitter: `Tree.NamedArg`, param default as `Term`, arms in `StandardTraversal`/`Xref`/`TirPrinter`/`TirEmitter`; `runtime/js/Truthy` | Java ports flat (`just measure-all`) |
| 1.5 | `TsVocab`: `Array`/`Map`/`Set` families for `CollectionsTransform`, `Math`/`String`/`Number` rows (`TS-L`), regex rows; `TS-E/S/C/G` catalog rows for every §4.4 lowering, attached and consulted | `catalog(unreached)` = 0 on the prototype |
| 1.6 | `ported/ssg-graphs-commons` conf (§4.6) over the four helpers; JVM row compiles; `api-parity` against the hand port with every divergence classified in `divergence-verdicts.tsv` | `errors.tsv` 0 by member; hand-port tests for the four helpers pass on the emitted code |
| 1.7 | `NumberSplitTransform(seeds, scope, derive)` on `FlowPropagation`; measured on roughjs with `derive = true` against the hand port | before: all `Double`, compiles; after: `Int` where the reference spells it, seams counted, tests still pass |
| 1.8 | roughjs core (`core`, `geometry`, `math`, `generator`, `renderer`, `fillers/*`), DOM files dropped, `RoughSVG.scala` injected | 0 errors JVM; JS and Native rows compile; the hand port's rough tests pass; `PROGRESS.md` section with the numbers |

Exit criterion for Phase 1: the regenerated rough family replaces the hand-written files in
`ssg-graphs-commons` (drop-in lane) with the rough tests green on all three platforms.

### Phase 2 — TypeScript at scale: KaTeX (≈6 weeks)

| # | deliverable | gate |
|---|---|---|
| 2.1 | lowering for the rest of the language KaTeX uses: classes with accessors, `static`, generics with defaults, mapped/conditional types as the checker resolves them, `arguments` refusal, `instanceof`, `Object.keys`, `allowJs` data files (object literals → `Map` literals), `export default`, enums | `TsKinds` shows every kind KaTeX exercises as `Lowered` or `MarkedUnportable`; markers enumerated by kind |
| 2.2 | `DiscriminatedUnionTransform(scope, mutableFields)` — a §1(b) idiom phase: literal-discriminated object-type union → sealed trait + case classes, narrowing → `match`; policy says which unions and whether fields are `var`; refusal enumeration per the idiom-lane rules | the `AnyParseNode` family converts; every declined site names its guard |
| 2.3 | Jest policy for `TestFrameworkTransform` (`describe`/`it`/`expect` matchers → MUnit) | `test/katex-spec.js` converts; counted unsupported matchers |
| 2.4 | `ported/ssg-katex` conf; `contrib/` and `katex.ts` DOM sites dropped/injected; parity vs `ssg-katex` with the `Nullable` target | 0 errors JVM; katex-spec pass count recorded; JS/Native rows |
| 2.5 | `ENGINE-LIMITS.md` entries for what refused at scale (the `Partial<T>` extend idiom, `""`-sentinel literal unions) | each with its count |

### Phase 3 — Dart frontend and dart-sass (≈8 weeks)

| # | deliverable | gate |
|---|---|---|
| 3.1 | `just doctor` installs a pinned Dart SDK; `dart_guard` records SDK + `analyzer` versions in `counts.tsv` | a version change fails the guard |
| 3.2 | `frontend-dart/exporter` (`bin/export.dart`, `package:analyzer` pinned) → RAST with Dart kinds and `DartType`s; `DartKinds` + totality spec from the `AstVisitor` method list | two exports byte-equal on `lib/src/value/` |
| 3.3 | `frontend-dart` lowering: library/part units, `_private` → `private[pkg]`, constructors (named/factory/redirecting/initialiser lists/`this.x`), `late`, extensions → qualified calls, mixins with `on` → self types, cascades, interpolation, `is`/`as`, operators, named args (`NamedArg`), optional positionals, Dart 3 patterns (`AltPattern`, `SeqPattern`, object-pattern lowering), switch expressions, records → tuples/minted classes | `value/` + `ast/` (14k LOC) → 0 open markers; `DT-*` catalog rows attached |
| 3.4 | Dart vocabulary: `dart:core` (`String`, `int` 64-bit → `Long` where derived, `List`/`Map`/`Set`/`Iterable` families for the collections phase, `StringBuffer`, `RegExp`), `package:collection`, `package:charcode`, `package:path` | `DT-L` lanes; unmatched rows listed |
| 3.5 | external-spec lane: `PortRun` option `specRunner` running SSG's sass-spec adapter (HRX unpacker reused) against the emitted module; the pass count is a baselined number in both directions | the hand port's CURRENT count measured first (§3.1's discrepancy resolved by running it) |
| 3.6 | `ported/ssg-sass` conf over the 293 in-scope files (sync twins only); JVM row; parity vs `ssg-sass` | errors by member → 0; sass-spec ≥ the hand port's measured count; JS/Native rows |

### Phase 4 — advanced TypeScript / JavaScript (scoped spikes, each with a stop rule)

| # | item | stop rule |
|---|---|---|
| 4.1 | Mermaid diagram databases + utilities with renderers dropped (`*Renderer.ts`, `svgDraw*`, styles) and the hand port's `render/` injected; only the 20 upstream-traceable diagrams | stop if `any`/`@ts-ignore` refusals exceed 10% of members in the first three diagrams |
| 4.2 | jison driver as a `runtime/` shim + generated tables as data (regenerates every jison parser mechanically) | stop if the driver needs more than the ~600-line generic LALR loop |
| 4.3 | Terser `DEFNODE` unfolding as a §1(c) rule: `DEFNODE(name, props, ctor, methods, base)` → `ClassDef`; fields typed `Any`; measured | stop at the measurement: if `DynamicMember` markers exceed 30% of member bodies, record the number in `ENGINE-LIMITS.md` and close |
| 4.4 | TS decorators, generators (`Iterator` lowering), `async`/`await` → `Future` | only when a targeted upstream needs them |
| 4.5 | beautification backend: imports, `s""` interpolation, `recv.m()` for extensions, `enum` for literal unions | after the structural phases are stable; never a correctness prerequisite |

### What is deliberately not planned

- A Ruby frontend (§6.2). A JavaScript frontend beyond what the TS checker gives for `allowJs` files
  (§6.1). Porting the four post-11.0.0 and six invented Mermaid diagrams (they have no upstream).
- Re-implementing any type checker on the JVM. Emitting Scala structural types.

---

## 8. Decisions to record, and where

| decision | home |
|---|---|
| the TIR frontend interface (`TirFrontend`) and that `PortRun` takes one | `DESIGN.md` §3.2 (replaces "a second frontend is added beside Spoon") |
| the exporter/reader split and the RAST format | `DESIGN.md`, new §3.13 |
| the catalog's language dimension (`TS-`, `DT-`) | `DESIGN.md` §2.8 |
| `NumberSplitTransform` as a §1(b) phase on `FlowPropagation` | `CLAUDE.md` §1(b) table row |
| the structural-type mint and its naming | `DESIGN.md`; the `Decision` kind |
| "no static frontend for a runtime class factory" (Terser), measured | `ENGINE-LIMITS.md`, one entry with the DEFNODE count |
| Ruby: none, and why | `ENGINE-LIMITS.md` |
| the Dart SDK and `node` as measurement inputs (`ts_guard`, `dart_guard`) | `.claude/rules/measurement.md` |

## 8. Implementation status (2026-09-12)

| Phase | Status | Evidence |
|-------|--------|----------|
| **0.1–0.2** | Done | `TirFrontend` trait, `SpoonTirFrontend`, `PortRun` integration |
| **0.3–0.4** | Done | `FrontendRegistry` (ServiceLoader), `DiffId(lang)`, `.conf` `input.frontend` |
| **1.1** | Done | TypeScript exporter (`ts.createProgram`, RAST v1 JSON), deterministic |
| **1.2** | Done | `frontend-ts` Scala module (jsoniter codecs, `TsFrontend`, `TsMinter`) |
| **1.3** | Done | Integration tests (path-data-parser 4 files, 15 units, 148 syms, 0 unportable) |
| **1.4** | Done | For/ForEach/Match/Lambda/DoWhile/Break/Continue/Try lowering |
| **1.5** | Stub | TsVocab documented, needs CollectionsTransform integration |
| **1.6** | Done | Multi-library tests (5 libraries, 6 tests passing) |
| **1.7** | Stub | NumberSplitTransform needs FlowPropagation substrate |
| **1.8** | Done | rough.js full: 58 units, 770 syms, 64 unportable (object literals) |
| **2.x** | Validated | KaTeX exports 109 files, 1 diagnostic; needs DiscriminatedUnionTransform |
| **3.x** | Designed | Dart needs package:analyzer SDK; ssg-sass scope assessed |
| **4.x** | Designed | Mermaid/Terser scoped spikes documented |

### Measured corpus through the pipeline

| Library | Files | Units | Symbols | Unportable | Notes |
|---------|-------|-------|---------|------------|-------|
| path-data-parser | 4 | 15 | 148 | 1 | 1 object literal |
| points-on-curve | 2 | 11 | 90 | 3 | object literals |
| hachure-fill | 1 | 7 | 72 | 0 | fully lowered |
| rough.js | 17 | 58 | 770 | 64 | object literals/configs |
| **KaTeX** | 109 | — | — | — | exports clean, not yet minted |
