---
name: consumer-ci
description: Make a consumer PR (sge, ssg) green from the Baltic Porter side — the fix-all-locally-then-push loop over JVM, JS, Native and Android rows, the pin bump, the regenerate/compile/test chain, the covenant gate and its approval marker, and the list of things that count as cheating. Use whenever a sge/ssg CI job is red, before pushing to a consumer branch, and when a consumer file was patched to make generated code compile.
---

# Consumer CI — the loop

CI is not the debugger. One push per cycle, every row green locally first.

## 1. The loop

1. Collect EVERY failing job (JVM, JS, Native, Android, `packageSrc`, scalafmt, covenant gate,
   the cancelled dependents). Read each log to its root cause; group by cause.
2. Classify each cause: engine (a), parameterised phase/policy (b), library rule (c), consumer
   build (aliases, pins, markers), pre-existing drift. The fix goes where the cause is — almost
   always in Baltic Porter, never in the consumer's generated tree.
3. Fix ALL known causes; measure the lanes (`iterate-lane`); publish; pin; regenerate; the whole
   chain locally (step 3); then push. A red CI after that returns to step 1 for ALL failures —
   never a one-line patch and another push.

## 2. What counts as cheating (each was tried, each was reverted)

- `@Ignore`/`.ignore` on a test, deleting a test, an empty suite, `assert(true)`.
- Editing a test to the generated spelling (`same.getOrElse(null)`, boxed `Float.valueOf`) —
  the test is master's; the generated spelling is wrong.
- `testFull` → `compile`, a JS/Native row downgraded to compile-only, a module skipped in an alias.
- Growing `.rescale/data/covenant-gate-baseline.tsv`; a `@nowarn`, `null.asInstanceOf`,
  `.getOrElse(null)` in a hand file (the scanner counts them: `null-cast`, `get-or-else-null`).
- A JVM-only dependency (jackson, ANTLR, reflection) to make a row pass.
- Patching an extension to the generated core's spelling (`u_=`→`setU`, `compare`→`compareTo`):
  the fix is the derivation/fold that made the core diverge from master; then `git checkout
  <merge-base> -- <file>` puts the extension back.

## 3. The chain (sge; ssg is the same shape)

```
# Baltic Porter                          # JDK 22
sbt --client "reload; publishLocal"      → note <hash>
# sge                                    # JDK 25
Edit project/plugins.sbt: both balticporter pins → <hash>-SNAPSHOT
rm -f target/balticporter-sge/.generated-marker target/balticporter-lls/.generated-marker
sbt --client "reload; testCompile-jvm-3"; sbt --client "testCompile-js-3"; sbt --client "testCompile-native-3"
sbt --client "ci-jvm-3"; sbt --client "test-js-3"; sbt --client "test-native-3"
sbt --client "scalafmtCheckAll; scalafmtSbtCheck; sge / Compile / packageSrc"
PATH=<re-scale>/bin:$PATH .rescale/scripts/covenant-gate.sh
```
Each command through `tee` (skill `sbt2-client`). Rows fail in order: fix the first row's
root cause before reading the next; the same cause usually explains all three.

## 4. The consumer's generator and dedup

`project/BalticPorterGen.scala` drops a generated file whose path exists under `src/main` (the
hand file wins). So an "inject copy" living in `src/main` shadows the injected file and its
covenant header shows up as `missing-header`: delete the copy (six were), never header it.
"N fatal finding(s)" downgraded to a warning by the generator are pre-existing dropped-type
references the hand tree replaces; new fatal kinds are yours.

## 5. The covenant gate

`re-scale enforce verify --all` / `enforce shortcuts --covenanted` behind
`.rescale/scripts/covenant-gate.sh`. Growth vs merge-base (a new `(file, kind)` pair, a raised
count, rows removed with a deleted file) needs `covenant-baseline-approved: <reason>` in EVERY
commit that edits the baseline since merge-base; an uncommitted baseline edit can never be
approved. `methods-removed` lists the names (`re-scale enforce verify --all | grep 'methods
removed'`); each is a spelling the generated core lost against master — a derivation question
first, a header edit never.

## 6. What to report

Per row: the count before → after, the root cause per group, which repository fixed it, and the
residues left with their owner (pre-existing drift, parked engine items) — never "green" without
the three rows' test totals.
