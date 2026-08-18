#!/bin/bash
# ssg-md's COMMONMARK CONFORMANCE CONTROL, as a lane. SOURCED BY THE `Justfile` (`just md-conformance`)
# — the `md_*` variables arrive as the environment below and nothing here names a path a variable
# already holds.
#
# WHAT THIS LANE IS FOR. `PROGRESS.md` §10.6.7 quotes "1,870 of 1,870 spec examples (100 %), against
# a MEASURED green java control" and a per-example table beside it. That census was produced BY HAND,
# and `CLAUDE.md` §5's rule is that a number is reproduced by a lane or it is not quoted. This is the
# lane. It measures the CONTROL — the upstream java — because the control is the half nothing else in
# this repository ever touches: `md-test-measure` runs the PORT's suites and would report a green
# `OK` for a port that agreed with a java library which was itself wrong about the spec.
#
# WHY IT IS NOT PART OF `md-test-measure`, and not in `measure-all`. It compiles ~520 upstream java
# files with `javac` and drives four spec renderings; that is a control, not a measurement OF THE
# PORT, and it moves only when the UPSTREAM tree or the spec resources move — which is never, between
# corpus waves. A lane whose inputs are constant does not belong in the serial gate that every commit
# runs. Run it when the conformance claim is quoted, changed, or doubted.
#
# NOTE THE ABSENCE OF `set -e`, exactly as `scripts/_lib.sh` says: `grep -c` exits 1 when it counts
# zero and counting zero failures is this lane's success case. Every guard here is explicit and names
# what went wrong, which is the thing `set -e` cannot do.

# The one variable this file computes rather than receives. `.balticporter/` is gitignored and is
# where every lane's scratch already goes (`scripts/_lib.sh`'s `MEASURE_TMP`), which matters for
# `CLAUDE.md` §5.5's reason and not for tidiness: a `git status` that cannot distinguish a DECISION
# from an ARTEFACT defeats the whole measurement discipline, and ~520 java class files are an
# artefact.
BUILD="$(pwd)/.balticporter/tmp/md-conformance"
CONTROL_OUT="$BUILD/control"
PORT_OUT="$BUILD/port"
FAIL=0
WITH_PORT=0
for arg in "$@"; do
  case "$arg" in
    --with-port) WITH_PORT=1 ;;
    "") ;;
    *) echo "!! unknown argument: $arg   (the only one is --with-port)"; exit 2 ;;
  esac
done

for v in MD_SRC MD_MODULES MD_TEST_SRC MD_SPEC_RES MD_LIB_RES MD_TUTIL_RES MD_DEPS MD_TEST_DEPS; do
  if [ -z "${!v}" ]; then echo "!! $v is not set — this file is invoked from the Justfile, which exports it"; exit 2; fi
done

rm -rf "$BUILD"
mkdir -p "$BUILD/classes" "$CONTROL_OUT"

# ---------------------------------------------------------------------------------------------
# THE JARS — the SAME three the port lane compiles against, derived from the SAME two variables.
#
# `md_deps` and `md_test_deps` are written in scala-cli's `--dependency <coord>` form, so the
# coordinates are every token that follows one. The `::` ones are dropped, and that is a STRUCTURAL
# rule and not a list: `::` is scala-cli's marker for an artifact whose name carries a Scala binary
# suffix, which is by construction a Scala artifact and by construction not something `javac` can
# use. Today that drops exactly munit — the runner the CONVERSION targets, which the upstream java
# has never heard of. Everything else is shared, which is the point: a control compiled against
# different jars than the port is not a control.
#
# `org.hamcrest:hamcrest-core:1.3` is NOT named here and arrives with junit transitively, exactly as
# `md_test_deps`' own comment says it does for the port.
COORDS=$(echo "$MD_DEPS $MD_TEST_DEPS" | tr ' ' '\n' | grep -v '^--dependency$' | grep -v '^$' | grep -v '::')
CS=$(command -v cs || command -v coursier)
if [ -z "$CS" ]; then
  echo "!! neither \`cs\` nor \`coursier\` is on PATH — this lane resolves the control's jars with it"
  exit 2
fi
JARS=$("$CS" fetch -p $COORDS 2>&1 | tail -1)
if [ -z "$JARS" ] || [[ "$JARS" != *".jar"* ]]; then
  echo "!! could not resolve the control's jars from: $COORDS"
  echo "$JARS"
  exit 2
