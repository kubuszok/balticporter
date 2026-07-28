#!/bin/bash
# re-emit libgdx-core and count errors with scala-cli (the consistent gate; sbt incremental lies)
cd "$(dirname "$0")/.."
# ABORT if the migration itself did not run. Piping straight into `grep wrote` discarded the exit
# status, so an engine that failed to COMPILE printed nothing and the script went on to measure the
# PREVIOUS emit — reporting a stale number as a result. Two consecutive measurements were read as
# "no change" when the change had never been built.
MIGRATE_OUT=$(sbt -client "corpus-tests/runMain balticporter.corpus.LibgdxCoreMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
if ! grep -qE "wrote [0-9]+ Scala files" <<<"$MIGRATE_OUT"; then
  echo "!! MIGRATION DID NOT RUN — refusing to measure stale output"
  grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$MIGRATE_OUT" | head -20
  exit 1
fi
grep -E "wrote" <<<"$MIGRATE_OUT" | head -1
pkill -9 -f scala-cli 2>/dev/null; sleep 1
scala-cli compile --scala 3.8.4 --server=false libgdx-core/src_managed/main/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > /tmp/gdxmeasure.txt
# count ALL errors: coded `-- [Exxx] ... Error` AND bare `-- Error:` (e.g. "secondary constructor
# must call a preceding constructor" carries no code). The coded-only count silently undercounts.
echo "TOTAL ERRORS: $(grep -cE '^-- (\[E[0-9]+\] )?.*Error' /tmp/gdxmeasure.txt)  (coded $(grep -cE '\[E[0-9]+\].*Error' /tmp/gdxmeasure.txt) + bare $(grep -cE '^-- Error:' /tmp/gdxmeasure.txt))"
grep -oE "\[E[0-9]+\][^:]*Error" /tmp/gdxmeasure.txt | sort | uniq -c | sort -rn | head
echo "-- bare (uncoded) errors by message --"
grep -A1 '^-- Error:' /tmp/gdxmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head
