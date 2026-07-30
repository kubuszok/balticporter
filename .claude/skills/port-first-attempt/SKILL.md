---
name: port-first-attempt
description: Run a configured Baltic Porter port for the first time and read the result honestly — what the run writes, what the fifteen-plus checks mean, why a rising error count at the first zero is the gate telling the truth, why a compile proves nothing about behaviour, and what accepting a baseline commits you to. Use after `configure-port`, and every time you re-measure.
---

# The first run, and how to read it

You have a `.conf` (see **`configure-port`**) or a hand-written `PortRun(...)`. This skill is about
what happens when you run it and how to read the numbers without lying to yourself.

**Read first:** `CLAUDE.md` §3 (compiling is not the gate) and §5 (measurement discipline). Do not
paraphrase them from here — they are short and they are the standard.

**Not covered here:** the individual issues the run reports and what to do about each
(**`read-port-issues`**); changing what the port does (**`customize-port`**); instrumenting a run
(**`debug-port`**).

## 1. Run it

```
sbt -client "corpus/runMain balticporter.corpus.simplegraphs.SimpleGraphsMigrate"
```

or, for a conf with no `main` of its own:

```
sbt -client "corpus/runMain balticporter.runner.PortConfigMain path/to/main.conf"
```

**Use a per-port `main` for anything you will measure twice.** `CheckReport.dir` is derived from the
MAIN CLASS's simple name, so the `main` is the run's measurement identity: `port-report/<YourMain>/`.
Everything through `PortConfigMain` lands in `port-report/PortConfigMain/`, and two ports sharing one
report directory is two baselines overwriting each other.

A run ends with a line you should grep for before believing any number downstream:

```
[simple-graphs] wrote 33 Scala files (0 dropped, 0 injected) -> …/simplegraphs-core/src_managed/main/scala
```

If that line is absent the migration did not run, and every count you then read is the PREVIOUS
emit. Every measure lane aborts on exactly this (`grep -qE "wrote [0-9]+ Scala( test)? files"`).

## 2. What the run writes, without being asked

Into `port-report/<Main>/run-latest/`:

| file | |
|---|---|
| `findings.tsv` + `counts.tsv` + `report.md` + `diff.txt` | every check, sorted, path-relative, diffed against `baseline/` |
| `srcmap.tsv` | member → emitted line range → Java origin |
| `members.tsv` | one digest per emitted member — **the blast radius, before any compile** |
| `decisions.tsv` | one row per declaration a non-mechanical decision changed, each with its §1 classification |
| `port-map.tsv` | what a DEPENDENT of this module will read |
| `dropped-types.tsv` | upstream TAB emitted — from which deliberate test failures are DERIVED |
| `subject.txt` | the `before->after` fragment for your commit subject |

Plus `/* porter: … */` notes beside the code itself:
`grep -rn '/\* porter:' <port>/src_managed` is the complete inventory of what the port did
non-mechanically.

None of that needs configuring. If your numbers are not appearing, the run did not reach the
artifact layer — do not add a writer.

## 3. The checks — fifteen, not four

