#!/bin/bash
# `members.tsv` against its committed baseline, for every port with a run.
#
# The member digest is the BLAST RADIUS and it is available before a compile (CLAUDE.md §5.1):
# identical files mean the emitted text is byte-for-byte unchanged, which is a stronger revert
# check than any count — no check count moves for most transform regressions.
cd "$(dirname "$0")/.."
rc=0
for d in port-report/*/; do
  b="$d/baseline/members.tsv"; r="$d/run-latest/members.tsv"
  [ -f "$b" ] && [ -f "$r" ] || continue
  n=$(diff "$b" "$r" | grep -c '^[<>]')
  echo "$(basename "$d"): $n member(s) changed"
  [ "$n" = "0" ] || rc=1
done
exit $rc
