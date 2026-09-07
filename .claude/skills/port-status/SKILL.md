---
name: port-status
description: Produce the plain-language status table of every sge/ssg module — what compiles, which tests compile and pass, which fail, and every declared exception or workaround — from the committed baselines.
---

# Port status table

Run `scripts/port-status.sh` (reads `port-report/*/baseline`, never a run in progress) and turn its
TSV into ONE table plus a bullet list. Plain technical English: no internal ids, no coined words.

## Columns

| module | port | code compiles | tests compiled | tests passing | not passing | last measured | conventions |

- **module**: the sge/ssg module the port stands for (`module=` in the port map: `sge`, `sge-l0`,
  `sge-ecs`, `ssg-md`, …). A `*Test*`/`*Differential*` report is the test half of the same module —
  fold it into the module's row. `DemoCheck` is not a module; it is the demo row (see below).
- **code compiles**: the `jvm`/`js`/`native` columns. A `-` means NO LANE, which is "not measured",
  never "0". Say so.
- **tests compiled**: the `tests` count, plus the `ref-suite` column: the hand port's OWN suite
  compiled against the emitted port (`suite:N` for lls). Non-zero there means the reference suite
  does not compile and that gate is informational — say it.
- **not passing**: every name in `failing` and `skipped-names`, plus `declared-failures` (the
  escape-hatch rows) and `declared-lost` (upstream tests the port does not run). Never fold
  these into "passing".
- **last measured**: the baseline's commit date. The frozen family is not re-measured; say when
  it last was.
- **conventions**: `live` rows carry the new conventions; `frozen` rows carry the OLD POLICY.

## Modules with no port

List them from `../sge/build.sbt` (`projectMatrix` definitions) minus the ports: today colorful,
freetype, controllers, tools, the Android platform module; the non-java modules are out of scope.

## What "old policy" means — write this out, never just the words

The frozen family (the old core port `sge` and every port declaring `base = "sge"`: ecs, ai,
graphs, jbump, noise, anim8, gltf, screens, vfx, textra, visui, visui-usl, and the ssg ports) was
emitted by `LibgdxCoreMigrate`, the FULL-POLICY core port of the parity campaign. Concretely:

1. It has no lls base. The twelve shared utilities are emitted a second time under `sge.utils`,
   not under `lowlevel`, so nothing in that family compiles together with the new core.
2. Its policy is the parity campaign's list applied all at once (bean pairs and targets, three
   opaque types, the context holder, class-to-trait pools, member renames, method-body
   substitutions, nullability, arity), decided against exact hand-port parity and the drop-in bar
   the maintainer has since retired.
3. None of the ladder's measured decisions reach it: the lls base and its namespaces, the demo-
   driven steps, the follow of a base's published spellings, or any engine fix landed since the
   freeze (2026-09-06). Its numbers are a regression reference, not a statement about the target.

The live line is lls → the new core (`sge-l0`) → the demo check; moving a dependent onto it is
what "rebuild on the new core" means in the to-do list.

## Workarounds to flag every time

Read them off the data, and name each: dropped types whose tests fail by derivation (`dropped-
types.tsv` in the run report; Json on the core), excluded suites (`excludedFiles` in the
migrator), adjusted demo copies (`ported/demo-check/ADJUSTMENTS.tsv`), escape-hatch rows,
declared-lost counts, suites run only in adapted form (lls's own suite), platforms without a
lane, reference suites that do not compile, and that sge's hand-written tests are not run against
any port. A workaround is reported as a workaround, never as done.

## The demo check

`just demo-check` compiles sge's demo SOURCES in place (`../sge/demos`, plus any adjusted copy in
`ported/demo-check/adjusted`) against `ported/sge-l0` in THIS build, JVM only, compile only. It
says "sge's own game code typechecks against the port"; it says nothing about running.
