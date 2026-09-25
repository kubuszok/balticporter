# Customizing the translation

A port's `.conf` (see [Configuring a port](configuring-a-port.md)) can enable a list of phases in
`manifest.surface`, each with its own keys. This page documents what each shipped phase does, and
how to write your own when the config format genuinely cannot express what your library needs.

## Three kinds of rule

Before changing anything, decide which of three kinds the fix is — getting this wrong the first
time is how a general-purpose engine quietly turns into a converter for one library:

- **A fact about Java and Scala, true of every codebase.** Array covariance, `x++` used as a value,
  `switch` fall-through — these belong in the engine, unconditionally, and are never something a port
  configures. If you find yourself writing code in your own repository to compensate for one of
  these, report it instead; see [Java semantics Scala does not share](java-semantics.md).
- **The same mechanism for every library, with a per-library policy.** Renaming a package, retyping a
  collection, dropping a member, moving a nullability annotation into the type: the mechanics are one
  phase, shipped by the engine, and *which* declarations it applies to is a value you supply from your
  `.conf`. This is where almost every library-specific fix belongs. A phase's parameter, left empty
  or absent, must leave the phase a complete no-op — "turned off" needs no special case.
- **Knowledge that could only ever apply to one library.** An allocation invariant, a runtime
  class-building convention, a quirk of one library's own design — something no amount of
  configuration data would generalize. This is a separate rule you write and register from your own
  repository, never a change to the engine.

Reach for the third kind only after convincing yourself the mechanism really cannot be shared. A
rule whose only library-specific part is a list of names or a map is the second kind with the policy
written inline by mistake.

## How phases run

Each `surface` entry is `{ transform = "<name>", …its own keys… }`. Names are resolved by
`java.util.ServiceLoader` at load time; naming one that is not on your classpath lists every name
that *is*, so a typo is loud rather than silently doing nothing. Every key a phase does not read
fails the run the same way an unread top-level key does.

Phases run in the order they are declared in `surface`, followed by one the engine always appends
last on your behalf: the package and type renames from `manifest.packageRenames` /
`manifest.typeRenames`. Every other phase's policy is written against the library's *original*
names, so the rename has to happen after everything else — which is also why it is data on the
manifest rather than a `surface` entry you could place elsewhere; naming it as one is refused, with a
message pointing back at `packageRenames`.

Most phases that retype or move declarations accept the same `scope` object, so learning it once
covers all of them:

```hocon
{ transform = "collections", scope { except = ["com.foo.Bridge"] } }   # everywhere except these
{ transform = "collections", scope { only   = ["com.foo.gl"] } }       # only these
# scope absent                                                        # the phase's own default
```

Both `except` and `only` at once is refused — there is no value that means both. Whether the default
(no `scope` at all) means "everywhere" or "nowhere" depends on what the phase *does*: a phase that
retypes existing declarations defaults to everywhere, since narrowing is an opt-out; a phase that
*adds* new declarations (a registry, a splice of hand-written members) defaults to nowhere, since an
unrestricted default would put a new declaration on every matching type in every port that merely
carries the phase.

## The phases

### Collections and calls

**`collections`** is the largest phase: it retypes JDK (or a nominated library) collection types and
rewrites the call sites that used to resolve against them. `retarget` maps a source type to a Scala
target that can stand wherever the source was used; `retargetRewrites` maps individual member calls
on a retargeted receiver to one of several rewrite shapes — a rename, a boolean-argument dispatch, a
constructor-to-factory-call rewrite, a structural for-each, a collect-into-builder pattern, a chained
call, a field read or write, or a free-form expression template with `$recv`/`$0`/`$T0` holes for the
receiver, arguments and type arguments. `retargetTypeArgs` handles a retarget that changes a type's
arity. `reifiedCarriers` and `reflectiveSinks` name third-party types whose type arguments must stay
in the original namespace because a library like Jackson reads them back at runtime.

