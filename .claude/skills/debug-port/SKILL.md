---
name: debug-port
description: Instrument a Baltic Porter run — the §4.6 debug flags through `just debug-set`/`debug-flags`/`debug-clear`, `skipPhases` as the is-this-phase-even-responsible kill switch, `just debug-emit` for one type's TIR and Scala around a phase, `just correlate` for a compile you ran by hand, and `just members-unchanged` as the blast radius before any compile. Use when you need to know WHERE an emitted construct came from, not what it is.
---

# Debugging a porting run

Use this when the question is **"which code produced this?"** rather than "what does this finding
mean" (that is **`read-port-issues`**) or "what should it do instead" (**`customize-port`**).

**The governing rule is `CLAUDE.md` §4.6: a kill switch beats another condition.** When a
synthesized construct is wrong, establish which code produces it before touching the gate you
suspect. Three consecutive edits to one function measured no change at all before a kill switch
showed, in one run, that the construct came from the emitter instead.

## 1. The flags, and why they live in a FILE

```
just debug-flags [PORT]      WHICH layer defines each flag right now
just debug-set KEY VALUE     write one flag into .balticporter/debug.properties (the winning layer)
just debug-clear [KEY]       remove one flag, or ALL of them (no key = the file goes)
```

**A `-D` on your command line does not reach the migration, and neither does an environment
variable.** `sbt -client` talks to a long-running server, and it forks the migration with
`javaOptions` from `build.sbt`. Only a FILE crosses that boundary. The tool says so itself:

```
NOTE  a migration runs in a JVM FORKED from the sbt server, so it sees the two FILES plus
      build.sbt's javaOptions — never your shell's environment, never a -D on your command
      line (CLAUDE.md §4.6). The system-property layer above is THIS process's.
```

Resolution order, increasing precedence: **system property → `<root>/.balticporter/run.properties`
(written by the measure lanes) → `<root>/.balticporter/debug.properties` (hand-written, WINS)**.
`debug-set` edits only the last of those, is idempotent (`java.util.Properties` keeps the LAST
occurrence, so an appended duplicate would make the effective value depend on file order), and adds
the `balticporter.` prefix if you left it off.

`just debug-flags` prints all three layers, what each shadowed, and — with a `PORT` argument — what
that port's LAST RUN actually recorded, read out of its `report.md`. It also marks the two failures
nothing else can see:

```
EFFECTIVE — what a run started now would resolve:
  balticporter.skipPhase   = *      [debug.properties]   !! UNKNOWN KEY — nothing reads it
  balticporter.tracePhases = true   [debug.properties]
```

A misspelt flag is a flag that does nothing, and the run it was written for then looks entirely
normal.

**Clear a flag when you are done with it.** A leftover one moves no count, fails no check, and
quietly changes what every later run in that checkout emits. `just debug-clear` with no key removes
the file entirely, which is a state you can verify at a glance.

| flag | does |
|---|---|
| `skipPhases=<name>,<name>` or `*` | omit those phases |
| `dumpTirBefore=<phase>` / `dumpTirAfter=<phase>` | print the TIR around a phase |
| `dumpOnly=<fqn>` | narrow either dump to one type |
| `tracePhases` | one line per phase: name, units, symbols, decisions so far |
| `traceNode=<Kind>` | `TirTrace.mint` prints constructing frames for a node kind — no node gains a field |

`skipPhases` and the dump flags take a **`Phase.name`**, not the conf's factory name. `tracePhases`
tells you what those are for your port:

```
[balticporter] DEBUG FLAGS: tracePhases=true  (phases: java-collections->scala, reassigned-params->var, package-rename)
[balticporter] phase 'java-collections->scala': 29 units, 1167 symbols, decisions so far: 36
[balticporter] phase 'reassigned-params->var':  29 units, 1172 symbols, decisions so far: 41
[balticporter] phase 'package-rename':          29 units, 1172 symbols, decisions so far: 70
```

`balticporter.reportPathRoot` is in `run.properties` and is **not yours to set**: it anchors the
paths a finding's stable id is hashed from, and it must come from the PORT, not the operator. Set by
hand it makes every finding diff as removed-and-re-added against a baseline whose counts are
identical.

## 2. The kill switch — one run answers "is the pipeline even responsible"

```
just debug-set skipPhases '*'
sbt -client "corpus/runMain <your migration>"
just members-unchanged <Port>
just debug-clear
```

If the construct you are hunting is still there with every phase skipped, no phase produced it — it
is the frontend or the emitter, and editing a phase's gate would have measured nothing. Measured on
simple-graphs, this is what the two answers look like:

```
SimpleGraphsMigrate: 934 member(s) changed     # phases off — the pipeline IS doing the work
SimpleGraphsMigrate: 0 member(s) changed       # phases on  — output byte-for-byte at baseline
```

Narrow it the same way, one phase at a time: `just debug-set skipPhases package-rename` also moved
934 members on that port, which is how you learn which phase owns a shape.

