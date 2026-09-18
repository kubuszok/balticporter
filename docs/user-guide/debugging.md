# Debugging a port

Sometimes the question is not "what does this finding mean" (that is
[Reading the report](reading-the-report.md)) or "what should it do instead" (that is
[Customizing the translation](customizing-translation.md)), but simply **which code produced this?**
This page is about the flags that answer that question.

## Setting a flag

Every flag is a `balticporter.<name>` value, read in this order of increasing precedence:

1. `<root>/.balticporter/run.properties`
2. `<root>/.balticporter/debug.properties`
3. a JVM system property (`-Dbalticporter.<name>=…`)

`<root>` is `-Dbalticporter.root=…`, or the current working directory if that is not set. A system
property always wins over either file.

!!! warning "A forked JVM only sees the files"

    If you launch a port through a long-lived process that then forks a fresh JVM to run the
    migration — an sbt server driving `sbt --client`, for instance — a `-D` you pass to the outer
    process, or an environment variable, does not reach the forked JVM at all: neither is part of
    what gets forwarded. In that shape, write `.balticporter/debug.properties` instead, which the
    forked process reads from disk regardless of how it was launched. If you call
    `PortConfig.load(...).execute()` directly from your own `main`, a plain `-D` on that same JVM
    works exactly as expected — there is no fork in between.

Clear a flag when you are done with it. A flag left behind in `debug.properties` changes what every
subsequent run in that checkout emits, silently, with every check count unaffected.

## The flags

| flag | does |
|---|---|
| `balticporter.skipPhases=<name>,<name>` (or `*` for all) | omit those phases from the pipeline for this run |
| `balticporter.dumpTirBefore=<phase>` / `dumpTirAfter=<phase>` | print the internal model immediately before/after a named phase runs |
| `balticporter.dumpOnly=<fqn>` | narrow either dump above to one type — otherwise a whole-library dump is megabytes nobody reads |
| `balticporter.tracePhases` | print one line per phase as it runs: its name, how many units and symbols it saw, and how many decisions have accumulated so far |
| `balticporter.traceNode=<Kind>` (or `*` for every kind) | print the constructing stack frame for a node kind, at whichever call sites are wired to report it |

`skipPhases` and the two `dumpTir…` flags take a phase's own `Phase.name`, not the `.conf`'s
kebab-case `transform` name — set `balticporter.tracePhases=true` on a run over your own port to see
what those actually are for the phases your pipeline enables:

```
[balticporter] DEBUG FLAGS: tracePhases=true
[balticporter] phase 'java-collections->scala': 29 units, 1167 symbols, decisions so far: 36
[balticporter] phase 'reassigned-params->var':  29 units, 1172 symbols, decisions so far: 41
[balticporter] phase 'package-rename':          29 units, 1172 symbols, decisions so far: 70
```

A name in `skipPhases` that matches nothing currently in the pipeline is reported as a warning rather
than silently ignored, so a stale flag does not look like it worked when it did not.

!!! note "`traceNode` reports only where it is wired"

    Printing a construction site costs a call at the point a node is built, so `traceNode` only ever
    reports on the call sites someone has already instrumented — it is not a universal hook over
    every node the engine ever constructs. Treat a silent `traceNode` as "not wired here yet" rather
    than "nothing of that kind was built"; `tracePhases`, the kill switch below, and the two
    `dumpTir…` flags do not have this limitation.

## Naming the report explicitly

[Reading the report](reading-the-report.md) notes that a run only writes a report when it can name a
directory after a port identity, which it normally reads off the launching main class. Three more
flags exist for the cases where that guess is not what you want:

