---
name: customize-port
description: Change what a Baltic Porter port does — configure a parameterised (b) phase from the conf (every factory's keys, the shared scope grammar), or, when config cannot express it, write a §1(c) rule as a Phase plus a TransformFactory in your own repository and test it with the testkit. Use after `read-port-issues` has told you which of §1's three kinds the fix is.
---

# Customising a port

You know what the port got wrong and which of `CLAUDE.md` §1's three kinds the fix is (if not, go to
**`read-port-issues`** first). This skill is the mechanics of (b) and (c).

**Decide the kind BEFORE writing anything.** Get it wrong and the framework silently becomes a
porter for one library:

- **(a) universal** — a fact about Java and Scala, true of every codebase. It belongs in the ENGINE,
  unparameterised. Not in your repository. Report it; do not work around it locally.
- **(b) same mechanics, different values** — the engine ships the mechanism; you supply your
  library's values. **This is where most fixes belong.** An empty parameter must make the phase a
  no-op, so "turned off" needs no code path.
- **(c) only ever this library** — a separate rule you plug in, in your own repository.

**Reach for (c) only after establishing that the mechanism genuinely cannot be shared.** Most things
that look library-specific are a (b) with the policy inlined — that is exactly the mistake
`ReflectionToPortableTransform` made, hard-coding one library's class and its member list inside the
engine. A rule whose only library-specific part is a LIST OF NAMES is a (b); a rule that encodes an
INVARIANT of that library's design is a (c).

## 1. (b) — configuring a phase from the conf

A surface entry is `{ transform = "<stable-kebab-name>", …that transform's own keys… }`. The names
are resolved through `java.util.ServiceLoader`, and an unknown one lists every name your classpath
offers. Every key a factory does not read fails the run, so a typo is loud rather than a silent
no-op.

### The shared `scope { }` grammar — every retyping rule takes it

```hocon
scope { except = ["com.foo.Bridge"] }   # RuleScope.Everywhere(except)
scope { only   = ["com.foo.gl"] }       # RuleScope.Only(include)
# absent                                # RuleScope.Everywhere() — the no-op default
```

Both halves at once is REFUSED: `Everywhere(except)` and `Only(include)` point in opposite
directions and there is no value that is both. An empty `only = []` is honoured as written. Scopes
match by FQN and cut only at a `Symbol.fullName` separator, so `com.foo` never covers `com.foobar`.

Two things a scoped retyping phase owes you, and you should check both after scoping anything:
the scope is a fact about the emitted SURFACE (two modules scoping it differently emit signatures
that each compile alone and cannot compile together — that is what the `manifest` check is for), and
**every seam the scope creates is COUNTED** (`collection-boundary`).

### The engine's factories and their keys

Enumerated from `balticporter.runner.BuiltinFactories`. Anything not listed here is not read, and a
key not read fails the run.

