#!/bin/bash
# Shared helpers for the measure lanes. SOURCED BY THE `Justfile` — it is the only entry point;
# there are no per-lane scripts any more (`just gdx-measure`, `just sg-measure`, …).
#
# This file exists because these helpers are used by four lanes and the two of them that carry the
# most reasoning per line — `java_test_count`'s comment-aware perl and `reconcile_outcomes`' third
# MUnit marker — are exactly the ones a fourfold copy would let drift apart. Everything that is
# per-lane POLICY (which project, which upstream, which dependencies) lives in the `Justfile` as a
# variable; everything here is mechanism, and takes what it needs as an argument or reads a
# documented environment variable with a working default.
#
# NOTE THE ABSENCE OF `set -e`, in here and in every lane recipe, and do not add it. `grep -c`
# exits 1 when it counts zero, and `ERRORS=$(grep -cE '^-- .*Error' …)` counting zero is the
# SUCCESS case for every lane; `[ "$a" != "$b" ] && echo …` is the shape of two guards below.
# Under `set -e` a lane would abort precisely when the port is green. The guards here are explicit
# (`compile_guard`, the migration abort, the correlate exit status) because each one names what
# went wrong — which is the thing `set -e` cannot do.
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

# ---------------------------------------------------------------------------------------------
# THE JDK IS AN INPUT TO THE MEASUREMENT, AND UNTIL NOW NOTHING RECORDED IT.
#
# The frontend resolves an EXTERNAL symbol's parents, members and modifiers out of a CLASS FILE,
# so which JDK the migration JVM happens to be decides the emitted text — exactly as the manifest
# and the engine do. The compile is a SECOND JVM, chosen independently by `scala-cli`. Nothing
# compared the two.
#
# Measured 2026-08-27 in the primary checkout: a migration JVM on GraalVM **24** (a launchd job with
# no `JAVA_HOME`, so `/usr/bin/java` asks `java_home` for the NEWEST installed JDK) emitted
# `override def getChars` on `sge.utils.CharArray` — `java.lang.CharSequence` gained `getChars` in
# JDK 23 — where the same sources under JDK 22 emit no `override` at all. `scala-cli` then compiled
# on 22 and reported `E037 … overrides nothing`. Every check count was flat, every finding
# identical, and all three of the port map's fingerprints matched: the engine, the java and the
# policy really were unchanged. The only artifact that could have said so did not exist.
#
# So two things, and the guard is the one that does the work:
#
#  - the RUN records what it ran on (`PortRun` -> `run-latest/jvm.txt`, via `CheckReport`), because
#    a lane CANNOT force the frontend's JVM: `sbt -client` talks to a long-running server whose JVM
#    was chosen when the server started, so a `JAVA_HOME` exported here never reaches the forked
#    migration. That is the same boundary CLAUDE.md §4.6 records for a `-D` flag, with the same
#    remedy — something the run WRITES crosses it where an environment variable does not;
#  - the COMPILE is pinned (`jdk_version` in the `Justfile`, one variable, passed as `--jvm` to
#    every `scala-cli` invocation in a lane), so the half a lane CAN control is not ambient.
#
# jdk_guard <report-dir>
#
# Reads the run's recorded specification version, derives the compiler's THE SAME WAY THE LANE
# INVOKES IT — by running `scala-cli` with the lane's own `--jvm` flag, never by reading `java
# -version` or a coursier path, which is a second derivation free to disagree (§4.56) — prints both
# on every run, and FAILS the lane when they differ.
#
# FATAL, AND IMMEDIATELY, unlike the error and test-discovery baselines that defer their exit to
# `headline`: those defer so that a regression still gets its correlation printed, and a correlation
# computed over a tree emitted by the wrong JVM is not a diagnosis, it is a second wrong number.
# `compile_guard`'s precedent — refuse to report rather than report something unreadable.
#
# A MISSING `jvm.txt` is FATAL for `error_baseline_guard`'s reason: "nothing is comparing this" and
# "this compares clean" are indistinguishable from the outside, and this guard's whole subject is a
# defect that every other artifact reads clean on.
jdk_guard() {
  local dir="$1"
  local jvm="$dir/run-latest/jvm.txt"
  if [ ! -f "$jvm" ]; then
    echo "!! NO JVM RECORD — this run did not say which JDK its frontend read class files with."
    echo "   $jvm does not exist, so a frontend on JDK 24 emitting an \`override\` that the JDK-22"
    echo "   compile below rejects would print as an ordinary engine gap (ENGINE-LIMITS M5.10)."
    echo "   Every migration writes it; a run that did not is one whose artifact layer was off."
    exit 1
  fi
  local frontend
  frontend=$(grep '^specification'$'\t' "$jvm" | head -1 | cut -f2)
  if [ -z "$frontend" ]; then
    echo "!! JVM RECORD UNREADABLE — $jvm holds no \`specification\` row."
    exit 1
  fi

  # The compiler's half, derived by ASKING THE COMPILER'S OWN LAUNCHER. A `.java` probe and not a
  # `.scala` one: scala-cli hands a pure-Java project straight to that JVM's javac and runs it, so
  # this costs ~0.4s rather than a Scala compilation, and it answers about the JVM `scala-cli`
  # ACTUALLY selects — which is the question, `--jvm` overriding `JAVA_HOME` and both overriding the
  # system default.
  local probe="$MEASURE_TMP/BpJdkProbe.java"
  cat > "$probe" <<'PROBE'
public class BpJdkProbe {
  public static void main(String[] a) { System.out.println(System.getProperty("java.specification.version")); }
}
PROBE
  local compile
  compile=$(scala-cli run --server=false ${jdk_version:+--jvm "$jdk_version"} "$probe" 2>/dev/null \
            | sed 's/\x1b\[[0-9;]*m//g' | grep -E '^[0-9]+(\.[0-9]+)*$' | tail -1)
  if [ -z "$compile" ]; then
    echo "!! COULD NOT DERIVE THE COMPILE JDK — \`scala-cli run\` over a one-line java probe printed"
    echo "   no version. The frontend recorded JDK $frontend; the compiler's half is UNKNOWN, and"
    echo "   'I could not check' is not 'they agree' (CLAUDE.md §3)."
    exit 1
  fi

  if [ "$frontend" = "$compile" ]; then
    echo "  frontend jdk $frontend / compile jdk $compile"
    return 0
  fi
  echo "!! JDK SPLIT — frontend jdk $frontend / compile jdk $compile"
  echo "   The migration read its class files on JDK $frontend and this lane compiles on JDK $compile,"
  echo "   so the emitted Scala is a function of a JDK the compiler does not have. It arrives as an"
  echo "   ordinary typer error at a member whose translation is perfect — measured as one"
  echo "   \`E037 … overrides nothing\` on \`sge.utils.CharArray\` (ENGINE-LIMITS M5.10), with every"
  echo "   check count, every finding and all three port-map fingerprints flat."
  echo
  echo "   WHAT TO DO — make the two agree, then re-run this lane:"
  echo "     the COMPILE half is pinned by \`jdk_version\` at the top of the Justfile (currently"
  echo "     '${jdk_version:-<unset>}'); the FRONTEND half is whatever JVM the sbt SERVER held when"
  echo "     the MIGRATION ran, and a JAVA_HOME exported by this lane does not reach it. Restart the"
  echo "     server under a JDK $compile JAVA_HOME (\`sbt -client shutdown\`, then set JAVA_HOME and"
  echo "     run one sbt command) and RE-RUN the lane that emitted this tree — for a differential or"
  echo "     drop-in lane that is the MIGRATE lane, not this one. Changing \`jdk_version\` instead is a"
  echo "     change to the measurement and is ACKNOWLEDGED by re-accepting every baseline, not absorbed."
  exit 1
}

