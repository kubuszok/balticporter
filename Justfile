# Baltic Porter — the measurement suite (CLAUDE.md §5).
#
# One entry point for every number this project quotes. `just` with no recipe lists them.
#
#   just gdx-measure                 libGDX core        (emit → checks → break residue → compile → correlate)
#   just gdx-test-measure            libGDX's own suite (… → compile → RUN → correlate)
#   just ashley-measure              Ashley + its suite, compiled WITH libGDX core (a dependent port)
#   just anim8-measure               anim8-gdx, compiled WITH libGDX core (a dependent port)
#   just gltf-measure                gdx-gltf + its suite, compiled WITH libGDX core (a dependent port)
#   just screens-measure             libgdx-screenmanager, compiled WITH libGDX core (a dependent port)
#   just vfx-measure                 gdx-vfx, compiled WITH libGDX core (a dependent port)
#   just sg-measure                  simple-graphs + its suite
#   just noise4j-measure             noise4j — emit, checks, break residue, compile, correlate
#   just jbump-measure               jbump (no suite upstream — the lane re-derives the zero)
#   just measure-all                 every lane, SERIALLY, in dependency order
#   just decision-counts             decisions.tsv row counts by kind, every port
#   just members-unchanged [PORT]    members.tsv against its committed baseline — the blast radius
#   just baseline-list                        every port: baseline size and last run
#   just baseline-show   PORT                 the run's full report
#   just baseline-diff   PORT                 the run against the committed baseline
#   just baseline-accept PORT                 promote run-latest to baseline
#
# …and the DEBUGGING surface — the CLAUDE.md §4.6 flags and the diagnostics over emitted code.
# These are the same tools the lanes use, reachable without knowing which main class exists:
#
#   just debug-flags [PORT]          WHICH layer defines each balticporter.* flag right now
#   just debug-set   KEY VALUE       write one flag into .balticporter/debug.properties (wins)
#   just debug-clear [KEY]           remove one flag, or ALL of them (no key = the file goes)
#   just debug-emit  ROOT FQN [PHASES] [FLAGS…]   one type's TIR + Scala, around a phase boundary
#   just correlate   OUT [--scalac f] [--tests f] [--srcmap [scope=]f]…
#                                    join a compiler / test-runner log you produced BY HAND back
#                                    to members and Java origins (CLAUDE.md §5.1)
#   just debug-selfcheck             proves the four above do what they say — no sbt, no ports
#
# WHY ONE FILE AND NOT A SCRIPT PER LANE. The four lanes are one measurement over four sets of
# paths, and as four scripts the differences between them were invisible: each held its own copy of
# the compile-and-count block, so which lane prints the bare-error breakdown, which one correlates
# when the compile FAILS, and which one passes the base port's `srcmap.tsv` were all facts you could
# only get by diffing four files. Here they are side by side. The mechanism stays in
# `scripts/_lib.sh` — sourced by every lane, and the place the reasoning lives — and the POLICY,
# which sbt project and which upstream tree and which dependency coordinates, is a variable at the
# top of this file, so a module rename is one line and never a grep through shell.
#
# THREE THINGS THAT ARE NOT STYLE, each of which cost a measurement when it was got wrong:
#
#  - **No `set -e`, anywhere in a lane.** `grep -c` exits 1 when it counts zero, and
#    `ERRORS=$(grep -cE '^-- .*Error' …)` counting zero is the SUCCESS case; `[ "$a" != "$b" ] &&
#    echo …` is the shape of the test-discovery guards. Under `set -e` a lane would abort exactly
#    when the port is green. Every guard is explicit instead, and each one says what went wrong —
#    which `set -e` cannot. `baseline-accept` is the one recipe that DOES set it, because it is a
#    file-copying command where a failed copy must stop the promotion.
#  - **Scratch captures go under the CHECKOUT** (`.balticporter/tmp`, `MEASURE_TMP` in the helper),
#    never /tmp: the fixed /tmp names collided the moment two worktrees measured concurrently and
#    one checkout's compile output silently counted, and then CORRELATED, as the other's.
#  - **A lane with NO test set still measures test discovery.** `noise4j-measure` is the first one:
#    noise4j ships no Java tests at all, so the lane asserts that upstream `@Test` count is ZERO
#    rather than omitting the block. Omitting it is how a suite that runs no tests reports success
#    — and here it would also hide the day upstream gains one.
#  - **The lanes are SERIAL and ordered.** Each re-emits into `src_managed/`, so the dependent lanes
#    compile against what the base lane just wrote. `measure-all` runs them one at a time and stops
#    at the first failure rather than measuring a stale emit.

# ---------------------------------------------------------------------------------------------
# Policy: sbt project identifiers, emitted-port directories, upstream trees, compile dependencies.
# A module rename is a change HERE and nowhere else.
# ---------------------------------------------------------------------------------------------

# sbt projects
corpus        := "corpus"                # holds the migration programs (balticporter.corpus.*)
core_project  := "engine"                # holds balticporter.tir.CorrelateMain

# ported modules (their emitted Scala lives in <module>/src_managed/{main,test}/scala)
gdx_module    := "libgdx-core"
ashley_module := "ashley-core"
sg_module     := "simplegraphs-core"
anim8_module  := "anim8-core"
n4j_module    := "noise4j-core"
jbump_module  := "jbump-core"
gltf_module   := "gltf-core"
screens_module := "screens-core"
vfx_module    := "vfx-core"

# upstream Java, relative to the checkout root
gdx_src       := "../sge/original-src/libgdx/gdx"
ashley_src    := "../sge/original-src/ashley"
sg_src        := "../sge/original-src/simple-graphs"
anim8_src     := "../sge/original-src/anim8-gdx"
n4j_src       := "../sge/original-src/noise4j"
jbump_src     := "../sge/original-src/jbump/jbump/src"
# jbump's WHOLE upstream checkout, not just the ported module: the `@Test` census runs over it so
# that "jbump ships no test suite" is a number this lane re-derives, never a claim in a document.
# Upstream's `test` gradle module is a runnable libGDX demo (`TestBump extends ApplicationAdapter`),
# and the day somebody adds a real suite there this lane is what says so.
jbump_upstream := "../sge/original-src/jbump"
gltf_src      := "../sge/original-src/gdx-gltf/gltf/src"
screens_src   := "../sge/original-src/libgdx-screenmanager"
vfx_src       := "../sge/original-src/gdx-vfx"
# gdx-gltf's WHOLE test tree, not the one file the port migrates: SEVEN java files sit there and
# only ONE is a suite (`AttributesCompareTest`, 8 `@Test`). The other six are `extends Game` demos
# with a `main` that opens an lwjgl window. `java_test_count` over the tree is what re-derives the
# 8 — and what says so the day a second file gains a real `@Test`.
gltf_tests    := "../sge/original-src/gdx-gltf/gltf/test"

# the compiler every lane measures with — one version, one server-less invocation per lane
scala_version := "3.8.4"

# per-lane compile/test dependencies, verbatim scala-cli flags (word-split on purpose)
gdx_deps      := "--dependency org.junit.jupiter:junit-jupiter:5.10.2 --dependency junit:junit:4.13.2 --dependency org.scalameta::munit:1.0.2"
# The libGDX suite's RUN carries the same three coordinates in a DIFFERENT order, and it is kept
# that way on purpose: this is the order the run that produced the committed `tests.tsv` used, and
# the order of `--dependency` flags is an input to scala-cli's classpath — with junit4, jupiter and
# munit all present, which runner claims a suite is decided by scanning it. Reordering may well be
# harmless; it is not something this file is entitled to change silently, and the lane that would
# discover it costs 221 tests to run.
gdx_run_deps  := "--dependency org.scalameta::munit:1.0.2 --dependency junit:junit:4.13.2 --dependency org.junit.jupiter:junit-jupiter:5.10.2"
# Mockito 1.10.19, NOT a 2.x/5.x: Ashley's `ComponentClassFactory` uses `org.mockito.asm`, removed
# in 2.0. Read from Ashley's own build.gradle rather than guessed — guessing it cost a full cycle.
ashley_deps   := "--dependency junit:junit:4.13.2 --dependency org.mockito:mockito-all:1.10.19 --dependency org.scalameta::munit:1.0.2"
sg_deps       := "--dependency junit:junit:4.12 --dependency org.scalameta::munit:1.0.2"
# anim8 upstream declares NO test framework at all (its `src/test/java` is a set of lwjgl3 demo
# apps, not a suite — see Anim8Migrate's scope note), so the only coordinate this lane needs is the
# one its HAND-WRITTEN suite is written in.
anim8_deps    := "--dependency org.scalameta::munit:1.0.2"
# noise4j declares NO dependencies — its `build.gradle` has an empty `dependencies` block and the
# 12 sources import nothing outside `java.lang`, `java.math` and `java.util`. Stated as an empty
# variable rather than omitted from the lane, so the lane reads the same as every other one and the
# claim "this library needs nothing" is written down where it can be contradicted.
n4j_deps      := ""
# jbump declares NO dependencies at all (`jbump/build.gradle` is four lines and adds none), and it
# is a `RuntimeMode.Vendored` port, so the support types ship inside the emitted source set. Empty
# on purpose, and left as a variable rather than dropped from the lane: the day the port grows a
# test source set this is the line that gains a coordinate.
jbump_deps    := ""
# JUnit 4.12 — gdx-gltf's OWN `junitVersion`, from its root `build.gradle`, not the 4.13.2 the
# other lanes happen to use. The suite is converted to MUnit by `TestFrameworkTransform`, so the
# junit coordinate is not what RUNS it; it is here because scala-cli must resolve the same surface
# the frontend did, and because a port resolves what the library DECLARES (see `ashley_deps`).
gltf_deps     := "--dependency junit:junit:4.12 --dependency org.scalameta::munit:1.0.2"
# libgdx-screenmanager's `build.gradle` declares gdx 1.13.5 and `com.github.crykn.guacamole:gdx`.
# libGDX arrives as EMITTED SCALA on this compile, not as a jar, and guacamole is replaced by the
# hand-written Scala in `screens-core/src/main/scala` — so the only COMPILE coordinate left is the
# annotation jar those sources are written against. `@Nullable`/`@NullMarked` survive the port as
# real annotations (an annotation IS a declaration's contract, `Annot` in the TIR), so the jar has
# to be present or four emitted declarations do not resolve. munit is for the hand-written suite.
screens_deps  := "--dependency org.jspecify:jspecify:0.3.0 --dependency org.scalameta::munit:1.0.2"
# gdx-vfx's only compile dependency is libGDX itself, which this lane supplies as the SOURCE the
# base port emitted rather than as a coordinate. So the only coordinate here is the one its
# HAND-WRITTEN suite is written in — the same shape, and for the same reason, as anim8's.
vfx_deps      := "--dependency org.scalameta::munit:1.0.2"

