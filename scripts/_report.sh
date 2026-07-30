#!/bin/bash
# Shared helpers for the measure scripts.
#
# Why a marker FILE and not an environment variable (CLAUDE.md §4.6): `sbt -client` talks to a
# long-running server started with some earlier shell's environment, and the migration then runs in
# a JVM FORKED from that server whose `-D` options come from `build.sbt`, not from this script's
# command line. Neither an exported variable nor a `-D` on this script's `sbt` invocation reaches
# the migration. A properties file under the repo root does, because the migration reads it itself
# (`balticporter.tir.DebugFlags`). `.balticporter/` is gitignored.

# Scratch outputs (compiler and test-runner captures) go under the CHECKOUT, never /tmp: the
# fixed /tmp names collided the moment two checkouts (a main tree and a worktree, or two agents'
# worktrees) measured concurrently — one checkout's compile output silently counted, and then
# CORRELATED, as the other's. `.balticporter/` is gitignored, so this also survives nothing.
# The same rule removed `pkill -9 -f scala-cli` from every measure script: with `--server=false`
# each compile is self-contained, so the pkill's only effect on a correct run was to kill a
# CONCURRENT checkout's compile mid-write — whose truncated output then greps as fewer errors.
MEASURE_TMP="$(pwd)/.balticporter/tmp"
mkdir -p "$MEASURE_TMP"

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

# java_test_count <java-test-dir>...
# How many `@Test` methods the java suite ACTUALLY declares — comments stripped first.
#
# `grep -c "@Test"` counts annotations inside commented-out code, and simple-graphs has one: an entire
# `GridPoint` class and its `testExample` sit inside a `/* … */` block. The discovery check therefore
# reported "TESTS LOST — 1 of 17 would never run, and the suite would report success" on a port that
# had lost nothing. That is the worst possible failure for this particular check: it is the one guard
# against a suite that runs ZERO tests and reports success, and a check whose first firing is a false
# positive teaches its reader to ignore it (ENGINE-LIMITS M5).
#
# PER FILE and LINE-ORIENTED, tracking block-comment state — not a slurp-and-regex.
#
# The obvious version (`perl -0777 -pe 's{/\*.*?\*/}{}gs'` over the concatenated files) was written
# first and is wrong in the more dangerous direction: it removed 40 of libGDX's 221 LIVE `@Test`
# annotations. Concatenating the tree makes one unbalanced `/*` — inside a string literal, inside a
# javadoc example — swallow everything up to the next real `*/`, across file boundaries. So the fix
# for a check that cried wolf about 1 test produced a check that hid 40.
#
# What is relied on here, and it is the whole reason this is tractable: a real annotation is the first
# token on its line. That makes `^\s*@Test\b` outside a block comment sufficient, and makes every
# remaining imprecision (a `//` inside a string, an unbalanced marker) confined to one file and unable
# to affect the line the count actually looks at.
java_test_count() {
  find "$@" -name '*.java' -print0 2>/dev/null | xargs -0 perl -e '
    my $n = 0;
    for my $f (@ARGV) {
      open(my $h, "<", $f) or next;
      my $in = 0;                       # inside a /* … */ block, reset per FILE
      while (my $l = <$h>) {
        if ($in) { if ($l =~ s{^.*?\*/}{}) { $in = 0 } else { next } }
        # STRING LITERALS FIRST, and not optional: the libGDX suite holds path globs such as
        # "root/(a)/*", and treating that /* as a comment opener swallowed the rest of the file —
        # 22 live @Test annotations, silently, the direction that HIDES a lost test. Blanking
        # literals cannot affect the count, because an annotation is never inside one.
        # (NB no apostrophes in this program: it is single-quoted in the shell.)
        $l =~ s{"(?:\\.|[^"\\])*"}{""}g;
        $l =~ s{/\*.*?\*/}{}g;          # whole blocks opened and closed on this line
        if ($l =~ s{/\*.*$}{}) { $in = 1 }
        $n++ if $l =~ /^\s*\@Test\b/;   # an annotation is the first token on its line
      }
      close($h);
    }
    print $n;
  '
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