Every run prints, untruncated, **fifteen engine checks plus any check the port's own §1(c) rules
register** (libGDX's lanes show sixteen, adding `gdx-shared-iterator`). From a real
`counts.tsv`:

```
collection-boundary  collection-closure  manifest  omissions  policy  port-map
portability(all)  portability(emitted)  portability(injected)  porter-notes
remediation  signature  substitution(dangling)  substitution(emitted)  trivia
```

Twelve are required of every run; `porter-notes` records on every run; `collection-closure` and
`collection-boundary` record when `CollectionsTransform` is in the pipeline.
`PortRun.RequiredChecks` is asserted against what actually RECORDED, so a number that reaches stdout
and not `findings.tsv` fails the run — that guarantee exists because `LibgdxTestMigrate` once went
its whole life without calling `PortabilityCheck` at all.

What each headline number is:

| check | the number is |
|---|---|
| `signature` | call sites that disagree with their declaration. Non-zero = the port is internally inconsistent; fix before anything else |
| `omissions` | constructs the TIR carries and EMISSION loses. **§1(a) ENGINE.** A green compile says nothing about these |
| `portability(all\|emitted\|injected)` | sites on JVM-only JDK APIs — `all` counts everywhere, `emitted` in code this run wrote, `injected` in the replacements it copied in. **§1(b)/(c) PER-LIBRARY** |
| `remediation` | portability observations grouped into "here is the seam that would fix these", ranked |
| `substitution(emitted)` | a type you DROPPED that the emitter wrote a file for anyway |
| `substitution(dangling)` | a dropped type with neither an injected replacement nor its uses rewritten away |
| `policy` | declared policy keys that NEVER FIRED — a typo, or policy left behind by an upstream rename. Always §1(b) |
| `manifest` | `ManifestAgreement` between this module and its bases |
| `port-map` | references into a BASE module that its published port map says are not in its output |
| `porter-notes` | decisions with no note in the code, **and** notes with no decision behind them |
| `trivia` | comments in the Java that did not reach the Scala — a LICENCE among them. **§1(a) ENGINE** |
| `collection-closure` | a mapped supertype with an unmapped subtype |
| `collection-boundary` | stranded slots the collections phase's own scope created |

Each of those the engine classifies, it classifies IN THE OUTPUT. You do not have to guess:

```
[simple-graphs] OMISSIONS (emitted code silently loses these): 2
[simple-graphs]   §1(a) ENGINE: the TIR carries these constructs and emission loses them. A green
                  compile says nothing about them (CLAUDE.md §3). Fix in the engine, or record the
                  limit in ENGINE-LIMITS.md.
```

## 4. Four measurements that are NOT check counts

Each catches a class nothing else can see, so a measure lane prints them beside the checks:

- **`break_residue`** — untranslated `break`/`continue` jumps left in emitted code. Quoted in prose
  for a long time as "45, all switch-case" while nothing computed it; the real number was 55.
- **the TEST lane** — outcomes reconciled against the **emitted** test count, never a sum of markers.
- **`members.tsv`** — which members' emitted text moved, available BEFORE any compile.
- **`decisions.tsv` + the porter notes** — how many non-mechanical decisions the port made, by kind
  (`just decision-counts`), and whether every one reached the code (`porter-notes`).

## 5. Compiling is not the gate — three consequences you will meet in order

**(a) The compile-error count is typer-only.** dotty's `Phase.isRunnable` is
`!ctx.reporter.hasErrors`, so ONE typer error skips `RefChecks` for the whole program. Missing
`override`, unimplemented members and variance violations are unmeasured until the count reaches 0 —
**and then the number will RISE.** That is the gate beginning to tell the truth, not a regression.
Expect it, and say so in the commit subject rather than reverting.

**(b) A green compile says nothing about behaviour.** Four silent correctness defects in libGDX core
all compiled cleanly: dropped `static { }` blocks, dropped `super(args)`, dropped anonymous-class
bodies (156 sites, every button silently doing nothing), and the typer blind spot itself. Not one of
the Java forms in `CLAUDE.md` §4.4 moves a compile-error count. **Prefer running ported tests over
any number of further compile fixes.**

**(c) Read the emitted output, not just the count, when confirming a fix.**

## 6. The lane — one command that does all of it

In this repository the lanes live in the root `Justfile`:

```
just sg-measure          # simple-graphs + its suite
just gdx-measure         # libGDX core
just gdx-test-measure    # libGDX's own suite — … then RUN it
just ashley-measure      # a DEPENDENT port, compiled WITH libGDX core
just measure-all         # the four, SERIALLY, stopping at the first failure
```

**Serially, in dependency order, never in parallel** — each re-emits into `src_managed/`, so a
dependent lane compiles against what the base lane just wrote.

In another repository, build the equivalent: the shape and the reason for each step are in
**`add-corpus-library`** §3, and the shared mechanism is `scripts/_lib.sh`. Two things that are not
style: **never add `set -e`** (`grep -c` exits 1 when it counts zero, and zero errors is the success
case), and strip ANSI before counting errors (dropped once, and every line then began with an escape,
reporting 0 errors for a port that had 20).

A green lane looks like this, end to end — the numbers are simple-graphs':

```
-- test discovery --
@Test in Java: 16   discoverable in emitted Scala: 16 (munit 16 + junit 0)

break residue: 0 × untranslated jump(s) in emitted code
-- compile --
TOTAL ERRORS: 0  (coded 0 + bare 0)

-- run --
passing: 16   failing: 0   not run (skipped/ignored): 0   [outcomes 16 of 16 emitted]

units in source map: 36   members: 508
members whose EMITTED TEXT changed since the baseline: 0
tests: 16  passing=16  failing=0  (expected 0, unexpected 0)

==================================================================
HEADLINE  errors=0 | collection-boundary 0, …, trivia 1 | tests 16 passing, 0 failing
==================================================================
```

Read the guards, not only the headline:

- **`@Test in Java` vs `discoverable in emitted Scala`.** A suite with no discoverable tests runs
  ZERO and reports SUCCESS. A mismatch is tests that would never run.
- **`outcomes N of M emitted`.** Reconciled against the emitted count, so a test with no recognised
  line is reported whatever the reason. **A skipped test is not a passing test**; a skip moves no
  pass count and no fail count, which is exactly why it has its own gate, and it is kept apart from
  `ignored` because an ignored test is a DECISION and a skipped one is PREVENTION.
- **`members whose EMITTED TEXT changed`.** See §7.
- **`!! NEWLY FAILING` / `!! NEWLY SKIPPED`** in the headline — the only signal this project has for
  the §4.4 defect class, which moves no compile-error count.

## 7. The blast radius, before any compile

```
just members-unchanged            # every port
just members-unchanged <Port>     # one, and a missing input is FATAL
```

Identical `members.tsv` files mean the emitted text is byte-for-byte unchanged. That is a stronger
revert check than any count, because **no check count moves for most transform regressions** — with
the whole pipeline skipped, every check count is unchanged and 934 members move. It exits non-zero
when anything moved, so it is usable as a gate.

## 8. Baselines — what accepting one commits you to

```
just baseline-list            # every port: baseline size and last run
just baseline-show   <Port>   # run-latest/report.md
just baseline-diff   <Port>   # run-latest/diff.txt — the run against the committed baseline
just baseline-accept <Port>   # promote run-latest to baseline
```

`run-latest/` is overwritten by every run and is gitignored. `baseline/` moves only when a human
accepts a step — golden-test discipline, and it is what makes "omissions 31->33" a fact rather than a
memory. Promotion copies only the DETERMINISTIC, position-free files: `findings.tsv`, `counts.tsv`,
`members.tsv`, `tests.tsv`, `port-map.tsv`. `srcmap.tsv` is positional by construction and is
deliberately not promoted; `decisions.tsv` is not promoted either, which is why `just decision-counts`
exists.

Accepting a baseline **says these numbers are the intended state.** If it contains failing tests that
no substitution explains, `baseline-accept` says so and names them — either they are regressions, or
they are decisions that belong in `baseline/expected-failures.tsv` with a reason someone can defend.
Deliberate failures whose stack reaches a DROPPED type are derived automatically from
`dropped-types.tsv`; a hand-maintained list is the thing that rots into "we always ignore those four"
and then hides a fifth.

**Commit `port-report/<Port>/baseline/` with the change that produced it.**

## 9. The discipline, in four lines

- **Change one thing, then measure.** Two changes measured together cost a full cycle to untangle
  and tell you nothing about either.
- **State counts as `before->after` in the commit subject.** `run-latest/subject.txt` is the
  fragment.
- **Record what regressed and why**, with its number, in that library's `PROGRESS.md` section under
  "Do NOT retry". A measured failure is a result.
- **Reproduce every number with the lane**, serially. A number produced by a hand-run command with a
  different `balticporter.reportPathRoot` diffs as removed-and-re-added against a baseline whose
  counts are identical.

When the first wall of errors appears, go to **`read-port-issues`** — and read `ENGINE-LIMITS.md`
BEFORE designing any fix.
