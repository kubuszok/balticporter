---
name: port-status
description: Produce the plain-language status table of every sge/ssg module — what compiles, which tests compile and pass, which fail, and every declared exception or workaround — from the committed baselines.
---

# Port status table

Run `scripts/port-status.sh` (reads `port-report/*/baseline`, never a run in progress) and turn its
TSV into ONE table plus a bullet list. Plain technical English: no internal ids, no coined words.

## Columns

| module | port | code compiles | tests compiled | tests passing | not passing | last measured |

- **module**: the sge/ssg module the port stands for (`module=` in the port map: `sge`, `sge-ecs`,
  `ssg-md`, …). A `*Test*`/`*Differential*` report is the test half of the same module — fold it
  into the module's row.
- **code compiles**: the `jvm`/`js`/`native` columns. A `-` means NO LANE, which is "not measured",
  never "0". Say so.
- **tests compiled**: the `tests` count, plus the `ref-suite` column: the hand port's OWN suite
  compiled against the emitted port. Non-zero there means the reference suite does not compile and
  that gate is informational — say it.
- **not passing**: every name in `failing` and `skipped-names`, plus `declared-failures` (the
  escape-hatch rows) and `declared-lost` (upstream tests the port does not run). Never fold
  these into "passing".
- **last measured**: the baseline's commit date — when that lane was last promoted with
  `just baseline-accept`.

## What these baselines are — write this out, never just the words

lls and sge own their porting policy (`lls-port/` and `sge-port/` in their own repositories) and
measure it in their own CI; this repository carries no baseline for either consumer's port. What
`port-report/` holds is the CORPUS: the libGDX core port `LibgdxCoreMigrate` and its test port,
the extension ports declaring `base = "sge"` (ecs, ai, graphs, jbump, noise, anim8, gltf, screens,
vfx, textra, visui, visui-usl) and the ssg ports (liqp, flexmark). They exist to measure the
ENGINE — a lane row that moves attributes an engine change to a member — and say nothing about
what a consumer ships. A question about a consumer's state is answered from that consumer's own CI
and tracker, never from a table here.

## Modules with no port

List them from `../sge/build.sbt` (`projectMatrix` definitions) minus the ports: today colorful,
freetype, controllers, tools, the Android platform module; the non-java modules are out of scope.

## Workarounds to flag every time

Read them off the data, and name each: dropped types whose tests fail by derivation (`dropped-
types.tsv` in the run report), excluded suites (`excludedFiles` in the migrator), escape-hatch
rows, declared-lost counts, platforms without a lane, reference suites that do not compile, and
that sge's hand-written tests are not run against any port here. A workaround is reported as a
workaround, never as done.