| `transform` | keys | notes |
|---|---|---|
| `collections` | `scope`, `retarget`, `retargetRewrites`, `retargetTypeArgs`, `retargetCoercions`, `reifiedCarriers`, `reflectiveSinks` | retype JDK collections and API-map their call sites. `retarget` retypes a library type whose scala target is usable wherever the java source was (extends/implements it). `retargetRewrites` maps per-member call-site rewrites (nine variants, see below). `retargetTypeArgs` maps arity-changing retargets. `retargetCoercions` maps boundary coercion templates. `reifiedCarriers` / `reflectiveSinks` name third-party types (K20/K21) |
| `mutable-params` | — | §1(a), no policy; the entry exists so a conf can put it in the pipeline |
| `panama-ffi` | — | §1(a), no policy; its `isNative` predicate is `_.flags.isNative`, a fact about Java |
| `test-framework` | `suite` (default `TestFrameworkTransform.DefaultSuite`), `testMember` (default `"test"`) | JUnit → MUnit is a STRUCTURAL transform, not an annotation rename |
| `static-forwarder` | `forwarders = [ { wrapper, receiver, members = […] } ]` | all three required per entry; a forwarder with no `members` is refused — it can only ever be a mistake |
| `class-table` | `redirects { "a.B#forName" = "c.D#classFor" }` | re-point a reflective name lookup at an explicit table |
| `type-redirect` | `redirects { "a.B" = "c.D" }`, or the same entry as an object: `"a.B" = { to = "c.D", memberRenames { m = "n" } }` | re-point every reference to one type at another. A `memberRenames` key is a member SEGMENT under its owner — `dispose`, or `dispose()` for one overload — and renames every declaration of that member's override COMPONENT before the redirect, so a target that spells the member differently (`Disposable#dispose` → `AutoCloseable#close`) is expressible. Both entry shapes live in one map; the flat one is not legacy. A rename to a name a KNOWN target does not declare, or one whose component reaches a declaration this program cannot move, is REFUSED and counted — never half-applied |
| `method-body` | `bodies { "a.B#m()" = "{ … }" }` | keep the signature, replace the body |
| `port-map-migration` | `bases = […]` **required** | base MODULE NAMES; the maps themselves are discovered from the classpath and the report tree. Named for the phase, not shortened to `port-map`, which is a CHECK name |
| `primitive-to-opaque` | `fqn` **required**, `underlying` (default `Int`), `extraHints = […]`, `scope`; **`hints` REFUSED** | see below |
| `nullability` | `annotations = [...]`, `target` (`"union"` default / `"named"` / `"option"`), `wrapper` (required iff `target = "named"`), `scope`, `nullableMembers = [...]` | move a nullability annotation (or an explicitly named member) into the type. `nullableMembers` is a set of exact member FQNs (`Class#member`) matched at run time against `Symbol.fullName`, treated as if their return/field type carried an annotation: same target shape, same coercions, same boundary count. Empty = no-op; non-empty contributes a fingerprint segment. `MergeablePolicy` union (`ENGINE-LIMITS.md` K13.6) |
| `globals-to-implicits` | `holders = [ { holder, context { inject \| mint }, members { … }, attach, reader, boundary, sites { … }, promoteToClass = […], scope } ]` **required** | globals → CONTEXT (DESIGN.md §8.4). `members` values are dot-PATHS on the context type, not member names (`gl = "graphics.gl20"`); `attach = "method"` puts a trailing `(using T)` on each threaded method and `"class"` puts it on the class's constructors; `boundary` decides what a site with no signature does; `sites` overrides one of them (`"lazy-init"` is the only EAGER→LAZY change and is never a default). Every seam is counted by `context-seam` |
| `class-to-trait` | `specs { "com.foo.Pool" { params = [ { index = 0, name = "initialCapacity" }, { index = 1, name = "max" } ] } }` | rewrite a nominated abstract class into a trait: constructors removed, mapped parameters become abstract vals, every subclass gains `override val` members. The nominated type is typically DROPPED+INJECTED as a hand-written trait (DESIGN.md §8.27, ENGINE-LIMITS.md CT12). Empty specs = no-op. `SurfacePolicy` + `MergeablePolicy` |
| `add-members` | `members { "com.foo.Engine" = [ { name = "register", arity = 2, source = "def register[T](k: Class[T], f: () => T): Unit = ???", why = "factory registry" } ] }` | append hand-written Scala members to a mechanically-translated class. Each member is verbatim text spliced at the end of the owner's body (DESIGN.md §8.29). Empty map = no-op. ADD-scoped `Only(Set.empty)` default. `SurfacePolicy` + `MergeablePolicy` |

### The `class-to-trait` + drop+inject recipe

When a hand port reshaped an abstract class into a trait (verified by `divergence-investigator`),
the port needs three things:

1. **Drop the type**: `dropTypes = ["com.foo.Pool"]` in the manifest.
2. **Inject the hand-port trait**: copy the trait-shaped `.scala` file into the overrides directory
   and add it to the manifest's `inject` list. The injected file must carry the abstract vals
   that `ClassToTraitTransform` will override in subclasses.
3. **Enable the phase**: add a `class-to-trait` surface entry mapping each constructor parameter
   index to its val name.

```hocon
manifest {
  dropTypes = ["com.foo.Pool"]
  inject = ["overrides/sge/utils/Pool.scala"]
  surface = [
    { transform = "class-to-trait"
      specs { "com.foo.Pool" { params = [
        { index = 0, name = "initialCapacity" }
        { index = 1, name = "max" }
      ] } }
    }
  ]
}
```

`InjectedSurface` then reads the injected file's member surface with scalameta: overrides adopt
the injected parameter types and calls follow the injected arity (K35 CLOSED). No additional
configuration needed.

### The `retarget` and `retargetRewrites` `.conf` spelling

A retarget retypes a library type and API-maps its call sites through per-member rewrite entries.
The `.conf` spelling (read by `CollectionsFactory` in `BuiltinFactories.scala`):

