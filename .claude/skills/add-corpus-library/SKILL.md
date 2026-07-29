---
name: add-corpus-library
description: Add a new Java library to the Baltic Porter corpus — port it, make it compile, run its migrated tests, and classify every specialisation it needed as universal / parameterised / library-specific. Use when asked to bring a new library into the corpus or to widen the port to another module.
---

# Adding a library to the corpus

Until Baltic Porter is published and each library gets its own porter repository, new libraries are
added to the **corpus** (`corpus-tests/`). Read `CLAUDE.md` §1 before starting — the classification
it defines is the point of the exercise, not a formality.

Each library added should move engine rules **from (c) library-specific → (b) parameterised → (a)
universal**. A library that lands with a pile of new (c) rules has been ported but has not improved
the framework.

## 1. Scope it before porting it

Survey the module tree and write down, per module, the file count and whether it is in scope.
Platform backends, authoring tools and demo apps are usually **not** — sge targets Scala Native and
Scala.js, so a GWT or Android backend is dead weight. Say so explicitly rather than silently
skipping it.

Find the **tests** early and count `@Test` methods and assertions. That number is the only
behavioural evidence the port will ever have, and it decides whether the library is worth adding.

## 2. Write the migration program

One `object <Lib>Migrate` in `corpus-tests/src/main/scala/balticporter/corpus/`, modelled on
`LibgdxCoreMigrate`. It owns **all** per-library policy:

- `Substitutions(dropTypes, dropMethods, inject)` — what not to emit and the Scala to inject instead
- the parameterised transforms, constructed with this library's values
- the injected replacement sources, under `corpus-tests/<lib>-overrides/`

Nothing library-specific goes into `core` / `frontend-spoon` / `scala-emit`. When you need a new
rule, decide its kind FIRST (`CLAUDE.md` §1):

- universal → engine, unparameterised
- same mechanics, different values → engine, **constructor parameters**; empty parameter = no-op
- only ever this library → a separate plugged-in rule in the migration program

## 3. Make it compile

Add a measurement script beside `scripts/gdx_measure.sh`: re-emit, then compile with
`scala-cli compile --scala 3.8.4 --server=false`, and count
`^-- (\[E[0-9]+\] )?.*Error` — coded AND bare, since the coded-only count silently undercounts.

**The moment the first wall of errors appears, read `ENGINE-LIMITS.md` — before designing any fix.**
It is the measured record of what has already been tried: raw types and wildcards, constructors,
`this` and anonymous classes, the JDK/Scala collection boundary, portability, test porting, and the
ways the measurement itself misleads. Every entry carries its number and its direction (`13 → 28`,
`+277`, `inert`) and says which of `CLAUDE.md` §1's three kinds a fix would be. Reading it at the end
is reading it too late — most of these cost a session each to re-derive. Read `CLAUDE.md` §3.5 in the
same breath and check the reference port for the construct.

Then work the count down. Discipline (`CLAUDE.md` §5): **change one thing, then measure**; record
regressions and their cause; state `before->after` in the commit subject; read the emitted output to
confirm a fix rather than trusting the number.

Expect the count to **rise** the first time it reaches 0 — that is `RefChecks` running for the first
time (`CLAUDE.md` §3), not a regression.

## 4. Test-compile, then RUN the tests

Compiling is not the gate. Port the library's test sources through the same pipeline, compile them,
and run them. Report pass/fail honestly with the output; a test that does not run is not a passing
test.

Test frameworks differ — JUnit 4 (`org.junit.Test`, `Assert.*`), JUnit 5, TestNG. Decide the target
framework once and record it in the migration program.

## 5. Add the checks the library needed

Every translation path added for this library gets a check at the same time (`CLAUDE.md` §3). Walk
the tree with `StandardTraversal`, never a private recursion. Then **negative-test the check**:
break something deliberately and confirm it reports. A check that has never failed is not known to
work.

## 6. Write it down

Create `<LIB>-PORT-STATUS.md` alongside `LIBGDX-PORT-STATUS.md`:

- measured state (errors, omissions, portability, signature consistency) with the command to
  reproduce each
- remaining work, highest value first, each item with the *shape* of its fix
- **Do NOT retry** — every measured failure, with its cost and cause
- which specialisations this library needed, classified (a)/(b)/(c), and which engine rules it
  generalised

Then split it: any dead end that is a fact about **Java, Scala 3, Spoon, dotty or the engine** goes
into `ENGINE-LIMITS.md` — with its number, its worked example and its (a)/(b)/(c) kind — because the
next library will be ported in a repository that never sees your status file (`CLAUDE.md` §3.6,
§4.45). Leave a one-line pointer where you lifted it; the measurement stays. If your library
confirmed, contradicted or generalised an existing entry, **say so in that entry** — a limit that
survives a second library is stronger evidence than the one that first recorded it.

## 7. Hand off to the Auditor

When the work is delivered, tell the user it is ready for the **porting-auditor** agent
(`.claude/agents/porting-auditor.md`, Fable 5). Do **not** launch it yourself — it is expensive and
the user runs it deliberately, once per delivered body of work.
