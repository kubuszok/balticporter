# Reading the report

Every run writes a report describing what it did — not just whether it succeeded. This page is
about what that report contains and how to read it without guessing.

## Where it goes, and when it writes at all

A run's report lands at `<root>/port-report/<name>/run-latest/`, where `<root>` is the working
directory (or `-Dbalticporter.root=…`) and `<name>` is derived from the launching main class's simple
name — which is why [Configuring a port](configuring-a-port.md) recommends a small per-port `main`
rather than always going through `PortConfigMain`: two ports sharing one report directory would
overwrite each other's report on every run.

!!! note "The report is opt-in for anything that is not a plain `main`"

    Writing a report needs a *port identity* to name the directory after. Launched as a plain class
    (`sbt runMain MyLibMigrate`, or a `scala run` of the same), this happens automatically. Launched
    from inside a build tool's own JVM — an sbt task, a test runner — the engine cannot tell your
    port apart from the tool's own class, so nothing is written unless you either set
    `-Dbalticporter.root=…` on that JVM, or pass an explicit report directory. If your generated
    `.scala` files appear but `port-report/` does not, this is usually why.

A run ends with a line worth grepping for before trusting anything else you read:

```
[mylib] wrote 33 Scala files (0 dropped, 0 injected) -> …/target/port/src_managed/main/scala
```

If that line is missing, the run did not actually re-emit, and any report you are looking at is from
a previous run.

## What is in `run-latest/`

| file | what it holds |
|---|---|
| `findings.tsv` | one row per check finding: `id  check  kind  owner  path  line  detail` |
| `counts.tsv` | one line per check: `check` → how many findings, plus a row naming the upstream commit this run measured |
| `report.md` | the same findings, grouped by check, for a human to read — not diffed, may contain anything useful (the path root, the debug flags active during the run, the JVM it ran on) |
| `diff.txt` | this run's counts and findings against whatever is saved in `baseline/` (see below) |
| `subject.txt` | a one-line `before->after` summary, meant to be pasted into a commit message |
| `srcmap.tsv` | member → emitted line range → the Java file and line it came from |
| `members.tsv` | one digest per emitted member — lets you tell whether emitted text changed at all, before running any compiler |
| `decisions.tsv` | one row per declaration a non-mechanical decision changed, each carrying which of the three rule kinds it is |
| `port-map.tsv` | what this module's output looks like from a dependent's point of view (see [Multi-module ports](multi-module-ports.md)) |
| `dropped-types.tsv` | every dropped type, both its upstream and emitted names — the source a test failure is checked against before it is called a regression |
| `jvm.txt` | which JDK actually ran this migration |

`findings.tsv`'s `id` is a short hash of the check, its kind, the owner and the detail text — **not**
the line number, so an unrelated upstream edit that only shifts line numbers does not orphan an
entry between two runs.

Beside all of this, every non-mechanical decision the port made is also written directly into the
generated code:

```scala
/* porter: renamed-package reason=configured phase=package-rename
   key="com.example.mylib -> mylib" from=…MyClassTest to=mylib.MyClassTest */
```

`grep -rn '/\* porter:' <port>/src_managed` is the complete inventory of everything the port did that
was not a purely mechanical translation.

## The checks

A run always runs and records a fixed set of checks, plus a few more that switch on automatically
when a particular phase is in the pipeline (the collection checks appear only when `collections` is
enabled, for instance). What each headline number in `counts.tsv` means:

| check | the number is |
|---|---|
| `signature` | call sites that disagree with their own declaration — the port is internally inconsistent. Fix this before reading anything else; every other number is measured over a tree that is already wrong |
| `omissions` | constructs the internal model carried that emission simply lost. A green compile says nothing about these |
| `portability(all\|emitted\|injected)` | sites using a JVM-only JDK API — `all` everywhere, `emitted` in code this run wrote, `injected` in files it copied in from your `inject` list |
| `remediation` | the same portability findings grouped into "here is the one change that would fix these", ranked |
| `substitution(emitted)` | a type you dropped, but the emitter wrote a file for it anyway — check your drop key against what actually got generated |
| `substitution(dangling)` | a type you dropped, with neither an injected replacement nor its uses rewritten away — still referenced, and by how many files |
| `policy` | a policy entry you declared that never fired at all — see below |
| `manifest` | this module's declared base chain disagrees with it — see [Multi-module ports](multi-module-ports.md) |
| `port-map` | a reference into a base module the base's own published map says it does not emit |
| `porter-notes` | a decision with no `/* porter: … */` note beside the code, or a note with no decision behind it |
| `trivia` | a comment in the Java — a licence notice among them — that did not survive into the Scala |
| `collection-closure` / `collection-boundary` / `collection-retarget` | (only when `collections` is enabled) a mapped supertype with an unmapped subtype; a slot the collections phase's own scope left stranded; a value the JDK itself produces at a type the port retargeted |

