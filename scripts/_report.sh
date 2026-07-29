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

# correlate <out-report-dir> [--scalac f] [--tests f] [--srcmap [scope=]f]...
# Join compiler and test-runner output back to the MEMBER and the JAVA ORIGIN that produced it
# (UNPORTABLE-DESIGN.md §6.3 and its LIBRARY-READINESS.md §2.4 amendment). Without this, a
# diagnostic over emitted Scala is a file and a line and nothing else, and every triage starts by
# reverse-engineering the emitter by hand.
#
# The source maps are written by PortRun on every migration run, beside `dropped-types.tsv` (which
# is what makes a failure reaching a substituted type EXPECTED without anyone maintaining a list).
# Passing BOTH ports' maps is what lets a test failure be anchored on the library member that threw
# rather than on the test — and it is also how the dropped types of the LIBRARY reach the SUITE's
# correlation, since the two are different ports.
#
# A second JVM is right here: the compiler and the test runner have long since exited and the join
# is over files. `PortRun.correlate` is the same logic in-process, for a program that drives its own
# compile. Paths are ABSOLUTE, always: sbt's non-forked `run` has the subproject as its cwd.
correlate() {
  local out="$1"; shift
  # strip EVERY CSI sequence, not just colour: sbt -client also emits erase-display (`ESC[0J`),
  # which survives an SGR-only filter and lands in the middle of the report.
  sbt -client "core/runMain balticporter.tir.CorrelateMain --out $out --baseline $(dirname "$out")/baseline $*" \
    2>&1 | sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' | sed -n '/^units in source map/,$p' | grep -v '^\['
}

# headline <error-count> <report-dir>
# One line carrying BOTH gates, so the check numbers never bury the compile-error count and the
# compile-error count never hides the checks. `subject.txt` is the before->after fragment
# CLAUDE.md §5 wants in the commit subject.
headline() {
  local errors="$1" dir="$2"
  local checks="(no check report)"
  [ -f "$dir/run-latest/subject.txt" ] && checks="$(cat "$dir/run-latest/subject.txt")"
  local tests=""
  if [ -f "$dir/run-latest/tests.tsv" ]; then
    local p f
    p=$(grep -c $'\tpass$' "$dir/run-latest/tests.tsv" || true)
    f=$(grep -c $'\tfail$' "$dir/run-latest/tests.tsv" || true)
    tests=" | tests $p passing, $f failing"
    # a NEWLY failing test is the one number that must never scroll past: it is the only signal
    # this project has for the CLAUDE.md §4.4 defect class, which moves no compile-error count.
    if grep -q "^-- NEWLY FAILING" "$dir/run-latest/tests-diff.txt" 2>/dev/null; then
      tests="$tests | !! NEWLY FAILING — see $dir/run-latest/tests-diff.txt"
    fi
  fi
  echo
  echo "=================================================================="
  echo "HEADLINE  errors=$errors | $checks$tests"
  echo "=================================================================="
}