| flag | does |
|---|---|
| `balticporter.report=on` / `=off` | force the report on (even from inside a build tool's own JVM) or off entirely |
| `balticporter.reportDir=<path>` | write the report at this exact directory instead of the derived `port-report/<name>/` |
| `balticporter.reportPathRoot=<path>` | the source root paths in the report are made relative to — set this when a run's working directory is not the checkout root, so `srcmap.tsv` stays portable across machines |

`reportDir` alone is enough to make a report happen even when the launching class would otherwise be
mistaken for a build tool's own — supplying it is a statement that a report is wanted here.

## The kill switch: is the pipeline even responsible?

Before changing a phase's own logic, first establish whether that phase — or any phase at all —
actually produced the construct you are looking at. Skip everything, run, and compare:

```
balticporter.skipPhases=*
```

If the construct is still there with the whole pipeline switched off, no phase produced it — the
frontend or the emitter did, and editing a phase's condition would have changed nothing. Compare the
emitted member digests (`members.tsv` in the report — see
[Reading the report](reading-the-report.md)) between a run with the pipeline off and a normal run: if
they differ, the pipeline is doing real work; if they are identical, it is not touching that
declaration at all.

Narrow the same way, one phase at a time — `skipPhases=some-phase-name` alone — to find out which
single phase owns a given shape, rather than guessing from reading its source.

!!! note "A check count is not a witness here"

    With the whole pipeline skipped, every check still runs and every count still reports — usually
    unchanged, since the checks are asking questions about a program that itself did not change in
    the way the pipeline would have. Only the emitted member digest tells you whether anything was
    actually skipped.

## A worked walkthrough: which phase produced this?

Say a generated method still contains a raw `java.util.List` parameter you expected the `collections`
phase to have retyped. Work outward from "did the pipeline run at all" rather than staring at the
phase's own source:

1. **Rule out the pipeline entirely.** Set `balticporter.skipPhases=*`, run, and note the emitted
   member's digest in `members.tsv`. Then clear the flag and run again with the pipeline on. If the
   digest is identical either way, no phase touches that declaration — look at the frontend (is the
   type parsed at all?) or the emitter, not at `collections`.
2. **If the digest does differ**, narrow one phase at a time: `balticporter.skipPhases=collections`
   with everything else enabled. If the raw `java.util.List` reappears, `collections` was indeed the
   phase responsible for it in the normal run — the question becomes *why didn't its scope reach this
   declaration*, which is a [Customizing the translation](customizing-translation.md) question (check
   the `scope` you gave it, and whether the declaration's fully-qualified name is inside it).
3. **If it does not reappear**, some other phase is responsible, or the shape is coming from
   somewhere the collections phase's own scope cannot reach at all — an external, JDK-declared
   signature, for instance, which no port-side scope can retype.
4. **To see the value itself change**, rather than inferring it from a digest, dump the type around
   the phase boundary: `balticporter.dumpTirBefore=java-collections->scala` and
   `balticporter.dumpTirAfter=java-collections->scala`, narrowed with `dumpOnly=<the type's FQN>` so
   the output is one type's worth of text instead of the whole library's.

Clear every flag once you have your answer, and confirm the tree is otherwise unchanged with
`members.tsv` one more time (see below).

## Inspecting one type across a phase boundary

If your build exposes a way to run the engine over a single type — parsing only the java it needs and
printing the internal model, and optionally the emitted Scala, before and after one or more named
phases — that is exactly what `dumpTirBefore`/`dumpTirAfter` plus `dumpOnly` are for. What comes out
is the pipeline's view of *one type in isolation*: no injection, no package rename, no provenance
header, nothing a full `PortRun` also does. Do not compare it directly against what an actual port
wrote to `src_managed/` — it is a narrower question, answered faster.

## A compile or test log from outside the pipeline

If you already have a compiler's error log or a test runner's log for emitted code, and you want it
correlated back to the Java the same way a full run's report is, feed it through the same correlation
step the pipeline uses, pointing it at that run's `srcmap.tsv`. The output is the same `errors.tsv`
shape [Reading the report](reading-the-report.md) describes, with the same four categories
(`EngineGap`/`Approx`/`Declared`/`Unmapped`). Never work out which member an error belongs to by
opening the emitted file and reading it by hand — the source map already answers that question
exactly, and a hand-read guess is the more error-prone path.

## The blast radius, before any compile

`members.tsv` is one digest per emitted member. Two runs whose `members.tsv` are byte-identical
produced byte-identical output — a much stronger and much cheaper check than any compile, since it
needs no compiler at all. This is the right first check after any configuration change: did anything
move, and if so, how much of it.