# write_run_props <repo-root> <key=value>...
# Replaces .balticporter/run.properties wholesale. A hand-written .balticporter/debug.properties is
# read AFTER it and wins, so an operator's own flags survive a measure run.
write_run_props() {
  local root="$1"; shift
  mkdir -p "$root/.balticporter"
  {
    echo "# written by a measure lane of the Justfile — safe to delete"
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
#
# ZERO INPUT FILES IS AN EXACT ZERO, AND IT IS DECIDED BY LOOKING FOR A FILE — never by defaulting
# an empty answer to a number (CLAUDE.md §4.6). BSD `xargs -0` does not run its utility AT ALL on
# empty input, so the counter below prints NOTHING, and an empty string is not a `0`: the caller's
# `[ "$JAVA_TESTS" = "0" ]` is false, so a lane's "a suite has appeared upstream" alarm fires over a
# suite of zero, and `test_discovery_guard`'s `$((java - scala))` is a bash error rather than a
# number. Every lane before flexmark happened to pass at least one directory holding java — noise4j
# passes its `src`, jbump its whole checkout — so the counter always ran and the emptiness was
# unreachable. The first port whose scope has no `src/test` directory at all met it on its first
# run: the alarm printed, naming no count, over a zero the lane exists to assert.
#
# The `-z` test is not a fallback for an unknown value. It distinguishes "there is nothing to count"
# — where 0 is the exact answer — from "the counter ran", which is the only case the perl below
# speaks for; a `${n:-0}` after the pipe would have covered a crashed counter with the same digit.
java_test_count() {
  # nothing to count: an exact zero, and the one case an empty answer is honest about.
  if [ -z "$(find "$@" -name '*.java' -print 2>/dev/null | head -1)" ]; then echo 0; return 0; fi
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

# scala_code <emitted-scala-dir>...
# The emitted Scala with its COMMENTS REMOVED, on stdout — what the discovery counters below must
# count over — each file's code preceded by a `\x01<path>` MARKER LINE, and LINE-ALIGNED with it.
#
# Same reason `java_test_count` exists, one side further along, and it fired the moment the port
# started preserving comments properly: simple-graphs' `GraphBuilderTest` holds an entire
# commented-out `GridPoint` class with a `@Test public void testExample()` inside it, and once that
# comment reached the emitted file the plain `grep -rh "@Test"` counted it — "TESTS LOST — -1 of 16
# would never run", on a suite that had lost nothing and ran all 16. A NEGATIVE loss is the tell,
# and a guard whose firing is a false positive teaches its reader to ignore it (ENGINE-LIMITS M5).
#
# PER FILE and LINE-ORIENTED for the reason spelled out on `java_test_count`: concatenating a tree
# lets one unbalanced `/*` swallow across file boundaries, which is the direction that HIDES a lost
# test. String literals are blanked first (a path glob `"a/*"` is not a comment opener) — which
# cannot affect any count here, since neither `@Test` nor a `test(` call is inside a literal.
#
# THE MARKER IS WHAT LETS A COUNTER NAME A LOCATION. `munit_emitted` below reports the calls it
# cannot classify, and a residue with no file and no line is a residue nobody can act on
# (CLAUDE.md §4.45). `\x01` followed by a path can never match what a consumer of this stream greps
# for — `@Test` does not occur in a path, and neither does `test(` — and `just lane-selfcheck` holds
# both halves of that. LINES ARE PRESERVED for the same reason: a line INSIDE a block comment prints
# as an EMPTY line rather than being dropped, so the Nth line of a file's chunk is that file's Nth
# line. Nothing else about the output changes: the two consumers count `@Test` occurrences and
# `test(` calls, neither of which a blank line can be.
scala_code() {
  find "$@" -name '*.scala' -print0 2>/dev/null | xargs -0 perl -e '
    for my $f (@ARGV) {
      open(my $h, "<", $f) or next;
      print "\x01$f\n";                 # which file the lines below came from
      my $in = 0;                       # inside a /* … */ block, reset per FILE
      while (my $l = <$h>) {
        if ($in) { if ($l =~ s{^.*?\*/}{}) { $in = 0 } else { print "\n"; next } }
        $l =~ s{"(?:\\.|[^"\\])*"}{""}g;
        $l =~ s{/\*.*?\*/}{}g;          # whole blocks opened and closed on this line
        if ($l =~ s{/\*.*$}{}) { $in = 1 }
        $l =~ s{//.*$}{};               # …and the line form, which is how a NESTING block emits
        print $l;
      }
      close($h);
    }
  '
}

# junit_residue <emitted-scala-dir>...   — `@Test` annotations the conversion did NOT translate
junit_residue() { scala_code "$@" | grep -c "@org.junit.Test\|@Test" | tr -d ' '; }

# munit_emitted <scala-dir>...   — the MUnit test REGISTRATIONS the suite declares
#
# THE DENOMINATOR OF EVERY TEST LANE. `reconcile_outcomes` reconciles the runner's outcome lines
# against this number and `test_discovery_guard` subtracts it from the upstream `@Test` count, so
# both readings are only as good as it is.
#
# IT READS THE CALL SHAPE, NEVER THE NAME'S SPELLING, and that is the whole lesson of it. Anchored
# on `test("` — a string literal on the SAME LINE — it is exact for every EMITTED suite, because the
# conversion writes the name on the call's own line, and silently wrong about HAND-WRITTEN ones:
# gdx-ai's reference suite reads 194 that way against 196, and the lane that counts it argued —
# correctly, for that suite — that the two it missed sat in files the census excluded. Asked of the
# next library's reference suite the same anchor missed 37 calls across 18 files, **21 of them in
# files the census KEEPS**, which would have handed `reconcile_outcomes` a denominator BELOW the real
# outcome count (CLAUDE.md §4.56's instrument-silence rule; PROGRESS.md §10.8.13).
#
# So what is counted is MUnit's registration SHAPE — a CURRIED application, `test(<name>) { … }` or
# `test(<name>)(…)`. A name that wraps onto the next line, an interpolated `test(s"…")`, a computed
# `test(n)` and a `test(TestOptions(…))` are then counted BY CONSTRUCTION rather than by somebody
# adding an arm for each, which is the COMPLEMENT rule §4.56 states for an instrument's filter. The
# honest negatives are what gets enumerated, and there are three — each one measured, not imagined:
#
#   - a SELECTION (`validator.test(…)`) or a longer identifier ending in `test` — a different method;
#   - a DECLARATION (`def test(…)`) — flexmark's emitted suites declare three of them;
#   - a call applied to NO BODY — `test(0) + test(1)` is an ARRAY READ in liqp's emitted suite, **181
#     occurrences**, every one of which a counter that took each `test(` call would have counted.
#
# WHAT IT CANNOT DECIDE IT SAYS, on stderr, with the file and the line: a `test("…")` whose argument
# IS a name and which is applied to no body is none of the three negatives and is not a shape this
# counter has seen. It is 0 on every tree the corpus counts today; the point is that the day it is
# not, the number stops being silently smaller than the suite.
#
# WHY THE FIGURE IS NOT ASKED OF THE RUNNER. munit knows exactly how many tests it has — and taking
# the denominator from the run would make `reconcile_outcomes` compare the run with itself, when the
# question it exists to ask is whether an EMITTED test produced an outcome line at all.
#
# AND OUTCOMES ARE NOT REGISTRATIONS, which no counter of registration SITES can fix. One
# registration in an ABSTRACT suite runs once per CONCRETE SUBCLASS: flexmark's `FullSpecTestCase`
# declares one `test("testSpecExample")` and four concrete subclasses declare none, so 725
# registrations produce 727 outcomes. That is the over-count `reconcile_outcomes` reports
# non-fatally — a fact about INHERITANCE, measured here after the same figure had been written down
# as a third witness for the anchor above (CLAUDE.md §4.56).
munit_emitted() {
  scala_code "$@" | perl -0777 -e '
    my $t = do { local $/; <STDIN> };
    my @parts = split /\x01([^\n]*)\n/, $t, -1;
    shift @parts;                                            # text before the first marker: empty
    my $n = 0; my @open;
    while (@parts) {
      my $file = shift @parts;
      my $code = shift @parts; $code = "" unless defined $code;
      # CHAR LITERALS FIRST: `replace(CH, CH)` — a paren inside one is not a paren for the scan
      # below, and string literals are already blanked by `scala_code`.
      $code =~ s/\x27(?:\\.|[^\x27\\])\x27/CH/g;
      $code =~ s/\b(?:def|val|var)\s+test\s*\(/DECLARATION(/g;
      while ($code =~ /(?:\A|[^A-Za-z0-9_.\$])test\(/gs) {
        my $at = pos($code);
        my $depth = 1; my $i = $at; my $len = length($code);  # walk to the matching close paren
        while ($i < $len && $depth > 0) {
          my $c = substr($code, $i, 1);
          $depth++ if $c eq "(";
          $depth-- if $c eq ")";
          $i++;
        }
        next if $depth != 0;                                  # unbalanced: not a call this can read
        if (substr($code, $i, 40) =~ /\A\s*[\{\(]/) { $n++; next }   # applied to a BODY: a registration
        next unless substr($code, $at, 40) =~ /\A\s*[A-Za-z_]*"/;    # not name-shaped: not one either
        my $line = 1 + (() = substr($code, 0, $at) =~ /\n/g);
        my $snip = substr($code, $at, 48); $snip =~ s/\s+/ /g;
        push @open, "$file:$line: test($snip";
      }
    }
    print $n;
    if (@open) {
      print STDERR "!! NAMED test( CALL APPLIED TO NO BODY — " . scalar(@open) . ", so this count is a FLOOR.\n";
      print STDERR "   Neither a registration this counter knows nor one of its three honest negatives:\n";
      print STDERR "     $_\n" for @open;
    }
  '
}

# reconcile_outcomes <run-output-file> <emitted-test-count>
# Every emitted test must produce an OUTCOME LINE, and the two markers a measure script naturally
# reaches for do not cover them all.
#
# MUnit prints THREE terminal markers, not two: `  + name 0.0s` (pass), `==> X suite.name …s <detail>`
# (failure) and `==> s suite.name skipped 0.0s` — the last for a test the runner never reached. That
# third one is not exotic: a test that throws a FATAL `java.lang.Error` (an `ExceptionInInitializerError`
# from a mocking library on a modern JDK, say) is not `NonFatal`, MUnit abandons the suite, and EVERY
# remaining test in it prints as skipped. Ashley's `EntityListenerTests` loses its last two that way.
#
# Adding only passes and failures therefore drops those silently — the script prints a smaller total
# with no complaint at all, which is CLAUDE.md §3's "a test that stopped running is reported as such,
# never as a pass" failing inside the measurement itself.
#
# So the reconciliation is against the EMITTED count, not against a sum of markers: any test with no
# line this script recognises is reported, whatever the reason. That keeps the check honest about the
# next marker MUnit adds, which a fixed list of markers would not.
#
# WHAT THIS FUNCTION DELIBERATELY DOES NOT DECIDE. It prints the DID-NOT-RUN line and does not fail
# the lane on it, because it cannot tell a NEW skip from one the port has already accepted — and
# only one of those is a regression. That answer exists, in the engine (`Correlate.TestDiff
# .newlySkipped`: a skipped test the baseline does not already record as skipped), and it is
# produced one stage later, by `correlate`. Re-deriving it here would be a second implementation of
# a rule this project has already got wrong once, in shell, against the same TSV — so the gate is
# `test_outcome_guard` below, which READS the engine's answer instead. What this function does gate
# on is the other half, which needs no baseline at all: an emitted test that produced NO outcome
# line has no row in `tests.tsv` for a baseline to hold an opinion about, so it can only ever be a
# regression.
reconcile_outcomes() {
  local run="$1" emitted="$2"
  local pass fail other total
  pass=$(grep -cE '^  \+ ' "$run")
  fail=$(grep -c '^==> X ' "$run")
  other=$(grep -cE '^==> [^X] ' "$run")
  total=$((pass + fail + other))
  echo "passing: $pass   failing: $fail   not run (skipped/ignored): $other   [outcomes $total of $emitted emitted]"
  if [ "$other" != "0" ]; then
    echo "!! DID NOT RUN — $other emitted test(s) never executed. A skipped test is not a passing test:"
    grep -E '^==> [^X] ' "$run" | sed 's/^/     /'
    echo "   (whether any of them is NEW is \`test_outcome_guard\`'s answer, below, from the baseline)"
  fi
  # THE TWO DIRECTIONS ARE NOT THE SAME FINDING, and only one of them is fatal.
  #
  # FEWER outcomes than emitted is a test that VANISHED: it produced no line this script recognises,
  # so it has no row in `tests.tsv` either, and the suite reports success while it disappears. That
  # is the failure this function exists for.
  #
  # MORE outcomes than emitted means the two figures are not the same population, and the EMITTED
  # one is the figure that does not describe the suite. Printed as a subtraction it reads
  # `-3 of 5 produced no outcome line`, which is nonsense, and failed as a lost test it is a green
  # lane stopped for a counting artefact. It is still reported — a reconciliation that cannot
  # reconcile is worth saying — and it is not the gate.
  #
  # THE ONE LANE THAT PRINTS IT WAS MEASURED, and the cause is INHERITANCE rather than the counter:
  # flexmark's `FullSpecTestCase` is an ABSTRACT suite declaring one `test("testSpecExample")`, and
  # four concrete subclasses declare none, so MUnit runs that ONE registration four times — 725
  # registrations, 727 outcomes. No counter of registration SITES can see that, which is exactly why
  # this branch reports rather than gates (CLAUDE.md §4.56, and `munit_emitted`'s own comment).
  if [ "$total" -lt "$emitted" ]; then
    echo "!! OUTCOMES LOST — $((emitted - total)) of $emitted emitted test(s) produced no outcome line;" \
         "the suite would report success while they vanish (CLAUDE.md §3)"
    return 1
  fi
  if [ "$total" -gt "$emitted" ]; then
    echo "!! OUTCOME COUNT ABOVE THE EMITTED COUNT — $total outcomes against $emitted emitted." \
         "No test is lost; the EMITTED figure this lane passed is what does not describe the suite."
  fi
  return 0
}

# test_outcome_guard <correlate-out-dir> [reconcile-status]
# THE GATE for a test that stopped RUNNING — run AFTER `correlate`, which is what writes the file
# it reads, and handed the same directory `correlate` was.
#
# The second argument is `reconcile_outcomes`' exit status, deliberately carried here rather than
# acted on where it was produced: an OUTCOMES-LOST run is exactly the run whose correlation a
# reader most needs, so the lane finishes the diagnosis and fails at the end, never before it.
#
# A lane that prints `!! DID NOT RUN` and exits 0 is the failure CLAUDE.md §5.1 describes happening
# inside the measurement itself: a skip moves no pass count and no fail count, so with nothing
# gating on it a suite abandoned mid-way scrolls past as a smaller green number. Ashley's lane did
# exactly that for two tests, on every run, for as long as the marker has been parsed.
#
# The distinction the gate needs — new skip vs. accepted skip — is NOT re-derived here. `correlate`
# already ran `Correlate.TestDiff`, which compares the run against `baseline/tests.tsv` and emits a
# `-- NEWLY SKIPPED` block for a skipped test the baseline does not already record as skipped; this
# reads that block and nothing else. So ashley's two baselined skips keep printing and keep passing
# (they are a recorded state, promoted with `just baseline-accept`), and the first skip nobody has
# accepted stops the lane.
#
# NEWLY FAILING is deliberately NOT gated here even though `TestDiff.regressed` covers both: the
# headline already carries it, and turning it fatal in the same change would conflate two gates and
# stop lanes for a reason this commit did not measure. `TestDiff.regressed` remains the engine's
# whole answer; this is the half the lanes were throwing away.
test_outcome_guard() {
  local dir="$1" reconciled="${2:-0}" bad=0
  local diff="$dir/tests-diff.txt"
  # No diff file means correlate did not write one — never silently "clean" (CLAUDE.md §5.1's rule
  # for a missing input), because a run with no baseline and a run whose correlation died look
  # identical from a `grep -q` that treats absence as absence of findings.
  if [ ! -f "$diff" ]; then
    echo "!! NO TEST DIFF at $diff — the correlation did not write one, so nothing can say whether a"
    echo "   test stopped running. Refusing to report a green lane on a comparison that never happened."
    bad=1
  elif grep -q '^-- NEWLY SKIPPED' "$diff"; then
    echo "!! NEWLY SKIPPED — a test the baseline does not record as skipped DID NOT RUN. It moves no"
    echo "   pass count and no fail count, which is exactly why this is a gate and not a line to read:"
    sed -n '/^-- NEWLY SKIPPED/,/^$/p' "$diff" | sed 's/^/     /'
    echo "   If the skip is a state this port accepts, promote it: just baseline-accept <port>"
    bad=1
  fi
  # …and the OTHER way a test stops running: the row is not in the artifact AT ALL. A skip is a test
  # the runner reached and did not assert; a DISAPPEARANCE is a test the run never had — which is
  # what a conversion regression that stops EMITTING a suite looks like from here. Both sides fall
  # together, so no pass count drops and no fail count rises, and the run reports success on a
  # smaller suite. `TestDiff.disappeared` rendered this from the beginning and nothing gated it.
  if [ -f "$diff" ] && grep -q '^-- tests in the baseline that DID NOT RUN' "$diff"; then
    echo "!! TESTS DISAPPEARED — a test the baseline holds has no row in this run's artifact at all."
    echo "   It moves no pass count and no fail count; the suite simply got smaller and passed:"
    sed -n '/^-- tests in the baseline that DID NOT RUN/,/^$/p' "$diff" | sed 's/^/     /'
    echo "   If the test was DELETED on purpose, say so by promoting: just baseline-accept <port>"
    bad=1
  fi
  if [ "$reconciled" != "0" ]; then
    echo "!! OUTCOMES LOST (reported above) — an emitted test produced no outcome line at all, so it"
    echo "   has no row in tests.tsv for any baseline to hold an opinion about. Failing the lane."
    bad=1
  fi
  return $bad
}

# break_residue <emitted-scala-dir>...
# A `/* break … */ ()` / `/* continue … */ ()` comment is what the emitter leaves where a java jump
# had no translation. The number was QUOTED in a status file as a measure ("45, all switch-case")
# while nothing computed it — the real count was 55, 45 of them LABELLED breaks (JsonReader 30,
# TextField 11, GlyphLayout 3, Table 1) whose loss corrupted JsonReader's parsing, and 10 of them
# unlabelled breaks in the MIDDLE of a switch case. A quoted number with no computation behind it
# drifts the moment the next emit changes anything, so the measure scripts print it on every run.
#
# The comment now carries the DIAGNOSIS, not just the word (CLAUDE.md §4.45: a residue an agent
# cannot classify costs it a full investigation), so the breakdown is by REASON as well as by file
# — a jump in one reason is attributable to one gap. The match is deliberately `/* break` and
# `/* continue` rather than the whole comment: a new reason string must be counted, not missed.
break_residue() {
  local total
  total=$(grep -rho '/\* \(break\|continue\)[^*]*\*/' "$@" 2>/dev/null | wc -l | tr -d ' ')
  echo "break residue: $total × untranslated jump(s) in emitted code"
  if [ "$total" != "0" ]; then
    echo "   by reason:"
    grep -rho '/\* \(break\|continue\)[^*]*\*/' "$@" 2>/dev/null | sort | uniq -c | sort -rn | sed 's/^/    /'
    echo "   by file:"
    grep -rl '/\* \(break\|continue\)[^*]*\*/' "$@" 2>/dev/null | while read -r f; do
      echo "     $(grep -o '/\* \(break\|continue\)[^*]*\*/' "$f" | wc -l | tr -d ' ') $(basename "$f")"
    done | sort -rn
  fi
}

# declared_dep_flags <report-dir> [<report-dir> …]
#
# The scala-cli `--dependency`/`--repository` flags for the coordinates THE PORT ITSELF DECLARED,
# read from what the run published (`run-latest/dependencies.tsv`, written by `PortRun` from
# `PortManifest.dependencies`).
#
# WHY THIS IS NOT A STRING IN THE Justfile. The multiarch coordinate was stated THREE times — the
# port's `.conf`, the generated build's `resolvers`/`libraryDependencies`, and a hand-maintained
# `--dependency … --repository …` here — and nothing compared the third with the first. A revision
# bumped in the manifest and not in the lane compiles the port against a DIFFERENT JAR, with every
# check count, every member digest and every test outcome flat: exactly the divergence §5 exists to
# make impossible, and the same one-value-one-spelling rule §1.5 applies to a manifest. So the run
# publishes and the lane derives; a coordinate can now only be wrong in one place.
#
# WHICH coordinates: the ones the run marked `onClasspath=yes`, which it decides from the EVIDENCE
# and not from the coordinate's shape (`DependencyCheck.Evidence`). An artifact that answers a JDK
# API off the JVM — `scala-java-time` exists so `java.time` resolves on Scala.js — is one this JVM
# compile already has, and putting it on the line would shadow the JDK's own `java.time`. An
# artifact the emitted code NAMES is one this compile cannot resolve without. Unknown takes the
# INCLUDING arm at the run: a jar nothing needs costs a resolution, a missing one is a wall of
# errors that are not the port's.
#
# A report directory with no such file contributes NOTHING and is not an error — twelve of the
# corpus's fifteen ports declare no dependency at all, and a lane whose port declares none is a lane
# whose `{{x}}_deps` variable is the whole answer, exactly as before.
declared_dep_flags() {
  local dir f
  for dir in "$@"; do
    f="$dir/run-latest/dependencies.tsv"
    [ -f "$f" ] || continue
    awk -F'\t' '!/^#/ && $7 == "yes" && $6 != "" { print "--dependency\n" $6 }' "$f"
  done
  # …and the repositories, DEDUPLICATED across every declaration that named one: `-r` is a search
  # path and not a per-coordinate flag, so repeating one is noise and dropping one is a coordinate
  # that resolves nothing.
  for dir in "$@"; do
    f="$dir/run-latest/dependencies.tsv"
    [ -f "$f" ] || continue
    awk -F'\t' '!/^#/ && $7 == "yes" && $5 != "" { print $5 }' "$f"
  done | sort -u | while read -r r; do printf '%s\n%s\n' "--repository" "$r"; done
}

# compile_guard <scala-cli-exit-status> <counted-errors> <capture-file>
# A compile that never happened must not report 0. `scala-cli` aborting before compilation
# ("input file not found", a bad flag) exits non-zero and prints a line that matches neither
# `^-- [Exxx] ... Error` nor `^-- Error:` — so the grep count is 0 and the script printed
# `TOTAL ERRORS: 0` for a compile that never ran. Same failure class as the migration abort
# guard above the sbt call, one stage later. Exit status comes from `${PIPESTATUS[0]}`, captured
# by the caller immediately after the pipeline (the pipeline's own status is sed's).
# Non-zero WITH counted errors is the ordinary failing compile and stays silent.
#
# AND a THIRD state, which the two above cannot see and which reads exactly like a result: the
# compiler ABORTED. scalac can throw — an `AssertionError` out of `ClassfileParser` on a class file
# whose signature names a type the classpath does not hold is one measured way — and it then stops
# where it stood. Every error it had already reported is real, so the count is non-zero and the
# guard above stays quiet; but every file it had not yet typed is UNMEASURED, so the number is a
# FLOOR and nothing distinguishes it from a finished compile's total. Measured on liqp's first run:
# 25 errors and an abort, quoted as "25 errors" until the capture was read by hand.
#
# AND A FOURTH, which is about the compiler's INPUTS and not about its exit: `scala-cli compile`
# WITHOUT `--test` DOES NOT COMPILE A TEST SCOPE, whatever directories it is handed. Every lane that
# passes a `…/test/scala` alongside a `…/main/scala` was therefore reporting a MAIN-ONLY figure,
# under a headline that splits `main source set: N   test source set: M` and had been printing M=0
# because nothing had looked. Measured on ssg-md: `scala-cli compile main test` answers 185 warnings
# and NO errors on a tree where `scala-cli compile --test main test` answers 6, and where the test
# scope alone does not compile at all. Nothing else could see it — the errors surface at the RUN,
# where they read as "0 outcomes" rather than as a compile figure, and a port whose suite happens to
# run has a test scope that provably compiles, which is why the six lanes with live suites are flat
# and only the one whose suite had stopped moved.
#
# The flag is on the nine lanes whose inputs include a test directory and on no others; it is not a
# shared mechanism because the inputs are per-lane policy. Read this note before "simplifying" a
# lane's compile line.
#
# All three states FAIL THE LANE, and the third one did not until it was audited: it printed the
# warning and RETURNED, so the lane ran on to `headline`, the run looked like every other run, and
# `just baseline-accept` would happily bake a floor in as this port's number. A warning nobody is
# forced to act on is exactly the "stale number reads like a result" failure `measure-all` stops the
# whole sequence for. Nothing in the corpus relies on continuing past an abort — no lane's compile
# aborts today, liqp's included, since `LiqpClasspath` compiles the class files whose absence caused
# the one measured abort against the namespace the port emits (D-liqp-1b).
compile_guard() {
  local st="$1" errors="$2" file="$3"
  if [ "$st" != "0" ] && [ "$errors" = "0" ]; then
    echo "!! COMPILE DID NOT RUN — scala-cli exited $st with no countable error; refusing to report 0"
    tail -5 "$file" | sed 's/^/     /'
    exit 1
  fi
  if grep -qE 'An unhandled exception was thrown in the compiler|^Exception in thread "main"' "$file"; then
    echo "!! THE COMPILER ABORTED — $errors is a FLOOR, not this port's error count."
    echo "   scalac threw and stopped where it stood; every file it had not yet typed is UNMEASURED,"
    echo "   and an aborted compile's number reads exactly like a finished one's."
    echo
    echo "   WHAT TO DO — fix the abort, then re-run this lane; do not read, quote or accept a"
    echo "   baseline from this run. An abort is almost always the CLASSPATH SEAM rather than the"
    echo "   emitted code: a class file whose signature names a type the compile classpath does not"
    echo "   hold makes ClassfileParser throw (that is the measured case). Check what this lane puts"
    echo "   on --jar / --dependency against what the emitted code and its class files actually"
    echo "   reference. If it is a compiler crash instead, the capture below is the report."
    grep -m1 -B2 -A6 -E 'An unhandled exception was thrown in the compiler|^Exception in thread "main"' \
      "$file" | sed 's/^/     /'
    echo "   full capture: $file"
    exit 1
  fi
}

# error_baseline_guard <counted-errors> <report-dir>
#
# THE COMPILE-ERROR COUNT IS A MEASUREMENT, AND UNTIL NOW IT WAS THE ONLY ONE NOTHING COMPARED.
#
# Every other number a lane prints is diffed against a committed baseline: `findings.tsv` and
# `counts.tsv` per check, `members.tsv` per emitted member, `tests.tsv` per test. The headline
# number — the one every commit subject quotes and the one CLAUDE.md §3 calls the gate — was
# printed and thrown away. So a lane could go from 0 errors to 3, print `TOTAL ERRORS: 3`, exit 0,
# and `just measure-all` would run on through it to the next lane. Measured exactly that way: the
# screens lane read 0 -> 3 across an engine wave and `measure-all` reported success, because a
# non-zero error count is a legitimate state for a port that has not reached zero and no lane could
# tell "3, as always" from "3, as of this commit".
#
# So the expected count is a ONE-LINE BASELINE FILE, `<report-dir>/baseline/expected-errors`, and a
# mismatch fails the lane IN EITHER DIRECTION:
#
#   - MORE errors than the baseline is a regression, and stopping is the whole point;
#   - FEWER is a change too, and it is ACKNOWLEDGED by re-accepting rather than absorbed. A lane
#     that silently tolerated improvement would let a fix and a regression cancel in one run and
#     report nothing — and a baseline that only ever moves up is not a baseline.
#
# The observed count is written to `<report-dir>/run-latest/errors-count` on every run, so
# `just baseline-accept <port>` promotes it with the rest and nobody types a number.
#
# A MISSING baseline file is FATAL, for the reason `members-unchanged` is fatal on an input it
# cannot compare: "nothing is comparing this" and "this compares clean" are indistinguishable from
# the outside, and the second is the §3 false green.
#
# The verdict is PRINTED HERE and EXITED IN `headline`. Exiting on the spot would take the
# correlation with it — and the correlation is the thing that says WHICH member and WHICH java line
# the new errors came from, i.e. the only part of the run a regression needs.
#
# It travels between the two as a MARKER FILE, `run-latest/errors-baseline-failed`, and NOT as a
# shell variable. A variable set inside `$(…)` is set in a subshell and reaches nobody — which is
# how this gate first shipped, and the selfcheck caught it in the one shape a lane never uses. The
# same rule §4.6 gives the debug flags: a marker crosses every boundary a variable does not. It is
# rewritten (or removed) on every call, so a stale one from a previous run can never fail a run
# that is now green.
error_baseline_guard() {
  local errors="$1" dir="$2"
  local expected_file="$dir/baseline/expected-errors"
  local marker="$dir/run-latest/errors-baseline-failed"
  mkdir -p "$dir/run-latest" 2>/dev/null
  echo "$errors" > "$dir/run-latest/errors-count"
  rm -f "$marker"
  if [ ! -f "$expected_file" ]; then
    echo "!! NO ERROR BASELINE — nothing is comparing this lane's compile-error count."
    echo "   $expected_file does not exist, so a 0 -> $errors regression would print and pass."
    echo "   Seed it from this run's honest state: just baseline-accept <port>"
    : > "$marker"; return 1
  fi
  local expected
  expected=$(tr -dc '0-9' < "$expected_file")
  if [ -z "$expected" ]; then
    echo "!! ERROR BASELINE UNREADABLE — $expected_file holds no number."
    : > "$marker"; return 1
  fi
  if [ "$errors" = "$expected" ]; then
    echo "  errors vs baseline: $errors = $expected  (unchanged)"
    return 0
  fi
  if [ "$errors" -gt "$expected" ]; then
    echo "!! ERRORS ROSE — $expected -> $errors. This lane's compile REGRESSED."
    echo "   The correlation below names the member and the java origin of every one of them."
  else
    echo "!! ERRORS FELL — $expected -> $errors. That is a change, and it is ACKNOWLEDGED, not absorbed."
    echo "   Re-accept the baseline so the new floor is the one the next run is held to:"
    echo "     just baseline-accept <port>"
  fi
  : > "$marker"; return 1
}

# test_discovery_guard <java-@Test-count> <discoverable-scala-count> <report-dir>
#
# HOW MANY OF THIS LIBRARY'S TESTS THE PORT DOES NOT EMIT — baselined, and fatal when it moves.
#
# The count itself is old and right: a suite with no discoverable tests runs ZERO and reports
# SUCCESS, so every test lane sums what each FRAMEWORK would discover in the emitted Scala and holds
# it against the `@Test` count in the upstream java. What it was NOT was a gate. The line
#
#     !! TESTS LOST — 64 of 639 would never run, and the suite would report success
#
# printed, exited 0, and moved on. On liqp it is permanently 64 (an `excludeGlobs` of four files the
# frontend cannot read, and a three-key `dropMethods`), so it is a line an operator has learned to
# read past — and a 65th test lost to a CONVERSION regression changes one digit inside it. That is
# the same false green `error_baseline_guard` was written for, at the one number that says whether
# the behavioural evidence in CLAUDE.md §3 exists at all.
#
# So the expected loss is a ONE-LINE BASELINE FILE, `<report-dir>/baseline/expected-lost`, and a
# mismatch fails the lane IN EITHER DIRECTION, for `error_baseline_guard`'s reasons exactly:
#
#   - MORE lost is a regression — tests that ran yesterday do not run today;
#   - FEWER is a change too, and it is ACKNOWLEDGED by re-accepting rather than absorbed. A lane
#     that silently tolerated recovery would let a gain and a loss cancel inside one run.
#
# The number is DERIVED FROM THE PORT: every test in it is one the port's own `excludeGlobs` or
# `dropMethods` names, and the baseline is written by the run (`run-latest/tests-lost`) and promoted
# by `just baseline-accept`, so nobody ever types it — the same rule that keeps a hand-edited error
# floor from disagreeing with the run that produced it. A port that loses NOTHING is held to 0,
# which is the normal case and deliberately not an exemption.
#
# A MISSING baseline file is FATAL: "nothing is comparing this" and "this compares clean" are
# indistinguishable from the outside.
#
# The verdict is PRINTED here and EXITED in `headline`, and it travels between them as a MARKER FILE
# for the reason `error_baseline_guard` states — a variable set inside `$(…)` reaches nobody, and
# this guard runs BEFORE the compile, so exiting on the spot would take the compile, the correlation
# and the whole diagnosis with it.
test_discovery_guard() {
  local java="$1" scala="$2" dir="$3"
  local lost=$((java - scala))
  local expected_file="$dir/baseline/expected-lost"
  local marker="$dir/run-latest/tests-lost-baseline-failed"
  mkdir -p "$dir/run-latest" 2>/dev/null
  echo "$lost" > "$dir/run-latest/tests-lost"
  rm -f "$marker"
  if [ ! -f "$expected_file" ]; then
    echo "!! NO TEST-DISCOVERY BASELINE — nothing is comparing how many of this library's tests the"
    echo "   port fails to emit. $expected_file does not exist, so a 0 -> $lost regression would print"
    echo "   and pass. Seed it from this run's honest state: just baseline-accept <port>"
    : > "$marker"; return 1
  fi
  local expected
  expected=$(tr -dc '0-9' < "$expected_file")
  if [ -z "$expected" ]; then
    echo "!! TEST-DISCOVERY BASELINE UNREADABLE — $expected_file holds no number."
    : > "$marker"; return 1
  fi
  if [ "$lost" = "$expected" ]; then
    if [ "$lost" = "0" ]; then
      echo "  tests lost vs baseline: 0 = 0  (every @Test in the upstream java is emitted)"
    else
      echo "  tests lost vs baseline: $lost = $expected  (unchanged) — $lost of $java unaccounted for."
      echo "     WHY is this port's to state and this guard cannot know it: an excludeGlobs or a"
      echo "     dropMethods really does lose the test, and a \`@Test\` that OVERRIDES another emits"
      echo "     one registration for the pair and loses nothing (java's runner also runs one test"
      echo "     per concrete class). Read the port's own section before treating this as a loss."
    fi
    return 0
  fi
  if [ "$lost" -gt "$expected" ]; then
    echo "!! TESTS LOST ROSE — $expected -> $lost of $java would never run, and the suite would report"
    echo "   success. Nothing about this moves a pass count, a fail count or a compile-error count."
  else
    echo "!! TESTS LOST FELL — $expected -> $lost. That is a change, and it is ACKNOWLEDGED, not absorbed."
    echo "   Re-accept the baseline so the new floor is the one the next run is held to:"
    echo "     just baseline-accept <port>"
  fi
  : > "$marker"; return 1
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

# findings_baseline_guard <report-dir>
#
# THE FIFTH BASELINE, and the one `just baseline-accept` promoted while NOTHING compared it.
#
# `counts.tsv` is gated through the check report, `members.tsv` through `just members-unchanged`,
# `tests.tsv` through the correlator's diff, and `expected-errors`/`expected-lost` by their own
# guards. `findings.tsv` was promoted and never read — so every row's DETAIL (the owner a finding is
# attributed to, the `UsageKind` it was seen at, the running totals some checks print inside their
# own text) could move with nothing reporting it. A count that stays 100 while three of its hundred
# rows now say something else is a green lane over a changed answer.
#
# WHAT IS COMPARED IS THE CONTENT, NOT THE ROW ID. `CheckReport.Finding.id` is a hash over
# `check|kind|owner|path|detail` with a `/2`, `/3` sequence appended in LINE order, so an upstream
# whitespace edit renumbers rows that did not change — which is the reason the file was left ungated
# and it is a good one. `cut -f2-` drops exactly that column and leaves the six that carry meaning.
# The remaining order is the writer's own sort (`check, kind, owner, path, line, detail`), identical
# on both sides, so a plain `diff` pairs the rows a reader would pair.
#
# A MISSING baseline is FATAL, for `error_baseline_guard`'s reason: "nothing is comparing this" and
# "this compares clean" are indistinguishable from the outside.
#
# The verdict is PRINTED here and EXITED in `headline`, through the marker file the other two use —
# the diff is decided before the compile, and exiting here would take the compile and the
# correlation with it.
findings_baseline_guard() {
  local dir="$1"
  local base="$dir/baseline/findings.tsv" run="$dir/run-latest/findings.tsv"
  local marker="$dir/run-latest/findings-baseline-failed"
  mkdir -p "$dir/run-latest" 2>/dev/null
  rm -f "$marker"
  if [ ! -f "$run" ]; then
    echo "  (no findings.tsv at $dir/run-latest — the migration recorded no checks)"
    return 0
  fi
  if [ ! -f "$base" ]; then
    echo "!! NO FINDINGS BASELINE — nothing is comparing this lane's finding CONTENT."
    echo "   $base does not exist, so a changed owner, a changed UsageKind or a changed running"
    echo "   total would print and pass. Seed it from this run's honest state:"
    echo "     just baseline-accept <port>"
    : > "$marker"; return 1
  fi
  local b="$MEASURE_TMP/findings-base-$$.tsv" r="$MEASURE_TMP/findings-run-$$.tsv"
  grep -v '^#' "$base" | cut -f2- > "$b"
  grep -v '^#' "$run"  | cut -f2- > "$r"
  local moved
  moved=$(diff "$b" "$r" | grep -c '^[<>]')
  if [ "$moved" = "0" ]; then
    echo "  findings vs baseline: $(grep -c '' < "$r") row(s), content unchanged (ids not compared)"
    rm -f "$b" "$r"; return 0
  fi
  echo "!! FINDINGS CONTENT MOVED — $moved id-stripped line(s) differ from the committed baseline."
  echo "   Every check COUNT can be identical while this moves: a finding's owner, the usage it was"
  echo "   seen at and the totals printed inside its own text are none of them a count."
  diff "$b" "$r" | grep '^[<>]' | head -20 | sed 's/^/     /'
  [ "$moved" -gt 20 ] && echo "     … $((moved - 20)) more; full files: $base and $run"
  echo "   If this is the change you made, ACKNOWLEDGE it: just baseline-accept <port>"
  rm -f "$b" "$r"
  : > "$marker"; return 1
}

# port_map_guard <report-dir>
#
# THE SIXTH BASELINE, and the second one `just baseline-accept` promoted while NOTHING compared it.
#
# `findings_baseline_guard`'s argument, one artifact over — and with a larger blast radius, because
# `port-map.tsv` is the only committed baseline that another RUN reads. A dependent resolves against
# its base's JAVA, so what the base actually EMITTED reaches it through this file and nothing else:
# `PortMapTransform` looks its base up in it, `TirEmitter.baseName` takes emitted names from it, and
# the base-surface comparison reads the `shape` column out of it. A row that moves here without an
# acknowledgement is §1.5's two-ports-that-cannot-compile-together, produced by the artifact built to
# prevent it.
#
# It went stale TWICE, and both times it was found by accident:
#   - publishing `Surface.MemberShape.form` moved 60 member rows in the libGDX base's map, seen only
#     because somebody diffed the file by hand (`PROGRESS.md` §12.2.5);
#   - nine DEPENDENT maps carried a stale `policy=` header for days, because two bases' manifests
#     moved the policy chain every dependent digests and only the two bases were re-accepted
#     (`PROGRESS.md` §12.4.6).
#
# WHAT IS COMPARED IS THE WHOLE FILE — no column is stripped, and that is a decision rather than an
# omission. `findings_baseline_guard` drops `Finding.id` because it is a hash with a `/2`, `/3`
# sequence assigned in LINE order, so an unrelated edit renumbers rows that did not change. The port
# map has no such column, and every field it does have is a fact somebody has to acknowledge:
#
#   header  schema=   the file's own format. A bump regenerates all fifteen maps at once, which is
#                     exactly the shape a reader must be told about rather than shown as noise
#           module=   what a dependent's `baseChain` matches (`CLAUDE.md` §2.1); a moved one is a
#                     base no dependent can find any more
#           engine=   the publishing engine. `PortMap.freshness` turns a mismatch into `Stale`
#                     outright, so a version bump is the LOUDEST meaningful change here, never noise
#           sources=  a digest over the base's JAVA — content, not paths, so it is checkout- and
#                     worktree-independent, and it moves when the upstream tree does
#           files=    how much of the base that digest covers
#           policy=   the publisher's sorted `SurfacePolicy` fingerprint — the field the nine
#                     dependents carried stale, and the one neither `sources=` nor `engine=` can see
#   rows    every column is deterministic: `javaPath` is relativised against a root DERIVED from the
#           unit's own FQN (`SrcMap.sourceRootOf`, a string operation, so no §5.4 realpath hazard),
#           `javaLine` is the upstream java's line, and `digest` is the same emitted-member digest
#           `members.tsv` is already baselined on. The ORDER is the writer's own sort, sorted
#           section by section (`PortMap.of`), identical on both sides, so a plain `diff` pairs the
#           rows a reader would pair.
#
# The header is diffed FIELD BY FIELD ahead of the rows, because that is the incident that has
# actually happened: read as a raw diff, a moved `policy=` is one line of two sixteen-character
# digests and says nothing about which of six fields moved or what it means.
#
# A MISSING baseline is FATAL, for `error_baseline_guard`'s reason: "nothing is comparing this" and
# "this compares clean" are indistinguishable from the outside. So is a run that published NO map
# while a baseline exists — that is `TestDiff.disappeared`'s shape at this artifact, and a dependent
# then silently falls back to the COMMITTED map (`PortMap.discoverIn` prefers `run-latest` and takes
# `baseline` when it is absent), so the failure is a run reading an artifact nobody produced.
#
# The verdict is PRINTED here and EXITED in `headline`, through the marker file the other guards use
# — the diff is decided before the compile, and exiting here would take the compile and the
# correlation with it.
port_map_guard() {
  local dir="$1"
  local base="$dir/baseline/port-map.tsv" run="$dir/run-latest/port-map.tsv"
  local marker="$dir/run-latest/port-map-baseline-failed"
  mkdir -p "$dir/run-latest" 2>/dev/null
  rm -f "$marker"
  if [ ! -f "$run" ]; then
    if [ ! -f "$base" ]; then
      echo "  (no port-map.tsv at $dir/run-latest — this run published no port map)"
      return 0
    fi
    echo "!! PORT MAP DISAPPEARED — the baseline has one and this run published none."
    echo "   $run does not exist. A dependent that looks this base up now takes the COMMITTED map"
    echo "   instead (PortMap.discoverIn falls back to baseline/), so it would compile against an"
    echo "   artifact no run produced — with every other count flat."
    : > "$marker"; return 1
  fi
  if [ ! -f "$base" ]; then
    echo "!! NO PORT-MAP BASELINE — nothing is comparing what this port PUBLISHES to its dependents."
    echo "   $base does not exist, so a moved emitted name, a moved shape or a stale policy= header"
    echo "   would print and pass. Seed it from this run's honest state:"
    echo "     just baseline-accept <port>"
    : > "$marker"; return 1
  fi

  # the METADATA line, field by field. Tab-delimited, so a value may contain `=` (PortMap.field).
  local bh rh moved=0 hdr=0
  bh=$(head -1 "$base"); rh=$(head -1 "$run")
  if [ "$bh" != "$rh" ]; then hdr=1; fi

  local b="$MEASURE_TMP/portmap-base-$$.tsv" r="$MEASURE_TMP/portmap-run-$$.tsv"
  grep -v '^#' "$base" > "$b"
  grep -v '^#' "$run"  > "$r"
  moved=$(diff "$b" "$r" | grep -c '^[<>]')

  if [ "$hdr" = "0" ] && [ "$moved" = "0" ]; then
    echo "  port map vs baseline: $(grep -c '' < "$r") row(s), header and rows unchanged"
    rm -f "$b" "$r"; return 0
  fi

  echo "!! PORT MAP MOVED — what this port publishes to its DEPENDENTS is not what is committed."
  if [ "$hdr" = "1" ]; then
    echo "   header:"
    local f bv rv why named=0
    for f in schema module engine sources files policy jdk; do
      bv=$(tr '\t' '\n' <<<"$bh" | grep "^$f=" | head -1 | cut -d= -f2-)
      rv=$(tr '\t' '\n' <<<"$rh" | grep "^$f=" | head -1 | cut -d= -f2-)
      [ "$bv" = "$rv" ] && continue
      case "$f" in
        schema)  why="the map's own format — every port's map is regenerated by this" ;;
        module)  why="a dependent's baseChain matches THIS string (CLAUDE.md §2.1)" ;;
        engine)  why="PortMap.freshness reports a mismatch as Stale — every dependent must re-run" ;;
        sources) why="the base's JAVA changed (content digest, not paths)" ;;
        files)   why="how many java files that digest covers" ;;
        policy)  why="the base's MANIFEST changed — the field nine dependents carried stale" ;;
        jdk)     why="the JVM that PUBLISHED this map implements a different JDK specification, and the frontend reads external members out of ITS class files (ENGINE-LIMITS M5.10)" ;;
      esac
      echo "     $f=  $bv  ->  $rv"
      echo "         $why"
      named=1
    done
    # …and a field this guard does not know about is still a change, printed raw rather than
    # silently summarised as "the header moved". A schema bump is exactly how that arrives.
    if [ "$named" = "0" ]; then
      echo "     (no named field moved — a field this guard does not enumerate did)"
      echo "     - $bh"
      echo "     + $rh"
    fi
    [ "$moved" = "0" ] && echo "   rows: unchanged."
  fi
  if [ "$moved" != "0" ]; then
    echo "   $moved row(s) differ — each is an emitted name, a disposition or a shape a dependent reads:"
    diff "$b" "$r" | grep '^[<>]' | head -20 | sed 's/^/     /'
    [ "$moved" -gt 20 ] && echo "     … $((moved - 20)) more; full files: $base and $run"
  fi
  echo "   If this is the change you made, ACKNOWLEDGE it: just baseline-accept <port>"
  echo "   …and re-measure every DEPENDENT of this module: a base's map decides their emitted text,"
  echo "   and a base port's green numbers are not evidence about its dependents (CLAUDE.md §1.5)."
  rm -f "$b" "$r"
  : > "$marker"; return 1
}

# correlate <out-report-dir> [--scalac f] [--tests f] [--srcmap [scope=]f]...
# Join compiler and test-runner output back to the MEMBER and the JAVA ORIGIN that produced it
# (DESIGN.md §6.3, and the amendment that extends it to the TEST runner). Without this, a
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
  # The display filter starts at the first report line — which means it EATS anything the
  # correlator says while dying before one is printed. CorrelateMain's missing-input abort is
  # exactly that shape (fatal, exit 2, message before the report begins), so the raw capture is
  # kept and shown whenever the exit status is non-zero: a correlation that did not happen must
  # not render as an empty-but-tidy block (the §3 false green, one artifact later).
  local cap="$MEASURE_TMP/correlate-$$.txt"
  # The sbt project that holds CorrelateMain is POLICY: the Justfile exports it (`core_project`) so
  # a module rename is one line there and never a grep through shell. UNSET IS FATAL, and there is
  # deliberately no default: the one this line used to carry (`core`) named a project the module
  # restructure had already deleted, so "correct on its own" meant "wrong, silently, until a lane
  # happened to run". A missing input is fatal and names the file to edit — CLAUDE.md §5.1, the same
  # rule that makes a non-existent `--tests` path an abort rather than an empty artifact.
  : "${CORE_PROJECT:?not set — export it from the Justfile (\`core_project\`) before sourcing scripts/_lib.sh}"
  sbt -batch "$CORE_PROJECT/runMain balticporter.tir.CorrelateMain --out $out --baseline $(dirname "$out")/baseline $*" \
    2>&1 | sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' > "$cap"
  local st=${PIPESTATUS[0]}
  sed -n '/^units in source map/,$p' "$cap" | grep -v '^\['
  if [ "$st" != "0" ]; then
    echo "!! CORRELATION DID NOT RUN — CorrelateMain exited $st; its output:"
    grep -vE '^\[' "$cap" | tail -10 | sed 's/^/     /'
    rm -f "$cap"; exit 1
  fi
  rm -f "$cap"
}

# headline <error-count> <report-dir> [more-report-dirs...]
# One line carrying BOTH gates, so the check numbers never bury the compile-error count and the
# compile-error count never hides the checks. `subject.txt` is the before->after fragment
# CLAUDE.md §5 wants in the commit subject.
#
# EVERY dir given is checked for a deferred gate's marker, and a lane must name every dir it gated.
# A two-module lane runs `show_check_report`/`findings_baseline_guard` over the LIBRARY's report and
# the SUITE's, and then called this with one of them — so a gate that fired on the other wrote a
# marker nothing read. The headline itself is still about the FIRST dir, which is the one whose
# compile the error count belongs to.
# divergence_census <report-dir> <ref-repo> <module-dir> <header-pattern> <verdict-file>
#
# THE DIVERGENCE CENSUS — every non-surface-only difference between the emitted port and the
# hand port, enriched with header evidence and joined against a committed verdict file.
#
# Reads api-parity findings from run-latest (preferred) or baseline. Produces divergence.tsv
# in run-latest/ with columns:
#   module | kind | subject | java_says | hand_port_says | evidence | status | spelling | decided_by
#
# The verdict file (`ported/<module>/divergence-verdicts.tsv`) is JOINED, not consumed: a verdict
# for a subject the census no longer produces is reported as stale. A census row with no verdict
# is `open`. The divergence.tsv itself is baselined like findings.tsv (content diff, both directions).
divergence_census() {
  local dir="$1" ref_repo="$2" module_dir="$3" header_pattern="$4" verdict_file="$5" module_label="$6"
  local findings="" out="$dir/run-latest/divergence.tsv"
  mkdir -p "$dir/run-latest"

  # Prefer run-latest, fall back to baseline
  if [ -f "$dir/run-latest/findings.tsv" ]; then
    findings="$dir/run-latest/findings.tsv"
  elif [ -f "$dir/baseline/findings.tsv" ]; then
    findings="$dir/baseline/findings.tsv"
    echo "  (no run-latest/findings.tsv — reading from baseline)"
  else
    echo "!! FATAL — no findings.tsv in $dir/run-latest or $dir/baseline"
    return 1
  fi

  # ---- Build header evidence map: file -> header lines ----
  local header_map="$MEASURE_TMP/divergence-headers-$$.tsv"
  : > "$header_map"
  local ref_src="$ref_repo/$module_dir/src/main"
  if [ -d "$ref_src" ]; then
    find "$ref_src" -name '*.scala' -print0 | while IFS= read -r -d '' f; do
      local bn
      bn=$(basename "$f" .scala)
      # Extract Migration notes sub-keys from the file header (first 60 lines)
      local notes
      notes=$(head -60 "$f" | grep -iE '^\s*\*?\s*(Fixes|Improvement|Convention|Idiom|Renames|Breaking|Divergence|Issues?):' | sed 's/^[[:space:]]*\*[[:space:]]*//' | sed 's/"/\\"/g' | tr '\n' '|' | sed 's/|$//')
      if [ -n "$notes" ]; then
        printf '%s\t%s\n' "$bn" "$notes" >> "$header_map"
      fi
    done
  fi

  # ---- 1. API divergences from api-parity findings ----
  local api_rows="$MEASURE_TMP/divergence-api-$$.tsv"
  : > "$api_rows"

  # Extract relevant families: hand-port-extra, port-extra, mutability, accessor
  grep -E 'api-parity\((hand-port-extra|port-extra|mutability|accessor)\)' "$findings" | grep -v '^#' | while IFS=$'\t' read -r _id check kind owner path _line detail; do
    local family
    family=$(echo "$check" | sed 's/api-parity(\(.*\))/\1/')

    # Determine the kind for divergence.tsv
    local div_kind="api"
    case "$family" in
      hand-port-extra) div_kind="addition" ;;
      port-extra) div_kind="omission" ;;
      mutability|accessor) div_kind="api" ;;
    esac

    # What java says vs what hand port says
    local java_says="" hand_says=""
    case "$family" in
      hand-port-extra)
        java_says="not present"
        hand_says="$detail"
        ;;
      port-extra)
        java_says="$detail"
        hand_says="not present (skipped or redesigned)"
        ;;
      mutability)
        java_says="$detail"
        hand_says="$detail"
        ;;
      accessor)
        java_says="$detail"
        hand_says="$detail"
        ;;
    esac

    # Look up header evidence by trying to match the owner's type to a file
    local type_name evidence=""
    type_name=$(echo "$owner" | sed 's/#.*//' | sed 's/\$$//')
    if [ -n "$type_name" ]; then
      evidence=$(grep "^${type_name}	" "$header_map" | head -1 | cut -f2)
    fi

    printf '%s\t%s\t%s\t%s\t%s\t%s\topen\t\t\n' \
      "$module_label" "$div_kind" "$owner" "$java_says" "$hand_says" "$evidence" >> "$api_rows"
  done

  # ---- 2. Hand-added tests ----
  local test_rows="$MEASURE_TMP/divergence-tests-$$.tsv"
  : > "$test_rows"

  local ref_test_src="$ref_repo/$module_dir/src/test"
  if [ -d "$ref_test_src" ]; then
    find "$ref_test_src" -name '*Suite.scala' -o -name '*Test.scala' -o -name '*Spec.scala' | sort | while IFS= read -r f; do
      local has_ported
      has_ported=$(head -5 "$f" | grep -ci 'Ported from' || true)
      if [ "$has_ported" = "0" ]; then
        local bn
        bn=$(basename "$f" .scala)
        # Check drop-in test results if they exist
        local test_status="blocked-by-compile"
        printf '%s\ttest\t%s\tnot applicable\thand-written test suite\t\t%s\t\t\n' \
          "$module_label" "$bn" "$test_status" >> "$test_rows"
      fi
    done
  fi

  # ---- 3. Write the TSV ----
  {
    printf '#module\tkind\tsubject\tjava_says\thand_port_says\tevidence\tstatus\tspelling\tdecided_by\n'
    # API rows, sorted
    sort -t$'\t' -k2,2 -k3,3 "$api_rows"
    # Test rows, sorted
    sort -t$'\t' -k3,3 "$test_rows"
  } > "$out"

  # ---- 4. Join with verdict file ----
  if [ -f "$verdict_file" ]; then
    # Read verdict file and apply verdicts to matching census rows.
    # The header line is `#subject<TAB>...` — skip it by matching the exact header.
    # Data subjects starting with `#` (e.g. `#SystemListener`) are real data, not comments.
    local stale_count=0
    while IFS=$'\t' read -r v_subject v_status v_evidence v_spelling v_decided_by; do
      [ "$v_subject" = "#subject" ] && continue
      [ -z "$v_subject" ] && continue
      # Escape special characters in subject for grep/awk
      local esc_subject
      esc_subject=$(printf '%s' "$v_subject" | sed 's/[.[\*^$()+?{|\\]/\\&/g')
      # Use awk for the join: find lines with matching subject (field 3) and status "open" (field 7),
      # replace status/spelling/decided_by fields
      # Match census rows whose status is either "open" or "blocked-by-compile" (test rows).
      # A verdict always wins over these initial statuses.
      awk -F'\t' -v OFS='\t' -v subj="$v_subject" -v st="$v_status" -v sp="$v_spelling" -v db="$v_decided_by" \
        '$3 == subj && ($7 == "open" || $7 == "blocked-by-compile") { $7 = st; $8 = sp; $9 = db; found=1 } { print }' "$out" > "$out.tmp"
      if grep -q "$esc_subject" "$out.tmp" 2>/dev/null; then
        mv "$out.tmp" "$out"
      else
        stale_count=$((stale_count + 1))
        echo "  !! STALE verdict: $v_subject (no longer in census)"
        rm -f "$out.tmp"
      fi
    done < "$verdict_file"
    [ "$stale_count" -gt 0 ] && echo "  $stale_count stale verdict(s) — subject no longer produced by census"
  fi

  # ---- 5. Summary ----
  # NB: subjects starting with `#` are data, not comments. Only the header line `#module` is a comment.
  local total api_count test_count
  total=$(awk -F'\t' 'NR>1 && $1 != ""' "$out" | wc -l | tr -d ' ')
  api_count=$(awk -F'\t' 'NR>1 && ($2=="api" || $2=="addition" || $2=="omission")' "$out" | wc -l | tr -d ' ')
  test_count=$(awk -F'\t' 'NR>1 && $2=="test"' "$out" | wc -l | tr -d ' ')
  local open justified unjustified
  open=$(awk -F'\t' 'NR>1 && $7=="open"' "$out" | wc -l | tr -d ' ')
  justified=$(awk -F'\t' 'NR>1 && $7=="justified"' "$out" | wc -l | tr -d ' ')
  unjustified=$(awk -F'\t' 'NR>1 && $7=="unjustified"' "$out" | wc -l | tr -d ' ')
  local not_div blocked
  not_div=$(awk -F'\t' 'NR>1 && $7=="not-a-divergence"' "$out" | wc -l | tr -d ' ')
  blocked=$(awk -F'\t' 'NR>1 && $7=="blocked-by-compile"' "$out" | wc -l | tr -d ' ')
  echo "  divergence.tsv: $total rows ($api_count api/addition/omission, $test_count test)"
  echo "  verdicts: $justified justified, $unjustified unjustified, $not_div not-a-divergence, $blocked blocked-by-compile, $open open"
  echo "  output: $out"

  rm -f "$header_map" "$api_rows" "$test_rows"
}