fi
echo "-- control jars --"
echo "$JARS" | tr ':' '\n' | sed 's|.*/||' | sed 's/^/   /'
echo

# ---------------------------------------------------------------------------------------------
# THE JAVA SOURCES — `md_modules` plus the two halves of `md_test_src` this control needs.
#
# THE SELECTION IS STRUCTURAL AND NAMES NO MODULE. `md_test_src` holds three kinds of entry and the
# control wants two of them: the `.java` FILES (the five `flexmark-core-test` suites — written out
# one by one in the variable for `md_test_src`'s own documented reason) and the `src/main/java`
# DIRECTORY (the test harness, which is a MAIN source set of its own module). What it does not want
# is the `src/test` directory — that is the 52-file unit suite, which drives no spec and whose 730
# `@Test` would turn `JUnitCore` into a second run of a lane that already exists. Reading the kind
# off the path's own shape rather than off a library's name is `CLAUDE.md` §4.56 at a shell script.
SRC_DIRS=""
for m in $MD_MODULES; do SRC_DIRS="$SRC_DIRS $MD_SRC/$m/src/main/java"; done
SRC_FILES=""
for e in $MD_TEST_SRC; do
  case "$e" in
    *.java)          SRC_FILES="$SRC_FILES $e" ;;
    */src/main/java) SRC_DIRS="$SRC_DIRS $e" ;;
    *)               ;;   # a `src/test` tree — this control does not compile it, see above
  esac
done

for d in $SRC_DIRS; do
  if [ ! -d "$d" ]; then echo "!! missing java source directory: $d"; exit 2; fi
done
for f in $SRC_FILES; do
  if [ ! -f "$f" ]; then echo "!! missing java source file: $f"; exit 2; fi
done

find $SRC_DIRS -name '*.java' > "$BUILD/files.txt"
for f in $SRC_FILES; do echo "$f" >> "$BUILD/files.txt"; done
JAVA_FILES=$(wc -l < "$BUILD/files.txt" | tr -d ' ')
echo "-- javac: $JAVA_FILES upstream java files --"
javac -nowarn -encoding UTF-8 -d "$BUILD/classes" -cp "$JARS" "@$BUILD/files.txt" 2>&1 | tail -20
JAVAC_STATUS=${PIPESTATUS[0]}
if [ "$JAVAC_STATUS" != "0" ]; then
  echo "!! the CONTROL DOES NOT COMPILE — there is no control, and nothing below is a measurement"
  exit 1
fi
echo "   ok"
echo

# The driver, against the classes it just produced. It reproduces the suite's own construction and
# adds only the SPLIT — see its header for why the per-example reading needs a driver at all.
javac -nowarn -encoding UTF-8 -d "$BUILD/classes" -cp "$JARS:$BUILD/classes" \
  scripts/md-conformance/MdConformanceControl.java 2>&1 | tail -20
if [ "${PIPESTATUS[0]}" != "0" ]; then
  echo "!! the census driver does not compile against the control"
  exit 1
fi

# THE THREE RESOURCE DIRECTORIES, at their UPSTREAM paths — the spec files, the LIBRARY's own
# `entities.properties` and the harness's module marker. `md_spec_res` documents all three and why
# they are the upstream's own bytes; a missing one is loud on the java side too
# (`IllegalStateException: Could not load …`).
RUN_CP="$BUILD/classes:$JARS:$MD_SPEC_RES:$MD_LIB_RES:$MD_TUTIL_RES"

# ---------------------------------------------------------------------------------------------
# (b) THE SUITE AS UPSTREAM RUNS IT — four classes, `org.junit.runner.JUnitCore`, one assertion each.
#
# This is the claim `PROGRESS.md` §10.6.7 calls "a MEASURED green java control", and it is reported
# BEFORE the per-example split because it is the coarser and more authoritative of the two: the split
# is this repository's reading of the suite, the `OK (4 tests)` is the suite.
#
# `FullOrigSpec029CoreTest` is one of the four and its `@Test` is a VACUOUS PASS on both sides —
# `getSpecResourceLocation` returns `ResourceLocation.NULL` and `testSpecExample` returns
# immediately. It is counted in the 4 because upstream counts it in the 4; it renders nothing.
echo "-- (b) the four FullOrigSpec*CoreTest under JUnitCore --"
java -cp "$RUN_CP" org.junit.runner.JUnitCore \
  com.vladsch.flexmark.core.test.util.renderer.FullOrigSpecCoreTest \
  com.vladsch.flexmark.core.test.util.renderer.FullOrigSpec027CoreTest \
  com.vladsch.flexmark.core.test.util.renderer.FullOrigSpec028CoreTest \
  com.vladsch.flexmark.core.test.util.renderer.FullOrigSpec029CoreTest \
  > "$BUILD/junit.txt" 2>&1