```hocon
{ transform = "collections"
  retarget { "com.example.Bits" = "scala.collection.mutable.BitSet" }
  retargetRewrites {
    "com.example.Bits" {
      "get/1" = "apply"                                       # Rename
      "set/1" = "addOne"                                      # Rename
      "removeValue/2" {                                       # BoolDispatch
        boolDispatch = 1
        onTrue  = "removeValueByRef"
        onFalse = "removeValue"
      }
      "<init>/0" {                                            # Construct
        companion = "scala.collection.mutable.BitSet"
        factory   = "apply"
      }
      "<init>/(int)" {                                        # Construct with descriptor key
        companion = "scala.collection.mutable.BitSet"
        factory   = "apply"
      }
      "forEach/1" { forEach = "foreach", arity = 1 }         # ForEach
      "select/1" { collect = "collect", into = "lowlevel.util.DynamicArray" }   # Collect
      "toArray/0" { chain = ["toArray"], parens = ["toArray"], dropArgs = false }  # Chain
      "items/0"   { indexedField = "items" }                  # IndexedField
      "items/1"   { fieldWrite = "items" }                    # FieldWrite
      "ensureCapacity/1" {                                    # Template
        template = "{ val $bp0 = $recv; val $bp1 = $0; if ($bp1 < 0) throw new java.lang.IllegalArgumentException(); $bp0.items }"
      }
    }
  }
  retargetTypeArgs {
    "com.example.IntMap" = ["scala.Int", "arg(0)"]            # FixedType + SourceArg
  }
  retargetCoercions {
    # (actualHead, expectedHead) -> template; $0 is the value
    # "lowlevel.util.DynamicArray,lowlevel.JavaIterable" = "lowlevel.JavaIterable.fromIterator($0.iterator())"
  }
}
```

**Descriptor keys.** A member overloaded at the same arity needs a descriptor key: `"name/(params)"`
where `params` are comma-separated type names in the UPSTREAM (java) namespace. Examples:
`"<init>/(int)"`, `"<init>/(Array)"`, `"add/(float,float,float,float)"`. A descriptor key WINS over
an arity key at the same member. Translation to the target namespace happens at construction time.

**The nine `RetargetRewrite` variants:**

| variant | `.conf` shape | what it does |
|---|---|---|
| `Rename` | `"name/arity" = "newName"` (a plain string value) | rename the call |
| `BoolDispatch` | `{ boolDispatch = <argIndex>, onTrue = "…", onFalse = "…" }` | dispatch on a literal boolean argument |
| `Construct` | `{ companion = "…", factory = "…" }` | `new Source(args)` becomes `Companion.factory(args)`. Optional: `dropTrailing = N` (drop trailing args), `fillTypeArgs = true` (supply type args on arity-0) |
| `ForEach` | `{ forEach = "…", arity = N }` | structural for-each lowering with a return-boundary image |
| `Collect` | `{ collect = "…", into = "…" }` | collect-into-builder pattern |
| `Chain` | `{ chain = ["m1", "m2"], parens = ["m1"], dropArgs = false }` | chain member calls. `parens` lists which need `()`. `dropArgs` drops the original arguments |
| `FieldWrite` | `{ fieldWrite = "fieldName" }` | field-write image on a retarget target |
| `IndexedField` | `{ indexedField = "fieldName" }` | indexed field access via source-SymId matching |
| `Template` | `{ template = "expr with $recv, $0, $T0, $Target" }` | expression template with AST holes. `$recv` = receiver, `$0`..`$N` = args, `$T0`..`$TN` = type args (FQN text), `$Target` = retarget target FQN. Argument holes are bound to temporaries when the argument has side effects (evaluate-once, CLAUDE.md §4.4 F7) |

`package-rename` is **not** in this list and is refused by name: it is manifest DATA
(`manifest.packageRenames`), because it must run after every other phase. See **`configure-port`** §4.

### The one thing a conf cannot hold, and the door it names

Three phases take a `Symbol => Boolean`. A config format that grew a way to write one would have
become a scripting language with the engine as its interpreter, so where the port genuinely needs an
arbitrary predicate config refuses and names the escape hatch:

```
port config: manifest.surface[0].hints: `OpaqueSpec.hints` is a `Symbol => Boolean` and a
configuration file cannot hold one — a predicate written as a string would be code the engine
interprets, which is precisely what this SPI exists to avoid (CLAUDE.md §1.5). List the seeds by
fully-qualified name in `extraHints`, or register a `balticporter.tir.TransformFactory` of your own
that builds the `OpaqueSpec` with the predicate in Scala.
```

`extraHints` is spelled exactly as `OpaqueSpec` spells it — a friendlier second name for the same set
would be two homes for one policy.

### The other (b) surfaces, which are manifest DATA rather than phases