**Note the trap this exists to close:** with the whole pipeline skipped, **every check count is
unchanged**. A count is not a witness here; the member digest is.

## 3. One type, as TIR and as Scala, around a phase boundary

```
just debug-emit <JAVA-SOURCE-ROOT> <FQN> [PHASES] [FLAGS…]

just debug-emit ../sge/original-src/simple-graphs/src/main/java \
     space.earlygrey.simplegraphs.Path collections --fast
```

Prints the TIR before and after each named phase, narrowed to `--fqn`, and (with `--scala`, which the
recipe passes) the emitted Scala. Useful extra flags: `--fast` (parse ONLY the included files —
seconds instead of minutes on a large library, at the cost of resolution fidelity), `--include
<substr>` (narrow what is CONVERTED), `--canonical` (no symbol ids, no origins — the digest input, so
two runs are comparable), `--classpath`, `--lenient`.

**`--phases` names a phase exactly as a port `.conf` names it** — `collections`, `mutable-params`,
`panama-ffi`, … — because it resolves through the same `TransformFactory` SPI the conf's front door
uses (`TransformRegistry.discover()`), not a list of its own. Whatever is on the classpath is
available, a consumer's §1(c) rule included; run with a name that does not exist to have the list
printed. There is no second spelling to learn, which there was: the private registry this replaced
said `panama` while every `.conf` says `panama-ffi`.

A phase that takes POLICY is refused here, with its own factory's message and a pointer to `PortRun`.
That is the one honest limit and it is by design:

- **What it prints is the pipeline's view of one type, never a reproduction of a port's emitted
  file.** No substitution, no injection, no package rename, no provenance header — that is
  `PortRun`'s job. Do not diff its output against `src_managed`. Giving this tool a port `.conf`
  would make it a second assembly path, free to drift from the one that emits your port.

## 4. A compile or a test run you did by hand

**Never open an emitted file to work out which member an error is in** (`CLAUDE.md` §5.1). The
measure lanes correlate for you; this is the same command for a compile you ran yourself:

```
scala-cli compile --scala 3.8.4 --server=false <port>/src_managed/main/scala 2>&1 \
  | sed 's/\x1b\[[0-9;]*m//g' > .balticporter/c.txt

just correlate port-report/<Port>/run-latest --scalac .balticporter/c.txt \
     --srcmap port-report/<Port>/run-latest/srcmap.tsv
```

```
scalac errors: 1  Approx=0  EngineGap=1  Unmapped=0  Declared=0

-- EngineGap — (a) engine gap — located to the member and the Java it came from
   E008 Not Found: sge.graphs.Path#getLength()  [space/earlygrey/simplegraphs/Path.java:48]
```

`--tests <file>` does the same for a test-runner log; `--srcmap [scope=]<file>` is repeatable and you
pass the BASE port's map too when the port is a dependent — a stack that reaches the base is exactly
what a dependent's failure looks like. `--baseline` defaults to `<out>/../baseline`. The recipe
absolutises every path argument for you, which matters because `runMain` is forked and its working
directory is the subproject. Called with no options it prints usage and exits 2 rather than
silently doing nothing. The lanes for the four ports are still the right way to produce these
numbers — see **`port-first-attempt`**; this is for the one-off.

The lanes' meaning of each lane name is in **`read-port-issues`** §2.

## 5. The blast radius, before any compile

```
just members-unchanged [PORT]
```

`members.tsv` is one digest per emitted member. Identical files mean the output is byte-for-byte
unchanged — a stronger revert check than any count, and available before a compiler runs. A missing
input is FATAL rather than a clean report: a named port with no run, a run with no baseline, and an
empty sweep all exit non-zero and say which state they are in. (That gate exists because it once
exited 0 on a comparison that never happened.)

```
just decision-counts
```

`decisions.tsv` rows by kind, per port — the size of the non-mechanical translation, which nothing
else prints because the decision log is deliberately not baselined.

## 6. Proving the tools themselves

```
just debug-selfcheck
```

Runs in seconds, no sbt, no ports, no network. It covers the half a Scala spec cannot — the shell:
`debug-set` appending a duplicate instead of replacing it, `debug-clear` leaving a header that reads
as configuration, `members-unchanged` exiting 0 on an input that does not exist. The engine-side
halves (precedence, dump boundaries, emitted text) are covered by `DebugFlagsSpec`,
`DebugFlagsMainSpec`, `PipelineDebugSpec` and `DebugEmitSpec`.

## 7. Housekeeping

Scratch captures, research notes and hand-run logs go under **`.balticporter/`**, which is gitignored
and which the lanes already use. Nothing else in the repository is a valid home for one, and a
research file is never committed (`CLAUDE.md` §3.7).

And before you finish: `just debug-clear`, then re-run the port, then `just members-unchanged` to
prove you left the tree where you found it.