```hocon
{ transform = "collections"
  retarget { "com.example.Bits" = "scala.collection.mutable.BitSet" }
  retargetRewrites {
    "com.example.Bits" {
      "get/1" = "apply"
      "set/1" = "addOne"
      "<init>/0" { companion = "scala.collection.mutable.BitSet", factory = "apply" }
    }
  }
}
```

A member overloaded at one arity needs a descriptor key instead — `"add/(float,float,float,float)"`
— which wins over a plain arity key at the same name.

**`type-redirect`** re-points every reference to one type at another, optionally renaming members
along the way:

```hocon
{ transform = "type-redirect"
  redirects {
    "com.example.Disposable" = { to = "java.lang.AutoCloseable", memberRenames { dispose = "close" } }
  }
}
```

An entry may carry a `scope { only = [...] }`: only declarations inside it are retyped. A call
whose receiver such a scoped redirect moved binds to the TARGET type's member, so a base module's
published rename of the source member does not reach it — only this entry's `memberRenames` do.
The target's arity is yours to declare: list a parenless target member in `externalParenless`
(`"scala.collection.mutable.BitSet#isEmpty"`).

**`class-table`** re-points one reflective name lookup — `Class.forName`-shaped calls — at an
explicit table you provide (`redirects { "a.B#forName" = "c.D#classFor" }`).

**`static-forwarder`** treats a wrapper type's static members as though they belonged to another
type's companion:

```hocon
{ transform = "static-forwarder"
  forwarders = [ { wrapper = "com.example.Utils", receiver = "com.example.Core", members = ["helper"] } ]
}
```

A forwarder entry with no `members` is refused outright — it can only ever be a mistake.

**`call-site-substitution`** replaces one resolved call with a Scala expression template naming the
call's own receiver and arguments (`{recv}`, `{arg0}` …), for the cases `method-body` below cannot
reach because there is no single declaration to rewrite the body of. A literal brace is `{{`/`}}`.
Each hole is spliced where the template names it, so a template that needs the argument before the
receiver binds the receiver first to keep java's evaluation order.

`{this}` names the instance of the nearest named class whose member encloses the call. Inside an
anonymous class it is that OUTER class's instance, emitted `Outer.this`; inside a lambda it is plain
`this`, which Scala does not rebind. A call in a `static` member, or in an anonymous class created
in one, has no such instance: it is refused, reported as a finding starting `{this} refused`, and
left as java wrote it. Never write a literal `this` in a template — inside an anonymous class it
would silently name the anonymous instance.

```hocon
{ transform = "call-site-substitution"
  calls { "com.example.Err#<init>(String)" = "com.example.Errors.invalid({arg0})" }
  scoped = [
    { call = "com.example.Err#<init>(String)", template = "com.example.Errors.io({arg0})",
      scope { only = ["com.example.io.Loader#load"] } }
    { call = "com.example.Bits#containsAll(Bits)",
      template = "{{ val bpThis = {recv}; {arg0}.subsetOf(bpThis) }}", scope { only = ["com.dep"] } }
  ]
}
```

A `scoped` entry rewrites only the calls whose enclosing declaration its scope admits. Several
entries may name one callee; at each call the most specific scope wins (a member over its type
over its package over the unscoped `calls` entry), and two equally specific entries with different
templates are refused and reported. A dependent module may rewrite calls of a base module's member
this way without editing the base's surface, as long as the scope stays outside the base's namespace.

**`method-body`** keeps a method's translated signature but replaces its body verbatim:

```hocon
{ transform = "method-body", bodies { "com.example.Grid#step()" = "{ tick += 1 }" } }
```

A manifest that needs two sets of bodies at two different points in the pipeline names the second
one with `group`: instances of the same group fold into one at the shared position, and a named
group keeps the place it was declared at. Omit it and the manifest has one shared instance — two
unnamed entries in one `surface` list are two instances of the same phase.

### Renaming, arity and visibility

