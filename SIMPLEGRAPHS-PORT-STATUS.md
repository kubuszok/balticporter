# simple-graphs — port status

The third corpus library, and the first that is neither libGDX nor a dependent of it.
`space.earlygrey.simplegraphs` → **`sge.graphs`**, MIT (see "Licence" below).

Reproduce every number here with:

```
bash scripts/sg_measure.sh
```

---

## 1. Measured state

| gate | value |
|---|---|
| compile errors (`scala-cli`, Scala 3.8.4) | **0** — and past `RefChecks`, see §2 |
| files emitted | 32 (29 upstream units; 0 dropped, 0 injected) |
| signature consistency | 0 |
| omissions | 2 |
| portability (Scala.js/Native) | 6 sites, all in emitted code |
| substitutions removed / dangling | 0 / 0 |
| manifest agreement | 0 |
| port map | 0 |
| **tests ported and run** | **16 of 16 PASSING** (7 files; 16 live `@Test`) |

`0` is a real 0 twice over: the compile count reached 0, `RefChecks` ran, the count **rose to 8**, and
those 8 were fixed — and then the SUITE ran and found two more defects that no count had moved. Read
§2 before treating any port's first 0 as a finished port.

---

## 2. What this library taught the engine

Every item below is an engine (`§1(a)`) fix, not per-library policy — which is what a third library is
for. Each is recorded with its number in `ENGINE-LIMITS.md` under the key given.

| key | the gap | cost when wrong |
|---|---|---|
| **K5** | `java.util.Collection` and `java.util.AbstractCollection` mapped to different families, breaking a subtype relation java guarantees | 13 of 20 errors |
| **K5** | the shim's abstract/concrete split, parameter types and type-parameter bounds must be `AbstractCollection`'s OWN, member for member | 1 → 8 at RefChecks |
| **K6** | a `java.util.stream` chain COLLAPSES; and a stream operation may only be rewritten when its receiver was already collapsed | 3 errors; 0 → 1 on libGDX when the second rule was missing |
| **K7** | a java enhanced-for binding may be declared at a SUPERTYPE, and the port dropped the declaration | 2 errors |
| **K8** | `Type::method` is a qualified name only when the method is STATIC | 2 errors |
| — | `java.util.Collections` / `Map.Entry` statics have no receiver, so no receiver-keyed rewrite sees them | 4 errors |
| — | java's collection COPY CONSTRUCTOR (`new ArrayList<>(c)`) is not scala's capacity constructor | 1 error |
| — | a mapping must PRESERVE the source library's subtype relations — `ArrayDeque <: Queue` in java, `Queue <: ArrayDeque` in scala | 2 errors |

The last one is the transferable rule of this port: **two of the three multi-error causes were a
mapping that broke a subtype relation the source depends on.** Check that property of any new type
mapping before checking anything else.

### New engine machinery, all §1(a)

- `balticporter.runtime.JavaCollection` — the third shim, and now the family's most detailed member.
- `balticporter.runtime.JavaCollections` — the FOURTH runtime type, mirroring `java.util.Collections`'
  statics plus the `Map.Entry` statics that follow from `Map.Entry → Tuple2`.
- `CollectionsTransform.coerce` — one seam for every shim-typed slot (argument, declaration,
  assignment), replacing an argument-only `wrapIterableArgs`.
- `CollectionsTransform.staticRewrite` — receiver-less JDK utilities, keyed `owner#name`.
- `CollectionsTransform.copyConstructor` — `new X<>(collection)` through the target's companion.
- `TirEmitter.widenedBinding` — the for-each binding alias.
- `SpoonTir`: a conditional's unchecked conversion is applied to its BRANCHES.

### No §1(c) rules

simple-graphs needed **zero** library-specific rules. Its manifest is a namespace claim, two universal
phases and a package rename — nothing else. That is the outcome the corpus procedure is aiming for.

---

## 3. Do NOT retry