`dropTypes` + `inject` (do not emit this type; here is the Scala that supplies its FQN),
`dropMethods`, `packageRenames`, `supportSources`. See **`configure-port`** §4. Remember the
asymmetry: a **drop** is inherited by a dependent, an **injection** is not.

## 2. When config is not enough — a §1(c) rule, in YOUR repository

The worked example is a matched pair in `balticporter/corpus/` — the stand-in for "the porting program's own
repository":

- `balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/GdxSharedIteratorRule.scala` — the rule
- `balticporter/corpus/src/main/scala/balticporter/corpus/libgdx/GdxSharedIteratorFactory.scala` — its factory
- `balticporter/corpus/src/main/resources/META-INF/services/balticporter.tir.TransformFactory` — one line
- `balticporter/corpus/src/test/scala/balticporter/corpus/libgdx/GdxSharedIteratorRuleSpec.scala` — its spec

Read those four before writing your own. What they demonstrate is exactly the three things a new port
needs.

### (i) The rule — implement `balticporter.tir.Phase`

```scala
final class MyLibRule extends Phase:
  def name: String = "mylib-thing"            // a REPORT identity; may contain any characters

  // pick the hooks you need; every one defaults to identity
  override def transformTerm(t: Term)(using Program): Term = t
  override def transformType(tp: TypeRepr)(using Program): TypeRepr = tp
  override def run(program: Program): Program = program   // full control, for a whole-program pass
```

Also available: `runsAfter` / `runsBefore` (by phase name), the specific hooks
(`transformClassDef`, `transformDefDef`, `transformValDef`, `transformTypeDef`, `transformIdent`,
`transformSelect`, `transformApply`, `transformTypeApply`, `transformNew`, `transformLambda`,
`transformBlock`), and `record(d: Decision)` for provenance.

Four obligations, none optional:

- **Walk with `StandardTraversal`, never a private recursion.** Two of the four silent correctness
  defects this project has found were hand-rolled walks that stopped one node short, and a walk that
  misses a node kind reports zero hazards from a program that has them — the worst answer a check
  can give. `StandardTraversal.mapClassDef` / `scanTerm` are the entry points.
- **Ship a CHECK with the translation** (`CLAUDE.md` §3), and record it even when it finds nothing —
  `CheckReport.record(name, findings)` unconditionally, so `counts.tsv` can tell "found nothing"
  from "never ran".
- **Say which of §1's three kinds the fix is, in the finding itself.** A finding a reader cannot
  classify costs a full investigation. The worked rule prints
  `[§1(c) LIBRARY-SPECIFIC: rewrite the INNER loop to …. The engine cannot know this — it is
  libGDX's allocation strategy, not a Java/Scala fact.]`
- **If the rule RETYPES declarations**, it changes the emitted surface: implement `SurfacePolicy`
  (a pure, stable, order-independent `surfaceFingerprint`; sort anything set-like) so two modules'
  instances can be compared, and implement `PolicySource` if it takes policy that can fail to match,
  so a never-fired key reaches the `policy` check.

Your repository may name your library freely. The §1 enforcement grep covers only `api`, `engine`,
`frontend-spoon` and `runtime`, and a (c) rule being outside them is the point.

### (ii) Getting it into the pipeline

**From Scala** — nothing to register; it is an ordinary element of `PortRun(phases = …)`.

**From a `.conf`** — one five-line factory plus one service line, both in your own repository,
compiled by your own build:

```scala
final class MyLibFactory extends TransformFactory:
  def name: String = "mylib-thing"                       // STABLE, kebab-case, published API
  def fromConfig(config: ConfigView): Phase = new MyLibRule
```

```
# src/main/resources/META-INF/services/balticporter.tir.TransformFactory
com.you.port.MyLibFactory
```

```hocon
manifest { surface = [ { transform = "mylib-thing" } ] }
```

The contract, from `balticporter.tir.TransformFactory`:

- `name` is what a `.conf` writes and what an error message lists — **treat it as published API.** It
  is deliberately NOT `Phase.name`: those are report identities and contain characters a config key
  should not (`java-collections->scala`, `junit->portable-suite`). Where a rule has no policy to
  configure there is no reason to spell it twice, and the worked example uses one string for both.
- `fromConfig` gets the surface entry MINUS its `transform` key, and must **throw `ConfigError` for
  anything it cannot honour** — a value it silently ignores is the §1(b) silent no-op this whole
  mechanism exists to prevent. Quote the config path in the error: `config.at(key)` is the string an
  agent edits.