**`member-rename`** renames a member across its whole override family — every implementation and
override, not just the one you name:

```hocon
{ transform = "member-rename", renames { "com.example.VisWindow#close" = "closeWindow" } }
```

**`nullary-arity`** drops the empty parameter list from a getter-like method, mints scoped to
*nothing* by default (adding an arity where java had one is a change to the surface, so it needs an
explicit `scope`, or `force` naming exact members). **`visibility`** widens a member's visibility to
public where java declared it narrower (`widen = ["com.example.Pool#<init>(int)"]`). **`class-tag-params`**
turns a `Class<T>` parameter into a Scala `ClassTag[T]` context parameter for the members you name.

Each of these three, and `bean-properties` below, accepts `derive = true`: instead of (or beside)
your own list, the phase also reads a *reference* hand port declared under `manifest.parity` and
follows whatever spelling it finds there — useful when you are mechanizing a library that already has
a trusted hand-written translation to match.

Once the derived policy is stable, you can freeze it into a committed file instead of re-extracting
the reference tree on every build. Set `manifest.frozenDerivedPolicy` to a path pointing at a TSV
file in the same format `derived-policy.tsv` uses (copy it from the run's report directory):

```hocon
manifest {
  frozenDerivedPolicy = "sge-port/derived-policy.tsv"
}
```

With this key set, phases that `derive = true` read their policy from the file and no `parity`
reference tree is needed. If you keep `parity` alongside `frozenDerivedPolicy`, the run derives from
the reference as before and fails if the result disagrees with the frozen file — a guard against the
file going stale.

**`bean-properties`** turns a Java getter/setter pair into a Scala `var`/`val` property:

```hocon
{ transform = "bean-properties", pairs { "com.example.Sprite#width" = "getWidth/setWidth" } }
```

### Public fields, threading and statics

**`public-field-accessors`** adds a `getX`/`setX` pair beside a public field for the declarations you
scope it to — for a reflective framework (a bean-property library, a serializer) that needs one even
though Scala emits the field itself as a private JVM field.

**`globals-to-implicits`** threads what was global mutable state through a context parameter instead.
Its `holders` list is the most involved shape in the engine — see the class-level scaladoc on
`GlobalsToImplicitsTransform` in the engine sources for the full grammar (`context.inject`/`mint`,
`members`, `attach`, `sites`) before reaching for it; it exists for a library whose design leans on
singletons a mechanical port cannot leave in place.

**`thread-confined-statics`** turns a `static final` scratch field — the kind every thread on a
single-threaded platform was sharing as an implicit convention — into a companion method backed by a
`ThreadLocal`, so the port stays correct once it runs somewhere the JVM's single-thread convention no
longer holds:

```hocon
{ transform = "thread-confined-statics", fields = ["com.example.Pool#scratch"] }
```

### Shapes the frontend cannot see on its own

**`class-to-trait`** rewrites a nominated abstract class into a trait — constructors removed, mapped
constructor parameters become abstract members, every direct subclass gains an `override val`. It is
usually paired with dropping the original type and injecting a hand-written trait shaped to match:

```hocon
manifest {
  dropTypes = ["com.example.Pool"]
  inject    = ["overrides/mylib/Pool.scala"]
  surface = [
    { transform = "class-to-trait"
      specs { "com.example.Pool" { params = [ { index = 0, name = "capacity" } ] } } }
  ]
}
```

**`registry`** turns reflective instantiation (`Class.newInstance`-shaped construction from a runtime
value) into an explicit, `Class`-keyed registry the port mints at a location you name — either a new
top-level object or a member on a type the port already emits. The `miss` key decides what an
unregistered key answers: `"null"`, `"jvm-reflect"` (JVM reflection, counted on non-JVM targets),
`{ throw = "fqn", message = "…" }`, or `{ delegate = "fqn" }` (calls a consumer-named method with
the `Class` value — the cross-platform escape hatch for ports supplying a per-platform fallback).

**`type-class-array`** gives an array whose element type is a type parameter a witness value to
allocate, copy and clear through, closing the gap left by Java's implicit `Object` bound on generic
arrays.

**`discriminated-union`** turns a literal-discriminated union of object types into a sealed trait with
case classes and rewrites narrowing sites into `match` expressions.

### Structural, with no policy at all

**`mutable-params`** gives a reassigned method parameter a local `var`, and **`panama-ffi`** threads
Java's foreign-function bindings through Scala's `java.lang.foreign` equivalents. Neither takes any
configuration — they are fact-of-the-language phases that exist as `surface` entries only so a conf
can put them in the pipeline at all. **`test-framework`** turns JUnit test classes into MUnit suites
structurally (not by renaming annotations): each `@Test` method, `@Before` and expected-exception
declaration is reproduced as the equivalent MUnit shape.

```hocon
{ transform = "test-framework", suite = "munit.FunSuite", testMember = "test" }
```

JUnit 4's `@RunWith(Parameterized.class)` is translated: the `@Parameters` data method stays,
test methods become `def`s, and one registration loop iterates the data rows at suite construction
time, registering each test per row with the name pattern (`{index}`, `{0}`, `{1}`, ...) JUnit
uses. Both constructor injection (a constructor taking the row elements) and field injection
(`@Parameter`-annotated public fields) are supported. Other `@RunWith` runners (Suite, Enclosed)
remain refused and counted.

### Retyping a primitive into a distinct type

**`primitive-to-opaque`** turns every occurrence of a primitive that is being used as a distinct
concept — an entity id stored as `int`, say — into an opaque type with its own name:

```hocon
{ transform = "primitive-to-opaque"
  fqn        = "mylib.EntityId"
  underlying = "Int"
  hints      = ["com.example.World#createEntity"]
  extraHints = []
  carriers   = []
}
```

`fqn` names the object the phase mints (`EntityId.T`, with `apply`/`unwrap`); `hints` is the seed set
you already know about, `extraHints` is where you add more once a compile error tells you a slot was
missed — both are exact fully-qualified names, and both are read the same way. `carriers` names
one-type-parameter wrapper types (a nullability wrapper, for instance) whose element may be the
primitive, so a value typed `Carrier[Int]` retypes to `Carrier[EntityId]` one level deep.
`derive = true` also seeds from wherever a reference hand port spells the same slot at an opaque type.

### `nullability` — moving a nullability fact into the type

```hocon
{ transform = "nullability"
  annotations     = ["javax.annotation.Nullable"]
  target          = "option"        # union (default) | named | option
  nullableMembers = ["com.example.Cache#find"]
}
```

`target = "union"` emits `T | Null`; `"option"` emits `scala.Option`; `"named"` targets a wrapper type
you name with `wrapper = "…"` — required only for that target, since the engine ships no default of
its own (different hand ports of the same ecosystem have chosen different shapes, so it has no
standing to pick one for you). `nullableMembers` treats an exact member as nullable even though
nothing in the Java source annotates it.

## Writing a library-specific rule

When a fix is genuinely the third kind — knowledge that applies to exactly one library — write a
`Phase` and register it as a `TransformFactory`, both in your own repository. The corpus's own
worked example (`balticporter.corpus.libgdx.GdxSharedIteratorRule`) exists to demonstrate the shape:
libGDX's `Array.iterator()` returns a *cached* iterator that resets when nested, so two nested loops
over the same collection silently terminate the outer one early — an allocation quirk of one
library, not a fact about Java.

### The phase

```scala
final class MyLibRule extends Phase:
  def name: String = "mylib-thing"   // a report identity; any characters allowed

  override def transformTerm(t: Term)(using Program): Term = t   // pick the hooks you need
```

`Phase` gives you the same hooks the engine's own phases use
(`transformClassDef`, `transformDefDef`, `transformApply`, …, each defaulting to identity), plus
`run(program)` for full control when the rule is a whole-program analysis rather than a per-node
rewrite, and `record(decision)` to leave provenance behind. Two things every rule owes:

- **Walk with the standard traversal**, never a private recursion — `StandardTraversal.mapClassDef` /
  `scanTerm` are the entry points a `run` override should use. A hand-rolled walk that misses one node
  kind silently reports zero hazards from a program that has them.
- **Register a check even when it finds nothing**, via `CheckReport.record(name, findings)`
  unconditionally, so a report can tell "found nothing" apart from "never ran".

If the rule *retypes* declarations, it also changes what a dependent module compiles against —
implement `SurfacePolicy` (a stable, order-independent string describing the rule's policy) so two
modules' instances of it can be compared; see
[Multi-module ports](multi-module-ports.md).

### Getting it into a `.conf`

Register a `TransformFactory` and a `META-INF/services` line, both compiled by your own build:

```scala
final class MyLibFactory extends TransformFactory:
  def name = "mylib-thing"                          // stable, kebab-case — this is published API
  def fromConfig(config: ConfigView): Phase = new MyLibRule
```

```
# src/main/resources/META-INF/services/balticporter.tir.TransformFactory
com.example.port.MyLibFactory
```

```hocon
manifest { surface = [ { transform = "mylib-thing" } ] }
```

`fromConfig` must throw `ConfigError` for anything it cannot honour — a key it silently ignores would
be exactly the no-op bug the engine refuses everywhere else. `ConfigView` gives you `string`, `int`,
`bool`, `strings`, `stringMap`, `child`, `children`, `keys`, plus `requireString`, `requireChild` and
`enumerated`; every key an accessor reads is recorded automatically, so you never write your own
unknown-key check — an unread key fails the load exactly as it does for the engine's own phases.
`TransformFactory.scopeOf(config)` gives you the same `scope { }` grammar the built-in phases use, for
free, if your rule needs one.

Registration is a `META-INF/services` line naming a class with a public no-argument constructor —
which is why a factory is a `final class`, not a Scala `object` (an object's constructor is private,
and `ServiceLoader` cannot call it).

### Testing it

`balticporter.testkit.PortSuite` parses a Java snippet, runs your phases and emits, with no engine
internals in sight:

```scala
class MyLibRuleSpec extends PortSuite:
  test("the hazard is reported") {
    val r = new MyLibRule
    port("""class Scene { … }""", r)
    assertEquals(r.findings.size, 1)
  }

  test("a shape that is not the hazard is not reported") {
    val r = new MyLibRule
    port("""class Scene { … }""", r)
    assertEquals(r.findings, Nil)
  }
```

`port(java, phases*)` returns a `Ported` carrying `before`/`after` (the program on each side of the
pipeline), `out` (the emitted Scala) and helpers: `assertEmits`/`assertNotEmits`/`assertEmitsMatch`
read the emitted text, and `assertVacated(p, fullName)` checks the stronger fact that a type has no
*usages* left anywhere in the program, which is a different and better guarantee than its name simply
not appearing in the output. Include a negative test alongside every positive one — a rule that has
never been shown to *not* fire is not known to work.

## When config is not enough at all

A hand-written `PortRun(...)` (rather than a `.conf`) is the full-strength door: it takes a
`Symbol => Boolean` predicate directly, a computed file list, or several source sets driven from one
program. Both doors construct the same values underneath; neither is a downgrade from the other —
reach for a `PortRun` when what you need is a value a configuration file cannot spell, not when a
factory would do.

## Pushing a rule down the scale

A library-specific rule that turns out useful for a *second* library was never really the third kind.
When that happens: if its library-specific part is a name list or a map, parameterize it and move it
into the engine as the second kind (with an empty parameter as the no-op, and a `scope` if it
retypes); if it turns out to be a fact about Java and Scala after all, it belongs in the engine
unconditionally. A rule that survives unchanged across several libraries is probably the first kind;
one that needs a new parameter for every library is correctly the second.