Each is classified in its own output — you do not have to guess which repository a fix belongs in:

```
[mylib] OMISSIONS (emitted code silently loses these): 2
[mylib]   these are things the internal model carried and emission lost; a green compile says
          nothing about them. Fix the emitter, or accept the limit for now.
```

### `errors.tsv` — a compiler run, joined back to the Java

If you compile the emitted output yourself and feed the result back through the correlator, or your
own tooling reproduces the same join, you get `errors.tsv`: `lane  file  line  code  unit  member
javaPath  javaLine  message`. Every scalac error is sorted into one of four categories, named by the
`lane` column:

| category | meaning |
|---|---|
| `EngineGap` | anywhere in emitted code, located to the member and the Java line it came from — the diagnostic work is already done for you |
| `Approx` | at a region the engine itself marked approximate; a remediation is usually already attached |
| `Declared` | the engine's own declared limit — a `scala.compiletime.error` it wrote deliberately because it had no faithful translation and `preview = true` was set, rather than silently dropping the construct |
| `Unmapped` | in a file the source map does not cover at all — a file you injected, or a runtime dependency. **Not an engine gap** — it is a file you wrote or copied in, and the fix is there |

`Unmapped` is the one most often misread: a dropped type has no source-map entry by construction, so
an error inside the file you injected to replace it always lands here, never in `EngineGap`.

### "Refused and counted"

Where a faithful translation genuinely does not exist for a construct, the engine does not guess —
it counts the occurrence and says so, in the finding's own text, rather than emitting something that
merely compiles. This is deliberate: a wrong-but-compiling translation is worse than an honest gap,
because nothing downstream tells you it happened. Look for the check's own classification sentence
(as in the `omissions` example above) to see whether a given refusal is something the engine could
fix, something your configuration could resolve, or something genuinely specific to your library.

### `policy` — a rule you wrote that never fired

A misspelled *key* in your `.conf` is caught before any port runs at all (see
[Configuring a port](configuring-a-port.md)). A misspelled *value* — a `dropTypes` entry naming a
type that turns out not to exist, a rename key that no longer matches anything after an upstream
release — reaches the run and shows up as a `policy` finding instead:

```
[mylib] POLICY (declared keys that never fired): 1
```

Three flavours: the entry matched nothing at all (a typo, or policy left behind after an upstream
rename); the entry matched something, but the engine cannot prove the match is the one you meant; or
the entry was not shaped the way the phase expects, so it could never have matched anything. Every
one of these means the fix is in your manifest, never in the engine — the mechanism is doing exactly
what it was told, the policy is simply stale or wrong.

The opposite failure — a drop that *did* fire and left a dangling reference nothing replaced — is
`substitution(dangling)` above, not `policy`.

## `decisions.tsv` — the "why" behind one declaration

`srcmap.tsv` answers *which Java produced this Scala*. It cannot answer *why this type is absent, why
this package is not the upstream one, why this member came from a file you wrote by hand*. That is a
decision:

```
#kind  subjectFqn  reasonClass  reasonDetail  origin  line  detail
DroppedSuperCall  mylib.Grid  universal  ctor-funnel/super-args-dropped
    com/example/mylib/Grid.java  50  arguments=1; …
```

`reasonClass` is a constructor value, not free text, and it is always one of the same three kinds
this whole page keeps returning to:

- **`universal`** — a fact about Java and Scala the engine applies to every port.
- **`configured`** — the detail carries the manifest key you would edit, verbatim, to change the
  outcome.
- **`library-rule`** — produced by a phase your own repository registered.

One row is written per *declaration* whose emitted form a decision changed, never one per expression
— so `decisions.tsv` is a much smaller document than `findings.tsv`, and a good first thing to skim
after a run you have not seen before.

## Test results