# divergence_baseline_guard <report-dir>
#
# The divergence.tsv baseline, following the same pattern as findings_baseline_guard.
# Content diff (id-free, since divergence.tsv has no id column), both directions.
divergence_baseline_guard() {
  local dir="$1"
  local base="$dir/baseline/divergence.tsv" run="$dir/run-latest/divergence.tsv"
  local marker="$dir/run-latest/divergence-baseline-failed"
  mkdir -p "$dir/run-latest" 2>/dev/null
  rm -f "$marker"
  if [ ! -f "$run" ]; then
    echo "  (no divergence.tsv at $dir/run-latest)"
    return 0
  fi
  if [ ! -f "$base" ]; then
    echo "  !! NO DIVERGENCE BASELINE — seed it: just baseline-accept <port>"
    return 0  # not fatal on the first run
  fi
  local b="$MEASURE_TMP/div-base-$$.tsv" r="$MEASURE_TMP/div-run-$$.tsv"
  # Skip only the header line (`#module ...`), not data subjects starting with `#`
  awk 'NR>1' "$base" > "$b"
  awk 'NR>1' "$run"  > "$r"
  local moved
  moved=$(diff "$b" "$r" | grep -c '^[<>]' || true)
  if [ "$moved" = "0" ]; then
    echo "  divergence vs baseline: $(grep -c '' < "$r") row(s), content unchanged"
    rm -f "$b" "$r"; return 0
  fi
  echo "!! DIVERGENCE CONTENT MOVED — $moved line(s) differ from baseline."
  diff "$b" "$r" | grep '^[<>]' | head -10 | sed 's/^/     /'
  [ "$moved" -gt 10 ] && echo "     ... $((moved - 10)) more"
  echo "   Acknowledge: just baseline-accept <port>"
  rm -f "$b" "$r"
  : > "$marker"; return 1
}

