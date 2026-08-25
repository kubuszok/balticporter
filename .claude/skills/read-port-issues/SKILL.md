---
name: read-port-issues
description: Read the list of things a Baltic Porter run could NOT handle automatically — errors.tsv and its four lanes, findings.tsv per check, never-fired policy entries, decisions.tsv and the porter notes — and classify each as an engine bug, a configuration change, or a library-specific rule. Use when a port reports findings, fails to compile, or fails a test, before designing any fix.
---

# Reading what the port could not do

A run tells you what it could not translate mechanically. This skill is about finding that list,
reading it, and answering the one question every item needs an answer to before you touch anything.

**Not covered here:** running the port and reading the headline numbers (**`port-first-attempt`**);
making the change once you know what it is (**`customize-port`**); instrumenting a run to find out
WHERE something came from (**`debug-port`**).

## 0. Before you design any fix — read the record

**`ENGINE-LIMITS.md` is the measured record of what has already been tried and found worse.** Read
it when the first wall of errors appears, not at the end. Every entry carries its number and its
direction (`13 → 28`, `+277`, `inert`) and says which of `CLAUDE.md` §1's three kinds a fix would be.
Most of those entries cost a whole session of first-principles reasoning before a measurement settled
them; re-deriving one is waste. It is grouped by what you are doing when you hit the wall — generics
and raw types, constructors, `this` and anonymous classes, the JDK collection boundary, portability,
test porting.

And `CLAUDE.md` §3.5: **consult the reference port.** sge is a hand-written port of libGDX and ssg
contains hand-ported Java libraries. `grep -rn "<the construct>" ../sge/sge/src/main/scala/` before
concluding no faithful translation exists. Record whether they SOLVED it or merely SKIPPED it — a
construct that simply vanished from sge tells you nothing except that it is still open.

## 1. The one question every issue must be answered with

`CLAUDE.md` §1, and it decides **which repository the fix lives in**:

| kind | what it means | where the fix goes |
|---|---|---|
| **(a) universal** | the engine is wrong or incomplete for EVERY library | `api` / `engine` / `frontend-spoon`, unparameterised. Not your repo |
| **(b) configure an existing phase** | the mechanism exists; supply your library's values | your `.conf` / your `PortManifest` |
| **(c) library-specific rule** | knowledge that could only ever apply to this library | a `Phase` + `TransformFactory` in YOUR repository |

An error an agent cannot classify costs it a full investigation, so the engine classifies where it
can: `PortabilityCheck`, `RewriteTrace`, `PolicyReport`, every `decisions.tsv` row and every
`ENGINE-LIMITS.md` entry carry the classification. **Bare typer errors do not**, and they are the
bulk of a new library's first wall — which is what `errors.tsv` exists to reduce.

Reach for (c) only after establishing the mechanism genuinely cannot be shared. Most things that look
library-specific are a (b) with the policy inlined.

## 2. `errors.tsv` — compiler errors, located

**Never open an emitted file to work out which member an error is in.** The correlator already
joined the compiler's output back through `srcmap.tsv` to the member and the Java line. The measure
lane does this for you; run it yourself when you compiled by hand:

```
scala-cli compile --scala 3.8.4 --server=false --test <port>/src_managed/main/scala <port>/src_managed/test/scala 2>&1 \
  | sed 's/\x1b\[[0-9;]*m//g' > .balticporter/c.txt
just correlate port-report/<Port>/run-latest --scalac .balticporter/c.txt \
     --srcmap port-report/<Port>/run-latest/srcmap.tsv
```

