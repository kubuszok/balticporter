#!/bin/bash
# Re-emit Ashley (main + its JUnit suite) and compile BOTH together with the ported libGDX core.
#
# `AshleyMigrate.nextStep` has named this script since the port landed and it did not exist — the
# port's numbers were reproducible only by retyping the commands, which is exactly the state
# CLAUDE.md §5 calls not-a-baseline. Written now, in the shape gdx_test_measure.sh already has.
#
# Ashley is a DEPENDENT port (RuntimeMode.Dependency): the collection shims are vendored by
# libgdx-core, so both source sets must be on the same scala-cli invocation. Compiling
# ashley-core alone measures nothing — every one of its 21 files resolves against libGDX.
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
. scripts/_report.sh

write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/../sge/original-src/ashley"
REPORT="$ROOT/port-report/AshleyMigrate"
TREPORT="$ROOT/port-report/AshleyTestMigrate"

for M in AshleyMigrate AshleyTestMigrate; do
  OUT=$(sbt -client "corpus-tests/runMain balticporter.corpus.$M" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
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
# The same both-frameworks sum gdx_test_measure.sh uses: a ported suite is MUnit and any residue is
# still JUnit, so counting only one under-reports by every converted suite — in the safe-looking
# direction, which is the dangerous one.
JAVA_TESTS=$(grep -rh "@Test" ../sge/original-src/ashley/ashley/tests | wc -l | tr -d ' ')
JUNIT_LEFT=$(grep -rh "@org.junit.Test\|@Test" ashley-core/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
MUNIT_TESTS=$(grep -rhoE '(^|[^a-zA-Z0-9_.])test\("' ashley-core/src_managed/test/scala 2>/dev/null | wc -l | tr -d ' ')
SCALA_TESTS=$((JUNIT_LEFT + MUNIT_TESTS))
echo "@Test in Java: $JAVA_TESTS   discoverable in emitted Scala: $SCALA_TESTS (munit $MUNIT_TESTS + junit $JUNIT_LEFT)"
[ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! TESTS LOST — $((JAVA_TESTS - SCALA_TESTS)) of $JAVA_TESTS would never run, and the suite would report success"

# Mockito 1.10.19, NOT a 2.x/5.x: `ComponentClassFactory` uses `org.mockito.asm`, removed in 2.0.
# Read from Ashley's own build.gradle rather than guessed — guessing it cost a full cycle once.
DEPS="--dependency junit:junit:4.13.2 --dependency org.mockito:mockito-all:1.10.19 --dependency org.scalameta::munit:1.0.2"

echo
echo "-- compile --"
pkill -9 -f scala-cli 2>/dev/null; sleep 1
# NOTE the ANSI strip: without it `grep -cE '^-- .*Error'` matches nothing and reports 0 for a port
# that does not compile — a false NEGATIVE on the headline number.
scala-cli compile --scala 3.8.4 --server=false $DEPS \
  libgdx-core/src_managed/main/scala ashley-core/src_managed/main/scala ashley-core/src_managed/test/scala \
  2>&1 | sed 's/\x1b\[[0-9;]*m//g' > /tmp/ashleymeasure.txt
ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' /tmp/ashleymeasure.txt)
echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' /tmp/ashleymeasure.txt) + bare $(grep -cE '^-- Error:' /tmp/ashleymeasure.txt))"
grep -oE "\[E[0-9]+\][^:]*Error" /tmp/ashleymeasure.txt | sort | uniq -c | sort -rn | head

if [ "$ERRORS" = "0" ]; then
  echo
  echo "-- run --"
  scala-cli test --scala 3.8.4 --server=false $DEPS -Duser.language=en -Duser.country=US \
    libgdx-core/src_managed/main/scala ashley-core/src_managed/main/scala ashley-core/src_managed/test/scala \
    2>&1 | sed 's/\x1b\[[0-9;]*m//g' > /tmp/ashleyrun.txt
  echo "passing: $(grep -cE '^  \+ ' /tmp/ashleyrun.txt)   failing: $(grep -c '^==> X ' /tmp/ashleyrun.txt)"
  echo
  echo "-- correlation: test failures located to members and Java origins --"
  # BOTH ports' maps: only the library's own map can anchor a failure on the member that threw,
  # and only the suite's can name the test. libGDX's is passed too — a stack that reaches the base
  # is exactly what a dependent's failure looks like.
  correlate "$TREPORT/run-latest" --tests /tmp/ashleyrun.txt \
    --srcmap "$ROOT/port-report/LibgdxCoreMigrate/run-latest/srcmap.tsv" \
    --srcmap "$REPORT/run-latest/srcmap.tsv" \
    --srcmap "test=$TREPORT/run-latest/srcmap.tsv"
else
  echo "(not running the suite: it does not compile — a test that cannot run is not a test that passed)"
fi

headline "$ERRORS" "$TREPORT"