| tried | measured | why |
|---|---|---|
| `java.util.Collection` → `mutable.Buffer` while `AbstractCollection` → the shim | 13 of 20 errors | java's abstract base IMPLEMENTS the interface; the two must share a family |
| mapping `Collection` to the shim while bridging ARGUMENTS only | libGDX test port 0 → 3 | a declared slot is an expected type exactly as a formal is (`BezierTest`) |
| guarding the scala-shaped rewrite table per-rewrite instead of blanket-refusing on a shim receiver | 0 → 2 | it had already failed twice; `add`→`+=` and `addAll`→`++=` survived the guard |
| appending `$e` to the ESCAPED for-each name | libGDX main 0 → 3 (E040) | `` `object`$e `` is not an identifier; escape the WHOLE name |
| rewriting `Stream#filter` on method name alone | libGDX test 0 → 1 | `"…".lines()` is a Stream with no collection behind it |
| leaving the collapsed stream node typed `Stream<E>` | `Found: Buffer[V] / Required: JavaCollection[V]` | a rewritten node must be typed as what it EMITS |
| `Collectors.toSet` / `toMap` / `unmodifiableList` mapped to something approximate | not attempted, deliberately | each needs a different target type, and both a copy and the identity compile while being wrong |
| `IO.copyFile` → `IO.write` in the vendoring generator, to fix a stale resource | reverted | the staleness was sbt's classloader-layer cache, not the mtime (`ENGINE-LIMITS` M5.5) |

---

## 4. Licence — a discrepancy worth keeping

Upstream ships **MIT** (`LICENSE`: "MIT License, Copyright (c) 2020 earlygrey"). The reference
hand-port's file headers say "Licensed under the ISC License". One of the two is wrong; since a port
is a derived work the upstream file is the authority, so this port states **MIT**. Recorded rather
than silently followed, because the reference port is otherwise this project's tie-breaker (§3.5).

---

## 5. What the SUITE found that no count did

Two defects, both in code that compiled cleanly, and neither reachable from the library's own
compile:

- **`AlgorithmPath` constructed its parent with another constructor's arguments.** Java's
  `AlgorithmPath(Node v) { super(v.getIndex() + 1, true); … }` and its no-arg sibling reach the same
  parent constructor with DIFFERENT arguments; scala allows only the primary to reach `super`, so the
  engine nominated one and silently dropped the other's. `findShortestPath` returned a path of size 0
  instead of 39. Fixed by `CtorFunnel.Plan.synthetic` — a primary whose parameters ARE the parent's,
  with every java constructor a secondary that computes its own arguments. It also EXPRESSES three
  super calls libGDX had been dropping (omissions 46 → 43).
- **An `asInstanceOf` that could never succeed.** `(Collection<V>) anArrayList` is valid java; this
  engine sends the two sides to unrelated families — `Collection` to the shim, `ArrayList` to
  `mutable.ArrayBuffer` — so the surviving cast throws `ClassCastException` while compiling
  perfectly. Now dropped, which turns a runtime failure into a compile error on the same line.

  **CORRECTED, and the correction is the more transferable half.** The first form of the rule asked
  whether the cast's SOURCE type was named `java.*`, and `java.lang.Object` is. `(Collection<V>) o`
  where `o` is `Object` is an ordinary DOWNCAST that this phase does not touch on the source side —
  at run time the value IS a shim, so with its target retyped the cast succeeds — and deleting it
  turned a CORRECT program into a wrong one, at three sites in libGDX's `Json` (`Json.java:557`,
  `:563`, `:1121`, all `(Collection) value` guarded by `instanceof Collection`). The test is now
  structural: drop the cast only when `remap`/`kindOf` say this phase retyped the source OUT of the
  shim family. Nothing measured it — every check on every port reported the same number, because the
  three members are inside a type libGDX's manifest drops. The generalised rule is in CLAUDE.md
  §4.56. Regression cover: `CollectionsTransformSpec`, both directions.

The engine REPORTED the first one: all five dropped `super(args)` were in `findings.tsv`, including
`AlgorithmPath.java:12`. It survived because nobody opened the report — §5.2 of an earlier draft of
this very document said the omissions were "unexamined". A finding nobody reads is a finding nobody
made.

## 6. Remaining work

1. **2 omissions and 6 portability sites** — baselined and stable, and now actually read: the
   omissions are `DirectedGraph`/`UndirectedGraph`, whose several roots reach different parent
   overloads (the shape `ENGINE-LIMITS` records as having no single-primary encoding). Neither is
   reachable from a passing test.
2. `Arrays.asList` (element form, the only form this suite uses) returns a mutable `Buffer` where
   java returns a FIXED-SIZE list. Permissive, so it cannot make a correct program incorrect, and
   written down in `JavaCollections` — which also records the form that is NOT permissive: java's
   `asList(arr)` over a caller-held array is a live view, a copy would silently detach it, and
   today that form fails to compile (the pack is emitted unspread) rather than silently copying.
3. Non-local returns: 14 sites emit `return` inside a `for`, which scala desugars to a closure. They
   still work in 3.8 (verified) but are deprecated — a forward-compatibility item, not a defect.