headline() {
  local errors="$1" dir="$2"
  shift 2
  local extra=("$@")
  local checks="(no check report)"
  [ -f "$dir/run-latest/subject.txt" ] && checks="$(cat "$dir/run-latest/subject.txt")"
  local tests=""
  if [ -f "$dir/run-latest/tests.tsv" ]; then
    local p f
    p=$(grep -c $'\tpass$' "$dir/run-latest/tests.tsv" || true)
    f=$(grep -c $'\tfail$' "$dir/run-latest/tests.tsv" || true)
    tests=" | tests $p passing, $f failing"
    # a test the runner never reached is neither, and must never be summarised as absence. Shown only
    # when non-zero, so the headline carries no permanent "0 skipped" for a reader to learn to skip.
    local s
    s=$(grep -c $'\tskipped$' "$dir/run-latest/tests.tsv" || true)
    [ "$s" != "0" ] && tests="$tests, !! $s DID NOT RUN"
    # a NEWLY failing test is the one number that must never scroll past: it is the only signal
    # this project has for the CLAUDE.md §4.4 defect class, which moves no compile-error count.
    # NEWLY SKIPPED is its sibling and gates the same way — a skip moves no pass and no fail
    # count, which is exactly how ashley lost two tests from every artifact.
    if grep -q "^-- NEWLY FAILING" "$dir/run-latest/tests-diff.txt" 2>/dev/null; then
      tests="$tests | !! NEWLY FAILING — see $dir/run-latest/tests-diff.txt"
    fi
    if grep -q "^-- NEWLY SKIPPED" "$dir/run-latest/tests-diff.txt" 2>/dev/null; then
      tests="$tests | !! NEWLY SKIPPED — see $dir/run-latest/tests-diff.txt"
    fi
  fi
  echo
  echo "=================================================================="
  echo "HEADLINE  errors=$errors | $checks$tests"
  echo "=================================================================="
  # …and the ERROR-COUNT gate, exited HERE rather than where it was decided, so a regression still
  # gets its correlation printed (see `error_baseline_guard`). Last line of the lane, non-zero, so
  # `measure-all` stops the sequence exactly as it does for a lost test.
  if [ -f "$dir/run-latest/errors-baseline-failed" ]; then
    echo "!! this lane FAILED its error baseline — see the 'errors vs baseline' line above"
    exit 1
  fi
  # …and the TEST-DISCOVERY gate, deferred here for the same reason: it is decided before the
  # compile, and exiting there would take the compile and the correlation with it.
  if [ -f "$dir/run-latest/tests-lost-baseline-failed" ]; then
    echo "!! this lane FAILED its test-discovery baseline — see the 'tests lost' line above"
    exit 1
  fi
  # …and the FINDINGS-CONTENT gate, over every report this lane produced. Last of the three, because
  # it is the one whose diff is already printed in full above.
  local d
  for d in "$dir" "${extra[@]}"; do
    if [ -f "$d/run-latest/findings-baseline-failed" ]; then
      echo "!! this lane FAILED its findings baseline ($d) — see the 'FINDINGS CONTENT MOVED' block above"
      exit 1
    fi
  done
  # …and the PORT-MAP gate, over every report this lane produced, for the same reason: a two-module
  # lane publishes two maps and a dependent may read either.
  for d in "$dir" "${extra[@]}"; do
    if [ -f "$d/run-latest/port-map-baseline-failed" ]; then
      echo "!! this lane FAILED its port-map baseline ($d) — see the 'PORT MAP MOVED' block above"
      exit 1
    fi
  done
  # …and the CROSS-PLATFORM COMPILE gates — one marker per platform per report dir.
  for d in "$dir" "${extra[@]}"; do
    for plat_suffix in js native; do
      if [ -f "$d/run-latest/errors-baseline-failed.${plat_suffix}" ]; then
        echo "!! this lane FAILED its ${plat_suffix} error baseline ($d) — see the '${plat_suffix} ERRORS' line above"
        exit 1
      fi
    done
  done
  # …and the REFERENCE-FLAGS COMPILE gate — one marker per report dir.
  for d in "$dir" "${extra[@]}"; do
    if [ -f "$d/run-latest/errors-baseline-failed.ref" ]; then
      echo "!! this lane FAILED its ref error baseline ($d) — see the 'ref ERRORS' line above"
      exit 1
    fi
  done
}

