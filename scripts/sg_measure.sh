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

MIGRATE_OUT=$(sbt -client "corpus-tests/runMain balticporter.corpus.SimpleGraphsMigrate" 2>&1 | sed 's/\x1b\[[0-9;]*m//g')
if ! grep -qE "wrote [0-9]+ Scala files" <<<"$MIGRATE_OUT"; then
  echo "!! MIGRATION DID NOT RUN — refusing to measure stale output"
  grep -E "^\[error\].*\.scala:[0-9]+|^\[error\] +\|" <<<"$MIGRATE_OUT" | head -20
  exit 1
fi

echo "-- migration (all four checks, as the migration printed them) --"
sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$MIGRATE_OUT"

echo
echo "-- checks: persisted, untruncated, diffed against the baseline --"
show_check_report "$REPORT"

echo
echo "-- compile --"
pkill -9 -f scala-cli 2>/dev/null; sleep 1
# NOTE the ANSI strip. Dropped once, and `grep -cE '^-- .*Error'` then matched nothing because every
# line begins with a colour escape — reporting 0 errors for a port that had 20. A false NEGATIVE on
# the project's headline number is the worst failure a measure script can have.
scala-cli compile --scala 3.8.4 --server=false simplegraphs-core/src_managed/main/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > /tmp/sgmeasure.txt
ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' /tmp/sgmeasure.txt)
echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' /tmp/sgmeasure.txt) + bare $(grep -cE '^-- Error:' /tmp/sgmeasure.txt))"
grep -oE "\[E[0-9]+\][^:]*Error" /tmp/sgmeasure.txt | sort | uniq -c | sort -rn | head
echo "-- bare (uncoded) errors by message --"
grep -A1 '^-- Error:' /tmp/sgmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

echo
echo "-- correlation: every error located to its member and its Java origin --"
correlate "$REPORT/run-latest" --scalac /tmp/sgmeasure.txt --srcmap "$REPORT/run-latest/srcmap.tsv"

headline "$ERRORS" "$REPORT"
