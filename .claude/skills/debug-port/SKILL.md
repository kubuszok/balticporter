---
name: debug-port
description: Instrument a Baltic Porter run -- the debug flags through `just debug-set`/`debug-flags`/`debug-clear`, `skipPhases` as the is-this-phase-even-responsible kill switch, `just debug-emit` for one type's TIR and Scala around a phase, `just correlate` for a compile you ran by hand. Use when you need to know WHERE an emitted construct came from, not what it is.
---

# Debugging a porting run

Use this when the question is **"which code produced this?"** rather than "what does this finding
mean" (that is **`read-port-issues`**) or "what should it do instead" (**`customize-port`**).

**The governing rule is `CLAUDE.md` §4.6: a kill switch beats another condition.** When a
synthesized construct is wrong, establish which code produces it before touching the gate you
suspect.

## 1. The flags, and why they live in a FILE

```
just debug-flags [PORT]      WHICH layer defines each flag right now
just debug-set KEY VALUE     write one flag into .balticporter/debug.properties (the winning layer)
just debug-clear [KEY]       remove one flag, or ALL of them (no key = the file goes)
```

**A `-D` on your command line does not reach the migration, and neither does an environment
variable.** `sbt -client` talks to a long-running server, and it forks the migration with
`javaOptions` from `build.sbt`. Only a FILE crosses that boundary.

Resolution order, increasing precedence: **`<root>/.balticporter/run.properties` (written by a
porting run) -> `<root>/.balticporter/debug.properties` (hand-written; wins) -> a system property**.

| flag | does |
|---|---|
| `skipPhases=<name>,<name>` or `*` | omit those phases |
| `dumpTirBefore=<phase>` / `dumpTirAfter=<phase>` | print the TIR around a phase |
| `dumpOnly=<fqn>` | narrow either dump to one type |
| `tracePhases` | one line per phase: name, units, symbols, decisions so far |
| `traceNode=<Kind>` | `TirTrace.mint` prints constructing frames for a node kind |

**Clear a flag when you are done with it.** A leftover one moves no count, fails no check, and
quietly changes what every later run in that checkout emits.

## 2. The kill switch -- one run answers "is the pipeline even responsible"

```
just debug-set skipPhases '*'
# re-run the migration
just debug-clear
```

If the construct is still there with every phase skipped, no phase produced it -- it is the
frontend or the emitter.

## 3. One type, as TIR and as Scala, around a phase boundary

```
just debug-emit <JAVA-SOURCE-ROOT> <FQN> [PHASES] [FLAGS...]
```

Prints the TIR before and after each named phase, narrowed to `--fqn`, and the emitted Scala.
Useful extra flags: `--fast` (parse only included files), `--include <substr>`, `--canonical`
(no symbol ids, comparable across runs).

## 4. A compile or test run correlated to members

```
just correlate <out-dir> --scalac <log> --srcmap <srcmap.tsv>
```

Never open an emitted file to work out which member an error is in. `CorrelateMain` joins
compiler and test-runner output through `srcmap.tsv` and `members.tsv`.

## 5. Proving the tools themselves

```
just debug-selfcheck
```

Runs in seconds, no sbt, no ports. Covers `debug-set`/`debug-clear` idempotency and `correlate`
usage gate.
