---
name: ci-caching
description: How CI is structured and cached in repositories that generate code with Baltic Porter (sge, lls, ssg) — generate once, compile once, share through the Actions cache and the sbt 2 remote cache, one definition per step. Use when adding or changing a CI job or workflow, when a job is slow or repeats work another job did, when touching a cache key, when a job fails with a missing or stale generated tree, and before adding a setup step to a workflow.
---

# CI and caching in a Baltic Porter consumer

## The shape

```
wave 0   format · covenant gate · plugin tests            need nothing from the generator
         generate                                         the ONLY job with the upstream submodule
wave 1   compile (jvm | js | native)                      every module compiled once, on Linux
wave 2   tests · links · integration tests · scaladoc     restore, then only link and run
release  publish snapshot → demos against that snapshot   demos.yml (reusable workflow)
```

Rules that keep it that way:

1. **One job generates.** Only `generate` initialises the submodule, needs full git history and runs
   Baltic Porter. Every other job restores the tree. A job that adds `git submodule update` or a
   generation step is wrong — give it `needs: [generate]` and the `generated-port` action.
2. **One job per platform row compiles.** Downstream jobs `needs: [compile]` and get the outputs from
   the remote cache. Compiling needs no clang, Node or display — linking and running do, so only
   those jobs install them (`setup-native-toolchain`, `setup-js`, `setup-gl-xvfb`).
3. **Nothing resolves the engine from a checkout.** The engine is the artifact pinned in
   `project/plugins.sbt`; its jar carries the files the port injects.
4. **No step is written twice.** A step two jobs share is a composite action under
   `.github/actions/`; a job group two workflows share is a reusable workflow (`workflow_call`).
   Versions (JDK, Node) are input defaults of `setup-scala` / `setup-js`, nowhere else.
5. **Commands are sbt aliases**, never multi-line YAML: `generatePort`, `testCompile-<row>-3`,
   `ci-jvm-3`, `test-<row>-3`, `verifyLocal`. The `ci-*` aliases do NOT start with `clean`: it
   deletes `target/balticporter-*`, i.e. the generated tree.
6. **Demos consume published artifacts.** `release.yml` publishes the snapshot and then calls
   `demos.yml` with its version; the demos resolve it from Maven Central's snapshot repository.
   Only a fork PR (nothing is published for it) publishes locally, from `ci.yml`.

## The two caches, and what each is for

| | Actions cache (`actions/cache`) | sbt 2 remote cache (BuildBuddy, Bazel gRPC) |
|---|---|---|
| holds | the generated tree `target/balticporter-<module>` | compile outputs, by input hash |
| key | engine pin · upstream commit the checkout records · generator source · JDK major · (sge) the tree hash of the hand port on `master` | computed by sbt per task |
| written by | `generate` (and `release.yml`'s publish job), once per distinct key | every job that has the API key |
| shared across | jobs, workflows and re-runs of the same ref; a PR also reads the base branch's entries | jobs of the SAME os × arch × (ci \| dev) namespace |
| miss | `restore` mode fails the job: `generate` did not run or the key inputs changed mid-run | the task simply runs |

- The generated-tree key is computed by `kubuszok/balticporter/.github/actions/generated-port`
  (each consumer wraps it in a local action that pins its version in ONE place). Downstream jobs
  take the key from `needs.generate.outputs.key` — they cannot compute it (shallow checkout).
- The generator's own marker (`target/balticporter-<module>/.generated-marker`) repeats the inputs
  it can read without the submodule: engine pin, upstream commit, generator hash, JDK major. A
  restored tree whose marker does not match is refused with the reason — it is never silently
  regenerated or silently reused.
- **The JDK major is part of both.** The generated code differs by JDK (a member gets `override`
  only where that JDK's supertype declares it), so generate and compile on the same major.

## What is never cached

- `~/.cache/sbt/v2` through `actions/cache`: the remote cache supersedes it, and per-OS keys made it
  worse.
- Dependencies per job (`coursier/cache-action`, setup-java's `cache: sbt`): every job saves its own
  1–2 GB entry, a repository has 10 GB, and eviction is least-recently-used — one run pushed the
  12 MB generated-port entry out and the next job failed on `fail-on-cache-miss`. Before adding ANY
  cache, check `gh api repos/<owner>/<repo>/actions/cache/usage` and the size of what it would save.
- Native toolchain tasks (`nativeConfig`, discovered clang paths): machine-specific outputs poisoned
  other operating systems once; they are `Def.uncached` and the remote cache is namespaced per OS.
- Scaladoc with the remote cache ON: sbt 2 replays a cached *failure*, and scaladoc crashes
  nondeterministically. The doc job restores compile outputs with the cache on, then runs `doc`
  with `SGE_REMOTE_CACHE=off` and one retry with the local action cache purged.
- A tagged release: built from source, remote cache off.
- Fork PRs have no secrets: the remote cache is off and nothing is published; everything still works.

## One sbt server per job — and it keeps the FIRST step's environment

On a runner, `sbt <command>` is a thin client: the first call of a job starts a server in the
background and every later call of that job talks to it. The server has the environment of the step
that started it, so a later step's `env:` never reaches the build: Sonatype credentials on a publish
step after a generation step ("Unable to find credentials"), `SGE_REMOTE_CACHE=off` on a doc step
after a compile step, `JAVA21_HOME` exported after the first sbt call. Either put such variables at
JOB level (or in `$GITHUB_ENV` before the first sbt call), or run `sbt shutdown` between the two
steps — the `generated-port` action does that itself after generating.

In **bash on Windows** that thin client cannot start its server at all (`Cannot run program
"C:/Program Files"`): call `sbt.bat` there, which is what `sbt` means in a PowerShell step. The
`sbt-guarded` action does the substitution.

## Reading a run

- Wall clock and runner-minutes: `gh run view <id> --json jobs --jq '.jobs[] | [.name, .conclusion, ((.completedAt|fromdate)-(.startedAt|fromdate))] | @tsv'`.
- Did generation run? The `generate` job's "Restore the generated port" step says `Cache hit` or
  not; a hit skips the submodule and the generation entirely.
- Did a job recompile? Its sbt log prints `compiling N Scala sources` for a module that `compile`
  already built only on a remote-cache miss — then check the job ran on the same OS/arch namespace
  and that `BUILDBUDDY_API_KEY` reached it (reusable workflows need it passed under `secrets:`).
- After a push: ONE `gh run watch <id> --exit-status` in the background; read every red job before
  changing anything; re-run a flake with `gh run rerun <id> --failed`, never an empty commit.

## Before changing CI

Lint locally: `actionlint` (download script in the `format` job). A workflow change cannot be tested
locally beyond that — so batch CI edits into ONE push, and keep every sbt-level piece (aliases, the
generator, the marker) verifiable with `sbt --client`, which IS testable locally: a lone clone with
no sibling directories must `generatePort` and test-compile.