- Every key an accessor is called with is RECORDED, and the loader fails the run on a key nobody
  read. So you do not validate for unknown keys; you only read the ones you honour.
- `ConfigView` is dependency-free on purpose (`api` depends on nothing), which is what makes a (c)
  rule cost one dependency that drags in no emitter, no orchestrator and no Spoon. Accessors:
  `string`, `int`, `bool`, `strings`, `stringMap`, `child`, `children`, `keys`, plus
  `requireString`, `requireChild`, `enumerated`, `at`. A single string is never widened to a
  one-element list — quiet coercion is how a scalar typo becomes a valid document.
- Registration is a `META-INF/services` line naming a class with a **no-argument constructor**. That
  is why factories are `final class`, not `object` — a Scala object's constructor is private and
  `ServiceLoader` cannot instantiate it.
- `TransformFactory.scopeOf(config)` gives you the shared `scope { }` grammar for free. Use it rather
  than inventing a spelling.

**The predicate escape hatch is a factory of your own**: build the `OpaqueSpec` (or whatever value
needs the lambda) in Scala inside `fromConfig`, and let the conf name it. That is exactly as typed as
the Scala path — the predicate lives in your repository, checked by your compiler.

### (iii) Testing it — `balticporter.testkit.PortSuite`, with a NEGATIVE test

```scala
class MyLibRuleSpec extends PortSuite:
  test("the hazard is reported") {
    val r = new MyLibRule
    port("""class Scene { … }""", r)
    assertEquals(r.findings.size, 1)
  }

  test("a shape that is NOT the hazard is not reported") {
    val r = new MyLibRule
    port("""class Scene { … }""", r)
    assertEquals(r.findings, Nil)          // a check that has never reported is not known to work
  }

  test("an ANALYSIS must not change a single byte of the emitted Scala") {
    assertEquals(port(java, new MyLibRule).out, port(java).out)
  }
```

`PortSuite` parses a Java snippet, runs your phases and emits — no engine internals, no hand-built
`Program`, no fixture of your own. It gives you `port(java, phases*)` returning a `Ported` with
`before`, `after`, `out` (the emitted Scala) and `idBefore`/`idAfter`, plus `assertEmits`,
`assertNotEmits`, `assertEmitsMatch` and `assertVacated` (the XREF form: a type has no usages left,
which is a different and stronger fact than a name having vanished from the output).

**Include negative tests.** The worked spec has three of them beside its one positive, and the
reason is stated in the file: the rule currently finds zero hazards in the real library, so the only
evidence it can find one at all is a snippet where it must.

Run them:

```
sbt -batch "testOnly balticporter.corpus.libgdx.*"
```

Use `sbt -batch "testOnly *"` for the whole suite — **never bare `sbt test`**, which maps to
`testQuick` in this build and silently reports "No tests to run", and never `testFull` over an
unchanged tree, which keys on BYTECODE and is a cache REPLAY rather than a run.

## 3. The full-strength door — a hand-written `PortRun`

A conf holds names and sets. When a port needs a `Symbol => Boolean` you would rather not wrap in a
factory, a computed file list, or several source sets driven from one program, write the `PortRun(...)`
by hand instead — see **`add-corpus-library`** §2.01 for its shape and for what is now an error
rather than an omission (passing a `PackageRenameTransform` in `phases`, supplying `externalConcrete`,
a check going unrun). Both doors construct the same values; neither is a downgrade.

## 4. After it works — push the rule DOWN the scale

A (c) that proves useful on a SECOND library was never a (c). When that happens:

- if its library-specific part is a set of names or a map, **parameterise it and move it into the
  engine as a (b)** — with an empty parameter as a no-op, and a `RuleScope` if it retypes;
- if it turns out to be a fact about Java and Scala, it is an **(a)** and belongs in the engine
  unparameterised.

Each library added to the corpus is expected to move rules from (c) toward (b) toward (a). A rule
that survives three libraries unchanged is probably universal; one that needs a new parameter per
library is correctly a (b).

**Then write the lesson down in the same commit** (`CLAUDE.md` §3.6): a governing rule to
`CLAUDE.md`, a MEASURED dead end with its number to `ENGINE-LIMITS.md`, a decision to `DESIGN.md`,
state and residues to `PROGRESS.md`. A rule that names a specific library is per-library policy and
belongs in that library's manifest instead. Do not add a seventh document, and do not commit the
scratch file you worked it out in — `.balticporter/` is gitignored and is where it lives.

Then measure: change one thing, then measure (**`port-first-attempt`**).
