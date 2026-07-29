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
    # Only DETERMINISTIC, position-free files are promoted:
    #   findings.tsv / counts.tsv  — the four checks
    #   members.tsv                — one digest per emitted member; this is what makes "you changed
    #                                3 members you did not intend to" answerable before a compile,
    #                                and it is line-free so a member that only MOVED does not churn
    #   tests.tsv                  — the pass/fail set; the behavioural baseline, and the only one
    #                                that can catch a CLAUDE.md §4.4 regression
    # srcmap.tsv is deliberately NOT promoted: it is positional by construction and would rewrite
    # itself on every emit. report.md carries the absolute source root and diff.txt is derived.
    for f in findings.tsv counts.tsv members.tsv tests.tsv port-map.tsv; do
      if [ -f "$DIR/run-latest/$f" ]; then cp "$DIR/run-latest/$f" "$DIR/baseline/"; fi
    done
    echo "baseline accepted for $PORT:"
    cat "$DIR/baseline/counts.tsv"
    if [ -f "$DIR/baseline/members.tsv" ]; then
      echo "members: $(grep -vc '^#' "$DIR/baseline/members.tsv" || true)"
    fi
    if [ -f "$DIR/baseline/tests.tsv" ]; then
      echo "tests:   $(grep -c $'\tpass$' "$DIR/baseline/tests.tsv" || true) passing, $(grep -c $'\tfail$' "$DIR/baseline/tests.tsv" || true) failing"
    fi
    # A failing test in the baseline is a REGRESSION-FREE state only if something says why it fails.
    # Most of the time nothing has to: a failure whose stack reaches a type in the port's
    # `Substitutions.dropTypes` is DERIVED as expected from `run-latest/dropped-types.tsv`, which
    # PortRun regenerates every run. The warning below is for the residue — a failure no drop
    # explains and that is still a decision.
    if [ -f "$DIR/run-latest/test-failures.tsv" ] && grep -q $'\tunexpected\t' "$DIR/run-latest/test-failures.tsv"; then
      echo
      echo "NOTE: this baseline contains failing tests that NO SUBSTITUTION explains:"
      grep $'\tunexpected\t' "$DIR/run-latest/test-failures.tsv" | cut -f1,2 | sed 's/^/        /'
      echo "      Either they are regressions, or they are decisions — and a decision belongs in"
      echo "      baseline/expected-failures.tsv ('#suite<TAB>test<TAB>reason', '*' for a whole"
      echo "      suite) with a reason someone can defend. Left unstated they read as regressions"
      echo "      that someone once accepted."
    fi
    echo
    echo "commit port-report/$PORT/baseline/ with the change that produced it."
    ;;
  *)
    echo "usage: $0 {list|show|diff|accept} [port]"; exit 2 ;;
esac
