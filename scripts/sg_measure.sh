#!/bin/bash
# re-emit simple-graphs and count errors with scala-cli — the same gate as scripts/gdx_measure.sh.
#
# simple-graphs is a VENDORED-runtime port (RuntimeMode.Vendored): the shim family it retypes onto
# is written into its own source set, so the compile is over one directory and needs no classpath.
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
. scripts/_report.sh

# The finding ids are hashed from paths relative to this root (CLAUDE.md §4.6): set anywhere else
# and every finding diffs as removed-and-re-added against a baseline whose counts are identical.
write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/../sge/original-src/simple-graphs"
REPORT="$ROOT/port-report/SimpleGraphsMigrate"

TREPORT="$ROOT/port-report/SimpleGraphsTestMigrate"

for M in SimpleGraphsMigrate SimpleGraphsTestMigrate; do
  OUT=$(sbt -client "corpus-tests/runMain balticporter.corpus.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
  if ! grep -qE "wrote [0-9]+ Scala( test)? files" <<<"$OUT"; then
    echo "!! $M DID NOT RUN — refusing to measure stale output"
    grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$OUT" | head -20
    exit 1
  fi
  echo "-- $M (all four checks, as the migration printed them) --"
  sed -n '/building model over/,/wrote [0-9]* Scala\( test\)\? files/p' <<<"$OUT"
  echo
done

echo "-- checks: persisted, untruncated, diffed against the baseline --"
show_check_report "$REPORT"
show_check_report "$TREPORT"

echo
echo "-- test discovery --"
# Both frameworks summed, as in gdx_test_measure.sh: a ported suite is MUnit and any residue is still
# JUnit, so counting one under-reports by every converted suite — in the safe-looking direction. A
# suite with no discoverable tests runs ZERO and reports SUCCESS.
JAVA_TESTS=$(java_test_count ../sge/original-src/simple-graphs/src/test)
JUNIT_LEFT=$(grep -rh "@org.junit.Test\|@Test" simplegraphs-core/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
MUNIT_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' simplegraphs-core/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
[ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! TESTS LOST — $((JAVA_TESTS - SCALA_TESTS)) of $JAVA_TESTS would never run, and the suite would report success"

echo
echo "-- compile --"
# NOTE the ANSI strip. Dropped once, and `grep -cE '^-- .*Error'` then matched nothing because every
# line begins with a colour escape — reporting 0 errors for a port that had 20. A false NEGATIVE on
# the project's headline number is the worst failure a measure script can have.
# BOTH source sets on one invocation: the main port is RuntimeMode.Vendored, so the shims live in
# `src_managed/main` and the suite links against them there. Compiling either alone measures nothing.
DEPS="--dependency junit:junit:4.12 --dependency org.scalameta::munit:1.0.2"
scala-cli compile --scala 3.8.4 --server=false $DEPS \
  simplegraphs-core/src_managed/main/scala simplegraphs-core/src_managed/test/scala \
  2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/sgmeasure.txt
CLI_STATUS=${PIPESTATUS[0]}
ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/sgmeasure.txt)
compile_guard "$CLI_STATUS" "$ERRORS" "$MEASURE_TMP"/sgmeasure.txt
echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/sgmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/sgmeasure.txt))"
grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/sgmeasure.txt | sort | uniq -c | sort -rn | head
echo "-- bare (uncoded) errors by message --"
grep -A1 '^-- Error:' "$MEASURE_TMP"/sgmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

# ---------------------------------------------------------------------------------------------
# RUN them. Compiling a suite measures nothing about behaviour: CLAUDE.md §4.4 lists ten java forms
# that translate to VALID scala meaning something else, and not one moves the count above. For this
# library the live question is ORDER — `Collections.sort(list, cmp)` and `Comparator.reversed()` both
# compile whichever way round they sort.
# ---------------------------------------------------------------------------------------------
if [ "$ERRORS" = "0" ]; then
  echo
  echo "-- run --"
  scala-cli test --scala 3.8.4 --server=false $DEPS -Duser.language=en -Duser.country=US \
    simplegraphs-core/src_managed/main/scala simplegraphs-core/src_managed/test/scala \
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
