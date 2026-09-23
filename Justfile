# Baltic Porter — generic recipes.
#
# Per-library measure lanes, baselines and port projects live in each consumer
# repository (lls, sge, ssg). This file holds the engine's own tools: the
# difference catalog, the consumer regression check, the debugging surface,
# comment-lint, Metals and the TS/JS RAST exporters.
#
#   just consumers-check               publish the engine locally and test lls, sge, ssg against it
#   just catalog                       render balticporter.catalog to .balticporter/catalog.md
#   just debug-flags [PORT]            WHICH layer defines each balticporter.* flag right now
#   just debug-set   KEY VALUE         write one flag into .balticporter/debug.properties
#   just debug-clear [KEY]             remove one flag, or ALL of them
#   just debug-emit  ROOT FQN [PHASES] one type's TIR + Scala, around a phase boundary
#   just correlate   OUT [--scalac f]  join a compiler / test-runner log to members
#   just debug-selfcheck               proves the debug recipes do what they say
#   just comment-lint [RANGE]          comment blocks over 5 lines, narrative words in added comments
#   just metals-start / -stop / -status / -call
#   just ts-export TSCONFIG OUT       export a TypeScript project's syntax trees with the frontend's exporter

# sbt projects
core_project  := "engine"

root := justfile_directory()
bp_root := env_var_or_default("BP_ROOT", justfile_directory())
sbt_migrate := "sbt --client"

_default:
    @{{just_executable()}} --list --unsorted

# ---------------------------------------------------------------------------------------------
# Consumer regression check — the engine's gate against the libraries that depend on it.
# ---------------------------------------------------------------------------------------------
[doc("publish the committed engine locally and run each consumer's own tests against it (level: jvm | full; consumers default to lls sge)")]
consumers-check level="jvm" *consumers:
    "{{root}}/scripts/consumers-check.sh" {{level}} {{consumers}}

# ---------------------------------------------------------------------------------------------
# The difference catalog, rendered.
# ---------------------------------------------------------------------------------------------
[doc("render balticporter.catalog to .balticporter/catalog.md (a build product)")]
catalog:
    #!/usr/bin/env bash
    cd "{{root}}"
    mkdir -p .balticporter
    sbt -batch "api/runMain balticporter.catalog.CatalogDoc" | sed -n '/^# The difference catalog/,$p' > .balticporter/catalog.md
    echo "-> .balticporter/catalog.md ($(wc -l < .balticporter/catalog.md) lines)"

# ---------------------------------------------------------------------------------------------
# THE DEBUGGING SURFACE — CLAUDE.md §4.6, reachable.
# ---------------------------------------------------------------------------------------------
[doc("WHICH layer defines each balticporter.* flag right now (and what PORT's last run saw)")]
debug-flags PORT="":
    #!/usr/bin/env bash
    cd "{{root}}"
    mkdir -p .balticporter/tmp
    CAP=".balticporter/tmp/debug-flags-$$.txt"
    ARGS="--root {{bp_root}}"
    [ -n "{{PORT}}" ] && ARGS="$ARGS --port {{PORT}}"
    {{sbt_migrate}} "{{core_project}}/runMain balticporter.tir.DebugFlagsMain $ARGS" 2>&1 |
      sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' > "$CAP"
    st=${PIPESTATUS[0]}
    sed -n '/flag resolution under/,$p' "$CAP" | grep -vE '^\[(info|warn|error|success)\]'
    if [ "$st" != "0" ]; then
      echo "!! debug-flags DID NOT RUN — sbt exited $st; its output:"
      tail -20 "$CAP" | sed 's/^/     /'
      rm -f "$CAP"; exit 1
    fi
    rm -f "$CAP"

[doc("write one flag into .balticporter/debug.properties — the hand-written layer, which beats run.properties (a -D beats both, but never reaches a forked migration)")]
debug-set KEY VALUE:
    #!/usr/bin/env bash
    cd "{{root}}"
    F="{{bp_root}}/.balticporter/debug.properties"
    mkdir -p "$(dirname "$F")"
    K="{{KEY}}"
    case "$K" in balticporter.*) ;; *) K="balticporter.$K" ;; esac
    [ -f "$F" ] || printf '# hand-written debug flags (CLAUDE.md §4.6) — `just debug-clear` removes them\n' > "$F"
    awk -v k="$K=" 'substr($0, 1, length(k)) != k' "$F" > "$F.tmp" && mv "$F.tmp" "$F"
    printf '%s=%s\n' "$K" "{{VALUE}}" >> "$F"
    echo "$F now holds:"
    grep -v '^#' "$F" | sed 's/^/  /'
    echo "(confirm what a run will resolve:  just debug-flags)"

