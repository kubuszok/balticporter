#!/bin/bash
# Per-port `decisions.tsv` row counts by KIND, for every port that has a run.
#
# The decision log is NOT baselined (`port_baseline.sh accept` promotes findings/counts/members/
# tests/port-map and deliberately not this one), so nothing else in the workflow states its size.
# A provenance artifact whose row count nobody prints is one nobody notices going empty.
cd "$(dirname "$0")/.."
for d in port-report/*/; do
  f="$d/run-latest/decisions.tsv"
  [ -f "$f" ] || continue
  n=$(( $(grep -vc '^#' "$f") ))
  echo "$(basename "$d"): $n row(s)"
  grep -v '^#' "$f" | cut -f1 | sort | uniq -c | sed 's/^/    /'
done
