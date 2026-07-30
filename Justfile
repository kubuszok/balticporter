# Baltic Porter — the measurement suite (CLAUDE.md §5).
#
# One entry point for every number this project quotes. `just` with no recipe lists them.
#
#   just gdx-measure                 libGDX core        (emit → checks → break residue → compile → correlate)
#   just gdx-test-measure            libGDX's own suite (… → compile → RUN → correlate)
#   just ashley-measure              Ashley + its suite, compiled WITH libGDX core (a dependent port)
#   just sg-measure                  simple-graphs + its suite
#   just measure-all                 the four lanes, SERIALLY, in dependency order
#   just decision-counts             decisions.tsv row counts by kind, every port
#   just members-unchanged [PORT]    members.tsv against its committed baseline — the blast radius
#   just baseline-list                        every port: baseline size and last run
#   just baseline-show   PORT                 the run's full report
#   just baseline-diff   PORT                 the run against the committed baseline
#   just baseline-accept PORT                 promote run-latest to baseline
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

# upstream Java, relative to the checkout root
gdx_src       := "../sge/original-src/libgdx/gdx"
ashley_src    := "../sge/original-src/ashley"
sg_src        := "../sge/original-src/simple-graphs"

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

root          := justfile_directory()

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
# All four lanes, SERIALLY, in dependency order — never in parallel.
#
# Each lane re-emits into `src_managed/`, so `gdx-test-measure` and `ashley-measure` compile against
# what `gdx-measure` just wrote. Run concurrently they would measure each other's half-written
# output; run out of order they would measure the previous engine's. A lane that fails stops the
# sequence, for the same reason every lane aborts on a migration that did not run: the next number
# would be stale, and a stale number reads exactly like a result.
# ---------------------------------------------------------------------------------------------
[doc("the four lanes, SERIALLY, in dependency order — never in parallel")]
measure-all:
    #!/usr/bin/env bash
    cd "{{root}}"
    for lane in gdx-measure gdx-test-measure ashley-measure sg-measure; do
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
# ---------------------------------------------------------------------------------------------
[doc("members.tsv against its committed baseline (all ports, or one)")]
members-unchanged port="":
    #!/usr/bin/env bash
    cd "{{root}}"
    rc=0
    for d in port-report/*/; do
      [ -z "{{port}}" ] || [ "$(basename "$d")" = "{{port}}" ] || continue
      b="$d/baseline/members.tsv"; r="$d/run-latest/members.tsv"
      [ -f "$b" ] && [ -f "$r" ] || continue
      n=$(diff "$b" "$r" | grep -c '^[<>]')
      echo "$(basename "$d"): $n member(s) changed"
      [ "$n" = "0" ] || rc=1
    done
    exit $rc

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