# xplat_compile <platform> <scala-version> <report-dir> <capture-basename> <source-dirs...> [-- <extra-flags...>]
#
# Cross-platform compile gate: runs `scala-cli compile --platform <platform>` over the same
# source tree the JVM lane just compiled, counts errors, and baselines them.
#
# This is a COMPILE gate, not a portability gate (ENGINE-LIMITS P1): the Scala.js and Native
# compilers type-check against their own javalib, so a `java.lang.reflect.Field` that the JVM
# has and JS/Native do not is a real compile error here. The portability(all|emitted|injected)
# lanes stay as the TIR-level API-presence check.
#
# DEPENDENCIES are passed through the `--` separator, the SAME classpath the JVM compile gets.
# Both coordinate forms resolve on JS/Native for TYPE-CHECKING (not linking, which is not what
# this lane does): a Scala cross-published artifact (`org.scalameta::munit:1.0.2`, the `::`
# platform form) resolves the platform-specific JAR, and a Java-only artifact
# (`junit:junit:4.13.2`, the `:` form) resolves the JVM JAR whose class files scalac reads for
# type signatures. `portability(all|emitted|injected)` stays as the TIR-level check for
# whether those APIs exist off-JVM (ENGINE-LIMITS P1).
#
# `declared_dep_flags` output (explicit JVM coordinates, `org:name_3:rev`) also resolves on
# JS/Native — scalac type-checks against JVM class files, only linking would fail.
# `--jar` directories (e.g. liqp_parser_classes) are passed the same way.
#
# Each call site in the Justfile passes its lane's deps after `--`, e.g.:
#   xplat_compile scala-js ... <srcs> -- --test $DEPS
#
# The JS and Native version pins come from `project/plugins.sbt` and match sge's toolchain:
# Scala.js 1.22.0, Scala Native 0.5.12. scala-cli 1.16.0 defaults to these exact versions,
# so no explicit version flags are needed.
xplat_compile() {
  local platform="$1" scala_ver="$2" report_dir="$3" capture_base="$4"
  shift 4
  # Collect source dirs until we hit '--' or run out
  local -a srcs=()
  local -a extra_flags=()
  local past_sep=0
  for arg in "$@"; do
    if [ "$arg" = "--" ]; then past_sep=1; continue; fi
    if [ "$past_sep" = "1" ]; then extra_flags+=("$arg"); else srcs+=("$arg"); fi
  done

  local plat_suffix
  case "$platform" in
    scala-js)     plat_suffix="js" ;;
    scala-native) plat_suffix="native" ;;
    *)            echo "!! xplat_compile: unknown platform '$platform'"; return 1 ;;
  esac

  local cap="$MEASURE_TMP/${capture_base}.${plat_suffix}.txt"
  echo
  echo "-- cross-platform compile: ${plat_suffix} --"
  scala-cli compile --platform "$platform" --scala "$scala_ver" --server=false ${jdk_version:+--jvm "$jdk_version"} \
    "${extra_flags[@]}" "${srcs[@]}" \
    2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$cap"
  local cli_st=${PIPESTATUS[0]}
  local errors
  errors=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$cap")
  # compile_guard for the xplat compile — abort/crash detection
  compile_guard "$cli_st" "$errors" "$cap"
  echo "TOTAL ERRORS (${plat_suffix}): $errors  (coded $(grep -cE '\[E[0-9]+\].*Error' "$cap") + bare $(grep -cE '^-- Error:' "$cap"))"
  # top error families
  grep -oE "\[E[0-9]+\][^:]*Error" "$cap" | sort | uniq -c | sort -rn | head -5

  # baseline guard — same logic as error_baseline_guard but with a platform suffix
  local expected_file="$report_dir/baseline/expected-errors.${plat_suffix}"
  local marker="$report_dir/run-latest/errors-baseline-failed.${plat_suffix}"
  mkdir -p "$report_dir/run-latest" 2>/dev/null
  echo "$errors" > "$report_dir/run-latest/errors-count.${plat_suffix}"
  rm -f "$marker"
  if [ ! -f "$expected_file" ]; then
    echo "!! NO ${plat_suffix} ERROR BASELINE — $expected_file does not exist."
    echo "   Seed it: just baseline-accept <port>"
    : > "$marker"; return 1
  fi
  local expected
  expected=$(tr -dc '0-9' < "$expected_file")
  if [ -z "$expected" ]; then
    echo "!! ${plat_suffix} ERROR BASELINE UNREADABLE — $expected_file holds no number."
    : > "$marker"; return 1
  fi
  if [ "$errors" = "$expected" ]; then
    echo "  errors vs baseline (${plat_suffix}): $errors = $expected  (unchanged)"
    return 0
  fi
  if [ "$errors" -gt "$expected" ]; then
    echo "!! ${plat_suffix} ERRORS ROSE — $expected -> $errors."
  else
    echo "!! ${plat_suffix} ERRORS FELL — $expected -> $errors. Acknowledge: just baseline-accept <port>"
  fi
  : > "$marker"; return 1
}

