# Roadmap: non-Java frontends

This is what remains to be built on the TypeScript, JavaScript and Dart frontends described in
[Non-Java frontends](architecture/non-java-frontends.md), ordered by priority. Every item below
was checked against the current code in `balticporter/frontend-ts`, `balticporter/frontend-dart`
and `balticporter/engine` before being listed; nothing that already exists as a mechanism is
included as remaining work, even where it has not yet been applied to a specific library.

## 1. A shared lowering-rule catalogue on the minter, retiring the disposable text emitter

**What.** Every decision worked out so far for translating TypeScript and JavaScript constructs —
how numbers, arrays and optional parameters are represented, how truthiness and template literals
and destructuring lower, how a `switch` with fallthrough or an object literal against a known
interface is handled — needs to live in one shared analysis module that both the minter and any
throwaway harness can call, instead of being duplicated across a per-library emitter. **Why.** The
minter (`TsMinter`) constructs identifiers with no resolved symbol, binary expressions with no
operator, and member accesses with no resolved member; nothing it produces today can be printed by
the rest of the engine. Every file that currently compiles does so through the separate,
text-producing `TsToScalaEmitter`, which never reaches the phases, checks or reports the rest of a
port depends on. **Where.** `frontend-ts/src/main/scala/balticporter/frontend/ts/TsMinter.scala`
and `TsToScalaEmitter.scala`. **Done when.** The minter interns a real symbol for every declaration
and reference, preserves every operator, and at least one small fixture (for example, the
three-file `path-data-parser` library) reaches a compiling result through the minter alone.

## 2. A named-argument tree node

**What.** Add a tree node for a named argument, so an object literal instantiating a known
interface, or a Dart named call, renders as `name = value` rather than relying on declaration
order or on matching the literal's field names against a hard-coded list. **Why.** Without it, the
dedicated emitters recognize an object literal's target type by checking which field names it has
in code — exactly the kind of per-library knowledge a named-argument form removes, and Dart's named
arguments have no home at all today. **Where.** The node belongs in `api`'s tree definitions, with
arms in the tree walker, the reference index and the emitter; both `frontend-ts`'s object-literal
lowering and `frontend-dart`'s named-argument lowering consume it once it exists. **Done when.** An
object literal or a Dart named call renders with explicit parameter names, and the by-field-name
matching in the dedicated emitters is deleted.

## 3. Two pattern nodes for Dart's pattern matching

**What.** An alternative-pattern node (matching one of several patterns in a single arm) and a
list/sequence-pattern node, needed for Dart's switch expressions and destructuring patterns; Java
has no equivalent construct, so nothing today can represent them. **Where.** `api`'s tree
definitions and `frontend-dart/src/main/scala/balticporter/frontend/dart/DartMinter.scala`. **Done
when.** A Dart file using an alternative or list pattern in a `switch` lowers without an
unhandled-construct marker.

## 4. An integer/double split for TypeScript's and JavaScript's single number type