root          := justfile_directory()

# The checkout whose `.balticporter/` the debug recipes read and write, and the directory the
# baseline recipes read. Both are this checkout, always, in normal use — they are variables so
# `just debug-selfcheck` can point them at a throwaway directory and PROVE the recipes rather
# than describe them. A self-check that has to mutate the real `port-report/` to run is a
# self-check nobody runs twice.
bp_root       := env_var_or_default("BP_ROOT", justfile_directory())
report_root   := env_var_or_default("BP_REPORT", justfile_directory() / "port-report")

_default:
    @{{just_executable()}} --list --unsorted

# ---------------------------------------------------------------------------------------------
# libGDX core — emit, checks, break residue, compile, correlate.
#
# scala-cli is the consistent gate; sbt incremental lies. This is the command CLAUDE.md §5 tells
# everyone to run, so it must show everything the migration knows: the block below used to be
# `grep -E "wrote" | head -1`, and the checks were computed on every run and then DISCARDED by the
# one command anybody runs.
# ---------------------------------------------------------------------------------------------
[doc("libGDX core — emit, checks, break residue, compile, correlate")]
gdx-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # Make persisted findings machine-independent: paths in the artifact are relative to this root,
    # so a baseline committed from one checkout diffs cleanly against a run from another (or from a
    # worktree). See balticporter.tir.CheckReport.relativise.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{gdx_src}}"
    REPORT="$ROOT/port-report/LibgdxCoreMigrate"

    # ABORT if the migration itself did not run. Piping straight into `grep wrote` discarded the exit
    # status, so an engine that failed to COMPILE printed nothing and the lane went on to measure the
    # PREVIOUS emit — reporting a stale number as a result. Two consecutive measurements were read as
    # "no change" when the change had never been built.
    MIGRATE_OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.libgdx.LibgdxCoreMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$MIGRATE_OUT"; then
      echo "!! MIGRATION DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$MIGRATE_OUT" | head -20
      exit 1
    fi

    echo "-- migration (ALL checks, untruncated, as the migration printed them) --"
    # The whole block the migration emitted, in order, from its first line to its last. A `grep` for
    # named lines is how the checks got lost in the first place: it silently drops any line a future
    # check adds.
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$MIGRATE_OUT"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"

    echo
    break_residue {{gdx_module}}/src_managed/main/scala

    echo "-- compile --"
    scala-cli compile --scala {{scala_version}} --server=false {{gdx_module}}/src_managed/main/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    # count ALL errors: coded `-- [Exxx] ... Error` AND bare `-- Error:` (e.g. "secondary constructor
    # must call a preceding constructor" carries no code). The coded-only count silently undercounts.
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gdxmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/gdxmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/gdxmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/gdxmeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gdxmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/gdxmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # A count is not a triage. Join every error back to the member and the JAVA LINE it came from, and
    # split it into "at a region the engine marked approximate" vs "engine gap" (DESIGN.md §6.3).
    # With no markers minted yet everything lands in the second lane — which is the honest answer,
    # and the lane an agent in another repository has to act on.
    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/gdxmeasure.txt --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# libGDX's own JUnit suite — re-emitted and compiled TOGETHER with the ported core, then RUN.