[doc("remove one flag from .balticporter/debug.properties, or ALL of them (no KEY)")]
debug-clear KEY="":
    #!/usr/bin/env bash
    cd "{{root}}"
    F="{{bp_root}}/.balticporter/debug.properties"
    if [ ! -f "$F" ]; then echo "no debug flags set ($F is absent)"; exit 0; fi
    if [ -z "{{KEY}}" ]; then
      echo "removing all hand-written debug flags:"
      grep -v '^#' "$F" | sed 's/^/  -/'
      rm -f "$F"
    else
      K="{{KEY}}"
      case "$K" in balticporter.*) ;; *) K="balticporter.$K" ;; esac
      awk -v k="$K=" 'substr($0, 1, length(k)) != k' "$F" > "$F.tmp" && mv "$F.tmp" "$F"
      echo "removed $K; $F now holds:"
      grep -v '^#' "$F" | sed 's/^/  /'
      if [ -z "$(grep -v '^#' "$F")" ]; then rm -f "$F"; echo "(file removed — nothing left in it)"; fi
    fi

[doc("one type's TIR + emitted Scala, around a phase boundary (ROOT is a JAVA source root)")]
debug-emit ROOT FQN PHASES="" *FLAGS:
    #!/usr/bin/env bash
    cd "{{root}}"
    R="{{ROOT}}"; case "$R" in /*) ;; *) R="$(pwd)/$R" ;; esac
    ARGS="--root $R --fqn {{FQN}} --scala"
    [ -n "{{PHASES}}" ] && ARGS="$ARGS --phases {{PHASES}} --dump-before {{PHASES}} --dump-after {{PHASES}}"
    echo "+ {{core_project}}/runMain balticporter.runner.DebugEmit $ARGS {{FLAGS}}"
    echo "  (add --fast to parse ONLY the included files: seconds instead of minutes on a large"
    echo "   library, at the cost of resolution fidelity; --include <substr> narrows what is converted)"
    mkdir -p .balticporter/tmp
    CAP=".balticporter/tmp/debug-emit-$$.txt"
    {{sbt_migrate}} "{{core_project}}/runMain balticporter.runner.DebugEmit $ARGS {{FLAGS}}" 2>&1 |
      sed $'s/\033\\[[0-9;]*[a-zA-Z]//g' > "$CAP"
    st=${PIPESTATUS[0]}
    awk 'f || index($0, "[debug-emit]") > 0 { f = 1; print }' "$CAP" | grep -vE '^\[(info|warn|success)\]'
    if [ "$st" != "0" ]; then
      echo "!! debug-emit DID NOT RUN — sbt exited $st; its output:"
      tail -20 "$CAP" | sed 's/^/     /'
      rm -f "$CAP"; exit 1
    fi
    rm -f "$CAP"

[doc("CorrelateMain standalone — a compiler or test log you produced BY HAND, joined to members")]
correlate OUT *ARGS:
    #!/usr/bin/env bash
    cd "{{root}}"
    ROOT="$(pwd)"
    if [ -z "{{ARGS}}" ]; then
      echo "usage: just correlate <out-dir> [--scalac <file>] [--tests <file>] [--srcmap [scope=]<file>]..."
      echo
      echo "  never open an emitted file to work out which member an error is in."
      echo "  CorrelateMain joins compiler/test output through srcmap.tsv and members.tsv."
      echo
      echo "  --baseline defaults to <out>/../baseline, which is where the diffs come from."
      exit 2
    fi
    abs() { case "$1" in /*) printf '%s' "$1" ;; *) printf '%s' "$ROOT/$1" ;; esac; }
    OUT="$(abs "{{OUT}}")"
    set -- {{ARGS}}
    A=(); prev=""
    for a in "$@"; do
      case "$prev" in
        --scalac|--tests|--markers|--baseline|--out) a="$(abs "$a")" ;;
        --srcmap) case "$a" in
                    main=*|test=*) a="${a%%=*}=$(abs "${a#*=}")" ;;
                    *)             a="$(abs "$a")" ;;
                  esac ;;
      esac
      A+=("$a"); prev="$a"
    done
    {{sbt_migrate}} "{{core_project}}/runMain balticporter.tir.CorrelateMain --out $OUT ${A[*]}"

# ---------------------------------------------------------------------------------------------
# The debug recipes, proving themselves. No sbt, no ports, no network.
# ---------------------------------------------------------------------------------------------
[doc("prove the debug recipes do what they say — set/clear, in a temp root")]
debug-selfcheck:
    #!/usr/bin/env bash
    cd "{{root}}"
    T="$(mktemp -d)"
    trap 'rm -rf "$T"' EXIT
    fail=0
    ok()   { echo "  ok   $1"; }
    bad()  { echo "  FAIL $1"; fail=1; }
    want() { [ "$2" = "$3" ] && ok "$1" || bad "$1 (want [$3], got [$2])"; }
    J="{{just_executable()}}"
    F="$T/.balticporter/debug.properties"

    echo "-- debug-set / debug-clear (root=$T) --"
    BP_ROOT="$T" $J debug-set skipPhases '*' > /dev/null
    want "debug-set writes the file"            "$(grep -c . "$F" 2>/dev/null)" "2"
    want "...with the balticporter. prefix added" "$(grep -c '^balticporter.skipPhases=\*$' "$F")" "1"

    BP_ROOT="$T" $J debug-set skipPhases 'collections' > /dev/null
    want "debug-set is IDEMPOTENT — one entry"  "$(grep -c '^balticporter.skipPhases=' "$F")" "1"
    want "...and it is the NEW value"             "$(grep -c '^balticporter.skipPhases=collections$' "$F")" "1"

    BP_ROOT="$T" $J debug-set balticporter.tracePhases true > /dev/null
    want "an already-prefixed key is not double-prefixed" "$(grep -c '^balticporter.tracePhases=true$' "$F")" "1"
    want "...beside the first, which survives"    "$(grep -vc '^#' "$F")" "2"

    BP_ROOT="$T" $J debug-clear skipPhases > /dev/null
    want "debug-clear KEY removes one flag"     "$(grep -c '^balticporter.skipPhases=' "$F")" "0"
    want "...and leaves the other"                "$(grep -c '^balticporter.tracePhases=true$' "$F")" "1"

    BP_ROOT="$T" $J debug-clear tracePhases > /dev/null
    [ -f "$F" ] && bad "an emptied debug.properties must be REMOVED, not left as a header" \
                || ok "an emptied debug.properties is removed"

    BP_ROOT="$T" $J debug-set dumpOnly p.Foo > /dev/null
    BP_ROOT="$T" $J debug-clear > /dev/null
    [ -f "$F" ] && bad "debug-clear with no KEY must remove the file" || ok "debug-clear with no KEY removes the file"
    BP_ROOT="$T" $J debug-clear > /dev/null 2>&1
    want "...and is idempotent on an absent file" "$?" "0"

    echo "-- correlate: no arguments is a USAGE, not a silent no-op --"
    out=$($J correlate some/out 2>&1); rc=$?
    want "correlate with no options exits 2"      "$rc" "2"

    echo
    [ "$fail" = "0" ] && echo "debug-selfcheck: PASS" || { echo "debug-selfcheck: FAILED"; exit 1; }

# ---------------------------------------------------------------------------------------------
# Comment lint
# ---------------------------------------------------------------------------------------------
[doc("comment-lint: comment blocks over 5 lines, or narrative words in added comments (CLAUDE.md §7)")]
comment-lint RANGE="":
    scripts/comment-lint.sh {{RANGE}}

# ---------------------------------------------------------------------------------------------
# Metals MCP server
# ---------------------------------------------------------------------------------------------
[doc("start this checkout's Metals MCP server (idempotent, launchd) and write .mcp.json")]
metals-start:
    scripts/metals-server.sh start
[doc("stop this checkout's Metals MCP server")]
metals-stop:
    scripts/metals-server.sh stop
[doc("label, port and readiness of this checkout's Metals MCP server")]
metals-status:
    scripts/metals-server.sh status
[doc("call one Metals MCP tool from the shell: just metals-call list | just metals-call <tool> '<json args>'")]
metals-call +ARGS:
    scripts/metals-call.sh {{ARGS}}

# ---------------------------------------------------------------------------------------------
# TypeScript/JavaScript syntax-tree export — the generic exporter; which project is exported is
# the consumer's business (a JavaScript project passes a tsconfig with `allowJs`)
# ---------------------------------------------------------------------------------------------
[doc("export a TypeScript project's syntax trees: just ts-export path/to/tsconfig.json out-dir")]
ts-export TSCONFIG OUT:
    #!/usr/bin/env bash
    cd "{{root}}"
    EXPORTER="$(pwd)/balticporter/frontend-ts/exporter"
    echo "-- ts-export: {{TSCONFIG}} -> {{OUT}} --"
    node "$EXPORTER/dist/export.js" \
      --project "{{TSCONFIG}}" \
      --out "{{OUT}}"