# flags_compile <scala-version> <report-dir> <capture-basename> <flags-string> <source-dirs...> [-- <extra-flags...>]
#
# REFERENCE-BUILD compile gate: runs `scala-cli compile` with the reference repo's own
# scalacOptions over the emitted tree and counts errors, baselined as `expected-errors.ref`.
#
# This is the FOURTH compile in every lane (after JVM, JS, Native). The reference build
# compiles with `-no-indent -Werror -Wunused:privates,locals,patvars …` — flags that promote
# warnings to errors and reject indentation syntax. A port that is green under scala-cli's
# defaults and red under `-no-indent -Werror` is not at the bar (DESIGN.md §8.24).
#
# The FLAGS are a whitespace-separated string, read from a Justfile variable that states the
# source (SgePlugin.strictScalacOptions for sge ports, ssg/build.sbt for ssg ports). Macro
# settings (`-Xmacro-settings:*`) are dropped — they are timeouts that carry no diagnostic
# and produce no warning.
#
# The compile is run through the correlator so that the resulting `errors.tsv`-style output
# carries the member column — a `-Werror` row is a scalac diagnostic like any other, and the
# agent needs to know which Java line produced the unused local.
#
# The baseline file is `expected-errors.ref`, the marker file is `errors-baseline-failed.ref`,
# and both travel through `headline` like the other three compile gates.
flags_compile() {
  local scala_ver="$1" report_dir="$2" capture_base="$3" flags_str="$4"
  shift 4
  # Collect source dirs until we hit '--' or run out
  local -a srcs=()
  local -a extra_flags=()
  local past_sep=0
  for arg in "$@"; do
    if [ "$arg" = "--" ]; then past_sep=1; continue; fi
    if [ "$past_sep" = "1" ]; then extra_flags+=("$arg"); else srcs+=("$arg"); fi
  done

  # Parse the flags string, dropping -Xmacro-settings:*
  local -a ref_flags=()
  for flag in $flags_str; do
    case "$flag" in
      -Xmacro-settings:*) ;; # dropped: macro timeouts, not diagnostics
      *) ref_flags+=("$flag") ;;
    esac
  done

  if [ "${#ref_flags[@]}" = "0" ]; then
    echo "!! flags_compile: no reference flags — nothing to compile with"
    return 1
  fi

  local cap="$MEASURE_TMP/${capture_base}.ref.txt"
  echo
  echo "-- reference-flags compile: ${ref_flags[*]} --"
  scala-cli compile --scala "$scala_ver" --server=false ${jdk_version:+--jvm "$jdk_version"} \
    "${ref_flags[@]}" "${extra_flags[@]}" "${srcs[@]}" \
    2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$cap"
  local cli_st=${PIPESTATUS[0]}
  # SCOPE: count only diagnostics in the MODULE THIS LANE MEASURES. A dependent lane passes its
  # base's emitted tree as SOURCES (scala-cli has no jar of it), so the base's own `-Werror`
  # warnings would land in the dependent's count a second time — measured 2026-08-26: anim8 read
  # 307 of which 262 were the base's deprecations, already counted as gdx's 651. The module is the
  # root of the LAST source dir (everything up to `/src_managed` or `/src`); its main and test trees
  # are both "own", the base's tree is not, and the excluded count is printed so nobody reads 0.
  local own_root; own_root="${srcs[${#srcs[@]}-1]}"
  own_root="${own_root%%/src_managed/*}"; own_root="${own_root%%/src/*}"
  local own_cap="$cap.own"
  awk -v root="$own_root/" 'BEGIN{keep=0} /^-- /{keep=index($0, root)>0} {if(keep)print}' "$cap" > "$own_cap"
  local errors werrors=0 excluded
  errors=$(grep -cE '^-- (\[E[0-9]+\] )?.*Error' "$own_cap")
  excluded=$(( $(grep -cE '^-- (\[E[0-9]+\] )?.*(Error|Warning)' "$cap") - $(grep -cE '^-- (\[E[0-9]+\] )?.*(Error|Warning)' "$own_cap") ))
  echo "   (scoped to $own_root — $excluded diagnostic(s) in other trees on the source path are the BASE's and counted by its own lane)"
  # Under -Werror EVERY warning diagnostic is an error in the reference build: scalac still prints
  # them as `-- Warning:` / `-- [Exxx] … Warning:` and adds ONE closing error, `No warnings can be
  # incurred under -Werror`, which matches no Error pattern above — so a tree with 307 warnings
  # and no real error counted 0 and compile_guard refused to report it (measured on anim8,
  # 2026-08-26). The warnings ARE the count; the closing line is not a diagnostic of its own.
  case " ${ref_flags[*]} " in
    *" -Werror "*) werrors=$(grep -cE '^-- (\[E[0-9]+\] )?.*Warning' "$own_cap"); errors=$((errors + werrors)) ;;
  esac
  # compile_guard for the ref compile — abort/crash detection
  compile_guard "$cli_st" "$((errors + excluded))" "$cap"
  echo "TOTAL ERRORS (ref): $errors  (coded $(grep -cE '\[E[0-9]+\].*Error' "$own_cap") + bare $(grep -cE '^-- Error:' "$own_cap") + warnings-under-Werror $werrors)"
  # top families, warnings included — under -Werror they are the population
  grep -oE "\[E[0-9]+\][^:]*(Error|Warning)|^-- (Error|Warning)" "$own_cap" | sort | uniq -c | sort -rn | head -6

  # baseline guard — same logic as error_baseline_guard but with a .ref suffix
  local expected_file="$report_dir/baseline/expected-errors.ref"
  local marker="$report_dir/run-latest/errors-baseline-failed.ref"
  mkdir -p "$report_dir/run-latest" 2>/dev/null
  echo "$errors" > "$report_dir/run-latest/errors-count.ref"
  rm -f "$marker"
  if [ ! -f "$expected_file" ]; then
    echo "!! NO ref ERROR BASELINE — $expected_file does not exist."
    echo "   Seed it: just baseline-accept <port>"
    : > "$marker"; return 1
  fi
  local expected
  expected=$(tr -dc '0-9' < "$expected_file")
  if [ -z "$expected" ]; then
    echo "!! ref ERROR BASELINE UNREADABLE — $expected_file holds no number."
    : > "$marker"; return 1
  fi
  if [ "$errors" = "$expected" ]; then
    echo "  errors vs baseline (ref): $errors = $expected  (unchanged)"
    return 0
  fi
  if [ "$errors" -gt "$expected" ]; then
    echo "!! ref ERRORS ROSE — $expected -> $errors."
  else
    echo "!! ref ERRORS FELL — $expected -> $errors. Acknowledge: just baseline-accept <port>"
  fi
  : > "$marker"; return 1
}