#
# The tests are the port's only BEHAVIOURAL gate; everything `gdx-measure` reports is "compiles".
# Note the discovery check: a JUnit suite with no @Test annotations runs ZERO tests and reports
# success, which is exactly the silent-omission failure this project keeps finding.
# ---------------------------------------------------------------------------------------------
[doc("libGDX's own suite — the same, then RUN it")]
gdx-test-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{gdx_src}}"
    REPORT="$ROOT/port-report/LibgdxTestMigrate"

    # ABORT if the migration did not run — the same stale-output defect fixed in `gdx-measure`: piping
    # into grep discards the exit status, so an engine that fails to COMPILE measures the PREVIOUS emit
    # and reports it as a result.
    MIGRATE_OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.libgdx.LibgdxTestMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala test files" <<<"$MIGRATE_OUT"; then
      echo "!! TEST MIGRATION DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+" <<<"$MIGRATE_OUT" | head -20
      exit 1
    fi

    echo "-- migration (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala test files/p' <<<"$MIGRATE_OUT"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"

    echo
    echo "-- test discovery --"
    # Count what each FRAMEWORK would actually discover. A ported suite is MUnit (`test("name") {…}`)
    # and the residue is still JUnit (`@Test`), so counting only annotations under-reports by every
    # converted suite — the check must sum both or it lies in the safe-looking direction.
    JAVA_TESTS=$(java_test_count {{gdx_src}}/test)
    JUNIT_LEFT=$(grep -rh "@org.junit.Test\|@Test" {{gdx_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    MUNIT_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{gdx_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    [ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! TESTS LOST — $((JAVA_TESTS - SCALA_TESTS)) of $JAVA_TESTS would never run, and the suite would report success"

    echo
    break_residue {{gdx_module}}/src_managed/test/scala

    echo "-- compile --"
    scala-cli compile --scala {{scala_version}} --server=false {{gdx_deps}} \
      {{gdx_module}}/src_managed/main/scala {{gdx_module}}/src_managed/test/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxtestmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gdxtestmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/gdxtestmeasure.txt
    echo "TOTAL ERRORS: $ERRORS"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gdxtestmeasure.txt | sort | uniq -c | sort -rn | head

    # -------------------------------------------------------------------------------------------
    # RUN them. Compiling a test suite measures nothing about behaviour, and CLAUDE.md §4.4 lists ten
    # Java forms that translate to VALID Scala meaning something else — reference `==`, `x++` as a
    # value, `break`/`continue`, `switch` fall-out, a dropped `super(args)`, `@Before`. Not one of them
    # moves the error count above. Running the suite is the only gate that sees them.
    # -------------------------------------------------------------------------------------------
    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false {{gdx_run_deps}} \
        -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{gdx_module}}/src_managed/test/scala 2>&1 |
        sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxtestrun.txt
      reconcile_outcomes "$MEASURE_TMP"/gdxtestrun.txt "$MUNIT_TESTS"

      # Anchor every failure on the first stack frame that lands in PORTED code and resolve it, through
      # both ports' source maps, to a member and a Java origin — then diff the pass/fail sets against
      # the baseline. A newly-failing test whose member also changed digest is the highest-value signal
      # this engine can produce, and it is the only lane that catches a §4.4 regression.
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/gdxtestrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "test=$REPORT/run-latest/srcmap.tsv"
    else
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# Ashley (main + its JUnit suite), compiled BOTH together with the ported libGDX core.
#
# Ashley is a DEPENDENT port (RuntimeMode.Dependency): the collection shims are vendored by
# libgdx-core, so both source sets must be on the same scala-cli invocation. Compiling ashley-core
# alone measures nothing — every one of its 21 files resolves against libGDX.
# ---------------------------------------------------------------------------------------------
[doc("Ashley + its suite, compiled WITH libGDX core (a dependent port)")]
ashley-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{ashley_src}}"
    REPORT="$ROOT/port-report/AshleyMigrate"
    TREPORT="$ROOT/port-report/AshleyTestMigrate"

    for M in AshleyMigrate AshleyTestMigrate; do
      OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.ashley.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
        exit 1
      fi
      echo "-- $M (every line it printed) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    show_check_report "$TREPORT"

    echo
    echo "-- test discovery --"
    # The same both-frameworks sum `gdx-test-measure` uses: a ported suite is MUnit and any residue is
    # still JUnit, so counting only one under-reports by every converted suite — in the safe-looking
    # direction, which is the dangerous one.
    JAVA_TESTS=$(java_test_count {{ashley_src}}/ashley/tests)
    JUNIT_LEFT=$(grep -rh "@org.junit.Test\|@Test" {{ashley_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    MUNIT_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{ashley_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    [ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! TESTS LOST — $((JAVA_TESTS - SCALA_TESTS)) of $JAVA_TESTS would never run, and the suite would report success"

    DEPS="{{ashley_deps}}"

    echo
    break_residue {{ashley_module}}/src_managed

    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{gdx_module}}/src_managed/main/scala {{ashley_module}}/src_managed/main/scala {{ashley_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/ashleymeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/ashleymeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/ashleymeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/ashleymeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/ashleymeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/ashleymeasure.txt | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{ashley_module}}/src_managed/main/scala {{ashley_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/ashleyrun.txt
      reconcile_outcomes "$MEASURE_TMP"/ashleyrun.txt "$MUNIT_TESTS"
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps: only the library's own map can anchor a failure on the member that threw,
      # and only the suite's can name the test. libGDX's is passed too — a stack that reaches the base
      # is exactly what a dependent's failure looks like.
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/ashleyrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
    else
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$TREPORT"

# ---------------------------------------------------------------------------------------------
# anim8-gdx, compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port with the same shape as Ashley's — every one of its 16 files resolves against
# libGDX, the collection shims are vendored by libgdx-core, so both source sets must be on the same
# scala-cli invocation and this lane must run AFTER `gdx-measure` has re-emitted the base.
#
# WHERE THIS LANE DIFFERS FROM EVERY OTHER ONE, and it is not a shortcut: anim8 has NO upstream
# suite. Its `src/test/java` holds 20 files and ZERO `@Test` annotations — every one is an
# `ApplicationAdapter` demo or a startup bench driven by `gdx-backend-lwjgl3`, and no backend is
# ported. So there is no `Anim8TestMigrate` and no emitted test source set; the port's behavioural
# gate is the HAND-WRITTEN MUnit suite committed under `anim8-core/src/test/scala` (CLAUDE.md §5.5:
# `src/` is the hand-written half of a port). The discovery block below states both numbers
# explicitly rather than letting `0 == 0` read as agreement — a suite with no discoverable tests
# runs ZERO and reports SUCCESS, which is the exact failure `java_test_count` exists to catch, and
# a lane whose java side is legitimately zero must say so out loud or it teaches its reader nothing.
# ---------------------------------------------------------------------------------------------
[doc("anim8-gdx, compiled WITH libGDX core (a dependent port)")]
anim8-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{anim8_src}}"
    REPORT="$ROOT/port-report/Anim8Migrate"

    OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.anim8.Anim8Migrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! Anim8Migrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- Anim8Migrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"

    echo
    echo "-- test discovery --"
    # The java count is computed and printed even though it is expected to be 0: an upstream that
    # gains a suite must show up here rather than being assumed away by a comment.
    JAVA_TESTS=$(java_test_count {{anim8_src}}/src/test)
    HAND_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{anim8_module}}/src/test/scala 2>/dev/null | wc -l | tr -d ' ')
    EMITTED_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{anim8_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    echo "@Test in upstream java: $JAVA_TESTS (upstream ships DEMOS, not a suite — nothing to port)"
    echo "hand-written munit in {{anim8_module}}/src/test/scala: $HAND_TESTS   emitted: $EMITTED_TESTS"
    [ "$JAVA_TESTS" != "0" ] && echo "!! UPSTREAM NOW HAS A SUITE — $JAVA_TESTS @Test method(s) that this port does not migrate; add an Anim8TestMigrate"
    [ "$HAND_TESTS" = "0" ] && echo "!! NO BEHAVIOURAL GATE — this port would compile and prove nothing (CLAUDE.md §3)"

    echo
    break_residue {{anim8_module}}/src_managed

    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    DEPS="{{anim8_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{gdx_module}}/src_managed/main/scala {{anim8_module}}/src_managed/main/scala {{anim8_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/anim8measure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/anim8measure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/anim8measure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/anim8measure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/anim8measure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/anim8measure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/anim8measure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{anim8_module}}/src_managed/main/scala {{anim8_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/anim8run.txt
      reconcile_outcomes "$MEASURE_TMP"/anim8run.txt "$HAND_TESTS"
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps. There is no `test=` map: the suite is hand-written, so no srcmap can
      # anchor a test FRAME on a Java origin — but a failure inside the LIBRARY still resolves
      # through anim8's own map, and one that reaches the base resolves through libGDX's, which is
      # exactly what a dependent's failure looks like.
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/anim8run.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/anim8measure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# gdx-gltf (main + its one JUnit suite), compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port of the same shape as Ashley's and anim8's — all 118 distinct `com.badlogic.*`
# imports across its 135 files resolve inside `gdx/src`, and the collection shims are vendored by
# libgdx-core — so both source sets must be on the same scala-cli invocation and this lane must run
# AFTER `gdx-measure` has re-emitted the base.
#
# WHERE THIS LANE'S TEST DISCOVERY EARNS ITS KEEP. Upstream `gltf/test` holds SEVEN files and only
# ONE of them is a suite; the other six are `extends Game` / `extends ApplicationAdapter` demos with
# a `main` that opens an lwjgl window, and the ONLY import in the whole checkout that `gdx/src`
# cannot resolve is theirs (`com.badlogic.gdx.backends.lwjgl`). `GltfTestMigrate` therefore names
# its one input file rather than globbing, and this block counts `@Test` over the WHOLE tree so the
# 8 is a number the lane re-derives and not a claim in a comment — and so the day a second file
# gains a real `@Test`, the equality guard below says so instead of absorbing it.
# ---------------------------------------------------------------------------------------------
[doc("gdx-gltf + its suite, compiled WITH libGDX core (a dependent port)")]
gltf-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{gltf_src}}"
    REPORT="$ROOT/port-report/GltfMigrate"
    TREPORT="$ROOT/port-report/GltfTestMigrate"

    for M in GltfMigrate GltfTestMigrate; do
      OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.gltf.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
        exit 1
      fi
      echo "-- $M (every line it printed) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    show_check_report "$TREPORT"

    echo
    echo "-- test discovery --"
    # Both frameworks summed, as `gdx-test-measure` and `ashley-measure` do: a ported suite is MUnit
    # and any residue is still JUnit, so counting one under-reports in the safe-LOOKING direction.
    JAVA_TESTS=$(java_test_count {{gltf_tests}})
    JUNIT_LEFT=$(grep -rh "@org.junit.Test\|@Test" {{gltf_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    MUNIT_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{gltf_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java (whole {{gltf_tests}} tree): $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    [ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! TESTS LOST — $((JAVA_TESTS - SCALA_TESTS)) of $JAVA_TESTS would never run, and the suite would report success"
    # …and the HAND-WRITTEN half, counted and printed separately rather than summed into the line
    # above. Upstream's whole suite is 8 attribute-comparison tests, which says nothing about the
    # glTF reader that is most of the library; `gltf-core/src/test/scala` is what covers the §4.4
    # hazards in `GLTFTypes` (CLAUDE.md §5.5 — `src/` is the hand-written half of a port). Keeping
    # the two numbers apart is the point: a ported test and a written one are different evidence.
    HAND_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{gltf_module}}/src/test/scala 2>/dev/null | wc -l | tr -d ' ')
    echo "hand-written munit in {{gltf_module}}/src/test/scala: $HAND_TESTS"
    [ "$HAND_TESTS" = "0" ] && echo "!! the hand-written suite is GONE — the port's only cover for the loader would be missing"
    ALL_TESTS=$((MUNIT_TESTS + HAND_TESTS))

    DEPS="{{gltf_deps}}"

    echo
    break_residue {{gltf_module}}/src_managed

    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{gdx_module}}/src_managed/main/scala {{gltf_module}}/src_managed/main/scala \
      {{gltf_module}}/src_managed/test/scala {{gltf_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gltfmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gltfmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/gltfmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/gltfmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/gltfmeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gltfmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/gltfmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{gltf_module}}/src_managed/main/scala \
        {{gltf_module}}/src_managed/test/scala {{gltf_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gltfrun.txt
      # Reconciled against the SUM: both source sets are on the one invocation, so an outcome
      # count that matched only the ported half would report success for a hand-written suite that
      # never ran (CLAUDE.md §5.1).
      reconcile_outcomes "$MEASURE_TMP"/gltfrun.txt "$ALL_TESTS"
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # All three maps: only the library's own can anchor a failure on the member that threw, only
      # the suite's can name the test, and libGDX's is passed because a stack that reaches the base
      # is exactly what a dependent's failure looks like.
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/gltfrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/gltfmeasure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$TREPORT"

# ---------------------------------------------------------------------------------------------
# libgdx-screenmanager, compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port of the same shape as Ashley's and anim8's — every one of its 22 files resolves
# against libGDX and the collection shims are vendored by libgdx-core, so both source sets are on
# one scala-cli invocation and this lane must run AFTER `gdx-measure` has re-emitted the base.
#
# TWO THINGS THIS LANE HAS THAT NO OTHER ONE DOES:
#
#  - **A HAND-WRITTEN SOURCE SET IN `src/main`.** Every other port's `src/main` is empty. This one
#    ships Scala for ten guacamole types (`com.github.crykn.guacamole:gdx`, a separate upstream this
#    corpus resolves against and does not port), which `TypeRedirectTransform` re-points the emitted
#    references at. They are on the compile because without them the port does not have a
#    `NestableFrameBuffer` — the type upstream depends on guacamole FOR.
#  - **A MIGRATED TEST COUNT OF ZERO THAT IS NOT AN OMISSION.** Upstream ships 12 `@Test`, and 10 of
#    them boot `gdx-backend-headless` or call `Mockito.mockStatic`/`spy` on the type under test. No
#    libGDX backend is ported and neither is bytecode instrumentation portable, so there is no
#    `ScreensTestMigrate`. The block below prints upstream, emitted and hand-written side by side
#    rather than letting `0` read as agreement — the same rule `anim8-measure` follows, and the day
#    a backend is ported it is this block that says the 12 became reachable.
# ---------------------------------------------------------------------------------------------
[doc("libgdx-screenmanager, compiled WITH libGDX core (a dependent port)")]
screens-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{screens_src}}"
    REPORT="$ROOT/port-report/ScreensMigrate"

    OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.screens.ScreensMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! ScreensMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- ScreensMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"

    echo
    echo "-- test discovery --"
    JAVA_TESTS=$(java_test_count {{screens_src}}/src/test)
    HAND_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{screens_module}}/src/test/scala 2>/dev/null | wc -l | tr -d ' ')
    EMITTED_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{screens_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    echo "@Test in upstream java: $JAVA_TESTS   emitted by this port: $EMITTED_TESTS"
    echo "  (10 of the 12 need gdx-backend-headless — NO libGDX backend is ported — or Mockito"
    echo "   mockStatic/spy over the type under test, which is JVM bytecode instrumentation and"
    echo "   not portable to the Scala.js/Native targets this port exists for. See PROGRESS.md.)"
    echo "hand-written munit in {{screens_module}}/src/test/scala: $HAND_TESTS"
    [ "$JAVA_TESTS" != "12" ] && echo "!! UPSTREAM'S @Test COUNT MOVED ($JAVA_TESTS, was 12) — re-read whether the suite is now migratable"
    [ "$HAND_TESTS" = "0" ] && echo "!! NO BEHAVIOURAL GATE — this port would compile and prove nothing (CLAUDE.md §3)"

    echo
    break_residue {{screens_module}}/src_managed
    echo "-- hand-written support sources (CLAUDE.md §5.5: src/ is the hand-written half) --"
    echo "$(find {{screens_module}}/src/main/scala -name '*.scala' | wc -l | tr -d ' ') file(s), $(cat $(find {{screens_module}}/src/main/scala -name '*.scala') | wc -l | tr -d ' ') lines — the guacamole replacements TypeRedirectTransform points at"

    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a
    # port that does not compile — a false NEGATIVE on the headline number.
    DEPS="{{screens_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{gdx_module}}/src_managed/main/scala {{screens_module}}/src_managed/main/scala \
      {{screens_module}}/src/main/scala {{screens_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/screensmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/screensmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/screensmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/screensmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/screensmeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/screensmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/screensmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{screens_module}}/src_managed/main/scala \
        {{screens_module}}/src/main/scala {{screens_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/screensrun.txt
      reconcile_outcomes "$MEASURE_TMP"/screensrun.txt "$HAND_TESTS"
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps, and no `test=` map: the suite is hand-written, so no srcmap can anchor a
      # test FRAME on a Java origin — but a failure inside the LIBRARY still resolves through this
      # port's map, and one that reaches the base resolves through libGDX's.
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/screensrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/screensmeasure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# gdx-vfx, compiled TOGETHER with the ported libGDX core.
#
# A DEPENDENT port with the same shape as anim8's — every one of its 44 files resolves against
# libGDX, the collection shims are vendored by libgdx-core, so both source sets must be on the same
# scala-cli invocation and this lane must run AFTER `gdx-measure` has re-emitted the base.
#
# THE TEST STORY, stated rather than assumed: gdx-vfx ships NO test source set. The `@Test` census
# below runs over the WHOLE upstream checkout (library, gwt backend and the 74-file demo alike) and
# is expected to be 0 — this is the third corpus library with no upstream suite and the only one
# where the zero is total rather than "the test directory holds demos". The behavioural gate is
# therefore the HAND-WRITTEN MUnit suite committed under `vfx-core/src/test/scala` (CLAUDE.md §5.5:
# `src/` is the hand-written half of a port). Both numbers are printed, because `0 == 0` must not
# read as agreement — a suite with no discoverable tests runs ZERO and reports SUCCESS, which is
# the exact failure `java_test_count` exists to catch.
# ---------------------------------------------------------------------------------------------
[doc("gdx-vfx, compiled WITH libGDX core (a dependent port)")]
vfx-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{vfx_src}}"
    REPORT="$ROOT/port-report/VfxMigrate"

    OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.vfx.VfxMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! VfxMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- VfxMigrate (every line it printed) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"

    echo
    echo "-- test discovery --"
    JAVA_TESTS=$(java_test_count {{vfx_src}})
    HAND_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{vfx_module}}/src/test/scala 2>/dev/null | wc -l | tr -d ' ')
    EMITTED_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{vfx_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    echo "@Test in upstream java (WHOLE checkout): $JAVA_TESTS (gdx-vfx ships no test source set — nothing to port)"
    echo "hand-written munit in {{vfx_module}}/src/test/scala: $HAND_TESTS   emitted: $EMITTED_TESTS"
    [ "$JAVA_TESTS" != "0" ] && echo "!! UPSTREAM NOW HAS A SUITE — $JAVA_TESTS @Test method(s) that this port does not migrate; add a VfxTestMigrate"
    [ "$HAND_TESTS" = "0" ] && echo "!! NO BEHAVIOURAL GATE — this port would compile and prove nothing (CLAUDE.md §3)"

    echo
    break_residue {{vfx_module}}/src_managed

    echo "-- compile --"
    DEPS="{{vfx_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{gdx_module}}/src_managed/main/scala {{vfx_module}}/src_managed/main/scala {{vfx_module}}/src/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/vfxmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/vfxmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/vfxmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/vfxmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/vfxmeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/vfxmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/vfxmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false $DEPS -Duser.language=en -Duser.country=US \
        {{gdx_module}}/src_managed/main/scala {{vfx_module}}/src_managed/main/scala {{vfx_module}}/src/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/vfxrun.txt
      reconcile_outcomes "$MEASURE_TMP"/vfxrun.txt "$HAND_TESTS"
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      # BOTH ports' maps. There is no `test=` map: the suite is hand-written, so no srcmap can
      # anchor a test FRAME on a Java origin — but a failure inside the LIBRARY still resolves
      # through vfx's own map, and one that reaches the base resolves through libGDX's, which is
      # exactly what a dependent's failure looks like.
      correlate "$REPORT/run-latest" --tests "$MEASURE_TMP"/vfxrun.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/vfxmeasure.txt \
        --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# simple-graphs + its suite — the same gate as `gdx-measure`.
#
# simple-graphs is a VENDORED-runtime port (RuntimeMode.Vendored): the shim family it retypes onto
# is written into its own source set, so the compile is over one directory and needs no classpath.
# ---------------------------------------------------------------------------------------------
[doc("simple-graphs + its suite")]
sg-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{sg_src}}"
    REPORT="$ROOT/port-report/SimpleGraphsMigrate"

    TREPORT="$ROOT/port-report/SimpleGraphsTestMigrate"

    for M in SimpleGraphsMigrate SimpleGraphsTestMigrate; do
      OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.simplegraphs.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
      if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
        echo "!! $M DID NOT RUN — refusing to measure stale output"
        grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
        exit 1
      fi
      echo "-- $M (ALL checks, untruncated, as the migration printed them) --"
      sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
      echo
    done

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"
    show_check_report "$TREPORT"

    echo
    echo "-- test discovery --"
    # Both frameworks summed, as in `gdx-test-measure`: a ported suite is MUnit and any residue is still
    # JUnit, so counting one under-reports by every converted suite — in the safe-looking direction. A
    # suite with no discoverable tests runs ZERO and reports SUCCESS.
    JAVA_TESTS=$(java_test_count {{sg_src}}/src/test)
    JUNIT_LEFT=$(grep -rh "@org.junit.Test\|@Test" {{sg_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    MUNIT_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' {{sg_module}}/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
    SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
    echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
    [ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! TESTS LOST — $((JAVA_TESTS - SCALA_TESTS)) of $JAVA_TESTS would never run, and the suite would report success"

    echo
    break_residue {{sg_module}}/src_managed

    echo "-- compile --"
    # NOTE the ANSI strip. Dropped once, and `grep -cE '^-- .*Error'` then matched nothing because every
    # line begins with a colour escape — reporting 0 errors for a port that had 20. A false NEGATIVE on
    # the project's headline number is the worst failure a measure lane can have.
    # BOTH source sets on one invocation: the main port is RuntimeMode.Vendored, so the shims live in
    # `src_managed/main` and the suite links against them there. Compiling either alone measures nothing.
    DEPS="{{sg_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{sg_module}}/src_managed/main/scala {{sg_module}}/src_managed/test/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/sgmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/sgmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/sgmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/sgmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/sgmeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/sgmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/sgmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # -------------------------------------------------------------------------------------------
    # RUN them. Compiling a suite measures nothing about behaviour: CLAUDE.md §4.4 lists ten java forms
    # that translate to VALID scala meaning something else, and not one moves the count above. For this
    # library the live question is ORDER — `Collections.sort(list, cmp)` and `Comparator.reversed()` both
    # compile whichever way round they sort.
    # -------------------------------------------------------------------------------------------
    if [ "$ERRORS" = "0" ]; then
      echo
      echo "-- run --"
      scala-cli test --scala {{scala_version}} --server=false $DEPS -Duser.language=en -Duser.country=US \
        {{sg_module}}/src_managed/main/scala {{sg_module}}/src_managed/test/scala \
        2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/sgrun.txt
      reconcile_outcomes "$MEASURE_TMP"/sgrun.txt "$MUNIT_TESTS"
      echo
      echo "-- correlation: test failures located to members and Java origins --"
      correlate "$TREPORT/run-latest" --tests "$MEASURE_TMP"/sgrun.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
    else
      echo
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/sgmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv" \
        --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
      echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
    fi

    headline "$ERRORS" "$TREPORT"

# ---------------------------------------------------------------------------------------------
# noise4j — the same gate as `sg-measure`, minus the run.
#
# noise4j is a VENDORED-runtime port and a STANDALONE base port: one source set, no resolution
# roots, no dependencies, so the compile is over one directory with no classpath at all.
#
# IT HAS NO RUN PHASE, and that is the one thing to read before quoting a number from it. noise4j
# ships no test sources — `find` over the upstream tree returns `src/` and `examples/` (nine PNGs)
# and nothing else — so there is no Java suite to put through the pipeline and no `test.conf`. The
# lane therefore ASSERTS that fact instead of skipping the discovery block: a lane that silently
# has no tests is indistinguishable from a lane whose tests all vanished, which is the failure
# `java_test_count` exists to catch. Everything CLAUDE.md §4.4 lists is UNMEASURED for this port;
# `PROGRESS.md` §noise4j says so in the same words.
# ---------------------------------------------------------------------------------------------
[doc("noise4j — emit, checks, break residue, compile, correlate (no test set upstream)")]
noise4j-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{n4j_src}}"
    REPORT="$ROOT/port-report/Noise4jMigrate"

    # ABORT if the migration itself did not run, or the lane measures the PREVIOUS emit and reports a
    # stale number as a result.
    OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.noise4j.Noise4jMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$OUT"; then
      echo "!! Noise4jMigrate DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
      exit 1
    fi
    echo "-- Noise4jMigrate (ALL checks, untruncated, as the migration printed them) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$OUT"
    echo

    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"

    echo
    echo "-- test discovery --"
    # ASSERTED, not omitted. noise4j has no test sources; if it ever gains one, this lane says so
    # loudly rather than continuing to report a port with no behavioural evidence as complete.
    JAVA_TESTS=$(java_test_count {{n4j_src}}/src {{n4j_src}}/test {{n4j_src}}/tests)
    echo "@Test in Java: $JAVA_TESTS   emitted test files: 0 (this port has no test source set)"
    [ "$JAVA_TESTS" != "0" ] && echo "!! UPSTREAM NOW HAS $JAVA_TESTS @Test — this port has no test.conf, so none of them runs; add one (CLAUDE.md §3)"
    echo "   (no run phase: every CLAUDE.md §4.4 form is UNMEASURED for this port — see PROGRESS.md §noise4j)"

    echo
    break_residue {{n4j_module}}/src_managed

    echo "-- compile --"
    # NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
    # that does not compile — a false NEGATIVE on the headline number.
    DEPS="{{n4j_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{n4j_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/n4jmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/n4jmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/n4jmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/n4jmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/n4jmeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/n4jmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/n4jmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    echo
    echo "-- correlation: every error located to its member and its Java origin --"
    # Run WHETHER OR NOT it compiled. With no suite there is no second thing to correlate, so the
    # compile output is the only diagnostic this port has and it is always worth attributing.
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/n4jmeasure.txt \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"

# ---------------------------------------------------------------------------------------------
# jbump — the same gate as `sg-measure`, minus the one stage jbump cannot have.
#
# jbump ships NO TEST SUITE. Its `test` gradle module is a runnable libGDX demo (`TestBump extends
# ApplicationAdapter`, LWJGL3 + shapedrawer, driven by mouse and WASD) and declares zero `@Test`
# methods, so there is nothing for this engine to port and this port's evidence stops at the
# compiler. CLAUDE.md §3 is explicit about what that leaves unmeasured, so the lane does two things
# rather than quietly omitting the stage:
#
#   * it RE-DERIVES the zero on every run, with `java_test_count` over the whole upstream checkout
#     rather than over the ported module — a claim in a document rots, a number in a lane does not,
#     and the day upstream adds a suite this is the line that says so;
#   * it says out loud, in the run, that the behavioural gate is absent. A lane that simply had no
#     test stage would read as a lane whose tests passed.
# ---------------------------------------------------------------------------------------------
[doc("jbump — emit, checks, break residue, compile, correlate (no suite upstream: see the lane)")]
jbump-measure:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh

    # The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
    # and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
    write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/{{jbump_src}}"
    REPORT="$ROOT/port-report/JbumpMigrate"

    # ABORT if the migration itself did not run. Piping straight into `grep wrote` discards the exit
    # status, so an engine that failed to COMPILE would leave the lane measuring the PREVIOUS emit.
    MIGRATE_OUT=$(sbt -client "{{corpus}}/runMain balticporter.corpus.jbump.JbumpMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
    if ! grep -qE "wrote [0-9]+ Scala files" <<<"$MIGRATE_OUT"; then
      echo "!! MIGRATION DID NOT RUN — refusing to measure stale output"
      grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$MIGRATE_OUT" | head -20
      exit 1
    fi

    echo "-- migration (ALL checks, untruncated, as the migration printed them) --"
    sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$MIGRATE_OUT"

    echo
    echo "-- checks: persisted, untruncated, diffed against the baseline --"
    show_check_report "$REPORT"

    echo
    echo "-- test discovery --"
    JAVA_TESTS=$(java_test_count {{jbump_upstream}})
    echo "@Test in the WHOLE jbump upstream checkout: $JAVA_TESTS"
    if [ "$JAVA_TESTS" = "0" ]; then
      echo "   NO SUITE UPSTREAM — nothing for the engine to port, so the behavioural gate for this"
      echo "   port is the DIFFERENTIAL PROBE below, not a ported suite. It is hand-written and must"
      echo "   never be counted as a ported test (CLAUDE.md §3); PROGRESS.md §jbump says what it covers."
    else
      echo "!! A SUITE HAS APPEARED UPSTREAM — $JAVA_TESTS @Test method(s). This port has no test"
      echo "   source set; add corpus/ports/jbump/test.conf (\`base = \"main.conf\"\`) and a lane stage."
    fi

    echo
    break_residue {{jbump_module}}/src_managed

    echo "-- compile --"
    # NOTE the ANSI strip — dropped once, and every line then began with an escape, reporting 0
    # errors for a port that had 20. A false NEGATIVE on the headline number is the worst failure a
    # measure lane can have.
    DEPS="{{jbump_deps}}"
    scala-cli compile --scala {{scala_version}} --server=false $DEPS \
      {{jbump_module}}/src_managed/main/scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/jbumpmeasure.txt
    CLI_STATUS=${PIPESTATUS[0]}
    ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/jbumpmeasure.txt)
    compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/jbumpmeasure.txt
    echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/jbumpmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/jbumpmeasure.txt))"
    grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/jbumpmeasure.txt | sort | uniq -c | sort -rn | head
    echo "-- bare (uncoded) errors by message --"
    grep -A1 '^-- Error:' "$MEASURE_TMP"/jbumpmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

    # -------------------------------------------------------------------------------------------
    # RUN it — differentially, against the upstream Java.
    #
    # This is the stage a library with no suite would otherwise not have, and CLAUDE.md §3 says what
    # skipping it would cost: a green compile is not evidence, and jbump contains six of §4.4's ten
    # forms (reference `==`, `x++` as a value, `break`/`continue`, a `switch`, a `static {}` block,
    # a `super`-less secondary-constructor funnel) — none of which moves the count above.
    #
    # `corpus/ports/jbump/probe/{ProbeJava.java,Probe.scala}` walk the SAME scenario, one against
    # `{{jbump_src}}` and one against the emitted port, and the gate is that their transcripts are
    # IDENTICAL. No expected value is written anywhere, so none can be written down wrong: the
    # upstream Java is the authority and the diff is the whole assertion.
    # -------------------------------------------------------------------------------------------
    echo
    if [ "$ERRORS" != "0" ]; then
      echo "-- correlation: every error located to its member and its Java origin --"
      correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/jbumpmeasure.txt \
        --srcmap "$REPORT/run-latest/srcmap.tsv"
      echo "(not running the differential probe: the port does not compile — a probe that cannot run is not a probe that agreed)"
      headline "$ERRORS" "$REPORT"
      exit 0
    fi

    echo "-- differential probe: emitted Scala vs upstream Java, same scenario --"
    PROBE="$MEASURE_TMP/jbump-probe"
    rm -rf "$PROBE"; mkdir -p "$PROBE/classes"
    javac -nowarn -d "$PROBE/classes" -sourcepath {{jbump_src}} corpus/ports/jbump/probe/ProbeJava.java \
      > "$PROBE/javac.txt" 2>&1
    if [ "$?" != "0" ]; then
      echo "!! PROBE DID NOT COMPILE against the upstream Java — the AUTHORITY half is broken, so the"
      echo "   port half proves nothing. Fix corpus/ports/jbump/probe/ProbeJava.java:"
      grep -v '^Note:' "$PROBE/javac.txt" | head -20 | sed 's/^/     /'
      exit 1
    fi
    java -cp "$PROBE/classes" ProbeJava > "$PROBE/java.txt" 2>&1
    JAVA_ST=$?
    scala-cli run --scala {{scala_version}} --server=false $DEPS \
      {{jbump_module}}/src_managed/main/scala corpus/ports/jbump/probe/Probe.scala \
      2>&1 | sed 's/\x1b\[[0-9;]*m//g' \
      | grep -vE '^Warning: setting |deprecation warning|^[0-9]+ warning' > "$PROBE/scala.txt"
    SCALA_ST=${PIPESTATUS[0]}
    # Both halves must have RUN. A crashed probe whose partial output happens to match is the §3
    # false green one artifact later, so the exit statuses gate before the diff does.
    if [ "$JAVA_ST" != "0" ] || [ "$SCALA_ST" != "0" ]; then
      echo "!! PROBE DID NOT RUN (java exit $JAVA_ST, scala exit $SCALA_ST) — refusing to diff partial output"
      tail -20 "$PROBE/scala.txt" | sed 's/^/     /'
      exit 1
    fi
    LINES=$(grep -c "" "$PROBE/java.txt")
    if diff -u "$PROBE/java.txt" "$PROBE/scala.txt" > "$PROBE/diff.txt"; then
      echo "probe: $LINES transcript line(s), emitted Scala IDENTICAL to upstream Java"
    else
      echo "!! PROBE DIVERGED — the port behaves differently from the library it was made from."
      echo "   Left = upstream Java (the authority), right = emitted Scala:"
      sed 's/^/     /' "$PROBE/diff.txt"
      exit 1
    fi

    echo
    echo "-- correlation: nothing to locate (0 errors); the source map is still published --"
    correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/jbumpmeasure.txt \
      --srcmap "$REPORT/run-latest/srcmap.tsv"

    headline "$ERRORS" "$REPORT"


# ---------------------------------------------------------------------------------------------
# Every lane, SERIALLY, in dependency order — never in parallel.
#
# Each lane re-emits into `src_managed/`, so `gdx-test-measure` and `ashley-measure` compile against
# what `gdx-measure` just wrote. Run concurrently they would measure each other's half-written
# output; run out of order they would measure the previous engine's. A lane that fails stops the
# sequence, for the same reason every lane aborts on a migration that did not run: the next number
# would be stale, and a stale number reads exactly like a result.
# ---------------------------------------------------------------------------------------------
[doc("every lane, SERIALLY, in dependency order — never in parallel")]
measure-all:
    #!/usr/bin/env bash
    cd "{{root}}"
    for lane in gdx-measure gdx-test-measure ashley-measure anim8-measure gltf-measure vfx-measure sg-measure noise4j-measure jbump-measure screens-measure; do
      echo
      echo "################################################################## just $lane"
      if ! {{just_executable()}} "$lane"; then
        echo "!! $lane FAILED — stopping; the remaining lanes would compile against a stale emit"
        exit 1
      fi
    done

# ---------------------------------------------------------------------------------------------
# Per-port `decisions.tsv` row counts by KIND, for every port that has a run.
#
# The decision log is NOT baselined (`baseline-accept` promotes findings/counts/members/tests/
# port-map and deliberately not this one), so nothing else in the workflow states its size. A
# provenance artifact whose row count nobody prints is one nobody notices going empty.
# ---------------------------------------------------------------------------------------------
[doc("decisions.tsv row counts by kind, every port")]
decision-counts:
    #!/usr/bin/env bash
    cd "{{root}}"
    for d in port-report/*/; do
      f="$d/run-latest/decisions.tsv"
      [ -f "$f" ] || continue
      n=$(( $(grep -vc '^#' "$f") ))
      echo "$(basename "$d"): $n row(s)"
      grep -v '^#' "$f" | cut -f1 | sort | uniq -c | sed 's/^/    /'
    done

# ---------------------------------------------------------------------------------------------
# `members.tsv` against its committed baseline, for every port with a run (or just PORT).
#
# The member digest is the BLAST RADIUS and it is available before a compile (CLAUDE.md §5.1):
# identical files mean the emitted text is byte-for-byte unchanged, which is a stronger revert
# check than any count — no check count moves for most transform regressions. Exits non-zero if
# anything moved, so it is usable as a gate.
#
# A MISSING INPUT IS FATAL, and this is the half that was wrong: both sides were required with
# `[ -f "$b" ] && [ -f "$r" ] || continue`, so a port named on the command line whose baseline (or
# whose run) did not exist printed NOTHING and exited 0 — the §5.1 rule ("a `--tests` path that
# does not exist is fatal, never a headline of 0 passing, 0 failing") failing inside the one gate
# that is supposed to catch what no count moves. In a fresh checkout `run-latest/` is gitignored,
# so `just members-unchanged SimpleGraphsMigrate` reported a clean blast radius for a comparison
# that never happened.
#
# The two modes differ deliberately, and only in what "considered" means:
#
#   * a port NAMED on the command line is an assertion about that port — every missing side is
#     fatal, and an unknown name lists the ports there are;
#   * the SWEEP considers every port directory. One that has run and has no baseline is fatal
#     wherever it appears (an artifact exists and nothing checks it — the false green). One that
#     has a baseline and has not run in this checkout is printed as NOT COMPARED, because a lane
#     nobody has run here is not a regression; the sweep fails only if that describes them ALL,
#     which is the "printed nothing, exited 0" state this recipe exists to refuse.
# ---------------------------------------------------------------------------------------------
[doc("members.tsv against its committed baseline (all ports, or one)")]
members-unchanged port="":
    #!/usr/bin/env bash
    cd "{{root}}"
    R="{{report_root}}"
    if [ ! -d "$R" ]; then
      echo "!! no report directory at $R — nothing has been measured in this checkout"
      exit 1
    fi
    known=$(ls -1 "$R" 2>/dev/null | tr '\n' ' ')
    if [ -n "{{port}}" ] && [ ! -d "$R/{{port}}" ]; then
      echo "!! NO SUCH PORT: {{port}}"
      echo "   ports with a report directory: $known"
      exit 1
    fi
    rc=0; compared=0
    for d in "$R"/*/; do
      p="$(basename "$d")"
      [ -z "{{port}}" ] || [ "$p" = "{{port}}" ] || continue
      b="$d/baseline/members.tsv"; r="$d/run-latest/members.tsv"
      if [ -f "$b" ] && [ -f "$r" ]; then
        n=$(diff "$b" "$r" | grep -c '^[<>]')
        echo "$p: $n member(s) changed"
        compared=$((compared + 1))
        [ "$n" = "0" ] || rc=1
      elif [ -f "$r" ]; then
        echo "!! $p: MEASURED BUT UNBASELINED — run-latest/members.tsv exists, baseline/members.tsv does not."
        echo "   Nothing is comparing this port's emitted text. Accept one: just baseline-accept $p"
        rc=1
      elif [ -f "$b" ]; then
        echo "   $p: NOT COMPARED — no run-latest/members.tsv (its lane has not run in this checkout)"
        [ -z "{{port}}" ] || { echo "!! and you asked about $p specifically — run its measure lane first"; rc=1; }
      else
        echo "   $p: NOT COMPARED — neither a baseline nor a run"
        [ -z "{{port}}" ] || { echo "!! and you asked about $p specifically"; rc=1; }
      fi
    done
    if [ "$compared" = "0" ] && [ "$rc" = "0" ]; then
      echo "!! NOTHING WAS COMPARED — a blast radius nobody computed is not a blast radius of zero"
      rc=1
    fi
    exit $rc

# ---------------------------------------------------------------------------------------------
# THE DEBUGGING SURFACE — CLAUDE.md §4.6, reachable.
#
# Every tool below already existed; what did not exist was a way to reach it without knowing which
# main class or which hand-written file to create. That is a real cost and not a convenience: the
# consumer of this engine is an agent in ANOTHER repository (§4.45) with none of this session's
# folklore, and a diagnostic it cannot find is a diagnostic it re-invents — by copying
# `src_managed`, adding a `println` and rebuilding, which is exactly what these replaced.
#
# The layering is the §4.6 one and these recipes do not reimplement it: `debug-flags` renders
# `DebugFlags.resolution`, the same fold `DebugFlags.get` performs, so the explanation and the
# behaviour cannot drift apart. `debug-set`/`debug-clear` only edit the file the operator owns.
# ---------------------------------------------------------------------------------------------
[doc("WHICH layer defines each balticporter.* flag right now (and what PORT's last run saw)")]
debug-flags PORT="":
    #!/usr/bin/env bash
    cd "{{root}}"
    mkdir -p .balticporter/tmp
    CAP=".balticporter/tmp/debug-flags-$$.txt"
    ARGS="--root {{bp_root}}"
    [ -n "{{PORT}}" ] && ARGS="$ARGS --port {{PORT}}"
    sbt -client "{{core_project}}/runMain balticporter.tir.DebugFlagsMain $ARGS" 2>&1 |
      sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' > "$CAP"
    st=${PIPESTATUS[0]}
    # from the report's own first line, so sbt's preamble is dropped without dropping the report:
    # every line of it that matters starts with `[balticporter]` or two spaces, and a blanket
    # `grep -v '^\['` would eat the headline itself.
    sed -n '/flag resolution under/,$p' "$CAP" | grep -vE '^\[(info|warn|error|success)\]'
    if [ "$st" != "0" ]; then
      echo "!! debug-flags DID NOT RUN — sbt exited $st; its output:"
      tail -20 "$CAP" | sed 's/^/     /'
      rm -f "$CAP"; exit 1
    fi
    rm -f "$CAP"

[doc("write one flag into .balticporter/debug.properties — the hand-written layer, which beats run.properties (a -D beats both, but never reaches a forked migration)")]
debug-set KEY VALUE:
    #!/usr/bin/env bash
    cd "{{root}}"
    F="{{bp_root}}/.balticporter/debug.properties"
    mkdir -p "$(dirname "$F")"
    K="{{KEY}}"
    case "$K" in balticporter.*) ;; *) K="balticporter.$K" ;; esac
    [ -f "$F" ] || printf '# hand-written debug flags (CLAUDE.md §4.6) — `just debug-clear` removes them\n' > "$F"
    # IDEMPOTENT, and that is not tidiness: java.util.Properties keeps the LAST occurrence, so an
    # appended duplicate makes the effective value depend on the order of a file nobody reads.
    # Exact-prefix match rather than a regex — a key is full of dots, and `.` matches anything.
    awk -v k="$K=" 'substr($0, 1, length(k)) != k' "$F" > "$F.tmp" && mv "$F.tmp" "$F"
    printf '%s=%s\n' "$K" "{{VALUE}}" >> "$F"
    echo "$F now holds:"
    grep -v '^#' "$F" | sed 's/^/  /'
    echo "(confirm what a run will resolve:  just debug-flags)"

[doc("remove one flag from .balticporter/debug.properties, or ALL of them (no KEY)")]
debug-clear KEY="":
    #!/usr/bin/env bash
    cd "{{root}}"
    F="{{bp_root}}/.balticporter/debug.properties"
    if [ ! -f "$F" ]; then echo "no debug flags set ($F is absent)"; exit 0; fi
    if [ -z "{{KEY}}" ]; then
      # The whole file GOES. A stale debug flag is invisible — it moves no count, fails no check,
      # and quietly changes what every later run in this checkout emits — so "clear" has to mean
      # a state an operator can verify at a glance, and absent is that state.
      echo "removing all hand-written debug flags:"
      grep -v '^#' "$F" | sed 's/^/  -/'
      rm -f "$F"
    else
      K="{{KEY}}"
      case "$K" in balticporter.*) ;; *) K="balticporter.$K" ;; esac
      awk -v k="$K=" 'substr($0, 1, length(k)) != k' "$F" > "$F.tmp" && mv "$F.tmp" "$F"
      echo "removed $K; $F now holds:"
      grep -v '^#' "$F" | sed 's/^/  /'
      # …and an empty file is removed too, so `.balticporter/` never carries a file whose only
      # content is a header that reads as configuration.
      if [ -z "$(grep -v '^#' "$F")" ]; then rm -f "$F"; echo "(file removed — nothing left in it)"; fi
    fi

[doc("one type's TIR + emitted Scala, around a phase boundary (ROOT is a JAVA source root)")]
debug-emit ROOT FQN PHASES="" *FLAGS:
    #!/usr/bin/env bash
    cd "{{root}}"
    # Absolute, always: the main resolves a relative path against `balticporter.root`, which for an
    # UNFORKED `engine/runMain` is the sbt server's directory and not this shell's.
    R="{{ROOT}}"; case "$R" in /*) ;; *) R="$(pwd)/$R" ;; esac
    ARGS="--root $R --fqn {{FQN}} --scala"
    # A phase list means "show me this type ACROSS that boundary" — run the phases and bracket each
    # one. Without it the type is printed as the frontend built it, which is the other question.
    [ -n "{{PHASES}}" ] && ARGS="$ARGS --phases {{PHASES}} --dump-before {{PHASES}} --dump-after {{PHASES}}"
    echo "+ {{core_project}}/runMain balticporter.runner.DebugEmit $ARGS {{FLAGS}}"
    echo "  (add --fast to parse ONLY the included files: seconds instead of minutes on a large"
    echo "   library, at the cost of resolution fidelity; --include <substr> narrows what is converted)"
    mkdir -p .balticporter/tmp
    CAP=".balticporter/tmp/debug-emit-$$.txt"
    sbt -client "{{core_project}}/runMain balticporter.runner.DebugEmit $ARGS {{FLAGS}}" 2>&1 |
      sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' > "$CAP"
    st=${PIPESTATUS[0]}
    # from the tool's own first line. `index`, not a regex: the marker is `[debug-emit]`, and as a
    # regex those brackets are a CHARACTER CLASS that matches most of a stack trace.
    awk 'f || index($0, "[debug-emit]") > 0 { f = 1; print }' "$CAP" | grep -vE '^\[(info|warn|success)\]'
    if [ "$st" != "0" ]; then
      echo "!! debug-emit DID NOT RUN — sbt exited $st; its output:"
      tail -20 "$CAP" | sed 's/^/     /'
      rm -f "$CAP"; exit 1
    fi
    rm -f "$CAP"

[doc("CorrelateMain standalone — a compiler or test log you produced BY HAND, joined to members")]
correlate OUT *ARGS:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    export CORE_PROJECT="{{core_project}}"
    . scripts/_lib.sh
    if [ -z "{{ARGS}}" ]; then
      echo "usage: just correlate <out-dir> [--scalac <file>] [--tests <file>] [--srcmap [scope=]<file>]…"
      echo
      echo "  CLAUDE.md §5.1: never open an emitted file to work out which member an error is in."
      echo "  The lanes do this for you; this is the same command for a compile you ran by hand."
      echo
      echo "  scala-cli compile --scala 3.8.4 --server=false <port>/src_managed/main/scala > /tmp/c.txt"
      echo "  just correlate port-report/<Port>/run-latest --scalac /tmp/c.txt \\"
      echo "       --srcmap port-report/<Port>/run-latest/srcmap.tsv"
      echo
      echo "  --baseline defaults to <out>/../baseline, which is where the diffs come from."
      exit 2
    fi
    abs() { case "$1" in /*) printf '%s' "$1" ;; *) printf '%s' "$ROOT/$1" ;; esac; }
    OUT="$(abs "{{OUT}}")"
    # EVERY path argument is absolutised, and this is not politeness. `engine/runMain` is FORKED,
    # so the correlator's working directory is the SUBPROJECT: a relative `--scalac out.txt` that
    # reads correctly in this shell resolves to `engine/out.txt` and the run dies naming a file
    # nobody wrote. The lanes never met it because they compose `$ROOT/…` throughout; a recipe an
    # operator types by hand meets it immediately (measured, first try).
    set -- {{ARGS}}
    A=(); prev=""
    for a in "$@"; do
      case "$prev" in
        --scalac|--tests|--markers|--baseline|--out) a="$(abs "$a")" ;;
        --srcmap) case "$a" in
                    main=*|test=*) a="${a%%=*}=$(abs "${a#*=}")" ;;
                    *)             a="$(abs "$a")" ;;
                  esac ;;
      esac
      A+=("$a"); prev="$a"
    done
    # The lanes' own helper, deliberately: one invocation path, one set of guards (a correlation
    # that did not happen must not render as an empty-but-tidy block).
    correlate "$OUT" "${A[@]}"

# ---------------------------------------------------------------------------------------------
# The debug recipes, proving themselves. No sbt, no ports, no network — it runs in seconds, so
# there is no reason for it to rot.
#
# What it covers is the half a Scala spec cannot: the SHELL. `debug-set` appending a duplicate key
# instead of replacing it, `debug-clear` leaving a header that reads as configuration, and
# `members-unchanged` exiting 0 on an input that does not exist are all defects in bash, and the
# last of them shipped. The engine-side halves (the precedence rule, the dump boundaries, the
# emitted text) are proven by DebugFlagsSpec, DebugFlagsMainSpec, PipelineDebugSpec and
# DebugEmitSpec — this is not a substitute for those.
# ---------------------------------------------------------------------------------------------
[doc("prove the debug recipes do what they say — set/clear/members-unchanged, in a temp root")]
debug-selfcheck:
    #!/usr/bin/env bash
    cd "{{root}}"
    T="$(mktemp -d)"
    trap 'rm -rf "$T"' EXIT
    fail=0
    ok()   { echo "  ok   $1"; }
    bad()  { echo "  FAIL $1"; fail=1; }
    want() { [ "$2" = "$3" ] && ok "$1" || bad "$1 (want [$3], got [$2])"; }
    J="{{just_executable()}}"
    F="$T/.balticporter/debug.properties"

    echo "-- debug-set / debug-clear (root=$T) --"
    BP_ROOT="$T" $J debug-set skipPhases '*' > /dev/null
    want "debug-set writes the file"            "$(grep -c . "$F" 2>/dev/null)" "2"
    want "…with the balticporter. prefix added" "$(grep -c '^balticporter.skipPhases=\*$' "$F")" "1"

    BP_ROOT="$T" $J debug-set skipPhases 'collections' > /dev/null
    want "debug-set is IDEMPOTENT — one entry"  "$(grep -c '^balticporter.skipPhases=' "$F")" "1"
    want "…and it is the NEW value"             "$(grep -c '^balticporter.skipPhases=collections$' "$F")" "1"

    BP_ROOT="$T" $J debug-set balticporter.tracePhases true > /dev/null
    want "an already-prefixed key is not double-prefixed" "$(grep -c '^balticporter.tracePhases=true$' "$F")" "1"
    want "…beside the first, which survives"    "$(grep -vc '^#' "$F")" "2"

    BP_ROOT="$T" $J debug-clear skipPhases > /dev/null
    want "debug-clear KEY removes one flag"     "$(grep -c '^balticporter.skipPhases=' "$F")" "0"
    want "…and leaves the other"                "$(grep -c '^balticporter.tracePhases=true$' "$F")" "1"

    BP_ROOT="$T" $J debug-clear tracePhases > /dev/null
    [ -f "$F" ] && bad "an emptied debug.properties must be REMOVED, not left as a header" \
                || ok "an emptied debug.properties is removed"

    BP_ROOT="$T" $J debug-set dumpOnly p.Foo > /dev/null
    BP_ROOT="$T" $J debug-clear > /dev/null
    [ -f "$F" ] && bad "debug-clear with no KEY must remove the file" || ok "debug-clear with no KEY removes the file"
    BP_ROOT="$T" $J debug-clear > /dev/null 2>&1
    want "…and is idempotent on an absent file" "$?" "0"

    echo "-- correlate: no arguments is a USAGE, not a silent no-op --"
    out=$($J correlate some/out 2>&1); rc=$?
    want "correlate with no options exits 2"      "$rc" "2"
    case "$out" in *"§5.1"*) ok "…and points at the rule it serves" ;; *) bad "…rule: $out" ;; esac

    echo "-- _lib.sh: an unset CORE_PROJECT is FATAL, never a dead default --"
    # The lanes all export it; what this pins is the file's behaviour WITHOUT the Justfile, which is
    # how the old default (`core`) survived the module restructure — it named a project that no
    # longer existed, and the comment above it claimed the file was correct on its own.
    out=$( { unset CORE_PROJECT; . scripts/_lib.sh; correlate "$T/out"; } 2>&1 ); rc=$?
    want "correlate without CORE_PROJECT is fatal"  "$rc" "1"
    case "$out" in *CORE_PROJECT*Justfile*) ok "…and names the variable and where it is set" ;;
                   *) bad "…and names the variable and where it is set: $out" ;; esac
    case "$out" in *runMain*) bad "…it must not reach sbt at all" ;; *) ok "…before sbt is invoked" ;; esac

    echo "-- members-unchanged: a missing input is FATAL, and names the port --"
    R="$T/port-report"
    mkdir -p "$R/Both/baseline" "$R/Both/run-latest"
    printf 'a\tb\n' > "$R/Both/baseline/members.tsv"
    printf 'a\tb\n' > "$R/Both/run-latest/members.tsv"
    out=$(BP_REPORT="$R" $J members-unchanged Both); rc=$?
    want "an identical pair is 0 changed, exit 0" "$rc" "0"
    case "$out" in *"Both: 0 member(s) changed"*) ok "…and says so" ;; *) bad "…and says so: $out" ;; esac

    printf 'a\tc\n' > "$R/Both/run-latest/members.tsv"
    BP_REPORT="$R" $J members-unchanged Both > /dev/null 2>&1; rc=$?
    want "a moved member is exit 1"               "$rc" "1"

    out=$(BP_REPORT="$R" $J members-unchanged NoSuchPort 2>&1); rc=$?
    want "an unknown port is FATAL"               "$rc" "1"
    case "$out" in *"NO SUCH PORT: NoSuchPort"*) ok "…and names it" ;; *) bad "…and names it: $out" ;; esac

    mkdir -p "$R/NoBaseline/run-latest"; printf 'a\tb\n' > "$R/NoBaseline/run-latest/members.tsv"
    out=$(BP_REPORT="$R" $J members-unchanged NoBaseline 2>&1); rc=$?
    want "a run with NO BASELINE is fatal"        "$rc" "1"
    case "$out" in *"MEASURED BUT UNBASELINED"*) ok "…and says which state it is in" ;; *) bad "…state: $out" ;; esac

    mkdir -p "$R/NoRun/baseline"; printf 'a\tb\n' > "$R/NoRun/baseline/members.tsv"
    out=$(BP_REPORT="$R" $J members-unchanged NoRun 2>&1); rc=$?
    want "a NAMED port with no run is fatal"      "$rc" "1"
    case "$out" in *"NOT COMPARED"*) ok "…and says the comparison did not happen" ;; *) bad "…state: $out" ;; esac

    out=$(BP_REPORT="$T/nothing-here" $J members-unchanged 2>&1); rc=$?
    want "an absent report directory is fatal"    "$rc" "1"

    E="$T/empty-report"; mkdir -p "$E"
    out=$(BP_REPORT="$E" $J members-unchanged 2>&1); rc=$?
    want "a sweep that compares NOTHING is fatal" "$rc" "1"
    case "$out" in *"NOTHING WAS COMPARED"*) ok "…and says so" ;; *) bad "…says so: $out" ;; esac

    echo
    [ "$fail" = "0" ] && echo "debug-selfcheck: PASS" || { echo "debug-selfcheck: FAILED"; exit 1; }

# ---------------------------------------------------------------------------------------------
# The baseline half of the check report: list, show, diff and ACCEPT.
#
# <port> is a directory under port-report/, named after the migration program's main class
# (LibgdxCoreMigrate, LibgdxTestMigrate, …) — balticporter.tir.CheckReport derives it, so no
# migration program has to be told what it is called.
#
# Promotion is EXPLICIT, deliberately: `run-latest/` is overwritten by every run, `baseline/` moves
# only when a human accepts a step. That is golden-test discipline, and it is what makes
# "omissions 31->33" a fact rather than a memory (CLAUDE.md §5).
# ---------------------------------------------------------------------------------------------
[doc("every port: baseline size and last run")]
baseline-list:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    ROOT="$(pwd)"
    if [ ! -d "$ROOT/port-report" ]; then echo "no port-report/ yet — run a migration first"; exit 0; fi
    for d in "$ROOT"/port-report/*/; do
      name="$(basename "$d")"
      b="no baseline"; [ -f "$d/baseline/counts.tsv" ] && b="baseline: $(grep -vc '^#' "$d/baseline/findings.tsv" || echo 0) findings"
      r="no run"; [ -f "$d/run-latest/subject.txt" ] && r="$(cat "$d/run-latest/subject.txt")"
      printf '%-24s %-22s %s\n' "$name" "$b" "$r"
    done

[doc("the run's full report")]
baseline-show PORT:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    cat "port-report/{{PORT}}/run-latest/report.md"

[doc("the run against the committed baseline")]
baseline-diff PORT:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    cat "port-report/{{PORT}}/run-latest/diff.txt"

[doc("promote run-latest to baseline (commit it with the change)")]
baseline-accept PORT:
    #!/usr/bin/env bash
    set -e
    cd "{{root}}"
    DIR="$(pwd)/port-report/{{PORT}}"
    [ -f "$DIR/run-latest/findings.tsv" ] || { echo "no run-latest for {{PORT}} — run the migration first"; exit 1; }
    mkdir -p "$DIR/baseline"
    # Only DETERMINISTIC, position-free files are promoted:
    #   findings.tsv / counts.tsv  — every check
    #   members.tsv                — one digest per emitted member; this is what makes "you changed
    #                                3 members you did not intend to" answerable before a compile,
    #                                and it is line-free so a member that only MOVED does not churn
    #   tests.tsv                  — the pass/fail set; the behavioural baseline, and the only one
    #                                that can catch a CLAUDE.md §4.4 regression
    # srcmap.tsv is deliberately NOT promoted: it is positional by construction and would rewrite
    # itself on every emit. report.md carries the absolute source root and diff.txt is derived.
    for f in findings.tsv counts.tsv members.tsv tests.tsv port-map.tsv; do
      if [ -f "$DIR/run-latest/$f" ]; then cp "$DIR/run-latest/$f" "$DIR/baseline/"; fi
    done
    echo "baseline accepted for {{PORT}}:"
    cat "$DIR/baseline/counts.tsv"
    if [ -f "$DIR/baseline/members.tsv" ]; then
      echo "members: $(grep -vc '^#' "$DIR/baseline/members.tsv" || true)"
    fi
    if [ -f "$DIR/baseline/tests.tsv" ]; then
      echo "tests:   $(grep -c $'\tpass$' "$DIR/baseline/tests.tsv" || true) passing, $(grep -c $'\tfail$' "$DIR/baseline/tests.tsv" || true) failing"
    fi
    # A failing test in the baseline is a REGRESSION-FREE state only if something says why it fails.
    # Most of the time nothing has to: a failure whose stack reaches a type in the port's
    # `Substitutions.dropTypes` is DERIVED as expected from `run-latest/dropped-types.tsv`, which
    # PortRun regenerates every run. The warning below is for the residue — a failure no drop
    # explains and that is still a decision.
    if [ -f "$DIR/run-latest/test-failures.tsv" ] && grep -q $'\tunexpected\t' "$DIR/run-latest/test-failures.tsv"; then
      echo
      echo "NOTE: this baseline contains failing tests that NO SUBSTITUTION explains:"
      grep $'\tunexpected\t' "$DIR/run-latest/test-failures.tsv" | cut -f1,2 | sed 's/^/        /'
      echo "      Either they are regressions, or they are decisions — and a decision belongs in"
      echo "      baseline/expected-failures.tsv ('#suite<TAB>test<TAB>reason', '*' for a whole"
      echo "      suite) with a reason someone can defend. Left unstated they read as regressions"
      echo "      that someone once accepted."
    fi
    echo
    echo "commit port-report/{{PORT}}/baseline/ with the change that produced it."