`--test` is not optional: without it scala-cli READS the test tree and reports its warnings but not
its errors (CLAUDE.md §4.56's third occurrence — 0 errors without the flag, 6 with it, on one tree).

Which prints, and writes `run-latest/errors.tsv`:

```
scalac errors: 1  Approx=0  EngineGap=1  Unmapped=0  Declared=0

-- EngineGap — (a) engine gap — located to the member and the Java it came from
   E008 Not Found: sge.graphs.Path#getLength()  [space/earlygrey/simplegraphs/Path.java:48]
        value noSuchFieldAtAll is not a member of sge.graphs.Path[V]
```

Columns: `lane  file  line  code  unit  member  javaPath  javaLine  message`.

### The four lanes, and what each tells you to do

| lane | meaning | do |
|---|---|---|
| **`Approx`** | the error is at a region the engine MARKED as approximate | expected; a remediation is attached to the marker. Read `report.md`'s remediation section rather than the error |
| **`EngineGap`** | anywhere else in emitted code, located to a member and its Java | the diagnostic work is done. Classify it: usually **(a)**, sometimes a (b) you have not configured |
| **`Unmapped`** | in a file the source map does not cover — injected Scala, a runtime shim, a dependency | **NOT an engine gap.** The file is one YOU wrote or copied in; fix it there |
| **`Declared`** | the engine's OWN `scala.compiletime.error`, written under `preview = true` because it had no faithful Scala for the construct | the port is telling you what it could not do, at the place it could not do it. Counted separately so a preview run does not drown the real gaps |

`Unmapped` is the one most often misread. A dropped type has NO `srcmap` entry by construction — its
replacement is injected Scala the emitter never saw — so an error inside a replacement is
`Unmapped` and is yours.

## 3. `findings.tsv` — one line per check finding

Columns: `id  check  kind  owner  path  line  detail`. The `id` is a short hash of
`(kind, javaPath, ownerFullName, detailDigest)` and the **line number is carried but is not part of
it**, so a whitespace edit upstream does not orphan a baseline entry. `report.md` is the same content
grouped per check with the engine's classification paragraph at the head of each group; `diff.txt` is
the run against the committed baseline.

Per check, what a non-zero number is telling you:

- **`signature`** — a call site disagrees with its declaration. The port is internally inconsistent.
  Fix this before reading anything else; every other number is measured over a broken tree.
- **`omissions`** — the TIR carries a construct and EMISSION loses it. **§1(a) ENGINE.** These are
  invisible to a compile; the engine says so in the report. Either fix the emitter or record the
  limit in `ENGINE-LIMITS.md` with its number.
- **`portability(all|emitted|injected)`** — a JVM-only JDK API. **§1(b)/(c) PER-LIBRARY:** drop the
  type and inject a replacement, re-point it (`static-forwarder` / `class-table`), or accept it if
  this port targets the JVM only. `injected` non-zero means a replacement YOU shipped is unportable.
- **`remediation`** — the same portability sites grouped into "here is the seam that would fix these",
  ranked, with the sites that no single seam covers named as such.
- **`substitution(emitted)`** — a type you dropped that the emitter wrote a file for anyway (checked
  BEFORE the injection copy, so the file can only have come from the emitter).
  **`substitution(dangling)`** — a dropped type with neither an injected replacement nor its uses
  rewritten away, still referenced by N files (checked AFTER injection, over the final tree, and
  textually — injected sources never pass through the TIR). Both are §1(b)/(c): supply an `inject`
  replacement at that FQN, or plug in a rule that rewrites its uses away.
- **`policy`** — see §4.
- **`manifest`** — `ManifestAgreement` between this module and its base. A drop, rename or
  signature-affecting phase in one module and not the other; a phase name appearing twice with
  different policy; a dependent that declared no base at all (`NoBaseDeclared`).
- **`port-map`** — references into a BASE module that the base's published port map says are not in
  its output.
- **`porter-notes`** — a decision about an emitted subject with no note in the code, **or** a note
  with no decision behind it. Neither is visible to a compile, to any other count, or to a test.
- **`trivia`** — a comment in the Java that did not reach the Scala. **§1(a) ENGINE**, and not a
  formatting nicety: a LICENCE notice among these is a §4.57 obligation. Nothing else in the pipeline
  can fail when comment handling regresses — the output compiles perfectly with every comment gone.
- **`collection-closure` / `collection-boundary` / `collection-retarget`** — a mapped supertype with
  an unmapped subtype; a stranded slot the collections phase's scope created; a value the JDK
  PRODUCES at a type the port RETARGETS, which the boundary check cannot see (the retype moved the
  node type on both sides of that slot — `ENGINE-LIMITS.md` K14). Recorded only when that phase is
  in the pipeline.

## 4. Policy that never fired — the silent no-op

A misspelt policy entry is a rule that quietly did not run: the port emits, compiles, and keeps the
construct the policy was written to remove. Two different gates catch the two halves, and knowing
which is which saves an investigation:

- **A bad config KEY is refused at load**, before any port exists:
  `"1 key(s) nobody read: manifest.dropType"`. This is the config path's equivalent of
  `PolicyReport`. (A Scala `PortRun(...)` has no such gate — its keys are field names, checked by the
  compiler.)
- **A bad VALUE reaches the run** — a `dropTypes` entry naming a type that does not exist, a
  forwarder wrapper renamed upstream, a redirect key naming a member that is gone — and is reported
  by the **`policy`** check:

  ```
  [lib] POLICY (declared keys that never fired): 0
  ```

  Every such finding is `§1(b)`: the mechanism works, the policy is wrong, the fix is in your
  manifest and never in the engine. `PolicyIssue` distinguishes three: **`NeverMatched`** (matched
  nothing — a typo, or policy left behind by an upstream rename), **`Unverifiable`** (matched, but
  the engine cannot prove the rewrite is the intended one), **`Malformed`** (not in the shape the
  phase documents, so it could never match).

The opposite failure — a drop that FIRED and left a dangling reference — is `substitution(dangling)`.

## 5. `decisions.tsv` and the porter notes — the WHY channel

`srcmap.tsv` answers "which Java produced this Scala". It cannot answer "why is this type simply
absent, this package not the upstream one, this member from a hand-written file". That is a
`Decision`:

```
#kind  subjectFqn  reasonClass  reasonDetail  origin  line  detail
DroppedSuperCall  sge.graphs.DirectedGraph  universal  ctor-funnel/super-args-dropped(C3)
    space/earlygrey/simplegraphs/DirectedGraph.java  50  arguments=1; owner=…; why=…
```

`reasonClass` is the §1 classification and it is a CONSTRUCTOR PARAMETER, not free text —
`universal` / `configured` / `library-rule`. For a `configured` one the detail carries the manifest
key **verbatim**, which is the string you edit to change the outcome. One row per DECLARATION whose
emitted form the decision changed, never one per expression, and scoped to THIS module's
declarations.

```
just decision-counts     # rows by kind, every port — nothing else prints this size
```

The same facts are emitted **beside the code**, which is where the question is actually asked:

```
grep -rn '/\* porter:' <port>/src_managed
/* porter: renamed-package reason=configured phase=package-rename
   key="space.earlygrey.simplegraphs -> sge.graphs" from=…GraphBuilderTest to=sge.graphs.GraphBuilderTest */
```

The pairs carry the §1 classification FIRST, because which repository the fix lives in is your first
question. This grep is the complete inventory of non-mechanical translation in a port.

## 6. Test failures — the only evidence of behaviour

`CLAUDE.md` §4.4 lists Java forms that translate to valid Scala meaning something else. **None of
them moves a compile-error count.** The test lane is the only thing that sees them.

- **`tests.tsv`** — `suite  test  status`, no lines and no paths, so it is a promotable baseline.
- **`tests-diff.txt`** — the run against that baseline. `NEWLY FAILING` and `NEWLY SKIPPED` are the
  gates; a skip moves no pass count and no fail count, which is exactly why it needs its own.
- **`test-failures.tsv`** — each failure anchored on the first stack frame in ported code, plus how
  good that anchor is:
  - **`main-frame`** — the failure threw inside the ported LIBRARY. This is the §4.4 case and the
    anchor is the guilty member. Exact.
  - **`test-frame`** — the top ported frame is the test body; the anchor names WHERE THE FAILURE WAS
    OBSERVED, not what caused it.
  - **`assert-site` / `suite` / `none`** — progressively weaker fallbacks for a runner that trimmed
    the stack.
- **A test that stopped running is reported as such, never as a pass.**

**Deliberate failures are DERIVED, not listed.** A failure whose stack reaches a type in your
`dropTypes` fails because the port deliberately does not have that type; `run-latest/dropped-types.tsv`
carries both namespaces (upstream and emitted) and the correlator classifies from it. The artifact
records `expected#derived` against `expected#declared` so the two can never be confused.
`baseline/expected-failures.tsv` exists only for a failure no drop explains and is normally empty.

## 7. Then, and only then, fix something

Answer the (a)/(b)/(c) question for the issue, check `ENGINE-LIMITS.md` for whether it has been tried,
check the reference port for what a human wrote — and go to **`customize-port`**. Change one thing,
then measure (**`port-first-attempt`** §9).

If you cannot tell where a construct came from, that is a **`debug-port`** question, not a reading
question — a kill switch answers "is this phase even responsible" in one run.
