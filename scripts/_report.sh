#!/bin/bash
# Shared helpers for the measure scripts.
#
# Why a marker FILE and not an environment variable (CLAUDE.md §4.6): `sbt -client` talks to a
# long-running server started with some earlier shell's environment, and the migration then runs in
# a JVM FORKED from that server whose `-D` options come from `build.sbt`, not from this script's
# command line. Neither an exported variable nor a `-D` on this script's `sbt` invocation reaches
# the migration. A properties file under the repo root does, because the migration reads it itself
# (`balticporter.tir.DebugFlags`). `.balticporter/` is gitignored.

# write_run_props <repo-root> <key=value>...
# Replaces .balticporter/run.properties wholesale. A hand-written .balticporter/debug.properties is
# read AFTER it and wins, so an operator's own flags survive a measure run.
write_run_props() {
  local root="$1"; shift
  mkdir -p "$root/.balticporter"
  {
    echo "# written by scripts/$(basename "${BASH_SOURCE[1]:-measure}") — safe to delete"
    for kv in "$@"; do echo "$kv"; done
  } > "$root/.balticporter/run.properties"
}

# show_check_report <report-dir>
# The persisted, UNTRUNCATED check results and their diff against the committed baseline.
show_check_report() {
  local dir="$1"
  if [ ! -f "$dir/run-latest/diff.txt" ]; then
    echo "  (no check report at $dir/run-latest — the migration recorded no checks)"
    return
  fi
  cat "$dir/run-latest/diff.txt"
  echo "  full, untruncated findings: $dir/run-latest/findings.tsv"
}

# headline <error-count> <report-dir>
# One line carrying BOTH gates, so the check numbers never bury the compile-error count and the
# compile-error count never hides the checks. `subject.txt` is the before->after fragment
# CLAUDE.md §5 wants in the commit subject.
headline() {
  local errors="$1" dir="$2"
  local checks="(no check report)"
  [ -f "$dir/run-latest/subject.txt" ] && checks="$(cat "$dir/run-latest/subject.txt")"
  echo
  echo "=================================================================="
  echo "HEADLINE  errors=$errors | $checks"
  echo "=================================================================="
}