JUNIT_STATUS=$?
tail -20 "$BUILD/junit.txt"
if [ "$JUNIT_STATUS" != "0" ] || ! grep -q '^OK (4 tests)$' "$BUILD/junit.txt"; then
  echo "!! THE CONTROL IS NOT GREEN — every conformance number below is measured against a broken oracle"
  FAIL=1
fi
echo

# ---------------------------------------------------------------------------------------------
# (c) + (d) THE PER-EXAMPLE SPLIT — the shape of §10.6.7's table.
echo "-- (c)+(d) per-example census, java control --"
java -cp "$RUN_CP" MdConformanceControl dump "$CONTROL_OUT"
if [ "$?" != "0" ]; then
  echo "!! the census driver failed — no per-example number was produced"
  exit 1
fi
echo

# THE GATE. Three live specs, and the control is green on them or this lane has nothing to say.
# Read from the artifact the driver wrote and not from its stdout, so the number a later step diffs
# and the number the gate reads are the same number.
LIVE_FAILING=$(awk -F'\t' 'NR==2 { print $3 }' "$CONTROL_OUT/live-totals.tsv")
LIVE_EXAMPLES=$(awk -F'\t' 'NR==2 { print $1 }' "$CONTROL_OUT/live-totals.tsv")
LIVE_PASSING=$(awk -F'\t' 'NR==2 { print $2 }' "$CONTROL_OUT/live-totals.tsv")
if [ "$LIVE_FAILING" != "0" ]; then
  echo "!! THE JAVA CONTROL FAILS $LIVE_FAILING OF ITS OWN $LIVE_EXAMPLES EXAMPLES on the three specs it runs."
  echo "   A port measured against this is measured against nothing. Rows:"
  grep -h "FAIL" "$CONTROL_OUT"/spec.txt.status.tsv "$CONTROL_OUT"/spec.0.27.txt.status.tsv "$CONTROL_OUT"/spec.0.28.txt.status.tsv | head -40
  FAIL=1
else
  echo "CONTROL: $LIVE_PASSING of $LIVE_EXAMPLES examples on the three specs java runs — green."
fi

# 0.29 IS REPORTED APART AND IS NEVER ADDED IN. Upstream disables it
# (`return ResourceLocation.NULL` under `// FIX: implement 0.29 spec and enable test`), so java runs
# zero of its examples; this lane drives it anyway because the port's own 0.29 residue is only
# readable against a control, and a spec version flexmark never implemented is not a conformance
# claim anybody makes.
F029=$(awk -F'\t' '$4 == "FAIL"' "$CONTROL_OUT/spec.0.29.txt.status.tsv" | wc -l | tr -d ' ')
N029=$(awk -F'\t' '!/^#/ && NF' "$CONTROL_OUT/spec.0.29.txt.status.tsv" | wc -l | tr -d ' ')
echo "0.29 (NOT RUN by the java suite, NOT part of the $LIVE_EXAMPLES): java itself fails $F029 of its $N029."