**What.** A parameterised phase that seeds `Int` at array indices, `.length` results, loop
counters and bitwise-operator operands, then propagates that seed the same way the engine's
existing primitive-retyping mechanism (`PrimitiveToOpaqueTransform`) already does for other
primitives. **Why.** Every `number` is `Double` today, so every array or string index needs an
explicit conversion at the call site; this is a matter of reusing an existing propagate-and-coerce
mechanism with a TypeScript-specific seed set, not inventing new machinery. **Where.** The phase
belongs beside the engine's other retyping phases in `balticporter/engine/src/main/scala/
balticporter/transform/`; its seed derivation is frontend-ts's own. **Done when.** An index-only
slot compiles as `Int` with no explicit coercion in the fixtures that exercise it.

## 5. A standard-library vocabulary table dispatched by resolved symbol

**What.** A table mapping `(owning type, member name)` — read off the symbol RAST already
resolves — to a Scala rewrite, covering `Array`, `String`, `Math`, `RegExp`, `Number`, `Map`, `Set`
and `JSON`, mirroring the JDK vocabulary the Java path already has. **Why.** The dedicated emitters
currently recognize a standard-library call by matching rendered source text (any call ending in
`.push`, for instance), which also fires on an unrelated user method with the same name. **Where.**
A new file beside `frontend-ts`'s other frontend logic, consumed by the minter (item 1) and by
`JsFrontend`. **Done when.** Every place a dedicated emitter matches a library call by text today
reads the table instead.

## 6. A Jest/vitest test-framework conversion policy

**What.** Extend the engine's existing test-framework conversion mechanism
(`TestFrameworkTransform`, `balticporter/engine/src/main/scala/balticporter/transform/
TestFrameworkTransform.scala`, already configured for JUnit) with a table for `describe`, `it`,
`beforeEach` and the common `expect(...).toBe/toEqual/toThrow` matchers. **Why.** Porting a
library's own tests and running them against the emitted code is the primary way any port is
verified; without this, a TypeScript or JavaScript library has no such oracle, only the
hand-ported suite, which tests the hand port's own shape rather than the translation. **Where.** A
new policy table beside the existing mechanism, driven the same way the JUnit one is. **Done
when.** A converted suite compiles and its assertions run against emitted code.

## 7. A jison-grammar-to-Scala parser generator

**What.** At export time, expand a `.jison` grammar into its generated JavaScript parser, export
that under the JavaScript path, and translate its action-dispatch table and lexer rules onto one
shared parsing driver, instead of a hand-written recursive-descent parser per grammar. **Why.** A
library that ships several jison grammars currently needs one from-scratch parser per grammar; one
driver amortizes that work across every grammar it is given. **Where.** Partially started:
`frontend-ts`'s dedicated package (`JisonActionExtractor.scala`) already extracts a grammar's
productions and the calls their actions make; the shared driver and the lexer-table translation do
not exist yet. **Done when.** One grammar's generated parser drives its already-translated target
module through the shared driver, with no hand-written parsing logic left for that grammar.

## 8. Finishing and measuring the declaration-file bootstrap for a runtime-defined class hierarchy

**What.** From a recognized runtime class-factory pattern and a hand-ported reference's field
types, generate a TypeScript declaration file describing the classes and their fields with
concrete types, then re-run the exporter with it in the program so the checker can type a method
body it currently sees only through `any`. **Why.** A class built by calling a factory function at
runtime (Terser's AST classes are the motivating case) is invisible to the checker as a class, so
every field access inside it is untyped; deriving field types from a reference is the only way
past that without writing a second type checker. **Where.** `frontend-ts`'s dedicated package
(`AstDtsGenerator.scala`, `GenAstDts.scala`) already generates the declaration file and maps
reference types to TypeScript syntax. **Status unverified**: whether the resulting proportion of
still-untyped member bodies has been measured. **Done when.** That proportion is measured; a high
result is recorded as a limit rather than pursued further.

## 9. A d3-selection-to-SVG-builder retarget table

**What.** A per-member rewrite table, in the same shape as the engine's existing collections
retarget mechanism (`CollectionsTransform`), mapping calls against a d3 selection onto a hand
port's own SVG-builder type, which already shares most of d3's member names; refusing and counting
the handful of members with no equivalent (data joins). **Why.** A renderer built by chaining calls
on a d3 selection would otherwise need one call at a time hand-translated. **Where.** A new table
in `frontend-ts`, consumed by the dedicated Mermaid emitters. **Done when.** A renderer's d3 chain
lowers through the table with named, counted refusals for anything it cannot answer.

## 10. Mapped and utility TypeScript types resolved through the checker

**What.** Read a type built through `Required<T>`, `Partial<T>`, `Pick<T, K>` or `Record<K, V>` as
the checker already flattens it, instead of treating it as an opaque reference with no visible
members. **Where.** An exporter change (export the resolved property set for a mapped type) plus a
minter arm to consume it. **Done when.** A configuration object typed through one of these utility
types translates with every member present.

## 11. Static-registry lowering for a runtime registration call

**What.** Recognize a call that registers a value under a string key in a runtime table and mint a
`Map` in the enclosing module instead, so the registration and its lookup are both translated
rather than left as an untranslatable dynamic call. **Where.** `frontend-ts`, as a parameterised
rule naming which registration function and which argument is the key, not one hard-coded to a
single library's function name. **Done when.** A registered value is retrievable by key from the
emitted table with no runtime reflection.

## 12. Smaller, lower-priority mechanisms

A handful of remaining items are each needed by one part of one library and are lower priority
than the above: lowering an ES6 `class` declaration (constructor, fields, `get`/`set`, `extends`,
`super`) for Terser's tree-walker classes and a themes module's classes; lowering a closure that
returns an object of methods sharing its local state into a class with fields, for Terser's
tokenizer, parser and output stream; lowering an options-defaulting call
(`defaults(options, { key: default, … })`) into a case class that also tracks which options were
explicitly set; and small per-library vocabulary tables (a date library onto `java.time`, a color
library, a small utility library) for the libraries that use them. Each is a self-contained,
independently testable rule with no dependency on the items above other than the shared analysis
module from item 1.

## Not planned

A Ruby frontend, a from-scratch JavaScript type checker, and emitting Scala structural types
remain out of scope for the reasons already recorded in
[Non-Java frontends](architecture/non-java-frontends.md): no static frontend can see a class built
by a runtime factory well enough to type it faithfully, and Scala already has a language feature —
literal types, `Selectable` reflection — for the shapes a bespoke structural encoding would
otherwise reinvent.
