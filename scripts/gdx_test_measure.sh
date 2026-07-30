#!/bin/bash
# Re-emit libgdx's JUnit suite and compile it TOGETHER with the ported core.
#
# The tests are the port's only BEHAVIOURAL gate; everything gdx_measure.sh reports is "compiles".
# Note the discovery check below: a JUnit suite with no @Test annotations runs ZERO tests and
# reports success, which is exactly the silent-omission failure this project keeps finding.
#
# As in gdx_measure.sh, the migration's own output is shown WHOLE. Line 17 used to keep four named
# patterns, which drops every line a future check adds — and this script's migration is the one
# that historically went its whole life without calling PortabilityCheck at all. The persisted
# report below names a check that stopped running, which a grep over stdout cannot.
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
. scripts/_report.sh

write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/../sge/original-src/libgdx/gdx"
REPORT="$ROOT/port-report/LibgdxTestMigrate"

# ABORT if the migration did not run — the same stale-output defect fixed in gdx_measure.sh: piping
# into grep discards the exit status, so an engine that fails to COMPILE measures the PREVIOUS emit
# and reports it as a result.
MIGRATE_OUT=$(sbt -client "corpus-tests/runMain balticporter.corpus.LibgdxTestMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
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
JAVA_TESTS=$(java_test_count ../sge/original-src/libgdx/gdx/test)
JUNIT_LEFT=$(grep -rh "@org.junit.Test\|@Test" libgdx-core/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
MUNIT_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' libgdx-core/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
[ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! TESTS LOST — $((JAVA_TESTS - SCALA_TESTS)) of $JAVA_TESTS would never run, and the suite would report success"

echo
echo "-- compile --"
scala-cli compile --scala 3.8.4 --server=false \
  --dependency org.junit.jupiter:junit-jupiter:5.10.2 \
  --dependency junit:junit:4.13.2 \
  --dependency org.scalameta::munit:1.0.2 \
  libgdx-core/src_managed/main/scala libgdx-core/src_managed/test/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxtestmeasure.txt
ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gdxtestmeasure.txt)
echo "TOTAL ERRORS: $ERRORS"
grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gdxtestmeasure.txt | sort | uniq -c | sort -rn | head

# ---------------------------------------------------------------------------------------------
# RUN them. Compiling a test suite measures nothing about behaviour, and CLAUDE.md §4.4 lists ten
# Java forms that translate to VALID Scala meaning something else — reference `==`, `x++` as a
# value, `break`/`continue`, `switch` fall-out, a dropped `super(args)`, `@Before`. Not one of them
# moves the error count above. Running the suite is the only gate that sees them.
# ---------------------------------------------------------------------------------------------
if [ "$ERRORS" = "0" ]; then
  echo
  echo "-- run --"
  scala-cli test --scala 3.8.4 --server=false \
    --dependency org.scalameta::munit:1.0.2 \
    --dependency junit:junit:4.13.2 \
    --dependency org.junit.jupiter:junit-jupiter:5.10.2 \
    -Duser.language=en -Duser.country=US \
    libgdx-core/src_managed/main/scala libgdx-core/src_managed/test/scala 2>&1 |
    sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxtestrun.txt
  echo "passing: $(grep -cE '^  \+ ' "$MEASURE_TMP"/gdxtestrun.txt)   failing: $(grep -c '^==> X ' "$MEASURE_TMP"/gdxtestrun.txt)"

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
