#!/bin/bash
# Re-emit libgdx's JUnit suite and compile it TOGETHER with the ported core.
#
# The tests are the port's only BEHAVIOURAL gate; everything gdx_measure.sh reports is "compiles".
# Note the discovery check below: a JUnit suite with no @Test annotations runs ZERO tests and
# reports success, which is exactly the silent-omission failure this project keeps finding.
cd "$(dirname "$0")/.."
# ABORT if the migration did not run — the same stale-output defect fixed in gdx_measure.sh: piping
# into grep discards the exit status, so an engine that fails to COMPILE measures the PREVIOUS emit
# and reports it as a result.
MIGRATE_OUT=$(sbt -client "corpus-tests/runMain balticporter.corpus.LibgdxTestMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
if ! grep -qE "wrote [0-9]+ Scala test files" <<<"$MIGRATE_OUT"; then
  echo "!! TEST MIGRATION DID NOT RUN — refusing to measure stale output"
  grep -E "^\[error\].*\.scala:[0-9]+" <<<"$MIGRATE_OUT" | head -20
  exit 1
fi
grep -E "wrote|WARNING|OMISSIONS \(|PORTABILITY|signature" <<<"$MIGRATE_OUT"

echo "-- test discovery --"
JAVA_TESTS=$(grep -rh "@Test" ../sge/original-src/libgdx/gdx/test | wc -l | tr -d ' ')
SCALA_TESTS=$(grep -rh "@org.junit.Test\|@Test" libgdx-core/src/test/scala 2>/dev/null | wc -l | tr -d ' ')
echo "@Test in Java: $JAVA_TESTS   @Test in emitted Scala: $SCALA_TESTS"
[ "$JAVA_TESTS" != "$SCALA_TESTS" ] && echo "!! ANNOTATIONS LOST — JUnit would discover $SCALA_TESTS tests and report success"

pkill -9 -f scala-cli 2>/dev/null; sleep 1
scala-cli compile --scala 3.8.4 --server=false \
  --dependency org.junit.jupiter:junit-jupiter:5.10.2 \
  --dependency junit:junit:4.13.2 \
  --dependency org.scalameta::munit:1.0.2 \
  libgdx-core/src/main/scala libgdx-core/src/test/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > /tmp/gdxtestmeasure.txt
echo "TOTAL ERRORS: $(grep -cE '^-- (\[E[0-9]+\] )?.*Error' /tmp/gdxtestmeasure.txt)"
grep -oE "\[E[0-9]+\][^:]*Error" /tmp/gdxtestmeasure.txt | sort | uniq -c | sort -rn | head