# ---------------------------------------------------------------------------------------------
# --with-port — the same census over the EMITTED Scala, and the java-vs-port classification of 0.29.
#
# OFF BY DEFAULT because it compiles both emitted source sets, which is minutes, and because the
# control is the deliverable: the PORT's own conformance is what `md-test-measure` already runs the
# suites for. What only this half can answer is WHICH of the port's 0.29 residue is a port defect and
# which is a rule flexmark never implemented — a question with no meaning until there is a control.
if [ "$WITH_PORT" = "1" ]; then
  echo
  if [ -z "$MD_MODULE" ] || [ -z "$SCALA_VERSION" ]; then
    echo "!! MD_MODULE / SCALA_VERSION not set — --with-port needs the port's own emission and scala version"
    exit 2
  fi
  if [ ! -d "$MD_MODULE/src_managed/test/scala" ]; then
    echo "!! $MD_MODULE/src_managed/test/scala does not exist — run \`just md-test-measure\` first"
    exit 2
  fi
  mkdir -p "$PORT_OUT"
  echo "-- compiling the port, both source sets, exactly as \`md-test-measure\` does --"
  # BOTH SOURCE SETS ON ONE INVOCATION, WITH `--test`, and then the CLASS PATH — three things, none
  # of them incidental.
  #
  # The port is `RuntimeMode.Vendored`, so its shims live in `src_managed/main` and the suite links
  # against them there: compiling either alone measures nothing. `--test` is what makes the TEST
  # scope's errors reported rather than only its warnings — the same flag and the same reason as
  # `md-test-measure`'s compile.
  #
  # AND THE DRIVER CANNOT SIMPLY BE A THIRD SOURCE ON THAT LINE. scala-cli decides a source's SCOPE
  # from its PATH, so every emitted file under `src_managed/test/` is test-scope by construction and
  # a driver handed to the same invocation is MAIN-scope — `value test is not a member of ssg.md`,
  # measured. So the compile publishes its class path and the driver is a separate, ordinary `run`
  # against it. That also keeps `scala-cli test` out of this: running the port's own suites here
  # would be 725 registrations to obtain one census, and `md-test-measure` already runs them.
  scala-cli compile --test --scala "$SCALA_VERSION" --server=false $MD_DEPS $MD_TEST_DEPS \
    "$MD_MODULE/src_managed/main/scala" "$MD_MODULE/src_managed/test/scala" \
    --print-class-path 2>&1 | sed 's/\x1b\[[0-9;]*m//g' > "$BUILD/port-cp.txt"
  PORT_CP=$(tail -1 "$BUILD/port-cp.txt")
  if [[ "$PORT_CP" != *"/classes/"* ]]; then
    echo "!! the port did not compile — there is nothing to census. Last lines:"
    grep -E '^-- (\[E[0-9]+\] )?.*Error' "$BUILD/port-cp.txt" | head -20
    tail -5 "$BUILD/port-cp.txt"
    exit 1
  fi

  echo "-- the same census over the emitted Scala --"
  # The three `--resource-dir` flags are `md-test-measure`'s own, for `md_spec_res`'s own documented
  # reasons: the spec files, the LIBRARY's own `entities.properties` (a static initialiser reads it,
  # so every `&nbsp;` needs it) and the harness's module marker.
  scala-cli run --scala "$SCALA_VERSION" --server=false $MD_DEPS $MD_TEST_DEPS \
    --jar "$PORT_CP" \
    --resource-dir "$(pwd)/$MD_SPEC_RES" \
    --resource-dir "$(pwd)/$MD_LIB_RES" \
    --resource-dir "$(pwd)/$MD_TUTIL_RES" \
    --main-class MdConformancePort \
    scripts/md-conformance/MdConformancePort.scala \
    -- "$PORT_OUT" 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | tee "$BUILD/port-run.txt"
  if ! grep -q "THE THREE LIVE" "$BUILD/port-run.txt"; then
    echo "!! the port-side census did not run — nothing to classify against"
    exit 1
  fi

  echo
  # THE WHOLE-DUMP COMPARISON, which is a STRONGER statement than either census and costs one `cmp`.
  # Both sides passing 1,870 of 1,870 says each equals the SPEC on the three live versions; it says
  # nothing about the 649 examples of 0.29, where both sides are wrong about the spec and the only
  # question is whether they are wrong the SAME WAY. A byte-identical dump answers both at once — the
  # port and the java library render every example in the file identically, the ones neither gets
  # right included.
  echo "-- the two renderings, whole file, byte for byte --"
  for k in spec.txt spec.0.27.txt spec.0.28.txt spec.0.29.txt; do
    if cmp -s "$CONTROL_OUT/$k.actual" "$PORT_OUT/$k.actual"; then
      echo "   $k: java control and port IDENTICAL"
    else
      echo "   $k: THE TWO RENDERINGS DIFFER — see the per-example classification below"
    fi
  done

  echo
  echo "-- 0.29: java control against the port, example by example --"
  java -cp "$RUN_CP" MdConformanceControl classify "$CONTROL_OUT" "$PORT_OUT" spec.0.29.txt
  if [ "$?" != "0" ]; then echo "!! the classification failed"; FAIL=1; fi
fi

echo
if [ "$FAIL" != "0" ]; then
  echo "!! md-conformance FAILED"
  exit 1
fi
echo "md-conformance: ok"