If you correlate a test run's output the same way as a compile, you get `tests.tsv`
(`suite  test  status`) and `test-failures.tsv`, each failure anchored on the best available stack
frame in ported code: a frame inside the ported *library* itself is the strongest anchor (this is
where the behavioural differences catalogued in
[Java semantics Scala does not share](java-semantics.md) show up — none of them move a compile-error
count, only a test result); a frame in the *test* body only tells you where the failure was observed,
not what caused it; weaker fallbacks exist for a test runner that trims its own stack. A test that
stopped running partway through is reported as exactly that, never folded into "pass" or "fail".

A test whose failure traces back into a type you deliberately dropped is not a regression — it is
derived automatically from `dropped-types.tsv`, which is exactly why that file carries both the
upstream and the emitted name for every drop: so a failure can be matched against it without a
hand-maintained "ignore these" list quietly rotting out of date.

## Baselines

`run-latest/` is overwritten by every run. `baseline/` is not — it is whatever you choose to keep
around as "the last state I accepted", and `diff.txt`/`report.md` compare the two. There is nothing
special about promoting a run to a baseline beyond copying the position-independent files
(`findings.tsv`, `counts.tsv`, `members.tsv`, `tests.tsv`, `port-map.tsv`) into `baseline/` yourself;
`srcmap.tsv` is positional by construction and is never worth keeping as a baseline, and
`decisions.tsv` is deliberately not diffed either, since decisions are not a count you are trying to
hold steady, only a record of *why*.

Keeping a baseline under version control alongside the change that produced it is what turns "did
this get better or worse" into something you can answer by reading a diff instead of remembering.

## `bodies.tsv` — a TypeScript, JavaScript or Dart library ported beside a hand-written reference

A library that is not Java is ported differently: its hand-written reference Scala is the skeleton,
and a method body translated from the library's exported syntax trees replaces the reference body
wherever one exists and nothing refuses it. One call builds the translated bodies, and one call on
its result writes the output and the table:

```scala
import balticporter.frontend.ts.NonJavaBodies

NonJavaBodies.forLibrary("katex", referenceDir, rastDir) match
  case built: NonJavaBodies.Built =>
    val run = built.derive(outDir, reportDir) // every .scala under referenceDir -> the same path under outDir
    log.info(run.summary.line)                // translated 156/398 (39.2%); reference: no-translated-body=162, ...
    run.written
  case refused: NonJavaBodies.Refused =>
    sys.error(refused.message)                // names the registered libraries
```

`referenceDir` is the root of the hand-written Scala (package directories included or not — the
files are found either way), `rastDir` the root of that library's exported syntax trees. The
registered names are `dart-sass`, `katex`, `mermaid` and `terser`; any other name is refused, and
so is a directory that does not exist. `built.policy` is the library's policy (the text patterns
that cannot compile in the port, the reference spellings of upstream names), and
`built.bodiesFor("ssg/katex/Options.scala")` the translated bodies of one file, one entry per
OCCURRENCE of a name — the second `toMarkup` in the reference file takes the second translated
`toMarkup`, never the first again. `built.deriveFile(relativePath, source)` derives one file
without touching the disk.

`reportDir/bodies.tsv` has one row per method of the reference, in file then line order, with paths
relative to `referenceDir`:

```
file                      member      occurrence  source      why
ssg/katex/Namespace.scala beginGroup  0           translated
ssg/katex/Namespace.scala endGroup    0           reference   translator-refusal:DeleteExpression
```

(The file is tab-separated; the columns are aligned here for reading.)

`occurrence` counts the same-named methods of that file from 0; it is `-` for a method the reader
of the reference file cannot replace (anything but a plain or `private` `def` indented two spaces:
an `override def`, a method of a nested class). `why` is empty for a translated body and otherwise
one of:

| `why` | the reference body was kept because |
|---|---|
| `no-translated-body` | nothing of that name was translated for this file |
| `occurrence-out-of-range` | the name was translated fewer times than the reference declares it |
| `uncompilable-pattern:<pattern>` | the translated text contains a pattern the library's policy lists as not compilable in the port |
| `translator-refusal:<reason>` | the translator left a hole in the body (`<reason>` is its own, several joined by `+`), or the file's syntax tree is `missing-rast` / `unreadable-rast`, or the signature has no `=` to cut at (`unreadable-signature`) |
| `unclassified` | a translated body of that name EXISTS and no rule above turned it away — the method is one the reader cannot replace, or no table row connects this file to the syntax tree the body came from |

`unclassified` is the bucket to work on first: it is translated code that is not being used for a
reason nobody decided. `bodies-summary.txt` beside the table is the one line `run.summary.line`
returns; its counts add up to the table's rows.
