---
name: sbt2-client
description: Run sbt 2 the way this repository and its consumers (sge, ssg, lls) expect — `sbt --client` only, one client per server, `reload` before `publishLocal`, the action cache when it goes stale, JDK per repository, aliases in build.sbt not in YAML, long runs logged with tee. Use before ANY sbt invocation, and whenever sbt behaves strangely (stale output, a wrong version label, "Not a valid command", a hang).
---

# sbt 2 — how it is driven here

Every repository in this family is sbt 2 with sbt-kubuszok conventions. The sbt 2 change summary
applies (<https://www.scala-sbt.org/2.x/docs/en/changes/sbt-2.0-change-summary.html>); the points
below are the ones that have cost hours when ignored.

## 1. One server per checkout, one client at a time

- Always `sbt --client "cmd1; cmd2"`. A bare `sbt` starts a second server against the same
  checkout; two clients on one server queue, they do not fail — a "hang" is usually the other client.
- A worktree has its OWN server (its own dir). Lanes in a worktree and lanes in the main checkout
  run in parallel; two lanes in ONE checkout do not.
- Never `pkill` by pattern (machine-global: it kills other sessions' servers). Stop a client with
  the harness (TaskStop); the server finishes the command on its own.
- `sbt --client` never sees the shell's environment: a flag the build must read goes in a marker
  FILE (`run.properties` < `debug.properties` < `-D`), see `debug-port`.

## 2. The version is git-describe AT SERVER LOAD

`version` is computed when the build loads. After a commit, `publishLocal` without `reload` publishes
the NEW code under the OLD hash. Always: `sbt --client "reload; publishLocal"`, then read the
`published …/<hash>-SNAPSHOT/jars/…` line and pin exactly that hash in the consumer
(`~/.ivy2/local/com.kubuszok/<artifact>_3/<hash>-SNAPSHOT`). A docs-only commit also moves the hash.

## 3. The action cache goes stale; move it aside, never delete blindly

`~/Library/Caches/sbt/v2/ac` (action cache) and `…/cas` (content store, tens of GB). A lane that
"did nothing", a spec that passes on stale classes, or a `[error] … not found` after a clean tree:
`mv ~/Library/Caches/sbt/v2/ac ~/Library/Caches/sbt/v2/ac.stale-$(date +%F)` and rerun. Test
totals that vary between runs (1269 / 1125 / 874) are the cache skipping unchanged suites — not a
regression; read `Failed:` not `Total`.

## 4. JDK is an input

`export JAVA_HOME=$(cs java-home --jvm adoptium:<v>); export PATH=$JAVA_HOME/bin:$PATH` in every
shell. Baltic Porter lanes: 22 (`jdk_version`, `jdk_guard` refuses the rest). sge, ssg: 25 (their
CI). lls `release.yml` pins temurin 17 (pre-existing). `overrides nothing` is a JDK mismatch first.

## 5. Commands live in build.sbt, never in YAML

A multi-command CI step is an sbt ALIAS in `build.sbt` (sbt-welcome lists it): sge has
`testCompile-{jvm,js,native}-3`, `ci-jvm-3`, `test-js-3`, `test-native-3`. A `\`-continued
`sbt '…'` in YAML is "Not a valid command: \". A new step is a new alias plus one YAML line.

## 6. Long runs

- `tee` every long command into the scratchpad (`… 2>&1 | tee $S/<what>-<n>.log | grep …`) — the
  terminal shows a few lines, the log is what you read back.
- Over ~10 minutes: run in the background and wait for the notification; the harness kills a
  foreground process tree at call end (137). Measure lanes: `just <lane>-measure`, serially.
- Scalafmt-on-compile rewrites files during a build: `git status` after a run, and `git checkout`
  any fixture the lane rewrapped (`ported/*/src/test/scala/sge/SgeTestFixture.scala`) before
  committing; re-Read a file the build reformatted before editing it.

## 7. The consumer's generation marker

sge/ssg regenerate only when `target/balticporter-<port>/.generated-marker` is absent or the pin
changed: after a new pin, `rm -f target/balticporter-sge/.generated-marker
target/balticporter-lls/.generated-marker`, then `reload`. Parallel matrix rows share the generator
(`BalticPorterGen` is `synchronized`). The marker is keyed on the VENDORED java commit, not the
engine pin: a run still ahead of yours on the server rewrites it after your `rm`, and your command
then compiles the previous engine's tree — the log says `[Baltic Porter] Using cached generated
sources`. Remove the markers only when the server is idle, and grep that line before reading errors.
