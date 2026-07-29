#!/bin/bash
# The baseline half of the check report: show, diff and ACCEPT.
#
#   bash scripts/port_baseline.sh list
#   bash scripts/port_baseline.sh show   <port>
#   bash scripts/port_baseline.sh diff   <port>
#   bash scripts/port_baseline.sh accept <port>
#
# <port> is a directory under port-report/, named after the migration program's main class
# (LibgdxCoreMigrate, LibgdxTestMigrate, …) — balticporter.tir.CheckReport derives it, so no
# migration program has to be told what it is called.
#
# Promotion is EXPLICIT, deliberately: `run-latest/` is overwritten by every run, `baseline/` moves
# only when a human accepts a step. That is golden-test discipline, and it is what makes
# "omissions 31->33" a fact rather than a memory (CLAUDE.md §5).
set -e
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
CMD="${1:-list}"
PORT="$2"
DIR="$ROOT/port-report/$PORT"

case "$CMD" in
  list)
    if [ ! -d "$ROOT/port-report" ]; then echo "no port-report/ yet — run a migration first"; exit 0; fi
    for d in "$ROOT"/port-report/*/; do
      name="$(basename "$d")"
      b="no baseline"; [ -f "$d/baseline/counts.tsv" ] && b="baseline: $(grep -vc '^#' "$d/baseline/findings.tsv" || echo 0) findings"
      r="no run"; [ -f "$d/run-latest/subject.txt" ] && r="$(cat "$d/run-latest/subject.txt")"
      printf '%-24s %-22s %s\n' "$name" "$b" "$r"
    done
    ;;
  show)
    [ -n "$PORT" ] || { echo "usage: $0 show <port>"; exit 2; }
    cat "$DIR/run-latest/report.md"
    ;;
  diff)
    [ -n "$PORT" ] || { echo "usage: $0 diff <port>"; exit 2; }
    cat "$DIR/run-latest/diff.txt"
    ;;
  accept)
    [ -n "$PORT" ] || { echo "usage: $0 accept <port>"; exit 2; }
    [ -f "$DIR/run-latest/findings.tsv" ] || { echo "no run-latest for $PORT — run the migration first"; exit 1; }
    mkdir -p "$DIR/baseline"
    # only the two DETERMINISTIC files are promoted. report.md carries the absolute source root and
    # is a human document; diff.txt is derived. Committing either would put churn in the baseline.
    cp "$DIR/run-latest/findings.tsv" "$DIR/run-latest/counts.tsv" "$DIR/baseline/"
    echo "baseline accepted for $PORT:"
    cat "$DIR/baseline/counts.tsv"
    echo
    echo "commit port-report/$PORT/baseline/ with the change that produced it."
    ;;
  *)
    echo "usage: $0 {list|show|diff|accept} [port]"; exit 2 ;;
esac
