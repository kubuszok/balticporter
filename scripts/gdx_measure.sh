#!/bin/bash
# re-emit libgdx-core and count errors with scala-cli (the consistent gate; sbt incremental lies)
#
# This is the command CLAUDE.md §5 tells everyone to run, so it must show everything the migration
# knows. Until 2026-07-29 line 14 was `grep -E "wrote" | head -1`: the four independent checks
# (signature consistency, omissions, portability, substitutions removed) were computed on every run
# and then DISCARDED by the one script anybody runs. CLAUDE.md's claim that "the migration prints
# four independent checks on every run" was true of the migration and false of the workflow.
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
. scripts/_report.sh

# Make persisted findings machine-independent: paths in the artifact are relative to this root, so
# a baseline committed from one checkout diffs cleanly against a run from another (or from a
# worktree). See balticporter.tir.CheckReport.relativise.
write_run_props "$ROOT" "balticporter.reportPathRoot=$ROOT/../sge/original-src/libgdx/gdx"
REPORT="$ROOT/port-report/LibgdxCoreMigrate"

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

echo "-- migration (all four checks, as the migration printed them) --"
# The whole block the migration emitted, in order, from its first line to its last. A `grep` for
# named lines is how the checks got lost in the first place: it silently drops any line a future
# check adds.
sed -n '/building model over/,/wrote [0-9]* Scala files/p' <<<"$MIGRATE_OUT"

echo
echo "-- checks: persisted, untruncated, diffed against the baseline --"
show_check_report "$REPORT"

echo
echo "-- compile --"
scala-cli compile --scala 3.8.4 --server=false libgdx-core/src_managed/main/scala 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$MEASURE_TMP"/gdxmeasure.txt
# count ALL errors: coded `-- [Exxx] ... Error` AND bare `-- Error:` (e.g. "secondary constructor
# must call a preceding constructor" carries no code). The coded-only count silently undercounts.
ERRORS=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$MEASURE_TMP"/gdxmeasure.txt)
echo "TOTAL ERRORS: $ERRORS  (coded $(grep -cE '\[E[0-9]+\].*Error' "$MEASURE_TMP"/gdxmeasure.txt) + bare $(grep -cE '^-- Error:' "$MEASURE_TMP"/gdxmeasure.txt))"
grep -oE "\[E[0-9]+\][^:]*Error" "$MEASURE_TMP"/gdxmeasure.txt | sort | uniq -c | sort -rn | head
echo "-- bare (uncoded) errors by message --"
grep -A1 '^-- Error:' "$MEASURE_TMP"/gdxmeasure.txt | grep -vE '^-- Error:|^--$' | sed -E 's/^[0-9]+ \|//; s/[0-9]+//g' | sed -E 's/^ +//' | sort | uniq -c | sort -rn | head

# A count is not a triage. Join every error back to the member and the JAVA LINE it came from, and
# split it into "at a region the engine marked approximate" vs "engine gap" (UNPORTABLE-DESIGN.md
# §6.3). With no markers minted yet everything lands in the second lane — which is the honest
# answer, and the lane an agent in another repository has to act on.
echo
echo "-- correlation: every error located to its member and its Java origin --"
correlate "$REPORT/run-latest" --scalac "$MEASURE_TMP"/gdxmeasure.txt --srcmap "$REPORT/run-latest/srcmap.tsv"

headline "$ERRORS" "$REPORT"
